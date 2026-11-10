#!/usr/bin/env python3
"""Place corrections: the country and state a specimen is missing, proposed from its coordinates (report only).

Why (Brian Fisher, 2026-11-10, on the upload report of the California Academy of Sciences: "we would need the
casent specimen code and the field and value for updating TW ... Country is missing 714 specimens; Adm1 is
missing, but expected for these countries, 1,500 specimens; Not AntWeb valid countries 2 ... currently the list
is so long they are ignored: some of course can not be corrected but maybe some can be based on lat long or
other means"). The specimens of the Academy come from TaxonWorks every day, so a correction is entered in
TaxonWorks, for each collecting event. This tool writes the working list: what to enter, for which collecting
event, and why; and for the rest, what the record has, grouped so that a person can work through it.

It never writes to the database: every statement is a single select (coordinate_country.read_only).

  python3 place_corrections.py --group-id 1                the California Academy of Sciences
  python3 place_corrections.py --group "Field Museum"      one upload group by its name
  python3 place_corrections.py                             every group
  python3 place_corrections.py --shapes-source naturalearth    the country test on Natural Earth alone

A second tool, not a mode of coordinate_country.py: the question is another one (which state, for the records
with a gap) and so are the lists. It loads coordinate_country.py, which must lie next to it, and uses its rule
for the country, its index, its reader of boundary files and its lock file.

What it proposes.

  A. State missing: a valid AntWeb country, no state, not an island-country specimen, the country has valid
     states in AntWeb, usable coordinates.
     1. The point must agree with the stored country by the rule of coordinate_country.py: inside it, or
        outside by no more than 1 km (--tolerance-km) or the specimen's stated error. Otherwise no proposal
        ("coordinates outside the stated country": it is in the conflicts report of that tool).
     2. The state shape that holds the point, from geoBoundaries gbOpen for the country's ISO code, at the
        level whose names fit AntWeb's states of that country (see below).
     3. A point inside one state shape gets that state, always. The nearest other state within 25 km and
        its distance are given with the proposal; when it lies within the allowed distance (the stated
        error, "1 minute" is 1.85 km, or 1 km) the proposal is flagged "within the stated error of <state>",
        for a person to look at first. (Version 1 gave no proposal there, "two candidates": 4,412 records
        of the first real run, most in small units such as the districts of Mauritius.)
        A point outside every state shape (in the water, or in a gap of the file) gets the state within the
        allowed distance when there is exactly one; with more than one: "two candidates", both named.
  B. Country missing: with usable coordinates, the country by the rule of coordinate_country.py (inside
     exactly one, or in the water within the allowed distance of exactly one), then the state as in A.
     Without coordinates: no proposal; the records are grouped in not_derivable.csv with the place text.
  C. Country not valid in AntWeb ("Africa"): listed with what the record has; with coordinates the country
     the point is in is proposed, and the state when it is missing.

Island countries (Brian Fisher, 2026-11-10: TaxonWorks is filled as AntWeb needs it). AntWeb reads Hawaii as
the state of a specimen of the United States, or as its country, and stores the country United States with the
island country Hawaii (SpecimenUploadParse). So for TaxonWorks the correction is country United States and
state Hawaii. A state shape whose name is a valid island country of AntWeb (geolocale: georank country,
is_island 1, is_valid 1) with the specimen's country as its parent (geolocale.country) is proposed as the state,
by the island's AntWeb name, the basis saying "AntWeb files this under <country> as the island <name>"; it is
not listed as "not a state in AntWeb", and the table of names counts island shapes apart. A missing country
in such an island gets two lines: the country (the parent) and the state (the island). A specimen that has
its island country already is left out, as before.

Which level of the boundary files holds AntWeb's states. Not assumed to be ADM1. For each country the shape
names of ADM1, then ADM2, ADM3, ADM4 are compared with AntWeb's valid states of that country (geolocale,
georank adm1, parent the country): capitals, accents and punctuation ignored; an old name with a valid name
counts as a spelling of the valid state; then without a word such as Province, Region, State, Department.
Two shares are counted at each level: AntWeb's states that a shape names, of all AntWeb's valid states of the
country; and the shapes that are an AntWeb state, of all shapes of the level.
  - The first level where both shares are at least half is used, and the finer ones are not looked at.
  - Else the level with the largest share of shapes that are AntWeb states (the coarser one of two alike). It
    is used when at least half of its shapes are AntWeb states, or, for the coarsest level only, when at
    least half of AntWeb's states are matched (AntWeb may have few of a country's states).
  - Else no level is used: the country gives no proposal, every record with coordinates in it is listed
    with the reason "AntWeb's states and the boundary file do not match: needs a mapping" and the shape it
    lies in, and the report names the country in its summary.
  Why not the level with the most states matched (version 1): the units of a finer level often have the
  names of the states. The first real run took the communes of Chile, the districts of the Czech Republic,
  the provinces of Spain. A finer level is therefore never used on the states matched alone.
  The report has one line for each country: the two shares at each level looked at, the level used, why.
  A name spoilt when the boundary file was made is put right before it is compared (mend): the file of
  Chile has "RegiÃ³n de Antofagasta" for "Región de Antofagasta", so no region of Chile matched.
  A point in a shape that is no AntWeb state gets no proposal ("the boundary file's <name> is not a state in
  AntWeb"); the report lists these names with their specimens: the list for the Geolocale Manager.
  tools/place/adm1_shape_map.csv (next to this tool) names the AntWeb state of a shape where the two differ:
  Madagascar, where AntWeb has the six provinces and the file the 22 regions; four states of Mexico under
  their long names; the District of Columbia. A row is used only when AntWeb has that state. Rows count as
  matches, so a mapping made for a country lifts it over the minimum.
  mapping_suggestions.csv helps to make such rows: for each shape that is no AntWeb state, the AntWeb valid
  states of the country whose name (or an old name of which) is most like it, once the words that say the kind
  of unit are left out (Province, Region, Governorate, Oblast, District, County, Department, State, City,
  Capital, Autonomous, Special ...), with a score and the specimens that lie in the shape. A person approves a
  line and pastes it into adm1_shape_map.csv. The tool uses nothing from its own suggestions.
  The light ("simplified") files are used for the states: measured 2026-11-10 for Madagascar, Mexico and the
  United States, their lines lie within 100 m of the full files' for 99 in 100 points, and they are 2 to 50
  times smaller (Mexico 4 MB, not 36 MB; Canada 26 MB, not 648 MB). The country test uses the full files.

Madagascar by region. AntWeb has the six provinces only because nobody knew how to convert to the regions
(Brian Fisher, 2026-11-10); with coordinates and shapes he would convert. The mapping is not changed yet. When
AntWeb's states of Madagascar are the provinces, the report has a table: for each region (shape of the ADM1
file) the specimens of the group with coordinates inside it, of every record of Madagascar and not only those
with a gap, and how many of them have the province the mapping file gives, another state, or none; and how
many records of Madagascar have no coordinates. One more read of the database, for Madagascar alone.

It writes one folder (default /root/antweb-place-fix/<date>-<time>-corrections/):

  corrections.csv          one line per specimen and field: specimen code, collecting event code, locality
                           code, field (country or adm1), current value, proposed value (AntWeb's valid
                           name), the basis in plain words, the nearest other state, the km to it, the flag
                           "within the stated error of <state>", latitude, longitude, stated error, locality
                           name, country, upload group. In the order country, state, collecting event.
  by_collecting_event.csv  the working list for TaxonWorks: one line per collecting event and field with the
                           proposed value, the number of specimens, the first and last specimen code, the
                           locality name and the coordinates. A collecting event whose specimens get
                           different proposals is flagged and not to be entered as it stands. A specimen
                           with no collecting event code has a line of its own. With the nearest other
                           state, the km to it and the flag, as in corrections.csv.
  not_derivable.csv        one line per collecting event (per specimen when it has no code), field and
                           reason: no coordinates; coordinates outside the stated country; two candidates;
                           the boundary file's name is not a state in AntWeb; AntWeb's states and the
                           boundary file do not match; no boundary file; country has no states in AntWeb.
                           With the number of specimens and the place text of the record (state, adm2,
                           locality name, locality notes, bioregion), so a person can decide.
  mapping_suggestions.csv  country, level, shape, antweb_state (the columns of adm1_shape_map.csv), then score,
                           specimens, note. See above. Nothing is used from it.
  antweb_states_without_shape.csv   the other side: AntWeb's valid states that no shape was matched to, for
                           each country, with the shape that is no state whose name is most like it.
  report.txt               first a summary of at most 60 lines: the totals for each field, the reasons, the
                           countries below the minimum. Then the tables: proposed values; for each country
                           the levels and the table of names; the mapping rows used; Madagascar by region;
                           the shape files and their licences.

The password is read from <project>/etc/db.properties and goes to docker through the environment, never on a
command line. Tests: tests/unit/test_place_corrections.py.
"""
import argparse
import collections
import csv
import datetime
import difflib
import importlib.util
import json
import math
import os
import re
import sys
import time
import unicodedata
import urllib.error

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("coordinate_country", os.path.join(HERE, "coordinate_country.py"))
cc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(cc)

