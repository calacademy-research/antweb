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
    
public class FlickrPlace extends DataPlace {

    private static Log s_log = LogFactory.getLog(FlickrPlace.class);

    public static final String source = "Flickr";

    private String name;
    private String woeId;
    private String latitude;
    private String longitude;
    private String boundingBox;
    private int placeTypeId;
    private String _content;

/*
  See: https://www.flickr.com/services/api/explore/flickr.places.find
  
  Run the query and you can get a temporary api_key from the url at the bottom.
    This api key will expire in a day or so.
  
  To get the woeid:
    https://www.flickr.com/services/api/explore/flickr.places.find
   
  Once you have the woeid:
    https://www.flickr.com/places/info/2346453
  
  See the loaded bounding box data in the database:
    http://localhost/antweb/query.do?action=curiousQuery&name=flickrGeoData
    
  A log file is generated to record the bound violations upon specimen upload.
    http://localhost/antweb/web/log/outOfBounds.txt  

  To invoke a fetch of the Flickr data:
    /util.do?action=fetchFlickrPlaces
*/

    static String s_flickrAdm1 = "flickrAdm1.html";

    // 
    public static String fetchAdm1Data(Connection connection) {
      
      String message = "Flickr Child Data fetched:";
      
      if (!GeolocaleMgr.isInitialized()) {
        return "Geolocale Manager is not initialized.";
      }
      
      //GeolocaleMgr.populate(connection, true);
      
      GeolocaleDb geolocaleDb = new GeolocaleDb(connection);
      //geolocaleDb.deleteFetchedAdm1(FlickrPlace.source);

	  LogMgr.emptyLog(s_flickrAdm1);
	  
	  ArrayList<Country> countries = geolocaleDb.getCountries();
	  int i = 0;
	  for (Country country : countries) {

		if ((++i % 100) == 0) s_log.warn("fetchAdm1Data() countries processed:" + i);
        if (country.getName() == null || "".equals(country.getName())) {
          s_log.warn("fetchAdm1Data() No name:" + country.getName() + " for country with id:" + country.getId());
          continue;
        }
		fetchAdm1Data(connection, country.getName());
	  }

	  message += " for all countries:" + countries.size();

      return message;
    }

 
    public static String fetchAdm1Data(Connection connection, String countryName) {

        //Geolocale country = GeolocaleMgr.getAnyCountry(countryName);        

        GeolocaleDb geolocaleDb = new GeolocaleDb(connection);
        //geolocaleDb.deleteFetchedAdm1(country, FlickrPlace.source);

        Country country = geolocaleDb.getCountry(countryName);

        ArrayList<String> childList = FlickrPlace.getChildren(country.getWoeId());
        //A.log("Flickr.fetchAdm1Data() country:" + countryName + " woeId:" + country.getWoeId() + " childList:" + childList);

        FlickrPlace.processAdm1(country, childList, geolocaleDb);
        
        String message = "Flickr.fetchAdm1Data() for country:" + country.getName(); 
        return message;
    }
    
    private static void processAdm1(Geolocale country, ArrayList<String> childList, GeolocaleDb geolocaleDb) {
    
      if (!country.getIsValid()) {
        Geolocale validCountry = GeolocaleMgr.getValidCountry(country.getName());
        if (validCountry != null) {
          country = validCountry;
        } else {
          if (A.dev()) s_log.warn("country:" + country.getName() + " is not valid. Valid not found. Skip.");
        }
      }
    
		String prefix = "<br>" + (country.getIsValid() ? "v&nbsp;" : "&nbsp;&nbsp;");
		String countryStr = prefix + "<a href='https://www.flickr.com/places/info/" + country.getWoeId() + "'>" + country.getName() + "</a>";
        LogMgr.appendLog(s_flickrAdm1, countryStr);
		int i = 0;
		for (String child : childList) {
		  String[] childStrArray = child.split(":");
		  String childWoeId = childStrArray[0];
		  String adm1Name = childStrArray[1];
		  FlickrPlace place = FlickrPlace.scrapePlace(childWoeId);
		  if (place == null) {
		    s_log.warn("processAdm1() failed to scrape in processAdm1() for adm1:" + adm1Name);
		    continue;
		  }
		  if (place.getPlaceTypeId() != 8) continue; // Only states, provinces, etc...
		  	
		  ++i;
		  	  
		  //A.log("logAdm1() woeId:" + childStrArray[0] + " name:" + adm1Name + " box:" + box);
		  prefix = "<br>&nbsp;&nbsp;&nbsp;&nbsp;";
          String adm1Str = prefix + "<a href='https://www.flickr.com/places/info/" + childWoeId + "'>" + adm1Name + "</a>";
          LogMgr.appendLog(s_flickrAdm1, adm1Str + " boundingBox:" + place.getBoundingBox());

          geolocaleDb.makeAdm1(adm1Name, country.getName(), place, FlickrPlace.source); // will be an insert.
		}    
        s_log.warn("processAdm1() country:" + country + " childList:" + i);       
    }


