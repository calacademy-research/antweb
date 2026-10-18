package org.calacademy.antweb.home;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.PasswordHash;

/**
 * Database side of password reset (see PasswordResetMgr).
 *
 *   password_reset          one row per reset link; only the SHA-256 of the token is stored
 *   login.must_reset        1 = the next login must set a new password first
 *   login.session_version   incremented to end every existing session of a login
 *                           (also deletes the login's "Keep me logged in" tokens, RememberTokenDb)
 *
 * ensureSchema() creates the table and columns when missing (called once at
 * startup from LoginMgr). Same DDL: db/upgrade/8.108/2026-10-16.sql.
 */
public class PasswordResetDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(PasswordResetDb.class);

    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS password_reset ("
        + " id int NOT NULL AUTO_INCREMENT,"
        + " login_id int NOT NULL,"
        + " token_hash char(64) NOT NULL,"
        + " created datetime NOT NULL,"
        + " expires datetime NOT NULL,"
        + " used_at datetime NULL DEFAULT NULL,"
        + " requested_ip varchar(64) DEFAULT NULL,"
        + " PRIMARY KEY (id),"
        + " UNIQUE KEY token_hash (token_hash),"
        + " KEY login_created (login_id, created)"
        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    public PasswordResetDb(Connection connection) {
        super(connection);
    }

    /** Create password_reset and the two login columns if they are missing. Safe to run repeatedly. */
    public void ensureSchema() throws SQLException {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate(CREATE_TABLE);
            if (!hasLoginColumn("must_reset")) {
                stmt.executeUpdate("ALTER TABLE login ADD COLUMN must_reset tinyint NOT NULL DEFAULT 0");
                s_log.warn("ensureSchema() added login.must_reset");
            }
            if (!hasLoginColumn("session_version")) {
                stmt.executeUpdate("ALTER TABLE login ADD COLUMN session_version int NOT NULL DEFAULT 0");
                s_log.warn("ensureSchema() added login.session_version");
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

    /** Reset links created for this login in the last hour (used or not). */
    public int countRecentRequests(int loginId) throws SQLException {
        String q = "select count(*) from password_reset where login_id = ? and created > now() - interval 1 hour";
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            stmt.setInt(1, loginId);
            try (ResultSet rset = stmt.executeQuery()) {
                return rset.next() ? rset.getInt(1) : 0;
            }
        }
    }

    /** Links (reset, reactivation, invitation) made for this login in the last given minutes. */
    public int countRequestsWithin(int loginId, int minutes) throws SQLException {
        String q = "select count(*) from password_reset where login_id = ? and created > now() - interval ? minute";
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            stmt.setInt(1, loginId);
            stmt.setInt(2, minutes);
            try (ResultSet rset = stmt.executeQuery()) {
                return rset.next() ? rset.getInt(1) : 0;
            }
        }
    }

    public void insertToken(int loginId, String tokenHash, int validMinutes, String ip) throws SQLException {
        String q = "insert into password_reset (login_id, token_hash, created, expires, requested_ip)"
            + " values (?, ?, now(), now() + interval ? minute, ?)";
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            stmt.setInt(1, loginId);
            stmt.setString(2, tokenHash);
            stmt.setInt(3, validMinutes);
            if (ip != null && ip.length() > 64) ip = ip.substring(0, 64);
            stmt.setString(4, ip);
            stmt.executeUpdate();
        }
    }

    /** The login id of an unused, unexpired token, else 0. Does not consume it. */
    public int findValidLoginId(String tokenHash) throws SQLException {
        String q = "select login_id from password_reset where token_hash = ? and used_at is null and expires > now()";
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            stmt.setString(1, tokenHash);
            try (ResultSet rset = stmt.executeQuery()) {
                return rset.next() ? rset.getInt(1) : 0;
            }
        }
    }

    /** Atomically mark the token used. True only for the one request that consumed a valid token. */
    public boolean consumeToken(String tokenHash, int loginId) throws SQLException {
        String q = "update password_reset set used_at = now()"
            + " where token_hash = ? and login_id = ? and used_at is null and expires > now()";
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            stmt.setString(1, tokenHash);
            stmt.setInt(2, loginId);
            return stmt.executeUpdate() == 1;
        }
    }

    /** Mark every outstanding token of the login as used. */
    public int invalidateOutstanding(int loginId) throws SQLException {
        String q = "update password_reset set used_at = now() where login_id = ? and used_at is null";
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            stmt.setInt(1, loginId);
            return stmt.executeUpdate();
        }
    }

    /**
     * Store the new password (hashed), clear must_reset and end all sessions.
     * Returns the new session_version.
     */
    public int setNewPassword(int loginId, String plainPassword) throws SQLException {
        String q = "update login set password = ?, must_reset = 0, session_version = session_version + 1 where id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            stmt.setString(1, PasswordHash.hash(plainPassword));
            stmt.setInt(2, loginId);
            stmt.executeUpdate();
        }
        RememberTokenDb.deleteForLoginQuietly(getConnection(), loginId);
        return getSessionVersion(loginId);
    }

    /**
     * After a password change by its owner or an administrator: clear must_reset,
     * cancel outstanding reset links and end all sessions. Returns the new session_version.
     */
    public int afterPasswordChange(int loginId) throws SQLException {
        try (PreparedStatement stmt = getConnection().prepareStatement(
                "update login set must_reset = 0, session_version = session_version + 1 where id = ?")) {
            stmt.setInt(1, loginId);
            stmt.executeUpdate();
        }
        invalidateOutstanding(loginId);
        RememberTokenDb.deleteForLoginQuietly(getConnection(), loginId);
        return getSessionVersion(loginId);
    }

    public int getSessionVersion(int loginId) throws SQLException {
        try (PreparedStatement stmt = getConnection().prepareStatement("select session_version from login where id = ?")) {
            stmt.setInt(1, loginId);
            try (ResultSet rset = stmt.executeQuery()) {
                return rset.next() ? rset.getInt(1) : 0;
            }
        }
    }

    /** End all sessions of the login. Returns the new session_version. */
    public int bumpSessionVersion(int loginId) throws SQLException {
        try (PreparedStatement stmt = getConnection().prepareStatement("update login set session_version = session_version + 1 where id = ?")) {
            stmt.setInt(1, loginId);
            stmt.executeUpdate();
        }
        RememberTokenDb.deleteForLoginQuietly(getConnection(), loginId);
        return getSessionVersion(loginId);
    }

    public boolean isMustReset(int loginId) throws SQLException {
        try (PreparedStatement stmt = getConnection().prepareStatement("select must_reset from login where id = ?")) {
            stmt.setInt(1, loginId);
            try (ResultSet rset = stmt.executeQuery()) {
                return rset.next() && rset.getInt(1) != 0;
            }
        }
    }

    /** Set must_reset = 1 and end all sessions of the login. */
    public void markMustReset(int loginId) throws SQLException {
        String q = "update login set must_reset = 1, session_version = session_version + 1 where id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            stmt.setInt(1, loginId);
            stmt.executeUpdate();
        }
        RememberTokenDb.deleteForLoginQuietly(getConnection(), loginId);
    }

    /** A login in the reset campaign: administrators and curators. */
    public static final class Account {
        public int id;
        public String name;
        public String firstName;
        public String lastName;
        public String email;
        public boolean isAdmin;
        public boolean mustReset;

        public String getDisplayName() {
            String full = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
            return full.isEmpty() ? (name == null ? "" : name) : full;
        }
        public boolean hasEmail() {
            return email != null && email.contains("@");
        }
    }

    private static final String ACCOUNT_COLUMNS = "id, name, first_name, last_name, email, is_admin, must_reset";

    private static Account readAccount(ResultSet rset) throws SQLException {
        Account a = new Account();
        a.id = rset.getInt("id");
        a.name = rset.getString("name");
        a.firstName = rset.getString("first_name");
        a.lastName = rset.getString("last_name");
        a.email = rset.getString("email");
        a.isAdmin = rset.getBoolean("is_admin");
        a.mustReset = rset.getInt("must_reset") != 0;
        return a;
    }

    /** The login with this username, else the one with this email address, else null. */
    public Account findAccount(String nameOrEmail) throws SQLException {
        if (nameOrEmail == null || nameOrEmail.trim().isEmpty()) return null;
        String value = nameOrEmail.trim();
        Account a = findAccountBy("select " + ACCOUNT_COLUMNS + " from login where id > 0 and name = ? order by id limit 1", value);
        if (a == null && value.contains("@")) {
            a = findAccountBy("select " + ACCOUNT_COLUMNS + " from login where id > 0 and email = ? order by id limit 1", value);
        }
        return a;
    }

    public Account getAccount(int loginId) throws SQLException {
        try (PreparedStatement stmt = getConnection().prepareStatement("select " + ACCOUNT_COLUMNS + " from login where id = ?")) {
            stmt.setInt(1, loginId);
            try (ResultSet rset = stmt.executeQuery()) {
                return rset.next() ? readAccount(rset) : null;
            }
        }
    }

    private Account findAccountBy(String query, String value) throws SQLException {
        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setString(1, value);
            try (ResultSet rset = stmt.executeQuery()) {
                return rset.next() ? readAccount(rset) : null;
            }
        }
    }

    /**
     * Administrators and curators: is_admin, or a group, upload rights
     * (see Login.isCurator()), or assigned projects (login_project).
     */
    public List<Account> getCampaignAccounts() throws SQLException {
        String q = "select " + ACCOUNT_COLUMNS + " from login"
            + " where id > 0 and (is_admin = 1 or group_id > 0 or is_upload_specimens = 1 or is_upload_images = 1"
            + " or id in (select login_id from login_project))"
            + " order by id";
        List<Account> list = new ArrayList<>();
        try (PreparedStatement stmt = getConnection().prepareStatement(q);
             ResultSet rset = stmt.executeQuery()) {
            while (rset.next()) list.add(readAccount(rset));
        }
        return list;
    }
}
