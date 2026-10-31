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

    // ------------------------------------------------------------ specimens collected here not on the list (2026-10-30)

    private static PlaceTaxonNotes.OtherNames.Genus acropyga() {
        PlaceTaxonNotes.OtherNames o = new PlaceTaxonNotes.OtherNames();
        o.add("formicinae", "acropyga", "formicinaeacropyga mz01", "mz01", null, "species", 2);
        o.add("formicinae", "acropyga", "formicinaeacropyga (indet)", "(indet)", null, "species", 1);
        o.add("myrmicinae", "pheidole", "myrmicinaepheidole a x", "a", "x", "subspecies", 4);
        assertNull("no other names of a genus not in the result", o.genus("formicinae", "camponotus"));
        assertEquals(4, o.genus("MYRMICINAE", "Pheidole").specimens);   // the row's subfamily and genus, any case
        return o.genus("formicinae", "acropyga");
    }

    public void testGenusRowWording() {
        assertEquals("1 specimen collected here", PlaceTaxonNotes.collectedHereText(1));
        assertEquals("3 specimens collected here", PlaceTaxonNotes.collectedHereText(3));
        assertEquals("+ 1 specimen of other names", PlaceTaxonNotes.otherNamesText(1));
        assertEquals("+ 2 specimens of other names", PlaceTaxonNotes.otherNamesText(2));
        String row = PlaceTaxonNotes.genusRowNoSpeciesHtml(3, "/advancedSearch.do?country=Mozambique&genus=acropyga");
        assertEquals("No species on the list &middot; <a rel=\"nofollow\" class=\"other-names\" data-other-specimens=\"3\""
            + " href=\"/advancedSearch.do?country=Mozambique&amp;genus=acropyga\">3 specimens collected here</a>", row);
        assertEquals("a bot: the text, no search link", "No species on the list &middot; <span class=\"other-names\""
            + " data-other-specimens=\"1\">1 specimen collected here</span>", PlaceTaxonNotes.genusRowNoSpeciesHtml(1, null));
        String other = PlaceTaxonNotes.genusRowOtherHtml(2, "/s?g=x");
        assertTrue(other, other.startsWith("<br><small><a rel=\"nofollow\" class=\"other-names\" data-other-specimens=\"2\""));
        assertTrue(other, other.endsWith(">+ 2 specimens of other names</a></small>"));
    }

    public void testOtherNamesSearchAndLine() {
        PlaceTaxonNotes.OtherNames.Genus g = acropyga();
        assertEquals(3, g.specimens);
        assertEquals("Acropyga", g.displayName());
        assertEquals("no species on the list: every specimen of the genus", "genusSearchType=equals&genus=acropyga",
            PlaceTaxonNotes.searchParams(g, true));
        assertEquals("species on the list: only the other names", "genusSearchType=equals&genus=acropyga&speciesSearchType=equals&species=mz01%2C+%28indet%29",
            PlaceTaxonNotes.searchParams(g, false));
        String line = PlaceTaxonNotes.otherNamesLineHtml(g, "/advancedSearch.do?country=Mozambique&genus=acropyga", "browse.do", mozambique());
        assertTrue(line, line.startsWith("<div id=\"genus_other_names\" class=\"genus_indet\" data-other-specimens=\"3\""));
        assertTrue(line, line.contains("Specimens collected here not on the species list: <a rel=\"nofollow\" href=\"/advancedSearch.do?country=Mozambique&amp;genus=acropyga\">3</a> (names: "));
        assertTrue(line, line.contains("/browse.do?subfamily=formicinae&amp;genus=acropyga&amp;species=mz01&amp;rank=species&amp;countryName=Mozambique\">Acropyga mz01</a>, "));
        assertTrue(line, line.contains("species=%28indet%29&amp;rank=species&amp;countryName=Mozambique\">Acropyga (indet)</a>)</div>"));
        assertNull(PlaceTaxonNotes.otherNamesLineHtml(null, null, "browse.do", mozambique()));
        // a subspecies links to its own page
        PlaceTaxonNotes.OtherNames o = new PlaceTaxonNotes.OtherNames();
        o.add("myrmicinae", "pheidole", "myrmicinaepheidole a x", "a", "x", "subspecies", 4);
        PlaceTaxonNotes.OtherNames.Genus p = o.genus("myrmicinae", "pheidole");
        assertEquals("Pheidole a x", PlaceTaxonNotes.nameDisplay(p, p.names.get(0)));
        assertTrue(PlaceTaxonNotes.nameUrl("images.do", p, p.names.get(0), afrotropical()).endsWith(
            "/images.do?subfamily=myrmicinae&genus=pheidole&species=a&subspecies=x&rank=subspecies&bioregionName=Afrotropical"));
        // many names: the first ones and "and N more"; more than the search takes: the genus alone
        PlaceTaxonNotes.OtherNames many = new PlaceTaxonNotes.OtherNames();
        for (int i = 0; i < PlaceTaxonNotes.MAX_SEARCH_NAMES + 1; i++) many.add("formicinae", "acropyga", "n" + i, "sp" + i, null, "species", 1);
        PlaceTaxonNotes.OtherNames.Genus m = many.genus("formicinae", "acropyga");
        assertEquals("genusSearchType=equals&genus=acropyga", PlaceTaxonNotes.searchParams(m, false));
        String ml = PlaceTaxonNotes.otherNamesLineHtml(m, null, "browse.do", mozambique());
        assertTrue(ml, ml.contains("not on the species list: " + (PlaceTaxonNotes.MAX_SEARCH_NAMES + 1) + " (names: "));
        assertTrue(ml, ml.endsWith(" and " + (PlaceTaxonNotes.MAX_SEARCH_NAMES + 1 - PlaceTaxonNotes.MAX_LINE_NAMES) + " more)</div>"));
    }

    public void testNamesFromHeadingAndLabels() {
        assertEquals("54 Valid species on the list or collected in Mozambique",
            PlaceTaxonNotes.withSpecimensHeading("54 Valid species (of 1,203 species and morphospecies)", "Mozambique"));
        assertEquals("289 Species (all names) on the list or collected in Mozambique",
            PlaceTaxonNotes.withSpecimensHeading("289 Species (all names)", "Mozambique"));
        assertEquals("Place list", PlaceTaxonNotes.namesFromLabel(false));
        assertEquals("Specimens collected here", PlaceTaxonNotes.namesFromLabel(true));
    }

    public void testOtherNamesOnlyWherePlacesSelectSpecimens() throws Exception {
        assertNull("all of AntWeb: no place list", PlaceTaxonNotes.loadOtherNames(null, new Project(Project.ALLANTWEBANTS), null, null));
        assertNull("a curated project selects no specimens", PlaceTaxonNotes.loadOtherNames(null, new Project("madants"), null, null));
        assertNull(PlaceTaxonNotes.placeSearchUrl(new Project(Project.ALLANTWEBANTS)));
        Country moz = mozambique();
        moz.setParent("Eastern Africa");
        String url = PlaceTaxonNotes.placeSearchUrl(moz);
        assertTrue(url, url.endsWith("/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=formicidae&subregion=Eastern Africa&country=Mozambique&resultRank=specimen"));
        String q = PlaceTaxonNotes.otherNamesSql("geolocale", " and specimen.country = 'Mozambique'", false, true);
        assertTrue(q, q.contains("(specimen.flag is null or specimen.flag != 'red') and specimen.genus = ? and specimen.country = 'Mozambique' group by specimen.taxon_name"));
        assertTrue(q, q.contains("and not (t.status in ('valid', 'morphotaxon') and exists (select 1 from geolocale_taxon l where l.geolocale_id = ? and l.taxon_name = t.taxon_name))"));
        assertFalse("no subfamily parameter", q.contains("t.subfamily = ?"));
    }
}
