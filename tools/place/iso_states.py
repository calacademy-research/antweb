#!/usr/bin/env python3
"""AntWeb's states under their ISO 3166-2 names (Brian Fisher, 2026-11-20: "let's adopt ISO 3166-2"; the states keep
changing, so this is run again about once a year with a new iso3166_2.tsv, see build_iso3166_2.py).

Each valid state (adm1) is matched to an ISO 3166-2 subdivision of its country: by its name, or by one of its old names
(rows that are not valid and lead to it), against the ISO name, its translations and the geoBoundaries shape names of
that subdivision, written alike (capitals, accents, blanks and punctuation aside, as GeolocaleMgr.adm1SpellingKey).
When none is written alike, a name with the generic words left out ("Kherson" for "Khersonska oblast") is tried, and
used only when it finds exactly one subdivision and no other state finds it too.

  rename    one valid state matches the subdivision and is not written as ISO writes it: it takes the ISO name; the
            name it had becomes an old name of it (a row not valid, valid name the ISO name), so an upload that writes
            the old name is translated (GeolocaleMgr.adm1ToStore); its specimens get the ISO name. The row keeps its
            id, so its lists, counts, box and bioregion stay.
  merge     two or more valid states match the same subdivision by their written names (Aglona and Aglonas;
            Banskobystrický and Banskobystrický kraj): the one written as ISO, else the one with most specimens, is
            kept and takes the ISO name; the others become old names of it and their specimens move to it.
  report    the rest is listed and not changed: states no subdivision matches (Madagascar's regions: ISO still has
            the six provinces; French overseas arrondissements), a state that matches two subdivisions of different
            kinds equally, and a looser match that is not alone.

Where a subdivision has two kinds (Spain: communities and provinces; Bangladesh: divisions and districts), a state
written like both takes the kind most of its country's states match.

  python3 iso_states.py                       plan only: the summary, and the plan in <folder>/plan.tsv
  python3 iso_states.py --apply               backup tables iso_states_bak_<date> (places) and iso_states_spec_<date>
                                              (specimens), then the changes
  python3 iso_states.py --skip FILE           lines "country|state" left alone (a state looked at by hand)
  python3 iso_states.py --project /root/antweb-next      staging (default: the project on this server)

Rollback (same server, the date of the run):
  update specimen s join iso_states_spec_<date> b on b.code = s.code set s.adm1 = b.adm1 where binary s.adm1 = binary b.new_adm1;
  update geolocale g join iso_states_bak_<date> b on b.id = g.id set g.name = concat('~rb~', g.id) where binary g.name <> binary b.name;
  update geolocale g join iso_states_bak_<date> b on b.id = g.id set g.name = b.name, g.is_valid = b.is_valid, g.valid_name = b.valid_name, g.admin_notes = b.admin_notes;
  delete from geolocale where admin_notes like 'iso_states <date>: added%';
then reload the places (util.do?action=reloadAntwebMgr&name=geolocale, administrator)."""
import argparse
import collections
import datetime
import os
import re
import sys
import unicodedata

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import place_fix as pf  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ISO_FILE = os.path.join(HERE, "iso3166_2.tsv")
COUNTRY_FILE = os.path.join(HERE, "iso3166_countries.tsv")

# AntWeb's country names that ISO 3166-1 writes otherwise. A country not found is reported, not changed.
COUNTRY_NAMES = {
    "Bolivia": "BO", "Brunei": "BN", "Cabo Verde": "CV", "Côte d'Ivoire": "CI", "Czechia": "CZ", "Democratic Republic of the Congo": "CD",
    "Congo": "CG", "Eswatini": "SZ", "Iran": "IR", "Laos": "LA", "Micronesia": "FM", "Moldova": "MD", "North Korea": "KP",
    "North Macedonia": "MK", "Macedonia": "MK", "Palestine": "PS", "Reunion": "RE", "Russia": "RU", "South Korea": "KR", "Syria": "SY",
    "Taiwan": "TW", "Tanzania": "TZ", "Türkiye": "TR", "Turkey": "TR", "United Kingdom": "GB", "United States": "US",
    "Venezuela": "VE", "Vietnam": "VN", "Saint Helena, Ascension and Tristan da Cunha": "SH",
}

GENERIC = set("""province provincia provinsi provincie region regiao región région regione state estado departement département
departamento district distrikt county governorate muhafazah oblast oblasti oblast' voblasts voblasts' voblast kray krai
respublika republic prefecture voivodeship division municipality municipio municipiul zupanija novads novada kraj
wilaya changwat parish canton kanton land ostan vilayeti ili okrug avtonomnyy avtonomnaya avtonomna autonomous city
grad gorod shahri rajons apskritis savivaldybe of de del la le the y and et und das""".split())


