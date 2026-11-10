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
     3. The AntWeb valid state is proposed only when the point is inside exactly one state, or in the water
        within the allowed distance of exactly one. A second state within the allowed distance: "two
        candidates", both named, no proposal.
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
counts as a spelling of the valid state; then without a word such as Province, Region, State, Department. The
level with the most AntWeb states matched is used (the first one that matches 8 in 10 ends the search). The
report prints the table for each country: AntWeb states matched and not matched, shapes that are no state in
AntWeb. A point in such a shape gets no proposal ("the boundary file's <name> is not a state in AntWeb"); the
report lists these names with their specimens for each country: the list for the Geolocale Manager.
  tools/place/adm1_shape_map.csv (next to this tool) names the AntWeb state of a shape where the two differ:
  Madagascar, where AntWeb has the six provinces and the file the 22 regions; four states of Mexico under
  their long names; the District of Columbia. A row is used only when AntWeb has that state.
  The light ("simplified") files are used for the states: measured 2026-11-10 for Madagascar, Mexico and the
  United States, their lines lie within 100 m of the full files' for 99 in 100 points, and they are 2 to 50
  times smaller (Mexico 4 MB, not 36 MB; Canada 26 MB, not 648 MB). The country test uses the full files.

It writes one folder (default /root/antweb-place-fix/<date>-<time>-corrections/):

  corrections.csv          one line per specimen and field: specimen code, collecting event code, locality
                           code, field (country or adm1), current value, proposed value (AntWeb's valid
                           name), the basis in plain words, latitude, longitude, stated error, locality name,
                           country, upload group. In the order country, state, collecting event.
  by_collecting_event.csv  the working list for TaxonWorks: one line per collecting event and field with the
                           proposed value, the number of specimens, the first and last specimen code, the
                           locality name and the coordinates. A collecting event whose specimens get
                           different proposals is flagged and not to be entered as it stands. A specimen
                           with no collecting event code has a line of its own.
  not_derivable.csv        one line per collecting event (per specimen when it has no code), field and
                           reason: no coordinates; coordinates outside the stated country; two candidates;
                           the boundary file's name is not a state in AntWeb; no boundary file; country has
                           no states in AntWeb. With the number of specimens and the place text of the record
                           (state, adm2, locality name, locality notes, bioregion), so a person can decide.
  report.txt               the totals for each field, proposals and reasons; for each country the level used
                           and the table of names; the mapping rows used; the shape files and their licences.

