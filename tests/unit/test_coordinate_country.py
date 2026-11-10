"""tools/place/coordinate_country.py: the coordinates of a specimen against its stored country (2026-11-10, Brian
Fisher: "if there is no country but lat long then we should derive the country: also lat long could be in conflict
with the country given, that should result in an upload report so it is verified"; and on the first version, which
accepted a point up to 10 km outside: "it will miss flagging those in the water: why not 1 km?").

No database and no network: the country shapes are small squares written here, the AntWeb countries a short
list, the detailed files of geoBoundaries small files in a temporary folder read through file: addresses.
Tested: a point inside a shape, inside a hole, on a border, in the water 0.5 km, 2 km and 300 km off, inside two
shapes, in a shape of several pieces, across the 180 meridian; nothing outside a shape agrees silently (listed
with its distance, flagged beyond 1 km or the specimen's stated error); the stated error as AntWeb stores it; the
bands of the report; latitude and longitude swapped and wrong signs; 0 / 0 and out-of-range coordinates; an island
country against its parent; an old country name; a country AntWeb does not know or that has no shape; the lists
of the mapping; the detailed shapes (read piece by piece, thinned, one country at a time, the lock file, the
checksum of the pinned commit, Natural Earth's answer where there is no file); the grid index against plain ray
casting; the report and the three lists; and that every statement sent to the database is a single select.

Run: python3 tests/unit/test_coordinate_country.py   (also in .github/workflows/tests.yaml)
"""
import csv
import hashlib
import importlib.util
import inspect
import json
import os
import random
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
spec = importlib.util.spec_from_file_location("coordinate_country", os.path.join(ROOT, "tools", "place", "coordinate_country.py"))
cc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cc)
NE = cc.NATURAL_EARTH


def square(west, south, east, north):
    return [[west, south], [east, south], [east, north], [west, north], [west, south]]


def feature(name, polygons, **props):
    props["NAME"] = name
    kind = "MultiPolygon" if len(polygons) > 1 else "Polygon"
    return {"type": "Feature", "properties": props,
            "geometry": {"type": kind, "coordinates": polygons if len(polygons) > 1 else polygons[0]}}


def geojson():
    usa = dict(ADMIN="United States of America", ISO_A3="-99", ISO_A3_EH="USA", SOVEREIGNT="United States of America")
    return {"type": "FeatureCollection", "features": [
        # Mexico: a square with a hole in the middle (longitude -106 to -104, latitude 24 to 26)
        feature("Mexico", [[square(-110, 20, -100, 30), square(-106, 24, -104, 26)]], ISO_A3="MEX", ADMIN="Mexico"),
        # the United States north of it: the border is latitude 30
        feature("United States of America", [[square(-110, 30, -100, 40)]], SUBUNIT="United States", **usa),
        feature("Alaska", [[square(-150, 60, -140, 70)]], **usa),
        # Hawaii: two pieces
        feature("Hawaii", [[square(-160, 21, -159, 22)], [square(-156, 19, -155, 20)]], **usa),
        feature("France", [[square(0, 44, 5, 49)]], ISO_A3="FRA", ADMIN="France", SOVEREIGNT="France"),
        feature("Réunion", [[square(55, -21.5, 56, -20.5)]], ISO_A3="REU", ADMIN="France", ISO_A3_EH="FRA", SOVEREIGNT="France"),
        # Guatemala and Belize overlap between longitude -96 and -95, latitude 16 to 18
        feature("Guatemala", [[square(-100, 15, -95, 20)]], ISO_A3="GTM"),
        feature("Belize", [[square(-96, 16, -94, 18)]], ISO_A3="BLZ"),
        # Fiji: one piece on each side of the 180 meridian; Kiribati: only west of it, up to 180
        feature("Fiji", [[square(177, -18, 180, -16)], [square(-180, -18, -179, -16)]], ISO_A3="FJI"),
        feature("Kiribati", [[square(179, 0, 180, 2)]], ISO_A3="KIR"),
        feature("Somalia", [[square(41, 0, 48, 8)]], ISO_A3="SOM", ADMIN="Somalia"),
        feature("Somaliland", [[square(43, 8, 48, 11)]], ISO_A3="-99", ADMIN="Somaliland", SOVEREIGNT="Somaliland"),
        feature("Atlantis", [[square(-30, 30, -28, 32)]], ISO_A3="-99"),
        # a large triangle: long slanted borders through many cells
        feature("Triangle", [[[[60, -40], [90, -35], [70, -10], [60, -40]]]], ISO_A3="-99"),
    ]}


def countries():
    C = cc.Country
    return cc.Countries([
        C("Mexico", 1, "", 0, "", "MX", "MEX"),
        C("United States", 1, "", 0, "", "US", "USA"),
        C("USA", 0, "United States", 0, ""),
        C("Hawaii", 1, "none", 1, "United States"),
        C("France", 1, "", 0, "", "FR", "FRA"),
        C("Reunion", 1, "", 0, ""),
        C("Guatemala", 1, "", 0, "", "GT", "GTM"),
        C("Belize", 1, "", 0, "", "BZ", "BLZ"),
        C("Fiji", 1, "", 0, "", "FJ", "FJI"),
        C("Kiribati", 1, "", 0, "", "KI", "KIR"),
        C("Somalia", 1, "", 0, "", "SO", "SOM"),
        C("Triangle", 1, "", 0, ""),
        C("Borneo", 1, "", 0, ""),
        C("Zaire", 0, "", 0, ""),
    ])


_made = {}


def checker(tolerance=1.0):
    if tolerance not in _made:
        shapes = cc.read_shapes(geojson())
        known = countries()
        cc.map_shapes(shapes, known)
        _made[tolerance] = cc.Checker(known, shapes, cc.Index(shapes), tolerance)
    return _made[tolerance]


def d(country, lat, lon, island="", stated=""):
    return checker().decide(country, island, lat, lon, stated)


def near(value, low, high):
    return value is not None and low <= value <= high


def test_inside():
    assert d("Mexico", 22.0, -102.0) == cc.Result(cc.INSIDE, "Mexico", 0.0, "", 1.0, NE, ""), d("Mexico", 22.0, -102.0)
    assert d("", 22.0, -102.0) == cc.Result(cc.DERIVE_IN, "Mexico", 0.0, "", 1.0, NE, "")
    assert d("France", 46.0, 2.0).cls == cc.INSIDE


def test_inside_a_hole():
    # the middle of the hole is 1 degree (about 101 km) from the nearest land of Mexico: flagged, with the distance
    r = d("Mexico", 25.0, -105.0)
    assert r.cls == cc.FLAG and r.country == cc.WATER and near(r.km, 99, 102), r
    r = d("", 25.0, -105.0)
    assert r.cls == cc.DERIVE_FAR and r.country == "" and near(r.km, 99, 102), r       # beyond 100 km no country is named
    # in the hole, 0.5 km from its edge: not flagged, but listed with its distance; with no country it is derived as near
    r = d("Mexico", 25.0, -104.005)
    assert r.cls == cc.NEAR and r.country == cc.WATER and near(r.km, 0.45, 0.55), r
    assert d("", 25.0, -104.005) == cc.Result(cc.DERIVE_NEAR, "Mexico", r.km, "", 1.0, NE, "")
    # and outside the hole again
    assert d("Mexico", 25.0, -103.9).cls == cc.INSIDE


def test_nothing_outside_a_shape_agrees_silently():
    # Brian Fisher, 2026-11-10: a tolerance of 10 km "will miss flagging those in the water: why not 1 km?"
    assert cc.TOLERANCE_KM == 1.0
    for km_off, cls in ((0.2, cc.NEAR), (0.9, cc.NEAR), (1.2, cc.FLAG), (2.0, cc.FLAG), (9.0, cc.FLAG)):
        lon = -100.0 + km_off / (cc.KM_PER_DEGREE * cc.math.cos(cc.math.radians(25.0)))
        r = d("Mexico", 25.0, lon)
        assert r.cls == cls and r.country == cc.WATER and abs(r.km - km_off) < 0.02 and r.allowed == 1.0, (km_off, r)
    # every class outside the shape goes to one of the two lists; only "inside" does not
    assert cc.FILES == {cc.FLAG: "conflicts.csv", cc.NEAR: "near.csv"}
    assert cc.INSIDE == "inside" and cc.NEAR.startswith("in the water or across the border") and cc.FLAG.startswith("flagged")


