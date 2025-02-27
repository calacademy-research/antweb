package org.calacademy.antweb.util;

import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.upload.UploadAction;

import java.util.Date;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.servlet.*;
import javax.servlet.http.*;
import java.sql.*;
import javax.sql.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.home.ServerDb;


public class SessionRequestFilter implements Filter {
    
    private static final Log s_log = LogFactory.getLog("org.calacademy.antweb.util.SessionRequestFilter.class");
    
    private static boolean afterPopulated = false;
    
    boolean justOnce = false;

    private static int s_period = 2; // minutes after which the infrequent code will execute. (Debug string fetch).
    private static Date s_periodDate = null;

    private static int s_concurrentRequests = 0;
    public static int getConcurrentRequests() {
        return s_concurrentRequests;
    }

    private static int perCounter = 0;
    private static Date perStart = new Date();
    private static int periodInSeconds = 10;
    private static double countPer = 0;  // requests per period
    public static String getRequestsPerSecond() {
        return Double.toString(countPer);
    }

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        if (AntwebProps.isDevMode()) s_period = 0;

        ++s_concurrentRequests;

        // Count hits per second.
        ++perCounter;
        //A.log("doFilter() perCounter:" + perCounter + " countPer:" + countPer + " periodInSeconds:" + periodInSeconds + " perStart:" + perStart);
        if (AntwebUtil.secsSince(perStart) >= periodInSeconds) {
            countPer = (double) perCounter / (periodInSeconds);
            //double actualQuotient = (double)dividend / divisor;

            perStart = new Date();
            perCounter = 0;
        }

        String formatDateTime = DateUtil.getFormatDateTimeStr(new java.util.Date());

        Date startTime = new Date();
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String target = HttpUtil.getTarget(request);

