package org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The table background_job: one row per job started from a web page (BackgroundJobs), so its
 * status page works after a reload and after a restart. Created by the application when missing
 * (ensureSchema(), called at startup); the record and undo are in db/upgrade/8.113/2026-10-20-background-job.sql.
 *
 * The application's store takes connections from the "conPool" data source, which the actions
 * hand over (setDataSource) before they start or read a job. The static methods take a connection
 * (startup, tests).
 */
public final class BackgroundJobDb implements BackgroundJobs.Store {

    private static final Log s_log = LogFactory.getLog(BackgroundJobDb.class);

    public static final String TABLE = "background_job";

    private static volatile DataSource s_dataSource;

    /** The data source the application's store uses (the actions pass "conPool"). */
    public static void setDataSource(DataSource dataSource) {
        if (dataSource != null) s_dataSource = dataSource;
    }

    public static String createTable() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
            + " id int NOT NULL AUTO_INCREMENT,"
            + " kind varchar(16) NOT NULL,"
            + " action varchar(64) NOT NULL,"
            + " title varchar(255) NULL,"
            + " login_id int NOT NULL DEFAULT 0,"
            + " login_name varchar(128) NULL,"
            + " group_id int NOT NULL DEFAULT 0,"
            + " status varchar(16) NOT NULL,"
            + " step varchar(500) NULL,"
            + " result text NULL,"
            + " result_link varchar(500) NULL,"
            + " result_link_text varchar(255) NULL,"
            + " created datetime NOT NULL,"
            + " started datetime NULL,"
            + " finished datetime NULL,"
            + " PRIMARY KEY (id),"
            + " KEY by_status (status),"
            + " KEY by_group (group_id, id)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    private static volatile boolean s_schemaChecked = false;

