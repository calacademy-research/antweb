"""tools/place/iso_states.py: AntWeb's states under their ISO 3166-2 names (2026-11-20)."""
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


def test_the_iso_file_reads():
    subs = iso.read_iso()
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
