package org.calacademy.antweb.geolocale;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.*;
//import javax.media.j3d.*;
import org.apache.regexp.*;

/*

Prevent
  https://www.antweb.org/specimen.do?name=fmnhins0000115373
    from showing up in: https://www.antweb.org/bigMap.do?taxonName=myrmicinaemyrmica%20incompleta&adm1Name=California


API access of ADM1 info.
	Flickr coords has commas.
	  Flickr centroid does not have parenthesis and is reversed order.
	Get the adm1 from geonames.
	  Get the centroid and Bounding Box (extent) from Flickr.
    Of note:Statoids.com

Use Bounding box in googleMap api call. See: https://developers.google.com/maps/documentation/javascript/reference
  https://maps.googleapis.com/maps/api/geocode/json?address=California

Validate lat long using extents and coords.
  Check bounding box coords of madagascar at specimen upload.


Fiji
Flickr
  174.8662, -21.0171, -178.2032, -12.4662
  180 -21.02 -179.98 -12.47
  
*/

// LocalityOverview objects are Bioregion, Geolocale and Project.
public abstract class LocalityOverview extends Overview {

    private String extent;
    private String coords;
    
    
    private String boundingBox;    
    private String boundingBoxFixed;

    private String centroid;
    private String centroidFixed;
    
    private String woeId;
    
    private String adminNotes;
    
    // Bounding Box details
    private transient double left = 0;
    private transient double bottom = 0;
    private transient double right = 0;
    private transient double top = 0;
    
    private boolean boundingBoxLoaded = false;
    
    public abstract String getLocality();
    public abstract void setLocality(String locality);

    protected String mapImage;    
    public String getMapImage() {
        return mapImage;
    }
    public void setMapImage(String mapImage) {
        this.mapImage = mapImage;
    }

    private Map map = null;
    public Map getMap() {
        return map;
    }
    public void setMap(Map map) {
        this.map = map;
    }   
    
    public abstract String getName();
    public abstract String getRoot();



// -------------- Bounding Box ----------------------
/*
Extent
Ex USA:
  179.1506 18.9117 -66.9406  71.4410
bbox = left, bottom, right, top
bbox = min Longitude , min Latitude , max Longitude , max Latitude
West Longitude: 172.445896 South Latitude: 18.910677 East Longitude: -66.950286 North Latitude: 71.386775 

specimen country:United States adm1:Idaho lat:44.07 lon:-114.74
specimen country:United States adm1:Minnisota lat:46.47 lon:46.47
specimen country:United States adm1:Maine lat:44.78 lon:-68.42 
*/
    public String getExtent() {
        return extent;
    }
    public void setExtent(String extent) {
        this.extent = extent;

        //parseExtent(); // for performance, use within isWithinBounds()
    }


    public String getWoeId() {
      return woeId;
    }
    public void setWoeId(String woeId) {
      this.woeId = woeId;
    }

    public String getAdminNotes() {
      return adminNotes;
    }
    public void setAdminNotes(String adminNotes) {
      this.adminNotes = adminNotes;
    }
    

    public String getCentroid() {
      return centroid;
    }      
    public void setCentroid(String centroid) {
        this.centroid = centroid;
    }
    public String getCentroidFixed() {
      return centroidFixed;
    }  
    public void setCentroidFixed(String centroidFixed) {
        this.centroidFixed = centroidFixed;
    }
    public String useCentroid() {
      if (getCentroidFixed() != null && !("".equals(getCentroidFixed()))) {
        return getCentroidFixed();
      } else { 
        if ("null".equals(getCentroid())) return null;
        return getCentroid();
      }      
    }
    
    public String getBoundingBox() {
        return boundingBox;
    }
    public void setBoundingBox(String boundingBox) {
        this.boundingBox = boundingBox;
    }
    
    // The Fixed versions are curator modifiable and will override the others. 
    public String getBoundingBoxFixed() {
        return boundingBoxFixed;
    }
    public void setBoundingBoxFixed(String boundingBoxFixed) {
        this.boundingBoxFixed = boundingBoxFixed;
    }
        
