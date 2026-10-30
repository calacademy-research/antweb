package test.org.calacademy.antweb.util;

import java.util.*;

import junit.framework.TestCase;

import org.calacademy.antweb.Museum;
import org.calacademy.antweb.Project;
import org.calacademy.antweb.StatusSet;
import org.calacademy.antweb.TaxaPage;
import org.calacademy.antweb.geolocale.Bioregion;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.home.GenusOnly;
import org.calacademy.antweb.home.GeolocaleDb;
import org.calacademy.antweb.home.PlaceImageCountDb;
import org.calacademy.antweb.home.PlaceTotals;
import org.calacademy.antweb.home.StatisticsDb;
import org.calacademy.antweb.util.CountInvariants;

/**
 * Counting rules decided by Brian Fisher on 2026-10-28 (docs/numbers.md section 5, "Counting rules"), without a database:
 * a specimen counts at each rank it is identified to, and no further. One test per rank (Subfamilies, Genera, Species,
 * Specimens) and per decision of the day (images of synonyms, museum and bioregion Specimens, their list images, Introduced
 * of regions). The rules are written once (PlaceTotals) and every user of a number takes it from there; the database
 * behaviour of the same queries is covered by PlaceTotalsDbTest, CountCheckTest and NumberChecksTest (CI, MySQL 8.4) and
 * end-to-end on staging (test_numbers_lists.py, test_count_check.py).
 */
public class CountRules20261028Test extends TestCase {

    private static final String[] NOT_SPECIES = {"indetermined", "unidentifiable", "unrecognized"};

    private static Country country() {
        Country c = new Country();
        c.setId(7);
        c.setName("Madagascar");
        return c;
    }

    private static Museum museum() {
        Museum m = new Museum();
        m.setCode("CASC");
        return m;
    }

    private static Bioregion bioregion() {
        Bioregion b = new Bioregion();
        b.setName("Afrotropical");
        return b;
    }

    // ---------------------------------------------------------------- Species

    public void testSpeciesAreValidNamesAndMorphospeciesOnly() {
        assertEquals("('valid', 'morphotaxon')", PlaceTotals.SPECIES_STATUSES);
        String c = PlaceTotals.rankCondition(PlaceTotals.SPECIES);
        assertTrue(c, c.contains("t.status in " + PlaceTotals.SPECIES_STATUSES));
        assertTrue("names not identified to genus are still not species: " + c, c.contains(PlaceTotals.identifiedToGenus("t")));
        assertTrue("ants only: " + c, c.contains(PlaceTotals.ANTS));
        for (String s : NOT_SPECIES) {
            assertFalse(s + " is not a species: " + c, c.contains(s));
            assertFalse(s, PlaceTotals.isSpeciesStatus(s));
        }
        assertTrue(PlaceTotals.isSpeciesStatus("valid"));
        assertTrue(PlaceTotals.isSpeciesStatus("morphotaxon"));
        assertFalse(PlaceTotals.isSpeciesStatus(null));
        // the place's Species (one place, all places) and its pie take the same rule
        for (String type : PlaceTotals.TYPES) {
            assertTrue(type, PlaceTotals.listedCountSql(type, PlaceTotals.SPECIES).contains(c));
            assertTrue(type, PlaceTotals.listedCountsByPlaceSql(type, PlaceTotals.SPECIES).contains(c));
            assertTrue(type, PlaceTotals.speciesPieSql(type).contains(c));
        }
    }

