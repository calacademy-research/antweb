"""Orphan photos tool (tools/data/orphan_photos.py): planning, file names, SQL text and file steps, without a database.

The curator's decisions for the CAS list (tools/data/orphan_photos_cas_2026-10.tsv, 28 codes: 17 delete, 11 move)
and a fake image list stand in for production. Checked:
  - the decisions file reads as 17 deletes and 11 moves, targets in lower case as AntWeb stores codes;
  - a code is refused when its state differs from the decision (a record now, a target without record or with
    photos, another group, a chosen taxon image, two codes to one target);
  - moved files get the target's name in every size and the original keeps its upper case;
  - the SQL counts every change and stops before the commit on a mismatch; the rollback restores from the backup;
  - backup, run and rollback of the files on a temporary folder, and the whole command flow with mysql faked:
    run refuses without a backup or when the photos changed after it, and can be run again to finish files.

Run: python3 tests/unit/test_orphan_photos.py   (also in .github/workflows/tests.yaml)
"""
import contextlib
import importlib.util
import io
import json
import os
import re
import sys
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
spec = importlib.util.spec_from_file_location("orphan_photos", os.path.join(ROOT, "tools", "data", "orphan_photos.py"))
op = importlib.util.module_from_spec(spec)
spec.loader.exec_module(op)
DECISIONS = os.path.join(ROOT, "tools", "data", "orphan_photos_cas_2026-10.tsv")

SIZES = ["thumbview", "low", "med", "high"]


def fake_files(code, shots, original="tif"):
    """{name: size} as the uploader leaves a folder: four sizes per shot and the original in upper case."""
    out = {}
    for shot in shots:
        s, n = shot[0], int(shot[1:])
        for size in SIZES:
            out[f"{code}_{s}_{n}_{size}.jpg"] = 1000 + n
        out[f"{code.upper()}_{s.upper()}_{n}.{original}"] = 50000 + n
    return out


def world():
    """Decisions, image rows, specimens, folders like production on 2026-10-27 (shots from the orphan list)."""
    decisions = op.read_decisions(DECISIONS)
    shots = {"antweb1008408": "d1,d2,d3,h1,h2,l1,p1,p2", "casent0269148": "d1,h1,p1", "mhng-ento-76668": "h1,h2,l1,l2,p1",
             "mczent00021675": "d1,h1,h2,p1", "mczent00512567": "d1,h1,p1,p2", "mczent00532842": "h1,l1,p1",
             "mczent00532843": "h1,l1,p1", "mczent00532846": "h1,l1,p1", "mczent00594288": "d1,d3,h1,l1,p1,p2",
             "mczent00728282": "d1,h1,l1,p1,p2", "mhng-ento-0076668": "h1,h2,l1,l2,p1"}
    rows, folders, specimens = [], {}, {}
    next_id = 1000
    for d in decisions:
        sh = shots.get(d["code"], "d1,h1,l1,p1").split(",")
        for s in sh:
            next_id += 1
            rows.append(dict(id=next_id, code=d["code"], shot=s[0], number=int(s[1:]), groups=["1"], likes=0, uploaded=0))
        folders[d["code"]] = fake_files(d["code"], sh)
        if d["action"] == "move":
            specimens[d["target"]] = (d["target"], "16", 0)
            folders[d["target"]] = None
    rows[0]["likes"] = 2
    return decisions, rows, specimens, folders


def by_code(plans):
    return {p["code"]: p for p in plans}