    public String useBoundingBox() {
      if (getBoundingBoxFixed() != null && !("".equals(getBoundingBoxFixed()))) {
        return getBoundingBoxFixed();
      } else { 
        if ("null".equals(getBoundingBox())) return null;
        return getBoundingBox();
      }  
    }

    private void loadBoundingBox() {
        if (boundingBoxLoaded) {
           //A.log("loadBoundingBox() possibly extraneous? name:" + getName());
           return;
        }
        boundingBoxLoaded = true;

        String bounds = useBoundingBox();

        left = LocalityOverview.getDoubleListVal(bounds, 1);
        bottom = LocalityOverview.getDoubleListVal(bounds, 2);
        right = LocalityOverview.getDoubleListVal(bounds, 3);
        top = LocalityOverview.getDoubleListVal(bounds, 4);
    }
    
    private static double getDoubleListVal(String list, int i) {
        // list will be a comma separated list (with spaces). Could be a bounding box or a centroid.

//if (AntwebProps.isDevMode()) AntwebUtil.logStackTrace();

        if (i < 1 || i > 4) return 0;

        list = Formatter.replace(list, ",", "");       

        if (list != null && !"".equals(list) && !list.contains("null")) {
          list = Formatter.replace(list, "  ", " "); // if an extra spaces...
          list = list.trim();
          double val = 0;

          try {
            RE tab = new RE(" ");
            String[] listArray = tab.split(list + " ");

//A.log("getDoubleListVal() 1 i:" + i + " list:" + list + " listArray:" + listArray);

            val = (Double.valueOf(listArray[i-1])).doubleValue();

//A.log("getDoubleListVal() val:" + val);

            return val;
          } catch (org.apache.regexp.RESyntaxException e) {
            A.log("getDoubleListVal() list:" + list + " val:" + val + " e:" + e);
          } catch (Exception e) {
            A.log("getDoubleListVal() list:" + list + " val:" + val + " e:" + e);
          }
        }
        return 0;
    }

    public double getLeft() {
      return left;
    } 
    public double getBottom() {
      return bottom;
    } 
    public double getRight() {
      return right;
    }
    public double getTop() {
      return top;
    }

// ------------------ Centroid -----------------------
/*
Ex USA: 
  (37.1679, -95.8450)
  (lat, lon)
*/
    public String getCoords() {
        return coords;
    }
    public void setCoords(String coords) {
        this.coords = coords;
    }

    public String getFlagIcon() {
      String name = getName();
      name = name.toLowerCase();
      name = (new Formatter()).capitalizeFirstLetter(name);
      name = name.replaceAll(" ", "-");
      name = name + "-flag.png";
      return name;
    }
    
    public static final String s_speciesListDir = "web/speciesList/";

    public static String getSpeciesListDir() {
        return s_speciesListDir;
    }    
    
    public boolean isWithinBounds(double latitude, double longitude) {
    
      loadBoundingBox();  // To populate the left,... from the extent (bounding box).
    
      return LocalityOverview.isWithinBounds(getLeft(), getBottom(), getRight(), getTop(), latitude, longitude);      
    }
    
    public static boolean isWithinBounds(String latLonPoint, String boundingBox) {
      // bounding box in format: left, bottom, right, top
      // latLonPoint in format (as is centroid): lat, lon
    
        double left = LocalityOverview.getDoubleListVal(boundingBox, 1);
        double bottom = LocalityOverview.getDoubleListVal(boundingBox, 2);
        double right = LocalityOverview.getDoubleListVal(boundingBox, 3);
        double top = LocalityOverview.getDoubleListVal(boundingBox, 4);
        double lat = LocalityOverview.getDoubleListVal(latLonPoint, 1);
        double lon = LocalityOverview.getDoubleListVal(latLonPoint, 2);
        if (left == 0 || bottom == 0 || right == 0 || top == 0 || lat == 0 || lon == 0) return false;
        return LocalityOverview.isWithinBounds(left, bottom, right, top, lat, lon);    
    }
    
