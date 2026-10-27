"""The numbers of the admin and upload report pages (docs/numbers.md rows 83, 94, 95, 96, 98), 2026-10-27.

  row 83  curator.do "Last Upload": Specimen, Ungeoreferenced, Flagged = the numbers of the upload's group that the
          count check compared with a recount of the group's specimens (admin.groups; the same page numbers as
          groups.do and group.do, test_count_check.py); a curator with specimen uploads shows a Last Upload.
  row 94  imageUploadReport.do: "Images" = the batch's Image Count on listImageUploads.do = the files the report lists
          (a complete batch); "Total Images Uploaded" = the files listed as saved (linked to their image page);
          "Not saved" = the files listed as not saved; the list's "holding M images" = its Image Counts added up.
  row 95  specimen upload report "Inserted Species", "Updated Species": each species or subspecies name once, so
          together at most the distinct names of the uploaded file (B60: a new name was counted as updated and
          "Inserted Species" never shown; B61: a name was counted once per run of its lines). Reports are files
          written once: a report of today's rule carries id="report-counts-rule"; older ones are held to the old
          bound (at most the lines processed).
  row 96  specimen upload report counts: Record Count = the lines after the header of the uploaded file (B62: counted
          the header too), Parsed <= Record Count, Processed <= Parsed, Inserted Specimens <= Processed; each report
          line's "(total:N)": the lines of a section listed by key = N, a section of values lists at most N values and
          at most N specimens ("M specimens" of its lines); the Inserted Specimens of the test group's last upload =
          the group's specimens (statistics.do by upload, and its zztest codes found through the API).
  row 98  statistics.do breakdowns: byUpload: the specimens of each upload group, at most the current specimen count
          together; the test group's = its specimens found through the API. byProject, byBioregion, byMuseum,
          byGeolocale: on the day's row of each, every number = the names its taxaList.do link lists (up to 998;
          the list shows at most 999), All = Extinct + Extant, Valid and Imaged valid species at most All species
          (B63: the Imaged v. sp. link listed names imaged anywhere; B64: geolocale Imaged v. sp. counted every rank,
          and the links named the place by its name, which other places share).

Staging only (admin test login, read-only: nothing is uploaded or changed here; the upload round trip,
test_specimen_upload_roundtrip.py, makes the test group's reports). Pages too slow for the request (over Cloudflare's
limit) are skipped with the reason.
"""
import io
import re
import zipfile
from html import unescape
from urllib.parse import quote, unquote_plus

import pytest

import numbers_sample as ns
from antweb import BASE_URL, TIMEOUT
from staging_accounts import CODE_PREFIX, GROUP_ABBREV, GROUP_NAME, IS_STAGING, code, login, needs

pytestmark = pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only")

SLOW = max(TIMEOUT, 180)
MAX_FILE_BYTES = 5_000_000      # uploaded files read to recount their lines; larger ones are left out
TEST_CODES = 9                  # the test group's codes: zztest0001 to zztest0007 (test_specimen_upload_roundtrip.py)
COUNTS_RULE = "2026-10-27"      # UploadDetails.COUNTS_RULE

_ADMIN = {}


def admin():
    if "s" not in _ADMIN:
        _ADMIN["s"] = login("admin")[0]
    return _ADMIN["s"]


def page(s, path, timeout=SLOW):
    """The page's HTML, or skip when it cannot be read (slow, error)."""
    url = path if path.startswith("http") else BASE_URL + path
    try:
        r = s.get(url, timeout=timeout)
    except Exception as e:   # requests' timeouts and connection errors
        pytest.skip(f"{path}: {type(e).__name__} (slower than {timeout:.0f} s?)")
    if r.status_code != 200:
        pytest.skip(f"{path}: HTTP {r.status_code}")
    return r.text


def num(text):
    t = re.sub(r"[^\d]", "", unescape(re.sub(r"<[^>]+>", "", text or "")))
    return int(t) if t else None


# ----------------------------------------------------------------------------------------------- row 83

