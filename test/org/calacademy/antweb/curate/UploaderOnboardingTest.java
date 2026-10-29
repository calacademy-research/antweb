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
 * (UploaderOnboarding, plain text, signed with the administrator's name), the summary page's welcome
 * email box and the checks before Send (AddUploaderAction), and the quotes trimmed from a new group's names.
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

    /** The summary page's welcome email box: escaped, editable, Send (POST with the token) and Copy, no mailto:. */
    public void testDraftBox() {
        String body = UploaderOnboarding.welcomeBody("https://www.antweb.org", login("x<y", "<script>", "a&b", false),
            group("Lab <i>", "L\"1"), "https://www.antweb.org/resetPassword.do?token=abc", 14);
        String csrf = "<input type=\"hidden\" name=\"_csrf\" value=\"TOKEN\">";
        String html = AddUploaderAction.draftHtml("https://www.antweb.org", "jane+ants@museum-x.org", "Your AntWeb upload account", body, true, 7, csrf, null, null);
        assertTrue(html.contains("<h2>Welcome email</h2>"));
        assertTrue(html.contains("<form method=\"POST\" action=\"https://www.antweb.org/addUploader.do\""));
        assertTrue(html.contains(csrf) && html.contains("name=\"step\" value=\"send\"") && html.contains("name=\"loginId\" value=\"7\""));
        assertTrue(html.contains("id=\"draft-to\" name=\"to\" size=") && html.contains("value=\"jane+ants@museum-x.org\""));
        assertTrue(html.contains("id=\"draft-subject\" name=\"subject\" size=") && html.contains("value=\"Your AntWeb upload account\""));
        assertTrue(html.contains("<textarea id=\"draft-body\" name=\"body\" rows="));
        assertFalse("editable", html.contains("readonly"));
        assertTrue("escaped in the text box", html.contains("Hello &lt;script&gt; a&amp;b,"));
        assertFalse(html.contains("<script> a&b") || html.contains("Lab <i>"));
        assertTrue(html.contains("<input type=\"submit\" id=\"draft-send\" value=\"Send\">"));
        assertTrue(html.contains("onclick=\"awCopyDraft(this)\">Copy</button>"));
        assertTrue(html.contains("navigator.clipboard") && html.contains("t.select()"));
        assertFalse("no mailto: link", html.contains("mailto:") || html.contains("Open in my email program"));
        assertTrue("the one-time link warning", html.contains("send it only to them") && html.contains("do not share a screenshot"));
        assertTrue(html.contains("replies go to you, and you get a copy"));

        String noAddress = AddUploaderAction.draftHtml("", null, "S", "Hello,", false, 7, csrf, null, null);
        assertTrue(noAddress.contains("This login has no email address."));
        assertFalse("no Send without an address", noAddress.contains("value=\"Send\"") || noAddress.contains("<form"));
        assertFalse(noAddress.contains("send it only to them"));

        String stale = AddUploaderAction.draftHtml("", "jane@x.org", "S", body, true, 0, null, null, AddUploaderAction.NOT_CURRENT);
        assertFalse("not sendable: no form", stale.contains("<form") || stale.contains("value=\"Send\""));
        assertTrue(stale.contains("id=\"draft-body\" name=\"body\" readonly") && stale.contains("Not sent: this welcome email belongs"));
    }

    /** After sending: the confirmation, the text read-only, no second Send. */
    public void testSentBox() {
        LoginRow p = login("jane@museum-x.org", "Jane", "Doe", false);
        String note = AddUploaderAction.sentNote(p, "2026-10-29 14:05 PDT", "bfisher@calacademy.org");
        assertEquals("Sent to Jane Doe <jane@museum-x.org> at 2026-10-29 14:05 PDT; a copy went to you (bfisher@calacademy.org).", note);
        assertEquals("Sent to jane@museum-x.org at T; no copy to you: your login has no email address.",
            AddUploaderAction.sentNote(login("jane@museum-x.org", "", "", false), "T", null));
        String html = AddUploaderAction.draftHtml("", "jane@museum-x.org", "S", "Hello <b>", true, 7, null, note, null);
        assertTrue(html.contains("id=\"draft-sent\"") && html.contains("Sent to Jane Doe &lt;jane@museum-x.org&gt; at"));
        assertFalse(html.contains("<form") || html.contains("value=\"Send\""));
        assertTrue(html.contains("readonly") && html.contains("Hello &lt;b&gt;"));
        assertFalse("no link warning once sent", html.contains("do not share a screenshot"));
    }

    /** Send: only to the new login's address (as stored), the one-time link kept, length limits; no message holds the link. */
    public void testSendChecks() {
        String link = "https://www.antweb.org/resetPassword.do?token=SECRET_abc123";
        String body = "Hello,\n\n" + link + "\n\nBest regards,\nBrian Fisher\n";
        assertNull(AddUploaderAction.sendProblem("jane@museum-x.org", "jane@museum-x.org", "Your AntWeb upload account", body, link));
        assertNull("case and spaces do not matter", AddUploaderAction.sendProblem(" Jane@Museum-X.org ", "jane@museum-x.org ", "S", body, link));
        assertNull("no link to keep", AddUploaderAction.sendProblem("jane@x.org", "jane@x.org", "S", "Hello,", null));

        String other = AddUploaderAction.sendProblem("someone@else.org", "jane@museum-x.org", "S", body, link);
        assertTrue(other, other.startsWith("Not sent: the To address must be the email address of the login just added (jane@museum-x.org)"));
        assertNotNull("two addresses", AddUploaderAction.sendProblem("jane@museum-x.org, x@y.org", "jane@museum-x.org", "S", body, link));
        assertTrue(AddUploaderAction.sendProblem("jane@x.org", null, "S", body, link).contains("no valid email address"));
        assertTrue(AddUploaderAction.sendProblem("jane@x.org", "", "S", body, link).contains("no valid email address"));

        assertEquals("Not sent: enter a subject.", AddUploaderAction.sendProblem("jane@x.org", "jane@x.org", " ", body, link));
        assertNotNull("header injection", AddUploaderAction.sendProblem("jane@x.org", "jane@x.org", "S\nBcc: x@y.org", body, link));
        assertNotNull("subject too long", AddUploaderAction.sendProblem("jane@x.org", "jane@x.org", "S".repeat(201), body, link));
        assertNotNull("empty text", AddUploaderAction.sendProblem("jane@x.org", "jane@x.org", "S", " \n ", link));
        String longBody = body + "x".repeat(20001);
        assertTrue(AddUploaderAction.sendProblem("jane@x.org", "jane@x.org", "S", longBody, link).contains("longer than 20000"));

        String noLink = AddUploaderAction.sendProblem("jane@x.org", "jane@x.org", "S", body.replace(link, ""), link);
        assertTrue(noLink, noLink.contains("one-time link to choose a password is no longer in the text"));
        String cut = AddUploaderAction.sendProblem("jane@x.org", "jane@x.org", "S", body.replace(link, link.substring(0, link.length() - 2)), link);
        assertNotNull("a shortened link counts as removed", cut);
        for (String m : new String[] {other, noLink, cut}) assertFalse("no token in a message", m.contains("SECRET"));
    }

    /** The sign-off is the administrator's name (2026-10-29); "[Your name]" only when the login has none. */
    public void testSignature() {
        assertEquals("Brian Fisher", UploaderOnboarding.signature("Brian", "Fisher"));
        assertEquals("Brian", UploaderOnboarding.signature(" Brian ", null));
        assertEquals("[Your name]", UploaderOnboarding.signature("", " "));
        assertEquals("[Your name]", UploaderOnboarding.signature(null, null));
        String body = UploaderOnboarding.welcomeBody("https://www.antweb.org", login("jane@museum-x.org", "Jane", "Doe", false),
            group("Museum X", "MX"), null, 14, UploaderOnboarding.signature("Brian", "Fisher"));
        assertTrue(body.endsWith("Best regards,\nBrian Fisher\nAntWeb, California Academy of Sciences\n"));
        assertFalse(body.contains("[Your name]"));
        assertNoIgnore(body);
    }

    /** A group name pasted with quotes keeps none at its ends (Brian's "Auburn ...\u201D", 2026-10-29). */
    public void testGroupNameQuotesTrimmed() {
        assertEquals("Auburn University Museum of Natural History",
            request("a@b.org", 0, "Auburn University Museum of Natural History\u201D", "AUM").newGroupName);
        assertEquals("Auburn University Museum of Natural History",
            request("a@b.org", 0, " \u201CAuburn University  Museum of Natural History\u201D\u00A0", "AUM").newGroupName);
        assertEquals("Museum X", request("a@b.org", 0, "\"Museum X\"", "MX").newGroupName);
        assertEquals("Museum X", request("a@b.org", 0, "'Museum X'", "MX").newGroupName);
        assertEquals("Museum X", request("a@b.org", 0, "\u2018Museum X\u2019 ", "MX").newGroupName);
        assertEquals("Museum X", request("a@b.org", 0, "\u00ABMuseum X\u00BB", "MX").newGroupName);
        assertEquals("quotes inside stay", "The \u201CAnt\u201D Lab", request("a@b.org", 0, "The \u201CAnt\u201D Lab", "MX").newGroupName);
        assertEquals("AUM", request("a@b.org", 0, "Museum X", " \u201CAUM\u201D ").newGroupAbbrev);
        assertEquals("AUM", request("a@b.org", 0, "Museum X", "\"AUM'").newGroupAbbrev);
        assertEquals("", UploaderOnboarding.stripQuotes("\u201D\" "));
        assertEquals("", UploaderOnboarding.stripQuotes(null));
        assertTrue(UploaderOnboarding.inputProblems(request("a@b.org", 0, "Auburn University Museum of Natural History\u201D", "\u201CAUM\u201D")).isEmpty());
        assertFalse("only quotes: no name", UploaderOnboarding.inputProblems(request("a@b.org", 0, "\u201C\u201D", "AUM")).isEmpty());
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

    /** Plain text, no "reply to this email" (replies go to the administrator), "[Your name]" without a signer. */
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
