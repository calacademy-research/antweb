#!/usr/bin/env python3
"""Coordinate country: compare the coordinates of each specimen with its stored country (report only).

Why (Brian Fisher, 2026-11-10: "if there is no country but lat long then we should derive the country: also lat
long could be in conflict with the country given, that should result in an upload report so it is verified"):
AntWeb stores a country (text, from the curator's file) and a latitude and longitude with each specimen, and
nothing compares the two. Production 2026-11-10: 1,920 specimens with no country, 77 of them with coordinates.
This tool is the first step, a report an administrator runs on the server. The same rule goes into the upload
later. It never writes to the database: every statement it sends is a single select (read_only() refuses
anything else), and it needs no more than the right to read.

  python3 coordinate_country.py --shapes-source geoboundaries    the whole table, detailed shapes near borders
  python3 coordinate_country.py                                   the whole table, Natural Earth alone
  python3 coordinate_country.py --countries "Mexico,Panama"       a quick run: only specimens stored under these
  python3 coordinate_country.py --countries none                  a quick run: only specimens with no country
  python3 coordinate_country.py --tolerance-km 2                  another tolerance (default 1)
  python3 coordinate_country.py --shapes FILE                     a local file in place of the Natural Earth file

The rule (Brian Fisher on the first version, which accepted a point up to 10 km outside its country: "it will
miss flagging those in the water: why not 1 km?"). Nothing outside a shape agrees silently:

  inside        the point is inside its stored country. An island country and its parent country agree both
                ways (United States with island country Hawaii: a Hawaii or a United States shape).
  in the water or across the border    the point is outside its stored country: always listed with its
                distance. Up to the allowed distance it is not flagged (near.csv); beyond, it is flagged
                (conflicts.csv), with the data-entry error that would bring it into the stored country:
                longitude sign, latitude sign, both signs, latitude and longitude swapped.
  the allowed distance    1 km (--tolerance-km), or the specimen's own stated error (column latlonmaxerror)
                when that is larger. AntWeb stores the curator's text: "1 minute" (1.852 km, the usual value
                of the CAS file), "0.1 minute", "3 km", "150 m"; a bare number is read as metres; minutes,
                seconds, miles and feet are read. A text that cannot be read counts as none: 1 km.
  the bands     the report counts the points outside in bands of the distance: up to 1 km, 1 to 2, 2 to 5,
                5 to 10, over 10 km, flagged and not, for each kind of shape, and says which bands that kind
                of shape can tell apart.
  inside a territory or shared area of the stored country    stored France, the point in Reunion; stored
                Russia, the point in Crimea. Listed by pair in the report. Not flagged.
  not checked   the stored name is not known to AntWeb, or its country has no shape. Never flagged.
  no stored country    inside exactly one country: that country ("country can be derived, inside"). In the
                water, within the allowed distance of exactly one country: that country, as "near". Otherwise
                nothing is derived and the nearest country and the distance are given (derive.csv).
  not usable    no coordinates, 0 and 0, latitude outside -90 to 90, longitude outside -180 to 180: counted.
  The stored country is read after AntWeb's own old-name mapping (USA: United States) and the spelling rule of
  place_fix.py (México: Mexico).

Two sets of shapes.

  Natural Earth 1:10m "map subunits" (public domain; github.com/nvkelso/natural-earth-vector at SHAPES_COMMIT,
  14 MB, sha256 checked): every country of the world in one file, always used. It says which country a point
  is in. Its coast is up to 5 km off (measured against the detailed coast of Mexico and Madagascar: farthest
  5.1 km; of 80 ports of the United States 5 lay 1 to 3 km outside it), so alone it cannot carry a 1 km rule:
  with it, read the bands under 5 km as within the shape's own error. "Map subunits" because AntWeb lists
  many territories as countries: the "countries" file draws Reunion, Martinique, Guadeloupe, French Guiana and
  Mayotte inside France, "map units" keeps Hawaii and the Galapagos inside their country; a shape AntWeb does
  not list (Alaska, England) goes to the country it is part of. 1:10m and not 1:50m: of 50 small places,
  most of them islands where ants are collected, 5 are at sea at 1:10m and 22 at 1:50m.

  geoBoundaries gbOpen ADM0 (--shapes-source geoboundaries; github.com/wmgeolab/geoBoundaries at GB_COMMIT):
  one file for each ISO code, 229 files, 710 MB in all, each under its own licence (ODbL, CC BY, public
  domain ...: the report names the licence of every file used). 160 files are detailed (their points a median
  of 2 to 236 m apart: Mexico 23 m, Madagascar 91 m, Indonesia 57 m, New Zealand 5 m); 69 are coarse
  (France 0.6 km, United States 1.4 km, United Kingdom 2 km, Colombia 8 km, China 12 km).
    When: a point is tested on the detailed file when Natural Earth has it within 10 km of a border of its
    stored country (inside or outside), or in the water within 100 km of it (an islet Natural Earth leaves
    out). Every other point is far from every border and Natural Earth's answer cannot change.
    Which file: the ISO code of the Natural Earth shape the point is in or nearest to (Hawaii: USA; Reunion:
    REU; Guam: GUM); a shape without a code takes the code AntWeb has for its country (geolocale.iso3Code),
    else the code of the Natural Earth shape matched to that country.
    Natural Earth's answer stands, and the report says for how many specimens and why, when: geoBoundaries
    has no file for the code (17 codes: Puerto Rico, Hong Kong, Western Sahara ...); the file has no more
    points than Natural Earth's own shape of the country (30 of the coarse files: United States, China, United
    Kingdom, France ...: Natural Earth is then the finer of the two); the file draws nothing within 25 km of
    the point (a territory the country's file leaves out); the file could not be fetched.
    One country at a time: the points waiting for a file are kept by ISO code and answered country by
    country; two countries are in memory at most. A file is read piece by piece, not as a whole, and points
    closer than 50 m to the last one kept are left out (the line moves by less than 50 m): New Zealand's
    107 MB and 2.7 million points take 3 seconds and 80 MB.
    Checksums: the files are Git LFS objects. The pointer file of the pinned commit names the sha256 of the
    data, and data with another sha256 is refused. The lock file geoboundaries-<commit>.lock.json, next to
    the shapes, keeps the sha256, the size and the licence of every file fetched; a later run uses a kept
    file only when its bytes are still the same.
    Is 1 km honest with them? Measured 2026-11-10 on 200 known coastal points (ports of the World Port Index
    and coastal towns, from Natural Earth's own lists). Detailed files, Mexico, Madagascar, Indonesia, 120
    points: 94 inside, 24 less than 1 km outside, 2 port positions 7 and 10 km out at sea on every map
    (Senipah, an oil terminal, and Surabaya). The same points on Natural Earth: 5 lie 1 to 2 km outside. A
    coarse file, United States, 80 ports: 4 lie 1 to 2 km outside the geoBoundaries file, 5 lie 1 to 3 km
    outside Natural Earth. So: 1 km holds where the file is detailed; where it is coarse or missing, the
    report says so and the bands under 2 km (coarse file) or 5 km (Natural Earth) are within the shape's error.

Which AntWeb country a Natural Earth shape is (map_shapes): the shape's own names against AntWeb's valid
countries (capitals and accents ignored, AntWeb's old names followed to the valid name), then its own ISO codes
(geolocale.iso3Code and isoCode), then the alias table ALIASES. If none matches: the first country of the
shared-area table SHARED, then the same three steps for the unit, the country and the sovereign country the
shape is part of. The name comes before the code because several small shapes carry the code of a larger one
(Juan de Nova Island: ATF, the French Southern Territories). The report lists the AntWeb valid countries that
got no shape, the shapes with no AntWeb country, and every shape matched in another way than by its own name or
code; mapping.csv has all of them. Read these lists before trusting the numbers.

It writes one folder (default /root/antweb-place-fix/<date>-<time>-coordinates/):

  report.txt       the totals, the bands, flagged specimens by upload group and by stored country -> where the
                   point is, countries that can be derived, the mapping lists, the shapes with their commits,
                   checksums and licences, the run time. Also printed.
  conflicts.csv    one line per flagged specimen: code, upload group, stored country, state, locality name,
                   latitude, longitude, coordinate country, class, distance km, likely error, stated error,
                   allowed km, the shape that decided, a note when Natural Earth's answer stood.
  near.csv         the same columns: outside the stored country, within the allowed distance.
  derive.csv       the same columns: every specimen with usable coordinates and no country.
  mapping.csv      every Natural Earth shape, its AntWeb country, how it was matched, the code of its file.
  shape_files.csv  with geoboundaries: every file asked for, its size, points, licence, checksum, used or not.

Speed (measured 2026-11-10 on 2 CPUs with Python 3.13 and MariaDB 10.11, a made-up table of 2,500,000 specimens
at 400,000 places near coasts and borders, 1,025,000 distinct combinations): Natural Earth alone 53 seconds and
300 MB of memory; with the detailed files of 228 countries already fetched 132 seconds and 490 MB. Fetching
all 229 files (710 MB) and reading them took 5 minutes more, once. 100,000 points near the line of a detailed
file: 3 seconds. The index is a grid of cells: a cell no border runs through is answered at once; in a cell
with a border, only the border pieces inside it are tested.

Known limits. Natural Earth draws borders as they are run today (Crimea with Russia, Somaliland apart from
Somalia): the table SHARED lists the areas where more than one stored country is accepted. It cannot help
where a disputed area is drawn inside a country's own shape: Western Sahara west of the wall (Laayoune) is
drawn as Morocco, the Golan and East Jerusalem as Israel, Kashmir is split along the line of control, Abyei is
Sudan, the Hala'ib triangle Egypt. A conflict there is a matter of opinion. The detailed files come from many
sources: two neighbours' lines need not meet, and a lake can be water in one (the Great Lakes in the file of
the United States) and land in Natural Earth. "Coarse" is judged by the distance between a file's points, so
a country of long straight borders counts as coarse. Islets no shape draws show as "in the water".

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
import urllib.error
import urllib.request

VERSION = "2"

SHAPES_COMMIT = "ca96624a56bd078437bca8184e78163e5039ad19"
SHAPES_NAME = "ne_10m_admin_0_map_subunits.geojson"
SHAPES_URL = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/" + SHAPES_COMMIT + "/geojson/" + SHAPES_NAME
SHAPES_SHA256 = "76896018b9265072d8063e118e46df765be0ceb54a803b1a2571ebe25b36a071"
SHAPES_FOLDER = "/root/antweb-place-fix/shapes"
NATURAL_EARTH = "Natural Earth 1:10m"
NATURAL_EARTH_ERROR_KM = 5.0     # measured: its mainland coast is up to 5.1 km off (Mexico, Madagascar)

# The detailed shapes: geoBoundaries gbOpen ADM0, one file per ISO code (github.com/wmgeolab/geoBoundaries).
# The files are Git LFS objects: the small pointer file of the pinned commit names the sha256 of the data.
GB_COMMIT = "8ce2700661a28ee944350e2b329bbabb58d2ff83"
GB_PATH = "/wmgeolab/geoBoundaries/{commit}/releaseData/gbOpen/{iso}/ADM0/geoBoundaries-{iso}-ADM0{ext}"
GB_POINTER = "https://raw.githubusercontent.com" + GB_PATH
GB_DATA = "https://media.githubusercontent.com/media" + GB_PATH
GEOBOUNDARIES = "geoBoundaries"

TOLERANCE_KM = 1.0               # a point farther outside its stored country than this is flagged
BANDS = [(1.0, "up to 1 km"), (2.0, "1 to 2 km"), (5.0, "2 to 5 km"), (10.0, "5 to 10 km"), (float("inf"), "over 10 km")]
ZONE_KM = 10.0                   # a point this close to a Natural Earth border is tested on the detailed shape
REACH_KM = 100.0                 # a point in the water this close to its country too (an islet Natural Earth leaves out)
DETAIL_KM = 25.0                 # a detailed file with no line this close to the point does not draw the place
THIN_M = 50.0                    # points of a detailed line closer together than this are left out
COARSE_M = 250.0                 # a detailed file whose points are farther apart than this (median) is called coarse

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

INSIDE = "inside"
TERRITORY = "inside a territory or shared area of the stored country"
NEAR = "in the water or across the border, within the allowed distance"
FLAG = "flagged: in the water or across the border"
UNKNOWN = "not checked, country not known to AntWeb"
NO_SHAPE = "not checked, no shape for the stored country"
DERIVE_IN = "country can be derived, inside"
DERIVE_NEAR = "country can be derived, near"
TWO = "two candidates"
DERIVE_FAR = "not derived, in the water"
DERIVE_NONE = "in an area with no AntWeb country"
STORED_CLASSES = [INSIDE, TERRITORY, NEAR, FLAG, UNKNOWN, NO_SHAPE]
DERIVE_CLASSES = [DERIVE_IN, DERIVE_NEAR, TWO, DERIVE_FAR, DERIVE_NONE]
WATER = "in the water"
WATER_OR_BORDER = "in the water or across the border"


# ---------------------------------------------------------------- the error a curator stated (latlonmaxerror)

# AntWeb stores the text of the curator's file: "1 minute" (the usual value of the CAS file), "0.1 minute",
# "3 km", "150 m", and from TaxonWorks the uncertainty as written. A bare number is read as metres.
ERROR_TEXT = re.compile(r"^(?:[<≤~±]|ca\.?|about|approx\.?|\+/-)?\s*(\d+(?:[.,]\d+)*)\s*([a-zµ°'\"′″]*)\.?$")
ERROR_UNITS_KM = {
    "": 0.001, "m": 0.001, "meter": 0.001, "meters": 0.001, "metre": 0.001, "metres": 0.001,
    "km": 1.0, "kilometer": 1.0, "kilometers": 1.0, "kilometre": 1.0, "kilometres": 1.0,
    "mi": 1.609344, "mile": 1.609344, "miles": 1.609344, "ft": 0.0003048, "foot": 0.0003048, "feet": 0.0003048,
    "minute": 1.852, "minutes": 1.852, "min": 1.852, "'": 1.852, "′": 1.852,
    "second": 1.852 / 60, "seconds": 1.852 / 60, "sec": 1.852 / 60, '"': 1.852 / 60, "″": 1.852 / 60,
    "degree": 111.195, "degrees": 111.195, "deg": 111.195, "°": 111.195,
}


_stated = {}


def stated_km(text):
    """The stated error in km, or None when the text is blank or cannot be read as a distance."""
    if text not in _stated:
        if len(_stated) > 100000:
            _stated.clear()
        _stated[text] = read_stated(text)
    return _stated[text]


def read_stated(text):
    m = ERROR_TEXT.match(" ".join((text or "").lower().split()))
    if not m or m.group(2) not in ERROR_UNITS_KM:
        return None
    number = m.group(1)
    if re.match(r"^\d{1,3}(,\d{3})+$", number):
        number = number.replace(",", "")                 # 1,000 m
    number = number.replace(",", ".")                    # 0,5 km
    if number.count(".") > 1:
        return None
    km = float(number) * ERROR_UNITS_KM[m.group(2)]
    return km if 0.0 < km <= 20000.0 else None


def allowed_km(text, tolerance):
    """How far outside its stored country a point may lie before it is flagged: the tolerance, or the specimen's
    own stated error when that is larger."""
    km = stated_km(text)
    return tolerance if km is None or km < tolerance else km


def band_of(km):
    for i, (limit, _) in enumerate(BANDS):
        if km <= limit:
            return i
    return len(BANDS) - 1


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
        # the ISO code of the detailed file that draws this shape: its own code, else its country's
        self.iso3 = next((c for c in distinct([g("ISO_A3"), g("shapeGroup"), g("ISO_A3_EH")]) if ISO3.match(c)), "")
        self.admin = g("ADMIN")
        self.levels = [
            ("itself", own, distinct([g("ISO_A3"), g("ISO_A2"), g("shapeGroup")])),
            ("part of " + g("GEOUNIT"), distinct([g("GEOUNIT")]), []),
            ("part of " + g("ADMIN"), distinct([g("ADMIN")]), distinct([g("ISO_A3_EH"), g("ISO_A2_EH")])),
            ("belongs to " + g("SOVEREIGNT"), distinct([g("SOVEREIGNT")]), []),
        ]
        self.polygons = polygons
        self.points = sum(len(ring) for polygon in polygons for ring in polygon)
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

CELL = 0.25                 # degrees: the cell of the world-wide index, 1,440 columns by 720 rows
BLOCK = 8                   # cells: the far-distance search looks at blocks of 8 by 8 cells
# The fixed point of a cell that a test walks to. Not the middle: a straight border between round coordinates
# can run exactly through the middle of a cell, never through these fractions of it.
FX, FY = 0.4142135623730951, 0.5772156649015329
EARTH_KM = 6371.0088
KM_PER_DEGREE = EARTH_KM * math.pi / 180.0


def km_between(lat1, lon1, lat2, lon2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    a = math.sin((p2 - p1) / 2.0) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lon2 - lon1) / 2.0) ** 2
    return 2.0 * EARTH_KM * math.asin(min(1.0, math.sqrt(a)))


class Index:
    """A grid of cells, over the world or over one country. For each cell: the shapes that cover it whole (no
    border inside), and for each shape with a border inside it, the border pieces and whether the cell's fixed
    point is inside the shape. A point is tested by walking from it to the fixed point of its cell and counting
    the border pieces crossed.

    bounds (west, south, east, north) limits the grid to one country, with a smaller cell; a country that
    reaches round the 180 meridian gets the full width."""

    def __init__(self, shapes, forget=False, cell=CELL, bounds=None):
        self.cell, self.inv = cell, 1.0 / cell
        if bounds is None:
            west, south, east, north = -180.0, -90.0, 180.0, 90.0
        else:
            west, south, east, north = bounds
            if east - west > 300.0:
                west, east = -180.0, 180.0
            else:
                west, east = math.floor(west / cell - 1.0) * cell, east + cell
            south, north = max(-90.0, math.floor(south / cell - 1.0) * cell), min(90.0, north + cell)
        self.west, self.south = west, south
        self.ncols = max(1, int(math.ceil((east - west) / cell - 1e-9)))
        self.nrows = max(1, int(math.ceil((north - south) / cell - 1e-9)))
        self.round = west == -180.0 and self.ncols * cell >= 360.0 - 1e-9     # the grid goes round the world
        self.bcols = self.ncols // BLOCK + 1
        self.whole = array.array("h", [-1]) * (self.ncols * self.nrows)       # shape number; -2: see self.several
        self.several = {}
        self.cells = {}                                          # cell -> {shape number: [fixed point inside, pieces]}
        self.blocks = [collections.defaultdict(list) for _ in shapes]   # shape -> block -> cells with its border
        self._far = {}
        self._quiet = {}
        for number, shape in enumerate(shapes):
            self._add(number, shape)
            if forget:
                shape.polygons = None                            # the index holds the borders now: less memory

    def fixed_x(self, col):
        return self.west + (col + FX) * self.cell

    def fixed_y(self, row):
        return self.south + (row + FY) * self.cell

    def col_of(self, x):
        return min(max(int((x - self.west) * self.inv), 0), self.ncols - 1)

    def row_of(self, y):
        return min(max(int((y - self.south) * self.inv), 0), self.nrows - 1)

    def pieces_cells(self, x1, y1, x2, y2):
        """The cells a border piece runs through (a long piece is walked in short steps, so that a slanted
        border of ten degrees is not filed in every cell of the square around it)."""
        ncols = self.ncols
        c1, r1, c2, r2 = self.col_of(x1), self.row_of(y1), self.col_of(x2), self.row_of(y2)
        if c1 == c2 and r1 == r2:
            return (r1 * ncols + c1,)
        if abs(c1 - c2) <= 1 and abs(r1 - r2) <= 1:
            return {r * ncols + c for r in (r1, r2) for c in (c1, c2)}
        out = set()
        steps = 2 * max(abs(c1 - c2), abs(r1 - r2))
        pc, pr = c1, r1
        for i in range(1, steps + 1):
            t = i / steps
            c, r = self.col_of(x1 + t * (x2 - x1)), self.row_of(y1 + t * (y2 - y1))
            for rr in range(min(pr, r), max(pr, r) + 1):
                for cc in range(min(pc, c), max(pc, c) + 1):
                    out.add(rr * ncols + cc)
            pc, pr = c, r
        return out

    def _add(self, number, shape):
        cells, blocks = self.cells, self.blocks[number]
        ncols, nrows, inv, west, south, bcols = self.ncols, self.nrows, self.inv, self.west, self.south, self.bcols
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
                    for cell in self.pieces_cells(x1, y1, x2, y2):
                        d = cells.get(cell)
                        if d is None:
                            d = cells[cell] = {}
                        entry = d.get(number)
                        if entry is None:
                            entry = d[number] = [False, []]
                            blocks[(cell // ncols // BLOCK) * bcols + (cell % ncols) // BLOCK].append(cell)
                        entry[1].append(piece)
                    if y1 != y2:
                        lo, hi = (y1, y2) if y1 < y2 else (y2, y1)
                        first = max(int(math.ceil((lo - south) * inv - FY)) - 1, 0)
                        last = min(int(math.floor((hi - south) * inv - FY)) + 1, nrows - 1)
                        for row in range(first, last + 1):
                            cy = self.fixed_y(row)
                            if (y1 > cy) != (y2 > cy):
                                crossings[row].append(x1 + (cy - y1) * (x2 - x1) / (y2 - y1))
                    x1, y1 = x2, y2
        whole, several = self.whole, self.several
        for row, xs in crossings.items():
            xs.sort()
            for k in range(0, len(xs) - 1, 2):
                # the cells whose fixed point is between the two crossings: xs[k] <= fixed_x(col) < xs[k + 1]
                first = max(int(math.ceil((xs[k] - west) * inv - FX)) - 1, 0)
                last = min(int(math.ceil((xs[k + 1] - west) * inv - FX)), ncols - 1)
                while first <= last and self.fixed_x(first) < xs[k]:
                    first += 1
                while last >= first and self.fixed_x(last) >= xs[k + 1]:
                    last -= 1
                for col in range(first, last + 1):
                    cell = row * ncols + col
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
        x, y = (lon - self.west) * self.inv, (lat - self.south) * self.inv
        if x < 0.0 or y < 0.0 or x > self.ncols or y > self.nrows:
            return []                                            # outside the grid of this country
        col, row = min(int(x), self.ncols - 1), min(int(y), self.nrows - 1)
        cell = row * self.ncols + col
        w = self.whole[cell]
        found = [w] if w >= 0 else (list(self.several[cell]) if w == -2 else [])
        d = self.cells.get(cell)
        if d:
            cx, cy = self.fixed_x(col), self.fixed_y(row)
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

    def quiet(self, lat, lon, km):
        """True when no border piece is within km of any point of the cell that holds the point: near() would
        find nothing. Remembered for each cell, so that the many points far from every border cost one look."""
        x, y = (lon - self.west) * self.inv, (lat - self.south) * self.inv
        if x < 0.0 or y < 0.0 or x > self.ncols or y > self.nrows:
            return False
        ncols, nrows = self.ncols, self.nrows
        col, row = min(int(x), ncols - 1), min(int(y), nrows - 1)
        key = (row * ncols + col, km)
        answer = self._quiet.get(key)
        if answer is None:
            reach = km / KM_PER_DEGREE
            rows = int(math.ceil(reach * self.inv))
            widest = min(max(abs(self.south + row * self.cell - reach), abs(self.south + (row + 1) * self.cell + reach)), 89.9)
            cols = min(int(math.ceil(reach / math.cos(math.radians(widest)) * self.inv)), ncols // 2)
            answer = True
            for r in range(max(row - rows, 0), min(row + rows, nrows - 1) + 1):
                for c in range(col - cols, col + cols + 1):
                    if self.round:
                        c %= ncols
                    elif c < 0 or c >= ncols:
                        continue
                    if r * ncols + c in self.cells:
                        answer = False
                        break
                if not answer:
                    break
            self._quiet[key] = answer
        return answer

    def near(self, lat, lon, km):
        """{shape number: km} for every shape with a border within km of the point (the 180 meridian included)."""
        reach = km / KM_PER_DEGREE
        scale = math.cos(math.radians(lat))
        reach_x = reach / max(scale, 0.01)
        ncols, inv = self.ncols, self.inv
        row0 = max(int(math.floor((lat - reach - self.south) * inv)), 0)
        row1 = min(int(math.floor((lat + reach - self.south) * inv)), self.nrows - 1)
        col0 = int(math.floor((lon - reach_x - self.west) * inv))
        col1 = int(math.floor((lon + reach_x - self.west) * inv))
        if not self.round:
            col0, col1 = max(col0, 0), min(col1, ncols - 1)
        elif col1 - col0 >= ncols:
            col0, col1 = 0, ncols - 1
        kx, ky = KM_PER_DEGREE * scale, KM_PER_DEGREE
        limit = km * km
        best = {}
        cells = self.cells
        for row in range(row0, row1 + 1):
            for c in range(col0, col1 + 1):
                col = c % ncols
                d = cells.get(row * ncols + col)
                if not d:
                    continue
                px = lon - 360.0 * ((c - col) // ncols)          # the point seen from the other side of 180
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
        Blocks of 8 by 8 cells are tried nearest first (by the angle to the middle of the block); a block or a
        cell that cannot beat the best is skipped."""
        key = tuple(sorted(numbers))
        ncols, cell_size, bcols = self.ncols, self.cell, self.bcols
        size = cell_size * BLOCK
        blocks = self._far.get(key)
        if blocks is None:
            merged = collections.defaultdict(list)
            for number in key:
                for block, cells in self.blocks[number].items():
                    merged[block].extend((cell, number) for cell in cells)
            blocks = self._far[key] = []
            for b, cells in merged.items():
                la = math.radians(self.south + (b // bcols + 0.5) * size)
                lo = math.radians(self.west + (b % bcols + 0.5) * size)
                blocks.append((math.cos(la) * math.cos(lo), math.cos(la) * math.sin(lo), math.sin(la), cells))
        if not blocks:
            return None
        la, lo = math.radians(lat), math.radians(lon)
        px, py, pz = math.cos(la) * math.cos(lo), math.cos(la) * math.sin(lo), math.sin(la)
        order = sorted(((ux * px + uy * py + uz * pz, i) for i, (ux, uy, uz, _) in enumerate(blocks)), reverse=True)
        radius = 0.7072 * size * KM_PER_DEGREE                   # no point of a block is farther from its middle
        best = float("inf")
        kx = math.cos(la)
        for dot, i in order:
            if math.acos(max(-1.0, min(1.0, dot))) * EARTH_KM - radius > best:
                break
            for cell, number in blocks[i][3]:
                south, west = self.south + (cell // ncols) * cell_size, self.west + (cell % ncols) * cell_size
                if box_km(lat, lon, south, west, cell_size) - 2.0 * cell_size * KM_PER_DEGREE > best:
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


# ---------------------------------------------------------------- the detailed shapes, one country at a time

PAIR = re.compile(rb"\[\s*(-?\d[\d.eE+-]*)\s*,\s*(-?\d[\d.eE+-]*)[^\[\]]*\](\s*\])?")


def read_rings(path, thin_m=THIN_M, block_size=1 << 20):
    """The rings of every polygon of a GeoJSON file, read piece by piece so that a file of 100 MB does not fill
    the memory. Points closer than thin_m to the last one kept are left out (the line moves by less than
    thin_m). Returns (rings, points in the file, median distance in metres between the file's points).
    Holes and pieces need not be told apart: a point is inside when it is inside an odd number of rings."""
    rings, ring, start = [], [], []
    points, gaps = 0, []
    limit = (thin_m / 1000.0 / KM_PER_DEGREE) ** 2
    scale = kx = ky = px = py = 0.0

    def close():
        kept = ring if len(ring) >= 3 else start       # a ring smaller than thin_m keeps its first points
        if len(kept) >= 3:
            if kept[0] != kept[-1]:
                kept.append(kept[0])
            rings.append(kept)

    with open(path, "rb") as f:
        data, in_coordinates = b"", False
        while True:
            block = f.read(block_size)
            data += block
            while data:
                if not in_coordinates:
                    at = data.find(b'"coordinates"')
                    if at < 0:
                        data = data[-16:] if block else b""
                        break
                    data, in_coordinates = data[at + 13:], True
                end = data.find(b"}")
                part = data if end < 0 else data[:end]
                if end < 0 and block:
                    cut = part.rfind(b"[")                    # the last, perhaps unfinished, point waits for more
                    part, data = (part[:cut], data[cut:]) if cut >= 0 else (b"", data)
                else:
                    data, in_coordinates = (data[end + 1:] if end >= 0 else b""), False
                for m in PAIR.finditer(part):
                    x, y = float(m.group(1)), float(m.group(2))
                    points += 1
                    if not ring:
                        scale = math.cos(math.radians(y))
                        ring, start = [(x, y)], [(x, y)]
                        kx, ky = x, y
                    else:
                        if len(gaps) < 50000 or points % 20 == 0:       # the distances between the file's points, a sample of a long file
                            gaps.append(math.hypot((x - px) * scale, y - py))
                        if len(start) < 8:
                            start.append((x, y))
                        dx, dy = (x - kx) * scale, y - ky
                        if dx * dx + dy * dy >= limit or m.group(3) is not None:
                            ring.append((x, y))
                            kx, ky = x, y
                    px, py = x, y
                    if m.group(3) is not None:
                        close()
                        ring, start = [], []
                if end < 0 and block:
                    break
            if not block:
                break
    if ring:
        close()
    gaps.sort()
    median = gaps[len(gaps) // 2] * KM_PER_DEGREE * 1000.0 if gaps else 0.0
    return rings, points, median


class Rings:
    """What the index needs of a shape: one polygon made of all the rings."""

    def __init__(self, rings):
        self.polygons = [rings]


def index_of_rings(rings):
    """An index over one country: a cell small enough for a detailed coast, a grid of about two million cells."""
    xs = [p[0] for ring in rings for p in ring]
    ys = [p[1] for ring in rings for p in ring]
    west, south, east, north = min(xs), min(ys), max(xs), max(ys)
    width = 360.0 if east - west > 300.0 else east - west
    cell = min(CELL, max(0.01, math.sqrt(max(width, 0.1) * max(north - south, 0.1) / 2.0e6)))
    return Index([Rings(rings)], forget=True, cell=cell, bounds=(west, south, east, north))


def fetch_bytes(url, limit=None):
    """The bytes at url (limit: no more than this many); None when the address has no file (404)."""
    try:
        with urllib.request.urlopen(url, timeout=120) as r:
            return r.read() if limit is None else r.read(limit)
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        raise
    except urllib.error.URLError as e:
        if isinstance(e.reason, FileNotFoundError):       # a file: address, in the tests
            return None
        raise


class Detail:
    """The detailed shape of one country at a time: geoBoundaries gbOpen ADM0, one file for each ISO code.

    A file is fetched when a point needs it, into folder, at the pinned commit. The pointer file of that
    commit names the sha256 of the data; the data is used only when it has that sha256. The lock file
    (geoboundaries-<commit>.lock.json, next to the shapes) keeps the sha256, the size and the licence of every
    file fetched: a later run uses a file only when its bytes are still the same. Two countries are kept in
    memory at most (keep), one while the waiting points are answered country by country."""

    def __init__(self, folder, commit=GB_COMMIT, pointer_url=GB_POINTER, data_url=GB_DATA, thin_m=THIN_M):
        self.folder, self.commit, self.pointer_url, self.data_url, self.thin_m = folder, commit, pointer_url, data_url, thin_m
        self.lock_path = os.path.join(folder, "geoboundaries-" + commit[:12] + ".lock.json")
        self.lock = {"source": "github.com/wmgeolab/geoBoundaries, releaseData/gbOpen/<ISO>/ADM0", "commit": commit, "files": {}}
        if os.path.exists(self.lock_path):
            with open(self.lock_path, encoding="utf-8") as f:
                self.lock = json.load(f)
            if self.lock.get("commit") != commit:
                sys.exit(self.lock_path + " is for another commit (" + str(self.lock.get("commit")) + "). Nothing was done.")
        self.used = {}                                   # ISO code -> what the report says about its file
        self.keep = 2                                    # how many countries stay in memory
        self._loaded = collections.OrderedDict()         # ISO code -> Index, the ones used last

    def _save_lock(self):
        os.makedirs(self.folder, exist_ok=True)
        with open(self.lock_path + ".part", "w", encoding="utf-8") as f:
            json.dump(self.lock, f, indent=1, sort_keys=True)
            f.write("\n")
        os.replace(self.lock_path + ".part", self.lock_path)

    def _url(self, template, iso, ext):
        return template.format(commit=self.commit, iso=iso, ext=ext)

    def _fetch(self, iso, ext, path=None):
        """One file of the pinned commit, checked against the sha256 its pointer names. Returns (bytes or None,
        sha256, size); with path the data is written there instead. (None, "", 0): there is no such file."""
        pointer = fetch_bytes(self._url(self.pointer_url, iso, ext), limit=1 << 16)
        if pointer is None:
            return None, "", 0
        if pointer.startswith(b"version https://git-lfs"):
            wanted = pointer.split(b"oid sha256:")[1].split()[0].decode("ascii")
            data = fetch_bytes(self._url(self.data_url, iso, ext))
            if data is None:
                return None, "", 0
        else:                                            # a small file kept in the repository itself
            data = fetch_bytes(self._url(self.pointer_url, iso, ext))
            wanted = hashlib.sha256(data).hexdigest()
        found = hashlib.sha256(data).hexdigest()
        if found != wanted:
            sys.exit("geoBoundaries " + iso + ext + ": the data has sha256 " + found + ", the pinned commit names " + wanted + ". Nothing was done.")
        if path is not None:
            with open(path + ".part", "wb") as f:
                f.write(data)
            os.replace(path + ".part", path)
            return b"", found, len(data)
        return data, found, len(data)

    def file(self, iso):
        """The path of the country's file, fetched and checked if need be; None when there is none."""
        entry = self.lock["files"].get(iso)
        if entry is not None and entry.get("missing"):
            return None
        path = os.path.join(self.folder, "geoBoundaries-" + iso + "-ADM0-" + self.commit[:12] + ".geojson")
        if entry is not None and os.path.exists(path):
            found = sha256_of(path)
            if found != entry["sha256"]:
                sys.exit(path + " has sha256 " + found + "; the lock file recorded " + entry["sha256"] + " on " + entry.get("fetched", "?")
                         + ". Remove the file to fetch it again. Nothing was done.")
            return path
        os.makedirs(self.folder, exist_ok=True)
        _, sha, size = self._fetch(iso, ".geojson", path)
        if not sha:
            self.lock["files"][iso] = {"missing": True, "checked": datetime.date.today().isoformat()}
            self._save_lock()
            return None
        if entry is not None and entry["sha256"] != sha:
            os.remove(path)
            sys.exit("geoBoundaries " + iso + ": fetched again, the data has sha256 " + sha + "; the lock file recorded " + entry["sha256"] + ". Nothing was done.")
        licence, source = "not read", ""
        meta, _, _ = self._fetch(iso, "-metaData.json")
        if meta:
            try:
                about = json.loads(meta.decode("utf-8"))
                licence, source = str(about.get("boundaryLicense") or "not stated"), str(about.get("boundarySource") or "")
            except ValueError:
                pass
        self.lock["files"][iso] = {"file": os.path.basename(path), "bytes": size, "sha256": sha, "licence": licence, "source": source,
                                   "fetched": datetime.date.today().isoformat()}
        self._save_lock()
        return path

    def index(self, iso):
        """The index of the country's detailed shape, or None when geoBoundaries has no file for the code or the
        file could not be fetched (the report says which)."""
        if not iso:
            return None
        if iso in self._loaded:
            self._loaded.move_to_end(iso)
            return self._loaded[iso]
        if iso in self.used and not self.used[iso].get("bytes"):
            return None                                  # asked before: no file
        started = time.time()
        try:
            path = self.file(iso)
        except (urllib.error.URLError, OSError) as e:
            self.used[iso] = {"problem": "could not be fetched (" + str(getattr(e, "reason", e)) + ")"}
            return None
        if path is None:
            self.used[iso] = {"problem": "geoBoundaries has no file"}
            return None
        rings, points, median = read_rings(path, self.thin_m)
        if not rings:
            self.used[iso] = {"problem": "the file has no polygon"}
            return None
        index = index_of_rings(rings)
        entry = self.lock["files"][iso]
        self.used[iso] = {"bytes": entry["bytes"], "points": points, "kept": sum(len(r) for r in rings), "median_m": median,
                          "coarse": median > COARSE_M, "licence": entry["licence"], "source": entry["source"],
                          "sha256": entry["sha256"], "seconds": time.time() - started, "cell": index.cell}
        self._loaded[iso] = index
        while len(self._loaded) > self.keep:
            self._loaded.popitem(last=False)
        return index

    def kind(self, shape):
        """The line of the band table a result belongs to, from the name of the shape that decided it."""
        if shape.startswith(GEOBOUNDARIES):
            return GEOBOUNDARIES + (", coarse files" if self.used.get(shape.split()[-1], {}).get("coarse") else ", detailed files")
        return NATURAL_EARTH


# ---------------------------------------------------------------- the rule

Result = collections.namedtuple("Result", "cls country km error allowed shape note")


def usable(lat, lon):
    return -90.0 <= lat <= 90.0 and -180.0 <= lon <= 180.0 and not (lat == 0.0 and lon == 0.0)


def rounded(km):
    """A distance as the lists show it: to 10 metres under 10 km, to the km beyond."""
    return format(round(km, 2) if km < 10.0 else round(km), "g")


class Checker:
    """The rule. natural() answers from Natural Earth alone and names the detailed file that could say more;
    refine() asks that file; decide() does both for one point."""

    def __init__(self, countries, shapes, index, tolerance_km=TOLERANCE_KM, detail=None):
        self.countries, self.shapes, self.index, self.tolerance, self.detail = countries, shapes, index, tolerance_km, detail
        self.of_country = collections.defaultdict(list)          # AntWeb country -> its shape numbers
        for number, s in enumerate(shapes):
            if s.country:
                self.of_country[s.country].append(number)
        # the ISO code of each shape's detailed file: the shape's own code, else its AntWeb country's
        self.code_of_country = {}
        for s in shapes:
            if s.country and s.iso3 and s.how.startswith("itself"):
                self.code_of_country.setdefault(s.country, s.iso3)
        for c in countries.rows.values():
            if c.is_valid and ISO3.match(c.iso3):
                self.code_of_country[c.name] = c.iso3
        self.codes = [s.iso3 or self.code_of_country.get(s.country, "") for s in shapes]
        self.natural_points = collections.Counter()              # ISO code -> points of Natural Earth's shapes of it
        for s, code in zip(shapes, self.codes):
            self.natural_points[code] += s.points
        self._stored = {}
        self._inside = {}
        self._all = tuple(range(len(shapes)))

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

    def grid(self, code):
        """The index of the detailed file of an ISO code, or None: there is no file, or the file has no more
        points than Natural Earth's own shapes of that code (then Natural Earth is the finer of the two; the
        geoBoundaries files of 69 countries are coarse, China's and Colombia's with points 8 to 12 km apart)."""
        if self.detail is None or self.detail.used.get(code, {}).get("problem"):
            return None
        grid = self.detail.index(code)
        if grid is not None and self.detail.used[code]["kept"] <= self.natural_points[code]:
            self.detail.used[code]["problem"] = ("the file has no more points (" + n(self.detail.used[code]["kept"]) + ") than Natural Earth's shape ("
                                                 + n(self.natural_points[code]) + ")")
            return None
        return grid

    def code_at(self, lat, lon):
        """The ISO code of the shape the point is in or nearest to within ZONE_KM ("" for none): to put the
        points with no country in the order of their files."""
        inside = self.index.inside(lat, lon)
        if inside:
            return self.codes[inside[0]]
        near = self.index.near(lat, lon, ZONE_KM)
        return self.codes[min(near, key=near.get)] if near else ""

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
        """True when Natural Earth has the point in, or within its own error of, a shape the stored country
        agrees with. Only to name a likely data-entry error."""
        fits = lambda n: self.shapes[n].country in accept or (self.shapes[n].also & accept)
        if any(fits(n) for n in self.index.inside(lat, lon)):
            return True
        return any(fits(n) for n in self.index.near(lat, lon, ZONE_KM))

    def likely_error(self, accept, lat, lon):
        tries = [("longitude sign", lat, -lon), ("latitude sign", -lat, lon), ("both signs", -lat, -lon),
                 ("latitude and longitude swapped", lon, lat)]
        return " or ".join(label for label, la, lo in tries
                           if (la, lo) != (lat, lon) and usable(la, lo) and self.agrees(accept, la, lo))

    def outside(self, accept, inside, lat, lon, km, allowed, shape, note=""):
        """A point km outside its stored country: within the allowed distance, or flagged."""
        others = self.names([n for n in inside if self.shapes[n].country not in accept])
        where = " or ".join(others) if others else (WATER if not inside else WATER_OR_BORDER)
        if km <= allowed:
            return Result(NEAR, where, km, "", allowed, shape, note)
        return Result(FLAG, where, km, self.likely_error(accept, lat, lon), allowed, shape, note)

    def natural(self, country, island, lat, lon, stated=""):
        """(Result, code) for a stored country, from Natural Earth alone. code is the ISO code of the detailed
        file to ask ("" when the shape has none) when the point is within ZONE_KM of a border of its country, or
        in the water within REACH_KM of it; None when a detailed file could not change the answer."""
        allowed = allowed_km(stated, self.tolerance)
        cls, accept, numbers = self.stored(country, island)
        if cls is not None:
            return Result(cls, "", None, "", allowed, "", ""), None
        shapes, index = self.shapes, self.index
        inside = index.inside(lat, lon)
        home = [n for n in inside if shapes[n].country in accept]
        close = {} if index.quiet(lat, lon, ZONE_KM) else index.near(lat, lon, ZONE_KM)
        close = {n: km for n, km in close.items() if shapes[n].country in accept}
        if home:
            key = (home[0], allowed)                             # one object for the many rows with this answer
            result = self._inside.get(key)
            if result is None:
                result = self._inside[key] = Result(INSIDE, shapes[home[0]].country, 0.0, "", allowed, NATURAL_EARTH, "")
            return result, (self.codes[home[0]] if close else None)
        territory = [n for n in inside if shapes[n].also & accept]
        if territory:
            return Result(TERRITORY, self.names(territory)[0], 0.0, "", allowed, NATURAL_EARTH, ""), None
        if not close:
            close = {n: km for n, km in index.near(lat, lon, REACH_KM).items() if shapes[n].country in accept}
        if close:
            nearest = min(close, key=close.get)
            return self.outside(accept, inside, lat, lon, close[nearest], allowed, NATURAL_EARTH), self.codes[nearest]
        km = index.distance(numbers, lat, lon)
        return self.outside(accept, inside, lat, lon, km, allowed, NATURAL_EARTH), None

    def refine(self, first, country, island, lat, lon, code, grid):
        """The answer of the detailed shape (grid, the index of the file of code) for a point natural() sent on.
        Natural Earth's answer stands, with a note, when there is no file or the file draws nothing within
        DETAIL_KM of the point (a territory the country's file leaves out)."""
        if grid is None:
            problem = self.detail.used.get(code, {}).get("problem", "no detailed file") if self.detail is not None else "no detailed file"
            return first._replace(note=(code or "no ISO code") + ": " + problem)
        name = GEOBOUNDARIES + " " + code
        _, accept, _ = self.stored(country, island)
        if grid.inside(lat, lon):
            return Result(INSIDE, self.countries.resolve(country).name, 0.0, "", first.allowed, name, "")
        near = {}
        for reach in (2.0, 8.0, DETAIL_KM):                      # most points outside are close: look near first
            if not near and reach >= min(first.allowed, DETAIL_KM):
                near = grid.near(lat, lon, max(reach, first.allowed))
        if not near:
            return first._replace(note=code + ": the file draws nothing within " + format(DETAIL_KM, "g") + " km of the point")
        return self.outside(accept, self.index.inside(lat, lon), lat, lon, min(near.values()), first.allowed, name)

    def decide(self, country, island, lat, lon, stated=""):
        """The class of one usable point against one stored country ("" for none)."""
        if not country:
            return self.derive(lat, lon, stated)
        first, code = self.natural(country, island, lat, lon, stated)
        if code is None or self.detail is None:
            return first
        return self.refine(first, country, island, lat, lon, code, self.grid(code))

    def derive(self, lat, lon, stated=""):
        """No stored country. Inside exactly one country: that country. In the water, within the allowed
        distance of exactly one country: that country, as "near". Otherwise nothing is derived and the distance
        to the nearest country is given. Near a border the detailed shapes of the countries around are asked."""
        allowed = allowed_km(stated, self.tolerance)
        shapes, index = self.shapes, self.index
        inside = index.inside(lat, lon)
        near = index.near(lat, lon, max(ZONE_KM, allowed))
        found = {n: 0.0 for n in inside}                         # shape number -> km outside it (0: inside)
        for n, km in near.items():
            found.setdefault(n, km)
        shape, note = NATURAL_EARTH, ""
        if self.detail is not None and near:
            used = []
            for n in sorted(found, key=found.get):
                grid = self.grid(self.codes[n])
                if grid is None:
                    continue
                if grid.inside(lat, lon):
                    found[n] = 0.0
                else:
                    close = grid.near(lat, lon, DETAIL_KM)
                    if not close:
                        continue                                 # the file does not draw this place
                    found[n] = min(close.values())
                if self.codes[n] not in used:
                    used.append(self.codes[n])
            if used:
                shape = GEOBOUNDARIES + " " + " ".join(used)
        order = sorted(found, key=found.get)
        if not order:
            reach = index.near(lat, lon, REACH_KM)
            if reach:
                nearest = min(reach, key=reach.get)
                return Result(DERIVE_FAR, self.names([nearest])[0], reach[nearest], "", allowed, shape, note)
            return Result(DERIVE_FAR, "", index.distance(self._all, lat, lon), "", allowed, shape, note)
        within = [n for n in order if found[n] == 0.0]
        cls = DERIVE_IN
        if not within:
            within, cls = [n for n in order if found[n] <= allowed], DERIVE_NEAR
        km = found[order[0]]
        if not within:
            return Result(DERIVE_FAR, self.names([order[0]])[0], km, "", allowed, shape, note)
        mapped = self.names([n for n in within if shapes[n].country], candidates=True)
        if len(mapped) == 1:
            return Result(cls, mapped[0], km, "", allowed, shape, note)
        if mapped:
            return Result(TWO, " or ".join(mapped), km, "", allowed, shape, note)
        return Result(DERIVE_NONE, shapes[within[0]].name, km, "", allowed, shape, note)


# ---------------------------------------------------------------- counting and the report

class Tally:
    """What the report needs: specimens and distinct points for each class, the bands, and the lists."""

    def __init__(self, kind=None):
        self.kind = kind or (lambda shape: NATURAL_EARTH)        # shape name -> line of the band table
        self.specimens = collections.Counter()
        self.points = collections.Counter()
        self.bands = collections.defaultdict(lambda: [[0, 0] for _ in BANDS])   # kind -> band -> [flagged, allowed]
        self.tested = collections.Counter()                      # kind -> specimens whose answer that shape gave
        self.notes = collections.Counter()                       # why Natural Earth's answer stood
        self.stated = collections.Counter()                      # the stated errors: read, not read, none
        self.unread = collections.Counter()
        self.conflict_groups = collections.Counter()
        self.conflict_pairs = collections.Counter()
        self.conflict_errors = collections.Counter()
        self.near_groups = collections.Counter()
        self.derive_groups = collections.Counter()
        self.derive_countries = collections.Counter()
        self.territory_pairs = collections.Counter()
        self.not_checked = collections.Counter()
        self.flagged = {}                                        # key of the point -> Result, for the three lists

    def add(self, key, country, group, count, result, valid_name="", stated=""):
        self.specimens[result.cls] += count
        self.points[result.cls] += 1
        if stated.strip():
            if stated_km(stated) is None:
                self.stated["not read"] += count
                self.unread[stated.strip()] += count
            else:
                self.stated["read"] += count
        if result.shape:
            self.tested[self.kind(result.shape)] += count
        if result.note:
            self.notes[result.note] += count
        if result.cls in (NEAR, FLAG):
            self.bands[self.kind(result.shape)][band_of(result.km)][0 if result.cls == FLAG else 1] += count
            self.flagged[key] = result
            if result.cls == FLAG:
                self.conflict_groups[group] += count
                self.conflict_pairs[(valid_name or country, result.country)] += count
                self.conflict_errors[result.error or "none of the four"] += count
            else:
                self.near_groups[group] += count
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


def resolves(kind, files):
    """What the report says a kind of shape can tell apart (the measurements: 2026-11-10, see the header)."""
    if kind == NATURAL_EARTH:
        return ("its coast is up to " + format(NATURAL_EARTH_ERROR_KM, "g") + " km off (measured: 5.1 km in Mexico, 4.9 km in Madagascar; of 80 ports of the United States "
                "5 lay 1 to 3 km outside it). The bands under " + format(NATURAL_EARTH_ERROR_KM, "g") + " km are within the shape's own error: a specimen there may well be on land.")
    spacing = sorted(f["median_m"] for f in files)
    if not spacing:
        return ""
    span = n(spacing[0]) + " m" if len(spacing) == 1 else n(spacing[0]) + " to " + n(spacing[-1]) + " m"
    if kind.endswith("coarse files"):
        return ("the points of their lines are " + span + " apart (median of each file), more than " + n(COARSE_M) + " m. A line that coarse cannot place "
                "a coast to 1 km: read the bands under 2 km as within the shape's own error.")
    return ("the points of their lines are " + span + " apart (median of each file; points closer than " + n(THIN_M) + " m are left out). Measured on 120 ports "
            "and coastal towns of Mexico, Madagascar and Indonesia: 118 inside or less than 1 km outside, the other two 7 and 10 km out at sea on every map. "
            "The bands can be read as they stand.")


def band_table(tally, files_of_kind):
    out = []
    for kind in sorted(tally.tested):
        rows = tally.bands[kind]
        out += ["", "Outside the stored country, tested on " + kind + " (" + n(tally.tested[kind]) + " specimens tested on it, the ones inside included):",
                "  " + "distance to the stored country".ljust(32) + "flagged".rjust(11) + "within the allowed distance".rjust(30)]
        out += ["  " + label.ljust(32) + n(rows[i][0]).rjust(11) + n(rows[i][1]).rjust(30) for i, (_, label) in enumerate(BANDS)]
        out += ["  " + "all".ljust(32) + n(sum(r[0] for r in rows)).rjust(11) + n(sum(r[1] for r in rows)).rjust(30)]
        out += ["  What this shape can tell apart: " + resolves(kind, files_of_kind.get(kind, []))]
    return out or ["", "Outside the stored country: no specimen was tested."]


def shape_lines(detail, tally):
    """The detailed files used, their licences, and where Natural Earth's answer stood instead."""
    if detail is None:
        return ["Detailed shapes: not used (--shapes-source naturalearth). Every point was tested on " + NATURAL_EARTH + "."]
    files = {iso: f for iso, f in detail.used.items() if f.get("bytes")}
    used = {iso: f for iso, f in files.items() if not f.get("problem")}
    out = ["Detailed shapes: geoBoundaries gbOpen ADM0, commit " + detail.commit + ". " + n(len(files)) + " files read (" + n(sum(f["bytes"] for f in files.values()) / 1e6)
           + " MB, in " + detail.folder + "), " + n(len(used)) + " used: " + n(sum(1 for f in used.values() if not f["coarse"])) + " detailed, "
           + n(sum(1 for f in used.values() if f["coarse"])) + " coarse (points more than " + n(COARSE_M) + " m apart). Each file: shape_files.csv. Checksums: " + detail.lock_path,
           "  A point is tested on them when Natural Earth has it within " + format(ZONE_KM, "g") + " km of a border of its stored country, or in the water within "
           + format(REACH_KM, "g") + " km of it. Every other point is tested on " + NATURAL_EARTH + " alone.",
           "  Which file: the ISO code of the Natural Earth shape the point is in or nearest to (a shape without a code: the code AntWeb has for its country).",
           "  A file with no more points than Natural Earth's own shape of the country is not used: Natural Earth is then the finer of the two."]
    by_licence = collections.defaultdict(list)
    for iso, f in used.items():
        by_licence[f["licence"]].append(iso)
    out += ["  Licences of the files used:"] + ["    " + lic + ": " + " ".join(sorted(isos)) for lic, isos in sorted(by_licence.items())]
    problems = sorted(iso + " (" + f["problem"] + ")" for iso, f in detail.used.items() if f.get("problem"))
    out += ["  " + NATURAL_EARTH + " used for these codes (" + n(len(problems)) + "):"] + wrap(problems)
    out += ["  Specimens near a border whose answer is Natural Earth's, and why:"] + lines_of(tally.notes, str, limit=40)
    return out


def write_shape_files(path, detail):
    """shape_files.csv: every detailed file asked for in this run."""
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["iso_code", "used", "megabytes", "points_in_file", "points_kept", "median_metres_between_points", "coarse", "licence", "source",
                    "sha256", "seconds_to_read", "why_not_used"])
        for iso in sorted(detail.used):
            u = detail.used[iso]
            if u.get("bytes"):
                w.writerow([iso, "no" if u.get("problem") else "yes", format(u["bytes"] / 1e6, ".1f"), u["points"], u["kept"], round(u["median_m"]),
                            "yes" if u["coarse"] else "no", u["licence"], u["source"], u["sha256"], format(u["seconds"], ".1f"), u.get("problem", "")])
            else:
                w.writerow([iso, "no", "", "", "", "", "", "", "", "", "", u.get("problem", "")])


def report_text(when, commit, counts, tally, groups, tolerance, lists, notes, shape_info, seconds, only=None, detail=None):
    km = format(tolerance, "g") + " km"
    group = lambda g: groups.get(g, "group " + str(g))
    words = {
        INSIDE: "inside the stored country",
        TERRITORY: "inside a territory or shared area of the stored country (list below)",
        NEAR: "in the water or across the border, within the allowed distance (near.csv)",
        FLAG: "FLAGGED: in the water or across the border, farther than the allowed distance (conflicts.csv)",
        UNKNOWN: "not checked: the stored country is not known to AntWeb",
        NO_SHAPE: "not checked: the stored country has no shape",
        DERIVE_IN: "country can be derived: the point is inside one country",
        DERIVE_NEAR: "country can be derived: in the water, within the allowed distance of one country",
        TWO: "two candidates: nothing derived",
        DERIVE_FAR: "not derived: in the water, farther than the allowed distance from any country",
        DERIVE_NONE: "the point is in an area with no AntWeb country",
    }
    total, no_coords, zero, bad_lat, bad_lon = counts
    usable_count = total - no_coords - zero - bad_lat - bad_lon
    sp, pt = tally.specimens, tally.points
    with_country = sum(sp[c] for c in STORED_CLASSES)
    without = sum(sp[c] for c in DERIVE_CLASSES)
    no_shape, no_country, other_way = lists
    files_of_kind = collections.defaultdict(list)
    if detail is not None:
        for iso, f in detail.used.items():
            if f.get("bytes") and not f.get("problem"):
                files_of_kind[detail.kind(GEOBOUNDARIES + " " + iso)].append(f)
    out = ["AntWeb coordinates against the stored country, " + when + " (tool version " + VERSION + ", AntWeb commit " + (commit or "unknown") + ")"]
    if only:
        out += ["", "A quick run: only specimens stored under " + only + ". The counts below are for these only."]
    out += ["", "Specimens: " + n(total),
            "  " + n(usable_count).rjust(9) + "  with coordinates that can be used (" + n(sum(pt.values())) + " distinct combinations of country, point, stated error and group)",
            "  " + n(no_coords).rjust(9) + "  no coordinates",
            "  " + n(zero).rjust(9) + "  latitude 0 and longitude 0",
            "  " + n(bad_lat).rjust(9) + "  latitude outside -90 to 90",
            "  " + n(bad_lon).rjust(9) + "  longitude outside -180 to 180",
            "", "The allowed distance: " + km + " (--tolerance-km), or the specimen's own stated error (latlonmaxerror) when that is larger.",
            "  " + n(tally.stated["read"]).rjust(9) + "  specimens with usable coordinates state an error that could be read (1 minute is 1.852 km; a bare number is metres)",
            "  " + n(tally.stated["not read"]).rjust(9) + "  state an error that could not be read: " + km + " is used. The most frequent:"]
    out += ["  " + line for line in lines_of(tally.unread, lambda text: repr(text), limit=10)]
    out += ["", "With a stored country and usable coordinates: " + n(with_country)]
    out += ["  " + n(sp[c]).rjust(9) + "  " + words[c] for c in STORED_CLASSES]
    out += band_table(tally, files_of_kind)
    out += ["", "With no country and usable coordinates (derive.csv): " + n(without)]
    out += ["  " + n(sp[c]).rjust(9) + "  " + words[c] for c in DERIVE_CLASSES]
    out += ["", "Flagged: " + n(sp[FLAG]) + " specimens. The data-entry error that would bring the point into the stored country:"]
    out += lines_of(tally.conflict_errors, str)
    out += ["", "Flagged, by upload group:"] + lines_of(tally.conflict_groups, group, limit=200)
    out += ["", "Flagged, by stored country -> where the point is (the 50 largest):"]
    out += lines_of(tally.conflict_pairs, lambda k: k[0] + " -> " + k[1])
    out += ["", "Within the allowed distance, by upload group (the 50 largest):"] + lines_of(tally.near_groups, group)
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
    out += ["", "Shapes: " + shape_info] + shape_lines(detail, tally)
    out += ["", "Run time: " + seconds]
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



def key_sql(columns):
    """One text for a point, its stored country and its stated error, the same in both statements: the country,
    the island country and the stated error as hex (exact, accents and capitals included), the coordinates to
    5 decimals (about 1 metre). columns: the columns of specimen (a table without latlonmaxerror has no error)."""
    stated = "hex(ifnull(s.latlonmaxerror, ''))" if "latlonmaxerror" in columns else "''"
    return ("concat(hex(ifnull(s.country, '')), '|', hex(ifnull(s.island_country, '')), '|', "
            "round(s.decimal_latitude, 5), '|', round(s.decimal_longitude, 5), '|', " + stated + ")")


def columns_sql(table):
    """The names of the columns of geolocale or specimen, in small letters."""
    if table not in ("geolocale", "specimen"):
        raise ValueError(table)
    return "select lower(column_name) from information_schema.columns where table_schema = database() and table_name = '" + table + "'"


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


def points_sql(key, only=""):
    """Each distinct stored country, point and stated error once for each upload group, with its number of specimens."""
    return "select " + key + ", ifnull(s.access_group, 0), count(*) from specimen s where " + USABLE + only + " group by 1, 2"


def specimens_sql(key, keys, only=""):
    """The specimens of the given points, for conflicts.csv, near.csv and derive.csv."""
    return ("select hex(s.code), ifnull(s.access_group, 0), " + key + ", hex(ifnull(s.adm1, '')), hex(ifnull(s.localityname, '')) "
            "from specimen s where " + USABLE + only + " and " + key + " in (" + ", ".join(sql_string(k) for k in keys) + ")")


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
          "coordinate_country", "class", "distance_km", "likely_error", "stated_error", "allowed_km", "shape", "note"]


def split_key(key):
    hc, hi, lat, lon, he = key.split("|")
    return unhex(hc), unhex(hi), lat, lon, unhex(he)


def check_points(checker, rows, tally):
    """Classifies each (key, group, count) row; returns the number of rows.

    Every row is answered from Natural Earth as it arrives. The rows a detailed file could answer better are
    kept by the ISO code of that file and answered afterwards, one country at a time, so that only one
    country's detailed shape is in memory. The rows with no country come last."""
    seen = 0
    waiting = collections.defaultdict(list)              # ISO code -> rows for that country's detailed file
    blank = []
    detail = checker.detail

    def add(key, country, group, count, result, stated):
        valid = ""
        if country and result.cls not in (UNKNOWN, NO_SHAPE):
            valid = checker.countries.resolve(country).name
        tally.add(key, country, str(group), int(count), result, valid, stated)

    for key, group, count in rows:
        seen += 1
        country, island, lat, lon, stated = split_key(key)
        if not country:
            blank.append((key, group, count))
            continue
        first, code = checker.natural(country, island, float(lat), float(lon), stated)
        if code is not None and detail is not None:
            waiting[code].append((key, int(group), int(count), first))
        else:
            add(key, country, group, count, first, stated)
    if detail is not None:
        detail.keep = 1                                  # one country at a time
    for code in sorted(waiting):
        grid = checker.grid(code)
        for key, group, count, first in waiting.pop(code):
            country, island, lat, lon, stated = split_key(key)
            add(key, country, group, count, checker.refine(first, country, island, float(lat), float(lon), code, grid), stated)
    if detail is not None:
        detail.keep = 2                                  # a point with no country may lie between two
        blank.sort(key=lambda row: checker.code_at(float(row[0].split("|")[2]), float(row[0].split("|")[3])))
    for key, group, count in blank:
        _, _, lat, lon, stated = split_key(key)
        add(key, "", group, count, checker.derive(float(lat), float(lon), stated), stated)
    return seen


def csv_line(code, group_name, key, adm1, locality, result):
    country, _, lat, lon, stated = split_key(key)
    return [code, group_name, country, adm1, locality, str(float(lat)), str(float(lon)), result.country, result.cls,
            "" if result.km is None else rounded(result.km), result.error, stated, format(result.allowed, "g"), result.shape, result.note]


FILES = {FLAG: "conflicts.csv", NEAR: "near.csv"}


def write_lists(folder, answers, flagged, groups):
    """conflicts.csv (flagged), near.csv (outside, within the allowed distance) and derive.csv (no country): one
    line per specimen of the kept points, by upload group and code.
    answers: the rows of each specimens_sql statement (code, group, key, state, locality, the texts as hex)."""
    lines = {"conflicts.csv": [], "near.csv": [], "derive.csv": []}
    for rows in answers:
        for code, group, key, adm1, locality in rows:
            result = flagged.get(key)
            if result is not None:
                lines[FILES.get(result.cls, "derive.csv")].append(
                    csv_line(unhex(code), groups.get(group, "group " + group), key, unhex(adm1), unhex(locality), result))
    for name in sorted(lines):
        with open(os.path.join(folder, name), "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(HEADER)
            w.writerows(sorted(lines[name], key=lambda line: (line[1], line[0])))
    return len(lines["conflicts.csv"]), len(lines["near.csv"]), len(lines["derive.csv"])


def write_mapping(path, shapes, codes):
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["shape", "part_of", "type", "antweb_country", "how_matched", "also_accepted", "iso_code_of_detailed_file"])
        for s, code in sorted(zip(shapes, codes), key=lambda x: (x[0].admin, x[0].name)):
            w.writerow([s.name, s.admin, s.kind, s.country or "", s.how, "; ".join(sorted(s.also)), code])


def peak_mb():
    return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024.0


def main(argv=None):
    a = argparse.ArgumentParser(description="Compare each specimen's coordinates with its stored country. Report only.")
    a.add_argument("--tolerance-km", type=float, default=TOLERANCE_KM,
                   help="a point farther outside its stored country than this is flagged (default 1; a larger stated error of the specimen counts instead)")
    a.add_argument("--shapes-source", choices=["naturalearth", "geoboundaries"], default="naturalearth",
                   help="naturalearth: Natural Earth 1:10m alone (its coast is up to 5 km off). geoboundaries: near a border or a coast, "
                        "the detailed file of the country as well, fetched when needed")
    a.add_argument("--countries", default=None, help='a quick run: only specimens stored under these, e.g. "Mexico,Panama"; none = no country')
    a.add_argument("--shapes", default=None, help="a local GeoJSON file in place of the Natural Earth file")
    a.add_argument("--shapes-folder", default=SHAPES_FOLDER, help="where shape files and the lock file are kept (default " + SHAPES_FOLDER + ")")
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

    columns = {r[0] for r in run_sql(project, columns_sql("geolocale"), args.container)}
    countries = read_countries(run_sql(project, countries_sql(columns), args.container))
    key = key_sql({r[0] for r in run_sql(project, columns_sql("specimen"), args.container)})
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
        path, source = fetch_shapes(args.shapes_folder), "Natural Earth, public domain, " + SHAPES_URL
    with open(path, encoding="utf-8") as f:
        shapes = read_shapes(json.load(f))
    notes = map_shapes(shapes, countries)
    index = Index(shapes, forget=True)
    shape_info = (path + " (" + n(len(shapes)) + " shapes; " + source + "; sha256 " + sha256_of(path) + ")")
    built = time.time()

    detail = Detail(args.shapes_folder) if args.shapes_source == "geoboundaries" else None
    checker = Checker(countries, shapes, index, args.tolerance_km, detail)
    counts = [int(float(x)) for x in run_sql(project, counts_sql(only), args.container)[0]]
    tally = Tally(detail.kind if detail is not None else None)
    check_points(checker, rows_sql(project, points_sql(key, only), args.container), tally)
    checked = time.time()

    keys = sorted(tally.flagged)
    statements = (specimens_sql(key, keys[i:i + KEYS_PER_STATEMENT], only) for i in range(0, len(keys), KEYS_PER_STATEMENT))
    write_lists(folder, (rows_sql(project, sql, args.container) for sql in statements), tally.flagged, groups)
    write_mapping(os.path.join(folder, "mapping.csv"), shapes, checker.codes)
    if detail is not None:
        write_shape_files(os.path.join(folder, "shape_files.csv"), detail)

    done = time.time()
    seconds = (format(done - started, ".0f") + " seconds (Natural Earth and its index " + format(built - started, ".0f") + ", points "
               + format(checked - built, ".0f") + ", the three lists " + format(done - checked, ".0f") + "); memory at the peak "
               + n(peak_mb()) + " MB")
    text = report_text(now.strftime("%Y-%m-%d %H:%M"), commit, counts, tally, groups, args.tolerance_km,
                       mapping_lists(shapes, countries), notes, shape_info, seconds, only=only_text, detail=detail)
    with open(os.path.join(folder, "report.txt"), "w", encoding="utf-8") as f:
        f.write(text)
    print(text)
    print("Files: " + folder)
    return 0


if __name__ == "__main__":
    sys.exit(main())