VERSION = "3"
LEVELS = ["ADM1", "ADM2", "ADM3", "ADM4"]
MIN_SHARE = 0.5                  # the minimum for a level to be used: half of AntWeb's states matched, or half of its shapes
OTHER_KM = (5.0, 25.0)           # how far the nearest other state is looked for, in two steps
MIN_SCORE = 0.7                  # a suggestion of mapping_suggestions.csv is at least this like the shape's name
SUGGESTIONS = 3                  # at most this many suggestions for one shape
SIMPLIFIED = "_simplified"       # the light file of geoBoundaries: within 100 m of the full one for 99 in 100 points
MAP_FILE = os.path.join(HERE, "adm1_shape_map.csv")
UNIT_WORDS = {"province", "provincia", "provincie", "region", "regiao", "state", "estado", "department", "departamento", "departement",
              "district", "distrito", "county", "prefecture", "governorate", "oblast", "parish", "canton", "municipality", "territory",
              "of", "de", "del", "do", "da", "du", "the"}

# More words that say what kind of unit a name is. Left out when two names are compared for mapping_suggestions.csv
# only: a name that fits once these are left out is suggested to a person, never used by itself.
GENERIC_WORDS = UNIT_WORDS | {"city", "capital", "autonomous", "special", "federal", "metropolitan", "administrative", "area", "division",
                              "emirate", "republic", "kraj", "krai", "kray", "okrug", "voivodeship", "megye", "autonome", "autonoma", "autonomo",
                              "comunidad", "comunitat", "community", "principado", "foral", "national", "capitale", "ciudad", "d", "l"}
MADAGASCAR = "Madagascar"
PROVINCES = {"antananarivo", "antsiranana", "fianarantsoa", "mahajanga", "toamasina", "toliara"}     # the six provinces of Madagascar

NO_COORDINATES = "no coordinates"
OUTSIDE = "coordinates outside the stated country"
TWO = "two candidates"
NOT_A_STATE = "the boundary file's name is not a state in AntWeb"
NO_MATCH = "AntWeb's states and the boundary file do not match: needs a mapping"
NO_FILE = "no boundary file"
NO_STATES = "country has no states in AntWeb"
NO_SHAPE_HERE = "the boundary file draws no state at the point"
TERRITORY = "the point is in a territory AntWeb lists as a country of its own"
AT_SEA = "in the water, farther than the allowed distance from any country"
NO_COUNTRY_HERE = "the point is in an area with no AntWeb country"


# ---------------------------------------------------------------- AntWeb's states and the names of the shapes

def plain(name):
    """A name without capitals, accents or punctuation: Amoron'i Mania and amoron i mania are the same."""
    decomposed = unicodedata.normalize("NFKD", name or "")
    text = "".join(c for c in decomposed if not unicodedata.combining(c)).casefold()
    return " ".join(re.sub(r"[^0-9a-z]+", " ", text).split())


def loose(name):
    """The plain name without the words Province, Region, State ...: Fianarantsoa Province is Fianarantsoa."""
    words = [w for w in plain(name).split() if w not in UNIT_WORDS]
    return " ".join(words)


MOJIBAKE = re.compile("[\u00c2-\u00f4][\u0080-\u00bf\u0152\u0153\u0160\u0161\u0178\u017d\u017e\u0192\u02c6\u02dc\u2013-\u203a\u20ac\u2122]")
SHAPE_NAME = re.compile(rb'"shapeName"\s*:\s*"((?:[^"\\]|\\.)*)"')


def mend(name):
    """A name whose UTF-8 letters were read as Latin-1 when the boundary file was made, put right: the file of
    Chile has "RegiÃ³n de Antofagasta" for "Región de Antofagasta". Any other name comes back as it is."""
    if not MOJIBAKE.search(name):
        return name
    for codec in ("latin-1", "cp1252"):
        try:
            return name.encode(codec).decode("utf-8")
        except UnicodeError:
            pass
    return name


def shape_names(path):
    """The shape names of a boundary file, without reading its lines: enough to see how a level fits."""
    with open(path, "rb") as f:
        return [mend(json.loads(b'"' + m.group(1) + b'"')) for m in SHAPE_NAME.finditer(f.read())]


def generic(name):
    """The plain name without any word that says what kind of unit it is (Jakarta Special Capital Region is
    jakarta). A name made of such words alone (Distrito Federal) stays whole."""
    words = [w for w in plain(name).split() if w not in GENERIC_WORDS]
    return " ".join(words) or plain(name)


def variants(name):
    """The name and its parts: West Sepik (Sandaun) Province is also West Sepik and Sandaun, Cataluña/Catalunya
    also Cataluña and Catalunya."""
    out = [generic(name)]
    for part in re.split(r"[/()\[\]]", name):
        g = " ".join(w for w in plain(part).split() if w not in GENERIC_WORDS)       # a part that only says the kind of unit is none
        if g and g not in out:
            out.append(g)
    return out


def alike(a, b):
    """How alike two names are, 0 to 1, both already made generic. 1: the same. Else the larger of: the share of
    letters in the same order (difflib), and, when every word of one is a word of the other, 0.75 to 0.95 by
    how much of the longer name the shorter one is."""
    if not a or not b:
        return 0.0
    if a == b:
        return 1.0
    if a.replace(" ", "") == b.replace(" ", ""):
        return 0.98
    score = difflib.SequenceMatcher(None, a, b).ratio()
    wa, wb = set(a.split()), set(b.split())
    if wa <= wb or wb <= wa:
        score = max(score, 0.75 + 0.2 * min(len(a), len(b)) / max(len(a), len(b)))
    return score


def likeness(shape, name):
    """How alike a shape name and an AntWeb name are: the best of the shape name's parts."""
    b = generic(name)
    return round(max(alike(a, b) for a in variants(shape)), 2)


