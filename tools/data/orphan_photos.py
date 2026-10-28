#!/usr/bin/env python3
"""Orphan photos: delete them, or move them to the right specimen code, as a curator decided.

An orphan photo is a row of the image table (and its files) whose code (image.image_of_id) is the code of no
specimen record, so it shows on no page (orphanedImages.do lists them). Curators answer, code by code, whether the
photos are a duplicate (delete) or belong to another code that has a record (move). Their answers go into a decisions
file, one per group list, for example tools/data/orphan_photos_cas_2026-10.tsv (tab-separated, first line the
header): code, action (delete or move), target (the code to move to; empty for delete), group (the upload group the
photos belong to, from group_image), note.

What a code has (read by preview, checked again by backup and run):
  rows    image rows with image_of_id = the code (MySQL compares ignoring case), their group_image rows and
          image_like rows. image_uploaded rows (the upload history: which file each upload received) are copied
          to the backup but never changed.
  files   every file in <images>/<code>/ whose name starts with "<code>_" in any case: the four sizes the uploader
          makes (<code>_<shot>_<n>_thumbview.jpg, _low, _med, _high) and the original as uploaded
          (<CODE>_<SHOT>_<n>.tif or .jpg or .png, or <CODE>_<SHOT>.tif for number 1). Other files in the folder are
          listed and left alone. Old originals the uploader replaced are in <images>/backup/ and are left alone.
          <images> is /mnt/antweb/images on the host (the Spaces bucket on production, a plain folder on staging,
          which may not hold these photos at all: then only the rows change there).

Steps (docs/runbook.md, "Orphan photos"):
  preview   read-only. Prints for each code its rows and files and what will happen, or why it is refused:
            delete: refused if the code has a specimen record now, if a row belongs to another group, or if a
                    taxon's chosen image (taxon_prop) names the code;
            move:   refused if the code has a specimen record now, if the target has no specimen record, if the target
                    has photos (rows or files) now, if two codes move to the same target, or if a row belongs to
                    another group.
            A code with no rows and no files is "nothing to do". Refused codes are left as they are by every step.
  backup    copies every file of the accepted codes to <backup-root>/orphan_photos_<date>/images/<code>/ and the rows
            to tables orphan_photos_bak_<date>_image, _group_image, _image_like, _image_uploaded, _specimen (and as
            TSV files under db/ in the same folder); checks file sizes and row counts; writes manifest.json last.
  run       needs the backup of today (or --date) made at most --max-age-hours ago, and the same plan as then. Shows
            the SQL, asks for "yes", runs it in one transaction (a count that differs stops it before the commit),
            then deletes or renames the files (log: run.log in the backup folder). Moved files get the target's
            name in every size: casent0005505_h_1_high.jpg -> mcz1603635w_h_1_high.jpg, CASENT0005505_H_1.tif ->
            MCZ1603635W_H_1.tif. The target's specimen.image_count is set from its rows; taxon, place and home page
            image numbers follow at the next nightly run (set 4). Running run again after a failure in the file
            step only finishes the files.
  rollback  puts rows and files back from the backup (rows in one transaction, then files).

Database: mysql in the container antweb_mysql_1 with the login of <project>/etc/db.properties (the password goes
to docker through the environment, never on a command line or in a prompt).

Usage on a server (checkout in /root/antweb on production, /root/antweb-next on staging):
  python3 tools/data/orphan_photos.py preview  --project /root/antweb-next --decisions tools/data/orphan_photos_cas_2026-10.tsv
  python3 tools/data/orphan_photos.py backup   --project /root/antweb-next --decisions tools/data/orphan_photos_cas_2026-10.tsv
  python3 tools/data/orphan_photos.py run      --project /root/antweb-next --decisions tools/data/orphan_photos_cas_2026-10.tsv
  python3 tools/data/orphan_photos.py rollback --project /root/antweb-next --decisions tools/data/orphan_photos_cas_2026-10.tsv

Tests: tests/unit/test_orphan_photos.py.
"""
import argparse
import datetime
import json
import os
import re
import shutil
import subprocess
import sys

DECISION_COLUMNS = ["code", "action", "target", "group", "note"]
ACTIONS = ("delete", "move")
SIZES = ("thumbview", "low", "med", "high")
# Codes the tool works with: what the specimen upload accepts (UploadUtil.SPECIMEN_CODE) minus "/" (a path).
SAFE_CODE = re.compile(r"[a-z0-9._:()#+-]+")
TOMCAT_UID, TOMCAT_GID = 2001, 2000


# ------------------------------------------------------------------ decisions file

