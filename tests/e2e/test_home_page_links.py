"""Every link on the home page works, AntWeb's own and outside sites.

Why: the home page and the menu on every page had links to things that are gone (AntShare,
the old Ant Course page at calacademy.org). Brian asked (10-17) that the pre-deploy run check
all home page links.

Read-only (as the test curator when set, so the anonymous-browsing limit does not answer
instead). AntWeb links (ANTWEB_HOME_LINK_SAMPLE, default 150 spread over the page; 0 = all) must answer
200 and not be a "not found" or "not in the AntWeb database" page. Outside links fail only when
the page is gone (404 or 410) or the site does not answer at all; sites that refuse robots
(403, 429, 999) are not counted as dead.
"""
import os
import re
from concurrent.futures import ThreadPoolExecutor
from urllib.parse import urljoin, urlparse

import requests

from antweb import BASE_URL, TIMEOUT, new_session, post_login
from test_upload_report_links import _check_links

HOST = urlparse(BASE_URL).hostname or ""
SAMPLE = int(os.environ.get("ANTWEB_HOME_LINK_SAMPLE", "150"))  # 0 = all
USER = os.environ.get("ANTWEB_TEST_USER")
PASSWORD = os.environ.get("ANTWEB_TEST_PASSWORD")
GONE = {404, 410}
BROWSER_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Safari/537.36"


def _home():
    s = new_session()
    if USER and PASSWORD:
        # Logged in, so AntWeb's limit on anonymous browsing does not answer instead of the page.
        post_login(s, USER, PASSWORD)
    r = s.get(BASE_URL + "/", timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    hrefs = []
    for href in re.findall(r"""<a\s[^>]*?href\s*=\s*["']([^"']+)["']""", r.text, re.I):
        href = href.replace("&amp;", "&").strip()
        if href.startswith(("mailto:", "javascript:", "#", "tel:")):
            continue
        hrefs.append(urljoin(BASE_URL + "/", href))
    return s, list(dict.fromkeys(hrefs))


def test_home_page_antweb_links():
    s, hrefs = _home()
    own = [h for h in hrefs if (urlparse(h).hostname or "") == HOST]
    assert own, "no AntWeb links found on the home page"
    if SAMPLE and len(own) > SAMPLE:
        step = len(own) / SAMPLE
        own = [own[int(i * step)] for i in range(SAMPLE)]
    _check_links(s, BASE_URL + "/", own)


def test_home_page_outside_links():
    _, hrefs = _home()
    outside = [h for h in hrefs if (urlparse(h).hostname or "") != HOST and urlparse(h).scheme in ("http", "https")]
    if not outside:
        return

    def check(url):
        try:
            r = requests.get(url, timeout=30, allow_redirects=True, headers={"User-Agent": BROWSER_UA})
        except requests.RequestException as e:
            return f"{url}: no answer ({type(e).__name__})"
        if r.status_code in GONE:
            return f"{url}: HTTP {r.status_code}"
        return None

    with ThreadPoolExecutor(max_workers=4) as pool:
        bad = [b for b in pool.map(check, outside) if b]
    assert not bad, f"{len(bad)} of {len(outside)} outside links on the home page are dead:\n" + "\n".join(bad)
