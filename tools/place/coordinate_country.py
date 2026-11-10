#!/usr/bin/env python3
"""Coordinate country: compare the coordinates of each specimen with its stored country (report only).

Why (Brian Fisher, 2026-11-10: "if there is no country but lat long then we should derive the country: also lat
long could be in conflict with the country given, that should result in an upload report so it is verified"):
AntWeb stores a country (text, from the curator's file) and a latitude and longitude with each specimen, and
nothing compares the two. Production 2026-11-10: 1,920 specimens with no country, 77 of them with coordinates.
This tool is the first step, a report an administrator runs on the server. The same rule goes into the upload
later. It never writes to the database: every statement it sends is a single select (read_only() refuses
anything else), and it needs no more than the right to read.

  python3 coordinate_country.py                              the whole specimen table
  python3 coordinate_country.py --countries "Mexico,Panama"  a quick run: only specimens stored under these
  python3 coordinate_country.py --countries none             a quick run: only specimens with no country
  python3 coordinate_country.py --tolerance-km 5             another tolerance (default 10)
  python3 coordinate_country.py --shapes FILE                a local GeoJSON file of country shapes

Country shapes: Natural Earth (public domain, naturalearthdata.com), file ne_10m_admin_0_map_subunits.geojson
of github.com/nvkelso/natural-earth-vector at the commit SHAPES_COMMIT below (14 MB). The tool fetches it once
into /root/antweb-place-fix/shapes/ and checks its sha256; a file that does not match is not used.

  Why the 1:10m file and not the 1:50m one (3 MB). Measured 2026-11-10 against the detailed coast of Mexico and
  Madagascar (geoBoundaries, 8,000 mainland coast points each): with the 1:10m shapes the farthest point is
  5.1 km outside its country and 0.3 and 1.9 in 100 are more than 2 km outside; with the 1:50m shapes 0.5 and
  9.2 in 100 are more than 5 km outside, the farthest 11 and 14 km. Small islands: the 1:10m file has 4,293
  pieces, the 1:50m file 1,629; of 50 small places, most of them islands where ants are collected (Aldabra,
  Europa, Juan de Nova, Rodrigues, Isla del Coco, Midway ...), 5 are at sea in the 1:10m file and 22 in the
  1:50m file. The price is small: a run on a table of production's size took 17 seconds and 260 MB of memory.
  Why "map subunits" and not "countries" or "map units". AntWeb lists many territories as countries of their
  own. The "countries" file draws Reunion, Martinique, Guadeloupe, French Guiana and Mayotte as part of France.
  "Map units" splits those but keeps Hawaii, the Galapagos and the Canary Islands inside their country.
  "Map subunits" has all of them as shapes of their own (360 shapes). A shape AntWeb does not list (Alaska,
  England, Corsica) is given to the country it is part of, so the finest file fits whatever AntWeb lists.

Which AntWeb country a shape is (map_shapes): the shape's own names against AntWeb's valid countries (capitals
and accents ignored, AntWeb's old names followed to the valid name), then its own ISO codes (geolocale.iso3Code
and isoCode), then the alias table ALIASES. If none matches: the first country of the shared-area table SHARED,
then the same three steps for the unit, the country and the sovereign country the shape is part of. The name
comes before the code because several small shapes carry the code of a larger one (Juan de Nova Island: ATF,
the French Southern Territories). The report lists the AntWeb valid countries that got no shape, the shapes
with no AntWeb country, and every shape matched in another way than by its own name or code; mapping.csv has
all of them. Read these lists before trusting the numbers.

The rule (Checker.decide):

  not usable    no coordinates, 0 and 0, latitude outside -90 to 90, longitude outside -180 to 180: counted.
  the point's country    the shape that contains the point (holes and shapes of several pieces handled). When
                no shape contains it: the nearest shape within the tolerance (a coast or a small island drawn
                a little off). Beyond the tolerance: at sea.
  no stored country    in or near exactly one country: "country can be derived" (inside, or near with the km).
                A second country within the tolerance, or a shared area: "two candidates", nothing derived.
  a stored country, after AntWeb's own old-name mapping (USA: United States) and the spelling rule of
  place_fix.py (México: Mexico):
      agree           the point is in the stored country. An island country and its parent country agree both
                      ways (United States with island country Hawaii: a Hawaii or a United States shape).
      agree, just outside the shape    within the tolerance of the stored country and in no other country.
      agree, in a territory or shared area of the stored country    stored France, the point in Reunion;
                      stored Russia, the point in Crimea. Listed by pair in the report. Not a conflict.
      near the border    inside another country, within the tolerance of the stored one. Not a conflict.
      conflict        anything else, with the distance to the stored country and the data-entry error that
                      would make the point agree: longitude sign, latitude sign, both signs, latitude and
                      longitude swapped. "Conflict, at sea": no country within the tolerance.
      not checked     the stored name is not known to AntWeb, or its country has no shape. Never a conflict.

Tolerance (--tolerance-km, default 10). With the 1:10m shapes the farthest a point of the mainland coast lay
outside its country was 5.1 km; 10 km is twice that, for coordinates rounded on a label and for islands drawn
smaller than they are. It does not cover every islet: 3 in 100 coast points of the small islands of Mexico and
Madagascar are more than 10 km from any drawn shape (9 and 7 in 100 at 5 km). A conflict "at sea" with a short
distance to the stored country is most likely such an islet. A larger tolerance hides real errors near a
border, a smaller one reports more coast points; the report states the value used.

It writes one folder (default /root/antweb-place-fix/<date>-<time>-coordinates/):

  report.txt      totals for each class; conflicts by upload group and by stored country -> coordinate country;
                  countries that can be derived, by group; the mapping lists; the shape file with its commit
                  and checksum; the run time. Also printed.
  conflicts.csv   one line per specimen in conflict: code, upload group, stored country, state, locality name,
                  latitude, longitude, coordinate country, class, distance km, likely error.
  derive.csv      the same columns, one line per specimen with usable coordinates and no country.
  mapping.csv     every shape, the AntWeb country it was given to, and how it was matched.

Speed (measured 2026-11-10 on 2 CPUs with Python 3.13 and MariaDB 10.11, a made-up table of 2,500,000 specimens
at 400,000 places): 17 seconds in all, 260 MB of memory at the peak; reading the shapes and building the index
took 1 second, 300,000 points without the database 9 seconds (6 seconds with the 1:50m shapes). The database
returns each distinct combination of country, point and upload group once (one statement), then the specimens
of the conflicts (one statement for every 20,000 points). The index is a grid of quarter-degree cells: a cell
no border runs through is answered at once; in a cell with a border, only the border pieces inside it are
tested.

Known limits. Natural Earth draws borders as they are run today (Crimea with Russia, Somaliland apart from
Somalia): the table SHARED lists the areas where more than one stored country is accepted. It cannot help
where a disputed area is drawn inside a country's own shape: Western Sahara west of the wall (Laayoune) is
drawn as Morocco, the Golan and East Jerusalem as Israel, Kashmir is split along the line of control, Abyei is
Sudan, the Hala'ib triangle Egypt. A conflict there is a matter of opinion. Islets missing from the 1:10m file
(Round Island of Mauritius, Dry Tortugas, Heron Island, Koh Tao, Anak Krakatau) show as "at sea". Coordinates
are compared as stored, to 5 decimals; the uncertainty a curator gave (latlonmaxerror) is not used. Lakes are
part of their country; a border in a lake or a river is the line Natural Earth drew.

The password is read from <project>/etc/db.properties and goes to docker through the environment, never on a
command line. Tests: tests/unit/test_coordinate_country.py.
"""
import argparse
import array
import collections
import csv
import datetime
import hashlib
import json
import math
import os
import re
import resource
import subprocess
import sys
import tempfile
import time
import unicodedata
import urllib.request

