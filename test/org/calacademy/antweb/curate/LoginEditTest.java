package test.org.calacademy.antweb.curate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import junit.framework.TestCase;

import org.calacademy.antweb.curate.login.LoginEdit;
import org.calacademy.antweb.curate.login.LoginEdit.Change;
import org.calacademy.antweb.curate.login.LoginEdit.Choice;
import org.calacademy.antweb.curate.login.LoginEdit.GroupChoice;
import org.calacademy.antweb.curate.login.LoginEdit.State;
import org.calacademy.antweb.curate.login.LoginEditPage;
import org.calacademy.antweb.curate.login.LoginEditPage.PageData;

/**
 * The login form without a database (LoginEdit, LoginEditPage): finding what was typed in the
 * add boxes, the new lists, the change summary, the checks (your own administrator rights,
 * changed fields only) and the page (sections, escaping, warning, upload group list).
 * The database side is LoginEditDbTest.
 */
public class LoginEditTest extends TestCase {

    private static final List<Choice> PROJECTS = Arrays.asList(
        new Choice("madagascarants", "Madagascar (madagascarants)"),
        new Choice("fijiants", "Fiji (fijiants)"),
        new Choice("galapagosants", "Galapagos (galapagosants)"),
        new Choice("galapagos2ants", "Galapagos (galapagos2ants)"));
    private static final List<Choice> COUNTRIES = Arrays.asList(
        new Choice("Kenya", "Kenya"), new Choice("Madagascar", "Madagascar"), new Choice("United States", "United States"));

    private static State state() {
        State s = new State();
        s.id = 40;
        s.name = "jdoe";
        s.email = "jane@museum-x.org";
        s.firstName = "Jane";
        s.lastName = "Doe";
        s.groupId = 71;
        s.uploadSpecimens = true;
        s.hasPassword = true;
        s.projects.add("madagascarants");
        s.countries.add("Kenya");
        return s;
    }

    private static Change unchanged(State s) {
        Change c = new Change();
        c.id = s.id;
        c.name = s.name;
        c.email = s.email;
        c.firstName = s.firstName;
        c.lastName = s.lastName;
        c.isAdmin = s.isAdmin;
        c.uploadSpecimens = s.uploadSpecimens;
        c.uploadImages = s.uploadImages;
        c.group = "" + s.groupId;
        c.uploadAs = s.uploadAs;
        return c;
    }

    private static List<String> check(State before, Change c, int by, State after) {
        List<String> problems = new ArrayList<>();
        LoginEdit.checkAndBuild(before, c, by, true, PROJECTS, COUNTRIES, after, problems);
        return problems;
    }

    public void testResolve() {
        assertEquals("madagascarants", LoginEdit.resolve("Madagascar (madagascarants)", PROJECTS));
        assertEquals("madagascarants", LoginEdit.resolve("  MADAGASCAR ", PROJECTS));
        assertEquals("fijiants", LoginEdit.resolve("fijiants", PROJECTS));
        assertNull("two projects with this title", LoginEdit.resolve("Galapagos", PROJECTS));
        assertEquals("galapagos2ants", LoginEdit.resolve("Galapagos (galapagos2ants)", PROJECTS));
        assertNull(LoginEdit.resolve("Mada", PROJECTS));
        assertNull(LoginEdit.resolve("", PROJECTS));
        assertEquals("United States", LoginEdit.resolve("united states", COUNTRIES));
    }

