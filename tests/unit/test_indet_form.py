"""Indet form tool (tools/data/indet_form.py): names, SQL text and the command flow, without a database.

Decision of Brian Fisher, 2026-10-28: one form for a specimen identified only to genus, "Pheidole (indet)". The tool
renames the old form "<genus> indet" (also Indet, indet., undet) in every table that keys on the name. Checked:
  - which names are in the old form and what they become; three-word names (2026-10-29): "<g> indet <x>" becomes
    "<g> (indet)", "<g> <sp> indet" becomes "<g> <sp>" (status as an upload gives it); four words are left by hand;
  - the clash query counts a row that meets the "(indet)" name, or an earlier old name with the same new name;
  - run deletes clashing rows only in the merge tables, gives taxon and specimen rows species "(indet)" and status
    indetermined, and checks every count before the commit; rollback restores from the dated backup tables;
  - preview sends only select statements; production needs --production; a clash in a rename table stops run.

Run: python3 tests/unit/test_indet_form.py   (also in .github/workflows/tests.yaml)
"""
import contextlib
import importlib.util
import io
import os
import re
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
spec = importlib.util.spec_from_file_location("indet_form", os.path.join(ROOT, "tools", "data", "indet_form.py"))
tf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tf)


def test_names():
    assert tf.new_name("myrmicinaepheidole indet") == "myrmicinaepheidole (indet)"
    assert tf.new_name("myrmicinaepheidole Indet") == "myrmicinaepheidole (indet)"
    assert tf.new_name("myrmicinaepheidole indet.") == "myrmicinaepheidole (indet)"
    assert tf.new_name("formicinaecamponotus UNDET.") == "formicinaecamponotus (indet)"
    assert tf.new_name("(myrmicinae) indet") == "(myrmicinae) (indet)"
    for keep in ("myrmicinaepheidole (indet)", "myrmicinaepheidole megacephala", "myrmicinaepheidole indetx",
                 "myrmicinaepheidole indet x y", "myrmicinaepheidole sp.", "myrmicinaeindet", None, "",
                 "myrmicinaepheidole megacephala indetx", "myrmicinaepheidole a b indet"):
        assert tf.new_name(keep) is None, keep
    m = tf.name_map(["myrmicinaepheidole indet", "Myrmicinaepheidole indet", "myrmicinaepheidole indet.",
                     "myrmicinaepheidole (indet)", "myrmicinaepheidole megacephala"])
    # MySQL compares names ignoring case: the two spellings are one name
    assert m == {"Myrmicinaepheidole indet": "Myrmicinaepheidole (indet)",
                 "myrmicinaepheidole indet.": "myrmicinaepheidole (indet)"}, m
    assert tf.not_handled(["formicinaecamponotus indet x y", "myrmicinaepheidole indet", "myrmicinaepheidole (indet)",
                           "formicinaecamponotus x y undet", "formicinaecamponotus x undet",
                           "formicinaecamponotus (indet) x", "formicinaecamponotus a (indet) b"]) == [
        "formicinaecamponotus a (indet) b", "formicinaecamponotus indet x y", "formicinaecamponotus x y undet"]

    # three words (2026-10-29): species indet -> "<g> (indet)", subspecies dropped; subspecies indet -> "<g> <sp>"
    three = {"myrmicinaeocymyrmex indet karooicus": ("myrmicinaeocymyrmex (indet)", tf.SPECIES_INDET),
             "myrmicinaeocymyrmex Undet. karooicus": ("myrmicinaeocymyrmex (indet)", tf.SPECIES_INDET),
             "myrmicinaeocymyrmex (indet) karooicus": ("myrmicinaeocymyrmex (indet)", tf.SPECIES_INDET),
             "myrmicinaeocymyrmex sp. karooicus": ("myrmicinaeocymyrmex (indet)", tf.SPECIES_INDET),
             "myrmicinaeocymyrmex indet indet": ("myrmicinaeocymyrmex (indet)", tf.SPECIES_INDET),
             "ectatomminaegnamptogenys striatula-complex indet": ("ectatomminaegnamptogenys striatula-complex", tf.SUBSPECIES_INDET),
             "ectatomminaegnamptogenys striatula-complex (indet)": ("ectatomminaegnamptogenys striatula-complex", tf.SUBSPECIES_INDET),
             "myrmicinaepheidole megacephala undet.": ("myrmicinaepheidole megacephala", tf.SUBSPECIES_INDET),
             "myrmicinaepheidole indet": ("myrmicinaepheidole (indet)", tf.TWO_WORD)}
    for old, (new, kind) in three.items():
        assert tf.new_name(old) == new, (old, tf.new_name(old))
        assert tf.kind_of(old) == kind, (old, tf.kind_of(old))
    assert tf.not_handled(list(three)) == []
    # status of a "<g> <sp>" name: as an upload gives it
    assert tf.is_morpho("ectatomminaegnamptogenys striatula-complex")
    assert tf.is_morpho("myrmicinaepheidole a") and tf.is_morpho("myrmicinaepheidole jtl001")
    assert not tf.is_morpho("myrmicinaepheidole megacephala") and not tf.is_morpho("myrmicinaepheidole (indet)")
    assert tf.species_status("ectatomminaegnamptogenys striatula-complex", "indetermined") == "morphotaxon"
    assert tf.species_status("myrmicinaepheidole megacephala", "indetermined") == "unrecognized"
    assert tf.species_status("myrmicinaepheidole megacephala", "valid") == "valid"
    assert tf.species_status("myrmicinaepheidole megacephala", None) == "unrecognized"
    print("Names: indet, Indet, indet., undet become (indet); three-word names follow the upload's rules;"
          " four words are left for a look by hand.")


