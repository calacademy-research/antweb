package test.org.calacademy.antweb.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import junit.framework.TestCase;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import org.calacademy.antweb.util.DataExtract;

/**
 * The data extract engine (DataExtract, dataExtract.do; 2026-11-10) against MySQL 8.4, with the cases of the
 * command-line tool's test (tests/unit/test_specimen_extract.py): the extract of three countries and the island
 * filed under one of them is complete and verified; tabs, line breaks, backslashes, quotes, commas and accents
 * come out as they are in the database and NULL as an empty field; numbers and dates read as the database prints
 * them (37.9, 0, 1.500, a zero date); specimens without coordinates (none, or 0/0) and with a name that has no taxon row
 * stay in the file, marked; a specimen of another country is not in it; a database count that differs from the
 * rows written is reported (not verified, the difference named); an unknown or outdated country name stops before
 * any file is written; islands can be left out; names are spelled as the database spells them; the connection
 * comes back as it was; 12,000 rows are streamed with progress reports.
 *
 * Makes its own small tables (specimen, taxon, ant_group, geolocale, bioregion, bioregion_taxon) and drops them
 * at the end: last in the list of the database tests. Runs in CI against MySQL 8.4 (host "mysql");
 * ANTWEB_TEST_DB_URL overrides.
 */
public class DataExtractDbTest extends TestCase {

    private static final String[] TABLES = {"specimen", "taxon", "ant_group", "geolocale", "bioregion", "bioregion_taxon"};

    private Connection connection;
    private File dir;

    private static String url() {
        return System.getenv().getOrDefault("ANTWEB_TEST_DB_URL", "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
    }

    private static Connection connect(String url) throws SQLException {
        Connection c = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        c.setAutoCommit(true);
        return c;
    }

    private void exec(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) { s.executeUpdate(sql); }
    }