def normal(name):
    """GeolocaleMgr.normalName: accents off, capitals off, blanks as one."""
    s = unicodedata.normalize("NFKD", name or "")
    s = "".join(c for c in s if not unicodedata.category(c).startswith("M"))
    s = s.lower().upper().lower()
    return " ".join(w for w in re.split(r"[\s   -​    　\x1c-\x1f\x85]+", s) if w)


def key(name):
    """GeolocaleMgr.adm1SpellingKey: also hyphens, dashes, slashes and underscores as blanks; apostrophes, periods, commas,
    brackets and quotes left out."""
    s = re.sub("[-‐-―/_]", " ", name or "")
    s = re.sub("['‘’ʻʼʿʾ`´.,()\\[\\]\"]", "", s)
    return normal(s)


def loose(name):
    """The key without the generic words (province, oblast, kraj ...): the looser match."""
    return " ".join(w for w in key(name).split() if w not in GENERIC)


INVERT = re.compile(r"^(.*), (Respublika|Région de|Region de|Región de|Principado de|Comunidad de|Comunidad Foral de|Comunidad|Région|"
                    r"Hlavní město|Grad|Avtonomna Respublika|Unitatea teritorială autonomă \(UTAG\)|unitatea teritorială din)$")


def display(iso_name):
    """The name AntWeb gives a subdivision: ISO's first form of "A [B]"; a name ISO writes inverted for sorting
    ("Zagreb, Grad", "Adygeya, Respublika") the right way round. Lists ("Armagh City, Banbridge and Craigavon") and
    "Bristol, City of" (ISO's own form for the city) stay as ISO writes them."""
    n = re.sub(r"\s*\[.*?\]\s*$", "", iso_name).strip()
    m = INVERT.match(n)
    if m:
        n = m.group(2) + " " + m.group(1)
        n = n[0].upper() + n[1:]
    return n


Sub = collections.namedtuple("Sub", "code country type parent name others")


def read_iso(path=ISO_FILE):
    subs = collections.defaultdict(list)
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            code, country, kind, parent, name, others = (line.rstrip("\n").split("\t") + [""] * 6)[:6]
            subs[country].append(Sub(code, country, kind, parent, name, [o for o in others.split(" | ") if o]))
    return subs


def read_countries(path=COUNTRY_FILE):
    by_name = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            parts = line.rstrip("\n").split("\t")
            for n in parts[1:]:
                if n:
                    by_name[key(n)] = parts[0]
    for n, a2 in COUNTRY_NAMES.items():
        by_name[key(n)] = a2
    return by_name


State = collections.namedtuple("State", "id name specimens old_names")


def match_country(states, subs):
    """states: the valid states of one country (State); subs: its ISO subdivisions. Returns (decisions, reported):
    decisions [(action, state, sub, kept_state)] with action 'rename' or 'merge' (state becomes an old name of
    kept_state, which takes sub's ISO name); reported [(state, reason)]."""
    exact = collections.defaultdict(set)
    looser = collections.defaultdict(set)
    for s in subs:
        for n in [s.name, display(s.name)] + re.findall(r"\[(.*?)\]", s.name) + s.others:
            if key(n):
                exact[key(n)].add(s.code)
            if loose(n):
                looser[loose(n)].add(s.code)
    by_code = {s.code: s for s in subs}
    hits, how = {}, {}
    for st in states:
        h = set()
        for n in [st.name] + list(st.old_names):
            h |= exact.get(key(n), set())
        if h:
            hits[st.id], how[st.id] = h, "exact"
        else:
            h = looser.get(loose(st.name), set()) if loose(st.name) else set()
            hits[st.id], how[st.id] = h, "loose"
    kinds = collections.Counter(by_code[next(iter(h))].type for h in hits.values() if len(h) == 1)
    main = kinds.most_common(1)[0][0] if kinds else None
    chosen, reported = {}, []
    for st in states:
        h = hits[st.id]
        if len(h) > 1 and main:
            same = {c for c in h if by_code[c].type == main}
            if len(same) == 1:
                h = same
        if len(h) == 1:
            chosen[st.id] = next(iter(h))
        elif not h:
            reported.append((st, "no ISO 3166-2 subdivision written alike"))
        else:
            reported.append((st, "written like " + ", ".join(sorted(h))))
    by_sub = collections.defaultdict(list)
    for st in states:
        if st.id in chosen:
            by_sub[chosen[st.id]].append(st)
    decisions = []
    for code, group in sorted(by_sub.items()):
        sub = by_code[code]
        iso = display(sub.name)
        if len(group) == 1:
            st = group[0]
            if how[st.id] == "loose" and any(chosen.get(o.id) == code for o in states if o.id != st.id):
                reported.append((st, "loose match shared"))
            elif st.name != iso:
                decisions.append(("rename", st, sub, st))
            continue
        # a looser match joins only states that differ by generic words alone (Banskobystrický, Banskobystrický kraj)
        if any(how[st.id] == "loose" for st in group) and len({loose(st.name) for st in group}) > 1:
            for st in group:
                reported.append((st, "with %s matches %s (%s): looked at by hand" % (", ".join(o.name for o in group if o is not st), code, iso)))
            continue
        kept = next((st for st in group if st.name == iso), None) or max(group, key=lambda st: (st.specimens, -st.id))
        if kept.name != iso:
            decisions.append(("rename", kept, sub, kept))
        for st in group:
            if st is not kept:
                decisions.append(("merge", st, sub, kept))
    return decisions, reported


