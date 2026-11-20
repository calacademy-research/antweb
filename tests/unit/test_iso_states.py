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
            "iso": "Grad Zagreb", "kept_id": 5, "kept_name": "Zagreb, Grad"}]
    sql, left = iso.statements(dec, {("Croatia", "Grad Zagreb"): [(9, 0, "Zagreb, Grad")]}, "20261120")
    assert not left and sql[0].startswith("update geolocale set name = convert(unhex('") and "where id = 9" in sql[0]
    assert any("is_valid = 0" in x and "where id = 9" in x for x in sql) and not any(x.startswith("insert") for x in sql)
    sql, left = iso.statements(dec, {}, "20261120")
    assert any(x.startswith("insert into geolocale") for x in sql)
    sql, left = iso.statements(dec, {("Croatia", "Grad Zagreb"): [(9, 1, "")]}, "20261120")
    assert left and not sql


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
