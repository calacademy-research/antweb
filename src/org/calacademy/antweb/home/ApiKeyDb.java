package org.calacademy.antweb.home;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.ApiKeys;

/**
 * Database side of the free API keys (rules in util/ApiKeys; pages ApiKeyAction, ApiKeyConfirmAction and
 * curate/ApiKeysAction).
 *
 *   api_key           one row per registration; only the SHA-256 of the key and of the email confirmation token
 *   api_usage_daily   requests, records and refusals (HTTP 429) per key and day, written by the API (PR 3);
 *                     key_id 0 = every request without a key
 *
 * ensureSchema() creates both tables when missing (once at startup from LoginMgr), with the same DDL as
 * db/upgrade/9.4.0/2026-11-08-api-keys.sql (tools/api/setup-usage-login.sh runs that file and adds the API's own
 * database login). Usage counts are read for the last 30 days.
 *
 * Times are UTC throughout (one rule for the web application and the API): the API writes api_usage_daily.day and
 * last_used in UTC, so this class writes created, confirmed, confirm_expires and revoked with utc_timestamp(), compares
 * with utc_timestamp() and utc_date() (not the server's local clock, which is Pacific), and the admin page labels the
 * times "(UTC)". The column default of created (current_timestamp) is never used: every insert sets it.
 * Tests: test/org/calacademy/antweb/home/ApiKeyDbTest.java (MySQL 8.4 in CI).
 */
