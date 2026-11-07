package org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The table daily_update_step: one row per step of each daily update (DailyUpdate, 2026-11-07), with its start, end,
 * result (ok, failed, skipped; null while it runs) and a short detail. Key (run_start, step). The step "end" marks the
 * end of a run; a run with that row is completed.
 *
 * Created by the application when missing (ensureSchema, at start-up from LoginMgr.startPasswordConversion and before
 * each write, always on DailyUpdate's own autocommit connection, never in the nightly transaction); record and undo in
 * db/upgrade/9.3.0/2026-11-07-daily-update-step.sql. Rows older than KEEP_DAYS are deleted at the start of each run.
 * Tests: DailyUpdateDbTest (MySQL 8.4 in CI).
 */
public final class DailyUpdateDb {

    public static final String TABLE = "daily_update_step";
    public static final int KEEP_DAYS = 400;

    private static volatile boolean s_schemaChecked = false;

    private DailyUpdateDb() {}

    public static String createTable() {
        return "CREATE TABLE IF NOT EXISTS daily_update_step ("
            + " run_start datetime NOT NULL,"
            + " step varchar(32) NOT NULL,"
            + " step_order int NOT NULL,"
            + " started datetime NULL,"
            + " ended datetime NULL,"
            + " result varchar(16) NULL,"
            + " detail varchar(500) NULL,"
            + " PRIMARY KEY (run_start, step)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    /** Creates the table when missing (once per server). Call only on a connection of its own (CREATE commits). */
    public static void ensureSchema(Connection connection) throws SQLException {
        if (s_schemaChecked) return;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createTable());
        }
        s_schemaChecked = true;
    }

    /** For tests: check the table again. */
    public static void resetSchemaCheck() {
        s_schemaChecked = false;
    }

    private static Timestamp ts(Date d) {
        return d == null ? null : new Timestamp(d.getTime());
    }

    /** The step starts (a second start of the same step in the same run starts it again). */
    public static void start(Connection c, Date runStart, String step, int order, Date started) throws SQLException {
        String sql = "insert into daily_update_step (run_start, step, step_order, started, ended, result, detail)"
            + " values (?, ?, ?, ?, null, null, null)"
            + " on duplicate key update step_order = values(step_order), started = values(started), ended = null, result = null, detail = null";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setTimestamp(1, ts(runStart));
            p.setString(2, step);
            p.setInt(3, order);
            p.setTimestamp(4, ts(started));
            p.executeUpdate();
        }
    }

    /** The step ends with result (ok, failed, skipped) and detail (cut to 500 characters). */
    public static void end(Connection c, Date runStart, String step, Date ended, String result, String detail) throws SQLException {
        String sql = "update daily_update_step set ended = ?, result = ?, detail = ? where run_start = ? and step = ?";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setTimestamp(1, ts(ended));
            p.setString(2, result);
            p.setString(3, detail == null ? null : (detail.length() > 500 ? detail.substring(0, 497) + "..." : detail));
            p.setTimestamp(4, ts(runStart));
            p.setString(5, step);
            p.executeUpdate();
        }
    }

    /** The starts of the last n runs, newest first. */
    public static List<Date> lastRuns(Connection c, int n) throws SQLException {
        List<Date> out = new ArrayList<>();
        String sql = "select run_start from daily_update_step group by run_start order by run_start desc limit ?";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, n);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) out.add(new Date(r.getTimestamp(1).getTime()));
            }
        }
        return out;
    }

    /** The rows of one run, in step order. */
    public static List<DailyUpdate.Row> rows(Connection c, Date runStart) throws SQLException {
        List<DailyUpdate.Row> out = new ArrayList<>();
        String sql = "select step, started, ended, result, detail from daily_update_step where run_start = ? order by step_order, step";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setTimestamp(1, ts(runStart));
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    Timestamp s = r.getTimestamp(2);
                    Timestamp e = r.getTimestamp(3);
                    out.add(new DailyUpdate.Row(r.getString(1), s == null ? null : new Date(s.getTime()),
                        e == null ? null : new Date(e.getTime()), r.getString(4), r.getString(5)));
                }
            }
        }
        return out;
    }

    /**
     * Usual seconds per step: the median duration of the step's ok rows in the last runs completed (with an "end" row)
     * before the run starting at before (null: any).
     */
    public static Map<String, Long> usual(Connection c, Date before, int runs) throws SQLException {
        String sql = "select s.step, timestampdiff(second, s.started, s.ended) from daily_update_step s"
            + " join (select run_start from daily_update_step where step = 'end'" + (before == null ? "" : " and run_start < ?")
            + " order by run_start desc limit ?) r on r.run_start = s.run_start"
            + " where s.result = 'ok' and s.started is not null and s.ended is not null";
        Map<String, List<Long>> values = new HashMap<>();
        try (PreparedStatement p = c.prepareStatement(sql)) {
            int i = 1;
            if (before != null) p.setTimestamp(i++, ts(before));
            p.setInt(i, runs);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) values.computeIfAbsent(r.getString(1), k -> new ArrayList<>()).add(r.getLong(2));
            }
        }
        Map<String, Long> out = new HashMap<>();
        for (Map.Entry<String, List<Long>> e : values.entrySet()) out.put(e.getKey(), DailyUpdate.median(e.getValue()));
        return out;
    }

    /** Deletes runs older than days. */
    public static int prune(Connection c, int days) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("delete from daily_update_step where run_start < now() - interval ? day")) {
            p.setInt(1, days);
            return p.executeUpdate();
        }
    }
}
