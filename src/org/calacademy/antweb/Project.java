package org.calacademy.antweb;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.Hashtable;
import java.util.Set;

import java.util.Comparator;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class Project extends LocalityOverview implements SpeciesListable, Comparable<Project>, Countable {      // was LocalityOverview

    private static Log s_log = LogFactory.getLog(Project.class);
    
    // This is the directory where all of the generated pages are put...
   // public static final String s_speciesListDir = "web/speciesList/";

    public static String ALLANTWEBANTS = "allantwebants";
    public static String WORLDANTS = "worldants";
    public static String FOSSILANTS = "fossilants";

    public static String PROJECT = "PROJECT";
    public static String GLOBAL = "GLOBAL";

    //public static String CALANTS = "calants";
    //public static String CZECHANTS = "czechants";

// calants and madants are the two projects where the project root is not the project_name
// minus "ants".  For instance, comorants has a root of comoros.  We should probably 
// change them all to be [root]ants and there is some logic in the app that assumes so
// and

    protected String name;
    protected String projectName; // used by template engine
    protected String title;
    protected String author;
    protected Date lastChanged;
    
    protected String contents;
    protected String specimenImage1;
    protected String specimenImage2;
    protected String specimenImage3;
    protected String specimenImage1Link;
    protected String specimenImage2Link;
    protected String specimenImage3Link;

    protected String authorBio;
    protected String authorImage;
    protected String authorImageTag;  // kind of transient

    protected String country;
    protected String adm1;
    protected String root;
    protected String locality;
    protected Connection connection;
    protected int numSubfamilies;
    protected int numGenera;
    protected int numSpecies;
    protected int numSpeciesImaged;
    protected String previewPage;
    protected boolean speciesListMappable;

    private Hashtable description;    
    
    // Geolocale dependent properties.  Add in: map?

    private String scope;
    private boolean isLive;
        
    protected String coords;
    protected String extent;

    protected String source;
    
    private String displayKey;
    
    public Project() {
        super();
    }    
    public Project(String project) {
        super();
        setName(project);
    }    
    
    // This relates to Overview.  Project behaves like the others.
    public Project getProject() {
      return this;
    }

    public boolean isCanShowSpeciesListTool(Login accessLogin) {
      A.log("isCanShowSpeciesListTool() isSpeciesListMappable:" + isSpeciesListMappable() + " useName:" + getUseName());
        //  || PROJECT.equals(getBioregion()   // Now is scope.
        return isSpeciesListMappable()
                && accessLogin != null
                && (accessLogin.getProjectNames().contains(getUseName()) || accessLogin.isAdmin());
    }

    public String toString() {
        return getName();
    }
        

    public static Comparator<Project> getNameComparator = new Comparator<>() {

        public int compare(Project a1, Project a2) {
            String name1 = a1.getTitle().toUpperCase();
            String name2 = a2.getTitle().toUpperCase();

            //ascending order
            return name1.compareTo(name2);
        }
    };        
        
    public int compareTo(Project other) {
        //A.log("compareTo() fullName:" + getFullName() + " vs " + other.getFullName());
        if (getName() == null) return 1;
        if (other.getName() == null) return 1;
        return getName().compareTo(other.getName());
        //return getTaxonName().compareTo(other.getTaxonName());
    }    

    
    public String makeSpecimenImageHtml(int num) {
        if (num == 1) return makeSpecimenImageHtml(getSpecimenImage1(), getSpecimenImage1Link());
        if (num == 2) return makeSpecimenImageHtml(getSpecimenImage2(), getSpecimenImage2Link());
        if (num == 3) return makeSpecimenImageHtml(getSpecimenImage3(), getSpecimenImage3Link());
        return null;
    }
    
    private String makeSpecimenImageHtml(String specimenInfo, String linkInfo) {
        
        // image links look like this:
        //<a href=description.do?rank=species&genus=&name=&project=>
          //<img class=border border=0 src=images/.jpg></a> 
        
        String thisString = "";
        if ((specimenInfo != null) && (specimenInfo.length() > 0)) {
            if ((linkInfo != null) && (linkInfo.length() > 0)) {
                thisString += "<a href=\""  + linkInfo + "\">";
            }

            thisString+= "<img src=\"" + AntwebProps.getImgDomainApp() + "/" + Project.getSpeciesListDir() + getRoot() + "/" + specimenInfo + "\">"; 

            if ((linkInfo != null) && (linkInfo.length() > 0)) {
                thisString += "</a>"; 
            }
        }
        
        return thisString;
    }

                
    private Object getSlotValue(String slot) {
        Object variable = null;
        try {
            Field thisField = Project.class.getDeclaredField(slot);            
            variable = thisField.get(this);
        } catch (Exception e) {
            s_log.error("getSlotValue() for slot:" + variable + " e:" + e);
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        }
        return variable;
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
    
    public String getAuthorImageTag() { return authorImageTag; }


    public Date getLastChanged() {
        return lastChanged;
    }
    public void setLastChanged(Date lastChanged) {
        this.lastChanged = lastChanged;
    }

 // implements LocalityOverview
    public String getLocality() {
        return locality;
    }
    public void setLocality(String locality) {
        this.locality = locality;
    }
        
    public static String getSpeciesListPath() {
        //s_log.warn("getSpeciesListPath() docRoot:" + AntwebProps.getDocRoot() + " speciesListDir:" + Project.getSpeciesListDir());
        return AntwebProps.getDocRoot() + Project.getSpeciesListDir();
    }

    public String getProjectName() {
      return this.projectName;
    }
    
	// SpeciesListable
    public String getKey() {
      return getName();
    }
    
    public String getName() {   // Will end with "ants"
        return name; 
    }
    public void setName(String name) {
        this.name = name;
        int antsIndex = name.indexOf("ants");
        if (antsIndex > 0) {
          this.projectName = name.substring(0, antsIndex);   // name sans "ants"
        } else {
          s_log.warn("setName() ants not found in name:" + name);
          if (AntwebProps.isDevMode()) AntwebUtil.logStackTrace();
        }
    }

    public String getRoot() {  // projectName without "ants"
      String name = getName();
      name = (new Formatter()).removeSpaces(name);
      name = name.toLowerCase();
      int antsIndex = name.indexOf("ants");
      if (antsIndex > 0) {
          name = name.substring(0, antsIndex);
      }
      return name;
    }

    // To implement SpeciesListable
    public String getType() { 
      return SpeciesListable.PROJECT; 
    }
    public void setType(String type) { // not used 
    }
    public boolean getIsUseChildren() {
      return false;
    }    

    public String getOverviewLink() {
      String link = AntwebProps.getDomainApp() + "/project.do?name=" + getKey();
      return link;    
    }
    public String getListLink() {
      String link = AntwebProps.getDomainApp() + "/taxonomicPage.do?rank=species&project=" + getName();
      return link;    
    }

    
/*
    public static String getLocalityName(String name) {
        // Currently not in use. Projects never correlate with countries or adm1.
    
        // To be used searching the country or adm1 fields of the specimen table.
        if (name == null) return null;
        
        Project project = ProjectMgr.getProject(name, false); // Do not default
        
        A.log("getLocalityName() name:" + name + " project:" + project);
        
        if (project == null) return null;
        
        if ("guianashieldants".equals(name)) return "Guyana";
        
        return null;
    }
*/
    
    public int getNumSpeciesImaged() {
        return numSpeciesImaged;
    }
    public void setNumSpeciesImaged(int numSpeciesImaged) {
        this.numSpeciesImaged = numSpeciesImaged;
    }

    public int getNumSubfamilies() {
        return numSubfamilies;
    }
    public void setNumSubfamilies(int numSubfamilies) {
        this.numSubfamilies = numSubfamilies;
    }

    public int getNumGenera() {
        return numGenera;
    }
    public void setNumGenera(int numGenera) {
        this.numGenera = numGenera;
    }

    public int getNumSpecies() {
        return numSpecies;
    }
    public void setNumSpecies(int numSpecies) {
        this.numSpecies = numSpecies;
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

    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    public String getContents() {
        return contents;
    }
    public void setContents(String contents) {
        this.contents = contents;
    }

    // This means we can use the Species List Tool.  Not going to load the specimen file.
	public boolean isSpeciesListMappable() {
        return getSpeciesListMappable();
    }
	public boolean getSpeciesListMappable() {
		return speciesListMappable;
	}
	public void setSpeciesListMappable(boolean speciesListMappable) {

       //s_log.warn("setSpeciesListMappable() " + speciesListMappable);	
		this.speciesListMappable = speciesListMappable;
	}    
	
    public boolean isAntProject() {
        return PROJECT.equals(getScope());
    }

/*    
    public boolean isGlobalProject() {
      if (GLOBAL.equals(getScope())) return true;
      return false;
    }
    public boolean isGeoProject() {
      return (! (isAntProject() || isGlobalProject()));
    }
*/

    public String getScope() {
        return scope;
    }
    public void setScope(String scope) {
        this.scope = scope;
    }
    
    public boolean getIsLive() {
        return isLive;
    }
    public void setIsLive(boolean isLive) {
        this.isLive = 	isLive;
    }

    public String getCoords() {
        return coords;
    }
    public void setCoords(String coords) {
        this.coords = coords;
    }
    

    public String getSource() {
        return source;
    }
    public void setSource(String source) {
        this.source = source;
    }
        
    public String getExtent() {
        return extent;
    }
    public void setExtent(String extent) {
        this.extent = extent;
    }

    public Connection getConnection() {
        return connection;
    }
    public void setConnection(Connection connection) {
        this.connection = connection;
    }
		
    public String getDisplayKey() {
      return this.displayKey;
    }
    public void setDisplayKey(String displayKey) {
      this.displayKey = displayKey;
    }
    
    // UseName for Project means the user specified projectname.  For instance, if the
    // projectName was comorosants, the useName could be comorosants or Comoros.
    public String getUseName() {
      if (getDisplayKey() != null && !"".equals(getDisplayKey())) return getDisplayKey();
      return getName();
    }
	    
    public String getPreviewPage() {
        return "/" + Project.getSpeciesListDir() + getRoot() + "/" + getRoot() + "-preview.jsp";
    }
    
    public String getRootName() {
      return Project.getRootName(getName());
    }
    
	public static String getRootName(String project) {  // projectName, or title will work
	    if (project == null) return null;
  	    project = (new Formatter()).removeSpaces(project);
        project = project.toLowerCase();

        if (project.contains("ants")) {
          int antsIndex = project.indexOf("ants");
          project = project.substring(0, antsIndex);
        }
        return project;
	}
	
	public static String getProjectName(String country) {
	  country = country.toLowerCase();
	  country = (new Formatter()).removeSpaces(country);
	  //if ("madagascar".equals(country)) return "madants";
      //if ("czech republic".equals(country)) return "czechants";
      String projectName = country + "ants";
      return projectName;	  
	}
	
	// Called from some JSP files.
    public static String getPrettyName(String project) {

        //s_log.warn("getPrettyName() project:" + project);
        // Project.java does the courtesy (currently) of prettyfying specimen list names...
        if (project == null) return null;
        if (project.contains("specimen")) {
          //Currently the pretty name is the same.  Should be Group name, for instance CAS Specimens.
          return project;
        }

        return Project.getTitleLookup(project);
    }    
    
// To be used sparingly.  Better to get the projectObj.getTitle()    
    public static String getTitleLookup(String projectName) {
      if (projectName == null) return null;
      //String projectName = projectName.toLowerCase();
      // Costa_Rica display name was messed up by this.  Maybe this better?  Mar 8, 2016.

      String projectTitle = "";
      Project project = ProjectMgr.getLiveProjectsHash().get(projectName);
      if (project != null) {
        return project.getTitle();
      } else {
        // This will happen for projectName: Nearctic, etc... or PROJECT
        //if (AntwebProps.isDevMode()) s_log.error("getTitleLookup() projectName:" + projectName + " project is null.");
        //AntwebUtil.logStackTrace();      
      }
      return projectTitle;
    }

    public boolean isAggregate() {
        return "Project".equals(getScope()) || "GLOBAL".equals(getScope());
    }
            
	public String getTag() {
      String projectUri = "/project.do?name=";
      // String taxonomicPageUri = "/taxonomicPage.do?rank=genus&project=";
      String tag = "<a href='" + AntwebProps.getDomainApp() + projectUri;
      tag += getName() + "'>" + getProjectName() + "</a>";
      return tag;	
	}		    


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
    
// ----- Implement Overviewable
   
    public String getTaxonSetTable() {
      return "proj_taxon";
    }
    public String getTable() {
        return "project";
    }

    public String getHeading() {
      return "Project";
    }
         
    public String getTargetDo() {
      return "project.do";
    }
    public String getPluralTargetDo() {
      return "projects.do";
    }       
    public String getThisPageTarget() {
      return AntwebProps.getDomainApp() + "/" + getTargetDo() + "?name=" + getUseName();
    }
    
    public String getDisplayName() {
      String displayName = null;
      if (getName() != null) displayName = getName();
      if (getTitle() != null) displayName = getTitle();
      //s_log.warn("getDisplayname() displayName:" + displayName + " code:" + getCode() + " title:" + getTitle() + " name:" + getName());
      return displayName;
    }
    public String getShortDisplayName() {
      //return getName();
      return getTitle();
    }

    public String getParams() {
      return "project=" + getUseName();   
    }      
    public String getSearchCriteria() {
      return "project=" + getUseName();   
    }          
    
    public String getFetchChildrenClause() {
  	  return ", proj_taxon where taxon.taxon_name = proj_taxon.taxon_name"
  	        + " and project_name = '" + getName() + "'";      
    }       

//    public String getChosenImageClause() {
    public String getSpecimenTaxonSetClause() {
      return "proj_taxon where proj_taxon.taxon_name = specimen.taxon_name "
            + " and proj_taxon.project_name = '"  + getName() + "'";
    }       
    
    public String getImageCountQuery(String taxonName) {
        String theQuery = " select image_count from proj_taxon " 
            + " where taxon_name = \"" + taxonName + "\" "
		    + " and project_name ='" + getName() + "'";
        return theQuery;    
    }
    
    public String getRecalcLink() {
      return "<a href='" + getThisPageTarget() + "&action=recalc'>Recalculate " + getName() + "</a>";    
    }        
    
    public TaxonSet getTaxonSet(String taxonName, String rank, Connection connection) {
      //TaxonSet taxonSet = null;
      
        TaxonSet taxonSet = new ProjTaxon(getName(), taxonName, rank);
        try {
            taxonSet.init(connection);
        } catch (SQLException e) {
            s_log.error("getTaxonSet(" + taxonName + "," + rank + ", connection) e:" + e);
        }
        return taxonSet;

      /*
      new ProjTaxon(this, taxonName, rank);
      try {
        taxonSet.init(connection);
      } catch (SQLException e) {
        s_log.error("getTaxonSet(taxonName, rank, conn) e:" + e);
      }
      */
      //return taxonSet;      
    }   
        
// --- Implement Countable ---
    
    // Not overview specific as are Museum, Bioregion and Geolocale.
    public String getCountSpecimensQuery() {
      String query = "select count(*) count, specimen.taxon_name taxonName from specimen " 
          + " join proj_taxon on specimen.taxon_name = proj_taxon.taxon_name " 
          + " where proj_taxon.project_name = '" + getName() + "'" 
          //+ StatusSet.getAndCriteria(getName())  // The weird worldants status taxa will not have specimens
          + " group by taxonName";
      return query;
    }    
    
/*
select count(*) count, specimen.taxon_name taxonName from specimen 
join proj_taxon on specimen.taxon_name = proj_taxon.taxon_name
where proj_taxon.project_name = 'introducedants'
group by taxonName
*/    
    
    public String getCountChildrenQuery(String rank) {
        String projectName = getName();
        String rankClause = " taxon.taxarank = '" + rank + "'";
	        if (Rank.SPECIES.equals(rank)) { 
          rankClause = " (taxon.taxarank = 'species' or taxon.taxarank = 'subspecies') ";
        }

        String query = "select count(taxon.parent_taxon_name) count, taxon.parent_taxon_name parentTaxonName from taxon "
          + " join proj_taxon pt on taxon.taxon_name = pt.taxon_name " 
          + " where " + rankClause
	      + "   and pt.project_name = '" + projectName + "'"
          + StatusSet.getAndCriteria(getName())
                //+ new StatusSet(StatusSet.ALL).getAndCriteria();
          + " group by parentTaxonName "
          + " order by pt.project_name";  
        return query;  
    }      
    
    public String getCountGrandChildrenQuery(String rank, String column) {
       
        String rankClause = " taxon.taxarank = '" + rank + "'";
	        if (Rank.SPECIES.equals(rank)) { 
          rankClause = " (taxon.taxarank = 'species' or taxon.taxarank = 'subspecies') ";
        }

        // parent is a genus
        String query = "select sum(pt." + column + ") sum, taxon.parent_taxon_name parentTaxonName from taxon " 
            + " join proj_taxon pt on taxon.taxon_name = pt.taxon_name " 
            + "  where " + rankClause
            + "   and pt.project_name = '" + getName() + "'"
            + StatusSet.getAndCriteria(getName())
                //+ new StatusSet(StatusSet.ALL).getAndCriteria();
            + "  group by parentTaxonName";
            
        return query;
    }    

    public String getUpdateCountSQL(String parentTaxonName, String columnName, int count) {        
        String updateCountSQL = "update proj_taxon set " + columnName + " = '" + count + "'" //, source='" + Source.SPECIMEN + "'" 
            + " where project_name = '" + getName() + "' and taxon_name = '" + parentTaxonName + "'";
        return updateCountSQL;
    }
    
    public String getTaxonImageCountQuery() {
       String query = "select s.taxon_name taxonName, s.family family, s.subfamily subfamily " 
           + ", s.genus genus, s.species species, sum(s.image_count) imageSum" 
           + " from specimen s join proj_taxon pt on s.taxon_name = pt.taxon_name " 
           + " where pt.project_name = '" + getName() + "'" 
           + " group by s.taxon_name, s.family, s.subfamily, s.genus, s.species";   
      return query;
    }

    
    public String getUpdateImageCountSQL(String taxonName, int sum) { //, String rank) {
        String updateSql = "update proj_taxon set image_count = " + sum 
            + " where project_name = '" + getName() + "' and taxon_name = '" + taxonName + "'";
        return updateSql;    
    }
    
    // --------------------------------------------------
    

    public String getChangeViewOptions(String taxonName, String otherUrl, Connection connection) {
        String changeViewOptions = "";
/*
        ArrayList<Project> antProjects = ProjectMgr.getAntProjects();
        
		for (Project project : antProjects) {
      	  changeViewOptions += "<li><a href='" + otherUrl + "&" + project.getParams() + "'>" + project.getTitle() + "</a></li>";
		}
*/
        return changeViewOptions;
    }    

    public static boolean isTaxonPage(String url) {
      if (url.contains("taxonomicPage.do")) return true;
      if (url.contains("browse.do")) return true;
      if (url.contains("images.do")) return true;
        return url.contains("description.do");
    }
    
    public static boolean isPerformanceSensitive(String url) {
        return url.contains("taxonomicPage.do?rank=species") && url.contains("images=true");
    }

    public static String getBoltonLi(String url) {
      if (!Project.isTaxonPage(url)) return "<li><a href=\"" + AntwebProps.getDomainApp() + "/project.do?name=worldants\">Bolton World Catalog</a></li>";
      if (Project.isPerformanceSensitive(url)) return "";
      return "<li><a href=\"" + url + "&project=worldants\">Bolton World Catalog</a></li>";
    }
    public static String getAllAntwebLi(String url) {
      //A.log("Project.getAllAntwebLi() url:" + url + " perf:" + Project.isPerformanceSensitive(url) + " taxonPage:" + isTaxonPage(url));

      if (!Project.isTaxonPage(url)) return "<li><a href=\"" + AntwebProps.getDomainApp() + "/project.do?name=allantwebants\">All Antweb Ants</a></li>";
      if (Project.isPerformanceSensitive(url)) return "";

      return "<li><a href=\"" + url + "&project=allantwebants\">All AntWeb Ants</a></li>";
    }    
    public static String getFossilLi(String url) {
      if (!Project.isTaxonPage(url)) return "<li><a href=\"" + AntwebProps.getDomainApp() + "/project.do?name=fossilants\">Fossil Ants</a></li>";
      if (Project.isPerformanceSensitive(url)) return "";
      return "<li><a href=\"" + url + "&project=fossilants\">Fossil Ants</a></li>";
    }

    public static boolean isProjectName(String projectName) {
      if (projectName == null || projectName.length() < 5) return false;
      if (projectName.contains(" ")) return false;
      if (!projectName.equals(projectName.toLowerCase())) return false;
        return "ants".equals(projectName.substring(projectName.length() - 4));
    }  
}


