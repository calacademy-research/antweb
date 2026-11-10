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
  python3 place_corrections.py --states-source naturalearth    the state shapes from Natural Earth's states and provinces
  python3 place_corrections.py --states-source both            the corrections by geoBoundaries, and the two sources compared
  python3 place_corrections.py --no-specimen-test              by the names and the mapping file alone (no read of the specimens)

A second tool, not a mode of coordinate_country.py: the question is another one (which state, for the records
with a gap) and so are the lists. It loads coordinate_country.py, which must lie next to it, and uses its rule
for the country, its index, its reader of boundary files and its lock file.

What it proposes.

  A. State missing: a valid AntWeb country, no state, not an island-country specimen, the country has valid
     states in AntWeb, usable coordinates.
     1. The point must agree with the stored country by the rule of coordinate_country.py: inside it, or
        outside by no more than 1 km (--tolerance-km) or the specimen's stated error. Otherwise no proposal
        ("coordinates outside the stated country": it is in the conflicts report of that tool).
     2. The state shape that holds the point, from geoBoundaries gbOpen for the country's ISO code (or from
        Natural Earth's states), at the level and with the state that AntWeb's own specimens confirm (below).
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

Which shape is which AntWeb state: tested with AntWeb's own specimens, not approved by a person (Brian Fisher,
2026-11-10: "aren't we dealing with facts that can be tested and evaluated? ... georeference them to test").
AntWeb holds many specimens, of every upload group, that have both a state and coordinates. They are read once
(one select: a valid country, a state, usable coordinates, no island-country specimen; counted by country,
state and place). The stored state is taken as AntWeb's valid state (itself, or the valid state an old name
points to). For a country and a level of its boundary file, every such specimen that lies inside exactly one
shape is counted for (shape, state). Then, for each shape, in this order:
  - a row of the mapping file wins (adm1_shape_map.csv: the decisions the data cannot make);
  - an island country of AntWeb stays what it is;
  - "confirmed by specimens": at least 5 specimens of the state lie in the shape (MIN_POINTS), they are at
    least 80% of the state's located specimens (MIN_OF_STATE), and at least 80% of the shape's specimens that
    have a state are filed under it (MIN_OF_SHAPE). Used whatever the two names are: this settles "Lower
    Uele" and "Bas-Uele". One exception: a shape that has the name of another AntWeb state, when no specimens
    say that this name is wrong, is left to a person (five misfiled specimens must not move a state);
  - a name match (capitals, accents and punctuation ignored; an old name counts; then without a word such as
    Province, Region, State; for Natural Earth every name the unit has): not used when the specimens
    contradict it, that is when the state has 5 or more located specimens and fewer than half of them lie in
    the shape (CONTRADICTED_BELOW); used and marked "by name only, N specimens" when the state has fewer than
    5; used and marked "the specimens agree in part" otherwise.
  mapping_tested.csv has every pair with what it rests on and the three numbers. What a person still has to
  decide is in to_decide.csv, and nothing else is: shapes that hold several AntWeb states (AntWeb is finer than
  the file), AntWeb states that lie in several shapes (the file is finer), shapes named as one state with the
  specimens of another. mapping_suggestions.csv keeps the names most like a shape for the shapes the test
  could not settle, with what AntWeb's specimens in the shape are filed under.

