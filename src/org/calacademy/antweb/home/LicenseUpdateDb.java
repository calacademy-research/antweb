package org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.License;

/**
 * One-time update of the stored licence text to CC BY 4.0 (Brian Fisher, 2026-10-29: images and data on AntWeb are
 * CC BY 4.0, everywhere and for all CAS images; the stored texts of earlier uploads are updated too).
 *
 * Two places hold a licence text: image_upload.license (one row per image upload; before 2026-10-29 every upload
 * recorded the old Creative Commons ShareAlike plus GNU documentation licence text) and
 * the old license table (id, license; image.license = 1 points at it). Both are set to {@link License#LINE}.
 *
 * Run once at every application start (LoginMgr, with the other ensureSchema steps). First a backup of the rows it
 * changes: image_upload_license_bak_20261029 (id, license of the rows not yet CC BY 4.0) and license_bak_20261029
 * (a full copy of license). CREATE TABLE IF NOT EXISTS ... SELECT copies nothing when the table exists (MySQL 8), so
 * the backup is taken on the first run only, before anything changed. Then the UPDATEs, limited to rows whose text is
 * not already {@link License#LINE}: a second run changes nothing and logs nothing.
 * Record of the same SQL: db/upgrade/8.115/2026-10-29-license.sql (with the rollback).
 */
public final class LicenseUpdateDb {

    private static final Log s_log = LogFactory.getLog(LicenseUpdateDb.class);

    public static final String UPLOAD_BACKUP = "image_upload_license_bak_20261029";
    public static final String LICENSE_BACKUP = "license_bak_20261029";

    static final String BACKUP_UPLOADS = "CREATE TABLE IF NOT EXISTS " + UPLOAD_BACKUP
        + " AS SELECT id, license FROM image_upload WHERE license IS NOT NULL AND license <> ?";
    static final String BACKUP_LICENSE = "CREATE TABLE IF NOT EXISTS " + LICENSE_BACKUP + " AS SELECT * FROM license";
    static final String UPDATE_UPLOADS = "UPDATE image_upload SET license = ? WHERE license IS NOT NULL AND license <> ?";
    static final String UPDATE_LICENSE = "UPDATE license SET license = ? WHERE license IS NULL OR license <> ?";

    private LicenseUpdateDb() {
    }

    /** Counts of one run. */
    public static final class Result {
        public int uploads;
        public int licenses;
    }

    /** Back up, then update; a table that does not exist is skipped. A failed backup throws before its UPDATE. */
    public static Result run(Connection c) throws SQLException {
        Result r = new Result();
        if (tableExists(c, "image_upload")) {
            exec(c, BACKUP_UPLOADS, License.LINE);
            r.uploads = exec(c, UPDATE_UPLOADS, License.LINE, License.LINE);
        }
        if (tableExists(c, "license")) {
            exec(c, BACKUP_LICENSE);
            r.licenses = exec(c, UPDATE_LICENSE, License.LINE, License.LINE);
        }
        if (r.uploads > 0 || r.licenses > 0) {
            s_log.warn("run() licence set to " + License.NAME + ": image_upload rows " + r.uploads + ", license rows "
                + r.licenses + " (backups " + UPLOAD_BACKUP + ", " + LICENSE_BACKUP + ")");
        }
        return r;
    }

    static boolean tableExists(Connection c, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM information_schema.tables"
                + " WHERE table_schema = DATABASE() AND table_name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int exec(Connection c, String sql, String... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            return ps.executeUpdate();
        }
    }
}
