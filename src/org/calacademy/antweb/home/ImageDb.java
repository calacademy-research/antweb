package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.imageUploader.*;

/**
 * Table image (one row per image file of a specimen: code, shot, number) and its owners (group_image). Reads
 * a specimen's images (specimen pages, bigPicture.do), the image counts of statsPage.do and the likes lists
 * (list.do?action=likes); saves the row of an uploaded file (putImage, Image Uploader) and deletes images
 * (deleteImages.do); EXIF and has-tif admin jobs (utilData.do). Tables image, group_image, image_like, specimen,
 * taxon. Docs: docs/flows/image-upload.md, docs/flows/specimen-and-image-pages.md, docs/numbers.md.
 * Tests: ImageUploadSaveTest, tests/e2e/test_image_upload.py.
 */
public class ImageDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(ImageDb.class);

    public ImageDb(Connection connection) {
      super(connection);
    }

    public ArrayList<SpecimenImage> getExifImages() throws SQLException {
        ArrayList<SpecimenImage> images = new ArrayList<>();
        Statement stmt = null;
        ResultSet rset = null;
        try {
            // image_path is always null/
        
            String query = "select i.id, image_of_id, shot_type, shot_number, has_tiff, c.copyright " 
              + " from image i, copyright c "
              + " where i.copyright = c.id"
              //+ " and modified > '2019-05-01'"
              //+ " and image_of_id = 'antweb1038462'"
              //+ " and image_of_id in ('casent0005904')"
              //+ " and shot_type = 'd' and shot_number = 1"
            ;

            stmt = DBUtil.getStatement(getConnection(), "getExifImages()");

            rset = stmt.executeQuery(query);
            //A.log("getExifImages() query:" + query);

            int i = 0;
            while (rset.next()) {
                ++i;
                int limit = 1000000;
                if (i > limit) break;

                SpecimenImage specimenImage = new SpecimenImage();   
                
                //int id = rset.getInt("id");
                //specimenImage.setId(id);
                specimenImage.setCode(rset.getString("image_of_id"));
                specimenImage.setShotType(rset.getString("shot_type"));
                specimenImage.setNumber(rset.getInt("shot_number"));
                specimenImage.setCopyright(rset.getString("copyright"));
                specimenImage.setHasTiff(rset.getBoolean("has_tiff"));
                images.add(specimenImage);
            }
        } finally {
            DBUtil.close(stmt, rset, this, "getExifImages()");
        }
        return images;        
    }

    public void updateHasTif(ArrayList<SpecimenImage> notTifList) {
        String dml = "";
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateHasTif()");

            for (SpecimenImage image : notTifList) {
                dml = "update image set has_tiff = 0";
                String whereClause = " where image_of_id = '" + AntFormatter.escapeQuotes(image.getCode()) + "'";
                whereClause += " and shot_number = " + image.getShotNumber();
                whereClause += " and shot_type = '" + AntFormatter.escapeQuotes(image.getShotType()) + "'";
                dml += whereClause;
                s_log.debug("updateHasTif() dml:" + dml);
                int c = stmt.executeUpdate(dml); 
                ++ImageUtil.hasTifCorrected;
            }

		} catch (SQLException e) {
			s_log.error("updateHasTif() dml:" + dml + " e:" + e);
		} finally { 		
			DBUtil.close(stmt, "updateHasTif()");
		}
    }

    // Just for utilData.
    public void getExifData() throws SQLException {
        int artistCount = 0;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            // image_path is always null/
        
            String query = "select id, image_of_id, shot_type, shot_number, has_tiff from image";
            query += " where image_of_id in ('lacment145067', 'casent0102695')";
            // where artist = 1";
            stmt = DBUtil.getStatement(getConnection(), "getExifData()");

            rset = stmt.executeQuery(query);
            s_log.debug("getExifData() query:" + query);

            int i = 0;
            while (rset.next()) {
                ++i;
                if (i > 100) break;
                
                int id = rset.getInt("id");
                String code = rset.getString("image_of_id");
                String shotType = rset.getString("shot_type");
                int shotNumber = rset.getInt("shot_number");

                SpecimenImage specimenImage = getSpecimenImage(code, shotType, shotNumber);   
                //specimenImage.setPaths();
                String highres = specimenImage.getHighres();
                if (highres != null && !"null".equals(highres)) {             
                    String imagePath = AntwebProps.getDocRoot() + specimenImage.getHighres();
                    s_log.debug("getExifData() imagePath:" + imagePath);
                    Exif exif = new Exif(imagePath);
                    if (exif.isFound()) {
                      String artistName = exif.getArtist();
                      LogMgr.appendLog("exifData.txt", imagePath + " exif:" + exif);
                    }                      
                } else {
                    //A.log("getExifData() id:" + id + " code:" + code + " shotType:" + shotType + " shotNumber:" + shotNumber + " highres:" + highres);
                }
                //if ((i % 1000) == 0) getConnection().commit();
            }

        } finally {
            DBUtil.close(stmt, rset, this, "getExifData()");
        }
        s_log.warn("getExifData() artistCount:" + artistCount);
    }

    public ArrayList<SpecimenImage> getSpecimenImages(String code) throws SQLException {
        ArrayList<SpecimenImage> images = new ArrayList<>();
        Statement stmt = null;
        ResultSet rset = null;
        try {
            // if (getTaxonName().contains("calyptomyrmex") s_log.warn("setImages() overview:" + overview + " caste:" + caste + " chosenImageCode:" + chosenImageCode);

            // Get the images for the chosen code
            if (code.length() > 0) {
                String imgQuery =
                    "select shot_type, has_tiff from image where "
                        + " image_of_id='"
                        + AntFormatter.escapeQuotes(code)
                        + "' and source_table = 'specimen' and shot_number = 1";
                stmt = DBUtil.getStatement(getConnection(), "getSpecimenImages()");

                rset = stmt.executeQuery(imgQuery);

                int hasTiff = 0;
                SpecimenImage specImage = null;
                while (rset.next()) {
                    specImage = new SpecimenImage();
                    specImage.setShot(rset.getString(1));
                    specImage.setCode(code);
                    specImage.setNumber(1);
                    hasTiff = rset.getInt(2);
                    specImage.setHasTiff(hasTiff == 1);
                    //specImage.setPaths();
                    images.add(specImage);
                }
                //if (getTaxonName().equals(debugCode)) A.log("setImages(" + overview + ") code:" + chosenImageCode + " theQuery:" + imgQuery);
                //A.log("setImages(" + overview + ") code:" + chosenImageCode + " theQuery:" + imgQuery);
            }
        } finally {
            DBUtil.close(stmt, rset, this, "getSpecimenImages()");
        }
        return images;
    }


    public SpecimenImage getSpecimenImage(String imageId) {
      int id;
      try {
        id = Integer.parseInt(imageId == null ? "" : imageId.trim());
      } catch (NumberFormatException e) {
        s_log.info("getSpecimenImage() non-numeric imageId rejected");
        return null;
      }
      String clause = "i.id = " + id;
      A.log("clause:" + clause);
      return getSpecimenImageWithClause(clause);
    }

    public SpecimenImage getSpecimenImage(String code, String shot, int number) {
        String clause = " i.image_of_id = '" + AntFormatter.escapeQuotes(code) + "'"
                + " and i.shot_type = '" + AntFormatter.escapeQuotes(shot) + "'"
                + " and i.shot_number = " + number;
        return getSpecimenImageWithClause(clause);
    }

    public SpecimenImage getSpecimenImageWithClause(String clause) {
        String code = null;
        String shot = null;
        int number = 0;

        String query = null;
        ResultSet rset = null;
        Statement stmt = null;
		SpecimenImage specimenImage = null;

        try {
            query = "select i.image_of_id code, i.shot_type shot, i.shot_number number, i.upload_date, i.has_tiff, i.artist, gi.group_id, i.image_upload_id "
                + " from image i, group_image gi " 
                + " where i.id = gi.image_id and "
                + clause;

            A.log("getSpecimenImage() query:" + query);

            stmt = DBUtil.getStatement(getConnection(), "getSpecimenImage()");
            stmt.executeQuery(query);

            rset = stmt.getResultSet();

            int hasTiff = 0;
            int artistId = 0;
            while (rset.next()) {

                specimenImage = new SpecimenImage();

				specimenImage.setCode(rset.getString("code"));
				specimenImage.setShot(rset.getString("shot"));
                specimenImage.setNumber(rset.getInt("number"));

				specimenImage.setDate(rset.getString("upload_date"));

                hasTiff = rset.getInt("has_tiff");
				boolean ifHasTiff = hasTiff == 1;
				specimenImage.setHasTiff(ifHasTiff);

                specimenImage.setArtistId(rset.getInt("artist"));
                specimenImage.setGroupId(rset.getInt("group_id"));
                specimenImage.setUploadId(rset.getInt("image_upload_id"));
            }
            if (specimenImage == null) {
                //s_log.warn("getSpecimenImage() not found code:" + code + " shot:" + shot + " number:" + number);
                return null;
            }
            specimenImage.setHasTiff(hasTiff == 1);
        } catch (SQLException e) {
            s_log.error("getSpecimenImage() e:" + e + " query:" + query);
        } finally {
            //A.log("getSpecimenImage() before close");
            DBUtil.close(stmt, rset, "ImageDb", "getSpecimenImage()");
            //A.log("getSpecimenImage() after close");
        }

        return specimenImage;
    }

    /**
     * True if an image row exists for this code, shot and number (with or without an owning group).
     * On a database error: true, so that a check built on it refuses rather than allows.
     */
    public boolean imageExists(String code, String shotType, int shotNumber) {
        String query = "select 1 from image where image_of_id = ? and shot_type = ? and shot_number = ? limit 1";
        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setString(1, code);
            stmt.setString(2, shotType);
            stmt.setInt(3, shotNumber);
            try (ResultSet rset = stmt.executeQuery()) {
                return rset.next();
            }
        } catch (SQLException e) {
            s_log.error("imageExists() code:" + code + " e:" + e);
            return true;
        }
    }

    /**
     * The access groups that own an image (group_image rows). Empty when the image is
     * not found or has no owner recorded.
     */
    public Set<Integer> getImageGroupIds(String code, String shotType, int shotNumber) {
        Set<Integer> groupIds = new HashSet<>();
        String query = "select gi.group_id from image i, group_image gi"
            + " where i.id = gi.image_id and i.image_of_id = ? and i.shot_type = ? and i.shot_number = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setString(1, code);
            stmt.setString(2, shotType);
            stmt.setInt(3, shotNumber);
            try (ResultSet rset = stmt.executeQuery()) {
                while (rset.next()) groupIds.add(rset.getInt("group_id"));
            }
        } catch (SQLException e) {
            s_log.error("getImageGroupIds() code:" + code + " e:" + e);
        }
        return groupIds;
    }

    public boolean deleteImage(String code, String shotType, int shotNumber) {
        try {
            return deleteImageRows(code, shotType, shotNumber);
        } catch (SQLException e) {
            s_log.error("deleteImage() code:" + code + " shotType:" + shotType + " shotNumber:" + shotNumber + " e:" + e);
            return false;
        }
    }

    /** Delete the image rows (and their group_image rows) of this code, shot and number. Throws on a database error. */
    private boolean deleteImageRows(String code, String shotType, int shotNumber) throws SQLException {
        boolean deleted = false;
        String dml = "";
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "deleteImage()");
            if (stmt == null) throw new SQLException("deleteImage() no statement");

            //A.log("execute()  deleting:" + code + " " + shotType + " " + shotNumber);
            String whereClause = "where image_of_id = '" + AntFormatter.escapeQuotes(code) + "' and shot_type='" + AntFormatter.escapeQuotes(shotType) + "' and shot_number=" + shotNumber;
            
            dml = "delete from group_image where image_id in (select id from image " + whereClause + ")";                                    
            int c = stmt.executeUpdate(dml);                    
            if (c > 0) deleted = true;
            //A.log("delete 1 c:" + c + " query: " + dml);
                 
            dml = "delete from image " + whereClause;
            c = stmt.executeUpdate(dml);
            if (c > 0) deleted = true;
            //A.log("delete 2 c:" + c + " query: " + dml);

            //s_log.warn("deleteImage() Should we remove image from disk? code:" + code + " shotType:" + shotType + " shotNumber:" + shotNumber);
            // No! We use this prior to insertion, after the image has already been copied into place.
            
            if (deleted) LogMgr.appendLog("deletedImageLog.txt", "code:" + code + " shotType:" + shotType + " shotNumber:" + shotNumber);
        } catch (SQLException e) {
            s_log.error("deleteImage() dml" + dml + " e:" + e);
            throw e;
        } finally { 		
            DBUtil.close(stmt, "deleteImage()");
        }
        return deleted;
    }
    
    /**
     * Save the image row of an uploaded file, replacing the row of the same code, shot and number if there is one.
     * The delete and the insert are one transaction: if the insert fails, the old row (and its group) is kept.
     * Returns false if nothing was saved. Every row of that code, shot and number is replaced, also one without a
     * group_image row (before, such a row was kept and a second row was added).
     */
    public boolean putImage(ImageUploaded imageUploaded) {
        String code = imageUploaded.getCode();
        String shot = imageUploaded.getShot();
        int number = imageUploaded.getNumber();

        Connection connection = getConnection();
        boolean autoCommit = true;
        try {
            autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            deleteImageRows(code, shot, number);
            insertImage(imageUploaded);
            connection.commit();
            return true;
        } catch (SQLException e) {
            s_log.error("putImage() code:" + code + " shot:" + shot + " number:" + number + " not saved, e:" + e);
            try {
                connection.rollback();
            } catch (SQLException e2) {
                s_log.error("putImage() rollback e:" + e2);
            }
            return false;
        } finally {
            try {
                connection.setAutoCommit(autoCommit);
            } catch (SQLException e) {
                s_log.error("putImage() setAutoCommit e:" + e);
            }
        }
    }

    public int getMaxId() {
      try {
        return maxId();
      } catch (SQLException e) {
        s_log.error("getMaxId() e:" + e);
        return 0;
      }
    }

    private int maxId() throws SQLException {
      int id = 0;
      String query = "select max(id) id from image";    
      ResultSet rset = null;
      Statement stmt = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getMaxId()");
        if (stmt == null) throw new SQLException("getMaxId() no statement");
        rset = stmt.executeQuery(query);
        while (rset.next()) {
            id = rset.getInt("id");
        }
      } finally {
        DBUtil.close(stmt, rset, "ImageDb", "getMaxId()");
      }
      return id;
    }

    /** Insert the image row and its group_image row. Throws on a database error (putImage() then rolls back). */
    private void insertImage(ImageUploaded imageUploaded) throws SQLException {

        ImageUpload imageUpload = imageUploaded.getImageUpload();
        Statement stmt = null;
        String dml = null;
    	try {
    	    int id = maxId();
            ++id;
    	
            stmt = DBUtil.getStatement(getConnection(), "insertImage()");            
            if (stmt == null) throw new SQLException("insertImage() no statement");
            dml = "insert into image (id, image_of_id, source_table, shot_type, shot_number" 
                  + ", artist, copyright, license" 
                  + ", upload_date, has_tiff, image_upload_id)" 
              + " values (" + id
                  + ", '" + AntFormatter.escapeQuotes(imageUploaded.getCode()) + "', 'specimen', '" + AntFormatter.escapeQuotes(imageUploaded.getShot()) + "', " + imageUploaded.getNumber() 
                  + ", " + imageUpload.getArtistId() + ", " + imageUpload.getCopyright().getId() + ", " + 1
                  + ", now(), " + imageUploaded.hasTiff() + ", " + imageUploaded.getImageUpload().getId() + ")";

    	    stmt.executeUpdate(dml);
    	    //A.log("insertImage() 1 dml:" + dml);

            dml = "insert into group_image (group_id, image_id)" 
              + " values (" + imageUploaded.getImageUpload().getGroupId() + ", " + id + ")";

    	    stmt.executeUpdate(dml);

            //A.log("insertImage() dml:" + dml);
		} catch (SQLException e) {
			s_log.error("insertImage() dml" + dml + " e:" + e);
			throw e;
		} finally { 		
			DBUtil.close(stmt, "insertImage()");
		}    
    }


