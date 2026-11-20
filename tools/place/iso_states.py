#!/usr/bin/env python3
"""AntWeb's states under their ISO 3166-2 names (Brian Fisher, 2026-11-20: "let's adopt ISO 3166-2"; the states keep
changing, so this is run again about once a year with a new iso3166_2.tsv, see build_iso3166_2.py).

Each valid state (adm1) is matched to an ISO 3166-2 subdivision of its country: by its name, or by one of its old names
(rows that are not valid and lead to it), against the ISO name, its translations and the geoBoundaries shape names of
that subdivision, written alike (capitals, accents, blanks and punctuation aside, as GeolocaleMgr.adm1SpellingKey).
When none is written alike, a name with the generic words left out ("Kherson" for "Khersonska oblast") is tried, and
used only when it finds exactly one subdivision, no other state finds it too, and the state leaves out a generic word
of the ISO name (Banskobystrický, Banskobystrický kraj), never adds one ("Kiev Oblast" is not the city Kiev).
Specimens are matched by the exact name (a spelling with other capitals or accents stays; the plan counts those
written exactly).

  rename    one valid state matches the subdivision and is not written as ISO writes it: it takes the ISO name; the
            name it had becomes an old name of it (a row not valid, valid name the ISO name), so an upload that writes
            the old name is translated (GeolocaleMgr.adm1ToStore); its specimens get the ISO name. The row keeps its
            id, so its lists, counts, box and bioregion stay.
  merge     two or more valid states match the same subdivision by their written names (Aglona and Aglonas;
            Banskobystrický and Banskobystrický kraj): the one written as ISO, else the one with most specimens, is
            kept and takes the ISO name; the others become old names of it and their specimens move to it. The rows
            merged keep their id and their lists (geolocale_taxon is keyed by id); not valid, they are not shown, and
            the nightly counts the kept state again from its specimens. Places finer than a state (adm2) are not
            loaded by AntWeb (GeolocaleDb), so no row is left pointing at an old state name.
  alias     a country written in another script (ENGLISH: Egypt, Russia, Thailand ...): the state keeps the English
            name AntWeb uses; the ISO name (Al Qāhirah) is added as an old name of it. Of two states of such a country
            that are one (Cairo and Al Qāhirah), the English one is kept and the ISO-written one merged into it.
  report    the rest is listed and not changed: states no subdivision matches (Madagascar's regions: ISO still has
            the six provinces; French overseas arrondissements), a state that matches two subdivisions of different
            kinds equally, and a looser match that is not alone.

The plan also lists, not changed: lines of adm1_shape_map.csv that name a state renamed or merged (update them in the
same change, or the place report loses their shape), and curator species lists of states to be merged (such a merge
is left out by --apply: move the list by hand first, or skip the state).

Where a subdivision has two kinds (Spain: communities and provinces; Bangladesh: divisions and districts), a state
written like both takes the kind most of its country's states match.

  python3 iso_states.py                       plan only: the summary, and the plan in <folder>/plan.tsv
  python3 iso_states.py --apply               applies the plan reviewed today: it refuses when <folder>/plan.tsv is
                                              missing or the plan differs from it now (an upload in between); backup
                                              tables iso_states_bak_<date> (places), iso_states_spec_<date>
                                              (specimens) and iso_states_text_<date> (page text moved),
                                              then the changes; what was done in <folder>/applied.tsv
  python3 iso_states.py --skip FILE           lines "country|state" left alone (a state looked at by hand)
  python3 iso_states.py --project /root/antweb-next      staging (default: the project on this server)

Rollback (same server, the date of the run; one transaction; the rows added go first, so the old names are free again):
  start transaction;
  update specimen s join iso_states_spec_<date> b on b.code = s.code set s.adm1 = b.adm1 where binary s.adm1 = binary b.new_adm1;
  update specimen s join iso_states_bak_<date> b on b.parent = s.country and b.is_valid = 1 join geolocale g on g.id = b.id and binary g.name <> binary b.name left join iso_states_spec_<date> x on x.code = s.code set s.adm1 = b.name where binary s.adm1 = binary g.name and x.code is null;
  update object_edit o join iso_states_text_<date> b on b.tbl = 'object_edit' and binary o.object_key = binary b.new_key and o.title <=> b.title and o.created <=> b.created set o.object_key = b.old_key, o.created = o.created;
  update object_hist o join iso_states_text_<date> b on b.tbl = 'object_hist' and binary o.object_key = binary b.new_key and o.title <=> b.title and o.created <=> b.created set o.object_key = b.old_key, o.created = o.created;
  delete from geolocale where georank = 'adm1' and admin_notes like 'iso_states <date>: added%';
  update geolocale g join iso_states_bak_<date> b on b.id = g.id set g.name = concat('~rb~', g.id) where binary g.name <> binary b.name;
  update geolocale g join iso_states_bak_<date> b on b.id = g.id set g.name = b.name, g.is_valid = b.is_valid, g.valid_name = b.valid_name, g.admin_notes = b.admin_notes;
  commit;
then reload the places (util.do?action=reloadAntwebMgr&name=geolocale, administrator). The third line puts back
specimens uploaded after the apply under a renamed state's new name: those not in the backup, which also lists every
specimen already under a new name before the apply (left as they were). The fourth and fifth move the page text back
(a text written under an old name after the apply makes them fail: the transaction then changes nothing; move that
text aside first). A text edited after the apply (its date changes) stays under the new name, and two rows of one name
written in two ways in the same second may swap spellings: check the state's page after a rollback.

Page text: an overview page's text and its history (object_edit, object_hist) are keyed by the place's name, so a
rename moves them as editGeolocale.do does (PageTextKeys), and a merge moves the merged state's text to the state it
joins. A rename or merge whose new name already has page text is left out; text under a name another place also has
stays where it is (GeolocaleDb.updateGeolocale does the same)."""
import argparse
import collections
import csv
import datetime
import io
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


