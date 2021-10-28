package org.calacademy.antweb;

import java.util.*;
import java.io.Serializable;
import java.sql.*;
import java.math.BigDecimal;

import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.sort.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Taxon implements Describable, Serializable, Comparable<Taxon> {

    public final static int MAX_DISPLAY = 2000;
      // May be overridden by a &limit= parameter in browse.do pages.

    public int compareTo(Taxon other) {
        //A.log("compareTo() fullName:" + getFullName() + " vs " + other.getFullName());
        if (getFullName() == null) return 1;
        if (other == null) {
          s_log.warn("compareTo() WST other is null. FullName:" + getFullName());
          return 1;
        }
        if (other.getFullName() == null) return 1;
        return getFullName().compareTo(other.getFullName());
        //return getTaxonName().compareTo(other.getTaxonName());
    }

    private static Log s_log = LogFactory.getLog(Taxon.class);

    protected Taxon parent;
    protected String name;
    //protected int taxonId;
    //protected int parentTaxonId;
    protected String parentTaxonName;
    
    protected String prettyName;
    protected String rank;
    protected String theXml;
    protected Hashtable description;
    protected Vector habitats;
    protected Vector microhabitats;
    protected Vector methods;
    protected String types;
    protected String elevations = "";
    protected String collectDateRange = "";
    protected ArrayList<Taxon> children;
    protected ArrayList similar;
    protected String similarComparisonString;
    protected String test;
    private Connection connection;

    protected String kingdomName;
    protected String phylumName;
    protected String className;
    protected String orderName;
    protected String family;
    protected String subfamily;
    protected String tribe;
    protected String genus;
    protected String subgenus;
    protected String speciesGroup;
    protected String species;
    protected String subspecies;

    protected String browserParams;
    protected Map map;
    protected String fullName;
    protected Hashtable images;
    protected boolean hasImages;
    protected String code;
    //protected String nextRank = "";
    protected int childrenCount;
    protected int childImagesCount;
    protected int imageCount;
    protected int hasImagesCount = 0;
    
    protected int similarCount;    
    protected Formatter myFormatter = new Formatter();
    protected String binomial = "";
    protected boolean fossil;
    protected boolean extant = false;
    protected String source = null;
    protected int lineNum = 0;
    protected String insertMethod = null;
    protected Timestamp created = null;
    protected String execTime = null;
    protected boolean isType = false;
    protected boolean isAntCat = false;
    protected boolean isPending = false;
    //protected String authorDate = "";

    protected int antcatId;
    protected String authorDate = "";
    protected String authorDateHtml = "";
    protected String authors = "";
    protected String year = "";
    protected String status = "";
    protected boolean isAvailable = false;
    protected String currentValidName = "";
    protected String currentValidTaxonName = "";
    protected String currentValidRank = "";
    protected String currentValidParent = "";
    protected boolean originalCombination = false;
    protected String wasOriginalCombination = "";
    protected String country = "";
    protected String bioregion = "";
    protected int holId;
    protected int groupId;

    private String chartColor = null;
    
    private String maleSpecimen = null;
    private String workerSpecimen = null;
    private String queenSpecimen = null;    

    private String bioregionMap;  // only for genera
    private String introducedMap; // only for species
    
    protected Vector<String> homonymAuthorDates = null;        
    //private ArrayList<Project> projects = null;
    private ArrayList<Country> countries = null;
    private ArrayList<Bioregion> bioregions = null;
    
    protected TaxonSet taxonSet;
    
    private String uploadDate = null;  // Used on the specimenReport.jsp    

    private transient ArrayList<String> countryList = null;
    private transient ArrayList<String> adm1List = null;

    public boolean equals(Object o) {
      String thisTaxonName = getTaxonName();
      if (o == null) return false;
      String thatTaxonName = ((Taxon) o).getTaxonName();
        return thisTaxonName.equals(thatTaxonName);
    }
    
    protected String seeAlso = null;
    protected String alsoDatabased = null;

    //private static String debugCode = null;
    private static String debugCode = "ponerinaethaumatomyrmex zeteki";
    
/*
    For Taxon construction, see BrowseAction.java:153.  It is a dirty process in that
    the outside class does all of the business of creating the taxon in a very messy
    manner.  
    
    Generally a getTaxonOfRank() method is called to get the right class type.  Then
    parameters are set on it.  Init() is probably called.  Eventually, a setTaxonomicInfo() 
    will be invoked upon the appropriate subclass.  Other method calls are optional.

    This is a good lesson in how to write object oriented code backwards.  Thanks Thau.
*/

/*
 // Replace with:  taxon = (new TaxonDb(connection)).getTaxon(taxonName);
    // Called 22 times from various Java classes
    public static Taxon getInfoInstance(Connection connection, String taxonName) {
        TaxonDb taxonDb = new TaxonDb(connection);

        if (taxonName.contains("be right back")) {
          s_log.error("getInfoInstance() Error.  Investigate.  taxonName:" + taxonName);
          AntwebUtil.logStackTrace();
          return null;
        }

        Taxon t = taxonDb.getInfoInstance(connection, "taxon", taxonName);

        // A.log("getInfoInstance() taxonName:" + taxonName + " taxon:" + t.getTaxonName());

        return t;
    }      
*/


    // Called from getInstance() above.
    public void finishInstance(Connection connection) throws SQLException {
            
        //A.log("getInstance() taxon:" + taxon.getClass() + " isExtant:" + taxon.isExtant() + " subfamily:" + taxon.getSubfamily());

        init(connection);
        setDescription(new DescEditDb(connection).getDescEdits(this, false)); // false is isManualEntry
        setHabitats(connection);
        setMicrohabitats(connection);
        setMethods(connection);
        setTypes(connection);
              
        setElevations(connection);
        setCollectDateRange(connection);

        //setGeolocales();
        setCountries(new GeolocaleTaxonDb(connection).getCountries(getTaxonName()));
        setBioregions(new BioregionTaxonDb(connection).getBioregions(getTaxonName()));
        
        setHomonymAuthorDates(connection);
    }

    public boolean isDummy() {
      return false;
    }

    // Called from Taxon, Family, Subfamily and Genus.
    public void init(Connection connection) throws SQLException {
        /* Beginning effort to consolidate initialization process.  Currently BrowseAction calls
           all sorts of methods to instantiate the taxon */

        String theQuery = "";
        String taxonName = null;

        Statement stmt = null;
        ResultSet rset = null;
        try {        
            taxonName = AntFormatter.escapeQuotes(getTaxonName());            
            theQuery = "select source, line_num, insert_method, created, fossil, antcat, pending " 
              + ", parent_taxon_name, image_count, type"
              + ", antcat_id, author_date, author_date_html, authors, year, status, available " 
              + ", current_valid_name, current_valid_rank, current_valid_parent, original_combination, was_original_combination "
              + ", country, bioregion "    
              + ", hol_id, access_group, chart_color "
              + " from taxon where taxon_name='" + taxonName + "'";

            // A.log("init() query:" + theQuery);
            stmt = DBUtil.getStatement(connection, "init()");
            
            rset = stmt.executeQuery(theQuery);
            while (rset.next()) {
                // should return only one record             
                setSource(rset.getString("source"));
                setLineNum(rset.getInt("line_num"));
                setInsertMethod(rset.getString("insert_method"));
                setCreated(rset.getTimestamp("created"));
                setIsFossil(rset.getInt("fossil") == 1);
                setIsType(rset.getInt("type") == 1);
                setIsAntCat(rset.getInt("antcat") == 1);
                setIsPending(rset.getInt("pending") == 1);

                setAntcatId(rset.getInt("antcat_id"));
                setAuthorDate(rset.getString("author_date"));
                setAuthorDateHtml(rset.getString("author_date_html"));
                setAuthors(rset.getString("authors"));
                setYear(rset.getString("year"));
                setStatus(rset.getString("status"));
                setIsAvailable(rset.getInt("available") == 1);
                setCurrentValidName(rset.getString("current_valid_name"));
                setCurrentValidRank(rset.getString("current_valid_rank"));
                setCurrentValidParent(rset.getString("current_valid_parent"));                
                setIsOriginalCombination(rset.getInt("original_combination") == 1);
                setWasOriginalCombination(rset.getString("was_original_combination"));
                //setCountry(rset.getString("country"));
                //setBioregion(rset.getString("bioregion"));
                
                setParentTaxonName(rset.getString("parent_taxon_name"));                
                setImageCount(rset.getInt("image_count"));
                setHolId(rset.getInt("hol_id"));
                setGroupId(rset.getInt("access_group"));

                setChartColor(rset.getString("chart_color"));
            }

            if (Rank.SUBFAMILY.equals(getRank()) || isSpeciesOrSubspecies()) {
              // if species we use "=" if subfamily we use "like". Genera are fetched with an overview specific child speciesStr.
              ImagePickDb imagePickDb = new ImagePickDb(connection);
              setDefaultSpecimen(Caste.MALE, imagePickDb.getDefaultSpecimen(Caste.MALE, this));
              setDefaultSpecimen(Caste.WORKER, imagePickDb.getDefaultSpecimen(Caste.WORKER, this));
              setDefaultSpecimen(Caste.QUEEN, imagePickDb.getDefaultSpecimen(Caste.QUEEN, this));
              //A.log("init() taxonName:" + taxonName + " class:" + this.getClass() + " workerDefault:" + getDefaultSpecimen(Caste.WORKER));
            }            
            TaxonPropDb taxonPropDb = (new TaxonPropDb(connection));
            if (Rank.GENUS.equals(getRank())) {
              setBioregionMap(taxonPropDb.getBioregionMap(taxonName));
            }
            if (isSpeciesOrSubspecies()) {
   			  //A.log("init() setting Introduced. Is that OK?");
  			  setIntroducedMap(taxonPropDb.getIntroducedMap(taxonName));        
            }
			
            //A.log("init() taxonName:" + taxonName + " defaultSpecimen:" + getDefaultSpecimen() + " bioregionMap:" + getNativeBioregionMap());            
            //s_log.warn("init() taxonName:" + taxonName + " isFossil;" + isFossil);
        } catch (SQLException e) {
            s_log.error("init() taxonName:" + taxonName + " e:" + e + " theQuery:" + theQuery);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "init()");
        }
    }
    
    public void initTaxonSet(Connection connection, Overviewable overview) {
        // Overridden by Specimen only.

        if (taxonSet == null) { 
          TaxonSet taxonSet = overview.getTaxonSet(getTaxonName(), rank, connection);
          
          //if ("Mayotte".equals(overview.getName())) A.log("initTaxonSet() overview:" + overview + " class:" + taxonSet.getClass() + " rank:" + rank + " taxonSet:" + taxonSet + " size:" + taxonSet.getNextSubtaxon());

          setTaxonSet(taxonSet);
        }
    }

    //public ProjTaxon getProjTaxon() {
    //  return (ProjTaxon) getTaxonSet();
    //}    
    public TaxonSet getTaxonSet() {

      if (taxonSet == null) {
        // This is a backup ass covering maneuver in case the server does not set a Taxon's projTaxon,
        //   to avoid a null pointer exception.
        //s_log.error("getProjTaxon() taxonName:" + getTaxonName() + " rank:" + getRank());
        return new ProjTaxon("allantwebants", "formicidae", "family");
      }

      // Taxons only have one ProjTaxon at a time, depending on what was selected at request time, on the server.
      return taxonSet;
    }
    // Was setProjTaxon()
    public void setTaxonSet(TaxonSet taxonSet) {
        this.taxonSet = taxonSet;
    }
    
    
    public void setCountryList(ArrayList<String> countryList) {
      this.countryList = countryList;
    }
    public ArrayList<String> getCountryList() {
      return this.countryList;
    }
    public void setAdm1List(ArrayList<String> adm1List) {
      this.adm1List = adm1List;
    }
    public ArrayList<String> getAdm1List() {
      return this.adm1List;
    }
    public void setBioregionMap(String bioregionMap) {
      this.bioregionMap = bioregionMap;
    }
    public String getBioregionMap() {
      return this.bioregionMap;
    }     
    public ArrayList<Bioregion> getNativeBioregions() {
		String bioregionMap = getBioregionMap();
		return TaxonPropMgr.getNativeBioregionList(bioregionMap);
        //return bioregionsStr;
    }
    
    public void setIntroducedMap(String introducedMap) {
      this.introducedMap = introducedMap;
    }
    public String getIntroducedMap() {
      return this.introducedMap;
    }
    public String getIntroducedStr() {
        // True means native! Counter-intuitive.
		String introducedMap = getIntroducedMap();
		A.log("getIntroducedStr() introducedMap:" + introducedMap);
		String introducedStr = TaxonPropMgr.getNonNativeBioregionsStr(introducedMap);
		return introducedStr;
    }
    public String getNativeStr() {
        // True means native! Counter-intuitive.
		String introducedMap = getIntroducedMap();
		//A.log("getNativeStr() introducedMap:" + introducedMap);
		String nativeStr = TaxonPropMgr.getNativeBioregionsStr(introducedMap);
		return nativeStr;
    }
    
    public String getFullName() {  // !! What?  get and set are operating on different elements.  Thau!
        // Overridden
        return getName();
    }


    public String getPrettyConciseName() {
      String name = getPrettyName();
      try {
        name = removeParens(name);
      } catch (java.lang.OutOfMemoryError e) {
        s_log.warn("getPrettyConciseName() name:" + name + " e:" + e);
      }
      return name;
    }

    public String removeParens(String name) {
      if (name == null) return null;

      // remove everything inside the parens and any extra spaces left over
      // return name.replaceAll("\\(.*?\\)", "").replaceAll("\\s+", " ");

      int i = 0;
      while (name.contains("(") && name.contains(")")) {
        ++i;
        if (i > 5) break;
        int lParenPos = name.indexOf("(");      
        int rParenPos = name.indexOf(")");
        name = name.substring(0, lParenPos) + name.substring(rParenPos + 1).trim();
      }
      return name;
    }

    public String getPrettyTaxonName() {
      return getPrettyName();
    }
    // Also see: getPrettyTaxonName(String taxonName)  below
    public String getPrettyName() {
        // So, for instance, myrmicinaecrematogaster aberrans assmuthi  =  Crematogaster aberrans assmuthi 

        if (prettyName == null) {
            setPrettyName();
        }
        //A.log("getPrettyName() prettyName:" + prettyName);
        return prettyName;
    }

    public void setPrettyName() {
        if (Rank.SPECIMEN.equals(getRank())) {
            this.prettyName = getFullName().toUpperCase();
        } else {
            this.prettyName = new Formatter().capitalizeFirstLetter(getFullName());
            if ((subgenus != null) && (subgenus.length() > 0)) {
                this.prettyName = new Formatter().capitalizeSubgenus(this.prettyName);
            }
        }
        //A.log("setPrettyName() rank:" + getRank() + " prettyName:" + this.prettyName + " subgenus:" + subgenus + " fullName:" + getFullName());
    }
    
    public static String getNoSubfamilyTaxonName(String taxonName) {
      // The Antcat current_valid_name does not include subfamily
      String noSubfamilyTaxonName = null;
      int inaeIndex = taxonName.indexOf("inae");
      
      if ((inaeIndex == taxonName.length() - 4) || ("incertae_sedis".equals(taxonName))) {
        // This is a subfamily.
        return taxonName;
      }
      
      if (taxonName.contains("inae")) {
        noSubfamilyTaxonName = taxonName.substring(inaeIndex + 4);
      }
      if (taxonName.contains("incertae_sedis")) {
        noSubfamilyTaxonName = taxonName.substring(15);
      }
      A.log("taxonName:" + taxonName + " noSubfamilyTaxonName:" + noSubfamilyTaxonName);
      return noSubfamilyTaxonName;      
    }
    
    public String getLongPrettyTaxonName() {
      return Taxon.getLongPrettyTaxonName(getTaxonName());
    }
    public static String getLongPrettyTaxonName(String taxonName) {
      String longPrettyTaxonName = taxonName;
      int inaeIndex = taxonName.indexOf("inae");
      if (taxonName.contains("inae")) {
        longPrettyTaxonName = (new Formatter()).capitalizeFirstLetter(taxonName.substring(0, inaeIndex + 4)) + " " + (new Formatter()).capitalizeFirstLetter(taxonName.substring(inaeIndex + 4));
      }
      if (taxonName.contains("incertae_sedis")) {
        longPrettyTaxonName = "Incertae Sedis " + (new Formatter()).capitalizeFirstLetter(taxonName.substring(15));
      }
      //A.log("taxonName:" + taxonName + " prettyTaxonName:" + prettyTaxonName);
      return longPrettyTaxonName;
    }

    public String getTaxonNameDisplay() {
      return Taxon.getPrettyTaxonName(getTaxonName());
    }    

    public static String getDisplayName(String taxonName) {
      return Taxon.displayTaxonName(taxonName);    
    }
    public static String displayTaxonName(String taxonName) {
      return Taxon.getPrettyTaxonName(taxonName);
    }

    // Not to be confused with Specimen.getTaxonPrettyName() or getPrettyTaxonName() above.
    public static String getPrettyTaxonName(String taxonName) {
      // This is preferred over displayTaxonName(taxonName)
      // myrmicinaecrematogaster aberrans assmuthi  =  Crematogaster aberrans assmuthi
      if (taxonName == null) return "";
      String prettyTaxonName = taxonName;
      int inaeIndex = taxonName.indexOf("inae");

      if ((inaeIndex == taxonName.length() - 4) || ("incertae_sedis".equals(taxonName))) {
        // This is a subfamily.
        return (new Formatter()).capitalizeFirstLetter(taxonName);
      }
      if (taxonName.contains("inae)")) {
        prettyTaxonName = taxonName.substring(inaeIndex + 5);
      } else if (taxonName.contains("inae")) {
        prettyTaxonName = taxonName.substring(inaeIndex + 4);
      }
      if (taxonName.contains("incertae_sedis")) {
        prettyTaxonName = taxonName.substring(14);
      }
      prettyTaxonName = prettyTaxonName.trim();      
      prettyTaxonName = (new Formatter()).capitalizeFirstLetter(prettyTaxonName);
      //A.log("taxonName:" + taxonName + " prettyTaxonName:" + prettyTaxonName);
      return prettyTaxonName;
    }

    // Works for genus and up.
    public static String getTaxonNameFromPrettyName(String prettyName) {
      if ("Formicidae".equals(prettyName)) return "formicidae";
      
      String lowerName = prettyName.toLowerCase();
      
      // Maybe a subfamily?
      if (Subfamily.isValidAntSubfamily(lowerName)) return lowerName;

      // Maybe a genus?
      if (!prettyName.contains(" ")) {
        Genus genus = TaxonMgr.getGenusFromName(lowerName);
        if (genus != null) return genus.getTaxonName();
      }

      // Maybe a species or subspecies?
      String[] splitNames = lowerName.split(" ");
      String genusName = splitNames[0];
      Genus genus = TaxonMgr.getGenusFromName(genusName);

      //A.log("getTaxonNameFromPrettyName() prettyName:" + prettyName + " genus:" + genus + " genusName:" + genusName);

      if (genus != null) {
        return genus.getSubfamily() + lowerName;
      }

      A.log("getTaxonFromPrettyName() not found:" + prettyName);
      return null;
    }

    // Works for genus and up.
    public static String getRankFromTaxonName(String prettyName) {
      if ("Formicidae".equals(prettyName)) return Rank.FAMILY;
      
      String lowerName = prettyName.toLowerCase();
      
      // Maybe a subfamily?
      if (Subfamily.isValidAntSubfamily(lowerName)) return Rank.SUBFAMILY;

      // Maybe a genus?
      if (!prettyName.contains(" ")) {
        Genus genus = TaxonMgr.getGenusFromName(lowerName);
        if (genus != null) return Rank.GENUS;
      }

      // Maybe a species or subspecies?
      String[] splitNames = lowerName.split(" ");
      String genusName = splitNames[0];
      Genus genus = TaxonMgr.getGenusFromName(genusName);

      if (genus != null) {
        return Rank.GENUS;
      }

      return Rank.SPECIES;
    }
    
    public String getPrettyFamily() {
      if (family == null) return "";
      return Formatter.initCap(family);
    }
    
    public String getPrettySubfamily() {
      if (subfamily == null) return "";
      return Formatter.initCap(subfamily);
    }

    public String getPrettyGenus() {
      if (genus == null) return "";
      return Formatter.initCap(genus);
    }

    public String getPrettySpecies() {
      if (species == null) return "";
      return species;
    }

    public String getPrettySubspecies() {
      if (subspecies == null) return "";
      return subspecies;
    }

    public static String displaySubfamilyGenus(String subfamily, String genus) {
      String displaySubfamilyGenus = Formatter.initCap(subfamily) + " " + Formatter.initCap(genus);
      //A.log("taxonName:" + taxonName + " prettyTaxonName:" + prettyTaxonName);
      return displaySubfamilyGenus;        
    }
    
    public static String displaySubfamilyGenus(String subfamilyGenus) {
      // This subfamilyGenus is a taxonName.  For instance: incertis_sedisnoonilla
      String displaySubfamilyGenus = subfamilyGenus;
      String subfamily = "";
      String genus = "";
      int inaeIndex = subfamilyGenus.indexOf("inae");
      if (subfamilyGenus.contains("inae")) {
        subfamily = subfamilyGenus.substring(0, inaeIndex + 4);
        genus = subfamilyGenus.substring(inaeIndex + 4);
      }
      if (subfamilyGenus.contains("incertae_sedis")) {
        subfamily = subfamilyGenus.substring(0, 15);
        genus = subfamilyGenus.substring(15);
      }
      displaySubfamilyGenus = Formatter.initCap(subfamily) + " " + Formatter.initCap(genus);
      //A.log("taxonName:" + taxonName + " prettyTaxonName:" + prettyTaxonName);
      return displaySubfamilyGenus;    
    }
   
    public static String displaySubfamilyGenusLinkToGenus(String subfamily, String genus) {
      String displaySubfamilyGenus = Formatter.initCap(subfamily)  + " <a href='" + AntwebProps.getDomainApp() + "/genus=" + genus + "'>" + Formatter.initCap(genus) + "</a>";
      //A.log("taxonName:" + taxonName + " prettyTaxonName:" + prettyTaxonName);
      return displaySubfamilyGenus;        
    }
        
    public static String displaySubfamilyGenusLinkToGenus(String subfamilyGenus) {
      //just like displaySubfamilyGenus but hyperlinks only the genus without specifying subfamily.
      
      String displaySubfamilyGenus = subfamilyGenus;
      String subfamily = "";
      String genus = "";
      int inaeIndex = subfamilyGenus.indexOf("inae");
      if (subfamilyGenus.contains("inae")) {
        subfamily = subfamilyGenus.substring(0, inaeIndex + 4);
        genus = subfamilyGenus.substring(inaeIndex + 4);
      }
      if (subfamilyGenus.contains("incertae_sedis")) {
        subfamily = subfamilyGenus.substring(0, 15);
        genus = subfamilyGenus.substring(15);
      }
      displaySubfamilyGenus = Formatter.initCap(subfamily) + " <a href='" + AntwebProps.getDomainApp() + "/genus=" + genus + "'>" + Formatter.initCap(genus) + "</a>";
      //A.log("taxonName:" + taxonName + " prettyTaxonName:" + prettyTaxonName);
      return displaySubfamilyGenus;    
    }    
    
    public void setHomonymAuthorDates(Connection connection) throws SQLException {
      Vector<String> homonymAuthorDates = new Vector<>();
        String taxonName = getTaxonName();
      Statement stmt = null;
      ResultSet rset = null;
      try {
        String query = "select author_date " 
            + " from homonym " 
            + " where taxon_name = '" + taxonName + "'";
        stmt = DBUtil.getStatement(connection, "setHomonymAuthorDates()");
        rset = stmt.executeQuery(query);
        //A.log("setHomonymAuthorDates() query:" + query);
        while (rset.next()) {
            String authorDate = rset.getString("author_date");
            homonymAuthorDates.add(authorDate);
        }
        //A.log("setHomonymAuthorDates() homonymAuthorDates:" + homonymAuthorDates);        
      } finally {
        DBUtil.close(stmt, rset, this, "setHomonymAuthorDates()");
      }
      this.homonymAuthorDates = homonymAuthorDates;
    }

    public Vector<String> getHomonymAuthorDates() {
      return this.homonymAuthorDates;
    }

    public String getAlsoDatabased() {
      return alsoDatabased;
    }
    
    public void setAlsoDatabased(Connection connection) throws SQLException {
    
      if (AntwebProps.isDevMode()) {
        s_log.warn("setAlsoDatabased()");
        AntwebUtil.logStackTrace();
      }

      // Was used by SetSeeAlso()
      String seeAlsoSynonyms = "";
      String taxonName = getTaxonName();
      Statement stmt = null;
      ResultSet rset = null;
      try {
       // String synTaxonName = Taxon.getNoSubfamilyTaxonName(getTaxonName());

        String query = "select taxon.current_valid_name " 
            + " from taxon " 
            + " where taxon.taxon_name = '" + taxonName + "'"
            + "   and status = 'synonym'"
          ;
        stmt = DBUtil.getStatement(connection, "setSeeAlsoSynonyms()");
        rset = stmt.executeQuery(query);
        //A.log("setSeeAlsoSynonyms() query:" + query);
        while (rset.next()) {
            String currentValidName = rset.getString("taxon.current_valid_name");
            if (taxonName.equals(currentValidName)) continue;
            if (!"".equals(seeAlsoSynonyms)) {
              seeAlsoSynonyms += ", ";
            }
            if (!"subfamily".equals(getRank())) {
              currentValidName = getSubfamily() + currentValidName;
            }
            seeAlsoSynonyms += "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + currentValidName + "'>" + Taxon.displayTaxonName(currentValidName) + "</a>";
        }
        //A.log("setSeeAlsoSynonyms() seeAlsoSynonyms:" + seeAlsoSynonyms);        
        //return seeAlsoSynonyms;
        if (!"".equals(seeAlsoSynonyms))
          alsoDatabased = seeAlsoSynonyms;
      } finally {
        DBUtil.close(stmt, rset, this, "setSeeAlsoSynonyms()");
      }
    }
    
    public String getSeeAlso() {
      return seeAlso;
    }    
    
    public void setSeeAlso()  throws SQLException {
        // Overridden by Species.  
    }    
    

    public String getDetails() {
      String taxonString = "kingdom:" + getKingdomName() + " phylum:" + getPhylumName() 
        + " class:" + getClassName() + " order:" + getOrderName()
        + " family:" + getFamily() + " subfamily:" + getSubfamily() 
        + " tribe:" + getTribe() + " genus:" + getGenus()
        + " subgenus:" + getSubgenus() + " speciesgroup:" + getSpeciesGroup()
        + " species:" + getSpecies() + " subspecies:" + getSubspecies();
      return "{" + taxonString + "}";
    }
  
    public String getDetailsComplete() {
      return "taxonName:{" + getTaxonName() + "} rank:" + getRank() + " " + getDetails();    
    }

    public static Taxon getTaxonOfRank(String rank) {
        /* Called by GenericSearchResults.java and FieldGuideAction.java */
        Taxon taxon = null;
        if (Rank.KINGDOM.equals(rank)) {
            taxon = new Kingdom();
        } else if (Rank.PHYLUM.equals(rank)) {
            taxon = new Phylum();
        } else if (Rank.PHYLUM.equals(rank)) {
            taxon = new org.calacademy.antweb.Class();
        } else if (Rank.ORDER.equals(rank)) {
            taxon = new Order();
        } else if (Rank.FAMILY.equals(rank)) {
            taxon = new Family();
        } else if (Rank.SUBFAMILY.equals(rank)) {
            taxon = new Subfamily();
        } else if (Rank.TRIBE.equals(rank)) {
            taxon = new Tribe();
        } else if (Rank.GENUS.equals(rank)) {
            taxon = new Genus();
        } else if (Rank.SPECIES.equals(rank)) {
            taxon = new Species();
        } else if (Rank.SUBSPECIES.equals(rank)) {
            taxon = new Subspecies();
        } else if (Rank.SPECIMEN.equals(rank)) {
            taxon = new Specimen();
        } else {
            return null;
        }
        taxon.setRank(rank);
        return taxon;
    }

        public static Taxon getTaxonOfRank(String subfamily, String genus, String species, String subspecies) {
          return getTaxonOfRank(subfamily, genus, null, species, subspecies);
        }

        public static Taxon getTaxonOfRank(String subfamily, String genus, String subgenus, String species, String subspecies) {
        Taxon taxon = null;
        if ((genus != null) && (species != null) && (subspecies != null)) {
            taxon = new Subspecies();
            //taxon.setName(subspecies);
            taxon.setRank("subspecies");
            taxon.setSubfamily(subfamily);
            taxon.setGenus(genus);
            taxon.setSubgenus(subgenus);
            taxon.setSpecies(species);
            taxon.setSubspecies(subspecies);
        } else if ((genus != null) && (species != null)) {
            taxon = new Species();
            //taxon.setName(species);
            taxon.setRank("species");
            taxon.setSubfamily(subfamily);
            taxon.setGenus(genus);
            taxon.setSubgenus(subgenus);
            taxon.setSpecies(species);
        } else if (genus != null) {
            taxon = new Genus();
            //taxon.setName(genus);
            taxon.setRank("genus");
            taxon.setSubfamily(subfamily);
            taxon.setGenus(genus);
        } else if (subfamily != null) {
            taxon = new Subfamily();
            //taxon.setName(subfamily);
            taxon.setRank("subfamily");
            taxon.setSubfamily(subfamily);
        }
        return taxon;
    }

    
    public String getRank() {
        return rank;
    }
    public void setRank(String rank) {
        this.rank = rank;
    }
    // Convenience method
    public boolean isSpeciesOrSubspecies() {
      return "species".equals(getRank()) || "subspecies".equals(getRank());
    }

    /** Wacky code here Thau.  I (Mark) moved this code from Utility class and simplified.  Further
      work is to get rid of the inane hashtable business. */
    public String getTaxonName() {
      // This is what gets persisted in the taxon_name field of the taxon table.


      if (getRank() != null) {
        String returnVal = null;
        if (getRank().equals("kingdom")) returnVal = getKingdomName(); 
        if (getRank().equals("phylum")) returnVal = getPhylumName(); 
        if (getRank().equals("class")) returnVal = getClassName(); 
        if (getRank().equals("order")) returnVal = getOrderName(); 
        if (getRank().equals("family")) returnVal = getFamily(); 
        if (returnVal != null) {
          //A.log("getTaxonName() rank:" + getRank() + " returnVal:" + returnVal);
          //AntwebUtil.logStackTrace();
          return returnVal;
        }
      }
      
      Hashtable taxonomy = new Hashtable();
    
      if (getFamily() != null) taxonomy.put("family", getFamily());
      if (getSubfamily() != null) taxonomy.put("subfamily", getSubfamily());
// if (getTribe() != null) taxonomy.put("tribe", getTribe());
      if (getGenus() != null) taxonomy.put("genus", getGenus());
      if (getSpecies() != null) taxonomy.put("species", getSpecies());
      if (getSubspecies() != null) taxonomy.put("subspecies", getSubspecies());
  //    if (getSubgenus() != null) taxonomy.put("subgenus", getSubgenus());
  //    if (getSpeciesGroup() != null) taxonomy.put("speciesgroup", getSpeciesGroup());

      return makeName(taxonomy);
    }

    public void setName(String name) {  } // implements Describable


    private String makeName(Hashtable item) {

      if (item == null) return null;
      
      StringBuffer sb = new StringBuffer();

      if (validNameKey("subfamily", item)) {
        sb.append((String) item.get("subfamily"));
      }
 
      if (validNameKey("genus", item)) {
        sb.append((String) item.get("genus"));
      }

      if (validNameKey("species", item)) {
        sb.append(" " + (String) item.get("species"));     
      }
 
      if (validNameKey("subspecies", item)) {
        sb.append(" " + (String) item.get("subspecies"));     
      }

      return sb.toString();
    }

    public String getSimpleName() {
        // Used in site_nav.jsp.  Useful for species or specimen only.
        String simpleName = (new Formatter()).capitalizeFirstLetter(getGenus()) + " " + getSpecies();
        if ((getSubspecies() != null) && !"".equals(getSubspecies())) simpleName += " " + getSubspecies();
        return simpleName;
    }
  
    public String getParentTaxonName() {
        return parentTaxonName;
    }
    public void setParentTaxonName(String parentTaxonName) {
        this.parentTaxonName = parentTaxonName;
    }

    public String getKingdomName() {
        return kingdomName;
    }
    public void setKingdomName(String kingdomName) {
        this.kingdomName = kingdomName;
    }
    public String getPhylumName() {
        return phylumName;
    }
    public void setPhylumName(String phylumName) {
        this.phylumName = phylumName;
    }
    public String getClassName() {
        return className;
    }
    public void setClassName(String className) {
        this.className = className;
    }
    public String getOrderName() {
        return orderName;
    }
    public void setOrderName(String orderName) {
        this.orderName = orderName;
    }
    public String getFamily() {
        return family;
    }
    public void setFamily(String family) {
        this.family = family;
    }
 
    public String getSubfamily() {
        return subfamily;
    }
    public void setSubfamily(String subfamily) {
        this.subfamily = subfamily;
    }
    
    public String getTribe() {
        return tribe;
    }
    public void setTribe(String tribe) {
        this.tribe = tribe;
    }

    public String getGenus() {
        return genus;
    }
    public void setGenus(String genus) {
        this.genus = genus;
    }
    
    public String getSubgenus() {
        return subgenus;
    }
    public void setSubgenus(String subgenus) {
        this.subgenus = subgenus;
    }
    
    public String getSpeciesGroup() {
        return speciesGroup;
    }
    public void setSpeciesGroup(String speciesGroup) {
        this.speciesGroup = speciesGroup;
    }

    public String getSpecies() {
        return species;
    }
    public void setSpecies(String species) {
        this.species = species;
    }

    public String getSubspecies() {
        return subspecies;
    }
    public void setSubspecies(String subspecies) {
        this.subspecies = subspecies;
    }

    public String getSpeciesSubspecies() {
      String retval = getSpecies();
      if (getSubspecies() != null) retval += " " + getSubspecies();
      return retval;
    }
    
    public Taxon getParent() {
        return parent;
    }
    public void setParent(Taxon parent) {
        this.parent = parent;
    }

    public String getName() {
        return name;
    }
    //public void setName() {
      // just a stub
    //}
    
        /* This gets called from the subclass.setTaxonomicInfo() method.
         * It will be the family name for a family, subfamily name for subfamily, etc..
         */