    // http://localhost/antweb/util.do?action=fetchGeoData
    // Flickr is used to fetch the bounding box, latitude and longitude for each country. 
    public static String fetchCountryData(Connection connection) {
      
      if (!GeolocaleMgr.isInitialized()) {
        return "Geolocale Manager not yet initialized.";
      }
      String message = "";
      
      GeolocaleDb geolocaleDb = new GeolocaleDb(connection);

        ArrayList<Country> countries = geolocaleDb.getCountries();
        for (Geolocale country : countries) {
            String countryName = country.getName();
            
            FlickrPlace.fetchCountryData(connection, countryName);   
        }
        
        message = "full fetchCountryData() completed.";
      return message;
    }   

    public static String fetchCountryData(Connection connection, String countryName) {
      
      //if (!GeolocaleMgr.isInitialized()) {
      //  return "Geolocale Manager not yet initialized.";
      //}
      String message = "";
      
      GeolocaleDb geolocaleDb = new GeolocaleDb(connection);
      Geolocale country = geolocaleDb.getGeolocale(countryName, Georank.COUNTRY);

		FlickrPlace place = null;
		try {
		  place = FlickrPlace.getPlace(countryName);
		} catch (AntwebException e) {
		  s_log.error("fetchCountryData() e:" + e);
		}
		if (place != null) {
			//A.log("fetchGeoData() id:" + geolocale.getId() + " country:" + countryName + " box:" + place.getBoundingBox() + " lat:" + place.getLatitude() + " lon:" + place.getLongitude());    
			geolocaleDb.updateGeoData(country.getId(), null, place, FlickrPlace.source);
		} else {
			if (A.dev()) s_log.warn("fetchCountryData() Place not found:" + countryName);             			
		}
        
        message = "full fetchCountryData() completed.";
      return message;
    }   
  
    public static FlickrPlace fetchGeoData(Geolocale geolocale) 
      throws AntwebException {
        FlickrPlace place = null;
		if ("adm1".equals(geolocale.getGeorank())) {
		  place = FlickrPlace.getPlace(geolocale.getName(), geolocale.getParent());
		} else {
		  place = FlickrPlace.getPlace(geolocale.getName());
		}
		return place;        
    }

// ---------------- Place Management Methods --------------------------
    
    public static FlickrPlace getPlace(String placeName)
      throws AntwebException {
        return FlickrPlace.getPlace(placeName, null);
    }

