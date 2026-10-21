package org.calacademy.antweb.upload;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.calacademy.antweb.Status;

import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.util.Html;

/**
 * Names that belong to two or more AntCat taxa (homonyms and the like, SharedNames): the one place that
 * decides when such a name is held for the curator, and writes the line about it. Brian Fisher,
 * 2026-10-20: the specimen upload report and the Name updates page (nameUpdates.do) share this code, so
 * that a fix in one fixes the other.
 *
 *   the specimen upload (SpecimenUploadProcess.setStatusAndCurrentValidName) keeps a specimen under the
 *   uploaded name instead of moving it to one taxon's current valid name, and lists it in the report
 *   under "Needs the curator's decision" (MessageMgr.sharedNameKept);
 *   the nightly name update (SpecimenNameUpdate) does not move it and lists it on nameUpdates.do under
 *   "Needs the curator's decision" (NameUpdatesAction.sharedHtml).
 *
 * Both ask candidates() (the same detection, from SharedNames.load(): the AntCat export the nightly
 * import loaded) and write line() (the same words and the same links: the name's AntWeb page, and each
 * AntCat taxon of the name with author, year and status, linked to its AntCat page). Only a move is held
 * back: a specimen that would stay under its name anyway is not listed.
 *
 * It replaces the upload report's old "Taxon names recognized as Homonyms" section, which read only the
 * homonym table and was asked only for names without a taxon row; every name in the homonym table has
 * one (the import puts a name there only when the taxon table already has it, and deletes the others),
 * so it never listed a shared name. What it was meant to say for a name AntCat has only as a junior
 * homonym is now homonymOnlyLine(), from the export.
 */
public final class SharedNameReport {

    private SharedNameReport() {}

    /** Section heading, the same in the upload report and on the page. */
    public static final String HEADING = "Needs the curator's decision";

    /** The sentence under the heading, the same in both. */
    public static final String EXPLANATION = "Not moved: the name belongs to two or more AntCat taxa, so the name alone does not tell "
        + "which one the specimens are. They stay under the name they have. Please confirm the identification and correct it in "
        + "TaxonWorks (CAS) or in your upload file.";

    /** Upload report heading (MessageMgr.sharedNameKept). */
    public static final String UPLOAD_HEADING = HEADING + ". Name shared by two or more AntCat taxa: kept under the uploaded name";

    /** Upload report heading (MessageMgr.taxonNamesAreHomonyms). */
    public static final String HOMONYM_ONLY_HEADING = "Names AntCat has only as a junior homonym (or another name AntWeb does not file specimens under): kept as uploaded";

    /** insert_method of the placeholder taxa the upload creates for a valid name that is only a homonym. */
    public static final String PLACEHOLDER_INSERT_METHOD = "homononymMirroringTaxon";

    /** The placeholder taxa (read once per run or upload; the nightly step and the upload ask the same). */
    public static Set<String> placeholders(Connection connection) throws SQLException {
        Set<String> names = new HashSet<>();
        try (Statement stmt = connection.createStatement();
             ResultSet r = stmt.executeQuery("select taxon_name from taxon where insert_method = '" + PLACEHOLDER_INSERT_METHOD + "'")) {
            while (r.next()) names.add(r.getString(1));
        }
        return names;
    }

    /** A specimen name that involves a name of two or more AntCat taxa: which name, and its candidates (null when unknown). */
    public static final class Held {
        /** Taxon name of the shared name (the filed, uploaded or current valid name). */
        public final String taxonName;
        /** The valid names it can stand for, or null when only the homonym table or a placeholder says it is shared. */
        public final List<String> candidates;
        Held(String taxonName, List<String> candidates) {
            this.taxonName = taxonName;
            this.candidates = candidates;
        }
        public String toString() { return taxonName + " " + candidates; }
    }