def test_decisions_file():
    d = op.read_decisions(DECISIONS)
    assert len(d) == 28
    assert sum(x["action"] == "delete" for x in d) == 17 and sum(x["action"] == "move" for x in d) == 11
    moves = {x["code"]: x["target"] for x in d if x["action"] == "move"}
    assert moves["casent0005505"] == "mcz1603635w" and moves["casent0007322"] == "mcz-ent00528501"
    assert moves["mhng-ento-0076668"] == "mhngento0076668"
    assert all(t == t.lower() for t in moves.values())
    assert all(x["group"] == "1" for x in d)
    for bad in ("code\taction\ttarget\tgroup\tnote\nx1\tmove\t\t1\t\n", "code\taction\ttarget\tgroup\tnote\nx1\tkeep\t\t1\t\n",
                "code\taction\ttarget\tgroup\tnote\nx1\tdelete\t\t1\t\nx1\tdelete\t\t1\t\n",
                "code\taction\ttarget\tgroup\tnote\na/b\tdelete\t\t1\t\n", "code\ttarget\n"):
        with tempfile.NamedTemporaryFile("w", suffix=".tsv", delete=False) as f:
            f.write(bad)
        try:
            op.read_decisions(f.name)
            raise AssertionError("accepted: " + bad)
        except SystemExit:
            pass
        finally:
            os.unlink(f.name)
    print("Decisions file: 17 deletes, 11 moves, lower-case targets; bad files refused.")


def test_names():
    assert op.renamed("casent0005505_h_1_high.jpg", "casent0005505", "mcz1603635w") == "mcz1603635w_h_1_high.jpg"
    assert op.renamed("CASENT0005505_H_1.tif", "casent0005505", "mcz1603635w") == "MCZ1603635W_H_1.tif"
    assert op.renamed("CASENT0005505_H.tif", "casent0005505", "mcz1603635w") == "MCZ1603635W_H.tif"
    assert op.renamed("mczent00021675_p_1_thumbview.jpg", "mczent00021675", "mcz-ent00021675") == "mcz-ent00021675_p_1_thumbview.jpg"
    assert op.renamed("123_H_1.jpg", "123", "abc-1") == "ABC-1_H_1.jpg"
    assert op.renamed("123_h_1_low.jpg", "123", "abc-1") == "abc-1_h_1_low.jpg"
    mine, other = op.own_files("casent1", ["casent1_h_1_low.jpg", "CASENT1_H.tif", "casent12_h_1_low.jpg", "notes.txt"])
    assert mine == ["CASENT1_H.tif", "casent1_h_1_low.jpg"] and other == ["casent12_h_1_low.jpg", "notes.txt"]
    assert op.shot_of("casent1_h_2_high.jpg", "casent1") == ("h", 2)
    assert op.shot_of("CASENT1_H.tif", "casent1") == ("h", 1)
    assert op.shot_of("CASENT1_L_3.jpg", "casent1") == ("l", 3)
    print("File names: sizes lower case, originals upper case, other codes' files not taken.")


def test_plan_as_decided():
    decisions, rows, specimens, folders = world()
    plans = op.plan(decisions, rows, specimens, folders)
    assert all(p["status"] == "ok" for p in plans), [(p["code"], p["reasons"]) for p in plans if p["status"] != "ok"]
    p = by_code(plans)["casent0005505"]
    assert p["target_code"] == "mcz1603635w" and len(p["files"]) == 20
    assert {f["new_name"] for f in p["files"]} >= {"mcz1603635w_h_1_high.jpg", "MCZ1603635W_H_1.tif"}
    assert not any("period" in w for w in p["warnings"])
    c = op.counts(plans)
    assert c["delete_codes"] == 17 and c["move_codes"] == 11
    assert len(c["delete_ids"]) == 72 and len(c["move_ids"]) == 45, (len(c["delete_ids"]), len(c["move_ids"]))
    out = io.StringIO()
    op.print_plan(plans, "/mnt/antweb/images", out)
    assert "Accepted: 17 codes to delete (72 rows), 11 to move (45 rows)" in out.getvalue()
    print("Plan as decided: 17 codes delete 72 rows, 11 codes move 45 rows; every file renamed.")


