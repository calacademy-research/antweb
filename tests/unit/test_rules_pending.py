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
    for name in ("RULES_LOG", "COUNTING_RULES_LOG", "ENDEMIC_RULE_LOG", "GENERA_RULE_LOG"):
        assert java_constant(java, name) == py_constant(py, name), name


def test_nightly_writes_the_lines():
    src = read("src/org/calacademy/antweb/util/UtilDataAction.java")
    assert '"  Set 2 done (" + CountInvariants.ENDEMIC_RULE_LOG + "; "' in src
    assert "+ org.calacademy.antweb.home.GeolocaleTaxonDb.GENUS_ROWS_LOG + \")\"" in src
    assert '"  Set 3 done (" + CountInvariants.ENDEMIC_RULE_LOG + ")"' in src
    assert '"  Set 4 done (" + CountInvariants.RULES_LOG' in src
    # since 2026-10-30 the genera rule is the last part of the line (after COUNTING_RULES_LOG)
    assert '+ "; " + CountInvariants.COUNTING_RULES_LOG + "; " + CountInvariants.GENERA_RULE_LOG + ")"' in src
    # set 2's line after the geolocale counts, set 3's after the bioregions
    assert src.index('"  Set 2 done (') > src.index('"geolocaleCountCrawl"')
    assert src.index('"  Set 3 done (') > src.index('"populateBioregion"')


def test_bioregion_lists_take_valid_names_only():
    """Brian Fisher, 2026-10-29: Formica was an Afrotropical genus through unidentifiable AntCat names (Formica chufejif
    Forskal, 1775) listed under their own name. Country lists already took valid names only; bioregion lists now too,
    and set 1 says so in compute.log for the e2e test (tests/e2e/test_images_anywhere.py)."""
    up = read("src/org/calacademy/antweb/upload/SpeciesListUpload.java")
    assert java_constant(up, "BIOREGION_VALID_LOG") == py_constant(read("tests/e2e/numbers_sample.py"), "BIOREGION_VALID_LOG")
    assert "if (bioregion != null && isValidForPlaceList(currentValidName)) {" in up
    assert "return current != null && current.isValid();" in up
    nightly = read("src/org/calacademy/antweb/util/UtilDataAction.java")
    assert '"  AntCat import done (" + org.calacademy.antweb.upload.SpeciesListUpload.BIOREGION_VALID_LOG + ")"' in nightly


def test_genus_rows_from_specimens_identified_to_genus():
    """Brian Fisher, 2026-10-29: Lividopone, known in Mozambique only from "Lividopone (indet)", was not on the Mozambique
    list. Set 2 adds the genus rows of every specimen identified to genus (not the names) to the country and adm1 lists
    and says so on its compute.log line, which tests/e2e/test_genus_indet_places.py waits for."""
    g = read("src/org/calacademy/antweb/home/GeolocaleTaxonDb.java")
    assert java_constant(g, "GENUS_ROWS_LOG") == py_constant(read("tests/e2e/numbers_sample.py"), "GENUS_ROWS_LOG")
    assert '+ " union " + GENUS_ROWS_FROM_SPECIMENS_SQL' in g
    assert "join taxon g on g.taxon_name = t.parent_taxon_name" in g and "g.taxarank = 'genus'" in g


def test_genera_rule_is_pending_until_the_nightly_run():
    """Brian Fisher, 2026-10-30: Genera are valid genera and morphogenera. The count check (rulesCounted) and the e2e tests
    (genera_rule_counted) call the stored Genera pending until the "Set 4 done" line has GENERA_RULE_LOG; the e2e tests
    that read compute.log log in, so they are not in [parallel]."""
    java = read("src/org/calacademy/antweb/util/CountInvariants.java")
    assert "line.contains(GENERA_RULE_LOG)" in java
    sample = read("tests/e2e/numbers_sample.py")
    assert 'return compute_log_has("Set 4 done", GENERA_RULE_LOG)' in sample
    lists = read("tests/e2e/test_numbers_lists.py")
    assert lists.count("ns.genera_rule_counted() is False") == 2
    assert "ns.genera_rule_counted()" in read("tests/e2e/test_numbers_live.py")
    assert 'name == "Genera" and ns.genera_rule_counted() is False' in read("tests/e2e/test_count_check.py")
    src = read("src/org/calacademy/antweb/util/NumberChecks.java")
    assert src.count("i == 6 && v[i] != recount[i] && !inv.result.rulesCounted) pi = CountInvariants.GENERA_RULE_PENDING") == 1
    assert src.count("i == 6 && stored[i] != recount[i] && !inv.result.rulesCounted) pi = CountInvariants.GENERA_RULE_PENDING") == 1
    areas = read("tests/e2e/areas.toml")
    parallel = areas[areas.index("[parallel]"):]
    parallel = parallel[:parallel.index("\n[", 1)]
    for entry in ('"test_numbers_lists.py"', '"test_numbers_live.py"', '"test_numbers_live.py::test_list_headers_equal_place_numbers"',
                  '"test_numbers_lists.py::test_list_headers_equal_place_numbers_of_every_kind"',
                  '"test_numbers_lists.py::test_browse_headers_equal_the_list_rows"'):
        assert entry not in parallel, entry


def test_group_species_is_pending_until_the_nightly_run():
    src = read("src/org/calacademy/antweb/util/NumberChecks.java")
    assert src.count("i == 7 && v[i] != recount[i] && !inv.result.rulesCounted ? CountInvariants.SPECIES_RULE_PENDING") == 1
    assert src.count("i == 7 && stored[i] != recount[i] && !inv.result.rulesCounted ? CountInvariants.SPECIES_RULE_PENDING") == 1


def test_e2e_tests_use_the_helpers():
    lists = read("tests/e2e/test_numbers_lists.py")
    assert lists.count("ns.counting_rules_counted() is False") == 2   # the default-list test compares with the default choice (2026-10-30)
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
