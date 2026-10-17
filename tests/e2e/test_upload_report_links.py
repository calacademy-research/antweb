"""Links in the newest specimen upload report and the newest Worldants upload report work.

Why: the image upload report's "Uploaded Specimen Set" linked to image folders after folder
listings were turned off (10-17). The other upload reports are checked the same way, so a
change to Caddy, the log folders or a page does not leave curators with dead links.

Read-only: page loads only, as the test curator. Checks every same-site link in each report,
up to MAX_LINKS (a sample spread over the report), for a 200 answer that is not a "not in the
AntWeb database" or error page. Links to other sites are not checked.
"""
import os
import re
from concurrent.futures import ThreadPoolExecutor
from urllib.parse import urljoin, urlparse

import pytest

from antweb import post_login, BASE_URL, TIMEOUT, new_session

USER = os.environ.get("ANTWEB_TEST_USER")
PASSWORD = os.environ.get("ANTWEB_TEST_PASSWORD")
HOST = urlparse(BASE_URL).hostname or ""
MAX_LINKS = int(os.environ.get("ANTWEB_REPORT_LINK_SAMPLE", "80"))

pytestmark = pytest.mark.skipif(not USER or not PASSWORD, reason="needs ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD")


@pytest.fixture(scope="module")
def session():
    s = new_session()
    post_login(s, USER, PASSWORD)
    return s


def _links(html, page_url):
    found = []
    for href in re.findall(r"""href\s*=\s*["']([^"']+)["']""", html, re.I):
        href = href.replace("&amp;", "&").strip()
        if href.startswith(("mailto:", "javascript:", "#")):
            continue
        url = urljoin(page_url, href)
        if (urlparse(url).hostname or "") == HOST:
            found.append(url.replace("http://", "https://", 1) if BASE_URL.startswith("https") else url)
    found = list(dict.fromkeys(found))
    if len(found) > MAX_LINKS:
        step = len(found) / MAX_LINKS
        found = [found[int(i * step)] for i in range(MAX_LINKS)]
    return found


def _check_all(session, report_url):
    r = session.get(report_url, timeout=TIMEOUT)
    assert r.status_code == 200, (report_url, r.status_code)
    links = _links(r.text, report_url)
    assert links, f"no links found in {report_url}"

    def check(url):
        try:
            resp = session.get(url, timeout=TIMEOUT)
        except Exception as e:  # noqa: BLE001
            return f"{urlparse(url).path}: {type(e).__name__}"
        body = resp.text[:200000]
        if resp.status_code != 200:
            return f"{urlparse(url).path}: HTTP {resp.status_code}"
        if "is not in the AntWeb database" in body:
            return f"{urlparse(url).path}: not in the AntWeb database"
        if re.search(r"<title>[^<]*(Not Found|Index of)", body, re.I):
            return f"{urlparse(url).path}: not found page"
        return None

    with ThreadPoolExecutor(max_workers=4) as pool:
        bad = [b for b in pool.map(check, links) if b]
    assert not bad, f"{len(bad)} of {len(links)} links in {report_url} fail:\n" + "\n".join(bad[:15])


def test_specimen_upload_report_links(session):
    page = session.get(BASE_URL + "/listSpecimenUploads.do", timeout=TIMEOUT).text
    logs = re.findall(r"""href=["']([^"']*/web/log/specimen/[^"']+\.html)["']""", page)
    if not logs:
        pytest.skip("no specimen upload reports listed on listSpecimenUploads.do")
    _check_all(session, urljoin(BASE_URL + "/", logs[0]))


def test_worldants_upload_report_links(session):
    page = session.get(BASE_URL + "/query.do?name=worldantsUploads", timeout=TIMEOUT).text
    logs = re.findall(r"/web/log/worldants/\d{8}[^'\"<]+\.html", page)
    if not logs:
        pytest.skip("no Worldants upload reports listed")
    _check_all(session, BASE_URL + logs[0])
