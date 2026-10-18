package org.calacademy.antweb.home;

import java.sql.*;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Files of an image upload that were not saved, and why (shown under "Not saved" in the upload
 * report, imageUploadReport.do, and counted in the upload-complete email).
 *
 *   image_upload_refused   one row per file name per upload. Written by
 *                          ImageUploadBatchAction: names the server refused (bad name, unknown
 *                          specimen) and names the Image Uploader page refused or could not send
 *                          (selected twice, too large, empty, failed after retries), which the
 *                          page sends with the start and finish steps. The page's names and
 *                          reasons are only text to show: they are cleaned and limited here and
 *                          escaped when shown.
 *
 * A file name is recorded once per upload (the first reason is kept). At most MAX_PER_UPLOAD
 * rows per upload.
 *
 * ensureSchema() creates the table when missing (once at startup from LoginMgr, and again
 * before the first write if that has not run). Same DDL: db/upgrade/8.111/2026-10-18-image-upload-refused.sql.
 */
public class ImageUploadRefusedDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(ImageUploadRefusedDb.class);

    public static final int MAX_NAME = 255;
    public static final int MAX_REASON = 300;
    public static final int MAX_PER_UPLOAD = 2000;

    public static final String SERVER = "server";
    public static final String BROWSER = "browser";

    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS image_upload_refused ("
        + " id int NOT NULL AUTO_INCREMENT,"
        + " image_upload_id int NOT NULL,"
        + " filename varchar(255) NOT NULL,"
        + " reason varchar(300) NOT NULL,"
        + " source varchar(10) NOT NULL,"
        + " created datetime NOT NULL,"
        + " PRIMARY KEY (id),"
        + " UNIQUE KEY upload_file (image_upload_id, filename)"
        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    /** A file that was not saved. */
    public static final class Refused {
        public final String fileName;
        public final String reason;
        public final String source;   // SERVER or BROWSER
        public java.util.Date created;

        public Refused(String fileName, String reason, String source) {
            this.fileName = fileName;
            this.reason = reason;
            this.source = source;
        }

        public String getFileName() { return fileName; }
        public String getReason() { return reason; }
        public String getSource() { return source; }
        public java.util.Date getCreated() { return created; }
    }

    public ImageUploadRefusedDb(Connection connection) {
        super(connection);
    }

    private static volatile boolean s_schemaChecked = false;

    /** Create image_upload_refused if missing. Safe to run repeatedly. */
    public void ensureSchema() throws SQLException {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate(CREATE_TABLE);
        }
        s_schemaChecked = true;
    }

    private void ensureSchemaOnce() throws SQLException {
        if (!s_schemaChecked) ensureSchema();
    }

    // ---------- cleaning what the page sends ----------

    /** A file name as stored: no folder part, no control characters, at most MAX_NAME characters. Null if nothing is left. */
    public static String cleanName(String name) {
        if (name == null) return null;
        String n = name.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        if (slash >= 0) n = n.substring(slash + 1);
        n = n.replaceAll("[\\p{Cntrl}\\p{Cf}]", "").trim();
        if (n.isEmpty()) return null;
        return n.length() > MAX_NAME ? n.substring(0, MAX_NAME) : n;
    }

    /** A reason as stored: one line, no control characters, at most MAX_REASON characters. */
    public static String cleanReason(String reason) {
        String r = reason == null ? "" : reason.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").replaceAll("\\s+", " ").trim();
        if (r.isEmpty()) r = "not sent";
        return r.length() > MAX_REASON ? r.substring(0, MAX_REASON - 3) + "..." : r;
    }

    /**
     * The page's list: one file per line, "name<TAB>reason". Lines without a name are skipped,
     * a name listed twice is kept once, and at most MAX_PER_UPLOAD entries are read.
     */
    public static List<Refused> parse(String param, String source) {
        List<Refused> out = new ArrayList<>();
        if (param == null || param.isEmpty()) return out;
        Set<String> seen = new HashSet<>();
        int lines = 0;
        for (String line : param.split("\n", MAX_PER_UPLOAD * 2 + 1)) {
            if (++lines > MAX_PER_UPLOAD * 2 || out.size() >= MAX_PER_UPLOAD) break;
            int tab = line.indexOf('\t');
            String name = cleanName(tab >= 0 ? line.substring(0, tab) : line);
            if (name == null || !seen.add(name)) continue;
            out.add(new Refused(name, cleanReason(tab >= 0 ? line.substring(tab + 1) : null), source));
        }
        return out;
    }

    // ---------- database ----------

    /**
     * Record files that were not saved. Names already recorded for this upload, and names in
     * skipNames (files that were saved), are left out. Returns the number of rows added.
     */
    public int record(int imageUploadId, Collection<Refused> files, Set<String> skipNames) throws SQLException {
        if (imageUploadId <= 0 || files == null || files.isEmpty()) return 0;
        ensureSchemaOnce();
        int room = MAX_PER_UPLOAD - count(imageUploadId);
        if (room <= 0) return 0;
        int added = 0;
        String dml = "insert ignore into image_upload_refused (image_upload_id, filename, reason, source, created)"
            + " values (?, ?, ?, ?, now())";
        try (PreparedStatement ps = getConnection().prepareStatement(dml)) {
            for (Refused r : files) {
                if (added >= room) break;
                String name = cleanName(r.fileName);
                if (name == null || (skipNames != null && skipNames.contains(name))) continue;
                ps.setInt(1, imageUploadId);
                ps.setString(2, name);
                ps.setString(3, cleanReason(r.reason));
                ps.setString(4, SERVER.equals(r.source) ? SERVER : BROWSER);
                added += ps.executeUpdate();
            }
        }
        return added;
    }

    /** One file the server refused. */
    public int record(int imageUploadId, String fileName, String reason) throws SQLException {
        return record(imageUploadId, List.of(new Refused(fileName, reason, SERVER)), null);
    }

    /** Number of files recorded as not saved; 0 if the table does not exist yet. */
    public int count(int imageUploadId) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "select count(*) from image_upload_refused where image_upload_id = ?")) {
            ps.setInt(1, imageUploadId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            s_log.warn("count() id:" + imageUploadId + " e:" + e);
            return 0;
        }
    }

    /** The files not saved, in the order recorded; empty if none or the table does not exist yet. */
    public List<Refused> list(int imageUploadId) {
        List<Refused> out = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(
                "select filename, reason, source, created from image_upload_refused where image_upload_id = ? order by id")) {
            ps.setInt(1, imageUploadId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Refused r = new Refused(rs.getString("filename"), rs.getString("reason"), rs.getString("source"));
                    r.created = rs.getTimestamp("created");
                    out.add(r);
                }
            }
        } catch (SQLException e) {
            s_log.warn("list() id:" + imageUploadId + " e:" + e);
        }
        return out;
    }
}
