package org.calacademy.antweb.data;

import java.sql.*;
import java.util.*;
import java.io.*;

import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;  
  
import com.google.gson.*;
    
public class GeonamesPlace extends DataPlace {

    private static Log s_log = LogFactory.getLog(GeonamesPlace.class);

    public static final String source = "Geonames";
    
    private String name;
    private String geonameId;
    private String latitude;
    private String longitude;
    private String boundingBox;
    private String _content;

/*

*/

    // Geonames.org is used to fetch the bounding box, latitude and longitude for each country. 
    public static String fetchData(Connection connection) {
        String message = "";

        LogMgr.emptyLog("geonames.log");
        LogMgr.emptyLog("DataPlaceCase.txt");

		GeolocaleDb geolocaleDb = new GeolocaleDb(connection);
        //geolocaleDb.deleteFetchedAdm1(GeonamesPlace.source);
      
        // Fetch country data
        //ArrayList<Geolocale> countries = GeolocaleMgr.getCountries();
        ArrayList<Country> countries = geolocaleDb.getCountries();
        for (Country country : countries) {
            String countryName = country.getName();
                        
            String fetchMsg = GeonamesPlace.fetchData(connection, countryName);
            s_log.debug("fetchData() message:" + fetchMsg);
          }		
          			
        s_log.debug("fetchData(1) cleanCount:" + s_cleanCount + " FoundCleanNameInFlickr:" + s_cleanedFlickrNameCount);
        message = "Geonames data fetched.";        
        return message;
    }
        

    public static String fetchData(Connection connection, String countryName) {
        String message = "";

        try {
			GeolocaleDb geolocaleDb = new GeolocaleDb(connection);
			//geolocaleDb.deleteFetchedAdm1(country, GeonamesPlace.source);

			//Geolocale country = GeolocaleMgr.getAnyCountry(countryName);
			Geolocale country = geolocaleDb.getGeolocale(countryName, Georank.COUNTRY);      
			if (country == null) {
			  s_log.warn("fetchData(2) country:" + countryName + " not found.");
			  return "fetchData failed for country:" + country + ".";
			}

			GeonamesPlace countryPlace = GeonamesPlace.getPlace(countryName);

			s_log.debug("fetchData(2) countryName:" + countryName + " countryPlace:" + countryPlace);

			String prefix = "  ";
			if (country.getIsValid()) prefix = "v ";
			if (countryPlace.getName() == null || "null".equals(countryPlace.getName())) {
				LogMgr.appendLog("geonames.log", prefix + countryName + " not found.");
			} else {
				LogMgr.appendLog("geonames.log", prefix + countryPlace.getName());
			}

			List<GeonamesPlace> children = null;
			if (countryPlace.geonameId != null) {
			  children = GeonamesPlace.getChildren(countryPlace.geonameId);
            } else {
              s_log.warn("fetchData(2) country:" + country + " has a null geonameId. Why?");
            }

            if (children == null) {
              s_log.debug("GeonamesPlace.fetchData(2) no children found for " + countryName);
              return null;			
			}
			LogMgr.appendLog("geonames.log", "" + countryName);
		  
			//A.log("FlickrData() children:" + children);
			for (GeonamesPlace child : children) {
		
			  processAdm1(child.getName(), countryPlace, geolocaleDb); 
			}
			message = "Geonames data fetched for " + countryName + ".";
        } catch (AntwebException e) {
            s_log.error("fetchData(2) country:" + countryName + " e:" + e);
            AntwebUtil.logShortStackTrace(e);
            DBUtil.rollback(connection); 
        }

        s_log.debug("fetchData(2) cleanCount:" + s_cleanCount + " FoundCleanNameInFlickr:" + s_cleanedFlickrNameCount);
        return message;
    }

	private static int s_cleanedFlickrNameCount = 0;
    private static int s_cleanCount = 0;

