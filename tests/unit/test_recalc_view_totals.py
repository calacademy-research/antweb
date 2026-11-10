"""Rebuilding one place's list counts that place's stored list totals again (2026-11-10).

A place page shows Subfamilies, Genera and Species from stored totals (table place_view_total, PlaceViewTotalDb): the
headers of the place's lists in the default view. Only the daily update counted them. The "Update statistics" button of
a place page rebuilt the place's list and left the totals, so the page and the list disagreed until the next daily
update (staging, bioregion Nearctic: the genus list had 91 genera, the page said Genera 90; Indomalaya: species list
3,785, page 3,784).

Source checks (the behaviour is tested against a database by home.PlaceViewTotalDbTest and on staging by
tests/e2e/test_place_recalc.py):
  - PlaceViewTotalDb.recountPlace counts one place through the same per-place method as the daily fill (countAndStore)
    and never through fill(), which removes the rows of every place it is not given;
  - every branch of OverviewAction that recounts a place (project, museum, bioregion, country and island, adm1) calls it
    after the place's manager is populated again;
  - the admin jobs for one place (utilData.do: geolocaleCountCrawl with num, populateBioregion with name, populateMuseum
    with code, projectCountCrawl with name) call it too.
"""
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def body(src, signature):
    """The text of a method from its signature to the closing brace of its body."""
    start = src.index(signature)
    depth, i = 0, src.index("{", start)
    while True:
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return src[start:i + 1]
        i += 1


def test_one_place_is_counted_by_the_daily_fills_own_step():
    src = read("src/org/calacademy/antweb/home/PlaceViewTotalDb.java")
    step = body(src, "private void countAndStore(Overview place, String[] tk, String fingerprint)")
    assert "store(place, listRows(place, Rank.GENUS), listRows(place, Rank.SPECIES), listRows(place, Rank.SUBFAMILY));" in step
    assert "writeState(tk, fingerprint, true);" in step
    # counted and stored in one method only: the daily fill and the single place both go through it
    assert src.count("store(place, listRows(") == 1
    assert src.count("writeState(tk, fingerprint, true);") == 1
    fill = body(src, "public String fill(List<Overview> placeList, boolean all)")
    assert "countAndStore(place, tk, fingerprint);" in fill
    one = body(src, "public boolean recountPlace(Overview place)")
    assert "countAndStore(place, tk, fingerprint(place, loadCurated()));" in one
    # fill() removes the rows of every place not in its list: never used for one place
    assert "fill(" not in one and "fillAll(" not in one and "removeOthers(" not in one
    assert "if (tk == null || !counts(place)) return false;" in one
    # a count that fails is caught (the page is still shown) and the place's own rows are removed
    assert "catch (SQLException | RuntimeException e)" in one and "removePlace(tk);" in one
    remove = body(src, "private void removePlace(String[] tk)")
    assert 'where list_type = ? and list_key = ?' in remove


def test_update_statistics_counts_the_list_totals_in_every_branch():
    src = read("src/org/calacademy/antweb/OverviewAction.java")
    helper = body(src, "static void recountListTotals(Connection connection, Overview place)")
    assert "new PlaceViewTotalDb(connection).recountPlace(place);" in helper
    # after each manager is populated again, inside the same recount block (before its closing brace)
    branches = [
        ("ProjectMgr.populate(connection, true);", ["recountListTotals(connection, project);"]),
        ("MuseumMgr.populate(connection, true);", ["recountListTotals(connection, MuseumMgr.getMuseum(museumCode));"]),
        ("BioregionMgr.populate(connection, true);", ["recountListTotals(connection, BioregionMgr.getBioregion(name));"]),
        # country.do and island.do, then adm1.do
        ("GeolocaleMgr.populate(connection, true, false);",
         ["recountListTotals(connection, GeolocaleMgr.getGeolocale(geolocale.getId()));"] * 2),
    ]
    calls = 0
    for populate, expected in branches:
        parts = src.split(populate)
        assert len(parts) == len(expected) + 1, (populate, len(parts) - 1)
        for after, call in zip(parts[1:], expected):
            block = after[:after.index("}")]
            assert call in block, (populate, call)
            calls += 1
    assert calls == 5
    assert len(re.findall(r"(?<!static void )recountListTotals\(connection, ", src)) == 5
    # the statistics page still forgets its kept tables right after the museum and bioregion recounts
    for populate in ("MuseumMgr.populate(connection, true);", "BioregionMgr.populate(connection, true);"):
        assert src.split(populate, 1)[1].lstrip().startswith("StatsPageAction.forget();"), populate


def test_admin_jobs_for_one_place_count_its_list_totals():
    src = read("src/org/calacademy/antweb/util/UtilDataAction.java")
    helper = body(src, "static void recountListTotals(Connection connection, Overview place)")
    assert "new PlaceViewTotalDb(connection).recountPlace(place);" in helper
    for rebuilt, call in (
            ("new GeolocaleDb(connection).updateCounts(geolocaleId);", "recountListTotals(connection, GeolocaleMgr.getGeolocale(geolocaleId));"),
            ("new BioregionDb(connection).populate(bioregion);", "recountListTotals(connection, BioregionMgr.getBioregion(bioregion));"),
            ("museumDb.populate(museumCode);", "recountListTotals(connection, MuseumMgr.getMuseum(museumCode));"),
            ("projectDb.updateCounts(projectName);", "recountListTotals(connection, project);")):
        parts = src.split(rebuilt)
        assert len(parts) == 2, rebuilt
        # in the branch for one place: before the "else" that does every place, or the end of the branch
        branch = parts[1][:parts[1].index("}")]
        assert call in branch, (rebuilt, call)


if __name__ == "__main__":
    test_one_place_is_counted_by_the_daily_fills_own_step()
    test_update_statistics_counts_the_list_totals_in_every_branch()
    test_admin_jobs_for_one_place_count_its_list_totals()
    print("ok")
