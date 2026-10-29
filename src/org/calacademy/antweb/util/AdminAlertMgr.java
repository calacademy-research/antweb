package org.calacademy.antweb.util;

import java.util.*;

import java.sql.*;

import org.calacademy.antweb.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * Admin alerts (table admin_alerts): add() logs the message to web/log/adminAlerts.log and inserts a row;
 * addIfFresh adds it only when no matching alert exists in the period; remove and removeAll set acknowledged =
 * 1. Keeps the unacknowledged rows in memory for the Admin Alerts page (adminAlert.do) and the count in the
 * admins' header, and the alerting queries' results (QueryManager.adminAlerts). Callers: AntCat import,
 * Scheduler, MountCheck, LogSizeCheck, HttpUtil and others. Tests: tests/unit/test_admin_alert_page.py,
 * tests/e2e/test_manager_tools.py.
 */
public final class AdminAlertMgr {

    private static final Log s_log = LogFactory.getLog(AdminAlertMgr.class);

    public static List<AdminAlert> s_adminAlerts = new ArrayList<>();

    public static String s_queryAlerts = "";
    
    public static void populate(Connection connection) {
        loadAlerts(connection);

        // Add any alerting queries.
        try {
          s_queryAlerts = QueryManager.adminAlerts(connection);
        } catch (SQLException e) {
          s_log.warn("checkIntegrity() e:" + e);
        }
    }

    /** The unacknowledged rows of admin_alerts (without rerunning the alerting queries). */
    private static void loadAlerts(Connection connection) {
        List<AdminAlert> alerts = new ArrayList<>();


        // Get any alerts from the admin_alerts table.          
        String query = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {
          query = "select * from admin_alerts where acknowledged = 0 order by created desc";
          stmt = DBUtil.getStatement(connection, "AdminAlertMgr.populate()");

          stmt.execute(query);        
          rset = stmt.getResultSet();
          String alert = null;
          while (rset.next()) {
            AdminAlert adminAlert = new AdminAlert();
            adminAlert.setId(rset.getInt("id")); 
            adminAlert.setAlert(rset.getString("alert")); 
            adminAlert.setCreated(rset.getTimestamp("created")); 
            adminAlert.setIsAcknowledged(rset.getInt("acknowledged") == 1);
    
            alerts.add(adminAlert);
          }
          s_adminAlerts = alerts;
        } catch (Exception e) {
          s_log.warn("loadAlerts() e:" + e);
        } finally {
          DBUtil.close(stmt, "AdminAlertMgr.loadAlerts()");        
        }
    }

    public static String getQueryAlerts() {
      return s_queryAlerts;
    }


    // Don't use this! Just writes to a log file. Use add(String, connection).
    public static void log(String message) {
        LogMgr.appendLog("adminAlerts.log", message, true);   
    }

    /**
     * Record an alert for the administrators (Admin Alert Manager, and the count in the page header
     * of administrators). Stored as plain text; the page shows it escaped. Writing was switched off in
     * May 2026 (AdminAlertMgr.add only logged "skipping"), so failed AntCat imports, low disk space at
     * start-up and the weekly "no AntCat changes" note were never shown; restored 2026-10-21 with a
     * bound insert. Never throws: an alert must not break the caller.
     */
    public static void add(String message, Connection connection) {
        if (message == null || message.isEmpty()) return;
        LogMgr.appendLog("adminAlerts.log", message, true);
        PreparedStatement stmt = null;
        try {
          if (connection == null || connection.isClosed()) {
            s_log.warn("add() no database connection; logged only: " + message);
            return;
          }
          stmt = connection.prepareStatement("insert into admin_alerts (alert) values (?)");
          stmt.setString(1, message.length() > 4000 ? message.substring(0, 4000) : message);
          stmt.executeUpdate();
        } catch (Exception e) {
          s_log.warn("add() e:" + e);
          return;
        } finally {
          DBUtil.close(stmt, "AdminAlertMgr.add()");
        }
        loadAlerts(connection);
    }

    public static final String noWorldantsChanges = "No Worldants changes in last week. See the AntCat import history (query.do?name=worldantsUploads).";
    public static final String noWorldantsChangesContains = "No Worldants changes";  // Should be a substring of the above.
    public static final String WEEK = "INTERVAL DAYOFWEEK(curdate()) + 6 DAY";
    public static void addIfFresh(String message, String contains, String period, Connection connection) {
        Statement stmt = null;
        ResultSet rset = null;
        String query = "select count(*) count from admin_alerts where alert like '%" + contains + "%' and created >= curdate() - " + period;
        try {        
            stmt = DBUtil.getStatement(connection, "AdminAlertMgr.addIfFresh()");
            stmt.execute(query);        
            s_log.warn("addIfFresh() query:" + query);
            rset = stmt.getResultSet();
            while (rset.next()) {
              int count = rset.getInt("count"); 
              if (count <= 0) add(message, connection);
            }
        } catch (Exception e) {
          s_log.warn("addIfFresh() e:" + e);
        } finally {
          DBUtil.close(stmt, "AdminAlertMgr.addIfFresh()");        
        }
    }

    public static void removeAll(Connection connection) {
        s_log.debug("AdminAlertMgr.removeAll()");
        Statement stmt = null;
        String dml = "update admin_alerts set acknowledged = 1, created = created";   // created has ON UPDATE CURRENT_TIMESTAMP: keep the alert's date (2026-10-28)
        try {
          stmt = DBUtil.getStatement(connection, "AdminAlertMgr.removeAll()");  
          stmt.executeUpdate(dml);
        } catch (Exception e) {
          s_log.warn("removeAll() e:" + e);
        } finally {
          DBUtil.close(stmt, "AdminAlertMgr.removeAll()");        
        }

        s_adminAlerts = new ArrayList<>();
    }

    public static void remove(int id, Connection connection) {
        Statement stmt = null;
        String dml = "update admin_alerts set acknowledged = 1, created = created where id = " + id;   // keep the alert's date
        try {
          stmt = DBUtil.getStatement(connection, "AdminAlertMgr.remove()");  
          stmt.executeUpdate(dml);
        } catch (Exception e) {
          s_log.warn("remove() e:" + e);
        } finally {
          DBUtil.close(stmt, "AdminAlertMgr.remove()");        
        }

        AdminAlertMgr.populate(connection);
    }
    
    public static List<AdminAlert> getAdminAlerts() {
      return s_adminAlerts;
    }

}
