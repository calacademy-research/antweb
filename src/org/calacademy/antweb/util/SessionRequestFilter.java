package org.calacademy.antweb.util;

import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.Login;

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



public class SessionRequestFilter implements Filter {
    
    private static final Log s_log = LogFactory.getLog("org.calacademy.antweb.util.SessionRequestFilter.class");
    
    private static boolean afterPopulated = false;
    
    boolean justOnce = false;
    
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        Date startTime = new Date();

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String target = HttpUtil.getTarget(request);

 //A.log("doFilter()");
      ServletContext ctx = request.getSession().getServletContext();

      PageTracker.add(request);
      
	  //A.log("target:"+ target);

      Login accessLogin = LoginMgr.getAccessLogin(request);
      String loginName = "-";
      if (accessLogin != null) loginName = accessLogin.getName();
      String logMessage = loginName + " " + AntwebUtil.getRequestInfo(request);
      LogMgr.appendLog("accessLog.txt", logMessage, true);
      //A.log("doFilter() message:" + logMessage);

      try {

          // Log insecure links.
          if (!HttpUtil.isSecure(request)) {
              s_log.debug("doFilter() insecure");
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

          UserAgentTracker.track(request);

          chain.doFilter(request, response);

          //if (target.contains("ionName=Oceania") && (AntwebProps.isDevMode() || LoginMgr.isMark(request))) s_log.warn("MarkNote() finished:" + target);

      } catch (Exception e) {

          String formatDateTime = DateUtil.getFormatDateTimeStr(new java.util.Date());
          String note = ""; // Usually do nothing, but in cases...
          int postActionPeriodPos = 0;
          if (target != null) {
            int actionPeriodPos = target.indexOf(".do");
            // Periods are not allowed after the .do. This is a struts I limitation.
            if (actionPeriodPos > 0)
              postActionPeriodPos = target.indexOf(".", actionPeriodPos + 1);
              //String periodStr = target.substring(postActionPeriodPos, postActionPeriodPos + 6);
              if (postActionPeriodPos > 0) {
                note = " Handled. No periods allowed after action.";
              }
          }
          String message = formatDateTime;
          String htmlMessage = null;

          if (!"".equals(note)) {    // for instance: http://localhost/antweb/adm1.do?%20(Terr.%20Amazonas)
            message += note + " e:" + e.toString() + " target:" + target;          
            s_log.warn("doFilter() " + message);
            htmlMessage 
              = "<br><b>Request Error: </b>" + message;
          //} else if (e instanceof AntwebException) {
          //    s_log.error("AntwebException handled error:" + e.toString());
          } else {
            int caseNumber = AntwebUtil.getCaseNumber();
            message += " See " + AntwebProps.getDomainApp() + "/web/log/srfExceptions.jsp for case#:" + caseNumber;
            message += " e:" + e.toString() + " target:" + target;
            s_log.error("doFilter() WST " + message);
            message += " stacktrace:" + "<br><pre><br><b> StackTrace:</b>" + AntwebUtil.getStackTrace(e) + "</pre>";
  		    LogMgr.appendLog("srfExceptions.jsp", message);    
            htmlMessage 
              = "<br><b>Request Error</b>"
              + "<br><br><b>Case#:</b>" + caseNumber 
              + "<br>(Please notify " + AntwebUtil.getAdminEmail() + ") with this info and description of use case. Thank you."
              + "<br><b>Exception:</b>" + e.toString()
              + "<br><b>Request:</b>" + target
              + "<br><b>Datetime:</b>" + formatDateTime
              ; 
            if (AntwebProps.isDevMode()) {
              htmlMessage += "<br><pre><br><b> StackTrace:</b>" + AntwebUtil.getStackTrace(e) + "</pre>";
            }
          }
		  HttpUtil.write(htmlMessage, response);   
      } finally {
          PageTracker.remove(request);

          finish(request, startTime);

          if (target.contains("ionName=Oceania") && (AntwebProps.isDevMode() || LoginMgr.isMark(request))) s_log.warn("MarkNote() finished:" + target);
      }
    }

    public static int MILLIS = 1000;
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
        String message = new Date().toString() + " time:" + execTime + " requestInfo:" + HttpUtil.getRequestInfo(request);
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
		  ds = DBUtil.getDataSource();
		  connection = ds.getConnection(); 
		  AntwebMgr.populate(connection, true, true); // Trusted to close the connection
        } catch (SQLException e) {
          s_log.error("init() e:" + e + " datasource:" + ds + " connection:" + connection);
        } finally {
          // Could we check on connections?
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
        s_log.warn(UserAgentTracker.summary() + "overactive:" + UserAgentTracker.overActiveReport());
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
      } else {
          AntwebUtil.log("warn", "CustomTask.run() DEV MODE SKIPPING scheduler.doAction()");
      }

    } catch (Exception ex) {
        AntwebUtil.log("SessionRequestFilter.CustomTask.run() error running thread " + ex.getMessage());
    }
  }
}