// --------------------------------------------------------------------

    public static ArrayList<String> getLikesLinkList(Connection connection) throws SQLException {
        ArrayList<String> imageList = new ArrayList<>();
        String theQuery = "select image_id, count(image_id) likes from image_like group by image_id";

        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(connection, "getLikesLinkList()");

            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                String imageId = rset.getString("image_id");
                String likes = rset.getString("likes");                
                imageList.add("<a href='" + AntwebProps.getDomainApp() + "/bigPicture.do?imageId=" + imageId + "'>" + imageId + "(" + likes + ")</a>");
            }
        } finally {
            DBUtil.close(stmt, rset, "ImageDb", "getLikesLinkList()");
        }
        return imageList;
    }

    public static ArrayList<LikeObject> getLikesObjectList(Connection connection) throws SQLException {
        ArrayList<LikeObject> likeObjectList = new ArrayList<>();
        String theQuery = "select image_id, count(image_id) likes, shot_type, shot_number, image_of_id,  s.taxon_name " 
            + " from specimen s, image_like il join image i on image_id = i.id " 
            + " where s.code = i.image_of_id " 
            + " and image_id != 0 "
            + " group by image_id" 
            + "  order by max(il.created)";
        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(connection, "getLikesObjectList()");

          rset = stmt.executeQuery(theQuery);

          while (rset.next()) {
            LikeObject likeObject = new LikeObject();

            likeObject.imageId = rset.getString("image_id");
            likeObject.likes = rset.getString("likes");                     
            likeObject.shot = rset.getString("shot_type");
            likeObject.number = rset.getString("shot_number");
            likeObject.code = rset.getString("image_of_id");
            likeObject.taxonName = rset.getString("taxon_name");

            likeObjectList.add(likeObject);
          }
        } finally {
            DBUtil.close(stmt, rset, "ImageDb", "getLikesObjectList()");
        }

        return likeObjectList;
    }

