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


public class GBIFTransformer {

    private static final Log s_log = LogFactory.getLog(GBIFUploader.class);

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
            , Pair.of("county", "county")
            , Pair.of("stateProvince", "adm1")
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

                //if (AntwebProps.isDevMode() && c > testLimit) break;

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

        // Do we have to do this for every line??

        // perform direct header translations
        for (Pair<String, String> pair : directHeaderTranslations) {
            String GBIFName = pair.getLeft();
            String antwebName = pair.getRight();
            try {

                // This weird logic is to work around the missing subfamily in the header of the occurrence.txt file.
                // If it is subfamily, skip it.
                //if ("subfamily".equals(GBIFName)) {
                //    A.iLog("tranformLine() subfamily isMapped:" + line.isMapped("subfamily"));
                //    continue;
                //}

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
        */

        // disabled until GeoBoundaries is added as a data source for TW, generated stateProvince is too inconsistent
        /*if (StringUtils.isEmpty(line.get("TW:DataAttribute:CollectingEvent:adm1"))) {
            String stateProvince = line.get("stateProvince");
            if (StringUtils.isNotEmpty(stateProvince)) {
                row.put("Adm1", stateProvince);
            }
        }*/

        // validate ADM data
        /*
        row.put("Adm2", setAdm2(
                (line.isMapped("TW:DataAttribute:CollectingEvent:Country")) ? line.get("TW:DataAttribute:CollectingEvent:Country") : null,
                (line.isMapped("TW:DataAttribute:CollectingEvent:adm1")) ? line.get("TW:DataAttribute:CollectingEvent:adm1") : null,
                (line.isMapped("TW:DataAttribute:CollectingEvent:adm2")) ? line.get("TW:DataAttribute:CollectingEvent:adm2") : null,
                line.get("country"),
                line.get("stateProvince"),
                line.get("county")));
        */


        String countryStr = row.get("country");
        Country country = GeolocaleMgr.getCountry(countryStr);
        String adm1Str = row.get("adm1");
        Adm1 adm1 = (Adm1) GeolocaleMgr.getAdm1(adm1Str, countryStr);
        //A.log("dataMassaging() country:" + countryStr + " adm1:" + adm1Str);


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
            A.log("transformLine() family:" + family + " subfamily:" + subfamily + " genus:" + genus + " species:" + species);
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
/*
    private String setAdm2(String verbatimCountry, String adm1, String adm2, String computedCountry, String stateProvince, String county) {
        if (StringUtils.equals(verbatimCountry, computedCountry)
                && StringUtils.equals(adm1, stateProvince)
                && StringUtils.isBlank(adm2)
                && StringUtils.isNotBlank(county)) {
            return county;
        }
        return adm2;
    }
*/

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
     * <p>
     * Currently, only replaces CAS with CASC.
     */
    private static String setOwnedBy(String ownerRepository) {
        if ("CAS".equals(ownerRepository)) {
            return "CASC";
        }

        return ownerRepository;
    }

    /*
    TaxonWorks headers

id	basisOfRecord	occurrenceID	catalogNumber	otherCatalogNumbers	individualCount	preparations	country
stateProvince	county	eventDate	year	month	day	startDayOfYear	endDayOfYear	fieldNumber
maximumElevationInMeters	minimumElevationInMeters	samplingProtocol	habitat	verbatimElevation
verbatimEventDate	verbatimLocality	identifiedBy	identifiedByID	dateIdentified	nomenclaturalCode	kingdom
phylum	class	order	higherClassification	family	subfamily	tribe	genus	specificEpithet	infraspecificEpithet
scientificName	scientificNameAuthorship	taxonRank	previousIdentifications	typeStatus	institutionCode
institutionID	recordedBy	recordedByID	verbatimCoordinates	verbatimLatitude	verbatimLongitude	decimalLatitude
decimalLongitude	footprintWKT	coordinateUncertaintyInMeters	geodeticDatum	georeferenceProtocol
georeferenceRemarks	georeferenceSources	georeferencedBy	georeferencedDate	occurrenceStatus	occurrenceRemarks
verbatimLabel	TW:DataAttribute:CollectionObject:DatePrepared	TW:DataAttribute:CollectionObject:LifeStageSex
TW:DataAttribute:CollectionObject:LocatedAt	TW:DataAttribute:CollectionObject:MolProjectNotes
TW:DataAttribute:CollectionObject:PreparedBy	TW:DataAttribute:CollectionObject:SpeciesGroup
TW:DataAttribute:CollectionObject:SpecimenNotes	TW:DataAttribute:CollectionObject:VerbatimTypeStatus
TW:DataAttribute:CollectingEvent:adm1	TW:DataAttribute:CollectingEvent:adm2
TW:DataAttribute:CollectingEvent:BiogeographicRegion	TW:DataAttribute:CollectingEvent:CollectionNotes
TW:DataAttribute:CollectingEvent:Country	TW:DataAttribute:CollectingEvent:LocalityCode
TW:DataAttribute:CollectingEvent:LocalityNotes	TW:DataAttribute:CollectingEvent:Microhabitat
TW:DataAttribute:CollectingEvent:VerbatimCollectionCode	TW:DataAttribute:CollectingEvent:VerbatimCoordinateUncertainty
TW:Internal:otu_name	TW:Internal:collecting_event_id	TW:Internal:elevation_precision	TW:Internal:collection_object_id
     */

    /*
Antweb Specimen Upload headers

SpecimenCode	DeterminedBy	DateDetermined	LifeStageSex	Medium	PreparedBy	DatePrepared	LocatedAt
TypeStatus	SpecimenNotes	DNANotes	CollectedBy	Habitat	Microhabitat	Method	CollXYAccuracy	LocalityName
LocalityCode	Adm2	Adm1	Country	LocLatitude	LocLongitude	BiogeographicRegion	LocalityNotes
ElevationMaxError	[Species]Genus	SpeciesName	Subspecies	SpeciesGroup	Subfamily	Family	Order	Class	Phylum
Kingdom	LocXYAccuracy	taxonworks_co_id	datecollectedstart	datecollectedend	elevation	ElevationMaxError
ownedby	collectioncode


Occurrence.txt headers

id	type	modified	language
license	accessRights	references	institutionID	collectionID	institutionCode
collectionCode	basisOfRecord	informationWithheld	dynamicProperties	occurrenceID	catalogNumber	recordNumber
recordedBy	individualCount	sex	lifeStage	establishmentMeans	georeferenceVerificationStatus	associatedMedia
associatedOccurrences	associatedTaxa	otherCatalogNumbers	occurrenceRemarks	organismID	previousIdentifications
preparations	associatedSequences	fieldNumber	eventDate	eventTime	endDayOfYear	year	month	day
verbatimEventDate	habitat	samplingProtocol	eventRemarks	higherGeography	continent	waterBody	islandGroup
island	country	stateProvince	county	locality	verbatimLocality	minimumElevationInMeters
maximumElevationInMeters	minimumDepthInMeters	maximumDepthInMeters	locationAccordingTo	locationRemarks
decimalLatitude	decimalLongitude	geodeticDatum	coordinateUncertaintyInMeters	verbatimCoordinates
verbatimCoordinateSystem	footprintWKT	georeferencedBy	georeferencedDate	georeferenceProtocol
georeferenceSources	earliestEonOrLowestEonothem	earliestEraOrLowestErathem	earliestPeriodOrLowestSystem
earliestEpochOrLowestSeries	earliestAgeOrLowestStage	group	formation	member	identificationQualifier	typeStatus
identifiedBy	dateIdentified	identificationReferences	identificationVerificationStatus	identificationRemarks
scientificNameID	scientificName	higherClassification	kingdom	phylum	class	order	family	genus
specificEpithet	infraspecificEpithet
taxonRank	nomenclaturalCode


Sample Occurrence.txt record:

http://arctos.database.museum/guid/UTEP:Ento:24305?seid=4797616	PhysicalObject	2024-07-17 15:32:00.015143	en
http://vertnet.org/resources/norms.html	http://arctos.database.museum/guid/UTEP:Ento:24305	UTEP	https://arctos.database.museum/collection/UTEP:Ento	UTEP	Ento	PreservedSpecimen
http://arctos.database.museum/guid/UTEP:Ento:24305?seid=4797616	UTEP:Ento:24305	Mackay 20936
Collector(s): William P. Mackay, Emma Mackay				wild
[{"remarks": null, "issued_by": null, "identifier": "Mackay 20936", "assigned_by": "unknown", "assigned_date": "2022-04-27", "identifier_type": "collector number"}]
http://arctos.database.museum/guid/UTEP:Ento:24305
[{"idby": "unknown", "made_date": null, "concept_label": null, "short_citation": null, "scientific_name": "Odontomachus", "sensu_publication": null, "identification_taxa": [{"taxon": {"ftn": "Animalia, Arthropoda, Insecta, Hymenoptera, Apocrita, Formicidae, Ponerinae, Ponerini, Odontomachus", "name": "Odontomachus", "ctrms": [{"psn": 1, "typ": "kingdom", "term": "Animalia"}, {"psn": 2, "typ": "phylum", "term": "Arthropoda"}, {"psn": 3, "typ": "class", "term": "Insecta"}, {"psn": 4, "typ": "order", "term": "Hymenoptera"}, {"psn": 5, "typ": "suborder", "term": "Apocrita"}, {"psn": 6, "typ": "family", "term": "Formicidae"}, {"psn": 7, "typ": "subfamily", "term": "Ponerinae"}, {"psn": 8, "typ": "tribe", "term": "Ponerini"}, {"psn": 9, "typ": "genus", "term": "Odontomachus"}], "nctrms": [{"typ": "author_text", "term": "Latreille, 1804"}, {"typ": "display_name", "term": "<i>Odontomachus</i> Latreille, 1804"}, {"typ": "nomenclatural_code", "term": "ICZN"}, {"typ": "remark", "term": "Imported from ITIS 6 Feb 2007"}, {"typ": "scientific_name", "term": "Odontomachus"}, {"typ": "source_authority", "term": "ITIS"}, {"typ": "taxon_status", "term": "valid"}], "source": "Arctos", "classification_id": "https://arctos.database.museum/name/Odontomachus#Arctos"}, "taxon_id": "https://arctos.database.museum/name/Odontomachus", "variable": "A"}], "identification_order": 1, "identification_agents": [{"agent_name": "unknown", "agent_identifier": "https://arctos.database.museum/agent/0", "identifier_order": 1}], "identification_remarks": null, "identification_attributes": [{"agent_name": null, "attribute_type": "nature of identification", "attribute_units": null, "attribute_value": "features", "determined_date": null, "agent_identifier": null, "attribute_remark": null, "determination_method": null}]}]
whole organism (pinned)			2004-08-06	2004-08-06	219	2004	08	06	2004-08-06				Costa Rica, Heredia
Costa Rica	Heredia		Estacion Biologica La Selva, Sarapiqui	Costa Rica, Heredia, Sarapiqui, Estacion Biologica La Selva
William P. Mackay	Added Canton, Is "Sarapiqui"	10.4333333333	-84.0166666667	unknown		10d 26m N/84d 1m W	degrees dec. minutes
POLYGON((-84.01666210458322 10.433333290673202,-84.0166622006444 10.433332410464319,-84.01666246833614 10.433331565719298,-84.01666289737119 10.433330788901218,-84.01666347126195 10.433330109862762,-84.01666416795415 10.433329554698997,-84.01666496067428 10.433329144744551,-84.01666581895856 10.433328895753748,-84.0166667098236 10.433328817295157,-84.01666759903395 10.433328912383908,-84.01666845241779 10.433329177365787,-84.01666923718003 10.433329602057693,-84.0166699231627 10.433330170138952,-84.01667048400388 10.433330859778525,-84.01667089815076 10.433331644473945,-84.01667114968788 10.433332494069813,-84.01667122894884 10.433333375916632,-84.01667113288767 10.433334256125525,-84.01667086519595 10.433335100870558,-84.01667043616092 10.43333587768865,-84.01666986227013 10.433336556727122,-84.01666916557794 10.433337111890898,-84.01666837285778 10.433337521845349,-84.01666751457348 10.433337770836152,-84.01666662370842 10.433337849294738,-84.01666573449803 10.433337754205976,-84.01666488111418 10.433337489224087,-84.01666409635193 10.433337064532166,-84.01666341036926 10.433336496450893,-84.0166628495281 10.43333580681131,-84.01666243538124 10.433335022115882,-84.01666218384413 10.433334172520013,-84.01666210458322 10.433333290673202))
William P. Mackay	2004-08-06 00:00:00	not recorded	collector's notes									A		unknown
Odontomachus	Animalia, Arthropoda, Insecta, Hymenoptera, Apocrita, Formicidae, Ponerinae, Ponerini,	Animalia	Arthropoda	Insecta	Hymenoptera	Formicidae	Odontomachus
genus	ICZN


     */


    /*
    // these are just extra headers that we might use, right now the CSV parser doesn't check for header column presence
    // in the future, we could perform more strict checking that certain headers exist.
    private final String[] extraInputHeaders = {
              "fieldNumber"  // must have 'eventID:' prefix removed
            , "country"
            , "stateProvince"
            , "county"
            , "rank"
            // , "subfamily"   // Seems to have been omitted from dwca-v1.85.

    };
*/

}