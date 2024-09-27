package org.calacademy.antweb.upload;

import org.calacademy.antweb.Genus;
import org.calacademy.antweb.util.AntwebProps;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


public class GBIFTransformer extends Transformer {

    private static final Log s_log = LogFactory.getLog(GBIFTransformer.class);

    // The columns to be written in the output file. Using a list to specify column order.
    private final List<String> outputColumnOrder = new LinkedList<>();

    // list of GBIF headers and their AntWeb counterparts
    // On the left are GBIF header values. On the right are Antweb header values.
    private final Pair<String, String>[] directHeaderTranslations = new Pair[]{
            //Pair.of("id", "SpecimenCode"),
              Pair.of("catalogNumber", "SpecimenCode")
            , Pair.of("identifiedBy", "DeterminedBy")
            , Pair.of("dateIdentified", "DateDetermined")
            , Pair.of("preparations", "Medium")
            , Pair.of("typeStatus", "TypeStatus")
            , Pair.of("institutionCode", "OwnedBy")    // convert CAS to CASC
            , Pair.of("recordedBy", "CollectedBy")
            , Pair.of("habitat", "Habitat")
            , Pair.of("samplingProtocol", "Method")
            , Pair.of("verbatimLocality", "LocalityName")
            , Pair.of("decimalLatitude", "LocLatitude")
            , Pair.of("decimalLongitude", "LocLongitude")
            , Pair.of("genus", "[Species]Genus")
            , Pair.of("specificEpithet", "SpeciesName")
            , Pair.of("infraspecificEpithet", "Subspecies")
            //, Pair.of("subgenus", "Subgenus"),  // not included in export
            //, Pair.of("subfamily", "Subfamily"),    // not included in export
            , Pair.of("family", "Family")
            , Pair.of("order", "Order")
            , Pair.of("class", "Class")
            , Pair.of("phylum", "Phylum")
            , Pair.of("kingdom", "Kingdom")
            //, Pair.of("collectionCode", "collectionCode")
            , Pair.of("country", "country")
            , Pair.of("stateProvince", "adm1")
            , Pair.of("county", "adm2")
            , Pair.of("type", "type")
    };

