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
        assertTrue(waiting("skipped: archive built 2026-10-23T12:01:22 UTC, before this run's request (TaxonWorks builds the new one now)"));
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

    /**
     * 2026-10-24: the 05:00 Pacific run got the archive TaxonWorks built at 2026-10-23 12:01 UTC (05:01 Pacific the day
     * before) and uploaded it, so a name fixed in TaxonWorks during 10-23 was missing. That archive is "older"; the one
     * built after this run's request is not; a time zone read wrongly by up to 12 hours does not change the answer.
     */
    public void testArchiveBuiltBeforeThisRunIsOlder() throws Exception {
        Method older = method("isOlderArchive", java.time.LocalDateTime.class, long.class);
        long request = java.time.ZonedDateTime.of(2026, 10, 24, 5, 0, 0, 0, java.time.ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli();
        assertTrue((Boolean) older.invoke(null, java.time.LocalDateTime.of(2026, 10, 23, 12, 1, 22), request));
        assertFalse((Boolean) older.invoke(null, java.time.LocalDateTime.of(2026, 10, 24, 12, 1, 22), request));
        assertFalse((Boolean) older.invoke(null, java.time.LocalDateTime.of(2026, 10, 24, 5, 1, 22), request));   // read as Pacific
        assertFalse((Boolean) older.invoke(null, null, request));
    }

    /** The build time is the newest file time in the archive. */
    public void testArchiveBuiltTimeIsNewestEntry() throws Exception {
        Path zip = Files.createTempFile("tw", ".zip");
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            for (String[] e : new String[][] {{"meta.xml", "2026-10-23T12:01:20"}, {"data.tsv", "2026-10-23T12:01:22"}}) {
                java.util.zip.ZipEntry z = new java.util.zip.ZipEntry(e[0]);
                z.setTimeLocal(java.time.LocalDateTime.parse(e[1]));
                out.putNextEntry(z);
                out.write(1);
                out.closeEntry();
            }
        }
        Object built = method("archiveBuiltUtc", Path.class).invoke(null, zip);
        assertEquals(java.time.LocalDateTime.of(2026, 10, 23, 12, 1, 22), built);
        Files.delete(zip);
    }

    /** An upload of an older archive (the wait ran out) raises an alert; a fresh upload does not. */
    public void testOlderArchiveUploadIsAlerted() throws Exception {
        Method m = method("isOlderArchiveUploaded", String.class);
        assertTrue((Boolean) m.invoke(null, "uploaded 267630 records for group 1 (was 267350 specimens) in 20 min; archive built 2026-10-23T12:01:22 UTC (older than this run: TaxonWorks did not build a new one in time)"));
        assertFalse((Boolean) m.invoke(null, "uploaded 267630 records for group 1 (was 267350 specimens) in 20 min; archive built 2026-10-24T12:04:10 UTC"));
        assertFalse((Boolean) m.invoke(null, "skipped: archive built 2026-10-23T12:01:22 UTC, before this run's request (TaxonWorks builds the new one now)"));
        assertFalse((Boolean) m.invoke(null, (Object) null));
    }

    /**
     * 2026-10-28: when the wait runs out during a check that did not accept an older archive (a slow download),
     * one more check accepts it, so the day is not left without an upload; the server log keeps no URL.
     */
    public void testFinalCheckWhenWaitRunsOut() throws Exception {
        Method m = method("afterCheck", boolean.class, boolean.class, long.class, int.class);
        assertEquals("stop", m.invoke(null, false, false, 10L, 60));   // uploaded, or failed: done
        assertEquals("stop", m.invoke(null, true, true, 62L, 60));     // that check already accepted an older archive
        assertEquals("stop", m.invoke(null, true, true, 0L, 0));       // admin run: one check
        assertEquals("wait", m.invoke(null, true, false, 10L, 60));
        assertEquals("final", m.invoke(null, true, false, 62L, 60));   // slow download ran past the wait
        assertEquals("final", m.invoke(null, true, false, 58L, 60));
        Method w = method("withoutUrls", String.class);
        String text = (String) w.invoke(null, "Illegal character in query at index 40: https://sfg.taxonworks.org/api/v1/x?project_token=abc123 more");
        assertFalse(text.contains("abc123"));
        assertTrue(text.contains("<url>"));
        assertFalse(((String) w.invoke(null, "bad project_token=abc123")).contains("abc123"));
        assertEquals("", w.invoke(null, (Object) null));
    }
    // ------------------------------------------------------------------ the early request (2026-11-10)

    private static java.util.Calendar pacific(int year, int month, int day, int hour, int minute, int second) {
        java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Los_Angeles"));
        c.clear();
        c.set(year, month - 1, day, hour, minute, second);
        return c;
    }

    private static String pacificText(java.util.Date d) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        f.setTimeZone(java.util.TimeZone.getTimeZone("America/Los_Angeles"));
        return f.format(d);
    }

    /**
     * The archive is requested 25 minutes before the daily update (05:00: at 04:35 by the clock), strictly after now:
     * a server started between 04:35 and 05:00 asks tomorrow (today's update requests the archive itself, as before);
     * also on the days the clocks change.
     */
    public void testEarlyRequestIs25MinutesBeforeTheDailyUpdate() {
        assertEquals(25, TaxonWorksNightly.EARLY_REQUEST_MINUTES);
        assertEquals("2026-11-10 04:35:00", pacificText(TaxonWorksNightly.nextEarlyRequestTime(pacific(2026, 11, 10, 0, 10, 0), 5)));
        assertEquals("2026-11-10 04:35:00", pacificText(TaxonWorksNightly.nextEarlyRequestTime(pacific(2026, 11, 10, 4, 34, 59), 5)));
        assertEquals("2026-11-11 04:35:00", pacificText(TaxonWorksNightly.nextEarlyRequestTime(pacific(2026, 11, 10, 4, 35, 0), 5)));
        assertEquals("2026-11-11 04:35:00", pacificText(TaxonWorksNightly.nextEarlyRequestTime(pacific(2026, 11, 10, 4, 50, 0), 5)));
        assertEquals("2026-11-11 04:35:00", pacificText(TaxonWorksNightly.nextEarlyRequestTime(pacific(2026, 11, 10, 6, 50, 0), 5)));
        // the clocks go back on 2026-11-01 and forward on 2027-03-14: 04:35 by the clock on those days too
        assertEquals("2026-11-01 04:35:00", pacificText(TaxonWorksNightly.nextEarlyRequestTime(pacific(2026, 10, 31, 12, 0, 0), 5)));
        assertEquals("2027-03-14 04:35:00", pacificText(TaxonWorksNightly.nextEarlyRequestTime(pacific(2027, 3, 13, 12, 0, 0), 5)));
        // before the daily update's own time, whatever the launch hour
        assertEquals("2026-11-10 05:35:00", pacificText(TaxonWorksNightly.nextEarlyRequestTime(pacific(2026, 11, 10, 0, 10, 0), 6)));
    }

    /** Off without the settings file or its url (staging, development); not while the upload job runs. */
    public void testEarlyRequestDecision() {
        assertEquals("off", TaxonWorksNightly.earlyRequestDecision(false, null, false));
        assertEquals("off", TaxonWorksNightly.earlyRequestDecision(false, "https://example.org/x", false));
        assertEquals("off", TaxonWorksNightly.earlyRequestDecision(true, null, false));
        assertEquals("off", TaxonWorksNightly.earlyRequestDecision(true, "  ", false));
        assertEquals("busy", TaxonWorksNightly.earlyRequestDecision(true, "https://example.org/x", true));
        assertEquals("request", TaxonWorksNightly.earlyRequestDecision(true, "https://example.org/x", false));
        // no settings file on this machine: nothing is requested and there is no line for compute.log
        if (!new File("/antweb/deploy/etc/taxonworks.properties").isFile()) assertNull(TaxonWorksNightly.earlyRequest());
    }

    /** The compute.log line: what TaxonWorks answered, the archive's age; a failure says the update goes on as before. */
    public void testEarlyRequestLine() {
        long now = java.time.ZonedDateTime.of(2026, 11, 10, 4, 35, 0, 0, java.time.ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli();
        assertEquals("TaxonWorks early request: HTTP 200, the archive on offer was built 2026-11-09T13:20 UTC (23.3 hours ago);"
            + " TaxonWorks builds a new one now if that is over the project's maximum age, for the upload of the daily update",
            TaxonWorksNightly.earlyRequestLine(200, java.time.LocalDateTime.of(2026, 11, 9, 13, 20), now, null));
        assertEquals("TaxonWorks early request: HTTP 422, TaxonWorks is building the archive", TaxonWorksNightly.earlyRequestLine(422, null, now, null));
        assertEquals("TaxonWorks early request: HTTP 500; the daily update requests the archive itself, as before",
            TaxonWorksNightly.earlyRequestLine(500, null, now, null));
        assertEquals("TaxonWorks early request: HTTP 200, the answer was not a readable archive; the daily update requests the archive itself, as before",
            TaxonWorksNightly.earlyRequestLine(200, null, now, null));
        assertEquals("TaxonWorks early request: FAILED (ConnectException); the daily update requests the archive itself, as before",
            TaxonWorksNightly.earlyRequestLine(0, null, now, "ConnectException"));
        for (String line : new String[] {TaxonWorksNightly.earlyRequestLine(200, java.time.LocalDateTime.of(2026, 11, 9, 13, 20), now, null),
                TaxonWorksNightly.earlyRequestLine(0, null, now, "IOException")}) {
            assertTrue(line.startsWith(TaxonWorksNightly.EARLY_LOG + ": "));
            assertFalse("pages say daily update", line.toLowerCase().contains("nightly"));
            assertFalse(line.contains("http://") || line.contains("https://") || line.contains("token"));
        }
    }

    /**
     * The request against a local server standing in for TaxonWorks: a zip answer gives the archive's build time and
     * leaves no file behind; "building" (422), an error page and a server that is not there give their lines, never an
     * exception and never the address (it carries the project token).
     */
    public void testEarlyRequestAgainstALocalServer() throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(bytes)) {
            java.util.zip.ZipEntry e = new java.util.zip.ZipEntry("data.tsv");
            e.setTimeLocal(java.time.LocalDateTime.of(2026, 11, 9, 13, 20, 0));
            z.putNextEntry(e);
            z.write("occurrenceID\n1\n".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        final byte[] zip = bytes.toByteArray();
        final java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            String path = exchange.getRequestURI().getPath();
            byte[] body = path.startsWith("/archive") ? zip : "{\"error\":\"building\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(path.startsWith("/archive") ? 200 : path.startsWith("/building") ? 422 : 500, body.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        Path dir = Files.createTempDirectory("twearly");
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            String ok = TaxonWorksNightly.earlyRequest(base + "/archive?project_token=secret123", dir, 20);
            assertTrue(ok, ok.startsWith("TaxonWorks early request: HTTP 200, the archive on offer was built 2026-11-09T13:20 UTC"));
            assertEquals("one request", 1, requests.get());
            assertEquals("TaxonWorks early request: HTTP 422, TaxonWorks is building the archive",
                TaxonWorksNightly.earlyRequest(base + "/building?project_token=secret123", dir, 20));
            assertEquals("TaxonWorks early request: HTTP 500; the daily update requests the archive itself, as before",
                TaxonWorksNightly.earlyRequest(base + "/down?project_token=secret123", dir, 20));
            server.stop(0);
            String gone = TaxonWorksNightly.earlyRequest(base + "/archive?project_token=secret123", dir, 5);
            assertTrue(gone, gone.startsWith("TaxonWorks early request: FAILED (") && gone.endsWith("; the daily update requests the archive itself, as before"));
            String bad = TaxonWorksNightly.earlyRequest("not a url project_token=secret123", dir, 5);
            for (String line : new String[] {ok, gone, bad}) assertFalse(line, line.contains("secret123") || line.contains("127.0.0.1"));
            try (java.util.stream.Stream<Path> left = Files.list(dir)) {
                assertEquals("no file left in the working directory", 0L, left.count());
            }
        } finally {
            server.stop(0);
        }
    }
}
