package test.org.calacademy.antweb.curate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.curate.geolocale.EditGeolocaleAction;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.util.Queries;
import org.calacademy.antweb.util.QueryManager;

/**
 * Review of the Manager Tools on the Curate page (2026-10-21, docs/guides/manager-tools.md).
 *
 * - Edit Geolocale: the menus' "None" (and an empty or "null" value) is saved as no value, never as the
 *   text "none"; a stored "none" bioregion reads as no bioregion (it made the specimens of that country
 *   lose their bioregion at upload).
 * - Query Manager: result values are shown as text; only the links the queries build to AntWeb pages stay links.
 * - Every named query that a manager page links by name exists.
 * - Each manager page explains itself in one short block (class mgr_help) and prints no request value unescaped.
 *
 * Run with -Dantweb.web=<the web/ folder> and AntwebResources on the class path (as the other tests of this step).
 */
public class ManagerToolsTest extends TestCase {

    public void testNoneMeansNoValue() {
        assertNull(EditGeolocaleAction.noneToNull(null));
        assertNull(EditGeolocaleAction.noneToNull(""));
        assertNull(EditGeolocaleAction.noneToNull("  "));
        assertNull(EditGeolocaleAction.noneToNull("none"));
        assertNull(EditGeolocaleAction.noneToNull("None"));
        assertNull(EditGeolocaleAction.noneToNull("null"));
        assertEquals("Malagasy", EditGeolocaleAction.noneToNull("Malagasy"));
        assertEquals("Eastern Africa", EditGeolocaleAction.noneToNull(" Eastern Africa "));
    }

    public void testStoredNoneBioregionIsNoBioregion() {
        Geolocale g = new Geolocale();
        g.setBioregion("Malagasy");
        g.setAltBioregion("none");
        assertNull("a stored 'none' is no alternate bioregion", g.getAltBioregion());
        assertEquals("one bioregion: specimens get it at upload", "Malagasy", g.getTheOneBioregion());
        g.setAltBioregion("Afrotropical");
        assertNull("two bioregions: none is chosen from the place", g.getTheOneBioregion());
        g.setBioregion("none");
        assertNull(g.getBioregion());
    }

    public void testMuseumPageTextIsKeyedByCode() {
        // Museum pages load their text by code; the editor must read, write and key drafts by code too.
        org.calacademy.antweb.Museum m = new org.calacademy.antweb.Museum();
        m.setCode("ABS");
        m.setName("ABS, Lake Placid");
        assertEquals("ABS", org.calacademy.antweb.home.DescEditDb.objectKey(m));
        assertEquals("object:ABS", org.calacademy.antweb.DescEditSafety.pageKey(m));
        Geolocale g = new Geolocale();
        g.setName("Madagascar");
        assertEquals("places stay keyed by name", "Madagascar", org.calacademy.antweb.home.DescEditDb.objectKey(g));
    }

    public void testQueryCellsAreTextExceptAntwebLinks() {
        String app = "https://www.antweb.org";
        assertEquals("null", QueryManager.cell(null, app));
        assertEquals("a &lt;b&gt; &amp; &#39;c&#39;", QueryManager.cell("a <b> & 'c'", app));
        String link = "<a href='https://www.antweb.org/description.do?taxonName=myrmicinaepheidole'>myrmicinaepheidole</a>";
        assertEquals("a link the query builds stays a link", link, QueryManager.cell(link, app));
        String unquoted = "<a href=https://www.antweb.org/specimenImages.do?name=casent0001>link</a>";
        assertEquals(unquoted, QueryManager.cell(unquoted, app));
        String other = "<a href='https://example.org/x'>x</a>";
        assertEquals("a link elsewhere is text", "&lt;a href=&#39;https://example.org/x&#39;&gt;x&lt;/a&gt;", QueryManager.cell(other, app));
        String broken = "<a href='https://www.antweb.org/description.do?taxonName=x' onmouseover='y'>x</a>";
        assertTrue("an attribute after the href is text", QueryManager.cell(broken, app).startsWith("&lt;a "));
        String script = "<a href='https://www.antweb.org/x'><script>y</script></a>";
        assertTrue(QueryManager.cell(script, app).startsWith("&lt;a "));
    }

