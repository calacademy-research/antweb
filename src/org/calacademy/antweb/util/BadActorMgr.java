package org.calacademy.antweb.util;
	
import java.io.*;
import java.net.*;
import java.util.*;

import java.text.*;
import java.io.IOException;

import javax.net.ssl.HttpsURLConnection;

import javax.servlet.http.*;
import javax.servlet.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


import org.apache.struts.action.*;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.OutputKeys;
import org.w3c.dom.Document;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.AddressException;
import org.calacademy.antweb.Utility;
import org.calacademy.antweb.util.AntwebUtil;

import javax.sql.DataSource;
import java.sql.SQLException;

import java.util.regex.*;

import org.calacademy.antweb.AntFormatter;
import org.apache.commons.httpclient.util.URIUtil;

public abstract class BadActorMgr {

    private static final Log s_log = LogFactory.getLog(BadActorMgr.class);

    public static boolean isBadActorBlocked(HttpServletRequest request) {
      return false; // To be...
    }

    private static HashMap badActorMap = new HashMap<String, Integer>();

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
        if (count != null && count > BAD_ACTOR_LIMIT) return true;
        return false;
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
        String report = "";
        int i = 0;
        for (String key : keys) {
            ++i;
            report += "i:" + i + " key:" + key + " value:" + badActorMap.get(key) + "\n";
        }
        return report;
    }
    
}



