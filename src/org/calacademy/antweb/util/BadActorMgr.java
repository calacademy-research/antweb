package org.calacademy.antweb.util;
	
import java.util.*;

import jakarta.servlet.http.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public abstract class BadActorMgr {

    private static final Log s_log = LogFactory.getLog(BadActorMgr.class);

    public static boolean isBadActorBlocked(HttpServletRequest request) {
      return false; // To be...
    }

    // Request threads add to it at the same time: a concurrent map with atomic counting (2026-10-29), and a cap so
    // that many distinct addresses cannot grow it without limit (it is cleared, counts start again).
    private static final java.util.concurrent.ConcurrentHashMap<String, Integer> badActorMap = new java.util.concurrent.ConcurrentHashMap<>();
    static final int MAX_TRACKED = 50000;

    public static void addBadActor(HttpServletRequest request) {
        String ip = HttpUtil.getClientIpAddress(request);
        addBadActor(ip);
    }

    public static void addBadActor(String ip) {
      A.iLog("addBadActor() ip:" + ip);
      if (ip != null) {
        if (badActorMap.size() >= MAX_TRACKED && !badActorMap.containsKey(ip)) {
          s_log.warn("addBadActor() " + badActorMap.size() + " addresses tracked: list cleared");
          badActorMap.clear();
        }
        badActorMap.merge(ip, 1, Integer::sum);
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



