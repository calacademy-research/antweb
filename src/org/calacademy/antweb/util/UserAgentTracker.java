package org.calacademy.antweb.util;

import java.util.*;
import java.util.Map;
import java.sql.*;

import javax.servlet.http.*;


import javax.sql.DataSource;

import org.calacademy.antweb.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


public class UserAgentTracker {

  private static final Log s_log = LogFactory.getLog(UserAgentTracker.class);

  private static final Map<String, Integer> agentsMap = new HashMap<>();
  private static int nullAgent = 0;

  private static final Set<String> knownAgentsSet = new HashSet<>();
      
  private static final int OVERACTIVE = 1000;

  public static void init(Connection connection) {
      
      String query = "select name from user_agent";
      Statement stmt = null;
      ResultSet rset = null;
      try { 
          stmt = DBUtil.getStatement(connection, "UserAgentTracker.init()"); 
          rset = stmt.executeQuery(query);
      
          while (rset.next()) {
            String agent = rset.getString("name");
            knownAgentsSet.add(agent);
          } 
      } catch (SQLException e) {
          s_log.warn("init() query:" + query + " e:" + e);
      } finally {
            DBUtil.close(stmt, rset, "UserAgentTracker.init()");
      }	
  }
        
  public static void saveAgent(String agent, Connection connection) throws SQLException {
      String dml = "";
      Statement stmt = null;
      try {
          stmt = DBUtil.getStatement(connection, "saveAgent()");
          String val = AntFormatter.escapeQuotes(agent);
          dml = "insert into user_agent (name) values ('" + val + "')";  
          stmt.execute(dml);

          //A.log("saveAgent() agent:" + agent);
      } catch (SQLException e) {
          s_log.error("saveAgent() e:" + e + " dml:" + dml);
          throw e;
      } finally {
          DBUtil.close(stmt, "saveAgent()");
      }
  }        

  public static void track(HttpServletRequest request) {
      if (agentsMap.size() > 1000) return;
  
      String userAgent = getUserAgent(request);      
    
      if (userAgent == null) {
         ++nullAgent;
      } else {
          int count = agentsMap.getOrDefault(userAgent, 0);
          count = count + 1;
          agentsMap.put(userAgent, count);   
          
          if (count == OVERACTIVE) {
              // PERSIST!

            Connection connection = null;
            try {              
              DataSource ds = DBUtil.getDataSource();
		      connection = ds.getConnection(); 
		  
              saveAgent(userAgent, connection);
            } catch (SQLException e) {
              try {
                connection.close();       
              } catch (SQLException e2) {
                s_log.warn("track() failed to close connection:" + connection + " e:" + e2);
              }
            }
          }          
      }
  }

  private static String getUserAgent(HttpServletRequest request) {
      String userAgent = request.getHeader("user-agent");
      Login accessLogin = LoginMgr.getAccessLogin(request);
      if (accessLogin != null) userAgent += " (login:" + accessLogin.getName() + ")";
      return userAgent;  
  }

  public static boolean isOveractive(HttpServletRequest request) {
      String userAgent = getUserAgent(request);
      if (userAgent == null) { 
        return false;
      }
      return isOveractive(userAgent); 
  }
  
  private static boolean isOveractive(String userAgent) {
      if (userAgent.contains("(login:")) return false;

      if (knownAgentsSet.contains(userAgent)) {
        s_log.debug("KNOWN AGENT:" + userAgent);
        return true;
      }
      Integer countInteger = agentsMap.get(userAgent);
      if (countInteger == null) return false;
      int count = countInteger;
      return count > OVERACTIVE;
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
  public static String htmlReport() {
      Set<String> keySet = agentsMap.keySet();
      String report = "";
      String agent = "";

      // Used for sorting
      Map<Integer, String> countMap = new HashMap<>();

      for (String key : keySet) {
        int count = agentsMap.get(key);
        String star = "";
        if (knownAgentsSet.contains(key)) star = "<b><font color=red>X</font></b>";        
        if (key.contains("(login:")) star = "<b><font color=red><img src='" + AntwebProps.getDomainApp() + "/image/greenCheck.png'></font></b>";
        agent += "<br>" + key + star + ": <b>" + count + "</b>";
        //report += agent;
        countMap.put(count, agent);
      }

      report += "<br><br>DevMode:";

      TreeSet treeSet = new TreeSet(countMap.keySet());
      ArrayList<Integer> list = new ArrayList<>(treeSet);

      for (Integer count : list) {
          agent = countMap.get(count);
          s_log.debug("htmlReport() count:" + count + " agent:" + agent);
          report += agent;
      }

      return report;
  }

  public static String overActiveReport() {
      Set<String> keySet = agentsMap.keySet();
      String report = "";
      for (String key : keySet) {
        int count = agentsMap.get(key);
        if (count > OVERACTIVE) {
          report += "\n" + key + ": " + count; 
        }
      }
      return report;
  }

  public static String htmlKnownAgents() {
    String knownAgents = "";
    for (String agent : knownAgentsSet) {
      knownAgents += "<br>" + agent;
    }
    return knownAgents;
  }
}