/*
    public void setName(String name) {
         this.name = name;
    }
*/

    public String toString() {
        return getName();
    }

    // This accessor/mutator is used by GenericSearchResults and specimenReport.jsp
    public String getUploadDate() {
        return uploadDate;
    }
    public void setUploadDate(String uploadDate) {
        this.uploadDate = uploadDate;
    }
    
    public String getTest() {
        return test;
    }
    public void setTest(String test) {
        this.test = test;
    }

    public String getNextRank() {
        return Rank.getNextRank(getRank());
    }
 
    public boolean getIsFossil() {
      return fossil;
    }
    public void setIsFossil(boolean fossil) {
      this.fossil = fossil;
    } 
    
    private static int MAX_SAFE_CHILDREN_COUNT = 300;        
        
    public static int getMaxSafeChildrenCount() {
      return MAX_SAFE_CHILDREN_COUNT;
    }
    
    public boolean useSafeMode() {
        if (
            (getChildrenCount() > MAX_SAFE_CHILDREN_COUNT)
            && ("species".equals(getRank()))
            ) {
            s_log.info("Using safe mode for rank:" + getRank() + " taxon:" + getTaxonName());
            return true;
        }
        return false;
    }
    
    public int getChildrenCount() {
        if (children != null) {
            return children.size();
        } else {
            return -1;
        }
    }

    public void setChildrenCount(int childrenCount) {
        this.childrenCount = childrenCount;
    }
    

    public void setDetails(Connection connection, boolean withImages) throws SQLException {
        String taxonName = AntFormatter.escapeQuotes(getTaxonName());
     	String query = "select antcat_id, type, type, fossil, author_date, status" 
     	+ " from taxon where taxon_name = '" + taxonName + "'";

        Statement stmt = null;
        ResultSet rset = null;
		try {		
			stmt = DBUtil.getStatement(connection, "setDetails");            		
			rset = stmt.executeQuery(query);

			//if (taxonName.contains("insularis")) A.log("setDetails() query:" + query);
			while (rset.next()) {
                setAntcatId(rset.getInt("antcat_id"));

				if (rset.getInt("type") == 1) {
				  setIsType(true);
				}                    
				if (rset.getInt("fossil") == 1) {
				  setIsFossil(true);
				}  
						
				setAuthorDate(rset.getString("author_date"));
				setStatus(rset.getString("status"));
			}
            
            
            //A.log("setDetails() taxonName:" + taxonName + " class:" + this.getClass());
            if (withImages && (Rank.SUBFAMILY.equals(getRank()) || isSpeciesOrSubspecies())) {
              ImagePickDb imagePickDb = new ImagePickDb(connection);
              setDefaultSpecimen(Caste.MALE, imagePickDb.getDefaultSpecimen(Caste.MALE, this));
              setDefaultSpecimen(Caste.WORKER, imagePickDb.getDefaultSpecimen(Caste.WORKER, this));
              setDefaultSpecimen(Caste.QUEEN, imagePickDb.getDefaultSpecimen(Caste.QUEEN, this));
            }   
        } catch (Exception e) {
          s_log.warn("setDetails() e:" + e);
          throw e;        
        } finally {
            DBUtil.close(stmt, rset, this, "setDetails()");
        }
     }			
         
    public int getImageCount() {
        // To be deprecated?  Calculates all images below, regardless of project, perhaps not useful.
        //if (getHasImagesCount() != 0) return getHasImagesCount();
    
        int theCount = 0;
        if (images != null) {
          if (images.size() > 0) {
            theCount = images.size();
          }
        } else {
          if (this.imageCount == 0) {  
            theCount = getHasImagesCount();
          } else {
            return this.imageCount;
          }
        }
        //A.log("getImageCount() taxonName:" + getTaxonName() + " imageCount:" + theCount + " hasImageCount:" + getHasImagesCount());
        return theCount;
    }

    public void setImageCount(int imageCount) {
      this.imageCount = imageCount;
    }
    
    public boolean isImaged() { 
      return getHasImages(); 
    }

     
    public boolean getHasImages() {
        boolean hasEm = false;

        if ((hasImages == true) || ((images != null) && (images.size() > 0))) {
            hasEm = true;
        }

        return hasEm;
    }