    public GBIFTransformer() {
        for (Pair<String, String> pair : directHeaderTranslations) {
            outputColumnOrder.add(pair.getRight());
        }

        // generated columns go here
        List<String> generatedOutputColumns = Arrays.asList(
                  "Subfamily"
                , "datecollectedstart"
                , "datecollectedend"
                , "elevation"
                , "ElevationMaxError"
                , "ownedby"
                , "collectioncode"    // taken from fieldNumber, strip 'eventID:' prefix
        );

        outputColumnOrder.addAll(generatedOutputColumns);

        //outputColumnOrder.add("subfamily");

        A.log("outputColumnOrder:" + String.join(", ", outputColumnOrder));
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
    public String transformFile(Path inputFile, Path outputFile) throws AntwebException {

        String errMsg = null;

        CSVFormat csvOutputFormat = CSVFormat.TDF.builder()
                .setAllowMissingColumnNames(true)
                .setHeader(outputColumnOrder.toArray(new String[0]))
                .build();

        //A.log("transformFile() header:" + csvOutputFormat);
        // [SpecimenCode, DeterminedBy, DateDetermined, Medium, TypeStatus, OwnedBy, CollectedBy, Habitat, Method, LocalityName,
        // LocLatitude, LocLongitude, [Species]Genus, SpeciesName, Subspecies, Family, Order, Class, Phylum, Kingdom]
        //A.log("transformFile() header:" + csvOutputFormat.getHeader());
        //A.log("transformFile() columns:" + outputColumnOrder.toArray(new String[0]));

        try (Reader in = new FileReader(inputFile.toFile());
             BufferedWriter writer = Files.newBufferedWriter(outputFile);
             CSVParser csvParser = new CSVParser(in, csvInputFormat);
             CSVPrinter csvPrinter = new CSVPrinter(writer, csvOutputFormat)) {

            int c = 0;
            int testLimit = 5;
            Iterator<CSVRecord> iterator = csvParser.iterator();
            while (true) {
                ++c;

                if (AntwebProps.isDevMode() && c > testLimit) break;

                try {
                    // So we can have the condition inside the try/catch block.
                    if (!iterator.hasNext()) {
                        A.log("tranformFile() break on hasNext at line:" + c);
                        break;
                    }

                    CSVRecord record = iterator.next();
                    List<String> transformed = transformLine(record, c);
                    //A.log("transformFile() c:" + c + " transformed:" + transformed);

// transformed:[http://arctos.database.museum/guid/UTEP:Ento:3034?seid=4325710, unknown, , whole organism (pinned), , UTEP,
// Collector(s): William P. Mackay, Emma Mackay, Position: Level, Vegetation Description: forest, rain, next to road,
// Vegetation Abundance: Sparse, Soil Texture: Clay-loam, Soil Drainage: Poor, Soil Moisture: Moist,
// Organic Content in Soil: High; Microhabitat: Stone (under), nest female, , Mexico, Nayarit, Jala, 60 k SW Tepic (rest area on camino cuota),
// elev. 1159 (21Deg, 8', 43secN 104Deg, 29', 4secW), 21.1452777778, -104.4844444444, Pheidole, obtusospinosa, ,
// Formicidae, Hymenoptera, Insecta, Arthropoda, Animalia]

                    if (transformed != null && !transformed.isEmpty()) {
                        csvPrinter.printRecord(transformed);
                    }

                } catch (UncheckedIOException e) {
                    A.log("tranformFile() line:" + c + " e:" + e.toString());
                    //A.log("transformFile() sampleErrorLine:" + sampleErrorLine);
                    //errMsg = "transformFile() e:" + e;
                    continue;
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

        A.log("TranformFile() complete. NotAntCount:" + s_notAntCount + " subfamilyFoundCount:" + s_subfamilyFoundCount
        + " noGenusCount:"+ s_noGenusCount + " noSpeciesCount:"+ s_noSpeciesCount);

        return errMsg;
    }

    private static String sampleErrorLine = null;
    private static int s_notAntCount = 0;
    private static int s_subfamilyFoundCount = 0;
    private static int s_noGenusCount = 0;
    private static int s_noSpeciesCount = 0;
    private static int s_noSubfamilyCount = 0;

    private List<String> transformLine(CSVRecord line, int c) throws AntwebException {

        int testNum = 5;

        passFieldCheck(line);

        Map<String, String> row = new HashMap<>();

        if (StringUtils.isBlank(line.get("catalogNumber")) || StringUtils.equals(line.get("catalogNumber"), "\"\"")) {
            return Collections.emptyList();
        }

        // perform direct header translations
        for (Pair<String, String> pair : directHeaderTranslations) {
            String GBIFName = pair.getLeft();
            String antwebName = pair.getRight();
            try {

                String value = line.get(GBIFName);

                if ("genus".equals(GBIFName)) {
                    String subfamily = TaxonProxy.inferSubfamily(value);
                    row.put("Subfamily", subfamily);
                    //if (c <= testNum) A.log("transformLine() GBIFName:" + GBIFName + " antwebName:" + antwebName + " subfamily:" + subfamily + " value:" + value);
                }
                //if (c <= testNum) A.log("transformLine() antwebName:" + antwebName + " GBIFName:" + GBIFName + " value:" + value);

                row.put(antwebName, value);

                //if (List.of("subfamily", "genus", "specificEpithet").contains(GBIFName))
                //A.log("transformLine() directTranslations GBIFName:" + GBIFName + " antwebName:" + antwebName + " value:" + value);
            } catch (Exception e) {
                A.log("transformLine() GBIFName:" + GBIFName + " antwebName:" + antwebName + " e:" + e); // + " line:" + line);
                if (sampleErrorLine == null) sampleErrorLine = line.toString();
                AntwebUtil.logStackTrace(e);
            }
        }

        String family = row.get("Family");
        if (!("formicidae".equals(family) || "Formicidae".equals(family))) {
            //A.log("transformLine() ignore non-ants:" + family);
            s_notAntCount++;
            return null;
        }

        dataMassaging(line, row);

        if (c <= testNum) {
            debug(line, row);
        }

        return outputColumnOrder
            .stream()
            .map(row::get)
            .collect(Collectors.toList());
    }

    private void dataMassaging(CSVRecord line, Map<String, String> row) {
        // line uses GBIF headers as keys. Row uses Antweb headers as keys.


        String col = "catalogNumber";
        String oldVal = line.get(col);
        String newVal = oldVal.replaceAll("\\p{Punct}", "");
        //A.log("dataMassaging() oldVal:" + oldVal + " newVal:" + newVal);
        row.put("SpecimenCode", newVal);

        String code = newVal; // used for debugging later.
        boolean debug = false; //code.contains("10648");

        // split date collected into two columns
        Pair<String, String> dates = splitDate(line.get("eventDate"));
        row.put("datecollectedstart", dates.getLeft());
        row.put("datecollectedend", dates.getRight());


        // trim eventid: from collecting events
        row.put("collectioncode", StringUtils.removeStart(line.get("fieldNumber"), "eventID:"));

        /*
        String countryL = line.get("country");
        String countyL = line.get("county");
        String adm1L = line.get("stateProvince");
        String typeL = line.get("type");
        String typeStatusL = line.get("typeStatus");
        A.log("code:" + code + " country:" + countryL + " adm1:" + adm1L + " county:" + countyL + " type:" + typeL + " typeStatus:" + typeStatusL);
        */

        String countryStr = row.get("country");
        Country country = GeolocaleMgr.getCountry(countryStr);
        String adm1Str = row.get("adm1");
        Adm1 adm1 = (Adm1) GeolocaleMgr.getAdm1(adm1Str, countryStr);
        // Adm1 may be from country where Antweb does not track adm1.
          //if (adm1 == null) A.log("dataMassaging() Adm1 not found:" + adm1Str);
        // ? Adm2 is already taken care of automatically. No need for checking.
        //String adm2Str = row.get("adm2");
        //A.log("dataMassaging() country:" + countryStr + " adm1:" + adm1Str + " adm2:" + adm2Str);

        //decimalLatitude", "LocLatitude")
        String lat = line.get("decimalLatitude");
        String lon = line.get("decimalLongitude");
        LatLon latLon = new LatLon(lat, lon);
        if (country != null && latLon.isValid()) {
            boolean isWithinCountryBounds = country.isWithinBounds(latLon);
            if (!isWithinCountryBounds) {
                LatLon newBounds = country.correctIntoBounds(latLon);
                if (newBounds != null) {
                    latLon = newBounds;
                    lat = newBounds.getLat();
                    row.put("LocLatitude", lat);
                    lon = newBounds.getLon();
                    row.put("LocLongitude", lon);
                    if (debug) A.log("dataMassaging() lat:" + lat + " lon:" + lon + " withinCountry:" + isWithinCountryBounds + " newBounds:" + newBounds);
                }
            }
        }
        if (debug) A.log("dataMassaging() lat:" + lat + " lon:" + lon + " withinAdm1:" + adm1.isWithinBounds(lat, lon));

        row.put("elevation", buildElevation(
                line.get("minimumElevationInMeters"),
                line.get("maximumElevationInMeters")));

        row.put("ownedby", setOwnedBy(line.get("institutionCode")));

    }

    private void debug(CSVRecord line, Map<String, String> row) {
        /*
         *  subfamily ias not Mapped: line.isMapped("subfamily") always false. Header not exists, and values ignored.
         *  We infer the subfamily from genus.
         */

        String family = row.get("Ffamily");

        String subfamily = row.get("Subfamily");
        if (subfamily == null) {
            s_noSubfamilyCount++;
        }

        String genus = row.get("[Species]Genus");
        if (genus == null) {
            s_noGenusCount++;
        }

        String species = row.get("SpeciesName");
        if (species == null) {
            s_noSpeciesCount++;
        }

        if (AntwebProps.isDevMode()) {
            //A.log("transformLine() family:" + family + " subfamily:" + subfamily + " genus:" + genus + " species:" + species);
        } else {
            //A.iLog("family:" + family + " subfamily:" + subfamily + " genus:" + genus + " species:" + species);
            //if (AntwebProps.isDevMode()) throw new AntwebException("Terminate test");
        }

        //if (c == 1) A.log("dataMassaging() country:" + line.get("country") + " stateProvince" + line.get("stateProvince") + " county:" + line.get("county") + " minElev:" + line.get("minimumElevationInMeters") + " maxElev:" + line.get("maximumElevationInMeters"));
    }


    private void passFieldCheck(CSVRecord line) throws AntwebException {
        // static ArrayList ***
        String[] fieldArray = {"taxonRank", "specificEpithet"   // "CatalogNumber",
                , "eventDate", "fieldNumber"
                , "minimumElevationInMeters", "maximumElevationInMeters"
                , "eventDate", "country"
                , "stateProvince", "county", "institutionCode"};  // "ownedBy",
        int c = 0;
        //A.log("passFieldCheck() fields:" + String.join(", ", fieldArray));
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

}