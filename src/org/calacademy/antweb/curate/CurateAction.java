package org.calacademy.antweb.curate;

import org.calacademy.antweb.AntwebAction;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

/**
 * /curate.do: the Curate page (curate/curate.jsp), the curators' start page with the upload forms, read-only
 * tools and, for admins, the manager tools. Curators only (Check.curator); reads and writes nothing itself.
 * Docs: docs/guides/curate-page.md. Tests: tests/e2e/test_curate_page_clarity.py, test_curate_page_layout.py,
 * test_curator.py.
 */
public final class CurateAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(CurateAction.class);
    
	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward c = Check.curator(request, mapping); if (c != null) return c;
        //Login accessLogin = LoginMgr.getAccessLogin(request);

		HttpSession session = request.getSession();
		
        return mapping.findForward("curate");
	}
}
