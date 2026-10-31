package test.org.calacademy.antweb.util;

import java.nio.charset.MalformedInputException;

import junit.framework.TestCase;

import org.calacademy.antweb.Curator;
import org.calacademy.antweb.home.GroupCountDb;
import org.calacademy.antweb.upload.SpecimenNameUpdate;
import org.calacademy.antweb.upload.SpecimenUpload;
import org.calacademy.antweb.upload.SpecimenUploadParse;
import org.calacademy.antweb.upload.UploadAction;
import org.calacademy.antweb.upload.UploadDetails;
import org.calacademy.antweb.upload.UploadUtil;

/**
 * Audit fixes of 2026-10-30 (triage tk), no database:
 * - "upload as" an id that is not a curator is refused, never the requester's own group;
 * - a read error part way through a specimen file rolls the upload back (the group's specimens were removed first);
 * - a specimen the nightly name update moves gets the status of the taxon it moves to (was always "valid");
 * - the museum counts of an upload match the museum code as a whole word (CASC was counted for CAS too);
 * - a specimen without a collection code is stored without one (no random "tc" + number code) and Collections leaves
 *   it out (Brian Fisher, 2026-10-30).
 */
public class AuditTkFixesTest extends TestCase {

    public void testUploadAsUnknownCuratorIsRefused() {
        Curator own = new Curator();
        Curator other = new Curator();
        java.util.function.Function<String, Curator> byId = id -> "1234".equals(id) ? other : null;
        assertSame(own, UploadAction.uploadCurator(null, byId, own));
        assertSame(own, UploadAction.uploadCurator("  ", byId, own));
        assertSame(other, UploadAction.uploadCurator(" 1234 ", byId, own));
        assertNull(UploadAction.uploadCurator("1243", byId, own));
        assertNull(UploadAction.uploadCurator("12a", id -> { throw new NumberFormatException(id); }, own));
    }

    public void testReadErrorRollsTheUploadBack() {
        UploadDetails details = new UploadDetails("specimenUpload");
        SpecimenUpload.readFailed(details, new MalformedInputException(1));
        assertEquals("message", details.getForwardPage());
        assertTrue(details.getMessage(), details.getMessage().contains("nothing was changed"));
        assertTrue(details.getMessage(), details.getMessage().contains("MalformedInputException"));
    }

    public void testMovedSpecimenKeepsTheStatusOfItsTaxon() {
        assertEquals("synonym", SpecimenNameUpdate.specimenStatus("synonym"));
        assertEquals("valid", SpecimenNameUpdate.specimenStatus("valid"));
        assertEquals("valid", SpecimenNameUpdate.specimenStatus(null));
        assertEquals("valid", SpecimenNameUpdate.specimenStatus(" "));
    }

    public void testMuseumCodeIsAWholeWord() {
        assertTrue(UploadDetails.namesMuseum("CASC", "CASC"));
        assertFalse(UploadDetails.namesMuseum("CASC", "CAS"));
        assertTrue(UploadDetails.namesMuseum("CAS, San Francisco", "CAS"));
        assertTrue(UploadDetails.namesMuseum("ex CAS", "CAS"));
        assertFalse(UploadDetails.namesMuseum("CASENT", "CAS"));
        assertTrue("second occurrence counts", UploadDetails.namesMuseum("CASC/CAS", "CAS"));
        // species "sp." or "sp", any case, names no species; "sp 1" and "spinosa" do
        assertTrue(org.calacademy.antweb.upload.UploadUtil.isSpEpithet(" Sp. "));
        assertTrue(org.calacademy.antweb.upload.UploadUtil.isSpEpithet("sp"));
        assertFalse(org.calacademy.antweb.upload.UploadUtil.isSpEpithet("sp 1"));
        assertFalse(org.calacademy.antweb.upload.UploadUtil.isSpEpithet("spinosa"));
        assertTrue(UploadDetails.namesMuseum("MCZ:ENT", "MCZ"));
        assertFalse(UploadDetails.namesMuseum("MCZC", "MCZ"));
        assertFalse(UploadDetails.namesMuseum(null, "CAS"));
        assertFalse(UploadDetails.namesMuseum("CAS", ""));
    }

    public void testSpecimenWithoutACollectionCodeGetsNone() {
        assertNull(SpecimenUploadParse.storedCollectionCode(null));
        assertNull(SpecimenUploadParse.storedCollectionCode(""));
        assertNull(SpecimenUploadParse.storedCollectionCode("   "));
        assertNull(SpecimenUploadParse.storedCollectionCode("#%"));
        assertEquals("BLF1234", SpecimenUploadParse.storedCollectionCode("BLF 1234"));
        assertEquals("KM55-805", SpecimenUploadParse.storedCollectionCode("KM55\"\"805"));
        for (java.lang.reflect.Method m : UploadUtil.class.getMethods()) {
            assertFalse("the random code maker is gone", "createCode".equals(m.getName()));
        }
    }

    public void testCollectionsLeaveOutSpecimensWithoutACode() {
        assertEquals("nullif(trim(s.collectioncode), '')", GroupCountDb.COLLECTION);
        String columns = GroupCountDb.countColumns();
        assertTrue(columns, columns.contains(" then " + GroupCountDb.COLLECTION + " end)"));
        assertFalse(columns, columns.contains("then s.collectioncode end"));
        String collections = GroupCountDb.totalsSqls("1,2")[1];
        assertTrue(collections, collections.contains("select distinct " + GroupCountDb.COLLECTION + " from specimen s"));
        assertTrue(collections, collections.contains(GroupCountDb.COLLECTION + " is not null"));
    }
}