def _curator_last_upload(html):
    """(report file name or None, counted note shown, [Specimen, Ungeoreferenced, Flagged]) of curator.do's Last
    Upload, or None when the page shows none (curator-body.jsp)."""
    i = html.find("<li><b>Last Upload:</b></li>")
    if i < 0:
        return None
    block = html[i: html.find("<li><b>Image Uploads:</b></li>", i)]
    link = re.search(r"href='[^']*/web/log/[^']*/([^'/]+)'", block)
    values = []
    for label in ("Specimen", "Ungeoreferenced", "Flagged"):
        m = re.search(r"&nbsp;&nbsp;" + label + r": ([\d,]+)</li>", block)
        values.append(num(m.group(1)) if m else None)
    return (unquote_plus(unescape(link.group(1))) if link else None,
            'id="curator-group-numbers-note"' in block, values)


def _report_groups(s):
    """Upload report file name -> group id, and the test group's id, from the list of every group's uploads."""
    html = page(s, "/listSpecimenUploads.do")
    groups, test_gid = {}, None
    for row in html.split("<tr>")[1:]:
        g = re.search(r'listUploads\.do\?groupId=(\d+)">([^<]*)</a>', row)
        r = re.search(r'/web/log/specimen/([^"\'<>]+)"', row)
        if not g:
            continue
        if g.group(2).strip() == GROUP_ABBREV:
            test_gid = g.group(1)
        if r:
            groups.setdefault(unquote_plus(unescape(r.group(1))), g.group(1))
    return groups, test_gid


@needs("admin")
def test_curator_last_upload_numbers_equal_the_checked_group_numbers():
    """Row 83: curator.do Last Upload Specimen, Ungeoreferenced, Flagged = the numbers of the upload's group the count
    check compared with its recount (test_count_check.py, admin.groups), on the curator with the most uploads and a
    day's sample of the others; a curator with specimen uploads shows a Last Upload."""
    import test_count_check as cc
    s, exp = cc.expected_numbers()
    curators, groups = exp.get("curators") or {}, exp.get("groups") or {}
    if not curators or not groups:
        pytest.skip("the count check has no curator or group numbers (LoginMgr not loaded, or no upload groups)")
    with_uploads = sorted((cid for cid, v in curators.items() if v and v[0] > 0), key=int)
    if not with_uploads:
        pytest.skip("no curator with specimen uploads")
    sample = [max(with_uploads, key=lambda c: curators[c][0])] + ns.pick(with_uploads, 5, "curator-last-upload")
    report_groups, test_gid = _report_groups(s)
    wrong, checked, pending = [], 0, []
    for cid in dict.fromkeys(sample):
        html = page(s, "/curator.do?id=" + cid, timeout=TIMEOUT)
        last = _curator_last_upload(html)
        if last is None:
            wrong.append(f"curator.do?id={cid}: {curators[cid][0]} specimen uploads but no Last Upload")
            continue
        report, counted, shown = last
        if not counted:
            pending.append(cid)   # the group has not been recounted by a nightly run: its last upload row is shown
            continue
        gid = report_groups.get(report) if report else None
        if gid is None:
            g = re.search(r"group\.do\?id=(\d+)", html)
            gid = g.group(1) if g else None
        if gid is None or gid == test_gid:
            continue
        want = groups.get(gid)
        if want is None:
            if any(shown):
                wrong.append(f"curator.do?id={cid}: Last Upload {shown} of group {gid}, which the check did not count")
            continue
        checked += 1
        if shown != [want[1], want[2], want[3]]:
            wrong.append(f"curator.do?id={cid} Last Upload (group {gid}) Specimen, Ungeoreferenced, Flagged {shown}; "
                         f"checked {want[1:4]}")
    assert not wrong, "curator.do Last Upload numbers" + cc.UPLOAD_NOTE + ":\n" + "\n".join(wrong) + "\n" + ns.day_note()
    if not checked:
        pytest.skip(f"no sampled curator's group counted yet (pending: {pending}) " + ns.day_note())


# ----------------------------------------------------------------------------------------------- row 94

