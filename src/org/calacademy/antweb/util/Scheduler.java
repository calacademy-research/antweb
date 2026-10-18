	package org.calacademy.antweb.util;

import java.io.*;
import java.util.Date;
import java.util.Calendar;

import jakarta.servlet.http.*;

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
public class Scheduler extends AntwebAction {

    public static final int LAUNCHTIME = 5; // 5 am. Referenced from SessionRequestFilter.

    // Read timeout for the utilData.do?action=set1..set5 self-calls below (#62). Each set
    // runs 10 to 50 minutes on production (see the timing comments in UtilDataAction),
    // far longer than HttpUtil.DEFAULT_READ_TIMEOUT_MS (5 min). With the default, set1
    // timed out every night from 2026-05-22 and set2..set5 were never requested
    // (compute.log: "Begin run 05:00:00 / End run 05:05:00").
    public static final int SET_READ_TIMEOUT_MS = 4 * 60 * 60 * 1000; // 4 hours

    // Restart after the full nightly run. Until 2026-10 the run always ended with
    // AntwebSystem.setIsRestart(true), and the host's reboot_checker.sh (every 5 minutes)
    // then restarted the container, around 8 AM. It is now off unless the server-only file
    // etc/scheduler.properties (~/antweb/etc/ on the host, gitignored; the container sees
    // /antweb/deploy/etc/) has the line  nightlyRestart=true . Read at the end of each run,
    // so a change needs no restart or deploy. The manual restart (utilData.do?action=setIsRestart),
    // the "stuck in set1" restart and the host's case#/watchdog restarts are unchanged.
    static final String CONFIG = "/antweb/deploy/etc/scheduler.properties";

    public static boolean isNightlyRestartOn() {
        return isNightlyRestartOn(new File(CONFIG));
    }

    public static boolean isNightlyRestartOn(File config) {
        if (config == null || !config.isFile()) return false;
        java.util.Properties p = new java.util.Properties();
        try (InputStream in = new FileInputStream(config)) {
            p.load(in);
        } catch (IOException e) {
            s_log.error("isNightlyRestartOn() could not read " + config + " e:" + e);
            return false;
        }
        return isOn(p.getProperty("nightlyRestart"));
    }

    public static boolean isOn(String value) {
        if (value == null) return false;
        String v = value.trim().toLowerCase();
        return "true".equals(v) || "on".equals(v) || "yes".equals(v) || "1".equals(v);
    }

    /** JVM heap use, for compute.log (the restart used to hide slow growth; watch it instead). */
    public static String memoryReport() {
        Runtime rt = Runtime.getRuntime();
        long mb = 1024L * 1024L;
        long used = (rt.totalMemory() - rt.freeMemory()) / mb;
        return "heap used:" + used + " MB committed:" + rt.totalMemory() / mb + " MB max:" + rt.maxMemory() / mb + " MB";
    }


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
		ActionForward confirm = PostOnly.confirm(request, mapping, "Start the scheduled (nightly) tasks: " + request.getParameter("action"));
		if (confirm != null) return confirm;

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

        String secureCode = AntwebUtil.getSecureCode();

        if (AntwebMgr.isServerInitializing()) {
            String message = "Scheduler failed because isInitializing:" + AntwebMgr.isServerInitializing();
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
      String secureCode = AntwebUtil.getSecureCode();
      String value = doAction("run", 0, secureCode); // Perform all
      return value;
    }

