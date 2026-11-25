"""tools/place/place_daily.py, the daily place step in report mode (Places from coordinates, step 1, 2026-11-16):
the rows it makes from place_corrections.py's corrections.csv, the SQL of one load (one transaction, today's rows
replaced, old runs removed, values quoted), the lock, the refused apply mode and the summary line. No database: the
SQL is checked as text; the MySQL run of the file db/upgrade/9.4.0/2026-11-16-place-update.sql is in CI.

  python3 tests/unit/test_place_daily.py
"""
import csv
import os
import sys
import tempfile

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "tools", "place"))
import place_corrections  # noqa: E402
import place_daily as pd  # noqa: E402

HEADER = place_corrections.CORRECTIONS_HEADER


def corrections_file(folder, lines):
    path = os.path.join(folder, "corrections.csv")
    with open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(HEADER)
        for d in lines:
            w.writerow([d.get(h, "") for h in HEADER])
    return path


SAMPLE = [
    {"specimen_code": "casent0266850", "field": "adm1", "current_value": "Toliara", "proposed_value": "Menabe",
     "basis": "coordinates inside Menabe, OCHA COD-AB MDG ADM1", "note": "the stored province is the province of the region", "upload_group": "California Academy of Sciences"},
    {"specimen_code": "psw1", "field": "adm1", "current_value": "", "proposed_value": "Amoron'i Mania", "upload_group": "UC Davis Ant Group",
     "other_state_flag": "within the stated error of Vakinankaratra"},
    {"specimen_code": "x9", "field": "country", "current_value": "", "proposed_value": "Côte d'Ivoire", "upload_group": "UC Davis Ant Group"},
    {"specimen_code": "", "field": "adm1", "proposed_value": "Sava"},          # no code: skipped
    {"specimen_code": "nothing", "field": "adm1", "proposed_value": ""},       # no proposal: skipped
]


def test_rows_from_corrections():
    with tempfile.TemporaryDirectory() as d:
        rows = pd.rows_of(corrections_file(d, SAMPLE), "2026-11-17", "report")
    assert [r["code"] for r in rows] == ["casent0266850", "psw1", "x9"], rows
    one = rows[0]
    assert (one["field"], one["stored_value"], one["proposed"], one["upload_group"], one["mode"]) == ("adm1", "Toliara", "Menabe", "California Academy of Sciences", "report")
    assert rows[1]["stored_value"] is None and rows[1]["flag"] == "within the stated error of Vakinankaratra"
    assert rows[2]["field"] == "country" and rows[2]["proposed"] == "Côte d'Ivoire"


def test_long_values_are_cut_to_the_columns():
    with tempfile.TemporaryDirectory() as d:
        rows = pd.rows_of(corrections_file(d, [{"specimen_code": "a", "field": "adm1", "proposed_value": "P", "basis": "b" * 400}]), "2026-11-17", "report")
    assert len(rows[0]["basis"]) == 255


def test_one_load_is_one_transaction_that_replaces_today():
    with tempfile.TemporaryDirectory() as d:
        rows = pd.rows_of(corrections_file(d, SAMPLE), "2026-11-17", "report")
    sql = pd.statements(rows, "2026-11-17", "report")
    assert sql[0] == "START TRANSACTION" and sql[-1] == "COMMIT", sql[:2]
    assert sql[1] == "DELETE FROM place_update WHERE run_date = '2026-11-17' AND mode = 'report'"
    inserts = [s for s in sql if s.startswith("INSERT")]
    assert len(inserts) == 1 and "'Amoron''i Mania'" in inserts[0] and "'Côte d''Ivoire'" in inserts[0], inserts
    assert "NULL" in inserts[0]                                   # psw1 has no stored state
    assert sql[-2] == "DELETE FROM place_update WHERE run_date < '2026-11-17' - INTERVAL 60 DAY"
    many = [dict(rows[0], code="c" + str(i)) for i in range(pd.BATCH * 2 + 1)]
    assert len([s for s in pd.statements(many, "2026-11-17", "report") if s.startswith("INSERT")]) == 3


def test_a_code_too_long_is_skipped_never_cut():
    with tempfile.TemporaryDirectory() as d:
        rows = pd.rows_of(corrections_file(d, [{"specimen_code": "c" * 129, "field": "adm1", "proposed_value": "P"},
                                               {"specimen_code": "ok", "field": "adm1", "proposed_value": "P"}]), "2026-11-17", "report")
    assert [r["code"] for r in rows] == ["ok"], rows


