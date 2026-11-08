package test.org.calacademy.antweb.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import junit.framework.TestCase;

import org.calacademy.antweb.util.DailyUpdate;
import org.calacademy.antweb.util.DailyUpdate.Row;
import org.calacademy.antweb.util.DailyUpdate.StepView;

/**
 * DailyUpdate (2026-11-07), the Daily update page's logic without a database: the usual time (median), the slow
 * (yellow) and overrun (email) flags, the steps and their order, the state of each step and of the run, the minutes
 * left, the hover and the visitors' line, the result of a step from its compute.log line, and the JSON shape agreed
 * with the production morning check. The table: DailyUpdateDbTest.
 */
public class DailyUpdateTest extends TestCase {

    private static Date t(String hhmmss) {
        return DailyUpdate.pacific("2026-11-07T" + hhmmss);
    }

    public void testMedian() {
        assertNull(DailyUpdate.median(null));
        assertNull(DailyUpdate.median(Collections.emptyList()));
        assertEquals(Long.valueOf(5), DailyUpdate.median(Arrays.asList(5L)));
        assertEquals(Long.valueOf(30), DailyUpdate.median(Arrays.asList(90L, 10L, 30L)));
        assertEquals("even count: mean of the middle two", Long.valueOf(25), DailyUpdate.median(Arrays.asList(40L, 10L, 20L, 30L)));
        assertEquals("one slow night does not move it", Long.valueOf(300),
            DailyUpdate.median(Arrays.asList(300L, 290L, 310L, 3600L, 300L, 305L, 295L)));
    }

    public void testSlowAndOverrunFlags() {
        assertFalse("no usual time, no flag", DailyUpdate.isSlow(10000, null));
        assertFalse(DailyUpdate.isOverrun(10000, null));
        assertFalse(DailyUpdate.isSlow(1400, 1000L));                // 1.4x
        assertTrue(DailyUpdate.isSlow(1600, 1000L));                 // 1.6x and 600 s over
        assertFalse("short step: seconds of noise", DailyUpdate.isSlow(20, 10L));
        assertFalse(DailyUpdate.isOverrun(1900, 1000L));             // 1.9x
        assertTrue(DailyUpdate.isOverrun(2100, 1000L));              // 2.1x and 1100 s over
        assertFalse("2.5x but only 150 s over", DailyUpdate.isOverrun(250, 100L));
        assertTrue(DailyUpdate.isOverrun(3600, 1200L));
    }

    public void testStepsAndOrder() {
        List<String> keys = new ArrayList<>();
        for (DailyUpdate.Step s : DailyUpdate.STEPS) keys.add(s.key);
        assertEquals(Arrays.asList("antcatImport", "taxonWorks", "nameUpdate", "set1Finish", "set2", "set3", "set4",
            "placeListTotals", "set5", "logSizeCheck", "statsPage", "apiCounts", "cacheReload", "countCheck", "end"), keys);
        Set<String> labels = new HashSet<>();
        for (int i = 0; i < DailyUpdate.STEPS.size(); i++) {
            DailyUpdate.Step s = DailyUpdate.STEPS.get(i);
            assertEquals(i, DailyUpdate.index(s.key));
            assertTrue("a label and an explanation", s.label.length() > 2 && s.what.length() > 5);
            assertFalse("pages say daily update, never nightly: " + s.label, (s.label + s.what).toLowerCase().contains("nightly"));
            assertTrue("labels differ", labels.add(s.label));
            assertTrue("fits daily_update_step.step", s.key.length() <= 32);
        }
        assertEquals(-1, DailyUpdate.index("nope"));
        assertNull(DailyUpdate.step("nope"));
    }

