package test.org.calacademy.antweb.curate;

import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.curate.group.AddUploaderAction;
import org.calacademy.antweb.curate.group.UploaderOnboarding;
import org.calacademy.antweb.curate.group.UploaderOnboarding.GroupRow;
import org.calacademy.antweb.curate.group.UploaderOnboarding.LoginRow;
import org.calacademy.antweb.curate.group.UploaderOnboarding.Request;

/**
 * "Add an uploader" without a database: the checks of the form input, the welcome email text
 * (UploaderOnboarding, plain text) and the summary page's draft email box (AddUploaderAction).
 * The database side is UploaderOnboardingDbTest.
 */
public class UploaderOnboardingTest extends TestCase {

    private static Request request(String email, int groupId, String name, String abbrev) {
        Request r = new Request();
        r.email = email;
        r.groupId = groupId;
        r.newGroupName = name;
        r.newGroupAbbrev = abbrev;
        return r.normalized();
    }

    public void testInputChecks() {
        assertTrue(UploaderOnboarding.inputProblems(request("jane@museum-x.org", 0, "Museum X", "MX")).isEmpty());
        assertTrue(UploaderOnboarding.inputProblems(request(" jane@museum-x.org ", 12, "", "")).isEmpty());
        assertTrue("a username of an existing account", UploaderOnboarding.inputProblems(request("jdoe", 12, "", "")).isEmpty());

        assertFalse(UploaderOnboarding.inputProblems(request("", 12, "", "")).isEmpty());
        assertFalse(UploaderOnboarding.inputProblems(request("jane@museum", 12, "", "")).isEmpty());
        assertFalse(UploaderOnboarding.inputProblems(request("<b>@x.org", 12, "", "")).isEmpty());
        assertFalse("no group chosen", UploaderOnboarding.inputProblems(request("jane@x.org", -1, "", "")).isEmpty());
        assertFalse("new group without name", UploaderOnboarding.inputProblems(request("jane@x.org", 0, "", "MX")).isEmpty());
        assertFalse("new group without short name", UploaderOnboarding.inputProblems(request("jane@x.org", 0, "Museum X", "")).isEmpty());
        assertFalse("short name with a space", UploaderOnboarding.inputProblems(request("jane@x.org", 0, "Museum X", "M X")).isEmpty());
        assertFalse("short name starting with a digit", UploaderOnboarding.inputProblems(request("jane@x.org", 0, "Museum X", "62MX")).isEmpty());
        assertFalse("short name over 20", UploaderOnboarding.inputProblems(request("jane@x.org", 0, "Museum X", "ABCDEFGHIJKLMNOPQRSTU")).isEmpty());
        assertFalse("markup in the group name", UploaderOnboarding.inputProblems(request("jane@x.org", 0, "<script>", "MX")).isEmpty());

        Request names = request("jane@x.org", 12, "", "");
        names.firstName = "Jane<";
        assertFalse(UploaderOnboarding.inputProblems(names).isEmpty());
        names.firstName = "Zoë-Anne";
        names.lastName = "O'Brien";
        assertTrue(UploaderOnboarding.inputProblems(names).isEmpty());

        assertEquals("spaces in the group name collapse", "Museum X Ant Lab", request("a@b.org", 0, "  Museum   X  Ant Lab ", "MX").newGroupName);
    }

    private static LoginRow login(String name, String first, String last, boolean images) {
        LoginRow l = new LoginRow();
        l.id = 7;
        l.name = name;
        l.email = "jane@museum-x.org";
        l.firstName = first;
        l.lastName = last;
        l.uploadImages = images;
        return l;
    }

    private static GroupRow group(String name, String abbrev) {
        GroupRow g = new GroupRow();
        g.id = 80;
        g.name = name;
        g.abbrev = abbrev;
        return g;
    }

    public void testWelcomeEmailWithLink() {
        String link = "https://www.antweb.org/resetPassword.do?token=abc_DEF-123";
        String body = UploaderOnboarding.welcomeBody("https://www.antweb.org", login("jane@museum-x.org", "Jane", "Doe", false),
            group("Museum X", "MX"), link, 14);
        assertTrue(body.startsWith("Hello Jane Doe,\n\n"));
        assertTrue(body.contains("upload group Museum X (MX)"));
        assertTrue("the link on a line of its own", body.contains("\n" + link + "\n"));
        assertTrue("the link is for the recipient", body.contains("This link is for you only: open it to choose your password"));
        assertTrue(body.contains("it works once, for 14 days"));
        assertTrue(body.contains("with your username jane@museum-x.org or your email address"));
        assertTrue(body.contains("https://www.antweb.org/curate.do"));
        assertTrue(body.contains("https://www.antweb.org/web/homepage/AntWeb_specimen_data_requirements_2026v1.xlsx"));
        assertTrue(body.contains("Each upload replaces all specimen records of your upload group"));
        assertTrue(body.contains("unique on AntWeb"));
        assertFalse("no image paragraph without image rights", body.contains("Specimen Image Upload"));
        assertNoIgnore(body);
        assertPlainTextFromTheAdmin(body);
        assertEquals("Your AntWeb upload account", UploaderOnboarding.welcomeSubject());
    }

    public void testWelcomeEmailExistingAccount() {
        String body = UploaderOnboarding.welcomeBody("https://www.antweb.org", login("jdoe", "", "", true),
            group("Economo Ant Lab", ""), null, 14);
        assertTrue("no name: plain greeting", body.startsWith("Hello,"));
        assertFalse(body.contains("resetPassword.do"));
        assertFalse(body.contains("This link is for you only"));
        assertTrue(body.contains("log out and in again"));
        assertTrue(body.contains("upload group Economo Ant Lab."));
        assertTrue(body.contains("Specimen Image Upload"));
        assertNoIgnore(body);
        assertPlainTextFromTheAdmin(body);
    }

