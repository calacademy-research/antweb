package test.org.calacademy.antweb.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;

import org.calacademy.antweb.Login;
import org.calacademy.antweb.upload.UploadAction;
import org.calacademy.antweb.util.BackgroundJobs;
import org.calacademy.antweb.util.BackgroundJobs.Job;
import org.calacademy.antweb.util.BackgroundJobs.Outcome;
import org.calacademy.antweb.util.BackgroundJobs.Runner;
import org.calacademy.antweb.util.BackgroundJobs.Status;
import org.calacademy.antweb.util.BackgroundJobs.Submitted;
import org.calacademy.antweb.util.JobStatusAction;
import org.calacademy.antweb.util.UtilDataAction;

/**
 * Uploads and admin data jobs run in the background (BackgroundJobs, 2026-10-20): status
 * changes, one job at a time, a second upload of the same group (or by the same login) goes to
 * the first one, waiting while the gate says busy (another upload, the nightly run), jobs of a
 * stopped server shown as interrupted, the progress text, who may see a job, and the upload and
 * compute flags taken only when free. No database (a list stands in for the table; the table
 * itself: BackgroundJobDbTest).
 */
public class BackgroundJobsTest extends TestCase {

    /** The table, in memory. */
    static final class MemoryStore implements BackgroundJobs.Store {
        final Map<Integer, Job> rows = new ConcurrentHashMap<>();
        final AtomicInteger next = new AtomicInteger(100);
        final AtomicInteger updates = new AtomicInteger();

        public int insert(Job job) { int id = next.incrementAndGet(); rows.put(id, copy(job, id)); return id; }
        public void update(Job job) { updates.incrementAndGet(); rows.put(job.getId(), copy(job, job.getId())); }
        public Job load(int id) { Job j = rows.get(id); return j == null ? null : copy(j, id); }
        public List<Job> recent(int groupId, int limit) {
            List<Job> out = new ArrayList<>();
            for (Job j : rows.values()) if (groupId == 0 || j.getGroupId() == groupId) out.add(copy(j, j.getId()));
            out.sort(Comparator.comparingInt(Job::getId).reversed());
            return out;
        }
        static Job copy(Job j, int id) {
            Job c = new Job(j.getKind(), j.getAction(), j.getTitle(), j.getLoginId(), j.getLoginName(), j.getGroupId());
            c.restore(id, j.getStatus(), j.getStep(), j.getResult(), j.getResultLink(), j.getResultLinkText(),
                j.getCreated(), j.getStarted(), j.getFinished());
            return c;
        }
    }

    private MemoryStore store;
    private Runner runner;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    @Override
    protected void setUp() {
        store = new MemoryStore();
        busy.set(false);
        runner = new Runner(store, job -> busy.get() ? "Waiting for another upload to finish." : null, 20, 0);
    }

    @Override
    protected void tearDown() {
        runner.shutdown();
        UploadAction.setIsInUploadProcess(null);
        UtilDataAction.setInComputeProcess(null);
    }

    private static Job upload(int loginId, int groupId) {
        return new Job(BackgroundJobs.UPLOAD, "specimenUpload", "Specimen upload for group " + groupId, loginId, "login" + loginId, groupId);
    }

    private static void waitFor(Job job, Status status) throws InterruptedException {
        for (int i = 0; i < 500 && job.getStatus() != status; i++) Thread.sleep(10);
        assertEquals(job.toString(), status, job.getStatus());
    }

