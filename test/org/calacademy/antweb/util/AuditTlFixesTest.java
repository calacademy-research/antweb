package test.org.calacademy.antweb.util;

import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import org.calacademy.antweb.Caste;
import org.calacademy.antweb.Homonym;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.LoginAction;
import org.calacademy.antweb.SpeciesListable;
import org.calacademy.antweb.Specimen;
import org.calacademy.antweb.StatsPageAction;
import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.curate.login.LoginEdit;
import org.calacademy.antweb.curate.login.SaveLoginAction;
import org.calacademy.antweb.curate.speciesList.SpeciesListValidator;
import org.calacademy.antweb.geolocale.LocalityOverview;
import org.calacademy.antweb.home.GroupCountDb;
import org.calacademy.antweb.home.ImageDb;
import org.calacademy.antweb.home.InactiveAccountDb;
import org.calacademy.antweb.upload.LineNumMgr;
import org.calacademy.antweb.upload.MessageMgr;
import org.calacademy.antweb.util.Csrf;
import org.calacademy.antweb.util.DateUtil;
import org.calacademy.antweb.util.QueriesWithParams;
import org.calacademy.antweb.util.Scheduler;
import org.calacademy.antweb.util.SpeciesListAccess;
import org.calacademy.antweb.util.UtilDataAction;
import org.calacademy.antweb.util.ListUploadsAction;
import org.calacademy.antweb.util.TaxonPropMgr;

/**
 * Audit fixes of 2026-11-02 (triage tl), no database:
 * - taking away an upload group, "upload as", a project or a country ends the login's sessions, as other rights do;
 * - "Origin: null" is refused on the log-in and forgot-password pages;
 * - Create Account follows the password rules and the username length; a must_reset read error refuses the log-in;
 * - changing your own email address needs your current password;
 * - the caste sort keeps every specimen; a homonym link URL-encodes the author date; the statistics page does not wait
 *   for a recount; the subfamily of a name is the longest one it starts with;
 * - the species list validator offers only a close, unique spelling; inactive accounts recheck projects and countries;
 * - a replaced image keeps its owner groups; a run of the sets is claimed atomically; a failed data job no longer says
 *   "No changes made"; carriage-return fragments are checked by their file line; upload errors are escaped;
 * - species lists are edited by key, a name only when it stands for that list; range reports by place id;
 * - "10-12-2012" is read as a month/day date (was 2012-01-01); centroids in parentheses are read;
 * - CREATE TABLE runs only when the table is missing (it commits the caller's transaction);
 * - upload lists: a curator sees their own group's uploads, admins every group's (Brian Fisher, 2026-11-02);
 * - no (or an unknown) bioregion is never introduced, in the nightly flags and on the species list (2026-11-02).
 */
public class AuditTlFixesTest extends TestCase {

    // ------------------------------------------------------------------ logins

    private static LoginEdit.State state(int group, String uploadAs, String... projects) {
        LoginEdit.State s = new LoginEdit.State();
        s.groupId = group;
        s.uploadAs = uploadAs;
        s.uploadSpecimens = true;
        s.projects.addAll(Arrays.asList(projects));
        return s;
    }

    public void testRemovedGroupProjectOrUploadAsEndsSessions() {
        LoginEdit.State before = state(16, "", "madants");
        assertFalse("nothing removed", LoginEdit.removesRights(before, before.copy()));
        LoginEdit.State added = before.copy();
        added.projects.add("fijiants");
        assertFalse("a project added", LoginEdit.removesRights(before, added));
        assertTrue("group changed", LoginEdit.removesRights(before, state(17, "", "madants")));
        assertTrue("group removed", LoginEdit.removesRights(before, state(LoginEdit.NO_GROUP, "", "madants")));
        assertTrue("project removed", LoginEdit.removesRights(before, state(16, "")));
        assertTrue("upload as changed", LoginEdit.removesRights(before, state(16, "42", "madants")));
        LoginEdit.State country = before.copy();
        country.countries.add("Madagascar");
        assertTrue("country removed", LoginEdit.removesRights(country, before.copy()));
        assertFalse("no group before: getting one takes nothing away", LoginEdit.removesRights(state(LoginEdit.NO_GROUP, ""), state(16, "")));
    }