def test_on_a_border_and_across_it():
    # exactly on the border: never flagged, whichever side it is counted on
    for stored in ("Mexico", "United States"):
        assert d(stored, 30.0, -105.0).cls in (cc.INSIDE, cc.NEAR), (stored, d(stored, 30.0, -105.0))
    # 0.56 km inside the United States, stored as Mexico: across the border, listed, not flagged
    r = d("Mexico", 30.005, -105.0)
    assert r.cls == cc.NEAR and r.country == "United States" and near(r.km, 0.5, 0.6), r
    # 2.2 km inside: flagged
    r = d("Mexico", 30.02, -105.0)
    assert r.cls == cc.FLAG and r.country == "United States" and near(r.km, 2.1, 2.3) and r.error == "", r
    # 111 km inside: flagged, with the distance to Mexico
    r = d("Mexico", 31.0, -105.0)
    assert r.cls == cc.FLAG and r.country == "United States" and near(r.km, 110, 112), r
    # with no country: inside exactly one country gives that country, however close the border
    assert d("", 30.005, -105.0) == cc.Result(cc.DERIVE_IN, "United States", 0.0, "", 1.0, NE, "")


def test_in_the_water():
    # 0.5 km east of Mexico's east coast
    r = d("Mexico", 25.0, -99.995)
    assert r.cls == cc.NEAR and r.country == cc.WATER and near(r.km, 0.45, 0.55), r
    assert d("", 25.0, -99.995) == cc.Result(cc.DERIVE_NEAR, "Mexico", r.km, "", 1.0, NE, "")
    # 2 km off: flagged; with no country nothing is derived, the nearest country and the distance are given
    r = d("Mexico", 25.0, -99.98)
    assert r.cls == cc.FLAG and near(r.km, 1.9, 2.1), r
    r = d("", 25.0, -99.98)
    assert r.cls == cc.DERIVE_FAR and r.country == "Mexico" and near(r.km, 1.9, 2.1), r
    # 300 km off
    r = d("Mexico", 25.0, -97.0)
    assert r.cls == cc.FLAG and near(r.km, 300, 304), r
    r = d("", 25.0, -97.0)
    assert r.cls == cc.DERIVE_FAR and r.country == "" and near(r.km, 300, 304), r
    # --tolerance-km decides: 2 km off is within a tolerance of 5 km
    assert checker(5.0).decide("Mexico", "", 25.0, -99.98).cls == cc.NEAR
    assert checker(5.0).decide("", "", 25.0, -99.98).cls == cc.DERIVE_NEAR
    # in the water within 1 km of two countries: two candidates, nothing derived
    assert d("", 15.995, -94.995).cls == cc.TWO


def test_stated_error_as_antweb_stores_it():
    # doc/specimen16.txt (a CAS file): "1 minute" 151 times, "0.1 minute", "3 km", "150 m", "85 km"; a bare number is metres
    for text, km in (("1 minute", 1.852), ("0.1 minute", 0.1852), ("3 km", 3.0), ("150 m", 0.15), ("85 km", 85.0), ("2.5 km", 2.5),
                     ("100", 0.1), ("100m", 0.1), ("1,000 m", 1.0), ("0,5 km", 0.5), (" 30 M ", 0.03), ("< 100 m", 0.1), ("ca. 500 m", 0.5),
                     ("1 mile", 1.609344), ("300 ft", 0.09144), ("30 seconds", 0.926), ("±50 m", 0.05), ("1 Minutes", 1.852)):
        assert abs(cc.stated_km(text) - km) < 1e-9, (text, cc.stated_km(text))
    for text in ("", None, "unknown", "GPS", "10-20 m", "0 m", "1.2.3 km", "5 furlongs", "m", "-5 m"):
        assert cc.stated_km(text) is None, text
    # the allowed distance: 1 km, or the stated error when that is larger and can be read
    assert cc.allowed_km("150 m", 1.0) == 1.0 and cc.allowed_km("3 km", 1.0) == 3.0 and cc.allowed_km("GPS", 1.0) == 1.0
    assert cc.allowed_km("3 km", 5.0) == 5.0 and cc.allowed_km("", 1.0) == 1.0
    # a point 2 km in the water: flagged with no stated error, with one that cannot be read, with 1 minute (1.852 km)
    for stated, cls, allowed in (("", cc.FLAG, 1.0), ("GPS", cc.FLAG, 1.0), ("150 m", cc.FLAG, 1.0), ("1 minute", cc.FLAG, 1.852),
                                 ("3 km", cc.NEAR, 3.0), ("5000", cc.NEAR, 5.0)):
        r = d("Mexico", 25.0, -99.98, stated=stated)
        assert r.cls == cls and r.allowed == allowed and near(r.km, 1.9, 2.1), (stated, r)
    # it counts for a missing country as well
    assert d("", 25.0, -99.98, stated="3 km").cls == cc.DERIVE_NEAR and d("", 25.0, -99.98, stated="1 km").cls == cc.DERIVE_FAR


def test_bands():
    assert [label for _, label in cc.BANDS] == ["up to 1 km", "1 to 2 km", "2 to 5 km", "5 to 10 km", "over 10 km"]
    assert [cc.band_of(km) for km in (0.01, 1.0, 1.01, 2.0, 2.01, 5.0, 5.01, 10.0, 10.01, 4000.0)] == [0, 0, 1, 1, 2, 2, 3, 3, 4, 4]
    tally = cc.Tally()
    c = checker()
    rows = [(cc_key("Mexico", 25.0, -99.995), "1", "10"),                      # 0.5 km: not flagged
            (cc_key("Mexico", 25.0, -99.985), "1", "20"),                      # 1.5 km: flagged
            (cc_key("Mexico", 25.0, -99.985, stated="1 minute"), "1", "3"),    # 1.5 km, stated 1.852 km: not flagged
            (cc_key("Mexico", 25.0, -99.97), "1", "40"),                       # 3 km: flagged
            (cc_key("Mexico", 25.0, -99.97, stated="5 km"), "1", "5"),         # 3 km, stated 5 km: not flagged
            (cc_key("Mexico", 25.0, -99.93), "1", "60"),                       # 7 km: flagged
            (cc_key("Mexico", 25.0, -97.0), "1", "70"),                        # 300 km: flagged
            (cc_key("Mexico", 22.0, -102.0), "1", "1000")]                     # inside
    cc.check_points(c, rows, tally)
    assert tally.bands[NE] == [[0, 10], [20, 3], [40, 5], [60, 0], [70, 0]], tally.bands[NE]
    assert tally.specimens[cc.FLAG] == 190 and tally.specimens[cc.NEAR] == 18 and tally.specimens[cc.INSIDE] == 1000
    assert tally.tested[NE] == 1208
    text = "\n".join(cc.band_table(tally, {}))
    for part in ("Outside the stored country, tested on Natural Earth 1:10m (1,208 specimens tested on it, the ones inside included):",
                 "  distance to the stored country      flagged   within the allowed distance",
                 "  up to 1 km                                0                            10",
                 "  1 to 2 km                                20                             3",
                 "  2 to 5 km                                40                             5",
                 "  5 to 10 km                               60                             0",
                 "  over 10 km                               70                             0",
                 "  all                                     190                            18",
                 "its coast is up to 5 km off", "The bands under 5 km are within the shape's own error"):
        assert part in text, part + "\n" + text


def test_inside_two_shapes():
    r = d("", 17.0, -95.5)
    assert r.cls == cc.TWO and r.country == "Guatemala or Belize", r
    assert d("Belize", 17.0, -95.5).cls == cc.INSIDE and d("Guatemala", 17.0, -95.5).cls == cc.INSIDE
    r = d("France", 17.0, -95.5)
    assert r.cls == cc.FLAG and r.country == "Guatemala or Belize", r


