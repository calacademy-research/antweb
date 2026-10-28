package test.org.calacademy.antweb.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.home.GroupCountDb;
import org.calacademy.antweb.home.ProjTaxonDb;
import org.calacademy.antweb.home.TaxonDb;
import org.calacademy.antweb.util.BackgroundJobDb;
import org.calacademy.antweb.util.CountCheck;
import org.calacademy.antweb.util.CountInvariants;
import org.calacademy.antweb.util.Scheduler;
import org.calacademy.antweb.util.UtilDataAction;

/**
 * Nightly run and count check fixes of 2026-10-28, without a database (JDBC stand-ins made with java.lang.reflect.Proxy):
 *
 *   recountGlobal          commits set 2's clean-up before the Global recount, so a failed recount does not undo it
 *   claimNotDuringNightly  a background data job does not start while the nightly run is working
 *   hasNoChildren          a failed query is not "no children" (the caller deletes the genus row then)
 *   isOnAnyList            a name still on another list is never deleted outright; an error means "on a list"
 *   getTaxonName           a synonym row read last does not replace the valid name
 *   GroupCountDb           a failure in the group recount rolls back to its savepoint
 *   cutUtf8                a job result fits the TEXT column (bytes, not characters)
 *   count check            no compute.log is not a reason for "pending"; a run still working is seen as the window
 *   Scheduler              a set number other than 0 to 5 is refused
 */
public class NightlyTriageThTest extends TestCase {

    /** Calls made on the stand-in connection and its statements, in order. */
    private final List<String> calls = new ArrayList<>();

    /** Rows of the next query (columns by name), or null: executeQuery throws. */
    private List<Object[]> rows;
    private String[] columns;
    private boolean prepareFails;