# Countries written in another script: ISO 3166-2 names their states by a transliteration (Egypt: Al Qāhirah for
# Cairo). Brian Fisher 2026-11-20: match every state to its ISO code, but keep the English name AntWeb uses for these
# countries; the ISO form becomes an old name of it (alias), so an upload in either form is filed under the state.
# Latin-script countries take ISO's name (Saaremaa, Ādažu novads, Şamaxı).
ENGLISH = set("""AE AF AM BG BH BY CN CY EG ER GE GR IL IQ IR JO JP KG KH KP KR KW KZ LA LB LK LY MK MM MN OM PS QA RS RU SA
SD SY TH TJ TW UA YE IN""".split())

# Countries whose ISO 3166-2 subdivisions are older than the ones in use: their states are reported, not changed.
OUTDATED = {"CF": "ISO 3166-2 has the prefectures before the 2020 reform (Gribingui for Nana-Grébizi)",
            "MG": "ISO 3166-2 has the six provinces; Madagascar has had regions since 2004"}


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
    m = INVERT.match(n)                  # first: one inverted form ends in a bracket, "..., Unitatea teritorială autonomă (UTAG)"
    if m:
        n = m.group(2) + " " + m.group(1)
        n = n[0].upper() + n[1:]
    # a note in brackets is not part of the name: "Western Visayas (Region VI)", "Maritime (Région)",
    # "Guelmim-Oued Noun (EH-partial)", "National Capital District (Port Moresby)"
    return re.sub(r"\s*\([^()]*\)\s*$", "", n).strip()


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


def iso_names(sub):
    """ISO's own names of the subdivision (its name, AntWeb's form of it, the alternative in brackets)."""
    return [sub.name, display(sub.name)] + re.findall(r"\[(.*?)\]", sub.name)


def sub_names(sub):
    return iso_names(sub) + sub.others


def shorter(name, sub):
    """The state's name is one of the subdivision's names with generic words left out, none added."""
    words = set(key(name).split())
    return any(loose(n) == loose(name) and words <= set(key(n).split()) for n in sub_names(sub))


