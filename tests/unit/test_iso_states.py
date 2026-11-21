"""tools/place/iso_states.py: AntWeb's states under their ISO 3166-2 names (2026-11-20)."""
import collections
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "tools", "place"))
import iso_states as iso  # noqa: E402

S = iso.Sub
SK = [S("SK-BC", "SK", "Region", "", "Banskobystrický kraj", []), S("SK-BL", "SK", "Region", "", "Bratislavský kraj", ["Bratislava"])]
ES = [S("ES-AN", "ES", "Autonomous community", "", "Andalucía", ["Andalusia"]), S("ES-CO", "ES", "Province", "ES-AN", "Córdoba", []),
      S("ES-MD", "ES", "Autonomous community", "", "Madrid, Comunidad de", ["Madrid"]), S("ES-M", "ES", "Province", "ES-MD", "Madrid", []),
      S("ES-CT", "ES", "Autonomous community", "", "Catalunya [Cataluña]", ["Catalonia"])]
UA = [S("UA-65", "UA", "Region", "", "Khersonska oblast", ["Kherson Oblast"]), S("UA-30", "UA", "City", "", "Kyiv", ["Kiev"]), S("UA-32", "UA", "Region", "", "Kyivska oblast", [])]


def st(i, name, n=0, olds=()):
    return iso.State(i, name, n, tuple(olds))


def test_display():
    assert iso.display("Zagreb, Grad") == "Grad Zagreb"
    assert iso.display("Adygeya, Respublika") == "Respublika Adygeya"
    assert iso.display("Catalunya [Cataluña]") == "Catalunya"
    assert iso.display("Armagh City, Banbridge and Craigavon") == "Armagh City, Banbridge and Craigavon"
    assert iso.display("Bristol, City of") == "Bristol, City of"
    # an inverted form that ends in a bracket is turned round before notes in brackets are dropped (review of #395)
    assert iso.display("Găgăuzia, Unitatea teritorială autonomă (UTAG)") == "Unitatea teritorială autonomă (UTAG) Găgăuzia"


def test_rename_and_merge():
    d, rep = iso.match_country([st(1, "Banskobystrický", 3), st(2, "Banskobystrický kraj", 9), st(3, "Bratislava", 5)], SK)
    got = sorted((a, s.name, sub.code, k.name) for a, s, sub, k in d)
    # the one written as ISO is kept; the other becomes an old name of it; Bratislava (another name) takes the ISO name
    assert got == [("merge", "Banskobystrický", "SK-BC", "Banskobystrický kraj"), ("rename", "Bratislava", "SK-BL", "Bratislava")], got
    assert not rep


def test_the_main_kind_decides():
    # Spain's states are communities: Madrid is the community, not the province; Andalusia by its English name
    d, rep = iso.match_country([st(1, "Andalusia"), st(2, "Madrid"), st(3, "Catalonia"), st(4, "Cataluña")], ES)
    got = sorted((a, s.name, sub.code, iso.display(sub.name)) for a, s, sub, k in d)
    assert ("rename", "Madrid", "ES-MD", "Comunidad de Madrid") in got and ("rename", "Andalusia", "ES-AN", "Andalucía") in got, got
    assert ("merge", "Cataluña", "ES-CT", "Catalunya") in got or ("merge", "Catalonia", "ES-CT", "Catalunya") in got, got


def test_old_names_and_loose():
    # an old name of the state finds the subdivision; a looser match (generic words off) only when it is alone
    d, rep = iso.match_country([st(1, "Kherson"), st(2, "Kiev City", olds=("Kiev",)), st(3, "Kiev")], UA)
    got = sorted((a, s.name, sub.code) for a, s, sub, k in d)
    assert ("rename", "Kherson", "UA-65", ) == got[[g[1] for g in got].index("Kherson")][:3], got
    names = {s.name for a, s, sub, k in d}
    assert "Kiev" in names and "Kiev City" in names        # both written like Kyiv (one by its old name): merged
    assert not [r for r in rep if r[0].name == "Kherson"]


def test_not_matched_is_reported():
    d, rep = iso.match_country([st(1, "Diana")], [S("MG-D", "MG", "Province", "", "Antsiranana", [])])
    assert not d and rep[0][1].startswith("no ISO")


def test_statements_swap_and_insert():
    dec = [{"country": "Croatia", "action": "rename", "id": 5, "name": "Zagreb, Grad", "specimens": 2, "code": "HR-21", "type": "City",
            "iso": "Grad Zagreb", "kept_id": 5, "kept_name": "Zagreb, Grad", "target": "Grad Zagreb"}]
    sql, left, done = iso.statements(dec, {("Croatia", "Grad Zagreb"): [(9, 0, "Zagreb, Grad")]}, "20261120")
    assert not left and sql[0].startswith("update geolocale set name = convert(unhex('") and "where id = 9" in sql[0]
    assert any("is_valid = 0" in x and "where id = 9" in x for x in sql) and not any(x.startswith("insert") for x in sql)
    sql, left, done = iso.statements(dec, {}, "20261120")
    assert any(x.startswith("insert into geolocale") for x in sql)
    sql, left, done = iso.statements(dec, {("Croatia", "Grad Zagreb"): [(9, 1, "")]}, "20261120")
    assert left and not sql


def dec(action, i, name, iso_name, kept_id, kept_name, country="Slovakia", code="SK-BL"):
    return {"country": country, "action": action, "id": i, "name": name, "specimens": 1, "code": code, "type": "Region",
            "iso": iso_name, "kept_id": kept_id, "kept_name": kept_name, "target": iso_name}


