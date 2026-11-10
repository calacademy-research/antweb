"""tools/place/coordinate_country.py: the coordinates of a specimen against its stored country (2026-11-10, Brian
Fisher: "if there is no country but lat long then we should derive the country: also lat long could be in conflict
with the country given, that should result in an upload report so it is verified").

No database and no network: the country shapes are small squares written here, the AntWeb countries a short
list. Tested: a point inside a shape, inside a hole, on a border, 2 km and 300 km off the coast, inside two
shapes, in a shape of several pieces, across the 180 meridian; latitude and longitude swapped and wrong signs;
0 / 0 and out-of-range coordinates; an island country against its parent; an old country name; a country AntWeb
does not know or that has no shape (not checked, never a conflict); the lists of the mapping; the grid index
against plain ray casting on 4,000 random points; the report; and that every statement sent to the database
is a single select.

Run: python3 tests/unit/test_coordinate_country.py   (also in .github/workflows/tests.yaml)
"""
import hashlib
import importlib.util
import inspect
import os
import random
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
spec = importlib.util.spec_from_file_location("coordinate_country", os.path.join(ROOT, "tools", "place", "coordinate_country.py"))
cc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cc)


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
        feature("Triangle", [[[[60, -40], [90, -35], [70, -10], [60, -40]]]], ISO_A3="TRI"),
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


def checker(tolerance=5.0):
    if tolerance not in _made:
        shapes = cc.read_shapes(geojson())
        known = countries()
        notes = cc.map_shapes(shapes, known)
        _made[tolerance] = (cc.Checker(known, shapes, cc.Index(shapes), tolerance), notes)
    return _made[tolerance][0]


def d(country, lat, lon, island=""):
    return checker().decide(country, island, lat, lon)


def test_inside():
    assert d("Mexico", 22.0, -102.0) == cc.Result(cc.AGREE, "Mexico", 0.0, ""), d("Mexico", 22.0, -102.0)
    assert d("", 22.0, -102.0) == cc.Result(cc.DERIVE_IN, "Mexico", 0.0, "")
    assert d("France", 46.0, 2.0).cls == cc.AGREE


def test_inside_a_hole():
    # the middle of the hole is 1 degree (about 101 km) from the nearest land of Mexico
    r = d("Mexico", 25.0, -105.0)
    assert r.cls == cc.SEA and r.country == "" and 99 <= r.km <= 102, r
    assert d("", 25.0, -105.0).cls == cc.DERIVE_SEA
    # just inside the hole, 1 km from its edge: the stored country is accepted, and it can be derived
    r = d("Mexico", 25.0, -104.01)
    assert r.cls == cc.AGREE_NEAR and 0.9 <= r.km <= 1.1, r
    assert d("", 25.0, -104.01) == cc.Result(cc.DERIVE_NEAR, "Mexico", r.km, "")
    # and outside the hole again
    assert d("Mexico", 25.0, -103.9).cls == cc.AGREE


def test_on_a_border_and_near_it():
    # exactly on the border: never a conflict, whichever side it is counted on
    for stored in ("Mexico", "United States"):
        assert d(stored, 30.0, -105.0).cls in (cc.AGREE, cc.BORDER), (stored, d(stored, 30.0, -105.0))
    # 2.2 km inside the United States, stored as Mexico: near the border, not a conflict
    r = d("Mexico", 30.02, -105.0)
    assert r.cls == cc.BORDER and r.country == "United States" and 2.1 <= r.km <= 2.3, r
    # 111 km inside: a conflict, with the distance to Mexico
    r = d("Mexico", 31.0, -105.0)
    assert r.cls == cc.CONFLICT and r.country == "United States" and 110 <= r.km <= 112 and r.error == "", r
    # with no country, a point that close to two countries is not derived
    assert d("", 30.02, -105.0) == cc.Result(cc.TWO, "United States or Mexico", 0.0, "")
    assert d("", 30.2, -105.0) == cc.Result(cc.DERIVE_IN, "United States", 0.0, "")


