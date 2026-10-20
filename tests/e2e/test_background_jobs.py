"""Uploads and admin data jobs run in the background (2026-10-20, BackgroundJobs).

Cloudflare ends a request after 100 seconds with a "524 timeout" page while the server keeps
working; curators took it for a failed upload and sent the file again. Now upload.do and the
admin data jobs answer at once with a redirect to the job's status page (jobStatus.do?id=N),
which shows the step and reloads itself until the job has ended.

- anonymous visitors get the log-in page for jobStatus.do (any host);
- the Curate page no longer tells curators about Cloudflare timeouts (static check of the source);
- staging, test uploader: the upload POST answers in under 20 seconds with the status page, which
  ends with "Upload complete" and the report link; a second upload sent while the first is still
  waiting or running goes to the first one's status page ("You already have an upload ...");
- staging, test admin: a data job (taxonFinish: the subfamily chart colours, part of nightly set 1,
  changes nothing else) answers in under 20 seconds with its status page and finishes; the test
  uploader cannot see that admin job.

The uploads use the test group's baseline file only, so the group is left as the other upload
tests expect it.
"""
import re
import time
from pathlib import Path

import pytest

from antweb import BASE_URL, TIMEOUT, get, new_session
from staging_accounts import (assert_upload_complete, baseline_text, job_status, job_url, login, needs, post_upload,
                              report_url, require_test_group, upload_specimens, wait_for_job)

REPO = Path(__file__).resolve().parents[2]
FAST = 20  # seconds: well under Cloudflare's 100


def _text(html):
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", html))


def _baseline():
    from test_specimen_upload_roundtrip import find_names
    _syn, valid = find_names()
    return baseline_text((valid.get("subfamily") or "").capitalize(), valid["genus"].capitalize(), valid["species"])


def test_status_page_needs_login():
    for url in ("/jobStatus.do", "/jobStatus.do?id=1"):
        outcome = get(new_session(), url)
        assert outcome.category == "login", f"{url}: {outcome}"


def test_curate_page_note_is_accurate():
    src = (REPO / "web" / "curate" / "curate-body.jsp").read_text(encoding="utf-8")
    assert "Cloudflare timeout" not in src
    assert "Large files take several minutes; you will see the progress on the next page" in src
    assert "setTimeout" not in src.split("function validateZipUpload", 1)[1].split("</script>", 1)[0], \
        "the upload form must not leave the page while the file is being sent"
    assert "/jobStatus.do" in src


@needs("uploader")
def test_upload_answers_at_once_and_shows_progress():
    s, token, _login_id = login("uploader")
    require_test_group(s)
    r = upload_specimens(s, token, _baseline())
    assert r.job_url, "the upload POST did not answer with a redirect to jobStatus.do: " + _text(r.text)[:400]
    assert r.post_seconds < FAST, f"the upload POST took {r.post_seconds:.1f} s"
    assert_upload_complete(r)
    rep = s.get(report_url(r), timeout=TIMEOUT)
    assert rep.status_code == 200 and "Upload Report" in rep.text, (report_url(r), rep.status_code)
    # The list of recent uploads has this one.
    listing = s.get(BASE_URL + "/jobStatus.do", timeout=TIMEOUT).text
    job_id = r.job_url.split("id=", 1)[1].split("&", 1)[0]
    assert f"jobStatus.do?id={job_id}" in listing, "the upload is not on the Upload status list"


@needs("uploader")
def test_second_upload_goes_to_the_first():
    s, token, _login_id = login("uploader")
    require_test_group(s)
    text = _baseline()
    first, seconds = post_upload(s, token, text)
    url1 = job_url(first)
    assert url1, "the upload POST did not answer with a redirect to jobStatus.do: " + _text(first.text)[:400]
    assert seconds < FAST, f"the upload POST took {seconds:.1f} s"
    second, seconds2 = post_upload(s, token, text)
    url2 = job_url(second)
    assert url2 and seconds2 < FAST, (second.status_code, seconds2, _text(second.text)[:300])
    try:
        id1 = url1.split("id=", 1)[1].split("&", 1)[0]
        id2 = url2.split("id=", 1)[1].split("&", 1)[0]
        if id1 != id2:
            pytest.skip("the first upload had already ended when the second was sent")
        assert "already=1" in url2, url2
        page = s.get(url2, timeout=TIMEOUT).text
        assert "You already have an upload waiting or running" in _text(page), _text(page)[:500]
        assert job_status(page) in ("queued", "running", "finished", "failed"), _text(page)[:300]
    finally:
        last = wait_for_job(s, url1)
        if url2 != url1 and url2.split("&", 1)[0] != url1.split("&", 1)[0]:
            wait_for_job(s, url2)
    assert_upload_complete(last)


@needs("admin")
def test_admin_data_job_answers_at_once():
    s, token, _login_id = login("admin")
    t0 = time.monotonic()
    r = s.post(BASE_URL + "/utilData.do", headers={"X-CSRF-Token": token}, data={"action": "taxonFinish"},
               timeout=60, allow_redirects=False)
    seconds = time.monotonic() - t0
    if "Another data job is running" in r.text:
        pytest.skip("staging is busy: another data job is running")
    url = job_url(r)
    assert url, f"utilData.do did not answer with a redirect to jobStatus.do: HTTP {r.status_code} " + _text(r.text)[:300]
    assert seconds < FAST, f"the Run POST took {seconds:.1f} s"
    page = wait_for_job(s, url, timeout=600)
    assert job_status(page.text) == "finished", _text(page.text)[:500]
    assert "taxonFinished" in page.text, _text(page.text)[:500]

    # A curator does not see admin jobs.
    from staging_accounts import has
    if has("uploader"):
        c, _t, _l = login("uploader")
        other = c.get(url.split("&", 1)[0], timeout=TIMEOUT)
        assert other.status_code < 500
        assert job_status(other.text) is None and "Not available." in other.text, _text(other.text)[:300]