def match_country(states, subs, english=False):
    """states: the valid states of one country (State); subs: its ISO subdivisions. Returns (decisions, reported):
    decisions [(action, state, sub, kept_state)] with action 'rename', 'merge' (state becomes an old name of
    kept_state, which takes sub's ISO name) or 'alias' (english: the state keeps its name, sub's ISO name is added as
    an old name of it); reported [(state, reason)]. english: a country written in another script (ENGLISH): no
    rename; of states that are one, the one not written as ISO (the English name) is kept."""
    by_code = {s.code: s for s in subs}
    own = collections.defaultdict(set)          # ISO's own names first: the other names (translations, the boundary
    exact = collections.defaultdict(set)        # file's shape names) are matched only when no ISO name is. The
    looser = collections.defaultdict(set)       # boundary file has codes on the wrong shapes (its Distrito Federal
    for s in subs:                              # carries México state's code, its Niamey Tillabéri's)
        for n in iso_names(s):
            if key(n):
                own[key(n)].add(s.code)
        for n in sub_names(s):
            if key(n):
                exact[key(n)].add(s.code)
            if loose(n):
                looser[loose(n)].add(s.code)
    hits, how = {}, {}
    for st in states:
        h, other = set(), set()
        for n in [st.name] + list(st.old_names):
            h |= own.get(key(n), set())
            other |= exact.get(key(n), set())
        # an other name joins an ISO-name hit only when the state is that subdivision's ISO name with generic words left
        # out (Madrid: the province by name, Comunidad de Madrid by its English name; the kind decides below)
        words = set(key(st.name).split())
        h = (h | {c for c in other - h if any(words <= set(key(n).split()) for n in iso_names(by_code[c]))}) if h else other
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
            if how[st.id] == "loose" and any(code in hits[o.id] for o in states if o.id != st.id):
                reported.append((st, "loose match %s (%s) that another state finds too: looked at by hand" % (code, iso)))
            elif how[st.id] == "loose" and not shorter(st.name, sub):
                reported.append((st, "loose match %s (%s) adds a generic word: looked at by hand" % (code, iso)))
            elif st.name != iso and (english or "/" in iso):      # a slash in a name (Taita/Taveta, //Karas) is kept out of URLs
                if key(iso) not in {key(n) for n in st.old_names}:
                    decisions.append(("alias", st, sub, st))
            elif st.name != iso:
                decisions.append(("rename", st, sub, st))
            continue
        # a looser match joins a merge (which moves specimens) only when the state leaves out a generic word of the ISO
        # name ("Banskobystrický" for "Banskobystrický kraj"), never when it adds one: "Kiev Oblast" is not the city Kiev
        if any(how[st.id] == "loose" and not shorter(st.name, sub) for st in group):
            for st in group:
                reported.append((st, "with %s matches %s (%s): looked at by hand" % (", ".join(o.name for o in group if o is not st), code, iso)))
            continue
        if english or "/" in iso:
            kept = max([st for st in group if key(st.name) != key(iso) and "/" not in st.name] or group, key=lambda st: (st.specimens, -st.id))
        else:
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
        if a2 in OUTDATED:
            reported += [(country, n, c, OUTDATED[a2]) for i, n, c in valid[country] if (country + "|" + n) not in skip]
            continue
        english = a2 in ENGLISH
        decisions, rep = match_country(rows, subs, english)
        for action, st, sub, kept in decisions:
            # target: the name the state, its old names and its specimens end up under
            target = kept.name if english or "/" in display(sub.name) else display(sub.name)
            out.append({"country": country, "action": action, "id": st.id, "name": st.name, "specimens": st.specimens, "code": sub.code,
                        "type": sub.type, "iso": display(sub.name), "kept_id": kept.id, "kept_name": kept.name, "target": target})
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


def read_texts(db):
    """(page text keys: {table: {object_key as stored: rows}} for object_edit and object_hist, how many geolocale rows
    have each name exactly, any rank and parent)."""
    texts = {}
    for t in ("object_edit", "object_hist"):
        texts[t] = collections.Counter()
        for r in pf.run_sql(db, "select " + pf.hx("object_key") + ", count(*) from %s group by 1" % t):
            texts[t][pf.unhex(r[0])] += int(r[1])
    allnames = collections.Counter()          # by normal(name): page text keys ignore accents and capitals
    for r in pf.run_sql(db, "select " + pf.hx("name") + ", count(*) from geolocale group by 1"):
        allnames[normal(pf.unhex(r[0]))] += int(r[1])
    return texts, allnames


SPECIMEN_ADM1 = 128          # specimen.adm1 is varchar(128): a longer name cannot be stored on a specimen


def q(value):
    return "convert(unhex('%s') using utf8mb4)" % (value or "").encode("utf-8").hex()


