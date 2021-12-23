package org.calacademy.antweb.curate.speciesList;

import java.sql.*;
import java.io.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.upload.*;

import org.apache.struts.upload.FormFile;

public class SpeciesListUploader {

  private static Log s_log = LogFactory.getLog(SpeciesListUploader.class);

  // Old link.
  //  public static String fetchWorldantsUrl = "http://ibss-info/antcat.antweb.txt";

  // New production link.
  //public static String fetchWorldantsUrl = "http://10.2.22.28:9090/antcat.antweb.txt";
  public static String fetchWorldantsUrl = "http://antcat-export:9090/antcat.antweb.txt";
  // See below to set a dev link.
   
/*
   We download the antcat.antweb.txt file from the above link to here:
     /data/antweb/web/speciesList/world/worldants_speciesList.txt
*/

  Connection connection = null;

  private static String worldDir = AntwebProps.getWebDir() + "speciesList/world/";

  public SpeciesListUploader(Connection connection) {  
    this.connection = connection;

    // For testing... cp /Users/mark/Downloads/antcatTest.txt /usr/local/tomcat/webapps/antweb/test.antcat.txt    
    if (false && AntwebProps.isDevMode()) {
      //fetchWorldantsUrl = "http://localhost/antweb/test.antcat.txt";  
      fetchWorldantsUrl = "http://localhost/antweb/web/upload/test_worldants_speciesList.txt";
    }
  }

  public UploadDetails worldantsFetchAndReload() throws AntwebException, IOException, SQLException {
  
    UploadDetails fetchDetails = fetchWorldantsList();
    if (!"success".equals(fetchDetails.getMessage())) {
      return fetchDetails;
    }

    UploadDetails uploadDetails = worldantsReload();  
    uploadDetails.setStartTime(fetchDetails.getStartTime());
    uploadDetails.setOperation("fetchAndReloadWorldants");    

    int worldantsChangeCount = (new WorldantsUploadDb(connection)).getWorldantsChangeCount();
    if (worldantsChangeCount == 0) {
      AdminAlertMgr.addIfFresh(AdminAlertMgr.noWorldantsChanges, AdminAlertMgr.noWorldantsChangesContains, AdminAlertMgr.WEEK, connection);
    }  
    
    return uploadDetails;    
  }

  public UploadDetails uploadWorldants(FormFile theFile, UploadFile uploadFile, Group accessGroup) throws AntwebException, IOException, SQLException {
    UploadDetails uploadDetails = null;
    UploadHelper.init(uploadFile, accessGroup);   

    SpeciesListUpload speciesListUpload = new SpeciesListUpload(connection);
    uploadDetails = speciesListUpload.uploadSpeciesList("worldants", theFile, uploadFile, accessGroup.getId());

   // (new WorldantsUploadDb(connection)).insertWorldantsUpload(backupFileName, backupFileSize, origWorldantsCount, validateMessage, fileSize);
    String fileLoc = worldDir + "worldants_speciesList.txt";
    int origWorldantsCount = (new TaxonDb(connection)).getWorldantsCount();
    String validateMessage = validateWorldantsFile(fileLoc, origWorldantsCount);
    String backupDirFile = record(validateMessage, fileLoc, origWorldantsCount, true);
    uploadDetails.setBackupDirFile(backupDirFile);
    uploadDetails.setMessage(validateMessage);
                
    return uploadDetails;  
  }

  public UploadDetails fetchWorldantsList() throws IOException, SQLException {

    UploadDetails uploadDetails = new UploadDetails("Fetch");  // This one will be thrown away but useful for return values.
    // A.log("execute()  in action:" + action);

	// Here we fetch.  Down below we will reloadSpeciesList for worldants. 
    Utility.makeDirTree(worldDir);
    String fileLoc = worldDir + "worldants_speciesList.txt";
    //String urlLoc = "http://localhost/antweb/web/speciesList/world/worldants_speciesList.txt";
    //String urlLoc = "http://antweb.org/web/speciesList/world/worldants_speciesList.txt";
    String urlLoc = fetchWorldantsUrl;

    boolean success = HttpUtil.writeUrlContents(urlLoc, fileLoc);                                  
    if (!success) {
	   String message = "Failed to fetch:" + urlLoc + " and write it here:" + fileLoc + ". Connected to VPN?";
       uploadDetails.setMessage(message);
       return uploadDetails;
    }
    
    s_log.warn("fetchWorldantsList() Worldants fetched:" + urlLoc + " written here:" + fileLoc + " successfully.");

    int origWorldantsCount = (new TaxonDb(connection)).getWorldantsCount();

    String validateMessage = validateWorldantsFile(fileLoc, origWorldantsCount);
    uploadDetails.setMessage(validateMessage);

    String backupDirFile = record(validateMessage, fileLoc, origWorldantsCount, false);
    uploadDetails.setBackupDirFile(backupDirFile);

    return uploadDetails;
  }

