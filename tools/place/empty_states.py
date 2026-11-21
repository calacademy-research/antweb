#!/usr/bin/env python3
"""Retire the empty states ISO 3166-2 does not have (Brian Fisher, 2026-11-20: adopt ISO 3166-2; 2026-11-21: the 602
states iso_states.py reports as "no ISO 3166-2 subdivision written alike" hold 4,662 specimens, most none: Latvia's
former districts, Serbia's municipalities, Malta's local councils, loaded long ago).

A state is retired (is_valid = 0, no valid name: no longer listed, its page says it is not valid) when all hold:
  - iso_states.py reports it: no ISO 3166-2 subdivision of its country is written like it (or NOT_ISO rules it out);
    countries whose ISO list is older than their units (Madagascar, the Central African Republic) are never touched,
    nor countries ISO gives no subdivisions;
  - no specimen has it as adm1, written exactly or with other capitals, accents or punctuation;
  - no old-name row leads to it (an upload of that name would find a state that is not valid);
  - no species list entry other than from specimens (geolocale_taxon: curator, antwiki or any other source), and no
    curator dispute (geolocale_taxon_dispute);
  - no page text under its name (object_edit; keyed by name alone).
Each state that fails one is listed with the reason and left as it is.

  python3 empty_states.py                     plan only: the summary, and the plan in <folder>/plan.tsv
  python3 empty_states.py --apply             applies the plan reviewed today: refuses when <folder>/plan.tsv is missing
                                              or the plan differs from it now; backup table empty_states_bak_<date>
  python3 empty_states.py --tag 20261121b     a second run the same day (its own folder and backup table)
  python3 empty_states.py --skip FILE         lines "country|state" left alone
  python3 empty_states.py --project /root/antweb-next      staging (default: the project on this server)

Rollback (same server, the date or --tag of the run):
  update geolocale g join empty_states_bak_<date> b on b.id = g.id set g.is_valid = b.is_valid, g.valid_name = b.valid_name, g.admin_notes = b.admin_notes;
then reload the places (util.do?action=reloadAntwebMgr&name=geolocale, administrator). Nothing else is changed: the
rows keep their ids, so a state put back has its lists and counts as before."""
import argparse
import collections
import datetime
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import iso_states as iso  # noqa: E402
import place_fix as pf    # noqa: E402

NOT_IN_ISO = ("no ISO 3166-2 subdivision written alike", "not an ISO 3166-2 subdivision")
NOTE = "empty_states %s: retired, no ISO 3166-2 subdivision and no specimens"


def candidates(valid, reported):
    """The valid states iso_states reports as not in ISO 3166-2 and with no specimen under their exact name:
    [(id, country, name)]."""
    ids = {(c, n): i for c, rows in valid.items() for i, n, s in rows}
    return sorted((ids[(c, n)], c, n) for c, n, s, why in reported if s == 0 and why.startswith(NOT_IN_ISO) and (c, n) in ids)


def blockers(cands, spelled, olds, lists, disputes, texts):
    """{id: reason} for the candidates that must stay. spelled: Counter((country, key(adm1))) of specimens; olds:
    {(country, valid name): [old names]}; lists: {id: {source: rows}}; disputes: Counter(id); texts: set(normal(key))."""
    out = {}
    for i, c, n in cands:
        why = []
        if spelled.get((c, iso.key(n))):
            why.append("%d specimens with the name written otherwise" % spelled[(c, iso.key(n))])
        if olds.get((c, n)):
            why.append("old names lead to it (%s)" % ", ".join(sorted(olds[(c, n)])[:3]))
        other = {s: k for s, k in lists.get(i, {}).items() if s != "specimen"}
        if other:
            why.append("species list entries (%s)" % ", ".join("%s %d" % (s, k) for s, k in sorted(other.items())))
        if disputes.get(i):
            why.append("%d curator disputes" % disputes[i])
        if iso.normal(n) in texts:
            why.append("page text under its name")
        if why:
            out[i] = "; ".join(why)
    return out


def plan_lines(cands, blocked):
    return sorted(("keep\t%s\t%s\t%s" % (c, n, blocked[i])) if i in blocked else ("retire\t%s\t%s\t%d" % (c, n, i)) for i, c, n in cands)


def retire_sql(ids, tag):
    return ["update geolocale set is_valid = 0, admin_notes = concat(ifnull(concat(admin_notes, '\\n'), ''), %s) "
            "where georank = 'adm1' and is_valid = 1 and id in (%s)" % (iso.q(NOTE % tag), ",".join(map(str, sorted(ids))))]


