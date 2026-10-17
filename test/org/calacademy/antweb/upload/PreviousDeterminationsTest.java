package test.org.calacademy.antweb.upload;

import junit.framework.TestCase;
import org.calacademy.antweb.Specimen;
import org.calacademy.antweb.upload.SpecimenUploadParse;
import org.calacademy.antweb.upload.TaxonWorksTransformer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Previous determinations: TaxonWorks previousIdentifications and the specimen-file column previousDeterminations. */
public class PreviousDeterminationsTest extends TestCase {

    private static final String TWO = "Nylanderia Emery, 1906: US_CA01 by Ward on 2025-12-10 | Nylanderia: US_CA01 by Fisher on 2000-11-10";

    // Every TaxonWorks column the transformer reads. Country is filled so no GeolocaleMgr lookup is needed.
    private static final String[] TW_HEADERS = {
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

    private static String twRow(String code, boolean withPrevious, String previous) {
        String[] values = new String[TW_HEADERS.length];
        Arrays.fill(values, "");
        for (int i = 0; i < TW_HEADERS.length; i++) {
            switch (TW_HEADERS[i]) {
                case "catalogNumber": values[i] = code; break;
                case "identifiedBy": values[i] = "Ward"; break;
                case "TW:DataAttribute:CollectingEvent:Country": values[i] = "United States"; break;
                case "genus": values[i] = "Nylanderia"; break;
                case "specificEpithet": values[i] = "vividula"; break;
                case "TW:Internal:otu_name": values[i] = "Nylanderia vividula"; break;
                case "taxonRank": values[i] = "species"; break;
                case "eventDate": values[i] = "2025-06-01"; break;
                case "institutionCode": values[i] = "CAS"; break;
                default: break;
            }
        }
        String line = String.join("\t", values);
        return withPrevious ? line + "\t" + previous : line;
    }

    private static List<String> transform(boolean withPrevious, String... previous) throws Exception {
        Path dir = Files.createTempDirectory("prevdet");
        Path in = dir.resolve("data.tsv");
        Path out = dir.resolve("specimen.txt");
        StringBuilder sb = new StringBuilder(String.join("\t", TW_HEADERS));
        if (withPrevious) sb.append("\tpreviousIdentifications");
        sb.append('\n');
        for (int i = 0; i < Math.max(1, previous.length); i++) {
            sb.append(twRow("CASENT00000" + i, withPrevious, previous.length > i ? previous[i] : "")).append('\n');
        }
        Files.write(in, sb.toString().getBytes(StandardCharsets.UTF_8));
        String err = new TaxonWorksTransformer().transformFile(in, out);
        assertNull("transformFile error: " + err, err);
        return Files.readAllLines(out, StandardCharsets.UTF_8);
    }

    private static String column(List<String> lines, int row, String header) {
        List<String> headers = Arrays.asList(lines.get(0).split("\t", -1));
        int index = headers.indexOf(header);
        assertTrue("output has no column " + header + ": " + headers, index >= 0);
        return lines.get(row).split("\t", -1)[index];
    }

    public void testValueIsTrimmedAndBlankIsNull() {
        assertEquals(TWO, TaxonWorksTransformer.previousDeterminations("  " + TWO + " "));
        assertNull(TaxonWorksTransformer.previousDeterminations(""));
        assertNull(TaxonWorksTransformer.previousDeterminations("   "));
        assertNull(TaxonWorksTransformer.previousDeterminations(null));
    }

    public void testTaxonWorksColumnIsCopiedAsIs() throws Exception {
        List<String> lines = transform(true, " " + TWO + " ", "");
        assertEquals(3, lines.size());
        assertEquals(TWO, column(lines, 1, "previousDeterminations"));
        assertEquals("", column(lines, 2, "previousDeterminations"));
        assertEquals("Ward", column(lines, 1, "DeterminedBy"));
    }

    public void testExportWithoutTheColumnStillTransforms() throws Exception {
        List<String> lines = transform(false);
        assertEquals(2, lines.size());
        assertEquals("", column(lines, 1, "previousDeterminations"));
        assertEquals("casent000000", column(lines, 1, "SpecimenCode").toLowerCase());
    }

    public void testTransformedHeaderMapsToTheNewColumn() {
        // The transformer's output header must be one the specimen upload stores.
        assertEquals("previous_determinations", SpecimenUploadParse.specimenColumnForHeader("previousDeterminations"));
    }

    public void testUploadHeaderIsCaseInsensitive() {
        assertEquals("previous_determinations", SpecimenUploadParse.specimenColumnForHeader("previousdeterminations"));
        assertEquals("previous_determinations", SpecimenUploadParse.specimenColumnForHeader(" PreviousDeterminations "));
        assertEquals("previous_determinations", SpecimenUploadParse.specimenColumnForHeader("PREVIOUSDETERMINATIONS"));
    }

    public void testExistingUploadHeadersUnchanged() {
        assertEquals("determinedby", SpecimenUploadParse.specimenColumnForHeader("DeterminedBy"));
        assertEquals("datedetermined", SpecimenUploadParse.specimenColumnForHeader("DateDetermined"));
        assertEquals("code", SpecimenUploadParse.specimenColumnForHeader("SpecimenCode"));
        assertEquals("caste", SpecimenUploadParse.specimenColumnForHeader("LifeStageSex"));
        assertEquals("taxonworks_co_id", SpecimenUploadParse.specimenColumnForHeader("taxonworks_co_id"));
        assertNull(SpecimenUploadParse.specimenColumnForHeader("SomethingElse"));
        assertNull(SpecimenUploadParse.specimenColumnForHeader("previous_determination"));
        assertNull(SpecimenUploadParse.specimenColumnForHeader(null));
    }

    public void testDisplaySplitsOnBar() {
        assertEquals(Arrays.asList("Nylanderia Emery, 1906: US_CA01 by Ward on 2025-12-10", "Nylanderia: US_CA01 by Fisher on 2000-11-10"),
            Specimen.splitPreviousDeterminations(TWO));
        assertEquals(Arrays.asList("Camponotus|x"), Specimen.splitPreviousDeterminations("Camponotus|x"));
        assertTrue(Specimen.splitPreviousDeterminations(null).isEmpty());
        assertTrue(Specimen.splitPreviousDeterminations("  ").isEmpty());
        Specimen specimen = new Specimen();
        assertTrue(specimen.getPreviousDeterminationList().isEmpty());
        specimen.setPreviousDeterminations("<b>x</b>");
        assertEquals(Arrays.asList("<b>x</b>"), specimen.getPreviousDeterminationList());   // escaped in the JSP
    }
}