/*
Used to be used by the Taxon hiearchy in setChildren(). Now handled by taxonSets.
*/
    public void setHasImages(boolean hasImages) {
        this.hasImages = hasImages;
    }

    public void setHasImages(Connection connection) throws SQLException{
      setHasImages(connection, null);
    }
    public void setHasImages(Connection connection, Overview overview) throws SQLException {
    
        //if (AntwebProps.isDevMode()) AntwebUtil.logStackTrace();    
        String theQuery = null;
        boolean hasOne = false;
        int imageCount = 0;

        Statement stmt = null;
        ResultSet rset = null;
        try {
            if (overview != null) {
              theQuery = overview.getImageCountQuery(getTaxonName());
            } else {
              s_log.error("setHasImages() unsupported overview type:" + overview.getClass()); 
              return;        
            }

            stmt = DBUtil.getStatement(connection, "setHasImages()");  
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                imageCount += rset.getInt(1);
            }

            if (imageCount > 0) {
                hasOne = true;
                setHasImagesCount(imageCount);
            }

            A.log("setHasImages() XXX imageCount:" + imageCount + " overview:" + overview + " query:" + theQuery);  //  && (theQuery.contains("acanthobius"))

        } catch (SQLException e) {
            s_log.error("setHasImages(" + overview + ") e:" + e + " query:" + theQuery);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "setHasImages()");
        }
        //A.log("setHasImages(" + overview + ") imageCount:" + imageCount + " query:" + theQuery);
    
        this.hasImages = hasOne;
    }   

    public int getHasImagesCount() {
      return hasImagesCount;
    }

    //  This is the count discovered during the setHasImages() method of the taxon subclass, or GenericSearchResults.java.  
    public void setHasImagesCount(int hasImagesCount) {
      this.hasImagesCount = hasImagesCount;
    }

    public Hashtable getImages() {
        return images;
    }
    
    public void setImages(Hashtable images) {
        // Not called very much. Common practice in Specimen and Species, and Taxon to directly set with this.images =

		if (false) {
			A.log("setImages images:" + images);
			AntwebUtil.logStackTrace();
		}
    
        this.images = images;
    }

    public void setAllImages() {
        // Just like setImages(project) but gets all images instead of a single set
    }

    // theSort is a comma delimited string of image labels.  If you see something like
    // h|h1, that means if h is there show it, if not check if h1 is there
    // If you see something like * that means "everything else"
    public ArrayList<SpecimenImage> getImagesSorted(String theSort, boolean padding) {
        ArrayList<SpecimenImage> thisList = new ArrayList<>();
        Hashtable<String, SpecimenImage> theImages = getImages();
        ArrayList<String> notProcessed = new ArrayList<>(theImages.keySet());
        SpecimenImage blankImage = new SpecimenImage();
        //blankImage.setLowres("none");
        //blankImage.setMedres("none");
        //blankImage.setHighres("none");
        String[] components = theSort.split(",");
        String thisComponent = "";
        int loop;

        boolean addRest = false;
        int addHere = 0;
        for (loop = 0; loop < components.length; loop++)  {
            thisComponent = components[loop];
            String [] ors;
            if (thisComponent.equals("*")) {
                addRest = true;
                addHere = loop;
            } else {
                if (thisComponent.indexOf('|') != -1) {
                    ors = thisComponent.split("\\|");
                } else {
                    ors = new String[1];
                    ors[0] = thisComponent;
                }
                boolean foundOne = false;
                String thisHeader;
                int count = 0;
                do {
                    thisHeader = ors[count];
                    if (theImages.containsKey(thisHeader)){
                        thisList.add(theImages.get(thisHeader));
                        notProcessed.remove(thisHeader);
                        foundOne = true;
                    } 
                    count++;
                } while (foundOne == false && (count < ors.length));

                // add padding if necessary
                if (padding && foundOne == false) {
                    thisList.add(blankImage);
                }
            }
        }
        
        if (addRest) {
            ArrayList<SpecimenImage> theRest = new ArrayList<>();
            for (String nextStr : notProcessed) {
              theRest.add(theImages.get(nextStr));
            }
            thisList.addAll(addHere, theRest); 
        }
        
        //A.log("getImagesSorted() theList:" + thisList);
        return thisList;
    }

    public boolean hasImage(String shot) {
        boolean hasImage = false;
        //s_log.warn("hasImage() images:" + images + " shot:" + shot);        
        if ((images != null) && (images.size() > 0)) {
          if (images.containsKey(shot)) {
              return true;
          } 
          if (shot.equals("h") && images.containsKey("h1")) {
              return true;
          }
          if (shot.equals("p") && images.containsKey("p1")) {
              return true;
          }
          if (shot.equals("h") && images.containsKey("h2")) {
              return true;
          }
          if (shot.equals("p") && images.containsKey("p2")) {
              return true;
          }

          // Added Jun 4, 2021 to allow image here to display: https://www.antweb.org/specimenImages.do?name=usnm609585
          if (shot.equals("d") && images.containsKey("d1")) {
              return true;
          }
          if (shot.equals("d") && images.containsKey("d2")) {
              return true;
          }

        }
        return hasImage;
    }

    public void setSimilarCount(int similarCount) {
        this.similarCount = similarCount;
    }

    public int getSimilarCount() {
        return similarCount;
    }

    public void setSimilarComparisonString(String theString) {
        this.similarComparisonString = theString;
    }

    public String getSimilarComparisonString() {
        return similarComparisonString;
    }

    public boolean getChildrenHaveImages() {
      if (children != null) {
        for (Taxon child : getChildren()) {
          if (child.getHasImages()) return true;
        }
      }
      return false;
    }
    
    // This would only work in cases where the children images have been loaded...
    public boolean getChildrenHaveImages(String shot) {
      if (children != null) {
        for (Taxon child : getChildren()) {
          if (child.hasImage(shot)) return true;
        }
      }
      return false;
    }

    public int getUniqueChildImagesCount(String shot, String shot2, String shot3) {
        int theCount = 0;

        if (getChildren() != null) {

            // To avoid ConcurrentModificationException
            ArrayList<Taxon> children = getChildren();
            int childrenCount = children.size();
            Taxon[] childrenArray = new Taxon[childrenCount];
            children.toArray(childrenArray);

            for (Taxon taxon : childrenArray) {   // was getChildren()
                boolean hasImage = taxon.hasImage(shot);
                if (!hasImage) hasImage = taxon.hasImage(shot2);
                if (!hasImage) hasImage = taxon.hasImage(shot3);
                if (hasImage) {
                    theCount += 1;
                }
                if (taxonDebug()) A.log("getUniqueChildImagesCount() for name:" + getTaxonName() + " shot:" + shot + " shot2:" + shot2 + " shot3:" + shot3 + " hasImage:" + hasImage + " theCount:" + theCount);
            }
        }
        return theCount;
    }

    // What?  Should be abstract if anything?
    public String getCode() {
        return code;
    }
    public void setCode(String code) {
        this.code = code;
    }

    protected String getThisWhereClause() {
      // This method is overridden by subclasses, and used by setImages(), maybe setChildren, etc...
      //if (AntwebProps.isDevMode()) AntwebUtil.logStackTrace();
      return "";
    }

