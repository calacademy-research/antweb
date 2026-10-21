package org.calacademy.antweb.upload;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.Specimen;
import org.calacademy.antweb.curate.NameUpdatesAction;
import org.calacademy.antweb.util.Html;

/**
 * The "Name History" section of the specimen page (specimen.do; road map step 6, Brian Fisher,
 * 2026-10-21): how this specimen's name has changed and why, newest first.
 *
 *   Filed under: the name it is filed under now and the name in the curator's file ("uploaded as"),
 *     when they differ, with the reason the upload report gives (UploadNameReasons, the same code);
 *     when a nightly name update listed below made the move, it points there instead of repeating it.
 *   Needs the curator's decision: the latest nightly run held the specimen because its name belongs to
 *     two or more AntCat taxa (SharedNameReport.line, the words of the Name updates page). Shown only
 *     while the specimen is still filed under that name.
 *   Name update (date): each move the nightly name update made (specimen_name_update, mode 'changed'),
 *     "old -> new: reason", as on the Name updates page (NameUpdatesAction.nameLine,
 *     UploadNameReasons.reasonPart). Report-only rows (would_change, Preview) and the kept_valid
 *     spot-check list are never shown: nothing was changed for those.
 *   Previous determination: specimen.previous_determinations (TaxonWorks), moved here from Specimen
 *     Information (same markup, so the existing e2e test keeps working).
 *
 * Nothing at all (no heading) when there is none of these. Public page: every value is escaped (names
 * through Taxon.makeDescriptionLink and NameUpdateReason.toHtml, the rest through Html.esc); no group or
 * curator is named. One query on specimen_name_update by code (index by_code, in the table's DDL since
 * it was made); if the table cannot be read the section shows what the specimen row has.
 */
public final class SpecimenNameHistory {

    private static final Log s_log = LogFactory.getLog(SpecimenNameHistory.class);

    private SpecimenNameHistory() {}

    public static final String HEADING = "Name History";

    /** Shown after each nightly move. */
    public static final String NIGHTLY = "nightly name update from AntCat";

    /** One specimen_name_update row of this specimen. */
    public static final class Event {
        public Timestamp runDate;
        public String mode;
        public String uploadedName;
        public String oldName;
        public String newName;
        public String antcatStatus;
        public String reason;
        public Integer antcatId;

        public Event() {}

        public Event(Timestamp runDate, String mode, String uploadedName, String oldName, String newName,
                     String antcatStatus, String reason, Integer antcatId) {
            this.runDate = runDate;
            this.mode = mode;
            this.uploadedName = uploadedName;
            this.oldName = oldName;
            this.newName = newName;
            this.antcatStatus = antcatStatus;
            this.reason = reason;
            this.antcatId = antcatId;
        }

        boolean changed() { return SpecimenNameUpdate.CHANGED.equals(mode); }
        boolean shared() { return SpecimenNameUpdate.SHARED.equals(mode); }
    }

    /** The one query: rows of this specimen that were applied (changed) or hold it (shared_name), newest first. */
    public static final String QUERY = "select run_date, mode, uploaded_name, old_name, new_name, antcat_status, reason, antcat_id"
        + " from specimen_name_update where code = ? and mode in ('" + SpecimenNameUpdate.CHANGED + "', '" + SpecimenNameUpdate.SHARED + "')"
        + " order by run_date desc, id desc";

