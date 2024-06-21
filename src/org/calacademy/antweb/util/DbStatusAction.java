package org.calacademy.antweb.util;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import com.mchange.v2.c3p0.PooledDataSource;
import org.apache.struts.action.*;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.home.*;
    
public final class DbStatusAction extends Action {

    private static final Log s_log = LogFactory.getLog(DbStatusAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

		boolean success = true;
        s_log.info("in DbStatusAction");

        String target = HttpUtil.getTarget(request);
        DynaActionForm df = (DynaActionForm) form;

        String op = (String) df.get("name");
        if (op != null) {
          if (op.equals("holdOpenConnection")) {
            df.set("name", null);
            s_log.warn("Holding open connection");
            holdOpenConnection(request, response);
          } else if (op.equals("closeOpenConnection")) {
            closeOpenConnection();
          }
        }

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("DbStatusAction.execute()");
        try {
            HttpSession session = request.getSession();
            DataSource dataSource1 = getDataSource(request, "conPool");
            DataSource dataSource2 = getDataSource(request, "mediumConPool");
            DataSource dataSource3 = getDataSource(request, "longConPool");

            if (op.equals("resetDS")) {
                ((PooledDataSource)dataSource1).hardReset();
                String message = "conPool hardReset()";
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

    private static Connection connection;

    public boolean holdOpenConnection(HttpServletRequest request, HttpServletResponse response) {
        // This should only be done to test connection expiration...
		DataSource dataSource = getDataSource(request, "conPool");
		
        connection = null;
        try {
            connection = DBUtil.getConnection(dataSource, "DbStatusAction.holdOpenConnection()", HttpUtil.getTarget(request));
            new OperationLockDb(connection).getOperationLock();

            s_log.warn("Connection held open");
        } catch (SQLException e) {
            s_log.error("holdOpenConnection() e:" + e);
        }             
        // Finally nothing! Holding open...
        
        return true;
    }

    public void closeOpenConnection() {
      if (connection != null) {
        try {
          s_log.warn("closeOpenConnection");
          DBUtil.close(connection, "DbStatusAction.holdOpenConnection()");
          connection = null;        
        } catch (Exception e) {
          s_log.warn("e:" + e);
        }
      } else s_log.warn("No open connection found.");
    }
}