    private static void processAdm1(String adm1Name, GeonamesPlace countryPlace, GeolocaleDb geolocaleDb)
      throws AntwebException {

      String cleanAdm1Name = DataPlace.cleanName(adm1Name);

      if (!adm1Name.equals(cleanAdm1Name)) {
        s_log.debug("geoname:" + adm1Name + " cleanName:" + cleanAdm1Name + " for countryPlace:" + countryPlace.getName());
        s_cleanCount++;
      }
      
	  FlickrPlace flickrPlace = null;
	  //flickrPlace = FlickrPlace.getPlace(adm1Name, countryPlace.name);

      // If we didn't find a FlickrPlace with the adm1Name, then try to find one with the cleaned name.      
 /*
 // Never works. Don't bother.
      if (!adm1Name.equals(cleanAdm1Name)) {
        if (flickrPlace == null) {
            flickrPlace = FlickrPlace.getPlace(cleanAdm1Name, countryPlace.name);
			if (flickrPlace != null) {
			  s_cleanedFlickrNameCount++;
			  A.log("processAdm1() WE FOUND ONE adm1Name:" + adm1Name + " cleanedFlickrNameCount:" + s_cleanedFlickrNameCount);
			}        
        }
      }
	  String boundingBox = " boundingBox:";
	  if (flickrPlace != null) {
		boundingBox += flickrPlace.getBoundingBox();
	  }
*/
	  
      //A.log("GeonamesPlace.processAdm1() adm1:" + adm1Name + " cleanName:" + cleanAdm1Name + ", " + countryPlace.name + boundingBox);
	  LogMgr.appendLog("geonames.log", "    " + cleanAdm1Name); // + boundingBox);

	  //A.log("fetchData() id:" + country.getId() + " name:" + countryName + " box:" + countryPlace.getBoundingBox() + " lat:" + countryPlace.getLatitude() + " lon:" + countryPlace.getLongitude());    
	  geolocaleDb.makeAdm1(cleanAdm1Name, countryPlace.getName(), flickrPlace, GeonamesPlace.source);

	}
    
    public static GeonamesPlace getPlace(String placeName) {
        GeonamesPlace geonamesPlace = new GeonamesPlace();
        
        String url = GeonamesPlace.getCountryUrl(placeName);
        String json = GeonamesPlace.getJson(url);
        if (json == null) {
          if (A.dev()) s_log.warn("Failed to get Json from url:" + url);
          return null;
        }
        
		try {
			GeonamesResponse geonamesResponse = new Gson().fromJson(json, GeonamesResponse.class);

			if (false && AntwebProps.isDevMode()) {
			  s_log.warn("GeonamesPlace.getPlace() placeName:" + placeName + " url:" + url + " json:" + json + " response:" + geonamesResponse);
	          AntwebUtil.logShortStackTrace();
			}
            if (geonamesResponse == null) {
               s_log.debug("GeonamesPlace.getPlace() geonamesResponse is null. json:" + json);
               return null;
            }
            if (geonamesResponse.geonames == null) {
               s_log.debug("GeonamesPlace.getPlace() geonames is null. geonamesResponse:" + geonamesResponse + " json:" + json + " url:" + url);
			   return null;
			}
			
			List<Geoname> geonamesList = geonamesResponse.geonames;
            boolean found = false;
			for (Geoname geoname : geonamesList) {
              // Loop through all the places. Find the best fit.
              
              //A.log("GeonamesPlace.getPlace() name:" + geoname.name + " placeName:" + placeName + " id:" + geoname.geonameId); //" cleanName:" + DataPlace.cleanName(geoname.name));   

              if (placeName.equals(geoname.name) || placeName.equals(Formatter.stripAccents(geoname.name))) {

                found = true;
                s_log.debug("GeonamesPlace.getPlace() country:" + placeName + " name:" + geoname.name); // + " json:" + json);
				geonamesPlace.name = placeName;
				geonamesPlace.geonameId = geoname.geonameId;
				geonamesPlace.latitude = geoname.lat;
				geonamesPlace.longitude = geoname.lng;
				break;
              }
			}
            //if (!found) A.log("GeonamesPlace.getPlace() not found. placeName:" + placeName + " place:" + placeList + " json:" + json);

		} catch (com.google.gson.JsonSyntaxException e) {
		  s_log.warn("getPlace() e:" + e);
		} 
        return geonamesPlace;
    }

