package org.calacademy.antweb.imageUploader;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.core.FileUploadSizeException;
import org.apache.commons.fileupload2.jakarta.JakartaServletFileUpload;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.AntwebAction;
import org.calacademy.antweb.Artist;
import org.calacademy.antweb.Group;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.home.Copyright;
import org.calacademy.antweb.home.CopyrightDb;
import org.calacademy.antweb.home.ImageDb;
import org.calacademy.antweb.home.ImageUploadDb;
import org.calacademy.antweb.home.SpecimenDb;
import org.calacademy.antweb.upload.UploadNotice;
import org.calacademy.antweb.util.*;

/**
 * imageUploadBatch.do: the Image Uploader sends one file per request, so no request comes near
 * Cloudflare's limits (100 MB per request body; an answer within 100 seconds, else error 524).
 * Before, imageUploader.do took every selected file in one request and made all the image
 * sizes before answering: 4 or 5 TIFFs filled 100 MB, and 25 or more took longer than 100 s.
 *
 * One upload (one image_upload row, one report) spans many requests, all POST with the form
 * token (X-CSRF-Token header, checked by SessionRequestFilter) from a logged-in curator who may
 * upload images:
 *   step=start   artist=<id>, names=<one file name per line>  -> {"batchId":..,"rejected":[..]}
 *                Checks every name (naming rules, file type, specimen exists) before anything is
 *                sent, and creates the upload record if at least one name is good.
 *   step=add     batch=<id>, multipart with exactly one file -> {"status":"saved"|...}
 *                Checks the name again, saves the file (an existing file of that name is copied
 *                to images/backup/ first, as before), makes the four sizes and the image row.
 *                Answers in seconds for a normal TIFF. A retry of a file already saved in this
 *                batch is accepted and does not touch the backup.
 *   step=finish  batch=<id>                                  -> {"reportUrl":..}
 *                Marks the upload complete and regenerates the home page's recent images.
 * A batch belongs to the curator who started it: nobody else can add to or finish it.
 * dryRun=1 (start, add, finish) checks everything, reads the file, and saves nothing; the
 * end-to-end tests use it on staging.
 *
 * Errors answer JSON {"ok":false,"error":"...","retry":true|false}; retry=true (with
 * retryAfter seconds) means the server is busy and the same request can be sent again.
 */
