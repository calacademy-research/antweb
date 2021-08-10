
package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.Formatter;

public class Adm1Db {
/* Data from this table is manually fetched in UploadAction to create the search select box. */

    private static Log s_log = LogFactory.getLog(Adm1Db.class);
    
    private static ArrayList<String> adm1s = new ArrayList<>();
    
    public static boolean isValid(Connection connection, String adm1){
      if (adm1s.size() == 0) {
        populate(connection);
      }
      
      // This will work sometimes...
      adm1 = Formatter.initCap(adm1);
            
      boolean contains = adm1s.contains(adm1);
      if (contains) return true;

      //s_log.warn("isValid() contains:" + contains + " adm1:" + adm1);
      return contains;
    }

    public static void populate(Connection connection) {
        Statement stmt = null;
        ResultSet rset = null;
        String query = null;
        try {
            stmt = DBUtil.getStatement(connection, "Adm1Db.populate()");

            query = "select name from adm1 order by name";        
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                String name = rset.getString("name");
                adm1s.add(name);
            }
        } catch (SQLException e) {
            s_log.error("populate() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "Adm1Db.populate()");
        }
    }

    public static ArrayList<String> getList(Connection connection) throws SQLException {
      if (adm1s.size() == 0) {
        populate(connection);
      }
      return adm1s;
    }
    
    public static String toList(Connection connection) throws SQLException {
      if (adm1s.size() == 0) {
        populate(connection);
      }    
      return adm1s.toString();
    }
}