    public static List<GeonamesPlace> getChildren(String geonameId) {        
        String url = GeonamesPlace.getChildrenUrl(geonameId);
        String json = GeonamesPlace.getJson(url);

        //A.log("GeonamesPlace.getChildren() geonameId:" + geonameId + " url:" + url + " json:" + json);

		try {
			GeonamesResponse geonamesResponse = new Gson().fromJson(json, GeonamesResponse.class);

            if (geonamesResponse == null) {
               s_log.debug("getChildren() geonamesResponse is null. json:" + json);
               return new ArrayList<>();
            }
            if (geonamesResponse.geonames == null) {
               s_log.debug("getChildren() geonames is null. geonamesResponse:" + geonamesResponse + " json:" + json + " url:" + url);
			   return null;
			}

			List<Geoname> geonamesList = geonamesResponse.geonames;

            //A.log("GeonamesPlace.getChildren() geonameId:" + geonameId + " url:" + url + " list:" + geonamesList);
			
            ArrayList<GeonamesPlace> geonamesPlaceList = new ArrayList<>();
			for (Geoname geoname : geonamesList) {

              //A.log("GeonamesPlace.getChildren() name:" + geoname.name + " url:" + url + " size:" + geonamesPlaceList.size());

			  GeonamesPlace geonamesPlace = new GeonamesPlace();
			  geonamesPlace.name = geoname.name;
			  geonamesPlace.geonameId = geoname.geonameId;
			  geonamesPlace.latitude = geoname.lat;
			  geonamesPlace.longitude = geoname.lng;
			  geonamesPlaceList.add(geonamesPlace);
			}
			return geonamesPlaceList;

		} catch (com.google.gson.JsonSyntaxException e) {
		  s_log.warn("getChildren() e:" + e + " url:" + url + " json:" + json);
		} 
        return null;
    }

    public static String getLink(Geolocale geolocale) {
      s_log.debug("getLink() name:" + geolocale.getName() + " parent:" + geolocale.getParent() + " georank:" + geolocale.getGeorank());
      String link = "";
	  try {
		GeonamesPlace place = null;
  	    place = GeonamesPlace.getPlace(geolocale.getName());   
		if (place != null) {
		  s_log.debug("getLink() woeId:" + place.getGeonameId() + "-");
		  link = "Geonames place (suggestion): <a href='http://www.geonames.org/" + place.getGeonameId() + "'>" + geolocale.getName() + "</a>";

	    }
	  } catch (Exception e) {
		s_log.debug("GeonamesPlace.getLink() e:" + e);
	  }
      return link;
    }

    public static String getCountryTag(Geolocale geolocale) {
        String url = GeonamesPlace.getCountryUrl(geolocale.getName());
        return "Geonames: <a href='" + url + "'>" + geolocale.getName() + "</a>"; 
    }
    public static String getCountryUrl(String placeName) {
        String placeNameNoSpace = Formatter.replace(placeName, " ", "+");
		String url = "http://api.geonames.org/searchJSON?q=" + placeNameNoSpace + "&maxRows=1&username=markj";
        return url;
    }
    
    public static String getChildrenUrl(String geonameId) {
        return "http://api.geonames.org/childrenJSON?geonameId=" + geonameId + "&username=markj";
    }
    public static String getChildrenTag(Geolocale geolocale) {
        GeonamesPlace place = GeonamesPlace.getPlace(geolocale.getName());
        if (place == null) {
          s_log.warn("Why would place be GeonamesPlace be null for geolocale id:" + geolocale.getId() + " name:" + geolocale.getName() + "?");
          return "";
        }
        String url = GeonamesPlace.getChildrenUrl(place.geonameId);
        return "Geonames: <a href='" + url + "'>Adm1</a>"; 
    }   

    public static String getGeonamePageUrl(String geonameId) {
        //return "http://www.geonames.org/" + geonameId;
        return "http://geotree.geonames.org/" + geonameId + "/";
    }
    public static String getGeonamePageTag(Geolocale geolocale) {
        GeonamesPlace place = GeonamesPlace.getPlace(geolocale.getName());
        String url = GeonamesPlace.getGeonamePageUrl(place.geonameId);
        return "Geonames Page: <a href='" + url + "'>" + geolocale.getName() + "</a>"; 
    }   
    
