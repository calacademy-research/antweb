#!/usr/bin/env python3
"""The Data extract page and the command-line tool write the same files (2026-11-12).

DataExtract.java is a port of tools/export/specimen_extract.py: the same columns, the same filter, the same manifest
keys and text. Both carry a format number (VERSION) that is raised in both when the files change.

The island rule (Brian Fisher, 2026-11-11: "An oceanic island or archipelago with its own ant fauna is its own
country in AntWeb. Its specimens never count toward the political parent.") came to the tool with the
island-countries work (branch feat/island-countries, pull request 345: format 10). The page follows that rule and
writes format 10. So the repository is in one of two states, and this test fails in any other:

  1. the tool has the island rule: the two format numbers must be equal;
  2. the tool has not got it yet (pull request 345 is not merged): the tool is at 8 and the page at 10. The page is
     right for Brian's rule already; the tool still brings a country's islands with it. This state ends when 345
     is merged, and the test then asks for equal numbers by itself.

Also: no text of the page says that a country's islands come with it, and the form has no islands box.
Run: python3 tests/unit/test_data_extract_tool_version.py
"""
import os
import re
import sys
import unittest

REPO = os.environ.get("REPO_DIR") or os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def read(path):
    with open(os.path.join(REPO, path), encoding="utf-8") as f:
        return f.read()


class ToolAndPage(unittest.TestCase):

    def setUp(self):
        self.tool = read("tools/export/specimen_extract.py")
        self.engine = read("src/org/calacademy/antweb/util/DataExtract.java")
        self.tool_version = re.search(r'^VERSION = "(\d+)"', self.tool, re.M).group(1)
        self.page_version = re.search(r'public static final String VERSION = "(\d+)";', self.engine).group(1)
        self.tool_has_island_rule = "islands_left_out" in self.tool

    def test_the_format_numbers_agree(self):
        if self.tool_has_island_rule:
            self.assertEqual(self.tool_version, self.page_version,
                             "tools/export/specimen_extract.py and DataExtract.java write the same files: raise VERSION in both, "
                             "port the change, and regenerate the reference texts of DataExtractTest from the tool")
        else:
            self.assertEqual((self.tool_version, self.page_version), ("8", "10"),
                             "the tool has no island rule yet (pull request 345 not merged): the only state allowed is tool 8, page 10")

    def test_the_manifest_lines_of_the_island_rule_are_the_tools(self):
        """With the island rule in the tool, the page's manifest lines are the tool's, word for word."""
        lines = ("Islands included (named in the countries): ",
                 "Islands of these countries that are their own country in AntWeb, NOT in this file: ",
                 " (name an island with the countries to include it)",
                 "Old island names read as the island (a specimen marked with one is the island's): ")
        for line in lines:
            self.assertIn(line, self.engine)
            if self.tool_has_island_rule:
                self.assertIn(line, self.tool)
        for key in ('"islands_left_out"', '"old_island_names"'):
            self.assertIn(key, self.engine)

    def test_no_text_says_a_countrys_islands_come_with_it(self):
        action = read("src/org/calacademy/antweb/curate/DataExtractAction.java")
        self.assertNotIn('name=\\"islands\\"', action, "the form has no islands box (Brian Fisher, 2026-11-11)")
        self.assertIn("Islands are countries of their own.", action)
        for path in ("src/org/calacademy/antweb/curate/DataExtractAction.java", "src/org/calacademy/antweb/util/DataExtractJob.java",
                     "src/org/calacademy/antweb/util/DataExtractFiles.java", "docs/guides/data-extract.md"):
            text = read(path)
            for gone in ("with islands", "without islands", "Islands included: "):
                self.assertNotIn(gone, text, path)
        guide = read("docs/guides/data-extract.md")
        self.assertIn("An extract of `United States` has no specimen of Hawaii", guide)
        # the one sentence about the old box says that it is gone
        self.assertEqual(guide.count("Include islands filed under these countries"), 1)
        self.assertIn('ticked by default; it is gone', guide)
        engine_filter = self.engine.split("public static String whereSql(", 1)[1].split("\n    }", 1)[0]
        self.assertIn("s.island_country not in", engine_filter, "a specimen marked as another island's is left out")

    def test_the_request_has_no_islands_switch(self):
        request = self.engine.split("public static final class Request {", 1)[1].split("\n    }", 1)[0]
        self.assertNotIn("boolean islands", request)
        self.assertNotIn("request.islands", self.engine + read("src/org/calacademy/antweb/util/DataExtractJob.java"))


if __name__ == "__main__":
    unittest.main(verbosity=1)
