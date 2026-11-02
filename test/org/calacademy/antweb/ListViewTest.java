package test.org.calacademy.antweb;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.calacademy.antweb.Genus;
import org.calacademy.antweb.ListView;
import org.calacademy.antweb.Museum;
import org.calacademy.antweb.PlaceTaxonNotes;
import org.calacademy.antweb.Project;
import org.calacademy.antweb.StatusSet;
import org.calacademy.antweb.TaxaPage;
import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Bioregion;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.geolocale.Region;

/**
 * The views of lists (Brian Fisher, 2026-11-02; ListView), without a database: the vetted rule's SQL, which places add
 * the names of specimens collected there, the genus rows each view shows (a genus known only from specimens identified
 * to genus: All specimen names only), the Other specimens of a view, the source icons (specimens trump literature) and
 * the words of About this list. The queries run against MySQL in home.PlaceTaxonNotesDbTest. Runs in CI
 * (.github/workflows/tests.yaml).
 */
public class ListViewTest extends TestCase {

    private static Country mozambique() {
        Country moz = new Country();
        moz.setId(40);
        moz.setName("Mozambique");
        return moz;
    }

    public void testVettedIsACuratorsEntryOnAnyList() {
        String v = ListView.vettedCondition("taxon");
        assertTrue(v, v.contains("from geolocale_taxon vgt where vgt.taxon_name = taxon.taxon_name and vgt.source in ('curator', 'speciesList', 'speciesListTool', 'speciesListUpload')"));
        assertTrue(v, v.contains("from project vp join proj_taxon vpt on vpt.project_name = vp.project_name where vpt.taxon_name = taxon.taxon_name"));
        assertTrue("not the system lists: " + v, v.contains("vp.project_name not in ('worldants', 'allantwebants')"));
        assertFalse("not the proxy rows of parent regions", v.contains("proxy"));
    }

    public void testWhichPlacesAddSpecimenNames() {
        assertTrue(ListView.addsSpecimenNames(mozambique()));
        Adm1 adm1 = new Adm1();
        adm1.setName("Cabo Delgado");
        assertTrue(ListView.addsSpecimenNames(adm1));
        Region africa = new Region();
        africa.setId(2);
        africa.setName("Africa");
        assertTrue(ListView.addsSpecimenNames(africa));
        Bioregion afro = new Bioregion();
        afro.setName("Afrotropical");
        assertFalse("a bioregion's list takes every specimen name already", ListView.addsSpecimenNames(afro));
        Museum casc = new Museum();
        casc.setCode("CASC");
        assertFalse("a museum's list too", ListView.addsSpecimenNames(casc));
        assertFalse("All AntWeb too", ListView.addsSpecimenNames(new Project(Project.ALLANTWEBANTS)));
        assertFalse("a curated project selects no specimens", ListView.addsSpecimenNames(new Project("madants")));
        assertNull(ListView.specimenNamesSet(StatusSet.listStatusSet("valid")));
        assertNull(ListView.specimenNamesSet(StatusSet.listStatusSet("fossil")));
        assertEquals(StatusSet.VETTED_MORPHO, ListView.specimenNamesSet(StatusSet.listStatusSet("vetted")).getValue());
        assertEquals(StatusSet.ALL_NAMES, ListView.specimenNamesSet(StatusSet.listStatusSet("all")).getValue());
    }

