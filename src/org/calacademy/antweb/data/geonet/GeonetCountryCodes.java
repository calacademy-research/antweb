package org.calacademy.antweb.data.geonet;

import java.sql.*;
import java.util.*;
import java.io.*;

import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.data.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;  
  
import com.google.gson.*;
    
public class GeonetCountryCodes {

    private static Log s_log = LogFactory.getLog(GeonetCountryCodes.class);

    public static final String source = "Geonet";
    
    private String name;
    private String geonameId;
    private String latitude;
    private String longitude;
    private String boundingBox;
    private String _content;

/*
For countryies:
    http://geonames.nga.mil/arcgis/rest/services/Research/GeoNames/MapServer/1/query?where=COUNTRY_CD+is+not+NULL&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=COUNTRY_CD%2C+COUNTRY_NM&returnGeometry=true&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=

For Adm1:
    http://geonames.nga.mil/arcgis/rest/services/Research/GeoNames/MapServer/2/query?where=COUNTRY_CD+is+not+NULL&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=COUNTRY_CD%2C+ADM1%2C+ADM1_NAME&returnGeometry=false&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=

Below each form is the data we are looking for. Selecting JSON and clicking Query (Get) gives us the actual json code that our app could request. This gives us the equivalence of what we could scrape from http://geonames.nga.mil/gns/html/rest/lookuptables.html but in a better way.
*/

    // Geonames.org is used to fetch the bounding box, latitude and longitude for each country. 
    public static String fetchData(Connection connection) {
        String message = "";

        LogMgr.emptyLog("geonet.log");

		GeolocaleDb geolocaleDb = new GeolocaleDb(connection);
        //geolocaleDb.deleteFetchedAdm1(GeonamesPlace.source);

        GeonetCountryResponse geonetCountryResponse = getCountryCodes();
          
        String notFound = "";
          			
        for (Feature feature: geonetCountryResponse.features) {
          
          String countryCode = feature.attributes.getCountryCode();
          String countryName = feature.attributes.getCountryName();
          
          Geolocale country = geolocaleDb.getCountry(countryName);

          
          if (country == null) {
           A.log("a");
            notFound += countryName + ", ";
          }
          
          A.log("feature:" + feature.attributes + " cc:" + countryCode + " name:" + countryName + " country:" + countryName);
        }  			

        A.log("fetchCountryData() notFound:" + notFound);
          
        //A.log("fetchData(1) cleanCount:" + s_cleanCount + " FoundCleanNameInFlickr:" + s_cleanedFlickrNameCount);
        A.log("fetchCountryData() geonetResponse:" + geonetCountryResponse);
        message = "Geonet Country Codes fetched.";        
        return message;
    }

    public static HashMap<String, String> getCountryHash() {

        HashMap<String, String> countryHash = new HashMap<String, String>();

        GeonetCountryResponse geonetCountryResponse = getCountryCodes();
          
        for (Feature feature: geonetCountryResponse.features) {
          
          String countryCode = feature.attributes.getCountryCode();
          String countryName = useCountryName(feature.attributes.getCountryName());

          countryHash.put(countryCode, countryName);
        }  			

        return countryHash;
    }

    private static String useCountryName(String countryName) {
      // These names come from Geonames but we prefer to use existing ones.
      if ("Democratic Republic of the Congo".equals(countryName)) return "Democratic Republic of Congo";
      //if ("Kosovo".equals(countryName)) return "";
      if ("Wallis and Futuna".equals(countryName)) return "Wallis and Futuna Islands";
      if ("Bahamas, The".equals(countryName)) return "Bahamas";
      if ("Côte d’Ivoire".equals(countryName)) return "Ivory Coast";
      if ("Czechia".equals(countryName)) return "Czech Republic";
      if ("Gambia, The".equals(countryName)) return "Gambia";
      return countryName;
    }

    public static GeonetCountryResponse getCountryCodes() {
        GeonetCountryResponse geonetCountryResponse = null;
           
        String url = "http://geonames.nga.mil/arcgis/rest/services/Research/GeoNames/MapServer/1/query?where=COUNTRY_CD+is+not+NULL&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=COUNTRY_CD%2C+COUNTRY_NM&returnGeometry=true&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=&f=pjson";
        String json = HttpUtil.getJson(url);
        if (json == null) {
          if (A.dev()) s_log.warn("Failed to get Json from url:" + url);
          return null;
        }

		try {
			geonetCountryResponse = new Gson().fromJson(json, GeonetCountryResponse.class);

			if (false && AntwebProps.isDevMode()) {
			  s_log.warn("GeonamesPlace.getPlace() json:" + json + " response:" + geonetCountryResponse);
	          AntwebUtil.logShortStackTrace();
			}
            if (geonetCountryResponse == null) {
               A.log("GeonetPlace.getPlace() geonamesResponse is null. json:" + json);
               return null;
            }

		} catch (com.google.gson.JsonSyntaxException e) {
		  s_log.warn("getPlaces() e:" + e);
		} 
        return geonetCountryResponse;
    }    
}


