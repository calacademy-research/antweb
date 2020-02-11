package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.Formatter;

public class CountryDb extends AntwebDb {

// Deprecated.  Use GeolocaleDb.

    private static Log s_log = LogFactory.getLog(CountryDb.class);

    public CountryDb(Connection connection) {
      super(connection);
    }

/*
    private static ArrayList<String> countries = new ArrayList<String>();    
    private static ArrayList<String> unCountries = new ArrayList<String>();    
  
    public static boolean isValid(Connection connection, String country) {
      if (countries.size() == 0) {
        populate(connection);
      }

      country = Formatter.initCap(country);
      
      boolean contains = countries.contains(country);
      if (contains) return true;

      contains = unCountries.contains(country);

      A.log("isValid() contains:" + contains + " country:" + country + " unCountries:" + unCountries);
      return contains;
    }

    public static void populate(Connection connection) {
        Statement stmt = null;
        ResultSet rset = null;
        String query = null;
        try {
            stmt = DBUtil.getStatement(connection, "CountryDb.populate()");

            query = "select name, iso_code from country order by name";        
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                String name = rset.getString("name");
                //String description = rset.getString("iso_code");
                countries.add(name);
            }

            query = "select name from un_country order by name";        
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                String name = rset.getString("name");
                unCountries.add(name);
            }
        } catch (SQLException e) {
            s_log.error("populate() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "CountryDb.populate()");
        }
    }

    public static ArrayList<String> getList(Connection connection) throws SQLException {
      if (countries.size() == 0) {
        populate(connection);
      }
      return countries;
    }
    
    public static String toList(Connection connection) throws SQLException {
      if (countries.size() == 0) {
        populate(connection);
      }    
      return countries.toString();
    }


    public static ArrayList<String> getUNList(Connection connection) throws SQLException {
      if (unCountries.size() == 0) {
        populate(connection);
      }
      return unCountries;
    }
    
    public static String toUNList(Connection connection) throws SQLException {
      if (unCountries.size() == 0) {
        populate(connection);
      }    
      return unCountries.toString();
    */
}
