package org.calacademy.antweb.search;

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
        
import org.calacademy.antweb.util.*;

public final class Output {

    private static Log s_log = LogFactory.getLog(Output.class);

    public static String LIST = "list";
    public static String MAP_SPECIMEN = "mapSpecimen";
    public static String MAP_LOCALITY = "mapLocality"; 

    public Output() {
    }
    
}