/*
    public static void getFormProps(SpecimenImageForm form, Connection connection) throws SQLException {
        String theQuery = "select shot_type, image_of_id, shot_number, artist from image where id = " + form.getImageId();

        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(connection, "getFormProps()");
          rset = stmt.executeQuery(theQuery);
A.log("getFormProps() theQuery:" + theQuery);
          while (rset.next()) {
            String shot = rset.getString("shot_type");
            String code = rset.getString("image_of_id");
            int number = rset.getInt("shot_number");
            String artist = rset.getString("artist");
            form.setShot(shot);
            form.setCode(code);
            form.setNumber(number);
            form.setArtist(artist);
          }
        } finally {
            DBUtil.close(stmt, rset, "ImageDb", "getFormProps()");
        }
    }
*/

    public HashMap<String, int[]> getImageStats() throws SQLException {
        HashMap<String, int[]> imageStats = new HashMap<>();
        // 2026-10-27 (Brian Fisher): Images are images of the ant; label images in their own column (ImageCounts).
        String ant = StatisticsDb.ANT_IMAGE;
        String theQuery = "select "
          + " status "
          + ", sum(IF(" + ant + ", 1, 0)) total "
          + ", sum(IF(" + ant + " and caste = 'worker', 1, 0)) worker"
          + ", sum(IF(" + ant + " and caste = 'male', 1, 0)) male"
          + ", sum(IF(" + ant + " and caste = 'queen', 1, 0)) queen"
          // NULL caste counts as Other (2026-10-21: it fell in no column, so the four did not add up to Total)
          + ", sum(IF(" + ant + " and (caste is null or caste not in ('worker', 'male', 'queen')), 1, 0)) as other"
          + ", sum(IF(" + ant + ", 0, 1)) labels"
          + " from specimen s, image i where s.code = i.image_of_id group by status"
          + " order by total desc";

        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(getConnection(), "getImageStats()");
          rset = stmt.executeQuery(theQuery);
          //A.log("getImageStats() query:" + theQuery);
          while (rset.next()) {
            String status = rset.getString("status");
            int total = rset.getInt("total");
            int worker = rset.getInt("worker");
            int male = rset.getInt("male");
            int queen = rset.getInt("queen");
            int other = rset.getInt("other");
            int labels = rset.getInt("labels");
            int[] numbers = {total, worker, male, queen, other, labels};
            imageStats.put(status, numbers);
          }
        } finally {
            DBUtil.close(stmt, rset, "ImageDb", "getImageStats()");
        }
        return imageStats;
    }
