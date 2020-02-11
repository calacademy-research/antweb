package org.calacademy.antweb.curate.geolocale;


import java.util.*;
import java.sql.*;

import org.apache.struts.action.*;
import javax.servlet.http.*;

import org.apache.struts.action.ActionForm;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;

public final class EditGeolocaleForm extends ActionForm {

    private static Log s_log = LogFactory.getLog(EditGeolocaleForm.class);

    protected int id;
    protected String name;
    private String isoCode;
    private String iso3Code;
    private String georank;
    protected boolean isValid;
    private boolean isLive;
    private boolean isUn;
    private String source;
    private String validName;
    private int validNameId;
    private String parent;
    private String region;
    private String bioregion;
    private String altBioregion;
//    private boolean isLive;

    private String centroidFixed;
    private String boundingBoxFixed;
    //private String latitudeFixed;
    //private String longitudeFixed;    

    private String woeId;

    private String adminNotes;
    
    private String mapImage;
    private boolean isSpeciesListMappable;
    private boolean isUseChildren;
    private boolean isUseParentRegion;
    private boolean isIsland;
    private Timestamp created;
    
    private String georankType;
    
    private String action;
    
    private boolean isCreate = false;
    private boolean isDelete = false;

    private boolean isSubmit = false;
    private boolean isFast;
        
    private String orderBy;
        
    public EditGeolocaleForm() {
    }
    
    public int getId() {
      return id;
    }
    public void setId(int id) {
      this.id = id;
    }
        
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getIsoCode() {
        return isoCode;
    }
    public void setIsoCode(String isoCode) {
        this.isoCode = isoCode;
    }
    public String getIso3Code() {
        return iso3Code;
    }
    public void setIso3Code(String iso3Code) {
        this.iso3Code = iso3Code;
    }
    
    public String getGeorank() {
        return georank;
    }
    public void setGeorank(String georank) {
        this.georank = georank;
    }

    public String getGeorankType() {
        return georankType;
    }
    public void setGeorankType(String georankType) {
        this.georankType = georankType;
    }
    
    public boolean isValid() {
        return isValid;
    }
    public void setIsValid(boolean isValid) {
        this.isValid = isValid;
    }

    public boolean isUn() {
        return isUn;
    }
    public void setIsUn(boolean isUn) {
        this.isUn = isUn;
    }

    public boolean isLive() {
        return isLive;
    }
    public void setIsLive(boolean isLive) {
        this.isLive = isLive;
    }

    public String getSource() {
        return source;
    }
    public void setSource(String source) {
        this.source = source;
    }
    
    public String getValidName() {
        return validName;
    }
    public void setValidName(String validName) {
        this.validName = validName;
    }

    public int getValidNameId() {
        return validNameId;
    }
    public void setValidNameId(int validNameId) {
        this.validNameId = validNameId;
    }
    
    // Could be the name of a particular region, subregion or country
    public String getParent() {
        return parent;
    }
    public void setParent(String parent) {
    
    /*
        A.log("setParent() parent:" + parent);
        Geolocale parentGeolocale = GeolocaleMgr.getGeolocale(parent);
        if (parentGeolocale != null) {
          setRegion(parentGeolocale.getRegion());
        }
    */
        this.parent = parent;
    }

    public String getRegion() {
        return region;
    }
    public void setRegion(String region) {
        A.log("setRegion() region:" + region);
        this.region = region;
    }

    public String getBioregion() {
        return bioregion;
    }
    public void setBioregion(String bioregion) {
        this.bioregion = bioregion;
    }

    public String getAltBioregion() {
        return altBioregion;
    }
    public void setAltBioregion(String altBioregion) {
        this.altBioregion = altBioregion;
    }

    public String getCentroidFixed() {
        return centroidFixed;
    }
    public void setCentroidFixed(String centroidFixed) {	
        this.centroidFixed = centroidFixed;
    }

