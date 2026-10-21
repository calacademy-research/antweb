package test.org.calacademy.antweb.curate;

import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.curate.group.UploaderOnboarding;
import org.calacademy.antweb.curate.group.UploaderOnboarding.GroupRow;
import org.calacademy.antweb.curate.group.UploaderOnboarding.LoginRow;
import org.calacademy.antweb.curate.group.UploaderOnboarding.Request;

/**
 * "Add an uploader" without a database: the checks of the form input and the welcome email
 * text (UploaderOnboarding). The database side is UploaderOnboardingDbTest.
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
        assertTrue(body.startsWith("Hello Jane Doe,"));
        assertTrue(body.contains("upload group Museum X (MX)"));
        assertTrue(body.contains("href=\"" + link + "\""));
        assertTrue(body.contains("it works once, for 14 days"));
        assertTrue(body.contains("<b>jane@museum-x.org</b>"));
        assertTrue(body.contains("https://www.antweb.org/curate.do"));
        assertTrue(body.contains("https://www.antweb.org/web/homepage/AntWeb_specimen_data_requirements_2026v1.xlsx"));
        assertTrue(body.contains("Each upload replaces all specimen records of your upload group"));
        assertTrue(body.contains("unique on AntWeb"));
        assertFalse("no image paragraph without image rights", body.contains("Specimen Image Upload"));
        assertNoIgnore(body);
        assertEquals("Your AntWeb upload account", UploaderOnboarding.welcomeSubject());
    }

    public void testWelcomeEmailExistingAccount() {
        String body = UploaderOnboarding.welcomeBody("https://www.antweb.org", login("jdoe", "", "", true),
            group("Economo Ant Lab", ""), null, 14);
        assertTrue("no name: plain greeting", body.startsWith("Hello,"));
        assertFalse(body.contains("resetPassword.do"));
        assertTrue(body.contains("log out and in again"));
        assertTrue(body.contains("upload group Economo Ant Lab."));
        assertTrue(body.contains("Specimen Image Upload"));
        assertNoIgnore(body);
    }

    public void testWelcomeEmailEscapes() {
        String body = UploaderOnboarding.welcomeBody("https://www.antweb.org", login("x<y", "<script>", "a&b", false),
            group("Lab <i>", "L\"1"), null, 14);
        assertFalse(body.contains("<script>"));
        assertFalse(body.contains("<i>"));
        assertTrue(body.contains("&lt;script&gt; a&amp;b"));
        assertTrue(body.contains("Lab &lt;i&gt; (L&quot;1)"));
        assertTrue(body.contains("<b>x&lt;y</b>"));
    }

    public void testGroupLabel() {
        GroupRow g = group("Museum X", "MX");
        g.uploaders = 1;
        assertEquals("Museum X (MX), group 80, 1 login", g.label());
        g.abbrev = null;
        g.uploaders = 3;
        assertEquals("Museum X, group 80, 3 logins", g.label());
    }

    private static void assertNoIgnore(String body) {
        String lower = body.toLowerCase();
        for (String bad : List.of("ignore this email", "you can ignore", "disregard")) {
            assertFalse(bad, lower.contains(bad));
        }
    }
}