def plan(valid, olds, iso, countries, skip=frozenset()):
    """valid: {country: [(id, name, specimens)]}; olds: {(country, valid name): [old names]}. Returns (decisions as
    dicts, reported, countries without ISO subdivisions)."""
    out, reported, nocountry = [], [], []
    for country in sorted(valid):
        a2 = countries.get(key(country))
        subs = iso.get(a2, []) if a2 else []
        rows = [State(i, n, c, tuple(olds.get((country, n), ()))) for i, n, c in valid[country] if (country + "|" + n) not in skip]
        if not subs:
            if rows:
                nocountry.append((country, len(rows)))
            continue
        decisions, rep = match_country(rows, subs)
        for action, st, sub, kept in decisions:
            out.append({"country": country, "action": action, "id": st.id, "name": st.name, "specimens": st.specimens, "code": sub.code,
                        "type": sub.type, "iso": display(sub.name), "kept_id": kept.id, "kept_name": kept.name})
        reported += [(country, st.name, st.specimens, why) for st, why in rep]
    return out, reported, nocountry


# --- the database --------------------------------------------------------------------------------------------------

def read_db(db):
    valid = collections.defaultdict(list)
    for r in pf.run_sql(db, "select id, " + pf.hx("parent") + ", " + pf.hx("name") + " from geolocale where georank = 'adm1' and is_valid = 1"):
        valid[pf.unhex(r[1])].append([int(r[0]), pf.unhex(r[2]), 0])
    count = collections.Counter()
    for r in pf.run_sql(db, "select " + pf.hx("country") + ", " + pf.hx("adm1") + ", count(*) from specimen where adm1 <> '' group by 1, 2"):
        count[(pf.unhex(r[0]), pf.unhex(r[1]))] += int(r[2])
    for c, rows in valid.items():
        for row in rows:
            row[2] = count[(c, row[1])]
    olds = collections.defaultdict(list)
    for r in pf.run_sql(db, "select " + pf.hx("parent") + ", " + pf.hx("name") + ", " + pf.hx("valid_name")
                        + " from geolocale where georank = 'adm1' and (is_valid = 0 or is_valid is null) and valid_name <> ''"):
        olds[(pf.unhex(r[0]), pf.unhex(r[2]))].append(pf.unhex(r[1]))
    names = collections.defaultdict(list)          # (country, exact name) -> [(id, valid, valid_name)]
    for r in pf.run_sql(db, "select id, " + pf.hx("parent") + ", " + pf.hx("name") + ", ifnull(is_valid, 0), " + pf.hx("valid_name")
                        + " from geolocale where georank = 'adm1'"):
        names[(pf.unhex(r[1]), pf.unhex(r[2]))].append((int(r[0]), int(r[3]), pf.unhex(r[4])))
    return {c: [tuple(x) for x in v] for c, v in valid.items()}, olds, names


def q(value):
    return "convert(unhex('%s') using utf8mb4)" % (value or "").encode("utf-8").hex()


