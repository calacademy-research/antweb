package org.calacademy.antweb.util;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.servlet.ServletException;
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

/**
 * Sanitize page text that was saved before description.do edits were sanitized (security batch 3).
 * Administrators only.
 *
 *   GET  /sanitizeDescriptions.do                 preview: per table, how many rows contain something
 *                                                 unsafe, and up to 20 samples with the reason and
 *                                                 the unsafe part. Changes nothing.
 *   POST /sanitizeDescriptions.do confirm=yes     apply: copy each of those rows into a new table
 *                                                 description_sanitize_backup_yyyyMMdd_HHmmss, then
 *                                                 write the sanitized text. One transaction.
 *
 * A row is cleaned only if HtmlSanitizer.findUnsafe() reports something removed for a security
 * reason (script and other active elements, on* handlers, javascript:/data: URLs, dangerous CSS).
 * Cosmetic differences (<br /> to <br>, closing unclosed tags, quoting, dropping id or rel) do not
 * count. A row that is cleaned gets the full HtmlSanitizer.sanitize(content, title is "videos"),
 * the same rule as the save path (DescriptionAction), so only the videos field keeps
 * YouTube/Vimeo iframes.
 *
 * Tables and their keys (edit_id is not usable: it is 0 on most rows since its auto_increment
 * primary key was dropped in 2014):
 *   description_edit     (taxon_name, title, code)   code is null for taxon pages, set for specimens
 *   description_homonym  (taxon_name, author_date, title)
 *   object_edit          (object_key, title)          project, geolocale and other object pages
 * A table that does not exist is skipped.
 *
 * Undo (all rows of one run), with BACKUP the table name the run reports:
 *   update description_edit d join BACKUP b on b.src_table='description_edit' and b.taxon_name=d.taxon_name
 *     and b.title=d.title and b.code<=>d.code set d.content=b.content;
 *   update description_homonym h join BACKUP b on b.src_table='description_homonym' and b.taxon_name=h.taxon_name
 *     and b.author_date<=>h.author_date and b.title=h.title set h.content=b.content;
 *   update object_edit o join BACKUP b on b.src_table='object_edit' and b.object_key=o.object_key
 *     and b.title=o.title set o.content=b.content;
 */
