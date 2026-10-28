package org.calacademy.antweb;

import java.util.Date;
import java.sql.*;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;

public class Museum extends LocalityOverview implements Countable {
    
    private static final Log s_log = LogFactory.getLog(Museum.class);

    private String code;
    private String name;
    private String title;

    private boolean isActive;    

    private Hashtable<String, String> description;

    private Date created;      
    private Date modified;      
    
    public Museum() {
    }

    public String getCode() {
        return code;
    }
    public void setCode(String code) {
        this.code = code;
    }
        
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
   
    public Date getCreated() {
        return this.created;
    }
    public void setCreated(Date created) {
        this.created = created;
    }
            
    public Date getModified() {
        return this.modified;
    }
    public void setModified(Date modified) {
        this.modified = modified;
    }


    public boolean getIsActive() {
      return isActive;
    }
      
    public void setIsActive(boolean isActive) {
      this.isActive = isActive;
    }         


// --- Implement Overviewable    

    public String getTaxonSetTable() {
      return "museum_taxon";
    }
    public String getTable() {
        return "museum";
    }

    public String getHeading() {
      return "Museum";
    }
        
    public String getTargetDo() {
      return "museum.do";
    }
    public String getPluralTargetDo() {
      return "museums.do";
    }     
    public String getParams() {
      return "museumCode=" + getCode();   
    }      

    public String getSearchCriteria() {
      return "museum=" + getName();   
    }  
    
    public String getThisPageTarget() {
      return AntwebProps.getDomainApp() + "/" + getTargetDo() + "?" + getParams();    
    }
    
    public String getDisplayName() {
      String displayName = getCode();
      if (getName() != null) displayName = getName();
      //if (getTitle() != null) displayName = getTitle();
      //s_log.warn("getDisplayname() displayName:" + displayName + " code:" + getCode() + " title:" + getTitle() + " name:" + getName());
      return displayName;
    }
    public String getShortDisplayName() {
      return getCode();
    }
        
    public String getFetchChildrenClause() {
  	  return ", museum_taxon where museum_taxon.taxon_name = taxon.taxon_name"
  	        + " and code = '" + getCode() + "'";      
    }    
//    public String getChosenImageClause() {
    public String getSpecimenTaxonSetClause() {    
      return "museum_taxon where museum_taxon.taxon_name = specimen.taxon_name "
            + " and museum_taxon.code = '"  + getCode() + "'";
    }       

    public String getImageCountQuery(String taxonName) {
        String theQuery = " select image_count from museum_taxon where taxon_name = \"" + taxonName + "\" "
		    + " and code ='" + getCode() + "'";
        return theQuery;    
    }    
        
    public TaxonSet getTaxonSet(String taxonName, String rank, Connection connection) throws SQLException {
        TaxonSet taxonSet = new MuseumTaxon(this, taxonName, rank);
        try {
            taxonSet.init(connection);
        } catch (SQLException e) {
            s_log.error("getTaxonSet(" + getCode() + ") e:" + e);
            throw e;
        }
        return taxonSet;
    }
    
    public String getRecalcLink() {
      return "<a href='" + getThisPageTarget() + "&action=recalc'>Update statistics</a>";    
    }    
    
    
/*
    public String getGoogleMapFunctionLink() {
      String genLink = "<a href='" + AntwebProps.getDomainApp() + "/utilData.do?action=genGoogleMapFunction&num=" + getId() + "'>Generate Google Map " + getName() + "</a>.";
      String delLink = "[<a href='" + AntwebProps.getDomainApp() + "/utilData.do?action=delGoogleMapFunction&num=" + getId() + "'>x</a>]";
      return genLink + delLink;
    }    
 */    
     
    public ArrayList sort(ArrayList children) {
        // The logic here will sort with the specified museum at the top.
        ArrayList<Specimen> sortedChildren = new ArrayList<>();

        String museumCode = getCode();
        for (Object o : children) {
          Specimen s = (Specimen) o;
          if (museumCode.equals(s.getMuseumCode())) {
            sortedChildren.add(s);
          }
        }
        for (Object o : children) {
          Specimen s = (Specimen) o;
          if (!museumCode.equals(s.getMuseumCode())) {
            sortedChildren.add(s);          
          }
        }
        return sortedChildren;
    }  
    
// --------------------------------------------------------------------   

        
    public String toString(String createdStr) {
      return "museumCode:" + getCode() + " specimenCount:" + getSpecimenCount() + " imageCount:" + getImageCount(); 
    }   
    
    public Hashtable<String, String> getDescription() {
        if (description == null) description = new Hashtable<>();
        return description;
    }
    public void setDescription(Hashtable<String, String> description) {
        this.description = description;
    }

    public boolean hasDescription(String title) {
      Set<String> keys = getDescription().keySet();
      for (String key : keys) {
        if (key.equals(title)) return true;
      }
      return false;
    }
    
    // -------------------- Countable Implementation ---------------------
    
    // The museum's specimens of each name on its list, of a countable status; a specimen filed under a synonym
    // counts under the valid name (2026-10-22, PlaceTotals.specimensByListNameSql).
    public String getCountSpecimensQuery() {
      String code = org.calacademy.antweb.home.PlaceTotals.literal(getCode());
      return org.calacademy.antweb.home.PlaceTotals.specimensByListNameSql(org.calacademy.antweb.home.PlaceTotals.MUSEUM, code,
          " and s.museum = " + code + " and s.status in " + StatusSet.getCountables());
    }        

