package org.calacademy.antweb.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class ArtistMgr {

    private static final Log s_log = LogFactory.getLog(ArtistMgr.class);

    private static ArrayList<Artist> s_artists = null;
        
    public static void populate(Connection connection) {
      populate(connection, false);
    }
    
    public static void populate(Connection connection, boolean forceReload) {
      if (!forceReload && (s_artists != null)) return;      
      
      try {
        //A.log("populate()");
        ArtistDb artistDb = new ArtistDb(connection);
        s_artists = artistDb.getArtists();
      } catch (SQLException e) {
        s_log.warn("populate() e:" + e);
      }
      //A.log("GroupMgr.populate() groups:" + s_groups);
    }
    
    public static boolean isInitialized() {
      return s_artists != null;
    }

    public static ArrayList<Artist> getArtists() {
      return s_artists;
    }


    public static Artist getArtist(int id) {
      ArrayList<Artist> artists = getArtists();
      if (artists == null) return null;
      for (Artist artist : artists) {
        if (id == artist.getId()) return artist;
      }     
      return null;
    }
    public static Artist getArtist(String name) {
      ArrayList<Artist> artists = getArtists();
      if (artists == null) return null;
      for (Artist artist : artists) {       
        if (name.equals(artist.getName())) {
          //A.log("getArtist() name:" + name + " artist:" + artist);
          return artist;
        }
      }     
      return null;
    }

}
