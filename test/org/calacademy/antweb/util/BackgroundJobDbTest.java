package test.org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.util.BackgroundJobDb;
import org.calacademy.antweb.util.BackgroundJobs;
import org.calacademy.antweb.util.BackgroundJobs.Job;
import org.calacademy.antweb.util.BackgroundJobs.Status;

/**
 * The table background_job (BackgroundJobDb, 2026-10-20) against MySQL 8.4: a job is written,
 * changed and read back with its status, step, result and times; the recent list by group; at
 * start-up the rows a stopped server left queued or running become "interrupted", except the
 * jobs of the new run.
 *
 * Runs in CI against MySQL 8.4 (host "mysql"), like PlaceImageCountDbTest; ANTWEB_TEST_DB_URL overrides.
 */
public class BackgroundJobDbTest extends TestCase {

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        connection = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        connection.setAutoCommit(true);
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("drop table if exists " + BackgroundJobDb.TABLE);
            s.executeUpdate(BackgroundJobDb.createTable());   // as the application does at startup
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    private static Job upload(int loginId, int groupId, String title) {
        return new Job(BackgroundJobs.UPLOAD, "specimenUpload", title, loginId, "login" + loginId, groupId);
    }

    public void testWriteChangeRead() throws Exception {
        Job job = upload(20, 5, "Specimen upload for Testgruppe (Ä fichier.zip)");
        int id = BackgroundJobDb.insert(connection, job);
        assertTrue(id > 0);
        Job read = BackgroundJobDb.load(connection, id);
        assertEquals(Status.queued, read.getStatus());
        assertEquals("Specimen upload for Testgruppe (Ä fichier.zip)", read.getTitle());
        assertEquals(5, read.getGroupId());
        assertEquals(20, read.getLoginId());
        assertEquals("specimenUpload", read.getAction());
        assertNull(read.getStarted());

        Date started = new Date((System.currentTimeMillis() / 1000) * 1000);
        Date finished = new Date(started.getTime() + 61000);
        job.restore(id, Status.finished, "Finished.", "Upload complete: 2 specimens loaded. Upload ID 7.",
            "https://www.antweb.org/web/log/specimen/x.html", "Your upload report", job.getCreated(), started, finished);
        BackgroundJobDb.update(connection, job);
        read = BackgroundJobDb.load(connection, id);
        assertEquals(Status.finished, read.getStatus());
        assertEquals("Finished.", read.getStep());
        assertEquals("Upload complete: 2 specimens loaded. Upload ID 7.", read.getResult());
        assertEquals("https://www.antweb.org/web/log/specimen/x.html", read.getResultLink());
        assertEquals("Your upload report", read.getResultLinkText());
        assertEquals("the same moment back", started.getTime(), read.getStarted().getTime());
        assertEquals(finished.getTime(), read.getFinished().getTime());
        assertNull(BackgroundJobDb.load(connection, id + 1000));
    }

    public void testLongTextsAreCut() throws Exception {
        Job job = upload(20, 5, "x");
        int id = BackgroundJobDb.insert(connection, job);
        char[] longStep = new char[2000];
        Arrays.fill(longStep, 's');
        job.restore(id, Status.running, new String(longStep), null, null, null, job.getCreated(), new Date(), null);
        BackgroundJobDb.update(connection, job);
        assertEquals(500, BackgroundJobDb.load(connection, id).getStep().length());
    }

    public void testRecentByGroup() throws Exception {
        int a = BackgroundJobDb.insert(connection, upload(20, 5, "a"));
        int b = BackgroundJobDb.insert(connection, upload(21, 6, "b"));
        int c = BackgroundJobDb.insert(connection, upload(20, 5, "c"));
        int d = BackgroundJobDb.insert(connection, new Job(BackgroundJobs.DATA, "placeImageCounts", "d", 1, "admin", 0));
        List<Job> group5 = BackgroundJobDb.recent(connection, 5, 10);
        assertEquals(2, group5.size());
        assertEquals("newest first", c, group5.get(0).getId());
        assertEquals(a, group5.get(1).getId());
        List<Job> all = BackgroundJobDb.recent(connection, 0, 3);
        assertEquals(3, all.size());
        assertEquals(d, all.get(0).getId());
        assertEquals(b, all.get(2).getId());
    }

    public void testStartupMarksTheLastRunInterrupted() throws Exception {
        Job running = upload(20, 5, "running when the server stopped");
        int r = BackgroundJobDb.insert(connection, running);
        running.restore(r, Status.running, "Reading and saving specimens: line 500.", null, null, null, running.getCreated(), new Date(), null);
        BackgroundJobDb.update(connection, running);
        int q = BackgroundJobDb.insert(connection, upload(21, 6, "queued when the server stopped"));
        Job done = upload(22, 7, "finished");
        int f = BackgroundJobDb.insert(connection, done);
        done.restore(f, Status.finished, "Finished.", "ok", null, null, done.getCreated(), new Date(), new Date());
        BackgroundJobDb.update(connection, done);
        int kept = BackgroundJobDb.insert(connection, upload(23, 8, "a job of the new run"));

        int n = BackgroundJobDb.markInterrupted(connection, Collections.singletonList(kept));
        assertEquals(2, n);
        assertEquals(Status.interrupted, BackgroundJobDb.load(connection, r).getStatus());
        assertEquals(BackgroundJobs.INTERRUPTED_STEP, BackgroundJobDb.load(connection, r).getStep());
        assertNotNull(BackgroundJobDb.load(connection, r).getFinished());
        assertEquals(Status.interrupted, BackgroundJobDb.load(connection, q).getStatus());
        assertEquals("finished stays finished", Status.finished, BackgroundJobDb.load(connection, f).getStatus());
        assertEquals("the new run's job is kept", Status.queued, BackgroundJobDb.load(connection, kept).getStatus());
        assertEquals("without the keep list the queued job is marked too", 1, BackgroundJobDb.markInterrupted(connection, Collections.<Integer>emptyList()));
        assertEquals(0, BackgroundJobDb.markInterrupted(connection, Collections.<Integer>emptyList()));
    }
}
