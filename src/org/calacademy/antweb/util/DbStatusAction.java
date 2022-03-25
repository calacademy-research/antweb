package org.calacademy.antweb.util;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

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
        
  	    HttpSession session = request.getSession();
        DataSource dataSource = getDataSource(request, "conPool");
		
        setCpDiagnosticsAttr(dataSource, request);

        try {       
          request.setAttribute("isServerBusy", DBUtil.isServerBusy(dataSource, request));
        } catch (SQLException e) {
          s_log.error("e:" + e);
        }

        String mySqlProcessListHtml = getMySqlProcessListHtml(request);
        request.setAttribute("mySqlProcessListHtml", mySqlProcessListHtml);

		if (success) {
			return mapping.findForward("success");
		} else {
			return mapping.findForward("failure");
		}
	}
	
	private void setCpDiagnosticsAttr(DataSource dataSource, HttpServletRequest request) {
        String cpDiagnostics = DBUtil.getCpDiagnosticsAttr(dataSource);
        request.setAttribute("cpDiagnostics", cpDiagnostics);
	}

    public String getMySqlProcessListHtml(HttpServletRequest request) {
        String mySqlProcessListHtml = null;
		DataSource dataSource = getDataSource(request, "conPool");
        Connection connection = null;
        try {
            connection = DBUtil.getConnection(dataSource, "DbStatusAction.getMySqlProcessListHtml()", HttpUtil.getTarget(request));
            mySqlProcessListHtml = AntwebFunctions.getMysqlProcessListHtml(connection);
            //AntwebFunctions.logMysqlProcessList(connection);
        } catch (SQLException e) {
            s_log.error("getMySqlProcessListHtml() e:" + e);
        } finally {
            DBUtil.close(connection, this, "DbStatusAction.getMySqlProcessListHtml()");
        }
        return mySqlProcessListHtml;
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
