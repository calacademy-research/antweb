package org.calacademy.antweb.data;

import java.io.*;

import org.apache.regexp.*;

import org.apache.struts.action.Action;

import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public final class Adm1LoadAction extends Action {

    private static Log s_log = LogFactory.getLog(Adm1LoadAction.class);

    private static final Log s_antwebEventLog = LogFactory.getLog("adm1Load");
 
    
// ---------------------------------------------------------------------------------------     


    /*
       Primary entry point.  This will load from a file into Antwiki_taxon_country.
       On curate page, Upload Data File.  Submit.
       Upload of a file with title containing "Regional_Taxon_List" in the fileName.  
       This file comes from Antwiki.  Added to source tree, see: /web/data/
       WARNING!  Must open the file in Textwrangler, copy all, past in new file and save.
       This file is binary and will not work unless massaged as such.
    */
    public String loadList(BufferedReader in, Connection connection) 
      throws IOException, SQLException {
        String messageStr = "";
      
//        AntwikiTaxonCountryDb antwikiTaxonCountryDb = new AntwikiTaxonCountryDb(connection);
        //A.log("AntwikiDataAction.loadRegionalTaxonList()");
//        antwikiTaxonCountryDb.emptyTaxonCountry();
        
        String message = load(in, connection);
        messageStr += message;

        return messageStr;
    }

    private String load(BufferedReader in, Connection connection) 
      throws IOException, SQLException {        

if (true) return "Functionality down. Investigate Adm1LoadAction.java";
/*
This functionality is disabled pending fix of georankType. This field of geolocale table
(without an underscore) is not used elsewhere. It is currently NULL in the database.
We have added a new georank_type to support our fetches from Google Api (It adds Province
in some cases and may be overridden on the Edit Geolocale page). The two functionalities
should be able to be used together. If we end up overriding that geolocale type, for 
instance to support country islands, may need to consider this funcitonality.
Not sure if this functionality here is being used.
*/

        String messageStr = null;
        String line = "";
        int validTaxonCount = 0;
        int notValidTaxonCount = 0;
        int notFoundCount = 0;
        
        Adm1LoadDb adm1LoadDb = new Adm1LoadDb(connection);
        
        adm1LoadDb.deleteAdm1LoadedAdm1();
        
        try {
            RE tab = new RE("\t");

            String[] components;
            StringBuffer content = new StringBuffer();

            int lineNum = 1;
            int countryCount = 0;
            int adm1Count = 0;
            String country = "";
            String adm1 = "";
            while (line != null) {            
              line = in.readLine();
              if (line != null) line = line.trim();

              if (line == null || "".equals(line) || " ".equals(line)) {
                //s_log.warn("loadRegionalTaxonList() 1 line:" + line + ":");
                continue;
              }
              ++lineNum;

              line = line.substring(13);

              int delimIndex = line.indexOf("__");
 			  String georankTypeStr = line.substring(0, delimIndex);
              String georankType = null;
              String georankTypeLoc = null;

              if (georankTypeStr.contains("_")) {
                int georankTypeStrI = georankTypeStr.indexOf("_");
                georankType = georankTypeStr.substring(0, georankTypeStr.indexOf("_"));
                georankTypeLoc = georankTypeStr.substring(georankTypeStr.indexOf("_") + 1);
              } else {
                georankType = georankTypeStr;
              }
              
              String geolocaleName = null;
              int geolocaleNameInt = line.indexOf("___") + 3;
              int geolocaleNameEndInt = line.indexOf("__", geolocaleNameInt);   
              if (geolocaleNameInt > 0) {             

                if (geolocaleNameInt < 0 || geolocaleNameEndInt < 0) {
                  String logLine = "lineNum:" + lineNum + " line:" + line + " i1:" + geolocaleNameInt + " i2:" + geolocaleNameEndInt + " SKIPPING";
                  A.log("Adm1LoadAction.load() " + logLine);
                  LogMgr.appendLog("adm1Load.txt", logLine); 
                  continue;
                } else {
                  geolocaleName = line.substring(geolocaleNameInt, geolocaleNameEndInt);
                }
              } else {
                geolocaleName = line.substring(geolocaleNameInt);
              }            

              geolocaleName = geolocaleName.replace("_", "");

              if ("country".equals(georankType)) {
                country = geolocaleName;
                ++countryCount;
                String logLine = "  lineNum:" + lineNum + " Country:" + country;
                //A.log("Adm1LoadAction.load() " + logLine);
                LogMgr.appendLog("adm1Load.txt", logLine); 
                adm1LoadDb.putCountry(country);
              } else {
                adm1 = geolocaleName;
                ++adm1Count;
                String logLine = "    lineNum:" + lineNum + " geolocaleName:" + geolocaleName + " georankType:" + georankType + " georankTypeLoc:" + georankTypeLoc;
                //A.log("Adm1LoadAction.load() " + logLine);
                LogMgr.appendLog("adm1Load.txt", logLine);
                adm1LoadDb.putAdm1(adm1, country, georankType, georankTypeLoc);
              }

              //A.log("Adm1LoadAction.load() line:" + line);
              //A.log("   lineNum:" + lineNum + " georankType:" + georankType + " georankTypeLoc:" + georankTypeLoc + " geolocaleName:" + geolocaleName + " country:" + country);

            } // end while loop through lines

            messageStr = "<h3>Adm1 Load List</h3><br><br>" 
              + "lineCount:" + lineNum + " countryCount:" + countryCount + " adm1Count:" + adm1Count
              + "<br><br>";

        } catch (RESyntaxException e) {
          s_log.warn("loadRegionalTaxonList() e:" + e);
        }
        return messageStr;
    }
	
}