Which level of the boundary files. Not assumed to be ADM1.
  - With specimens to test with (5 or more of the country, and a state with 5 located at the coarsest level):
    each level is tested, coarse to fine; the next finer level is looked at only while more AntWeb states lie
    inside a shape that is not their own than are settled. The level with the most AntWeb states confirmed by
    specimens or matched by name is used (the coarser one of two alike).
  - Without (or when the specimens confirm nothing): by the names, as in version 3. Two shares at each level:
    AntWeb's states that a shape names of all AntWeb's states of the country, and the shapes that are an AntWeb
    state of all shapes. The first level with both at least half; else the level with the largest share of
    shapes, used when half of its shapes are AntWeb states or, for the coarsest level only, half of AntWeb's
    states are matched. Else no level: the country gives no proposal ("AntWeb's states and the boundary file
    do not match: needs a mapping"), with the shape each record lies in. A finer level is never used on the
    states matched alone: its units often have the names of the states (version 1 took the communes of
    Chile, the districts of the Czech Republic, the provinces of Spain that way).
  The report has one line for each country: the levels looked at, what the specimens gave, the level used, why.
  A name spoilt when the boundary file was made is put right before it is compared (mend): the file of
  Chile has "RegiÃ³n de Antofagasta" for "Región de Antofagasta".
  The light ("simplified") files of geoBoundaries are used for the states: measured 2026-11-10 for Madagascar,
  Mexico and the United States, their lines lie within 100 m of the full files' for 99 in 100 points, and they
  are 2 to 50 times smaller. The country test uses the full files.

The mapping file, tools/place/adm1_shape_map.csv (next to this tool), has three kinds of line:
  country,level,shape,antweb_state[,new]   the state of a shape; wins over the test and the names. With "new" in
      the fifth cell the state is one AntWeb does not have yet (Seychelles: the shape Other Islands is the
      state Outer Islands): it is proposed, marked "state to add in the Geolocale Manager first" in the basis
      and the note, and listed in the report under "States to add". Without "new" a row whose state AntWeb
      does not have is not used.
  convert,country,unit,unit,...    AntWeb has two sets of valid states for the country and gives the coarser one
      up. Madagascar (Brian Fisher, 2026-11-10: convert to regions): AntWeb has the six provinces and the 22
      regions; a specimen whose stored state is a province and whose coordinates lie inside one region gets a
      correction: field adm1, current value the province, proposed value the region. The provinces are left
      out of the test and of the states the shapes are compared with. With a province and no coordinates:
      "no coordinates: stays with the province"; in the water or in a gap: "coordinates in no region's shape:
      stays with the province".
  within,country,state,unit        the province a region belongs to, for one check: the note of a converted
      specimen says "the stored province is the province of the region", or "stored province differs from the
      coordinates' region: check the coordinates" (production 2026-11-10: about 3,400 of the Academy).

Two sources of state shapes (--states-source).
  geoboundaries (the default): geoBoundaries gbOpen, a file for each country and level, ADM1 to ADM4.
  naturalearth: Natural Earth "admin 1 states and provinces", 1:10m, public domain: one file of 4,596 units for
      the world (40.7 MB), pinned to the commit of the country file and to a checksum. One level, called NE in
      the mapping file. A unit has its ISO 3166-2 code, its ne_id, and names in many languages and
      alternative names: every one is a spelling when a unit is matched with an AntWeb state (NE_NAMES).
      The code and the ne_id are carried into corrections.csv, by_collecting_event.csv and mapping_tested.csv.
  both: the corrections by geoBoundaries; then every country that has specimens to test with is looked up in
      both sources, and the report has "Sources compared": for each country and source the share of AntWeb's
      specimens with a state and coordinates that lie in the unit confirmed or matched for their state
      (agreement), in the unit of another state, in a unit with no AntWeb state, in no unit (the coast).
      mapping_tested_natural_earth_states_and_provinces_1_10m.csv has the second source's pairs.

For TaxonWorks. The Academy's project keeps country, state and bioregion as predicates (data attributes) of the
collecting event, and those are what AntWeb's upload reads (TaxonWorksTransformer: Country, adm1, adm2,
BiogeographicRegion). corrections.csv and by_collecting_event.csv have a column taxonworks_predicate with the
predicate of the field (Country or adm1) and a column biogeographic_region with the bioregion AntWeb's
definition gives for the place as proposed, to fill the predicate BiogeographicRegion from the same line: the
valid state's bioregion when it has exactly one, else the country's when it has exactly one, else blank (the
rule of place_fix.decide(); "none", "null" and blank mean none; a state that is an island country has the
island's).

Madagascar by region (in the report, when AntWeb has the provinces): for each region the specimens of the
group with coordinates inside it, of every record of Madagascar, and how many of them have as stored state the
region's province, the region itself, another state or none; and the records with no coordinates. One more
read of the database, for Madagascar alone.

It writes one folder (default /root/antweb-place-fix/<date>-<time>-corrections/):

  corrections.csv          one line per specimen and field: specimen code, collecting event code, locality
                           code, field (country or adm1), current value, proposed value (AntWeb's valid
                           name), the basis in plain words, the nearest other state, the km to it, the flag
                           "within the stated error of <state>", a note (state to add; the province check),
                           latitude, longitude, stated error, locality name, country, upload group, the
                           unit's ISO 3166-2 code and ne_id (Natural Earth), the predicate of TaxonWorks,
                           the bioregion. In the order country, state, collecting event.
  by_collecting_event.csv  the working list for TaxonWorks: one line per collecting event, field and current
                           value with the proposed value, the number of specimens, the first and last
                           specimen code, the locality name and the coordinates. A collecting event whose
                           specimens get different proposals is flagged and not to be entered as it stands. A
                           specimen with no collecting event code has a line of its own. With the columns
                           of corrections.csv from the nearest other state on.
  not_derivable.csv        one line per collecting event (per specimen when it has no code), field and
                           reason: no coordinates; coordinates outside the stated country; two candidates;
                           the boundary file's name is not a state in AntWeb; AntWeb's states and the
                           boundary file do not match; no boundary file; country has no states in AntWeb;
                           stays with the province. With the number of specimens and the place text of the
                           record (state, adm2, locality name, locality notes, bioregion).
  mapping_tested.csv       country, level, shape, antweb_state (the cells of a line of adm1_shape_map.csv),
                           what the pair rests on, the state's specimens in the shape, their percent of the
                           state's and of the shape's, what the shape's specimens are filed under today, the
                           unit's ISO 3166-2 code and ne_id.
  to_decide.csv            what a person has to decide: shapes that hold several AntWeb states, AntWeb states
                           that lie in several shapes, shapes named as one state with the specimens of another.
  mapping_suggestions.csv  for the shapes that got no state: the AntWeb states with the most similar names, a
                           score, the records of this run in the shape, what its specimens are filed under.
                           Nothing is used from it.
  report.txt               first a summary of at most 60 lines: the totals for each field, the reasons, the
                           conversion, what the test gave, what is to decide, the states to add, the countries
                           with no level. Then the tables.

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

VERSION = "4"
LEVELS = ["ADM1", "ADM2", "ADM3", "ADM4"]
NE_LEVEL = "NE"                  # the one level of Natural Earth's states and provinces, as the mapping file names it
NE_STATES_NAME = "ne_10m_admin_1_states_provinces.geojson"       # Natural Earth 1:10m, public domain; pinned like the country file
NE_STATES_URL = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/" + cc.SHAPES_COMMIT + "/geojson/" + NE_STATES_NAME
NE_STATES_SHA256 = "22d0e3ad85eb3e27f17cabf8ba2d50e554fbc27a87796ff891d958185da62fb5"       # 40,726,851 bytes, 4,596 units
# The names of a unit of Natural Earth, in the order they are tried as spellings of an AntWeb state
NE_NAMES = ["name", "name_local", "name_en", "name_fr", "name_es", "name_pt", "name_de", "name_it", "name_nl", "name_pl", "name_sv", "name_hu", "name_id",
            "name_tr", "name_vi", "name_ru", "name_uk", "name_el", "name_ar", "name_fa", "name_ur", "name_he", "name_hi", "name_bn", "name_ja", "name_ko",
            "name_zh", "name_zht", "gn_name", "woe_name", "gns_name", "name_alt"]
MIN_SHARE = 0.5                  # the minimum for a level to be used: half of AntWeb's states matched, or half of its shapes
OTHER_KM = (5.0, 25.0)           # how far the nearest other state is looked for, in two steps
MIN_POINTS = 5                   # the test with specimens: at least this many specimens of the state lie in the shape,
MIN_OF_STATE = 0.8               # at least this share of the state's located specimens lie in the shape,
MIN_OF_SHAPE = 0.8               # and at least this share of the shape's specimens that have a state are filed under the state
CONTRADICTED_BELOW = 0.5         # a name match is not used when fewer than this share of the state's specimens lie in the shape
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
STAYS = "no coordinates: stays with the province"
STAYS_OUTSIDE = "coordinates in no region's shape: stays with the province"
TO_ADD = "state to add in the Geolocale Manager first"
SAME_PROVINCE = "the stored province is the province of the region"
OTHER_PROVINCE = "stored province differs from the coordinates' region: check the coordinates"
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

    def valid_of(self, country, name):
        """The valid state a stored name means: itself, or the valid state an old name points to. None otherwise."""
        return self.by_plain.get(country, {}).get(plain(name))

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


def map_lines(path):
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8", newline="") as f:
        return [[cell.strip() for cell in line] for line in csv.reader(row for row in f if not row.lstrip().startswith("#")) if line]


PREDICATE = {"country": "Country", "adm1": "adm1"}                # the predicate of TaxonWorks (a data attribute of the collecting event) for a field


def no_bioregion(value):
    """Geolocale.noBioregion, as in place_fix.py: blank, "none" and "null" mean no bioregion."""
    v = (value or "").strip()
    return "" if v == "" or v.lower() == "none" or v == "null" else v


class Bioregions:
    """The bioregion AntWeb's definition gives for a place, by the rule of place_fix.decide(): the valid state's
    when it has exactly one (a bioregion and no second one), else the country's when it has exactly one, else
    none. rows: (name, georank, parent, is_valid, bioregion, alt_bioregion) of the place rows."""

    def __init__(self, rows, countries, states):
        self.countries, self.states = countries, states
        self.of_country, self.of_state = {}, {}
        for name, georank, parent, is_valid, bioregion, alt in rows:
            if str(is_valid) != "1" or not name:
                continue
            one = "" if no_bioregion(alt) else no_bioregion(bioregion)
            if georank == "adm1":
                country = countries.resolve(parent)
                if country is not None:
                    self.of_state[(country.name, name)] = one
            else:
                self.of_country[name] = one

    def of(self, country, state=""):
        """The bioregion to enter for a country and a state (AntWeb's valid names, or a state to add). A state
        that is an island country of AntWeb (Hawaii) has the island's."""
        if state:
            valid = self.states.valid_of(country, state) or state
            one = self.of_state.get((country, valid), "")
            if not one:
                island = self.countries.resolve(valid)
                if island is not None and island.is_island:
                    one = self.of_country.get(island.name, "")
            if one:
                return one
        return self.of_country.get(country, "")


def read_map(path):
    """The rows of adm1_shape_map.csv that name the state of a shape: [(country, level, shape, AntWeb state)].
    Notes (#), the header and the lines of the other kinds (read_rules) are left out."""
    return [tuple(line[:4]) for line in map_lines(path) if len(line) >= 4 and line[1].upper() in LEVELS + [NE_LEVEL]]


class Rules:
    """The other lines of adm1_shape_map.csv. new: the rows (as read_map gives them) whose state AntWeb does not
    have yet. convert: country -> the coarser units that are converted to the states of the shapes. within:
    country -> plain state name -> the coarser unit it belongs to."""

    def __init__(self, new=(), convert=None, within=None):
        self.new = set(new)
        self.convert = dict(convert or {})
        self.within = {country: {plain(state): unit for state, unit in table.items()} for country, table in (within or {}).items()}


def read_rules(path):
    new, convert, within = set(), {}, collections.defaultdict(dict)
    for line in map_lines(path):
        kind = line[0].lower()
        if kind == "convert" and len(line) >= 3:
            convert.setdefault(line[1], []).extend(cell for cell in line[2:] if cell)
        elif kind == "within" and len(line) >= 4:
            within[line[1]][line[2]] = line[3]
        elif len(line) >= 5 and line[1].upper() in LEVELS + [NE_LEVEL] and line[4].lower() == "new":
            new.add(tuple(line[:4]))
    return Rules(new, convert, within)


ISLAND = "island country"
SPECIMENS = "specimens"                                           # how a shape got its state: AntWeb's specimens in it are filed under it
NEW = "mapping file, a " + TO_ADD                                 # by a row of the mapping file whose state AntWeb does not have yet
BY_FILE = ("mapping file", NEW)


class Evidence:
    """AntWeb's own specimens that have both a state and coordinates, of every upload group: what the shapes are
    tested with. points[valid country] = [(valid state, latitude, longitude, specimens)]."""

    def __init__(self, countries, states):
        self.countries, self.states = countries, states
        self.points = collections.defaultdict(list)
        self.totals = collections.Counter()

    def add(self, country, adm1, lat, lon, count):
        self.totals["specimens with a country, a state and coordinates, not of an island country"] += count
        place = self.countries.resolve(country.strip())
        if place is None:
            self.totals["left out: a country AntWeb does not know"] += count
            return
        state = self.states.valid_of(place.name, adm1.strip())
        if state is None:
            self.totals["left out: a state AntWeb does not know as a state of the country"] += count
            return
        try:
            lat, lon = float(lat), float(lon)
        except ValueError:
            lat = lon = 0.0
        if not cc.usable(lat, lon):
            self.totals["left out: coordinates that cannot be used"] += count
            return
        self.totals["used for the test"] += count
        self.points[place.name].append((sys.intern(state), lat, lon, count))


Unit = collections.namedtuple("Unit", "name spellings code ident", defaults=((), "", ""))     # a shape: its name, other names, ISO 3166-2 code, id


class GeoSource:
    """geoBoundaries gbOpen as the source of state shapes: for each country a file for each level ADM1 to ADM4
    (the simplified files), fetched and checked by coordinate_country.Detail."""
    label = "geoBoundaries"
    levels = LEVELS

    def __init__(self, detail):
        self.detail = detail

    def has(self, code, level):
        return self.detail.file(code, level, SIMPLIFIED) is not None

    def names(self, code, level):
        """The units of a level by their names alone (the lines are not read); None when there is no file."""
        path = self.detail.file(code, level, SIMPLIFIED)
        return None if path is None else [Unit(name) for name in shape_names(path)]

    def shapes(self, code, level):
        """(units, index) of a level: its shapes read, their names put right."""
        features, _, _ = cc.read_features(self.detail.file(code, level, SIMPLIFIED))
        features = [(mend(name), rings) for name, rings in features if rings]
        return [Unit(name) for name, _ in features], cc.index_of_shapes([rings for _, rings in features])

    def where(self, code, level):
        return "geoBoundaries " + code + " " + level

    def entry(self, code, level):
        return code + " " + level + SIMPLIFIED, self.detail.lock["files"].get(code + " " + level + SIMPLIFIED, {})


def fetch_states(folder, url=NE_STATES_URL, sha256=NE_STATES_SHA256):
    """The pinned file of Natural Earth's states and provinces, fetched once into folder. A file with another
    checksum is never used."""
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, NE_STATES_NAME[:-len(".geojson")] + "-" + cc.SHAPES_COMMIT[:12] + ".geojson")
    if os.path.exists(path) and cc.sha256_of(path) == sha256:
        return path
    part = path + ".part"
    with urllib.request.urlopen(url, timeout=300) as r, open(part, "wb") as f:
        for block in iter(lambda: r.read(1 << 20), b""):
            f.write(block)
    found = cc.sha256_of(part)
    if found != sha256:
        os.remove(part)
        sys.exit("The file of states from " + url + " has sha256 " + found + ", expected " + sha256 + ". Nothing was done.")
    os.replace(part, path)
    return path


