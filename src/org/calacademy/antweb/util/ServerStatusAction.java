package org.calacademy.antweb.util;

import java.io.IOException;
import java.io.File;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.home.*;
import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.Formatter;
    
public final class ServerStatusAction extends Action {

    private static Log s_log = LogFactory.getLog(ServerStatusAction.class);

/*
public static double JAVA_VERSION = getVersion ();

static double getVersion () {
    String version = System.getProperty("java.version");
    int pos = version.indexOf('.');
    pos = version.indexOf('.', pos+1);
    return Double.parseDouble (version.substring (0, pos));
}
*/
	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        HttpUtil.setUtf8(request, response);
        
        DynaActionForm df = (DynaActionForm) form;
     
        Connection connection = null;
        String action = (String) df.get("action");   
    
        //A.log("execute action:" + action);


        try {
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "ServerStatusAction.execute");
 
            if (action.equals("toggleDownTime")) {
                String message = ServerStatusAction.toggleDownTime(connection); 
               // request.setAttribute("message", message);
               // return (mapping.findForward("message"));
            }
     
            setOperationLockAttr(dataSource, request);
            try {       
                request.setAttribute("isServerBusy", DBUtil.isServerBusy(dataSource, request));
            } catch (SQLException e) {
                s_log.error("e:" + e);
            }

