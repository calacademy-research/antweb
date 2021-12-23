package org.calacademy.antweb.curate.speciesList;

import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.util.*;

import org.calacademy.antweb.search.*;

public class SpeciesListToolSearchAction extends Action { //extends SpeciesListToolAction 

  private static Log s_log = LogFactory.getLog(SpeciesListToolSearchAction.class);

  public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) {

    HttpSession session = request.getSession();
    ActionForward c = Check.login(request, mapping); if (c != null) return c; 

    SpeciesListToolForm toolForm = (SpeciesListToolForm) form;
    s_log.debug("SpeciesListToolSearchAction.execute() toolForm:" + toolForm);

    String message = "";
    Connection connection = null;
    try {
      DataSource dataSource = getDataSource(request, "longConPool");
      connection = DBUtil.getConnection(dataSource, "SpeciesListToolSearchAction.execute()");

      boolean goToSearch = "changeRefSpeciesList".equals(toolForm.getAction());
      if (goToSearch) {
          SearchIncludeFactory searchIncludeFactory = new SearchIncludeFactory(connection);
          request.setAttribute("bioregionGenInc", searchIncludeFactory.getBioregionGenInc(toolForm.getBioregion()));
          request.setAttribute("countryGenInc", searchIncludeFactory.getCountryGenInc(toolForm.getCountry()));
          request.setAttribute("adm1GenInc", searchIncludeFactory.getAdm1GenInc(toolForm.getAdm1()));
          return mapping.findForward("advSearch");
      }

      if (message != null) request.setAttribute("message", message);

      s_log.debug("execute() toolForm:" + toolForm);
 
    } catch (SQLException e) {
      s_log.error("execute() e:" + e);
      AntwebUtil.logStackTrace(e);
      message = "SpeciesListToolSearch failed due to e:" + e;
      request.setAttribute("message", message);
      return mapping.findForward("message");      
    } finally {
      DBUtil.close(connection, "SpeciesListToolSearchAction.execute()");
    }
  
    return mapping.findForward("speciesListTool");
  }

}