def test_refusals():
    decisions, rows, specimens, folders = world()
    specimens["casent0102908"] = ("casent0102908", "1", 0)               # a delete code has a record again
    specimens.pop("mcz-ent00528501")                                      # a target lost its record
    rows.append(dict(id=9999, code="mcz-ent00021675", shot="h", number=1, groups=["16"], likes=0, uploaded=0))  # target has photos
    folders["mcz-ent00512567"] = {"mcz-ent00512567_h_1_low.jpg": 5}      # target folder has files
    for r in rows:
        if r["code"] == "anic32-066597":
            r["groups"] = ["2"]                                           # another group's photo
    picks = {"anic32-066603": ["myrmicinaepheidole x"]}
    plans = by_code(op.plan(decisions, rows, specimens, folders, picks))
    expect = {"casent0102908": "specimen record now", "casent0007322": "has no specimen record",
              "mczent00021675": "has 1 photos now", "mczent00512567": "holds 1 files", "anic32-066597": "belongs to group 2",
              "anic32-066603": "chosen image"}
    for code, words in expect.items():
        assert plans[code]["status"] == "refused" and any(words in r for r in plans[code]["reasons"]), (code, plans[code]["reasons"])
    assert plans["anic32-066663"]["status"] == "ok"
    # two codes to one target; a target stored with capitals; a code with no rows and no files
    d2 = [dict(code="a1", action="move", target="t1", group="1", note=""), dict(code="a2", action="move", target="t1", group="1", note=""),
          dict(code="a3", action="move", target="t2", group="1", note=""), dict(code="a4", action="delete", target="", group="1", note="")]
    r2 = [dict(id=i, code=c, shot="h", number=1, groups=["1"], likes=0, uploaded=0) for i, c in ((1, "a1"), (2, "a2"), (3, "a3"))]
    p2 = by_code(op.plan(d2, r2, {"t1": ("t1", "1", 0), "t2": ("T2", "1", 0)}, {}))
    assert p2["a1"]["status"] == p2["a2"]["status"] == "refused" and "2 codes move to t1" in p2["a1"]["reasons"][0]
    assert p2["a3"]["status"] == "refused" and "lower case" in p2["a3"]["reasons"][0]
    assert p2["a4"]["status"] == "nothing"
    # staging: rows but no files is accepted with a note
    assert p2["a1"]["warnings"] and "no files found" in p2["a1"]["warnings"][0]
    print("Refusals: record now, target without record or with photos, other group, chosen image, shared target.")


def test_sql():
    decisions, rows, specimens, folders = world()
    plans = op.plan(decisions, rows, specimens, folders)
    tables = {"image", "group_image", "image_like", "image_uploaded", "specimen"}
    run = op.run_sql(plans, tables)
    assert run.index("start transaction;") < run.index("delete from image_like") < run.index("delete from group_image") \
        < run.index("delete from image where") < run.index("commit;")
    assert "select @n as image_deleted, 72 as expected" in run
    assert "select @n as group_image_deleted, 72 as expected" in run
    assert "select @n as image_like_deleted, 2 as expected" in run
    assert "update image set image_of_id = 'mcz1603635w' where id in (" in run
    assert "update specimen set image_count = (select count(*) from image where image_of_id = 'mcz1603635w') where code = 'mcz1603635w';" in run
    assert run.count("(select 'mismatch' union all select 'stop')") == 3 + 2 * 11
    assert run.rstrip().endswith("select 'committed' as result;")
    bak = op.backup_sql(plans, "orphan_photos_bak_20261027", tables)
    assert "create table orphan_photos_bak_20261027_image as select * from image where id in (" in bak
    assert "select @n as image_rows, 117 as expected" in bak and "select @n as specimen_rows, 11 as expected" in bak
    rb = op.rollback_sql(plans, "orphan_photos_bak_20261027", tables)
    assert "select @n as ids_in_use, 0 as expected" in rb and "select @n as image_restored, 72 as expected" in rb
    assert "select @n as moved_back, 45 as expected" in rb
    assert "i.modified = b.modified" in op.rollback_sql(plans, "p", tables | {"image.modified"}) and "modified" not in rb
    assert op.in_list([]) == "(NULL)" and op.sql_string("a'b\\") == "'a''b\\\\'"
    # no accepted delete: no delete statements at all
    only_moves = [p for p in plans if p["action"] == "move"]
    assert "delete from" not in op.run_sql(only_moves, tables)
    print("SQL: every change counted before the commit; backup and rollback counts.")


