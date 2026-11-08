package test.org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Date;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.calacademy.antweb.util.DailyUpdate;
import org.calacademy.antweb.util.DailyUpdateDb;

/**
 * The table daily_update_step (DailyUpdateDb, 2026-11-07) against MySQL 8.4: a step's start and end are written and
 * read back in step order; a second start starts it again; the usual time is the median of the ok rows of the last
 * completed runs before a run (runs without an end row, failed and skipped steps left out); the last runs newest first;
 * old runs pruned.
 *
 * Runs in CI against MySQL 8.4 (host "mysql"), like BackgroundJobDbTest; ANTWEB_TEST_DB_URL overrides.
 */
public class DailyUpdateDbTest extends TestCase {

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        connection = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        connection.setAutoCommit(true);
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("drop table if exists " + DailyUpdateDb.TABLE);
        }
        DailyUpdateDb.resetSchemaCheck();
        DailyUpdateDb.ensureSchema(connection);   // as the application does at startup
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    private static Date at(String day, String time) {
        return DailyUpdate.pacific(day + "T" + time);
    }

    /** A step that took seconds, started at 05:00 of day. */
    private void step(String day, String key, long seconds, String result) throws Exception {
        Date run = at(day, "05:00:00");
        DailyUpdateDb.start(connection, run, key, DailyUpdate.index(key) + 1, run);
        DailyUpdateDb.end(connection, run, key, new Date(run.getTime() + seconds * 1000), result, null);
    }

    public void testStartEndAndReadBack() throws Exception {
        Date run = at("2026-11-07", "05:00:00");
        DailyUpdateDb.start(connection, run, "set2", 5, at("2026-11-07", "05:43:00"));
        DailyUpdateDb.start(connection, run, "antcatImport", 1, run);
        DailyUpdateDb.end(connection, run, "antcatImport", at("2026-11-07", "05:05:00"), "ok", "AntCat import done");
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 70; i++) longText.append("0123456789");
        DailyUpdateDb.end(connection, run, "set2", at("2026-11-07", "05:53:00"), "failed", longText.toString());

        List<DailyUpdate.Row> rows = DailyUpdateDb.rows(connection, run);
        assertEquals(2, rows.size());
        assertEquals("step order", "antcatImport", rows.get(0).step);
        assertEquals(run, rows.get(0).started);
        assertEquals(at("2026-11-07", "05:05:00"), rows.get(0).ended);
        assertEquals("ok", rows.get(0).result);
        assertEquals("AntCat import done", rows.get(0).detail);
        assertEquals("failed", rows.get(1).result);
        assertEquals("detail cut to the column", 500, rows.get(1).detail.length());

        // Started again (a set run twice in one run): its end and result are cleared.
        DailyUpdateDb.start(connection, run, "set2", 5, at("2026-11-07", "06:00:00"));
        DailyUpdate.Row again = DailyUpdateDb.rows(connection, run).get(1);
        assertEquals(at("2026-11-07", "06:00:00"), again.started);
        assertNull(again.ended);
        assertNull(again.result);
    }

    public void testUsualIsTheMedianOfTheLastCompletedRuns() throws Exception {
        // Nine completed runs, 2026-10-28 .. 2026-11-05; set2 took 100, 200, ..., 900 s.
        for (int d = 0; d < 9; d++) {
            String day = String.format("2026-%s", d < 4 ? "10-" + (28 + d) : "11-0" + (d - 3));
            step(day, "set2", (d + 1) * 100L, "ok");
            step(day, "set3", 60, d == 8 ? "failed" : "ok");      // a failed step does not count
            step(day, "taxonWorks", 999, "skipped");              // nor a skipped one
            step(day, DailyUpdate.END, 0, "ok");
        }
        // 2026-11-06: no end row (interrupted): not a completed run.
        step("2026-11-06", "set2", 5000, "ok");

        Date today = at("2026-11-07", "05:00:00");
        Map<String, Long> usual = DailyUpdateDb.usual(connection, today, 7);
        // the last 7 completed: set2 300..900 s, median 600
        assertEquals(Long.valueOf(600), usual.get("set2"));
        assertEquals(Long.valueOf(60), usual.get("set3"));
        assertNull("skipped rows give no usual time", usual.get("taxonWorks"));

        // Before an earlier run: only the runs before it.
        Map<String, Long> earlier = DailyUpdateDb.usual(connection, at("2026-10-31", "05:00:00"), 7);
        assertEquals("100, 200, 300", Long.valueOf(200), earlier.get("set2"));
        assertTrue(DailyUpdateDb.usual(connection, at("2026-10-28", "05:00:00"), 7).isEmpty());
        assertEquals(Long.valueOf(600), DailyUpdateDb.usual(connection, null, 7).get("set2"));
    }

    public void testLastRunsAndPrune() throws Exception {
        step("2026-11-05", "set2", 10, "ok");
        step("2026-11-06", "set2", 10, "ok");
        step("2026-11-06", DailyUpdate.END, 0, "ok");
        step("2025-01-01", "set2", 10, "ok");
        List<Date> runs = DailyUpdateDb.lastRuns(connection, 2);
        assertEquals(2, runs.size());
        assertEquals("newest first", at("2026-11-06", "05:00:00"), runs.get(0));
        assertEquals(at("2026-11-05", "05:00:00"), runs.get(1));
        assertEquals("the 2025 run is older than 400 days (today 2026-11 or later)", 1, DailyUpdateDb.prune(connection, 400));
        assertEquals(2, DailyUpdateDb.lastRuns(connection, 10).size());
    }

    public void testSchemaCheckIsQuietWhenTheTableExists() throws Exception {
        DailyUpdateDb.resetSchemaCheck();
        DailyUpdateDb.ensureSchema(connection);   // CREATE TABLE IF NOT EXISTS: no error, rows kept
        step("2026-11-07", "set2", 10, "ok");
        DailyUpdateDb.resetSchemaCheck();
        DailyUpdateDb.ensureSchema(connection);
        assertEquals(1, DailyUpdateDb.rows(connection, at("2026-11-07", "05:00:00")).size());
    }
}
