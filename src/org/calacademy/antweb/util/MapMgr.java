package org.calacademy.antweb.util;

import java.util.*;
import java.sql.Connection;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Map;
import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.apache.struts.action.*;

public abstract class MapMgr {

    private static final Log s_log = LogFactory.getLog(MapMgr.class);

    private static HashMap<String, Map> s_maps = new HashMap<>();
    
    private static int skipped = 0;

    public static Map getMap(Taxon taxon, LocalityOverview overview, Connection connection) {

        boolean persist = (taxon instanceof Subfamily || taxon instanceof Genus); // They will be geolocaleFocus == false.
        Map map = null;
        if (persist) {
            map = MapMgr.getMap(taxon);
            //A.log("getMap() map:" + map);
            if (map == null) {
              map = new Map(taxon, overview, connection);
              MapMgr.setMap(taxon, map);
            } else {
              ++skipped;
            }                  
        } else {
          map = new Map(taxon, overview, connection);
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