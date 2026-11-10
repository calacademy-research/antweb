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
    
/**
 * /uptime.do: health check for the uptime monitors. Public. Checks the database (a query on table image, then
 * AntwebMgr.populate) and the data directory (data/uptime.txt must contain "success"); answers util/uptime.jsp
 * (OK), or the message page when one fails. Read-only. The admin switch that made it fail on purpose (fail=1,
 * util/uptimeFail.jsp, toggled from Server Status) was removed on 2026-11-09 (Brian Fisher). AntwebMgr.webPopulate()
 * calls this page on the local server to load AntWeb's data. Docs: docs/catalog/actions.csv. Tests:
 * tests/e2e/test_curate_cleanup.py, tests/e2e/test_tool_pages_style.py.
 */
public final class UptimeAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(UptimeAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward returnVal;
        String message = null;

        boolean successDB = isDatabaseUp(request);
        if (!successDB) {
            message = "Database is down.  " + Html.esc(AntwebUtil.getRequestInfo(request));
        }

        boolean successDisk = isWebDirAccessible();
        if (!successDisk) {
            message = "/data/uptime.txt inaccessible.  " + Html.esc(AntwebUtil.getRequestInfo(request));
        }

        if (!successDB || !successDisk) {
            request.setAttribute("message", message);
            returnVal = mapping.findForward("message");
        } else {
            returnVal = mapping.findForward("success");
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
