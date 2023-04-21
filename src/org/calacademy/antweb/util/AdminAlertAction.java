package org.calacademy.antweb.util;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class AdminAlertAction extends Action {

    private static final Log s_log = LogFactory.getLog(AdminAlertAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward c = Check.admin(request, mapping); if (c != null) return c;

		Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("AdminAlertAction.execute()");
		try {
			DataSource dataSource = getDataSource(request, "conPool");
			connection = DBUtil.getConnection(dataSource, dbMethodName);

            DynaActionForm df = (DynaActionForm) form;
            String action = (String) df.get("action"); 

            if ("removeAll".equals(action)) {
                AdminAlertMgr.removeAll(connection);
            }             

            if ("remove".equals(action)) {
                int id = 0;
                Integer integerId = (Integer) df.get("id");
                if (integerId != null) id = integerId;
                AdminAlertMgr.remove(id, connection);
            }

            if ("refresh".equals(action)) {
                AdminAlertMgr.populate(connection);
            }             
        
		} catch (SQLException | ClassCastException e) {
			s_log.error("execute() e:" + e);
		} finally {
			DBUtil.close(connection, this, dbMethodName);
		}        
 
    	return mapping.findForward("success");
	}
	
}
