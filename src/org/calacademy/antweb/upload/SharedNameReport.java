package org.calacademy.antweb.upload;

import java.util.List;

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

    /**
     * The candidates when the name the specimen is filed under, or else the name it was uploaded as,
     * belongs to two or more AntCat taxa; otherwise null. Taxon names (ponerinaepachycondyla sauteri).
     */
    public static List<String> candidates(String filedTaxonName, String uploadedTaxonName, SharedNames shared) {
        if (shared == null) return null;
        List<String> c = filedTaxonName == null ? null : shared.candidatesOf(Taxon.getPrettyTaxonName(filedTaxonName));
        if (c == null && uploadedTaxonName != null) c = shared.candidatesOf(Taxon.getPrettyTaxonName(uploadedTaxonName));
        return c != null && c.size() > 1 ? c : null;
    }

    /**
     * For the specimen upload: the candidates when the name rule would move the uploaded name to another
     * name (as the nightly step would) and the name is shared; the upload then keeps it. Otherwise null.
     */
    public static List<String> heldAtUpload(String uploadedTaxonName, CurrentValidNameRule.Result rule, SharedNames shared) {
        if (rule == null || !rule.replacesName()) return null;
        return candidates(uploadedTaxonName, uploadedTaxonName, shared);
    }

    /**
     * The upload report's line when the upload keeps the uploaded name for the curator (heldAtUpload),
     * otherwise null. The text is line(), as on nameUpdates.do.
     */
    public static String uploadLine(String uploadedTaxonName, CurrentValidNameRule.Result rule, SharedNames shared) {
        if (heldAtUpload(uploadedTaxonName, rule, shared) == null) return null;
        return line(uploadedTaxonName, uploadedTaxonName, shared, null);
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