def statements(decisions, names, tag, no_merge=None, texts=None, allnames=None, text_moves=None):
    """The SQL of the change to the place rows, the list of what could not be done (left out), and the decisions
    done. Renames first, then merges; a merge into a state whose rename was left out is left out too (no valid state
    would carry the ISO name), and so is one in no_merge (ids: a curator species list on the state merged). The
    specimens are moved by main, from the backup table (moved_specimens)."""
    sql, left, done = [], [], []
    blocked, targets = set(), set()
    no_merge = no_merge or {}
    texts, allnames = texts or {}, allnames or {}
    if texts and not isinstance(next(iter(texts.values())), dict):      # a plain {key: rows}: object_edit only
        texts = {"object_edit": texts}
    text_moves = text_moves if text_moves is not None else []
    # object_edit's key compares without accents or capitals (its unique key): page text names are compared the same
    # way; a name with rows in object_edit or object_hist has page text (a conflict). The move updates the keys as
    # stored (stored[normal] -> {(table, key)}), so a text written "bratislava" moves with Bratislava.
    stored = collections.defaultdict(set)
    for t, keys in texts.items():
        for k, v in keys.items():
            if v:
                stored[normal(k)].add((t, k))
    has_text = set(stored)

    def shared(name):
        """How many places have this name for page text (accents and capitals aside)."""
        return max(allnames.get(normal(name), 0), allnames.get(name, 0))

    def move_text(old, new):
        """Page text of old to new (when old has text and no other place has that name): SQL, recorded for the rollback."""
        if normal(old) == normal(new):        # one key to object_edit already
            return []
        if normal(old) in has_text and shared(old) <= 1:
            out = []
            for t, k in sorted(stored.pop(normal(old))):
                text_moves.append((t, k, new))
                # the row recorded for the rollback, then moved (in this order, so a key moved twice is recorded as it is then)
                out.append("insert into {text} select '%s', object_key, %s, title, created from %s where binary object_key = binary %s" % (t, q(new), t, q(k)))
                out.append("update %s set object_key = %s, created = created where binary object_key = binary %s" % (t, q(new), q(k)))
            stored[normal(new)] |= {(t, new) for t, k, n in text_moves if n == new}
            has_text.discard(normal(old))
            has_text.add(normal(new))
            return out
        return []
    for d in decisions:
        if d["action"] != "rename":
            continue
        c, old, iso, vid = d["country"], d["name"], d["iso"], d["id"]
        other = [x for x in names.get((c, iso), []) if x[0] != vid]
        swap = [x for x in other if x[1] != 1 and x[2] in (old, "")]        # at most one: the name is unique in a country
        why = None
        if any(x[1] == 1 for x in other):          # match_country keeps the state written as ISO, so this is another state
            why = "ISO name %s is another valid state" % iso
        elif any(x[1] != 1 and x[2] not in (old, "") for x in other):
            why = "a row named %s is an old name of another state" % iso
        elif any(m["action"] == "merge" and m["kept_id"] == vid and m["id"] in no_merge for m in decisions):
            why = "a state to be merged into it has a curator species list (%s)" % ", ".join(
                m["name"] for m in decisions if m["action"] == "merge" and m["kept_id"] == vid and m["id"] in no_merge)
        elif len(iso) > SPECIMEN_ADM1:
            why = "the ISO name is longer than a specimen's state can be (%d characters)" % SPECIMEN_ADM1
        elif normal(iso) in has_text and normal(iso) != normal(old):
            why = "the ISO name %s already has page text (or a name written like it)" % iso
        elif normal(old) in has_text and shared(iso) > len(swap) + sum(1 for m in decisions if m["action"] == "merge" and m["kept_id"] == vid
                                                                          and normal(m["name"]) == normal(iso)):
            # this state's own old-name row of that name, and states of that spelling merged into it, aside
            # page text is keyed by name alone: under a name another place has, it would show on that place's page too
            why = "it has page text, and another place is already named %s" % iso
        elif (c, iso) in targets:
            why = "another state of %s is renamed %s too" % (c, iso)
        if why:
            left.append("%s: %s, %s" % (c, old, why))
            blocked.add(vid)
            continue
        done.append(d)
        targets.add((c, iso))
        note = "iso_states %s: named %s, ISO 3166-2 %s; was %s." % (tag, iso, d["code"], old)
        if swap:
            sql.append("update geolocale set name = %s where id = %d" % (q("~iso~%d" % swap[0][0]), swap[0][0]))
        sql.append("update geolocale set name = %s, admin_notes = concat(ifnull(concat(admin_notes, '\\n'), ''), %s) where id = %d" % (q(iso), q(note), vid))
        if swap:
            sql.append("update geolocale set name = %s, is_valid = 0, valid_name = %s, admin_notes = concat(ifnull(concat(admin_notes, '\\n'), ''), %s) where id = %d"
                       % (q(old), q(iso), q("iso_states %s: old name of %s (id %d)." % (tag, iso, vid)), swap[0][0]))
        else:
            sql.append("insert into geolocale (name, georank, parent, source, is_valid, valid_name, admin_notes) values (%s, 'adm1', %s, 'antweb', 0, %s, %s)"
                       % (q(old), q(c), q(iso), q("iso_states %s: added, old name of %s (ISO 3166-2 %s), id %d." % (tag, iso, d["code"], vid))))
        sql.append("update geolocale set valid_name = %s where georank = 'adm1' and parent = %s and binary valid_name = binary %s" % (q(iso), q(c), q(old)))
        sql += move_text(old, iso)
    for d in decisions:
        if d["action"] != "alias":
            continue
        c, name, iso = d["country"], d["name"], d["iso"]
        if names.get((c, iso)):
            left.append("%s: %s, a row named %s exists already (ISO name not added)" % (c, name, iso))
            continue
        done.append(d)
        sql.append("insert into geolocale (name, georank, parent, source, is_valid, valid_name, admin_notes) values (%s, 'adm1', %s, 'antweb', 0, %s, %s)"
                   % (q(iso), q(c), q(name), q("iso_states %s: added, ISO 3166-2 name (%s) of %s, id %d." % (tag, d["code"], name, d["id"]))))
    for d in decisions:
        if d["action"] != "merge":
            continue
        c, old, iso = d["country"], d["name"], d["target"]
        if d["kept_id"] in blocked:
            left.append("%s: %s, not merged into %s: that state's rename was left out" % (c, old, d["kept_name"]))
            continue
        if d["id"] in no_merge:
            left.append("%s: %s, not merged into %s: it has a curator species list of %d taxa" % (c, old, iso, no_merge[d["id"]]))
            continue
        if normal(old) != normal(iso) and normal(old) in has_text and shared(old) <= 1 and normal(iso) in has_text:
            left.append("%s: %s, not merged into %s: both have page text" % (c, old, iso))
            continue
        done.append(d)
        sql += move_text(old, iso)
        note = "iso_states %s: the same state as %s (ISO 3166-2 %s, id %d); made an old name of it, its specimens moved there." % (tag, iso, d["code"], d["kept_id"])
        sql.append("update geolocale set is_valid = 0, valid_name = %s, admin_notes = concat(ifnull(concat(admin_notes, '\\n'), ''), %s) where id = %d"
                   % (q(iso), q(note), d["id"]))
        sql.append("update geolocale set valid_name = %s where georank = 'adm1' and parent = %s and binary valid_name = binary %s" % (q(iso), q(c), q(old)))
    return sql, left, done


