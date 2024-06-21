package org.calacademy.antweb.util;

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

public class DBStatus {

    private static final Log s_log = LogFactory.getLog(DBStatus.class);

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

          logMessage += "<br><b>unreturnedConnectionTimeout:</b>" + reportUnreturnedConnection(dataSource);
        } catch (SQLException e) {
          logMessage = "Exception:" + e;
        }
      } else {
        logMessage = "DataSource is not ComboPooledDataSource.  No diagnostics.";
      }
      return logMessage;
    }

    public static String reportUnreturnedConnection(DataSource dataSource) {
        int unreturnedConnectionTimeout = 0;
        String report = "";
        String unreturnedConnectionStackTraces = "";
        String cpDiagnostics = null;
        ComboPooledDataSource c3p0DataSource = (ComboPooledDataSource) dataSource;
        //try {
        unreturnedConnectionTimeout = c3p0DataSource.getUnreturnedConnectionTimeout();
        //unreturnedConnectionStackTraces = c3p0DataSource.getUnreturnedConnectionStackTraces();
        report = unreturnedConnectionTimeout + " " + unreturnedConnectionStackTraces;
        //} catch (SQLException e) {
        //    s_log.error("getUnreturnedConnectionTimeout() error:" + e);
        //      }
        return report;
    }

    private static boolean isServerBusy = false;
    private static boolean wasServerBusy = false;

    public static boolean getIsServerBusy() {

        // serverBusy.log
        if (isServerBusy != wasServerBusy) {
            String message = "";
            if (!isServerBusy) message += " ";
            message += DateUtil.getFormatDateTimeMilliStr() + " busy:" + isServerBusy;
            LogMgr.appendDataLog("serverBusy.log", message);
            wasServerBusy = isServerBusy;
        }

        return isServerBusy;
    }
    public static void setIsServerBusy(boolean isBusy) {
        isServerBusy = isBusy;
    }

    public static boolean isServerBusy(Connection connection)
            throws SQLException {
        boolean isBusy = false;

        try {
            ArrayList<String> processListArray = DBStatus.getMysqlProcessList(connection);
            if (processListArray.size() > 100) {
                isBusy = true;
            }
        } catch (SQLException e) {
            s_log.error("isServerBusy(connection) e:" + e);
        }

        setIsServerBusy(isBusy);
        return isBusy;
    }

    private static String NOT_BUSY_MSG = "Server not busy.";
    private static int testAgain = 0;
    private static int testAgainLimit = 10;

    public static boolean isServerBusy(Connection connection, HttpServletRequest request) throws SQLException {
        if (testAgain >= testAgainLimit) {
            testAgain = 0;
            isServerBusy = isServerBusy(connection); //, null, null);
        } else {
            testAgain  = testAgain + 1;
        }

        String message = getServerBusyReport();
        request.setAttribute("message", message);

        return isServerBusy;
    }


    // Only to be caalled from Admin functions. DBStatus.do and serverStatus.do
    public static boolean isServerBusy(DataSource dataSource1, DataSource dataSource2, DataSource dataSource3)
            throws SQLException {

        if (!AntwebProps.isDevMode()) return false;  // Do not run in production

        //if (!timeToRun()) return getIsServerBusy();

        int numBusy1 = DBStatus.getNumBusyConnections(dataSource1);
        int numBusy2 = DBStatus.getNumBusyConnections(dataSource2);
        int numBusy3 = DBStatus.getNumBusyConnections(dataSource3);
        String poolName = null;
        DataSource dataSource = null;

        ComboPooledDataSource cpds1 = (ComboPooledDataSource) dataSource1;
        ComboPooledDataSource cpds2 = (ComboPooledDataSource) dataSource2;
        ComboPooledDataSource cpds3 = (ComboPooledDataSource) dataSource3;

        if (cpds1 != null && (numBusy1 > (cpds1.getMaxPoolSize() - 1))) {
            poolName = "shortPool";
        }
        if (cpds2 != null && (numBusy2 > (cpds2.getMaxPoolSize() - 1))) {
            poolName = "middlePool";
        }
        if (cpds3 != null && (numBusy3 > (cpds3.getMaxPoolSize() - 1))) {
            poolName = "longPool";
        }
        boolean busy = false;
        if (poolName != null) busy = true;

        if (busy)  {
            reportServerBusy(cpds1, cpds2, cpds3);
            isServerBusy = true;
        } else {
            serverBusyReport = NOT_BUSY_MSG;
            isServerBusy = false;
        }
        return isServerBusy;
    }

    private static String serverBusyReport = null;
    public static String getServerBusyReport() {
        return serverBusyReport;
    }

    public static int logFreq = 3;      // Log frequency in minutes
    public static int emailFreq = 15;   // email frequency in minutes

    private static Date lastLog;
    private static Date lastEmail;

    public static String reportServerBusy(ArrayList<String> processlist, boolean force) {
            if (force || (lastLog == null || AntwebUtil.minsSince(lastLog) > logFreq)) {

                lastLog = new Date();
                String logMessage = "<br><br>" + new Date() + " reportServerBusy forced:" + force
                        + "<br><br>" + QueryProfiler.report() + "<br><br> Memory:" + AntwebUtil.getMemoryStats() + "<br><br> oldConns:" + DBUtil.getOldConnectionList();
                s_log.warn(logMessage);
                logMessage += "<br><br> processes:" + getMysqlProcessListHtml(processlist);
                LogMgr.appendLog("serverBusy.html", logMessage);
                serverBusyReport = logMessage;
            }

            if (force || !NOT_BUSY_MSG.equals(serverBusyReport)) {
                if (lastEmail == null || AntwebUtil.minsSince(lastEmail) > emailFreq) {
                    lastEmail = new Date();
                    String recipients = AntwebUtil.getDevEmail();
                    String subject = "Antweb Server Busy";
                    String body = serverBusyReport;
                    //s_log.warn("cpuCheck() Send " + message + " to recipients:" + recipients);
                    Emailer.sendMail(recipients, subject, body);
                }
            }

        return serverBusyReport;
    }

    public static String reportServerBusy(ComboPooledDataSource cpds1, ComboPooledDataSource cpds2, ComboPooledDataSource cpds3) {
        return reportServerBusy(cpds1, cpds2, cpds3, false);
    }

    public static String reportServerBusy(ComboPooledDataSource cpds1, ComboPooledDataSource cpds2, ComboPooledDataSource cpds3, boolean force) {
        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("DBStatus.isServerBusy()");
        try {
          if (force || (lastLog == null || AntwebUtil.minsSince(lastLog) > logFreq)) {

            lastLog = new Date();
            String logMessage = "<br><br>" + new Date() + " reportServerBusy forced:" + force
                    + "<br><br>shortPool:" + getSimpleCpDiagnosticsAttr (cpds1) + ". <br><br>mediumPool:" + getSimpleCpDiagnosticsAttr(cpds2) + ". <br><br>longPools:" + getSimpleCpDiagnosticsAttr(cpds3) + " "
                    + "<br><br>" + QueryProfiler.report() + "<br><br> Memory:" + AntwebUtil.getMemoryStats() + "<br><br> oldConns:" + DBUtil.getOldConnectionList();
            s_log.warn(logMessage);

            connection = DBUtil.getConnection(cpds1, dbMethodName);
            logMessage += "<br><br> processes:" + getMysqlProcessListHtml(connection);

            // Report cpds2 and 3?

            LogMgr.appendLog("serverBusy.html", logMessage);
            serverBusyReport = logMessage;
          }

          if (force || !NOT_BUSY_MSG.equals(serverBusyReport)) {
              if (lastEmail == null || AntwebUtil.minsSince(lastEmail) > emailFreq) {
                lastEmail = new Date();
                String recipients = AntwebUtil.getDevEmail();
                String subject = "Antweb Server Busy";
                String body = serverBusyReport;
                //s_log.warn("cpuCheck() Send " + message + " to recipients:" + recipients);
                Emailer.sendMail(recipients, subject, body);
              }
            }
        } catch (SQLException e) {
            s_log.error("reportServerBusy() e:" + e);
        } finally {
            DBUtil.close(connection, dbMethodName);
        }

        return serverBusyReport;
    }

    private static int s_serverBusyConnectionCount = 0;
    public static int getServerBusyConnectionCount() {
        return s_serverBusyConnectionCount;
    }

    /*

    static final int MAXNUMBUSYCONNECTIONS = 100; // was 13;
    private static final int MINUTES = 1000 * 60

    public static boolean xisServerBusy(DataSource dataSource, HttpServletRequest request)
      throws SQLException {
      int numBusy = DBUtil.getNumBusyConnections(dataSource);
      if (numBusy > DBUtil.MAXNUMBUSYCONNECTIONS) {
        String message = "Due to current server load, Antweb is not able to fulfill this request at this time.  Please try again later.";
//        if ((lastLog == null) || (AntwebUtil.timePassed(lastLog, new Date()) > (MINUTES * .5))) {
        if (lastLog == null || AntwebUtil.minsSince(lastLog) > 1) {
          lastLog = new Date();
          String logMessage = "<br><br>" + new Date() + " isServerBusy YES!  num:" + numBusy + " " + QueryProfiler.report() + " Memory:" + AntwebUtil.getMemoryStats();
          s_log.warn(logMessage);
          Connection connection = null;
          String dbMethodName = DBUtil.getDbMethodName("DBStatus."isServerBusy()"");
          try {
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            logMessage += "<br>" + DBUtil.getMysqlProcessListHtml(connection);

              String recipients = AntwebUtil.getDevEmail();
              String subject = "Antweb Server Busy";
              String body = logMessage;
              //s_log.warn("cpuCheck() Send " + message + " to recipients:" + recipients);
              Emailer.sendMail(recipients, subject, body);

          } catch (SQLException e) {
            s_log.error("isServerBusy() e:" + e);
          } finally {
            DBUtil.close(connection, dbMethodName);
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


*/

    private static int getNumBusyConnections(DataSource dataSource) {
        if (dataSource == null) return -1;
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


/* Unclosed Connections
     In struts-configDbAnt.xml there are properties defined:
     unreturnedConnectionTimeout and debugUnreturnedConnectionStackTraces

     If timeout > 0 and true then, to track down an unreturnedConnection, go to /root/antweb/ and"

        grep checkoutPooledConnection logs/antwebInfo.log -A 5 -B 5

     These should not be left on in production because of performance impact.
*/

    public static String getSimpleCpDiagnosticsAttr(DataSource dataSource) throws SQLException {
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
                throw e;
            }
        }
        return cpDiagnostics;
    }

    public static String getAllCpDiagnosticsAttr(DataSource dataSource1, DataSource dataSource2, DataSource dataSource3) {
        String cpDiagnostics = "";
        if (dataSource1 instanceof ComboPooledDataSource && dataSource2 instanceof ComboPooledDataSource && dataSource3 instanceof ComboPooledDataSource) {
            ComboPooledDataSource c3p0DataSource1 = (ComboPooledDataSource) dataSource1;
            ComboPooledDataSource c3p0DataSource2 = (ComboPooledDataSource) dataSource2;
            ComboPooledDataSource c3p0DataSource3 = (ComboPooledDataSource) dataSource3;
            try {
                cpDiagnostics = getSimpleCpDiagnosticsAttr(dataSource1)
                  + getSimpleCpDiagnosticsAttr(dataSource2)
                  + getSimpleCpDiagnosticsAttr(dataSource3);
            } catch (SQLException e) {
                s_log.error("getAllCpDiagnosticsAttr() error:" + e);
            }
        }
        return cpDiagnostics;
    }

    public static String getCpDiagnosticsAttr(DataSource dataSource) {
        String cpDiagnostics = "";
        if (dataSource instanceof ComboPooledDataSource) {
            ComboPooledDataSource c3p0DataSource = (ComboPooledDataSource) dataSource;
            try {
                cpDiagnostics = getSimpleCpDiagnosticsAttr(dataSource)
                    + " \r\r" + DBStatus.getThreadPoolStatus(dataSource);
            } catch (SQLException e) {
                s_log.error("getCpDiagnosticsAttr() error:" + e);
            }
        }
        return cpDiagnostics;
    }


    public static String getMysqlProcessListHtml(Connection connection)
            throws SQLException {
        String returnVal = "";
        ArrayList<String> list = getMysqlProcessList(connection);
        for (String record : list) {
            returnVal += record + "<br>";
        }
        return returnVal;
    }

    public static String getMysqlProcessListHtml(ArrayList<String> list) {
        String returnVal = "";
        for (String record : list) {
            returnVal += record + "<br>";
        }
        return returnVal;
    }

    public static String getMysqlProcessListStr(Connection connection)
            throws SQLException {
        String returnVal = "";
        ArrayList<String> list = getMysqlProcessList(connection);
        for (String record : list) {
            returnVal += record + "\n";
        }
        return returnVal;
    }

    public static void logMysqlProcessList(Connection connection)
            throws SQLException {
        ArrayList<String> list = getMysqlProcessList(connection);
        for (String record : list) {
            s_log.warn("getMysqlProcessList() record:" + record);
        }
    }

    public static ArrayList<String> getMysqlProcessList(Connection connection)
            throws SQLException {
        String query = "show full processlist";
        String delim = " | ";
        ArrayList<String> list = new ArrayList<>();

        Statement stmt = null;
        ResultSet rset = null;
        String dbMethodName = DBUtil.getDbMethodName("DBStatus.getMysqlProcessList()");
        try {
            stmt = DBUtil.getStatement(connection, "DBStatus.getMysqlProcessList()");
            rset = stmt.executeQuery(query);
            int count = 0;
            while (rset.next()) {
                if (count == 0) {
                    list.add("| Id     | User   | Host            | db   | Command | Time | State                | Info   \r\n");
                    list.add("--------------------------------------------------------------------------------------------\r\n");
                }
                ++count;

                String id = rset.getString("id");
                String user = rset.getString("user");
                String host = rset.getString("host");
                String db = rset.getString("db");
                String command = rset.getString("command");
                String time = rset.getString("time");
                String state = rset.getString("state");
                String info = rset.getString("info");

                //if ((new Integer(time)).intValue() > 1000) {
                String record = id + delim + user + delim + host + delim + db + delim + command + delim + time + delim + state + delim + info;
                list.add(record + "\r\n");
                //}
            }
        } finally {
            DBUtil.close(stmt, rset, dbMethodName);
        }
        return list;
    }


}