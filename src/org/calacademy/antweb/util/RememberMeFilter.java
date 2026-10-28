package org.calacademy.antweb.util;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * "Keep me logged in" on pages that are not Struts actions.
 *
 * SessionRequestFilter (which calls RememberMeMgr.autoLogin) is mapped to *.do only. The home
 * page (https://www.antweb.org/, the welcome file index.jsp) and the other JSPs people open
 * directly never pass through it, so after the container restarts (all sessions gone) those
 * pages showed a remembered user as logged out, with a "Login" link. This filter, mapped in
 * web.xml to "/" and *.jsp (REQUEST only, so not the JSPs that .do actions forward to), makes
 * the same call there. Without the AW_REMEMBER cookie, or with a login already in the
 * session, autoLogin returns at once.
 */
public class RememberMeFilter implements Filter {

    private static final Log s_log = LogFactory.getLog(RememberMeFilter.class);

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (req instanceof HttpServletRequest request && res instanceof HttpServletResponse response) {
            try {
                RememberMeMgr.autoLogin(request, response);
            } catch (RuntimeException e) {
                // Never let "keep me logged in" break a page.
                s_log.warn("doFilter() e:" + e);
            }
            try {
                // End a stale login session (and start its replacement) before any of the page is sent, as
                // SessionRequestFilter does for .do pages; otherwise the header's check did it after the home page had
                // started and the next JSP could not get a session (page cut off, staging e2e 2026-10-27).
                LoginMgr.getAccessLogin(request);
            } catch (RuntimeException e) {
                s_log.warn("doFilter() getAccessLogin e:" + e);
            }
        }
        chain.doFilter(req, res);
    }
}