    /** The rows of one specimen (QUERY). Empty when there are none or the table cannot be read (never throws). */
    public static List<Event> read(Connection connection, String code) {
        List<Event> events = new ArrayList<>();
        if (connection == null || code == null || code.isEmpty()) return events;
        try (PreparedStatement stmt = connection.prepareStatement(QUERY)) {
            stmt.setString(1, code);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    int id = r.getInt(8);
                    Integer antcatId = r.wasNull() ? null : id;
                    events.add(new Event(r.getTimestamp(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5),
                        r.getString(6), r.getString(7), antcatId));
                }
            }
        } catch (SQLException e) {
            // A new database before the first nightly name update has no table: the page shows the rest.
            s_log.debug("read() code:" + code + " e:" + e);
        }
        return events;
    }

    /** For SpecimenAction: the section of this specimen, or "" (never throws; the page must render). */
    public static String html(Connection connection, Specimen specimen) {
        if (specimen == null) return "";
        try {
            List<Event> events = read(connection, specimen.getCode());
            String filed = specimen.getParentTaxonName();
            boolean needsShared = false;
            for (Event e : events) if (e.shared() && e.oldName != null && e.oldName.equals(filed)) needsShared = true;
            SharedNames shared = needsShared ? SharedNames.load(connection) : SharedNames.none();
            UploadNameReasons reasons = connection == null ? null : UploadNameReasons.of(connection);
            return render(filed, specimen.getOriginalTaxonName(), specimen.getPreviousDeterminationList(), events, shared, reasons);
        } catch (RuntimeException e) {
            s_log.warn("html() code:" + specimen.getCode() + " e:" + e);
            return render(specimen.getParentTaxonName(), null, specimen.getPreviousDeterminationList(), new ArrayList<>(), null, null);
        }
    }

    /**
     * The section. filedTaxonName: specimen.taxon_name; uploadedTaxonName: specimen.original_taxon_name
     * (may be null); events newest first (read()); shared and reasons may be null (no candidates list,
     * no worked-out reason for the uploaded name). "" when there is nothing to show.
     */
    public static String render(String filedTaxonName, String uploadedTaxonName, List<String> previousDeterminations,
                                List<Event> events, SharedNames shared, UploadNameReasons reasons) {
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd");
        StringBuilder rows = new StringBuilder();

        // Filed under (uploaded as).
        boolean uploadedDiffers = uploadedTaxonName != null && !uploadedTaxonName.isEmpty() && filedTaxonName != null
            && !filedTaxonName.isEmpty() && !uploadedTaxonName.equals(filedTaxonName);
        if (uploadedDiffers) {
            rows.append(row("name_history_filed", "Filed under",
                org.calacademy.antweb.Taxon.makeDescriptionLink(filedTaxonName)
                + " (uploaded as " + org.calacademy.antweb.Taxon.makeDescriptionLink(uploadedTaxonName) + ")"
                + filedReason(filedTaxonName, uploadedTaxonName, events, reasons, day)));
        }

        // Held for the curator (latest run), while it still applies.
        if (events != null) {
            for (Event e : events) {
                if (!e.shared() || e.oldName == null || !e.oldName.equals(filedTaxonName)) continue;
                rows.append(row("name_history_shared", SharedNameReport.HEADING,
                    SharedNameReport.line(e.oldName, e.uploadedName, shared, e.reason)
                    + "<br>" + Html.esc(SharedNameReport.EXPLANATION)));
                break;
            }
        }

        // The nightly moves, newest first.
        if (events != null) {
            for (Event e : events) {
                if (!e.changed() || e.oldName == null || e.newName == null) continue;
                StringBuilder v = new StringBuilder(NameUpdatesAction.nameLine(e.oldName, e.newName));
                if (e.reason != null && !e.reason.isEmpty()) {
                    v.append(UploadNameReasons.reasonPart(e.reason, e.antcatId, e.uploadedName, e.oldName, e.newName));
                } else if (e.antcatStatus != null && !e.antcatStatus.isEmpty()) {
                    v.append(" (AntCat: ").append(Html.esc(e.antcatStatus)).append(")");
                }
                v.append(". <i>").append(NIGHTLY).append("</i>");
                rows.append(row("name_history_update", "Name update " + (e.runDate == null ? "" : day.format(e.runDate)), v.toString()));
            }
        }

        // Previous determinations (TaxonWorks), as text.
        if (previousDeterminations != null && !previousDeterminations.isEmpty()) {
            StringBuilder v = new StringBuilder();
            for (int i = 0; i < previousDeterminations.size(); i++) {
                if (i > 0) v.append("<br>");
                v.append(Html.esc(previousDeterminations.get(i)));
            }
            rows.append(row("previous_determinations", "Previous determination", v.toString()));
        }

        if (rows.length() == 0) return "";
        return "<div class=\"clear\"></div>\n<div class=\"name_history\" id=\"name_history\">\n<h3>" + HEADING + ": </h3>\n"
            + rows + "</div>\n";
    }

    /**
     * Why the specimen is filed under another name than the uploaded one: when a nightly move listed in
     * the section took it there, a pointer to that line; otherwise the upload report's reason.
     */
    static String filedReason(String filed, String uploaded, List<Event> events, UploadNameReasons reasons, SimpleDateFormat day) {
        if (events != null) {
            for (Event e : events) {
                if (e.changed() && filed.equals(e.newName) && uploaded.equals(e.uploadedName)) {
                    return ": see the name update of " + (e.runDate == null ? "below" : day.format(e.runDate) + " below");
                }
            }
        }
        return reasons == null ? "" : reasons.reasonHtml(uploaded, filed);
    }

    /** One label/value row in the specimen page's style. value is HTML already escaped. */
    static String row(String cssClass, String label, String valueHtml) {
        return "<ul class=\"" + cssClass + "\">\n<li><b>" + Html.esc(label) + ": </b></li>\n<li>" + valueHtml + "</li>\n</ul>\n";
    }
}
