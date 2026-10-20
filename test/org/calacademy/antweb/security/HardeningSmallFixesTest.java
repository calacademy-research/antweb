package test.org.calacademy.antweb.security;

import junit.framework.TestCase;
import org.calacademy.antweb.PageAction;

/**
 * Hardening and small fixes of 2026-10-19: /page.do redirects only plain project names.
 * (The AntWiki date check and /getSpeciesList.do were removed later the same day.)
 * Runs in CI inside the antweb image (.github/workflows/tests.yaml).
 */
public class HardeningSmallFixesTest extends TestCase {

    public void testPageRedirectPath() {
        assertEquals("/project.do?name=californiaants", PageAction.redirectPath("california"));
        assertEquals("/project.do?name=floridakeysants", PageAction.redirectPath("floridakeys"));
        for (String s : new String[]{null, "", "a b", "x&y=1", "../x", "http://evil.example", "x\r\nSet-Cookie:a",
                "<script>", "x%26y"}) {
            assertNull(String.valueOf(s), PageAction.redirectPath(s));
        }
    }
}
