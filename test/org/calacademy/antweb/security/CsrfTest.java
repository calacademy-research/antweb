package test.org.calacademy.antweb.security;

import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;
import org.calacademy.antweb.util.Csrf;

/** CSRF tokens: random, URL-safe, compared in constant time; upload form actions carry one. */
public class CsrfTest extends TestCase {

    public void testTokenFormat() {
        String t = Csrf.newToken();
        assertEquals(43, t.length());  // 32 bytes, base64url without padding
        assertTrue(t.matches("[A-Za-z0-9_-]{43}"));
    }

    public void testTokensDiffer() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) assertTrue(seen.add(Csrf.newToken()));
    }

    public void testMatches() {
        String t = Csrf.newToken();
        assertTrue(Csrf.matches(t, new String(t)));
        assertFalse(Csrf.matches(t, Csrf.newToken()));
        assertFalse(Csrf.matches(t, t.substring(1)));
        assertFalse(Csrf.matches(t, t + "x"));
        assertFalse(Csrf.matches(t, null));
        assertFalse(Csrf.matches(null, t));
        assertFalse(Csrf.matches("", ""));
        assertFalse(Csrf.matches(null, null));
    }

    public void testQueryParamAndStrip() {
        assertEquals("abc", Csrf.queryParam("a=1&_csrf=abc&b=2"));
        assertNull(Csrf.queryParam("a=1&x_csrf=abc"));
        assertNull(Csrf.queryParam(null));
        assertEquals("upload.do", Csrf.stripParam("upload.do?_csrf=old"));
        assertEquals("description.do?genus=a&rank=genus", Csrf.stripParam("description.do?genus=a&_csrf=old&rank=genus"));
        assertEquals("x.do?id=1#top", Csrf.stripParam("x.do?id=1&_csrf=old#top"));
        assertEquals("x.do", Csrf.stripParam("x.do"));
    }

    public void testHostOf() {
        assertEquals("www.antweb.org", Csrf.hostOf("https://www.antweb.org"));
        assertEquals("example.com", Csrf.hostOf("https://Example.com/page?x=1"));
        assertNull(Csrf.hostOf(null));
        assertNull(Csrf.hostOf("null"));
    }
}
