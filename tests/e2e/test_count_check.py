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
from staging_accounts import GROUP_NAME, IS_STAGING, login, needs

# The checks of CountInvariants (docs/numbers.md names them in its Test column).
INVARIANTS = {"place.totals.subfamily", "place.totals.genus", "place.totals.species", "place.totals.valid",
              "place.totals.specimens", "place.totals.endemic", "place.totals.introduced", "place.pies",
              "place.images.order", "place.images", "list.genus", "list.subfamily", "list.family", "list.global",
              "global.specimens", "home", "taxon.images", "geo.regions",
              # NumberChecks, 2026-10-22 (admin.curators and admin.artists run when LoginMgr and ArtistMgr are loaded)
              "stats.global", "stats.images", "stats.imaged", "stats.bioregions", "stats.museums", "stats.nonants", "admin.statistics",
              "admin.groups", "admin.imageUploads", "list.images", "place.endemic.flags", "taxon.parents", "geo.adm1.country"}
# api.taxa runs only where the taxon table has the columns API /taxa reads (B11: no code writes them).

# Checks whose real differences are known findings, not yet fixed (docs/numbers.md section 3): named in the failure.
# Each of these checks reports only its known finding (taxon.parents, B25, is not here: by the code it finds nothing,
# and any parent it finds is a new finding).
KNOWN = {"api.taxa": "B11, API /taxa counts nobody writes", "geo.adm1.country": "B26, adm1 by name only",
         "stats.nonants": "B31, statsPage counts every family"}
# The recount checks of the sampled places (CountCheck).
RECOUNT_CHECKS = {"specimens", "species", "genera", "global", "images", "place images", "place specimens", "place totals"}


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


def real_rows(r):
    """The listed real differences of the report: [place, check, taxon or number, stored (shown), recount]."""
    if "Real differences" not in r.text:
        return []
    start = r.text.index("Real differences")
    end = r.text.find("Pending differences", start)
    section = r.text[start:end if end > 0 else len(r.text)]
    return [[unescape(re.sub(r"<[^>]+>", "", c)).strip() for c in re.findall(r"<td>(.*?)</td>", row, re.S)]
            for row in re.findall(r'<tr class="count-mismatch">(.*?)</tr>', section, re.S)]


def examples(rows, skip=(), per_check=3):
    """The first examples of each check (the GitHub annotation shows only the start of the message): one line each,
    'check | place | what | shown -> must be'; the known findings' checks last, one example each."""
    by_check = {}
    for c in rows:
        if len(c) >= 5:
            by_check.setdefault(c[1], []).append(c)
    lines = []
    for check in sorted(by_check, key=lambda k: (k in skip, k)):
        n = 1 if check in skip else per_check
        for c in by_check[check][:n]:
            lines.append(f"{check} | {c[0]} | {c[2][:160]} | {c[3]} -> {c[4]}")
    return "Examples (check | place | number | shown -> must be):\n" + "\n".join(lines)


