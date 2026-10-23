"""The table-list generator of docs/data-model.md (tools/datamodel/list_tables.py), on small cases.

The generator replays the db/upgrade files, which mix SQL with notes and pasted output. These cases pin the
parts of its parser that the real files need: a missing semicolon, an apostrophe in a note, comments, ALTER TABLE
... RENAME TO, RENAME TABLE, CREATE TABLE ... LIKE and AS SELECT, views, a table dropped and later altered, and a
CREATE TABLE built from Java string literals and a constant (and not from a comment). Also --check on a
duplicate heading, and (2026-10-22) the live snapshot: the catch-up file's guarded procedure calls and DELIMITER
blocks, the allow-list of differences, and the dated-backup pattern shared with SchemaCheck.java.

Run: python3 tests/unit/test_datamodel_list_tables.py   (also in .github/workflows/tests.yaml)
"""
import contextlib
import io
import os
import re
import sys
import tempfile
from pathlib import Path

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
sys.path.insert(0, os.path.join(ROOT, "tools", "datamodel"))
import list_tables as L  # noqa: E402


def replay(sql):
    schema = L.Schema()
    for stmt in L.sql_statements(sql):
        L.apply_statement(schema, stmt, "test.sql", "db/upgrade")
    return schema


def test_missing_semicolon_and_note_with_apostrophe():
    s = replay("create table a (id int, name varchar(10))\n"
               "This doesn't matter, it's a note\n"
               "alter table a add column b int;\n"
               "-- alter table a add column commented int;\n"
               "# alter table a add column hashed int;\n"
               "alter table a add column c varchar(5); # trailing note\n")
    assert list(s.tables["a"]["columns"]) == ["id", "name", "b", "c"], s.tables["a"]["columns"]


def test_apostrophe_in_a_note_does_not_hide_comments_or_columns():
    s = replay("create table n (a int, b int) -- the curator's table\n"
               "alter table n add column c int; # Brian's request; also add column d\n")
    assert list(s.tables["n"]["columns"]) == ["a", "b", "c"], s.tables["n"]["columns"]


def test_rename_of_a_dropped_table_is_ignored():
    s = replay("create table r (a int);\ndrop table r;\nrename table r to r2;\n")
    assert "r2" not in s.tables and "r" in s.dropped


def test_rename_and_change_and_drop_column():
    s = replay("create table old (uid int, x int);\n"
               "alter table old change column uid id int(11);\n"
               "alter table old drop column x;\n"
               "alter table old rename to newer;\n"
               "create table t2 (k int);\n"
               "rename table t2 to t3;\n")
    assert "old" not in s.tables and "newer" in s.tables
    assert list(s.tables["newer"]["columns"]) == ["id"]
    assert "t3" in s.tables and "t2" in s.dropped


def test_like_and_as_select():
    s = replay("create table base (a int, b int);\n"
               "create table copy1 like base;\n"
               "create table copy2 as select * from base;\n"
               "create table pick as select distinct l.a login_id, l.b from base l;\n")
    assert list(s.tables["copy1"]["columns"]) == ["a", "b"]
    assert list(s.tables["copy2"]["columns"]) == ["a", "b"]
    assert list(s.tables["pick"]["columns"]) == ["login_id", "b"]


def test_comments_and_parenthesised_select():
    s = replay("create table p (a int, b int);\n"
               "alter table p add column c int; --\n"
               "alter table p add column d int; --\tnote\n"
               "create table q (select a, b from p);\n")
    assert list(s.tables["p"]["columns"]) == ["a", "b", "c", "d"], s.tables["p"]["columns"]
    assert list(s.tables["q"]["columns"]) == ["a", "b"], s.tables["q"]["columns"]


def test_java_create_table_in_a_comment_is_ignored():
    java = ('class Y {\n'
            '    String a = "first";\n'
            '    // the old code did create table foo here\n'
            '    /* "create table bar (x int)" */\n'
            '    String url = "http://example.org/create";\n'
            '}\n')
    assert L.java_create_statements(java) == []


def test_columns_that_start_with_a_keyword_and_enum_values():
    s = replay("create table k (id int, kind enum('A','b'));\n"
               "alter table k add column key_count int;\n"
               "alter table k add column unique_id int, add index by_id (id);\n"
               "alter table k drop column primary_x;\n"
               "alter table k drop index by_id;\n")
    assert list(s.tables["k"]["columns"]) == ["id", "kind", "key_count", "unique_id"], s.tables["k"]["columns"]
    assert s.tables["k"]["columns"]["kind"] == "enum('A','b')"