def read_decisions(path):
    """The decisions as dicts. Stops on a bad line: unknown action, move without target, a code twice."""
    with open(path, encoding="utf-8") as f:
        lines = [l.rstrip("\r\n") for l in f]
    head = lines[0].split("\t")
    if head != DECISION_COLUMNS:
        sys.exit(f"{path}: the first line must be {' '.join(DECISION_COLUMNS)} (tab-separated), not {lines[0]!r}")
    out, seen = [], set()
    for n, line in enumerate(lines[1:], 2):
        if not line.strip() or line.startswith("#"):
            continue
        cells = (line.split("\t") + [""] * 5)[:5]
        d = dict(zip(DECISION_COLUMNS, (c.strip() for c in cells)))
        d["code"] = d["code"].lower()
        d["target"] = d["target"].lower()
        d["action"] = d["action"].lower()
        problem = None
        if d["action"] not in ACTIONS:
            problem = f"action must be delete or move, not {d['action']!r}"
        elif d["action"] == "move" and not d["target"]:
            problem = "move needs a target code"
        elif d["action"] == "delete" and d["target"]:
            problem = "delete takes no target"
        elif not SAFE_CODE.fullmatch(d["code"]) or d["code"] in (".", ".."):
            problem = f"code {d['code']!r} has a character the tool does not handle"
        elif d["target"] and (not SAFE_CODE.fullmatch(d["target"]) or d["target"] in (".", "..")):
            problem = f"target {d['target']!r} has a character the tool does not handle"
        elif not d["group"].isdigit():
            problem = f"group must be a group id (a number), not {d['group']!r}"
        elif d["code"] in seen:
            problem = f"code {d['code']} is listed twice"
        if problem:
            sys.exit(f"{path} line {n}: {problem}")
        seen.add(d["code"])
        out.append(d)
    return out


# ------------------------------------------------------------------ files

def own_files(code, names):
    """(files of the code, other files) of a folder listing: a file of the code starts with '<code>_' in any case."""
    prefix = code.lower() + "_"
    mine = sorted(n for n in names if n.lower().startswith(prefix))
    other = sorted(n for n in names if not n.lower().startswith(prefix))
    return mine, other


def renamed(name, code, target):
    """The file name under the target code, in the same case as the original: the sizes are lower case
    (casent0005505_h_1_high.jpg), the uploaded original upper case (CASENT0005505_H_1.tif)."""
    prefix, rest = name[:len(code)], name[len(code):]
    letters = [c for c in prefix if c.isalpha()]
    if not letters:  # a code without letters: the shot letter decides (_H_1.tif or _h_1_high.jpg)
        stem = rest.rsplit(".", 1)[0]
        letters = [c for c in stem if c.isalpha()]
    upper = bool(letters) and all(c.isupper() for c in letters)
    return (target.upper() if upper else target) + rest


def shot_of(name, code):
    """(shot, number) of a file name: casent1_h_2_high.jpg -> ('h', 2), CASENT1_H.tif -> ('h', 1); None if unknown."""
    m = re.match(r"([a-z]+)(?:_(\d+))?(?:_|\.)", name[len(code) + 1:].lower())
    return (m.group(1), int(m.group(2) or 1)) if m else None


def list_folder(images, folder):
    """{name: size} of the files in <images>/<folder>/, or None if the folder does not exist."""
    path = os.path.join(images, folder)
    if not os.path.isdir(path):
        return None
    out = {}
    for name in os.listdir(path):
        p = os.path.join(path, name)
        if os.path.isfile(p):
            out[name] = os.path.getsize(p)
    return out


# ------------------------------------------------------------------ planning (no database, no files)

def plan(decisions, image_rows, specimens, folders, picks=None):
    """What to do with each code.

    decisions   from read_decisions
    image_rows  dicts: id, code (image_of_id as stored), shot, number, groups (list of group ids, one per
                group_image row), likes (image_like rows), uploaded (image_uploaded rows of the code: not per row)
    specimens   {lower-case code: (code as stored, access group, image_count)}
    folders     {folder name: {file name: size} or None}: the folders of the codes and the targets
    picks       {lower-case code: [taxon names whose chosen image (taxon_prop) names the code]}
    Returns a list of dicts (one per decision): code, action, target, group, note, status (ok, refused, nothing),
    reasons, warnings, folder, target_code, target_folder, rows (sorted by id), files [{name, size, new_name}],
    other_files.
    """
    picks = picks or {}
    rows_by = {}
    for r in image_rows:
        rows_by.setdefault(r["code"].lower(), []).append(r)
    targets = {}
    for d in decisions:
        if d["action"] == "move":
            targets.setdefault(d["target"], []).append(d["code"])
    codes = {d["code"] for d in decisions}
    out = []
    for d in decisions:
        code, action, target, group = d["code"], d["action"], d["target"], d["group"]
        p = dict(code=code, action=action, target=target, group=group, note=d.get("note", ""), status="ok",
                 reasons=[], warnings=[], folder=code, target_code="", target_folder="", rows=[], files=[],
                 other_files=[])
        rows = sorted(rows_by.get(code, []), key=lambda r: r["id"])
        p["rows"] = rows
        stored = sorted({r["code"] for r in rows})
        if len(stored) > 1:
            p["reasons"].append("the rows write the code in more than one way: " + ", ".join(stored))
        elif stored:
            p["folder"] = stored[0]
            if stored[0] != code:
                p["warnings"].append(f"the rows store the code as {stored[0]}")
        spec = specimens.get(code)
        if spec:
            p["reasons"].append(f"the code has a specimen record now ({spec[0]}, group {spec[1]})")
        for r in rows:
            other = sorted({g for g in r["groups"] if str(g) != str(group)})
            if other:
                p["reasons"].append(f"image {r['id']} ({r['shot']}{r['number']}) belongs to group "
                                    f"{', '.join(map(str, other))}, the list is for group {group}")
            elif not r["groups"]:
                p["warnings"].append(f"image {r['id']} ({r['shot']}{r['number']}) has no group (no group_image row)")
        if picks.get(code):
            p["reasons"].append("chosen image of " + ", ".join(picks[code]) + " (taxon_prop names the code)")
        if code in targets:
            p["reasons"].append("the code is also the target of " + ", ".join(targets[code]))
        listing = folders.get(p["folder"])
        if listing is None:
            mine, other = [], []
        else:
            mine, other = own_files(p["folder"], listing)
        p["other_files"] = other
        if other:
            p["warnings"].append(f"{len(other)} other files in the folder are left alone: " + ", ".join(other[:5])
                                 + (" ..." if len(other) > 5 else ""))
        p["files"] = [dict(name=n, size=listing[n], new_name="") for n in mine]
        if rows and not mine:
            p["warnings"].append("no files found in images/" + p["folder"] + "/ (only the rows change)")
        shots = {(r["shot"].lower(), int(r["number"])) for r in rows}
        loose = [f["name"] for f in p["files"] if shot_of(f["name"], p["folder"]) not in shots]
        if loose and rows:
            p["warnings"].append(f"{len(loose)} files have no image row; they go with the others: " + ", ".join(loose[:5]))

        if action == "move":
            tspec = specimens.get(target)
            if target == code:
                p["reasons"].append("the target is the code itself")
            elif not tspec:
                p["reasons"].append(f"the target {target} has no specimen record")
            else:
                tcode = tspec[0]
                p["target_code"] = tcode
                p["target_folder"] = tcode
                if tcode != tcode.lower() or not SAFE_CODE.fullmatch(tcode):
                    p["reasons"].append(f"the target is stored as {tcode!r}: AntWeb stores codes in lower case and "
                                        "the tool handles only letters, digits and . _ : ( ) # + -")
                trows = rows_by.get(target, [])
                if trows:
                    p["reasons"].append(f"the target {tcode} has {len(trows)} photos now")
                tlisting = folders.get(tcode) or {}
                tmine = own_files(tcode, tlisting)[0]
                if tmine:
                    p["reasons"].append(f"images/{tcode}/ holds {len(tmine)} files of the target already")
                if "." in tcode:
                    p["warnings"].append("the target code has a period: pages and image links handle it, but the "
                                         "Image Uploader cannot take it in a file name (it reads the code up to the "
                                         "first period), so later photos of this specimen need the curator to ask an "
                                         "admin")
                news = [renamed(f["name"], p["folder"], tcode) for f in p["files"]]
                if len(set(news)) != len(news):
                    p["reasons"].append("two files would get the same name")
                for f, n in zip(p["files"], news):
                    f["new_name"] = n
            if len(targets.get(target, [])) > 1:
                p["reasons"].append(f"{len(targets[target])} codes move to {target}: " + ", ".join(targets[target]))
        if p["reasons"]:
            p["status"] = "refused"
        elif not rows and not p["files"]:
            p["status"] = "nothing"
        out.append(p)
    return out