    private static jakarta.servlet.http.HttpServletRequest post(String path, String origin) {
        return (jakarta.servlet.http.HttpServletRequest) Proxy.newProxyInstance(AuditTlFixesTest.class.getClassLoader(),
            new Class<?>[]{jakarta.servlet.http.HttpServletRequest.class}, (p, m, a) -> {
                switch (m.getName()) {
                    case "getServletPath": return path;
                    case "getMethod": return "POST";
                    case "getHeader": return "Origin".equals(a[0]) ? origin : null;
                    case "getServerName": return "www.antweb.org";
                    default: return null;
                }
            });
    }

    public void testNullOriginRefusedOnSignInPages() {
        assertEquals("null Origin", Csrf.nullOrigin(post("/login.do", "null")));
        assertEquals("null Origin", Csrf.nullOrigin(post("/forgotPassword.do", " null ")));
        assertNull("the reset page itself sends Origin: null (no-referrer)", Csrf.nullOrigin(post("/resetPassword.do", "null")));
        assertNull(Csrf.nullOrigin(post("/login.do", "https://www.antweb.org")));
        assertNull(Csrf.nullOrigin(post("/login.do", null)));
        assertNull(Csrf.crossSite(post("/login.do", "https://www.antweb.org")));
    }

    public void testNewAccountRules() {
        assertNotNull("too short", LoginAction.newAccountProblem("bob", "a"));
        String longName = "abcdefghijklmnopqrstuvwxyz1234567";   // 33
        String problem = LoginAction.newAccountProblem(longName, "Xq7-long-enough-Pw");
        assertNotNull(problem);
        assertTrue(problem, problem.contains("32"));
        assertNull(LoginAction.newAccountProblem("bob", "Xq7-long-enough-Pw"));
    }

    public void testMustResetFailsClosed() {
        assertFalse("column not made yet", LoginAction.mustResetOnError(new SQLException("Unknown column", "42S22", 1054)));
        assertTrue("any other error refuses", LoginAction.mustResetOnError(new SQLException("Lock wait timeout", "HY000", 1205)));
        assertTrue(LoginAction.mustResetOnError(new SQLException("gone")));
    }

    public void testEmailChangeNeedsCurrentPassword() {
        assertFalse(SaveLoginAction.emailChanges("a@b.org", " A@B.org "));
        assertFalse(SaveLoginAction.emailChanges(null, ""));
        assertTrue(SaveLoginAction.emailChanges("a@b.org", "x@y.org"));
        assertTrue(SaveLoginAction.emailChanges("", "x@y.org"));
        assertTrue(SaveLoginAction.EMAIL_NEEDS_PASSWORD.contains("current password"));
    }

    // ------------------------------------------------------------------ core

    private static Specimen specimen(String code, String caste, String subcaste) {
        Specimen s = new Specimen();
        s.setCode(code);
        s.setCaste(caste);
        s.setSubcaste(subcaste);
        return s;
    }

    public void testCasteSortKeepsEverySpecimen() {
        ArrayList<Taxon> in = new ArrayList<>();
        in.add(specimen("a", "worker", ""));
        in.add(specimen("b", "worker", "ergatoid"));
        in.add(specimen("c", null, "ergatoid"));
        in.add(specimen("d", "queen", "foo"));
        in.add(specimen("e", "queen", "ergatoid"));
        in.add(specimen("f", "worker", "normal"));
        in.add(specimen("g", "male", null));
        ArrayList<Taxon> out = Caste.sortSpecimenByCasteSubcaste(in);
        assertEquals(in.size(), out.size());
        List<String> inCodes = new ArrayList<>();
        for (Taxon t : in) inCodes.add(((Specimen) t).getCode());
        List<String> outCodes = new ArrayList<>();
        for (Taxon t : out) outCodes.add(((Specimen) t).getCode());
        java.util.Collections.sort(inCodes);
        java.util.Collections.sort(outCodes);
        assertEquals("each specimen once", inCodes, outCodes);
        assertEquals("normal first", "f", ((Specimen) out.get(0)).getCode());
    }

