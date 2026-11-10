"""Data extract (dataExtract.do, 2026-11-10): an administrator makes a complete, verified specimen extract for a
collaborator from the website (every specimen of the chosen countries, a genus summary, a manifest whose counts are
checked against the database), as a background job; the files are downloaded through dataExtractFile.do.

- anonymous visitors get the log-in page for the page, for a POST to it and for a download, and the folder the
  extracts are kept in is not served (any host);
- the Curate page links the page, and the folder is under the data logs, which Caddy never serves (static checks);
- staging, a curator who is not an administrator is refused the page, cannot start an extract and gets no file;
- staging, test admin: the page opens with its form; a country AntWeb does not know starts nothing and says so; a
  download of an unknown job or of a name not on the list is a 404;
- staging, test admin: an extract of a small country runs to the end: the job is on "Uploads and data jobs" with
  the country in its title, its page says VERIFIED and shows the manifest, the downloaded specimens.csv.gz has as
  many rows as the manifest says and its sha256 is the manifest's, manifest.json agrees, the zip holds the files,
  the extract is listed on the page; a curator gets none of its files.

The extract only reads the database; it writes its files to the server's extract folder, where they are removed
after 30 days. Skips when staging is busy (the daily update or another extract is running).
"""
import csv
import gzip
import hashlib
import io
import json
import re
import zipfile
from pathlib import Path

import pytest

from antweb import BASE_URL, TIMEOUT, classify, get, new_session
from staging_accounts import has, job_status, job_url, login, needs, wait_for_job

REPO = Path(__file__).resolve().parents[2]
PAGE = "/dataExtract.do"
FILE = "/dataExtractFile.do"
# Small countries, tried in this order until AntWeb knows one (names as on AntWeb's country pages).
SMALL_COUNTRIES = ["Luxembourg", "Liechtenstein", "Andorra", "Malta", "Iceland"]
BUSY = ("The daily update (or another data job) is running", "Another extract is")
FILES = ["specimens.csv.gz", "genus_summary.csv", "manifest.txt", "manifest.json"]


def _text(html):
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", html))


def _is_file(r):
    return r.status_code == 200 and "attachment" in r.headers.get("Content-Disposition", "")


# ---------- anyone ----------

def test_page_and_download_need_login():
    assert get(new_session(), PAGE).category == "login"
    r = new_session().get(BASE_URL + FILE + "?job=1&file=manifest.txt", timeout=TIMEOUT)
    assert classify(r).category == "login" and not _is_file(r), classify(r)
    r = new_session().get(BASE_URL + FILE + "?job=1&file=all.zip", timeout=TIMEOUT)
    assert classify(r).category == "login" and not _is_file(r), classify(r)
    r = new_session().post(BASE_URL + PAGE, data={"countries": SMALL_COUNTRIES[0], "islands": "on"}, timeout=TIMEOUT)
    assert classify(r).category == "login", classify(r)
    assert "jobStatus.do?id=" not in r.url and "Start an extract" not in r.text


@pytest.mark.parametrize("path", ["/log/extracts/", "/log/extracts/2026-11-10-1/manifest.txt",
                                  "/log/extracts/2026-11-10-1/specimens.csv.gz", "/extracts/2026-11-10-1/manifest.txt"])
def test_extract_folder_is_not_served(path):
    r = new_session().get(BASE_URL + path, timeout=TIMEOUT)
    assert r.status_code in (403, 404), f"{path} answered HTTP {r.status_code}"


def test_source_page_linked_and_folder_private():
    links = (REPO / "web" / "curate" / "curatorLinks.jsp").read_text(encoding="utf-8")
    assert '/dataExtract.do">Data extract</a>' in links
    files = (REPO / "src/org/calacademy/antweb/util/DataExtractFiles.java").read_text(encoding="utf-8")
    assert 'AntwebProps.getDataRoot() + "log/extracts"' in files, "the extract folder moved: check that Caddy still refuses it"
    caddy = (REPO / "docker/caddy/Caddyfile.common").read_text(encoding="utf-8")
    denied = caddy.split("@denied_paths {", 1)[1].split("}", 1)[0]
    assert "/log/*" in denied.split(), "Caddy no longer refuses /log/*, where the extracts are kept"
    # the download action takes no path: a job number and a name from the fixed list
    action = (REPO / "src/org/calacademy/antweb/curate/DataExtractFileAction.java").read_text(encoding="utf-8")
    assert "DataExtractFiles.file(root, jobId, name)" in action and "new File(" not in action


# ---------- a curator who is not an administrator ----------

