package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;
import java.time.*;

//import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

/**
 * Table copyright: one row per year with the text "California Academy of Sciences, 2000-<year>", cached in a
 * static list. getCurrentCopyright inserts the current year's row the first time it is asked for. Used by the
 * Image Uploader (ImageUploadBatchAction) and ImageUploadDb (an upload batch's copyright_year).
 * Docs: docs/data-model.md (copyright).
 */
public class CopyrightDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(CopyrightDb.class);

    private static final int lastRequest = 0;
    private static Copyright lastResponse;
    
    public CopyrightDb(Connection connection) {
      super(connection);
    }

    public void newCopyright(int year) throws SQLException {

        String copyright = CopyrightDb.getCopyrightFromYear(year); 

        String theInsert = "insert into copyright(copyright, year) " 
            + " values ('" + copyright + "', " + year + ")";
            
        Statement stmt = null;    
        try {
            stmt = DBUtil.getStatement(getConnection(), "addCopyright()");
            stmt.executeUpdate(theInsert);
        } finally {
            DBUtil.close(stmt, null, "addCopyright()");
        }
    }

    public static String getCopyrightFromYear(int year) throws SQLException {
      return "California Academy of Sciences, 2000-" + year;
    }
    
    public Copyright getCurrentCopyright() throws SQLException {
        return getCopyrightByYear(DateUtil.getYear());
    }
    
    public Copyright getCopyrightByYear(int year) throws SQLException {
        if (year < 2000 || year > 2200) {
          s_log.warn("invalid year:" + year);
          return null;
        }
        if (copyrights == null) getCopyrights();

        for (Copyright copyright : copyrights) {
          if (copyright.getYear() == year) return copyright;
        }      
        
        if (year == Year.now().getValue()) {
          newCopyright(year);
          copyrights = null; // To reset. Force refresh.
          return getCopyrightByYear(year);
        }
        
        return null;
    }
    
    public Copyright getCopyrightById(int id) throws SQLException {
        if (copyrights == null) getCopyrights();

        for (Copyright copyright : copyrights) {
          if (copyright.getId() == id) return copyright;
        }      
        return null;
    }
    
    private static ArrayList<Copyright> copyrights;
        
    private void getCopyrights() throws SQLException {    
        Copyright copyright = null;        
        String theQuery = "select * from copyright";
        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getCopyrights()");
            rset = stmt.executeQuery(theQuery);

            // One list of every row (B74: the list was made anew for each row, so only the last row's year was found).
            ArrayList<Copyright> all = new ArrayList<>();
            while (rset.next()) {
                copyright = new Copyright();
                copyright.setId(rset.getInt("id"));
                copyright.setCopyright(rset.getString("copyright"));
                copyright.setYear(rset.getInt("year"));
                all.add(copyright);
            }
            copyrights = all;
        } finally {
            DBUtil.close(stmt, rset, "this", "getCopyrights()");
        }
    }

}
