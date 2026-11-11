"""tools/place/place_corrections.py: the country and state a specimen is missing, proposed from its coordinates, as
a working list for TaxonWorks (2026-11-10, Brian Fisher: "we would need the casent specimen code and the field and
value for updating TW ... currently the list is so long they are ignored: some of course can not be corrected but
maybe some can be based on lat long or other means").

No database and no network: country shapes and state shapes are small squares written here, read through file:
addresses; AntWeb's countries and states are short lists; the mapping file is the one of the repository.
Tested: the level of the boundary files is chosen by how the names fit AntWeb's states (Reunion: ADM4, not ADM1);
a row of the mapping file (Madagascar's regions to provinces, Coahuila de Zaragoza); a point inside one state;
near two states; in the water near one; outside the stated country; an island-country specimen left out; a
country with no states in AntWeb; no coordinates; no boundary file; a shape that is no AntWeb state; a missing
country and a country AntWeb does not know; a collecting event with two different proposals flagged; the four
files; and that every statement sent to the database is a single select.

Run: python3 tests/unit/test_place_corrections.py   (also in .github/workflows/tests.yaml)
"""
import collections
import contextlib
import csv
import hashlib
import importlib.util
import inspect
import io
import json
import os
import tempfile
import zipfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
spec = importlib.util.spec_from_file_location("place_corrections", os.path.join(ROOT, "tools", "place", "place_corrections.py"))
pc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pc)
cc = pc.cc
NE = cc.NATURAL_EARTH


def square(west, south, east, north):
    return [[west, south], [east, south], [east, north], [west, north], [west, south]]


def feature(name, polygons, **props):
    props["NAME"] = name
    kind = "MultiPolygon" if len(polygons) > 1 else "Polygon"
    return {"type": "Feature", "properties": props, "geometry": {"type": kind, "coordinates": polygons if len(polygons) > 1 else polygons[0]}}


def world():
    usa = dict(ADMIN="United States of America", ISO_A3="-99", ISO_A3_EH="USA", SOVEREIGNT="United States of America")
    return {"type": "FeatureCollection", "features": [
        feature("Mexico", [[square(-110, 20, -100, 30)]], ISO_A3="MEX"),
        feature("United States of America", [[square(-110, 30, -100, 40)]], SUBUNIT="United States", **usa),
        feature("Hawaii", [[square(-160, 21, -159, 22)]], **usa),
        feature("Madagascar", [[square(43, -26, 51, -12)]], ISO_A3="MDG"),
        feature("Réunion", [[square(55, -21.5, 56, -20.5)]], ISO_A3="REU"),
        feature("Tinyland", [[square(0, 0, 1, 1)]], ISO_A3="TNY"),
        feature("Atoll", [[square(10, 0, 11, 1)]], ISO_A3="ATL"),
        feature("Andesia", [[square(-75, -40, -70, -20)]], ISO_A3="ADS"),
        feature("Iberia", [[square(-9, 36, 3, 44)]], ISO_A3="IBR"),
        feature("Congoland", [[square(20, -6, 30, 0)]], ISO_A3="CGL"),
        feature("Omanland", [[square(50, 16, 54, 24)]], ISO_A3="OML"),
        feature("Nameland", [[square(60, 0, 67, 2)]], ISO_A3="NML"),
        feature("Splitland", [[square(70, 0, 76, 2)]], ISO_A3="SPL"),
        feature("Isleland", [[square(80, 0, 82, 2)]], ISO_A3="ILE"),
    ]}


def countries():
    C = cc.Country
    return cc.Countries([C("Mexico", 1, "", 0, "", "MX", "MEX"), C("United States", 1, "", 0, "", "US", "USA"), C("USA", 0, "United States", 0, ""),
                         C("Hawaii", 1, "", 1, "United States"), C("Madagascar", 1, "", 0, "", "MG", "MDG"), C("Reunion", 1, "", 0, "", "RE", "REU"),
                         C("Tinyland", 1, "", 0, "", "TN", "TNY"), C("Atoll", 1, "", 0, "", "AT", "ATL"),
                         C("Andesia", 1, "", 0, "", "AD", "ADS"), C("Iberia", 1, "", 0, "", "IB", "IBR"),
                         C("Congoland", 1, "", 0, "", "CG", "CGL"), C("Omanland", 1, "", 0, "", "OM", "OML"),
                         C("Nameland", 1, "", 0, "", "NM", "NML"), C("Splitland", 1, "", 0, "", "SP", "SPL"),
                         C("Isleland", 1, "", 0, "", "IL", "ILE")])


