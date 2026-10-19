package test.org.calacademy.antweb.security;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TimeZone;

import javax.sql.DataSource;

import junit.framework.TestCase;

/**
 * Dates between AntWeb and MySQL are in Pacific time (2026-10-19).
 *
 * Runs in CI (.github/workflows/tests.yaml) in the antweb image, against a MySQL 8.4 container
 * set up as in docker-compose.yml (TZ America/Los_Angeles, docker/mysql/mysql.cnf), reachable
 * as host "mysql". The connection pools are built from WEB-INF/struts-configDbAnt.xml (path in
 * ANTWEB_DB_XML) exactly as the site builds them (AntwebDataSources).
 *
 * For each pool: a date written by MySQL (now(), CURRENT_TIMESTAMP) and a date written from
 * Java (setTimestamp) are read back as the same moment, and are stored as Pacific wall-clock
 * time. With the former serverTimezone=UTC, reads were 7 hours early (8 in winter) and Java
 * writes were stored 7 hours late.
 */
public class DbTimeZoneTest extends TestCase {

    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    private static final long SLACK_MS = 120_000L;
    private static final DateTimeFormatter SQL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @SuppressWarnings("unchecked")
    private static Map<String, DataSource> pools() throws Exception {
        Class<?> c = Class.forName("org.calacademy.antweb.util.AntwebDataSources");
        Method load = c.getDeclaredMethod("load", String.class);
        load.setAccessible(true);
        String path = System.getenv().getOrDefault("ANTWEB_DB_XML", "/w/struts-configDbAnt.xml");
        Map<String, DataSource> m = (Map<String, DataSource>) load.invoke(null, path);
        assertNotNull(m);
        return m;
    }

    private static void assertNear(String what, long expectedMs, long actualMs) {
        long diff = actualMs - expectedMs;
        assertTrue(what + " is off by " + (diff / 60000.0) + " minutes", Math.abs(diff) < SLACK_MS);
    }

    private static void assertWallClock(String what, String stored) {
        LocalDateTime s = LocalDateTime.parse(stored, SQL);
        long diff = Duration.between(LocalDateTime.now(PACIFIC), s).toMillis();
        assertTrue(what + " stored as " + stored + ", " + (diff / 60000.0) + " minutes from Pacific time now",
            Math.abs(diff) < SLACK_MS);
    }

    public void testJvmIsPacific() {
        assertEquals("the antweb image runs in Pacific time (Dockerfile TZ)", PACIFIC.getId(), TimeZone.getDefault().getID());
    }

    public void testMysqlIsPacific() throws Exception {
        try (Connection c = pools().get("conPool").getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("select date_format(now(), '%Y-%m-%d %H:%i:%s'), @@system_time_zone, @@time_zone")) {
            assertTrue(rs.next());
            assertWallClock("MySQL now() (system_time_zone " + rs.getString(2) + ", time_zone " + rs.getString(3) + ")", rs.getString(1));
        }
    }

    /** A DATE (e.g. specimen.datecollectedstart) read with getTimestamp or getDate is the same day. */
    public void testDatesKeepTheirDay() throws Exception {
        for (Map.Entry<String, DataSource> e : pools().entrySet()) {
            try (Connection c = e.getValue().getConnection(); Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("select cast('2020-05-01' as date), cast('2020-01-15' as date)")) {
                assertTrue(rs.next());
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd");
                assertEquals(e.getKey() + " getTimestamp", "2020-05-01", f.format(rs.getTimestamp(1)));
                assertEquals(e.getKey() + " getDate", "2020-05-01", rs.getDate(1).toString());
                assertEquals(e.getKey() + " getTimestamp (winter)", "2020-01-15", f.format(rs.getTimestamp(2)));
            }
        }
    }

    public void testEveryPoolReadsAndWritesPacificTime() throws Exception {
        Map<String, DataSource> pools = pools();
        assertTrue(pools.keySet().toString(), pools.keySet().containsAll(java.util.List.of("conPool", "mediumConPool", "longConPool")));
        for (Map.Entry<String, DataSource> e : pools.entrySet()) {
            String pool = e.getKey();
            try (Connection c = e.getValue().getConnection()) {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("create table if not exists tz_check (id int primary key, dt datetime null,"
                        + " ts timestamp null default null, dflt timestamp default current_timestamp)");
                    st.executeUpdate("delete from tz_check");
                    st.executeUpdate("insert into tz_check (id, dt, ts) values (1, now(), now())");
                }
                long javaNow = System.currentTimeMillis();
                try (PreparedStatement ps = c.prepareStatement("insert into tz_check (id, dt, ts) values (2, ?, ?)")) {
                    ps.setTimestamp(1, new Timestamp(javaNow));
                    ps.setTimestamp(2, new Timestamp(javaNow));
                    ps.executeUpdate();
                }
                int rows = 0;
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("select id, dt, ts, dflt, date_format(dt, '%Y-%m-%d %H:%i:%s'),"
                         + " date_format(ts, '%Y-%m-%d %H:%i:%s'), date_format(dflt, '%Y-%m-%d %H:%i:%s') from tz_check order by id")) {
                    while (rs.next()) {
                        rows++;
                        String row = pool + " row " + rs.getInt(1) + (rs.getInt(1) == 1 ? " (MySQL now())" : " (Java setTimestamp)");
                        long now = System.currentTimeMillis();
                        assertNear(row + " datetime read", now, rs.getTimestamp(2).getTime());
                        assertNear(row + " timestamp read", now, rs.getTimestamp(3).getTime());
                        assertNear(row + " default CURRENT_TIMESTAMP read", now, rs.getTimestamp(4).getTime());
                        assertWallClock(row + " datetime", rs.getString(5));
                        assertWallClock(row + " timestamp", rs.getString(6));
                        assertWallClock(row + " default CURRENT_TIMESTAMP", rs.getString(7));
                    }
                }
                assertEquals(pool, 2, rows);
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("drop table tz_check");
                }
            }
        }
    }
}
