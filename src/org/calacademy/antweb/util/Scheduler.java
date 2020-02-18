package org.calacademy.antweb.util;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.struts.upload.FormFile;

import javax.servlet.http.*;

import org.apache.avalon.framework.logger.ConsoleLogger;
import org.apache.avalon.framework.logger.Logger;

import org.apache.struts.action.*;
import org.apache.regexp.*;

import com.zonageek.jpeg.Jpeg;

import java.sql.*;
import javax.sql.DataSource;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.data.*;

import java.util.Calendar;


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

    public static int LAUNCHTIME = 5; // 5 am. Referenced from SessionRequestFilter.

    private static Log s_log = LogFactory.getLog(Scheduler.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) {
      
        HttpSession session = request.getSession();
		UtilForm theForm = (UtilForm) form;
		String action = theForm.getAction();
		int num = theForm.getNum();
		String name = theForm.getName();
        String param = theForm.getParam();

        // if (!"allow".equals(param)) { // Being called from this class through a getUrl()...   }

        // Verify that the request is written as antweb:antweb.
        // Test: ls -al  /data/antweb/web/data/t.log
        s_log.warn("execute() see test file here:/data/antweb/web/data/t.txt");
        AntwebUtil.writeDataFile("t.log", "test");  

		s_log.warn("execute() target:" + HttpUtil.getTarget(request) + " action:" + action + " num:" + num);
						
        String message = doAction(action, num);

        //s_log.warn("execute() " + message);
        LogMgr.appendLog("admin.log", DateUtil.getFormatDate() + " Scheduler complete: " + message);
		request.setAttribute("message", message);
		return (mapping.findForward("adminMessage")); 	  
    }

    public String doAction() {
      //return doAction("justLog", 0); // Perform all
      return doAction("run", 0); // Perform all
    }
        
    public String doAction(String action, int num) {
    
        if (action == null) return "Null action";
    
		String message = "";

        while (!ServerStatusAction.isReady()) {
          s_log.warn("doAction() not ready:" + ServerStatusAction.notReadyReason());          
          try {            
            if (AntwebProps.isDevMode()) {
              Thread.sleep(10 * 1000);  // seconds * milliseconds
            } else {
              Thread.sleep(10 * 60 * 1000);  // minutes * seconds * milliseconds
            }
          } catch (InterruptedException e) {
            s_log.warn("doAction() e:" + e);          
          }
        }
				
		try {
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
					int i = 0;  // This will invoke all of them.
					if (num > 0) i = num;    // This would invoke one of them
 
					// if (AntwebProps.isDevMode()) i = 1;
					//s_log.warn("doAction() action:" + action + " i:" + i + " num:" + num);

					if (i == 0 || i == 1) {
					  url = AntwebProps.getThisDomainApp() + "/utilData.do?action=set1&param=allow";
					  s_log.warn("doAction() url:" + url);
					  output += HttpUtil.fetchUrl(url); 
					}
					if (i == 0 || i == 2) {
					  url = AntwebProps.getThisDomainApp() + "/utilData.do?action=set2&param=allow";
					  s_log.warn("doAction() url:" + url);
					  output += HttpUtil.fetchUrl(url); 
					}
					if (i == 0 || i == 3) {
					  url = AntwebProps.getThisDomainApp() + "/utilData.do?action=set3&param=allow";
					  s_log.warn("doAction() url:" + url);
					  output += HttpUtil.fetchUrl(url); 
					}
					if (i == 0 || i == 4) {
					  url = AntwebProps.getThisDomainApp() + "/utilData.do?action=set4&param=allow";
					  s_log.warn("doAction() url:" + url);
					  output += HttpUtil.fetchUrl(url); 
					}

					if (i == 0 || i == 5) {
					  url = AntwebProps.getThisDomainApp() + "/utilData.do?action=set5&param=allow";
					  s_log.warn("doAction() url:" + url);
					  output += HttpUtil.fetchUrl(url); 
					}

                    // 4 sec
                    if (i == 0 || i == 6) {
                      s_log.warn("doAction() Check For Antwiki Species and Fossil List Updates");
                      output += HttpUtil.fetchUrl(AntwebProps.getThisDomainApp() + "/antWikiData.do?action=checkForUpdates");
                    }
			
					s_log.warn("doAction() scheduler complete.");
			
				} catch (IOException e) {
				  s_log.warn("doAction e:" + e + " url:" + url);
				}
			
				return "Scheduler " + action + ":" + num + " completed in " + AntwebUtil.getMinsPassed(startTime) + ". output:" + output;
		    }     
		
		} finally {
		  UtilDataAction.setInComputeProcess(null);	  
		}	          

        //this shouldn't happen in this example
        return "action:" + action + " not found.";
    }
}

