package org.calacademy.antweb.util;

import java.util.*;

import java.sql.*;

import org.calacademy.antweb.home.*;
import org.calacademy.antweb.upload.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * In-memory list of the specimen uploads (table upload, through UploadDb.getUploads), loaded by AntwebMgr at
 * start-up and on util.do?action=reloadAntwebMgr; getUpload(id) and hasUpload(id) are used by the upload report
 * links (UploadReportAction, specimen page, Upload). No writes. Docs: docs/flows/specimen-upload.md,
 * docs/data-model.md.
 */
public class UploadMgr {

  private static final Log s_log = LogFactory.getLog(UploadMgr.class);
  
  private static ArrayList<Upload> s_uploads;

    public static void populate(Connection connection, boolean forceReload) {
    
      //A.log("UploadMgr.populate() forceReload:" + forceReload);
      
      if (!forceReload && s_uploads != null) return;
      
      UploadDb uploadDb = new UploadDb(connection);

      s_uploads = uploadDb.getUploads();
    }  
  
    public static Upload getUpload(int id) {    
      AntwebMgr.isPopulated();
      for (Upload upload : s_uploads) {
          if (id == upload.getUploadId()) {
              s_log.debug("UploadMgr.getUpload(" + id + ") logFileName:" + upload.getLogFileName());
              return upload;
          }
      }
      s_log.debug("getUpload(" + id + ") not found");
      return null;
    }  
  
    public static boolean hasUpload(int id) {
      AntwebMgr.isPopulated();
      for (Upload upload : s_uploads) {
          if (id == upload.getUploadId()) {
              return true;
          }
      }
      return false;
    }  
  
  
    public static void log() {
      s_log.debug("UploadMgr.log() s_uploads.size():" + s_uploads.size());
    }  
  
}


