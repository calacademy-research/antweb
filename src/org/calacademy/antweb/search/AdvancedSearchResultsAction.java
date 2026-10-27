package org.calacademy.antweb.search;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

/**
 * advancedSearchResults.do: the last advanced search results shown again (2026-10-27, finding F25).
 *
 * Until then this was a plain forward to advancedSearchResults.jsp, which shows the rows kept in the session.
 * A paged specimen search keeps only its current page there (200 rows), so a search of 3,000 specimens came
 * back as "Showing 1-200 of 200" with no Next link. Now, when the session holds a paged search and the request
 * has no parameters, the browser is sent to that search again (advancedSearch.do with its query string and
 * page): the total, Next and sorting come from the database as on the first visit. Requests with parameters
 * (Map and Compare views, the sort of an unpaged list) and sessions without a paged search are unchanged.
 */
public final class AdvancedSearchResultsAction extends Action {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        HttpSession session = request.getSession(false);
        SearchPaging paging = null;
        if (session != null && session.getAttribute("advancedSearchPaging") instanceof SearchPaging) {
            paging = (SearchPaging) session.getAttribute("advancedSearchPaging");
        }
        String url = SearchPaging.reshowUrl("/advancedSearch.do", request.getQueryString(), paging);
        if (url != null) return new ActionForward(url, true);
        return mapping.findForward("success");
    }
}
