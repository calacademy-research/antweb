package org.calacademy.antweb.util;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class UptimeAction extends Action {

    private static final Log s_log = LogFactory.getLog(UptimeAction.class);

    private static boolean isFailOnPurpose = false;
    public static boolean isFailOnPurpose() {
      return isFailOnPurpose;
    }

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        s_log.debug("UptimeAction.execute()");
  
        String queryString = HttpUtil.getQueryString(request);
        if (queryString.contains("fail=1") || queryString.contains("fail=true")) isFailOnPurpose = true;
        if (queryString.contains("fail=0") || queryString.contains("fail=false")) isFailOnPurpose = false;
        if (isFailOnPurpose()) {
            return mapping.findForward("fail");
        }
  
		HttpSession session = request.getSession();
		boolean successDB = false;
		boolean successDisk = false;
        String message = "";	
		
		successDB = isDatabaseUp(request);
        if (!successDB) {
            message = "Database is down.  " + AntwebUtil.getRequestInfo(request);
            s_log.error(message); 
        }

		successDisk = isWebDirAccessible();
        if (!successDisk) {
            message = "/data/uptime.txt inaccessible.  " + AntwebUtil.getRequestInfo(request);
            s_log.error(message); 
        }

        if (!successDB || !successDisk) {
            request.setAttribute("message", message);
            return mapping.findForward("message");
        }
        
		return mapping.findForward("success");
	}

    private boolean isWebDirAccessible() {
        String uptimeTxt = AntwebUtil.readFile("data/", "uptime.txt");
        if (uptimeTxt == null) {
          s_log.error("isWebDirAccessible() uptimeTxt is null");
          return false;
        }
        if (!uptimeTxt.contains("success")) {
          s_log.debug("isWebDirAccessible() uptimeTxt:" + uptimeTxt);
          return false;
        }
        return true;
    }

    private boolean isDatabaseUp(HttpServletRequest request) {
		boolean success = false;
		Connection connection = null;
        Statement stmt = null;
        String dbMethodName = DBUtil.getDbMethodName("UptimeAction.isDatabaseUp()");
		try {
			DataSource dataSource = getDataSource(request, "conPool");
			connection = DBUtil.getConnection(dataSource, dbMethodName);
			stmt = connection.createStatement();
			
			stmt.executeQuery("select count(*) from image");

            AntwebMgr.populate(connection);

            success = true;
		} catch (SQLException e) {
			s_log.error("execute() e:" + e);
		} finally {
            DBUtil.close(connection, stmt, null, this, dbMethodName);
		}
        return success;
    }
}
