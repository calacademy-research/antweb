"""Numbers whose rule changed on 2026-10-28 are pending until a nightly run has counted them by it (no server, no
database).

The Java nightly writes the lines (CountInvariants.COUNTING_RULES_LOG on "Set 4 done", ENDEMIC_RULE_LOG on "Set 2
done" and "Set 3 done"); the count check (CountInvariants.rulesCounted) and the e2e tests
(tests/e2e/numbers_sample.py: counting_rules_counted, endemic_rule_counted) read them from compute.log. The texts must
be the same in Java and Python, and the tests that compare stored numbers with live lists must use the helpers, so
they compare strictly again after the nightly run.

Run: python3 tests/unit/test_rules_pending.py   (also in .github/workflows/tests.yaml)
"""
import os
import re

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8", newline="") as f:
        return f.read()


def java_constant(src, name):
    m = re.search(r'static final String ' + name + r' = "([^"]+)";', src)
    assert m, name
    return m.group(1)


def py_constant(src, name):
    m = re.search(r'^' + name + r' = "([^"]+)"$', src, re.M)
    assert m, name
    return m.group(1)


def test_python_and_java_log_lines_are_the_same():
    java = read("src/org/calacademy/antweb/util/CountInvariants.java")
    py = read("tests/e2e/numbers_sample.py")
    for name in ("RULES_LOG", "COUNTING_RULES_LOG", "ENDEMIC_RULE_LOG"):
        assert java_constant(java, name) == py_constant(py, name), name


def test_nightly_writes_the_lines():
    src = read("src/org/calacademy/antweb/util/UtilDataAction.java")
    assert '"  Set 2 done (" + CountInvariants.ENDEMIC_RULE_LOG + ")"' in src
    assert '"  Set 3 done (" + CountInvariants.ENDEMIC_RULE_LOG + ")"' in src
    assert '"  Set 4 done (" + CountInvariants.RULES_LOG' in src and "CountInvariants.COUNTING_RULES_LOG + \")\"" in src
    # set 2's line after the geolocale counts, set 3's after the bioregions
    assert src.index('"  Set 2 done (') > src.index('"geolocaleCountCrawl"')
    assert src.index('"  Set 3 done (') > src.index('"populateBioregion"')


def test_group_species_is_pending_until_the_nightly_run():
    src = read("src/org/calacademy/antweb/util/NumberChecks.java")
    assert src.count("i == 7 && v[i] != recount[i] && !inv.result.rulesCounted ? CountInvariants.SPECIES_RULE_PENDING") == 1
    assert src.count("i == 7 && stored[i] != recount[i] && !inv.result.rulesCounted ? CountInvariants.SPECIES_RULE_PENDING") == 1


def test_e2e_tests_use_the_helpers():
    lists = read("tests/e2e/test_numbers_lists.py")
    assert lists.count("ns.counting_rules_counted() is False") == 2
    endemic = read("tests/e2e/test_numbers_endemic.py")
    assert "ns.endemic_rule_counted" in endemic and "ns.counting_rules_counted" in endemic
    groups = read("tests/e2e/test_count_check.py")
    assert 'name == "Species" and ns.counting_rules_counted() is False' in groups
    sample = read("tests/e2e/numbers_sample.py")
    # one reader of compute.log; an unreadable log (production) compares strictly
    assert sample.count("fileName=compute&ext=log") == 1
    assert 'return compute_log_has("Set 4 done", RULES_LOG, COUNTING_RULES_LOG)' in sample


if __name__ == "__main__":
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("ok", name)
