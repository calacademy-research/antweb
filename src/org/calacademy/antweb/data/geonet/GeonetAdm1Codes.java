package org.calacademy.antweb.data.geonet;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;  
  
import com.google.gson.*;
    
public class GeonetAdm1Codes {

    private static Log s_log = LogFactory.getLog(GeonetAdm1Codes.class);

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

    public static GeonetAdm1Response getAdm1Codes() {
        GeonetAdm1Response geonetAdm1Response = null;
           
        String url = "http://geonames.nga.mil/arcgis/rest/services/Research/GeoNames/MapServer/2/query?where=COUNTRY_CD+is+not+NULL&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=COUNTRY_CD%2C+ADM1%2C+ADM1_NAME&returnGeometry=false&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=&f=pjson";
        String json = HttpUtil.getJson(url);
        if (json == null) {
          if (A.dev()) s_log.warn("Failed to get Json from url:" + url);
          return null;
        }

		try {
			geonetAdm1Response = new Gson().fromJson(json, GeonetAdm1Response.class);

			if (false && AntwebProps.isDevMode()) {
			  s_log.warn("GeonamesPlace.getPlace() json:" + json + " response:" + geonetAdm1Response);
	          AntwebUtil.logShortStackTrace();
			}
            if (geonetAdm1Response == null) {
               s_log.debug("GeonetPlace.getPlace() geonamesResponse is null. json:" + json);
               return null;
            }

		} catch (com.google.gson.JsonSyntaxException e) {
		  s_log.warn("getPlaces() e:" + e);
		} 
        return geonetAdm1Response;
    }    

    public static GeonetAdm1CentroidResponse getAdm1CentroidResponse() {
        GeonetAdm1CentroidResponse geonetAdm1CentroidResponse = null;
           
        //String url = "http://geonames.nga.mil/arcgis/rest/services/Research/GeoNames/MapServer/2/query?where=COUNTRY_CD+is+not+NULL&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=COUNTRY_CD%2C+ADM1%2C+ADM1_NAME&returnGeometry=false&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=&f=pjson";
        String url = "http://geonames.nga.mil/arcgis/rest/services/Research/GeoNames/MapServer/0/query?where=FEATURE_DESIGNATION_CODE+%3D+%27ADM1%27+and+NAME_TYPE_CODE+%3D+%27N%27+&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=FEATURE_COUNTRY_CODE%2C+FEATURE_COUNTRY_NAME%2C+NAME%2C+PRIMARY_ADMIN_DIVISION&returnGeometry=true&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=6&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=&f=pjson";
        String json = HttpUtil.getJson(url);
        if (json == null) {
          if (A.dev()) s_log.warn("Failed to get Json from url:" + url);
          return null;
        }

		try {
			geonetAdm1CentroidResponse = new Gson().fromJson(json, GeonetAdm1CentroidResponse.class);

			if (false && AntwebProps.isDevMode()) {
			  s_log.warn("GeonamesPlace.getPlace() json:" + json + " response:" + geonetAdm1CentroidResponse);
	          AntwebUtil.logShortStackTrace();
			}
            if (geonetAdm1CentroidResponse == null) {
               s_log.debug("GeonetPlace.getPlace() geonetAdm1CentroidResponse is null. json:" + json);
               return null;
            }

		} catch (com.google.gson.JsonSyntaxException e) {
		  s_log.warn("getPlaces() e:" + e);
		} 
        return geonetAdm1CentroidResponse;
    }    

}





