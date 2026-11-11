package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.home.GroupCountDb;
import org.calacademy.antweb.home.GroupDb;
import org.calacademy.antweb.util.CountCheck;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * The daily update writes every group's stored specimen count (ant_group.upload_specimens; GroupDb
 * .recountUploadSpecimens, set 3). Until 2026-11-11 only a group's own upload wrote it, so a correction, a deletion or
 * a clean-up of a group's specimens left it stale, and the count check reported "specimens (statistics.do by upload,
 * live) = ant_group.upload_specimens" as a real difference every day until the group's next upload (production
 * 2026-11-11: group 52, no upload for months, 21,102 specimens, stored 21,099; 2 such differences).
 *
 * On the numbers fixture (NumbersTestDb: group 1, CAS, 5 specimens, stored 5) with five more groups:
 *
 *   52  stored 3, 4 specimens          stale: becomes 4
 *   60  stored 7, no specimens         stale: becomes 0 (it was listed as a group with specimens only by the old number)
 *   61  no stored number, no specimens becomes 0
 *   62  stored 0, 1 specimen           becomes 1 (it has specimens, and is now listed as such)
 *   63  stored 2, 2 specimens          unchanged
 *
 * and a specimen of a group that has no row (999) and one of no group, which belong to no group's number.
 *
 * The tests: the stale numbers are corrected and nothing else of any group changes; the line for compute.log; the
 * count check's differences are gone; the recount runs in the caller's transaction (nothing committed, undone by a
 * rollback) and keeps no lock on the specimen table; a failure is said in the line and not thrown.
 * Runs in CI against MySQL 8.4 (.github/workflows/tests.yaml); ANTWEB_TEST_DB_URL overrides.
 */
