"""tools/place/place_fix.py: the country and bioregion stored with a specimen, brought up to today's upload rule
(2026-11-10, Brian Fisher: the place data must be right and reproducible before an extract goes to collaborators).

The rule is tested without a database, on the cases production showed on 2026-11-10: a state whose second
bioregion is the text "none" (Chihuahua: 1,340 specimens blank), a state filed under an old name (Nuevo Leon),
a state with two bioregions (Jalisco: stays), a country spelled with an accent (México), a country with two
bioregions and no state, an island country, no country. The SQL of --apply is tested for what it must contain:
the backup before the update, exact (hex) matching, and a rollback that restores both fields.

Run: python3 tests/unit/test_place_fix.py   (also in .github/workflows/tests.yaml)
"""
import importlib.util
import os
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
spec = importlib.util.spec_from_file_location("place_fix", os.path.join(ROOT, "tools", "place", "place_fix.py"))
pf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pf)
KEEP = pf.KEEP


def places():
    P = pf.Place
    return pf.Places([
        P("Canada", "country", "Northern America", 1, "", "Nearctic", "", 0),
        P("Mexico", "country", "Central America", 1, "", "Neotropical", "Nearctic", 0),
        P("United States", "country", "Northern America", 1, "", "Nearctic", "Oceania", 0),
        P("USA", "country", "Northern America", 0, "United States", "", "", 0),
        P("Panama", "country", "Central America", 1, "", "Neotropical", "", 0),
        P("Hawaii", "country", "Polynesia", 1, "", "Oceania", "", 1),
        P("Zaire", "country", "Middle Africa", 0, "", "Afrotropical", "", 0),
        P("Chihuahua", "adm1", "Mexico", 1, "", "Nearctic", "none", 0),
        P("Jalisco", "adm1", "Mexico", 1, "", "Neotropical", "Nearctic", 0),
        P("Nuevo León", "adm1", "Mexico", 1, "", "Nearctic", "none", 0),
        P("Nuevo Leon", "adm1", "Mexico", 0, "Nuevo León", "Neotropical", "", 0),
        P("Baja California", "adm1", "Mexico", 1, "none", "Nearctic", "none", 0),
        P("Baja California Norte", "adm1", "Mexico", 0, "Baja California", "Neotropical", "", 0),
        P("Old Lost State", "adm1", "Mexico", 0, "", "Neotropical", "", 0),
        P("California", "adm1", "United States", 1, "", "Nearctic", "", 0),
        P("Ontario", "adm1", "Canada", 1, "", "", "", 0),
        P("Old Canadian Name", "adm1", "Canada", 0, "", "Oceania", "", 0),
    ])


def d(country, adm1, bioregion, island=False):
    return pf.decide(places(), country, adm1, bioregion, island)


def test_none_text_is_no_second_bioregion():
    r = d("Mexico", "Chihuahua", "")
    assert r.new_country is KEEP and r.new_bioregion == "Nearctic" and r.problem is None, r
    assert r.reasons == ["bioregion: the state's (was blank)"], r.reasons


def test_old_state_name_takes_the_valid_states_bioregion_not_its_own():
    assert d("Mexico", "Nuevo Leon", "").new_bioregion == "Nearctic"
    assert d("Mexico", "Baja California Norte", "Neotropical").new_bioregion == "Nearctic"
    # an old name that points to no valid state defines nothing (Irian Jaya said Indomalaya, the map Australasia)
    r = d("Mexico", "Old Lost State", "")
    assert r.new_bioregion is KEEP, r
    assert r.problem == "no bioregion: an old state name with no valid name in AntWeb, and the country has two bioregions", r
    assert d("Mexico", "Old Lost State", "Nearctic") == pf.Decision(KEEP, KEEP, [], None)
    # in a country with one bioregion the country decides, not the old row's own value
    assert d("Canada", "Old Canadian Name", "").new_bioregion == "Nearctic"


def test_state_with_two_bioregions_keeps_what_is_stored():
    r = d("Mexico", "Jalisco", "")
    assert r.new_bioregion is KEEP and r.problem == "no bioregion: the state has two bioregions", r
    r = d("Mexico", "Jalisco", "Nearctic")
    assert r.new_bioregion is KEEP and r.problem is None, r


def test_country_with_two_bioregions_and_no_or_unknown_state():
    assert d("United States", "", "").problem == "no bioregion: no state, and the country has two bioregions"
    assert d("Mexico", "Cancun", "").problem == "no bioregion: state not known to AntWeb, and the country has two bioregions"
    r = d("United States", "", "Indomalaya")
    assert r.new_bioregion is KEEP and r.problem == "bioregion Indomalaya is not one of the country's", r
    assert d("United States", "", "Oceania").problem is None


def test_country_with_one_bioregion():
    assert d("Canada", "", "").new_bioregion == "Nearctic"
    assert d("Canada", "Ontario", "Nearctic") == pf.Decision(KEEP, KEEP, [], None)
    r = d("Canada", "Nowhere", "Neotropical")
    assert r.new_bioregion == "Nearctic" and r.reasons == ["bioregion: the country's (was Neotropical)"], r


