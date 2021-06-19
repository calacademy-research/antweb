package org.calacademy.antweb.util;

import java.util.*;
import java.util.Collection;

import org.apache.struts.action.*;
import javax.servlet.http.*;
import org.calacademy.antweb.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class PageTracker {

/*
At the bottom of each page, if logged in as a developer, we can see all of the requests
that the server is currently handling. The idea is that pages are added to the PageTracker
in SessionRequestFilter.doFilter() - pretty much all pages. And then removed at the last
possible moment - ideally in jsp footer.
*/

    private static boolean isDebug = AntwebProps.isDevMode();

    private static final Log s_log = LogFactory.getLog(PageTracker.class);

    private static HashMap<String, Tracker> trackerMap = new HashMap<String, Tracker>();
    
    public static void add(HttpServletRequest request) {    
      //if (AntwebProps.isDevMode() && getRequestCount() > 3) purge();

      if (getRequestCount() > 100) {
        try {
          purge();
        } catch (java.util.ConcurrentModificationException e) {
          // nothing to do.  
        }
      }
      
      String target = HttpUtil.getTarget(request);
      
      // Curator pages can go in here. Things that an admin would notice going wrong may be exempt.
      if (target.contains("curate.do")
       || target.contains("adminAlert.do")
      ) return;
      Tracker tracker = new Tracker();
      tracker.setTarget(target);
      tracker.setStartTime(new Date());    
      tracker.setCode(AntwebUtil.getRandomNumber());
      request.setAttribute("trackerKey", tracker.getKey());
      trackerMap.put(tracker.getKey(), tracker);

      if (isDebug) {
<<<<<<< HEAD
          if (target.contains("ionName=Oceania") && LoginMgr.isMark(request))
=======
          if (target.contains("ionName=Oceania") && (AntwebProps.isDevMode() || LoginMgr.isMark(request)))
>>>>>>> master
              s_log.warn("add() request:" + (String) request.getAttribute("trackerKey"));

          //A.log("add() request:" + (String) request.getAttribute("trackerKey"));
          //if (AntwebProps.isDevMode()) AntwebUtil.logAntwebStackTrace();
      }
    }

    public static void remove(HttpServletRequest request) {
      String key = (String) request.getAttribute("trackerKey");
      trackerMap.remove(key);

      if (isDebug) {
          if (target.contains("ionName=Oceania") && LoginMgr.isMark(request))
              A.log("remove() request:" + (String) request.getAttribute("trackerKey"));
        //if (AntwebProps.isDevMode()) AntwebUtil.logAntwebStackTrace();
      }
    }

    // was synchronized
    public static void purge() {
      //StringBuffer sb = new StringBuffer();
      Collection<Tracker> values = null;
      try {
        values = trackerMap.values();
      } catch (java.util.ConcurrentModificationException e) {
        s_log.warn("purge() e:" + e + " values:" + values);
        return;
      }
      LogMgr.appendLog("pageTracker.log", "Purge", true); 
      for (Tracker tracker : values) {
        String logString = tracker.getStartTime() + " " + tracker.getKey() + " " + tracker.getSinceStartTime();
        LogMgr.appendLog("pageTracker.log", logString); 
      }
      trackerMap = new HashMap<String, Tracker>(); // was  .clear();
    }

    public static Collection<Tracker> getTrackers() {
      return trackerMap.values();    
    }

    public static Tracker getTracker(HttpServletRequest request) {
      String key = (String) request.getAttribute("trackerKey");
      Tracker tracker = trackerMap.get(key);
      return tracker;
    }
        
    public static Date getTime(HttpServletRequest request) {
      String target = HttpUtil.getTarget(request);
      String key = (String) request.getAttribute("trackerKey");
      Tracker tracker = trackerMap.get(key);
      if (tracker == null) {
        A.log("getTime() tracker not found for target:" + target);
        return null;
      }
      return tracker.getStartTime();
    }

    public static int getRequestCount() {
      return trackerMap.size();
    }
    
    public static String showRequests() {
        String message = "";

        Collection<Tracker> trackers = trackerMap.values();
        ArrayList<Tracker> trackerList = new ArrayList<>(trackers);
        Collections.sort(trackerList);

        for (Tracker tracker : trackerList) {
            message += tracker.toString() + "\r\n";
        }
        return message;
    }
}


