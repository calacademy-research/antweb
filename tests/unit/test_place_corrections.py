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
    ]}


def countries():
    C = cc.Country
    return cc.Countries([C("Mexico", 1, "", 0, "", "MX", "MEX"), C("United States", 1, "", 0, "", "US", "USA"), C("USA", 0, "United States", 0, ""),
                         C("Hawaii", 1, "", 1, "United States"), C("Madagascar", 1, "", 0, "", "MG", "MDG"), C("Reunion", 1, "", 0, "", "RE", "REU"),
                         C("Tinyland", 1, "", 0, "", "TN", "TNY"), C("Atoll", 1, "", 0, "", "AT", "ATL")])


# AntWeb's states: (name, parent, is_valid, valid_name)
STATES = [("Sonora", "Mexico", 1, ""), ("Chihuahua", "Mexico", 1, ""), ("Coahuila", "Mexico", 1, ""), ("Chih.", "Mexico", 0, "Chihuahua"),
          ("Arizona", "United States", 1, ""), ("New Mexico", "United States", 1, ""), ("Hawaii", "United States", 1, ""),
          ("Antsiranana", "Madagascar", 1, ""), ("Toliara", "Madagascar", 1, ""), ("Tulear", "Madagascar", 0, "Toliara"),
          ("Saint-Denis", "Reunion", 1, ""), ("Saint-Pierre", "Reunion", 1, ""), ("Lagoon", "Atoll", 1, ""),
          ("Lost", "Nowhere", 1, "")]

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
}


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
        state_shapes = pc.StateShapes(store, states, known, pc.read_map(pc.MAP_FILE))
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
    assert t["tried"] == ["ADM1: no file", "ADM2: no file", "ADM3: 2 shapes, 0 of 2 AntWeb states matched", "ADM4: 3 shapes, 2 of 2 AntWeb states matched"], t["tried"]
    assert t["matched"] == ["Saint-Denis", "Saint-Pierre"] and t["not_matched"] == [] and t["shapes_not_states"] == ["Le Port"] and t["level"] == "ADM4"
    # Mexico: ADM1 fits all three (one by its name, one without "Estado de", one by the mapping file): the search ends there
    chosen = shapes.choose("Mexico", "MEX")
    assert chosen.level == "ADM1" and chosen.states == ["Sonora", "Chihuahua", "Coahuila"]
    assert chosen.hows == ["name", "name without the unit word", "mapping file"]
    assert shapes.tables["Mexico"]["tried"] == ["ADM1: 3 shapes, 3 of 3 AntWeb states matched"]
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
    assert all(len(r) == 4 and r[1] in pc.LEVELS for r in rows) and ("country", "level", "shape", "antweb_state") not in rows
    mdg = {shape: state for country, level, shape, state in rows if country == "Madagascar" and level == "ADM1"}
    # the 22 shape names of the file geoBoundaries-MDG-ADM1 (read 2026-11-10), each with its province
    regions = ["Diana", "Sava", "Analanjirofo", "Amoron'i Mania", "Ihorombe", "Melaky", "Menabe", "Vakinankaratra", "Atsinanana", "Alaotra-Mangoro", "Sofia",
               "Anosy", "Boeny", "Betsiboka", "Analamanga", "Bongolava", "Itasy", "Atsimo-Andrefana", "Androy", "Atsimo-Atsinanana", "Matsiatra Ambony",
               "Vatovavy-Fitovinany"]
    assert all(r in mdg for r in regions) and len(regions) == 22
    assert sorted(set(mdg.values())) == ["Antananarivo", "Antsiranana", "Fianarantsoa", "Mahajanga", "Toamasina", "Toliara"]
    count = lambda province: sum(1 for r in regions if mdg[r] == province)
    assert [count(p) for p in ("Antananarivo", "Antsiranana", "Fianarantsoa", "Mahajanga", "Toamasina", "Toliara")] == [4, 2, 5, 4, 3, 4]
    assert mdg["Diana"] == "Antsiranana" and mdg["Atsimo-Andrefana"] == "Toliara" and mdg["Matsiatra Ambony"] == "Fianarantsoa" and mdg["Sofia"] == "Mahajanga"
    assert ("Mexico", "ADM1", "Coahuila de Zaragoza", "Coahuila") in rows and ("United States", "ADM1", "District of Columbia", "Washington, D.C.") in rows
    # a row is used only when AntWeb has the state: Analamanga's province is not in this AntWeb
    assert w("Madagascar", -20.0, 49.0) == [pc.Not("adm1", pc.NOT_A_STATE, "Analamanga", "Madagascar")]
    # a region of a province AntWeb has
    assert w("Madagascar", -15.0, 47.0) == [pc.Proposal("adm1", "", "Antsiranana", "coordinates inside Diana (Antsiranana by the mapping file), geoBoundaries MDG ADM1", "Madagascar")]
    # two regions of one province are one state: next to their border the proposal stands
    assert w("Madagascar", -24.0, 46.999)[0].proposed == "Toliara"
    assert ("Madagascar", "ADM1", "Diana", "Antsiranana") in setup().shapes.rows_used


