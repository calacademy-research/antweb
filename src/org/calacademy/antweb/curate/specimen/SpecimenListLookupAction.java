package org.calacademy.antweb.curate.specimen;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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

public class SpecimenListLookupAction extends org.apache.struts.action.Action {
    private static final Log s_log = LogFactory.getLog(SpecimenListLookupAction.class);

    @Override
    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) {
        ActionForward curatorCheck = Check.curator(request, mapping);
        if (curatorCheck != null) return curatorCheck;

        SpecimenListLookupForm lookupForm = (SpecimenListLookupForm) form;
        FormFile uploadedFile = lookupForm.getFile();
        if (!HttpUtil.isPost(request)) return mapping.findForward("specimenListLookup");
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

            response.setContentType("text/tab-separated-values; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"specimen_list_lookup.tsv\"");
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setContentLength((int) Files.size(outputFile));
            Files.copy(outputFile, response.getOutputStream());
            response.getOutputStream().flush();
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
}
