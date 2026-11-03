package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.calacademy.antweb.ListView;
import org.calacademy.antweb.ProjTaxon;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.geolocale.GeolocaleTaxon;
import org.calacademy.antweb.home.GeolocaleTaxonLogDb;
import org.calacademy.antweb.home.ListRemovalLog;
import org.calacademy.antweb.home.ProjTaxonLogDb;
import org.calacademy.antweb.home.TaxonSetDb;

import test.org.calacademy.antweb.util.NumbersTestDb;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * Brian Fisher, 2026-11-03 (transparency: "if something is deleted, we still need transparency"): a name removed from a
 * place's list keeps a record. The Species List Tool's dispute keeps the curator's reason; list_removal_log records the
 * curator's removals and the jobs' (the nightly clean-up); the view All specimen names reads them in one load per page
 * (ListView.loadRemovals). Against NumbersTestDb.
 *
 * Runs in CI against MySQL 8.4 (host "mysql"); ANTWEB_TEST_DB_URL overrides.
 */
public class ListRemovalDbTest extends TestCase {

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        connection = connect();
        fill(connection);
        exec("drop table if exists " + ListRemovalLog.TABLE);
        ListRemovalLog.forgetSchema();
        ListRemovalLog.ensureSchema(connection);
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    private void exec(String sql) throws Exception {
        NumbersTestDb.exec(connection, sql);
    }