def test_inside_one_state():
    assert w("Mexico", 25.0, -108.0) == [pc.Proposal("adm1", "", "Sonora", "coordinates inside Sonora, geoBoundaries MEX ADM1", "Mexico")]
    assert w("USA", 35.0, -108.0) == [pc.Proposal("adm1", "", "Arizona", "coordinates inside Arizona, geoBoundaries USA ADM1", "United States")]
    # a record that has a state is not touched
    assert w("Mexico", 25.0, -108.0, adm1="Sonora") == []


def test_near_two_states():
    # 0.5 km from the border of Sonora and Chihuahua: both named, no proposal
    assert w("Mexico", 27.0, -105.005) == [pc.Not("adm1", pc.TWO, "Sonora or Chihuahua", "Mexico")]
    # 2 km from it: a proposal; with a stated error of 3 km again two candidates
    assert w("Mexico", 27.0, -105.02)[0].proposed == "Sonora"
    assert w("Mexico", 27.0, -105.02, stated="3 km") == [pc.Not("adm1", pc.TWO, "Sonora or Chihuahua", "Mexico")]
    # next to a shape that is no AntWeb state: two candidates as well, the shape named as what it is
    assert w("Madagascar", -22.005, 49.0) == [pc.Not("adm1", pc.TWO, "Toliara or Analamanga (not a state in AntWeb)", "Madagascar")]


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


def test_reasons_for_no_proposal():
    assert w("Tinyland", 0.5, 0.5) == [pc.Not("adm1", pc.NO_STATES, "Tinyland", "Tinyland")]              # the country has no states in AntWeb
    assert w("Mexico") == [pc.Not("adm1", pc.NO_COORDINATES, "")]
    assert w("Mexico", 0, 0) == [pc.Not("adm1", pc.NO_COORDINATES, "")] and w("Mexico", 95, -100) == [pc.Not("adm1", pc.NO_COORDINATES, "")]
    assert w("Atoll", 0.5, 10.5) == [pc.Not("adm1", pc.NO_FILE, "geoBoundaries has no file of the divisions of ATL", "Atoll")]
    assert w("Reunion", -21.0, 55.9) == [pc.Not("adm1", pc.NOT_A_STATE, "Le Port", "Reunion")]
    assert w("Reunion", -21.0, 55.2) == [pc.Proposal("adm1", "", "Saint-Denis", "coordinates inside Saint-Denis, geoBoundaries REU ADM4", "Reunion")]