class States:
    """The states of geolocale (georank adm1): for each valid country its valid states and the names that mean one."""

    def __init__(self, rows, countries):
        """rows: (name, parent, is_valid, valid_name) of every adm1 row."""
        self.valid = collections.defaultdict(set)                # country -> valid state names
        self.by_plain = collections.defaultdict(dict)            # country -> plain name -> valid state
        self.by_loose = collections.defaultdict(lambda: collections.defaultdict(set))
        self.old = collections.defaultdict(lambda: collections.defaultdict(list))   # country -> valid state -> its old names
        old = []
        for name, parent, is_valid, valid_name in rows:
            country = countries.resolve(parent)
            if country is None or not name:
                continue
            if str(is_valid) == "1":
                self.valid[country.name].add(name)
            elif cc.clean(valid_name):
                old.append((country.name, name, cc.clean(valid_name)))
        for country, names in self.valid.items():
            for name in names:
                self.by_plain[country][plain(name)] = name
                self.by_loose[country][loose(name)].add(name)
        for country, name, valid_name in old:                    # an old name is a spelling of its valid state
            if valid_name in self.valid[country]:
                self.by_plain[country].setdefault(plain(name), valid_name)
                self.old[country][valid_name].append(name)

    def of(self, country):
        return sorted(self.valid.get(country, ()))

    def find(self, country, name):
        """(valid state, how) for a name, or (None, ""): the name itself, an old name, or without the unit word."""
        state = self.by_plain.get(country, {}).get(plain(name))
        if state is not None:
            return state, "name"
        states = self.by_loose.get(country, {}).get(loose(name), ()) if loose(name) else ()
        if len(states) == 1:
            return next(iter(states)), "name without the unit word"
        return None, ""

    def closest(self, country, shape, limit=SUGGESTIONS):
        """The valid states of the country most like a shape name: [(score, state, the old name that was like it
        or "")], best first, at least MIN_SCORE. For a person to look at: nothing is matched by this."""
        found = []
        for state in self.valid.get(country, ()):
            best = (likeness(shape, state), "")
            for name in self.old.get(country, {}).get(state, ()):
                score = likeness(shape, name)
                if score > best[0]:
                    best = (score, name)
            if best[0] >= MIN_SCORE:
                found.append((best[0], state, best[1]))
        return sorted(found, key=lambda x: (-x[0], x[1]))[:limit]


def read_map(path):
    """The rows of adm1_shape_map.csv: [(country, level, shape, AntWeb state)]. Notes (#) and the header are left out."""
    rows = []
    if not os.path.exists(path):
        return rows
    with open(path, encoding="utf-8", newline="") as f:
        for line in csv.reader(row for row in f if not row.lstrip().startswith("#")):
            if len(line) >= 4 and line[0].strip().lower() != "country":
                rows.append(tuple(cell.strip() for cell in line[:4]))
    return rows


ISLAND = "island country"


class Chosen:
    """The level of the boundary files of one country: its shapes, the AntWeb state of each, the index. usable:
    the level passed the minimum, a point in it may get a proposal."""

    def __init__(self, country, code, level, names, states, hows, index, usable=True):
        self.country, self.code, self.level, self.names, self.states, self.hows = country, code, level, names, states, hows
        self.index, self.usable = index, usable


At = collections.namedtuple("At", "state shape basis reason detail other other_km flag", defaults=("", "", "", "", "", ""))


def share(part, whole):
    return part / whole if whole else 0.0


def pick_level(tried):
    """Which level of a country is used: (its entry in tried, usable, why). tried: for each level looked at,
    coarse to fine, a dict with shapes (None: no file), shape_hits (shapes that are an AntWeb state), states
    (AntWeb's valid states of the country) and state_hits (those of them a shape names).

    The first level where at least half of AntWeb's states are matched and at least half of the shapes are
    AntWeb states is taken as it stands. Else the level with the largest share of shapes that are AntWeb states
    (the coarser one of two alike). It is used when at least half of its shapes are AntWeb states (AntWeb's
    list has more names than the states); or, for the coarsest level only, when at least half of AntWeb's
    states are matched (AntWeb has few of the country's states). A finer level is not used on the states
    matched alone: its units often have the names of the states they lie in. That is how version 1, which
    counted the states matched, took the communes of Chile for its regions, the districts of the Czech
    Republic for its regions, the provinces of Spain for its communities.

    When no level is used, the entry given is the level whose number of shapes is nearest to the number of
    AntWeb's states (the coarser one of two alike): the level a mapping would most likely be made for. Its
    shapes say where a record lies, and mapping_suggestions.csv is written for it."""
    files = [t for t in tried if t["shapes"]]
    if not files:
        return None, False, "no boundary file"
    for t in files:
        if share(t["state_hits"], t["states"]) >= MIN_SHARE and share(t["shape_hits"], t["shapes"]) >= MIN_SHARE:
            return t, True, "at least half of AntWeb's states are matched and at least half of the shapes are AntWeb states"
    best = max(files, key=lambda t: share(t["shape_hits"], t["shapes"]))            # the first of equals: the coarser
    start = "the level with the largest share of shapes that are AntWeb states"
    if share(best["shape_hits"], best["shapes"]) >= MIN_SHARE:
        return best, True, start + "; at least half of its shapes are AntWeb states"
    if best is files[0] and share(best["state_hits"], best["states"]) >= MIN_SHARE:
        return best, True, start + ", and the coarsest; at least half of AntWeb's states are matched"
    nearest = min(files, key=lambda t: abs(math.log(t["shapes"] / max(t["states"], 1))))
    return nearest, False, ("below the minimum: at the best level, " + best["level"] + ", " + cc.n(best["shape_hits"]) + " of " + cc.n(best["shapes"])
                            + " shapes are AntWeb states and " + cc.n(best["state_hits"]) + " of " + cc.n(best["states"]) + " AntWeb states are matched")


def level_text(t):
    if not t["shapes"]:
        return t["level"] + " no file"
    n = cc.n
    return (t["level"] + " " + n(t["state_hits"]) + "/" + n(t["states"]) + " AntWeb states, " + n(t["shape_hits"]) + "/" + n(t["shapes"]) + " shapes"
            + (", " + n(t["islands"]) + (" of them an island country" if t["islands"] == 1 else " of them island countries") + " of AntWeb" if t["islands"] else ""))


