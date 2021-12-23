package org.calacademy.antweb.home;

import java.util.*;

import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.util.*;


public class UploadDb extends AntwebDb {

    private static Log s_log = LogFactory.getLog(UploadDb.class);

    public UploadDb(Connection connection) {
      super(connection);
    }

    public Upload getUpload(int uploadId) throws SQLException {
      Upload upload = null;
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getUpload()");

        String query = "select *" 
          + " from upload " 
          + " where upload_id = " + uploadId;
        rset = stmt.executeQuery(query);

        while (rset.next()) {
          upload = new Upload();
          upload.setId(rset.getInt("id"));
          upload.setUploadId(rset.getInt("upload_id"));
          upload.setLoginId(rset.getInt("login_id"));
          upload.setGroupName(rset.getString("group_name"));
          upload.setGroupId(rset.getInt("group_id"));
          upload.setLogFileName(rset.getString("log_file_name"));
          upload.setCreated(rset.getDate("created"));

		  upload.setSpecimens(rset.getInt("specimens"));
		  upload.setCollections(rset.getInt("collections"));
		  upload.setLocalities(rset.getInt("localities"));
		  upload.setSubfamilies(rset.getInt("subfamilies"));
		  upload.setGenera(rset.getInt("genera"));
		  upload.setSpecies(rset.getInt("species"));
		  upload.setUngeoreferenced(rset.getInt("ungeoreferenced"));
		  upload.setFlagged(rset.getInt("flagged"));        }

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
              upload.setCreated(rset.getDate("created"));  
                
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
              + "values ('" + fileName + "', " + lineNum + ", " + displayLineNum + ", '" + AntFormatter.escapeQuotes(theLine) + "', " + groupId + ")";  
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
        Statement stmt = null;
        ResultSet rset = null;
        String query = "select * from upload_line where file_name = '" + fileName + "' and line_num = " + lineNum;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getUploadLines()");
            rset = stmt.executeQuery(query);
            s_log.debug("getUploadLine() query:" + query);
            
            while (rset.next()) {
              UploadLine uploadLine = new UploadLine();
			  uploadLine.setId(rset.getInt("id"));
			  uploadLine.setFileName(rset.getString("file_name"));
			  uploadLine.setLineNum(rset.getInt("line_num"));
			  uploadLine.setDisplayLineNum(rset.getInt("display_line_num"));
			  uploadLine.setGroupId(rset.getInt("group_id"));
			  uploadLine.setLine(rset.getString("line"));
              uploadLine.setCreated(rset.getDate("created"));  
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

    private Upload getCounts(Upload upload) throws SQLException {
        //A.log("getCounts() upload:" + upload.getId() + AntwebUtil.getShortStackTrace());  // Why three times?
        if (upload == null) return null;

        Statement stmt = null;
        ResultSet rset = null;
        String query = "select count(code) specimens" 
          + " , count(distinct collectioncode) collections"
          + " , count(distinct localitycode) localities"
          + " , count(if(decimal_latitude is null, 1, null)) ungeoreferenced" 
          + " from specimen where access_group = " + upload.getGroupId()
          + " and " + SpecimenDb.getFlagCriteria()
          + " and " + SpecimenDb.getStatusCriteria()
          + " and " + SpecimenDb.getTaxaCriteria()
          ;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getCounts()");
            //A.log("getCounts() groupId:" + upload.getGroupId() + " query:" + query);
            rset = stmt.executeQuery(query);

            while (rset.next()) {
			  upload.setSpecimens(rset.getInt("specimens"));
			  upload.setCollections(rset.getInt("collections"));
			  upload.setLocalities(rset.getInt("localities"));
			  upload.setUngeoreferenced(rset.getInt("ungeoreferenced"));
			}
        } catch (SQLException e) {
            s_log.warn("getCounts() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "getCounts()");
        }
        
        query = "select "
          + "   count(distinct subfamily) subfamilies" 
          + " , count(distinct genus) genera" 
          + " , count(distinct species) species" 
          + " from specimen where access_group = " + upload.getGroupId()
          + " and " + SpecimenDb.getFlagCriteria()
          + " and " + SpecimenDb.getStatusCriteria()
          + " and " + SpecimenDb.getTaxaCriteria()
          + " and " + SpecimenDb.getAntwebSubfamilyCriteria()
          + " and " + SpecimenDb.getAntwebGenusCriteria()
          ;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getCounts()");
            //A.log("getCounts() groupId:" + upload.getGroupId() + " query:" + query);
            rset = stmt.executeQuery(query);

            while (rset.next()) {
			  upload.setSubfamilies(rset.getInt("subfamilies"));
			  upload.setGenera(rset.getInt("genera"));
			  upload.setSpecies(rset.getInt("species"));
			}
        } catch (SQLException e) {
            s_log.warn("getCounts() 2 e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "getCounts()");
        }
        
        //A.log("getCounts() query:" + query);
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
        Upload upload = null;
        int uploadId = 0;
        Statement stmt = null;
        ResultSet rset = null;
        String query = "select max(upload_id) upload_id from upload where group_id = " + groupId;

        try {
            stmt = DBUtil.getStatement(getConnection(), "getLastUploadByGroup()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
              uploadId = rset.getInt("upload_id");
			}
        } catch (SQLException e) {
            s_log.warn("getLastUploadId() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "getLastUploadByGroup()");        
        }
        
		if (uploadId > 0) upload = getUpload(uploadId);
		
        //if (upload != null) A.slog("getLastUploadId() uploadId:" + uploadId + " upload:" + upload.getId() + " specimens:" + upload.getSpecimens() + " query:" + query);

        return upload;
    }   
    public Upload getLastUploadByLogin(int loginId) throws SQLException {
        Upload upload = null;
        int uploadId = 0;
        Statement stmt = null;
        ResultSet rset = null;
        String query = "select max(upload_id) upload_id from upload where login_id = " + loginId;

        try {
            stmt = DBUtil.getStatement(getConnection(), "getLastUploadByLogin()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
              uploadId = rset.getInt("upload_id");
			}
        } catch (SQLException e) {
            s_log.warn("getLastUploadId() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "getLastUploadByLogin()");        
        }
        
		if (uploadId > 0) upload = getUpload(uploadId);
		
        //if (upload != null) A.slog("getLastUploadId() uploadId:" + uploadId + " upload:" + upload.getId() + " specimens:" + upload.getSpecimens() + " query:" + query);

        return upload;
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
			  + " where upload_id = " + uploadId;

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

    public void updateUpload(Login accessLogin, String logFileName) {
        //s_log.warn("updateUpload()");
        Group accessGroup = accessLogin.getGroup();
        String insert = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateUpload()");
            insert = "insert into upload(upload_id, login_id, group_name, group_id, log_file_name) " 
              + "values (" + AntwebMgr.getNextSpecimenUploadId() + ", " + accessLogin.getId() + ", \"" + accessGroup.getName() + "\", " + accessGroup.getId() + ", \"" + logFileName + "\")";  
            stmt.executeUpdate(insert);
        } catch (SQLException e) {
            s_log.error("updateUpload() logFileName:" + logFileName + " e:" + e);
        } finally {
           DBUtil.close(stmt, null, this, "updateUpload()");        
        }
        
        updateCounts(accessGroup.getId());
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
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "UploadDb.insertDescription()");      
    
            dml = "delete from " + table + " where taxon_name = '" + taxonName
              + "' and title = 'taxonomichistory'"; 
            if (authorDate != null) dml += " and author_date = '" + authorDate + "'";  
              
            //s_log.warn("insertDescription() dml:" + dml);
            stmt.executeUpdate(dml);

            //s_log.info("insertDescription() query:" +  query);
        
            stmt = getConnection().createStatement();
            dml = "insert into " + table + " (taxon_name,";
            if (authorDate != null) dml += " author_date,";  
            dml += " title, content, is_manual_entry) " + " values ('" + taxonName + "', ";
            if (authorDate != null) dml += " '" + authorDate + "',"; 
            dml += " '" + title  + "', '" + content + "', 0" +  ")";
            stmt.executeUpdate(dml);
            

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
      
        Taxon dummyTaxon = (new TaxonDb(getConnection())).getTaxon(taxonName);
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

This work should clear this integrity report: https://www.antweb.org/util.do?action=curiousQuery&name=geolocaleTaxaWithoutTaxon

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
        
        dml = "insert into taxon (taxon_name, family, subfamily, genus, taxarank, source, insert_method, status, access_group) "
            + " values ('" + taxonName + "', '" + family + "', '" + subfamily + "', '" + genus + "', 'genus', '" 
            + source + "', '" + insertMethod + "', '" + status + "', " + groupId + ")";

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
            + " values ('" + taxonName + "', '" + family + "', '" + subfamily + "', 'subfamily', '" 
            + source + "', '" + insertMethod + "', '" + family + "', '" + status + "')";            
        //A.log("insertSubfamily() insert dml:" + dml);
        stmt = DBUtil.getStatement(getConnection(), "UploadDb.insertSubfamily()");      
        c = stmt.executeUpdate(dml);

        stmt = getConnection().createStatement();
        dml = "delete from proj_taxon where project_name = '" + source + "' and taxon_name = '" + taxonName + "'";
        //A.log("insertSubfamily() delete proj_taxon dml:" + dml);
        stmt.executeUpdate(dml);

        (new ProjTaxonDb(getConnection())).insert(source, taxonName, "insertSubfamily");

      } catch (SQLException e) {
        // This will happen in cases where the records are not sequential.  It is OK.
        s_log.warn("insertSubfamily() Not a problem.  into proj_taxon.  For taxonName:" + taxonName + " theStatement:" + dml + " e:" + e);
      } finally {
        DBUtil.close(stmt, "UploadDb.insertSubfamily()");
      } 
      return c;
    }

    private static ArrayList<String> s_extantIndetSubfamilies = null;
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
        return s_extantIndetSubfamilies;    
      } else return s_extantIndetSubfamilies;
    }
    
    public String getAntwebSubfamily(String genus) 
      throws SQLException
    {
        String subfamily = "";
        String query = "select distinct subfamily from taxon where genus = '" + genus + "' and taxarank = 'genus'";
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
          if (AntwebProps.isDevMode() && count == 0) s_log.error("getAntwebSubfamily() Problem? Not sure.  No subfamily for genus:" + genus + " subfamilySet:" + debugSubfamilyList);
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
