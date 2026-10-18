package test.org.calacademy.antweb.security;

import junit.framework.TestCase;
import org.calacademy.antweb.upload.UploadUtil;
import org.calacademy.antweb.util.HtmlSanitizer;

/** Checks added with the 2026-10-17 (c) hardening: AntCat history markup and specimen codes. */
public class SecurityAuditCTest extends TestCase {

    // A taxonomic history item as AntCat exports it (links to antcat.org, AntWiki, a DOI;
    // italics, superscript, a list), with injected active content added.
    private static final String HISTORY =
        "<div class=\"history-item\"><p><i>Camponotus</i> Mayr, 1861: 35 "
        + "(<a href=\"https://www.antcat.org/references/123\" data-tooltip=\"x\">Mayr, 1861</a>; "
        + "<a href=\"https://www.antwiki.org/wiki/Camponotus\" target=\"_blank\">AntWiki</a>; "
        + "<a class=\"external\" href=\"https://doi.org/10.1234/abc\">DOI</a>)<sup>1</sup> H<sub>2</sub>O"
        + "<b>Senior synonym</b> of <span class=\"name\">Myrmentoma</span>.<br>"
        + "<img src=x onerror=alert(1)></p>"
        + "<ul><li>Junior synonym of <a href=\"javascript:alert(2)\">x</a></li>"
        + "<li><a href=\"/catalog/430244\">relative</a><script>alert(3)</script></li></ul></div>";

    public void testHistoryActiveContentRemoved() {
        String out = HtmlSanitizer.sanitize(HISTORY);
        assertFalse(out, out.toLowerCase().contains("onerror"));
        assertFalse(out, out.toLowerCase().contains("javascript:"));
        assertFalse(out, out.toLowerCase().contains("<script"));
        assertFalse(out, out.contains("alert(3)"));
        assertFalse(out, out.contains("data-tooltip"));
        assertFalse(HtmlSanitizer.findUnsafe(HISTORY).isEmpty());
    }

    public void testHistoryMarkupKept() {
        String out = HtmlSanitizer.sanitize(HISTORY);
        assertTrue(out, out.contains("<div class=\"history-item\"><p><i>Camponotus</i> Mayr, 1861: 35"));
        assertTrue(out, out.contains("<a href=\"https://www.antcat.org/references/123\">Mayr, 1861</a>"));
        assertTrue(out, out.contains("<a href=\"https://www.antwiki.org/wiki/Camponotus\" target=\"_blank\" rel=\"noopener noreferrer\">AntWiki</a>"));
        assertTrue(out, out.contains("<a class=\"external\" href=\"https://doi.org/10.1234/abc\">DOI</a>"));
        assertTrue(out, out.contains("<sup>1</sup> H<sub>2</sub>O<b>Senior synonym</b>"));
        assertTrue(out, out.contains("<span class=\"name\">Myrmentoma</span>.<br>"));
        assertTrue(out, out.contains("<ul><li>Junior synonym of "));
        assertTrue(out, out.contains("<a href=\"/catalog/430244\">relative</a>"));
        assertTrue(out, out.endsWith("</li></ul></div>"));
    }

    public void testCleanHistoryUnchangedAndNotFlagged() {
        String clean = "<p><i>Formica</i> Linnaeus, 1758: 579 (<a href=\"https://www.antcat.org/references/1\">ref</a>).</p>";
        assertEquals(clean, HtmlSanitizer.sanitize(clean));
        assertTrue(HtmlSanitizer.findUnsafe(clean).isEmpty());
    }

    public void testAuthorDateHtml() {
        assertEquals("<i>et al.</i>, 2010", HtmlSanitizer.sanitize("<i>et al.</i>, 2010"));
        assertEquals("Smith, 1858", HtmlSanitizer.sanitize("Smith<svg onload=alert(1)></svg>, 1858"));
    }

    // ---------- Specimen codes at upload ----------

    public void testValidSpecimenCodes() {
        for (String code : new String[] {"casent0005428", "antweb1032442", "blf1862(05)-16", "casent0123456-d01",
                "mcz:ent:00012345", "psw17240-4", "jtl000123_a", "inbiocri001234567", "uce.123", "lacm/ent/1", "x#1", "a+b"}) {
            assertTrue(code, UploadUtil.isValidSpecimenCode(code));
        }
    }

    public void testInvalidSpecimenCodes() {
        for (String code : new String[] {"", "casent'0001", "x' or '1'='1", "<script>", "a\"b", "a b", "a;b",
                "a\\b", "a<b", "a>b", "a`b", "a%27b", "a&b", "a\tb", "CASENT0001"}) {
            assertFalse(code, UploadUtil.isValidSpecimenCode(code));
        }
        assertFalse(UploadUtil.isValidSpecimenCode(null));
    }
}