def test_country_spelling_and_old_name():
    r = d("México", "Chihuahua", "")
    assert r.new_country == "Mexico" and r.new_bioregion == "Nearctic", r
    assert r.reasons == ["country: spelling of Mexico", "bioregion: the state's (was blank)"], r.reasons
    r = d("USA", "California", "Nearctic")
    assert r.new_country == "United States" and r.new_bioregion is KEEP and r.reasons == ["country: old name of United States"], r
    assert d("  panamá ", "", "Neotropical").new_country == "Panama"
    # nothing is guessed: a different word, an old name that points nowhere, a blank
    assert d("Equador", "", "") == pf.Decision(KEEP, KEEP, [], "country not known to AntWeb")
    assert d("Zaire", "", "") == pf.Decision(KEEP, KEEP, [], "country not known to AntWeb")
    assert d("", "Chihuahua", "") == pf.Decision(KEEP, KEEP, [], "no country")


def test_islands_are_left_alone():
    assert d("United States", "", "Oceania", island=True) == pf.Decision(KEEP, KEEP, [], None)
    assert d("United States", "Hawaii", "").problem.startswith("an island AntWeb treats as a country")


def test_the_state_is_never_changed():
    for adm1 in ("Nuevo Leon", "Baja California Norte", "Cancun"):
        r = d("Mexico", adm1, "")
        assert not hasattr(r, "new_adm1")
    assert "adm1 =" not in pf.update_sql("place_fix_20261110_0900").split(" set ", 1)[1]


def test_plan_and_report():
    C = pf.Combination
    combos = [C("Mexico", "Chihuahua", "", False, 1340), C("México", "", "", False, 3), C("Mexico", "Jalisco", "", False, 174),
              C("", "", "", False, 1909), C("Canada", "Ontario", "Nearctic", False, 7000), C("United States", "", "Oceania", True, 248)]
    changes, not_fixed = pf.plan(places(), combos)
    assert [(c.country, c.specimens) for c, _ in changes] == [("Mexico", 1340), ("México", 3)], changes
    assert sorted(c.specimens for c, _ in not_fixed) == [3, 174, 1909], not_fixed
    text = pf.report_text("2026-11-10 09:00", "abc1234", changes, not_fixed)
    assert "differs from what an upload today would store: 1,343" in text, text
    assert "1,340  bioregion: the state's" in text and "3  country: spelling of Mexico" in text, text
    assert "1,909  no country" in text, text
    applied = pf.report_text("2026-11-10 09:00", "abc1234", changes, not_fixed, after=0, table="place_fix_20261110_0900")
    assert "--rollback place_fix_20261110_0900" in applied and "still differ from the rule: 0 (as expected)" in applied, applied
    assert "NOT ZERO" in pf.report_text("x", "y", changes, not_fixed, after=5, table="place_fix_20261110_0900")


def test_only_some_countries():
    C = pf.Combination
    combos = [C("Mexico", "Chihuahua", "", False, 1340), C("México", "Chihuahua", "", False, 3), C("Canada", "", "", False, 2),
              C("", "", "", False, 9)]
    changes, not_fixed = pf.plan(places(), combos, only={"Mexico"})
    # México becomes Mexico, so it belongs to Mexico; Canada is left for another day; the curators' list is whole
    assert sorted(c.specimens for c, _ in changes) == [3, 1340], changes
    assert [c.specimens for c, _ in not_fixed] == [9], not_fixed
    text = pf.report_text("x", "y", changes, not_fixed, only={"Mexico"})
    assert "Limited to these countries (--countries): Mexico." in text, text
    assert len(pf.plan(places(), combos)[0]) == 3


def test_sql_of_apply():
    C = pf.Combination
    changes, _ = pf.plan(places(), [C("México", "Chihuahua", "", False, 3), C("Mexico", "O'Brien", "x", False, 1)])
    stmts = pf.map_sql("place_fix_20261110_0900", changes)
    assert stmts[0].startswith("create table place_fix_20261110_0900_map ")
    assert len(stmts) == 2, stmts
    # the stored values go in as hex: México is not Mexico, and no quote of a name reaches the SQL
    assert "'4DC3A97869636F', '436869687561687561', ''" in stmts[1], stmts[1]
    assert "1, 'Mexico', 1, 'Nearctic'" in stmts[1], stmts[1]
    backup = pf.backup_sql("place_fix_20261110_0900")
    assert "old_country" in backup and "old_bioregion" in backup and "hex(ifnull(s.country, ''))" in backup
    assert "where not (s.island_country is not null and s.island_country <> '')" in backup
    rollback = pf.rollback_sql("place_fix_20261110_0900")
    assert "s.country = f.old_country" in rollback and "s.bioregion = f.old_bioregion" in rollback
    assert pf.TABLE_NAME.match("place_fix_20261110_0900") and not pf.TABLE_NAME.match("specimen; drop table x")
    assert pf.sql_string("O'Br\\ien") == "'O''Br\\\\ien'"


def test_names():
    assert pf.normal("  MÉXICO ") == "mexico" and pf.normal("Côte  d'Ivoire") == "cote d'ivoire"
    assert pf.clean_bioregion("none") is None and pf.clean_bioregion(" None ") is None and pf.clean_bioregion("null") is None
    assert pf.clean_bioregion("") is None and pf.clean_bioregion("Nearctic") == "Nearctic"
    assert pf.hexed("México") == "4DC3A97869636F" and pf.unhex("4DC3A97869636F") == "México" and pf.unhex("") == ""


if __name__ == "__main__":
    tests = [(n, f) for n, f in sorted(globals().items()) if n.startswith("test_") and callable(f)]
    for name, fn in tests:
        fn()
    print(str(len(tests)) + " tests passed")
