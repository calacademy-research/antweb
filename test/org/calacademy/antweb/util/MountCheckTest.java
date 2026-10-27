package test.org.calacademy.antweb.util;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.util.MountCheck;

/**
 * MountCheck (2026-10-23): the hourly check of /mnt/antweb (write and read back) and /mnt/backup (newest daily dump),
 * added after both production mounts failed for 26 hours unnoticed.
 */
public class MountCheckTest extends TestCase {

    private static final long HOUR = 3600 * 1000L;

    private File tmp() throws Exception {
        File d = Files.createTempDirectory("mountcheck").toFile();
        d.deleteOnExit();
        return d;
    }

    /** The status line bin/db-daily-dump.sh writes, with the file's time set to modified. */
    private static File status(File dir, String line, long modified) throws Exception {
        File f = new File(dir, MountCheck.DUMP_STATUS);
        Files.write(f.toPath(), (line + "\n").getBytes("UTF-8"));
        assertTrue(f.setLastModified(modified));
        return f;
    }

    public void testWorkingMountsAreOk() throws Exception {
        File antweb = tmp();
        new File(antweb, MountCheck.PROBE_DIR).mkdirs();
        long now = System.currentTimeMillis();
        File st = status(tmp(), "ok 2026-10-24T01:02:00-0700 123456789 /mnt/backup/db/daily/2026-10-24.sql.gz", now - 2 * HOUR);
        List<String> p = MountCheck.problems(antweb, st, true, "production", now, 10_000);
        assertTrue(p.toString(), p.isEmpty());
        assertNull(MountCheck.alert(p));
        assertTrue(new File(antweb, MountCheck.PROBE_DIR + "/mountcheck-production.txt").isFile());
    }

    public void testMissingMountIsReported() throws Exception {
        File antweb = new File(tmp(), "gone/antweb");   // a file stands where the folder should be: cannot write
        antweb.getParentFile().mkdirs();
        Files.write(antweb.toPath(), new byte[1]);
        File noStatus = new File(tmp(), MountCheck.DUMP_STATUS);
        MountCheck.s_startedAt = 0;   // running for a long time: a missing status line is a problem
        List<String> p = MountCheck.problems(antweb, noStatus, true, "production", System.currentTimeMillis(), 10_000);
        assertEquals(p.toString(), 2, p.size());
        assertTrue(p.get(0), p.get(0).contains("is missing"));
        assertTrue(p.get(1), p.get(1).startsWith("database dump: no db-dump-status.txt"));
        String alert = MountCheck.alert(p);
        assertTrue(alert, alert.startsWith("Mount check: ") && alert.contains("docs/runbook.md"));
    }

    /** The check never creates folders: a missing upload-report folder means the mount is missing (staging false alarm, 2026-10-23). */
    public void testDoesNotCreateFolders() throws Exception {
        File antweb = tmp();
        String p = MountCheck.writeReadProblem(new File(antweb, MountCheck.PROBE_DIR + "/mountcheck-staging.txt"), "x");
        assertTrue(p, p.contains("is missing"));
        assertFalse(new File(antweb, "web").exists());
    }

    /** The host's dump writes a status line: an old line, a failure or a small dump is reported (2026-10-24). */
    public void testDumpStatus() throws Exception {
        long now = System.currentTimeMillis();
        // Just started (the first day after the deploy): no status line yet is not a problem.
        MountCheck.s_startedAt = now - HOUR;
        assertNull(MountCheck.dumpStatusProblem(new File(tmp(), MountCheck.DUMP_STATUS), now, MountCheck.DUMP_MAX_AGE_MS, MountCheck.DUMP_MIN_BYTES));
        MountCheck.s_startedAt = -1;
        long max = MountCheck.DUMP_MAX_AGE_MS, min = MountCheck.DUMP_MIN_BYTES;
        File dir = tmp();
        assertNull(MountCheck.dumpStatusProblem(status(dir, "ok 2026-10-24T01:02:00-0700 123456789 /mnt/backup/db/daily/2026-10-24.sql.gz", now - HOUR), now, max, min));
        String p = MountCheck.dumpStatusProblem(status(dir, "ok 2026-10-22T01:02:00-0700 123456789 x.sql.gz", now - 30 * HOUR), now, max, min);
        assertTrue(p, p.contains("30 hours ago"));
        p = MountCheck.dumpStatusProblem(status(dir, "failed 2026-10-24T01:00:01-0700 /mnt/backup/db/daily is missing (/mnt/backup not mounted?)", now - HOUR), now, max, min);
        assertTrue(p, p.startsWith("the daily database dump failed") && p.contains("not mounted"));
        p = MountCheck.dumpStatusProblem(status(dir, "ok 2026-10-24T01:02:00-0700 2048 x.sql.gz", now - HOUR), now, max, min);
        assertTrue(p, p.contains("only 2048 bytes"));
        p = MountCheck.dumpStatusProblem(status(dir, "ok 2026-10-24T01:02:00-0700", now - HOUR), now, max, min);
        assertTrue(p, p.contains("malformed"));
    }

    public void testStagingSkipsTheDump() throws Exception {
        File antweb = tmp();
        new File(antweb, MountCheck.PROBE_DIR).mkdirs();
        List<String> p = MountCheck.problems(antweb, new File("/nonexistent-mount"), false, "staging", System.currentTimeMillis(), 10_000);
        assertTrue(p.toString(), p.isEmpty());
    }
}