    /** Creates the table when missing. */
    public static void ensureSchema(Connection connection) throws SQLException {
        if (s_schemaChecked) return;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createTable());
        }
        s_schemaChecked = true;
    }

    /**
     * At startup: rows still queued or running belong to a run of the application that has
     * stopped. They become "interrupted" (except the ids in keep: jobs of this run). Returns the
     * number of rows changed.
     */
    public static int markInterrupted(Connection connection, List<Integer> keep) throws SQLException {
        StringBuilder sql = new StringBuilder("update " + TABLE + " set status = 'interrupted', step = ?, finished = ?"
            + " where status in ('queued', 'running')");
        for (int i = 0; i < keep.size(); i++) sql.append(i == 0 ? " and id not in (?" : ", ?");
        if (!keep.isEmpty()) sql.append(")");
        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            stmt.setString(1, BackgroundJobs.INTERRUPTED_STEP);
            stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            for (int i = 0; i < keep.size(); i++) stmt.setInt(3 + i, keep.get(i));
            return stmt.executeUpdate();
        }
    }

    public static int insert(Connection connection, BackgroundJobs.Job job) throws SQLException {
        String sql = "insert into " + TABLE + " (kind, action, title, login_id, login_name, group_id, status, step, created)"
            + " values (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, job.kind);
            stmt.setString(2, cut(job.action, 64));
            stmt.setString(3, cut(job.title, 255));
            stmt.setInt(4, job.loginId);
            stmt.setString(5, cut(job.loginName, 128));
            stmt.setInt(6, job.groupId);
            stmt.setString(7, job.getStatus().name());
            stmt.setString(8, cut(job.getStep(), 500));
            stmt.setTimestamp(9, ts(job.getCreated()));
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("no id for the new background job");
    }

    public static void update(Connection connection, BackgroundJobs.Job job) throws SQLException {
        String sql = "update " + TABLE + " set status = ?, step = ?, result = ?, result_link = ?, result_link_text = ?,"
            + " started = ?, finished = ? where id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, job.getStatus().name());
            stmt.setString(2, cut(job.getStep(), 500));
            stmt.setString(3, cut(job.getResult(), 60000));
            stmt.setString(4, cut(job.getResultLink(), 500));
            stmt.setString(5, cut(job.getResultLinkText(), 255));
            stmt.setTimestamp(6, ts(job.getStarted()));
            stmt.setTimestamp(7, ts(job.getFinished()));
            stmt.setInt(8, job.getId());
            stmt.executeUpdate();
        }
    }

    public static BackgroundJobs.Job load(Connection connection, int id) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("select * from " + TABLE + " where id = ?")) {
            stmt.setInt(1, id);
            try (ResultSet rset = stmt.executeQuery()) {
                return rset.next() ? row(rset) : null;
            }
        }
    }

    public static List<BackgroundJobs.Job> recent(Connection connection, int groupId, int limit) throws SQLException {
        String sql = "select * from " + TABLE + (groupId != 0 ? " where group_id = ?" : "") + " order by id desc limit ?";
        List<BackgroundJobs.Job> out = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            int i = 1;
            if (groupId != 0) stmt.setInt(i++, groupId);
            stmt.setInt(i, Math.max(1, limit));
            try (ResultSet rset = stmt.executeQuery()) {
                while (rset.next()) out.add(row(rset));
            }
        }
        return out;
    }

    private static BackgroundJobs.Job row(ResultSet rset) throws SQLException {
        BackgroundJobs.Job job = new BackgroundJobs.Job(rset.getString("kind"), rset.getString("action"), rset.getString("title"),
            rset.getInt("login_id"), rset.getString("login_name"), rset.getInt("group_id"));
        job.restore(rset.getInt("id"), BackgroundJobs.Status.of(rset.getString("status")), rset.getString("step"),
            rset.getString("result"), rset.getString("result_link"), rset.getString("result_link_text"),
            date(rset.getTimestamp("created")), date(rset.getTimestamp("started")), date(rset.getTimestamp("finished")));
        return job;
    }

    private static Timestamp ts(Date d) {
        return d == null ? null : new Timestamp(d.getTime());
    }

    private static Date date(Timestamp t) {
        return t == null ? null : new Date(t.getTime());
    }

    private static String cut(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    // ------------------------------------------------------------------ the store (BackgroundJobs.Store)

    private Connection connection(String name) throws SQLException {
        DataSource ds = s_dataSource;
        if (ds == null) throw new SQLException("no data source for " + TABLE);
        Connection c = DBUtil.getConnection(ds, name);
        if (c == null) throw new SQLException("no connection for " + TABLE);
        c.setAutoCommit(true);
        ensureSchema(c);
        return c;
    }

    @Override
    public int insert(BackgroundJobs.Job job) throws Exception {
        String name = DBUtil.getDbMethodName("BackgroundJobDb.insert()");
        Connection c = connection(name);
        try {
            return insert(c, job);
        } finally {
            DBUtil.close(c, this, name);
        }
    }

    @Override
    public void update(BackgroundJobs.Job job) throws Exception {
        String name = DBUtil.getDbMethodName("BackgroundJobDb.update()");
        Connection c = connection(name);
        try {
            update(c, job);
        } finally {
            DBUtil.close(c, this, name);
        }
    }

    @Override
    public BackgroundJobs.Job load(int id) throws Exception {
        String name = DBUtil.getDbMethodName("BackgroundJobDb.load()");
        Connection c = connection(name);
        try {
            return load(c, id);
        } finally {
            DBUtil.close(c, this, name);
        }
    }

    @Override
    public List<BackgroundJobs.Job> recent(int groupId, int limit) throws Exception {
        String name = DBUtil.getDbMethodName("BackgroundJobDb.recent()");
        Connection c = connection(name);
        try {
            return recent(c, groupId, limit);
        } finally {
            DBUtil.close(c, this, name);
        }
    }

    /** At startup (LoginMgr's start-up thread): create the table, mark jobs of the last run interrupted. */
    public static void startup(Connection connection) {
        try {
            ensureSchema(connection);
            int n = markInterrupted(connection, BackgroundJobs.get().activeIds());
            if (n > 0) s_log.warn("startup() " + n + " background jobs of the last run marked interrupted");
        } catch (SQLException e) {
            s_log.error("startup() e:" + e);
        }
    }
}