def _image_report(html):
    """The numbers and rows of imageUploadReport.do (imageUploadReport-body.jsp)."""
    def li(label):
        m = re.search(r"<li><b>" + re.escape(label) + r"\s*</b></li>\s*<li>(.*?)</li>", html, re.S)
        return m.group(1).strip() if m else None
    start = html.find("<li><b>Images:</b></li>")
    ends = [html.find(k, start) for k in ('id="notSaved"', "Uploaded Specimen Set", "No Specimen Data Set", "<b>Images Dir</b>")]
    ends = [e for e in ends if e > 0]
    files = html[start: min(ends) if ends else len(html)] if start >= 0 else ""
    not_saved_link = re.search(r'<li><b>Not saved:</b></li>\s*<li><a href="#notSaved">(\d+)</a></li>', html)
    return {
        "complete": li("Upload Completed:"),
        "total": num(li("Total Images Uploaded:")),
        "images": num(li("Images:")),
        "rows": len(re.findall(r"<li><b>\d+:</b></li>", files)),
        "saved_rows": files.count("bigPicture.do?code="),
        "not_saved": int(not_saved_link.group(1)) if not_saved_link else 0,
        "not_saved_rows": html.count('class="not-saved-name"'),
    }


@needs("admin")
def test_image_upload_report_counts_equal_their_files():
    """Row 94: on the newest image upload batch and a day's sample of the others, the report's Images = the batch's
    Image Count on listImageUploads.do = the files the report lists (complete batches); Total Images Uploaded = the
    files listed as saved, at most Images; Not saved = the files listed as not saved; and the list's total images =
    its Image Counts added up."""
    s = admin()
    html = page(s, "/listImageUploads.do")
    batches = re.findall(r"<td>(true|false)</td>\s*<td class=\"aw-num\">(\d+)</td>\s*"
                         r"<td class=\"aw-nowrap\"><a href='[^']*imageUploadReport\.do\?id=(\d+)'>", html)
    if not batches:
        pytest.skip("listImageUploads.do lists no image upload batch")
    wrong = []
    total = re.search(r"holding ([\d,]+) images", html)
    if total and num(total.group(1)) != sum(int(c) for _, c, _ in batches):
        wrong.append(f"listImageUploads.do holding {total.group(1)} images; Image Counts add up to "
                     f"{sum(int(c) for _, c, _ in batches)}")
    for complete, count, bid in [batches[0]] + ns.pick(batches[1:], 3, "image-upload-report"):
        report = page(s, "/imageUploadReport.do?id=" + bid)
        rep = _image_report(report)
        where = f"imageUploadReport.do?id={bid}"
        if rep["images"] is None:
            # B74: every report of a batch from another year than the newest copyright row failed (error page).
            title = re.search(r"<title>(.*?)</title>", report, re.S)
            title = " ".join(title.group(1).split()) if title else None
            wrong.append(f"{where}: no Images number (page title {title!r})")
            continue
        if rep["images"] != int(count):
            wrong.append(f"{where}: Images {rep['images']}, listImageUploads.do Image Count {count}")
        if complete == "true" and rep["images"] != rep["rows"]:
            wrong.append(f"{where}: Images {rep['images']}, {rep['rows']} files listed")
        if rep["total"] is not None:
            if rep["total"] != rep["saved_rows"]:
                wrong.append(f"{where}: Total Images Uploaded {rep['total']}, {rep['saved_rows']} files listed as saved")
            if rep["total"] > rep["rows"]:
                wrong.append(f"{where}: Total Images Uploaded {rep['total']} > the {rep['rows']} files listed")
        if rep["not_saved"] != rep["not_saved_rows"]:
            wrong.append(f"{where}: Not saved {rep['not_saved']}, {rep['not_saved_rows']} files listed as not saved")
    assert not wrong, "image upload report numbers:\n" + "\n".join(wrong) + "\n" + ns.day_note()


# ----------------------------------------------------------------------------------------------- rows 95, 96

