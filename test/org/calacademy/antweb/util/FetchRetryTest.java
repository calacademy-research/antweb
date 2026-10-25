package test.org.calacademy.antweb.util;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpServer;

import junit.framework.TestCase;

import org.calacademy.antweb.curate.speciesList.SpeciesListUploader;
import org.calacademy.antweb.util.FetchRetry;
import org.calacademy.antweb.util.HttpUtil;

/**
 * FetchRetry and HttpUtil.downloadToFile (2026-10-25): on 10-23 the nightly AntCat import could not fetch
 * antcat.antweb.txt once and that night's name update was skipped. The nightly now tries again every
 * 10 minutes for up to an hour, says why it failed, and never leaves a half-written file under the real name.
 */
public class FetchRetryTest extends TestCase {

    private final List<Long> waits = new ArrayList<>();
    private final FetchRetry.Sleeper recordWaits = waits::add;

    public void testFirstTryWorksNoWait() {
        FetchRetry.Result r = FetchRetry.run(() -> null, 7, 1000, recordWaits);
        assertTrue(r.ok());
        assertEquals(1, r.attempts);
        assertTrue(waits.isEmpty());
    }

    public void testWorksAtThirdTryAfterTwoWaits() {
        int[] n = {0};
        FetchRetry.Result r = FetchRetry.run(() -> ++n[0] < 3 ? "refused" : null, 7, 600_000, recordWaits);
        assertTrue(r.ok());
        assertEquals(3, r.attempts);
        assertEquals(List.of(600_000L, 600_000L), waits);
    }

    public void testGivesUpAfterMaxWithLastReason() {
        int[] n = {0};
        FetchRetry.Result r = FetchRetry.run(() -> "fail " + (++n[0]), 7, 5, recordWaits);
        assertFalse(r.ok());
        assertEquals(7, r.attempts);
        assertEquals("fail 7", r.error);
        assertEquals(6, waits.size());   // no wait after the last try
    }

    public void testNightlyGivesUpAboutAnHourAfterTheFirstFailure() {
        assertEquals(60 * 60 * 1000L, (FetchRetry.NIGHTLY_ATTEMPTS - 1) * FetchRetry.NIGHTLY_WAIT_MS);
    }

    public void testSingleTryNeverWaits() {
        FetchRetry.Result r = FetchRetry.run(() -> "down", 1, 600_000, recordWaits);
        assertFalse(r.ok());
        assertEquals(1, r.attempts);
        assertTrue(waits.isEmpty());
    }

    public void testFailureMessageSaysWhereHowOftenAndWhy() {
        FetchRetry.Result r = FetchRetry.run(() -> "ConnectException: Connection refused", 7, 0, ms -> {});
        String m = SpeciesListUploader.fetchFailedMessage("http://antcat-export:9090/antcat.antweb.txt", r);
        assertEquals("Failed to fetch http://antcat-export:9090/antcat.antweb.txt after 7 tries: ConnectException: Connection refused", m);
        assertTrue(m.startsWith("Failed to fetch"));
        assertTrue(SpeciesListUploader.fetchFailedMessage("u", FetchRetry.run(() -> "x", 1, 0, ms -> {})).contains("after 1 try: x"));
    }

    // --- downloadToFile against a local web server ---

    private HttpServer server;
    private String base;

    @Override
    protected void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok.txt", ex -> {
            byte[] b = "Formicidae\tvalid\n".getBytes("UTF-8");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(b); }
        });
        server.createContext("/empty.txt", ex -> {
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.createContext("/missing.txt", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    protected void tearDown() {
        if (server != null) server.stop(0);
    }

    private File target() throws Exception {
        File d = Files.createTempDirectory("fetchretry").toFile();
        d.deleteOnExit();
        File f = new File(d, "worldants_speciesList.txt");
        Files.write(f.toPath(), "yesterday\n".getBytes("UTF-8"));
        return f;
    }

    private static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), "UTF-8");
    }

    public void testDownloadReplacesFile() throws Exception {
        File f = target();
        assertNull(HttpUtil.downloadToFile(base + "/ok.txt", f.getPath()));
        assertEquals("Formicidae\tvalid\n", read(f));
        assertFalse(new File(f.getPath() + ".part").exists());
    }

    public void testNotFoundKeepsYesterdaysFileAndSaysWhy() throws Exception {
        File f = target();
        String error = HttpUtil.downloadToFile(base + "/missing.txt", f.getPath());
        assertNotNull(error);
        assertTrue(error, error.contains("FileNotFoundException") || error.contains("404"));
        assertEquals("yesterday\n", read(f));
        assertFalse(new File(f.getPath() + ".part").exists());
    }

    public void testEmptyDownloadRefused() throws Exception {
        File f = target();
        assertEquals("the download was empty", HttpUtil.downloadToFile(base + "/empty.txt", f.getPath()));
        assertEquals("yesterday\n", read(f));
    }

    public void testServerDownSaysConnectionRefused() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) { port = s.getLocalPort(); }   // free port, nothing listening
        File f = target();
        String error = HttpUtil.downloadToFile("http://127.0.0.1:" + port + "/antcat.antweb.txt", f.getPath());
        assertNotNull(error);
        assertTrue(error, error.contains("ConnectException"));
        assertEquals("yesterday\n", read(f));
        assertTrue(HttpUtil.writeUrlContents(base + "/ok.txt", f.getPath()));
        assertFalse(HttpUtil.writeUrlContents("http://127.0.0.1:" + port + "/x", f.getPath()));
    }
}
