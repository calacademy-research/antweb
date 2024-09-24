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


public class Transformer {

    private static final Log s_log = LogFactory.getLog(Transformer.class);

    /**
     * Splits an ISO-8601 date duration (YYYY-MM-DD/YYYY-MM-DD) into two separate date strings
     *
     * @param eventDate The date duration to split
     * @return a Pair of (first date, second date), or (first date, "") if only one date
     */
    protected Pair<String, String> splitDate(String eventDate) {
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
    protected String setAdm2(String verbatimCountry, String adm1, String adm2, String computedCountry, String stateProvince, String county) {
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
    protected static String buildElevation(String minElevation, String maxElevation) {

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
    protected static String setOwnedBy(String ownerRepository) {
        if ("CAS".equals(ownerRepository)) {
            return "CASC";
        }

        return ownerRepository;
    }

}