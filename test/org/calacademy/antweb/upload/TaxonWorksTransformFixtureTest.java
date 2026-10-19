package test.org.calacademy.antweb.upload;

import junit.framework.TestCase;
import org.calacademy.antweb.upload.TaxonWorksTransformer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * TaxonWorks export (data.tsv of the Darwin Core archive) to AntWeb specimen file, with the
 * fixture fixtures/taxonworks-data.tsv (6 records). See docs/flows/specimen-upload.md 2.2.
 *
 * Fixture rows: 1 a date range, "eventID:" field number, CAS, elevation 100 to 250.5;
 * 2 one date, MCZC, one elevation, adm2 taken from TaxonWorks' county; 3 one column short
 * (left out, listed); 4 no catalogNumber (dropped); 5 quote marks in a value, elevation 80.4
 * to 80.9; 6 an OTU name only (morphospecies, rank genus).
 *
 * Run with -Dantweb.test=<the test/ folder> (default "test", the repository's).
 */
public class TaxonWorksTransformFixtureTest extends TestCase {

    private static Path fixture() {
        return Paths.get(System.getProperty("antweb.test", "test"), "org/calacademy/antweb/upload/fixtures/taxonworks-data.tsv");
    }

    private List<String> lines;
    private List<String> skipped;

    @Override
    protected void setUp() throws Exception {
        Path dir = Files.createTempDirectory("twfixture");
        Path in = dir.resolve("data.tsv");
        Files.copy(fixture(), in);
        Path out = dir.resolve("specimenTW99.txt");
        TaxonWorksTransformer t = new TaxonWorksTransformer();
        String err = t.transformFile(in, out);
        assertNull("transformFile error: " + err, err);
        lines = Files.readAllLines(out, StandardCharsets.UTF_8);
        skipped = t.getSkippedRecords();
    }

    private String col(int row, String header) {
        List<String> headers = Arrays.asList(lines.get(0).split("\t", -1));
        int i = headers.indexOf(header);
        assertTrue("no output column " + header + " in " + headers, i >= 0);
        return lines.get(row).split("\t", -1)[i];
    }

    public void testRecordsKeptDroppedAndSkipped() {
        // Header + rows 1, 2, 5, 6. Row 3 (column count) is listed, row 4 (no catalogNumber) dropped silently.
        assertEquals(5, lines.size());
        assertEquals("CASENT0000001", col(1, "SpecimenCode"));
        assertEquals("CASENT0000002", col(2, "SpecimenCode"));
        assertEquals("CASENT0000005", col(3, "SpecimenCode"));
        assertEquals("CASENT0000006", col(4, "SpecimenCode"));
        assertEquals(1, skipped.size());
        assertTrue(skipped.get(0), skipped.get(0).contains("catalogNumber CASENT0000003"));
        assertTrue(skipped.get(0), skipped.get(0).startsWith("line 3 "));
    }

    public void testColumnRenames() {
        List<String> headers = Arrays.asList(lines.get(0).split("\t", -1));
        for (String h : new String[] {"SpecimenCode", "DeterminedBy", "[Species]Genus", "SpeciesName", "Subfamily",
                "Country", "Adm1", "Adm2", "LocLatitude", "LocLongitude", "taxonworks_co_id", "CollectedBy"}) {
            assertTrue("missing " + h, headers.contains(h));
        }
        assertFalse(headers.contains("catalogNumber"));
        assertEquals("B.L. Fisher", col(1, "DeterminedBy"));
        assertEquals("Camponotus", col(1, "[Species]Genus"));
        assertEquals("maculatus", col(1, "SpeciesName"));
        assertEquals("-18.9", col(1, "LocLatitude"));
        assertEquals("9001", col(1, "taxonworks_co_id"));
    }

    public void testDateRangeIsSplit() {
        assertEquals("2024-01-02", col(1, "datecollectedstart"));
        assertEquals("2024-01-05", col(1, "datecollectedend"));
        assertEquals("2023-07-01", col(2, "datecollectedstart"));
        assertEquals("", col(2, "datecollectedend"));
        assertEquals("", col(4, "datecollectedstart"));
    }

    public void testEventIdPrefixRemoved() {
        assertEquals("BLF12345", col(1, "collectioncode"));
        assertEquals("BLF999", col(2, "collectioncode"));
    }

    public void testOwnerCasBecomesCasc() {
        assertEquals("CASC", col(1, "ownedby"));
        assertEquals("MCZC", col(2, "ownedby"));
    }

    public void testElevation() {
        assertEquals("100 - 250", col(1, "elevation"));
        assertEquals("300", col(2, "elevation"));
        assertEquals("80", col(3, "elevation"));
    }

    public void testAdm2FromCountyWhenCountryAndAdm1Agree() {
        assertEquals("Ambatondrazaka", col(2, "Adm2"));
        assertEquals("", col(1, "Adm2"));
    }

    /**
     * Quote marks in a value do not shift the columns. Found while writing this test (2026-10-19):
     * the output file writes such a value in CSV quoting ("""Smith"" collection"), and the
     * specimen upload only trims the outer quote marks (SpecimenUploadParse line 120), so the
     * stored value would keep the doubled ones. To check on staging and fix separately.
     */
    public void testQuoteMarksInAValueStayInThatColumn() {
        assertEquals("CASENT0000005", col(3, "SpecimenCode"));
        assertEquals("2022-03-04", col(3, "datecollectedstart"));
        String raw = col(3, "CollectedBy");
        assertEquals("written in CSV quoting today", "\"\"\"Smith\"\" collection\"", raw);
        assertEquals("\"Smith\" collection", raw.substring(1, raw.length() - 1).replace("\"\"", "\""));
    }

    public void testOtuNameGivesTheMorphospecies() {
        assertEquals("mg001", col(4, "SpeciesName"));
    }

    /** A required TaxonWorks column missing: the transform stops with a message naming it. */
    public void testMissingRequiredColumnIsReported() throws Exception {
        Path dir = Files.createTempDirectory("twmissing");
        List<String> src = Files.readAllLines(fixture(), StandardCharsets.UTF_8);
        List<String> header = Arrays.asList(src.get(0).split("\t", -1));
        int drop = header.indexOf("TW:Internal:otu_name");
        StringBuilder sb = new StringBuilder();
        for (String line : src.subList(0, 2)) {
            String[] f = line.split("\t", -1);
            StringBuilder l = new StringBuilder();
            for (int i = 0; i < f.length; i++) {
                if (i == drop) continue;
                if (l.length() > 0) l.append('\t');
                l.append(f[i]);
            }
            sb.append(l).append('\n');
        }
        Path in = dir.resolve("data.tsv");
        Files.write(in, sb.toString().getBytes(StandardCharsets.UTF_8));
        String err = new TaxonWorksTransformer().transformFile(in, dir.resolve("out.txt"));
        assertNotNull("a missing required column must stop the transform", err);
        assertTrue(err, err.contains("TW:Internal:otu_name"));
        assertTrue(err, err.startsWith("transformFile() record 1"));
        // Known (docs/flows/test-gaps-uploads-nightly.md, bug 12): the message reads "nullTW:Internal:otu_name".
    }
}
