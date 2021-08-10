package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class UtilDb extends AntwebDb {
    
    private static Log s_log = LogFactory.getLog(UtilDb.class);
        
    public UtilDb(Connection connection) {
      super(connection);
    }
    
    public int getCount(String table, String where) {
      int count = 0;
      String fromWhere = "from " + table + " where " + where;
      count = getCount(fromWhere);
      return count;      
    }

    // Should return a single value. Will return the first if a list.
    public String XgetValue(String query) {
        String result = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getValue()");

            rset = stmt.executeQuery(query);
            result = rset.getString(1);
        } catch (SQLException e) {
            s_log.error("getValue() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "getValue()");
        }
        return result;
    }

    // Should return a single value. Will return the first if a list.
    public String getDateValue(String query) {
        String result = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getDateValue()");

            rset = stmt.executeQuery(query);
            while (rset.next()) {
                Object o = rset.getDate("theDate");
                if (o != null) result = o.toString();
            }
        } catch (SQLException e) {
            s_log.error("getDateValue() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "getDateValue()");
        }
        return result;
    }

    public String runQuery(String query) {
      String results = "[results]";
      Statement stmt = null;
      ResultSet rset = null;
      try {
          stmt = DBUtil.getStatement(getConnection(), "runQuery()");            
           
          rset = stmt.executeQuery(query);
          results = getResultString(rset, "\n");          
      } catch (SQLException e) {
          s_log.error("runQuery() e:" + e + " query:" + query);
      } finally {
          DBUtil.close(stmt, rset, "runQuery()");
      }   
      return results;
    }

    public int runDml (String dml) {
        int count = 0;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "runDml()");
            count = stmt.executeUpdate(dml);
        } catch (SQLException e) {
            s_log.error("runDml() e:" + e + " dml:" + dml);
        } finally {
            DBUtil.close(stmt, null, this, "runDml()");
        }
        return count;
    }

    public static String getResultString(ResultSet rs, String newLine) throws SQLException {
        String results = "";
        
        // the order of the rows in a cursor
        // are implementation dependent unless you use the SQL ORDER statement
        ResultSetMetaData meta = rs.getMetaData();
        int colmax = meta.getColumnCount();
        int i;
        Object o = null;

        // the result set is a cursor into the data.  You can only point to one row at a time
        // assume we are pointing to BEFORE the first row rs.next() points to next row and returns true
        // or false if there is no next row, which breaks the loop
        int rowNum = 0;
        String columns = "";
        String rowData = "";
        while (rs.next()) {
            ++rowNum;
            String row = "";
            for (i = 0; i < colmax; ++i) {
                if (rowNum == 1) columns += meta.getColumnLabel(i + 1) + " ";
                o = rs.getObject(i + 1);    // Is SQL the first column is indexed
                // with 1 not 0
                row += o.toString() + " ";                
            }
            rowData += newLine + row; 
        }
        results = newLine + columns + rowData;
        return results;
    }    

    public int getCount(String fromWhere) {
      int count = 0;
      String query = "";
      Statement stmt = null;
      ResultSet rset = null;
      try {
          stmt = DBUtil.getStatement(getConnection(), "getCount()");            

          query = "select count(*) " + fromWhere;            
          rset = stmt.executeQuery(query);
          while (rset.next()) {
              count = rset.getInt(1);
          }            
      } catch (SQLException e) {
          s_log.error("getCount() e:" + e + " query:" + query);
      } finally {
            DBUtil.close(stmt, rset, "getCount()");
      }   
      return count;      
    } 
    
    public String executeDmls(ArrayList<String> dmls) { // throws SQLException {     
        String returnVal = "success";
        Statement stmt = null;
        String thisDml = "";
        try {
            stmt = DBUtil.getStatement(getConnection(), "executeDmls()");

            for (String dml : dmls) {
              thisDml = dml;
              stmt.executeUpdate(dml);
              //A.log("executeDml() dml:" + dml);
            }
        } catch (SQLException e) {
            s_log.error("executeDmls() e:" + e + " dml:" + thisDml);
            returnVal = e.toString();
        } finally {
            DBUtil.close(stmt, null, "executeDmls()");
        }   
        return returnVal;
    }

    public int deleteFrom(String table) {
      return deleteFrom(table, null);
    }

    // DO include "where" in whereClause.
    public int deleteFrom(String table, String whereClause) {
        Statement stmt = null;
        String dml = "delete from " + table;
        if (whereClause != null) dml = dml + " " + whereClause;

        try {
            stmt = DBUtil.getStatement(getConnection(), "deleteFrom()");

            int count = stmt.executeUpdate(dml);
            //A.log("deleteFrom() count:" + count + " dml:" + dml);
            LogMgr.appendLog("taxonSet.log", "UtilDb.deleteFrom(" + table + "):" + count, true);                      
            
            return count;
        } catch (SQLException e) {
            s_log.error("deleteFrom() e:" + e + " dml:" + dml);
        } finally {
            DBUtil.close(stmt, null, "deleteFrom()");
        }   
        return 0;
    }

    public void updateField(String table, String field, String value) {
      updateField(table, field, value, null);
    }
    
    // If the value is a string include it in single quotes as such: "'x'" !
    // Don't include "where" or "and" in the clause. Just the clause.
    public int updateField(String table, String field, String value, String whereClause) {
        int count = 0;
        Statement stmt = null;
        if (value == null) return 0;
        //if (value.contains("'")) value = Formatter.escapeQuotes(value);
        String dml = "update " + table + " set " + field + " = " + value;
        if (whereClause != null) dml += " where " + whereClause;
        
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateField()");
            count = stmt.executeUpdate(dml);
            //A.log("updateField() count:" + count + " dml:" + dml);
        } catch (SQLException e) {
            s_log.error("updateField() e:" + e + " dml:" + dml);
        } finally {
            DBUtil.close(stmt, null, "updateField()");
        }  
        return count; 
    }
    
    public boolean isBlankField(String query) throws SQLException {
      Statement stmt = null;
      String value = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "isBlankField()");

        ResultSet rset = stmt.executeQuery(query);
        while (rset.next()) {
          value = rset.getString("value");
            //s_log.warn("isBlankField() false for query:" + query);
            return Utility.isBlank(value);
          //A.log("isBlankField() from projectName:" + project_name + " to geolocaleId:" + geolocaleId);
        }
      } finally {
        DBUtil.close(stmt, null, "isBlankField()");
      }
      return false;
    }    
    
}