def test_country_missing():
    r = w("", 25.0, -108.0)
    assert r == [pc.Proposal("country", "", "Mexico", "coordinates inside Mexico, Natural Earth 1:10m", "Mexico"),
                 pc.Proposal("adm1", "", "Sonora", "coordinates inside Sonora, geoBoundaries MEX ADM1", "Mexico")], r
    # in the water within 1 km of one country
    r = w("", 25.0, -110.005)
    assert [x.proposed for x in r] == ["Mexico", "Sonora"] and r[0].basis == "in the water, 0.5 km from Mexico, Natural Earth 1:10m", r
    # an island country is proposed as its parent country
    r = w("", 21.5, -159.5)
    assert r[0] == pc.Proposal("country", "", "United States", "coordinates inside Hawaii, Natural Earth 1:10m (Hawaii is an island country of United States in AntWeb)", "United States")
    assert r[1].proposed == "Hawaii" and r[1].field == "adm1"
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
    assert corrections[4] == ["casent0000011", "BLF100", "L1", "adm1", "", "Sonora", "coordinates inside Sonora, geoBoundaries MEX ADM1", "25.0", "-108.0", "",
                              "Sierra, 5 km N", "Mexico", "California Academy of Sciences"]
    events = {(e[0], e[1]): e for e in lists.events()}
    # a collecting event whose specimens get different proposals is flagged
    e = events[("BLF100", "adm1")]
    assert e[3] == "Chihuahua | Sonora" and e[4] == 3 and e[5:7] == ["casent0000011", "casent0000013"], e
    assert e[10] == "DIFFERENT PROPOSALS, do not enter as it stands: Chihuahua (1); Sonora (2). 2 different coordinates in this collecting event" and e[12] == ""
    e = events[("BLF200", "adm1")]
    assert e[3] == "Sonora" and e[4] == 1 and e[10] == "1 more of this collecting event in not_derivable.csv" and e[12].startswith("coordinates inside Sonora")
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
    assert read[0] == ["specimen_code", "collecting_event_code", "locality_code", "field", "current_value", "proposed_value", "basis", "latitude", "longitude",
                       "stated_error", "locality_name", "country", "upload_group"] and read[5] == corrections[4]
    text = pc.report_text("2026-11-10 09:00", "abc1234", "California Academy of Sciences (id 1)", lists, work.shapes, _made["store"], "3 seconds", 1.0)
    for part in ("Upload group: California Academy of Sciences (id 1)",
                 "Records looked at (no state, no country, or a country AntWeb does not know): 12",
                 "        8  state missing", "        3  country missing", "        1  state missing, island-country specimen (left out)",
                 "Country: 1 specimens with a proposal (corrections.csv), in 1 lines of by_collecting_event.csv",
                 "  No proposal (not_derivable.csv): 2 specimens, by reason:\n          2  no coordinates",
                 "State (adm1): 6 specimens with a proposal (corrections.csv), in 4 lines of by_collecting_event.csv, 1 of them flagged for different proposals",
                 "          2  coordinates outside the stated country", "          1  no coordinates",
                 "          4  Mexico: Sonora", "          1  Madagascar: Antsiranana",
                 "  Mexico: geoBoundaries MEX ADM1 (ADM1: 3 shapes, 3 of 3 AntWeb states matched)",
                 "    AntWeb states matched (3): Chihuahua; Coahuila; Sonora",
                 "    matched in another way than by the name itself: Coahuila de Zaragoza -> Coahuila (mapping file); Estado de Chihuahua -> Chihuahua (name without the unit word)",
                 "  Madagascar: geoBoundaries MDG ADM1", "    shapes that are no state in AntWeb (1): Analamanga",
                 "  Madagascar ADM1: Diana -> Antsiranana", "  Mexico ADM1: Coahuila de Zaragoza -> Coahuila",
                 "    Open Data Commons Open Database License 1.0: MDG ADM1_simplified",
                 "Report only: nothing was changed in the database."):
        assert part in text, part + "\n" + text
    assert "nightly" not in text.lower()


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
    assert "adm1_shape_map.csv: 34 rows, " in text
    missing = pc.report_text("x", "y", "every group", lists, work.shapes, _made["store"], "1 second", 1.0, "/nowhere/adm1_shape_map.csv")
    assert "Mapping file /nowhere/adm1_shape_map.csv: NOT FOUND (fetch it next to the tool), " in missing


def test_every_statement_is_a_single_select():
    columns = {"code", "collectioncode", "localitycode", "country", "adm1", "adm2", "island_country", "latlonmaxerror", "localityname", "localitynotes", "bioregion"}
    statements = [pc.ADM1_SQL, pc.stored_countries_sql(), pc.stored_countries_sql(1), pc.rows_sql(columns), pc.rows_sql(columns, 1, ["Africa", "O'Brien; drop table specimen"]),
                  pc.rows_sql({"code", "country", "adm1"}, 21)]
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
    assert "subprocess" not in source and "os.system" not in source and source.count("cc.rows_sql(") == 1 and source.count("cc.run_sql(") == 1
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