def moved_specimens(spec):
    """The specimens moved: those in the backup table that still have the state the backup read. A specimen an upload
    loaded after the backup keeps what it has (it is not in the backup, so a rollback could not put it back); the
    after line counts it, and a second run moves it (review of 4054e85)."""
    return "update specimen s join %s b on b.code = s.code set s.adm1 = b.new_adm1 where binary s.adm1 = binary b.adm1" % spec


def curator_lists(db, decisions):
    """{id: taxa} of the states to be merged that have a curator species list (geolocale_taxon, source curator)."""
    ids = sorted({d["id"] for d in decisions if d["action"] == "merge"})
    if not ids:
        return {}
    return {int(r[0]): int(r[1]) for r in pf.run_sql(db, "select geolocale_id, count(*) from geolocale_taxon where source = 'curator' and geolocale_id in (%s) group by 1"
                                                       % ",".join(map(str, ids)))}


def mapping_lines(decisions, path=None):
    """Lines of adm1_shape_map.csv that name a state renamed or merged in its country: [(line number, line)]."""
    path = path or os.path.join(HERE, "adm1_shape_map.csv")
    names = collections.defaultdict(set)
    for d in decisions:
        names[d["country"]].add(d["name"])
    out = []
    if not os.path.exists(path):
        return out
    with open(path, encoding="utf-8") as f:
        for i, line in enumerate(f, 1):
            if line.lstrip().startswith("#") or not line.strip():
                continue
            cells = [c.strip() for c in next(csv.reader([line]))]
            if any(c in names for c in cells) and any(x in names[c] for c in cells if c in names for x in cells):
                out.append((i, line.rstrip("\n")))
    return out