class StateShapes:
    """For each country: the level of geoBoundaries whose names fit AntWeb's states, and the state of a point."""

    def __init__(self, detail, states, countries, shape_map):
        self.detail, self.states, self.countries = detail, states, countries
        self.map = collections.defaultdict(list)                 # (country, level, plain shape) -> AntWeb states, in order
        self.map_rows = shape_map
        for country, level, shape, state in shape_map:
            c = countries.resolve(country)
            self.map[(c.name if c is not None else country, level.upper(), plain(shape))].append(state)
        self.rows_used = set()                                   # (country, level, shape, state) of the rows that named a shape
        self.level_of = {}                                       # country -> the level chosen, once found
        self.tables = {}                                         # country -> what the report prints
        self.problems = {}                                       # country -> why it has no state shapes
        self.files = {}                                          # lock key -> licence, for the report
        self._chosen = collections.OrderedDict()                 # country -> Chosen, the two used last

    def island(self, country, name):
        """The AntWeb name of the valid island country a shape name means, when the island's parent country is
        country (Hawaii for the United States); None otherwise."""
        place = self.countries.resolve(name)
        if place is None or not place.is_island or not place.parent:
            return None
        parent = self.countries.resolve(place.parent)
        return place.name if parent is not None and parent.name == country else None

    @staticmethod
    def states_matched(wanted, states, hows):
        """The AntWeb states the shapes of a level name. An island shape is counted apart; it counts here only
        when AntWeb has the island as a state of the country as well (Hawaii of the United States)."""
        return {s for s, h in zip(states, hows) if s and (h != ISLAND or s in wanted)}

    def match(self, country, level, names):
        """What to enter as the state for each shape name (None for none) and how it was found: by the mapping
        file, as an island country of AntWeb, by the name of a state."""
        states, hows = [], []
        for name in names:
            state, how = None, ""
            for wanted in self.map.get((country, level, plain(name)), ()):
                found, _ = self.states.find(country, wanted)
                if found is not None:
                    state, how = found, "mapping file"
                    break
            if state is None:
                island = self.island(country, name)
                if island is not None:
                    state, how = island, ISLAND
            if state is None:
                state, how = self.states.find(country, name)
            states.append(state)
            hows.append(how)
        return states, hows

    def look(self, country, code):
        """How each level fits, coarse to fine, until one fits by both halves: the list pick_level reads.
        Only the names of a level are read here."""
        wanted = set(self.states.of(country))
        tried = []
        for level in LEVELS:
            path = self.detail.file(code, level, SIMPLIFIED)
            if path is None:
                tried.append({"level": level, "shapes": 0})
                continue
            names = shape_names(path)
            states, hows = self.match(country, level, names)
            tried.append({"level": level, "shapes": len(names), "shape_hits": sum(1 for x in states if x), "states": len(wanted),
                          "state_hits": len(self.states_matched(wanted, states, hows)), "islands": sum(1 for h in hows if h == ISLAND)})
            t = tried[-1]
            if share(t["state_hits"], t["states"]) >= MIN_SHARE and share(t["shape_hits"], t["shapes"]) >= MIN_SHARE:
                break
        return tried

    def choose(self, country, code):
        """The Chosen level of a country, or None (self.problems says why). A level below the minimum is given
        too, marked not usable: its shapes say where a point is, for the mapping a person has to make."""
        if country in self._chosen:
            self._chosen.move_to_end(country)
            return self._chosen[country]
        if country in self.problems:
            return None
        if not code:
            self.problems[country] = "AntWeb has no ISO code for the country and no Natural Earth shape gives one"
            return None
        try:
            if country not in self.tables:                       # the first time: which level, and what the report says of it
                tried = self.look(country, code)
                picked, usable, why = pick_level(tried)
                if picked is None:
                    self.problems[country] = "geoBoundaries has no file of the divisions of " + code
                    return None
                self.tables[country] = {"code": code, "level": picked["level"], "tried": tried, "usable": usable, "why": why}
            t = self.tables[country]
            level = t["level"]
            features, _, _ = cc.read_features(self.detail.file(code, level, SIMPLIFIED))
        except (urllib.error.URLError, OSError) as e:
            self.problems[country] = "a boundary file of " + code + " could not be fetched (" + str(getattr(e, "reason", e)) + ")"
            return None
        features = [(mend(name), rings) for name, rings in features if rings]
        names = [name for name, _ in features]
        states, hows = self.match(country, level, names)
        chosen = Chosen(country, code, level, names, states, hows, cc.index_of_shapes([rings for _, rings in features]), t["usable"])
        if "matched" not in t:
            wanted = self.states.of(country)
            entry = self.detail.lock["files"].get(code + " " + level + SIMPLIFIED, {})
            self.files[code + " " + level + SIMPLIFIED] = entry
            for name, state, how in zip(names, states, hows):
                if how == "mapping file":
                    self.rows_used.add((country, level, name, state))
            got = self.states_matched(wanted, states, hows)
            shape_of = {}
            for name, state in zip(names, states):
                if state:
                    shape_of.setdefault(state, name)
            t.update({
                "matched": sorted(got), "not_matched": [x for x in wanted if x not in got], "shape_of": shape_of,
                "shapes_not_states": sorted({n for n, x in zip(names, states) if not x}),
                "islands": sorted(n + (" (" + x + ")" if x != n else "") for n, x, h in zip(names, states, hows) if h == ISLAND),
                "other_way": sorted(n + " -> " + x + " (" + h + ")" for n, x, h in zip(names, states, hows) if x and h not in ("name", ISLAND)),
                "licence": entry.get("licence", ""), "source": entry.get("source", "")})
        self._chosen[country] = chosen
        while len(self._chosen) > 2:
            self._chosen.popitem(last=False)
        return chosen

    def state_at(self, chosen, lat, lon, allowed, tolerance=None):
        """What the shapes say of a point: an At. With a state and a basis when the point is inside one state
        shape, or outside every shape and within the allowed distance of one; else the reason why there is no
        proposal, with the names a person needs. A point inside one state always gets it: another state within
        the allowed distance is named (other, other_km) and flagged, for a person to look at first. "Two
        candidates" is left for a point outside every shape and within the allowed distance of more than one,
        and for the rare point that two shapes of the file both hold."""
        label = lambda n: chosen.states[n] or chosen.names[n] + " (not a state in AntWeb)"
        inside = chosen.index.inside(lat, lon)
        near = chosen.index.near(lat, lon, allowed)
        if inside:
            labels = sorted({label(n) for n in inside})
            if len(labels) > 1:
                return At(None, "", "", TWO, "inside both " + " and ".join(labels) + ": the shapes of the file overlap")
            first = min(inside, key=lambda n: chosen.names[n])
        else:
            best = {}
            for n, km in near.items():
                if km < best.get(label(n), (float("inf"), 0))[0]:
                    best[label(n)] = (km, n)
            if not best:
                return At(None, "", "", NO_SHAPE_HERE, "")
            if len(best) > 1:
                return At(None, "", "", TWO, " or ".join(sorted(best, key=lambda x: (best[x][0], x))))
            first = next(iter(best.values()))[1]
        name = chosen.names[first]
        if chosen.states[first] is None:
            return At(None, name, "", NOT_A_STATE, name)
        # the nearest other state: within the allowed distance, or looked for a little farther
        own, other, other_km, flag = label(first), "", "", ""
        for reach in (allowed,) + tuple(km for km in OTHER_KM if km > allowed):
            found = near if reach == allowed else chosen.index.near(lat, lon, reach)
            others = sorted((km, label(n)) for n, km in found.items() if label(n) != own and n not in inside)
            if others:
                other, other_km = others[0][1], cc.rounded(others[0][0])
                if reach == allowed:
                    limit = tolerance if tolerance is not None and allowed <= tolerance else None
                    flag = ("within the allowed " + format(limit, "g") + " km of " if limit is not None
                            else "within the stated error (" + cc.rounded(allowed) + " km) of ") + other
                break
        where = "geoBoundaries " + chosen.code + " " + chosen.level
        if chosen.hows[first] == ISLAND:
            where += "; " + files_as_island(chosen.country, chosen.states[first])
        elif chosen.hows[first] != "name":
            name += " (" + chosen.states[first] + " by the " + chosen.hows[first] + ")"
        basis = ("coordinates inside " if inside else "in the water, " + cc.rounded(near[first]) + " km from ") + name + ", " + where
        return At(chosen.states[first], chosen.names[first], basis, "", "", other, other_km, flag)


def files_as_island(country, island):
    return "AntWeb files this under " + country + " as the island " + island


# ---------------------------------------------------------------- one specimen

Row = collections.namedtuple("Row", "code event locality_code country adm1 adm2 island lat lon stated locality notes bioregion group")
Proposal = collections.namedtuple("Proposal", "field current proposed basis country other other_km flag", defaults=("", "", ""))
Not = collections.namedtuple("Not", "field reason detail country", defaults=("",))


