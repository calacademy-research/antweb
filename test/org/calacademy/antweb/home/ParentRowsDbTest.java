package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import junit.framework.TestCase;

import org.calacademy.antweb.Project;
import org.calacademy.antweb.home.ParentRows;
import org.calacademy.antweb.home.PlaceTotals;
import org.calacademy.antweb.home.ProjTaxonCountDb;

import test.org.calacademy.antweb.util.NumbersTestDb;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * B76 (Brian Fisher, 2026-11-03: "missing parent rows: add them"): the nightly count adds the genus row of every counted
 * species and the subfamily row of every counted genus of a list that lacks it (ParentRows), on every kind of list, so the
 * admin column totals of the lists equal the place's numbers. Placeholders, names of another status, names with no row
 * in the taxon table (B36) and parents a curator removed from the list are not added. Against NumbersTestDb.
 *
 * Runs in CI against MySQL 8.4 (host "mysql"); ANTWEB_TEST_DB_URL overrides.
 */
public class ParentRowsDbTest extends TestCase {

    private Connection connection;

    /** Every list of the fixture: type, key column value, list table, key column. */
    static final String[][] LISTS = {
        {PlaceTotals.GEOLOCALE, "" + MADAGASCAR}, {PlaceTotals.PROJECT, "madants"}, {PlaceTotals.MUSEUM, "CASC"},
        {PlaceTotals.BIOREGION, "Malagasy"}};

    @Override
    protected void setUp() throws Exception {
        connection = connect();
        fill(connection);
        // the columns of the live tables that the rows get (museum lists have neither)
        exec("alter table geolocale_taxon add column insert_method varchar(64)");
        exec("alter table bioregion_taxon add column insert_method varchar(64)");
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    private void exec(String sql) throws Exception {
        NumbersTestDb.exec(connection, sql);
    }

    private String one(String sql) throws Exception {
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getString(1) : null;
        }
    }

    private static String quoted(String type, String key) {
        return PlaceTotals.GEOLOCALE.equals(type) ? key : "'" + key + "'";
    }

    private boolean onList(String type, String key, String name) throws Exception {
        return one("select count(*) from " + PlaceTotals.listTable(type) + " where " + PlaceTotals.listKey(type) + " = " + quoted(type, key)
            + " and taxon_name = '" + name + "'").equals("1");
    }

    private void remove(String type, String key, String name) throws Exception {
        exec("delete from " + PlaceTotals.listTable(type) + " where " + PlaceTotals.listKey(type) + " = " + quoted(type, key)
            + " and taxon_name = '" + name + "'");
    }

    public void testMissingGenusAndSubfamilyRowsAreAddedOnEveryKindOfList() throws Exception {
        for (String[] l : LISTS) {
            remove(l[0], l[1], PHEIDOLE);
            remove(l[0], l[1], MYRM);
        }
        exec("update geolocale_taxon set source = 'specimen' where geolocale_id = " + MADAGASCAR + " and taxon_name = '" + PA + "'");
        exec("update geolocale_taxon set source = 'speciesListTool' where geolocale_id = " + MADAGASCAR + " and taxon_name = '" + PB + "'");
        for (String[] l : LISTS) {
            int added = new ParentRows(connection).add(l[0], l[1]);
            assertEquals(l[0] + " " + l[1], 2, added);
            assertTrue(l[0] + " genus row", onList(l[0], l[1], PHEIDOLE));
            assertTrue(l[0] + " subfamily row (the parent of the new genus row)", onList(l[0], l[1], MYRM));
            assertEquals(l[0] + " nothing more to add", 0, new ParentRows(connection).add(l[0], l[1]));
        }
        // the conventions of the derived rows: proxy + the best source of the children (specimen trumps curator)
        assertEquals("proxyspecimen", one("select source from geolocale_taxon where geolocale_id = " + MADAGASCAR + " and taxon_name = '" + PHEIDOLE + "'"));
        assertEquals(ParentRows.INSERT_METHOD, one("select insert_method from geolocale_taxon where geolocale_id = " + MADAGASCAR
            + " and taxon_name = '" + MYRM + "'"));
        assertEquals("proxyspecimen", one("select source from geolocale_taxon where geolocale_id = " + MADAGASCAR + " and taxon_name = '" + MYRM + "'"));
        // other lists: no source on the children, none on the parents
        assertNull(one("select source from proj_taxon where project_name = 'madants' and taxon_name = '" + PHEIDOLE + "'"));
    }

