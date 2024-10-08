package org.calacademy.antweb.util;

import org.calacademy.antweb.util.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.Date;
import java.sql.*;

import javax.servlet.http.HttpServletRequest;
import javax.sql.*;

import com.mchange.v2.c3p0.*;
import com.mysql.cj.jdbc.MysqlDataSource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.mchange.v2.c3p0.impl.*;

import org.calacademy.antweb.AntFormatter;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.*;

public class DBUtil {

    /* This class helps us manage connection, statements and resultSets.  We open, close,
       and log.  The name should be the Class.method(), or at least be consistent.

    To convert a slow.log timestamp from something like this:
  
      SET timestamp=1551081313;

    Do this in mysql:  

      select from_unixtime(1551081313) from dual;    
      +---------------------------+
      | from_unixtime(1551081313) |
      +---------------------------+
      | 2019-02-24 23:55:13       |
      +---------------------------+
      1 row in set (0.01 sec)
      
      

Also, this is how we manage connections now...
  
          // Instead of:
          // connection = getDataSource(request, "conPool").getConnection();
          // do:
          
          javax.sql.DataSource dataSource = getDataSource(request, "conPool");
          connection = DBUtil.getConnection(dataSource, "METHODNAME()");
          
          //...

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return (mapping.findForward("error"));
        } finally { 		
            DBUtil.close(connection, this, "METHODNAME()");
        }

Look for and replace with the above...
  dataSource.getConnection()

Or, if there are stmts and/or rsets...

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "setPoints(2)"); 
            rset = stmt.executeQuery(theQuery);
            
            // ...

        } catch (SQLException e) {
            s_log.error("setPoints() 2 e:" + e);
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        } finally {
          DBUtil.close(stmt, rset, this, "setPoints(2)");
        }	  
*/

    static class DbRequest {
      final String name;
      final String queryString;
      final Date date;

      DbRequest(String name, String queryString, Date date) {
        this.name = name;
        this.queryString = queryString;
        this.date = date;  
      }
      
      public String toString() {
        return name + " " + date.toString() + " " + queryString;
      }
    }

    private static final Log s_log = LogFactory.getLog(DBUtil.class);
    private static final ConcurrentHashMap<NewProxyConnection, String> connectionMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<NewProxyConnection, DbRequest> connectionRequestMap = new ConcurrentHashMap<>();


    public static Connection getConnection(DataSource dataSource, String name) throws SQLException {
      return getConnection(dataSource, name, null);
    }

    private static final int logNum = 1000;
    private static int c = 0;

    public static Connection getConnection(DataSource dataSource, String name, String queryString) throws SQLException {
      Connection connection = null;

      String message = null;
      // Log the getConnection in getConns.log

      try {
          connection = dataSource.getConnection();

          if (connection.isClosed()) {
              // This never seems to happen. Even when "Logging the stack trace by which the overdue resource was checked-out."
              s_log.error("+++This connection is already closed");
              // return null;  Better to throw a sqlException
              //SQLException e = new SQLException();
              //throw e;
          }

          c = c + 1;
          if (c <= logNum || ServerDebug.isDebug("logGetConns")) {
              //s_log.warn("stack:" + AntwebUtil.getAntwebStackTrace() );
              String stackLine = AntwebUtil.getAntwebStackLine();
              String dataSourceName = "N/a";
              if (dataSource instanceof com.mchange.v2.c3p0.PooledDataSource) {
                  dataSourceName = ((com.mchange.v2.c3p0.PooledDataSource) dataSource).getDataSourceName();
              }
              // " dataSource:" + dataSourceName +
              String logLine = (DateUtil.getFormatDateTimeMilliStr() + " connection:" + connection.toString() + " name:" + name + " queryString:" + queryString + " " + stackLine);
              LogMgr.appendLog("getConns.log", logLine);
          }


      } catch (java.sql.SQLException e) {
        message = "getConnection() name:" + name + " e:" + e;
        Logger.iLog(Logger.dBUtilGetConnection, message, 30);
        throw e;
      } catch (Exception e) {
        // Fail gracefully, without stacktrace, upon server shutdown
        message = "getConnection() name:" + name + " e:" + e;
        s_log.error(message);
        throw e;
      }

      if (connection != null) {
          addConn(connection, name, queryString);
      } else {
          s_log.warn("open() connection is null.  name:" + name);
      }

      return connection;    
    }