    public void testHomonymLinkEncodesAuthorDate() {
        String url = new Homonym().makeUrl("description.do", "species", "myrmicinae", "pheidole", "x", null, "Smith & Jones, 1900");
        assertTrue(url, url.endsWith("&authorDate=Smith+%26+Jones%2C+1900"));
    }

    public void testStatisticsPageVisitsDoNotWaitForARecount() throws Exception {
        assertFalse(Modifier.isSynchronized(StatsPageAction.class.getDeclaredMethod("keptNumbers").getModifiers()));
        assertTrue(Modifier.isVolatile(StatsPageAction.class.getDeclaredField("s_numbers").getModifiers()));
    }

    public void testSubfamilyIsTheLongestItStartsWith() {
        List<String> order1 = Arrays.asList("ponerinae", "paraponerinae", "heteroponerinae", "myrmicinae");
        List<String> order2 = Arrays.asList("paraponerinae", "heteroponerinae", "myrmicinae", "ponerinae");
        for (List<String> list : Arrays.asList(order1, order2)) {
            assertEquals("paraponerinae", Taxon.knownSubfamilyOf("paraponerinaeparaponera clavata", list));
            assertEquals("heteroponerinae", Taxon.knownSubfamilyOf("heteroponerinaeheteroponera relicta", list));
            assertEquals("ponerinae", Taxon.knownSubfamilyOf("ponerinaeponera coarctata", list));
            assertNull(Taxon.knownSubfamilyOf("formicinaecamponotus x", list));
        }
    }

    // ------------------------------------------------------------------ curate

    public void testSpellingGuessOnlyWhenCloseAndUnique() {
        List<String> c = Arrays.asList("Pheidole rugosa", "Pheidole rufula", "Pheidole rugosa abc");
        assertEquals("one edit", 0, SpeciesListValidator.closeSpelling("Pheidole rugosaa", c));
        assertEquals("four edits are too many", -1, SpeciesListValidator.closeSpelling("Pheidole rubisa", Arrays.asList("Pheidole rufula")));
        assertEquals("a subspecies is not offered for a species", -1,
            SpeciesListValidator.closeSpelling("Pheidole rugosa ab", Arrays.asList("Pheidole rugosa")));
        assertEquals("a tie offers nothing", -1, SpeciesListValidator.closeSpelling("Pheidole aaa", Arrays.asList("Pheidole aab", "Pheidole aac")));
        assertEquals(2, SpeciesListValidator.MAX_SPELLING_DISTANCE);
    }

    public void testInactiveAccountsRecheckListsToo() {
        assertTrue(InactiveAccountDb.STILL_REGULAR.contains("login_project"));
        assertTrue(InactiveAccountDb.STILL_REGULAR.contains("login_country"));
        assertTrue(InactiveAccountDb.STILL_REGULAR.startsWith("inactive = 0"));
    }

    // ------------------------------------------------------------------ images, nightly, upload

    public void testReplacedImageKeepsItsOwners() {
        assertEquals(new HashSet<>(Arrays.asList(16)), ImageDb.ownerGroups(new HashSet<>(Arrays.asList(16)), 1));
        assertEquals(new HashSet<>(Arrays.asList(1)), ImageDb.ownerGroups(new HashSet<>(), 1));
        assertEquals(new HashSet<>(Arrays.asList(1)), ImageDb.ownerGroups(null, 1));
        assertEquals(new HashSet<>(Arrays.asList(4, 16)), ImageDb.ownerGroups(new HashSet<>(Arrays.asList(4, 16, 0)), 1));
    }

    public void testRunOfTheSetsIsClaimedOnce() {
        assertTrue(Scheduler.claimRun());
        try {
            assertFalse("a second run can not start", Scheduler.claimRun());
            assertTrue(Scheduler.isRunning());
        } finally {
            Scheduler.endRun();
        }
        assertFalse(Scheduler.isRunning());
        assertTrue(Scheduler.claimRun());
        Scheduler.endRun();
    }

