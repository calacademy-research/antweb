package org.calacademy.antweb.curate;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import com.google.gson.Gson;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.AntwebAction;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.Csrf;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.HtmlSanitizer;
import org.calacademy.antweb.util.LoginMgr;

/**
 * Editor trial (page editor plan, Phase 3): HugeRTE and TinyMCE 8 side by side, for curators and
 * administrators, so Brian and the curators can pick the editor that replaces CKEditor 3.
 *
 * READ ONLY. This action never writes to description_edit, object_edit or their history tables.
 *
 *   GET  /editorTrial.do                          the page, with empty editors (paste tests)
 *   GET  /editorTrial.do?q=pheidole               find stored fields by taxon name, specimen code or object key
 *   GET  /editorTrial.do?samples=1                a few stored fields with tables, old formatting, videos, Word styles
 *   GET  /editorTrial.do?src=taxon&key=<taxon_name>&field=overview
 *   GET  /editorTrial.do?src=specimen&key=<code>&field=notes
 *   GET  /editorTrial.do?src=object&key=<object_key>&field=contents
 *                                                 load that field's stored HTML into both editors
 *   POST /editorTrial.do op=sanitize field=<f> html=<editor HTML>   (CSRF token required)
 *                                                 JSON {"sanitized": ...}: the same HtmlSanitizer.sanitize()
 *                                                 rule the save path uses (DescriptionAction), so the page
 *                                                 can check whether an editor round-trips the stored text.
 */
