package org.calacademy.antweb.util;

import java.util.*;
import java.util.Date;
import javax.servlet.ServletContext;
import java.sql.*;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
//import org.calacademy.antweb.util.*;

/**
 *  <p>This class is called by the Servlet Container on startup
 *  It initializes all app-scoped objects and places them into
 *  The servlet context for use by app components
 *
 *  @version 0.9
 *  @author <a href="mailto:max@codemonks.org">Max McCormick</a>
 */

public final class AppContextListener
    implements ServletContextListener {
        
    private static final Log s_log = LogFactory.getLog(AppContextListener.class);

    private static final Log s_antwebEventLog = LogFactory.getLog("antwebEventLog");
    
    public AppContextListener() {}
     
    private CacheTask cacheTask;
     
    public void contextDestroyed(ServletContextEvent event)
    {
        /** This method is invoked when the Web Application
            has been removed and is no longer able to accept
          requests
        */

        //AntwebUtil.logStackTrace();
        
        ServletContext ctx = event.getServletContext();
        
        s_log.warn("Removing Antweb application context...");

        String stats = AntwebUtil.getMemoryStats();
        s_log.warn("Memory stats - " + stats);

        s_log.info("GarbageCollection: " + AntwebUtil.timedGC());
        s_log.info("Memory stats - " + AntwebUtil.getMemoryStats());

        // Apparently we have to explicitly release some references in
        // the Apache Commons Logging library, otherwise it will hold onto
        // memory on each webapp unload/reload
        s_log.warn("------------- Terminating --------------");
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        LogFactory.release(contextClassLoader);

		// This manually deregisters JDBC driver, which prevents
		// Tomcat 7 from complaining about memory leaks wrto this class
		Enumeration drivers = DriverManager.getDrivers();
		while (drivers.hasMoreElements()) {
			Driver driver = (Driver) drivers.nextElement();
			try {
			  DriverManager.deregisterDriver(driver);
			  s_log.warn("deregistering jdbc driver:" + driver);
			} catch (SQLException e) {
			  s_log.warn("Error deregistering driver:" + driver);
			}
    	}

        s_antwebEventLog.info("Antweb Shutdown.  Stats:" + stats);

        //Output a simple message to the server's console
        System.out.println("--- Terminating Antweb application: " + new Date());

    }
    
    //This method is invoked when the Web Application
    //is ready to service requests
    public void contextInitialized(ServletContextEvent event)
    {
        boolean resourcesLoaded = AntwebProps.loadResources();
        if (!resourcesLoaded) contextDestroyed(event);

        String stats = AntwebUtil.getMemoryStats();
        s_log.warn("+++++ Server Initialized. Antweb Version: " + AntwebProps.getAntwebVersion() + "+++++");
        s_log.info("Memory stats - " + stats);

        s_antwebEventLog.info("Antweb Startup.  Stats:" + stats);

        // Can not use A.log or isDevMode or any AntwebProps here.

        LogMgr.startup();

        String message = LogMgr.archiveLogs();
    }
        

}

