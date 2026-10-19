package test.org.calacademy.antweb.security;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.sun.net.httpserver.HttpServer;
import junit.framework.TestCase;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.imageUploader.ImageUploadReport;
import org.calacademy.antweb.upload.SafeUnzip;
import org.calacademy.antweb.upload.UploadAction;
import org.calacademy.antweb.upload.Uploader;
import org.calacademy.antweb.util.HttpUtil;
import org.calacademy.antweb.util.SchedulerCall;

/**
 * Upload and scheduler hardening of 2026-10-18 (second batch): zip extraction (SafeUnzip),
 * specimen header checks, species list deletion, image upload reports, and AntWeb's calls to
 * itself (the nightly Scheduler and postInstantiate, SchedulerCall). Runs in CI inside the
 * antweb image (.github/workflows/tests.yaml), with junit.textui.TestRunner.
 */
public class UploadSchedulerHardeningTest extends TestCase {

    private static final String SECRET = "SECRET-CONTENT-NOT-TO-BE-ECHOED";

    private File dir;

    @Override
    protected void setUp() throws IOException {
        dir = Files.createTempDirectory("safeunzip").toFile();
    }

    @Override
    protected void tearDown() {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
        dir.delete();
    }

    // ---------- helpers ----------

    /** A zip with the given entries (name -> content; a name ending in / is a directory). */
    private File zip(String fileName, Map<String, byte[]> entries) throws IOException {
        File f = new File(dir, fileName);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(f.toPath()))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                if (!e.getKey().endsWith("/")) zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return f;
    }

    private static Map<String, byte[]> entries(String... nameContent) {
        Map<String, byte[]> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < nameContent.length; i += 2) {
            m.put(nameContent[i], nameContent[i + 1].getBytes(StandardCharsets.UTF_8));
        }
        return m;
    }

    /** Marks the central directory entry named name as a Unix symbolic link (as Info-ZIP writes one). */
    private static void makeSymlink(File zip, String name) throws IOException {
        byte[] b = Files.readAllBytes(zip.toPath());
        byte[] n = name.getBytes(StandardCharsets.UTF_8);
        boolean done = false;
        for (int p = 0; p + 46 + n.length <= b.length; p++) {
            if (b[p] == 0x50 && b[p + 1] == 0x4b && b[p + 2] == 0x01 && b[p + 3] == 0x02) {
                int nameLen = (b[p + 28] & 0xff) | ((b[p + 29] & 0xff) << 8);
                if (nameLen == n.length && java.util.Arrays.equals(java.util.Arrays.copyOfRange(b, p + 46, p + 46 + nameLen), n)) {
                    b[p + 5] = 3;                    // version made by: Unix
                    int attrs = 0120777 << 16;        // S_IFLNK | 0777
                    b[p + 38] = (byte) attrs;
                    b[p + 39] = (byte) (attrs >>> 8);
                    b[p + 40] = (byte) (attrs >>> 16);
                    b[p + 41] = (byte) (attrs >>> 24);
                    done = true;
                }
            }
        }
        assertTrue("central directory entry not found: " + name, done);
        Files.write(zip.toPath(), b);
    }

    private String refusal(File zip, SafeUnzip.Picker picker, String what, File out, long max, int entries) throws IOException {
        try {
            SafeUnzip.extract(zip, picker, what, out, max, entries);
        } catch (SafeUnzip.Refused r) {
            assertFalse("nothing is written when refused", out.exists());
            assertFalse("the message never quotes content", r.getMessage().contains(SECRET));
            return r.getMessage();
        }
        fail("expected the archive to be refused");
        return null;
    }

    private String refusal(File zip, SafeUnzip.Picker picker, String what, File out) throws IOException {
        return refusal(zip, picker, what, out, SafeUnzip.MAX_TOTAL_BYTES, SafeUnzip.MAX_ENTRIES);
    }

    // ---------- SafeUnzip: picks the right entry ----------

    public void testPicksTheOneTxt() throws Exception {
        File z = zip("a.zip", entries("folder/", "", "folder/specimens.txt", "SpecimenCode\tGenus\nx\ty\n",
                "__MACOSX/folder/._specimens.txt", "junk", "folder/.DS_Store", "junk", "readme.pdf", "pdf"));
        File out = new File(dir, "specimen16.txt");
        assertEquals("folder/specimens.txt", SafeUnzip.extract(z, SafeUnzip.ONE_TXT, ".txt file", out));
        assertEquals("SpecimenCode\tGenus\nx\ty\n", new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8));
    }

    public void testTwoTxtRefused() throws Exception {
        File z = zip("b.zip", entries("one.txt", SECRET, "two.TXT", SECRET));
        String m = refusal(z, SafeUnzip.ONE_TXT, ".txt file", new File(dir, "out.txt"));
        assertTrue(m, m.contains("only one .txt file"));
    }

    public void testNoTxtRefused() throws Exception {
        File z = zip("c.zip", entries("specimens.csv", SECRET));
        String m = refusal(z, SafeUnzip.ONE_TXT, ".txt file", new File(dir, "out.txt"));
        assertTrue(m, m.contains("no .txt file"));
    }

    public void testTaxonWorksTakesTopLevelDataTsv() throws Exception {
        File z = zip("dwc.zip", entries("meta.xml", "<archive/>", "eml.xml", "<eml/>", "data.tsv", "catalogNumber\nCASENT1\n",
                "other/data.tsv", "not this one"));
        File out = new File(dir, "specimenTW1_temp.txt");
        assertEquals("data.tsv", SafeUnzip.extract(z, SafeUnzip.named("data.tsv"), "data.tsv file", out));
        assertEquals("catalogNumber\nCASENT1\n", new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8));
    }

    public void testGbifNeedsTopLevelOccurrence() throws Exception {
        File z = zip("gbif.zip", entries("sub/occurrence.txt", SECRET));
        String m = refusal(z, SafeUnzip.named("occurrence.txt"), "occurrence.txt file", new File(dir, "out.txt"));
        assertTrue(m, m.contains("no occurrence.txt file"));
    }

    // ---------- SafeUnzip: refusals ----------

    public void testSymlinkRefused() throws Exception {
        File z = zip("link.zip", entries("specimens.txt", "/etc/passwd"));
        makeSymlink(z, "specimens.txt");
        String m = refusal(z, SafeUnzip.ONE_TXT, ".txt file", new File(dir, "out.txt"));
        assertTrue(m, m.contains("symbolic link"));
    }

    public void testSymlinkAnywhereRefused() throws Exception {
        File z = zip("link2.zip", entries("specimens.txt", "SpecimenCode\n", "data/link", "/proc/self/status"));
        makeSymlink(z, "data/link");
        String m = refusal(z, SafeUnzip.ONE_TXT, ".txt file", new File(dir, "out.txt"));
        assertTrue(m, m.contains("symbolic link"));
    }

    public void testDotDotRefused() throws Exception {
        File z = zip("dd.zip", entries("../../specimens.txt", SECRET));
        String m = refusal(z, SafeUnzip.ONE_TXT, ".txt file", new File(dir, "out.txt"));
        assertTrue(m, m.contains("not allowed"));
        z = zip("dd2.zip", entries("ok.txt", "x", "a\\..\\..\\b.txt", SECRET));
        assertTrue(refusal(z, SafeUnzip.ONE_TXT, ".txt file", new File(dir, "out.txt")).contains("not allowed"));
    }

    public void testAbsoluteRefused() throws Exception {
        File z = zip("abs.zip", entries("/tmp/specimens.txt", SECRET));
        assertTrue(refusal(z, SafeUnzip.ONE_TXT, ".txt file", new File(dir, "out.txt")).contains("not allowed"));
        z = zip("abs2.zip", entries("C:/specimens.txt", SECRET));
        assertTrue(refusal(z, SafeUnzip.ONE_TXT, ".txt file", new File(dir, "out.txt")).contains("not allowed"));
    }

    public void testSizeCap() throws Exception {
        Map<String, byte[]> m = new HashMap<>();
        m.put("big.txt", new byte[3 * 1024 * 1024]);   // 3 MB of zeros, compresses to a few KB
        File z = zip("bomb.zip", m);
        String msg = refusal(z, SafeUnzip.ONE_TXT, ".txt file", new File(dir, "out.txt"), 1024 * 1024, 10);
        assertTrue(msg, msg.contains("too large"));
        // Under the cap it is taken.
        File out = new File(dir, "ok.txt");
        SafeUnzip.extract(z, SafeUnzip.ONE_TXT, ".txt file", out, 4L * 1024 * 1024, 10);
        assertEquals(3L * 1024 * 1024, out.length());
    }

    public void testSizeCapCountsEveryEntry() throws Exception {
        Map<String, byte[]> m = new java.util.LinkedHashMap<>();
        m.put("a.bin", new byte[700 * 1024]);
        m.put("b.bin", new byte[700 * 1024]);
        m.put("specimens.txt", "SpecimenCode\n".getBytes(StandardCharsets.UTF_8));
        File z = zip("sum.zip", m);
        assertTrue(refusal(z, SafeUnzip.ONE_TXT, ".txt file", new File(dir, "out.txt"), 1024 * 1024, 10).contains("too large"));
    }

    public void testEntryCountCap() throws Exception {
        File z = zip("many.zip", entries("1.bin", "x", "2.bin", "x", "3.bin", "x", "4.bin", "x", "specimens.txt", "x"));
        assertTrue(refusal(z, SafeUnzip.ONE_TXT, ".txt file", new File(dir, "out.txt"), 1024 * 1024, 3).contains("more than 3 entries"));
    }

    public void testNotAZip() throws Exception {
        File f = new File(dir, "plain.zip");
        Files.write(f.toPath(), (SECRET + "\tother\n").getBytes(StandardCharsets.UTF_8));
        String m = refusal(f, SafeUnzip.ONE_TXT, ".txt file", new File(dir, "out.txt"));
        assertTrue(m, m.contains("not a zip file"));
    }

    public void testCapsLeaveRoomForTaxonWorks() {
        // CAS TaxonWorks archives: about 265,000 records, data.tsv a few hundred MB.
        assertTrue(SafeUnzip.MAX_TOTAL_BYTES >= 2L * 1024 * 1024 * 1024);
        assertTrue(SafeUnzip.MAX_ENTRIES >= 100);
    }

    // ---------- specimen file header ----------

    public void testHeaderProblemNeverQuotesTheLine() {
        String line = SECRET + "/x\tGenus";
        String p = Uploader.headerProblem(line);
        assertNotNull(p);
        assertFalse(p, p.contains(SECRET));
        assertNotNull(Uploader.headerProblem(null));
        assertNull(Uploader.headerProblem("SpecimenCode\tGenus\tSpecies"));
    }

    public void testSpecimenCodeColumnNeeded() {
        assertTrue(Uploader.hasSpecimenCodeColumn("SpecimenCode\tGenus"));
        assertTrue(Uploader.hasSpecimenCodeColumn("\uFEFFspecimencode\tgenus"));
        assertTrue(Uploader.hasSpecimenCodeColumn("Genus\t SpecimenCode \tSpecies"));
        assertFalse(Uploader.hasSpecimenCodeColumn("Name:\tvalue"));
        assertFalse(Uploader.hasSpecimenCodeColumn(""));
        assertFalse(Uploader.hasSpecimenCodeColumn(null));
    }

    // ---------- upload.do ----------

    private static Login login(int groupId, boolean admin) {
        Login l = new Login();
        l.setId(42);
        l.setGroupId(groupId);
        l.setIsAdmin(admin);
        return l;
    }

    public void testDeleteSpeciesListAdminOnly() {
        Map<String, Object> existing = new HashMap<>();
        existing.put("madants", new Object());
        assertEquals(UploadAction.DELETE_PROJECT_ADMIN_ONLY, UploadAction.deleteProjectRefusal(login(16, false), "madants", existing));
        assertEquals(UploadAction.DELETE_PROJECT_ADMIN_ONLY, UploadAction.deleteProjectRefusal(null, "madants", existing));
        assertNull(UploadAction.deleteProjectRefusal(login(1, true), "madants", existing));
    }

    public void testDeleteSpeciesListMustExist() {
        Map<String, Object> existing = new HashMap<>();
        existing.put("madants", new Object());
        assertEquals(UploadAction.DELETE_PROJECT_NOT_FOUND, UploadAction.deleteProjectRefusal(login(1, true), "x' or '1'='1", existing));
        assertEquals(UploadAction.DELETE_PROJECT_NOT_FOUND, UploadAction.deleteProjectRefusal(login(1, true), "nosuchants", existing));
        assertEquals(UploadAction.DELETE_PROJECT_NOT_FOUND, UploadAction.deleteProjectRefusal(login(1, true), "madants", null));
    }

    public void testRemovedDeveloperActions() {
        assertTrue(UploadAction.isRemovedAction("specimenTest"));
        assertTrue(UploadAction.isRemovedAction("speciesTest"));
        assertFalse(UploadAction.isAdminOnlyAction("speciesTest"));
        assertFalse(UploadAction.isRemovedAction("specimenUpload"));
    }

    // ---------- image upload reports ----------

    public void testImageUploadReportOwnGroupOrAdmin() {
        assertTrue(ImageUploadReport.mayView(login(16, false), 16));
        assertFalse(ImageUploadReport.mayView(login(16, false), 1));
        assertTrue(ImageUploadReport.mayView(login(1, true), 16));
        assertFalse(ImageUploadReport.mayView(null, 16));
    }

    // ---------- AntWeb's calls to itself (nightly Scheduler, postInstantiate) ----------

    private static final String CODE = "0123456789abcdef0123456789abcdef";
    private static final Predicate<String> CURRENT = CODE::equals;

    public void testSchedulerCallAccepted() {
        // What the Scheduler sends: the code in the header, from inside the container.
        assertTrue(SchedulerCall.accepts(CODE, null, "127.0.0.1", null, null, CURRENT));
        assertTrue(SchedulerCall.accepts(CODE, null, "0:0:0:0:0:0:0:1", null, null, CURRENT));
        assertTrue(SchedulerCall.accepts(CODE, null, "::1", null, null, CURRENT));
    }

    public void testSchedulerCodeInQueryStringRefused() {
        assertFalse(SchedulerCall.accepts(null, CODE, "127.0.0.1", null, null, CURRENT));
        assertFalse(SchedulerCall.accepts(CODE, CODE, "127.0.0.1", null, null, CURRENT));
        assertFalse(SchedulerCall.accepts(CODE, "", "127.0.0.1", null, null, CURRENT));
    }

    public void testSchedulerCallFromOutsideRefused() {
        assertFalse(SchedulerCall.accepts(CODE, null, "172.18.0.5", null, null, CURRENT));      // Caddy
        assertFalse(SchedulerCall.accepts(CODE, null, "165.232.136.170", null, null, CURRENT));
        assertFalse(SchedulerCall.accepts(CODE, null, null, null, null, CURRENT));
        assertFalse(SchedulerCall.accepts(CODE, null, "127.0.0.1", "203.0.113.9", null, CURRENT));
        assertFalse(SchedulerCall.accepts(CODE, null, "127.0.0.1", null, "203.0.113.9", CURRENT));
    }

    public void testSchedulerWrongOrNoCodeRefused() {
        assertFalse(SchedulerCall.accepts("wrong", null, "127.0.0.1", null, null, CURRENT));
        assertFalse(SchedulerCall.accepts("", null, "127.0.0.1", null, null, CURRENT));
        assertFalse(SchedulerCall.accepts(null, null, "127.0.0.1", null, null, CURRENT));
        assertFalse(SchedulerCall.accepts(CODE, null, "127.0.0.1", null, null, c -> false));   // no run in progress
    }

    /**
     * The nightly path end to end: HttpUtil.fetchUrl with SchedulerCall.headers(code), as
     * Scheduler.doAction() calls utilData.do?action=setN, reaches a local server that applies
     * SchedulerCall.accepts() to what it received, and is accepted; the URL carries no code.
     */
    public void testSchedulerRequestAsSentIsAccepted() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/utilData.do", ex -> {
            String q = ex.getRequestURI().getRawQuery();
            query.set(q);
            String queryCode = null;
            if (q != null) for (String kv : q.split("&")) if (kv.startsWith(SchedulerCall.QUERY_PARAM + "=")) queryCode = kv.substring(11);
            boolean ok = SchedulerCall.accepts(ex.getRequestHeaders().getFirst(SchedulerCall.HEADER), queryCode,
                ex.getRemoteAddress().getAddress().getHostAddress(), ex.getRequestHeaders().getFirst("X-Forwarded-For"),
                ex.getRequestHeaders().getFirst("CF-Connecting-IP"), CURRENT);
            byte[] body = (ok ? "accepted" : "refused").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            String answer = HttpUtil.fetchUrl(base + "/utilData.do?action=set1&param=allow", 30_000, SchedulerCall.headers(CODE));
            assertEquals("accepted", answer);
            assertFalse(query.get(), query.get().contains(CODE));
            // The old form (code in the URL) is refused.
            assertEquals("refused", HttpUtil.fetchUrl(base + "/utilData.do?action=set1&param=allow&secureCode=" + CODE, 30_000, null));
            assertEquals("refused", HttpUtil.fetchUrl(base + "/utilData.do?action=set1&param=allow&secureCode=" + CODE, 30_000,
                SchedulerCall.headers(CODE)));
        } finally {
            server.stop(0);
        }
    }
}
