package org.calacademy.antweb.search;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public final class ResultRank {

    private static Log s_log = LogFactory.getLog(ResultRank.class);

    public static String SUBFAMILY = "subfamily";
    public static String GENUS = "genus";
    public static String SPECIES = "species";
    public static String SPECIMEN = "specimen";    

    // older...
    public static String SPECIES_SPECIFIC = "speciesSpecific";
    public static String BAY_AREA = "bayArea";

    public static boolean isTaxonRank(String resultRank) {
        return SPECIES.equals(resultRank) || GENUS.equals(resultRank) || SUBFAMILY.equals(resultRank);
    }

 
    public ResultRank() {
    }
    
}