    /**
     * The one test, for the upload and the nightly step (Brian Fisher, 2026-10-20: "homonym" and "name
     * shared by two or more AntCat taxa" are one category): does a specimen filed under filedTaxonName and
     * uploaded as uploadedTaxonName involve a name that belongs to two or more AntCat taxa? Yes when
     *   the filed or the uploaded name is shared (SharedNames);
     *   the uploaded name is in the homonym table (the import puts a second taxon of a name there);
     *   AntCat's status of the uploaded name is homonym;
     *   the rule's current valid name is only a homonym (TARGET_IS_HOMONYM), or a homonym placeholder taxon;
     *   the specimen is filed under a homonym placeholder taxon.
     * Returns null otherwise. rule may be null; isPlaceholder may be null (no placeholders).
     */
    public static Held involved(String filedTaxonName, String uploadedTaxonName, CurrentValidNameRule.Result rule,
                                boolean uploadedInHomonymTable, Predicate<String> isPlaceholder, SharedNames shared) {
        String target = rule == null ? null : rule.validTaxonName != null ? rule.validTaxonName : rule.replacesName() ? rule.taxonName : null;
        // Not the current valid name: AntCat names one taxon there, and the import keeps the valid one of a
        // name first (Formica picea Nylander, valid, before two obsolete combinations of the same name).
        for (String n : new String[] {filedTaxonName, uploadedTaxonName}) {
            List<String> c = sharedCandidates(n, shared);
            if (c != null) return new Held(n, c);
        }
        if (uploadedInHomonymTable) return new Held(uploadedTaxonName, null);
        if (rule != null && Status.HOMONYM.equals(rule.uploadedStatus)) return new Held(uploadedTaxonName, null);
        if (rule != null && rule.kind == CurrentValidNameRule.Kind.TARGET_IS_HOMONYM) return new Held(rule.taxonName, null);
        if (isPlaceholder != null) {
            if (target != null && isPlaceholder.test(target)) return new Held(target, null);
            if (filedTaxonName != null && isPlaceholder.test(filedTaxonName)) return new Held(filedTaxonName, null);
        }
        return null;
    }

    private static List<String> sharedCandidates(String taxonName, SharedNames shared) {
        if (shared == null || taxonName == null) return null;
        List<String> c = shared.candidatesOf(Taxon.getPrettyTaxonName(taxonName));
        return c != null && c.size() > 1 ? c : null;
    }

    /**
     * The candidates when the name the specimen is filed under, or else the name it was uploaded as,
     * belongs to two or more AntCat taxa (SharedNames); otherwise null. Taxon names (ponerinaepachycondyla sauteri).
     */
    public static List<String> candidates(String filedTaxonName, String uploadedTaxonName, SharedNames shared) {
        List<String> c = sharedCandidates(filedTaxonName, shared);
        return c != null ? c : sharedCandidates(uploadedTaxonName, shared);
    }

    /**
     * The reason recorded and shown for a held name: "name is shared by two taxa; please confirm the
     * identification: Cryptopone sauteri or Ectomomyrmex sauteri"; when the shared name is the current
     * valid name rather than the specimen's own, it is named; without known candidates, no list.
     */
    public static String reason(Held held, String filedTaxonName, String uploadedTaxonName) {
        boolean own = held.taxonName == null || held.taxonName.equals(filedTaxonName) || held.taxonName.equals(uploadedTaxonName);
        String subject = own ? "name" : "its current valid name, " + Taxon.getPrettyTaxonName(held.taxonName) + ",";
        if (held.candidates == null) return subject + " is shared by two or more AntCat taxa; please confirm the identification";
        String r = SharedNames.reason(held.candidates);   // "name is shared by two taxa; ..."
        return own ? r : subject + r.substring("name".length());
    }

    /**
     * For the specimen upload: what involved() says, when the name rule would move the uploaded name to
     * another name (the nightly step holds moves only, too); the upload then keeps it. Otherwise null.
     */
    public static Held heldAtUpload(String uploadedTaxonName, CurrentValidNameRule.Result rule, boolean uploadedInHomonymTable,
                                    Predicate<String> isPlaceholder, SharedNames shared) {
        if (rule == null || !rule.replacesName()) return null;
        return involved(uploadedTaxonName, uploadedTaxonName, rule, uploadedInHomonymTable, isPlaceholder, shared);
    }

    /** As heldAtUpload, with only the shared names (no homonym table, no placeholders). */
    public static List<String> heldAtUpload(String uploadedTaxonName, CurrentValidNameRule.Result rule, SharedNames shared) {
        Held h = heldAtUpload(uploadedTaxonName, rule, false, null, shared);
        return h == null ? null : h.candidates;
    }

    /**
     * The upload report's line when the upload keeps the uploaded name for the curator (heldAtUpload),
     * otherwise null. The text is line(), as on nameUpdates.do.
     */
    public static String uploadLine(String uploadedTaxonName, CurrentValidNameRule.Result rule, boolean uploadedInHomonymTable,
                                    Predicate<String> isPlaceholder, SharedNames shared) {
        Held h = heldAtUpload(uploadedTaxonName, rule, uploadedInHomonymTable, isPlaceholder, shared);
        if (h == null) return null;
        return line(uploadedTaxonName, uploadedTaxonName, shared, reason(h, uploadedTaxonName, uploadedTaxonName));
    }