/*
+----------------+----------+--------+-------+-------+-------+
| status         |    total | worker | male  | queen | other |
+----------------+----------+--------+-------+-------+-------+
| valid          |   187252 | 153469 | 12869 | 19056 |    97 |
| morphotaxon    |    29372 |  22516 |  3164 |  2926 |    24 |
| indetermined   |     4001 |   2607 |   940 |   411 |     0 |
| unrecognized   |      412 |    252 |    66 |    64 |     0 |
| unavailable    |      141 |    135 |     6 |     0 |     0 |
| unidentifiable |       29 |     29 |     0 |     0 |     0 |
+----------------+----------+--------+-------+-------+-------+
6 rows in set (0.93 sec)
*/


    public HashMap<String, Integer> getImageTaxonStats() throws SQLException {
        HashMap<String, Integer> imageTaxonStats = new HashMap<>();
        // names with a specimen that has an image of the ant (2026-10-27: label images did count)
        String theQuery = "select t.status, count(distinct t.taxon_name) count from taxon t, specimen, image i "
                + " where t.taxon_name = specimen.taxon_name and specimen.code = i.image_of_id and " + StatisticsDb.ANT_IMAGE
                + " group by t.status order by count(distinct t.taxon_name) desc";

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getImageTaxonStats()");
            rset = stmt.executeQuery(theQuery);
            //A.log("getImageTaxonStats() query:" + theQuery);
            while (rset.next()) {
                String status = rset.getString("t.status");
                int count = rset.getInt("count");
                imageTaxonStats.put(status, Integer.valueOf(count));
            }
        } finally {
            DBUtil.close(stmt, rset, "ImageDb", "getImageTaxonStats()");
        }
        return imageTaxonStats;
    }



}    