# AntWeb's states: (name, parent, is_valid, valid_name)
STATES = [("Sonora", "Mexico", 1, ""), ("Chihuahua", "Mexico", 1, ""), ("Coahuila", "Mexico", 1, ""), ("Chih.", "Mexico", 0, "Chihuahua"),
          ("Arizona", "United States", 1, ""), ("New Mexico", "United States", 1, ""), ("Hawaii", "United States", 1, ""),
          ("Antsiranana", "Madagascar", 1, ""), ("Toliara", "Madagascar", 1, ""), ("Tulear", "Madagascar", 0, "Toliara"),
          ("Saint-Denis", "Reunion", 1, ""), ("Saint-Pierre", "Reunion", 1, ""), ("Lagoon", "Atoll", 1, ""),
          ("Lost", "Nowhere", 1, ""),
          ("Norte", "Andesia", 1, ""), ("Sur", "Andesia", 1, ""),
          ("Andalusia", "Iberia", 1, ""), ("Catalonia", "Iberia", 1, ""), ("Madrid", "Iberia", 1, ""), ("Navarre", "Iberia", 1, ""),
          ("Navarra", "Iberia", 0, "Navarre"),
          ("Bas-Uele", "Congoland", 1, ""), ("Equateur", "Congoland", 1, ""), ("Kasai", "Congoland", 1, ""), ("Katanga Nord", "Congoland", 1, ""),
          ("Katanga Sud", "Congoland", 1, ""), ("Ituri", "Congoland", 1, ""), ("Kivu", "Congoland", 1, ""), ("Kivou", "Congoland", 0, "Kivu"),
          ("Alpha", "Omanland", 1, ""), ("Beta", "Omanland", 1, ""), ("Gamma", "Omanland", 1, ""), ("Delta", "Omanland", 1, "")] + [
          (name, "Nameland", 1, "") for name in ("Alfa", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India")] + [
          ("Joined", "Splitland", 1, ""), ("West", "Splitland", 1, ""), ("Rest", "Splitland", 1, ""), ("Plain", "Splitland", 1, ""),
          ("Mainland", "Isleland", 1, "")]

# The rows of a mapping file for the test world (the file of the repository is tested apart)
TEST_MAP = [("Madagascar", "ADM1", "Diana", "Antsiranana"), ("Madagascar", "ADM1", "Atsimo-Andrefana", "Toliara"), ("Madagascar", "ADM1", "Anosy", "Toliara"),
            ("Madagascar", "ADM1", "Analamanga", "Antananarivo"), ("Mexico", "ADM1", "Coahuila de Zaragoza", "Coahuila"),
            ("United States", "ADM1", "District of Columbia", "Washington, D.C.")]

# The state files of geoBoundaries: ISO code and level -> [(shapeName, square)]
FILES = {
    ("MEX", "ADM1"): [("Sonora", square(-110, 20, -105, 30)), ("Estado de Chihuahua", square(-105, 25, -100, 30)),
                      ("Coahuila de Zaragoza", square(-105, 20, -100, 25))],
    ("USA", "ADM1"): [("Arizona", square(-110, 30, -105, 40)), ("New Mexico", square(-105, 30, -100, 40)), ("Hawaii", square(-160, 21, -159, 22))],
    # Madagascar: the file has regions, AntWeb provinces. Analamanga lies in Antananarivo, which this AntWeb does not have.
    ("MDG", "ADM1"): [("Diana", square(43, -19, 51, -12)), ("Atsimo-Andrefana", square(43, -26, 47, -19)), ("Anosy", square(47, -26, 51, -22)),
                      ("Analamanga", square(47, -22, 51, -19))],
    ("MDG", "ADM2"): [("Antsiranana I", square(49, -13, 50, -12)), ("Toliara-I", square(43, -24, 44, -23))],
    # Reunion: nothing at ADM1 and ADM2, arrondissements at ADM3, the communes AntWeb has as states at ADM4
    ("REU", "ADM3"): [("Arrondissement du Vent", square(55, -21.5, 55.5, -20.5)), ("Arrondissement Sous-le-Vent", square(55.5, -21.5, 56, -20.5))],
    ("REU", "ADM4"): [("Saint-Denis", square(55, -21.5, 55.4, -20.5)), ("Saint-Pierre", square(55.4, -21.5, 55.8, -20.5)), ("Le Port", square(55.8, -21.5, 56, -20.5))],
    # Andesia (as Chile): the regions' names were spoilt when the file was made; communes have the names of the regions
    ("ADS", "ADM1"): [("Regi\u00c3\u00b3n de Norte", square(-75, -30, -70, -20)), ("Regi\u00c3\u00b3n del Sur", square(-75, -40, -70, -30))],
    ("ADS", "ADM2"): [("Sur", square(-75, -25, -70, -20)), ("Norte", square(-75, -30, -70, -25)), ("Valle", square(-75, -35, -70, -30)), ("Lago", square(-75, -40, -70, -35))],
    # Iberia (as Spain): the communities under their own names, AntWeb's in English; one province has the name of a community
    ("IBR", "ADM1"): [("Andaluc\u00eda", square(-9, 36, -3, 40)), ("Catalu\u00f1a/Catalunya", square(-3, 40, 3, 44)), ("Comunidad de Madrid", square(-9, 40, -3, 44)),
                      ("Comunidad Foral de Navarra", square(-3, 38, 3, 40)), ("Islas", square(-3, 36, 3, 38))],
    ("IBR", "ADM2"): [("Madrid", square(-9, 36, -6, 40)), ("Sevilla", square(-6, 36, -3, 40)), ("Girona", square(-3, 40, 3, 44)), ("Lugo", square(-9, 40, -3, 44)),
                      ("Soria", square(-3, 38, 3, 40)), ("Palma", square(-3, 36, 3, 38))],
    # Congoland: for the test with specimens (SPECIMENS below says what AntWeb has filed where)
    ("CGL", "ADM1"): [("Lower Uele", square(20, -2, 22, 0)), ("Equateur", square(22, -2, 24, 0)), ("Kasai", square(24, -2, 26, 0)), ("Sankuru", square(26, -2, 28, 0)),
                      ("Katanga", square(20, -4, 24, -2)), ("Ituri Est", square(24, -4, 26, -2)), ("Ituri Ouest", square(26, -4, 28, -2)), ("Kivu", square(28, -4, 30, 0)),
                      ("Ile Nouvelle", square(28, -6, 30, -4)), ("Vide", square(20, -6, 28, -4))],
    # Omanland: AntWeb's four states are the second level; the first has two shapes that hold two states each
    ("OML", "ADM1"): [("North", square(50, 20, 54, 24)), ("South", square(50, 16, 54, 20))],
    ("OML", "ADM2"): [("N1", square(50, 20, 52, 24)), ("N2", square(52, 20, 54, 24)), ("S1", square(50, 16, 52, 20)), ("S2", square(52, 16, 54, 20))],
    # Nameland: for the rule on names and specimens. Fourteen shapes of one degree, seven in a row, two rows (NAME_SPECIMENS below)
    ("NML", "ADM1"): [(name, square(60 + i % 7, i // 7, 61 + i % 7, 1 + i // 7)) for i, name in enumerate(
        ["Alfa", "Bravo", "Zulu", "Charlie Province", "Yankee", "Delta Region", "Xray", "Echo", "Echo", "Foxtrot", "Foxtrot", "Whisky", "Victor", "Uniform"])],
    # Splitland: the shape Joined is two states today, told apart by the districts of the second level; the shape Rest has lost one district
    ("SPL", "ADM1"): [("Joined", square(70, 0, 73, 2)), ("Rest", square(73, 0, 75, 2)), ("Plain", square(75, 0, 76, 2))],
    ("SPL", "ADM2"): [("East District", square(72, 0, 73, 2)), ("West-District", square(70, 0, 71, 2)), ("Middle", square(71, 0, 72, 2)), ("Lost", square(73, 0, 74, 2)),
                      ("Kept", square(74, 0, 75, 2)), ("Flat", square(75, 0, 76, 2))],
    # Isleland: the country's shape is the mainland alone. The states' file draws an islet of the state Mainland 17 km off the coast, and two
    # islets that are a state AntWeb does not have yet (a list of squares: a shape of several parts)
    ("ILE", "ADM1"): [("Mainland", [square(80, 0, 82, 2), square(82.1, 0.5, 82.2, 0.6)]), ("Outer", [square(82.1, 1.5, 82.3, 1.7), square(82.5, 0.9, 82.6, 1.0)])],
}

# Nameland, AntWeb's specimens: (stored state, the number of the shape they lie in, specimens)
NAME_SPECIMENS = [("Golf", 0, 6),                        # six of Golf in the shape Alfa: the Ihorombe case
                  ("Bravo", 1, 3), ("Bravo", 2, 27),     # Bravo's specimens lie in the shape Zulu, three in the shape Bravo
                  ("Charlie", 3, 2), ("Charlie", 4, 23), # 23 of Charlie's 25 lie in Yankee: evidence against the looser name Charlie Province
                  ("Delta", 5, 1), ("Delta", 6, 7),      # 7 of Delta's 8 lie in Xray: too few against the name Delta Region
                  ("Echo", 8, 10),                       # two shapes are called Echo; Echo's specimens lie in the second
                  ("Foxtrot", 9, 3),                     # two shapes are called Foxtrot; three specimens say nothing
                  ("Hotel", 12, 22)]                     # the mapping file says Whisky is Hotel; Hotel's 22 specimens lie in Victor
NAME_LINES = [("Nameland", "ADM1", "Whisky", "Hotel"), ("Nameland", "ADM1", "Uniform", "India"), ("Nameland", "ADM1", "Victor", "No Such State")]
SPLIT_LINES = [("Splitland", "Joined", "ADM2", "East District", "East", "Law 1 of 2021"), ("Splitland", "Joined", "ADM2", "West-District", "West", "Law 1 of 2021"),
               ("Splitland", "Rest", "ADM2", "Lost", "Newland", "Law 2 of 2023"), ("Splitland", "Rest", "ADM2", "*", "Rest", "every other district")]

# AntWeb's own specimens with a state and coordinates: (country, stored state, latitude, longitude, specimens)
SPECIMENS = [
    ("Congoland", "Bas-Uele", -1.0, 21.0, 40), ("Congoland", "Bas-Uele", -1.0, 23.0, 2),      # 40 of 42 in the shape Lower Uele: the names differ
    ("Congoland", "Equateur", -1.5, 23.0, 3),                                                # 3 in the shape of its name: too few to test
    ("Congoland", "Kasai", -1.0, 27.0, 18), ("Congoland", "Kasai", -1.0, 25.0, 2),            # 18 of 20 in the shape Sankuru, 2 in the shape Kasai
    ("Congoland", "Katanga Nord", -2.5, 21.0, 30), ("Congoland", "Katanga Sud", -3.5, 23.0, 20),   # both in the one shape Katanga
    ("Congoland", "Ituri", -3.0, 25.0, 12), ("Congoland", "Ituri", -3.0, 27.0, 10), ("Congoland", "Ituri", -5.0, 29.0, 3),   # over two shapes, 3 in Ile Nouvelle
    ("Congoland", "Kivou", -2.0, 29.0, 8), ("Congoland", "Kivu", -0.5, 21.5, 4),              # 8 of 12 in the shape of its name (an old name counts), 4 in Lower Uele
    ("Congoland", "Nowhere", -1.0, 21.0, 9), ("Congoland", "Kivu", 0.0, 0.0, 5), ("Atlantis", "Kivu", -1.0, 21.0, 7),   # not used: state, coordinates, country
    ("Omanland", "Alpha", 22.0, 51.0, 6), ("Omanland", "Beta", 22.0, 53.0, 6), ("Omanland", "Gamma", 18.0, 51.0, 6), ("Omanland", "Delta", 18.0, 53.0, 6),
]


def make_source(folder):
    for sub in ("raw", "media"):
        os.makedirs(os.path.join(folder, sub))
    for (iso, level), shapes in FILES.items():
        data = json.dumps({"type": "FeatureCollection", "features": [
            {"type": "Feature", "properties": {"shapeName": name, "shapeGroup": iso, "shapeType": level},
             "geometry": {"type": "MultiPolygon", "coordinates": [[part] for part in ring]} if isinstance(ring[0][0], list) else {"type": "Polygon", "coordinates": [ring]}}
            for name, ring in shapes]}).encode("utf-8")
        meta = json.dumps({"boundaryLicense": "Open Data Commons Open Database License 1.0" if iso == "MDG" else "Public Domain", "boundarySource": "a test"}).encode("utf-8")
        for name, content in ((iso + "-" + level + "_simplified.geojson", data), (iso + "-" + level + "-metaData.json", meta)):
            with open(os.path.join(folder, "media", name), "wb") as f:
                f.write(content)
            with open(os.path.join(folder, "raw", name), "wb") as f:
                f.write(("version https://git-lfs.github.com/spec/v1\noid sha256:" + hashlib.sha256(content).hexdigest() + "\nsize " + str(len(content)) + "\n").encode("ascii"))


_made = {}


def setup():
    """(Corrections, StateShapes) over the test world; made once."""
    if "work" not in _made:
        folder = _made["folder"] = tempfile.mkdtemp()
        make_source(folder)
        known = countries()
        shapes = cc.read_shapes(world())
        cc.map_shapes(shapes, known)
        checker = cc.Checker(known, shapes, cc.Index(shapes), 1.0)
        store = cc.Detail(os.path.join(folder, "store"), commit="c" * 40, pointer_url="file://" + folder + "/raw/{iso}-{level}{ext}",
                          data_url="file://" + folder + "/media/{iso}-{level}{ext}")
        states = pc.States(STATES, known)
        state_shapes = pc.StateShapes(store, states, known, TEST_MAP)
        _made["work"] = pc.Corrections(checker, states, state_shapes)
        _made["store"] = store
    return _made["work"]


def row(code, country, lat="", lon="", adm1="", island="", event="", stated="", locality="", group="1", adm2="", notes="", bioregion="", locality_code=""):
    return pc.Row(code, event, locality_code, country, adm1, adm2, island, str(lat), str(lon), stated, locality, notes, bioregion, group)


def w(country, lat="", lon="", **more):
    return setup().work(row("casent0000001", country, lat, lon, **more))


def test_names():
    assert pc.plain("Amoron'i  Mania") == "amoron i mania" and pc.plain("Alaotra-Mangoro") == pc.plain("alaotra mangoro") and pc.plain("Querétaro") == "queretaro"
    assert pc.loose("Fianarantsoa Province") == "fianarantsoa" and pc.loose("Estado de Chihuahua") == "chihuahua" and pc.loose("Province") == ""
    states = pc.States(STATES, countries())
    assert states.of("Mexico") == ["Chihuahua", "Coahuila", "Sonora"] and states.of("Tinyland") == [] and states.of("Nowhere") == []
    assert states.find("Mexico", "SONORA") == ("Sonora", "name")
    assert states.find("Mexico", "Chih.") == ("Chihuahua", "name")                         # an old name is a spelling of its valid state
    assert states.find("Madagascar", "Tuléar") == ("Toliara", "name")
    assert states.find("Mexico", "Estado de Chihuahua") == ("Chihuahua", "name without the unit word")
    assert states.find("Mexico", "Arizona") == (None, "") and states.find("United States", "Sonora") == (None, "")
    assert states.find("USA", "Arizona") == (None, "")                                     # asked by the valid country name only


def test_level_is_chosen_by_how_the_names_fit():
    work = setup()
    shapes = work.shapes
    # Reunion: no ADM1 and ADM2 file, ADM3 fits no AntWeb state, ADM4 fits both
    chosen = shapes.choose("Reunion", "REU")
    assert chosen.level == "ADM4" and chosen.names == ["Saint-Denis", "Saint-Pierre", "Le Port"] and chosen.states == ["Saint-Denis", "Saint-Pierre", None]
    t = shapes.tables["Reunion"]
    assert [pc.level_text(x) for x in t["tried"]] == ["ADM1 no file", "ADM2 no file", "ADM3 0/2 AntWeb states, 0/2 shapes", "ADM4 2/2 AntWeb states, 2/3 shapes"]
    assert t["usable"] and t["why"] == "at least half of AntWeb's states are matched and at least half of the shapes are AntWeb states"
    assert t["matched"] == ["Saint-Denis", "Saint-Pierre"] and t["not_matched"] == [] and t["shapes_not_states"] == ["Le Port"] and t["level"] == "ADM4"
    # Mexico: ADM1 fits all three (one by its name, one without "Estado de", one by the mapping file): the search ends there
    chosen = shapes.choose("Mexico", "MEX")
    assert chosen.level == "ADM1" and chosen.states == ["Sonora", "Chihuahua", "Coahuila"]
    assert chosen.hows == ["name", "name without the unit word", "mapping file"]
    assert pc.level_line("Mexico", shapes.tables["Mexico"]) == ("  Mexico (MEX): ADM1 3/3 AntWeb states, 3/3 shapes; ADM2 to ADM4 not looked at | used: ADM1 "
                                                                "(at least half of AntWeb's states are matched and at least half of the shapes are AntWeb states)")
    assert shapes.tables["Mexico"]["other_way"] == ["Coahuila de Zaragoza -> Coahuila (mapping file)", "Estado de Chihuahua -> Chihuahua (name without the unit word)"]
    # Madagascar: by the mapping file ADM1 (regions) fits both provinces; ADM2 is not looked at
    chosen = shapes.choose("Madagascar", "MDG")
    assert chosen.level == "ADM1" and chosen.states == ["Antsiranana", "Toliara", "Toliara", None]
    assert shapes.tables["Madagascar"]["shapes_not_states"] == ["Analamanga"] and shapes.tables["Madagascar"]["licence"].startswith("Open Data Commons")
    # the lock file has the light file of each level that was fetched
    with open(_made["store"].lock_path, encoding="utf-8") as f:
        lock = json.load(f)["files"]
    assert lock["REU ADM1_simplified"] == {"missing": True, "checked": lock["REU ADM1_simplified"]["checked"]}
    assert lock["REU ADM4_simplified"]["file"] == "geoBoundaries-REU-ADM4_simplified-cccccccccccc.geojson" and len(lock["MEX ADM1_simplified"]["sha256"]) == 64
    assert "MDG ADM2_simplified" not in lock
    # no ISO code, no file
    assert shapes.choose("Nowhere", "") is None and "no ISO code" in shapes.problems["Nowhere"]
    assert shapes.choose("Atoll", "ATL") is None and shapes.problems["Atoll"] == "geoBoundaries has no file of the divisions of ATL"


def test_mapping_file_of_the_repository():
    rows = pc.read_map(pc.MAP_FILE)
    assert all(len(r) == 4 and r[1] in pc.LEVELS for r in rows) and ("country", "level", "shape", "antweb_state") not in rows and len(rows) == 18
    with open(pc.MAP_FILE, "rb") as f:
        assert b"\r" not in f.read()
    assert ("Mexico", "ADM1", "Coahuila de Zaragoza", "Coahuila") in rows and ("United States", "ADM1", "District of Columbia", "Washington, D.C.") in rows
    rules = pc.read_rules(pc.MAP_FILE)
    # Madagascar: the regions are AntWeb's states now. One shape has another name; the provinces are converted; each region has its province
    assert [r for r in rows if r[0] == "Madagascar"] == [("Madagascar", "ADM1", "Matsiatra Ambony", "Haute Matsiatra"), ("Madagascar", "ADM1", "Vatovavy", "Vatovavy"),
                                                        ("Madagascar", "ADM1", "Fitovinany", "Fitovinany"), ("Madagascar", "ADM1", "Ambatosoa", "Ambatosoa")]
    # Madagascar's shapes are OCHA's (24 regions, the three new ones shapes of their own: rows marked new until AntWeb has them)
    assert rules.sources == {"Madagascar": "ocha"} and {r for r in rules.new if r[0] == "Madagascar"} == {
        ("Madagascar", "ADM1", "Vatovavy", "Vatovavy"), ("Madagascar", "ADM1", "Fitovinany", "Fitovinany"), ("Madagascar", "ADM1", "Ambatosoa", "Ambatosoa")}
    assert rules.convert == {"Madagascar": ["Antananarivo", "Antsiranana", "Fianarantsoa", "Mahajanga", "Toamasina", "Toliara"]}
    within = rules.within["Madagascar"]
    # AntWeb's 22 regions (production, 2026-11-10) and the 22 shape names of the file geoBoundaries-MDG-ADM1 (read 2026-11-10)
    antweb = ["Alaotra Mangoro", "Amoron'i Mania", "Analamanga", "Analanjirofo", "Androy", "Anosy", "Atsimo-Andrefana", "Atsimo-Atsinanana", "Atsinanana", "Betsiboka",
              "Boeny", "Bongolava", "Diana", "Haute Matsiatra", "Ihorombe", "Itasy", "Melaky", "Menabe", "Sava", "Sofia", "Vakinankaratra", "Vatovavy Fitovinany"]
    shapes = ["Diana", "Sava", "Analanjirofo", "Amoron'i Mania", "Ihorombe", "Melaky", "Menabe", "Vakinankaratra", "Atsinanana", "Alaotra-Mangoro", "Sofia",
              "Anosy", "Boeny", "Betsiboka", "Analamanga", "Bongolava", "Itasy", "Atsimo-Andrefana", "Androy", "Atsimo-Atsinanana", "Matsiatra Ambony",
              "Vatovavy-Fitovinany"]
    # ... and the three regions made since: Vatovavy and Fitovinany (2021), Ambatosoa (2023)
    assert len(within) == 25 and sorted(within) == sorted(pc.plain(x) for x in antweb + ["Vatovavy", "Fitovinany", "Ambatosoa"])
    count = lambda province: sum(1 for x in within.values() if x == province)
    assert [count(x) for x in ("Antananarivo", "Antsiranana", "Fianarantsoa", "Mahajanga", "Toamasina", "Toliara")] == [4, 2, 7, 4, 4, 4]
    assert within["vatovavy"] == within["fitovinany"] == "Fianarantsoa" and within["ambatosoa"] == "Toamasina"
    assert within["diana"] == "Antsiranana" and within["atsimo andrefana"] == "Toliara" and within["haute matsiatra"] == "Fianarantsoa" and within["sofia"] == "Mahajanga"
    # with AntWeb's regions and provinces, each of the 22 shapes is its region: 21 by the name (a hyphen makes no difference), one by the row
    known = cc.Countries([cc.Country("Madagascar", 1, "", 0, "")])
    states = pc.States([(x, "Madagascar", 1, "") for x in antweb + rules.convert["Madagascar"]], known)
    both = pc.StateShapes(_made.get("store") or (setup() and _made["store"]), states, known, rows, None, rules)
    found, hows = both.match("Madagascar", "ADM1", shapes)
    assert sorted(found) == antweb and found[shapes.index("Matsiatra Ambony")] == "Haute Matsiatra" and hows.count("mapping file") == 1
    # three shapes are split by the districts of the file MDG ADM2 (the names as that file spells them, read 2026-11-10); the others are their names
    assert hows.count("name") == 18 and [shapes[i] for i, h in enumerate(hows) if h == pc.SPLIT] == ["Analanjirofo", "Atsimo-Atsinanana", "Vatovavy-Fitovinany"]
    split = both.splits["Madagascar"]
    assert split["vatovavy fitovinany"]["level"] == "ADM2" and split["vatovavy fitovinany"]["rest"] is None
    assert split["vatovavy fitovinany"]["units"] == {"ifanadiana": "Vatovavy", "mananjary": "Vatovavy", "nosy varika": "Vatovavy", "ikongo": "Fitovinany",
                                                     "manakara atsimo": "Fitovinany", "vohipeno": "Fitovinany"}
    assert split["analanjirofo"]["units"] == {"maroantsetra": "Ambatosoa", "mananara avaratra": "Ambatosoa"} and split["analanjirofo"]["rest"] == "Analanjirofo"
    assert split["atsimo atsinanana"]["units"] == {"vohipeno": "Fitovinany"} and split["atsimo atsinanana"]["rest"] == "Atsimo-Atsinanana"
    assert all(note for _, _, _, _, _, note in rules.splits) and "Loi 2021-012 of 24 June 2021" in rules.splits[0][5] and "Loi 2023-012 of 29 June 2023" in rules.splits[6][5]
    assert both.split_targets("Madagascar", shapes, hows, True) == ["Ambatosoa", "Fitovinany", "Vatovavy"]
    assert both.wanted("Madagascar") == antweb and both.convert["Madagascar"] == set(rules.convert["Madagascar"])
    assert both.within["Madagascar"]["vatovavy fitovinany"] == "Fianarantsoa"
    # Seychelles: La Digue is Inner Islands; Other Islands is a state to add. The rows are given for the two levels that have these shapes
    assert [r for r in rows if r[0] == "Seychelles"] == [("Seychelles", "ADM2", "La Digue", "Inner Islands"), ("Seychelles", "ADM3", "La Digue", "Inner Islands"),
                                                        ("Seychelles", "ADM2", "Other Islands", "Outer Islands"), ("Seychelles", "ADM3", "Other Islands", "Outer Islands")]
    assert {r for r in rules.new if r[0] == "Seychelles"} == {("Seychelles", "ADM2", "Other Islands", "Outer Islands"), ("Seychelles", "ADM3", "Other Islands", "Outer Islands")}
    # the name lines: 148 pairs the lead checked by the names, 99 that are plain from the names; no shape twice; none is a hard row as well
    assert len(rules.names) == 247 and len({(c, l, pc.plain(x)) for c, l, x, _ in rules.names}) == 247 and all(l in pc.LEVELS and x and a for _, l, x, a in rules.names)
    assert ("United Arab Emirates", "ADM1", "Ras al-Khaimah", "Ra's al Khaymah") in rules.names and ("Czech Republic", "ADM1", "Jiho\u010desk\u00fd kraj", "Jiho\u010desk\u00fd") in rules.names
    assert ("Democratic Republic of Congo", "ADM1", "Lower Uele", "Bas-U\u00e9l\u00e9") in rules.names and ("Macedonia", "ADM2", "Shtip", "\u0160tip") in rules.names
    assert not {(c, l, pc.plain(x)) for c, l, x, _ in rules.names} & {(c, l, pc.plain(x)) for c, l, x, _ in rows}
    # Nicaragua: the two Caribbean regions under the names of geoBoundaries and of OCHA's file; the department Jinotega by a row
    assert ("Nicaragua", "ADM1", "Regi\u00f3n Aut\u00f3noma Costa Caribe Norte", "Costa Caribe Norte") in rules.names and ("Nicaragua", "ADM1", "Jinotega (Departamento)", "Jinotega") in rows
    assert ("Nicaragua", "ADM1", "North Carribean Coast Autonomous Region", "Costa Caribe Norte") in rules.names


def test_mapping_rows():
    # a row is used only when AntWeb has the state: Analamanga's province is not in this AntWeb
    assert w("Madagascar", -20.0, 49.0) == [pc.Not("adm1", pc.NOT_A_STATE, "Analamanga", "Madagascar")]
    # a region of a province AntWeb has
    assert w("Madagascar", -15.0, 47.0) == [pc.Proposal("adm1", "", "Antsiranana", "coordinates inside Diana (Antsiranana by the mapping file), geoBoundaries MDG ADM1", "Madagascar")]
    # two regions of one province are one state: next to their border the proposal stands
    assert w("Madagascar", -24.0, 46.999)[0].proposed == "Toliara"
    assert ("Madagascar", "ADM1", "Diana", "Antsiranana") in setup().shapes.rows_used
    # a file of the three kinds of line
    with tempfile.TemporaryDirectory() as folder:
        path = os.path.join(folder, "map.csv")
        with open(path, "w", encoding="utf-8") as f:
            f.write("# a note\ncountry,level,shape,antweb_state\nA,ADM1,S,T\nA,adm2,S2,T2,new\nA,ADM1,S3,T3,0.91,12,a pasted line\n"
                    "convert,A,P1,P2\nwithin,A,T,P1\nwithin,A,T-2,P2\nname,A,adm1,S4,T4,the same in French\nname,A,NE,S5,T5\n"
                    "split,A,S6,ADM2,U1,T6,\"Law 1, of 2021\"\nsplit,A,S6,adm2,*,T7\nsplit,A,S6,XX,U2,T8\nsource,A,OCHA\nsource,B,somewhere\n\n")
        assert pc.read_map(path) == [("A", "ADM1", "S", "T"), ("A", "adm2", "S2", "T2"), ("A", "ADM1", "S3", "T3")]
        rules = pc.read_rules(path)
        assert rules.new == {("A", "adm2", "S2", "T2")} and rules.convert == {"A": ["P1", "P2"]} and rules.within == {"A": {"t": "P1", "t 2": "P2"}}
        assert rules.names == [("A", "ADM1", "S4", "T4"), ("A", "NE", "S5", "T5")]
        assert rules.splits == [("A", "S6", "ADM2", "U1", "T6", "Law 1, of 2021"), ("A", "S6", "ADM2", "*", "T7", "")] and rules.sources == {"A": "ocha"}
        assert pc.read_map(os.path.join(folder, "none.csv")) == [] and pc.read_rules(os.path.join(folder, "none.csv")).convert == {}


def test_inside_one_state():
    assert w("Mexico", 25.0, -108.0) == [pc.Proposal("adm1", "", "Sonora", "coordinates inside Sonora, geoBoundaries MEX ADM1", "Mexico")]
    assert w("USA", 35.0, -108.0) == [pc.Proposal("adm1", "", "Arizona", "coordinates inside Arizona, geoBoundaries USA ADM1", "United States")]
    # a record that has a state is not touched
    assert w("Mexico", 25.0, -108.0, adm1="Sonora") == []


def test_inside_one_state_is_a_proposal_whatever_is_near():
    # 0.5 km from the border of Sonora and Chihuahua, inside Sonora: Sonora, the neighbour named and flagged
    r = w("Mexico", 27.0, -105.005)
    assert r == [pc.Proposal("adm1", "", "Sonora", "coordinates inside Sonora, geoBoundaries MEX ADM1", "Mexico", "Chihuahua", "0.5", "within the allowed 1 km of Chihuahua")], r
    # 2 km from it: the neighbour and its distance, no flag; with a stated error of 3 km the flag says so
    r = w("Mexico", 27.0, -105.02)
    assert (r[0].proposed, r[0].other, r[0].other_km, r[0].flag) == ("Sonora", "Chihuahua", "1.98", ""), r
    r = w("Mexico", 27.0, -105.02, stated="3 km")
    assert (r[0].proposed, r[0].other, r[0].other_km, r[0].flag) == ("Sonora", "Chihuahua", "1.98", "within the stated error (3 km) of Chihuahua"), r
    # 20 km from it: still named (looked for up to 25 km); 100 km from it: no other state near
    r = w("Mexico", 27.0, -105.2)
    assert (r[0].other, r[0].other_km, r[0].flag) == ("Chihuahua", "20", ""), r
    assert w("Mexico", 27.0, -106.0) == [pc.Proposal("adm1", "", "Sonora", "coordinates inside Sonora, geoBoundaries MEX ADM1", "Mexico")]
    # the other side of the same border
    r = w("Mexico", 27.0, -104.995)
    assert (r[0].proposed, r[0].other, r[0].flag) == ("Chihuahua", "Sonora", "within the allowed 1 km of Sonora"), r
    # next to a shape that is no AntWeb state: the proposal stands, the shape named as what it is; inside that shape: no proposal
    r = w("Madagascar", -22.005, 49.0)
    assert (r[0].proposed, r[0].other, r[0].other_km, r[0].flag) == ("Toliara", "Analamanga (not a state in AntWeb)", "0.56",
                                                                    "within the allowed 1 km of Analamanga (not a state in AntWeb)"), r
    assert w("Madagascar", -21.995, 49.0) == [pc.Not("adm1", pc.NOT_A_STATE, "Analamanga", "Madagascar")]
    # two regions of one province are one state: next to their border no other state is near
    r = w("Madagascar", -24.0, 46.999)
    assert (r[0].proposed, r[0].other, r[0].flag) == ("Toliara", "", ""), r


def test_two_candidates_only_outside_every_shape():
    # in the water, 0.44 km south of the coast where Sonora and Coahuila meet: within 1 km of both, no proposal
    assert w("Mexico", 19.996, -105.001) == [pc.Not("adm1", pc.TWO, "Sonora or Coahuila", "Mexico")]
    # 5 km west of there: in the water off Sonora alone; Coahuila is named as the nearest other state
    r = w("Mexico", 19.996, -105.05)
    assert (r[0].proposed, r[0].basis, r[0].other, r[0].flag) == ("Sonora", "in the water, 0.44 km from Sonora, geoBoundaries MEX ADM1", "Coahuila", ""), r
    assert r[0].other_km == "5.24", r
    # two shapes of a file that both hold the point: no proposal; a gap between two shapes: two candidates; else as inside
    shapes = setup().shapes
    chosen = pc.Chosen("X", "XXX", "ADM1", ["A", "B", "C"], ["A", "B", "C"], ["name", "name", "name"],
                       cc.index_of_shapes([[square(0, 0, 2, 2)], [square(1, 0, 3, 2)], [square(3.01, 0, 5, 2)]]))
    at = shapes.state_at(chosen, 1.0, 1.5, 1.0, 1.0)
    assert (at.state, at.reason, at.detail) == (None, pc.TWO, "inside both A and B: the shapes of the file overlap"), at
    at = shapes.state_at(chosen, 1.0, 3.005, 1.0, 1.0)
    assert (at.state, at.reason, at.detail) == (None, pc.TWO, "B or C"), at
    at = shapes.state_at(chosen, 1.0, 0.95, 1.0, 1.0)
    assert (at.state, at.shape, at.other, at.other_km, at.flag) == ("A", "A", "B", "5.56", ""), at
    at = shapes.state_at(chosen, 1.0, 0.5, 1.0, 1.0)
    assert (at.state, at.other, at.other_km) == ("A", "", ""), at
    at = shapes.state_at(chosen, 1.0, 9.0, 1.0, 1.0)
    assert (at.state, at.reason) == (None, pc.NO_SHAPE_HERE), at


def test_the_flag_in_the_lists():
    work = setup()
    lists = pc.Lists({"1": "California Academy of Sciences"})
    records = [row("casent0000051", "Mexico", 27.0, -105.005, event="BLF600", stated="1 minute"),
               row("casent0000052", "Mexico", 27.0, -105.005, event="BLF600", stated="1 minute"),
               row("casent0000053", "Mexico", 27.0, -106.0, event="BLF600"),
               row("casent0000054", "Mexico", 27.0, -105.005, event="BLF700"),
               row("casent0000055", "Mexico", 27.0, -106.0, event="BLF800")]
    for record in records:
        lists.add(record, work.work(record), work.kind(record))
    c = lists.corrections()
    assert pc.CORRECTIONS_HEADER[6:11] == ["basis", "nearest_other_state", "km_to_other_state", "other_state_flag", "note"]
    assert [x[7:10] for x in c] == [["Chihuahua", "0.5", "within the stated error (1.85 km) of Chihuahua"]] * 2 + [["", "", ""]] + [
        ["Chihuahua", "0.5", "within the allowed 1 km of Chihuahua"], ["", "", ""]], c
    events = {e[0]: e for e in lists.events()}
    assert pc.EVENTS_HEADER[10:15] == ["flag", "nearest_other_state", "km_to_other_state", "other_state_flag", "note"]
    assert events["BLF600"][10:14] == ["2 different coordinates in this collecting event", "Chihuahua", "0.5",
                                       "2 of 3 specimens: within the stated error (1.85 km) of Chihuahua"], events["BLF600"]
    assert events["BLF700"][11:14] == ["Chihuahua", "0.5", "within the allowed 1 km of Chihuahua"] and events["BLF800"][11:14] == ["", "", ""]
    text = pc.report_text("x", "y", "every group", lists, work.shapes, _made["store"], "1 second", 1.0)
    assert ("State (adm1): 5 specimens with a proposal (corrections.csv), in 3 lines of by_collecting_event.csv\n"
            "  For information: 3 of them (2 lines) lie within the stated error, or the allowed 1 km, of another state (column other_state_flag).\n") in text, text


def test_in_the_water_near_one_state():
    # in no state shape, within the allowed distance of one state alone: that state, and the note says how far outside the shape
    r = w("Mexico", 25.0, -110.005)
    assert r == [pc.Proposal("adm1", "", "Sonora", "in the water, 0.5 km from Sonora, geoBoundaries MEX ADM1", "Mexico", "", "", "", "outside the shape, 0.5 km from it (coast)")], r
    # 2 km out: outside the stated country by more than 1 km, no proposal
    r = w("Mexico", 25.0, -110.02)
    assert r == [pc.Not("adm1", pc.OUTSIDE, "in the water, 2.02 km from Mexico")], r
    # with a stated error of 3 km the same point is within the allowed distance
    r = w("Mexico", 25.0, -110.02, stated="3 km")
    assert (r[0].proposed, r[0].note) == ("Sonora", "outside the shape, 2.02 km from it (coast)"), r
    # in no state shape of Mexico, 0.44 km from Sonora, and inside another country: no proposal, the country named
    r = w("Mexico", 30.004, -108.0)
    assert r == [pc.Not("adm1", pc.ANOTHER_COUNTRY, "United States, the point lies in it; 0.44 km from Sonora", "Mexico")], r
    # in the water off Sonora with a stated error that reaches another country: no proposal either
    r = w("Mexico", 29.99, -110.005, stated="2 km")
    assert r == [pc.Not("adm1", pc.ANOTHER_COUNTRY, "United States, 1.21 km; 0.48 km from Sonora", "Mexico")], r
    assert w("Mexico", 29.99, -110.005)[0].proposed == "Sonora"                              # with the 1 km of the rule the other country is too far


def test_outside_the_stated_country():
    r = w("Mexico", 35.0, -108.0)
    assert r == [pc.Not("adm1", pc.OUTSIDE, "United States, 556 km from Mexico")], r


def test_island_country_specimen_is_left_out():
    work = setup()
    record = row("casent0000002", "United States", 21.5, -159.5, island="Hawaii")
    assert work.work(record) == [] and work.kind(record) == "state missing, island-country specimen (left out)"
    assert work.kind(row("x", "Mexico")) == "state missing" and work.kind(row("x", "")) == "country missing"
    assert work.kind(row("x", "Africa")) == "country not valid in AntWeb"


HAWAII = "AntWeb files this under United States as the island Hawaii"


def without_hawaii_as_a_state():
    """The test world where AntWeb has Hawaii as an island country only, not as a state of the United States too."""
    work = setup()
    states = pc.States([s for s in STATES if s[0] != "Hawaii"], work.countries)
    return pc.Corrections(work.checker, states, pc.StateShapes(_made["store"], states, work.countries, TEST_MAP))


def test_state_shape_that_is_an_island_country():
    # Brian Fisher, 2026-11-10: AntWeb reads Hawaii as the state of a specimen of the United States and stores it as
    # the island country; so the state to enter in TaxonWorks is Hawaii.
    for work in (setup(), without_hawaii_as_a_state()):
        r = work.work(row("casent0000003", "United States", 21.5, -159.5))
        assert r == [pc.Proposal("adm1", "", "Hawaii", "coordinates inside Hawaii, geoBoundaries USA ADM1; " + HAWAII, "United States")], r
        r = work.work(row("casent0000003", "USA", 21.5, -160.005))                         # an old name of the country, in the water
        assert r == [pc.Proposal("adm1", "", "Hawaii", "in the water, 0.52 km from Hawaii, geoBoundaries USA ADM1; " + HAWAII, "United States", "", "", "",
                                 "outside the shape, 0.52 km from it (coast)")], r
        r = work.work(row("casent0000003", "United States", 35.0, -108.0))                 # a state of the mainland: no word of islands
        assert r == [pc.Proposal("adm1", "", "Arizona", "coordinates inside Arizona, geoBoundaries USA ADM1", "United States")], r
        t = work.shapes.tables["United States"]
        assert t["islands"] == ["Hawaii"] and t["shapes_not_states"] == [] and t["other_way"] == [] and t["level"] == "ADM1"
    # the island shape is counted apart from the states matched; it is a state matched too only when AntWeb has it as one
    t = setup().shapes.tables["United States"]
    assert [pc.level_text(x) for x in t["tried"]] == ["ADM1 3/3 AntWeb states, 3/3 shapes, 1 of them an island country of AntWeb"], t["tried"]
    assert t["matched"] == ["Arizona", "Hawaii", "New Mexico"] and t["not_matched"] == []
    other = without_hawaii_as_a_state()
    other.work(row("casent0000003", "United States", 21.5, -159.5))
    t = other.shapes.tables["United States"]
    assert [pc.level_text(x) for x in t["tried"]] == ["ADM1 2/2 AntWeb states, 3/3 shapes, 1 of them an island country of AntWeb"], t["tried"]
    assert t["matched"] == ["Arizona", "New Mexico"] and t["not_matched"] == []
    # only a valid island country whose parent is the specimen's country
    shapes = setup().shapes
    assert shapes.island("United States", "HAWAII") == "Hawaii" and shapes.island("Mexico", "Hawaii") is None
    assert shapes.island("United States", "Mexico") is None and shapes.island("United States", "Arizona") is None
    known = cc.Countries([cc.Country("United States", 1, "", 0, ""), cc.Country("Hawaii", 0, "", 1, "United States"), cc.Country("Guam", 1, "", 1, "")])
    old = pc.StateShapes(_made["store"], pc.States(STATES, known), known, [])
    assert old.island("United States", "Hawaii") is None and old.island("United States", "Guam") is None      # not valid; no parent
    # the report: the island shape in the table of names, and not in the list for the Geolocale Manager
    lists = pc.Lists()
    record = row("casent0000003", "United States", 21.5, -159.5, event="BLF300")
    lists.add(record, other.work(record), other.kind(record))
    text = pc.report_text("x", "y", "every group", lists, other.shapes, _made["store"], "1 second", 1.0)
    assert ("    AntWeb states matched (2): Arizona; New Mexico\n    AntWeb states not matched (0): \n"
            "    shapes that are an island country of AntWeb, entered as the state (1): Hawaii\n    shapes that are no state in AntWeb (0): \n") in text, text
    assert "with the specimens that lie in them:\n  none\n" in text, text
    assert lists.corrections()[0][3:7] == ["adm1", "", "Hawaii", "coordinates inside Hawaii, geoBoundaries USA ADM1; " + HAWAII]


def test_country_missing_in_an_island_country():
    # two lines: the country is the island's parent, the state the island
    for work in (setup(), without_hawaii_as_a_state()):
        basis = "coordinates inside Hawaii, Natural Earth 1:10m; " + HAWAII
        r = work.work(row("casent0000004", "", 21.5, -159.5))
        assert r == [pc.Proposal("country", "", "United States", basis, "United States"), pc.Proposal("adm1", "", "Hawaii", basis, "United States")], r
        basis = "in the water, 0.52 km from Hawaii, Natural Earth 1:10m; " + HAWAII
        r = work.work(row("casent0000004", "", 21.5, -160.005))
        assert r == [pc.Proposal("country", "", "United States", basis, "United States"), pc.Proposal("adm1", "", "Hawaii", basis, "United States")], r
        # a country AntWeb does not know: the same two lines, the stored value in the first
        r = work.work(row("casent0000004", "Sandwich Islands", 21.5, -159.5))
        assert [(x.field, x.current, x.proposed) for x in r] == [("country", "Sandwich Islands", "United States"), ("adm1", "", "Hawaii")], r
        # a record that has a state, or its island country, gets the country alone
        assert [(x.field, x.proposed) for x in work.work(row("casent0000004", "", 21.5, -159.5, adm1="Honolulu"))] == [("country", "United States")]
        assert [(x.field, x.proposed) for x in work.work(row("casent0000004", "", 21.5, -159.5, island="Hawaii"))] == [("country", "United States")]
    # a country that is no island: one line for the country and the state from the state shapes, as before
    assert [x.basis for x in w("", 25.0, -108.0)] == ["coordinates inside Mexico, Natural Earth 1:10m", "coordinates inside Sonora, geoBoundaries MEX ADM1"]


def test_reasons_for_no_proposal():
    assert w("Tinyland", 0.5, 0.5) == [pc.Not("adm1", pc.NO_STATES, "Tinyland", "Tinyland")]              # the country has no states in AntWeb
    assert w("Mexico") == [pc.Not("adm1", pc.NO_COORDINATES, "")]
    assert w("Mexico", 0, 0) == [pc.Not("adm1", pc.NO_COORDINATES, "")] and w("Mexico", 95, -100) == [pc.Not("adm1", pc.NO_COORDINATES, "")]
    assert w("Atoll", 0.5, 10.5) == [pc.Not("adm1", pc.NO_FILE, "geoBoundaries has no file of the divisions of ATL", "Atoll")]
    assert w("Reunion", -21.0, 55.9) == [pc.Not("adm1", pc.NOT_A_STATE, "Le Port", "Reunion")]
    assert w("Reunion", -21.0, 55.2) == [pc.Proposal("adm1", "", "Saint-Denis", "coordinates inside Saint-Denis, geoBoundaries REU ADM4", "Reunion", "Saint-Pierre", "21", "")]


def test_country_missing():
    r = w("", 25.0, -108.0)
    assert r == [pc.Proposal("country", "", "Mexico", "coordinates inside Mexico, Natural Earth 1:10m", "Mexico"),
                 pc.Proposal("adm1", "", "Sonora", "coordinates inside Sonora, geoBoundaries MEX ADM1", "Mexico")], r
    # in the water within 1 km of one country
    r = w("", 25.0, -110.005)
    assert [x.proposed for x in r] == ["Mexico", "Sonora"] and r[0].basis == "in the water, 0.5 km from Mexico, Natural Earth 1:10m", r
    # no coordinates, far at sea: nothing proposed, and no second line for the state
    assert w("") == [pc.Not("country", pc.NO_COORDINATES, "")]
    assert w("", 25.0, -112.0) == [pc.Not("country", pc.AT_SEA, "202 km from the nearest country")]
    assert w("", 25.0, -110.5) == [pc.Not("country", pc.AT_SEA, "50 km from Mexico")]
    # a record with no country that has a state keeps it
    assert [x.field for x in w("", 25.0, -108.0, adm1="Sonora")] == ["country"]


def test_country_not_valid_in_antweb():
    r = w("Africa", -15.0, 47.0)
    assert r[0] == pc.Proposal("country", "Africa", "Madagascar", "coordinates inside Madagascar, Natural Earth 1:10m", "Madagascar")
    assert r[1].proposed == "Antsiranana"
    assert w("Africa") == [pc.Not("country", "country not valid in AntWeb; no coordinates", "")]


def test_lists_and_a_collecting_event_with_two_proposals():
    work = setup()
    lists = pc.Lists({"1": "California Academy of Sciences"})
    records = [row("casent0000011", "Mexico", 25.0, -108.0, event="BLF100", locality="Sierra, 5 km N", locality_code="L1"),
               row("casent0000012", "Mexico", 25.0, -108.0, event="BLF100", locality="Sierra, 5 km N", locality_code="L1"),
               row("casent0000013", "Mexico", 27.0, -103.0, event="BLF100", locality="Sierra, 5 km N"),          # the same event, another state
               row("casent0000014", "Mexico", 22.0, -108.0, event="BLF200", locality="Coast"),
               row("casent0000015", "Mexico", event="BLF200", locality="Coast"),                                 # the same event, no coordinates
               row("casent0000016", "", -15.0, 47.0, event="BLF300", locality="Montagne d'Ambre"),
               row("casent0000017", "", event="", locality="no data", notes="presumably Mexico", bioregion="Neotropical", adm2="X"),
               row("casent0000018", "", event="", locality="no data"),
               row("casent0000019", "Mexico", 35.0, -108.0, event="BLF400"),
               row("casent0000020", "Mexico", 35.0, -108.0, event="BLF400"),
               row("casent0000021", "United States", 21.5, -159.5, island="Hawaii", event="BLF500"),
               row("casent0000022", "Mexico", 23.0, -108.0, event="", locality="no event code")]
    for record in records:
        lists.add(record, work.work(record), work.kind(record))
    corrections = lists.corrections()
    # in the order country, state, collecting event; the country before the state of the same specimen
    assert [(c[0], c[3], c[5]) for c in corrections] == [
        ("casent0000016", "country", "Madagascar"), ("casent0000016", "adm1", "Antsiranana"),
        ("casent0000013", "adm1", "Chihuahua"), ("casent0000022", "adm1", "Sonora"), ("casent0000011", "adm1", "Sonora"),
        ("casent0000012", "adm1", "Sonora"), ("casent0000014", "adm1", "Sonora")], [(c[0], c[3], c[5]) for c in corrections]
    assert corrections[4] == ["casent0000011", "BLF100", "L1", "adm1", "", "Sonora", "coordinates inside Sonora, geoBoundaries MEX ADM1", "", "", "", "", "25.0", "-108.0", "",
                              "Sierra, 5 km N", "Mexico", "California Academy of Sciences", "", "", "adm1", "", "", ""]
    events = {(e[0], e[1]): e for e in lists.events()}
    # a collecting event whose specimens get different proposals is flagged
    e = events[("BLF100", "adm1")]
    assert e[3] == "Chihuahua | Sonora" and e[4] == 3 and e[5:7] == ["casent0000011", "casent0000013"], e
    assert e[10] == "DIFFERENT PROPOSALS, do not enter as it stands: Chihuahua (1); Sonora (2). 2 different coordinates in this collecting event" and e[16] == ""
    e = events[("BLF200", "adm1")]
    assert e[3] == "Sonora" and e[4] == 1 and e[10] == "1 more of this collecting event in not_derivable.csv" and e[16].startswith("coordinates inside Sonora")
    assert events[("BLF300", "country")][3] == "Madagascar" and events[("BLF300", "adm1")][3] == "Antsiranana"
    assert events[("", "adm1")][5] == "casent0000022" and len(events) == 5                 # no event code: a line of its own
    nots = lists.not_derivable()
    assert [(x[0], x[1], x[2], x[4], x[5], x[6]) for x in nots] == [
        ("BLF400", "adm1", pc.OUTSIDE, 2, "casent0000019", "casent0000020"),
        ("BLF200", "adm1", pc.NO_COORDINATES, 1, "casent0000015", "casent0000015"),
        ("", "country", pc.NO_COORDINATES, 1, "casent0000017", "casent0000017"),
        ("", "country", pc.NO_COORDINATES, 1, "casent0000018", "casent0000018")], nots
    assert nots[2][7:13] == ["", "", "X", "no data", "presumably Mexico", "Neotropical"] and nots[0][3] == "United States, 556 km from Mexico"
    assert dict(lists.looked) == {"state missing": 8, "country missing": 3, "state missing, island-country specimen (left out)": 1}
    with tempfile.TemporaryDirectory() as folder:
        pc.write_csv(os.path.join(folder, "c.csv"), pc.CORRECTIONS_HEADER, corrections)
        with open(os.path.join(folder, "c.csv"), encoding="utf-8", newline="") as f:
            read = list(csv.reader(f))
    assert read[0] == ["specimen_code", "collecting_event_code", "locality_code", "field", "current_value", "proposed_value", "basis", "nearest_other_state",
                       "km_to_other_state", "other_state_flag", "note", "latitude", "longitude", "stated_error", "locality_name", "country", "upload_group", "unit_iso_3166_2", "unit_ne_id", "taxonworks_predicate",
                       "biogeographic_region", "unit_code", "unit_source"] and read[5] == corrections[4]
    text = pc.report_text("2026-11-10 09:00", "abc1234", "California Academy of Sciences (id 1)", lists, work.shapes, _made["store"], "3 seconds", 1.0)
    for part in ("Upload group: California Academy of Sciences (id 1)",
                 "Records looked at (no state, no country, a country AntWeb does not know, a province to convert): 12",
                 "        8  state missing", "        3  country missing", "        1  state missing, island-country specimen (left out)",
                 "Country: 1 specimens with a proposal (corrections.csv), in 1 lines of by_collecting_event.csv",
                 "  No proposal (not_derivable.csv): 2 specimens, by reason:\n          2  no coordinates",
                 "State (adm1): 6 specimens with a proposal (corrections.csv), in 4 lines of by_collecting_event.csv, 1 of them flagged for different proposals",
                 "          2  coordinates outside the stated country", "          1  no coordinates",
                 "          4  Mexico: Sonora", "          1  Madagascar: Antsiranana",
                 "  Mexico (MEX): ADM1 3/3 AntWeb states, 3/3 shapes; ADM2 to ADM4 not looked at | used: ADM1 (at least half", "  Mexico: geoBoundaries MEX ADM1\n",
                 "    AntWeb states matched (3): Chihuahua; Coahuila; Sonora",
                 "    matched in another way than by the name itself: Coahuila de Zaragoza -> Coahuila (mapping file); Estado de Chihuahua -> Chihuahua (name without the unit word)",
                 "  Madagascar: geoBoundaries MDG ADM1", "    shapes that are no region in AntWeb (1): Analamanga",
                 "  Madagascar ADM1: Diana -> Antsiranana", "  Mexico ADM1: Coahuila de Zaragoza -> Coahuila",
                 "    Open Data Commons Open Database License 1.0: MDG ADM1_simplified",
                 "Report only: nothing was changed in the database."):
        assert part in text, part + "\n" + text
    assert "nightly" not in text.lower()
    # the summary fits one screen: the tables start after it
    lines = text.split("\n")
    assert lines.index("Details") < pc.SUMMARY_LINES and lines.index("Details") < lines.index("Country, proposed values (the 40 largest):")
    assert lines.index("Report only: nothing was changed in the database.") < lines.index("Details")


def test_names_for_the_suggestions():
    # a name whose accents were spoilt when the boundary file was made (the file of Chile) is put right; any other is left alone
    assert pc.mend("Regi\u00c3\u00b3n de Antofagasta") == "Regi\u00f3n de Antofagasta" and pc.mend("Regi\u00c3\u00b3n de \u00c3\u0091uble") == "Regi\u00f3n de \u00d1uble"
    for name in ("Regi\u00f3n de \u00d1uble", "S\u00e3o Paulo", "C\u00f4te d\u2019Ivoire", "\u00c3uble", "Hessen", ""):
        assert pc.mend(name) == name, name
    assert pc.shape_names(os.path.join(_made.get("folder") or (setup() and _made["folder"]), "media", "ADS-ADM1_simplified.geojson")) == ["Regi\u00f3n de Norte", "Regi\u00f3n del Sur"]
    # the words that say what kind of unit a name is are left out; a name made of them alone stays
    assert pc.generic("Jakarta Special Capital Region") == "jakarta" and pc.generic("District Autonome D'Abidjan") == "abidjan"
    assert pc.generic("Distrito Federal") == "distrito federal" and pc.generic("Xinjiang Uyghur Autonomous Region") == "xinjiang uyghur"
    assert pc.variants("West Sepik (Sandaun) Province") == ["west sepik sandaun", "west sepik", "sandaun"]
    assert pc.variants("Catalu\u00f1a/Catalunya") == ["cataluna catalunya", "cataluna", "catalunya"]
    # the names of the first real run, each with a state name AntWeb may have: all at least 0.8
    for shape, state in (("Rio de Jeneiro", "Rio de Janeiro"), ("Nothern Cape", "Northern Cape"), ("Northen Red Sea Region", "Northern Red Sea"),
                         ("Ras al-Khaimah", "Ra's al Khaymah"), ("Tanitharyi", "Tanintharyi"), ("District Autonome D'Abidjan", "Abidjan"),
                         ("West Sepik (Sandaun) Province", "Sandaun"), ("Jakarta Special Capital Region", "Jakarta"),
                         ("Xinjiang Uyghur Autonomous Region", "Xinjiang"), ("Hessen", "Hesse"), ("Bogota Capital District", "Bogot\u00e1")):
        assert pc.likeness(shape, state) >= 0.8, (shape, state, pc.likeness(shape, state))
    assert pc.likeness("Jakarta Special Capital Region", "Jakarta") == 1.0 and pc.likeness("Hessen", "Hesse") == 0.91
    assert pc.likeness("Sonora", "Sinaloa") < pc.MIN_SCORE and pc.likeness("North", "South") < pc.MIN_SCORE and pc.likeness("Hlavn\u00ed m\u011bsto Praha", "Prague") < pc.MIN_SCORE
    # nothing of this is used to match: Jakarta Special Capital Region is no state until a person says so
    states = pc.States([("Jakarta", "Indonesia", 1, ""), ("Navarre", "Iberia", 1, ""), ("Navarra", "Iberia", 0, "Navarre"), ("Madrid", "Iberia", 1, "")],
                       cc.Countries([cc.Country("Indonesia", 1, "", 0, ""), cc.Country("Iberia", 1, "", 0, "")]))
    assert states.find("Indonesia", "Jakarta Special Capital Region") == (None, "")
    assert states.closest("Indonesia", "Jakarta Special Capital Region") == [(1.0, "Jakarta", "")]
    # an old name of AntWeb that points to a valid state counts, and is named
    assert states.closest("Iberia", "Comunidad Foral de Navarra") == [(1.0, "Navarre", "Navarra")]
    assert states.closest("Iberia", "Comunidad de Madrid") == [(1.0, "Madrid", "")] and states.closest("Iberia", "Islas") == []


def L(level, state_hits=0, states=0, shape_hits=0, shapes=0):
    return {"level": level, "shapes": shapes, "shape_hits": shape_hits, "states": states, "state_hits": state_hits, "islands": 0}


def test_the_level_rule():
    both = "at least half of AntWeb's states are matched and at least half of the shapes are AntWeb states"
    largest = "the level with the largest share of shapes that are AntWeb states; "
    # the usual country: the first level fits, the finer ones are not looked at
    assert pc.pick_level([L("ADM1", 30, 32, 30, 32)]) == (L("ADM1", 30, 32, 30, 32), True, both)
    # Chile as the first run saw it: no region matched (spoilt names), some provinces and communes have the names of regions.
    # Version 1 took the communes (the most states matched). Now: none used; a mapping would be for the level of 16 shapes
    tried = [L("ADM1", 0, 16, 0, 16), L("ADM2", 5, 16, 5, 56), L("ADM3", 7, 16, 7, 345), L("ADM4")]
    picked, usable, why = pc.pick_level(tried)
    assert (picked["level"], usable) == ("ADM1", False), picked
    assert why == "below the minimum: at the best level, ADM2, 5 of 56 shapes are AntWeb states and 5 of 16 AntWeb states are matched", why
    # the Czech Republic with its 14 regions in AntWeb under English names: 8 districts have the names of regions. Not used on that alone.
    tried = [L("ADM1", 0, 14, 0, 14), L("ADM2", 8, 14, 8, 77), L("ADM3", 6, 14, 6, 6257), L("ADM4", 4, 14, 4, 13090)]
    assert pc.pick_level(tried)[:2] == (tried[0], False)
    # even when the fine level has half of AntWeb's states by its names, the coarse one with a larger share of shapes is the one looked at
    tried = [L("ADM1", 6, 16, 6, 16), L("ADM2", 5, 16, 5, 56), L("ADM3", 8, 16, 8, 345)]
    assert pc.pick_level(tried)[:2] == (tried[0], False)
    # AntWeb has few states of a country: all of them matched, few of the shapes
    tried = [L("ADM1", 5, 5, 5, 30), L("ADM2", 1, 5, 1, 300), L("ADM3"), L("ADM4")]
    assert pc.pick_level(tried) == (tried[0], True, largest[:-2] + ", and the coarsest; at least half of AntWeb's states are matched")
    # the same at a finer level is not enough (the coarser level has a file and fits nothing)
    assert pc.pick_level([L("ADM1", 0, 5, 0, 30), L("ADM2", 5, 5, 5, 300)])[1] is False
    # AntWeb's list has the regions and many communes besides: the regions cover the file's first level
    tried = [L("ADM1", 16, 56, 16, 16), L("ADM2", 3, 56, 3, 56), L("ADM3", 40, 56, 40, 345)]
    assert pc.pick_level(tried) == (tried[0], True, largest + "at least half of its shapes are AntWeb states")
    # AntWeb's states are the second level
    tried = [L("ADM1", 0, 50, 0, 19), L("ADM2", 45, 50, 45, 52)]
    assert pc.pick_level(tried) == (tried[1], True, both)
    # two levels alike: the coarser
    tried = [L("ADM1", 2, 8, 2, 8), L("ADM2", 4, 8, 4, 16)]
    assert pc.pick_level(tried)[0]["level"] == "ADM1" and not pc.pick_level(tried)[1]
    assert pc.pick_level([L("ADM1"), L("ADM2")]) == (None, False, "no boundary file")
    assert pc.level_text(L("ADM3", 7, 16, 7, 1345)) == "ADM3 7/16 AntWeb states, 7/1,345 shapes" and pc.level_text(L("ADM4")) == "ADM4 no file"


def test_a_file_with_spoilt_names_and_a_fine_level_with_the_names_of_the_states():
    # Andesia: the regions are "RegiÃ³n de Norte" in the file; the communes Norte and Sur lie elsewhere. The regions are used.
    r = w("Andesia", -22.0, -72.0)
    assert r == [pc.Proposal("adm1", "", "Norte", "coordinates inside Regi\u00f3n de Norte (Norte by the name without the unit word), geoBoundaries ADS ADM1", "Andesia")], r
    assert w("Andesia", -27.0, -72.0)[0].proposed == "Norte"          # in the commune "Norte" too; the commune "Sur" (lat -22) was in the region Norte
    t = setup().shapes.tables["Andesia"]
    assert pc.level_line("Andesia", t).startswith("  Andesia (ADS): ADM1 2/2 AntWeb states, 2/2 shapes; ADM2 to ADM4 not looked at | used: ADM1 (")
    assert setup().shapes.match("Andesia", "ADM1", ["Regi\u00c3\u00b3n de Norte"]) == ([None], [""])       # the name as the file has it fits nothing


def test_a_country_below_the_minimum_and_the_suggestions():
    work = setup()
    lists = pc.Lists()
    records = [row("casent0000061", "Iberia", 42.0, -6.0, event="E1"), row("casent0000062", "Iberia", 42.0, -6.0, event="E1"),
               row("casent0000063", "Iberia", 38.0, -6.0, event="E2"), row("casent0000064", "Iberia", 37.0, 0.0, event="E3"),
               row("casent0000065", "Madagascar", -20.0, 49.0, event="E4"), row("casent0000066", "Reunion", -21.0, 55.9, event="E5")]
    for record in records:
        lists.add(record, work.work(record), work.kind(record))
    # Iberia: AntWeb has the communities in English, the file in Spanish; one province is called Madrid. Version 1 took the provinces.
    assert work.work(records[0]) == [pc.Not("adm1", pc.NO_MATCH, "Comunidad de Madrid", "Iberia")]
    t = work.shapes.tables["Iberia"]
    assert not t["usable"] and t["level"] == "ADM1" and t["not_matched"] == ["Andalusia", "Catalonia", "Madrid", "Navarre"]
    assert pc.level_line("Iberia", t) == (
        "  Iberia (IBR): ADM1 0/4 AntWeb states, 0/5 shapes; ADM2 1/4 AntWeb states, 1/6 shapes; ADM3 no file; ADM4 no file | NONE USED; a mapping would be for ADM1 "
        "(below the minimum: at the best level, ADM2, 1 of 6 shapes are AntWeb states and 1 of 4 AntWeb states are matched)")
    assert not [p for _, p in lists.proposals]
    nots = lists.not_derivable()
    assert [(x[0], x[2], x[3], x[4]) for x in nots if x[7] == "Iberia"] == [("E1", pc.NO_MATCH, "Comunidad de Madrid", 2), ("E2", pc.NO_MATCH, "Andaluc\u00eda", 1),
                                                                             ("E3", pc.NO_MATCH, "Islas", 1)], nots
    lines, left_out = pc.suggestions(work.shapes, work.states, lists.in_shapes())
    wanted = ("Iberia", "Madagascar", "Reunion")
    assert [x for x in lines if x[0] in wanted] == [
        ["Iberia", "ADM1", "Comunidad de Madrid", "Madrid", "1.00", 2, "", ""],
        ["Iberia", "ADM1", "Andaluc\u00eda", "Andalusia", "0.89", 1, "", ""],
        ["Iberia", "ADM1", "Islas", "", "", 1, "no AntWeb state of Iberia has a name like it", ""],
        ["Iberia", "ADM1", "Catalu\u00f1a/Catalunya", "Catalonia", "0.82", 0, "", ""],
        ["Iberia", "ADM1", "Comunidad Foral de Navarra", "Navarre", "1.00", 0, "by AntWeb's old name Navarra", ""],
        ["Madagascar", "ADM1", "Analamanga", "", "", 1, "no AntWeb state of Madagascar has a name like it", ""],
        ["Reunion", "ADM4", "Le Port", "", "", 1, "no AntWeb state of Reunion has a name like it", ""]], lines
    assert pc.SUGGESTIONS_HEADER[:4] == ["country", "level", "shape", "antweb_state"]
    # the report: the country in the summary, the count by reason, the line of its levels
    text = pc.report_text("x", "y", "every group", lists, work.shapes, _made["store"], "1 second", 1.0, pc.MAP_FILE, (lines, left_out))
    top = text.split("\nDetails\n")[0]
    assert ("AntWeb's states and the boundary file do not match: needs a mapping (1 country, 4 specimens; see mapping_suggestions.csv):\n  Iberia ADM1 (4)\n") in top, top
    assert "          4  AntWeb's states and the boundary file do not match: needs a mapping\n          2  the boundary file's name is not a state in AntWeb\n" in top
    assert len(top.split("\n")) <= pc.SUMMARY_LINES
    assert "  Iberia: geoBoundaries IBR ADM1 (NOT USED: below the minimum)\n" in text and "mapping_suggestions.csv: " in text
    assert "  Iberia: geoBoundaries IBR ADM1 (NOT USED: below the minimum)\n    AntWeb states matched (0): \n    AntWeb states not matched (4): Andalusia; Catalonia; Madrid; Navarre\n" in text
    # nothing is used from the suggestions; pasted into the mapping file, the approved lines make the country usable
    with tempfile.TemporaryDirectory() as folder:
        pc.write_csv(os.path.join(folder, "mapping_suggestions.csv"), pc.SUGGESTIONS_HEADER, lines)
        pasted = [r for r in pc.read_map(os.path.join(folder, "mapping_suggestions.csv")) if r[0] == "Iberia" and r[3]]
    assert ("Iberia", "ADM1", "Comunidad de Madrid", "Madrid") in pasted and len(pasted) == 4
    mapped = pc.Corrections(work.checker, work.states, pc.StateShapes(_made["store"], work.states, work.countries, pasted))
    assert len(pasted[0]) == 4
    r = mapped.work(records[0])
    assert r == [pc.Proposal("adm1", "", "Madrid", "coordinates inside Comunidad de Madrid (Madrid by the mapping file), geoBoundaries IBR ADM1", "Iberia")], r
    assert pc.level_line("Iberia", mapped.shapes.tables["Iberia"]).startswith("  Iberia (IBR): ADM1 4/4 AntWeb states, 4/5 shapes; ADM2 to ADM4 not looked at | used: ADM1 (")


def test_a_summary_with_many_countries_stays_on_one_screen():
    work = setup()
    work.work(row("casent0000061", "Iberia", 42.0, -6.0))
    lists = pc.Lists()
    table = work.shapes.tables["Iberia"]
    many = {"Country with a long name number %03d" % i: table for i in range(400)}
    saved = dict(work.shapes.tables)
    try:
        work.shapes.tables.update(many)
        text = pc.report_text("x", "y", "every group", lists, work.shapes, _made["store"], "1 second", 1.0)
    finally:
        work.shapes.tables.clear()
        work.shapes.tables.update(saved)
    lines = text.split("\n")
    assert lines.index("Details") == pc.SUMMARY_LINES + 1 and lines[pc.SUMMARY_LINES] == "" and "  ... and more: every one of them is marked NONE USED in the list of levels below" in lines[:pc.SUMMARY_LINES]
    assert "needs a mapping (401 countries, 0 specimens" in text


def ne_unit(name, a3, code, ident, polygons, **more):
    props = dict({"name": name, "adm0_a3": a3, "gu_a3": a3, "iso_3166_2": code, "ne_id": ident, "name_alt": None, "name_local": None}, **more)
    return {"type": "Feature", "properties": props,
            "geometry": {"type": "MultiPolygon", "coordinates": [[p] for p in polygons]} if len(polygons) > 1 else {"type": "Polygon", "coordinates": polygons}}


# A file like Natural Earth's states and provinces: Congoland drawn in another way than the boundary files above
NE_UNITS = [
    ne_unit("Bas-U\u00e9l\u00e9", "CGL", "CG-BU", 101, [square(20, -2, 22, 0)], name_en="Lower Uele"),
    ne_unit("Province de l'\u00c9quateur", "CGL", "CG-EQ", 102, [square(22, -2, 24, 0)], name_fr="\u00c9quateur", name_en="Equator", name_alt="Equatorial|Equateur"),
    ne_unit("Kasa\u00ef", "CGL", "CG-KA", 103, [square(24, -2, 26, 0), square(26, -2, 28, 0)], name_alt="Kassai"),
    ne_unit("Katanga Nord", "CGL", "CG-KN", 104, [square(20, -4, 22, -2)]), ne_unit("Katanga Sud", "CGL", "CG-KS", 105, [square(22, -4, 24, -2)]),
    ne_unit("Ituri", "CGL", "CG-IT", 106, [square(24, -4, 28, -2)], name_ru="\u0418\u0442\u0443\u0440\u0438"),
    ne_unit("Nord-Kivu", "CGL", "CG-NK", 107, [square(28, -4, 30, 0)], name_alt="Kivou|North Kivu"),
    ne_unit("La R\u00e9union", "FRA", "FR-RE", 201, [square(55, -21.5, 56, -20.5)], gu_a3="REU"),
    ne_unit(None, "CGL", "CG-X01~", 108, [square(20, -6, 21, -5)]),
    {"type": "Feature", "properties": {"name": "Nowhere", "adm0_a3": "CGL", "gu_a3": "CGL", "iso_3166_2": "CG-NO", "ne_id": 109}, "geometry": None},
]


def natural_earth():
    """The test file of states and provinces, written once; a NaturalEarthStates over it."""
    setup()
    if "ne" not in _made:
        path = os.path.join(_made["folder"], "ne_states.geojson")
        with open(path, "w", encoding="utf-8") as f:
            json.dump({"type": "FeatureCollection", "name": "ne_10m_admin_1_states_provinces", "features": NE_UNITS}, f)
        _made["ne_path"], _made["ne"] = path, pc.NaturalEarthStates(path)
    return _made["ne"]


def specimen_world():
    """The test world with AntWeb's own specimens to test the shapes with, and two rows of a mapping file: one for a
    state AntWeb does not have yet (marked new), one for a state AntWeb does not have (not marked: not used)."""
    work = setup()
    evidence = pc.Evidence(work.countries, work.states)
    for country, state, lat, lon, count in SPECIMENS:
        evidence.add(country, state, str(lat), str(lon), count)
    rows = TEST_MAP + [("Congoland", "ADM1", "Ile Nouvelle", "Nouvelle"), ("Congoland", "ADM1", "Vide", "Rien")]
    shapes = pc.StateShapes(_made["store"], work.states, work.countries, rows, evidence, pc.Rules(new=[("Congoland", "ADM1", "Ile Nouvelle", "Nouvelle")]))
    return pc.Corrections(work.checker, work.states, shapes), evidence


def test_shapes_tested_with_antweb_s_own_specimens():
    work, evidence = specimen_world()
    at = lambda lat, lon: work.work(row("casent0000071", "Congoland", lat, lon))
    # read: 197 specimens; a state or a country AntWeb does not know and unusable coordinates are left out
    assert dict(evidence.totals) == {"specimens with a country, a state and coordinates, not of an island country": 197, "used for the test": 176,
                                     "left out: a state AntWeb does not know as a state of the country": 9, "left out: coordinates that cannot be used": 5,
                                     "left out: a country AntWeb does not know": 7}
    # confirmed by specimens, whatever the names: 40 of Bas-Uele's 42 lie in the shape Lower Uele, and 40 of the shape's 44 are Bas-Uele
    assert at(-1.0, 21.0) == [pc.Proposal("adm1", "", "Bas-Uele", "coordinates inside Lower Uele (Bas-Uele by the specimens), geoBoundaries CGL ADM1", "Congoland")]
    # the shape Kasai has exactly the name of the state Kasai: it stays Kasai, though 18 of Kasai's 20 specimens lie in the shape Sankuru
    # (listed for a person). Sankuru, the name of no state, is Kasai by those specimens
    assert at(-1.0, 25.0) == [pc.Proposal("adm1", "", "Kasai", "coordinates inside Kasai, geoBoundaries CGL ADM1", "Congoland")]
    assert at(-1.0, 27.0)[0].basis == "coordinates inside Sankuru (Kasai by the specimens), geoBoundaries CGL ADM1" and at(-1.0, 27.0)[0].proposed == "Kasai"
    # a name match with too few specimens to test, and one the specimens agree with in part, are used
    assert at(-1.0, 23.0) == [pc.Proposal("adm1", "", "Equateur", "coordinates inside Equateur, geoBoundaries CGL ADM1", "Congoland")]
    assert at(-2.0, 29.0)[0].proposed == "Kivu"
    # a shape that holds two AntWeb states, a state that lies in two shapes: no proposal, for a person to decide
    assert at(-3.0, 22.0) == [pc.Not("adm1", pc.NOT_A_STATE, "Katanga", "Congoland")] and at(-3.0, 25.0) == [pc.Not("adm1", pc.NOT_A_STATE, "Ituri Est", "Congoland")]
    t = work.shapes.tables["Congoland"]
    lines = [x for x in pc.tested_lines(work.shapes) if x[0] == "Congoland"]
    assert pc.TESTED_HEADER[:4] == ["country", "level", "shape", "antweb_state"] and pc.TESTED_HEADER[9:] == ["unit_iso_3166_2", "unit_ne_id", "unit_code", "unit_source"]
    # shape, state, what it rests on; the state's specimens in the shape, their share of the state's, of the shape's; what the shape's specimens are filed under
    assert [x[2:9] for x in lines] == [
        ["Equateur", "Equateur", "by name only, 3 specimens", 3, "100", "60", "Equateur 3; Bas-Uele 2"],
        ["Ile Nouvelle", "Nouvelle", "mapping file: state to add in the Geolocale Manager first", 0, "", "0", "Ituri 3"],
        ["Kasai", "Kasai", "by name; the name stands, the specimens of Kasai lie in Sankuru 18; Kasai 2 (to_decide.csv)", 2, "10", "100", "Kasai 2"],
        ["Kivu", "Kivu", "by name; the specimens agree in part", 8, "67", "100", "Kivu 8"],
        ["Lower Uele", "Bas-Uele", "confirmed by specimens", 40, "95", "91", "Bas-Uele 40; Kivu 4"],
        ["Sankuru", "Kasai", "confirmed by specimens", 18, "90", "100", "Kasai 18"]], lines
    assert [x for x in pc.decide_lines(work.shapes) if x[0] == "Congoland"] == [
        ["Congoland", "ADM1", "stored state differs from where the coordinates fall", "Kasai", "the shape is Kasai by its name; the specimens of Kasai lie in Sankuru 18; Kasai 2", 20],
        ["Congoland", "ADM1", "shape holds several AntWeb states", "Katanga", "Katanga Nord 30; Katanga Sud 20", 50],
        ["Congoland", "ADM1", "AntWeb state lies in several shapes", "Ituri", "Ituri Est 12; Ituri Ouest 10", 25]]
    assert t["stats"]["confirmed"] == 2 and t["stats"]["contradicted"] == 0 and t["stats"]["kept"] == 1 and t["stats"]["testable"] == 6 and t["stats"]["located"] == 152
    assert t["stats"]["state_hits"] == 4 and t["stats"]["sound_hits"] == 3                  # for the choice of the level a name that stands against specimens does not count
    assert t["matched"] == ["Bas-Uele", "Equateur", "Kasai", "Kivu"] and t["not_matched"] == ["Ituri", "Katanga Nord", "Katanga Sud"]
    assert t["shapes_not_states"] == ["Ituri Est", "Ituri Ouest", "Katanga", "Vide"]
    assert pc.level_line("Congoland", t) == (
        "  Congoland (CGL): ADM1 4/7 AntWeb states, 6/10 shapes [specimens: 2 states confirmed, 0 name matches not used, 1 names standing against specimens, "
        "of 6 states with 5 or more located]; "
        "ADM2 to ADM4 not looked at | used: ADM1 (the level with the most AntWeb states confirmed by specimens or matched by name)")
    # the same country without the specimens: by the names alone 3 of 7 states fit, below the minimum, and nothing is proposed
    assert w("Congoland", -1.0, 25.0) == [pc.Not("adm1", pc.NO_MATCH, "Kasai", "Congoland")] and w("Congoland", -1.0, 21.0) == [pc.Not("adm1", pc.NO_MATCH, "Lower Uele", "Congoland")]
    # what is left for the suggestions: the shapes that got no state, with what AntWeb's specimens in them are filed under
    lists = pc.Lists()
    for i, (lat, lon) in enumerate([(-3.0, 22.0), (-3.0, 22.5), (-5.0, 24.0), (-5.0, 29.0)]):
        record = row("casent00000%d" % (80 + i), "Congoland", lat, lon, event="E%d" % i)
        lists.add(record, work.work(record), work.kind(record))
    suggested, left_out = pc.suggestions(work.shapes, work.states, lists.in_shapes())
    assert [x for x in suggested if x[0] == "Congoland"] == [
        ["Congoland", "ADM1", "Katanga", "Katanga Sud", "0.88", 2, "", "Katanga Nord 30; Katanga Sud 20"],
        ["Congoland", "ADM1", "Katanga", "Katanga Nord", "0.87", 2, "", "Katanga Nord 30; Katanga Sud 20"],
        ["Congoland", "ADM1", "Vide", "", "", 1, "no AntWeb state of Congoland has a name like it", ""],
        ["Congoland", "ADM1", "Ituri Est", "Ituri", "0.86", 0, "", "Ituri 12"],
        ["Congoland", "ADM1", "Ituri Ouest", "Ituri", "0.84", 0, "", "Ituri 10"]], suggested
    # the report: the numbers of the test in the summary, the constants and what a person has to decide in the tables
    text = pc.report_text("x", "y", "every group", lists, work.shapes, _made["store"], "1 second", 1.0, pc.MAP_FILE, (suggested, left_out), None, evidence)
    top, rest = text.split("\nDetails\n")
    assert ("The shapes and AntWeb's states (mapping_tested.csv), tested with AntWeb's own specimens that have a state and coordinates (176 of every group):\n"
            "  2 shapes confirmed by specimens, 1 by name only, 1 by name with the specimens agreeing in part, 1 by a name that stands against specimens, "
            "1 by the mapping file, 0 island countries; 0 name matches not used.\n"
            "For a person to decide (to_decide.csv): 1 shapes that hold several AntWeb states, 1 AntWeb states that lie in several shapes, "
            "1 names that stand against specimens (their stored state differs from where they lie).\n"
            "Proposed states not yet in AntWeb, to add in the Geolocale Manager first (specimens in brackets): Congoland: Nouvelle (1)\n"
            "For TaxonWorks: no archive was given (--tw-archive), so tw_load.tsv was not written.\n") in top, top
    assert len(top.split("\n")) <= pc.SUMMARY_LINES
    for part in ("A shape is a state \"confirmed by specimens\" when at least 5 specimens of the state lie in the shape, they are\nat least 80% of the state's",
                 "at least 80% of the shape's specimens that have a state are filed under it.",
                 "is not used when the state has 20 or more located specimens and fewer than 50% of them lie in the shape",
                 "whatever the specimens say; those that say otherwise are in to_decide.csv",
                 "         20  Congoland ADM1: the shape is Kasai by its name; the specimens of Kasai lie in Sankuru 18; Kasai 2",
                 "        176  used for the test",
                 "         50  Congoland ADM1: the shape Katanga holds Katanga Nord 30; Katanga Sud 20\n         25  Congoland ADM1: AntWeb's Ituri lies in Ituri Est 12; Ituri Ouest 10",
                 "add them in the Geolocale Manager first. With the specimens they are proposed for:\n          1  Congoland: Nouvelle",
                 "mapping_suggestions.csv: what the test could not settle. 5 lines for 4 shapes"):
        assert part in rest, part + "\n" + rest


def test_natural_earth_states_as_a_second_source():
    ne = natural_earth()
    work, evidence = specimen_world()
    # the units of a country by its code; a unit with no name or no shape is left out; every name of a unit is a spelling, the main one first
    assert ne.levels == ["NE"] and ne.has("CGL", "NE") and not ne.has("MEX", "NE") and ne.names("MEX", "NE") is None
    units = ne.names("CGL", "NE")
    assert [u.name for u in units] == ["Bas-U\u00e9l\u00e9", "Province de l'\u00c9quateur", "Kasa\u00ef", "Katanga Nord", "Katanga Sud", "Ituri", "Nord-Kivu"]
    assert units[1] == pc.Unit("Province de l'\u00c9quateur", ("Equator", "\u00c9quateur", "Equatorial", "Equateur"), "CG-EQ", "102")
    assert units[6].spellings == ("Kivou", "North Kivu") and units[5].spellings == ("\u0418\u0442\u0443\u0440\u0438",)
    # a territory filed under its sovereign in the file is found by its own code (La Reunion: adm0_a3 FRA, gu_a3 REU)
    assert [u.name for u in ne.names("REU", "NE")] == ["La R\u00e9union"] == [u.name for u in ne.names("FRA", "NE")]
    shapes = pc.StateShapes(_made["store"], work.states, work.countries, [("Congoland", "NE", "Katanga Sud", "Katanga Nord")], evidence, None, ne)
    second = pc.Corrections(work.checker, work.states, shapes)
    at = lambda lat, lon: second.work(row("casent0000075", "Congoland", lat, lon))
    # by the main name (accents make no difference), with the unit's ISO 3166-2 code and ne_id
    assert at(-1.0, 21.0) == [pc.Proposal("adm1", "", "Bas-Uele", "coordinates inside Bas-U\u00e9l\u00e9, Natural Earth states CGL", "Congoland", "", "", "", "", "CG-BU", "101", "",
                                         "CG-BU", "Natural Earth states and provinces 1:10m")]
    # by another name of the unit
    r = at(-1.0, 23.0)
    assert (r[0].proposed, r[0].basis, r[0].code) == ("Equateur", "coordinates inside Province de l'\u00c9quateur (Equateur by the name \u00c9quateur of the unit), "
                                                      "Natural Earth states CGL", "CG-EQ"), r
    assert at(-2.0, 29.0)[0].basis == "coordinates inside Nord-Kivu (Kivu by the name Kivou of the unit), Natural Earth states CGL"
    # a unit of two pieces; a row of the mapping file for this source (level NE) wins; no unit at the point
    assert at(-1.0, 25.0)[0].proposed == "Kasai" and at(-1.0, 27.0)[0].code == "CG-KA"
    assert at(-3.0, 23.0)[0].basis == "coordinates inside Katanga Sud (Katanga Nord by the mapping file), Natural Earth states CGL"
    assert at(-5.0, 29.0) == [pc.Not("adm1", pc.NO_SHAPE_HERE, "", "Congoland")]
    assert second.work(row("casent0000075", "Mexico", 25.0, -108.0)) == [pc.Not("adm1", pc.NO_FILE, "Natural Earth states and provinces 1:10m has no units of MEX", "Mexico")]
    t = shapes.tables["Congoland"]
    assert pc.level_line("Congoland", t) == ("  Congoland (CGL): NE 6/7 AntWeb states, 7/7 shapes [specimens: 4 states confirmed, 0 name matches not used, 0 names standing "
                                              "against specimens, of 6 states with 5 or more located] | used: NE (the level with the most AntWeb states confirmed by specimens "
                                              "or matched by name)")
    lines = pc.tested_lines(shapes)
    assert lines[0] == ["Congoland", "NE", "Bas-U\u00e9l\u00e9", "Bas-Uele", "confirmed by specimens, and by the name", 40, "95", "91", "Bas-Uele 40; Kivu 4", "CG-BU", "101",
                        "CG-BU", "Natural Earth states and provinces 1:10m"]
    assert [x[4] for x in lines] == ["confirmed by specimens, and by the name"] * 4 + ["mapping file", "by name; the specimens agree in part", "by name only, 3 specimens"]
    lists = pc.Lists()
    record = row("casent0000075", "Congoland", -1.0, 21.0, event="E1")
    lists.add(record, second.work(record), "state missing")
    ends = ["CG-BU", "101", "adm1", "", "CG-BU", "Natural Earth states and provinces 1:10m"]
    assert lists.corrections()[0][-6:] == ends and lists.events()[0][-6:] == ends
    assert pc.CORRECTIONS_HEADER[-6:] == pc.EVENTS_HEADER[-6:] == ["unit_iso_3166_2", "unit_ne_id", "taxonworks_predicate", "biogeographic_region", "unit_code", "unit_source"]
    # the pinned file: fetched once, never used with another checksum
    with tempfile.TemporaryDirectory() as folder:
        with open(_made["ne_path"], "rb") as f:
            digest = hashlib.sha256(f.read()).hexdigest()
        path = pc.fetch_states(folder, "file://" + _made["ne_path"], digest)
        assert os.path.basename(path) == "ne_10m_admin_1_states_provinces-" + cc.SHAPES_COMMIT[:12] + ".geojson" and pc.fetch_states(folder, "file:///nowhere", digest) == path
        try:
            pc.fetch_states(os.path.join(folder, "other"), "file://" + _made["ne_path"], "0" * 64)
            raise AssertionError("a file with another checksum was used")
        except SystemExit as e:
            assert "expected " + "0" * 64 in str(e) and os.listdir(os.path.join(folder, "other")) == []
    assert len(pc.NE_STATES_SHA256) == 64 and pc.NE_STATES_URL.endswith("/" + cc.SHAPES_COMMIT + "/geojson/ne_10m_admin_1_states_provinces.geojson")


def test_the_sources_compared():
    ne = natural_earth()
    work, evidence = specimen_world()
    second = pc.StateShapes(_made["store"], work.states, work.countries, [], evidence, None, ne)
    for country, code in (("Congoland", "CGL"), ("Omanland", "OML"), ("Reunion", "REU")):
        work.shapes.choose(country, code)
        second.choose(country, code)
    # geoBoundaries, Congoland: 71 of 152 specimens lie in the shape of their state, 9 in another state's, 72 in shapes with no state
    assert [work.shapes.tables["Congoland"]["stats"]["shares"][x] for x in pc.SHARES] == [71, 9, 72, 0]
    # the other file: 143 in the unit of their state, 6 in another's, none in a unit with no state, 3 in no unit (the file draws nothing there)
    assert [second.tables["Congoland"]["stats"]["shares"][x] for x in pc.SHARES] == [143, 6, 0, 3]
    lines, totals = pc.compared([("geoBoundaries", work.shapes), (ne.label, second)])
    assert lines[5:] == [
        "  country                      specimens  | geoBoundaries                       | Natural Earth states and provinces",
        "                                          | level   agree  other no st.no unit  | level   agree  other no st.no unit",
        "  ALL IT HAS SHAPES OF                    |          54.0    5.1   40.9    0.0  |          94.1    3.9    0.0    2.0",
        "    specimens in them                     | 176; none: 0 countries, 0           | 152; none: 1 countries, 24        ",
        "  Congoland                          152  | ADM1     46.7    5.9   47.4    0.0  | NE       94.1    3.9    0.0    2.0",
        "  Omanland                            24  | ADM2    100.0    0.0    0.0    0.0  | none      0.0    0.0    0.0  100.0",
        "  Reunion                              0  | ADM4  " + " " * 28 + "  | NE*   " + " " * 28], "\n".join(lines)
    assert [sum(x.values()) for x in totals] == [176, 152]             # a country the source has no shapes of is not in its total
    text = pc.report_text("x", "y", "every group", pc.Lists(), work.shapes, _made["store"], "1 second", 1.0, pc.MAP_FILE, None, None, evidence,
                          [("geoBoundaries", work.shapes), (ne.label, second)], ["Sources compared, geoBoundaries: 3 countries looked up in 2 seconds more."])
    top, rest = text.split("\nDetails\n")
    assert ("Sources compared, the specimens that lie in the unit of their state: geoBoundaries 54.0% of 176; Natural Earth states and provinces 1:10m 94.1% of 152 "
            "(table under Details).\n") in top and len(top.split("\n")) <= pc.SUMMARY_LINES
    assert "\n".join(lines) in rest and "\nSources compared, geoBoundaries: 3 countries looked up in 2 seconds more.\n" in rest


def test_the_predicate_and_the_bioregion():
    base = setup()
    # (name, georank, parent, is_valid, bioregion, alt_bioregion): as geolocale has them; "none", "null" and blank mean none
    places = [("Mexico", "country", "", 1, "Neotropical", "Nearctic"), ("Sonora", "adm1", "Mexico", 1, "Nearctic", "none"), ("Chihuahua", "adm1", "Mexico", 1, "Nearctic", "Neotropical"),
              ("Coahuila", "adm1", "Mexico", 1, "null", ""), ("Madagascar", "country", "", 1, "Malagasy", ""), ("Antsiranana", "adm1", "Madagascar", 1, "", ""),
              ("United States", "country", "", 1, "Nearctic", ""), ("Hawaii", "country", "", 1, "Oceania", ""), ("Arizona", "adm1", "United States", 1, "Nearctic", ""),
              ("Old", "adm1", "Mexico", 0, "Palearctic", ""), ("Tinyland", "country", "", 1, "none", "")]
    bio = pc.Bioregions(places, base.countries, base.states)
    # the state's when it has exactly one; else the country's when it has exactly one; else none
    assert bio.of("Mexico", "Sonora") == "Nearctic" and bio.of("Mexico", "Chihuahua") == "" and bio.of("Mexico", "Coahuila") == "" and bio.of("Mexico") == ""
    assert bio.of("Madagascar", "Antsiranana") == "Malagasy" and bio.of("Madagascar", "A state to add") == "Malagasy" and bio.of("Madagascar") == "Malagasy"
    assert bio.of("Mexico", "Chih.") == "" and bio.of("Tinyland") == "" and bio.of("Atlantis", "X") == "" and bio.of("Mexico", "Old") == ""
    # a state that is an island country of AntWeb has the island's bioregion
    assert bio.of("United States", "Hawaii") == "Oceania" and bio.of("United States", "Arizona") == "Nearctic"
    assert bio.names == {"Neotropical", "Nearctic", "Malagasy", "Oceania"}                # every bioregion a valid place has: what the load file accepts
    assert pc.no_bioregion(" None ") == "" and pc.no_bioregion("null") == "" and pc.no_bioregion("Nearctic ") == "Nearctic"
    work = pc.Corrections(base.checker, base.states, pc.StateShapes(_made["store"], base.states, base.countries, TEST_MAP), bio)
    lists = pc.Lists()
    records = [row("casent0000111", "Mexico", 25.0, -108.0, event="E1"), row("casent0000112", "", -15.0, 47.0, event="E2"), row("casent0000113", "", 21.5, -159.5, event="E3"),
               row("casent0000114", "Mexico", 27.0, -103.0, event="E4"), row("casent0000115", "", 25.0, -108.0, adm1="Sonora", event="E5")]
    for record in records:
        lists.add(record, work.work(record), work.kind(record))
    # specimen, field, proposed value; the predicate of TaxonWorks for the field; the bioregion of the place as it will be
    assert [(c[0], c[3], c[5], c[-4], c[-3]) for c in lists.corrections()] == [
        ("casent0000112", "country", "Madagascar", "Country", "Malagasy"), ("casent0000112", "adm1", "Antsiranana", "adm1", "Malagasy"),
        ("casent0000115", "country", "Mexico", "Country", "Nearctic"),            # the country proposed for a record that has the state Sonora
        ("casent0000114", "adm1", "Chihuahua", "adm1", ""),                       # the state has two bioregions, and so has the country: left blank
        ("casent0000111", "adm1", "Sonora", "adm1", "Nearctic"),
        ("casent0000113", "country", "United States", "Country", "Oceania"), ("casent0000113", "adm1", "Hawaii", "adm1", "Oceania")], lists.corrections()
    assert sorted((e[0], e[1], e[-4], e[-3]) for e in lists.events()) == [
        ("E1", "adm1", "adm1", "Nearctic"), ("E2", "adm1", "adm1", "Malagasy"), ("E2", "country", "Country", "Malagasy"), ("E3", "adm1", "adm1", "Oceania"),
        ("E3", "country", "Country", "Oceania"), ("E4", "adm1", "adm1", ""), ("E5", "country", "Country", "Nearctic")], lists.events()
    # the names of the predicates are those AntWeb's upload reads (TaxonWorksTransformer)
    with open(os.path.join(os.path.dirname(pc.HERE), "..", "src", "org", "calacademy", "antweb", "upload", "TaxonWorksTransformer.java"), encoding="utf-8", errors="replace") as f:
        java = f.read()
    for name in list(pc.PREDICATE.values()) + ["BiogeographicRegion"]:
        assert '"TW:DataAttribute:CollectingEvent:' + name + '"' in java, name
    sql = pc.places_sql({"name", "bioregion", "alt_bioregion"})
    assert "hex(ifnull(bioregion, '')), hex(ifnull(alt_bioregion, ''))" in sql and "georank in ('adm1', 'country')" in sql and pc.places_sql({"name"}).count("''") >= 2


def test_a_state_to_add():
    work, _ = specimen_world()
    # the mapping file names a state AntWeb does not have yet, marked new: proposed, and marked in the basis and the note
    r = work.work(row("casent0000072", "Congoland", -5.0, 29.0, event="E9"))
    assert r == [pc.Proposal("adm1", "", "Nouvelle", "coordinates inside Ile Nouvelle (Nouvelle by the mapping file, a state to add in the Geolocale Manager first), "
                                                   "geoBoundaries CGL ADM1", "Congoland", "", "", "", "state to add in the Geolocale Manager first")], r
    assert work.shapes.tables["Congoland"]["to_add"] == ["Nouvelle"] and ("Congoland", "ADM1", "Ile Nouvelle", "Nouvelle") in work.shapes.rows_used
    # a row for a state AntWeb does not have, not marked new, is not used (a slip of the pen makes no state)
    assert work.work(row("casent0000072", "Congoland", -5.0, 24.0)) == [pc.Not("adm1", pc.NOT_A_STATE, "Vide", "Congoland")]
    lists = pc.Lists()
    lists.add(row("casent0000072", "Congoland", -5.0, 29.0, event="E9"), r, "state missing")
    assert lists.corrections()[0][5:11] == ["Nouvelle", r[0].basis, "", "", "", "state to add in the Geolocale Manager first"]
    assert lists.events()[0][14] == "state to add in the Geolocale Manager first"
    # what AntWeb's specimens in the shape are filed under today is in mapping_tested.csv (3 of Ituri), for the decision to be checked
    line = [x for x in pc.tested_lines(work.shapes) if x[2] == "Ile Nouvelle"][0]
    assert line[4:] == ["mapping file: state to add in the Geolocale Manager first", 0, "", "0", "Ituri 3", "", "", "", ""]


def test_the_level_by_specimens():
    work, _ = specimen_world()
    # Omanland: at the first level each shape holds two AntWeb states (AntWeb is finer); at the second each state has its shape
    r = work.work(row("casent0000073", "Omanland", 22.0, 51.0))
    assert r == [pc.Proposal("adm1", "", "Alpha", "coordinates inside N1 (Alpha by the specimens), geoBoundaries OML ADM2", "Omanland")], r
    t = work.shapes.tables["Omanland"]
    assert t["level"] == "ADM2" and t["usable"] and [x["level"] for x in t["tried"]] == ["ADM1", "ADM2"]
    assert (t["tried"][0]["inside_mixed"], t["tried"][0]["state_hits"], t["tried"][1]["confirmed"]) == (4, 0, 4)
    assert pc.level_line("Omanland", t).endswith("ADM3 to ADM4 not looked at | used: ADM2 (the level with the most AntWeb states confirmed by specimens or matched by name)")
    # without specimens the names decide, as before: nothing fits, no proposal
    assert w("Omanland", 22.0, 51.0) == [pc.Not("adm1", pc.NO_MATCH, "N1", "Omanland")]
    # fewer than 5 specimens of a country: by the names alone (no test)
    few = pc.Evidence(work.countries, work.states)
    few.add("Mexico", "Sonora", "27.0", "-104.0", 4)              # 4 of Sonora in the shape of Chihuahua: too few to say anything
    by_names = pc.Corrections(work.checker, work.states, pc.StateShapes(_made["store"], work.states, work.countries, TEST_MAP, few))
    assert by_names.work(row("casent0000074", "Mexico", 25.0, -108.0))[0].proposed == "Sonora"
    assert "located" not in by_names.shapes.tables["Mexico"]["tried"][0] and by_names.shapes.tables["Mexico"]["lines"][0][2:5] == ["Sonora", "Sonora", "by name only, 4 specimens"]
    # five of them, all in the shape of Chihuahua: both shapes keep the state of their name (Sonora's is exact; Chihuahua's, without
    # "Estado de", has no 20 specimens against it), and both are listed for a person
    few.add("Mexico", "Sonora", "27.0", "-104.0", 1)
    tested = pc.Corrections(work.checker, work.states, pc.StateShapes(_made["store"], work.states, work.countries, TEST_MAP, few))
    assert tested.work(row("casent0000074", "Mexico", 25.0, -108.0))[0].proposed == "Sonora"
    assert tested.work(row("casent0000074", "Mexico", 27.0, -103.0))[0].proposed == "Chihuahua"
    t = tested.shapes.tables["Mexico"]
    assert [x[2:5] for x in t["lines"]] == [
        ["Sonora", "Sonora", "by name; the name stands, the specimens of Sonora lie in Estado de Chihuahua 5 (to_decide.csv)"],
        ["Estado de Chihuahua", "Chihuahua", "by name; the name stands, the specimens in the shape are filed under Sonora 5 (to_decide.csv)"],
        ["Coahuila de Zaragoza", "Coahuila", "mapping file"]], t["lines"]
    # the five specimens are listed once, where they lie
    assert [x[2:] for x in t["decide"]] == [[pc.DIFFERS, "Estado de Chihuahua", "the shape is Chihuahua by its name; the specimens in the shape are filed under Sonora 5", 5]]
    assert t["stats"]["confirmed"] == 0 and t["stats"]["kept"] == 1 and t["stats"]["contradicted"] == 0


def name_world():
    """Nameland with AntWeb's specimens placed in its shapes, and three name lines of a mapping file."""
    work = setup()
    evidence = pc.Evidence(work.countries, work.states)
    for state, shape, count in NAME_SPECIMENS:
        evidence.add("Nameland", state, str(shape // 7 + 0.5), str(60.5 + shape % 7), count)
    shapes = pc.StateShapes(_made["store"], work.states, work.countries, [], evidence, pc.Rules(names=NAME_LINES))
    return pc.Corrections(work.checker, work.states, shapes), evidence


def test_a_name_is_not_thrown_out_by_a_few_specimens():
    work, _ = name_world()
    at = lambda shape: work.work(row("casent0000120", "Nameland", shape // 7 + 0.5, 60.5 + shape % 7))
    inside = lambda name, more="": "coordinates inside " + name + more + ", geoBoundaries NML ADM1"
    P = lambda state, basis: [pc.Proposal("adm1", "", state, basis, "Nameland")]
    # Staging 2026-11-10: six specimens filed under Haute Matsiatra lay in the shape Ihorombe, version 4 took the state away from the shape
    # and 6,986 specimens lost their proposal. Here: six of Golf in the shape Alfa. Alfa stays Alfa.
    assert at(0) == P("Alfa", inside("Alfa"))
    # an exact name stands whatever the specimens say: 27 of Bravo's 30 lie in Zulu. Zulu, the name of no state, is Bravo by them
    assert at(1) == P("Bravo", inside("Bravo")) and at(2) == P("Bravo", inside("Zulu", " (Bravo by the specimens)"))
    # a looser name (without the word Province) is not used against 20 specimens or more: 23 of Charlie's 25 lie in Yankee
    assert at(3) == [pc.Not("adm1", pc.NOT_A_STATE, "Charlie Province", "Nameland")] and at(4) == P("Charlie", inside("Yankee", " (Charlie by the specimens)"))
    # ... and stands against fewer: 7 of Delta's 8 lie in Xray
    assert at(5) == P("Delta", inside("Delta Region", " (Delta by the name without the unit word)")) and at(6)[0].proposed == "Delta"
    # two shapes with the name of one state: the name does not say which, the specimens do. With three specimens both stay
    assert at(7) == [pc.Not("adm1", pc.NOT_A_STATE, "Echo", "Nameland")] and at(8) == P("Echo", inside("Echo"))
    assert at(9) == P("Foxtrot", inside("Foxtrot")) and at(10) == P("Foxtrot", inside("Foxtrot"))
    # a name line of the mapping file is a name like any other: not used when the specimens contradict it (Hotel's 22 lie in Victor),
    # used when nothing says otherwise; a line for a state AntWeb does not have is not used
    assert at(11) == [pc.Not("adm1", pc.NOT_A_STATE, "Whisky", "Nameland")] and at(12) == P("Hotel", inside("Victor", " (Hotel by the specimens)"))
    assert at(13) == P("India", inside("Uniform", " (India by the name the mapping file gives)"))
    t = work.shapes.tables["Nameland"]
    assert [x[2:5] for x in t["lines"]] == [
        ["Alfa", "Alfa", "by name; the name stands, the specimens in the shape are filed under Golf 6 (to_decide.csv)"],
        ["Bravo", "Bravo", "by name; the name stands, the specimens of Bravo lie in Zulu 27; Bravo 3 (to_decide.csv)"],
        ["Zulu", "Bravo", "confirmed by specimens"],
        ["Charlie Province", "Charlie", "name match NOT USED: contradicted by specimens; those of the state lie in Yankee 23; Charlie Province 2"],
        ["Yankee", "Charlie", "confirmed by specimens"],
        ["Delta Region", "Delta", "by name; the name stands, the specimens of Delta lie in Xray 7; Delta Region 1 (to_decide.csv)"],
        ["Xray", "Delta", "confirmed by specimens"],
        ["Echo", "Echo", "name match NOT USED: another shape has the name too; those of the state lie in Echo 10"],
        ["Echo", "Echo", "confirmed by specimens, and by the name"],
        ["Foxtrot", "Foxtrot", "by name only, 3 specimens"], ["Foxtrot", "Foxtrot", "by name only, 3 specimens"],
        ["Whisky", "Hotel", "name match NOT USED: contradicted by specimens; those of the state lie in Victor 22"],
        ["Victor", "Hotel", "confirmed by specimens"],
        ["Uniform", "India", "by name only, 0 specimens"]], t["lines"]
    # for a person: the specimens whose stored state differs from where their coordinates fall, where a name stood against them
    assert [x[2:] for x in t["decide"]] == [
        [pc.DIFFERS, "Alfa", "the shape is Alfa by its name; the specimens in the shape are filed under Golf 6", 6],
        [pc.DIFFERS, "Bravo", "the shape is Bravo by its name; the specimens of Bravo lie in Zulu 27; Bravo 3", 30],
        [pc.DIFFERS, "Delta Region", "the shape is Delta by its name; the specimens of Delta lie in Xray 7; Delta Region 1", 8]], t["decide"]
    stats = t["stats"]
    assert (stats["contradicted"], stats["kept"], stats["confirmed"]) == (3, 3, 5) and t["named"] == ["Uniform -> India"]
    assert pc.level_line("Nameland", t).startswith("  Nameland (NML): ADM1 8/9 AntWeb states, 11/14 shapes [specimens: 5 states confirmed, 3 name matches not used, "
                                                   "3 names standing against specimens, of 6 states with 5 or more located]")
    assert pc.MIN_AGAINST == 20 and pc.MIN_POINTS == 5
    text = pc.report_text("x", "y", "every group", pc.Lists(), work.shapes, _made["store"], "1 second", 1.0)
    assert "  Names of the mapping file (3 \"name\" lines, tested with specimens like any name) that are in use: 1\n  Nameland ADM1: Uniform -> India\n" in text, text


def split_world(states=None):
    work = setup()
    states = states or work.states
    shapes = pc.StateShapes(_made["store"], states, work.countries, [], None, pc.Rules(splits=SPLIT_LINES, within={"Splitland": {"East": "Old", "West": "Old"}}))
    return pc.Corrections(work.checker, states, shapes)


def test_a_shape_split_by_the_units_of_a_finer_level():
    work = split_world()
    at = lambda lat, lon, **more: work.work(row("casent0000130", "Splitland", lat, lon, **more))
    where = "coordinates inside Joined, geoBoundaries SPL ADM1; in its unit "
    # the shape Joined is two states now. A point in the district West-District is West, which AntWeb has
    assert at(1.0, 70.5) == [pc.Proposal("adm1", "", "West", where + "West-District (geoBoundaries SPL ADM2), which the mapping file gives to West", "Splitland")]
    # a point in East District is East, which AntWeb does not have yet: proposed, and marked
    r = at(1.0, 72.5)
    assert r == [pc.Proposal("adm1", "", "East", where + "East District (geoBoundaries SPL ADM2), which the mapping file gives to East, a state to add in the Geolocale Manager first",
                             "Splitland", "", "", "", "state to add in the Geolocale Manager first")], r
    # a point in the shape and in a district the mapping file does not name: no proposal, the district named
    assert at(1.0, 71.5) == [pc.Not("adm1", pc.NO_SPLIT_UNIT, "Joined: in Middle (geoBoundaries SPL ADM2)", "Splitland")]
    # next to the line between the two halves: the other half is the nearest other state, and flagged within the allowed distance
    r = at(1.0, 70.995)
    assert r[0].proposed == "West" and r[0].other == "" and r[0].flag == ""                 # the district next to it has no state
    r = work.work(row("casent0000131", "Splitland", 1.0, 72.995, stated="1 minute"))
    assert (r[0].proposed, r[0].other, r[0].other_km, r[0].flag) == ("East", "Rest", "0.56", "within the stated error (1.85 km) of Rest"), r
    # the shape Rest has lost the district Lost to a new state; every other district stays Rest (the line with *)
    assert at(1.0, 73.5)[0].proposed == "Newland" and at(1.0, 73.5)[0].note == pc.TO_ADD
    r = at(1.0, 74.1)
    assert r == [pc.Proposal("adm1", "", "Rest", "coordinates inside Rest, geoBoundaries SPL ADM1; in its unit Kept (geoBoundaries SPL ADM2), which the mapping file gives to Rest",
                             "Splitland", "Newland", "11", "")], r
    assert at(1.0, 73.995)[0].flag == "within the allowed 1 km of Rest" and at(1.0, 74.005)[0].flag == "within the allowed 1 km of Newland"
    # a shape that is not split is as before
    assert at(1.0, 75.1) == [pc.Proposal("adm1", "", "Plain", "coordinates inside Plain, geoBoundaries SPL ADM1", "Splitland", "Rest", "11", "")]
    t = work.shapes.tables["Splitland"]
    assert t["to_add"] == ["East", "Newland"] and t["level"] == "ADM1" and t["matched"] == ["Joined", "Plain", "Rest"]
    assert [x[2:5] for x in t["lines"]] == [["Joined", "Joined", "mapping file: split by its ADM2 units into East; West"],
                                           ["Rest", "Rest", "mapping file: split by its ADM2 units into Newland; Rest"], ["Plain", "Plain", "by name only, 0 specimens"]]
    assert work.shapes.splits_used == SPLIT_LINES
    # once AntWeb has the new states they are states like any other
    later = split_world(pc.States(STATES + [("East", "Splitland", 1, ""), ("Newland", "Splitland", 1, "")], setup().countries))
    r = later.work(row("casent0000130", "Splitland", 1.0, 72.5))
    assert (r[0].proposed, r[0].note) == ("East", "") and later.shapes.tables["Splitland"]["to_add"] == []
    # the report: the lines used with their notes, the specimens and collecting events of each state, and what is not in AntWeb yet
    lists = pc.Lists()
    records = [row("casent0000132", "Splitland", 1.0, 72.5, event="E1"), row("casent0000133", "Splitland", 1.0, 72.5, event="E1"), row("casent0000134", "Splitland", 1.2, 72.6, event="E2"),
               row("casent0000135", "Splitland", 1.0, 70.5, event="E3"), row("casent0000136", "Splitland", 1.0, 73.5), row("casent0000137", "Splitland", 1.0, 71.5, event="E4")]
    for record in records:
        lists.add(record, work.work(record), work.kind(record))
    text = pc.report_text("x", "y", "every group", lists, work.shapes, _made["store"], "1 second", 1.0)
    top, rest = text.split("\nDetails\n")
    assert "Proposed states not yet in AntWeb, to add in the Geolocale Manager first (specimens in brackets): Splitland: East (3); Splitland: Newland (1)\n" in top, top
    assert "          1  in a shape the mapping file splits, but in none of the units it names\n" in top
    assert ("Shapes told apart by finer units (the split lines of the mapping file that were used):\n"
            "  Splitland, Joined, by ADM2: East District -> East (Law 1 of 2021)\n  Splitland, Joined, by ADM2: West-District -> West (Law 1 of 2021)\n"
            "  Splitland, Rest, by ADM2: Lost -> Newland (Law 2 of 2023)\n  Splitland, Rest, by ADM2: every other unit -> Rest (every other district)\n"
            "  state                              specimens  collecting events  in AntWeb\n"
            "  Splitland: East                            3                  2  NOT YET: proposed state not yet in AntWeb, add it in the Geolocale Manager first\n"
            "  Splitland: West                            1                  1  yes\n"
            "  Splitland: Newland                         1                  1  NOT YET: proposed state not yet in AntWeb, add it in the Geolocale Manager first\n"
            "  Splitland: Rest                            0                  0  yes\n") in rest, rest
    # a mapping file that splits one shape by two levels is refused
    try:
        pc.StateShapes(_made["store"], setup().states, setup().countries, [], None, pc.Rules(splits=SPLIT_LINES + [("Splitland", "Joined", "ADM3", "X", "Y", "")]))
        raise AssertionError("one shape split by two levels")
    except ValueError as e:
        assert "split by two levels, ADM2 and ADM3" in str(e)


def test_madagascar_from_the_provinces_to_the_regions():
    base = setup()
    states = pc.States(MDG_STATES, base.countries)
    evidence = pc.Evidence(base.countries, states)
    for state, lat, lon, count in (("Toliara", -15.0, 47.0, 50), ("Toliara", -24.0, 45.0, 400), ("Antsiranana", -15.0, 47.5, 300), ("Diana", -15.0, 47.0, 6)):
        evidence.add("Madagascar", state, str(lat), str(lon), count)
    shapes = pc.StateShapes(_made["store"], states, base.countries, [], evidence, MDG_RULES)
    work = pc.Corrections(base.checker, states, shapes)
    m = lambda code, lat="", lon="", adm1="", **more: row(code, "Madagascar", lat, lon, adm1=adm1, **more)
    inside = "coordinates inside Diana, geoBoundaries MDG ADM1"
    # the stored province is the province of the region the coordinates lie in
    r = work.work(m("casent0000091", -15.0, 47.0, "Antsiranana"))
    assert r == [pc.Proposal("adm1", "Antsiranana", "Diana", inside, "Madagascar", "", "", "", "the stored province is the province of the region")], r
    # it is not: flagged. The current value is the stored one, an old name too
    flag = "stored province differs from the coordinates' region: the coordinates are followed (Diana is in Antsiranana)"
    assert work.work(m("casent0000092", -15.0, 47.0, "Toliara")) == [pc.Proposal("adm1", "Toliara", "Diana", inside, "Madagascar", "", "", "", flag)]
    assert work.work(m("casent0000093", -15.0, 47.0, "Tulear")) == [pc.Proposal("adm1", "Tulear", "Diana", inside, "Madagascar", "", "", "", flag)]
    # a region the mapping file has no province for: said so, not called a difference
    assert work.work(m("casent0000094", -23.0, 49.0, "Toliara"))[0].note == "the mapping file does not say which province Anosy is in"
    # no coordinates, outside the country: the record stays with the province
    assert work.work(m("casent0000095", adm1="Toliara")) == [pc.Not("adm1", pc.STAYS, "")]
    assert work.work(m("casent0000097", -30.0, 45.0, "Toliara")) == [pc.Not("adm1", pc.OUTSIDE, "in the water, 445 km from Madagascar")]
    # in no region's shape, within the allowed distance of one region alone: the region, as for a record with no state, and the note says so
    # (version 6 left the province: 2,423 specimens of the Academy, all coast points that the table by region counted in a region)
    water, coast = "in the water, 0.51 km from Atsimo-Andrefana, geoBoundaries MDG ADM1", "outside the shape, 0.51 km from it (coast)"
    assert work.work(m("casent0000096", -24.0, 42.995, "Toliara")) == [pc.Proposal("adm1", "Toliara", "Atsimo-Andrefana", water, "Madagascar", "", "", "",
                                                                                 pc.SAME_PROVINCE + "; " + coast)]
    assert work.work(m("casent0000096", -24.0, 42.995)) == [pc.Proposal("adm1", "", "Atsimo-Andrefana", water, "Madagascar", "", "", "", coast)]
    # a missing state gets the region; a record that has a region, another state, or its island country, is left as it is
    assert work.work(m("casent0000098", -15.0, 47.0)) == [pc.Proposal("adm1", "", "Diana", inside, "Madagascar")]
    assert work.work(m("casent0000099", -15.0, 47.0, "Diana")) == [] and work.work(m("casent0000100", -15.0, 47.0, "Nosy Be")) == []
    assert work.work(m("casent0000101", -15.0, 47.0, "Toliara", island="Nosy Be")) == []
    assert work.work(row("casent0000102", "Mexico", 25.0, -108.0, adm1="Toliara")) == []           # another country: no conversion
    assert work.kind(m("x", adm1="Toliara")) == "Madagascar: a province to convert to its region" and work.kind(m("x", adm1="Diana")) == "has a state (left as it is)"
    assert setup().work(m("casent0000103", -15.0, 47.0, "Antsiranana")) == []                      # no "convert" line in the mapping file: nothing
    # the provinces are left out of the test and of the states the shapes are compared with: 450 specimens of Toliara decide nothing
    t = shapes.tables["Madagascar"]
    assert shapes.wanted("Madagascar") == ["Analamanga", "Anosy", "Atsimo-Andrefana", "Diana"] and t["decide"] == [] and t["stats"]["located"] == 6
    assert [x[2:5] for x in t["lines"]] == [["Diana", "Diana", "confirmed by specimens, and by the name"], ["Atsimo-Andrefana", "Atsimo-Andrefana", "by name only, 0 specimens"],
                                           ["Anosy", "Anosy", "by name only, 0 specimens"], ["Analamanga", "Analamanga", "by name only, 0 specimens"]], t["lines"]
    # the case of staging, 2026-11-10: thousands of specimens with a province lie in the shape Diana, and six that are filed under another
    # region. The shape keeps the state of its name, the records with a province get it, and the six are listed for a person
    more = pc.Evidence(base.countries, states)
    for state, count in (("Antsiranana", 5000), ("Toliara", 1900), ("Atsimo-Andrefana", 6)):
        more.add("Madagascar", state, "-15.0", "47.0", count)
    again = pc.Corrections(base.checker, states, pc.StateShapes(_made["store"], states, base.countries, [], more, MDG_RULES))
    assert again.work(m("casent0000091", -15.0, 47.0, "Antsiranana"))[0].proposed == "Diana"
    assert [x[2:5] for x in again.shapes.tables["Madagascar"]["lines"] if x[2] == "Diana"] == [
        ["Diana", "Diana", "by name; the name stands, the specimens in the shape are filed under Atsimo-Andrefana 6 (to_decide.csv)"]]
    assert again.shapes.tables["Madagascar"]["decide"] == [["Madagascar", "ADM1", pc.DIFFERS, "Diana",
                                                            "the shape is Diana by its name; the specimens in the shape are filed under Atsimo-Andrefana 6", 6]]
    # the lists: a line for each collecting event and current value; the report has the conversion apart
    lists = pc.Lists()
    records = [m("casent0000091", -15.0, 47.0, "Antsiranana", event="E1"), m("casent0000092", -15.0, 47.0, "Toliara", event="E1"),
               m("casent0000104", -15.0, 47.0, "Toliara", event="E1"), m("casent0000098", -15.0, 47.0, event="E1"), m("casent0000095", adm1="Toliara", event="E2"),
               m("casent0000105", -15.0, 47.0, "Antsiranana", event="E3"), m("casent0000106", -15.0, 47.0, "Fianarantsoa", event="E3")]
    for record in records:
        lists.add(record, work.work(record), work.kind(record))
    assert [(e[0], e[2], e[3], e[4], e[14]) for e in lists.events()] == [
        ("E1", "", "Diana", 1, ""), ("E1", "Antsiranana", "Diana", 1, "the stored province is the province of the region"), ("E1", "Toliara", "Diana", 2, flag),
        ("E3", "Antsiranana", "Diana", 1, "the stored province is the province of the region"),
        ("E3", "Fianarantsoa", "Diana", 1, flag)], lists.events()
    assert [c[4:6] + [c[10]] for c in lists.corrections() if c[0] == "casent0000092"] == [["Toliara", "Diana", flag]]
    assert [(x[0], x[2], x[8]) for x in lists.not_derivable()] == [("E2", pc.STAYS, "Toliara")]
    text = pc.report_text("x", "y", "every group", lists, shapes, _made["store"], "1 second", 1.0, pc.MAP_FILE, None, None, evidence)
    top = text.split("\nDetails\n")[0]
    assert ("          6  Madagascar: a province to convert to its region\n          1  state missing\n") in top
    assert ("State (adm1): 1 specimens with a proposal (corrections.csv), in 1 lines of by_collecting_event.csv\n") in top
    assert ("Madagascar (adm1), from a province to its region: 5 specimens with a proposal (corrections.csv), in 4 lines of by_collecting_event.csv\n"
            "  For information: 0 of them (0 lines) lie within the stated error, or the allowed 1 km, of another region (column other_state_flag).\n"
            "  For information: 3 of them have a stored province that is not the province of the coordinates' region (column note; stored_differs.csv). By region: Diana 3.\n"
            "  No proposal (not_derivable.csv): 1 specimens, by reason:\n          1  no coordinates: stays with the province\n") in top, top
    assert "Madagascar (adm1), from a province to its region, proposed values (the 40 largest):\n          2  Madagascar: Antsiranana -> Diana\n          2  Madagascar: Toliara -> Diana\n          1  Madagascar: Fianarantsoa -> Diana\n" in text
    # the statement that reads the records: the provinces of Madagascar, by the stored spellings of the country
    sql = pc.rows_sql({"code", "country", "adm1"}, 1, [], [(["Madagascar"], ["Toliara", "Tulear"])])
    assert " or (hex(s.country) in ('4D616461676173636172') and s.adm1 in ('Toliara', 'Tulear'))" in sql and cc.read_only(sql) == sql


# Madagascar as AntWeb has it now: the provinces and the regions, both valid; and what the mapping file says of them
MDG_STATES = [(name, "Madagascar", 1, "") for name in ("Antananarivo", "Antsiranana", "Fianarantsoa", "Toliara", "Diana", "Atsimo-Andrefana", "Anosy", "Analamanga")] + [
    ("Tulear", "Madagascar", 0, "Toliara")]
MDG_RULES = pc.Rules(convert={"Madagascar": ["Antananarivo", "Antsiranana", "Fianarantsoa", "Mahajanga", "Toliara"]},
                     within={"Madagascar": {"Diana": "Antsiranana", "Atsimo-Andrefana": "Toliara", "Analamanga": "Antananarivo"}})


# Every record of Madagascar: (collecting event, locality name, stored state, island country, latitude, longitude, stated error, specimens, first specimen)
MDG_RECORDS = [
    ("E1", "Montagne", "Antsiranana", "", "-15.0", "47.0", "", 10, "casent0000201"), ("E1", "Montagne", "", "", "-15.0", "47.0", "", 2, "casent0000211"),
    ("E2", "Foret", "Toliara", "", "-15.0", "47.0", "", 1, "casent0000213"), ("E3", "Sud", "Tulear", "", "-24.0", "45.0", "", 5, "casent0000214"),
    ("", "", "", "", "", "", "", 7, "casent0000219"), ("", "", "", "", "0.0", "0.0", "", 1, "casent0000226"), ("E4", "En mer", "", "", "-30.0", "45.0", "", 3, "casent0000227"),
    ("E5", "Tana", "Antananarivo", "", "-20.0", "49.0", "", 4, "casent0000230"), ("", "", "", "", "NULL", "NULL", "", 2, "casent0000234"),
    ("E6", "Plage", "Toliara", "", "-24.0", "42.995", "", 12, "casent0000236"),            # in the water, 0.51 km from Atsimo-Andrefana
    ("E7", "Diana", "Diana", "", "-15.0", "47.0", "", 6, "casent0000248"), ("E8", "Nosy Be", "Nosy Be", "", "-15.0", "47.0", "", 1, "casent0000254"),
    ("E9", "Anosy", "Toliara", "", "-23.0", "49.0", "", 2, "casent0000255"),
    ("E10", "Recif", "Toliara", "", "-24.0", "42.985", "1 minute", 9, "casent0000257"),    # in the water, 1.52 km out: within the stated error of 1.85 km
    ("E11", "Recif", "Toliara", "", "-24.0", "42.985", "", 8, "casent0000266"),            # the same place with no stated error: outside the country
    ("E12", "Sud", "Antsiranana", "", "-24.0", "45.0", "", 3, "casent0000274"),            # the province of the far north on a point of the south
    ("E13", "Ile", "Toliara", "Nosy Be", "-15.0", "47.0", "", 2, "casent0000277")]         # an island-country specimen: no proposal, counted by its place


def test_madagascar_by_region():
    base = setup()
    C = cc.Countries([cc.Country("Madagascar", 1, "", 0, "")])
    six = [(name, "Madagascar", 1, "") for name in ("Antananarivo", "Antsiranana", "Fianarantsoa", "Mahajanga", "Toamasina", "Toliara")]
    assert pc.provinces_in_antweb(pc.States(six, C)) and not pc.provinces_in_antweb(pc.States(six[:3] + [("Diana", "Madagascar", 1, "")], C))
    assert not pc.provinces_in_antweb(base.states)                # the test world has two of them
    # AntWeb with provinces and regions; the mapping file says which province a region is in
    states = pc.States(MDG_STATES, base.countries)
    work = pc.Corrections(base.checker, states, pc.StateShapes(_made["store"], states, base.countries, [], None, MDG_RULES))
    # the same records, one by one, through the proposals
    lists, number = pc.Lists(), 0
    for event, locality, adm1, island, lat, lon, stated, count, first in MDG_RECORDS:
        for _ in range(count):
            number += 1
            record = row("casent%07d" % (200 + number), "Madagascar", "" if lat == "NULL" else lat, "" if lon == "NULL" else lon, adm1=adm1, island=island, event=event,
                         stated=stated, locality=locality)
            lists.add(record, work.work(record), work.kind(record))
    lines, differs = pc.madagascar_regions(work, "MDG", MDG_RECORDS, "California Academy of Sciences (id 1)", lists)
    assert lines[:8] == ["", "Madagascar by region: every record of Madagascar of the group by the region its coordinates lie in, not only the records with a gap; "
                         "geoBoundaries MDG ADM1, 4 regions.",
                         "  Each point is placed by the function that makes the proposals: inside one region's shape, or in no shape and within the allowed distance (1 km, or the",
                         "  stated error when larger) of one region alone and of no other country.",
                         "  Records of Madagascar: 78. Placed in a region: 57 (21 of them outside its shape, within the allowed distance of it alone; 0 on an islet the "
                         "country outline leaves out). No coordinates: 9. Coordinates that cannot be used: 1.",
                         "  With coordinates and not placed: 11, by reason: 11 coordinates outside the stated country.",
                         "  Records with a province that are placed in a region: 46; proposals from a province to its region in this run: 46 (the same).",
                         "  stored_differs.csv: 5 lines, 9 specimens whose stored province or region is not that of their coordinates (the column \"another\"), "
                         "by collecting event. For information: the coordinates are followed."], lines[:8]
    assert lines[8].split() == ["region", "specimens", "outside", "shape", "its", "province", "stored:", "its", "province", "the", "region", "another", "none"]
    cells = [line.split() for line in lines[9:]]
    # Diana: 22 placed; 10 have its province, 6 the region itself, 4 another state (Toliara twice, a name AntWeb does not know), 2 none.
    # Atsimo-Andrefana: 21 of its 29 lie outside its shape, within the allowed distance. Anosy's province is not in the mapping file of this test.
    assert cells == [["Analamanga", "4", "0", "Antananarivo", "4", "0", "0", "0"], ["Anosy", "2", "0", "not", "in", "the", "mapping", "file", "0", "0", "2", "0"],
                     ["Atsimo-Andrefana", "29", "21", "Toliara", "26", "0", "3", "0"], ["Diana", "22", "0", "Antsiranana", "10", "6", "4", "2"]], cells
    # the two counts are the same because both ask the one function: for every record, a proposal exactly where the table places it
    proposed = collections.Counter(p.proposed for _, p in lists.proposals if p.field == "adm1")
    assert proposed == {"Analamanga": 4, "Anosy": 2, "Atsimo-Andrefana": 29, "Diana": 13}           # Diana: all but the 6 that have the region and the 3 left as they are
    assert collections.Counter(n.reason for _, n in lists.nots) == {pc.OUTSIDE: 11, pc.NO_COORDINATES: 10}, lists.nots       # 9 with none, 1 with 0, 0
    for event, locality, adm1, island, lat, lon, stated, count, first in MDG_RECORDS:
        if lat not in ("", "NULL") and cc.usable(float(lat), float(lon)):
            placed = work.locate("Madagascar", "Madagascar", float(lat), float(lon), stated)
            got = work.work(row(first, "Madagascar", lat, lon, event=event, stated=stated))            # the record as if it had no state
            assert [(p.proposed, p.basis) for p in got if isinstance(p, pc.Proposal)] == ([(placed.at.state, placed.at.basis)] if placed.stop is None else []), (event, got, placed)
            assert [x for x in got if isinstance(x, pc.Not)] == ([placed.stop] if placed.stop is not None else [])
    # stored_differs.csv: the records whose stored state is neither the region of their coordinates nor its province, by collecting event
    assert pc.DIFFERS_HEADER[:11] == ["collecting_event_code", "locality_name", "specimens", "first_specimen_code", "stored_value", "stored_value_is", "region_by_coordinates",
                                      "province_of_that_region", "km_to_the_stored_value", "nearest_other_region", "km_to_the_nearest_border"]
    inside = "coordinates inside Diana, geoBoundaries MDG ADM1"
    assert differs == [
        ["E9", "Anosy", 2, "casent0000255", "Toliara", "a province", "Anosy", "", "205", "Analamanga", "111", "-23.0", "49.0", "", "coordinates inside Anosy, geoBoundaries MDG ADM1",
         "", "Madagascar"],
        # 556 km from the nearest region of the stored province: not a matter of where the line runs
        ["E12", "Sud", 3, "casent0000274", "Antsiranana", "a province", "Atsimo-Andrefana", "Toliara", "556", "Anosy", "203", "-24.0", "45.0", "",
         "coordinates inside Atsimo-Andrefana, geoBoundaries MDG ADM1", "", "Madagascar"],
        ["E8", "Nosy Be", 1, "casent0000254", "Nosy Be", "no province or region of Madagascar in AntWeb", "Diana", "Antsiranana", "", "Analamanga", "445", "-15.0", "47.0", "", inside,
         "", "Madagascar"],
        ["E13", "Ile", 2, "casent0000277", "Toliara", "a province", "Diana", "Antsiranana", "445", "Analamanga", "445", "-15.0", "47.0", "", inside, "", "Madagascar"],
        ["E2", "Foret", 1, "casent0000213", "Toliara", "a province", "Diana", "Antsiranana", "445", "Analamanga", "445", "-15.0", "47.0", "", inside, "", "Madagascar"]], differs
    assert sum(x[2] for x in differs) == sum(int(c[-2]) for c in cells)
    sql = pc.madagascar_sql({"code", "collectioncode", "localityname", "island_country", "latlonmaxerror"}, ["Madagascar", "MADAGASCAR "], 1)
    assert cc.read_only(sql) == sql and sql.endswith(" group by 1, 2, 3, 4, 5, 6, 7") and "access_group = 1" in sql and "4D4144414741534341522" in sql and "Madagascar" not in sql
    assert sql.startswith("select hex(ifnull(s.collectioncode, '')), hex(ifnull(s.localityname, '')), hex(ifnull(s.adm1, '')), hex(ifnull(s.island_country, '')), ")
    assert "hex(ifnull(s.latlonmaxerror, '')), count(*), hex(min(s.code)) from specimen s" in sql
    assert pc.madagascar_sql({"code"}, ["Madagascar"]).startswith("select '', '', hex(ifnull(s.adm1, '')), '', ")
    text = pc.report_text("x", "y", "every group", pc.Lists(), base.shapes, _made["store"], "1 second", 1.0, pc.MAP_FILE, None, lines)
    assert "\nMadagascar by region: every record" in text and text.index("Madagascar by region") > text.index("\nDetails\n")
    # the summary says how many proposals lie outside the shape of their state
    text = pc.report_text("x", "y", "every group", lists, work.shapes, _made["store"], "1 second", 1.0)
    # Madagascar's six old units are provinces, its current ones regions: no line of the report on Madagascar calls either a state
    assert pc.in_regions("Madagascar", "AntWeb states matched; no state in AntWeb; the stated error") == "AntWeb regions matched; no region in AntWeb; the stated error"
    assert pc.in_regions("Mexico", "AntWeb states matched") == "AntWeb states matched"
    about = [line for line in lines + text.split("\n") if "Madagascar" in line or "province" in line or "region" in line]
    assert len(about) > 25 and not [line for line in about if cc.re.search(r"\bstates?\b", line) and not line.startswith(("  1. ", "Records looked at", "AntWeb has two"))], about
    assert "  Madagascar (MDG): ADM1 4/4 AntWeb regions, 4/4 shapes" in text and "    AntWeb regions matched (4): Analamanga; Anosy; Atsimo-Andrefana; Diana" in text
    assert ("  For information: 21 of them lie outside the region's shape, within the allowed distance of it alone (column note: outside the shape, N km from it (coast)); "
            "0 on an islet the country outline leaves out (column note).\n") in text.split("\nDetails\n")[0], text


def test_an_islet_the_country_shape_leaves_out():
    base = setup()
    new = [("Isleland", "ADM1", "Outer", "Outer Islands")]
    work = pc.Corrections(base.checker, base.states, pc.StateShapes(_made["store"], base.states, base.countries, new, None, pc.Rules(new=new)))
    at = lambda lat, lon, **more: work.work(row("casent0000301", "Isleland", lat, lon, **more))
    left = pc.LEFT_OUT + " (17 km from Isleland as Natural Earth 1:10m draws it)"
    # on the mainland: as ever
    assert at(1.0, 81.0) == [pc.Proposal("adm1", "", "Mainland", "coordinates inside Mainland, geoBoundaries ILE ADM1", "Isleland")]
    # on the islet the country's shape leaves out, 17 km in the water by that shape (17 km): the state whose shape holds the point, and the note says so
    r = at(0.55, 82.15)
    assert r == [pc.Proposal("adm1", "", "Mainland", "coordinates inside Mainland, geoBoundaries ILE ADM1", "Isleland", "", "", "", left)], r
    # the same on an islet of a state AntWeb does not have yet: both notes
    r = at(1.6, 82.2)
    assert (r[0].proposed, r[0].note) == ("Outer Islands", pc.TO_ADD + "; " + left.replace("17 km", "22 km")), r
    # half a kilometre off the islet: the rule of the coast holds there as on the mainland, and both notes are given
    r = at(0.55, 82.205)
    assert (r[0].proposed, r[0].basis, r[0].note) == ("Mainland", "in the water, 0.56 km from Mainland, geoBoundaries ILE ADM1",
                                                      "outside the shape, 0.56 km from it (coast); " + left.replace("17 km", "23 km")), r
    # in the water between the islets, far from every state shape: outside the country, as before
    assert at(1.0, 82.15) == [pc.Not("adm1", pc.OUTSIDE, "in the water, 17 km from Isleland")]
    # a point the country shapes put inside another country is not asked of the states' file at all
    assert setup().work(row("casent0000302", "Mexico", 35.0, -108.0)) == [pc.Not("adm1", pc.OUTSIDE, "United States, 556 km from Mexico")]
    # the load file for TaxonWorks has it like any other proposal, counted for information; --tw-hold islet keeps it out
    with tempfile.TemporaryDirectory() as folder:
        path = os.path.join(folder, "data.tsv")
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.write("\t".join(TW_HEAD) + "\n" + "\t".join(["1", "CASENT1", "Isleland", "E90", "0.55", "82.15", "", "", "", "Isleland", "190"]) + "\n")
        lists = pc.Lists()
        record = row("casent0000301", "Isleland", 0.55, 82.15, event="E90")
        lists.add(record, work.work(record), work.kind(record))
        stored = {"E90": [("Isleland", "", "", "", 1, "casent0000301", "0.55", "82.15")]}
        load, _, unresolved, counts = pc.tw_load(pc.TwArchive(path), lists, stored, base.countries, base.states, set())
        assert load == [["E90", "adm1", "create", "", "Mainland", "0.55", "82.15", "190"]] and unresolved == [] and counts["info"]["islet"] == 1
        load, _, unresolved, _ = pc.tw_load(pc.TwArchive(path), lists, stored, base.countries, base.states, set(), "near-border")
        assert len(load) == 1 and unresolved == []                # near-border is another matter
        load, _, unresolved, _ = pc.tw_load(pc.TwArchive(path), lists, stored, base.countries, base.states, set(), "islet")
        assert load == [] and unresolved == [["E90", "190", "adm1", "Mainland", pc.LEFT_OUT, 1, "casent0000301", "0.55", "82.15"]], (load, unresolved)
    # the report: the summary counts them; for the state to add, what the Geolocale Manager asks for, from the shape
    lists = pc.Lists()
    for i, (lat, lon) in enumerate([(0.55, 82.15), (1.6, 82.2), (0.95, 82.55), (1.0, 81.0)]):
        record = row("casent%07d" % (310 + i), "Isleland", lat, lon, event="E9%d" % i)
        lists.add(record, work.work(record), work.kind(record))
    text = pc.report_text("x", "y", "every group", lists, work.shapes, _made["store"], "1 second", 1.0)
    top, rest = text.split("\nDetails\n")
    assert ("State (adm1): 4 specimens with a proposal (corrections.csv), in 4 lines of by_collecting_event.csv\n"
            "  For information: 0 of them (0 lines) lie within the stated error, or the allowed 1 km, of another state (column other_state_flag).\n"
            "  For information: 0 of them lie outside the state's shape, within the allowed distance of it alone (column note: outside the shape, N km from it (coast)); "
            "3 on an islet the country outline leaves out (column note).\n") in top, top
    # the rule stands at the top of the report, in plain words
    assert top.split("\n")[3:9] == [
        "The rule (Brian Fisher, 2026-11-10: follow the coordinates):",
        "  1. A record with coordinates gets the state its coordinates fall in. Nothing else is weighed: not the stored province, not how near a border is.",
        "  2. A point just off the coast (in no state's shape, within 1 km or the stated error of exactly one state, no other country that near) gets that state. "
        "An islet the country outline leaves out but the states' file draws gets its state.",
        "  3. No proposal only where the rule cannot give one answer: no coordinates; the point lies in another country or far at sea; outside every shape and within reach of "
        "two states; the country has no states or no shapes, or the shape is no state of AntWeb yet (those are listed for the Geolocale Manager).",
        "  4. The state's name is the map's current name; where AntWeb has that name as a valid state it is used; other spellings are for AntWeb's synonym list.",
        "  5. TaxonWorks gets the same values."], top
    # the centroid of the two islets lies in the water between them: the centre is a point inside the larger part
    assert ("For each of them, what the Geolocale Manager asks for, measured on the shape of the source (to type as they stand):\n"
            "  Isleland: Outer Islands (2 specimens)\n"
            "    Centroid (lat, lon): 1.6000, 82.2000      a point inside the largest part (the centroid of the shape lies outside it)\n"
            "    Bounding box (west lon, south lat, east lon, north lat): 82.1000, 0.9000, 82.6000, 1.7000      latitude 0.9000 to 1.7000, longitude 82.1000 to 82.6000\n"
            "    from geoBoundaries ILE ADM1, shape Outer; 2 rings\n") in rest, rest
    # a state that a split line makes of finer units: measured on those units
    split = split_world()
    split.work(row("casent0000130", "Splitland", 1.0, 72.5))
    found = {state: (box, where, unit) for state, box, where, unit in split.shapes.tables["Splitland"]["new_states"]}
    assert sorted(found) == ["East", "Newland"] and found["East"][1:] == ("geoBoundaries SPL ADM2, units East District", "")
    box = found["East"][0]
    assert [round(x, 4) for x in box[:6]] == [0.0, 2.0, 72.0, 73.0, 1.0, 72.5] and box.centre == "the centroid of the shape" and box.parts == 1


def test_the_outline_of_a_shape():
    close = lambda box, values: all(abs(a - b) < 1e-6 for a, b in zip(box[:6], values))
    # a square: its box, and its centroid as the centre
    box = pc.outline([square(10, 20, 12, 21)])
    assert close(box, (20, 21, 10, 12, 20.5, 11)) and box.centre == "the centroid of the shape" and box.parts == 1
    # a ring inside another is a hole: a square with a hole in its east half has its centroid west of the middle
    box = pc.outline([square(0, 0, 4, 2), square(2.5, 0.5, 3.5, 1.5)])
    assert box.centre == "the centroid of the shape" and abs(box.lat - 1.0) < 1e-3 and 1.8 < box.lon < 1.9, box
    # ... and a square with a hole in its middle has its centroid in the hole: the centre is put inside the shape
    box = pc.outline([square(0, 0, 4, 2), square(1, 0.5, 3, 1.5)])
    assert box.centre.startswith("a point inside the largest part") and abs(box.lat - 1.0) < 1e-3 and (0 < box.lon < 1 or 3 < box.lon < 4), box
    # a crescent (a C): the centroid lies in its opening
    c = [[0, 0], [4, 0], [4, 1], [1, 1], [1, 3], [4, 3], [4, 4], [0, 4], [0, 0]]
    box = pc.outline([c])
    assert box.centre.startswith("a point inside the largest part") and pc.holds(c, box.lon, box.lat) and close(box, (0, 4, 0, 4, box.lat, box.lon)), box
    # a shape across the 180 meridian: measured in one piece, west larger than east
    box = pc.outline([square(179, -17, 180, -16), square(-180, -17, -178, -16)])
    assert close(box, (-17, -16, 179, -178, -16.5, -179.5)) and box.centre == "the centroid of the shape", box
    assert pc.outline([]) is None and pc.outline([[[0, 0], [1, 1]]]) is None
    # every source gives the rings it builds its index from
    base = setup()
    units, rings = pc.GeoSource(_made["store"]).rings("ILE", "ADM1")
    assert [u.name for u in units] == ["Mainland", "Outer"] and [len(r) for r in rings] == [2, 2]


# ---------------------------------------------------------------- OCHA's files, a third source of state shapes

def ocha_feature(properties, polygons):
    return {"type": "Feature", "properties": properties, "geometry": {"type": "MultiPolygon", "coordinates": [[p] for p in polygons]} if len(polygons) > 1
            else {"type": "Polygon", "coordinates": polygons}}


def ocha_site(folder, version="v01", districts_as_drawn=True):
    """A folder that stands for HDX: the answer of its API for Splitland's dataset and the zip it names (two levels,
    P-codes, a layer of lines beside them). Splitland has four regions here: East and West are shapes of their own.
    Returns the template of the API's address."""
    os.makedirs(os.path.join(folder, "api"), exist_ok=True)
    common = {"adm0_name": "Splitland", "adm0_pcode": "SP", "version": version, "valid_on": "2026-07-06", "valid_to": None}
    regions = [("East", "SP01", [square(72, 0, 73, 2)]), ("West", "SP02", [square(70, 0, 72, 2)]), ("Rest Region", "SP03", [square(74, 0, 75, 2)]),
               ("Newland", "SP04", [square(73, 0, 74, 2)]), ("Plain", "SP05", [square(75, 0, 76, 1), square(75, 1, 76, 2)])]
    districts = [("East District", "SP0101", "SP01", square(72, 0, 73, 2)), ("West-District", "SP0201", "SP02", square(70, 0, 71, 2)), ("Middle", "SP0202", "SP02", square(71, 0, 72, 2)),
                 ("Lost", "SP0401", "SP04", square(73, 0, 74, 2) if districts_as_drawn else square(73, 0, 73.8, 2)),
                 ("Kept", "SP0301", "SP03", square(74, 0, 75, 2) if districts_as_drawn else square(73.8, 0, 75, 2)),
                 ("Flat", "SP0501", "SP05", square(75, 0, 76, 2))]
    layers = {
        "spl_admin_boundaries/spl_admin0.geojson": [ocha_feature(dict(common), [square(70, 0, 76, 2)])],
        "spl_admin_boundaries/spl_admin1.geojson": [ocha_feature(dict(common, adm1_name=name, adm1_name1="R\u00e9gion " + name, adm1_pcode=code, area_sqkm=1.0), polygons)
                                                    for name, code, polygons in regions],
        "spl_admin_boundaries/spl_admin2.geojson": [ocha_feature(dict(common, adm2_name=name, adm2_pcode=code, adm1_name="x", adm1_pcode=parent), [ring])
                                                    for name, code, parent, ring in districts],
        "spl_admin_boundaries/spl_adminlines.geojson": [{"type": "Feature", "properties": {"adm1_pcode": "SP01"}, "geometry": {"type": "LineString", "coordinates": [[70, 0], [76, 0]]}}],
    }
    with zipfile.ZipFile(os.path.join(folder, "spl_admin_boundaries.geojson.zip"), "w", zipfile.ZIP_DEFLATED) as z:
        for member, features in layers.items():
            z.writestr(member, json.dumps({"type": "FeatureCollection", "name": os.path.basename(member), "features": features}))
        z.writestr("spl_admin_boundaries/readme.txt", "made up")
    answer = {"success": True, "result": {"name": "cod-ab-spl", "last_modified": "2026-08-13T10:00:00", "review_date": "2026-07-06T00:00:00", "dataset_source": "Splitland's office",
                                          "license_title": "Creative Commons Attribution for Intergovernmental Organisations", "num_resources": 2,
                                          "resources": [{"name": "spl_admin_boundaries.shp.zip", "format": "SHP", "url": "file://" + folder + "/none.shp.zip"},
                                                        {"name": "spl_admin_boundaries.geojson.zip", "format": "GeoJSON", "last_modified": "2026-08-13T10:00:00" + version,
                                                         "url": "file://" + folder + "/spl_admin_boundaries.geojson.zip"}]}}
    with open(os.path.join(folder, "api", "cod-ab-spl.json"), "w", encoding="utf-8") as f:
        json.dump(answer, f)
    return "file://" + folder + "/api/cod-ab-{iso}.json"


def test_the_ocha_files_from_hdx():
    with tempfile.TemporaryDirectory() as folder:
        api = ocha_site(os.path.join(folder, "hdx"))
        cache = os.path.join(folder, "shapes")
        ocha = pc.OchaSource(cache, api)
        # the dataset of the country is asked for at HDX, its GeoJSON file fetched once into the shapes folder under a name of our own
        path = ocha.file("SPL")
        assert path == os.path.join(cache, "ocha-cod-ab-spl.geojson.zip") and os.path.exists(path) and pc.OCHA_FILE == "ocha-cod-ab-{iso}.geojson.zip"
        assert pc.OCHA_API == "https://data.humdata.org/api/3/action/package_show?id=cod-ab-{iso}" and "Mozilla/5.0" in pc.OCHA_AGENT and "place_corrections.py" in pc.OCHA_AGENT
        # the layout of the zip is read from the file: the polygons of each level, not the lines; the name and the P-code of each unit
        assert ocha.layers("SPL") == {0: "spl_admin_boundaries/spl_admin0.geojson", 1: "spl_admin_boundaries/spl_admin1.geojson", 2: "spl_admin_boundaries/spl_admin2.geojson"}
        assert ocha.has("SPL", "ADM1") and ocha.has("SPL", "ADM2") and not ocha.has("SPL", "ADM3") and ocha.levels == ["ADM1", "ADM2"]
        units = ocha.names("SPL", "ADM1")
        assert units[0] == pc.Unit("East", ("R\u00e9gion East",), "", "", "SP01") and [u.pcode for u in units] == ["SP01", "SP02", "SP03", "SP04", "SP05"]
        units, index = ocha.shapes("SPL", "ADM2")
        assert [u.name for u in units] == ["East District", "West-District", "Middle", "Lost", "Kept", "Flat"] and units[3].pcode == "SP0401"
        assert [units[i].name for i in index.inside(1.0, 73.5)] == ["Lost"] and ocha.below("SPL", "ADM1") == ("ADM2", "") and ocha.below("SPL", "ADM2") == (None, "no layer of ADM3")
        # OCHA's older files call the properties ADM1_EN, ADM1_FR, ADM1_PCODE; a name in two languages is two spellings
        old = pc.OchaSource.unit({"ADM1_EN": "North", "ADM1_FR": "Nord", "ADM1_PCODE": "XX01", "ADM1_REF": "Northern", "ADM0_EN": "X", "ADM0_PCODE": "XX", "ADM2_EN": "no"}, 1)
        assert old == pc.Unit("North", ("Nord", "Northern"), "", "", "XX01") and pc.OchaSource.unit({"adm2_name": "x"}, 1) is None
        # the record for the report: HDX has no pinned release, so the file's checksum, its dates and its version are written down
        with open(path, "rb") as f:
            digest = hashlib.sha256(f.read()).hexdigest()
        text = ocha.report()
        assert text[0] == ("  SPL: ocha-cod-ab-spl.geojson.zip, " + cc.n(os.path.getsize(path)) + " bytes, sha256 " + digest + "; first seen; last modified on HDX 2026-08-13T10:00:00v01"), text
        assert text[1] == ("    dataset_source Splitland's office; last_modified 2026-08-13T10:00:00; license_title Creative Commons Attribution for Intergovernmental Organisations; "
                           "review_date 2026-07-06T00:00:00; valid_on 2026-07-06; version v01"), text
        assert text[2] == "    layers: level 0 spl_admin_boundaries/spl_admin0.geojson; level 1 spl_admin_boundaries/spl_admin1.geojson; level 2 spl_admin_boundaries/spl_admin2.geojson"
        # the next run: HDX says the file is unchanged, it is not fetched again (the file at HDX is taken away to show it); the same checksum
        os.rename(os.path.join(folder, "hdx", "spl_admin_boundaries.geojson.zip"), os.path.join(folder, "hdx", "kept.zip"))
        again = pc.OchaSource(cache, api)
        assert again.file("SPL") == path and again.report()[0].endswith("sha256 " + digest + "; the same file as on " + __import__("datetime").date.today().isoformat()
                                                                        + "; last modified on HDX 2026-08-13T10:00:00v01")
        # a new release at HDX: fetched again, and the report says that the checksum changed
        ocha_site(os.path.join(folder, "hdx"), "v02")
        newer = pc.OchaSource(cache, api)
        newer.file("SPL")
        assert "; NEW RELEASE: the checksum was " + digest + " on " in newer.report()[0] and "version v02" in "\n".join(newer.report() + [str(newer.names("SPL", "ADM1"))] + newer.report())
        # a country HDX has no dataset of: no file, and the reason; a country not asked for is not asked at HDX
        assert pc.OchaSource(cache, api, only=["spl"]).file("MEX") is None
        # HDX refuses, or cannot be reached, and no file is in the folder: a plain message that says what to do by hand
        empty = os.path.join(folder, "empty")
        refused = pc.OchaSource(empty, "file://" + folder + "/nowhere/cod-ab-{iso}.json")
        try:
            refused.file("MDG")
            raise AssertionError("no file and no HDX")
        except pc.Refused as e:
            message = str(e)
        assert message.startswith("OCHA's boundaries of MDG could not be fetched from HDX (") and (
            "Download the GeoJSON file (mdg_admin_boundaries.geojson.zip) of https://data.humdata.org/dataset/cod-ab-mdg in a browser and put it on the server as "
            + os.path.join(empty, "ocha-cod-ab-mdg.geojson.zip") + " ; the next run uses it. Or run with --ignore-source-lines to do without OCHA's files.") in message, message
        # ... and with the file put there by hand it is used, and the report says so
        os.makedirs(empty)
        with open(os.path.join(folder, "hdx", "spl_admin_boundaries.geojson.zip"), "rb") as f, open(os.path.join(empty, "ocha-cod-ab-spl.geojson.zip"), "wb") as g:
            g.write(f.read())
        by_hand = pc.OchaSource(empty, "file://" + folder + "/nowhere/cod-ab-{iso}.json")
        assert [u.name for u in by_hand.names("SPL", "ADM1")][:2] == ["East", "West"]
        assert "; first seen; put into the folder by hand or kept from an earlier run; HDX could not be asked (" in by_hand.report()[0] and by_hand.report()[0].endswith("the file in the folder was used")
        # a file that is no zip is refused with the same advice
        with open(os.path.join(empty, "ocha-cod-ab-mdg.geojson.zip"), "w") as f:
            f.write("<html>refused</html>")
        try:
            pc.OchaSource(empty, "file://" + folder + "/nowhere/cod-ab-{iso}.json").file("MDG")
            raise AssertionError("a file that is no zip")
        except pc.Refused as e:
            assert "is not a zip file" in str(e) and "Download the GeoJSON file" in str(e)
    # the features of a large file are read one at a time, whatever the size of the pieces it comes in
    data = json.dumps({"type": "FeatureCollection", "crs": {"x": [1, 2]}, "features": [ocha_feature({"adm1_name": "N\u00famero %d" % i, "n": i}, [square(i, 0, i + 1, 1)]) for i in range(40)]})
    for block in (7, 64, 1 << 20):
        got = list(pc.geojson_features(io.BytesIO(data.encode("utf-8")), block))
        assert len(got) == 40 and got[39]["properties"] == {"adm1_name": "N\u00famero 39", "n": 39} and got[0]["geometry"]["type"] == "Polygon", block
    assert list(pc.geojson_features(io.BytesIO(b'{"type": "FeatureCollection", "features": []}'))) == [] and list(pc.geojson_features(io.BytesIO(b"{}"))) == []
    assert len(list(pc.geojson_features(io.BytesIO(data.encode("utf-8")[:len(data) // 2])))) in range(15, 25)       # a file cut short gives what is whole
    # a detailed line is thinned: points closer than 50 m to the last one kept are left out, the ends stay
    ring = [(0.0, 0.0)] + [(0.0001 * i, 0.0) for i in range(1, 100)] + [(0.01, 0.01), (0.0, 0.01), (0.0, 0.0)]
    assert len(pc.thin(ring)) < 30 and pc.thin(ring)[0] == pc.thin(ring)[-1] == (0.0, 0.0) and pc.thin(ring[:4]) == ring[:4]


def test_a_country_from_ocha_and_the_three_sources():
    with tempfile.TemporaryDirectory() as folder:
        api = ocha_site(os.path.join(folder, "hdx"))
        base = setup()
        states = pc.States(STATES + [("Old", "Splitland", 1, "")], base.countries)
        ocha = pc.OchaSource(os.path.join(folder, "shapes"), api)
        geo = pc.GeoSource(_made["store"])
        rules = pc.Rules(splits=SPLIT_LINES, sources={"Splitland": "ocha"}, convert={"Splitland": ["Old"]}, within={"Splitland": {"West": "Old", "East": "Old"}})
        rows = [("Splitland", "ADM1", "East", "East"), ("Splitland", "ADM1", "Newland", "Newland"), ("Splitland", "ADM1", "Rest Region", "Rest")]
        rules.new = {rows[0], rows[1]}
        shapes = pc.StateShapes(_made["store"], states, base.countries, rows, None, rules, geo, {"geoboundaries": geo, "naturalearth": None, "ocha": ocha}, {"Splitland"})
        work = pc.Corrections(base.checker, states, shapes)
        at = lambda lat, lon, **more: work.work(row("casent0000140", "Splitland", lat, lon, **more))
        # the mapping file takes Splitland from OCHA (a "source" line); Mexico stays with the source of the run
        assert shapes.source_of("Splitland") is ocha and shapes.source_of("Mexico") is geo
        # OCHA's file has the new regions as shapes of their own: no split. The unit's P-code and its source go with the proposal
        r = at(1.0, 70.5)
        assert r == [pc.Proposal("adm1", "", "West", "coordinates inside West, OCHA COD-AB SPL ADM1", "Splitland", "", "", "", "", "", "", "", "SP02", "OCHA COD-AB")], r
        assert at(1.0, 71.5)[0].proposed == "West"                 # in the district the split lines do not name: of no matter here
        # a region AntWeb does not have yet: a row marked new, as with any source
        r = at(1.0, 72.5)
        assert (r[0].proposed, r[0].note, r[0].unit_code, r[0].basis) == ("East", pc.TO_ADD, "SP01", "coordinates inside East (East by the mapping file, a state to add in the "
                                                                          "Geolocale Manager first), OCHA COD-AB SPL ADM1"), r
        assert at(1.0, 74.5)[0].basis == "coordinates inside Rest Region (Rest by the mapping file), OCHA COD-AB SPL ADM1" and at(1.0, 75.5)[0].unit_code == "SP05"
        # the conversion from a coarser unit works with it as with any source
        r = at(1.0, 70.5, adm1="Old")
        assert (r[0].current, r[0].proposed, r[0].note) == ("Old", "West", "the stored province is the province of the region")
        t = shapes.tables["Splitland"]
        assert t["label"] == "OCHA COD-AB" and t["level"] == "ADM1" and t["where"] == "OCHA COD-AB SPL ADM1" and t["to_add"] == ["East", "Newland"] and shapes.splits_used == []
        # in the lists: the P-code beside the ISO code and the ne_id, with the source it is from
        lists = pc.Lists()
        record = row("casent0000141", "Splitland", 1.0, 70.5, event="E1")
        lists.add(record, work.work(record), work.kind(record))
        assert lists.corrections()[0][-6:] == ["", "", "adm1", "", "SP02", "OCHA COD-AB"] and lists.events()[0][-2:] == ["SP02", "OCHA COD-AB"]
        assert [x[2:5] + x[-2:] for x in t["lines"]][:2] == [["East", "East", "mapping file: state to add in the Geolocale Manager first", "SP01", "OCHA COD-AB"],
                                                            ["West", "West", "by name only, 0 specimens", "SP02", "OCHA COD-AB"]]
        # consistency of the source: OCHA's regions and districts fit (every district wholly in one region) ...
        assert t["consistency"] == {"level": "ADM1", "lower": "ADM2", "units": 6, "measured": 6, "under": 0, "worst": [], "step_km": t["consistency"]["step_km"]}
        # ... and a source whose district file draws a district over two regions is shown: 84% of Kept in one region, 16% in the next
        other = pc.OchaSource(os.path.join(folder, "shapes2"), ocha_site(os.path.join(folder, "hdx2"), districts_as_drawn=False))
        crooked = pc.StateShapes(_made["store"], states, base.countries, rows, None, rules, other, None, True)
        crooked.choose("Splitland", "SPL")
        c = crooked.tables["Splitland"]["consistency"]
        assert (c["under"], c["measured"], c["worst"]) == (1, 6, ["Kept: 84% in Rest Region, 16% in Newland"]), c
        # with geoBoundaries' own two levels of Splitland: the district Middle lies in the shape Joined, as do East and West: nothing under the mark
        plain_geo = pc.StateShapes(_made["store"], states, base.countries, [], None, pc.Rules(), geo, None, True)
        plain_geo.choose("Splitland", "SPL")
        assert plain_geo.tables["Splitland"]["consistency"]["under"] == 0 and plain_geo.tables["Splitland"]["label"] == "geoBoundaries"
        # Natural Earth has one level: not measured, and said so. A country that is not to be measured has no entry
        ne = natural_earth()
        one = pc.StateShapes(_made["store"], base.states, base.countries, [], None, None, ne, None, True)
        one.choose("Congoland", "CGL")
        assert one.tables["Congoland"]["consistency"] == {"level": "NE", "lower": "", "why": "the source has one level"}
        assert pc.StateShapes(_made["store"], states, base.countries, [], None, None, geo).choose("Splitland", "SPL") and True
        lines, measured, bad = pc.consistency_lines([("", shapes), ("x", crooked), ("y", one)])
        assert (measured, bad) == (1, 0) and lines[4:] == [
            "  Splitland, OCHA COD-AB, ADM1 and ADM2: 0 of 6 units of ADM2 lie less than 97% inside one shape of ADM1",
            "  Congoland, Natural Earth states and provinces 1:10m, NE: not measured (the source has one level)"], lines      # each country and source once
        lines, measured, bad = pc.consistency_lines([("x", crooked)])
        assert (measured, bad) == (1, 1) and lines[4] == ("  Splitland, OCHA COD-AB, ADM1 and ADM2: 1 of 6 units of ADM2 lie less than 97% inside one shape of ADM1. "
                                                          "The worst: Kept: 84% in Rest Region, 16% in Newland")
        # the three sources compared on AntWeb's specimens: a third column; a country a source does not have is "none"
        evidence = pc.Evidence(base.countries, states)
        for state, lon, count in (("West", 70.5, 20), ("West", 71.5, 10), ("Rest", 74.5, 30), ("Plain", 75.5, 10), ("Joined", 72.5, 6)):
            evidence.add("Splitland", state, "1.0", str(lon), count)
        forced = [(x.label, pc.StateShapes(_made["store"], states, base.countries, rows, evidence, rules, x)) for x in (geo, ne, ocha)]
        for _, x in forced:
            x.choose("Splitland", "SPL")
        assert forced[2][1].source_of("Splitland") is ocha and forced[0][1].source_of("Splitland") is geo            # each with its own source, whatever the mapping file says
        lines, totals = pc.compared(forced)
        assert lines[5:] == [
            "  country                      specimens  | geoBoundaries                       | Natural Earth states and provinces  | OCHA COD-AB                       ",
            "                                          | level   agree  other no st.no unit  | level   agree  other no st.no unit  | level   agree  other no st.no unit",
            "  ALL IT HAS SHAPES OF                    |          78.9    7.9   13.2    0.0  |                                     |          92.1    7.9    0.0    0.0",
            "    specimens in them                     | 76; none: 0 countries, 0            | 0; none: 1 countries, 76            | 76; none: 0 countries, 0          ",
            "  Splitland                           76  | ADM1     78.9    7.9   13.2    0.0  | none      0.0    0.0    0.0  100.0  | ADM1     92.1    7.9    0.0    0.0"], "\n".join(lines)
        # (geoBoundaries' joined shape is counted by the districts the split lines name: the ten specimens in the district they do not name are in no state)
        # the report: which file of OCHA's was used, the country's own source, the consistency in the summary and in the tables
        text = pc.report_text("x", "y", "every group", lists, shapes, _made["store"], "1 second", 1.0, pc.MAP_FILE, None, None, evidence, forced, (), None, {"ocha": ocha})
        top, rest = text.split("\nDetails\n")
        assert "Consistency of the sources (do the state level and the next level down fit?): 1 countries measured, 0 with units that lie in more than one state shape" in top
        assert len(top.split("\n")) <= pc.SUMMARY_LINES
        for part in ("  OCHA COD-AB from HDX (data.humdata.org), in " + os.path.join(folder, "shapes") + ". HDX has no pinned release: this is the record of the files used.\n"
                     "  SPL: ocha-cod-ab-spl.geojson.zip, ", "; first seen; last modified on HDX 2026-08-13T10:00:00v01\n",
                     "  Countries the mapping file takes from a source of their own: Splitland: OCHA COD-AB\n",
                     "\nConsistency of the source: where a source has the state level and the next level down",
                     "  Splitland, OCHA COD-AB, ADM1 and ADM2: 0 of 6 units of ADM2 lie less than 97% inside one shape of ADM1\n",
                     "  geoBoundaries gbOpen, the simplified files, commit ", "  Natural Earth states and provinces 1:10m, ne_10m_admin_1_states_provinces.geojson at commit "):
            assert part in rest, part + "\n" + rest
    # a refused download stops the run with the message, whatever asked for the file
    refused = pc.OchaSource(os.path.join(tempfile.gettempdir(), "no-such-folder-of-shapes"), "file:///nowhere/cod-ab-{iso}.json")
    try:
        pc.StateShapes(_made["store"], setup().states, setup().countries, [], None, None, refused).choose("Splitland", "SPL")
        raise AssertionError("no file and no HDX")
    except pc.Refused as e:
        assert "put it on the server as " + os.path.join(tempfile.gettempdir(), "no-such-folder-of-shapes", "ocha-cod-ab-spl.geojson.zip") in str(e)


# ---------------------------------------------------------------- the load file for TaxonWorks

TW_HEAD = ["id", "catalogNumber", "country", "fieldNumber", "decimalLatitude", "decimalLongitude", "TW:DataAttribute:CollectingEvent:adm1",
           "TW:DataAttribute:CollectingEvent:adm2", "TW:DataAttribute:CollectingEvent:BiogeographicRegion", "TW:DataAttribute:CollectingEvent:Country", "TW:Internal:collecting_event_id"]

# What TaxonWorks holds: (collecting event id, code as the archive has it, Country, adm1, BiogeographicRegion, latitude, longitude)
TW_EVENTS = [
    ("101", "eventID:E01", "Mexico", "", "Neotropical", "25.0", "-108.0"), ("102", "E02", "Mexico", "Sonora", "Neotropical", "25.0", "-108.0"),
    ("103", "E03", "Mexico", "", "Neotropical", "27.0", "-105.005"), ("104", "E04", "Mexico", "Sinaloa", "Neotropical", "25.0", "-108.0"),
    ("105", "E05", "Mexico", "", "Neotropical", "25.0", "-108.0"), ("106", "E06", "", "", "", "-15.0", "47.0"), ("107", "E07", "Africa", "", "Malagasy", "", ""),
    ("108", "E08", "USA", "Arizona", "Nearctic", "35.0", "-108.0"), ("109", "E09", "", "Sonora", "Neotropical", "25.0", "-108.0"),
    ("110", "E10", "Mexico", "Toliara", "Malagasy", "-24.0", "45.0"), ("111", "E11", "Hawaii", "", "Nearctic", "21.5", "-159.5"),
    ("112", "E12", "United States", "Hawaii", "Oceania", "21.5", "-159.5"), ("113", "E13", "Hawaii", "Oahu", "Oceania", "21.5", "-159.5"),
    ("114", "E14", "Mexico", "Sonora", "", "25.0", "-108.0"), ("115", "E15", "Mexico", "Sonora", "", "25.0", "-108.0"),
    ("118", "E18", "Mexico", "", "Neotropical", "25.0", "-108.0"), ("119", "E18", "Mexico", "", "Neotropical", "25.0", "-108.0"),
    ("120", "E19", "Mexico", "", "Neotropical", "25.0", "-108.0"), ("120", "E19B", "Mexico", "", "Neotropical", "25.0", "-108.0"),
    ("121", "E20", "Mexico", "", "Neotropical", "25.0", "-108.0"), ("121", "E20", "Mexico", "Sonora", "Neotropical", "25.0", "-108.0"),
    ("122", "E21", "Madagascar", "Antsiranana", "Malagasy", "-15.0", "47.0"), ("123", "E22", "Madagascar", "Toliara", "Malagasy", "-15.0", "47.0"),
    ("124", "E23", "Splitland", "", "Neotropical", "1.0", "72.5"), ("125", "E24", "Mexico", "Sonora", "", "25.0", "-108.0"),
    ("126", "E25", "Mexico", "Sonora", "", "25.0", "-108.0"), ("127", "", "Mexico", "", "", "", ""), ("128", "E 27", "Mexico", "", "", "", ""),
    ("129", "BLF-0-1/2", "Mexico", "Sonora", "Neotropical", "", ""), ("", "E99", "Mexico", "", "", "", ""),
]

# What AntWeb stores: (specimen code, collecting event code, country, latitude, longitude, state, island country, bioregion, stated error, which rules)
TW_SPECIMENS = [
    ("c0101", "E01", "Mexico", 25.0, -108.0, "", "", "Neotropical", "", ""), ("c0102", "E01", "Mexico", 25.0, -108.0, "", "", "Neotropical", "", ""),
    ("c0201", "E02", "Mexico", 25.0, -108.0, "", "", "Neotropical", "", ""),
    ("c0301", "E03", "Mexico", 27.0, -105.005, "", "", "Neotropical", "", ""),             # inside Sonora, 0.5 km from Chihuahua
    ("c0401", "E04", "Mexico", 25.0, -108.0, "", "", "Neotropical", "", ""),               # TaxonWorks holds Sinaloa
    ("c0501", "E05", "Mexico", 25.0, -108.0, "", "", "Neotropical", "", ""), ("c0502", "E05", "Mexico", 27.0, -103.0, "", "", "Neotropical", "", ""),
    ("c0601", "E06", "", -15.0, 47.0, "", "", "", "", ""),                                 # no country anywhere: from the coordinates
    ("c0701", "E07", "Africa", -15.0, 47.0, "", "", "Malagasy", "", ""),                   # a country AntWeb does not know
    ("c0801", "E08", "USA", 35.0, -108.0, "Arizona", "", "Nearctic", "", ""),              # an old name of United States
    ("c0901", "E09", "Mexico", 25.0, -108.0, "Sonora", "", "Neotropical", "", ""),         # AntWeb has the country (from the DwC column), TaxonWorks' predicate is blank
    ("c1001", "E10", "Madagascar", -24.0, 45.0, "Toliara", "", "Malagasy", "", ""),        # TaxonWorks holds another valid country
    ("c1101", "E11", "United States", 21.5, -159.5, "", "Hawaii", "Oceania", "", ""),      # TaxonWorks has Hawaii as the country
    ("c1201", "E12", "United States", 21.5, -159.5, "", "Hawaii", "Oceania", "", ""),      # TaxonWorks has it as AntWeb needs it
    ("c1301", "E13", "United States", 21.5, -159.5, "", "Hawaii", "Oceania", "", ""),      # TaxonWorks has the state Oahu
    ("c1401", "E14", "Mexico", 25.0, -108.0, "Sonora", "", "Neotropical", "", ""), ("c1402", "E14", "Mexico", 25.0, -108.0, "Sonora", "", "Nearctic", "", ""),
    ("c1501", "E15", "Mexico", 25.0, -108.0, "Sonora", "", "Atlantis", "", ""),
    ("c1601", "E16", "Mexico", 25.0, -108.0, "", "", "Neotropical", "", ""),               # the code is not in the archive
    ("c1701", "E 17", "Mexico", 25.0, -108.0, "", "", "Neotropical", "", ""), ("c1702", "", "Mexico", 25.0, -108.0, "", "", "Neotropical", "", ""),
    ("c1801", "E18", "Mexico", 25.0, -108.0, "", "", "Neotropical", "", ""), ("c1901", "E19", "Mexico", 25.0, -108.0, "", "", "Neotropical", "", ""),
    ("c2001", "E20", "Mexico", 25.0, -108.0, "", "", "Neotropical", "", ""),
    ("c2101", "E21", "Madagascar", -15.0, 47.0, "Antsiranana", "", "Malagasy", "", "regions"), ("c2102", "E21", "Madagascar", -15.0, 47.0, "Antsiranana", "", "Malagasy", "", "regions"),
    ("c2201", "E22", "Madagascar", -15.0, 47.0, "Toliara", "", "Malagasy", "", "regions"),  # the province is not that of the region
    ("c2301", "E23", "Splitland", 1.0, 72.5, "", "", "Neotropical", "", "split"),           # a state AntWeb does not have yet
    ("c2401", "E24", "Mexico", 25.0, -108.0, "Sonora", "", "Neo\ttropical", "", ""),
    ("c2501", "E25", "Mexico", 25.0, -108.0, "Sonora", "", "Neotropical", "", ""),
]


def tw_world(folder, hold=(), include=()):
    """(TwArchive, the result of tw_load) for the events and specimens above; the archive is written into folder."""
    path = os.path.join(folder, "data.tsv")
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write("\t".join(TW_HEAD) + "\n")
        for ident, code, country, adm1, bio, lat, lon in TW_EVENTS:
            f.write("\t".join(["1", "CASENT1", country or "Mexico", code, lat, lon, adm1, "", bio, country, ident]) + "\n")
    base = setup()
    regions = pc.States(MDG_STATES, base.countries)
    works = {"": base, "regions": pc.Corrections(base.checker, regions, pc.StateShapes(_made["store"], regions, base.countries, [], None, MDG_RULES)), "split": split_world()}
    lists, stored = pc.Lists(), collections.defaultdict(list)
    for code, event, country, lat, lon, adm1, island, bio, stated, rules in TW_SPECIMENS:
        record = row(code, country, lat, lon, adm1=adm1, island=island, event=event, stated=stated, bioregion=bio)
        lists.add(record, works[rules].work(record), works[rules].kind(record))
        stored[event].append((country, adm1, island, bio, 1, code, str(lat), str(lon)))
    archive = pc.TwArchive(path)
    bioregions = {"Neotropical", "Nearctic", "Oceania", "Malagasy", "Neo\ttropical"}
    return archive, pc.tw_load(archive, lists, stored, base.countries, base.states, bioregions, hold, None, include)


def test_the_taxonworks_archive():
    with tempfile.TemporaryDirectory() as folder:
        archive, _ = tw_world(folder)
        # the code is the column fieldNumber without "eventID:"; a row with no collecting event id is left out
        assert archive.rows == 30 and archive.rows_without_id == 1 and len(archive.events) == 27 and archive.by_code["E01"] == ["101"]
        assert archive.events["101"].values == ("Mexico", "", "Neotropical") and (archive.events["106"].lat, archive.events["106"].lon) == ("-15.0", "47.0")
        assert archive.find("E01") == (archive.events["101"], "") and archive.find("E16") == (None, pc.NOT_IN_ARCHIVE) and archive.find("E99") == (None, pc.NOT_IN_ARCHIVE)
        assert archive.find("") == (None, pc.BLANK_CODE) and archive.find("E 27") == (None, pc.CODE_WITH_BLANK)
        assert archive.find("E18") == (None, "code on 2 collecting events in TaxonWorks") and archive.find("E19") == (None, pc.EVENT_WITH_CODES) == archive.find("E19B")
        assert archive.find("E20") == (None, pc.EVENT_MIXED)
        text = archive.summary()
        assert text[0].endswith("data.tsv, dated " + archive.date + ": 30 rows, 27 collecting events, 26 codes (1 rows with no collecting event id left out).")
        assert text[1] == "  Collecting events with no Country predicate: 2; no adm1: 13; no BiogeographicRegion: 7."
        assert text[2] == ("  Codes on more than one collecting event: 1. Collecting events with a blank code: 1. Codes that contain a blank: 1. "
                           "Collecting events with more than one code: 1. Collecting events whose rows differ in a predicate: 1.")
        # the codes that are not letters followed by digits, by their shape: one namespace for each prefix in TaxonWorks
        assert text[3] == ("  Codes that are letters followed by digits: 23 (25 rows), with 1 prefixes. Other codes: 3 (3 rows), by their shape (A letters, 9 digits), "
                           "the 15 most common:") and text[4:] == ["          1  A-9-9/9", "          1  A9A", "          1  A_blank_9"], text
        # the same from the .zip the server has; the date is that of the file inside
        with zipfile.ZipFile(os.path.join(folder, "taxonworksNightly.zip"), "w") as z:
            z.write(os.path.join(folder, "data.tsv"), "data.tsv")
            z.writestr("meta.xml", "<x/>")
        zipped = pc.TwArchive(os.path.join(folder, "taxonworksNightly.zip"))
        assert zipped.rows == 30 and len(zipped.events) == 27 and zipped.find("E01")[0].values == ("Mexico", "", "Neotropical") and len(zipped.date) == 16
        # a file without the columns is refused, and says which
        with open(os.path.join(folder, "other.tsv"), "w", encoding="utf-8") as f:
            f.write("id\tfieldNumber\n1\tE01\n")
        try:
            pc.TwArchive(os.path.join(folder, "other.tsv"))
            raise AssertionError("an archive without the columns was read")
        except SystemExit as e:
            assert "has no column TW:Internal:collecting_event_id, TW:DataAttribute:CollectingEvent:Country" in str(e)
        # what the archive alone says, from the command line, without the database
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            assert pc.main(["--tw-archive", os.path.join(folder, "data.tsv"), "--tw-summary"]) == 0
        assert out.getvalue().split("\n")[1:5] == text[1:5]
        for arguments, message in ((["--tw-summary"], "--tw-summary needs --tw-archive."), (["--tw-archive", os.path.join(folder, "data.tsv")], "--tw-archive needs --group or --group-id"),
                                   (["--tw-archive", os.path.join(folder, "none.zip"), "--group-id", "1"], "is not there")):
            try:
                pc.main(arguments)
                raise AssertionError(arguments)
            except SystemExit as e:
                assert message in str(e), e


def test_the_load_file_for_taxonworks():
    with tempfile.TemporaryDirectory() as folder:
        archive, (load, differs, unresolved, counts) = tw_world(folder)
        pc.write_tsv(os.path.join(folder, "tw_load.tsv"), pc.TW_LOAD_HEADER, load)
        with open(os.path.join(folder, "tw_load.tsv"), "rb") as f:
            written = f.read()
        _, old = tw_world(folder, "near-border,stored-differs")
    # the exact bytes: tab-delimited, UTF-8 with no byte order mark, one header row, no quote anywhere, line ends \n, eight columns
    assert written == (
        "ce_code\tpredicate\taction\tcurrent_value\tnew_value\tlatitude\tlongitude\tcollecting_event_id\n"
        "E01\tadm1\tcreate\t\tSonora\t25.0\t-108.0\t101\n"                         # state missing, coordinates inside one state
        "E03\tadm1\tcreate\t\tSonora\t27.0\t-105.005\t103\n"                       # ... and 0.5 km from another state: the coordinates are followed
        "E06\tCountry\tcreate\t\tMadagascar\t-15.0\t47.0\t106\n"                    # no country: from the coordinates
        "E06\tadm1\tcreate\t\tAntsiranana\t-15.0\t47.0\t106\n"
        "E07\tCountry\tupdate\tAfrica\tMadagascar\t-15.0\t47.0\t107\n"              # a name AntWeb does not know; the coordinates are AntWeb's, the archive has none
        "E07\tadm1\tcreate\t\tAntsiranana\t-15.0\t47.0\t107\n"
        "E09\tCountry\tcreate\t\tMexico\t25.0\t-108.0\t109\n"                       # the predicate is blank, AntWeb has one valid country
        "E11\tCountry\tupdate\tHawaii\tUnited States\t21.5\t-159.5\t111\n"          # an island: the parent country and the island as the state
        "E11\tadm1\tcreate\t\tHawaii\t21.5\t-159.5\t111\n"
        "E21\tadm1\tupdate\tAntsiranana\tDiana\t-15.0\t47.0\t122\n"                 # Madagascar: the province to its region
        "E22\tadm1\tupdate\tToliara\tDiana\t-15.0\t47.0\t123\n"                     # ... also where the stored province is not the province of that region
        "E25\tBiogeographicRegion\tcreate\t\tNeotropical\t25.0\t-108.0\t126\n").encode("utf-8"), written
    assert not written.startswith(b"\xef\xbb\xbf") and b'"' not in written and b"\r" not in written and written.count(b"\n") == 13
    assert all(line.count(b"\t") == 7 for line in written.split(b"\n")[:-1]) and pc.TW_LOAD_HEADER[-1] == "collecting_event_id"
    # one line for each collecting event and predicate: E01 has two specimens and one line. The current value is TaxonWorks' own
    assert [x for x in load if x[0] == "E21"] == [["E21", "adm1", "update", "Antsiranana", "Diana", "-15.0", "47.0", "122"]]
    # where TaxonWorks holds another bioregion than AntWeb: not in the load file, a file of its own in the same columns, for a person to decide
    assert differs == [["E11", "BiogeographicRegion", "update", "Nearctic", "Oceania", "21.5", "-159.5", "111"]] and counts["differs"] == 1
    # no line where TaxonWorks holds the value already
    assert dict(counts["already"]) == {"Country": 13, "adm1": 2, "BiogeographicRegion": 14}, counts["already"]
    assert dict(counts["load"]) == {("adm1", "create"): 5, ("adm1", "update"): 2, ("Country", "create"): 2, ("Country", "update"): 2, ("BiogeographicRegion", "create"): 1}
    # what versions 5 to 7 held back for a person is counted for information, and is in the load file
    assert dict(counts["info"]) == {"near-border": 1, "islet": 0, "stored-differs": 1} and counts["hold"] == set()
    # E08: TaxonWorks holds USA, an old name of the United States in AntWeb. A name of the same country is left as TaxonWorks has it
    # (AntWeb is to take the current names): no line, in no file, counted by pair. "Africa" is no name of a country: replaced
    assert not [x for x in load + unresolved if x[0] == "E08"] and dict(counts["names"]) == {("USA", "United States"): 1} and not counts["elsewhere"]
    assert dict(counts["replaced"]) == {("Africa", "Madagascar"): 1}
    # what is not in the load file, and why: code, TaxonWorks' id when known, predicate, proposed value, reason, specimens, first specimen, coordinates
    assert pc.TW_UNRESOLVED_HEADER == ["ce_code", "tw_collecting_event_id", "predicate", "proposed_value", "reason", "specimens", "first_specimen_code", "latitude", "longitude"]
    got = {(x[0], x[2]): x for x in unresolved}
    assert len(got) == len(unresolved) == 14, unresolved
    assert ("E03", "adm1") not in got and ("E22", "adm1") not in got
    assert got[("E04", "adm1")][3:5] == ["Sonora", "TaxonWorks holds Sinaloa, AntWeb has no state: the archive and AntWeb differ"]
    assert got[("E05", "adm1")][3:6] == ["Chihuahua | Sonora", "specimens of one event give different proposals", 2]
    assert got[("E10", "Country")][3:5] == ["Madagascar", "TaxonWorks holds Mexico, AntWeb has Madagascar: the archive and AntWeb differ"]
    # an island whose state in TaxonWorks is another one: neither the country nor the state is touched (the island would be lost)
    assert got[("E13", "adm1")][3:5] == ["Hawaii", pc.ISLAND_STATE] and ("E13", "Country") not in got and not [x for x in load if x[0] == "E13"]
    assert got[("E14", "BiogeographicRegion")][3:5] == ["Nearctic | Neotropical", "specimens of one event give different proposals"]
    assert got[("E15", "BiogeographicRegion")][3:5] == ["Atlantis", pc.BAD_BIOREGION]
    assert got[("E16", "adm1")] == ["E16", "", "adm1", "Sonora", "code not in the TaxonWorks archive", 1, "c1601", "25.0", "-108.0"]
    assert got[("E 17", "adm1")][4] == "code contains a blank" and got[("", "adm1")][4] == "blank code"
    assert got[("E18", "adm1")][4] == "code on 2 collecting events in TaxonWorks" and got[("E19", "adm1")][4] == pc.EVENT_WITH_CODES and got[("E20", "adm1")][4] == pc.EVENT_MIXED
    assert got[("E23", "adm1")][3:5] == ["East", "proposed state not yet in AntWeb"]
    assert got[("E24", "BiogeographicRegion")][3:5] == ["Neo\ttropical", "value holds a tab or a line break"]
    # --tw-hold keeps a kind out, as before version 8: the line is in tw_unresolved.csv with its reason
    assert [x for x in load if x not in old[0]] == [["E03", "adm1", "create", "", "Sonora", "27.0", "-105.005", "103"], ["E22", "adm1", "update", "Toliara", "Diana", "-15.0", "47.0", "123"]]
    held = {(x[0], x[2]): x for x in old[2]}
    assert len(held) == 16 and held[("E03", "adm1")] == ["E03", "103", "adm1", "Sonora", "within tolerance of another state", 1, "c0301", "27.0", "-105.005"]
    assert held[("E22", "adm1")][3:5] == ["Diana", pc.OTHER_PROVINCE] and old[3]["hold"] == {"near-border", "stored-differs"} and not sum(old[3]["info"].values())
    # --tw-include bioregion-differs: the bioregion TaxonWorks holds differently goes into the load file as an update
    with tempfile.TemporaryDirectory() as folder:
        _, (more, no_differs, _, more_counts) = tw_world(folder, (), "bioregion-differs")
    assert [x for x in more if x not in load] == [["E11", "BiogeographicRegion", "update", "Nearctic", "Oceania", "21.5", "-159.5", "111"]]
    assert no_differs == [] and more_counts["differs"] == 0 and more_counts["include"] == {"bioregion-differs"}
    assert pc.chosen_of("", pc.TW_HOLD, "--tw-hold") == set() and pc.chosen_of(" islet , near-border", pc.TW_HOLD, "--tw-hold") == {"islet", "near-border"}
    for text, allowed, option, said in (("near-border,everything", pc.TW_HOLD, "--tw-hold", "--tw-hold: everything is not one of near-border, islet, stored-differs"),
                                        ("islet", pc.TW_INCLUDE, "--tw-include", "--tw-include: islet is not one of bioregion-differs")):
        try:
            pc.chosen_of(text, allowed, option)
            raise AssertionError("a name the option does not have")
        except ValueError as e:
            assert str(e) == said, str(e)
    # the report: collecting events, not specimens; the archive's date and rows; the codes of another shape
    text = pc.tw_report(archive, counts)
    assert text[1:16] == [
        "For TaxonWorks (tw_load.tsv: one line for each collecting event and predicate; the counts are collecting events):",
        "  predicate                 create    update   already set in TaxonWorks",
        "  Country                        2         2                          13",
        "  adm1                           5         2                           2",
        "  BiogeographicRegion            1         0                          14",
        "  BiogeographicRegion where TaxonWorks holds another one than AntWeb: 1 (tw_bioregion_differs.tsv, not in the load file, to decide; "
        "--tw-include bioregion-differs puts them in).",
        "  Country, TaxonWorks' value replaced (it is no name of any country in AntWeb): 1",
        "           1  Africa -> Madagascar",
        "  Country, a name AntWeb knows of the same country: left as TaxonWorks has it, in no file (AntWeb is to take the current names): 1. "
        "TaxonWorks' name -> AntWeb's valid name:",
        "           1  USA -> United States",
        "  TaxonWorks' country is an old name AntWeb points elsewhere (the map draws a place of its own under that name; AntWeb files it under another country): "
        "0, in no file:",
        "  For information, not held back: the coordinates are followed, and these lines are in the load file like any other "
        "(--tw-hold near-border,islet,stored-differs keeps a kind out):",
        "    near-border     1 collecting events, for information, not held back       within the tolerance of another state (column other_state_flag of the lists)",
        "    islet           0 collecting events, for information, not held back       on an islet the country outline leaves out (column note)",
        "    stored-differs  1 collecting events, for information, not held back       Madagascar: the stored province is not the province of the coordinates' region "
        "(stored_differs.csv)"], text[:16]
    assert text[16] == "  Not in the load file (tw_unresolved.csv): 14, by reason:"
    kept_out = pc.tw_report(archive, old[3])
    assert "    near-border     1 HELD (--tw-hold), in tw_unresolved.csv                  within the tolerance of another state (column other_state_flag of the lists)" in kept_out
    assert "    islet           0 collecting events, for information, not held back       on an islet the country outline leaves out (column note)" in kept_out
    assert "  BiogeographicRegion where TaxonWorks holds another one than AntWeb: in the load file as updates (--tw-include bioregion-differs)." in pc.tw_report(archive, more_counts)
    assert "          2  TaxonWorks holds another value than AntWeb: the archive and AntWeb differ" in text and "          2  specimens of one event give different proposals" in text
    assert "          1  proposed state not yet in AntWeb" in text and text[-4].startswith("  Codes that are letters followed by digits: 23 (25 rows)")
    report = pc.report_text("x", "y", "California Academy of Sciences (id 1)", pc.Lists(), setup().shapes, _made["store"], "1 second", 1.0, pc.MAP_FILE, None, None, None, None, (), text)
    top, rest = report.split("\nDetails\n")
    assert "For TaxonWorks: tw_load.tsv, tw_bioregion_differs.tsv and tw_unresolved.csv were written; the table of predicates and actions is under Details.\n" in top
    assert "\n".join(text) in rest and len(top.split("\n")) <= pc.SUMMARY_LINES
    # without an archive nothing is written, and the report says so (test_shapes_tested_with_antweb_s_own_specimens)
    sql = pc.tw_events_sql({"code", "collectioncode", "country", "adm1", "island_country", "bioregion"}, 1)
    assert cc.read_only(sql) == sql and "s.access_group = 1 group by 1, 2, 3, 4, 5" in sql and "hex(ifnull(s.collectioncode, ''))" in sql


def test_a_country_name_is_left_as_taxonworks_has_it():
    # AntWeb's countries: two that TaxonWorks knows under their current names, and Western Sahara as an old name of Morocco
    C = cc.Country
    known = cc.Countries([C("Czech Republic", 1, "", 0, ""), C("Czechia", 0, "Czech Republic", 0, ""), C("Ivory Coast", 1, "", 0, ""),
                          C("C\u00f4te d'Ivoire", 0, "Ivory Coast", 0, ""), C("Morocco", 1, "", 0, ""), C("Western Sahara", 0, "Morocco", 0, ""),
                          C("Madagascar", 1, "", 0, ""), C("Democratic Republic of Congo", 1, "", 0, ""), C("Swaziland", 1, "", 0, ""), C("Turkey", 1, "", 0, "")])
    # the map: Czechia and the Czech Republic are two names of one shape; Western Sahara is a shape of its own, and Morocco is no name of it
    the_map = cc.read_shapes({"type": "FeatureCollection", "features": [
        feature("Czechia", [[square(12, 48, 19, 51)]], NAME_LONG="Czech Republic", ISO_A3="CZE"), feature("C\u00f4te d'Ivoire", [[square(-8, 4, -3, 10)]], SUBUNIT="Ivory Coast"),
        feature("Morocco", [[square(-10, 28, -1, 36)]]), feature("W. Sahara", [[square(-17, 21, -9, 27)]], NAME_LONG="Western Sahara"),
        feature("Dem. Rep. Congo", [[square(12, -13, 31, 5)]], NAME_LONG="Democratic Republic of the Congo"), feature("eSwatini", [[square(31, -27, 32, -26)]]),
        feature("Turkey", [[square(26, 36, 44, 42)]])]})
    places = pc.map_names(the_map)
    assert places["czechia"] == places["czech republic"] == {"czechia", "czech republic"} and "morocco" not in places["western sahara"]
    assert "democratic republic of congo" in places["democratic republic of the congo"]          # by the alias table of the country tool
    events = [("201", "F01", "Czechia"), ("202", "F02", "C\u00f4te d'Ivoire"), ("203", "F03", "Western Sahara"), ("204", "F04", "Atlantis"), ("205", "F05", ""),
              ("206", "F06", "Czech Republic"), ("207", "F07", "Morocco"), ("208", "F08", "Czechia"), ("209", "F09", "Eswatini"), ("210", "F10", "T\u00fcrkiye")]
    stored = {"F01": "Czech Republic", "F02": "Ivory Coast", "F03": "Morocco", "F04": "Madagascar", "F05": "Morocco", "F06": "Czech Republic", "F07": "Madagascar",
              "F08": "Czech Republic", "F09": "Swaziland", "F10": "Turkey"}
    with tempfile.TemporaryDirectory() as folder:
        path = os.path.join(folder, "data.tsv")
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.write("\t".join(TW_HEAD) + "\n")
            for ident, code, country in events:
                f.write("\t".join(["1", "CASENT1", country, code, "", "", "x", "", "y", country, ident]) + "\n")
        archive = pc.TwArchive(path)
        specimens = {code: [(country, "x", "", "y", 2, "c" + code, "1.0", "2.0")] for code, country in stored.items()}
        load, _, unresolved, counts = pc.tw_load(archive, pc.Lists(), specimens, known, setup().states, {"y"}, (), places)
    # in the load file: a blank Country, and a value that is no name of any country in AntWeb. Nothing else
    assert load == [["F04", "Country", "update", "Atlantis", "Madagascar", "1.0", "2.0", "204"], ["F05", "Country", "create", "", "Morocco", "1.0", "2.0", "205"]], load
    # a name of the same country is left: Czechia (twice), Cote d'Ivoire. Western Sahara apart: the map has it as a place of its own
    assert dict(counts["names"]) == {("Czechia", "Czech Republic"): 2, ("C\u00f4te d'Ivoire", "Ivory Coast"): 1, ("Eswatini", "Swaziland"): 1, ("T\u00fcrkiye", "Turkey"): 1}
    assert dict(counts["elsewhere"]) == {("Western Sahara", "Morocco"): 1}
    # Eswatini and Turkiye: current names AntWeb does not have yet. The map and its alias table know them for the same country: left as they are
    assert dict(counts["replaced"]) == {("Atlantis", "Madagascar"): 1} and counts["already"]["Country"] == 1
    # TaxonWorks holds the name of another country than AntWeb has for the specimens: for a person, as before
    assert [x[:5] for x in unresolved] == [["F07", "207", "Country", "Madagascar", "TaxonWorks holds Morocco, AntWeb has Madagascar: the archive and AntWeb differ"]]
    text = "\n".join(pc.tw_report(archive, counts))
    assert ("  Country, a name AntWeb knows of the same country: left as TaxonWorks has it, in no file (AntWeb is to take the current names): 5. "
            "TaxonWorks' name -> AntWeb's valid name:\n           2  Czechia -> Czech Republic\n           1  C\u00f4te d'Ivoire -> Ivory Coast\n") in text, text
    assert ("another country): 1, in no file:\n           1  Western Sahara -> Morocco\n") in text
    # without the map every name AntWeb knows is one list, and a name it does not know is replaced
    bare = pc.tw_load(archive, pc.Lists(), specimens, known, setup().states, {"y"})
    assert dict(bare[3]["names"])[("Western Sahara", "Morocco")] == 1 and ["F09", "Country", "update", "Eswatini", "Swaziland", "1.0", "2.0", "209"] in bare[0]


def test_two_valid_states_for_one_shape():
    base = setup()
    # AntWeb has pairs of valid states that are one name: Alfa and ALFA, Charlie and CHARLIE, Ho tel and Ho-tel
    names = ["Alfa", "ALFA", "Bravo", "Golf", "Charlie", "CHARLIE", "Ho tel", "Ho-tel", "Delta", "Xray", "Yankee", "Victor", "Uniform"]
    states = pc.States([(x, "Nameland", 1, "") for x in names] + [("Hotel", "Nameland", 0, "Ho-tel")], base.countries)
    assert states.twins("Nameland", "alfa") == ["ALFA", "Alfa"] and states.twins("Nameland", "Bravo") == ["Bravo"] and states.twins("Nameland", "Nothing") == []
    # the two count as one state wherever names are compared: always the same one of them, the first in order
    assert states.valid_of("Nameland", "Alfa") == states.valid_of("Nameland", "ALFA") == states.find("Nameland", "alfa")[0] == "ALFA"
    assert states.find("Nameland", "Charlie Province") == ("CHARLIE", "name without the unit word")
    # as AntWeb spells it: the state written exactly so, the state an old name's row points to, else the twin most like the name
    assert [states.exact("Nameland", x) for x in ("Alfa", "ALFA", "alfa", "Hotel", "Ho tel", "Bravo", "Nothing")] == ["Alfa", "ALFA", "Alfa", "Ho-tel", "Ho tel", "Bravo", None]
    evidence = pc.Evidence(base.countries, states)
    for state, lat, lon, count in (("Alfa", 0.5, 60.5, 4), ("ALFA", 0.5, 60.5, 3), ("Golf", 0.5, 61.5, 2), ("Bravo", 0.5, 61.5, 9), ("CHARLIE", 0.5, 63.5, 1), ("Hotel", 0.5, 62.5, 6)):
        evidence.add("Nameland", state, str(lat), str(lon), count)
    assert dict(evidence.filed["Nameland"]) == {"Alfa": 4, "ALFA": 3, "Golf": 2, "Bravo": 9, "CHARLIE": 1, "Ho-tel": 6}
    # name lines: Bravo is Golf (but Bravo is a valid state itself); Zulu is Ho-tel, as the line writes it
    rules = pc.Rules(names=[("Nameland", "ADM1", "Bravo", "Golf"), ("Nameland", "ADM1", "Zulu", "Ho-tel")])
    shapes = pc.StateShapes(_made["store"], states, base.countries, [], evidence, rules)
    work = pc.Corrections(base.checker, states, shapes)
    at = lambda lon: work.work(row("casent0000401", "Nameland", 0.5, lon))[0].proposed
    # the shape Alfa: the state written as the shape. The shape Charlie Province: the twin most like it. Both run after run
    assert at(60.5) == "Alfa" and at(63.5) == "Charlie"
    # the shape's own name is a valid state: that state, and the name line does not go before it
    assert at(61.5) == "Bravo"
    # no valid state has the shape's name: the name line serves, and its spelling is the one proposed
    assert at(62.5) == "Ho-tel"
    t = shapes.tables["Nameland"]
    assert [x[2:5] for x in t["lines"] if x[2] in ("Alfa", "Bravo", "Zulu", "Charlie Province")] == [
        ["Alfa", "Alfa", "confirmed by specimens, and by the name"], ["Bravo", "Bravo", "confirmed by specimens, and by the name"],
        ["Zulu", "Ho-tel", "confirmed by specimens, and by the name"], ["Charlie Province", "Charlie", "by name only, 1 specimens"]], t["lines"]
    # the list for merging duplicates in AntWeb: country, level, shape, unit code, the state used, the other valid state, specimens filed under each
    assert t["stats"]["twins"] == [["Nameland", "ADM1", "Alfa", "", "Alfa", "ALFA", 4, 3], ["Nameland", "ADM1", "Bravo", "", "Bravo", "Golf", 9, 2],
                                   ["Nameland", "ADM1", "Zulu", "", "Ho-tel", "Ho tel", 6, 0], ["Nameland", "ADM1", "Charlie Province", "", "Charlie", "CHARLIE", 0, 1]], t["stats"]["twins"]
    text = pc.report_text("x", "y", "every group", pc.Lists(), shapes, _made["store"], "1 second", 1.0, pc.MAP_FILE, None, None, evidence)
    top, rest = text.split("\nDetails\n")
    assert " AntWeb has two valid states for one shape: 4 (list under Details).\n" in top and len(top.split("\n")) <= pc.SUMMARY_LINES
    assert ("AntWeb has two valid states for one shape (4): the list for merging duplicates in AntWeb. ") in rest
    assert ("  Nameland ADM1: shape Alfa: used Alfa (4 specimens), the other valid state ALFA (3)\n"
            "  Nameland ADM1: shape Bravo: used Bravo (9 specimens), the other valid state Golf (2)\n") in rest, rest
    # without twins the list says none
    assert "AntWeb has two valid states for one shape (0)" in pc.report_text("x", "y", "every group", pc.Lists(), base.shapes, _made["store"], "1 second", 1.0)


def test_the_geolocale_manager_list():
    work = setup()
    lists = pc.Lists()
    for i, (lat, lon) in enumerate([(-20.0, 49.0), (-20.5, 49.5), (-21.0, 48.0)]):
        record = row("casent%07d" % (30 + i), "Madagascar", lat, lon, event="BLF9%d" % i)
        lists.add(record, work.work(record), work.kind(record))
    record = row("casent0000040", "Reunion", -21.0, 55.9)
    lists.add(record, work.work(record), work.kind(record))
    text = pc.report_text("x", "y", "every group", lists, work.shapes, _made["store"], "1 second", 1.0)
    assert ("For the Geolocale Manager: names of the boundary files that are no state in AntWeb, with the specimens that lie in them:\n"
            "          3  Madagascar: Analamanga\n          1  Reunion: Le Port") in text, text
    assert ("adm1_shape_map.csv: 6 rows that give a shape its state whatever the names and the specimens say (not counted here: 0 name lines, 0 split lines, "
            "0 source lines; they follow). ") in text, text
    missing = pc.report_text("x", "y", "every group", lists, work.shapes, _made["store"], "1 second", 1.0, "/nowhere/adm1_shape_map.csv")
    assert "Mapping file /nowhere/adm1_shape_map.csv: NOT FOUND (fetch it next to the tool): " in missing


def test_every_statement_is_a_single_select():
    columns = {"code", "collectioncode", "localitycode", "country", "adm1", "adm2", "island_country", "latlonmaxerror", "localityname", "localitynotes", "bioregion"}
    statements = [pc.places_sql({"name"}), pc.places_sql({"name", "bioregion", "alt_bioregion"}), pc.stored_countries_sql(), pc.stored_countries_sql(1), pc.rows_sql(columns), pc.rows_sql(columns, 1, ["Africa", "O'Brien; drop table specimen"]),
                  pc.rows_sql({"code", "country", "adm1"}, 21), pc.madagascar_sql(columns, ["Madagascar"]), pc.madagascar_sql({"code"}, ["Madagascar", "O'Brien; drop table specimen"], 1),
                  pc.evidence_sql(columns), pc.evidence_sql({"code"}), pc.tw_events_sql(columns | {"bioregion"}, 1), pc.tw_events_sql({"code"}, 21),
                  pc.rows_sql(columns, 1, [], [(["Madagascar", "MADAGASCAR"], ["Toliara", "Tulear", "O'Brien; drop table specimen"]), ([], ["x"])])]
    for sql in statements:
        assert cc.read_only(sql) == sql
        bare = cc.QUOTED.sub("''", sql).lower()
        assert bare.startswith("select ") and ";" not in bare
        for word in ("insert", "update", "delete", "replace", "create", "drop", "alter", "truncate", "rename", "grant", "into", "set", "call", "lock"):
            assert word not in cc.re.findall(r"[a-z_]+", bare), (word, sql)
    # the records with a gap, of one group; a name AntWeb does not know is compared exactly, as hex
    sql = pc.rows_sql(columns, 1, ["Africa"])
    assert " and s.access_group = 1 and (s.adm1 is null or s.adm1 = '' or s.country is null or s.country = '' or hex(s.country) in ('416672696361'))" in sql
    assert sql.endswith("order by s.country, s.code") and "hex(ifnull(s.collectioncode, ''))" in sql and "ifnull(round(s.decimal_latitude, 5), '')" in sql
    # a table without a column gives an empty text for it
    assert "collectioncode" not in pc.rows_sql({"code", "country", "adm1"}) and "access_group = " not in pc.rows_sql(columns)
    try:
        pc.group_sql("1 or 1 = 1")
        raise AssertionError("the group is a number")
    except ValueError:
        pass
    r = pc.read_row(["636173656E7431", "424C46313030", "", "4D657869636F20", "", "", "", "25.50000", "-108.25", "33206B6D", "5369657272612C2035206B6D", "", "", "1"])
    assert pc.read_row(["41", "", "", "", "", "", "", "", "NULL", "", "", "", "", "0"])[7:9] == ("", "")
    assert r == pc.Row("casent1", "BLF100", "", "Mexico", "", "", "", "25.5", "-108.25", "3 km", "Sierra, 5 km", "", "", "1")
    # the tool has no statement of its own that is sent anywhere but through coordinate_country's read-only door
    source = inspect.getsource(pc)
    assert "subprocess" not in source and "os.system" not in source and source.count("cc.rows_sql(") == 3 and source.count("cc.run_sql(") == 1
    # the group by its name
    groups = {"1": "California Academy of Sciences", "21": "University of Utah", "22": "University of Utah, Longino"}
    assert pc.find_group(groups, "california academy of sciences") == "1"
    for name in ("Utah", "Nobody"):
        try:
            pc.find_group(groups, name)
            raise AssertionError("no such group")
        except SystemExit as e:
            assert name in str(e)


if __name__ == "__main__":
    tests = [(n, f) for n, f in sorted(globals().items()) if n.startswith("test_") and callable(f)]
    for name, fn in tests:
        fn()
    print(str(len(tests)) + " tests passed")