def test_a_failed_run_is_logged():
    lines = []
    was_log, was_main = pd.log, pd.main
    pd.log = lines.append
    try:
        pd.main = lambda argv=None: (_ for _ in ()).throw(RuntimeError("mysql failed: Access denied"))
        assert pd.run([]) == 1 and lines == ["place-daily FAILED: RuntimeError: mysql failed: Access denied"], lines
        del lines[:]
        pd.main = lambda argv=None: sys.exit("place-daily: another run holds /run/x; nothing done")
        assert pd.run([]) == 1 and lines == ["place-daily FAILED: place-daily: another run holds /run/x; nothing done"], lines
        del lines[:]
        pd.main = lambda argv=None: 0
        assert pd.run([]) == 0 and lines == []
    finally:
        pd.log, pd.main = was_log, was_main


def test_the_upgrade_file_caps_its_lock_wait():
    sql = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "db", "upgrade", "9.4.0", "2026-11-16-place-update.sql")).read()
    assert sql.index("SET SESSION lock_wait_timeout = 5;") < sql.index("'ALTER TABLE specimen ADD")


def test_quote_escapes():
    assert pd.quote(None) == "NULL"
    assert pd.quote("a'b\\c") == "'a''b\\\\c'"
    assert pd.quote("x\0y") == "'xy'"


APPLY = [
    {"specimen_code": "ok1", "field": "adm1", "current_value": "Toliara", "proposed_value": "Menabe", "country": "Madagascar", "upload_group": "CAS"},
    {"specimen_code": "ok2", "field": "adm1", "current_value": "", "proposed_value": "Sava", "country": "Madagascar", "upload_group": "CAS"},
    {"specimen_code": "near", "field": "adm1", "current_value": "", "proposed_value": "Sava", "country": "Madagascar",
     "other_state_flag": "within the stated error of Analanjirofo"},
    {"specimen_code": "ctry", "field": "country", "current_value": "", "proposed_value": "Kenya", "country": "Kenya"},
    {"specimen_code": "uk", "field": "adm1", "current_value": "", "proposed_value": "England", "country": "United Kingdom"},
    {"specimen_code": "bad", "field": "adm1", "current_value": "", "proposed_value": "Atlantis", "country": "Madagascar"},
    {"specimen_code": "gone", "field": "adm1", "current_value": "", "proposed_value": "Sava", "country": "Madagascar"},
    {"specimen_code": "moved", "field": "adm1", "current_value": "", "proposed_value": "Sava", "country": "Madagascar"},
    {"specimen_code": "edit", "field": "adm1", "current_value": "Toliara", "proposed_value": "Menabe", "country": "Madagascar"},
]
VALID = {("Madagascar", "Menabe"), ("Madagascar", "Sava"), ("United Kingdom", "England")}
NOW = {"ok1": ("Madagascar", "Toliara"), "ok2": ("Madagascar", ""), "near": ("Madagascar", ""), "ctry": ("", ""),
       "uk": ("United Kingdom", ""), "bad": ("Madagascar", ""), "moved": ("Comoros", ""), "edit": ("Madagascar", "Atsimo-Andrefana")}


def test_only_safe_states_are_applied():
    with tempfile.TemporaryDirectory() as d:
        rows = pd.rows_of(corrections_file(d, APPLY), "2026-11-23", "report")
    todo, why = pd.eligible(rows, VALID, NOW, {"United Kingdom"})
    assert [r["code"] for r in todo] == ["ok1", "ok2"], todo
    assert why == {"flag": 1, "country": 1, "skip": 1, "invalid": 1, "gone": 1, "moved": 1, "changed": 1}, why