class Corrections:
    """The rule: what is proposed for one record and why not."""

    def __init__(self, checker, states, shapes):
        self.checker, self.states, self.shapes = checker, states, shapes
        self.countries = checker.countries
        self.kinds = collections.Counter()                       # what the records looked at were
        self._memo = {}

    def work(self, row):
        """The proposals and the reasons for one record: a list of Proposal and Not."""
        key = (row.country, row.adm1 == "", row.island == "", row.lat, row.lon, row.stated)
        if key not in self._memo:
            if len(self._memo) > 200000:
                self._memo.clear()
            self._memo[key] = self._work(row)
        return self._memo[key]

    def point(self, row):
        try:
            lat, lon = float(row.lat), float(row.lon)
        except ValueError:
            return None
        return (lat, lon) if cc.usable(lat, lon) else None

    def derive(self, row, point, current):
        """The country of a record that has none, or none AntWeb knows: (Proposal or Not, valid country or None,
        Proposal of the state or None). A point in an island country of AntWeb (Hawaii) gives its parent country
        as the country and the island as the state: that is how AntWeb, and so TaxonWorks, files it."""
        prefix = "" if current == "" else "country not valid in AntWeb; "
        if point is None:
            return Not("country", prefix + NO_COORDINATES, ""), None, None
        r = self.checker.derive(point[0], point[1], row.stated)
        if r.cls in (cc.DERIVE_IN, cc.DERIVE_NEAR):
            place = self.countries.rows[r.country]
            basis = ("coordinates inside " if r.cls == cc.DERIVE_IN else "in the water, " + cc.rounded(r.km) + " km from ") + r.country + ", " + r.shape
            parent = self.countries.resolve(place.parent) if place.is_island and place.parent else None
            if parent is None:
                return Proposal("country", current, r.country, basis, r.country), r.country, None
            basis += "; " + files_as_island(parent.name, r.country)
            return Proposal("country", current, parent.name, basis, parent.name), parent.name, Proposal("adm1", "", r.country, basis, parent.name)
        if r.cls == cc.TWO:
            return Not("country", prefix + TWO, r.country), None, None
        if r.cls == cc.DERIVE_NONE:
            return Not("country", prefix + NO_COUNTRY_HERE, r.country), None, None
        nearest = (cc.rounded(r.km) + " km from " + r.country) if r.country else ((cc.rounded(r.km) + " km from the nearest country") if r.km is not None else "")
        return Not("country", prefix + AT_SEA, nearest), None, None

    def _work(self, row):
        out = []
        point = self.point(row)
        valid = self.countries.resolve(row.country) if row.country else None
        stated_country = None                                    # the stored country, when it is valid
        island_state = None
        if not row.country or valid is None:
            first, country, island_state = self.derive(row, point, row.country if row.country else "")
            out.append(first)
        else:
            country = stated_country = valid.name
        if row.adm1 or row.island or country is None:
            return out
        if island_state is not None:
            return out + [island_state]
        if not self.states.of(country):
            return out + [Not("adm1", NO_STATES, country, country)]
        if point is None:
            return out + [Not("adm1", NO_COORDINATES, "")]
        lat, lon = point
        if stated_country is not None:
            r = self.checker.decide(row.country, "", lat, lon, row.stated)
            if r.cls == cc.FLAG:
                return out + [Not("adm1", OUTSIDE, r.country + ", " + cc.rounded(r.km) + " km from " + stated_country)]
            if r.cls == cc.TERRITORY:
                return out + [Not("adm1", TERRITORY, r.country)]
            if r.cls not in (cc.INSIDE, cc.NEAR):
                return out + [Not("adm1", NO_FILE, "the stated country has no shape")]
        chosen = self.shapes.choose(country, self.checker.code_of_country.get(country, ""))
        if chosen is None:
            return out + [Not("adm1", NO_FILE, self.shapes.problems.get(country, ""), country)]
        at = self.shapes.state_at(chosen, lat, lon, cc.allowed_km(row.stated, self.checker.tolerance), self.checker.tolerance)
        if not chosen.usable:                                    # no proposal from a level below the minimum; the shape is named
            return out + [Not("adm1", NO_MATCH, at.shape, country)]
        if at.state is None:
            return out + [Not("adm1", at.reason, at.detail, country)]
        return out + [Proposal("adm1", "", at.state, at.basis, country, at.other, at.other_km, at.flag)]

    def kind(self, row):
        """Which of the three lists of Brian Fisher's note a record belongs to (for the totals of the report)."""
        if not row.country:
            return "country missing"
        if self.countries.resolve(row.country) is None:
            return "country not valid in AntWeb"
        if row.island:
            return "state missing, island-country specimen (left out)"
        return "state missing"


# ---------------------------------------------------------------- the lists

CORRECTIONS_HEADER = ["specimen_code", "collecting_event_code", "locality_code", "field", "current_value", "proposed_value", "basis",
                      "nearest_other_state", "km_to_other_state", "other_state_flag",
                      "latitude", "longitude", "stated_error", "locality_name", "country", "upload_group"]
EVENTS_HEADER = ["collecting_event_code", "field", "current_value", "proposed_value", "specimens", "first_specimen_code", "last_specimen_code",
                 "locality_name", "latitude", "longitude", "flag", "nearest_other_state", "km_to_other_state", "other_state_flag",
                 "country", "basis", "upload_group"]
SUGGESTIONS_HEADER = ["country", "level", "shape", "antweb_state", "score", "specimens", "note"]      # the first four: adm1_shape_map.csv
WITHOUT_HEADER = ["country", "level", "antweb_state", "closest_shape_that_is_no_state", "score"]
NOT_HEADER = ["collecting_event_code", "field", "reason", "detail", "specimens", "first_specimen_code", "last_specimen_code", "country",
              "state", "adm2", "locality_name", "locality_notes", "bioregion", "latitude", "longitude", "stated_error", "upload_group"]


class Lists:
    """Everything the four files need, gathered record by record."""

    def __init__(self, groups=None):
        self.groups = groups or {}
        self.proposals = []                                      # (Row, Proposal)
        self.nots = []                                           # (Row, Not)
        self.looked = collections.Counter()

    def group(self, row):
        return self.groups.get(row.group, "group " + row.group if row.group else "")

    def add(self, row, results, kind):
        self.looked[kind] += 1
        for r in results:
            (self.proposals if isinstance(r, Proposal) else self.nots).append((row, r))

    def corrections(self):
        lines = []
        states = {(row.code): p.proposed for row, p in self.proposals if p.field == "adm1"}
        for row, p in self.proposals:
            lines.append(((p.country, states.get(row.code, ""), row.event, row.code, p.field != "country"),
                          [row.code, row.event, row.locality_code, p.field, p.current, p.proposed, p.basis, p.other, p.other_km, p.flag,
                           row.lat, row.lon, row.stated, row.locality, p.country, self.group(row)]))
        return [line for _, line in sorted(lines, key=lambda x: x[0])]

    def events(self):
        """One line per collecting event and field. A collecting event whose specimens get different proposals is flagged."""
        by_event = collections.defaultdict(list)
        for row, p in self.proposals:
            by_event[(row.event or "\0" + row.code, p.field)].append((row, p))
        more = collections.Counter((row.event, n.field) for row, n in self.nots if row.event)
        lines = []
        for (event, field), items in by_event.items():
            items.sort(key=lambda x: x[0].code)
            values = collections.Counter(p.proposed for _, p in items)
            row, p = items[0]
            flags = []
            if len(values) > 1:
                flags.append("DIFFERENT PROPOSALS, do not enter as it stands: " + "; ".join(v + " (" + cc.n(c) + ")" for v, c in sorted(values.items())))
            if row.event and more[(row.event, field)]:
                flags.append(cc.n(more[(row.event, field)]) + " more of this collecting event in not_derivable.csv")
            places = {(r.lat, r.lon) for r, _ in items}
            if len(places) > 1:
                flags.append(cc.n(len(places)) + " different coordinates in this collecting event")
            flagged = collections.Counter(x.flag for _, x in items if x.flag)
            near = ""
            if flagged:                                          # another state within the stated error: for a person to look at first
                near = flagged.most_common(1)[0][0]
                if sum(flagged.values()) < len(items) or len(flagged) > 1:
                    near = cc.n(sum(flagged.values())) + " of " + cc.n(len(items)) + " specimens: " + near
            lines.append(((p.country, " | ".join(sorted(values)), row.event, field != "country"),
                          [row.event, field, p.current, " | ".join(sorted(values)), len(items), items[0][0].code, items[-1][0].code,
                           row.locality, row.lat, row.lon, ". ".join(flags), p.other if len(values) == 1 else "", p.other_km if len(values) == 1 else "", near,
                           p.country, p.basis if len(values) == 1 else "", self.group(row)]))
        return [line for _, line in sorted(lines, key=lambda x: x[0])]

    def not_derivable(self):
        by_key = collections.defaultdict(list)
        for row, n in self.nots:
            by_key[(row.event or "\0" + row.code, n.field, n.reason, n.detail)].append(row)
        lines = []
        for (event, field, reason, detail), rows in by_key.items():
            rows.sort(key=lambda r: r.code)
            row = rows[0]
            lines.append(((field, reason, row.country, row.event, row.code),
                          [row.event, field, reason, detail, len(rows), rows[0].code, rows[-1].code, row.country, row.adm1, row.adm2, row.locality,
                           row.notes, row.bioregion, row.lat, row.lon, row.stated, self.group(row)]))
        return [line for _, line in sorted(lines, key=lambda x: x[0])]

    def in_shapes(self):
        """How many records lie in each shape that gave no proposal: {(country, shape name): specimens}."""
        counts = collections.Counter()
        for row, x in self.nots:
            if x.reason in (NOT_A_STATE, NO_MATCH) and x.detail:
                counts[(x.country, x.detail)] += 1
        return counts


