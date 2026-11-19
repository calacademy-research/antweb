package test.org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.calacademy.antweb.util.NamedQuery;
import org.calacademy.antweb.util.Queries;

/**
 * The Query Manager's report of specimen codes that may name one specimen twice (possibleDuplicateSpecimenCodes,
 * 2026-11-18), on MySQL 8.4 (CI host "mysql"; ANTWEB_TEST_DB_URL overrides): case, punctuation, a short suffix after a
 * long number and leading zeros are ignored; short numbered codes of one series (jtl-1, jtl-2) are not one specimen.
 */
public class PossibleDuplicateCodesDbTest extends TestCase {

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        connection = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        connection.setAutoCommit(true);
        exec("drop table if exists specimen");
        exec("create table specimen (code varchar(128) primary key, access_group int, taxon_name varchar(128),"
            + " collectioncode varchar(128), image_count int)");
        String[][] rows = {
            {"anic32-066597", "40", "formicinaemelophorus argus", "c1", "0"},
            {"anic32-066597-1", "1", "formicinaemelophorus argus", "c1", "4"},
            {"CASENT0012345", "1", "myrmicinaepheidole a", "c2", "3"},
            {"casent12345", "7", "myrmicinaepheidole a", "c2", "0"},
            {"jtl-1", "2", "x", "c3", "0"},
            {"jtl-2", "2", "x", "c3", "0"},
            {"ANTWEB1008986", "1", "y", "c4", "2"},
            {"ANTWEB1008989", "1", "y", "c4", "2"},     // another number: not found by this report
            {"FMNH-INS 0000123456", "5", "z", "c5", "0"},
            {"fmnhins123456", "5", "z", "c5", "0"},
        };
        try (PreparedStatement ps = connection.prepareStatement("insert into specimen values (?, ?, ?, ?, ?)")) {
            for (String[] r : rows) {
                for (int i = 0; i < 5; i++) ps.setString(i + 1, r[i]);
                ps.executeUpdate();
            }
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) {
            exec("drop table if exists specimen");
            connection.close();
        }
    }

    private void exec(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    public void testSetsOfCodes() throws SQLException {
        NamedQuery q = null;
        for (NamedQuery n : Queries.getNamedQueries()) if ("possibleDuplicateSpecimenCodes".equals(n.getName())) q = n;
        assertNotNull(q);
        Map<String, String> sets = new LinkedHashMap<>();
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(q.getQuery())) {
            while (rs.next()) sets.put(rs.getString(1), rs.getString(2) + " | " + rs.getString(4) + " | " + rs.getInt(7));
        }
        assertEquals(sets.toString(), 3, sets.size());
        assertEquals("ANIC32-066597 first (images), two groups", "anic32-066597, anic32-066597-1 | 1, 40 | 4", sets.get("anic32066597"));
        assertEquals("CASENT0012345, casent12345 | 1, 7 | 3", sets.get("casent12345"));
        assertEquals("FMNH-INS 0000123456, fmnhins123456 | 5 | 0", sets.get("fmnhins123456"));
        assertEquals("sets with images first", "anic32066597", sets.keySet().iterator().next());
    }
}