    public void testViewQueriesAddTheSpecimensCollectedHere() {
        Country moz = mozambique();
        assertNull("Valid: the list alone", TaxaPage.specimenNamesWhere(moz, "species", true, StatusSet.listStatusSet("valid")));
        String vetted = TaxaPage.specimenNamesWhere(moz, "species", true, StatusSet.listStatusSet("vetted"));
        assertTrue(vetted, vetted.contains("taxon.taxon_name in (select specimen.taxon_name from specimen where"));
        assertTrue("red flags never count: " + vetted, vetted.contains("(specimen.flag is null or specimen.flag != 'red')"));
        assertTrue(vetted, vetted.contains("specimen.country = 'Mozambique'"));
        assertTrue("the vetted morphospecies: " + vetted, vetted.contains("taxon.status = 'morphotaxon' and " + ListView.vettedCondition("taxon")));
        assertTrue("not where a curator removed it: " + vetted, vetted.contains("geolocale_taxon_dispute gtd where gtd.geolocale_id = 40"));
        assertNull("Vetted genus rows come from the list", TaxaPage.specimenNamesWhere(moz, "genus", true, StatusSet.listStatusSet("vetted")));
        assertNotNull(TaxaPage.specimenNamesWhere(moz, "genus", true, StatusSet.listStatusSet("all")));
        String q = TaxaPage.viewQuery(moz, "species", true, StatusSet.listStatusSet("all"), "taxon.taxon_name", " and taxon.genus = 'acropyga'");
        assertTrue(q, q.contains(" union select taxon.taxon_name from taxon where "));
        assertEquals("the extra condition on both halves", 2, q.split(" and taxon.genus = 'acropyga'", -1).length - 1);
        Bioregion afro = new Bioregion();
        afro.setName("Afrotropical");
        assertFalse(TaxaPage.viewQuery(afro, "species", true, StatusSet.listStatusSet("all"), "taxon.taxon_name", "").contains(" union "));
    }

    /**
     * Genus rows: Acropyga in Mozambique (no species on the list, morphospecies collected there) is in Valid with no
     * species; a genus known only from "(indet)" specimens is in All specimen names only; a morphogenus is in Vetted only
     * with a vetted morphospecies; a genus entered by a curator or from the literature stays.
     */
    public void testWhichGenusRowsEachViewShows() {
        String sp = ListView.SOURCE_SPECIMEN;
        assertTrue("known from a morphospecies", ListView.keepGenus("valid", "valid", sp, 0, 1));
        assertFalse("known only from specimens identified to genus", ListView.keepGenus("valid", "valid", sp, 0, 0));
        assertFalse(ListView.keepGenus("vetted", "valid", sp, 0, 0));
        assertTrue(ListView.keepGenus("all", "valid", sp, 0, 0));
        assertTrue(ListView.keepGenus("fossil", "valid", sp, 0, 0));
        assertTrue("a curator's entry", ListView.keepGenus("valid", "valid", ListView.SOURCE_CURATED, 0, 0));
        assertTrue("the literature", ListView.keepGenus("vetted", "valid", ListView.SOURCE_LITERATURE, 0, 0));
        assertTrue("species in the view", ListView.keepGenus("vetted", "morphotaxon", sp, 2, 2));
        assertFalse("a morphogenus with no vetted morphospecies", ListView.keepGenus("vetted", "morphotaxon", sp, 0, 3));

        Map<String, Integer> species = new HashMap<>();
        species.put("formicinae|camponotus", 12);
        Map<String, Integer> evidence = new HashMap<>();
        evidence.put("formicinae|camponotus", 14);
        evidence.put("formicinae|acropyga", 1);
        ListView.GenusRows rows = new ListView.GenusRows("valid", species, evidence, new PlaceTaxonNotes.OtherNames());
        assertEquals(12, rows.species("Formicinae", "Camponotus"));
        assertEquals(0, rows.species("formicinae", "acropyga"));
        Genus acropyga = genus("acropyga", "valid");
        Genus lividopone = genus("lividopone", "valid");
        Genus camponotus = genus("camponotus", "valid");
        assertEquals(Arrays.asList(camponotus, acropyga), rows.filter(Arrays.asList(camponotus, acropyga, lividopone)));
        ListView.GenusRows all = new ListView.GenusRows("all", species, null, null);
        assertEquals(3, all.filter(Arrays.asList(camponotus, acropyga, lividopone)).size());
    }

    private static Genus genus(String name, String status) {
        Genus g = new Genus();
        g.setSubfamily("formicinae");
        g.setGenus(name);
        g.setStatus(status);
        return g;
    }

