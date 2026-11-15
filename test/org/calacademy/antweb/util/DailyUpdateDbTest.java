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

    /**
     * 2026-11-15: Place list totals counts every place on Sundays (2,440 places, 17 min) and only the changed ones on
     * the other days (about 2.5 min); the weekday median (7 min) made the first Sunday email "running long". Its usual
     * time is the median of the same kind of day; other steps keep the median of all days.
     */
    public void testPlaceListTotalsIsComparedWithTheSameKindOfDay() throws Exception {
        // 2026-11-01 and 2026-11-08 are Sundays (full counts, 1000 and 1100 s); the weekdays between take 150 s.
        String[] days = {"2026-10-31", "2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06", "2026-11-07",
                         "2026-11-08", "2026-11-09", "2026-11-10", "2026-11-11", "2026-11-12", "2026-11-13", "2026-11-14"};
        for (String day : days) {
            boolean sunday = day.equals("2026-11-01") || day.equals("2026-11-08");
            step(day, "placeListTotals", sunday ? (day.endsWith("01") ? 1000 : 1100) : 150, "ok");
            step(day, "set2", 540, "ok");
            step(day, DailyUpdate.END, 0, "ok");
        }
        assertTrue(DailyUpdateDb.fullCountDay(at("2026-11-15", "05:00:00")));
        assertFalse(DailyUpdateDb.fullCountDay(at("2026-11-16", "05:00:00")));

        Map<String, Long> sunday = DailyUpdateDb.usual(connection, at("2026-11-15", "05:00:00"), 7);
        assertEquals("a Sunday: the median of the earlier Sundays (1000, 1100)", Long.valueOf(1050), sunday.get("placeListTotals"));
        assertEquals("other steps: all days", Long.valueOf(540), sunday.get("set2"));
        assertFalse("17 min on a Sunday is not an overrun", DailyUpdate.isOverrun(1030, sunday.get("placeListTotals")));

        Map<String, Long> monday = DailyUpdateDb.usual(connection, at("2026-11-16", "05:00:00"), 7);
        assertEquals("a weekday: the last 7 weekdays only (the Sunday of 11-08 is left out)", Long.valueOf(150), monday.get("placeListTotals"));

        // No earlier full count (only the Saturday of 10-31 before it): the longest recorded run of the step, so a hung
        // step still shows yellow and emails (review of #363), never no usual time once the step has run.
        Map<String, Long> firstSunday = DailyUpdateDb.usual(connection, at("2026-11-01", "05:00:00"), 7);
        assertEquals(Long.valueOf(150), firstSunday.get("placeListTotals"));
        assertNull("no recorded run at all: no usual time", DailyUpdateDb.usual(connection, at("2026-10-31", "05:00:00"), 7).get("placeListTotals"));
    }

    /** A step that took seconds, started at 05:00 of day, with its recorded line. */
    private void step(String day, String key, long seconds, String result, String detail) throws Exception {
        Date run = at(day, "05:00:00");
        DailyUpdateDb.start(connection, run, key, DailyUpdate.index(key) + 1, run);
        DailyUpdateDb.end(connection, run, key, new Date(run.getTime() + seconds * 1000), result, detail);
    }

    /**
     * Review of #363: an earlier run's kind is what the step recorded, not its weekday. A Tuesday that counted every
     * place (new counting rules, old fingerprints: production 2026-11-10, 1,987 places, 584 s) joins the full counts;
     * a Sunday that counted few joins the partial ones; a row without the line goes by its weekday.
     */
    public void testAnEarlierRunIsAFullCountByWhatItRecorded() throws Exception {
        assertTrue(DailyUpdateDb.wasFullCount("Place list totals counted: 2440 places in 1030 s (2440 recounted, 0 unchanged); recounted because: full count 2440", at("2026-11-15", "05:00:00")));
        assertTrue("a weekday full count", DailyUpdateDb.wasFullCount("1987 places in 584 s (1987 recounted, 0 unchanged)", at("2026-11-10", "05:00:00")));
        assertFalse(DailyUpdateDb.wasFullCount("2435 places in 152 s (50 recounted, 2385 unchanged)", at("2026-11-14", "05:00:00")));
        assertFalse("a Sunday that counted few", DailyUpdateDb.wasFullCount("2435 places in 60 s (40 recounted, 2395 unchanged)", at("2026-11-15", "05:00:00")));
        assertTrue("no line: by its day (Sunday)", DailyUpdateDb.wasFullCount(null, at("2026-11-08", "05:00:00")));
        assertFalse("no line: by its day (weekday)", DailyUpdateDb.wasFullCount("Place list totals counted: FAILED, 0 places", at("2026-11-09", "05:00:00")));

        step("2026-11-08", "placeListTotals", 140, "ok", "2435 places in 140 s (45 recounted, 2390 unchanged)");    // a Sunday, partial
        step("2026-11-08", DailyUpdate.END, 0, "ok");
        step("2026-11-10", "placeListTotals", 584, "ok", "1987 places in 584 s (1987 recounted, 0 unchanged)");    // a Tuesday, full
        step("2026-11-10", DailyUpdate.END, 0, "ok");
        step("2026-11-11", "placeListTotals", 150, "ok", "2435 places in 150 s (50 recounted, 2385 unchanged)");
        step("2026-11-11", DailyUpdate.END, 0, "ok");
        assertEquals("a Sunday: the Tuesday full count, not the partial Sunday", Long.valueOf(584),
            DailyUpdateDb.usual(connection, at("2026-11-15", "05:00:00"), 7).get("placeListTotals"));
        assertEquals("a weekday: the partial runs (140, 150)", Long.valueOf(145),
            DailyUpdateDb.usual(connection, at("2026-11-16", "05:00:00"), 7).get("placeListTotals"));
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