class NaturalEarthStates:
    """Natural Earth "admin 1 states and provinces" (1:10m, public domain) as the source of state shapes: one
    file for the world, one level. A unit carries its ISO 3166-2 code, its ne_id and its names in many languages;
    every one of them is a spelling when a unit is matched with AntWeb's states (NE_NAMES, in that order).
    The units of a country: those whose adm0_a3 is the country's code, or, when there are none, whose gu_a3 is
    (La Réunion has adm0_a3 FRA and gu_a3 REU). The lines of a unit are read only when its country is asked for."""
    label = "Natural Earth states and provinces 1:10m"
    levels = [NE_LEVEL]

    def __init__(self, path):
        with open(path, encoding="utf-8") as f:
            self.text = f.read()
        self.by_code = collections.defaultdict(list)             # ISO code of a country -> [(Unit, where the feature begins and ends in the text)]
        by_unit = collections.defaultdict(list)
        decoder = json.JSONDecoder()
        at = self.text.index("[", self.text.index('"features"')) + 1
        while True:
            while self.text[at] in " \n\r\t,":
                at += 1
            if self.text[at] == "]":
                break
            feature, end = decoder.raw_decode(self.text, at)
            props = feature.get("properties") or {}
            spellings = []
            for key in NE_NAMES:
                for text in (props.get(key) or "").split("|"):
                    if text.strip() and text.strip() not in spellings:
                        spellings.append(text.strip())
            if spellings and feature.get("geometry"):
                unit = Unit(spellings[0], tuple(spellings[1:]), props.get("iso_3166_2") or "", str(props.get("ne_id") or ""))
                self.by_code[(props.get("adm0_a3") or "").upper()].append((unit, at, end))
                by_unit[(props.get("gu_a3") or "").upper()].append((unit, at, end))
            at = end
        for code, units in by_unit.items():
            if code and code not in self.by_code:
                self.by_code[code] = units

    def has(self, code, level):
        return bool(self.by_code.get(code))

    def names(self, code, level):
        return [unit for unit, _, _ in self.by_code[code]] if self.by_code.get(code) else None

    def shapes(self, code, level):
        decoder = json.JSONDecoder()
        shapes = []
        for unit, at, _ in self.by_code[code]:
            geometry = decoder.raw_decode(self.text, at)[0]["geometry"]
            polygons = geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
            shapes.append([[(p[0], p[1]) for p in ring] for polygon in polygons for ring in polygon if len(ring) >= 3])
        return [unit for unit, _, _ in self.by_code[code]], cc.index_of_shapes(shapes)

    def where(self, code, level):
        return "Natural Earth states " + code

    def entry(self, code, level):
        return "Natural Earth states and provinces", {"licence": "Public domain (Natural Earth)", "source": "naturalearthdata.com"}


class Chosen:
    """The level of the boundary files of one country: its shapes, the AntWeb state of each, the index. usable:
    a point in it may get a proposal. where: what the basis of a proposal calls the shapes; units: each shape's
    other names, ISO 3166-2 code and id, where the source has them."""

    def __init__(self, country, code, level, names, states, hows, index, usable=True, where="", units=None):
        self.country, self.code, self.level, self.names, self.states, self.hows = country, code, level, names, states, hows
        self.index, self.usable = index, usable
        self.where = where or "geoBoundaries " + code + " " + level
        self.units = units or [Unit(name) for name in names]


At = collections.namedtuple("At", "state shape basis reason detail other other_km flag how inside unit", defaults=("", "", "", "", "", "", "", False, None))
Tested = collections.namedtuple("Tested", "stats states hows lines decide today")
SHARES = ["in the unit of their state", "in the unit of another state", "in a unit with no AntWeb state", "in no unit"]


def share(part, whole):
    return part / whole if whole else 0.0


def percent(part, whole):
    return format(100.0 * part / whole, ".0f") if whole else ""


