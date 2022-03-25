/*
 * Created on Feb 8, 2007
 *
 */
package org.calacademy.antweb;

import java.util.ArrayList;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class Locality {
    private static Log s_log = LogFactory.getLog(Locality.class);

    private String localityCode="";
    private String localityName="";
    private String country="";
    private String islandCountry = "";
    private String adm1="";  // was province
    private String adm2="";  // was county
    private String bioregion="";
    private String museumCode = "";
    private String museumName = "";
    private float decimalLatitude= 0.0F;
    private float decimalLongitude = 0.0F ;
    private String elevation = "";
    private ArrayList collections = new ArrayList();
    private String other="";

    protected String localityNotes = "";
    protected String elevationMaxError = "";
    protected String latLonMaxError = "";
    
    private ArrayList<Group> groupList = new ArrayList();
    
    // MarkMap
    protected Map map;

    public String getElevation() {
        return elevation;
    }
    public void setElevation(String elevation) {
        this.elevation = elevation;
    }

    public String getOther() {
        return other;
    }
    public void setOther(String other) {
        this.other = other;
    }

    public String getBioregion() {
        return bioregion;
    }
    public void setBioregion(String bioregion) {
        this.bioregion = bioregion;
    }
    
    public String getMuseumCode() {
        return museumCode;
    }
    public void setMuseumCode(String museumCode) {
        this.museumCode = museumCode;
    }
    public String getMuseumName() {
        return museumName;
    }
    public void setMuseumName(String museumName) {
        this.museumName = museumName;
    }
        
    public String getCountry() {
        return country;
    }
    public void setCountry(String country) {
        this.country = country;
    }
    public String getAdm1() {
        return adm1;
    }
    public void setAdm1(String adm1) {
        this.adm1 = adm1;
    }
    public String getAdm2() {
        return adm2;
    }
    public void setAdm2(String adm2) {
        this.adm2 = adm2;
    }
    
    public float getDecimalLatitude() {
        return decimalLatitude;
    }
    public void setDecimalLatitude(float decimalLatitude) {
        this.decimalLatitude = decimalLatitude;
    }
    public float getDecimalLongitude() {
        return decimalLongitude;
    }
    public void setDecimalLongitude(float decimalLongitude) {
        this.decimalLongitude = decimalLongitude;
    }
    public String getLocalityCode() {
        return localityCode;
    }
    public void setLocalityCode(String localityCode) {
        this.localityCode = localityCode;
    }
    public String getLocalityName() {
        return localityName;
    }
    public void setLocalityName(String localityName) {
        this.localityName = localityName;
    }
    
    public String getLocalityKey() {
        if (getLocalityCode() != null) return getLocalityCode();
        return getLocalityName();
    }
    
    public String getObjectName() {
      if (getLocalityCode() != null) return getLocalityCode();
      return getLocalityName();
    }
    public ArrayList getCollections() {
        return collections;
    }
    public void setCollections(ArrayList collections) {
        this.collections = collections;
    }
    
    public String getLocalityNotes() {
        return localityNotes;
    }
    public void setLocalityNotes(String localityNotes) {
        this.localityNotes = localityNotes;
    }    
    
    public String getElevationMaxError() {
       return elevationMaxError;
    }
    public void setElevationMaxError(String elevationMaxError) {
        this.elevationMaxError = elevationMaxError;
    }    
  
    public String getLatLonMaxError() {
      return latLonMaxError;
    }
    public void setLatLonMaxError(String latLonMaxError) {
      this.latLonMaxError = latLonMaxError;
    }     

    public Map getMap() {
        return map;
    }
    public void setMap(Map map) {
        this.map = map;
    }

    public ArrayList<Group> getGroupList() {
      return this.groupList;
    }
    public void setGroupList(ArrayList<Group> groupList) {
      this.groupList = groupList;
    }
    public void addToGroupList(Group group) {
      if (!getGroupList().contains(group)) 
      getGroupList().add(group);
    }            

   public String getInstitutionsStr() {
     String institutionsStr = "";
     ArrayList<Group> groupList = getGroupList();
     int i = 0;
     for (Group group : groupList) {
       ++i;
       if (i > 1) institutionsStr += ", ";
       institutionsStr += group.getName();
     }
     return institutionsStr;
  }        
  
  public String getAmissEmail() {
    String amissEmail = "antweb@calacademy.org";  
    if (getGroupList().size() == 1) {
      amissEmail = getGroupList().get(0).getAdminEmail();
    }
    s_log.debug("getAmissEmail() size:" + getGroupList().size() + " amissEmail:" + amissEmail);
    return amissEmail;
  }

    public String getIslandCountry() {
        return islandCountry;
    }
    public void setIslandCountry(String islandCountry) {
        this.islandCountry = islandCountry;
    }

  public String toString() {
    String str = getLocalityCode();
    if (str == null) str = getLocalityName();
    return str;
  }
    
}