def suggestions(shapes, states, counts):
    """For a person who keeps adm1_shape_map.csv: (lines of mapping_suggestions.csv, lines of
    antweb_states_without_shape.csv, shapes left out). For each shape of a country's level that is no AntWeb
    state, the AntWeb valid states of that country whose name, or an old name of which, is most like it. A shape
    is listed when records lie in it or a state is like it. Nothing is used from these lists by the tool."""
    lines, without, left_out = [], [], 0
    for country in sorted(shapes.tables):
        t = shapes.tables[country]
        if "matched" not in t:
            continue
        for shape in t["shapes_not_states"]:
            found = states.closest(country, shape)
            specimens = counts.get((country, shape), 0)
            if not found and not specimens:
                left_out += 1
                continue
            found.sort(key=lambda x: (x[1] in t["shape_of"], -x[0], x[1]))          # a state that has no shape yet comes first
            for score, state, old in found or [("", "", "")]:
                notes = []
                if not state:
                    notes.append("no AntWeb state of " + country + " has a name like it")
                if old:
                    notes.append("by AntWeb's old name " + old)
                if state in t["shape_of"]:
                    notes.append("the state has a shape already: " + t["shape_of"][state])
                lines.append(((country, -specimens, shape, -(score or 0)),
                              [country, t["level"], shape, state, format(score, ".2f") if state else "", specimens, "; ".join(notes)]))
        for state in t["not_matched"]:
            best = max(((likeness(shape, name), shape) for shape in t["shapes_not_states"] for name in [state] + states.old[country].get(state, [])),
                       default=(0.0, ""))
            without.append([country, t["level"], state] + ([best[1], format(best[0], ".2f")] if best[0] >= MIN_SCORE else ["", ""]))
    return [line for _, line in sorted(lines, key=lambda x: x[0])], without, left_out


def by_region(index, names, province_of, states, rows, tolerance):
    """Madagascar by region: what a conversion from the provinces to the regions would give. rows: (stored
    state, latitude, longitude, specimens) of every record of Madagascar. For each region (shape of the boundary
    file): the specimens with coordinates inside it (or in the water within the tolerance of it alone), and of
    them how many have the province the mapping file gives for the region, another state, or none.
    Returns (table {region: [specimens, that province, another state, no state]}, totals Counter)."""
    table = {name: [0, 0, 0, 0] for name in names}
    totals = collections.Counter()
    for adm1, lat, lon, count in rows:
        totals["specimens"] += count
        try:
            point = (float(lat), float(lon))
        except ValueError:
            totals["no coordinates"] += count
            continue
        if not cc.usable(*point):
            totals["coordinates that cannot be used"] += count
            continue
        found = set(index.inside(*point)) or set(index.near(point[0], point[1], tolerance))
        if len(found) != 1:
            totals["in no region's shape" if not found else "on the line between two regions"] += count
            continue
        region = names[next(iter(found))]
        totals["in a region"] += count
        line = table[region]
        line[0] += count
        stored = states.find(MADAGASCAR, adm1)[0] if adm1 else None
        line[3 if not adm1 else (1 if stored is not None and stored == province_of.get(region) else 2)] += count
    return table, totals


def write_csv(path, header, lines):
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(lines)


SUMMARY_LINES = 60


def top(counter, limit):
    """Counted lines, largest first, never more than limit lines: the rest is one line."""
    rows = sorted(counter.items(), key=lambda kv: (-kv[1], str(kv[0])))
    if len(rows) > limit:
        rest = rows[limit - 1:]
        rows = rows[:limit - 1] + [(cc.n(len(rest)) + " other reasons", sum(c for _, c in rest))]
    return ["  " + cc.n(count).rjust(9) + "  " + str(key) for key, count in rows] or ["  none"]


def level_line(country, t):
    """One line for a country: how each level fits, which is used and why."""
    looked = [x["level"] for x in t["tried"]]
    rest = [level for level in LEVELS if level not in looked]
    return ("  " + country + " (" + t["code"] + "): " + "; ".join(level_text(x) for x in t["tried"])
            + ("; " + (rest[0] if len(rest) == 1 else rest[0] + " to " + rest[-1]) + " not looked at" if rest else "")
            + (" | used: " if t["usable"] else " | NONE USED; a mapping would be for ") + t["level"] + " (" + t["why"] + ")")


def region_lines(table, totals, province_of, code, group_text):
    """The lines of the report for Madagascar by region (by_region)."""
    n = cc.n
    out = ["", "Madagascar by region: what a conversion from the provinces to the regions would give. Every record of " + MADAGASCAR + " of "
           + ("every group" if group_text == "every group" else "the group") + ", not only those with a gap; geoBoundaries " + code + " ADM1, "
           + n(len(table)) + " shapes.",
           "  Records of Madagascar: " + n(totals["specimens"]) + ". With coordinates in one region: " + n(totals["in a region"])
           + ". No coordinates: " + n(totals["no coordinates"]) + ". Coordinates that cannot be used: " + n(totals["coordinates that cannot be used"])
           + ". In no region's shape (in the water or outside): " + n(totals["in no region's shape"])
           + ". On the line between two regions: " + n(totals["on the line between two regions"]) + ".",
           "  " + "region".ljust(26) + "specimens".rjust(10) + "  " + "province (mapping file)".ljust(25) + "stored state: that province".rjust(28) + "another".rjust(10) + "none".rjust(10)]
    for region in sorted(table):
        inside, same, another, none = table[region]
        out.append("  " + region.ljust(26) + n(inside).rjust(10) + "  " + (province_of.get(region) or "no province AntWeb has").ljust(25)
                   + n(same).rjust(28) + n(another).rjust(10) + n(none).rjust(10))
    return out