@needs("uploader")
def test_curator_is_refused():
    s, token, _id = login("uploader")
    assert get(s, PAGE).category in ("login", "denied")
    r = s.post(BASE_URL + PAGE, data={"_csrf": token, "countries": SMALL_COUNTRIES[0], "islands": "on"}, timeout=TIMEOUT)
    assert classify(r).category in ("login", "denied"), classify(r)
    assert "jobStatus.do?id=" not in r.url, "a curator started an extract"
    for name in ("manifest.txt", "all.zip"):
        r = s.get(BASE_URL + FILE + f"?job=1&file={name}", timeout=TIMEOUT)
        assert classify(r).category in ("login", "denied") and not _is_file(r), (name, classify(r))


# ---------- the administrator ----------

@pytest.fixture(scope="module")
def admin():
    return login("admin")


@needs("admin")
def test_admin_page_and_refusals(admin):
    s, token, _id = admin
    r = s.get(BASE_URL + PAGE, timeout=TIMEOUT)
    assert r.status_code == 200 and classify(r).category == "ok", classify(r)
    for part in ("<h1>Data extract</h1>", 'id="data-extract-page"', 'id="data-extract-form"', 'name="_csrf"', 'name="countries"',
                 'name="islands" value="on" checked', 'name="bioregion"', 'name="note"', "Start the extract", 'id="data-extract-list"',
                 "has_coordinates = 0", "VERIFIED"):
        assert part in r.text, part
    own = r.text.split('id="data-extract-page"', 1)[1].split('id="data-extract-list"', 1)[0]
    assert "nightly" not in own.lower() and "\u2014" not in own, "the page says daily update, and uses no em-dash"
    assert "no-store" in r.headers.get("Cache-Control", "")
    # a country AntWeb does not know: nothing starts, the page says which name, the form keeps what was typed
    r = s.post(BASE_URL + PAGE, data={"_csrf": token, "countries": "Canada, Atlantis <b>", "islands": "on", "note": "e2e"},
               timeout=TIMEOUT, allow_redirects=False)
    assert r.status_code == 200, r.status_code
    assert "No extract was started." in r.text and "AntWeb has no country named: Atlantis &lt;b&gt;." in r.text
    assert 'value="Canada, Atlantis &lt;b&gt;"' in r.text
    # no country at all
    r = s.post(BASE_URL + PAGE, data={"_csrf": token, "countries": " ", "islands": "on"}, timeout=TIMEOUT, allow_redirects=False)
    assert r.status_code == 200 and "Give at least one country" in r.text
    # a POST without the form token is refused before the page
    r = s.post(BASE_URL + PAGE, data={"countries": SMALL_COUNTRIES[0]}, timeout=TIMEOUT, allow_redirects=False)
    assert r.status_code == 403, r.status_code
    # downloads: an unknown job, a name that is not on the list, a path
    for query in ("?job=999999999&file=manifest.txt", "?job=999999999&file=all.zip", "?file=manifest.txt", "?job=abc&file=manifest.txt",
                  "?job=1&file=request.json", "?job=1&file=../../compute.log", "?job=1&file=%2Fetc%2Fpasswd", "?job=1"):
        r = s.get(BASE_URL + FILE + query, timeout=TIMEOUT)
        assert r.status_code == 404 and not _is_file(r), (query, r.status_code)


def _start(s, token):
    """Starts an extract of the first small country AntWeb knows; (country, job url). Skips when staging is busy."""
    for country in SMALL_COUNTRIES:
        r = s.post(BASE_URL + PAGE, data={"_csrf": token, "countries": country, "islands": "on", "note": "e2e test <b>"},
                   timeout=TIMEOUT, allow_redirects=False)
        url = job_url(r)
        if url:
            return country, url
        assert r.status_code == 200, (country, r.status_code)
        if any(b in r.text for b in BUSY):
            pytest.skip("staging is busy: " + _text(r.text.split('id="data-extract-message"', 1)[1])[:200])
        assert "AntWeb has no country named" in r.text or "Not a current country name" in r.text, _text(r.text)[:600]
    pytest.fail("AntWeb knows none of " + ", ".join(SMALL_COUNTRIES))


