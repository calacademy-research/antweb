"""The count check after the daily update tells someone when the counts are not true (Brian Fisher, 2026-11-11: "how
did we not get an alert that the counts were not true").

What was missing (the Fossil Ants page said Genera 259 beside a list of 258 for weeks):
  1. no check of the rule "every number on a place page is the header of the list its link opens": the count check now
     has place.view.totals (NumberChecks.placeListTotals): every place page with a list has its list totals, counted in
     the last 8 days; the places are PlaceViewTotalDb.places(), the daily update's own list;
  2. the one end-to-end test of that rule skipped a place whose totals were never counted, and its sample left the two
     site-wide lists out: test_numbers_lists.py now checks Fossil Ants and the AntCat list on every run, uncounted = failure;
  3. real differences found after the daily update went into the job's result and nowhere else: CountCheckJob.warning
     now ends the Daily update page's Count check step with a warning (yellow, the number) and one email, by
     DailyUpdate's own mail path. Known findings (CountInvariants.KNOWN_FINDINGS) and pending differences send nothing.

This file reads the sources: the wiring a refactoring could silently drop. The behaviour is in the JUnit tests
(NumberChecksTest.testAPlacePageWithoutFreshListTotalsIsARealDifference, testRealDifferencesOutsideTheKnownFindings,
DailyUpdateTest.testCountCheckWithRealDifferencesIsAWarningAndOneEmail).

Run: python3 tests/unit/test_count_check_alert.py
"""
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def code(text):
    """Java source without its comments."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def test_the_check_is_run_and_listed():
    checks = code(read("src/org/calacademy/antweb/util/NumberChecks.java"))
    run = checks.split("void run() {", 1)[1].split("}", 1)[0]
    assert "placeListTotals();" in run, "NumberChecks.run no longer runs the list totals check"
    body = checks.split("void placeListTotals() {", 1)[1].split("\n    }\n", 1)[0]
    # the daily update's own list of places, so the two cannot drift
    assert "s_placeListForTests != null ? s_placeListForTests : db.places()" in body
    assert "db.storedRanks(org.calacademy.antweb.home.PlaceViewTotalDb.FRESH_DAYS)" in body
    # a real difference, never pending: the last argument of compare is the pending reason
    # (expected: three list totals; four on the AntCat list and Fossil Ants, whose Valid species is one too)
    assert "int expected = org.calacademy.antweb.home.PlaceViewTotalDb.expectedTotals(place);" in body
    assert 'inv.compare(c, tk[0], tk[1], what, ranks == null ? 0 : Math.min(ranks[1], expected), expected, "=", null);' in body, body
    ids = re.search(r"public static final String\[\] IDS = \{(.*?)\};", read("src/org/calacademy/antweb/util/CountInvariants.java"), re.S).group(1)
    assert '"place.view.totals"' in ids
    assert "public static final int FRESH_DAYS = 8;" in read("src/org/calacademy/antweb/home/PlaceViewTotalDb.java")
    print("ok   test_the_check_is_run_and_listed")


def test_the_site_wide_lists_are_among_the_places():
    db = code(read("src/org/calacademy/antweb/home/PlaceViewTotalDb.java"))
    places = db.split("public List<Overview> places()", 1)[1].split("\n    }\n", 1)[0]
    assert "countedProjects(ProjectMgr.getAntProjects(), ProjectMgr.getGlobalProjects())" in places
    print("ok   test_the_site_wide_lists_are_among_the_places")


def test_the_daily_update_is_told_and_tells():
    job = code(read("src/org/calacademy/antweb/util/CountCheckJob.java"))
    after = job.split("public static void startAfterNightly()", 1)[1]
    assert "run(j, source, SECONDS, counts)" in after
    assert re.search(r"DailyUpdate\.countCheckFinished\(runStart\.get\(\), outcome\.ok \? null : [^,]+,\s*outcome\.ok \? warning\(counts\[0\], counts\[1\]\) : null\);", after), \
        "the count check after the daily update no longer hands its real differences to the Daily update page"
    assert "counts[1] = result.realOutside(CountInvariants.KNOWN_FINDINGS);" in job
    daily = code(read("src/org/calacademy/antweb/util/DailyUpdate.java"))
    assert 'public static final String WARNING = "warning";' in daily
    assert 'if (WARNING.equals(result)) emailOnce(run, key, "differences", detail == null ? "" : detail);' in daily
    # the same mail path as a failed or slow step: one sendMail in the class, inside emailOnce
    assert daily.count("Emailer.sendMail(") == 1
    assert "Emailer.sendMail(" in daily.split("private static void emailOnce(", 1)[1].split("\n    }\n", 1)[0]
    assert '"/countCheck.do' in daily.split("private static void emailOnce(", 1)[1].split("\n    }\n", 1)[0], "the email does not link the report"
    page = code(read("src/org/calacademy/antweb/util/DailyUpdateAction.java"))
    assert 'v.slow || v.warning ? " style=\\"background:#fff3cd\\""' in page, "a warning step is not yellow on the page"
    assert 'v.warning ? " data-warning=\\"1\\"" : ""' in page
    # the usual time of a step counts its warning runs too
    assert "s.result in ('ok', 'warning')" in read("src/org/calacademy/antweb/util/DailyUpdateDb.java")
    print("ok   test_the_daily_update_is_told_and_tells")


def test_known_findings_are_the_same_list_in_the_check_and_in_the_test():
    java = re.search(r"KNOWN_FINDINGS = java\.util\.Collections\.unmodifiableList\(\s*java\.util\.Arrays\.asList\((.*?)\)\);",
                     read("src/org/calacademy/antweb/util/CountInvariants.java"), re.S).group(1)
    java_ids = re.findall(r'"([^"]+)"', java)
    e2e = re.search(r"^KNOWN = \{(.*?)\}", read("tests/e2e/test_count_check.py"), re.S | re.M).group(1)
    e2e_ids = re.findall(r'"([a-z.A-Z0-9]+)":', e2e)
    assert sorted(java_ids) == sorted(e2e_ids), (java_ids, e2e_ids)
    ids = re.search(r"public static final String\[\] IDS = \{(.*?)\};", read("src/org/calacademy/antweb/util/CountInvariants.java"), re.S).group(1)
    for check in java_ids:
        assert f'"{check}"' in ids, check + " is not a check id"
    print("ok   test_known_findings_are_the_same_list_in_the_check_and_in_the_test")


def test_the_end_to_end_test_always_checks_the_two_lists():
    test = read("tests/e2e/test_numbers_lists.py")
    assert 'OWN_VIEW_LISTS = ("fossilants", "worldants")' in test
    body = test.split("def test_list_headers_equal_place_numbers_of_every_kind():", 1)[1].split("\ndef ", 1)[0]
    assert "for project in OWN_VIEW_LISTS:\n        _own_view_list(project, wrong, slow, checked)" in body
    own = test.split("def _own_view_list(project, wrong, slow, checked):", 1)[1].split("\ndef ", 1)[0]
    # uncounted is a failure there, not pending
    assert "data-view-counted=\"true\"' not in page" in own and "pending" not in own.split('"""', 2)[2]
    print("ok   test_the_end_to_end_test_always_checks_the_two_lists")


if __name__ == "__main__":
    test_the_check_is_run_and_listed()
    test_the_site_wide_lists_are_among_the_places()
    test_the_daily_update_is_told_and_tells()
    test_known_findings_are_the_same_list_in_the_check_and_in_the_test()
    test_the_end_to_end_test_always_checks_the_two_lists()
    print("ok")
