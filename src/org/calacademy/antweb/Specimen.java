package org.calacademy.antweb;

import java.io.*;
import java.util.Date;
import java.util.*;
import java.sql.*;
import java.text.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.xml.sax.SAXParseException;

/** Class Species keeps track of the information about a specific taxon */
public class Specimen extends Taxon implements Serializable, Comparable<Taxon>  {

    private static final Log s_log = LogFactory.getLog(Specimen.class);

    public int compareTo(Taxon other) {
      String thisOne = getTaxonName() + getFullName();
      String theOther = other.getTaxonName() + other.getFullName();

      s_log.debug("compareTo() compare:" + thisOne + " 2:" + theOther);
      return thisOne.compareTo(theOther);
    }

    protected String typeStatus;
    protected String country; 
    protected String adm1;  // was state/province;
    protected String adm2;  // was county;
    protected String islandCountry;
    protected String collectionCode;
    protected String localityName;
    protected String localityCode;
    protected String bioregion;
    protected String habitat;
    protected String method;
    protected String ownedBy;
    protected String collectedBy;
    protected String lifeStage;
    protected String caste;
    protected String subcaste;
    protected boolean isMale;
    protected boolean isWorker;
    protected boolean isQueen;    
    protected String locatedAt;
    protected Date lastModified;
    protected int groupId = 0;
    protected int curatorId = 0;
    protected String medium;
    protected String determinedBy;
    protected String dateDetermined;
    protected String dateDeterminedStr;
    protected String specimenNotes;
    protected String localityNotes;
    protected String collectionNotes;
    protected String dnaExtractionNotes;    
    protected String microhabitat;
//    protected String datesCollected;
    
    protected String dateCollectedStart;
    protected String dateCollectedEnd;
    protected String dateCollectedStartStr;
    protected String dateCollectedEndStr;
        
    private float decimalLatitude= 0.0F;
    private float decimalLongitude = 0.0F ;
    private String elevation = "";
    protected String elevationMaxError = "";    
    protected String latLonMaxError = "";
    protected String originalTaxonName = "";
    //Utilize Taxon superclass.
    //protected String created = "";
    protected int lineNum = 0;
    private boolean isIntroduced = false;
    //private boolean isEndemic = false;
    
    private String museumCode;
    private String backupFileName;

    private String defaultFor;

    private final Hashtable features = new Hashtable();

    private int uploadId = 0;
    
    private String flag;
    private String issue;

    private String taxonworksCollectionObjectID;

    public Specimen() {
    }    

    public Specimen(String code, Connection connection) throws SQLException {
        this.setCode(code);
        this.init(connection);
    }

    // Used by PictureLikeAction
    public Specimen(String code, Connection connection, boolean getFullData) throws SQLException {
        this.setCode(code);
        this.init(connection);
        if (getFullData) {
           this.fullInit(connection);
        }
    }
    
    public Specimen(String code, Overview overview, Connection connection, boolean getFullData) throws SQLException {
        this.setCode(code);
        this.init(connection);
        this.setHasImages(connection, overview);  // Need this?  Yes, for the count.  Could be worked around.
        if (getFullData) {
           this.fullInit(connection);
           this.setHasImages(connection, overview);  // Need this?  Yes, for the count.  Could be worked around.
        }
    }

    public void init(Connection connection) throws SQLException {
        setTaxonomicInfo(connection);
        setRank("specimen");
    }
    
    public void fullInit(Connection connection) throws SQLException {
        //setFeatures();

        setImages(connection);

        setSeeAlso(connection);

        setCountries(new GeolocaleTaxonDb(connection).getCountries(getTaxonName()));

        setDescription(connection, true);
    }

    public static Specimen getShallowInstance(String code, Connection connection) throws SQLException {
        Specimen specimen = new Specimen(code, connection);
        specimen.setRank("specimen");
        //specimen.setConnection(null);
        return specimen;
    }


    public void setTaxonomicInfo(Connection connection) throws SQLException {
        Statement stmt = null;
        ResultSet rset = null;
        try {
            String theQuery =
                "select subfamily, speciesgroup, genus, subgenus, species, subspecies, type_status, country, adm1, adm2, island_country"  // was province, county"
                    + ", localityName, collectionCode, bioregion, habitat, method, ownedby, collectedby"
                    + ", life_stage, caste, subcaste"
                    + ", locatedAt, localityCode, museum "
                    
                    //Added by Mark, Aug, 2011.
                    + ", access_group, access_login, last_modified, medium, determinedby " 
                    + ", specimenNotes, dnaExtractionNotes, microhabitat" //, datescollected "
                    
                    // Locality info added for the crazy link we create.
                    + ", elevation, elevationMaxError, decimal_latitude, decimal_longitude, latlonmaxerror"  // locxyaccuracy " 
                    + ", datedetermined, datedeterminedstr, localitynotes, collectionnotes"
                    + ", datecollectedstart, datecollectedend, datecollectedstartStr, datecollectedendStr"
                    + ", family, kingdom_name, phylum_name, class_name, order_name "
                    //+ ", parent_taxon_id, image_count "
                    + ", taxon_name, image_count "
                    + ", original_taxon_name, line_num, is_introduced" //, is_endemic "
                    + ", created "
                    + ", backup_file_name "
                    + ", upload_id "
                    + ", flag, issue, taxonworks_co_id, other"
                    + " from specimen where code='" + AntFormatter.escapeQuotes(getCode())
                    + "'";

            stmt = DBUtil.getStatement(connection, "setTaxonomicInfo()");
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                setSubfamily(rset.getString("subfamily"));
                setSpeciesGroup(rset.getString("speciesgroup"));
                setGenus(rset.getString("genus"));
                setSubgenus(rset.getString("subgenus"));
                //A.log("setTaxonomicInfo() subgenus:" + getSubgenus());

                setSpecies(rset.getString("species"));
                setSubspecies(rset.getString("subspecies"));
                setTypeStatus(rset.getString("type_status"));
                setCountry(rset.getString("country"));
                setAdm1(rset.getString("adm1"));
                setAdm2(rset.getString("adm2"));
                setIslandCountry(rset.getString("island_country"));
                setLocalityName(rset.getString("localityName"));
                setCollectionCode(rset.getString("collectionCode"));
                setBioregion(rset.getString("bioregion"));
                setHabitat(rset.getString("habitat"));
                setMethod(rset.getString("method"));
                setOwnedBy(rset.getString("ownedby"));
                setCollectedBy(rset.getString("collectedby"));

                setLifeStage(rset.getString("life_stage"));
                setCaste(rset.getString("caste"));
                setSubcaste(rset.getString("subcaste"));

                setLocatedAt(rset.getString("locatedAt"));
                setLocalityCode(rset.getString("localityCode"));
                setLastModified(rset.getTimestamp("last_modified"));
                setMedium(rset.getString("medium"));
                setDeterminedBy(rset.getString("determinedby"));
                setSpecimenNotes(rset.getString("specimenNotes"));
                setDnaExtractionNotes(rset.getString("dnaExtractionNotes"));
                setMicrohabitat(rset.getString("microhabitat"));
                
                // The above is poor design, discontinued.  Be explicit.
                //setDatesCollected(rset.getString("datescollected"));
                setDecimalLatitude(rset.getFloat("decimal_latitude"));
                setDecimalLongitude(rset.getFloat("decimal_longitude"));
                //setLocXYAccuracy(rset.getString("locxyaccuracy"));
                setLatLonMaxError(rset.getString("latlonmaxerror"));
                setElevation(rset.getString("elevation"));
                setElevationMaxError(rset.getString("elevationmaxerror"));
                setDateDetermined(rset.getString("datedetermined"));
                setDateDeterminedStr(rset.getString("datedeterminedStr"));
                setLocalityNotes(rset.getString("localitynotes"));
                setCollectionNotes(rset.getString("collectionnotes"));
                setDateCollectedStart(rset.getString("datecollectedstart"));
                setDateCollectedEnd(rset.getString("datecollectedend"));
                setDateCollectedStartStr(rset.getString("datecollectedstartstr"));
                setDateCollectedEndStr(rset.getString("datecollectedendstr"));
                setKingdomName(rset.getString("kingdom_name"));
                setPhylumName(rset.getString("phylum_name"));
                setClassName(rset.getString("class_name"));
                setOrderName(rset.getString("order_name"));
                setFamily(rset.getString("family"));         
                //setParentTaxonId(rset.getInt("parent_taxon_id"));   
                setParentTaxonName(rset.getString("taxon_name"));
                setImageCount(rset.getInt("image_count"));
                setOriginalTaxonName(rset.getString("original_taxon_name"));
                setLineNum(rset.getInt("line_num"));
                setCreated(rset.getTimestamp("created"));
                setIsIntroduced(rset.getInt("is_introduced") == 1);
                //setIsEndemic((rset.getInt("is_endemic") == 1) ? true : false);                
                setMuseumCode(rset.getString("museum"));
                setBackupFileName(rset.getString("backup_file_name"));
                setUploadId(rset.getInt("upload_id"));
                
                setGroupId(rset.getInt("access_group"));
                setCuratorId(rset.getInt("access_login"));
                
                setFlag(rset.getString("flag"));
                setIssue(rset.getString("issue"));
                setTaxonworksCollectionObjectID(rset.getString("taxonworks_co_id"));

                setDetailXml(rset.getString("other"));

                //goSetStatus(connection);
                goSetDetails();
                            
              //A.log("setTaxonomicInfo() code:" + getCode() + " uploadId:" + getUploadId() + " query:" + theQuery);      
            }
        } catch (SQLException e) {
            s_log.error("setTaxonomicInfo() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, this, "setTaxonomicInfo()");
        }
    }

