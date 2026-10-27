package test.org.calacademy.antweb.upload;

import junit.framework.TestCase;
import org.calacademy.antweb.upload.SpecimenUploadParse;
import org.calacademy.antweb.upload.TaxonWorksTransformer;
import org.calacademy.antweb.util.DateUtil;
import org.calacademy.antweb.util.Scheduler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Decisions of Brian Fisher, 2026-10-27, without a database: upload dates read strictly (an impossible date is not
 * turned into another real date; day "00" is the 1st), lines with no subfamily and a genus AntCat does not know keep
 * uploading and are not listed under "Preferred Subfamily For Genus", TaxonWorks collection notes go to the
 * collectionnotes column, and a failed nightly set stops the run.
 */
public class UploadNightlyDecisionsTest extends TestCase {

    private static String date(String typed) {
        return DateUtil.getConstructDateStr(typed);
    }

    public void testImpossibleDatesAreNotOtherDates() {
        assertNull(date("2011-02-30"));      // was 2011-03-02
        assertNull(date("2010/02/30"));      // was 2010-02-28
        assertNull(date("2011-13-01"));      // was 2012-01-01
        assertNull(date("32/01/2010"));      // was 2012-08-01
        assertNull(date("2011-05-02 xyz"));  // was 2011-05-02 (text after the date ignored)
        assertNull(date("1/2/3x"));          // threw NumberFormatException
        assertNull(date("1898/1899"));
        assertNull(date("not a date"));
        assertNull(date(""));
        assertNull(date(null));
    }

    public void testDayFirstWhenTheMonthCannotBeFirst() {
        assertEquals("2013-10-19", date("19/10/2013"));  // was 2014-07-10 (month 19)
        assertEquals("2011-11-08", date("11/08/2011"));  // two readings: month first, as before
    }

    public void testDayZeroIsTheFirst() {
        assertEquals("2011-05-01", date("2011-05-00"));  // was 2011-04-30
        assertEquals("1915-07-01", date("1915-7-00"));   // was 1915-06-30
        assertEquals("2012-01-01", date("2012-00-00"));  // was 2011-11-30
        assertEquals("1986-01-01", date("1986/00/00"));
        assertEquals("1986-10-01", date("1986/10/"));
        assertEquals("2011-05-01", DateUtil.normalizeUploadDate("2011-05-00"));
        assertEquals("1986/10", DateUtil.normalizeUploadDate(" 1986/10/ "));
    }

    public void testUsualFormatsUnchanged() {
        String[][] cases = {
            {"2011-05-02", "2011-05-02"}, {" 2011-05-02 ", "2011-05-02"}, {"1 May 2011", "2011-05-01"},
            {"May 1, 2011", "2011-05-01"}, {"11 Dec 1995", "1995-12-11"}, {"2011-05", "2011-05-01"},
            {"2011", "2011-01-01"}, {"29.v.1986", "1986-05-29"}, {"10.VIII.1987", "1987-08-10"},
            {"11-14.ii.2007", "2007-02-14"}, {"8-11 Feb 2010", "2010-02-11"}, {"21-Jul-99", "1999-07-21"},
            {"08/1957", "1957-08-01"}, {"2017/", "2017-01-01"}, {"2010/08/12", "2010-08-12"},
            {"11-Oct-2015", "2015-10-11"}, {"4/2/69", "1969-04-02"}, {"7/28/1958", "1958-07-28"},
            {"7/94", "1994-07-01"}, {"Jul 2002", "2002-07-01"}, {"12 2012", "2012-12-01"},
            {"iv.1976", "1976-04-01"}, {"10 12 2012", "2012-12-10"}, {"2012-03-27 15:48:09.0", "2012-03-27"},
        };
        for (String[] c : cases) assertEquals(c[0], c[1], date(c[0]));
        assertNull("before 1700", date("21-Jul-1699"));
        assertNull("in the future", date("21-Jul-2222"));
    }

    private static final Set<String> VALID = Set.of("myrmicinae", "formicinae", "ponerinae");

    public void testUnknownGenusWithoutSubfamilyKeepsUploading() {
        assertTrue(SpecimenUploadParse.keepsUnknownGenus("(formicidae)", VALID::contains));
        assertTrue(SpecimenUploadParse.keepsUnknownGenus("(indet)", VALID::contains));
        assertTrue(SpecimenUploadParse.keepsUnknownGenus("gryllinae", VALID::contains));   // a non-ant
        assertFalse(SpecimenUploadParse.keepsUnknownGenus("myrmicinae", VALID::contains)); // "No valid Subfamily For Genus"
    }

    public void testMissingSubfamilyIsNotAPreferredSubfamilyReplacement() {
        assertFalse(SpecimenUploadParse.isPreferredSubfamilyReplacement("(formicidae)", "myrmicinae"));
        assertFalse(SpecimenUploadParse.isPreferredSubfamilyReplacement("indet", "myrmicinae"));
        assertFalse(SpecimenUploadParse.isPreferredSubfamilyReplacement(null, "myrmicinae"));
        assertFalse(SpecimenUploadParse.isPreferredSubfamilyReplacement("myrmicinae", "myrmicinae"));
        assertTrue(SpecimenUploadParse.isPreferredSubfamilyReplacement("formicinae", "myrmicinae"));
    }

