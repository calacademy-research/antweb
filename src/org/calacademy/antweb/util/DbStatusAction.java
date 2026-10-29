package org.calacademy.antweb.util;

import org.calacademy.antweb.AntwebAction;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import com.mchange.v2.c3p0.PooledDataSource;
import org.apache.struts.action.*;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.home.*;
    
/**
 * /dbStatus.do: database status page (util/dbStatus.jsp), admins only (Check.admin): the MySQL process list and
 * the three connection pools (conPool, mediumConPool, longConPool) with the server busy report (DBStatus).
 * name=resetDS (or resetDS1, 2, 3) hard-resets the pools, POST only (PostOnly.confirm). Docs:
 * docs/catalog/actions.csv. Tests: tests/e2e/test_curate_merge.py, test_curator.py, test_request_handling.py.
 */
public final class DbStatusAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(DbStatusAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        // Administrators only (the JSP check alone ran after this action had done its work).
        ActionForward c = Check.admin(request, mapping); if (c != null) return c;

		boolean success = true;
        s_log.info("in DbStatusAction");

        String target = HttpUtil.getTarget(request);
        DynaActionForm df = (DynaActionForm) form;

        String op = (String) df.get("name");
        if (op == null) op = "";

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("DbStatusAction.execute()");
        try {
            HttpSession session = request.getSession();
            DataSource dataSource1 = getDataSource(request, "conPool");
            DataSource dataSource2 = getDataSource(request, "mediumConPool");
            DataSource dataSource3 = getDataSource(request, "longConPool");

            if (op.contains("resetDS")) {
                // Resetting the pools breaks requests in flight: POST only (CSRF-checked).
                ActionForward confirm = PostOnly.confirm(request, mapping, "Hard reset database connection pool: " + op);
                if (confirm != null) return confirm;
                if (op.equals("resetDS") || op.equals("resetDS1")) ((PooledDataSource) dataSource1).hardReset();
                if (op.equals("resetDS") || op.equals("resetDS2")) ((PooledDataSource) dataSource2).hardReset();
                if (op.equals("resetDS") || op.equals("resetDS3")) ((PooledDataSource) dataSource3).hardReset();
                String message = "conPool hardReset():" + Html.esc(op);
                s_log.warn("execute() message:" + message);
                request.setAttribute("message", message);
                return mapping.findForward("message");
            }

            connection = DBUtil.getConnection(dataSource1, dbMethodName, target);
            String mySqlProcessListHtml = DBStatus.getMysqlProcessListHtml(connection);
            request.setAttribute("mySqlProcessListHtml", mySqlProcessListHtml);

            request.setAttribute("cpDiagnostics", DBStatus.getCpDiagnosticsAttr(dataSource1));
            request.setAttribute("mediumConPoolDiagnostics", DBStatus.getCpDiagnosticsAttr(dataSource2));
            request.setAttribute("longConPoolDiagnostics", DBStatus.getCpDiagnosticsAttr(dataSource3));

            request.setAttribute("isServerBusy", DBStatus.isServerBusy(dataSource1, dataSource2, dataSource3));
            request.setAttribute("message", DBStatus.getServerBusyReport());
        } catch (SQLException e) {
            s_log.error("e:" + e);
        } finally {
            DBUtil.close(connection, dbMethodName);
        }

		if (success) {
			return mapping.findForward("success");
		} else {
			return mapping.findForward("failure");
		}
	}

}