def test_off_the_coast():
    # 2 km east of Mexico's east coast (0.02 degrees of longitude at latitude 25)
    r = d("Mexico", 25.0, -99.98)
    assert r.cls == cc.AGREE_NEAR and 1.9 <= r.km <= 2.1, r
    r = d("", 25.0, -99.98)
    assert r.cls == cc.DERIVE_NEAR and r.country == "Mexico" and 1.9 <= r.km <= 2.1, r
    # 300 km off: at sea
    r = d("Mexico", 25.0, -97.0)
    assert r.cls == cc.SEA and 300 <= r.km <= 304, r
    assert d("", 25.0, -97.0) == cc.Result(cc.DERIVE_SEA, "", None, "")
    # the tolerance decides: 2 km off is at sea with a tolerance of 1 km
    assert checker(1.0).decide("Mexico", "", 25.0, -99.98).cls == cc.SEA
    assert checker(1.0).decide("", "", 25.0, -99.98).cls == cc.DERIVE_SEA


def test_inside_two_shapes():
    r = d("", 17.0, -95.5)
    assert r.cls == cc.TWO and r.country == "Guatemala or Belize", r
    assert d("Belize", 17.0, -95.5).cls == cc.AGREE and d("Guatemala", 17.0, -95.5).cls == cc.AGREE
    r = d("France", 17.0, -95.5)
    assert r.cls == cc.CONFLICT and r.country == "Guatemala or Belize", r
    # off the coast and within the tolerance of both: two candidates too
    assert d("", 15.99, -95.999).cls == cc.TWO


def test_swapped_and_wrong_signs():
    # France is latitude 44 to 49, longitude 0 to 5; (2, 46) is in Somalia
    r = d("France", 2.0, 46.0)
    assert r.cls == cc.CONFLICT and r.country == "Somalia" and r.error == "latitude and longitude swapped", r
    r = d("Mexico", 25.0, 102.0)
    assert r.cls == cc.SEA and r.error == "longitude sign", r
    r = d("Reunion", 21.0, 55.5)
    assert r.cls == cc.SEA and r.error == "latitude sign", r
    r = d("Mexico", -22.0, 102.0)
    assert r.error == "both signs", r
    # a point that no single slip explains
    assert d("Mexico", 46.0, 2.0).error == ""
    # a slip that lands within the tolerance of the stored country counts as well
    assert d("Mexico", 25.0, 99.98).error == "longitude sign"


def test_unusable_coordinates():
    assert not cc.usable(0.0, 0.0) and not cc.usable(91.0, 10.0) and not cc.usable(-90.5, 10.0)
    assert not cc.usable(10.0, 181.0) and not cc.usable(10.0, -180.5)
    assert cc.usable(0.0, 10.0) and cc.usable(10.0, 0.0) and cc.usable(-90.0, 180.0)
    # the database leaves them out of the points and counts each kind once
    for part in ("is not null", "not (s.decimal_latitude = 0 and s.decimal_longitude = 0)", "between -90 and 90", "between -180 and 180"):
        assert part in cc.USABLE, part
    assert cc.USABLE in cc.points_sql() and cc.USABLE in cc.specimens_sql(["A|B|1|2"])
    assert cc.counts_sql().count("sum(") == 4
    # a swap or a sign change never tests a point that cannot exist (longitude -120 as a latitude)
    assert d("Mexico", 60.0, -120.0).error == ""


def test_island_country_and_its_parent():
    kauai, mainland = (21.5, -159.5), (35.0, -105.0)
    assert d("United States", *kauai, island="Hawaii").cls == cc.AGREE
    assert d("United States", *mainland, island="Hawaii").cls == cc.AGREE
    assert d("United States", *kauai).cls == cc.AGREE            # stored under the parent only
    assert d("Hawaii", *kauai).cls == cc.AGREE
    assert d("Hawaii", *mainland).cls == cc.AGREE                # both ways
    assert d("", *kauai) == cc.Result(cc.DERIVE_IN, "Hawaii", 0.0, "")
    r = d("Mexico", *kauai)
    assert r.cls == cc.CONFLICT and r.country == "Hawaii", r
    r = d("Hawaii", 25.0, -102.0)
    assert r.cls == cc.CONFLICT and r.country == "Mexico", r


