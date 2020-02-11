package org.calacademy.antweb.geolocale;

import java.sql.*;
import java.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.*;

public class Geolocale extends LocalityOverview implements SpeciesListable, Countable, Comparable<Geolocale> {

    public int compareTo(Geolocale other) {
        if (getName() == null) return 1;
        if (other == null) {
          s_log.warn("compareTo() name:" + getName());
          return 1;
        }
        if (other.getName() == null) return 1;
        return getName().compareTo(other.getName());
    }

    private static Log s_log = LogFactory.getLog(Geolocale.class);

    private int id;
    protected String name;
    private String georank;
    private String georankType;
    protected boolean isValid;
    private boolean isUn;
    private String isoCode;
    private String iso3Code;
    private boolean isLive; // This is actually from project.  Do not set through UI.  Used in menus.
    private String source;
    private String validName;
    private String parent;
    private String region;
    private String subregion;
    private String country;
    private String bioregion;
    private String altBioregion;
    
    private String locality;
    private Timestamp created;
    private boolean isUseChildren;
    private boolean isUseParentRegion;
    private boolean isIsland;    

    protected String specimenImage1;
    protected String specimenImage2;
    protected String specimenImage3;
    protected String specimenImage1Link;
    protected String specimenImage2Link;
    protected String specimenImage3Link;
    protected String author;
    protected String authorBio;
    protected String authorImage;
    protected String authorImageTag;  // kind of transient    

    private int rev;

    private Hashtable description;
            
    private transient ArrayList<Geolocale> children = new ArrayList<Geolocale>();

    public static final String REGION = "region";
    public static final String SUBREGION = "subregion";
    public static final String COUNTRY = "country";
    public static final String ADM1 = "adm1";

    public String alternatives;
    
    public String type = SpeciesListable.COUNTRY;    
    

/*    
    // This gets set, but only for Country and Adm1 in which there is a 1-1 mapping.
    private transient Project project = null;
    public void setProject(Project project) {
      this.project = project;
    }
    public Project getProject() {
      return this.project;
    }
   */ 
    public Geolocale() {
    }    
    
    // SpeciesListable
    public String getKey() {
      //return getName();
      return "" + getId(); // Switched back on Oct 27, 2018. For Adm1, name is insufficient for a key. ?
      // If a problem shows up somewhere, it should be using getName. Yes?
    }