def accepted(plans):
    return [p for p in plans if p["status"] == "ok"]


def signature(plans):
    """What backup saved and run must find again: codes, actions, targets, row ids and groups, likes, files."""
    return [dict(code=p["code"], action=p["action"], target_code=p["target_code"],
                 rows=[[r["id"], r["code"], r["shot"], r["number"], sorted(map(str, r["groups"])), r["likes"]]
                       for r in p["rows"]],
                 files=[[f["name"], f["size"], f["new_name"]] for f in p["files"]])
            for p in accepted(plans)]


def counts(plans):
    ok = accepted(plans)
    dele = [p for p in ok if p["action"] == "delete"]
    move = [p for p in ok if p["action"] == "move"]
    ids = lambda ps: [r["id"] for p in ps for r in p["rows"]]
    return dict(
        codes=len(ok), delete_codes=len(dele), move_codes=len(move),
        delete_ids=ids(dele), move_ids=ids(move), all_ids=ids(ok),
        delete_group_rows=sum(len(r["groups"]) for p in dele for r in p["rows"]),
        group_rows=sum(len(r["groups"]) for p in ok for r in p["rows"]),
        delete_likes=sum(r["likes"] for p in dele for r in p["rows"]),
        likes=sum(r["likes"] for p in ok for r in p["rows"]),
        files=sum(len(p["files"]) for p in ok),
        bytes=sum(f["size"] for p in ok for f in p["files"]),
        targets=[p["target_code"] for p in move],
    )


def print_plan(plans, images, out=None, verbose=False):
    out = out or sys.stdout
    by = {}
    for p in plans:
        by.setdefault((p["status"], p["action"]), []).append(p)
    out.write(f"{len(plans)} codes in the decisions file\n")
    for (status, action), ps in sorted(by.items()):
        out.write(f"  {status} {action}: {len(ps)} codes, {sum(len(p['rows']) for p in ps)} image rows, "
                  f"{sum(len(p['files']) for p in ps)} files\n")
    out.write("\n")
    for p in plans:
        what = "DELETE" if p["action"] == "delete" else f"MOVE to {p['target_code'] or p['target']}"
        head = {"ok": what, "refused": "REFUSED (" + what + ")", "nothing": "NOTHING TO DO (" + what + ")"}
        out.write(f"{p['code']}: {head[p['status']]}\n")
        for reason in p["reasons"]:
            out.write(f"    refused: {reason}\n")
        for w in p["warnings"]:
            out.write(f"    note: {w}\n")
        if p["rows"]:
            groups = sorted({str(g) for r in p["rows"] for g in r["groups"]}) or ["none"]
            out.write(f"    {len(p['rows'])} rows (group {', '.join(groups)}): "
                      + " ".join(f"{r['shot']}{r['number']}" for r in p["rows"])
                      + f"  image.id {', '.join(str(r['id']) for r in p['rows'])}"
                      + f"; likes {sum(r['likes'] for r in p['rows'])}"
                      + f"; upload history rows {p['rows'][0].get('uploaded', 0)} (kept)\n")
        if p["files"]:
            size = sum(f["size"] for f in p["files"]) / 1e6
            line = f"    {len(p['files'])} files in {os.path.join(images, p['folder'])}/ ({size:.1f} MB)"
            if p["action"] == "move" and p["target_folder"]:
                first = next((f for f in p["files"] if f["new_name"][:1].isupper()), p["files"][0])
                line += f" -> {os.path.join(images, p['target_folder'])}/, e.g. {first['name']} -> {first['new_name']}"
            out.write(line + "\n")
            if verbose:
                for f in p["files"]:
                    out.write(f"      {f['name']} ({f['size']} bytes)" + (f" -> {f['new_name']}" if f["new_name"] else "") + "\n")
    c = counts(plans)
    out.write(f"\nAccepted: {c['delete_codes']} codes to delete ({len(c['delete_ids'])} rows), {c['move_codes']} to move "
              f"({len(c['move_ids'])} rows); {c['files']} files, {c['bytes'] / 1e6:.1f} MB.\n")


