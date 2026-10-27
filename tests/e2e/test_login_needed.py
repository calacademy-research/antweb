"""Field guides, image comparisons and maps of search results need a login (any login).
Anonymous visitors see the links, and get the log-in page with an explanation; after logging in
they return to the page they asked for (a same-site relative path only)."""
import html
from urllib.parse import parse_qs, urljoin, urlparse
import re

import pytest

from antweb import BASE_URL, TIMEOUT, classify, new_session

MESSAGE = "Field guides, image comparisons and maps of search results need an AntWeb login."

FEATURE_URLS = [
    "/fieldGuide.do?genus=camponotus&rank=genus&project=allantwebants",
    "/fieldguide.do?genus=camponotus&rank=genus&project=allantwebants",
    "/prepareMapResults.do?resultRank=specimen",
    "/prepareCompareResults.do?resultRank=specimen",
    "/mapResults.do",
    "/compareResults.do",
    "/fieldGuideResults.do",
    # The comparisons of a taxon's children (finding F15, 2026-10-26; before, a message asking to log in).
    "/getComparison.do?genus=mystrium&rank=genus&pr=d",
    "/mapComparison.do?genus=mystrium&rank=genus&pr=d",
    "/mapcomparison.do?genus=mystrium&rank=genus&pr=d",
]

TARGET = re.compile(r'<input type="hidden" name="target" value="([^"]*)"')


@pytest.mark.parametrize("url", FEATURE_URLS)
def test_anonymous_gets_explained_login(url):
    r = new_session().get(BASE_URL + url, timeout=TIMEOUT, allow_redirects=True)
    assert classify(r).category == "login", f"{url}: {classify(r)}"
    assert MESSAGE in r.text, f"{url}: no explanation on the log-in page"
    assert 'value="Create Account"' in r.text


def test_login_page_has_no_link_to_the_page_without_its_taxon():
    """The log-in page shown for getComparison.do?name=formicidae&rank=family had a Change View menu built from
    the page asked for, minus name= (HttpUtil.removeOverview): /getComparison.do?rank=family&pr=b&project=...
    gave "Taxon not found" (link check of PR #289, 2026-10-27). The log-in page has no Change View menu now."""
    for url in ("/getComparison.do?name=formicidae&rank=family&project=allantwebants&pr=b",
                "/mapComparison.do?name=formicidae&rank=family&project=allantwebants&pr=b"):
        r = new_session().get(BASE_URL + url, timeout=TIMEOUT, allow_redirects=True)
        assert classify(r).category == "login", f"{url}: {classify(r)}"
        assert 'id="change_view"' not in r.text, f"{url}: Change View menu on the log-in page"
        action = urlparse(url).path
        for href in re.findall(r'href="([^"]*)"', r.text):
            u = urlparse(urljoin(BASE_URL + "/", html.unescape(href)))
            if u.path.endswith(action):
                assert "name" in parse_qs(u.query), f"{url}: link without the taxon: {href}"


def test_return_target_is_the_requested_page():
    url = "/fieldGuide.do?genus=camponotus&rank=genus&project=allantwebants"
    r = new_session().get(BASE_URL + url, timeout=TIMEOUT, allow_redirects=True)
    m = TARGET.search(r.text)
    assert m, "no return target in the log-in form"
    target = html.unescape(m.group(1))
    assert target.startswith("/fieldGuide.do?") and "genus=camponotus" in target, target


def test_post_return_target_ignores_other_sites():
    """A POST from another site's page gets no return target from its Referer."""
    s = new_session()
    r = s.post(BASE_URL + "/mapResults.do", headers={"Referer": "https://evil.example/x"},
               timeout=TIMEOUT, allow_redirects=True)
    assert MESSAGE in r.text
    m = TARGET.search(r.text)
    if m:
        value = html.unescape(m.group(1))
        assert "evil.example" not in value, value
        assert value.startswith("/") or urlparse(value).hostname == urlparse(BASE_URL).hostname, value