def make_folders(images, folders):
    for code, files in folders.items():
        if files is None:
            continue
        os.makedirs(os.path.join(images, code), exist_ok=True)
        for name, size in files.items():
            with open(os.path.join(images, code, name), "wb") as f:
                f.write(b"x" * size)


def test_file_steps():
    decisions, rows, specimens, folders = world()
    with tempfile.TemporaryDirectory() as tmp:
        images, dest = os.path.join(tmp, "images"), os.path.join(tmp, "bak")
        make_folders(images, folders)
        with open(os.path.join(images, "casent0102908", "keep.txt"), "w") as f:
            f.write("other")
        plans = op.plan(decisions, rows, specimens, {c: op.list_folder(images, c) for c in folders})
        log = lambda m: None
        assert op.backup_files(plans, images, dest, log) == []
        assert os.path.getsize(os.path.join(dest, "images", "casent0005505", "CASENT0005505_H_1.tif")) == 50001
        assert op.apply_files(plans, images, log) == []
        assert not os.path.exists(os.path.join(images, "anic32-066597"))
        assert os.listdir(os.path.join(images, "casent0102908")) == ["keep.txt"]       # other file left alone
        assert not os.path.exists(os.path.join(images, "casent0005505"))
        moved = sorted(os.listdir(os.path.join(images, "mcz1603635w")))
        assert "MCZ1603635W_H_1.tif" in moved and "mcz1603635w_l_1_thumbview.jpg" in moved and len(moved) == 20
        assert op.apply_files(plans, images, log) == []                               # again: nothing left to do
        assert op.restore_files(plans, images, dest, log) == []
        assert not os.path.exists(os.path.join(images, "mcz1603635w"))
        for code, files in folders.items():
            if files is not None:
                assert op.list_folder(images, code) == (dict(files, **{"keep.txt": 5}) if code == "casent0102908" else files), code
    print("Files: backup, delete and rename, run again, restore: every folder as before.")


class FakeDb:
    """Answers the tool's queries from the fake world; records the scripts it was given."""

    def __init__(self, rows, specimens):
        self.rows, self.specimens, self.scripts = rows, specimens, []

    def __call__(self, args, sql, batch=True):
        if not batch:
            self.scripts.append(sql)
            return "committed\n" if "commit;" in sql else "backup tables written\n"
        if "information_schema.columns" in sql:
            return "1\n"
        if "information_schema" in sql:
            return "image\ngroup_image\nimage_like\nimage_uploaded\nspecimen\n"
        if sql.startswith("select i.id"):
            return "".join(f"{r['id']}\t{r['code']}\t{r['shot']}\t{r['number']}\t{','.join(r['groups'])}\n" for r in self.rows)
        if sql.startswith("select l.image_id"):
            return "".join(f"{r['id']}\t{r['likes']}\n" for r in self.rows if r["likes"])
        if sql.startswith("select code, access_group"):
            return "".join(f"{v[0]}\t{v[1]}\t{v[2]}\n" for v in self.specimens.values())
        return ""


