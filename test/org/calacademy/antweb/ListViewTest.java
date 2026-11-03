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
 * The views of lists (Brian Fisher, 2026-11-02; ListView), without a database: the curated rule's SQL, which places add
 * the names of specimens collected there, the genus rows each view shows (a genus known only from specimens identified
 * to genus: All specimen names only), the Not curated column and the view Not curated (2026-11-02, counted nightly),
 * the genus rows' species imaged, the names of a genus page's line, the source icons (specimens trump literature) and
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

    /** The names collected here, read once, in place of the subquery (2026-11-02, speed): quoted; none: no rows. */
    public void testNamedIn() {
        assertEquals("1 = 0", ListView.namedIn("taxon", java.util.Collections.emptyList()));
        assertEquals("taxon.taxon_name in ('a b', 'o''brien')", ListView.namedIn("taxon", Arrays.asList("a b", "o'brien")));
        String w = TaxaPage.specimenNamesWhere(mozambique(), "species", true, StatusSet.listStatusSet("all"), Arrays.asList("x"));
        assertTrue(w, w.startsWith(" from taxon where taxon.taxon_name in ('x') and taxon.family = 'formicidae'"));
        assertFalse("no scan of the specimen table: " + w, w.contains("from specimen"));
    }

    public void testWhichPlacesAddSpecimenNames() {
        assertTrue(ListView.addsSpecimenNames(mozambique()));
        Adm1 adm1 = new Adm1();
        adm1.setName("Cabo Delgado");
        assertTrue(ListView.addsSpecimenNames(adm1));
        Region africa = new Region();
        africa.setId(2);
        africa.setName("Africa");
        // 2026-11-02 (speed, review of #316): a region's list holds its countries' specimen names already (proxy rows)
        assertFalse(ListView.addsSpecimenNames(africa));
        assertFalse("no whole-region specimen read", ListView.readsPlaceSpecimens(africa));
        assertTrue(ListView.readsPlaceSpecimens(mozambique()));
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
        assertEquals(StatusSet.VETTED_MORPHO, ListView.specimenNamesSet(StatusSet.listStatusSet("curated")).getValue());
        assertEquals(StatusSet.ALL_NAMES, ListView.specimenNamesSet(StatusSet.listStatusSet("all")).getValue());
    }

    public void testViewQueriesAddTheSpecimensCollectedHere() {
        Country moz = mozambique();
        assertNull("Valid: the list alone", TaxaPage.specimenNamesWhere(moz, "species", true, StatusSet.listStatusSet("valid")));
        String curated = TaxaPage.specimenNamesWhere(moz, "species", true, StatusSet.listStatusSet("curated"));
        assertTrue(curated, curated.contains("taxon.taxon_name in (select specimen.taxon_name from specimen where"));
        assertTrue("red flags never count: " + curated, curated.contains("(specimen.flag is null or specimen.flag != 'red')"));
        assertTrue(curated, curated.contains("specimen.country = 'Mozambique'"));
        assertTrue("the curated morphospecies: " + curated, curated.contains("taxon.status = 'morphotaxon' and " + ListView.vettedCondition("taxon")));
        assertTrue("not where a curator removed it: " + curated, curated.contains("geolocale_taxon_dispute gtd where gtd.geolocale_id = 40"));
        assertNull("Curated genus rows come from the list", TaxaPage.specimenNamesWhere(moz, "genus", true, StatusSet.listStatusSet("curated")));
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
     * species; a genus known only from "(indet)" specimens is in All specimen names only; a morphogenus is in Curated only
     * with a curated morphospecies; a genus entered by a curator or from the literature stays.
     */
    public void testWhichGenusRowsEachViewShows() {
        String sp = ListView.SOURCE_SPECIMEN;
        assertTrue("known from a morphospecies", ListView.keepGenus("valid", "valid", sp, 0, 1));
        assertFalse("known only from specimens identified to genus", ListView.keepGenus("valid", "valid", sp, 0, 0));
        assertFalse(ListView.keepGenus("curated", "valid", sp, 0, 0));
        assertTrue(ListView.keepGenus("all", "valid", sp, 0, 0));
        assertTrue(ListView.keepGenus("fossil", "valid", sp, 0, 0));
        assertTrue("a curator's entry", ListView.keepGenus("valid", "valid", ListView.SOURCE_CURATED, 0, 0));
        assertTrue("the literature", ListView.keepGenus("curated", "valid", ListView.SOURCE_LITERATURE, 0, 0));
        assertTrue("species in the view", ListView.keepGenus("curated", "morphotaxon", sp, 2, 2));
        assertFalse("a morphogenus with no curated morphospecies", ListView.keepGenus("curated", "morphotaxon", sp, 0, 3));

        Map<String, Integer> species = new HashMap<>();
        species.put("formicinae|camponotus", 12);
        Map<String, Integer> evidence = new HashMap<>();
        evidence.put("formicinae|camponotus", 14);
        evidence.put("formicinae|acropyga", 1);
        ListView.GenusRows rows = new ListView.GenusRows("valid", species, evidence);
        assertEquals(12, rows.species("Formicinae", "Camponotus"));
        assertEquals(0, rows.species("formicinae", "acropyga"));
        Genus acropyga = genus("acropyga", "valid");
        Genus lividopone = genus("lividopone", "valid");
        Genus camponotus = genus("camponotus", "valid");
        assertEquals(Arrays.asList(camponotus, acropyga), rows.filter(Arrays.asList(camponotus, acropyga, lividopone)));
        ListView.GenusRows all = new ListView.GenusRows("all", species, null);
        assertEquals(3, all.filter(Arrays.asList(camponotus, acropyga, lividopone)).size());
    }

    private static Genus genus(String name, String status) {
        Genus g = new Genus();
        g.setSubfamily("formicinae");
        g.setGenus(name);
        g.setStatus(status);
        return g;
    }

    /**
     * The genus page's line (specimens collected here not on the species list): the place's names of the genus that are not
     * names of the view; red flags are left out by the query. (The Other specimens column of genus lists, which used the
     * same names, is replaced by Not curated, 2026-11-02.)
     */
    public void testOtherNamesAreTheNamesNotInTheView() {
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
        ListView.GenusRows rows = new ListView.GenusRows("fossil", null, null).withSpecimens(ps);
        assertTrue(rows.hasSpecimens("formicinae", "acropyga"));
        assertFalse(rows.hasSpecimens("formicinae", "lividopone"));
    }

    private static org.calacademy.antweb.home.PlaceGenusCurationDb.Row ncRow(String subfamily, String genus, int specimens, int notCurated) {
        org.calacademy.antweb.home.PlaceGenusCurationDb.Row r = new org.calacademy.antweb.home.PlaceGenusCurationDb.Row();
        r.subfamily = subfamily;
        r.genus = genus;
        r.specimens = specimens;
        r.notCurated = notCurated;
        return r;
    }

    /**
     * Not curated (Brian Fisher, 2026-11-02, 19:44): the stored number in Valid and Curated (the same in both), on places
     * that have the column; not in All specimen names or Fossil; the cell empty when not counted. A genus with stored
     * specimens shows the specimen source icon.
     */
    public void testNotCuratedColumnInValidAndCuratedOnly() {
        Map<String, org.calacademy.antweb.home.PlaceGenusCurationDb.Row> stored = new HashMap<>();
        stored.put(org.calacademy.antweb.home.PlaceGenusCurationDb.key("formicinae", "acropyga"), ncRow("formicinae", "acropyga", 9, 7));
        for (String choice : new String[] {"valid", "curated"}) {
            ListView.GenusRows rows = new ListView.GenusRows(choice, null, null).withCuration(true, stored);
            assertTrue(choice, rows.showsNotCurated());
            assertEquals(choice, 7, rows.notCurated("Formicinae", "Acropyga").notCurated);
            assertNull("no stored row: empty cell", rows.notCurated("formicinae", "camponotus"));
            assertTrue("stored specimens: the specimen icon", rows.hasSpecimens("formicinae", "acropyga"));
        }
        for (String choice : new String[] {"all", "fossil"}) {
            ListView.GenusRows rows = new ListView.GenusRows(choice, null, null).withCuration(true, stored);
            assertFalse(choice, rows.showsNotCurated());
            assertNull(choice, rows.notCurated("formicinae", "acropyga"));
        }
        ListView.GenusRows region = new ListView.GenusRows("curated", null, null).withCuration(false, null);
        assertFalse("regions, subregions and projects: no column", region.showsNotCurated());
        ListView.GenusRows notCounted = new ListView.GenusRows("curated", null, null).withCuration(true, null);
        assertTrue("not counted yet: the column, empty cells", notCounted.showsNotCurated());
        assertNull(notCounted.notCurated("formicinae", "acropyga"));
        assertEquals(StatusSet.NOT_CURATED, ListView.specimenNamesSet(StatusSet.listStatusSet(StatusSet.LIST_NOT_CURATED)).getValue());
        assertNotNull(ListView.choiceTitle(StatusSet.LIST_NOT_CURATED, "species"));
    }

    /** Images of genus rows (2026-11-02, 18:40): of the species of the view, those with an image of the ant, by genus. */
    public void testGenusRowsSpeciesImaged() {
        ListView.ViewNames view = new ListView.ViewNames();
        view.add("formicinaeacropyga mz01", "formicinae", "acropyga", "morphotaxon");
        view.add("formicinaeacropyga a", "formicinae", "acropyga", "valid");
        view.add("formicinaecamponotus b", "formicinae", "camponotus", "valid");
        Map<String, Integer> imaged = view.countByGenusOf(Arrays.asList("formicinaeacropyga a", "formicinaeacropyga mz01", "not in the view"));
        ListView.GenusRows rows = new ListView.GenusRows("curated", null, null).withImaged(imaged);
        assertEquals(2, rows.imaged("formicinae", "acropyga"));
        assertEquals("a genus with no imaged species", 0, rows.imaged("formicinae", "camponotus"));
        assertEquals("not read", -1, new ListView.GenusRows("curated", null, null).imaged("formicinae", "acropyga"));
        assertEquals(3, view.taxonNames().size());
        assertEquals("<span class='numbers'>2</span> species imaged", org.calacademy.antweb.ImagesAnywhere.speciesImagedCell(2));
        assertEquals("No species imaged", org.calacademy.antweb.ImagesAnywhere.speciesImagedCell(0));
    }

    /** Source icons: specimen, curated, literature; a name with a specimen collected here shows specimen. */
    public void testSourceIcons() {
        assertEquals(ListView.SOURCE_SPECIMEN, ListView.sourceKind("literature", true));
        assertEquals("specimens trump literature", ListView.SOURCE_SPECIMEN, ListView.sourceKind("antwiki", true));
        assertEquals(ListView.SOURCE_LITERATURE, ListView.sourceKind("literature", false));
        assertEquals(ListView.SOURCE_LITERATURE, ListView.sourceKind("antwiki", false));
        // the AntCat import has its own label; literature is Bolton's catalogue only (Brian Fisher, 2026-11-02)
        assertEquals("the AntCat import", ListView.SOURCE_ANTCAT, ListView.sourceKind("worldants", false));
        assertEquals(ListView.SOURCE_ANTCAT, ListView.sourceKind("antcat", false));
        assertEquals(ListView.SOURCE_SPECIMEN, ListView.sourceKind("worldants", true));
        String antcat = ListView.sourceIconHtml(ListView.SOURCE_ANTCAT);
        assertEquals("<span class=\"src-icon src-antcat\" data-source=\"antcat\" title=\"From the AntCat import\" role=\"img\""
            + " aria-label=\"AntCat\">AntCat</span>", antcat);
        assertEquals(ListView.SOURCE_CURATED, ListView.sourceKind("speciesListTool", false));
        assertEquals(ListView.SOURCE_CURATED, ListView.sourceKind("proxyspeciesListTool", false));
        assertEquals(ListView.SOURCE_SPECIMEN, ListView.sourceKind("adm1Specimen", false));
        assertEquals("museum lists have no source: specimen", ListView.SOURCE_SPECIMEN, ListView.sourceKind(null, false));
        String icon = ListView.sourceIconHtml(ListView.SOURCE_CURATED);
        assertTrue(icon, icon.startsWith("<span class=\"src-icon src-curated\" data-source=\"curated\" title=\"Curated\" role=\"img\" aria-label=\"Curated\">"));
        assertEquals("Literature", ListView.sourceWord(ListView.SOURCE_LITERATURE));
        assertEquals("Specimen", ListView.sourceWord(ListView.SOURCE_SPECIMEN));
    }

    /** The Fossil button only where the list has fossil names (2026-11-02): one EXISTS query; shown when not known. */
    public void testFossilButton() {
        String q = ListView.hasFossilSql(mozambique(), "genus", "formicinae", null);
        assertTrue(q, q.startsWith("select 1 from taxon, geolocale_taxon where geolocale_taxon.taxon_name = taxon.taxon_name"));
        assertTrue(q, q.contains("fossil = 1") && q.contains("taxon.subfamily = 'formicinae'") && q.endsWith(" limit 1"));
        assertTrue(ListView.showsFossilButton(null, "curated"));
        assertTrue(ListView.showsFossilButton(Boolean.TRUE, "curated"));
        assertFalse(ListView.showsFossilButton(Boolean.FALSE, "curated"));
        assertTrue("the current view stays visible", ListView.showsFossilButton(Boolean.FALSE, "fossil"));
    }

    public void testAboutThisListNamesTheFourViews() {
        String[][] d = ListView.viewDescriptions();
        assertEquals(4, d.length);
        for (int i = 0; i < 4; i++) assertEquals(StatusSet.choiceLabel(StatusSet.LIST_CHOICES[i], "species"), d[i][0]);
        assertTrue(d[1][1].contains("curated once a curator has put it on any species list"));
        assertTrue(d[2][1].contains("listed only in this view"));
        assertTrue(ListView.hasViews(mozambique()));
        assertFalse(ListView.hasViews(new Project(Project.WORLDANTS)));
        assertFalse(ListView.hasViews(null));
        assertNull(ListView.choiceTitle("valid extant", "species"));
    }

    /**
     * 2026-11-02 (speed): genus lists read the names collected in a place from place_specimen_name (stored nightly), so
     * the views' specimen half is a subquery of that table, not of the specimen table; the in-memory cache of the place's
     * specimens is gone. The view Not curated: counted names, neither valid nor curated morphospecies.
     */
    public void testStoredNamesAndTheNotCuratedCondition() {
        ListView.PlaceSpecimens stored = new ListView.PlaceSpecimens().stored("country", "Mozambique");
        assertTrue(stored.isStored());
        assertEquals("taxon.taxon_name in (select psn.taxon_name from place_specimen_name psn where psn.list_type = 'country'"
            + " and psn.list_key = 'Mozambique')", ListView.hereCondition("taxon", stored));
        assertNull("nothing read: a subquery of the specimen table", ListView.hereCondition("taxon", null));
        ListView.PlaceSpecimens read = new ListView.PlaceSpecimens();
        read.add("x", "formicinae", "acropyga", "x", null, "species", "morphotaxon", 1);
        assertEquals("taxon.taxon_name in ('x')", ListView.hereCondition("taxon", read));
        String w = TaxaPage.specimenNamesWhereHere(mozambique(), "species", true, StatusSet.listStatusSet("curated"), ListView.hereCondition("taxon", stored));
        assertTrue(w, w.contains("from place_specimen_name psn") && !w.contains("from specimen "));
        String nc = ListView.notCuratedCondition("t");
        assertTrue(nc, nc.startsWith("(t.status in ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable') and not (t.status = 'valid' or (t.status = 'morphotaxon' and "));
        assertTrue(nc, nc.contains(ListView.vettedCondition("t")));
        // the view Not curated on a country genus page adds the names collected there that are not curated
        String ncWhere = TaxaPage.specimenNamesWhere(mozambique(), "species", true, StatusSet.listStatusSet(StatusSet.LIST_NOT_CURATED));
        assertTrue(ncWhere, ncWhere.contains(ListView.notCuratedCondition("taxon")) && ncWhere.contains("specimen.country = 'Mozambique'"));
        assertNull("the view Not curated adds no genus rows from specimens", TaxaPage.specimenNamesWhere(mozambique(), "genus", true,
            StatusSet.listStatusSet(StatusSet.LIST_NOT_CURATED)));
        Museum casc = new Museum();
        casc.setCode("CASC");
        assertArrayEqualsStr(new String[] {"museum", "CASC"}, org.calacademy.antweb.home.PlaceGenusCurationDb.typeAndKey(casc));
        Region africa = new Region();
        africa.setName("Africa");
        assertNull("regions have no column", org.calacademy.antweb.home.PlaceGenusCurationDb.typeAndKey(africa));
        assertNull("projects neither", org.calacademy.antweb.home.PlaceGenusCurationDb.typeAndKey(new Project("madants")));
    }

    private static void assertArrayEqualsStr(String[] a, String[] b) {
        assertEquals(Arrays.toString(a), Arrays.toString(b));
    }
}