# ------------------------------------------------------------------ SQL text

def sql_string(value):
    """A single-quoted MySQL literal (backslashes and apostrophes escaped), as DBUtil.sqlLiteral."""
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def in_list(values, numeric=False):
    """'(1, 2)' or "('a', 'b')"; '(NULL)' when empty (valid SQL that matches nothing)."""
    if not values:
        return "(NULL)"
    return "(" + ", ".join(str(int(v)) if numeric else sql_string(v) for v in values) + ")"


def check(name, actual_sql, expected):
    """A check that stops mysql (an error ends the batch before the commit, so the transaction is rolled back)."""
    return (f"set @n = {actual_sql};\n"
            f"select @n as {name}, {int(expected)} as expected, if(@n = {int(expected)}, 'ok', "
            f"(select 'mismatch' union all select 'stop')) as check_{name};")


def bak_tables(prefix):
    return {t: f"{prefix}_{t}" for t in ("image", "group_image", "image_like", "image_uploaded", "specimen")}


def lookup_sql(decisions, tables):
    """The read-only queries of preview: image rows of the codes and targets, specimens, likes, picks, uploads."""
    codes = sorted({d["code"] for d in decisions} | {d["target"] for d in decisions if d["target"]})
    q = {}
    q["rows"] = ("select i.id, i.image_of_id, i.shot_type, i.shot_number,"
                 " coalesce(group_concat(gi.group_id order by gi.group_id separator ','), '')"
                 " from image i left join group_image gi on gi.image_id = i.id"
                 f" where i.image_of_id in {in_list(codes)} group by i.id, i.image_of_id, i.shot_type, i.shot_number"
                 " order by i.id")
    q["specimens"] = f"select code, access_group, coalesce(image_count, 0) from specimen where code in {in_list(codes)}"
    if "image_like" in tables:
        q["likes"] = ("select l.image_id, count(*) from image_like l join image i on i.id = l.image_id"
                      f" where i.image_of_id in {in_list(codes)} group by l.image_id")
    if "image_uploaded" in tables:
        q["uploaded"] = f"select lower(code), count(*) from image_uploaded where code in {in_list(codes)} group by lower(code)"
    if "taxon_prop" in tables:
        q["picks"] = f"select lower(value), taxon_name from taxon_prop where value in {in_list(codes)}"
    return q


def backup_sql(plans, prefix, tables):
    c = counts(plans)
    t = bak_tables(prefix)
    codes = sorted({p["code"] for p in accepted(plans)} | {p["folder"] for p in accepted(plans)})
    lines = ["-- Orphan photos: backup of the rows (tools/data/orphan_photos.py backup).",
             "-- Stops at the first statement if the backup tables exist already.",
             "set names utf8mb4;",
             f"create table {t['image']} as select * from image where id in {in_list(c['all_ids'], True)};",
             f"create table {t['group_image']} as select * from group_image where image_id in {in_list(c['all_ids'], True)};"]
    if "image_like" in tables:
        lines.append(f"create table {t['image_like']} as select * from image_like where image_id in {in_list(c['all_ids'], True)};")
    if "image_uploaded" in tables:
        lines.append(f"create table {t['image_uploaded']} as select * from image_uploaded where code in {in_list(codes)};")
    lines.append(f"create table {t['specimen']} as select code, image_count from specimen where code in {in_list(c['targets'])};")
    lines.append(check("image_rows", f"(select count(*) from {t['image']})", len(c["all_ids"])))
    lines.append(check("group_image_rows", f"(select count(*) from {t['group_image']})", c["group_rows"]))
    if "image_like" in tables:
        lines.append(check("image_like_rows", f"(select count(*) from {t['image_like']})", c["likes"]))
    lines.append(check("specimen_rows", f"(select count(*) from {t['specimen']})", len(c["targets"])))
    lines.append("select 'backup tables written' as result;")
    return "\n".join(lines) + "\n"


