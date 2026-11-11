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

Review of PR #341 (2026-11-11): the four jobs end with the same two steps as the button (UtilDataAction.finishOnePlace:
the place's image numbers by the rule, then its list totals; util.OnePlaceJobDbTest runs them against a database) and
add to their answer why the list totals were not counted when they were not; the count of one place stores a mark, not
a fingerprint, so the next daily update counts the place once more; the schema check creates only a missing table.
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
    # the state it stores is a mark that equals no fingerprint: the next daily update counts the place once more
    assert "countAndStore(place, tk, ONE_PLACE);" in one and "fingerprint(" not in one
    assert 'public static final String ONE_PLACE = "one place, counted between two daily updates";' in src
    changed = body(src, "static List<String> changedParts(String stored, String now)")
    assert "if (ONE_PLACE.equals(stored)) {" in changed and "out.add(ONE_PLACE_REASON);" in changed
    # why a count failed, for the admin job's answer
    assert "lastFailure = e.getMessage() == null ? e.toString() : e.getMessage();" in one
    # the schema check creates only a table that is missing (CREATE TABLE commits the job's transaction)
    schema = body(src, "public void ensureSchema()")
    assert 'boolean totals = LicenseUpdateDb.tableExists(connection, "place_view_total");' in schema
    assert 'boolean state = LicenseUpdateDb.tableExists(connection, "place_view_total_state");' in schema
    assert "if (!totals) stmt.executeUpdate(createTable(charset, collation));" in schema
    assert "if (!state) stmt.executeUpdate(createStateTable(charset, collation));" in schema
    assert schema.count("executeUpdate(") == 2
    # and it runs at start-up, with the other tables' checks
    assert "new org.calacademy.antweb.home.PlaceViewTotalDb(conn).ensureSchema();" in read("src/org/calacademy/antweb/util/LoginMgr.java")
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


def test_admin_jobs_for_one_place_count_its_images_and_list_totals():
    src = read("src/org/calacademy/antweb/util/UtilDataAction.java")
    finish = body(src, "public static String finishOnePlace(Connection connection, String imageType, String imageKey, Overview place)")
    # the image numbers first (by the rule: no red-flagged specimens' images), then the list totals; the note is returned
    assert re.search(r"new PlaceImageCountDb\(connection\)\.updatePlace\(imageType, imageKey\);\s*return recountListTotals\(connection, place\);", finish)
    helper = body(src, "public static String recountListTotals(Connection connection, Overview place)")
    assert "db.recountPlace(place)" in helper and "db.getLastFailure()" in helper and "Html.esc(reason)" in helper
    assert 'public static final String LIST_TOTALS_NOT_RECOUNTED = "; list totals not recounted: ";' in src
    for rebuilt, call, answer in (
            ("new GeolocaleDb(connection).updateCounts(geolocaleId);",
             'onePlaceNote = finishOnePlace(connection, PlaceImageCountDb.GEOLOCALE, "" + geolocaleId, GeolocaleMgr.getGeolocale(geolocaleId));',
             'message = "Updated Geolocale Counts. " + onePlaceNote;'),
            ("new BioregionDb(connection).populate(bioregion);",
             "onePlaceNote = finishOnePlace(connection, PlaceImageCountDb.BIOREGION, bioregion, BioregionMgr.getBioregion(bioregion));",
             'message = "Bioregion populated" + appendStr + onePlaceNote;'),
            ("museumDb.populate(museumCode);",
             "onePlaceNote = finishOnePlace(connection, PlaceImageCountDb.MUSEUM, museumCode, MuseumMgr.getMuseum(museumCode));",
             'message = "Museum populated" + appendStr + onePlaceNote;'),
            ("projectDb.updateCounts(projectName);",
             "onePlaceNote = finishOnePlace(connection, PlaceImageCountDb.PROJECT, projectName, project);",
             'message = "Project" + appendStr + " counts updated" + onePlaceNote;')):
        parts = src.split(rebuilt)
        assert len(parts) == 2, rebuilt
        # in the branch for one place: before the "else" that does every place, or the end of the branch
        branch = parts[1][:parts[1].index("}")]
        assert call in branch, (rebuilt, call)
        # the job's answer carries the note
        assert answer in parts[1], answer
    assert src.count("onePlaceNote = finishOnePlace(") == 4
    # no job counts the list totals without the place's images
    assert len(re.findall(r"(?<!return )(?<!String )recountListTotals\(connection, ", src)) == 0


if __name__ == "__main__":
    test_one_place_is_counted_by_the_daily_fills_own_step()
    test_update_statistics_counts_the_list_totals_in_every_branch()
    test_admin_jobs_for_one_place_count_its_images_and_list_totals()
    print("ok")
