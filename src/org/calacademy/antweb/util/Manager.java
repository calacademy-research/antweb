package org.calacademy.antweb.util;

import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * Base class of some in-memory managers loaded from the database at start-up (TaxonMgr, ArtistMgr,
 * GeolocaleMgr, LoginMgr); AntwebMgr calls their static populate methods. Static methods are not overridden in
 * Java, so this populate is never reached through a subclass; it only logs an error and a stack trace if called
 * directly. No table of its own.
 */
public abstract class Manager {

    private static final Log s_log = LogFactory.getLog(Manager.class);

/*
    public static void populate(Connection connection) {
        populate(connection, false);
    }

    public static void populate(Connection connection, boolean forceReload) {
      populate(connection, forceReload, false);
    }
*/
    public static void populate(Connection connection, boolean forceReload, boolean initialRun) throws SQLException {
      // Effectively abstract.
      s_log.error("populate() should not be overridden, not executed.");
      AntwebUtil.logStackTrace();
    }

}