package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.calacademy.antweb.ListView;
import org.calacademy.antweb.Museum;
import org.calacademy.antweb.Project;
import org.calacademy.antweb.StatusSet;
import org.calacademy.antweb.TaxaPage;
import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Bioregion;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.geolocale.Subregion;
import org.calacademy.antweb.home.PlaceGenusCurationDb;

import test.org.calacademy.antweb.util.NumbersTestDb;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * The Not curated column of genus lists (Brian Fisher, 2026-11-02, 19:44), against the NumbersTestDb database: the
 * nightly fill (PlaceGenusCurationDb.fillAll) counts, per place and genus, the specimens collected there (not
 * red-flagged) whose name is not curated: valid names and curated morphospecies are left out; uncurated morphospecies,
 * names identified only to genus and unrecognized names are counted; countries take country and island_country, each
 * specimen once; adm1s, museums and bioregions their own column. The page reads the stored numbers (never an error when
 * the table is missing) and the view Not curated lists those names.
 *
 * Madagascar (NumbersTestDb): Pheidole a (valid, 2 specimens), b (morphospecies on no curated entry, 1), a x (valid, 1),
 * Hypoponera b (valid, 1). Added here: Pheidole zz (unrecognized, 1); Carebara (indet) (1, and 1 red-flagged), the
 * uncurated morphospecies Carebara mg01 (2, and 1 on the island_country column only), the curated morphospecies Carebara
 * mg02 (1; a curator's entry in Mozambique). Runs in CI against MySQL 8.4 (host "mysql"); ANTWEB_TEST_DB_URL overrides.
 */
public class PlaceGenusCurationDbTest extends TestCase {

    private Connection connection;

    static final String CAREBARA = "myrmicinaecarebara";
    static final String INDET = "myrmicinaecarebara (indet)";
    static final String MG01 = "myrmicinaecarebara mg01";
    static final String MG02 = "myrmicinaecarebara mg02";
    static final String ZZ = "myrmicinaepheidole zz";

    @Override
    protected void setUp() throws Exception {
        connection = connect();
        fill(connection);
        exec("alter table taxon add column subspecies varchar(64)");   // as the live table
        exec("update taxon set genus = 'pheidole' where taxon_name like 'myrmicinaepheidole%'");
        exec("update taxon set genus = 'hypoponera' where taxon_name like 'ponerinaehypoponera%'");
        exec("update taxon set species = 'a' where taxon_name = '" + PA + "'");
        exec("update taxon set species = 'b' where taxon_name = '" + PB + "'");
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus, species) values"
            + " ('" + CAREBARA + "', 'genus', 'valid', '" + MYRM + "', 'formicidae', '" + MYRM + "', 'carebara', null),"
            + " ('" + INDET + "', 'species', 'indetermined', '" + CAREBARA + "', 'formicidae', '" + MYRM + "', 'carebara', '(indet)'),"
            + " ('" + MG01 + "', 'species', 'morphotaxon', '" + CAREBARA + "', 'formicidae', '" + MYRM + "', 'carebara', 'mg01'),"
            + " ('" + MG02 + "', 'species', 'morphotaxon', '" + CAREBARA + "', 'formicidae', '" + MYRM + "', 'carebara', 'mg02'),"
            + " ('" + ZZ + "', 'species', 'unrecognized', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "', 'pheidole', 'zz')");
        // mg02 is curated: a curator's entry on Mozambique's list
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (40, '" + MG02 + "', 'speciesListTool')");
        specimen("c1", INDET, "indetermined", "Madagascar", null, null);
        specimen("cred", INDET, "indetermined", "Madagascar", null, "red");
        specimen("m1", MG01, "morphotaxon", "Madagascar", null, null);
        specimen("m2", MG01, "morphotaxon", "Madagascar", null, null);
        specimen("m3", MG01, "morphotaxon", null, "Madagascar", null);
        specimen("n1", MG02, "morphotaxon", "Madagascar", "Madagascar", null);
        specimen("z1", ZZ, "unrecognized", "Madagascar", null, null);
        exec("update specimen set genus = 'pheidole' where taxon_name like 'myrmicinaepheidole%'");
        exec("update specimen set genus = 'hypoponera' where taxon_name like 'ponerinaehypoponera%'");
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    private void exec(String sql) throws Exception {
        NumbersTestDb.exec(connection, sql);
    }

    /** A specimen in museum CASC, bioregion Malagasy, adm1 Toliara. */
    private void specimen(String code, String name, String status, String country, String islandCountry, String flag) throws Exception {
        String genus = name.startsWith("myrmicinaecarebara") ? "carebara" : "pheidole";
        exec("insert into specimen (code, taxon_name, status, country, island_country, adm1, museum, bioregion, family, subfamily, genus, flag)"
            + " values ('" + code + "', '" + name + "', '" + status + "', " + q(country) + ", " + q(islandCountry) + ", 'Toliara', 'CASC',"
            + " 'Malagasy', 'formicidae', '" + MYRM + "', '" + genus + "', " + q(flag) + ")");
    }

    private static String q(String s) {
        return s == null ? "null" : "'" + s + "'";
    }

    private static Country madagascar() {
        Country c = new Country();
        c.setId(MADAGASCAR);
        c.setName("Madagascar");
        return c;
    }

    private PlaceGenusCurationDb.Row row(String type, String key, String genusTaxonName) throws Exception {
        try (PreparedStatement s = connection.prepareStatement("select specimens, not_curated, morpho_names, morpho_specimens, indet_specimens,"
                + " other_specimens, subfamily, genus from place_genus_curation where list_type = ? and list_key = ? and taxon_name = ?")) {
            s.setString(1, type);
            s.setString(2, key);
            s.setString(3, genusTaxonName);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return null;
                PlaceGenusCurationDb.Row row = new PlaceGenusCurationDb.Row();
                row.specimens = r.getInt(1);
                row.notCurated = r.getInt(2);
                row.morphoNames = r.getInt(3);
                row.morphoSpecimens = r.getInt(4);
                row.indetSpecimens = r.getInt(5);
                row.otherSpecimens = r.getInt(6);
                row.subfamily = r.getString(7);
                row.genus = r.getString(8);
                return row;
            }
        }
    }

    /**
     * Madagascar: Carebara 5 specimens (the red-flagged one left out), 4 not curated: mg01 3 (one only on island_country)
     * and the indet 1; mg02 is curated. Pheidole 5 specimens, 2 not curated: b (an uncurated morphospecies) and zz
     * (unrecognized); a and a x are valid. Hypoponera: nothing not curated.
     */
    public void testFillCountsTheSpecimensNotCurated() throws Exception {
        String log = new PlaceGenusCurationDb(connection).fillAll();
        assertTrue(log, log.startsWith(PlaceGenusCurationDb.LOG + ": country "));
        PlaceGenusCurationDb.Row c = row("country", "Madagascar", CAREBARA);
        assertEquals(5, c.specimens);
        assertEquals(4, c.notCurated);
        assertEquals(1, c.morphoNames);
        assertEquals("mg01: country 2 + island_country 1; never mg02 (curated)", 3, c.morphoSpecimens);
        assertEquals("the indet name, the red-flagged one left out", 1, c.indetSpecimens);
        assertEquals(0, c.otherSpecimens);
        assertEquals("carebara", c.genus);
        PlaceGenusCurationDb.Row p = row("country", "Madagascar", PHEIDOLE);
        assertEquals(5, p.specimens);
        assertEquals("b and zz; a and a x are valid", 2, p.notCurated);
        assertEquals(1, p.morphoNames);
        assertEquals(1, p.morphoSpecimens);
        assertEquals(0, p.indetSpecimens);
        assertEquals("zz: unrecognized", 1, p.otherSpecimens);
        PlaceGenusCurationDb.Row h = row("country", "Madagascar", HYPO);
        assertEquals(1, h.specimens);
        assertEquals(0, h.notCurated);
        assertNull("no specimens in Mozambique: no row", row("country", "Mozambique", CAREBARA));
        // a curator puts Pheidole b on a list: curated everywhere the next night
        exec("insert into proj_taxon (project_name, taxon_name, source) values ('madants2', '" + PB + "', 'speciesListTool')");
        exec("insert into project (project_name, is_live) values ('madants2', 1)");
        new PlaceGenusCurationDb(connection).fill(PlaceGenusCurationDb.COUNTRY);
        assertEquals("only zz now", 1, row("country", "Madagascar", PHEIDOLE).notCurated);
        assertEquals(0, row("country", "Madagascar", PHEIDOLE).morphoNames);
    }

    /** adm1s by specimen.adm1, museums by code, bioregions by name; the same rule. */
    public void testOtherKindsOfPlaces() throws Exception {
        new PlaceGenusCurationDb(connection).fillAll();
        assertEquals("adm1 Toliara: mg01 3 + indet 1", 4, row("adm1", "Toliara", CAREBARA).notCurated);
        assertEquals("CASC: every Carebara specimen here", 4, row("museum", "CASC", CAREBARA).notCurated);
        assertEquals("CASC Pheidole: b (casent3) and zz", 2, row("museum", "CASC", PHEIDOLE).notCurated);
        assertEquals("MCZC: Hypoponera b, valid", 0, row("museum", "MCZC", HYPO).notCurated);
        assertEquals(4, row("bioregion", "Malagasy", CAREBARA).notCurated);
        assertEquals("Malagasy Pheidole: zz only (casent3 has no bioregion)", 1, row("bioregion", "Malagasy", PHEIDOLE).notCurated);
        // one kind again replaces only its own rows
        exec("update specimen set flag = 'red' where code = 'm1'");
        int[] n = new PlaceGenusCurationDb(connection).fill(PlaceGenusCurationDb.MUSEUM);
        assertTrue(n[0] >= 2 && n[1] >= 3);
        assertEquals(3, row("museum", "CASC", CAREBARA).notCurated);
        assertEquals("other kinds wait for their own fill", 4, row("adm1", "Toliara", CAREBARA).notCurated);
        try {
            new PlaceGenusCurationDb(connection).fill("region");
            fail("regions have no column");
        } catch (IllegalArgumentException expected) {
        }
    }

    /** The page reads the stored numbers in one query; no rows or no table: null (empty cells), never an error. */
    public void testPageReadsTheStoredNumbers() throws Exception {
        assertNull("not counted yet", PlaceGenusCurationDb.read(connection, madagascar()));
        new PlaceGenusCurationDb(connection).fillAll();
        Map<String, PlaceGenusCurationDb.Row> rows = PlaceGenusCurationDb.read(connection, madagascar());
        assertEquals(4, rows.get(PlaceGenusCurationDb.key(MYRM, "carebara")).notCurated);
        assertEquals("4 specimens not curated: 1 morphospecies (3 specimens), 1 identified only to genus",
            PlaceGenusCurationDb.read(connection, madagascar(), MYRM, "Carebara").breakdown());
        Museum casc = new Museum();
        casc.setCode("CASC");
        assertNotNull(PlaceGenusCurationDb.read(connection, casc));
        Adm1 toliara = new Adm1();
        toliara.setName("Toliara");
        assertNotNull(PlaceGenusCurationDb.read(connection, toliara));
        Bioregion malagasy = new Bioregion();
        malagasy.setName("Malagasy");
        assertNotNull(PlaceGenusCurationDb.read(connection, malagasy));
        Subregion io = new Subregion();
        io.setId(INDIAN_OCEAN);
        io.setName("Indian Ocean");
        assertNull("subregions have no column", PlaceGenusCurationDb.read(connection, io));
        assertNull("projects neither", PlaceGenusCurationDb.read(connection, new Project("madants")));
        // the genus rows of the list: the same number in Valid and Curated, none in All specimen names
        for (String choice : new String[] {"valid", "curated"}) {
            ListView.GenusRows g = ListView.loadGenusRows(connection, madagascar(), choice, null);
            assertEquals(choice, 4, g.notCurated(MYRM, "carebara").notCurated);
            assertEquals(choice, 2, g.notCurated(MYRM, "pheidole").notCurated);
            assertTrue(choice, g.hasSpecimens(MYRM, "carebara"));
        }
        assertNull(ListView.loadGenusRows(connection, madagascar(), "all", null).notCurated(MYRM, "carebara"));
        exec("drop table place_genus_curation");
        assertNull("table missing: no numbers, no error", PlaceGenusCurationDb.read(connection, madagascar()));
        exec("drop table place_specimen_name");
        assertFalse(PlaceGenusCurationDb.namesFilled(connection, "country"));
        assertNull("no stored names: the genus list reads the specimens", ListView.loadStoredPlaceSpecimens(connection, madagascar(), null));
        assertNotNull(ListView.genusListSpecimens(connection, madagascar(), null));
    }

    private List<String> names(String sql) throws Exception {
        List<String> out = new ArrayList<>();
        try (PreparedStatement s = connection.prepareStatement(sql); ResultSet r = s.executeQuery()) {
            while (r.next()) out.add(r.getString(1));
        }
        Collections.sort(out);
        return out;
    }

    /**
     * The view Not curated of a genus in the place (the click on the number): the names whose specimens the number counts,
     * from the list and from the specimens collected here (a country's list has no uncurated morphospecies).
     */
    public void testNotCuratedViewListsThoseNames() throws Exception {
        StatusSet nc = StatusSet.listStatusSet(StatusSet.LIST_NOT_CURATED);
        String carebara = TaxaPage.viewQuery(madagascar(), "species", true, nc, "taxon.taxon_name", " and taxon.genus = 'carebara'");
        assertEquals("[" + INDET + ", " + MG01 + "]", names(carebara).toString());
        String pheidole = TaxaPage.viewQuery(madagascar(), "species", true, nc, "taxon.taxon_name", " and taxon.genus = 'pheidole'");
        assertEquals("[" + PB + ", " + ZZ + "]", names(pheidole).toString());
        // the same from the names stored nightly
        new PlaceGenusCurationDb(connection).fillAll();
        ListView.PlaceSpecimens stored = ListView.loadStoredPlaceSpecimens(connection, madagascar(), null);
        String fromStored = TaxaPage.viewQueryHere(madagascar(), "species", true, nc, "taxon.taxon_name", " and taxon.genus = 'carebara'",
            ListView.hereCondition("taxon", stored));
        assertEquals(names(carebara), names(fromStored));
        assertEquals("the stored names hold the island_country specimen too", 3, stored.count(MG01));
        assertFalse("never a red-flagged specimen", stored.count(INDET) > 1);
    }

    /** The fill's own SQL: one grouped query per kind, no query per place; both tables replaced in one transaction. */
    public void testFillIsSetBased() throws Exception {
        String names = PlaceGenusCurationDb.fillNamesSql(PlaceGenusCurationDb.COUNTRY);
        assertTrue(names, names.contains("group by left(x.place, 128), x.taxon_name"));
        assertTrue(names, names.contains("union all select specimen.island_country place"));
        assertTrue(names, names.contains("(specimen.flag is null or specimen.flag != 'red')"));
        assertTrue("two spellings of one key add up, not fail", names.contains("on duplicate key update specimens = specimens + values(specimens)"));
        String genus = PlaceGenusCurationDb.fillGenusSql(PlaceGenusCurationDb.MUSEUM);
        assertTrue(genus, genus.contains(ListView.notCuratedCondition("t")));
        assertTrue(genus, genus.contains("group by p.list_type, p.list_key, t.subfamily, t.genus"));
        connection.setAutoCommit(true);
        new PlaceGenusCurationDb(connection).fill(PlaceGenusCurationDb.COUNTRY);
        assertTrue("autocommit restored", connection.getAutoCommit());
    }
}
