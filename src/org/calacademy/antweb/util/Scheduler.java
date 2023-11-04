	package org.calacademy.antweb.util;

import java.io.*;
import java.util.Date;
import java.util.Calendar;

import javax.servlet.http.*;

import org.apache.struts.action.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;


/**
This get's called from the curate page as such:
  /schedule.do?action=run
  /schedule.do?action=run&num=1
  /schedule.do?action=run&num=3   // admin tasks
  
  
to kick off a long and complete computation. This method gets around the limit on 
database connection timeouts by breaking the task into chunks and having each chunk
be a executed sequentially in a separate web request.

The requested urls should perhaps be in utilData.do instead of here.
*/
public class Scheduler extends Action {

    public static final int LAUNCHTIME = 5; // 5 am. Referenced from SessionRequestFilter.


    private static final Log s_log = LogFactory.getLog(Scheduler.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) {


		Login accessLogin = LoginMgr.getAccessLogin(request);
		if (accessLogin == null || !accessLogin.isAdmin()) {
   		    String message = "Scheduler can only be launched by administrative login";
			s_log.warn(message);
			request.setAttribute("message", message);
			return mapping.findForward("message");
		}

        HttpSession session = request.getSession();
		UtilForm theForm = (UtilForm) form;
		String action = theForm.getAction();
		int num = theForm.getNum();
		String name = theForm.getName();
        String param = theForm.getParam();

        // if (!"allow".equals(param)) { // Being called from this class through a getUrl()...   }

        // Verify that the request is written as antweb:antweb.
        // Test: ls -al  /data/antweb/web/data/t.log
        //s_log.warn("execute() see test file here:/data/antweb/web/data/t.txt");
        //AntwebUtil.writeDataFile("t.log", "test");

		s_log.warn("execute() action:" + action + " num:" + num + " target:" + HttpUtil.getTarget(request));

        int secureCode = AntwebUtil.getSecureCode();

        if (!AntwebMgr.isInitialized() || AntwebMgr.isServerInitializing()) {
            String message = "Scheduler failed because isInitialized:" + AntwebMgr.isInitialized() + " isInitializing:" + AntwebMgr.isServerInitializing();
            s_log.error("execute() " + message);
			request.setAttribute("message", message);
			return mapping.findForward("adminMessage");
		}

        String message = doAction(action, num, secureCode);

        LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr() + " Scheduler complete. "); // + 	" mesage:" + message);
		request.setAttribute("message", message);
		return mapping.findForward("adminMessage");
    }

    // Called from SessionRequestFilter
    public String doAction() {
      //return doAction("justLog", 0); // Perform all
      int secureCode = AntwebUtil.getSecureCode();
      String value = doAction("run", 0, secureCode); // Perform all
      return value;
    }

    public String doAction(String action, int num, int secureCode) {
    
        if (action == null) return "Null action";
    
		String message = "";

		int waitI = 0;
        while (!ServerStatusAction.isReady()) {
          ++waitI;
          int seconds = 10;
          int milliseconds = 1000;
          int waitTime = seconds * milliseconds;
		  if (!AntwebProps.isDevOrStageMode()) {
			  waitTime = 60 * waitTime;
		  }
		  s_log.warn("doAction() not ready to action:" + action + " num	:" + num + " because:" + ServerStatusAction.notReadyReason() + " waitTime:" + waitTime + ". waitI:" + waitI);
          try {            
            Thread.sleep(waitTime);
          } catch (InterruptedException e) {
            s_log.warn("doAction() e:" + e);          
          }
        }
				
		try {
			if (UtilDataAction.isInComputeProcess()) {
			    String m = "Already in compute proceess:" + UtilDataAction.getIsInComputeProcess();
				s_log.warn("doAction() " + m);
				LogMgr.appendLog("compute.log", m, true);

				// Would be good to send email alert?

				return m;
			}

			String codeParam = "&secureCode=" + secureCode;
		    UtilDataAction.setInComputeProcess(action);

            Object list = DBUtil.getOldConnectionList();
            if (list != null) s_log.warn("doAction() overdue resource:" + list);
		
		    LogMgr.appendLog("compute.log", "Begin " + action, true);

			// -------------- Full Computation ----------------

			if (action.equals("justLog")) {
			  s_log.warn("justLog");
			  return action + " completed.";
			}

			if (action.equals("run")) {
			    String output = "";
				Date startTime = new Date();
			    String url = null;
				try {
					int i = Math.max(num, 0);  // This will invoke one of them if num != 0, otherwise invokes all of them

					// if (AntwebProps.isDevMode()) i = 1;
					//s_log.warn("doAction() action:" + action + " i:" + i + " num:" + num);

					if (i == 0 || i == 1) {
						url = AntwebProps.getThisDomainApp() + "/utilData.do?action=set1&param=allow" + codeParam;
						output += HttpUtil.fetchUrl(url);
						String note = null; if (output != null && output.length() > 20) note = output.substring(0, 20);
						s_log.info("doAction() url:" + url + " " + note);
						if (output.contains("Unexpected error")) throw new AntwebException("Unexpected error");
					}
					if (i == 0 || i == 2) {
						url = AntwebProps.getThisDomainApp() + "/utilData.do?action=set2&param=allow" + codeParam;
						output += HttpUtil.fetchUrl(url);
						String note = null; if (output != null && output.length() > 20) note = output.substring(0, 20);
						s_log.info("doAction() url:" + url + " " + note);
						if (output.contains("Unexpected error")) throw new AntwebException("Unexpected error");
					}
					if (i == 0 || i == 3) {
						url = AntwebProps.getThisDomainApp() + "/utilData.do?action=set3&param=allow" + codeParam;
						output += HttpUtil.fetchUrl(url);
						String note = null; if (output != null && output.length() > 20) note = output.substring(0, 20);
						s_log.info("doAction() url:" + url + " " + note);
						if (output.contains("Unexpected error")) throw new AntwebException("Unexpected error");
					}
					if (i == 0 || i == 4) {
						url = AntwebProps.getThisDomainApp() + "/utilData.do?action=set4&param=allow" + codeParam;
						output += HttpUtil.fetchUrl(url);
						String note = null; if (output != null && output.length() > 20) note = output.substring(0, 20);
						s_log.info("doAction() url:" + url + " " + note);
						if (output.contains("Unexpected error")) throw new AntwebException("Unexpected error");
					}
					if (i == 0 || i == 5) {
						url = AntwebProps.getThisDomainApp() + "/utilData.do?action=set5&param=allow&reload=all" + codeParam;
						output += HttpUtil.fetchUrl(url);
						String note = null; if (output != null && output.length() > 20) note = output.substring(0, 20);
						s_log.info("doAction() url:" + url + " " + note);
						if (output.contains("Unexpected error")) throw new AntwebException("Unexpected error");
					}
				} catch (AntwebException | IOException e) {
					s_log.error("doAction() Scheduler failed. Investigate log files. e:" + e + " url:" + url);
				}
				message = "Scheduler " + action + ":" + num + " completed in " + AntwebUtil.getMinsPassed(startTime) + ". ";
				s_log.warn("doAction() " + message);

				if (num == 0) {
				  // This was the full scheduled tasks, now completed.
				  AntwebSystem.setIsRestart(true);	// This will trigger a reboot within 5 minutes.
				}

				return message + " output:" + output;
		    }
		} finally {
		  UtilDataAction.setInComputeProcess(null);

          AntwebUtil.resetSecureCode();

          LogMgr.appendLog("compute.log", "End " + action, true);
		}	          

        //this shouldn't happen in this example
        return "action:" + action + " not found.";
    }

    private static boolean s_isStuckInSet1 = false;
    public static boolean isStuckInSet1() {
      return s_isStuckInSet1;
	}
	private static boolean set1Tested = false;
	// Return true if set1 running after an hour of scheduled tasks. Only report true once.
	public static void set1Test() {
		// Test for the set1 not finishing issue.
		Calendar rightNow = Calendar.getInstance();
		int hour = rightNow.get(Calendar.HOUR_OF_DAY);
		if (hour <= LAUNCHTIME) {
			set1Tested = false;
            return;
		}

		if (set1Tested) return;

		if (hour >= (LAUNCHTIME + 1)) {
			set1Tested = true;
			String msg = UtilDataAction.getIsInComputeProcess();
			s_isStuckInSet1 = (msg != null && msg.contains("set1"));
            /*
			if (stuckInSet1) {
				// Yes, in set 1 after it should have completed.
				String body = "Stuck in set1. " + msg;
				String recipients = AntwebUtil.getDevEmail();
				String subject = "Antweb Alert";
				Emailer.sendMail(recipients, subject, body);
			}
        	*/
			if (s_isStuckInSet1) s_log.error("set1Test() stuckInSet1:" + s_isStuckInSet1);
		}
	}
}