public final class SanitizeDescriptionsAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(SanitizeDescriptionsAction.class);

    private static final int SAMPLES = 20;
    private static final int SNIPPET = 60;

    /** One row that contains something unsafe. */
    static final class Change {
        final String table;
        final String taxonName;    // description_edit, description_homonym
        final String code;         // description_edit (null for taxon pages)
        final String authorDate;   // description_homonym
        final String objectKey;    // object_edit
        final String title;
        final String before;
        final String after;
        final List<HtmlSanitizer.Unsafe> unsafe;

        Change(String table, String taxonName, String code, String authorDate, String objectKey,
               String title, String before, String after, List<HtmlSanitizer.Unsafe> unsafe) {
            this.table = table; this.taxonName = taxonName; this.code = code; this.authorDate = authorDate;
            this.objectKey = objectKey; this.title = title; this.before = before; this.after = after;
            this.unsafe = unsafe;
        }

        String id() {
            if ("object_edit".equals(table)) return table + ": " + objectKey + " / " + title;
            if ("description_homonym".equals(table)) return table + ": " + taxonName + " (" + authorDate + ") / " + title;
            return table + ": " + taxonName + (code != null ? " specimen " + code : "") + " / " + title;
        }

        String reasons() {
            Set<String> r = new LinkedHashSet<>();
            for (HtmlSanitizer.Unsafe u : unsafe) r.add(u.reason);
            return String.join("; ", r);
        }

        /** About SNIPPET characters each side of the first unsafe part, whitespace collapsed. */
        String snippet() {
            int at = unsafe.isEmpty() ? 0 : unsafe.get(0).offset;
            int from = Math.max(0, at - SNIPPET);
            int to = Math.min(before.length(), at + SNIPPET);
            return (from > 0 ? "..." : "") + before.substring(from, to).replaceAll("\\s+", " ")
                + (to < before.length() ? "..." : "");
        }
    }

    /** A Change if the field contains something unsafe, else null. Same sanitize rule as DescriptionAction. */
    static Change check(String table, String taxonName, String code, String authorDate, String objectKey,
                        String title, String content) {
        if (content == null) return null;
        boolean videos = "videos".equals(title);
        List<HtmlSanitizer.Unsafe> unsafe = HtmlSanitizer.findUnsafe(content, videos);
        if (unsafe.isEmpty()) return null;
        String clean = HtmlSanitizer.sanitize(content, videos);
        if (content.equals(clean)) return null;
        return new Change(table, taxonName, code, authorDate, objectKey, title, content, clean, unsafe);
    }

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ActionForward denied = LoginMgr.mustBeAdmin(request, mapping); if (denied != null) return denied;
        Login admin = LoginMgr.getAccessLogin(request);
        String who = admin.getName() + " (id " + admin.getId() + ")";
        boolean apply = "POST".equalsIgnoreCase(request.getMethod()) && "yes".equals(request.getParameter("confirm"));

        StringBuilder msg = new StringBuilder();
        msg.append("<h2>Sanitize stored page text</h2>");

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("SanitizeDescriptionsAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "longConPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);

            List<Change> changes = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            int[] scanned = new int[3];
            scanned[0] = scanDescriptionEdit(connection, changes, notes);
            scanned[1] = scanHomonym(connection, changes, notes);
            scanned[2] = scanObjectEdit(connection, changes, notes);

            int cDesc = 0, cHom = 0, cObj = 0;
            for (Change c : changes) {
                if ("description_edit".equals(c.table)) cDesc++;
                else if ("description_homonym".equals(c.table)) cHom++;
                else cObj++;
            }

            if (apply) {
                if (changes.isEmpty()) {
                    msg.append("<p>Nothing to change.</p>");
                } else {
                    String backup = "description_sanitize_backup_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    int updated = applyChanges(connection, backup, changes);
                    msg.append("<p><b>Done.</b> ").append(updated).append(" of ").append(changes.size())
                       .append(" rows sanitized (a row edited since the scan is left alone). The previous text of each is in table <b>")
                       .append(Html.esc(backup)).append("</b>.</p>");
                    String line = DateUtil.getFormatDateTimeStr() + " sanitizeDescriptions by " + who + ": " + updated
                        + " rows (found description_edit " + cDesc + ", description_homonym " + cHom + ", object_edit " + cObj
                        + "), backup table " + backup;
                    LogMgr.appendLog("admin.log", line);
                    s_log.warn("execute() " + line);
                }
            } else {
                msg.append("<p>Preview only; nothing has been changed. Rows that contain something unsafe "
                    + "(script or other active elements, on* event handlers, javascript: or data: URLs, CSS with url() "
                    + "or expression(); outside the videos field, any iframe). Cosmetic differences are ignored. "
                    + "Only these rows are rewritten, with the same sanitizer used when page text is saved:</p>");
                msg.append("<ul>");
                msg.append("<li>description_edit: ").append(cDesc).append(" of ").append(scanned[0]).append(" rows</li>");
                msg.append("<li>description_homonym: ").append(cHom).append(" of ").append(scanned[1]).append(" rows</li>");
                msg.append("<li>object_edit: ").append(cObj).append(" of ").append(scanned[2]).append(" rows</li>");
                msg.append("</ul>");
                if (!changes.isEmpty()) {
                    msg.append("<p>Samples (up to ").append(SAMPLES).append("):</p><ol>");
                    for (int i = 0; i < changes.size() && i < SAMPLES; i++) {
                        Change c = changes.get(i);
                        msg.append("<li><b>").append(Html.esc(c.id())).append("</b><br>")
                           .append(Html.esc(c.reasons())).append("<br><code>").append(Html.esc(c.snippet()))
                           .append("</code><br>").append(c.before.length()).append(" -> ").append(c.after.length())
                           .append(" characters</li>");
                    }
                    msg.append("</ol>");
                    msg.append("<form method=\"POST\" action=\"").append(Html.escAttr(AntwebProps.getSecureDomainApp()))
                       .append("/sanitizeDescriptions.do\">").append(Csrf.field(request))
                       .append("<label><input type=\"checkbox\" name=\"confirm\" value=\"yes\"> Yes, sanitize these ")
                       .append(changes.size()).append(" rows (each is copied to a backup table first)</label> ")
                       .append("<input type=\"submit\" value=\"Sanitize\"></form>");
                }
            }
            for (String n : notes) msg.append("<p>").append(Html.esc(n)).append("</p>");
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            msg.append("<p>Database error; nothing was changed: ").append(Html.esc(e.getMessage())).append("</p>");
        } finally {
            DBUtil.close(connection, dbMethodName);
        }

        request.setAttribute("message", msg.toString());
        return mapping.findForward("message");
    }

    private static boolean isMissingTable(SQLException e) {
        return e.getErrorCode() == 1146;  // ER_NO_SUCH_TABLE
    }

    // The tables are small (about 9,000, 400 and 600 rows), so each is read in one query.

    private int scanDescriptionEdit(Connection connection, List<Change> changes, List<String> notes) throws SQLException {
        int count = 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("select taxon_name, title, code, content from description_edit order by taxon_name, title, code")) {
            while (rs.next()) {
                count++;
                Change c = check("description_edit", rs.getString("taxon_name"), rs.getString("code"), null, null,
                    rs.getString("title"), rs.getString("content"));
                if (c != null) changes.add(c);
            }
        } catch (SQLException e) {
            if (!isMissingTable(e)) throw e;
            notes.add("Table description_edit not found; skipped.");
        }
        return count;
    }

    private int scanHomonym(Connection connection, List<Change> changes, List<String> notes) throws SQLException {
        int count = 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("select taxon_name, author_date, title, content from description_homonym order by taxon_name, author_date, title")) {
            while (rs.next()) {
                count++;
                Change c = check("description_homonym", rs.getString("taxon_name"), null, rs.getString("author_date"), null,
                    rs.getString("title"), rs.getString("content"));
                if (c != null) changes.add(c);
            }
        } catch (SQLException e) {
            if (!isMissingTable(e)) throw e;
            notes.add("Table description_homonym not found; skipped.");
        }
        return count;
    }

    private int scanObjectEdit(Connection connection, List<Change> changes, List<String> notes) throws SQLException {
        int count = 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("select object_key, title, content from object_edit order by object_key, title")) {
            while (rs.next()) {
                count++;
                Change c = check("object_edit", null, null, null, rs.getString("object_key"),
                    rs.getString("title"), rs.getString("content"));
                if (c != null) changes.add(c);
            }
        } catch (SQLException e) {
            if (!isMissingTable(e)) throw e;
            notes.add("Table object_edit not found; skipped.");
        }
        return count;
    }

    /** Back up, then update, every changed row in one transaction. Returns the number of rows updated. */
    private int applyChanges(Connection connection, String backup, List<Change> changes) throws SQLException {
        // "created = created" in the updates keeps the curator's edit date (the column is
        // ON UPDATE CURRENT_TIMESTAMP in older schemas).
        // The backup table is created outside the transaction (DDL commits implicitly in MySQL).
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("create table " + backup + " ("
                + " backup_id int not null auto_increment primary key,"
                + " src_table varchar(32) not null,"
                + " taxon_name varchar(255) null,"
                + " code varchar(128) null,"
                + " author_date varchar(255) null,"
                + " object_key varchar(255) null,"
                + " title varchar(64) null,"
                + " reasons text,"
                + " content longtext,"
                + " sanitized_content longtext,"
                + " created timestamp not null default current_timestamp"
                + ") default charset=utf8mb4");
        }

        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        int updated = 0;
        try (PreparedStatement ins = connection.prepareStatement("insert into " + backup
                 + " (src_table, taxon_name, code, author_date, object_key, title, reasons, content, sanitized_content)"
                 + " values (?,?,?,?,?,?,?,?,?)");
             PreparedStatement updDesc = connection.prepareStatement(
                 "update description_edit set content = ?, created = created where taxon_name = ? and title = ? and code <=> ? and content = ?");
             PreparedStatement updHom = connection.prepareStatement(
                 "update description_homonym set content = ? where taxon_name = ? and author_date <=> ? and title = ? and content = ?");
             PreparedStatement updObj = connection.prepareStatement(
                 "update object_edit set content = ?, created = created where object_key = ? and title = ? and content = ?")) {
            for (Change c : changes) {
                ins.setString(1, c.table);
                ins.setString(2, c.taxonName);
                ins.setString(3, c.code);
                ins.setString(4, c.authorDate);
                ins.setString(5, c.objectKey);
                ins.setString(6, c.title);
                ins.setString(7, c.reasons());
                ins.setString(8, c.before);
                ins.setString(9, c.after);
                ins.executeUpdate();

                // "and content = ?": a row edited since the scan is left alone.
                if ("description_edit".equals(c.table)) {
                    updDesc.setString(1, c.after); updDesc.setString(2, c.taxonName); updDesc.setString(3, c.title);
                    updDesc.setString(4, c.code); updDesc.setString(5, c.before);
                    updated += updDesc.executeUpdate();
                } else if ("description_homonym".equals(c.table)) {
                    updHom.setString(1, c.after); updHom.setString(2, c.taxonName); updHom.setString(3, c.authorDate);
                    updHom.setString(4, c.title); updHom.setString(5, c.before);
                    updated += updHom.executeUpdate();
                } else {
                    updObj.setString(1, c.after); updObj.setString(2, c.objectKey); updObj.setString(3, c.title);
                    updObj.setString(4, c.before);
                    updated += updObj.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        return updated;
    }
}