def statements(decisions, names, tag):
    """The SQL of the change, and the list of what could not be done (left out). Renames first, then merges."""
    sql, left = [], []
    renamed = {}
    for d in decisions:
        if d["action"] != "rename":
            continue
        c, old, iso, vid = d["country"], d["name"], d["iso"], d["id"]
        other = [x for x in names.get((c, iso), []) if x[0] != vid]
        if any(x[1] == 1 for x in other) and not any(m["action"] == "merge" and m["country"] == c and m["name"] == iso for m in decisions):
            left.append("%s: %s, ISO name %s is another valid state" % (c, old, iso))
            continue
        note = "iso_states %s: named %s, ISO 3166-2 %s; was %s." % (tag, iso, d["code"], old)
        swap = [x for x in other if x[1] != 1 and x[2] in (old, "")]
        if any(x[1] != 1 and x[2] not in (old, "") for x in other):
            left.append("%s: %s, a row named %s is an old name of another state" % (c, old, iso))
            continue
        if any(x[1] == 1 for x in other):        # a valid state of the ISO name merged into this one below: move it aside
            for x in other:
                sql.append("update geolocale set name = %s where id = %d" % (q("~iso~%d" % x[0]), x[0]))
            renamed.update({x[0]: "~iso~%d" % x[0] for x in other})
        elif swap:
            sql.append("update geolocale set name = %s where id = %d" % (q("~iso~%d" % swap[0][0]), swap[0][0]))
        sql.append("update geolocale set name = %s, admin_notes = concat(ifnull(concat(admin_notes, '\\n'), ''), %s) where id = %d" % (q(iso), q(note), vid))
        if swap and not any(x[1] == 1 for x in other):
            sql.append("update geolocale set name = %s, is_valid = 0, valid_name = %s, admin_notes = concat(ifnull(concat(admin_notes, '\\n'), ''), %s) where id = %d"
                       % (q(old), q(iso), q("iso_states %s: old name of %s (id %d)." % (tag, iso, vid)), swap[0][0]))
        else:
            sql.append("insert into geolocale (name, georank, parent, source, is_valid, valid_name, admin_notes) values (%s, 'adm1', %s, 'antweb', 0, %s, %s)"
                       % (q(old), q(c), q(iso), q("iso_states %s: added, old name of %s (ISO 3166-2 %s), id %d." % (tag, iso, d["code"], vid))))
        sql.append("update geolocale set valid_name = %s where georank = 'adm1' and parent = %s and binary valid_name = binary %s" % (q(iso), q(c), q(old)))
        sql.append("update specimen set adm1 = %s where country = %s and binary adm1 = binary %s" % (q(iso), q(c), q(old)))
    for d in decisions:
        if d["action"] != "merge":
            continue
        c, old, iso = d["country"], d["name"], d["iso"]
        note = "iso_states %s: the same state as %s (ISO 3166-2 %s, id %d); made an old name of it, its specimens moved there." % (tag, iso, d["code"], d["kept_id"])
        name_now = renamed.get(d["id"])
        if name_now:        # it had the ISO name and was moved aside above: it keeps no name of its own, so it takes the kept state's old name
            sql.append("update geolocale set name = %s, is_valid = 0, valid_name = %s, admin_notes = concat(ifnull(concat(admin_notes, '\\n'), ''), %s) where id = %d"
                       % (q("~merged~%d" % d["id"]), q(iso), q(note), d["id"]))
        else:
            sql.append("update geolocale set is_valid = 0, valid_name = %s, admin_notes = concat(ifnull(concat(admin_notes, '\\n'), ''), %s) where id = %d"
                       % (q(iso), q(note), d["id"]))
        sql.append("update geolocale set valid_name = %s where georank = 'adm1' and parent = %s and binary valid_name = binary %s" % (q(iso), q(c), q(old)))
        sql.append("update specimen set adm1 = %s where country = %s and binary adm1 = binary %s" % (q(iso), q(c), q(old)))
    return sql, left


def write_plan(path, decisions, reported, nocountry):
    with open(path, "w", encoding="utf-8") as f:
        f.write("action\tcountry\tstate\tspecimens\tiso_name\tcode\ttype\tkept\n")
        for d in decisions:
            f.write("\t".join([d["action"], d["country"], d["name"], str(d["specimens"]), d["iso"], d["code"], d["type"], d["kept_name"]]) + "\n")
        for c, n, k, why in reported:
            f.write("\t".join(["report", c, n, str(k), "", "", why, ""]) + "\n")
        for c, k in nocountry:
            f.write("\t".join(["no ISO subdivisions", c, "", str(k), "", "", "", ""]) + "\n")