    public void testGenusRowsCountTheSameSpeciesOnEveryKindOfList() {
        Project curated = new Project("madants");
        String status = PlaceTotals.speciesStatusCondition("taxon");
        String[] queries = {country().getCountChildrenQuery("species"), museum().getCountChildrenQuery("species"),
            bioregion().getCountChildrenQuery("species"), curated.getCountChildrenQuery("species"),
            new Project("worldants").getCountChildrenQuery("species")};
        for (String q : queries) {
            assertTrue("a genus row's Species count valid names and morphospecies: " + q, q.contains(status));
            assertTrue(q, q.contains(PlaceTotals.identifiedToGenus("taxon")));
        }
        // the genus rows of a subfamily (its Genera) count by the genus row's own status: a genus known only from
        // "Lioponera indet" counts (its genus row is valid), whatever the status of the indet name; since 2026-10-30
        // valid genera and morphogenera only (PlaceTotals.countsAsGenus, CountRules20261030Test)
        for (String q : new String[] {country().getCountChildrenQuery("genus"), museum().getCountChildrenQuery("genus"),
                bioregion().getCountChildrenQuery("genus"), curated.getCountChildrenQuery("genus")}) {
            assertTrue(q, q.contains(PlaceTotals.countsAsGenus("taxon")));
        }
    }

    public void testSpeciesListsLeaveOutNamesThatAreNotSpecies() {
        for (String v : new String[] {StatusSet.ALL, StatusSet.ALL_DETERMINED, StatusSet.ALL_INDETERMINED, StatusSet.COMPLETE}) {
            assertTrue(v, TaxaPage.hidesNonSpecies(new StatusSet(v)));
        }
        assertTrue("the default status set", TaxaPage.hidesNonSpecies(new StatusSet()));
        assertTrue(TaxaPage.hidesNonSpecies(null));
        // a status chosen on its own, or a valid-only set, is left as it is (curators can still list indetermined names)
        for (String v : new String[] {"valid", "indetermined", "unrecognized", StatusSet.VALID_EXTANT, StatusSet.VALID_FOSSIL, StatusSet.WORLDANTS}) {
            assertFalse(v, TaxaPage.hidesNonSpecies(new StatusSet(v)));
        }
    }

    public void testImagedSpeciesCountValidNamesAndMorphospecies() {
        String sql = StatisticsDb.IMAGED_SPECIES_SQL;
        assertTrue(sql, sql.contains("t.status in " + PlaceTotals.SPECIES_STATUSES));
        for (String s : NOT_SPECIES) assertFalse(s + ": " + sql, sql.contains(s));
        // the rule before, kept so the count check can call a number written before the change pending
        for (String s : NOT_SPECIES) assertTrue(s, StatisticsDb.IMAGED_SPECIES_SQL_BEFORE_20261028.contains(s));
    }

    // ---------------------------------------------------------------- Genera and Subfamilies

    public void testGeneraAndSubfamiliesCountTheRowsOfTheirRule() {
        // A "Lioponera indet" specimen puts its name and its genus row on the list (GeolocaleTaxonDb.insert adds the parents);
        // the genus row counts in Genera, the subfamily row in Subfamilies. Since 2026-10-30 Subfamilies count valid
        // subfamilies only and Genera valid genera and morphogenera (CountRules20261030Test); until then both counted
        // rows of every counted status.
        String c = PlaceTotals.rankCondition(PlaceTotals.SUBFAMILY);
        assertTrue(c, c.contains(PlaceTotals.countsAsSubfamily("t")));
        assertFalse(c, c.contains(PlaceTotals.COUNTABLE));
        String g = PlaceTotals.rankCondition(PlaceTotals.GENUS);
        assertTrue(g, g.contains(PlaceTotals.countsAsGenus("t")));
        assertFalse(g, g.contains(PlaceTotals.COUNTABLE));
        assertTrue(PlaceTotals.COUNTABLE.contains("indetermined"));
    }