    public void testNewList() {
        List<String> problems = new ArrayList<>();
        TreeSet<String> current = new TreeSet<>(Arrays.asList("madagascarants", "oldprojectants"));
        TreeSet<String> out = LoginEdit.newList(current, new String[] {"madagascarants"},
            new String[] {"Fiji (fijiants)", "fiji", "", null}, PROJECTS, "live project", problems);
        assertEquals("[fijiants, oldprojectants]", out.toString());
        assertTrue(problems.isEmpty());
        assertEquals("the current list is not changed", 2, current.size());

        out = LoginEdit.newList(current, null, new String[] {"Atlantis"}, PROJECTS, "live project", problems);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0), problems.get(0).contains("No live project is called \"Atlantis\""));
        assertEquals("[madagascarants, oldprojectants]", out.toString());
    }

    public void testUploadAs() {
        assertEquals("23,45,7", LoginEdit.normalizeUploadAs(" 23, 45 ,7, "));
        assertEquals("", LoginEdit.normalizeUploadAs(null));
        assertEquals("", LoginEdit.normalizeUploadAs("  "));
        assertEquals("michele", LoginEdit.normalizeUploadAs(" michele "));
        assertEquals(Arrays.asList(23, 45), LoginEdit.uploadAsIds("23,45,23"));
        assertTrue(LoginEdit.uploadAsIds("michele").isEmpty());
    }

    public void testDescribe() {
        State before = state();
        State after = before.copy();
        Map<Integer, String> groups = new HashMap<>();
        groups.put(71, "Museum X (MX) — group 71");
        groups.put(1, "California Academy of Sciences (CAS) — group 1");
        Map<String, String> labels = LoginEdit.labels(PROJECTS);
        assertTrue("nothing changed", LoginEdit.describe(before, after, groups, labels).isEmpty());

        after.uploadSpecimens = false;
        after.uploadImages = true;
        after.isAdmin = true;
        after.groupId = 1;
        after.projects.remove("madagascarants");
        after.projects.add("fijiants");
        after.countries.add("Madagascar");
        after.email = "jane.doe@museum-x.org";
        after.uploadAs = "23";
        List<String> d = LoginEdit.describe(before, after, groups, labels);
        assertTrue(d.toString(), d.contains("Upload specimen data: removed"));
        assertTrue(d.contains("Upload specimen images: added"));
        assertTrue(d.contains("Administrator: added"));
        assertTrue(d.toString(), d.contains("Upload group: California Academy of Sciences (CAS) — group 1 (was: Museum X (MX) — group 71)"));
        assertTrue(d.contains("Project Fiji (fijiants): added"));
        assertTrue(d.contains("Project Madagascar (madagascarants): removed"));
        assertTrue(d.contains("Country Madagascar: added"));
        assertTrue(d.contains("Email: \"jane@museum-x.org\" changed to \"jane.doe@museum-x.org\""));
        assertTrue(d.contains("Upload as: (empty) changed to \"23\""));
        assertEquals(9, d.size());

        State noGroup = before.copy();
        noGroup.groupId = LoginEdit.NO_GROUP;
        assertEquals("Upload group: No upload group (was: Museum X (MX) — group 71)",
            LoginEdit.describe(before, noGroup, groups, labels).get(0));
        State spaces = before.copy();
        before.uploadAs = "23,45";
        spaces.uploadAs = "23, 45";
        assertTrue("the same numbers", LoginEdit.describe(before, spaces, groups, labels).isEmpty());
    }

    public void testChecks() {
        State before = state();
        State after = before.copy();
        Change c = unchanged(before);
        assertTrue(check(before, c, 1, after).isEmpty());
        assertTrue(LoginEdit.describe(before, after, null, null).isEmpty());

        // Your own administrator rights cannot be removed here.
        State admin = state();
        admin.isAdmin = true;
        c = unchanged(admin);
        c.isAdmin = false;
        List<String> p = check(admin, c, admin.id, admin.copy());
        assertEquals(1, p.size());
        assertTrue(p.get(0), p.get(0).contains("cannot remove your own administrator rights"));
        assertTrue("another administrator may", check(admin, c, 1, admin.copy()).isEmpty());

        // Only changed values are checked: an old stored address that is not valid still saves.
        State legacy = state();
        legacy.email = "jane at museum";
        assertTrue(check(legacy, unchanged(legacy), 1, legacy.copy()).isEmpty());
        c = unchanged(before);
        c.email = "jane at museum";
        assertTrue(check(before, c, 1, before.copy()).get(0).contains("is not a valid email address"));

        c = unchanged(before);
        c.name = "  ";
        assertTrue(check(before, c, 1, before.copy()).contains("Enter a username."));
        c.name = "<b>jane</b>";
        assertFalse(check(before, c, 1, before.copy()).isEmpty());
        c.name = "a-very-long-username-of-more-than-32-chars";
        assertTrue(check(before, c, 1, before.copy()).get(0).contains("at most 32"));

        c = unchanged(before);
        c.uploadAs = "michele";
        assertTrue(check(before, c, 1, before.copy()).get(0).contains("login numbers separated by commas"));

        c = unchanged(before);
        c.group = "none";
        after = before.copy();
        assertTrue(check(before, c, 1, after).isEmpty());
        assertEquals(LoginEdit.NO_GROUP, after.groupId);
        c.group = "Museum X";
        assertTrue(check(before, c, 1, before.copy()).contains("Choose the upload group from the list."));

        List<String> denied = new ArrayList<>();
        LoginEdit.checkAndBuild(before, unchanged(before), 40, false, PROJECTS, COUNTRIES, before.copy(), denied);
        assertEquals(Arrays.asList("Only administrators can change logins."), denied);
    }

    private static PageData page(State s) {
        PageData d = new PageData();
        d.state = s;
        d.created = "2012-01-01 00:00";
        d.lastLogin = "";
        d.groups = new ArrayList<>(Arrays.asList(new GroupChoice(71, "Museum X", "MX"), new GroupChoice(1, "California Academy of Sciences", "CAS"),
            new GroupChoice(62, "economo ant lab", "OIST")));
        LoginEdit.sortGroups(d.groups);
        d.projectOptions = PROJECTS;
        d.countryOptions = COUNTRIES;
        d.projectLabels = LoginEdit.labels(PROJECTS);
        d.projectLabels.put("oldprojectants", "Old project (oldprojectants)");
        return d;
    }

    public void testAdminPage() {
        State s = state();
        s.firstName = "<script>alert(1)</script>";
        s.projects.add("oldprojectants");
        s.countries.add("Atlantis");
        s.uploadAs = "23";
        PageData d = page(s);
        d.uploadAsNames.put(23, "michele (California Academy of Sciences)");
        String html = LoginEditPage.renderAdmin(d, 1, "<input type=\"hidden\" name=\"_csrf\" value=\"T\">", "https://x", "https://x");

        for (String text : new String[] {"<h2>Account</h2>", "<h2>What this person can do</h2>", "<h2>Upload group</h2>",
                "<h2>Places and projects this curator can edit</h2>", "<h2>Other settings</h2>", "<h2>Other actions</h2>",
                "method=\"POST\"", "name=\"_csrf\" value=\"T\"", "name=\"editForm\" value=\"2\"",
                "Upload specimen data", "Upload specimen images", "Full access to all tools", "Give only to AntWeb staff",
                "Each upload replaces all specimens of that upload group", "/addUploader.do\">Add an uploader</a>",
                "<option value=\"none\">No upload group</option>",
                "<option value=\"71\" selected>Museum X (MX) — group 71</option>",
                "name=\"removeProjects\" value=\"madagascarants\"", "Old project (oldprojectants) (not live)",
                "Atlantis (not a valid country on AntWeb now)", "name=\"removeCountries\" value=\"Kenya\"",
                "list=\"projectOptions\"", "<option value=\"Fiji (fijiants)\"></option>", "<datalist id=\"countryOptions\">",
                "name=\"uploadAs\" value=\"23\"", "23 = michele (California Academy of Sciences)",
                "name=\"delete\" value=\"delete\"", "return confirm(", "name=\"changePassword\" value=\"Change Password\"",
                "Save changes"}) {
            assertTrue("missing: " + text, html.contains(text));
        }
        assertFalse("escaped", html.contains("<script>alert"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        // Sorted by name, case does not matter.
        int cas = html.indexOf("California Academy"), eco = html.indexOf("economo ant lab"), mx = html.indexOf(">Museum X");
        assertTrue(cas < eco && eco < mx);
        // One right per line, in this order, without the site's floating "right" class.
        assertFalse(html.contains("class=\"right\""));
        int sp = html.indexOf("<b>Upload specimen data</b>"), im = html.indexOf("<b>Upload specimen images</b>"), ad = html.indexOf("> Administrator</label>");
        assertTrue(sp > 0 && sp < im && im < ad && ad < html.indexOf("<h2>Upload group</h2>"));
        assertTrue(html.contains(".loginEdit h2 { clear: both;"));
        assertTrue(html.contains("@media (max-width: 760px)"));
        // The warning is there, hidden while the box is not ticked.
        assertTrue(html.contains("id=\"adminWarning\" hidden"));
        assertFalse("no old fields", html.contains("Is Super Admin") || html.contains("Upload As (comma") || html.contains("multiple size"));
        assertFalse("has a password: no link button", html.contains("value=\"invite\""));

        s.isAdmin = true;
        s.hasPassword = false;
        s.groupId = 0;
        html = LoginEditPage.renderAdmin(page(s), 1, "", "", "");
        assertTrue(html.contains("id=\"adminWarning\">"));
        assertTrue(html.contains("name=\"isAdmin\" id=\"isAdmin\" checked"));
        assertTrue("a group not in the list is kept", html.contains("<option value=\"0\" selected>Group 0 (not in the list of upload groups)</option>"));
        assertTrue(html.contains("name=\"step\" value=\"invite\""));
    }

    public void testOwnAdminLoginIsLocked() {
        State s = state();
        s.isAdmin = true;
        String html = LoginEditPage.renderAdmin(page(s), s.id, "", "", "");
        assertTrue(html.contains("Administrator: yes"));
        assertTrue(html.contains("<input type=\"hidden\" name=\"isAdmin\" value=\"on\">"));
        assertFalse(html.contains("type=\"checkbox\" name=\"isAdmin\""));
        assertTrue("own password with the current one", html.contains("name=\"currentPassword\""));
        assertFalse("no delete of your own login", html.contains("name=\"delete\""));
    }

    public void testNewLoginPage() {
        State s = new State();
        s.id = 9001;
        PageData d = page(s);
        d.isNew = true;
        String html = LoginEditPage.renderAdmin(d, 1, "", "", "");
        assertTrue(html.contains("<h1>New login</h1>"));
        assertTrue(html.contains("name=\"newLogin\" value=\"true\""));
        assertTrue(html.contains("<option value=\"none\" selected>No upload group</option>"));
        assertTrue(html.contains("Create login"));
        assertFalse(html.contains("<td>Login number</td>"));
    }

    public void testCuratorOwnPage() {
        State s = state();
        String html = LoginEditPage.renderSelf(page(s), "<input type=\"hidden\" name=\"_csrf\" value=\"T\">", "", "");
        assertTrue(html.contains("<h1>Your login</h1>"));
        assertTrue(html.contains("name=\"currentPassword\""));
        assertTrue(html.contains("Upload specimen data: yes"));
        assertTrue(html.contains("Upload group: Museum X (MX) — group 71"));
        assertTrue(html.contains("Project Madagascar (madagascarants)"));
        for (String control : new String[] {"name=\"isAdmin\"", "name=\"isUploadSpecimens\"", "name=\"groups\"", "name=\"uploadAs\"",
                "name=\"addProjects\"", "name=\"removeProjects\"", "name=\"delete\""}) {
            assertFalse(control, html.contains(control));
        }
    }

    public void testChangeSummary() {
        assertEquals("", LoginEditPage.renderChanges(null));
        assertTrue(LoginEditPage.renderChanges(new ArrayList<>()).contains("Nothing was changed"));
        String html = LoginEditPage.renderChanges(Arrays.asList("Upload specimen data: added", "Username: \"<b>\" changed to \"x\""));
        assertTrue(html.contains("<li>Upload specimen data: added</li>"));
        assertTrue(html.contains("&lt;b&gt;"));
    }

    /** Empty "Places and projects" lists: administrators need none, other logins have none yet. */
    public void testEmptyPlacesText() {
        String admin = "<p>Not needed: administrators can edit every project and country.</p>";
        String none = "<p>None assigned yet.</p>";
        State s = state();
        s.projects.clear();
        s.countries.clear();
        String html = LoginEditPage.renderAdmin(page(s), 1, "", "", "");
        assertEquals("curator: projects and countries both say none yet", 2, html.split(java.util.regex.Pattern.quote(none), -1).length - 1);
        assertFalse(html.contains(admin));
        assertFalse(html.contains("<p>None.</p>"));
        assertTrue(LoginEditPage.renderSelf(page(s), "", "", "").contains(none));

        s.isAdmin = true;
        html = LoginEditPage.renderAdmin(page(s), 1, "", "", "");
        assertEquals("administrator: both lists say not needed", 2, html.split(java.util.regex.Pattern.quote(admin), -1).length - 1);
        assertFalse(html.contains(none));

        s.projects.add("madagascarants");
        html = LoginEditPage.renderAdmin(page(s), 1, "", "", "");
        assertEquals("only the empty list (countries) gets the text", 1, html.split(java.util.regex.Pattern.quote(admin), -1).length - 1);
    }
}
