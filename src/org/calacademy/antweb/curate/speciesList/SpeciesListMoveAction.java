package org.calacademy.antweb.curate.speciesList;

import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public class SpeciesListMoveAction extends SpeciesListSuperAction {

  private static final Log s_log = LogFactory.getLog(SpeciesListMoveAction.class);

  public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) {

      HttpSession session = request.getSession();

      ActionForward c = Check.curator(request, mapping); if (c != null) return c;
      Login accessLogin = LoginMgr.getAccessLogin(request);
      int loginId = accessLogin.getId();
      //Group accessGroup = accessLogin.getGroup();

      DynaActionForm df = (DynaActionForm) form;
      String fromTaxonName = (String) df.get("fromTaxonName");
      String toTaxonName = (String) df.get("toTaxonName");

      Connection connection = null;
      try {
        DataSource dataSource = getDataSource(request, "conPool");
        connection = DBUtil.getConnection(dataSource, "SpeciesListMoveAction.execute()");      
        TaxonDb taxonDb = new TaxonDb(connection);

        Taxon fromTaxon = taxonDb.getTaxon(fromTaxonName);
        Taxon toTaxon = taxonDb.getTaxon(toTaxonName);

        if (fromTaxon == null) {
          String message = "fromTaxonName:" + Html.esc(fromTaxonName) + " not found";
          s_log.warn(message);
          request.setAttribute("message", message);
          return mapping.findForward("message");
        }

        String message = null;
        String action = (String) df.get("action");
        // save (preview) and confirm only by POST, so the CSRF filter checks the form token.
        if (("save".equals(action) || "confirm".equals(action)) && !HttpUtil.isPost(request)) {
          request.setAttribute("message", "Use the form on the Move page to rename a taxon.");
          return mapping.findForward("message");
        }
        // A rename or combine changes the taxon everywhere on AntWeb: administrators only.
        if ("confirm".equals(action) && !accessLogin.isAdmin()) {
          request.setAttribute("message", "Only an administrator may rename or combine taxa.");
          return mapping.findForward("message");
        }
        if ("save".equals(action)) {
          if (toTaxonName == null) {
            message = "To Move a taxon, you must select a valid taxon";        
            message = "<b><font color=red>" + message + "</font></b>";
          } else {
            message = saveTaxon(connection, loginId, fromTaxonName, toTaxonName, false); 
            message += "<br><br><form method='POST' action='" + AntwebProps.getDomainApp() + "/speciesListMove.do'>"
              + Csrf.field(request)
              + "<input type='hidden' name='fromTaxonName' value='" + Html.escAttr(fromTaxonName) + "'>"
              + "<input type='hidden' name='toTaxonName' value='" + Html.escAttr(toTaxonName) + "'>"
              + "<input type='hidden' name='action' value='confirm'>"
              + "<input type='submit' value='Confirm'></form>" 
              + "<br><br>  Back to <a href='" + AntwebProps.getDomainApp() 
              + "/speciesListTool.do'>Species List Tool</a>.";
            request.setAttribute("message", message);
            return mapping.findForward("message");
          }
        } else if ("confirm".equals(action)) {
          if (toTaxonName == null || "".equals(toTaxonName)){
             message = "To Move a taxon, you must select a valid taxon";        
             message = "<b><font color=red>" + message + "</font></b>";
           } else {
            message = saveTaxon(connection, loginId, fromTaxonName, toTaxonName, true); 
            message += "<br><br>  Back to <a href='" + AntwebProps.getDomainApp() 
              + "/speciesListTool.do'>Species List Tool</a>.";
            request.setAttribute("message", message);
            return mapping.findForward("message");
          }
        } else if ("back".equals(action)) {
            return mapping.findForward("speciesListTool");
        } else {
          request.setAttribute("fromTaxon", fromTaxon);   
          request.setAttribute("toTaxon", toTaxon);   
        }

        if (message != null) {
          request.setAttribute("message", message); 
        }
        //putLookupDataInRequest(request, connection);
 
      } catch (SQLException e) {
        s_log.error("execute() e:" + e);
        AntwebUtil.logStackTrace(e);
      } finally {
        DBUtil.close(connection, "SpeciesListMoveAction.execute()");
      }

      return mapping.findForward("speciesListMove");
  }  

  private String saveTaxon(Connection connection, int loginId, String fromTaxonName
    , String toTaxonName, boolean confirm) 
    throws SQLException {
 
    // update taxon record.  Set taxonName and species
    // update all proj_taxon records
    
    String message = "";

    Statement stmt = DBUtil.getStatement(connection, "SpeciesListMoveAction.saveTaxon()");
    try {

/*  skip all checks for now.

        // first, fetch the subfamily for this genus
        String subfamily = "";
        String query = "select distinct subfamily from taxon where genus = '" + genus + "'";
        ResultSet rset1 = stmt.executeQuery(query);
        int count = 0;

        message = "Improper count for genus:" + genus + ".  <br>";
        while (rset1.next()) {
            subfamily = rset1.getString(1);
            if (count > 0) message += ", ";
            message += subfamily;
            ++count;
            if (count != 1) {
              s_log.warn(message);
              return message;
            }
        }
*/
        
        //String toTaxonName = subfamily + genus + " " + species;

        LogMgr.appendLog("speciesListMapping.txt", "moveTaxon - " + DateUtil.getFormatDateTimeStr() + " curatorId:" + loginId 
           + " fromTaxonName:" + fromTaxonName + " toTaxonName:" + toTaxonName); 
        s_log.warn("moveTaxon() fromTaxonName:" + fromTaxonName + " toTaxonName:" + toTaxonName);
        TaxonDb taxonDb = new TaxonDb(connection);
        if (!confirm) {
            message = taxonDb.prepareMoveTaxon(fromTaxonName, toTaxonName);
        } else {
          Taxon taxon = taxonDb.getTaxon(toTaxonName);
          if (taxon != null) {
              message = taxonDb.combineTaxa(fromTaxonName, toTaxonName);
          } else {
              message = taxonDb.renameTaxon(fromTaxonName, toTaxonName);
          }
        } 

    } finally {
        DBUtil.close(stmt, "SpeciesListMoveAction.saveTaxon()");
    }       
     
    return message;
  }
  
}
