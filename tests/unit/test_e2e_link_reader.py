"""The e2e link reader (tests/e2e/public_pages.py) reads links as a browser does (finding F27, 2026-11-03):
- '&regionName=' in a link stays as it is ('&reg' without ';' is not the (R) sign; html.unescape decoded it, so every
  region list link lost its rank and regionName);
- links inside scripts and HTML comments are not links (the Africa page has 198 in comments, 5 real ones).
Runs without a server: python3 tests/unit/test_e2e_link_reader.py
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "e2e"))
import public_pages as pp  # noqa: E402

PAGE = """<html><!--[if gte IE 9]><!--><html><!--<![endif]-->
<script>var s = "<a href='/taxonomicPage.do?rank=genus&regionName=Script'>";</script>
<!-- <a href="/taxonomicPage.do?rank=genus&regionName=Commented">old</a> -->
<a href="https://staging.antweb.org/taxonomicPage.do?rank=species&amp;regionName=Africa">Species</a><!--->
<a href='/taxonomicPage.do?rank=subfamily&regionName=Africa'>Subfamilies</a>
"""


def main():
    found = pp.links(PAGE, "taxonomicPage")
    assert found == ["/taxonomicPage.do?rank=species&regionName=Africa",
                     "/taxonomicPage.do?rank=subfamily&regionName=Africa"], found
    assert pp.params(found[1]) == {"rank": "subfamily", "regionName": "Africa"}, pp.params(found[1])
    assert pp.attr_unescape("a&amp;b") == "a&b"
    assert pp.attr_unescape("a&regionName=b") == "a&regionName=b"
    assert pp.attr_unescape("x&#38;y") == "x&y"
    # 2026-11-07: the list readers of numbers_sample had the same bug; a region list's subspecies row was read as its
    # species (Camponotus ligniperda nigrescens as ligniperda: "list 0, API specimenCount 350")
    try:
        import pytest  # noqa: F401  (numbers_sample imports it; the CI unit step has no pytest)
    except ImportError:
        import types
        sys.modules["pytest"] = types.ModuleType("pytest")
    import numbers_sample as ns
    row = '<td><a href="https://staging.antweb.org/description.do?subfamily=formicinae&genus=camponotus&species=ligniperda&subspecies=nigrescens&rank=subspecies&regionName=Europe">x</a></td>'
    assert ns.taxon_name_of(row) == "formicinaecamponotus ligniperda nigrescens", ns.taxon_name_of(row)
    print("e2e link reader OK")


if __name__ == "__main__":
    main()