    public void testPlaceholderUncountedAndUnknownParentsAreNotAdded() throws Exception {
        // a morphospecies under the placeholder genus "(myrmicinae)", a valid species under a synonym genus, a valid species
        // whose genus has no taxon row (B36), a genus under a subfamily of another status
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily) values"
            + " ('myrmicinae(myrmicinae)', 'genus', 'morphotaxon', '" + MYRM + "', 'formicidae', '" + MYRM + "'),"
            + " ('myrmicinae(myrmicinae) zz1', 'species', 'morphotaxon', 'myrmicinae(myrmicinae)', 'formicidae', '" + MYRM + "'),"
            + " ('myrmicinaeoldgenus', 'genus', 'synonym', '" + MYRM + "', 'formicidae', '" + MYRM + "'),"
            + " ('myrmicinaeoldgenus a', 'species', 'valid', 'myrmicinaeoldgenus', 'formicidae', '" + MYRM + "'),"
            + " ('myrmicinaenogenus a', 'species', 'valid', 'myrmicinaenogenus', 'formicidae', '" + MYRM + "'),"
            + " ('agroecomyrmecinae', 'subfamily', 'synonym', 'formicidae', 'formicidae', 'agroecomyrmecinae'),"
            + " ('agroecomyrmecinaetatuidris', 'genus', 'valid', 'agroecomyrmecinae', 'formicidae', 'agroecomyrmecinae')");
        for (String n : new String[] {"myrmicinae(myrmicinae) zz1", "myrmicinaeoldgenus a", "myrmicinaenogenus a", "agroecomyrmecinaetatuidris"}) {
            exec("insert into proj_taxon (project_name, taxon_name) values ('madants', '" + n + "')");
        }
        assertEquals(0, new ParentRows(connection).add(PlaceTotals.PROJECT, "madants"));
        for (String n : new String[] {"myrmicinae(myrmicinae)", "myrmicinaeoldgenus", "myrmicinaenogenus", "agroecomyrmecinae"}) {
            assertFalse(n, onList(PlaceTotals.PROJECT, "madants", n));
        }
    }

    public void testAParentACuratorRemovedIsNotAddedBack() throws Exception {
        remove(PlaceTotals.GEOLOCALE, "" + MADAGASCAR, PHEIDOLE);
        exec("insert into geolocale_taxon_dispute (geolocale_id, taxon_name, source) values (" + MADAGASCAR + ", '" + PHEIDOLE + "', 'speciesListTool')");
        assertEquals(0, new ParentRows(connection).add(PlaceTotals.GEOLOCALE, "" + MADAGASCAR));
        assertFalse(onList(PlaceTotals.GEOLOCALE, "" + MADAGASCAR, PHEIDOLE));
    }

    /** The nightly count of a project list: the rows' numbers add up to the place's Genera and Species again. */
    public void testTheCountAddsThemAndTheRowsAddUpToThePlaceNumbers() throws Exception {
        remove(PlaceTotals.PROJECT, "madants", PHEIDOLE);
        remove(PlaceTotals.PROJECT, "madants", MYRM);
        Project p = new Project();
        p.setName("madants");
        ProjTaxonCountDb crawl = new ProjTaxonCountDb(connection);
        crawl.recount(p);
        assertEquals(2, crawl.getParentRowsAdded());
        String genera = one("select sum(l.genus_count) from proj_taxon l join taxon t on t.taxon_name = l.taxon_name where l.project_name = 'madants'"
            + " and " + PlaceTotals.rankCondition(PlaceTotals.SUBFAMILY));
        String species = one("select sum(l.species_count) from proj_taxon l join taxon t on t.taxon_name = l.taxon_name where l.project_name = 'madants'"
            + " and " + PlaceTotals.rankCondition(PlaceTotals.GENUS));
        assertEquals("2", genera);
        assertEquals("4", species);
        assertEquals(Integer.parseInt(genera), PlaceTotals.count(connection, PlaceTotals.listedCountSql(PlaceTotals.PROJECT, PlaceTotals.GENUS), "madants"));
        assertEquals(Integer.parseInt(species), PlaceTotals.count(connection, PlaceTotals.listedCountSql(PlaceTotals.PROJECT, PlaceTotals.SPECIES), "madants"));
    }
}
