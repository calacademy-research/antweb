package org.calacademy.antweb.util;

import java.util.*;
import java.util.Map;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.*;


import javax.sql.*;
import java.sql.*;

import org.apache.struts.action.*;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.DynaActionForm;
import org.calacademy.antweb.*;
import org.calacademy.antweb.home.UserAgentDb;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.home.ServerDb;


public class UserAgentTracker extends Action {

  private static final Log s_log = LogFactory.getLog(UserAgentTracker.class);


    private static Map<String, Integer> agentsMap = new HashMap<>();
    private static int nullAgent = 0;

    private static Set<String> knownAgentsSet = null;
    private static Set<String> whiteList = null;

    private static final int OVERACTIVE = 1000;

    private static java.util.Date lastRefreshDate = null;

    public ActionForward execute(ActionMapping mapping, ActionForm form,
                                 HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        String target = HttpUtil.getTarget(request);
        DynaActionForm df = (DynaActionForm) form;

        String name = (String) df.get("name");
        if ("refresh".equals(name)) UserAgentTracker.refresh();
        A.log("name:" + name);

        return mapping.findForward("userAgents");
    }

  public static void init(Connection connection) {
      UserAgentDb userAgentDb = new UserAgentDb(connection);
      knownAgentsSet = userAgentDb.getKnownAgents();

      whiteList = userAgentDb.getWhiteList();
  }

  public static void refresh() {
      lastRefreshDate = new java.util.Date();
      agentsMap = new HashMap<>();
  }
  public static String getLastRefresh() {
      if (lastRefreshDate == null) return " - ";
      return lastRefreshDate.toString();
  }

  public static void track(HttpServletRequest request, Connection connection) {

      //if (ServerDb.isDebug("debugUserAgents")) return;

      if (knownAgentsSet == null) return;  // Not yet initialized.

      //if (agentsMap.size() > 1000) return;

      UserAgentDb userAgentDb = new UserAgentDb(connection);

      String userAgent = getUserAgent(request, userAgentDb);
    
      if (userAgent == null) {
         ++nullAgent;
      } else {
          int count = agentsMap.getOrDefault(userAgent, 0);
          count = count + 1;

          //s_log.warn("track() count:" + count + " agent:" + userAgent);
          agentsMap.put(userAgent, count);
          
          if (count == OVERACTIVE && !whiteList.contains(userAgent) && !userAgent.contains("login:")) {
              // So many requests. Known agent. PERSIST!
              s_log.info("track() persist userAgent:" + userAgent);
              userAgentDb.saveAgent(userAgent);
          }
      }
  }

    public static String getUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("user-agent");
        Login accessLogin = LoginMgr.getAccessLogin(request);

        if (accessLogin != null) {
            userAgent += " (login:" + accessLogin.getName() + ")";
        }

        return userAgent;
    }

    public static String getUserAgent(HttpServletRequest request, UserAgentDb userAgentDb) {
      String userAgent = request.getHeader("user-agent");
      Login accessLogin = LoginMgr.getAccessLogin(request);

      if (accessLogin != null) {

          if (!whiteList.contains(userAgent)) {
              userAgentDb.addToWhiteList(userAgent);
              whiteList.add(userAgent);
          }
          userAgent += " (login:" + accessLogin.getName() + ")";
      }

      return userAgent;  
  }

  public static boolean isOveractive(HttpServletRequest request) {

      //if (ServerDb.isDebug("debugUserAgents")) return false;

      String userAgent = getUserAgent(request);
      if (userAgent == null) { 
        return false;
      }
      return isOveractive(userAgent); 
  }

  // overactive agents have had more than OVERACTIVE (was: 1000) requests during one server execution.
  // (This is not wrong, of course, but is an indicator of bot activity).
  private static boolean isOveractive(String userAgent) {

      // Logged in users are never "overactive" or treated as bots.
      if (userAgent.contains("(login:")) {
          //s_log.warn("isOveractive() userAgent allowed with login:" + userAgent);
          return false;
      }

      if (whiteList.contains(userAgent)) return false;

      // If already a known agent,
      if (knownAgentsSet.contains(userAgent)) {
        //s_log.info("known agent:" + userAgent);
        return true;
      }

      Integer countInteger = agentsMap.get(userAgent);
      if (countInteger == null) return false;

      int count = countInteger;
      boolean isOveractive = count > OVERACTIVE;

      return isOveractive;
  }

  public static String summary() {
      String report = "report nullAgent:" + nullAgent + " agents:" + agentsMap.size() + " knownAgents:" + knownAgentsSet.size();
      return report;
  }

  public static String htmlSummary() {
      String report = "nullAgent:<b>" + nullAgent + "</b> agents:<b>" + agentsMap.size() + "</b>" + " knownAgents:<b>" + knownAgentsSet.size() + "</b>";
      return report;
  }
  
  public static String report() {
      Set<String> keySet = agentsMap.keySet();
      String report = "";
      for (String key : keySet) {
        int count = agentsMap.get(key);
        String star = "";
        if (key.contains("(login:")) star = "*";
        report += "\n" + key + star + ": " + count; 
      }
      return report;
  }
  public static String htmlUserAgents() {
      Set<String> keySet = agentsMap.keySet();

      String report = "<br><br>";
      String agent = "";

      // Used for sorting
      //Map<Integer, String> countMap = new HashMap<>();

      int c = 0;
      for (String key : keySet) {
        ++c;
        int count = agentsMap.get(key);
        String star = "";
        if (knownAgentsSet.contains(key)) star = "<b><font color=red>X</font></b>";        
        if (key.contains("(login:")) star = "<b><font color=red><img src='" + AntwebProps.getDomainApp() + "/image/greenCheck.png'></font></b>";
        agent = key + star + ": <b>" + count + "</b>";
        report += "<br><b>" + c + ": </b> " + agent;
        //countMap.put(count, agent);
      }

/*
      TreeSet treeSet = new TreeSet(countMap.keySet());
      ArrayList<Integer> list = new ArrayList<>(treeSet);

      int c2 = 0;
      for (Integer count : list) {
          ++c2;
          agent = countMap.get(count);
          A.log("htmlReport() c:" + c2 + " count:" + count + " agent:" + agent);
          report += "<b>" + count + ": </b>" + agent;
      }
*/
      //A.log("htmlReport() report:" + report);

      return report;
  }

  public static String overActiveReport() {
      Set<String> keySet = agentsMap.keySet();
      String report = "";
      for (String key : keySet) {
        int count = agentsMap.get(key);
        if (count > OVERACTIVE) {
          report += "\n" + count + ". " + key + ": " + count;
        }
      }
      return report;
  }

    public static String htmlWhiteList() {
        String whiteListAgents = "";
        int c = 0;
        for (String agent : whiteList) {
            ++c;
            whiteListAgents += "<br><b>" + c + ": </b>" + agent;
        }
        return whiteListAgents;
    }

  public static String htmlKnownAgents() {
    String knownAgents = "";
    int c = 0;
    for (String agent : knownAgentsSet) {
        ++c;
        knownAgents += "<br><b>" + c + ": </b>" + agent;
    }
    return knownAgents;
  }
}