    public String doAction(String action, int num, String secureCode) {
    
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
				boolean set5Skipped = false;
				try {
					int i = Math.max(num, 0);  // This will invoke one of them if num != 0, otherwise invokes all of them

					// if (AntwebProps.isDevMode()) i = 1;
					//s_log.warn("doAction() action:" + action + " i:" + i + " num:" + num);

					boolean log = true;

					if (i == 0 || i == 1) {
						url = AntwebProps.getThisDomainApp() + "/utilData.do?action=set1&param=allow" + codeParam;
						output += HttpUtil.fetchUrl(url, SET_READ_TIMEOUT_MS);
						String note = null; if (output != null && output.length() > 20) note = output.substring(0, 20);
						if (log) s_log.warn("doAction() url:" + HttpUtil.forLog(url) + " " + note);
						if (output.contains("Unexpected error")) throw new AntwebException("Unexpected error");
					}
					if (i == 0 || i == 2) {
						url = AntwebProps.getThisDomainApp() + "/utilData.do?action=set2&param=allow" + codeParam;
						output += HttpUtil.fetchUrl(url, SET_READ_TIMEOUT_MS);
						String note = null; if (output != null && output.length() > 20) note = output.substring(0, 20);
						if (log) s_log.warn("doAction() url:" + HttpUtil.forLog(url) + " " + note);
						if (output.contains("Unexpected error")) throw new AntwebException("Unexpected error");
					}
					if (i == 0 || i == 3) {
						url = AntwebProps.getThisDomainApp() + "/utilData.do?action=set3&param=allow" + codeParam;
						output += HttpUtil.fetchUrl(url, SET_READ_TIMEOUT_MS);
						String note = null; if (output != null && output.length() > 20) note = output.substring(0, 20);
						if (log) s_log.warn("doAction() url:" + HttpUtil.forLog(url) + " " + note);
						if (output.contains("Unexpected error")) throw new AntwebException("Unexpected error");
					}
					if (i == 0 || i == 4) {
						url = AntwebProps.getThisDomainApp() + "/utilData.do?action=set4&param=allow" + codeParam;
						output += HttpUtil.fetchUrl(url, SET_READ_TIMEOUT_MS);
						String note = null; if (output != null && output.length() > 20) note = output.substring(0, 20);
						if (log) s_log.warn("doAction() url:" + HttpUtil.forLog(url) + " " + note);
						if (output.contains("Unexpected error")) throw new AntwebException("Unexpected error");
					}
					// set5 (object maps, reload all) needs more memory than staging's 4 GB droplet has:
					// skip it there in the nightly run (an admin can still request it by hand).
					if (i == 0 && AntwebProps.isStageMode()) {
						set5Skipped = true;
						s_log.warn("doAction() set5 skipped on staging (memory)");
						LogMgr.appendLog("compute.log", "  set5 skipped on staging (memory)", true);
					} else
					if (i == 0 || i == 5) {
						url = AntwebProps.getThisDomainApp() + "/utilData.do?action=set5&param=allow&reload=all" + codeParam;
						output += HttpUtil.fetchUrl(url, SET_READ_TIMEOUT_MS);
						String note = null; if (output != null && output.length() > 20) note = output.substring(0, 20);
						if (log) s_log.warn("doAction() url:" + HttpUtil.forLog(url) + " " + note);
						if (output.contains("Unexpected error")) throw new AntwebException("Unexpected error");
					}
				} catch (AntwebException | IOException e) {
					s_log.error("doAction() Scheduler failed. Investigate log files. e:" + e + " url:" + url);
					// Make the failure visible in the web-readable compute.log, not only in catalina.out.
					LogMgr.appendLog("compute.log", "Scheduler FAILED (remaining sets skipped) url:" + HttpUtil.forLog(url) + " e:" + e, true);
				}
				message = "Scheduler " + Html.esc(action) + ":" + num + " completed in " + AntwebUtil.getMinsPassed(startTime) + ". ";
				s_log.warn("doAction() " + message);

				if (num == 0) {
				  // This was the full scheduled tasks, now completed.
				  if (isNightlyRestartOn()) {
					s_log.warn("SET REBOOT TRUE");
					LogMgr.appendLog("compute.log", "Nightly restart requested (nightlyRestart=true in etc/scheduler.properties). " + memoryReport(), true);
				    AntwebSystem.setIsRestart(true);	// This will trigger a reboot within 5 minutes.
				  } else {
					if (set5Skipped) {
					  // set5 ends with AntwebMgr.populate(), which reloads every cache after the
					  // counts of sets 2 to 4. Where set5 is skipped (staging), the restart did that.
					  try (java.sql.Connection connection = DBUtilSimple.getDataSource().getConnection()) {
						AntwebMgr.populate(connection, true);
						LogMgr.appendLog("compute.log", "  caches reloaded (AntwebMgr.populate), as set5 was skipped", true);
					  } catch (Exception e) {
						s_log.error("doAction() cache reload after the nightly run e:" + e);
						LogMgr.appendLog("compute.log", "  cache reload FAILED e:" + e, true);
					  }
					}
					s_log.warn("doAction() nightly restart skipped (off)");
					LogMgr.appendLog("compute.log", "Nightly restart skipped (off; nightlyRestart=true in etc/scheduler.properties turns it on). " + memoryReport(), true);
				  }
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

