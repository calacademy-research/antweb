package org.calacademy.antweb.util;

import org.calacademy.antweb.AntwebAction;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class UptimeAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(UptimeAction.class);

    private static boolean isFailOnPurpose = false;
    public static boolean isFailOnPurpose() {
      return isFailOnPurpose;
    }

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward returnVal = null;
        String message = null;

        // The fail= test switch changes the answer for every caller: administrators only, by POST.
        String fail = request.getParameter("fail");
        if (fail != null && LoginMgr.isAdmin(request)) {
            ActionForward confirm = PostOnly.confirm(request, mapping, "Set uptime fail-on-purpose: " + fail);
            if (confirm != null) return confirm;
            if ("1".equals(fail) || "true".equals(fail)) isFailOnPurpose = true;
            if ("0".equals(fail) || "false".equals(fail)) isFailOnPurpose = false;
        }

        if (isFailOnPurpose()) {
            message = "fail on purpuse";
            returnVal = mapping.findForward("fail");
        }

        if (returnVal == null) {
            HttpSession session = request.getSession();
            boolean successDB = false;
            boolean successDisk = false;

            successDB = isDatabaseUp(request);
            if (!successDB) {
                message = "Database is down.  " + Html.esc(AntwebUtil.getRequestInfo(request));
            }

            successDisk = isWebDirAccessible();
            if (!successDisk) {
                message = "/data/uptime.txt inaccessible.  " + Html.esc(AntwebUtil.getRequestInfo(request));
            }

            if (!successDB || !successDisk) {
                request.setAttribute("message", message);
                returnVal = mapping.findForward("message");
            } else {
                returnVal = mapping.findForward("success");
            }
        }

        String logMsg = "UptimeAction.execute() returnVal:" + returnVal;
        //logMsg = logMsg + " reqInfo:" + HttpUtil.getShortRequestInfo(request);
        if (message == null) {
            s_log.info(logMsg);
        } else {
            logMsg = logMsg + " message:" + message;
            s_log.error(logMsg);
        }

        return returnVal;
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
