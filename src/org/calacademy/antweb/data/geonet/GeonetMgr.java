package org.calacademy.antweb.data.geonet;

import java.sql.*;
import java.util.*;
import java.io.*;

import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.data.*;
import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;  
  
import com.google.gson.*;
    
public class GeonetMgr {

    private static Log s_log = LogFactory.getLog(GeonetMgr.class);

    public static final String source = "Geonet";
    
/*
For countryies:
    http://geonames.nga.mil/arcgis/rest/services/Research/GeoNames/MapServer/1/query?where=COUNTRY_CD+is+not+NULL&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=COUNTRY_CD%2C+COUNTRY_NM&returnGeometry=true&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=

For Adm1:
    http://geonames.nga.mil/arcgis/rest/services/Research/GeoNames/MapServer/2/query?where=COUNTRY_CD+is+not+NULL&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=COUNTRY_CD%2C+ADM1%2C+ADM1_NAME&returnGeometry=false&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=

Below each form is the data we are looking for. Selecting JSON and clicking Query (Get) gives us the actual json code that our app could request. 
This gives us the equivalence of what we could scrape from http://geonames.nga.mil/gns/html/rest/lookuptables.html but in a better way.
*/


    /*  Get the Geonet centroids
		See if I did this right. Jan 16th. Get geonet centroids
		  Adm1s, only if not empty or contains "null". Where source = "Geonet"  
		  Get Geonet centroids... X, Y.
		  http://geonames.nga.mil/arcgis/rest/services/Research/GeoNames/MapServer/0/query?where=FEATURE_DESIGNATION_CODE+%3D+%27ADM1%27+and+NAME_TYPE_CODE+%3D+%27N%27+&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=FEATURE_COUNTRY_CODE%2C+FEATURE_COUNTRY_NAME%2C+NAME%2C+PRIMARY_ADMIN_DIVISION&returnGeometry=true&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=6&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=&f=html
    */    

    public static String fetchCentroidData(Connection connection) {
      return fetchCentroidData(connection, null);
    }
    
    public static String fetchCentroidData(Connection connection, String country) {
        String message = "";

        LogMgr.emptyLog("geonetAdm1.log");
		GeolocaleDb geolocaleDb = new GeolocaleDb(connection);

        HashMap<String, String> countryHash = GeonetCountryCodes.getCountryHash();
        GeonetAdm1CentroidResponse geonetAdm1CentroidResponse = GeonetAdm1Codes.getAdm1CentroidResponse();
        A.log("fetchCentroidData() response:" + geonetAdm1CentroidResponse);

        int i = 0;
        int cIsNull = 0;
        for (CentroidFeature feature : geonetAdm1CentroidResponse.features) {

          String countryCode = feature.attributes.getCountryCode();          
          String adm1Name = feature.attributes.getName();
          String primaryAdminDivision = feature.attributes.getPrimaryAdminDivision();
          String useAdm1Name = DataPlace.cleanName(adm1Name);
 		  String countryName = countryHash.get(countryCode);
          String lon = feature.geometry.getX();
          String lat = feature.geometry.getY();

          // So that we can run on a single country if we want.
          if (country != null && !country.equals(countryName)) continue;

          A.log("fetchCentroidData()country:" + countryName);

          Geolocale adm1 = GeolocaleMgr.getAdm1(useAdm1Name, countryName);
          if (adm1 != null) {
            ++i;
            if (adm1.getCentroid() == null) {
              ++cIsNull;
              //A.log("fetchData() countryCode:" + countryCode + " adm1Name:" + adm1Name + " primaryAdminDivision:" + primaryAdminDivision + " lat:" + lat + " lon:" + lon);
              //A.log("fetchData() country:" + countryName + " adm1Name:" + adm1.getName() + " centroid:" + adm1.getCentroid() + " lat:" + lat + " lon:" + lon);
              String centroid = lat + ", " + lon;
              adm1.setCentroid(centroid);
              geolocaleDb.updateCentroid(adm1);
            }
          }
        }

        A.log("fetchCentroidData() a:" + GeolocaleDb.a + " b:" + GeolocaleDb.b + " c:" + GeolocaleDb.c);
        message = "Geonet Centroid Data fetched. i:" + i + " cIsNull:" + cIsNull;        
        return message;
    }