    public String getCountChildrenQuery(String rank) {
        
        String rankClause = " taxon.taxarank = '" + rank + "'";
        if (Rank.SPECIES.equals(rank)) rankClause = " (taxon.taxarank = 'species' or taxon.taxarank = 'subspecies') "
              // a genus row's Species: names identified to genus only (Brian Fisher, 2026-10-22; PlaceTotals.identifiedToGenus)
              + " and " + org.calacademy.antweb.home.PlaceTotals.identifiedToGenus("taxon") + " ";

        String query = "select count(taxon.parent_taxon_name) count, taxon.parent_taxon_name parentTaxonName from taxon "
          + " join museum_taxon mt on taxon.taxon_name = mt.taxon_name " 
          + " where " + rankClause
          + new StatusSet(StatusSet.ALL).getAndCriteria()  // Needed for museum?
          + "   and mt.code = '" + getCode() + "'"
          + " group by parentTaxonName ";
        return query;    
    }
    
    public String getCountGrandChildrenQuery(String rank, String column) {
       
        String rankClause = " taxon.taxarank = '" + rank + "'";
	        if (Rank.SPECIES.equals(rank)) { 
          rankClause = " (taxon.taxarank = 'species' or taxon.taxarank = 'subspecies') ";
        }
        
        // parent is a genus
        String query = "select sum(mt." + column + ") sum, taxon.parent_taxon_name parentTaxonName from taxon " 
            + " join museum_taxon mt on taxon.taxon_name = mt.taxon_name " 
            + "  where " + rankClause
            + new StatusSet(StatusSet.ALL).getAndCriteria() // needed for museum?
            + "   and mt.code = '" + getCode() + "'"
            + "  group by parentTaxonName";
        return query;
    }

    public String getUpdateCountSQL(String parentTaxonName, String columnName, int count) {
        String updateCountSQL = "update museum_taxon set " + columnName + " = '" + count + "'" 
            + " where code = '" + getCode() + "'" 
            + " and taxon_name = '" + parentTaxonName + "'";

        return updateCountSQL;    
    }
    
    public String getTaxonImageCountQuery() {
      String query = "select s.taxon_name taxonName, s.family family, s.subfamily subfamily " 
           + ", s.genus genus, s.species species, count(i.id) imageSum"   // ant images only (2026-10-27)
           + " from specimen s join museum_taxon mt on s.taxon_name = mt.taxon_name "
           + org.calacademy.antweb.home.StatisticsDb.ANT_IMAGES_OF_SPECIMEN
           + " where mt.code = '" + getCode() + "'" 
           + " and s.museum = '" + getCode() + "'"  // 2026-10-20: images of this museum's specimens only
           + " group by s.taxon_name, s.family, s.subfamily, s.genus, s.species"
           + " order by s.family, s.subfamily, s.genus, s.taxon_name";  // CountDb.imageCountCrawl() adds the rows up by the taxon tree
      //A.log("getTaxonImageCountQuery() query:" + query);

      return query;
    }

    public String getUpdateImageCountSQL(String taxonName, int sum) { //, String rank) {
        String updateSql = "update museum_taxon set image_count = " + sum 
            + " where code = '" + getCode() + "'" 
            + " and taxon_name = '" + taxonName + "'";
        return updateSql;    
    }

// ----------------------------------------------------    

    public String getChangeViewOptions(String otherUrl) {
        String changeViewOptions = "";

        ArrayList<Museum> museums = MuseumMgr.getMuseums();
        
		for (Museum museum : museums) {
		  if (!museum.getCode().equals(getCode())) {

			boolean isOverviewPage = otherUrl.contains(getTargetDo());


            //A.log("getChangeViewOptions() isOverviewPage:" + isOverviewPage + " otherUrl:" + otherUrl);

			if (isOverviewPage) {
			  //changeViewOptions += "<li><a href='" + museum.getThisPageTarget() + "'>" + museum.getTitle() + "</a></li>";          			
			  changeViewOptions += "<li><a href='" + Html.escAttr(otherUrl + "?" + museum.getParams()) + "'>" + Html.esc(museum.getTitle()) + "</a></li>";          
			} else {
			  changeViewOptions += "<li><a href='" + Html.escAttr(otherUrl + "&" + museum.getParams()) + "'>" + Html.esc(museum.getTitle()) + "</a></li>";
			}
          }
 		}

        if (!"".equals(changeViewOptions) && !Project.isPerformanceSensitive(otherUrl)) changeViewOptions = Overview.selectSeparator + changeViewOptions;

        return changeViewOptions;
    }  
      
    public String getRoot() {
      return null;
    }     
    public String getLocality() {
      //return "bioregion = '" + getName() + "'";
      //return locality;
      return null;
    }
    public void setLocality(String locality) {
      // Does nothing.  We construct in the get method.
    //  this.locality = locality;
    }          
        
      
    public String getLink() {
      String link = "";
      link += "<a href='" + AntwebProps.getDomainApp() + "/museum.do?code=" + Html.escAttr(Html.url(getCode())) + "'>" + Html.esc(getName()) + "</a>";
      //A.log("getLink() link:" + link);
      return link;
    }         
         
}
