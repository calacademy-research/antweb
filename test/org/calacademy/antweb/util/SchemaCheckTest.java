package test.org.calacademy.antweb.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;
import org.calacademy.antweb.util.SchemaCheck;

/**
 * SchemaCheck (2026-10-22): the live database's tables and columns against the committed snapshot
 * (doc/schema/live-schema-*.tsv). The comparison and the snapshot file, without a database. With the system
 * property snapshot.dir (CI mounts doc/schema there) the committed snapshot is read too.
 */
public class SchemaCheckTest extends TestCase {

    private static final List<String> SNAPSHOT = Arrays.asList(
        "taxon\ttaxon_name\tvarchar(128)\tBASE TABLE",
        "taxon\tspecies_count\tint\tBASE TABLE",
        "specimen\tcode\tvarchar(128)\tBASE TABLE",
        "specimen\thabitat\tvarchar(512)\tBASE TABLE",
        "old_table\tid\tint\tBASE TABLE",
        "proj_taxon_bak_20261019\tproject_name\tvarchar(32)\tBASE TABLE",
        "api3_1_specimen\tcode\tvarchar(128)\tVIEW");

    private static SchemaCheck.Report compare(List<String> snapshot, List<String> live) {
        SchemaCheck.Report r = new SchemaCheck.Report();
        SchemaCheck.compare(SchemaCheck.parse(snapshot), SchemaCheck.parse(live), r);
        return r;
    }

    public void testSameSchemaHasNoDifference() {
        SchemaCheck.Report r = compare(SNAPSHOT, SNAPSHOT);
        assertTrue(r.differences.toString(), r.differences.isEmpty());
        assertTrue(r.ok());
    }

    public void testIntegerDisplayWidthAndCaseAreNotDifferences() {
        List<String> live = Arrays.asList(
            "taxon\tTaxon_Name\tVARCHAR(128)\tBASE TABLE",
            "taxon\tspecies_count\tint(11)\tBASE TABLE",
            "specimen\tcode\tvarchar(128)\tBASE TABLE",
            "specimen\thabitat\tvarchar(512)\tBASE TABLE",
            "old_table\tid\tint\tBASE TABLE",
            "proj_taxon_bak_20261019\tproject_name\tvarchar(32)\tBASE TABLE",
            "api3_1_specimen\tcode\tvarchar(128)\tVIEW");
        assertTrue(compare(SNAPSHOT, live).differences.toString(), compare(SNAPSHOT, live).differences.isEmpty());
    }

    public void testAddedRemovedAndRetyped() {
        List<String> live = Arrays.asList(
            "taxon\ttaxon_name\tvarchar(128)\tBASE TABLE",
            "taxon\tspecies_count\tint\tBASE TABLE",
            "taxon\tnew_count\tint\tBASE TABLE",
            "specimen\tcode\tvarchar(128)\tBASE TABLE",
            "specimen\thabitat\tvarchar(256)\tBASE TABLE",
            "new_table\tid\tint\tBASE TABLE",
            "api3_1_specimen\tcode\tvarchar(128)\tBASE TABLE");
        SchemaCheck.Report r = compare(SNAPSHOT, live);
        assertTrue(r.differences.toString(), r.differences.contains("column taxon.new_count added (int)"));
        assertTrue(r.differences.toString(), r.differences.contains("column specimen.habitat retyped: varchar(512) -> varchar(256)"));
        assertTrue(r.differences.toString(), r.differences.contains("table old_table removed"));
        assertTrue(r.differences.toString(), r.differences.contains("table new_table added (1 columns)"));
        assertTrue(r.differences.toString(), r.differences.contains("table api3_1_specimen was a VIEW, is a BASE TABLE"));
        assertEquals(r.differences.toString(), 5, r.differences.size());
        assertEquals("the dropped dated backup is left out", 1, r.ignored);
        assertFalse(r.ok());
    }

    public void testViewColumnTypesFollowTheirTables() {
        List<String> live = new java.util.ArrayList<>(SNAPSHOT);
        live.set(6, "api3_1_specimen\tcode\tvarchar(256)\tVIEW");
        assertTrue(compare(SNAPSHOT, live).differences.isEmpty());
    }

