package org.calacademy.antweb.home;
    

import java.sql.*;
import java.util.*;
    
import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;

public class SearchDb {
    private static Log s_log = LogFactory.getLog(SearchDb.class);        

    private Connection connection = null;
    
    public SearchDb(Connection connection) {
      this.connection = connection;
    }
    private Connection getConnection() {
      return this.connection;
    }
    
    public ArrayList<String> getBioregionList() throws SQLException {
      ArrayList<String> bioregions = new ArrayList<>();
      Statement stmt = null;
      ResultSet rset = null;
      try { 
        String theQuery = "select name from bioregion";
        stmt = DBUtil.getStatement(connection, "getBioregionList()");
        rset = stmt.executeQuery(theQuery);
        while (rset.next()) {
          bioregions.add(rset.getString("name"));
        }
      } finally {
        DBUtil.close(stmt, rset, "getBioregionList()");        
      }
      return bioregions;
    }

    public ArrayList<String> getCountryList() throws SQLException {
      ArrayList<String> countries = new ArrayList<>();

      ArrayList<Geolocale> geolocales = GeolocaleMgr.getValidCountries();
      for (Geolocale country : geolocales) {
        countries.add(country.getName());   
        Collections.sort(countries);   
      }
      return countries;
    }

    public ArrayList<String> getAdm1List() throws SQLException {    
      ArrayList<String> adm1s = new ArrayList<>();
      
      ArrayList<Geolocale> geolocales = GeolocaleMgr.getAdm1s();
      for (Geolocale adm1 : geolocales) {
        adm1s.add(adm1.getName());
      }

      // remove duplicates
        Set<String> set = new HashSet<>(adm1s);
	  adm1s.clear();
	  adm1s.addAll(set);

      Collections.sort(adm1s);

      return adm1s;
    }

}    
