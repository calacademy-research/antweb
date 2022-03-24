package org.calacademy.antweb;

import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

public class Extent {

    private static Log s_log = LogFactory.getLog(Extent.class);

    float maxLat;
    float minLat;
    float maxLon;
    float minLon;

    public Extent() {
        super();
    }
    
    public Extent(float minLon, float minLat, float maxLon, float maxLat) {
        super();
        this.maxLat = maxLat;
        this.minLat = minLat;
        this.maxLon = maxLon;
        this.minLon = minLon;
    }
    
    public Extent(String extent) {
        extent = extent.trim();
        String[] parts = extent.split(" ");
        try {
          this.minLon = getExtentFloat(parts[0]);
          this.minLat = getExtentFloat(parts[1]);
          this.maxLon = getExtentFloat(parts[2]);
          this.maxLat = getExtentFloat(parts[3]);
        } catch (NumberFormatException e) {
          String message = "extent:" + extent + " e:" + e;
          s_log.error("Extent() " + message);
          AdminAlertMgr.log(message);                
            //+ " part0:" + parts[0] + " part1:" + parts[1] + " part2:" + parts[2] + " part3:" + parts[3]);
          //if (AntwebProps.isDevMode()) AntwebUtil.logStackTrace(e);
        }    
    }
    
    //public int getZoom() {
      
    //}

    /* this adds or subtracts to the extent by a given amount if the number given is 10,
     * it will add a 5% border to each side of the extent */
    public void modifyByPercent(int thePercent) {
        float latDiff = maxLat - minLat;
        float lonDiff = maxLon - minLon;
        
        float product = thePercent / 100.0f / 2.0f;
        
        float latIncrement = latDiff * product; 
        float lonIncrement = lonDiff * product;
        
        this.minLat =  minLat - latIncrement;
        this.maxLat = maxLat + latIncrement;
        
        this.minLon = minLon - lonIncrement;
        this.maxLon = maxLon + lonIncrement;
    }
    
    public void conform(int minWidth, int minHeight) {
        float latDiff = maxLat - minLat;
        float lonDiff = maxLon - minLon;
        
        float latMid = maxLat - latDiff / 2.0f;
        float lonMid = maxLon - lonDiff / 2.0f;
        
        
        if (latDiff < minHeight) {
            this.maxLat = latMid + minHeight / 2.0f;
            this.minLat = latMid - minHeight / 2.0f;
        }
        
        if (lonDiff < minWidth) {
            this.maxLon = lonMid + minWidth / 2.0f;
            this.minLon = lonMid - minWidth / 2.0f;
        }    
    }
    
    private float getExtentFloat(String s) {
      // Strings can contain ",".  Trim.
      s = s.replaceFirst(",", "");
      float f = Float.parseFloat(s);
      return f;
    }
    
    
/*
    // The salient code moved into a new constructor, above.
    public void fitProject(String project) {

        if (!((project == null) 
            || (project.equals(Project.WORLDANTS)) 
            || (project.equals(Project.ALLANTWEBANTS))  // MARK Sep 7 2011
            || (project.length() < 1))) {
            
            String projExtent = null;
            try {
              projExtent = AntwebProps.getProp(project + ".extent");
            } catch (java.util.MissingResourceException e) {
              s_log.error("fitProject() e:" + e + " on:" + project + ".extent");
              return;
            }

            if (projExtent == null) {
              s_log.error("fitProject() project:" + project + " extent not found.");
              return;                        
            }
            if (projExtent.contains(",")) {
              // africanants.extent and fijiants.extent contained ',' causing errors.  Hand modified
              // by mark on Nov 8, 2010.  If this error re-appears, then software is miswriting that file.
              s_log.error("!!! Does ProjectResources.properties contain , in the extents for project:" + project + "?  Fix.");
            }
            
            if ((projExtent == null) || ("".equals(projExtent))) {
                projExtent = AntwebProps.getProp("mapserver.extent.default");
            }
            // s_log.info("in fit project extent is " + projExtent);

            projExtent = projExtent.trim();
            String[] parts = projExtent.split(" ");
            try {
              this.minLon = getExtentFloat(parts[0]);
              this.minLat = getExtentFloat(parts[1]);
              this.maxLon = getExtentFloat(parts[2]);
              this.maxLat = getExtentFloat(parts[3]);
            } catch (NumberFormatException e) {
              String message = "e:" + e + " on projExtent:" + projExtent + " for project:" + project + " parts:" + parts;
              //s_log.error("fitProject() " + message);
              AdminUpdatesAction.add(message);
                
                //+ " part0:" + parts[0] + " part1:" + parts[1] + " part2:" + parts[2] + " part3:" + parts[3]);
              //if (AntwebProps.isDevMode()) AntwebUtil.logStackTrace(e);
            }
        }
    }
*/

    // this assumes it's being fed a query where the select contains columns
    // minlon, minlat, maxlon, maxlat 
    //
    public Extent(String theQuery, Connection connection) {
        
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                this.minLon = rset.getFloat("minlon");
                this.minLat = rset.getFloat("minlat");
                this.maxLon = rset.getFloat("maxlon");
                this.maxLat = rset.getFloat("maxlat");
            
                s_log.info(theQuery + " extent finds minlon " + minLon + " minlat " + minLat + " maxlon " + maxLon + " maxlat " + maxLat);
            }
        } catch (Exception e) {
            s_log.error("error in  getExtentFromDb " + e + " query:" + theQuery);
            AntwebUtil.logStackTrace(e);
        } finally {
            DBUtil.close(stmt, rset, this, "extent()");
        }
    }
    
    public boolean hasAllZeros() {
        return minLon == 0 && minLat == 0 && maxLon == 0 && maxLat == 0;
    }

    public float getMaxLat() {
        return maxLat;
    }
    public void setMaxLat(float maxLat) {
        this.maxLat = maxLat;
    }
    public float getMaxLon() {
        return maxLon;
    }
    public void setMaxLon(float maxLon) {
        this.maxLon = maxLon;
    }
    public float getMinLat() {
        return minLat;
    }
    public void setMinLat(float minLat) {
        this.minLat = minLat;
    }
    public float getMinLon() {
        return minLon;
    }
    public void setMinLon(float minLon) {
        this.minLon = minLon;
    }
    
    public String toString() {
        return this.minLon + "," + this.minLat + "," + this.maxLon + "," + this.maxLat;
    }

}
