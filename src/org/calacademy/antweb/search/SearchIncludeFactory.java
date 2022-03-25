package org.calacademy.antweb.search;

import org.calacademy.antweb.home.*;

import java.sql.*;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class SearchIncludeFactory {

    private static Log s_log = LogFactory.getLog(SearchIncludeFactory.class);    
    private Connection connection;

    public SearchIncludeFactory(Connection connection) {
       this.connection = connection;
    }
    private Connection getConnection() {
      return this.connection;
    }

    public String getBioregionGenInc(String formBioregion) throws SQLException {
    String bioregionGenInc =   
          "<select class='input_150' name='bioregion'>"
        + "<option value=''>Any</a></option>";
      ArrayList<String> bioregions = new SearchDb(connection).getBioregionList();
      for (String bioregion : bioregions) {
        String selected = "";        
        if (bioregion.equalsIgnoreCase(formBioregion)) selected = " selected";
        bioregionGenInc += "<option value='" + bioregion + "'" + selected + ">" + bioregion + "</option>";
      }
      bioregionGenInc += "</select>";
      return bioregionGenInc;
    }

    public String getCountryGenInc() throws SQLException {
      return getCountryGenInc(null);
    }
    public String getCountryGenInc(String formCountry) throws SQLException {
    String countryGenInc =   
          "<select class='input_150' name='country'>"
        + "<option value=''>Any</a></option>";
      ArrayList<String> countries = new SearchDb(connection).getCountryList();
      //A.log("getCountryGenInc() countries:" + countries);      
      for (String country : countries) {
        String selected = "";        
        if (country.equalsIgnoreCase(formCountry)) selected = " selected";
        countryGenInc += "<option value='" + country + "'" + selected + ">" + country + "</option>";
      }
      countryGenInc += "<option value='Port of Entry'>Port of Entry	</option>";
      countryGenInc += "</select>";
      return countryGenInc;
    }

    public String getAdm1GenInc() throws SQLException {
      return getAdm1GenInc(null);
    }
    public String getAdm1GenInc(String formAdm1) throws SQLException {
    String adm1GenInc =   
          "<select class='input_150' name='adm1'>"
        + "<option value=''>Any</a></option>";
      ArrayList<String> adm1s = new SearchDb(connection).getAdm1List();
      for (String adm1 : adm1s) {
        String selected = "";        
        if (adm1.equalsIgnoreCase(formAdm1)) selected = " selected";
        adm1GenInc += "<option value='" + adm1 + "'" + selected + ">" + adm1 + "</option>";
      }
      adm1GenInc += "</select>";
      return adm1GenInc;
    }
}