/*
	setImages(overview, caste)
	  getChosenImageCode(overview, caste)
		getUnipickedDefault()
		  getUnpickedFromSpeciesSet()
			selectCodeByCaste()
		
	This genus (formicinaecataglyphis) does have a Brachypterous (in formicinaecataglyphis aenescens):
	http://localhost/antweb/images.do?subfamily=formicinae&genus=cataglyphis&rank=genus&project=allantwebants		
	
	
    Image Picking... documentation from Brian.
	Basically, for a genus or species that has workers, the same worker images should show up for caste=worker and caste=default.  
	  [stop here and ask questions if this does not seem clear]

	For the following, "default images"  are the images that are chosen to represent the species and "Default view" is equal to  "&caste=default"

	For testing, I think the first genus in the following provides a good example: 
	Worker: https://www.antweb.org/taxonomicPage.do?rank=genus&countryName=Madagascar&images=true&caste=worker
	Default: https://www.antweb.org/taxonomicPage.do?rank=genus&countryName=Madagascar&images=true&caste=default

	When viewing "caste=worker"
	This could be improved if we the default image is present if available.  So how about the following:

	Species:  Must be worker: Use default image when available, if not, use the first specimen that is worker

	Genus: Must be worker: choose from the first species (alphabetically) in genus for the given geolocal/region. 
	  Use default if available; if no default, select worker image from the first species with worker. 

	When viewing "caste=default"

	Species: same as Worker above but if no worker present for species, then first choose queen and if no queen, then male.

	[Thus in the example, above, a male would not be chosen to represent the genus, 
	  the same worker image should be found both caste=default and caste=worker

	Genus:
	Same as worker above but if no worker then choose queen and if no queen then male.  
	Choose worker: look alphabetically through species, choose default worker for species when available; 
	  if no worker, choose queen, if no queen choose male.    
    */

    private String selectCodeByCaste(ResultSet rset, String caste) 
      throws java.sql.SQLException {
      
        /* Looking for a code with the given caste. Caste will be null, default, male, worker or queen.		
		 If looking for a worker. 
		   loop through to find the caste. If none found, take first queen. Otherwise take unassigned. Otherwise male.
		 If looking for a queen. 
		   loop through to find the caste. If none found, take first worker. Else null.
		 If looking for a male. 
		   loop through to find the caste. Else null.
		 */
      
        String selectedCode = null;
        String firstMale = null;
        String firstWorker = null;
        String firstQueen = null;
        String firstUndesignated = null;
        while (rset.next()) {
        
            String code = rset.getString("code");
            String rsetCaste = rset.getString("caste");
            boolean isMale = "male".equals(rsetCaste);
            boolean isWorker = "worker".equals(rsetCaste);
            boolean isQueen = "queen".equals(rsetCaste);
 
            // Set in case we don't find what we are looking for...
            if (firstMale == null && isMale) firstMale = code;
            if (firstWorker == null && isWorker) firstWorker = code;
            if (firstQueen == null && isQueen) firstQueen = code;
            if (!isMale && !isWorker && !isQueen) firstUndesignated = code;

            // If you have found what you are looking for...
            if ("male".equals(caste) && isMale) return code;
            if ("worker".equals(caste) && isWorker) return code;
            if ("queen".equals(caste) && isQueen) return code;

			//String taxonName = rset.getString("taxon_name");
			//if (taxonName.contains("nochetus")) A.log("selectCodeByCaste() code:" + code + " male:" + isMale  + " worker:" + isWorker + " queen:" + isQueen);
            
            if ((caste == null || Caste.DEFAULT.equals(caste)) && isWorker) return code;

			// When searching on a subcaste, they must be exact.
			String rsetSubcaste = rset.getString("subcaste");
			//if (taxonDebug()) A.log("selectCodeByCaste() caste:" + caste + " rsetCaste:" + rsetCaste + " code:" + code);
			if ("alateDealateQueen".equals(caste) && "alate/dealate".equals(rsetSubcaste)) return code;
			if ("ergatoidQueen".equals(caste) && "ergatoid".equals(rsetSubcaste) && "queen".equals(rsetCaste)) return code;
			if ("brachypterous".equals(caste) && "brachypterous".equals(rsetSubcaste)) return code;
			if ("majorSoldier".equals(caste) && "major/soldier".equals(rsetSubcaste)) return code;
			if ("normal".equals(caste) && "normal".equals(rsetSubcaste)) return code;
			if ("ergatoidMale".equals(caste) && "ergatoid".equals(rsetSubcaste) && "male".equals(rsetCaste)) return code;
			if ("alateMale".equals(caste) && "alate".equals(rsetSubcaste)) return code;
			if ("intercaste".equals(caste) && "intercaste".equals(rsetSubcaste)) return code;
			if ("gynandromorph".equals(caste) && "gynandromorph".equals(rsetSubcaste)) return code;
			if ("brachypterous".equals(caste) && "brachypterous".equals(rsetSubcaste)) return code;
			if ("larvaPupa".equals(caste) && "larva/pupa".equals(rsetSubcaste)) return code;
        }

        // Return what we will settle for...
        if ((caste == null || Caste.DEFAULT.equals(caste))) {
          if (firstWorker != null) return firstWorker;
          if (firstUndesignated != null) return firstUndesignated;
          if (firstQueen != null) return firstQueen;
          if (firstMale != null) return firstMale;
          return firstUndesignated; 
        }
        return null;        
    }

    // if Genus, see if we can get the pick from a species in our geolocale.
    private String getDefaultFromSpeciesSet(Connection connection, String caste, ArrayList<String> speciesNameSet) {
      //if (getTaxonName().contains("aenictogiton")) A.log("getDefaultFromSpeciesSet() caste:" + caste + " speciesNameSet:" + speciesNameSet);
     
      int maxLoop = 50;  // Some genera can have (camponotus has 1000+ species)... 
      ImagePickDb imagePickDb = new ImagePickDb(connection);
      int i = 0;
      for (String speciesName : speciesNameSet) {
        ++i;
        if (i > maxLoop) return null;
        String pick = imagePickDb.getDefaultSpecimenForTaxon(caste, speciesName);   
        if (pick != null) {
          if (taxonDebug()) A.log("getDefaultFromSpeciesSet() caste:" + caste + " pick:" + pick + " taxonName:" + getTaxonName() + " speciesNameSet:" + speciesNameSet);
          return pick;
        }
      }
      return null;
    }

