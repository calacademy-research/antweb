package org.calacademy.antweb;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.calacademy.antweb.util.*;

/**
 * History of one page-text field, for curators (page editor plan, phase 2).
 *
 * Where the versions come from:
 *   taxon pages      description_edit (taxon_name, title, code is null)  history: description_hist
 *   specimen pages   description_edit (code, title)                      history: description_hist
 *   overview pages   object_edit (object_key, title): projects, museums,  history: object_hist
 *                    bioregions, countries, regions, subregions, adm1
 *   homonym pages    description_homonym: not editable and no history table, so no History link.
 *
 * description_edit / object_edit hold the current text. Every save first copies the text it
 * replaces, with that text's own author and date, into the history table
 * (DescriptionAction.writeField). Clearing a field copies the text and then adds a history row
 * with empty text, the time and the person who cleared it. So each history row is a version, with
 * who wrote it and when; a row with empty text records a clear.
 *
 * Restoring a version is a normal save (DescriptionAction.saveDescriptionEdit with restoreFrom =
 * the history row's hist_id): same login, CSRF, sanitizer, conflict check, history copy and
 * write. Nothing is ever copied between the tables with SQL.
 */
public final class DescHistory {

    /** Request attribute holding the DescHistory shown by fieldHistory.do. */
    public static final String ATTR = "descHistory";

    /** At most this many history rows are listed (newest first). */
    public static final int MAX_VERSIONS = 200;

    /** Which page a field belongs to: kind taxon, specimen or object, and its key. Same form as DescEditSafety.pageKey. */
    public static final class Key {
        public final String kind;
        public final String name;

        private Key(String kind, String name) {
            this.kind = kind;
            this.name = name;
        }

        /** "taxon:<taxon_name>", "specimen:<code>" or "object:<object_key>"; null if not one of these. */
        public static Key parse(String pageKey) {
            if (pageKey == null) return null;
            int colon = pageKey.indexOf(':');
            if (colon < 0) return null;
            String kind = pageKey.substring(0, colon);
            String name = pageKey.substring(colon + 1);
            if (name.isEmpty() || name.length() > 255) return null;
            if (!"taxon".equals(kind) && !"specimen".equals(kind) && !"object".equals(kind)) return null;
            return new Key(kind, name);
        }

        public static Key of(Describable describable) {
            return parse(DescEditSafety.pageKey(describable));
        }

        @Override public String toString() { return kind + ":" + name; }

        /** The page name for headings: "Polyrhachis amana", "Specimen CASENT0922067", ... */
        public String displayName() {
            if ("taxon".equals(kind)) {
                String pretty = null;
                try { pretty = Taxon.getPrettyTaxonName(name); } catch (RuntimeException e) { /* show the key */ }
                return pretty == null || pretty.isEmpty() ? name : pretty;
            }
            if ("specimen".equals(kind)) return "Specimen " + name.toUpperCase();
            return name;
        }

        boolean isDesc() { return !"object".equals(kind); }
        String table() { return isDesc() ? "description_edit" : "object_edit"; }
        String histTable() { return isDesc() ? "description_hist" : "object_hist"; }
        String where(String alias) {
            if ("taxon".equals(kind)) return " where " + alias + ".taxon_name = ? and " + alias + ".title = ? and " + alias + ".code is null";
            if ("specimen".equals(kind)) return " where " + alias + ".code = ? and " + alias + ".title = ?";
            return " where " + alias + ".object_key = ? and " + alias + ".title = ?";
        }
    }

    /** One version of the field: the current text (histId 0) or a history row. */
    public static final class Version {
        public final int histId;              // 0: the current text
        public final String content;          // "" for a history row that records a clear
        public final Timestamp created;       // when this text was saved (or the field cleared)
        public final String who;              // login name, else group, else "unknown"

        Version(int histId, String content, Timestamp created, String who) {
            this.histId = histId;
            this.content = content == null ? "" : content;
            this.created = created;
            this.who = who;
        }

        public boolean isCurrent() { return histId == 0; }
        /** A history row with no text: the field was cleared at this time. */
        public boolean isClear() { return DescEditSafety.isBlank(content); }
        public int size() { return content.length(); }
        public String date() { return created == null ? "date unknown" : DateUtil.getFormatDateTimeStr(created); }
    }

    public final Key key;
    public final String field;
    /** The page the field is on, relative ("description.do?..."); null if not known (no Restore buttons). */
    public final String page;
    /** The stored text now, or null when the field is empty. */
    public final Version current;
    /** Fingerprint of the stored text now (DescEditSafety.hash): sent with Restore for the conflict check. */
    public final String currentHash;
    /** History rows, newest first. */
    public final List<Version> versions;
    public final boolean truncated;

    private DescHistory(Key key, String field, String page, Version current, List<Version> versions, boolean truncated) {
        this.key = key;
        this.field = field;
        this.page = page;
        this.current = current;
        this.currentHash = DescEditSafety.hash(current == null ? null : current.content);
        this.versions = versions;
        this.truncated = truncated;
    }

    public String prettyField() { return DescEditSafety.prettyField(field); }

    private static final String WHO_COLS = ", l.first_name, l.last_name, l.name login_name, g.name group_name, x.access_login, x.access_group";
    private static final String WHO_JOINS = " left join login l on l.id = x.access_login left join ant_group g on g.id = x.access_group";