    public void testGenusOnlySpecimensAreNamedOnGenusAndSpeciesLists() {
        String sql = PlaceTotals.genusOnlySql(PlaceTotals.GEOLOCALE);
        assertTrue(sql, sql.contains("t.status in " + PlaceTotals.GENUS_ONLY_STATUSES));
        assertTrue(sql, sql.contains(PlaceTotals.identifiedToGenus("t")));
        assertTrue(sql, sql.contains("l.geolocale_id = ?"));
        assertEquals("('indetermined', 'unidentifiable')", PlaceTotals.GENUS_ONLY_STATUSES);

        GenusOnly g = new GenusOnly();
        assertNull(g.speciesListText());
        g.add("ponerinaelioponera", "ponerinae", "lioponera", "ponerinaelioponera indet", "indet", null, "species", 1);
        g.add("myrmicinaepheidole", "myrmicinae", "pheidole", "myrmicinaepheidole indet", "indet", null, "species", 1);
        g.add("myrmicinaepheidole", "myrmicinae", "pheidole", "myrmicinaepheidole sp", "sp", null, "species", 1);
        g.add("myrmicinaecrematogaster", "myrmicinae", "crematogaster", "myrmicinaecrematogaster indet", "indet", null, "species", 0);
        assertEquals("3 specimens identified only to genus are not listed here: Lioponera (1), Pheidole (2).", g.speciesListText());
        assertEquals(3, g.totalSpecimens());
        assertEquals(2, g.specimens("myrmicinaePheidole"));
        assertEquals("a row without specimens is not counted", 0, g.specimens("myrmicinaecrematogaster"));
        assertEquals("1 specimen identified to genus only", GenusOnly.genusRowText(1));
        assertEquals("2 specimens identified to genus only", GenusOnly.genusRowText(2));
        assertEquals("genusSearchType=equals&genus=pheidole&speciesSearchType=equals&species=indet%2C+sp",
            GenusOnly.searchParams(g.genus("myrmicinaepheidole")));
        assertEquals(PlaceTotals.GEOLOCALE, GenusOnly.typeAndKey(country())[0]);
        assertEquals("7", GenusOnly.typeAndKey(country())[1]);
        assertEquals("CASC", GenusOnly.typeAndKey(museum())[1]);
    }

    // ---------------------------------------------------------------- Specimens (decision 4)

    public void testMuseumAndBioregionSpecimensAreTheSumOfTheirListRows() {
        for (String type : PlaceTotals.TYPES) {
            String sql = PlaceTotals.listedSpecimensSql(type);
            assertTrue(type + ": " + sql, sql.contains("sum(l.specimen_count)") && sql.contains(PlaceTotals.listTable(type)));
            assertTrue(sql, sql.contains("t.taxarank in ('species', 'subspecies')") && sql.contains(PlaceTotals.ANTS));
            // the Specimens pie adds up to the same rows
            String pie = PlaceTotals.specimenPieSql(type);
            assertTrue(type + ": " + pie, pie.contains("sum(l.specimen_count)") && pie.contains(PlaceTotals.listTable(type)));
            assertFalse(type + ": " + pie, pie.contains("from specimen s"));
        }
    }

    // ---------------------------------------------------------------- Images (decisions 3 and 5)

    public void testImagesOfSynonymsCountUnderTheValidName() {
        String renamed = PlaceTotals.renamedCondition("st");
        String m = museum().getTaxonImageCountQuery();
        String b = bioregion().getTaxonImageCountQuery();
        String p = new Project("madants").getTaxonImageCountQuery();
        for (String q : new String[] {m, b, p}) {
            assertTrue("a synonym's images count under the valid name: " + q, q.contains(renamed));
            assertTrue(q, q.contains("m.list_name taxonName") && q.contains("imageSum"));
            assertTrue("images of the ant: " + q, q.contains(StatisticsDb.ANT_IMAGES_OF_SPECIMEN));
        }
        // museums and bioregions: their own specimens of a counted status, as the countries' lists (decision 5)
        assertTrue(m, m.contains("s.museum = 'CASC'") && m.contains("s.status in " + StatusSet.getCountables()));
        assertTrue(b, b.contains("s.bioregion = 'Afrotropical'") && b.contains("s.status in " + StatusSet.getCountables()));
        // worldants and allantwebants (the Images column's AntWeb-wide number) count the very name
        for (String name : PlaceTotals.UNCURATED_PROJECTS) {
            String q = new Project(name).getTaxonImageCountQuery();
            assertFalse(name + ": " + q, q.contains(renamed));
        }
    }