    public static FlickrPlace getPlace(String placeName, String parent) 
      throws AntwebException {
        FlickrPlace flickrPlace = new FlickrPlace();
        
        //A.log("FlickrPlace.getPlace() placeName:" + placeName + " parent:" + parent);

        String url = FlickrPlace.getPlaceInfoUrl(placeName);
        String json = FlickrPlace.getPlaceJson(url);
        if (json == null) {
          if (A.dev()) s_log.warn("json is null for name:" + placeName + " url:" + url);
          return null;
        }
        //A.log("FlickrPlace.getPlace() placeName:" + placeName + " parent:" + parent + " json:" + json);

		try {
			PlacesResponse placesResponse = new Gson().fromJson(json, PlacesResponse.class);

            if (placesResponse == null) {
               s_log.debug("getPlace() placesResponse is null. json:" + json);
               return null;
            }
            if (placesResponse.places == null) {
               s_log.debug("getPlace() places is null. placesResponse:" + placesResponse + " json:" + json);
			   return null;
			}
			
			List<Place> placeList = placesResponse.places.place;
            boolean found = false;
			for (Place place : placeList) {
              // Loop through all the places. Find the best fit.
              //A.log("FlickrPlace.getPlace() parent:" + parent + " woe_name:" + place.woe_name + " woeId:" + place.woeid + " place_type:" + place.place_type + " place_type_id:" + place.place_type_id + " content:" + place._content);   

              if (placeName.equals(place.woe_name) && ("country".equals(place.place_type) || "region".equals(place.place_type) || 24 == place.place_type_id)) {

                if (parent != null && !FlickrPlace.placeContainsParent(place, parent)) {
                  //A.log("FlickrPlace parent:" + parent + " not in place._content:" + place._content);
                  continue;
                }

                found = true;
                //A.log("FlickrPlace.getPlace() country:" + placeName + " placeType:" + place.place_type + " json:" + json);
                
                String cleanName = DataPlace.cleanName(place.woe_name);
				flickrPlace.name = cleanName;
				flickrPlace.woeId = place.woeid;

                try {
  				  flickrPlace.boundingBox = scrapePlace(place.woeid).getBoundingBox();
                } catch (Exception e) {
                  // O, well. Flickr can miss sometimes. Log and skip. Likely to work next time. 
                  String message = "failed to scrape woe_name:" + place.woe_name + " woeId:" + place.woeid;
                  String eMess = " e:" + e;
                  if (e instanceof java.lang.StringIndexOutOfBoundsException) eMess = "";
                  s_log.error("getPlace() " + message + e);
                  LogMgr.appendLog("DataPlaceCase.txt", message);
                  continue;
                }
                
				flickrPlace.latitude = place.latitude;
				flickrPlace.longitude = place.longitude;
				flickrPlace._content = place._content;
				break;
              }
			}
            //if (!found) A.log("FlickrPlace.getPlace() not found. placeName:" + placeName + " place:" + placeList + " json:" + json);

		} catch (com.google.gson.JsonSyntaxException e) {
		  s_log.warn("execute() e:" + e);
		} 
        return flickrPlace;
    }

    public static String getPlaceInfoTag(Geolocale geolocale) {
        String url = FlickrPlace.getPlaceInfoUrl(geolocale.getName());
        return "Flickr API: <a href='" + url + "'>url</a>"; 
    }
    public static String getPlaceInfoUrl(String placeName) {
        String placeNameNoSpace = Formatter.replace(placeName, " ", "+");

        // Yahoo markshermanjohnson account        
        String apiKey = "1d343308387163f48089fae4f1dccbc0";
        // Secret: 24b1e95a9a03a5b1

        String flickrUrl = "https://www.flickr.com/services/api/explore/flickr.places.find"; // For humans
		String url = 
		"https://api.flickr.com/services/rest/?method=flickr.places.find&api_key=" + apiKey + "&query=" + placeNameNoSpace + "&format=json&nojsoncallback=1"; //&api_sig=" + apiSig;

        return url;
    }
    
    public static String getPlaceJson(String url)
      throws AntwebException {
		String json = null;
		try {
		  json = HttpUtil.getUrlIso(url);

        } catch (javax.net.ssl.SSLHandshakeException e) {
          throw new AntwebException("Bad handshake");   

          //} catch (sun.security.validator.ValidatorException e) {

        } catch (Exception e) {
          s_log.warn("getPlaceJson() e:" + e + " for url:" + url);
          return null;
        }
        return json;    
    }
        
    public static boolean placeContainsParent(Place place, String parent) {
      if (place._content.contains(parent)) return true;

        return place._content.contains(FlickrPlace.getAlternate(parent));
    }

    public static String getAlternate(String parent) {
      if ("Brazil".equals(parent)) return "Brasil";
      return parent;
    }

    public String getName() {
      return name;
    }
    public String getWoeId() {
      return woeId;
    }
    public String getLatitude() {
      return latitude;
    }
    public String getLongitude() {
      return longitude;
    }
    
    public String getCentroid() {
      return "(" + getLatitude() + ", " + getLongitude() + ")";
    }
    public String getBoundingBox() {
      return boundingBox;
    }    
    public int getPlaceTypeId() {
      return placeTypeId;
    }
    public String getContent() {
      return _content;
    }

