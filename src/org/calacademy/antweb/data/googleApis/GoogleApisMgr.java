package org.calacademy.antweb.data.googleApis;

import java.sql.*;
import java.util.*;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class GoogleApisMgr {

    private static Log s_log = LogFactory.getLog(GoogleApisMgr.class);
      
/*
https://maps.googleapis.com/maps/api/geocode/json?address=Antananarivo, Madagascar&key=AntwebProps.getGoogleMapKey()	
*/


/*
    // Some countries return incorrect results if you add "Province" into the query. Most are helped.    
    private static String[] exceptionCountries = {"Peru", "Morocco"};
    private static ArrayList<String> googleApisAdm1ExceptionCountries = new ArrayList(Arrays.asList(exceptionCountries));
    public static boolean isGoogleApisAdm1ExceptionCountry(String country) {
      return googleApisAdm1ExceptionCountries.contains(country);
    }    
*/

    static int apiCallCount = 0;      
    static int maxDevApiCall = 10;
    static int maxLiveApiCall = 1000;
    static int maxApiCall = maxLiveApiCall;

    public static String fetchData(Connection connection) {

      boolean aRecordUpdated = false;
                  
      String message = "";

      int rev = AntwebProps.getRev();

      //AntwebUtil.emptyLog("googleApisAdm1.html");
      String fileBreakStr = "<br><br>----" + DateUtil.getFormatDateTimeStr() + "----<br>";
	  LogMgr.appendLog("googleApisAdm1.html", fileBreakStr);
	  LogMgr.appendLog("googleApisAdm1Issue.html", fileBreakStr);			

	  
	  GeolocaleDb geolocaleDb = new GeolocaleDb(connection);

      if (AntwebProps.isDevMode()) maxApiCall = maxDevApiCall;
      
 	  // Loop through all the countries and all the adm1.
	  ArrayList<Geolocale> countries = GeolocaleMgr.getCountries();
	  for (Geolocale country : countries) {

	  // Loop through all the adm1.
		ArrayList<Adm1> adm1s = geolocaleDb.getAdm1s(country.getName());
		//ArrayList<Adm1> adm1s = geolocaleDb.getAdm1s(null, "g.created");

		for (Adm1 adm1 : adm1s) {
		
			// if we haven't yet found a bounding box, skip it. Stop trying (for now).
			//if (adm1.getBoundingBox() == null) continue;

			if (adm1.getRev() != 0) continue;

			// Respecting the google limit.
			if (apiCallCount >= maxApiCall) break;

			geolocaleDb.updateRev(adm1.getId(), rev);
			aRecordUpdated = true;

			try {			 	
			  if (adm1.getNameCommaCountry().contains(", null")) {
				throw new AntwebException("Null Country");
			  }
	
			  fetchAdm1(adm1, geolocaleDb);          
			} catch (AntwebException e) {
				LogMgr.appendLog("googleApisAdm1Issue.html", "<br>" + e.getMessage() + " " + adm1.getNameCommaCountry() + " id:<a href='" + AntwebProps.getDomainApp() + "/editGeolocale.do?id=" + adm1.getId() + "'>" + adm1.getId() + "</a>");			
				s_log.warn("fetchData() 1 nameCommaCountry:" + adm1.getNameCommaCountry() + " id:" + adm1.getId() + " e:" + e.getMessage());
            } catch (Exception e) {
			  s_log.warn("fetchData() 2 adm1CommaCountry:" + adm1.getNameCommaCountry() + " id:" + adm1.getId() + " e:" + e);
			  message = "GoogleApisMgr aborted. apiCallCount:" + apiCallCount;        
			  return message;              
			}
		} // for adm1

		// Respecting the google limit.
		if (apiCallCount >= maxApiCall) break;

	  } // for country
		  	
      if (!aRecordUpdated) AdminAlertMgr.add("GoogleApiMgr.fetchData() complete.", connection);
		  	
      s_log.debug("fetchData() apiCallCount:" + apiCallCount);
      message = "GoogleApids Data fetched. apiCallCount:" + apiCallCount;        
      return message;
    }
    
    public static String fetchData(Connection connection, int id) {
        String message = "";
	    GeolocaleDb geolocaleDb = new GeolocaleDb(connection);
	            
        Adm1 adm1 = (Adm1) geolocaleDb.getGeolocale(id);
		s_log.warn("fetchData() process id:" + adm1.getId() + " adm1:" + adm1);

		try {
		  if (adm1.getNameCommaCountry().contains(", null")) {
			throw new AntwebException("Null Country");
		  }

		  fetchAdm1(adm1, geolocaleDb);          
		} catch (AntwebException e) {
		    message = e.getMessage() + " " + adm1.getNameCommaCountry() + " id:" + adm1.getId();
			LogMgr.appendLog("googleApisAdm1.log", message);			
			s_log.warn("fetchData() log to googleApisAdm1.log message: " + message);
			return message;
		} catch (Exception e) {
		  s_log.warn("fetchData() adm1CommaCountry:" + adm1.getNameCommaCountry() + " id:" + adm1.getId() + " e:" + e);
		  message = "GoogleApisMgr aborted. apiCallCount:" + apiCallCount;        
		  return message;              
		}


      s_log.debug("fetchData() apiCallCount:" + apiCallCount);
      message = "GoogleApids Data fetched. apiCallCount:" + apiCallCount;        
      return message;
    }

    public static String getFetchAdm1Url(Geolocale geolocale) { 
        String name = geolocale.getName();
        String country = geolocale.getParent();  
        
        String georankTypeStr = "";
        if (geolocale.getGeorankType() != null) georankTypeStr = "%20" + geolocale.getGeorankType();
        
        String encodeAdm1 = HttpUtil.encode(name) + georankTypeStr;
        String encodeCountry = HttpUtil.encode(country);
		String fetchStr = "address=" + encodeAdm1 + ",%20" + encodeCountry + "&components=administrative_area_level_1:" + encodeAdm1 + "|Country:" + encodeCountry;
    
        String fetchUrl = "https://maps.googleapis.com/maps/api/geocode/json?" + fetchStr + "&key=" + AntwebProps.getGoogleMapKey();
       
        return fetchUrl;
    }

    public static String getFetchCountryUrl(Geolocale geolocale) { 
        String country = geolocale.getName();
        String encodeCountry = HttpUtil.encode(country);
		String fetchStr = "address=" + encodeCountry + "&components=Country:" + encodeCountry;
        String fetchUrl = "https://maps.googleapis.com/maps/api/geocode/json?" + fetchStr + "&key=" + AntwebProps.getGoogleMapKey();
        return fetchUrl;
    }

    private static void fetchAdm1(Adm1 adm1, GeolocaleDb geolocaleDb) throws AntwebException {

        String fetchUrl = GoogleApisMgr.getFetchAdm1Url(adm1);

		++apiCallCount;

		s_log.debug("GoogleApisMgr.fetchAdm1() BEFORE adm1:" + adm1.getName() + ", " + adm1.getParent() + " box:" + adm1.getBoundingBox() + " centroid:" + adm1.getCentroid());
	 
		GoogleApisAdm1 googleAdm1 = new GoogleApisAdm1();
		googleAdm1.fetch(fetchUrl);

        if (googleAdm1.hasAdm2Data()) {
          String message = "<br>country:" + adm1.getParent() + " adm1:" + adm1.getName() + " <a href='" + fetchUrl + "'>link</a>";
          s_log.debug("fetchAdm1() log to googleApisAdm1Adm2Data.html message:" + message);
		  LogMgr.appendLog("googleApisAdm1Adm2Data.html", message);
        }

		AntwebUtil.sleep(.1);

		String boundingBox = googleAdm1.getBoundingBox();
		String centroid = googleAdm1.getCentroid();
		s_log.debug("fetchAdm1() adm1:" + adm1.getName() + ", " + adm1.getParent() + " centroid:" + centroid + " boundingBox:" + boundingBox);
		  
		if (boundingBox != null && centroid != null) {
		  adm1.setBoundingBox(boundingBox);
		  adm1.setCentroid(centroid);
		  geolocaleDb.updateCentroid(adm1);
		  geolocaleDb.updateBoundingBox(adm1);

		  LogMgr.appendLog("googleApisAdm1.html", " <a href='" + AntwebProps.getDomainApp() + "/adm1.do?id=" + adm1.getId() + "'>" + adm1.getName() + ", " + adm1.getParent() + "</a>");
		}
    }

}