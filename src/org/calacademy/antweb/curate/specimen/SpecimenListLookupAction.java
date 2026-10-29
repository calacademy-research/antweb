package org.calacademy.antweb.curate.specimen;

import org.calacademy.antweb.AntwebAction;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.upload.FormFile;
import org.calacademy.antweb.Specimen;
import org.calacademy.antweb.home.SpecimenDb;
import org.calacademy.antweb.util.Check;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.HttpUtil;

/**
 * /specimenListLookup.do: Specimen List Lookup (curate/specimen/specimenListLookup.jsp), curators. A POST of a
 * .txt file of specimen codes (name contains "specimen", up to 5 MB) returns their specimen data as a TSV file
 * (table specimen, read-only connection). When some codes are not found the page lists them and keeps the TSV
 * for ?download=tsv (a temporary file deleted with the session). Docs: docs/guides/curate-page.md (Read-only
 * tools). Tests: tests/e2e/test_curator.py, test_curate_page_clarity.py.
 */
public class SpecimenListLookupAction extends AntwebAction {
    private static final Log s_log = LogFactory.getLog(SpecimenListLookupAction.class);

    // When some codes are not in AntWeb, the results page is shown and the TSV for the
    // codes that were found is kept for this session, for the "Download specimen data"
    // link (GitHub #64). Replaced by the next lookup; deleted when the session ends.
    static final String SESSION_TSV = "specimenListLookupTsv";
    private static final String TSV_NAME = "specimen_list_lookup.tsv";

    @Override
    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) {
        ActionForward curatorCheck = Check.curator(request, mapping);
        if (curatorCheck != null) return curatorCheck;

        SpecimenListLookupForm lookupForm = (SpecimenListLookupForm) form;
        FormFile uploadedFile = lookupForm.getFile();
        if (!HttpUtil.isPost(request)) {
            if ("tsv".equals(request.getParameter("download"))) return downloadSavedTsv(request, response, mapping);
            return mapping.findForward("specimenListLookup");
        }
        if (uploadedFile == null || uploadedFile.getFileSize() == 0) {
            request.setAttribute("message", "Choose a specimen list file before submitting.");
            return mapping.findForward("specimenListLookup");
        }

        String filenameError = SpecimenListLookupService.validateFilename(uploadedFile.getFileName());
        if (filenameError != null) {
            request.setAttribute("message", filenameError);
            return mapping.findForward("specimenListLookup");
        }
        if (uploadedFile.getFileSize() > SpecimenListLookupService.MAX_FILE_SIZE) {
            request.setAttribute("message", "File is too large. Maximum size is 5 MB.");
            return mapping.findForward("specimenListLookup");
        }

        Connection connection = null;
        Path outputFile = null;
        try {
            DataSource dataSource = getDataSource(request, "longConPool");
            connection = DBUtil.getConnection(dataSource, "SpecimenListLookupAction.execute()");
            if (connection == null) {
                request.setAttribute("message", "Could not obtain a database connection.");
                return mapping.findForward("specimenListLookup");
            }
            connection.setReadOnly(true);

            outputFile = Files.createTempFile("antweb-specimen-list-", ".tsv");
            SpecimenDb specimenDb = new SpecimenDb(connection);
            SpecimenListLookupSource source = new SpecimenListLookupSource() {
                @Override
                public Map<String, Specimen> findByCodes(List<String> codes) throws SQLException {
                    return specimenDb.getSpecimensByCodes(codes);
                }
            };

            SpecimenListLookupResult result;
            try (Writer writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
                result = new SpecimenListLookupService().lookup(
                        uploadedFile.getInputStream(), source, writer);
            }
            if (!result.isSuccess()) {
                request.setAttribute("lookupResult", result);
                request.setAttribute("message", "The lookup could not be completed. Correct the listed issues and try again.");
                return mapping.findForward("specimenListLookup");
            }

            HttpSession session = request.getSession();
            session.removeAttribute(SESSION_TSV); // deletes the previous lookup's file
            if (result.hasProblems()) {
                if (result.getFoundRowCount() > 0) {
                    session.setAttribute(SESSION_TSV, new SavedTsv(outputFile.toString()));
                    outputFile = null; // kept for the download link; SavedTsv deletes it
                }
                request.setAttribute("lookupResult", result);
                return mapping.findForward("specimenListLookup");
            }

            sendTsv(outputFile, response);
            return null;
        } catch (SQLException e) {
            s_log.error("Specimen list lookup database error", e);
            request.setAttribute("message", "A database error occurred during the lookup. Please try again.");
            return mapping.findForward("specimenListLookup");
        } catch (IOException e) {
            s_log.error("Specimen list lookup file error", e);
            request.setAttribute("message", "The specimen list file could not be processed. Please try again.");
            return mapping.findForward("specimenListLookup");
        } finally {
            if (connection != null) {
                try {
                    connection.setReadOnly(false);
                } catch (SQLException e) {
                    s_log.error("Failed to reset specimen lookup connection", e);
                }
            }
            DBUtil.close(connection, this, "SpecimenListLookupAction.execute()");
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException e) {
                    s_log.warn("Could not delete specimen lookup temporary file", e);
                }
            }
        }
    }

    private ActionForward downloadSavedTsv(HttpServletRequest request, HttpServletResponse response,
            ActionMapping mapping) {
        HttpSession session = request.getSession(false);
        SavedTsv saved = session == null ? null : (SavedTsv) session.getAttribute(SESSION_TSV);
        Path file = saved == null ? null : Path.of(saved.path);
        if (file == null || !Files.isRegularFile(file)) {
            request.setAttribute("message", "That download is no longer available. Please run the lookup again.");
            return mapping.findForward("specimenListLookup");
        }
        try {
            sendTsv(file, response);
            return null;
        } catch (IOException e) {
            s_log.error("Specimen list lookup download error", e);
            request.setAttribute("message", "The file could not be downloaded. Please run the lookup again.");
            return mapping.findForward("specimenListLookup");
        }
    }

    private static void sendTsv(Path file, HttpServletResponse response) throws IOException {
        response.setContentType("text/tab-separated-values; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + TSV_NAME + "\"");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentLengthLong(Files.size(file));
        Files.copy(file, response.getOutputStream());
        response.getOutputStream().flush();
    }

    /** Session-held TSV file; deleted when replaced, removed or when the session ends. */
    static final class SavedTsv implements HttpSessionBindingListener, java.io.Serializable {
        private static final long serialVersionUID = 1L;
        final String path;

        SavedTsv(String path) {
            this.path = path;
        }

        @Override
        public void valueUnbound(HttpSessionBindingEvent event) {
            try {
                Files.deleteIfExists(Path.of(path));
            } catch (IOException e) {
                s_log.warn("Could not delete specimen lookup file " + path, e);
            }
        }
    }
}
