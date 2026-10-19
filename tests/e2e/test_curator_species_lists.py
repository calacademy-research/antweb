"""Curator rows of region and project species lists survive the automated cleanups (2026-10-19).

A list row needs a taxon row to be shown. Curator names such as morphospecies have a taxon row only
while a specimen uses the name, and the nightly dataCleanup (set 2) and the orphan step of a
specimen upload deleted every list row whose taxon row was missing at that moment. A curator's
morphospecies whose specimens were missing for one night was gone from every curated list for good.
Now curator rows are parked in taxon_set_parked (CuratorTaxonSetDb) and put back when the name exists
again. The database behaviour is tested in test/org/calacademy/antweb/home/CuratorTaxonSetTest.java
(CI, MySQL 8.4).

Static checks (any host): each cleanup that deletes list rows without taxon parks the curator rows
first; the morphospecies cleanup of project lists spares curator rows; the Species List Tool clears
a dispute before it inserts (the insert skips disputed names); a save recounts the list. Live check
(read-only, anonymous): the new data job exists and needs an administrator.
"""
import re

from antweb import REPO_ROOT, get, new_session

HOME = REPO_ROOT / "src/org/calacademy/antweb/home"


def _code(path):
    text = path.read_text(encoding="utf-8", errors="replace")
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def _method(text, signature):
    start = text.index(signature)
    depth = 0
    for i in range(text.index("{", start), len(text)):
        depth += {"{": 1, "}": -1}.get(text[i], 0)
        if depth == 0:
            return text[start:i]
    raise AssertionError(signature)


def test_data_cleanup_parks_curator_rows_before_deleting():
    body = _method(_code(HOME / "TaxonSetDb.java"), "public static String dataCleanup(")
    park = body.index("parkCuratorRowsWithoutTaxon")
    assert park < body.index("deleteGeolocaleTaxaWithoutTaxon")
    assert park < body.index('deleteFrom("proj_taxon"')
    assert park < body.index("deleteUncuratedMorphosWithoutSpecimen")


def test_data_cleanup_job_restores_first():
    code = _code(REPO_ROOT / "src/org/calacademy/antweb/util/UtilDataAction.java")
    block = code[code.index('action.equals("dataCleanup")'):]
    assert block.index(".restore()") < block.index("deleteSpeciesWithoutSpecimenOrAntcatSource")
    assert '"restoreParkedTaxonSets"' in code


def test_upload_orphan_step_parks_curator_rows():
    body = _method(_code(HOME / "OrphansDb.java"), "public void deleteTaxon(")
    park = body.index("parkCuratorRows(taxonName)")
    assert park < body.index("delete from proj_taxon") and park < body.index("delete from geolocale_taxon")


def test_morpho_cleanups_spare_curator_rows():
    proj = _method(_code(HOME / "ProjTaxonDb.java"), "String deleteUncuratedMorphosWithoutSpecimen(")
    assert "source not in" in proj and "curatorSourceList()" in proj
    geo = _method(_code(HOME / "GeolocaleTaxonDb.java"), "String deleteUncuratedMorphosWithoutSpecimen(")
    assert "gt.source = '\" + Source.SPECIMEN" in geo


def test_species_list_tool_clears_dispute_before_insert_and_recounts():
    body = _method(_code(HOME / "SpeciesListDb.java"), "public String saveTaxonSet(")
    loop = body[body.index("for (String taxonName : chosen)"):]
    assert loop.index("removeDispute(") < loop.index('insert(overview, taxonName, "speciesListTool")')
    assert "recount(speciesListName" in body
    log = _code(HOME / "GeolocaleTaxonLogDb.java")
    for m in ("public void insertDispute(", "public void removeDispute("):
        assert "s_disputes = null" in _method(log, m), m


def test_restore_job_needs_admin():
    """The job exists (not 404) and an anonymous visitor is asked to log in; nothing runs."""
    outcome = get(new_session(), "/utilData.do?action=restoreParkedTaxonSets")
    assert outcome.category == "login", outcome
