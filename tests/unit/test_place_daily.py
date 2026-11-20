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


def test_the_apply_sql_backs_up_first_and_writes_only_unchanged_states():
    with tempfile.TemporaryDirectory() as d:
        rows = pd.rows_of(corrections_file(d, APPLY[:2]), "2026-11-23", "report")
    sql = pd.apply_statements(rows, "2026-11-23")
    assert sql[1] == "START TRANSACTION" and sql[-1] == "COMMIT"
    assert sql[2].startswith("CREATE TABLE place_apply_bak_20261123 ")    # no IF NOT EXISTS: a second run the same day fails, writes nothing
    assert sql[3].startswith("INSERT INTO place_apply_bak_20261123 (code, adm1, adm1_verbatim) SELECT") and "'ok1','ok2'" in sql[3]
    assert sql[4] == ("UPDATE specimen SET adm1_verbatim = IFNULL(adm1_verbatim, adm1), adm1 = 'Menabe' WHERE code = 'ok1'"
                      " AND BINARY IFNULL(adm1, '') = BINARY 'Toliara'"), sql[4]
    assert sql[5].endswith("WHERE code = 'ok2' AND BINARY IFNULL(adm1, '') = BINARY ''"), sql[5]
    assert sql[6].startswith("INSERT INTO place_update (") and ",applied) VALUES " in sql[6] and "'apply'" in sql[6] and sql[6].count(",1)") == 2
    back = pd.rollback_statements("2026-11-23")
    assert "UPDATE specimen s JOIN place_apply_bak_20261123 b ON b.code = s.code SET s.adm1 = b.adm1, s.adm1_verbatim = b.adm1_verbatim" in back
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
        pd.SKIP_FILE = os.path.join(d, "none.txt")
        try:
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                pd.main(args + ["--dry-run"], corrections=path, reader=lambda *a: (VALID, NOW, 0))
            assert not calls and "would apply 3 states" in out.getvalue(), out.getvalue()   # uk too: the skip file is not read here
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                pd.main(args, corrections=path, reader=lambda *a: (VALID, NOW, 0))
            assert len(calls) == 2 and calls[0][0] == "START TRANSACTION" and calls[1][2].startswith("CREATE TABLE place_apply_bak_"), calls
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
        finally:
            pd.write_sql, pd.SKIP_FILE = was, skip


def test_the_skip_file_names_countries():
    skip = pd.skipped_countries()
    assert {"United Kingdom", "Slovenia", "Uganda", "Guadeloupe"} <= skip and "Madagascar" not in skip, skip
    assert all("#" not in c and c == c.strip() for c in skip)


def test_rollback_takes_only_a_date():
    try:
        pd.main(["--rollback", "2026-11-23; drop table specimen", "--dry-run"])
    except ValueError:
        pass
    else:
        raise AssertionError("a rollback date that is not a date must be refused")


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


def test_the_cron_line_runs_report_mode_on_production():
    cron = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "tools", "place", "antweb-place-daily.cron")).read()
    line = [l for l in cron.splitlines() if l and not l.startswith(("#", "SHELL", "PATH", "MAILTO"))]
    assert line[0].startswith("10 9 * * * root /usr/bin/python3 /root/antweb/tools/place/place_daily.py >/dev/null 2>&1"), line


if __name__ == "__main__":
    n = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            n += 1
    print(n, "tests passed")