def test_old_name_and_spelling():
    assert d("USA", 35.0, -105.0) == cc.Result(cc.AGREE, "United States", 0.0, "")
    assert d("México", 22.0, -102.0).cls == cc.AGREE
    assert d("  mexico ", 22.0, -102.0).cls == cc.AGREE
    r = d("USA", 22.0, -102.0)
    assert r.cls == cc.CONFLICT and r.country == "Mexico", r


def test_not_checked_is_never_a_conflict():
    for lat, lon in ((22.0, -102.0), (25.0, -97.0), (46.0, 2.0)):
        assert d("Equador", lat, lon) == cc.Result(cc.UNKNOWN, "", None, "")       # a name AntWeb does not know
        assert d("Zaire", lat, lon) == cc.Result(cc.UNKNOWN, "", None, "")         # an old name that points nowhere
        assert d("Borneo", lat, lon) == cc.Result(cc.NO_SHAPE, "", None, "")       # a valid country with no shape
    tally = cc.Tally()
    rows = [(cc_key("Borneo", 22.0, -102.0), "1", "7"), (cc_key("Equador", 22.0, -102.0), "1", "2")]
    cc.check_points(checker(), rows, tally)
    assert tally.specimens[cc.NO_SHAPE] == 7 and tally.specimens[cc.UNKNOWN] == 2
    assert tally.specimens[cc.CONFLICT] == 0 and tally.specimens[cc.SEA] == 0 and not tally.flagged


def test_territory_and_shared_area():
    # Reunion is a country of its own in AntWeb and a part of France in the shape file
    assert d("Reunion", -21.0, 55.5).cls == cc.AGREE
    r = d("France", -21.0, 55.5)
    assert r.cls == cc.TERRITORY and r.country == "Reunion", r
    r = d("Reunion", 46.0, 2.0)
    assert r.cls == cc.CONFLICT and r.country == "France", r
    # Somaliland is not an AntWeb country: the shared-area table gives it to Somalia
    assert d("Somalia", 9.0, 45.0).cls == cc.AGREE
    assert d("", 9.0, 45.0) == cc.Result(cc.DERIVE_IN, "Somalia", 0.0, "")
    # an area with two countries in the table: both are accepted, none is derived
    shapes = cc.read_shapes(geojson())
    known = countries()
    cc.map_shapes(shapes, known, shared={"Atlantis": ["Mexico", "France"]})
    both = cc.Checker(known, shapes, cc.Index(shapes), 5.0)
    assert both.decide("Mexico", "", 31.0, -29.0).cls == cc.AGREE
    assert both.decide("France", "", 31.0, -29.0).cls == cc.TERRITORY
    assert both.decide("Fiji", "", 31.0, -29.0).cls == cc.CONFLICT
    assert both.decide("", "", 31.0, -29.0) == cc.Result(cc.TWO, "Mexico or France", 0.0, "")


def test_area_with_no_antweb_country():
    r = d("", 31.0, -29.0)
    assert r.cls == cc.DERIVE_NONE and r.country == "Atlantis", r
    r = d("Mexico", 31.0, -29.0)
    assert r.cls == cc.CONFLICT and r.country == "Atlantis (not an AntWeb country)", r


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
    # the name wins over the code, and the report says so
    known = cc.Countries([cc.Country("Juan de Nova Island", 1, "", 0, ""), cc.Country("French Southern Territories", 1, "", 0, "", "TF", "ATF")])
    shapes = cc.read_shapes({"features": [feature("Juan De Nova Island", [[square(0, 0, 1, 1)]], ISO_A3="ATF")]})
    notes = cc.map_shapes(shapes, known)
    assert shapes[0].country == "Juan de Nova Island"
    assert notes == ["Juan De Nova Island: the name says Juan de Nova Island, the ISO code says French Southern Territories (the name is used)"]


