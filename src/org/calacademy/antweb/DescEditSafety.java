package org.calacademy.antweb;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Hashtable;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.calacademy.antweb.home.DescriptionTable;
import org.calacademy.antweb.util.*;

/**
 * Safety rules for the page-text editor (taxon, specimen and overview page fields).
 *
 * A save is accepted only when the editor fully loaded the stored text (the form carries
 * loadedHash, a fingerprint of the stored text when the editor opened, and editorReady=1, set by
 * the page script once the editor shows that text), when nobody else saved the field in between
 * (the stored text still has that fingerprint), and when the box is not empty (clearing a field
 * is the separate "Clear this field" action, clearField=1). Every refused save shows the
 * curator's text again with a message. See DescriptionAction.saveDescriptionEdit.
 *
 * The request attributes set by DescriptionAction and read by the JSPs are defined here.
 */
public final class DescEditSafety {

    private DescEditSafety() {}

    /** loadedHash of a field that has no stored text. */
    public static final String ABSENT = "absent";

    /** Request attribute: the Result of a save attempt in this request. */
    public static final String RESULT_ATTR = "descEditResult";
    /** Request attribute: the Loaded text of the field open in the editor. */
    public static final String LOAD_ATTR = "descEditLoad";
    /** Request attribute: the page key used for browser drafts ("taxon:...", "specimen:...", "object:..."). */
    public static final String PAGE_KEY_ATTR = "descEditPageKey";

    /** The old placeholder put into empty editors. Never saved as text. */
    private static final String PLACEHOLDER = "add your content here";

    /** The text that was in the database when the editor opened (fresh read, not the page cache). */
    public static final class Loaded {
        public final String field;
        public final String content;   // null: the field has no stored text
        public final String hash;      // ABSENT when content is null
        public final boolean failed;   // the database read failed: show no editor

        public Loaded(String field, String content, boolean failed) {
            this.field = field;
            this.content = content;
            this.hash = failed ? "" : hash(content);
            this.failed = failed;
        }
    }

    /** What happened to a save attempt. */
    public static final class Result {
        public final String field;
        public final boolean saved;     // true: stored (or nothing to do); false: refused
        public final String message;    // plain text for the curator
        public final String submitted;  // the curator's text, re-shown after a refusal (may be null)
        public boolean conflict;        // someone else saved the field since the editor opened
        public boolean offerClear;      // refused because the box was empty
        public boolean reshowInEditor;  // the curator can edit: show the text in the editor again
        public boolean loggedOut;       // the session had ended
        public boolean restore;         // a "Restore this version" from the History page (keeps any browser draft)

        private Result(String field, boolean saved, String message, String submitted) {
            this.field = field;
            this.saved = saved;
            this.message = message;
            this.submitted = submitted;
        }

        public static Result saved(String field, String message) {
            return new Result(field, true, message, null);
        }

        public static Result refused(String field, String message, String submitted) {
            return new Result(field, false, message, submitted);
        }
    }

