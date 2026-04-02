package org.calacademy.antweb.curate.speciesList;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.upload.FormFile;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.Check;
import org.calacademy.antweb.ValidationParseException;

public class ValidateSpeciesListAction extends SpeciesListSuperAction {

    private static final Log s_log = LogFactory.getLog(ValidateSpeciesListAction.class);

    @Override
    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) {

        ActionForward loginCheck = Check.login(request, mapping);
        if (loginCheck != null) return loginCheck;

        ValidateSpeciesListForm toolForm = (ValidateSpeciesListForm) form;
        FormFile uploadedFile = toolForm.getFile();

        Connection connection = null;
        try {
            // Check downloads first, since they won't have the uploaded file!
            if ("download".equals(toolForm.getAction()) || "downloadCorrected".equals(toolForm.getAction())) {
                ValidateSpeciesReport cachedReport = (ValidateSpeciesReport) request.getSession().getAttribute("validationReport");
                if (cachedReport != null) {
                    response.setContentType("text/tab-separated-values");
                    if ("downloadCorrected".equals(toolForm.getAction())) {
                        response.setHeader("Content-Disposition", "attachment; filename=\"corrected_species_list_for_upload.tsv\"");
                        response.getWriter().write(cachedReport.generateCorrectedTsvReport());
                    } else {
                        response.setHeader("Content-Disposition", "attachment; filename=\"validation_report.tsv\"");
                        response.getWriter().write(cachedReport.generateTsvReport());
                    }
                    return null;
                } else {
                    request.setAttribute("message", "Session expired or no report found. Please re-validate your file.");
                    return mapping.findForward("validateSpeciesList");
                }
            }

            // Initial render or empty submission
            if (uploadedFile == null || uploadedFile.getFileSize() == 0) {
                return mapping.findForward("validateSpeciesList");
            }

            String filename = uploadedFile.getFileName().toLowerCase();
            if (!filename.endsWith(".txt") && !filename.endsWith(".tsv")) {
                request.setAttribute("message", "File must be a tab-delimited .txt or .tsv file.");
                return mapping.findForward("validateSpeciesList");
            }
            
            // 5MB max
            if (uploadedFile.getFileSize() > (5 * 1024 * 1024)) {
                request.setAttribute("message", "File is too large. Maximum size is 5MB (approx 50,000 rows).");
                return mapping.findForward("validateSpeciesList");
            }

            InputStream fileStream = uploadedFile.getInputStream();

            DataSource ds = getDataSource(request, "longConPool");
            connection = DBUtil.getConnection(ds, "ValidateSpeciesListAction.execute()");
            if (connection == null) {
                request.setAttribute("message", "Could not obtain database connection.");
                return mapping.findForward("validateSpeciesList");
            }

            // Enforce strictly read-only mode to prevent ALL data modifications.
            connection.setReadOnly(true);

            SpeciesListValidator validator = new SpeciesListValidator(connection);
            ValidateSpeciesReport report = validator.validate(fileStream, "UTF-8", toolForm.isShowUnmatched());

            request.getSession().setAttribute("validationReport", report);
            request.setAttribute("validationReport", report);
            return mapping.findForward("validateSpeciesList");

        } catch (ValidationParseException e) {
            s_log.warn("ValidateSpeciesListAction parse error: " + e.getMessage());
            request.setAttribute("message", e.getMessage());
            return mapping.findForward("validateSpeciesList");
        } catch (SQLException e) {
            s_log.error("ValidateSpeciesListAction DB error: " + e);
            request.setAttribute("message", "Database error occurred during validation: " + e.getMessage());
            return mapping.findForward("validateSpeciesList");
        } catch (IOException e) {
            s_log.error("ValidateSpeciesListAction IO error: " + e);
            request.setAttribute("message", "Error reading uploaded file: " + e.getMessage());
            return mapping.findForward("validateSpeciesList");
        } finally {
            if (connection != null) {
                try { 
                    connection.setReadOnly(false); // Reset to default pool state
                } catch (SQLException e) { s_log.error("Failed to reset connection readOnly status", e); }
            }
            DBUtil.close(connection, this, "ValidateSpeciesListAction.execute()");
        }
    }
}