def report_numbers(html):
    """The numbers of a specimen upload report (UploadDetails.genUploadReport)."""
    def b(label):
        m = re.search(r"<b>" + re.escape(label) + r":</b>\s*([\d,]+)", html)
        return num(m.group(1)) if m else None
    op = re.search(r"<b>Operation: ([A-Za-z]+)</b>", html)
    return {"operation": op.group(1) if op else None, "record": b("Record Count"), "parsed": b("Parsed"),
            "processed": b("Processed"), "inserted_specimens": b("Inserted Specimens"),
            "inserted_species": b("Inserted Species") or 0, "updated_species": b("Updated Species") or 0,
            "new_rule": f'id="report-counts-rule" hidden>{COUNTS_RULE}' in html}


# Report sections listed by key (MessageMgr MAP tests): "(total:N)" = the lines of the section.
MAP_HEADINGS = ("Invalid Subfamily for Genus.  Submitted subfamily replaced", "Taxon Subfamily / Genus combinations not found",
                "Genus found outside of known native biogeographic region", "Data field should not be in quotations",
                "Duplicated record")


def message_problems(html):
    """Each report line with a "(total:N)" (MessageMgr.getMessagesReport, Test.toString)."""
    i, j = html.find("<h3>Errors:</h3>"), html.find("<h3>Passed Tests:</h3>")
    if i < 0:
        return []
    section = html[i: j if j > i else len(html)]
    problems = []
    for msg in re.split(r"<b>\d+</b>:", section)[1:]:
        m = re.search(r"\(total:([\d,]+)\)", msg)
        if not m:
            continue
        total = num(m.group(1))
        heading = re.sub(r"<[^>]+>", "", msg[: m.start()]).replace("&nbsp;", " ").strip()[:90]
        body = msg[m.end():]
        keyed = len(re.findall(r"<br>&nbsp;&nbsp;<b>", body))
        lines = len(re.findall(r"<br>&nbsp;&nbsp;", body))
        if any(h in msg[: m.start()] for h in MAP_HEADINGS):
            # by key: "(total:N)" counts the keys (Test.addCount), one line each
            if keyed != total:
                problems.append(f"{heading!r}: total {total}, {keyed} lines")
        elif lines and keyed < lines:
            # a list of values, one per line: "(total:N)" counts every report of a value (Test.count), so at least the
            # lines, and at least the specimens of its lines (each specimen is listed once per line)
            if lines > total:
                problems.append(f"{heading!r}: total {total}, {lines} lines listed")
            specimens = sum(num(c) for c in re.findall(r"<summary>([\d,]+) specimens?</summary>", body))
            # (Morpho Genera: one report per genus, its specimens listed afterwards, MessageMgr.addCode)
            if specimens > total and "Morpho Genera" not in msg[: m.start()]:
                problems.append(f"{heading!r}: total {total}, its lines list {specimens} specimens")
        # values on one line ("a, b"): "(total:N)" counts the distinct values (shown when there are two or more)
    return problems


def uploaded_file(s, row_html):
    """The uploaded file of an upload list row (uploadFile.do), as text, or None (no link, too large, binary)."""
    m = re.search(r'href="([^"]*uploadFile\.do\?file=[^"]+)"', row_html)
    if not m:
        return None
    try:
        r = s.get(unescape(m.group(1)), timeout=SLOW, stream=True)
        if r.status_code != 200:
            return None
        data = r.raw.read(MAX_FILE_BYTES + 1, decode_content=True)
    except Exception:
        return None
    if len(data) > MAX_FILE_BYTES:
        return None
    if data[:2] == b"PK":
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as z:
                names = [n for n in z.namelist() if not n.endswith("/")]
                if len(names) != 1 or z.getinfo(names[0]).file_size > MAX_FILE_BYTES:
                    return None
                data = z.read(names[0])
        except zipfile.BadZipFile:
            return None
    if b"\x00" in data:
        return None   # UTF-16 or binary: lines not recounted here
    return data.decode("utf-8", errors="replace")


def file_lines(text):
    """The lines as Java's BufferedReader.readLine reads them (\\n, \\r or \\r\\n end a line)."""
    lines = re.split(r"\r\n|\r|\n", text)
    if lines and lines[-1] == "":
        lines.pop()
    return lines


