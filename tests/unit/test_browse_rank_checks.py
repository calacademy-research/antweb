"""browse.do and images.do decide page-level work by exact rank (BrowseAction.isRank), never by instanceof Family,
Subfamily or Genus: Species extends Genus extends Subfamily extends Family, so instanceof matched every lower rank and
species images pages read the genus rows of their whole subfamily (2026-11-17, Jack Longino and Brian Fisher: species
images pages had become slow, 7 s for Pheidole megacephala on staging). Source check, no build."""
import os
import re
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BROWSE = os.path.join(ROOT, "src", "org", "calacademy", "antweb", "BrowseAction.java")


class BrowseRankChecks(unittest.TestCase):
    def test_no_instanceof_of_a_rank_class(self):
        with open(BROWSE, encoding="utf-8") as f:
            code = [l for l in f if not l.strip().startswith("//") and not l.strip().startswith("*")]
        found = [l.strip() for l in code if re.search(r"instanceof\s+(Family|Subfamily|Genus)\b", l)]
        self.assertEqual(found, [], "use BrowseAction.isRank(taxon, Rank.X): " + "; ".join(found))

    def test_the_page_blocks_use_isRank(self):
        with open(BROWSE, encoding="utf-8") as f:
            text = f.read()
        for rank in ("SUBFAMILY", "FAMILY", "GENUS"):
            self.assertIn("isRank(taxon, Rank." + rank + ")", text)


if __name__ == "__main__":
    unittest.main()
