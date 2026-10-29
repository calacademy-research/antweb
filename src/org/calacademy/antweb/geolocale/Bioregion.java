package org.calacademy.antweb.geolocale;

import java.sql.*;
import java.util.*;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.*;

/*
mysql> select description from bioregion;
+----------------------------------------------------------------------------------------------------------------------------+
| description                                                                                                                |
+----------------------------------------------------------------------------------------------------------------------------+
| including Sub-Saharan Africa                                                                                               |
| Madagascar and Southwest Indian Ocean Islands including Seychelles, Comoros, Mascarenes                                    |
| including the bulk of Eurasia and North Africa                                                                             |
| including Australia, New Guinea, and neighboring islands. The northern boundary of this zone is known as the Wallace line. |
| including the Indian subcontinent and Southeast Asia                                                                       |
| Pacific Ocean islands including Polynesia, Melanesia, Micronesia.                                                          |
| including most of North America                                                                                            |
| including South America and the Caribbean                                                                                  |
+----------------------------------------------------------------------------------------------------------------------------+
8 rows in set (0.00 sec)
*/

public class Bioregion extends LocalityOverview implements Countable {

    private static final Log s_log = LogFactory.getLog(Bioregion.class);

    private String name;

    private Date created;
        
    //private String description;
    private String projectName;
    private String title;
    private String locality;
    private String extent;
    private String coords;
    
    public static final String AFROTROPICAL = "Afrotropical";
    public static final  String ANTARCTICA = "Antarctica";
    public static final  String AUSTRALASIA = "Australasia";
    public static final  String INDOMALAYA = "Indomalaya";
    public static final  String MALAGASY = "Malagasy";
    public static final  String NEARCTIC = "Nearctic";
    public static final  String NEOTROPICAL = "Neotropical";
    public static final  String OCEANIA = "Oceania";
    public static final  String PALEARCTIC = "Palearctic";
    
	public static final String[] list = {AFROTROPICAL, ANTARCTICA, AUSTRALASIA, INDOMALAYA, MALAGASY, NEARCTIC, NEOTROPICAL, OCEANIA, PALEARCTIC};
    
    private ArrayList<Project> projects;

    //private ArrayList<Country> countries;
    private ArrayList<String> countries;

    private Hashtable<String, String> description;
    

    public Bioregion() {
    }

    public static String getAbbrev(String bioregion) {
      String abbrev = null;
      
      switch (bioregion) {
        case AFROTROPICAL: abbrev = "At";
          break;
        case ANTARCTICA: abbrev = "A";
          break;
        case AUSTRALASIA: abbrev = "AA";
          break;
        case INDOMALAYA: abbrev = "I";
          break;
        case MALAGASY: abbrev = "M";
          break;
        case NEARCTIC: abbrev = "N";
          break;
        case NEOTROPICAL: abbrev = "Nt";
          break;
        case OCEANIA: abbrev = "O";
          break;
        case PALEARCTIC: abbrev = "P";
          break;
      }
      return abbrev;
    }

    public String fullReport() {
      String fullReport = "";
      fullReport += "\r\n" + getName() + " {";

      for (Project project : getProjects()) {
        fullReport += project.toString() + ", ";
        //A.log("fullReport() fullReport:" + fullReport + " subregion.fullReport:" + subregion.fullReport());      
      }
      fullReport += "\r\n}";
      return fullReport;
    }

    public String getName() {
      return name;
    }
    public void setName(String name) {
      this.name = name;
    }

/*
    public String getDescription() {
      return description;
    }
    public void setDescription(String description) {
       this.description = description;
    }
*/        
    // Will contain "ants"    
    public String getProjectName() {
      return projectName;
    }
    public void setProjectName(String projectName) {
      this.projectName = projectName;
    }
        
    public String getTitle() {
      return title;
    }
    public void setTitle(String title) {
      this.title = title;
    }
    
    public String getLocality() {
      return "bioregion = '" + getName() + "'";
      //return locality;
    }
    public void setLocality(String locality) {
      // Does nothing.  We construct in the get method.
    //  this.locality = locality;
    }

    public String getExtent() {
      return extent;
    }
    public void setExtent(String extent) {
      this.extent = extent;
    }

    public String getCoords() {
      return coords;
    }
    public void setCoords(String coords) {
      this.coords = coords;
    }
    
    // We implement LocalityOverview, but not fully supporting.
//    public String getMap() {
//      return null;
//    }    

    public String getRoot() {
      return null;
    }    
    
    public Date getCreated() {
        return this.created;
    }
    public void setCreated(Date created) {
        this.created = created;
    }
    
    
	public String getTag() {
        return "<a href='" + getThisPageTarget() + "'>" + getName() + "</a>";
	}

    public String toString() {
      return getName();
    }
 