    private static final HashMap<String, java.util.Date> s_stmtTimeMap = new HashMap<>();
    private static HashMap<String, QueryStats> s_queryStatsMap = new HashMap<>();
    
    // These methods are for statements, include timing.    
    public static @Nullable Statement getStatement(Connection connection, String name)
      throws SQLException  {
        if (connection == null) {
            String message = "getStatement() connection is null for name: " + name;
            s_log.error(message);
            throw new SQLException(message);
        }
        if (connection instanceof NewProxyConnection) {
            if (((NewProxyConnection) connection).isClosed()) {
                String message = "getStatement() connection is closed. Server shutdown in progress?";
                s_log.error(message);
                throw new SQLException(message);
            }
        }

        Statement stmt = null;
        try {
          //DBUtil.open(name);
          stmt = connection.createStatement();
        } catch (SQLException e) {
          // Fail gracefully, without stacktrace, upon server shutdown
          //AntwebUtil.logShortStackTrace();
          s_log.error("getStatement() connection:" + connection.toString() + " name:" + name + " isClosed():" + ((NewProxyConnection) connection).isClosed() + " e:" + e);
          throw e;
        }
        if (stmt == null) {
          s_log.error("getStatement() unable to getStatement:" + name + " from connection:" + connection);
        }
        return stmt;
    }

    /*
    public static void open(String name) {
        java.util.Date startTime = new java.util.Date();       
        s_stmtTimeMap.put(name, startTime);
        //LogMgr.appendLog("dbUtil.log", "open name:" + name + " startTime:" + startTime, true);
    }

    public static void close(String name) {
        java.util.Date startTime = s_stmtTimeMap.get(name);
        //LogMgr.appendLog("dbUtil.log", "close name:" + name + " startTime:" + startTime, true);
        if (startTime == null) return;
        long millisSince = AntwebUtil.millisSince(startTime);
        QueryStats queryStats = s_queryStatsMap.get(name);
        if (queryStats == null) queryStats = new QueryStats();
        queryStats.count(millisSince);
        s_queryStatsMap.put(name, queryStats);
    }
  */

    public static void close(Statement stmt, String name)
      //throws SQLException  
    {
        //DBUtil.close(name);
        try { 
          if (stmt != null) stmt.close();        
        } catch (SQLException e) {
          s_log.error("close(stmt, name) failed e:" + e);
        }
    }

    public static void close(Statement stmt, ResultSet rset, String name) {
        close(stmt, rset, null, name);                    
    }
    
    public static void rollback(Connection connection) {
        //AntwebUtil.errorStackTrace(e);
        try {
            s_log.error("rollback() No changes made.");
            connection.rollback();
        } catch (Exception e) {
            s_log.error("rollback() failure e:" + e);
        }    
    }

    public static void logQueryStats() {
        LogMgr.appendLog("queryStats.log", "\r-----Created:" + new java.util.Date());
        
        
        // To avoid concurrentModificationException
        Set<String> keySet = s_queryStatsMap.keySet();
        int keyCount = keySet.size();
        String[] stringArray = new String[keyCount];
        keySet.toArray(stringArray);

        //was: for (String name : s_queryStatsMap.keySet()) {

        for (String name : stringArray) {        
          String stats = s_queryStatsMap.get(name).log();
          String logData = name + " " + stats;
          LogMgr.appendLog("queryStats.log", logData);      
        }
        s_queryStatsMap = new HashMap<>();
    }
    
