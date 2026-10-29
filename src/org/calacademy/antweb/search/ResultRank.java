package org.calacademy.antweb.search;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Values of the advanced search's "Group by" field (resultRank): subfamily, genus, species and specimen (the
 * default), plus the old speciesSpecific and bayArea; isTaxonRank() is true for the three taxon ranks. Read by
 * SearchAction, the Prepare, Compare, Map and Field Guide actions, ObjectMapDb and UtilDataAction.
 */
public final class ResultRank {

    private static final Log s_log = LogFactory.getLog(ResultRank.class);

    public static final String SUBFAMILY = "subfamily";
    public static final String GENUS = "genus";
    public static final String SPECIES = "species";
    public static final String SPECIMEN = "specimen";

    // older...
    public static final String SPECIES_SPECIFIC = "speciesSpecific";
    public static final String BAY_AREA = "bayArea";

    public static boolean isTaxonRank(String resultRank) {
        return SPECIES.equals(resultRank) || GENUS.equals(resultRank) || SUBFAMILY.equals(resultRank);
    }

 
    public ResultRank() {
    }
    
}