    public void testStatusChangesAndResult() throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        Submitted s = runner.submit(upload(1, 5), job -> {
            BackgroundJobs.step("Reading the file.");
            go.await(5, TimeUnit.SECONDS);
            return Outcome.ok("Upload complete: 2 specimens loaded.", "https://example.org/web/log/specimen/r.html", "Your upload report");
        });
        assertTrue(s.isNew);
        Job job = s.job;
        assertTrue("a job number from the table", job.getId() > 100);
        waitFor(job, Status.running);
        for (int i = 0; i < 100 && !"Reading the file.".equals(job.getStep()); i++) Thread.sleep(10);
        assertEquals("Reading the file.", job.getStep());
        assertNotNull(job.getStarted());
        assertEquals("the table has the running status", Status.running, store.load(job.getId()).getStatus());
        go.countDown();
        waitFor(job, Status.finished);
        assertEquals("Upload complete: 2 specimens loaded.", job.getResult());
        assertEquals("Your upload report", job.getResultLinkText());
        assertNotNull(job.getFinished());
        assertEquals("the table has the end", Status.finished, store.load(job.getId()).getStatus());
        assertEquals("the same job from the runner", job, runner.get(job.getId()));
    }

    public void testFailedOutcomeAndException() throws Exception {
        Job a = runner.submit(upload(1, 5), job -> Outcome.failed("Specimen File must be a tab-delimited file.")).job;
        waitFor(a, Status.failed);
        assertEquals("Specimen File must be a tab-delimited file.", a.getResult());
        Job b = runner.submit(upload(2, 6), job -> { throw new IllegalStateException("secret detail"); }).job;
        waitFor(b, Status.failed);
        assertTrue(b.getResult(), b.getResult().contains("IllegalStateException"));
        assertFalse("no exception text on the page", b.getResult().contains("secret detail"));
    }

    public void testOneAtATime() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger runningNow = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        BackgroundJobs.Work work = job -> {
            int n = runningNow.incrementAndGet();
            maxRunning.accumulateAndGet(n, Math::max);
            release.await(5, TimeUnit.SECONDS);
            runningNow.decrementAndGet();
            return Outcome.ok("done");
        };
        Job a = runner.submit(upload(1, 5), work).job;
        Job b = runner.submit(upload(2, 6), work).job;   // another group: queued behind a
        Job c = runner.submit(new Job(BackgroundJobs.DATA, "placeImageCounts", "Data job", 9, "admin", 0), work).job;
        waitFor(a, Status.running);
        Thread.sleep(100);
        assertEquals(Status.queued, b.getStatus());
        assertEquals(Status.queued, c.getStatus());
        release.countDown();
        waitFor(c, Status.finished);
        assertEquals(Status.finished, a.getStatus());
        assertEquals(Status.finished, b.getStatus());
        assertEquals("never two at once", 1, maxRunning.get());
        assertFalse("b started after a ended", b.getStarted().before(a.getFinished()));
        assertFalse("c started after b ended", c.getStarted().before(b.getFinished()));
    }

    public void testSecondUploadGoesToTheFirst() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        BackgroundJobs.Work work = job -> { release.await(5, TimeUnit.SECONDS); return Outcome.ok("done"); };
        Submitted first = runner.submit(upload(1, 5), work);
        Submitted sameGroup = runner.submit(upload(7, 5), work);       // another curator of the group
        Submitted sameLogin = runner.submit(upload(1, 8), work);       // the same login, "upload as" another group
        assertTrue(first.isNew);
        assertFalse(sameGroup.isNew);
        assertSame(first.job, sameGroup.job);
        assertFalse(sameLogin.isNew);
        assertSame(first.job, sameLogin.job);
        assertSame(first.job, runner.findDuplicate(upload(7, 5)));
        Submitted other = runner.submit(upload(2, 6), work);           // another group and login: queued
        assertTrue(other.isNew);
        release.countDown();
        waitFor(other.job, Status.finished);
        assertNull("after the end a new upload is allowed", runner.findDuplicate(upload(7, 5)));
        Submitted again = runner.submit(upload(7, 5), job -> Outcome.ok("done"));
        assertTrue(again.isNew);
        waitFor(again.job, Status.finished);
    }

    public void testDataJobSameActionNotTwice() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        BackgroundJobs.Work work = job -> { release.await(5, TimeUnit.SECONDS); return Outcome.ok("done"); };
        Submitted a = runner.submit(new Job(BackgroundJobs.DATA, "placeImageCounts", "x", 9, "admin", 0), work);
        Submitted b = runner.submit(new Job(BackgroundJobs.DATA, "placeImageCounts", "x", 10, "admin2", 0), work);
        Submitted c = runner.submit(new Job(BackgroundJobs.DATA, "imageCountCrawl", "y", 9, "admin", 0), work);
        assertSame(a.job, b.job);
        assertFalse(b.isNew);
        assertTrue(c.isNew);
        release.countDown();
        waitFor(c.job, Status.finished);
    }

    public void testWaitsWhileBusy() throws Exception {
        busy.set(true);
        Job job = runner.submit(upload(1, 5), j -> Outcome.ok("done")).job;
        Thread.sleep(150);
        assertEquals(Status.queued, job.getStatus());
        assertEquals("Waiting for another upload to finish.", job.getStep());
        assertEquals("the table shows the reason", "Waiting for another upload to finish.", store.load(job.getId()).getStep());
        busy.set(false);
        waitFor(job, Status.finished);
    }

    public void testJobsOfAStoppedServerAreInterrupted() throws Exception {
        // Rows left queued or running by the last run of the application (not in this runner).
        Job old = upload(3, 7);
        int id = store.insert(old);
        Job stored = store.load(id);
        stored.restore(id, Status.running, "Reading and saving specimens: line 5,000 of 9,000.", null, null, null,
            stored.getCreated(), new java.util.Date(), null);
        store.update(stored);
        Job seen = runner.get(id);
        assertEquals(Status.interrupted, seen.getStatus());
        assertEquals(BackgroundJobs.INTERRUPTED_STEP, seen.getStep());
        assertFalse(seen.isActive());
        List<Job> recent = runner.recent(7, 10);
        assertEquals(1, recent.size());
        assertEquals(Status.interrupted, recent.get(0).getStatus());
        assertTrue("not active in this run", runner.activeIds().isEmpty());
        assertNull("an interrupted upload does not block a new one", runner.findDuplicate(upload(3, 7)));
        String page = JobStatusAction.jobHtml(seen, false, false, "https://example.org");
        assertTrue(page, page.contains("data-status=\"interrupted\""));
        assertTrue(page, page.contains("Please upload the file again."));
    }

    public void testProgressText() {
        assertEquals("Reading and saving specimens: line 1,500 of 265,384.",
            BackgroundJobs.progressText("Reading and saving specimens", 1500, 265384));
        assertEquals("Saving: line 500.", BackgroundJobs.progressText("Saving", 500, 0));
        // Outside a background job (a web request, the nightly run) step and progress do nothing.
        assertNull(BackgroundJobs.current());
        BackgroundJobs.step("x");
        BackgroundJobs.progress(500);
    }

    public void testProgressInsideAJob() throws Exception {
        Job job = runner.submit(upload(1, 5), j -> {
            j.setProgress("Reading and saving specimens", 2000);
            for (long n = 1; n <= 1000; n++) BackgroundJobs.progress(n);
            return Outcome.ok(j.getStep());
        }).job;
        waitFor(job, Status.finished);
        assertEquals("Reading and saving specimens: line 1,000 of 2,000.", job.getResult());
    }

    public void testWhoSeesAJob() {
        Login curator = new Login();
        curator.setId(20);
        curator.setGroupId(5);
        Login other = new Login();
        other.setId(21);
        other.setGroupId(6);
        Login admin = new Login();
        admin.setId(1);
        admin.setGroupId(1);
        admin.setIsAdmin(true);
        Job up = upload(30, 5);
        Job data = new Job(BackgroundJobs.DATA, "placeImageCounts", "x", 1, "admin", 0);
        assertTrue("same group", JobStatusAction.mayView(up, curator, false, 5));
        assertFalse("another group", JobStatusAction.mayView(up, other, false, 6));
        assertTrue("own upload as another group", JobStatusAction.mayView(upload(21, 5), other, false, 6));
        assertFalse("admin jobs: admins only", JobStatusAction.mayView(data, curator, false, 5));
        assertTrue(JobStatusAction.mayView(data, admin, true, 1));
        assertTrue(JobStatusAction.mayView(up, admin, true, 1));
        assertFalse(JobStatusAction.mayView(up, null, false, 5));
    }

    public void testUploadResultIsEscaped() throws Exception {
        Job job = runner.submit(upload(1, 5), j -> Outcome.failed("Bad line <script>alert(1)</script>")).job;
        waitFor(job, Status.failed);
        String page = JobStatusAction.jobHtml(job, false, false, "https://example.org");
        assertFalse(page, page.contains("<script>"));
        assertTrue(page, page.contains("The upload did not complete."));
        assertTrue(page, page.contains("data-status=\"failed\""));
        String already = JobStatusAction.jobHtml(job, true, false, "https://example.org");
        assertTrue(already, already.contains("You already have an upload waiting or running"));
    }

    public void testFlagsTakenOnlyWhenFree() {
        assertTrue(UploadAction.claimUploadProcess("a:group"));
        assertFalse(UploadAction.claimUploadProcess("b:group"));
        UploadAction.releaseUploadProcess("b:group");   // not the holder: no change
        assertEquals("a:group", UploadAction.getIsInUploadProcess());
        UploadAction.releaseUploadProcess("a:group");
        assertFalse(UploadAction.isInUploadProcess());

        assertTrue(UtilDataAction.claimComputeProcess("placeImageCounts"));
        assertFalse(UtilDataAction.claimComputeProcess("set2"));
        UtilDataAction.releaseComputeProcess("placeImageCounts");
        assertFalse(UtilDataAction.isInComputeProcess());
    }

    /** The upload page checks a zip's list of entries before it queues the upload (SafeUnzip.check). */
    public void testZipCheckedBeforeTheQueue() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("bgjobs").toFile();
        java.io.File good = new java.io.File(dir, "good.zip");
        java.io.File bad = new java.io.File(dir, "bad.zip");
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(good))) {
            z.putNextEntry(new java.util.zip.ZipEntry("__MACOSX/._specimens.txt"));
            z.write(new byte[] {1});
            z.putNextEntry(new java.util.zip.ZipEntry("specimens.txt"));
            z.write("SpecimenCode\tGenus\n".getBytes("UTF-8"));
        }
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(bad))) {
            z.putNextEntry(new java.util.zip.ZipEntry("specimens.csv"));
            z.write("x".getBytes("UTF-8"));
        }
        assertEquals("specimens.txt", org.calacademy.antweb.upload.SafeUnzip.check(good, org.calacademy.antweb.upload.SafeUnzip.ONE_TXT, ".txt file"));
        try {
            org.calacademy.antweb.upload.SafeUnzip.check(bad, org.calacademy.antweb.upload.SafeUnzip.ONE_TXT, ".txt file");
            fail("a zip without a .txt file was accepted");
        } catch (org.calacademy.antweb.upload.SafeUnzip.Refused r) {
            assertEquals("The zip file has no .txt file.", r.getMessage());
        }
        assertEquals("nothing unpacked", 2, dir.listFiles().length);
    }

    public void testInRequestAndBackgroundActions() {
        assertTrue(UploadAction.isBackgroundAction("specimenUpload"));
        assertTrue(UploadAction.isBackgroundAction("taxonWorksUpload"));
        assertTrue(UploadAction.isBackgroundAction("fetchAndReloadWorldants"));
        assertFalse(UploadAction.isBackgroundAction("museumCalc"));
        assertTrue(UtilDataAction.runsInBackground("placeImageCounts", false));
        assertTrue(UtilDataAction.runsInBackground("specimenNameUpdatePreview", false));
        assertFalse("the nightly run stays in the request", UtilDataAction.runsInBackground("set1", true));
        assertFalse(UtilDataAction.runsInBackground("siteWarning", false));
        assertFalse(UtilDataAction.runsInBackground("fixGeolocaleTaxonParentage", false));
        assertFalse(UtilDataAction.runsInBackground("noSuchAction", false));
    }
}