MAP = {"myrmicinaepheidole indet": "myrmicinaepheidole (indet)",
       "myrmicinaepheidole indet.": "myrmicinaepheidole (indet)",
       "formicinaecamponotus undet": "formicinaecamponotus (indet)"}
KEYS = {"taxon": [("taxon_name",)], "proj_taxon": [("project_name", "taxon_name")],
        "specimen": [], "description_edit": [("taxon_name", "title")]}
TABLES = ["taxon", "proj_taxon", "specimen", "description_edit"]


def test_sql():
    m = tf.map_sql(MAP)
    assert m.startswith("(select 0 as seq, 'formicinaecamponotus undet' as old_name")
    assert "select 2, 'myrmicinaepheidole indet.', 'myrmicinaepheidole (indet)'" in m
    assert tf.sql_string("it's") == "'it''s'"
    c = tf.clash_sql("proj_taxon", MAP, KEYS["proj_taxon"])
    assert "b.taxon_name = m.new_name and b.project_name <=> a.project_name" in c
    assert "m2.new_name = m.new_name and m2.seq < m.seq" in c
    assert tf.clash_sql("specimen", MAP, []) is None
    assert "where b.taxon_name = m.new_name)" in tf.clash_sql("taxon", MAP, KEYS["taxon"])

    before = {"taxon": 4, "proj_taxon": 5, "specimen": 5, "description_edit": 1}
    after = {"taxon": 2, "proj_taxon": 2, "specimen": 5, "description_edit": 1}
    b = tf.backup_sql("20261028", TABLES, MAP, before)
    assert "create table indet_form_bak_20261028_names" in b
    assert "create table indet_form_bak_20261028_taxon as select * from taxon where (taxon_name in (" in b
    assert "or current_valid_name in (" in b and "or parent_taxon_name in (" in b
    assert "original_taxon_name" not in b, "original_taxon_name records the uploaded name: never changed"
    assert "create table indet_form_bak_20261028_after" in b
    assert "1 as expected" in b  # description_edit

    r = tf.run_sql("20261028", TABLES, MAP, KEYS, after)
    assert r.index("start transaction;") < r.index("update taxon") < r.index("commit;")
    assert "create table" not in r, "a create table inside the transaction would commit it early"
    assert ("delete a from taxon a join taxon b on b.taxon_name = 'myrmicinaepheidole (indet)'"
            " where a.taxon_name = 'myrmicinaepheidole indet';") in r
    assert ("delete a from proj_taxon a join proj_taxon b on b.taxon_name = 'myrmicinaepheidole (indet)' and "
            "b.project_name <=> a.project_name where a.taxon_name = 'myrmicinaepheidole indet';") in r
    assert "delete a from description_edit" not in r and "delete a from specimen" not in r
    assert ("update specimen set taxon_name = 'myrmicinaepheidole (indet)', species = '(indet)', "
            "status = 'indetermined' where taxon_name = 'myrmicinaepheidole indet';") in r
    assert "update taxon set current_valid_name = 'myrmicinaepheidole (indet)' where current_valid_name = 'myrmicinaepheidole indet';" in r
    assert "update description_edit set taxon_name = 'myrmicinaepheidole (indet)' where taxon_name = 'myrmicinaepheidole indet';" in r
    assert "update proj_taxon set taxon_name = 'myrmicinaepheidole (indet)' where" in r and "species = '(indet)'" not in r.split("update proj_taxon", 1)[1].split(";")[0]
    # old names done in sorted order, each one's delete before its update
    olds = re.findall(r"^-- (.+) -> ", r, re.M)
    assert olds == sorted(MAP), olds
    assert r.count(" as check_left_") == 6, r.count(" as check_left_")  # name columns: taxon 3, proj_taxon 1, specimen 1, description_edit 1
    assert "original_taxon_name" not in r
    assert "set @n = (select count(*) from taxon where (taxon_name in" in r and "2 as expected" in r
    assert "insert into indet_form_bak_20261028_after select 'taxon', count(*)" in r

    m3 = {"ectatomminaegnamptogenys striatula-complex indet": "ectatomminaegnamptogenys striatula-complex",
          "myrmicinaeocymyrmex indet karooicus": "myrmicinaeocymyrmex (indet)"}
    r3 = tf.run_sql("20261029", TABLES, m3, KEYS, after,
                    {"ectatomminaegnamptogenys striatula-complex indet": "morphotaxon"})
    assert ("update taxon set taxon_name = 'ectatomminaegnamptogenys striatula-complex', species = 'striatula-complex',"
            " subspecies = null, status = 'morphotaxon', taxarank = 'species'"
            " where taxon_name = 'ectatomminaegnamptogenys striatula-complex indet';") in r3, r3
    assert ("update specimen set taxon_name = 'ectatomminaegnamptogenys striatula-complex', species = 'striatula-complex',"
            " subspecies = null, status = 'morphotaxon' where taxon_name = 'ectatomminaegnamptogenys striatula-complex indet';") in r3
    assert ("update taxon set taxon_name = 'myrmicinaeocymyrmex (indet)', species = '(indet)', subspecies = null,"
            " status = 'indetermined', taxarank = 'species' where taxon_name = 'myrmicinaeocymyrmex indet karooicus';") in r3
    assert ("update specimen set taxon_name = 'myrmicinaeocymyrmex (indet)', species = '(indet)', subspecies = null,"
            " status = 'indetermined' where taxon_name = 'myrmicinaeocymyrmex indet karooicus';") in r3
    # merged into an existing target exactly as the two-word case; other name columns follow
    assert ("delete a from taxon a join taxon b on b.taxon_name = 'myrmicinaeocymyrmex (indet)'"
            " where a.taxon_name = 'myrmicinaeocymyrmex indet karooicus';") in r3
    assert ("update taxon set parent_taxon_name = 'ectatomminaegnamptogenys striatula-complex'"
            " where parent_taxon_name = 'ectatomminaegnamptogenys striatula-complex indet';") in r3
    assert "original_taxon_name" not in r3
    b3 = tf.backup_sql("20261029", TABLES, m3, before)
    assert "'ectatomminaegnamptogenys striatula-complex'" in b3.split("create table indet_form_bak_20261029_taxon", 1)[1].split(";")[0], \
        "the backup holds rows of the new names too (a merge target)"

    rb = tf.rollback_sql("20261028", TABLES, sorted(set(MAP) | set(MAP.values())))
    assert "delete from taxon where (taxon_name in (" in rb
    assert "insert into taxon select * from indet_form_bak_20261028_taxon;" in rb
    assert rb.index("start transaction;") < rb.index("delete from taxon") < rb.index("commit;")
    print("SQL: clashes in merge tables deleted, renames counted before the commit, rollback from the backup.")


