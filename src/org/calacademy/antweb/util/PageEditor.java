package org.calacademy.antweb.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Which editor the curator page-text forms use: TinyMCE 8 (default) or the old CKEditor 3
 * (fallback until CKEditor is removed in phase 4 of the page editor plan).
 *
 * Switch, no code change or deploy needed:
 *   etc/editor.properties on the server (~/antweb/etc/, seen in the container as
 *   /antweb/deploy/etc/editor.properties; gitignored) with the line
 *       pageEditor=ckeditor
 *   puts every edit form back on CKEditor 3 within a minute (no restart). Delete the file, or
 *   set pageEditor=tinymce, to go back to TinyMCE. A missing, unreadable or unknown value means TinyMCE.
 *
 * One page only: adding pageEditor=ckeditor (or pageEditor=tinymce) to an edit page's address
 * overrides the setting for that page (the edit form carries it into the save, so a refused save
 * shows the same editor again).
 *
 * Both editors post the same form (textarea "contents", loadedHash, editorReady, clearField), and
 * the safety rules (editSafety.js, DescEditSafety) work with either.
 *
 * Rollback: as above (setting), or revert the pull request.
 */
public final class PageEditor {

    private static final Log s_log = LogFactory.getLog(PageEditor.class);

    public static final String TINYMCE = "tinymce";
    public static final String CKEDITOR = "ckeditor";
    /** Request parameter that overrides the setting for one page. */
    public static final String PARAM = "pageEditor";

    /** TinyMCE version in use: its files are in web/common/editors/tinymce/<version>/. */
    public static final String TINYMCE_VERSION = "8.10.0";
    public static final String TINYMCE_BASE = "/common/editors/tinymce/" + TINYMCE_VERSION;
    public static final String TINYMCE_JS = TINYMCE_BASE + "/tinymce.min.js";
    public static final String TINYMCE_SOURCE_URL = "https://github.com/tinymce/tinymce/tree/" + TINYMCE_VERSION;
    /** The shared TinyMCE settings (also used by the editor trial page). */
    public static final String SETUP_JS = "/common/editors/antwebEditor.js";
    public static final String CKEDITOR_JS = "/ckeditor/ckeditor.js";

    static final String CONFIG = "/antweb/deploy/etc/editor.properties";
    private static final long RELOAD_MS = 60_000;

    private static String s_configured = TINYMCE;
    private static long s_loaded = 0;

    private PageEditor() {}

    /** The editor setting from etc/editor.properties (re-read at most once a minute). */
    public static synchronized String configured() {
        long now = System.currentTimeMillis();
        if (s_loaded == 0 || now - s_loaded > RELOAD_MS) {
            s_loaded = now;
            s_configured = read(new File(CONFIG));
        }
        return s_configured;
    }

    static String read(File f) {
        if (!f.isFile()) return TINYMCE;
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            p.load(in);
        } catch (Exception e) {
            s_log.error("read() could not read " + f + " e:" + e + "; using " + TINYMCE);
            return TINYMCE;
        }
        String v = normalize(p.getProperty("pageEditor"));
        return v == null ? TINYMCE : v;
    }

    /** "tinymce" or "ckeditor" (any case, trimmed), else null. */
    static String normalize(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase();
        return TINYMCE.equals(v) || CKEDITOR.equals(v) ? v : null;
    }

    /** The editor for this request: the pageEditor parameter if valid, else the setting. */
    public static String choice(HttpServletRequest request) {
        String override = request == null ? null : normalize(request.getParameter(PARAM));
        return override != null ? override : configured();
    }

    public static boolean isTinyMce(HttpServletRequest request) {
        return TINYMCE.equals(choice(request));
    }

    /** The override given on this request ("tinymce" / "ckeditor"), or null. */
    public static String override(HttpServletRequest request) {
        return request == null ? null : normalize(request.getParameter(PARAM));
    }

    /** "?v=<token>" for the files TinyMCE loads itself (plugins, skin): changes with tinymce.min.js. */
    public static String tinyMceCacheSuffix(ServletContext context) {
        return "?v=" + Asset.v(context, TINYMCE_JS);
    }
}