    /** Other specimens: the place's names of the genus that are not names of the view; red flags are left out by the query. */
    public void testOtherSpecimensAreTheNamesNotInTheView() {
        ListView.PlaceSpecimens ps = new ListView.PlaceSpecimens();
        ps.add("formicinaeacropyga mz01", "formicinae", "acropyga", "mz01", null, "species", "morphotaxon", 2);
        ps.add("formicinaeacropyga (indet)", "formicinae", "acropyga", "(indet)", null, "species", "indetermined", 1);
        ps.add("formicinaecamponotus maculatus", "formicinae", "camponotus", "maculatus", null, "species", "valid", 5);
        ps.add("formicinaeacropyga", "formicinae", "acropyga", null, null, "genus", "valid", 4);
        ps.add("(formicidae) indet", "(formicidae)", "(formicidae)", "indet", null, "species", "indetermined", 3);
        ListView.ViewNames valid = new ListView.ViewNames();
        valid.add("formicinaecamponotus maculatus", "formicinae", "camponotus", "valid");
        PlaceTaxonNotes.OtherNames o = ListView.otherNames(ps, valid);
        assertEquals("mz01 2 + (indet) 1; not the genus-rank name, not a name not identified to genus", 3, o.genus("formicinae", "acropyga").specimens);
        assertNull(o.genus("formicinae", "camponotus"));
        assertEquals(1, o.genera().size());
        ListView.ViewNames all = new ListView.ViewNames();
        all.add("formicinaeacropyga mz01", "formicinae", "acropyga", "morphotaxon");
        all.add("formicinaeacropyga (indet)", "formicinae", "acropyga", "indetermined");
        assertTrue(ListView.otherNames(ps, all).genus("formicinae", "acropyga") == null);
        assertNull("no place specimens: no column", ListView.otherNames(null, valid));
        assertEquals(2, ps.count("formicinaeacropyga mz01"));
        assertEquals(0, ps.count("nothing"));
        assertEquals(1, all.countByGenus(ListView.BEYOND_GENUS_STATUSES).get("formicinae|acropyga").intValue());
        ListView.GenusRows rows = new ListView.GenusRows("fossil", null, null, o).withSpecimens(ps);
        assertNull("Fossil shows no Other specimens", rows.other("formicinae", "acropyga"));
        assertTrue(rows.hasSpecimens("formicinae", "acropyga"));
        assertFalse(rows.hasSpecimens("formicinae", "lividopone"));
    }

    /** Source icons: specimen, curated, literature; a name with a specimen collected here shows specimen. */
    public void testSourceIcons() {
        assertEquals(ListView.SOURCE_SPECIMEN, ListView.sourceKind("literature", true));
        assertEquals("specimens trump literature", ListView.SOURCE_SPECIMEN, ListView.sourceKind("antwiki", true));
        assertEquals(ListView.SOURCE_LITERATURE, ListView.sourceKind("literature", false));
        assertEquals(ListView.SOURCE_LITERATURE, ListView.sourceKind("antwiki", false));
        assertEquals("AntCat type localities: literature", ListView.SOURCE_LITERATURE, ListView.sourceKind("worldants", false));
        assertEquals(ListView.SOURCE_CURATED, ListView.sourceKind("speciesListTool", false));
        assertEquals(ListView.SOURCE_CURATED, ListView.sourceKind("proxyspeciesListTool", false));
        assertEquals(ListView.SOURCE_SPECIMEN, ListView.sourceKind("adm1Specimen", false));
        assertEquals("museum lists have no source: specimen", ListView.SOURCE_SPECIMEN, ListView.sourceKind(null, false));
        String icon = ListView.sourceIconHtml(ListView.SOURCE_CURATED);
        assertTrue(icon, icon.startsWith("<span class=\"src-icon src-curated\" data-source=\"curated\" title=\"Curated\" role=\"img\" aria-label=\"Curated\">"));
        assertEquals("Literature", ListView.sourceWord(ListView.SOURCE_LITERATURE));
        assertEquals("Specimen", ListView.sourceWord(ListView.SOURCE_SPECIMEN));
    }

    public void testAboutThisListNamesTheFourViews() {
        String[][] d = ListView.viewDescriptions();
        assertEquals(4, d.length);
        for (int i = 0; i < 4; i++) assertEquals(StatusSet.choiceLabel(StatusSet.LIST_CHOICES[i], "species"), d[i][0]);
        assertTrue(d[1][1].contains("vetted once a curator has put it on any species list"));
        assertTrue(d[2][1].contains("listed only in this view"));
        assertTrue(ListView.hasViews(mozambique()));
        assertFalse(ListView.hasViews(new Project(Project.WORLDANTS)));
        assertFalse(ListView.hasViews(null));
        assertNull(ListView.choiceTitle("valid extant", "species"));
    }
}