    private List<String> rows(String sql) throws Exception {
        List<String> out = new ArrayList<>();
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            int n = r.getMetaData().getColumnCount();
            while (r.next()) {
                StringBuilder b = new StringBuilder();
                for (int i = 1; i <= n; i++) b.append(i > 1 ? "|" : "").append(r.getString(i));
                out.add(b.toString());
            }
        }
        return out;
    }

    private static Country madagascar() {
        Country mad = new Country();
        mad.setId(MADAGASCAR);
        mad.setName("Madagascar");
        return mad;
    }

    public void testSchemaAddsTheReasonColumnsAndTheTable() throws Exception {
        assertEquals(1, rows("select 1 from information_schema.columns where table_schema = database()"
            + " and table_name = 'geolocale_taxon_dispute' and column_name = 'reason'").size());
        assertEquals(1, rows("select 1 from information_schema.columns where table_schema = database()"
            + " and table_name = 'proj_taxon_dispute' and column_name = 'reason'").size());
        ListRemovalLog.forgetSchema();
        ListRemovalLog.ensureSchema(connection);   // a second run changes nothing
        assertEquals(0, rows("select * from " + ListRemovalLog.TABLE).size());
    }

    /** The tool's removal path: the dispute with the reason (cut to 255 characters), and the record of the save. */
    public void testACuratorRemovalKeepsTheReason() throws Exception {
        GeolocaleTaxon gt = new GeolocaleTaxon();
        gt.setGeolocaleId(MADAGASCAR);
        gt.setTaxonName(PB);
        gt.setSource("speciesListTool");
        gt.setCuratorId(5);
        new GeolocaleTaxonLogDb(connection).insertDispute(gt, "  not in Madagascar:\n a <b>Comoros</b> record  ");
        assertEquals("not in Madagascar: a <b>Comoros</b> record", rows("select reason from geolocale_taxon_dispute where taxon_name = '" + PB + "'").get(0));
        ProjTaxon pt = new ProjTaxon();
        pt.setProjectName("madants");
        pt.setTaxonName(PB);
        pt.setSource("speciesListTool");
        pt.setCuratorId(5);
        StringBuilder longReason = new StringBuilder();
        for (int i = 0; i < 40; i++) longReason.append("too long ");
        new ProjTaxonLogDb(connection).insertDispute(pt, longReason.toString());
        assertEquals(255, rows("select reason from proj_taxon_dispute where taxon_name = '" + PB + "'").get(0).length());
        // no reason: the dispute as before, reason null
        gt.setTaxonName(PA);
        new GeolocaleTaxonLogDb(connection).insertDispute(gt, "   ");
        assertEquals("null", rows("select reason from geolocale_taxon_dispute where taxon_name = '" + PA + "'").get(0));

        List<ListRemovalLog.Entry> entries = new ArrayList<>();
        entries.add(new ListRemovalLog.Entry(ListRemovalLog.GEOLOCALE, "" + MADAGASCAR, PB, "speciesListTool", 5, null, "not in Madagascar"));
        assertEquals(1, ListRemovalLog.record(connection, entries));
        assertEquals("geolocale|" + MADAGASCAR + "|" + PB + "|speciesListTool|5|null|not in Madagascar",
            rows("select list_type, list_key, taxon_name, source, removed_by_login, removed_by_job, reason from " + ListRemovalLog.TABLE).get(0));
    }

    /** The nightly clean-up records the rows it removes (not the rows it keeps), before it deletes them. */
    public void testTheNightlyCleanUpRecordsWhatItRemoves() throws Exception {
        // a name no longer in the taxon table on Madagascar's list (from specimens) and on madants (a literature entry);
        // a morphospecies without specimens on madants (not a curator's row)
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (" + MADAGASCAR + ", 'myrmicinaepheidole gone', 'specimen')");
        exec("insert into proj_taxon (project_name, taxon_name, source) values ('madants', 'myrmicinaepheidole gone', 'literature')");
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily) values"
            + " ('myrmicinaepheidole zz9', 'species', 'morphotaxon', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "')");
        exec("insert into proj_taxon (project_name, taxon_name, source) values ('madants', 'myrmicinaepheidole zz9', 'specimen')");
        TaxonSetDb.dataCleanup(connection, true);
        assertEquals(0, rows("select * from proj_taxon where taxon_name in ('myrmicinaepheidole gone', 'myrmicinaepheidole zz9')").size());
        List<String> log = rows("select list_type, list_key, taxon_name, source, removed_by_job from " + ListRemovalLog.TABLE
            + " order by list_type, taxon_name");
        assertEquals(log.toString(), 3, log.size());
        assertEquals("geolocale|" + MADAGASCAR + "|myrmicinaepheidole gone|specimen|" + ListRemovalLog.JOB_NO_TAXON, log.get(0));
        assertEquals("project|madants|myrmicinaepheidole gone|literature|" + ListRemovalLog.JOB_NO_TAXON, log.get(1));
        assertEquals("project|madants|myrmicinaepheidole zz9|specimen|" + ListRemovalLog.JOB_MORPHO_NO_SPECIMEN, log.get(2));
        // a second run removes and records nothing more
        TaxonSetDb.dataCleanup(connection, true);
        assertEquals(3, rows("select * from " + ListRemovalLog.TABLE).size());
    }

    /** The page's load: who, when and why, for names not on the list now; the dispute when it is the same save. */
    public void testThePageLoadsWhoRemovedTheNamesAndWhy() throws Exception {
        assertNull("nothing removed", ListView.loadRemovals(connection, madagascar()));
        exec("delete from geolocale_taxon where geolocale_id = " + MADAGASCAR + " and taxon_name = '" + PB + "'");
        GeolocaleTaxon gt = new GeolocaleTaxon();
        gt.setGeolocaleId(MADAGASCAR);
        gt.setTaxonName(PB);
        gt.setSource("speciesListTool");
        gt.setCuratorId(5);
        new GeolocaleTaxonLogDb(connection).insertDispute(gt, "a Comoros record");
        exec("update geolocale_taxon_dispute set created = now()");
        List<ListRemovalLog.Entry> entries = new ArrayList<>();
        entries.add(new ListRemovalLog.Entry(ListRemovalLog.GEOLOCALE, "" + MADAGASCAR, PB, "speciesListTool", 5, null, "a Comoros record"));
        ListRemovalLog.record(connection, entries);
        // a job's removal of a name no longer on the list, and one of a name back on the list (not marked)
        exec("insert into " + ListRemovalLog.TABLE + " (list_type, list_key, taxon_name, removed_by_job, reason, removed_at) values"
            + " ('geolocale', '" + MADAGASCAR + "', 'myrmicinaepheidole zz9', '" + ListRemovalLog.JOB_MORPHO_NO_SPECIMEN + "', null, '2026-11-01 05:30:00'),"
            + " ('geolocale', '" + MADAGASCAR + "', '" + PA + "', '" + ListRemovalLog.JOB_NO_TAXON + "', null, '2026-11-01 05:30:00')");
        Map<String, ListView.Removal> removals = ListView.loadRemovals(connection, madagascar());
        assertEquals(removals.keySet().toString(), 2, removals.size());
        ListView.Removal pb = removals.get(PB);
        assertEquals("a Comoros record", pb.reason);
        assertNotNull(pb.date);
        assertTrue(pb.text(), pb.text().startsWith("removed from the list by "));   // the curator (no logins in memory here: "a curator")
        assertEquals(ListRemovalLog.JOB_MORPHO_NO_SPECIMEN, removals.get("myrmicinaepheidole zz9").by);
        assertNull("back on the list: not marked", removals.get(PA));
        assertTrue(ListView.removedMarkHtml(removals, PB).contains(": a Comoros record</span>"));
        // the one-off script's SQL writes the same record
        exec(ListRemovalLog.scriptSql(ListRemovalLog.PROJECT, "script 2026-11-03 test", "a test's reason", "l.taxon_name = '" + HB + "'"));
        assertEquals("project|madants|" + HB + "|script 2026-11-03 test|a test's reason",
            rows("select list_type, list_key, taxon_name, removed_by_job, reason from " + ListRemovalLog.TABLE
                + " where list_type = 'project' and list_key = 'madants'").get(0));
    }
}
