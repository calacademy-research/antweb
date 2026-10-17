package org.calacademy.antweb;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.util.*;

/**
 * editSession.do: "am I still logged in?" for the page-text editor (editSafety.js).
 * Answers JSON {"loggedIn":true,"canEdit":true,"loginId":12,"csrf":"..."} or {"loggedIn":false,...}.
 * Called every few minutes while a curator has unsaved text (which also keeps the session
 * alive) and just before Save, so the form carries this session's current CSRF token.
 * Read-only; never cached.
 */
public final class EditSessionAction extends AntwebAction {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) throws IOException {

        Login accessLogin = LoginMgr.getAccessLogin(request);
        boolean loggedIn = accessLogin != null;
        boolean canEdit = DescEditSafety.canEdit(accessLogin);

        StringBuilder json = new StringBuilder();
        json.append("{\"loggedIn\":").append(loggedIn)
            .append(",\"canEdit\":").append(canEdit);
        if (loggedIn) {
            json.append(",\"loginId\":").append(accessLogin.getId())
                .append(",\"csrf\":\"").append(Csrf.token(request)).append('"');
        }
        json.append('}');

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(json.toString());
        return null;
    }
}
