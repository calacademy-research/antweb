
package org.calacademy.antweb.home;

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

public class SpecimenUploadLookup {
/* Data from this table is manually fetched in UploadAction to create the search select box. */

    private static Log s_log = LogFactory.getLog(SpecimenUploadLookup.class);
    
    private static ArrayList specimenUploads = new ArrayList();    

    public static void populate(Connection connection) throws SQLException {
        String theQuery = "select g.name name, count(s.code) count from specimen s left join ant_group g on s.access_group = g.id group by s.access_group";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "SpecimenUploadLookup.populate()");
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                String name = rset.getString("name");
                String count = rset.getString("count");
                specimenUploads.add(name + ": " + count);
            }
        } finally {
          DBUtil.close(stmt, rset, "SpecimenUploadLookup.populate()");        
        }
    }

    public static ArrayList getList(Connection connection) throws SQLException {
      if (specimenUploads.size() == 0) {
        populate(connection);
      }    
      return specimenUploads;
    }
    
    public static String toList(Connection connection) throws SQLException {
      if (specimenUploads.size() == 0) {
        populate(connection);
      }
      return specimenUploads.toString();
    }

}