def test_merge_statements():
    d = [dec("rename", 1, "Bratislava", "Bratislavský kraj", 1, "Bratislava"), dec("merge", 2, "Bratislavsky", "Bratislavský kraj", 1, "Bratislava")]
    sql, left, done = iso.statements(d, {}, "20261120")
    assert not left and done == d
    merge = [x for x in sql if "where id = 2" in x]
    assert merge and "is_valid = 0" in merge[0] and "unhex('%s')" % "Bratislavský kraj".encode().hex() in merge[0]
    assert not any(x.startswith("update specimen") for x in sql)        # the specimens move from the backup table, in main
    assert iso.moved_specimens("iso_states_spec_20261120") == ("update specimen s join iso_states_spec_20261120 b on b.code = s.code"
                                                               " set s.adm1 = b.new_adm1 where binary s.adm1 = binary b.adm1")


def test_a_left_out_rename_stops_its_merges():
    # the ISO name is an old name of a third state: the rename is left out, and so is the merge into it
    d = [dec("rename", 1, "Bratislava", "Bratislavský kraj", 1, "Bratislava"), dec("merge", 2, "Bratislavsky", "Bratislavský kraj", 1, "Bratislava")]
    sql, left, done = iso.statements(d, {("Slovakia", "Bratislavský kraj"): [(7, 0, "Trnava")]}, "20261120")
    assert not sql and not done and len(left) == 2 and "not merged" in left[1]


def test_a_curator_list_stops_the_merge():
    d = [dec("merge", 2, "Bratislavsky", "Bratislava", 1, "Bratislava")]
    sql, left, done = iso.statements(d, {}, "20261120", {2: 14})
    assert not sql and not done and "curator species list of 14" in left[0]


def test_a_loose_match_never_merges():
    # "Kiev Oblast" loosened is "Kiev": it finds the city, but a loose match does not join the city's merge
    subs = [S("UA-30", "UA", "City", "", "Kyiv", ["Kiev"]), S("UA-32", "UA", "Region", "", "Kyivska oblast", [])]
    d, rep = iso.match_country([st(1, "Kiev", 50), st(2, "Kiev Oblast", 30)], subs)
    assert not [x for x in d if x[0] == "merge"], d
    assert {r[0].name for r in rep} == {"Kiev", "Kiev Oblast"}


def test_a_lone_loose_match_that_adds_a_word_is_reported():
    # "Kiev Oblast" alone, no "Kiev": loosened it finds the city, but it adds "oblast": not renamed to Kyiv
    subs = [S("UA-30", "UA", "City", "", "Kyiv", ["Kiev"]), S("UA-32", "UA", "Region", "", "Kyivska oblast", [])]
    d, rep = iso.match_country([st(1, "Kiev Oblast", 30)], subs)
    assert not d and rep[0][0].name == "Kiev Oblast" and "adds a generic word" in rep[0][1], (d, rep)


def test_a_shared_loose_match_is_reported():
    # both loosen to "kherson"; "Kherson Province" adds a generic word ISO's names do not have: looked at by hand
    subs = [S("UA-65", "UA", "Region", "", "Khersonska oblast", ["Kherson Oblast"])]
    d, rep = iso.match_country([st(1, "Kherson"), st(2, "Kherson Province")], subs)
    assert not d and {r[0].name for r in rep} == {"Kherson", "Kherson Province"}, (d, rep)


def test_the_reviewed_plan_reads_back():
    import tempfile
    d = [dec("rename", 1, "Bratislava", "Bratislavský kraj", 1, "Bratislava"), dec("merge", 2, "Bratislavsky", "Bratislavský kraj", 1, "Bratislava")]
    path = os.path.join(tempfile.mkdtemp(), "plan.tsv")
    iso.write_plan(path, d, [("Slovakia", "X", 3, "no ISO")], [("Atlantis", 2)], {2: 4}, [(10, "name,Slovakia,ADM1,Bratislava,Bratislava")])
    assert iso.reviewed_lines(path) == iso.decision_lines(d)
    assert iso.reviewed_lines(path) != iso.decision_lines(d[:1])


def test_mapping_lines():
    import tempfile
    path = os.path.join(tempfile.mkdtemp(), "map.csv")
    with open(path, "w", encoding="utf-8") as f:
        f.write("# kind,...\nname,Slovakia,ADM1,Bratislavsky kraj,Bratislava\nname,Austria,ADM1,Wien,Bratislava\n")
    got = iso.mapping_lines([dec("rename", 1, "Bratislava", "Bratislavský kraj", 1, "Bratislava")], path)
    assert [n for n, line in got] == [2], got


def test_a_country_in_another_script_keeps_its_english_names():
    # Brian Fisher 2026-11-20: ISO code, English names. Cairo and Al Qāhirah are one: Cairo is kept, Al Qāhirah merged
    # into it; Giza keeps its name and Al Jīzah is added as an old name; Suez already has its ISO name as an old name
    eg = [S("EG-C", "EG", "Governorate", "", "Al Qāhirah", ["Cairo"]), S("EG-GZ", "EG", "Governorate", "", "Al Jīzah", ["Giza"]),
          S("EG-SUZ", "EG", "Governorate", "", "As Suways", ["Suez"])]
    d, rep = iso.match_country([st(1, "Cairo", 17), st(2, "Al Qāhirah", 40), st(3, "Giza", 2), st(4, "Suez", 4, olds=("As Suways",))], eg, english=True)
    got = sorted((a, s.name, k.name) for a, s, sub, k in d)
    assert got == [("alias", "Giza", "Giza"), ("merge", "Al Qāhirah", "Cairo")], got
    assert not rep
    out, reported, nocountry = iso.plan({"Egypt": [(1, "Cairo", 17), (2, "Al Qāhirah", 40)]}, {}, {"EG": eg}, {iso.key("Egypt"): "EG"})
    assert [(x["action"], x["name"], x["target"]) for x in out] == [("merge", "Al Qāhirah", "Cairo")], out
    sql, left, done = iso.statements(out, {}, "20261120")
    assert "unhex('%s')" % "Cairo".encode().hex() in sql[0] and "where id = 2" in sql[0], sql
    d = [dict(dec("alias", 3, "Giza", "Al Jīzah", 3, "Giza", "Egypt", "EG-GZ"), target="Giza")]
    sql, left, done = iso.statements(d, {}, "20261120")
    assert len(sql) == 1 and sql[0].startswith("insert into geolocale") and "added, ISO 3166-2 name" in bytes.fromhex(sql[0].split("unhex('")[-1].split("'")[0]).decode()
    assert not iso.moves(d)
    sql, left, done = iso.statements(d, {("Egypt", "Al Jīzah"): [(9, 0, "Gizeh")]}, "20261120")
    assert not sql and left