def test_swapped_and_wrong_signs():
    # France is latitude 44 to 49, longitude 0 to 5; (2, 46) is in Somalia
    r = d("France", 2.0, 46.0)
    assert r.cls == cc.FLAG and r.country == "Somalia" and r.error == "latitude and longitude swapped", r
    r = d("Mexico", 25.0, 102.0)
    assert r.cls == cc.FLAG and r.country == cc.WATER and r.error == "longitude sign", r
    r = d("Reunion", 21.0, 55.5)
    assert r.cls == cc.FLAG and r.error == "latitude sign", r
    assert d("Mexico", -22.0, 102.0).error == "both signs"
    # a point that no single slip explains
    assert d("Mexico", 46.0, 2.0).error == ""
    # a slip that lands just off the coast of the stored country counts as well
    assert d("Mexico", 25.0, 99.98).error == "longitude sign"


def test_unusable_coordinates():
    assert not cc.usable(0.0, 0.0) and not cc.usable(91.0, 10.0) and not cc.usable(-90.5, 10.0)
    assert not cc.usable(10.0, 181.0) and not cc.usable(10.0, -180.5)
    assert cc.usable(0.0, 10.0) and cc.usable(10.0, 0.0) and cc.usable(-90.0, 180.0)
    # the database leaves them out of the points and counts each kind once
    for part in ("is not null", "not (s.decimal_latitude = 0 and s.decimal_longitude = 0)", "between -90 and 90", "between -180 and 180"):
        assert part in cc.USABLE, part
    key = cc.key_sql({"latlonmaxerror"})
    assert cc.USABLE in cc.points_sql(key) and cc.USABLE in cc.specimens_sql(key, ["A|B|1|2|"])
    assert cc.counts_sql().count("sum(") == 4
    # a swap or a sign change never tests a point that cannot exist (longitude -120 as a latitude)
    assert d("Mexico", 60.0, -120.0).error == ""


def test_island_country_and_its_parent():
    kauai, mainland = (21.5, -159.5), (35.0, -105.0)
    assert d("United States", *kauai, island="Hawaii").cls == cc.INSIDE
    assert d("United States", *mainland, island="Hawaii").cls == cc.INSIDE
    assert d("United States", *kauai).cls == cc.INSIDE           # stored under the parent only
    assert d("Hawaii", *kauai).cls == cc.INSIDE
    assert d("Hawaii", *mainland).cls == cc.INSIDE               # both ways
    assert d("", *kauai) == cc.Result(cc.DERIVE_IN, "Hawaii", 0.0, "", 1.0, NE, "")
    r = d("Mexico", *kauai)
    assert r.cls == cc.FLAG and r.country == "Hawaii", r
    r = d("Hawaii", 25.0, -102.0)
    assert r.cls == cc.FLAG and r.country == "Mexico", r


def test_old_name_and_spelling():
    assert d("USA", 35.0, -105.0) == cc.Result(cc.INSIDE, "United States", 0.0, "", 1.0, NE, "")
    assert d("México", 22.0, -102.0).cls == cc.INSIDE
    assert d("  mexico ", 22.0, -102.0).cls == cc.INSIDE
    r = d("USA", 22.0, -102.0)
    assert r.cls == cc.FLAG and r.country == "Mexico", r


def test_not_checked_is_never_flagged():
    for lat, lon in ((22.0, -102.0), (25.0, -97.0), (46.0, 2.0)):
        assert d("Equador", lat, lon).cls == cc.UNKNOWN            # a name AntWeb does not know
        assert d("Zaire", lat, lon).cls == cc.UNKNOWN              # an old name that points nowhere
        assert d("Borneo", lat, lon).cls == cc.NO_SHAPE            # a valid country with no shape
    tally = cc.Tally()
    rows = [(cc_key("Borneo", 22.0, -102.0), "1", "7"), (cc_key("Equador", 22.0, -102.0), "1", "2")]
    cc.check_points(checker(), rows, tally)
    assert tally.specimens[cc.NO_SHAPE] == 7 and tally.specimens[cc.UNKNOWN] == 2
    assert tally.specimens[cc.FLAG] == 0 and tally.specimens[cc.NEAR] == 0 and not tally.flagged


def test_territory_and_shared_area():
    # Reunion is a country of its own in AntWeb and a part of France in the shape file
    assert d("Reunion", -21.0, 55.5).cls == cc.INSIDE
    r = d("France", -21.0, 55.5)
    assert r.cls == cc.TERRITORY and r.country == "Reunion", r
    r = d("Reunion", 46.0, 2.0)
    assert r.cls == cc.FLAG and r.country == "France", r
    # Somaliland is not an AntWeb country: the shared-area table gives it to Somalia
    assert d("Somalia", 9.0, 45.0).cls == cc.INSIDE
    assert d("", 9.0, 45.0) == cc.Result(cc.DERIVE_IN, "Somalia", 0.0, "", 1.0, NE, "")
    # an area with two countries in the table: both are accepted, none is derived
    shapes = cc.read_shapes(geojson())
    known = countries()
    cc.map_shapes(shapes, known, shared={"Atlantis": ["Mexico", "France"]})
    both = cc.Checker(known, shapes, cc.Index(shapes))
    assert both.decide("Mexico", "", 31.0, -29.0).cls == cc.INSIDE
    assert both.decide("France", "", 31.0, -29.0).cls == cc.TERRITORY
    assert both.decide("Fiji", "", 31.0, -29.0).cls == cc.FLAG
    r = both.decide("", "", 31.0, -29.0)
    assert r.cls == cc.TWO and r.country == "Mexico or France", r


def test_area_with_no_antweb_country():
    r = d("", 31.0, -29.0)
    assert r.cls == cc.DERIVE_NONE and r.country == "Atlantis", r
    r = d("Mexico", 31.0, -29.0)
    assert r.cls == cc.FLAG and r.country == "Atlantis (not an AntWeb country)", r


def test_mapping_lists():
    c = checker()
    by_name = {s.name: s for s in c.shapes}
    assert by_name["Mexico"].country == "Mexico" and by_name["Mexico"].how == "itself, by name"
    assert by_name["Réunion"].country == "Reunion" and by_name["Réunion"].also == {"France"}      # the accent is ignored
    assert by_name["Hawaii"].country == "Hawaii" and by_name["Hawaii"].also == {"United States"}
    assert by_name["United States of America"].country == "United States"
    assert by_name["Alaska"].country == "United States" and by_name["Alaska"].how == "part of United States of America, by ISO code USA"
    assert by_name["Somaliland"].country == "Somalia" and by_name["Somaliland"].how == "shared area table"
    assert by_name["Atlantis"].country is None
    no_shape, no_country, other_way = cc.mapping_lists(c.shapes, c.countries)
    assert no_shape == ["Borneo"], no_shape
    assert no_country == ["Atlantis"], no_country
    assert other_way == ["Alaska -> United States (part of United States of America, by ISO code USA)",
                         "Somaliland -> Somalia (shared area table)"], other_way
    # the ISO code of the detailed file of each shape: its own code, else the code of its AntWeb country
    codes = {s.name: code for s, code in zip(c.shapes, c.codes)}
    assert codes["Mexico"] == "MEX" and codes["Hawaii"] == "USA" and codes["Alaska"] == "USA" and codes["Réunion"] == "REU"
    assert codes["Somaliland"] == "SOM" and codes["Atlantis"] == "" and codes["Triangle"] == ""
    # the code is used when the name is not known, the alias table when neither is
    known = cc.Countries([cc.Country("Viet Nam", 1, "", 0, ""), cc.Country("Ivory Coast", 1, "", 0, "", "CI", "CIV"),
                          cc.Country("Hawaii", 1, "", 1, "United States", "US", "USA"), cc.Country("United States", 1, "", 0, "", "US", "USA")])
    shapes = cc.read_shapes({"features": [feature("Vietnam", [[square(0, 0, 1, 1)]], ISO_A3="VNM"),
                                          feature("Côte d'Ivoire", [[square(2, 0, 3, 1)]], ISO_A3="CIV"),
                                          feature("Guam", [[square(4, 0, 5, 1)]], ISO_A3="GUM", ADMIN="Guam", SOVEREIGNT="United States of America"),
                                          feature("United States of America", [[square(6, 0, 7, 1)]], ADMIN="United States of America", ISO_A3_EH="USA")]})
    notes = cc.map_shapes(shapes, known)
    assert [(s.country, s.how) for s in shapes] == [
        ("Viet Nam", "itself, by alias table (Viet Nam)"), ("Ivory Coast", "itself, by ISO code CIV"),
        ("United States", "belongs to United States of America, by ISO code USA"),
        ("United States", "itself, by alias table (United States)")], [(s.country, s.how) for s in shapes]
    # an island country never takes its parent's ISO code: USA is the United States, not Hawaii
    assert known.by_code("USA").name == "United States" and notes == []
    # Guam goes to the United States, and its detailed file is Guam's own
    assert cc.Checker(known, shapes, cc.Index(shapes)).codes == ["VNM", "CIV", "GUM", "USA"]
    # the name wins over the code, and the report says so
    known = cc.Countries([cc.Country("Juan de Nova Island", 1, "", 0, ""), cc.Country("French Southern Territories", 1, "", 0, "", "TF", "ATF")])
    shapes = cc.read_shapes({"features": [feature("Juan De Nova Island", [[square(0, 0, 1, 1)]], ISO_A3="ATF")]})
    notes = cc.map_shapes(shapes, known)
    assert shapes[0].country == "Juan de Nova Island"
    assert notes == ["Juan De Nova Island: the name says Juan de Nova Island, the ISO code says French Southern Territories (the name is used)"]


