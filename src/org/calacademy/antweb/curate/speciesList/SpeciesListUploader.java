package org.calacademy.antweb.curate.speciesList;

import java.sql.*;
import java.io.*;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.upload.*;

import org.apache.struts.upload.FormFile;

/**
 * The AntCat names import ("worldants"): downloads antcat.antweb.txt from the antcat-export server (port 9090)
 * to web/speciesList/world/, checks its line count (not on dev or staging), keeps a dated copy in web/upload/
 * and reloads the names (SpeciesListUpload.reloadSpeciesList; table worldants_upload records each run).
 * Callers: nightly set 1 (UtilDataAction, with prefetchWorldantsList before set 1) and "Import AntCat names
 * now" (UploadAction). A nightly failure adds an admin alert and emails. Docs: docs/flows/antcat-to-antweb.md,
 * docs/flows/nightly.md. Tests: tests/unit/test_set1_prefetch.py, test/.../util/FetchRetryTest.java.
 */
public class SpeciesListUploader {

  private static final Log s_log = LogFactory.getLog(SpeciesListUploader.class);

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

  final Connection connection;

  private static final String worldDir = AntwebProps.getWebDir() + "speciesList/world/";

  public SpeciesListUploader(Connection connection) {  
    this.connection = connection;

    // For testing... cp /Users/mark/Downloads/antcatTest.txt /usr/local/tomcat/webapps/antweb/test.antcat.txt    
    if (false && AntwebProps.isDevMode()) {
      //fetchWorldantsUrl = "http://localhost/antweb/test.antcat.txt";  
      fetchWorldantsUrl = "http://localhost/antweb/web/upload/test_worldants_speciesList.txt";
    }
  }

  public UploadDetails worldantsFetchAndReload() throws AntwebException, IOException, SQLException {
    return worldantsFetchAndReload(false);
  }

  /**
   * nightly: the 05:00 set 1, which uses the file prefetchWorldantsList downloaded before set 1 took its connection
   * (up to an hour of tries) and alerts and emails when there is none; admin runs download once.
   */
  public UploadDetails worldantsFetchAndReload(boolean nightly) throws AntwebException, IOException, SQLException {

    UploadDetails fetchDetails = fetchWorldantsList(nightly);
    if (!"success".equals(fetchDetails.getMessage())) {
      return fetchDetails;
    }

    UploadDetails uploadDetails = worldantsReload();  
    uploadDetails.setStartTime(fetchDetails.getStartTime());
    uploadDetails.setOperation("fetchAndReloadWorldants");    

    int worldantsChangeCount = new WorldantsUploadDb(connection).getWorldantsChangeCount();
    if (worldantsChangeCount == 0) {
      AdminAlertMgr.addIfFresh(AdminAlertMgr.noWorldantsChanges, AdminAlertMgr.noWorldantsChangesContains, AdminAlertMgr.WEEK, connection);
    }  
    
    return uploadDetails;    
  }

  public UploadDetails fetchWorldantsList() throws IOException, SQLException {
    return fetchWorldantsList(false);
  }

  /** The nightly's download before set 1 takes its connection (null until the first nightly since start-up). */
  private static volatile FetchRetry.Result s_lastPrefetch = null;
  private static volatile long s_lastPrefetchAt = 0;

  /**
   * The nightly download (2026-10-25): tries every 10 minutes and makes no new try once an hour has passed
   * (FetchRetry). Called by UtilDataAction.execute for the scheduler's set 1 BEFORE set 1 takes its database
   * connection: the long pool closes a connection held over 60 minutes and set 1 already takes up to an hour.
   * Writes the same file set 1 reads; set 1 then fetches once more (one try) as before.
   */
  public static FetchRetry.Result prefetchWorldantsList() {
    String fileLoc = worldDir + "worldants_speciesList.txt";
    String urlLoc = fetchWorldantsUrl;
    FetchRetry.Result result = FetchRetry.run(() -> {
        String error = HttpUtil.downloadToFile(urlLoc, fileLoc);
        if (error != null) s_log.warn("prefetchWorldantsList() " + urlLoc + " failed: " + error);
        return error;
      }, FetchRetry.NIGHTLY_DEADLINE_MS, FetchRetry.NIGHTLY_WAIT_MS, FetchRetry.SYSTEM_CLOCK, FetchRetry.THREAD_SLEEP);
    s_lastPrefetch = result;
    s_lastPrefetchAt = System.currentTimeMillis();
    s_log.warn("prefetchWorldantsList() " + (result.ok() ? "fetched at try " + result.attempts : FetchRetry.failureMessage(urlLoc, result)));
    return result;
  }

