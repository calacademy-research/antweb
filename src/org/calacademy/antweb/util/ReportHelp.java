package org.calacademy.antweb.util;

/**
 * The plain-language header of an admin report page (2026-10-19): what the page shows, why it
 * matters, and what to do with a hit. The texts are fixed (no user input), so they are not escaped.
 * Tested in tests/e2e/test_reports_plain_language.py (the "report-help" box on each page).
 */
public final class ReportHelp {

    private ReportHelp() {}

    public static String box(String what, String why, String todo) {
        return "<div class=\"report-help\" style=\"border:1px solid #ccc;background:#f7f7f7;padding:8px 12px;margin:10px 0;max-width:900px;font-weight:normal\">"
            + "<p><b>What this shows:</b> " + what + "</p>"
            + "<p><b>Why it matters:</b> " + why + "</p>"
            + "<p><b>What to do with a hit:</b> " + todo + "</p>"
            + "</div>";
    }

    public static final String INTEGRITY = box(
        "Automatic checks of AntWeb's names, species lists and specimen data. Each check normally finds nothing; "
            + "a check that finds something lists the rows below its explanation.",
        "A hit means a page can show a wrong name, a wrong count or a broken link, or that a name in specimen data "
            + "does not fit the AntCat catalog.",
        "Read the check's explanation. Most hits are fixed at the source: the identification in TaxonWorks (CAS) or in "
            + "the curator's upload file, or the name in AntCat. The next upload or nightly run then clears the hit. "
            + "A hit that stays after the source is fixed is a problem for the AntWeb developers.");

    public static final String PARTS = box(
        "Specimens whose code was split into parts (for example casent0001234-d01 and "
            + "casent0001234-d02: separate individuals recorded under one specimen code) where the parts carry different taxon names. Each base "
            + "specimen is listed once per group, with each part, its name, its determination and the group that uploaded it. "
            + "Use the group filter to get the list for one institution; each curator can only fix the specimens their group uploaded.",
        "Parts of one specimen normally belong to one species. Different names usually mean a data entry mistake, "
            + "and the specimen then counts toward two species on AntWeb.",
        "Check the specimens (or their images) and correct the identification in TaxonWorks (CAS) or in the curator's "
            + "upload file; the change shows after the next upload. If the parts really are different species (a mixed "
            + "series), nothing needs to change.");

    public static final String HOMONYMS = box(
        "The homonyms AntWeb holds: names spelled the same as another ant name but by a different author and year. "
            + "Each line opens that homonym's own page.",
        "A specimen identified with a homonym's name is filed under the wrong taxon unless the author and year are known. "
            + "The current valid name (bold where it differs) is the name the species is known by now.",
        "Nothing, if the specimens of that name belong to the senior (valid) name. If specimens were filed under the "
            + "homonym by mistake, change their identification to the current valid name in TaxonWorks (CAS) or in "
            + "the curator's upload file.");

    public static final String WORLDANTS_UPLOADS = box(
        "The last 60 imports of AntCat names into AntWeb (the nightly run at 05:00 Pacific, or Import AntCat names now), "
            + "newest first, each with its log file.",
        "AntWeb takes its taxon names from AntCat. If an import fails or finds no changes for days, AntWeb's names "
            + "fall behind AntCat.",
        "If the newest import is older than a day or its message reports a failure, open its log file. If the AntCat "
            + "export was not ready, run Import AntCat names now from the Curate page later in the morning.");
}
