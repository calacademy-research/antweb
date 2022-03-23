package org.calacademy.antweb.data.googleApis;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;  

import com.google.gson.*;

public class GoogleApisAdm1 {

    private static Log s_log = LogFactory.getLog(GoogleApisAdm1.class);


    public static final String source = "GoogleApis";

    private static String json = null;

    private static String centroid;
    private static String boundingBox;
    
    public static String getCentroid() { return centroid; }
    public static String getBoundingBox() { return boundingBox; }

/*
https://maps.googleapis.com/maps/api/geocode/json?address=Yolo, California&key=AntwebProps.getGoogleMapKey()
*/
    public static void fetch(String fetchUrl) throws AntwebException {
	  centroid = null;
	  boundingBox = null;

        Adm1Response adm1Response = null;

        json = null;
        json = HttpUtil.getJson(fetchUrl);
        if (json == null) {
          s_log.warn("GoogleApisAdm1.getAdm1() Failed to get Json from fetchUrl:" + fetchUrl);
          return;
        }

        //A.log("getAdm1() json:" + json);

		try {
			adm1Response = new Gson().fromJson(json, Adm1Response.class);

            s_log.debug("GoogleApisAdm1.fetch() adm1Response:" + adm1Response);
            if (adm1Response == null) {
              s_log.debug("fetch() nullResults. adm1Response:" + adm1Response + " json:" + json);
              //s_log.warn("fetch("+ adm1CommaCountry + ") null adm1Response for json:" + json);
              throw new AntwebException("nullResults");
            }

/*
s_log.warn("1:" + adm1Response);            
s_log.warn("2:" + adm1Response.results);            
s_log.warn("3:" + adm1Response.results.get(0));            
s_log.warn("4:" + adm1Response.results.get(0).address_components[0]);            
s_log.warn("4:" + adm1Response.results.get(0).address_components[0].short_name);            
s_log.warn("5:" + adm1Response.results.get(0).address_components[0].types);            
s_log.warn("6:" + adm1Response.results.get(0).address_components[0].types[0].toCanonicalLiteral());      
      
            String type = adm1Response.results.get(0).address_components[0].types[0].toCanonicalLiteral();
	 	    s_log.warn("fetch() type:" + type);
				        
            if (!"administrative_area_level_1".equals(type)) {
				String message = adm1CommaCountry + " type:" + type;			
                s_log.warn("fetch() message:" + message);
				LogMgr.appendLog("googleApisAdm1Level.log", message);			
            }
*/
            
			if (adm1Response.results.size() > 1) {
              throw new AntwebException("ambiguous");
			}
			if (adm1Response.results.size() < 1) {
              throw new AntwebException("zeroResults");
			}

            s_log.debug("fetch() c:" + adm1Response.results.size());

			centroid = getCentroid(adm1Response);
			boundingBox = getBoundingBox(adm1Response);

			if (false && AntwebProps.isDevMode()) {
			  s_log.warn("GoogleApisAdm1.getAdm1() json:" + json + " response:" + adm1Response);
	          AntwebUtil.logShortStackTrace();
			}
		} catch (AntwebException e) {
          throw e;
		} catch (JsonSyntaxException e) {
		  s_log.warn("fetch() fetchUrl:" + fetchUrl + " e:" + e);
        } catch (Exception e) {
		  s_log.warn("fetch() fetchUrl:" + fetchUrl + " adm1Response:" + adm1Response + " e:" + e);
          throw new AntwebException("processError");
        }
    }     

    public static boolean hasAdm2Data() {
      if (json != null) {
          return json.contains("administrative_area_level_2");
      }
      return false;
    }

    private static String getCentroid(Adm1Response adm1Response) {        
        Geometry geometry = adm1Response.results.get(0).geometry;
        String lng = geometry.location.lng;
        String lat = geometry.location.lat;
        String centroid = lat + ", " + lng;
        //String message = "getBoundingBox() l:" + l + " geometry:" + geometry; 
        //A.log(message);
        return centroid;
    }

    private static String getBoundingBox(Adm1Response adm1Response) {        
        Geometry geometry = adm1Response.results.get(0).geometry;
        String l = geometry.bounds.southwest.lng;
        String b = geometry.bounds.southwest.lat;
        String r = geometry.bounds.northeast.lng;
        String t = geometry.bounds.northeast.lat;
        String box = l + ", " + b + ", " + r + ", " + t;
        //String message = "getBoundingBox() l:" + l + " geometry:" + geometry; 
        //A.log(message);
        return box;
    }
    
    
}