def run_sql(plans, tables):
    """Deletes and moves in one transaction, each change counted; the commit only if every count matches."""
    c = counts(plans)
    ok = accepted(plans)
    lines = ["-- Orphan photos: delete and move rows (tools/data/orphan_photos.py run).",
             f"-- {c['delete_codes']} codes deleted ({len(c['delete_ids'])} rows), {c['move_codes']} moved ({len(c['move_ids'])} rows).",
             "set names utf8mb4;", "start transaction;"]
    dele = [p for p in ok if p["action"] == "delete"]
    if dele:
        ids = in_list(c["delete_ids"], True)
        codes = in_list(sorted({p["folder"] for p in dele}))
        if "image_like" in tables:
            lines.append(f"delete from image_like where image_id in {ids};")
            lines.append(check("image_like_deleted", "row_count()", c["delete_likes"]))
        lines.append(f"delete from group_image where image_id in {ids};")
        lines.append(check("group_image_deleted", "row_count()", c["delete_group_rows"]))
        lines.append(f"delete from image where id in {ids} and image_of_id in {codes};")
        lines.append(check("image_deleted", "row_count()", len(c["delete_ids"])))
    for p in ok:
        if p["action"] != "move" or not p["rows"]:
            continue
        ids = in_list([r["id"] for r in p["rows"]], True)
        tc = sql_string(p["target_code"])
        name = re.sub(r"[^a-z0-9]", "_", p["code"])[:40]
        lines.append(f"-- {p['folder']} -> {p['target_code']}")
        lines.append(check(f"target_photos_{name}", f"(select count(*) from image where image_of_id = {tc})", 0))
        lines.append(f"update image set image_of_id = {tc} where id in {ids} and image_of_id = {sql_string(p['folder'])};")
        lines.append(check(f"moved_{name}", "row_count()", len(p["rows"])))
        lines.append(f"update specimen set image_count = (select count(*) from image where image_of_id = {tc}) where code = {tc};")
    lines += ["commit;", "select 'committed' as result;"]
    return "\n".join(lines) + "\n"


def rollback_sql(plans, prefix, tables):
    """Puts the rows back from the backup tables, in one transaction, each change counted."""
    c = counts(plans)
    t = bak_tables(prefix)
    lines = ["-- Orphan photos: rollback from the backup tables (tools/data/orphan_photos.py rollback).",
             "set names utf8mb4;", "start transaction;"]
    if c["delete_ids"]:
        ids = in_list(c["delete_ids"], True)
        lines.append(check("ids_in_use", f"(select count(*) from image where id in {ids})", 0))
        lines.append(f"insert into image select * from {t['image']} where id in {ids};")
        lines.append(check("image_restored", "row_count()", len(c["delete_ids"])))
        lines.append(f"insert into group_image select * from {t['group_image']} where image_id in {ids};")
        lines.append(check("group_image_restored", "row_count()", c["delete_group_rows"]))
        if "image_like" in tables:
            lines.append(f"insert into image_like select * from {t['image_like']} where image_id in {ids};")
            lines.append(check("image_like_restored", "row_count()", c["delete_likes"]))
    if c["move_ids"]:
        ids = in_list(c["move_ids"], True)
        lines.append(f"update image i join {t['image']} b on b.id = i.id set i.image_of_id = b.image_of_id"
                     + (", i.modified = b.modified" if "image.modified" in tables else "") +
                     f" where i.id in {ids} and i.image_of_id <> b.image_of_id;")
        lines.append(check("moved_back", "row_count()", len(c["move_ids"])))
        lines.append(f"update specimen s join {t['specimen']} b on b.code = s.code set s.image_count = b.image_count;")
    lines += ["commit;", "select 'committed' as result;"]
    return "\n".join(lines) + "\n"


# ------------------------------------------------------------------ file steps (after the commit)

def _chown(path):
    try:
        os.chown(path, TOMCAT_UID, TOMCAT_GID)
    except OSError:
        pass  # the Spaces mount (rclone) sets owners itself


def apply_files(plans, images, log):
    """Deletes or renames the files of the accepted codes. Safe to run again: a file already gone (delete) or
    already renamed (move) is skipped. Returns the list of errors."""
    errors = []
    for p in accepted(plans):
        src_dir = os.path.join(images, p["folder"])
        if p["action"] == "move" and p["files"]:
            dst_dir = os.path.join(images, p["target_folder"])
            if not os.path.isdir(dst_dir):
                os.makedirs(dst_dir)
                _chown(dst_dir)
                log(f"mkdir {dst_dir}")
        for f in p["files"]:
            src = os.path.join(src_dir, f["name"])
            try:
                if p["action"] == "delete":
                    if os.path.exists(src):
                        os.remove(src)
                        log(f"deleted {src}")
                    else:
                        log(f"already gone {src}")
                else:
                    dst = os.path.join(images, p["target_folder"], f["new_name"])
                    if os.path.exists(dst) and not os.path.exists(src):
                        log(f"already moved {src} -> {dst}")
                    elif os.path.exists(dst):
                        raise OSError(f"{dst} exists already")
                    else:
                        os.rename(src, dst)
                        log(f"moved {src} -> {dst}")
            except OSError as e:
                errors.append(f"{src}: {e}")
                log(f"ERROR {src}: {e}")
        try:
            if p["files"] and os.path.isdir(src_dir) and not os.listdir(src_dir):
                os.rmdir(src_dir)
                log(f"rmdir {src_dir}")
        except OSError as e:
            log(f"left folder {src_dir}: {e}")
    return errors