def test_a_valid_stored_state_is_never_overwritten():
    # Brian Fisher, 2026-11-22 (North Grimston): the label's state is valid and the point lies in another state: the
    # coordinates are in doubt, so the row stays a report row. An old unit (Toliara, a former province) and an empty
    # state follow the point.
    rows = [{"specimen_code": "label", "field": "adm1", "current_value": "Sava", "proposed_value": "Menabe", "country": "Madagascar"},
            {"specimen_code": "old", "field": "adm1", "current_value": "Toliara", "proposed_value": "Menabe", "country": "Madagascar"},
            {"specimen_code": "empty", "field": "adm1", "current_value": "", "proposed_value": "Menabe", "country": "Madagascar"}]
    with tempfile.TemporaryDirectory() as d:
        rows = pd.rows_of(corrections_file(d, rows), "2026-11-24", "report")
    now = {"label": ("Madagascar", "Sava"), "old": ("Madagascar", "Toliara"), "empty": ("Madagascar", "")}
    todo, why = pd.eligible(rows, VALID, now, set())
    assert [r["code"] for r in todo] == ["old", "empty"] and why == {"valid": 1}, (todo, why)
    assert "coordinates are in doubt" in pd.LEFT_OUT["valid"]


def test_a_point_outside_antwebs_box_of_the_proposed_state_is_not_written():
    # 2026-11-25: the first apply run wrote Tarawa (173.0 E) as Phoenix Islands and West Caprivi (21.7 E) as Zambezi;
    # AntWeb's boxes of both states were right. Canton Island and Sangwali are inside their boxes and are written;
    # a state with no box is not guarded.
    rows = [{"specimen_code": "tarawa", "field": "adm1", "current_value": "", "proposed_value": "Phoenix Islands", "country": "Kiribati",
             "latitude": "1.33", "longitude": "172.98"},
            {"specimen_code": "canton", "field": "adm1", "current_value": "", "proposed_value": "Phoenix Islands", "country": "Kiribati",
             "latitude": "-2.81", "longitude": "-171.67"},
            {"specimen_code": "mahango", "field": "adm1", "current_value": "", "proposed_value": "Zambezi", "country": "Namibia",
             "latitude": "-18.2", "longitude": "21.7"},
            {"specimen_code": "sangwali", "field": "adm1", "current_value": "", "proposed_value": "Zambezi", "country": "Namibia",
             "latitude": "-18.2", "longitude": "23.6"},
            {"specimen_code": "nobox", "field": "adm1", "current_value": "", "proposed_value": "Erongo", "country": "Namibia",
             "latitude": "-22.0", "longitude": "15.0"},
            {"specimen_code": "nopoint", "field": "adm1", "current_value": "", "proposed_value": "Zambezi", "country": "Namibia"}]
    with tempfile.TemporaryDirectory() as d:
        rows = pd.rows_of(corrections_file(d, rows), "2026-11-25", "report")
    valid = {("Kiribati", "Phoenix Islands"), ("Namibia", "Zambezi"), ("Namibia", "Erongo")}
    now = {r["code"]: (r["country"], "") for r in rows}
    boxes = {("Kiribati", "Phoenix Islands"): pd.box_of("-174.6, -4.7, -170.6, -2.7"), ("Namibia", "Zambezi"): pd.box_of("(23.3, -18.5, 25.3, -17.4)")}
    todo, why = pd.eligible(rows, valid, now, set(), boxes)
    assert [r["code"] for r in todo] == ["canton", "sangwali", "nobox", "nopoint"] and why == {"box": 2}, (todo, why)
    assert pd.eligible(rows, valid, now, set())[1] == {}                       # without boxes, as before
    # the box as AntWeb reads it: four numbers, 0.2 degree of grace, across the 180 meridian when left is east of right
    assert pd.box_of("") is None and pd.box_of("null") is None and pd.box_of("0, 0, 0, 0") is None and pd.box_of("1, 2, 3") is None
    assert pd.within_box((10, 10, 20, 20), 20.19, 9.81) and not pd.within_box((10, 10, 20, 20), 20.21, 15)
    fiji = (177.0, -19.2, -178.2, -16.1)
    assert pd.within_box(fiji, -17.0, 179.9) and pd.within_box(fiji, -17.0, -179.5) and not pd.within_box(fiji, -17.0, 170.0)


