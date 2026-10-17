package org.calacademy.antweb.util;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

/**
 * Actions that change data must not run on a GET: SessionRequestFilter checks the CSRF token
 * only on POST, and a browser sends the session cookie on a top-level cross-site GET.
 *
 * Admin pages still have many plain links to such actions. Instead of running, a GET now gets a
 * short page with a button that sends the same request again by POST, with the form token.
 * Server-to-server calls (the Scheduler's secureCode) are checked by the caller before this.
 */
public final class PostOnly {

    private PostOnly() {}

    /**
     * Null for a POST (go ahead). For any other method, a forward to the "message" page with a
     * button that repeats this request as a POST. what is plain text describing the action.
     */
    public static ActionForward confirm(HttpServletRequest request, ActionMapping mapping, String what) {
        if (HttpUtil.isPost(request)) return null;
        request.setAttribute("message", confirmHtml(request, what));
        return mapping.findForward("message");
    }

    /** The url of this request (path and query string, any _csrf parameter removed). */
    static String selfUrl(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) uri = "";
        String query = request.getQueryString();
        String url = (query == null || query.isEmpty()) ? uri : uri + "?" + query;
        return Csrf.stripParam(url);
    }

    static String confirmHtml(HttpServletRequest request, String what) {
        return "<div style=\"margin:1em 0\"><p>" + Html.esc(what) + "</p>"
            + "<p>This changes data, so it runs only from a button on AntWeb.</p>"
            + "<form method=\"post\" action=\"" + Html.escAttr(selfUrl(request)) + "\">"
            + Csrf.field(request)
            + "<input type=\"submit\" value=\"Run it\"> &nbsp; <a href=\"" + AntwebProps.getDomainApp() + "/curate.do\">Cancel</a>"
            + "</form></div>";
    }
}