    public void testTableNamesKeepTheirCase() {
        List<String> live = new java.util.ArrayList<>(SNAPSHOT);
        live.add("Taxon\tid\tint\tBASE TABLE");
        assertEquals(Arrays.asList("table Taxon added (1 columns)"), compare(SNAPSHOT, live).differences);
    }

    public void testColumnRemoved() {
        List<String> live = Arrays.asList(
            "taxon\ttaxon_name\tvarchar(128)\tBASE TABLE",
            "specimen\tcode\tvarchar(128)\tBASE TABLE",
            "specimen\thabitat\tvarchar(512)\tBASE TABLE",
            "old_table\tid\tint\tBASE TABLE",
            "proj_taxon_bak_20261019\tproject_name\tvarchar(32)\tBASE TABLE",
            "api3_1_specimen\tcode\tvarchar(128)\tVIEW");
        SchemaCheck.Report r = compare(SNAPSHOT, live);
        assertEquals(Arrays.asList("column taxon.species_count removed"), r.differences);
    }

    public void testBackupNames() {
        for (String t : new String[] {"ant_group_abbrev_bak_20261020", "login_deleted_2026_10_16", "login_project_20261018",
                "specimen_badcode_backup_20261018", "login_inactive_backup_20261018", "description_sanitize_backup_20261016_200412",
                "login_spam_deleted_2026_10_14", "proj_taxon20170422"}) {
            assertTrue(t, SchemaCheck.isBackup(t));
        }
        for (String t : new String[] {"taxon", "image_backup", "museumbak", "darwin_core_2", "geolocale_taxon", "specimen"}) {
            assertFalse(t, SchemaCheck.isBackup(t));
        }
    }

    public void testLatestSnapshotAndReport() throws Exception {
        Path dir = Files.createTempDirectory("schema");
        try {
            assertNull(SchemaCheck.latest(dir));
            Files.write(dir.resolve("live-schema-2026-10-22.tsv"), SNAPSHOT, StandardCharsets.UTF_8);
            Files.write(dir.resolve("live-schema-2026-11-30.tsv"), SNAPSHOT, StandardCharsets.UTF_8);
            Files.write(dir.resolve("notes.txt"), Arrays.asList("x"), StandardCharsets.UTF_8);
            assertEquals("live-schema-2026-11-30.tsv", SchemaCheck.latest(dir).getFileName().toString());
            SchemaCheck.Report r = SchemaCheck.run(null, dir.resolve("missing"));
            assertTrue(r.error, r.error.startsWith("no snapshot"));
            assertTrue(SchemaCheck.html(r).contains("data-error=\"1\""));
            SchemaCheck.Report d = compare(SNAPSHOT, Arrays.asList("taxon\ttaxon_name\tvarchar(128)\tBASE TABLE"));
            d.snapshot = "live-schema-2026-10-22.tsv";
            String html = SchemaCheck.html(d);
            assertTrue(html, html.contains("id=\"schema-check\" data-differences=\"" + d.differences.size() + "\" data-error=\"0\""));
            assertTrue(d.summary(), d.summary().startsWith("Schema: " + d.differences.size() + " changes since the snapshot"));
        } finally {
            for (Path p : Files.newDirectoryStream(dir)) Files.delete(p);
            Files.delete(dir);
        }
    }

    /** The committed snapshot (CI: -Dsnapshot.dir=<doc/schema>): parses, and equals itself. */
    public void testCommittedSnapshot() throws Exception {
        String d = System.getProperty("snapshot.dir");
        if (d == null) return;
        Path file = SchemaCheck.latest(Paths.get(d));
        assertNotNull("no live-schema-*.tsv in " + d, file);
        SchemaCheck.Schema s = SchemaCheck.parse(Files.readAllLines(file, StandardCharsets.UTF_8));
        assertTrue(s.kinds.size() > 90);
        assertEquals("VIEW", s.kinds.get("api3_1_specimen"));
        assertTrue(s.columns.get("specimen").containsKey("taxon_name"));
        SchemaCheck.Report r = new SchemaCheck.Report();
        SchemaCheck.compare(s, s, r);
        assertTrue(r.differences.isEmpty());
    }
}