def test_several_pieces():
    assert d("", 19.5, -155.5) == cc.Result(cc.DERIVE_IN, "Hawaii", 0.0, "", 1.0, NE, "")     # the second piece
    r = d("", 20.5, -155.5)                                                                # in the water north of it
    assert r.cls == cc.DERIVE_FAR and r.country == "Hawaii" and near(r.km, 55, 56), r
    assert d("United States", 19.5, -155.5).cls == cc.INSIDE


def test_across_the_180_meridian():
    assert d("Fiji", -17.0, 179.99).cls == cc.INSIDE and d("Fiji", -17.0, -179.5).cls == cc.INSIDE
    assert d("Fiji", -17.0, 180.0).cls in (cc.INSIDE, cc.NEAR) and d("Fiji", -17.0, -180.0).cls in (cc.INSIDE, cc.NEAR)
    # Kiribati ends at 180; a point 0.56 km beyond, written with a negative longitude, is next to it
    r = d("Kiribati", 1.0, -179.995)
    assert r.cls == cc.NEAR and near(r.km, 0.5, 0.6), r
    assert d("", 1.0, -179.995).country == "Kiribati"
    # the far distance goes the short way round: 5 degrees, not 355
    r = d("Kiribati", 1.0, -175.0)
    assert r.cls == cc.FLAG and near(r.km, 550, 560), r
    # a wrong longitude sign next to 180 is found: Fiji's east piece is at -180 to -179
    assert d("Kiribati", -17.0, 179.5).cls == cc.FLAG


def plain_inside(rings, lat, lon):
    """Plain ray casting over every border piece: the reference for the index."""
    inside = False
    for ring in rings:
        for (x1, y1), (x2, y2) in zip(ring, ring[1:]):
            if (y1 > lat) != (y2 > lat) and x1 + (lat - y1) * (x2 - x1) / (y2 - y1) > lon:
                inside = not inside
    return inside


def jagged(rnd, lat, lon, corners=400):
    ring = [[lon + (4 + 3 * rnd.random()) * cc.math.sin(i * 2 * cc.math.pi / corners), lat + (4 + 3 * rnd.random()) * cc.math.cos(i * 2 * cc.math.pi / corners)]
            for i in range(corners)]
    return ring + [ring[0]]


def test_index_against_plain_ray_casting():
    rnd = random.Random(20261110)
    data = geojson()
    data["features"].append(feature("Star", [[jagged(rnd, 10, -60)]]))    # many border pieces in many cells
    shapes = cc.read_shapes(data)
    index = cc.Index(shapes)
    assert (index.ncols, index.nrows, index.round) == (1440, 720, True)
    boxes = [(-112, 18, -92, 42), (-161, 18, -154, 23), (54, -22, 57, -20), (176, -19, 180, -15), (-180, -19, -178, -15),
             (58, -42, 92, -8), (2, -68, 18, -52), (40, -1, 49, 12)]
    for i in range(4000):
        west, south, east, north = boxes[i % len(boxes)]
        lon, lat = rnd.uniform(west, east), rnd.uniform(south, north)
        got = sorted(shapes[n].name for n in index.inside(lat, lon))
        want = sorted(s.name for s in shapes if plain_inside([r for p in s.polygons for r in p], lat, lon))
        assert got == want, (lat, lon, got, want)
    # forget=True gives the borders back to the system once they are in the index: same answers
    lean = cc.Index(cc.read_shapes(data), forget=True)
    assert lean.inside(25.0, -102.0) == index.inside(25.0, -102.0) and lean.inside(25.0, -105.0) == []


def test_index_of_one_country():
    # the index of a detailed file: a grid over the country only, with a small cell
    rnd = random.Random(7)
    rings = [jagged(rnd, -19, 47, 2000), [[46.5, -19.5], [47.5, -19.5], [47.5, -18.5], [46.5, -18.5], [46.5, -19.5]]]   # an island with a lake
    index = cc.index_of_rings([[tuple(p) for p in ring] for ring in rings])
    assert 0.01 <= index.cell < 0.02 and not index.round and index.ncols * index.nrows < 3000000, (index.cell, index.ncols, index.nrows)
    for i in range(3000):
        lon, lat = rnd.uniform(38, 56), rnd.uniform(-28, -10)
        assert bool(index.inside(lat, lon)) == plain_inside(rings, lat, lon), (lat, lon)
    assert index.inside(-19.0, 47.0) == [] and index.inside(-19.0, 44.0) == [0]        # the lake is a hole
    # a point outside the grid is outside the country, and nothing is near it
    assert index.inside(40.0, -100.0) == [] and index.near(40.0, -100.0, 50.0) == {} and index.inside(-19.0, 179.0) == []
    # the distance to the line, inside the grid and from beyond its edge
    assert abs(index.near(-19.0, 47.2, 100.0)[0] - 0.3 * cc.KM_PER_DEGREE * cc.math.cos(cc.math.radians(19.0))) < 0.05
    edge = max(p[0] for p in rings[0])
    nearest = min(cc.km_between(-19.0, edge + 0.5, y, x) for x, y in rings[0])
    assert 40 < nearest < 100 and abs(index.near(-19.0, edge + 0.5, 100.0)[0] - nearest) < 1.0
    # a country on both sides of the 180 meridian gets a grid round the world
    both = cc.index_of_rings([[(177.0, -18.0), (180.0, -18.0), (180.0, -16.0), (177.0, -16.0), (177.0, -18.0)],
                              [(-180.0, -18.0), (-179.0, -18.0), (-179.0, -16.0), (-180.0, -16.0), (-180.0, -18.0)]])
    assert both.round and both.inside(-17.0, 179.0) == [0] and both.inside(-17.0, -179.5) == [0] and both.inside(-17.0, -178.0) == []
    assert abs(both.near(-17.0, -178.99, 5.0)[0] - 0.01 * cc.KM_PER_DEGREE * cc.math.cos(cc.math.radians(17.0))) < 0.01


def test_distances():
    assert abs(cc.km_between(0.0, 0.0, 0.0, 1.0) - 111.195) < 0.01
    assert abs(cc.km_between(0.0, 179.5, 0.0, -179.5) - 111.195) < 0.01
    index, shapes = checker().index, checker().shapes
    mexico = [n for n, s in enumerate(shapes) if s.name == "Mexico"]
    # 10 degrees south of Mexico's south coast
    assert abs(index.distance(mexico, 10.0, -105.0) - 1111.95) < 1.0
    # near() and distance() agree close to a border
    close = index.near(19.95, -105.0, 10.0)
    assert abs(close[mexico[0]] - index.distance(mexico, 19.95, -105.0)) < 0.01
    assert index.near(10.0, -105.0, 10.0) == {} and index.distance([], 10.0, -105.0) is None