    public void testPlaceImagesOfMuseumsBioregionsAndProjectsAreThoseOfTheirListRows() {
        String renamed = PlaceTotals.renamedCondition("st");
        for (String type : new String[] {PlaceTotals.MUSEUM, PlaceTotals.BIOREGION, PlaceTotals.PROJECT}) {
            String one = PlaceImageCountDb.listSource(type, true);
            String all = PlaceImageCountDb.listSource(type, false);
            assertTrue(type + ": " + one, one.contains(renamed) && one.contains("join image i on i.image_of_id = s.code"));
            assertTrue(type + ": " + one, one.contains("t.taxarank in ('species', 'subspecies')"));
            assertEquals(type + ": the key twice (both halves of the list)", 2, one.length() - one.replace("?", "").length());
            assertEquals(type, 0, all.length() - all.replace("?", "").length());
        }
        assertTrue(PlaceImageCountDb.listSource(PlaceTotals.MUSEUM, false).contains("s.museum = m.pk and s.status in"));
        assertTrue(PlaceImageCountDb.listSource(PlaceTotals.BIOREGION, false).contains("s.bioregion = m.pk and s.status in"));
        String project = PlaceImageCountDb.listSource(PlaceTotals.PROJECT, false);
        assertTrue("Port of Entry has no image numbers: " + project, project.contains(Project.SPECIES_LIST_ONLY_SQL));
        assertTrue("worldants and allantwebants count the very name: " + project, project.contains("or m.pk in ('worldants', 'allantwebants')"));
    }

    // ---------------------------------------------------------------- Introduced of regions (decision 6)

    public void testRegionsAndSubregionsCountIntroducedAsTheyCountEndemic() {
        assertEquals(2, GeolocaleDb.HIGHER_INTRODUCED_SQL.length);
        String sub = GeolocaleDb.HIGHER_INTRODUCED_SQL[0];
        String reg = GeolocaleDb.HIGHER_INTRODUCED_SQL[1];
        assertTrue(sub, sub.contains("lg.georank = 'country'") && sub.contains("u.georank = 'subregion'"));
        assertTrue(reg, reg.contains("lg.georank = 'subregion'") && reg.contains("u.georank = 'region'"));
        for (String q : GeolocaleDb.HIGHER_INTRODUCED_SQL) {
            assertTrue(q, q.contains("lgt.is_introduced = 1") && q.contains("set gt.is_introduced = 1"));
            assertTrue("materialized, so MySQL can read the table it updates: " + q, q.contains("select distinct"));
        }
    }

    // ---------------------------------------------------------------- unchanged, and pending until the nightly run

    // ---------------------------------------------------------------- Endemic: valid names only (Q3, Q4)

    /**
     * Brian Fisher, 2026-10-22 and 2026-10-28: a name counts toward endemism only if it is valid; morphospecies,
     * indetermined, unidentifiable and unrecognized names never count, at any kind of place. One constant
     * (PlaceTotals.ENDEMIC_STATUSES) in every endemism rule, the lists and the numbers.
     */
    public void testEndemicCountsValidNamesOnly() {
        assertEquals("('valid')", PlaceTotals.ENDEMIC_STATUSES);
        assertEquals(Arrays.asList("valid"), PlaceTotals.ENDEMIC_STATUS_LIST);
        String e = PlaceTotals.endemicEligible("t");
        assertTrue(e, e.contains("t.status in " + PlaceTotals.ENDEMIC_STATUSES));
        assertTrue(e, e.contains("t.taxarank in ('species', 'subspecies')") && e.contains("t.fossil = 0") && e.contains("t.family = 'formicidae'"));
        assertTrue("identified to genus: " + e, e.contains(PlaceTotals.identifiedToGenus("t")));
        String endemic = PlaceTotals.flaggedCondition("endemic");
        assertTrue(endemic, endemic.contains("ft.status in " + PlaceTotals.ENDEMIC_STATUSES));
        assertFalse(endemic, endemic.contains(PlaceTotals.COUNTABLE));
        assertTrue("Introduced keeps the counted statuses", PlaceTotals.flaggedCondition("introduced").contains(PlaceTotals.COUNTABLE));
        for (String s : new String[] {"morphotaxon", "indetermined", "unidentifiable", "unrecognized"}) {
            assertFalse(s, PlaceTotals.ENDEMIC_STATUSES.contains(s));
            assertFalse(s, e.contains(s));
            assertFalse(s, endemic.contains(s));
        }
    }

