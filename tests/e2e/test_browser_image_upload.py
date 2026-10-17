"""Image Uploader in a real browser (Playwright), as the test curator. Staging only, never saves.

Opens imageUploader.do?dryRun=1 (test mode: every file is checked and sent one per request,
nothing is saved), selects several small generated JPEGs named for the test specimen plus one
badly named file, presses Upload Files, and checks:
- the good files are each sent on their own and all end "checked (test, not saved)";
- the badly named one is not sent and shows the reason;
- the page says "Upload finished", with no JavaScript errors;
- the curator note about many images and retries is on the page.
Skipped without Playwright, without the test login, if the login may not upload images, where
imageUploadBatch.do is not deployed, or on production.
"""
from urllib.parse import urlsplit

import pytest

sync_api = pytest.importorskip("playwright.sync_api")

from antweb import BASE_URL, IS_PRODUCTION  # noqa: E402
import test_image_upload as iu  # noqa: E402
from test_browser import playwright, browser, _context  # noqa: E402,F401  (fixtures)

pytestmark = [
    pytest.mark.skipif(IS_PRODUCTION or (urlsplit(BASE_URL).hostname or "") not in iu.ALLOWED_HOSTS,
                       reason="browser image upload checks run on staging or a local server only"),
    iu.needs_curator,
]

deployed = iu.deployed
curator = iu.curator


@pytest.fixture(scope="module")
def page(browser, deployed, curator):  # noqa: F811
    s, _token, _ = curator
    ctx = _context(browser, viewport={"width": 1280, "height": 900})
    host = urlsplit(BASE_URL).hostname
    secure = BASE_URL.startswith("https")
    ctx.add_cookies([{"name": c.name, "value": c.value, "domain": host, "path": c.path or "/",
                      "secure": secure, "httpOnly": True} for c in s.cookies])
    pg = ctx.new_page()
    pg.errors = []
    pg.requests = []
    pg.on("pageerror", lambda e: pg.errors.append(str(e)))
    pg.on("request", lambda r: pg.requests.append(r.url) if "imageUploadBatch.do" in r.url else None)
    pg.on("dialog", lambda d: d.accept())
    pg.goto(BASE_URL + "/imageUploader.do?dryRun=1")
    yield pg
    ctx.close()


def test_page_has_curator_note(page):
    note = page.inner_text("#iuHowItWorks")
    assert "one at a time" in note and "Retry failed files" in note, note
    assert page.locator("#imageUploadForm[data-batch-url]").count() == 1


def test_several_files_all_done(page):
    spec = iu.TEST_SPECIMEN
    good = [f"{spec}_D_1.JPG", f"{spec}_P_1.JPG", f"{spec}_H_1.JPG", f"{spec}_L_1.JPG"]
    bad = spec.lower() + "_d_2.jpg"
    files = [{"name": n, "mimeType": "image/jpeg", "buffer": iu.JPEG} for n in good + [bad]]
    page.set_input_files("#file", files)
    page.click("#iuSubmit")
    page.wait_for_function("document.getElementById('iuTotals').textContent.indexOf('Upload finished') === 0",
                           timeout=120000)
    rows = page.locator("#iuList li")
    assert rows.count() == len(files)
    done = page.locator("#iuList li.iu-done")
    assert done.count() == len(good), page.inner_text("#iuList")
    for i in range(done.count()):
        assert "checked (test, not saved)" in done.nth(i).inner_text()
    rejected = page.locator("#iuList li.iu-rejected")
    assert rejected.count() == 1 and "uppercase" in rejected.first.inner_text(), page.inner_text("#iuList")
    adds = [u for u in page.requests if "step=add" in u]
    assert len(adds) == len(good), adds            # one request per good file, none for the bad one
    assert all("dryRun=1" in u for u in page.requests), page.requests
    assert not page.errors, page.errors
