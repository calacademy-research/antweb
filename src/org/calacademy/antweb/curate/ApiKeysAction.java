package org.calacademy.antweb.curate;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import org.calacademy.antweb.Login;
import org.calacademy.antweb.home.ApiKeyDb;
import org.calacademy.antweb.util.ApiKeyMgr;
import org.calacademy.antweb.util.ApiKeys;
import org.calacademy.antweb.util.AntwebProps;
import org.calacademy.antweb.util.Csrf;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.Html;
import org.calacademy.antweb.util.HttpUtil;
import org.calacademy.antweb.util.LoginMgr;

/**
 * API keys admin page (apiKeys.do; step 6 PR 5, Brian Fisher 2026-11-08). Administrators only.
 *
 *   GET  /apiKeys.do                    every registration, newest first: name, email, institution, project, level,
 *                                       status, key prefix, created, confirmed, last used, requests today / 7 days /
 *                                       30 days, records and refusals (HTTP 429) in 30 days (api_usage_daily), and
 *                                       a first row for all requests without a key (key_id 0). Filters status=,
 *                                       level=, q= (name, email or institution).
 *   GET  /apiKeys.do?format=csv         the same list (same filters) as a CSV file.
 *   GET  /apiKeys.do?id=N               one key: its details and the forms to change it.
 *   POST op=revoke|reactivate|level|limits|note, id, confirm=yes (CSRF token): changes the key; without the
 *                                       confirmation box nothing changes. POST/redirect/GET; admin.log.
 *
 * Never shows a key (only its first 7 characters) or a confirmation token. Tests: tests/e2e/test_api_keys.py,
 * test/.../security/ApiKeysTest.java (CSV, limits), test/.../home/ApiKeyDbTest.java (list, usage, changes).
 */