    public void testFailedJobDoesNotSayNothingChanged() {
        String m = UtilDataAction.failureMessage("set2", "nightly", new SQLException("lock"));
        assertFalse(m, m.contains("No changes made"));
        assertTrue(m, m.contains("may have saved"));
        assertNotNull("the Scheduler still finds it", Scheduler.setFailure("<div>" + m + "</div>"));
    }

    public void testCarriageReturnFragmentsByFileLine() throws Exception {
        assertEquals(2, LineNumMgr.fileLine(1));
        java.lang.reflect.Field f = LineNumMgr.class.getDeclaredField("badCarriageReturnLines");
        f.setAccessible(true);
        if (f.get(null) == null) f.set(null, new ArrayList<Integer>());   // as LineNumMgr.init leaves it
        LineNumMgr.setIsBadCarriageReturnLine(900102);
        assertFalse("data line 900101 is file line 900102", LineNumMgr.isGoodCarriageReturnLine(LineNumMgr.fileLine(900101)));
        assertTrue(LineNumMgr.isGoodCarriageReturnLine(LineNumMgr.fileLine(900102)));
    }

    public void testUploadErrorsAreEscaped() {
        new MessageMgr();
        MessageMgr.addToErrors("e:duplicate query:insert ... '<img src=x onerror=alert(1)>'");
        String r = MessageMgr.getErrorsReport();
        assertFalse(r, r.contains("<img"));
        assertTrue(r, r.contains("&lt;img"));
    }

    // ------------------------------------------------------------------ places

    private static SpeciesListable list(String key, String name) {
        return new SpeciesListable() {
            public String getType() { return null; }
            public void setType(String type) {}
            public String getKey() { return key; }
            public String getName() { return name; }
            public String getTitle() { return name; }
            public boolean getIsUseChildren() { return false; }
            public String getOverviewLink() { return ""; }
            public String getListLink() { return ""; }
        };
    }

    public void testRangeReportsByPlaceId() {
        assertEquals(" and g.id = 123", QueriesWithParams.placeCondition("Distrito Federal", 123));
        String q = QueriesWithParams.geolocaleSpeciesListWithRangeData("Distrito Federal", "adm1", 123).getQuery();
        assertTrue(q, q.contains("g.id = 123") && !q.contains("g.name = 'Distrito Federal'"));
        q = QueriesWithParams.geolocaleSpeciesListRangeSummary("Distrito Federal", "adm1", 123).getQuery();
        assertTrue(q, q.contains("g.id = 123") && !q.contains("g.name ="));
        assertTrue(QueriesWithParams.geolocaleSpeciesListWithRangeData("O'Higgins", "adm1").getQuery().contains("'O''Higgins'"));
    }

    public void testCentroidInParentheses() {
        assertEquals(37.1679, LocalityOverview.getDoubleListVal("(37.1679, -95.8450)", 1), 1e-9);
        assertEquals(-95.845, LocalityOverview.getDoubleListVal("(37.1679, -95.8450)", 2), 1e-9);
        assertEquals(43.5, LocalityOverview.getDoubleListVal("43.5, -12.1, 50.0, 1.0", 1), 1e-9);
    }

    // ------------------------------------------------------------------ dates

    public void testDashedNumericDates() {
        assertEquals("2012-10-12", DateUtil.getConstructDateStr("10-12-2012"));
        assertEquals("2012-12-25", DateUtil.getConstructDateStr("25-12-2012"));
        assertTrue(DateUtil.isAmbiguousMonthDay("05-04-2013"));
        assertFalse(DateUtil.isAmbiguousMonthDay("25-12-2012"));
        assertEquals("ranges unchanged", "2010-02-08", DateUtil.getConstructDateStr("8-11 Feb 2010"));
        assertEquals("2010-01-01", DateUtil.getConstructDateStr("2010-2011"));
        assertEquals("2012-03-27", DateUtil.getConstructDateStr("2012-03-27"));
    }

    // ------------------------------------------------------------------ CREATE TABLE only when missing

