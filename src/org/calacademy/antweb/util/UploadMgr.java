package org.calacademy.antweb.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.home.UploadDb;
import org.calacademy.antweb.upload.Upload;

import java.sql.Connection;
import java.util.ArrayList;

public class UploadMgr {

  private static final Log s_log = LogFactory.getLog(UploadMgr.class);
  
  private static ArrayList<Upload> s_uploads = null;

    public static void populate(Connection connection, boolean forceReload) {
    
      //A.log("UploadMgr.populate() forceReload:" + forceReload);
      
      if (!forceReload && (s_uploads != null)) return;      
      
      UploadDb uploadDb = (new UploadDb(connection));

      s_uploads = uploadDb.getUploads();
    }  
  
    public static Upload getUpload(int id) {    
      AntwebMgr.isPopulated();
      for (Upload upload : s_uploads) {
          if (id == upload.getUploadId()) {
              A.log("UploadMgr.getUpload(" + id + ") logFileName:" + upload.getLogFileName());
              return upload;
          }
      }
      A.log("getUpload(" + id + ") not found");
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
      A.log("UploadMgr.log() s_uploads.size():" + s_uploads.size());
    }  
  
}


