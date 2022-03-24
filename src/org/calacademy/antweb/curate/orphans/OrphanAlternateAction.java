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

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public final class OrphanAlternateAction extends Action {

    private static Log s_log = LogFactory.getLog(OrphanAlternateAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ActionForward c = Check.login(request, mapping); if (c != null) return c;  
        
        HttpSession session = request.getSession();

        ArrayList<Taxon> orphanTaxonList = new ArrayList();
        Connection connection = null;
                        
        try {
          DataSource dataSource = getDataSource(request, "conPool");
          connection = DBUtil.getConnection(dataSource, "OrphanAlterateAction.execute()");

          OrphansDb orphansDb = new OrphansDb(connection);
            
          //Take care of the deletion first.
          if (form instanceof OrphanTaxonsForm) {
            OrphanTaxonsForm theForm = (OrphanTaxonsForm) form;
            String action = theForm.getAction();
            String taxonName = theForm.getTaxonName();
            String subfamily = theForm.getSubfamily();
            String source = theForm.getSource();

            if (theForm.getBrowse() != null && theForm.getBrowse().equals("browse")) {
               String newTaxonName = null;
               int inaeIndex = taxonName.indexOf("inae");
               if (taxonName.contains("inae")) {
                 newTaxonName = subfamily + taxonName.substring(inaeIndex + 4);
               }
               s_log.debug("execute() browse newTaxonName:" + newTaxonName);
               
               Taxon fetchTaxon = new TaxonDb(connection).getTaxon(taxonName);
               if (fetchTaxon != null) {
                  String url = fetchTaxon.getUrl();
                  response.sendRedirect(url);  
                  return null;
               }
            } else {
              if (action != null && action.equals("moveOrDelete")) {
			    String newTaxonName = null;
			    int inaeIndex = taxonName.indexOf("inae");
			    if (taxonName.contains("inae")) {
			 	  newTaxonName = subfamily + taxonName.substring(inaeIndex + 4);
			    }
			    String statusMessage = null;
			    if (newTaxonName != null) {
				  s_log.debug("execute() moving taxonName:" + taxonName + " to :" + newTaxonName);
				  orphansDb.moveTaxonSupportingDataToAlternate(newTaxonName, taxonName);                   
				  statusMessage = "<b>" + taxonName + " moved to <a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + newTaxonName + "'>" + newTaxonName + "</a></b><br><br>";
			    } else {
			      statusMessage = "New taxonName not found for taxonName:" + taxonName + ". No action taken.";
			    }
			    request.setAttribute("statusMessage", statusMessage);
              }
            }
          }
          
          orphanTaxonList = orphansDb.getOrphanAlternatesList();
          request.setAttribute("orphans", orphanTaxonList);

          ArrayList<String> antwebSubfamilyList = new ArrayList<>();
          UploadDb uploadDb = new UploadDb(connection);
          for (Taxon orphan : orphanTaxonList) {
            String antwebSubfamily = uploadDb.getAntwebSubfamily(orphan.getGenus());
            antwebSubfamilyList.add(antwebSubfamily);
          }
          request.setAttribute("antwebSubfamilies", antwebSubfamilyList);

          putLookupDataInRequest(request, connection);
          
          return mapping.findForward("success");

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
        } finally {
            DBUtil.close(connection, "OrphanAlterateAction.execute()");
        }

        return mapping.findForward("error");
    }


    private void putLookupDataInRequest(HttpServletRequest request, Connection connection) 
        throws SQLException {
        Statement stmt = null;
        ArrayList<String> subfamilies = new ArrayList<>();
        try {        
          stmt = DBUtil.getStatement(connection, "OrphanAlternateAction.putLookupDataInRequest");
          String query = "select distinct subfamily from taxon where family='formicidae' and rank = 'subfamily' and status = 'valid'";
          ResultSet rset = stmt.executeQuery(query);
          while (rset.next()) {
            String subfamily = rset.getString(1);
            subfamilies.add(subfamily);
          }
        } finally {
          DBUtil.close(stmt, "OrphanAlternateAction.putLookupDataInRequest");
        }
        request.setAttribute("subfamilies", subfamilies);
    }
    
    
}