def test_reading_a_detailed_file_piece_by_piece():
    rnd = random.Random(3)
    dense = [[cc.math.sin(i * cc.math.pi / 10000), cc.math.cos(i * cc.math.pi / 10000)] for i in range(20000)]    # a circle, a point every 35 m
    dense.append(dense[0])
    polygons = [[dense, square(-1, -1, 1, 1)], [[[20.5, 5.25, 12.0], [21.5, 5.25, 12.0], [21.0, 6.125, 12.0], [20.5, 5.25, 12.0]]]]
    data = {"type": "FeatureCollection", "bbox": [-7.0, -7.0, 21.5, 7.0],
            "features": [{"type": "Feature", "properties": {"shapeName": "Test", "shapeGroup": "TST", "list": [1, 2], "pair": [[3, 4]]},
                          "geometry": {"type": "MultiPolygon", "coordinates": polygons}},
                         {"type": "Feature", "properties": {}, "geometry": {"type": "Polygon", "coordinates": [square(30, 30, 31, 31)]}}]}
    want = [[tuple(p[:2]) for p in ring] for polygon in polygons for ring in polygon] + [[tuple(p) for p in square(30, 30, 31, 31)]]
    with tempfile.TemporaryDirectory() as folder:
        path = os.path.join(folder, "t.geojson")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f)
        # every point, whatever the size of the pieces the file is read in; numbers of other members are not points
        for size in (1 << 20, 4096, 61, 17):
            rings, points, median = cc.read_rings(path, thin_m=0.0, block_size=size)
            assert rings == want and points == sum(len(r) for r in want), (size, points, len(rings))
        # thinned: points closer than 500 m to the last one kept are left out; the line moves by less than that
        rings, points, median = cc.read_rings(path, thin_m=500.0)
        assert points == sum(len(r) for r in want) and len(rings) == 4 and rings[0][0] == rings[0][-1]
        assert 1300 < len(rings[0]) < 1500 and rings[1] == want[1] and 30 < median < 40, (len(rings[0]), median)
        thinned = cc.Index([cc.Rings([rings[0]])])
        for x, y in dense[::7]:
            assert min(thinned.near(y, x, 5.0).values()) <= 0.25, (x, y)
        # a ring smaller than the thinning distance is kept by its first points
        with open(path, "w", encoding="utf-8") as f:
            json.dump({"features": [{"geometry": {"type": "Polygon", "coordinates": [[[10.0, 10.0], [10.0001, 10.0], [10.0001, 10.0001], [10.0, 10.0001], [10.0, 10.0]]]}}]}, f)
        rings, points, median = cc.read_rings(path, thin_m=50.0)
        assert points == 5 and len(rings) == 1 and len(rings[0]) == 5 and bool(cc.index_of_rings(rings).inside(10.00005, 10.00005))


# ---- the detailed files of geoBoundaries, without the network: a folder read through file: addresses

DETAILED = {
    # the detailed coast of Mexico runs 3 km west of Natural Earth's east coast (longitude -100.03, not -100)
    "MEX": ([square(-110, 20, -100.03, 30), square(-106, 24, -104, 26)], 0.0003, "Creative Commons Attribution 4.0 (CC BY 4.0)", "geoBoundaries, World Bank"),
    # the United States: the same square, a point every 5.5 km (a coarse file); no Hawaii and no Alaska in it
    "USA": ([square(-110, 30, -100, 40)], 0.05, "Public Domain", "United States Census Bureau"),
    # Guatemala: the corners only, no more points than Natural Earth's own shape
    "GTM": ([square(-100, 15, -95, 20)], 0, "Public Domain", "a test"),
    "KIR": ([square(179, 0, 180, 2)], 0.0003, "Open Data Commons Open Database License 1.0", "OpenStreetMap"),
}


def with_points(ring, step):
    """The ring with a point every step degrees along each side: a detailed line (0: the corners only)."""
    out = []
    for (x1, y1), (x2, y2) in zip(ring, ring[1:]):
        parts = int(round(max(abs(x2 - x1), abs(y2 - y1)) / step)) if step else 1
        out += [[x1 + (x2 - x1) * i / parts, y1 + (y2 - y1) * i / parts] for i in range(parts)]
    return out + [ring[0]]


def pointer(data):
    return ("version https://git-lfs.github.com/spec/v1\noid sha256:" + hashlib.sha256(data).hexdigest() + "\nsize " + str(len(data)) + "\n").encode("ascii")


def make_source(folder):
    """What github serves for the pinned commit: under raw/ the pointer of each file, under media/ its data."""
    for sub in ("raw", "media"):
        os.makedirs(os.path.join(folder, sub))
    for iso, (rings, step, licence, source) in DETAILED.items():
        data = json.dumps({"type": "FeatureCollection", "features": [{"type": "Feature", "properties": {"shapeGroup": iso},
               "geometry": {"type": "Polygon", "coordinates": [with_points(r, step) for r in rings]}}]}).encode("utf-8")
        meta = json.dumps({"boundaryISO": iso, "boundaryLicense": licence, "boundarySource": source}).encode("utf-8")
        for name, content in ((iso + ".geojson", data), (iso + "-metaData.json", meta)):
            with open(os.path.join(folder, "media", name), "wb") as f:
                f.write(content)
            with open(os.path.join(folder, "raw", name), "wb") as f:
                f.write(pointer(content) if iso != "KIR" or name.endswith(".geojson") else content)    # one small file kept as it is


def detail_in(folder, store="store"):
    return cc.Detail(os.path.join(folder, store), commit="c" * 40, pointer_url="file://" + folder + "/raw/{iso}{ext}",
                     data_url="file://" + folder + "/media/{iso}{ext}")


def detailed_checker(folder, tolerance=1.0):
    shapes = cc.read_shapes(geojson())
    known = countries()
    cc.map_shapes(shapes, known)
    return cc.Checker(known, shapes, cc.Index(shapes), tolerance, detail_in(folder))