    public ArrayList<Project> getProjects() {
      return projects;
    }
    public void setProjects(ArrayList<Project> projects) {
      this.projects = projects;
    } 

/*
    public ArrayList<Country> getCountries() {
      return countries;
    }
    public void setCountries(ArrayList<Country> countries) {
      this.countries = countries;
    } 
*/
    public ArrayList<String> getCountries() {
      return countries;
    }
    public void setCountries(ArrayList<String> countries) {
      this.countries = countries;
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


    // ------------------- Implement Overviewable        
      
    public String getTaxonSetTable() {
      return "bioregion_taxon";
    }
    public String getTable() {
        return "bioregion";
    }

    public String getHeading() {
      return "Bioregion";
    }
      
    public String getTargetDo() {
      return "bioregion.do";
    }
    public String getPluralTargetDo() {
      return "bioregions.do";
    }     
    public String getDisplayName() {
      String displayName = null;
      if (getName() != null) displayName = getName();
      if (getTitle() != null) displayName = getTitle();
      //s_log.warn("getDisplayname() displayName:" + displayName + " code:" + getCode() + " title:" + getTitle() + " name:" + getName());
      return displayName;
    }
    public String getShortDisplayName() {
      return getName();
    }
    
    public String getParams() {
      return "bioregionName=" + getName();   
    }  
        
    public String getSearchCriteria() {
      return "bioregion=" + getName();   
    }      
    public String getFetchChildrenClause() {
  	  return ", bioregion_taxon where bioregion_taxon.taxon_name = taxon.taxon_name "
  	        + " and bioregion_name = '" + getName() + "'";      
    }   

//    public String getChosenImageClause() {
    public String getSpecimenTaxonSetClause() {
      return "bioregion_taxon where bioregion_taxon.taxon_name = specimen.taxon_name "
            + " and bioregion_taxon.bioregion_name = '"  + getName() + "'";
    }       
        
    public String getImageCountQuery(String taxonName) {
        String theQuery = " select image_count from bioregion_taxon " 
            + " where taxon_name = \"" + taxonName + "\" "
		    + " and bioregion_name ='" + getName() + "'";
        return theQuery;    
    }
            
    public TaxonSet getTaxonSet(String taxonName, String rank, Connection connection) throws SQLException {
      TaxonSet taxonSet = new BioregionTaxon(this, taxonName, rank);
      try {
        taxonSet.init(connection);
      } catch (SQLException e) {
        s_log.error("getTaxonSet(taxonName, rank, conn) e:" + e);
        throw e;
      }
      return taxonSet;      
    }           

    public String getRecalcLink() {
      return "<a href='" + getThisPageTarget() + "&action=recalc'>Update statistics</a>";    
    } 
    
    public ArrayList<Specimen> sort(ArrayList children) {
        // The logic here will sort with the specified museum at the top.
        ArrayList<Specimen> sortedChildren = new ArrayList<>();

        String name = getName();
        for (Object o : children) {
          Specimen s = (Specimen) o;
          if (name.equals(s.getBioregion())) {
            sortedChildren.add(s);
          }
        }
        for (Object o : children) {
          Specimen s = (Specimen) o;
          if (!name.equals(s.getBioregion())) {
            sortedChildren.add(s);          
          }
        }
        return sortedChildren;
    }    
    
    // -------------------- Countable Implementation ---------------------
    
    // The bioregion's specimens of each name on its list, of a countable status; a specimen filed under a synonym
    // counts under the valid name (2026-10-22, PlaceTotals.specimensByListNameSql).
    public String getCountSpecimensQuery() {
      String name = org.calacademy.antweb.home.PlaceTotals.literal(getName());
      return org.calacademy.antweb.home.PlaceTotals.specimensByListNameSql(org.calacademy.antweb.home.PlaceTotals.BIOREGION, name,
          " and s.bioregion = " + name + " and s.status in " + StatusSet.getCountables());
    }        

    public String getCountChildrenQuery(String rank) {
        
        String rankClause = " taxon.taxarank = '" + rank + "'";
	        if (Rank.SPECIES.equals(rank)) { 
          rankClause = " (taxon.taxarank = 'species' or taxon.taxarank = 'subspecies') "
              // a genus row's Species: names identified to genus only (Brian Fisher, 2026-10-22; PlaceTotals.identifiedToGenus),
              // of status valid or morphotaxon (2026-10-28, PlaceTotals.SPECIES_STATUSES; every list, curated projects too)
              + " and " + org.calacademy.antweb.home.PlaceTotals.identifiedToGenus("taxon")
              + " and " + org.calacademy.antweb.home.PlaceTotals.speciesStatusCondition("taxon") + " ";
        }

        String query = "select count(taxon.parent_taxon_name) count, taxon.parent_taxon_name parentTaxonName from taxon "
          + " join bioregion_taxon bt on taxon.taxon_name = bt.taxon_name " 
          + " where " + rankClause
          + new StatusSet(StatusSet.ALL).getAndCriteria()
	      + "   and bt.bioregion_name = '" + getName() + "'"
          + " group by parentTaxonName ";
        return query;    
    }
    
    public String getCountGrandChildrenQuery(String rank, String column) {
       
        String rankClause = " taxon.taxarank = '" + rank + "'";
	        if (Rank.SPECIES.equals(rank)) { 
          rankClause = " (taxon.taxarank = 'species' or taxon.taxarank = 'subspecies') ";
        }
        
        // parent is a genus
        String query = "select sum(bt." + column + ") sum, taxon.parent_taxon_name parentTaxonName " 
            + " from taxon " 
            + " join bioregion_taxon bt on taxon.taxon_name = bt.taxon_name " 
            + "  where " + rankClause
            + new StatusSet(StatusSet.ALL).getAndCriteria()
            + "   and bt.bioregion_name = '" + getName() + "'"
            + "  group by parentTaxonName";
        return query;
    }
    
    public String getUpdateCountSQL(String parentTaxonName, String columnName, int count) {
        String updateCountSQL = "update bioregion_taxon set " + columnName + " = '" + count + "'" 
            + " where bioregion_name = " + org.calacademy.antweb.util.DBUtil.sqlQuote(getName())
            + " and taxon_name = " + org.calacademy.antweb.util.DBUtil.sqlQuote(parentTaxonName);   // quoted (2026-10-29): a name with ' broke the update
        return updateCountSQL;
    }
    public String getOtherUpdateCountSQL(String parentTaxonName, String columnName, int count) {
        return null;
    }
    
    public String getUpdateImageCountSQL(String taxonName, int sum) { //, String rank) {
        String updateSql = "update bioregion_taxon set image_count = " + sum 
            + " where bioregion_name = " + org.calacademy.antweb.util.DBUtil.sqlQuote(getName())
            + " and taxon_name = " + org.calacademy.antweb.util.DBUtil.sqlQuote(taxonName);
        return updateSql;    
    }

    // Images of the ant (2026-10-27) of this bioregion's specimens (2026-10-20) of each name on its list. Brian Fisher,
    // 2026-10-28: images follow the Specimens rule: a specimen filed under a synonym counts under the valid name (Carebara
    // polita, a synonym of C. diversa: its images count for C. diversa), and only specimens of a counted status, as the
    // countries' lists (PlaceTotals.imagesByListNameSql). CountDb.imageCountCrawl() adds the rows up by the taxon tree.
    public String getTaxonImageCountQuery() {
      String name = org.calacademy.antweb.home.PlaceTotals.literal(getName());
      String query = org.calacademy.antweb.home.PlaceTotals.imagesByListNameSql(org.calacademy.antweb.home.PlaceTotals.BIOREGION, name,
          " and s.bioregion = " + name + " and s.status in " + StatusSet.getCountables());
      return query;
    }

// ------------------------------------------------------------------

    public String getChangeViewOptions(String otherUrl) {
        String changeViewOptions = "";

        ArrayList<Bioregion> bioregions = BioregionMgr.getBioregions();
        
		for (Bioregion bioregion : bioregions) {
		  if (!bioregion.getName().equals(getName())) {

			boolean isOverviewPage = otherUrl.contains(getTargetDo());

            //A.log("getChangeViewOptions() isOverviewPage:" + isOverviewPage + " otherUrl:" + otherUrl);

			if (isOverviewPage) {
      	      changeViewOptions += "<li><a href='" + Html.escAttr(bioregion.getThisPageTarget()) + "'>" + Html.esc(bioregion.getTitle()) + "</a></li>";
            } else {
      	      changeViewOptions += "<li><a href='" + Html.escAttr(otherUrl + (otherUrl != null && otherUrl.contains("?") ? "&" : "?") + bioregion.getParams()) + "'>" + Html.esc(bioregion.getTitle()) + "</a></li>";
            }
		  }
		}

        if (!"".equals(changeViewOptions) && !Project.isPerformanceSensitive(otherUrl)) changeViewOptions = Overview.selectSeparator + changeViewOptions;

        return changeViewOptions;
    }

    public String getChildrenListDisplay(String validOrLive, String overviewOrList, String rank) {
      String display = "";
      int i = 0;
      for (String country : getCountries()) {
        ++i;
        if (i == 1) display = "<br><b>Countries:</b> ";
        if (i > 1) display += ", ";
          display += "<a href='" + Html.escAttr(AntwebProps.getDomainApp() + "/country.do?name=" + HttpUtil.encode(country)) + "'>" + Html.esc(country) + "</a>";
      }
      //display += "</b>";
      return display;
    }    

}