def test_command_flow():
    decisions, rows, specimens, folders = world()
    with tempfile.TemporaryDirectory() as tmp:
        project, images, root = os.path.join(tmp, "antweb"), os.path.join(tmp, "mnt", "images"), os.path.join(tmp, "archive")
        os.makedirs(os.path.join(project, "etc"))
        with open(os.path.join(project, "etc", "db.properties"), "w") as f:
            f.write("user=antweb\npassword=secret\n")
        make_folders(images, folders)
        db = FakeDb(rows, specimens)
        op.mysql = db
        common = ["--project", project, "--decisions", DECISIONS, "--images", images, "--backup-root", root,
                  "--date", "20261027", "--yes"]
        quiet = lambda: contextlib.redirect_stdout(io.StringIO())
        with quiet():
            op.main(["preview"] + common)
        assert db.scripts == []
        try:
            with quiet():
                op.main(["run"] + common)
            raise AssertionError("run without a backup")
        except SystemExit as e:
            assert "No backup" in str(e)
        with quiet():
            op.main(["backup"] + common)
        dest = os.path.join(root, "orphan_photos_20261027")
        m = json.load(open(os.path.join(dest, "manifest.json")))
        assert m["counts"]["files"] == sum(len(v) for v in folders.values() if v) and len(m["plans"]) == 28
        assert os.path.exists(os.path.join(dest, "images", "mczent00594288", "MCZENT00594288_D_3.tif"))
        # a new photo after the backup: run refuses
        rows.append(dict(id=5000, code="anic32-066597", shot="v", number=1, groups=["1"], likes=0, uploaded=0))
        try:
            with quiet():
                op.main(["run"] + common)
            raise AssertionError("run after a change")
        except SystemExit as e:
            assert "changed since the backup" in str(e)
        rows.pop()
        # a file the run cannot rename: run stops after the commit; a second run finishes the files only
        real_rename = os.rename
        def failing_rename(src, dst):
            if dst.endswith("MCZ-ENT00728282_P_2.tif"):
                raise OSError("simulated failure")
            return real_rename(src, dst)
        op.os.rename = failing_rename
        try:
            with quiet():
                op.main(["run"] + common)
            raise AssertionError("run with a failing rename")
        except SystemExit as e:
            assert "files failed" in str(e)
        finally:
            op.os.rename = real_rename
        assert os.path.exists(os.path.join(images, "mczent00728282", "MCZENT00728282_P_2.tif"))
        n = len(db.scripts)
        with quiet():
            op.main(["run"] + common)
        assert len(db.scripts) == n, "the second run must not run the SQL again"
        assert not os.path.exists(os.path.join(images, "mczent00728282"))
        assert os.path.exists(os.path.join(images, "mcz-ent00728282", "MCZ-ENT00728282_P_2.tif"))
        with quiet():
            op.main(["rollback"] + [a for a in common if a not in ("--date", "20261027")])   # finds the newest backup
        assert "insert into image select * from orphan_photos_bak_20261027_image" in db.scripts[-1]
        for code, files in folders.items():
            assert op.list_folder(images, code) == files, code
        try:
            with quiet():
                op.main(["run"] + common)
            raise AssertionError("run after a rollback")
        except SystemExit as e:
            assert "rolled back" in str(e)
    print("Commands: preview reads only; run needs the backup and the same photos; a second run finishes files; rollback.")


def test_runbook():
    text = open(os.path.join(ROOT, "docs", "runbook.md"), encoding="utf-8").read()
    assert "### Orphan photos" in text
    section = text[text.index("### Orphan photos"):]
    section = section[:section.index("\n## ", 1)]
    for word in ("preview", "backup", "run", "rollback", "ssh root@165.232.136.170", "ssh root@165.232.137.86",
                 "orphan_photos_cas_2026-10.tsv"):
        assert word in section, word
    assert "\u2014" not in section, "no em-dashes in the runbook"
    print("Runbook section has the four steps for staging and production.")


def main():
    test_decisions_file()
    test_names()
    test_plan_as_decided()
    test_refusals()
    test_sql()
    test_file_steps()
    test_command_flow()
    test_runbook()
    test_period_target_warns()
    test_ecofog_decisions_file()
    test_merge()


def test_period_target_warns():
    decisions, rows, specimens, folders = world()
    d = [dict(x) for x in decisions]
    for x in d:
        if x["code"] == "casent0005505":
            x["target"] = "mcz160.3635w"
    specimens["mcz160.3635w"] = ("mcz160.3635w", "16", 0)
    folders["mcz160.3635w"] = None
    p = by_code(op.plan(d, rows, specimens, folders))["casent0005505"]
    assert any("period" in w for w in p["warnings"]), p["warnings"]
    print("A target code with a period gets a warning.")