def test_the_apply_sql_backs_up_first_and_writes_only_unchanged_states():
    with tempfile.TemporaryDirectory() as d:
        rows = pd.rows_of(corrections_file(d, APPLY[:2]), "2026-11-23", "report")
    sql = pd.apply_statements(rows, "2026-11-23")
    # the backup table before the transaction (CREATE TABLE commits); no IF NOT EXISTS: a second run the same day fails, writes nothing
    assert sql[1].startswith("CREATE TABLE place_apply_bak_20261123 ") and sql[2] == "START TRANSACTION" and sql[-1] == "COMMIT"
    sql = sql[:1] + sql[2:3] + sql[1:2] + sql[3:]      # the rest as before
    assert sql[3].startswith("INSERT INTO place_apply_bak_20261123 (code, adm1, adm1_verbatim) SELECT") and "'ok1','ok2'" in sql[3]
    assert sql[4] == ("UPDATE specimen SET adm1_verbatim = IFNULL(adm1_verbatim, adm1), adm1 = 'Menabe' WHERE code = 'ok1'"
                      " AND BINARY IFNULL(adm1, '') = BINARY 'Toliara'"), sql[4]
    assert sql[5].endswith("WHERE code = 'ok2' AND BINARY IFNULL(adm1, '') = BINARY ''"), sql[5]
    assert sql[6].startswith("INSERT INTO place_update (") and ",applied) VALUES " in sql[6] and "'apply'" in sql[6] and sql[6].count(",1)") == 2
    back = pd.rollback_statements("2026-11-23")
    # only where the specimen still has the state that run wrote (a later upload is kept)
    assert ("UPDATE specimen s JOIN place_apply_bak_20261123 b ON b.code = s.code JOIN place_update p ON p.code = s.code AND p.run_date = '2026-11-23'"
            " AND p.mode = 'apply' AND p.applied = 1 AND p.field = 'adm1' SET s.adm1 = b.adm1, s.adm1_verbatim = b.adm1_verbatim, p.applied = 0"
            " WHERE BINARY s.adm1 = BINARY p.proposed") in back, back
    # counted before (locked) and after the UPDATE in the same transaction (review of #389)
    i = [k for k, x in enumerate(back) if x.startswith("UPDATE")][0]
    assert back[i - 1].startswith("SELECT 'before', COUNT(*)") and back[i - 1].endswith("FOR UPDATE") and back[i + 1].startswith("SELECT 'after'")
    # the count before a rollback has the same join and condition as its UPDATE (review of #389)
    join, where = pd.put_back_join("2026-11-23")
    assert pd.rollback_counts("2026-11-23")[1] == "SELECT COUNT(*) FROM " + join + " WHERE " + where
    assert back[i] == "UPDATE " + join + " SET s.adm1 = b.adm1, s.adm1_verbatim = b.adm1_verbatim, p.applied = 0 WHERE " + where
    assert pd.rollback_result([["before", "5"], ["after", "2"]]) == (3, 2)
    assert pd.rollback_result([]) is None and pd.rollback_result(None) is None
    assert not any(x.startswith("UPDATE place_update") for x in back)     # a row kept by the rollback stays applied (review of #388)
    assert back[-1] == "COMMIT"