// ??? *** Shouldn't we pass in the view here. To default to H...?

    private String getUnpickedFromSpeciesSet(Connection connection, String caste, String speciesSetStr) {
      String shotClause = " and shot_type = 'h'";
      return getUnpickedFromSpeciesSet(connection, caste, speciesSetStr, shotClause);
    }
    private String getUnpickedFromSpeciesSetFlex(Connection connection, String caste, String speciesSetStr) {
      String shotClause = " and (shot_type = 'h' or shot_type = 'p' or shot_type = 'd' or shot_type = 'v')";
      return getUnpickedFromSpeciesSet(connection, caste, speciesSetStr, shotClause);
    }

    private String getUnpickedFromSpeciesSet(Connection connection, String caste, String speciesSetStr, String shotClause) {
	  String query = "select code, taxon_name, caste, subcaste "
		  + " from specimen, image "
		  + " where specimen.code = image.image_of_id "
		  + shotClause
		  + " and shot_number = 1 "
		  + " and specimen.taxon_name in " + speciesSetStr
          + " and " + Caste.getSpecimenClause(caste)		  
		  + " order by specimen.species, code"
		  ;
      Statement stmt = null;
      ResultSet rset = null;    
      try {
        if (taxonDebug()) A.log("getUnpickedFromSpeciesSet() query:" + query);

		stmt = DBUtil.getStatement(connection, "getUnpickedFromSpeciesSet()");
		rset = stmt.executeQuery(query);

		// now if we got a specimen, get its image information
		String code = selectCodeByCaste(rset, caste);
		if (taxonDebug()) A.log("getUnpickedFromSpeciesSet() caste:" + caste + " code:" + code + " taxonName:" + getTaxonName()); // + " speciesSetStr:" + speciesSetStr);
        return code;

      } catch (Exception e) {
        s_log.error("getUnpickedFromSpeciesSet() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, this, "getUnpickedFromSpeciesSet()");
      }    
      return null;
    }
    
    private ArrayList<String> getSpeciesNameSet(Connection connection, Overview overview) {
      ArrayList<String> speciesNameSet = new ArrayList<>();
      Statement stmt = null;
      ResultSet rset = null;
      String query = null;
      try {
        // This works for genera.  Find a taxon with the genera, or subfamily, and use it.
        String overviewClause = " where 1 = 1 ";
        if (overview != null) overviewClause = overview.getFetchChildrenClause();
		query = "select taxon.taxon_name taxon_name from taxon"
		  + overviewClause
		  + getThisWhereClause()
		  //+ " and taxon.image_count > 0"   // Removed this.  Yes?
		  + " and taxarank in ('species', 'subspecies')"
		  + " and status in ('valid', 'morphotaxon', 'indetermined')"   
          //  Above line added back in. Seems to work. Then this won't show an image: /description.do?species=acuta&genus=aphaenogaster&rank=species
		  + " order by status desc, taxon_name"
		  // + " limit 100"  // Dropped. Myrmicinae still seems to perform (10 seconds).
		  ;

		  stmt = DBUtil.getStatement(connection, "getSpeciesNameSet()");
		  rset = stmt.executeQuery(query);

          if (taxonDebug()) A.log("getSpeciesNameSet() this:" + this.getClass() + " query:" + query);

           int i = 0;
           while (rset.next()) {
               i = i + 1;
               String taxonName = rset.getString("taxon_name");
               speciesNameSet.add(taxonName);
           }


      } catch (SQLException e) {
        s_log.error("getSpeciesNameSet() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, this, "getSpeciesNameSet()");
      }

      return speciesNameSet;
    }

    // Should show all the genera and use images from the species. 
    private String getUnpickedDefault(Connection connection, Overview overview, String caste) {
        String chosenImageCode = null;

        if (caste == null) caste = Caste.DEFAULT;
        ArrayList<String> speciesNameSet = new ArrayList<>();

        /*
        Doubled http requests. It seems that if we let these queries run in getSpeciesNameSet() on a page such as:
        https://localhost/taxonomicPage.do?rank=species&images=true&statusSet=all&regionName=Oceania
        ... even if we discard the results, the whole http request is rerun. Makes no sense (and hard to track down).
        Adjustment made so that it doesn't run on overviews that are regions.
         */
        boolean skipGetUnpickedDefault = false;
        if (overview instanceof Region || overview instanceof Subregion) {
            skipGetUnpickedDefault = true;
        }
        if (!skipGetUnpickedDefault ) speciesNameSet = getSpeciesNameSet(connection, overview); // Could just returned null here?

        // Subfamilies are different.
        if (Rank.SUBFAMILY.equals(getRank())) {
          //A.log("getUnpickedDefault() subfamily speciesNameSet:" + speciesNameSet);
          chosenImageCode = getDefaultFromSpeciesSet(connection, caste, speciesNameSet);
          if (chosenImageCode != null) {
  	 	    A.log("getUnpickedDefault() subfamily caste:" + caste + " overview:" + overview + " speciesNameSet:" + speciesNameSet + " chosenImageCode:" + chosenImageCode);
            return chosenImageCode;
          }
        }

        String speciesSetStr = SqlUtil.getSetStr(speciesNameSet);
		if (speciesSetStr == null) {
		  return null;
		}
		
		if (taxonDebug()) A.log("caste:" + caste);

        if (Rank.GENUS.equals(getRank())) {
          if (Caste.DEFAULT.equals(caste)) {
            chosenImageCode = getDefaultFromSpeciesSet(connection, Caste.WORKER, speciesNameSet);
            if (chosenImageCode == null)
              chosenImageCode = getUnpickedFromSpeciesSet(connection, Caste.WORKER, speciesSetStr);
            if (chosenImageCode == null)
              chosenImageCode = getDefaultFromSpeciesSet(connection, Caste.QUEEN, speciesNameSet);
            if (chosenImageCode == null)
              chosenImageCode = getUnpickedFromSpeciesSet(connection, Caste.QUEEN, speciesSetStr);
            if (chosenImageCode == null)
              chosenImageCode = getDefaultFromSpeciesSet(connection, Caste.MALE, speciesNameSet);
            if (chosenImageCode == null)
              chosenImageCode = getUnpickedFromSpeciesSet(connection, Caste.MALE, speciesSetStr);
// DO WE NEED THESE?
//            if (chosenImageCode == null) 
//              chosenImageCode = getUnpickedFromSpeciesSetFlex(caste, speciesSetStr);
          } else {
            // ? Shouldn't do this if the caste represents a subcaste. For we won't find one. Image picking not supporting subcaste (yet). Performance.
            chosenImageCode = getDefaultFromSpeciesSet(connection, caste, speciesNameSet);
            if (taxonDebug()) A.log("getUnpickedDefault() chosenImageCode:" + chosenImageCode);
            if (chosenImageCode == null) 
              chosenImageCode = getUnpickedFromSpeciesSet(connection, caste, speciesSetStr);
// Need these?
//            if (chosenImageCode == null) 
//              chosenImageCode = getUnpickedFromSpeciesSetFlex(caste, speciesSetStr);
          }

		  if (taxonDebug()) A.log("getUnpickedDefault() " + getRank() + " taxonName:" + getTaxonName() + " caste:" + caste + " overview:" + overview + " chosenImageCode:" + chosenImageCode); // + " speciesNameSet:" + speciesNameSet + " speciesSetStr:" + speciesSetStr);
		  //AntwebUtil.logStackTrace();

          return chosenImageCode;          
        }

        chosenImageCode = getUnpickedFromSpeciesSet(connection, caste, speciesSetStr);
        // It would be nice here to know what the caste the retuned image code is, and
        // return the taxon's default for that caste if it is different. This would cause
        // us to use a default instead of a random in cases of species. If we request
        // a default, we don't here know what we are getting back.

        // This gets us a specimen even though there is no h image. See sculptinodis
        // on: http://localhost/antweb/taxonomicPage.do?rank=species&bioregionName=Malagasy&images=true&statusSetSize=max&statusSet=valid%20extant
        if (chosenImageCode == null) 
          chosenImageCode = getUnpickedFromSpeciesSetFlex(connection, caste, speciesSetStr);

		if (taxonDebug()) A.log("getUnpickedDefault() rank:" + getRank() + " taxonName:" + getTaxonName() + " caste:" + caste + " overview:" + overview + " chosenImageCode:" + chosenImageCode); // + " speciesNameSet:" + speciesNameSet + " speciesSetStr:" + speciesSetStr);

        //A.log("getUnpickedDefault() species caste:" + caste + " overview:" + overview + " speciesNameSet:" + speciesSetStr + " chosenImageCode:" + chosenImageCode + " speciesSetStr:" + speciesSetStr);
        //AntwebUtil.logStackTrace();
        return chosenImageCode;
    }

    // Theses Defaults are Picked!
    public String getDefaultSpecimenLink(String caste) {
        String specimen = getDefaultSpecimen(caste);
        if (specimen == null) return null;
        String link = "<a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + specimen + "'>" + specimen + "</a>";
        return link;
    }
    public String getDefaultSpecimen(String caste) {
        if (caste == null) return null;
        if (Caste.DEFAULT.equals(caste)) {
            return getDefaultSpecimen(Caste.WORKER);
        }
        if (Caste.MALE.equals(caste)) return maleSpecimen;
        if (Caste.WORKER.equals(caste)) return workerSpecimen;
        if (Caste.QUEEN.equals(caste)) return queenSpecimen;
        return null;
    }
    public void setDefaultSpecimen(String caste, String specimen) {
        if (caste == null || Caste.DEFAULT.equals(caste)) {
            s_log.warn("Must specimen male, worker or queen");
            return;
        }
        if (Caste.MALE.equals(caste)) this.maleSpecimen = specimen;
        if (Caste.WORKER.equals(caste)) this.workerSpecimen = specimen;
        if (Caste.QUEEN.equals(caste)) this.queenSpecimen = specimen;
    }



    /*
    // Used at all?
    public void setImages() throws SQLException {
    }
*/

    public void setImages(Connection connection, Overview overview) throws SQLException {
        setImages(connection, overview, Caste.DEFAULT);
        //A.log("setImages(" + overview + ")");
    }

    public void setImages(Connection connection, Overview overview, String caste) throws SQLException {

// Break here, not doubled.

      /* we have to get one good specimen and load up all the shots into the images hashtable
 
         This.images which is set, is only one set of the best images.  No counts.
 
         This method can set images that belong to taxons in the project, where the image itself is of 
         a specimen that is not in the project.  Email to Brian dated Feb 22nd, 2012 entitled, Taxon
         Image imprecision outlines, and there is a piece of documentation in taxonPage-body.jsp 
         around line 135.

         // imageSet: useDefaults, male, worker, queen

         Called from:
           for subfamily: Subfamily.setChildren(Subfamily.java:164)
           for genus: Genus.setChildren(Genus.java:179)
           for species: BrowseAction.execute(BrowseAction.java:387)
         
	   Overview could be an adm1, country, subregion, region, global
	   Perhaps a default specimen is selected for this taxon
	   A child taxon has a default set
	   Or, we will just select a suitable specimen from this taxon (with head shot)
	   
	   This is not exactly what happens, but is our intent:

	   Default pics for species...
		 choose in the descending priority:
		 caste=default
		 use picW available; if not
		 use unpicW, if not
		 use picQ, if not
		 use unpicQ, if not
		 use picM, if not
		 use unpicM  
        */  

        //A.log("setImages() taxonName:" + getTaxonName());
        Hashtable myImages = new Hashtable();
        String chosenImageCode = null;
        
        if ("formicidae".equals(getTaxonName())) chosenImageCode = "antweb1008052";

        // Will exist (perhaps) for subfamily and species. Genera are determined at run-time.
        if (chosenImageCode == null) {
          chosenImageCode = getDefaultSpecimen(caste);
          //if (chosenImageCode != null) A.log("setImages() PICKED:" + chosenImageCode + " caste:" + caste + "  taxonName:" + getTaxonName());
        }

// Break here, not doubled.

        if (taxonDebug()) A.log("setImages(" + overview + ", " + caste + ") 1 taxonName:" + getTaxonName() + " code:" + chosenImageCode);
        
		// well, no default Image, so try to find one good specimen for this family
        if (chosenImageCode == null) {
          chosenImageCode = getUnpickedDefault(connection, overview, caste);
          if (taxonDebug() && chosenImageCode != null) A.log("setImages(" + overview + "," + caste + ") unPickedDefault:" + chosenImageCode + " taxonName:" + getTaxonName());
        }

// Break here, doubled

        if (taxonDebug()) A.log("setImages(" + overview + ", " + caste + ") 2 taxonName:" + getTaxonName() + " code:" + chosenImageCode);

		if (chosenImageCode == null) {
		  if (taxonDebug()) A.log("setImages() none found. Bail. TaxonName:" + getTaxonName());
		  return; 
        }

        // Found a good one. Load it into images.
		ArrayList<SpecimenImage> specImages = (new ImageDb(connection)).getSpecimenImages(chosenImageCode); 
		for (SpecimenImage specImage : specImages) {
		  myImages.put(specImage.getShotType(), specImage);
		}
        if (taxonDebug()) A.log("setImages(" + overview + ", " + caste + ") 3 taxonName:" + getTaxonName() + " code:" + chosenImageCode + " count:" + myImages.size());

// If break here, doubled.

        this.images = myImages;
    }

    private boolean taxonDebug() {
      return true && AntwebDebug.isDebugTaxon(getTaxonName());
        /*
      return (
        true && (
           "formicidae".equals(getTaxonName())
        || "myrmicinaecrematogaster".equals(getTaxonName())
        || "myrmicinaeaphaenogaster acuta".equals(getTaxonName())
        || "myrmicinaecardiocondyla shuckardi sculptinodis".equals(getTaxonName()) 
        || "formicinaecamponotus hova radamae".equals(getTaxonName())
        || "dolichoderinaektunaxia jucunda".equals(getTaxonName())
        ));
*/
    }

    public static boolean isQuadrinomial(String taxonName) {
      String work = taxonName;
      for (int i = 1 ; i <= 3; ++i) {
        int spaceIndex = work.indexOf(" ");
        if (spaceIndex < 0) return false;
          //A.log("isQuadrinomial() taxonName:" + taxonName + " work:" + work + " i:" + i + " spaceIndex:" + spaceIndex);
        work = work.substring(spaceIndex + 1);
        if (i == 3) {	
          //A.log("isQuadrinomial() TRUE taxonName:" + taxonName + " work:" + work);
          return true;
        }
      }
      return false;
    }

    public static boolean isAnt(String taxonName) {
      if (taxonName == null) return false;
      
      if (taxonName.contains("(formicidae)")) return true;
      
      boolean returnVal = false;
      String subfamilyName = Taxon.getSubfamilyFromName(taxonName);

      ArrayList<Taxon> subfamilies = TaxonMgr.getSubfamilies(); 
      for (Taxon subfamily : subfamilies) {
        if (subfamily.getName().equals(subfamilyName)) {
          returnVal = true;
          break;
        }
      }
      
      //A.log("isAnt() taxonName:" + taxonName + " subfamilyName:" + subfamilyName + " returnVal:" + returnVal);
      return returnVal;
    }
    
    public boolean isAnt() {
        return "formicidae".equals(getFamily());
    }

    public static boolean isMorphoOrIndet(String taxonName) {
        return Taxon.isIndet(taxonName) || Taxon.isMorpho(taxonName);
    }

    public static boolean isIndet(String taxonName) {
        return (taxonName.contains("undet")) || (taxonName.contains("indet"));
    }
    
    public static String getNotMorphoCriteria() {
      return Taxon.getNotMorphoCriteria("");
    }
    public static String getNotMorphoCriteria(String alias) {
      String notMorphoCriteria 
        = " " + alias + "taxon_name not like '%?%'"
        + " and " + alias + "taxon_name not like '%(indet)%'"
        + " and " + alias + "taxon_name not like '%1%'"
        + " and " + alias + "taxon_name not like '%2%'"
        + " and " + alias + "taxon_name not like '%3%'"
        + " and " + alias + "taxon_name not like '%4%'"
        + " and " + alias + "taxon_name not like '%5%'"
        + " and " + alias + "taxon_name not like '%6%'"
        + " and " + alias + "taxon_name not like '%7%'"
        + " and " + alias + "taxon_name not like '%8%'"
        + " and " + alias + "taxon_name not like '%9%'"
        + " and " + alias + "taxon_name not like '%-%'"
        + " and " + alias + "taxon_name not like '%\\_%'"
        + " and " + alias + "taxon_name not like '%(%'"
        + " and " + alias + "taxon_name not like '%)%'"
        + " and " + alias + "taxon_name not like '%.%'"
      ;
      return notMorphoCriteria;
    }
    
    public static String getNotQuadrinomialCriteria() {
      return Taxon.getNotQuadrinomialCriteria("");
    }
    public static String getNotQuadrinomialCriteria(String alias) {
      String notQuadrinomialCriteria 
        = " length(" + alias + "taxon_name) - length(replace(" + alias + "taxon_name, ' ', '')) < 3";
      return notQuadrinomialCriteria;
    }    
    
// select taxon_name from proj_taxon where taxon_name like '%?%' or taxon_name like '%1%' or taxon_name like '%2%' or taxon_name like '%3%' or taxon_name like '%4%' or taxon_name like '%5%' or taxon_name like '%6%' or taxon_name like '%7%' or taxon_name like '%8%' or taxon_name like '%9%' or taxon_name like '%-%' or taxon_name like '%\_%' or taxon_name like '%(%' or taxon_name like '%)%' or taxon_name like '%.%';
        
    
    public static boolean isMorpho(String taxonName) {
        // This should be modified to use the getSimpleName() method instead of receiving a parameter.
        boolean isMorpho = false;

        if (taxonName == null) return false;
        if (taxonName.contains("(indet)")) return false;  // Indets are not morphos.

        // Single letter species or subspecies.  Morphos like: myrmicinaetetramorium a 
        String work = taxonName;
        for (int i = 1 ; i <= 3; ++i) {
          int spaceIndex = work.indexOf(" ");
          if (spaceIndex < 0) break;
          work = work.substring(spaceIndex + 1);
        }
        if (work.length() == 1) {
          //A.log("isMorpho() Last letter of taxonName:" + taxonName + " is singular, so isMorpho");
          return true;
        }

        isMorpho = (taxonName.contains("1"))
                || (taxonName.contains("2"))
                || (taxonName.contains("3"))
                || (taxonName.contains("4"))
                || (taxonName.contains("5"))
                || (taxonName.contains("6"))
                || (taxonName.contains("7"))
                || (taxonName.contains("8"))
                || (taxonName.contains("9"))
                || (taxonName.contains("-"))
                || (taxonName.contains("_"))
                || (taxonName.contains("("))
                || (taxonName.contains(")"))
                || (taxonName.contains("."));
        //if ("myrmicinaecrematogaster jtl-022".equals(taxonName)) s_log.warn("isMorphoSpecies(" + taxonName + ") isMorpho:" + isMorpho);
        return isMorpho;
    }		
    
    
    private transient String favoriteImagesProjectsStr;    
    public String getFavoriteImagesProjectsStr() {
       A.log("getFavoriteImagesProjectsStr() str:" + favoriteImagesProjectsStr);
        return this.favoriteImagesProjectsStr;
    }    
    public void setFavoriteImagesProjectsStr(String favoriteImagesProjectsStr) {
        this.favoriteImagesProjectsStr = favoriteImagesProjectsStr;
    }   

    private transient Integer specimenCount = null;
    public void setSpecimenCount(Integer specimenCount) {
      this.specimenCount = specimenCount;
    }
    public int getSpecimenCount() {
      return this.specimenCount.intValue();
    }
    public boolean hasSpecimens() {
      return (this.specimenCount != null) && (this.specimenCount.intValue() > 0);
    }

    public String getBrowserParams() {
        return browserParams;
    }
    public void setBrowserParams(String browserParams) {
      //A.log("setBrowserParams() browserParams:" + browserParams);    
        if (browserParams != null) {
          String stripString = "&genCache=true";
          browserParams = Formatter.stripString(browserParams, stripString);
        }
        this.browserParams = browserParams;
    }
    
    public String getTaxonomicBrowserParams() {
        // Overridden by subclasses
        return "";
    }

    public String getTaxonomicBrowserParams(Overview overview) {
        String params = getTaxonomicBrowserParams();
        if (overview != null) {
            params += "&" + overview.getParams();
        }    
        return params;
    }    

    public void generateBrowserParams() {
        generateBrowserParams(null);
    }

    public void generateBrowserParams(Overview overview) {
        String theParams = getTaxonomicBrowserParams();
    
        if (overview != null) {
            
            theParams += "&" + overview.getParams();
        }
        setBrowserParams(theParams);
    }

    public String getBrowserParams(String rank, Overview overview) {
    // Similar functionality exists in TaxaPage.java
    // This one behaves differently.  Not overridden.
        int rankLevel = Rank.getRankLevel(rank);
        String params = "";
        if (rankLevel == Rank.getRankLevel(Rank.FAMILY)) params += "family=" + getFamily();
        if (rankLevel <= Rank.getRankLevel(Rank.SUBFAMILY)) params += "subfamily=" + getSubfamily();
        if (rankLevel <= Rank.getRankLevel(Rank.GENUS)) params += "&" + "genus=" + getGenus();
        if (rankLevel <= Rank.getRankLevel(Rank.SPECIES)) params += "&" + "species=" + getSpecies();
        if (rankLevel <= Rank.getRankLevel(Rank.SUBSPECIES)) params += "&" + "subspecies=" + getSubspecies();
        if (rankLevel == Rank.getRankLevel(Rank.SPECIMEN)) params = "code=" + getCode(); // += "&" + 
        params += "&rank=" + rank;
        if (overview != null) {
            params += "&" + overview.getParams();
        }         
        //A.log("getBrowserParams(" + rank + ", " + project + ") params:" + params);
        return params;   
    }

    public boolean isBaseTaxon() {
      // Means that it can have specimens and should show the specimen report.  
        return isSpeciesOrSubspecies();
    }

    public String getSource() {
      return source;
    }
    public void setSource(String source) {
      this.source = source;
    }

    public int getLineNum() {
      return lineNum;
    }
    public void setLineNum(int lineNum) {
      this.lineNum = lineNum;
    }

    public String getLineNumLink() {
        if (Status.isWorldantsStatus(getStatus())) return "<a href='" + AntwebProps.getDomainApp() + "/showLog.do?action=worldants&line=" + getLineNum() + "'>" + getLineNum() + "</a>";
        return "" + getLineNum();
    }

    public String getInsertMethod() {
      return insertMethod;
    }
    public void setInsertMethod(String insertMethod) {
      this.insertMethod = insertMethod;
    }

    public Timestamp getCreated() {
      return created;
    }
    public void setCreated(Timestamp created) {
      this.created = created;
    }
    
    public boolean isValid() {
      // for convenience
      return getIsValid();
    }
    /* Valid comes from the Antcat upload (worldants) if it is an identified species.
       It may or may not have taxonomic history, but is a valid name.  */
    public boolean getIsValid() {
      return "valid".equals(getStatus());
    }

    /* Type is a denormalization indicating that there is taxonomic history for this 
    taxon.  It is populated at the end of the upload process in calculateTaxonIsValidNames()   */
    public boolean getIsType() {
      if ("morphotaxon".equals(getStatus())) return false;
      //A.log("getIsType() getTaxonName(): " + getTaxonName() + " status:" + getStatus());
      return isType;
    }  
    public void setIsType(boolean isType) {
      this.isType = isType;
    } 
    
    public boolean addNotValidWarning() {
        return !isValid()
                && !Status.EXCLUDED_FROM_FORMICIDAE.equals(getStatus())
                && !Status.HOMONYM.equals(getStatus());
    }
 
    public boolean getIsAntCat() {
      return isAntCat;
    }
    
    public void setIsAntCat(boolean isAntCat) {
      this.isAntCat = isAntCat;
    }
    
    public boolean getIsPending() {
      return isPending;
    }
    
    public void setIsPending(boolean isPending) {
      this.isPending = isPending;
    }

    public boolean isExtant() {
      return extant;
    }
    public void setIsExtant(boolean extant) {
      this.extant = extant;
    }
    
    public Hashtable getDescription() {
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

    // This is not being used
    public String getTheXml() {
        return theXml;
    }
    public void setTheXml(String theXml) {
        this.theXml = theXml;
    }
    

    public int getAntcatId() {
        return antcatId;
    }
    public void setAntcatId(int antcatId) {
        this.antcatId = antcatId;
    }

    public int getHolId() {
        return holId;
    }
    public void setHolId(int holId) {
        this.holId = holId;
    }

    public int getGroupId() {
        return groupId;
    }
    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public String getChartColor() {
        return chartColor;
    }
    public void setChartColor(String chartColor) {
        this.chartColor = chartColor;
    }    

    public String getAuthorDate() {
        return authorDate;
    }
    public void setAuthorDate(String authorDate) {
        this.authorDate = authorDate;
    }

    public String getAuthorDateHtml() {
        return authorDateHtml;
    }
    public void setAuthorDateHtml(String authorDateHtml) {
        this.authorDateHtml = authorDateHtml;
    }

    public String getAuthors() {
        return authors;
    }
    public void setAuthors(String authors) {
        this.authors = authors;
    }

    public String getYear() {
        return year;
    }
    public void setYear(String year) {
        this.year = year;
    }

    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }

    public boolean getIsAvailable() {
        return isAvailable;
    }
    public void setIsAvailable(boolean isAvailable) {
        this.isAvailable = isAvailable;
    }

    public String getCurrentValidName() {
        return currentValidName;
    }
    public void setCurrentValidName(String currentValidName) {
        this.currentValidName = currentValidName;
    }
 
    public String getCurrentValidTaxonName() {
        if (currentValidName == null) return null;
        return currentValidName.toLowerCase();
    }
  
    public String getCurrentValidRank() {
        return currentValidRank;
    }
    public void setCurrentValidRank(String currentValidRank) {
        this.currentValidRank = currentValidRank;
    }
    public String getCurrentValidParent() {
        return currentValidParent;
    }
    public void setCurrentValidParent(String currentValidParent) {
        this.currentValidParent = currentValidParent;
    }

    public boolean getIsOriginalCombination() {
        return originalCombination;
    }
    public void setIsOriginalCombination(boolean originalCombination) {
        this.originalCombination = originalCombination;
    }

    public String getWasOriginalCombination() {
        return wasOriginalCombination;
    }
    public void setWasOriginalCombination(String wasOriginalCombination) {
        this.wasOriginalCombination = wasOriginalCombination;
    }
       
    public boolean isExists(Connection connection) {
        // Check with the database.  Useful for OrphanDescEditsAction.java.
        extant = false;
        String taxonName = getTaxonName();
        String theQuery = "";
        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(connection, "isExists");
          theQuery = "select * from taxon where taxon_name='" + taxonName + "'";
          rset = stmt.executeQuery(theQuery);
          while (rset.next()) {
            extant = true;
          }
        } catch (SQLException e) {
            s_log.error("isExists() for taxonName:" + taxonName + " exception:" + e + " theQuery:" + theQuery);
            // project:" + project + " 
        } finally {
            DBUtil.close(stmt, rset, this, "isExists()");
        }
        return extant;
    }

    // This is a slightly sloppy way to pass along a value in the Orphan Manager
    private String toTaxonName;
    public void setToTaxonName(String taxonName) {
      toTaxonName = taxonName;
    }
    public String getToTaxonName() {
      return toTaxonName;
    }

    public Vector getHabitats() {
        return habitats;
    }
    public void setHabitats(Vector habitats) {
        this.habitats = habitats;
    }

    // These is to support the field guide.  Should not exist.  OO backwards.    
    // Overriden by Species
    public void setHabitats(Connection connection) throws SQLException {
        // Override by Species

        // A.log("setHabitats() this:" + getClass());
      //AntwebUtil.logStackTrace();
      //if (true) return;

      //setHabitats(connection);
      //this.connection = null;
    }

    public Vector getMicrohabitats() {
        return microhabitats;
    }    
    // These is to support the field guide.  Should not exist.  OO backwards.    
    // Override by Species
    public void setMicrohabitats(Connection connection) throws SQLException {
        // Override by Species

        // this.connection = connection;
      //setMicrohabitats(connection);
      //this.connection = null;
    }

    public Vector getMethods() {
        return methods;
    }
    public void setMethods(Vector methods) {
        this.methods = methods;
    }

    public void setTypes(Connection connection) {
      // Override by Species
    }

    public String getTypes() {
        return types;
    }
    public void setTypes(String types) {
        this.types = types;
    }

    // This is to support the field guide.  Should not exist.  OO backwards.
    // override by Species.
    public void setMethods(Connection connection) throws SQLException {
        // Override by Species
    }

    public String getCollectDateRange() {
      return this.collectDateRange;
    }
    public void setCollectDateRange(Connection connection) {
        String collectDateRange = "";
        String taxonName = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            taxonName = AntFormatter.escapeQuotes(getTaxonName());

            String theQuery =
              //  " select count(method), method " 
                " select min(dateCollectedStart), max(dateCollectedStart)"
              + " from specimen " 
              + " where taxon_name='" + taxonName + "'"
              + " and datecollectedstart is not null";

            stmt = DBUtil.getStatement(connection, "setCollectDateRange()");
            rset = stmt.executeQuery(theQuery);

            java.util.Date min = null;
            java.util.Date max = null;
            while (rset.next()) {
                min = rset.getDate(1);
                max = rset.getDate(2);
            }

            //A.log("getDateCollected() min:" + min + " max:" + max + " query:" + theQuery);

            if ((min != null) && (min.equals(max))) {
              collectDateRange = "collected on " + min;
            } else {
              if (min != null) collectDateRange += "collected between " + min + " ";
              if (max != null) collectDateRange += " and " + max;
            }
            //s_log.info("setCollectDateRange() collectDateRange:" + collectDateRange + " q:" + theQuery);
        } catch (Exception e) {
            s_log.error("setCollectDateRange() for taxonName:" + taxonName + " exception:" + e);
        } finally {
            DBUtil.close(stmt, rset, this, "setCollectDateRange()");
        }
        this.collectDateRange = collectDateRange;
      }

