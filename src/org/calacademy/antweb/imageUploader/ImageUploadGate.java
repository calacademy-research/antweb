package org.calacademy.antweb.imageUploader;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.calacademy.antweb.upload.UploadAction;

/**
 * Shared state for image uploads (the one-file-per-request batch upload in ImageUploadBatchAction).
 *
 * - The site-wide "upload in process" flag (UploadAction.setIsInUploadProcess) is set while at
 *   least one image is being processed, and cleared when the last one finishes. Before, the flag
 *   was a plain string, so two image requests running at once would clear each other's flag.
 *   A specimen upload or the TaxonWorks nightly that holds the flag makes image requests wait.
 * - DB_LOCK serializes the image database rows: ImageDb.insertImage takes max(id)+1, so two
 *   inserts at the same moment could pick the same id and one image would be lost.
 * - A file name being processed is reserved, so a retry that arrives while the first try is
 *   still running (the browser gave up, the server did not) waits instead of backing up the
 *   new file over the backup of the old one.
 */
public final class ImageUploadGate {

    private static final Object FLAG_LOCK = new Object();
    private static int s_active = 0;
    private static String s_label = null;   // the flag value this class set (identity compare)

    /** Held around the image / image_uploaded database writes of one file. */
    public static final ReentrantLock DB_LOCK = new ReentrantLock(true);

    private static final Set<String> s_inProgress = ConcurrentHashMap.newKeySet();

    private ImageUploadGate() {}

    /**
     * Start processing images. False if another kind of upload (specimen upload, TaxonWorks
     * nightly) holds the flag: the caller answers "busy, try again shortly".
     */
    public static boolean enter(String who) {
        synchronized (FLAG_LOCK) {
            String current = UploadAction.getIsInUploadProcess();
            if (current != null && (s_active == 0 || current != s_label)) return false;
            if (s_active == 0) {
                s_label = "Image upload:" + who;
                UploadAction.setIsInUploadProcess(s_label);
            }
            s_active++;
            return true;
        }
    }

    /** Pair of enter(); call in a finally block. */
    public static void exit() {
        synchronized (FLAG_LOCK) {
            if (s_active > 0) s_active--;
            if (s_active == 0) {
                if (UploadAction.getIsInUploadProcess() == s_label) UploadAction.setIsInUploadProcess(null);
                s_label = null;
            }
        }
    }

    /** Reserve a file (code + file name). False if the same file is being processed now. */
    public static boolean reserve(String key) {
        return s_inProgress.add(key);
    }

    public static void release(String key) {
        if (key != null) s_inProgress.remove(key);
    }
}
