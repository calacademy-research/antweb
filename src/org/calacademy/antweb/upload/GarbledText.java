package org.calacademy.antweb.upload;

import java.util.regex.Pattern;

/**
 * Text whose accented letters were damaged before the upload (2026-11-25). The upload refuses a file that is not
 * UTF-8 (UploadFile.firstNonUtf8Line, 2026-10-28), but a file can be valid UTF-8 and still carry the damage: it was
 * opened or exported once with the wrong encoding and saved again. On 25 Nov 2026 AntWeb held 12,000 specimens whose
 * state was such a text (Liege with a square-root sign and a registered sign for its e-grave, 3,468 specimens; San
 * Luis Potosi ending in the replacement character; Panama ending in a middle dot). The upload report names them so
 * the curator corrects the source file. The samples are in test/.../GarbledTextTest.
 *
 * What counts:
 *   1. the replacement character U+FFFD (the letter was lost);
 *   2. UTF-8 read as Latin-1 or Windows-1252: A-tilde or A-circumflex followed by a character of U+0080 to U+00BF,
 *      or by one of the characters Windows-1252 puts at 0x80 to 0x9F (curly quotes, dashes, the per-mille sign, S-caron...);
 *   3. UTF-8 read as MacRoman: the square-root sign followed by a symbol or letter;
 *   4. MacRoman read as Latin-1: an upper-case I-grave, U-circumflex, E-grave, O-grave or U-grave straight after a
 *      lower-case letter; a double dagger or a low double quote after any letter; a middle dot after a lower-case
 *      letter that is not the Catalan l-dot-l.
 * A name written in capitals, a real middle dot between two l and ordinary accented text do not count. No
 * database, no state.
 */
public final class GarbledText {

    private GarbledText() {}

    /** A column of free text (lower case, as the upload reads the header): notes, habitat, method. Not the locality
     *  name or code: place names, where this damage is most likely. */
    public static boolean isFreeText(String column) {
        if (column == null) return false;
        return column.contains("notes") || column.contains("habitat") || column.contains("method")
            || column.contains("remarks") || column.contains("description");
    }

    private static final java.util.Set<String> NAME_COLUMNS = java.util.Set.of("country", "adm1", "adm2", "localityname",
        "subfamily", "tribe", "genus", "subgenus", "speciesgroup", "species", "subspecies");

    /** A place or taxon column, the only kind where the middle-dot rule applies: elsewhere (Collected By, notes) a
     *  middle dot after a letter can be a real separator ("Smith\u00B7Jones"; review of #420). */
    public static boolean isNameColumn(String column) {
        return column != null && NAME_COLUMNS.contains(column);
    }

    private static final Pattern GARBLED = Pattern.compile(
        "\uFFFD"
        + "|[\u00C3\u00C2][\u0080-\u00BF]"
        + "|[\u00C3\u00C2][\u0152\u0153\u0160\u0161\u0178\u017D\u017E\u0192\u02C6\u02DC\u2013\u2014\u2018-\u201E\u2020-\u2022\u2026\u2030\u2039\u203A\u20AC\u2122]"
        + "|\u221A[^\\s\\d\\p{Punct}]"
        + "|\\p{Ll}[\u00CC\u00DB\u00C8\u00D2\u00D9]"
        + "|\\p{L}[\u2021\u201E]");
    // The middle dot of rule 4: only in a name (a place, a taxon), never in free text, where it can be a real separator
    // ("leaf\u00B7litter"; review of #420).
    private static final Pattern MIDDLE_DOT = Pattern.compile("(?!l\u00B7l)\\p{Ll}\u00B7");

    public static boolean is(String text) {
        return is(text, false);
    }

    /** freeText: a column of notes or descriptions (FREE_TEXT), where the middle-dot rule does not apply. */
    public static boolean is(String text, boolean freeText) {
        if (text == null || text.isEmpty()) return false;
        // Plain ASCII, nearly every cell of a file: no pattern needed (a CAS file has about 13 million cells).
        boolean ascii = true;
        for (int i = 0; i < text.length() && ascii; i++) ascii = text.charAt(i) < 0x80;
        return !ascii && (GARBLED.matcher(text).find() || !freeText && MIDDLE_DOT.matcher(text).find());
    }
}