def pick_level(tried):
    """Which level of a country is used when there are no specimens to test with: (its entry in tried, usable,
    why). tried: for each level looked at, coarse to fine, a dict with shapes (0: no file), shape_hits (shapes
    that are an AntWeb state), states (AntWeb's valid states of the country) and state_hits (those of them a
    shape names).

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


def pick_tested(tried):
    """Which level is used when specimens confirmed a state at some level: the one with the most AntWeb states
    confirmed by specimens or matched by a name the specimens do not contradict (the coarser one of two alike)."""
    files = [t for t in tried if t["shapes"]]
    best = max(files, key=lambda t: t["state_hits"])
    return best, True, "the level with the most AntWeb states confirmed by specimens or matched by name"


def level_text(t):
    if not t["shapes"]:
        return t["level"] + " no file"
    n = cc.n
    text = (t["level"] + " " + n(t["state_hits"]) + "/" + n(t["states"]) + " AntWeb states, " + n(t["shape_hits"]) + "/" + n(t["shapes"]) + " shapes"
            + (", " + n(t["islands"]) + (" of them an island country" if t["islands"] == 1 else " of them island countries") + " of AntWeb" if t["islands"] else ""))
    if t.get("located"):
        text += (" [specimens: " + n(t["confirmed"]) + " states confirmed, " + n(t["contradicted"]) + " name matches contradicted, of " + n(t["testable"])
                 + " states with " + str(MIN_POINTS) + " or more located]")
    return text


class StateShapes:
    """For each country: the level of geoBoundaries that holds AntWeb's states, the state of each of its shapes
    (tested with AntWeb's own specimens where there are any), and the state of a point."""

    def __init__(self, detail, states, countries, shape_map, evidence=None, rules=None, source=None):
        self.detail, self.states, self.countries, self.evidence = detail, states, countries, evidence
        self.source = source or GeoSource(detail)                # where the shapes come from: geoBoundaries, or Natural Earth's states
        self.rules = rules or Rules()
        self.map = collections.defaultdict(list)                 # (country, level, plain shape) -> [(AntWeb state, a state to add)], in order
        self.map_rows = shape_map
        for row in shape_map:
            country, level, shape, state = row[:4]
            c = countries.resolve(country)
            self.map[(c.name if c is not None else country, level.upper(), plain(shape))].append((state, tuple(row[:4]) in self.rules.new))
        self.convert, self.within = {}, {}                       # valid country -> the coarser states converted; plain state -> coarser state
        for country, units in self.rules.convert.items():
            c = countries.resolve(country)
            if c is not None:
                self.convert[c.name] = {u for u in (states.valid_of(c.name, unit) for unit in units) if u}
                self.within[c.name] = {state: states.valid_of(c.name, unit) for state, unit in self.rules.within.get(country, {}).items()}
        self.rows_used = set()                                   # (country, level, shape, state) of the rows that named a shape
        self.level_of = {}                                       # country -> the level chosen, once found
        self.tables = {}                                         # country -> what the report prints
        self.problems = {}                                       # country -> why it has no state shapes
        self.files = {}                                          # lock key -> licence, for the report
        self._chosen = collections.OrderedDict()                 # country -> Chosen, the two used last

    def wanted(self, country):
        """AntWeb's valid states of the country that the shapes are compared with: all but the coarser units that
        are being converted (the provinces of Madagascar)."""
        return [x for x in self.states.of(country) if x not in self.convert.get(country, ())]

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
        when AntWeb has the island as a state of the country as well (Hawaii of the United States). A state to
        add is none of AntWeb's yet."""
        return {s for s, h in zip(states, hows) if s and (h not in (ISLAND, NEW) or s in wanted)}

    def match(self, country, level, names, units=None):
        """What to enter as the state for each shape name (None for none) and how it was found, before any test
        with specimens: by the mapping file, as an island country of AntWeb, by the name of a state, by another
        name the source gives the unit (units: Natural Earth's names in other languages and its alternative names)."""
        states, hows = [], []
        skip = self.convert.get(country, ())
        for number, name in enumerate(names):
            state, how = None, ""
            for wanted, new in self.map.get((country, level, plain(name)), ()):
                found, _ = self.states.find(country, wanted)
                if found is not None:
                    state, how = found, "mapping file"
                    break
                if new:
                    state, how = wanted, NEW
                    break
            if state is None:
                island = self.island(country, name)
                if island is not None:
                    state, how = island, ISLAND
            if state is None:
                state, how = self.states.find(country, name)
                if state in skip:                                # a shape with the name of a unit that is being given up
                    state, how = None, ""
            if state is None and units is not None:
                for spelling in units[number].spellings:
                    found, _ = self.states.find(country, spelling)
                    if found is not None and found not in skip:
                        state, how = found, "name " + spelling + " of the unit"
                        break
            states.append(state)
            hows.append(how)
        return states, hows

    def test(self, country, level, names, index, units=None):
        """The state of each shape of a level, tested with AntWeb's own specimens: a Tested.

        Every specimen of the country with a state and coordinates that lies inside exactly one shape of the
        level is counted for (shape, state). Lying inside a shape of the country's own file is what makes the
        point agree with its stored country here. Then, for each shape, in this order:
          - a row of the mapping file wins; an island country of AntWeb stays what it is;
          - "confirmed by specimens": at least MIN_POINTS specimens of a state lie in the shape, they are at
            least MIN_OF_STATE of the state's located specimens, and at least MIN_OF_SHAPE of the shape's
            specimens are filed under the state. Used whatever the two names are, with one exception: a shape
            that has the name of another AntWeb state, when no specimens say that name is wrong (that state
            has fewer than MIN_POINTS located, or not most of them elsewhere). Then neither is used and a
            person decides: five misfiled specimens must not move a state;
          - a name match: not used when the specimens contradict it (the state has MIN_POINTS located specimens
            and fewer than CONTRADICTED_BELOW of them lie in the shape, or the shape is confirmed for another
            state); used and marked "by name only, N specimens" when the state has too few to test; used and
            marked "the specimens agree in part" otherwise.
        The lines for mapping_tested.csv carry the three numbers. decide: shapes that hold several AntWeb states
        and AntWeb states that lie in several shapes, which a person has to decide."""
        n = cc.n
        skip = self.convert.get(country, ())
        wanted = set(self.wanted(country))
        named, named_how = self.match(country, level, names, units)
        ids = lambda i: [units[i].code, units[i].ident] if units is not None else ["", ""]
        tab = collections.defaultdict(collections.Counter)       # shape number -> state -> specimens
        where = collections.defaultdict(collections.Counter)     # state -> shape number -> specimens
        unlocated = 0
        for state, lat, lon, count in (self.evidence.points.get(country, ()) if self.evidence is not None else ()):
            if state in skip:
                continue
            inside = index.inside(lat, lon)
            if len(inside) != 1:
                unlocated += count
                continue
            tab[inside[0]][state] += count
            where[state][inside[0]] += count
        of_state = {state: sum(shapes.values()) for state, shapes in where.items()}
        confirmed = {}
        for i, counts in tab.items():
            state, most = max(counts.items(), key=lambda kv: (kv[1], kv[0]))
            if most >= MIN_POINTS and most >= MIN_OF_STATE * of_state[state] and most >= MIN_OF_SHAPE * sum(counts.values()):
                confirmed[i] = state
        places = lambda state: "; ".join(names[i] + " " + n(c) for i, c in sorted(where[state].items(), key=lambda kv: (-kv[1], names[kv[0]]))[:4])
        states, hows, lines, today, contradicted, conflicts = [], [], [], {}, 0, []
        for i, name in enumerate(names):
            counts = tab.get(i, {})
            total = sum(counts.values())
            filed = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))
            filed = "; ".join(x + " " + n(c) for x, c in filed[:6]) + (" ... and " + n(len(filed) - 6) + " more" if len(filed) > 6 else "")
            line = lambda state, basis: [country, level, name, state, basis, counts.get(state, 0), percent(counts.get(state, 0), of_state.get(state, 0)),
                                         percent(counts.get(state, 0), total), filed] + ids(i)
            state, how = named[i], named_how[i]
            final = (None, "")
            if how in BY_FILE:
                final = (state, how)
                lines.append(line(state, "mapping file" + (": " + TO_ADD if how == NEW else "")))
            elif how == ISLAND:
                final = (state, how)
                lines.append(line(state, "island country of AntWeb"))
            elif i in confirmed:
                if state == confirmed[i]:
                    final = (state, how)
                    lines.append(line(state, "confirmed by specimens, and by the name"))
                elif state and not (of_state.get(state, 0) >= MIN_POINTS and counts.get(state, 0) < CONTRADICTED_BELOW * of_state[state]):
                    # the shape has the name of one AntWeb state and the specimens of another, and nothing says the name is wrong: neither is used
                    conflicts.append([country, level, "shape named as one AntWeb state, its specimens filed under another", name,
                                      "named " + state + " (" + n(of_state.get(state, 0)) + " located specimens); filed under " + filed, total])
                    lines.append(line(confirmed[i], "NOT USED: the specimens say " + confirmed[i] + ", the name says " + state + ": for a person to decide"))
                else:
                    final = (confirmed[i], SPECIMENS)
                    lines.append(line(confirmed[i], "confirmed by specimens"))
                    if state:
                        contradicted += 1
                        lines.append(line(state, "name match NOT USED: contradicted by specimens, those of the state lie in " + places(state)))
            elif state:
                have = of_state.get(state, 0)
                if have >= MIN_POINTS and counts.get(state, 0) < CONTRADICTED_BELOW * have:
                    contradicted += 1
                    lines.append(line(state, "name match NOT USED: contradicted by specimens, those of the state lie in " + places(state)))
                else:
                    final = (state, how)
                    lines.append(line(state, "by name only, " + n(have) + " specimens" if have < MIN_POINTS else "by name; the specimens agree in part"))
            states.append(final[0])
            hows.append(final[1])
            if final[0] is None and filed:
                today[name] = filed
        settled = {x for x in states if x}
        settled_by_test = {confirmed[i] for i in confirmed if states[i] == confirmed[i]}
        decide, inside_mixed = conflicts, 0
        for i, counts in sorted(tab.items(), key=lambda kv: names[kv[0]]):
            several = sorted(((c, x) for x, c in counts.items() if c >= MIN_POINTS), reverse=True)
            if len(several) > 1 and i not in confirmed and hows[i] not in BY_FILE + (ISLAND,):
                decide.append([country, level, "shape holds several AntWeb states", names[i], "; ".join(x + " " + n(c) for c, x in several), sum(counts.values())])
        for state in sorted(where):
            if of_state[state] < MIN_POINTS or state in settled_by_test or state in {x for x, h in zip(states, hows) if h in BY_FILE}:
                continue
            parts = sorted(((c, names[i]) for i, c in where[state].items() if c >= MIN_POINTS), reverse=True)
            if len(parts) > 1:
                decide.append([country, level, "AntWeb state lies in several shapes", state, "; ".join(x + " " + n(c) for c, x in parts), of_state[state]])
            if state not in settled and max(where[state].values()) >= MIN_OF_STATE * of_state[state]:
                inside_mixed += 1                                # the state lies in one shape that is not its own: AntWeb is finer than this level
        stats = {"level": level, "shapes": len(names), "shape_hits": sum(1 for x in states if x), "states": len(wanted),
                 "state_hits": len(self.states_matched(wanted, states, hows)), "islands": sum(1 for h in hows if h == ISLAND),
                 "located": sum(of_state.values()), "unlocated": unlocated, "testable": sum(1 for c in of_state.values() if c >= MIN_POINTS),
                 "confirmed": len(settled_by_test), "contradicted": contradicted, "inside_mixed": inside_mixed}
        # how the specimens lie in the units as they are now named: what the source is worth for AntWeb's states
        shares = collections.Counter({SHARES[3]: unlocated})
        for i, counts in tab.items():
            for state, count in counts.items():
                shares[SHARES[0] if states[i] == state else SHARES[1] if states[i] else SHARES[2]] += count
        stats["shares"] = shares
        return Tested(stats, states, hows, lines, decide, today)

    def look(self, country, code):
        """How each level fits, coarse to fine: (tried, kept). tried is the list pick_level and pick_tested read;
        kept is the (names, index, Tested) of the best level tested with specimens so far, or None.

        With specimens to test with (at least MIN_POINTS of the country, and at least one state with that many
        located at the coarsest level): each level is read and tested. The next finer level is looked at only
        while more AntWeb states lie inside a shape that is not their own (AntWeb is finer than the level) than
        are settled at the level. Without: only the names of a level are read, as before, until one fits by
        both halves."""
        wanted = set(self.wanted(country))
        have = sum(p[3] for p in self.evidence.points.get(country, ()) if p[0] not in self.convert.get(country, ())) if self.evidence is not None else 0
        testing = have >= MIN_POINTS
        tried, kept = [], None
        for level in self.source.levels:
            if not self.source.has(code, level):
                tried.append({"level": level, "shapes": 0})
                continue
            if testing:
                units, index = self.source.shapes(code, level)
                tested = self.test(country, level, [u.name for u in units], index, units)
                t = tested.stats
                tried.append(t)
                if kept is None or t["state_hits"] > kept[2].stats["state_hits"]:
                    kept = (units, index, tested)
                if t["testable"] == 0 and len([x for x in tried if x["shapes"]]) == 1:
                    testing = False                              # nothing to test with here: go on by the names alone
                elif t["inside_mixed"] <= t["state_hits"]:
                    break
                else:
                    continue
            else:
                units = self.source.names(code, level)
                names = [u.name for u in units]
                states, hows = self.match(country, level, names, units)
                tried.append({"level": level, "shapes": len(names), "shape_hits": sum(1 for x in states if x), "states": len(wanted),
                              "state_hits": len(self.states_matched(wanted, states, hows)), "islands": sum(1 for h in hows if h == ISLAND)})
            t = tried[-1]
            if share(t["state_hits"], t["states"]) >= MIN_SHARE and share(t["shape_hits"], t["shapes"]) >= MIN_SHARE:
                break
        return tried, kept

    def choose(self, country, code):
        """The Chosen level of a country, or None (self.problems says why). A level that is not usable is given
        too: its shapes say where a point is, for what a person has to decide."""
        if country in self._chosen:
            self._chosen.move_to_end(country)
            return self._chosen[country]
        if country in self.problems:
            return None
        if not code:
            self.problems[country] = "AntWeb has no ISO code for the country and no Natural Earth shape gives one"
            return None
        try:
            if country in self.tables:                           # chosen before, dropped to make room: read again
                t = self.tables[country]
                units, index = self.source.shapes(code, t["level"])
            else:                                                # the first time: which level, the state of each shape, what the report says
                tried, kept = self.look(country, code)
                if any(x.get("confirmed") for x in tried):
                    picked, usable, why = pick_tested(tried)
                else:
                    picked, usable, why = pick_level(tried)
                if picked is None:
                    self.problems[country] = ("geoBoundaries has no file of the divisions of " if self.source.label == "geoBoundaries" else
                                              self.source.label + " has no units of ") + code
                    return None
                level = picked["level"]
                if kept is not None and kept[2].stats["level"] == level:
                    units, index, tested = kept
                else:
                    units, index = self.source.shapes(code, level)
                    tested = self.test(country, level, [u.name for u in units], index, units)
                    usable = usable and any(tested.states)       # the specimens may have contradicted every name
                kept = None
                names = [u.name for u in units]
                wanted = self.wanted(country)
                key, entry = self.source.entry(code, level)
                self.files[key] = entry
                states, hows = tested.states, tested.hows
                for name, state, how in zip(names, states, hows):
                    if how in BY_FILE:
                        self.rows_used.add((country, level, name, state))
                got = self.states_matched(wanted, states, hows)
                shape_of = {}
                for name, state in zip(names, states):
                    if state:
                        shape_of.setdefault(state, name)
                t = self.tables[country] = {
                    "code": code, "level": level, "tried": tried, "usable": usable, "why": why, "states": states, "hows": hows,
                    "where": self.source.where(code, level), "levels": list(self.source.levels),
                    "lines": tested.lines, "decide": tested.decide, "today": tested.today, "stats": tested.stats,
                    "matched": sorted(got), "not_matched": [x for x in wanted if x not in got], "shape_of": shape_of,
                    "shapes_not_states": sorted({n for n, x in zip(names, states) if not x}),
                    "islands": sorted(n + (" (" + x + ")" if x != n else "") for n, x, h in zip(names, states, hows) if h == ISLAND),
                    "to_add": sorted({x for x, h in zip(states, hows) if h == NEW}),
                    "other_way": sorted(n + " -> " + x + " (" + h + ")" for n, x, h in zip(names, states, hows) if x and h not in ("name", ISLAND)),
                    "licence": entry.get("licence", ""), "source": entry.get("source", "")}
                self.level_of[country] = level
        except (urllib.error.URLError, OSError) as e:
            self.problems[country] = "a boundary file of " + code + " could not be fetched (" + str(getattr(e, "reason", e)) + ")"
            return None
        chosen = Chosen(country, code, t["level"], [u.name for u in units], t["states"], t["hows"], index, t["usable"], t["where"], units)
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
        where = chosen.where
        if chosen.hows[first] == ISLAND:
            where += "; " + files_as_island(chosen.country, chosen.states[first])
        elif chosen.hows[first] != "name":
            name += " (" + chosen.states[first] + " by the " + chosen.hows[first] + ")"
        basis = ("coordinates inside " if inside else "in the water, " + cc.rounded(near[first]) + " km from ") + name + ", " + where
        return At(chosen.states[first], chosen.names[first], basis, "", "", other, other_km, flag, chosen.hows[first], bool(inside), chosen.units[first])


