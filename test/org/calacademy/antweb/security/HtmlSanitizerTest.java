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

    public void testOldTargetKeptWithRel() {
        // Old pages use target="new": it is kept (with rel), not dropped while rel is still added.
        assertEquals("<a href=\"http://x.org/\" target=\"new\" rel=\"noopener noreferrer\">A</a>",
            s("<a href=\"http://x.org/\" target=\"new\">A</a>"));
        assertEquals("<a href=\"http://x.org/\">A</a>", s("<a href=\"http://x.org/\" target=\"a b\">A</a>"));
    }

    private static String unsafe(String html) {
        return HtmlSanitizer.findUnsafe(html).toString();
    }

    public void testFindUnsafeIgnoresCosmetic() {
        // Typical CKEditor 3 / pasted page text: cosmetic changes only, nothing unsafe.
        String ck = "<p>Workers &nbsp;small, <em>Camponotus</em> &eacute; <strong>x</strong><br />line<p>unclosed\r\n"
            + "<img alt=\"\" class=\"taxon_page_img\" src=\"/images/x.jpg\" style=\"width: 300px; height: 200px;\" />"
            + "<a href=\"http://www.alexanderwild.com/\" target=\"new\">Alex Wild</a> <a href=\"mailto:a@b.org\">m</a>"
            + "<font face=\"Arial\" size=\"2\">f</font><span style=\"font-family: &quot;Times New Roman&quot;;\">s</span>"
            + "<a id=\"z\" rel=\"nofollow\" href=\"ftp://x.org/f\">ftp</a><!-- comment --><o:p></o:p> size < 5 mm & more"
            + "<table border=1><tr><td>c</td></tr></table>";
        assertEquals("[]", unsafe(ck));
        assertTrue(HtmlSanitizer.findUnsafe(null).isEmpty());
        assertTrue(HtmlSanitizer.findUnsafe("<iframe src=\"https://www.youtube.com/embed/abc\" width=\"560\"></iframe>", true).isEmpty());
    }

    public void testFindUnsafeReports() {
        assertEquals("[<script> element @2]", unsafe("x <script>alert(1)</script>"));
        assertEquals("[onclick attribute on <b> @0]", unsafe("<b onclick=\"x()\">c</b>"));
        assertEquals("[onerror attribute on <img> @0]", unsafe("<img src=x onerror=alert(1)>"));
        assertEquals("[javascript: URL in href of <a> @0]", unsafe("<a href=\" jav&#x61;script:alert(1)\">j</a>"));
        assertEquals("[data: URL in src of <img> @0]", unsafe("<img src=\"data:image/png;base64,AAA\">"));
        assertEquals("[style with url( on <span> @0]", unsafe("<span style=\"background: URL (x)\">u</span>"));
        assertEquals("[style with expression on <p> @0]", unsafe("<p style=\"width:expression(alert(1))\">u</p>"));
        assertEquals("[<iframe> element @0]", unsafe("<iframe src=\"https://www.youtube.com/embed/abc\"></iframe>"));
        assertEquals("[<meta> tag @0]", unsafe("<meta http-equiv=refresh content=\"0;url=http://x\">"));
        assertEquals("[<object> element @0]", unsafe("<object data=\"x.swf\"></object>"));
        assertEquals("[<svg> element @0]", unsafe("<svg onload=alert(1)>"));
        assertFalse(HtmlSanitizer.findUnsafe("<iframe src=\"https://evil.example/embed/abc\"></iframe>", true).isEmpty());
    }
}