class FakeDb:
    """Answers the tool's read queries from a small world; records every statement and the scripts."""

    def __init__(self, clash_desc=0):
        self.reads, self.scripts, self.clash_desc = [], [], clash_desc

    def __call__(self, args, sql, batch=True):
        if not batch:
            self.scripts.append(sql)
            return "committed\n" if "commit;" in sql else "backup tables written\n"
        self.reads.append(sql)
        if "information_schema.tables" in sql and "like" in sql:
            return "indet_form_bak_20261028_names\nindet_form_bak_20261028_after\nindet_form_bak_20261028_taxon\n"
        if "information_schema.tables" in sql:
            return "taxon\nproj_taxon\nspecimen\ndescription_edit\nspecimen_name_update\n"
        if "information_schema.statistics" in sql:
            return "taxon\tPRIMARY\ttaxon_name\nproj_taxon\tPRIMARY\tproject_name,taxon_name\ndescription_edit\tPRIMARY\ttaxon_name,title\n"
        if sql.startswith("select distinct taxon_name from taxon "):
            return ("myrmicinaepheidole indet\nmyrmicinaepheidole indet.\nformicinaecamponotus indet x y\n"
                    "ectatomminaegnamptogenys striatula-complex indet\nmyrmicinaeocymyrmex indet karooicus\n")
        if sql.startswith("select distinct"):
            return ""
        if sql.startswith("select taxon_name, count(*) from taxon"):
            return ("myrmicinaepheidole indet\t1\nmyrmicinaepheidole indet.\t1\nmyrmicinaepheidole (indet)\t1\n"
                    "ectatomminaegnamptogenys striatula-complex indet\t1\nmyrmicinaeocymyrmex indet karooicus\t1\n"
                    "myrmicinaeocymyrmex (indet)\t1\n")
        if sql.startswith("select taxon_name, count(*) from specimen"):
            return ("myrmicinaepheidole indet\t3\nectatomminaegnamptogenys striatula-complex indet\t6\n"
                    "myrmicinaeocymyrmex indet karooicus\t1\n")
        if sql.startswith("select taxon_name, status from taxon"):
            return ("ectatomminaegnamptogenys striatula-complex indet\tindetermined\n"
                    "myrmicinaeocymyrmex indet karooicus\tindetermined\nmyrmicinaepheidole indet\tindetermined\n")
        if sql.startswith("select old_name, new_name"):
            return "myrmicinaepheidole indet\tmyrmicinaepheidole (indet)\n"
        if sql.startswith("select table_name, row_count"):
            return "taxon\t1\n"
        if sql.startswith("select count(*) from description_edit a join"):
            return f"{self.clash_desc}\n"
        if sql.startswith("select count(*)"):
            return "1\n"
        return ""


