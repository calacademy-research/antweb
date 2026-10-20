package org.calacademy.antweb.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

/**
 * Sends the browser to another page of AntWeb with a permanent redirect (301). The target path
 * is the mapping's parameter, for example parameter="/advSearch.do". Query strings are not
 * passed on: the old address's parameters do not apply to the new page.
 * Used for old addresses that are kept working (2026-10-19: /search.do).
 */
public final class RedirectAction extends Action {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        response.setHeader("Location", AntwebProps.getDomainApp() + target(mapping.getParameter()));
        return null;
    }

    /** The parameter when it is a local path ("/x.do"), else the home page. */
    public static String target(String parameter) {
        if (parameter == null || !parameter.matches("/[A-Za-z0-9_./-]*")) return "/";
        if (parameter.startsWith("//") || parameter.contains("..")) return "/";
        return parameter;
    }
}
