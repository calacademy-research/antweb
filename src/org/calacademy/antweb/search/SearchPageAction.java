package org.calacademy.antweb.search;

import org.calacademy.antweb.AntwebAction;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * /advSearch.do: the advanced search form (search/advancedSearch.jsp), public. Waits until the places are loaded
 * (Check.init), sets the session's status set and project back to all (AntwebProps.resetSessionProperties) and
 * writes the bioregion, country and adm1 pick lists (SearchIncludeFactory; request attributes bioregionGenInc,
 * countryGenInc, adm1GenInc). Read-only. Docs: docs/flows/search.md. Tests:
 * tests/e2e/test_search_pages.py::test_advanced_search_form.
 */
public final class SearchPageAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(SearchPageAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ActionForward a = Check.init(Check.GEOLOCALE, request, mapping); if (a != null) return a;

        AdvancedSearchForm searchForm = (AdvancedSearchForm) form;
        HttpSession session = request.getSession();

        AntwebProps.resetSessionProperties(session);
        
        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("SearchPageAction.execute()");
		try {
          DataSource dataSource = getDataSource(request, "conPool");
          connection = DBUtil.getConnection(dataSource, dbMethodName);

            if (DBStatus.isServerBusy(connection, request)) {
                return mapping.findForward("message");
            }

          session.setAttribute("activeSession", Boolean.TRUE);

          SearchIncludeFactory searchIncludeFactory = new SearchIncludeFactory(connection);
          request.setAttribute("bioregionGenInc", searchIncludeFactory.getBioregionGenInc(searchForm.getBioregion()));
          request.setAttribute("countryGenInc", searchIncludeFactory.getCountryGenInc(searchForm.getCountry()));
          request.setAttribute("adm1GenInc", searchIncludeFactory.getAdm1GenInc(searchForm.getAdm1()));

		} catch (SQLException e) {
			s_log.error("execute() e:" + e);
		} finally {
			DBUtil.close(connection, this, dbMethodName);
		}
        
        return mapping.findForward("success");
    }

}
