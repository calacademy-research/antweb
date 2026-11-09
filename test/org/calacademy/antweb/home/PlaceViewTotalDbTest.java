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
        assertTrue(night(places).contains("(1 recounted, 1 unchanged)"));
        // a place no longer counted (not in tonight's places): its totals and state go
        anHourLater();
        night(Arrays.asList(madagascar()));
        assertEquals(-1, stored("museum", "CASC", "species"));
        assertTrue(PlaceViewTotalDb.weeklyFullCount(java.time.LocalDate.of(2026, 11, 8)));
        assertFalse(PlaceViewTotalDb.weeklyFullCount(java.time.LocalDate.of(2026, 11, 9)));
    }

    /**
     * A curator's entry for a morphospecies on any list curates it in every place (ListView.vettedCondition): every place
     * is counted again. A curator's entry for a valid name elsewhere changes no other place.
     */
    public void testCuratingAMorphospeciesAnywhereRecountsEveryPlace() throws Exception {
        List<Overview> places = Arrays.asList(madagascar(), casc());
        night(places);
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (40, '" + HB + "', 'speciesListTool')");
        anHourLater();
        assertTrue("a valid name on another place's list", night(places).contains("(0 recounted, 2 unchanged)"));
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (40, '" + PB + "', 'speciesListTool')");
        anHourLater();
        assertTrue("pheidole b (morphotaxon) curated on Mozambique's list", night(places).contains("(2 recounted, 0 unchanged)"));
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