def test_iso_names_come_before_other_names():
    # the boundary file puts México state's code on its Distrito Federal shape and Tillabéri's on Niamey: a state written
    # like an ISO name goes to that subdivision, not to one whose other names happen to include it
    mx = iso.read_iso()["MX"]
    d, rep = iso.match_country([st(1, "Ciudad de México", 3), st(2, "México", 40)], mx)
    assert not [x for x in d if x[0] == "merge"], d
    ne = iso.read_iso()["NE"]
    d, rep = iso.match_country([st(1, "Niamey", 3), st(2, "Tillabéri", 5)], ne)
    assert not d, d


def test_notes_in_brackets_and_slashes():
    assert iso.display("Western Visayas (Region VI)") == "Western Visayas"
    assert iso.display("Guelmim-Oued Noun (EH-partial)") == "Guelmim-Oued Noun"
    assert iso.display("Maritime (Région)") == "Maritime"
    ke = [S("KE-39", "KE", "County", "", "Taita/Taveta", [])]
    d, rep = iso.match_country([st(1, "Taita Taveta", 15)], ke)
    assert [(a, s.name) for a, s, sub, k in d] == [("alias", "Taita Taveta")], d


def test_a_skipped_state_of_an_outdated_country_is_left_out():
    out, reported, nocountry = iso.plan({"Madagascar": [(1, "Diana", 9), (2, "Sava", 4)]}, {}, iso.read_iso(), iso.read_countries(),
                                        skip={"Madagascar|Sava"})
    assert [r[1] for r in reported] == ["Diana"] and not out


def test_outdated_countries_are_reported():
    subs = iso.read_iso()
    out, reported, nocountry = iso.plan({"Central African Republic": [(1, "Sangha-Mbaéré", 2566)], "India": [(2, "Karnataka", 147)]}, {},
                                        subs, iso.read_countries())
    assert reported[0][:2] == ("Central African Republic", "Sangha-Mbaéré") and "2020" in reported[0][3], reported
    assert [(x["action"], x["name"], x["iso"]) for x in out] == [("alias", "Karnataka", "Karnātaka")], out


def test_the_mapping_file_follows_the_renames():
    import tempfile
    path = os.path.join(tempfile.mkdtemp(), "map.csv")
    with open(path, "w", encoding="utf-8") as f:
        f.write("# kinds\n"
                "name,Sweden,ADM1,Gotlands län,Gotland,the same name\n"                     # now found by name: dropped
                "name,Croatia,ADM1,Split-Dalmatia,Splitsko-Dalmatinska,Croatian name\n"     # renamed in place
                "convert,Malawi,Central,Northern,Southern\n"
                'United States,ADM1,District of Columbia,"Washington, D.C."\n'
                "within,Sweden,Gotland,X\n"
                "Croatia,ADM1,Zagreb County,Splitsko-Dalmatinska,new\n"
                "name,Norway,ADM1,Oslo,Oslo,untouched\n")
    d = [dict(dec("rename", 1, "Gotland", "Gotlands län", 1, "Gotland", "Sweden", "SE-I")),
         dict(dec("rename", 2, "Splitsko-Dalmatinska", "Splitsko-dalmatinska županija", 2, "Splitsko-Dalmatinska", "Croatia", "HR-17")),
         dict(dec("rename", 3, "Southern", "Southern Region", 3, "Southern", "Malawi", "MW-S")),
         dict(dec("rename", 4, "Washington, D.C.", "District of Columbia", 4, "Washington, D.C.", "United States", "US-DC")),
         dict(dec("alias", 5, "Oslo", "Oslo kommune", 5, "Oslo", "Norway", "NO-03"), target="Oslo")]
    text, changed, check = iso.mapping_update(d, path)
    lines = text.split("\n")
    assert "name,Sweden,ADM1,Gotlands län,Gotland,the same name" not in lines
    assert "name,Croatia,ADM1,Split-Dalmatia,Splitsko-dalmatinska županija,Croatian name" in lines, lines
    assert "convert,Malawi,Central,Northern,Southern Region" in lines
    assert "United States,ADM1,District of Columbia,\"Washington, D.C.\"" not in lines         # now by name: dropped
    assert "name,Norway,ADM1,Oslo,Oslo,untouched" in lines and [n for n, l in check] == [6]
    with open(path, "a", encoding="utf-8") as f:
        f.write("stray\n")
    text, changed, check = iso.mapping_update(d, path)
    assert check[-1][1] == "stray"
    # a plain line marked new keeps its new mark and is never dropped
    assert "Croatia,ADM1,Zagreb County,Splitsko-dalmatinska županija,new" in lines, lines