/*
    setElevations()
        called by Homonym.setElevations()


    public void setElevations(Connection connection) throws SQLException {
        this.connection = connection;
        setElevations();
        this.connection = null;
    }
*/
    public String getElevations() {
      return this.elevations;
    }

    public void setElevations(Connection connection) throws SQLException {
        //A.log("setElevations()");
        String elevations = "";
        String taxonName = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            taxonName = AntFormatter.escapeQuotes(getTaxonName());
            
            String theQuery =
              //  " select count(method), method " 
                " select min(elevation), max(elevation), avg(elevation)" 
              + " from specimen " 
              + " where taxon_name='" + taxonName + "'"
              + " and elevation is not null and elevation > 0";

            stmt = DBUtil.getStatement(connection, "setElevations()");
            rset = stmt.executeQuery(theQuery);

            int min = 0;
            int max = 0;
            double avg = 0;
            int recordCount = 0;
            while (rset.next()) {
              recordCount++;
              min = rset.getInt(1);
              max = rset.getInt(2);
              avg = rset.getDouble(3);
            }

            if ((min == max) && (min != 0)) {
              elevations = "collected at " + max + " m";
              //s_log.info("setElevations() elevations:" + elevations + " q:" + theQuery);
            } else {
              if (min > 0) elevations += "collected from " + min + " ";
              if (max > 0) elevations += "- " + max + " meters";
              if (avg > 0) {
                int avgInt = new BigDecimal(avg).intValue();
                //bdAvg = avg.setScale(0,BigDecimal.ROUND_HALF_UP);                
                elevations += ", " + avgInt + " meters average";
              }
              //s_log.info("setElevations() elevations:" + elevations + " q:" + theQuery);
            }  
        } catch (SQLException e) {
            s_log.error("setElevations() for taxonName:" + taxonName + " exception:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "setElevations()");
        }
        this.elevations = elevations;
    }


    // Used by Change View 
    public String getOtherUrl(String strutsTarget) {
        String otherUrl = null;
		String targetUrl = null;

		String descUrl = null;
		String otherViewUrl = null;
		String targetName = null;
		String browseIcon = "";
	
        Formatter format = new Formatter();
        String domainApp = AntwebProps.getDomainApp();    
            
        if ((getFamily() != null) && (getFamily().length() > 0)) {
            otherUrl = domainApp + strutsTarget + "?family=" + getFamily() + "&rank=family";
            targetName = format.capitalizeFirstLetter(getFamily());
            if (Rank.FAMILY.equals(getRank())) targetUrl = otherViewUrl; else targetUrl = descUrl;
        }
        if ((getSubfamily() != null) && (getSubfamily().length() > 0)) {
            otherUrl = domainApp + strutsTarget + "?subfamily=" + getSubfamily() + "&rank=subfamily";
            targetName = format.capitalizeFirstLetter(getSubfamily());
            if (Rank.SUBFAMILY.equals(getRank())) targetUrl = otherViewUrl; else targetUrl = descUrl;
        }
        if ((getGenus() != null) && (getGenus().length() > 0)) {
            otherUrl = domainApp + strutsTarget + "?genus=" + getGenus() + "&rank=genus";
            targetName = format.capitalizeFirstLetter(getGenus());
            if (Rank.GENUS.equals(getRank())) targetUrl = otherViewUrl; else targetUrl = descUrl;
        }
        if ((getSpecies() != null) && (getSpecies().length() > 0)) {
            otherUrl = domainApp + strutsTarget + "?genus=" + getGenus() + "&species=" + getSpecies() + "&rank=species";
            if (Rank.SPECIMEN.equals(getRank())) {
                targetName = format.capitalizeFirstLetter(getGenus()) + " " + getSpecies();
            } else {
                targetName = format.capitalizeFirstLetter(getPrettyName());
            }
            if (Rank.SPECIES.equals(getRank())) targetUrl = otherViewUrl; else targetUrl = descUrl;
        }
        //A.log("getOtherUrl() strutsTarget:" + strutsTarget + " otherUrl:" + otherUrl);
        return otherUrl;
    }

    private String changeViewOptions = "";
    
    public void setChangeViewOptions(Connection connection, Overview overview, String facet) {

      //if (overview instanceof Project) return;

      String otherUrl = getOtherUrl(facet);
    
	  if (!Project.ALLANTWEBANTS.equals(overview.getName())) {
		  changeViewOptions += Project.getAllAntwebLi(otherUrl);           
	  }
	  if (!Project.WORLDANTS.equals(overview.getName())) {
		if (getIsValid()) {
			changeViewOptions += Project.getBoltonLi(otherUrl);
		}
	  }
	  if (!Project.FOSSILANTS.equals(overview.getName())) {
		if (getIsFossil()) {
			changeViewOptions += Project.getFossilLi(otherUrl);
		}
	  }
	  
	  String taxonOptions = null;
	  
	  if (
		  (overview instanceof Geolocale) 
	   || (overview instanceof Museum)
	   || (overview instanceof Bioregion)    
	   ) {
		taxonOptions = overview.getChangeViewOptions(getTaxonName(), otherUrl, connection);
	  }
	  
	  if (taxonOptions != null) {
		changeViewOptions += "<li>___________________</li>" + taxonOptions;
	  }

      //A.log("setChangeViewOptions() overview:" + overview + " facet:" + facet + " taxonOptions:" + taxonOptions + " changeViewOptions:" + changeViewOptions);
    }
    
    public String getChangeViewOptions() {
        return this.changeViewOptions;
    }

    public ArrayList<Geolocale> getGeolocales() {
      ArrayList<Geolocale> geolocales = new ArrayList<>();
        geolocales.addAll(getCountries());
      return geolocales;
    }
    
    // Used to make the Distribution List.  Ordered by bioregion.
    public ArrayList<Country> getCountries() {
      return countries;
    }
    public void setCountries(ArrayList<Country> countries) {
      this.countries = countries;
    }
    // Used to make the Distribution List.
    public ArrayList<Bioregion> getBioregions() {
      return bioregions;
    }
    public void setBioregions(ArrayList<Bioregion> bioregions) {
      this.bioregions = bioregions;
    }
    