    /** A connection whose information_schema answer is tableThere; records every statement run. */
    private static Connection schemaConnection(boolean tableThere, List<String> ran) {
        ClassLoader cl = AuditTlFixesTest.class.getClassLoader();
        ResultSet rs = (ResultSet) Proxy.newProxyInstance(cl, new Class<?>[]{ResultSet.class}, (p, m, a) -> {
            switch (m.getName()) {
                case "next": return tableThere;
                case "close": return null;
                default: return m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
            }
        });
        PreparedStatement ps = (PreparedStatement) Proxy.newProxyInstance(cl, new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
            if (m.getName().equals("executeQuery")) return rs;
            return m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
        });
        Statement st = (Statement) Proxy.newProxyInstance(cl, new Class<?>[]{Statement.class}, (p, m, a) -> {
            if (m.getName().equals("executeQuery")) {
                ran.add((String) a[0]);
                return rs;
            }
            if (m.getName().startsWith("execute")) {
                ran.add((String) a[0]);
                return m.getReturnType() == int.class ? 0 : m.getReturnType() == boolean.class ? false : null;
            }
            return m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
        });
        return (Connection) Proxy.newProxyInstance(cl, new Class<?>[]{Connection.class}, (p, m, a) -> {
            switch (m.getName()) {
                case "prepareStatement": ran.add("prepare: " + a[0]); return ps;
                case "createStatement": return st;
                default: return m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
            }
        });
    }

    public void testCreateTableOnlyWhenMissing() throws Exception {
        List<String> ran = new ArrayList<>();
        GroupCountDb.forgetTable();
        GroupCountDb.ensureTable(schemaConnection(true, ran));
        for (String s : ran) assertFalse("no CREATE when the table is there: " + s, s.toUpperCase().contains("CREATE TABLE"));
        ran.clear();
        GroupCountDb.forgetTable();
        GroupCountDb.ensureTable(schemaConnection(false, ran));
        boolean created = false;
        for (String s : ran) created |= s.toUpperCase().contains("CREATE TABLE");
        assertTrue("created when missing: " + ran, created);
        GroupCountDb.forgetTable();
    }

    // ------------------------------------------------------------------ upload lists, introduced rule (2026-11-02)

    public void testCuratorsListOnlyTheirOwnGroupsUploads() {
        // Admin: what was asked (0 = every group).
        assertEquals(0, ListUploadsAction.scopeGroupId(true, 5, 0));
        assertEquals(7, ListUploadsAction.scopeGroupId(true, 5, 7));
        // Curator: their own group, whatever was asked.
        assertEquals(5, ListUploadsAction.scopeGroupId(false, 5, 0));
        assertEquals(5, ListUploadsAction.scopeGroupId(false, 5, 7));
        assertEquals(5, ListUploadsAction.scopeGroupId(false, 5, 5));
        // A login without a group sees no upload (-1 matches no group_id).
        assertEquals(-1, ListUploadsAction.scopeGroupId(false, 0, 0));
        assertEquals(-1, ListUploadsAction.scopeGroupId(false, -1, 3));
    }

    public void testNoBioregionIsNotIntroduced() {
        String map = "Neotropical:true Afrotropical:false Malagasy:false";
        assertTrue(TaxonPropMgr.isIntroducedIn(map, "Malagasy"));
        assertTrue("a bioregion missing from the map is not native", TaxonPropMgr.isIntroducedIn(map, "Palearctic"));
        assertFalse(TaxonPropMgr.isIntroducedIn(map, "Neotropical"));
        // No bioregion, blank, or a name that is not a bioregion: not introduced (was introduced in the nightly flags).
        assertFalse(TaxonPropMgr.isIntroducedIn(map, null));
        assertFalse(TaxonPropMgr.isIntroducedIn(map, ""));
        assertFalse(TaxonPropMgr.isIntroducedIn(map, "Malgasy"));
        assertFalse(TaxonPropMgr.isIntroducedIn(map, "malagasy"));
        // No introduced map: never introduced.
        assertFalse(TaxonPropMgr.isIntroducedIn(null, "Malagasy"));
        assertFalse(TaxonPropMgr.isIntroducedIn(" ", "Malagasy"));
        // A map with no native bioregion: introduced everywhere (the species list said native before).
        assertTrue(TaxonPropMgr.isIntroducedIn("Malagasy:false", "Nearctic"));
    }
}