VERSION = "1"

SHAPES_COMMIT = "ca96624a56bd078437bca8184e78163e5039ad19"
SHAPES_NAME = "ne_10m_admin_0_map_subunits.geojson"
SHAPES_URL = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/" + SHAPES_COMMIT + "/geojson/" + SHAPES_NAME
SHAPES_SHA256 = "76896018b9265072d8063e118e46df765be0ceb54a803b1a2571ebe25b36a071"
SHAPES_FOLDER = "/root/antweb-place-fix/shapes"
TOLERANCE_KM = 10.0

# A shape name of Natural Earth, and the names AntWeb may list the same place under. Used only when neither the
# name nor an ISO code matched. Add a line when the report lists a pair that belongs together.
ALIASES = {
    "United States of America": ["United States", "USA"],
    "Democratic Republic of the Congo": ["Democratic Republic of Congo", "Congo, Democratic Republic of the", "DRC"],
    "Republic of the Congo": ["Congo", "Republic of Congo", "Congo, Republic of the"],
    "Ivory Coast": ["Cote d'Ivoire"],
    "eSwatini": ["Swaziland"],
    "Cabo Verde": ["Cape Verde"],
    "North Macedonia": ["Macedonia"],
    "Myanmar": ["Burma"],
    "East Timor": ["Timor-Leste"],
    "Vatican": ["Vatican City", "Holy See"],
    "Falkland Islands": ["Falkland Islands (Malvinas)"],
    "Republic of Serbia": ["Serbia"],
    "Russia": ["Russian Federation"],
    "Laos": ["Lao People's Democratic Republic"],
    "Vietnam": ["Viet Nam"],
    "Federated States of Micronesia": ["Micronesia"],
    "Galápagos Islands": ["Galapagos"],
    "United States Virgin Islands": ["Virgin Islands, U.S.", "U.S. Virgin Islands"],
    "United Republic of Tanzania": ["Tanzania"],
    "Palestine": ["Palestinian Territory", "Palestinian Territories", "State of Palestine"],
    "French Southern and Antarctic Lands": ["French Southern Territories"],
    "Saint Helena": ["Saint Helena, Ascension and Tristan da Cunha"],
    "South Georgia and the Islands": ["South Georgia and the South Sandwich Islands"],
    "Pitcairn Islands": ["Pitcairn"],
    "Svalbard Islands": ["Svalbard and Jan Mayen"],
}

# Areas Natural Earth draws apart from, or under another country than, the one a curator may write. A specimen
# stored under any of the listed countries agrees with a point in the area. When AntWeb does not list the area
# itself, the first country here is the one named in the reports; with two countries listed, a specimen with no
# country gets "two candidates" and nothing is derived. A matter of judgement: Brian Fisher decides.
SHARED = {
    "Crimea": ["Ukraine", "Russia"],
    "Somaliland": ["Somalia"],
    "Northern Cyprus": ["Cyprus"],
    "Cyprus U.N. Buffer Zone": ["Cyprus"],
    "Akrotiri": ["Cyprus", "United Kingdom"],
    "Dhekelia": ["Cyprus", "United Kingdom"],
    "Kosovo": ["Serbia"],
    "Western Sahara": ["Morocco"],
    "Taiwan": ["China"],
    "West Bank": ["Palestine", "Israel"],
    "Gaza": ["Palestine", "Israel"],
    "Siachen Glacier": ["India", "Pakistan"],
    "US Naval Base Guantanamo Bay": ["Cuba", "United States"],
    "Southern Patagonian Ice Field": ["Argentina", "Chile"],
    "Bir Tawil": ["Egypt", "Sudan"],
    "Brazilian Island": ["Brazil", "Uruguay"],
}

AGREE = "agree"
AGREE_NEAR = "agree, just outside the shape"
TERRITORY = "agree, in a territory or shared area of the stored country"
BORDER = "near the border"
CONFLICT = "conflict"
SEA = "conflict, at sea"
UNKNOWN = "not checked, country not known to AntWeb"
NO_SHAPE = "not checked, no shape for the stored country"
DERIVE_IN = "country can be derived, inside"
DERIVE_NEAR = "country can be derived, near"
TWO = "two candidates"
DERIVE_SEA = "at sea"
DERIVE_NONE = "in an area with no AntWeb country"
STORED_CLASSES = [AGREE, AGREE_NEAR, TERRITORY, BORDER, CONFLICT, SEA, UNKNOWN, NO_SHAPE]
DERIVE_CLASSES = [DERIVE_IN, DERIVE_NEAR, TWO, DERIVE_SEA, DERIVE_NONE]
CONFLICT_CLASSES = (CONFLICT, SEA)


# ---------------------------------------------------------------- AntWeb's countries (no database)

def normal(name):
    """A name without capitals, accents or extra blanks: México and  mexico  are the same."""
    decomposed = unicodedata.normalize("NFKD", name or "")
    return " ".join("".join(c for c in decomposed if not unicodedata.combining(c)).casefold().split())


def clean(value):
    """Blank, "none" and "null" mean nothing is stored (valid_name holds the text "none")."""
    v = (value or "").strip()
    return "" if v.lower() in ("", "none", "null") else v


class Country:
    def __init__(self, name, is_valid, valid_name, is_island, parent, iso2="", iso3=""):
        self.name = name
        self.is_valid = str(is_valid) == "1"
        self.valid_name = clean(valid_name)
        self.is_island = str(is_island) == "1"
        self.parent = clean(parent)          # geolocale.country: the country an island belongs to
        self.iso2 = clean(iso2).upper()
        self.iso3 = clean(iso3).upper()