def test_apply_mode_dry_run_and_guards():
    import io
    import contextlib
    with tempfile.TemporaryDirectory() as d:
        path = corrections_file(d, APPLY)
        args = ["--mode", "apply", "--project", d, "--out-root", os.path.join(d, "out"), "--lock", os.path.join(d, "lock")]
        calls = []
        was, skip = pd.write_sql, pd.SKIP_FILE
        pd.write_sql = lambda *a: calls.append(a[2])
        pd.SKIP_FILE = os.path.join(d, "skip.txt")
        with open(pd.SKIP_FILE, "w", encoding="utf-8") as f:
            f.write("# no country left out\n")
        try:
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                pd.main(args + ["--dry-run"], corrections=path, reader=lambda *a: (VALID, NOW, 0))
            assert not calls and "would apply 3 states" in out.getvalue(), out.getvalue()   # uk too: this skip file names none
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                pd.main(args, corrections=path, reader=lambda *a: (VALID, NOW, 0))
            assert len(calls) == 2 and calls[0][0] == "START TRANSACTION" and calls[1][1].startswith("CREATE TABLE place_apply_bak_"), calls
            assert "applied 3 states" in out.getvalue() and "left out: " in out.getvalue(), out.getvalue()
            calls.clear()
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                pd.main(args, corrections=path, reader=lambda *a: (VALID, NOW, 3))   # already applied today
            assert len(calls) == 1 and "today's apply run already wrote 3" in out.getvalue(), out.getvalue()
            calls.clear()
            limit = pd.MAX_APPLY
            pd.MAX_APPLY = 2
            try:
                with contextlib.redirect_stdout(io.StringIO()):
                    pd.main(args, corrections=path, reader=lambda *a: (VALID, NOW, 0))
            except SystemExit as e:
                assert "over the limit" in str(e)
            else:
                raise AssertionError("over the limit must stop the apply")
            finally:
                pd.MAX_APPLY = limit
            assert len(calls) == 1, calls          # the report rows only
            # --max-apply N sets the limit for that run only (review of #412): 2 refuses, 3 writes, the default is unchanged
            calls.clear()
            try:
                with contextlib.redirect_stdout(io.StringIO()):
                    pd.main(args + ["--max-apply", "2"], corrections=path, reader=lambda *a: (VALID, NOW, 0))
            except SystemExit as e:
                assert "apply REFUSED: 3 changes, over the limit of 2" in str(e), e
            else:
                raise AssertionError("--max-apply 2 must refuse 3 changes")
            assert len(calls) == 1, calls
            calls.clear()
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                pd.main(args + ["--max-apply", "3"], corrections=path, reader=lambda *a: (VALID, NOW, 0))
            assert len(calls) == 2 and "applied 3 states" in out.getvalue(), out.getvalue()
            assert pd.MAX_APPLY == limit
            # a skip file that cannot be read stops the apply (review of #384)
            calls.clear()
            os.remove(pd.SKIP_FILE)
            try:
                with contextlib.redirect_stdout(io.StringIO()):
                    pd.main(args, corrections=path, reader=lambda *a: (VALID, NOW, 0))
            except SystemExit as e:
                assert "skip file" in str(e), e
            else:
                raise AssertionError("a missing skip file must stop the apply")
            assert len(calls) == 1, calls          # the report rows only
        finally:
            pd.write_sql, pd.SKIP_FILE = was, skip


def test_the_skip_file_names_countries():
    skip = pd.skipped_countries()
    assert {"United Kingdom", "Slovenia", "Uganda", "Guadeloupe"} <= skip and "Madagascar" not in skip, skip
    # held for a day, released 2026-11-24: Fiji's province file is the statistics office's (pacificdata.org) and puts 22
    # known places right; where it stands against stored provinces, the stored ones disagree among themselves (the same
    # point filed under Ba and Naitasiri). Belize: two files agree; the one conflict is a peak 0.4 km from the line.
    assert not ({"Fiji", "Belize"} & skip), skip
    assert all("#" not in c and c == c.strip() for c in skip)


def test_rollback_takes_only_a_date():
    try:
        pd.main(["--rollback", "2026-11-23; drop table specimen", "--dry-run"])
    except ValueError:
        pass
    else:
        raise AssertionError("a rollback date that is not a date must be refused")
    for other in ("20261123", "2026-W47-1"):        # dates fromisoformat takes, but not the backup table's form
        try:
            pd.main(["--rollback", other, "--dry-run"])
        except ValueError:
            pass
        else:
            raise AssertionError(other + " must be refused")


def test_dry_run_writes_nothing_and_says_so():
    with tempfile.TemporaryDirectory() as d:
        path = corrections_file(d, SAMPLE)
        calls = []
        was = pd.write_sql
        pd.write_sql = lambda *a: calls.append(a)
        try:
            import io
            import contextlib
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                assert pd.main(["--dry-run", "--project", d, "--out-root", os.path.join(d, "out"), "--lock", os.path.join(d, "lock")], corrections=path) == 0
            assert not calls
            line = out.getvalue()
            assert "place-daily report: 3 proposals (adm1 2, country 1) in 2 groups" in line and "dry run, nothing written" in line, line
            with contextlib.redirect_stdout(io.StringIO()):
                pd.main(["--project", d, "--out-root", os.path.join(d, "out"), "--lock", os.path.join(d, "lock")], corrections=path)
            assert len(calls) == 1 and calls[0][2][0] == "START TRANSACTION"
        finally:
            pd.write_sql = was


def test_a_second_run_stops_at_once():
    import fcntl
    with tempfile.TemporaryDirectory() as d:
        lock = os.path.join(d, "lock")
        with open(lock, "w") as held:
            fcntl.flock(held, fcntl.LOCK_EX)
            try:
                pd.main(["--dry-run", "--project", d, "--out-root", os.path.join(d, "out"), "--lock", lock], corrections=corrections_file(d, SAMPLE))
            except SystemExit as e:
                assert "another run holds" in str(e), e
            else:
                raise AssertionError("a held lock must stop the run")


