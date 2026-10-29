package org.calacademy.antweb.upload;

import org.calacademy.antweb.util.AntwebProps;

import org.calacademy.antweb.util.*;

import org.calacademy.antweb.util.AntwebProps;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.util.GeolocaleMgr;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Turns the data.tsv of a CAS TaxonWorks Darwin Core archive into an AntWeb specimen file (transformFile):
 * renames columns (directHeaderTranslations), builds the date range, collection code, elevation, owner (CAS
 * to CASC) and previous determinations, and leaves out records whose column count is wrong (getSkippedRecords).
 * Used by TaxonWorksUploader (manual TaxonWorks upload and the nightly job). Files only, no database.
 * Docs: docs/flows/specimen-upload.md (section 2.2).
 * Tests: TaxonWorksTransformFixtureTest (fixtures/taxonworks-data.tsv), PreviousDeterminationsTest.
 */
public class TaxonWorksTransformer extends Transformer {


    private static final Log s_log = LogFactory.getLog(TaxonWorksTransformer.class);

    // The columns to be written in the output file. Using a list to specify column order.
    private final List<String> outputColumnOrder = new LinkedList<>();


    // list of TW headers and their AntWeb counterparts
    private final Pair<String, String>[] directHeaderTranslations = new Pair[]{
            Pair.of("catalogNumber", "SpecimenCode"),
            Pair.of("identifiedBy", "DeterminedBy"),
            Pair.of("dateIdentified", "DateDetermined"),
            Pair.of("TW:DataAttribute:CollectionObject:LifeStageSex", "LifeStageSex"),
            Pair.of("preparations", "Medium"),
            Pair.of("TW:DataAttribute:CollectionObject:PreparedBy", "PreparedBy"),
            Pair.of("TW:DataAttribute:CollectionObject:DatePrepared", "DatePrepared"),
            Pair.of("TW:DataAttribute:CollectionObject:LocatedAt", "LocatedAt"),
            Pair.of("typeStatus", "TypeStatus"),
//            Pair.of("institutionCode", "OwnedBy"),    // convert CAS to CASC
            Pair.of("TW:DataAttribute:CollectionObject:SpecimenNotes", "SpecimenNotes"),
            Pair.of("TW:DataAttribute:CollectionObject:MolProjectNotes", "DNANotes"),
            Pair.of("recordedBy", "CollectedBy"),
            Pair.of("habitat", "Habitat"),
            Pair.of("TW:DataAttribute:CollectingEvent:Microhabitat", "Microhabitat"),
            Pair.of("samplingProtocol", "Method"),
            // Collection notes (Brian Fisher, 2026-10-27). Was "CollXYAccuracy", the old Biota name that the specimen
            // upload also stores in collectionnotes (SpecimenUploadParse.buildColumnTranslations), so the column was
            // already right; the name now says what it is.
            Pair.of("TW:DataAttribute:CollectingEvent:CollectionNotes", "CollectionNotes"),
            Pair.of("verbatimLocality", "LocalityName"),
            Pair.of("TW:DataAttribute:CollectingEvent:LocalityCode", "LocalityCode"),
            Pair.of("TW:DataAttribute:CollectingEvent:adm2", "Adm2"),
            Pair.of("TW:DataAttribute:CollectingEvent:adm1", "Adm1"),
            Pair.of("TW:DataAttribute:CollectingEvent:Country", "Country"),
            Pair.of("decimalLatitude", "LocLatitude"),
            Pair.of("decimalLongitude", "LocLongitude"),
            Pair.of("TW:DataAttribute:CollectingEvent:BiogeographicRegion", "BiogeographicRegion"),
            Pair.of("TW:DataAttribute:CollectingEvent:LocalityNotes", "LocalityNotes"),
            Pair.of("TW:Internal:elevation_precision", "ElevationMaxError"),
            Pair.of("genus", "[Species]Genus"),
            Pair.of("specificEpithet", "SpeciesName"),
            Pair.of("infraspecificEpithet", "Subspecies"),
//            Pair.of("subgenus", "Subgenus"),  // not included in export
            Pair.of("TW:DataAttribute:CollectionObject:SpeciesGroup", "SpeciesGroup"),
            Pair.of("subfamily", "Subfamily"),    // not included in export
            Pair.of("family", "Family"),
            Pair.of("order", "Order"),
            Pair.of("class", "Class"),
            Pair.of("phylum", "Phylum"),
            Pair.of("kingdom", "Kingdom"),
            Pair.of("TW:DataAttribute:CollectingEvent:VerbatimCoordinateUncertainty", "LocXYAccuracy"),
            Pair.of("TW:Internal:collection_object_id", "taxonworks_co_id"),
    };

    // these are just extra headers that we might use, right now the CSV parser doesn't check for header column presence
    // in the future, we could perform more strict checking that certain headers exist.
    private final String[] extraInputHeaders = {
            "fieldNumber",  // must have 'eventID:' prefix removed
            "country",
            "stateProvince",
            "county",
            "TW:DataAttribute:CollectionObject:verbatimTypeStatus",
            "TW:DataAttribute:CollectingEvent:VerbatimCollectionCode",  // maybe will use to validate fieldNumber?
            "TW:Internal:otu_name",
            "rank",
    };

