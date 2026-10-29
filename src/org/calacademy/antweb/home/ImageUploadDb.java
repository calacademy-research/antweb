package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;
import java.time.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.imageUploader.*;

/**
 * Image upload batches of the Image Uploader (imageUploadBatch.do): image_upload (one row per batch: curator,
 * group, artist, license, copyright year, image count, completion) and image_uploaded (one row per file and
 * its state, so a cut-off batch can be resumed). Read by the upload report (ImageUploadReport) and the list of
 * image uploads (listImageUploads.do). Tables image_upload, image_uploaded. Docs: docs/flows/image-upload.md.
 * Tests: tests/e2e/test_image_upload.py.
 */
public class ImageUploadDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(ImageUploadDb.class);

    public ImageUploadDb(Connection connection) {
      super(connection);
    }


    /** The upload with its files, or null if there is no such upload. A database error is thrown (it is not "not found"). */
    public ImageUpload getImageUpload(int id) throws SQLException {
        ImageUpload imageUpload = null;

        Statement stmt = null;
        ResultSet rset = null;
        String theQuery = "select * from image_upload where id = " + id;
        try {            
            stmt = DBUtil.getStatement(getConnection(), "getImageUpload()");
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                imageUpload = new ImageUpload();
                imageUpload.setId(rset.getInt("id"));
                imageUpload.setCuratorId(rset.getInt("curator_id"));
                imageUpload.setGroupId(rset.getInt("group_id"));
                imageUpload.setCreated(rset.getTimestamp("created"));
                imageUpload.setArtistId(rset.getInt("artist_id"));
                imageUpload.setImageCount(rset.getInt("image_count"));
                
                int year = rset.getInt("copyright_year");
                Copyright copyright = new CopyrightDb(getConnection()).getCopyrightByYear(year);
                imageUpload.setCopyright(copyright);
                imageUpload.setLicense(rset.getString("license"));
                imageUpload.setIsComplete(rset.getInt("complete") == 1);
                imageUpload.setImages(getImagesUploaded(imageUpload));
            }
        } catch (SQLException e) {
            s_log.error("getImageUpload() exception:" + e + " theQuery:" + theQuery);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "getImageUpload()");
        }
        return imageUpload;
    }


    public ArrayList<ImageUploaded> getImagesUploaded(ImageUpload imageUpload) throws SQLException {
        ArrayList<ImageUploaded> images = new ArrayList<>();
        Statement stmt = null;
        ResultSet rset = null;
        String theQuery = "select * from image_uploaded where image_upload_id = " + imageUpload.getId();
        try {            
            stmt = DBUtil.getStatement(getConnection(), "getImagesUploaded()");
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                ImageUploaded imageUploaded = new ImageUploaded();
                imageUploaded.setId(rset.getInt("id"));
                imageUploaded.setFileName(rset.getString("filename"));
                imageUploaded.setCode(rset.getString("code"));
                imageUploaded.setNumber(rset.getInt("number"));
                imageUploaded.setImageUpload(imageUpload);
                imageUploaded.setCreated(rset.getTimestamp("created"));
                imageUploaded.setShot(rset.getString("shot"));
                imageUploaded.setExt(rset.getString("ext"));
                imageUploaded.setIsReUploaded(rset.getInt("reuploaded") == 1);
                imageUploaded.setIsSpecimenDataExists(rset.getInt("specimen_data_exists") == 1);
                imageUploaded.setErrorMessage(rset.getString("error_message"));
                images.add(imageUploaded);
            }
        } catch (SQLException e) {
            s_log.error("getImagesUploaded() exception:" + e + " theQuery:" + theQuery);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "getImagesUploaded()");
        }
      
      return images;
    }    
    
    public ArrayList<ImageUpload> getImageUploads(String criteria) {
        ArrayList<ImageUpload> imageUploads = new ArrayList<>();
        Statement stmt = null;
        String query = null;
        ResultSet rset = null;
        try {
            //s_log.warn("execute groupId:" + groupId);

            stmt = DBUtil.getStatement(getConnection(), "getImageUploads()");
            
            query = "select * from image_upload";
            query += " " + criteria;
            query += " order by created desc";

            //A.log("getImageUploads() query:" + query);
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                ImageUpload imageUpload = new ImageUpload();
                imageUpload.setId(rset.getInt("id"));
                imageUpload.setCuratorId(rset.getInt("curator_id"));
                imageUpload.setGroupId(rset.getInt("group_id"));
                imageUpload.setCreated(rset.getTimestamp("created"));
                imageUpload.setImageCount(rset.getInt("image_count"));
                imageUpload.setArtistId(rset.getInt("artist_id"));
                imageUpload.setLicense(rset.getString("license"));
                int year = rset.getInt("copyright_year");
                Copyright copyright = new CopyrightDb(getConnection()).getCopyrightByYear(year);
                imageUpload.setCopyright(copyright);
                imageUpload.setIsComplete(rset.getInt("complete") == 1);
                imageUploads.add(imageUpload);
            }

        } catch (SQLException e) {
            s_log.error("getImageUploads() e:" + e + " theQuery:" + query);
        } finally {
            DBUtil.close(stmt, rset, this, "getImageUploads()");
        }            
        return imageUploads;               
    }

    public ImageUpload saveImageUpload(ImageUpload imageUpload) throws SQLException {
    
        Statement stmt = null;
        String dml = null;
    	try {
            stmt = DBUtil.getStatement(getConnection(), "saveImageUpload()");
  
            ImageUpload savedImageUpload = getImageUpload(imageUpload.getId());
            if (savedImageUpload == null) {

                dml = "insert into image_upload (curator_id, group_id, created, artist_id, image_count, license, copyright_year) values(?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = getConnection().prepareStatement(dml, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, imageUpload.getCuratorId());
                    ps.setInt(2, imageUpload.getGroupId());
                    ps.setTimestamp(3, new Timestamp(imageUpload.getCreated().getTime()));
                    ps.setInt(4, imageUpload.getArtistId());
                    ps.setInt(5, imageUpload.getImageCount());
                    ps.setString(6, imageUpload.getLicense());
                    ps.setInt(7, Year.now().getValue()); //imageUpload.getCopyright());

                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            imageUpload.setId(rs.getInt(1));
                        }
                    }
                }

            } else {
              s_log.warn("NOT IMPLEMENTED. Error.");
            }       

            //A.log("saveImageUpload() dml:" + dml);
		} catch (SQLException e) {
			s_log.error("saveImageUpload() dml" + dml + " e:" + e);
			throw e;
		} finally { 		
			DBUtil.close(stmt, "saveImageUpload()");
		}
		return imageUpload; // May have updated ID.
	}

    /**
     * Mark the upload complete. True only for the call that completed it: two finish requests at once (the
     * page sent it twice) must not both send the completion email.
     */
    public boolean completeImageUpload(ImageUpload imageUpload) throws SQLException {    
        Statement stmt = null;
        String dml = null;
    	try {
            stmt = DBUtil.getStatement(getConnection(), "completeImageUpload()");
            
            dml = "update image_upload set " 
                + "  complete = 1"
                + " where id = " + imageUpload.getId()
                + " and (complete is null or complete != 1)";
     
            return stmt.executeUpdate(dml) > 0;

            //A.log("completeImageUpload() dml:" + dml);
		} catch (SQLException e) {
			s_log.error("completeImageUpload() dml" + dml + " e:" + e);
			throw e;
		} finally { 		
			DBUtil.close(stmt, "completeImageUpload()");
		}
	}
	
    public void saveImageUploaded(ImageUploaded imageUploaded) throws SQLException {
        // A prepared statement: the file name comes from the curator's computer.
        String dml = "insert into image_uploaded (filename, code, shot, number, ext"
              + " , image_upload_id, reuploaded, specimen_data_exists, error_message)"
              + " values (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement ps = null;
        try {
            ps = DBUtil.getPreparedStatement(getConnection(), "saveImageUploaded()", dml);
            if (ps == null) throw new SQLException("saveImageUploaded() no statement");
            ps.setString(1, imageUploaded.getFileName());
            ps.setString(2, imageUploaded.getCode());
            ps.setString(3, imageUploaded.getShot());
            ps.setInt(4, imageUploaded.getNumber());
            ps.setString(5, imageUploaded.getExt());
            ps.setInt(6, imageUploaded.getImageUpload().getId());
            ps.setInt(7, imageUploaded.getIsReUploaded() ? 1 : 0);
            ps.setInt(8, imageUploaded.getIsSpecimenDataExists() ? 1 : 0);
            ps.setString(9, imageUploaded.getErrorMessage());
            ps.executeUpdate();
        } catch (SQLException e) {
            s_log.error("saveImageUploaded() fileName:" + imageUploaded.getFileName() + " e:" + e);
            throw e;
        } finally {
            DBUtil.close(ps, "saveImageUploaded()");
        }
    }

    /** error_message of a file the batch uploader is still working on (see ImageUploadBatchAction). */
    public static final String PROCESSING = "processing";

    /**
     * State of a file in an upload: "done" (recorded without an error), PROCESSING (an earlier
     * try was cut off part way), or null (not in this upload yet).
     */
    public String getUploadedState(int imageUploadId, String fileName) throws SQLException {
        String query = "select error_message from image_uploaded where image_upload_id = ? and filename = ?";
        PreparedStatement ps = null;
        ResultSet rset = null;
        String state = null;
        try {
            ps = DBUtil.getPreparedStatement(getConnection(), "getUploadedState()", query);
            if (ps == null) throw new SQLException("getUploadedState() no statement");
            ps.setInt(1, imageUploadId);
            ps.setString(2, fileName);
            rset = ps.executeQuery();
            while (rset.next()) {
                String error = rset.getString("error_message");
                if (error == null) return "done";
                if (PROCESSING.equals(error)) state = PROCESSING;
            }
        } finally {
            DBUtil.close(ps, rset, "this", "getUploadedState()");
        }
        return state;
    }

    /** Record a file as being processed (error_message PROCESSING until markUploadedDone()). */
    public void saveImageUploadedProcessing(ImageUploaded imageUploaded) throws SQLException {
        String error = imageUploaded.getErrorMessage();
        imageUploaded.setErrorMessage(PROCESSING);
        try {
            saveImageUploaded(imageUploaded);
        } finally {
            imageUploaded.setErrorMessage(error);
        }
    }

    /** The file is saved and its image row made. */
    public void markUploadedDone(int imageUploadId, String fileName, boolean reUploaded) throws SQLException {
        String dml = "update image_uploaded set error_message = null, reuploaded = greatest(coalesce(reuploaded, 0), ?)"
            + " where image_upload_id = ? and filename = ? and error_message = ?";
        PreparedStatement ps = null;
        try {
            ps = DBUtil.getPreparedStatement(getConnection(), "markUploadedDone()", dml);
            if (ps == null) throw new SQLException("markUploadedDone() no statement");
            ps.setInt(1, reUploaded ? 1 : 0);
            ps.setInt(2, imageUploadId);
            ps.setString(3, fileName);
            ps.setString(4, PROCESSING);
            ps.executeUpdate();
        } finally {
            DBUtil.close(ps, "markUploadedDone()");
        }
    }

    /** Set image_count to the number of files recorded for this upload; returns it. */
    public int updateImageCount(int imageUploadId) throws SQLException {
        int count = 0;
        PreparedStatement ps = null;
        ResultSet rset = null;
        try {
            ps = DBUtil.getPreparedStatement(getConnection(), "updateImageCount()",
                "select count(*) c from image_uploaded where image_upload_id = ?");
            if (ps == null) throw new SQLException("updateImageCount() no statement");
            ps.setInt(1, imageUploadId);
            rset = ps.executeQuery();
            if (rset.next()) count = rset.getInt("c");
        } finally {
            DBUtil.close(ps, rset, "this", "updateImageCount()");
        }
        ps = null;
        try {
            ps = DBUtil.getPreparedStatement(getConnection(), "updateImageCount()",
                "update image_upload set image_count = ? where id = ?");
            if (ps == null) throw new SQLException("updateImageCount() no statement");
            ps.setInt(1, count);
            ps.setInt(2, imageUploadId);
            ps.executeUpdate();
        } finally {
            DBUtil.close(ps, "updateImageCount()");
        }
        return count;
    }
}    