def main(argv=None):
    a = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    a.add_argument("--project", default=None)
    a.add_argument("--container", default="antweb_mysql_1")
    a.add_argument("--database", default="ant")
    a.add_argument("--apply", action="store_true")
    a.add_argument("--skip", default=None)
    a.add_argument("--folder", default=None)
    a.add_argument("--show", type=int, default=40, help="plan lines printed (all are in plan.tsv)")
    args = a.parse_args(argv)
    db = pf.Db(args.project or pf.default_project(), args.container, args.database)
    tag = datetime.date.today().strftime("%Y%m%d")
    folder = args.folder or os.path.join("/root/antweb-place-fix/iso", tag)
    os.makedirs(folder, exist_ok=True)
    skip = set()
    if args.skip:
        with open(args.skip, encoding="utf-8") as f:
            skip = {l.strip() for l in f if l.strip() and not l.startswith("#")}
    valid, olds, names = read_db(db)
    decisions, reported, nocountry = plan(valid, olds, read_iso(), read_countries(), skip)
    write_plan(os.path.join(folder, "plan.tsv"), decisions, reported, nocountry)
    ren = [d for d in decisions if d["action"] == "rename"]
    mer = [d for d in decisions if d["action"] == "merge"]
    print("iso_states %s, %s: %d valid states in %d countries" % (tag, db.project, sum(len(v) for v in valid.values()), len(valid)))
    print("  rename %d states (%d specimens), merge %d into another (%d specimens); %d reported, not changed; %d countries with no ISO subdivisions"
          % (len(ren), sum(d["specimens"] for d in ren), len(mer), sum(d["specimens"] for d in mer), len(reported), len(nocountry)))
    print("  plan: " + os.path.join(folder, "plan.tsv"))
    for d in (mer + ren)[:args.show]:
        print("  %-6s %-22s %-34s -> %-34s %-8s %5d" % (d["action"], d["country"][:22], d["name"][:34], d["iso"][:34], d["code"], d["specimens"]))
    if not args.apply:
        print("Plan only. Add --apply to make the change.")
        return 0
    bak, spec = "iso_states_bak_" + tag, "iso_states_spec_" + tag
    if pf.run_sql(db, "show tables like '%s'" % bak) or pf.run_sql(db, "show tables like '%s'" % spec):
        raise SystemExit("The backup table %s exists already: not applied twice today. Nothing was changed." % bak)
    sql, left = statements(decisions, names, tag)
    if left:
        print("Left out:\n  " + "\n  ".join(left))
    touched = sorted({d["id"] for d in decisions} | {x[0] for d in decisions for x in names.get((d["country"], d["iso"]), [])})
    pointing = " or ".join("(parent = %s and binary valid_name = binary %s)" % (q(d["country"]), q(d["name"])) for d in decisions) or "false"
    moving = " or ".join("(country = %s and binary adm1 = binary %s)" % (q(d["country"]), q(d["name"])) for d in decisions) or "false"
    new_adm1 = "case " + " ".join("when country = %s and binary adm1 = binary %s then %s" % (q(d["country"]), q(d["name"]), q(d["iso"])) for d in decisions) + " end"
    wait = "set session innodb_lock_wait_timeout = 600; set session transaction isolation level read committed;\n"
    pf.run_sql(db, wait + "create table %s as select id, name, parent, is_valid, valid_name, admin_notes from geolocale where georank = 'adm1' and (id in (%s) or %s);"
               % (bak, ",".join(map(str, touched)) or "0", pointing))
    pf.run_sql(db, wait + "create table %s as select code, country, adm1, %s as new_adm1 from specimen where %s;" % (spec, new_adm1, moving))
    print("backup: %s place rows in %s, %s specimens in %s" % (pf.run_sql(db, "select count(*) from " + bak)[0][0], bak,
                                                               pf.run_sql(db, "select count(*) from " + spec)[0][0], spec))
    pf.run_sql(db, wait + "start transaction;\n" + ";\n".join(sql) + ";\ncommit;")
    after_spec = pf.run_sql(db, "select count(*) from specimen where " + moving)[0][0]
    after_valid = pf.run_sql(db, "select count(*) from geolocale where georank = 'adm1' and is_valid = 1 and id in (%s) and name like '~%%'"
                             % (",".join(map(str, touched)) or "0"))[0][0]
    print("after: specimens under a former name %s (expect 0); valid rows with a temporary name %s (expect 0)" % (after_spec, after_valid))
    print("Now reload the places: /util.do?action=reloadAntwebMgr&name=geolocale")
    return 0


if __name__ == "__main__":
    sys.exit(main())