   /*
    * top: north latitude of bounding box.
    * left: left longitude of bounding box (western bound). 
    * bottom: south latitude of the bounding box.
    * right: right longitude of bounding box (eastern bound).
    * latitude: latitude of the point to check.
    * longitude: longitude of the point to check.
    */
    public static boolean isWithinBounds(double left, 
	  double bottom, double right, double top,  
	  double latitude, double longitude) {
	  
	    // This method only looks for negatives. If we can't tell, return true.

        //A.log("LocalityOverview.isWithinBounds() left:" + left + " bottom:" + bottom + " right:" + right + " top:" + top + " lat:" + latitude + " lon:" + longitude);

        if (left == 0.0 && bottom == 0.0 && right == 0.0 && top == 0.0) return true;


        // Because we only care about egregious errors (right now) add a whole point to each.
        double grace = 0.2;
        left += -1 * grace;
        bottom += -1 * grace;
        right += grace;
        top += grace;

/*
		String lonModified = "";
		if (right < 0 && left > 0) { // This place crosses the anti-meridian.
		  // We need to convert the left to be it's equal but negated point for the comparisons.
		  // For instance, USA. The left == 179 which is the same as -181. Modify to make comparisons work.
		  left = -360 + left;
		  lonModified = "L";
		}
*/
        boolean isWithin = false;
		/* Check latitude bounds first. */
		if (top >= latitude && latitude >= bottom){
		/* If your bounding box doesn't wrap the date line the value must simply be between 
		   the bounds. If your bounding box does wrap the date line it only needs to be 
		   higher than the left bound or lower than the right bound. */
		  if (left <= right && left <= longitude && longitude <= right){
			isWithin = true;			
		  } else if (left > right) {  // It is wrapping around the anti-meridian (180 degrees).
		    if (longitude >= left || longitude <= right) {
			  isWithin = true;
			}
	      }
	    }

/*
Bounds:
  New Zealand	179.07 -52.62 -178.9 -29.22     165.8838, -52.6186, -175.9872, -29.2100	(-40.916667, -179.9166665)	-43.586	170.370
  Fiji	        180 -21.02 -179.98 -12.47	    174.8662, -21.0171, -178.2032, -12.4662	(-16.741667, -179.9916665)	-17.790	177.973
  Russia	    null	                        19.6389, 41.1859, -168.9978, 81.8569	null	                    59.453	108.830

Out of bounds:
  code:casent0922016 country:New Zealand boundingBox:left:165.8838 bottom:-52.6186 right:-175.9872 top:-29.21 lat:-35.89 lon:174.75 
  code:casent0173151 country:Russia boundingBox:left:19.6389 bottom:41.1859 right:-168.9978 top:81.8569 lat:58.16083 lon:44.40083
*/

        //if (true) {
        if (!isWithin
         && false
  		  ) {
  		  //if (longitude == 109.23999786376953) 
            A.log("LocalityOverview.isWithinBounds() isWithin:" + isWithin + " left:" + left + " bottom:" + bottom + " right:" + right + " top:" + top + " lat:" + latitude + " lon:" + longitude); // + " lonModified:" + lonModified);
        }

        //if ("R".equals(lonModified))
        //  A.log("LocalityOverview.isWithinBounds() isWithin:" + isWithin + " left:" + left + " bottom:" + bottom + " right:" + right + " top:" + top + " lat:" + latitude + " lon:" + longitude + " lonModified:" + lonModified);

	    return isWithin;
    }    
    
    public String getBoundingBoxStr() {
      return "left:" + left + " bottom:" + bottom + " right:" + right + " top:" + top;
    }

    public static int calculateZoomLevel(int lngMin, int latMin, int lngMax, int latMax) {
  	  int zoomLevel;
	  double latDiff = latMax - latMin;
	  double lngDiff = lngMax - lngMin;

	  double maxDiff = (lngDiff > latDiff) ? lngDiff : latDiff;
	  if (maxDiff < 360 / Math.pow(2, 20)) {
		zoomLevel = 21;
	  } else {
		zoomLevel = (int) (-1*( (Math.log(maxDiff)/Math.log(2)) - (Math.log(360)/Math.log(2))));
		if (zoomLevel < 1)
			zoomLevel = 1;
	  }
	  A.log("calculateZoomLevel() zoomLevel:" + zoomLevel);
      return zoomLevel;
    }    
    
}