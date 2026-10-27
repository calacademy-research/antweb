package org.calacademy.antweb.util;

import java.sql.*;
import java.util.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class MuseumMgr {

    private static final Log s_log = LogFactory.getLog(MuseumMgr.class);

    private static ArrayList<Museum> s_museums;
        
    public static void populate(Connection connection) throws SQLException {
      populate(connection, false);
    }
    
    public static void populate(Connection connection, boolean forceReload) throws SQLException {
      if (!forceReload && s_museums != null) return;
      
      MuseumDb museumDb = new MuseumDb(connection);
      s_museums = museumDb.getMuseums(true); // deep copy
      
      // This will appear a string of nulls until museum table name is populated.  No problem.
      //A.log("populate() museums:" + s_museums);

      //s_log.warn("POPULATED");
    }

    public static ArrayList<Museum> getMuseums() {
      return s_museums;
    }

    /**
     * The number of collaborating institutions on the statistics page: the museums of the Museums list (museums.do
     * lists every museum of MuseumMgr). Brian Fisher, 2026-10-27 (docs/numbers.md B73): counted from that list, was
     * the fixed text "21". 0 until the list is loaded (in the minutes after a start).
     */
    public static int getMuseumCount() {
      return countOf(s_museums);
    }

    public static int countOf(java.util.List<Museum> museums) {
      return museums == null ? 0 : museums.size();
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