    public static String uploadLine(String uploadedTaxonName, CurrentValidNameRule.Result rule, SharedNames shared) {
        return uploadLine(uploadedTaxonName, rule, false, null, shared);
    }

    /**
     * The line about one shared name, the same in the upload report and on nameUpdates.do (the page adds
     * the specimen codes after it):
     *
     *   Pachycondyla sauteri: name is shared by two taxa; please confirm the identification: Cryptopone sauteri
     *   or Ectomomyrmex sauteri. AntCat: Pachycondyla sauteri Wheeler, 1906, obsolete combination, now Cryptopone
     *   sauteri; Pachycondyla sauteri Forel, 1912, obsolete combination, now Ectomomyrmex sauteri
     *
     * The first name links to its AntWeb page, each AntCat taxon to its AntCat page. uploadedTaxonName is
     * shown ("uploaded as") when it differs from the name the specimens are filed under. storedReason (the
     * page: the reason the run recorded) is used when the name is no longer in shared.
     */
    public static String line(String filedTaxonName, String uploadedTaxonName, SharedNames shared, String storedReason) {
        StringBuilder b = new StringBuilder(Taxon.makeDescriptionLink(filedTaxonName));
        if (uploadedTaxonName != null && !uploadedTaxonName.isEmpty() && !uploadedTaxonName.equals(filedTaxonName)) {
            b.append(" (uploaded as <i>").append(Html.esc(Taxon.getPrettyTaxonName(uploadedTaxonName))).append("</i>)");
        }
        List<String> c = candidates(filedTaxonName, uploadedTaxonName, shared);
        String reason = c != null ? SharedNames.reason(c) : storedReason;
        if (reason != null && !reason.isEmpty()) b.append(": <span class=\"name-update-reason\">").append(Html.esc(reason)).append("</span>");
        List<SharedNames.AntcatTaxon> taxa = null;
        if (shared != null && c != null) {
            taxa = shared.taxaOf(Taxon.getPrettyTaxonName(filedTaxonName));
            if (taxa == null && uploadedTaxonName != null) taxa = shared.taxaOf(Taxon.getPrettyTaxonName(uploadedTaxonName));
        }
        b.append(taxaHtml(taxa));
        return b.toString();
    }

    /**
     * The upload report line for a name AntCat has but AntWeb does not file specimens under (only a junior
     * homonym, an excluded status), or null when AntCat does not have the name:
     * "Gnamptogenys zzz. AntCat: Gnamptogenys zzz Smith, 1900, homonym".
     */
    public static String homonymOnlyLine(String taxonName, SharedNames shared) {
        if (shared == null || taxonName == null) return null;
        List<SharedNames.AntcatTaxon> taxa = shared.taxaOf(Taxon.getPrettyTaxonName(taxonName));
        if (taxa == null || taxa.isEmpty()) return null;
        return Taxon.makeDescriptionLink(taxonName) + taxaHtml(taxa);
    }

    /** ". AntCat: <a href=antcat page><i>Name</i> Author, year</a>, status, now <i>Valid name</i>; ...", or "". */
    static String taxaHtml(List<SharedNames.AntcatTaxon> taxa) {
        if (taxa == null || taxa.isEmpty()) return "";
        StringBuilder b = new StringBuilder(". <span class=\"shared-name-taxa\">AntCat: ");
        int i = 0;
        for (SharedNames.AntcatTaxon t : taxa) {
            if (i++ > 0) b.append("; ");
            String label = "<i>" + Html.esc(t.name) + "</i>" + (t.authorDate.isEmpty() ? "" : " " + Html.esc(t.authorDate));
            if (t.antcatId > 0) {
                b.append("<a href=\"https://www.antcat.org/catalog/").append(t.antcatId).append("\">").append(label).append("</a>");
            } else {
                b.append(label);
            }
            if (!t.status.isEmpty()) b.append(", ").append(Html.esc(t.status));
            if (t.validName != null && !t.validName.equals(t.name)) b.append(", now <i>").append(Html.esc(t.validName)).append("</i>");
        }
        return b.append("</span>").toString();
    }
}
