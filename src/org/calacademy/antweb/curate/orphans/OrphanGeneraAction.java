package org.calacademy.antweb.curate.orphans;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
import java.util.*;
import java.sql.*;

import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public final class OrphanGeneraAction extends Action {

    private static final Log s_log = LogFactory.getLog(OrphanGeneraAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ActionForward c = Check.login(request, mapping); if (c != null) return c;  
        
        HttpSession session = request.getSession();

        ArrayList<Taxon> orphanTaxonList = new ArrayList<>();
        Connection connection = null;
                        
        try {
          DataSource dataSource = getDataSource(request, "conPool");
          connection = DBUtil.getConnection(dataSource, "OrphanGeneraAction()");

          OrphansDb orphansDb = new OrphansDb(connection);
            
          //Take care of the deletion first.
          if (form instanceof OrphanTaxonsForm) {
            OrphanTaxonsForm theForm = (OrphanTaxonsForm) form;
            String action = theForm.getAction();
            String taxonName = theForm.getTaxonName();
            String source = theForm.getSource();
            if (action != null) {       
              if (action.equals("delete")) {
                 if (taxonName != null && !"".equals(taxonName)) {
                   orphansDb.deleteTaxon(taxonName);
                 }
                 if (source != null && !"".equals(source)) {
                   orphansDb.deleteOrphanedGeneraFromSource(source);
                 }
              }
            }
          }
            
          orphanTaxonList = orphansDb.getOrphanGeneraList();
            
          request.setAttribute("orphans", orphanTaxonList);

          return mapping.findForward("success");

          
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return mapping.findForward("error");
        } finally { 		
            DBUtil.close(connection, this, "OrphanGeneraAction()");
        }
    }

}

