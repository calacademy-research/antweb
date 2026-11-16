package test.org.calacademy.antweb.home;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

import junit.framework.TestCase;

import org.calacademy.antweb.Museum;
import org.calacademy.antweb.home.PlaceViewTotalDb;

/**
 * Review of PR #341 (2026-11-16): PlaceViewTotalDb.recountPlace swallowed every error, also a deadlock inside an admin
 * job's transaction (UtilDataAction, autocommit off). MySQL rolls the whole transaction back on a deadlock, so the job
 * went on and committed as if its earlier work had been saved. Now:
 *  - in a caller's transaction, a deadlock is thrown (TransactionRolledBack), so the job fails and says so;
 *  - with autocommit on (a page's "Update statistics"), a deadlock is still a failed count, not thrown;
 *  - a lock wait timeout (1205), which rolls back only the statement, is a failed count in both cases.
 * No database: the connection is a stand-in whose every statement fails with the given error.
 */
public class PlaceViewTotalRollbackTest extends TestCase {

    static Connection failing(boolean autoCommit, SQLException error) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (p, m, a) -> {
            switch (m.getName()) {
                case "getAutoCommit": return autoCommit;
                case "isClosed": return false;
                case "close": return null;
                case "toString": return "failing connection";
                case "hashCode": return 1;
                case "equals": return p == a[0];
                default: throw error;
            }
        });
    }

    static Museum museum() {
        Museum m = new Museum();
        m.setCode("TESTM");
        m.setName("TESTM");
        return m;
    }

    static SQLException deadlock() {
        return new SQLException("Deadlock found when trying to get lock; try restarting transaction", "40001", 1213);
    }

    public void testDeadlockInCallersTransactionIsThrown() {
        PlaceViewTotalDb db = new PlaceViewTotalDb(failing(false, deadlock()));
        try {
            db.recountPlace(museum());
            fail("a deadlock inside the caller's transaction must be thrown");
        } catch (PlaceViewTotalDb.TransactionRolledBack e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Deadlock"));
            assertTrue(PlaceViewTotalDb.rolledBackTransaction(e));
        }
    }

    public void testDeadlockWithAutocommitIsAFailedCount() {
        PlaceViewTotalDb db = new PlaceViewTotalDb(failing(true, deadlock()));
        assertFalse(db.recountPlace(museum()));
        assertTrue(db.getLastFailure(), db.getLastFailure().contains("Deadlock"));
    }

    public void testLockWaitTimeoutIsAFailedCount() {
        SQLException timeout = new SQLException("Lock wait timeout exceeded; try restarting transaction", "HY000", 1205);
        PlaceViewTotalDb db = new PlaceViewTotalDb(failing(false, timeout));
        assertFalse(db.recountPlace(museum()));
        assertFalse(PlaceViewTotalDb.rolledBackTransaction(timeout));
    }

    public void testCauseChainIsRead() {
        assertTrue(PlaceViewTotalDb.rolledBackTransaction(new RuntimeException("wrapped", deadlock())));
        assertFalse(PlaceViewTotalDb.rolledBackTransaction(new RuntimeException("other")));
        assertFalse(PlaceViewTotalDb.rolledBackTransaction(null));
    }
}
