package org.calacademy.antweb;

import java.io.IOException;
import java.sql.Connection;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.util.ApiKeyMgr;
import org.calacademy.antweb.util.ApiKeys;
import org.calacademy.antweb.util.Csrf;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.HttpUtil;
import org.calacademy.antweb.util.Turnstile;

/**
 * /apiKey.do: the public form to register for a free API key (step 6 PR 4; Brian Fisher, 2026-11-08). No AntWeb
 * account is needed.
 *
 *   GET   the form: name, email, project description (required), institution or affiliation (optional), "I agree
 *         to the terms" (links to apiV3.1.do#terms), the Cloudflare Turnstile check. Without apiKeys.open=true in
 *         etc/api-keys.properties on the server the page says "Registration opens soon" and shows no form.
 *   POST  checks, in order: registration open, the form's CSRF token (Csrf.check; the form's session), the fields
 *         (ApiKeys.Registration.problem), Turnstile, at most 5 registrations an hour from the IP address; then
 *         ApiKeyMgr.register (3 a day per email address, 100 a day in all, saves 'pending', emails the link and
 *         the copy to Brian Fisher). The answer is "check your email".
 *
 * Request attributes for apiKey-body.jsp: apiKeyState ("closed", "form" or "sent"), apiKeyError (a message shown
 * with the form, or absent), apiKeyForm (the ApiKeys.Registration typed, to fill the form again).
 * Tests: test/.../security/ApiKeysTest.java, tests/e2e/test_api_keys.py.
 */
public final class ApiKeyAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(ApiKeyAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        response.setHeader("Cache-Control", "no-store");
        if (!ApiKeys.isOpen()) {
            request.setAttribute("apiKeyState", "closed");
            return mapping.findForward("success");
        }

        String state = "form";
        if (HttpUtil.isPost(request)) {
            ApiKeys.Registration r = new ApiKeys.Registration(request.getParameter("name"), request.getParameter("email"),
                request.getParameter("institution"), request.getParameter("project"), "yes".equals(request.getParameter("agree")));
            request.setAttribute("apiKeyForm", r);
            String error = null;
            if (Csrf.check(request) != null) {
                error = "This form was open too long, or your browser does not keep cookies for antweb.org. Please send it again.";
            }
            if (error == null) error = r.problem();
            if (error == null) {
                Turnstile.Result bot = Turnstile.check(request);
                if (bot == Turnstile.Result.UNAVAILABLE) error = "The human check could not be verified just now. Please try again in a minute.";
                else if (bot != Turnstile.Result.PASSED) error = "Please complete the human check (the box above the button).";
            }
            if (error == null && !ApiKeys.allowIp(HttpUtil.getClientIpAddress(request))) {
                error = "Too many registrations from your network. Please try again in an hour.";
            }
            if (error == null) {
                String outcome = register(request, r);
                if (ApiKeyMgr.SENT.equals(outcome)) {
                    state = "sent";
                } else if (ApiKeyMgr.EMAIL_LIMIT.equals(outcome)) {
                    error = "This email address has registered " + ApiKeys.MAX_PER_EMAIL_PER_DAY + " times today. Please use one of the links we sent, or try again tomorrow.";
                } else if (ApiKeyMgr.DAILY_LIMIT.equals(outcome)) {
                    error = "We have had many registrations today. Please try again tomorrow, or write to antweb@antweb.org.";
                } else if (ApiKeyMgr.MAIL_FAILED.equals(outcome)) {
                    error = "Your registration was saved, but we could not send the email just now. Please try again later, or write to antweb@antweb.org.";
                } else {
                    error = "Sorry, there is a problem with our database. Please try again later.";
                }
            }
            if (error != null) request.setAttribute("apiKeyError", error);
        }
        request.setAttribute("apiKeyState", state);
        return mapping.findForward("success");
    }

    private String register(HttpServletRequest request, ApiKeys.Registration r) {
        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("ApiKeyAction.register()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            return ApiKeyMgr.register(connection, r);
        } catch (Exception e) {
            s_log.error("register() e:" + e);
            return ApiKeyMgr.ERROR;
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }
    }
}
