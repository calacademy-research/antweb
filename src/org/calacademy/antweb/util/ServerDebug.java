package org.calacademy.antweb.util;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.curate.OperationDetails;
import org.calacademy.antweb.home.OperationLock;
import org.calacademy.antweb.home.OperationLockDb;
import org.calacademy.antweb.home.ServerDb;
import org.calacademy.antweb.upload.UploadAction;
import org.calacademy.antweb.upload.UploadDetails;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.*;

public final class ServerDebug extends Action {

    private static final Log s_log = LogFactory.getLog(ServerDebug.class);

	// Maintain this list. It drives the links on the Server Status Page.
	private static String[] s_debugs = {"isDebug", "logGetConns", "debugUserAgents", "isBlockUnLoggedIn"};

	public static String[] getDebugs() { return s_debugs; }

	private static String s_debug = "";
	public static String getDebug() {
		return s_debug;
	}
    public static void setDebug(String debug) {
		s_debug = debug;
	}
	public static boolean isDebug(String option) {
		if (s_debug != null && s_debug.equals(option)) return true;
		return false;
	}

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

		HttpUtil.setUtf8(request, response);

		DynaActionForm df = (DynaActionForm) form;

		String message = null;

		HttpSession session = request.getSession();
		HttpUtil.setUtf8(request, response);

		Connection connection = null;


		//Connection connection = null;
		String action = (String) df.get("action");
		String param = (String) df.get("param");

        String dbMethodName = "ServerDebug.execute()";

        try {

			DataSource dataSource = getDataSource(request, "conPool");
			connection = DBUtil.getConnection(dataSource, dbMethodName);

			if (action != null) {
				if ("setDebug".equals(action)) {
					message = "serverDebug set:" + param;
					ServerDb.setServerDebug(param, connection);
                    setDebug(param);
				}
			}

		} catch (SQLException e) {
			s_log.error("execute() action:" + action + " e:" + e);
			AntwebUtil.errorStackTrace(e);
		} finally {
			DBUtil.close(connection, this, dbMethodName);
		}

		request.setAttribute("message", message);
		return mapping.findForward("success");
	}

}
