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

    private static void dump(File daily, String name, long bytes, long modified) throws Exception {
        daily.mkdirs();
        File f = new File(daily, name);
        Files.write(f.toPath(), new byte[(int) bytes]);
        assertTrue(f.setLastModified(modified));
    }

    public void testWorkingMountsAreOk() throws Exception {
        File antweb = tmp(), backup = tmp();
        new File(antweb, MountCheck.PROBE_DIR).mkdirs();
        long now = System.currentTimeMillis();
        dump(new File(backup, "db/daily"), "2026-10-23.sql.gz", MountCheck.DUMP_MIN_BYTES + 1, now - 2 * HOUR);
        List<String> p = MountCheck.problems(antweb, backup, true, "production", now, 10_000);
        assertTrue(p.toString(), p.isEmpty());
        assertNull(MountCheck.alert(p));
        assertTrue(new File(antweb, MountCheck.PROBE_DIR + "/mountcheck-production.txt").isFile());
    }

    public void testMissingMountIsReported() throws Exception {
        File antweb = new File(tmp(), "gone/antweb");   // a file stands where the folder should be: cannot write
        antweb.getParentFile().mkdirs();
        Files.write(antweb.toPath(), new byte[1]);
        File backup = new File(tmp(), "not-mounted");
        List<String> p = MountCheck.problems(antweb, backup, true, "production", System.currentTimeMillis(), 10_000);
        assertEquals(p.toString(), 2, p.size());
        assertTrue(p.get(0), p.get(0).contains("is missing"));
        assertTrue(p.get(1), p.get(1).contains("cannot list"));
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

    public void testOldOrSmallDumpIsReported() throws Exception {
        File daily = new File(tmp(), "db/daily");
        long now = System.currentTimeMillis();
        dump(daily, "2026-10-21.sql.gz", MountCheck.DUMP_MIN_BYTES + 1, now - 50 * HOUR);
        dump(daily, "2026-10-22.sql.gz", MountCheck.DUMP_MIN_BYTES + 1, now - 30 * HOUR);
        dump(daily, "ant-notadump.txt", 10, now);
        String p = MountCheck.dumpProblem(daily, now, MountCheck.DUMP_MAX_AGE_MS, MountCheck.DUMP_MIN_BYTES);
        assertNotNull(p);
        assertTrue(p, p.contains("2026-10-22.sql.gz") && p.contains("30 hours"));

        dump(daily, "2026-10-23.sql.gz", 100, now - HOUR);
        p = MountCheck.dumpProblem(daily, now, MountCheck.DUMP_MAX_AGE_MS, MountCheck.DUMP_MIN_BYTES);
        assertTrue(p, p.contains("only 100 bytes"));

        assertTrue(MountCheck.dumpProblem(tmp(), now, MountCheck.DUMP_MAX_AGE_MS, MountCheck.DUMP_MIN_BYTES).startsWith("no database dump"));
    }

    public void testStagingSkipsTheDump() throws Exception {
        File antweb = tmp();
        new File(antweb, MountCheck.PROBE_DIR).mkdirs();
        List<String> p = MountCheck.problems(antweb, new File("/nonexistent-mount"), false, "staging", System.currentTimeMillis(), 10_000);
        assertTrue(p.toString(), p.isEmpty());
    }
}