    public static String getJson(String url) {		
		String json = null;
		try {		
		  json = HttpUtil.getUrl(url);
		  //A.log("getJson() fetched:" + url);
        } catch (IOException e) {
          s_log.warn("getJson() e:" + e + " url:" + url);
          return null;
        }
        return json;    
    }
    
    public String getName() {
      return name;
    }
    public String getGeonameId() {
      return geonameId;
    }
    public String getLatitude() {
      return latitude;
    }
    public String getLongitude() {
      return longitude;
    }
    
    public String toString() {
      return "GeonamesPlace name:" + getName() + " geonameId:" + getGeonameId() + " lat:" + latitude + " lon:" + longitude;
    }    
    
}


/* for instance:
Peru:

http://api.geonames.org/searchJSON?q=Peru&maxRows=1&username=markj

{"totalResultsCount":103243,"geonames":[{"adminCode1":"00","lng":"-75.25","geonameId":3932488,"toponymName":"Republic of Peru","countryId":"3932488","fcl":"A","population":29907003,"countryCode":"PE","name":"Peru","fclName":"country, state, region,...","countryName":"Peru","fcodeName":"independent political entity","adminName1":"","lat":"-10","fcode":"PCLI"}]}

<geonames style="MEDIUM">
  <totalResultsCount>103243</totalResultsCount>
  <geoname>
    <toponymName>Republic of Peru</toponymName>
    <name>Peru</name>
    <lat>-10</lat>
    <lng>-75.25</lng>
    <geonameId>3932488</geonameId>
    <countryCode>PE</countryCode>
    <countryName>Peru</countryName>
    <fcl>A</fcl>
    <fcode>PCLI</fcode>
  </geoname>
</geonames>

And then once we have the geonameId: 3932488

http://api.geonames.org/childrenJSON?geonameId=3932488&username=markj

{"totalResultsCount":26,"geonames":
  [{"adminCode1":"01","lng":"-78.23333","geonameId":3699699,"toponymName":"Amazonas","countryId":"3932488","fcl":"A","population":443025,"numberOfChildren":7,"countryCode":"PE","name":"Amazonas","fclName":"country, state, region,...","countryName":"Peru","fcodeName":"first-order administrative division","adminName1":"Amazonas","lat":"-5","fcode":"ADM1"}
    ,{"adminCode1":"02","lng":"-77.75","geonameId":3699674,"toponymName":"Ancash","countryId":"3932488","fcl":"A","population":1039415,"numberOfChildren":20,"countryCode":"PE","name":"Ancash","fclName":"country, state, region,...","countryName":"Peru","fcodeName":"first-order administrative division","adminName1":"Ancash","lat":"-9.5","fcode":"ADM1"}
    , ...
  ]
}

<geonames style="MEDIUM">
  <totalResultsCount>26</totalResultsCount>
  <geoname>
    <toponymName>Amazonas</toponymName>
    <name>Amazonas</name>
    <lat>-5</lat>
    <lng>-78.23333</lng>
    <geonameId>3699699</geonameId>
    <countryCode>PE</countryCode>
    <countryName>Peru</countryName>
    <fcl>A</fcl>
    <fcode>ADM1</fcode>
    <numberOfChildren>7</numberOfChildren>
  </geoname>
...
</geonames>

*/


// These classes facilitate Gson.
class GeonamesResponse {
    String totalResultsCount;
    List<Geoname> geonames;
    
    public String toString() { return "GeonamesResponse c:" + totalResultsCount + " l:" + ((geonames != null) ? geonames.size() : 0); }
}

class Geoname {
  String adminCode1;
  String toponymName;  
  String name;
  String geonameId;
  String lat;
  String lng;
  String countryCode; // 2 digit identifier.
  
  public String toString() {
    return String.format("geoname:%s,geonameId:%s,lat:%s,lng:%s", name, geonameId, lat, lng);
  }

}
