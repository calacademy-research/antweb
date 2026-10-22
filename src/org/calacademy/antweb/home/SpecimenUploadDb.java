package org.calacademy.antweb.home;


import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.upload.*;

import java.util.ArrayList;


public class SpecimenUploadDb extends UploadDb {

    private static final Log s_log = LogFactory.getLog(SpecimenUploadDb.class);
    private static final Log uploadLog = LogFactory.getLog("uploadLog");

    //ArrayList goodHeaders = new ArrayList(Arrays.asList(biotaHeaders));    

    public SpecimenUploadDb(Connection connection) {
      super(connection);
    }

    public void deleteSpecimens(ArrayList<String> specimens) throws SQLException {
        StringBuffer codeString = new StringBuffer();
        int count = 0;
        for (String code : specimens) {            
            if (code.length() > 0) {
              codeString.append(",'" + code.toLowerCase() + "'");
            }
        }
        if (codeString.length() > 0) {
            String query = "delete from specimen where code in (" + codeString.substring(1) + ")";
            Statement stmt = null; 

            try {
                stmt = DBUtil.getStatement(getConnection(), "SpecimenUploadDb.deleteSpecimens()");
                count = stmt.executeUpdate(query);
            } catch (SQLException e) {
                uploadLog.error("problem in deleteSpecimens(connect, specimens) - queryLength:" + query.length() + " " + e);
            } finally {
                DBUtil.close(stmt, "SpecimenUploadDb.deleteSpecimens()");
            }

            uploadLog.info("deleteSpecimens() count:" + count + " query:" + query);
        }   
    }
    
    public void updateSpecimenUploadDate(Group group) {
        
        int id = group.getId();
        String dml = "update ant_group set last_specimen_upload = now() where id = ?";
        PreparedStatement stmt = null;
        try {
            stmt = DBUtil.getPreparedStatement(getConnection(), "SpecimenUploadDb.uploadSpecimenUploadDate()", dml);
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            uploadLog.error("updateSpecimenUploadDate() e: " + e);
        } finally {
            DBUtil.close(stmt, "SpecimenUploadDb.uploadSpecimenUploadDate()");
        }        
    }
    
    void deleteSpecimen(String code) throws SQLException {
        PreparedStatement stmt = null;
        try {
            String dml = "delete from specimen where code = ?";
            stmt = DBUtil.getPreparedStatement(getConnection(), "SpecimenUploadDb.deleteSpecimen()", dml);
            stmt.setString(1, code);
            stmt.executeUpdate();
        } catch (SQLException e) {
            uploadLog.error("problem deleteSpecimen() code:" + code + " " + e);
        } finally {
            DBUtil.close(stmt, "SpecimenUploadDb.deleteSpecimen()");
        }
    }
    
    public void dropSpecimens(Group group) throws SQLException {
        int count;
        String query = "";
        PreparedStatement stmt = null;
        try {
          query = "delete from specimen where access_group = ?";
          //s_log.warn("dropSpecimens() why is this query so slow:" + query);                    
          stmt = DBUtil.getPreparedStatement(getConnection(), "SpecimenUploadDb.dropSpecimens()", query);
          stmt.setInt(1, group.getId());
          count = stmt.executeUpdate();

          LogMgr.appendLog("taxonSet.log", "specimenUploadDb.dropSpecimens(" + group + "):" + count, true);
          
          uploadLog.info("dropSpecimens() count:" + count + " query completed");
        } catch (SQLException e) {
          uploadLog.error("dropSpecimens() 1 e:" + e + " query:" + query);
        } finally {
          DBUtil.close(stmt, "SpecimenUploadDb.dropSpecimens()");
        }

        try {
          query = "delete from taxon where access_group = ? and insert_method in ('addMissingGenus', 'homononymMirroringTaxon')";
          //s_log.warn("dropSpecimens() query:" + query);                    
          stmt = DBUtil.getPreparedStatement(getConnection(), "SpecimenUploadDb.dropSpecimens2()", query);
          stmt.setInt(1, group.getId());
          count = stmt.executeUpdate();
          //s_log.warn("dropSpecimens() count:" + count + " query completed");
        } catch (SQLException e) {
          uploadLog.error("dropSpecimens() 2 e:" + e + " query:" + query);
        } finally {
          DBUtil.close(stmt, "SpecimenUploadDb.dropSpecimens2()");
        }
    }

    public void deleteTaxonOrphans() throws SQLException {
      // Retired.  This would just remove old Bolton records.  Does Bolton not clear in/out or reload?

        String query = "";
        Statement stmt = null;
        try {

          //s_log.warn("removeTaxonOrphan action suspended");
          if (true) return;

            stmt = DBUtil.getStatement(getConnection(), "SpecimenUploadDb.deleteTaxonOrphans()");
            query = "delete from taxon where "
                    + " taxon_name not in (select taxon_name from proj_taxon) and "
                    + " taxon_name not in (select taxon_name from specimen) and status = 'valid'";
            stmt.executeUpdate(query);

        } catch (SQLException e) {
            uploadLog.error("problem removeTaxonOrphans e:" + e + " query:" + query);
            throw e;
        } finally {
            DBUtil.close(stmt, "SpecimenUploadDb.deleteTaxonOrphans()");
        }
    }


    /** Width of the text columns of the upload table (group_name, log_file_name, backup_dir_file). */
    public static final int UPLOAD_TEXT_WIDTH = 64;

