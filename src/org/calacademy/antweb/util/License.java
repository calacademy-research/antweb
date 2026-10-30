package org.calacademy.antweb.util;

/**
 * The one licence statement for AntWeb images and data, used by every page that states a licence.
 *
 * Decision of Brian Fisher, 2026-10-29: images and data on AntWeb are licensed CC BY 4.0, everywhere and for all
 * CAS images. Pages include web/common/licenseStatement.jsp (which prints {@link #html()}) or use the constants
 * below; no page writes its own licence text, so the wording cannot drift. tests/unit/test_license_statement.py
 * fails if an older Creative Commons version or variant (ShareAlike, NonCommercial, version 3.0 and so on) appears
 * in src/ or web/.
 *
 * Per-image photographer and copyright holder credit is unchanged. Photos and drawings that belong to other persons
 * or organizations keep their own copyright statement.
 */
public final class License {

    /** Short name. */
    public static final String NAME = "CC BY 4.0";

    /** Full name of the licence. */
    public static final String FULL_NAME = "Creative Commons Attribution 4.0 International";

    /** The licence deed. */
    public static final String URL = "https://creativecommons.org/licenses/by/4.0/";

    /** The site statement, plain text. */
    public static final String STATEMENT = "Images and data on AntWeb are licensed under " + NAME + " (" + FULL_NAME
            + "). Cite AntWeb and the photographer or collection.";

    /** One line for an image upload record, image metadata or a citation box: name, full name and link. */
    public static final String LINE = NAME + " (" + FULL_NAME + "), " + URL;

    private License() {
    }

    /** The statement with the licence name linked to the deed (rel="license" for machine readers). */
    public static String html() {
        return "Images and data on AntWeb are licensed under <a href=\"" + URL + "\" rel=\"license\" target=\"_blank\">"
                + NAME + " (" + FULL_NAME + ")</a>. Cite AntWeb and the photographer or collection.";
    }
}
