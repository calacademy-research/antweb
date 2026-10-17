package org.calacademy.antweb;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.util.*;

/**
 * fieldHistory.do?field=<field>&key=<page key>&page=<page address>: the versions of one page-text
 * field, for curators and administrators (page editor plan, phase 2). See DescHistory.
 *
 *   field  a page-text field (DescriptionAction.EDIT_FIELDS), e.g. comments
 *   key    taxon:<taxon_name>, specimen:<code> or object:<object_key> (DescEditSafety.pageKey)
 *   page   the page the field is on, e.g. description.do?genus=polyrhachis&species=amana&rank=species
 *          (only the pages and parameters DescHistory.safePage allows). "Restore this version" posts
 *          back to fieldHistory.do, which hands the request to that page's normal save; the save checks
 *          that the version belongs to that page and field. Without a valid page there are no Restore buttons.
 *   show   (optional) a hist_id: also compare that version's HTML source with the current text.
 *
 * A GET only reads. Never cached. Anyone who is not a curator or administrator gets a message.
 */
public final class FieldHistoryAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(FieldHistoryAction.class);

    public static final String SHOW_ATTR = "descHistoryShow";

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Robots-Tag", "noindex");

        // "Restore this version" posts here. The restore itself is the page's normal save, so the
        // request is handed (server side) to the page named by "page", but only if that is one of the
        // pages that host page text, with only the parameters that name what it shows
        // (DescHistory.safePage). The form token was checked on this POST by SessionRequestFilter;
        // the page's save checks the login, the field rules and that the version belongs to it.
        if (HttpUtil.isPost(request)) return restore(mapping, request);

        Login accessLogin = LoginMgr.getAccessLogin(request);
        if (accessLogin == null) return mapping.findForward("notLoggedIn");
        if (!DescEditSafety.canEdit(accessLogin)) {
            request.setAttribute("message", "Only curators and administrators can see the history of page text.");
            return mapping.findForward("message");
        }

        String field = request.getParameter("field");
        DescHistory.Key key = DescHistory.Key.parse(request.getParameter("key"));
        if (field == null || !DescriptionAction.EDIT_FIELDS.contains(field) || key == null) {
            request.setAttribute("message", "Unknown page or field. Open the History link next to a field's [Edit] link.");
            return mapping.findForward("message");
        }
        if (!DescHistory.canView(accessLogin, key, field)) {
            request.setAttribute("message", "Your account cannot edit " + Html.esc(DescEditSafety.prettyField(field)) + " on this page, so its history is not shown.");
            return mapping.findForward("message");
        }
        String page = DescHistory.safePage(request.getParameter("page"));

        int show = 0;
        try {
            String s = request.getParameter("show");
            if (s != null) show = Integer.parseInt(s.trim());
        } catch (NumberFormatException e) { /* ignore */ }

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("FieldHistoryAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName, HttpUtil.getTarget(request));
            DescHistory history = DescHistory.load(connection, key, field, page);
            request.setAttribute(DescHistory.ATTR, history);
            if (show > 0) request.setAttribute(SHOW_ATTR, show);
        } catch (SQLException e) {
            s_log.error("execute() key:" + key + " field:" + field + " e:" + e);
            request.setAttribute("message", "The history could not be read from the database just now (AntWeb may be busy or restarting). Please try again in a minute.");
            return mapping.findForward("message");
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }
        return mapping.findForward("success");
    }

    private ActionForward restore(ActionMapping mapping, HttpServletRequest request) {
        String page = DescHistory.safePage(request.getParameter("page"));
        String editField = request.getParameter("editField");
        String restoreFrom = request.getParameter("restoreFrom");
        boolean ok = page != null
            && "true".equals(request.getParameter("isSaveEditField"))
            && editField != null && DescriptionAction.EDIT_FIELDS.contains(editField)
            && restoreFrom != null && restoreFrom.matches("[1-9][0-9]{0,9}");
        if (!ok) {
            s_log.warn("restore() refused page:" + request.getParameter("page") + " field:" + editField);
            request.setAttribute("message", "Nothing was changed: this is not a restore of page text. Open the History link next to the field and press Restore there.");
            return mapping.findForward("message");
        }
        return new ActionForward("/" + page);
    }
}