    /** The body is plain text: names stay as typed; the page escapes them (testDraftBox). */
    public void testWelcomeEmailIsPlainText() {
        String body = UploaderOnboarding.welcomeBody("https://www.antweb.org", login("x<y", "<script>", "a&b", false),
            group("Lab <i>", "L\"1"), null, 14);
        assertTrue(body.startsWith("Hello <script> a&b,"));
        assertTrue(body.contains("upload group Lab <i> (L\"1)."));
        assertTrue(body.contains("with your username x<y and your password"));
        assertFalse(body.contains("&lt;") || body.contains("&amp;"));
    }

    /** The summary page's draft box: escaped, read-only, Copy and mailto:, and no Send button. */
    public void testDraftBox() {
        String body = UploaderOnboarding.welcomeBody("https://www.antweb.org", login("x<y", "<script>", "a&b", false),
            group("Lab <i>", "L\"1"), "https://www.antweb.org/resetPassword.do?token=abc", 14);
        String html = AddUploaderAction.draftHtml("jane+ants@museum-x.org", "Your AntWeb upload account", body, true);
        assertTrue(html.contains("<h2>Draft email</h2>"));
        assertTrue(html.contains("id=\"draft-to\" readonly") && html.contains("value=\"jane+ants@museum-x.org\""));
        assertTrue(html.contains("value=\"Your AntWeb upload account\""));
        assertTrue(html.contains("<textarea id=\"draft-body\" readonly"));
        assertTrue("escaped in the text box", html.contains("Hello &lt;script&gt; a&amp;b,"));
        assertFalse(html.contains("<script> a&b") || html.contains("Lab <i>"));
        assertTrue(html.contains("onclick=\"awCopyDraft(this)\">Copy</button>"));
        assertTrue(html.contains("navigator.clipboard") && html.contains("t.select()"));
        assertTrue(html.contains("id=\"draft-mailto\" href=\"mailto:jane%2Bants@museum-x.org?subject=Your%20AntWeb%20upload%20account&amp;body="));
        assertTrue(html.contains(">Open in my email program</a>"));
        assertTrue("the one-time link warning", html.contains("send it only to them"));
        assertTrue(html.contains("AntWeb does not send this email"));
        String lower = html.toLowerCase();
        assertFalse("no Send button", lower.contains("send this email to") || lower.contains("type=\"submit\"") || lower.contains("value=\"send\"") || lower.contains("<form"));

        String noAddress = AddUploaderAction.draftHtml(null, "S", "Hello,", false);
        assertTrue(noAddress.contains("This login has no email address."));
        assertFalse(noAddress.contains("mailto:"));
        assertFalse(noAddress.contains("send it only to them"));
    }

    public void testMailtoUrl() {
        String url = AddUploaderAction.mailtoUrl("jane@x.org", "A & B?", "Line 1\nZoë: 50% = ok\n");
        assertEquals("mailto:jane@x.org?subject=A%20%26%20B%3F&body=Line%201%0D%0AZo%C3%AB%3A%2050%25%20%3D%20ok%0D%0A", url);
    }

    public void testGroupLabel() {
        GroupRow g = group("Museum X", "MX");
        g.uploaders = 1;
        assertEquals("Museum X (MX), group 80, 1 login", g.label());
        g.abbrev = null;
        g.uploaders = 3;
        assertEquals("Museum X, group 80, 3 logins", g.label());
    }

    /** A stale form token on Add an uploader shows the form again (filled in); everything else keeps the 403. */
    public void testStaleTokenRefillOnlyForAddUploader() {
        assertTrue(org.calacademy.antweb.util.Csrf.mayRefill("/addUploader.do", "token mismatch", false));
        assertFalse("missing token", org.calacademy.antweb.util.Csrf.mayRefill("/addUploader.do", "missing token", false));
        assertFalse("cross-site", org.calacademy.antweb.util.Csrf.mayRefill("/addUploader.do", "cross-site Origin", false));
        assertFalse("cross-site", org.calacademy.antweb.util.Csrf.mayRefill("/addUploader.do", "cross-site Referer", false));
        assertFalse("multipart", org.calacademy.antweb.util.Csrf.mayRefill("/addUploader.do", "token mismatch", true));
        assertFalse("other pages", org.calacademy.antweb.util.Csrf.mayRefill("/saveGroup.do", "token mismatch", false));
        assertFalse(org.calacademy.antweb.util.Csrf.mayRefill(null, "token mismatch", false));
    }

    /** Sent from the administrator's own address: no markup, no "reply to this email", a name to fill in. */
    private static void assertPlainTextFromTheAdmin(String body) {
        assertFalse(body, body.contains("<a ") || body.contains("<b>") || body.contains("<br"));
        assertFalse(body.toLowerCase().contains("reply to this email"));
        assertFalse(body.contains("antweb@antweb.org"));
        assertTrue(body.contains("\n" + UploaderOnboarding.SIGNATURE_PLACEHOLDER + "\n"));
        assertEquals("[Your name]", UploaderOnboarding.SIGNATURE_PLACEHOLDER);
    }

    private static void assertNoIgnore(String body) {
        String lower = body.toLowerCase();
        for (String bad : List.of("ignore this email", "you can ignore", "disregard")) {
            assertFalse(bad, lower.contains(bad));
        }
    }
}
