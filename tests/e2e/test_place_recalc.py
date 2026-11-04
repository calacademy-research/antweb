"""Update statistics (action=recalc) on a place page, as an administrator on staging (road map step 5; catalog gaps 21-36,
the place recounts).

The page shows administrators an "Update statistics" button (overview-body.jsp; Overview.getRecalcButton: a POST
form with the form token). OverviewAction recounts only on a POST by a curator or administrator. The test presses it on a small country and one of its adm1 and
checks the page comes back as that place, with its statistics, and no error. An anonymous visitor gets no button.
Staging only (it recounts, so it changes data); one at a time (the recount takes the long connection pool).
"""
import re
from html import unescape
from urllib.parse import quote, urlparse

import pytest

import numbers_sample as ns
from antweb import BASE_URL, TIMEOUT, new_session
from staging_accounts import login, needs

COUNTRY = "Seychelles"
BUTTON = "Update statistics"
RECALC_FORM = re.compile(r'<form method="post" action="([^"]*action=recalc[^"]*)"[^>]*>(.*?)</form>', re.S)


def _button(html):
    """(action url, hidden fields) of the page's Update statistics form, or None."""
    for action, body in RECALC_FORM.findall(html):
        if BUTTON in body:
            fields = dict(re.findall(r'<input type="hidden" name="([^"]+)" value="([^"]*)"', body))
            url = unescape(action)
            return (url if url.startswith("http") else BASE_URL + url), fields
    return None


def _press(session, path):
    page = session.get(BASE_URL + path, timeout=TIMEOUT).text
    button = _button(page)
    assert button, f"{path}: an administrator sees no '{BUTTON}' button"
    url, fields = button
    r = session.post(url, data=fields, timeout=max(TIMEOUT, 300))
    assert r.status_code == 200, (path, r.status_code)
    return urlparse(url).path + "?action=recalc", r.text


def _check(html, title_words, path):
    text = ns.text_of(html)
    for word in title_words:
        assert word in text, f"{path} after Update statistics: '{word}' not on the page: {text[:300]}"
    for bad in ("not found", "Exception", "failed due to"):
        assert bad not in text, f"{path} after Update statistics: '{bad}': {text[:300]}"
    assert re.search(r"Valid species", text), f"{path} after Update statistics: no statistics table"


def test_anonymous_visitor_gets_no_update_button():
    html = ns.get(f"/country.do?name={quote(COUNTRY)}", new_session())
    assert _button(html) is None, "an anonymous visitor sees the Update statistics button"


@needs("admin")
def test_admin_updates_a_country_and_an_adm1():
    s, _token, _lid = login("admin")
    path = f"/country.do?name={quote(COUNTRY)}"
    posted, page = _press(s, path)
    assert posted == "/country.do?action=recalc", posted
    _check(page, [COUNTRY], path)
    adm1 = re.search(r"""href=['"][^'"]*/adm1\.do\?([^'"]+)['"][^>]*>([^<]+)</a>""", page)
    if not adm1:
        pytest.skip(f"{COUNTRY}'s page links no adm1")
    path = "/adm1.do?" + unescape(adm1.group(1))
    posted, page = _press(s, path)
    assert posted == "/adm1.do?action=recalc", posted
    _check(page, [adm1.group(2).strip()], path)


@needs("admin")
def test_region_and_subregion_pages_offer_no_update_button():
    """Region and subregion pages do not recount (OverviewAction): no "Update statistics" button there (2026-11-04)."""
    s, _token, _lid = login("admin")
    for path in ("/region.do?name=Africa", "/subregion.do?name=Eastern%20Africa"):
        page = s.get(BASE_URL + path, timeout=max(TIMEOUT, 120)).text
        assert _button(page) is None, f"{path}: shows an Update statistics button that does nothing"
