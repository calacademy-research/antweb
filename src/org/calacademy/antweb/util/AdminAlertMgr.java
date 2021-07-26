package org.calacademy.antweb.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.AntFormatter;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class AdminAlertMgr {

    private static final Log s_log = LogFactory.getLog(AdminAlertMgr.class);

    public static List<AdminAlert> s_adminAlerts = new ArrayList<AdminAlert>();

    public static String s_queryAlerts = "";

    public static void populate(Connection connection) {

        s_adminAlerts = new ArrayList<>();

        // Get any alerts from the admin_alerts table.          
        String query;
        Statement stmt = null;
        ResultSet rset;
        try {
            query = "select * from admin_alerts where acknowledged = 0 order by created desc";
            stmt = DBUtil.getStatement(connection, "AdminAlertMgr.populate()");

            stmt.execute(query);
            rset = stmt.getResultSet();
            while (rset.next()) {
                AdminAlert adminAlert = new AdminAlert();
                adminAlert.setId(rset.getInt("id"));
                adminAlert.setAlert(rset.getString("alert"));
                adminAlert.setCreated(rset.getTimestamp("created"));
                adminAlert.setIsAcknowledged(rset.getInt("acknowledged") == 1);

                s_adminAlerts.add(adminAlert);
            }
        } catch (Exception e) {
            s_log.warn("populate() e:" + e);
        } finally {
            DBUtil.close(stmt, "AdminAlertMgr.populate()");
        }

        // Add any alerting queries.
        try {
            s_queryAlerts = QueryManager.adminAlerts(connection);
        } catch (java.sql.SQLException e) {
            s_log.warn("checkIntegrity() e:" + e);
        }

    }

    public static String getQueryAlerts() {
        return s_queryAlerts;
    }


    // Don't use this! Just writes to a log file. Use add(String, connection).
    public static void log(String message) {
        LogMgr.appendLog("adminAlerts.log", message, true);
    }

    public static void add(String message, Connection connection) {
        s_log.warn("AdminAlertMgr.add(message)");
        Statement stmt = null;
        String dml = "insert into admin_alerts (alert) values ('" + AntFormatter.escapeQuotes(message) + "')	";
        try {
            stmt = DBUtil.getStatement(connection, "AdminAlertMgr.add()");
            stmt.executeUpdate(dml);
            A.log("add() dml:" + dml);

        } catch (Exception e) {
            s_log.warn("add() e:" + e + " dml:" + dml);
        } finally {
            DBUtil.close(stmt, "AdminAlertMgr.add()");
        }

        AdminAlertMgr.populate(connection);
    }

    public static final String noWorldantsChanges = "No Worldants changes in last week. See: <a href='" + AntwebProps.getDomainApp() + "/query.do?name=worldantsUploads'>Worldants Upload Report</a>.";
    public static final String noWorldantsChangesContains = "No Worldants changes";  // Should be a substring of the above.
    public static final String WEEK = "INTERVAL DAYOFWEEK(curdate()) + 6 DAY";

    public static void addIfFresh(String message, String contains, String period, Connection connection) {
        Statement stmt = null;
        ResultSet rset;
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
        A.log("AdminAlertMgr.removeAll()");
        Statement stmt = null;
        String dml = "update admin_alerts set acknowledged = 1";
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
        String dml = "update admin_alerts set acknowledged = 1 where id = " + id;
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