    public static boolean close(Connection connection, String name) {
        return DBUtil.close(connection, null, null, null, name);
    }
    // object is just a name to help us with debugging
    public static boolean close(Connection connection, Object object, String name) {
        return DBUtil.close(connection, null, null, object, name);
    }
    public static boolean close(Statement stmt, Object object, String name) {
        return DBUtil.close(stmt, null, object, name);
    }
    public static boolean close(Statement stmt, ResultSet rset, Object object, String name) {
        //A.log("close() WARNING.  This will not close a connection.  Name:" + name);
        return DBUtil.close(null, stmt, rset, object, name);
    }    
    public static boolean close(Connection conn, Statement stmt, Object object, String name) {     
        return DBUtil.close(conn, stmt, null, object, name);
    }
    public static boolean close(Connection conn, Statement stmt, ResultSet rset, Object object, String name) {     
        //DBUtil.close(name);

        boolean d = false & "getSpecimenImage()".equals(name);
        if (d) A.log("close(conn:"+ conn + " stmt:" + stmt + " rset:" + rset + " object:" + object + " name:" + name + ") 1");

        boolean success = true;
        //	A.log("close() object:" + object + " name:" + name);  
        String objectName = null;
        if (object != null) objectName = object + " ";

        if (d) A.log("close() 2");
        try {
            if (rset != null) rset.close();   
        } catch (SQLException e) {
            success = false;
            s_log.error("close() " + objectName + name + " resultSet close Failure:" + e);  
        }

        if (d) A.log("close() 3");
        try {   
            if (stmt != null) stmt.close();   
        } catch (SQLException e) {
            success = false;
            s_log.error("close() " + objectName + name + "statement close Failure:" + e);  
        }

        if (d) A.log("close() 4");
        try {
            if (conn != null && !conn.isClosed()) {
              //A.log("close() name:" + name);

              // In case of: e:java.sql.SQLException: You can't operate on a closed Connection!!!
              // Uncomment the two lines below and see output here: https://www.antweb.org/web/log/connections.txt
              // To find how the connections are closed.
              //String str = "close conn:" + conn.toString() + " " + AntwebUtil.getShortStackTrace();
              //LogMgr.appendLog("connections.txt", str, true);

              conn.close();
            }
        } catch (SQLException e)  {
            success = false;
            s_log.error("close() " + objectName + name + "connection close Failure:" + e);  
        }

        if (d) A.log("close() 5");

        if (conn != null) {
            removeConn(conn);
        }

        if (d) A.log("close() 6");
        return success;
    }

    private static void addConn(Connection conn, String name, String queryString) {
        if (conn instanceof NewProxyConnection) {
            connectionMap.put((NewProxyConnection) conn, name + " " + new Date());

            DbRequest dbRequest = new DbRequest(name, queryString, new java.util.Date());
            connectionRequestMap.put((NewProxyConnection) conn, dbRequest);
        }
    }
    private static boolean removeConn(Connection conn) {
        NewProxyConnection newProxyConn = (NewProxyConnection) conn;
        int connMapSize = connectionRequestMap.size();
        //connectionMap.remove();
        boolean containsConn = connectionRequestMap.containsKey(newProxyConn);
        if (containsConn) {
            connectionRequestMap.remove(newProxyConn);
            return true;
 /*        Sometimes the warning is fired. Perhaps not synchronized? No evidence of problem.
           if (connectionRequestMap.size() == connMapSize) {
                containsConn = connectionRequestMap.containsKey(newProxyConn);
                s_log.warn("removeConn() failed to remove conn:" + conn + " from connectionRequestMap.  connMapSize:" + connMapSize + " contains:" + containsConn);
                return false;
            } else {
                return true;
            }
*/
        }
        return false;
    }

    public static String getOldConnectionList() {
      String val = null;
      int i = 0;
      for (NewProxyConnection conn : connectionRequestMap.keySet()) {
        ++i;
        DbRequest dbRequest = connectionRequestMap.get(conn);
        if (dbRequest != null) {
            Date date = dbRequest.date;
            if (AntwebUtil.minsSince(date) > 1) {
                if (val == null) val = "";
                val += "i:" + i + " conn:" + conn + " dbRequest:" + dbRequest + "<br>";
            }
        }
      }
      return val;
    }

    public static String getConnectionList() {
      String val = "";
      //s_log.warn("getConnectionList() map:" + connectionMap.toString());
      int i = 0;
      for (NewProxyConnection conn : connectionRequestMap.keySet()) {
        ++i;
        DbRequest dbRequest = connectionRequestMap.get(conn);
        val += "i:" + i + " conn:" + conn + " dbRequest:" + dbRequest + "<br>";
        //s_log.warn(val);
//        connString += "<br>" + i + ". " + conn.toString() + " closed:" + conn.isClosed() + " warnings: " + conn.getWarnings() + " catalog:" + conn.getCatalog()
//          + " metadata:" + conn.getMetaData() + " typeMap:" + conn.getTypeMap();      
      }
      return val;
    }

    private static final int MAX_BUSY_CONNECTIONS = 10;
    private static boolean wasBusy = false;

