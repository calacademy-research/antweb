package test.org.calacademy.antweb.util;

import junit.framework.TestCase;

import org.calacademy.antweb.util.BusyStatus;

/**
 * BusyStatus (util.do?action=isBusy), 2026-10-27: a deploy on 2026-10-26 restarted AntWeb in the
 * middle of a curator's image upload. The deploy now asks isBusy and waits while it answers "busy".
 * No database or server needed.
 */
public class BusyStatusTest extends TestCase {

    public void testIdleWhenNothingRuns() {
        assertEquals("idle", BusyStatus.describe(null, 0, false, false, false));
    }

    public void testUploadFlagGivesTheKindNotTheName() {
        assertEquals("busy: specimen upload", BusyStatus.describe("someCurator", 0, false, false, false));
        assertEquals("busy: image upload", BusyStatus.describe("Image upload:someCurator", 0, false, false, false));
        assertEquals("busy: TaxonWorks nightly upload", BusyStatus.describe("TaxonWorks nightly:someCurator", 0, false, false, false));
        assertEquals("busy: nightly name update", BusyStatus.describe("nightly name update", 0, false, false, false));
        assertFalse(BusyStatus.describe("Image upload:someCurator", 1, false, false, false).contains("someCurator"));
    }

    public void testImageBatchBetweenFilesIsBusy() {
        assertEquals("busy: image upload", BusyStatus.describe(null, 1, false, false, false));
        // A file being processed and the batch open: said once.
        assertEquals("busy: image upload", BusyStatus.describe("Image upload:x", 2, false, false, false));
    }

    public void testJobsAndNightly() {
        assertEquals("busy: background job", BusyStatus.describe(null, 0, true, false, false));
        assertEquals("busy: data job", BusyStatus.describe(null, 0, false, true, true));
        assertEquals("busy: nightly run", BusyStatus.describe(null, 0, false, false, true));
        assertEquals("busy: specimen upload, background job, data job",
            BusyStatus.describe("x", 0, true, true, false));
    }

    public void testImageBatchTracking() {
        long idle = BusyStatus.IMAGE_BATCH_IDLE_MS;
        BusyStatus.imageBatchActive(6667);
        long now = System.currentTimeMillis();
        assertEquals(1, BusyStatus.openImageBatches(now));
        assertTrue(BusyStatus.isImageUploadInProgress());
        // Still open a minute before the idle limit, forgotten after it (the curator closed the page).
        assertEquals(1, BusyStatus.openImageBatches(now + idle - 60_000));
        assertEquals(0, BusyStatus.openImageBatches(now + idle + 1));
        assertFalse(BusyStatus.isImageUploadInProgress());
        // Finishing the upload ends it at once.
        BusyStatus.imageBatchActive(6668);
        assertEquals(1, BusyStatus.openImageBatches(System.currentTimeMillis()));
        BusyStatus.imageBatchFinished(6668);
        assertEquals(0, BusyStatus.openImageBatches(System.currentTimeMillis()));
        // A dry run has no batch (id 0): not tracked.
        BusyStatus.imageBatchActive(0);
        assertEquals(0, BusyStatus.openImageBatches(System.currentTimeMillis()));
    }
}
