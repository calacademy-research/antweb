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
 
 //A.log("doFilter()");
      HttpServletRequest request = (HttpServletRequest) req;
      HttpServletResponse response = (HttpServletResponse) res;
      ServletContext ctx = request.getSession().getServletContext();      

      PageTracker.add(request);

	  String target = HttpUtil.getTarget(request);      
      Login accessLogin = LoginMgr.getAccessLogin(request);
      String loginName = "-";
      if (accessLogin != null) loginName = accessLogin.getName();
      String logMessage = loginName + " " + AntwebUtil.getRequestInfo(request);
      LogMgr.appendLog("accessLog.txt", logMessage, true);
      //A.log("doFilter() message:" + logMessage);

      try {

		// Log insecure links.
		if (!!HttpUtil.isSecure(request)) {
		  A.log("doFilter() insecure");
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
        
/*        
   June 11, 2019. Removed these and added in the finally clause.

   Do we care to know about the ones that don't finish as expected, or the ones that don't finish?

        // Here we remove non-standard pages. Curator pages etc...
        if (target.contains("curate.do")
         || target.contains("listUploads.do") 
         || target.contains("forgotpassword.do") 
         || target.contains("manageMuseum.do") 
         || target.contains("viewMuseum.do")
         || target.contains("listUploads.do")
           ) {
          PageTracker.remove(request);
        }
        
*/
      } catch (Exception e) {
          // for instance: http://localhost/antweb/adm1.do?%20(Terr.%20Amazonas)
          String formatDateTime = DateUtil.getFormatDateTimeStr(new java.util.Date());
          int caseNumber = AntwebUtil.getCaseNumber();

          String note = ""; // Usually do nothing, but in cases...          
          int postActionPeriodPos = 0;
          if (target != null) {
            int actionPeriodPos = target.indexOf(".do");
            // Periods are not allowed after the .do. This is a struts I limitation.
            if (actionPeriodPos > 0)
              postActionPeriodPos = target.indexOf(".", actionPeriodPos + 1);
              if (postActionPeriodPos > 0) {
                note = " Handled. No periods allowed after action.";
              }
          }

          String message = formatDateTime;
          String htmlMessage = null;
          if (!"".equals(note)) {
            message += note + " e:" + e.toString() + " target:" + target;          
            s_log.warn("doFileter() " + message);
            htmlMessage 
              = "<br><b>Request Error: </b>" + message;
          } else {
            message += " See " + AntwebProps.getDomainApp() + "/web/log/srfExceptions.jsp for case#:" + caseNumber + " e:" + e.toString() + " target:" + target;
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
      }
      
    }
        
    public void init(FilterConfig filterConfig) throws ServletException {
        s_log.warn("init() - Server is initializing...");
		System.setProperty("jsse.enableSNIExtension", "false");       
		
        Connection connection = null;
        try {
		  DataSource ds = DBUtil.getDataSource();
		  connection = ds.getConnection(); 
		  AntwebMgr.populate(connection, true, true); // Trusted to close the connection
        } catch (SQLException e) {
          s_log.error("init() e:" + e);
        }

        long freeSpace = AntwebSystem.getFreeSpace();        
        long minFreeSpace = 7000000000L; // 12G
        String spaceMessage = "Free space:" + Formatter.formatMB(freeSpace); //Formatter.numberConverter(freeSpace);        
        if (freeSpace < minFreeSpace) {
          AdminAlertMgr.add(spaceMessage, connection);
          s_log.warn("init() warning:" + spaceMessage);
        } else {
          s_log.warn("init() " + spaceMessage);
        }

        String cpuMessage = AntwebSystem.getCpuLoad();
        s_log.warn("init() " + cpuMessage);

        this.runTask(); // Set up the Scheduler

        s_startTime = new Date();
        //s_log.warn("init() domainApp:" + AntwebProps.getDomainApp());
        s_log.warn("init() - Server is up.");
    }    
    
    public void destroy() { 
        s_log.warn("destroy() runTime:" + AntwebUtil.hrsSince(getStartTime()) + " " + AntwebMgr.getReport());
        s_log.warn("destroy() " +  UserAgentTracker.summary() + " overactive:" + UserAgentTracker.overActiveReport());  
        s_log.warn("destroy() overdue resource:" + DBUtil.getOldConnectionList());      
    }
       
    public static Date s_startTime = null;        
    public static Date getStartTime() {
      return s_startTime;
    }
    public static void setStartTime(Date time) {
      s_startTime = time;
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
      // if you want to run the task immediately, set the 2nd parameter to 0
      s_log.warn("runTask() calendar:" + calendar + " time:" + calendar.getTime());
      
      //if (AntwebProps.isDevMode()) return;
      
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
      
        (new Scheduler()).doAction();
      }

    } catch (Exception ex) {
        AntwebUtil.log("SessionRequestFilter.CustomTask.run() error running thread " + ex.getMessage());
    }
  }
}



