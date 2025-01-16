package org.calacademy.antweb.util;

import java.util.*;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.http.*;


import org.apache.struts.action.*;

import org.calacademy.antweb.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


public class UserAgentTracker extends Action {

    private static final Log s_log = LogFactory.getLog(UserAgentTracker.class);

    private static LinkedList<Request> requestLinkList = new LinkedList<>();

    private static int MAX_LINKLIST_SIZE = 100;
    private static int PERIOD_IN_SECONDS = 100;
    private static int ALLOWED_PERCENT = 25;

    private static int blockCount = 0;
    private static int notBlockCount = 0;

    /*
    public UserAgentTracker() {
        this.requestLinkList = new LinkedList<>();
    }
    */



    private static String[] allowPages = new String[]{"login", "index", "basicLayout", "about.do", "documentation.do", "antShare.do", "press.do", "favs.do", "contact.do", "api.do"};
    // Kind of slow: , "statsPage.do"
    private static List<String> allowPagesList = Arrays.asList(allowPages);

    public static boolean isBlockUser(HttpServletRequest request) {

        addRequest(request);

        boolean block = blockTest(request);

        if (block == true) {
            blockCount++;
        } else {
            notBlockCount++;
        }

        return block;
    }

    private static boolean blockTest(HttpServletRequest request) {

        // For now, if logged in, allow. If bots start logging in, this could be removed.
        if (LoginMgr.isLoggedIn(request)) return false;

        // Even if rate of requests is too high, respond to these pages...
        String reqPage = HttpUtil.getTarget(request);
        if (reqPage == null) return false;  // Maybe the home page?
        for (String pageStr : allowPagesList) {
            if (reqPage.contains(pageStr)) return false;
        }

        // Nuclear block bot option. Block all non-logged-in users. If this gets used, change implemntation.
        if (ServerDebug.isDebug("isBlockUnLoggedIn")) return true;

        // If the list is yet to be filled...
        if (requestLinkList.size() < MAX_LINKLIST_SIZE) return false;

        // If the oldest in the list is less than PERIOD_IN_SECONDS..
        Request first = (Request) requestLinkList.getFirst();
        long secsSinceFirst = AntwebUtil.secsSince(first.getDate());
        if (secsSinceFirst > PERIOD_IN_SECONDS) return false;

        // Finally, is this the offending user-agent?
        if (getPercentage(request) >= ALLOWED_PERCENT) return true;

        return false;
    }

    private static void addRequest(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        boolean isLoggedIn = LoginMgr.isLoggedIn(request);
        requestLinkList.add(new Request(userAgent, isLoggedIn, new Date()));
        if (requestLinkList.size() > MAX_LINKLIST_SIZE) requestLinkList.removeFirst();
    }

    public static double getPercentage(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return getPercentageByName(userAgent);
    }
    public static double getPercentageByName(String name) {
        if (requestLinkList.isEmpty()) {
            return 0.0;  // Avoid division by zero
        }

        long count = requestLinkList.stream()
                .filter(req -> req.getUserAgent().equals(name))
                .count();

        return (count * 100.0) / requestLinkList.size();
    }



