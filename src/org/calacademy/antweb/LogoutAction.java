package org.calacademy.antweb;

import java.io.IOException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class LogoutAction extends Action {

    private static final Log s_log = LogFactory.getLog(LogoutAction.class);

    public ActionForward execute(
        ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        Locale locale = getLocale(request);
        HttpSession session = request.getSession();
        
        
        // This block of code, and only this block, is in service of forgotPassword.do
        String requestURI = request.getRequestURI();
        if (requestURI.contains("forgotPassword")) {
          //s_log.warn("Yes, forgotTarget");
          return mapping.findForward("success");
        }
        
        LoginMgr.removeAccessLogin(request);

        String target = ((LoginForm) form).getTarget();

        if (target != null && !target.equals("") && !target.contains("login.do")) {
          //s_log.warn("Logout:" + target);
          return new ActionForward(target, true);  // target must be a physical page
        } else {
          return mapping.findForward("success");
        }
    }
}
