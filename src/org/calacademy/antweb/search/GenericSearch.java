package org.calacademy.antweb.search;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.Formatter;

import java.util.*;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.Serializable;
import java.sql.*;

import org.apache.regexp.RE;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * Class GenericSearch has the methods used by all the different types of
 * search. The methods generally overridden by subclasses are
 * createInitialResults() which is where the initial query string is, and
 * sometimes getListFromRset() which turns the results of the inital query into
 * an arraylist of searchitems.
 */

public class GenericSearch implements Serializable {

    private static Log s_log = LogFactory.getLog(GenericSearch.class);
    
    protected String name;
    protected String taxonName;
    protected String searchType;
    protected String imagesOnly;
    protected String types;
    protected String project;
    protected int geolocaleId;
    protected Connection connection;
    protected ArrayList<ResultItem> results; 
    protected Pattern inQuotes = Pattern.compile("(\".*?\")");
    
    public ArrayList<ResultItem> getResults() throws SearchException {

        if (results == null) {
            setResults();
        }
        
        //A.log("GenericSearch.getResults() list:" + results.get(0).getDateCollectedStart());
        return results;
    }

    public void setResults() throws SearchException {
        //A.log("GenericSearch.setResults()");
        //    first do a big search getting images, types, and the validity
        //    then for the invalid ones - do another search to get the valid names
        //    put all of these in an array
        //    then filter out the ones that aren't in the project, if there is one
        // the result classes will figure out how to deal with the image only
        // searches and type only searches

        ResultSet rset = null;

        Date startDate = new Date();
        
        // get the initial result set
        ArrayList<ResultItem> initialResults = createInitialResults();

        Date now = new Date();
        A.log("setResults() took " + (now.getTime() - startDate.getTime()) + " initialSize:" + initialResults.size());
        
        // for each invalid name, get the valid version
        //ArrayList validResults = getValidVersion(initialResults);

        now = new Date();
        //s_log.info("getting valid version took " + (now.getTime() - startDate.getTime()));
        
        // now filter out based on project - the reason we can't do this in the
        // initial query is that junior synonyms may not be part of a project, but
        // their valid names may be - we want these to show up, so we need two
        // steps here. In theory we could do it in one query - but I'm afraid
        // that one query would become too complicated to understand and debug (Thau).
        ArrayList<ResultItem> thisProjectResults = filterByProject(initialResults, project);
        
        // same kind of thing with types
        //ArrayList typeLookup = setResultTypes(imageLookup, project);
        
        this.results = thisProjectResults;

    }