The password is read from <project>/etc/db.properties and goes to docker through the environment, never on a
command line. Tests: tests/unit/test_place_corrections.py.
"""
import argparse
import collections
import csv
import datetime
import importlib.util
import json
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

VERSION = "2"
LEVELS = ["ADM1", "ADM2", "ADM3", "ADM4"]
GOOD_MATCH = 0.8                 # a level that matches this share of AntWeb's states ends the search
SIMPLIFIED = "_simplified"       # the light file of geoBoundaries: within 100 m of the full one for 99 in 100 points
MAP_FILE = os.path.join(HERE, "adm1_shape_map.csv")
UNIT_WORDS = {"province", "provincia", "provincie", "region", "regiao", "state", "estado", "department", "departamento", "departement",
              "district", "distrito", "county", "prefecture", "governorate", "oblast", "parish", "canton", "municipality", "territory",
              "of", "de", "del", "do", "da", "du", "the"}

NO_COORDINATES = "no coordinates"
OUTSIDE = "coordinates outside the stated country"
TWO = "two candidates"
NOT_A_STATE = "the boundary file's name is not a state in AntWeb"
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


class States:
    """The states of geolocale (georank adm1): for each valid country its valid states and the names that mean one."""

    def __init__(self, rows, countries):
        """rows: (name, parent, is_valid, valid_name) of every adm1 row."""
        self.valid = collections.defaultdict(set)                # country -> valid state names
        self.by_plain = collections.defaultdict(dict)            # country -> plain name -> valid state
        self.by_loose = collections.defaultdict(lambda: collections.defaultdict(set))
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
    """The level of the boundary files used for one country: its shapes, the AntWeb state of each, the index."""

    def __init__(self, country, code, level, names, states, hows, index, tried):
        self.country, self.code, self.level, self.names, self.states, self.hows = country, code, level, names, states, hows
        self.index, self.tried = index, tried


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

    def choose(self, country, code):
        """The Chosen level of a country, or None (self.problems says why)."""
        if country in self._chosen:
            self._chosen.move_to_end(country)
            return self._chosen[country]
        if country in self.problems:
            return None
        if not code:
            self.problems[country] = "AntWeb has no ISO code for the country and no Natural Earth shape gives one"
            return None
        wanted = self.states.of(country)
        best, tried = None, []
        for level in ([self.level_of[country]] if country in self.level_of else LEVELS):
            try:
                path = self.detail.file(code, level, SIMPLIFIED)
            except (urllib.error.URLError, OSError) as e:
                self.problems[country] = "the file of " + code + " " + level + " could not be fetched (" + str(getattr(e, "reason", e)) + ")"
                return None
            if path is None:
                tried.append(level + ": no file")
                continue
            features, _, _ = cc.read_features(path)
            features = [(name, rings) for name, rings in features if rings]
            names = [name for name, _ in features]
            states, hows = self.match(country, level, names)
            matched = len(self.states_matched(wanted, states, hows))
            islands = sum(1 for h in hows if h == ISLAND)
            tried.append(level + ": " + cc.n(len(names)) + " shapes, " + cc.n(matched) + " of " + cc.n(len(wanted)) + " AntWeb states matched"
                         + (", " + cc.n(islands) + (" shape is an island country" if islands == 1 else " shapes are island countries") + " of AntWeb" if islands else ""))
            if features and (best is None or matched > best[0]):
                best = (matched, level, features, states, hows)
            if wanted and matched >= GOOD_MATCH * len(wanted):
                break
        if best is None:
            self.problems[country] = "geoBoundaries has no file of the divisions of " + code
            return None
        matched, level, features, states, hows = best
        names = [name for name, _ in features]
        chosen = Chosen(country, code, level, names, states, hows, cc.index_of_shapes([rings for _, rings in features]), tried)
        if country not in self.level_of:                         # the first time: what the report says of this country
            self.level_of[country] = level
            entry = self.detail.lock["files"].get(code + " " + level + SIMPLIFIED, {})
            self.files[code + " " + level + SIMPLIFIED] = entry
            for name, state, how in zip(names, states, hows):
                if how == "mapping file":
                    self.rows_used.add((country, level, name, state))
            got = self.states_matched(wanted, states, hows)
            self.tables[country] = {
                "code": code, "level": level, "tried": tried, "matched": sorted(got), "not_matched": [s for s in wanted if s not in got],
                "shapes_not_states": sorted(n for n, s in zip(names, states) if not s),
                "islands": sorted(n + (" (" + s + ")" if s != n else "") for n, s, h in zip(names, states, hows) if h == ISLAND),
                "other_way": sorted(n + " -> " + s + " (" + h + ")" for n, s, h in zip(names, states, hows) if s and h not in ("name", ISLAND)),
                "licence": entry.get("licence", ""), "source": entry.get("source", "")}
        self._chosen[country] = chosen
        while len(self._chosen) > 2:
            self._chosen.popitem(last=False)
        return chosen

    def state_at(self, chosen, lat, lon, allowed):
        """(state, basis, reason, detail) for a point: the state and the basis of the proposal, or the reason why
        there is none, with the names a person needs (the candidates, the shape that is no AntWeb state)."""
        inside = chosen.index.inside(lat, lon)
        found = {n: 0.0 for n in inside}
        for n, km in chosen.index.near(lat, lon, allowed).items():
            found.setdefault(n, km)
        if not found:
            return None, "", NO_SHAPE_HERE, ""
        labels, first = [], None
        for n in sorted(found, key=lambda n: (found[n], chosen.names[n])):
            label = chosen.states[n] or chosen.names[n] + " (not a state in AntWeb)"
            if label not in labels:
                labels.append(label)
                first = n if first is None else first
        if len(labels) > 1:
            return None, "", TWO, " or ".join(labels)
        if chosen.states[first] is None:
            return None, "", NOT_A_STATE, chosen.names[first]
        where = "geoBoundaries " + chosen.code + " " + chosen.level
        name = chosen.names[first]
        if chosen.hows[first] == ISLAND:
            where += "; " + files_as_island(chosen.country, chosen.states[first])
        elif chosen.hows[first] != "name":
            name += " (" + chosen.states[first] + " by the " + chosen.hows[first] + ")"
        if inside:
            return chosen.states[first], "coordinates inside " + name + ", " + where, "", ""
        return chosen.states[first], "in the water, " + cc.rounded(found[first]) + " km from " + name + ", " + where, "", ""


def files_as_island(country, island):
    return "AntWeb files this under " + country + " as the island " + island


# ---------------------------------------------------------------- one specimen

Row = collections.namedtuple("Row", "code event locality_code country adm1 adm2 island lat lon stated locality notes bioregion group")
Proposal = collections.namedtuple("Proposal", "field current proposed basis country")
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
        state, basis, reason, detail = self.shapes.state_at(chosen, lat, lon, cc.allowed_km(row.stated, self.checker.tolerance))
        if state is None:
            return out + [Not("adm1", reason, detail, country)]
        return out + [Proposal("adm1", "", state, basis, country)]

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
                      "latitude", "longitude", "stated_error", "locality_name", "country", "upload_group"]
EVENTS_HEADER = ["collecting_event_code", "field", "current_value", "proposed_value", "specimens", "first_specimen_code", "last_specimen_code",
                 "locality_name", "latitude", "longitude", "flag", "country", "basis", "upload_group"]
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
                          [row.code, row.event, row.locality_code, p.field, p.current, p.proposed, p.basis, row.lat, row.lon, row.stated,
                           row.locality, p.country, self.group(row)]))
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
            lines.append(((p.country, " | ".join(sorted(values)), row.event, field != "country"),
                          [row.event, field, p.current, " | ".join(sorted(values)), len(items), items[0][0].code, items[-1][0].code,
                           row.locality, row.lat, row.lon, ". ".join(flags), p.country, p.basis if len(values) == 1 else "", self.group(row)]))
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


def write_csv(path, header, lines):
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(header)
        w.writerows(lines)


def report_text(when, commit, group_text, lists, shapes, detail, seconds, tolerance, map_path=MAP_FILE):
    n = cc.n
    out = ["AntWeb place corrections, " + when + " (tool version " + VERSION + ", AntWeb commit " + (commit or "unknown") + ")",
           "Upload group: " + group_text, "",
           "Records looked at (no state, no country, or a country AntWeb does not know): " + n(sum(lists.looked.values()))]
    out += cc.lines_of(lists.looked, str)
    events = lists.events()
    for field, label in (("country", "Country"), ("adm1", "State (adm1)")):
        proposals = [(row, p) for row, p in lists.proposals if p.field == field]
        nots = collections.Counter()
        for row, x in lists.nots:
            if x.field == field:
                nots[x.reason] += 1
        lines = [e for e in events if e[1] == field]
        flagged = sum(1 for e in lines if e[10].startswith("DIFFERENT"))
        out += ["", label + ": " + n(len(proposals)) + " specimens with a proposal (corrections.csv), in " + n(len(lines)) + " lines of by_collecting_event.csv"
                + (", " + n(flagged) + " of them flagged for different proposals" if flagged else ""),
                "  No proposal (not_derivable.csv): " + n(sum(nots.values())) + " specimens, by reason:"]
        out += cc.lines_of(nots, str)
        by_value = collections.Counter((p.country + ": " + p.proposed) if field == "adm1" else p.proposed for _, p in proposals)
        out += ["  Proposed values (the 40 largest):"] + cc.lines_of(by_value, str, limit=40)
    names = collections.Counter()
    for row, x in lists.nots:
        if x.reason == NOT_A_STATE:
            names[(x.country, x.detail)] += 1
    out += ["", "For the Geolocale Manager: names of the boundary files that are no state in AntWeb, with the specimens that lie in them:"]
    out += cc.lines_of(names, lambda k: k[0] + ": " + k[1], limit=200)
    out += ["", "The level of the boundary files used for each country, and how the names fit (" + n(len(shapes.tables)) + " countries):"]
    for country in sorted(shapes.tables):
        t = shapes.tables[country]
        out += ["  " + country + ": geoBoundaries " + t["code"] + " " + t["level"] + " (" + "; ".join(t["tried"]) + ")",
                "    AntWeb states matched (" + n(len(t["matched"])) + "): " + "; ".join(t["matched"]),
                "    AntWeb states not matched (" + n(len(t["not_matched"])) + "): " + "; ".join(t["not_matched"]),
                "    shapes that are an island country of AntWeb, entered as the state (" + n(len(t["islands"])) + "): " + "; ".join(t["islands"]),
                "    shapes that are no state in AntWeb (" + n(len(t["shapes_not_states"])) + "): " + "; ".join(t["shapes_not_states"])]
        if t["other_way"]:
            out += ["    matched in another way than by the name itself: " + "; ".join(t["other_way"])]
    if shapes.problems:
        out += ["", "Countries with no state shapes (" + n(len(shapes.problems)) + "):"] + ["  " + c + ": " + why for c, why in sorted(shapes.problems.items())]
    used = sorted(shapes.rows_used)
    out += ["", "Mapping file " + map_path + (": " + n(len(shapes.map_rows)) + " rows, " if os.path.exists(map_path) else ": NOT FOUND (fetch it next to the tool), ")
            + n(len(used)) + " shapes named by it in this run:"]
    out += ["  " + c + " " + level + ": " + shape + " -> " + state for c, level, shape, state in used] or ["  none"]
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

    unknown = [cc.unhex(r[0]) for r in sql(stored_countries_sql(group_id)) if cc.unhex(r[0]).strip() and countries.resolve(cc.unhex(r[0]).strip()) is None]
    lists = Lists(groups)
    columns = {r[0] for r in sql(cc.columns_sql("specimen"))}
    for r in cc.rows_sql(project, rows_sql(columns, group_id, unknown), args.container):
        row = read_row(r)
        lists.add(row, work.work(row), work.kind(row))

    write_csv(os.path.join(folder, "corrections.csv"), CORRECTIONS_HEADER, lists.corrections())
    write_csv(os.path.join(folder, "by_collecting_event.csv"), EVENTS_HEADER, lists.events())
    write_csv(os.path.join(folder, "not_derivable.csv"), NOT_HEADER, lists.not_derivable())
    seconds = format(time.time() - started, ".0f") + " seconds; memory at the peak " + cc.n(cc.peak_mb()) + " MB"
    text = report_text(now.strftime("%Y-%m-%d %H:%M"), commit, group_text, lists, state_shapes, store, seconds, args.tolerance_km, args.map)
    with open(os.path.join(folder, "report.txt"), "w", encoding="utf-8") as f:
        f.write(text)
    print(text)
    print("Files: " + folder)
    return 0


if __name__ == "__main__":
    sys.exit(main())
