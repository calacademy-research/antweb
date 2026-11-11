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
 *  - every place is committed on its own (no transaction held from one place to the next);
 *  - one place between two daily updates (2026-11-10, staging: after "Update statistics" on the bioregion Nearctic its
 *    genus list had 91 genera and the page said Genera 90): PlaceViewTotalDb.recountPlace counts the one place whose list
 *    was rebuilt, by its own key for every kind of place, and stores its totals and state; every row of every other
 *    place stays as it was; the AntWeb-wide list is not counted; a count that fails leaves no totals for the place (its
 *    page shows the place's own counts until the next daily update) instead of the totals of before the change;
 *  - review of PR #341 (2026-11-11): a place counted that way is counted once more by the next daily update, whatever
 *    its data is then (its lists were counted from today's specimens, the daily update's fingerprint reads the specimen
 *    names stored the day before); the schema check creates nothing, and so commits nothing, when the tables exist
 *    (recountPlace runs inside an admin job's transaction); a failed count says why (getLastFailure).
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
    public void testCuratingAMorphospeciesAnywhereRecountsThePlacesThatHaveIt() throws Exception {
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

    // ------------------------------------------------------------------ one place between two daily updates (2026-11-10)

    static final String TETRA = "myrmicinaetetramorium";
    static final String TT = "myrmicinaetetramorium t";

    /** A valid genus and a valid species of it that no list has yet. */
    private void newGenusAndSpecies() throws Exception {
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus, species) values"
            + " ('" + TETRA + "', 'genus', 'valid', '" + MYRM + "', 'formicidae', '" + MYRM + "', 'tetramorium', null),"
            + " ('" + TT + "', 'species', 'valid', '" + TETRA + "', 'formicidae', '" + MYRM + "', 'tetramorium', 't')");
    }

    /**
     * Every column of every row of the totals and the state table, as text, without the rows of one place (null: every
     * row): to see that the rows of the other places are exactly as they were, their times too.
     */
    private List<String> rowsWithout(String type, String key) throws Exception {
        List<String> out = new ArrayList<>();
        for (String table : new String[] {"place_view_total", "place_view_total_state"}) {
            try (PreparedStatement s = connection.prepareStatement("select * from " + table + " order by 1, 2, 3"); ResultSet r = s.executeQuery()) {
                int columns = r.getMetaData().getColumnCount();
                while (r.next()) {
                    if (r.getString(1).equals(type) && r.getString(2).equals(key)) continue;
                    StringBuilder b = new StringBuilder(table);
                    for (int i = 1; i <= columns; i++) b.append('|').append(r.getString(i));
                    out.add(b.toString());
                }
            }
        }
        return out;
    }

    /** A place's state row: {rules, fingerprint, seconds since counted}, or null without one. */
    private String[] state(String type, String key) throws Exception {
        try (PreparedStatement s = connection.prepareStatement("select rules, fingerprint, timestampdiff(second, counted, now())"
                + " from place_view_total_state where list_type = ? and list_key = ?")) {
            s.setString(1, type);
            s.setString(2, key);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? new String[] {r.getString(1), r.getString(2), r.getString(3)} : null;
            }
        }
    }

    /**
     * "Update statistics" on a place page rebuilds that place's list between two daily updates (staging 2026-11-10, the
     * bioregion Nearctic: its genus list then had 91 genera, the page still said Genera 90). After a place's list gains
     * a genus and a species, recountPlace makes its stored totals the rows of its lists and stores its state; every row
     * of the other places is exactly as it was; the next daily update finds every place unchanged.
     */
    public void testOnePlaceIsCountedAgainAndNoOtherPlaceIsTouched() throws Exception {
        exec("alter table proj_taxon add column rev int default 0");   // read by the project list's query
        List<Overview> places = Arrays.asList(madagascar(), casc(), malagasy(), madants());
        night(places);
        anHourLater();
        PlaceViewTotalDb db = new PlaceViewTotalDb(connection);
        String key = "" + MADAGASCAR;
        int genera = stored("geolocale", key, "genus");
        int species = stored("geolocale", key, "species");
        int subfamilies = stored("geolocale", key, "subfamily");
        assertTrue("Madagascar's lists have rows: " + genera + ", " + species + ", " + subfamilies, genera > 0 && species > 0 && subfamilies > 0);
        List<String> others = rowsWithout("geolocale", key);
        assertEquals("CASC, Malagasy and madants: three totals and one state row each: " + others, 12, others.size());

        // Madagascar's list gains a genus and a species
        newGenusAndSpecies();
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (" + MADAGASCAR + ", '" + TETRA + "', 'antcat'),"
            + " (" + MADAGASCAR + ", '" + TT + "', 'antcat')");
        assertEquals("the genus list shows the new genus", genera + 1, db.listRows(madagascar(), "genus"));
        assertEquals("the species list shows the new species", species + 1, db.listRows(madagascar(), "species"));
        assertEquals("the stored totals are still those of the last daily update", genera, stored("geolocale", key, "genus"));
        assertEquals(species, stored("geolocale", key, "species"));

        assertTrue("counted", new PlaceViewTotalDb(connection).recountPlace(madagascar()));
        assertEquals("Genera = the genus list's rows", genera + 1, stored("geolocale", key, "genus"));
        assertEquals("Species = the species list's rows", species + 1, stored("geolocale", key, "species"));
        assertEquals(subfamilies, stored("geolocale", key, "subfamily"));
        assertTotalsAreTodays("after the count of one place", Arrays.asList(madagascar()));
        assertEquals("no row of another place was written or removed", others, rowsWithout("geolocale", key));
        // its state: today's rules, counted now, and the mark of a place counted between two daily updates (no fingerprint)
        String[] state = state("geolocale", key);
        assertNotNull(state);
        assertEquals(PlaceViewTotalDb.RULES, state[0]);
        assertEquals(PlaceViewTotalDb.ONE_PLACE, state[1]);
        assertFalse("the mark is no fingerprint", PlaceViewTotalDb.ONE_PLACE.equals(db.fingerprint(madagascar(), db.loadCurated())));
        assertTrue("counted now, not an hour ago: " + state[2], Integer.parseInt(state[2]) < 600);

        // a second count changes no number (and still no other place)
        assertTrue(new PlaceViewTotalDb(connection).recountPlace(madagascar()));
        assertEquals(genera + 1, stored("geolocale", key, "genus"));
        assertEquals(species + 1, stored("geolocale", key, "species"));
        assertEquals(others, rowsWithout("geolocale", key));

        // the next daily update counts that one place once more (and says why), the others stand; the one after, none
        anHourLater();
        String next = night(places);
        assertTrue("the next daily update: " + next, next.contains("(1 recounted, 3 unchanged); recounted because: data changed 1 ("
            + PlaceViewTotalDb.ONE_PLACE_REASON + " 1); comparing "));
        assertEquals(genera + 1, stored("geolocale", key, "genus"));
        assertEquals(species + 1, stored("geolocale", key, "species"));
        assertEquals("its state is a fingerprint again", db.fingerprint(madagascar(), db.loadCurated()), state("geolocale", key)[1]);
        anHourLater();
        String after = night(places);
        assertTrue("the daily update after that: " + after, after.contains("(0 recounted, 4 unchanged)"));
    }

    /**
     * Review of PR #341 (2026-11-11). A country's species list is counted from its specimens as they are now; the daily
     * update's fingerprint reads the specimen names stored by the Not curated count of the daily update before. With a
     * fingerprint stored by the count of one place: specimens of a curated morphospecies arrive (an upload), "Update
     * statistics" counts the country with them, the specimens go again before the next daily update (the upload is
     * corrected). The next daily update then stores the same specimen names as the day before, finds the same
     * fingerprint, calls the country unchanged and keeps the total that counted the specimens, for up to 7 days. The
     * count of one place therefore stores a mark that equals no fingerprint: the next daily update counts the place.
     */
    public void testAPlaceCountedBetweenTwoDailyUpdatesIsCountedAgainByTheNext() throws Exception {
        List<Overview> places = Arrays.asList(madagascar(), casc());
        // a morphospecies a curator has on Mozambique's list (curated everywhere), with no specimen in Madagascar yet
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus, species) values ('" + PZ
            + "', 'species', 'morphotaxon', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "', 'pheidole', 'zz')");
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (40, '" + PZ + "', 'speciesListTool')");
        night(places);
        anHourLater();
        String key = "" + MADAGASCAR;
        int species = stored("geolocale", key, "species");
        PlaceViewTotalDb db = new PlaceViewTotalDb(connection);
        // an upload brings a specimen of it from Madagascar: the Curated species list of Madagascar shows the name
        exec("insert into specimen (code, taxon_name, status, country, museum, family, subfamily, genus, access_group) values ('up1', '" + PZ
            + "', 'morphotaxon', 'Madagascar', 'MCZC', 'formicidae', '" + MYRM + "', 'pheidole', 2)");
        assertEquals("the list, counted from the specimens of now", species + 1, db.listRows(madagascar(), "species"));
        assertTrue(db.recountPlace(madagascar()));
        assertEquals(species + 1, stored("geolocale", key, "species"));
        // the upload is corrected before the next daily update: the specimen is gone again
        exec("delete from specimen where code = 'up1'");
        assertEquals(species, db.listRows(madagascar(), "species"));
        // the next daily update stores the same specimen names as the one before: the same fingerprint as then
        anHourLater();
        String next = night(places);
        assertTrue("Madagascar is counted again: " + next, next.contains("(1 recounted, 1 unchanged); recounted because: data changed 1 ("
            + PlaceViewTotalDb.ONE_PLACE_REASON + " 1); comparing "));
        assertEquals("the total of the list as it is, not the one that counted the specimen", species, stored("geolocale", key, "species"));
        assertTotalsAreTodays("after the daily update", places);
    }

    /** Sets PlaceViewTotalDb's "schema checked" mark back, as after a restart of the server. */
    private static void asAfterARestart() throws Exception {
        java.lang.reflect.Field f = PlaceViewTotalDb.class.getDeclaredField("s_schemaChecked");
        f.setAccessible(true);
        f.setBoolean(null, false);
    }

    private boolean tableExists(String table) throws Exception {
        try (PreparedStatement s = connection.prepareStatement("select 1 from information_schema.tables where table_schema = database() and table_name = ?")) {
            s.setString(1, table);
            try (ResultSet r = s.executeQuery()) { return r.next(); }
        }
    }

    /**
     * Review of PR #341 (2026-11-11). The first count of one place after a restart ran CREATE TABLE IF NOT EXISTS for both
     * tables. Any CREATE TABLE commits the transaction it runs in, also when the table exists, and the admin jobs for one
     * place run in one transaction (autocommit off): the job's work so far was committed, so a failure later in the job
     * could no longer undo it. The schema check now asks whether the tables exist and creates only a missing one.
     */
    public void testTheSchemaCheckCommitsNothingWhenTheTablesExist() throws Exception {
        night(Arrays.asList(madagascar(), casc()));
        connection.setAutoCommit(false);
        try {
            // the job's work so far, not committed
            exec("update geolocale set parent = 'changed by the job' where id = " + MADAGASCAR);
            asAfterARestart();
            new PlaceViewTotalDb(connection).ensureSchema();
            asAfterARestart();
            assertTrue("the count of one place, first use after a restart", new PlaceViewTotalDb(connection).recountPlace(casc()));
            // the job fails afterwards: everything it did is undone, the count of the place too
            connection.rollback();
        } finally {
            connection.setAutoCommit(true);
        }
        try (PreparedStatement s = connection.prepareStatement("select parent from geolocale where id = " + MADAGASCAR); ResultSet r = s.executeQuery()) {
            assertTrue(r.next());
            assertEquals("the job's work was not committed by the schema check", "Indian Ocean", r.getString(1));
        }
        assertFalse("the count of the place was undone with the job", PlaceViewTotalDb.ONE_PLACE.equals(state("museum", "CASC")[1]));
        // a missing table is still created (a new server), each one on its own
        exec("drop table place_view_total_state");
        asAfterARestart();
        new PlaceViewTotalDb(connection).ensureSchema();
        assertTrue(tableExists("place_view_total_state"));
        exec("drop table place_view_total");
        exec("drop table place_view_total_state");
        asAfterARestart();
        new PlaceViewTotalDb(connection).ensureSchema();
        assertTrue(tableExists("place_view_total") && tableExists("place_view_total_state"));
    }

    /**
     * The button is on museum, bioregion and project pages too (and country, island and adm1 pages are geolocales): each
     * kind of place is counted by its own key (museum code, bioregion name, project name), and only that place.
     */
    public void testEveryKindOfPlaceIsCountedByItsOwnKey() throws Exception {
        exec("alter table proj_taxon add column rev int default 0");   // read by the project list's query
        List<Overview> places = Arrays.asList(madagascar(), casc(), malagasy(), madants());
        night(places);
        anHourLater();
        newGenusAndSpecies();
        exec("insert into museum_taxon (code, taxon_name) values ('CASC', '" + TETRA + "'), ('CASC', '" + TT + "')");
        exec("insert into bioregion_taxon (bioregion_name, taxon_name) values ('Malagasy', '" + TETRA + "'), ('Malagasy', '" + TT + "')");
        exec("insert into proj_taxon (project_name, taxon_name, source) values ('madants', '" + TETRA + "', 'antcat'), ('madants', '" + TT + "', 'antcat')");
        for (Overview place : Arrays.asList(casc(), malagasy(), madants())) {
            String[] tk = PlaceViewTotalDb.typeAndKey(place);
            String what = tk[0] + " " + tk[1];
            int genera = stored(tk[0], tk[1], "genus");
            int species = stored(tk[0], tk[1], "species");
            List<String> others = rowsWithout(tk[0], tk[1]);
            assertEquals(what + ": the three other places", 12, others.size());
            assertTrue(what, new PlaceViewTotalDb(connection).recountPlace(place));
            assertEquals(what + " Genera", genera + 1, stored(tk[0], tk[1], "genus"));
            assertEquals(what + " Species", species + 1, stored(tk[0], tk[1], "species"));
            assertTotalsAreTodays(what, Arrays.asList(place));
            assertEquals(what + ": no row of another place was written or removed", others, rowsWithout(tk[0], tk[1]));
        }
    }

    /**
     * A place the daily update does not count (counts(): the AntWeb-wide list) is not counted here either: nothing is
     * read, written or removed. Rows put there by hand show it: a count would change them, a failed count remove them.
     */
    public void testAPlaceThatIsNotCountedIsLeftAlone() throws Exception {
        exec("alter table proj_taxon add column rev int default 0");   // read by the project list's query
        night(Arrays.asList(madagascar(), casc()));
        anHourLater();
        String allAntweb = org.calacademy.antweb.Project.ALLANTWEBANTS;
        exec("insert into place_view_total (list_type, list_key, taxarank, view_name, n, counted) values"
            + " ('project', '" + allAntweb + "', 'species', 'curated', 99, now() - interval 1 day)");
        exec("insert into place_view_total_state (list_type, list_key, rules, fingerprint, counted, checked) values"
            + " ('project', '" + allAntweb + "', 'rules', 'fingerprint', now() - interval 1 day, now() - interval 1 day)");
        List<String> before = rowsWithout(null, null);
        assertEquals("two places, three totals and one state row each, and the two rows put there by hand", 10, before.size());
        org.calacademy.antweb.Project all = new org.calacademy.antweb.Project();
        all.setName(allAntweb);
        assertFalse(PlaceViewTotalDb.counts(all));
        PlaceViewTotalDb db = new PlaceViewTotalDb(connection);
        assertFalse("the AntWeb-wide list", db.recountPlace(all));
        assertFalse("no place", db.recountPlace(null));
        assertEquals("nothing written or removed", before, rowsWithout(null, null));
        assertEquals(99, stored("project", allAntweb, "species"));
    }

    /**
     * A count that fails is not thrown (the page that asked for it is still shown) and leaves no wrong number: the place's
     * totals and state are removed, as the daily update does for a place that fails, so its page shows the place's own
     * counts (read: -1, data-view-counted="false") and not the totals of before its list changed. No other place is
     * touched, and the next daily update counts the place as a new one.
     */
    public void testAFailingCountLeavesNoWrongNumber() throws Exception {
        List<Overview> places = Arrays.asList(madagascar(), casc());
        night(places);
        anHourLater();
        String key = "" + MADAGASCAR;
        int species = stored("geolocale", key, "species");
        List<String> others = rowsWithout("geolocale", key);
        assertEquals(4, others.size());
        // the list changes: the stored Species is now one short
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus, species) values ('" + PC
            + "', 'species', 'valid', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "', 'pheidole', 'c')");
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (" + MADAGASCAR + ", '" + PC + "', 'antcat')");
        // the count fails: the lists' query reads a column that is gone
        exec("alter table taxon drop column subspecies");
        PlaceViewTotalDb db = new PlaceViewTotalDb(connection);
        assertFalse("a failed count is reported, not thrown", db.recountPlace(madagascar()));
        assertTrue("and says why (the admin job's answer): " + db.getLastFailure(), db.getLastFailure() != null && db.getLastFailure().contains("subspecies"));
        for (String rank : new String[] {"genus", "species", "subfamily"}) {
            assertEquals("no stored " + rank + " total rather than the one of before the change", -1, stored("geolocale", key, rank));
        }
        assertTrue("the page reads no totals and shows the place's own counts", Arrays.equals(new int[] {-1, -1, -1}, db.read(madagascar())));
        assertNull("no state, so the next daily update counts the place", state("geolocale", key));
        assertEquals("no row of another place was written or removed", others, rowsWithout("geolocale", key));

        // the query works again: the next daily update counts the place as a new one
        exec("alter table taxon add column subspecies varchar(64)");
        anHourLater();
        String next = night(places);
        assertTrue(next, next.contains("(1 recounted, 1 unchanged); recounted because: new place 1;"));
        assertEquals(species + 1, stored("geolocale", key, "species"));
        assertTrue("a count that works leaves no reason", db.recountPlace(madagascar()) && db.getLastFailure() == null);
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

    // ------------------------------------------------------------------ the site-wide lists (2026-11-11)

    private static org.calacademy.antweb.Project project(String name, String scope) {
        org.calacademy.antweb.Project p = new org.calacademy.antweb.Project();
        p.setName(name);
        p.setScope(scope);
        return p;
    }

    /**
     * Production 2026-11-11 (Brian Fisher): the Fossil Ants page said Genera 259 and its list "258 Valid (with fossils)
     * Genera". The daily update counted the list totals of the projects of scope PROJECT only (ProjectMgr.getAntProjects);
     * Fossil Ants and the AntCat list have scope GLOBAL, so their pages showed the project's stored counts, which another
     * rule counts. They are counted now; the AntWeb-wide list still is not; no project is counted twice.
     */
    public void testTheSiteWideListsAreCountedToo() throws Exception {
        List<org.calacademy.antweb.Project> ant = Arrays.asList(project("madants", "PROJECT"));
        List<org.calacademy.antweb.Project> global = Arrays.asList(project(org.calacademy.antweb.Project.ALLANTWEBANTS, "GLOBAL"),
            project(org.calacademy.antweb.Project.WORLDANTS, "GLOBAL"), project(org.calacademy.antweb.Project.FOSSILANTS, "GLOBAL"),
            project("madants", "PROJECT"), null);
        List<String> names = new ArrayList<>();
        for (org.calacademy.antweb.Project p : PlaceViewTotalDb.countedProjects(ant, global)) names.add(p.getName());
        assertEquals(Arrays.asList("madants", "worldants", "fossilants"), names);
        assertTrue(PlaceViewTotalDb.countedProjects(null, null).isEmpty());
        assertEquals(1, PlaceViewTotalDb.countedProjects(ant, null).size());
    }

    /**
     * Fossil Ants is counted in its own default view, Valid (with fossils), with the list's code: the stored totals are
     * the rows its lists show. The rule of the page's lines (Brian Fisher, 2026-11-11): "Valid species / subspecies" is the
     * rows of the list's Valid view, and on the two lists with their own views the Valid view is Valid (with fossils)
     * (StatusSet.ownChoice("valid")), the default view: both species lines of the page show the species total of that
     * view (web/overview-body.jsp), never valid_species_count (valid names that are not fossils: 1 here, 3 on production
     * beside a list of 868).
     *
     * Genera 259 beside a list of 258: every day the AntCat import puts each fossil name on the list, a fossil genus with
     * no species there among them; set 3 and set 4 count it; set 5's list check (ProjTaxonDb.verifyProjTaxon) then takes a
     * genus without species off the list. The check now names the projects that lost a row, and their totals are counted
     * again (PlaceViewTotalDb.recountProjects), so the page's Genera are the list's rows after the check too.
     */
    public void testFossilAntsNumbersAreTheRowsOfItsOwnViews() throws Exception {
        exec("alter table proj_taxon add column rev int default 0");   // read by the project list's query
        String fossil = org.calacademy.antweb.Project.FOSSILANTS;
        String genus = "myrmicinaeeocenomyrma";
        String childless = "myrmicinaeagroecomyrmex";
        String morphoGenus = "ponerinaeponerine_genus2";
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus, species, fossil) values"
            + " ('" + genus + "', 'genus', 'valid', '" + MYRM + "', 'formicidae', '" + MYRM + "', 'eocenomyrma', null, 1),"
            + " ('" + genus + " electrina', 'species', 'valid', '" + genus + "', 'formicidae', '" + MYRM + "', 'eocenomyrma', 'electrina', 1),"
            + " ('" + genus + " rugosostriata', 'species', 'valid', '" + genus + "', 'formicidae', '" + MYRM + "', 'eocenomyrma', 'rugosostriata', 1),"
            + " ('" + childless + "', 'genus', 'valid', '" + MYRM + "', 'formicidae', '" + MYRM + "', 'agroecomyrmex', null, 1),"
            + " ('" + morphoGenus + "', 'genus', 'morphotaxon', '" + PON + "', 'formicidae', '" + PON + "', 'ponerine_genus2', null, 1),"
            + " ('" + morphoGenus + " sp1', 'species', 'morphotaxon', '" + morphoGenus + "', 'formicidae', '" + PON + "', 'ponerine_genus2', 'sp1', 1)");
        exec("insert into project (project_name, is_live, scope) values ('" + fossil + "', 1, 'GLOBAL')");
        // the list: two subfamilies, the fossil genus with two species, the morphogenus with its morphospecies, a living
        // genus with one living valid species, and a fossil genus with no species on the list
        for (String name : new String[] {FAMILY, MYRM, PON, genus, genus + " electrina", genus + " rugosostriata", childless, morphoGenus,
                morphoGenus + " sp1", PHEIDOLE, PA}) {
            exec("insert into proj_taxon (project_name, taxon_name, source) values ('" + fossil + "', '" + name + "', 'antcat')");
        }
        org.calacademy.antweb.Project p = project(fossil, "GLOBAL");
        assertEquals("Valid (with fossils)", org.calacademy.antweb.StatusSet.defaultChoice(p));
        PlaceViewTotalDb db = new PlaceViewTotalDb(connection);
        new PlaceGenusCurationDb(connection).fillAll();
        db.fill(Arrays.<Overview>asList(p), false);
        assertEquals("Genera as the daily update counts them: the genus without species is still on the list", 4, stored("project", fossil, "genus"));
        assertEquals("Species: two fossil species, the morphospecies and the living one", 4, stored("project", fossil, "species"));
        assertEquals("Subfamilies", 2, stored("project", fossil, "subfamily"));
        try (PreparedStatement s = connection.prepareStatement("select distinct view_name from place_view_total where list_key = ?")) {
            s.setString(1, fossil);
            try (ResultSet r = s.executeQuery()) {
                assertTrue(r.next());
                assertEquals("counted in the list's own default view", "Valid (with fossils)", r.getString(1));
                assertFalse(r.next());
            }
        }
        assertEquals("the page's Genera, Species, Subfamilies", "[4, 4, 2]", Arrays.toString(db.read(p)));

        // set 5: the list check takes the genus without species off the list, and says which projects lost a row
        org.calacademy.antweb.home.ProjTaxonDb lists = new org.calacademy.antweb.home.ProjTaxonDb(connection);
        assertTrue(lists.getProjectsWithRowsRemoved().isEmpty());
        lists.verifyProjTaxon();
        assertTrue("" + lists.getProjectsWithRowsRemoved(), lists.getProjectsWithRowsRemoved().contains(fossil));
        org.calacademy.antweb.TaxaPage genera = new org.calacademy.antweb.TaxaPage();
        genera.fetchChildren(connection, p, "genus", false, true, false, true, null,
            org.calacademy.antweb.StatusSet.listStatusSet(org.calacademy.antweb.StatusSet.defaultChoice(p), fossil));
        assertEquals("the genus list after the check", 3, genera.getChildrenCount());
        assertEquals("until 2026-11-11 the page kept the number of before the check", 4, stored("project", fossil, "genus"));
        List<String> counted = db.recountProjects(lists.getProjectsWithRowsRemoved());
        assertTrue("" + counted, counted.contains(fossil));
        assertFalse("the AntWeb-wide list is not counted", counted.contains(org.calacademy.antweb.Project.ALLANTWEBANTS));
        assertEquals("the page's Genera are the list's rows", 3, stored("project", fossil, "genus"));
        assertEquals("3 Valid (with fossils) Genera", org.calacademy.antweb.StatusSet.listHeading(org.calacademy.antweb.StatusSet.defaultChoice(p),
            3, "Genera", "Genera", -1, fossil));
        assertTrue(db.recountProjects(null).isEmpty());
        // a check that removes nothing names no project
        org.calacademy.antweb.home.ProjTaxonDb again = new org.calacademy.antweb.home.ProjTaxonDb(connection);
        again.verifyProjTaxon();
        assertFalse("" + again.getProjectsWithRowsRemoved(), again.getProjectsWithRowsRemoved().contains(fossil));

        // the page's two species lines: the Valid view of this list is its default view, so both show its species total
        String validView = org.calacademy.antweb.StatusSet.ownChoice(org.calacademy.antweb.StatusSet.LIST_VALID);
        assertEquals("the Valid view of a list with its own views", "Valid (with fossils)", validView);
        assertEquals("and that is its default view", org.calacademy.antweb.StatusSet.defaultChoice(p), validView);
        assertTrue(org.calacademy.antweb.StatusSet.hasOwnStatusSets(fossil));
        assertTrue(org.calacademy.antweb.StatusSet.hasOwnStatusSets(org.calacademy.antweb.Project.WORLDANTS));
        assertFalse("every other list: the view Valid, and the number valid_species_count", org.calacademy.antweb.StatusSet.hasOwnStatusSets("madants"));
        org.calacademy.antweb.TaxaPage valid = new org.calacademy.antweb.TaxaPage();
        valid.fetchChildren(connection, p, "species", false, true, false, true, null, org.calacademy.antweb.StatusSet.listStatusSet(validView, fossil));
        assertEquals("the list the Valid line opens has the rows of the Species line", stored("project", fossil, "species"), valid.getChildrenCount());
        assertEquals("4 Valid (with fossils) Species", org.calacademy.antweb.StatusSet.listHeading(validView, 4, "Species", "Species", -1, fossil));
        assertEquals("the label the page's texts name", "Valid (with fossils)", org.calacademy.antweb.StatusSet.choiceLabel(validView, null, fossil));
        // valid_species_count's rule (valid, not fossil) finds one name here: not what the page shows
        try (PreparedStatement s = connection.prepareStatement("select count(*) from proj_taxon l join taxon t on t.taxon_name = l.taxon_name"
                + " where l.project_name = ? and t.taxarank in ('species', 'subspecies') and t.status = 'valid' and t.fossil = 0")) {
            s.setString(1, fossil);
            try (ResultSet r = s.executeQuery()) {
                r.next();
                assertEquals(1, r.getInt(1));
            }
        }
        // the next daily update keeps the site-wide list's totals (it removes the rows of the places it is not given)
        db.fill(Arrays.<Overview>asList(p), false);
        assertEquals(3, stored("project", fossil, "genus"));
    }
}