    private void goSetDetails() {
        Taxon taxon = TaxonMgr.getTaxon(getTaxonName());
        if (taxon == null) {
          //A.log("goSetDetails() taxon is null for taxonName:" + getTaxonName());
          return;
        }
        setStatus(taxon.getStatus());
        setIsFossil(taxon.getIsFossil());
    }

    public static String getDataHeader() {
      String header = "SpecimenCode" + "\t" +
        "Subfamily" + "\t" +
        "Genus" + "\t" +
        "Species" + "\t" +
        "LifeStageSex" + "\t" +
        "Medium" + "\t" +
        "SpecimenNotes" + "\t" +
        "DNANotes" + "\t" +
        "LocatedAt" + "\t" +
        "OwnedBy" + "\t" +
        "TypeStatus" + "\t" +
        "DeterminedBy" + "\t" +
        "DateDetermined" + "\t" +
        "CollectionCode" + "\t" +
        "CollectedBy" + "\t" +
        "DateCollectedStart" + "\t" +
        "DateCollectedEnd" + "\t" +
        "Method" + "\t" +
        "Habitat" + "\t" +
        "Microhabitat" + "\t" +
        "CollectionNotes" + "\t" +
        "LocalityName" + "\t" +
        "Adm1" + "\t" +
        "Adm2" + "\t" +
        "Country" + "\t" +
        "Elevation" + "\t" +
        "ElevationMaxError" + "\t" +
        "LocLatitude" + "\t" +
        "LocLongitude" + "\t" +
        "LatLonMaxError"  + "\t" +
        "Bioregion" + "\t" +
        "LocalityNotes" + "\t" +
        "LocalityCode" + "\t" +
        "Created" + "\t" +
        "uploadId"
        ;
      return header;
    }
    
    public String getData(Connection connection) throws SQLException {
      if (getCode() == null) setTaxonomicInfo(connection);

      String data = "";
      String delimiter = "\t";   // ", ";
      
      data += AntFormatter.escapeQuotes(getCode()) + delimiter;  // getCode() not populated? 
      data += Utility.notBlankValue(getSubfamily()) + delimiter;
      data += Utility.notBlankValue(getGenus()) + delimiter;
      data += Utility.notBlankValue(getSpecies()) + delimiter;
      data += Utility.notBlankValue(getLifeStage()) + delimiter;
      data += Utility.notBlankValue(getMedium()) + delimiter;
      data += Utility.notBlankValue(getSpecimenNotes()) + delimiter;
      data += Utility.notBlankValue(getDnaExtractionNotes()) + delimiter;  // not getDnaNotes()
      data += Utility.notBlankValue(getLocatedAt()) + delimiter;
      data += Utility.notBlankValue(getOwnedBy()) + delimiter;
      data += Utility.notBlankValue(getTypeStatus()) + delimiter;  // getTypeStatus() ?
      data += Utility.notBlankValue(getDeterminedBy()) + delimiter;
      data += (getDateDetermined() == null ? "" : getDateDetermined()) + delimiter;
      data += Utility.notBlankValue(getCollectionCode()) + delimiter;
      data += Utility.notBlankValue(getCollectedBy()) + delimiter;
      data += (getDateCollectedStart() == null ? "" : getDateCollectedStart()) + delimiter;
      data += (getDateCollectedEnd() == null ? "" : getDateCollectedEnd()) + delimiter;
      data += Utility.notBlankValue(getMethod()) + delimiter;
      data += Utility.notBlankValue(getHabitat()) + delimiter;
      data += Utility.notBlankValue(getMicrohabitat()) + delimiter;
      data += Utility.notBlankValue(getCollectionNotes()) + delimiter;
      data += Utility.notBlankValue(getLocalityName()) + delimiter;
      data += Utility.notBlankValue(getAdm1()) + delimiter;
      data += Utility.notBlankValue(getAdm2()) + delimiter;
      data += Utility.notBlankValue(getCountry()) + delimiter;
      data += Utility.notBlankValue(getElevation()) + delimiter;
      data += Utility.notBlankValue(getElevationMaxError()) + delimiter;
      data += Utility.notBlankValue(getLocLatitude()) + delimiter;
      data += Utility.notBlankValue(getLocLongitude()) + delimiter;
      data += Utility.notBlankValue(getLatLonMaxError()) + delimiter;
      data += Utility.notBlankValue(getBioregion()) + delimiter;
      data += Utility.notBlankValue(getLocalityNotes()) + delimiter;
      data += Utility.notBlankValue(getLocalityCode()) + delimiter;
      data += (getCreated() == null ? "" : getCreated()) + delimiter;
      data += Utility.notBlankValue("" + getUploadId()) + delimiter;
      return data;      
    }

