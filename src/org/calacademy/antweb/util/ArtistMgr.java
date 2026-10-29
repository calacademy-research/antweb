package org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * In-memory list of the image artists (table artist, through ArtistDb), loaded by AntwebMgr; postInitialize
 * adds each artist's Images and Specimen numbers (isCounted is false until then, and the count check skips
 * artists). Used by the artist pages, image uploader and count checks. Docs: docs/numbers.md,
 * docs/data-model.md. Tests: tests/e2e/test_count_check.py.
 */
public class ArtistMgr extends Manager {

    private static final Log s_log = LogFactory.getLog(ArtistMgr.class);

    private static ArrayList<Artist> s_artists;
    
    public static void populate(Connection connection, boolean forceReload, boolean initialRun) {
      if (!forceReload && s_artists != null) return;
      
      try {
        //A.log("populate()");
        ArtistDb artistDb = new ArtistDb(connection);
        s_artists = artistDb.getArtists();
        s_counted = false;  // the Images and Specimen numbers come with postInitialize
      } catch (SQLException e) {
        s_log.warn("populate() e:" + e);
      }

      if (!initialRun) {
          try {
              postInitialize(connection);
          } catch (SQLException e) {
            s_log.warn("populate() e:" + e);
          }
      }

      //A.log("GroupMgr.populate() groups:" + s_groups);
    }

    //Called through UtilAction to, in a separate thread, populate the curators with adm1.
    public static void postInitialize(Connection connection) throws SQLException {
        ArtistDb artistDb = new ArtistDb(connection);
        ArrayList<Artist> artists = getArtists();
        artistDb.postInstantiate(artists);
        s_counted = true;
    }

    /*
     * True once the artists' Images and Specimen numbers are loaded (postInitialize). Between a reload and
     * postInitialize every artist shows 0; the count check skips the artists then (2026-10-24: a check during the
     * nightly run listed 584 artists as 0 while artists.do showed their numbers).
     */
    private static volatile boolean s_counted = false;

    public static boolean isCounted() {
      return s_counted;
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
