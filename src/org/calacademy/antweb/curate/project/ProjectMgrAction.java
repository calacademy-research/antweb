package org.calacademy.antweb.curate.project;

import java.io.IOException;
import java.util.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public final class ProjectMgrAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(ProjectMgrAction.class);
    
	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

		// Extract attributes we will need
		Locale locale = getLocale(request);
		HttpSession session = request.getSession();
        
        // Creating and deleting projects: administrators only (the page itself checks too).
        ActionForward c = Check.admin(request, mapping); if (c != null) return c;    
        Login accessLogin = LoginMgr.getAccessLogin(request);
			
        if (HttpUtil.getRequestURI(request).contains("projectMgr")) return mapping.findForward("projectMgr");        
        return mapping.findForward("curate");
	}
}
