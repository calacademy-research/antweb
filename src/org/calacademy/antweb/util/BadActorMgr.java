package org.calacademy.antweb.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Set;

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
          Integer count = (Integer) badActorMap.get(ip);
          if (count == null) {
              badActorMap.put(ip, 1);
          } else {
              badActorMap.put(ip, ++count);
          }
        } else {
          A.log("Not adding ip:" + ip + " to bad actor list");
        }
      }
    }

    public static boolean isBadActor(String ip) {
        int BAD_ACTOR_LIMIT = 10;
        Integer count = (Integer) badActorMap.get(ip);
        return count != null && count > BAD_ACTOR_LIMIT;
    }

    public static String ifBadActorBlockedGetMessage(HttpServletRequest request) {
        String ip = HttpUtil.getClientIpAddress(request);
        if (!isBadActor(ip)) return null;

        //Check the map;
        String message = "User from IP:" + ip
                + " is blocked for having too many illegal requests. If in error contact bfisher@calacademy.org for resolution.";
        request.setAttribute("message", message);
        //s_log.error(message);
        LogMgr.appendLog("badActorBlocked.txt", message, true);
        return message;
    }

    public static String getBadActorReport() {
        Set<String> keys = badActorMap.keySet();
        StringBuilder report = new StringBuilder();
        int i = 0;
        for (String key : keys) {
            ++i;
            report.append("i:").append(i).append(" key:").append(key).append(" value:").append(badActorMap.get(key)).append("\n");
        }
        return report.toString();
    }
    
}



