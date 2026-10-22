"""Count check (countCheck.do): the numbers on taxon lists against a recount from the specimen records.

Brian Fisher, 2026-10: "we need to make sure we have a test that would validate the counts based on
real specimen data to make sure these counts are true". The page (administrators, read-only) recounts,
straight from the specimen and taxon tables, for Madagascar, Reunion, the country with the most
morphospecies, the subregion of Madagascar (since 2026-10-20: the specimens of its countries; its
differences are pending until a nightly run has counted it that way), the largest live curated project,
the Malagasy bioregion and the CASC museum: the place's own Specimens, Imaged specimens and Images, the specimens
of each species in the place, the species of each genus and the genera of each subfamily in the list,
and the "Global" specimens of each species (rules: src/org/calacademy/antweb/util/CountCheck.java).
Since 2026-10-21 also today's rotating slice of all places (every place within a week, adm1s within a month)
and, for every place, the numbers that must agree (CountInvariants; docs/numbers.md lists every number on
the site and the check or test that covers it).

Rule for failing: the site stores the counts in the nightly run (05:00 to 08:15 Pacific). A difference
caused by a change after the last nightly run (an upload, a list edit) is "pending" and allowed; any
other difference is "real" and fails this test with both numbers. Between 05:00 and 08:15 the nightly
run is still writing the counts: skipped. When compute.log has no completed nightly run (emptied, or the
run failed), the stored counts may be from any earlier day: the differences are listed in the job
summary and the test skips (test_admin_staging.py::test_nightly_run_completed reports the run itself). Staging only (admin test login); skipped where the page is
not deployed yet. Database behaviour: test/org/calacademy/antweb/util/CountCheckTest.java (CI).
"""
import json
import os
import re
import time
from html import unescape

import pytest

from antweb import BASE_URL, TIMEOUT, classify, get, new_session
from staging_accounts import IS_STAGING, login, needs

# The checks of CountInvariants (docs/numbers.md names them in its Test column).
INVARIANTS = {"place.totals.subfamily", "place.totals.genus", "place.totals.species", "place.totals.valid",
              "place.totals.specimens", "place.totals.endemic", "place.totals.introduced", "place.pies",
              "place.images.order", "place.images", "list.genus", "list.subfamily", "list.family", "list.global",
              "global.specimens", "home", "taxon.images", "geo.regions",
              # NumberChecks, 2026-10-22 (admin.curators and admin.artists run when LoginMgr and ArtistMgr are loaded)
              "stats.global", "stats.images", "stats.imaged", "stats.bioregions", "stats.museums", "admin.statistics",
              "admin.groups", "admin.imageUploads", "list.images", "place.endemic.flags", "taxon.parents", "geo.adm1.country"}
# api.taxa runs only where the taxon table has the columns API /taxa reads (B11: no code writes them).


def test_count_check_needs_login():
    outcome = get(new_session(), "/countCheck.do")
    if outcome.category == "not_found":
        pytest.skip("countCheck.do is not deployed on this server yet")
    assert outcome.category == "login", outcome


# The whole check runs as a background job since 2026-10-21 (107 seconds on staging, over Cloudflare's 100):
# countCheck.do?run=1 starts it and sends the browser to jobStatus.do?id=N; the job's report is countCheck.do?job=N.
JOB_LIMIT_SECONDS = 600
POLL_SECONDS = 10


_REPORT = {}


def count_check_report():
    """(admin session, report page) of one count check run per test session (the job takes minutes): the tests
    below compare the pages with the numbers of that run."""
    if "r" not in _REPORT:
        s, _token, _id = login("admin")
        _REPORT["s"] = s
        _REPORT["r"] = run_count_check(s)
    return _REPORT["s"], _REPORT["r"]


