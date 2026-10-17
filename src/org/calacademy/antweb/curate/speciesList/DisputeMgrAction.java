package org.calacademy.antweb.curate.speciesList;

import org.calacademy.antweb.AntwebAction;

import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public class DisputeMgrAction extends AntwebAction {

  private static final Log s_log = LogFactory.getLog(DisputeMgrAction.class);

  public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) {

    ActionForward c = Check.curator(request, mapping); if (c != null) return c;
    //Login accessLogin = LoginMgr.getAccessLogin(request);

    Connection connection = null;
    try {
       DataSource dataSource = getDataSource(request, "conPool");
       connection = DBUtil.getConnection(dataSource, "DisputeMgrAction.execute()");

       ProjTaxonLogDb projTaxonLogDb = new ProjTaxonLogDb(connection);

       DynaActionForm df = (DynaActionForm) form;
        
       String projectName = (String) df.get("projectName");
       String taxonName = (String) df.get("taxonName");  
       String action = (String) df.get("action");
       if ("remove".equals(action)) {
         // Changes data: POST only (CSRF filter), and only for a list assigned to this curator.
         if (!HttpUtil.isPost(request)) {
           request.setAttribute("message", "Use the remove button on the Dispute Manager page.");
           return mapping.findForward("message");
         }
         if (!SpeciesListAccess.canEdit(LoginMgr.getAccessLogin(request), projectName)) {
           request.setAttribute("message", SpeciesListAccess.NOT_ASSIGNED);
           return mapping.findForward("message");
         }
         projTaxonLogDb.removeDispute(projectName, taxonName);
       } 

       request.setAttribute("disputes", projTaxonLogDb.getDisputes());

    } catch (SQLException e) {
      s_log.error("execute() e:" + e);
      AntwebUtil.logStackTrace(e);
      DBUtil.rollback(connection);
    } finally {
      DBUtil.close(connection, "DisputesMgrAction.execute()");
    }
  
    return mapping.findForward("disputeMgr");
  }
  
}

