package org.calacademy.antweb.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Is AntWeb in the middle of something a restart would break? Answered by util.do?action=isBusy
 * (anonymous, read-only), which the deploy script (.github/scripts/deploy.sh) asks before it
 * restarts AntWeb, and used by AntwebSystem.isRestart() (bin/reboot_checker.sh).
 *
 * 2026-10-26: a production deploy restarted AntWeb while a curator was uploading images; her upload
 * (batch 6667) stayed unfinished with 8 images and she had to upload again.
 *
 * The answer is "idle" or "busy: " followed by the kinds of work running (never who started them):
 *   - an upload holding the site-wide upload flag (UploadAction): a specimen upload, the nightly
 *     TaxonWorks upload, the nightly name update, or an image file being processed;
 *   - an image upload in progress: the Image Uploader sends one file per request, so between two
 *     files nothing is running, but the upload is not finished. ImageUploadBatchAction reports each
 *     start and file here (imageBatchActive) and the finish (imageBatchFinished). A batch counts as
 *     in progress until it is finished or nothing has arrived for IMAGE_BATCH_IDLE_MS (the curator
 *     closed the page);
 *   - a job started from a page (BackgroundJobs), a data job (UtilDataAction's compute flag) or the
 *     nightly run (Scheduler).
 *
 * The decision (describe) takes plain values, so it is tested without a server or a database
 * (test/org/calacademy/antweb/util/BusyStatusTest.java).
 */
public final class BusyStatus {

    /** An image upload with no request for this long is treated as abandoned. */
    public static final long IMAGE_BATCH_IDLE_MS = 10L * 60 * 1000;

    /** Open image uploads: batch id to the time of its last request. */
    private static final Map<Integer, Long> s_imageBatches = new ConcurrentHashMap<>();

    private BusyStatus() {}

    /** An image upload started, or one of its files arrived. */
    public static void imageBatchActive(int batchId) {
        imageBatchActive(batchId, System.currentTimeMillis());
    }

    static void imageBatchActive(int batchId, long now) {
        if (batchId > 0) s_imageBatches.put(batchId, now);
    }

    /** An image upload was finished (step=finish). */
    public static void imageBatchFinished(int batchId) {
        s_imageBatches.remove(batchId);
    }

    /** How many image uploads had a request within IMAGE_BATCH_IDLE_MS before now. Forgets older ones. */
    public static int openImageBatches(long now) {
        int open = 0;
        for (Iterator<Map.Entry<Integer, Long>> it = s_imageBatches.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> e = it.next();
            if (now - e.getValue() < IMAGE_BATCH_IDLE_MS) open++;
            else it.remove();
        }
        return open;
    }

    /** For tests. */
    static void clearImageBatches() {
        s_imageBatches.clear();
    }

    /** The kind of upload that holds the site-wide upload flag (its value names the login: not shown). */
    static String uploadKind(String uploadFlag) {
        if (uploadFlag == null) return null;
        if (uploadFlag.startsWith("Image upload")) return "image upload";
        if (uploadFlag.startsWith("TaxonWorks nightly")) return "TaxonWorks nightly upload";
        if (uploadFlag.startsWith("nightly name update")) return "nightly name update";
        return "specimen upload";
    }

    /**
     * "idle", or "busy: " and the kinds of work running, comma separated.
     * uploadFlag: UploadAction.getIsInUploadProcess(); openImageBatches: openImageBatches(now);
     * backgroundJob: BackgroundJobs.isJobRunning(); dataJob: UtilDataAction.isInComputeProcess();
     * nightly: Scheduler.isRunning().
     */
    public static String describe(String uploadFlag, int openImageBatches, boolean backgroundJob,
                                  boolean dataJob, boolean nightly) {
        List<String> busy = new ArrayList<>();
        String upload = uploadKind(uploadFlag);
        if (upload != null) busy.add(upload);
        if (openImageBatches > 0 && !"image upload".equals(upload)) busy.add("image upload");
        if (backgroundJob) busy.add("background job");
        if (dataJob) busy.add("data job");
        if (nightly && !dataJob) busy.add("nightly run");
        return busy.isEmpty() ? "idle" : "busy: " + String.join(", ", busy);
    }

    /** The answer for this server now. */
    public static String current() {
        return describe(org.calacademy.antweb.upload.UploadAction.getIsInUploadProcess(),
            openImageBatches(System.currentTimeMillis()), BackgroundJobs.isJobRunning(),
            UtilDataAction.isInComputeProcess(), Scheduler.isRunning());
    }

    /** True while an image upload is in progress (a file being processed, or a batch not yet finished). */
    public static boolean isImageUploadInProgress() {
        return openImageBatches(System.currentTimeMillis()) > 0;
    }
}