    /** SHA-256 (hex) of the stored text, or ABSENT. The same fingerprint as SanitizeDescriptionsAction. */
    public static String hash(String content) {
        if (content == null) return ABSENT;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern CONTENT_TAG = Pattern.compile("<\\s*(img|iframe|table|hr)\\b", Pattern.CASE_INSENSITIVE);

    /** True if the HTML shows nothing: no text (spaces and &amp;nbsp; do not count) and no image, video, table or rule. */
    public static boolean isBlank(String html) {
        if (html == null) return true;
        if (CONTENT_TAG.matcher(html).find()) return false;
        String text = TAG.matcher(html).replaceAll(" ")
            .replace("&nbsp;", " ").replace("&#160;", " ").replace("&#xa0;", " ").replace(' ', ' ');
        text = text.trim();
        return text.isEmpty() || isPlaceholder(text);
    }

    /** The old "Add your content here" placeholder, with or without a full stop. */
    public static boolean isPlaceholder(String text) {
        if (text == null) return false;
        String t = TAG.matcher(text).replaceAll(" ").replace("&nbsp;", " ").trim().toLowerCase();
        if (t.endsWith(".")) t = t.substring(0, t.length() - 1).trim();
        return PLACEHOLDER.equals(t);
    }

    /**
     * True if two texts are the same apart from line endings (CRLF, CR or LF). Browsers send a text
     * box's line breaks as CRLF, so stored text with LF line breaks that was opened and saved
     * unchanged comes back with CRLF.
     */
    public static boolean sameText(String a, String b) {
        if (a == null || b == null) return a == b;
        if (a.equals(b)) return true;
        return a.replace("\r\n", "\n").replace('\r', '\n').equals(b.replace("\r\n", "\n").replace('\r', '\n'));
    }

    /** Curators and administrators may edit page text. */
    public static boolean canEdit(Login login) {
        return LoginMgr.isCurator(login) || LoginMgr.isAdmin(login);
    }

    /**
     * May this login edit (and restore) this field? Curators and administrators, except the taxon
     * page "notes" field, which only administrators and Jack edit (as on the page, taxonPage-body.jsp).
     * kind is the page key kind: "taxon", "specimen" or "object".
     */
    public static boolean canEditField(Login login, String kind, String field) {
        if (!canEdit(login)) return false;
        if ("taxon".equals(kind) && "notes".equals(field)) return LoginMgr.isAdmin(login) || LoginMgr.isJack(login);
        return true;
    }

    public static boolean canEditField(Login login, Describable describable, String field) {
        String key = describable == null ? "" : pageKey(describable);
        return canEditField(login, key.substring(0, Math.max(0, key.indexOf(':'))), field);
    }

    public static Result result(HttpServletRequest request) {
        Object r = request.getAttribute(RESULT_ATTR);
        return r instanceof Result ? (Result) r : null;
    }

    public static Loaded loaded(HttpServletRequest request) {
        Object l = request.getAttribute(LOAD_ATTR);
        return l instanceof Loaded ? (Loaded) l : null;
    }

    /**
     * The field to show in the editor on this page, or "none". After a save attempt: the field
     * again if the save was refused (so the curator gets their text back), otherwise none.
     */
    public static String activeEditField(HttpServletRequest request) {
        Result r = result(request);
        if (r != null) return (!r.saved && r.reshowInEditor) ? r.field : "none";
        if ("true".equals(request.getParameter("isSaveEditField"))) return "none";
        String f = request.getParameter("editField");
        if (f == null || !DescriptionAction.EDIT_FIELDS.contains(f)) return "none";
        return f;
    }

    /** The text for the editor box: the curator's own text after a refusal, else the stored text. */
    public static String editorText(Loaded load, Result result) {
        if (result != null && !result.saved && result.submitted != null) return result.submitted;
        if (load == null || load.content == null) return "";
        return load.content;
    }

    /** True if the description could not be read from the database when the page was built. */
    public static boolean loadFailed(Hashtable<?, ?> desc) {
        return desc instanceof DescriptionTable && ((DescriptionTable) desc).isLoadFailed();
    }

    /** The page key for browser drafts: which page the text belongs to. */
    public static String pageKey(Describable describable) {
        if (describable instanceof Specimen) return "specimen:" + ((Specimen) describable).getCode();
        if (describable instanceof Taxon) return "taxon:" + ((Taxon) describable).getTaxonName();
        return "object:" + describable.getName();
    }

    /** localStorage key for a draft: antweb.draft.<login id>.<page key>.<field>. Null if not known. */
    public static String draftKey(HttpServletRequest request, Login login, String field) {
        Object pageKey = request.getAttribute(PAGE_KEY_ATTR);
        if (login == null || pageKey == null || field == null) return null;
        return "antweb.draft." + login.getId() + "." + pageKey + "." + field;
    }

    /** A field name for messages ("taxanomicnotes" is "Taxonomic notes"). */
    public static String prettyField(String field) {
        if (field == null) return "";
        String pretty = DescEdit.getPrettyTitle(field);
        return pretty == null || pretty.isEmpty() ? field : pretty;
    }
}