def test_page_text_moves_with_a_rename_and_blocks_a_conflict():
    d = [dec("rename", 1, "Bratislava", "Bratislavský kraj", 1, "Bratislava"), dec("merge", 2, "Bratislavsky", "Bratislavský kraj", 1, "Bratislava")]
    moved = []
    sql, left, done = iso.statements(d, {}, "20261120", texts={"Bratislava": 2}, allnames={"Bratislava": 1}, text_moves=moved)
    assert moved == [("object_edit", "Bratislava", "Bratislavský kraj")] and sum(x.startswith("update object_edit") for x in sql) == 1, sql
    # each moved row is recorded for the rollback just before its move
    i = [k for k, x in enumerate(sql) if x.startswith("update object_edit")][0]
    assert sql[i - 1].startswith("insert into {text} select 'object_edit', object_key"), sql[i - 1]
    # another place with the name written with other accents shares the text: it stays (Córdoba, Cordoba)
    moved = []
    sql, left, done = iso.statements(d, {}, "20261120", texts={"Bratislava": 2}, allnames={iso.normal("Bratislava"): 2}, text_moves=moved)
    assert not moved
    # the keys as stored move (a history row written "bratislava"); a name with history only is a conflict too
    moved = []
    sql, left, done = iso.statements(d, {}, "20261120", texts={"object_edit": {"Bratislava": 1}, "object_hist": {"bratislava": 3}},
                                     allnames={"Bratislava": 1}, text_moves=moved)
    assert moved == [("object_edit", "Bratislava", "Bratislavský kraj"), ("object_hist", "bratislava", "Bratislavský kraj")], moved
    assert any("binary object_key = binary convert(unhex('%s')" % "bratislava".encode().hex() in x for x in sql)
    sql, left, done = iso.statements(d, {}, "20261120", texts={"object_edit": {}, "object_hist": {"Bratislavský kraj": 2}}, allnames={})
    assert not done and "already has page text" in left[0], left
    # text under a name another place also has stays where it is
    moved = []
    sql, left, done = iso.statements(d, {}, "20261120", texts={"Bratislava": 2}, allnames={"Bratislava": 2}, text_moves=moved)
    assert not moved and done == d
    # the ISO name already has text, written with other accents (object_edit's key ignores them): left out with its merge
    sql, left, done = iso.statements(d, {}, "20261120", texts={"Bratislavsky kraj": 1}, allnames={})
    assert not done and "already has page text" in left[0] and "not merged" in left[1], left
    # the merged state's text joins the kept one, unless both have text
    sql, left, done = iso.statements(d[1:], {}, "20261120", texts={"Bratislavsky": 1, "Bratislavský kraj": 1}, allnames={"Bratislavsky": 1})
    assert not done and "both have page text" in left[0], left
    # the state's own old-name row written otherwise is not another place: the text moves; a real other place: a note
    moved, notes = [], []
    sql, left, done = iso.statements(d[:1], {("Slovakia", "Bratislavá"): [(9, 0, "Bratislava")]}, "20261120", texts={"Bratislava": 1},
                                     allnames={iso.normal("Bratislava"): 2}, text_moves=moved, notes=notes)
    assert moved and not notes, (moved, notes)
    moved, notes = [], []
    sql, left, done = iso.statements(d[:1], {}, "20261120", texts={"Bratislava": 1}, allnames={iso.normal("Bratislava"): 2}, text_moves=moved, notes=notes)
    assert not moved and "stays under Bratislava" in notes[0] and done, notes
    # two aliases to one ISO name in one country: the second is left out
    al = [dict(dec("alias", 1, "A", "Same", 1, "A"), target="A"), dict(dec("alias", 2, "B", "Same", 2, "B"), target="B")]
    sql, left, done = iso.statements(al, {}, "20261120")
    assert len(done) == 1 and left
    # page text and a new name another place already has (page text is keyed by name alone): left out
    sql, left, done = iso.statements(d[:1], {}, "20261120", texts={"Bratislava": 1}, allnames={iso.normal("Bratislava"): 1, iso.normal("Bratislavský kraj"): 1})
    assert not done and "another place is already named" in left[0], left
    # two states of one country renamed to one name: the second is left out
    two = [dec("rename", 1, "A", "Same", 1, "A"), dec("rename", 2, "B", "Same", 2, "B")]
    sql, left, done = iso.statements(two, {}, "20261120")
    assert [x["name"] for x in done] == ["A"] and "renamed Same too" in left[0], left
    # a name longer than a specimen's state can be is left out
    long = "X" * 129
    sql, left, done = iso.statements([dec("rename", 3, "Y", long, 3, "Y")], {}, "20261120")
    assert not done and "longer than" in left[0]


def test_a_convert_line_keeps_each_name_once():
    import tempfile
    path = os.path.join(tempfile.mkdtemp(), "map.csv")
    with open(path, "w", encoding="utf-8") as f:
        f.write("convert,Philippines,ARMM,Autonomous Region in Muslim Mindanao,Bicol\n")
    d = [dec("merge", 1, "ARMM", "Autonomous Region in Muslim Mindanao", 2, "Autonomous Region in Muslim Mindanao", "Philippines", "PH-14")]
    text, changed, check = iso.mapping_update(d, path)
    assert text.split("\n")[0] == "convert,Philippines,Autonomous Region in Muslim Mindanao,Bicol", text


def test_the_name_comes_before_old_names():
    # AntWeb has Cordoba as an old name of Andalusia: Andalusia is the community by its English name, not the province
    es = iso.read_iso()["ES"]
    d, rep = iso.match_country([st(1, "Andalusia", 247, olds=("Cordoba",)), st(2, "Catalonia", 302), st(3, "Madrid", 41), st(4, "Murcia", 22)], es)
    got = {s.name: sub.code for a, s, sub, k in d}
    assert got["Andalusia"] == "ES-AN", got
    # but an old name written as ISO's own name counts before the boundary file's shape names (review of #396): a state
    # "Distrito Federal" with the old name Ciudad de México is Mexico City, not México state (whose shape carries DF)
    mx = iso.read_iso()["MX"]
    d, rep = iso.match_country([st(1, "Distrito Federal", 5, olds=("Ciudad de México",)), st(2, "México", 40)], mx)
    assert ("Distrito Federal", "MX-CMX") in {(s.name, sub.code) for a, s, sub, k in d} and not [x for x in d if x[0] == "merge"], d