  /**
   * nightly: the 05:00 set 1. Uses the file prefetchWorldantsList fetched within the last 3 hours (or its failure);
   * without one (set 1 started another way) it downloads once. Alert and email when there is no file.
   * Admin runs: one download, no alert.
   */
  public UploadDetails fetchWorldantsList(boolean nightly) throws IOException, SQLException {

    UploadDetails uploadDetails = new UploadDetails("Fetch");  // This one will be thrown away but useful for return values.
    // A.log("execute()  in action:" + action);

    String fileLoc = worldDir + "worldants_speciesList.txt";
    String urlLoc = fetchWorldantsUrl;

    // The nightly downloaded the file just before set 1 (prefetchWorldantsList): use it, no second download.
    FetchRetry.Result before = s_lastPrefetch;
    boolean recent = nightly && before != null && System.currentTimeMillis() - s_lastPrefetchAt < 3L * 3600 * 1000;
    FetchRetry.Result result = recent ? before : FetchRetry.once(() -> HttpUtil.downloadToFile(urlLoc, fileLoc));
    if (!result.ok()) {
       String message = FetchRetry.failureMessage(urlLoc, result);
       uploadDetails.setMessage(message);
       if (nightly) notifyNightlyFailure(message, connection);
       return uploadDetails;
    }
    
    s_log.warn("fetchWorldantsList() Worldants fetched:" + urlLoc + " written here:" + fileLoc + " successfully.");

    int origWorldantsCount = new TaxonDb(connection).getWorldantsCount();

    String validateMessage = validateWorldantsFile(fileLoc, origWorldantsCount);
    uploadDetails.setMessage(validateMessage);

    String backupDirFile = record(validateMessage, fileLoc, origWorldantsCount, false);
    uploadDetails.setBackupDirFile(backupDirFile);

    return uploadDetails;
  }

  public UploadDetails worldantsReload() throws AntwebException, IOException, SQLException {
    A.log("worldantsReload start");

    // The count before the reload: worldants_upload.orig_worldants_count (it was read after the reload).
    int origWorldantsCount = new TaxonDb(connection).getWorldantsCount();

	SpeciesListUpload speciesListUpload = new SpeciesListUpload(connection);
	UploadDetails uploadDetails = speciesListUpload.reloadSpeciesList(Project.WORLDANTS, Group.TESTGROUP);
    String fileLoc = worldDir + "worldants_speciesList.txt";


   // String validateMessage = validateWorldantsFile(fileLoc, origWorldantsCount);
   // A.log("Must we do this? uploadDetails.getMessage():" + uploadDetails.getMessage() + " We already validated in the reloadSpecieslist() above. ValidateMessage:" + validateMessage);
   // uploadDetails.setMessage(validateMessage);


    String backupDirFile = record(uploadDetails.getMessage(), fileLoc, origWorldantsCount, true);  // was validateMessage
    uploadDetails.setBackupDirFile(backupDirFile);

    s_log.debug("worldantsReload message:" + uploadDetails.getMessage());

    return uploadDetails;
  }

  public static final int WORLDANTS_LOW_COUNT = 29000;

  public String validateWorldantsFile(String fileLoc, int origWorldantsCount) {
    if (AntwebProps.isDevOrStageMode()) {
      A.log("validateWorldantsFile() DEV MODE SKIPPING (so that short files can be tested)");
      AntwebUtil.logShortStackTrace();
      return "success";
    }
    
    String message = "";
    int min_reasonable_worldants_count = 1000;
    
    int worldantsCount = AntwebSystem.countLines(fileLoc);
    A.log("validateWorldantsFile() worldantsCount:" + worldantsCount);

    boolean countIsLow = worldantsCount <= WORLDANTS_LOW_COUNT;

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
      
      s_log.warn("validateWorldantsFile() message:" + message); //allowedPercentage:" + allowedPercentage + " max:" + max + " min:" + min + " isWithin:" + newIsWithinTenPercent + " multiplier:" + multiplier);

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
        backupDirFile = "upload/" + Utility.getDateForFileName(new Date()) + "-worldants.txt";
        backupFileName = AntwebProps.getWebDir() + backupDirFile;
        Utility.copyFile(fileLoc, backupFileName);
        backupFileSize = FileUtil.getFileSize(backupFileName);
    }

    if (persistToDb) {
      // we do not persist fetches     
      new WorldantsUploadDb(connection).insertWorldantsUpload(backupDirFile, backupFileSize, origWorldantsCount, validateMessage, fileSize);
    }
    
    return backupDirFile;
  }



  /**
   * The nightly could not fetch the AntCat file: an admin alert and an email. Never throws (a failed notice
   * must not abort set 1: no TaxonWorks upload, no name update).
   */
  static void notifyNightlyFailure(String message, Connection connection) {
    try {
      AdminAlertMgr.add("AntCat import: " + message, connection);
    } catch (Exception e) {
      s_log.warn("notifyNightlyFailure() admin alert not added: " + e);
    }
    try {
      Emailer.sendMail(AntwebUtil.getDevEmail(), "AntWeb: tonight's AntCat import could not fetch the AntCat file",
        message + "\n\nThe name update was skipped tonight. AntWeb tries again at the next nightly (05:00);"
        + " an admin can also run \"Import AntCat names now\" on the Technical page. On antcat-export check:"
        + " ls -l /root/antcat/database_export/antcat.antweb.txt; ss -ltnp | grep 9090");
    } catch (Exception e) {
      s_log.warn("notifyNightlyFailure() email not sent: " + e);
    }
  }
}
