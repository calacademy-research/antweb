package org.calacademy.antweb.curate.speciesList;

import java.util.*;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public class SpeciesListHistoryAction extends SpeciesListToolAction {

  private static final Log s_log = LogFactory.getLog(SpeciesListHistoryAction.class);

  public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) {

    HttpSession session = request.getSession();
    
    ActionForward c = Check.login(request, mapping); if (c != null) return c;   
    Login accessLogin = LoginMgr.getAccessLogin(request);

    int loginId = accessLogin.getId();

    SpeciesListToolForm historyForm = (SpeciesListToolForm) form;
    if (historyForm == null) historyForm = new SpeciesListToolForm();

    Connection connection = null;
    try {
       DataSource dataSource = getDataSource(request, "mediumConPool");
       connection = DBUtil.getConnection(dataSource, "SpeciesListHistoryAction.execute()");

       ProjTaxonLogDb projTaxonLogDb = new ProjTaxonLogDb(connection);
       GeolocaleTaxonLogDb geolocaleTaxonLogDb = new GeolocaleTaxonLogDb(connection);

       String speciesListName = historyForm.getSpeciesListName();
       int projLogId = historyForm.getProjLogId();       
       int geoLogId = historyForm.getGeoLogId();
       if ((projLogId == -1 || geoLogId == -1) && AntwebProps.isDevOrStageMode()) {       
         projTaxonLogDb.deleteAllLogs();
         geolocaleTaxonLogDb.deleteAllLogs();

         s_log.debug("execute() all logs deleted");
         //return mapping.findForward("speciesListHistory");
       }
         
       String displaySubfamily = (String) request.getSession().getAttribute("displaySubfamily");
       s_log.debug("execute() form:" + form + " speciesListName:" + speciesListName + " projLogId:" + projLogId + " geoLogId:" + geoLogId + " displaySubfamily:" + displaySubfamily);
       
       if (speciesListName != null) {
         if (Project.isProjectName(speciesListName)) {
           ArrayList<ProjTaxonLog> projTaxonLogs = projTaxonLogDb.getProjTaxonLogs(speciesListName, projLogId); //, displaySubfamily);
           // These will be used in the speciesListHistory-body.jsp
           request.setAttribute("projTaxonLogs", projTaxonLogs);
         } else {
           int geolocaleId = GeolocaleMgr.getGeolocaleId(speciesListName);         
if (geolocaleId == 0) s_log.warn("execute() geolocaleId:0 for speciesListName:" + speciesListName);
           ArrayList<GeolocaleTaxonLog> geolocaleTaxonLogs = geolocaleTaxonLogDb.getGeolocaleTaxonLogs(geolocaleId, geoLogId); //, displaySubfamily);
           // These will be used in the speciesListHistory-body.jsp
           request.setAttribute("geolocaleTaxonLogs", geolocaleTaxonLogs);
         }
       }

       //request.setAttribute("speciesListList", (new SpeciesListDb(connection)).fetchSpeciesListsStr(accessLogin));
       
       SpeciesListToolProps toolProps = (SpeciesListToolProps) session.getAttribute("speciesListToolProps");

       //String val = getRefSpeciesListName(historyForm);
       //A.log("SpeciesListHistoryAction.execute() val:" + val); 
       //toolProps.setRefSpeciesListName(val);
       
       request.setAttribute("curatorList", projTaxonLogDb.getCuratorLogins(accessLogin));

    } catch (SQLException e) {
      s_log.error("execute() e:" + e);
      AntwebUtil.logStackTrace(e);
      DBUtil.rollback(connection);
    } finally {
      DBUtil.close(connection, "SpeciesListHistoryAction.execute()");
    }
  
    return mapping.findForward("speciesListHistory");
  }
  
}