    @Override
    protected void setUp() throws Exception {
        connection = connect(url());
        dir = Files.createTempDirectory("extract-db-test").toFile();
        for (String t : TABLES) exec("drop table if exists " + t);
        exec("create table specimen (code varchar(40) primary key, taxon_name varchar(128), family varchar(40), subfamily varchar(64),"
            + " genus varchar(64), country varchar(64), island_country varchar(64), bioregion varchar(64), localityname text,"
            + " decimal_latitude float, decimal_longitude float, elevation int, flag varchar(10), access_group int, is_introduced tinyint(1),"
            + " datecollectedstart varchar(20), created timestamp null, last_modified datetime null, day_entered date null,"
            + " weight decimal(8, 3), other longtext) default charset=utf8mb4");
        exec("create table taxon (taxon_name varchar(128) primary key, status varchar(40), fossil tinyint default 0, taxarank varchar(20),"
            + " current_valid_name varchar(128), author_date varchar(128), subfamily varchar(64), genus varchar(64), species varchar(64),"
            + " subspecies varchar(64)) default charset=utf8mb4");
        exec("create table ant_group (id int primary key, name varchar(128)) default charset=utf8mb4");
        exec("create table geolocale (id int primary key, name varchar(128), georank varchar(20), is_valid tinyint, valid_name varchar(128),"
            + " is_island tinyint default 0, parent varchar(128), country varchar(250), bioregion varchar(64), alt_bioregion varchar(64))"
            + " default charset=utf8mb4");
        exec("create table bioregion (name varchar(64) primary key) default charset=utf8mb4");
        exec("create table bioregion_taxon (bioregion_name varchar(64), taxon_name varchar(128), source varchar(30), is_endemic tinyint,"
            + " is_introduced tinyint, primary key (bioregion_name, taxon_name)) default charset=utf8mb4");

        exec("insert into ant_group values (1, 'CAS'), (2, 'Utah, \"Longino\" lab')");
        exec("insert into bioregion values ('Nearctic'), ('Oceania'), ('Neotropical')");
        exec("insert into geolocale values"
            + " (1, 'Canada', 'country', 1, null, 0, 'Northern America', null, 'Nearctic', null),"
            + " (2, 'Hawaii', 'country', 1, null, 1, 'United States', 'United States', 'Oceania', null),"
            + " (3, 'Mexico', 'country', 1, null, 0, 'Central America', null, 'Neotropical', 'Nearctic'),"
            + " (4, 'United States', 'country', 1, null, 0, 'Northern America', null, 'Nearctic', 'Neotropical'),"
            + " (5, 'USA', 'country', 0, 'United States', 0, null, null, null, null),"
            + " (6, 'Brazil', 'country', 1, null, 0, 'South America', null, 'Neotropical', null),"
            + " (7, 'Cote d''Ivoire', 'country', 1, null, 0, 'Western Africa', null, 'Afrotropical', null),"
            + " (8, 'California', 'adm1', 1, null, 0, 'United States', null, 'Nearctic', null),"
            + " (9, 'Galapagos Islands', 'country', 1, null, 1, 'Ecuador', 'Ecuador', 'Neotropical', null)");
        exec("insert into taxon values"
            + " ('myrmicinaestenamma diecki', 'valid', 0, 'species', null, 'Emery, 1895', 'myrmicinae', 'stenamma', 'diecki', null),"
            + " ('myrmicinaestenamma ca01', 'morphotaxon', 0, 'species', null, null, 'myrmicinae', 'stenamma', 'ca01', null),"
            + " ('myrmicinaeveromessor andrei', 'valid', 0, 'species', null, '(Mayr, 1886)', 'myrmicinae', 'veromessor', 'andrei', null),"
            + " ('myrmicinaestenamma lit', 'valid', 0, 'species', null, 'Smith, 1957', 'myrmicinae', 'stenamma', 'lit', null),"
            + " ('myrmicinaestenamma', 'valid', 0, 'genus', null, 'Westwood, 1839', 'myrmicinae', 'stenamma', null, null)");
        exec("insert into bioregion_taxon values"
            + " ('Nearctic', 'myrmicinaestenamma diecki', 'specimen', 0, 0),"
            + " ('Nearctic', 'myrmicinaestenamma lit', 'antcat', 0, 0),"
            + " ('Nearctic', 'myrmicinaestenamma', 'specimen', 0, 0),"            // a genus row: not on the species list
            + " ('Oceania', 'myrmicinaestenamma diecki', 'specimen', 0, 0)");
        // casent1: a tab, a line break, a backslash, a quote and a comma in one field; NULL flag and island.
        exec("insert into specimen values ('casent1', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', 'United States', null,"
            + " 'Nearctic', 'Mt. Tam\\tridge\\nline2 \\\\ back, \"quoted\"', 37.9, -122.6, 784, null, 1, 0, '2001-05-03',"
            + " '2026-11-10 06:49:21', '2026-11-10 06:49:21', '2026-11-10', 1.500, null)");
        // casent2: no coordinates at all.
        exec("insert into specimen values ('casent2', 'myrmicinaestenamma ca01', 'formicidae', 'myrmicinae', 'stenamma', 'United States', null,"
            + " 'Nearctic', 'no coords', null, null, null, null, 1, 0, null, null, null, null, null, null)");
        // casent3: accents, red flag, a group name with a comma and quotes.
        exec("insert into specimen values ('casent3', 'myrmicinaeveromessor andrei', 'formicidae', 'myrmicinae', 'veromessor', 'Mexico', null,"
            + " 'Nearctic', 'Ensenada, Bahía – Punta Banda', 31.8, -116.6, 0, 'red', 2, 1, null, null, null, null, 12.000, 'x=1')");
        // casent4: coordinates 0/0, a name without a taxon row.
        exec("insert into specimen values ('casent4', 'formicinaenotintaxon x', 'formicidae', 'formicinae', 'notintaxon', 'Canada', null,"
            + " 'Nearctic', 'zero coords', 0, 0, null, null, 2, 0, null, null, null, null, null, null)");
        // casent5: on an island AntWeb files under the United States.
        exec("insert into specimen values ('casent5', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', 'Hawaii', 'Hawaii',"
            + " 'Oceania', 'Oahu', 21.3, -157.8, 10, null, 1, 0, null, null, null, null, null, null)");
        // Not in an extract of the three countries: another country, another country's island, no country.
        exec("insert into specimen values ('casent6', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', 'Brazil', null,"
            + " 'Neotropical', 'Manaus', -3.1, -60.0, 90, null, 1, 0, null, null, null, null, null, null)");
        exec("insert into specimen values ('casent7', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', 'Ecuador', 'Galapagos Islands',"
            + " 'Neotropical', 'Santa Cruz', -0.6, -90.3, 5, null, 1, 0, null, null, null, null, null, null)");
        exec("insert into specimen values ('casent8', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', null, null,"
            + " null, 'nowhere', null, null, null, null, 1, 0, null, null, null, null, null, null)");
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) {
            for (String t : TABLES) exec("drop table if exists " + t);
            connection.close();
        }
        if (dir != null) {
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) f.delete();
            dir.delete();
        }
    }

    private static final DataExtract.Site SITE = new DataExtract.Site(new Date(1794322161000L), "abc1234", "9.9.9");   // 2026-11-10 14:49:21 UTC

    private static DataExtract.Request three(boolean islands, String bioregion) {
        return new DataExtract.Request(Arrays.asList("United States", "Canada", "Mexico"), islands, bioregion);
    }

    private List<Map<String, String>> csv(String name) throws Exception {
        File f = new File(dir, name);
        List<Map<String, String>> rows = new ArrayList<>();
        try (Reader r = new BufferedReader(new InputStreamReader(name.endsWith(".gz") ? new GZIPInputStream(Files.newInputStream(f.toPath()))
            : Files.newInputStream(f.toPath()), StandardCharsets.UTF_8))) {
            List<CSVRecord> records = CSVFormat.DEFAULT.parse(r).getRecords();
            CSVRecord head = records.get(0);
            for (CSVRecord rec : records.subList(1, records.size())) {
                assertEquals("fields of line " + rec.getRecordNumber(), head.size(), rec.size());
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < head.size(); i++) row.put(head.get(i), rec.get(i));
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<String> column(List<Map<String, String>> rows, String name) {
        List<String> out = new ArrayList<>();
        for (Map<String, String> r : rows) out.add(r.get(name));
        return out;
    }

    private void checkComplete(Connection c) throws Exception {
        DataExtract.Result result = DataExtract.run(c, dir, three(true, "Nearctic"), SITE, null);
        assertTrue(result.verified);
        assertEquals(5, result.specimens);
        assertTrue(result.text, result.text.contains("Check against the database: VERIFIED, every count equal"));
        List<Map<String, String>> rows = csv(DataExtract.SPECIMENS);
        assertEquals(Arrays.asList("casent1", "casent2", "casent3", "casent4", "casent5"), column(rows, "code"));
        // every specimen column, then the taxon columns, the group, the two marks
        assertEquals(Arrays.asList("code", "taxon_name", "family", "subfamily", "genus", "country", "island_country", "bioregion", "localityname",
            "decimal_latitude", "decimal_longitude", "elevation", "flag", "access_group", "is_introduced", "datecollectedstart", "created",
            "last_modified", "day_entered", "weight", "other", "taxon_status", "taxon_fossil", "taxon_rank", "taxon_current_valid_name",
            "taxon_author_date", "group_name", "no_taxon_row", "has_coordinates"), new ArrayList<>(rows.get(0).keySet()));
        assertEquals(result.manifest.get("columns"), new ArrayList<>(rows.get(0).keySet()));
        // text as it is in the database; NULL is empty
        assertEquals("Mt. Tam\tridge\nline2 \\ back, \"quoted\"", rows.get(0).get("localityname"));
        assertEquals("", rows.get(0).get("flag"));
        assertEquals("", rows.get(0).get("island_country"));
        assertEquals("Ensenada, Bahía – Punta Banda", rows.get(2).get("localityname"));
        assertEquals("Utah, \"Longino\" lab", rows.get(2).get("group_name"));
        // numbers and dates as the database prints them
        assertEquals(Arrays.asList("37.9", "", "31.8", "0", "21.3"), column(rows, "decimal_latitude"));
        assertEquals("-122.6", rows.get(0).get("decimal_longitude"));
        assertEquals(Arrays.asList("784", "", "0", "", "10"), column(rows, "elevation"));
        assertEquals(Arrays.asList("0", "0", "1", "0", "0"), column(rows, "is_introduced"));
        assertEquals("2026-11-10 06:49:21", rows.get(0).get("created"));
        assertEquals("2026-11-10 06:49:21", rows.get(0).get("last_modified"));
        assertEquals("2026-11-10", rows.get(0).get("day_entered"));
        assertEquals("", rows.get(1).get("created"));
        assertEquals(Arrays.asList("1.500", "", "12.000", "", ""), column(rows, "weight"));
        assertEquals("2001-05-03", rows.get(0).get("datecollectedstart"));
        // the name's status, rank and author; the group
        assertEquals(Arrays.asList("valid", "morphotaxon", "valid", "", "valid"), column(rows, "taxon_status"));
        assertEquals("Emery, 1895", rows.get(0).get("taxon_author_date"));
        assertEquals("species", rows.get(0).get("taxon_rank"));
        assertEquals("0", rows.get(0).get("taxon_fossil"));
        assertEquals("CAS", rows.get(0).get("group_name"));
        // specimens without coordinates (none, or 0/0) and without a taxon row stay in the file, marked
        assertEquals(Arrays.asList("1", "0", "1", "0", "1"), column(rows, "has_coordinates"));
        assertEquals(Arrays.asList("0", "0", "0", "1", "0"), column(rows, "no_taxon_row"));

        Map<String, Object> m = result.manifest;
        assertEquals(5L, m.get("specimens"));
        assertEquals(Boolean.TRUE, m.get("verified"));
        assertEquals(new ArrayList<>(), m.get("differences"));
        assertEquals(2L, m.get("without_coordinates"));
        assertEquals(1L, m.get("no_taxon_row"));
        assertEquals(1L, m.get("flagged"));
        assertEquals(0L, m.get("not_ants"));
        assertEquals(Arrays.asList("Hawaii"), m.get("islands"));
        assertEquals(Arrays.asList("United States", "Canada", "Mexico"), m.get("countries"));
        assertEquals("abc1234", m.get("antweb_commit"));
        assertEquals("2026-11-10 14:49:21", m.get("made_utc"));
        assertEquals("{Nearctic=4, Oceania=1}", m.get("by_bioregion").toString());
        assertEquals("{(no taxon row)=1, morphotaxon=1, valid=3}", m.get("by_taxon_status").toString());
        assertEquals("[{country=Canada, island_country=, specimens=1}, {country=Hawaii, island_country=Hawaii, specimens=1},"
            + " {country=Mexico, island_country=, specimens=1}, {country=United States, island_country=, specimens=2}]", m.get("by_place").toString());
        assertEquals("{names=2, names_without_specimen_in_extract=1}", m.get("bioregion_list").toString());
        assertEquals(3L, m.get("genera"));
        assertEquals(4L, m.get("names"));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> files = (Map<String, Map<String, Object>>) m.get("files");
        assertEquals("[specimens.csv.gz, genus_summary.csv, bioregion_list.csv]", files.keySet().toString());
        for (String name : files.keySet()) {
            assertEquals(name, DataExtract.sha256(new File(dir, name)), files.get(name).get("sha256"));
            assertEquals(name, new File(dir, name).length(), files.get(name).get("bytes"));
        }
        assertEquals(Arrays.asList("specimens.csv.gz", "genus_summary.csv", "bioregion_list.csv", "manifest.json", "manifest.txt"), result.files);

        List<Map<String, String>> genus = csv(DataExtract.GENUS_SUMMARY);
        assertEquals("[subfamily, genus, specimens, names, names_with_coordinates, specimens_in_Nearctic, names_in_Nearctic]",
            genus.get(0).keySet().toString());
        assertEquals(Arrays.asList("notintaxon", "stenamma", "veromessor"), column(genus, "genus"));
        Map<String, String> stenamma = genus.get(1);
        assertEquals("myrmicinae 3 2 1 2 2", stenamma.get("subfamily") + " " + stenamma.get("specimens") + " " + stenamma.get("names") + " "
            + stenamma.get("names_with_coordinates") + " " + stenamma.get("specimens_in_Nearctic") + " " + stenamma.get("names_in_Nearctic"));

        List<Map<String, String>> list = csv(DataExtract.BIOREGION_LIST);
        assertEquals("[taxon_name, subfamily, genus, species, subspecies, rank, status, fossil, list_source, is_endemic, is_introduced,"
            + " specimens_in_extract, specimens_in_extract_in_Nearctic]", list.get(0).keySet().toString());
        assertEquals(Arrays.asList("myrmicinaestenamma diecki", "myrmicinaestenamma lit"), column(list, "taxon_name"));
        assertEquals(Arrays.asList("2", "0"), column(list, "specimens_in_extract"));
        assertEquals(Arrays.asList("1", "0"), column(list, "specimens_in_extract_in_Nearctic"));
        assertEquals("specimen", list.get(0).get("list_source"));
        assertEquals("0", list.get(0).get("fossil"));

        String text = new String(Files.readAllBytes(new File(dir, DataExtract.MANIFEST_TXT).toPath()), StandardCharsets.UTF_8);
        assertEquals(result.text, text);
        assertTrue(text, text.contains("Specimens in the file: 5\n"));
        assertTrue(text, text.contains("Islands included: Hawaii\n"));
        assertTrue(text, text.contains("  Hawaii / Hawaii                                   1\n"));   // as Python's "  {:<40} {:>10,}"
        assertTrue(text, text.contains("Without usable coordinates: 2 (in the file, has_coordinates = 0"));
        assertTrue(text, text.contains("AntWeb's Nearctic species list: 2 names; 1 of them have no specimen in this extract"));
        assertTrue(text, text.contains("Cite: AntWeb. Version 9.9.9. California Academy of Sciences, online at https://www.antweb.org. Extract of 2026-11-10."));
        assertFalse(text, text.contains("Differences:"));
        String json = new String(Files.readAllBytes(new File(dir, DataExtract.MANIFEST_JSON).toPath()), StandardCharsets.UTF_8);
        assertTrue(json, json.startsWith("{\n \"tool_version\": \"1\",\n \"made_utc\": \"2026-11-10 14:49:21\",\n \"antweb_commit\": \"abc1234\","));
        assertTrue(json, json.contains("\n \"specimens\": 5,\n \"verified\": true,\n \"differences\": [],\n"));
    }

    public void testTheExtractIsCompleteAndVerified() throws Exception {
        checkComplete(connection);
    }

    /** The same through a connection opened as the application's pools open theirs (results sent as UTF-8). */
    public void testTheSameWithTheApplicationsConnectionSettings() throws Exception {
        try (Connection c = connect(url() + "&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8&characterSetResults=utf8"
            + "&connectionCollation=utf8_general_ci&connectionTimeZone=LOCAL")) {
            checkComplete(c);
        }
    }

    public void testADifferentDatabaseCountIsReported() throws Exception {
        // A specimen arrives between the export and the count, as an upload during the extract would bring it.
        DataExtract.Result result = DataExtract.run(connection, dir, three(true, ""), SITE, null, () -> {
            try (Connection other = connect(url()); Statement s = other.createStatement()) {
                s.executeUpdate("insert into specimen (code, taxon_name, family, country) values ('casent9', 'myrmicinaestenamma diecki', 'formicidae', 'United States')");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        assertFalse(result.verified);
        assertEquals(5, result.specimens);
        assertEquals("[{country=United States, island_country=, in_file=2, in_database=3}]", result.manifest.get("differences").toString());
        assertEquals(Boolean.FALSE, result.manifest.get("verified"));
        assertTrue(result.text, result.text.contains("Check against the database: DIFFERENT, see differences; run again"));
        assertTrue(result.text, result.text.contains("Differences:\n  {\"country\": \"United States\", \"island_country\": \"\", \"in_file\": 2, \"in_database\": 3}\n"));
        // no bioregion: no list file, no bioregion columns
        assertFalse(new File(dir, DataExtract.BIOREGION_LIST).exists());
        assertNull(result.manifest.get("bioregion_list"));
        assertEquals("[subfamily, genus, specimens, names, names_with_coordinates]", csv(DataExtract.GENUS_SUMMARY).get(0).keySet().toString());
    }

    public void testAnUnknownCountryNameStopsBeforeAnyFile() throws Exception {
        for (String[] bad : new String[][] {
            {"Atlantis", "AntWeb has no country named: Atlantis."},
            {"USA", "Not a current country name: USA (AntWeb files its specimens under United States)."},
            {"California", "AntWeb has no country named: California."},      // an adm1, not a country
        }) {
            try {
                DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("Canada", bad[0]), true, ""), SITE, null);
                fail("accepted " + bad[0]);
            } catch (DataExtract.BadRequest e) {
                assertTrue(e.getMessage(), e.getMessage().contains(bad[1]));
            }
            assertEquals("no file written for " + bad[0], 0, dir.list().length);
        }
        try {
            DataExtract.run(connection, dir, new DataExtract.Request(new ArrayList<>(), true, ""), SITE, null);
            fail("accepted no country");
        } catch (DataExtract.BadRequest e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("Give at least one country"));
        }
        try {
            DataExtract.run(connection, dir, three(true, "Atlantic"), SITE, null);
            fail("accepted an unknown bioregion");
        } catch (DataExtract.BadRequest e) {
            assertEquals("AntWeb has no bioregion named: Atlantic.", e.getMessage());
        }
        assertEquals(0, dir.list().length);
    }

    public void testIslandsCanBeLeftOut() throws Exception {
        DataExtract.Result result = DataExtract.run(connection, dir, three(false, ""), SITE, null);
        assertTrue(result.verified);
        assertEquals(new ArrayList<>(), result.manifest.get("islands"));
        assertEquals(Arrays.asList("casent1", "casent2", "casent3", "casent4"), column(csv(DataExtract.SPECIMENS), "code"));
        assertTrue(result.text, result.text.contains("Islands included: none\n"));
    }

    public void testNamesAreSpelledAsTheDatabaseSpellsThem() throws Exception {
        assertEquals(Arrays.asList("United States", "Cote d'Ivoire"), DataExtract.resolve(connection, Arrays.asList("united states", "COTE D'IVOIRE")));
        assertEquals("Nearctic", DataExtract.resolveBioregion(connection, " nearctic "));
        assertEquals("", DataExtract.resolveBioregion(connection, ""));
        // an island country asked for by name is a country of the extract, not listed again as an island
        assertEquals(new ArrayList<>(), DataExtract.islands(connection, Arrays.asList("United States", "Hawaii")));
        assertEquals(Arrays.asList("Hawaii"), DataExtract.islands(connection, Arrays.asList("United States")));
        assertEquals(new ArrayList<>(), DataExtract.islands(connection, Arrays.asList("Cote d'Ivoire")));
        // a quote in a name does not break the queries
        exec("insert into specimen (code, taxon_name, family, country) values ('casent10', 'myrmicinaestenamma diecki', 'formicidae', 'Cote d''Ivoire')");
        DataExtract.Result result = DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("cote d'ivoire"), true, "nearctic"), SITE, null);
        assertTrue(result.verified);
        assertEquals(1, result.specimens);
        assertEquals(Arrays.asList("Cote d'Ivoire"), result.manifest.get("countries"));
        assertEquals("Nearctic", result.manifest.get("bioregion"));
    }

    public void testTheConnectionComesBackAsItWasAndOnlyReads() throws Exception {
        connection.setAutoCommit(false);
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        DataExtract.run(connection, dir, three(true, ""), SITE, null);
        assertFalse(connection.getAutoCommit());
        assertFalse(connection.isReadOnly());
        assertEquals(Connection.TRANSACTION_REPEATABLE_READ, connection.getTransactionIsolation());
        connection.setAutoCommit(true);
        // the session's own counters: the extract ran no statement that changes data or tables
        long before = writes();
        DataExtract.run(connection, dir, three(true, "Nearctic"), SITE, null);
        assertEquals("statements that write, run by the extract", before, writes());
        exec("insert into specimen (code, country) values ('casent11', 'Canada')");   // the connection writes again afterwards
        assertEquals(before + 1, writes());
    }

    private long writes() throws SQLException {
        long n = 0;
        try (Statement s = connection.createStatement();
             java.sql.ResultSet r = s.executeQuery("show session status where Variable_name in ('Com_insert', 'Com_insert_select', 'Com_update',"
                 + " 'Com_update_multi', 'Com_delete', 'Com_delete_multi', 'Com_replace', 'Com_replace_select', 'Com_truncate', 'Com_create_table',"
                 + " 'Com_drop_table', 'Com_alter_table', 'Com_load')")) {
            while (r.next()) n += r.getLong(2);
        }
        return n;
    }

    public void testAZeroDateDoesNotStopTheExtract() throws Exception {
        exec("set session sql_mode = ''");
        exec("insert into specimen (code, taxon_name, family, country, last_modified, day_entered) values"
            + " ('casent0', 'myrmicinaestenamma diecki', 'formicidae', 'Mexico', '0000-00-00 00:00:00', '0000-00-00')");
        DataExtract.Result result = DataExtract.run(connection, dir, three(true, ""), SITE, null);
        assertTrue(result.verified);
        Map<String, String> row = csv(DataExtract.SPECIMENS).get(0);
        assertEquals("casent0", row.get("code"));
        assertEquals("0000-00-00 00:00:00", row.get("last_modified"));
        assertEquals("0000-00-00", row.get("day_entered"));
    }

    public void testManyRowsAreStreamedWithProgress() throws Exception {
        exec("create table if not exists extract_seq (n int primary key)");
        try {
            exec("delete from extract_seq");
            StringBuilder values = new StringBuilder();
            for (int i = 0; i < 110; i++) values.append(i == 0 ? "" : ", ").append("(").append(i).append(")");
            exec("insert into extract_seq values " + values);
            exec("insert into specimen (code, taxon_name, family, subfamily, genus, country, bioregion, localityname, decimal_latitude, decimal_longitude)"
                + " select concat('bulk', lpad(a.n * 110 + b.n, 6, '0')), 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', 'Canada',"
                + " 'Nearctic', concat('site ', a.n, ', \"', b.n, '\"'), 45.5, -75.5 from extract_seq a, extract_seq b");
            List<Long> reports = new ArrayList<>();
            DataExtract.Result result = DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("Canada"), true, ""), SITE, reports::add);
            assertTrue(result.verified);
            assertEquals(12101, result.specimens);
            assertEquals(Arrays.asList(5000L, 10000L), reports);
            List<Map<String, String>> rows = csv(DataExtract.SPECIMENS);
            assertEquals(12101, rows.size());
            assertEquals("bulk000000", rows.get(0).get("code"));
            assertEquals("site 0, \"0\"", rows.get(0).get("localityname"));
            assertEquals("casent4", rows.get(12100).get("code"));
            assertTrue(result.text, result.text.contains("Specimens in the file: 12,101\n"));
            assertTrue(result.text, result.text.contains("  Canada                                       12,101\n"));
        } finally {
            exec("drop table if exists extract_seq");
        }
    }
}
