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
 *   2. UTF-8 read as Latin-1 or Windows-1252: A-tilde or A-circumflex followed by a character of U+0080 to U+00BF;
 *   3. UTF-8 read as MacRoman: the square-root sign followed by a symbol or letter;
 *   4. MacRoman read as Latin-1: an upper-case I-grave, U-circumflex, E-grave, O-grave or U-grave straight after a
 *      lower-case letter; a double dagger or a low double quote after any letter; a middle dot after a lower-case
 *      letter that is not the Catalan l-dot-l.
 * A name written in capitals, a real middle dot between two l and ordinary accented text do not count. No
 * database, no state.
 */
public final class GarbledText {

    private GarbledText() {}

    private static final Pattern GARBLED = Pattern.compile(
        "\uFFFD"
        + "|[\u00C3\u00C2][\u0080-\u00BF]"
        + "|\u221A[^\\s\\d\\p{Punct}]"
        + "|\\p{Ll}[\u00CC\u00DB\u00C8\u00D2\u00D9]"
        + "|\\p{L}[\u2021\u201E]"
        + "|(?!l\u00B7l)\\p{Ll}\u00B7");

    public static boolean is(String text) {
        if (text == null || text.isEmpty()) return false;
        // Plain ASCII, nearly every cell of a file: no pattern needed (a CAS file has about 13 million cells).
        boolean ascii = true;
        for (int i = 0; i < text.length() && ascii; i++) ascii = text.charAt(i) < 0x80;
        return !ascii && GARBLED.matcher(text).find();
    }
}
