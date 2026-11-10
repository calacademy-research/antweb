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
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.HttpUtil;

/**
 * /apiKeyConfirm.do?token=... : the page the API key confirmation email links to (step 6 PR 4).
 *
 * GET shows a button "Show my API key" if the link is valid (pending, not expired) and changes nothing: email
 * security scanners open links before the person does, and the key can be shown only once. POST (the button,
 * carrying the token) makes the key, activates it and shows it once, with how to send it (header X-API-Key) and
 * links to the terms and documentation (ApiKeyMgr.confirm). An unknown, used, cancelled or expired link says so.
 * The page is never cached and sends no Referer (the address holds the token).
 *
 * Request attributes for apiKeyConfirm-body.jsp: apiKeyConfirm (ApiKeyMgr.ConfirmPage), apiKeyToken (only for
 * the button's hidden field). Tests: home/ApiKeyDbTest.java, tests/e2e/test_api_keys.py.
 */
public final class ApiKeyConfirmAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(ApiKeyConfirmAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");

        String token = request.getParameter("token");
        ApiKeyMgr.ConfirmPage page = new ApiKeyMgr.ConfirmPage();
        if (ApiKeys.isWellFormedToken(token)) {
            Connection connection = null;
            String dbMethodName = DBUtil.getDbMethodName("ApiKeyConfirmAction.execute()");
            try {
                DataSource dataSource = getDataSource(request, "conPool");
                connection = DBUtil.getConnection(dataSource, dbMethodName);
                page = ApiKeyMgr.confirm(connection, token, HttpUtil.isPost(request));
            } catch (Exception e) {
                s_log.error("execute() e:" + e);
                page.state = "error";
            } finally {
                DBUtil.close(connection, this, dbMethodName);
            }
        }
        if ("confirm".equals(page.state)) request.setAttribute("apiKeyToken", token);
        request.setAttribute("apiKeyConfirm", page);
        return mapping.findForward("success");
    }
}
