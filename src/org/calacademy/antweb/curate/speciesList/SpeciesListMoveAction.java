package org.calacademy.antweb.curate.speciesList;

import javax.servlet.http.*;
import org.apache.struts.action.*;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public class SpeciesListMoveAction extends SpeciesListSuperAction {

  private static Log s_log = LogFactory.getLog(SpeciesListMoveAction.class);

  public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) {

      HttpSession session = request.getSession();

      ActionForward c = Check.login(request, mapping); if (c != null) return c;
      Login accessLogin = LoginMgr.getAccessLogin(request);
      int loginId = accessLogin.getId();
      //Group accessGroup = accessLogin.getGroup();

      DynaActionForm df = (DynaActionForm) form;
      String fromTaxonName = (String) df.get("fromTaxonName");
      String toTaxonName = (String) df.get("toTaxonName");

      java.sql.Connection connection = null;
      try {
        javax.sql.DataSource dataSource = getDataSource(request, "conPool");
        connection = DBUtil.getConnection(dataSource, "SpeciesListMoveAction.execute()");      

        Taxon fromTaxon = Taxon.getDummyInfoInstance(connection, fromTaxonName);
        Taxon toTaxon = Taxon.getDummyInfoInstance(connection, toTaxonName);

        if (fromTaxon == null) {
          String message = "fromTaxonName:" + fromTaxonName + " not found";
          s_log.warn(message);
          request.setAttribute("message", message);
          return (mapping.findForward("message"));
        }

        String message = null;
        String action = (String) df.get("action");
        if ("save".equals(action)) {
          if (toTaxonName == null) {
            message = "To Move a taxon, you must select a valid taxon";        
            message = "<b><font color=red>" + message + "</font></b>";
          } else {
            message = saveTaxon(connection, loginId, fromTaxonName, toTaxonName, false); 
            message += "<br><br><a href='" + AntwebProps.getDomainApp() 
              + "/speciesListMove.do?fromTaxonName=" + fromTaxonName + "&toTaxonName=" + toTaxonName 
              + "&action=confirm'>Confirm</a>" 
              + "<br><br>  Back to <a href='" + AntwebProps.getDomainApp() 
              + "/moveToValid.do'>Move To Valid</a> Tool.";
            request.setAttribute("message", message);
            return (mapping.findForward("message"));
          }
        } else if ("confirm".equals(action)) {
          if ((toTaxonName == null) || ("".equals(toTaxonName))){
             message = "To Move a taxon, you must select a valid taxon";        
             message = "<b><font color=red>" + message + "</font></b>";
           } else {
            message = saveTaxon(connection, loginId, fromTaxonName, toTaxonName, true); 
            message += "<br><br>  Back to <a href='" + AntwebProps.getDomainApp() 
              + "/moveToValid.do'>Move To Valid</a> Tool.";
            request.setAttribute("message", message);
            return (mapping.findForward("message"));
          }
        } else if ("back".equals(action)) {
            return (mapping.findForward("moveToValid"));     
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
          if (Taxon.getDummyInfoInstance(connection, toTaxonName) != null) {
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