        try {


            try {

                // Block bots nuclear option. All not logged in users forced to login.
                boolean blockUser = UserAgentTracker.isBlockUser(request);
                if (blockUser) {
                    String message = "<br><h2>Due to current Bot traffic, we are supporting only logged in users: "
                            + "<a href=" + AntwebProps.getDomainApp() + "/login.do>Login</a></h2><!-- reqPage:" + target + " -->";
                    HttpUtil.write(message, response);
                    return;
                }

            } catch (Exception e) {
                s_log.error("e:" + e);
                AntwebUtil.logStackTrace(e);
            }


            //A.log("doFilter()");
            ServletContext ctx = request.getSession().getServletContext();

            DataSource ds = null;
            //Connection connection = null;

            PageTracker.add(request);

            boolean allow = true;
            //boolean allow = UserAgentTracker.vetForBot(request, response);

            if (allow) {
                allow = !Check.blockRecursiveCalls(request, response);
            }

            if (!allow) {
                return;
            }

            Login accessLogin = LoginMgr.getAccessLogin(request);
            String loginName = "-";
            if (accessLogin != null) loginName = accessLogin.getName();
            String logMessage = loginName + " " + AntwebUtil.getRequestInfo(request);
            LogMgr.appendLog("accessLog.txt", logMessage, true);
            //A.log("doFilter() message:" + logMessage);

            // Log insecure links.
            if (!HttpUtil.isSecure(request)) {
                s_log.info("doFilter() insecure target:" + target);
                String message = "req:" + target + " scheme:" + request.getScheme()
                        + " forward:" + request.getHeader("x-forwarded-proto") + " protocol:" + target.contains("https");
                LogMgr.appendLog("insecure.log", message, true);
            }

            HttpUtil.setUtf8(request, response);

            // To fix the Desc Edit Image Upload
            response.setHeader("X-XSS-Protection", "0");

            if (!afterPopulated && AntwebMgr.isPopulated()) {
                //s_log.warn("doFilter() Pop done. target:" + target);
                afterPopulated = true;
            }

            chain.doFilter(request, response);

        } catch (Exception e) {
            String note = ""; // Usually do nothing, but in cases...
            int postActionPeriodPos = 0;
            if (target != null) {
                int actionPeriodPos = target.indexOf(".do");
                // Periods are not allowed after the .do. This is a struts I limitation.
                if (actionPeriodPos > 0)
                    postActionPeriodPos = target.indexOf(".", actionPeriodPos + 1);
                //String periodStr = target.substring(postActionPeriodPos, postActionPeriodPos + 6);
                if (postActionPeriodPos > 0) {
                    note = " Periods not allowed in query String.";
                }
            }
            String message = "<br>" + formatDateTime;
            String htmlMessage = null;

            int caseNumber = AntwebUtil.getCaseNumber();
            message += " e:" + e + " target:" + target;
            s_log.error("doFilter() See " + AntwebProps.getDomainApp() + "/web/log/srfExceptions.jsp for case#:" + caseNumber + " message:" + message);

            message = ""
                    + "<br><b>startTime:</b>" + startTime
                    + "<br><b>case#:</b>" + caseNumber
                    + "<br><b>target:</b>" + target
                    + "<br><b>e:</b>" + e
                    //+ "<br><b>userAgent:</b>" + UserAgentTracker.getUserAgent(request)
                    //+ "<br><b>info:</b>" + HttpUtil.getLongRequestInfo(request)
                    + "<br><b>StackTrace:</b><pre>" + AntwebUtil.getAntwebStackTrace(e) + "</pre>";   // AntwebUtil.getAntwebStackTraceHtml(e) didn't work
            LogMgr.appendLog("srfExceptions.jsp", message);

            htmlMessage
                    = "<br><b>Request Error</b>"
                    + "<br><br><b>Case#:</b>" + caseNumber
                    + "<br>(Please notify " + AntwebUtil.getAdminEmail() + ") with this info and description of use case. Thank you."
                    + "<br><b>Request:</b>" + target
                    + "<br><b>Datetime:</b>" + formatDateTime
            ;
            if (AntwebProps.isDevMode()) {
                htmlMessage += "<br><pre><br><b> StackTrace:</b>" + AntwebUtil.getStackTrace(e) + "</pre>";
            }
            HttpUtil.write(htmlMessage, response);

        } finally {
            --s_concurrentRequests;

            PageTracker.remove(request);

            finish(request, startTime);

            //if (target.contains("ionName=Oceania") && (AntwebProps.isDevMode() || LoginMgr.isMark(request))) s_log.warn("MarkNote() finished:" + target);
        }
    }

    // This method used to be inline of doFilter(), but now called by Action classes with their connection.
    public static void processRequest(HttpServletRequest request, Connection connection) {
        try {
            // Populate ServerDb Debug flag.
            if (s_periodDate == null) s_periodDate = new Date();
            //A.log("doFilter() s_periodDate:" + s_periodDate + " s_period:" + s_period + " since:" + AntwebUtil.minsSince(s_periodDate));
            if (AntwebUtil.minsSince(s_periodDate) >= s_period) {
                // This will happen only every s_period.
                s_periodDate = new Date();
                String debug = ServerDb.getServerDebug(connection);
                //A.log("doFilter() period s_periodDate:" + s_periodDate + " debug:" + debug);
            }
        } catch (SQLException e) {
            s_log.warn("processRequest() e:" + e);
        }

        //UserAgentTracker.track(request, connection);
    }

    public static final int MILLIS = 1000;
    public static int SECS = 60;
    public static int MAX_REQUEST_TIME = MILLIS * 10;
    public static String finish(HttpServletRequest request, java.util.Date startTime) {
        String execTime = "";
        long millis = AntwebUtil.millisSince(startTime);
        if (millis > 2000) {
            execTime = AntwebUtil.secsSince(startTime) + " secs";
        } else {
            execTime = millis + " millis";
        }
        String message = new Date() + " time:" + execTime + " requestInfo:" + HttpUtil.getRequestInfo(request);
        if (AntwebProps.isDevMode()) {
            //s_log.warn(message);
            MAX_REQUEST_TIME = 1;
        }

        s_log.debug("finish() millis:" + millis + " info:" + HttpUtil.getRequestInfo(request));
        if (millis > MAX_REQUEST_TIME) LogMgr.appendDataLog("longRequest.log", message);
        return execTime;
    }

    public void init(FilterConfig filterConfig) throws ServletException {
        s_log.warn("init() - Server is initializing...");

        String message = "";
		System.setProperty("jsse.enableSNIExtension", "false");


        DataSource ds = null;
        Connection connection = null;
        try {
		  ds = DBUtilSimple.getDataSource();
		  connection = ds.getConnection(); 
		  AntwebMgr.populate(connection, true, true);

          // Should be in an set of server init checks.
          //(new UserAgentDb(connection)).flagWhiteList();

          LogMgr.backupSrf();

        } catch (SQLException e) {
            s_log.error("init() e:" + e + " datasource:" + ds + " connection:" + connection);
        //} catch (java.beans.PropertyVetoException e) {
        //    s_log.error("init() e:" + e);
        } finally {

            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                s_log.error("init() e:" + e);
            }
        }

        long freeSpace = AntwebSystem.getFreeSpace();        
        long minFreeSpace = 7000000000L; // 12G
        String spaceMessage = "Free space:" + Formatter.formatMB(freeSpace); //Formatter.numberConverter(freeSpace);        
        if (freeSpace < minFreeSpace) {
          AdminAlertMgr.add(spaceMessage, connection);
          message += "warning:" + spaceMessage;
        } else {
          message += spaceMessage;
        }

        String cpuMessage = AntwebSystem.getCpuLoad();
        message += " " + cpuMessage;

        this.runTask(); // Set up the Scheduler

        setInitTime(new Date());
        //s_log.warn("init() domainApp:" + AntwebProps.getDomainApp());
        s_log.warn("init() - Server is up. " + message);
    }    
    
    public void destroy() { 
      report();
    }

    public void report() {
        s_log.warn("runTime:" + AntwebUtil.hrsSince(getInitTime()) + " " + AntwebMgr.getReport());
        //s_log.warn(UserAgentTracker.summary() + "overactive:" + UserAgentTracker.overActiveReport());
        s_log.warn("Overdue resource:" + DBUtil.getOldConnectionList());
        s_log.warn("Bad Actor Report:" + BadActorMgr.getBadActorReport());
        s_log.warn(AntwebSystem.getTopReport());
    }
       
    public static Date s_initTime;
    public static Date getInitTime() {
      return s_initTime;
    }
    public static void setInitTime(Date time) {
      s_initTime = time;
    }

    public void runTask() {
      // In production, execute at 8:05 pm and every 24 thereafter...

      Calendar calendar = Calendar.getInstance();

      if (AntwebProps.isDevMode()) {
          if (true) return;

          calendar.set(Calendar.HOUR_OF_DAY, 13);
          calendar.set(Calendar.MINUTE, 54);
      } else {
          // 5am scheduled tasks. 
          calendar.set(Calendar.HOUR_OF_DAY, Scheduler.LAUNCHTIME);
          calendar.set(Calendar.MINUTE, 0);  // was: 5. Set to allow time in case of a reboot.
          calendar.set(Calendar.SECOND, 0);
          calendar.set(Calendar.MILLISECOND, 0);
      }

      Calendar now = Calendar.getInstance();
      if(calendar.before(now) || calendar.equals(now)) {
         calendar.add(Calendar.DATE, 1);
      }

      Timer timer = new Timer();
      s_log.warn("runTask() time:" + calendar.getTime());  // calendar:" + calendar + "
      //if (AntwebProps.isDevMode()) return;  // Don't run on dev

      // if you want to run the task immediately, set the 2nd parameter to 0
      timer.schedule(new CustomTask(), calendar.getTime(), TimeUnit.HOURS.toMillis(24));
    }    
}


class CustomTask extends TimerTask  {
    
  public CustomTask() {
  }

  public void run() {
    try {

      // Scheduler launch.
      AntwebUtil.log("SessionRequestFilter.CustomTask.run()");
      if (!AntwebProps.isDevMode()) {
          new Scheduler().doAction();
          //UserAgentTracker.refresh();
      } else {
          AntwebUtil.log("warn", "CustomTask.run() DEV MODE SKIPPING scheduler.doAction()");
      }
    
    } catch (Exception ex) {
        AntwebUtil.log("SessionRequestFilter.CustomTask.run() error running thread " + ex.getMessage());
    }
  }
}