def distinct_names(lines):
    """The distinct taxon columns (every column whose header names a family, genus or species part) of the data lines
    with a species, or None when the header has no genus or species column."""
    header = [h.strip().strip('"').lower() for h in lines[0].split("\t")]
    cols = [k for k, h in enumerate(header) if any(w in h for w in ("family", "genus", "species"))]
    species = [k for k, h in enumerate(header) if h in ("species", "speciesname", "[species]species")]
    if not cols or not species:
        return None
    names = set()
    for line in lines[1:]:
        cells = line.split("\t")
        if len(cells) > species[0] and cells[species[0]].strip():
            names.add(tuple(cells[k].strip().lower() if k < len(cells) else "" for k in cols))
    return len(names)


# Statuses of names AntWeb keeps that AntCat does not give (Status.java); an upload writes rows of these names only:
# a name AntCat gives (worldants) is never rewritten by a specimen upload (AntwebUpload.updateTaxon).
NOT_ANTCAT = {None, "unrecognized", "morphotaxon", "indetermined"}


def expected_species(lines):
    """The species and subspecies names of a small file that an upload writes to the taxon table: the distinct names
    (subfamily + genus, species[, subspecies]) that AntCat does not give (API /taxa status), or None when a name
    cannot be made (a column missing or empty)."""
    header = [h.strip().strip('"').lower() for h in lines[0].split("\t")]
    def col(*names):
        return next((k for k, h in enumerate(header) if h in names), None)
    sf, g, sp, ssp = col("subfamily"), col("genus", "[species]genus"), col("species", "speciesname"), col("subspecies")
    if None in (sf, g, sp):
        return None
    names = set()
    for line in lines[1:]:
        cells = [c.strip().strip('"').lower() for c in line.split("\t")]
        if len(cells) <= max(sf, g, sp) or not cells[sp]:
            continue
        if not cells[sf] or not cells[g]:
            return None
        name = cells[sf] + cells[g] + " " + cells[sp]
        if ssp is not None and ssp < len(cells) and cells[ssp]:
            name += " " + cells[ssp]
        names.add(name)
    count = 0
    for name in names:
        body = ns.api("taxa?taxonName=" + quote(name))
        if body is None:
            return None
        taxa = ns.api_rows(body)
        if (taxa[0].get("status") if taxa else None) in NOT_ANTCAT:
            count += 1
    return count


def upload_rows(s):
    """[(group abbrev, report url, row html)] of the list of every group's uploads, newest first."""
    html = page(s, "/listSpecimenUploads.do")
    rows = []
    for row in html.split("<tr>")[1:]:
        g = re.search(r'listUploads\.do\?groupId=\d+">([^<]*)</a>', row)
        r = re.search(r'href="([^"]*/web/log/specimen/[^"]+)"', row)
        if g and r:
            url = unescape(r.group(1))
            rows.append((g.group(1).strip(), url if url.startswith("http") else BASE_URL + url, row))
    return rows


