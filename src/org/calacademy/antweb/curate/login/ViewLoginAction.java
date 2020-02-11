package org.calacademy.antweb.curate.login;


import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public final class ViewLoginAction extends Action {

    private static Log s_log = LogFactory.getLog(ViewLoginAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
            
        ActionForward a = Check.init("project", request, mapping); if (a != null) return a;
        a = Check.init("geolocale", request, mapping); if (a != null) return a;
        ActionForward c = Check.login(request, mapping); if (c != null) return c;        
        
        Login accessLogin = LoginMgr.getAccessLogin(request);
        
        HttpSession session = request.getSession();

        Login login = null;
        java.sql.Connection connection = null;
        try {
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "ViewLoginAction()");
          
            String idStr = ((SaveLoginForm) form).getId();
            int id = (new Integer(idStr)).intValue();
            //s_log.info("looking up login " + id);
             
            login = (new LoginDb(connection)).getLogin(id);
            session.setAttribute("thisLogin", login);

            // This fetches the full list for select box population
            ArrayList groupList = (new GroupDb(connection)).getAllGroups();
            request.getSession().setAttribute("antwebGroups", groupList);             

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return (mapping.findForward("error"));
        } finally { 		
            DBUtil.close(connection, this, "ViewLoginAction()");
        }
        
        if (accessLogin.isAdmin()) {    
          return (mapping.findForward("viewLogin"));
        } else if (accessLogin.getId() == login.getId()) {
          // This user is visiting their own user account
          if (accessLogin.isCurator()) {
            return (mapping.findForward("viewLogin"));        
          } else {
            return (mapping.findForward("editLogin"));        
          }
        } else {
          // This user is not authorized to view this page
          return (mapping.findForward("error"));
        }

    }
}
