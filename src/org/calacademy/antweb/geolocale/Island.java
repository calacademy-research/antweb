package org.calacademy.antweb.geolocale;

import java.sql.*;
import java.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.*;

/*
This class is not currently used except for documentation. Maybe some day.

Islands have been an evolving concept with compromises made.

It is treated sometimes as a country though it is sometimes considered (rightly) an adm1.

See SpecimenUploadParse.java:463



*/

public class Island extends Country {

    private static Log s_log = LogFactory.getLog(Island.class);
    
    public Island() {
    }

}