@needs("admin")
def test_upload_report_counts_agree_with_the_uploaded_file():
    """Rows 95, 96: on the test group's newest specimen upload reports and a day's sample of the newest 40 reports,
    the report's counts against each other and against its uploaded file (the file is read when it is at most
    5 MB): Record Count = the lines after the header, Parsed <= Record Count, Processed <= Parsed, Inserted
    Specimens <= Processed, Inserted + Updated Species <= the file's distinct names and, for the test group's files,
    = its names that AntCat does not give (reports of the old rule: <= the lines processed), and each line's
    "(total:N)" against the lines and specimens it lists."""
    s = admin()
    rows = upload_rows(s)
    if not rows:
        pytest.skip("listSpecimenUploads.do lists no upload report")
    test_rows = [r for r in rows[:20] if r[0] == GROUP_ABBREV][:6] or [r for r in rows if r[0] == GROUP_ABBREV][:1]
    sample = test_rows + ns.pick([r for r in rows[:40] if r[0] != GROUP_ABBREV], 2, "upload-report")
    wrong, checked = [], 0
    for abbrev, url, row in sample:
        html = page(s, url)
        rep = report_numbers(html)
        where = f"{abbrev} {url.rsplit('/', 1)[-1]}"
        if rep["operation"] != "specimenUpload" or None in (rep["record"], rep["parsed"], rep["processed"]):
            continue
        checked += 1
        if not (rep["parsed"] <= rep["record"] and rep["processed"] <= rep["parsed"]):
            wrong.append(f"{where}: Record Count {rep['record']}, Parsed {rep['parsed']}, Processed {rep['processed']}")
        if rep["inserted_specimens"] is not None and rep["inserted_specimens"] > rep["processed"]:
            wrong.append(f"{where}: Inserted Specimens {rep['inserted_specimens']} > Processed {rep['processed']}")
        species = rep["inserted_species"] + rep["updated_species"]
        if species > rep["processed"]:
            wrong.append(f"{where}: Inserted + Updated Species {species} > Processed {rep['processed']}")
        wrong += [f"{where}: {p}" for p in message_problems(html)]
        text = uploaded_file(s, row)
        if text is None:
            continue
        lines = file_lines(text)
        data_lines = len(lines) - 1
        if rep["new_rule"]:
            if rep["record"] != data_lines:
                wrong.append(f"{where}: Record Count {rep['record']}, the file has {data_lines} lines after its header")
            names = distinct_names(lines)
            if names is not None and species > names:
                wrong.append(f"{where}: Inserted + Updated Species {species}, the file has {names} distinct names")
            # the test group's files (a few lines): exactly the names AntCat does not give, each once
            exact = expected_species(lines) if abbrev == GROUP_ABBREV and data_lines <= 20 else None
            if exact is not None and species != exact:
                wrong.append(f"{where}: Inserted {rep['inserted_species']} + Updated {rep['updated_species']} Species; "
                             f"the file has {exact} names AntCat does not give")
        elif rep["record"] not in (data_lines, data_lines + 1):
            wrong.append(f"{where}: Record Count {rep['record']} (rule before {COUNTS_RULE}: the lines after the "
                         f"header, plus one), the file has {data_lines}")
        if rep["parsed"] > len([ln for ln in lines[1:] if ln.strip()]):
            wrong.append(f"{where}: Parsed {rep['parsed']} > the file's non-empty lines")
    assert not wrong, "upload report counts:\n" + "\n".join(wrong[:40]) + "\n" + ns.day_note()
    if not checked:
        pytest.skip("no specimen upload report in the sample " + ns.day_note())


# ----------------------------------------------------------------------------------------------- row 98

def _by_upload(html):
    """statistics.do?byUpload=true: group id -> (specimens, name)."""
    return {gid: (int(c), unescape(name).strip())
            for c, name, gid in re.findall(r"<tr><td>(\d+)</td><td>(.*?)\((\d+)\)</td></tr>", html, re.S)}


def _test_group_specimens_in_api():
    found = 0
    for n in range(1, TEST_CODES + 1):
        body = ns.api(f"specimens?specimenCode={code(n)}")
        if body is None:
            return None
        found += len([r for r in ns.api_rows(body) if (r.get("specimenCode") or "").lower().startswith(CODE_PREFIX)])
    return found


@needs("admin")
def test_statistics_by_upload_counts_each_groups_specimens():
    """Row 98 (byUpload), row 96 (Inserted Specimens): statistics.do?byUpload=true lists each upload group once, its
    specimens together at most statistics.do's current specimen count; the test group's = its specimens found through
    the API (codes zztest0001 to zztest0009) = the Inserted Specimens of its newest upload report (an upload replaces
    the group's specimens)."""
    s = admin()
    rows = _by_upload(page(s, "/statistics.do?byUpload=true"))
    if not rows:
        pytest.skip("statistics.do?byUpload=true lists no group")
    main = page(s, "/statistics.do")
    m = re.search(r"current specimen count:<b>(\d+)</b>", main)
    wrong = []
    if m and sum(c for c, _ in rows.values()) > int(m.group(1)):
        wrong.append(f"by upload: {sum(c for c, _ in rows.values())} specimens together, current specimen count {m.group(1)}")
    test = [c for c, name in rows.values() if name == GROUP_NAME]
    in_group = test[0] if test else 0
    api = _test_group_specimens_in_api()
    if api is not None and api != in_group:
        wrong.append(f"by upload: test group {in_group} specimens; the API finds {api} of its codes")
    newest = [r for r in upload_rows(s) if r[0] == GROUP_ABBREV][:1]
    if newest:
        rep = report_numbers(page(s, newest[0][1]))
        if rep["inserted_specimens"] is not None and rep["inserted_specimens"] != in_group:
            wrong.append(f"by upload: test group {in_group} specimens; its newest upload report inserted "
                         f"{rep['inserted_specimens']} ({newest[0][1]})")
    assert not wrong, "statistics.do by upload:\n" + "\n".join(wrong)


