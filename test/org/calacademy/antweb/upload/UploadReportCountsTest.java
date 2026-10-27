package test.org.calacademy.antweb.upload;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    /** PR #291 staging run 2: a specimen upload's report said nothing about species (the e2e test read "Inserted 0 +
     *  Updated 0" for a file with one new name): the two lines were printed only with project statistics, which only
     *  a species list upload sets. */
    public void testSpecimenUploadReportShowsTheSpeciesCounts() {
        UploadDetails d = new UploadDetails("specimenUpload");
        assertNull(d.getPostUploadStatistics());
        assertEquals("", d.speciesCountsHtml());
        d.countInsertedSpecies("ponerinaeleptogenys zztestunknown");
        d.countUpdatedSpecies("ponerinaeleptogenys pavesii");
        d.countUpdatedSpecies("ponerinaeleptogenys pavesii");
        assertEquals("<br>&nbsp;&nbsp;&nbsp;<b>Inserted Species:</b> 1.<br>&nbsp;&nbsp;&nbsp;<b>Updated Species:</b> 1.",
            d.speciesCountsHtml());
    }

    /** genUploadReport prints the species counts before, not inside, the block that needs project statistics. */
    public void testReportPrintsTheCountsWithoutProjectStatistics() throws Exception {
        Path test = Paths.get(System.getProperty("antweb.test", "test"));
        Path src = test.toAbsolutePath().getParent().resolve("src/org/calacademy/antweb/upload/UploadDetails.java");
        if (!Files.isRegularFile(src)) return;   // src/ not next to test/ (the method test above still runs)
        String s = Files.readString(src, StandardCharsets.UTF_8);
        String report = s.substring(s.indexOf("public void genUploadReport(UploadFile"));
        int counts = report.indexOf("logString += speciesCountsHtml();");
        int stats = report.indexOf("if (postUploadStatistics != null) {");
        assertTrue("genUploadReport does not print speciesCountsHtml()", counts > 0);
        assertTrue("the species counts are printed inside the project statistics block", stats < 0 || counts < stats);
        assertFalse(report.substring(Math.max(stats, 0)).contains("getInsertedSpeciesCount() > 0) logString"));
    }
}
