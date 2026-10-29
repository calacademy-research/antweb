package org.calacademy.antweb.search;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Values of the advanced search's output field: list (the default), mapSpecimen and mapLocality. Read by
 * SearchAction, MapResultsAction and the callers of AdvancedSearchAction.getGoogleMap (ObjectMapDb,
 * UtilDataAction).
 */
public final class Output {

    private static final Log s_log = LogFactory.getLog(Output.class);

    public static final String LIST = "list";
    public static final String MAP_SPECIMEN = "mapSpecimen";
    public static final String MAP_LOCALITY = "mapLocality";

    public Output() {
    }
    
}
