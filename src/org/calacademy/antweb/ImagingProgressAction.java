package org.calacademy.antweb;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.home.ImagingProgressDb;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.HttpUtil;

/**
 * /imagingProgress.do (public; util/imagingProgress.jsp; Brian Fisher, 2026-11-08): how many valid living ant species
 * and subspecies have a photo of the ant on AntWeb, overall, by bioregion, country, genus and museum, the trend since
 * 2026-10-28, and the names still to image. Reads the table imaging_progress (ImagingProgressDb, filled by the daily
 * update, set 4) and the statistics rows; nothing is counted on a visit except the unimaged list, read once per day.
 *
 *   (no parameter)     the page (request attributes progress, trend, computed)
 *   genus=<key>        the unimaged species and subspecies of one genus (key: subfamily + genus, as the genus table's links)
 *   format=csv         the unimaged list as a CSV download (with genus=: that genus only)
 *
 * Docs: docs/numbers.md rows 124 to 128, docs/data-model.md (imaging_progress). Tests: tests/e2e/test_imaging_progress.py,
 * test/.../home/ImagingProgressDbTest.java.
 */
public final class ImagingProgressAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(ImagingProgressAction.class);

    /** A genus key: a subfamily and genus name run together (lower case letters, digits, parentheses, _ . -). */
    static boolean validGenusKey(String key) {
        return key != null && key.length() <= 128 && key.matches("[A-Za-z0-9()_.\\-]+");
    }

    /** The download's file name. */
    static String csvFileName(String genusKey) {
        String day = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        return "antweb-unimaged-species" + (genusKey == null ? "" : "-" + genusKey.replaceAll("[^A-Za-z0-9_-]", "")) + "-" + day + ".csv";
    }

    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        HttpUtil.setUtf8(request, response);
        String genus = request.getParameter("genus");
        boolean csv = "csv".equals(request.getParameter("format"));
        if (genus != null && !validGenusKey(genus)) {
            request.setAttribute("message", "No such genus.");
            return mapping.findForward("message");
        }
        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("ImagingProgressAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            ImagingProgressDb db = new ImagingProgressDb(connection);
            if (!db.tableExists()) {
                request.setAttribute("notCounted", Boolean.TRUE);
                return mapping.findForward("success");
            }
            ImagingProgressDb.Row genusRow = genus == null ? null : db.read(ImagingProgressDb.GENUS, genus);
            if (genus != null && genusRow == null) {
                request.setAttribute("message", "No such genus in the imaging progress.");
                return mapping.findForward("message");
            }
            if (csv || genus != null) {
                List<ImagingProgressDb.Unimaged> list = db.unimaged();
                if (genus != null) {
                    List<ImagingProgressDb.Unimaged> one = new ArrayList<>();
                    for (ImagingProgressDb.Unimaged u : list) if (u.genusKey().equalsIgnoreCase(genus)) one.add(u);
                    list = one;
                }
                if (csv) {
                    byte[] body = ImagingProgressDb.csv(list).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    response.setContentType("text/csv; charset=UTF-8");
                    response.setHeader("Content-Disposition", "attachment; filename=\"" + csvFileName(genus) + "\"");
                    response.setHeader("X-Content-Type-Options", "nosniff");
                    response.setContentLength(body.length);
                    response.getOutputStream().write(body);
                    response.getOutputStream().flush();
                    return null;
                }
                request.setAttribute("genusRow", genusRow);
                request.setAttribute("genusList", list);
                return mapping.findForward("success");
            }
            Map<String, List<ImagingProgressDb.Row>> progress = new LinkedHashMap<>();
            java.sql.Timestamp computed = null;
            for (String kind : ImagingProgressDb.KINDS) {
                List<ImagingProgressDb.Row> rows = db.read(kind);
                progress.put(kind, rows);
                for (ImagingProgressDb.Row r : rows) if (r.computed != null && (computed == null || r.computed.after(computed))) computed = r.computed;
            }
            request.setAttribute("progress", progress);
            request.setAttribute("computed", computed);
            request.setAttribute("trend", db.trend());
            if (progress.get(ImagingProgressDb.ALL).isEmpty()) request.setAttribute("notCounted", Boolean.TRUE);
            return mapping.findForward("success");
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            request.setAttribute("message", "The imaging progress could not be read. Please try again later.");
            return mapping.findForward("message");
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }
    }
}
