package test.org.calacademy.antweb.security;

import java.util.ArrayList;

import junit.framework.TestCase;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.SpeciesListable;
import org.calacademy.antweb.util.DBStatus;
import org.calacademy.antweb.util.LoginNames;
import org.calacademy.antweb.util.SpeciesListAccess;
import org.calacademy.antweb.util.SpecimenFetch;

/** Checks added with security batch 5 (review of 2026-10-17). */
public class SecurityBatch5Test extends TestCase {

    // Stand-in for Project / Geolocale (whose constructors need the server's configuration).
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

    private static Login curator(boolean admin, String projectName, int countryId, String countryName) {
        Login l = new Login();
        l.setId(42);
        l.setGroupId(16);
        l.setIsAdmin(admin);
        ArrayList<SpeciesListable> projects = new ArrayList<>();
        if (projectName != null) projects.add(list(projectName, projectName));
        l.setProjects(projects);
        ArrayList<SpeciesListable> geolocales = new ArrayList<>();
        if (countryName != null) geolocales.add(list("" + countryId, countryName));
        l.setGeolocales(geolocales);
        return l;
    }

    /**
     * Brian Fisher, 2026-11-02 ("encourage participation"): any curator may edit any place's or project's species list;
     * a login that is not a curator none; the system lists (worldants, allantwebants) administrators only. Editing a
     * project itself (EditProjectAction) stays with the curators it is assigned to (canEditProject).
     */
    public void testCuratorEditsEveryList() {
        Login c = curator(false, "madants", 392, "Madagascar");
        assertTrue(SpeciesListAccess.canEdit(c, "madants"));
        assertTrue(SpeciesListAccess.canEdit(c, "392"));
        assertTrue(SpeciesListAccess.canEdit(c, "Madagascar"));
        assertTrue("a list not assigned to the curator", SpeciesListAccess.canEdit(c, "fijiants"));
        assertTrue(SpeciesListAccess.canEdit(c, "393"));
        assertFalse("a system list", SpeciesListAccess.canEdit(c, "worldants"));
        assertFalse(SpeciesListAccess.canEdit(c, "allantwebants"));
        assertFalse(SpeciesListAccess.canEdit(c, null));
        assertFalse(SpeciesListAccess.canEdit(null, "madants"));
        Login visitor = new Login();
        visitor.setId(43);
        assertFalse("not a curator: no list", SpeciesListAccess.canEdit(visitor, "madants"));
        // the project itself: only the projects assigned to the curator
        assertTrue(SpeciesListAccess.canEditProject(c, "madants"));
        assertFalse(SpeciesListAccess.canEditProject(c, "fijiants"));
        assertFalse(SpeciesListAccess.canEditProject(null, "madants"));
    }

    public void testAdminEditsAnyList() {
        assertTrue(SpeciesListAccess.canEdit(curator(true, null, 0, null), "fijiants"));
        assertTrue(SpeciesListAccess.canEdit(curator(true, null, 0, null), "worldants"));
        assertTrue(SpeciesListAccess.canEditProject(curator(true, null, 0, null), "fijiants"));
    }

    public void testNone() {
        assertTrue(SpeciesListAccess.isNone(null));
        assertTrue(SpeciesListAccess.isNone("none"));
        assertFalse(SpeciesListAccess.isNone("madants"));
    }

    public void testUserNames() {
        assertTrue(LoginNames.isValidName("bfisher"));
        assertTrue(LoginNames.isValidName("b.fisher@calacademy.org"));
        assertTrue(LoginNames.isValidName("Rakotoarijaona Harifetra"));
        assertTrue(LoginNames.isValidName("O'Brien"));
        assertTrue(LoginNames.isValidName("Müller-Lüdenscheidt"));
        assertTrue(LoginNames.isValidName("田中"));
        assertFalse(LoginNames.isValidName("<script>x</script>"));
        assertFalse(LoginNames.isValidName("a\"b"));
        assertFalse(LoginNames.isValidName("a&b"));
        assertFalse(LoginNames.isValidName(""));
        assertFalse(LoginNames.isValidName("   "));
    }

    public void testNameChecksOnlyChangedValues() {
        Login stored = new Login();
        stored.setName("legacy<name>");
        stored.setFirstName("Ann");
        stored.setLastName("Lee");
        stored.setEmail("ann@example.org");
        // Unchanged legacy value: still saves.
        assertNull(LoginNames.problem("legacy<name>", "Ann", "Lee", "ann@example.org", stored));
        assertNotNull(LoginNames.problem("<img src=x>", "Ann", "Lee", "ann@example.org", stored));
        assertNotNull(LoginNames.problem("legacy<name>", "<b>", "Lee", "ann@example.org", stored));
        assertNotNull(LoginNames.problem("legacy<name>", "Ann", "Lee", "not-an-email", stored));
        assertNull(LoginNames.problem("newname", "", "", "", null));
    }

    public void testSpecimenShortLinkRedirects() {
        assertEquals("/specimen.do?name=CASENT0106322", SpecimenFetch.target("", "/CASENT0106322"));
        assertEquals("/antweb/specimen.do?name=jtl028522-01", SpecimenFetch.target("/antweb", "/jtl028522-01"));
        assertNull(SpecimenFetch.target("", "/<script>alert(1)</script>"));
        assertNull(SpecimenFetch.target("", "/a b"));
        assertNull(SpecimenFetch.target("", "/"));
        assertNull(SpecimenFetch.target("", null));
    }

    public void testServerBusyEscaping() {
        assertEquals("a<br>&lt;script&gt;", DBStatus.escBr("a<br><script>"));
        assertEquals("null", DBStatus.escBr(null));
    }
}