def backup_files(plans, images, dest, log):
    """Copies the files of the accepted codes to <dest>/images/<folder>/<name>; checks each size."""
    errors = []
    for p in accepted(plans):
        for f in p["files"]:
            src = os.path.join(images, p["folder"], f["name"])
            dst = os.path.join(dest, "images", p["folder"], f["name"])
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            try:
                shutil.copyfile(src, dst)
                if os.path.getsize(dst) != f["size"]:
                    raise OSError(f"copied {os.path.getsize(dst)} bytes, expected {f['size']}")
                log(f"copied {src} -> {dst}")
            except OSError as e:
                errors.append(f"{src}: {e}")
                log(f"ERROR {src}: {e}")
    return errors


def restore_files(plans, images, dest, log):
    """Copies the files back from the backup to their old names, then removes the renamed copies of moved codes."""
    errors = []
    for p in accepted(plans):
        src_dir = os.path.join(images, p["folder"])
        if p["files"] and not os.path.isdir(src_dir):
            os.makedirs(src_dir)
            _chown(src_dir)
        for f in p["files"]:
            saved = os.path.join(dest, "images", p["folder"], f["name"])
            back = os.path.join(src_dir, f["name"])
            try:
                if os.path.exists(back) and os.path.getsize(back) == f["size"]:
                    log(f"already there {back}")
                else:
                    shutil.copyfile(saved, back)
                    _chown(back)
                    if os.path.getsize(back) != f["size"]:
                        raise OSError(f"restored {os.path.getsize(back)} bytes, expected {f['size']}")
                    log(f"restored {back}")
                if p["action"] == "move" and f["new_name"]:
                    moved = os.path.join(images, p["target_folder"], f["new_name"])
                    if os.path.exists(moved):
                        os.remove(moved)
                        log(f"removed {moved}")
            except OSError as e:
                errors.append(f"{back}: {e}")
                log(f"ERROR {back}: {e}")
        if p["action"] == "move" and p["target_folder"]:
            tdir = os.path.join(images, p["target_folder"])
            try:
                if os.path.isdir(tdir) and not os.listdir(tdir):
                    os.rmdir(tdir)
                    log(f"rmdir {tdir}")
            except OSError as e:
                log(f"left folder {tdir}: {e}")
    return errors


# ------------------------------------------------------------------ database

def db_login(project):
    props = {}
    with open(os.path.join(project, "etc", "db.properties"), encoding="utf-8") as f:
        for line in f:
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.rstrip("\n").split("=", 1)
                props[k.strip()] = v
    return props.get("user", ""), props.get("password", "")


def mysql(args, sql, batch=True):
    """Runs sql through mysql in the container; returns stdout. Exits with mysql's message on an error."""
    user, password = db_login(args.project)
    env = dict(os.environ, MYSQL_PWD=password)
    cmd = ["docker", "exec", "-i", "-e", "MYSQL_PWD", args.container, "mysql", "-u", user, "ant",
           "--default-character-set=utf8mb4"] + (["-B", "-N", "--raw"] if batch else ["--table"])
    r = subprocess.run(cmd, input=sql.encode("utf-8"), capture_output=True, env=env, timeout=1800)
    text = r.stdout.decode("utf-8", "replace")
    if r.returncode != 0:
        if not batch:
            print(text)
        sys.exit("mysql stopped: " + r.stderr.decode("utf-8", "replace").strip()
                 + ("\nNothing was committed." if not batch else ""))
    return text


def rows_of(text):
    return [line.split("\t") for line in text.split("\n") if line]


def mysql_line(args):
    user = db_login(args.project)[0]
    return (f"docker exec -i -e MYSQL_PWD {args.container} mysql -u {user} ant"
            f"   (password from {os.path.join(args.project, 'etc', 'db.properties')})")


def tables_present(args):
    names = ("image", "group_image", "image_like", "image_uploaded", "taxon_prop", "specimen")
    q = ("select table_name from information_schema.tables where table_schema = 'ant' and table_name in "
         + in_list(names) + ";")
    found = {r[0].lower() for r in rows_of(mysql(args, q))}
    q = ("select count(*) from information_schema.columns where table_schema = 'ant' and table_name = 'image'"
         " and column_name = 'modified';")
    if rows_of(mysql(args, q)) == [["1"]]:
        found.add("image.modified")   # rollback puts back its old value (it changes on every update)
    return found


def current_plan(args, decisions, tables):
    q = lookup_sql(decisions, tables)
    likes = {}
    if "likes" in q:
        likes = {int(i): int(n) for i, n in rows_of(mysql(args, q["likes"] + ";"))}
    uploaded = {}
    if "uploaded" in q:
        uploaded = {c: int(n) for c, n in rows_of(mysql(args, q["uploaded"] + ";"))}
    image_rows = []
    for i, code, shot, number, groups in rows_of(mysql(args, q["rows"] + ";")):
        image_rows.append(dict(id=int(i), code=code, shot=shot, number=int(number),
                               groups=[g for g in groups.split(",") if g], likes=likes.get(int(i), 0),
                               uploaded=uploaded.get(code.lower(), 0)))
    specimens = {}
    for code, group, n in rows_of(mysql(args, q["specimens"] + ";")):
        specimens[code.lower()] = (code, group, int(n))
    picks = {}
    if "picks" in q:
        for code, taxon in rows_of(mysql(args, q["picks"] + ";")):
            picks.setdefault(code, []).append(taxon)
    folders = {}
    names = {d["code"] for d in decisions} | {r["code"] for r in image_rows}
    names |= {specimens[d["target"]][0] for d in decisions if d["target"] and d["target"] in specimens}
    for name in sorted(names):
        try:
            folders[name] = list_folder(args.images, name)
        except OSError as e:
            sys.exit(f"Cannot read {os.path.join(args.images, name)}: {e} (is the /mnt/antweb mount working? "
                     "docs/runbook.md, Storage mounts)")
    return plan(decisions, image_rows, specimens, folders, picks)


