package org.calacademy.antweb.search;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/** Class Advanced does the searching for the advanced_search.jsp page */
public class DescEditSearch extends GenericSearch implements Serializable {

    private static Log s_log = LogFactory.getLog(DescEditSearch.class);

    private String daysAgo;
    private String numToShow;
    protected String groupName;
    protected String fromDate;
    protected String toDate;
    
    protected ArrayList<ResultItem> createInitialResults() {

        s_log.info("creating initial results for Description Edits");
        
        if (daysAgo == null) {
            daysAgo = "1";
        }
        
        if (groupName == null) {
            groupName = "";
        }
        
        String fromString = " ant_group, artist, group_image, image left join specimen on  specimen.code = image.image_of_id ";
                
        // taxon removed        
        String theQuery =
                "select specimen.valid, specimen.code, specimen.taxon_name,  " 
                + "image.shot_type, image.shot_number, image.id, image.upload_date, " 
                + "artist.artist, ant_group.name as group_name, specimen.toc, "
                + "specimen.subfamily, specimen.genus, specimen.species ";
        theQuery += " from " + fromString + 
                "where image.upload_date is not null and " 
                + "group_image.image_id = image.id and ant_group.id=group_image.group_id and "
                + "artist.id = image.artist ";

        s_log.info("days ago is " + daysAgo);
        if (daysAgo != null && daysAgo.length() > 0) {

            int daysToSub = -Integer.parseInt(daysAgo);
            GregorianCalendar cal = new GregorianCalendar();
            cal.add(Calendar.DATE, daysToSub);
            theQuery += " and image.upload_date > '" + Utility.getCurrentDateAndTimeString(cal.getTime()) + "'";
                
        } else if (fromDate != null && toDate != null) {
            theQuery += " and image.upload_date >= '" + fromDate + "' and image.upload_date <= '" +
                toDate + "' ";
        }

        if (groupName != null && groupName.length() > 0) {
            theQuery += " and ant_group.id = group_image.group_id and ant_group.name='" + groupName + "'";
        }
            
        theQuery += " group by specimen.taxon_name, specimen.subfamily, specimen.genus, " 
            + "specimen.species, image.shot_type, image.shot_number, image.upload_date, " 
            + "group_name order by image.upload_date desc, specimen.code, image.shot_type, image.shot_number ";

        s_log.info("createInitialResults() query:" + theQuery);

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);
            
            return getListFromRset(GenericSearch.DESC_EDIT, rset, null, theQuery);
        } catch (Exception e) {
            s_log.error("createInitialResults() e:" + e + " " + theQuery);
        } finally {
            DBUtil.close(stmt, rset, this, "createInitialResults()");
        }
        return null;
    }

    public String getNumToShow() {
        return numToShow;
    }

    public void setNumToShow(String numToShow) {
        this.numToShow = numToShow;
    }

    public String getDaysAgo() {
        return daysAgo;
    }
    public void setDaysAgo(String daysAgo) {
        this.daysAgo = daysAgo;
    }
    
    public String getGroupName() {
        return groupName;
    }
    public void setGroupName(String groupName) {
        this.groupName = groupName;
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
}
