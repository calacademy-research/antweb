"""Links in upload reports and in the curator and admin parts of specimen and taxon pages work.

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
    # Only <a> links, the ones people click (a stylesheet or icon <link> is not a link to check).
    for href in re.findall(r"""<a\s[^>]*?href\s*=\s*["']([^"']+)["']""", html, re.I):
        href = href.replace("&amp;", "&").strip()
        if href.startswith(("mailto:", "javascript:", "#")):
            continue
        # Reports written before 10-17 link the raw upload file under /web/upload/, which is no
        # longer served (newer reports link uploadFile.do, checked like any other link).
        if "/web/upload/" in href:
            continue
        # Archived upload files (uploadFile.do) are open to admins and to curators of the file's
        # group only (10-18); Worldants and species list files are admin only, so the test
        # curator is refused them by design.
        if "/uploadFile.do" in href and "-specimen" not in href:
            continue
        url = urljoin(page_url, href)
        if (urlparse(url).hostname or "") == HOST:
            found.append(url.replace("http://", "https://", 1) if BASE_URL.startswith("https") else url)
    found = list(dict.fromkeys(found))
    if len(found) > MAX_LINKS:
        step = len(found) / MAX_LINKS
        found = [found[int(i * step)] for i in range(MAX_LINKS)]
    return found


def _check_all(session, report_url, after=None):
    r = session.get(report_url, timeout=TIMEOUT)
    assert r.status_code == 200, (report_url, r.status_code)
    html = r.text
    if after:
        if after not in html:
            pytest.skip(f'"{after}" is not on {report_url} for the test login')
        html = html.split(after, 1)[1]
    links = _links(html, report_url)
    assert links, f"no links found in {report_url}"
    _check_links(session, report_url, links)
    return r.text


def _check_links(session, report_url, links):

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
        if re.search(r"Log not available|Unable to find the file|Specimen not found", body):
            return f"{urlparse(url).path}: log or record not found"
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


SPECIMEN = os.environ.get("ANTWEB_LINK_TEST_SPECIMEN", "casent0922067")
TAXON = os.environ.get("ANTWEB_LINK_TEST_TAXON", "myrmicinaepheidole megacephala")
DEAD = ("hol.osu.edu", "plazi.cs.umb.edu", "/showLog.do")


def test_specimen_page_curator_links(session):
    """Curator and admin links on a specimen page (upload line, upload report, description). They pointed at showLog.do, renamed logMgr.do in 2023, and the images
    folder, whose listing is off."""
    url = BASE_URL + "/specimen.do?code=" + SPECIMEN
    html = _check_all(session, url, after="Curator Information")
    for dead in DEAD:
        assert dead not in html, f"{dead} still linked on {url}"
    assert not re.search(r"href=['\"][^'\"]*/images/[^/'\"]+/?['\"]", html), "images folder still linked"
    # A search over the whole upload (265,000 specimens for TaxonWorks) does not belong on a specimen page.
    assert not re.search(r"advancedSearch\.do\?[^'\"]*uploadId=", html), "Upload Search (whole-upload search) still linked"


def test_taxon_page_has_no_dead_sites(session):
    """HOL (hol.osu.edu) has been gone for years; its ID and the Plazi search built on it are
    no longer shown. The taxon page's links are checked like a report's."""
    url = BASE_URL + "/description.do?taxonName=" + TAXON.replace(" ", "%20")
    html = _check_all(session, url)
    for dead in DEAD:
        assert dead not in html, f"{dead} still linked on {url}"


# Links on the Curate page that start jobs, change data or end the session: never followed.
CURATE_SKIP = ("/util.do", "/utilData.do", "/logout.do", "/upload.do", "/adminAlert.do",
               "/pageTracker.do", "/schedule.do", "/imageUploader.do", "/loginAs.do",
               "/sanitizeDescriptions.do", "/passwordResetCampaign.do")
# Deliberate test links (an error message on purpose).
CURATE_TEST_LINKS = ("/adm1.do?%20(Terr.%20Amazonas)",)


def test_curate_page_links(session):
    """Every link on the Curate page (curate-body.jsp and curatorLinks.jsp) opens a real page:
    the old "Access" URL templates, log links under /web/log/ and /web/workingdir/, the Flickr and
    Geonames log files and a 2017 upload report link were dead (removed or replaced, cleanup batch 2)."""
    from policy import NEVER, WRITE_ACTIONS
    url = BASE_URL + "/curate.do"
    r = session.get(url, timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    if "Please Log In" in r.text[:5000]:
        pytest.skip("test login could not open curate.do")
    html = re.sub(r"<!--.*?-->", "", r.text, flags=re.S)  # commented-out links are not shown
    skip = set(CURATE_SKIP) | {p + ".do" for p in NEVER} | {p + ".do" for p in WRITE_ACTIONS}
    links = [u for u in _links(html, url)
             if urlparse(u).path not in skip and not any(t in u for t in CURATE_TEST_LINKS)]
    assert links, "no links found on curate.do"
    for dead in ("/web/workingdir/", "/web/log/geonames.log", "/web/log/flickrAdm1.html",
                 "/web/log/DataPlaceCase.txt", "/moveToValid.do", "20170731-14:44:39"):
        assert dead not in html, f"{dead} still linked on curate.do"
    for template in ("description.do?taxonName=", "description.do?antcatId=", "specimen.do?code=",
                     "geolocale.do?id=", "curiousQuery&name=", "curiousQuery&amp;name="):
        assert not re.search(r"""href=["'][^"']*""" + re.escape(template) + """["']""", html), f"empty link {template}"
    _check_links(session, url, links)


def test_upload_report_folders_stay_public():
    """Upload reports under /web/log/specimen/ are linked from public group pages: still served
    after the rest of /web/log/ was closed (10-17)."""
    page = new_session().get(BASE_URL + "/groups.do", timeout=TIMEOUT).text
    logs = re.findall(r"""href=["']([^"']*/web/log/(?:specimen|upload|worldants)/[^"']+\.html)["']""", page)
    if not logs:
        pytest.skip("no upload reports linked on groups.do")
    # Newest report: files from before about 2019 are no longer on the server (old links fixed 10-17).
    newest = max(logs, key=lambda h: h.rsplit("/", 1)[-1])
    url = urljoin(BASE_URL + "/", newest.replace("&amp;", "&"))
    r = new_session().get(url, timeout=TIMEOUT)
    assert r.status_code == 200, (url, r.status_code)


def test_groups_page_links_only_existing_reports():
    """groups.do links a group's last upload report only while the report is on the server
    (most reports from before Nov 2019 are gone); otherwise it says "(report no longer available)".
    Every /web/log/ link on the page answers 200."""
    page = new_session().get(BASE_URL + "/groups.do", timeout=TIMEOUT).text
    hrefs = re.findall(r"""href=["']([^"']*/web/log/[^"']+)["']""", page)
    if not hrefs:
        pytest.skip("no upload reports linked on groups.do")
    urls = list(dict.fromkeys(urljoin(BASE_URL + "/", h.replace("&amp;", "&")) for h in hrefs))

    def check(url):
        try:
            r = new_session().head(url, timeout=TIMEOUT, allow_redirects=True)
        except Exception as e:  # noqa: BLE001
            return f"{urlparse(url).path}: {type(e).__name__}"
        return None if r.status_code == 200 else f"{urlparse(url).path}: HTTP {r.status_code}"

    with ThreadPoolExecutor(max_workers=4) as pool:
        bad = [b for b in pool.map(check, urls) if b]
    assert not bad, f"{len(bad)} of {len(urls)} report links on groups.do fail:\n" + "\n".join(bad[:15])
