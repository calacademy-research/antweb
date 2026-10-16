package test.org.calacademy.antweb.security;

import junit.framework.TestCase;
import org.calacademy.antweb.util.HtmlSanitizer;

/** Curator page text (description.do edits) is reduced to an allowlist of tags and attributes. */
public class HtmlSanitizerTest extends TestCase {

    private static String s(String html) {
        return HtmlSanitizer.sanitize(html);
    }

    public void testKeepsAllowedMarkup() {
        assertEquals("<p>Hello <b>world</b> &nbsp; &amp; x</p>", s("<p>Hello <b>world</b> &nbsp; &amp; x</p>"));
        assertEquals("<a href=\"/description.do?genus=x\">r</a>", s("<a href=\"/description.do?genus=x\">r</a>"));
        assertEquals("<img src=\"https://www.antweb.org/a.jpg\" class=\"taxon_page_img\" width=\"200\">",
            s("<img src=\"https://www.antweb.org/a.jpg\" class=\"taxon_page_img\" width=\"200\">"));
        assertEquals("<table border=\"1\"><tr><td colspan=\"2\">c</td></tr></table>",
            s("<table border=1><tr><td colspan=2 onclick=x>c</td></tr></table>"));
    }

    public void testDropsScriptAndHandlers() {
        assertEquals("ok", s("<script>alert(1)</script>ok"));
        assertEquals("oky", s("<SCRIPT >alert(1)</script >ok<style>x</style>y"));
        assertEquals("<img src=\"x\">", s("<img src=x onerror=alert(1)>"));
        assertEquals("after", s("<svg><script>alert(1)</script></svg>after"));
        assertEquals("after", s("<form action=x><input name=a>t</form>after"));
        assertEquals("vid", s("<iframe src=\"https://www.youtube.com/embed/abc\"></iframe>vid"));
        assertEquals("<p>x</p>", s("<p/onmouseover=alert(1)>x"));
    }

    public void testUrls() {
        assertEquals("<a>x</a>", s("<a href=\"jav&#x61;script:alert(1)\">x</a>"));
        assertEquals("<a>x</a>", s("<a href=\"javascript&colon;alert(1)\">x</a>"));
        assertEquals("<a>d</a>", s("<a href=\"data:text/html,x\">d</a>"));
        assertEquals("<img>", s("<img src=\"&#0000106&#0000097vascript:alert(1)\">"));
        assertEquals("<a href=\"mailto:a@b.org\">m</a>", s("<a href=\"mailto:a@b.org\">m</a>"));
        assertEquals("<img alt=\"a\">", s("<img src=\"mailto:a@b.org\" alt=\"a\">"));
    }

    public void testStyleAndText() {
        assertEquals("<span style=\"color:red\">r</span><span>u</span>",
            s("<span style=\"color:red\">r</span><span style=\"background:url(javascript:x)\">u</span>"));
        assertEquals("x &lt;3 and 3 &gt; 2 &amp;unknown", s("x <3 and 3 > 2 &unknown"));
        assertEquals("<img src=\"x\" alt=\"&quot;&gt;&lt;script&gt;\">", s("<img src=x alt=\"&quot;><script>\">"));
        assertEquals("<b><i>unclosed</i></b>", s("<b><i>unclosed"));
        assertEquals("stray<p>x</p>", s("</b>stray</div><p>x</p>"));
    }

    public void testVideoEmbeds() {
        String yt = HtmlSanitizer.sanitize("<iframe src=\"https://www.youtube.com/embed/abc123\" onload=x></iframe>", true);
        assertTrue(yt.startsWith("<iframe src=\"https://www.youtube.com/embed/abc123\""));
        assertFalse(yt.contains("onload"));
        assertEquals("", HtmlSanitizer.sanitize("<iframe src=\"https://evil.example/embed/abc\"></iframe>", true));
    }
}
