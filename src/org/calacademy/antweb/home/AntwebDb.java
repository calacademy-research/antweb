package org.calacademy.antweb.home;

/*
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
*/

import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;


/**
 * Base class of the home package's database classes (the *Db classes). Holds the connection the caller
 * opened (the caller commits or rolls back) and a few taxon checks shared by the subclasses: isExists,
 * isGenusExists, isSubfamilyForGenus (TaxonDb, HomonymDb) and delete (one taxon row). Table taxon.
 * Docs: docs/data-model.md.
 */
public class AntwebDb {

    private static final Log s_log = LogFactory.getLog(AntwebDb.class);

    static int MAXLENGTH = 80;
    final String currentDateFunction = "now()";

    Connection m_connection;
    
    AntwebDb() {
    }
    
    public AntwebDb(Connection connection) {
      m_connection = connection;
    }
    
    Connection getConnection() {
      return m_connection;
    }
    void setConnection(Connection connection) {
      m_connection = connection;
    }
    
    public boolean isSubfamilyForGenus(String query, String subfamily) throws SQLException {
        // Called from TaxonDb and HomonymDb.
    
        boolean isValidSubfamilyForGenus = false;
        String selectedSubfamilies = "";
    
        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(getConnection(), "isSubfamilyForAGenus()");
          stmt.execute(query);
          rset = stmt.getResultSet();
          while (rset.next()) {
              String selectSubfamily = rset.getString(1);
              if (subfamily.equals(selectSubfamily)) {
                isValidSubfamilyForGenus = true;
                break; // return true;  
              } else {
                selectedSubfamilies += ", " + selectSubfamily;
                //isValidSubfamilyForGenus = false;
              }
          }   
        } finally {
          DBUtil.close(stmt, rset, "isSubfamilyForAGenus()");
        }
        
        if (false && AntwebProps.isDevMode() && !isValidSubfamilyForGenus) s_log.warn("isSubfamilyForGenus() false for subfamily:" + subfamily + ".  Found:" + selectedSubfamilies); // + " query:" + query);

        if (!isValidSubfamilyForGenus) s_log.debug("isSubfamilyForGenus() isValid:" + isValidSubfamilyForGenus + " subfamily:" + subfamily + " query:" + query);
        return isValidSubfamilyForGenus;
    }    
        
    public boolean isExists(String taxonName) {
        boolean retVal = false;
        String query = "select taxon_name from taxon where taxon_name = " + DBUtil.sqlLiteral(String.valueOf(taxonName));
        Statement stmt = null;
        ResultSet rset = null;
        try {
          Connection connection = getConnection();
          stmt = DBUtil.getStatement(connection, "AntwebDb.isExists()");      
          stmt.execute(query);
          rset = stmt.getResultSet();
          while (rset.next()) {
            retVal = true;
          }
        } catch (SQLException e) {
          s_log.warn("isExists() taxonName:" + taxonName + " e:" + e);
        } finally {
          DBUtil.close(stmt, "AntwebDb.isExists()");        
        }    
        if ("leptanillinaeprotanilla".equals(taxonName)) s_log.debug("isExists() taxonName:" + taxonName + " retVal:" + retVal + " query:" + query);
        return retVal;
    }
    public boolean isGenusExists(String subfamily, String genus) {
        boolean retVal = false;
        String query = "select taxon_name from taxon where subfamily = " + DBUtil.sqlLiteral(String.valueOf(subfamily))
            + " and genus = " + DBUtil.sqlLiteral(String.valueOf(genus)) + " and taxarank = 'genus'";
        Statement stmt = null;
        ResultSet rset = null;
        try {
          Connection connection = getConnection();
          stmt = DBUtil.getStatement(connection, "AntwebDb.isGenusExists()");
          stmt.execute(query);
          rset = stmt.getResultSet();
          while (rset.next()) {
            retVal = true;
          }
        } catch (SQLException e) {
          s_log.warn("isGenusExists() subfamily:" + subfamily + " genus:" + genus + " e:" + e);
        } finally {
          DBUtil.close(stmt, "AntwebDb.isGenusExists()");
        }
        return retVal;
    }        
    public int delete(String taxonName) {
        String dml = "delete from taxon where taxon_name = " + DBUtil.sqlLiteral(String.valueOf(taxonName));
        Statement stmt = null;
        try {
          Connection connection = getConnection();
          stmt = DBUtil.getStatement(connection, "AntwebDb.delete()");      
          int c = stmt.executeUpdate(dml);
          return c;
        } catch (SQLException e) {
          s_log.warn("delete() taxonName:" + taxonName + " e:" + e);
        } finally {
          DBUtil.close(stmt, "AntwebDb.delete()");        
        }            
        return 0;
    }


    
            
}
