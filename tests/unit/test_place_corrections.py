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
    ]}


def countries():
    C = cc.Country
    return cc.Countries([C("Mexico", 1, "", 0, "", "MX", "MEX"), C("United States", 1, "", 0, "", "US", "USA"), C("USA", 0, "United States", 0, ""),
                         C("Hawaii", 1, "", 1, "United States"), C("Madagascar", 1, "", 0, "", "MG", "MDG"), C("Reunion", 1, "", 0, "", "RE", "REU"),
                         C("Tinyland", 1, "", 0, "", "TN", "TNY"), C("Atoll", 1, "", 0, "", "AT", "ATL"),
                         C("Andesia", 1, "", 0, "", "AD", "ADS"), C("Iberia", 1, "", 0, "", "IB", "IBR")])


# AntWeb's states: (name, parent, is_valid, valid_name)
STATES = [("Sonora", "Mexico", 1, ""), ("Chihuahua", "Mexico", 1, ""), ("Coahuila", "Mexico", 1, ""), ("Chih.", "Mexico", 0, "Chihuahua"),
          ("Arizona", "United States", 1, ""), ("New Mexico", "United States", 1, ""), ("Hawaii", "United States", 1, ""),
          ("Antsiranana", "Madagascar", 1, ""), ("Toliara", "Madagascar", 1, ""), ("Tulear", "Madagascar", 0, "Toliara"),
          ("Saint-Denis", "Reunion", 1, ""), ("Saint-Pierre", "Reunion", 1, ""), ("Lagoon", "Atoll", 1, ""),
          ("Lost", "Nowhere", 1, ""),
          ("Norte", "Andesia", 1, ""), ("Sur", "Andesia", 1, ""),
          ("Andalusia", "Iberia", 1, ""), ("Catalonia", "Iberia", 1, ""), ("Madrid", "Iberia", 1, ""), ("Navarre", "Iberia", 1, ""),
          ("Navarra", "Iberia", 0, "Navarre")]

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
    assert pc.CORRECTIONS_HEADER[6:10] == ["basis", "nearest_other_state", "km_to_other_state", "other_state_flag"]
    assert [x[7:10] for x in c] == [["Chihuahua", "0.5", "within the stated error (1.85 km) of Chihuahua"]] * 2 + [["", "", ""]] + [
        ["Chihuahua", "0.5", "within the allowed 1 km of Chihuahua"], ["", "", ""]], c
    events = {e[0]: e for e in lists.events()}
    assert pc.EVENTS_HEADER[10:14] == ["flag", "nearest_other_state", "km_to_other_state", "other_state_flag"]
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
    return pc.Corrections(work.checker, states, pc.StateShapes(_made["store"], states, work.countries, pc.read_map(pc.MAP_FILE)))


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
    assert corrections[4] == ["casent0000011", "BLF100", "L1", "adm1", "", "Sonora", "coordinates inside Sonora, geoBoundaries MEX ADM1", "", "", "", "25.0", "-108.0", "",
                              "Sierra, 5 km N", "Mexico", "California Academy of Sciences"]
    events = {(e[0], e[1]): e for e in lists.events()}
    # a collecting event whose specimens get different proposals is flagged
    e = events[("BLF100", "adm1")]
    assert e[3] == "Chihuahua | Sonora" and e[4] == 3 and e[5:7] == ["casent0000011", "casent0000013"], e
    assert e[10] == "DIFFERENT PROPOSALS, do not enter as it stands: Chihuahua (1); Sonora (2). 2 different coordinates in this collecting event" and e[15] == ""
    e = events[("BLF200", "adm1")]
    assert e[3] == "Sonora" and e[4] == 1 and e[10] == "1 more of this collecting event in not_derivable.csv" and e[15].startswith("coordinates inside Sonora")
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
                       "km_to_other_state", "other_state_flag", "latitude", "longitude", "stated_error", "locality_name", "country", "upload_group"] and read[5] == corrections[4]
    text = pc.report_text("2026-11-10 09:00", "abc1234", "California Academy of Sciences (id 1)", lists, work.shapes, _made["store"], "3 seconds", 1.0)
    for part in ("Upload group: California Academy of Sciences (id 1)",
                 "Records looked at (no state, no country, or a country AntWeb does not know): 12",
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
    lines, without, left_out = pc.suggestions(work.shapes, work.states, lists.in_shapes())
    wanted = ("Iberia", "Madagascar", "Reunion")
    assert [x for x in lines if x[0] in wanted] == [
        ["Iberia", "ADM1", "Comunidad de Madrid", "Madrid", "1.00", 2, ""],
        ["Iberia", "ADM1", "Andaluc\u00eda", "Andalusia", "0.89", 1, ""],
        ["Iberia", "ADM1", "Islas", "", "", 1, "no AntWeb state of Iberia has a name like it"],
        ["Iberia", "ADM1", "Catalu\u00f1a/Catalunya", "Catalonia", "0.82", 0, ""],
        ["Iberia", "ADM1", "Comunidad Foral de Navarra", "Navarre", "1.00", 0, "by AntWeb's old name Navarra"],
        ["Madagascar", "ADM1", "Analamanga", "", "", 1, "no AntWeb state of Madagascar has a name like it"],
        ["Reunion", "ADM4", "Le Port", "", "", 1, "no AntWeb state of Reunion has a name like it"]], lines
    # the other side: AntWeb's valid states that no shape was matched to, with the shape most like each
    assert [x for x in without if x[0] == "Iberia"] == [["Iberia", "ADM1", "Andalusia", "Andaluc\u00eda", "0.89"], ["Iberia", "ADM1", "Catalonia", "Catalu\u00f1a/Catalunya", "0.82"],
                                                        ["Iberia", "ADM1", "Madrid", "Comunidad de Madrid", "1.00"], ["Iberia", "ADM1", "Navarre", "Comunidad Foral de Navarra", "1.00"]]
    assert pc.SUGGESTIONS_HEADER[:4] == ["country", "level", "shape", "antweb_state"]
    # the report: the country in the summary, the count by reason, the line of its levels
    text = pc.report_text("x", "y", "every group", lists, work.shapes, _made["store"], "1 second", 1.0, pc.MAP_FILE, (lines, without, left_out))
    top = text.split("\nDetails\n")[0]
    assert ("AntWeb's states and the boundary file do not match: needs a mapping (1 country, 4 specimens; see mapping_suggestions.csv):\n  Iberia ADM1 (4)\n") in top, top
    assert "          4  AntWeb's states and the boundary file do not match: needs a mapping\n          2  the boundary file's name is not a state in AntWeb\n" in top
    assert len(top.split("\n")) <= pc.SUMMARY_LINES
    assert "  Iberia: geoBoundaries IBR ADM1 (NOT USED: below the minimum)\n" in text and "mapping_suggestions.csv: " in text
    assert "antweb_states_without_shape.csv: " in text
    # nothing is used from the suggestions; pasted into the mapping file, the approved lines make the country usable
    with tempfile.TemporaryDirectory() as folder:
        pc.write_csv(os.path.join(folder, "mapping_suggestions.csv"), pc.SUGGESTIONS_HEADER, lines)
        pasted = [r for r in pc.read_map(os.path.join(folder, "mapping_suggestions.csv")) if r[0] == "Iberia" and r[3]]
    assert ("Iberia", "ADM1", "Comunidad de Madrid", "Madrid") in pasted and len(pasted) == 4
    mapped = pc.Corrections(work.checker, work.states, pc.StateShapes(_made["store"], work.states, work.countries, pasted))
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


def test_madagascar_by_region():
    work = setup()
    C = cc.Countries([cc.Country("Madagascar", 1, "", 0, "")])
    six = [(name, "Madagascar", 1, "") for name in ("Antananarivo", "Antsiranana", "Fianarantsoa", "Mahajanga", "Toamasina", "Toliara")]
    assert pc.provinces_in_antweb(pc.States(six, C)) and not pc.provinces_in_antweb(pc.States(six[:3] + [("Diana", "Madagascar", 1, "")], C))
    assert not pc.provinces_in_antweb(work.states)                # the test world has two of them
    # (stored state, latitude, longitude, specimens) of every record of Madagascar
    rows = [("Antsiranana", "-15.0", "47.0", 10), ("", "-15.0", "47.0", 2), ("Toliara", "-15.0", "47.0", 1), ("Tulear", "-24.0", "45.0", 5), ("", "", "", 7),
            ("", "0.0", "0.0", 1), ("", "-30.0", "45.0", 3), ("Antananarivo", "-20.0", "49.0", 4), ("", "NULL", "NULL", 2), ("", "-24.0", "42.995", 1200)]
    lines = pc.madagascar_regions(work.shapes, work.states, "MDG", rows, 1.0, "California Academy of Sciences (id 1)")
    assert lines[:3] == ["", "Madagascar by region: what a conversion from the provinces to the regions would give. Every record of Madagascar of the group, "
                         "not only those with a gap; geoBoundaries MDG ADM1, 4 shapes.",
                         "  Records of Madagascar: 1,235. With coordinates in one region: 1,222. No coordinates: 9. Coordinates that cannot be used: 1. "
                         "In no region's shape (in the water or outside): 3. On the line between two regions: 0."], lines
    cells = [line.split(None, 1)[0:1] + line[28:].split() for line in lines[4:]]
    assert lines[3].split() == ["region", "specimens", "province", "(mapping", "file)", "stored", "state:", "that", "province", "another", "none"]
    # Diana: 13 inside, 10 with the province of the mapping file, 1 with another state, 2 with none. A point in the water within 1 km counts.
    assert cells == [["Analamanga", "4", "no", "province", "AntWeb", "has", "0", "4", "0"], ["Anosy", "0", "Toliara", "0", "0", "0"],
                     ["Atsimo-Andrefana", "1,205", "Toliara", "5", "0", "1,200"], ["Diana", "13", "Antsiranana", "10", "1", "2"]], cells
    sql = pc.madagascar_sql(["Madagascar", "MADAGASCAR "], 1)
    assert cc.read_only(sql) == sql and "group by 1, 2, 3" in sql and "access_group = 1" in sql and "4D4144414741534341522" in sql and "Madagascar" not in sql
    text = pc.report_text("x", "y", "every group", pc.Lists(), work.shapes, _made["store"], "1 second", 1.0, pc.MAP_FILE, None, lines)
    assert "\nMadagascar by region: what a conversion" in text and text.index("Madagascar by region") > text.index("\nDetails\n")


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
                  pc.rows_sql({"code", "country", "adm1"}, 21), pc.madagascar_sql(["Madagascar"]), pc.madagascar_sql(["Madagascar", "O'Brien; drop table specimen"], 1)]
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
