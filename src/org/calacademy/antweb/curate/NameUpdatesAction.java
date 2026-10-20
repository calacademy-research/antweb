package org.calacademy.antweb.curate;

import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.AntwebAction;
import org.calacademy.antweb.Group;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.upload.NameUpdateReason;
import org.calacademy.antweb.upload.SpecimenNameUpdate;
import org.calacademy.antweb.upload.SpecimenNameUpdateDb;
import org.calacademy.antweb.util.*;

/**
 * The "Name updates" page (nameUpdates.do): the specimens the nightly name update (SpecimenNameUpdate)
 * moved to the current valid name, or would move (report mode, Preview). Read-only.
 *
 * Curators see their own group only (a groupId parameter is ignored for them); administrators see all
 * groups, can pick one, and get the "Preview now (report only)" button, which runs the step at once
 * in report mode (utilData.do?action=specimenNameUpdatePreview, POST). Newest run first; filters by
 * group and by date (from, to). Each line is "old name -> new name" with the same links as the
 * upload report's "Recognized invalid species" section, the reason with AntCat's reference (linked to
 * the AntCat page of the taxon whose history gave it; NameUpdateReason), the number of specimens and
 * their codes. Rows recorded before the reason existed show the AntCat status of the old name.
 */
public final class NameUpdatesAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(NameUpdatesAction.class);

    public static final int MAX_RUNS = 30;

    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        ActionForward a = Check.initLogin(request, mapping); if (a != null) return a;
        ActionForward c = Check.curator(request, mapping); if (c != null) return c;

        Login accessLogin = LoginMgr.getAccessLogin(request);
        boolean isAdmin = LoginMgr.isAdmin(accessLogin);

        int groupId = 0;
        if (isAdmin) {
            try {
                String g = request.getParameter("groupId");
                if (g != null && !g.isBlank()) groupId = Integer.parseInt(g.trim());
            } catch (NumberFormatException e) {
                groupId = 0;
            }
        } else {
            Group group = accessLogin.getGroup();
            groupId = group == null ? -1 : group.getId();   // -1: a curator without a group sees nothing
        }
        String from = SpecimenNameUpdateDb.day(request.getParameter("from"));
        String to = SpecimenNameUpdateDb.day(request.getParameter("to"));

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("NameUpdatesAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            SpecimenNameUpdateDb db = new SpecimenNameUpdateDb(connection);
            List<SpecimenNameUpdateDb.Run> runs = groupId < 0 ? new ArrayList<>() : db.getRuns(groupId, from, to, MAX_RUNS);
            List<SpecimenNameUpdateDb.Line> lines = groupId < 0 ? new ArrayList<>() : db.getLines(groupId, runs);
            List<Integer> groupIds = isAdmin ? db.getGroupIds() : new ArrayList<>();

            StringBuilder html = new StringBuilder();
            html.append(HELP);
            if (isAdmin) html.append(adminBox(request, SpecimenNameUpdate.readSettings()));
            html.append(filterForm(isAdmin, groupIds, Math.max(groupId, 0), from, to));
            html.append(linesHtml(runs, lines, AntwebProps.getDomainApp()));
            request.setAttribute("html", html.toString());
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            request.setAttribute("message", "The name updates could not be read. Please try again later.");
            return mapping.findForward("message");
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }
        return mapping.findForward("success");
    }

    public static final String HELP = ReportHelp.box(
        "Specimens that AntWeb moved to another name because AntCat changed a name after the specimens were uploaded: "
            + "for example a species that AntCat has since made a synonym, or moved to another genus. "
            + "Each night, after the AntCat names are loaded (05:00 Pacific), AntWeb applies the rule every specimen upload uses: "
            + "a specimen uploaded under a name that is no longer valid is filed under the current valid name. "
            + "The name in the curator's file is kept and shown on the specimen page as \"Uploaded As\". "
            + "A line reads: old name -> new name, why (from AntCat's taxonomic history, for example \"synonym: junior synonym of "
            + "Strumigenys pilinasis (Booher 2019)\", with the publication linked to the AntCat page it comes from), and the specimens. "
            + "\"Valid again\" means AntCat restored the name in the curator's file, so the specimens go back to it. "
            + "\"Would change (report only)\" lists what the step would do; nothing has been changed for those.",
        "Without this, a specimen whose name AntCat changed after the upload drops out of both species pages (the old and the "
            + "valid one) and its counts and maps stay under the old name until the group uploads its data again.",
        "Usually nothing: the counts, lists and maps follow by the end of the nightly run (about 8 AM). If a move looks wrong, "
            + "check the name in AntCat first. If the identification itself is out of date, correct it in TaxonWorks (CAS) or in "
            + "your upload file; the next upload or nightly run uses it. Morphospecies, indets and homonyms are never moved.");

    static String adminBox(HttpServletRequest request, SpecimenNameUpdate.Settings settings) {
        String domainApp = AntwebProps.getDomainApp();
        return "<div style=\"margin:10px 0;max-width:900px\">"
            + "<p><b>Setting on this server:</b> " + Html.esc(settings.mode)
            + (settings.note != null ? " (" + Html.esc(settings.note) + ")" : "")
            + ", maxChanges " + settings.maxChanges
            + ". File etc/name-update.properties: mode=off, report or apply. In apply mode the nightly run changes nothing "
            + "if more than maxChanges specimens would move; they are then listed here as \"would change\".</p>"
            + "<form method=\"post\" action=\"" + domainApp + "/utilData.do\" style=\"margin:4px 0\">" + Csrf.field(request)
            + "<input type=\"hidden\" name=\"action\" value=\"specimenNameUpdatePreview\">"
            + "<input type=\"submit\" value=\"Preview now (report only)\"> "
            + "Runs the name update now in report mode, whatever the setting: lists what would change, changes no specimen, "
            + "and replaces the previous preview. Takes about a minute; then come back to this page.</form>"
            + "</div>";
    }

    static String filterForm(boolean isAdmin, List<Integer> groupIds, int groupId, String from, String to) {
        StringBuilder b = new StringBuilder("<form method=\"get\" action=\"" + AntwebProps.getDomainApp() + "/nameUpdates.do\" style=\"margin:10px 0\">");
        if (isAdmin) {
            b.append("Group: <select name=\"groupId\" aria-label=\"Group\"><option value=\"0\">All groups</option>");
            for (Integer id : groupIds) {
                b.append("<option value=\"").append(id).append("\"").append(id == groupId ? " selected" : "").append(">")
                 .append(Html.esc(groupName(id))).append("</option>");
            }
            b.append("</select> ");
        }
        b.append("From: <input type=\"date\" name=\"from\" value=\"").append(from == null ? "" : from).append("\" aria-label=\"From\"> ");
        b.append("To: <input type=\"date\" name=\"to\" value=\"").append(to == null ? "" : to).append("\" aria-label=\"To\"> ");
        b.append("<input type=\"submit\" value=\"Show\"></form>");
        return b.toString();
    }

    static String groupName(int groupId) {
        try {
            Group g = GroupMgr.getGroup(groupId);
            if (g != null && g.getName() != null) return g.getName();
        } catch (RuntimeException e) {
            // not loaded
        }
        return "Group " + groupId;
    }

    /** One upload-report style line: old -> new with the links of Taxon.makeDescriptionLink. */
    public static String nameLine(String oldName, String newName) {
        return Taxon.makeDescriptionLink(oldName) + " -> " + Taxon.makeDescriptionLink(newName);
    }

    public static String linesHtml(List<SpecimenNameUpdateDb.Run> runs, List<SpecimenNameUpdateDb.Line> lines, String domainApp) {
        if (runs.isEmpty()) {
            return "<p><b>No name updates</b> for this selection.</p>";
        }
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        StringBuilder b = new StringBuilder();
        for (SpecimenNameUpdateDb.Run run : runs) {
            boolean changed = SpecimenNameUpdate.CHANGED.equals(run.mode);
            b.append("<h3>").append(f.format(run.runDate)).append(": ")
             .append(changed ? "Recognized invalid species. Specimens moved to the current valid name from AntCat.org"
                             : "Would change (report only). Nothing was changed")
             .append(" (").append(String.format("%,d", run.specimens)).append(run.specimens == 1 ? " specimen" : " specimens").append(")</h3>");
            int lastGroup = Integer.MIN_VALUE;
            boolean open = false;
            for (SpecimenNameUpdateDb.Line line : lines) {
                if (!line.runDate.equals(run.runDate) || !line.mode.equals(run.mode)) continue;
                if (line.groupId != lastGroup) {
                    if (open) b.append("</ul>");
                    b.append("<h4>").append(Html.esc(groupName(line.groupId))).append("</h4><ul>");
                    open = true;
                    lastGroup = line.groupId;
                }
                b.append("<li class=\"name-update\">").append(nameLine(line.oldName, line.newName));
                if (line.reason != null) {
                    b.append(": <span class=\"name-update-reason\">").append(NameUpdateReason.toHtml(line.reason, line.antcatId,
                        Taxon.getPrettyTaxonName(line.uploadedName), Taxon.getPrettyTaxonName(line.oldName), Taxon.getPrettyTaxonName(line.newName)))
                     .append("</span>");
                } else if (line.antcatStatus != null) {
                    b.append(" (AntCat: ").append(Html.esc(line.antcatStatus)).append(")");
                }
                b.append(" <details style=\"display:inline\"><summary>").append(line.count).append(line.count == 1 ? " specimen" : " specimens").append("</summary>");
                int i = 0;
                for (String code : line.codes) {
                    if (i++ > 0) b.append(", ");
                    b.append("<a href=\"").append(domainApp).append("/specimen.do?name=").append(Html.url(code)).append("\">").append(Html.esc(code)).append("</a>");
                }
                if (line.count > line.codes.size()) b.append(" ... (").append(line.count - line.codes.size()).append(" more)");
                b.append("</details></li>");
            }
            if (open) b.append("</ul>");
        }
        return b.toString();
    }
}