def read_checks(db, ids):
    spelled = collections.Counter()
    for r in pf.run_sql(db, "select " + pf.hx("country") + ", " + pf.hx("adm1") + ", count(*) from specimen where adm1 <> '' group by 1, 2"):
        spelled[(pf.unhex(r[0]), iso.key(pf.unhex(r[1])))] += int(r[2])
    lists, disputes = collections.defaultdict(dict), collections.Counter()
    if ids:
        inl = ",".join(map(str, ids))
        for r in pf.run_sql(db, "select geolocale_id, ifnull(source, ''), count(*) from geolocale_taxon where geolocale_id in (%s) group by 1, 2" % inl):
            lists[int(r[0])][r[1] or "(none)"] = int(r[2])
        for r in pf.run_sql(db, "select geolocale_id, count(*) from geolocale_taxon_dispute where geolocale_id in (%s) group by 1" % inl):
            disputes[int(r[0])] = int(r[1])
    texts = {iso.normal(pf.unhex(r[0])) for r in pf.run_sql(db, "select distinct " + pf.hx("object_key") + " from object_edit")}
    return spelled, lists, disputes, texts


def main(argv=None):
    a = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    a.add_argument("--project", default=None)
    a.add_argument("--container", default="antweb_mysql_1")
    a.add_argument("--database", default="ant")
    a.add_argument("--apply", action="store_true")
    a.add_argument("--skip", default=None)
    a.add_argument("--folder", default=None)
    a.add_argument("--tag", default=None)
    a.add_argument("--show", type=int, default=30)
    args = a.parse_args(argv)
    db = pf.Db(args.project or pf.default_project(), args.container, args.database)
    tag = args.tag or datetime.date.today().strftime("%Y%m%d")
    if not re.fullmatch(r"\d{8}[a-z]?", tag):
        raise SystemExit("--tag takes the date and at most one letter (20261121 or 20261121b): " + tag)
    folder = args.folder or os.path.join("/root/antweb-place-fix/empty", tag)
    os.makedirs(folder, exist_ok=True)
    skip = set()
    if args.skip:
        with open(args.skip, encoding="utf-8") as f:
            skip = {l.strip() for l in f if l.strip() and not l.startswith("#")}
    valid, olds, names = iso.read_db(db)
    decisions, reported, nocountry = iso.plan(valid, olds, iso.read_iso(), iso.read_countries(), skip)
    cands = candidates(valid, reported)
    spelled, lists, disputes, texts = read_checks(db, [i for i, c, n in cands])
    blocked = blockers(cands, spelled, olds, lists, disputes, texts)
    lines = plan_lines(cands, blocked)
    retire = [i for i, c, n in cands if i not in blocked]
    plan_file = os.path.join(folder, "plan.tsv")
    if args.apply:
        if not os.path.exists(plan_file):
            raise SystemExit("No plan in %s: run without --apply first and review it. Nothing was changed." % plan_file)
        with open(plan_file, encoding="utf-8") as f:
            if sorted(l.rstrip("\n") for l in f if l.strip() and not l.startswith("#")) != lines:
                raise SystemExit("The plan differs from the one reviewed in %s (the data changed since): run without --apply again. "
                                 "Nothing was changed." % plan_file)
    else:
        with open(plan_file, "w", encoding="utf-8") as f:
            f.write("# empty_states %s, %s: action, country, state, id (retire) or why it stays (keep)\n" % (tag, db.project))
            f.write("\n".join(lines) + "\n")
    by_country = collections.Counter(c for i, c, n in cands if i not in blocked)
    print("empty_states %s, %s: %d states reported as not in ISO 3166-2 with no specimens; retire %d, keep %d"
          % (tag, db.project, len(cands), len(retire), len(blocked)))
    print("  plan: " + plan_file)
    print("  retire by country: " + ", ".join("%s %d" % (c, k) for c, k in by_country.most_common(args.show)))
    why = collections.Counter(re.sub(r" \(.*?\)|^\d+ ", "", w) for r in blocked.values() for w in r.split("; "))
    for w, k in why.most_common():
        print("  keep, %s: %d" % (w, k))
    for l in [l for l in lines if l.startswith("keep")][:args.show]:
        print("  " + l.replace("\t", " | "))
    if not args.apply:
        print("Plan only. Add --apply to make the change.")
        return 0
    if not retire:
        print("Nothing to apply.")
        return 0
    bak = "empty_states_bak_" + tag
    if int(pf.run_sql(db, "select count(*) from information_schema.tables where table_schema = database() and table_name = '%s'" % bak)[0][0]):
        raise SystemExit("The backup table %s exists already: not applied twice. Nothing was changed." % bak)
    inl = ",".join(map(str, sorted(retire)))
    pf.run_sql(db, "create table %s as select id, name, parent, is_valid, valid_name, admin_notes from geolocale where false" % bak)
    pf.run_sql(db, "start transaction;\ninsert into %s select id, name, parent, is_valid, valid_name, admin_notes from geolocale where id in (%s);\n"
               % (bak, inl) + ";\n".join(retire_sql(retire, tag)) + ";\ncommit;")
    print("backup: %s rows in %s" % (pf.run_sql(db, "select count(*) from " + bak)[0][0], bak))
    left = pf.run_sql(db, "select count(*) from geolocale where id in (%s) and is_valid = 1" % inl)[0][0]
    print("after: states of the plan still valid %s (expect 0)" % left)
    print("Now reload the places: /util.do?action=reloadAntwebMgr&name=geolocale")
    return 0


if __name__ == "__main__":
    sys.exit(main())