/*
    public Connection getConnection() {
        return connection;
    }
    public void setConnection(Connection connection) {
        this.connection = connection;
    }
*/

    public void filterChildren(String[] goodList) {
        if (getChildren() != null) {

//            Taxon thisChild;
            List goodArrayList = Arrays.asList(goodList);
            ArrayList<Taxon> newChildren = new ArrayList();
            for (Taxon thisChild : getChildren()) {
//            Iterator iterator = children.iterator();
//            while (iterator.hasNext()) {
 //               thisChild = (Taxon) iterator.next();
                if (goodArrayList.contains(thisChild.getFullName())) {
                    newChildren.add(thisChild);
                }
            }
            this.children = newChildren;
        }
    }
    
    public ArrayList<Taxon> getChildren() {
        return children;
    }
    public void setChildren(ArrayList<Taxon> children) {
        this.children = children;
    }

    public void setChildren(Connection connection, String name) {
    }

    public void setChildren(Connection connection, Overview overview) throws SQLException {
        StatusSet statusSet = StatusSet.getInstance(overview.getName());
        setChildren(connection, overview, statusSet, false, false, Caste.DEFAULT, false, null);
    }
 
    // Overridden by the Subfamily, genus, species, subspecies.
    public void setChildren(Connection connection, Overview overview, StatusSet statusSet, boolean getImages, boolean getMaps, String caste, boolean global, String subgenus) throws SQLException {
        //A.log("setChildren(5) overview:" + overview + " getImages:" + getImages + " getMaps:" + getMaps + " caste:" + caste);
        this.children = new ArrayList();
    }
    
    public void setChildrenLocalized(Connection connection, Overview overview) throws SQLException {
        
    }


    private String statusSetStr = null;
    private String statusSetSize = null;
    
	public String getStatusSetStr() {
	    return statusSetStr;
	}
	public void setStatusSetStr(String statusSetStr) {
		this.statusSetStr = statusSetStr;
	}    
	public String getStatusSetSize() {
	    return statusSetSize;
	}
	public void setStatusSetSize(String statusSetSize) {
		this.statusSetSize = statusSetSize;
	}

