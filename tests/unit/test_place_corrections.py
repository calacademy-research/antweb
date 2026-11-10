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
import csv
import hashlib
import importlib.util
import inspect
import json
import os
import tempfile

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
    ]}


def countries():
    C = cc.Country
    return cc.Countries([C("Mexico", 1, "", 0, "", "MX", "MEX"), C("United States", 1, "", 0, "", "US", "USA"), C("USA", 0, "United States", 0, ""),
                         C("Hawaii", 1, "", 1, "United States"), C("Madagascar", 1, "", 0, "", "MG", "MDG"), C("Reunion", 1, "", 0, "", "RE", "REU"),
                         C("Tinyland", 1, "", 0, "", "TN", "TNY"), C("Atoll", 1, "", 0, "", "AT", "ATL"),
                         C("Andesia", 1, "", 0, "", "AD", "ADS"), C("Iberia", 1, "", 0, "", "IB", "IBR"),
                         C("Congoland", 1, "", 0, "", "CG", "CGL"), C("Omanland", 1, "", 0, "", "OM", "OML")])


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
          ("Alpha", "Omanland", 1, ""), ("Beta", "Omanland", 1, ""), ("Gamma", "Omanland", 1, ""), ("Delta", "Omanland", 1, "")]

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
}

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
             "geometry": {"type": "Polygon", "coordinates": [ring]}} for name, ring in shapes]}).encode("utf-8")
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
    assert all(len(r) == 4 and r[1] in pc.LEVELS for r in rows) and ("country", "level", "shape", "antweb_state") not in rows and len(rows) == 14
    assert ("Mexico", "ADM1", "Coahuila de Zaragoza", "Coahuila") in rows and ("United States", "ADM1", "District of Columbia", "Washington, D.C.") in rows
    rules = pc.read_rules(pc.MAP_FILE)
    # Madagascar: the regions are AntWeb's states now. One shape has another name; the provinces are converted; each region has its province
    assert [r for r in rows if r[0] == "Madagascar"] == [("Madagascar", "ADM1", "Matsiatra Ambony", "Haute Matsiatra")]
    assert rules.convert == {"Madagascar": ["Antananarivo", "Antsiranana", "Fianarantsoa", "Mahajanga", "Toamasina", "Toliara"]}
    within = rules.within["Madagascar"]
    # AntWeb's 22 regions (production, 2026-11-10) and the 22 shape names of the file geoBoundaries-MDG-ADM1 (read 2026-11-10)
    antweb = ["Alaotra Mangoro", "Amoron'i Mania", "Analamanga", "Analanjirofo", "Androy", "Anosy", "Atsimo-Andrefana", "Atsimo-Atsinanana", "Atsinanana", "Betsiboka",
              "Boeny", "Bongolava", "Diana", "Haute Matsiatra", "Ihorombe", "Itasy", "Melaky", "Menabe", "Sava", "Sofia", "Vakinankaratra", "Vatovavy Fitovinany"]
    shapes = ["Diana", "Sava", "Analanjirofo", "Amoron'i Mania", "Ihorombe", "Melaky", "Menabe", "Vakinankaratra", "Atsinanana", "Alaotra-Mangoro", "Sofia",
              "Anosy", "Boeny", "Betsiboka", "Analamanga", "Bongolava", "Itasy", "Atsimo-Andrefana", "Androy", "Atsimo-Atsinanana", "Matsiatra Ambony",
              "Vatovavy-Fitovinany"]
    assert len(within) == 22 and sorted(within) == sorted(pc.plain(x) for x in antweb)
    count = lambda province: sum(1 for x in within.values() if x == province)
    assert [count(x) for x in ("Antananarivo", "Antsiranana", "Fianarantsoa", "Mahajanga", "Toamasina", "Toliara")] == [4, 2, 5, 4, 3, 4]
    assert within["diana"] == "Antsiranana" and within["atsimo andrefana"] == "Toliara" and within["haute matsiatra"] == "Fianarantsoa" and within["sofia"] == "Mahajanga"
    # with AntWeb's regions and provinces, each of the 22 shapes is its region: 21 by the name (a hyphen makes no difference), one by the row
    known = cc.Countries([cc.Country("Madagascar", 1, "", 0, "")])
    states = pc.States([(x, "Madagascar", 1, "") for x in antweb + rules.convert["Madagascar"]], known)
    both = pc.StateShapes(_made.get("store") or (setup() and _made["store"]), states, known, rows, None, rules)
    found, hows = both.match("Madagascar", "ADM1", shapes)
    assert sorted(found) == antweb and hows.count("name") == 21 and found[shapes.index("Matsiatra Ambony")] == "Haute Matsiatra" and hows.count("mapping file") == 1
    assert both.wanted("Madagascar") == antweb and both.convert["Madagascar"] == set(rules.convert["Madagascar"])
    assert both.within["Madagascar"]["vatovavy fitovinany"] == "Fianarantsoa"
    # Seychelles: La Digue is Inner Islands; Other Islands is a state to add. The rows are given for the two levels that have these shapes
    assert [r for r in rows if r[0] == "Seychelles"] == [("Seychelles", "ADM2", "La Digue", "Inner Islands"), ("Seychelles", "ADM3", "La Digue", "Inner Islands"),
                                                        ("Seychelles", "ADM2", "Other Islands", "Outer Islands"), ("Seychelles", "ADM3", "Other Islands", "Outer Islands")]
    assert rules.new == {("Seychelles", "ADM2", "Other Islands", "Outer Islands"), ("Seychelles", "ADM3", "Other Islands", "Outer Islands")}


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
                    "convert,A,P1,P2\nwithin,A,T,P1\nwithin,A,T-2,P2\n\n")
        assert pc.read_map(path) == [("A", "ADM1", "S", "T"), ("A", "adm2", "S2", "T2"), ("A", "ADM1", "S3", "T3")]
        rules = pc.read_rules(path)
        assert rules.new == {("A", "adm2", "S2", "T2")} and rules.convert == {"A": ["P1", "P2"]} and rules.within == {"A": {"t": "P1", "t 2": "P2"}}
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
            "  3 of them (2 lines) lie within the stated error, or the allowed 1 km, of another state: column other_state_flag, look at those first.\n") in text, text