def test_capitals_only_and_apostrophes():
    za = [S("ZA-NL", "ZA", "Province", "", "Kwazulu-Natal", [])]
    d, rep = iso.match_country([st(1, "KwaZulu-Natal", 6734)], za)
    assert not d and not rep, (d, rep)
    assert iso.display("Grande’Anse") == "Grande'Anse" and iso.display("Provence-Alpes-Côte-d’Azur") == "Provence-Alpes-Côte-d'Azur"


def test_notes_and_left_outs_are_part_of_the_reviewed_plan():
    import tempfile
    d = [dec("rename", 1, "Bratislava", "Bratislavský kraj", 1, "Bratislava")]
    path = os.path.join(tempfile.mkdtemp(), "plan.tsv")
    iso.write_plan(path, d, [], [], {}, [], ["Slovakia: X, why"], ["Slovakia: Bratislava, its page text stays"])
    got = iso.reviewed_lines(path)
    assert "note\tSlovakia: Bratislava, its page text stays" in got and "left out\tSlovakia: X, why" in got, got


def test_crlf_mapping_and_a_name_of_punctuation():
    import tempfile
    path = os.path.join(tempfile.mkdtemp(), "map.csv")
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write("name,Croatia,ADM1,Split-Dalmatia,Splitsko-Dalmatinska,Croatian name\r\n")
    d = [dec("rename", 2, "Splitsko-Dalmatinska", "Splitsko-dalmatinska županija", 2, "Splitsko-Dalmatinska", "Croatia", "HR-17")]
    text, changed, check = iso.mapping_update(d, path)
    assert text.split("\n")[0] == "name,Croatia,ADM1,Split-Dalmatia,Splitsko-dalmatinska županija,Croatian name", text
    d, rep = iso.match_country([st(1, "--", 1)], [S("HR-17", "HR", "County", "", "Splitsko-dalmatinska županija", [])])
    assert not d and rep and rep[0][1].startswith("no ISO"), rep


def test_every_exonym_is_a_subdivision_of_its_country():
    subs, countries = iso.read_iso(), iso.read_countries()
    by_a2 = collections.defaultdict(list)
    for country, names in iso.EXONYMS.items():
        a2 = countries[iso.key(country)]
        codes = {x.code for x in subs[a2]}
        for name, code in names.items():
            assert code in codes, (country, name, code)
        by_a2[a2] += [iso.key(n) for n in names]
    # looked up by key() under the ISO country code: two entries of one country (under any of its names) must not share
    # a key, the later would win unseen (review of #400)
    for a2, keys in by_a2.items():
        assert len(set(keys)) == len(keys), a2
    seen = collections.Counter((countries[iso.key(c)], iso.key(n)) for c, n in iso.NOT_ISO)
    assert not [k for k, v in seen.items() if v > 1], seen
    # a state is in one table or the other, never both (NOT_ISO would win unseen: fourth review of #400)
    exo = {(countries[iso.key(c)], iso.key(n)) for c, names in iso.EXONYMS.items() for n in names}
    assert not exo & set(seen), exo & set(seen)
    for (country, name), why in iso.NOT_ISO.items():
        assert iso.key(country) in countries and why


def test_exonyms_and_states_that_are_not_iso():
    subs = iso.read_iso()
    # Spain's communities by AntWeb's English names; Germany's Länder; Graubünden and Canton Grisons are one canton
    out, rep, noc = iso.plan({"Spain": [(1, "Canary Islands", 154), (2, "Navarre", 1), (3, "Basque Country", 3)],
                              "Germany": [(4, "Lower Saxony", 47)], "Switzerland": [(5, "Graubünden", 3814), (6, "Canton Grisons", 2)]},
                             {}, subs, iso.read_countries())
    got = {(x["action"], x["name"]): x["target"] for x in out}
    assert got[("rename", "Canary Islands")] == "Canarias" and got[("rename", "Navarre")] == "Comunidad Foral de Navarra", got
    assert got[("rename", "Lower Saxony")] == "Niedersachsen" and got[("merge", "Canton Grisons")] == "Graubünden", got
    assert got[("rename", "Basque Country")] == "País Vasco", got
    # Quezon City is a city of Metro Manila, not Quezon province: never merged into it
    out, rep, noc = iso.plan({"Philippines": [(7, "Quezon", 857), (8, "Quezon City", 900)]}, {}, subs, iso.read_countries())
    assert not [x for x in out if x["action"] == "merge"] and ("Philippines", "Quezon City") in {(r[0], r[1]) for r in rep}, (out, rep)
    assert [r for r in rep if r[1] == "Quezon City"][0][3].startswith("not an ISO 3166-2 subdivision: a city of Metro Manila")
    # Quezon City alone (no loose rule to fall back on) and written otherwise: still not Quezon province; a table entry
    # found with other capitals or accents (Castilla-Leon for Castile-Leon is not one; CASTILE-LEON is)
    out, rep, noc = iso.plan({"Philippines": [(8, "quezon city", 900)], "Spain": [(9, "CASTILE-LEON", 37)]}, {}, subs, iso.read_countries())
    assert [(x["name"], x["code"]) for x in out] == [("CASTILE-LEON", "ES-CL")], (out, rep)
    assert [r for r in rep if r[1] == "quezon city"][0][3].startswith("not an ISO"), rep


