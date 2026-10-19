package test.org.calacademy.antweb.upload;

import junit.framework.TestCase;
import org.calacademy.antweb.Status;
import org.calacademy.antweb.Taxon;

/**
 * The parts of the specimen upload's name rule (SpecimenUploadProcess.setStatusAndCurrentValidName,
 * docs/flows/specimen-upload.md section 4) that need no database:
 *  1. indets and morphospecies are recognised from the name alone and kept as uploaded;
 *  2. which AntCat statuses send a specimen to the current valid name (synonyms, obsolete
 *     combinations, ...), and which keep the uploaded name (valid, unavailable, unidentifiable, ...).
 * The database half (taxon lookup, homonym placeholder) is covered by the staging round trip in
 * tests/e2e/test_specimen_upload_roundtrip.py; a unit test of the whole rule needs the decision
 * extracted into a method that takes a lookup (test-gaps-uploads-nightly.md, test 7).
 */
public class NameResolutionRuleTest extends TestCase {

    public void testStatusesThatUseTheCurrentValidName() {
        for (String s : new String[] {Status.SYNONYM, Status.ORIGINAL_COMBINATION, Status.OBSOLETE_COMBINATION,
                Status.OBSOLETE_CLASSIFICATION, Status.UNAVAILABLE_MISSPELLING, Status.UNAVAILABLE_UNCATEGORIZED}) {
            assertTrue(s, Status.usesCurrentValidName(s));
        }
    }

    public void testStatusesThatKeepTheUploadedName() {
        for (String s : new String[] {Status.VALID, Status.UNAVAILABLE, Status.UNIDENTIFIABLE, Status.HOMONYM,
                Status.COLLECTIVE_GROUP_NAME, Status.EXCLUDED_FROM_FORMICIDAE, Status.UNRECOGNIZED, null, ""}) {
            assertFalse(String.valueOf(s), Status.usesCurrentValidName(s));
        }
    }

    /** Known (test-gaps bug 14): "unavailable uncategorized" uses the valid name but is never imported from AntCat. */
    public void testUnavailableUncategorizedIsNotImported() {
        assertTrue(Status.excludeFromImport(Status.UNAVAILABLE_UNCATEGORIZED));
        assertFalse(Status.excludeFromImport(Status.SYNONYM));
        assertFalse(Status.excludeFromImport(Status.VALID));
    }

    public void testIndetsAreRecognisedByName() {
        assertTrue(Taxon.isIndet("formicinaecamponotus (indet)"));
        assertTrue(Taxon.isIndet("formicinaecamponotus undet"));
        assertFalse(Taxon.isIndet("formicinaecamponotus maculatus"));
        assertTrue(Taxon.isMorphoOrIndet("formicinaecamponotus (indet)"));
        assertFalse("an indet is not a morphospecies", Taxon.isMorpho("formicinaecamponotus (indet)"));
    }

    public void testMorphospeciesAreRecognisedByName() {
        assertTrue(Taxon.isMorpho("myrmicinaecrematogaster jtl-022"));
        assertTrue(Taxon.isMorpho("formicinaecamponotus mg001"));
        assertTrue(Taxon.isMorpho("formicinaecamponotus bf_01"));
        assertTrue(Taxon.isMorpho("formicinaecamponotus sp.1"));
        assertTrue("one-letter species", Taxon.isMorpho("myrmicinaetetramorium a"));
        assertFalse(Taxon.isMorpho("formicinaecamponotus maculatus"));
        assertFalse(Taxon.isMorpho("myrmicinaepheidole megacephala"));
        assertFalse("the staging test's unknown name is not a morpho", Taxon.isMorpho("formicinaecamponotus zztestunknown"));
        assertFalse(Taxon.isMorpho(null));
    }

    public void testQuadrinomials() {
        assertTrue(Taxon.isQuadrinomial("formicinaecamponotus maculatus a b"));
        assertFalse(Taxon.isQuadrinomial("formicinaecamponotus maculatus a"));
        assertFalse(Taxon.isQuadrinomial("formicinaecamponotus maculatus"));
    }
}