def mapping_update(decisions, path=None):
    """adm1_shape_map.csv with the states renamed or merged by the decisions under their new names: (text, changed
    lines [(line number, old, new or None when dropped)], lines to check by hand). A name line, or a plain line, whose
    shape name is now written like the state's new name is dropped (the place report finds it by name). Other kinds of
    line (within, split, level, source) are only listed."""
    path = path or os.path.join(HERE, "adm1_shape_map.csv")
    new = {(d["country"], d["name"]): d["target"] for d in moves(decisions)}
    out, changed, check = [], [], []
    if not os.path.exists(path):
        return "", changed, check
    with open(path, encoding="utf-8", newline="") as f:
        lines = f.read().split("\n")
    for i, line in enumerate(lines, 1):
        if not line.strip() or line.lstrip().startswith("#"):
            out.append(line)
            continue
        cells = [c.strip() for c in next(csv.reader([line]))]
        kind = cells[0].lower()
        country = cells[1] if kind in ("name", "convert", "within", "split", "level", "source") else cells[0]
        if not any((country, c) in new for c in cells):
            out.append(line)
            continue
        cells2, drop = list(cells), False
        if kind == "name" and len(cells) >= 5 and (country, cells[4]) in new:
            cells2[4] = new[(country, cells[4])]
            drop = key(cells2[4]) == key(cells[3])
        elif kind == "convert":
            cells2 = cells[:2] + [new.get((country, c), c) for c in cells[2:]]
        elif kind not in ("name", "convert", "within", "split", "level", "source") and len(cells) >= 4 and (country, cells[3]) in new:
            cells2[3] = new[(country, cells[3])]
            drop = key(cells2[3]) == key(cells[2]) and not (len(cells) > 4 and cells[4].lower() == "new")
        else:
            check.append((i, line))
            out.append(line)
            continue
        if drop:
            changed.append((i, line, None))
            continue
        buf = io.StringIO()
        csv.writer(buf, lineterminator="").writerow(cells2)
        out.append(buf.getvalue())
        changed.append((i, line, buf.getvalue()))
    return "\n".join(out), changed, check


def decision_lines(decisions):
    return ["\t".join([d["action"], d["country"], d["name"], d["iso"], d["code"], d["kept_name"]]) for d in decisions]


def moves(decisions):
    """The decisions that move specimens (renames and merges; an alias moves none)."""
    return [d for d in decisions if d["action"] in ("rename", "merge")]


def reviewed_lines(path):
    """The decisions of a plan.tsv written earlier, in decision_lines' form."""
    out = []
    with open(path, encoding="utf-8") as f:
        next(f, None)
        for line in f:
            p = line.rstrip("\n").split("\t")
            if p[0] in ("rename", "merge", "alias") and len(p) >= 8:
                out.append("\t".join([p[0], p[1], p[2], p[4], p[5], p[7]]))
            elif p[0] == "left out" and len(p) >= 7:
                out.append("left out\t" + p[6])
    return out


def write_plan(path, decisions, reported, nocountry, curator=None, lines=(), left=()):
    with open(path, "w", encoding="utf-8") as f:
        f.write("action\tcountry\tstate\tspecimens\tiso_name\tcode\ttype\tkept\n")
        for why in left:
            f.write("\t".join(["left out", "", "", "", "", "", why.replace("\t", " "), ""]) + "\n")
        for d in decisions:
            f.write("\t".join([d["action"], d["country"], d["name"], str(d["specimens"]), d["iso"], d["code"], d["type"], d["kept_name"]]) + "\n")
        for c, n, k, why in reported:
            f.write("\t".join(["report", c, n, str(k), "", "", why, ""]) + "\n")
        for c, k in nocountry:
            f.write("\t".join(["no ISO subdivisions", c, "", str(k), "", "", "", ""]) + "\n")
        for d in decisions:
            if d["id"] in (curator or {}):
                f.write("\t".join(["curator list", d["country"], d["name"], str(curator[d["id"]]), d["iso"], d["code"], "taxa on its curator species list", d["kept_name"]]) + "\n")
        for n, line in lines:
            f.write("\t".join(["mapping line", "", "", "", "", "", "adm1_shape_map.csv line %d: %s" % (n, line.replace("\t", " ")), ""]) + "\n")


