package org.calacademy.antweb.home;

import java.util.*;

import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.util.*;


/**
 * Table upload (one row per specimen upload) and upload_line: reads an upload, the list of uploads (UploadMgr,
 * listSpecimenUploads.do), a group's or login's last upload, the counts of a group's specimens (getCounts) and
 * the lines of the last file (logMgr.do?action=uploadLine); helpers of the uploads (taxonomic history rows, a
 * genus's subfamily check). Base class of SpecimenUploadDb, SpeciesListUploadDb, WorldantsUploadDb.
 * Tables upload, upload_line, ant_group, specimen, taxon. Docs: docs/flows/specimen-upload.md, docs/numbers.md.
 * Tests: NumberDecisionsDbTest.
 */
public class UploadDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(UploadDb.class);

    public UploadDb(Connection connection) {
      super(connection);
    }

    public Upload getUpload(int uploadId) throws SQLException {
      return getUploadWhere("upload_id = " + uploadId);
    }

    /** The upload row of this row id (upload.id; rows from before 2018-01 have no upload_id). */
    public Upload getUploadByRowId(int id) throws SQLException {
      return getUploadWhere("id = " + id);
    }

    private Upload getUploadWhere(String where) throws SQLException {
      Upload upload = null;
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getUpload()");

        String query = "select *" 
          + " from upload " 
          + " where " + where;
        rset = stmt.executeQuery(query);

        while (rset.next()) {
          upload = new Upload();
          upload.setId(rset.getInt("id"));
          upload.setUploadId(rset.getInt("upload_id"));
          upload.setLoginId(rset.getInt("login_id"));
          upload.setGroupName(rset.getString("group_name"));
          upload.setGroupId(rset.getInt("group_id"));
          upload.setLogFileName(rset.getString("log_file_name"));
          upload.setCreated(rset.getTimestamp("created"));

		  upload.setSpecimens(rset.getInt("specimens"));
		  upload.setCollections(rset.getInt("collections"));
		  upload.setLocalities(rset.getInt("localities"));
		  upload.setSubfamilies(rset.getInt("subfamilies"));
		  upload.setGenera(rset.getInt("genera"));
		  upload.setSpecies(rset.getInt("species"));
		  upload.setUngeoreferenced(rset.getInt("ungeoreferenced"));
		  upload.setFlagged(rset.getInt("flagged"));        
        }
        //A.log("getUpload() query:" + query);       
      } finally {
        DBUtil.close(stmt, rset, "getUpload()");        
      }      
      return upload;
    }

    public ArrayList<Upload> getUploads() {
        //A.log("UploadDb.getUploads()");
        ArrayList<Upload> uploads = new ArrayList<>();
        Statement stmt = null;
        ResultSet rset = null;
        String query = "select * from upload";
        try {

            stmt = DBUtil.getStatement(getConnection(), "getUploads()");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
              Upload upload = new Upload();
			  upload.setId(rset.getInt("id"));
			  upload.setUploadId(rset.getInt("upload_id"));
			  upload.setLoginId(rset.getInt("login_id"));
			  upload.setGroupName(rset.getString("group_name"));
			  upload.setGroupId(rset.getInt("group_id"));
			  upload.setLogFileName(rset.getString("log_file_name"));
              upload.setCreated(rset.getTimestamp("created"));
                
			  upload.setSpecimens(rset.getInt("specimens"));
			  upload.setCollections(rset.getInt("collections"));
			  upload.setLocalities(rset.getInt("localities"));			  
			  upload.setSubfamilies(rset.getInt("subfamilies"));
			  upload.setGenera(rset.getInt("genera"));
			  upload.setSpecies(rset.getInt("species"));
			  upload.setUngeoreferenced(rset.getInt("ungeoreferenced"));
			  upload.setFlagged(rset.getInt("flagged"));                  
		      uploads.add(upload);
			}
        } catch (SQLException e) {
            s_log.warn("getUploads() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "getUploads()");        
        }
        //A.log("getUploads() query:" + query + " uploads:" + uploads);
        return uploads;
    }