    public String getName() { return getCode(); }

    public String getFullName() { 
      return getCode(); 
    }

    public boolean isSpecimen(Connection connection) {
        boolean isSpecimen = false;
  	    
  	    //A.log("isSpecimen() code:" + getCode() + " connection:" + connection);
    
        if (getCode() != null && !getCode().equals("") && connection != null) {
            Statement stmt = null;
            ResultSet rset = null;
            try {
                String query = "select code from specimen where code = '" + AntFormatter.escapeQuotes(getCode()) + "'";
                stmt = DBUtil.getStatement(connection, "isSpecimen()");
                rset = stmt.executeQuery(query);
 
                if (rset.next()) {
                    isSpecimen = true;
                }
            } catch (SQLException e) {
                s_log.error("isSpecimen() e:" + e);
            } finally {
                DBUtil.close(stmt, rset, this, "isSpecimen()");
            }
        }
        return isSpecimen;
    }

// ! Not using overview
    public boolean isSpecimen(Connection connection, Overview overview) {
        if (overview == null) return isSpecimen(connection);
    
        boolean isSpecimen = false;
		Statement stmt = null;
		ResultSet rset = null;
        if (getCode() != null && !getCode().equals("") && connection != null) {
            try {
//                String query = "select code from specimen " 
//                    + " where code = '" + AntFormatter.escapeQuotes(getCode()) + "'"
//                    + " and taxon_name in (select taxon_name from proj_taxon where project_name = '" + project + "')";

                String clause = " where 1 = 1 ";
                if (!Project.ALLANTWEBANTS.equals(overview.getName())) {
                //  clause = ", " + overview.getSpecimenTaxonSetClause();
                }
                String query = "select specimen.code from specimen"
                    + clause
                    + " and specimen.code = '" + AntFormatter.escapeQuotes(getCode()) + "'";

 				//A.log("isSpecimen() query:" + query);	

                stmt = DBUtil.getStatement(connection, "isSpecimen()");
                rset = stmt.executeQuery(query);
                if (rset.next()) {
                    isSpecimen = true;
                }
            } catch (Exception e) {
                s_log.error("isSpecimen() overview:" + overview + " e:" + e);
            } finally {
                DBUtil.close(stmt, rset, this, "isSpecimen()");
            }
        }
        return isSpecimen;
    }

    public void initTaxonSet(Connection connection, Overview overview) throws SQLException {
        // Called by setChildren()
        if (getTaxonSet() == null) { 
          
          super.initTaxonSet(connection, overview);

          getTaxonSet().setImageCount(getImageCount());
        }
    }    
    
    public ProjTaxon getProjTaxon() {
      // In cases such as advanced search, the server has not prepared the taxon with its
      // projTaxon.  Specimen have the data they need and do not need database access
      // to return a decent proj_taxon record (since they don't have children and their
      // images are already loaded.
      if (getTaxonSet() == null) {
        // This is a backup ass covering maneuver in case the server does not set a Taxon's projTaxon,
        //   to avoid a null pointer exception.
        //if (AntwebProps.isDevMode()) {
          //s_log.info("getProjTaxon() taxonName:" + getTaxonName() + " rank:" + getRank());
          //if (AntwebProps.isDevMode()) AntwebUtil.logShortStackTrace();
        //}
        // Was something like this.  Now waiting for NPE to retest.
        //initTaxonSet(ProjectMgr.getProject(Project.ALLANTWEBANTS));
      }
      return (ProjTaxon) getTaxonSet();
    }    

    public void setSeeAlso(Connection connection)  throws SQLException {
        // This could happen in a wrongly constructed description.do with a rank=specimen
        if (code == null) return;
      /* Voucher and DNA specimens ([casent]-dxx).  The voucher is the code without the -dxx */
        String voucherName = getCode();
        if (voucherName.indexOf("-d") > 0) voucherName = voucherName.substring(0, voucherName.indexOf("-d"));
        String seeAlsoLinks = "";
        String dnaCodeCondition = voucherName + "-d%";
        String notDnaCodeNameCondition = "";        
        if (getCode().contains("-d")) {
            notDnaCodeNameCondition = " or specimen.code = '" + voucherName + "'";
        }                
        String query = "select specimen.code " 
            + " from specimen " 
            + " where specimen.code like '" + dnaCodeCondition + "'"
            + notDnaCodeNameCondition
          ;
        Statement stmt = null;        
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(connection, "setSeeAlso()");
          rset = stmt.executeQuery(query);

          //A.log("setSeeAlso() query:" + query);
          while (rset.next()) {
            //A.log("setSeeAlso() in while");
            String code = rset.getString("specimen.code");
            if (code.equals(getCode())) continue;
            if (!"".equals(seeAlsoLinks)) {
              seeAlsoLinks += ", ";
            }
            seeAlsoLinks += "<a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + code + "'>" + code + "</a>";
          }
        } finally {
          DBUtil.close(stmt, rset, this, "setSeeAlso()");
        }

