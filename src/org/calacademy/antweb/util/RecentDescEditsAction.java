package org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.DynaActionForm;

import org.calacademy.antweb.AntwebAction;
import org.calacademy.antweb.DescEdit;
import org.calacademy.antweb.home.DescEditDb;

/**
 * /recentDescEdits.do: the Recent Description Edits page (public; linked from the home page and the
 * Curate page). Until 2026-10-19 this path ran ListAction, so every list.do report also answered
 * here, some as empty pages; now only action=recentDescEdits (or no action) is served and any other
 * action answers 404. The other reports stay on /list.do.
 */
public final class RecentDescEditsAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(RecentDescEditsAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) {

        if (HttpUtil.isBotAttackDefence(request)) {
            request.setAttribute("message", "Invalid request.");
            return mapping.findForward("message");
        }
        DynaActionForm df = (DynaActionForm) form;
        String action = (String) df.get("action");
        if (!isServed(action)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            request.setAttribute("message", "action:" + Html.esc(action) + " not found.");
            return mapping.findForward("message");
        }

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("RecentDescEditsAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            request.getSession().setAttribute("taxon", null);
            ArrayList<DescEdit> descEdits = new DescEditDb(connection).getRecentDescEdits();
            request.setAttribute("message", "Antweb Recent Description Edits");
            request.setAttribute("descEdits", descEdits);
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return mapping.findForward("error");
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }
        return mapping.findForward("success");
    }

    /** No action, or action=recentDescEdits. */
    public static boolean isServed(String action) {
        return action == null || action.isEmpty() || "recentDescEdits".equals(action);
    }
}
