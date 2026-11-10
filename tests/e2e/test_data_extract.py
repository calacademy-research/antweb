"""Data extract (dataExtract.do, 2026-11-10): an administrator makes a complete, verified specimen extract for a
collaborator from the website (every specimen of the chosen countries, a genus summary, a manifest whose counts are
checked against the database), as a background job; the files are downloaded through dataExtractFile.do.

- anonymous visitors get the log-in page for the page, for a POST to it and for a download, and no address
  leads to the folder the extracts are kept in (any host);
- the Curate page links the page; the folder is beside Tomcat's logs, outside the web application's folder, and
  both Caddy files still refuse /log/ (static checks: compose mounts, Tomcat's docBase, the Caddy files);
- staging, a curator who is not an administrator is refused the page, cannot start an extract and gets no file;
- staging, test admin: the page opens with its form; a country AntWeb does not know starts nothing and says so; a
  GET with the form's parameters starts nothing; a download of an unknown job or of a name not on the list is a 404;
- staging, test admin: an extract of a small country runs to the end: the job is on "Uploads and data jobs" with
  the country in its title, its page says VERIFIED and shows the manifest, the downloaded specimens.csv.gz has as
  many rows as the manifest says and its sha256 is the manifest's, manifest.json agrees, the zip holds the files,
  the extract is listed on the page; a curator gets none of its files; the extract's real folder cannot be
  reached by any address (plain, upper case, doubled slash, encoded slash, dot segments); a GET does not delete
  it; the Delete button removes it.

The extract only reads the database; it writes its files to the server's extract folder and the test deletes them
at its end. Skips when staging is busy (the daily update or another extract is running).
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
import requests

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


def _raw_get(session, path):
    """GET of the path exactly as written (requests would tidy doubled slashes and dot segments away)."""
    req = session.prepare_request(requests.Request("GET", BASE_URL + "/"))
    req.url = BASE_URL + path
    return session.send(req, timeout=TIMEOUT, allow_redirects=True)


def folder_addresses(folder):
    """Every address the extract folder could be guessed under: where it is now (beside Tomcat's logs), where it
    was first built (under the data logs), and the ways around a path rule (review M1)."""
    out = []
    for name in ("manifest.txt", "specimens.csv.gz", "request.json", ""):
        for base in ("/logs/extracts", "/log/extracts", "/extracts", "/web/log/extracts", "/usr/local/tomcat/logs/extracts"):
            out.append(f"{base}/{folder}/{name}")
    out += [f"/LOG/extracts/{folder}/manifest.txt", f"/Logs/Extracts/{folder}/manifest.txt",
            f"//log/extracts/{folder}/manifest.txt", f"//logs//extracts/{folder}/manifest.txt",
            f"/log%2Fextracts/{folder}/manifest.txt", f"/logs%2Fextracts%2F{folder}%2Fmanifest.txt",
            f"/web/../log/extracts/{folder}/manifest.txt", f"/web/../logs/extracts/{folder}/manifest.txt",
            f"/web/log/../../logs/extracts/{folder}/manifest.txt", f"/..%2Flogs/extracts/{folder}/manifest.txt",
            f"/%2e%2e/tomcat/logs/extracts/{folder}/manifest.txt", f"/web/log/%2e%2e/%2e%2e/log/extracts/{folder}/manifest.txt",
            f"/log/extracts/{folder}/manifest.txt;x=1", f"/logs/extracts/{folder}/manifest.txt?x=1.do"]
    return out


def assert_folder_unreachable(session, folder, who):
    for path in folder_addresses(folder):
        r = _raw_get(session, path)
        assert r.status_code in (400, 403, 404), f"{who}: {path} answered HTTP {r.status_code}"
        assert "AntWeb specimen extract" not in r.text and "tool_version" not in r.text, f"{who}: {path} shows a file of the extract"
        assert "attachment" not in r.headers.get("Content-Disposition", ""), f"{who}: {path} is a download"


def test_extract_folder_is_not_served():
    """A folder name that could exist; the test of the real folder is in test_small_extract_end_to_end."""
    assert_folder_unreachable(new_session(), "2026-11-10-1", "anonymous")


def test_source_page_linked_and_folder_private():
    links = (REPO / "web" / "curate" / "curatorLinks.jsp").read_text(encoding="utf-8")
    assert '/dataExtract.do">Data extract</a>' in links
    # Review M1: the folder is outside the web application's folder, so no address reaches it even past Caddy.
    files = (REPO / "src/org/calacademy/antweb/util/DataExtractFiles.java").read_text(encoding="utf-8")
    assert 'AntwebProps.getTomcatDir() + "logs/extracts"' in files, "the extract folder moved: check that it is still outside the web root"
    props = (REPO / "src/org/calacademy/antweb/util/AntwebProps.java").read_text(encoding="utf-8")
    assert re.search(r'getTomcatDir\(\)\s*\{\s*return "/usr/local/tomcat/";', props)
    assert re.search(r'getDocRoot\(\)\s*\{[^}]*return "/usr/local/antweb/";', props)
    server = (REPO / "docker/antweb/tomcat/conf/server.xml").read_text(encoding="utf-8")
    assert re.findall(r'<Context [^>]*docBase="([^"]*)"', server) == ["/data/antweb"], "Tomcat serves another folder: is the extract folder in it?"
    assert "ln -s /usr/local/antweb /data/antweb" in (REPO / "docker/antweb/Dockerfile").read_text(encoding="utf-8")
    compose = (REPO / "docker-compose.yml").read_text(encoding="utf-8")
    # on the host (kept when the container is rebuilt); only logs/web of it is inside the web application
    assert "- ./logs:/usr/local/tomcat/logs" in compose and "- ./logs/web:/usr/local/antweb/web/log" in compose
    assert not re.search(r"-\s*\./logs(/extracts)?:/usr/local/antweb", compose)
    # both Caddy files (production; staging and dev through Caddyfile.common) still refuse the data logs
    for name in ("Caddyfile", "Caddyfile.common"):
        caddy = (REPO / "docker/caddy" / name).read_text(encoding="utf-8")
        denied = caddy.split("@denied_paths {", 1)[1].split("}", 1)[0]
        assert {"/log", "/log/*", "/WEB-INF/*"} <= set(denied.split()), f"{name} no longer refuses /log/*"
        assert 'respond @denied_paths "Not found" 404' in caddy
    for name in ("Caddyfile.staging", "Caddyfile.dev"):
        assert "import Caddyfile.common" in (REPO / "docker/caddy" / name).read_text(encoding="utf-8")
    # Tomcat's log tidying never walks the folder
    assert "'extracts'" in (REPO / "bin/clearDisk.py").read_text(encoding="utf-8").split("SKIP_DIRS = ", 1)[1].split("\n", 1)[0]
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
    # review: a GET with the form's parameters starts nothing (it only shows the form filled in)
    before = _latest_job(s)
    r = s.get(BASE_URL + PAGE, params={"countries": SMALL_COUNTRIES[0], "islands": "on", "note": "by link", "op": "start"},
              timeout=TIMEOUT, allow_redirects=False)
    assert r.status_code == 200 and "Location" not in r.headers, (r.status_code, r.headers.get("Location"))
    assert 'id="data-extract-form"' in r.text and f'value="{SMALL_COUNTRIES[0]}"' in r.text
    assert _latest_job(s) == before, "a GET started a job"
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


def _latest_job(s):
    """The highest job number on Uploads and data jobs (0 when there is none)."""
    listing = s.get(BASE_URL + "/jobStatus.do", timeout=TIMEOUT).text
    return max([int(n) for n in re.findall(r"jobStatus\.do\?id=(\d+)", listing)] or [0])


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

    # Review M1: the extract's real folder (its name is in the download's file name) cannot be reached by any address
    disposition = s.get(BASE_URL + FILE + f"?job={job}&file=manifest.txt", timeout=TIMEOUT).headers["Content-Disposition"]
    folder = re.search(rf"antweb-extract-(\d{{4}}-\d\d-\d\d-{job})-manifest\.txt", disposition).group(1)
    assert_folder_unreachable(new_session(), folder, "anonymous")
    assert_folder_unreachable(s, folder, "the administrator")

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

    # Delete: a link (GET) removes nothing; the button (POST with the form token) removes the files, not the job's record
    r = s.get(BASE_URL + PAGE, params={"op": "delete", "job": job}, timeout=TIMEOUT)
    assert f'<tr id="extract-{job}">' in r.text, "a GET deleted the extract"
    assert f'name="op" value="delete"><input type="hidden" name="job" value="{job}">' in r.text, "no Delete button for the extract"
    r = s.post(BASE_URL + PAGE, data={"_csrf": token, "op": "delete", "job": job}, timeout=TIMEOUT)
    assert r.status_code == 200 and f"The files of extract {job} were removed from the server." in r.text, _text(r.text)[:400]
    assert f'<tr id="extract-{job}">' not in r.text
    for name in FILES + ["all.zip"]:
        assert s.get(BASE_URL + FILE + f"?job={job}&file={name}", timeout=TIMEOUT).status_code == 404, name
    assert job_status(s.get(url, timeout=TIMEOUT).text) == "finished", "the job's record is gone with its files"