    // Geonames.org is used to fetch the bounding box, latitude and longitude for each country. 
    public static String fetchData(Connection connection) {
        String message = "";

        LogMgr.emptyLog("geonetAdm1.log");

		GeolocaleDb geolocaleDb = new GeolocaleDb(connection);
        //geolocaleDb.deleteFetchedAdm1(GeonamesPlace.source);

        TreeMap<String, ArrayList<String>> countryAdm1Map = getAdm1CountryMap();

        //logCountryAdm1Map(countryAdm1Map);
        
        for (String country : countryAdm1Map.keySet()) {
          // loop through each country

          if (DataPlace.skipCountry(country)) continue;
          
          String validName = DataPlace.getValidName(country);
          
          A.log("GeonetMgr.fetchData() country:" + country + " validName:" + validName);
          if (validName != null) {
            // Then we will insert two. The valid will use the validName. The invalid will use country and have a validName of validName.
            processCountry(validName, null, geolocaleDb); // Parent?
            
            processCountry(country, validName, geolocaleDb); // Parent?
          }

          for (String adm1 : countryAdm1Map.get(country)) {
            // loop through each adm1
  
            if (DataPlace.skipAdm1(adm1)) continue;    
            
            geolocaleDb.makeAdm1(adm1, country, null, GeonetMgr.source); // Flickr is null

          }
        }        
/*
        GeonetAdm1Response geonetAdm1Response = GeonetAdm1Codes.getAdm1Codes();
        for (Feature feature: geonetAdm1Response.features) {
            String countryCode = feature.attributes.getCountryCode();
            String adm1Name = feature.attributes.getAdm1Name();
            A.log("feature:" + feature.attributes);
        }  			
*/
      
        message = "Geonet Data fetched.";        
        return message;
    }
    
	private static void processCountry(String name, String validName, GeolocaleDb geolocaleDb) {
	  Geolocale thisCountry = geolocaleDb.getCountry(name);
	  if (thisCountry != null) {
		geolocaleDb.updateGeoData(thisCountry.getId(), null, null, GeonetMgr.source); // validName, FlickrData are null
	  } else {
		geolocaleDb.insertCountry(name, validName, GeonetMgr.source);
	  }
	}
    
    private static void logCountryAdm1Map(TreeMap<String, ArrayList<String>> countryAdm1Map) {
        for (String countryName : countryAdm1Map.keySet()) {
          A.log("country:" + countryName);
          LogMgr.appendLog("geonetAdm1.log", countryName);
          ArrayList<String> adm1List = countryAdm1Map.get(countryName);
          for (String adm1Name : adm1List) {
            A.log("  adm1:" + adm1Name);
            LogMgr.appendLog("geonetAdm1.log", "    " + adm1Name);
          }
        }    
    }

    public static TreeMap<String, ArrayList<String>> getAdm1CountryMap() {

        HashMap<String, String> countryHash = GeonetCountryCodes.getCountryHash();

        TreeMap<String, ArrayList<String>> countryAdm1Map = new TreeMap<String, ArrayList<String>>();

        GeonetAdm1Response geonetAdm1Response = GeonetAdm1Codes.getAdm1Codes();

        for (Feature feature: geonetAdm1Response.features) {

          String countryCode = feature.attributes.getCountryCode();          
          String adm1Name = feature.attributes.getAdm1Name();
          adm1Name = DataPlace.cleanName(adm1Name);
 		  String countryName = countryHash.get(countryCode);
          
          A.log("getAdm1CountryMap() countryCode:" + countryCode + " countryName:" + countryName + " adm1Name:" + adm1Name);          

          ArrayList<String> adm1List = countryAdm1Map.get(countryName);
          if (adm1List == null) {
            // create it.
            adm1List = new ArrayList<String>();
          }
          adm1List.add(adm1Name);
          countryAdm1Map.put(countryName, adm1List);
        }  			

        return countryAdm1Map;
    }
    
    public static TreeMap<String, ArrayList<String>> getAdm1CentroidCountryMap() {

        HashMap<String, String> countryHash = GeonetCountryCodes.getCountryHash();

        TreeMap<String, ArrayList<String>> countryAdm1Map = new TreeMap<String, ArrayList<String>>();

        GeonetAdm1CentroidResponse geonetAdm1CentroidResponse = GeonetAdm1Codes.getAdm1CentroidResponse();

        for (CentroidFeature feature: geonetAdm1CentroidResponse.features) {

          String countryCode = feature.attributes.getCountryCode();          
          String adm1Name = ""; //feature.attributes.getAdm1Name();
          adm1Name = DataPlace.cleanName(adm1Name);
 		  String countryName = countryHash.get(countryCode);
          
          A.log("getAdm1CountryMap() countryCode:" + countryCode + " countryName:" + countryName + " adm1Name:" + adm1Name);          

          ArrayList<String> adm1List = countryAdm1Map.get(countryName);
          if (adm1List == null) {
            // create it.
            adm1List = new ArrayList<String>();
          }
          adm1List.add(adm1Name);
          countryAdm1Map.put(countryName, adm1List);
        }  			

        return countryAdm1Map;
    }    
    
}