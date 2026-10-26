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

import org.calacademy.antweb.util.FetchRetry;
import org.calacademy.antweb.util.HttpUtil;

/**
 * FetchRetry and HttpUtil.downloadToFile (2026-10-25): on 10-23 the nightly AntCat import could not fetch
 * antcat.antweb.txt once and that night's name update was skipped. The nightly now tries again every
 * 10 minutes and makes no new try an hour after the first, says why it failed, and never leaves a half-written
 * or cut-off file under the real name.
 */
public class FetchRetryTest extends TestCase {

    private static final long MIN = 60 * 1000L;

    /** A clock that moves only when the fake sleeper waits (and by the time each try takes). */
    private long now = 0;
    private final List<Long> waits = new ArrayList<>();
    private final FetchRetry.Clock clock = () -> now;
    private final FetchRetry.Sleeper sleeper = ms -> { waits.add(ms); now += ms; };

    private FetchRetry.Attempt failing(int failures, long tryMs) {
        int[] n = {0};
        return () -> { now += tryMs; return ++n[0] <= failures ? "ConnectException: Connection refused" : null; };
    }

    public void testFirstTryWorksNoWait() {
        FetchRetry.Result r = FetchRetry.run(failing(0, 0), FetchRetry.NIGHTLY_DEADLINE_MS, FetchRetry.NIGHTLY_WAIT_MS, clock, sleeper);
        assertTrue(r.ok());
        assertEquals(1, r.attempts);
        assertTrue(waits.isEmpty());
    }

    public void testWorksAtThirdTryAfterTwoWaits() {
        FetchRetry.Result r = FetchRetry.run(failing(2, 0), FetchRetry.NIGHTLY_DEADLINE_MS, FetchRetry.NIGHTLY_WAIT_MS, clock, sleeper);
        assertTrue(r.ok());
        assertEquals(3, r.attempts);
        assertEquals(2, waits.size());
        assertEquals(FetchRetry.NIGHTLY_WAIT_MS, (long) waits.get(0));
    }

    public void testNoNewTryAfterAnHour() {
        // tries at 0, 10, 20, 30, 40, 50 minutes; the next would start at 60: not made
        FetchRetry.Result r = FetchRetry.run(failing(100, 0), FetchRetry.NIGHTLY_DEADLINE_MS, FetchRetry.NIGHTLY_WAIT_MS, clock, sleeper);
        assertFalse(r.ok());
        assertEquals(6, r.attempts);
        assertTrue(now < 60 * MIN);
        assertEquals("ConnectException: Connection refused", r.error);
    }

    public void testSlowTriesCountTowardsTheHour() {
        // each try hangs 6 minutes (connect plus a stalled read): tries start at 0, 16, 32, 48; the next at 64 is not made
        FetchRetry.Result r = FetchRetry.run(failing(100, 6 * MIN), FetchRetry.NIGHTLY_DEADLINE_MS, FetchRetry.NIGHTLY_WAIT_MS, clock, sleeper);
        assertEquals(4, r.attempts);
        assertTrue("last try ended within about an hour: " + now, now <= 60 * MIN);
        assertEquals(54, r.minutes);
    }

    public void testOnceNeverWaits() {
        FetchRetry.Result r = FetchRetry.once(() -> "x");
        assertEquals(1, r.attempts);
        assertEquals("x", r.error);
        FetchRetry.Result r2 = FetchRetry.run(failing(5, 0), 0, FetchRetry.NIGHTLY_WAIT_MS, clock, sleeper);
        assertEquals(1, r2.attempts);
        assertTrue(waits.isEmpty());
    }

    public void testInterruptedWaitStops() {
        FetchRetry.Result r = FetchRetry.run(failing(5, 0), FetchRetry.NIGHTLY_DEADLINE_MS, FetchRetry.NIGHTLY_WAIT_MS, clock,
            ms -> { throw new InterruptedException(); });
        assertEquals(1, r.attempts);
        assertTrue(r.error.contains("interrupted"));
        Thread.interrupted();   // clear the flag for the next test
    }

    public void testFailureMessageSaysWhereHowOftenAndWhy() {
        FetchRetry.Result r = FetchRetry.run(failing(100, 0), FetchRetry.NIGHTLY_DEADLINE_MS, FetchRetry.NIGHTLY_WAIT_MS, clock, sleeper);
        String m = FetchRetry.failureMessage("http://antcat-export:9090/antcat.antweb.txt", r);
        assertEquals("Failed to fetch http://antcat-export:9090/antcat.antweb.txt after 6 tries over 50 minutes: ConnectException: Connection refused", m);
        assertEquals("Failed to fetch u after 1 try: x", FetchRetry.failureMessage("u", FetchRetry.once(() -> "x")));
    }

    // --- downloadToFile against a local web server ---

    private HttpServer server;
    private ServerSocket cut;
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
        // A server that promises 1000 bytes and sends 10 (a download cut off half-way).
        cut = new ServerSocket(0);
        Thread t = new Thread(() -> {
            try (java.net.Socket c = cut.accept()) {
                c.getInputStream().read(new byte[4096]);
                OutputStream o = c.getOutputStream();
                o.write("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\nConnection: close\r\n\r\nFormicidae".getBytes("UTF-8"));
                o.flush();
            } catch (Exception e) { /* test ends */ }
        });
        t.setDaemon(true);
        t.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    protected void tearDown() {
        if (server != null) server.stop(0);
        try { if (cut != null) cut.close(); } catch (Exception e) { /* closed */ }
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

    public void testCutOffDownloadRefused() throws Exception {
        File f = target();
        String error = HttpUtil.downloadToFile("http://127.0.0.1:" + cut.getLocalPort() + "/antcat.antweb.txt", f.getPath());
        assertNotNull("a download shorter than its Content-Length is refused", error);
        assertEquals("yesterday\n", read(f));
        assertFalse(new File(f.getPath() + ".part").exists());
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
