package test.org.calacademy.antweb.upload;

import junit.framework.TestCase;

import org.calacademy.antweb.upload.UploadDetails;

/**
 * The species counts of the specimen upload report (docs/numbers.md row 95), without a database:
 * "Inserted Species" are the species and subspecies names the upload added to the taxon table, "Updated Species" the
 * names whose existing row it rewrote; each name once, inserted or updated, never both.
 *
 * B60 (2026-10-27): "Inserted Species" was never shown: AntwebUpload.saveTaxon counted a new name as updated.
 * B61 (2026-10-27): a name was counted again each time its lines came back after another name's lines (A, B, A: 3).
 * The e2e test tests/e2e/test_numbers_uploads.py checks the reports on staging against their uploaded files.
 */
public class UploadReportCountsTest extends TestCase {

    public void testANewNameIsInsertedNotUpdated() {
        UploadDetails d = new UploadDetails("specimenUpload");
        d.countInsertedSpecies("myrmicinaepheidole zztestnew");
        // a later line of the same name finds the row and rewrites it: still one inserted name
        d.countUpdatedSpecies("myrmicinaepheidole zztestnew");
        assertEquals(1, d.getInsertedSpeciesCount());
        assertEquals(0, d.getUpdatedSpeciesCount());
    }

    public void testEachUpdatedNameCountsOnce() {
        UploadDetails d = new UploadDetails("specimenUpload");
        for (String name : new String[] {"a b", "c d", "a b", "c d", "a b"}) d.countUpdatedSpecies(name);
        assertEquals(2, d.getUpdatedSpeciesCount());
        assertEquals(0, d.getInsertedSpeciesCount());
    }

    public void testAnInsertedNameIsNotAlsoUpdated() {
        UploadDetails d = new UploadDetails("specimenUpload");
        d.countUpdatedSpecies("a b");
        d.countInsertedSpecies("a b");   // cannot happen in an upload; if it did, the name is counted once
        d.countInsertedSpecies("c d");
        d.countInsertedSpecies("c d");
        assertEquals(2, d.getInsertedSpeciesCount());
        assertEquals(0, d.getUpdatedSpeciesCount());
    }

    public void testNoNameIsNotCounted() {
        UploadDetails d = new UploadDetails("specimenUpload");
        d.countInsertedSpecies(null);
        d.countUpdatedSpecies(null);
        assertEquals(0, d.getInsertedSpeciesCount() + d.getUpdatedSpeciesCount());
    }
}
