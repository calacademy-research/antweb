package org.calacademy.antweb;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.util.HttpUtil;
import org.calacademy.antweb.util.PasswordResetMgr;

/**
 * /forgotPassword.do: asks for a username or email address and, if a login
 * matches and has an email address, emails it a reset link
 * (PasswordResetMgr). The answer is the same whether or not an account
 * exists. Requests are limited per IP address.
 *
 * Request attributes for forgot_password-body.jsp:
 *   resetState  "form" or "sent"
 *   resetError  a message to show with the form, or absent
 */
public final class ForgotPasswordAction extends AntwebAction {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        String state = "form";
        if (HttpUtil.isPost(request)) {
            String identifier = request.getParameter("identifier");
            if (identifier != null) identifier = identifier.trim();
            if (identifier == null || identifier.isEmpty() || identifier.length() > 255) {
                request.setAttribute("resetError", "Please enter your username or email address.");
            } else {
                String ip = HttpUtil.getClientIpAddress(request);
                if (!PasswordResetMgr.allowIp(ip)) {
                    request.setAttribute("resetError", "Too many requests from your network. Please try again in an hour.");
                } else {
                    PasswordResetMgr.requestResetAsync(identifier, ip);
                    state = "sent";
                }
            }
        }
        request.setAttribute("resetState", state);
        response.setHeader("Cache-Control", "no-store");
        return mapping.findForward("success");
    }
}