    /** Read the current text and the history of one field. */
    public static DescHistory load(Connection connection, Key key, String field, String page) throws SQLException {
        Version current = null;
        String sql = "select x.content, x.created" + WHO_COLS + " from " + key.table() + " x" + WHO_JOINS + key.where("x");
        if ("specimen".equals(key.kind)) sql += " order by x.created desc, x.edit_id desc";
        sql += " limit 1";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, key.name);
            stmt.setString(2, field);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) current = new Version(0, rs.getString("content"), rs.getTimestamp("created"), who(rs));
            }
        }
        // A stored row is the current text even if it is blank: its fingerprint is what a save compares with.
        List<Version> versions = new ArrayList<>();
        boolean truncated = false;
        sql = "select x.hist_id, x.content, x.created" + WHO_COLS + " from " + key.histTable() + " x" + WHO_JOINS + key.where("x")
            + " order by x.created desc, x.hist_id desc limit " + (MAX_VERSIONS + 1);
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, key.name);
            stmt.setString(2, field);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    if (versions.size() == MAX_VERSIONS) { truncated = true; break; }
                    versions.add(new Version(rs.getInt("hist_id"), rs.getString("content"), rs.getTimestamp("created"), who(rs)));
                }
            }
        }
        return new DescHistory(key, field, page, current, versions, truncated);
    }

    /**
     * One history row of this page's field, or null if hist_id is not a version of exactly this
     * field on this page (so a Restore can never take text from another page or field).
     */
    public static Version readVersion(Connection connection, Describable describable, String field, int histId) throws SQLException {
        Key key = Key.of(describable);
        if (key == null || histId <= 0) return null;
        String sql = "select x.hist_id, x.content, x.created" + WHO_COLS + " from " + key.histTable() + " x" + WHO_JOINS
            + key.where("x") + " and x.hist_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, key.name);
            stmt.setString(2, field);
            stmt.setInt(3, histId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                return new Version(rs.getInt("hist_id"), rs.getString("content"), rs.getTimestamp("created"), who(rs));
            }
        }
    }

    private static String who(ResultSet rs) throws SQLException {
        String first = rs.getString("first_name");
        String last = rs.getString("last_name");
        String full = ((first == null ? "" : first.trim()) + " " + (last == null ? "" : last.trim())).trim();
        if (!full.isEmpty()) return full;
        String login = rs.getString("login_name");
        if (login != null && !login.trim().isEmpty()) return login.trim();
        String group = rs.getString("group_name");
        if (group != null && !group.trim().isEmpty()) return "group " + group.trim();
        String groupId = rs.getString("access_group");
        if (groupId != null && !groupId.trim().isEmpty()) return "group #" + groupId.trim();
        return "unknown";
    }

    // ---------- page address and History link ----------

    private static final Pattern PAGE = Pattern.compile("[A-Za-z][A-Za-z0-9]*\\.do(\\?[^<>\"'`\\\\\\r\\n\\t#]*)?");

    /**
     * The page address as a relative "x.do?..." without the editor's own parameters, or null if
     * it is not a plain AntWeb page address. Used for the "Back to the page" link and as the
     * Restore form's target (the save there checks the version belongs to that page).
     */
    public static String safePage(String page) {
        if (page == null) return null;
        String p = page.trim();
        String domainApp = AntwebProps.getDomainApp();
        if (domainApp != null && !domainApp.isEmpty() && p.startsWith(domainApp + "/")) p = p.substring(domainApp.length() + 1);
        else if (p.contains("://")) p = HttpUtil.trimDomainApp(p);
        while (p.startsWith("/")) p = p.substring(1);
        if (p.length() > 1000 || !PAGE.matcher(p).matches()) return null;
        int q = p.indexOf('?');
        if (q < 0) return p;
        StringBuilder kept = new StringBuilder();
        for (String pair : p.substring(q + 1).split("&")) {
            if (pair.isEmpty()) continue;
            String name = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
            if ("editField".equals(name) || "isSaveEditField".equals(name) || "restoreFrom".equals(name)
                || Csrf.PARAM.equals(name) || "field".equals(name)) continue;
            if (kept.length() > 0) kept.append('&');
            kept.append(pair);
        }
        return kept.length() == 0 ? p.substring(0, q) : p.substring(0, q) + "?" + kept;
    }

    /** Curators and admins see history of the fields they may edit (taxon "notes": admins and Jack only). */
    public static boolean canView(Login login, Key key, String field) {
        return key != null && DescEditSafety.canEditField(login, key.kind, field);
    }

    /** The fieldHistory.do address for a field of this page. */
    public static String url(String pageKey, String field, String page) {
        StringBuilder u = new StringBuilder(AntwebProps.getDomainApp()).append("/fieldHistory.do?field=")
            .append(URLEncoder.encode(field, StandardCharsets.UTF_8))
            .append("&key=").append(URLEncoder.encode(pageKey, StandardCharsets.UTF_8));
        String safe = safePage(page);
        if (safe != null) u.append("&page=").append(URLEncoder.encode(safe, StandardCharsets.UTF_8));
        return u.toString();
    }

    /**
     * The " [History]" link for a field, shown next to [Edit] to curators and admins; "" for
     * everyone else. Uses the page key set by DescriptionAction.prepareDescriptionEdit.
     */
    public static String link(HttpServletRequest request, String field, String page) {
        Object pageKey = request.getAttribute(DescEditSafety.PAGE_KEY_ATTR);
        if (pageKey == null || field == null || !DescriptionAction.EDIT_FIELDS.contains(field)) return "";
        Login login = LoginMgr.getAccessLogin(request);
        if (!canView(login, Key.parse(pageKey.toString()), field)) return "";
        return " <a class=\"descEditHistoryLink\" href=\"" + Html.escAttr(url(pageKey.toString(), field, page))
            + "\" title=\"Earlier versions of this field; restore one\">[History]</a>";
    }
}