            String cpDiagnostics = DBUtil.getSimpleCpDiagnosticsAttr(dataSource);
            request.setAttribute("cpDiagnostics", cpDiagnostics);

        } catch (Exception e) {
            s_log.error("e:" + e);
        } finally {
            DBUtil.close(connection, this, "ServerStatusAction.execute()");
        }
                   
        String serverDetails = ServerStatusAction.getServerDetails();
        request.setAttribute("serverDetails", serverDetails);           
                   
		return (mapping.findForward("success"));
	}

    public static boolean isReady() {
      if (UploadAction.isInUploadProcess() 
       || UptimeAction.isFailOnPurpose() 
       || UtilDataAction.isInComputeProcess()) {
         return false;
      }
      return true;
    }
				
    public static String notReadyReason() {
      if (UploadAction.isInUploadProcess()) return "A curator (" + UploadAction.getIsInUploadProcess() + " is currently in the process of an Upload.  Please try again shortly.";
      if (UptimeAction.isFailOnPurpose()) return "Fail on purpose.";
      if (UtilDataAction.isInComputeProcess()) return "Currently in compute process:" + UtilDataAction.getIsInComputeProcess();
      return null;
    }
				   
	public static String getServerDetails()
	{
		String ret = "<br>&nbsp;&nbsp;Operating System: " + System.getProperty("os.name");
		ret += "<br>&nbsp;&nbsp;Version: " + System.getProperty("os.version");
		ret += "<br>&nbsp;&nbsp;Architecture: " + System.getProperty("os.arch");

		ret += "<br>&nbsp;&nbsp;Java Vendor: " + System.getProperty("java.vendor");
		ret += "<br>&nbsp;&nbsp;Version: " + System.getProperty("java.version");

		ret += "<br>&nbsp;&nbsp;Available processors (cores): " + Runtime.getRuntime().availableProcessors();
		ret += "<br>&nbsp;&nbsp;Free memory (bytes): " + Formatter.commaFormat(Runtime.getRuntime().freeMemory());
		long maxMemory = Runtime.getRuntime().maxMemory();
		ret += "<br>&nbsp;&nbsp;Maximum memory (bytes): " + (maxMemory == Long.MAX_VALUE ? "no limit" : Formatter.commaFormat(maxMemory));
		ret += "<br>&nbsp;&nbsp;Total memory available to JVM (bytes): " + Formatter.commaFormat(Runtime.getRuntime().totalMemory());

		/* Get a list of all filesystem roots on this system */
		File[] roots = File.listRoots();

		/* For each filesystem root, print some info */
		for (File root : roots)
			{
			ret += "<br>&nbsp;&nbsp;File system root: " + root.getAbsolutePath();
			ret += "<br>&nbsp;&nbsp;Total space (bytes): " + Formatter.commaFormat(root.getTotalSpace());
			ret += "<br>&nbsp;&nbsp;Free space (bytes): " + Formatter.commaFormat(root.getFreeSpace());
			ret += "<br>&nbsp;&nbsp;Usable space (bytes): " + Formatter.commaFormat(root.getUsableSpace());
			}

		//ret += "<br>&nbsp;&nbsp;Stack trace:";
		return ret;
	}
	

    public static void populate(Connection connection) {
        try {
            getIsDownTime(connection);
        } catch (SQLException e) {
            A.log("populate() e:" + e);
        }
    }	

    public static boolean isInDownTime() {
      return !"".equals(ServerStatusAction.getDownTimeMessage());
    }    
    public static String DOWN = "<h3><font color=red>The Upload Services are down for site maintenance.</font></h3>";
    private static String downTimeMessage = "";    
    public static String getDownTimeMessage() { 
      return downTimeMessage;
    }
    public static String getSimpleDownTimeMessage() { 
      return downTimeMessage;
    }    
    public static String isDownTime(String action, java.sql.Connection connection)      
      throws SQLException {
        String message = "";      

        boolean downTime = ServerStatusAction.getIsDownTime(connection);
        if (downTime) message = ServerStatusAction.getDownTimeMessage();
        A.log("isDownTime() downTime:" + downTime);

/*
        if ("".equals(message)) {
          long minUntilReboot = 0;
          if (!AntwebProps.isDevMode()) minUntilReboot = AntwebUtil.minUntil8pm();
          if (minUntilReboot > 0 && minUntilReboot < 30) { 
              message = "<h3><font color=red>Server is going down in " + minUntilReboot + " minutes. Please try again later.</font></h3>";
          }
        }
*/      
        if (!"".equals(message)) s_log.warn("isDownTime message:" + message);
        downTimeMessage = message;
        return message;
    }


    public static boolean getIsDownTime(java.sql.Connection connection) 
      throws SQLException {
        int downTime = 0; 
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "ServerStatusAction.isDownTime()");
            String query = "select is_down_time from server";
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                downTime = rset.getInt("is_down_time");
                if (downTime == 1) {
                  downTimeMessage = DOWN;
                } else {
                  downTimeMessage = "";
                }
            }            
        } catch (SQLException e) {
            s_log.error("runStatistics() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "ServerStatusAction.isDownTime()");
        }      
        return (downTime == 1);
    }

    public static String toggleDownTime(java.sql.Connection connection) 
      throws SQLException {
        int downTime = 0;
        
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "ServerStatusAction.toggleIsDownTime()");
            boolean isDownTime = ServerStatusAction.getIsDownTime(connection);
            if (isDownTime) downTime = 0; else downTime = 1;
            if (downTime == 1) {
                  downTimeMessage = DOWN;
            } else {
                  downTimeMessage = "";
            }            
            String dml = "update server set is_down_time = " + downTime;
            stmt.executeUpdate(dml);        
        } catch (SQLException e) {
            s_log.error("toggleIsDownTime() " + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "ServerStatusAction.toggleIsDownTime()");
        }      
        if (downTime == 1) {
          return "Service is now offline";
        } else {
          return "Service is now online";
        }
    }


// --------------------------------------------------
	
    private void setOperationLockAttr(javax.sql.DataSource dataSource, HttpServletRequest request) {
        OperationLock operationLock = null;
        Connection connection = null;    
        try {
            connection = DBUtil.getConnection(dataSource, "ServerStatusAction.setOperationLockAttr()");
            operationLock = (new OperationLockDb(connection)).getOperationLock();
            if (operationLock != null) {
              //s_log.warn("setOperationLockAttr() isLocked:" + operationLock.isLocked());
              request.setAttribute("operationLock", operationLock);
            }
        } catch (SQLException e) {
            s_log.error("setOperationLockAttr() e:" + e);
        } finally {
            DBUtil.close(connection, this, "ServerStatusAction.setOperationLockAttr()");
        }             
    }

    public boolean executeMethodHabitat(HttpServletRequest request, HttpServletResponse response)
        throws Exception {        
    
        HttpSession session = request.getSession();
        s_log.warn("executeMethodHabitat()");

        return true;
    }

}
