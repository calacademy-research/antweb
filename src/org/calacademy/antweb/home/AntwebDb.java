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

import org.calacademy.antweb.*;
        import org.calacademy.antweb.util.*;


public class AntwebDb {

    private static Log s_log = LogFactory.getLog(AntwebDb.class);

    static int MAXLENGTH = 80;
    String currentDateFunction = "now()";

    Connection m_connection = null;
    
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

        if (!isValidSubfamilyForGenus) A.log("isValidSubfamilyForGenus() isValid:" + isValidSubfamilyForGenus + " query:" + query);
        return isValidSubfamilyForGenus;
    }    
        
    public boolean isExists(String taxonName) {
        boolean retVal = false;
        String query = "select taxon_name from taxon where taxon_name = '" + taxonName + "'";
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
        if ("leptanillinaeprotanilla".equals(taxonName)) A.log("isExists() taxonName:" + taxonName + " retVal:" + retVal + " query:" + query);
        return retVal;
    }
            
    public int delete(String taxonName) {
        String dml = "delete from taxon where taxon_name = '" + taxonName + "'";
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

    public DummyTaxon getDummyTaxon(String taxonName) throws SQLException{
        return getDummyTaxon(taxonName, "taxon");
    }
    public DummyTaxon getDummyTaxon(String taxonName, String table) 
      throws SQLException {
    
      // Not a fully instantiated one.  Just contains...
        DummyTaxon taxon = null;
        String query = "select family, subfamily, tribe, genus, subgenus, species, subspecies, status, " 
          + " source, insert_method, line_num, access_group, current_valid_name, parent_taxon_name, fossil " 
          + " from " + table + " where taxon_name = '" + taxonName + "'";
        Statement stmt = null;
        ResultSet rset = null;
        try {
          Connection connection = getConnection();
          stmt = DBUtil.getStatement(connection, "AntwebDb.getDummyTaxon()");      
          stmt.execute(query);

          rset = stmt.getResultSet();

          while (rset.next()) {
              taxon = new DummyTaxon();              
              taxon.setTaxonName(taxonName);
              taxon.setFamily(rset.getString("family"));
              taxon.setSubfamily(rset.getString("subfamily"));
              taxon.setTribe(rset.getString("tribe"));
              taxon.setGenus(rset.getString("genus"));
              taxon.setSubgenus(rset.getString("subgenus"));
              taxon.setSpecies(rset.getString("species"));
              taxon.setSubspecies(rset.getString("subspecies"));
              taxon.setStatus(rset.getString("status"));
              taxon.setSource(rset.getString("source"));
              taxon.setInsertMethod(rset.getString("insert_method"));
              taxon.setLineNum(rset.getInt("line_num"));
              taxon.setGroupId(rset.getInt("access_group"));
              taxon.setIsFossil((rset.getInt("fossil") == 1) ? true : false);

              String currentValidName = rset.getString("current_valid_name");
              if (currentValidName != null && !taxonName.equals(currentValidName.toLowerCase())) {
                taxon.setCurrentValidName(currentValidName);
              }
              taxon.setParentTaxonName(rset.getString("parent_taxon_name"));
              
          }

          if (AntwebProps.isDevMode()
            && false
            // && taxon.getIsFossil()
               // && "myrmicinaecrematogaster jtl-022".equals(taxonName)
             ) s_log.warn("getDummyTaxon() 1 taxonName:" + taxonName + " query:" + query);              


        } catch (Exception e) {
          s_log.warn("getDummyTaxon() taxonName:" + taxonName + " table:" + table + " e:" + e);
        } finally {
          DBUtil.close(stmt, "AntwebDb.getDummyTaxon()");        
        }

        //if (AntwebProps.isDevMode() && "myrmicinaecrematogaster jtl-022".equals(taxonName)) s_log.warn("getDummyTaxon() 3 taxonName:" + taxonName + " taxon:" + taxon);              

        return taxon;
    }            
        
    public boolean isCurrentInLookup(Connection connection, String key, String value) {
        String query = null;
        Statement stmt = null;
        ResultSet rset = null;
        String dml = null;

        try {
          query = "select value from lookup where handle = '" + key + "'";          
          stmt = DBUtil.getStatement(connection, "AntwebDb.isCurrentInLookup()");  

		  // A.log("query:" + query);
          stmt.execute(query);        
          rset = stmt.getResultSet();
          String lookupValue = null;
          int i = 0;
          while (rset.next()) {
            ++i;
  		    // A.log("i:" + i);            
            lookupValue = rset.getString("value"); 

            //A.log("isCurrentInLookup() key:" + key + " value:" + value + " lookupValue:" + lookupValue);
            
            if (lookupValue.equals(value)) {
              return true;   
            } else {
              dml = "update lookup set value = '" + value + "' where handle = '" + key + "'";
            }
          }
          
          if (i == 0) dml = "insert into lookup (handle, value) values ('" + key + "', '" + value + "')";

          stmt = connection.createStatement();

          // A.log("dml:" + dml);

          stmt.executeUpdate(dml);

        } catch (Exception e) {
          s_log.warn("isCurrentInLookup() e:" + e);
        } finally {
          DBUtil.close(stmt, "AntwebDb.isCurrentInLookup()");        
        }        
        
        A.log("isCurrentInLookup() dml:" + dml);
        
        return false;
    }	
    
            
}
