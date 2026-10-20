package test.org.calacademy.antweb.security;

import junit.framework.TestCase;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.upload.UploadAction;
import org.calacademy.antweb.util.HtmlSanitizer;
import org.calacademy.antweb.util.LoginMgr;
import org.calacademy.antweb.util.NamedQuery;
import org.calacademy.antweb.util.Queries;
import org.calacademy.antweb.util.QueryAction;
import org.calacademy.antweb.util.QueryManager;

/**
 * Curate page cleanup (2026-10): named queries only read data and are for administrators except
 * the few public pages link to; developer rights are the admin role; removed upload tools; the
 * site warning keeps only safe HTML. Runs in CI inside the antweb image (.github/workflows/tests.yaml).
 */
public class CurateCleanupTest extends TestCase {

    public void testNamedQueriesOnlyRead() {
        assertTrue(QueryManager.isReadOnly("select taxon_name from taxon"));
        assertTrue(QueryManager.isReadOnly("  SELECT count(*)\nfrom specimen"));
        assertTrue(QueryManager.isReadOnly("(select 1) union (select 2)"));
        assertFalse(QueryManager.isReadOnly("delete from geolocale_taxon where taxon_name not in (select taxon_name from taxon)"));
        assertFalse(QueryManager.isReadOnly("update taxon set status = 'x'"));
        assertFalse(QueryManager.isReadOnly("insert into taxon values (1)"));
        assertFalse(QueryManager.isReadOnly("select 1; delete from taxon"));
        assertFalse(QueryManager.isReadOnly("select * from taxon into outfile '/tmp/x'"));
        assertFalse(QueryManager.isReadOnly("drop table taxon"));
        assertFalse(QueryManager.isReadOnly(null));
    }

    public void testEveryNamedQueryReads() {
        for (NamedQuery q : Queries.getNamedQueries()) {
            assertTrue(q.getName(), QueryManager.isReadOnly(q.getQuery()));
        }
        assertNull(Queries.getNamedQuery("deleteGeolocaleListMorphotaxa"));
        assertNull(Queries.getNamedQuery("deleteGeolocaleTaxaWithoutTaxon"));
    }

    public void testNamedQueryAccess() {
        assertEquals("public", QueryAction.namedQueryAccess("worldantsUploads"));
        assertEquals("public", QueryAction.namedQueryAccess("bioregionSpeciesListWithRangeData"));
        assertEquals("curator", QueryAction.namedQueryAccess("geolocaleSpeciesListWithRangeData"));
        for (String n : new String[]{"lastSpecimenUpload", "adminAlerts", "geolocaleListMorphotaxa",
                "geolocaleTaxaWithoutTaxon", "specimenOfArtist", "noSuchQuery", null}) {
            assertEquals(String.valueOf(n), "admin", QueryAction.namedQueryAccess(n));
        }
    }

    public void testDeveloperIsTheAdminRole() {
        Login admin = new Login();
        admin.setId(1234);
        admin.setIsAdmin(true);
        Login curator = new Login();
        curator.setId(22);
        curator.setIsAdmin(false);
        assertTrue(LoginMgr.isDeveloper(admin));
        assertTrue(admin.isDeveloper());
        assertFalse(LoginMgr.isDeveloper(curator));
        assertFalse(curator.isDeveloper());
        assertFalse(LoginMgr.isDeveloper((Login) null));
    }

    public void testRemovedUploadTools() {
        for (String a : new String[]{"allSpecimenFiles", "GBIFUpload", "reloadSpeciesLists", "toggleDownTime", "genAll"}) {
            assertTrue(a, UploadAction.isRemovedAction(a));
            assertFalse(a, UploadAction.isAdminOnlyAction(a));
        }
        assertFalse(UploadAction.isRemovedAction("taxonWorksUpload"));
        assertTrue(UploadAction.isAdminOnlyAction("runStatistics"));
    }

    public void testMuseumRecalcOnlyForOwnUpload() {
        Login curator = new Login();
        curator.setId(4321);
        curator.setIsAdmin(false);
        assertTrue(UploadAction.mayRecalcMuseums(curator, 4321));
        assertFalse(UploadAction.mayRecalcMuseums(curator, 1234));
        assertFalse(UploadAction.mayRecalcMuseums(curator, null));
        assertFalse(UploadAction.mayRecalcMuseums(null, 4321));
        assertFalse(UploadAction.isAdminOnlyAction("museumCalc"));
    }

    public void testSiteWarningKeepsOnlySafeHtml() {
        String kept = HtmlSanitizer.sanitize("<br><font color=lightgreen>Maintenance tonight</font>");
        assertTrue(kept, kept.contains("<font color=\"lightgreen\">Maintenance tonight</font>"));
        String bad = HtmlSanitizer.sanitize("Hi<script>alert(1)</script><img src=x onerror=alert(2)>"
            + "<a href=\"javascript:alert(3)\">x</a>");
        assertFalse(bad, bad.toLowerCase().contains("<script"));
        assertFalse(bad, bad.toLowerCase().contains("onerror"));
        assertFalse(bad, bad.toLowerCase().contains("javascript:"));
    }
}
