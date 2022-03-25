package org.calacademy.antweb.home;

import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.Formatter;

public class Adm1LoadDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(Adm1LoadDb.class);

    public Adm1LoadDb(Connection connection) {
      super(connection);
    }

    public void putCountry(String country) {
      country = new Formatter().capitalizeEachWord(country);
      Geolocale geolocale = GeolocaleMgr.getCountry(country);          
      if (geolocale == null || !"country".equals(geolocale.getGeorank())) {
        s_log.warn("putCountry() geolocale not found:" + country);
      }
    }

    public void putAdm1(String adm1, String country, String georankType, String georankTypeLoc) {
        Statement stmt = null;
        String dml = null;
        
        /*
        String properCasedCountry = getCountry(country);
        if (properCasedCountry == null) {
          s_log.warn("country not found:" + country);
        }
        country = properCasedCountry;
        */
        
        country = new Formatter().capitalizeEachWord(country);
        
		Geolocale geolocale = GeolocaleMgr.getCountry(country);          
		if (geolocale == null || !"country".equals(geolocale.getGeorank())) {
		  //A.log("putAdm1() geolocale not found:" + country);
		  return;
		}

        //if ("Sucre".equals(adm1)) A.log("putAdm1() 1 Sucre geolocale:" + geolocale);

		geolocale = GeolocaleMgr.getAdm1(adm1, country);          
		if (geolocale != null && "adm1".equals(geolocale.getGeorank())) {
		  //A.log("putAdm1() adm1 already exists:" + adm1 + " source:" + geolocale.getSource());
		  return;
		}

        //if ("Sucre".equals(adm1)) A.log("putAdm1() 2 Sucre geolocale:" + geolocale);
        
        adm1 = Formatter.escapeQuotes(adm1);
        try {        
            dml = "insert into geolocale (name, georank, georankType, georankTypeLoc, parent, source) values ('" + adm1 + "', 'adm1', '" + georankType + "', '" + georankTypeLoc + "', '" + country + "', 'adm1Load')";
            //A.log("putAdm1() dml:" + dml);
            stmt = DBUtil.getStatement(getConnection(), "putAdm1()");
            int x = stmt.executeUpdate(dml);
        } catch (SQLSyntaxErrorException e) {
            s_log.debug("Adm1LoadDb.putAdm1() failed to insert country:" + country + " adm1:" + adm1);
        } catch (SQLIntegrityConstraintViolationException e) {
            // no problem. Already there.
            //A.log("Adm1LoadDb.putAdm1() exists: " + country + " " + adm1);
        } catch (SQLException e) {
            s_log.error("putAdm1() e:" + e + " dml:" + dml);
        } finally {
            DBUtil.close(stmt, null, "putAdm1()");
        }         
    }
 
     
    public void deleteAdm1LoadedAdm1() {
        Statement stmt = null;

        String dml = null;
        try {
            dml = "delete from geolocale where source = 'adm1Load'";
            s_log.debug("deleteGeolocale() dml:" + dml);

            stmt = DBUtil.getStatement(getConnection(), "Adm1LoadDb.deleteAdm1LoadedAdm1()");
            int x = stmt.executeUpdate(dml);
        } catch (SQLException e) {
            s_log.error("Adm1LoadDb.deleteAdm1LoadedAdm1() e:" + e);
        } finally {
            DBUtil.close(stmt, null, "Adm1LoadDb.deleteAdm1LoadedAdm1()");
        }   
    }   

}