def report_text(when, commit, group_text, lists, shapes, detail, seconds, tolerance, map_path=MAP_FILE, suggested=None, madagascar=None):
    """The report: a summary of at most SUMMARY_LINES lines (totals, reasons, the countries below the minimum),
    then the long tables. suggested: (lines of mapping_suggestions.csv, lines of antweb_states_without_shape.csv,
    shapes left out of the first); madagascar: the lines of region_lines, when that table was made."""
    n = cc.n
    events = lists.events()
    out = ["AntWeb place corrections, " + when + " (tool version " + VERSION + ", AntWeb commit " + (commit or "unknown") + ")",
           "Upload group: " + group_text,
           "Summary: totals, reasons, countries that need a mapping. The tables follow under \"Details\".", "",
           "Records looked at (no state, no country, or a country AntWeb does not know): " + n(sum(lists.looked.values()))]
    out += top(lists.looked, 4)
    values = {}
    for field, label, limit in (("country", "Country", 6), ("adm1", "State (adm1)", 10)):
        proposals = [(row, p) for row, p in lists.proposals if p.field == field]
        nots = collections.Counter()
        for row, x in lists.nots:
            if x.field == field:
                nots[x.reason] += 1
        lines = [e for e in events if e[1] == field]
        different = sum(1 for e in lines if e[10].startswith("DIFFERENT"))
        out += ["", label + ": " + n(len(proposals)) + " specimens with a proposal (corrections.csv), in " + n(len(lines)) + " lines of by_collecting_event.csv"
                + (", " + n(different) + " of them flagged for different proposals" if different else "")]
        if field == "adm1":
            flagged = sum(1 for _, p in proposals if p.flag)
            out += ["  " + n(flagged) + " of them (" + n(sum(1 for e in lines if e[13])) + " lines) lie within the stated error, or the allowed " + format(tolerance, "g")
                    + " km, of another state: column other_state_flag, look at those first."]
        out += ["  No proposal (not_derivable.csv): " + n(sum(nots.values())) + " specimens, by reason:"] + top(nots, limit)
        values[field] = collections.Counter((p.country + ": " + p.proposed) if field == "adm1" else p.proposed for _, p in proposals)
    below = collections.Counter({c + " " + t["level"]: 0 for c, t in shapes.tables.items() if not t["usable"]})
    for row, x in lists.nots:
        if x.reason == NO_MATCH:
            below[x.country + " " + shapes.tables[x.country]["level"]] += 1
    out += ["", NO_MATCH + " (" + n(len(below)) + (" country, " if len(below) == 1 else " countries, ") + n(sum(below.values()))
            + " specimens; see mapping_suggestions.csv):"]
    names = [key + " (" + n(count) + ")" for key, count in sorted(below.items(), key=lambda kv: (-kv[1], kv[0]))]
    listed = cc.wrap(names)
    room = SUMMARY_LINES - len(out) - 2
    if len(listed) > room:
        listed = listed[:room - 1] + ["  ... and more: every one of them is marked NONE USED in the list of levels below"]
    out += listed + ["", "Report only: nothing was changed in the database."]
    assert len(out) <= SUMMARY_LINES

    out += ["", "Details", ""]
    for field, label in (("country", "Country"), ("adm1", "State (adm1)")):
        out += [label + ", proposed values (the 40 largest):"] + cc.lines_of(values[field], str, limit=40) + [""]
    names = collections.Counter()
    for row, x in lists.nots:
        if x.reason == NOT_A_STATE:
            names[(x.country, x.detail)] += 1
    out += ["For the Geolocale Manager: names of the boundary files that are no state in AntWeb, with the specimens that lie in them:"]
    out += cc.lines_of(names, lambda k: k[0] + ": " + k[1], limit=200)
    out += ["", "The level of the boundary files, for each country whose records were looked up in them (" + n(len(shapes.tables)) + " countries). At each level:",
            "AntWeb's valid states of the country that a shape names / all of them, and the shapes that are an AntWeb state / all shapes of the level.",
            "The level used: the one with the largest share of shapes that are AntWeb states (the coarser one of two alike), when at least half of its shapes",
            "are AntWeb states or, for the coarsest level only, at least half of AntWeb's states are matched. A country below that gives no proposal (NONE USED).",
            "A country whose records all lack coordinates, or lie outside it, is not in this list."]
    out += [level_line(country, shapes.tables[country]) for country in sorted(shapes.tables)]
    out += ["", "How the names fit at the level named above, country by country:"]
    for country in sorted(shapes.tables):
        t = shapes.tables[country]
        if "matched" not in t:
            continue
        out += ["  " + country + ": geoBoundaries " + t["code"] + " " + t["level"] + ("" if t["usable"] else " (NOT USED: below the minimum)"),
                "    AntWeb states matched (" + n(len(t["matched"])) + "): " + "; ".join(t["matched"]),
                "    AntWeb states not matched (" + n(len(t["not_matched"])) + "): " + "; ".join(t["not_matched"]),
                "    shapes that are an island country of AntWeb, entered as the state (" + n(len(t["islands"])) + "): " + "; ".join(t["islands"]),
                "    shapes that are no state in AntWeb (" + n(len(t["shapes_not_states"])) + "): " + "; ".join(t["shapes_not_states"][:300])
                + (" ... and " + n(len(t["shapes_not_states"]) - 300) + " more" if len(t["shapes_not_states"]) > 300 else "")]
        if t["other_way"]:
            out += ["    matched in another way than by the name itself: " + "; ".join(t["other_way"])]
    if shapes.problems:
        out += ["", "Countries with no state shapes (" + n(len(shapes.problems)) + "):"] + ["  " + c + ": " + why for c, why in sorted(shapes.problems.items())]
    used = sorted(shapes.rows_used)
    out += ["", "Mapping file " + map_path + (": " + n(len(shapes.map_rows)) + " rows, " if os.path.exists(map_path) else ": NOT FOUND (fetch it next to the tool), ")
            + n(len(used)) + " shapes named by it in this run:"]
    out += ["  " + c + " " + level + ": " + shape + " -> " + state for c, level, shape, state in used] or ["  none"]
    if suggested is not None:
        lines, without, left_out = suggested
        out += ["", "mapping_suggestions.csv: " + n(len(lines)) + " lines for " + n(len({(x[0], x[2]) for x in lines})) + " shapes that are no state in AntWeb, each with the AntWeb",
                "states of its country whose name is most like it (score 1: the same once words such as Province or Region are left out), and the specimens",
                "that lie in the shape. The first four columns are those of adm1_shape_map.csv: a line a person approves can be pasted there. Nothing is used",
                "from it by the tool. " + n(left_out) + " more shapes have no specimens and no AntWeb state like them, and are not listed.",
                "antweb_states_without_shape.csv: " + n(len(without)) + " valid AntWeb states that no shape was matched to, with the shape most like each."]
    out += madagascar or []
    by_licence = collections.defaultdict(list)
    for key, entry in shapes.files.items():
        by_licence[entry.get("licence", "not read")].append(key)
    out += ["", "State shapes: geoBoundaries gbOpen, the simplified files, commit " + detail.commit + ", in " + detail.folder + ". Checksums: " + detail.lock_path,
            "  Licences:"] + ["    " + lic + ": " + "; ".join(sorted(keys)) for lic, keys in sorted(by_licence.items())]
    countries_used = sorted(iso for iso, f in detail.used.items() if f.get("bytes") and not f.get("problem"))
    out += ["Country shapes: Natural Earth 1:10m" + ("; near a border the full geoBoundaries file of the country: " + " ".join(countries_used) if countries_used else " alone")
            + ". The allowed distance: " + format(tolerance, "g") + " km, or the specimen's stated error when larger."]
    out += ["", "Run time: " + seconds, "", "Report only: nothing was changed in the database."]
    return "\n".join(out) + "\n"


# ---------------------------------------------------------------- the database (read only)

ADM1_SQL = "select hex(name), hex(ifnull(parent, '')), is_valid, hex(ifnull(valid_name, '')) from geolocale where georank = 'adm1' and name is not null"


def group_sql(group_id):
    return "" if group_id is None else " and s.access_group = " + str(int(group_id))


def stored_countries_sql(group_id=None):
    return "select hex(ifnull(s.country, '')), count(*) from specimen s where 1 = 1" + group_sql(group_id) + " group by 1"


def madagascar_sql(names, group_id=None):
    """Every record of Madagascar (names: the stored spellings of the country), counted by state and coordinates."""
    return ("select hex(ifnull(s.adm1, '')), ifnull(round(s.decimal_latitude, 5), ''), ifnull(round(s.decimal_longitude, 5), ''), count(*)"
            + " from specimen s where hex(s.country) in (" + ", ".join(cc.sql_string(name.encode("utf-8").hex().upper()) for name in names) + ")"
            + group_sql(group_id) + " group by 1, 2, 3")


def provinces_in_antweb(states):
    """True when AntWeb's states of Madagascar are the six provinces (at least four of them are valid states)."""
    return len(PROVINCES & {plain(name) for name in states.of(MADAGASCAR)}) >= 4


