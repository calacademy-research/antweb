"""Sorting a search keeps every search parameter (no server needed; runs web/common/specimen_list.js's sortExtras in node).

Bug of 2026-11-03 (Michele Esposito): an Advanced Search with a pasted list of specimen codes, whose address had
sortBy near the start (sortBy=taxonname after isIgnoreInsufficientCriteria), answered "Not enough search criteria"
when sorted by specimen code. The script cut the DECODED query with /&sortBy.*/; "." stops at the line breaks of the
code list, so everything from sortBy to the first code was dropped and the rest of the codes ran together.
sortExtras works on the encoded query (location.search) and drops only the sortBy, t and page parameters.

Run: python3 tests/unit/test_sort_extras.py   (also in .github/workflows/tests.yaml; needs node)
"""
import json
import os
import shutil
import subprocess

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
JS = os.path.join(ROOT, "web", "common", "specimen_list.js")

# Michele's search (11-03), shortened to three codes; sortBy comes early, the codes are separated by %0D%0A.
MICHELE = ("?searchMethod=advancedSearch&advanced=true&isIgnoreInsufficientCriteria=false&sortBy=taxonname"
           "&collGroupOpen=none&family=Formicidae&subfamily=none&genus=none"
           "&specimenCodeSearchType=contains&specimenCode=casent0040404%0D%0Acasent0040404-d01%0D%0Acasent0063687%0D%0A"
           "&statusSet=complete&redFlagOption=exclude&resultRank=specimen&output=list&x=43&y=7&sortOrder=down&t=&page=3")


def sort_extras(search):
    node = shutil.which("node")
    assert node, "node is needed (it is on the GitHub runners)"
    src = open(JS, encoding="utf-8").read()
    start = src.index("function sortExtras(")
    script = src[start:] + "\nprocess.stdout.write(JSON.stringify(sortExtras(" + json.dumps(search) + ")));\n"
    out = subprocess.run([node, "-e", script], capture_output=True, text=True, timeout=30)
    assert out.returncode == 0, out.stderr
    return json.loads(out.stdout)


def test_micheles_search_keeps_every_parameter():
    got = sort_extras(MICHELE)
    params = got.split("&")
    names = [p.split("=")[0] for p in params]
    assert "sortBy" not in names and "t" not in names and "page" not in names, got
    assert "specimenCode=casent0040404%0D%0Acasent0040404-d01%0D%0Acasent0063687%0D%0A" in params, got
    for kept in ("searchMethod=advancedSearch", "isIgnoreInsufficientCriteria=false", "collGroupOpen=none",
                 "family=Formicidae", "resultRank=specimen", "sortOrder=down"):
        assert kept in params, (kept, got)
    assert "\n" not in got and "\r" not in got, "the query stays encoded"
    assert len(params) == len(MICHELE.lstrip("?").split("&")) - 3, got


def test_empty_and_plain():
    assert sort_extras("") == ""
    assert sort_extras("?") == ""
    assert sort_extras("?genus=Mystrium&sortBy=code") == "genus=Mystrium"
    assert sort_extras("?sortBy=code&genus=Mystrium&sortByX=1") == "genus=Mystrium&sortByX=1"


if __name__ == "__main__":
    test_micheles_search_keeps_every_parameter()
    test_empty_and_plain()
    print("sort keeps every search parameter: ok")