    public void testQueryHeadingsAreText() {
        assertEquals("<th>Count</th><th>a&lt;b &amp; &quot;c&quot;</th>",
            org.calacademy.antweb.util.NamedQuery.headerHtml(new String[] {"Count", "a<b & \"c\""}));
    }

    public void testLinkedNamedQueriesExist() {
        List<String> names = new ArrayList<>();
        names.addAll(Queries.getGeolocaleQueries());   // Geolocale Manager, Relevant Queries
        names.addAll(Queries.getLoginQueries());
        names.addAll(Queries.getGroupQueries());
        names.add("adminAlerts");                       // Admin Alert Manager, full list
        names.add("worldantsUploads");                  // Admin Alert Manager, Log Manager
        List<String> missing = new ArrayList<>();
        for (String name : names) if (Queries.getNamedQuery(name) == null) missing.add(name);
        assertTrue("linked named queries that do not exist: " + missing, missing.isEmpty());
    }

    static final String[] MANAGER_PAGES = {
        "curate/manageMuseums-body.jsp", "curate/viewMuseum-body.jsp",
        "curate/geolocale/geolocaleMgr-body.jsp", "curate/geolocale/editGeolocale-body.jsp", "curate/geolocale/adm1Mgr-body.jsp",
        "curate/projectMgr-body.jsp", "curate/project/editProject-body.jsp",
        "artists-body.jsp", "artist-body.jsp", "util/adminAlert.jsp",
        "curate/bioregionMapMgr-body.jsp", "curate/introducedMapMgr-body.jsp",
    };

    static String page(String name) throws Exception {
        String web = System.getProperty("antweb.web", "web");
        Path p = Paths.get(web, name);
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    public void testEveryManagerPageExplainsItself() throws Exception {
        for (String name : MANAGER_PAGES) {
            assertTrue(name + " has no explanation block", page(name).contains("class=\"mgr_help\""));
        }
    }

    public void testNoUnescapedValuesOnManagerPages() throws Exception {
        // Values that came from the request or the database, printed as they are, in the forms reviewed.
        String[] raw = {
            "<%= parentName %>", "<%= georank %>", "<%= adm1 %>", "<%= country %>", "<%= taxonName %>", "<%= orderBy %>",
            "<%= museum.getCode() %>", "<%= museum.getName() %>", "<%= thisMuseum.getCode() %>", "<%= thisMuseum.getName() %>",
            "<%= geolocale.getName() %>", "<%= geolocale.getParent() %>", "<%= geolocale.getCountry() %>", "<%= geolocale.getSource() %>",
            "<%= projectName %>", "<%= thisProject.getName() %>", "<%= thisProject.getMap() %>", "<%= thisProject.getCoords() %>",
            "<%= thisProject.getExtent() %>", "<%= locValue %>", "+ adminAlert.getAlert() +",
        };
        List<String> found = new ArrayList<>();
        for (String name : MANAGER_PAGES) {
            String text = page(name);
            for (String r : raw) if (text.contains(r)) found.add(name + ": " + r);
        }
        assertTrue("unescaped values: " + found, found.isEmpty());
    }

    public void testGeolocaleCreateNeverPostsTheTextNull() throws Exception {
        String text = page("curate/geolocale/geolocaleMgr-body.jsp");
        assertFalse("the Create form printed parent=\"null\" for a list without parent",
            text.contains("name=\"parent\" value=\"<%= parentName %>\""));
        assertTrue(text.contains("if (parentName != null) { %><input type=\"hidden\" name=\"parent\""));
    }

    public void testBioregionTotalsInColumnOrder() throws Exception {
        String text = page("curate/bioregionMapMgr-body.jsp");
        assertTrue("Totals follow the columns (... Nearctic, Neotropical, Oceania, Palearctic)",
            text.contains("<td><%= malaTot %></td><td><%= nearcTot %></td><td><%= neoTot %></td><td><%= oceanTot %></td><td><%= paleaTot %></td>"));
    }
}
