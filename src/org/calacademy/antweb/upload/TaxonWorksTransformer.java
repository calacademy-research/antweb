package org.calacademy.antweb.upload;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class TaxonWorksTransformer {


    private static final Log s_log = LogFactory.getLog(TaxonWorksUploader.class);

    // The columns to be written in the output file. Using a list to specify column order.
    private final List<String> outputColumnOrder = new LinkedList<>();

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
                "collectioncode"    // taken from fieldNumber, strip 'eventID:' prefix
        );
        outputColumnOrder.addAll(generatedOutputColumns);
    }

    private final CSVFormat csvInputFormat = CSVFormat.TDF.builder()
            .setAllowMissingColumnNames(true)
            .setHeader()
            .setSkipHeaderRecord(true)
            .build();

    /**
     * Transform a TaxonWorks .tsv file into an AntWeb-compatible file. Renames headers, applies data transformations.
     *
     * @param inputFile  Path to the TW .tsv file to be read
     * @param outputFile Path that the transformed file will be written to
     * @return An error message string? Might be used in the future
     */
    public String transformFile(Path inputFile, Path outputFile) {

        CSVFormat csvOutputFormat = CSVFormat.TDF.builder()
                .setAllowMissingColumnNames(true)
                .setHeader(outputColumnOrder.toArray(new String[0]))
                .build();

        try (Reader in = new FileReader(inputFile.toFile());
             BufferedWriter writer = Files.newBufferedWriter(outputFile);
             CSVParser csvParser = new CSVParser(in, csvInputFormat);
             CSVPrinter csvPrinter = new CSVPrinter(writer, csvOutputFormat)) {

            for (CSVRecord record : csvParser) {
                List<String> transformed = transformLine(record);
                if (!transformed.isEmpty()) {
                    csvPrinter.printRecord(transformed);
                }
            }


        } catch (FileNotFoundException e) {
            s_log.error("FileNotFound", e);
            return "File not found";
        } catch (IOException e) {
            s_log.error("IOException", e);
            return "IO Exception";
        }

        return null;
    }

    private List<String> transformLine(CSVRecord line) {
        
        Map<String, String> row = new HashMap<>();

        if (StringUtils.isBlank(line.get("catalogNumber")) || StringUtils.equals(line.get("catalogNumber"), "\"\"")) {
            return Collections.emptyList();
        }

        // perform direct header translations
        for (Pair<String, String> pair : directHeaderTranslations) {
            String oldName = pair.getLeft();
            
            String value = line.get(oldName);
            row.put(pair.getRight(), value);
        }

        // copy otu_name into SpeciesName (SpeciesName will be blank if there's an OTU value)
        if (StringUtils.isNotBlank(line.get("TW:Internal:otu_name"))) {
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

        // validate ADM data
        row.put("adm2", setAdm2(
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

        return outputColumnOrder
                .stream()
                .map(row::get)
                .collect(Collectors.toList());
    }


    /**
     * Splits an ISO-8601 date duration (YYYY-MM-DD/YYYY-MM-DD) into two separate date strings
     *
     * @param eventDate The date duration to split
     * @return a Pair of (first date, second date), or (first date, "") if only one date
     */
    private Pair<String, String> splitDate(String eventDate) {
        String[] dates = eventDate.split("/", 2);

        // split will always return at least 1 element ({""} if eventDate is empty)
        if (dates.length == 2) {
            return Pair.of(dates[0], dates[1]);
        } else {
            return Pair.of(dates[0], "");
        }
    }

    /**
     * Fills the adm2 entry with TW-computed county data if country & stateprovince data match, and original adm2 is empty
     */
    private String setAdm2(String verbatimCountry, String adm1, String adm2, String computedCountry, String stateProvince, String county) {
        if (StringUtils.equals(verbatimCountry, computedCountry)
                && StringUtils.equals(adm1, stateProvince)
                && StringUtils.isBlank(adm2)
                && StringUtils.isNotBlank(county)) {
            return county;
        }
        return adm2;
    }

    /**
     * Generate elevation string from min and max elevation strings.
     * AntWeb converts it to a single number, so no point adding units or >/< signs
     *
     * @return empty string if both elevations are blank, one elevation if one is blank,
     * or "num - num" if both elevations have a value
     */
    private static String buildElevation(String minElevation, String maxElevation) {

        // this covers the case of (val, "") and ("", "")
        if (StringUtils.isBlank(maxElevation)) {
            return minElevation;
        }

        // covers ("", val)
        if (StringUtils.isBlank(minElevation)) {
            return maxElevation;
        }

        // strip decimal points off when we might output a range.
        // importer can handle a single number w/ decimal point, but not two
        minElevation = StringUtils.substringBefore(minElevation, ".");
        maxElevation = StringUtils.substringBefore(maxElevation, ".");
        // if both elevations are the same, just return one of them
        if (minElevation.equals(maxElevation)) {
            return minElevation;
        } else {
            return minElevation + " - " + maxElevation;
        }
    }

    /**
     * Updates ownedBy to use Antweb codes so links can be generated.
     *
     * Currently, only replaces CAS with CASC.
     */
    private static String setOwnedBy(String ownerRepository) {
        if ("CAS".equals(ownerRepository)) {
            return "CASC";
        }

        return ownerRepository;
    }

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
            Pair.of("TW:DataAttribute:CollectingEvent:CollectionNotes", "CollXYAccuracy"),
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
}
