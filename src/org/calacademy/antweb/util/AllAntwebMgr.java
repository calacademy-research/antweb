package org.calacademy.antweb.util;

import java.sql.*;
import java.util.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class AllAntwebMgr {

    private static final Log s_log = LogFactory.getLog(AllAntwebMgr.class);

    private static HashMap<String, ProjTaxon> s_allAntwebMap = null;
        
    public static void populate(Connection connection) {
      populate(connection, false);
    }
    
    public static void populate(Connection connection, boolean forceReload) {
      if (!forceReload && (s_allAntwebMap != null)) return;      
      s_allAntwebMap = new HashMap<>();
      
      ProjTaxonDb projTaxonDb = new ProjTaxonDb(connection);
      try {
        //A.log("populate()");
        ArrayList<ProjTaxon> allAntwebList = projTaxonDb.getProjTaxa(Project.ALLANTWEBANTS);
        for (ProjTaxon projTaxon : allAntwebList) {
          s_allAntwebMap.put(projTaxon.getTaxonName(), projTaxon);
        }
      } catch (SQLException e) {
        s_log.warn("populate() e:" + e);
      }
      //A.log("AllAntwebMgr.populate()");
    }

    public static ProjTaxon get(String taxonName) {
      ProjTaxon projTaxon = (ProjTaxon) s_allAntwebMap.get(taxonName);
      return projTaxon;
    }
}

/*
class TaxonInfo {
  private int childCount = 0;
  
  public int getChildCount() {
    return this.childCount;
  }
  public void setChildCount(int childCount) {
    this.childCount = childCount;
  }
}
*/