        //A.log("setSeeAlso() seeAlsoLinks:" + seeAlsoLinks);        
        seeAlso = seeAlsoLinks;
        if ("".equals(seeAlso)) seeAlso = null;        
    }


    public void setImages(Connection connection, String whatever) throws SQLException {
        setImages(connection);
    }

    public void setImages(Connection connection, Overview overview) throws SQLException {
      // We do not use the overview
      setImages(connection);
    }
     
    public void setImages(Connection connection, Overview overview, String caste) throws SQLException {
      // We do not use the overview or caste.
      setImages(connection);
    }
            
    public void setImages(Connection connection) throws SQLException {

        //if (getTaxonName().contains("insularis")) A.log("setImages() code:" + getCode());

        // we have to get one good specimen and load up all
        // the shots into the images hashtable
        Hashtable<String, SpecimenImage> myImages = new Hashtable<>();

        String query = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            query = 
                "select shot_type, shot_number, has_tiff from image where "
                    + " image_of_id='" + AntFormatter.escapeQuotes(getCode()) + "'" 
                    + " and source_table = 'specimen'";

            stmt = DBUtil.getStatement(connection, "setImages()");
            rset = stmt.executeQuery(query);
            String shot = null;
            int shotNumber = 0;
            int hasTiff = 0;
            String combo = null;
            SpecimenImage specImage = null;

            //if ("casent0227526".equals(getCode()) A.log("setImages() query:" + query);

            while (rset.next()) {
                shot = rset.getString(1);
                shotNumber = rset.getInt(2);
                hasTiff = rset.getInt(3);
                combo = shot + shotNumber;
                specImage = new SpecimenImage();
                specImage.setShot(shot);
                specImage.setCode(getCode());
                specImage.setNumber(shotNumber);
                specImage.setHasTiff(hasTiff == 1);
                //specImage.setPaths();
                myImages.put(combo, specImage);

                //if ("casent0227526".equals(getCode())) A.log("setImages() specImage:"  + specImage);

            }

        } catch (SQLException e) {
            s_log.error("setImages() e:" + e + " query:" + query);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "setImages()");
        }

        this.images = myImages; // setting Taxon.java protected Hashtable images;

        //if ("casent0227526".equals(getCode())) A.log("setImages() images1:" + this.images + " images2:" + myImages + " images3:" + getImages() + " query:" + query);
        
    }
        
        
    public void setHasImages(Connection connection, Overview overview) throws SQLException {
        // We don't use overview here
        boolean hasOne = false;
        String theQuery = null;
        int theImageCount = 0;


        Statement stmt = null;
        ResultSet rset = null;
        try {
            theQuery =
                "select shot_type, shot_number, has_tiff from image where "
                    + " image_of_id='" + AntFormatter.escapeQuotes(getCode()) + "'" 
                    + " and source_table = 'specimen'";

            stmt = DBUtil.getStatement(connection, "setHasImages()");
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                // Now we always count them.
                ++theImageCount; // += rset.getInt(1);   // What?  Why.  Increment, no?
                /*
                if (!"l".equals(rset.getString(1))) {
                    // ++theImageCount; // += rset.getInt(1);   // What?  Why.  Increment, no?
                } else {
                    A.log("This will create a bad image count for code:" + code); 
                }
                */
            }

            if (theImageCount > 0) {
                hasOne = true;
                setHasImagesCount(theImageCount);
            }
            
            //A.log("setHasImages() query:" + theQuery);

        } catch (SQLException e) {
            s_log.error("setHasImages() e:" + e);
            throw e;            
        } finally {
            DBUtil.close(stmt, rset, this, "setHasImages()");
        }
        //A.log("setHasImages(" + project + ") imageCount:" + theImageCount + " query:" + theQuery);
        this.hasImages = hasOne;
    }

    private String detailXml;

    public String getDetailXml() {
        return detailXml;
    }
    public void setDetailXml(String detailXml) {
        this.detailXml = detailXml;
    }

    // Called from the specimen page (potentially). Not automatically constructed.
    public Hashtable getDetailHash() {
        return getDetailHash(getCode(), getDetailXml());
    }

    /*
    public Hashtable getFeatures() {
      return features;
    }
    public void setFeatures() throws SQLException {
        Hashtable features = null;

        // first get the XML from the database
        String theXML = getXMLFromDB();
        
        if (theXML == null) {
          if (!org.calacademy.antweb.upload.UploadAction.isInUploadProcess()) {
            s_log.warn("setFeatures() WSS.  Why is there no XML features for code:" + code);
          }
          return;
        }
        SpecimenXML handler = null;

        // then parse the sucker into the features Hashtable
        try {
            handler = new SpecimenXML();
            features = handler.parse(theXML);
        } catch (org.xml.sax.SAXParseException e) {
            s_log.info("setFeatures() Parse Exception of generated XML.  code:" + code + " e:" + e + " xml:" + theXML);
            setTheXml("Parse exception of generated XML.");
        } catch (Exception e) {
            // Mark - NPE caught here.  Should check for null?  theXML is null.  Reproduce case first...
            // Problem setting specimen features java.lang.NullPointerExceptionnullcode:psw9576-15 name:psw9576-15
            s_log.error("setFeatures() e:" + e + " code:" + code + " xml:" + theXML);
        }
        this.features = features;
    }
*/
    // Called during the specmen upload process. Specimen never constructed.
    public static Hashtable getDetailHash(String code, String theXML) {

        Hashtable detailHash = new Hashtable();

        if (theXML == null) {
            //if (!org.calacademy.antweb.upload.UploadAction.isInUploadProcess()) {
            //    s_log.warn("setFeatures() WSS.  Why is there no XML features for code:" + code);
            //}
            return detailHash;
        }
        SpecimenXML handler = null;

        try {
            handler = new SpecimenXML();
            detailHash = handler.parse(theXML);
        } catch (SAXParseException e) {
            s_log.info("parseXMLIntoHtmlMessage() Parse Exception of generated XML.  code:" + code + " e:" + e + " xml:" + theXML);
        } catch (Exception e) {
            // Mark - NPE caught here.  Should check for null?  theXML is null.  Reproduce case first...
            // Problem setting specimen features java.lang.NullPointerExceptionnullcode:psw9576-15 name:psw9576-15
            s_log.error("parseXMLIntoHtmlMessage() e:" + e + " code:" + code + " xml:" + theXML);
        }
        return detailHash;
    }


