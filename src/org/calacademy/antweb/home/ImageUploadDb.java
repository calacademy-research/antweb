package org.calacademy.antweb.home;

import java.util.*;
import java.io.Serializable;
import java.sql.*;
import java.time.*;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.imageUploader.*;
import org.calacademy.antweb.*;

public class ImageUploadDb extends AntwebDb {

    private static Log s_log = LogFactory.getLog(ImageUploadDb.class);

    public ImageUploadDb(Connection connection) {
      super(connection);
    }


    public ImageUpload getImageUpload(int id) {
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
                Copyright copyright = (new CopyrightDb(getConnection())).getCopyrightByYear(year);
                imageUpload.setCopyright(copyright);
                imageUpload.setLicense(rset.getString("license"));
                imageUpload.setIsComplete((rset.getInt("complete") == 1) ? true : false);
                imageUpload.setImages(getImagesUploaded(imageUpload));
            }
        } catch (SQLException e) {
            s_log.error("getImageUpload() exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getImageUpload()");
        }
        return imageUpload;
    }


    public ArrayList<ImageUploaded> getImagesUploaded(ImageUpload imageUpload) {
        ArrayList<ImageUploaded> images = new ArrayList<ImageUploaded>();
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
                imageUploaded.setIsReUploaded((rset.getInt("reuploaded") == 1) ? true : false);
                imageUploaded.setIsSpecimenDataExists((rset.getInt("specimen_data_exists") == 1) ? true : false);
                imageUploaded.setErrorMessage(rset.getString("error_message"));
                images.add(imageUploaded);
            }
        } catch (SQLException e) {
            s_log.error("getImagesUploaded() exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getImagesUploaded()");
        }
      
      return images;
    }    
    
    public ArrayList<ImageUpload> getImageUploads(String criteria) {
        ArrayList<ImageUpload> imageUploads = new ArrayList<ImageUpload>();
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
                Copyright copyright = (new CopyrightDb(getConnection())).getCopyrightByYear(year);
                imageUpload.setCopyright(copyright);
                imageUpload.setIsComplete((rset.getInt("complete") == 1) ? true : false);
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
                PreparedStatement ps=getConnection().prepareStatement(dml, Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, imageUpload.getCuratorId());
                ps.setInt(2, imageUpload.getGroupId());
                ps.setTimestamp(3, new java.sql.Timestamp(imageUpload.getCreated().getTime()));
                ps.setInt(4, imageUpload.getArtistId());
                ps.setInt(5, imageUpload.getImageCount());
                ps.setString(6, imageUpload.getLicense());
                ps.setInt(7, Year.now().getValue()); //imageUpload.getCopyright());

                ps.executeUpdate();
                ResultSet rs=ps.getGeneratedKeys();

                if(rs.next()){
                    imageUpload.setId(rs.getInt(1));
                }

            } else {
              s_log.warn("NOT IMPLEMENTED. Error.");
            }       

            A.log("saveImageUpload() dml:" + dml);
		} catch (SQLException e) {
			s_log.error("saveImageUpload() dml" + dml + " e:" + e);
			throw e;
		} finally { 		
			DBUtil.close(stmt, "saveImageUpload()");
		}
		return imageUpload; // May have updated ID.
	}

    public void completeImageUpload(ImageUpload imageUpload) throws SQLException {    
        Statement stmt = null;
        String dml = null;
    	try {
            stmt = DBUtil.getStatement(getConnection(), "completeImageUpload()");
            
            dml = "update image_upload set " 
                + "  complete = 1"
                + " where id = " + imageUpload.getId();
     
            stmt.executeUpdate(dml);

            A.log("completeImageUpload() dml:" + dml);
		} catch (SQLException e) {
			s_log.error("completeImageUpload() dml" + dml + " e:" + e);
			throw e;
		} finally { 		
			DBUtil.close(stmt, "completeImageUpload()");
		}
	}
	
    public void saveImageUploaded(ImageUploaded imageUploaded) throws SQLException {
    
        Statement stmt = null;
        String dml = null;
    	try {
            stmt = DBUtil.getStatement(getConnection(), "saveImageUploaded()");
  
            int reUploaded = 0;
            if (imageUploaded.getIsReUploaded()) reUploaded = 1;

            int specimenDataExists = 0;
            if (imageUploaded.getIsSpecimenDataExists()) specimenDataExists = 1;
            
            String errorMessage = "null";
            if (imageUploaded.getErrorMessage() != null) errorMessage = "'" + imageUploaded.getErrorMessage() + "'";

            dml = "insert into image_uploaded (filename, code, shot, number, ext"
                  + " , image_upload_id, reuploaded, specimen_data_exists, error_message) " 
              + " values ("
              + "'" + imageUploaded.getFileName() + "', '" + imageUploaded.getCode() + "', '" + imageUploaded.getShot() + "'"
              + ", " + imageUploaded.getNumber() + ", '" + imageUploaded.getExt() + "'"
              + ", " + imageUploaded.getImageUpload().getId() + ", " + reUploaded 
              + ", " + specimenDataExists + ", " + errorMessage + ")";
            
    	    stmt.executeUpdate(dml);

            A.log("saveImageUploaded() dml:" + dml);
		} catch (SQLException e) {
			s_log.error("saveImageUploaded() dml" + dml + " e:" + e);
			throw e;
		} finally { 		
			DBUtil.close(stmt, "saveImageUploaded()");
		}
	}
}    