class Countries:
    """The country rows of geolocale: valid names, old names, island countries and their parents."""

    def __init__(self, rows):
        self.rows = {}
        for c in rows:
            self.rows[c.name] = c
        self.by_normal = collections.defaultdict(set)
        self.by_iso = collections.defaultdict(set)
        for c in self.rows.values():
            if c.is_valid:
                self.by_normal[normal(c.name)].add(c.name)
                if not c.is_island:
                    for code in (c.iso3, c.iso2):
                        if code:
                            self.by_iso[code].add(c.name)
        self.children = collections.defaultdict(set)
        for c in self.rows.values():
            if c.is_valid and c.is_island and c.parent:
                parent = self.resolve(c.parent)
                if parent is not None:
                    self.children[parent.name].add(c.name)
        self._family = {}

    def valid(self):
        return sorted(c.name for c in self.rows.values() if c.is_valid)

    def resolve(self, name):
        """The valid country of a stored name: itself, the valid name of an old name, or the one valid country
        that differs only by capitals, accents or blanks (place_fix.py). None when AntWeb does not know it."""
        c = self.rows.get(name)
        if c is not None:
            if c.is_valid:
                return c
            target = self.rows.get(c.valid_name) if c.valid_name else None
            if target is not None and target.is_valid:
                return target
        names = self.by_normal.get(normal(name), ())
        return self.rows[next(iter(names))] if len(names) == 1 else None

    def by_code(self, code):
        names = self.by_iso.get((code or "").upper(), ())
        return self.rows[next(iter(names))] if len(names) == 1 else None

    def family(self, name):
        """The names that agree with a valid country: itself, its parent when it is an island, its islands."""
        if name not in self._family:
            names = {name} | self.children.get(name, set())
            c = self.rows.get(name)
            if c is not None and c.is_island and c.parent:
                parent = self.resolve(c.parent)
                if parent is not None:
                    names.add(parent.name)
            self._family[name] = frozenset(names)
        return self._family[name]


# ---------------------------------------------------------------- the shapes and which AntWeb country each is

ISO3 = re.compile(r"^[A-Z]{3}$")
ISO2 = re.compile(r"^[A-Z]{2}$")


def distinct(values):
    out = []
    for v in values:
        v = (v or "").strip() if isinstance(v, str) else ""
        if v and v != "-99" and v not in out:
            out.append(v)
    return out


class Shape:
    """One feature of the GeoJSON file: its names and codes at four levels, and its polygons."""

    def __init__(self, props, polygons):
        g = lambda key: props.get(key) if isinstance(props.get(key), str) else ""
        # NAME_EN is left out: the file has "Seychelles" there for the French Southern and Antarctic Lands
        own = distinct([g("NAME_LONG"), g("NAME"), g("SUBUNIT"), g("shapeName"), g("name")])
        self.name = own[0] if own else (g("ADMIN") or "shape with no name")
        self.kind = g("TYPE")
        self.admin = g("ADMIN")
        self.levels = [
            ("itself", own, distinct([g("ISO_A3"), g("ISO_A2"), g("shapeGroup")])),
            ("part of " + g("GEOUNIT"), distinct([g("GEOUNIT")]), []),
            ("part of " + g("ADMIN"), distinct([g("ADMIN")]), distinct([g("ISO_A3_EH"), g("ISO_A2_EH")])),
            ("belongs to " + g("SOVEREIGNT"), distinct([g("SOVEREIGNT")]), []),
        ]
        self.polygons = polygons
        self.country = None      # the AntWeb valid country this shape is
        self.how = ""            # how it was matched, for the mapping list
        self.also = frozenset()  # AntWeb countries a specimen may also be stored under: owner, shared area
        self.shared = ()         # the countries of the table SHARED that AntWeb lists, in the table's order


def read_shapes(geojson):
    shapes = []
    for f in geojson.get("features", []):
        g = f.get("geometry") or {}
        if g.get("type") == "Polygon":
            polygons = [g["coordinates"]]
        elif g.get("type") == "MultiPolygon":
            polygons = g["coordinates"]
        else:
            continue
        shapes.append(Shape(f.get("properties") or {}, polygons))
    return shapes


def match(countries, names, codes, aliases):
    """(Country, how) for the first of: a name, an ISO code, an alias. (None, "") when nothing matches."""
    for n in names:
        c = countries.resolve(n)
        if c is not None:
            return c, "name"
    for code in codes:
        if ISO3.match(code) or ISO2.match(code):
            c = countries.by_code(code)
            if c is not None:
                return c, "ISO code " + code
    for n in names:
        for alias in aliases.get(normal(n), ()):
            c = countries.resolve(alias)
            if c is not None:
                return c, "alias table (" + alias + ")"
    return None, ""


def map_shapes(shapes, countries, aliases=None, shared=None):
    """Sets country, how and also of every shape. Returns the notes for the report (name and code disagree)."""
    aliases = {normal(k): v for k, v in (ALIASES if aliases is None else aliases).items()}
    shared = {normal(k): v for k, v in (SHARED if shared is None else shared).items()}
    admin_codes = {}
    for s in shapes:
        admin_codes.setdefault(s.admin, s.levels[2][2])
    notes = []
    for s in shapes:
        found = []                                   # (level index, Country, how)
        for i, (label, names, codes) in enumerate(s.levels):
            if i == 3:
                codes = admin_codes.get(names[0], []) if names else []
            c, how = match(countries, names, codes, aliases)
            if c is not None:
                found.append((i, c, label + ", by " + how))
            if i == 0 and c is not None and how == "name":
                by_code = [countries.by_code(code) for code in codes]
                other = [b.name for b in by_code if b is not None and b.name != c.name]
                if other:
                    notes.append(s.name + ": the name says " + c.name + ", the ISO code says " + other[0] + " (the name is used)")
        table = next((shared[normal(n)] for n in s.levels[0][1] if normal(n) in shared), [])
        listed = [countries.resolve(candidate) for candidate in table]
        first = listed[0].name if listed and listed[0] is not None else None
        s.shared = tuple(distinct([c.name for c in listed if c is not None]))
        if found and found[0][0] == 0:
            s.country, s.how = found[0][1].name, found[0][2]
        elif first:
            s.country, s.how = first, "shared area table"
        elif found:
            s.country, s.how = found[0][1].name, found[0][2]
        elif s.shared:
            s.country, s.how = s.shared[0], "shared area table"
        else:
            s.country, s.how = None, ""
        s.also = frozenset(({c.name for _, c, _ in found} | set(s.shared)) - {s.country})
    return notes


def mapping_lists(shapes, countries):
    """(valid AntWeb countries with no shape, shapes with no AntWeb country, shapes matched another way than by
    their own name or code)."""
    used = {s.country for s in shapes if s.country}
    no_shape = [n for n in countries.valid() if n not in used]
    no_country = sorted({s.name + (" (" + s.admin + ")" if s.admin and s.admin != s.name else "") for s in shapes if not s.country})
    other_way = sorted({s.name + " -> " + s.country + " (" + s.how + ")" for s in shapes
                        if s.country and not (s.how.startswith("itself, by name") or s.how.startswith("itself, by ISO"))})
    return no_shape, no_country, other_way


# ---------------------------------------------------------------- the index: which shape holds a point

CELL = 0.25                 # degrees; 1,440 columns by 720 rows
INV = 4.0
NCOLS, NROWS = 1440, 720
BLOCK = 8                   # cells: the far-distance search looks at blocks of 2 by 2 degrees
# The fixed point of a cell that a test walks to. Not the middle: a straight border between round coordinates
# can run exactly through the middle of a cell, never through these fractions of it.
FX, FY = 0.4142135623730951, 0.5772156649015329
EARTH_KM = 6371.0088
KM_PER_DEGREE = EARTH_KM * math.pi / 180.0


def km_between(lat1, lon1, lat2, lon2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    a = math.sin((p2 - p1) / 2.0) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lon2 - lon1) / 2.0) ** 2
    return 2.0 * EARTH_KM * math.asin(min(1.0, math.sqrt(a)))


