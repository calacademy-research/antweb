package org.calacademy.antweb.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.Museum;
import org.calacademy.antweb.home.MuseumDb;

import java.sql.Connection;
import java.util.ArrayList;

public class MuseumMgr {

    private static final Log s_log = LogFactory.getLog(MuseumMgr.class);

    private static ArrayList<Museum> s_museums = null;
        
    public static void populate(Connection connection) {
      populate(connection, false);
    }
    
    public static void populate(Connection connection, boolean forceReload) {
      if (!forceReload && (s_museums != null)) return;      
      
      MuseumDb museumDb = (new MuseumDb(connection));
      s_museums = museumDb.getMuseums(true); // deep copy
      
      // This will appear a string of nulls until museum table name is populated.  No problem.
      //A.log("populate() museums:" + s_museums);

      //s_log.warn("POPULATED");
    }

    public static ArrayList<Museum> getMuseums() {
      return s_museums;
    }

/*
    public static Museum getMuseum(int id) {
      for (Museum museum : getMuseums()) {
        if (museum.getId() == id) return museum;
      }
      
      A.log("getMuseum(" + id + ") not found.  size:" + getMuseums().size());
      
      return null;
    }
*/

    public static Museum getMuseum(String code) {
      if (code == null) return null;
      ArrayList<Museum> museums = getMuseums();  
      if (museums == null) return null;    
      for (Museum museum : museums) {
        if (code.equals(museum.getCode())) return museum;
      }
      
      return null;
    }

    public static Museum getInferredMuseum(String str) {
      if (str == null) return null;
      ArrayList<Museum> museums = getMuseums();  
      if (museums == null) return null;    
      for (Museum museum : museums) {
        if (str.contains(museum.getCode())) return museum;
      }
      return null;      
    }
}

