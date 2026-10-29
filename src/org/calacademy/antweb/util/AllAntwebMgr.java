package org.calacademy.antweb.util;

import java.sql.*;
import java.util.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * In-memory copy of the allantwebants project list (table proj_taxon, project allantwebants: every ant with
 * AntWeb data), keyed by taxon name; loaded by AntwebMgr at start-up and on util.do?action=reloadAntwebMgr.
 * Used by the taxon lists of places, bioregions and museums (GeolocaleTaxon, BioregionTaxon, OverviewTaxon,
 * TaxonSet) to look up a taxon's AntWeb-wide counts.
 */
public class AllAntwebMgr {

    private static final Log s_log = LogFactory.getLog(AllAntwebMgr.class);

    private static volatile HashMap<String, ProjTaxon> s_allAntwebMap;
        
    public static void populate(Connection connection) {
      populate(connection, false);
    }
    
    public static void populate(Connection connection, boolean forceReload) {
      if (!forceReload && s_allAntwebMap != null) return;
      // Filled first, then published: readers never see a half-filled map; on an error the last map is kept
      // (or none, so the next call tries again).
      HashMap<String, ProjTaxon> map = new HashMap<>();
      
      ProjTaxonDb projTaxonDb = new ProjTaxonDb(connection);
      try {
        //A.log("populate()");
        ArrayList<ProjTaxon> allAntwebList = projTaxonDb.getProjTaxa(Project.ALLANTWEBANTS);
        for (ProjTaxon projTaxon : allAntwebList) {
          map.put(projTaxon.getTaxonName(), projTaxon);
        }
        s_allAntwebMap = map;
      } catch (SQLException e) {
        s_log.warn("populate() e:" + e);
      }
      //A.log("AllAntwebMgr.populate()");
    }

    public static ProjTaxon get(String taxonName) {
        HashMap<String, ProjTaxon> map = s_allAntwebMap;
        return map == null ? null : map.get(taxonName);  // not loaded (yet): no entry, not a NullPointerException
    }
}
