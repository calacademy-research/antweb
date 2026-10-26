"""A search for a pasted list of specimen codes (curator report 2026-10-26: after pasting specimen codes into the
Advanced Search, every Sort by choice gave an error page and the sort arrow gave "Bad Request").

- Codes separated by line breaks, tabs, commas or spaces are each searched (a list pasted from a spreadsheet).
- The specimen code box keeps line breaks (a textarea).
- A list long enough to make an address over 8 KB (Tomcat's old limit) is answered, also with its sort arrow
  (maxHttpHeaderSize 32 KB in docker/antweb/tomcat/conf/server.xml).
The Sort by list itself is tested in a browser: tests/e2e/test_browser.py::test_sort_dropdown_keeps_the_search.

Read-only.
"""
import re
from html import unescape
from urllib.parse import quote_plus, unquote

from antweb import BASE_URL, TIMEOUT, new_session

SEARCH = "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=formicidae&resultRank=specimen"
SOURCE = SEARCH + "&country=Madagascar&genus=Mystrium"
SHOWING = re.compile(r"Showing\s+[\d,]+(?:&ndash;|–)[\d,]+\s+of\s+([\d,]+)")
CODE = re.compile(r'/specimen\.do\?code=([^"\'&]+)')


def _codes(n=3):
    html = new_session().get(BASE_URL + SOURCE, timeout=TIMEOUT).text
    codes = []
    for c in CODE.findall(html):
        c = unquote(unescape(c)).lower()   # links hold codes URL-encoded, e.g. blf1234%2812%29-1 for blf1234(12)-1
        if c not in codes:
            codes.append(c)
    assert len(codes) >= n, "the Mystrium search of Madagascar shows fewer than %d specimens" % n
    return codes[:n]


def _search(codes_text, extra=""):
    url = BASE_URL + SEARCH + "&specimenCodeSearchType=equals&specimenCode=" + quote_plus(codes_text) + extra
    return url, new_session().get(url, timeout=max(TIMEOUT, 120))


def _found(r):
    m = SHOWING.search(r.text)
    return int(m.group(1).replace(",", "")) if m else None


def test_codes_separated_by_line_breaks_tabs_and_commas():
    a, b, c = _codes()
    for sep in ("\n", "\r\n", "\t", ", ", " "):
        url, r = _search(sep.join([a, b, c]))
        assert r.status_code == 200, (repr(sep), r.status_code)
        assert _found(r) == 3, "%r: found %s of 3 codes" % (sep, _found(r))


def test_code_box_keeps_line_breaks():
    html = new_session().get(BASE_URL + "/advSearch.do", timeout=TIMEOUT).text
    assert re.search(r'<textarea[^>]*name="specimenCode"', html), "the specimen code box is not a textarea"


def test_long_list_and_its_sort_arrow_are_answered():
    codes = _codes()
    # 3 real codes and 900 that match nothing: an address of about 12 KB (Tomcat refused over 8 KB until 2026-10-26)
    text = "\n".join(codes + ["zzcode%06d" % i for i in range(900)])
    url, r = _search(text)
    assert len(url) > 9000, len(url)
    assert r.status_code == 200, "a %d-character search address: %d" % (len(url), r.status_code)
    assert _found(r) == 3
    _, sorted_r = _search(text, "&sortBy=code&sortOrder=down")
    assert sorted_r.status_code == 200, "the sort arrow of that search: %d" % sorted_r.status_code
    assert _found(sorted_r) == 3
