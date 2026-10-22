package test.org.calacademy.antweb.curate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.curate.UploadAsOptions;
import org.calacademy.antweb.curate.UploadAsOptions.Option;

/**
 * The "Upload as" list of the specimen upload form on the Curate page (Brian Fisher, 2026-10-20): each
 * choice names the group the upload goes into (an upload replaces that group's specimens), sorted by
 * group name, with a warning next to the list; the submitted value is still the login id.
 * Run with -Dantweb.test=<the test/ folder> (the JSP is read from the folder next to it).
 */
public class UploadAsOptionsTest extends TestCase {

    static final Option ANTWEB = new Option(1, "antweb", "California Academy of Sciences", 1);
    static final Option MICHELE = new Option(52, "MEsposito@calacademy.org", "California Academy of Sciences", 1);
    static final Option AERO = new Option(31, "aerochild", "Curator Peter Hawkes AFRC", 31);
    static final Option NONE = new Option(99, "nogroup", null, 0);

    public void testLabelNamesTheGroup() {
        assertEquals("aerochild — Curator Peter Hawkes AFRC (group 31)", UploadAsOptions.label(AERO));
        assertEquals("nogroup — no group", UploadAsOptions.label(NONE));
        assertEquals("a group id without a loaded group", "x — group 5", UploadAsOptions.label(new Option(5, "x", null, 5)));
    }

    public void testSortedByGroupNameValueIsTheLogin() {
        String html = UploadAsOptions.optionsHtml(List.of(ANTWEB, NONE, AERO, MICHELE), 1);
        int cas1 = html.indexOf(">antweb ");
        int cas2 = html.indexOf(">MEsposito@calacademy.org ");
        int afrc = html.indexOf(">aerochild ");
        int none = html.indexOf(">nogroup ");
        assertTrue(html, cas1 >= 0 && cas1 < cas2 && cas2 < afrc && afrc < none);
        assertTrue("the value is the login id, as before", html.contains("<option value=\"31\">aerochild &#8212; Curator Peter Hawkes AFRC (group 31)</option>"));
        assertTrue("the administrator's own login stays selected", html.contains("<option value=\"1\" selected>antweb &#8212; California Academy of Sciences (group 1)</option>"));
        assertEquals("one option per login", 4, html.split("<option ", -1).length - 1);
        assertEquals(4, UploadAsOptions.optionsHtml(List.of(ANTWEB, AERO, MICHELE, NONE, AERO), 1).split("<option ", -1).length - 1);
    }

    public void testEscaped() {
        String html = UploadAsOptions.optionsHtml(List.of(new Option(3, "a<b", "G & \"H\"", 3)), 0);
        assertTrue(html, html.contains(">a&lt;b &#8212; G &amp; &quot;H&quot; (group 3)<"));
        assertFalse(html.contains(" selected"));
    }

    public void testCuratePageUsesItWithTheWarning() throws Exception {
        assertEquals("The upload replaces all specimens of the group shown.", UploadAsOptions.WARNING);
        Path test = Paths.get(System.getProperty("antweb.test", "test"));
        Path jsp = test.toAbsolutePath().getParent().resolve("web/curate/curate-body.jsp");
        if (!Files.isRegularFile(jsp)) jsp = Paths.get(System.getProperty("antweb.web", "web"), "curate/curate-body.jsp");
        if (System.getProperty("antweb.web") != null) assertTrue("CI mounts web/: " + jsp, Files.isRegularFile(jsp));
        if (!Files.isRegularFile(jsp)) return;   // web/ not next to test/ and not mounted
        String src = Files.readString(jsp, StandardCharsets.UTF_8);
        // The label is "Upload as" since the Curate page redesign (2026-10-22).
        String form = src.substring(src.indexOf(">Upload as</label>"), src.indexOf("</select>", src.indexOf(">Upload as</label>")) + 200);
        assertTrue(form, form.contains("<select name=\"uploadAs\""));
        assertTrue(form, form.contains("UploadAsOptions.optionsHtml("));
        assertTrue(form, form.contains("UploadAsOptions.WARNING"));
        assertFalse("no login-name-only options any more", form.contains("Html.esc(curator.getName())"));
    }
}