    // Darwin Core previousIdentifications (newest first, entries separated by " | ") is
    // stored as is in specimen.previous_determinations. Optional: older exports lack it.
    static final String PREVIOUS_IDENTIFICATIONS_IN = "previousIdentifications";
    static final String PREVIOUS_DETERMINATIONS_OUT = "previousDeterminations";

    /** previousIdentifications value as stored: trimmed, blank becomes null. */
    public static String previousDeterminations(String previousIdentifications) {
        if (previousIdentifications == null) return null;
        String value = previousIdentifications.trim();
        return value.isEmpty() ? null : value;
    }

    public TaxonWorksTransformer() {
        for (Pair<String, String> pair : directHeaderTranslations) {
            outputColumnOrder.add(pair.getRight());
        }

        // generated columns go here
        List<String> generatedOutputColumns = Arrays.asList(
                "datecollectedstart",
                "datecollectedend",
                "elevation",
                "ElevationMaxError",
                "ownedby",
                "collectioncode",   // taken from fieldNumber, strip 'eventID:' prefix
                PREVIOUS_DETERMINATIONS_OUT   // from previousIdentifications, when the export has it
        );
        outputColumnOrder.addAll(generatedOutputColumns);
    }

    // TaxonWorks writes one record per line, fields separated by tabs. Quote marks are
    // ordinary characters: a value such as  "Smith" collection  must not start a quoted
    // field (that merged columns and stopped uploads). See readLines() for "..." values.
    private final CSVFormat csvInputFormat = CSVFormat.TDF.builder()
            .setAllowMissingColumnNames(true)
            .setHeader()
            .setSkipHeaderRecord(true)
            .setQuote(null)
            .setIgnoreSurroundingSpaces(false)
            .build();

    /**
     * Copy the file, turning a field written in CSV quoting ("a ""b"" c") into its plain
     * value (a "b" c). Other fields, including ones that merely contain quote marks,
     * are left alone.
     */
    static void unquoteFields(Path inputFile, Path outputFile) throws IOException {
        try (BufferedReader in = Files.newBufferedReader(inputFile, java.nio.charset.StandardCharsets.UTF_8);
             BufferedWriter out = Files.newBufferedWriter(outputFile, java.nio.charset.StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                String[] fields = line.split("\t", -1);
                for (int i = 0; i < fields.length; i++) {
                    String f = fields[i];
                    if (f.length() >= 2 && f.startsWith("\"") && f.endsWith("\"")
                            && f.substring(1, f.length() - 1).replace("\"\"", "").indexOf('"') < 0) {
                        fields[i] = f.substring(1, f.length() - 1).replace("\"\"", "\"");
                    }
                }
                out.write(String.join("\t", fields));
                out.write('\n');
            }
        }
    }

    /**
     * Transform a TaxonWorks .tsv file into an AntWeb-compatible file. Renames headers, applies data transformations.
     *
     * @param inputFile  Path to the TW .tsv file to be read
     * @param outputFile Path that the transformed file will be written to
     * @return An error message string? Might be used in the future
     */
    private final List<String> skippedRecords = new ArrayList<>();

    /** Records left out by the last transformFile() because their column count was wrong. */
    public List<String> getSkippedRecords() {
        return skippedRecords;
    }

    public String transformFile(Path inputFile, Path outputFile) throws AntwebException {
        skippedRecords.clear();

        String errMsg = null;

        CSVFormat csvOutputFormat = CSVFormat.TDF.builder()
                .setAllowMissingColumnNames(true)
                .setHeader(outputColumnOrder.toArray(new String[0]))
                .build();

        Path plainInput = inputFile.resolveSibling(inputFile.getFileName() + ".plain");
        try {
            unquoteFields(inputFile, plainInput);
        } catch (IOException e) {
            return "transformFile() e:" + e;
        }

        int c = 0;
        try (Reader in = Files.newBufferedReader(plainInput, java.nio.charset.StandardCharsets.UTF_8);
            BufferedWriter writer = Files.newBufferedWriter(outputFile);
            CSVParser csvParser = new CSVParser(in, csvInputFormat);
            CSVPrinter csvPrinter = new CSVPrinter(writer, csvOutputFormat)) {
            for (CSVRecord record : csvParser) {
                c++;
                if (!record.isConsistent()) {
                    // Fewer or more columns than the header: usually a line break or tab inside
                    // a text field in TaxonWorks. Skip this record rather than the whole file.
                    String code = record.isSet("catalogNumber") ? record.get("catalogNumber") : "";
                    skippedRecords.add("line " + record.getRecordNumber() + " (" + record.size() + " of "
                        + csvParser.getHeaderNames().size() + " columns)" + (code.isEmpty() ? "" : " catalogNumber " + code));
                    continue;
                }
                List<String> transformed = transformLine(record);
                if (!transformed.isEmpty()) {
                    csvPrinter.printRecord(transformed);
                }
            }
        } catch (AntwebException | RuntimeException e) {
            errMsg = "transformFile() record " + c + ": " + e;
            return errMsg;
        } catch (FileNotFoundException e) {
            errMsg = "transformFile() e:" + e;
            return errMsg;
        } catch (IOException e) {
            errMsg = "transformFile() e:" + e;
            return errMsg;
        } finally {
            try { Files.deleteIfExists(plainInput); } catch (IOException ignore) { }
        }

        return errMsg;
    }