def test_lock_file_and_checksums():
    with tempfile.TemporaryDirectory() as folder:
        make_source(folder)
        detail = detail_in(folder)
        assert detail.index("MEX").inside(25.0, -102.0) == [0]
        lock_path = os.path.join(folder, "store", "geoboundaries-cccccccccccc.lock.json")
        assert detail.lock_path == lock_path
        with open(lock_path, encoding="utf-8") as f:
            lock = json.load(f)
        path = os.path.join(folder, "store", "geoBoundaries-MEX-ADM0-cccccccccccc.geojson")
        entry = lock["files"]["MEX"]
        assert lock["commit"] == "c" * 40 and entry["file"] == os.path.basename(path) and entry["bytes"] == os.path.getsize(path)
        assert entry["sha256"] == cc.sha256_of(path) == cc.sha256_of(os.path.join(folder, "media", "MEX.geojson"))
        assert entry["licence"] == "Creative Commons Attribution 4.0 (CC BY 4.0)" and entry["source"] == "geoBoundaries, World Bank"
        used = detail.used["MEX"]
        assert used["points"] > 150000 and used["kept"] < used["points"] / 1.4 and 25 < used["median_m"] < 40, used
        assert not used["coarse"] and used["licence"] == entry["licence"] and used["bytes"] == entry["bytes"]
        # a licence file that is not a pointer is read as well; a file with few points is called coarse
        assert detail.index("KIR") is not None and detail.used["KIR"]["licence"].startswith("Open Data Commons")
        assert detail.index("USA") is not None and detail.used["USA"]["coarse"] and 4000 < detail.used["USA"]["median_m"] < 6000
        assert detail.kind("geoBoundaries USA") == "geoBoundaries, coarse files" and detail.kind("geoBoundaries MEX") == "geoBoundaries, detailed files"
        assert detail.kind(NE) == NE
        # a code geoBoundaries has no file for: no index, written down, not asked for again
        assert detail.index("FJI") is None and detail.used["FJI"] == {"problem": "geoBoundaries has no file"} and detail.index("") is None
        with open(lock_path, encoding="utf-8") as f:
            assert json.load(f)["files"]["FJI"]["missing"] is True
        # a later run: the source is gone, the kept files are used when their bytes are the same
        for name in os.listdir(os.path.join(folder, "media")):
            os.remove(os.path.join(folder, "media", name))
        later = detail_in(folder)
        assert later.index("MEX").inside(25.0, -102.0) == [0] and later.index("FJI") is None
        # two countries in memory at most; one while the waiting points are answered country by country
        later.index("USA"), later.index("KIR")
        assert list(later._loaded) == ["USA", "KIR"]
        later.keep = 1
        later.index("MEX")
        assert list(later._loaded) == ["MEX"]
        # changed bytes are refused
        with open(path, "ab") as f:
            f.write(b" ")
        try:
            detail_in(folder).index("MEX")
            raise AssertionError("a changed file must not be used")
        except SystemExit as e:
            assert "the lock file recorded" in str(e) and entry["sha256"] in str(e)
        # a lock file of another commit is refused
        try:
            cc.Detail(os.path.join(folder, "store"), commit="c" * 12 + "d" * 28)
            raise AssertionError("a lock file of another commit must not be used")
        except SystemExit as e:
            assert "another commit" in str(e)
    with tempfile.TemporaryDirectory() as folder:
        make_source(folder)
        # data that is not what the pointer of the pinned commit names is refused, and nothing is kept
        with open(os.path.join(folder, "media", "MEX.geojson"), "ab") as f:
            f.write(b" ")
        try:
            detail_in(folder).index("MEX")
            raise AssertionError("data with another checksum must not be used")
        except SystemExit as e:
            assert "the pinned commit names" in str(e)
        assert not [name for name in os.listdir(os.path.join(folder, "store")) if "MEX" in name]
    # the pinned source
    assert len(cc.GB_COMMIT) == 40 and "{commit}" in cc.GB_POINTER and "{iso}" in cc.GB_DATA
    assert cc.GB_POINTER.startswith("https://raw.githubusercontent.com/wmgeolab/geoBoundaries/")
    assert cc.GB_DATA.startswith("https://media.githubusercontent.com/media/wmgeolab/geoBoundaries/")


def test_detailed_shape_decides_near_a_border():
    with tempfile.TemporaryDirectory() as folder:
        make_source(folder)
        c = detailed_checker(folder)
        # far from every border Natural Earth answers and no file is fetched
        assert c.decide("Mexico", "", 22.0, -102.0) == cc.Result(cc.INSIDE, "Mexico", 0.0, "", 1.0, NE, "")
        assert c.detail.used == {} and not os.path.exists(os.path.join(folder, "store"))
        # 1 km inside Natural Earth's coast, 2 km outside the detailed one: flagged (Natural Earth alone says inside)
        assert checker().decide("Mexico", "", 25.0, -100.01).cls == cc.INSIDE
        r = c.decide("Mexico", "", 25.0, -100.01)
        assert r.cls == cc.FLAG and r.country == cc.WATER_OR_BORDER and near(r.km, 1.9, 2.1) and r.shape == "geoBoundaries MEX", r
        assert c.decide("Mexico", "", 25.0, -100.01, "3 km").cls == cc.NEAR
        # 4 km inside Natural Earth's coast, 1 km inside the detailed one
        assert c.decide("Mexico", "", 25.0, -100.04) == cc.Result(cc.INSIDE, "Mexico", 0.0, "", 1.0, "geoBoundaries MEX", "")
        # in the water by both
        r = c.decide("Mexico", "", 25.0, -99.99)
        assert r.cls == cc.FLAG and r.country == cc.WATER and near(r.km, 3.9, 4.1) and r.shape == "geoBoundaries MEX", r
        # across the border: the detailed line is the same, the neighbour is named by Natural Earth
        r = c.decide("Mexico", "", 30.005, -105.0)
        assert r.cls == cc.NEAR and r.country == "United States" and near(r.km, 0.5, 0.6) and r.shape == "geoBoundaries MEX", r
        # the fallback: Natural Earth's answer stands, and the result says why
        r = c.decide("Fiji", "", -17.0, 179.99)                    # geoBoundaries has no file for the code
        assert r.cls == cc.INSIDE and r.shape == NE and r.note == "FJI: geoBoundaries has no file", r
        r = c.decide("United States", "", 21.5, -159.99, "Hawaii")  # the file of the United States does not draw Hawaii
        assert r.cls == cc.INSIDE and r.shape == NE and r.note == "USA: the file draws nothing within 25 km of the point", r
        r = c.decide("Triangle", "", -39.9, 60.5)                  # a shape with no ISO code
        assert r.cls == cc.INSIDE and r.shape == NE and r.note == "no ISO code: no detailed file", r
        r = c.decide("Guatemala", "", 15.01, -98.0)                # a file coarser than Natural Earth's own shape
        assert r.cls == cc.INSIDE and r.shape == NE and r.note == "GTM: the file has no more points (5) than Natural Earth's shape (5)", r
        assert c.grid("GTM") is None and c.grid("MEX") is not None and c.natural_points["USA"] == 20
        # no country: the detailed shapes of the countries around the point are asked
        assert c.decide("", "", 25.0, -100.04) == cc.Result(cc.DERIVE_IN, "Mexico", 0.0, "", 1.0, "geoBoundaries MEX", "")
        r = c.decide("", "", 25.0, -100.025)                       # in the water, 0.5 km from the detailed coast
        assert r.cls == cc.DERIVE_NEAR and r.country == "Mexico" and near(r.km, 0.4, 0.6), r
        r = c.decide("", "", 25.0, -100.01)                        # 2 km from it: not derived, listed with the distance
        assert r.cls == cc.DERIVE_FAR and r.country == "Mexico" and near(r.km, 1.9, 2.1) and r.shape == "geoBoundaries MEX", r
        assert c.decide("", "", 30.005, -105.0).shape == "geoBoundaries USA MEX"


