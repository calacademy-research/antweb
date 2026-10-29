/*
 * BlankNameCleanupDbTest: the cleanups of names and list rows without specimens with specimens of blank name present
 * (Brian Fisher, 2026-10-29), against MySQL 8.4 in CI (host "mysql"; ANTWEB_TEST_DB_URL overrides).
 */
package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.home.BlankNameCleanup;
import org.calacademy.antweb.home.CuratorTaxonSetDb;
import org.calacademy.antweb.home.GeolocaleTaxonDb;
import org.calacademy.antweb.home.OrphansDb;
import org.calacademy.antweb.home.ProjTaxonDb;
import org.calacademy.antweb.home.TaxonDb;

import test.org.calacademy.antweb.util.NumbersTestDb;

/**
 * A database with specimens whose taxon name is NULL or empty (and one without subfamily or genus). The old rules
 * ("not in (select taxon_name from specimen)") delete nothing at all; the blank-safe rules delete exactly the names and
 * list rows no specimen uses, never a name AntCat lists, a name with a specimen, a curator's list row, a blank name row
 * or a genus row without a subfamily; the preview counts what each rule deletes.
 */
public class BlankNameCleanupDbTest extends TestCase {

    private Connection c;

    static final String PA = "myrmicinaepheidole a";          // a specimen: kept
    static final String PZ = "myrmicinaepheidole zz";         // no specimen, not in AntCat: deleted
    static final String PW = "myrmicinaepheidole w";          // in AntCat's list (worldants): kept
    static final String GP = "myrmicinaepheidole";            // genus with specimens: kept
    static final String GO = "myrmicinaeorphanella";          // genus without specimens: deleted
    static final String GN = "nosubfamilygenus";              // genus row without a subfamily: kept
    static final String IX = "myrmicinaepheidole (indet)x";   // indet from an upload, no specimen: deleted
    static final String IK = "myrmicinaepheidole (indet)k";   // indet with a specimen: kept
    static final String MO = "myrmicinaepheidole jtl001";     // morphospecies, no specimen: its uncurated list rows go

    @Override
    protected void setUp() throws Exception {
        c = NumbersTestDb.connect();
        for (String t : new String[] {"taxon", "specimen", "proj_taxon", "geolocale_taxon"}) exec("drop table if exists " + t);
        exec("create table taxon (taxon_name varchar(128) primary key, taxarank varchar(20), status varchar(40), source varchar(100),"
            + " subfamily varchar(64), genus varchar(64))");
        exec("create table specimen (code varchar(40) primary key, taxon_name varchar(128), subfamily varchar(64), genus varchar(64), species varchar(64))");
        exec("create table proj_taxon (project_name varchar(64), taxon_name varchar(128), source varchar(30), primary key (project_name, taxon_name))");
        exec("create table geolocale_taxon (geolocale_id int, taxon_name varchar(128), source varchar(30), primary key (geolocale_id, taxon_name))");
        taxon(PA, "species", "valid", "specimen1", "myrmicinae", "pheidole");
        taxon(PZ, "species", "valid", "specimen1", "myrmicinae", "pheidole");
        taxon(PW, "species", "valid", "worldants", "myrmicinae", "pheidole");
        taxon("", "species", "unrecognized", "specimen1", null, null);
        taxon(GP, "genus", "valid", "worldants", "myrmicinae", "pheidole");
        taxon(GO, "genus", "morphotaxon", "specimen1", "myrmicinae", "orphanella");
        taxon(GN, "genus", "morphotaxon", "specimen1", null, "nosubfamilygenus");
        taxon(IX, "species", "indetermined", "specimen1", "myrmicinae", "pheidole");
        taxon(IK, "species", "indetermined", "specimen1", "myrmicinae", "pheidole");
        taxon(MO, "species", "morphotaxon", "specimen1", "myrmicinae", "pheidole");
        exec("insert into proj_taxon values ('worldants', '" + PW + "', 'worldants'), ('worldants', '" + GP + "', 'worldants'),"
            + " ('madants', '" + MO + "', 'specimen'), ('bayareaants', '" + MO + "', 'curator')");
        exec("insert into geolocale_taxon values (7, '" + MO + "', 'specimen'), (8, '" + MO + "', 'curator'), (7, '" + PA + "', 'specimen')");
        exec("insert into specimen values ('c1', '" + PA + "', 'myrmicinae', 'pheidole', 'a'), ('c2', null, null, null, null),"
            + " ('c3', '', 'myrmicinae', 'pheidole', ''), ('c4', '" + IK + "', 'myrmicinae', 'pheidole', '(indet)k')");
    }

    @Override
    protected void tearDown() throws Exception {
        if (c != null) c.close();
    }

    private void exec(String sql) throws Exception {
        NumbersTestDb.exec(c, sql);
    }

    private void taxon(String name, String rank, String status, String source, String subfamily, String genus) throws Exception {
        exec("insert into taxon values ('" + name + "', '" + rank + "', '" + status + "', '" + source + "', "
            + (subfamily == null ? "null" : "'" + subfamily + "'") + ", " + (genus == null ? "null" : "'" + genus + "'") + ")");
    }

