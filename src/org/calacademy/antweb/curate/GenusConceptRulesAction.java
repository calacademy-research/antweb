package org.calacademy.antweb.curate;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.AntwebAction;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.upload.GenusConceptProposals;
import org.calacademy.antweb.upload.GenusConceptRuleDb;
import org.calacademy.antweb.util.AntwebProps;
import org.calacademy.antweb.util.Csrf;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.Html;
import org.calacademy.antweb.util.HttpUtil;
import org.calacademy.antweb.util.LogMgr;
import org.calacademy.antweb.util.LoginMgr;
import org.calacademy.antweb.util.ReportHelp;

/**
 * Old genus concepts (genusConceptRules.do; Brian Fisher, 2026-11-03). Administrators only.
 *
 *   GET  /genusConceptRules.do      the proposals from AntCat's data (GenusConceptProposals, GenusConceptRuleDb.loadPairs):
 *                                   PROPOSE first with Approve and Reject, then SPLIT, MIXED and no valid new genus
 *                                   (read only), then the approved and rejected rules with who and when.
 *   POST op=approve|reject, genus, bioregion, newGenus, note (CSRF token): writes genus_concept_rule. Approve only
 *                                   while the pair is still proposed with that new genus. The next nightly name update
 *                                   (third pass) and every upload apply approved rules. POST/redirect/GET; admin.log.
 */