def test_a_table_entry_decides_over_iso_names_and_finds_its_country_by_any_name():
    # ISO's data has "Kiev" as a name of the city UA-30; AntWeb's Kiev is the oblast (the table), Kiev City the city
    d, rep = iso.match_country([st(1, "Kiev", 7), st(2, "Kiev City", 3)], UA, english=True, exonyms={"Kiev": "UA-32", "Kiev City": "UA-30"})
    assert {(s.name, sub.code) for a, s, sub, k in d} == {("Kiev", "UA-32"), ("Kiev City", "UA-30")}, d
    # the table is found by the country's ISO code: Laos written as ISO writes it
    out, rep, noc = iso.plan({"Lao People's Democratic Republic": [(1, "Savannahkhét", 2)]}, {}, iso.read_iso(), iso.read_countries())
    # (since 2026-11-21 the transliteration Savannahkhét is renamed to the English name)
    assert [(x["action"], x["code"], x["target"]) for x in out] == [("rename", "LA-SV", "Savannakhet")], (out, rep)
    # a table entry does not decide the country's main kind: a state written like a region and its prefecture keeps
    # the kind the country's own matches give (here prefectures)
    gn = [S("GN-K", "GN", "Administrative region", "", "Kankán", []), S("GN-KA", "GN", "Prefecture", "GN-K", "Kankan", []),
          S("GN-KO", "GN", "Prefecture", "GN-K", "Kouroussa", []), S("GN-SI", "GN", "Prefecture", "GN-K", "Siguiri", []),
          S("GN-N", "GN", "Administrative region", "", "Nzérékoré", []), S("GN-B", "GN", "Administrative region", "", "Boké", []),
          S("GN-M", "GN", "Administrative region", "", "Mamou", [])]
    states = [st(1, "Nzerekore", 3), st(2, "Boke", 2), st(3, "Mamou", 1), st(4, "Kouroussa", 1), st(5, "Siguiri", 1), st(6, "Kankan", 5)]
    d, rep = iso.match_country(states, gn, exonyms={"Nzerekore": "GN-N", "Boke": "GN-B", "Mamou": "GN-M"})
    # three table regions against two matched prefectures: counted, the regions would win and Kankan become Kankán
    assert "Kankan" not in {s.name for a, s, sub, k in d}, d


def test_a_name_two_subdivisions_carry_is_not_a_match():
    by = iso.read_iso()["BY"]
    # without the table: never merged (the ambiguity rule itself is tested on an inline fixture below, so this holds
    # whether or not a rebuilt file still carries the wrong translation: fifth review of #400)
    d, rep = iso.match_country([st(1, "Brest", 3), st(2, "Vitebsk", 0)], by, english=True)
    assert not [x for x in d if x[0] == "merge"], d
    # with the table (as plan() passes it): Brest is Brest, Vitebsk is Vitebsk
    out, rep, noc = iso.plan({"Belarus": [(1, "Brest", 3), (2, "Vitebsk", 0)]}, {}, iso.read_iso(), iso.read_countries())
    assert {(x["name"], x["code"]) for x in out} == {("Brest", "BY-BR"), ("Vitebsk", "BY-VI")} and not [x for x in out if x["action"] == "merge"], out
    out, rep, noc = iso.plan({"Latvia": [(3, "Jelgavas", 0), (4, "Jelgava", 0), (5, "Rēzeknes", 0), (6, "Rēzekne", 0)]}, {},
                             iso.read_iso(), iso.read_countries())
    got = {(x["action"], x["name"], x["target"]) for x in out}
    # the first pass merged the municipalities into the state cities (review of #400): no merge, each stays its own
    assert ("rename", "Jelgavas", "Jelgavas novads") in got and ("rename", "Rēzeknes", "Rēzeknes novads") in got, out
    assert not [x for x in out if x["action"] == "merge"], out
    assert {x["code"] for x in out if x["name"] == "Jelgavas"} == {"LV-041"} and {x["code"] for x in out if x["name"] == "Rēzeknes"} == {"LV-077"}, out
    assert not [x for x in out if x["name"] in ("Jelgava", "Rēzekne") and x["code"] in ("LV-041", "LV-077")], out


def test_the_state_s_own_name_loosely_comes_before_its_old_names():
    # the first pass renamed Liberecký (old name Liberec) to the district Liberec and Kauno (old name Kaunas) to the
    # city municipality Kauno miestas: the state's own name, generic words off, within the country's main kind, counts first
    cz = iso.read_iso()["CZ"]
    # with a state written exactly as the capital, which must not decide the kind for the loose states (fifth review)
    d, rep = iso.match_country([st(1, "Liberecký", 4, olds=("Liberec",)), st(2, "Karlovarský", 2, olds=("Karlovy Vary",)),
                                st(3, "Jihomoravský", 9), st(4, "Zlínský", 1, olds=("Zlín",)), st(5, "Hlavní město Praha", 20)], cz)
    got = {s.name: (a, sub.code) for a, s, sub, k in d}
    assert got == {"Liberecký": ("rename", "CZ-51"), "Karlovarský": ("rename", "CZ-41"), "Jihomoravský": ("rename", "CZ-64"),
                   "Zlínský": ("rename", "CZ-72")}, (got, rep)
    assert not [r for r in rep if r[0].name != "Hlavní město Praha"], rep
    lt = iso.read_iso()["LT"]
    d, rep = iso.match_country([st(1, "Kauno", 6, olds=("Kaunas",)), st(2, "Vilniaus", 3, olds=("Vilnius",)), st(3, "Utenos", 1),
                                st(4, "Alytaus", 2)], lt)
    got = {s.name: (a, sub.code, iso.display(sub.name)) for a, s, sub, k in d}
    assert got["Kauno"] == ("rename", "LT-KU", "Kauno apskritis") and got["Vilniaus"][1] == "LT-VL" and got["Alytaus"][1] == "LT-AL", (got, rep)
    assert not [x for x in d if x[0] == "merge"], d


