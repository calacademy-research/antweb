package org.calacademy.antweb.search;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class SearchPageAction extends Action {

    private static Log s_log = LogFactory.getLog(SearchPageAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ActionForward a = Check.init(Check.GEOLOCALE, request, mapping); if (a != null) return a;

        AdvancedSearchForm searchForm = (AdvancedSearchForm) form;
        HttpSession session = request.getSession();

        AntwebProps.resetSessionProperties(session);
        
        Connection connection = null;
		try {
          javax.sql.DataSource dataSource = getDataSource(request, "conPool");
          if (DBUtil.isServerBusy(dataSource, request)) {
            return mapping.findForward("message");            
          }			
          connection = DBUtil.getConnection(dataSource, "SearchPageAction.execute()");

          session.setAttribute("activeSession", Boolean.TRUE);

          SearchIncludeFactory searchIncludeFactory = new SearchIncludeFactory(connection);
          request.setAttribute("bioregionGenInc", searchIncludeFactory.getBioregionGenInc(searchForm.getBioregion()));
          request.setAttribute("countryGenInc", searchIncludeFactory.getCountryGenInc(searchForm.getCountry()));
          request.setAttribute("adm1GenInc", searchIncludeFactory.getAdm1GenInc(searchForm.getAdm1()));

		} catch (SQLException e) {
			s_log.error("execute() e:" + e);
		} finally {
			DBUtil.close(connection, this, "SearchPageAction.execute()");
		}
        
        return mapping.findForward("success");
    }

}