public final class ImageUploadBatchAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(ImageUploadBatchAction.class);

    /** Largest single file: Cloudflare refuses request bodies over 100 MB. */
    public static final long MAX_FILE_BYTES = 95L * 1000 * 1000;
    private static final long MAX_REQUEST_BYTES = MAX_FILE_BYTES + 1024 * 1024;
    private static final int MAX_NAMES = 2000;

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setHeader("Cache-Control", "no-store");

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return json(response, 405, error("Use POST.", false));
        }

        // A logged-in curator who may upload images. The form token was checked by
        // SessionRequestFilter for every logged-in POST; an anonymous POST is refused here.
        Login accessLogin = LoginMgr.getAccessLogin(request);
        if (accessLogin == null) {
            return refuse(request, response, 401, "{\"ok\":false,\"login\":true,\"retry\":false,"
                + "\"error\":\"Not logged in. Log in again (in another tab), then press Resume.\"}");
        }
        if (!LoginMgr.isCurator(accessLogin) || !(Boolean.TRUE.equals(accessLogin.isUploadImages()) || accessLogin.isAdmin())) {
            return refuse(request, response, 403, error("This login may not upload images.", false));
        }
        Group accessGroup = GroupMgr.getAccessGroup(request);
        if (accessGroup == null) {
            return refuse(request, response, 403, error("This login has no group.", false));
        }

        if (Check.init(request, mapping) != null) {
            return refuse(request, response, 503, busy("AntWeb is starting up.", 60));
        }

        String step = request.getParameter("step");   // query string (the add request is multipart)
        boolean dryRun = "1".equals(request.getParameter("dryRun"));

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("ImageUploadBatchAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "longConPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            if (connection == null) return refuse(request, response, 503, busy("No database connection.", 30));

            if (DBStatus.isServerBusy(connection)) {
                return refuse(request, response, 503, busy("The server is busy.", 30));
            }

            if ("start".equals(step)) return start(request, response, connection, accessLogin, accessGroup, dryRun);
            if ("add".equals(step)) return add(request, response, connection, accessLogin, dryRun);
            if ("finish".equals(step)) return finish(request, response, connection, accessLogin, dryRun);
            return json(response, 400, error("Unknown step.", false));
        } catch (Exception e) {
            s_log.warn("execute() step:" + step + " login:" + accessLogin.getId() + " e:" + e);
            AntwebUtil.log("ImageUploadBatchAction step:" + step + " e:" + e);
            return json(response, 500, error("Server error: " + e.getClass().getSimpleName(), true));
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }
    }

    // ---------- start ----------

    private ActionForward start(HttpServletRequest request, HttpServletResponse response, Connection connection,
                                Login accessLogin, Group accessGroup, boolean dryRun) throws Exception {
        int artistId;
        try {
            artistId = Integer.parseInt(request.getParameter("artist"));
        } catch (Exception e) {
            return json(response, 400, error("Choose an artist.", false));
        }
        Artist artist = ArtistMgr.getArtist(artistId);
        if (artist == null) return json(response, 400, error("Unknown artist.", false));

        String namesParam = request.getParameter("names");
        List<String> names = new ArrayList<>();
        if (namesParam != null) {
            for (String n : namesParam.split("\n")) {
                n = n.trim();
                if (!n.isEmpty()) names.add(n);
            }
        }
        if (names.isEmpty()) return json(response, 400, error("No files.", false));
        if (names.size() > MAX_NAMES) return json(response, 400, error("At most " + MAX_NAMES + " files at a time.", false));

        SpecimenDb specimenDb = new SpecimenDb(connection);
        StringBuilder rejected = new StringBuilder();
        int good = 0;
        for (String name : names) {
            String reason = checkFile(name, specimenDb);
            if (reason == null) {
                good++;
            } else {
                if (rejected.length() > 0) rejected.append(',');
                rejected.append("{\"name\":\"").append(Html.js(name)).append("\",\"reason\":\"").append(Html.js(reason)).append("\"}");
            }
        }

        int batchId = 0;
        if (good > 0 && !dryRun) {
            ImageUpload imageUpload = new ImageUpload();
            imageUpload.setCuratorId(accessLogin.getId());
            imageUpload.setGroupId(accessGroup.getId());
            imageUpload.setArtistId(artistId);
            imageUpload.setCreated(new Date());
            imageUpload.setCopyright(new CopyrightDb(connection).getCurrentCopyright());
            imageUpload.setLicense(ImageUpload.LICENSE);
            imageUpload.setImages(new ArrayList<>());
            imageUpload.setImageCount(0);
            imageUpload = new ImageUploadDb(connection).saveImageUpload(imageUpload);
            batchId = imageUpload.getId();
            s_log.info("start() batch:" + batchId + " login:" + accessLogin.getId() + " files:" + good);
        }
        return json(response, 200, "{\"ok\":true,\"batchId\":" + batchId + ",\"dryRun\":" + dryRun
            + ",\"accepted\":" + good + ",\"maxBytes\":" + MAX_FILE_BYTES + ",\"rejected\":[" + rejected + "]}");
    }

    /** Null if the file name is acceptable and its specimen exists, else the reason. */
    private static String checkFile(String fileName, SpecimenDb specimenDb) throws SQLException {
        String reason = ImageUploaded.checkName(fileName);
        if (reason != null) return reason;
        ImageUploaded iu = new ImageUploaded();
        iu.init(ImageUploaded.baseName(fileName));
        if (!specimenDb.exists(iu.getCode())) return "Specimen " + iu.getCode().toUpperCase() + " is not in AntWeb. Upload its data first.";
        return null;
    }

    // ---------- add ----------

    private ActionForward add(HttpServletRequest request, HttpServletResponse response, Connection connection,
                              Login accessLogin, boolean dryRun) throws Exception {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("multipart/form-data")) {
            return json(response, 400, error("Expected a file.", false));
        }
        long length = request.getContentLengthLong();
        if (length > MAX_REQUEST_BYTES) {
            return json(response, 413, error("File larger than " + (MAX_FILE_BYTES / 1000000) + " MB.", false));
        }

        ImageUploadDb imageUploadDb = new ImageUploadDb(connection);
        ImageUpload imageUpload = null;
        if (!dryRun) {
            int batchId = intParam(request, "batch");
            imageUpload = batchId > 0 ? imageUploadDb.getImageUpload(batchId) : null;
            int status = 0;
            String refused = null;
            if (imageUpload == null) { status = 404; refused = "Upload " + batchId + " not found."; }
            else if (imageUpload.getCuratorId() != accessLogin.getId()) { status = 403; refused = "This upload was started by another login."; }
            else if (imageUpload.getIsComplete()) { status = 409; refused = "This upload is already finished. Start a new one."; }
            if (refused != null) {
                drain(request);   // read the file first, so the browser sees the answer
                return json(response, status, error(refused, false));
            }
        }

        ImageUploaded.tempDir.toFile().mkdirs();
        DiskFileItemFactory factory = DiskFileItemFactory.builder().setPath(ImageUploaded.tempDir).get();
        JakartaServletFileUpload<DiskFileItem, DiskFileItemFactory> upload = new JakartaServletFileUpload<>(factory);
        upload.setFileSizeMax(MAX_FILE_BYTES);
        upload.setSizeMax(MAX_REQUEST_BYTES);
        upload.setFileCountMax(5);

        List<DiskFileItem> items;
        try {
            items = upload.parseRequest(request);
        } catch (FileUploadSizeException e) {
            return json(response, 413, error("File larger than " + (MAX_FILE_BYTES / 1000000) + " MB.", false));
        } catch (FileUploadException e) {
            // Usually the connection was cut while the file was being sent: worth a retry.
            return json(response, 400, error("The file did not arrive complete.", true));
        }

        DiskFileItem fileItem = null;
        int files = 0;
        for (DiskFileItem item : items) {
            if (!item.isFormField()) {
                files++;
                fileItem = item;
            }
        }
        String reserved = null;
        boolean entered = false;
        try {
            if (files != 1) return json(response, 400, error("Send exactly one file per request.", false));
            String name = ImageUploaded.baseName(fileItem.getName());
            if (fileItem.getSize() <= 0) return json(response, 400, error("Empty file.", true));

            SpecimenDb specimenDb = new SpecimenDb(connection);
            String reason = checkFile(name, specimenDb);
            if (reason != null) return json(response, 422, fileError(name, reason));

            if (dryRun) {
                return json(response, 200, "{\"ok\":true,\"status\":\"checked\",\"dryRun\":true,\"name\":\""
                    + Html.js(name) + "\",\"bytes\":" + fileItem.getSize() + "}");
            }

            ImageUploaded imageUploaded = new ImageUploaded();
            imageUploaded.init(name);
            imageUploaded.setIsSpecimenDataExists(true);
            imageUploaded.setImageUpload(imageUpload);
            if (imageUpload.getCopyright() == null) imageUpload.setCopyright(new CopyrightDb(connection).getCurrentCopyright());

            reserved = imageUploaded.getCode() + "/" + name;
            if (!ImageUploadGate.reserve(reserved)) {
                reserved = null;
                return json(response, 409, busy("This file is still being processed from an earlier try.", 20));
            }
            // Already saved in this upload (the answer to an earlier try was lost): nothing to do.
            String state = imageUploadDb.getUploadedState(imageUpload.getId(), name);
            if ("done".equals(state)) {
                return json(response, 200, "{\"ok\":true,\"status\":\"alreadySaved\",\"name\":\"" + Html.js(name)
                    + "\",\"code\":\"" + Html.js(imageUploaded.getCode()) + "\",\"ms\":0}");
            }

            if (!ImageUploadGate.enter(accessLogin.getName())) {
                return json(response, 503, busy("Another upload is running on the server.", 30));
            }
            entered = true;

            long t0 = System.currentTimeMillis();
            boolean firstTry = state == null;
            if (firstTry) {
                // Order matters: back up an existing image, then record the file as "processing",
                // then write it. A later try of a file left "processing" makes no new backup, so
                // the backup always holds the image that was there before this upload.
                imageUploaded.backupExisting();
                ImageUploadGate.DB_LOCK.lock();
                try {
                    imageUploadDb.saveImageUploadedProcessing(imageUploaded);
                    imageUploadDb.updateImageCount(imageUpload.getId());
                } finally {
                    ImageUploadGate.DB_LOCK.unlock();
                }
            }
            imageUploaded.setFileItem(fileItem, false);
            imageUploaded.genImages();   // the four sizes (thumbview, low, med, high)
            ImageUploadGate.DB_LOCK.lock();
            try {
                new ImageDb(connection).putImage(imageUploaded);
                imageUploadDb.markUploadedDone(imageUpload.getId(), name, imageUploaded.getIsReUploaded());
            } finally {
                ImageUploadGate.DB_LOCK.unlock();
            }
            long ms = System.currentTimeMillis() - t0;
            String status = imageUploaded.getIsReUploaded() ? "replaced" : firstTry ? "saved" : "savedOnRetry";
            s_log.info("add() batch:" + imageUpload.getId() + " file:" + name + " status:" + status + " ms:" + ms);
            return json(response, 200, "{\"ok\":true,\"status\":\"" + status + "\",\"name\":\"" + Html.js(name)
                + "\",\"code\":\"" + Html.js(imageUploaded.getCode()) + "\",\"ms\":" + ms + "}");
        } finally {
            if (entered) ImageUploadGate.exit();
            ImageUploadGate.release(reserved);
            for (DiskFileItem item : items) {
                try {
                    item.delete();
                } catch (Exception e) {
                    s_log.debug("add() delete temp e:" + e);
                }
            }
        }
    }

    // ---------- finish ----------

    private ActionForward finish(HttpServletRequest request, HttpServletResponse response, Connection connection,
                                 Login accessLogin, boolean dryRun) throws Exception {
        if (dryRun) return json(response, 200, "{\"ok\":true,\"dryRun\":true}");
        int batchId = intParam(request, "batch");
        ImageUploadDb imageUploadDb = new ImageUploadDb(connection);
        ImageUpload imageUpload = batchId > 0 ? imageUploadDb.getImageUpload(batchId) : null;
        if (imageUpload == null) return json(response, 404, error("Upload " + batchId + " not found.", false));
        if (imageUpload.getCuratorId() != accessLogin.getId()) {
            return json(response, 403, error("This upload was started by another login.", false));
        }
        int count = imageUpload.getImageCount();
        if (!imageUpload.getIsComplete()) {   // finishing twice changes nothing
            count = imageUploadDb.updateImageCount(batchId);
            imageUploadDb.completeImageUpload(imageUpload);
            if (count > 0) new ImageUploaderAction().writeRecentImages(connection);
            s_log.info("finish() batch:" + batchId + " login:" + accessLogin.getId() + " images:" + count);
            sendNotice(accessLogin, imageUpload, count);
        }
        String reportUrl = AntwebProps.getDomainApp() + "/imageUploadReport.do?id=" + batchId;
        return json(response, 200, "{\"ok\":true,\"batchId\":" + batchId + ",\"images\":" + count
            + ",\"reportUrl\":\"" + Html.js(reportUrl) + "\"}");
    }

    /** Emails the uploader that the upload is complete (UploadNotice). Sent once, when the upload is first finished. */
    private static void sendNotice(Login accessLogin, ImageUpload imageUpload, int count) {
        UploadNotice.Facts f = new UploadNotice.Facts();
        f.images = true;
        f.complete = true;
        f.batchImages = true;
        f.imagesSaved = count;
        f.reportUrl = AntwebProps.getDomainApp() + "/imageUploadReport.do?id=" + imageUpload.getId();
        f.reportNeedsLogin = true;
        UploadNotice.send(accessLogin, true, GroupMgr.getGroup(imageUpload.getGroupId()), f);
    }

    // ---------- helpers ----------

    private static int intParam(HttpServletRequest request, String name) {
        try {
            return Integer.parseInt(request.getParameter(name));
        } catch (Exception e) {
            return 0;
        }
    }

    /** Read and discard the rest of a refused upload, so the browser sees the answer. */
    private static void drain(HttpServletRequest request) {
        try (java.io.InputStream in = request.getInputStream()) {
            byte[] buf = new byte[65536];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0 && total < MAX_REQUEST_BYTES) total += n;
        } catch (Exception e) {
            // nothing to do
        }
    }

    /**
     * Answer before reading the request: a file still being sent is read and dropped first,
     * otherwise the server closes the connection and the browser sees a network error instead
     * of the answer (for example "log in again").
     */
    private static ActionForward refuse(HttpServletRequest request, HttpServletResponse response, int status, String body)
            throws IOException {
        String ct = request.getContentType();
        if (ct != null && ct.toLowerCase().startsWith("multipart/")) drain(request);
        return json(response, status, body);
    }

    private static String error(String message, boolean retry) {
        return "{\"ok\":false,\"retry\":" + retry + ",\"error\":\"" + Html.js(message) + "\"}";
    }

    private static String fileError(String name, String message) {
        return "{\"ok\":false,\"retry\":false,\"name\":\"" + Html.js(name) + "\",\"error\":\"" + Html.js(message) + "\"}";
    }

    private static String busy(String message, int retryAfter) {
        return "{\"ok\":false,\"retry\":true,\"retryAfter\":" + retryAfter + ",\"error\":\"" + Html.js(message) + "\"}";
    }

    private static ActionForward json(HttpServletResponse response, int status, String body) throws IOException {
        if (response.isCommitted()) return null;
        response.setStatus(status);
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(body);
        return null;
    }
}