def test_several_pieces():
    assert d("", 19.5, -155.5) == cc.Result(cc.DERIVE_IN, "Hawaii", 0.0, "")     # the second piece
    assert d("", 20.5, -157.5).cls == cc.DERIVE_SEA                              # between the two
    assert d("United States", 19.5, -155.5).cls == cc.AGREE


def test_across_the_180_meridian():
    assert d("Fiji", -17.0, 179.99).cls == cc.AGREE and d("Fiji", -17.0, -179.5).cls == cc.AGREE
    assert d("Fiji", -17.0, 180.0).cls in (cc.AGREE, cc.AGREE_NEAR) and d("Fiji", -17.0, -180.0).cls in (cc.AGREE, cc.AGREE_NEAR)
    # Kiribati ends at 180; a point 1.1 km beyond, written with a negative longitude, is near it
    r = d("Kiribati", 1.0, -179.99)
    assert r.cls == cc.AGREE_NEAR and 1.0 <= r.km <= 1.2, r
    assert d("", 1.0, -179.99).country == "Kiribati"
    # the far distance goes the short way round: 5 degrees, not 355
    r = d("Kiribati", 1.0, -175.0)
    assert r.cls == cc.SEA and 550 <= r.km <= 560, r
    # a wrong longitude sign next to 180 is found: Fiji's east piece is at -180 to -179
    assert d("Kiribati", -17.0, 179.5).cls == cc.CONFLICT


def plain_inside(shape, lat, lon):
    """Plain ray casting over every border piece of the shape: the reference for the index."""
    inside = False
    for polygon in shape.polygons:
        for ring in polygon:
            for (x1, y1), (x2, y2) in zip(ring, ring[1:]):
                if (y1 > lat) != (y2 > lat) and x1 + (lat - y1) * (x2 - x1) / (y2 - y1) > lon:
                    inside = not inside
    return inside


def test_index_against_plain_ray_casting():
    rnd = random.Random(20261110)
    # a jagged shape of 400 corners around (10, -60), so that many cells hold many border pieces
    star = [[10 + (4 + 3 * rnd.random()) * cc.math.cos(i * cc.math.pi / 200), -60 + (4 + 3 * rnd.random()) * cc.math.sin(i * cc.math.pi / 200)]
            for i in range(400)]
    data = geojson()
    data["features"].append(feature("Star", [[star + [star[0]]]]))
    shapes = cc.read_shapes(data)
    index = cc.Index(shapes)
    boxes = [(-112, 18, -92, 42), (-161, 18, -154, 23), (54, -22, 57, -20), (176, -19, 180, -15), (-180, -19, -178, -15),
             (58, -42, 92, -8), (2, -68, 18, -52), (40, -1, 49, 12)]
    for i in range(4000):
        west, south, east, north = boxes[i % len(boxes)]
        lon, lat = rnd.uniform(west, east), rnd.uniform(south, north)
        got = sorted(shapes[n].name for n in index.inside(lat, lon))
        want = sorted(s.name for s in shapes if plain_inside(s, lat, lon))
        assert got == want, (lat, lon, got, want)
    # forget=True gives the borders back to the system once they are in the index: same answers
    lean = cc.Index(cc.read_shapes(data), forget=True)
    assert lean.inside(25.0, -102.0) == index.inside(25.0, -102.0) and lean.inside(25.0, -105.0) == []


def test_distances():
    assert abs(cc.km_between(0.0, 0.0, 0.0, 1.0) - 111.195) < 0.01
    assert abs(cc.km_between(0.0, 179.5, 0.0, -179.5) - 111.195) < 0.01
    index, shapes = checker().index, checker().shapes
    mexico = [n for n, s in enumerate(shapes) if s.name == "Mexico"]
    # 10 degrees south of Mexico's south coast
    assert abs(index.distance(mexico, 10.0, -105.0) - 1111.95) < 1.0
    # near() and distance() agree close to a border
    near = index.near(19.95, -105.0, 10.0)
    assert abs(near[mexico[0]] - index.distance(mexico, 19.95, -105.0)) < 0.01
    assert index.near(10.0, -105.0, 10.0) == {} and index.distance([], 10.0, -105.0) is None