def test_in_the_water_near_one_state():
    r = w("Mexico", 25.0, -110.005)
    assert r == [pc.Proposal("adm1", "", "Sonora", "in the water, 0.5 km from Sonora, geoBoundaries MEX ADM1", "Mexico")], r
    # 2 km out: outside the stated country by more than 1 km, no proposal
    r = w("Mexico", 25.0, -110.02)
    assert r == [pc.Not("adm1", pc.OUTSIDE, "in the water, 2.02 km from Mexico")], r


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
        assert r == [pc.Proposal("adm1", "", "Hawaii", "in the water, 0.52 km from Hawaii, geoBoundaries USA ADM1; " + HAWAII, "United States")], r
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
                              "Sierra, 5 km N", "Mexico", "California Academy of Sciences", "", "", "adm1", ""]
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
                       "biogeographic_region"] and read[5] == corrections[4]
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
                 "  Madagascar: geoBoundaries MDG ADM1", "    shapes that are no state in AntWeb (1): Analamanga",
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


def tested_world():
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
    work, evidence = tested_world()
    at = lambda lat, lon: work.work(row("casent0000071", "Congoland", lat, lon))
    # read: 197 specimens; a state or a country AntWeb does not know and unusable coordinates are left out
    assert dict(evidence.totals) == {"specimens with a country, a state and coordinates, not of an island country": 197, "used for the test": 176,
                                     "left out: a state AntWeb does not know as a state of the country": 9, "left out: coordinates that cannot be used": 5,
                                     "left out: a country AntWeb does not know": 7}
    # confirmed by specimens, whatever the names: 40 of Bas-Uele's 42 lie in the shape Lower Uele, and 40 of the shape's 44 are Bas-Uele
    assert at(-1.0, 21.0) == [pc.Proposal("adm1", "", "Bas-Uele", "coordinates inside Lower Uele (Bas-Uele by the specimens), geoBoundaries CGL ADM1", "Congoland")]
    # a name match the specimens contradict is not used: 18 of Kasai's 20 specimens lie in the shape Sankuru, which is Kasai by the specimens
    assert at(-1.0, 25.0) == [pc.Not("adm1", pc.NOT_A_STATE, "Kasai", "Congoland")]
    assert at(-1.0, 27.0)[0].basis == "coordinates inside Sankuru (Kasai by the specimens), geoBoundaries CGL ADM1" and at(-1.0, 27.0)[0].proposed == "Kasai"
    # a name match with too few specimens to test, and one the specimens agree with in part, are used
    assert at(-1.0, 23.0) == [pc.Proposal("adm1", "", "Equateur", "coordinates inside Equateur, geoBoundaries CGL ADM1", "Congoland")]
    assert at(-2.0, 29.0)[0].proposed == "Kivu"
    # a shape that holds two AntWeb states, a state that lies in two shapes: no proposal, for a person to decide
    assert at(-3.0, 22.0) == [pc.Not("adm1", pc.NOT_A_STATE, "Katanga", "Congoland")] and at(-3.0, 25.0) == [pc.Not("adm1", pc.NOT_A_STATE, "Ituri Est", "Congoland")]
    t = work.shapes.tables["Congoland"]
    lines = [x for x in pc.tested_lines(work.shapes) if x[0] == "Congoland"]
    assert pc.TESTED_HEADER[:4] == ["country", "level", "shape", "antweb_state"] and pc.TESTED_HEADER[9:] == ["unit_iso_3166_2", "unit_ne_id"]
    # shape, state, what it rests on; the state's specimens in the shape, their share of the state's, of the shape's; what the shape's specimens are filed under
    assert [x[2:9] for x in lines] == [
        ["Equateur", "Equateur", "by name only, 3 specimens", 3, "100", "60", "Equateur 3; Bas-Uele 2"],
        ["Ile Nouvelle", "Nouvelle", "mapping file: state to add in the Geolocale Manager first", 0, "", "0", "Ituri 3"],
        ["Kasai", "Kasai", "name match NOT USED: contradicted by specimens, those of the state lie in Sankuru 18; Kasai 2", 2, "10", "100", "Kasai 2"],
        ["Kivu", "Kivu", "by name; the specimens agree in part", 8, "67", "100", "Kivu 8"],
        ["Lower Uele", "Bas-Uele", "confirmed by specimens", 40, "95", "91", "Bas-Uele 40; Kivu 4"],
        ["Sankuru", "Kasai", "confirmed by specimens", 18, "90", "100", "Kasai 18"]], lines
    assert [x for x in pc.decide_lines(work.shapes) if x[0] == "Congoland"] == [
        ["Congoland", "ADM1", "shape holds several AntWeb states", "Katanga", "Katanga Nord 30; Katanga Sud 20", 50],
        ["Congoland", "ADM1", "AntWeb state lies in several shapes", "Ituri", "Ituri Est 12; Ituri Ouest 10", 25]]
    assert t["stats"]["confirmed"] == 2 and t["stats"]["contradicted"] == 1 and t["stats"]["testable"] == 6 and t["stats"]["located"] == 152
    assert t["matched"] == ["Bas-Uele", "Equateur", "Kasai", "Kivu"] and t["not_matched"] == ["Ituri", "Katanga Nord", "Katanga Sud"]
    assert t["shapes_not_states"] == ["Ituri Est", "Ituri Ouest", "Kasai", "Katanga", "Vide"]
    assert pc.level_line("Congoland", t) == (
        "  Congoland (CGL): ADM1 4/7 AntWeb states, 5/10 shapes [specimens: 2 states confirmed, 1 name matches contradicted, of 6 states with 5 or more located]; "
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
        ["Congoland", "ADM1", "Ituri Ouest", "Ituri", "0.84", 0, "", "Ituri 10"],
        ["Congoland", "ADM1", "Kasai", "Kasai", "1.00", 0, "the state has a shape already: Sankuru", "Kasai 2"]], suggested
    # the report: the numbers of the test in the summary, the constants and what a person has to decide in the tables
    text = pc.report_text("x", "y", "every group", lists, work.shapes, _made["store"], "1 second", 1.0, pc.MAP_FILE, (suggested, left_out), None, evidence)
    top, rest = text.split("\nDetails\n")
    assert ("The shapes and AntWeb's states (mapping_tested.csv), tested with AntWeb's own specimens that have a state and coordinates (176 of every group):\n"
            "  2 shapes confirmed by specimens, 1 by name only, 1 by name with the specimens agreeing in part, 1 by the mapping file, 0 island countries; "
            "1 name matches contradicted by specimens and not used.\n"
            "For a person to decide (to_decide.csv): 1 shapes that hold several AntWeb states, 1 AntWeb states that lie in several shapes, "
            "0 shapes named as one state with the specimens of another.\n"
            "States to add in the Geolocale Manager first (proposed for the specimens in brackets): Congoland: Nouvelle (1)\n") in top, top
    assert len(top.split("\n")) <= pc.SUMMARY_LINES
    for part in ("A shape is a state \"confirmed by specimens\" when at least 5 specimens of the state lie in the shape, they are\nat least 80% of the state's",
                 "at least 80% of the shape's specimens that have a state are filed under it.", "fewer than 50% of them lie in the shape",
                 "        176  used for the test",
                 "         50  Congoland ADM1: the shape Katanga holds Katanga Nord 30; Katanga Sud 20\n         25  Congoland ADM1: AntWeb's Ituri lies in Ituri Est 12; Ituri Ouest 10",
                 "States to add in the Geolocale Manager first (a row of the mapping file marked new), with the specimens they are proposed for:\n          1  Congoland: Nouvelle",
                 "mapping_suggestions.csv: what the test could not settle. 6 lines for 5 shapes"):
        assert part in rest, part + "\n" + rest


