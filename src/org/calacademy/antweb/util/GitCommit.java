package org.calacademy.antweb.util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The git commit the site was deployed from, shown in small print next to the
 * version number in the page header and linked to the commit on GitHub.
 *
 * .github/scripts/deploy.sh writes the full hash (git rev-parse HEAD) to
 * etc/git-commit on the server after every pull (~/antweb/etc/, which the
 * container sees as /antweb/deploy/etc/; the file is in .gitignore). The file
 * is re-read only when it changes, so a deploy that does not restart Tomcat
 * still shows the right commit. With no file, or anything other than a hash in
 * it, nothing extra is shown and nothing fails.
 */
public final class GitCommit {

    private static final Log s_log = LogFactory.getLog(GitCommit.class);

    static final String FILE = "/antweb/deploy/etc/git-commit";
    static final String COMMIT_URL = "https://github.com/calacademy-research/antweb/commit/";
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{40}");

    private static long s_mtime = -1;
    private static String s_hash = null;

    private GitCommit() {}

    /** Full 40-character commit hash, or null when it is not known. */
    public static synchronized String full() {
        try {
            File f = new File(FILE);
            long mtime = f.isFile() ? f.lastModified() : 0;
            if (mtime != s_mtime) {
                s_mtime = mtime;
                s_hash = null;
                if (mtime != 0) {
                    String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.US_ASCII).trim().toLowerCase();
                    if (HASH.matcher(s).matches()) s_hash = s;
                }
            }
        } catch (Exception e) {
            s_log.warn("full() could not read " + FILE + ": " + e);
            s_hash = null;
        }
        return s_hash;
    }

    /** First 7 characters of the commit hash, or null. */
    public static String shortHash() {
        String h = full();
        return h == null ? null : h.substring(0, 7);
    }

    /** Link to the commit on GitHub, or null. */
    public static String url() {
        String h = full();
        return h == null ? null : COMMIT_URL + h;
    }
}
