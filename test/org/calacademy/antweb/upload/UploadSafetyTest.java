package test.org.calacademy.antweb.upload;

import junit.framework.TestCase;

import org.calacademy.antweb.upload.MessageMgr;
import org.calacademy.antweb.upload.SpecimenUpload;
import org.calacademy.antweb.upload.UploadAction;

/**
 * Upload safety (2026-10-27):
 * - the upload flag is claimed and released by its holder only (the nightly TaxonWorks job and the nightly name
 *   update checked it, then set it, and cleared it whoever held it);
 * - a report heading looked up for a specimen page does not empty the error list of an upload running then;
 * - the message of an upload that stopped says nothing was changed, with the reason escaped.
 */
public class UploadSafetyTest extends TestCase {

    public void testUploadFlagIsReleasedOnlyByItsHolder() {
        UploadAction.releaseUploadProcess(UploadAction.getIsInUploadProcess());
        assertTrue(UploadAction.claimUploadProcess("first"));
        try {
            assertFalse(UploadAction.claimUploadProcess("second"));
            UploadAction.releaseUploadProcess("second");
            assertEquals("first", UploadAction.getIsInUploadProcess());
        } finally {
            UploadAction.releaseUploadProcess("first");
        }
        assertNull(UploadAction.getIsInUploadProcess());
    }

    public void testHeadingLookupKeepsRunningUploadErrors() {
        new MessageMgr();   // an upload starts: its error list is empty
        MessageMgr.addToErrors("taxon insert failed");
        String heading = MessageMgr.getMessageDisplay(MessageMgr.noRecordsProcessed);
        assertTrue(heading, heading.contains("Rollback"));
        assertEquals(1, MessageMgr.getErrorCount());
    }

    public void testFutureDateHeadings() {
        assertTrue(MessageMgr.getMessageDisplay(MessageMgr.futureDateCollected).contains("Date Collected"));
        assertTrue(MessageMgr.getMessageDisplay(MessageMgr.futureDateDetermined).contains("Date Determined"));
    }

    public void testRolledBackMessage() {
        String m = SpecimenUpload.rolledBackMessage("Invalid Column <b>x</b>");
        assertTrue(m, m.contains("nothing was changed"));
        assertTrue(m, m.contains("&lt;b&gt;"));
        assertFalse(m, m.contains("<b>"));
        assertTrue(SpecimenUpload.rolledBackMessage(null).contains("nothing was changed"));
    }
}
