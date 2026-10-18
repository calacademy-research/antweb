"""Caddy refuses direct *.jsp requests except an allow-list (docker/caddy/Caddyfile.common, and
the same list in Caddyfile for production). Pages are reached through .do actions, which forward
to their JSPs inside Tomcat and are not affected.

Caddy's own refusal is a plain-text "Not found" 404. Tomcat may also answer 404 (a page that is
gone, or a JSP that refuses a direct call); for allow-listed JSPs any answer except Caddy's
refusal is fine.
"""
import pytest

from antweb import BASE_URL, TIMEOUT, new_session
from caddy_lists import PROD, STAGING, jsp_allow_list

ALLOWED = jsp_allow_list(STAGING)

# Concrete URLs for the wildcard entries.
WILDCARD_EXAMPLES = {
    "/web/speciesList/*/*.jsp": "/web/speciesList/florida/florida.jsp",
    "/web/log/*": "/web/log/profiler.jsp",
}

# Real JSPs that are not on the list: Caddy must refuse them.
NOT_LISTED = ["/memory.jsp", "/util/test.jsp", "/curate/curate-body.jsp", "/specimen-body.jsp",
              "/search/advancedSearchResults.jsp", "/curate/curatorCheck.jsp", "/layouts/layout.jsp",
              "/web/genInc/statistics.jsp", "/INDEX-body.jsp", "/index-body.jsp", "/x.jspx"]


def is_caddy_refusal(r) -> bool:
    return (r.status_code == 404 and r.text.strip() == "Not found"
            and r.headers.get("Content-Type", "").startswith("text/plain"))


def test_prod_and_staging_lists_match():
    assert sorted(jsp_allow_list(PROD)) == sorted(ALLOWED)


@pytest.mark.parametrize("path", NOT_LISTED)
def test_unlisted_jsp_refused_by_caddy(path):
    r = new_session().get(BASE_URL + path, timeout=TIMEOUT, allow_redirects=False)
    assert is_caddy_refusal(r), f"{path}: HTTP {r.status_code} {r.headers.get('Content-Type')}"


@pytest.mark.parametrize("path", sorted(WILDCARD_EXAMPLES.get(p, p) for p in ALLOWED))
def test_listed_jsp_reaches_tomcat(path):
    r = new_session().get(BASE_URL + path, timeout=TIMEOUT, allow_redirects=False)
    assert not is_caddy_refusal(r), f"{path} is on the allow-list but Caddy refused it"


def test_do_pages_unaffected():
    """A .do page that forwards to a JSP still works (the forward stays inside Tomcat)."""
    r = new_session().get(BASE_URL + "/about.do", timeout=TIMEOUT)
    assert r.status_code == 200 and not is_caddy_refusal(r)