@needs("admin")
def test_small_extract_end_to_end(admin):
    s, token, _id = admin
    country, url = _start(s, token)
    job = re.search(r"id=(\d+)", url).group(1)
    assert "already=1" not in url, "another extract was already waiting or running"

    # on "Uploads and data jobs", with the country in its title
    listing = s.get(BASE_URL + "/jobStatus.do", timeout=TIMEOUT).text
    assert f"jobStatus.do?id={job}" in listing and f"Data extract: {country}" in listing

    page = wait_for_job(s, url).text
    assert job_status(page) == "finished", _text(page.split('id="job-status-card"', 1)[-1])[:800]
    assert 'data-verified="true"' in page and ">VERIFIED</span>" in page
    assert "Note: e2e test &lt;b&gt;" in page
    manifest_shown = page.split('<pre id="extract-manifest"', 1)[1].split("</pre>", 1)[0]
    assert "Check against the database: VERIFIED, every count equal" in manifest_shown
    assert f"Countries: {country}" in manifest_shown
    n = int(re.search(r"Specimens in the file: ([\d,]+)", manifest_shown).group(1).replace(",", ""))

    def download(name):
        r = s.get(BASE_URL + FILE + f"?job={job}&file={name}", timeout=max(TIMEOUT, 300))
        assert _is_file(r), (name, r.status_code, r.headers.get("Content-Type"))
        assert re.search(rf'filename="antweb-extract-\d{{4}}-\d\d-\d\d-{job}', r.headers["Content-Disposition"]), r.headers["Content-Disposition"]
        assert "no-store" in r.headers.get("Cache-Control", "")
        return r.content

    for name in FILES + ["all.zip"]:
        assert f"dataExtractFile.do?job={job}&amp;file={name}" in page, f"{name} is not linked on the job page"

    manifest = json.loads(download("manifest.json").decode("utf-8"))
    assert manifest["specimens"] == n and manifest["verified"] is True and manifest["differences"] == []
    assert manifest["countries"] == [country]
    assert sum(p["specimens"] for p in manifest["by_place"]) == n
    assert sum(manifest["by_bioregion"].values()) == n

    gz = download("specimens.csv.gz")
    assert hashlib.sha256(gz).hexdigest() == manifest["files"]["specimens.csv.gz"]["sha256"]
    assert len(gz) == manifest["files"]["specimens.csv.gz"]["bytes"]
    rows = list(csv.reader(io.StringIO(gzip.decompress(gz).decode("utf-8"), newline="")))
    header, rows = rows[0], rows[1:]
    assert len(rows) == n, f"{len(rows)} rows in specimens.csv.gz, the manifest says {n}"
    assert header == manifest["columns"]
    assert header[0] == "code" and header[-8:] == ["taxon_status", "taxon_fossil", "taxon_rank", "taxon_current_valid_name",
                                                  "taxon_author_date", "group_name", "no_taxon_row", "has_coordinates"]
    assert all(len(r) == len(header) for r in rows)
    col = {name: i for i, name in enumerate(header)}
    places = set(manifest["countries"]) | set(manifest["islands"])
    for r in rows:
        assert r[col["country"]] in places or r[col["island_country"]] in places, r[col["code"]]
    assert sum(1 for r in rows if r[col["has_coordinates"]] == "0") == manifest["without_coordinates"]
    assert sum(1 for r in rows if r[col["no_taxon_row"]] == "1") == manifest["no_taxon_row"]
    codes = [r[col["code"]] for r in rows]
    assert codes == sorted(codes) and len(set(codes)) == len(codes)

    text = download("manifest.txt").decode("utf-8")
    assert f"Specimens in the file: {n:,}\n" in text and "VERIFIED, every count equal" in text
    genus = list(csv.DictReader(io.StringIO(download("genus_summary.csv").decode("utf-8"), newline="")))
    assert sum(int(g["specimens"]) for g in genus) == n

    with zipfile.ZipFile(io.BytesIO(download("all.zip"))) as z:
        assert z.namelist() == FILES[:2] + ["manifest.txt", "manifest.json"]
        assert z.read("specimens.csv.gz") == gz
    # no bioregion was asked for: no list file
    assert s.get(BASE_URL + FILE + f"?job={job}&file=bioregion_list.csv", timeout=TIMEOUT).status_code == 404

    # listed on the page, with who started it and the links
    page = s.get(BASE_URL + PAGE, timeout=TIMEOUT).text
    row = page.split(f'<tr id="extract-{job}">', 1)[1].split("</tr>", 1)[0]
    assert f"{country}; with islands" in row and "e2e test &lt;b&gt;" in row and ">VERIFIED</span>" in row
    assert f"{n:,}</td>" in row and f"dataExtractFile.do?job={job}&amp;file=all.zip" in row

    # a curator who is not an administrator gets none of this extract's files
    if has("uploader"):
        c, _token, _cid = login("uploader")
        for name in FILES + ["all.zip"]:
            r = c.get(BASE_URL + FILE + f"?job={job}&file={name}", timeout=TIMEOUT)
            assert not _is_file(r) and classify(r).category in ("login", "denied"), (name, classify(r))
        other = c.get(BASE_URL + f"/jobStatus.do?id={job}", timeout=TIMEOUT)
        assert job_status(other.text) is None and "Not available." in other.text, "a curator can read the extract's job page"
        assert "extract-manifest" not in other.text and "dataExtractFile.do" not in other.text
    # nor does an anonymous visitor
    r = new_session().get(BASE_URL + FILE + f"?job={job}&file=manifest.txt", timeout=TIMEOUT)
    assert not _is_file(r) and classify(r).category == "login"