def test_one_country_at_a_time():
    with tempfile.TemporaryDirectory() as folder:
        make_source(folder)
        c = detailed_checker(folder)
        order = []
        index_of = c.detail.index
        c.detail.index = lambda iso: (order.append(iso), index_of(iso))[1]
        rows = [(cc_key("United States", 30.01, -105.0), "1", "4"),          # near the border: the file of the United States
                (cc_key("Mexico", 25.0, -100.01), "1", "5"),                 # near the coast: the file of Mexico
                (cc_key("Mexico", 22.0, -102.0), "1", "100"),                # far from every border: no file
                (cc_key("USA", 39.99, -105.0), "21", "6"),                   # the United States again, under its old name
                (cc_key("Mexico", 25.0, -100.04), "21", "7"),
                (cc_key("Fiji", -17.0, 179.99), "1", "8"),                   # no file: Natural Earth
                (cc_key("Mexico", 25.0, 102.0), "1", "9"),                   # the other side of the world: no file
                (cc_key("Guatemala", 15.01, -98.0), "1", "11"),              # a file coarser than Natural Earth: not used
                (cc_key("", 30.2, -105.0), "21", "3"),                       # no country, the United States, 22 km from the border
                (cc_key("", 30.01, -105.0), "21", "1"),                      # no country, the United States, 1 km from the border
                (cc_key("", 25.0, -100.04), "21", "2")]                      # no country, near the coast of Mexico
        tally = cc.Tally(c.detail.kind)
        assert cc.check_points(c, rows, tally) == 11
        # each file is opened once, in the order of its code, whatever the order of the rows; then the points with no
        # country, in the order of their files too (the point at the border asks both countries)
        assert order == ["FJI", "GTM", "MEX", "USA", "MEX", "USA", "MEX"], order
        assert c.detail.keep == 2 and len(c.detail._loaded) <= 2
        assert tally.specimens[cc.INSIDE] == 4 + 100 + 6 + 7 + 8 + 11 and tally.specimens[cc.FLAG] == 5 + 9 and tally.specimens[cc.DERIVE_IN] == 2 + 3 + 1
        assert dict(tally.tested) == {NE: 100 + 8 + 9 + 11 + 3, "geoBoundaries, detailed files": 5 + 7 + 2 + 1, "geoBoundaries, coarse files": 4 + 6}, dict(tally.tested)
        assert dict(tally.notes) == {"FJI: geoBoundaries has no file": 8, "GTM: the file has no more points (5) than Natural Earth's shape (5)": 11}
        assert tally.bands["geoBoundaries, detailed files"][2] == [5, 0] and tally.bands[NE][4] == [9, 0]
        lists = cc.mapping_lists(c.shapes, c.countries)
        text = cc.report_text("2026-11-10 09:00", "abc1234", [200, 0, 0, 0, 0], tally, {}, 1.0, lists, [], "ne.geojson", "3 seconds", detail=c.detail)
        for part in ("Outside the stored country, tested on geoBoundaries, detailed files (15 specimens tested on it, the ones inside included):",
                     "Outside the stored country, tested on Natural Earth 1:10m (131 specimens tested on it, the ones inside included):",
                     "Detailed shapes: geoBoundaries gbOpen ADM0, commit " + "c" * 40 + ". 3 files read (",
                     "), 2 used: 1 detailed, 1 coarse (points more than 250 m apart). Each file: shape_files.csv.",
                     "Checksums: " + os.path.join(folder, "store", "geoboundaries-cccccccccccc.lock.json"),
                     "  Licences of the files used:\n    Creative Commons Attribution 4.0 (CC BY 4.0): MEX\n    Public Domain: USA\n",
                     "Natural Earth 1:10m used for these codes (2):\n  FJI (geoBoundaries has no file); GTM (the file has no more points (5) than Natural Earth's shape (5))",
                     "       11  GTM: the file has no more points (5) than Natural Earth's shape (5)",
                     "        8  FJI: geoBoundaries has no file",
                     "A line that coarse cannot place a coast to 1 km: read the bands under 2 km as within the shape's own error."):
            assert part in text, part + "\n" + text
        cc.write_shape_files(os.path.join(folder, "shape_files.csv"), c.detail)
        with open(os.path.join(folder, "shape_files.csv"), encoding="utf-8", newline="") as f:
            files = list(csv.reader(f))
        assert files[0] == ["iso_code", "used", "megabytes", "points_in_file", "points_kept", "median_metres_between_points", "coarse", "licence", "source",
                            "sha256", "seconds_to_read", "why_not_used"]
        assert [(line[0], line[1], line[6]) for line in files[1:]] == [("FJI", "no", ""), ("GTM", "no", "yes"), ("MEX", "yes", "no"), ("USA", "yes", "yes")]
        assert files[3][7:9] == ["Creative Commons Attribution 4.0 (CC BY 4.0)", "geoBoundaries, World Bank"] and len(files[3][9]) == 64
        assert files[1][-1] == "geoBoundaries has no file" and files[2][-1] == "the file has no more points (5) than Natural Earth's shape (5)"
        plain = cc.report_text("x", "y", [1, 0, 0, 0, 0], cc.Tally(), {}, 1.0, lists, [], "s", "1 second")
        assert "Detailed shapes: not used (--shapes-source naturalearth). Every point was tested on Natural Earth 1:10m." in plain


def cc_key(country, lat, lon, island="", stated=""):
    hexed = lambda text: text.encode("utf-8").hex().upper()
    return hexed(country) + "|" + hexed(island) + "|" + repr(lat) + "|" + repr(lon) + "|" + hexed(stated)


def test_report_and_lists():
    c = checker()
    rows = [(cc_key("Mexico", 22.0, -102.0), "1", "250000"), (cc_key("México", 22.5, -102.0), "21", "1200"),
            (cc_key("Mexico", 25.0, -99.995), "1", "40"), (cc_key("Mexico", 30.02, -105.0, stated="1 minute"), "1", "3"),
            (cc_key("Mexico", 25.0, 102.0), "21", "1500"), (cc_key("USA", 22.0, -102.0, stated="GPS"), "1", "12"),
            (cc_key("France", -21.0, 55.5), "1", "30"), (cc_key("Borneo", 1.0, 114.0), "1", "9"), (cc_key("Equador", -1.0, -78.0), "21", "5"),
            (cc_key("", 22.0, -102.0), "21", "60"), (cc_key("", 25.0, -99.995), "21", "10"), (cc_key("", 17.0, -95.5), "1", "4"),
            (cc_key("", 25.0, -97.0), "1", "3")]
    tally = cc.Tally()
    assert cc.check_points(c, rows, tally) == 13
    # the points kept for the three lists: outside their stored country (flagged or not), and the four with no country
    assert sorted(tally.flagged) == sorted(rows[i][0] for i in (2, 3, 4, 5, 9, 10, 11, 12))
    groups = {"1": "California Academy of Sciences", "21": "University of Utah"}
    lists = cc.mapping_lists(c.shapes, c.countries)
    text = cc.report_text("2026-11-10 09:00", "abc1234", [2500000, 400000, 1200, 30, 7], tally, groups, 1.0, lists, ["a note"],
                          "shapes.geojson (14 shapes; sha256 00)", "12 seconds")
    for part in ("Specimens: 2,500,000", "2,098,763  with coordinates that can be used", "  400,000  no coordinates",
                 "    1,200  latitude 0 and longitude 0", "       30  latitude outside -90 to 90", "        7  longitude outside -180 to 180",
                 "The allowed distance: 1 km (--tolerance-km), or the specimen's own stated error (latlonmaxerror) when that is larger.",
                 "        3  specimens with usable coordinates state an error that could be read",
                 "       12  state an error that could not be read: 1 km is used. The most frequent:\n           12  'GPS'",
                 "  251,200  inside the stored country",
                 "       30  inside a territory or shared area of the stored country (list below)",
                 "       40  in the water or across the border, within the allowed distance (near.csv)",
                 "    1,515  FLAGGED: in the water or across the border, farther than the allowed distance (conflicts.csv)",
                 "        5  not checked: the stored country is not known to AntWeb", "        9  not checked: the stored country has no shape",
                 "  up to 1 km                                0                            40",
                 "  2 to 5 km                                 3                             0",
                 "  over 10 km                            1,512                             0",
                 "With no country and usable coordinates (derive.csv): 77",
                 "       60  country can be derived: the point is inside one country",
                 "       10  country can be derived: in the water, within the allowed distance of one country",
                 "        4  two candidates: nothing derived", "        3  not derived: in the water, farther than the allowed distance from any country",
                 "Flagged: 1,515 specimens.", "    1,500  longitude sign", "       15  none of the four",
                 "    1,500  University of Utah", "       15  California Academy of Sciences",
                 "    1,500  Mexico -> in the water", "       12  United States -> Mexico", "        3  Mexico -> United States",
                 "Within the allowed distance, by upload group (the 50 largest):\n         40  California Academy of Sciences",
                 "       70  University of Utah", "       70  Mexico", "       30  France -> Reunion",
                 "        9  Borneo (no shape)", "        5  Equador (not known to AntWeb)",
                 "AntWeb valid countries with no shape (1); their specimens are not checked:\n  Borneo",
                 "Shapes with no AntWeb country (1); a point in one is named by the shape:\n  Atlantis",
                 "Somaliland -> Somalia (shared area table)", "  a note", "Run time: 12 seconds",
                 "Report only: nothing was changed in the database."):
        assert part in text, part + "\n" + text
    assert "nightly" not in text.lower()
    # the three files: one line per specimen of a kept point, by upload group and code; other points left out
    hexed = lambda text: text.encode("utf-8").hex().upper()
    answer = [[hexed("utah0002"), "21", cc_key("Mexico", 25.0, 102.0), hexed("Chihuahua"), hexed("Cañón, \"El Alto\"")],
              [hexed("casent0009"), "1", cc_key("USA", 22.0, -102.0, stated="GPS"), hexed(""), hexed("")],
              [hexed("utah0001"), "21", cc_key("Mexico", 25.0, 102.0), hexed(""), hexed("")],
              [hexed("casent0001"), "1", cc_key("Mexico", 22.0, -102.0), hexed(""), hexed("")],
              [hexed("casent0002"), "1", cc_key("Mexico", 25.0, -99.995), hexed("Tamaulipas"), hexed("beach")],
              [hexed("casent0003"), "1", cc_key("Mexico", 30.02, -105.0, stated="1 minute"), hexed(""), hexed("")],
              [hexed("utah0003"), "99", cc_key("", 17.0, -95.5), hexed(""), hexed("")]]
    with tempfile.TemporaryDirectory() as folder:
        assert cc.write_lists(folder, [answer[:2], answer[2:]], tally.flagged, groups) == (4, 1, 1)
        read = {}
        for name in ("conflicts.csv", "near.csv", "derive.csv"):
            with open(os.path.join(folder, name), encoding="utf-8") as f:
                read[name] = f.read().split("\n")
    head = "code,upload_group,stored_country,state,locality_name,latitude,longitude,coordinate_country,class,distance_km,likely_error,stated_error,allowed_km,shape,note"
    assert all(lines[0] == head and lines[-1] == "" for lines in read.values())
    conflicts = read["conflicts.csv"]
    assert conflicts[1] == "casent0003,California Academy of Sciences,Mexico,,,30.02,-105.0,United States,flagged: in the water or across the border,2.22,,1 minute,1.852,Natural Earth 1:10m,", conflicts[1]
    assert conflicts[2] == "casent0009,California Academy of Sciences,USA,,,22.0,-102.0,Mexico,flagged: in the water or across the border,890,,GPS,1,Natural Earth 1:10m,", conflicts[2]
    assert conflicts[3].startswith("utah0001,University of Utah,Mexico,,,25.0,102.0,in the water,flagged: in the water or across the border,") and ",longitude sign,,1," in conflicts[3]
    assert conflicts[4].startswith("utah0002,University of Utah,Mexico,Chihuahua,\"Cañón, \"\"El Alto\"\"\",25.0,102.0,") and len(conflicts) == 6
    assert read["near.csv"][1:] == ["casent0002,California Academy of Sciences,Mexico,Tamaulipas,beach,25.0,-99.995,in the water,"
                                    "\"in the water or across the border, within the allowed distance\",0.5,,,1,Natural Earth 1:10m,", ""], read["near.csv"]
    assert read["derive.csv"][1:] == ["utah0003,group 99,,,,17.0,-95.5,Guatemala or Belize,two candidates,0,,,1,Natural Earth 1:10m,", ""], read["derive.csv"]
    # a quick run says so
    quick = cc.report_text("x", "y", [10, 0, 0, 0, 0], cc.Tally(), {}, 2.5, lists, [], "s", "1 second", only="Mexico, none")
    assert "A quick run: only specimens stored under Mexico, none." in quick and "The allowed distance: 2.5 km" in quick


