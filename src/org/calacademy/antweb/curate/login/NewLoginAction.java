package org.calacademy.antweb.curate.login;

import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import java.sql.*;
import java.util.ArrayList;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

/**
 * This class takes the UploadForm and retrieves the text value
 * and file attributes and puts them in the request for the display.jsp
 * page to display them
 *
 */

public class NewLoginAction extends Action {

    private static final Log s_log = LogFactory.getLog(NewLoginAction.class);

    public ActionForward execute( ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) {

        ActionForward a = Check.init("project", request, mapping); if (a != null) return a;
        
        Connection connection = null;
        String query;
        int newLoginId = 0;
        Login login = null;
        
        try {         
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "NewLoginAction");

            LoginDb loginDb = new LoginDb(connection);
            newLoginId = loginDb.getNewLoginId();

            if (newLoginId > 0) {
              login = new Login();
              login.init();
              login.setId(newLoginId);
              request.getSession().setAttribute("thisLogin", login);
              request.setAttribute("isNewLogin", "true");

              ArrayList groupList = new GroupDb(connection).getAllGroups();
              request.getSession().setAttribute("antwebGroups", groupList);   
            }
          
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return mapping.findForward("error");
        } finally { 		
            DBUtil.close(connection, this, "NewLoginAction");
        }

        if (login != null) {
          return mapping.findForward("success");
        } else {
          return mapping.findForward("failure");
        }
    }

}