# ------------------------------------------------------------------ commands

def backup_dir(args):
    tag = ("_" + re.sub(r"[^a-z0-9]", "", args.tag.lower())) if args.tag else ""
    return os.path.join(args.backup_root, f"orphan_photos_{args.date}{tag}"), f"orphan_photos_bak_{args.date}{tag}"


def start(args):
    if not os.path.isfile(os.path.join(args.project, "etc", "db.properties")):
        sys.exit(f"No {os.path.join(args.project, 'etc', 'db.properties')}: is --project the AntWeb checkout "
                 "(/root/antweb on production, /root/antweb-next on staging)?")
    parent = os.path.dirname(args.images.rstrip("/"))
    if os.path.ismount(parent):
        if not os.path.isdir(args.images):
            sys.exit(f"No folder {args.images}: is the {parent} mount working? (docs/runbook.md, Storage mounts)")
    else:
        print(f"Note: {parent} is a plain folder (staging), not the production bucket: photos may be missing "
              "here; then only the rows change.")
    decisions = read_decisions(args.decisions)
    print("Database: " + mysql_line(args))
    tables = tables_present(args)
    for needed in ("image", "group_image", "specimen"):
        if needed not in tables:
            sys.exit(f"Table {needed} not found in database ant.")
    return decisions, tables


def cmd_preview(args):
    decisions, tables = start(args)
    plans = current_plan(args, decisions, tables)
    print()
    print_plan(plans, args.images, verbose=args.files)
    print("\nPreview only: nothing was changed.")


def cmd_backup(args):
    decisions, tables = start(args)
    plans = current_plan(args, decisions, tables)
    print_plan(plans, args.images)
    if not accepted(plans):
        sys.exit("Nothing to back up: no code is accepted.")
    dest, prefix = backup_dir(args)
    manifest = os.path.join(dest, "manifest.json")
    if os.path.exists(manifest):
        sys.exit(f"{manifest} exists: the backup of {args.date} was made already. Use another --tag, or see "
                 "docs/runbook.md (Orphan photos) to start again.")
    os.makedirs(os.path.join(dest, "db"), exist_ok=True)
    log = logger(os.path.join(dest, "backup.log"))
    log(f"backup to {dest}, tables {prefix}_*")
    sql = backup_sql(plans, prefix, tables)
    write(os.path.join(dest, "backup.sql"), sql)
    print("\nRunning:\n" + sql)
    print(mysql(args, sql, batch=False))
    for name, table in bak_tables(prefix).items():
        if name in tables or name == "specimen":
            text = mysql(args, f"select * from {table};")
            write(os.path.join(dest, "db", table + ".tsv"), text)
    errors = backup_files(plans, args.images, dest, log)
    if errors:
        sys.exit(f"{len(errors)} files were not copied (see {dest}/backup.log); no manifest written, so run "
                 "refuses to start. First: " + errors[0])
    c = counts(plans)
    write(manifest, json.dumps(dict(
        created=datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
        project=args.project, decisions=os.path.abspath(args.decisions), images=args.images, prefix=prefix,
        tables=sorted(tables), plan=signature(plans), plans=accepted(plans),
        counts={k: v for k, v in c.items() if not k.endswith("_ids")}), indent=1))
    print(f"\nBackup done: {c['files']} files ({c['bytes'] / 1e6:.1f} MB) in {dest}/images, "
          f"{len(c['all_ids'])} image rows in {prefix}_image (and TSV copies in {dest}/db). Manifest: {manifest}")


def find_backup(args):
    """The newest backup folder of this decisions file and project when --date is not given."""
    if args.date:
        return
    found = []
    root = args.backup_root
    for name in sorted(os.listdir(root)) if os.path.isdir(root) else []:
        if not re.fullmatch(r"orphan_photos_\d{8}(_[a-z0-9]+)?", name):
            continue
        if args.tag and not name.endswith("_" + re.sub(r"[^a-z0-9]", "", args.tag.lower())):
            continue
        try:
            with open(os.path.join(root, name, "manifest.json"), encoding="utf-8") as f:
                m = json.load(f)
        except (OSError, ValueError):
            continue
        if os.path.basename(m.get("decisions", "")) == os.path.basename(args.decisions) \
                and os.path.realpath(m.get("project", "")) == os.path.realpath(args.project):
            found.append((m.get("created", ""), name))
    if not found:
        sys.exit(f"No backup of {os.path.basename(args.decisions)} in {root}: run the backup step first.")
    name = max(found)[1]
    args.date = name.split("_")[2]
    args.tag = name.split("_", 3)[3] if name.count("_") > 2 else ""
    print(f"Backup: {os.path.join(root, name)}")


def load_manifest(args):
    find_backup(args)
    dest, prefix = backup_dir(args)
    path = os.path.join(dest, "manifest.json")
    if not os.path.exists(path):
        sys.exit(f"No backup at {path}: run the backup step first (same --date and --tag).")
    with open(path, encoding="utf-8") as f:
        return dest, json.load(f)


def confirm(args, question):
    if args.yes:
        return
    answer = input(question + " Type yes to go on: ")
    if answer.strip().lower() != "yes":
        sys.exit("Stopped: nothing was changed.")


