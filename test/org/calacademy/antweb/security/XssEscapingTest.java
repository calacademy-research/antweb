package test.org.calacademy.antweb.security;

import junit.framework.TestCase;
import org.calacademy.antweb.util.Html;
import org.calacademy.antweb.util.HttpUtil;

/** HTML escaping and login/logout redirect target checks added 2026-10-15. */
public class XssEscapingTest extends TestCase {

    public void testEsc() {
        assertEquals("", Html.esc(null));
        assertEquals("plain text", Html.esc("plain text"));
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", Html.esc("<script>alert(1)</script>"));
        assertEquals("a &amp; b", Html.esc("a & b"));
        assertEquals("&quot;x&quot; &#39;y&#39;", Html.esc("\"x\" 'y'"));
        assertEquals("42", Html.esc(42));
        assertEquals("&quot; onmouseover=&quot;alert(1)", Html.escAttr("\" onmouseover=\"alert(1)"));
        assertEquals("", Html.escAttr(null));
    }

    public void testJsAndUrl() {
        assertEquals("", Html.js(null));
        assertEquals("a\\u0027b\\u003c\\u002fscript\\u003e", Html.js("a'b</script>"));
        assertEquals("a\\\\b\\n", Html.js("a\\b\n"));
        assertEquals("", Html.url(null));
        assertEquals("a+b%27%3C%26", Html.url("a b'<&"));
    }

    public void testRedirectTargets() {
        String app = "https://www.antweb.org";
        assertTrue(HttpUtil.isSafeRedirectTarget("/description.do?genus=camponotus", app));
        assertTrue(HttpUtil.isSafeRedirectTarget("/", app));
        assertTrue(HttpUtil.isSafeRedirectTarget("https://www.antweb.org/specimen.do?code=casent0000001", app));
        assertTrue(HttpUtil.isSafeRedirectTarget("https://www.antweb.org", app));
        assertTrue(HttpUtil.isSafeRedirectTarget("https://staging.antweb.org/index.do", null));
        assertTrue(HttpUtil.isSafeRedirectTarget("http://localhost/antweb/index.do", "http://localhost/antweb"));

        assertFalse(HttpUtil.isSafeRedirectTarget(null, app));
        assertFalse(HttpUtil.isSafeRedirectTarget("", app));
        assertFalse(HttpUtil.isSafeRedirectTarget("//evil.example/x", app));
        assertFalse(HttpUtil.isSafeRedirectTarget("/\\evil.example", app));
        assertFalse(HttpUtil.isSafeRedirectTarget("https://evil.example/", app));
        assertFalse(HttpUtil.isSafeRedirectTarget("https://www.antweb.org.evil.example/", app));
        assertFalse(HttpUtil.isSafeRedirectTarget("https://www.antweb.org@evil.example/", app));
        assertFalse(HttpUtil.isSafeRedirectTarget("javascript:alert(1)", app));
        assertFalse(HttpUtil.isSafeRedirectTarget("evil.example", app));
        assertFalse(HttpUtil.isSafeRedirectTarget("/x\r\nSet-Cookie: a=b", app));
        assertFalse(HttpUtil.isSafeRedirectTarget("http://www.antweb.org/x", null));
    }
}
