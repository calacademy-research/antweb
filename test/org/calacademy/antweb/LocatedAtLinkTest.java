package test.org.calacademy.antweb;

import junit.framework.TestCase;

import org.calacademy.antweb.Specimen;
import org.calacademy.antweb.util.Html;

/**
 * "Located at" on the specimen page (Specimen.locatedAtLink, 2026-11-12): the field is free text and is shown as the
 * curator's file wrote it; a museum code in it adds the museum's link and no longer replaces the text (Jack Longino,
 * 2026-11-12: "can't find at JTLC" showed as the museum's name).
 */
public class LocatedAtLinkTest extends TestCase {

    private static final String LINK = "<a href='/museum.do?code=JTLC'>John T. Longino Collection</a>";

    public void testExactlyAMuseumCodeShowsTheMuseum() {
        assertEquals(LINK, Specimen.locatedAtLink("JTLC", "JTLC", LINK));
        assertEquals(LINK, Specimen.locatedAtLink(" jtlc ", "JTLC", LINK));
    }

    public void testANoteThatContainsACodeIsShownAsWrittenWithTheMuseumsLink() {
        String html = Specimen.locatedAtLink("can't find at JTLC", "JTLC", LINK);
        assertEquals(Html.esc("can't find at JTLC") + " (" + LINK + ")", html);
        assertTrue(html, html.contains("find at JTLC") && html.endsWith("(" + LINK + ")"));
        assertEquals("MCZC, on loan (" + LINK + ")", Specimen.locatedAtLink("MCZC, on loan", "MCZC", LINK));
    }

    public void testTheTextIsEscaped() {
        String html = Specimen.locatedAtLink("JTLC <b>x</b> & y", "JTLC", LINK);
        assertTrue(html, html.startsWith("JTLC &lt;b&gt;x&lt;/b&gt; &amp; y ("));
    }

    public void testNoTextOrNoMuseumShowsTheTextAlone() {
        assertEquals(Html.esc(null), Specimen.locatedAtLink(null, "JTLC", LINK));
        assertEquals("private collection", Specimen.locatedAtLink("private collection", null, null));
    }
}