    public String getBoundingBoxFixed() {
        return boundingBoxFixed;
    }
    public void setBoundingBoxFixed(String boundingBoxFixed) {	
        if (boundingBoxFixed.contains("North Latitude:")) {
          // North Latitude: -17.350311 South Latitude: -17.983958 East Longitude: 178.039970 West Longitude: 177.343368
          // We will parse this into our preferred format.
          // Should be left, bottom, right, top.
          // So the above would be...  177.343368, -17.983958, 178.039970, -17.350311
          String top = getLoc(boundingBoxFixed, 1);
          String bottom = getLoc(boundingBoxFixed, 2);
          String right = getLoc(boundingBoxFixed, 3);
          String left = getLoc(boundingBoxFixed, 4);
          String newBoundingBoxFixed = left + ", " + bottom + ", " + right + ", " + top;          
          A.log("EditGeolocaleForm.setBoundingBoxFixed() was: boundingBoxFixed:" + boundingBoxFixed + " newBoundingBoxFixed:" + newBoundingBoxFixed);
          boundingBoxFixed = newBoundingBoxFixed;
        }   
        this.boundingBoxFixed = boundingBoxFixed;
    }

    private String getLoc(String box, int i) {
      String thisLoc = "";
      try {
		  // see: https://www.mapdevelopers.com/geocode_bounding_box.php. This is where the box format comes from.
		  int thisLocColon = box.indexOf(":");
		  while (i > 1) {
			thisLocColon = box.indexOf(":", thisLocColon + 1);
			i = i - 1;        
		  }
		  int endOfLoc = box.indexOf(" ", thisLocColon + 2);
		  if (endOfLoc > 0) {
	  	    thisLoc = box.substring(thisLocColon + 2, endOfLoc);
          } else {
            thisLoc = box.substring(thisLocColon + 2);
          }
		  A.log("EditGeolocaleForm.getLoc() i:" + i + " thisLocColon:" + thisLocColon + " endOfLoc:" + endOfLoc + " thisLoc:" + thisLoc);
      } catch (Exception e) {
        s_log.warn("getLoc() e:" + e);
      }
      return thisLoc;
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

    public String getMapImage() {
        return mapImage;
    }
    public void setMapImage(String mapImage) {
        this.mapImage = mapImage;
    }
    
    public boolean isUseChildren() {
        return isUseChildren;
    }
    public void setIsUseChildren(boolean isUseChildren) {
        this.isUseChildren = isUseChildren;
    }
        
    public boolean isUseParentRegion() {
        return isUseParentRegion;
    }
    public void setIsUseParentRegion(boolean isUseParentRegion) {
        this.isUseParentRegion = isUseParentRegion;
    }
    
    public boolean isIsland() {
        return getIsIsland();
    }
    public boolean getIsIsland() {
        return isIsland;
    }
    public void setIsIsland(boolean isIsland) {
        this.isIsland = isIsland;
    }
            
    public Timestamp getCreated() {
        return created;
    }
    public void setCreated(Timestamp created) {
        this.created = created;
    }
    
    public boolean getIsCreate() {
      return isCreate;
    }
    public void setIsCreate(boolean isCreate) {
      this.isCreate = isCreate;
    }

    public boolean getIsDelete() {
      return isDelete;
    }
    public void setIsDelete(boolean isDelete) {
      this.isDelete = isDelete;
    }
    
    public boolean getIsSubmit() {
      return isSubmit;
    }
    public void setIsSubmit(boolean isSubmit) {
      this.isSubmit = isSubmit;
    }
    
    
    public boolean getIsFast() {
      return isFast;
    }
    public void setIsFast(boolean isFast) {
      this.isFast = isFast;
    }
        
    public String getOrderBy() {
      return this.orderBy;
    }
    public void setOrderBy(String orderBy) {
      this.orderBy = orderBy;
    }

    public String getAction() {
      return this.action;
    }
    public void setAction(String action) {
      this.action = action;
    }
       

	public void reset(ActionMapping mapping, HttpServletRequest request) {
	    super.reset(mapping, request);
	    
	    //A.log("EditGeolocaleForm reset()");
	    
//		this.boundingBox = null;
	}       
        
    public String toString() {
      return "id:" + id + " name:" + getName() + " isValid:" + isValid() + " isLive:" + isLive() + " validName:" + getValidName() + " woeId:" + getWoeId();
    }
}