    private List<String> names(String sql) throws Exception {
        List<String> list = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            while (r.next()) list.add(r.getString(1));
        }
        return list;
    }

    private boolean hasTaxon(String name) throws Exception {
        return !names("select taxon_name from taxon where taxon_name = '" + name + "'").isEmpty();
    }

    private BlankNameCleanup.Count count(List<BlankNameCleanup.Count> counts, String prefix) {
        for (BlankNameCleanup.Count n : counts) if (n.name.startsWith(prefix)) return n;
        fail("no count " + prefix);
        return null;
    }

    public void testOldRulesDeleteNothingWhileASpecimenHasNoName() throws Exception {
        assertEquals("0 deleteSpeciesWithoutSpecimenOrAntcatSource. ", TaxonDb.deleteSpeciesWithoutSpecimenOrAntcatSource(c, false));
        assertEquals("0 deletedGeneraWithoutSpecimenOrAntcatSource. ", TaxonDb.deleteGeneraWithoutSpecimenOrAntcatSource(c, false));
        assertEquals(0, new TaxonDb(c).removeIndetWithoutSpecimen(false));
        assertTrue(names(OrphansDb.getOrphanedSpeciesQuery("specimen", "", false)).isEmpty());
        assertTrue(hasTaxon(PZ) && hasTaxon(GO) && hasTaxon(IX));
    }

    public void testBlankSafeRulesDeleteWhatNoSpecimenUses() throws Exception {
        assertEquals("3 deleteSpeciesWithoutSpecimenOrAntcatSource (blank-safe). ", TaxonDb.deleteSpeciesWithoutSpecimenOrAntcatSource(c, true));
        // PZ, IX and MO (species not in AntCat's list without a specimen), not the AntCat name, not the blank row
        assertFalse(hasTaxon(PZ) || hasTaxon(IX) || hasTaxon(MO));
        assertTrue(hasTaxon(PA) && hasTaxon(PW) && hasTaxon("") && hasTaxon(IK));
        assertEquals("1 deletedGeneraWithoutSpecimenOrAntcatSource (blank-safe). ", TaxonDb.deleteGeneraWithoutSpecimenOrAntcatSource(c, true));
        assertFalse(hasTaxon(GO));
        assertTrue(hasTaxon(GP) && hasTaxon(GN));
    }

    public void testUploadCleanupsAreBlankSafe() throws Exception {
        List<String> orphans = names(OrphansDb.getOrphanedSpeciesQuery("specimen", " order by taxon_name", true).replace("select source, taxon_name", "select taxon_name"));
        assertEquals(List.of(IX, MO, PZ), orphans);   // not the blank row, not names with a specimen, species ranks only
        assertEquals(1, new TaxonDb(c).removeIndetWithoutSpecimen(true));
        assertFalse(hasTaxon(IX));
        assertTrue(hasTaxon(IK) && hasTaxon(""));
    }

    public void testMorphospeciesListRowsKeepTheCuratorsRows() throws Exception {
        new ProjTaxonDb(c).deleteUncuratedMorphosWithoutSpecimen(false);
        new GeolocaleTaxonDb(c).deleteUncuratedMorphosWithoutSpecimen(false);
        assertEquals(4, names("select taxon_name from proj_taxon").size());
        assertEquals(3, names("select taxon_name from geolocale_taxon").size());
        new ProjTaxonDb(c).deleteUncuratedMorphosWithoutSpecimen(true);
        new GeolocaleTaxonDb(c).deleteUncuratedMorphosWithoutSpecimen(true);
        assertEquals(List.of("bayareaants"), names("select project_name from proj_taxon where taxon_name = '" + MO + "'"));
        assertEquals(List.of("8"), names("select geolocale_id from geolocale_taxon where taxon_name = '" + MO + "'"));
        assertEquals(1, names("select taxon_name from geolocale_taxon where taxon_name = '" + PA + "'").size());
    }

    public void testPreviewCountsWhatEachRuleDeletes() throws Exception {
        List<BlankNameCleanup.Count> counts = BlankNameCleanup.counts(c, CuratorTaxonSetDb.curatorSourceList());
        assertEquals(6, counts.size());
        for (BlankNameCleanup.Count n : counts) assertEquals(n.name, 0, n.oldRule);
        assertEquals(3, count(counts, "speciesWithoutSpecimen").blankSafe);
        assertEquals(List.of(IX, MO, PZ), count(counts, "speciesWithoutSpecimen").examples);
        assertEquals(1, count(counts, "generaWithoutSpecimen").blankSafe);
        assertEquals(1, count(counts, "indetWithoutSpecimen").blankSafe);
        assertEquals(3, count(counts, "orphanedSpecies").blankSafe);
        assertEquals(1, count(counts, "projectMorphos").blankSafe);
        assertEquals(1, count(counts, "placeMorphos").blankSafe);
        List<String> lines = BlankNameCleanup.previewLines(c, CuratorTaxonSetDb.curatorSourceList());
        assertTrue(lines.get(0), lines.get(0).startsWith(BlankNameCleanup.PREVIEW_LOG));
        assertTrue(lines.get(0), lines.get(0).contains("specimens with a blank name 2, blank subfamily or genus 1"));
        assertEquals(7, lines.size());
        // reads only
        assertTrue(hasTaxon(PZ) && hasTaxon(GO) && hasTaxon(IX));
        assertEquals(4, names("select taxon_name from proj_taxon").size());
        // the counts are what the deletes then delete
        TaxonDb.deleteSpeciesWithoutSpecimenOrAntcatSource(c, true);
        TaxonDb.deleteGeneraWithoutSpecimenOrAntcatSource(c, true);
        counts = BlankNameCleanup.counts(c, CuratorTaxonSetDb.curatorSourceList());
        assertEquals(0, count(counts, "speciesWithoutSpecimen").blankSafe);
        assertEquals(0, count(counts, "generaWithoutSpecimen").blankSafe);
    }
}
