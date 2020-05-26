package org.calacademy.antweb.util;

import java.sql.Connection;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

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
    public static void populate(Connection connection, boolean forceReload, boolean initialRun) {
      // Effectively abstract.
      s_log.error("populate() should not be overridden, not executed.");
      AntwebUtil.logStackTrace();
    }

}