/*
    public void setTaxonomicInfo(String project) throws SQLException {
    // This method should be abstract.  The whole class should be abstract.  But alas, not so.
    }

    public void setTaxonomicInfo() throws SQLException {
        setTaxonomicInfo("");
    }
*/

    /*
    public void setTaxonomicInfo(Connection connection, String project) throws SQLException {
        // This method should be abstract. It is overriden. The whole class should be abstract.  But alas, not so.
    }
*/
    public void setTaxonomicInfo(Connection connection) throws SQLException {
        // This method should be abstract. It is overriden. The whole class should be abstract.  But alas, not so.
        //setTaxonomicInfo(connection, "");
    }



    /* // Feb2020
    public void callFinalize() throws Throwable {
      finalize();
    }
    
    protected void finalize() throws Throwable {
        super.finalize();
        if (images != null)
            images.clear();
        if (description != null)
            description.clear();
        connection = null;
        if (children != null) {
            for (Taxon thisChild : getChildren()) {
                thisChild.finalize();
            }
            children.clear();
        }
    }
*/

    public Map getMap() {
        return map;
    }
    public void setMap(Map map) {
        this.map = map;
    }

    public boolean hasMap() {
      boolean hasMap = false;

      // if we want to always pretend we have a map, and not load the map in all cases
      // of browseAction, then just do this:
      //hasMap = true;
      // See BrowseAction.java where we taxon.setMap(new Map(taxon, project, connection, 1));

      Map map = getMap();
      if (map != null) {
        if (map.hasPoints()) hasMap = true;
        //String function = map.getGoogleMapFunction();
        //if ((function != null) && (function.length() > 0)) return true;
        //A.log("hasMap() hasMap:" + hasMap + " map:" + map + " hasPoints:" + map.hasPoints());
      } else {
        //A.log("hasMap() hasMap:" + hasMap + " map:" + map);
      }
      return hasMap;
    }

    public String getBinomial() {
        //Formatter form = new Formatter();
        Utility util = new Utility();
        String binomial = "";
        String genus = getGenus();
        String species = getSpecies();
        if ((util.notBlank(genus)) && (util.notBlank(species))) {
            binomial = myFormatter.capitalizeFirstLetter(genus) + " " + species;
        }
        return binomial;
    }
    
    public boolean hasSpecimenDataSummary() {
        // Only species may return true
        return false;
    }

    public String getSpecimenDataSummary(int maxCount) {
      String summary = "";
      if (getHabitats().size() > 0) { 
        summary += "<p><b>Found most commonly in these habitats: </b>";

        // Habitats
        Enumeration elements = getHabitats().elements();
        int habitatI = 0;
        StringBuffer habitatString = new StringBuffer();
        while (elements.hasMoreElements()) {
          ++habitatI;
          String comma = (habitatI > 1)?", ":""; 
          String habitatCount = (String) elements.nextElement();
          String[] habitatCountArray = habitatCount.split(":");
          String habitatDesc = habitatCountArray[0];
          habitatDesc = habitatDesc.trim();
          String count = habitatCountArray[1];
          habitatString.append(comma + count + " times found in " + habitatDesc);
          if (habitatI >= maxCount) break;
        }
        if (getHabitats().size() > maxCount) { 
          habitatString.append(", ...");
        } else habitatString.append(".");
        summary += habitatString.toString();  
      }

      if (getMicrohabitats() != null && getMicrohabitats().size() > 0) {
        summary += "<p><b>Found most commonly in these microhabitats: </b>";
        Enumeration elements = getMicrohabitats().elements();
        int i = 0;
        StringBuffer microhabitatString = new StringBuffer();
        while (elements.hasMoreElements()) {
          ++i;
          String comma = (i > 1)?", ":""; 
          String countStr = (String) elements.nextElement();
          String[] countStrArray = countStr.split(":");
          String desc = countStrArray[0];
          desc = desc.trim();
          String count2 = countStrArray[1];
          microhabitatString.append(comma + count2 + " times " + desc);
          if (i > maxCount) break;
        }
        if (getMicrohabitats().size() > maxCount) { 
          microhabitatString.append(", ...");
        } else microhabitatString.append(".");        
        summary += microhabitatString.toString();
      }

      if (getMethods() != null && getMethods().size() > 0) {
        summary += "<p><b>Collected most commonly using these methods: </b>";
        Enumeration elements = getMethods().elements();
        int i = 0;
        StringBuffer methodString = new StringBuffer();
        while (elements.hasMoreElements()) {
          ++i;
          String comma = (i > 1)?", ":""; 
          String methodCount = (String) elements.nextElement();
          String[] methodCountArray = methodCount.split(":");
          String methodDesc = methodCountArray[0];
          methodDesc = methodDesc.trim();
          String count = methodCountArray[1];
          methodString.append(comma + count + " times " + methodDesc);
          if (i > maxCount) break;
        }
        if (getMethods().size() > maxCount) { 
          methodString.append(", ...");
        } else methodString.append(". ");
        summary += methodString.toString();
}

      //A.log("getSpecimenDataSummary(" + maxCount + ") elevation:" + getElevations());
      
      if (!getElevations().equals("")) {
        summary += "<p><b>Elevations: </b>" + getElevations();
      }
 
      if (!getCollectDateRange().equals("")) {
        summary += "<p><b>Collect Date Range: </b>" + getCollectDateRange();
      }
      
      //if (AntwebProps.isDevMode()) {
        //s_log.warn("getSpecimenDataSummary() types:" + getTypes());
      if (getTypes() != null && !"null".equals(getTypes())) {
        summary += "<p><b>Type specimens: </b>" + getTypes();
      }
      
      return summary;
    }
    
    
    public static String getTaxonUrl(Connection connection, String taxonName) throws SQLException{
      /* Should probably 
            1.Construct a Taxon and 
            2.Call a nonstatic method to return url.  
            3.Consolidate functionality.

         But instead we do it the procedural way...
      */ 
        String query = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            query = "select taxarank, family, subfamily, tribe, genus, subgenus, speciesgroup, species, subspecies "
              + " from taxon where taxon_name = '" + taxonName +"'";

            stmt = connection.createStatement();
            rset = stmt.executeQuery(query);

            int rsetCount = 0;
            String href = null;
            while (rset.next()) {
              ++rsetCount;
              String rank = rset.getString("taxarank");
              String family = rset.getString("family");
              String subfamily = rset.getString("subfamily");
              String genus = rset.getString("genus");
              String species = rset.getString("species");
              String subspecies = rset.getString("subspecies");
              href = Taxon.makeLink(rank, subfamily, genus, species, subspecies);
            }
            
            if (rsetCount == 1) {
              return href;
            }
        } catch (SQLException e) {
            s_log.error("getTaxonUrl() e:" + e + " query:" + query);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "getTaxonUrl() taxonName:" + taxonName);
        }

        return null;
    }

    private static String makeLink(String rank, String subfamily, String genus, String species, String subspecies) {
        // This makes something like: 
        //        Subfamily: <a href="http://www.antweb.org/description.do?genus=&name=&rank=
        Formatter format = new Formatter();

        String url = AntwebProps.getSecureDomainApp() + "/description.do?";
        String uri = null;        
        String name = null;
        String header = null;
        
        if (Rank.SUBFAMILY.equals(rank)) {
          header = "Subfamily:";
          uri = "&subfamily=" + subfamily 
              + "&rank=" + rank; 
          name = format.capitalizeFirstLetter(subfamily);
        } else if (Rank.GENUS.equals(rank)) {
          header = "Genus:"; 
          uri = "genus=" + genus
              + "&rank=" + rank;
          name = format.capitalizeFirstLetter(genus);
        } else if (Rank.SPECIES.equals(rank)) {
          header = "Species:";
          uri = "genus=" + genus 
              + "&species=" + species 
              + "&rank=" + rank; 
          name = format.capitalizeFirstLetter(genus) + " " + species;
        } else if (Rank.SUBSPECIES.equals(rank)) {
          header = "Subspecies:";
          uri = "genus=" + genus 
              + "&species=" + species 
              + "&subspecies=" + subspecies
              + "&rank=" + rank; 
          name = format.capitalizeFirstLetter(genus) + " " + species + " " + subspecies;        
        }

        String link = header + " " + "<a href=\"" + url + uri + "\">" 
            + name
            + "</a>";

        //s_log.info("makeLink() rank:" + rank + " subfamily:" + subfamily + " genus:" + genus + " species:" + species);                    

        return link;
    }

    public String getFullUrl() {
      return makeFullUrl(getRank(), getSubfamily(), getGenus(), getSpecies(), getSubspecies());
    }
    protected String makeFullUrl(String rank, String subfamily, String genus, String species, String subspecies) {
        //Formatter format = new Formatter();

        String url = AntwebProps.getDomainApp() + "/description.do?";
        String uri = null;        
       
        A.log("makeUrl() rank:" + rank + " subfamily:" + subfamily + " genus:" + genus + " species:" + species + " subspecies:" + subspecies);  
        if (rank.equals(Rank.FAMILY)) {
          uri = "family=" + family 
              + "&rank=" + rank;           
        } else if (rank.equals(Rank.SUBFAMILY)) {
          uri = "family=" + family 
              + "&subfamily=" + subfamily 
              + "&rank=" + rank; 
        } else if (rank.equals(Rank.GENUS)) {
          uri = "subfamily=" + subfamily 
              + "&genus=" + genus
              + "&rank=" + rank;
        } if (rank.equals(Rank.SPECIES)) {
          uri = "subfamily=" + subfamily 
              + "&genus=" + genus 
              + "&species=" + species 
              + "&rank=" + rank;         
        } if (rank.equals(Rank.SUBSPECIES)) {
          uri = "subfamily=" + subfamily 
              + "&genus=" + genus 
              + "&species=" + species 
              + "&subspecies=" + subspecies 
              + "&rank=" + rank; 
        }

        String link = url + uri;
        return link;
    }

    public String getUrl() {
       // This is the default
       return getUrl("description.do");
    }
    
    public String getUrl(String targetDo) {
      // description.do or browse.do or images.do 
      return makeUrl(targetDo, getRank(), getSubfamily(), getGenus(), getSpecies(), getSubspecies());
    }

    public String makeUrl(String rank, String subfamily, String genus, String species, String subspecies) {
      return makeUrl("description.do", getRank(), getSubfamily(), getGenus(), getSpecies(), getSubspecies());
    }
        
    protected String makeUrl(String targetDo, String rank, String subfamily, String genus, String species, String subspecies) {

        String url = AntwebProps.getDomainApp() + "/" + targetDo + "?";
        String uri = null;        
       // A.log("makeUrl() rank:" + rank + " family:" + family + " subfamily:" + subfamily + " genus:" + genus + " species:" + species);  
        if (rank.equals(Rank.FAMILY)) {
          uri = "family=" + family 
              + "&rank=" + rank;           
        } else if (rank.equals(Rank.SUBFAMILY)) {
          uri = "subfamily=" + subfamily 
              + "&rank=" + rank; 
        } else if (rank.equals(Rank.GENUS) || rank.equals(Rank.SUBGENUS)) {
          uri = "subfamily=" + subfamily 
              + "&genus=" + genus
              + "&rank=" + rank;
        } if (rank.equals(Rank.SPECIES)) {
          uri = "genus=" + genus 
              + "&species=" + species 
              + "&rank=" + rank;         
        } if (rank.equals(Rank.SUBSPECIES)) {
          uri = "genus=" + genus 
              + "&species=" + species 
              + "&subspecies=" + subspecies 
              + "&rank=" + rank; 
        }

        String link = url + uri;
        return link;
    }

    public static String getParentTaxonNameFromName(String taxonName) {
      if (taxonName == null) return null;

      if (taxonName.equals("formicidae")) return "hymenoptera";
      
      // if it is a species or subspecies:
      if (taxonName.contains(" ")) return taxonName.substring(0, taxonName.indexOf(" "));
      
      // if it is a subfamily:
      ArrayList<Taxon> subfamilies = TaxonMgr.getSubfamilies();
      for (Taxon subfamily : subfamilies) {
        if (taxonName.equals(subfamily.getTaxonName())) return "formicidae";
      }

      int inaeIndex = taxonName.indexOf("inae");
      if (inaeIndex > 0 && inaeIndex < 20) {
        return taxonName.substring(0, inaeIndex + 4);
      }

      return null; // shouldn't happen.
    }
    
    public static boolean isSpeciesOrSubspecies(String taxonName) {
      String rank = Taxon.getRankFromName(taxonName);
        return "species".equals(rank) || "subspecies".equals(rank);
    }
        
    public static String getRankFromName(String taxonName) {
      if ("formicidae".equals(taxonName)) return Rank.FAMILY;

      ArrayList<Taxon> subfamilies = TaxonMgr.getSubfamilies();
      for (Taxon subfamily : subfamilies) {
        if (taxonName.equals(subfamily.getTaxonName())) return Rank.SUBFAMILY;
      }

      //A.log("getRankFromName() subfamilies:" + subfamilies);
      
      if (!taxonName.contains(" ")) return Rank.GENUS;
      
      if (Taxon.getSubspeciesFromName(taxonName) != null) return Rank.SUBSPECIES;      
      
      return Rank.SPECIES;
    }

    public static String getSubfamilyFromName(String taxonName) {

      if (taxonName.contains("incertae_sedis")) return "incertae_sedis";

      ArrayList<Taxon> subfamilies = TaxonMgr.getSubfamilies();
      if (subfamilies == null) return null;
      
      for (Taxon aSubfamily : subfamilies) {
        if (taxonName.contains(aSubfamily.getSubfamily())) {
          return aSubfamily.getSubfamily();
        }
      }

      // Doesn't seem to be an ant.  Search for "nae"
      if (taxonName.contains("nae")) {
        int indexOf = taxonName.indexOf("nae");
        if (indexOf <= 0) indexOf = taxonName.indexOf("tae");
        if (indexOf > 0) {
          String subfamily = taxonName.substring(0, indexOf + 3);
          if ("(".equals(subfamily.substring(0,1))) subfamily += ")";
          return subfamily;
        }
      }
      return null;
    }
    public static String getGenusFromName(String taxonName) {
      // Take what comes after the subfamily and before the first space
      String genus = null;
      String subfamily = Taxon.getSubfamilyFromName(taxonName);
      if (subfamily == null) {
        // A.log("getGenusFromName() subfamily null for taxonName:" + taxonName);
        return null;
      }
      if (taxonName.contains(" ")) {
        try {
          genus = taxonName.substring(subfamily.length(), taxonName.indexOf(" ")); 
        } catch (StringIndexOutOfBoundsException e) {
          // exeuponerinae?
          //s_log.error("getGenusFromName() taxonName:" + taxonName + " subfamily:" + subfamily + " e:" + e);        
        }
      } else {
        genus = taxonName.substring(subfamily.length());            
      }
      return genus;
    }
    public static String getGenusTaxonNameFromName(String taxonName) {
    
      String subfamily = Taxon.getSubfamilyFromName(taxonName);
      if (subfamily == null) return null;
      String genus = Taxon.getGenusFromName(taxonName);
      if (genus == null) return null;
      String subfamilyGenus = subfamily + genus;
      return subfamilyGenus;
    }
    
    public static String getSpeciesFromName(String taxonName) {
      // if there is a space, take what comes after the space and before the next space, if there is one.
      String species = null;
      if (!taxonName.contains(" ")) return null;
      int firstSpace = taxonName.indexOf(" ");
      int secondSpace = taxonName.indexOf(" ", firstSpace + 1);
      if (secondSpace <= 0) {
        species = taxonName.substring(firstSpace + 1);
      } else {
        try {
          species = taxonName.substring(firstSpace + 1, secondSpace); // - firstSpace);
          //A.log("getSpeciesFromName() taxonName:" + taxonName + " species:" + species + " 2nd:" + secondSpace + " 1st:" + firstSpace);
        } catch (StringIndexOutOfBoundsException e) {
          s_log.warn("getSpeciesFromName() taxonName:" + taxonName + " e:" + e + " 2nd:" + secondSpace + " 1st:" + firstSpace);
          return null;
        }
      }
      return species;
    }
    public static String getSubspeciesFromName(String taxonName) {
      // if there are two spaces, takes what comes after the second space.
      String subspecies = null;
      if (!taxonName.contains(" ")) return null;
      int firstSpace = taxonName.indexOf(" ");
      int secondSpace = taxonName.indexOf(" ", firstSpace + 1);
      if (secondSpace <= 0) {
        return null;
      } else {
        subspecies = taxonName.substring(secondSpace + 1);
      }
      return subspecies;
    }


    private boolean validNameKey(String key, Hashtable item) {
  	  boolean valid = true;

 	 if (!item.containsKey(key)) {
 	    //s_log.info("validNameKey not found key:" + key);
	    return false;
	  }
 
	  String val = (String) item.get(key);
	  if (val.equals("null")) valid = false;
	  if (val.equals("")) valid = false;

	  if (!valid) {
	    if (AntwebProps.isDevMode()) {
	      if (!val.equals("")) s_log.warn("! validNameKey(" + key + ", " + val + ")");
	      /*
	      //AntwebUtil.logStackTrace();
			at org.calacademy.antweb.Taxon.validNameKey(Taxon.java:2990)
			at org.calacademy.antweb.Taxon.makeName(Taxon.java:808)
			at org.calacademy.antweb.Taxon.getTaxonName(Taxon.java:787)
			at org.calacademy.antweb.util.TaxonMgr.getGenus(TaxonMgr.java:67)
			at org.calacademy.antweb.upload.SpecimenUpload.setStatusAndCurrentValidName(SpecimenUpload.java:1359)
	       */	      
        } 
	  }
	  return valid;
    }

	public void logData() {
	  int imagesHashCount = 0;
	  if (getImages() != null) imagesHashCount = getImages().size();
	  s_log.warn("logData for taxon:" + getPrettyName()
	   // + " childImagesCount:" + getChildImagesCount() 
		+ " getImageCount:" + getImageCount()
		+ " imagesHashCount:" + imagesHashCount
		//+ " hasImagesCount:" + getHasImagesCount()
		+ " antCat:" + getIsAntCat()
		+ " pending:" + getIsPending()
		);
	}
        
        
    public String getExecTime() {
        return execTime;
    }
    public void setExecTime(String execTime) {
        this.execTime = execTime;
    }

    public static void sortTaxa(String orderBy, ArrayList<Taxon> children) {
      sortTaxa(orderBy, children, null);
	}
    public static void sortTaxa(String orderBy, ArrayList<Taxon> children, Overview overview) {
        // Sort the ChildrenList

        if ("taxonName".equals(orderBy)) Collections.sort(children, new SortTaxaByGenusSpecies());
        if ("authorDate".equals(orderBy)) Collections.sort(children, new SortTaxaByAuthorDate());
        if ("images".equals(orderBy)) Collections.sort(children, new SortTaxaByImages());
        if ("genera".equals(orderBy)) Collections.sort(children, new SortTaxaByGenera());
        if ("subgenera".equals(orderBy)) {
            A.log("taxonReportBody.jsp sort by subgenera");
            Collections.sort(children, new SortTaxaByGenusSubgenusSpecies());
        }
        if ("lifestage".equals(orderBy)) Collections.sort(children, new SortTaxaByLifeStage());
        if ("medium".equals(orderBy)) Collections.sort(children, new SortTaxaByMedium());
        if ("specimennotes".equals(orderBy)) Collections.sort(children, new SortTaxaBySpecimenNotes());

        if ("species".equals(orderBy)) Collections.sort(children, new SortTaxaBySpecies());
        if ("specimens".equals(orderBy)) Collections.sort(children, new SortTaxaBySpecimens());
        if ("specimensGlobal".equals(orderBy)) Collections.sort(children, new SortTaxaByGlobalChildCount());
        if ("specimens".equals(orderBy)) Collections.sort(children, new SortTaxaBySpecimens());
        if ("map".equals(orderBy)) Collections.sort(children, new SortTaxaByMap());
        if ("source".equals(orderBy)) Collections.sort(children, new SortTaxaBySource());
        if ("status".equals(orderBy)) Collections.sort(children, new SortTaxaByStatus());
        if ("type".equals(orderBy)) Collections.sort(children, new SortTaxaByIsType());
        if ("ie".equals(orderBy)) Collections.sort(children, new SortTaxaByIE(overview));
        if ("fromSpecimen".equals(orderBy)) Collections.sort(children, new SortTaxaByFromSpecimen());
	}
}

  
  
  