def test_quick_run_names():
    known = countries()
    assert cc.stored_names(known, ["United States"]) == ["Hawaii", "USA", "United States"]
    assert cc.stored_names(known, ["USA", "none"]) == ["", "Hawaii", "USA", "United States"]
    assert cc.only_sql(["", "Mexico", "O'Brien"]) == " and (s.country in ('Mexico', 'O''Brien') or s.country is null or s.country = '')"
    assert cc.only_sql([]) == ""
    try:
        cc.stored_names(known, ["Equador"])
        raise AssertionError("an unknown country must stop the run")
    except SystemExit as e:
        assert "Equador" in str(e)


def test_every_statement_is_a_single_select():
    only = cc.only_sql(["", "Mexico", "O'Brien; drop table specimen", "Update"])
    key, bare_key = cc.key_sql({"code", "latlonmaxerror"}), cc.key_sql({"code"})
    # the stated error is part of the point's text only when the table has the column
    assert "hex(ifnull(s.latlonmaxerror, ''))" in key and "latlonmaxerror" not in bare_key and bare_key.endswith("'|', '')")
    assert cc.split_key("4D|48|1.5|-2|33206B6D") == ("M", "H", "1.5", "-2", "3 km") and cc.split_key("||1|2|") == ("", "", "1", "2", "")
    statements = [cc.columns_sql("geolocale"), cc.columns_sql("specimen"), cc.GROUPS_SQL, cc.countries_sql({"isocode", "iso3code"}),
                  cc.countries_sql(set()), cc.counts_sql(), cc.counts_sql(only), cc.points_sql(key), cc.points_sql(bare_key, only),
                  cc.specimens_sql(key, ["4D|5A|1.5|-2|", "'; delete from specimen; --"], only)]
    for sql in statements:
        assert cc.read_only(sql) == sql
        bare = cc.QUOTED.sub("''", sql).lower()
        assert bare.startswith("select ") and ";" not in bare
        for word in ("insert", "update", "delete", "replace", "create", "drop", "alter", "truncate", "rename", "grant", "into", "set", "call", "lock"):
            assert word not in cc.re.findall(r"[a-z_]+", bare), (word, sql)
    assert "isoCode" in statements[3] and "iso3Code" in statements[3] and "iso" not in statements[4].lower()
    try:
        cc.columns_sql("specimen; drop table specimen")
        raise AssertionError("only the two tables")
    except ValueError:
        pass
    for bad in ("update specimen set country = 'x'", "select 1; delete from specimen", "delete from specimen",
                "select code from specimen into outfile '/tmp/x'", "create table t as select 1", " drop table specimen",
                "select 1 from specimen for update", "insert into specimen (code) values ('x')", "set @a = 1"):
        try:
            cc.read_only(bad)
            raise AssertionError("accepted: " + bad)
        except ValueError:
            pass
    # nothing reaches the database except through rows_sql, and rows_sql sends only what read_only accepts
    source = inspect.getsource(cc)
    assert source.count("subprocess.Popen(") == 1 and "subprocess.run(" not in source and "os.system" not in source
    assert "p.stdin.write(read_only(sql)" in inspect.getsource(cc.rows_sql)
    # the password travels in the environment, never on the command line
    assert '"-e", "MYSQL_PWD", container' in inspect.getsource(cc.rows_sql) and "-p" not in cc.re.findall(r'"(-\w+)"', inspect.getsource(cc.rows_sql))


def test_shape_file_checksum():
    with tempfile.TemporaryDirectory() as folder:
        source = os.path.join(folder, "source.geojson")
        with open(source, "w", encoding="utf-8") as f:
            f.write('{"features": []}')
        right = hashlib.sha256(b'{"features": []}').hexdigest()
        target = os.path.join(folder, "shapes")
        path = cc.fetch_shapes(target, url="file://" + source, sha256=right)
        assert os.path.dirname(path) == target and cc.SHAPES_COMMIT[:12] in os.path.basename(path) and cc.sha256_of(path) == right
        os.remove(source)
        assert cc.fetch_shapes(target, url="file://" + source, sha256=right) == path      # kept: not fetched twice
        with open(source, "w", encoding="utf-8") as f:
            f.write('{"features": [1]}')
        other = os.path.join(folder, "other")
        try:
            cc.fetch_shapes(other, url="file://" + source, sha256=right)
            raise AssertionError("a file with another checksum must not be used")
        except SystemExit as e:
            assert "sha256" in str(e)
        assert os.listdir(other) == []
    # the pinned file: a full commit, a 64-character checksum, the commit in the address
    assert len(cc.SHAPES_COMMIT) == 40 and len(cc.SHAPES_SHA256) == 64 and cc.SHAPES_COMMIT in cc.SHAPES_URL
    assert cc.SHAPES_URL.startswith("https://raw.githubusercontent.com/nvkelso/natural-earth-vector/")


if __name__ == "__main__":
    tests = [(n, f) for n, f in sorted(globals().items()) if n.startswith("test_") and callable(f)]
    for name, fn in tests:
        fn()
    print(str(len(tests)) + " tests passed")