    /** Every endemism computation (countries, adm1s, subregions, regions, bioregions) uses the rule and native rows only. */
    public void testEveryEndemismRuleCountsValidNativeRows() {
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("country", GeolocaleDb.COUNTRY_ENDEMIC_SQL);
        rules.put("adm1", GeolocaleDb.ADM1_ENDEMIC_SQL);
        rules.put("subregion", GeolocaleDb.HIGHER_ENDEMIC_SQL[0]);
        rules.put("region", GeolocaleDb.HIGHER_ENDEMIC_SQL[1]);
        rules.put("bioregion", org.calacademy.antweb.home.BioregionDb.ENDEMIC_SQL);
        String[] alias = {"taxon", "taxon", "t", "t", "taxon"};
        String[] row = {"gt", "gt", "ct", "ct", "bt"};
        int i = 0;
        for (Map.Entry<String, String> r : rules.entrySet()) {
            String q = r.getValue();
            assertTrue(r.getKey() + ": " + q, q.contains(PlaceTotals.endemicEligible(alias[i])));
            assertTrue(r.getKey() + " counts native rows only (Q4): " + q, q.contains(PlaceTotals.nativeRow(row[i])));
            assertFalse(r.getKey() + ": no other status rule: " + q, q.contains("morphotaxon") || q.contains("unrecognized"));
            ++i;
        }
        assertEquals("coalesce(gt.is_introduced, 0) = 0", PlaceTotals.nativeRow("gt"));
    }

    /**
     * Q3 for regions and subregions ("native species found only in that region"): the names whose native country rows are
     * all in the subregion's countries (all in its region's subregions), and only rows on the place's list.
     */
    public void testRegionsAndSubregionsHaveTheNamesFoundOnlyInThem() {
        String sub = GeolocaleDb.HIGHER_ENDEMIC_SQL[0];
        String reg = GeolocaleDb.HIGHER_ENDEMIC_SQL[1];
        assertTrue(sub, sub.contains("s.name = c.parent and s.georank = 'subregion'") && !sub.contains("georank = 'region'"));
        assertTrue(reg, reg.contains("r.name = s.parent and r.georank = 'region'"));
        assertTrue(sub, sub.contains("count(distinct coalesce(s.id, 0)) = 1 and min(coalesce(s.id, 0)) > 0"));
        assertTrue(reg, reg.contains("count(distinct coalesce(r.id, 0)) = 1 and min(coalesce(r.id, 0)) > 0"));
        for (String q : GeolocaleDb.HIGHER_ENDEMIC_SQL) {
            assertTrue("the row must be on the list: " + q, q.startsWith("update geolocale_taxon gt join (") && q.endsWith("set gt.is_endemic = 1"));
            assertTrue(q, q.contains("c.georank = 'country'"));
        }
        try {
            GeolocaleDb.higherEndemicSql("country");
            fail("subregion and region only");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testNumbersArePendingUntilANightlyRunCountsByTheNewRules() {
        List<String> before = Arrays.asList("  Set 4 done (" + CountInvariants.RULES_LOG + "; " + StatisticsDb.ANT_IMAGES_RULE_LOG + ")");
        assertFalse(CountInvariants.rulesCounted(before));
        List<String> after = Arrays.asList("  Set 4 done (" + CountInvariants.RULES_LOG + "; " + StatisticsDb.ANT_IMAGES_RULE_LOG
            + "; " + CountInvariants.COUNTING_RULES_LOG + "; " + CountInvariants.GENERA_RULE_LOG + ")");
        assertTrue(CountInvariants.rulesCounted(after));
    }
}