    // Just used for Adm1. Can be called from GeolocaleMgr through the record update feature.
    public static FlickrPlace scrapePlace(String woeId) {
      FlickrPlace place = new FlickrPlace();
      place.woeId = woeId;
      
      String output = "";
      String url = "https://www.flickr.com/places/info/" + woeId;
      try {
        output = HttpUtil.getUrl(url);  
      } catch (IOException e) {
        s_log.warn("scrapePlace() e:" + e);
        return null;
      }
      
      // <td>PlaceType ID</td><td>8 (<i>State</i>)</td>
      int i = 0;
      int nextTdI = 0;

      i = output.indexOf("PlaceType ID");
      i = output.indexOf("<td>", i + 16);
      nextTdI = output.indexOf("(", i) - 1;  // The space will come after the place Type Id 
      String placeTypeId = output.substring(i+4, nextTdI);
      //A.log("FlickrPlace.scrapePlace() i:" + i + " nextTdI:" + nextTdI + " placeTypeId:" + placeTypeId + " woeId:" + woeId);
      place.placeTypeId = (Integer.valueOf(placeTypeId)).intValue();

      // Go about getting the place type?

      i = output.indexOf("Bounding Box:</td>");
      i = output.indexOf("<td>", i + 19) + 4;
      nextTdI = output.indexOf("</td>", i + 19);
      if (nextTdI > 0) {
        //A.log("FlickrPlace.getBoundingBox() i:" + i + " woeId:" + woeId + " nextTdI:" + nextTdI);
        String box = output.substring(i, nextTdI);
        //A.log("FlickrPlace.getBoundingBox() woeId:" + woeId + " box:" + box);
        place.boundingBox = box;
      }
      
      i = output.indexOf("Centroid:</td>");
      i = output.indexOf("<td>", i + 14) + 4;
      nextTdI = output.indexOf("</td>", i + 14);
      if (nextTdI > 0) {
        String centroid = output.substring(i, nextTdI);
        //A.log("FlickrPlace.scrapePlace() i:" + i + " woeId:" + woeId + " nextTdI:" + nextTdI + " centroid:" + centroid);
        String[] latLng = centroid.split(",");
        place.latitude = latLng[0].trim();
        place.longitude = latLng[1].trim();
      }

      //A.log("FlickrPlace.scrapePlace() woeId:" + woeId + " box:" + place.getBoundingBox() + " centroid:" + place.getCentroid());

      return place;
    }

    // Returns a list of strings that are woeId:adm1Name
    private static ArrayList<String> getChildren(String woeId) {
      ArrayList<String> list = new ArrayList<>();
      String output = "";
      try {
        output = HttpUtil.getUrl("https://www.flickr.com/places/info/" + woeId);  
      } catch (IOException e) {
        s_log.warn("getChildren() e:" + e);
      }
      int i = output.indexOf("Children of");

//s_log.warn("FlickrPlace.getChidren() i:" + i + " woeId:" + woeId);

      if (i < 0) return list;
      i = output.indexOf("/places/info/", i);
      while (i > 0) {
         int j = output.indexOf("\">", i);
         int k = output.indexOf("</a></li>", j);
         if (k < 0) continue;
         String childWoeId = output.substring(i+13, j);
         String childName = output.substring(j+2, k);
         childName = DataPlace.cleanName(childName);
//s_log.warn("FlickrPlace.getChidren() i:" + i + " j:" + j + " k:" + k + " childWoeId:" + childWoeId + " childName:" + childName);      
         i = output.indexOf("/places/info/", k);
         list.add(childWoeId + ":" + childName);
      }
 
      return list;
    }
    
    public String toString() {
      return "FlickrPlace name:" + getName() + " woeId:" + getWoeId() + " lat:" + latitude + " lon:" + longitude + " boundingBox:" + boundingBox;
    }    

    public static String getLink(Geolocale geolocale) {
      //A.log("getLink() name:" + geolocale.getName() + " parent:" + geolocale.getParent() + " georank:" + geolocale.getGeorank());
      String link = "";
	  try {
  	    if (geolocale.getWoeId() != null) {
		  //A.log("getLink() woeId:" + geolocale.getWoeId() + "-");
		  link = "Flickr place (suggestion): <a href='https://www.flickr.com/places/info/" + geolocale.getWoeId() + "'>" + geolocale.getName() + "</a>";
	    } else {
	      link = "Flickr place: Not found<a href='https://www.flickr.com/places/info/'>.</a>";
	    }
	  } catch (Exception e) {
		s_log.debug("FlickrPace.getLink() e:" + e);
	  }
      return link;
    }

}


// These classes facilitate Gson.
class PlacesResponse {
    Places places;
    /*
    public String toString() {
      return places.place.toString();
    }
*/
}

class Places {
    List<Place> place;
}

class Place {
  String place_id;
  String woeid;
  String woe_name;
  String latitude;
  String longitude;
  String place_type;
  int place_type_id;
  String _content;
  
  public String toString() {
    return String.format("place:%s,woeId:%s,latitude:%s,longitude:%s, _content:%s", place_id, woeid, latitude, longitude, _content);
  }

}