    private Connection connection() {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class}, (p, m, a) -> {
            calls.add(m.getName() + (a != null && a.length == 1 && a[0] instanceof Savepoint ? "(savepoint)" : ""));
            switch (m.getName()) {
                case "createStatement": return statement(Statement.class);
                case "prepareStatement":
                    if (prepareFails) throw new SQLException("test: prepare fails");
                    return statement(PreparedStatement.class);
                case "getAutoCommit": return false;
                case "setSavepoint": return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Savepoint.class}, (p2, m2, a2) -> null);
                case "isClosed": return false;
                case "toString": return "test connection";
                case "hashCode": return 1;
                case "equals": return p == a[0];
                default: return null;
            }
        });
    }

    private Object statement(Class<?> type) {
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {type}, (p, m, a) -> {
            switch (m.getName()) {
                case "executeQuery":
                    calls.add("executeQuery");
                    if (rows == null) throw new SQLException("test: query fails");
                    return resultSet();
                case "executeUpdate": calls.add("executeUpdate"); return 0;
                case "toString": return "test statement";
                case "hashCode": return 2;
                case "equals": return p == a[0];
                default: return null;
            }
        });
    }

    private ResultSet resultSet() {
        final int[] i = {-1};
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ResultSet.class}, (p, m, a) -> {
            switch (m.getName()) {
                case "next": return ++i[0] < rows.size();
                case "getString": return String.valueOf(value(rows.get(i[0]), a[0]));
                case "getLong": return ((Number) value(rows.get(i[0]), a[0])).longValue();
                case "getInt": return ((Number) value(rows.get(i[0]), a[0])).intValue();
                case "toString": return "test result set";
                case "hashCode": return 3;
                case "equals": return p == a[0];
                default: return null;
            }
        });
    }

    private Object value(Object[] row, Object column) {
        if (column instanceof Integer) return row[(Integer) column - 1];
        return row[Arrays.asList(columns).indexOf(column)];
    }

    private static Object call(Object target, Class<?> type, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = type.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    // ---------------------------------------------------------------- the nightly run

    public void testGlobalRecountKeepsTheCleanUp() throws Exception {
        prepareFails = true;   // the recount fails at its first statement
        String line = (String) call(null, UtilDataAction.class, "recountGlobal", new Class<?>[] {Connection.class}, connection());
        assertTrue(line, line.contains("NOT recounted"));
        int commit = calls.indexOf("commit");
        int rollback = calls.indexOf("rollback");
        assertTrue("commit before the recount: " + calls, commit >= 0 && rollback > commit);
        assertEquals("the first call commits the clean-up: " + calls, "commit", calls.get(0));
    }

    public void testBackgroundJobWaitsForTheNightlyRun() throws Exception {
        Field running = Scheduler.class.getDeclaredField("s_running");
        running.setAccessible(true);
        UtilDataAction.setInComputeProcess(null);
        try {
            running.set(null, true);
            assertFalse(UtilDataAction.claimNotDuringNightly("populateMuseum"));
            assertNull("the flag is not taken", UtilDataAction.getIsInComputeProcess());
            running.set(null, false);
            assertTrue(UtilDataAction.claimNotDuringNightly("populateMuseum"));
            assertEquals("populateMuseum", UtilDataAction.getIsInComputeProcess());
            assertFalse("another job waits", UtilDataAction.claimNotDuringNightly("populateBioregion"));
        } finally {
            running.set(null, false);
            UtilDataAction.setInComputeProcess(null);
        }
    }

    public void testSchedulerRefusesAnUnknownSet() {
        assertTrue(new Scheduler().doAction("run", 9).startsWith("No set 9"));
        assertTrue(new Scheduler().doAction("run", -1).startsWith("No set -1"));
    }

    // ---------------------------------------------------------------- list maintenance

    public void testFailedChildQueryIsNotNoChildren() throws Exception {
        rows = null;   // the query fails
        Object noChildren = call(new ProjTaxonDb(connection()), ProjTaxonDb.class, "hasNoChildren",
            new Class<?>[] {String.class, String.class}, "myrmicinaepheidole", "allantwebants");
        assertEquals(Boolean.FALSE, noChildren);
        columns = new String[] {"taxon_name", "project_name"};
        rows = new ArrayList<>();
        assertEquals(Boolean.TRUE, call(new ProjTaxonDb(connection()), ProjTaxonDb.class, "hasNoChildren",
            new Class<?>[] {String.class, String.class}, "myrmicinaepheidole", "allantwebants"));
    }

    public void testNameOnAnotherListIsNotDeleted() {
        columns = new String[] {"n"};
        rows = Collections.singletonList(new Object[] {2L});
        assertTrue(new TaxonDb(connection()).isOnAnyList("myrmicinaepheidole mg01"));
        rows = Collections.singletonList(new Object[] {0L});
        assertFalse(new TaxonDb(connection()).isOnAnyList("myrmicinaepheidole mg01"));
        rows = null;   // an error: never delete
        assertTrue(new TaxonDb(connection()).isOnAnyList("myrmicinaepheidole mg01"));
        assertTrue(TaxonDb.ON_OTHER_LIST_SQL.contains("geolocale_taxon") && TaxonDb.ON_OTHER_LIST_SQL.contains("bioregion_taxon")
            && TaxonDb.ON_OTHER_LIST_SQL.contains("museum_taxon") && TaxonDb.ON_OTHER_LIST_SQL.contains("project_name <> 'allantwebants'"));
    }

    public void testSynonymReadLastDoesNotReplaceTheValidName() throws Exception {
        columns = new String[] {"taxon_name", "status"};
        Class<?>[] types = {String.class, String.class, String.class, String.class, String.class, String.class};
        rows = Arrays.asList(new Object[] {"ectatomminaeholcoponera", "valid"}, new Object[] {"dorylinaeholcoponera", "synonym"});
        assertEquals("ectatomminaeholcoponera", call(new TaxonDb(connection()), TaxonDb.class, "getTaxonName", types,
            null, null, "holcoponera", null, null, "genus"));
        rows = Collections.singletonList(new Object[] {"dorylinaeholcoponera", "synonym"});
        assertEquals("only a synonym: as before", "dorylinaeholcoponera", call(new TaxonDb(connection()), TaxonDb.class, "getTaxonName", types,
            null, null, "holcoponera", null, null, "genus"));
    }

    public void testGroupRecountRollsBackToItsSavepoint() {
        GroupCountDb.forgetTable();
        prepareFails = true;
        String message = new GroupCountDb(connection()).recountAll();
        GroupCountDb.forgetTable();
        assertTrue(message, message.startsWith("Group counts not written"));
        assertTrue(calls.toString(), calls.contains("setSavepoint") && calls.contains("rollback(savepoint)"));
        assertTrue("savepoint after the table check: " + calls, calls.indexOf("setSavepoint") > calls.indexOf("executeUpdate"));
    }

    // ---------------------------------------------------------------- background jobs

    public void testJobResultIsCutByBytes() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 30000; i++) b.append("é");   // 2 bytes each: 60,000 bytes, fits
        assertEquals(b.toString(), BackgroundJobDb.cutUtf8(b.toString(), 60000));
        b.append("€€");                               // 3 bytes each: over
        String cut = BackgroundJobDb.cutUtf8(b.toString(), 60000);
        assertTrue(cut.endsWith("..."));
        assertTrue(cut.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 60000);
        StringBuilder e = new StringBuilder();
        for (int i = 0; i < 20000; i++) e.append("🐜");   // an ant, 4 bytes, two chars
        String cut2 = BackgroundJobDb.cutUtf8(e.toString(), 60000);
        assertTrue(cut2.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 60000);
        assertFalse("no half character", Character.isHighSurrogate(cut2.charAt(cut2.length() - 4)));
        assertNull(BackgroundJobDb.cutUtf8(null, 10));
    }

    // ---------------------------------------------------------------- count check

    public void testNoComputeLogIsNotPending() {
        List<String> none = new ArrayList<>();
        assertTrue(CountInvariants.rulesCounted(none));
        assertTrue(CountCheck.regionRuleCounted(none));
        assertTrue(CountCheck.antImagesRuleCounted(none));
        assertFalse("a log without the line still says pending", CountInvariants.rulesCounted(Arrays.asList("2026-10-28 05:00:00 Begin run")));
    }

    public void testRunStillWorkingIsSeen() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 28, 9, 0);
        List<String> log = new ArrayList<>(Arrays.asList(
            "2026-10-27 05:00:00 Begin run", "2026-10-27 07:50:00 End run", "2026-10-28 05:00:00 Begin run",
            "2026-10-28 06:00:00   name update: FAILED, nothing changed (x)"));
        assertTrue(CountCheck.nightlyInProgress(log, now));
        log.add("2026-10-28 09:10:00 End run");
        assertFalse(CountCheck.nightlyInProgress(log, now));
        log.add("2026-10-28 09:20:00 Begin run");
        log.add("2026-10-28 09:30:00 Scheduler FAILED in set 2 after 10 mins: x");
        assertFalse("a failed run is not working", CountCheck.nightlyInProgress(log, LocalDateTime.of(2026, 10, 28, 10, 0)));
        List<String> stopped = Arrays.asList("2026-10-26 05:00:00 Begin run");
        assertFalse("a run stopped by a restart days ago", CountCheck.nightlyInProgress(stopped, now));
        assertFalse(CountCheck.nightlyInProgress(new ArrayList<>(), now));
    }
}