/*
update specimen set other = '
<features>
  <preparedby>PGH</preparedby>
  <dateprepared>26 Nov 2013</dateprepared>
  <abundance>1</abundance>
  <spcmauxfields>0</spcmauxfields>
  <numberspcmimages>0</numberspcmimages>
  <spcmrecorddate>26 Nov 2013</spcmrecorddate>
  <spcmrecchangeddate>26 Nov 2013</spcmrecchangeddate>
  <spcmrecchangedby>Administrator</spcmrecchangedby>
  <collxcoordinate>0</collxcoordinate><collycoordinate>0</collycoordinate>
  <collauxfields>0</collauxfields><numbercollimages>0</numbercollimages>
  <collrecorddate>26 Nov 2013</collrecorddate>
  <collrecchangeddate>29 Nov 2013</collrecchangeddate>
  <collrecchangedby>Administrator</collrecchangedby>
  <localitynameindex>Loucoumé Forest, Afog2</localitynameindex>
  <elevation>660</elevation>
  <loclatitude>-2.30757</loclatitude>
  <loclongitude>12.82985</loclongitude>
  <locauxfields>0</locauxfields>
  <numberlocimages>0</numberlocimages>
  <locrecorddate>25 Nov 2013</locrecorddate>
  <locrecchangeddate>26 Nov 2013</locrecchangeddate>
  <locrecchangedby>Administrator</locrecchangedby>
  <speciescode>Polyrhachis.curta</speciescode>
  <validsppcode>Polyrhachis.curta</validsppcode>
  <speciesauxfields>0</speciesauxfields>
  <numberspeciesimages>0</numberspeciesimages>
  <speciesrecorddate>22 Nov 2013</speciesrecorddate>
  <speciesrecchangeddate>22 Nov 2013</speciesrecchangeddate>
  <speciesrecchangedby>Administrator</speciesrecchangedby>
  <[genus]genus>Polyrhachis</[genus]genus>
</features>
' where code = "casent0250040";
*/

    // This is NOT the Description field as presented on the specimen page.
    // This is description edits.
    public void setDescription(Connection connection, boolean isManualEntry) {
    /* 
     * All Specimen description_edits are manual entry
     */
        if (!isManualEntry) {
          s_log.warn("setDescription() not is manual entry?");
        }

        Hashtable<String, String> description = new Hashtable<>();
        String taxonName = null;
        String theQuery = "";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            taxonName = AntFormatter.escapeQuotes(getTaxonName());
            theQuery = "select title, content from description_edit where code = '" + code + "'";
            
            if (isManualEntry) {
              theQuery += " and is_manual_entry = 1";
            }

            stmt = DBUtil.getStatement(connection, "setDescription()");
            rset = stmt.executeQuery(theQuery);

            String key = null;
            String value = null;
            int recordCount = 0;
            while (rset.next()) {
                recordCount++;
                key = rset.getString("title");
                value = rset.getString("content");
                value = Formatter.dequote(value);
                description.put(key, value);
 
                if (false)
                  if (AntwebProps.isDevOrStageMode())
                    if ("pseudomyrmecinaetetraponera rufonigra".equals(taxonName))
                      if ("taxonomictreatment".equals(key))
                        s_log.warn("setDescription() key:" + key + " value:" + value);
            }

            //s_log.info("setDescription() recordCount:" + recordCount + " query:" + theQuery);
            
        } catch (SQLException e) {
            s_log.error("setDescription() for taxonName:" + taxonName + " exception:" + e + " theQuery:" + theQuery);
            // project:" + project + " 
        } finally {
            DBUtil.close(stmt, rset, this, "setDescription()");
        }
        this.description = description;
    }
    
    public String getTitleString() {
		String titleString = "AntWeb Specimen: ";
		if (getGenus() != null)
			titleString += new Formatter().capitalizeFirstLetter(getGenus());
		if (getSpecies() != null)
		  titleString += " " + getSpecies();
		  titleString += " - " + getName().toUpperCase(); 
		return titleString;
    }

    public String getMetaString() {
		String metaString = "<meta name='keywords' content='Specimen ";
		if (getGenus() != null)
		  metaString+= new Formatter().capitalizeFirstLetter(getGenus());
		if (getSpecies() != null)
		  metaString += " " + getSpecies();
		metaString += " - " + getName().toUpperCase();
		metaString += ", AntWeb, ants,ant,formicidae '/>";
		metaString += "<meta name='description' content='Overview of ";
		if (getGenus() != null)
		  metaString+= new Formatter().capitalizeFirstLetter(getGenus());
		if (getSpecies() != null)
		  metaString += " " + getSpecies();
		metaString += " - " + getName().toUpperCase();
		metaString += " from AntWeb.'/>";
		return metaString;
    }


    public String getCollectionCode() {
        return collectionCode;
    }
    public void setCollectionCode(String collectionCode) {
        this.collectionCode = collectionCode;
    }
    
    public String getBioregion() {
      return bioregion;
    } 
    public void setBioregion(String bioregion) {
      this.bioregion = bioregion;
    }

    public String getAdm1() {
        // was state/Province
        return adm1;
    }
    public void setAdm1(String adm1) {
        this.adm1 = adm1;
    }

    public String getAdm2() {
        // was county
        return adm2;
    }
    public void setAdm2(String adm2) {
        this.adm2 = adm2;
    }

    public String getLocalityName() {
        return localityName;
    }
    public void setLocalityName(String localityName) {
        this.localityName = localityName;
    }

    public String getIslandCountry() {
        return islandCountry;
    }
    public void setIslandCountry(String islandCountry) {
        this.islandCountry = islandCountry;
    }

    public String getIslandOrCountryAdm1() {
        if (getIslandCountry() != null) return getIslandCountry();
        return country + ":" + adm1;
    }
	public String getLocalityString() {
		return getIslandOrCountryAdm1() + ":" + localityName;  // was province
	}

	public String getLocalityInfoString() {
        return getLocalityInfoString(null);
    }

