package test.org.calacademy.antweb;

import junit.framework.TestCase;

import org.calacademy.antweb.Specimen;

/**
 * "Located at" on the specimen page and in the specimen lists (Specimen.locatedAtLink, 2026-11-12): the field is free
 * text and is shown as the curator's file wrote it; a museum code in it is linked and no longer replaces the text
 * (Jack Longino, 2026-11-12: "can't find at JTLC" showed as the museum's name).
 */
public class LocatedAtLinkTest extends TestCase {

    private static final String HREF = "/museum.do?code=JTLC";
    private static final String NAME = "JTLC, Longino";
    private static final String MUSEUM = "<a href='" + HREF + "'>JTLC, Longino</a>";
    private static final String CODE = "<a href='" + HREF + "'>JTLC</a>";

    private static String show(String locatedAt) {
        return Specimen.locatedAtLink(locatedAt, "JTLC", NAME, HREF);
    }

    public void testExactlyTheMuseumsCodeOrNameShowsTheMuseumAsBefore() {
        assertEquals(MUSEUM, show("JTLC"));
        assertEquals(MUSEUM, show(" jtlc "));
        assertEquals(MUSEUM, show("JTLC, Longino"));
        assertEquals(MUSEUM, show("jtlc, longino "));
        assertEquals("<a href='" + HREF + "'>JTLC</a>", Specimen.locatedAtLink("JTLC", "JTLC", null, HREF));
    }

    public void testANoteThatContainsTheCodeIsShownAsWritten() {
        String html = show("can't find at JTLC");
        assertTrue(html, html.endsWith(" find at " + CODE));
        assertTrue(html, html.startsWith("can") && !html.contains("Longino"));
        assertEquals(CODE + ", on loan to MCZ", show("JTLC, on loan to MCZ"));
        assertEquals("lost? was " + CODE + " until 2020", show("lost? was JTLC until 2020"));
    }

    public void testTheTextIsEscaped() {
        assertEquals("&lt;b&gt;x&lt;/b&gt; " + CODE + " &amp; y", show("<b>x</b> JTLC & y"));
    }

    public void testNoTextOrNoMuseumShowsTheTextAlone() {
        assertEquals(Specimen.locatedAtLink(null, null, null, null), show(null));
        assertEquals("private collection", Specimen.locatedAtLink("private collection", null, null, null));
        assertEquals("private collection", Specimen.locatedAtLink("private collection", "JTLC", NAME, HREF));
    }
}