def run_count_check(s):
    """Starts the check, waits for its job (at most JOB_LIMIT_SECONDS) and returns the report page."""
    r = s.get(BASE_URL + "/countCheck.do?run=1", timeout=90)
    outcome = classify(r)
    if outcome.category == "not_found":
        pytest.skip("countCheck.do is not deployed on this server yet")
    assert outcome.category == "ok", outcome
    m = re.search(r"jobStatus\.do\?id=(\d+)", r.url)
    if not m:
        return r   # a build before 2026-10-21: the report came in the request
    job = m.group(1)
    started = time.time()
    status, step = "", ""
    while True:
        page = s.get(BASE_URL + "/jobStatus.do?id=" + job, timeout=60).text
        st = re.search(r'id="job-status" data-status="(\w+)"', page)
        status = st.group(1) if st else "?"
        sp = re.search(r'id="job-step">(.*?)</span>', page, re.S)
        step = unescape(sp.group(1)) if sp else ""
        if status not in ("queued", "running"):
            break
        if status == "queued" and "nightly" in step and time.time() - started > 60:
            pytest.skip(f"count check job {job} waits for the nightly run: {step}")
        assert time.time() - started < JOB_LIMIT_SECONDS, (
            f"count check job {job} did not finish in {JOB_LIMIT_SECONDS} s (status {status}: {step})")
        time.sleep(POLL_SECONDS)
    assert status == "finished", f"count check job {job} ended {status}: {re.sub(r'<[^>]+>', ' ', page)[-1500:]}"
    report = s.get(BASE_URL + "/countCheck.do?job=" + job, timeout=60)
    assert classify(report).category == "ok"
    return report


@pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only")
@needs("admin")
def test_list_counts_match_the_specimen_records():
    _s, r = count_check_report()
    assert "What this shows:" in r.text
    assert "<h1>Check the counts</h1>" in r.text, "page renamed from Count check (2026-10-20)"
    m = re.search(r'id="count-check-result" data-real="(\d+)" data-pending="(\d+)" data-window="(\d)" data-nightly="(\d)"', r.text)
    assert m, "no result line on countCheck.do"
    real, pending, window, nightly = int(m.group(1)), int(m.group(2)), m.group(3) == "1", m.group(4) == "1"
    if window:
        pytest.skip("the nightly run is writing the counts now (05:00 to 08:15 Pacific)")
    for place in ("Madagascar", "Malagasy bioregion", "museum CASC"):
        assert f"<td>{place}</td>" in r.text, f"{place} was not checked"
    rows = []
    if real:
        section = r.text[r.text.index("Real differences"):r.text.index("Pending differences")]
        for row in re.findall(r'<tr class="count-mismatch">(.*?)</tr>', section, re.S):
            cells = [unescape(re.sub(r"<[^>]+>", "", c)).strip() for c in re.findall(r"<td>(.*?)</td>", row, re.S)]
            rows.append(cells)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary and rows:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(f"\n### Count check: {real} real differences ({pending} pending)"
                    f"{'' if nightly else ' - compute.log has no completed nightly run'}\n\n")
            f.write("| Place | Number | Taxon | Stored (shown) | Recount |\n|---|---|---|---|---|\n")
            for cells in rows[:300]:
                f.write("| " + " | ".join(c.replace("|", "/") for c in cells[:5]) + " |\n")
    # Since 2026-10-21: the invariants over every place (CountInvariants) and the run time (the page must answer
    # well within Cloudflare's 100 seconds; the rotating sample stops at about 50).
    if 'id="count-invariants"' in r.text:
        ids = set(re.findall(r'<tr class="count-invariant" data-id="([^"]+)"', r.text))
        missing = INVARIANTS - ids
        assert not missing, f"invariants not run: {sorted(missing)}"
        failed = [(i, unescape(e)) for i, e in re.findall(
            r'<tr class="count-invariant" data-id="([^"]+)"><td>.*?</td><td>.*? <b>\(failed: (.*?)\)</b></td>', r.text, re.S)]
        assert not failed, "invariants could not run (check id: exception, code line, SQL):\n" + "\n".join(
            f"{i}: {e}" for i, e in failed)
        t = re.search(r'id="count-check-time" data-seconds="(\d+)"[^>]*>(.*?)</p>', r.text, re.S)
        assert t, "no run time on the report"
        assert int(t.group(1)) < JOB_LIMIT_SECONDS, unescape(t.group(2))
        if summary:
            with open(summary, "a", encoding="utf-8") as f:
                f.write("\n### Count check: every place\n\n| Check | Compared | Real | Pending | ms |\n|---|---|---|---|---|\n")
                for row in re.findall(r'<tr class="count-invariant" data-id="([^"]+)"><td>.*?</td><td>.*?</td><td>.*?</td>'
                                      r'<td>(\d+)</td><td>(\d+)</td><td>(\d+)</td><td>(\d+)</td></tr>', r.text, re.S):
                    f.write("| " + " | ".join(row) + " |\n")
    if not nightly:
        pytest.skip(f"compute.log has no completed nightly run, so the stored counts may be older than a day "
                    f"({real} differences, listed in the job summary)")
    if real:
        pytest.fail(f"{real} list counts differ from the specimen records ({pending} more are pending). "
                    "Place | number | taxon | stored (shown) | recount:\n" + "\n".join(" | ".join(c) for c in rows[:40]))


