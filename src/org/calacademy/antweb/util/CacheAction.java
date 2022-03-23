package org.calacademy.antweb.util;

import java.util.ArrayList;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;
import java.util.Date;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;    
    
/*
Cron jobs should be in place delete the cache and regenerate it, perhaps every morning.
Purge the database every week or so.	

15 1 * * * curl http://www.antweb.org/cache.do?action=genCacheThread

http://www.antweb.org/cache.do?action=genCacheThread
*/    
    
public final class CacheAction extends Action {

    private static Log s_log = LogFactory.getLog(CacheAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {
 
        DynaActionForm df = (DynaActionForm) form;        
        String action = (String) df.get("action");
        String url = (String) df.get("url");
        String orderBy = (String) df.get("orderBy");
        ActionForward returnVal = null; 

        Date startTime = new Date();

        if ((url != null) && (!"".equals(url))) {
          boolean success = getLongRequestDetails(request, url, orderBy);
		  if (success) {
			return (mapping.findForward("longRequestDetails"));
		  } else {
			return (mapping.findForward("failure"));
  		  }             
        }

        if (true) {
          request.setAttribute("message", "Caching turned off.");
          return (mapping.findForward("message"));        
        }

        if (action.equals("genCacheThread")) {
          genCacheThread(request);
          request.setAttribute("message", "genCacheThread completed.");
          return (mapping.findForward("message"));        
        }
        /*
        if (action.equals("keepCurrent")) {
          keepCurrent(request);
          request.setAttribute("message", "keepCurrent()  Old records and caches deleted.");
          return (mapping.findForward("message"));        
        }
        */
        if (action.equals("genCacheItem")) {
          returnVal = generateCacheItem(mapping, request);
          HttpUtil.finish(request, startTime);
          request.setAttribute("message", "genCacheItem completed.");
          return (mapping.findForward("message"));        
        } else {
          if (action.equals("forgetCaching")) {
            forgetCaching(request);
          }     
        /*
          if (action.equals("deleteCaches")) {
            deleteCaches(request);
          }                
        */
          if (action.equals("purgeCache")) {
            purgeCache(request);
          }        

          // if (action.equals("display")) { // this is the default
          boolean success = getLongRequests(request, orderBy);
		  if (success) {
			return (mapping.findForward("longRequests"));
		  } else {  
			return (mapping.findForward("failure"));
  		  }        
        }        
        //return (mapping.findForward("error"));
     }

    private void forgetCaching(HttpServletRequest request) {
		DataSource dataSource = getDataSource(request, "conPool");
        Connection connection = null;
        try {
            connection = DBUtil.getConnection(dataSource, "CacheAction.forgetCaching()");
            AntwebCacheMgr.forgetCaching(connection);    
        } catch (SQLException e) {
          s_log.error("forgetCaching() e:" + e);        
        } finally {
          DBUtil.close(connection, this, "CacheAction.forgetCaching()");
        }    
    }
    
/*
    private void keepCurrent(HttpServletRequest request) {
            AntwebCacheMgr.deleteOldLongRequests(connection);    
    }
    
    private void deleteCaches(HttpServletRequest request) {
            AntwebCacheMgr.deleteCaches(connection);    
    }
*/

    private void purgeCache(HttpServletRequest request) {
		DataSource dataSource = getDataSource(request, "conPool");
        Connection connection = null;
        try {
            connection = DBUtil.getConnection(dataSource, "CacheAction.purgeCache()");
            AntwebCacheMgr.deleteLongRequests(connection);
            AntwebCacheMgr.deleteCaches();            
        } catch (SQLException e) {
          s_log.error("purgesCaches() e:" + e);        
        } finally {
          DBUtil.close(connection, this, "CacheAction.purgeCache()");
        }    
    }

    private static int GEN_CACHE_THREAD_MINUTES = 10;
    private static int PAUSE_SECONDS = 2;
    private static int SLEEP_SECONDS = 60;
    
    private void genCacheThread(HttpServletRequest request) {
	  DataSource dataSource = getDataSource(request, "conPool");
      Connection connection = null;
      int didCacheCount = 0;
      
      Date startTime = new Date();
      s_log.info("getCacheThread() starting");

      if (true) return; // To turn off caching.

      try {
        connection = DBUtil.getConnection(dataSource, "CacheAction.genCacheThread()");

        int i = 0;
        while(true){
          ++i;          
          AntwebUtil.sleep(PAUSE_SECONDS);

          AntwebCacheMgr.deleteOldLongRequests(connection);

          //s_log.info("invoking genCacheItem()");
          boolean didCache = AntwebCacheMgr.genCacheItem(connection);
          if (!didCache) {
            // We can cache more later.  Keep running, but take a break;
            s_log.info("genCacheThread() sleep");
            AntwebUtil.sleep(SLEEP_SECONDS);
          } else {
            ++didCacheCount;
          }
          
          long millis = AntwebUtil.millisSince(startTime);

          if (AntwebProps.isDevOrStageMode()) if (millis > 1000 * 60) break;

          if (millis > 1000 * 60 * GEN_CACHE_THREAD_MINUTES) {
            s_log.info("getCacheThread() ending");
            break;
          }
        } // end while

      } catch (SQLException e) {
        s_log.error("genCacheThread() e:" + e);     
        AntwebUtil.logStackTrace(e);   
      } finally {

       boolean isClosed = false;       
       try {
         isClosed = connection.isClosed();
       } catch (SQLException e2) {
         s_log.warn("e:" + e2);      
       }
       
        DBUtil.close(connection, this, "CacheAction.genCacheThread()");

       try {
         isClosed = connection.isClosed();
         if (!isClosed) s_log.warn("NOT CLOSED CONNECTION conn:" + connection);
       } catch (SQLException e2) {
         s_log.warn("e:" + e2);      
       }


      }
      if (didCacheCount >= 1) {
        s_log.warn("genCacheThread() completion.  didCache:" + didCacheCount);
      }  
    }


     private boolean getLongRequests(HttpServletRequest request, String orderBy) {
        boolean success = false;
		DataSource dataSource = getDataSource(request, "conPool");
        Connection connection = null;
        try {
            connection = DBUtil.getConnection(dataSource, "CacheAction.getLongRequests()");
            ArrayList longRequests = AntwebCacheMgr.getLongRequestDetails(connection, "all", orderBy);            
            request.setAttribute("longRequests", longRequests);
            success = true;
        } catch (SQLException e) {
          s_log.error("getLongRequests() e:" + e);        
        } finally {
          DBUtil.close(connection, this, "CacheAction.getLongRequests()");
        }
        return success;
     }


     private boolean getLongRequestDetails(HttpServletRequest request, String url, String orderBy) {
        boolean success = false;
		DataSource dataSource = getDataSource(request, "conPool");
        Connection connection = null;
        try {
            connection = DBUtil.getConnection(dataSource, "CacheAction.getLongRequestDetails()");
            ArrayList longRequests = AntwebCacheMgr.getLongRequestDetails(connection, url, orderBy);    
            request.setAttribute("longRequests", longRequests);
            success = true;
        } catch (SQLException e) {
          s_log.error("getLongRequestDetails() e:" + e);        
        } finally {
          DBUtil.close(connection, this, "CacheAction.getLongRequestDetails()");
        }
        return success;
     }

     private ActionForward generateCacheItem(ActionMapping mapping, HttpServletRequest request) {
        boolean isLoggedIn = LoginMgr.isLoggedIn(request);
        
        if (!isLoggedIn) { 
          // This function must not be logged in in order to generate the proper pages...

            Connection connection = null;
            try {
              DataSource dataSource = getDataSource(request, "conPool");
              connection = DBUtil.getConnection(dataSource, "CacheAction.generateCacheItem()");

              AntwebCacheMgr.genCacheItem(connection);

            } catch (SQLException e) {
              String message = e.toString();
              s_log.error("generateCacheItem() e:" + message);
              request.setAttribute("message", message);
              request.setAttribute("header", "Cache Exception");
              return mapping.findForward("message");              
            } finally {
              DBUtil.close(connection, this, "CacheAction.generateCacheItem()");
            }          
                    
          request.setAttribute("message", "Cache item generated. Back to <a href=\"" + AntwebProps.getDomainApp() + "/cache.do?action=display\">CacheMgr</a>.");
          request.setAttribute("header", "Cache item generated.");          
          return (mapping.findForward("message"));
        } else {
          request.setAttribute("message", "The Antweb cache generation functions will not function if you are logged in.");
          return (mapping.findForward("message"));        
        }
	}
}
