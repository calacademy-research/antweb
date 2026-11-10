package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.ListView;
import org.calacademy.antweb.Museum;
import org.calacademy.antweb.Overview;
import org.calacademy.antweb.PlaceTaxonNotes;
import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.home.PlaceGenusCurationDb;
import org.calacademy.antweb.home.PlaceViewTotalDb;

import test.org.calacademy.antweb.util.NumbersTestDb;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * Production 2026-11-09 (Brian Fisher): the nightly "Place list totals" (PlaceViewTotalDb.fillAll) took 60 minutes on
 * 11-07 and 11-08 and about 4 hours on 11-09. Against the NumbersTestDb database:
 *  - B26 (fixed 2026-11-09): an adm1 takes the specimens of its own country and name: a state of the same name in
 *    another country, and a specimen with that adm1 and no country, are not its specimens;
 *  - the adm1 and country queries of a place's specimens can use the index country_adm1_idx (EXPLAIN; adm1 alone could
 *    not: MySQL walked every ant specimen for each state);
 *  - only places whose data changed are counted again (fingerprint); a change of the rules, an old count, specimen names
 *    not stored again, the weekly full count and the admin's param=all count them all;
 *  - the daily sequence (2026-11-10, production: "1987 recounted, 0 unchanged" on a Tuesday): what the daily update
 *    rewrites every day before the totals leaves every place unchanged; a morphospecies curated on a list somewhere
 *    counts the places that have the name again, not every place; the stored totals are the lists' rows every day;
 *  - every place is committed on its own (no transaction held from one place to the next).
 * Runs in CI against MySQL 8.4 (host "mysql"); ANTWEB_TEST_DB_URL overrides.
 */
public class PlaceViewTotalDbTest extends TestCase {

    private Connection connection;

    static final int KENYA_ATSINANANA = 9;
    static final String PC = "myrmicinaepheidole c";

    @Override
    protected void setUp() throws Exception {
        connection = connect();
        fill(connection);
        // the columns the list page's own code reads (TaxaPage.fetchChildren, Taxon.setDetails), as the live table
        exec("alter table taxon add column subspecies varchar(64), add column antcat_id int, add column type tinyint default 0,"
            + " add column author_date varchar(128)");
        exec("update taxon set genus = 'pheidole' where taxon_name like 'myrmicinaepheidole%'");
        exec("update taxon set genus = 'hypoponera' where taxon_name like 'ponerinaehypoponera%'");
        exec("update taxon set species = substring_index(taxon_name, ' ', -1) where taxarank in ('species', 'subspecies')");
        exec("drop table if exists place_view_total");
        exec("drop table if exists place_view_total_state");
        exec(PlaceViewTotalDb.createTable(null, null));
        exec(PlaceViewTotalDb.createStateTable(null, null));
        PlaceViewTotalDb.setAfterPlaceForTests(null);
    }

    @Override
    protected void tearDown() throws Exception {
        PlaceViewTotalDb.setAfterPlaceForTests(null);
        if (connection != null) connection.close();
    }

    private void exec(String sql) throws Exception {
        NumbersTestDb.exec(connection, sql);
    }

    private static Country madagascar() {
        Country c = new Country();
        c.setId(MADAGASCAR);
        c.setName("Madagascar");
        c.setGeorank("country");
        return c;
    }

    private static Adm1 adm1(int id, String name, String country) {
        Adm1 a = new Adm1();
        a.setId(id);
        a.setName(name);
        a.setParent(country);
        a.setGeorank("adm1");
        return a;
    }

    private static Museum casc() {
        Museum m = new Museum();
        m.setCode("CASC");
        m.setName("CASC");
        return m;
    }