def count_check_report():
    """(admin session, report page) of one count check run per test session (the job takes minutes): the tests
    below compare the pages with the numbers of that run."""
    if "outcome" not in _REPORT:
        try:
            s, _token, _id = login("admin")
            _REPORT["s"] = s
            _REPORT["r"] = run_count_check(s)
            _REPORT["outcome"] = ("ok", "")
        except pytest.skip.Exception as e:
            _REPORT["outcome"] = ("skip", str(e))
        except (Exception, pytest.fail.Exception) as e:   # an assertion or a timeout: the other tests do not start another job
            _REPORT["outcome"] = ("fail", f"{type(e).__name__}: {e}")
            raise
    kind, why = _REPORT["outcome"]
    if kind == "skip":
        pytest.skip(why)
    if kind == "fail":
        pytest.fail("the count check job failed earlier in this run: " + why)
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
        # One row at a time (a pattern with several .*? over the whole page took about 20 minutes on a
        # report with a few thousand pending rows: each lazy group can run to the end of the page).
        inv_rows = [(i, [c for c in re.findall(r"<td>(.*?)</td>", body, re.S)])
                    for i, body in re.findall(r'<tr class="count-invariant" data-id="([^"]+)">(.*?)</tr>', r.text, re.S)]
        ids = {i for i, _cells in inv_rows}
        missing = INVARIANTS - ids
        assert not missing, f"invariants not run: {sorted(missing)}"
        failed = []
        for i, cells in inv_rows:
            m_failed = re.search(r" <b>\(failed: (.*?)\)</b>", cells[1], re.S) if len(cells) > 1 else None
            if m_failed:
                failed.append((i, unescape(m_failed.group(1))))
        assert not failed, "invariants could not run (check id: exception, code line, SQL):\n" + "\n".join(
            f"{i}: {e}" for i, e in failed)
        t = re.search(r'id="count-check-time" data-seconds="(\d+)"[^>]*>(.*?)</p>', r.text, re.S)
        assert t, "no run time on the report"
        assert int(t.group(1)) < JOB_LIMIT_SECONDS, unescape(t.group(2))
        if summary:
            with open(summary, "a", encoding="utf-8") as f:
                f.write("\n### Count check: every place\n\n| Check | Compared | Real | Pending | ms |\n|---|---|---|---|---|\n")
                for i, cells in inv_rows:
                    if len(cells) == 7 and all(c.strip().isdigit() for c in cells[3:]):
                        f.write("| " + " | ".join([i] + [c.strip() for c in cells[3:]]) + " |\n")
    # Since 2026-10-22: the database structure against the committed snapshot (SchemaCheck). The check must run
    # (the snapshot is readable in the container); differences are listed in the job summary, not failed here
    # (staging's database can differ from production's snapshot; production's are read on countCheck.do).
    # The help text names the section since the same change, so a server that has SchemaCheck must show it
    # whenever the invariants ran.
    if 'id="count-invariants"' in r.text and "(Database structure)" in r.text:
        assert 'id="schema-check"' in r.text, "the count check ran its invariants but shows no Database structure section"
    if 'id="schema-check"' in r.text:
        sm = re.search(r'id="schema-check" data-differences="(\d+)" data-error="(\d)"', r.text)
        assert sm and sm.group(2) == "0", "the schema check could not run: " + unescape(
            re.sub(r"<[^>]+>", "", r.text[r.text.index('id="schema-check"'):][:600]))
        if summary and sm.group(1) != "0":
            items = [unescape(x) for x in re.findall(r'<li class="schema-difference">(.*?)</li>', r.text)]
            with open(summary, "a", encoding="utf-8") as f:
                f.write(f"\n### Database structure: {sm.group(1)} changes since the snapshot\n\n")
                for x in items[:100]:
                    f.write(f"- {x}\n")
    if not nightly:
        pytest.skip(f"compute.log has no completed nightly run, so the stored counts may be older than a day "
                    f"({real} differences, listed in the job summary)")
    if real:
        # Real differences per check, the known findings named (docs/numbers.md section 3), so that a new kind of
        # difference stands out from the known ones.
        per_check = {}
        for row in re.findall(r'<tr class="count-invariant" data-id="([^"]+)"><td>.*?</td><td>.*?</td><td>.*?</td><td>\d+</td><td>(\d+)</td>', r.text, re.S):
            if int(row[1]):
                per_check[row[0]] = per_check.get(row[0], 0) + int(row[1])
        recounts = sum(1 for c in rows if c[1] in RECOUNT_CHECKS)
        if recounts:
            per_check["recounts of the sampled places"] = recounts
        summary_line = "; ".join(f"{k}: {v}" + (f" (known: {KNOWN[k]})" if k in KNOWN else "") for k, v in sorted(per_check.items()))
        pytest.fail(f"{real} numbers differ from the records ({pending} more are pending). By check: {summary_line}.\n"
                    + examples(rows, skip=KNOWN))


@pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only")
@needs("admin")
def test_no_real_differences_outside_the_known_findings():
    """The known findings (KNOWN: B11, B26, B31, not fixed yet) make test_list_counts_match_the_specimen_records fail
    every day until they are fixed (Brian Fisher, 2026-10-21: a real wrong number fails the test). This test fails only
    on real differences of the other checks, so a new kind of wrong number still changes the state of the run."""
    _s, r = count_check_report()
    m = re.search(r'id="count-check-result" data-real="(\d+)" data-pending="(\d+)" data-window="(\d)" data-nightly="(\d)"', r.text)
    assert m, "no result line on countCheck.do"
    if m.group(3) == "1":
        pytest.skip("the nightly run is writing the counts now (05:00 to 08:15 Pacific)")
    if m.group(4) == "0":
        pytest.skip("compute.log has no completed nightly run")
    other = {}
    for check_id, real in re.findall(r'<tr class="count-invariant" data-id="([^"]+)"><td>.*?</td><td>.*?</td><td>.*?</td><td>\d+</td><td>(\d+)</td>', r.text, re.S):
        if int(real) and check_id not in KNOWN:
            other[check_id] = other.get(check_id, 0) + int(real)
    if int(m.group(1)) and "Real differences" in r.text:
        start = r.text.index("Real differences")
        end = r.text.find("Pending differences", start)
        section = r.text[start:end if end > 0 else len(r.text)]
        recounts = sum(1 for row in re.findall(r'<tr class="count-mismatch"><td>.*?</td><td>(.*?)</td>', section, re.S) if row in RECOUNT_CHECKS)
        if recounts:
            other["recounts of the sampled places"] = recounts
    assert not other, ("real differences outside the known findings: " + "; ".join(f"{k}: {v}" for k, v in sorted(other.items()))
                       + ".\n" + examples(real_rows(r), skip=KNOWN))


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
    """curators.do, curator.do (rows 80 to 82, 84) and artists.do (row 92): each number = the number the check compared
    with its recount (LoginMgr's and ArtistMgr's, read at the nightly reload; admin.curators and admin.artists compare
    those with the records). So the pages show what was checked, and an upload by another test in the same run does
    not fail this test."""
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
                wrong.append(f"curators.do curator {m.group(1)}: Specimen Uploads, Description Edits {uploads}, {edits}; checked {want[:2]}")
        assert seen, "curators.do: no curator row found"
        # one curator page: the one with the most uploads
        cid, want = max(curators.items(), key=lambda kv: kv[1][0])
        page = s.get(BASE_URL + "/curator.do?id=" + cid, timeout=TIMEOUT).text
        m = re.search(r"listUploads\.do\?loginId=" + cid + r"'>(\d+)</a>", page)
        if m and int(m.group(1)) != want[0]:
            wrong.append(f"curator.do?id={cid} Specimen Uploads {m.group(1)}, checked {want[0]}")
        m = re.search(r"listImageUploads\.do\?curatorId=" + cid + r"'>(\d+)</a>", page)
        if m and int(m.group(1)) != want[2]:
            wrong.append(f"curator.do?id={cid} Image Uploads {m.group(1)}, checked {want[2]}")
        m = re.search(r"<li><b>Images Uploaded:</b></li>\s*<li>([\d,]+)</li>", page)
        if m and _int(m.group(1)) != want[3]:
            wrong.append(f"curator.do?id={cid} Images Uploaded {m.group(1)}, checked {want[3]}")
    artists = exp.get("artists") or {}
    if artists:
        html = s.get(BASE_URL + "/artists.do", timeout=TIMEOUT).text
        seen = 0
        for aid, images, specimens in re.findall(r"<tr><td>\d+\.</td><td>(\d+)</td><td>.*?</td><td>([\d,]+)</td><td>([\d,]+)</td></tr>", html, re.S):
            if aid not in artists:
                continue
            seen += 1
            if [_int(images), _int(specimens)] != artists[aid]:
                wrong.append(f"artists.do artist {aid}: Images, Specimen {images}, {specimens}; checked {artists[aid]}")
        assert seen, "artists.do: no artist row found"
    if not curators and not artists:
        pytest.skip("LoginMgr and ArtistMgr were not loaded when the check ran")
    assert not wrong, "curator and artist pages differ from the recount" + UPLOAD_NOTE + ":\n" + "\n".join(wrong[:60])


def _groups_rows(html, skip_name=None):
    """groups.do: group id -> [uploads, specimens, ungeoreferenced, flagged, collections, localities, subfamilies, genera, species]."""
    rows = {}
    for m in re.finditer(r"group\.do\?id=(\d+)'>(.*?)</a>.*?</td>((?:\s*<td align=\"right\">.*?</td>){11})", html, re.S):
        if skip_name and unescape(m.group(2)).strip() == skip_name:
            continue
        cells = re.findall(r'<td align="right">(.*?)</td>', m.group(3), re.S)
        nums = [_int(c) for c in cells]
        rows[m.group(1)] = [nums[0]] + nums[3:]
    return rows


@pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only")
@needs("admin")
def test_group_pages_equal_the_stored_upload_numbers():
    """groups.do and group.do (rows 85 to 91): each group's numbers = the numbers the check compared with a recount
    (admin.groups: since 2026-10-22 the group's specimens today, group_count, recounted every night; before a nightly
    run has counted a group, its last upload row);
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
    # the staging test group uploads during the suite (test_specimen_upload_roundtrip.py): its numbers can change
    # between the check and this page
    others = _groups_rows(html, skip_name=GROUP_NAME)
    wrong = []
    for gid, values in others.items():
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
    pick = others or rows
    gid = max(pick, key=lambda g: pick[g][1] or 0)
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
        if stat not in stats:
            wrong.append(f"the Madagascar page has no data-stat {stat}")
            continue
        html = s.get(BASE_URL + LIST + rank, timeout=max(TIMEOUT, 180)).text
        m = re.search(r"Subtaxon total: (\d+)", html)
        if not m:
            continue
        if int(m.group(1)) != stats[stat]:
            wrong.append(f"taxonomicPage rank={rank}: Subtaxon total {m.group(1)}, place page {stat} {stats[stat]}")
    assert not wrong, "Madagascar:\n" + "\n".join(wrong)