public class ApiKeyDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(ApiKeyDb.class);

    public static final String CREATE_API_KEY = "create table if not exists api_key ("
        + " id int not null auto_increment,"
        + " prefix varchar(16) not null,"
        + " key_hash char(64) not null,"
        + " name varchar(200) not null,"
        + " email varchar(254) not null,"
        + " institution varchar(300) null,"
        + " project text not null,"
        + " level varchar(16) not null default 'registered',"
        + " status varchar(16) not null default 'pending',"
        + " per_minute int null,"
        + " per_day int null,"
        + " max_records int null,"
        + " records_per_day int null,"
        + " confirm_token_hash char(64) null,"
        + " confirm_expires datetime null,"
        + " created datetime not null default current_timestamp,"
        + " confirmed datetime null,"
        + " last_used datetime null,"
        + " revoked datetime null,"
        + " note text null,"
        + " primary key (id),"
        + " unique key api_key_hash (key_hash),"
        + " unique key api_key_prefix (prefix),"
        + " key api_key_email (email)"
        + ")";

    public static final String CREATE_API_USAGE_DAILY = "create table if not exists api_usage_daily ("
        + " day date not null,"
        + " key_id int not null,"
        + " requests int not null default 0,"
        + " records int not null default 0,"
        + " limited int not null default 0,"
        + " primary key (day, key_id)"
        + ")";

    public ApiKeyDb(Connection connection) {
        super(connection);
    }

    /** Create api_key and api_usage_daily if they are missing. Safe to run repeatedly. */
    public void ensureSchema() throws SQLException {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate(CREATE_API_KEY);
            stmt.executeUpdate(CREATE_API_USAGE_DAILY);
        }
    }

    // ---------------------------------------------------------------- registration

    /** Registrations made with this email address in the last 24 hours (any status). */
    public int countByEmailLastDay(String email) throws SQLException {
        return count("select count(*) from api_key where email = ? and created > utc_timestamp() - interval 1 day", email);
    }

    /** Registrations made in the last 24 hours, by anyone, whose email link was followed (any status since). */
    public int countConfirmedLastDay() throws SQLException {
        return count("select count(*) from api_key where confirmed is not null and created > utc_timestamp() - interval 1 day", null);
    }

    /** Registrations made in the last 24 hours, by anyone, still pending (link not followed yet, or expired). */
    public int countPendingLastDay() throws SQLException {
        return count("select count(*) from api_key where status = 'pending' and created > utc_timestamp() - interval 1 day", null);
    }

    private int count(String q, String param) throws SQLException {
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            if (param != null) stmt.setString(1, param);
            try (ResultSet rset = stmt.executeQuery()) {
                return rset.next() ? rset.getInt(1) : 0;
            }
        }
    }

    /**
     * Save a registration as 'pending', with the SHA-256 of its confirmation token, valid for hours. The key does not
     * exist yet: key_hash and prefix get stand-ins (ApiKeys.placeholderPrefix/Hash) until the link is followed.
     * Returns the new id.
     */
    public int insertPending(ApiKeys.Registration r, String tokenHash, int hours) throws SQLException {
        String q = "insert into api_key (prefix, key_hash, name, email, institution, project, level, status,"
            + " confirm_token_hash, confirm_expires, created)"
            + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, utc_timestamp() + interval ? hour, utc_timestamp())";
        try (PreparedStatement stmt = getConnection().prepareStatement(q, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, ApiKeys.placeholderPrefix());
            stmt.setString(2, ApiKeys.placeholderHash());
            stmt.setString(3, r.name);
            stmt.setString(4, r.email);
            stmt.setString(5, r.institution.isEmpty() ? null : r.institution);
            stmt.setString(6, r.project);
            stmt.setString(7, ApiKeys.REGISTERED);
            stmt.setString(8, ApiKeys.PENDING);
            stmt.setString(9, tokenHash);
            stmt.setInt(10, hours);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    /** The registration whose confirmation token has this SHA-256, or null. */
    public Row findByTokenHash(String tokenHash) throws SQLException {
        List<Row> rows = select("where k.confirm_token_hash = ?", tokenHash);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Activate a pending registration whose link is still valid: store the key's SHA-256 and prefix, status 'active',
     * confirmed now. True only for the one request that activated it. A duplicate prefix (two keys starting with
     * the same 7 characters) throws SQLIntegrityConstraintViolationException; the caller makes a new key.
     */
    public boolean activate(int id, String tokenHash, String keyHash, String prefix) throws SQLException {
        String q = "update api_key set key_hash = ?, prefix = ?, status = ?, confirmed = utc_timestamp()"
            + " where id = ? and status = ? and confirm_token_hash = ? and confirm_expires > utc_timestamp()";
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            stmt.setString(1, keyHash);
            stmt.setString(2, prefix);
            stmt.setString(3, ApiKeys.ACTIVE);
            stmt.setInt(4, id);
            stmt.setString(5, ApiKeys.PENDING);
            stmt.setString(6, tokenHash);
            return stmt.executeUpdate() == 1;
        }
    }

    // ---------------------------------------------------------------- admin page

    /** What the admin page filters on; null or "" = all. */
    public static final class Filter {
        public String status;
        public String level;
        public String search;
    }

    /** Every key matching the filter, newest first, with its use in the last 30 days. */
    public List<Row> list(Filter f) throws SQLException {
        StringBuilder where = new StringBuilder("where 1 = 1");
        List<String> params = new ArrayList<>();
        if (f != null && f.status != null && !f.status.isEmpty()) { where.append(" and k.status = ?"); params.add(f.status); }
        if (f != null && f.level != null && !f.level.isEmpty()) { where.append(" and k.level = ?"); params.add(f.level); }
        if (f != null && f.search != null && !f.search.trim().isEmpty()) {
            where.append(" and (k.name like ? or k.email like ? or k.institution like ?)");
            String like = "%" + f.search.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            params.add(like); params.add(like); params.add(like);
        }
        return select(where + " order by k.created desc, k.id desc", params.toArray(new String[0]));
    }

    public Row get(int id) throws SQLException {
        List<Row> rows = select("where k.id = " + id);  // an int, never text from the request
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static final String USAGE = "(select key_id,"
        + " sum(case when day = utc_date() then requests else 0 end) as today,"
        + " sum(case when day >= utc_date() - interval 6 day then requests else 0 end) as d7,"
        + " sum(requests) as d30, sum(records) as r30, sum(limited) as l30"
        + " from api_usage_daily where day >= utc_date() - interval 29 day group by key_id)";

    private List<Row> select(String whereOrderBy, String... params) throws SQLException {
        String q = "select k.*, coalesce(u.today, 0) as u_today, coalesce(u.d7, 0) as u_d7, coalesce(u.d30, 0) as u_d30,"
            + " coalesce(u.r30, 0) as u_r30, coalesce(u.l30, 0) as u_l30, utc_timestamp() as db_now"
            + " from api_key k left join " + USAGE + " u on u.key_id = k.id " + whereOrderBy;
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            for (int i = 0; i < params.length; i++) stmt.setString(i + 1, params[i]);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    Row k = new Row();
                    k.id = r.getInt("id");
                    k.prefix = r.getString("prefix");
                    k.name = r.getString("name");
                    k.email = r.getString("email");
                    k.institution = r.getString("institution");
                    k.project = r.getString("project");
                    k.level = r.getString("level");
                    k.status = r.getString("status");
                    k.perMinute = intOrNull(r, "per_minute");
                    k.perDay = intOrNull(r, "per_day");
                    k.maxRecords = intOrNull(r, "max_records");
                    k.recordsPerDay = intOrNull(r, "records_per_day");
                    k.confirmExpires = time(r, "confirm_expires");
                    k.created = time(r, "created");
                    k.confirmed = time(r, "confirmed");
                    k.lastUsed = time(r, "last_used");
                    k.revoked = time(r, "revoked");
                    k.note = r.getString("note");
                    k.dbNow = time(r, "db_now");
                    k.usage = usage(r);
                    rows.add(k);
                }
            }
        }
        return rows;
    }

    /** Use without a key (key_id 0) in the last 30 days. */
    public Usage noKeyUsage() throws SQLException {
        String q = "select coalesce(u.today, 0) as u_today, coalesce(u.d7, 0) as u_d7, coalesce(u.d30, 0) as u_d30,"
            + " coalesce(u.r30, 0) as u_r30, coalesce(u.l30, 0) as u_l30 from (select 1) one left join " + USAGE
            + " u on u.key_id = 0";
        try (Statement stmt = getConnection().createStatement(); ResultSet r = stmt.executeQuery(q)) {
            return r.next() ? usage(r) : new Usage();
        }
    }

    private static Usage usage(ResultSet r) throws SQLException {
        Usage u = new Usage();
        u.today = r.getLong("u_today");
        u.last7 = r.getLong("u_d7");
        u.last30 = r.getLong("u_d30");
        u.records30 = r.getLong("u_r30");
        u.refused30 = r.getLong("u_l30");
        return u;
    }

    private static Integer intOrNull(ResultSet r, String col) throws SQLException {
        int v = r.getInt(col);
        return r.wasNull() ? null : v;
    }

    private static LocalDateTime time(ResultSet r, String col) throws SQLException {
        Timestamp t = r.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime();
    }

    /** Revoke: the key stops working (PR 3 accepts only 'active'). True if changed. */
    public boolean revoke(int id) throws SQLException {
        return update("update api_key set status = 'revoked', revoked = utc_timestamp() where id = ? and status <> 'revoked'", id);
    }

    /** Reactivate a revoked key that had been confirmed. True if changed. */
    public boolean reactivate(int id) throws SQLException {
        return update("update api_key set status = 'active', revoked = null"
            + " where id = ? and status = 'revoked' and confirmed is not null", id);
    }

    public boolean setLevel(int id, String level) throws SQLException {
        if (!ApiKeys.isLevel(level)) throw new IllegalArgumentException("unknown level");
        try (PreparedStatement stmt = getConnection().prepareStatement("update api_key set level = ? where id = ?")) {
            stmt.setString(1, level);
            stmt.setInt(2, id);
            return stmt.executeUpdate() == 1;
        }
    }

    /** The four limits of a key; null = the level's default. */
    public boolean setLimits(int id, Integer perMinute, Integer perDay, Integer maxRecords, Integer recordsPerDay) throws SQLException {
        String q = "update api_key set per_minute = ?, per_day = ?, max_records = ?, records_per_day = ? where id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            setInt(stmt, 1, perMinute);
            setInt(stmt, 2, perDay);
            setInt(stmt, 3, maxRecords);
            setInt(stmt, 4, recordsPerDay);
            stmt.setInt(5, id);
            return stmt.executeUpdate() == 1;
        }
    }

    public boolean setNote(int id, String note) throws SQLException {
        try (PreparedStatement stmt = getConnection().prepareStatement("update api_key set note = ? where id = ?")) {
            stmt.setString(1, note == null || note.isEmpty() ? null : note);
            stmt.setInt(2, id);
            return stmt.executeUpdate() == 1;
        }
    }

    private static void setInt(PreparedStatement stmt, int i, Integer v) throws SQLException {
        if (v == null) stmt.setNull(i, Types.INTEGER); else stmt.setInt(i, v);
    }

    private boolean update(String q, int id) throws SQLException {
        try (PreparedStatement stmt = getConnection().prepareStatement(q)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() == 1;
        }
    }

    // ---------------------------------------------------------------- rows

    /** Use in the last 30 days (api_usage_daily). */
    public static final class Usage {
        public long today, last7, last30, records30, refused30;
    }

    /** One api_key row (never the key; key_hash and confirm_token_hash are not kept here). */
    public static final class Row {
        public int id;
        public String prefix, name, email, institution, project, level, status, note;
        public Integer perMinute, perDay, maxRecords, recordsPerDay;
        public LocalDateTime confirmExpires, created, confirmed, lastUsed, revoked;
        /** The database's clock (UTC) when the row was read (confirm_expires is compared with it, not with Java's). */
        public LocalDateTime dbNow;
        public Usage usage = new Usage();

        /** The prefix to show: none while pending (the stored value is only a stand-in). */
        public String shownPrefix() {
            return ApiKeys.PENDING.equals(status) || prefix == null || !prefix.startsWith(ApiKeys.KEY_START) ? "" : prefix;
        }
    }
}