def files_as_island(country, island):
    return "AntWeb files this under " + country + " as the island " + island


# ---------------------------------------------------------------- one specimen

Row = collections.namedtuple("Row", "code event locality_code country adm1 adm2 island lat lon stated locality notes bioregion group")
Proposal = collections.namedtuple("Proposal", "field current proposed basis country other other_km flag note code ident bioregion",
                                  defaults=("", "", "", "", "", "", ""))
Not = collections.namedtuple("Not", "field reason detail country", defaults=("",))


class Corrections:
    """The rule: what is proposed for one record and why not."""

    def __init__(self, checker, states, shapes, bioregions=None):
        self.checker, self.states, self.shapes, self.bioregions = checker, states, shapes, bioregions
        self.countries = checker.countries
        self.kinds = collections.Counter()                       # what the records looked at were
        self._memo = {}

    def work(self, row):
        """The proposals and the reasons for one record: a list of Proposal and Not."""
        key = (row.country, row.adm1, row.island == "", row.lat, row.lon, row.stated)
        if key not in self._memo:
            if len(self._memo) > 200000:
                self._memo.clear()
            self._memo[key] = self.with_bioregion(row, self._work(row))
        return self._memo[key]

    def with_bioregion(self, row, results):
        """Each proposal with the bioregion AntWeb's definition gives for the place as it will be: the proposed
        country with the state the record has or gets; the proposed state in its country."""
        if self.bioregions is None:
            return results
        state = next((r.proposed for r in results if isinstance(r, Proposal) and r.field == "adm1"), row.adm1)
        return [r if not isinstance(r, Proposal) else r._replace(bioregion=self.bioregions.of(r.country, state if r.field == "country" else r.proposed))
                for r in results]

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

    def converting(self, row, country):
        """The coarser unit (a province of Madagascar) stored as the state of a record, when the mapping file says
        that the country's coarser units are converted to the states of the shapes; None otherwise."""
        units = self.shapes.convert.get(country)
        if not units or not row.adm1 or row.island:
            return None
        stored = self.states.valid_of(country, row.adm1)
        return stored if stored in units else None

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
        converting = self.converting(row, country) if stated_country is not None else None
        if (row.adm1 and converting is None) or row.island or country is None:
            return out
        if island_state is not None:
            return out + [island_state]
        if not self.states.of(country):
            return out + [Not("adm1", NO_STATES, country, country)]
        if point is None:
            return out + [Not("adm1", STAYS if converting else NO_COORDINATES, "")]
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
            return out + [Not("adm1", STAYS_OUTSIDE if converting and at.reason in (NO_SHAPE_HERE, TWO) else at.reason, at.detail, country)]
        note = TO_ADD if at.how == NEW else ""
        if converting:                                           # from the province to the region the coordinates lie in
            if not at.inside:
                return out + [Not("adm1", STAYS_OUTSIDE, at.basis, country)]
            if at.state == converting:
                return out
            belongs = self.shapes.within.get(country, {}).get(plain(at.state))
            if belongs is None:
                check = "the mapping file does not say which province " + at.state + " is in"
            else:
                check = SAME_PROVINCE if belongs == converting else OTHER_PROVINCE + " (" + at.state + " is in " + belongs + ")"
            note = check + ("; " + note if note else "")
        return out + [Proposal("adm1", row.adm1, at.state, at.basis, country, at.other, at.other_km, at.flag, note, at.unit.code, at.unit.ident)]

    def kind(self, row):
        """Which of the three lists of Brian Fisher's note a record belongs to (for the totals of the report)."""
        if not row.country:
            return "country missing"
        valid = self.countries.resolve(row.country)
        if valid is None:
            return "country not valid in AntWeb"
        if row.adm1:
            return "state is a province to convert to its region" if self.converting(row, valid.name) else "has a state (left as it is)"
        if row.island:
            return "state missing, island-country specimen (left out)"
        return "state missing"


# ---------------------------------------------------------------- the lists

CORRECTIONS_HEADER = ["specimen_code", "collecting_event_code", "locality_code", "field", "current_value", "proposed_value", "basis",
                      "nearest_other_state", "km_to_other_state", "other_state_flag", "note",
                      "latitude", "longitude", "stated_error", "locality_name", "country", "upload_group", "unit_iso_3166_2", "unit_ne_id",
                      "taxonworks_predicate", "biogeographic_region"]
EVENTS_HEADER = ["collecting_event_code", "field", "current_value", "proposed_value", "specimens", "first_specimen_code", "last_specimen_code",
                 "locality_name", "latitude", "longitude", "flag", "nearest_other_state", "km_to_other_state", "other_state_flag", "note",
                 "country", "basis", "upload_group", "unit_iso_3166_2", "unit_ne_id", "taxonworks_predicate", "biogeographic_region"]
# the first four of the next two: the cells of a line of adm1_shape_map.csv
TESTED_HEADER = ["country", "level", "shape", "antweb_state", "basis", "specimens_of_the_state_in_the_shape", "percent_of_the_state",
                 "percent_of_the_shape", "the_shape's_specimens_are_filed_under", "unit_iso_3166_2", "unit_ne_id"]