def main(argv=None):
    a = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    a.add_argument("--project", default=None)
    a.add_argument("--container", default="antweb_mysql_1")
    a.add_argument("--database", default="ant")
    a.add_argument("--apply", action="store_true")
    a.add_argument("--skip", default=None)
    a.add_argument("--folder", default=None)
    a.add_argument("--show", type=int, default=40, help="plan lines printed (all are in plan.tsv)")
    a.add_argument("--mapping", action="store_true", help="print the change to adm1_shape_map.csv the plan needs (and write it to <folder>)")
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
    curator = curator_lists(db, decisions)
    texts, allnames = read_texts(db)
    text_moves = []
    sql, left, done = statements(decisions, names, tag, curator, texts, allnames, text_moves)
    lines = mapping_lines(moves(done))         # the mapping follows what the apply does, not the plan (review of #395)
    plan_file = os.path.join(folder, "plan.tsv")
    if args.apply:
        if not os.path.exists(plan_file):
            raise SystemExit("No plan in %s: run without --apply first and review it. Nothing was changed." % plan_file)
        if sorted(reviewed_lines(plan_file)) != sorted(decision_lines(decisions) + ["left out\t" + w.replace("\t", " ") for w in left]):
            raise SystemExit("The plan differs from the one reviewed in %s (the data changed since): run without --apply again and "
                             "review it. Nothing was changed." % plan_file)
    else:
        write_plan(plan_file, decisions, reported, nocountry, curator, lines, left)
    ren = [d for d in decisions if d["action"] == "rename"]
    mer = [d for d in decisions if d["action"] == "merge"]
    ali = [d for d in decisions if d["action"] == "alias"]
    print("iso_states %s, %s: %d valid states in %d countries" % (tag, db.project, sum(len(v) for v in valid.values()), len(valid)))
    print("  rename %d states (%d specimens), merge %d into another (%d specimens), add the ISO name as an old name of %d (English names kept);"
          " %d reported, not changed; %d countries with no ISO subdivisions"
          % (len(ren), sum(d["specimens"] for d in ren), len(mer), sum(d["specimens"] for d in mer), len(ali), len(reported), len(nocountry)))
    print("  plan: " + plan_file)
    if curator:
        print("  %d states to be merged have a curator species list: --apply leaves those merges out (plan.tsv, 'curator list')" % len(curator))
    if lines:
        print("  %d lines of adm1_shape_map.csv name a state renamed or merged: update them in the same change (plan.tsv, 'mapping line')" % len(lines))
    if left:
        print("  %d decisions the apply leaves out (%d page texts would move with the rest):\n    %s" % (len(left), len(text_moves), "\n    ".join(left)))
    for d in (mer + ren + ali)[:args.show]:
        print("  %-6s %-22s %-34s -> %-34s %-8s %5d" % (d["action"], d["country"][:22], d["name"][:34], (d["target"] if d["action"] == "merge" else d["iso"])[:34],
                                                         d["code"], d["specimens"]))
    if args.mapping and not os.path.exists(os.path.join(HERE, "adm1_shape_map.csv")):
        print("adm1_shape_map.csv not found next to this tool: no mapping change written")
    elif args.mapping:
        text, changed, check = mapping_update(done)
        with open(os.path.join(folder, "adm1_shape_map.csv"), "w", encoding="utf-8") as f:
            f.write(text)
        print("adm1_shape_map.csv: %d lines changed, %d dropped (now found by name), %d to check by hand; the new file: %s"
              % (sum(1 for c in changed if c[2]), sum(1 for c in changed if c[2] is None), len(check), os.path.join(folder, "adm1_shape_map.csv")))
        for n, old, new_line in changed:
            print("  %d - %s" % (n, old))
            print("  %d + %s" % (n, new_line if new_line is not None else "(dropped)"))
        for n, line in check:
            print("  %d ? %s" % (n, line))
    if not args.apply:
        print("Plan only. Add --apply to make the change.")
        return 0
    bak, spec, text = "iso_states_bak_" + tag, "iso_states_spec_" + tag, "iso_states_text_" + tag
    if int(pf.run_sql(db, "select count(*) from information_schema.tables where table_schema = database() and table_name in ('%s', '%s', '%s')"
                      % (bak, spec, text))[0][0]):
        raise SystemExit("The backup table %s exists already: not applied twice today. Nothing was changed." % bak)
    # the renames rely on geolocale.name comparing exactly (utf8_bin since 2016, db/upgrade/6.10): under a collation
    # that ignores accents, Bratislavsky kraj and Bratislavský kraj would be one name to the unique key
    coll = pf.run_sql(db, "select collation_name from information_schema.columns where table_schema = database() and table_name = 'geolocale'"
                          " and column_name = 'name'")
    if not coll or not coll[0][0].endswith("_bin"):
        raise SystemExit("geolocale.name has the collation %s, not a binary one: this tool cannot rename safely. Nothing was changed."
                         % (coll[0][0] if coll else "(unknown)"))
    decisions = done
    if not decisions:
        print("Nothing to apply.")
        return 0
    with open(os.path.join(folder, "applied.tsv"), "w", encoding="utf-8") as f:
        f.write("\n".join(decision_lines(decisions)) + "\n")
    touched = sorted({d["id"] for d in decisions} | {x[0] for d in decisions for x in names.get((d["country"], d["iso"]), [])})
    pointing = " or ".join("(parent = %s and binary valid_name = binary %s)" % (q(d["country"]), q(d["name"])) for d in moves(decisions)) or "false"
    moving = " or ".join("(country = %s and binary adm1 = binary %s)" % (q(d["country"]), q(d["name"])) for d in moves(decisions)) or "false"
    new_adm1 = ("case " + " ".join("when country = %s and binary adm1 = binary %s then %s" % (q(d["country"]), q(d["name"]), q(d["target"]))
                                   for d in moves(decisions)) + " end") if moves(decisions) else "adm1"
    wait = "set session innodb_lock_wait_timeout = 600; set session transaction isolation level read committed;\n"
    # the backup tables are made empty first (CREATE TABLE commits), then filled in the transaction of the change, so
    # the backup is exactly what the change reads; the specimens move from the backup by code (the primary key)
    # both built from the tables' own columns (same character sets and collations, so the joins by code use the key)
    # each page text row moved is recorded (its table, key as stored, new key, title, date) just before its move, so the
    # rollback puts back each row's own key, also when two spellings of one name moved together
    # the specimens already under a new name before the apply are listed too (new_adm1 = adm1: the change and the
    # rollback's first line leave them alone; its second line knows them as not uploaded after the apply)
    already = " or ".join("(country = %s and binary adm1 = binary %s)" % (q(d["country"]), q(d["target"])) for d in decisions if d["action"] == "rename") or "false"
    try:
        pf.run_sql(db, "create table %s as select id, name, parent, is_valid, valid_name, admin_notes from geolocale where false;"
                       "create table %s as select code, country, adm1, adm1 as new_adm1 from specimen where false;"
                       "alter table %s add primary key (code);"
                       "create table %s as select 'object_edit' as tbl, object_key as old_key, object_key as new_key, title, created"
                       " from object_edit where false" % (bak, spec, spec, text))
        pf.run_sql(db, wait + "start transaction;\n"
                   + "insert into %s select id, name, parent, is_valid, valid_name, admin_notes from geolocale where georank = 'adm1' and (id in (%s) or %s);\n"
                   % (bak, ",".join(map(str, touched)) or "0", pointing)
                   + "insert into %s select code, country, adm1, %s from specimen where %s;\n" % (spec, new_adm1, moving)
                   + "insert ignore into %s select code, country, adm1, adm1 from specimen where %s;\n" % (spec, already)
                   + ";\n".join([x.replace("{text}", text) for x in sql] + [moved_specimens(spec)]) + ";\ncommit;")
    except pf.SqlFailed:
        # nothing was changed (one transaction); the empty backup tables go, so the run can be made again today
        made = [t for t in (bak, spec, text) if pf.run_sql(db, "select count(*) from information_schema.tables where table_schema = database()"
                                                               " and table_name = '%s'" % t)[0][0] != "0"]
        if sum(int(pf.run_sql(db, "select count(*) from " + t)[0][0]) for t in made) == 0:
            if made:
                pf.run_sql(db, ";".join("drop table " + t for t in made))
            print("The change failed and was not made; the empty backup tables were dropped.")
        else:
            print("The backup tables %s are filled: the change may have been committed after all (a lost connection)."
                  " Check with the after line's queries before anything else; the rollback in this file's header undoes it." % ", ".join(made))
        raise
    print("backup: %s place rows in %s, %s specimens in %s, %d page texts moved (%s)" % (
        pf.run_sql(db, "select count(*) from " + bak)[0][0], bak, pf.run_sql(db, "select count(*) from " + spec)[0][0], spec, len(text_moves), text))
    after_spec = pf.run_sql(db, "select count(*) from specimen where " + moving)[0][0]
    after_valid = pf.run_sql(db, "select count(*) from geolocale where georank = 'adm1' and is_valid = 1 and id in (%s) and name like '~%%'"
                             % (",".join(map(str, touched)) or "0"))[0][0]
    print("after: specimens under a former name %s (expect 0); valid rows with a temporary name %s (expect 0)" % (after_spec, after_valid))
    print("Now reload the places: /util.do?action=reloadAntwebMgr&name=geolocale")
    return 0


if __name__ == "__main__":
    sys.exit(main())