BREAKDOWNS = ("byProject", "byBioregion", "byMuseum", "byGeolocale")
LIST_MAX = 998   # taxaList.do lists at most 999 names (list.jsp stops at 1000, the query at 1000)


def _breakdown_rows(html):
    """statisticsRealTime-body.jsp: [(place, [(number, taxaList.do url)] * 14)] (links on)."""
    out = []
    for row in html.split("<tr>")[1:]:
        name = re.search(r"<td>(.*?)</td>", row, re.S)
        links = re.findall(r'<td><a href="([^"]*taxaList\.do\?[^"]*)">(\d+)</a></td>', row)
        if name and links:
            out.append((unescape(name.group(1)).strip(), [(int(n), unescape(u)) for u, n in links]))
    return out


def _list_count(url):
    html = ns.fetch(url)
    if html is None:
        return None
    return len(re.findall(r"<br>\d+\. <a ", html))


@needs("admin")
@pytest.mark.parametrize("breakdown", BREAKDOWNS)
def test_statistics_breakdown_numbers_equal_their_lists(breakdown):
    """Row 98: on the day's row of statistics.do?<breakdown>=true, All subfamilies, genera, species = Extinct +
    Extant, Valid and Imaged valid species at most All, and every number up to 998 = the names its taxaList.do link
    lists (B63: the Imaged v. sp. link listed names imaged anywhere; B64: geolocale rows counted imaged names of every
    rank and linked the place by a name other places share)."""
    s = admin()
    rows = _breakdown_rows(page(s, f"/statistics.do?{breakdown}=true", timeout=SLOW))
    if not rows:
        pytest.skip(f"statistics.do?{breakdown}=true lists no row")
    wrong, checked = [], 0
    for place, cells in ns.pick(rows, 1, "statistics-" + breakdown) + ([rows[-1]] if len(rows) > 1 else []):
        if len(cells) < 14:
            wrong.append(f"{breakdown} {place}: {len(cells)} numbers, 14 expected")
            continue
        v = [n for n, _ in cells]
        # 0 extinct subf, 1 extant, 2 valid, 3 all; 4..7 genera; 8 extinct sp, 9 extant, 10 valid, 11 imaged valid, 12 all
        for name, all_, parts in (("subfamilies", 3, (0, 1)), ("genera", 7, (4, 5)), ("species", 12, (8, 9))):
            if v[all_] != v[parts[0]] + v[parts[1]]:
                wrong.append(f"{breakdown} {place}: All {name} {v[all_]} != Extinct {v[parts[0]]} + Extant {v[parts[1]]}")
        if v[11] > v[10]:
            wrong.append(f"{breakdown} {place}: Imaged v. sp. {v[11]} > V. sp. {v[10]}")
        for n, url in cells:
            if n > LIST_MAX:
                continue
            listed = _list_count(url)
            if listed is None:
                continue
            checked += 1
            if listed != n:
                wrong.append(f"{breakdown} {place}: {n}, its list {url.split('taxaList.do', 1)[-1]} names {listed}")
    assert not wrong, f"statistics.do?{breakdown}=true:\n" + "\n".join(wrong[:40]) + "\n" + ns.day_note()
    if not checked:
        pytest.skip(f"no number of statistics.do?{breakdown}=true checked against its list " + ns.day_note())