def test_java_text_block():
    java = ('class Z {\n'
            '    static final String DDL = """\n'
            '        CREATE TABLE IF NOT EXISTS block_t (\n'
            '          id int NOT NULL, -- a note // not a Java comment\n'
            '          url varchar(200)\n'
            '        )""";\n'
            '}\n')
    stmts = L.java_create_statements(java)
    assert len(stmts) == 1, stmts
    s = L.Schema()
    L.apply_statement(s, L.sql_statements(stmts[0])[0], "Z.java", "code")
    assert list(s.tables["block_t"]["columns"]) == ["id", "url"], s.tables


def test_views_and_drop():
    s = replay("CREATE ALGORITHM=UNDEFINED DEFINER=`x`@`localhost` SQL SECURITY DEFINER VIEW `v1` AS select 1;\n"
               "create view v2 as select 2;\n"
               "drop view if exists v2;\n"
               "alter table v1 convert to character set utf8;\n")
    assert set(s.views) == {"v1"} and "v2" in s.dropped_views
    assert "v1" not in s.tables


def test_alter_after_drop_stays_dropped_unless_listed():
    s = replay("create table gone (a int);\ndrop table gone;\nalter table gone add column b int;\n")
    assert "gone" not in s.tables and "gone" in s.dropped
    s = replay("create table taxon_country (a int);\ndrop table taxon_country;\nalter table taxon_country engine = InnoDB;\n")
    assert "taxon_country" in s.tables, "taxon_country is listed in ALTERED_AFTER_DROP"


def test_java_create_table_with_constant():
    java = ('public class X {\n'
            '    public static final String TABLE = "job_x";\n'
            '    String ddl() {\n'
            '        return "CREATE TABLE IF NOT EXISTS " + TABLE + " ("\n'
            '            + " id int NOT NULL,"\n'
            '            + " note varchar(20) DEFAULT \\"caf\u00e9\\","\n'
            '            + " PRIMARY KEY (id))";\n'
            '    }\n'
            '    void backup(String backup) { run("create table " + backup + " like login"); }\n'
            '}\n')
    stmts = L.java_create_statements(java)
    assert stmts[0].startswith("CREATE TABLE IF NOT EXISTS job_x ("), stmts[0]
    assert "caf\u00e9" in stmts[0], "non-ASCII text in a literal is kept"
    s = L.Schema()
    s.tables["login"] = {"columns": {"id": "int"}, "created": "x", "origin": "x", "history": []}
    for stmt in stmts:
        L.apply_statement(s, stmt, "X.java", "code")
    assert list(s.tables["job_x"]["columns"]) == ["id", "note"]
    assert "*" not in s.tables, "a name made entirely at run time is not listed"


def check_output(doc_text):
    old = L.DOC
    try:
        with tempfile.TemporaryDirectory() as d:
            doc = Path(d) / "data-model.md"
            doc.write_text(doc_text, encoding="utf-8")
            L.DOC = doc
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                code = L.main(["--check"])
            return code, out.getvalue()
    finally:
        L.DOC = old


def test_check_reports_missing_unknown_and_dropped_sections():
    live = set(L.read_live(L.latest_snapshot())[1])
    every = "".join(f"### `{n}`\n" for n in sorted(set(L.build().tables) | set(L.build().views) | live))
    code, out = check_output(every)
    assert code == 0, out
    code, out = check_output(every.replace("### `taxon`\n", ""))
    assert code == 1 and "table taxon (" in out and "has no section" in out, out
    code, out = check_output(every + "### `no_such_table`\n")
    assert code == 1 and "`no_such_table` names a table" in out, out
    code, out = check_output(every + "### `groups`\n")
    assert code == 1 and "`groups`: the table was dropped" in out, out
    code, out = check_output(every.replace("### `ant_group_abbrev_bak_20261020`\n", ""))
    assert code == 1 and "table ant_group_abbrev_bak_20261020 (live only" in out, \
        "a table only production has needs a section too: " + out
    # the old tables dropped on 2026-10-23 (db/upgrade/8.115/2026-10-23-...) may not keep a section
    code, out = check_output(every + "### `junkspec`\n")
    assert code == 1 and "`junkspec`: the table was dropped by db/upgrade/8.115/2026-10-23" in out, out


def test_empty_names_in_drop_do_not_crash():
    s = replay("create table a (x int);\ndrop table a, ;\ndrop view if exists ,;\n")
    assert "a" in s.dropped


def test_check_reports_a_heading_written_twice():
    old = L.DOC
    try:
        with tempfile.TemporaryDirectory() as d:
            doc = Path(d) / "data-model.md"
            doc.write_text("### `taxon`\n\ntext\n\n### `taxon`\n", encoding="utf-8")
            L.DOC = doc
            assert L.doc_headings() == ["taxon", "taxon"]
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                assert L.main(["--check"]) == 1
            assert "table taxon has two sections" in out.getvalue()
    finally:
        L.DOC = old


# --- the live snapshot (2026-10-22) -----------------------------------------------------------------------------

