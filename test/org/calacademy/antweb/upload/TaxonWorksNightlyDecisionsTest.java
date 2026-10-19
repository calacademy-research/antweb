package test.org.calacademy.antweb.upload;

import junit.framework.TestCase;
import org.calacademy.antweb.upload.TaxonWorksNightly;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decisions of the nightly TaxonWorks job (TaxonWorksNightly), without a server, a database or
 * TaxonWorks: which answers mean "wait and ask again", the zip check, the "archive unchanged"
 * fingerprint, the record count the 95% guard uses, one run at a time, and no settings file.
 * See docs/flows/specimen-upload.md section 5.
 *
 * The private helpers are reached by reflection: this test changes no production code. The
 * guard itself (fewer than minRatio x current specimens) and the HTTP steps need a seam for the
 * settings path and the URL first (docs/flows/test-gaps-uploads-nightly.md, test 6).
 */
public class TaxonWorksNightlyDecisionsTest extends TestCase {

    private static Method method(String name, Class<?>... types) throws Exception {
        Method m = TaxonWorksNightly.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
    }

    private static boolean waiting(String result) throws Exception {
        return (Boolean) method("isWaitingForTaxonWorks", String.class).invoke(null, result);
    }

    public void testWaitsOnlyForUnchangedArchiveOrHttp422() throws Exception {
        assertTrue(waiting("skipped: archive unchanged since the last upload"));
        assertTrue(waiting("skipped: TaxonWorks answered HTTP 422 {\"error\":\"building\"}"));
        assertFalse(waiting("skipped: TaxonWorks answered HTTP 500 oops"));
        assertFalse(waiting("skipped: TaxonWorks answered HTTP 404 "));
        assertFalse(waiting("SKIPPED BY GUARD: archive has 1 records, group 1 has 100 specimens now (minimum 95%). Nothing changed."));
        assertFalse(waiting("skipped: another upload is running (x)"));
        assertFalse(waiting("uploaded 10 records for group 1 (was 9 specimens) in 1 min"));
        assertFalse(waiting("FAILED: IOException"));
        assertFalse(waiting("skipped: no /antweb/deploy/etc/taxonworks.properties"));
        assertFalse(waiting(null));
    }

    public void testZipCheck() throws Exception {
        Method isZip = method("isZip", Path.class);
        Path dir = Files.createTempDirectory("twzip");
        Path zip = dir.resolve("a.zip");
        Files.write(zip, new byte[] {'P', 'K', 3, 4, 0, 0});
        Path html = dir.resolve("b.zip");
        Files.write(html, "<html>error</html>".getBytes(StandardCharsets.UTF_8));
        Path tiny = dir.resolve("c.zip");
        Files.write(tiny, new byte[] {'P', 'K'});
        assertTrue((Boolean) isZip.invoke(null, zip));
        assertFalse((Boolean) isZip.invoke(null, html));
        assertFalse("fewer than 4 bytes is not a zip", (Boolean) isZip.invoke(null, tiny));
    }

    public void testFingerprintIsSha256Hex() throws Exception {
        Method sha = method("sha256", Path.class);
        Path f = Files.createTempFile("twsha", ".zip");
        Files.write(f, "abc".getBytes(StandardCharsets.UTF_8));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha.invoke(null, f));
        Files.write(f, "abd".getBytes(StandardCharsets.UTF_8));
        assertFalse("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".equals(sha.invoke(null, f)));
    }

    /** The guard compares this count (data lines of the converted file) with the group's specimens. */
    public void testRecordCountSkipsHeaderAndBlankLines() throws Exception {
        Method count = method("countRecords", Path.class);
        Path f = Files.createTempFile("twcount", ".txt");
        Files.write(f, "SpecimenCode\tGenus\na1\tX\na2\tY\n\n  \na3\tZ\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(3L, count.invoke(null, f));
        Files.write(f, "SpecimenCode\tGenus\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("header only: 0 records (the guard then refuses unless the group is empty)", 0L, count.invoke(null, f));
        Files.write(f, new byte[0]);
        assertEquals(0L, count.invoke(null, f));
    }

    /** A second run while one is running does nothing (no database is touched). */
    public void testOneRunAtATime() throws Exception {
        Field f = TaxonWorksNightly.class.getDeclaredField("s_running");
        f.setAccessible(true);
        AtomicBoolean running = (AtomicBoolean) f.get(null);
        running.set(true);
        try {
            assertEquals("skipped: already running", TaxonWorksNightly.run(null, 0));
            assertTrue("the first run's flag is left alone", running.get());
        } finally {
            running.set(false);
        }
    }

    /** No settings file: the job does nothing and says why (staging and development). */
    public void testNoSettingsFileSkips() throws Exception {
        Field cfg = TaxonWorksNightly.class.getDeclaredField("CONFIG");
        cfg.setAccessible(true);
        String config = (String) cfg.get(null);
        assertEquals("/antweb/deploy/etc/taxonworks.properties", config);
        if (new File(config).isFile()) return;  // a server with the file: nothing to check here
        assertEquals("skipped: no " + config, TaxonWorksNightly.run(null, 0));
        assertEquals("an admin run (wait 0) and the nightly (wait 90) give the same answer, without waiting",
            "skipped: no " + config, TaxonWorksNightly.run(null, 90));
    }

    public void testWaitStepIsThreeMinutes() throws Exception {
        Field f = TaxonWorksNightly.class.getDeclaredField("WAIT_STEP_MINUTES");
        f.setAccessible(true);
        assertEquals(3, f.getInt(null));
    }
}
