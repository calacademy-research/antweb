package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.calacademy.antweb.home.MuseumDb;

/**
 * specimen.museum of every specimen in one pass (MuseumDb.populateAllSpecimenMuseums, daily update stage two,
 * 2026-11-18) leaves exactly what the old passes left: for each museum in turn, empty the specimens that carry its code,
 * then give its code to every specimen whose ownedBy names it as a whole word (MuseumDb.specimenMuseumDml).
 * The same specimens are loaded twice, one copy goes through the old passes and one through the new pass, and every
 * row is compared byte for byte. Cases: CAS inside CASC, MCZ inside MCZC, two codes in one text (the later museum takes
 * it), case and accent variants, a code no museum has (left alone), a code with no matching text (emptied), no
 * ownedBy, a lower-case code that matches (written in the museum's case). Also on a latin1 column.
 *
 * Runs in CI against MySQL 8.4 (host "mysql"); ANTWEB_TEST_DB_URL overrides.
 */
public class MuseumOwnerDbTest extends TestCase {

    private Connection connection;

    static final List<String> CODES = Arrays.asList("AFRC", "CAS", "CASC", "MCZC", "MHNG", "UCDC");

    /** code, ownedBy, museum before */
    static final String[][] ROWS = {
        {"s1", "CASC, San Francisco, CA, USA", null},
        {"s2", "CAS", null},
        {"s3", "casc", null},
        {"s4", "CASCADE collection", null},                 // a longer word: no museum
        {"s5", "MCZ, Harvard", null},                       // MCZ is no museum here: nothing
        {"s6", "MCZC", "CASC"},                             // was CASC, now MCZC
        {"s7", "UCDC / CASC", null},                        // two codes: the later museum in order (UCDC) takes it
        {"s8", "CASC / UCDC", "CASC"},
        {"s9", "Muséum d'histoire naturelle, MHNG, Genève", null},
        {"s10", "Museum d'histoire naturelle, MHNG, Geneve", "MHNG"},
        {"s11", "private collection", "CASC"},              // a code but no matching text: emptied
        {"s12", "private collection", "ZZZZ"},              // a code no museum has: left alone
        {"s13", null, "AFRC"},                              // no ownedBy: emptied
        {"s14", null, null},
        {"s15", "AFRC ", "afrc"},                           // trailing space; lower-case code written as AFRC
        {"s16", "afrc", "AFRC"},
        {"s17", "CAS-CASC", null},                          // both, joined by a hyphen: CASC (later) takes it
        {"s18", "[CASC]", "UCDC"},
    };

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        connection = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        connection.setAutoCommit(true);
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

    private void load(String charset) throws SQLException {
        exec("drop table if exists specimen");
        exec("create table specimen (code varchar(128) primary key, ownedBy varchar(512), museum varchar(64))"
            + (charset == null ? "" : " default charset=" + charset));
        try (PreparedStatement ps = connection.prepareStatement("insert into specimen (code, ownedBy, museum) values (?, ?, ?)")) {
            for (String[] r : ROWS) {
                ps.setString(1, r[0]);
                ps.setString(2, r[1]);
                ps.setString(3, r[2]);
                ps.executeUpdate();
            }
        }
    }

    /** The old way: for each museum, empty its code, then set it where ownedBy matches (MuseumDb.populateSpecimenMuseum). */
    private void oldPasses() throws SQLException {
        for (String code : CODES) {
            try (PreparedStatement ps = connection.prepareStatement("update specimen set museum = null where museum = ?")) {
                ps.setString(1, code);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(MuseumDb.specimenMuseumDml())) {
                ps.setString(1, code);
                ps.setString(2, MuseumDb.ownedByPattern(code));
                ps.executeUpdate();
            }
        }
    }

    private Map<String, String> museums() throws SQLException {
        Map<String, String> out = new LinkedHashMap<>();
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("select code, hex(museum) from specimen order by code")) {
            while (rs.next()) out.put(rs.getString(1), rs.getString(2));
        }
        return out;
    }

    private String museumOf(String code) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("select museum from specimen where code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "absent";
            }
        }
    }

    private void sameAsTheOldPasses(String charset) throws SQLException {
        load(charset);
        oldPasses();
        Map<String, String> before = museums();
        load(charset);
        MuseumDb.populateAllSpecimenMuseums(connection, CODES);
        assertEquals(before, museums());
    }

    public void testSameAsTheOldPasses() throws SQLException {
        sameAsTheOldPasses(null);
        // a few rows spelled out, so a change of both ways together is seen too
        assertEquals("CASC", museumOf("s1"));
        assertEquals("CAS", museumOf("s2"));
        assertEquals("CASC", museumOf("s3"));
        assertNull(museumOf("s4"));
        assertNull(museumOf("s5"));
        assertEquals("MCZC", museumOf("s6"));
        assertEquals("UCDC", museumOf("s7"));
        assertEquals("UCDC", museumOf("s8"));
        assertEquals("MHNG", museumOf("s9"));
        assertNull(museumOf("s11"));
        assertEquals("ZZZZ", museumOf("s12"));
        assertNull(museumOf("s13"));
        assertEquals("AFRC", museumOf("s15"));
        assertEquals("CASC", museumOf("s17"));
        assertEquals("CASC", museumOf("s18"));
    }

    public void testSameOnALatin1Column() throws SQLException {
        sameAsTheOldPasses("latin1");
    }

    public void testSecondRunChangesNothing() throws SQLException {
        load(null);
        assertTrue(MuseumDb.populateAllSpecimenMuseums(connection, CODES) > 0);
        assertEquals(0, MuseumDb.populateAllSpecimenMuseums(connection, CODES));
        assertEquals(0, MuseumDb.populateAllSpecimenMuseums(connection, Arrays.asList()));
    }

    /** The two numbers of the compute.log line (2026-11-19): a reloaded specimen given its museum, a changed owner. */
    public void testGivenAndChangedCounted() throws SQLException {
        load(null);
        MuseumDb.populateAllSpecimenMuseumCounts(connection, CODES);
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("update specimen set museum = null where code = 's1'");          // as reloaded by an upload
            st.executeUpdate("update specimen set ownedBy = 'UCDC' where code = 's6'");       // owner changed: MCZC to UCDC
        }
        int[] counts = MuseumDb.populateAllSpecimenMuseumCounts(connection, CODES);
        assertEquals(1, counts[0]);
        assertEquals(1, counts[1]);
        assertEquals("CASC", museumOf("s1"));
        assertEquals("UCDC", museumOf("s6"));
        int[] again = MuseumDb.populateAllSpecimenMuseumCounts(connection, CODES);
        assertEquals(0, again[0] + again[1]);
    }
}
