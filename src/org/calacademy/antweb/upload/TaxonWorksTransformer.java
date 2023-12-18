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

    private final List<String> outputColumnOrder = new ArrayList<>();

    public TaxonWorksTransformer() {
        for (Pair<String, String> pair : directHeaderTranslations) {
            outputColumnOrder.add(pair.getRight());
        }
        // generated columns go here
        // taken from fieldNumber, strip 'eventID:' prefix
        List<String> generatedOutputColumns = Arrays.asList(
                "datecollectedstart",
                "datecollectedend",
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
                csvPrinter.printRecord(transformed);
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

        // perform direct header translations
        for (Pair<String, String> pair : directHeaderTranslations) {
            String oldName = pair.getLeft();
            
            String value = line.get(oldName);
            row.put(pair.getRight(), value);
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

        return outputColumnOrder
                .stream()
                .map(row::get)
                .collect(Collectors.toList());
    }


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

//    private String calculateElevationMaxError(String minimumElevation, String maximumElevation) {
//        if (StringUtils.isBlank(minimumElevation) || StringUtils.isBlank(maximumElevation)) {
//            return "";
//        }
//    }

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
            Pair.of("institutionCode", "OwnedBy"),
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
//            Pair.of("TW:CollectingEvent:elevation_precision", "ElevationMaxError"),  // not included in export, not a DwC field
            Pair.of("genus", "[Species]Genus"),
            Pair.of("specificEpithet", "SpeciesName"),
            Pair.of("infraspecificEpithet", "Subspecies"),
//            Pair.of("subgenus", "Subgenus"),  // not included in export
            Pair.of("TW:DataAttribute:CollectionObject:SpeciesGroup", "SpeciesGroup"),
//            Pair.of("subfamily", "Subfamily"),    // not included in export
            Pair.of("family", "Family"),
            Pair.of("order", "Order"),
            Pair.of("class", "Class"),
            Pair.of("phylum", "Phylum"),
            Pair.of("kingdom", "Kingdom"),
            Pair.of("TW:DataAttribute:CollectingEvent:VerbatimCoordinateUncertainty", "LocXYAccuracy"),
    };

    private final String[] extraInputHeaders = {
            "fieldNumber",  // must have 'eventID:' prefix removed
            "country",
            "stateProvince",
            "county",
            "TW:DataAttribute:CollectionObject:verbatimTypeStatus",
            "TW:DataAttribute:CollectingEvent:VerbatimCollectionCode",  // maybe will use to validate fieldNumber?
    };
}
