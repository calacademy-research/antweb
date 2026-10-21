package org.calacademy.antweb.upload;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.Taxon;

/**
 * The reason on each line of the specimen upload report's "Recognized invalid species" section (Brian
 * Fisher, 2026-10-20): "old -> new: synonym: junior synonym of NEW (Author year)", worded and linked as
 * on the Name updates page (nameUpdates.do), from the same code (SpecimenNameUpdate.Reasons,
 * NameUpdateReason). The same old -> new pair reads the same in the upload report and on the page.
 *
 * In an upload the old name is the uploaded name: the specimen would be filed under it, and goes under
 * the current valid name instead. Each distinct pair is worked out once per upload (a few queries of
 * the taxon and description_edit tables, as the nightly name update does), not once per specimen.
 * If the reason cannot be worked out (the history cannot be read, any error), the line stays the plain
 * "old -> new": the upload never fails because of it.
 */
public final class UploadNameReasons {

    private static final Log s_log = LogFactory.getLog(UploadNameReasons.class);

    /** (old taxon name, new taxon name) -> reason; may return null or throw. */
    private final BiFunction<String, String, NameUpdateReason> source;
    private final Map<String, String> htmlByPair = new HashMap<>();
    private boolean broken = false;

    public UploadNameReasons(BiFunction<String, String, NameUpdateReason> source) {
        this.source = source;
    }

    /** For an upload: the reasons of the nightly name update, read from this connection. */
    public static UploadNameReasons of(Connection connection) {
        SpecimenNameUpdate.Reasons reasons = new SpecimenNameUpdate.Reasons(connection);
        return new UploadNameReasons((oldName, newName) -> {
            NameUpdateReason r = reasons.get(oldName, oldName, newName);
            if (reasons.failed()) throw new IllegalStateException("the taxon table or AntCat's history could not be read");
            return r;
        });
    }

    /**
     * The report line: plainLine ("old -> new" with links) followed by ": " and the reason as HTML, or
     * plainLine alone when there is no reason.
     */
    public String line(String plainLine, String oldTaxonName, String newTaxonName) {
        if (plainLine == null) return null;
        String reason = reasonHtml(oldTaxonName, newTaxonName);
        return reason.isEmpty() ? plainLine : plainLine + reason;
    }

    /** ": <span class="name-update-reason">...</span>", or "" (no reason, or it could not be worked out). */
    String reasonHtml(String oldTaxonName, String newTaxonName) {
        if (broken || oldTaxonName == null || newTaxonName == null || oldTaxonName.isEmpty() || newTaxonName.isEmpty()) return "";
        String key = oldTaxonName + "\t" + newTaxonName;
        String html = htmlByPair.get(key);
        if (html != null) return html;
        try {
            html = reasonPart(source.apply(oldTaxonName, newTaxonName), oldTaxonName, oldTaxonName, newTaxonName);
        } catch (RuntimeException e) {
            broken = true;   // one failure: the rest of the report keeps the plain lines (and the upload goes on)
            s_log.warn("reasonHtml() no reasons in this upload report. old:" + oldTaxonName + " new:" + newTaxonName + " e:" + e);
            return "";
        }
        htmlByPair.put(key, html);
        return html;
    }

    /**
     * The reason part of a line, the same on the Name updates page and in the upload report:
     * ": <span class="name-update-reason">reason with names in italics and the reference linked to AntCat</span>".
     */
    public static String reasonPart(NameUpdateReason reason, String uploadedTaxonName, String oldTaxonName, String newTaxonName) {
        if (reason == null || reason.text == null || reason.text.isEmpty()) return "";
        return reasonPart(reason.text, reason.antcatId, uploadedTaxonName, oldTaxonName, newTaxonName);
    }

    public static String reasonPart(String text, Integer antcatId, String uploadedTaxonName, String oldTaxonName, String newTaxonName) {
        if (text == null || text.isEmpty()) return "";
        return ": <span class=\"name-update-reason\">" + NameUpdateReason.toHtml(text, antcatId,
            Taxon.getPrettyTaxonName(uploadedTaxonName), Taxon.getPrettyTaxonName(oldTaxonName), Taxon.getPrettyTaxonName(newTaxonName))
            + "</span>";
    }
}
