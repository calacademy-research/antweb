package org.calacademy.antweb.home;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.InactiveAccounts;

/**
 * Database side of inactive accounts (see InactiveAccounts for the rule).
 *
 *   login.inactive         1 = the account cannot log in with its password until it is
 *                          reactivated by an emailed link
 *   login.inactive_since   when it was made inactive (NULL when active)
 *
 * ensureSchema() adds both columns when missing (once at startup from LoginMgr).
 * Same DDL: db/upgrade/8.112/2026-10-18-login-inactive.sql.
 *
 * Every update of a login row here sets "created = created": login.created is
 * ON UPDATE CURRENT_TIMESTAMP in the production schema.
 */
public class InactiveAccountDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(InactiveAccountDb.class);

    public InactiveAccountDb(Connection connection) {
        super(connection);
    }

    /** Add login.inactive and login.inactive_since if missing. Safe to run repeatedly. */
    public void ensureSchema() throws SQLException {
        try (Statement stmt = getConnection().createStatement()) {
            if (!hasLoginColumn("inactive")) {
                stmt.executeUpdate("ALTER TABLE login ADD COLUMN inactive tinyint NOT NULL DEFAULT 0");
                s_log.warn("ensureSchema() added login.inactive");
            }
            if (!hasLoginColumn("inactive_since")) {
                stmt.executeUpdate("ALTER TABLE login ADD COLUMN inactive_since datetime NULL DEFAULT NULL");
                s_log.warn("ensureSchema() added login.inactive_since");
            }
        }
    }

    private boolean hasLoginColumn(String column) throws SQLException {
        String q = "select count(*) from information_schema.columns"
            + " where table_schema = database() and table_name = 'login' and column_name = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            stmt.setString(1, column);
            try (ResultSet rset = stmt.executeQuery()) {
                return rset.next() && rset.getInt(1) > 0;
            }
        }
    }

    /** True if the login is inactive. False if not, or if the column does not exist yet. */
    public boolean isInactive(int loginId) {
        try (PreparedStatement stmt = getConnection().prepareStatement("select inactive from login where id = ?")) {
            stmt.setInt(1, loginId);
            try (ResultSet rset = stmt.executeQuery()) {
                return rset.next() && rset.getInt(1) != 0;
            }
        } catch (SQLException e) {
            s_log.warn("isInactive() login:" + loginId + " e:" + e);  // e.g. column not created yet
            return false;
        }
    }

    /** Set the login active again (after a reactivation link was used). True if it was inactive. */
    public boolean reactivate(int loginId) throws SQLException {
        String q = "update login set inactive = 0, inactive_since = null, created = created where id = ? and inactive = 1";
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            stmt.setInt(1, loginId);
            return stmt.executeUpdate() > 0;
        }
    }

    /** reactivate(), logging instead of throwing (e.g. column not created yet). */
    public static boolean reactivateQuietly(Connection connection, int loginId) {
        try {
            return new InactiveAccountDb(connection).reactivate(loginId);
        } catch (SQLException e) {
            s_log.warn("reactivateQuietly() login:" + loginId + " e:" + e);
            return false;
        }
    }

    /** Every login with id > 0, with what the rule needs. About 2,900 rows. */
    public List<InactiveAccounts.Candidate> getCandidates() throws SQLException {
        String q = "select l.id, l.name, l.first_name, l.last_name, l.email, l.is_admin, l.group_id,"
            + " l.is_upload_specimens, l.is_upload_images, l.last_login, l.inactive,"
            + " exists (select 1 from login_project p where p.login_id = l.id) as has_projects,"
            + " exists (select 1 from login_country c where c.login_id = l.id) as has_countries"
            + " from login l where l.id > 0 order by l.id";
        List<InactiveAccounts.Candidate> list = new ArrayList<>();
        try (PreparedStatement stmt = getConnection().prepareStatement(q);
             ResultSet rset = stmt.executeQuery()) {
            while (rset.next()) {
                InactiveAccounts.Candidate c = new InactiveAccounts.Candidate();
                c.id = rset.getInt("id");
                c.name = rset.getString("name");
                c.firstName = rset.getString("first_name");
                c.lastName = rset.getString("last_name");
                c.email = rset.getString("email");
                c.isAdmin = rset.getInt("is_admin") != 0;
                c.groupId = rset.getInt("group_id");  // NULL reads as 0: no group
                c.uploadSpecimens = rset.getInt("is_upload_specimens") != 0;
                c.uploadImages = rset.getInt("is_upload_images") != 0;
                Timestamp last = rset.getTimestamp("last_login");
                c.lastLogin = last == null ? null : last.toLocalDateTime();
                c.inactive = rset.getInt("inactive") != 0;
                c.hasProjects = rset.getInt("has_projects") != 0;
                c.hasCountries = rset.getInt("has_countries") != 0;
                list.add(c);
            }
        }
        return list;
    }

    /**
     * For the preview: how many of these accounts have rows in tables that refer to a login
     * (nothing is deleted, so these rows stay as they are). Label -> count; a table that does
     * not exist is left out.
     */
    public Map<String, Integer> countLinkedData(List<InactiveAccounts.Candidate> accounts) throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (accounts.isEmpty()) return counts;
        String[][] refs = {
            {"specimen uploads (upload.login_id)", "upload", "login_id"},
            {"image uploads (image_upload.curator_id)", "image_upload", "curator_id"},
            {"page text edits (description_edit.access_login)", "description_edit", "access_login"},
            {"page text edits (object_edit.access_login)", "object_edit", "access_login"},
            {"image likes (image_like.login_id)", "image_like", "login_id"},
        };
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (InactiveAccounts.Candidate c : accounts) ids.add(c.id);
        for (String[] r : refs) {
            // Table and column names are the constants above, never user input.
            String q = "select distinct " + r[2] + " from " + r[1] + " where " + r[2] + " > 0";
            try (Statement stmt = getConnection().createStatement(); ResultSet rset = stmt.executeQuery(q)) {
                int n = 0;
                while (rset.next()) if (ids.contains(rset.getInt(1))) n++;
                counts.put(r[0], n);
            } catch (SQLException e) {
                if (e.getErrorCode() != 1146 && e.getErrorCode() != 1054) throw e;  // no such table / column
            }
        }
        return counts;
    }

    /** What apply() did. */
    public static final class ApplyResult {
        public String backupTable;
        public int backedUp;
        public int updated;
        public int rememberTokensDeleted;
    }

    /**
     * Make these accounts inactive: copy their login rows into a new backup table
     * (login_inactive_backup_YYYYMMDD, or with _HHMMSS added if that exists), then in one
     * transaction set inactive = 1, inactive_since = now(), session_version + 1 (ends every
     * session) and delete their "Keep me logged in" tokens. Only rows still active and still
     * regular are changed (checked again in the update).
     */
    public ApplyResult apply(List<InactiveAccounts.Candidate> accounts, LocalDateTime now) throws SQLException {
        ApplyResult r = new ApplyResult();
        if (accounts.isEmpty()) return r;

        String backup = InactiveAccounts.backupTableName(now);
        if (tableExists(backup)) backup += String.format("_%02d%02d%02d", now.getHour(), now.getMinute(), now.getSecond());
        r.backupTable = backup;
        // DDL commits implicitly in MySQL, so the backup table is made before the transaction.
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate("create table " + backup + " like login");
        }

        List<Integer> ids = new ArrayList<>();
        for (InactiveAccounts.Candidate c : accounts) ids.add(c.id);

        Connection conn = getConnection();
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (int from = 0; from < ids.size(); from += 500) {
                List<Integer> chunk = ids.subList(from, Math.min(ids.size(), from + 500));
                String in = placeholders(chunk.size());
                try (PreparedStatement ins = conn.prepareStatement(
                        "insert into " + backup + " select * from login where inactive = 0 and id in (" + in + ")")) {
                    bind(ins, chunk);
                    r.backedUp += ins.executeUpdate();
                }
                try (PreparedStatement upd = conn.prepareStatement(
                        "update login set inactive = 1, inactive_since = now(), session_version = session_version + 1,"
                        + " created = created"
                        + " where inactive = 0 and coalesce(is_admin, 0) = 0 and coalesce(group_id, 0) <= 0"
                        + " and coalesce(is_upload_specimens, 0) = 0 and coalesce(is_upload_images, 0) = 0"
                        + " and id in (" + in + ")")) {
                    bind(upd, chunk);
                    r.updated += upd.executeUpdate();
                }
                try (PreparedStatement del = conn.prepareStatement(
                        "delete from remember_token where login_id in (" + in + ")")) {
                    bind(del, chunk);
                    r.rememberTokensDeleted += del.executeUpdate();
                } catch (SQLException e) {
                    if (e.getErrorCode() != 1146) throw e;  // remember_token not created yet: nothing to delete
                }
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
        return r;
    }

    private boolean tableExists(String table) throws SQLException {
        String q = "select count(*) from information_schema.tables where table_schema = database() and table_name = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            stmt.setString(1, table);
            try (ResultSet rset = stmt.executeQuery()) {
                return rset.next() && rset.getInt(1) > 0;
            }
        }
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(i == 0 ? "?" : ",?");
        return sb.toString();
    }

    private static void bind(PreparedStatement stmt, List<Integer> ids) throws SQLException {
        for (int i = 0; i < ids.size(); i++) stmt.setInt(i + 1, ids.get(i));
    }
}
