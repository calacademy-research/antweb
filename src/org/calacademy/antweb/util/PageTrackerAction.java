package org.calacademy.antweb.util;

import org.calacademy.antweb.AntwebAction;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.apache.struts.action.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * /pageTracker.do: admins only (Check.admin); shows util/pageTracker.jsp, the requests being served right now
 * (PageTracker). PageTracker is switched off in the code (isOn = false), so the list is always empty. Docs:
 * docs/catalog/actions.csv. Tests: tests/e2e/test_curate_merge.py, test_upload_report_links.py.
 */
public final class PageTrackerAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(PageTrackerAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
                                 HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        ActionForward c = Check.admin(request, mapping); if (c != null) return c;

        return mapping.findForward("success");
    }
}