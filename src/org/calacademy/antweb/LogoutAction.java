package org.calacademy.antweb;

import java.io.IOException;
import java.util.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class LogoutAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(LogoutAction.class);

    public ActionForward execute(
        ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        Locale locale = getLocale(request);
        
        String target = ((LoginForm) form).getTarget();
        if (!HttpUtil.isSafeRedirectTarget(target)) target = null;

        // End the session entirely (not just the login attribute). Pages create a fresh one if they need it.
        HttpSession session = request.getSession(false);
        if (session != null) {
          LoginMgr.removeAccessLogin(request);
          try {
            session.invalidate();
          } catch (IllegalStateException e) {
            // already invalidated
          }
        }

        if (target != null && !target.equals("") && !target.contains("login.do")) {
          //s_log.warn("Logout:" + target);
          return new ActionForward(target, true);  // target must be a physical page
        } else {
          return mapping.findForward("success");
        }
    }
}
