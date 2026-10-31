package test.org.calacademy.antweb;

import java.util.Arrays;
import java.util.Collections;

import junit.framework.TestCase;

import org.calacademy.antweb.Museum;
import org.calacademy.antweb.PlaceTaxonNotes;
import org.calacademy.antweb.Project;
import org.calacademy.antweb.Rank;
import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Bioregion;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.geolocale.Subregion;
import org.calacademy.antweb.home.StatisticsDb;

/**
 * The notes of taxon pages seen in a place (Brian Fisher, 2026-10-29; PlaceTaxonNotes): the indet line of a genus page
 * ("Not identified to species: Lividopone (indet), 45 specimens (4 imaged)") and the not-recorded notice ("Lividopone is
 * not recorded from Afrotropical."). No database: the text, the links, which places select which specimens and when the
 * notice can apply. The queries run against MySQL in PlaceTaxonNotesDbTest. Runs in CI (.github/workflows/tests.yaml).
 */
public class PlaceTaxonNotesTest extends TestCase {

    private static Country mozambique() {
        Country c = new Country();
        c.setId(40);
        c.setName("Mozambique");
        return c;
    }

    private static Bioregion afrotropical() {
        Bioregion b = new Bioregion();
        b.setName("Afrotropical");
        return b;
    }

    public void testIndetNameIsTheGenusInParentheses() {
        assertEquals("dorylinaelividopone (indet)", PlaceTaxonNotes.indetName("dorylinaelividopone"));
        assertNull(PlaceTaxonNotes.indetName(null));
    }

    public void testIndetLineTextAndLink() {
        String url = PlaceTaxonNotes.indetUrl("images.do", "dorylinae", "lividopone", mozambique());
        assertTrue(url, url.endsWith("/images.do?subfamily=dorylinae&genus=lividopone&species=%28indet%29&rank=species&countryName=Mozambique"));
        String line = PlaceTaxonNotes.indetLineHtml("Lividopone", 45, 4, url);
        assertTrue(line, line.contains("Not identified to species: <a href=\""));
        assertTrue(line, line.contains(">Lividopone (indet)</a>, 45 specimens (4 imaged)</div>"));
        assertTrue(PlaceTaxonNotes.indetLineHtml("Lividopone", 1, 0, url).contains("1 specimen (0 imaged)"));
        assertNull("no line when the place has none", PlaceTaxonNotes.indetLineHtml("Lividopone", 0, 0, url));
        // the numbers are not in class "numbers": the page's number script blanks a 0
        assertFalse(line.contains("numbers"));
    }

    public void testAllOfAntWebLinksToAllantwebants() {
        String url = PlaceTaxonNotes.indetUrl("browse.do", "dorylinae", "lividopone", new Project(Project.ALLANTWEBANTS));
        assertTrue(url, url.endsWith("&rank=species&project=allantwebants"));
        assertEquals("project=allantwebants", PlaceTaxonNotes.placeParams(new Project(Project.WORLDANTS)));
    }

    public void testPlaceParamsAreEncoded() {
        Adm1 a = new Adm1();
        a.setName("Cabo Delgado");
        a.setParent("Mozambique");
        assertEquals("adm1Name=Cabo+Delgado&countryName=Mozambique", PlaceTaxonNotes.placeParams(a));
        assertEquals("Cabo Delgado, Mozambique", PlaceTaxonNotes.placeName(a));
    }

