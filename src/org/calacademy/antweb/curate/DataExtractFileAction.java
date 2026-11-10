package org.calacademy.antweb.curate;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.AntwebAction;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.util.Check;
import org.calacademy.antweb.util.DataExtractFiles;
import org.calacademy.antweb.util.LogMgr;
import org.calacademy.antweb.util.DateUtil;
import org.calacademy.antweb.util.LoginMgr;

/**
 * dataExtractFile.do?job=N&amp;file=specimens.csv.gz: downloads one file of a finished data extract (dataExtract.do),
 * or file=all.zip for all of them in one zip file. Administrators only: anyone else gets the log-in page, never
 * a file.
 *
 * The folder is found by the job number and the file by a name from a fixed list (DataExtractFiles.DOWNLOADS):
 * no part of a path comes from the request, so nothing outside an extract's folder can be asked for. An unknown
 * job, a name not on the list, or an extract that has not finished (no readable manifest yet) is a 404. An
 * extract that is not verified has no zip (404), and its files are saved as antweb-extract-NOT-VERIFIED-...,
 * so nobody sends one by mistake. The extract folders are outside the web application: no address reaches them.
 * Each download is noted in admin.log (who, which job, which file).
 *
 * Tests: tests/e2e/test_data_extract.py, test/org/calacademy/antweb/util/DataExtractTest.java (the file lookup).
 */
public final class DataExtractFileAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(DataExtractFileAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        ActionForward a = Check.login(request, mapping); if (a != null) return a;
        ActionForward c = Check.admin(request, mapping); if (c != null) return c;
        Login login = LoginMgr.getAccessLogin(request);

        int jobId = 0;
        try {
            String job = request.getParameter("job");
            if (job != null && job.trim().matches("\\d{1,9}")) jobId = Integer.parseInt(job.trim());
        } catch (NumberFormatException e) {
            jobId = 0;
        }
        String name = request.getParameter("file");
        File root = DataExtractFiles.root();
        boolean zip = DataExtractFiles.ZIP.equals(name);
        boolean verified = DataExtractFiles.isVerified(root, jobId);
        List<File> all = !zip ? null : verified ? DataExtractFiles.files(root, jobId) : new java.util.ArrayList<>();
        File file = zip ? null : DataExtractFiles.file(root, jobId, name);
        if (zip ? all.isEmpty() : file == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        File folder = zip ? all.get(0).getParentFile() : file.getParentFile();
        String saveAs = DataExtractFiles.downloadName(folder, zip ? DataExtractFiles.ZIP : file.getName(), verified);

        response.setContentType(DataExtractFiles.contentType(saveAs));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + saveAs + "\"");
        LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr() + " data extract download by " + login.getName()
            + ": job " + jobId + " " + (zip ? DataExtractFiles.ZIP : file.getName()) + (verified ? "" : " (NOT VERIFIED)"));
        try (OutputStream out = response.getOutputStream()) {
            if (zip) {
                DataExtractFiles.writeZip(all, out);
            } else {
                response.setContentLengthLong(file.length());
                Files.copy(file.toPath(), out);
            }
        } catch (IOException e) {
            s_log.warn("execute() download of job " + jobId + " ended early e:" + e);
        }
        return null;
    }
}