    public String getOverviewLink() {
      String link = AntwebProps.getDomainApp() + "/geolocale.do?id=" + getKey();
      return link;    
    }
    public String getListLink() {
      String link = AntwebProps.getDomainApp() + "/taxonomicPage.do?rank=species&geolocaleId=" + getKey();
      return link;    
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

    public String getTitle() {
      return getName();
    }

    public String getGeorank() {
        return georank;
    }
    public void setGeorank(String georank) {
        this.georank = georank;
    }
    public String getPrettyGeorank() {
      return georank;
    }

    public String getGeorankType() {
        return georankType;
    }
    public void setGeorankType(String georankType) {
        this.georankType = georankType;
    }

    public boolean isValid() { return getIsValid(); }
    public boolean getIsValid() {
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

    public String getParent() {
        return parent;
    }
    public void setParent(String parent) {
        this.parent = parent;
    }

    public String getRegion() {
        return region;
    }
    public void setRegion(String region) {
        this.region = region;
    }


    public String getSubregion() {
        return subregion;
    }
    public void setSubregion(String subregion) {
        this.subregion = subregion;
    }
        
    public String getCountry() {
        return country;
    }
    public void setCountry(String country) {
        this.country = country;
    }
    
    // in Geolocale
    public String getTheOneBioregion() {
        String returnVal = getBioregion();
        if (getAltBioregion() != null) returnVal = null;
         //A.log("getTheOneBioregion() name:" + getName() + " returnVal:" + returnVal + " bioregion:" + getBioregion() + " alt:" + getAltBioregion());
        return returnVal;
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

    public boolean isLive() { return getIsLive(); }
    public boolean getIsLive() {
        return isLive;
    }
    public void setIsLive(boolean isLive) {
        this.isLive = isLive;
    }

    public String getLocality() {  
      // These fired against specimen table.  What about subregion?  Bioregion?
      if (Georank.region.equals(getGeorank())) {
        return "";
      }
      if (Georank.subregion.equals(getGeorank())) {
        return "";
      }

      // Misguided, I think.  Currently all geolocale.is_use_parent_region = 0.
      //if (getIsUseParentRegion()) {
      //  return " region = '" + getRegion() + "'";
      //}

      if (Georank.country.equals(getGeorank())) {
        return " country = '" + getName() + "'";
      }
      if (Georank.adm1.equals(getGeorank())) {
        return " adm1 = '" + getName() + "'";
      }
      if (Georank.adm2.equals(getGeorank())) {
        return " adm2 = '" + getName() + "'";
      }      
      return "";
    }
    // In order to support the LocalityOverview
    public void setLocality(String locality) {
      // Does nothing.  We construct in the get method.
    //  this.locality = locality;
    }
    
    public Timestamp getCreated() {
        return created;
    }
    public void setCreated(Timestamp created) {
        this.created = created;
    }    

    public ArrayList<Geolocale> getChildren() {
      return children;
    }
    public void setChildren(ArrayList<Geolocale> children) {
      this.children = children;
    }        
    public ArrayList<Geolocale> getValidChildren() {
      ArrayList<Geolocale> validChildren = new ArrayList<Geolocale>();
      for (Geolocale child : getChildren()) {
        if (child.getIsValid()) {
          validChildren.add(child);
          //s_log.warn("getValidChildren() geolocale:" + getName() + " validChild:" + child);
        }
      }
      return validChildren;
    }
    
    public boolean getIsUseChildren() {
        return isUseChildren;
    }
    public void setIsUseChildren(boolean isUseChildren) {
        this.isUseChildren = isUseChildren;
    }    
    
    public boolean getIsUseParentRegion() {
        return isUseParentRegion;
    }
    public void setIsUseParentRegion(boolean isUseParentRegion) {
        this.isUseParentRegion = isUseParentRegion;
    }    
    
    // Also see GeolocaleMgr.isIsland().
    public boolean isIsland() {
        return getIsIsland();
    }
    public boolean getIsIsland() {
        return isIsland;
    }
    public void setIsIsland(boolean isIsland) {
        this.isIsland = isIsland;
    } 
        
    public int getRev() {
      return rev;
    }
    public void setRev(int rev) {
      this.rev = rev;
    }

	public String getTag() {
        return "<a href=\"" + getThisPageTarget() + "\">" + getName() + "</a>";
	}

    public String getRoot() {
      String name = getName();
      name = (new Formatter()).removeSpaces(name);
      name = name.toLowerCase();
      return name;
    }
    
    public String getAuthor() {
        return author;
    }
    public void setAuthor(String author) {
        this.author = author;
    }

    public String getAuthorBio() {
        return authorBio;
    }
    public void setAuthorBio(String authorBio) {
        this.authorBio = authorBio;
    }
    public String getSpecimenImage1() {
        return specimenImage1;
    }
    public void setSpecimenImage1(String specimenImage1) {
        this.specimenImage1 = specimenImage1;
    }
    public String getSpecimenImage2() {
        return specimenImage2;
    }
    public void setSpecimenImage2(String specimenImage2) {
        this.specimenImage2 = specimenImage2;
    }
    public String getSpecimenImage3() {
        return specimenImage3;
    }
    public void setSpecimenImage3(String specimenImage3) {
        this.specimenImage3 = specimenImage3;
    }
    

    public String getSpecimenImage1Link() {
        return specimenImage1Link;
    }
    public void setSpecimenImage1Link(String specimenImage1Link) {
        this.specimenImage1Link = specimenImage1Link;
    }

    public String getSpecimenImage2Link() {
        return specimenImage2Link;
    }
    public void setSpecimenImage2Link(String specimenImage2Link) {
        this.specimenImage2Link = specimenImage2Link;
    }

    public String getSpecimenImage3Link() {
        return specimenImage3Link;
    }
    public void setSpecimenImage3Link(String specimenImage3Link) {
        this.specimenImage3Link = specimenImage3Link;
    }

    public String getAuthorImage() {
        return authorImage;
    }
    public void setAuthorImage(String authorImage) {
        this.authorImage = authorImage;

        // This is so no broken image shows up on the project page prior to adding it.
        authorImageTag = "";
        if ((authorImage != null) && !(authorImage.equals(""))) {
          authorImageTag = "<img src=\"" + AntwebProps.getImgDomainApp() + "/" + Project.getSpeciesListDir() + getRoot() + "/" + getAuthorImage() + "\">";
          A.log("setAuthorImage() authorImageTag:" + authorImageTag);
        }        
    }        
        
    public boolean isCanShowSpecimenTaxa() {
      if ((isCountry() && !getIsUseChildren()) || isAdm1()) {
        return true;
      }
      return false;
    }
	public boolean isCountry() {
	  return Georank.country.equals(getGeorank());
	}

	public boolean isAdm1() {
	  return Georank.adm1.equals(getGeorank());
	}
    
    
    public String getLink() {  
      return "<a href='" + AntwebProps.getDomainApp() + "/geolocale.do?id=" + getId() + "'>" + getName() + "</a>";
    }

    
    public void setAlternatives(String alternatives) {
      this.alternatives = alternatives;
    }
    public String getAlternatives() {
      return this.alternatives;
    }
    
    public String getType() { 
      return type; 
    }
    public void setType(String type) {
      this.type = type;
    }

        
// --- Implement Overviewable  

    public String getTaxonSetTable() {
      return "geolocale_taxon";
    }      

    public String getHeading() {
      return ""; // Should not happen.  Subclasses.
    }
    public String getChildrenHeading() {
      return ""; // Should not happen.  Subclasses.
    }
    
    public String getTargetDo() {
      return ""; // Should not happen.  Subclasses.
    }
    public String getPluralTargetDo() {
      return ""; // Should not happen.  Subclasses.
    } 
    public String getThisPageTarget() {
	  String encodedName = java.net.URLEncoder.encode(getName());
      return AntwebProps.getDomainApp() + "/" + getTargetDo() + "?name=" + encodedName; 
    }
        
    public String getAltThisPageTarget() {
      return AntwebProps.getDomainApp() + "/" + getTargetDo() + "?id=" + getId();    
    }

	public String getThisPageTag() {
      String tag = "<a href=\"" + getThisPageTarget();
      tag += getDisplayName() + "\">" + getName() + "</a>";
      return tag;
	}
    
    public String getDisplayName() {
      String displayName = null;
      if (getName() != null) displayName = getName();
      //s_log.warn("getDisplayname() displayName:" + displayName + " code:" + getCode() + " title:" + getTitle() + " name:" + getName());
      return displayName;
    }
    public String getShortDisplayName() {
      //return new Integer(getId()).toString();
      return getName();
    }    

    public String getParams() {
      // This could be removed as all of the subclasses override this method.
      if ("region".equals(getGeorank())) return "regionName=" + getName();
      if ("subregion".equals(getGeorank())) return "subregionName=" + getName();
      if ("country".equals(getGeorank())) return "countryName=" + getName();
      if ("adm1".equals(getGeorank())) return "countryName=" + getParent() + "&adm1Name=" + getName();      

      //A.log("getParams() georank:" + getGeorank() + " id:" + getId());
      return "geolocaleId=" + getId();   
    }  

    public String getSearchCriteria() {
      return null;  // Will be overridden by subclasses.
    } 
    
    
    public String getFetchChildrenClause() {
  	  return ", geolocale_taxon where geolocale_taxon.taxon_name = taxon.taxon_name"
  	        + " and geolocale_id = '" + getId() + "'";      
    }   
    
//    public String getChosenImageClause() {
    public String getSpecimenTaxonSetClause() {
      return "geolocale_taxon where geolocale_taxon.taxon_name = specimen.taxon_name "
            + " and geolocale_taxon.geolocale_id = '"  + getId() + "'";
    }       
                
    public TaxonSet getTaxonSet(String taxonName, String rank, Connection connection) {
      TaxonSet taxonSet = new GeolocaleTaxon(this, taxonName, rank);
      try {
        taxonSet.init(connection);
      } catch (SQLException e) {
        s_log.error("getTaxonSet(taxonName, rank, conn) e:" + e);
      }
      return taxonSet;      
    }             
    
    public String getRecalcLink() {
      return "<a href='" + getThisPageTarget() + "&action=recalc'>Recalculate " + getName() + "</a>";    
    }

    public String getCountCrawlLink() {
      return "<a href='" + AntwebProps.getDomainApp() + "/utilData.do?action=countCrawl&num=" + getId() + "'>Count Crawl " + getName() + "</a>";
    }
    public String getGoogleMapFunctionLink() {
      String genLink = "<a href='" + AntwebProps.getDomainApp() + "/utilData.do?action=genGoogleMapFunction&num=" + getId() + "'>Generate Google Map " + getName() + "</a>.";
      String delLink = "[<a href='" + AntwebProps.getDomainApp() + "/utilData.do?action=delGoogleMapFunction&num=" + getId() + "'>x</a>]";
      return genLink + delLink;
    }
        
// ---------- Describable Interface ----------------

    public Hashtable getDescription() {
        if (description == null) description = new Hashtable();
        return description;
    }
    public void setDescription(Hashtable description) {
        this.description = description;
    }

    public boolean hasDescription(String title) {
      Set<String> keys = (Set<String>) getDescription().keySet();
      for (String key : keys) {
        if (key.equals(title)) return true;
      }
      return false;
    }
    
    
    // -------------------- Countable Implementation ---------------------
/*
    
    public String getCountSpecimensQuery() {
      String query = "select count(*) count, specimen.taxon_name taxonName from specimen " 
          + " join geolocale_taxon gt on specimen.taxon_name = gt.taxon_name " 
          + " where gt.geolocale_id = " + getId()
          + " and specimen.status in " + StatusSet.getCountables()
	          + " group by taxonName";
      return query;
    }        
*/ 

    // was getCountSpecimensLocalQuery() Now is the default.
    public String getCountSpecimensQuery() {
      String overviewCriteria = "";
      if (Georank.ADM1.equals(getGeorank())) overviewCriteria = " and specimen.adm1 = '" + getName() + "'"; 
      if (Georank.COUNTRY.equals(getGeorank())) overviewCriteria = " and specimen.country = '" + getName() + "'"; 
      String query = "select count(*) count, specimen.taxon_name taxonName from specimen " 
          + " join geolocale_taxon gt on specimen.taxon_name = gt.taxon_name " 
          + " where gt.geolocale_id = " + getId()
          + overviewCriteria
          + " and specimen.status in " + StatusSet.getCountables()
	          + " group by taxonName";
      return query;
    }        
   
    public String getCountChildrenQuery(String rank) {
        
        String rankClause = " taxon.rank = '" + rank + "'";
	        if (Rank.SPECIES.equals(rank)) { 
          rankClause = " (taxon.rank = 'species' or taxon.rank = 'subspecies') "; 
        }
      
        String query = "select count(taxon.parent_taxon_name) count, taxon.parent_taxon_name parentTaxonName from taxon "
          + " join geolocale_taxon gt on taxon.taxon_name = gt.taxon_name " 
          + " where " + rankClause
          + "   and gt.geolocale_id = " + getId()
          + " group by parentTaxonName ";
        return query;    
    }
    
// *** Do we need this? It was wrong until 2018-10-28. rankClause was just rank!
    public String getCountGrandChildrenQuery(String rank, String column) {
       
        String rankClause = " taxon.rank = '" + rank + "'";
	        if (Rank.SPECIES.equals(rank)) { 
          rankClause = " (taxon.rank = 'species' or taxon.rank = 'subspecies') "; 
        }
        
        // parent is a genus
        String query = "select sum(gt." + column + ") sum, taxon.parent_taxon_name parentTaxonName " 
            + " from taxon " 
            + " join geolocale_taxon gt on taxon.taxon_name = gt.taxon_name " 
            + "  where " + rankClause
            + "   and gt.geolocale_id = " + getId()
            + "  group by parentTaxonName";
        return query;
    }
    
    public String getUpdateCountSQL(String parentTaxonName, String columnName, int count) {
        String updateCountSQL = "update geolocale_taxon set " + columnName + " = '" + count + "'" //, source='" + Source.SPECIMEN + "'" 
            + " where geolocale_id = " + getId() 
            + " and taxon_name = '" + parentTaxonName + "'";
        return updateCountSQL;    
    }

/*    
    public String getOtherUpdateCountSQL(String parentTaxonName, String columnName, int count) {
        String updateCountSQL = "update proj_taxon set " + columnName + " = '" + count + "'" 
            + " where project_name = '" + getProjectName() + "'"
            + " and taxon_name = '" + parentTaxonName + "'";
        return updateCountSQL;    
    }
*/            
    public String getUpdateImageCountSQL(String taxonName, int sum) { //, String rank) {
        String updateSql = "update geolocale_taxon set image_count = " + sum 
            + " where geolocale_id = " + getId() 
            + " and taxon_name = '" + taxonName + "'";
        return updateSql;    
    }
    
    public String getImageCountQuery(String taxonName) {
        String theQuery = " select image_count from geolocale_taxon " 
            + " where taxon_name = \"" + taxonName + "\" "
		    + " and geolocale_id = " + getId();
        return theQuery;    
    }

    public String getTaxonImageCountQuery() {
      String query = "select s.taxon_name taxonName, s.family family, s.subfamily subfamily " 
           + ", s.genus genus, s.species species, sum(s.image_count) imageSum" 
           + " from specimen s join geolocale_taxon gt on s.taxon_name = gt.taxon_name " 
           + " where gt.geolocale_id = " + getId()
           + " group by s.taxon_name, s.family, s.subfamily, s.genus, s.species";    
      return query;
    }

// -------------------------

    
    public String toString() {
      return getName();
    }

    public String toLog() {
      return getName() + " isoCode:" + getIsoCode() + " iso3Code:" + getIso3Code() + " isIsland:" + isIsland();
    }

    
/*
    public boolean equals(Object name) {
      boolean isEqual = false;
      if (name instanceof String) {
        isEqual = getName().equals(name);
      } else {
        isEqual = super.equals(name);
      }
      if (AntwebProps.isDevMode()) if ("Suriname".equals(name)) s_log.warn("equals() name:" + name + " isEqual:" + isEqual);
      return isEqual;      
    }
*/

    // Used from browse pages.
    public String getChangeViewOptions(String taxonName, String otherUrl, Connection connection) {
        String changeViewOptions = "";
        GeolocaleDb geolocaleDb = new GeolocaleDb(connection);
        
		ArrayList<Geolocale> regions = geolocaleDb.getChildrenWithTaxon(taxonName, "region", null);

        //A.log("getChangeViewOptions() taxonName:" + taxonName + " otherUrl:" + otherUrl + " regions:" + regions);    

		for (Geolocale region : regions) {
      	  changeViewOptions += "<li><a href='" + otherUrl + "&" + region.getParams() + "'>" + region.getName() + "</a></li>";
		  ArrayList<Geolocale> subregions = geolocaleDb.getChildrenWithTaxon(taxonName, "subregion", region);
		  for (Geolocale subregion : subregions) {
			changeViewOptions += "<li>&nbsp;&nbsp;<a href='" + otherUrl + "&" + subregion.getParams() + "'>" + subregion.getName() + "</a></li>";
			ArrayList<Geolocale> countries = geolocaleDb.getChildrenWithTaxon(taxonName, "country", subregion);
			for (Geolocale country : countries) {
  			  changeViewOptions += "<li>&nbsp;&nbsp;&nbsp;&nbsp;<a href='" + otherUrl + "&" + country.getParams() + "'>" + country.getName() + "</a></li>";
			  ArrayList<Geolocale> adm1s = geolocaleDb.getChildrenWithTaxon(taxonName, "adm1", country);
			  for (Geolocale adm1 : adm1s) {
    			  changeViewOptions += "<li>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='" + otherUrl + "&" + adm1.getParams() + "'>" + adm1.getName() + "</a></li>";
			  }
			}
		  }
		}
        return changeViewOptions;
    } 

    // Used from taxonomic pages
    public String getChangeViewOptions(String otherUrl) {
        String changeViewOptions = "";
  	    boolean isOverviewPage = otherUrl.contains(getTargetDo());

        if (REGION.equals(getGeorank())) {
          ArrayList<Geolocale> regions = GeolocaleMgr.getRegions();
          changeViewOptions += "<li>Regions</li>";
          for (Geolocale region: regions) {
            if (!getName().equals(region.getName())) {            
 			  if (isOverviewPage) {
			    changeViewOptions += "<li>&nbsp;&nbsp;<a href='" + region.getThisPageTarget() + "'>" + region.getName() + "</a></li>";
			  } else {
                changeViewOptions += "<li>&nbsp;&nbsp;<a href='" + otherUrl + "&" + region.getParams() + "'>" + region.getName() + "</a></li>";
              }
            }
          }
        }

        if (SUBREGION.equals(getGeorank())) {
          ArrayList<Geolocale> subregions = GeolocaleMgr.getSubregions();
          changeViewOptions += "<li>Subregions</li>";
          for (Geolocale subregion: subregions) {
            if (!getName().equals(subregion.getName())) {
 			  if (isOverviewPage) {
			    changeViewOptions += "<li>&nbsp;&nbsp;<a href='" + subregion.getThisPageTarget() + "'>" + subregion.getName() + "</a></li>";
			  } else {
                changeViewOptions += "<li>&nbsp;&nbsp;<a href='" + otherUrl + "&" + subregion.getParams() + "'>" + subregion.getName() + "</a></li>";
              }
            }  
          }
        }
        
        if (COUNTRY.equals(getGeorank())) {
          ArrayList<Geolocale> countries = GeolocaleMgr.getValidCountries();
          changeViewOptions += "<li>Countries</li>";
          for (Geolocale country : countries) {
            if (!getName().equals(country.getName())) {
 			  if (isOverviewPage) {
			    changeViewOptions += "<li>&nbsp;&nbsp;<a href='" + country.getThisPageTarget() + "'>" + country.getName() + "</a></li>";
			  } else {
                changeViewOptions += "<li>&nbsp;&nbsp;<a href='" + otherUrl + "&" + country.getParams() + "'>" + country.getName() + "</a></li>";
              }
            }
          }
        }
        if (ADM1.equals(getGeorank()))  {
            changeViewOptions += "<li>Adm1</li>";
            Geolocale parent = GeolocaleMgr.getDeepGeolocale(getParent(), COUNTRY);
            if (parent == null) return "";
            ArrayList<Geolocale> adm1s = parent.getChildren();
            for (Geolocale adm1 : adm1s) {
              if (!getName().equals(adm1.getName())) {
   			    if (isOverviewPage) {
			      changeViewOptions += "<li>&nbsp;&nbsp;<a href='" + adm1.getThisPageTarget() + "'>" + adm1.getName() + "</a></li>";
			    } else {
                  changeViewOptions += "<li>&nbsp;&nbsp;<a href='" + otherUrl + "&" + adm1.getParams() + "'>" + adm1.getName() + "</a></li>";
                }
              }
            }
        }

        if (!"".equals(changeViewOptions) && !Project.isPerformanceSensitive(otherUrl)) changeViewOptions = Overview.selectSeparator + changeViewOptions;

        return changeViewOptions;
    }

/*
    public String getChildrenListDisplay() {      
      return getChildrenListDisplay(true);
    }

    public String getAdminChildrenListDisplay() {
      return getChildrenListDisplay(false);
    }

    public String getChildrenListDisplay(boolean showOnlyValid) {      
      if ("adm1".equals(getGeorank())) return "";
      Geolocale geolocale = GeolocaleMgr.getDeepGeolocale(getName(), getGeorank());
      if (geolocale == null) {
        //s_log.warn("getChildrenListDisplay() name:" + getName() + " georank:" + getGeorank() + " geolocale:" + geolocale);
        return "";
      }
      ArrayList<Geolocale> children = geolocale.getChildren();
      if (children.isEmpty()) return "";
      int i = 0;
      String display = "";
      for (Geolocale child : children) {
        if (showOnlyValid && !child.getIsValid()) continue;
        ++i;
        if (i == 1) display = "<b>" + getChildrenHeading() + ": ";
        if (i > 1) display += ", ";
        display += "<a href='" + child.getThisPageTarget() + "'>" + child.getName() + "</a>";
      }
      display += "</b><br>";
      return display;
    }    
*/

    public String getChildrenListDisplay(String validOrLive, String overviewOrList, String rank) {  
      if ("adm1".equals(getGeorank())) return "";
      Geolocale geolocale = GeolocaleMgr.getDeepGeolocale(getName(), getGeorank());
      if (geolocale == null) {
        //s_log.warn("getChildrenListDisplay() name:" + getName() + " georank:" + getGeorank() + " geolocale:" + geolocale);
        return "";
      }
      ArrayList<Geolocale> children = geolocale.getChildren();
      if (children.isEmpty()) return "";
      int i = 0;
      String display = "";
      for (Geolocale child : children) {
        if ("valid".equals(validOrLive) && !child.getIsValid()) continue;
        if ("live".equals(validOrLive) && !child.getIsLive()) continue;        
        if ("list".equals(overviewOrList) && (georank.equals("country") && !getIsUseChildren())) continue;
        ++i;
        if (i == 1) display = "<b>" + getChildrenHeading() + ":</b> ";
        if (i > 1) display += ", ";
        if ("overview".equals(overviewOrList))
          display += "<a href='" + child.getThisPageTarget() + "'>" + child.getName() + "</a>";
        if ("list".equals(overviewOrList))
          if (!georank.equals("country") || getIsUseChildren())
            display += "<a href='" + AntwebProps.getDomainApp() + "/taxonomicPage.do?rank=" + rank + "&" + child.getParams() + "'>" + child.getName() + "</a>";
      }
      //display += "</b>";
      return display;
    }    
/*
    public String getValidChildrenListDisplay() {      
      if ("adm1".equals(getGeorank())) return "";
      Geolocale geolocale = GeolocaleMgr.getDeepGeolocale(getName(), getGeorank());
      if (geolocale == null) {
        //s_log.warn("getChildrenListDisplay() name:" + getName() + " georank:" + getGeorank() + " geolocale:" + geolocale);
        return "";
      }
      ArrayList<Geolocale> children = geolocale.getChildren();
      if (children.isEmpty()) return "";
      int i = 0;
      String display = "";
      for (Geolocale child : children) {
        if (!child.getIsValid()) continue;
        ++i;
        if (i == 1) display = "<b>" + getChildrenHeading() + ": ";
        if (i > 1) display += ", ";
        display += "<a href='" + child.getThisPageTarget() + "'>" + child.getName() + "</a>";
      }
      display += "</b><br>";
      return display;
    }    
    public String getLiveChildrenListDisplay() {      
      if ("adm1".equals(getGeorank())) return "";
      Geolocale geolocale = GeolocaleMgr.getDeepGeolocale(getName(), getGeorank());
      if (geolocale == null) {
        //s_log.warn("getChildrenListDisplay() name:" + getName() + " georank:" + getGeorank() + " geolocale:" + geolocale);
        return "";
      }
      ArrayList<Geolocale> children = geolocale.getChildren();
      if (children.isEmpty()) return "";
      int i = 0;
      String display = "";
      for (Geolocale child : children) {
        if (!child.getIsLive()) continue;
        ++i;
        if (i == 1) display = "<b>" + getChildrenHeading() + ": ";
        if (i > 1) display += ", ";
        display += "<a href='" + child.getThisPageTarget() + "'>" + child.getName() + "</a>";
      }
      display += "</b><br>";
      return display;
    }    
*/    

    
}
