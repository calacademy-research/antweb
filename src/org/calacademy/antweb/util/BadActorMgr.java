package org.calacademy.antweb.util;
	
import java.util.*;

import jakarta.servlet.http.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * Counts, per client IP address, the requests HttpUtil found illegal (hasIllegalChars, hasIllegalStr). After
 * more than 10, HttpUtil.invalidRequest answers that IP with a "blocked" message and appends a line to
 * web/log/badActorBlocked.txt. In memory only, cleared by a restart. isBadActorBlocked always answers false and
 * has no callers. The report is written to the log by SessionRequestFilter.
 */
public abstract class BadActorMgr {

    private static final Log s_log = LogFactory.getLog(BadActorMgr.class);

    public static boolean isBadActorBlocked(HttpServletRequest request) {
      return false; // To be...
    }

    private static final HashMap<String, Integer> badActorMap = new HashMap<>();

    public static void addBadActor(HttpServletRequest request) {
        String ip = HttpUtil.getClientIpAddress(request);
        addBadActor(ip);
    }

    public static void addBadActor(String ip) {
      A.iLog("addBadActor() ip:" + ip);
      if (ip != null) {
        if (true || !"http://0:0:0:0:0:0:0:1".equals(ip)) {
          Integer count = badActorMap.get(ip);
          if (count == null) {
              badActorMap.put(ip, 1);
          } else {
              badActorMap.put(ip, ++count);
          }
        } else {
          s_log.debug("Not adding ip:" + ip + " to bad actor list");
        }
      }
    }

    public static boolean isBadActor(String ip) {
        int BAD_ACTOR_LIMIT = 10;
        Integer count = badActorMap.get(ip);
        return count != null && count > BAD_ACTOR_LIMIT;
    }

    public static String ifBadActorBlockedGetMessage(HttpServletRequest request) {
        String ip = HttpUtil.getClientIpAddress(request);
        if (!isBadActor(ip)) return null;

        //Check the map;
        String message = "User from IP:" + Html.esc(ip)
                + " is blocked for having too many illegal requests. If in error contact bfisher@calacademy.org for resolution.";
        request.setAttribute("message", message);
        //s_log.error(message);
        LogMgr.appendLog("badActorBlocked.txt", message, true);
        return message;
    }

    public static String getBadActorReport() {
        Set<String> keys = badActorMap.keySet();
        String report = "";
        int i = 0;
        for (String key : keys) {
            ++i;
            report += "i:" + i + " key:" + key + " value:" + badActorMap.get(key) + "\n";
        }
        return report;
    }
    
}