def cmd_run(args):
    decisions, tables = start(args)
    dest, m = load_manifest(args)
    state_path = os.path.join(dest, "run.json")
    state = json.load(open(state_path)) if os.path.exists(state_path) else {}
    log = logger(os.path.join(dest, "run.log"))
    if state.get("rolled_back"):
        sys.exit("This backup was rolled back already. Make a new backup (--tag) to run again.")
    plans = [dict(p) for p in m["plans"]]
    if not state.get("sql_committed"):
        age = datetime.datetime.now(datetime.timezone.utc) - datetime.datetime.fromisoformat(m["created"])
        if age > datetime.timedelta(hours=args.max_age_hours):
            sys.exit(f"The backup is {age} old (more than {args.max_age_hours} hours): make a fresh one (--tag).")
        now = current_plan(args, decisions, tables)
        if signature(now) != m["plan"]:
            sys.exit("The photos changed since the backup (rows, files or decisions differ). Run preview, then a "
                     "fresh backup with --tag.")
        plans = accepted(now)
        print_plan(plans, args.images)
        sql = run_sql(plans, tables)
        write(os.path.join(dest, "run.sql"), sql)
        print("\nWill run:\n" + sql)
        confirm(args, "Then the files are deleted or renamed.")
        out = mysql(args, sql, batch=False)
        print(out)
        if "committed" not in out:
            sys.exit("No commit seen: check the output above; the files were not touched.")
        state = dict(sql_committed=datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"))
        write(state_path, json.dumps(state, indent=1))
        log("SQL committed")
    else:
        print(f"The rows were changed at {state['sql_committed']}; finishing the files only.")
    errors = apply_files(plans, args.images, log)
    state["files_done"] = not errors
    state["file_errors"] = errors
    write(state_path, json.dumps(state, indent=1))
    c = counts(plans)
    if errors:
        sys.exit(f"{len(errors)} files failed (log {dest}/run.log); run the same command again to retry them. "
                 "First: " + errors[0])
    print(f"\nDone: {c['delete_codes']} codes deleted, {c['move_codes']} moved, {c['files']} files. Log: {dest}/run.log\n"
          "The target specimens' image_count is set; taxon, place and home page image numbers follow at the next "
          "nightly run (set 4).")


def cmd_rollback(args):
    decisions, tables = start(args)
    dest, m = load_manifest(args)
    state_path = os.path.join(dest, "run.json")
    if not os.path.exists(state_path):
        sys.exit("The run step has not changed anything for this backup: nothing to roll back.")
    state = json.load(open(state_path))
    plans = [dict(p) for p in m["plans"]]
    log = logger(os.path.join(dest, "rollback.log"))
    if not state.get("rows_restored"):
        sql = rollback_sql(plans, m["prefix"], set(m["tables"]))
        write(os.path.join(dest, "rollback.sql"), sql)
        print("Will run:\n" + sql)
        confirm(args, "Then the files are copied back from the backup.")
        out = mysql(args, sql, batch=False)
        print(out)
        if "committed" not in out:
            sys.exit("No commit seen: check the output above; the files were not touched.")
        state["rows_restored"] = datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")
        state["rolled_back"] = True
        write(state_path, json.dumps(state, indent=1))
        log("rows restored")
    errors = restore_files(plans, args.images, dest, log)
    state["files_restored"] = not errors
    write(state_path, json.dumps(state, indent=1))
    if errors:
        sys.exit(f"{len(errors)} files were not restored (log {dest}/rollback.log); run rollback again to retry. "
                 "First: " + errors[0])
    print(f"Rollback done. Log: {dest}/rollback.log. Image numbers follow at the next nightly run.")


def write(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def logger(path):
    def log(message):
        line = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S ") + message
        print(line)
        with open(path, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    return log


def main(argv=None):
    here = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = p.add_subparsers(dest="cmd", required=True)
    for name, func, text in (("preview", cmd_preview, "read-only: what would happen to each code"),
                             ("backup", cmd_backup, "copy the files and rows that will change"),
                             ("run", cmd_run, "delete and move (needs a fresh backup)"),
                             ("rollback", cmd_rollback, "put rows and files back from the backup")):
        a = sub.add_parser(name, help=text)
        a.add_argument("--project", default=here, help="AntWeb checkout with etc/db.properties (default: this checkout)")
        a.add_argument("--decisions", required=True, help="decisions file (TSV: code, action, target, group, note)")
        a.add_argument("--images", default="/mnt/antweb/images")
        a.add_argument("--backup-root", default="/mnt/backup/archive")
        a.add_argument("--date", default="", help="backup date (YYYYMMDD); backup: today; run and rollback: the "
                       "newest backup of the decisions file")
        a.add_argument("--tag", default="", help="added to the backup folder and tables, for a second backup")
        a.add_argument("--container", default="antweb_mysql_1")
        a.add_argument("--max-age-hours", type=float, default=24)
        a.add_argument("--yes", action="store_true", help="do not ask before changing anything")
        a.add_argument("--files", action="store_true", help="list every file (preview)")
        a.set_defaults(func=func)
    args = p.parse_args(argv)
    if args.date and not re.fullmatch(r"\d{8}", args.date):
        sys.exit("--date must be YYYYMMDD")
    if args.cmd in ("preview", "backup") and not args.date:
        args.date = datetime.date.today().strftime("%Y%m%d")
    args.func(args)


if __name__ == "__main__":
    main()
