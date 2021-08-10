package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;
import java.time.*;

//import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

public class CopyrightDb extends AntwebDb {

    private static Log s_log = LogFactory.getLog(CopyrightDb.class);

    private static int lastRequest = 0;
    private static Copyright lastResponse = null;
    
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
    
    private static ArrayList<Copyright> copyrights = null;
        
    private void getCopyrights() throws SQLException {    
        Copyright copyright = null;        
        String theQuery = "select * from copyright";
        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getCopyrights()");
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                copyrights = new ArrayList<>();
                copyright = new Copyright();
                copyright.setId(rset.getInt("id"));
                copyright.setCopyright(rset.getString("copyright"));
                copyright.setYear(rset.getInt("year"));
                copyrights.add(copyright);
            }
        } finally {
            DBUtil.close(stmt, rset, "this", "getCopyrights()");
        }
    }

}
