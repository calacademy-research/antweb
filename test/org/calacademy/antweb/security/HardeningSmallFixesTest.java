package test.org.calacademy.antweb.security;

import junit.framework.TestCase;
import org.calacademy.antweb.PageAction;
import org.calacademy.antweb.data.AntWikiDataAction;
import org.calacademy.antweb.util.SpeciesListAction;

/**
 * Hardening and small fixes of 2026-10-19: the AntWiki date check accepts only real dates,
 * /page.do redirects only plain project names, /getSpeciesList.do accepts only plain names.
 * Runs in CI inside the antweb image (.github/workflows/tests.yaml).
 */
public class HardeningSmallFixesTest extends TestCase {

    public void testAntWikiDateAcceptsDates() {
        assertEquals("2026-10-01", AntWikiDataAction.validDate("2026-10-01"));
        assertEquals("2024-02-29", AntWikiDataAction.validDate(" 2024-02-29 "));
        assertEquals("01/10/2026", AntWikiDataAction.validDate("01/10/2026"));
        assertEquals("31.12.2025", AntWikiDataAction.validDate("31.12.2025"));
    }

    public void testAntWikiDateRefusesOtherText() {
        for (String s : new String[]{null, "", "2026-13-01", "2025-02-29", "2026-1-0'", "x' or '1'='1",
                "2026-10-01'; drop table lookup; --", "<a href=", "abcdefghij", "2026/10/01x"}) {
            assertNull(String.valueOf(s), AntWikiDataAction.validDate(s));
        }
    }

    public void testAntWikiDateAfterMarker() {
        String marker = "List of valid fossil species (names in use)</a> (tab-delimited text) (Date: ";
        assertEquals("2026-09-30", AntWikiDataAction.dateAfter("xx" + marker + "2026-09-30) more", marker, marker.length()));
        assertNull(AntWikiDataAction.dateAfter("xx" + marker + "'); delete", marker, marker.length()));
        assertNull(AntWikiDataAction.dateAfter("no marker here", marker, marker.length()));
        assertNull(AntWikiDataAction.dateAfter(marker + "2026", marker, marker.length()));
        assertNull(AntWikiDataAction.dateAfter(null, marker, marker.length()));
    }

    public void testPageRedirectPath() {
        assertEquals("/project.do?name=californiaants", PageAction.redirectPath("california"));
        assertEquals("/project.do?name=floridakeysants", PageAction.redirectPath("floridakeys"));
        for (String s : new String[]{null, "", "a b", "x&y=1", "../x", "http://evil.example", "x\r\nSet-Cookie:a",
                "<script>", "x%26y"}) {
            assertNull(String.valueOf(s), PageAction.redirectPath(s));
        }
    }

    public void testSpeciesListNames() {
        assertTrue(SpeciesListAction.isValidName("allantwebants"));
        assertTrue(SpeciesListAction.isValidName("madants"));
        assertFalse(SpeciesListAction.isValidName(null));
        assertFalse(SpeciesListAction.isValidName(""));
        assertFalse(SpeciesListAction.isValidName("../etc/passwd"));
        assertFalse(SpeciesListAction.isValidName("a'b"));
    }
}