def test_parenthesised_add_column_keeps_its_size():
    s = replay("create table om (id int);\nalter table om add column (title varchar(1000));\n")
    assert s.tables["om"]["columns"]["title"] == "varchar(1000)", s.tables["om"]["columns"]


def test_catch_up_calls_and_delimiter_blocks():
    s = replay("DELIMITER //\n"
               "CREATE PROCEDURE schema_add_column(IN t VARCHAR(64))\n"
               "BEGIN\n"
               "  SET @schema_sql = CONCAT('ALTER TABLE `', t, '` ADD COLUMN x int');\n"
               "alter table never add column from_the_body int;\n"
               "END //\n"
               "DELIMITER ;\n"
               "create table c (a int, b int, gone int);\n"
               "create view v as select 1;\n"
               "CALL schema_add_column('c', 'New_Col', 'varchar(30)');\n"
               "CALL schema_modify_column('c', 'a', 'bigint');  -- a note\n"
               "CALL schema_drop_column('c', 'gone');\n"
               "CALL schema_drop_view('v');\n"
               "CALL schema_drop_view('not_a_view');\n"
               "create table old_copy (a int);\n"
               "CALL schema_drop_empty_table('old_copy');\n"
               "DROP PROCEDURE IF EXISTS schema_add_column;\n")
    assert s.tables["c"]["columns"] == {"a": "bigint", "b": "int", "new_col": "varchar(30)"}, s.tables["c"]["columns"]
    assert "never" not in s.tables, "a procedure body is not a table change"
    assert "v" not in s.views and "v" in s.dropped_views
    assert "old_copy" not in s.tables and "old_copy" in s.dropped


def test_norm_type():
    assert L.norm_type("int(11)") == L.norm_type("INT")
    assert L.norm_type("long") == "mediumtext"
    assert L.norm_type("int(10) unsigned") == "int unsigned"
    assert L.norm_type("varchar(100)") != L.norm_type("varchar(128)")


def snapshot_file(rows):
    d = tempfile.mkdtemp()
    path = Path(d) / "live-schema-2026-10-22.tsv"
    path.write_text("".join("\t".join(r) + "\n" for r in rows), encoding="utf-8")
    return path


def test_snapshot_errors_and_allow_list():
    s = replay("create table taxon (taxon_name varchar(128), species_count int);\n"
               "create table only_repo (a int);\n"
               "create view v1 as select 1;\n")
    s.tables["login_inactive_backup_*"] = {"columns": {"id": "int"}, "created": "x", "origin": "code", "history": []}
    rows = [("taxon", "taxon_name", "varchar(128)", "BASE TABLE"), ("taxon", "species_count", "int(11)", "BASE TABLE"),
            ("taxon", "extra", "int", "BASE TABLE"), ("v1", "x", "int", "VIEW"),
            ("ant_group_abbrev_bak_20261020", "id", "int", "BASE TABLE"),
            ("login_inactive_backup_20261018", "id", "int", "BASE TABLE"),
            ("junkspec", "code", "varchar(128)", "BASE TABLE"), ("darwin_core_2_test", "x", "int", "VIEW"),
            ("handmade", "id", "int", "BASE TABLE")]
    old = (L.LIVE_ONLY_UNUSED, L.LIVE_ONLY_UNUSED_VIEWS)
    try:
        L.LIVE_ONLY_UNUSED, L.LIVE_ONLY_UNUSED_VIEWS = {"junkspec"}, {"darwin_core_2_test"}
        errors = L.snapshot_errors(s, snapshot_file(rows))
    finally:
        L.LIVE_ONLY_UNUSED, L.LIVE_ONLY_UNUSED_VIEWS = old
    text = "\n".join(errors)
    assert len(errors) == 3, text
    assert "table handmade is live" in text, text
    assert "table only_repo is in the repository's SQL but not live" in text, text
    assert "column taxon.extra is live" in text, text
    rows[1] = ("taxon", "species_count", "bigint", "BASE TABLE")
    errors = L.snapshot_errors(s, snapshot_file(rows))
    assert any("taxon.species_count int -> bigint" in e for e in errors), errors
    try:  # an entry left on the allow-list after its table was dropped from production (and from the snapshot)
        L.LIVE_ONLY_UNUSED, L.LIVE_ONLY_UNUSED_VIEWS = {"junkspec", "dropped_since"}, {"darwin_core_2_test"}
        errors = L.snapshot_errors(s, snapshot_file(rows))
    finally:
        L.LIVE_ONLY_UNUSED, L.LIVE_ONLY_UNUSED_VIEWS = old
    assert any("LIVE_ONLY_UNUSED names dropped_since" in e for e in errors), "an allow-list entry the snapshot does not have is reported"