public class GroupSpecimenCountDbTest extends TestCase {

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        connection = connect();
        fill(connection);
        exec(connection, "drop table if exists ant_group_gone");
        exec(connection, "insert into ant_group (id, name, upload_count, upload_specimens) values (52, 'Stale', 3, 3), (60, 'Emptied', 2, 7),"
            + " (61, 'New', 0, null), (62, 'Moved in', 0, 0), (63, 'Right', 1, 2)");
        StringBuilder rows = new StringBuilder();
        String[][] specimens = {{"g52a", "52"}, {"g52b", "52"}, {"g52c", "52"}, {"g52d", "52"}, {"g62a", "62"}, {"g63a", "63"}, {"g63b", "63"},
            {"nogrouprow", "999"}, {"nogroup", "null"}};
        for (String[] s : specimens) {
            rows.append(rows.length() == 0 ? "" : ", ").append("('").append(s[0]).append("', '").append(PA).append("', 'valid', 'Madagascar', 'formicidae', '")
                .append(MYRM).append("', ").append(s[1]).append(")");
        }
        exec(connection, "insert into specimen (code, taxon_name, status, country, family, subfamily, access_group) values " + rows);
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) {
            connection.setAutoCommit(true);
            exec(connection, "drop table if exists ant_group_gone");
            connection.close();
        }
    }

    /** Every column of every group's row, as text ("id|name|upload_count|upload_specimens"), by id. */
    private List<String> groupRows(Connection c) throws Exception {
        List<String> out = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement("select id, name, upload_count, upload_specimens from ant_group order by id"); ResultSet r = s.executeQuery()) {
            while (r.next()) out.add(r.getInt(1) + "|" + r.getString(2) + "|" + r.getInt(3) + "|" + r.getString(4));
        }
        return out;
    }

    private List<Integer> ids(String sql) throws Exception {
        List<Integer> out = new ArrayList<>();
        try (PreparedStatement s = connection.prepareStatement(sql); ResultSet r = s.executeQuery()) {
            while (r.next()) out.add(r.getInt(1));
        }
        return out;
    }

    static final List<String> BEFORE = Arrays.asList("1|CAS|1|5", "52|Stale|3|3", "60|Emptied|2|7", "61|New|0|null", "62|Moved in|0|0", "63|Right|1|2");
    static final List<String> AFTER = Arrays.asList("1|CAS|1|5", "52|Stale|3|4", "60|Emptied|2|0", "61|New|0|0", "62|Moved in|0|1", "63|Right|1|2");

    public void testStaleNumbersAreCorrectedAndTheOthersAreUnchanged() throws Exception {
        assertEquals(BEFORE, groupRows(connection));
        // the groups listed as having specimens (GroupMgr's upload groups, groups.do, the group counts): the number > 0
        String listed = "select id from ant_group where upload_specimens > 0 order by id";
        assertEquals(Arrays.asList(1, 52, 60, 63), ids(listed));

        String line = new GroupDb(connection).recountUploadSpecimens();
        assertTrue(line, line.startsWith(GroupDb.SPECIMENS_LOG + ": 6 groups recounted, 4 numbers changed"
            + " (group 52: 3 to 4; group 60: 7 to 0; group 61: no number to 0; group 62: 0 to 1) in "));
        assertFalse(line, line.contains("FAILED"));
        // each number is the group's specimens; the groups whose number was right, and every other column, are as they were
        assertEquals(AFTER, groupRows(connection));
        for (String row : groupRows(connection)) {
            String id = row.substring(0, row.indexOf('|'));
            assertEquals("group " + id, ids("select count(*) from specimen where access_group = " + id).get(0).toString(), row.substring(row.lastIndexOf('|') + 1));
        }
        // the rule of who is listed is the same (the number > 0): the group listed only by a stale number is not any
        // more, the group that has specimens is
        assertEquals(Arrays.asList(1, 52, 62, 63), ids(listed));

        // nothing to do the next day
        line = new GroupDb(connection).recountUploadSpecimens();
        assertTrue(line, line.startsWith(GroupDb.SPECIMENS_LOG + ": 6 groups recounted, 0 numbers changed in "));
        assertEquals(AFTER, groupRows(connection));

        // a clean-up removes a specimen of group 63 between two daily updates: corrected by the next one, alone
        exec(connection, "delete from specimen where code = 'g63b'");
        line = new GroupDb(connection).recountUploadSpecimens();
        assertTrue(line, line.startsWith(GroupDb.SPECIMENS_LOG + ": 6 groups recounted, 1 numbers changed (group 63: 2 to 1) in "));
        assertEquals(Arrays.asList("1|CAS|1|5", "52|Stale|3|4", "60|Emptied|2|0", "61|New|0|0", "62|Moved in|0|1", "63|Right|1|1"), groupRows(connection));
    }

    /** More changed groups than the line names: the first LOG_EXAMPLES, then "...". The number of changes is of all. */
    public void testTheLineNamesAFewGroupsAndCountsAll() throws Exception {
        StringBuilder rows = new StringBuilder();
        for (int id = 100; id < 112; id++) rows.append(id == 100 ? "" : ", ").append("(").append(id).append(", 'g").append(id).append("', 0, 9)");
        exec(connection, "insert into ant_group (id, name, upload_count, upload_specimens) values " + rows);
        String line = new GroupDb(connection).recountUploadSpecimens();
        assertTrue(line, line.startsWith(GroupDb.SPECIMENS_LOG + ": 18 groups recounted, 16 numbers changed (group 52: 3 to 4; "));
        assertTrue(line, line.contains("; group 105: 9 to 0; ...) in "));
        assertFalse("the eleventh changed group is not named: " + line, line.contains("group 106:"));
        assertEquals(0, ids("select count(*) from ant_group where id >= 100 and upload_specimens <> 0").get(0).intValue());
    }

    // ------------------------------------------------------------------ the count check

    private CountCheck.Result checkRun() throws Exception {
        CountCheck check = new CountCheck(connection);
        check.curatorNumbers = curatorNumbers();
        check.artistNumbers = artistNumbers();
        return check.run(new ArrayList<>(), new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"), true, true, true, null);
    }

    /** The real differences "specimens (statistics.do by upload, live) = ant_group.upload_specimens". */
    private static List<String> staleGroups(CountCheck.Result r) {
        List<String> out = new ArrayList<>();
        for (CountCheck.Mismatch m : r.mismatches) {
            if ("admin.statistics".equals(m.check) && m.isReal() && String.valueOf(m).contains("ant_group.upload_specimens")) out.add(String.valueOf(m));
        }
        return out;
    }

    /** As production on 2026-11-11: two groups with a stale number are two real differences; after the recount, none. */
    public void testTheCountChecksDifferencesAreGoneAfterTheRecount() throws Exception {
        List<String> stale = staleGroups(checkRun());
        assertEquals("groups 52 and 60: " + stale, 2, stale.size());
        assertTrue(stale.toString(), stale.toString().contains("group 52:") && stale.toString().contains("group 60:"));
        new GroupDb(connection).recountUploadSpecimens();
        stale = staleGroups(checkRun());
        assertEquals(stale.toString(), 0, stale.size());
        // the group counts (set 3, right after) list the groups by the corrected number
        assertEquals(Arrays.asList(1), ids(GroupCountDb.LISTED_GROUPS_SQL + " order by g.id"));
    }

    // ------------------------------------------------------------------ in the set's transaction

    /**
     * Set 3 runs in one transaction (autocommit off). The recount commits nothing: another connection sees the old
     * numbers until the set commits, and a rollback of the set undoes it. It keeps no lock on the specimen table: a
     * specimen can be added by another connection while the set's transaction is open (one UPDATE with the specimen
     * count as a subquery would keep shared locks on the specimen rows it read until the commit).
     */
    public void testTheRecountRunsInTheSetsTransactionAndLocksNoSpecimen() throws Exception {
        connection.setAutoCommit(false);
        try (Connection other = connect()) {
            String line = new GroupDb(connection).recountUploadSpecimens();
            assertTrue(line, line.contains("4 numbers changed"));
            assertFalse("autocommit as it was", connection.getAutoCommit());
            assertEquals("in the set's transaction", AFTER, groupRows(connection));
            assertEquals("not committed: another connection sees the old numbers", BEFORE, groupRows(other));
            exec(other, "set innodb_lock_wait_timeout = 2");
            exec(other, "insert into specimen (code, taxon_name, status, country, family, subfamily, access_group) values"
                + " ('added1', '" + PA + "', 'valid', 'Madagascar', 'formicidae', '" + MYRM + "', 52)");
            exec(other, "delete from specimen where code = 'g63a'");
            // the set fails later: its work is undone, the recount with it
            connection.rollback();
            assertEquals(BEFORE, groupRows(other));
            // the set succeeds: committed with it
            new GroupDb(connection).recountUploadSpecimens();
            connection.commit();
            assertEquals(Arrays.asList("1|CAS|1|5", "52|Stale|3|5", "60|Emptied|2|0", "61|New|0|0", "62|Moved in|0|1", "63|Right|1|1"), groupRows(other));
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /** A failure (here: no ant_group table) is said in the line and not thrown: the rest of the set goes on. */
    public void testAFailureIsSaidInTheLineAndNotThrown() throws Exception {
        exec(connection, "alter table ant_group rename to ant_group_gone");
        String line = new GroupDb(connection).recountUploadSpecimens();
        assertTrue(line, line.startsWith(GroupDb.SPECIMENS_LOG + ": FAILED ("));
        exec(connection, "alter table ant_group_gone rename to ant_group");
        assertEquals("no number was changed", BEFORE, groupRows(connection));
    }
}
