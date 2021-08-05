package org.calacademy.antweb.util;

import java.util.*;
import java.util.Date;
import java.sql.*;

import javax.sql.*;
import com.mchange.v2.c3p0.*;
import com.mysql.cj.jdbc.MysqlDataSource;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import com.mchange.v2.c3p0.impl.*;

import org.calacademy.antweb.Utility;
import org.calacademy.antweb.AntFormatter;
import org.jetbrains.annotations.Nullable;

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
      String name = null;
      String queryString = null;
      Date date = null;

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
    private static HashMap<NewProxyConnection, String> connectionMap = new HashMap<NewProxyConnection, String>();
    private static HashMap<NewProxyConnection, DbRequest> connectionRequestMap = new HashMap<NewProxyConnection, DbRequest>();

    public static DataSource getDataSource() {
		MysqlDataSource ds = null;
		String jdbcUrl = "jdbc:mysql://mysql:3306/ant?autoReconnect=true&useUnicode=true&characterEncoding=UTF-8&characterSetResults=utf8&connectionCollation=utf8_general_ci";
		ds = new MysqlDataSource();				
		ds.setURL(jdbcUrl);
		ds.setUser("antweb");
		ds.setPassword(AntwebProps.getDbPwd());
		return ds;
    }

    public static Connection Xopen(javax.sql.DataSource dataSource, String name) throws SQLException {
       // getConnection() is the preferred call.
       return DBUtil.getConnection(dataSource, name);
    }
    public static Connection getConnection(javax.sql.DataSource dataSource, String name) throws SQLException {
      return getConnection(dataSource, name, null);
    }

    public static Connection getConnection(javax.sql.DataSource dataSource, String name, String queryString) throws SQLException {
      Connection connection = null;
      try {
        connection = dataSource.getConnection();
      } catch (Exception e) {
        // Fail gracefully, without stacktrace, upon server shutdown
        s_log.error("getConnection() name:" + name + " e:" + e);
      }

      if (connection != null) {
        connectionMap.put((NewProxyConnection) connection, name + " " + (new java.util.Date()));
        
        DbRequest dbRequest = new DbRequest(name, queryString, new java.util.Date());        
        connectionRequestMap.put((NewProxyConnection) connection, dbRequest);
      } else {
        s_log.warn("open() connection is null.  data:" + name);
      }
      return connection;    
    }

    private static int MAX_BUSY_CONNECTIONS = 10;
    public static boolean isServerBusy() {
      return getServerBusyConnectionCount() >= MAX_BUSY_CONNECTIONS;
    }
 
    private static HashMap<String, java.util.Date> s_stmtTimeMap = new HashMap<String, java.util.Date>();
    private static HashMap<String, QueryStats> s_queryStatsMap = new HashMap<String, QueryStats>();

    
    // These methods are for statements, include timing.    
    public static @Nullable Statement getStatement(Connection connection, String name)
      throws SQLException  {
        if (connection == null) {
            s_log.error("getStatement() connection is null for name: " + name);
            return null;
        }
        Statement stmt = null;
        try {
          DBUtil.open(name);
          stmt = connection.createStatement();  
      } catch (Exception e) {
        // Fail gracefully, without stacktrace, upon server shutdown
        AntwebUtil.logShortStackTrace();
        s_log.error("getStatement() name:" + name + " e:" + e);
      }
      if (stmt == null) {
          s_log.error("getStatement() unable to getStatement:" + name + " from connection:" + connection);
      }
      return stmt;
    }


    /**
     * Create a prepared statement to query the database
     * @param connection
     * @param name The name of the calling function, for logging and timing
     * @param query The SQL query to prepare
     * @return The generated PreparedStatement
     */
    public static @Nullable PreparedStatement getPreparedStatement(Connection connection, String name, String query) {
        if (connection == null) {
            s_log.error("getPreparedStatement() connection is null for name: " + name);
            return null;
        }
        PreparedStatement stmt = null;
        try {
            DBUtil.open(name);
            stmt = connection.prepareStatement(query);
        } catch (Exception e) {
            // Fail gracefully, without stacktrace, upon server shutdown
            AntwebUtil.logShortStackTrace();
            s_log.error("getPreparedStatement() name:" + name + " e:" + e);
        }
        if (stmt == null) {
            s_log.error("getPreparedStatement() unable to getPreparedStatement:" + name + " from connection:" + connection);
        }
        return stmt;
    }

    public static void open(String name) {
        java.util.Date startTime = new java.util.Date();       
        s_stmtTimeMap.put(name, startTime);          
    }

    public static void close(String name) {
        java.util.Date startTime = (java.util.Date) s_stmtTimeMap.get(name);
        if (startTime == null) return;
        long millisSince = AntwebUtil.millisSince(startTime);
        QueryStats queryStats = (QueryStats) s_queryStatsMap.get(name);
        if (queryStats == null) queryStats = new QueryStats();
        queryStats.count(millisSince);
        s_queryStatsMap.put(name, queryStats);
    }
    
    public static void close(Statement stmt, String name)
      //throws SQLException  
    {
        DBUtil.close(name);
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
            s_log.error("rollback() rollback()");
            connection.rollback();
        } catch (Exception e) {
            s_log.error("execute() rollback failure e:" + e);
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
          String stats = ((QueryStats) s_queryStatsMap.get(name)).log();
          String logData = name + " " + stats;
          LogMgr.appendLog("queryStats.log", logData);      
        }
        s_queryStatsMap = new HashMap<String, QueryStats>();
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
        DBUtil.close(name);

        boolean success = true;
        //	A.log("close() object:" + object + " name:" + name);  
        String objectName = null;
        if (object != null) objectName = object.toString() + " ";
        try {   
            if (rset != null) rset.close();   
        } catch (SQLException e) {
            success = false;
            s_log.error("close() " + objectName + name + " resultSet close Failure:" + e);  
        }   
        try {   
            if (stmt != null) stmt.close();   
        } catch (SQLException e) {
            success = false;
            s_log.error("close() " + objectName + name + "statement close Failure:" + e);  
        }   
        try {   
            if (conn != null && !conn.isClosed()) {
              //A.log("close() name:" + name);
              conn.close();   
            }
        } catch (SQLException e)  {
            success = false;
            s_log.error("close() " + objectName + name + "connection close Failure:" + e);  
        }

        if (conn != null) {
          NewProxyConnection newProxyConn = (NewProxyConnection) conn;
          int connMapSize = connectionRequestMap.size(); 
          //connectionMap.remove();
          boolean containsConn = connectionRequestMap.containsKey(newProxyConn);
          if (containsConn) {
              connectionRequestMap.remove(newProxyConn);
              if (connectionRequestMap.size() == connMapSize) {
                  s_log.warn("close() failed to remove name:" + name + " from connectionRequestMap.  connMapSize:" + connMapSize + " contains:" + containsConn);
              }
          }
        }

        return success;        
    }

    public static String getOldConnectionList() {
      String val = null;
      int i = 0;
      for (NewProxyConnection conn : connectionRequestMap.keySet()) {
        ++i;
        DbRequest dbRequest = connectionRequestMap.get(conn);
        Date date = dbRequest.date;
        if (AntwebUtil.minsSince(date) > 5) {
          if (val == null) val = "";
          val += "i:" + i + " conn:" + conn + " dbRequest:" + dbRequest + "<br>";
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

    // These were moved from AntwebUtil



    public static String getThreadPoolStatus(DataSource dataSource) {
      String logMessage = "";
      if (dataSource instanceof ComboPooledDataSource) {
        ComboPooledDataSource poolSource = (ComboPooledDataSource) dataSource;
        try {
          logMessage = "<b>getDataSourceName:</b>" + poolSource.getDataSourceName() + "<br>";        
          logMessage += "<b>sampleThreadPoolStatus:</b><br>" + poolSource.sampleThreadPoolStatus() + "<br>"; 
          logMessage += "<b>SampleThreadPoolStackTraces:</b><br>" + poolSource.sampleThreadPoolStackTraces() + "<br>";
          logMessage += "<b>getThreadPoolNumTasksPending:</b>" + poolSource.getThreadPoolNumTasksPending() + "<br>";        
          //logMessage += "getLastCheckoutFailure:" + poolSource.getLastCheckoutFailure() + "<br>";
          //logMessage += "sampleLastCheckinFailureStackTrace:" + poolSource.sampleLastCheckinFailureStackTrace() + "<br>";
          //logMessage += "sampleLastCheckoutFailureStackTrace:" + poolSource.sampleLastCheckoutFailureStackTrace() + "<br>";
          //logMessage += "sampleLastConnectionTestFailureStackTrace:" + poolSource.sampleLastConnectionTestFailureStackTrace() + "<br>";
          logMessage += "<b>sampleLastAcquisitionFailureStackTraceDefaultUser:</b>" + poolSource.sampleLastAcquisitionFailureStackTraceDefaultUser() + "<br>";
          logMessage += "<b>sampleLastCheckinFailureStackTraceDefaultUser:</b>" + poolSource.sampleLastCheckinFailureStackTraceDefaultUser() + "<br>";
          //logMessage += "sampleStatementDestroyerStatus:" + poolSource.sampleStatementDestroyerStatus() + "<br>";
          //logMessage += "sampleStatementDestroyerStackTraces:" + poolSource.sampleStatementDestroyerStackTraces() + "<br>";
          logMessage += "<b>sampleStatementCacheStatusDefaultUser:</b>" + poolSource.sampleStatementCacheStatusDefaultUser() + "<br>";
 //         logMessage += ":" + poolSource.() + "<br>";
          logMessage += "<b>sampleLastIdleTestFailureStackTraceDefaultUser:</b>" + poolSource.sampleLastIdleTestFailureStackTraceDefaultUser() + "<br>";
          logMessage += "<b>sampleLastConnectionTestFailureStackTraceDefaultUser:</b>" + poolSource.sampleLastConnectionTestFailureStackTraceDefaultUser() + "<br>";
          logMessage += "<b>sampleLastCheckoutFailureStackTraceDefaultUser:</b>" + poolSource.sampleLastCheckoutFailureStackTraceDefaultUser();
        } catch (SQLException e) {
          logMessage = "Exception:" + e;
        }
      } else {
        logMessage = "DataSource is not ComboPooledDataSource.  No diagnostics.";
      }
      return logMessage;
    }


    private static int s_serverBusyConnectionCount = 0;

    public static int getServerBusyConnectionCount() {
      return s_serverBusyConnectionCount;
    }

    static int MAXNUMBUSYCONNECTIONS = 100; // was 13;
    private static int MINUTES = 1000 * 60;
    private static Date lastLog = null;
    
    public static boolean isServerBusy(javax.sql.DataSource dataSource, javax.servlet.http.HttpServletRequest request) 
      throws SQLException {
      int numBusy = DBUtil.getNumBusyConnections(dataSource);
      if (numBusy > DBUtil.MAXNUMBUSYCONNECTIONS) {
        String message = "Due to current server load, Antweb is not able to fulfill this request at this time.  Please try again later.";
//        if ((lastLog == null) || (AntwebUtil.timePassed(lastLog, new Date()) > (MINUTES * .5))) {
        if ((lastLog == null) || AntwebUtil.minsSince(lastLog) > 1) {
          lastLog = new Date();
          String logMessage = "<br><br>" + (new Date()).toString() + " isServerBusy YES!  num:" + numBusy + " " + QueryProfiler.report() + " Memory:" + AntwebUtil.getMemoryStats();
          s_log.warn(logMessage);
          java.sql.Connection connection = null;
          try {
            connection = DBUtil.getConnection(dataSource, "AntwebUtil.isServerBusy()");
            logMessage += "<br>" + AntwebFunctions.getMysqlProcessListHtml(connection);
          } catch (SQLException e) {
            s_log.error("isServerBusy() e:" + e);
          } finally {
            DBUtil.close(connection, "AntwebUtil", "AntwebUtil.isServerBusy()");
          }
          LogMgr.appendLog("serverBusy.html", logMessage);
          s_log.warn("isServerBusy() overdue resource:" + DBUtil.getOldConnectionList());
        }
        request.setAttribute("message", message);
        return true;
      } else {
        return false;
      }
    }

	public static int getNumBusyConnections(javax.sql.DataSource dataSource) {
        int numBusy = 0;
        String cpDiagnostics = null;
        if (dataSource instanceof ComboPooledDataSource) {
          ComboPooledDataSource c3p0DataSource = (ComboPooledDataSource) dataSource;	
          try {
            numBusy = c3p0DataSource.getNumBusyConnectionsDefaultUser();
            s_serverBusyConnectionCount = numBusy;
          } catch (SQLException e) {
            s_log.error("getNumBusyConnections() error:" + e);
          }
        }	
        return numBusy;   
	}       
    
    public static java.sql.Date getCurrentSQLDate() {
        Calendar cal = Calendar.getInstance();
        return new java.sql.Date(cal.getTime().getTime());
    }
    
	public static String escapeQuotes(String theString) {
	  return AntFormatter.escapeQuotes(theString);
	}

    public static String getSimpleCpDiagnosticsAttr(javax.sql.DataSource dataSource) {
        String cpDiagnostics = "";
        if (dataSource instanceof ComboPooledDataSource) {
            ComboPooledDataSource c3p0DataSource = (ComboPooledDataSource) dataSource;
            try {
                cpDiagnostics = "C3P0 maxPoolSize:" + c3p0DataSource.getMaxPoolSize()
                        + " numConnectionsDefaultUser:" + c3p0DataSource.getNumConnectionsDefaultUser()
                        + " numConnectionsAllUsers:" + c3p0DataSource.getNumConnectionsAllUsers()
                        + " numIdleConnections:" + c3p0DataSource.getNumIdleConnectionsDefaultUser()
                        + " numBusyConnections:" + c3p0DataSource.getNumBusyConnectionsDefaultUser();
            } catch (SQLException e) {
                s_log.error("getCpDiagnosticsAttr() error:" + e);
            }
        }
        return cpDiagnostics;
    }


    public static String getCpDiagnosticsAttr(javax.sql.DataSource dataSource) {
        String cpDiagnostics = "";
        if (dataSource instanceof ComboPooledDataSource) {
            ComboPooledDataSource c3p0DataSource = (ComboPooledDataSource) dataSource;
            try {
                cpDiagnostics = "C3P0 maxPoolSize:" + c3p0DataSource.getMaxPoolSize()
                        + " numConnectionsDefaultUser:" + c3p0DataSource.getNumConnectionsDefaultUser()
                        + " numConnectionsAllUsers:" + c3p0DataSource.getNumConnectionsAllUsers()
                        + " numIdleConnections:" + c3p0DataSource.getNumIdleConnectionsDefaultUser()
                        + " numBusyConnections:" + c3p0DataSource.getNumBusyConnectionsDefaultUser()
                        + " \r\r" + DBUtil.getThreadPoolStatus(dataSource);
            } catch (SQLException e) {
                s_log.error("getCpDiagnosticsAttr() error:" + e);
            }
        }
        return cpDiagnostics;
    }


}