package org.calacademy.antweb.util;

import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.upload.UploadAction;

import java.util.Date;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
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

            // "Keep me logged in": log in from the AW_REMEMBER cookie if the session has no login.
            // Only pages (.do) reach this filter ("/" and *.jsp: RememberMeFilter); without the cookie this costs nothing.
            RememberMeMgr.autoLogin(request, response);

            try {

                // Block bots nuclear option. All not logged in users forced to login.
                boolean blockUser = UserAgentTracker.isBlockUser(request);
                if (blockUser) {
                    String message = "<br><h2>Due to current Bot traffic, we are supporting only logged in users: "
                            + "<a href=" + AntwebProps.getDomainApp() + "/login.do>Login</a></h2><!-- reqPage:" + Html.esc(target) + " -->";
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
            // Never write the nightly secureCode (or other query secrets) to a log.
            String logMessage = HttpUtil.forLog(loginName + " " + AntwebUtil.getRequestInfo(request));
            LogMgr.appendLog("accessLog.txt", logMessage, true);
            //A.log("doFilter() message:" + logMessage);

            // Log insecure links.
            if (!HttpUtil.isSecure(request)) {
                s_log.info("doFilter() insecure target:" + target);
                String message = "req:" + target + " scheme:" + request.getScheme()
                        + " forward:" + request.getHeader("x-forwarded-proto") + " protocol:" + target.contains("https");
                LogMgr.appendLog("insecure.log", HttpUtil.forLog(message), true);
            }

            HttpUtil.setUtf8(request, response);

            // CSRF: a POST from a logged-in user must carry this session's token and must not
            // come from another site (see Csrf). Checked after setUtf8 so form values decode as UTF-8.
            if (accessLogin != null && "POST".equalsIgnoreCase(request.getMethod()) && !Csrf.isExempt(request)) {
                String refused = Csrf.check(request);
                if (refused != null) {
                    String note = "csrf refused: " + refused + " login:" + accessLogin.getId()
                        + " path:" + request.getServletPath() + " ip:" + HttpUtil.getClientIpAddress(request);
                    s_log.warn("doFilter() " + note);
                    LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr() + " " + note);
                    if (Csrf.isEditorSave(request)) {
                        Csrf.denyEditorSave(response, request.getParameter("contents"));
                    } else {
                        Csrf.deny(response);
                    }
                    return;
                }
            }

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
            message += " e:" + e + " target:" + HttpUtil.forLog(target);
            s_log.error("doFilter() See " + AntwebProps.getDomainApp() + "/web/log/srfExceptions.txt for case#:" + caseNumber + " message:" + message);

            message = ""
                    + "<br><b>startTime:</b>" + startTime
                    + "<br><b>case#:</b>" + caseNumber
                    + "<br><b>target:</b>" + Html.esc(HttpUtil.forLog(target))
                    + "<br><b>e:</b>" + e
                    //+ "<br><b>userAgent:</b>" + UserAgentTracker.getUserAgent(request)
                    //+ "<br><b>info:</b>" + HttpUtil.getLongRequestInfo(request)
                    + "<br><b>StackTrace:</b><pre>" + AntwebUtil.getAntwebStackTrace(e) + "</pre>";   // AntwebUtil.getAntwebStackTraceHtml(e) didn't work
            LogMgr.appendLog("srfExceptions.txt", message);

            htmlMessage
                    = "<br><b>Request Error</b>"
                    + "<br><br><b>Case#:</b>" + caseNumber
                    + "<br>(Please notify " + AntwebUtil.getAdminEmail() + ") with this info and description of use case. Thank you."
                    + "<br><b>Request:</b>" + Html.esc(target)
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
      // Production and staging: the nightly sets at LAUNCHTIME (5 AM Pacific, the container's
      // time zone). Not in dev.
      if (AntwebProps.isDevMode()) return;

      // Each run schedules the next one at the next 5:00 (CustomTask.run). A fixed 24-hour
      // period drifts by an hour at each daylight-saving change, and by the delay of each start;
      // that did not matter while the container restarted every morning (and re-anchored the
      // timer), but the nightly restart is now off by default (Scheduler.isNightlyRestartOn).
      scheduleNextRun(new Timer());
    }

    /** The next LAUNCHTIME:00:00 strictly after now, in the JVM's time zone. */
    public static Date nextLaunchTime(Calendar now) {
      Calendar calendar = (Calendar) now.clone();
      calendar.set(Calendar.HOUR_OF_DAY, Scheduler.LAUNCHTIME);
      calendar.set(Calendar.MINUTE, 0);  // was: 5. Set to allow time in case of a reboot.
      calendar.set(Calendar.SECOND, 0);
      calendar.set(Calendar.MILLISECOND, 0);
      if (!calendar.after(now)) {
         calendar.add(Calendar.DATE, 1);
      }
      return calendar.getTime();
    }

    static void scheduleNextRun(Timer timer) {
      Date next = nextLaunchTime(Calendar.getInstance());
      s_log.warn("runTask() time:" + next);
      timer.schedule(new CustomTask(timer), next);
    }
}


class CustomTask extends TimerTask  {

  private final Timer timer;

  public CustomTask(Timer timer) {
    this.timer = timer;
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
    } finally {
        // Tomorrow's run (also after a failure).
        try {
            SessionRequestFilter.scheduleNextRun(timer);
        } catch (RuntimeException e) {
            AntwebUtil.log("SessionRequestFilter.CustomTask.run() could not schedule the next run " + e);
        }
    }
  }
}