    public static boolean isServerOverMaxBusy() {
        boolean isBusy = getServerBusyConnectionCount() >= MAX_BUSY_CONNECTIONS;

        // To only report on a change of isBusy to true...
        if (isBusy && !wasBusy) {
            wasBusy = true;
            // Send email alert to developers?
        }
        if (!isBusy && wasBusy) {
            wasBusy = false;
        }

        return isBusy;
    }


    private static int s_serverBusyConnectionCount = 0;
    public static int getServerBusyConnectionCount() {
        return s_serverBusyConnectionCount;
    }

    
    /*

/* Unclosed Connections
     In struts-configDbAnt.xml there are properties defined:
     unreturnedConnectionTimeout and debugUnreturnedConnectionStackTraces

     If timeout > 0 and true then, to track down an unreturnedConnection, go to /root/antweb/ and"

        grep checkoutPooledConnection logs/antwebInfo.log -A 5 -B 5

     These should not be left on in production because of performance impact.
*/


    private static final int s_threshold = 8;
    private static String s_lastMethod;
    private static int s_sameMethod = 0;
    public static void profileQuery(String method, Date startTime, String query) {
      long secs = AntwebUtil.secsSince(startTime);
      if (secs < s_threshold) return;
      if (method.equals(s_lastMethod)) {
          ++s_sameMethod;
          if (s_sameMethod % 10 == 0) {
              s_log.info("profileQuery sameMethod:" + s_sameMethod + " method:" + method);
          }
          return;
      } else {
          s_sameMethod = 0;
      }
      s_lastMethod = method;
      if (secs > s_threshold) {
        String message = "profileQuery() date:" + DateUtil.getFormatDateTimeStr() + " method:" + method + " secs:" + secs + " query:" + query;
          LogMgr.appendLog("profileQuery.log", message);
      }
    }
    
    public static java.sql.Date getCurrentSQLDate() {
        Calendar cal = Calendar.getInstance();
        return new java.sql.Date(cal.getTime().getTime());
    }
    
	public static String escapeQuotes(String theString) {
	  return AntFormatter.escapeQuotes(theString);
	}


    /**
     * Create a prepared statement to query the database
     * @param connection
     * @param name The name of the calling function, for logging and timing
     * @param query The SQL query to prepare
     * @return The generated PreparedStatement
     */
    public static @Nullable PreparedStatement getPreparedStatement(Connection connection, String name, String query)
      throws SQLException
    {

        if (connection == null) {
            s_log.error("getPreparedStatement() connection is null for name: " + name);
            //return null;
            throw new SQLException("No DB Connection");
        }
        PreparedStatement stmt = null;
        try {
            //DBUtil.open(name);
            stmt = connection.prepareStatement(query);
        } catch (Exception e) {
            // Fail gracefully, without stacktrace, upon server shutdown
            AntwebUtil.logShortStackTrace();
            s_log.error("getPreparedStatement() name:" + name + " e:" + e);
            throw e;
        }
        if (stmt == null) {
            s_log.error("getPreparedStatement() unable to getPreparedStatement:" + name + " from connection:" + connection);
        }
        return stmt;
    }


    /** Fetch the bound values of a prepared statement and return a complete SQL statement
     *
     * To facilitate debugging and logging, this method gets the filled values of the prepared statement
     * from the underlying JDBC connection, replacing the question marks in the query string with their filled values.
     *
     *
     * @param stmt The prepared statement to view
     * @return A complete SQL statement with the bound values
     */
    public static String getPreparedStatementString(PreparedStatement stmt) {
        try {
            C3P0ProxyStatement c3p0Stmt = (C3P0ProxyStatement) stmt;

            Method toStringMethod = Object.class.getMethod("toString");
            Object toStr = c3p0Stmt.rawStatementOperation(toStringMethod,
                    C3P0ProxyStatement.RAW_STATEMENT, new Object[]{});
            String sql;
            sql = (String) toStr;
            sql = StringUtils.substringAfter(sql, "PreparedStatement:").trim() + ";";
            return sql;

        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | SQLException e) {
            s_log.error("Exception extracting SQL:" + e.getMessage());
            return "";
        }
    }

    public static String getDbMethodName(String name) {
        return name + AntwebUtil.getUniqueNumber();
    }

}