def cc_key(country, lat, lon, island=""):
    return country.encode("utf-8").hex().upper() + "|" + island.encode("utf-8").hex().upper() + "|" + repr(lat) + "|" + repr(lon)


def test_report_and_lists():
    c = checker()
    rows = [(cc_key("Mexico", 22.0, -102.0), "1", "250000"), (cc_key("México", 22.5, -102.0), "21", "1200"),
            (cc_key("Mexico", 25.0, -99.98), "1", "40"), (cc_key("Mexico", 30.02, -105.0), "1", "3"),
            (cc_key("Mexico", 25.0, 102.0), "21", "1500"), (cc_key("USA", 22.0, -102.0), "1", "12"),
            (cc_key("France", -21.0, 55.5), "1", "30"), (cc_key("Borneo", 1.0, 114.0), "1", "9"), (cc_key("Equador", -1.0, -78.0), "21", "5"),
            (cc_key("", 22.0, -102.0), "21", "60"), (cc_key("", 25.0, -99.98), "21", "10"), (cc_key("", 17.0, -95.5), "1", "4"),
            (cc_key("", 25.0, -97.0), "1", "3")]
    tally = cc.Tally()
    assert cc.check_points(c, rows, tally) == 13
    # the points kept for the two lists: the two conflicts and the four with no country
    assert sorted(tally.flagged) == sorted(rows[i][0] for i in (4, 5, 9, 10, 11, 12))
    groups = {"1": "California Academy of Sciences", "21": "University of Utah"}
    lists = cc.mapping_lists(c.shapes, c.countries)
    text = cc.report_text("2026-11-10 09:00", "abc1234", [2500000, 400000, 1200, 30, 7], tally, groups, 5.0, lists, ["a note"],
                          "shapes.geojson (14 shapes; sha256 00)", "12 seconds")
    for part in ("Specimens: 2,500,000", "2,098,763  with coordinates that can be used", "  400,000  no coordinates",
                 "    1,200  latitude 0 and longitude 0", "       30  latitude outside -90 to 90", "        7  longitude outside -180 to 180",
                 "  251,200  agree: the point is in the stored country",
                 "       40  agree: just outside the shape (coast, small island), within 5 km of the stored country",
                 "       30  agree: the point is in a territory or shared area of the stored country",
                 "        3  near the border: in a neighbouring country, within 5 km of the stored one (not a conflict)",
                 "       12  CONFLICT: the point is in another country (conflicts.csv)",
                 "    1,500  CONFLICT: at sea, no country within 5 km (conflicts.csv)",
                 "        5  not checked: the stored country is not known to AntWeb", "        9  not checked: the stored country has no shape",
                 "With no country and usable coordinates (derive.csv): 77",
                 "       60  country can be derived: the point is inside one country",
                 "       10  country can be derived: the point is within 5 km of one country",
                 "        4  two candidates: the point is in or near two countries", "        3  at sea, no country within 5 km",
                 "Conflicts: 1,512 specimens.", "    1,500  longitude sign", "       12  none of the four",
                 "    1,500  University of Utah", "       12  California Academy of Sciences",
                 "    1,500  Mexico -> at sea", "       12  United States -> Mexico",
                 "       70  University of Utah", "       70  Mexico", "       30  France -> Reunion",
                 "        9  Borneo (no shape)", "        5  Equador (not known to AntWeb)",
                 "AntWeb valid countries with no shape (1); their specimens are not checked:\n  Borneo",
                 "Shapes with no AntWeb country (1); a point in one is named by the shape:\n  Atlantis",
                 "Somaliland -> Somalia (shared area table)", "  a note", "Tolerance: 5 km", "Run time: 12 seconds",
                 "Report only: nothing was changed in the database."):
        assert part in text, part + "\n" + text
    assert "nightly" not in text.lower()
    # the lines of the two lists
    key = cc_key("Mexico", 25.0, 102.0)
    line = cc.csv_line("casent0001", "University of Utah", key, "Chihuahua", "5 km N of Town", tally.flagged[key])
    assert line[:9] == ["casent0001", "University of Utah", "Mexico", "Chihuahua", "5 km N of Town", "25.0", "102.0", "", cc.SEA], line
    assert float(line[9]) > 10000 and line[10] == "longitude sign" and len(line) == len(cc.HEADER) == 11
    key = cc_key("", 25.0, -99.98)
    line = cc.csv_line("utah0001", "University of Utah", key, "", "", tally.flagged[key])
    assert line[7:] == ["Mexico", cc.DERIVE_NEAR, "2", ""], line
    # the two files: one line per specimen of a kept point, by upload group and code; other points left out
    hexed = lambda text: text.encode("utf-8").hex().upper()
    answer = [[hexed("utah0002"), "21", cc_key("Mexico", 25.0, 102.0), hexed("Chihuahua"), hexed("Cañón, \"El Alto\"")],
              [hexed("casent0009"), "1", cc_key("USA", 22.0, -102.0), hexed(""), hexed("")],
              [hexed("utah0001"), "21", cc_key("Mexico", 25.0, 102.0), hexed(""), hexed("")],
              [hexed("casent0001"), "1", cc_key("Mexico", 22.0, -102.0), hexed(""), hexed("")],
              [hexed("utah0003"), "99", cc_key("", 17.0, -95.5), hexed(""), hexed("")]]
    with tempfile.TemporaryDirectory() as folder:
        assert cc.write_lists(folder, [answer[:2], answer[2:]], tally.flagged, groups) == (3, 1)
        with open(os.path.join(folder, "conflicts.csv"), encoding="utf-8") as f:
            conflicts = f.read().split("\n")
        with open(os.path.join(folder, "derive.csv"), encoding="utf-8") as f:
            derive = f.read().split("\n")
    assert conflicts[0] == "code,upload_group,stored_country,state,locality_name,latitude,longitude,coordinate_country,class,distance_km,likely_error"
    assert conflicts[1] == "casent0009,California Academy of Sciences,USA,,,22.0,-102.0,Mexico,conflict,890,", conflicts[1]
    assert conflicts[2].startswith("utah0001,University of Utah,Mexico,,,25.0,102.0,,\"conflict, at sea\",") and conflicts[2].endswith(",longitude sign")
    assert conflicts[3].startswith("utah0002,University of Utah,Mexico,Chihuahua,\"Cañón, \"\"El Alto\"\"\",25.0,102.0,") and conflicts[4:] == [""]
    assert derive[1:] == ["utah0003,group 99,,,,17.0,-95.5,Guatemala or Belize,two candidates,0,", ""], derive
    # a quick run says so
    quick = cc.report_text("x", "y", [10, 0, 0, 0, 0], cc.Tally(), {}, 10.0, lists, [], "s", "1 second", only="Mexico, none")
    assert "A quick run: only specimens stored under Mexico, none." in quick and "Tolerance: 10 km" in quick


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
    statements = [cc.COLUMNS_SQL, cc.GROUPS_SQL, cc.countries_sql({"isocode", "iso3code"}), cc.countries_sql(set()),
                  cc.counts_sql(), cc.counts_sql(only), cc.points_sql(), cc.points_sql(only),
                  cc.specimens_sql(["4D|5A|1.5|-2", "'; delete from specimen; --"], only)]
    for sql in statements:
        assert cc.read_only(sql) == sql
        bare = cc.QUOTED.sub("''", sql).lower()
        assert bare.startswith("select ") and ";" not in bare
        for word in ("insert", "update", "delete", "replace", "create", "drop", "alter", "truncate", "rename", "grant", "into", "set", "call", "lock"):
            assert word not in cc.re.findall(r"[a-z_]+", bare), (word, sql)
    assert "isoCode" in statements[2] and "iso3Code" in statements[2] and "iso" not in statements[3].lower()
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