    /** A run at 06:10: AntCat import to set 2 done, set 3 running, the rest waiting. */
    private static List<Row> runningRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("antcatImport", t("05:00:00"), t("05:05:00"), "ok", null));
        rows.add(new Row("taxonWorks", t("05:05:00"), t("05:30:00"), "skipped", "skipped: archive unchanged since the last upload"));
        rows.add(new Row("nameUpdate", t("05:30:00"), t("05:41:00"), "ok", "name update: 3 specimens moved"));
        rows.add(new Row("set1Finish", t("05:41:00"), t("05:43:00"), "ok", null));
        rows.add(new Row("set2", t("05:43:00"), t("05:53:00"), "ok", null));
        rows.add(new Row("set3", t("05:53:00"), null, null, null));
        return rows;
    }

    private static Map<String, Long> usual() {
        Map<String, Long> u = new HashMap<>();
        u.put("antcatImport", 300L);
        u.put("set2", 300L);       // set 2 took 600 s: 2x, yellow
        u.put("set3", 540L);
        u.put("set4", 1440L);
        u.put("placeListTotals", 3600L);
        return u;
    }

    public void testViewWhileRunning() {
        long now = t("06:10:00").getTime();   // set 3 running for 17 minutes, usually 9
        List<StepView> v = DailyUpdate.view(runningRows(), usual(), true, now);
        assertEquals(DailyUpdate.STEPS.size(), v.size());
        assertEquals(DailyUpdate.DONE, v.get(0).state);
        assertEquals(Long.valueOf(300), v.get(0).seconds);
        assertFalse(v.get(0).slow);
        assertEquals(DailyUpdate.SKIPPED, v.get(1).state);
        assertEquals("skipped: archive unchanged since the last upload", v.get(1).message);
        assertTrue("set 2 took twice its usual time: yellow", v.get(4).slow);
        StepView set3 = v.get(5);
        assertEquals(DailyUpdate.RUNNING, set3.state);
        assertEquals(6, set3.number);
        assertEquals(Long.valueOf(17 * 60), set3.seconds);
        assertTrue(set3.slow);
        assertEquals(t("06:02:00"), set3.estimatedEnd);
        assertEquals(DailyUpdate.WAITING, v.get(6).state);
        assertEquals(DailyUpdate.WAITING, v.get(14).state);
        assertEquals(DailyUpdate.RUN_RUNNING, DailyUpdate.overallState(v, true));
        // set 3 is over its usual time (0 left), set 4 24 min, place list totals 60 min, the rest unknown
        assertEquals(Long.valueOf(84), DailyUpdate.minutesLeft(v));
        assertEquals("Step 6 of 15: Set 3: projects, museums, bioregions, groups, started 05:53 Pacific, usually 9 min;"
            + " next: Set 4: images and Not curated", DailyUpdate.hover(v));
        assertEquals("AntWeb is running its daily update (about 84 minutes left).", DailyUpdate.publicLine(84L));
        assertEquals("AntWeb is running its daily update (almost done).", DailyUpdate.publicLine(1L));
        assertTrue(DailyUpdate.publicLine(null).startsWith("AntWeb is running its daily update"));
    }

    public void testViewAfterTheRun() {
        List<Row> rows = runningRows();
        // Not active any more (server restarted during set 3): set 3 interrupted, the rest did not run.
        List<StepView> v = DailyUpdate.view(rows, usual(), false, t("09:00:00").getTime());
        assertEquals(DailyUpdate.FAILED, v.get(5).state);
        assertTrue(v.get(5).message.startsWith("interrupted"));
        assertNull("no elapsed time for an interrupted step", v.get(5).seconds);
        assertEquals(DailyUpdate.SKIPPED, v.get(6).state);
        assertEquals("did not run", v.get(6).message);
        assertEquals(DailyUpdate.RUN_FAILED, DailyUpdate.overallState(v, false));
        assertNull(DailyUpdate.hover(v));
        assertNull("nothing left to run", DailyUpdate.minutesLeft(v));

        // A finished run: every step a row, end ok.
        List<Row> done = new ArrayList<>();
        for (DailyUpdate.Step s : DailyUpdate.STEPS) done.add(new Row(s.key, t("05:00:00"), t("05:01:00"), "ok", null));
        assertEquals(DailyUpdate.RUN_OK, DailyUpdate.overallState(DailyUpdate.view(done, null, false, 0), false));
        // One step failed, the run went on to its end: failed.
        done.set(2, new Row("nameUpdate", t("05:00:00"), t("05:01:00"), "failed", "name update: FAILED"));
        List<StepView> failed = DailyUpdate.view(done, null, false, 0);
        assertEquals(DailyUpdate.FAILED, failed.get(2).state);
        assertEquals(DailyUpdate.RUN_FAILED, DailyUpdate.overallState(failed, false));
    }

    public void testResultOfALogLine() {
        assertEquals("ok", DailyUpdate.resultOf(null));
        assertEquals("ok", DailyUpdate.resultOf("uploaded 1234 records for group 1 (was 1200)"));
        assertEquals("skipped", DailyUpdate.resultOf("skipped: archive unchanged since the last upload"));
        assertEquals("skipped", DailyUpdate.resultOf("name update: off (no etc/name-update.properties)"));
        assertEquals("skipped", DailyUpdate.resultOf("name update: skipped, tonight's AntCat import did not succeed (x)"));
        assertEquals("failed", DailyUpdate.resultOf("name update: FAILED, nothing changed (java.sql.SQLException)"));
        assertEquals("failed", DailyUpdate.resultOf("FAILED: the upload stopped"));
        assertEquals("failed", DailyUpdate.resultOf("taxon parents: 3 changed; AntCat list and Global column NOT recounted (e)"));
        assertEquals("failed", DailyUpdate.resultOf("SKIPPED BY GUARD: archive has 10 records"));   // the guard needs a look
    }

    public void testDurationAndTimes() {
        assertEquals("", DailyUpdate.duration(null));
        assertEquals("40 s", DailyUpdate.duration(40L));
        assertEquals("12 min", DailyUpdate.duration(725L));
        assertEquals("1 h 05 min", DailyUpdate.duration(3900L));
        assertEquals("05:53", DailyUpdate.hhmm(t("05:53:10")));
        assertEquals("2026-11-07T05:00:00", DailyUpdate.iso(t("05:00:00")));
        assertNull(DailyUpdate.iso(null));
    }

    private static String state(String key) {
        for (StepView v : DailyUpdate.activeView()) if (v.step.key.equals(key)) return v.state;
        return null;
    }

    /**
     * The run in memory as the Scheduler marks it (no database here: every write fails on its own thread and is only
     * logged, which is the point: recording never breaks the run).
     */
    public void testRecordingARun() {
        DailyUpdate.resetForTest();
        Map<String, Long> usual = new HashMap<>();
        usual.put("set3", 60L);
        usual.put("set4", 60L);
        DailyUpdate.testMode(true, usual);
        DailyUpdate.start("set2");   // no run recorded (a set started by hand): nothing happens
        assertFalse(DailyUpdate.isActive());
        assertNull(DailyUpdate.activeHover());
        assertNull(DailyUpdate.activePublicLine());

        DailyUpdate.beginRun();
        assertTrue(DailyUpdate.isActive());
        assertNotNull(DailyUpdate.activeRunStart());
        DailyUpdate.start("antcatImport");
        assertEquals(DailyUpdate.RUNNING, state("antcatImport"));
        assertEquals(DailyUpdate.WAITING, state("taxonWorks"));
        assertTrue(DailyUpdate.activeHover().startsWith("Step 1 of 15: AntCat import, started "));
        assertTrue(DailyUpdate.activeHover().endsWith("; next: TaxonWorks upload"));
        assertTrue(DailyUpdate.activePublicLine().startsWith("AntWeb is running its daily update"));
        DailyUpdate.noteFailed("antcatImport", "the AntCat import did not succeed");
        DailyUpdate.noteResult("antcatImport", "all fine");   // a failure noted first stays
        DailyUpdate.start("taxonWorks");
        assertEquals(DailyUpdate.FAILED, state("antcatImport"));
        DailyUpdate.noteResult("taxonWorks", "skipped: archive unchanged since the last upload");
        DailyUpdate.noteFailed("nameUpdate", "not the running step: ignored");
        DailyUpdate.start("nameUpdate");
        assertEquals(DailyUpdate.SKIPPED, state("taxonWorks"));
        DailyUpdate.start("set4");
        DailyUpdate.skip("set5", "skipped on staging (memory)");
        assertEquals(DailyUpdate.DONE, state("set4"));
        assertEquals(DailyUpdate.SKIPPED, state("set5"));
        DailyUpdate.start(DailyUpdate.COUNT_CHECK);
        Date runStart = DailyUpdate.countCheckSubmitted();
        assertEquals(DailyUpdate.activeRunStart(), runStart);
        DailyUpdate.schedulerFinished(true);
        assertTrue("the run ends when its count check has", DailyUpdate.isActive());
        DailyUpdate.countCheckFinished(new Date(0), null);   // another run's count check
        assertTrue(DailyUpdate.isActive());
        DailyUpdate.countCheckFinished(runStart, null);
        assertFalse(DailyUpdate.isActive());

        assertEquals("one email for the failed AntCat import, none for the skipped step",
            Arrays.asList("antcatImport:failed"), DailyUpdate.testEmails());

        // A set failed: the running step fails and the run ends at once (the Scheduler sent its own email).
        DailyUpdate.testMode(true, usual);
        DailyUpdate.beginRun();
        DailyUpdate.start("set3");
        DailyUpdate.testWatch(100);    // 100 s, usually 60: not yet
        assertEquals(0, DailyUpdate.testEmails().size());
        DailyUpdate.testWatch(1000);   // over twice its usual time and 5 minutes over: one email
        DailyUpdate.testWatch(2000);
        assertEquals(Arrays.asList("set3:overrun"), DailyUpdate.testEmails());
        assertTrue("yellow on the page", DailyUpdate.activeView().get(5).slow);
        DailyUpdate.failRun("Scheduler FAILED in set 3 after 2 mins: x", true);
        assertFalse(DailyUpdate.isActive());
        assertEquals("no second email for the failure, nor for the overrun at its end",
            Arrays.asList("set3:overrun"), DailyUpdate.testEmails());
        DailyUpdate.schedulerFinished(false);   // the Scheduler's finally: nothing left to do

        // Count check not started (one was running): the run ends with the Scheduler.
        DailyUpdate.beginRun();
        DailyUpdate.start(DailyUpdate.COUNT_CHECK);
        DailyUpdate.noteSkipped(DailyUpdate.COUNT_CHECK, "a count check was already running");
        DailyUpdate.schedulerFinished(true);
        assertFalse(DailyUpdate.isActive());

        // The run stopped on an unexpected error.
        DailyUpdate.beginRun();
        DailyUpdate.start("set2");
        DailyUpdate.schedulerFinished(false);
        assertFalse(DailyUpdate.isActive());
        assertEquals("unexpected stop: an email", Arrays.asList("set3:overrun", "set2:failed"), DailyUpdate.testEmails());
        DailyUpdate.resetForTest();
        DailyUpdate.testMode(false, null);
    }

    /** The keys agreed with the production morning check (2026-11-07): exactly these. */
    public void testJsonShape() {
        long now = t("06:10:00").getTime();
        JsonObject running = DailyUpdate.runJson(t("05:00:00"), DailyUpdate.view(runningRows(), usual(), true, now), true);
        List<Row> done = new ArrayList<>();
        for (DailyUpdate.Step s : DailyUpdate.STEPS) done.add(new Row(s.key, t("05:00:00"), t("07:32:06"), "ok", null));
        JsonObject finished = DailyUpdate.runJson(DailyUpdate.pacific("2026-11-06T05:00:00"),
            DailyUpdate.view(done, usual(), false, now), false);
        String json = DailyUpdate.json(Arrays.asList(running, finished));
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        JsonArray runs = o.getAsJsonArray("runs");
        assertEquals(2, runs.size());

        JsonObject r0 = runs.get(0).getAsJsonObject();
        assertEquals(new HashSet<>(Arrays.asList("started", "ended", "state", "steps")), r0.keySet());
        assertEquals("2026-11-07T05:00:00", r0.get("started").getAsString());
        assertTrue("running: ended is null", r0.get("ended").isJsonNull());
        assertEquals("running", r0.get("state").getAsString());
        JsonArray steps = r0.getAsJsonArray("steps");
        assertEquals(DailyUpdate.STEPS.size(), steps.size());
        Set<String> stepKeys = new HashSet<>(Arrays.asList("name", "label", "state", "started", "ended", "usual_seconds", "message"));
        Set<String> states = new HashSet<>(Arrays.asList("done", "running", "waiting", "skipped", "failed"));
        for (int i = 0; i < steps.size(); i++) {
            JsonObject s = steps.get(i).getAsJsonObject();
            assertEquals(stepKeys, s.keySet());
            assertEquals(DailyUpdate.STEPS.get(i).key, s.get("name").getAsString());
            assertTrue(states.contains(s.get("state").getAsString()));
        }
        JsonObject set3 = steps.get(5).getAsJsonObject();
        assertEquals("running", set3.get("state").getAsString());
        assertEquals("2026-11-07T05:53:00", set3.get("started").getAsString());
        assertTrue(set3.get("ended").isJsonNull());
        assertEquals(540, set3.get("usual_seconds").getAsLong());
        assertTrue("no usual time: null", steps.get(1).getAsJsonObject().get("usual_seconds").isJsonNull());
        assertTrue(steps.get(6).getAsJsonObject().get("started").isJsonNull());

        JsonObject r1 = runs.get(1).getAsJsonObject();
        assertEquals("ok", r1.get("state").getAsString());
        assertEquals("2026-11-06T05:00:00", r1.get("started").getAsString());
        assertEquals("2026-11-07T07:32:06", r1.get("ended").getAsString());

        assertEquals(0, JsonParser.parseString(DailyUpdate.json(new ArrayList<>())).getAsJsonObject().getAsJsonArray("runs").size());
    }
}