def test_old_folders_are_pruned():
    with tempfile.TemporaryDirectory() as d:
        for i in range(20):
            os.makedirs(os.path.join(d, "2026-10-%02d" % (i + 1)))
        pd.prune(d, 14)
        left = sorted(os.listdir(d))
        assert len(left) == 14 and left[0] == "2026-10-07", left


def test_the_cron_line_runs_apply_mode_on_production():
    cron = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "tools", "place", "antweb-place-daily.cron")).read()
    line = [l for l in cron.splitlines() if l and not l.startswith(("#", "SHELL", "PATH", "MAILTO"))]
    # apply mode since 2026-11-24 (Brian Fisher: "go ahead as suggested"); never --max-apply here: a day over 5,000 is for a person
    assert line[0].startswith("10 9 * * * root /usr/bin/python3 /root/antweb/tools/place/place_daily.py --mode apply >/dev/null 2>&1"), line
    assert "--max-apply" not in cron


def test_rollback_counts_and_refuses_when_nothing_is_applied():
    import io
    import contextlib
    calls = []
    was = pd.write_sql
    # the database answers the counts of the rollback itself: 5 applied before the UPDATE, 1 after (an upload came between)
    pd.write_sql = lambda *a: calls.append(a[2]) or [["before", "5"], ["after", "1"]]
    try:
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            pd.main(["--rollback", "2026-11-23", "--project", "/x"], counter=lambda q: 5 if "FROM place_update WHERE" in q else 3)
        assert len(calls) == 1 and "4 specimens put back" in out.getvalue() and "1 not put back" in out.getvalue(), out.getvalue()
        calls.clear()
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            pd.main(["--rollback", "2026-11-23", "--project", "/x", "--dry-run"], counter=lambda q: 5 if "FROM place_update WHERE" in q else 3)
        assert not calls and "would put back 3 specimens; 2 not put back" in out.getvalue(), out.getvalue()
        try:   # every row left changed since (or rolled back already but some kept): refused
            pd.main(["--rollback", "2026-11-23", "--project", "/x"], counter=lambda q: 2 if "FROM place_update WHERE" in q else (1 if "information_schema" in q else 0))
        except SystemExit as e:
            assert "nothing to put back" in str(e), e
        else:
            raise AssertionError("a rollback that puts nothing back must be refused")
        assert not calls
        try:   # rolled back already, or the rows aged out: refused, nothing written
            pd.main(["--rollback", "2026-11-23", "--project", "/x"], counter=lambda q: 0)
        except SystemExit as e:
            assert "no applied rows" in str(e), e
        else:
            raise AssertionError("a rollback with nothing applied must be refused")
        assert not calls
        # the database says 0 were put back (an upload came between the count and the UPDATE): a failure, not a success
        pd.write_sql = lambda *a: calls.append(a[2]) or [["before", "2"], ["after", "2"]]
        try:
            pd.main(["--rollback", "2026-11-23", "--project", "/x"], counter=lambda q: 5 if "FROM place_update WHERE" in q else 3)
        except SystemExit as e:
            assert "nothing put back" in str(e), e
        else:
            raise AssertionError("a rollback that put nothing back must not end as a success")
        # the counts did not come back: a warning that it was committed, not a FAILED line
        pd.write_sql = lambda *a: calls.append(a[2]) or []
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            assert pd.main(["--rollback", "2026-11-23", "--project", "/x"], counter=lambda q: 5 if "FROM place_update WHERE" in q else 3) == 0
        assert "committed; its counts were not read" in out.getvalue(), out.getvalue()
        calls.clear()
        try:   # the backup table was dropped: said plainly, nothing written
            pd.main(["--rollback", "2026-11-23", "--project", "/x"], counter=lambda q: 0 if "information_schema" in q else 5)
        except SystemExit as e:
            assert "backup table place_apply_bak_20261123 is missing" in str(e), e
        else:
            raise AssertionError("a rollback without its backup table must be refused")
        assert not calls
    finally:
        pd.write_sql = was


if __name__ == "__main__":
    n = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            n += 1
    print(n, "tests passed")