    protected ArrayList<ResultItem> filterByProject(ArrayList<ResultItem> currentList, String project) {
        //A.log("GenericSearch.filterByProject() project:" + project);
        if ((project == null) 
                || (project.length() <= 0)
                || project.equals(Project.WORLDANTS) 
                || project.equals(Project.ALLANTWEBANTS) 
                || project.equals("default") 
                || (currentList == null)
                || (currentList.size() == 0)) {
            return currentList;
        }

        ArrayList<ResultItem> theList = new ArrayList();
        ArrayList<String> addedList = new ArrayList();

        Iterator currIter = currentList.iterator();
        ResultItem thisItem = null;
        String theQuery = "select taxon.taxon_name from taxon, proj_taxon where taxon.taxon_name in ";
        String theIn = "";
        StringBuffer inBuffer = new StringBuffer();
        String thisName = "";

        while (currIter.hasNext()) {
            thisItem = (ResultItem) currIter.next();
            thisName = thisItem.getName();
            thisName = AntFormatter.escapeQuotes(thisName);
            if (!addedList.contains(thisName)) {

                if (inBuffer.length() != 0) {
                    inBuffer.append(",");
                }
                
                inBuffer.append("'");
                inBuffer.append(thisName);
                inBuffer.append("'");
                addedList.add(thisName);
            }
        }
        theQuery += "(" + inBuffer.toString() + ") ";
        theQuery += " and proj_taxon.project_name = '" + project + "' and ";
        theQuery += " taxon.taxon_name = proj_taxon.taxon_name";

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

            ArrayList resArray = new ArrayList();
            while (rset.next()) {
                resArray.add(rset.getString(1));
            }

            Iterator newIter = currentList.iterator();

            while (newIter.hasNext()) {
                thisItem = (ResultItem) newIter.next();
                if (resArray.indexOf(thisItem.getName()) != -1) {
                    theList.add(thisItem);
                }
            }
            return theList;
        } catch (SQLException e) {
            s_log.error("filterByProject() query:" + theQuery + " " + e);
            return null;
        } finally {
            DBUtil.close(stmt, rset, this, "filterByProject()");
        }
    }
    
    // to speed this up a bit, there are two passes.
    // the first pass deals with cases where we have the specimen code
    // in this case we can execute just one query to get the types of all
    // of these
    // The second pass deals with cases where we don't have the specimen
    // code.  For these, we'll execute a prepared statement
    //
    protected ArrayList<SearchItem> setResultTypes(ArrayList<SearchItem> currentList, String project) {
        //A.log("GenericSearch.setResultTypes() project:" + project);

        if ((currentList == null) || (currentList.size() == 0)) {
            return currentList;
        }

        Iterator currIter = currentList.iterator();
        SearchItem thisItem = null;
        String family, subfamily, genus, species, code = null;
        StringBuffer theQuery = new StringBuffer();
        ArrayList where = new ArrayList();
        HashMap specimens = new HashMap();
        String type = "";
        
        Date startTime = new Date();
        Date now = new Date();
        
        String preparedQuery = 
            "select specimen.type_status from specimen";
        
        if ((project != null) 
            && (project.length() > 0) 
            && (!project.equals(Project.WORLDANTS))
            && (!project.equals(Project.ALLANTWEBANTS))
        ) {
            preparedQuery += ", proj_taxon ";
        }
        
        preparedQuery += " where type_status != '' ";
            
        if ((project != null) 
            && (project.length() > 0)
            && (!project.equals(Project.WORLDANTS))
            && (!project.equals(Project.ALLANTWEBANTS))
        ) {
            preparedQuery += " and proj_taxon.project_name = '" + project + "' ";
            preparedQuery += " and proj_taxon.taxon_name = specimen.taxon_name ";
        }            
        preparedQuery += " and specimen.family = ? and specimen.subfamily = ? and specimen.genus = ? and specimen.species = ?";
            
        PreparedStatement prepStmt = null;        
        ResultSet rset = null;
        try {
            prepStmt = connection.prepareStatement(preparedQuery);
            
            while (currIter.hasNext()) {

                thisItem = (SearchItem) currIter.next();
                family = AntFormatter.escapeQuotes(thisItem.getFamily());
                subfamily = AntFormatter.escapeQuotes(thisItem.getSubfamily());
                genus = AntFormatter.escapeQuotes(thisItem.getGenus());
                species = AntFormatter.escapeQuotes(thisItem.getSpecies());
                code = AntFormatter.escapeQuotes(thisItem.getCode());

                // maintain a list of specimen codes for the second part
                if (code != null) {
                    specimens.put(code, "");
                } else {
                    prepStmt.setString(1,family);
                    prepStmt.setString(2,subfamily);
                    prepStmt.setString(3,genus);
                    prepStmt.setString(4,species);
                    
                    rset = prepStmt.executeQuery();
                    while (rset.next()) {
                        type = rset.getString(1);
                        if (type.length() > 0) {
                            thisItem.setType(type);
                        }
                    }
                }
            }

        } catch (SQLException e) {
            s_log.error("setResultTypes() 1 theQuery:" + theQuery + " e:" + e);
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        } finally {
            DBUtil.close(prepStmt, rset, this, "setResultTypes() 1");
        }

        
        now = new Date();
        //s_log.info("doing the preps took " + (now.getTime() - startTime.getTime()));
        startTime = now;
            
        // now do the specimen code part
        // first get the codes and put them in the hash
        if (specimens.keySet().size() > 0) {
            theQuery.append("select code, type_status from specimen where code in ");
            StringBuffer specString = new StringBuffer();
            Set specs = specimens.keySet();
            Iterator codeIter = specs.iterator();
            while (codeIter.hasNext()) {
                if (specString.length() > 0) {
                    specString.append(",");
                }
                specString.append("'");
                specString.append((String) codeIter.next());
                specString.append("'");
            }
            theQuery.append("(");
            theQuery.append(specString.toString());
            theQuery.append(")");
    
            now = new Date();
            //s_log.info("preparing the specimen query took " + (now.getTime() - startTime.getTime()));
            startTime = now;
                
            Statement stmt = null;
            rset = null;
            try {
                stmt = connection.createStatement();
                rset = stmt.executeQuery(theQuery.toString());
                
                now = new Date();
                //s_log.info("executing the specimen query took " + (now.getTime() - startTime.getTime()));
                startTime = now;        
                
                while (rset.next()) {
                    code = rset.getString(1);
                    type = rset.getString(2);
                    if (type.length() > 0) {
                        specimens.put(code, type);
                    }
                }
            } catch (SQLException e) {
                s_log.error("setResultTypes() 2 theQuery:" + theQuery + " e:" + e);
                org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
            } finally {
                DBUtil.close(stmt, rset, this, "setResultTypes() 2");
            }        
        }

        now = new Date();
        //s_log.info("parsing the specimen query took " + (now.getTime() - startTime.getTime()));
        startTime = now;
        
        // now loop through the items, setting the code as appropriate

        currIter = currentList.iterator();
        while (currIter.hasNext()) {
            thisItem = (SearchItem) currIter.next();
            code = AntFormatter.escapeQuotes(thisItem.getCode());

            if (specimens.containsKey(code)) {
                thisItem.setType((String) specimens.get(code));
            }
        }
        
        now = new Date();
        //s_log.info("setting the items took " + (now.getTime() - startTime.getTime()));
        startTime = now;
        
        return currentList;
    }

    protected ArrayList<ResultItem> createInitialResults() throws SearchException {
        //A.log("GenericSearch.createInitialResults()");

        // Need Subspecies logic?!
    
        // Added family Jul 22, 2018
        
        String theQuery = null;
        String genus = null;
        String species = null;
        String subspecies = null;

        // if this query has more than one term assume the first is the genus and the rest is the species
        if (name.indexOf(" ") != -1) {
            StringTokenizer toke = new StringTokenizer(name, " ");
            genus = toke.nextToken();
            species = toke.nextToken();
        }

        theQuery = "select sp.taxon_name, sp.family, sp.subfamily, sp.genus, sp.species, sp.subspecies, sp.type_status, sp.code "
            + " , sp.stutus "
            + " , sp.life_stage, sp.caste, sp.subcaste"
            + " , sp.medium, sp.specimennotes "
            + " , count(image.id) as imagecount "
            + " from specimen as sp " 
            + " left outer join image on sp.code = image.image_of_id " 
            + " where (";

        if ((genus != null) && (species != null)) {
            theQuery += getSearchString("sp.genus", "equals", genus)
                + " and " + getSearchString("sp.species", "equals", species)
                + ") ";
        } else {
            theQuery += getSearchString("sp.family", searchType, name)
                + " or " + getSearchString("sp.subfamily", searchType, name)
                + " or " + getSearchString("sp.genus", searchType, name)
                + " or " + getSearchString("sp.species", searchType, name) + ") ";
        }
        theQuery += " group by sp.taxon_name, sp.family, sp.subfamily, sp.genus, sp.species, sp.type_status, sp.code, sp.status" 
          + " , sp.life_stage, sp.caste, sp.subcaste, sp.medium, sp.specimennotes ";
        A.log("createInitialResults() query:" + theQuery);

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);
            return getListFromRset(GENERIC, rset, null, theQuery);
        } catch (SQLException e) {
            s_log.error("createInitialResults() query:"  + theQuery + " e:" + e);
            throw new SearchException(e.toString());
        } finally {
            DBUtil.close(stmt, rset, this, "createInitialResults()");
        }        
    }

    protected ArrayList<ResultItem> getListFromRset(ResultSet rset, SearchItem synonymousItem, String theQuery, int numToShow) {
        //A.log("GenericSearch.getListFromRset(rset..)"); 
        ArrayList<ResultItem> bigList = getListFromRset(GENERIC, rset, synonymousItem, theQuery);
        ArrayList<ResultItem> smallList = new ArrayList<ResultItem>();
        int loop = 0;
        int bigListSize = bigList.size();
        while ((loop < numToShow)  && (loop < bigListSize)){
            smallList.add(bigList.get(loop));
            loop++;
        }
        return smallList;        
    }

    public static int GENERIC = 0;
    public static int RECENT_IMAGE = 1;
    public static int ADVANCED = 2;
    public static int IMAGEPICK = 3;
    public static int BAY_AREA = 4;
    public static int DESC_EDIT = 5;
    public static int SEARCH = 6;
    
    protected ArrayList<ResultItem> getListFromRset(int searchType, ResultSet rset, SearchItem synonymousItem, String theQuery) {
        //A.log("GenericSearch.getListFromRset(searchType...)"); 
        ArrayList<ResultItem> theList = new ArrayList<ResultItem>();
        String family = null;
        String subfamily = null;
        String genus = null;
        String species = null;
        String subspecies = null;
        String code = null;
        int imageCount = 0;
        String type = null;
        ArrayList rank = null;
        String typeOriginalCombination = null;
        Iterator rankIterator = null;
        //ResultItem item = null;
        String thisRank = null;
        boolean hasImages = false;
        boolean hasTypes = false;
        //int valid = 0;
        String status = null;
        String country = null;
        String adm1 = null;
        String adm2 = null;
        String localityName = null;
        String localityCode = null;
        String collectionCode = null;
        String lifeStage = null;
        String caste = null;
        String subcaste = null;
        String medium = null;
        String specimenNotes = null;
        String artist = null;
        String group = null;
        String shotType = null;
        String shotNumber = null;
        String uploadDate = null;
        int imageId = -1;

        // Added Feb 1, 2013 by markj
        String habitat = "";
        String microhabitat = "";
        String method = "";
        String dnaExtractionNotes = "";
        String determinedBy = "";
        String collectedBy = "";
        String museumCode = "";
        String dateCollectedStart = null; 
        int accessGroup = 0; 
        String groupName = "";
        String ownedBy = "";

        // added May 14, 2016 by Mark
        String locatedAt = "";
        
        String elevation = "";
        float decimalLatitude = 0;
        float decimalLongitude = 0;
        
        String museum = null;
        String created = null;
        String bioregion = null;
        
        int uploadId = 0;
        
        int counter = 0;
        try {
            ResultSetMetaData meta = rset.getMetaData();
            //s_log.info("getListFromRset() searchType:" + searchType + " meta.columnCount:" + meta.getColumnCount() + " query:" + theQuery);

// RecentImageSearch.java  first columns: taxon.valid, specimen.code, specimen.taxon_name, image.shot_type, image.shot_number
// AdvancedSearch.java     first columns: taxon.taxon_name, taxon.subfamily, taxon.genus, taxon.species, taxon.valid, sp.type, sp.code
// ImagePickSearch.java    first columns: taxon.taxon_name, taxon.subfamily, taxon.genus, taxon.species, favorite_images.specimen
// BayAreaSearch.java      first columns: taxon.subfamily, taxon.genus, taxon.species, taxon.taxon_name, taxon.valid, sp.county
// Search.java             first columns: taxon.taxon_name, taxon.subfamily, taxon.genus, taxon.species, sp.type, sp.code, taxon.valid

            while (rset.next()) {
            
                //A.log("getListFromRset() counter:" + counter);            

                counter++;
                
// Won't this always be. Shouldn't we test the criteria, not the query? Field list will trigger these, no?                
/*
select specimen.code, specimen.taxon_name, image.shot_type, image.shot_number, image.id, image.upload_date
  , artist.artist, groups.name, specimen.toc, specimen.subfamily, specimen.genus, specimen.species
  , specimen.subspecies from  groups, artist, group_image, image 
  left join specimen on specimen.code = image.image_of_id where group_image.image_id = image.id  
    and groups.id=group_image.group_id  and image.upload_date is not null  
    and artist.id = image.artist  
    and image.upload_date > '1745-07-31 13:31:53' 
    group by specimen.taxon_name, specimen.subfamily, specimen.genus, specimen.species, specimen.subspecies , image.shot_type, image.shot_number, image.upload_date, groups.name  order by image.upload_date desc, specimen.code, image.shot_type, image.shot_number  
    To fix, see antweb/doc/dbDebug.txt. Must turn off sql_mode: ONLY_FULL_GROUP_BY in my.cnf.
*/

                if (theQuery.contains("taxon_name"))
                    name = rset.getString(rset.findColumn("taxon_name"));
                    
                /* Advanced search will always have family. DescEdit and recentEdit search only has subfamily." */    
                if (theQuery.contains("family"))
                    // and the instance of family is not actually a subfamily...
                    if (theQuery.indexOf("family") != theQuery.indexOf("subfamily") + 3) {
                      family = rset.getString(rset.findColumn("family"));
                      //A.log("getListFromRSet() found family:" + family);
                    } else {
                      A.log("getListFromRSet() They are the same. Dodged a bullet on that one. fi:" + theQuery.indexOf("family") + " si:" + (theQuery.indexOf("subfamily") + 3) + " query:" + theQuery);
                    
                    }
                if (theQuery.contains("subfamily"))
                    subfamily = rset.getString(rset.findColumn("subfamily"));
                if (theQuery.contains("genus"))
                    genus = rset.getString(rset.findColumn("genus"));
                if (theQuery.contains("species"))
                    species = rset.getString(rset.findColumn("species"));
                if (theQuery.contains("subspecies")) {
                    subspecies = rset.getString(rset.findColumn("subspecies"));
                    //A.log("getListFromRSet() subspecies:" + subspecies);
                }
                if (theQuery.contains("code")) {
                    code = rset.getString(rset.findColumn("code"));
                    if ("fmnhins0000049526".equals(code)) A.log("getListFromRSet() code:" + code);
                }
                if (theQuery.contains("country"))
                    country = rset.getString(rset.findColumn("country"));
                if (theQuery.contains("adm1"))
                    adm1 = rset.getString(rset.findColumn("adm1"));
                if (theQuery.contains("adm2"))
                    adm2 = rset.getString(rset.findColumn("adm2"));
                if (theQuery.contains("localityname"))
                    localityName = rset.getString(rset.findColumn("localityname"));
                if (theQuery.contains("localitycode"))
                    localityCode = rset.getString(rset.findColumn("localitycode"));
                if (theQuery.contains("collectioncode"))
                    collectionCode = rset.getString(rset.findColumn("collectioncode"));                    
                if (theQuery.contains("life_stage"))
                    lifeStage = rset.getString(rset.findColumn("life_stage"));
                if (theQuery.contains("sp.caste"))
                    caste = rset.getString(rset.findColumn("caste"));
                if (theQuery.contains("subcaste"))
                    subcaste = rset.getString(rset.findColumn("subcaste"));
                if (theQuery.contains("medium"))
                    medium = rset.getString(rset.findColumn("medium"));
                if (theQuery.contains("specimennotes"))
                    specimenNotes = rset.getString(rset.findColumn("specimennotes"));
                if (theQuery.contains("artist.name"))
                    artist = rset.getString(rset.findColumn("artist.name"));
                if (theQuery.contains("group_name"))
                    group = rset.getString(rset.findColumn("group_name"));
                if (theQuery.contains("shot_type,"))  // comma to differentiate from a join condition inclusion
                    shotType = rset.getString(rset.findColumn("shot_type"));
                if (theQuery.contains("shot_number"))
                    shotNumber = rset.getString(rset.findColumn("shot_number"));
                //if (theQuery.contains("created")) // was upload_date, same thing.
                //    uploadDate = rset.getString(rset.findColumn("created"));
                if (theQuery.contains("upload_date")) // was upload_date, same thing.
                    uploadDate = rset.getString(rset.findColumn("upload_date"));
                if (theQuery.contains("image.id,")) {                  // comma included to exclude count(id)
                    imageId = rset.getInt(rset.findColumn("image.id")); // changed in release 8.6 from uid  Was uid. Then id.
                    A.log("found id is it really? imageID:" + imageId + " query:" + theQuery);
                }
                if (theQuery.contains("imagecount"))
                    imageCount = rset.getInt(rset.findColumn("imagecount"));
                if ((theQuery.contains("type")) && !(searchType == RECENT_IMAGE))  // contains shot_type
                    type = rset.getString(rset.findColumn("type_status"));
                if (theQuery.contains("status"))
                  status = rset.getString(rset.findColumn("status"));
                if (theQuery.contains("habitat"))                
                    habitat = rset.getString(rset.findColumn("habitat"));
                if (theQuery.contains("microhabitat"))                
                   microhabitat = rset.getString(rset.findColumn("microhabitat"));             
                if (theQuery.contains("method")) 
                    method = rset.getString(rset.findColumn("method"));  
                //A.log("getListFromRset() dnaExtractionNotes:" + dnaExtractionNotes);                      
                if (theQuery.contains("dnaextractionnotes"))                
                    dnaExtractionNotes = rset.getString(rset.findColumn("dnaextractionnotes"));
                if (theQuery.contains("determinedby"))                
                    determinedBy = rset.getString(rset.findColumn("determinedby"));
                if (theQuery.contains("collectedby"))
                    collectedBy = rset.getString(rset.findColumn("collectedby"));
                if (theQuery.contains("museum"))
                    museumCode = rset.getString(rset.findColumn("museum"));
                if (theQuery.contains("datecollectedstart"))
                    dateCollectedStart = rset.getString(rset.findColumn("datecollectedstartstr"));
                if (theQuery.contains("access_group"))
                    accessGroup = rset.getInt(rset.findColumn("access_group"));
                if (theQuery.contains("groups.name")) { // was groupname
                    groupName = rset.getString(rset.findColumn("groups.name"));                    
                }
                if (theQuery.contains("ownedby"))
                    ownedBy = rset.getString(rset.findColumn("ownedby"));

                if (theQuery.contains("locatedat"))
                    locatedAt = rset.getString(rset.findColumn("locatedat"));
                    
                if (theQuery.contains("elevation"))
                    elevation = rset.getString(rset.findColumn("elevation"));
                if (theQuery.contains("decimal_latitude"))
                    decimalLatitude = rset.getFloat(rset.findColumn("decimal_latitude"));
                if (theQuery.contains("decimal_longitude"))
                    decimalLongitude = rset.getFloat(rset.findColumn("decimal_longitude"));                    

                if (theQuery.contains("created"))
                    created = rset.getString(rset.findColumn("created"));

                if (theQuery.contains("museum")) 
                    museum = rset.getString(rset.findColumn("museum"));
                    
                if (theQuery.contains("bioregion"))
                    bioregion = rset.getString(rset.findColumn("bioregion"));

                if (theQuery.contains("upload_id"))
                    uploadId = rset.getInt(rset.findColumn("upload_id"));
                    //A.log("GenericSearch.getListFromRSet() uploadId:" + uploadId);                                      
                                       
                                       
                rank = Rank.getRankList(name, subfamily, genus, species, subspecies);
                rankIterator = rank.iterator();
                while (rankIterator.hasNext()) {
                    thisRank = (String) rankIterator.next();

                    //A.log("GenericSearch().getListFromRSet() name:" + name + " adm1:" + adm1 + " adm2:" + adm2); // got it.
                    ResultItem resultItem = new ResultItem(name, code, family, subfamily, genus, species, subspecies
                        , thisRank, imageCount, type, null                      // was hasImages instead of imageCount
                        , status, country, adm1, adm2, localityName, localityCode  //valid was in place of status
                        , collectionCode
                        , lifeStage, caste, subcaste
                        , medium, specimenNotes, artist, group, shotType, shotNumber, uploadDate, imageId
                        , habitat, microhabitat, method, dnaExtractionNotes, determinedBy, collectedBy, museumCode
                        , dateCollectedStart, accessGroup, groupName, ownedBy, locatedAt
                        , elevation, decimalLatitude, decimalLongitude
                        , museum, created, bioregion, uploadId
                        );

                    //A.log("GenericSearch.getListFromRset() dateCollected:" + dateCollectedStart + " searchItem.dateCollected:" + resultItem.getDateCollectedStart());
                    //AntwebUtil.logStackTrace();
                    //A.log("getListFromRSet() code:" + code + " created:" + created + " uploadId:" + resultItem.getUploadId());
                    //A.log("getListFromRset() resultItem.dateCollected:" + resultItem.getDateCollectedStart());
                    theList.add(resultItem);
                    //A.log("GenericSearch().getListFromRSet() 1st:" + theList.get(0));
                }
            }
        } catch (SQLException e) {
            s_log.error("getListFromRset() e:" + e + " query:" + theQuery);
            AntwebUtil.logStackTrace(e);
        }

        // AntwebUtil.logStackTrace();
		/*
			at org.calacademy.antweb.search.GenericSearch.getListFromRset(GenericSearch.java:624)
			at org.calacademy.antweb.search.AdvancedSearch.createInitialResults(AdvancedSearch.java:300)
			at org.calacademy.antweb.search.AdvancedSearch.setResults(AdvancedSearch.java:332)
			at org.calacademy.antweb.search.GenericSearch.getResults(GenericSearch.java:46)
			at org.calacademy.antweb.search.AdvancedSearchAction.getSearchResults(AdvancedSearchAction.java:196)
			at org.calacademy.antweb.search.SearchAction.doAdvancedSearch(SearchAction.java:117)
			at org.calacademy.antweb.search.SearchAction.advancedSearch(SearchAction.java:100)
		*/

        return theList;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    protected String getSearchString(String property, String searchType, String value) {
        //A.log("GenericSearch.getSearchString)");
        if (searchType == null) {
          //if ("sp.ownedBy".equals(property)) 
          searchType = "equals";
          //s_log.warn("getSearchString() searchType:" + searchType + " for property:" + property + " value:" + value);
        }
        
        StringBuffer sb = new StringBuffer();
        String operator;
        String leftPercent;
        String rightPercent;

        if (searchType.equals("equals") || searchType.equals("equal")) {
            operator = "=";
            leftPercent = "";
            rightPercent = "";
        } else if (searchType.equals("notEquals") || searchType.equals("notEqual")) {
            operator = "!=";
            leftPercent = "";
            rightPercent = "";
        } else if (searchType.equals("contains")) {
            operator = "like";
            leftPercent = "%";
            rightPercent = "%";
        } else if (searchType.equals("begins")) {
            operator = "like";
            leftPercent = "";
            rightPercent = "%";
        } else if (searchType.equals("ends")) {
            operator = "like";
            leftPercent = "%";
            rightPercent = "";
        } else if (searchType.equals("greaterThanOrEqual")) {
            operator = ">=";
            leftPercent = "";
            rightPercent = "";
        } else if (searchType.equals("lessThanOrEqual")) {
            operator = "<=";
            leftPercent = "";
            rightPercent = "";
        } else {
            return " 1=1 ";   // This is new to avoid malformed queries.  Fairly untested performance wise.
        }
        
        A.log("getSearchString() property:" + property + " ");
        if ("sp.taxon_name".equals(property) && value != null && "(".equals(value.substring(0,1))) {
          A.log("getSearchString() value:" + value);
          return property + " in " + value;      
        }
        
        ArrayList<String> elements = new ArrayList<String>();
        Matcher m = inQuotes.matcher(value);
        String thisElement = "";
        int length = 0;
        while(m.find()) {
            thisElement = m.group();
            
            // take off the quotes
            length = thisElement.length();
            thisElement = thisElement.substring(1, length-1);
            elements.add(thisElement);
        }
        
        // pull quoted stuff out of the string
        String newValue = m.replaceAll("");
        
        if (newValue.length() > 0) {
            List<String> newElements =  Arrays.asList(newValue.split("[, ]"));
            elements.addAll(newElements);
        }
        for (int loop = 0; loop < elements.size(); loop++) {
            thisElement = elements.get(loop);
            if (thisElement.length() > 0) {
              sb.append(property);
              sb.append(" ");
              sb.append(operator);
              sb.append(" \'");
              sb.append(leftPercent);
              sb.append(AntFormatter.escapeQuotes(thisElement.trim()));
              sb.append(rightPercent);
              sb.append("\'");
              if ((thisElement.length() > 0) && (loop < (elements.size() - 1))) {
                  sb.append(" or ");
              }
            }
        }
        //sb.append(")");

        String returnStr = sb.toString();

        // debugging        
        if ("sp.caste like '%male%'".equals(returnStr)) {
          returnStr = "sp.caste = 'male'"; 
          A.log("getSearchString() returnStr:" + returnStr);
        }
        if ("sp.caste like '%worker%'".equals(returnStr))
          returnStr = "sp.caste = 'worker'"; 
        if ("sp.caste like '%queen%'".equals(returnStr))
          returnStr = "sp.caste = 'queen'"; 
        if ("sp.caste like '%other%'".equals(returnStr))
          returnStr = "sp.caste = 'other'"; 

        //A.log("GenericSearch. VERIFY returnStr:" + returnStr);
        //A.log("getSearchString() GROUPNAME property:" + property + " value:" + value); // + " id:" + group.getId());
        if (property.contains("groupName")) {
           Group group = GroupMgr.getGroup(value);
           if (group != null) {
             returnStr = "access_group = " + group.getId();        
           }
        }

        if ((returnStr == null) || (returnStr.equals(""))) {
          returnStr = " 1 = 1 ";
        }
        
        //A.log("getSearchString() property:" + property + " searchType:" + searchType + " value:" + value + " returnStr:" + returnStr);
        
        return returnStr;
    }
    public String getSearchType() {
        return (this.searchType);
    }

    public void setSearchType(String searchType) {
        this.searchType = searchType;
    }

    public String getName() {
        return (this.name);
    }
    public void setName(String name) {
        this.name = name.trim();
    }

    public String getTaxonName() {
        return (this.taxonName);
    }
    public void setTaxonName(String name) {
        this.taxonName = name;
    }

    public String getProject() {
        return (this.project);
    }

    public void setProject(String project) {
        this.project = project;
    }

    public int getGeolocaleId() {
        return geolocaleId;
    }
    public void setGeolocaleId(int geolocaleId) {
        this.geolocaleId = geolocaleId;
    }    

    public String getTypes() {
        return (this.types);
    }

    public void setTypes(String types) {
        this.types = types;
    }

}