# ---------------------------------------------------------------------------------------------------------------
# The pages against the numbers of the count check (2026-10-22, docs/numbers.md "every number checked"). The check
# (NumberChecks) recounts these numbers from the records and compares them with what the pages' code computes or
# stores; its report carries the recounts as JSON (id count-check-expected). These tests read the pages themselves
# and compare each number shown with that JSON, so a page that renders a number wrongly fails too. A page computes
# its numbers when it is shown, the check some minutes earlier: an upload in between can make a number differ (the
# message says so).

def expected_numbers():
    s, r = count_check_report()
    m = re.search(r'<script type="application/json" id="count-check-expected">(.*?)</script>', r.text, re.S)
    if not m:
        pytest.skip("the count check report has no expected numbers (NumberChecks not deployed yet)")
    return s, json.loads(m.group(1))


def _int(text):
    t = unescape(re.sub(r"<[^>]+>", "", text)).replace(",", "").replace("\xa0", " ").strip()
    return int(t) if re.fullmatch(r"-?\d+", t) else None


def _section(html, start, end="</table>"):
    i = html.find(start)
    if i < 0:
        return ""
    j = html.find(end, i)
    return html[i:j if j > 0 else len(html)]


def _statset_rows(section):
    """StatSet rows: name -> [4 numbers] (<tr align=right><td>name</td><td>n</td>...)."""
    rows = {}
    for name, cells in re.findall(r"<tr align=right><td>([^<]*)</td>((?:<td>[\d,]+</td>){4})</tr>", section):
        rows[name.strip()] = [int(v.replace(",", "")) for v in re.findall(r"<td>([\d,]+)</td>", cells)]
    return rows


UPLOAD_NOTE = " (the page was computed after the check: an upload in between can explain it; run again)"


@pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only")
@needs("admin")
def test_statspage_tables_equal_the_recount():
    """statsPage.do (rows 11 to 19): Global Data, the Images table with its Sum row, Imaged Species, Bioregion Data and
    Museum Data, each number = the check's recount."""
    _s, exp = expected_numbers()
    stats = exp.get("statsPage") or {}
    html = new_session().get(BASE_URL + "/statsPage.do", timeout=max(TIMEOUT, 180)).text
    wrong = []
    glob = _statset_rows(_section(html, "<h2>Global Data:</h2>"))
    for row, key in (("Extant", "extant"), ("Fossil", "fossil")):
        if key in stats and row in glob and glob[row] != stats[key]:
            wrong.append(f"Global Data {row}: page {glob[row]}, recount {stats[key]}")
    images = _section(html, "<h2>Images:</h2>")
    shown = {}
    for name, cells in re.findall(r"<tr align=right><td>([^<]*)</td>((?:<td>[\d,]+</td>){5})</tr>", images):
        shown[name.strip().lower()] = [int(v.replace(",", "")) for v in re.findall(r"<td>([\d,]+)</td>", cells)]
    for status, values in (stats.get("images") or {}).items():
        if shown.get(status) != values:
            wrong.append(f"Images table {status}: page {shown.get(status)}, recount {values}")
    if "imagesSum" in stats and shown.get("sum") != stats["imagesSum"]:
        wrong.append(f"Images table Sum: page {shown.get('sum')}, recount {stats['imagesSum']} (Sum = images of specimens with a status)")
    if "sum" in shown:
        rows = [v for k, v in shown.items() if k != "sum"]
        total = [sum(col) for col in zip(*rows)] if rows else [0] * 5
        if total != shown["sum"]:
            wrong.append(f"Images table: Sum row {shown['sum']} is not the sum of the rows {total}")
        for k, v in shown.items():
            if v[1] + v[2] + v[3] + v[4] != v[0]:
                wrong.append(f"Images table {k}: Worker + Male + Queen + Other {v[1:]} != Total {v[0]}")
    imaged = dict((k.strip().lower(), _int(v)) for k, v in re.findall(
        r"<tr><td align=right>([^<]*)</td><td align=right>([^<]*)</td></tr>", _section(html, "<h2>Imaged Species:</h2>")))
    for status, n in (stats.get("imagedSpecies") or {}).items():
        if status in imaged and imaged[status] is not None and imaged[status] != n:
            wrong.append(f"Imaged Species {status}: page {imaged[status]}, recount {n}")
    bio = _statset_rows(_section(html, "<h2>Bioregion Data:</h2>"))
    for name, values in (stats.get("bioregions") or {}).items():
        if name in bio and bio[name] != values:
            wrong.append(f"Bioregion Data {name}: page {bio[name]}, recount {values}")
    extant = _statset_rows(_section(html, "<h3>Valid Extant:</h3>"))
    fossil = _statset_rows(_section(html, "<h3>Valid Fossil:</h3>"))
    for code, (ex, fo) in (stats.get("museums") or {}).items():
        if code in extant and extant[code] != ex:
            wrong.append(f"Museum Data Valid Extant {code}: page {extant[code]}, recount {ex}")
        if code in fossil and fossil[code] != fo:
            wrong.append(f"Museum Data Valid Fossil {code}: page {fossil[code]}, recount {fo}")
    assert glob and shown, "statsPage.do: Global Data or Images table not found"
    assert not wrong, "statsPage.do numbers differ from the count check's recount" + UPLOAD_NOTE + ":\n" + "\n".join(wrong[:60])


@pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only")
@needs("admin")
def test_curator_and_artist_pages_equal_the_recount():
    """curators.do, curator.do (rows 80 to 82, 84) and artists.do (row 92): each number = the check's recount."""
    s, exp = expected_numbers()
    wrong = []
    curators = exp.get("curators") or {}
    if curators:
        html = s.get(BASE_URL + "/curators.do", timeout=TIMEOUT).text
        rows = re.findall(r"<tr><td>\d+</td><td>(.*?)</td><td>.*?</td><td>(\d+)</td><td>(\d+)</td>", html, re.S)
        seen = 0
        for link, uploads, edits in rows:
            m = re.search(r"curator\.do\?id=(\d+)", link)
            if not m or m.group(1) not in curators:
                continue
            seen += 1
            want = curators[m.group(1)]
            if [int(uploads), int(edits)] != want[:2]:
                wrong.append(f"curators.do curator {m.group(1)}: Specimen Uploads, Description Edits {uploads}, {edits}; recount {want[:2]}")
        assert seen, "curators.do: no curator row found"
        # one curator page: the one with the most uploads
        cid, want = max(curators.items(), key=lambda kv: kv[1][0])
        page = s.get(BASE_URL + "/curator.do?id=" + cid, timeout=TIMEOUT).text
        m = re.search(r"listUploads\.do\?loginId=" + cid + r"'>(\d+)</a>", page)
        if m and int(m.group(1)) != want[0]:
            wrong.append(f"curator.do?id={cid} Specimen Uploads {m.group(1)}, recount {want[0]}")
        m = re.search(r"listImageUploads\.do\?curatorId=" + cid + r"'>(\d+)</a>", page)
        if m and int(m.group(1)) != want[2]:
            wrong.append(f"curator.do?id={cid} Image Uploads {m.group(1)}, recount {want[2]}")
        m = re.search(r"<li><b>Images Uploaded:</b></li>\s*<li>([\d,]+)</li>", page)
        if m and _int(m.group(1)) != want[3]:
            wrong.append(f"curator.do?id={cid} Images Uploaded {m.group(1)}, recount {want[3]}")
    artists = exp.get("artists") or {}
    if artists:
        html = s.get(BASE_URL + "/artists.do", timeout=TIMEOUT).text
        seen = 0
        for aid, images, specimens in re.findall(r"<tr><td>\d+\.</td><td>(\d+)</td><td>.*?</td><td>([\d,]+)</td><td>([\d,]+)</td></tr>", html, re.S):
            if aid not in artists:
                continue
            seen += 1
            if [_int(images), _int(specimens)] != artists[aid]:
                wrong.append(f"artists.do artist {aid}: Images, Specimen {images}, {specimens}; recount {artists[aid]}")
        assert seen, "artists.do: no artist row found"
    if not curators and not artists:
        pytest.skip("LoginMgr and ArtistMgr were not loaded when the check ran")
    assert not wrong, "curator and artist pages differ from the recount" + UPLOAD_NOTE + ":\n" + "\n".join(wrong[:60])


def _groups_rows(html):
    """groups.do: group id -> [uploads, specimens, ungeoreferenced, flagged, collections, localities, subfamilies, genera, species]."""
    rows = {}
    for m in re.finditer(r"group\.do\?id=(\d+)'>.*?</td>((?:\s*<td align=\"right\">.*?</td>){11})", html, re.S):
        cells = re.findall(r'<td align="right">(.*?)</td>', m.group(2), re.S)
        nums = [_int(c) for c in cells]
        rows[m.group(1)] = [nums[0]] + nums[3:]
    return rows


@pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only")
@needs("admin")
def test_group_pages_equal_the_stored_upload_numbers():
    """groups.do and group.do (rows 85 to 91): each group's numbers = the upload row the check recounted (admin.groups);
    the Totals row = the sum of the rows (contributors, uploads, specimens, ungeoreferenced, flagged, collections,
    localities); its Subfamilies, Genera, Species are the largest of the rows (labelled Totals; question in
    docs/numbers.md)."""
    s, exp = expected_numbers()
    groups = exp.get("groups") or {}
    if not groups:
        pytest.skip("no upload groups in the count check")
    html = s.get(BASE_URL + "/groups.do", timeout=max(TIMEOUT, 120)).text
    rows = _groups_rows(html)
    assert rows, "groups.do: no group rows found"
    wrong = []
    for gid, values in rows.items():
        if gid in groups and values != groups[gid]:
            wrong.append(f"groups.do group {gid}: page {values}, stored {groups[gid]}")
    tot = re.search(r"<td><b>Totals:&nbsp;</b>(\d+)</td>((?:\s*<td align=\"right\">.*?</td>){11})", html, re.S)
    if tot:
        cells = [_int(c) for c in re.findall(r'<td align="right">(.*?)</td>', tot.group(2), re.S)]
        totals = [cells[0]] + cells[3:]
        if int(tot.group(1)) != len(rows):
            wrong.append(f"groups.do Totals: {tot.group(1)} contributors, {len(rows)} rows")
        for i, name in enumerate(["Uploads", "Specimens", "Ungeoreferenced", "Flagged", "Collections", "Localities"]):
            col = sum(v[i] or 0 for v in rows.values())
            if totals[i] != col:
                wrong.append(f"groups.do Totals {name} {totals[i]} != sum of the rows {col}")
        for i, name in ((6, "Subfamilies"), (7, "Genera"), (8, "Species")):
            if totals[i] != max(v[i] or 0 for v in rows.values()):
                wrong.append(f"groups.do Totals {name} {totals[i]} is not the largest row (the page's rule)")
    gid = max(rows, key=lambda g: rows[g][1] or 0)
    page = s.get(BASE_URL + "/group.do?id=" + gid, timeout=TIMEOUT).text
    shown = {}
    for label in ("Uploads", "Specimens", "Ungeoreferenced", "Flagged", "Collections", "Localities"):
        m = re.search(r"<li><b>" + label + r":</b></li>\s*<li>(.*?)</li>", page, re.S)
        if m:
            shown[label] = _int(m.group(1))
    for i, label in ((0, "Uploads"), (1, "Specimens"), (2, "Ungeoreferenced"), (3, "Flagged"), (4, "Collections"), (5, "Localities")):
        if label in shown and shown[label] != rows[gid][i]:
            wrong.append(f"group.do?id={gid} {label} {shown[label]}, groups.do {rows[gid][i]}")
    assert not wrong, "upload group numbers:\n" + "\n".join(wrong[:60])


@pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only")
@needs("admin")
def test_image_upload_and_orphan_counts_equal_the_recount():
    """listImageUploads.do "Total image uploads" (row 93) and orphanedImages.do "Found N" (row 99; it counts the
    specimen codes of orphan images, not the images)."""
    s, exp = expected_numbers()
    wrong = []
    total = (exp.get("imageUploads") or {}).get("total")
    if total is not None:
        m = re.search(r"Total image uploads: (\d+)", s.get(BASE_URL + "/listImageUploads.do", timeout=max(TIMEOUT, 120)).text)
        if m and int(m.group(1)) != total:
            wrong.append(f"listImageUploads.do Total image uploads {m.group(1)}, recount {total}")
    orphans = exp.get("orphanedImages") or {}
    if "codes" in orphans:
        m = re.search(r"Found\s+(\d+) orphaned images", s.get(BASE_URL + "/orphanedImages.do", timeout=max(TIMEOUT, 180)).text)
        if m and int(m.group(1)) != orphans["codes"]:
            wrong.append(f"orphanedImages.do Found {m.group(1)}; recount {orphans['codes']} codes ({orphans.get('images')} images)")
    assert not wrong, "\n".join(wrong)


PLACE = "/country.do?name=Madagascar"
LIST = "/taxonomicPage.do?countryName=Madagascar&statusSet=all&rank="


def _place_stats(session):
    html = session.get(BASE_URL + PLACE, timeout=max(TIMEOUT, 120)).text
    return {k: int(v) for k, v in re.findall(r'<tr data-stat="([a-z-]+)" data-n="(\d+)"', html)}


@pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only")
@needs("admin")
def test_subtaxon_totals_equal_the_place_numbers():
    """taxonomicPage.do, admin "Subtaxon total" (row 45): the sum of the rows' numbers. For Madagascar with every
    counted status: rank species = Specimens, rank genus = Species, rank subfamily = Genera of the place page (which
    the count check validates)."""
    s, _token, _id = login("admin")
    stats = _place_stats(s)
    if "species" not in stats:
        pytest.skip("Madagascar page without data-stat numbers")
    wrong = []
    for rank, stat in (("species", "specimens"), ("genus", "species"), ("subfamily", "genera")):
        html = s.get(BASE_URL + LIST + rank, timeout=max(TIMEOUT, 180)).text
        m = re.search(r"Subtaxon total: (\d+)", html)
        if not m:
            continue
        if int(m.group(1)) != stats[stat]:
            wrong.append(f"taxonomicPage rank={rank}: Subtaxon total {m.group(1)}, place page {stat} {stats[stat]}")
    assert not wrong, "Madagascar:\n" + "\n".join(wrong)
