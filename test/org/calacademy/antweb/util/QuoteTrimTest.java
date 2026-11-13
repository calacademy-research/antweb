package test.org.calacademy.antweb.util;

import junit.framework.TestCase;

import org.calacademy.antweb.Utility;

/**
 * Utility.customTrim, the quote removal of the specimen and species list uploads (SpecimenUploadParse.parseLine,
 * SpeciesListUpload): a quote mark is removed only when it wraps the whole value. TaxonWorks upload of 2026-11-13:
 * Şan'ā', Aḑ Ḑāli' (Yemen) and Wādī ash Shāţi' (Libya) lost their last apostrophe and were reported as adm1s AntWeb
 * does not know (11 specimens). No database.
 */
public class QuoteTrimTest extends TestCase {

    private static String trim(String s, String q) throws Exception {
        return Utility.customTrim(s, q);
    }

    public void testAPlaceNameThatEndsInAnApostropheKeepsIt() throws Exception {
        assertEquals("Şan'ā'", trim("Şan'ā'", "'"));
        assertEquals("Aḑ Ḑāli'", trim("Aḑ Ḑāli'", "'"));
        assertEquals("Wādī ash Shāţi'", trim("Wādī ash Shāţi'", "'"));
        assertEquals("Al Bayḑā'", trim("Al Bayḑā'", "'"));
    }

    public void testAnApostropheAtTheStartOnlyStays() throws Exception {
        assertEquals("'s-Hertogenbosch", trim("'s-Hertogenbosch", "'"));
    }

    public void testAWrappedValueIsUnwrapped() throws Exception {
        assertEquals("Madagascar", trim("'Madagascar'", "'"));
        assertEquals("Madagascar", trim("\"Madagascar\"", "\""));
        assertEquals("at most twice, as before", "Madagascar", trim("''Madagascar''", "'"));
        assertEquals("'Madagascar'", trim("'''Madagascar'''", "'"));
        assertEquals("Şan'ā'", trim("'Şan'ā''", "'"));
    }

    public void testADoubleQuoteOnOneSideStays() throws Exception {
        assertEquals("12\"", trim("12\"", "\""));
        assertEquals("\"Gully", trim("\"Gully", "\""));
    }

    public void testEmptyAndShortValues() throws Exception {
        assertEquals("", trim("", "'"));
        assertNull(trim(null, "'"));
        assertEquals("'", trim("'", "'"));
        assertEquals("", trim("''", "'"));
        assertEquals("plain", trim("plain", "'"));
    }
}
