package org.calacademy.antweb.util;

import java.io.IOException;
import java.io.File;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import com.mchange.v2.c3p0.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.home.*;
import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.Formatter;
    
public final class ServerStatusAction extends Action {

    private static final Log s_log = LogFactory.getLog(ServerStatusAction.class);

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
        String dbMethodName = DBUtil.getDbMethodName("ServerStatusAction.execute");

        try {
            ComboPooledDataSource dataSource1 = (ComboPooledDataSource) getDataSource(request, "conPool");
            ComboPooledDataSource dataSource2 = (ComboPooledDataSource) getDataSource(request, "mediumConPool");
            ComboPooledDataSource dataSource3 = (ComboPooledDataSource) getDataSource(request, "longConPool");

            connection = DBUtil.getConnection(dataSource1, dbMethodName);
 
            if (action.equals("toggleDownTime")) {
                String message = ServerDb.toggleDownTime(connection);
               // request.setAttribute("message", message);
               // return (mapping.findForward("message"));
            }
     
            setOperationLockAttr(dataSource1, request);

            request.setAttribute("isServerBusy", DBStatus.isServerBusy(dataSource1, dataSource2, dataSource3));
//            request.setAttribute("isServerBusy", DBStatus.isServerBusy(dataSource1, dataSource2, dataSource3));

            if (action.equals("email")) {
                String report = DBStatus.reportServerBusy(dataSource1, dataSource2, dataSource3, true);
                request.setAttribute("message", "message sent. <br><br>" + report);
                df.set("action", null);
                return (mapping.findForward("message"));
            }

            String cpDiagnostics = DBStatus.getSimpleCpDiagnosticsAttr(dataSource1);
            request.setAttribute("cpDiagnostics", cpDiagnostics);

            String cpMediumDiagnostics = DBStatus.getSimpleCpDiagnosticsAttr(dataSource2);
            request.setAttribute("cpMediumDiagnostics", cpMediumDiagnostics);

            String cpLongDiagnostics = DBStatus.getSimpleCpDiagnosticsAttr(dataSource3);
            request.setAttribute("cpLongDiagnostics", cpLongDiagnostics);

        } catch (SQLException e) {
            s_log.error("e:" + e);
        } catch (Exception e) {
            s_log.error("e:" + e);
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }
                   
        String serverDetails = ServerStatusAction.getServerDetails();
        request.setAttribute("serverDetails", serverDetails);           
                   
		return mapping.findForward("success");
	}

    public static boolean isReady() {
        return !UploadAction.isInUploadProcess()
                && !UptimeAction.isFailOnPurpose()
                && !UtilDataAction.isInComputeProcess();
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
            ServerDb.getIsDownTime(connection);
        } catch (SQLException e) {
            s_log.debug("populate() e:" + e);
        }
    }	

    /*
    public static boolean isInDownTime() {
      return !"".equals(ServerStatusAction.getDownTimeMessage());
    }    
    public static final String DOWN = "<h3><font color=red>The Upload Services are down for site maintenance.</font></h3>";
    private static String downTimeMessage = "";    
    public static String getDownTimeMessage() { 
      return downTimeMessage;
    }
    public static String getSimpleDownTimeMessage() { 
      return downTimeMessage;
    }    
    public static String isDownTime(String action, Connection connection)
      throws SQLException {
        String message = "";      

        boolean downTime = ServerStatusAction.getIsDownTime(connection);
        if (downTime) message = ServerStatusAction.getDownTimeMessage();
        s_log.debug("isDownTime() downTime:" + downTime);

        if (!"".equals(message)) s_log.warn("isDownTime message:" + message);
        downTimeMessage = message;
        return message;
    }


    public static boolean getIsDownTime(Connection connection)
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
        return downTime == 1;
    }

    public static String toggleDownTime(Connection connection)
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

*/

// --------------------------------------------------
	
    private void setOperationLockAttr(DataSource dataSource, HttpServletRequest request) {
        OperationLock operationLock = null;
        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("ServerStatusAction.setOperationLockAttr()");
        try {
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            operationLock = new OperationLockDb(connection).getOperationLock();
            if (operationLock != null) {
              //s_log.warn("setOperationLockAttr() isLocked:" + operationLock.isLocked());
              request.setAttribute("operationLock", operationLock);
            }
        } catch (SQLException e) {
            s_log.error("setOperationLockAttr() e:" + e);
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }             
    }

    public boolean executeMethodHabitat(HttpServletRequest request, HttpServletResponse response)
        throws Exception {        
    
        HttpSession session = request.getSession();
        s_log.warn("executeMethodHabitat()");

        return true;
    }

}