def test_upgrade_files_newer_than_the_snapshot_are_pending():
    s = L.Schema()
    for stmt, src in (("create table taxon (taxon_name varchar(128))", "db/upgrade/8.0/2020-01-01.sql"),
                      ("alter table taxon add column old_col int", "db/upgrade/8.0/2020-01-01.sql"),
                      ("alter table taxon add column new_col int", "db/upgrade/8.116/2026-11-02-new.sql"),
                      ("alter table taxon add column gone_col int", "db/upgrade/8.0/2020-01-01.sql"),
                      ("create table old_t (id int)", "db/upgrade/8.0/2020-01-01.sql"),
                      ("create view old_v as select 1", "db/upgrade/8.0/2020-01-01.sql"),
                      ("alter table taxon drop column gone_col", "db/upgrade/8.116/2026-11-02-new.sql"),
                      ("drop table old_t", "db/upgrade/8.116/2026-11-02-new.sql"),
                      ("drop view old_v", "db/upgrade/8.116/2026-11-02-new.sql"),
                      ("create view new_v as select 1", "db/upgrade/8.116/2026-11-02-new.sql"),
                      ("alter table taxon add column same_day int", "db/upgrade/8.115/2026-10-22-x.sql"),
                      ("create table brand_new (id int)", "db/upgrade/8.116/2026-11-02-new.sql")):
        L.apply_statement(s, stmt, src, "db/upgrade")
    path = snapshot_file([("taxon", "taxon_name", "varchar(128)", "BASE TABLE"), ("taxon", "gone_col", "int", "BASE TABLE"),
                          ("old_t", "id", "int", "BASE TABLE"), ("old_v", "x", "int", "VIEW")])
    old = (L.LIVE_ONLY_UNUSED, L.LIVE_ONLY_UNUSED_VIEWS)
    try:
        L.LIVE_ONLY_UNUSED, L.LIVE_ONLY_UNUSED_VIEWS = set(), set()
        errors = L.snapshot_errors(s, path)
    finally:
        L.LIVE_ONLY_UNUSED, L.LIVE_ONLY_UNUSED_VIEWS = old
    assert sorted(errors) == ["column taxon.old_col is in the repository's SQL but not live (snapshot)",
                              "column taxon.same_day is in the repository's SQL but not live (snapshot)"], errors
    L.apply_statement(s, "CREATE TABLE IF NOT EXISTS code_made (id int)", "src/X.java", "code")
    assert "code_made" not in "\n".join(L.snapshot_errors(s, path)), "a table the code creates is pending"
    notes = L.pending_notes(s, path)[0]
    assert len(notes) == 7 and all("newer than the snapshot" in n for n in notes), notes


def test_backup_pattern():
    for t in ("ant_group_abbrev_bak_20261020", "login_deleted_2026_10_16", "login_project_20261018",
              "specimen_badcode_backup_20261018", "login_spam_deleted_2026_10_14", "proj_taxon20170422",
              "description_sanitize_backup_20261016_200412"):
        assert L.BACKUP.search(t), t
    for t in ("taxon", "image_backup", "museumbak", "darwin_core_2", "geolocale_taxon"):
        assert not L.BACKUP.search(t), t


def test_backup_pattern_is_the_same_in_schema_check_java():
    java = (Path(ROOT) / "src/org/calacademy/antweb/util/SchemaCheck.java").read_text(encoding="utf-8")
    m = re.search(r'Pattern BACKUP = Pattern\.compile\("((?:[^"\\]|\\.)*)"\)', java)
    assert m, "SchemaCheck.BACKUP not found"
    assert m.group(1).replace("\\\\", "\\") == L.BACKUP_PATTERN, (m.group(1), L.BACKUP_PATTERN)


def test_repository_matches_the_committed_snapshot():
    snapshot = L.latest_snapshot()
    assert snapshot is not None and snapshot.parent == Path(ROOT).resolve() / "doc" / "schema", snapshot
    errors = L.snapshot_errors(L.build(), snapshot)
    assert errors == [], "\n".join(errors)


def test_catch_up_file_says_it_is_safe_on_production():
    text = (Path(ROOT) / "db/upgrade/8.115/2026-10-22-schema-catch-up.sql").read_text(encoding="utf-8")
    assert "SAFE TO RUN ON PRODUCTION: IT CHANGES NOTHING THERE" in text
    body = L.strip_sql_comments(text)
    # nothing unguarded: every ALTER, DROP and CREATE TABLE goes through a guard
    assert not re.search(r"(?im)^\s*ALTER\s+TABLE", body)
    assert not re.search(r"(?im)^\s*DROP\s+TABLE", body), "tables are dropped only when empty (schema_drop_empty_table)"
    assert not re.search(r"(?im)^\s*CREATE\s+TABLE\s+(?!IF\s+NOT\s+EXISTS)", body)
    assert not re.search(r"(?im)^\s*DROP\s+VIEW", body)


def main():
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("ok", name)


if __name__ == "__main__":
    main()