def test_an_old_name_of_the_main_kind_that_disagrees_is_reported():
    # review of #400: Liberecký loosely is Liberecký kraj, but an old name written as another region (Karlovarský kraj)
    # disagrees: reported, not renamed
    cz = iso.read_iso()["CZ"]
    d, rep = iso.match_country([st(1, "Liberecký", 4, olds=("Karlovarský kraj",)), st(2, "Jihomoravský kraj", 9),
                                st(3, "Plzeňský kraj", 3)], cz)
    assert "Liberecký" not in {s.name for a, s, sub, k in d}, d
    assert [r[1] for r in rep if r[0].name == "Liberecký"][0].startswith("loosely CZ-51 (Liberecký kraj), but an old name is written as CZ-41"), rep


def test_a_loose_own_name_at_another_level_than_the_country_s_states_is_reported():
    # review of #400: one state is a region by its own name (the early kind), but most states end up districts through
    # old names (the final kind): Liberecký, found loosely as a region, is looked at by hand, not renamed
    cz = iso.read_iso()["CZ"]
    d, rep = iso.match_country([st(1, "Plzeňský kraj", 3), st(2, "Liberecký", 4), st(3, "A", 1, olds=("Benešov",)),
                                st(4, "B", 1, olds=("Beroun",)), st(5, "C", 1, olds=("Kladno",))], cz)
    assert "Liberecký" not in {s.name for a, s, sub, k in d}, d
    assert [r[1] for r in rep if r[0].name == "Liberecký"][0].startswith("loose match CZ-51 (Liberecký kraj, a Region) while"), rep


def test_two_states_found_loosely_by_their_own_names_are_not_merged():
    # third review of #400: "Liberecký" and a stray "Liberecky" both find Liberecký kraj loosely: looked at by hand
    cz = iso.read_iso()["CZ"]
    d, rep = iso.match_country([st(1, "Liberecký", 4), st(2, "Liberecky", 1), st(3, "Jihomoravský kraj", 9)], cz)
    assert not [x for x in d if x[0] == "merge"], d
    assert {"Liberecký", "Liberecky"} <= {r[0].name for r in rep}, rep


def test_a_translation_and_a_shape_name_of_two_subdivisions_are_reported():
    # the Brest case on an inline fixture (fourth review of #400: not tied to the shipped file's wrong translation):
    # "Brest" is a translation of one subdivision and the shape name of another: reported with both codes
    by = [S("BY-BR", "BY", "Oblast", "", "Brestskaya voblasts'", [], ["Brest"]), S("BY-VI", "BY", "Oblast", "", "Vitsyebskaya voblasts'", ["Brest"], [])]
    d, rep = iso.match_country([st(1, "Brest", 3)], by, english=True)
    assert not d and rep[0][1].startswith("written like BY-BR, BY-VI"), (d, rep)


def test_a_tie_of_kinds_is_no_main_kind():
    # sixth review of #400: one vote each must not depend on the order the database returns the states
    c = collections.Counter
    assert iso.top_kind(c({"Region": 1, "City": 1})) is None and iso.top_kind(c({"Region": 2, "City": 1})) == "Region"
    assert iso.top_kind(c()) is None
    a = iso.match_country([st(1, "Kherson"), st(3, "Kiev")], UA)
    b = iso.match_country([st(3, "Kiev"), st(1, "Kherson")], UA)
    norm = lambda r: (sorted((x[0], x[1].name, x[2].code) for x in r[0]), sorted((x[0].name, x[1]) for x in r[1]))
    assert norm(a) == norm(b), (norm(a), norm(b))


def test_english_names_for_countries_in_another_script():
    # 2026-11-21: AntWeb kept its own names for these countries, and they were often transliterations, ISO's
    # romanization or names cut short, not English (Attikí, Al Başrah, Chelyabin): those are renamed to the English name
    subs = iso.read_iso()
    by = {s.code: s for a2 in ("GR", "RU", "IQ", "EG", "IR") for s in subs[a2]}
    yes = [("Attikí", "GR-I"), ("Al Başrah", "IQ-BA"), ("Eşfahān", "IR-10"), ("Chelyabin", "RU-CHE"), ("Kur", "RU-KRS"),
           ("Moskov", "RU-MOS"), ("Moskva", "RU-MOW"), ("Kamchatskiy", "RU-KAM"), ("Nizhegorod", "RU-NIZ")]
    no = [("Crete", "GR-M"), ("Amur", "RU-AMU"), ("Sharqia", "EG-SHR"), ("Chuvashiya", "RU-CU"), ("Basra", "IQ-BA")]
    for name, code in yes:
        assert iso.needs_english(name, by[code]), (name, code, iso.ENGLISH_NAMES.get(code))
    for name, code in no:
        assert not iso.needs_english(name, by[code]), (name, code, iso.ENGLISH_NAMES.get(code))
    assert iso.ENGLISH_NAMES["RU-MOS"] != iso.ENGLISH_NAMES["RU-MOW"] and iso.ENGLISH_NAMES["RU-AL"] != iso.ENGLISH_NAMES["RU-ALT"]
    out, rep, noc = iso.plan({"Greece": [(1, "Attikí", 14), (2, "Crete", 3)]}, {}, iso.read_iso(), iso.read_countries())
    got = {(x["action"], x["name"], x["target"]) for x in out}
    assert ("rename", "Attikí", "Attica") in got and not [x for x in out if x["name"] == "Crete" and x["action"] == "rename"], out
    assert [x["iso"] for x in out if x["name"] == "Attikí"] == ["Attica"], out