def madagascar_regions(state_shapes, states, code, rows, tolerance, group_text):
    """The lines of the report for Madagascar by region, from the ADM1 file of the country (the regions)."""
    path = state_shapes.detail.file(code, "ADM1", SIMPLIFIED)
    if path is None:
        return ["", "Madagascar by region: geoBoundaries has no ADM1 file of " + code + "."]
    features = [(mend(name), rings) for name, rings in cc.read_features(path)[0] if rings]
    names = [name for name, _ in features]
    provinces, _ = state_shapes.match(MADAGASCAR, "ADM1", names)
    table, totals = by_region(cc.index_of_shapes([rings for _, rings in features]), names, dict(zip(names, provinces)), states, rows, tolerance)
    return region_lines(table, totals, dict(zip(names, provinces)), code, group_text)


def rows_sql(columns, group_id=None, unknown=()):
    """The records with a gap: no state, no country, or a country AntWeb does not know (unknown: those names)."""
    text = lambda name: "hex(ifnull(s." + name + ", ''))" if name in columns else "''"
    number = lambda name: "ifnull(round(s." + name + ", 5), '')"
    gap = "s.adm1 is null or s.adm1 = '' or s.country is null or s.country = ''"
    if unknown:
        gap += " or hex(s.country) in (" + ", ".join(cc.sql_string(u.encode("utf-8").hex().upper()) for u in unknown) + ")"
    return ("select " + ", ".join([text("code"), text("collectioncode"), text("localitycode"), text("country"), text("adm1"), text("adm2"),
                                   text("island_country"), number("decimal_latitude"), number("decimal_longitude"), text("latlonmaxerror"),
                                   text("localityname"), text("localitynotes"), text("bioregion"), "ifnull(s.access_group, 0)"])
            + " from specimen s where 1 = 1" + group_sql(group_id) + " and (" + gap + ") order by s.country, s.code")


def read_row(r):
    u = cc.unhex
    number = lambda text: str(float(text)) if text not in ("", "NULL") else ""       # 54.01950 and 54.0195 are one place
    return Row(u(r[0]), u(r[1]), u(r[2]), u(r[3]).strip(), u(r[4]).strip(), u(r[5]), u(r[6]).strip(), number(r[7]), number(r[8]), u(r[9]), u(r[10]),
               u(r[11]), u(r[12]), r[13])


def find_group(groups, name):
    """The id of the upload group with this name (capitals ignored); stops when there is none or more than one."""
    exact = [gid for gid, text in groups.items() if text.casefold() == name.casefold()]
    if len(exact) == 1:
        return exact[0]
    close = sorted(text for text in groups.values() if name.casefold() in text.casefold())
    sys.exit("--group: no upload group is named " + repr(name) + (". Names with that text: " + "; ".join(close) if close else ""))


def main(argv=None):
    a = argparse.ArgumentParser(description="The country and state a specimen is missing, proposed from its coordinates. Report only.")
    a.add_argument("--group", default=None, help="the name of one upload group (default: every group)")
    a.add_argument("--group-id", type=int, default=None, help="the id of one upload group (1: California Academy of Sciences)")
    a.add_argument("--tolerance-km", type=float, default=cc.TOLERANCE_KM, help="how far outside a shape a point may lie (default 1; a larger stated error counts instead)")
    a.add_argument("--shapes-source", choices=["naturalearth", "geoboundaries"], default="geoboundaries",
                   help="the country test: geoboundaries (default) asks the country's detailed file near a border; naturalearth does not")
    a.add_argument("--shapes", default=None, help="a local GeoJSON file in place of the Natural Earth file")
    a.add_argument("--shapes-folder", default=cc.SHAPES_FOLDER, help="where shape files and the lock file are kept (default " + cc.SHAPES_FOLDER + ")")
    a.add_argument("--map", default=MAP_FILE, help="the mapping file (default adm1_shape_map.csv next to this tool)")
    a.add_argument("--project", default=None, help="AntWeb project folder (default /root/antweb or /root/antweb-next)")
    a.add_argument("--container", default="antweb_mysql_1")
    a.add_argument("--out", default=None, help="output folder (default /root/antweb-place-fix/<date>-<time>-corrections)")
    args = a.parse_args(argv)
    project = args.project or cc.default_project()
    now = datetime.datetime.now()
    started = time.time()
    folder = args.out or os.path.join("/root/antweb-place-fix", now.strftime("%Y-%m-%d-%H%M") + "-corrections")
    commit = cc.read_first_line(os.path.join(project, "etc", "git-commit"))
    sql = lambda statement: cc.run_sql(project, statement, args.container)

    countries = cc.read_countries(sql(cc.countries_sql({r[0] for r in sql(cc.columns_sql("geolocale"))})))
    states = States([(cc.unhex(r[0]), cc.unhex(r[1]), r[2], cc.unhex(r[3])) for r in sql(ADM1_SQL)], countries)
    groups = {r[0]: cc.unhex(r[1]) for r in sql(cc.GROUPS_SQL)}
    group_id = args.group_id
    if args.group is not None:
        group_id = int(find_group(groups, args.group))
    group_text = "every group" if group_id is None else groups.get(str(group_id), "?") + " (id " + str(group_id) + ")"
    os.makedirs(folder, exist_ok=True)

    path = args.shapes or cc.fetch_shapes(args.shapes_folder)
    with open(path, encoding="utf-8") as f:
        shapes = cc.read_shapes(json.load(f))
    cc.map_shapes(shapes, countries)
    index = cc.Index(shapes, forget=True)
    store = cc.Detail(args.shapes_folder)
    checker = cc.Checker(countries, shapes, index, args.tolerance_km, store if args.shapes_source == "geoboundaries" else None)
    state_shapes = StateShapes(store, states, countries, read_map(args.map))
    work = Corrections(checker, states, state_shapes)

    stored = [cc.unhex(r[0]) for r in sql(stored_countries_sql(group_id))]
    unknown = [name for name in stored if name.strip() and countries.resolve(name.strip()) is None]
    lists = Lists(groups)
    columns = {r[0] for r in sql(cc.columns_sql("specimen"))}
    for r in cc.rows_sql(project, rows_sql(columns, group_id, unknown), args.container):
        row = read_row(r)
        lists.add(row, work.work(row), work.kind(row))

    write_csv(os.path.join(folder, "corrections.csv"), CORRECTIONS_HEADER, lists.corrections())
    write_csv(os.path.join(folder, "by_collecting_event.csv"), EVENTS_HEADER, lists.events())
    write_csv(os.path.join(folder, "not_derivable.csv"), NOT_HEADER, lists.not_derivable())
    suggested = suggestions(state_shapes, states, lists.in_shapes())
    write_csv(os.path.join(folder, "mapping_suggestions.csv"), SUGGESTIONS_HEADER, suggested[0])
    write_csv(os.path.join(folder, "antweb_states_without_shape.csv"), WITHOUT_HEADER, suggested[1])
    madagascar = None
    names = [name for name in stored if name.strip() and getattr(countries.resolve(name.strip()), "name", None) == MADAGASCAR]
    if names and provinces_in_antweb(states) and checker.code_of_country.get(MADAGASCAR):      # one more read: every record of Madagascar
        rows = [(cc.unhex(r[0]).strip(), r[1], r[2], int(r[3])) for r in sql(madagascar_sql(names, group_id))]
        madagascar = madagascar_regions(state_shapes, states, checker.code_of_country[MADAGASCAR], rows, args.tolerance_km, group_text)
    seconds = format(time.time() - started, ".0f") + " seconds; memory at the peak " + cc.n(cc.peak_mb()) + " MB"
    text = report_text(now.strftime("%Y-%m-%d %H:%M"), commit, group_text, lists, state_shapes, store, seconds, args.tolerance_km, args.map, suggested, madagascar)
    with open(os.path.join(folder, "report.txt"), "w", encoding="utf-8") as f:
        f.write(text)
    print(text)
    print("Files: " + folder)
    return 0


if __name__ == "__main__":
    sys.exit(main())