    public void testTaxonWorksCollectionNotesGoToCollectionNotes() throws Exception {
        String[] headers = {
            "catalogNumber", "identifiedBy", "dateIdentified", "TW:DataAttribute:CollectionObject:LifeStageSex",
            "preparations", "TW:DataAttribute:CollectionObject:PreparedBy", "TW:DataAttribute:CollectionObject:DatePrepared",
            "TW:DataAttribute:CollectionObject:LocatedAt", "typeStatus", "TW:DataAttribute:CollectionObject:SpecimenNotes",
            "TW:DataAttribute:CollectionObject:MolProjectNotes", "recordedBy", "habitat",
            "TW:DataAttribute:CollectingEvent:Microhabitat", "samplingProtocol", "TW:DataAttribute:CollectingEvent:CollectionNotes",
            "verbatimLocality", "TW:DataAttribute:CollectingEvent:LocalityCode", "TW:DataAttribute:CollectingEvent:adm2",
            "TW:DataAttribute:CollectingEvent:adm1", "TW:DataAttribute:CollectingEvent:Country", "decimalLatitude",
            "decimalLongitude", "TW:DataAttribute:CollectingEvent:BiogeographicRegion", "TW:DataAttribute:CollectingEvent:LocalityNotes",
            "TW:Internal:elevation_precision", "genus", "specificEpithet", "infraspecificEpithet",
            "TW:DataAttribute:CollectionObject:SpeciesGroup", "subfamily", "family", "order", "class", "phylum", "kingdom",
            "TW:DataAttribute:CollectingEvent:VerbatimCoordinateUncertainty", "TW:Internal:collection_object_id",
            "fieldNumber", "country", "stateProvince", "county", "TW:Internal:otu_name", "taxonRank", "eventDate",
            "minimumElevationInMeters", "maximumElevationInMeters", "institutionCode",
        };
        String[] values = new String[headers.length];
        Arrays.fill(values, "");
        for (int i = 0; i < headers.length; i++) {
            switch (headers[i]) {
                case "catalogNumber": values[i] = "CASENT0000001"; break;
                case "TW:DataAttribute:CollectingEvent:Country": values[i] = "United States"; break;
                case "TW:DataAttribute:CollectingEvent:CollectionNotes": values[i] = "under bark, rotten log"; break;
                case "TW:DataAttribute:CollectingEvent:VerbatimCoordinateUncertainty": values[i] = "50 m"; break;
                case "genus": values[i] = "Nylanderia"; break;
                case "specificEpithet": values[i] = "vividula"; break;
                case "TW:Internal:otu_name": values[i] = "Nylanderia vividula"; break;
                case "taxonRank": values[i] = "species"; break;
                case "eventDate": values[i] = "2025-06-01"; break;
                case "institutionCode": values[i] = "CAS"; break;
                default: break;
            }
        }
        Path dir = Files.createTempDirectory("twnotes");
        Path in = dir.resolve("data.tsv");
        Path out = dir.resolve("specimen.txt");
        Files.write(in, (String.join("\t", headers) + "\n" + String.join("\t", values) + "\n").getBytes(StandardCharsets.UTF_8));
        assertNull(new TaxonWorksTransformer().transformFile(in, out));
        List<String> lines = Files.readAllLines(out, StandardCharsets.UTF_8);
        List<String> outHeaders = Arrays.asList(lines.get(0).split("\t", -1));
        assertFalse("no CollXYAccuracy column: " + outHeaders, outHeaders.contains("CollXYAccuracy"));
        int notes = outHeaders.indexOf("CollectionNotes");
        assertTrue(notes >= 0);
        assertEquals("under bark, rotten log", lines.get(1).split("\t", -1)[notes]);
        int accuracy = outHeaders.indexOf("LocXYAccuracy");
        assertEquals("50 m", lines.get(1).split("\t", -1)[accuracy]);
        // and the specimen upload stores those columns in collectionnotes and latlonmaxerror
        assertEquals("collectionnotes", SpecimenUploadParse.specimenColumnForHeader("CollectionNotes"));
        assertEquals("collectionnotes", SpecimenUploadParse.specimenColumnForHeader("CollXYAccuracy"));
        assertEquals("latlonmaxerror", SpecimenUploadParse.specimenColumnForHeader("LocXYAccuracy"));
    }

    public void testFailedSetIsRecognised() {
        assertNull(Scheduler.setFailure("<html><div class=\"wide_left\">set2 - data cleanup done in 3 mins</div></html>"));
        assertEquals("Unexpected error", Scheduler.setFailure("<html>Unexpected error</html>"));
        assertEquals("Error. No changes made for action:set3 user:null e:java.sql.SQLException: lock wait",
            Scheduler.setFailure("<div class=\"wide_left\">Error. No changes made for action:set3 user:null e:java.sql.SQLException: lock wait</div>"));
        assertEquals("no answer", Scheduler.setFailure(null));
        String line = Scheduler.failedLine(3, "Unexpected error", "12 mins");
        assertTrue(line, line.startsWith("Scheduler FAILED in set 3 "));
        assertTrue(line, line.contains("remaining sets"));
    }
}