def test_the_english_table_is_complete_and_has_no_shared_names():
    # every entry is an ISO code of an ENGLISH country, and no two units of one country share an English name
    subs = iso.read_iso()
    codes = {s.code for a2 in subs for s in subs[a2]}
    seen = collections.Counter()
    for code, name in iso.ENGLISH_NAMES.items():
        assert code in codes and code[:2] in iso.ENGLISH, code
        assert name and name == name.strip() and "\t" not in name, code
        seen[(code[:2], iso.key(name))] += 1
    assert not [k for k, v in seen.items() if v > 1], [k for k, v in seen.items() if v > 1]
    # complete: every subdivision of the 25 covered countries has its English name (a rebuilt iso3166_2.tsv with a new
    # or recoded subdivision fails here instead of leaving its state silently in transliteration: review of #403)
    covered = {code[:2] for code in iso.ENGLISH_NAMES}
    assert covered == set("AE AF BH EG ER GR IQ IR JO JP KG KH KP KW KZ LA LB LY MN OM QA RU SY TJ YE".split()), sorted(covered)
    missing = [s.code for a2 in sorted(covered) for s in subs[a2] if s.code not in iso.ENGLISH_NAMES]
    assert not missing, missing
    # page text is keyed by name: no English name shared across countries, none a bare compass word or "Capital"
    across = collections.defaultdict(set)
    for code, name in iso.ENGLISH_NAMES.items():
        across[iso.key(name)].add(code[:2])
        assert iso.key(name) not in {"capital", "north", "south", "east", "west", "northern", "southern", "eastern", "western",
                                     "central", "centre"}, (code, name)
    assert not [k for k, v in across.items() if len(v) > 1], [k for k, v in across.items() if len(v) > 1]


def test_english_rename_review_cases():
    # review of #403: (1) a loosely matched transliteration is renamed, never filed as an alias under the English name;
    # (2) a state already written in English is kept and the other merged into it, no rename; (3) a renamed state
    # keeps ISO's own form as an old name, under its English name
    countries, subs = iso.read_countries(), iso.read_iso()
    out, rep, noc = iso.plan({"Russia": [(1, "Chelyabinskaya", 4)]}, {}, subs, countries)
    got = {(x["action"], x["name"], x["iso"], x["target"]) for x in out}
    assert got == {("rename", "Chelyabinskaya", "Chelyabinsk", "Chelyabinsk"),
                   ("alias", "Chelyabinskaya", "Chelyabinskaya oblast'", "Chelyabinsk")}, (got, rep)
    # the 2021 stem with ISO's form as its old name (the first pass added it), beside a state written in English
    olds = {("Russia", "Chelyabin"): ["Chelyabinskaya oblast'"]}
    out, rep, noc = iso.plan({"Russia": [(1, "Chelyabinsk", 2), (2, "Chelyabin", 50)]}, olds, subs, countries)
    got = {(x["action"], x["name"], x["target"]) for x in out}
    assert got == {("merge", "Chelyabin", "Chelyabinsk")}, (got, rep)
    out, rep, noc = iso.plan({"Russia": [(1, "Chelyabin", 50)]}, olds, subs, countries)
    got = {(x["action"], x["name"], x["target"]) for x in out}
    assert got == {("rename", "Chelyabin", "Chelyabinsk")}, got        # ISO's form is an old name already


def test_an_alias_waits_for_its_english_rename():
    # second review of #403: when the rename to the English name is left out (here: another state is an old name
    # Chelyabinsk already), the ISO form is not added under that name either
    out, rep, noc = iso.plan({"Russia": [(1, "Chelyabinskaya", 4)]}, {}, iso.read_iso(), iso.read_countries())
    names = {("Russia", "Chelyabinsk"): [(7, 0, "Kurgan")]}
    sql, left, done = iso.statements(out, names, "20261121")
    assert not done and not sql, (done, sql)
    assert len(left) == 2 and any("not added: the rename to Chelyabinsk was left out" in l for l in left), left


def test_bern_not_berne():
    # 2026-11-21: ISO gives the canton two names; AntWeb uses Bern (the canton's language and the English name)
    out, rep, noc = iso.plan({"Switzerland": [(1, "Berne", 198)]}, {}, iso.read_iso(), iso.read_countries())
    assert [(x["action"], x["target"]) for x in out] == [("rename", "Bern")], out
    # production after the first pass: "Bern" is an old-name row of "Berne": the rename swaps them (review of #403)
    sql, left, done = iso.statements(out, {("Switzerland", "Bern"): [(9, 0, "Berne")]}, "20261121")
    assert not left and [d["target"] for d in done] == ["Bern"], (left, done)
    assert sql[0] == "update geolocale set name = %s where id = 9" % iso.q("~iso~9"), sql    # the old-name row moved aside first


def test_a_name_ending_at_a_word_is_not_cut_short():
    # review of #403: Almaty is not a stem of "Almaty oblysy"; Moskov is a stem of "Moskovskaya oblast'"
    subs = {s.code: s for s in iso.read_iso()["KZ"] + iso.read_iso()["RU"]}
    assert not iso.needs_english("Almaty", subs["KZ-19"])
    assert iso.needs_english("Moskov", subs["RU-MOS"]) and iso.needs_english("Kur", subs["RU-KRS"])


def test_the_iso_file_reads():
    subs = iso.read_iso()
    # translations and the boundary file's shape names are separate columns
    mex = [x for x in subs["MX"] if x.code == "MX-MEX"][0]
    assert "Mexico" in mex.others and "Distrito Federal" in mex.shapes and "Distrito Federal" not in mex.others
    assert len(subs["IT"]) > 100 and any(s.name == "Sicilia" for s in subs["IT"])
    c = iso.read_countries()
    assert c[iso.key("Russia")] == "RU" and c[iso.key("United States")] == "US" and c[iso.key("Türkiye")] == "TR"


if __name__ == "__main__":
    n = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            n += 1
    print(n, "tests passed")