    /**
     * Records a specimen upload in the upload table (the Specimen Upload Reports list, each group's
     * last upload, the curator's uploads, groups.do, the count check) and fills its counts. Returns
     * the upload_id written, the one the upload's specimens carry (AntwebMgr.getNextSpecimenUploadId()).
     *
     * Until 2026-10-22 the row was written with a text-built statement whose SQLException was only
     * logged: when the insert failed (a group name with a quote, or longer than the column), the
     * upload still committed and said "Upload complete", without a row. The row is part of the upload:
     * a failure now throws, so the upload's transaction rolls back and the uploader is told.
     */
    public int recordUpload(Login accessLogin, UploadDetails uploadDetails) throws SQLException {
        Group accessGroup = accessLogin.getGroup();
        if (accessGroup == null) throw new SQLException("recordUpload() login " + accessLogin.getId() + " has no group");
        int uploadId = AntwebMgr.getNextSpecimenUploadId();
        insertUploadRow(getConnection(), uploadId, accessLogin.getId(), accessGroup.getId(), accessGroup.getName(),
                uploadDetails.getLogFileName(), uploadDetails.getBackupDirFile());
        uploadDetails.setUploadId(uploadId);
        updateCounts(accessGroup.getId());
        return uploadId;
    }

    /**
     * Inserts one upload row (bound values; texts cut to the column width) and checks it is there,
     * in the caller's transaction. Throws when the row could not be written.
     */
    public static void insertUploadRow(Connection connection, int uploadId, int loginId, int groupId, String groupName,
                                       String logFileName, String backupDirFile) throws SQLException {
        if (uploadId <= 0) throw new SQLException("insertUploadRow() no upload id (" + uploadId + ")");
        String insert = "insert into upload(upload_id, login_id, group_name, group_id, log_file_name, backup_dir_file)"
                + " values (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insert)) {
            stmt.setInt(1, uploadId);
            stmt.setInt(2, loginId);
            stmt.setString(3, fit(groupName == null ? "" : groupName));   // display only: cut to the column
            stmt.setInt(4, groupId);
            // File names are used for links (report, Download): never cut, refuse instead.
            stmt.setString(5, fileName("log_file_name", logFileName == null ? "" : logFileName));
            stmt.setString(6, fileName("backup_dir_file", backupDirFile));
            if (stmt.executeUpdate() != 1) throw new SQLException("insertUploadRow() no row written for upload " + uploadId);
        } catch (SQLException e) {
            s_log.error("insertUploadRow() upload_id:" + uploadId + " group:" + groupId + " logFileName:" + logFileName + " e:" + e);
            throw e;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "select count(*) from upload where upload_id = ? and group_id = ?")) {
            stmt.setInt(1, uploadId);
            stmt.setInt(2, groupId);
            try (ResultSet rset = stmt.executeQuery()) {
                if (!rset.next() || rset.getInt(1) < 1) {
                    throw new SQLException("insertUploadRow() upload " + uploadId + " of group " + groupId + " not found after the insert");
                }
            }
        }
    }

    /** A file name for the upload table: refused (SQLException) when longer than the column, never cut. */
    static String fileName(String column, String name) throws SQLException {
        if (name != null && name.length() > UPLOAD_TEXT_WIDTH) {
            throw new SQLException("insertUploadRow() " + column + " longer than " + UPLOAD_TEXT_WIDTH + " characters: " + name);
        }
        return name;
    }

    /**
     * s as the upload table's text columns hold it (null stays null): cut to the column width, and
     * characters outside the Basic Multilingual Plane (which a utf8mb3 column refuses) as "?".
     */
    public static String fit(String s) {
        if (s == null) return null;
        StringBuilder b = new StringBuilder();
        s.codePoints().limit(UPLOAD_TEXT_WIDTH).forEach(c -> {
            if (Character.isSupplementaryCodePoint(c)) b.append('?'); else b.appendCodePoint(c);
        });
        return b.toString();
    }

    
    /*
    /web/upload/20220224-11:49:08-specimen2.txt

Was looking in the working dir. That file does not exist:
 /usr/local/antweb/workingDir/specimen2.txt 

It has been archived to here:
/web/upload/20220224-11:49:08-specimen2.txt

/data/antweb/web/upload/20220224-11:49:08-specimen2.txt
    */
    
/*
        // I think this would work during upload, but not during reload.
        // During reload: https://www.antweb.org/query.do?action=curiousQuery&name=lastSpecimenUpload
        // Look here: /web/upload/20220224-11:49:08-specimen2.txt
        // Full path: /data/antweb/web/upload/
 */

    public String getBackupDirFile(int accessGroup) {
        String backupDirFile = null;
        // Called from here: https://localhost/query.do?action=curiousQuery&name=lastSpecimenUpload

        Statement stmt = null;
        ResultSet rset = null;
        String query = "select backup_dir_file from upload where group_id = " + accessGroup + " order by created desc limit 1";
        try {
            stmt = DBUtil.getStatement(getConnection(), "getBackupDirFile()");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                backupDirFile = rset.getString("backup_dir_file");
            }
        } catch (SQLException e) {
            s_log.warn("getBackupDirFile() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "getBackupDirFile()");
        }

        if (backupDirFile == null || "".equals(backupDirFile)) {
            return null;
        }

        A.log("getBackupDirFile() backupDirFile:" + backupDirFile);
        return backupDirFile;
    }
}