SUGGESTIONS_HEADER = ["country", "level", "shape", "antweb_state", "score", "specimens", "note", "the_shape's_specimens_are_filed_under"]
DECIDE_HEADER = ["country", "level", "what", "name", "parts", "specimens"]
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
                          [row.code, row.event, row.locality_code, p.field, p.current, p.proposed, p.basis, p.other, p.other_km, p.flag, p.note,
                           row.lat, row.lon, row.stated, row.locality, p.country, self.group(row), p.code, p.ident, PREDICATE.get(p.field, ""), p.bioregion]))
        return [line for _, line in sorted(lines, key=lambda x: x[0])]

    def events(self):
        """One line per collecting event, field and current value. A collecting event whose specimens get different
        proposals is flagged."""
        by_event = collections.defaultdict(list)
        for row, p in self.proposals:
            by_event[(row.event or "\0" + row.code, p.field, p.current)].append((row, p))
        more = collections.Counter((row.event, n.field) for row, n in self.nots if row.event)
        several = lambda texts, of: "" if not texts else (texts.most_common(1)[0][0] if sum(texts.values()) == of and len(texts) == 1 else
                                                           cc.n(texts.most_common(1)[0][1]) + " of " + cc.n(of) + " specimens: " + texts.most_common(1)[0][0])
        lines = []
        for (event, field, current), items in by_event.items():
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
            lines.append(((p.country, " | ".join(sorted(values)), row.event, field != "country", current),
                          [row.event, field, p.current, " | ".join(sorted(values)), len(items), items[0][0].code, items[-1][0].code,
                           row.locality, row.lat, row.lon, ". ".join(flags), p.other if len(values) == 1 else "", p.other_km if len(values) == 1 else "", near,
                           several(collections.Counter(x.note for _, x in items if x.note), len(items)),
                           p.country, p.basis if len(values) == 1 else "", self.group(row), p.code if len(values) == 1 else "", p.ident if len(values) == 1 else "",
                           PREDICATE.get(field, ""), p.bioregion if len({x.bioregion for _, x in items}) == 1 else ""]))
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
    """What is left after the test with specimens: (lines of mapping_suggestions.csv, shapes left out). For each
    shape of a country's level that got no state: the AntWeb valid states of that country whose name, or an old
    name of which, is most like it, the records of this run that lie in it, and what AntWeb's specimens in it
    are filed under today. A shape is listed when records lie in it or a state is like it. Nothing is used from
    this list by the tool."""
    lines, left_out = [], 0
    for country in sorted(shapes.tables):
        t = shapes.tables[country]
        for shape in t["shapes_not_states"]:
            found = states.closest(country, shape)
            found = [x for x in found if x[1] not in shapes.convert.get(country, ())]
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
                              [country, t["level"], shape, state, format(score, ".2f") if state else "", specimens, "; ".join(notes), t["today"].get(shape, "")]))
    return [line for _, line in sorted(lines, key=lambda x: x[0])], left_out


def tested_lines(shapes):
    """The lines of mapping_tested.csv: for each country, the state of each shape of the level used and what it
    rests on, with the numbers of the test."""
    return [line for country in sorted(shapes.tables) for line in sorted(shapes.tables[country]["lines"], key=lambda x: (x[2], x[4]))]


def decide_lines(shapes):
    """The lines of to_decide.csv: shapes that hold several AntWeb states, AntWeb states that lie in several shapes."""
    return [line for country in sorted(shapes.tables) for line in shapes.tables[country]["decide"]]


def by_region(index, names, province_of, region_of, states, rows, tolerance):
    """Madagascar by region. rows: (stored state, latitude, longitude, specimens) of every record of Madagascar.
    For each region (shape of the boundary file): the specimens with coordinates inside it (or in the water
    within the tolerance of it alone), and of them how many have as their stored state the province the region
    belongs to (province_of), the region itself (region_of: its AntWeb name), another state, or none.
    Returns (table {shape name: [specimens, that province, the region, another state, no state]}, totals Counter)."""
    table = {name: [0, 0, 0, 0, 0] for name in names}
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
        stored = states.valid_of(MADAGASCAR, adm1) if adm1 else None
        if not adm1:
            line[4] += count
        elif stored is not None and stored == province_of.get(region):
            line[1] += count
        elif stored is not None and stored == region_of.get(region):
            line[2] += count
        else:
            line[3] += count
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
    rest = [level for level in t.get("levels", LEVELS) if level not in looked]
    return ("  " + country + " (" + t["code"] + "): " + "; ".join(level_text(x) for x in t["tried"])
            + ("; " + (rest[0] if len(rest) == 1 else rest[0] + " to " + rest[-1]) + " not looked at" if rest else "")
            + (" | used: " if t["usable"] else " | NONE USED; a mapping would be for ") + t["level"] + " (" + t["why"] + ")")


def compared(sources):
    """The sources of state shapes compared on AntWeb's own specimens: the lines of the report. sources:
    [(label, StateShapes)] after each has chosen its units for the countries. For each country and source:
    the specimens that have a state and coordinates, and the share of them that lie in the unit confirmed or
    matched for their state (agreement), in the unit of another state, in a unit with no AntWeb state, in no
    unit (the coast, or a country the source has no shapes for)."""
    n = cc.n
    pct = lambda part, whole: (format(100.0 * part / whole, ".1f") if whole else "").rjust(7)
    countries = sorted({c for _, shapes in sources for c in list(shapes.tables) + list(shapes.problems)})
    rows, totals = [], [collections.Counter() for _ in sources]
    for country in countries:
        cells, most = [], 0
        for k, (label, shapes) in enumerate(sources):
            t = shapes.tables.get(country)
            if t is None:
                have = sum(x[3] for x in shapes.evidence.points.get(country, ()) if x[0] not in shapes.convert.get(country, ())) if shapes.evidence is not None else 0
                counts, level = collections.Counter({SHARES[3]: have}), "none"
            else:
                counts, level = t["stats"]["shares"], t["level"] + ("" if t["usable"] else "*")
            whole = sum(counts.values())
            most = max(most, whole)
            totals[k].update(counts)
            cells.append(level.ljust(6) + "".join(pct(counts[x], whole) for x in SHARES))
        rows.append((most, country, cells))
    width = 6 + 7 * len(SHARES)
    out = ["", "Sources compared. AntWeb's specimens that have a state and coordinates (of every group; units being converted, such as the provinces of Madagascar, left",
           "out), by where they lie in the units of each source, in percent: in the unit confirmed or matched for their state (agreement), in the unit of",
           "another state, in a unit with no AntWeb state, in no unit (the coast, or no shapes for the country). level*: the country gives no proposals.",
           "  " + "country".ljust(28) + "specimens".rjust(10) + "".join("  | " + label[:width].ljust(width) for label, _ in sources),
           "  " + "".ljust(38) + "".join("  | " + "level".ljust(6) + "  agree  other no st.no unit" for _ in sources),
           "  " + "ALL COUNTRIES".ljust(28) + n(max(sum(t.values()) for t in totals) if totals else 0).rjust(10)
           + "".join("  | " + "".ljust(6) + "".join(pct(t[x], sum(t.values())) for x in SHARES) for t in totals)]
    for most, country, cells in sorted(rows, key=lambda r: (-r[0], r[1])):
        out.append("  " + country[:28].ljust(28) + n(most).rjust(10) + "".join("  | " + cell for cell in cells))
    return out, totals


def region_lines(table, totals, province_of, code, group_text):
    """The lines of the report for Madagascar by region (by_region)."""
    n = cc.n
    out = ["", "Madagascar by region: every record of " + MADAGASCAR + " of " + ("every group" if group_text == "every group" else "the group")
           + " by the region its coordinates lie in, not only the records with a gap; " + code + ", " + n(len(table)) + " shapes.",
           "  Records of Madagascar: " + n(totals["specimens"]) + ". With coordinates in one region: " + n(totals["in a region"])
           + ". No coordinates: " + n(totals["no coordinates"]) + ". Coordinates that cannot be used: " + n(totals["coordinates that cannot be used"])
           + ". In no region's shape (in the water or outside): " + n(totals["in no region's shape"])
           + ". On the line between two regions: " + n(totals["on the line between two regions"]) + ".",
           "  " + "region".ljust(26) + "specimens".rjust(10) + "  " + "its province".ljust(25) + "stored state: that province".rjust(28) + "the region".rjust(12)
           + "another".rjust(10) + "none".rjust(10)]
    for region in sorted(table):
        inside, same, itself, another, none = table[region]
        out.append("  " + region.ljust(26) + n(inside).rjust(10) + "  " + (province_of.get(region) or "not in the mapping file").ljust(25)
                   + n(same).rjust(28) + n(itself).rjust(12) + n(another).rjust(10) + n(none).rjust(10))
    return out