    public static String getDataAsHtml() {

        String returnStr = "UserAgentTracker";

        int size = requestLinkList.size();
        returnStr += "<br>linkListSize:" + size;

        if (size > 2) {
            Date date1 = ((Request) requestLinkList.getFirst()).getDate();
            Date date2 = ((Request) requestLinkList.getLast()).getDate();
            long seconds = AntwebUtil.secsBetween(date1, date2);
            long minutes = AntwebUtil.minsBetween(date1, date2);
            returnStr += "<br>seconds:" + seconds + " (minutes:" + minutes + ")";
        }

        returnStr += "<br>MAX_LINKLIST_SIZE:" + MAX_LINKLIST_SIZE + " PERIOD_IN_SECONDS:" + PERIOD_IN_SECONDS + " ALLOWED_PERCENT:"+ ALLOWED_PERCENT;

        returnStr += "<br> Max linked list size: " + MAX_LINKLIST_SIZE;
        returnStr += "<br> Full Linked List timestamp spread for busy threshold ( < seconds): " + PERIOD_IN_SECONDS;
        returnStr += "<br> Allowed percentage of list before being blocked: " + ALLOWED_PERCENT + "%";

        returnStr += "<br>Requests:<br>";
        int i = 0;
        String requests = "";
        for (Request req : requestLinkList) {
            ++i;
            requests += "<br>" + i + ": ";
            if (req.isLoggedIn()) requests += "*";
            requests += req.getUserAgent() + " - " + req.getDate();
        }

        returnStr += " Requests:" + requests;
        return returnStr;
    }

}


    class Request {
        private String userAgent;
        private boolean isLoggedIn;
        private Date date;
        private int count; // Used for summaries.

        public Request(String userAgent, boolean isLoggedIn, Date date) {
            this.userAgent = userAgent;
            this.isLoggedIn = isLoggedIn;
            this.date = date;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public boolean isLoggedIn() {
            return isLoggedIn;
        }

        public Date getDate() {
            return date;
        }

        public void setCount(int count) {
            this.count = count;
        }
        public int getCount(int count) {
            return count;
        }

    }



    /*
    public static String getData() {

        String returnStr = "UserAgentTracker";

        int size = requestLinkList.size();
        returnStr += "\r linkListSize:" + size;

        if (size > 2) {
            Date date1 = ((Request) requestLinkList.getFirst()).getDate();
            Date date2 = ((Request) requestLinkList.getLast()).getDate();
            long seconds = AntwebUtil.secsBetween(date1, date2);
            long minutes = AntwebUtil.minsBetween(date1, date2);
            returnStr += "\r Difference between earliest and latest in seconds: " + seconds + " (Minutes: " + minutes + ")";
        }

        returnStr += "\r Max linked list size: " + MAX_LINKLIST_SIZE;
        returnStr += "\r Full Linked List timestamp spread for busy threshold ( < seconds): " + PERIOD_IN_SECONDS;
        returnStr += "\r Allowed percentage of list before being blocked: " + ALLOWED_PERCENT + "%";

        int i = 0;
        String requests = "";
        for (Request req : requestLinkList) {
            ++i;
            requests += "\r " + i + ": " + req.getUserAgent() + " - " + req.getDate();
        }

        returnStr += " Requests:" + requests;
        return returnStr;
    }
*/

    /*
    public static String test() {
        UserAgentTracker userAgentTracker = new UserAgentTracker();

        userAgentTracker.addRequest("Order", new Date());
        userAgentTracker.addRequest("Cancel", new Date());
        userAgentTracker.addRequest("Order", new Date());
        userAgentTracker.addRequest("Order", new Date());
        userAgentTracker.addRequest("Return", new Date());

        A.log("requests:" + userAgentTracker.getRequestLinkListStr());

        return "Percentage of 'Order' requests: " + userAgentTracker.getPercentageByName("Order") + "%";
    }
*/



/*
    private static Map<String, Integer> agentsMap = new HashMap<>();
    private static int nullAgent = 0;

    private static Map<String, Integer> hyperActiveAgentsMap = new HashMap<>();

    private static java.util.Date s_thisSec = null;
    private static String s_hyperActiveAgent = null;
    private static int s_hyperActiveLimit = 4;  // Per second. Beyond this is rude!

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
        hyperActiveAgentsMap = new HashMap<>();
        s_hyperActiveAgent = null;
    }

    public static java.util.Date getLastRefresh() {
        if (lastRefreshDate == null) return null;
        return lastRefreshDate;
    }
    public static String getLastRefreshStr() {
        if (lastRefreshDate == null) return " - ";
        return lastRefreshDate.toString();
    }

    public static String denyAgent(HttpServletRequest request) {
        String target = HttpUtil.getTarget(request);
        String userAgent = getUserAgent(request);
        if (userAgent == null) userAgent = "Null UserAgent";

        if (isHyperActive(userAgent, target)) {
            String formatDateTime = DateUtil.getFormatDateTimeStr(new java.util.Date());
            String message
                = "<br><b>Antweb has found this requester making an unsupportable amount of requests on our server.</b>"
                + "<br>If you have received this response in error, Please notify " + AntwebUtil.getAdminEmail() + " with this info and description. Thank you."
                + "<br><b>Request: </b>" + target
                + "<br><b>Datetime: </b>" + formatDateTime
                + "<br><b>User Agent: </b>" + userAgent
                ;
            return message;
        }

        if (isOveractive(request)) {
            String formatDateTime = DateUtil.getFormatDateTimeStr(new java.util.Date());
            String message
                    = "<br><b>Bot requests unsupported at this time.</b>"
                    + "<br>If you have received this response in error, Please notify " + AntwebUtil.getAdminEmail() + " with this info and description. Thank you."
                    + "<br><b>Request: </b>" + target
                    + "<br><b>Datetime: </b>" + formatDateTime
                    + "<br><b>User Agent: </b>" + userAgent
                    ;
            return message;
        }
        return null;
    }

    private static int s_botDenial = 0;
    public static int getBotDenialCount() { return s_botDenial; }
    private static String s_botDenialReason = null;
    public static String getBotDenialReason() {
        if (s_botDenialReason == null) return "";
        return s_botDenialReason;
    }
    private static boolean s_vetMode = false;
    public static boolean isInVetMode() { return s_vetMode; }

    public static boolean vetForBot(HttpServletRequest request, HttpServletResponse response) throws IOException {

        boolean isServerBusy = DBUtil.isServerOverMaxBusy();
        s_vetMode = UploadAction.isInUploadProcess() || isServerBusy;
        if (s_vetMode) {
            String htmlMessage = UserAgentTracker.denyAgent(request);
            if (htmlMessage != null) {
                ++s_botDenial;
                if (UploadAction.isInUploadProcess()) {
                    s_botDenialReason = "isInUploadProcess";
                } else {
                    s_botDenialReason = null;
                }
                if (isServerBusy) s_botDenialReason = "isServerBusy";
                HttpUtil.write(htmlMessage, response);
                return false;  // Do not allow
            }
        } else {
            if (s_botDenial > 0) {
                s_log.info("doFilter() botDenial for reason:" + s_botDenialReason + " count:" + s_botDenial);
                s_botDenial = 0;
                s_botDenialReason = null;
            }
        }
        //A.log("target:"+ target);
        return true; // allow
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
              try {
                  userAgentDb.saveAgent(userAgent);
              } catch (java.sql.SQLIntegrityConstraintViolationException e) {
                  s_log.info("track() userAgent:" + userAgent + " already exists.");
              }
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

    // if in a 1 second time frame we receive more than s_hyperActiveLimit requests
    private static boolean isHyperActive(String userAgent, String target) {
        if (userAgent.equals(s_hyperActiveAgent)) {
            return true;
        }

        // Every period of time, flush and start over.
        if (s_thisSec == null || AntwebUtil.secsSince(s_thisSec) > 1) {
          s_thisSec = new java.util.Date();
          hyperActiveAgentsMap = new HashMap<>();
        }

        if (target.contains(".do") && hyperActiveAgentsMap.containsKey(userAgent)) {
            // get the count.
            int count = hyperActiveAgentsMap.getOrDefault(userAgent, 0);
            count = count + 1;
            hyperActiveAgentsMap.put(userAgent, count);
               //s_log.warn("track() count:" + count + " agent:" + userAgent);
            //Add one to it. Put it.

            if (count >= s_hyperActiveLimit) {
                s_log.warn("HyperActive agent: " + userAgent);
                // add to block list
                s_hyperActiveAgent = userAgent;
                return true;
            }
        }
        return false;
    }

    public static boolean isOveractive(HttpServletRequest request) {

      //if (ServerDb.isDebug("debugUserAgents")) return false;

      if (knownAgentsSet == null) return false;  // Not yet initialized.

      String userAgent = getUserAgent(request);
      if (userAgent == null) { 
        return false;
      }

      return isOveractive(userAgent);
  }

  // overactive agents have had more than OVERACTIVE (was: 1000) requests during one server execution/reset of userAgents.
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
      boolean isOveractive = count >= OVERACTIVE;

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

      String report = "";
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

      //A.log("htmlReport() report:" + report);

      return report;
  }

    public static int getAgentsSize() {
        return agentsMap.size();
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

    public static int getWhiteListSize() {
        return whiteList.size();
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

    public static int getKnownAgentsSize() {
        return knownAgentsSet.size();
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

*/