def fixed_x(col):
    return -180.0 + (col + FX) * CELL


def fixed_y(row):
    return -90.0 + (row + FY) * CELL


class Index:
    """A grid over the world. For each cell: the shapes that cover it whole (no border inside), and for each
    shape with a border inside it, the border pieces and whether the cell's fixed point is inside the shape.
    A point is tested by walking from it to the fixed point of its cell and counting the border pieces crossed."""

    def __init__(self, shapes, forget=False):
        self.whole = array.array("h", [-1]) * (NCOLS * NROWS)   # shape number; -2: several, see self.several
        self.several = {}
        self.cells = {}                                          # cell -> {shape number: [fixed point inside, pieces]}
        self.blocks = [collections.defaultdict(list) for _ in shapes]   # shape -> block -> cells with its border
        self._far = {}
        for number, shape in enumerate(shapes):
            self._add(number, shape)
            if forget:
                shape.polygons = None                            # the index holds the borders now: less memory

    def _add(self, number, shape):
        cells, blocks = self.cells, self.blocks[number]
        crossings = collections.defaultdict(list)                # row -> where the border crosses the row's fixed latitude
        for polygon in shape.polygons:
            for ring in polygon:
                if len(ring) < 3:
                    continue
                points = ring if ring[0] == ring[-1] else list(ring) + [ring[0]]
                x1, y1 = points[0][0], points[0][1]
                for i in range(1, len(points)):
                    x2, y2 = points[i][0], points[i][1]
                    if x1 == x2 and y1 == y2:
                        continue
                    piece = (x1, y1, x2, y2)
                    for cell in pieces_cells(x1, y1, x2, y2):
                        d = cells.get(cell)
                        if d is None:
                            d = cells[cell] = {}
                        entry = d.get(number)
                        if entry is None:
                            entry = d[number] = [False, []]
                            blocks[(cell // NCOLS // BLOCK) * (NCOLS // BLOCK) + (cell % NCOLS) // BLOCK].append(cell)
                        entry[1].append(piece)
                    if y1 != y2:
                        lo, hi = (y1, y2) if y1 < y2 else (y2, y1)
                        first = max(int(math.ceil((lo + 90.0) * INV - FY)) - 1, 0)
                        last = min(int(math.floor((hi + 90.0) * INV - FY)) + 1, NROWS - 1)
                        for row in range(first, last + 1):
                            cy = fixed_y(row)
                            if (y1 > cy) != (y2 > cy):
                                crossings[row].append(x1 + (cy - y1) * (x2 - x1) / (y2 - y1))
                    x1, y1 = x2, y2
        whole, several = self.whole, self.several
        for row, xs in crossings.items():
            xs.sort()
            for k in range(0, len(xs) - 1, 2):
                # the cells whose fixed point is between the two crossings: xs[k] <= fixed_x(col) < xs[k + 1]
                first = max(int(math.ceil((xs[k] + 180.0) * INV - FX)) - 1, 0)
                last = min(int(math.ceil((xs[k + 1] + 180.0) * INV - FX)), NCOLS - 1)
                while first <= last and fixed_x(first) < xs[k]:
                    first += 1
                while last >= first and fixed_x(last) >= xs[k + 1]:
                    last -= 1
                for col in range(first, last + 1):
                    cell = row * NCOLS + col
                    d = cells.get(cell)
                    if d is not None and number in d:
                        d[number][0] = True
                    elif whole[cell] == -1:
                        whole[cell] = number
                    elif whole[cell] == -2:
                        several[cell] += (number,)
                    else:
                        several[cell] = (whole[cell], number)
                        whole[cell] = -2

    def inside(self, lat, lon):
        """The numbers of the shapes that contain the point."""
        col = min(int((lon + 180.0) * INV), NCOLS - 1)
        row = min(int((lat + 90.0) * INV), NROWS - 1)
        cell = row * NCOLS + col
        w = self.whole[cell]
        found = [w] if w >= 0 else (list(self.several[cell]) if w == -2 else [])
        d = self.cells.get(cell)
        if d:
            cx, cy = fixed_x(col), fixed_y(row)
            for number, (is_in, pieces) in d.items():
                for x1, y1, x2, y2 in pieces:
                    if (y1 > lat) != (y2 > lat):
                        x = x1 + (lat - y1) * (x2 - x1) / (y2 - y1)
                        if (x > lon) != (x > cx):
                            is_in = not is_in
                    if (x1 > cx) != (x2 > cx):
                        y = y1 + (cx - x1) * (y2 - y1) / (x2 - x1)
                        if (y > lat) != (y > cy):
                            is_in = not is_in
                if is_in:
                    found.append(number)
        return found

    def near(self, lat, lon, km):
        """{shape number: km} for every shape with a border within km of the point (the 180 meridian included)."""
        reach = km / KM_PER_DEGREE
        scale = math.cos(math.radians(lat))
        reach_x = reach / max(scale, 0.01)
        row0 = max(int((lat - reach + 90.0) * INV), 0)
        row1 = min(int((lat + reach + 90.0) * INV), NROWS - 1)
        col0 = int(math.floor((lon - reach_x + 180.0) * INV))
        col1 = int(math.floor((lon + reach_x + 180.0) * INV))
        if col1 - col0 >= NCOLS:
            col0, col1 = 0, NCOLS - 1
        kx, ky = KM_PER_DEGREE * scale, KM_PER_DEGREE
        limit = km * km
        best = {}
        cells = self.cells
        for row in range(row0, row1 + 1):
            for c in range(col0, col1 + 1):
                col = c % NCOLS
                d = cells.get(row * NCOLS + col)
                if not d:
                    continue
                px = lon - 360.0 * ((c - col) // NCOLS)          # the point seen from the other side of 180
                for number, entry in d.items():
                    m = best.get(number, limit)
                    for x1, y1, x2, y2 in entry[1]:
                        ax, ay = (x1 - px) * kx, (y1 - lat) * ky
                        ex, ey = (x2 - x1) * kx, (y2 - y1) * ky
                        length = ex * ex + ey * ey
                        t = -(ax * ex + ay * ey) / length if length > 0.0 else 0.0
                        t = 0.0 if t < 0.0 else (1.0 if t > 1.0 else t)
                        qx, qy = ax + t * ex, ay + t * ey
                        d2 = qx * qx + qy * qy
                        if d2 <= m:
                            m = d2
                            best[number] = m
        return {number: math.sqrt(m) for number, m in best.items()}

    def distance(self, numbers, lat, lon):
        """Km from the point to the nearest border of the given shapes, however far (None when they have none).
        Blocks of 2 by 2 degrees are tried nearest first; a block or a cell that cannot beat the best is skipped."""
        key = tuple(sorted(numbers))
        blocks = self._far.get(key)
        if blocks is None:
            merged = collections.defaultdict(list)
            for number in key:
                for block, cells in self.blocks[number].items():
                    merged[block].extend((cell, number) for cell in cells)
            per_row = NCOLS // BLOCK
            blocks = self._far[key] = [(-90.0 + (b // per_row) * CELL * BLOCK, -180.0 + (b % per_row) * CELL * BLOCK, cells)
                                       for b, cells in merged.items()]
        if not blocks:
            return None
        size = CELL * BLOCK
        order = sorted(((box_km(lat, lon, south, west, size) - 2.0 * size * KM_PER_DEGREE, i) for i, (south, west, _) in enumerate(blocks)))
        best = float("inf")
        kx = math.cos(math.radians(lat))
        for bound, i in order:
            if bound > best:
                break
            for cell, number in blocks[i][2]:
                south, west = -90.0 + (cell // NCOLS) * CELL, -180.0 + (cell % NCOLS) * CELL
                if box_km(lat, lon, south, west, CELL) - 2.0 * CELL * KM_PER_DEGREE > best:
                    continue
                for x1, y1, x2, y2 in self.cells[cell][number][1]:
                    ax = ((x1 - lon + 180.0) % 360.0 - 180.0) * kx
                    ay = y1 - lat
                    ex, ey = (x2 - x1) * kx, y2 - y1
                    length = ex * ex + ey * ey
                    t = -(ax * ex + ay * ey) / length if length > 0.0 else 0.0
                    t = 0.0 if t < 0.0 else (1.0 if t > 1.0 else t)
                    d = km_between(lat, lon, y1 + t * (y2 - y1), x1 + t * (x2 - x1))
                    if d < best:
                        best = d
        return best


def pieces_cells(x1, y1, x2, y2):
    """The cells a border piece runs through (a long piece is walked in short steps, so that a diagonal border
    of ten degrees is not filed in every cell of the square around it)."""
    c1 = min(int((x1 + 180.0) * INV), NCOLS - 1)
    r1 = min(int((y1 + 90.0) * INV), NROWS - 1)
    c2 = min(int((x2 + 180.0) * INV), NCOLS - 1)
    r2 = min(int((y2 + 90.0) * INV), NROWS - 1)
    if c1 == c2 and r1 == r2:
        return (r1 * NCOLS + c1,)
    if abs(c1 - c2) <= 1 and abs(r1 - r2) <= 1:
        return {r * NCOLS + c for r in (r1, r2) for c in (c1, c2)}
    out = set()
    steps = 2 * max(abs(c1 - c2), abs(r1 - r2))
    pc, pr = c1, r1
    for i in range(1, steps + 1):
        t = i / steps
        c = min(int((x1 + t * (x2 - x1) + 180.0) * INV), NCOLS - 1)
        r = min(int((y1 + t * (y2 - y1) + 90.0) * INV), NROWS - 1)
        for rr in range(min(pr, r), max(pr, r) + 1):
            for cc in range(min(pc, c), max(pc, c) + 1):
                out.add(rr * NCOLS + cc)
        pc, pr = c, r
    return out


def box_km(lat, lon, south, west, size):
    """Km from the point to the nearest corner or side point of a box of size degrees (0 inside it)."""
    off = (lon - west + 180.0) % 360.0 - 180.0                   # the point's longitude seen from the west side
    if 0.0 <= off <= size:
        x = lon
    elif off < 0.0:
        x = west if -off <= 360.0 + off - size else west + size
    else:
        x = west + size if off - size <= 360.0 - off else west
    y = south if lat < south else (south + size if lat > south + size else lat)
    return km_between(lat, lon, y, x)


# ---------------------------------------------------------------- the rule

Result = collections.namedtuple("Result", "cls country km error")


def usable(lat, lon):
    return -90.0 <= lat <= 90.0 and -180.0 <= lon <= 180.0 and not (lat == 0.0 and lon == 0.0)


class Checker:
    def __init__(self, countries, shapes, index, tolerance_km=TOLERANCE_KM):
        self.countries, self.shapes, self.index, self.tolerance = countries, shapes, index, tolerance_km
        self.of_country = collections.defaultdict(list)          # AntWeb country -> its shape numbers
        for number, s in enumerate(shapes):
            if s.country:
                self.of_country[s.country].append(number)
        self._stored = {}

    def stored(self, country, island):
        """(class or None, the names that agree, the shape numbers of those names) for a stored country."""
        key = (country, island)
        if key not in self._stored:
            c = self.countries.resolve(country)
            if c is None:
                self._stored[key] = (UNKNOWN, frozenset(), ())
            else:
                names = set(self.countries.family(c.name))
                i = self.countries.resolve(island) if island else None
                if i is not None and i.is_island:
                    names |= self.countries.family(i.name)
                numbers = tuple(n for name in sorted(names) for n in self.of_country.get(name, ()))
                self._stored[key] = (None if numbers else NO_SHAPE, frozenset(names), numbers)
        return self._stored[key]

    def names(self, numbers, candidates=False):
        """The AntWeb countries of these shapes, each once. candidates: a shared area AntWeb does not list gives
        each of its countries (no country is derived there)."""
        out = []
        for n in numbers:
            s = self.shapes[n]
            names = [s.country or s.name + " (not an AntWeb country)"]
            if candidates and len(s.shared) > 1 and not s.how.startswith("itself"):
                names = s.shared
            for name in names:
                if name not in out:
                    out.append(name)
        return out

    def agrees(self, accept, lat, lon):
        """True when the point is in, or within the tolerance of, a shape a stored country of accept agrees with."""
        fits = lambda n: self.shapes[n].country in accept or (self.shapes[n].also & accept)
        if any(fits(n) for n in self.index.inside(lat, lon)):
            return True
        return any(fits(n) for n in self.index.near(lat, lon, self.tolerance))

    def likely_error(self, accept, lat, lon):
        tries = [("longitude sign", lat, -lon), ("latitude sign", -lat, lon), ("both signs", -lat, -lon),
                 ("latitude and longitude swapped", lon, lat)]
        return " or ".join(label for label, la, lo in tries
                           if (la, lo) != (lat, lon) and usable(la, lo) and self.agrees(accept, la, lo))

    def decide(self, country, island, lat, lon):
        """The class of one usable point against one stored country ("" for none)."""
        if not country:
            return self.derive(lat, lon)
        cls, accept, numbers = self.stored(country, island)
        if cls is not None:
            return Result(cls, "", None, "")
        shapes = self.shapes
        inside = self.index.inside(lat, lon)
        territory = None
        for n in inside:
            if shapes[n].country in accept:
                return Result(AGREE, shapes[n].country, 0.0, "")
            if territory is None and shapes[n].also & accept:
                territory = n
        if territory is not None:
            return Result(TERRITORY, self.names([territory])[0], 0.0, "")
        near = self.index.near(lat, lon, self.tolerance)
        close = [km for n, km in near.items() if shapes[n].country in accept or (shapes[n].also & accept)]
        if close:
            if inside:
                return Result(BORDER, " or ".join(self.names(inside)), round(min(close), 1), "")
            return Result(AGREE_NEAR, "", round(min(close), 1), "")
        km = self.index.distance(numbers, lat, lon)
        km = None if km is None else round(km)
        error = self.likely_error(accept, lat, lon)
        if inside:
            return Result(CONFLICT, " or ".join(self.names(inside)), km, error)
        if near:
            return Result(CONFLICT, " or ".join(self.names(sorted(near, key=near.get))), km, error)
        return Result(SEA, "", km, error)

    def derive(self, lat, lon):
        """No stored country: the one country the point is in or near. A second country within the tolerance
        (a border, a strait) makes it two candidates: the shapes are not exact enough to choose."""
        shapes = self.shapes
        inside = self.index.inside(lat, lon)
        near = self.index.near(lat, lon, self.tolerance)
        order = inside + sorted((n for n in near if n not in inside), key=near.get)
        if not order:
            return Result(DERIVE_SEA, "", None, "")
        km = 0.0 if inside else round(near[order[0]], 1)
        mapped = self.names([n for n in order if shapes[n].country], candidates=True)
        if len(mapped) == 1:
            return Result(DERIVE_IN if inside else DERIVE_NEAR, mapped[0], km, "")
        if mapped:
            return Result(TWO, " or ".join(mapped), km, "")
        return Result(DERIVE_NONE, shapes[order[0]].name, km, "")


# ---------------------------------------------------------------- counting and the report

class Tally:
    """What the report needs: specimens and distinct points for each class, and the lists."""

    def __init__(self):
        self.specimens = collections.Counter()
        self.points = collections.Counter()
        self.conflict_groups = collections.Counter()
        self.conflict_pairs = collections.Counter()
        self.conflict_errors = collections.Counter()
        self.derive_groups = collections.Counter()
        self.derive_countries = collections.Counter()
        self.territory_pairs = collections.Counter()
        self.not_checked = collections.Counter()
        self.flagged = {}                                        # key of the point -> Result, for the two lists

    def add(self, key, country, group, count, result, valid_name=""):
        self.specimens[result.cls] += count
        self.points[result.cls] += 1
        if result.cls in CONFLICT_CLASSES:
            self.conflict_groups[group] += count
            self.conflict_pairs[(valid_name or country, result.country or "at sea")] += count
            self.conflict_errors[result.error or "none of the four"] += count
            self.flagged[key] = result
        elif result.cls in DERIVE_CLASSES:
            if result.cls in (DERIVE_IN, DERIVE_NEAR):
                self.derive_groups[group] += count
                self.derive_countries[result.country] += count
            self.flagged[key] = result
        elif result.cls == TERRITORY:
            self.territory_pairs[(valid_name or country, result.country)] += count
        elif result.cls in (UNKNOWN, NO_SHAPE):
            self.not_checked[(country, result.cls)] += count


def n(value):
    return format(int(value), ",")


def lines_of(counter, label, limit=50):
    """Counted lines, largest first; label turns a key into text."""
    rows = sorted(counter.items(), key=lambda kv: (-kv[1], str(kv[0])))
    out = ["  " + n(count).rjust(9) + "  " + label(key) for key, count in rows[:limit]]
    if len(rows) > limit:
        out.append("  " + "".rjust(9) + "  ... and " + n(len(rows) - limit) + " more (" + n(sum(c for _, c in rows[limit:])) + " specimens)")
    return out or ["  none"]


def wrap(names, width=110):
    out, line = [], "  "
    for name in names:
        if len(line) + len(name) + 2 > width and line.strip():
            out.append(line.rstrip())
            line = "  "
        line += name + "; "
    if line.strip():
        out.append(line.rstrip().rstrip(";"))
    return out or ["  none"]


def report_text(when, commit, counts, tally, groups, tolerance, lists, notes, shape_info, seconds, only=None):
    km = format(tolerance, "g") + " km"
    group = lambda g: groups.get(g, "group " + str(g))
    words = {
        AGREE: "agree: the point is in the stored country",
        AGREE_NEAR: "agree: just outside the shape (coast, small island), within " + km + " of the stored country",
        TERRITORY: "agree: the point is in a territory or shared area of the stored country (list below)",
        BORDER: "near the border: in a neighbouring country, within " + km + " of the stored one (not a conflict)",
        CONFLICT: "CONFLICT: the point is in another country (conflicts.csv)",
        SEA: "CONFLICT: at sea, no country within " + km + " (conflicts.csv)",
        UNKNOWN: "not checked: the stored country is not known to AntWeb",
        NO_SHAPE: "not checked: the stored country has no shape",
        DERIVE_IN: "country can be derived: the point is inside one country",
        DERIVE_NEAR: "country can be derived: the point is within " + km + " of one country",
        TWO: "two candidates: the point is in or near two countries",
        DERIVE_SEA: "at sea, no country within " + km,
        DERIVE_NONE: "the point is in an area with no AntWeb country",
    }
    total, no_coords, zero, bad_lat, bad_lon = counts
    usable_count = total - no_coords - zero - bad_lat - bad_lon
    sp, pt = tally.specimens, tally.points
    with_country = sum(sp[c] for c in STORED_CLASSES)
    without = sum(sp[c] for c in DERIVE_CLASSES)
    conflicts = sum(sp[c] for c in CONFLICT_CLASSES)
    no_shape, no_country, other_way = lists
    out = ["AntWeb coordinates against the stored country, " + when + " (tool version " + VERSION + ", AntWeb commit " + (commit or "unknown") + ")"]
    if only:
        out += ["", "A quick run: only specimens stored under " + only + ". The counts below are for these only."]
    out += ["", "Specimens: " + n(total),
            "  " + n(usable_count).rjust(9) + "  with coordinates that can be used (" + n(sum(pt.values())) + " distinct combinations of country, point and group)",
            "  " + n(no_coords).rjust(9) + "  no coordinates",
            "  " + n(zero).rjust(9) + "  latitude 0 and longitude 0",
            "  " + n(bad_lat).rjust(9) + "  latitude outside -90 to 90",
            "  " + n(bad_lon).rjust(9) + "  longitude outside -180 to 180",
            "", "With a stored country and usable coordinates: " + n(with_country)]
    out += ["  " + n(sp[c]).rjust(9) + "  " + words[c] for c in STORED_CLASSES]
    out += ["", "With no country and usable coordinates (derive.csv): " + n(without)]
    out += ["  " + n(sp[c]).rjust(9) + "  " + words[c] for c in DERIVE_CLASSES]
    out += ["", "Conflicts: " + n(conflicts) + " specimens. The data-entry error that would make the point agree:"]
    out += lines_of(tally.conflict_errors, str)
    out += ["", "Conflicts by upload group:"] + lines_of(tally.conflict_groups, group, limit=200)
    out += ["", "Conflicts by stored country -> coordinate country (the 50 largest):"]
    out += lines_of(tally.conflict_pairs, lambda k: k[0] + " -> " + k[1])
    out += ["", "Countries that can be derived, by upload group:"] + lines_of(tally.derive_groups, group, limit=200)
    out += ["", "Countries that can be derived, by country:"] + lines_of(tally.derive_countries, str)
    out += ["", "In a territory or shared area of the stored country, stored country -> where the point is (the 50 largest):"]
    out += lines_of(tally.territory_pairs, lambda k: k[0] + " -> " + k[1])
    out += ["", "Not checked, by stored country (the 50 largest):"]
    out += lines_of(tally.not_checked, lambda k: k[0] + (" (not known to AntWeb)" if k[1] == UNKNOWN else " (no shape)"))
    out += ["", "The mapping of shapes to AntWeb countries (all of it: mapping.csv). Check these three lists:",
            "AntWeb valid countries with no shape (" + n(len(no_shape)) + "); their specimens are not checked:"] + wrap(no_shape)
    out += ["Shapes with no AntWeb country (" + n(len(no_country)) + "); a point in one is named by the shape:"] + wrap(no_country)
    out += ["Shapes matched in another way than by their own name or code (" + n(len(other_way)) + "):"] + wrap(other_way)
    if notes:
        out += ["Name and ISO code disagree (" + n(len(notes)) + "):"] + ["  " + t for t in notes]
    out += ["", "Shapes: " + shape_info, "Tolerance: " + km + " (--tolerance-km)", "Run time: " + seconds]
    out += ["", "Report only: nothing was changed in the database."]
    return "\n".join(out) + "\n"


# ---------------------------------------------------------------- the shape file

def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def fetch_shapes(folder, url=SHAPES_URL, sha256=SHAPES_SHA256):
    """The pinned shape file, fetched once into folder. A file with another checksum is never used."""
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, SHAPES_NAME[:-len(".geojson")] + "-" + SHAPES_COMMIT[:12] + ".geojson")
    if os.path.exists(path) and sha256_of(path) == sha256:
        return path
    part = path + ".part"
    with urllib.request.urlopen(url, timeout=120) as r, open(part, "wb") as f:
        for block in iter(lambda: r.read(1 << 20), b""):
            f.write(block)
    found = sha256_of(part)
    if found != sha256:
        os.remove(part)
        sys.exit("The shape file from " + url + " has sha256 " + found + ", expected " + sha256 + ". Nothing was done.")
    os.replace(part, path)
    return path


# ---------------------------------------------------------------- the database (read only)

USABLE = ("s.decimal_latitude is not null and s.decimal_longitude is not null "
          "and not (s.decimal_latitude = 0 and s.decimal_longitude = 0) "
          "and s.decimal_latitude between -90 and 90 and s.decimal_longitude between -180 and 180")

# One text for a point and its stored country, the same in both statements: the country and the island country
# as hex (exact, accents and capitals included), the coordinates to 5 decimals (about 1 metre).
KEY = ("concat(hex(ifnull(s.country, '')), '|', hex(ifnull(s.island_country, '')), '|', "
       "round(s.decimal_latitude, 5), '|', round(s.decimal_longitude, 5))")

COLUMNS_SQL = "select lower(column_name) from information_schema.columns where table_schema = database() and table_name = 'geolocale'"

GROUPS_SQL = "select id, hex(ifnull(name, '')) from ant_group"

KEYS_PER_STATEMENT = 20000


def countries_sql(columns):
    """The country rows of geolocale; the ISO columns only when the table has them."""
    iso2 = "hex(ifnull(isoCode, ''))" if "isocode" in columns else "''"
    iso3 = "hex(ifnull(iso3Code, ''))" if "iso3code" in columns else "''"
    return ("select hex(name), is_valid, hex(ifnull(valid_name, '')), ifnull(is_island, 0), hex(ifnull(country, '')), "
            + iso2 + ", " + iso3 + " from geolocale where name is not null and (georank = 'country' or (is_island = 1 and georank <> 'adm1'))")


def only_sql(names):
    """The condition of --countries: stored under one of these names ("none": no stored country)."""
    parts = []
    named = [x for x in names if x != ""]
    if named:
        parts.append("s.country in (" + ", ".join(sql_string(x) for x in named) + ")")
    if "" in names:
        parts.append("s.country is null or s.country = ''")
    return " and (" + " or ".join(parts) + ")" if parts else ""


def counts_sql(only=""):
    return ("select count(*), "
            "ifnull(sum(s.decimal_latitude is null or s.decimal_longitude is null), 0), "
            "ifnull(sum(s.decimal_latitude = 0 and s.decimal_longitude = 0), 0), "
            "ifnull(sum(s.decimal_longitude is not null and s.decimal_latitude not between -90 and 90), 0), "
            "ifnull(sum(s.decimal_latitude between -90 and 90 and s.decimal_longitude not between -180 and 180), 0) "
            "from specimen s where 1 = 1" + only)


def points_sql(only=""):
    """Each distinct stored country and point once for each upload group, with its number of specimens."""
    return "select " + KEY + ", ifnull(s.access_group, 0), count(*) from specimen s where " + USABLE + only + " group by 1, 2"


def specimens_sql(keys, only=""):
    """The specimens of the given points, for conflicts.csv and derive.csv."""
    return ("select hex(s.code), ifnull(s.access_group, 0), " + KEY + ", hex(ifnull(s.adm1, '')), hex(ifnull(s.localityname, '')) "
            "from specimen s where " + USABLE + only + " and " + KEY + " in (" + ", ".join(sql_string(k) for k in keys) + ")")


def sql_string(value):
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def unhex(value):
    return bytes.fromhex(value).decode("utf-8", "replace") if value and value != "NULL" else ""


CHANGING = re.compile(r"\b(insert|update|delete|replace|create|drop|alter|truncate|rename|grant|revoke|set|call|load|lock|"
                      r"handler|into|do|optimize|analyze|repair|flush|kill|start|commit|use)\b", re.I)
QUOTED = re.compile(r"'(?:[^'\\]|\\.|'')*'")


def read_only(sql):
    """The statement, when it is one select that changes nothing; otherwise an error. Every statement passes here."""
    bare = QUOTED.sub("''", sql)
    if not bare.lstrip().lower().startswith("select ") or ";" in bare or CHANGING.search(bare):
        raise ValueError("not a read-only statement: " + bare[:120])
    return sql


def db_login(project):
    props = {}
    with open(os.path.join(project, "etc", "db.properties"), encoding="utf-8") as f:
        for line in f:
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.rstrip("\n").split("=", 1)
                props[k.strip()] = v
    return props.get("user", ""), props.get("password", "")


def rows_sql(project, sql, container):
    """The rows of one select, one at a time as the database sends them (tab separated, no header)."""
    user, password = db_login(project)
    cmd = ["docker", "exec", "-i", "-e", "MYSQL_PWD", container, "mysql", "-u", user, "ant",
           "--default-character-set=utf8mb4", "--batch", "--quick", "-N"]
    with tempfile.TemporaryFile() as errors:
        p = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=errors, env=dict(os.environ, MYSQL_PWD=password))
        p.stdin.write(read_only(sql).encode("utf-8"))
        p.stdin.close()
        for line in p.stdout:
            line = line.decode("utf-8", "replace").rstrip("\n")
            if line:
                yield line.split("\t")
        p.stdout.close()
        if p.wait() != 0:
            errors.seek(0)
            sys.exit("mysql failed: " + errors.read().decode("utf-8", "replace").strip())


def run_sql(project, sql, container):
    return list(rows_sql(project, sql, container))


def read_countries(rows):
    return Countries([Country(unhex(r[0]), r[1], unhex(r[2]), r[3], unhex(r[4]), unhex(r[5]), unhex(r[6])) for r in rows])


def read_first_line(path):
    try:
        with open(path, encoding="utf-8") as f:
            return f.readline().strip()
    except OSError:
        return ""


def default_project():
    for p in ("/root/antweb", "/root/antweb-next"):
        if os.path.exists(os.path.join(p, "etc", "db.properties")) and not os.path.islink(p):
            return p
    return "/root/antweb"


def stored_names(countries, wanted):
    """--countries: every name AntWeb lists for the wanted countries (the valid name, its old names, its
    islands); "" stands for no stored country. Stops on a name AntWeb does not know."""
    names = set()
    for w in wanted:
        if w.lower() == "none":
            names.add("")
            continue
        c = countries.resolve(w)
        if c is None:
            sys.exit("--countries: AntWeb has no valid country for " + repr(w))
        family = {c.name} | countries.children.get(c.name, set())
        for row in countries.rows.values():
            target = countries.resolve(row.name)
            if target is not None and target.name in family:
                names.add(row.name)
    return sorted(names)


# ---------------------------------------------------------------- the run

HEADER = ["code", "upload_group", "stored_country", "state", "locality_name", "latitude", "longitude",
          "coordinate_country", "class", "distance_km", "likely_error"]


def split_key(key):
    hc, hi, lat, lon = key.split("|")
    return unhex(hc), unhex(hi), lat, lon


def check_points(checker, rows, tally):
    """Classifies each (key, group, count) row; returns the number of rows."""
    seen = 0
    for key, group, count in rows:
        country, island, lat, lon = split_key(key)
        result = checker.decide(country, island, float(lat), float(lon))
        valid = ""
        if country and result.cls not in (UNKNOWN, NO_SHAPE):
            valid = checker.countries.resolve(country).name
        tally.add(key, country, group, int(count), result, valid)
        seen += 1
    return seen


def csv_line(code, group_name, key, adm1, locality, result):
    country, _, lat, lon = split_key(key)
    return [code, group_name, country, adm1, locality, str(float(lat)), str(float(lon)), result.country, result.cls,
            "" if result.km is None else format(result.km, "g"), result.error]


def write_lists(folder, answers, flagged, groups):
    """conflicts.csv and derive.csv: one line per specimen of the flagged points, by upload group and code.
    answers: the rows of each specimens_sql statement (code, group, key, state, locality, the texts as hex)."""
    lines = {True: [], False: []}
    for rows in answers:
        for code, group, key, adm1, locality in rows:
            result = flagged.get(key)
            if result is not None:
                lines[result.cls in CONFLICT_CLASSES].append(
                    csv_line(unhex(code), groups.get(group, "group " + group), key, unhex(adm1), unhex(locality), result))
    for is_conflict, name in ((True, "conflicts.csv"), (False, "derive.csv")):
        with open(os.path.join(folder, name), "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(HEADER)
            w.writerows(sorted(lines[is_conflict], key=lambda line: (line[1], line[0])))
    return len(lines[True]), len(lines[False])


def write_mapping(path, shapes):
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["shape", "part_of", "type", "antweb_country", "how_matched", "also_accepted"])
        for s in sorted(shapes, key=lambda s: (s.admin, s.name)):
            w.writerow([s.name, s.admin, s.kind, s.country or "", s.how, "; ".join(sorted(s.also))])


def peak_mb():
    return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024.0


def main(argv=None):
    a = argparse.ArgumentParser(description="Compare each specimen's coordinates with its stored country. Report only.")
    a.add_argument("--tolerance-km", type=float, default=TOLERANCE_KM, help="how far outside a shape still counts as that country (default 10)")
    a.add_argument("--countries", default=None, help='a quick run: only specimens stored under these, e.g. "Mexico,Panama"; none = no country')
    a.add_argument("--shapes", default=None, help="a local GeoJSON file of country shapes (default: Natural Earth, fetched once)")
    a.add_argument("--project", default=None, help="AntWeb project folder (default /root/antweb or /root/antweb-next)")
    a.add_argument("--container", default="antweb_mysql_1")
    a.add_argument("--out", default=None, help="output folder (default /root/antweb-place-fix/<date>-<time>-coordinates)")
    args = a.parse_args(argv)
    if not 0 <= args.tolerance_km <= 500:
        sys.exit("--tolerance-km: between 0 and 500")
    project = args.project or default_project()
    now = datetime.datetime.now()
    started = time.time()
    folder = args.out or os.path.join("/root/antweb-place-fix", now.strftime("%Y-%m-%d-%H%M") + "-coordinates")
    commit = read_first_line(os.path.join(project, "etc", "git-commit"))

    columns = {r[0] for r in run_sql(project, COLUMNS_SQL, args.container)}
    countries = read_countries(run_sql(project, countries_sql(columns), args.container))
    groups = {r[0]: unhex(r[1]) for r in run_sql(project, GROUPS_SQL, args.container)}
    only, only_text = "", None
    if args.countries is not None:
        wanted = [w.strip() for w in args.countries.split(",") if w.strip()]
        names = stored_names(countries, wanted)
        only, only_text = only_sql(names), ", ".join(wanted)
    os.makedirs(folder, exist_ok=True)

    if args.shapes:
        path, source = args.shapes, "a local file (--shapes), commit not known"
    else:
        path, source = fetch_shapes(SHAPES_FOLDER), "Natural Earth, " + SHAPES_URL + ", commit " + SHAPES_COMMIT
    with open(path, encoding="utf-8") as f:
        shapes = read_shapes(json.load(f))
    notes = map_shapes(shapes, countries)
    index = Index(shapes, forget=True)
    shape_info = (path + " (" + n(len(shapes)) + " shapes; " + source + "; sha256 " + sha256_of(path) + ")")
    built = time.time()

    checker = Checker(countries, shapes, index, args.tolerance_km)
    counts = [int(float(x)) for x in run_sql(project, counts_sql(only), args.container)[0]]
    tally = Tally()
    check_points(checker, rows_sql(project, points_sql(only), args.container), tally)
    checked = time.time()

    keys = sorted(tally.flagged)
    statements = (specimens_sql(keys[i:i + KEYS_PER_STATEMENT], only) for i in range(0, len(keys), KEYS_PER_STATEMENT))
    write_lists(folder, (rows_sql(project, sql, args.container) for sql in statements), tally.flagged, groups)
    write_mapping(os.path.join(folder, "mapping.csv"), shapes)

    done = time.time()
    seconds = (format(done - started, ".0f") + " seconds (shapes and index " + format(built - started, ".0f") + ", points "
               + format(checked - built, ".0f") + ", the two lists " + format(done - checked, ".0f") + "); memory at the peak "
               + n(peak_mb()) + " MB")
    text = report_text(now.strftime("%Y-%m-%d %H:%M"), commit, counts, tally, groups, args.tolerance_km,
                       mapping_lists(shapes, countries), notes, shape_info, seconds, only=only_text)
    with open(os.path.join(folder, "report.txt"), "w", encoding="utf-8") as f:
        f.write(text)
    print(text)
    print("Files: " + folder)
    return 0


if __name__ == "__main__":
    sys.exit(main())