def test_natural_earth_states_as_a_second_source():
    ne = natural_earth()
    work, evidence = tested_world()
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
    assert at(-1.0, 21.0) == [pc.Proposal("adm1", "", "Bas-Uele", "coordinates inside Bas-U\u00e9l\u00e9, Natural Earth states CGL", "Congoland", "", "", "", "", "CG-BU", "101")]
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
    assert pc.level_line("Congoland", t) == ("  Congoland (CGL): NE 6/7 AntWeb states, 7/7 shapes [specimens: 4 states confirmed, 0 name matches contradicted, of 6 states with "
                                              "5 or more located] | used: NE (the level with the most AntWeb states confirmed by specimens or matched by name)")
    lines = pc.tested_lines(shapes)
    assert lines[0] == ["Congoland", "NE", "Bas-U\u00e9l\u00e9", "Bas-Uele", "confirmed by specimens, and by the name", 40, "95", "91", "Bas-Uele 40; Kivu 4", "CG-BU", "101"]
    assert [x[4] for x in lines] == ["confirmed by specimens, and by the name"] * 4 + ["mapping file", "by name; the specimens agree in part", "by name only, 3 specimens"]
    lists = pc.Lists()
    record = row("casent0000075", "Congoland", -1.0, 21.0, event="E1")
    lists.add(record, second.work(record), "state missing")
    assert lists.corrections()[0][-4:] == ["CG-BU", "101", "adm1", ""] and lists.events()[0][-4:] == ["CG-BU", "101", "adm1", ""]
    assert pc.CORRECTIONS_HEADER[-4:] == pc.EVENTS_HEADER[-4:] == ["unit_iso_3166_2", "unit_ne_id", "taxonworks_predicate", "biogeographic_region"]
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
    work, evidence = tested_world()
    second = pc.StateShapes(_made["store"], work.states, work.countries, [], evidence, None, ne)
    for country, code in (("Congoland", "CGL"), ("Omanland", "OML"), ("Reunion", "REU")):
        work.shapes.choose(country, code)
        second.choose(country, code)
    # geoBoundaries, Congoland: 69 of 152 specimens lie in the shape of their state, 9 in another state's, 74 in shapes with no state
    assert [work.shapes.tables["Congoland"]["stats"]["shares"][x] for x in pc.SHARES] == [69, 9, 74, 0]
    # the other file: 143 in the unit of their state, 6 in another's, none in a unit with no state, 3 in no unit (the file draws nothing there)
    assert [second.tables["Congoland"]["stats"]["shares"][x] for x in pc.SHARES] == [143, 6, 0, 3]
    lines, totals = pc.compared([("geoBoundaries", work.shapes), (ne.label, second)])
    assert lines[4:] == [
        "  country                      specimens  | geoBoundaries                       | Natural Earth states and provinces",
        "                                          | level   agree  other no st.no unit  | level   agree  other no st.no unit",
        "  ALL COUNTRIES                      176  |          52.8    5.1   42.0    0.0  |          81.2    3.4    0.0   15.3",
        "  Congoland                          152  | ADM1     45.4    5.9   48.7    0.0  | NE       94.1    3.9    0.0    2.0",
        "  Omanland                            24  | ADM2    100.0    0.0    0.0    0.0  | none      0.0    0.0    0.0  100.0",
        "  Reunion                              0  | ADM4  " + " " * 28 + "  | NE*   " + " " * 28], "\n".join(lines)
    assert [sum(x.values()) for x in totals] == [176, 176]
    text = pc.report_text("x", "y", "every group", pc.Lists(), work.shapes, _made["store"], "1 second", 1.0, pc.MAP_FILE, None, None, evidence,
                          [("geoBoundaries", work.shapes), (ne.label, second)], ["Sources compared, geoBoundaries: 3 countries looked up in 2 seconds more."])
    top, rest = text.split("\nDetails\n")
    assert ("Sources compared, the specimens that lie in the unit of their state: geoBoundaries 52.8% of 176; Natural Earth states and provinces 1:10m 81.2% of 176 "
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
    assert pc.no_bioregion(" None ") == "" and pc.no_bioregion("null") == "" and pc.no_bioregion("Nearctic ") == "Nearctic"
    work = pc.Corrections(base.checker, base.states, pc.StateShapes(_made["store"], base.states, base.countries, TEST_MAP), bio)
    lists = pc.Lists()
    records = [row("casent0000111", "Mexico", 25.0, -108.0, event="E1"), row("casent0000112", "", -15.0, 47.0, event="E2"), row("casent0000113", "", 21.5, -159.5, event="E3"),
               row("casent0000114", "Mexico", 27.0, -103.0, event="E4"), row("casent0000115", "", 25.0, -108.0, adm1="Sonora", event="E5")]
    for record in records:
        lists.add(record, work.work(record), work.kind(record))
    # specimen, field, proposed value; the predicate of TaxonWorks for the field; the bioregion of the place as it will be
    assert [(c[0], c[3], c[5], c[-2], c[-1]) for c in lists.corrections()] == [
        ("casent0000112", "country", "Madagascar", "Country", "Malagasy"), ("casent0000112", "adm1", "Antsiranana", "adm1", "Malagasy"),
        ("casent0000115", "country", "Mexico", "Country", "Nearctic"),            # the country proposed for a record that has the state Sonora
        ("casent0000114", "adm1", "Chihuahua", "adm1", ""),                       # the state has two bioregions, and so has the country: left blank
        ("casent0000111", "adm1", "Sonora", "adm1", "Nearctic"),
        ("casent0000113", "country", "United States", "Country", "Oceania"), ("casent0000113", "adm1", "Hawaii", "adm1", "Oceania")], lists.corrections()
    assert sorted((e[0], e[1], e[-2], e[-1]) for e in lists.events()) == [
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
    work, _ = tested_world()
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
    assert line[4:] == ["mapping file: state to add in the Geolocale Manager first", 0, "", "0", "Ituri 3", "", ""]


def test_the_level_by_specimens():
    work, _ = tested_world()
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
    # five of them, all in the shape of Chihuahua: the name match of Sonora is contradicted and not used. And the shape of Chihuahua
    # does not become Sonora: it has the name of an AntWeb state that no specimen speaks against. Neither is used; a person decides
    few.add("Mexico", "Sonora", "27.0", "-104.0", 1)
    tested = pc.Corrections(work.checker, work.states, pc.StateShapes(_made["store"], work.states, work.countries, TEST_MAP, few))
    # (with one of this small country's three states left, by the mapping file, it is below the minimum: no proposals at all)
    assert tested.work(row("casent0000074", "Mexico", 25.0, -108.0)) == [pc.Not("adm1", pc.NO_MATCH, "Sonora", "Mexico")]
    assert tested.work(row("casent0000074", "Mexico", 27.0, -103.0)) == [pc.Not("adm1", pc.NO_MATCH, "Estado de Chihuahua", "Mexico")]
    t = tested.shapes.tables["Mexico"]
    assert [x[2:5] for x in t["lines"]] == [
        ["Sonora", "Sonora", "name match NOT USED: contradicted by specimens, those of the state lie in Estado de Chihuahua 5"],
        ["Estado de Chihuahua", "Sonora", "NOT USED: the specimens say Sonora, the name says Chihuahua: for a person to decide"],
        ["Coahuila de Zaragoza", "Coahuila", "mapping file"]], t["lines"]
    assert t["decide"] == [["Mexico", "ADM1", "shape named as one AntWeb state, its specimens filed under another", "Estado de Chihuahua",
                            "named Chihuahua (0 located specimens); filed under Sonora 5", 5]] and t["stats"]["confirmed"] == 0
    # with the state of the name shown wrong by its own specimens (most of them elsewhere), the specimens in the shape decide
    few.add("Mexico", "Chihuahua", "22.0", "-103.0", 6)           # Chihuahua's own specimens lie in the shape Coahuila de Zaragoza
    moved = pc.Corrections(work.checker, work.states, pc.StateShapes(_made["store"], work.states, work.countries, [], few))
    assert moved.work(row("casent0000074", "Mexico", 27.0, -103.0))[0].basis == "coordinates inside Estado de Chihuahua (Sonora by the specimens), geoBoundaries MEX ADM1"


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
    flag = "stored province differs from the coordinates' region: check the coordinates (Diana is in Antsiranana)"
    assert work.work(m("casent0000092", -15.0, 47.0, "Toliara")) == [pc.Proposal("adm1", "Toliara", "Diana", inside, "Madagascar", "", "", "", flag)]
    assert work.work(m("casent0000093", -15.0, 47.0, "Tulear")) == [pc.Proposal("adm1", "Tulear", "Diana", inside, "Madagascar", "", "", "", flag)]
    # a region the mapping file has no province for: said so, not called a difference
    assert work.work(m("casent0000094", -23.0, 49.0, "Toliara"))[0].note == "the mapping file does not say which province Anosy is in"
    # no coordinates, in the water, outside the country: the record stays with the province
    assert work.work(m("casent0000095", adm1="Toliara")) == [pc.Not("adm1", pc.STAYS, "")]
    assert work.work(m("casent0000096", -24.0, 42.995, "Toliara")) == [pc.Not("adm1", pc.STAYS_OUTSIDE, "in the water, 0.51 km from Atsimo-Andrefana, geoBoundaries MDG ADM1", "Madagascar")]
    assert work.work(m("casent0000097", -30.0, 45.0, "Toliara")) == [pc.Not("adm1", pc.OUTSIDE, "in the water, 445 km from Madagascar")]
    # a missing state gets the region; a record that has a region, another state, or its island country, is left as it is
    assert work.work(m("casent0000098", -15.0, 47.0)) == [pc.Proposal("adm1", "", "Diana", inside, "Madagascar")]
    assert work.work(m("casent0000099", -15.0, 47.0, "Diana")) == [] and work.work(m("casent0000100", -15.0, 47.0, "Nosy Be")) == []
    assert work.work(m("casent0000101", -15.0, 47.0, "Toliara", island="Nosy Be")) == []
    assert work.work(row("casent0000102", "Mexico", 25.0, -108.0, adm1="Toliara")) == []           # another country: no conversion
    assert work.kind(m("x", adm1="Toliara")) == "state is a province to convert to its region" and work.kind(m("x", adm1="Diana")) == "has a state (left as it is)"
    assert setup().work(m("casent0000103", -15.0, 47.0, "Antsiranana")) == []                      # no "convert" line in the mapping file: nothing
    # the provinces are left out of the test and of the states the shapes are compared with: 450 specimens of Toliara decide nothing
    t = shapes.tables["Madagascar"]
    assert shapes.wanted("Madagascar") == ["Analamanga", "Anosy", "Atsimo-Andrefana", "Diana"] and t["decide"] == [] and t["stats"]["located"] == 6
    assert [x[2:5] for x in t["lines"]] == [["Diana", "Diana", "confirmed by specimens, and by the name"], ["Atsimo-Andrefana", "Atsimo-Andrefana", "by name only, 0 specimens"],
                                           ["Anosy", "Anosy", "by name only, 0 specimens"], ["Analamanga", "Analamanga", "by name only, 0 specimens"]], t["lines"]
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
    assert ("          6  state is a province to convert to its region\n          1  state missing\n") in top
    assert ("State (adm1): 1 specimens with a proposal (corrections.csv), in 1 lines of by_collecting_event.csv\n") in top
    assert ("State (adm1), from a province to its region: 5 specimens with a proposal (corrections.csv), in 4 lines of by_collecting_event.csv\n"
            "  0 of them (0 lines) lie within the stated error, or the allowed 1 km, of another state: column other_state_flag, look at those first.\n"
            "  3 of them: stored province differs from the coordinates' region: check the coordinates (column note). By region: Diana 3.\n"
            "  No proposal (not_derivable.csv): 1 specimens, by reason:\n          1  no coordinates: stays with the province\n") in top, top
    assert "State (adm1), from a province to its region, proposed values (the 40 largest):\n          2  Madagascar: Antsiranana -> Diana\n          2  Madagascar: Toliara -> Diana\n          1  Madagascar: Fianarantsoa -> Diana\n" in text
    # the statement that reads the records: the provinces of Madagascar, by the stored spellings of the country
    sql = pc.rows_sql({"code", "country", "adm1"}, 1, [], [(["Madagascar"], ["Toliara", "Tulear"])])
    assert " or (hex(s.country) in ('4D616461676173636172') and s.adm1 in ('Toliara', 'Tulear'))" in sql and cc.read_only(sql) == sql


# Madagascar as AntWeb has it now: the provinces and the regions, both valid; and what the mapping file says of them
MDG_STATES = [(name, "Madagascar", 1, "") for name in ("Antananarivo", "Antsiranana", "Fianarantsoa", "Toliara", "Diana", "Atsimo-Andrefana", "Anosy", "Analamanga")] + [
    ("Tulear", "Madagascar", 0, "Toliara")]
MDG_RULES = pc.Rules(convert={"Madagascar": ["Antananarivo", "Antsiranana", "Fianarantsoa", "Mahajanga", "Toliara"]},
                     within={"Madagascar": {"Diana": "Antsiranana", "Atsimo-Andrefana": "Toliara", "Analamanga": "Antananarivo"}})


def test_madagascar_by_region():
    work = setup()
    C = cc.Countries([cc.Country("Madagascar", 1, "", 0, "")])
    six = [(name, "Madagascar", 1, "") for name in ("Antananarivo", "Antsiranana", "Fianarantsoa", "Mahajanga", "Toamasina", "Toliara")]
    assert pc.provinces_in_antweb(pc.States(six, C)) and not pc.provinces_in_antweb(pc.States(six[:3] + [("Diana", "Madagascar", 1, "")], C))
    assert not pc.provinces_in_antweb(work.states)                # the test world has two of them
    # AntWeb with provinces and regions; the mapping file says which province a region is in
    states = pc.States(MDG_STATES, work.countries)
    shapes = pc.StateShapes(_made["store"], states, work.countries, [], None, MDG_RULES)
    # (stored state, latitude, longitude, specimens) of every record of Madagascar
    rows = [("Antsiranana", "-15.0", "47.0", 10), ("", "-15.0", "47.0", 2), ("Toliara", "-15.0", "47.0", 1), ("Tulear", "-24.0", "45.0", 5), ("", "", "", 7),
            ("", "0.0", "0.0", 1), ("", "-30.0", "45.0", 3), ("Antananarivo", "-20.0", "49.0", 4), ("", "NULL", "NULL", 2), ("", "-24.0", "42.995", 1200),
            ("Diana", "-15.0", "47.0", 6), ("Nosy Be", "-15.0", "47.0", 1), ("Toliara", "-23.0", "49.0", 2)]
    lines = pc.madagascar_regions(shapes, states, "MDG", rows, 1.0, "California Academy of Sciences (id 1)")
    assert lines[:3] == ["", "Madagascar by region: every record of Madagascar of the group by the region its coordinates lie in, not only the records with a gap; "
                         "geoBoundaries MDG ADM1, 4 shapes.",
                         "  Records of Madagascar: 1,244. With coordinates in one region: 1,231. No coordinates: 9. Coordinates that cannot be used: 1. "
                         "In no region's shape (in the water or outside): 3. On the line between two regions: 0."], lines
    cells = [line.split(None, 1)[0:1] + line[28:].split() for line in lines[4:]]
    assert lines[3].split() == ["region", "specimens", "its", "province", "stored", "state:", "that", "province", "the", "region", "another", "none"]
    # Diana: 20 inside; 10 have its province, 6 the region itself, 2 another state (Toliara, and a name AntWeb does not know), 2 none.
    # A point in the water within 1 km counts. Anosy's province is not in the mapping file of this test.
    assert cells == [["Analamanga", "4", "Antananarivo", "4", "0", "0", "0"], ["Anosy", "2", "not", "in", "the", "mapping", "file", "0", "0", "2", "0"],
                     ["Atsimo-Andrefana", "1,205", "Toliara", "5", "0", "0", "1,200"], ["Diana", "20", "Antsiranana", "10", "6", "2", "2"]], cells
    sql = pc.madagascar_sql(["Madagascar", "MADAGASCAR "], 1)
    assert cc.read_only(sql) == sql and "group by 1, 2, 3" in sql and "access_group = 1" in sql and "4D4144414741534341522" in sql and "Madagascar" not in sql
    text = pc.report_text("x", "y", "every group", pc.Lists(), work.shapes, _made["store"], "1 second", 1.0, pc.MAP_FILE, None, lines)
    assert "\nMadagascar by region: every record" in text and text.index("Madagascar by region") > text.index("\nDetails\n")


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
    assert "adm1_shape_map.csv: 6 rows, " in text
    missing = pc.report_text("x", "y", "every group", lists, work.shapes, _made["store"], "1 second", 1.0, "/nowhere/adm1_shape_map.csv")
    assert "Mapping file /nowhere/adm1_shape_map.csv: NOT FOUND (fetch it next to the tool), " in missing


def test_every_statement_is_a_single_select():
    columns = {"code", "collectioncode", "localitycode", "country", "adm1", "adm2", "island_country", "latlonmaxerror", "localityname", "localitynotes", "bioregion"}
    statements = [pc.places_sql({"name"}), pc.places_sql({"name", "bioregion", "alt_bioregion"}), pc.stored_countries_sql(), pc.stored_countries_sql(1), pc.rows_sql(columns), pc.rows_sql(columns, 1, ["Africa", "O'Brien; drop table specimen"]),
                  pc.rows_sql({"code", "country", "adm1"}, 21), pc.madagascar_sql(["Madagascar"]), pc.madagascar_sql(["Madagascar", "O'Brien; drop table specimen"], 1),
                  pc.evidence_sql(columns), pc.evidence_sql({"code"}),
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
    assert "subprocess" not in source and "os.system" not in source and source.count("cc.rows_sql(") == 2 and source.count("cc.run_sql(") == 1
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
