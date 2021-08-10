package org.calacademy.antweb.curate.orphans;

import java.io.IOException;

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
    
import java.util.*;
import java.sql.*;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public final class DupedGeneraAction extends Action {

    private static Log s_log = LogFactory.getLog(DupedGeneraAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        boolean isLoggedIn = LoginMgr.isLoggedIn(request);
        if (!isLoggedIn) {
          return (mapping.findForward("notLoggedIn"));
        }
        
        HttpSession session = request.getSession();

        ArrayList orphanTaxonList = new ArrayList();
        java.sql.Connection connection = null;
                        
        try {
          javax.sql.DataSource dataSource = getDataSource(request, "conPool");
          connection = DBUtil.getConnection(dataSource, "DupedGeneraAction.execute()");

          OrphansDb orphansDb = new OrphansDb(connection);
            
          //Take care of the deletion first.
          if (form instanceof OrphanTaxonsForm) {
            OrphanTaxonsForm theForm = (OrphanTaxonsForm) form;
            String action = theForm.getAction();
            String taxonName = theForm.getTaxonName();
            String source = theForm.getSource();
            if (action != null) {       
              if (action.equals("delete")) {
                 if ((taxonName != null) && (!"".equals(taxonName))) {
                   orphansDb.deleteTaxon(taxonName);
                 }
                 // if ((source != null) && (!"".equals(source))) {                   
                 //   orphansDb.deleteOrphanedGeneraFromSource(source);
                 // }
              }
            }
          }

          orphanTaxonList = orphansDb.getDupedGeneraList();
          request.setAttribute("orphans", orphanTaxonList);
          return (mapping.findForward("success"));
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return (mapping.findForward("error"));
        } finally { 		
            DBUtil.close(connection, this, "DupedGeneraAction.execute()");
        }
    }

}

