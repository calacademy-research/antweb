"""The names an e2e test expects a test-group specimen upload to write (tests/e2e/test_numbers_uploads.py: filed_name,
expected_species), by the upload's indet rules (Brian Fisher, 2026-10-28/29; SpecimenUploadParse.identifiedOnlyTo,
UploadUtil.indetColumns).

Staging run 40940710186: the round trip's file (test_specimen_upload_roundtrip.py) has a line with a genus only, a
line with species "Indet." and a line with a subfamily only; the upload files them as "<Genus> (indet)" (twice) and
"(<Subfamily>) (indet)" and its report said Inserted 2 + Updated 1 Species. The test skipped the lines without a
species and asked the API for "<genus> indet.", so it expected 2.

The functions are read from the e2e file without importing it (it needs requests and a server).

Run: python3 tests/unit/test_upload_expected_names.py   (also in .github/workflows/tests.yaml)
"""
import ast
import os
import re
from urllib.parse import quote

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
E2E = os.path.join(ROOT, "tests", "e2e", "test_numbers_uploads.py")
NAMES = ("_INDET_LIKE", "_indet_like", "filed_name", "expected_species", "distinct_names", "NOT_ANTCAT")


class FakeApi:
    """numbers_sample's api / api_rows for expected_species: name -> status (missing: no taxon)."""

    def __init__(self, statuses):
        self.statuses = statuses
        self.asked = []

    def api(self, path):
        self.asked.append(path)
        return path

    def api_rows(self, body):
        name = body.split("taxonName=", 1)[1]
        for n, status in self.statuses.items():
            if quote(n) == name:
                return [{"status": status}]
        return []


def load(statuses=None):
    with open(E2E, encoding="utf-8") as f:
        tree = ast.parse(f.read())
    keep = [node for node in tree.body
            if (isinstance(node, ast.FunctionDef) and node.name in NAMES)
            or (isinstance(node, ast.Assign) and any(getattr(t, "id", None) in NAMES for t in node.targets))]
    found = {n.name if isinstance(n, ast.FunctionDef) else n.targets[0].id for n in keep}
    assert found == set(NAMES), set(NAMES) - found
    env = {"re": re, "quote": quote, "ns": FakeApi(statuses or {})}
    exec(compile(ast.Module(body=keep, type_ignores=[]), E2E, "exec"), env)
    return env


def test_filed_name_follows_the_upload_rules():
    f = load()["filed_name"]
    assert f("myrmicinae", "pheidole", "") == ("myrmicinaepheidole (indet)", True), "genus only"
    for sp in ("indet", "Indet.", "undet", "(indet)", "sp.", "sp", "indet?"):
        assert f("myrmicinae", "pheidole", sp.lower()) == ("myrmicinaepheidole (indet)", True), sp
    assert f("myrmicinae", "", "") == ("(myrmicinae) (indet)", True), "subfamily only"
    assert f("myrmicinae", "(indet)", "") == ("(myrmicinae) (indet)", True), "genus placeholder"
    assert f("(formicidae)", "", "") == (None, False) and f("", "", "") == (None, False), "no subfamily: not filed"
    assert f("myrmicinae", "ocymyrmex", "indet", "karooicus") == ("myrmicinaeocymyrmex (indet)", True), "species indet"
    assert f("myrmicinae", "gnamptogenys", "striatula-complex", "indet") == ("myrmicinaegnamptogenys striatula-complex", False)
    assert f("myrmicinae", "pheidole", "a", "b") == ("myrmicinaepheidole a b", False)
    assert f("", "pheidole", "a") == (None, False), "no subfamily: the upload takes AntCat's, not modelled"


def round_trip_lines(with_subfamily=True, with_shared=False):
    """The file of test_specimen_upload_roundtrip.py (columns of BASELINE_HEADER + LocalityCode)."""
    head = "SpecimenCode\tSubfamily\t[Species]Genus\tSpeciesName\tCountry\tLocLatitude\tLocLongitude\tCollectedBy\tLocalityCode"
    def row(n, sf, g, sp, code=None):
        return "\t".join([code or f"zztest{n:04d}", sf, g, sp, "Madagascar", "-18.91", "47.52", "AntWeb e2e test", ""])
    lines = [head, row(1, "Myrmicinae", "Pheidole", "valida"), row(2, "Myrmicinae", "Pheidole", "synonyma"),
             row(3, "Myrmicinae", "Pheidole", "zztestunknown"), row(4, "Myrmicinae", "Pheidole", "valida", "zztest0004!"),
             row(5, "Myrmicinae", "Pheidole", "valida"), row(7, "Myrmicinae", "Pheidole", "valida"),
             row(8, "Myrmicinae", "Pheidole", ""), row(10, "Myrmicinae", "Pheidole", "Indet.")]
    if with_subfamily:
        lines.append(row(9, "Myrmicinae", "", ""))
    if with_shared:
        lines.append(row(6, "Ponerinae", "Pachycondyla", "sauteri"))
    return lines


STATUSES = {"myrmicinaepheidole valida": "valid", "myrmicinaepheidole synonyma": "synonym",
            "ponerinaepachycondyla sauteri": "obsolete combination"}


def test_round_trip_file_expects_the_indet_names():
    env = load(STATUSES)
    # zztestunknown (no taxon), Pheidole (indet) once (genus only and "Indet."), (Myrmicinae) (indet): the report's
    # Inserted 2 + Updated 1 of staging run 40940710186
    assert env["expected_species"](round_trip_lines()) == 3
    assert not any("indet" in a for a in env["ns"].asked), "an (indet) name is never asked of the API: " + str(env["ns"].asked)
    env = load(STATUSES)
    assert env["expected_species"](round_trip_lines(with_subfamily=False, with_shared=True)) == 2
    # the upper bound counts every line with a name, the genus-only and subfamily-only lines too
    assert env["distinct_names"](round_trip_lines()) >= 3


def test_baseline_expects_nothing():
    env = load(STATUSES)
    head = "SpecimenCode\tSubfamily\t[Species]Genus\tSpeciesName\tCountry\tLocLatitude\tLocLongitude\tCollectedBy"
    lines = [head] + ["\t".join([f"zztest000{n}", "Myrmicinae", "Pheidole", "valida", "Madagascar", "-18.91", "47.52", "x"]) for n in (1, 2)]
    assert env["expected_species"](lines) == 0


def test_a_name_without_subfamily_is_not_modelled():
    env = load(STATUSES)
    head = "SpecimenCode\tSubfamily\t[Species]Genus\tSpeciesName"
    assert env["expected_species"]([head, "zztest0001\t\tPheidole\t"]) is None
    assert env["expected_species"]([head, "zztest0001\t\tPheidole\tvalida"]) is None
    assert env["expected_species"]([head, "zztest0001\t\t\t"]) == 0


if __name__ == "__main__":
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("ok", name)
