package org.calacademy.antweb.util;

import java.util.*;
import java.sql.Connection;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Map;
import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public abstract class MapMgr {

    private static final Log s_log = LogFactory.getLog(MapMgr.class);

    private static HashMap<String, Map> s_maps = new HashMap<>();
    
    private static int skipped = 0;

    public static Map getMap(Taxon taxon, LocalityOverview overview, Connection connection) {
        Map map = null;

        boolean persist = taxon.isSubfamily() || taxon.isGenus(); // They will be geolocaleFocus == false.
        persist = false;  // Effectively, turn MapMgr off.
        if (persist) {
            map = MapMgr.getMap(taxon); // NO! Must be locality specific.
            A.log("getMap() class:" + taxon.getClass() + " taxonName:" + taxon.getTaxonName() + " persist:" + persist + " MUST BE LOCALITY SPECIFIC map:" + map);
            if (map == null) {
              map = new Map(taxon, overview, connection);
              MapMgr.setMap(taxon, map);
            } else {
              ++skipped;
            }                  
        } else {
            Date before = new Date();
            map = new Map(taxon, overview, connection);
            //A.log("getMap() millis:" + AntwebUtil.millisSince(before));
        }

        //A.log("execute() persist:" + persist + " taxon:" + taxon + " map:" + map);

        return map;
    }

    public static Map getMap(Taxon taxon) {
      if (AntwebProps.isDevMode()) report();
      return s_maps.get(taxon.getTaxonName()); 
    }

    public static void setMap(Taxon taxon, Map map) {
      map.setCached(new Date());
      if (!s_maps.containsKey(taxon.getTaxonName())) s_maps.put(taxon.getTaxonName(), map);
    }

    public static void removeMap(String taxonName) {
      s_maps.remove(taxonName);
    }

    public static void refresh() {
      skipped = 0;
      s_maps = new HashMap<>();
    }
    
    public static String report() {
      return "size:" + s_maps.size() + " skipped:" + skipped;    
    }
}