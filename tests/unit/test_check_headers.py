#!/usr/bin/env python3
"""tools/ci/check_headers.py: which files count as having a header comment, and the baseline rules."""
import os
import sys
import tempfile

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "tools", "ci"))
import check_headers as c  # noqa: E402


def write(root, rel, text):
    p = os.path.join(root, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as f:
        f.write(text)


JAVA_OK = [
    # javadoc directly before the class
    "package a;\nimport b.C;\n\n/**\n * Reads the upload file and writes the specimen table.\n */\npublic class A {}\n",
    # // block, annotation and blank line in between
    "package a;\n// Runs the nightly counts for every geolocale.\n\n@Deprecated\npublic final class A {}\n",
    # interface / enum / record / abstract
    "package a;\n/** Lists the reasons a name changed. */\npublic enum A { X }\n",
    "package a;\n/** Holds one parsed line of an upload file. */\nrecord A(int x) {}\n",
    "package a;\n/** Base of every upload kind here. */\npublic abstract class A {}\n",
    # a commented-out import before the javadoc does not matter
    "package a;\n//import x.Y;\n/**\n * Builds the upload report page.\n */\nclass A {}\n",
]
JAVA_MISSING = [
    "package a;\nimport b.C;\n\npublic class A {}\n",
    # only boilerplate
    "package a;\n/**\n * @author thau\n * Created by IntelliJ IDEA.\n * Date: 2010-01-02\n */\npublic class A {}\n",
    "package a;\n/*\n * Copyright 2004 California Academy of Sciences\n */\npublic class A {}\n",
    # commented-out code only
    "package a;\n//import java.util.*;\n//import org.x.Y;\npublic class A {}\n",
    # a comment above the imports is not directly before the class
    "/** Reads the upload file and writes the specimen table. */\npackage a;\nimport b.C;\npublic class A {}\n",
    # a nested class with a comment does not count for the top-level one
    "package a;\npublic class A {\n  /** Holds one parsed line of the file. */\n  static class B {}\n}\n",
    # too short to be a sentence
    "package a;\n/** Helper. */\npublic class A {}\n",
]
JSP_OK = [
    "<%-- Curate page: upload a specimen file (curators). --%>\n<%@ page import=\"x\" %>\n",
    "<%@ page errorPage=\"/error.jsp\" %>\n<%@ page import=\"x\" %>\n\n<%--\n  Upload report: the lines of one upload.\n--%>\n",
]
JSP_MISSING = [
    "<%@ page import=\"x\" %>\n<html></html>\n",
    "<!-- uploadReport.jsp -->\n<html></html>\n",
    # a commented-out include is not a header
    "<%-- <%@include file=\"/curate/adminCheck.jsp\" %> --%>\n<html></html>\n",
    # opens after the first 20 lines
    "\n" * 25 + "<%-- Upload report: the lines of one upload. --%>\n",
]


def test_rules():
    for i, t in enumerate(JAVA_OK):
        assert c.has_header("A.java", t), ("java ok", i, t)
    for i, t in enumerate(JAVA_MISSING):
        assert not c.has_header("A.java", t), ("java missing", i, t)
    for i, t in enumerate(JSP_OK):
        assert c.has_header("a.jsp", t), ("jsp ok", i, t)
    for i, t in enumerate(JSP_MISSING):
        assert not c.has_header("a.jsp", t), ("jsp missing", i, t)


def test_baseline():
    with tempfile.TemporaryDirectory() as root:
        write(root, "src/a/Has.java", JAVA_OK[0])
        write(root, "src/a/New.java", JAVA_MISSING[0])
        write(root, "src/a/Old.java", JAVA_MISSING[0])
        write(root, "web/p.jsp", JSP_OK[0])
        write(root, "web/q.jsp", JSP_MISSING[0])
        write(root, "web/notes.txt", "not checked")
        res = c.scan(root)
        assert res == {"src/a/Has.java": True, "src/a/New.java": False, "src/a/Old.java": False,
                       "web/p.jsp": True, "web/q.jsp": False}, res
        assert c.counts(res) == {"Java": [1, 2], "JSP": [1, 1]}
        # complete baseline: no problems
        assert c.problems(res, {"src/a/New.java", "src/a/Old.java", "web/q.jsp"}) == []
        # (1) a new file without a header fails
        errs = c.problems(res, {"src/a/Old.java", "web/q.jsp"})
        assert len(errs) == 1 and errs[0].startswith("src/a/New.java: no header"), errs
        # (2) a baseline file that now has a header fails, naming the line to remove
        errs = c.problems(res, {"src/a/New.java", "src/a/Old.java", "web/q.jsp", "web/p.jsp"})
        assert len(errs) == 1 and "Remove this line" in errs[0] and errs[0].endswith("web/p.jsp"), errs
        # a baseline line for a deleted file fails
        errs = c.problems(res, {"src/a/New.java", "src/a/Old.java", "web/q.jsp", "web/gone.jsp"})
        assert len(errs) == 1 and "no longer exists" in errs[0], errs
        # baseline file format: comments and blank lines ignored
        bl = os.path.join(root, "b.txt")
        write(root, "b.txt", "# comment\n\nweb/q.jsp\n")
        assert c.read_baseline(bl) == {"web/q.jsp"}


def test_repo_baseline_holds():
    """The committed baseline matches the tree (the same check CI runs)."""
    res = c.scan(c.ROOT)
    errs = c.problems(res, c.read_baseline(os.path.join(c.ROOT, c.BASELINE)))
    assert errs == [], "\n".join(errs[:20])


if __name__ == "__main__":
    test_rules()
    test_baseline()
    test_repo_baseline_holds()
    print("OK: check_headers")