    public void testWhichSpecimensAPlaceSelects() {
        assertEquals("", PlaceTaxonNotes.placeSpecimenCriteria(new Project(Project.ALLANTWEBANTS)));
        assertNull("a curated project selects no specimens", PlaceTaxonNotes.placeSpecimenCriteria(new Project("madants")));
        assertTrue(PlaceTaxonNotes.placeSpecimenCriteria(mozambique()).contains("specimen.country = 'Mozambique'"));
        assertTrue(PlaceTaxonNotes.placeSpecimenCriteria(afrotropical()).contains("specimen.bioregion = 'Afrotropical'"));
        Museum m = new Museum();
        m.setCode("CASC");
        assertTrue(PlaceTaxonNotes.placeSpecimenCriteria(m).contains("specimen.museum = 'CASC'"));
        Subregion s = new Subregion();
        s.setId(3);
        s.setName("Indian Ocean");
        String sub = PlaceTaxonNotes.placeSpecimenCriteria(s);
        assertTrue("a subregion: the specimens of its countries: " + sub, sub.contains("r.id = 3") && sub.contains("specimen.island_country in"));
    }

    public void testIndetCountQuery() {
        String q = PlaceTaxonNotes.indetCountSql(" and specimen.country = 'Mozambique'");
        assertTrue(q, q.contains("specimen.taxon_name = ?"));
        assertTrue("imaged: an image of the ant, labels do not count", q.contains(StatisticsDb.ANT_IMAGE));
        assertTrue(q.endsWith(" and specimen.country = 'Mozambique'"));
    }

    public void testNoticeAppliesToGenusAndSpeciesPagesInAPlace() {
        assertTrue(PlaceTaxonNotes.placeHasList(afrotropical(), Rank.GENUS));
        assertTrue(PlaceTaxonNotes.placeHasList(mozambique(), Rank.SPECIES));
        assertTrue(PlaceTaxonNotes.placeHasList(new Project("madants"), Rank.SUBSPECIES));
        assertFalse("subfamily pages: not asked for", PlaceTaxonNotes.placeHasList(afrotropical(), Rank.SUBFAMILY));
        assertFalse("all of AntWeb is not a place", PlaceTaxonNotes.placeHasList(new Project(Project.ALLANTWEBANTS), Rank.GENUS));
        assertFalse("the AntCat list is not a place", PlaceTaxonNotes.placeHasList(new Project(Project.WORLDANTS), Rank.GENUS));
    }

    public void testNoticeText() {
        String html = PlaceTaxonNotes.notRecordedHtml("Lividopone", "Afrotropical", Arrays.asList("Malagasy"), "images.do",
            "subfamily=dorylinae&genus=lividopone&rank=genus");
        assertTrue(html, html.contains("Lividopone is not recorded from Afrotropical."));
        assertTrue(html, html.contains("Recorded from: <a href=\""));
        assertTrue(html, html.contains("/images.do?subfamily=dorylinae&amp;genus=lividopone&amp;rank=genus&amp;bioregionName=Malagasy\">Malagasy</a>."));
        assertTrue(html, html.contains("&amp;rank=genus&amp;project=allantwebants\">See Lividopone in all of AntWeb</a>"));
        String none = PlaceTaxonNotes.notRecordedHtml("Lividopone x", "Mozambique", Collections.emptyList(), "browse.do", "genus=lividopone");
        assertFalse("no bioregion lists it: no Recorded from", none.contains("Recorded from"));
        assertTrue(PlaceTaxonNotes.notRecordedHtml("<b>", "<i>", Arrays.asList("<s>"), "description.do", "x").indexOf("<b>") < 0);
    }

    public void testSpecimenOfTheTaxonInThePlace() {
        String q = PlaceTaxonNotes.placeSpecimenSql(" and specimen.country = 'Mozambique'");
        assertEquals("select 1 from specimen where (specimen.taxon_name = ? or specimen.taxon_name like ?)"
            + " and (specimen.flag is null or specimen.flag != 'red') and specimen.country = 'Mozambique' limit 1", q);
        assertEquals("dorylinaelividopone %", PlaceTaxonNotes.childrenPattern("dorylinaelividopone"));
        assertEquals("a morphogenus's _ is not a wildcard", "amblyoponinaeamblyoponine\\_genus8 %",
            PlaceTaxonNotes.childrenPattern("amblyoponinaeamblyoponine_genus8"));
    }
}