public final class ApiKeysAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(ApiKeysAction.class);
    private static final String FLASH = "apiKeysFlash";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    static final int SHOWN = 500;
    static final java.util.Set<String> OPS = java.util.Set.of("revoke", "reactivate", "level", "limits", "note");

    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        ActionForward denied = LoginMgr.mustBeAdmin(request, mapping); if (denied != null) return denied;
        Login admin = LoginMgr.getAccessLogin(request);
        response.setHeader("Cache-Control", "no-store");

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("ApiKeysAction.execute()");
        StringBuilder html = new StringBuilder();
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            ApiKeyDb db = new ApiKeyDb(connection);
            db.ensureSchema();  // normally done at startup

            if (HttpUtil.isPost(request)) {
                int id = parseId(request.getParameter("id"));
                String result = change(db, id, request);
                ApiKeyMgr.logEvent("admin page by " + admin.getName() + " (id " + admin.getId() + "): key id:" + id
                    + " op:" + (request.getParameter("op") != null && OPS.contains(request.getParameter("op")) ? request.getParameter("op") : "unknown") + ": " + result);
                request.getSession().setAttribute(FLASH, result);
                return new ActionForward("/apiKeys.do" + (id > 0 ? "?id=" + id : ""), true);
            }

            ApiKeyDb.Filter filter = new ApiKeyDb.Filter();
            filter.status = ApiKeys.isStatus(request.getParameter("status")) ? request.getParameter("status") : null;
            filter.level = ApiKeys.isLevel(request.getParameter("level")) ? request.getParameter("level") : null;
            filter.search = ApiKeys.clean(request.getParameter("q"), false);

            if ("csv".equals(request.getParameter("format"))) {
                writeCsv(response, db.list(filter), db.noKeyUsage());
                return null;
            }

            Object flash = request.getSession().getAttribute(FLASH);
            if (flash != null) {
                request.getSession().removeAttribute(FLASH);
                html.append("<p class=\"aw-warn\" id=\"api-keys-flash\"><b>").append(Html.esc(flash.toString())).append("</b></p>");
            }
            int id = parseId(request.getParameter("id"));
            if (id > 0) {
                ApiKeyDb.Row row = db.get(id);
                if (row == null) html.append("<p class=\"aw-msg\">No API key registration number ").append(id).append(".</p>");
                else html.append(keyPage(row, request));
            } else {
                html.append(HELP);
                html.append(filterForm(filter));
                html.append(listTable(db.list(filter), db.noKeyUsage(), filter));
            }
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            html.append("<p class=\"aw-msg\">Database error; nothing was changed: ").append(Html.esc(e.getMessage())).append("</p>");
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }
        request.setAttribute("html", html.toString());
        return mapping.findForward("success");
    }

    static int parseId(String s) {
        try {
            return s == null ? 0 : Math.max(0, Integer.parseInt(s.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** One change from the key's page. Returns the sentence shown afterwards. */
    static String change(ApiKeyDb db, int id, HttpServletRequest request) throws SQLException {
        String op = request.getParameter("op");
        if (id <= 0 || db.get(id) == null) return "Nothing changed: no such key.";
        if (!"yes".equals(request.getParameter("confirm"))) return "Nothing changed: tick the box to confirm.";
        try {
            switch (op == null ? "" : op) {
                case "revoke":
                    return db.revoke(id) ? "Key " + id + " revoked: it no longer works." : "Nothing changed: the key was already revoked.";
                case "reactivate":
                    return db.reactivate(id) ? "Key " + id + " active again." : "Nothing changed: only a revoked key that had been confirmed can be reactivated.";
                case "level": {
                    String level = request.getParameter("level");
                    if (!ApiKeys.isLevel(level)) return "Nothing changed: choose a level.";
                    db.setLevel(id, level);
                    return "Key " + id + ": level set to " + level + ".";
                }
                case "limits":
                    db.setLimits(id, ApiKeys.parseLimit("Requests a minute", request.getParameter("perMinute")),
                        ApiKeys.parseLimit("Requests a day", request.getParameter("perDay")),
                        ApiKeys.parseLimit("Records an answer", request.getParameter("maxRecords")),
                        ApiKeys.parseLimit("Records a day", request.getParameter("recordsPerDay")));
                    return "Key " + id + ": limits saved (a blank limit uses the level's default).";
                case "note": {
                    String note = ApiKeys.clean(request.getParameter("note"), true);
                    if (note.length() > ApiKeys.MAX_NOTE) return "Nothing changed: the note is too long (at most " + ApiKeys.MAX_NOTE + " characters).";
                    db.setNote(id, note);
                    return "Key " + id + ": note saved.";
                }
                default:
                    return "Nothing changed: unknown change.";
            }
        } catch (IllegalArgumentException e) {
            return "Nothing changed: " + e.getMessage();
        }
    }

    // ---------------------------------------------------------------- page parts

    public static final String HELP = "<div class=\"aw-intro\" id=\"api-keys-help\">"
        + "<p><b>What it shows:</b> every registration for a free AntWeb API key, newest first, and how much each key was used."
        + " Keys are issued automatically: a registration is <i>pending</i> until the person opens the link we email them,"
        + " then <i>active</i>. You get a copy of every registration by email. Click a name to revoke or reactivate the key,"
        + " change its level or limits, or add a note.</p>"
        + "<p><b>Columns:</b> Key is the first 7 characters of the key (the key itself is never stored). Created is when the form"
        + " was sent, Confirmed when the email link was opened, Last used the last request with the key. Today, 7 days and 30 days"
        + " count requests; Records is the number of records the API sent in 30 days; Refused counts requests turned away"
        + " (HTTP 429) for going over a limit in 30 days. The first row adds up all requests made without a key.</p>"
        + "<p><b>Default limits:</b> without a key, " + ApiKeys.NO_KEY_PER_MINUTE + " requests a minute, " + ApiKeys.NO_KEY_PER_DAY
        + " a day and " + ApiKeys.NO_KEY_MAX_RECORDS + " records an answer (per IP address). Registered key: "
        + ApiKeys.REGISTERED_PER_MINUTE + " requests a minute, " + String.format("%,d", ApiKeys.REGISTERED_PER_DAY) + " a day, "
        + ApiKeys.REGISTERED_MAX_RECORDS + " records an answer and " + String.format("%,d", ApiKeys.REGISTERED_RECORDS_PER_DAY)
        + " records a day. Collaborator and admin keys use the limits you set on the key; a blank limit uses the registered default.</p>"
        + "</div>";

    static String filterForm(ApiKeyDb.Filter f) {
        String app = AntwebProps.getDomainApp();
        StringBuilder sb = new StringBuilder("<div class=\"aw-card\" id=\"api-keys-filter\"><form method=\"get\" action=\"")
            .append(Html.escAttr(app)).append("/apiKeys.do\"><div class=\"aw-row\">");
        sb.append("<label>Status <select name=\"status\"><option value=\"\">all</option>");
        for (String s : ApiKeys.STATUSES) sb.append(option(s, s.equals(f.status)));
        sb.append("</select></label> <label>Level <select name=\"level\"><option value=\"\">all</option>");
        for (String s : ApiKeys.LEVELS) sb.append(option(s, s.equals(f.level)));
        sb.append("</select></label> <label>Name, email or institution <input type=\"text\" name=\"q\" maxlength=\"100\" value=\"")
            .append(Html.escAttr(f.search == null ? "" : f.search)).append("\"></label>")
            .append(" <input class=\"aw-btn\" type=\"submit\" value=\"Show\"></div></form>");
        sb.append("<p class=\"aw-note\"><a id=\"api-keys-csv\" href=\"").append(Html.escAttr(app + "/apiKeys.do?format=csv" + query(f)))
            .append("\">Download this list as CSV</a> (opens in a spreadsheet). Registration is ")
            .append(ApiKeys.isOpen() ? "<b>open</b>" : "<b>not open yet</b> (apiKey.do says \"Registration opens soon\"; apiKeys.open=true in etc/api-keys.properties on the server opens it)")
            .append(".</p></div>");
        return sb.toString();
    }

    private static String option(String value, boolean selected) {
        return "<option value=\"" + Html.escAttr(value) + "\"" + (selected ? " selected" : "") + ">" + Html.esc(value) + "</option>";
    }

    private static String query(ApiKeyDb.Filter f) {
        StringBuilder q = new StringBuilder();
        if (f.status != null) q.append("&status=").append(f.status);
        if (f.level != null) q.append("&level=").append(f.level);
        if (f.search != null && !f.search.isEmpty()) q.append("&q=").append(java.net.URLEncoder.encode(f.search, java.nio.charset.StandardCharsets.UTF_8));
        return q.toString();
    }

    static String listTable(List<ApiKeyDb.Row> rows, ApiKeyDb.Usage noKey, ApiKeyDb.Filter f) {
        String app = AntwebProps.getDomainApp();
        StringBuilder sb = new StringBuilder("<div class=\"aw-card\" id=\"api-keys-list\"><h2>Registrations (")
            .append(rows.size()).append(")</h2>");
        sb.append("<table id=\"api-keys-table\"><tr><th>Name</th><th>Email</th><th>Institution</th><th>Project</th><th>Level</th>"
            + "<th>Status</th><th>Key</th><th>Created</th><th>Confirmed</th><th>Last used</th><th>Today</th><th>7 days</th>"
            + "<th>30 days</th><th>Records, 30 days</th><th>Refused, 30 days</th></tr>");
        sb.append("<tr id=\"api-keys-no-key\"><td colspan=\"10\"><i>All requests without a key</i></td>").append(usageCells(noKey)).append("</tr>");
        for (int i = 0; i < rows.size() && i < SHOWN; i++) {
            ApiKeyDb.Row r = rows.get(i);
            sb.append("<tr><td><a href=\"").append(Html.escAttr(app + "/apiKeys.do?id=" + r.id)).append("\">").append(Html.esc(r.name)).append("</a></td>")
              .append("<td>").append(Html.esc(r.email)).append("</td>")
              .append("<td>").append(Html.esc(r.institution == null ? "" : r.institution)).append("</td>")
              .append("<td>").append(Html.esc(shorten(r.project, 120))).append("</td>")
              .append("<td>").append(Html.esc(r.level)).append("</td>")
              .append("<td>").append(Html.esc(r.status)).append("</td>")
              .append("<td><code>").append(Html.esc(r.shownPrefix())).append("</code></td>")
              .append("<td>").append(time(r.created)).append("</td>")
              .append("<td>").append(time(r.confirmed)).append("</td>")
              .append("<td>").append(time(r.lastUsed)).append("</td>")
              .append(usageCells(r.usage)).append("</tr>");
        }
        sb.append("</table>");
        if (rows.size() > SHOWN) sb.append("<p class=\"aw-note\">The first ").append(SHOWN).append(" are shown; the CSV file has all ").append(rows.size()).append(".</p>");
        if (rows.isEmpty()) sb.append("<p class=\"aw-note\">No registrations").append(query(f).isEmpty() ? "" : " match the filter").append(".</p>");
        return sb.append("</div>").toString();
    }

    private static String usageCells(ApiKeyDb.Usage u) {
        return "<td class=\"aw-num\">" + num(u.today) + "</td><td class=\"aw-num\">" + num(u.last7) + "</td><td class=\"aw-num\">"
            + num(u.last30) + "</td><td class=\"aw-num\">" + num(u.records30) + "</td><td class=\"aw-num\">" + num(u.refused30) + "</td>";
    }

    static String keyPage(ApiKeyDb.Row r, HttpServletRequest request) {
        String app = AntwebProps.getDomainApp();
        String secure = AntwebProps.getSecureDomainApp();
        StringBuilder sb = new StringBuilder();
        sb.append("<p class=\"aw-note\"><a href=\"").append(Html.escAttr(app)).append("/apiKeys.do\">All API keys</a></p>");
        sb.append("<div class=\"aw-card\" id=\"api-key-details\"><h2>").append(Html.esc(r.name)).append("</h2><table class=\"aw-kv\">");
        kv(sb, "Number", String.valueOf(r.id));
        kv(sb, "Email", Html.esc(r.email));
        kv(sb, "Institution or affiliation", Html.esc(r.institution == null ? "" : r.institution));
        kv(sb, "Project", Html.esc(r.project).replace("\n", "<br>"));
        kv(sb, "Level", Html.esc(r.level));
        kv(sb, "Status", Html.esc(r.status) + (r.revoked != null ? " (revoked " + time(r.revoked) + ")" : ""));
        kv(sb, "Key", r.shownPrefix().isEmpty() ? "<i>not made yet (the email link was not opened)</i>" : "<code>" + Html.esc(r.shownPrefix()) + "...</code>");
        kv(sb, "Created", time(r.created));
        kv(sb, "Confirmed", time(r.confirmed));
        if (ApiKeys.PENDING.equals(r.status)) kv(sb, "Link valid until", time(r.confirmExpires));
        kv(sb, "Last used", time(r.lastUsed));
        kv(sb, "Requests today, 7 days, 30 days", num(r.usage.today) + ", " + num(r.usage.last7) + ", " + num(r.usage.last30));
        kv(sb, "Records, 30 days", num(r.usage.records30));
        kv(sb, "Refused (429), 30 days", num(r.usage.refused30));
        kv(sb, "Limits", limits(r));
        kv(sb, "Note", Html.esc(r.note == null ? "" : r.note).replace("\n", "<br>"));
        sb.append("</table></div>");

        String action = Html.escAttr(secure + "/apiKeys.do");
        String csrf = Csrf.field(request);
        String hidden = "<input type=\"hidden\" name=\"id\" value=\"" + r.id + "\">";
        String confirmBox = "<label><input type=\"checkbox\" name=\"confirm\" value=\"yes\"> Yes, change this key</label> ";

        sb.append("<div class=\"aw-card\" id=\"api-key-status\"><h2>Revoke or reactivate</h2>");
        if (!ApiKeys.REVOKED.equals(r.status)) {
            sb.append("<p class=\"aw-lead\">A revoked key stops working at once. You can reactivate it later.</p>")
              .append("<form method=\"post\" action=\"").append(action).append("\">").append(csrf).append(hidden)
              .append("<input type=\"hidden\" name=\"op\" value=\"revoke\">").append(confirmBox)
              .append("<input class=\"aw-btn\" type=\"submit\" value=\"Revoke the key\"></form>");
        } else if (r.confirmed != null) {
            sb.append("<form method=\"post\" action=\"").append(action).append("\">").append(csrf).append(hidden)
              .append("<input type=\"hidden\" name=\"op\" value=\"reactivate\">").append(confirmBox)
              .append("<input class=\"aw-btn\" type=\"submit\" value=\"Reactivate the key\"></form>");
        } else {
            sb.append("<p class=\"aw-lead\">This registration was revoked before its email link was opened; it cannot be reactivated. The person can register again.</p>");
        }
        sb.append("</div>");

        sb.append("<div class=\"aw-card\" id=\"api-key-level\"><h2>Level</h2>")
          .append("<p class=\"aw-lead\">Registered: the default limits. Collaborator: the limits you set below (for a project you agreed with). Admin: AntWeb's own use.</p>")
          .append("<form method=\"post\" action=\"").append(action).append("\">").append(csrf).append(hidden)
          .append("<input type=\"hidden\" name=\"op\" value=\"level\"><div class=\"aw-row\"><select name=\"level\">");
        for (String l : ApiKeys.LEVELS) sb.append(option(l, l.equals(r.level)));
        sb.append("</select> ").append(confirmBox).append("<input class=\"aw-btn\" type=\"submit\" value=\"Save the level\"></div></form></div>");

        sb.append("<div class=\"aw-card\" id=\"api-key-limits\"><h2>Limits of this key</h2>")
          .append("<p class=\"aw-lead\">Leave a box blank to use the level's default (shown in grey).</p>")
          .append("<form method=\"post\" action=\"").append(action).append("\">").append(csrf).append(hidden)
          .append("<input type=\"hidden\" name=\"op\" value=\"limits\">")
          .append(limitBox("Requests a minute", "perMinute", r.perMinute, ApiKeys.REGISTERED_PER_MINUTE))
          .append(limitBox("Requests a day", "perDay", r.perDay, ApiKeys.REGISTERED_PER_DAY))
          .append(limitBox("Records an answer", "maxRecords", r.maxRecords, ApiKeys.REGISTERED_MAX_RECORDS))
          .append(limitBox("Records a day", "recordsPerDay", r.recordsPerDay, ApiKeys.REGISTERED_RECORDS_PER_DAY))
          .append("<div class=\"aw-actions\">").append(confirmBox).append("<input class=\"aw-btn\" type=\"submit\" value=\"Save the limits\"></div></form></div>");

        sb.append("<div class=\"aw-card\" id=\"api-key-note\"><h2>Note</h2>")
          .append("<p class=\"aw-lead\">For yourself: who they are, what was agreed. Only admins see it.</p>")
          .append("<form method=\"post\" action=\"").append(action).append("\">").append(csrf).append(hidden)
          .append("<input type=\"hidden\" name=\"op\" value=\"note\">")
          .append("<textarea name=\"note\" rows=\"4\" style=\"width:100%\" maxlength=\"").append(ApiKeys.MAX_NOTE).append("\" aria-label=\"Note\">")
          .append(Html.esc(r.note == null ? "" : r.note)).append("</textarea>")
          .append("<div class=\"aw-actions\">").append(confirmBox).append("<input class=\"aw-btn\" type=\"submit\" value=\"Save the note\"></div></form></div>");
        return sb.toString();
    }

    private static String limitBox(String label, String name, Integer value, int dflt) {
        return "<div class=\"aw-row\"><label>" + Html.esc(label) + " <input type=\"text\" name=\"" + name + "\" size=\"10\" value=\""
            + (value == null ? "" : value) + "\" placeholder=\"" + String.format("%,d", dflt) + "\"></label></div>";
    }

    static String limits(ApiKeyDb.Row r) {
        return part("a minute", r.perMinute) + ", " + part("a day", r.perDay) + ", " + part("records an answer", r.maxRecords)
            + ", " + part("records a day", r.recordsPerDay);
    }

    private static String part(String label, Integer v) {
        return v == null ? "default " + label : String.format("%,d", v) + " " + label;
    }

    private static void kv(StringBuilder sb, String label, String valueHtml) {
        sb.append("<tr><th scope=\"row\">").append(Html.esc(label)).append("</th><td>").append(valueHtml).append("</td></tr>");
    }

    static String time(LocalDateTime t) {
        return t == null ? "" : t.format(TIME);
    }

    private static String num(long n) {
        return String.format("%,d", n);
    }

    static String shorten(String s, int max) {
        if (s == null) return "";
        String one = s.replace('\n', ' ');
        return one.length() <= max ? one : one.substring(0, max - 3) + "...";
    }

    // ---------------------------------------------------------------- CSV

    public static final String[] CSV_HEADER = {"number", "name", "email", "institution", "project", "level", "status", "key (first 7 characters)",
        "created", "confirmed", "last used", "revoked", "requests a minute", "requests a day", "records an answer", "records a day",
        "requests today", "requests 7 days", "requests 30 days", "records 30 days", "refused 30 days", "note"};

    /** The list as CSV text: a header, the no-key row (number 0), then one line per key. */
    public static String csv(List<ApiKeyDb.Row> rows, ApiKeyDb.Usage noKey) {
        StringBuilder sb = new StringBuilder(ApiKeys.csvLine((Object[]) CSV_HEADER));
        sb.append(ApiKeys.csvLine(0, "All requests without a key", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
            noKey.today, noKey.last7, noKey.last30, noKey.records30, noKey.refused30, ""));
        for (ApiKeyDb.Row r : rows) {
            sb.append(ApiKeys.csvLine(r.id, r.name, r.email, r.institution, r.project, r.level, r.status, r.shownPrefix(),
                time(r.created), time(r.confirmed), time(r.lastUsed), time(r.revoked),
                r.perMinute, r.perDay, r.maxRecords, r.recordsPerDay,
                r.usage.today, r.usage.last7, r.usage.last30, r.usage.records30, r.usage.refused30, r.note));
        }
        return sb.toString();
    }

    private static void writeCsv(HttpServletResponse response, List<ApiKeyDb.Row> rows, ApiKeyDb.Usage noKey) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"antweb-api-keys-" + LocalDate.now() + ".csv\"");
        PrintWriter out = response.getWriter();
        out.write('﻿');  // so spreadsheets read accented names as UTF-8
        out.write(csv(rows, noKey));
        out.flush();
    }
}
