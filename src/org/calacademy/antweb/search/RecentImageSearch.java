package org.calacademy.antweb.search;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import java.util.*;
import java.util.Date;
import java.io.Serializable; 
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/** Class Advanced does the searching for the advanced_search.jsp page */
public class RecentImageSearch extends GenericSearch implements Serializable {

    private static Log s_log = LogFactory.getLog(RecentImageSearch.class);

    private String daysAgo;
    private String numToShow;
    protected String group;
    protected String fromDate;
    protected String toDate;
    
    protected ArrayList<ResultItem> createInitialResults() {

        //s_log.info("creating initial results for recent images");
        
        if (daysAgo == null) {
            daysAgo = "1";
        }
        
        if (group == null) {
            group = "";
        }
        
        // This would be better, but the two lists do not match.  Must adequately test.
        //String fieldList = "specimen.taxon_name, specimen.subfamily, specimen.genus, specimen.species, specimen.subspecies " 
        //       + ", image.shot_type, image.shot_number, image.upload_date, name "
          //taxon, 
        String fromString = " ant_group, artist, group_image, image left join specimen on  specimen.code = image.image_of_id ";
        
        String theQuery =
                "select specimen.code, specimen.taxon_name" 
              + ", image.shot_type, image.shot_number, image.id, image.upload_date" 
              + ", artist.name, ant_group.name, specimen.toc"
              + ", specimen.subfamily, specimen.genus, specimen.species, specimen.subspecies "   // taxon.valid,  what about status?
              + "from " + fromString 
              + "where group_image.image_id = image.id " 
              + " and ant_group.id=group_image.group_id " //specimen.taxon_name = taxon.taxon_name
              + " and image.upload_date is not null " 
              + " and artist.id = image.artist ";
            //s_log.info("days ago is " + daysAgo);
            if ((daysAgo != null) && (daysAgo.length() > 0)) {
                Utility util = new Utility();
                int daysToSub = -Integer.parseInt(daysAgo);
                GregorianCalendar cal = new GregorianCalendar();
                cal.add(Calendar.DATE, daysToSub);
                theQuery += " and image.upload_date > '" + util.getCurrentDateAndTimeString(cal.getTime()) + "'";
                
            } else if ((fromDate != null) && (toDate != null)) {
                theQuery += " and image.upload_date >= '" + fromDate + "' and image.upload_date <= '" +
                     toDate + "' ";
            }

            if ((group != null) && (group.length() > 0)) {
                theQuery += " and ant_group.id = group_image.group_id and ant_group.name='" + group + "'";
            }

            theQuery += 
               " group by specimen.taxon_name, specimen.subfamily, specimen.genus, specimen.species, specimen.subspecies " 
               + ", image.shot_type, image.shot_number, image.upload_date, ant_group.name "
             + " order by image.upload_date desc, specimen.code, image.shot_type, image.shot_number ";

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

            A.log("createInitialResults() query:" + theQuery);
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);
            
            //return getListFromRset(rset,null, Integer.parseInt(numToShow));
            return getListFromRset(GenericSearch.RECENT_IMAGE, rset, null, theQuery);
        } catch (SQLSyntaxErrorException e) {
            s_log.error("createInitialResults() !!!!FIX!!!! e:" + e + " query:" + theQuery + " To fix, see antweb/doc/dbDebug.txt. Must turn off sql_mode: ONLY_FULL_GROUP_BY in my.cnf.");
        } catch (SQLException e) {
            s_log.error("createInitialResults() e:" + e + " query:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, this, "createInitialResults()");
        }
        return null;
    }
    
    class ImageGroup {
      String taxonName;
      String subfamily;
      String genus;
      String species;
      String subspecies;
      String shotType;
      String shotNumber;
      String uploadDate;
      String groupName;
    }
    
    public void setResults() {
     // ** This is same as GenericSearch but the filterByProject is commented out

        ResultSet rset = null;
        Date startDate = new Date();
        
        // get the initial result set
        ArrayList<ResultItem> initialResults = createInitialResults();

        if (initialResults == null) {
          s_log.error("setResults() initial Results are null.");
          return;
        }

        Date now = new Date();
        s_log.info("creating inital results took " + (now.getTime() - startDate.getTime()));
        startDate = now;

        /*
        try {
          s_log.info("the first type is " + ((ResultItem) initialResults.get(0)).getShotType());
        } catch (IndexOutOfBoundsException e) {
          s_log.info("Trapped info log exception:" + e);
        }
        */


        // for each invalid name, get the valid version
        //ArrayList validResults = getValidVersion(initialResults);
        // now filter out based on project - the reason we can't do this in the
        // initial query is that junior synonyms may not be part of a project,
        // but
        // their valid names may be - we want these to show up, so we need two
        // steps here. In theory we could do it in one query - but I'm afraid
        // that one
        // query would become too complicated to understand and debug
        //
        //ArrayList thisProjectResults = filterByProject(validResults, project);
        // same kind of thing with types
        //ArrayList typeLookup = setResultTypes(imageLookup, project);
        
        this.results = initialResults;
    }
    
    public String getDaysAgo() {
        return daysAgo;
    }
    public void setDaysAgo(String daysAgo) {
        this.daysAgo = daysAgo;
    }
        
    public String getGroup() {
        return group;
    }
    public void setGroup(String group) {
        this.group = group;
    }
    public String getFromDate() {
        return fromDate;
    }
    public void setFromDate(String fromDate) {
        this.fromDate = fromDate;
    }
    public String getToDate() {
        return toDate;
    }
    public void setToDate(String toDate) {
        this.toDate = toDate;
    }
    
    public String getNumToShow() {
        return numToShow;
    }
    public void setNumToShow(String numToShow) {
        this.numToShow = numToShow;
    }
}