def project_dir(tmp):
    p = os.path.join(tmp, "antweb-next")
    os.makedirs(os.path.join(p, "etc"))
    with open(os.path.join(p, "etc", "db.properties"), "w") as f:
        f.write("user=antweb\npassword=secret\n")
    return p


def test_command_flow():
    quiet = lambda: contextlib.redirect_stdout(io.StringIO())
    with tempfile.TemporaryDirectory() as tmp:
        project = project_dir(tmp)
        db = FakeDb()
        tf.mysql = db
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            tf.main(["preview", "--project", project])
        text = out.getvalue()
        assert db.scripts == [], "preview changes nothing"
        for sql in db.reads:
            assert sql.lstrip().lower().startswith("select"), sql
        assert "Names in the old form: 4 (2 '<genus> indet'" in text, text
        assert "myrmicinaepheidole indet -> myrmicinaepheidole (indet): 1 taxon, 3 specimens +; status indetermined -> indetermined" in text, text
        # the two production names of 2026-10-29
        assert ("ectatomminaegnamptogenys striatula-complex indet -> ectatomminaegnamptogenys striatula-complex:"
                " 1 taxon, 6 specimens; status indetermined -> morphotaxon") in text, text
        assert ("myrmicinaeocymyrmex indet karooicus -> myrmicinaeocymyrmex (indet): 1 taxon, 1 specimens +;"
                " status indetermined -> indetermined") in text, text
        assert "formicinaecamponotus indet x y" in text and "Not handled" in text
        assert re.search(r"taxon\s+1\s+1\s+merge: 1 deleted", text), text
        assert "specimen_name_update" in text and "history, not changed" in text
        assert "secret" not in text
        assert "Preview only: nothing was changed." in text

        with quiet():
            tf.main(["run", "--project", project, "--yes", "--date", "20261028"])
        assert len(db.scripts) == 2 and "create table indet_form_bak_20261028_names" in db.scripts[0]
        assert "start transaction;" in db.scripts[1]
        assert "status = 'morphotaxon', taxarank = 'species' where taxon_name = 'ectatomminaegnamptogenys striatula-complex indet';" in db.scripts[1]

        with quiet():
            tf.main(["rollback", "--project", project, "--yes", "--date", "20261028"])
        assert "insert into taxon select * from indet_form_bak_20261028_taxon;" in db.scripts[-1]
        try:
            tf.main(["rollback", "--project", project])
            raise AssertionError("rollback without --date")
        except SystemExit as e:
            assert "--date" in str(e)

        db = FakeDb(clash_desc=1)
        tf.mysql = db
        try:
            with quiet():
                tf.main(["run", "--project", project, "--yes", "--date", "20261028"])
            raise AssertionError("run with a clash in a rename table")
        except SystemExit as e:
            assert "Nothing was changed" in str(e)
        assert db.scripts == []
    print("Commands: preview selects only; run backs up then renames; a clash stops run; rollback needs --date.")