    // These are the must have fields. Ommission would break transformLine().
    private void passFieldCheck (CSVRecord line) throws AntwebException {
        String[] fieldArray = {"TW:Internal:otu_name", "TW:DataAttribute:CollectingEvent:Country"
            , "TW:DataAttribute:CollectingEvent:adm1", "TW:DataAttribute:CollectingEvent:adm2"};
        // The missing columns, comma separated (the text began with "null" until 2026-10-27).
        List<String> notMapped = new ArrayList<>();
        for (String field : fieldArray) {
            if (!line.isMapped(field)) notMapped.add(field);
        }
        if (!notMapped.isEmpty()) throw new AntwebException(String.join(", ", notMapped));
    }


    private List<String> transformLine(CSVRecord line) throws AntwebException  {

        passFieldCheck(line);

        Map<String, String> row = new HashMap<>();

        if (StringUtils.isBlank(line.get("catalogNumber")) || StringUtils.equals(line.get("catalogNumber"), "\"\"")) {
            return Collections.emptyList();
        }

        // perform direct header translations
        for (Pair<String, String> pair : directHeaderTranslations) {
            String oldName = pair.getLeft();
            try {
                String value = line.get(oldName);
                row.put(pair.getRight(), value);
            } catch (Exception e) {
                //A.log("transformLines() e for oldName:" + oldName + " right:" + pair.getRight() + " line:" + line);
                if (!AntwebProps.isDevMode()) throw e;
            }
        }

        // copy otu_name into SpeciesName (SpeciesName will be blank if there's an OTU value)
        if (StringUtils.isBlank(line.get("specificEpithet")) && StringUtils.isNotBlank(line.get("TW:Internal:otu_name"))) {
            String otu_name = line.get("TW:Internal:otu_name");

            // for a subspecies name,
            // Plagiolepis jerdonii jerdonii-rogeri
            String[] name_parts = otu_name.split(" ");
            String finest_name = name_parts[name_parts.length-1];

            switch (line.get("taxonRank")) {
                case "genus":
                    row.put("SpeciesName", finest_name);
                    break;

                case "species":
                    row.put("Subspecies", finest_name);
                    break;
            }
        }

        // split date collected into two columns
        Pair<String, String> dates = splitDate(line.get("eventDate"));
        row.put("datecollectedstart", dates.getLeft());
        row.put("datecollectedend", dates.getRight());

        // trim eventid: from collecting events
        row.put("collectioncode", StringUtils.removeStart(line.get("fieldNumber"), "eventID:"));

        // use auto-generated country if not overridden
        if (StringUtils.isEmpty(line.get("TW:DataAttribute:CollectingEvent:Country"))) {
            String country = line.get("country");
            // only include if country is recognized by antweb
            // I think it's easier to debug if we don't transform it into the valid country name here
            if (StringUtils.isNotEmpty(country)) {
                Country c = GeolocaleMgr.getCountry(country);
                if (c != null) {
                    row.put("Country", line.get("country"));
                }
            }
        }

        // disabled until GeoBoundaries is added as a data source for TW, generated stateProvince is too inconsistent
        /*if (StringUtils.isEmpty(line.get("TW:DataAttribute:CollectingEvent:adm1"))) {
            String stateProvince = line.get("stateProvince");
            if (StringUtils.isNotEmpty(stateProvince)) {
                row.put("Adm1", stateProvince);
            }
        }*/

        // validate ADM data
        row.put("Adm2", setAdm2(
                line.get("TW:DataAttribute:CollectingEvent:Country"),
                line.get("TW:DataAttribute:CollectingEvent:adm1"),
                line.get("TW:DataAttribute:CollectingEvent:adm2"),
                line.get("country"),
                line.get("stateProvince"),
                line.get("county")));

        row.put("elevation", buildElevation(
                line.get("minimumElevationInMeters"),
                line.get("maximumElevationInMeters")));

        row.put("ownedby", setOwnedBy(line.get("institutionCode")));

        if (line.isMapped(PREVIOUS_IDENTIFICATIONS_IN)) {
            row.put(PREVIOUS_DETERMINATIONS_OUT, previousDeterminations(line.get(PREVIOUS_IDENTIFICATIONS_IN)));
        }

        return outputColumnOrder
                .stream()
                .map(row::get)
                .collect(Collectors.toList());
    }


}