public final class EditorTrialAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(EditorTrialAction.class);

    private static final int MAX_MATCHES = 300;
    private static final int MAX_SANITIZE_CHARS = 2_000_000;
    private static final Pattern FIELD = Pattern.compile("[A-Za-z0-9_]{1,64}");

    /** One stored field: where it is (taxon page, specimen, or object page) and its size. */
    public static final class Match {
        public final String src;    // taxon, specimen, object
        public final String key;    // taxon_name, specimen code, or object_key
        public final String field;  // the title column
        public final long length;
        public final String why;    // for samples: what markup it contains
        Match(String src, String key, String field, long length, String why) {
            this.src = src; this.key = key; this.field = field; this.length = length; this.why = why;
        }
    }

    /** What the page shows. */
    public static final class View {
        public String query = "";
        public List<Match> matches = new ArrayList<>();
        public boolean showSamples;
        public List<Match> samples = new ArrayList<>();
        public String src;
        public String key;
        public String field;
        public boolean selected;      // a stored field was asked for
        public boolean found;         // and a row exists
        public int rowCount;          // rows with that key (more than one is a data problem)
        public String stored = "";            // content as stored
        public String storedSanitized = "";   // HtmlSanitizer.sanitize(stored)
        public boolean dequoteChanges;        // the current pages run Formatter.dequote on load
        public boolean videos;
        public String validElements;
        public String error;
    }

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ActionForward denied = LoginMgr.mustBeCurator(request, mapping); if (denied != null) return denied;

        if ("POST".equalsIgnoreCase(request.getMethod())) {
            return sanitize(request, response);
        }

        View v = new View();
        String q = trim(request.getParameter("q"), 100);
        v.query = q == null ? "" : q;
        v.showSamples = "1".equals(request.getParameter("samples"));
        String src = request.getParameter("src");
        String key = trim(request.getParameter("key"), 255);
        String field = request.getParameter("field");
        if (src != null && key != null && field != null) {
            if (!("taxon".equals(src) || "specimen".equals(src) || "object".equals(src)) || !FIELD.matcher(field).matches()) {
                v.error = "Unknown page or field.";
            } else {
                v.selected = true; v.src = src; v.key = key; v.field = field;
            }
        }
        v.videos = v.selected && "videos".equals(v.field);
        v.validElements = HtmlSanitizer.editorValidElements(v.videos);

        if (v.query.length() >= 2 || v.showSamples || v.selected) {
            Connection connection = null;
            String dbMethodName = DBUtil.getDbMethodName("EditorTrialAction.execute()");
            try {
                DataSource dataSource = getDataSource(request, "conPool");
                connection = DBUtil.getConnection(dataSource, dbMethodName);
                if (v.query.length() >= 2) search(connection, v);
                if (v.showSamples) samples(connection, v);
                if (v.selected) load(connection, v);
            } catch (SQLException e) {
                s_log.error("execute() e:" + e);
                v.error = "Database error: " + e.getMessage();
            } finally {
                DBUtil.close(connection, dbMethodName);
            }
        }
        if (v.found) {
            v.storedSanitized = HtmlSanitizer.sanitize(v.stored, v.videos);
            String dq = Formatter.dequote(v.stored);
            v.dequoteChanges = dq != null && !dq.equals(v.stored);
        }

        request.setAttribute("editorTrial", v);
        return mapping.findForward("success");
    }

    /** POST op=sanitize: the sanitized form of the posted HTML, as JSON. Changes nothing. */
    private ActionForward sanitize(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        Map<String, Object> out = new LinkedHashMap<>();
        // SessionRequestFilter already refuses a logged-in POST without this session's token;
        // checked again here so this endpoint never answers without one.
        String refused = Csrf.check(request);
        if (refused != null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.put("error", "Security token missing or out of date. Reload the page.");
        } else if (!"sanitize".equals(request.getParameter("op"))) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.put("error", "Unknown request.");
        } else {
            String html = request.getParameter("html");
            if (html == null) html = "";
            if (html.length() > MAX_SANITIZE_CHARS) {
                response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                out.put("error", "Text too long.");
            } else {
                boolean videos = "videos".equals(request.getParameter("field"));
                out.put("sanitized", HtmlSanitizer.sanitize(html, videos));
                out.put("videos", videos);
            }
        }
        response.getWriter().write(new Gson().toJson(out));
        return null;
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String like(String s) {
        return "%" + s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static boolean isMissingTable(SQLException e) {
        return e.getErrorCode() == 1146;  // ER_NO_SUCH_TABLE
    }

    private static void search(Connection connection, View v) throws SQLException {
        String p = like(v.query);
        query(connection, v.matches, "taxon", null,
            "select taxon_name k, title, char_length(content) n from description_edit"
            + " where code is null and taxon_name like ? order by taxon_name, title limit " + MAX_MATCHES, p);
        query(connection, v.matches, "specimen", null,
            "select code k, title, char_length(content) n from description_edit"
            + " where code is not null and code like ? order by code, title limit " + MAX_MATCHES, p);
        query(connection, v.matches, "object", null,
            "select object_key k, title, char_length(content) n from object_edit"
            + " where object_key like ? order by object_key, title limit " + MAX_MATCHES, p);
    }

    // Stored fields that are hard for an editor to keep unchanged.
    private static final String[][] SAMPLE_PATTERNS = {
        {"<table", "table"}, {"<font", "font tag"}, {"<center", "center tag"}, {"<iframe", "video embed"},
        {"<img", "image"}, {"mso-", "Word styles"}, {"&nbsp;", "&nbsp;"}, {"&lt;", "escaped <"},
        {"<sup", "superscript"}, {"<div", "div"},
    };

    private static void samples(Connection connection, View v) throws SQLException {
        for (String[] sp : SAMPLE_PATTERNS) {
            String p = like(sp[0]);
            query(connection, v.samples, "taxon", sp[1],
                "select taxon_name k, title, char_length(content) n from description_edit"
                + " where code is null and content like ? limit 3", p);
            query(connection, v.samples, "specimen", sp[1],
                "select code k, title, char_length(content) n from description_edit"
                + " where code is not null and content like ? limit 1", p);
            query(connection, v.samples, "object", sp[1],
                "select object_key k, title, char_length(content) n from object_edit"
                + " where content like ? limit 2", p);
        }
    }

    private static void query(Connection connection, List<Match> into, String src, String why, String sql, String param)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    into.add(new Match(src, rs.getString("k"), rs.getString("title"), rs.getLong("n"), why));
                }
            }
        } catch (SQLException e) {
            if (!isMissingTable(e)) throw e;
        }
    }

    private static void load(Connection connection, View v) throws SQLException {
        String sql;
        if ("taxon".equals(v.src)) {
            sql = "select content from description_edit where taxon_name = ? and title = ? and code is null limit 5";
        } else if ("specimen".equals(v.src)) {
            sql = "select content from description_edit where code = ? and title = ? limit 5";
        } else {
            sql = "select content from object_edit where object_key = ? and title = ? limit 5";
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, v.key);
            ps.setString(2, v.field);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    v.rowCount++;
                    if (!v.found) {
                        v.found = true;
                        String c = rs.getString("content");
                        v.stored = c == null ? "" : c;
                    }
                }
            }
        } catch (SQLException e) {
            if (!isMissingTable(e)) throw e;
        }
    }
}