// Upload Line functions

	public void removeUploadLines(Group group) {
	    int groupId = group.getId();

        String dml = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "removeUploadLines()");
            dml = "delete from upload_line where group_id = " + groupId;  
            stmt.executeUpdate(dml);
        } catch (SQLException e) {
            s_log.error("removeUploadLines() e:" + e);
        } finally {
           DBUtil.close(stmt, null, this, "removeUploadLines()");        
        }
    }
    
	public void addUploadLine(String fileName, int lineNum, int displayLineNum, String theLine, Group group) {
	    int groupId = group.getId();

        String insert = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "addUploadLine()");
            insert = "insert into upload_line(file_name, line_num, display_line_num, line, group_id) " 
              + "values (" + DBUtil.sqlLiteral(fileName) + ", " + lineNum + ", " + displayLineNum + ", '" + AntFormatter.escapeQuotes(theLine) + "', " + groupId + ")";  
            stmt.executeUpdate(insert);
        } catch (SQLException e) {
            s_log.error("addUploadLine() fileName:" + fileName + " lineNum:" + lineNum + " displayLineNum:" + displayLineNum + " e:" + e);
        } finally {
           DBUtil.close(stmt, null, this, "addUploadLine()");        
        }
    }	
    
    public UploadLine getUploadLine(String fileName, int lineNum) {
        //A.log("UploadDb.getUploadLines()");
        ArrayList<UploadLine> uploadLines = new ArrayList<>();
        PreparedStatement stmt = null;
        ResultSet rset = null;
        String query = "select * from upload_line where file_name = ? and line_num = ?";
        try {
            stmt = getConnection().prepareStatement(query);
            stmt.setString(1, fileName);
            stmt.setInt(2, lineNum);
            rset = stmt.executeQuery();
            s_log.debug("getUploadLine() query:" + query);
            
            while (rset.next()) {
              UploadLine uploadLine = new UploadLine();
			  uploadLine.setId(rset.getInt("id"));
			  uploadLine.setFileName(rset.getString("file_name"));
			  uploadLine.setLineNum(rset.getInt("line_num"));
			  uploadLine.setDisplayLineNum(rset.getInt("display_line_num"));
			  uploadLine.setGroupId(rset.getInt("group_id"));
			  uploadLine.setLine(rset.getString("line"));
              uploadLine.setCreated(rset.getTimestamp("created"));
		      return uploadLine;
			}
        } catch (SQLException e) {
            s_log.warn("getUploadLines() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "getUploadLines()");        
        }
        //A.log("getUploadLines() query:" + query + " uploadLines:" + uploadLines);
        return null;
    }
    
    public Upload getCounts(int groupId) throws SQLException {
        Upload upload = getLastUploadByGroup(groupId);
		if (upload == null) return null;

        getCounts(upload);
        getFlaggedCount(upload);
        return upload;
    }

    /**
     * The numbers of the group's specimens as they are now, by the rule of every upload-group number (GroupCountDb,
     * Brian Fisher 2026-10-22, docs/numbers.md Q8): Specimens, Collections, Localities, Ungeoreferenced of the counted
     * specimens; Subfamilies, Genera; Species = distinct full names of species and subspecies of the counted statuses,
     * ants, identified to genus (until 2026-10-22: distinct species epithets of valid names). Written into the upload
     * row at the end of an upload (updateCounts); the nightly recount (GroupCountDb.recountAll) uses the same query.
     */
    private Upload getCounts(Upload upload) throws SQLException {
        if (upload == null) return null;
        try (PreparedStatement stmt = getConnection().prepareStatement(GroupCountDb.groupCountsSql())) {
            stmt.setInt(1, upload.getGroupId());
            try (ResultSet rset = stmt.executeQuery()) {
                if (rset.next()) {
                    // columns: g, all, specimens, collections, localities, ungeoreferenced, subfamilies, genera, species, flagged
                    upload.setSpecimens(rset.getInt(3));
                    upload.setCollections(rset.getInt(4));
                    upload.setLocalities(rset.getInt(5));
                    upload.setUngeoreferenced(rset.getInt(6));
                    upload.setSubfamilies(rset.getInt(7));
                    upload.setGenera(rset.getInt(8));
                    upload.setSpecies(rset.getInt(9));
                } else {
                    upload.setSpecimens(0);
                    upload.setCollections(0);
                    upload.setLocalities(0);
                    upload.setUngeoreferenced(0);
                    upload.setSubfamilies(0);
                    upload.setGenera(0);
                    upload.setSpecies(0);
                }
            }
        } catch (SQLException e) {
            s_log.warn("getCounts() e:" + e);
        }
        return upload;
    }

    public Upload getFlaggedCount(Upload upload) throws SQLException {
        if (upload == null) return null;    
        Statement stmt = null;
        ResultSet rset = null;
        String query = "select count(if(flag = 'red', 1, null)) flagged " 
          + " from specimen where access_group = " + upload.getGroupId();

        try {
            stmt = DBUtil.getStatement(getConnection(), "getFlaggedCount()");
            //A.log("getFlaggedCount() groupId:" + upload.getGroupId());
            rset = stmt.executeQuery(query);

            while (rset.next()) {
			  upload.setFlagged(rset.getInt("flagged"));
			}
        } catch (SQLException e) {
            s_log.warn("getFlaggedCount() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "getFlaggedCount()");        
        }
        //A.log("getFlaggedCount() query:" + query);
        return upload;
    }
    
    
    public int getLastUploadId(int groupId) throws SQLException {
      Upload upload = getLastUploadByGroup(groupId);
      if (upload != null) return upload.getUploadId();
      return 0;
    }

    public Upload getLastUploadByGroup(int groupId) throws SQLException {
        return getLastUpload("group_id", groupId);
    }   
    public Upload getLastUploadByLogin(int loginId) throws SQLException {
        return getLastUpload("login_id", loginId);
    }       

    /**
     * The newest upload row of a group or login (column group_id or login_id): the one with the highest upload_id, or,
     * when none of the rows has one, the newest row (highest id). 2026-10-30: upload_id was added in 2018-01 (db/upgrade
     * 7.9/2018-01-11.sql), so the rows of earlier uploads have none, and "max(upload_id)" found nothing for a curator or
     * group whose uploads are all older: curator.do showed "Specimen Uploads 1" and no Last Upload (James Trager,
     * curator.do?id=5). Rows with an upload_id are newer than those without; among them the order is as before.
     */
    public Upload getLastUpload(String column, int value) throws SQLException {
        if (!"group_id".equals(column) && !"login_id".equals(column)) throw new IllegalArgumentException(column);
        String query = lastUploadSql(column);
        int rowId = 0;
        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setInt(1, value);
            try (ResultSet rset = stmt.executeQuery()) {
                if (rset.next()) rowId = rset.getInt(1);
            }
        } catch (SQLException e) {
            s_log.warn("getLastUpload() " + column + ":" + value + " e:" + e);
        }
        return rowId > 0 ? getUploadByRowId(rowId) : null;
    }

    /** The query (one parameter) of getLastUpload: the row id of the newest upload of the group or login. */
    public static String lastUploadSql(String column) {
        return "select id from upload where " + column + " = ?"
            + " order by coalesce(upload_id, 0) = 0, upload_id desc, id desc limit 1";
    }
    
    public Timestamp getFirstUploadDate(int groupId) {   
      return getUploadDate(groupId, "asc");
    }
    public Timestamp getLastUploadDate(int groupId) {   
      return getUploadDate(groupId, "desc");
    }
    public Timestamp getUploadDate(int groupId, String order) {   
        Timestamp uploadDate = null;     
		Statement stmt = null;
		ResultSet rset = null;
        try {
			String query = "select created from upload where group_id = " + groupId + " order by created " + order;

			stmt = DBUtil.getStatement(getConnection(), "getUploadDate()");
			rset = stmt.executeQuery(query);
			while (rset.next()) {
			  Timestamp created = rset.getTimestamp("created");
              //A.log("getUploadDate() created:" + created);
			  if (created != null) return created;
			}
			
        } catch (SQLException e) {
            s_log.warn("getUploadDate() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "getUploadDate()");        
        }
        //A.log("getUploadDate() query:" + query);
        return uploadDate;
    }   

    public int getUploadCount(int groupId) {   
        int uploadCount = 0;
		Statement stmt = null;
		ResultSet rset = null;
        try {
			String query = "select count(*) count from upload where group_id = " + groupId;

			stmt = DBUtil.getStatement(getConnection(), "getUploadCount()");
			rset = stmt.executeQuery(query);
			while (rset.next()) {
			  uploadCount = rset.getInt("count");
			}
			
        } catch (SQLException e) {
            s_log.warn("getUploadCount() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "getUploadCount()");        
        }
        //A.log("getUploadCount() query:" + query);
        return uploadCount;
    }

    public Upload updateCounts(int groupId) {
        Upload upload = null;
        Statement stmt = null;
        try {
            upload = getCounts(groupId);
            if (upload == null) return null;
            
            int uploadId = getLastUploadId(groupId);
            stmt = DBUtil.getStatement(getConnection(), "updateCounts()");
			
			String dml = "update upload set "
			  + "  specimens = " + upload.getSpecimens()
			  + ", collections = " + upload.getCollections()
			  + ", localities = " + upload.getLocalities()
			  + ", subfamilies = " + upload.getSubfamilies()
			  + ", genera = " + upload.getGenera()
			  + ", species = " + upload.getSpecies()
			  + ", ungeoreferenced = " + upload.getUngeoreferenced()
			  + ", flagged = " + upload.getFlagged()
			  // the row read by getCounts (2026-10-30: a group whose newest row is from before 2018 has no upload_id)
			  + " where id = " + upload.getId();

			//A.slog("updateCounts() dml:" + dml);
			stmt.execute(dml);
			
        } catch (SQLException e) {
            s_log.warn("updateCounts() e:" + e);
        } finally {
            DBUtil.close(stmt, "updateCounts()");        
        }
        //A.log("updateCounts() query:" + query);
        return upload;
    }   

    public void updateCounts() {
      ArrayList<Group> groups = GroupMgr.getGroups();
      for (Group group : groups) {
        //A.log("updateCounts() group:" + group.getId());
        updateCounts(group.getId());
      }         
    }

    public void updateGroup(Group accessGroup) {
        //s_log.warn("updateGroup()");
        String dml = null;
        Statement stmt = null;
        int uploadCount = getUploadCount(accessGroup.getId());
        Timestamp firstUpload = getFirstUploadDate(accessGroup.getId());
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateUpload()");
            dml = "update ant_group set first_specimen_upload = '" + firstUpload + "', upload_count = " + uploadCount + " where id = " + accessGroup.getId();
            //A.log("updateGroup insert:" + dml);
            stmt.executeUpdate(dml);
        } catch (SQLException e) {
            s_log.error("updateUpload() e:" + e);
        } finally {
           DBUtil.close(stmt, null, this, "updateUpload()");
        }
    }

    public void insertDescription(String table, String taxonName, String authorDate, String title, String content) {
        /** Creation of taxonomichistory description_edit records */

        if (!title.equals("taxonomichistory")) {
          s_log.error("We only insert taxonomichistory records now.");
          AntwebUtil.logStackTrace();
          return;
        }
        String dml = null;
        PreparedStatement stmt = null;
        try {

            dml = "delete from " + table + " where taxon_name = ? and title = 'taxonomichistory'";
            if (authorDate != null) dml += " and author_date = ?";

            stmt = DBUtil.getPreparedStatement(getConnection(), "UploadDb.insertDescription()", dml);

            stmt.setString(1, taxonName);
            if (authorDate != null) stmt.setString(2, authorDate);

            //s_log.warn("insertDescription() dml:" + dml);
            stmt.executeUpdate();

            //s_log.info("insertDescription() query:" +  query);

            stmt.close();

            String insertStatement = "insert into " + table + " (taxon_name, title, content, is_manual_entry) values (?, ?, ?, 0)";

            if (authorDate != null) {
                insertStatement = "insert into " + table + " (taxon_name, title, content, is_manual_entry, author_date) values (?, ?, ?, 0, ?)";
            }

            stmt = DBUtil.getPreparedStatement(getConnection(), "UploadDb.insertDescription()", insertStatement);

            stmt.setString(1, taxonName);
            stmt.setString(2, title);
            stmt.setString(3, content);
            if (authorDate != null) stmt.setString(4, authorDate);

            stmt.executeUpdate();

        } catch (SQLException e) {
            s_log.error("insertDescription() e:" + e + " dml:" + dml);
        } finally {
            DBUtil.close(stmt, "UploadDb.insertDescription()");
        }
    }    
  
    public Status getStatus(String taxonName) throws SQLException
    {  // upload may be a species list or a specimen list fileName
      if (Taxon.isMorpho(taxonName)) {
        return new Status(Status.MORPHOTAXON);
      } else if (Taxon.isIndet(taxonName)) {
        return new Status(Status.INDETERMINED);  
      } else {
      
        Taxon dummyTaxon = new TaxonDb(getConnection()).getTaxon(taxonName);
        if (dummyTaxon == null) return new Status(Status.UNRECOGNIZED);   // not in AntWeb
        String status = dummyTaxon.getStatus();
        String currentValidName = dummyTaxon.getCurrentValidName();
      
        if (status == null) {
          return new Status(Status.UNRECOGNIZED);
        } else {
          Status returnVal = new Status(status);
          if (Status.usesCurrentValidName(status)) {
            returnVal.setCurrentValidName(currentValidName);
          }
          return returnVal;
        }
      }
    }
        
    private static String s_lastTaxonName;
    private static boolean s_lastPass;
    
    public boolean passGenusSubfamilyCheck(String taxonName, String genus, String subfamily, String family, String source, int groupId) 
      throws SQLException {
        // For a specimen file upload, the genus and subfamily must exist, or they will be created.
        //  The taxon will pass if it is in Bolton, or if it is a morphospecies.   //if it is not an ant, 
        //  Otherwise, false will be returned as the genus/subfamily is new (it will be allowed, but will be logged (to catch misspellings)).  
      
        // Specimen uploads must have a family.  If it is NOT formicidae then it WILL pass this test.
        if (family == null || !"formicidae".equals(family)) {
          return true;
        }

        String testTaxon = "amblyoponinaeamblyoponine_genus1";
        //if (AntwebProps.isDevMode() && testTaxon.equals(taxonName)) s_log.warn("passGenusSubfamilyCheck() 0 taxonName:" + taxonName);
      
        // Project uploads do NOT require a family.
        if (source.equals(Project.WORLDANTS)) {
            // we only perform this test on non-bolton taxons
            return true;
        }

        //A.log("passGenusSubfamilyCheck() 1 taxonName:" + taxonName);
 
        // Because this method can be called repeatedly with the same taxon info, record and quickly return the last result in that case
        if (taxonName.equals(s_lastTaxonName)) return s_lastPass;
        s_lastTaxonName = taxonName;

        Taxon genusObj = TaxonMgr.getGenus(subfamily + genus);
        if (genusObj == null) {
          // Genus not found.  But, if it is a morpho, create it and return true...

          if (Taxon.isMorphoOrIndet(taxonName)) {
            //if (AntwebProps.isDevMode() && testTaxon.equals(taxonName)) s_log.warn("passGenusSubfamilyCheck() is morpho taxonName:" + taxonName);
            if (Taxon.isIndet(taxonName)) {
              insertGenus(taxonName, family, subfamily, genus, source, "insertGenus", Status.INDETERMINED, groupId);
            } else if (Taxon.isMorpho(taxonName)) {
              insertGenus(taxonName, family, subfamily, genus, source, "insertGenus", Status.MORPHOTAXON, groupId);            
            } else {
              s_log.error("passGenusSubfamily() so we do nothing?.  Not right.  TaxonName:" + taxonName + " source:" + source);
            }
            s_lastPass = true;  
          } else {
            s_lastPass = false;
          }
        } else {
          s_lastPass = true;
        }
        return s_lastPass;
    }        

/*
https://www.antweb.org/showLog.do?action=uploadLog&file=20180506-19:24:04-specimen24.txt&line=164

This specimen record claims to be of subfamily: Incertae_sedis
But that is not a valid taxon. Why do we allow that?

This specimen: https://www.antweb.org/specimen.do?name=jwj-bu19

This work should clear this integrity report: https://www.antweb.org/query.do?action=curiousQuery&name=geolocaleTaxaWithoutTaxon

Debug the above method UploadDb.passGenusSubfamilyCheck();
*/



    // These (insertGenus, insertSubfamily) are used during worldants upload. Would be nice
    // to have this elsewhere. Or done in batch afterwards. Not delete but check for existence.
    private int insertGenus(String taxonName, String family, String subfamily, String genus, String source, String insertMethod, String status, int groupId) {
      // Was insertMorphoGenus.  Should be generified?  // 
      //String insertMethod = "insertMorphoGenus";
      int c = 0;
      String dml = "";                                                                                                                         
      Statement stmt = null; 
      try {
      
                //new TaxonDb(getConnection()).delete(taxonName);
        if (new TaxonDb(getConnection()).isExists(taxonName)) return 0;

        if (new TaxonDb(getConnection()).isGenusExists(subfamily, genus)) {
            s_log.warn("insertGenus() genus row already exists for subfamily:" + subfamily + " genus:" + genus + ". Not inserting taxonName:" + taxonName);
            return 0;
        }
        
        dml = "insert into taxon (taxon_name, family, subfamily, genus, taxarank, source, insert_method, status, access_group) "
            + " values (" + lit(taxonName) + ", " + lit(family) + ", " + lit(subfamily) + ", " + lit(genus) + ", 'genus', "
            + lit(source) + ", " + lit(insertMethod) + ", " + lit(status) + ", " + groupId + ")";

        //s_log.warn("insertMorphoGenus() dml:" + dml);
        stmt = DBUtil.getStatement(getConnection(), "UploadDb.insertGenus()");      
        c = stmt.executeUpdate(dml);
      } catch (SQLException e) {
        // This will happen in cases where the records are not sequential.  It is OK.
        s_log.warn("insertGenus() for taxonName:" + taxonName + " theStatement:" + dml + " e:" + e);
      } finally {
        DBUtil.close(stmt, "UploadDb.insertGenus()");
      }     
      return c;
    }

    public int insertSubfamily(String taxonName, String family, String subfamily, String source, String insertMethod, String status) 
      throws SQLException {
    // This was insertIndetSubfamily.  Should it be generified?  Should it be in TaxonDb?
      int c = 0;
      
      String dml = "";                                                                                                                         

      getExtantIndetSubfamilies().add(subfamily);
      
      Statement stmt = null;

      try {        // This is to make sure that the following statement does not get a key conflict.
      
        //new TaxonDb(getConnection()).delete(taxonName);
        if (new TaxonDb(getConnection()).isExists(taxonName)) return 0;

        dml = "insert into taxon (taxon_name, family, subfamily, taxarank, source, insert_method, parent_taxon_name, status) "
            + " values (" + lit(taxonName) + ", " + lit(family) + ", " + lit(subfamily) + ", 'subfamily', "
            + lit(source) + ", " + lit(insertMethod) + ", " + lit(family) + ", " + lit(status) + ")";
        //A.log("insertSubfamily() insert dml:" + dml);
        stmt = DBUtil.getStatement(getConnection(), "UploadDb.insertSubfamily()");      
        c = stmt.executeUpdate(dml);

        stmt = getConnection().createStatement();
        dml = "delete from proj_taxon where project_name = " + lit(source) + " and taxon_name = " + lit(taxonName);
        //A.log("insertSubfamily() delete proj_taxon dml:" + dml);
        stmt.executeUpdate(dml);

        new ProjTaxonDb(getConnection()).insert(source, taxonName, "insertSubfamily");

      } catch (SQLException e) {
        // This will happen in cases where the records are not sequential.  It is OK.
        s_log.warn("insertSubfamily() Not a problem.  into proj_taxon.  For taxonName:" + taxonName + " theStatement:" + dml + " e:" + e);
      } finally {
        DBUtil.close(stmt, "UploadDb.insertSubfamily()");
      } 
      return c;
    }

    /**
     * A value from an uploaded file as a quoted SQL literal (backslash and quote escaped). As before, null is the
     * text 'null' (these values were concatenated in quotes); a quote in a name no longer breaks or changes the query.
     */
    static String lit(String value) {
        return DBUtil.sqlLiteral(String.valueOf(value));
    }

    private static ArrayList<String> s_extantIndetSubfamilies;
    public ArrayList<String> getExtantIndetSubfamilies() throws SQLException {
      if (s_extantIndetSubfamilies == null) {
        s_extantIndetSubfamilies = new ArrayList<>();
        String query = "select distinct subfamily from taxon " 
            + " where taxarank = 'subfamily'"
            + " and taxon_name like '(%'";
        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(getConnection(), "UploadDb.getExtantIndetSubfamilies()");
          stmt.execute(query);
          rset = stmt.getResultSet();
          while (rset.next()) {
              String subfamily = rset.getString(1);
              s_extantIndetSubfamilies.add(subfamily);
          }
        } finally {
          DBUtil.close(stmt, rset, "UploadDb.getExtantIndetSubfamilies()");
        }
      }
        return s_extantIndetSubfamilies;
    }
    
    public String getAntwebSubfamily(String genus) 
      throws SQLException
    {
        String subfamily = "";
        String query = "select distinct subfamily from taxon where genus = " + lit(genus) + " and taxarank = 'genus'";
        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(getConnection(), "UploadDb.getAntwebSubfamily()");
          stmt.execute(query);
          rset = stmt.getResultSet();
          int count = 0;
          String debugSubfamilyList = "";
          while (rset.next()) {
            ++count;
            subfamily = rset.getString("subfamily");        

            if (count == 1) {
              debugSubfamilyList += subfamily;
            } else {
              debugSubfamilyList += ", " + subfamily;
            }
          }
          if (AntwebProps.isDevMode() && count == 0) A.iLog("getAntwebSubfamily() Problem? Not sure.  No subfamily for genus:" + genus + " subfamilySet:" + debugSubfamilyList);
          if (count > 1) s_log.error("getAntwebSubfamily() more than one subfamily for genus:" + genus + " subfamilySet:" + debugSubfamilyList);
        } finally {
          DBUtil.close(stmt, rset, "UploadDb.getAntwebSubfamily()");
        }      
        s_log.debug("getAntwebSubfamily() genus:" + genus + " subfamily:" + subfamily);
        return subfamily;
    }

    public static int getMaxSpecimenUploadId(Connection connection)
    {
      int maxUploadId = 0;
      Statement stmt = null;
      ResultSet rset = null;
      try {
        String query = "select max(upload_id) as uploadId from upload";
        stmt = DBUtil.getStatement(connection, "UploadDb.getMaxSpecimenUploadId()");
        stmt.execute(query);
        rset = stmt.getResultSet();
        while (rset.next()) {
          maxUploadId = rset.getInt("uploadId");        
        }
      } catch (SQLException e) {
        s_log.warn("getMaxUploadId() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, "UploadDb.getMaxSpecimenUploadId()");      
      }
      //A.log("getMaxSpecimenUploadId() id:" + maxUploadId);
      return maxUploadId;     
    }
}