  public UploadDetails worldantsReload() throws AntwebException, IOException, SQLException {
    A.log("worldantsReload start");

	SpeciesListUpload speciesListUpload = new SpeciesListUpload(connection);
	UploadDetails uploadDetails = speciesListUpload.reloadSpeciesList(Project.WORLDANTS, Group.TESTGROUP);

    int origWorldantsCount = (new TaxonDb(connection)).getWorldantsCount();
    String fileLoc = worldDir + "worldants_speciesList.txt";
    String validateMessage = validateWorldantsFile(fileLoc, origWorldantsCount);
    uploadDetails.setMessage(validateMessage);
    
    String backupDirFile = record(validateMessage, fileLoc, origWorldantsCount, true);
    uploadDetails.setBackupDirFile(backupDirFile);

    A.log("worldantsReload message:" + uploadDetails.getMessage());

    return uploadDetails;
  }

  public String validateWorldantsFile(String fileLoc, int origWorldantsCount) {
    if (AntwebProps.isDevMode()) return "success";
    
    String message = "";
    int min_reasonable_worldants_count = 1000;
    
    int worldantsCount = (new AntwebSystem()).countLines(fileLoc);
    boolean countIsLow = true;

    int WORLDANTS_LOW_COUNT = 29000;
    if (worldantsCount > WORLDANTS_LOW_COUNT) countIsLow = false;
          
    /*
    // The existing count in the database is low. Waive the 
    boolean dbIsLow = (origWorldantsCount < min_reasonable_worldants_count);
    double allowedPercentage = 10;
    if (AntwebProps.isDevOrStageMode()) allowedPercentage = 50;
    double multiplier = allowedPercentage / 100;
    double max = origWorldantsCount + (origWorldantsCount * multiplier);
    double min = origWorldantsCount - (origWorldantsCount * multiplier);
    boolean newIsWithinTenPercent = (worldantsCount > min) && (worldantsCount < max);
    */
    
    // newIsWithinTenPercent = true;  // in order to override
    //A.log("validateWorldantsFile() details:" + details);

    if (countIsLow) { // && !newIsWithinTenPercent) {
      message = "Worldants count is low:" + worldantsCount + " origWorldantsCount:" + origWorldantsCount;
      //message = "Worldants download must be within " + allowedPercentage + "% of original.";
      //message += " original:" + origWorldantsCount + " new:" + worldantsCount;
      
      A.log("validateWorldantsFile() message:" + message); //allowedPercentage:" + allowedPercentage + " max:" + max + " min:" + min + " isWithin:" + newIsWithinTenPercent + " multiplier:" + multiplier);

    } else if (worldantsCount < min_reasonable_worldants_count) {
      message = "WorldantsCount is too small:" + worldantsCount;
    } else {
      message = "success";
    }

    //s_log.warn("validateWorldantsFile() worldantsCount:" + worldantsCount + " message:" + message);
    return message;
  }

  private String record(String validateMessage, String fileLoc, int origWorldantsCount, boolean persistToDb) 
    throws IOException, SQLException {
    // The uploaded files are dumped here: http://localhost/antweb/web/upload/
    String backupDirFile = null;
    
    int fileSize = FileUtil.getFileSize(fileLoc);
    String backupFileName = "";
    int backupFileSize = 0;
    if ("success".equals(validateMessage)) {	  
        // make backup copy                        
        backupDirFile = "upload/" + new Utility().getDateForFileName(new java.util.Date()) + "-worldants.txt";
        backupFileName = AntwebProps.getWebDir() + backupDirFile;
        new Utility().copyFile(fileLoc, backupFileName);
        backupFileSize = FileUtil.getFileSize(backupFileName);
    }

    if (persistToDb) {
      // we do not persist fetches     
      (new WorldantsUploadDb(connection)).insertWorldantsUpload(backupDirFile, backupFileSize, origWorldantsCount, validateMessage, fileSize);
    }
    
    return backupDirFile;
  }


}
