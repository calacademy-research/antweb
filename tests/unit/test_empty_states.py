"""tools/place/empty_states.py: which empty states ISO 3166-2 does not have are retired, and which stay (no database).
Run: python3 tests/unit/test_empty_states.py"""
import collections
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools", "place"))
import empty_states as es  # noqa: E402
import iso_states as iso   # noqa: E402


def _plan(valid):
    out, rep, noc = iso.plan(valid, {}, iso.read_iso(), iso.read_countries())
    return es.candidates(valid, rep)


def test_candidates_are_empty_states_iso_does_not_have():
    # Latvia: Aglona (a former district, empty) is no ISO subdivision; Riga is (LV-RIX); a district with specimens stays
    valid = {"Latvia": [(1, "Aglonas Novads Old District", 0), (2, "Riga", 0), (3, "Ventspils District Old", 4)]}
    got = _plan(valid)
    assert [(i, n) for i, c, n in got] == [(1, "Aglonas Novads Old District")], got


def test_outdated_countries_are_never_touched():
    # Madagascar's regions are reported (ISO has the provinces) but never retired, empty or not
    valid = {"Madagascar": [(1, "Analamanga", 0), (2, "Atsimo-Andrefana", 0)]}
    assert _plan(valid) == []


def test_reasons_to_keep():
    cands = [(1, "Latvia", "A"), (2, "Latvia", "B"), (3, "Latvia", "C"), (4, "Latvia", "D"), (5, "Latvia", "E"), (6, "Latvia", "F")]
    spelled = collections.Counter({("Latvia", iso.key("a")): 2})                 # specimens written "a"
    olds = {("Latvia", "B"): ["B old"]}
    lists = {3: {"curator": 5, "specimen": 1}, 6: {"specimen": 3}}             # stale specimen rows do not keep a state
    disputes = collections.Counter({4: 1})
    texts = {iso.normal("E")}
    got = es.blockers(cands, spelled, olds, lists, disputes, texts)
    assert sorted(got) == [1, 2, 3, 4, 5], got
    assert "written otherwise" in got[1] and "old names" in got[2] and "curator 5" in got[3] and "disputes" in got[4] and "page text" in got[5]
    lines = es.plan_lines(cands, got)
    assert "retire\tLatvia\tF\t6" in lines and sum(l.startswith("keep") for l in lines) == 5


def test_the_sql_and_the_rollback():
    sql = es.retire_sql([7, 3], "20261121")
    assert sql == ["update geolocale set is_valid = 0, admin_notes = concat(ifnull(concat(admin_notes, '\\n'), ''), "
                   + iso.q("empty_states 20261121: retired, no ISO 3166-2 subdivision and no specimens")
                   + ") where georank = 'adm1' and is_valid = 1 and id in (3,7)"], sql
    assert "update geolocale g join empty_states_bak_<date> b on b.id = g.id set g.is_valid = b.is_valid" in es.__doc__


if __name__ == "__main__":
    n = 0
    for name, f in sorted(globals().items()):
        if name.startswith("test_") and callable(f):
            f()
            n += 1
    print("%d tests passed" % n)