def test_ecofog_decisions_file():
    """EcoFoG's answers (Jerome Orivel, 2026-10-28): 147 duplicates to delete, 25 of them uploaded under the CAS
    account in 2018-10 (group 1), the others EcoFoG (group 67). The one move (ecofog-hc-0002 to ecofog-hc-0002-01)
    is left out: the target has a profile photo already. The 26 codes whose records Jerome will upload are not in it."""
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "tools", "data",
                        "orphan_photos_ecofog_2026-10.tsv")
    d = op.read_decisions(path)
    assert len(d) == 148 and sum(x["action"] == "delete" for x in d) == 147, len(d)
    assert sum(x["group"] == "1" for x in d) == 25 and sum(x["group"] == "67" for x in d) == 123
    merge = [x for x in d if x["action"] == "merge"]
    assert [(x["code"], x["target"]) for x in merge] == [("ecofog-hc-0002", "ecofog-hc-0002-01")], merge
    print("EcoFoG decisions: 147 deletes (25 in the CAS group, 122 in EcoFoG's), 1 merge.")


def test_merge():
    """merge (ecofog-hc-0002 into ecofog-hc-0002-01, Jerome Orivel 2026-10-28): the target has a profile photo already,
    the same photo; the code's profile is deleted, its head and dorsal are moved, nothing of the target changes."""
    code, tgt = "ecofog-hc-0002", "ecofog-hc-0002-01"
    d = [dict(code=code, action="merge", target=tgt, group="67", note="same specimen")]
    rows = [dict(id=10 + k, code=code, shot=s, number=1, groups=["67"], likes=0, uploaded=0) for k, s in enumerate("hpd")]
    rows.append(dict(id=99, code=tgt, shot="p", number=1, groups=["67"], likes=0, uploaded=0))
    specimens = {tgt: (tgt, "67", 1)}
    mine = {f"{code}_{s}_1_{z}.jpg": 100 for s in "hpd" for z in op.SIZES}
    mine.update({f"{code.upper()}_{s.upper()}_1.tif": 5000 for s in "hpd"})
    theirs = {f"{tgt}_p_1_{z}.jpg": 200 for z in op.SIZES}
    theirs[f"{tgt.upper()}_P_1.tif"] = 6000
    with tempfile.TemporaryDirectory() as tmp:
        images, dest = os.path.join(tmp, "images"), os.path.join(tmp, "bak")
        make_folders(images, {code: mine, tgt: theirs})
        plans = op.plan(d, rows, specimens, {c: op.list_folder(images, c) for c in (code, tgt)})
        assert [(p["action"], p["status"]) for p in plans] == [("delete", "ok"), ("move", "ok")], \
            [(p["action"], p["status"], p["reasons"]) for p in plans]
        dele, move = plans
        assert [r["id"] for r in dele["rows"]] == [11] and sorted(r["id"] for r in move["rows"]) == [10, 12]
        assert len(dele["files"]) == 5 and len(move["files"]) == 10
        sql = op.run_sql(plans, {"image_like"})
        assert "delete from image where id in (11)" in sql and "where id in (10, 12)" in sql, sql
        assert "1 as expected, if(@n = 1, 'ok'" in sql.split("as target_photos_", 1)[1][:60], sql   # its 1 profile
        log = lambda m: None
        assert op.backup_files(plans, images, dest, log) == []
        assert op.apply_files(plans, images, log) == []
        after = op.list_folder(images, tgt)
        for z in op.SIZES:
            assert after[f"{tgt}_p_1_{z}.jpg"] == 200          # the target's own profile untouched
            assert f"{tgt}_h_1_{z}.jpg" in after and f"{tgt}_d_1_{z}.jpg" in after
        assert len(after) == 15 and not os.path.exists(os.path.join(images, code))
        assert op.restore_files(plans, images, dest, log) == []
        assert op.list_folder(images, code) == mine and op.list_folder(images, tgt) == theirs
        # a target file in the way of a moved one: refused
        clash = dict(theirs, **{f"{tgt}_h_1_low.jpg": 1})
        refused = op.plan(d, [r for r in rows], specimens, {code: mine, tgt: clash})
        assert refused[0]["status"] == "refused", refused
    print("Merge: shots the target has are deleted, the others moved; target untouched; restore puts all back.")


if __name__ == "__main__":
    main()