// *** island_country?
    // This gets used on specimen-body.jsp.
	public String getLocalityLink() {
        String localityCode = getLocalityCode();
        String localityName = getLocalityName();
        String localityLink = "";

        String encodeCode = HttpUtil.encodePath(localityCode);
        if (encodeCode != null) {  // use the localityCode to link, if there is one.
            String label = localityName != null ? localityName : localityCode;  // but use the localityName to label, if there is one.
            localityLink = "<a href=\"" + AntwebProps.getDomainApp() + "/locality.do?code=" + encodeCode + "\">" + label + "</a>";
        } else { // use the localityName
            //String encodeName = HttpUtil.encodePath(localityName);
            localityLink = "<a href=\"" + AntwebProps.getDomainApp() + "/locality.do?name=" + localityName + "\">" + localityName + "</a>"; // was name = target
        }
        s_log.debug("getLocalityInfo() localityLink:" + localityLink + " encodeCode:" + encodeCode + " localityName:" + localityName);

        if (localityLink == null) localityLink = "";

/*
For a specimen like this, the localityName contains & so must use code to link:
  http://localhost/antweb/specimen.do?name=casent0762401  Must use localitycode as the Name contains &.

For a locality name without code (this name has special characters:
  http://localhost/antweb/specimen.do?code=ufv-labecol-000469
*/

        return localityLink;
    }

    
    // This gets used by the specimenReport.jsp. Rather complicated.
    //   at org.apache.jsp.search.advancedSearchResults_002dbody_jsp._jspService(advancedSearchResults_002dbody_jsp.java:1718)
	public String getLocalityInfoString(String sortBy) {
	    String localityInfoString = null;
        Formatter formatter = new Formatter();
        String localityCode = getLocalityCode();
        String countryColon = formatter.appendToNonNull(formatter.clearNull(getCountry()),":");
        String adm1Colon = formatter.appendToNonNull(formatter.clearNull(getAdm1()),":");
        String adm2Semicolon = formatter.appendToNonNull(formatter.clearNull(getAdm2()),":");
        String latLonMaxError = formatter.clearNull(getLatLonMaxError()); 
        // <!-- was:specimen.getLocXYAccuracy()  was: desc.get("locxyaccuracy") -->

        if ("country".equals(sortBy)) countryColon = "<span class=\"sorted_by\">" + countryColon + "</span>";

        String linkName = countryColon + " " + adm1Colon + " " + adm2Semicolon;

        String encodeCode = HttpUtil.encodePath(localityCode);

        String localityLink = null; // getLocalityName();

        if (encodeCode != null) {  // use the localityCode to link, if there is one.
            String label = getLocalityName() != null ? getLocalityName() : localityCode;
            //out.println("LocalityLink1:" + localityLink + " target:" + target + " name:" + getLocalityName());
            //if ("locality".equals(sortBy)) localityName =  "<span class=\"sorted_by\">" + localityName + "</span>";
            localityLink = "<a href=\"" + AntwebProps.getDomainApp() + "/locality.do?code=" + localityCode + "\">" + label + "</a>"; // was name = target
        } else { // use the localityName
            String encodeName = HttpUtil.encodePath(localityName);
            localityLink = "<a href=\"" + AntwebProps.getDomainApp() + "/locality.do?name=" + localityName + "\">" + localityName + "</a>"; // was name = target
        }

        if (localityLink == null) localityLink = "";

/*
For a specimen like this, the localityName contains & so must use code to link.  On this page, see casent0762401:
  http://localhost/antweb/browse.do?species=peperi&genus=pseudomyrmex&rank=species

For a locality name without code (this name has special characters):
  http://localhost/antweb/advancedSearch.do?searchMethod=advancedSearch&advanced=true&localityNameSearchType=equals&localityName=%22RPPN%20Cara%C3%A7a%22
*/

        if ( getLocalityName() != null
            || Utility.notBlank(getCountry())
            || Utility.notBlank(getAdm1())
            || Utility.notBlank(getAdm2())
        ) {
            localityInfoString = linkName + "&nbsp;&nbsp;" + localityLink + "";
            //A.log("getLocalityInfoString(str) linkName:" + linkName + " adm2:" + getAdm2());
        }
                
        //A.log("getLocalityInfoString(str) localityInfoStr:" + localityInfoString);
        return localityInfoString;
    }

    public String getTaxonomicBrowserParams() {
        String theParams = "";

        theParams = "subfamily=" + subfamily
              + "&genus=" + genus
              + "&species=" + species;
        if (getSubspecies() != null) {
          theParams += "&subspecies=" + subspecies;
        }
        theParams += "&";

        theParams += "code=" + code
              + "&rank=specimen";
        return theParams;
    }
    
    public String getCountry() {
        return country;
    }
    public void setCountry(String country) {
        this.country = country;
    }

    public String getTypeStatus() {
        return typeStatus;
    }
    public void setTypeStatus(String typeStatus) {
        this.typeStatus = typeStatus;
    }
    public boolean getIsType() {
        String typeStatus = getTypeStatus();
        boolean type = typeStatus != null && !"".equals(typeStatus);
        //A.log("getIsType() typeStatus:" + typeStatus + " type:" + type + " status:" + getStatus());
        if (Status.MORPHOTAXON.equals(getStatus())) {
          type = false;

        }
        return type;
    }
    
    public String getPrettyName() {
        return getFullName().toUpperCase(); // was getFullName()
    }
    public void setPrettyName() {
        this.prettyName = getFullName().toUpperCase();
    }

    public String getTaxonPrettyName() {
      Taxon taxon = Taxon.getTaxonOfRank(getSubfamily(), getGenus(), getSubgenus(), getSpecies(), getSubspecies());
      if (taxon == null) {
        s_log.warn("getTaxonPrettyName() taxon not found for subfamily:" + getSubfamily() + " genus:" + getGenus() + " species:" + getSpecies() + " subspecies:" + getSubspecies() + ". Returning:" + getTaxonName());
        return getTaxonName();
      }
      String prettyName = taxon.getPrettyName();
      s_log.debug("getTaxonPrettyName() rank:" + taxon.getRank() + " prettyName:" + prettyName);
      return prettyName;
    }

    public static String getSpecimenUrl(String code) {
      return AntwebProps.getDomainApp() + "specimen.do?code=" + code;
    }

    public static String makeLink(String code) {
        // This makes something like: 
        //        Specimen: <a href="http://www.antweb.org/specimen.do?code=casent0221924
        Formatter format = new Formatter();

        String url = AntwebProps.getSecureDomainApp() + "/specimen.do?";
        String uri = null;        
        String name = null;
        String header = "Specimen:";
        uri = "code=" + code; 

        String link = header + " " + "<a href=\"" + url + uri + "\">" 
            + code
            + "</a>";

        //s_log.info("makeLink() rank:" + rank + " subfamily:" + subfamily + " genus:" + genus + " species:" + species);                    

        return link;
    }

    /*
    Called from UploadAction.getSpecimenList()
     */
    public static String getTabDelimHeader() {
      String header = "code \t taxon_name \t subgenus \t tribe \t speciesgroup \t subfamily \t genus \t species \t other \t type_status \t subspecies \t country \t adm2 \t adm1 \t localityname \t localitycode \t collectioncode \t biogeographicregion \t decimal_latitude \t decimal_longitude \t last_modified \t habitat \t method \t ownedby \t collectedby \t life_stage \t access_group \t locatedat \t determinedby \t medium \t access_login \t elevation \t latlonmaxerror \t microhabitat \t datedetermined \t elevationmaxerror \t localitynotes \t dnaextractionnotes \t specimennotes \t created \t family \t collectionnotes \t datecollectedstart \t datecollectedend \t datecollectedstartstr \t datecollectedendstr \t datedeterminedstr \t kingdom_name \t phylum_name \t class_name \t order_name \t image_count \t status \t original_taxon_name \t line_num \t is_introduced \t museum \t bioregion \t backup_file_name \t is_male \t is_worker \t is_queen \t upload_id \t caste \t subcaste \t flag \t issue \t island_country \t";

      // Not fetched: toc 	 spcmrecorddate | spcmrecchangeddate | locrecorddate | locrecchangeddate | speciesauthor | region | subregion |
      return header;
    }
    public String getTabDelimString() {
        StringBuffer sb = new StringBuffer();
        sb.append(getCode());
        sb.append("\t" + getTaxonName());

        Taxon taxon = TaxonMgr.getTaxon(getTaxonName());
        String subgenus = "";
        if (taxon != null) subgenus = Formatter.initCap(taxon.getSubgenus());
        //A.log("getTabDelimString() specimen.subGenus:" + getSubgenus() + " taxon.subgenus:" + subgenus);
        sb.append("\t" + subgenus);
        sb.append("\t" + getTribe());
        sb.append("\t" + getSpeciesGroup());
        sb.append("\t" + Formatter.initCap(getSubfamily()));
        sb.append("\t" + Formatter.initCap(getGenus()));
        sb.append("\t" + getSpecies());
        sb.append("\t" + getDetailXml());
        sb.append("\t" + getTypeStatus());
        sb.append("\t" + getSubspecies());
        sb.append("\t" + getCountry());
        sb.append("\t" + getAdm2());
        sb.append("\t" + getAdm1());
        sb.append("\t" + getLocalityName());
        sb.append("\t" + getLocalityCode());
        sb.append("\t" + getCollectionCode());
        sb.append("\t" + getBioregion());
        sb.append("\t" + getDecimalLatitude());
        sb.append("\t" + getDecimalLongitude());
        sb.append("\t" + getLastModified());
        sb.append("\t" + getHabitat());
        sb.append("\t" + getMethod());
        //sb.append("\t" + getToc());
        sb.append("\t" + getOwnedBy());
        sb.append("\t" + getCollectedBy());
        sb.append("\t" + getLifeStage());
        sb.append("\t" + getGroupId());
        sb.append("\t" + getLocatedAt());
        sb.append("\t" + getDeterminedBy());
        sb.append("\t" + getMedium());
        //sb.append("\t" + getGetSpcmRecordDate());
        //sb.append("\t" + getSpcmRecChangedDate());
        //sb.append("\t" + getLocRecordDate());
        //sb.append("\t" + getLocRecChangedDate());
        //sb.append("\t" + getSpeciesAuthor());
        sb.append("\t" + getCuratorId());
        sb.append("\t" + getElevation());
        sb.append("\t" + getLatLonMaxError());
        sb.append("\t" + getMicrohabitat());
        sb.append("\t" + getDateDetermined());
        sb.append("\t" + getElevationMaxError());
        sb.append("\t" + getLocalityNotes());
        sb.append("\t" + getDnaExtractionNotes());
        sb.append("\t" + getSpecimenNotes());
        sb.append("\t" + getCreated());
        sb.append("\t" + getFamily());
        sb.append("\t" + getCollectionNotes());
        sb.append("\t" + getDateCollectedStart());
        sb.append("\t" + getDateCollectedEnd());
        sb.append("\t" + getDateCollectedStartStr());
        sb.append("\t" + getDateCollectedEndStr());
        sb.append("\t" + getDateDeterminedStr());
        sb.append("\t" + getKingdomName());
        sb.append("\t" + getPhylumName());
        sb.append("\t" + getClassName());
        sb.append("\t" + getOrderName());
        sb.append("\t" + getImageCount());
        sb.append("\t" + getStatus());
        sb.append("\t" + getOriginalTaxonName());
        sb.append("\t" + getLineNum());
        sb.append("\t" + getIsIntroduced());
        sb.append("\t" + getMuseum());
        sb.append("\t" + getBioregion());
        sb.append("\t" + getBackupFileName());
        sb.append("\t" + isMale());
        sb.append("\t" + isWorker());
        sb.append("\t" + isQueen());
        sb.append("\t" + getUploadId());
        sb.append("\t" + getCaste());
        sb.append("\t" + getSubcaste());
        //sb.append("\t" + getRegion());
        //sb.append("\t" + getSubregion());
        sb.append("\t" + getFlag());
        sb.append("\t" + getIssue());
        sb.append("\t" + getIslandCountry());
        return sb.toString();
    }

    public String getHabitat() {
        return habitat;
    }
    public void setHabitat(String string) {
        habitat = string;
    }

    public String getMethod() {
        return method;
    }
    public void setMethod(String string) {
        method = string;
    }

    public String getOwnedBy() {
        return ownedBy;
    }
    public void setOwnedBy(String string) {
        ownedBy = string;
    }

    public String getCollectedBy() {
        return collectedBy;
    }
    public void setCollectedBy(String string) {
        collectedBy = string;
    }


    public String getLifeStage() {
        return lifeStage;
    }
    public void setLifeStage(String lifeStage) {
        this.lifeStage = lifeStage;
    }
    public String getCaste() {
        return caste;
    }
    public void setCaste(String caste) {
        this.caste = caste;
    }
    public String getSubcaste() {
        return subcaste;
    }
    public void setSubcaste(String subcaste) {
        this.subcaste = subcaste;
    }

    public String getCasteStr() {
        if (caste == null) return "";
        if (subcaste == null) return caste;
        return caste + " - " + subcaste;
    }

    public boolean isMale() { return "male".equals(getCaste()); }
    public boolean isWorker() { return "worker".equals(getCaste()); }
    public boolean isQueen() { return "queen".equals(getCaste()); }
    public boolean isOther() { return "other".equals(getCaste()); }


    public String getOwnedByLink() {
      String link = new Formatter().clearNull(getOwnedBy());
      if (getMuseumCode() != null) {
        link = "<a href='" + AntwebProps.getDomainApp() + "/museum.do?code=" + getMuseumCode() + "'>" + link + "</a>";
      }
      return link;
    }
   
    public String getLocatedAtLink() {
        String link = new Formatter().clearNull(locatedAt);
        Museum museum = MuseumMgr.getInferredMuseum(locatedAt);
        if (museum != null) link = museum.getLink();        
        return link;
    }


    public String getLocatedAt() {
        return locatedAt;
    }
    public void setLocatedAt(String locatedAt) {
        this.locatedAt = locatedAt;
    }

    public String getLocalityCode() {
        return localityCode;
    }

    public void setLocalityCode(String localityCode) {
        this.localityCode = localityCode;
    }
     
    public String getSpecimenNotes() {
        return specimenNotes;
    }
    public void setSpecimenNotes(String specimenNotes) {
        this.specimenNotes = specimenNotes;
    }

    public String getLocalityNotes() {
        return localityNotes;
    }
    public void setLocalityNotes(String localityNotes) {
        this.localityNotes = localityNotes;
    }
    
    public String getCollectionNotes() {
        return collectionNotes;
    }
    public void setCollectionNotes(String collectionNotes) {
        this.collectionNotes = collectionNotes;
    }
    
    public String getDnaExtractionNotes() {
        return dnaExtractionNotes;
    }
    public void setDnaExtractionNotes(String dnaExtractionNotes) {
        this.dnaExtractionNotes = dnaExtractionNotes;
    }    
    
    public String getMicrohabitat() {
        return microhabitat;
    }
    public void setMicrohabitat(String microhabitat) {
        this.microhabitat = microhabitat;
    }

    public Date getLastModified() {
        return lastModified;
    }
    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }     

    public int getGroupId() {
      return groupId;
    }
    public void setGroupId(int groupId) {
      this.groupId = groupId;
    }
    public Group getGroup() {
      return GroupMgr.getGroup(groupId);
    }

    public int getCuratorId() {
      return curatorId;
    }
    public void setCuratorId(int curatorId) {
      this.curatorId = curatorId;
    }
    public Curator getCurator() {
      return LoginMgr.getCurator(curatorId);
    }
    
    public boolean isCurator(Group group) {
      if (group == null) return false;
      return getGroupId() == group.getId();
    }

    public boolean isCurator(Login login) {
      if (login == null) return false;
      return getCuratorId() == login.getId();
    }    
    
    public String getMedium() {
        return medium;
    }
    public void setMedium(String medium) {
        this.medium = medium;
    }

    public String getDeterminedBy() {
        return determinedBy;
    }
    public void setDeterminedBy(String determinedBy) {
        this.determinedBy = determinedBy;
    }

    public String getDateDetermined() {
        return dateDetermined;
    }
    public void setDateDetermined(String dateDetermined) {
        this.dateDetermined = dateDetermined;
    }       
    
    public String getDateDeterminedStr() {
        return dateDeterminedStr;
    }
    public void setDateDeterminedStr(String dateDeterminedStr) {
        this.dateDeterminedStr = dateDeterminedStr;
    }       

    public String getElevation() {
        return elevation;
    }
    public void setElevation(String elevation) {
        this.elevation = elevation;
    }
    
    public String getElevationMaxError() {
        return elevationMaxError;
    }
    public void setElevationMaxError(String elevationMaxError) {
        this.elevationMaxError = elevationMaxError;
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

    public double getDoubleLatitude() {
        return Float.valueOf(getDecimalLatitude()).doubleValue();
    }
    public double getDoubleLongitude() {
        return Float.valueOf(getDecimalLongitude()).doubleValue();
    }

    public String getLocLatitude() {
        return Float.valueOf(getDecimalLatitude()).toString();
    }
    public String getLocLongitude() {
        return Float.valueOf(getDecimalLongitude()).toString();
    }

    public String getLatLonMaxError() {
      return latLonMaxError;
    }
    public void setLatLonMaxError(String latLonMaxError) {
      this.latLonMaxError = latLonMaxError;
    }        
    


    public void setDateCollectedStart(String dateCollectedStart) {
      this.dateCollectedStart = dateCollectedStart;
    }
    public String getDateCollectedStart() {
      return dateCollectedStart;
    }

    public void setDateCollectedEnd(String dateCollectedEnd) {
      this.dateCollectedEnd = dateCollectedEnd;
    }
    public String getDateCollectedEnd() {
      return dateCollectedEnd;
    }
        

    public String getDateCollectedStartStr() {
        return dateCollectedStartStr;
    }
    public void setDateCollectedStartStr(String dateCollectedStartStr) {
        this.dateCollectedStartStr = dateCollectedStartStr;
    }          

    public String getDateCollectedEndStr() {
        return dateCollectedEndStr;
    }
    public void setDateCollectedEndStr(String dateCollectedEndStr) {
        this.dateCollectedEndStr = dateCollectedEndStr;
    }          


/*
    //So as to utilize the Taxon methods 
    public Timestamp getCreated() {
        return created;
    }
    public void setCreated(Timestamp created) {
        this.created = created;
    }   
*/
    public void setCreated(String created) {
           if (created == null) {
             this.created = null;
             return;
           } 
           try {
               java.util.Date utilDate = new SimpleDateFormat("yyyy-MM-dd").parse(created);
               Timestamp createdTs = new Timestamp(utilDate.getTime());
               
      // A.log("setCreated() created:" + created + " utilDate:" + utilDate);

               setCreated(createdTs);
           } catch (ParseException e) {
               s_log.debug("setCreated() e:" + e);
               // no action taken.
           }              
    }
    
    public boolean hasOriginalTaxonName() {
      //A.log("hasOriginalTaxonName() orig:" + getOriginalTaxonName() + " parentTaxonName:" + getParentTaxonName());
      if (getOriginalTaxonName() != null)
          return !getParentTaxonName().equals(getOriginalTaxonName());
      return false;
    }
    public String getOriginalTaxonName() {
      return originalTaxonName;
    }
    public void setOriginalTaxonName(String originalTaxonName) {
      this.originalTaxonName = originalTaxonName;
    }        

    public int getLineNum() {
        return lineNum;
    }
    public void setLineNum(int lineNum) {
        this.lineNum = lineNum;
    }

    public boolean getIsIntroduced() {
      return isIntroduced;
    }
    public void setIsIntroduced(boolean isIntroduced) {
      this.isIntroduced = isIntroduced;
    } 

/*
    // Doesn't really apply. Endemic is for genera, let alone species or specimen.
    public boolean getIsEndemic() {
      return isEndemic;
    }
    public void setIsEndemic(boolean isEndemic) {
      this.isEndemic = isEndemic;
    } 
*/
    public String getDistributionStatus() {
      if (getIsIntroduced()) return "Introduced";
      return "Native";
      // in the future, could be Endemic. Endemic is a subset of native. It is only found there.
    }
    
    public Museum getMuseum() {
      return MuseumMgr.getMuseum(museumCode);
    }    
    
    public String getMuseumCode() {
      return museumCode;
    }
    public void setMuseumCode(String museumCode) {
      this.museumCode = museumCode;
    }

    public String getBackupFileName() {
      return backupFileName;
    }
    public void setBackupFileName(String backupFileName) {
      this.backupFileName = backupFileName;
    }

    public String getDefaultFor() {
      return defaultFor;
    }
    public void setDefaultFor(String defaultFor) {
      this.defaultFor = defaultFor;
    }
    
    public int getUploadId() {
      return uploadId;
    }
    public void setUploadId(int uploadId) {
      this.uploadId = uploadId;
    }
    
    public String getFlag() {
      return flag;
    }
    public void setFlag(String flag) {
      this.flag = flag;
    }
    
    public String getIssue() {
      return issue;
    }
    public void setIssue(String issue) {
      this.issue = issue;
    }

    public String getTaxonworksCollectionObjectID() {
        return taxonworksCollectionObjectID;
    }

    private void setTaxonworksCollectionObjectID(String id) {
        this.taxonworksCollectionObjectID = id;
    }

    public String getTaxonWorksEditUrl() {
        if (taxonworksCollectionObjectID == null || taxonworksCollectionObjectID.isEmpty()) {
            return null;
        }
        return AntwebProps.getTaxonWorksEditSpecimenUrl(getTaxonworksCollectionObjectID());
    }

    // split typeStatus name to get a string of the original name so we can search for it in antcat
    private String getTypeStatusName() {
        if (typeStatus == null || typeStatus.isEmpty()) {
            return null;
        }
        Pattern typeStatusPattern = Pattern.compile("\\w*type of (?<name>(?:[A-Za-z]+ ){1,2}[a-z]+)");
        Matcher matcher = typeStatusPattern.matcher(typeStatus);

        boolean success = matcher.find();
        if (success) {
            return matcher.group("name");
        }
        return null;
    }

    public String getTypeStatusAntcatSearchURL() {
        String name = getTypeStatusName();

        if (name == null || name.isEmpty()) {
            return null;
        }

        return "https://www.antcat.org/catalog/search?qq=" + name.replace(" ", "+") + "&searching_from_header=true";
    }
    
}