public final class GenusConceptRulesAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(GenusConceptRulesAction.class);
    private static final String FLASH = "genusConceptRulesFlash";
    static final int EXAMPLES = 5;

    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        ActionForward denied = LoginMgr.mustBeAdmin(request, mapping); if (denied != null) return denied;
        Login admin = LoginMgr.getAccessLogin(request);
        String op = request.getParameter("op");
        boolean post = HttpUtil.isPost(request) && ("approve".equals(op) || "reject".equals(op));

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("GenusConceptRulesAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            GenusConceptRuleDb db = new GenusConceptRuleDb(connection);
            db.ensureSchema();
            List<GenusConceptProposals.Pair> listed = db.proposals();

            if (post) {
                String result = decide(db, listed, op, request.getParameter("genus"), request.getParameter("bioregion"),
                    request.getParameter("newGenus"), request.getParameter("note"), admin.getName());
                LogMgr.appendLog("admin.log", "genusConceptRules " + op + " by " + admin.getName() + " (id " + admin.getId() + "): " + result, true);
                request.getSession().setAttribute(FLASH, result);
                return new ActionForward("/genusConceptRules.do", true);
            }

            StringBuilder html = new StringBuilder();
            Object flash = request.getSession().getAttribute(FLASH);
            if (flash != null) {
                request.getSession().removeAttribute(FLASH);
                html.append("<p class=\"aw-warn\" id=\"genus-concept-flash\"><b>").append(Html.esc(flash.toString())).append("</b></p>");
            }
            html.append(HELP);
            html.append(proposalsHtml(listed, Csrf.field(request), AntwebProps.getDomainApp()));
            html.append(rulesHtml(db.all()));
            request.setAttribute("html", html.toString());
        } catch (SQLException e) {
            s_log.error("execute() op:" + op + " e:" + e);
            request.setAttribute("message", "The old genus concepts could not be read; nothing was changed. Please try again later.");
            return mapping.findForward("message");
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }
        return mapping.findForward("success");
    }

    /** Approve (only a pair still proposed with that new genus) or reject (a listed pair). Returns the sentence shown. */
    public static String decide(GenusConceptRuleDb db, List<GenusConceptProposals.Pair> listed, String op, String genus, String bioregion,
                         String newGenus, String note, String who) throws SQLException {
        GenusConceptProposals.Pair pair = null;
        for (GenusConceptProposals.Pair p : listed) if (p.genus.equals(genus) && p.bioregion.equals(bioregion)) pair = p;
        if (pair == null) return "Nothing changed: " + Taxon.getPrettyTaxonName(genus) + " in " + bioregion + " is not listed any more.";
        Timestamp now = new Timestamp(System.currentTimeMillis());
        if ("approve".equals(op)) {
            if (pair.kind != GenusConceptProposals.Kind.PROPOSE || newGenus == null || !newGenus.equals(pair.newGenus())) {
                return "Nothing changed: " + Taxon.getPrettyTaxonName(genus) + " in " + bioregion + " is not proposed as "
                    + Taxon.getPrettyTaxonName(newGenus) + " any more.";
            }
            db.save(genus, bioregion, newGenus, GenusConceptRuleDb.APPROVED, who, now, note);
            return "Approved: " + Taxon.getPrettyTaxonName(genus) + " in the " + bioregion + " region is " + Taxon.getPrettyTaxonName(newGenus)
                + ". The next nightly run moves its " + pair.specimens() + " morphospecies and indet specimens there.";
        }
        String target = pair.newGenus();
        db.save(genus, bioregion, target, GenusConceptRuleDb.REJECTED, who, now, note);
        return "Rejected: " + Taxon.getPrettyTaxonName(genus) + " in " + bioregion + " stays as it is and is not proposed again.";
    }

    static final String HELP = ReportHelp.box(
        "Old genera whose species AntCat has moved, by bioregion: for each genus with morphospecies or indet specimens in a "
            + "bioregion, where AntCat's species of that genus known from the bioregion now are. A species counts in a bioregion "
            + "when its current valid name is on the bioregion's list. \"Proposed\": every such species went to one genus, and "
            + "the old genus has no valid species left in the bioregion.",
        "A morphospecies or indet (Mesoponera kg01, Mesoponera (indet)) has no AntCat name to follow, so it stays in the old genus "
            + "after AntCat moves the species. An approved rule moves that bioregion's morphospecies and indets of the old genus to "
            + "the new genus, keeping the code part of the name and recording why.",
        "Approve or reject each proposal. Approved rules apply from the next nightly run (and in every upload). Splits, genera "
            + "with valid species left, and new genera AntWeb does not have stay manual.");

    public static String proposalsHtml(List<GenusConceptProposals.Pair> listed, String csrfField, String domainApp) {
        StringBuilder b = new StringBuilder("<div class=\"aw-card\" id=\"genus-concept-proposals\">");
        int proposed = 0;
        for (GenusConceptProposals.Pair p : listed) if (p.kind == GenusConceptProposals.Kind.PROPOSE) proposed++;
        b.append("<h2>Proposed (").append(proposed).append(")</h2>");
        if (proposed == 0) b.append("<p>Nothing to decide.</p>");
        else b.append(table(listed, GenusConceptProposals.Kind.PROPOSE, csrfField, domainApp));
        String[][] manual = {{"SPLIT", "Split: the species went to two or more genera (manual)"},
            {"MIXED", "Mixed: valid species of the old genus are left in the bioregion (manual)"},
            {"NO_VALID_TARGET", "The new genus is not a valid genus in AntWeb (manual)"}};
        for (String[] m : manual) {
            GenusConceptProposals.Kind kind = GenusConceptProposals.Kind.valueOf(m[0]);
            int n = 0;
            for (GenusConceptProposals.Pair p : listed) if (p.kind == kind) n++;
            if (n == 0) continue;
            b.append("<details class=\"genus-concept-").append(m[0].toLowerCase()).append("\"><summary><b>").append(Html.esc(m[1]))
             .append(" (").append(n).append(")</b></summary>").append(table(listed, kind, null, domainApp)).append("</details>");
        }
        return b.append("</div>").toString();
    }

    private static String table(List<GenusConceptProposals.Pair> listed, GenusConceptProposals.Kind kind, String csrfField, String domainApp) {
        StringBuilder b = new StringBuilder("<table><tr><th>Old genus</th><th>Bioregion</th><th>Evidence</th>"
            + "<th>Specimens (morphospecies, indets)</th><th>Examples</th>" + (csrfField != null ? "<th>Decision</th>" : "") + "</tr>");
        for (GenusConceptProposals.Pair p : listed) {
            if (p.kind != kind) continue;
            b.append("<tr class=\"genus-concept-row\"><td>").append(Taxon.makeDescriptionLink(p.genus, Taxon.getPrettyTaxonName(p.genus)))
             .append("</td><td>").append(Html.esc(p.bioregion)).append("</td><td>").append(Html.esc(GenusConceptProposals.evidence(p)))
             .append("</td><td>").append(p.specimens()).append(" (");
            int i = 0;
            for (Map.Entry<Integer, Integer> e : p.specimensByGroup.entrySet()) {
                if (i++ > 0) b.append(", ");
                b.append(Html.esc(NameUpdatesAction.groupName(e.getKey()))).append(" ").append(e.getValue());
            }
            b.append(")</td><td>").append(Html.esc(String.join("; ", GenusConceptProposals.examples(p, EXAMPLES)))).append("</td>");
            if (csrfField != null) {
                b.append("<td>").append(form(p, "approve", "Approve", csrfField, domainApp)).append(form(p, "reject", "Reject", csrfField, domainApp)).append("</td>");
            }
            b.append("</tr>");
        }
        return b.append("</table>").toString();
    }

    private static String form(GenusConceptProposals.Pair p, String op, String label, String csrfField, String domainApp) {
        return "<form method=\"post\" action=\"" + Html.escAttr(domainApp) + "/genusConceptRules.do\" style=\"display:inline\">" + csrfField
            + "<input type=\"hidden\" name=\"op\" value=\"" + op + "\">"
            + "<input type=\"hidden\" name=\"genus\" value=\"" + Html.escAttr(p.genus) + "\">"
            + "<input type=\"hidden\" name=\"bioregion\" value=\"" + Html.escAttr(p.bioregion) + "\">"
            + "<input type=\"hidden\" name=\"newGenus\" value=\"" + Html.escAttr(p.newGenus() == null ? "" : p.newGenus()) + "\">"
            + "<input type=\"submit\" value=\"" + label + "\"></form> ";
    }

    public static String rulesHtml(List<GenusConceptRuleDb.Row> rows) {
        StringBuilder b = new StringBuilder("<div class=\"aw-card\" id=\"genus-concept-rules\"><h2>Decided (").append(rows.size()).append(")</h2>");
        if (rows.isEmpty()) return b.append("<p>None yet.</p></div>").toString();
        b.append("<table><tr><th>Old genus</th><th>Bioregion</th><th>New genus</th><th>Decision</th><th>By</th><th>When</th><th>Note</th></tr>");
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (GenusConceptRuleDb.Row r : rows) {
            b.append("<tr class=\"genus-concept-rule\"><td>").append(Html.esc(Taxon.getPrettyTaxonName(r.oldGenus))).append("</td><td>")
             .append(Html.esc(r.bioregion)).append("</td><td>").append(Html.esc(r.newGenus == null || r.newGenus.isEmpty() ? "" : Taxon.getPrettyTaxonName(r.newGenus)))
             .append("</td><td>").append(Html.esc(r.status)).append("</td><td>").append(Html.esc(r.approvedBy == null ? "" : r.approvedBy))
             .append("</td><td>").append(r.approvedAt == null ? "" : f.format(r.approvedAt)).append("</td><td>")
             .append(Html.esc(r.note == null ? "" : r.note)).append("</td></tr>");
        }
        return b.append("</table></div>").toString();
    }
}
