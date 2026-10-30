package test.org.calacademy.antweb.util;

import java.nio.charset.MalformedInputException;

import junit.framework.TestCase;

import org.calacademy.antweb.Curator;
import org.calacademy.antweb.upload.SpecimenNameUpdate;
import org.calacademy.antweb.upload.SpecimenUpload;
import org.calacademy.antweb.upload.UploadAction;
import org.calacademy.antweb.upload.UploadDetails;

/**
 * Audit fixes of 2026-10-30 (triage tk), no database:
 * - "upload as" an id that is not a curator is refused, never the requester's own group;
 * - a read error part way through a specimen file rolls the upload back (the group's specimens were removed first);
 * - a specimen the nightly name update moves gets the status of the taxon it moves to (was always "valid");
 * - the museum counts of an upload match the museum code as a whole word (CASC was counted for CAS too).
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
        assertTrue(UploadDetails.namesMuseum("MCZ:ENT", "MCZ"));
        assertFalse(UploadDetails.namesMuseum("MCZC", "MCZ"));
        assertFalse(UploadDetails.namesMuseum(null, "CAS"));
        assertFalse(UploadDetails.namesMuseum("CAS", ""));
    }
}
