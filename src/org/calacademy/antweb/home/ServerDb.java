package org.calacademy.antweb.home;

import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.ServerStatusAction;

public class ServerDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(ServerDb.class);

    public ServerDb(Connection connection) {
        super(connection);
    }



    // To allow debugging on server without a restart, debug statements can be added to JSP files, and turned
    // Value can be viewed and set on the Server Status Page.
    // Deprecated:
    // on and off by modifying the server database table debug field as such:
    //     update server set debug = "debugUserAgents";
    // Then in JSP code (or Java code for premeditated live debugging):
    //     if (A.isDebug("debugUserAgents")) AntwebUtil.log("...");
    // Value will be fetched from database by SessionRequestFilter every SessionRequestFilter.s_period minutes.
    // Suggested user a debug option with "debug" in the term so that it can be easily searched for in the code base.

    // Maintain this list. It drives the links on the Server Status Page.
    private static String[] s_serverDebugs = {"logGetConns", "debugUserAgents"};
    public static String[] getServerDebugs() { return s_serverDebugs; }

    private static String s_debug = null;
    public static String getServerDebug() {
        return s_debug;
    }

    public static boolean isServerDebug(String option) {
        if (s_debug != null && s_debug.equals(option)) return true;
        return false;
    }
    public static String getServerDebug(Connection connection) throws SQLException {
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "ServerDb.getServerDebug()");
            String query = "select debug from server";
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                s_debug = rset.getString("debug");
            }
        } catch (SQLException e) {
            s_log.error("getServerDebug() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "ServerDb.getServerDebug()");
        }
        return s_debug;
    }
    public static void setServerDebug(String value, Connection connection) throws SQLException {
        s_debug = value;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "ServerDb.setServerDebug()");
            String dml = "update server set debug = '" + value +"'";
            stmt.executeUpdate(dml);
        } catch (SQLException e) {
            s_log.error("setServerDebug() " + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "ServerDb.setServerDebug()");
        }
    }


    public static boolean isInDownTime() {
        return !"".equals(getDownTimeMessage());
    }
    public static final String DOWN = "<h3><font color=red>The Upload Services are down for site maintenance.</font></h3>";
    private static String downTimeMessage = "";
    public static String getDownTimeMessage() {
        return downTimeMessage;
    }
    public static String getSimpleDownTimeMessage() {
        return downTimeMessage;
    }
    public static String isDownTime(String action, Connection connection)
            throws SQLException {
        String message = "";

        boolean downTime = getIsDownTime(connection);
        if (downTime) message = getDownTimeMessage();
        s_log.debug("isDownTime() downTime:" + downTime);

/*
        if ("".equals(message)) {
          long minUntilReboot = 0;
          if (!AntwebProps.isDevMode()) minUntilReboot = AntwebUtil.minUntil8pm();
          if (minUntilReboot > 0 && minUntilReboot < 30) {
              message = "<h3><font color=red>Server is going down in " + minUntilReboot + " minutes. Please try again later.</font></h3>";
          }
        }
*/
        if (!"".equals(message)) s_log.warn("isDownTime message:" + message);
        downTimeMessage = message;
        return message;
    }


    public static boolean getIsDownTime(Connection connection)
            throws SQLException {
        int downTime = 0;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "ServerDb.getIsDownTime()");
            String query = "select is_down_time from server";
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                downTime = rset.getInt("is_down_time");
                if (downTime == 1) {
                    downTimeMessage = DOWN;
                } else {
                    downTimeMessage = "";
                }
            }
        } catch (SQLException e) {
            s_log.error("getIsDownTime() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "ServerDb.getIsDownTime()");
        }
        return downTime == 1;
    }

    public static String toggleDownTime(Connection connection)
            throws SQLException {
        int downTime = 0;

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "ServerDb.toggleIsDownTime()");
            boolean isDownTime = getIsDownTime(connection);
            if (isDownTime) downTime = 0; else downTime = 1;
            if (downTime == 1) {
                downTimeMessage = DOWN;
            } else {
                downTimeMessage = "";
            }
            String dml = "update server set is_down_time = " + downTime;
            stmt.executeUpdate(dml);
        } catch (SQLException e) {
            s_log.error("toggleIsDownTime() " + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "ServerDb.toggleIsDownTime()");
        }
        if (downTime == 1) {
            return "Service is now offline";
        } else {
            return "Service is now online";
        }
    }

}

