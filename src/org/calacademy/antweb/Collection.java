package org.calacademy.antweb;

import java.sql.*;
import java.util.ArrayList;

import org.calacademy.antweb.search.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class Collection extends AdvancedSearchResults {

    private static Log s_log = LogFactory.getLog(Collection.class);

    private String code = "";
    private String habitat = "";
    private String microhabitat= "";
    private String method = "";
    private String collectedBy = "";
    private Locality locality = null;
    protected String collectionNotes = "";
    private String dateCollectedStart = "";
    private String dateCollectedEnd = "";
    private SearchResults specimenResults = new SearchResults();
    public SearchResults getSpecimenResults() {
      return specimenResults;
    }


    private ArrayList<Group> groupList = new ArrayList();
    
    protected Map map;
    
    public Collection makeCollectionFromRset(ResultSet rset) throws SQLException {

        Collection collection = null;
        if (rset != null) {
            collection = new Collection();
            //ResultSetMetaData meta = rset.getMetaData();
            collection.setHabitat(rset.getString(rset.findColumn("habitat")));
            collection.setMicrohabitat(rset.getString(rset.findColumn("microhabitat")));
            collection.setMethod(rset.getString(rset.findColumn("method")));
            collection.setCollectedBy(rset.getString(rset.findColumn("collectedby")));
            collection.setCode(rset.getString(rset.findColumn("collectioncode")));
            collection.setDateCollectedStart(rset.getString("datecollectedstart"));
        }
        return collection;
    }
    
    
    public static ResultItem makeResultItem(ResultSet rset) throws SQLException { //, ResultSetMetaData meta
        ResultItem result = new ResultItem();
        result.setCaste(rset.getString(rset.findColumn("caste")));
        result.setCode(rset.getString(rset.findColumn("code")));
        //A.log("makeResultItem() code:" + result.getCode());
        result.setGenus(rset.getString(rset.findColumn("genus")));
        result.setSpecies(rset.getString(rset.findColumn("species")));
        result.setLocalityName(rset.getString(rset.findColumn("localityname")));
        result.setFullName(result.getGenus() + " " + result.getSpecies());
        result.setPageParams("rank=species&genus=" + result.getGenus() + "&name=" + result.getSpecies());
        result.setType(rset.getString(rset.findColumn("type_status")));
        result.setAdm1(rset.getString(rset.findColumn("adm1")));    // No adm2?  County?
        result.setAdm2(rset.getString(rset.findColumn("adm2")));    // This was added.  12/13/2011
        result.setCountry(rset.getString(rset.findColumn("country")));
        if (rset.getInt(rset.findColumn("images")) > 0) {
            result.setHasImages(true);
        }
        //Mark added Feb 2013
        result.setHabitat(rset.getString(rset.findColumn("habitat")));
        result.setMicrohabitat(rset.getString(rset.findColumn("microhabitat")));
        result.setDecimalLatitude(rset.getFloat(rset.findColumn("decimal_latitude")));
        result.setDecimalLongitude(rset.getFloat(rset.findColumn("decimal_longitude")));  
        result.setElevation(rset.getString(rset.findColumn("elevation"))); 
        result.setMethod(rset.getString(rset.findColumn("method")));          
        result.setDnaExtractionNotes(rset.getString(rset.findColumn("dnaextractionnotes")));
        result.setDeterminedBy(rset.getString(rset.findColumn("determinedby")));
        result.setCollectedBy(rset.getString(rset.findColumn("collectedby")));
        result.setDateCollectedStart(rset.getString(rset.findColumn("datecollectedstart")));
        result.setAccessGroup(rset.getInt(rset.findColumn("access_group")));
        result.setGroupName(rset.getString(rset.findColumn("groupname")));                    
        result.setOwnedBy(rset.getString(rset.findColumn("ownedby")));        
        return result;
    }
    
    
    // We override both GenericSearchResults.getResultsAsTaxon() and GenericSearchResults.sortBy()
    // This method allows getResultsAsTaxon to work correctly...
    public ArrayList<ResultItem> getResults() {
        if (getSpecimenResults().getResults() == null) return new ArrayList();    
        return getSpecimenResults().getResults();
    }

    public String getCode() {
        return code;
    }
    public void setCode(String code) {
        this.code = code;
    }
    
    public String getCollectionCode() {
        return code;
    }
    public void setCollectionCode(String collectionCode) {
        this.code = code;
    }
        
    public String getHabitat() {
        return habitat;
    }
    public void setHabitat(String habitat) {
        this.habitat = habitat;
    }

    public String getMicrohabitat() {
        return microhabitat;
    }
    public void setMicrohabitat(String microhabitat) {
        this.microhabitat = microhabitat;
    }
    
    public String getCollectionNotes() {
        return collectionNotes;
    }
    public void setCollectionNotes(String collectionNotes) {
        this.collectionNotes = collectionNotes;
    }        
    
    public String getMethod() {
        return method;
    }
    
    public Locality getLocality() {
        return locality;
    }
    public void setLocality(Locality locality) {
        this.locality = locality;
    }
    public void setMethod(String method) {
        this.method = method;
    }

    public String getCollectedBy() {
        return collectedBy;
    }
    public void setCollectedBy(String collectedBy) {
        this.collectedBy = collectedBy;
    }
    
    public String getDateCollectedStart() {
        return dateCollectedStart;
    }
    public void setDateCollectedStart(String dateCollectedStart) {
        this.dateCollectedStart = dateCollectedStart;
    }
    public String getDateCollectedEnd() {
        return dateCollectedEnd;
    }
    public void setDateCollectedEnd(String dateCollectedEnd) {
        this.dateCollectedEnd = dateCollectedEnd;
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
      amissEmail = ((Group) getGroupList().get(0)).getAdminEmail();
    }
    s_log.debug("getAmissEmail() size:" + getGroupList().size() + " amissEmail:" + amissEmail);
    return amissEmail;
  }

  public String toString() {
    return "Collection code:" + code;
  }

}