def test_production_guard():
    with tempfile.TemporaryDirectory() as tmp:
        project = project_dir(tmp)
        db = FakeDb()
        tf.mysql = db
        real = tf.PRODUCTION_PROJECT
        tf.PRODUCTION_PROJECT = os.path.realpath(project)
        try:
            for cmd in ("preview", "run"):
                try:
                    tf.main([cmd, "--project", project, "--yes"])
                    raise AssertionError(cmd + " on production without --production")
                except SystemExit as e:
                    assert "Run on staging first" in str(e), e
            assert db.reads == [] and db.scripts == []
            with contextlib.redirect_stdout(io.StringIO()):
                tf.main(["preview", "--project", project, "--production"])
            assert db.reads and db.scripts == []
        finally:
            tf.PRODUCTION_PROJECT = real
    print("Production: every step refuses without --production.")


def test_production_paths():
    """/root/antweb may be a link to a dated folder: the link and its target both count as production."""
    with tempfile.TemporaryDirectory() as tmp:
        target = os.path.join(tmp, "antweb-2026-10-30")
        os.mkdir(target)
        link = os.path.join(tmp, "antweb")
        os.symlink(target, link)
        real_project, real_role = tf.PRODUCTION_PROJECT, tf.ROLE_FILE
        tf.PRODUCTION_PROJECT = link
        tf.ROLE_FILE = os.path.join(tmp, "no-role-file")
        try:
            assert tf.is_production(link) and tf.is_production(target)
            assert not tf.is_production(tmp)
            role = os.path.join(tmp, "antweb-role")
            tf.ROLE_FILE = role
            with open(role, "w") as f:
                f.write("staging\n")
            assert not tf.is_production(tmp)
            with open(role, "w") as f:
                f.write("production\n")
            assert tf.is_production(tmp)
        finally:
            tf.PRODUCTION_PROJECT, tf.ROLE_FILE = real_project, real_role
    print("Production: a linked /root/antweb and a role file other than staging count as production.")


def test_runbook():
    text = open(os.path.join(ROOT, "docs", "runbook.md"), encoding="utf-8").read()
    assert "### Indet form" in text
    section = text[text.index("### Indet form"):]
    section = section[:section.index("\n### ", 1)] if "\n### " in section[1:] else section
    for word in ("preview", "run", "rollback", "ssh root@165.232.136.170", "--production", "indet_form_bak_"):
        assert word in section, word
    assert "—" not in section, "no em-dashes in the runbook"
    print("Runbook section has preview, run and rollback, staging first.")


def test_columns_exist_in_the_schema():
    """Every taxon column the tool sets exists in the live schema snapshot (staging 2026-10-29: `rank` does not;
    the column is taxarank, and the run stopped before its commit)."""
    import glob
    snap = sorted(glob.glob(os.path.join(ROOT, "doc", "schema", "live-schema-*.tsv")))[-1]
    cols = {l.split("\t")[1] for l in open(snap, encoding="utf-8") if l.startswith("taxon\t")}
    assert "taxarank" in cols and "rank" not in cols
    src = open(os.path.join(ROOT, "tools", "data", "indet_form.py"), encoding="utf-8").read()
    assert "`rank`" not in src and "taxarank = 'species'" in src


def main():
    test_names()
    test_sql()
    test_command_flow()
    test_production_guard()
    test_production_paths()
    test_runbook()
    test_columns_exist_in_the_schema()
    print("OK")


if __name__ == "__main__":
    main()