def report_text(when, commit, group_text, lists, shapes, detail, seconds, tolerance, map_path=MAP_FILE, suggested=None, madagascar=None, evidence=None,
                sources=None, costs=()):
    """The report: a summary of at most SUMMARY_LINES lines (totals, reasons, what the test with specimens gave,
    what a person has to decide), then the long tables. suggested: (lines of mapping_suggestions.csv, shapes left
    out of it); madagascar: the lines of region_lines, when that table was made; evidence: the Evidence read;
    sources: [(label, StateShapes)] to compare (the first is shapes); costs: lines on time and memory."""
    n = cc.n
    events = lists.events()
    out = ["AntWeb place corrections, " + when + " (tool version " + VERSION + ", AntWeb commit " + (commit or "unknown") + ")",
           "Upload group: " + group_text,
           "Summary: totals, reasons, the test with specimens, what a person has to decide. The tables follow under \"Details\".", "",
           "Records looked at (no state, no country, a country AntWeb does not know, a province to convert): " + n(sum(lists.looked.values()))]
    out += top(lists.looked, 5)
    values = {}
    # (field, label, most reasons shown, converting: the records whose stored state is a province to convert)
    blocks = (("country", "Country", 5, None), ("adm1", "State (adm1)", 9, False), ("adm1", "State (adm1), from a province to its region", 4, True))
    for field, label, limit, converting in blocks:
        proposals = [(row, p) for row, p in lists.proposals if p.field == field and (converting is None or bool(p.current) == converting)]
        nots = collections.Counter()
        for row, x in lists.nots:
            if x.field == field and (converting is None or bool(row.adm1) == converting):
                nots[x.reason] += 1
        lines = [e for e in events if e[1] == field and (converting is None or bool(e[2]) == converting)]
        if converting and not proposals and not nots:
            continue
        different = sum(1 for e in lines if e[10].startswith("DIFFERENT"))
        out += ["", label + ": " + n(len(proposals)) + " specimens with a proposal (corrections.csv), in " + n(len(lines)) + " lines of by_collecting_event.csv"
                + (", " + n(different) + " of them flagged for different proposals" if different else "")]
        if field == "adm1":
            flagged = sum(1 for _, p in proposals if p.flag)
            out += ["  " + n(flagged) + " of them (" + n(sum(1 for e in lines if e[13])) + " lines) lie within the stated error, or the allowed " + format(tolerance, "g")
                    + " km, of another state: column other_state_flag, look at those first."]
        if converting:
            other = collections.Counter(p.proposed for _, p in proposals if p.note.startswith(OTHER_PROVINCE))
            out += ["  " + n(sum(other.values())) + " of them: " + OTHER_PROVINCE + " (column note)"
                    + (". By region: " + "; ".join(k + " " + n(c) for k, c in sorted(other.items(), key=lambda kv: (-kv[1], kv[0]))[:8]) if other else "") + "."]
        out += ["  No proposal (not_derivable.csv): " + n(sum(nots.values())) + " specimens, by reason:"] + top(nots, limit)
        if not converting:
            values[field] = collections.Counter((p.country + ": " + p.proposed) if field == "adm1" else p.proposed for _, p in proposals)
        else:
            values["convert"] = collections.Counter(p.country + ": " + p.current + " -> " + p.proposed for _, p in proposals)
    tested = tested_lines(shapes)
    kinds = collections.Counter("contradicted" if x[4].startswith("name match NOT USED") else x[4].split(",")[0].split(";")[0].split(":")[0] for x in tested)   # "NOT USED": to decide
    decide = decide_lines(shapes)
    used = evidence.totals["used for the test"] if evidence is not None else 0
    out += ["", "The shapes and AntWeb's states (mapping_tested.csv), tested with AntWeb's own specimens that have a state and coordinates (" + n(used) + " of every group):",
            "  " + n(kinds["confirmed by specimens"]) + " shapes confirmed by specimens, " + n(kinds["by name only"]) + " by name only, "
            + n(kinds["by name"]) + " by name with the specimens agreeing in part, " + n(kinds["mapping file"]) + " by the mapping file, "
            + n(kinds["island country of AntWeb"]) + " island countries; " + n(kinds["contradicted"]) + " name matches contradicted by specimens and not used.",
            "For a person to decide (to_decide.csv): " + n(sum(1 for x in decide if x[2].startswith("shape holds"))) + " shapes that hold several AntWeb states, "
            + n(sum(1 for x in decide if x[2].startswith("AntWeb"))) + " AntWeb states that lie in several shapes, "
            + n(sum(1 for x in decide if x[2].startswith("shape named"))) + " shapes named as one state with the specimens of another."]
    adding = collections.Counter({c + ": " + x: 0 for c, t in shapes.tables.items() for x in t["to_add"]})
    for row, p in lists.proposals:
        if p.field == "adm1" and TO_ADD in p.note:
            adding[p.country + ": " + p.proposed] += 1
    if adding:
        out += ["States to add in the Geolocale Manager first (proposed for the specimens in brackets): "
                + "; ".join(k + " (" + n(c) + ")" for k, c in sorted(adding.items())[:6]) + ("; and more, see Details" if len(adding) > 6 else "")]
    comparison = compared(sources) if sources else None
    if comparison is not None:
        out += ["Sources compared, the specimens that lie in the unit of their state: " + "; ".join(
            label + " " + (format(100.0 * t[SHARES[0]] / sum(t.values()), ".1f") if sum(t.values()) else "0") + "% of " + n(sum(t.values()))
            for (label, _), t in zip(sources, comparison[1])) + " (table under Details)."]
    looked = {x.country for _, x in lists.nots} | {p.country for _, p in lists.proposals}
    below = collections.Counter({c + " " + t["level"]: 0 for c, t in shapes.tables.items() if not t["usable"] and (sources is None or len(sources) < 2 or c in looked)})
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
    for field, label in (("country", "Country"), ("adm1", "State (adm1)"), ("convert", "State (adm1), from a province to its region")):
        if field in values:
            out += [label + ", proposed values (the 40 largest):"] + cc.lines_of(values[field], str, limit=40) + [""]
    out += ["The test with specimens. A shape is a state \"confirmed by specimens\" when at least " + str(MIN_POINTS) + " specimens of the state lie in the shape, they are",
            "at least " + format(100 * MIN_OF_STATE, ".0f") + "% of the state's specimens that lie in any shape of the level, and at least " + format(100 * MIN_OF_SHAPE, ".0f")
            + "% of the shape's specimens that have a state are filed under it.",
            "A name match is not used when the state has " + str(MIN_POINTS) + " or more located specimens and fewer than " + format(100 * CONTRADICTED_BELOW, ".0f")
            + "% of them lie in the shape. mapping_tested.csv has the numbers of each pair.",
            "A specimen counts when it lies inside exactly one shape of the level of its stored country's file; the units being converted (provinces) are left out."]
    if evidence is not None:
        out += cc.lines_of(evidence.totals, str)
    out += ["", "For a person to decide (to_decide.csv; the 60 with the most specimens):"]
    out += ["  " + n(x[5]).rjust(9) + "  " + x[0] + " " + x[1] + ": " + ("the shape " + x[3] + " is " if x[2].startswith("shape named") else
                                                                    "the shape " + x[3] + " holds " if x[2].startswith("shape") else "AntWeb's " + x[3] + " lies in ") + x[4]
            for x in sorted(decide, key=lambda x: (-x[5], x[0], x[3]))[:60]] or ["  none"]
    if adding:
        out += ["", "States to add in the Geolocale Manager first (a row of the mapping file marked new), with the specimens they are proposed for:"]
        out += cc.lines_of(adding, str)
    out += [""]
    names = collections.Counter()
    for row, x in lists.nots:
        if x.reason == NOT_A_STATE:
            names[(x.country, x.detail)] += 1
    out += ["For the Geolocale Manager: names of the boundary files that are no state in AntWeb, with the specimens that lie in them:"]
    out += cc.lines_of(names, lambda k: k[0] + ": " + k[1], limit=200)
    out += ["", "The level of the boundary files, for each country whose records were looked up in them (" + n(len(shapes.tables)) + " countries). At each level:",
            "AntWeb's valid states of the country that a shape names / all of them, and the shapes that are an AntWeb state / all shapes of the level.",
            "Where specimens confirmed a state: the level with the most AntWeb states confirmed by specimens or matched by name (the coarser one of two alike).",
            "Where there are none to test with: the level with the largest share of shapes that are AntWeb states (the coarser one of two alike), when at least half of",
            "its shapes are AntWeb states or, for the coarsest level only, at least half of AntWeb's states are matched. Below that: no proposal (NONE USED).",
            "A country whose records all lack coordinates, or lie outside it, is not in this list."]
    out += [level_line(country, shapes.tables[country]) for country in sorted(shapes.tables)]
    out += ["", "How the names fit at the level named above, country by country:"]
    for country in sorted(shapes.tables):
        t = shapes.tables[country]
        if "matched" not in t:
            continue
        out += ["  " + country + ": " + t["where"] + ("" if t["usable"] else " (NOT USED: below the minimum)"),
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
        lines, left_out = suggested
        out += ["", "mapping_suggestions.csv: what the test could not settle. " + n(len(lines)) + " lines for " + n(len({(x[0], x[2]) for x in lines}))
                + " shapes that got no state, each with the AntWeb states of its",
                "country whose name is most like it (score 1: the same once words such as Province or Region are left out), the records of this run that lie in",
                "the shape, and what AntWeb's specimens in it are filed under. Nothing is used from it by the tool. " + n(left_out) + " more shapes have no records",
                "and no AntWeb state like them, and are not listed."]
    out += madagascar or []
    if comparison is not None:
        out += comparison[0]
    if evidence is not None and getattr(evidence, "cost", ""):
        out += ["", evidence.cost]
    out += list(costs)
    by_licence = collections.defaultdict(list)
    for key, entry in shapes.files.items():
        by_licence[entry.get("licence", "not read")].append(key)
    out += ["", "State shapes: " + ("geoBoundaries gbOpen, the simplified files, commit " + detail.commit + ", in " + detail.folder + ". Checksums: " + detail.lock_path
                                    if shapes.source.label == "geoBoundaries" else
                                    shapes.source.label + ", " + NE_STATES_NAME + " at commit " + cc.SHAPES_COMMIT + " of nvkelso/natural-earth-vector, sha256 " + NE_STATES_SHA256),
            "  Licences:"] + ["    " + lic + ": " + "; ".join(sorted(keys)) for lic, keys in sorted(by_licence.items())]
    countries_used = sorted(iso for iso, f in detail.used.items() if f.get("bytes") and not f.get("problem"))
    out += ["Country shapes: Natural Earth 1:10m" + ("; near a border the full geoBoundaries file of the country: " + " ".join(countries_used) if countries_used else " alone")
            + ". The allowed distance: " + format(tolerance, "g") + " km, or the specimen's stated error when larger."]
    out += ["", "Run time: " + seconds, "", "Report only: nothing was changed in the database."]
    return "\n".join(out) + "\n"


# ---------------------------------------------------------------- the database (read only)

def places_sql(columns):
    """The state rows and the country rows of geolocale, with their bioregions when the table has those columns:
    name, parent, is_valid, valid_name, georank, bioregion, alt_bioregion."""
    bioregion = "hex(ifnull(bioregion, ''))" if "bioregion" in columns else "''"
    alt = "hex(ifnull(alt_bioregion, ''))" if "alt_bioregion" in columns else "''"
    return ("select hex(name), hex(ifnull(parent, '')), is_valid, hex(ifnull(valid_name, '')), georank, " + bioregion + ", " + alt
            + " from geolocale where georank in ('adm1', 'country') and name is not null")


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
    source, level = state_shapes.source, state_shapes.source.levels[0]
    if not source.has(code, level):
        return ["", "Madagascar by region: " + source.label + " has no shapes of " + code + "."]
    units, index = source.shapes(code, level)
    names = [u.name for u in units]
    regions, _ = state_shapes.match(MADAGASCAR, level, names, units)             # AntWeb's name of each region
    within = state_shapes.within.get(MADAGASCAR, {})
    province_of = {name: within.get(plain(region)) for name, region in zip(names, regions) if region}
    table, totals = by_region(index, names, province_of, dict(zip(names, regions)), states, rows, tolerance)
    return region_lines(table, totals, province_of, source.where(code, level), group_text)


def evidence_sql(columns):
    """AntWeb's specimens of every upload group that have a country, a state and coordinates and are no island-
    country specimens, counted by country, state and place: what the shapes are tested with."""
    island = " and (s.island_country is null or s.island_country = '')" if "island_country" in columns else ""
    return ("select hex(s.country), hex(s.adm1), round(s.decimal_latitude, 5), round(s.decimal_longitude, 5), count(*) from specimen s"
            " where s.country is not null and s.country <> '' and s.adm1 is not null and s.adm1 <> ''"
            " and s.decimal_latitude is not null and s.decimal_longitude is not null" + island + " group by 1, 2, 3, 4")


def rows_sql(columns, group_id=None, unknown=(), convert=()):
    """The records with a gap: no state, no country, or a country AntWeb does not know (unknown: those names); and
    the records whose state is a coarser unit that is converted (convert: [(the stored spellings of the country,
    the names of its coarser units)], Madagascar and its provinces)."""
    text = lambda name: "hex(ifnull(s." + name + ", ''))" if name in columns else "''"
    number = lambda name: "ifnull(round(s." + name + ", 5), '')"
    hexes = lambda names: ", ".join(cc.sql_string(u.encode("utf-8").hex().upper()) for u in names)
    gap = "s.adm1 is null or s.adm1 = '' or s.country is null or s.country = ''"
    if unknown:
        gap += " or hex(s.country) in (" + hexes(unknown) + ")"
    for spellings, units in convert:
        if spellings and units:
            gap += " or (hex(s.country) in (" + hexes(spellings) + ") and s.adm1 in (" + ", ".join(cc.sql_string(u) for u in units) + "))"
    return ("select " + ", ".join([text("code"), text("collectioncode"), text("localitycode"), text("country"), text("adm1"), text("adm2"),
                                   text("island_country"), number("decimal_latitude"), number("decimal_longitude"), text("latlonmaxerror"),
                                   text("localityname"), text("localitynotes"), text("bioregion"), "ifnull(s.access_group, 0)"])
            + " from specimen s where 1 = 1" + group_sql(group_id) + " and (" + gap + ") order by s.country, s.code")


def read_row(r):
    u = cc.unhex
    number = lambda text: str(float(text)) if text not in ("", "NULL") else ""       # 54.01950 and 54.0195 are one place
    i = sys.intern                                               # the records of a collecting event share most of their text: kept once
    return Row(u(r[0]), i(u(r[1])), i(u(r[2])), i(u(r[3]).strip()), i(u(r[4]).strip()), i(u(r[5])), i(u(r[6]).strip()), i(number(r[7])), i(number(r[8])),
               i(u(r[9])), i(u(r[10])), i(u(r[11])), i(u(r[12])), i(r[13]))


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
    a.add_argument("--states-source", choices=["geoboundaries", "naturalearth", "both"], default="geoboundaries",
                   help="the state shapes: geoboundaries (default), naturalearth (Natural Earth's states and provinces), or both: the corrections by "
                        "geoboundaries, and the two compared on AntWeb's specimens for every country")
    a.add_argument("--no-specimen-test", action="store_true", help="do not read AntWeb's specimens to test the shapes with: by the names and the mapping file alone")
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

    place_columns = {r[0] for r in sql(cc.columns_sql("geolocale"))}
    countries = cc.read_countries(sql(cc.countries_sql(place_columns)))
    places = [(cc.unhex(r[0]), cc.unhex(r[1]), r[2], cc.unhex(r[3]), r[4], cc.unhex(r[5]), cc.unhex(r[6])) for r in sql(places_sql(place_columns))]
    states = States([x[:4] for x in places if x[4] == "adm1"], countries)
    bioregions = Bioregions([(x[0], x[4], x[1], x[2], x[5], x[6]) for x in places], countries, states)
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
    columns = {r[0] for r in sql(cc.columns_sql("specimen"))}
    evidence = None
    if not args.no_specimen_test:                                # one more read: what the shapes are tested with
        evidence, began, lines = Evidence(countries, states), time.time(), 0
        for r in cc.rows_sql(project, evidence_sql(columns), args.container):
            evidence.add(cc.unhex(r[0]), cc.unhex(r[1]), r[2], r[3], int(r[4]))
            lines += 1
        evidence.cost = ("The read of AntWeb's specimens with a state and coordinates: " + cc.n(lines) + " lines (country, state, place) in "
                         + format(time.time() - began, ".0f") + " seconds; memory after it " + cc.n(cc.peak_mb()) + " MB.")
    natural = NaturalEarthStates(fetch_states(args.shapes_folder)) if args.states_source != "geoboundaries" else None
    state_shapes = StateShapes(store, states, countries, read_map(args.map), evidence, read_rules(args.map), natural if args.states_source == "naturalearth" else None)
    work = Corrections(checker, states, state_shapes, bioregions)
    sources = [(state_shapes.source.label, state_shapes)]
    if args.states_source == "both":
        sources.append((natural.label, StateShapes(store, states, countries, read_map(args.map), evidence, read_rules(args.map), natural)))

    stored = [cc.unhex(r[0]) for r in sql(stored_countries_sql(group_id))]
    unknown = [name for name in stored if name.strip() and countries.resolve(name.strip()) is None]
    spelt = lambda country: [name for name in stored if name.strip() and getattr(countries.resolve(name.strip()), "name", None) == country]
    convert = [(spelt(country), sorted({name for unit in units for name in [unit] + states.old[country].get(unit, [])}))
               for country, units in sorted(state_shapes.convert.items())]
    lists = Lists(groups)
    for r in cc.rows_sql(project, rows_sql(columns, group_id, unknown, convert), args.container):
        row = read_row(r)
        lists.add(row, work.work(row), work.kind(row))

    costs = []
    if args.states_source == "both" and evidence is not None:    # every country that has specimens to test with, in both sources
        for label, shapes in sources:
            began = time.time()
            for country in sorted(evidence.points):
                shapes.choose(country, checker.code_of_country.get(country, ""))
            costs.append("Sources compared, " + label + ": " + cc.n(len(shapes.tables)) + " countries looked up in " + format(time.time() - began, ".0f")
                         + " seconds more; memory at the peak after it " + cc.n(cc.peak_mb()) + " MB.")
    write_csv(os.path.join(folder, "corrections.csv"), CORRECTIONS_HEADER, lists.corrections())
    write_csv(os.path.join(folder, "by_collecting_event.csv"), EVENTS_HEADER, lists.events())
    write_csv(os.path.join(folder, "not_derivable.csv"), NOT_HEADER, lists.not_derivable())
    suggested = suggestions(state_shapes, states, lists.in_shapes())
    write_csv(os.path.join(folder, "mapping_tested.csv"), TESTED_HEADER, tested_lines(state_shapes))
    write_csv(os.path.join(folder, "mapping_suggestions.csv"), SUGGESTIONS_HEADER, suggested[0])
    write_csv(os.path.join(folder, "to_decide.csv"), DECIDE_HEADER, decide_lines(state_shapes))
    for label, shapes in sources[1:]:                            # the second source, when both are compared: its own list of tested pairs
        write_csv(os.path.join(folder, "mapping_tested_" + re.sub(r"[^a-z0-9]+", "_", label.lower()).strip("_") + ".csv"), TESTED_HEADER, tested_lines(shapes))
    madagascar = None
    names = spelt(MADAGASCAR)
    if names and provinces_in_antweb(states) and checker.code_of_country.get(MADAGASCAR):      # one more read: every record of Madagascar
        rows = [(cc.unhex(r[0]).strip(), r[1], r[2], int(r[3])) for r in sql(madagascar_sql(names, group_id))]
        madagascar = madagascar_regions(state_shapes, states, checker.code_of_country[MADAGASCAR], rows, args.tolerance_km, group_text)
    seconds = format(time.time() - started, ".0f") + " seconds; memory at the peak " + cc.n(cc.peak_mb()) + " MB"
    text = report_text(now.strftime("%Y-%m-%d %H:%M"), commit, group_text, lists, state_shapes, store, seconds, args.tolerance_km, args.map, suggested, madagascar, evidence,
                       sources if evidence is not None else None, costs)
    with open(os.path.join(folder, "report.txt"), "w", encoding="utf-8") as f:
        f.write(text)
    print(text)
    print("Files: " + folder)
    return 0


if __name__ == "__main__":
    sys.exit(main())
