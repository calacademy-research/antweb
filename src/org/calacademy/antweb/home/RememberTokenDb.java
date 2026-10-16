package org.calacademy.antweb.home;

import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Database side of "Keep me logged in" (see RememberMeMgr).
 *
 *   remember_token   one row per remembered browser. The cookie holds selector:validator;
 *                    the selector is stored as is (to find the row), the validator only as
 *                    its SHA-256 (token_hash). prev_hash is the hash before the last
 *                    rotation, accepted for a short time so parallel requests do not log out.
 *
 * ensureSchema() creates the table when missing (called once at startup from
 * LoginMgr, like PasswordResetDb). Same DDL: db/upgrade/8.109/2026-10-16.sql.
 */
public class RememberTokenDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(RememberTokenDb.class);

    public static final int MAX_PER_LOGIN = 10;

    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS remember_token ("
        + " id int NOT NULL AUTO_INCREMENT,"
        + " login_id int NOT NULL,"
        + " selector char(16) NOT NULL,"
        + " token_hash char(64) NOT NULL,"
        + " prev_hash char(64) NULL DEFAULT NULL,"
        + " rotated datetime NULL DEFAULT NULL,"
        + " created datetime NOT NULL,"
        + " expires datetime NOT NULL,"
        + " last_used datetime NULL DEFAULT NULL,"
        + " user_agent varchar(255) DEFAULT NULL,"
        + " session_version int NOT NULL DEFAULT 0,"
        + " PRIMARY KEY (id),"
        + " UNIQUE KEY selector (selector),"
        + " KEY login_id (login_id),"
        + " KEY expires (expires)"
        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    public RememberTokenDb(Connection connection) {
        super(connection);
    }

    /** Create remember_token if missing and drop expired rows. Safe to run repeatedly. */
    public void ensureSchema() throws SQLException {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate(CREATE_TABLE);
            stmt.executeUpdate("delete from remember_token where expires < now()");
        }
    }

    /** A row joined with its login's current state. Times are computed by the database. */
    public static final class Token {
        public int id;
        public int loginId;
        public String tokenHash;
        public String prevHash;
        public long remainingSecs;      // until expires; <= 0 means expired
        public long secsSinceRotated;   // -1 if never rotated
        public int sessionVersion;      // when the token was made
        public boolean loginExists;
        public int loginSessionVersion; // the login's current value
        public boolean mustReset;
    }

    public Token findBySelector(String selector) throws SQLException {
        String q = "select t.id, t.login_id, t.token_hash, t.prev_hash, t.session_version,"
            + " timestampdiff(SECOND, now(), t.expires) as remaining,"
            + " ifnull(timestampdiff(SECOND, t.rotated, now()), -1) as since_rotated,"
            + " l.id as lid, l.session_version as l_version, l.must_reset"
            + " from remember_token t left join login l on l.id = t.login_id"
            + " where t.selector = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            stmt.setString(1, selector);
            try (ResultSet rset = stmt.executeQuery()) {
                if (!rset.next()) return null;
                Token t = new Token();
                t.id = rset.getInt("id");
                t.loginId = rset.getInt("login_id");
                t.tokenHash = rset.getString("token_hash");
                t.prevHash = rset.getString("prev_hash");
                t.sessionVersion = rset.getInt("session_version");
                t.remainingSecs = rset.getLong("remaining");
                t.secsSinceRotated = rset.getLong("since_rotated");
                rset.getInt("lid");
                t.loginExists = !rset.wasNull();
                t.loginSessionVersion = rset.getInt("l_version");
                t.mustReset = rset.getInt("must_reset") != 0;
                return t;
            }
        }
    }

    /** Store a new token, then keep only the newest MAX_PER_LOGIN unexpired tokens of the login. */
    public void insert(int loginId, String selector, String tokenHash, int validDays, String userAgent, int sessionVersion) throws SQLException {
        String q = "insert into remember_token (login_id, selector, token_hash, created, expires, last_used, user_agent, session_version)"
            + " values (?, ?, ?, now(), now() + interval ? day, now(), ?, ?)";
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            stmt.setInt(1, loginId);
            stmt.setString(2, selector);
            stmt.setString(3, tokenHash);
            stmt.setInt(4, validDays);
            stmt.setString(5, truncate(userAgent, 255));
            stmt.setInt(6, sessionVersion);
            stmt.executeUpdate();
        }
        try (PreparedStatement stmt = getConnection().prepareStatement(
                "delete from remember_token where login_id = ? and expires < now()")) {
            stmt.setInt(1, loginId);
            stmt.executeUpdate();
        }
        String trim = "delete from remember_token where login_id = ? and id not in"
            + " (select id from (select id from remember_token where login_id = ? order by id desc limit " + MAX_PER_LOGIN + ") newest)";
        try (PreparedStatement stmt = getConnection().prepareStatement(trim)) {
            stmt.setInt(1, loginId);
            stmt.setInt(2, loginId);
            stmt.executeUpdate();
        }
    }

    /**
     * Replace the validator hash (keeping the old one as prev_hash for a short grace time).
     * False if another request rotated it first (the row no longer has oldHash).
     */
    public boolean rotate(int id, String oldHash, String newHash, String userAgent) throws SQLException {
        String q = "update remember_token set prev_hash = token_hash, token_hash = ?, rotated = now(), last_used = now(), user_agent = ?"
            + " where id = ? and token_hash = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            stmt.setString(1, newHash);
            stmt.setString(2, truncate(userAgent, 255));
            stmt.setInt(3, id);
            stmt.setString(4, oldHash);
            return stmt.executeUpdate() == 1;
        }
    }

    public void touch(int id) throws SQLException {
        try (PreparedStatement stmt = getConnection().prepareStatement("update remember_token set last_used = now() where id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        try (PreparedStatement stmt = getConnection().prepareStatement("delete from remember_token where id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    public void deleteBySelector(String selector) throws SQLException {
        try (PreparedStatement stmt = getConnection().prepareStatement("delete from remember_token where selector = ?")) {
            stmt.setString(1, selector);
            stmt.executeUpdate();
        }
    }

    /** Forget every remembered browser of the login. */
    public int deleteForLogin(int loginId) throws SQLException {
        try (PreparedStatement stmt = getConnection().prepareStatement("delete from remember_token where login_id = ?")) {
            stmt.setInt(1, loginId);
            return stmt.executeUpdate();
        }
    }

    /** Like deleteForLogin(), but never fails (e.g. the table is not created yet). */
    public static void deleteForLoginQuietly(Connection connection, int loginId) {
        try {
            new RememberTokenDb(connection).deleteForLogin(loginId);
        } catch (SQLException e) {
            s_log.warn("deleteForLoginQuietly() login:" + loginId + " e:" + e);
        }
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }
}
