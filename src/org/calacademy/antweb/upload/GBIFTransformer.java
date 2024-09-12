package org.calacademy.antweb.upload;

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
import org.calacademy.antweb.util.AntwebException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


public class GBIFTransformer {


    private static final Log s_log = LogFactory.getLog(GBIFUploader.class);

    // The columns to be written in the output file. Using a list to specify column order.
    private final List<String> outputColumnOrder = new LinkedList<>();

    public GBIFTransformer() {
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
     * Transform a occurence.txt file into an AntWeb-compatible file. Renames headers, applies data transformations.
     *
     * @param inputFile  Path to the TW .tsv file to be read
     * @param outputFile Path that the transformed file will be written to
     * @return An error message string? Might be used in the future
     */
    public String transformFile(Path inputFile, Path outputFile) {

        String errMsg = null;

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
        } catch (AntwebException e) {
            errMsg = "transformFile() e:" + e;
            return errMsg;
        } catch (FileNotFoundException e) {
            errMsg = "transformFile() e:" + e;
            return errMsg;
        } catch (IOException e) {
            errMsg = "transformFile() e:" + e;
            return errMsg;
        }

        return errMsg;
    }

    private void passFieldCheck (CSVRecord line) throws AntwebException {
        // static ArrayList ***
        String[] fieldArray = {"taxonRank", "specificEpithet"   // "CatalogNumber",
                , "eventDate", "fieldNumber"
                , "minimumElevationInMeters", "maximumElevationInMeters"
                , "eventDate", "country"
                , "stateProvince", "county", "institutionCode"};  // "ownedBy",
        int c = 0;
        String notMappedMessage = null;
        ArrayList<String> fieldList = new ArrayList<>(Arrays.asList(fieldArray));
        for (String field : fieldList) {
            c++;
            if (!line.isMapped(field)) {
                if (c > 1) notMappedMessage += ", ";
                notMappedMessage += field;
            }
        }
        if (notMappedMessage != null) throw new AntwebException(notMappedMessage);
    }


    private List<String> transformLine(CSVRecord line) throws AntwebException {

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

        /*
        // Don't do this for GBIF data!
        // copy otu_name into SpeciesName (SpeciesName will be blank if there's an OTU value)
        if (StringUtils.isBlank(line.get("specificEpithet"))) {
            String field = "TW:Internal:otu_name";
            if (StringUtils.isNotBlank(line.get(field))) {
                String otu_name = line.get(field);

                // for a subspecies name,
                // Plagiolepis jerdonii jerdonii-rogeri
                String[] name_parts = otu_name.split(" ");
                String finest_name = name_parts[name_parts.length - 1];

                switch (line.get("taxonRank")) {
                    case "genus":
                        row.put("SpeciesName", finest_name);
                        break;

                    case "species":
                        row.put("Subspecies", finest_name);
                        break;
                }
            }
        }
         */

        // split date collected into two columns
        Pair<String, String> dates = splitDate(line.get("eventDate"));
        row.put("datecollectedstart", dates.getLeft());
        row.put("datecollectedend", dates.getRight());

        // trim eventid: from collecting events
        row.put("collectioncode", StringUtils.removeStart(line.get("fieldNumber"), "eventID:"));

        //if the param is not mapped, or it is mapped but is empty... then use...
        // use auto-generated country if not overridden
        String header = "TW:DataAttribute:CollectingEvent:Country";
        if (!line.isMapped(header) || StringUtils.isEmpty(line.get(header))) {
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
                (line.isMapped("TW:DataAttribute:CollectingEvent:Country")) ? line.get("TW:DataAttribute:CollectingEvent:Country") : null,
                (line.isMapped("TW:DataAttribute:CollectingEvent:adm1")) ? line.get("TW:DataAttribute:CollectingEvent:adm1") : null,
                (line.isMapped("TW:DataAttribute:CollectingEvent:adm2")) ? line.get("TW:DataAttribute:CollectingEvent:adm2") : null,
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
}
