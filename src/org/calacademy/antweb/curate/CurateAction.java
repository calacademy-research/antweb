package org.calacademy.antweb.curate;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

public final class CurateAction extends Action {

    private static final Log s_log = LogFactory.getLog(CurateAction.class);
    
	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward c = Check.login(request, mapping); if (c != null) return c;
        //Login accessLogin = LoginMgr.getAccessLogin(request);

		HttpSession session = request.getSession();
		
        return mapping.findForward("curate");
	}
}