    private int stored(String type, String key, String rank) throws Exception {
        try (PreparedStatement s = connection.prepareStatement("select n from place_view_total where list_type = ? and list_key = ? and taxarank = ?")) {
            s.setString(1, type);
            s.setString(2, key);
            s.setString(3, rank);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? r.getInt(1) : -1;
            }
        }
    }

    /** As if the last run was an hour ago (the Not curated count of tonight then comes after it). */
    private void anHourLater() throws Exception {
        exec("update place_view_total_state set counted = counted - interval 1 hour, checked = checked - interval 1 hour");
    }

    /** The nightly order: the Not curated count (place_specimen_name) first, then the list totals. */
    private String night(List<Overview> places) throws Exception {
        new PlaceGenusCurationDb(connection).fillAll();
        return new PlaceViewTotalDb(connection).fill(places, false);
    }

    // ------------------------------------------------------------------ B26

    /** Atsinanana of Madagascar: casent1 and casent2; not Kenya's Atsinanana's specimen, not the one with no country. */
    public void testAdm1TakesTheSpecimensOfItsOwnCountry() throws Exception {
        exec("update specimen set adm1 = 'Atsinanana' where code in ('casent1', 'casent2')");
        exec("insert into specimen (code, taxon_name, status, country, adm1, family, subfamily, genus) values"
            + " ('kenya1', '" + PA + "', 'valid', 'Kenya', 'Atsinanana', 'formicidae', '" + MYRM + "', 'pheidole'),"
            + " ('nocountry1', '" + PA + "', 'valid', null, 'Atsinanana', 'formicidae', '" + MYRM + "', 'pheidole')");
        exec("insert into geolocale (id, name, georank, parent, is_valid, is_live) values (" + KENYA_ATSINANANA + ", 'Atsinanana', 'adm1', 'Kenya', 1, 1)");
        ListView.PlaceSpecimens mg = ListView.loadPlaceSpecimens(connection, adm1(ATSINANANA, "Atsinanana", "Madagascar"), null, null);
        assertEquals("casent1, casent2 only", 2, mg.count(PA));
        ListView.PlaceSpecimens ke = ListView.loadPlaceSpecimens(connection, adm1(KENYA_ATSINANANA, "Atsinanana", "Kenya"), null, null);
        assertEquals("kenya1 only", 1, ke.count(PA));
        // the page counts (Geolocale.getSpecimenPlaceCriteria) and the stored names (Not curated count) by the same rule
        Geolocale g = adm1(ATSINANANA, "Atsinanana", "Madagascar");
        assertEquals(" and s.country = 'Madagascar' and s.adm1 = 'Atsinanana'", g.getSpecimenPlaceCriteria());
        new PlaceGenusCurationDb(connection).fill(PlaceGenusCurationDb.ADM1);
        try (PreparedStatement s = connection.prepareStatement("select list_key, specimens from place_specimen_name where list_type = 'adm1' and taxon_name = ? order by list_key")) {
            s.setString(1, PA);
            try (ResultSet r = s.executeQuery()) {
                assertTrue(r.next());
                assertEquals("" + ATSINANANA, r.getString(1));
                assertEquals(2, r.getInt(2));
                assertTrue(r.next());
                assertEquals("" + KENYA_ATSINANANA, r.getString(1));
                assertEquals(1, r.getInt(2));
                assertFalse("the specimen with no country is no state's", r.next());
            }
        }
        // an adm1 without a parent country takes none
        assertTrue(ListView.loadPlaceSpecimens(connection, adm1(ATSINANANA, "Atsinanana", null), null, null).taxonNames().isEmpty());
    }

    // ------------------------------------------------------------------ indexes (EXPLAIN)

    /** possible_keys of the specimen table in EXPLAIN of the query (every row of it: a union has one per part). */
    private List<String> specimenKeys(String sql) throws Exception {
        List<String> out = new ArrayList<>();
        try (PreparedStatement s = connection.prepareStatement("explain " + sql); ResultSet r = s.executeQuery()) {
            while (r.next()) if ("specimen".equals(r.getString("table"))) out.add(String.valueOf(r.getString("possible_keys")));
        }
        return out;
    }

    /** The adm1 query (country and adm1) and both parts of a country's can use country_adm1_idx; adm1 alone could not. */
    public void testPlaceQueriesCanUseTheCountryAdm1Index() throws Exception {
        exec("alter table specimen add index country_adm1_idx (country, adm1)");   // as on production
        String adm1 = PlaceTaxonNotes.placeSpecimenCriteria(adm1(ATSINANANA, "Atsinanana", "Madagascar"));
        List<String> keys = specimenKeys(ListView.placeSpecimensSql(adm1, false, false));
        assertFalse(keys.isEmpty());
        for (String k : keys) assertTrue("adm1 by country and name: " + keys, k.contains("country_adm1_idx"));
        List<String> before = specimenKeys(ListView.placeSpecimensSql(" and specimen.adm1 = 'Atsinanana'", false, false));
        for (String k : before) assertFalse("adm1 alone (before 2026-11-09): no index: " + before, k.contains("country_adm1_idx"));
        List<String> parts = PlaceTaxonNotes.placeSpecimenParts(madagascar());
        assertEquals(2, parts.size());
        List<String> country = specimenKeys(ListView.placeSpecimensSql(parts.subList(0, 1), false, false, 0));
        for (String k : country) assertTrue("country part: " + country, k.contains("country_adm1_idx"));
        // the island_country part has no index on production yet (db/upgrade/9.3.1/2026-11-09-specimen-place-indexes.sql adds one)
    }

    /** A country's two parts select the same specimens as "country = X or island_country = X", each once. */
    public void testCountryPartsAreTheSameSpecimens() throws Exception {
        exec("insert into specimen (code, taxon_name, status, country, island_country, family, subfamily, genus) values"
            + " ('isl1', '" + PA + "', 'valid', null, 'Madagascar', 'formicidae', '" + MYRM + "', 'pheidole'),"
            + " ('isl2', '" + PA + "', 'valid', 'Madagascar', 'Madagascar', 'formicidae', '" + MYRM + "', 'pheidole'),"
            + " ('isl3', '" + PA + "', 'valid', 'Comoros', 'Madagascar', 'formicidae', '" + MYRM + "', 'pheidole')");
        ListView.PlaceSpecimens p = ListView.loadPlaceSpecimens(connection, madagascar(), null, null);
        assertEquals("casent1, casent2, isl1, isl2 (once), isl3", 5, p.count(PA));
        assertEquals(1, p.count(HB));
        ListView.PlaceSpecimens one = ListView.loadPlaceSpecimens(connection, madagascar(), "myrmicinae", "pheidole", Arrays.asList(PA));
        assertEquals("with the subfamily, genus and name parameters in each part", 5, one.count(PA));
    }

    // ------------------------------------------------------------------ only what changed

    public void testUnchangedPlacesAreSkippedAndChangedOnesCounted() throws Exception {
        List<Overview> places = Arrays.asList(madagascar(), casc());
        String first = night(places);
        assertTrue(first, first.startsWith(PlaceViewTotalDb.LOG + ": 2 places in "));
        assertTrue(first, first.contains("(2 recounted, 0 unchanged)"));
        int species = stored("geolocale", "" + MADAGASCAR, "species");
        assertTrue("Madagascar's species list has rows: " + species, species > 0);
        int casc = stored("museum", "CASC", "species");

        anHourLater();
        String second = night(places);
        assertTrue(second, second.contains("(0 recounted, 2 unchanged)"));
        assertEquals(species, stored("geolocale", "" + MADAGASCAR, "species"));

        // a new valid species on Madagascar's list: Madagascar is counted again, CASC is not
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus, species) values ('" + PC
            + "', 'species', 'valid', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "', 'pheidole', 'c')");
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (" + MADAGASCAR + ", '" + PC + "', 'antcat')");
        anHourLater();
        String third = night(places);
        assertTrue(third, third.contains("(1 recounted, 1 unchanged)"));
        assertEquals("the new row counted", species + 1, stored("geolocale", "" + MADAGASCAR, "species"));
        assertEquals(casc, stored("museum", "CASC", "species"));

        // a taxon's status changes (AntCat import): every place whose list or specimens have the name
        exec("update taxon set status = 'synonym' where taxon_name = '" + PC + "'");
        anHourLater();
        assertTrue(night(places).contains("(1 recounted, 1 unchanged)"));
        assertEquals(species, stored("geolocale", "" + MADAGASCAR, "species"));

        // a new specimen of CASC (stored by tonight's Not curated count): CASC counted again
        exec("insert into specimen (code, taxon_name, status, country, museum, family, subfamily, genus) values ('casc9', '" + HB
            + "', 'valid', 'Madagascar', 'CASC', 'formicidae', '" + PON + "', 'hypoponera')");
        anHourLater();
        assertTrue(night(places).contains("(2 recounted, 0 unchanged)"));   // Madagascar too: casc9 was collected there
    }

    /** Specimen names not stored again since the last comparison (the Not curated count failed or did not run): counted. */
    public void testPlacesAreCountedWhenTheirSpecimenNamesWereNotStoredAgain() throws Exception {
        List<Overview> places = Arrays.asList(madagascar(), casc());
        night(places);
        anHourLater();
        exec("update place_specimen_name set counted = counted - interval 1 hour");   // stored with the last run, not since
        String s = new PlaceViewTotalDb(connection).fill(places, false);
        assertTrue("no Not curated count since: unsure, so counted: " + s, s.contains("(2 recounted, 0 unchanged)"));
    }

    public void testRulesChangeOldCountsAndTheFullCountRecountEverything() throws Exception {
        List<Overview> places = Arrays.asList(madagascar(), casc());
        night(places);
        exec("update place_view_total_state set rules = 'older rules'");
        anHourLater();
        assertTrue(night(places).contains("(2 recounted, 0 unchanged)"));
        exec("update place_view_total_state set counted = counted - interval 8 day");
        anHourLater();
        assertTrue("over 7 days old", night(places).contains("(2 recounted, 0 unchanged)"));
        anHourLater();
        new PlaceGenusCurationDb(connection).fillAll();
        assertTrue("param=all and Sundays", new PlaceViewTotalDb(connection).fill(places, true).contains("(2 recounted, 0 unchanged)"));
        anHourLater();
        assertTrue(night(places).contains("(0 recounted, 2 unchanged)"));
        // totals deleted by hand: counted again
        exec("delete from place_view_total where list_type = 'museum'");
        anHourLater();
        String missing = night(places);
        assertTrue(missing, missing.contains("(1 recounted, 1 unchanged); recounted because: totals missing 1; comparing "));
        // a fingerprint stored before 2026-11-10 (one checksum of all curated morphospecies, "v"): counted once more
        exec("update place_view_total_state set fingerprint = 'l9:1;s4:1;d0:0;v0:0;r" + PlaceViewTotalDb.RULES + "'");
        anHourLater();
        String earlier = night(places);
        assertTrue(earlier, earlier.contains("(2 recounted, 0 unchanged); recounted because: data changed 2 (fingerprint of an earlier version 2);"));
        anHourLater();
        assertTrue(night(places).contains("(0 recounted, 2 unchanged)"));
        // a place no longer counted (not in tonight's places): its totals and state go
        anHourLater();
        night(Arrays.asList(madagascar()));
        assertEquals(-1, stored("museum", "CASC", "species"));
        assertTrue(PlaceViewTotalDb.weeklyFullCount(java.time.LocalDate.of(2026, 11, 8)));
        assertFalse(PlaceViewTotalDb.weeklyFullCount(java.time.LocalDate.of(2026, 11, 9)));
    }

    /**
     * A curator's entry for a morphospecies on any list curates it in every place (ListView.vettedCondition): every place
     * that has the name is counted again (here both: pheidole b was collected in Madagascar and is in CASC; a place
     * without the name is not, testTheDailySequenceRecountsOnlyThePlacesThatChanged). A curator's entry for a valid name
     * elsewhere changes no other place.
     */
    public void testCuratingAMorphospeciesAnywhereRecountsEveryPlace() throws Exception {
        List<Overview> places = Arrays.asList(madagascar(), casc());
        night(places);
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (40, '" + HB + "', 'speciesListTool')");
        anHourLater();
        assertTrue("a valid name on another place's list", night(places).contains("(0 recounted, 2 unchanged)"));
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (40, '" + PB + "', 'speciesListTool')");
        anHourLater();
        String s = night(places);
        assertTrue("pheidole b (morphotaxon) curated on Mozambique's list: " + s, s.contains("(2 recounted, 0 unchanged)"));
        assertTrue("the line says why: " + s, s.contains("; recounted because: data changed 2 (curated morphospecies 2); comparing "));
    }

    /**
     * The curated part of the fingerprints is read once per run and compared in memory (PlaceViewTotalDb.loadCurated,
     * Curated.isCurated); the lists are counted with ListView.vettedCondition. Both say the same of every morphotaxon
     * row: a curator's entry on a place's list or on a project's; not a system list's (allantwebants), not a project
     * missing from the project table, not a row of another source; a morphogenus with a curated morphospecies of it.
     */
    public void testCuratedPartFollowsTheListsRule() throws Exception {
        exec("alter table proj_taxon add column rev int default 0");
        String morpho = "insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus, species) values ";
        exec(morpho + "('myrmicinaegenus_a', 'genus', 'morphotaxon', '" + MYRM + "', 'formicidae', '" + MYRM + "', 'genus_a', null),"
            + " ('myrmicinaegenus_a mg01', 'species', 'morphotaxon', 'myrmicinaegenus_a', 'formicidae', '" + MYRM + "', 'genus_a', 'mg01'),"
            + " ('myrmicinaegenus_b', 'genus', 'morphotaxon', '" + MYRM + "', 'formicidae', '" + MYRM + "', 'genus_b', null),"
            + " ('myrmicinaegenus_b mg02', 'species', 'morphotaxon', 'myrmicinaegenus_b', 'formicidae', '" + MYRM + "', 'genus_b', 'mg02'),"
            + " ('ponerinaegenus_a', 'genus', 'morphotaxon', '" + PON + "', 'formicidae', '" + PON + "', 'genus_a', null),"
            + " ('myrmicinaepheidole p1', 'species', 'morphotaxon', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "', 'pheidole', 'p1'),"
            + " ('myrmicinaepheidole p2', 'species', 'morphotaxon', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "', 'pheidole', 'p2'),"
            + " ('myrmicinaepheidole p3', 'species', 'morphotaxon', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "', 'pheidole', 'p3'),"
            + " ('myrmicinaepheidole p4', 'species', 'morphotaxon', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "', 'pheidole', 'p4')");
        assertCuratedAsTheLists("no curator's entry yet", 0);
        // genus_a mg01 on a place's list (its morphogenus with it; not the genus of that name in another subfamily)
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (40, 'myrmicinaegenus_a mg01', 'speciesListTool')");
        assertCuratedAsTheLists("a place's list", 2);
        // p1 on a project's list; p2 on the system list only; p3 on a project that is not in the project table; p4 from specimens
        exec("insert into proj_taxon (project_name, taxon_name, source) values ('madants', 'myrmicinaepheidole p1', 'curator'),"
            + " ('allantwebants', 'myrmicinaepheidole p2', 'speciesListUpload'), ('goneants', 'myrmicinaepheidole p3', 'speciesListTool'),"
            + " ('madants', 'myrmicinaepheidole p4', 'specimen')");
        assertCuratedAsTheLists("a project's list", 3);
        // a morphogenus entered itself, without a curated species
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (40, 'myrmicinaegenus_b', 'speciesListUpload')");
        assertCuratedAsTheLists("a morphogenus entry", 4);
    }

    /** Every morphotaxon row: curated by the lists' SQL rule = curated by the fingerprint's; expected: how many are. */
    private void assertCuratedAsTheLists(String when, int expected) throws Exception {
        PlaceViewTotalDb.Curated curated = new PlaceViewTotalDb(connection).loadCurated();
        int n = 0;
        int rows = 0;
        try (PreparedStatement s = connection.prepareStatement("select t.taxon_name, t.subfamily, t.genus, " + ListView.vettedCondition("t")
                + " from taxon t where t.status = 'morphotaxon'"); ResultSet r = s.executeQuery()) {
            while (r.next()) {
                ++rows;
                assertEquals(when + ": " + r.getString(1), r.getBoolean(4), curated.isCurated(r.getString(1), r.getString(2), r.getString(3)));
                if (r.getBoolean(4)) ++n;
            }
        }
        assertTrue(rows >= 10);
        assertEquals(when, expected, n);
    }

    // ------------------------------------------------------------------ the daily sequence (2026-11-10)

    static final String PZ = "myrmicinaepheidole zz";

    private static org.calacademy.antweb.geolocale.Subregion indianOcean() {
        org.calacademy.antweb.geolocale.Subregion s = new org.calacademy.antweb.geolocale.Subregion();
        s.setId(INDIAN_OCEAN);
        s.setName("Indian Ocean");
        s.setParent("Africa");
        s.setGeorank("subregion");
        return s;
    }

    private static Museum museum(String code) {
        Museum m = new Museum();
        m.setCode(code);
        m.setName(code);
        return m;
    }

    private static org.calacademy.antweb.geolocale.Bioregion malagasy() {
        org.calacademy.antweb.geolocale.Bioregion b = new org.calacademy.antweb.geolocale.Bioregion();
        b.setName("Malagasy");
        return b;
    }

    private static org.calacademy.antweb.Project madants() {
        org.calacademy.antweb.Project p = new org.calacademy.antweb.Project();
        p.setName("madants");
        return p;
    }

    /** Deletes a table's rows (where) and inserts the same rows again, as the daily steps that rebuild a table do. */
    private void deleteAndInsertAgain(String table, String where) throws Exception {
        exec("drop table if exists daily_copy");
        exec("create table daily_copy as select * from " + table + " where " + where);
        exec("delete from " + table + " where " + where);
        exec("insert into " + table + " select * from daily_copy");
        exec("drop table daily_copy");
    }

    /**
     * What the daily update does before the place list totals, every day, also when no data changed (production
     * 2026-11-10: "1987 recounted, 0 unchanged" on a Tuesday). The steps that rewrite what the totals are counted from:
     *  set 1  the TaxonWorks upload deletes the specimens of CAS and loads them again (SpecimenUploadDb.dropSpecimens);
     *  set 2  "Geolocale taxa from specimens": the specimen rows of geolocale_taxon are deleted (source like '%specimen%')
     *         and inserted again, with a new created time; the count crawl resets and writes every list row's counts;
     *  set 3  the museum and bioregion lists are deleted and rebuilt; the source column is updated
     *         (TaxonSetDb.updateTaxonSetSources, the real step); the projects' list counts are written again;
     *  set 4  image counts of every list row; the place image counts (PlaceImageCountDb.updateAll, the real step); the
     *         Not curated count stores place_specimen_name again (PlaceGenusCurationDb.fillAll, the real step).
     * The rebuilds of sets 1 to 3 are done here in SQL with the same effect on the rows (deleted, inserted again, new
     * times, counts written again): their Java steps read the place managers' caches, which a test database has not.
     */
    private void dailyStepsBeforeTheTotals() throws Exception {
        // set 1: the CAS specimens deleted and loaded again, under a new upload id
        deleteAndInsertAgain("specimen", "access_group = 1");
        exec("update specimen set upload_id = upload_id + 1 where access_group = 1");
        // set 2: the specimen rows of the country, adm1, subregion and region lists
        deleteAndInsertAgain("geolocale_taxon", "source like '%specimen%'");
        exec("update geolocale_taxon set created = now() where source like '%specimen%'");
        // sets 2 and 3: every list's counts reset, then written again (the count crawls)
        for (String t : new String[] {"geolocale_taxon", "proj_taxon"}) {
            exec("create table daily_counts as select * from " + t);
            exec("update " + t + " set specimen_count = 0, species_count = 0, genus_count = 0, subfamily_count = 0, image_count = 0");
            String key = t.equals("geolocale_taxon") ? "geolocale_id" : "project_name";
            exec("update " + t + " l join daily_counts c on c." + key + " = l." + key + " and c.taxon_name = l.taxon_name set l.specimen_count = c.specimen_count,"
                + " l.species_count = c.species_count, l.genus_count = c.genus_count, l.subfamily_count = c.subfamily_count, l.image_count = c.image_count");
            exec("drop table daily_counts");
        }
        // set 3: museum and bioregion lists deleted and rebuilt; the source column
        deleteAndInsertAgain("museum_taxon", "1 = 1");
        exec("update museum_taxon set created = now()");
        deleteAndInsertAgain("bioregion_taxon", "1 = 1");
        exec("update bioregion_taxon set created = now()");
        org.calacademy.antweb.home.TaxonSetDb.updateTaxonSetSources(connection);
        // set 4: the place image counts and the Not curated count
        new org.calacademy.antweb.home.PlaceImageCountDb(connection).updateAll();
        new PlaceGenusCurationDb(connection).fillAll();
    }

    /** The next day: the last run was 24 hours ago; today's steps, then the totals. */
    private String nextDay(List<Overview> places) throws Exception {
        exec("update place_view_total_state set counted = counted - interval 1 day, checked = checked - interval 1 day");
        exec("update place_specimen_name set counted = counted - interval 1 day");
        dailyStepsBeforeTheTotals();
        return new PlaceViewTotalDb(connection).fill(places, false);
    }

    /** Correctness: every stored total is what the list page's own code counts now (no changed place was skipped). */
    private void assertTotalsAreTodays(String when, List<Overview> places) throws Exception {
        PlaceViewTotalDb db = new PlaceViewTotalDb(connection);
        for (Overview place : places) {
            String[] tk = PlaceViewTotalDb.typeAndKey(place);
            for (String rank : new String[] {"genus", "species", "subfamily"}) {
                assertEquals(when + ": " + tk[0] + " " + tk[1] + " " + rank, db.listRows(place, rank), stored(tk[0], tk[1], rank));
            }
        }
    }

    /**
     * The daily sequence, day after day (2026-11-10). No data changed: no place is counted again. A morphospecies that
     * a curator puts on a list somewhere is curated everywhere, but only the places that have the name (on their list or
     * among their specimens) can show another number: only they are counted again (before: every place of AntWeb, by one
     * checksum of all curated morphospecies, so one curator's entry anywhere, or one curated morphospecies whose
     * specimens were renamed, made the whole step count all 1,987 places). A changed taxon row: the places with the name.
     */
    public void testTheDailySequenceRecountsOnlyThePlacesThatChanged() throws Exception {
        // the lists as the daily update leaves them: rows from specimens (source specimen; the subregion's are proxies)
        exec("update geolocale_taxon set source = 'specimen' where geolocale_id = " + MADAGASCAR + " and taxon_name <> '" + PB + "'");
        exec("update geolocale_taxon set source = 'proxyspecimen' where geolocale_id = " + INDIAN_OCEAN + " and taxon_name <> '" + PB + "'");
        exec("delete from geolocale_taxon where taxon_name = '" + PB + "'");   // an uncurated morphospecies is on no country list
        exec("alter table proj_taxon add column rev int default 0");   // read by the project list's query
        // Atsinanana: casent1 and casent2 (pheidole a) and its list rows
        exec("update specimen set adm1 = 'Atsinanana' where code in ('casent1', 'casent2')");
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source, created) values (" + ATSINANANA + ", '" + FAMILY + "', 'specimen', now()),"
            + " (" + ATSINANANA + ", '" + MYRM + "', 'specimen', now()), (" + ATSINANANA + ", '" + PHEIDOLE + "', 'specimen', now()),"
            + " (" + ATSINANANA + ", '" + PA + "', 'specimen', now())");
        // a morphospecies of MCZC collected in Kenya: on MCZC's list, in no other place of this test
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus, species) values ('" + PZ
            + "', 'species', 'morphotaxon', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "', 'pheidole', 'zz')");
        exec("insert into specimen (code, taxon_name, status, country, museum, family, subfamily, genus, access_group) values ('mcz9', '" + PZ
            + "', 'morphotaxon', 'Kenya', 'MCZC', 'formicidae', '" + MYRM + "', 'pheidole', 2)");
        exec("insert into museum_taxon (code, taxon_name, created) values ('MCZC', '" + PZ + "', now()), ('MCZC', '" + PHEIDOLE + "', now()),"
            + " ('MCZC', '" + MYRM + "', now())");
        Museum mczc = museum("MCZC");
        List<Overview> places = Arrays.asList(madagascar(), adm1(ATSINANANA, "Atsinanana", "Madagascar"), indianOcean(), casc(), mczc,
            malagasy(), madants());

        dailyStepsBeforeTheTotals();
        String first = new PlaceViewTotalDb(connection).fill(places, false);
        assertTrue(first, first.contains("(7 recounted, 0 unchanged); recounted because: new place 7; comparing "));
        assertTotalsAreTodays("day 1", places);
        int mczcSpecies = stored("museum", "MCZC", "species");

        // days 2 and 3: the daily update ran, no data changed
        for (int day = 2; day <= 3; day++) {
            String s = nextDay(places);
            assertTrue("day " + day + ", nothing changed: " + s, s.contains("(0 recounted, 7 unchanged); comparing "));
            assertTotalsAreTodays("day " + day, places);
        }

        // day 4: a curator puts MCZC's morphospecies on Mozambique's list: curated everywhere; only MCZC has the name
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (40, '" + PZ + "', 'speciesListTool')");
        String fourth = nextDay(places);
        assertTrue("day 4, a morphospecies of MCZC curated on another place's list: " + fourth, fourth.contains("(1 recounted, 6 unchanged)"));
        assertTrue(fourth, fourth.contains("; recounted because: data changed 1 (curated morphospecies 1); comparing "));
        assertEquals("MCZC's Curated list has it now", mczcSpecies + 1, stored("museum", "MCZC", "species"));
        assertTotalsAreTodays("day 4", places);

        // day 5: the curator takes it off again
        exec("delete from geolocale_taxon where geolocale_id = 40");
        String fifth = nextDay(places);
        assertTrue("day 5, the entry removed: " + fifth, fifth.contains("(1 recounted, 6 unchanged)"));
        assertEquals(mczcSpecies, stored("museum", "MCZC", "species"));
        assertTotalsAreTodays("day 5", places);

        // day 6: AntCat makes pheidole a x a synonym: the places with the name (Madagascar, its subregion, CASC, madants)
        exec("update taxon set status = 'synonym' where taxon_name = '" + PAX + "'");
        String sixth = nextDay(places);
        assertTrue("day 6, a taxon row changed: " + sixth, sixth.contains("(4 recounted, 3 unchanged)"));
        assertTrue(sixth, sixth.contains("; recounted because: data changed 4 (list rows 4, specimen names 2); comparing "));
        assertTotalsAreTodays("day 6", places);

        // day 7: nothing again
        String seventh = nextDay(places);
        assertTrue("day 7: " + seventh, seventh.contains("(0 recounted, 7 unchanged)"));
        assertTotalsAreTodays("day 7", places);
    }

    /** Each place is committed when it is done, also on a connection whose autocommit was off (restored after). */
    public void testEachPlaceIsCommittedOnItsOwn() throws Exception {
        new PlaceGenusCurationDb(connection).fillAll();
        new PlaceViewTotalDb(connection).ensureSchema();
        List<String> seen = new ArrayList<>();
        try (Connection other = connect()) {
            PlaceViewTotalDb.setAfterPlaceForTests((type, key) -> {
                try (PreparedStatement s = other.prepareStatement("select count(*) from place_view_total where list_type = ? and list_key = ?")) {
                    s.setString(1, type);
                    s.setString(2, key);
                    try (ResultSet r = s.executeQuery()) {
                        r.next();
                        if (r.getInt(1) == 3) seen.add(type + " " + key);
                    }
                } catch (java.sql.SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            connection.setAutoCommit(false);
            new PlaceViewTotalDb(connection).fill(Arrays.asList(madagascar(), casc()), true);
            assertFalse("autocommit as it was", connection.getAutoCommit());
            connection.setAutoCommit(true);
        }
        assertEquals("another connection sees each place right after it", Arrays.asList("geolocale " + MADAGASCAR, "museum CASC"), seen);
    }
}
