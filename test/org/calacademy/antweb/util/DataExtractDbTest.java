package test.org.calacademy.antweb.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
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

import org.calacademy.antweb.curate.DataExtractAction;
import org.calacademy.antweb.util.DataExtract;

/**
 * The data extract engine (DataExtract, dataExtract.do; 2026-11-10) against MySQL 8.4, with the cases of the
 * command-line tool's test (tests/unit/test_specimen_extract.py): the extract of three countries and an island
 * named with them is complete and verified; tabs, line breaks, backslashes, quotes, commas and accents
 * come out as they are in the database and NULL as an empty field; numbers and dates read as the database prints
 * them (37.9, 0, 1.500, a zero date); specimens without coordinates (none, or 0/0) and with a name that has no taxon row
 * stay in the file, marked; a specimen of another country is not in it; a database count that differs from the
 * rows written is reported (not verified, the difference named); an unknown or outdated country name stops before
 * any file is written; names are spelled as the database spells them; the connection
 * comes back as it was; 12,000 rows are streamed with progress reports.
 *
 * Islands (Brian Fisher, 2026-11-11: an oceanic island or archipelago with its own ant fauna is its own country,
 * and its specimens never count toward the political parent): an island is in the file only when it is named with
 * the countries, in whichever form its specimens are stored (country = the island, or the parent with the island
 * in island_country), also under an old name of the island; the manifest names the islands left out; a specimen
 * with no country under an included island is in one file only. The warning for a country without specimens
 * counts what the file can hold (ants, when ants only).
 *
 * After the review of 2026-11-10: every country row that is not valid is refused (with or without a valid name);
 * specimens still filed under an old name of a requested country (USA) are in the file, named and counted; a
 * requested country without specimens is a warning; two spellings of a country (Mexico, MEXICO, with an accent)
 * are counted apart on both sides, so the extract is verified; the specimens are read in pages by code (the same
 * file whatever the page size) and a change of the table during the extract does not wait for it; a four-byte
 * character comes out whole through a connection set up as the application's; no file is left half-written.
 *
 * Format 5 (Brian Fisher, 2026-11-10: "why did you include non ants for an ant study?"): ants only is the default,
 * with the same filter for the file and the check; what is left out is counted by family; a specimen with no
 * family is kept and counted; all families on request; a row that is not an ant in an ants-only file is not
 * verified. The specimens without a country go in a file of their own on request, with the same choice of families.
 * Format 6: the bioregion's species list is of ants only too and says how many names of other families it leaves out.
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

    /**
     * This test drops and recreates specimen, taxon and the other tables it uses. It refuses to start on a database
     * that looks real (review of #355): a specimen table with more rows than any test makes (the tests insert at most
     * about 12,000), so a wrong ANTWEB_TEST_DB_URL on a server can never drop AntWeb's specimens.
     */
    static void refuseARealDatabase(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet r = st.executeQuery("select count(*) from information_schema.tables where table_schema = database() and table_name = 'specimen'")) {
            if (!r.next() || r.getLong(1) == 0) return;
        }
        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("select count(*) from specimen")) {
            if (r.next() && r.getLong(1) > 50000) {
                throw new IllegalStateException("refusing to run: the specimen table of " + c.getCatalog() + " holds " + r.getLong(1)
                    + " rows, so this is not a test database (this test drops its tables)");
            }
        }
    }

    @Override
    protected void setUp() throws Exception {
        connection = connect(url());
        dir = Files.createTempDirectory("extract-db-test").toFile();
        refuseARealDatabase(connection);
        for (String t : TABLES) exec("drop table if exists " + t);
        exec("create table specimen (code varchar(40) primary key, taxon_name varchar(128), family varchar(40), subfamily varchar(64),"
            + " genus varchar(64), country varchar(64), island_country varchar(64), bioregion varchar(64), localityname text,"
            + " decimal_latitude float, decimal_longitude float, elevation int, flag varchar(10), access_group int, is_introduced tinyint(1),"
            + " datecollectedstart varchar(20), created timestamp null, last_modified datetime null, day_entered date null,"
            + " weight decimal(8, 3), other longtext) default charset=utf8mb4");
        exec("create table taxon (taxon_name varchar(128) primary key, status varchar(40), fossil tinyint default 0, taxarank varchar(20),"
            + " current_valid_name varchar(128), author_date varchar(128), subfamily varchar(64), genus varchar(64), species varchar(64),"
            + " subspecies varchar(64), family varchar(40) default 'formicidae') default charset=utf8mb4");
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
            // an island that is its own country: parent = the subregion its page hangs under, country = its political parent
            + " (2, 'Hawaii', 'country', 1, null, 1, 'Polynesia', 'United States', 'Oceania', null),"
            + " (3, 'Mexico', 'country', 1, null, 0, 'Central America', null, 'Neotropical', 'Nearctic'),"
            + " (4, 'United States', 'country', 1, null, 0, 'Northern America', null, 'Nearctic', 'Neotropical'),"
            + " (5, 'USA', 'country', 0, 'United States', 0, null, null, null, null),"
            + " (6, 'Brazil', 'country', 1, null, 0, 'South America', null, 'Neotropical', null),"
            + " (7, 'Cote d''Ivoire', 'country', 1, null, 0, 'Western Africa', null, 'Afrotropical', null),"
            + " (8, 'California', 'adm1', 1, null, 0, 'United States', null, 'Nearctic', null),"
            + " (9, 'Galapagos Islands', 'country', 1, null, 1, 'South America', 'Ecuador', 'Neotropical', null),"
            + " (10, 'Zaire', 'country', 0, null, 0, null, null, null, null),"
            + " (11, 'Etats-Unis', 'country', 0, 'United States', 0, null, null, null, null),"
            + " (12, 'Mexique', 'country', null, 'Mexico', 0, null, null, null, null)");
        exec("insert into taxon (taxon_name, status, fossil, taxarank, current_valid_name, author_date, subfamily, genus, species, subspecies) values"
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

    private static final List<String> THREE = Arrays.asList("United States", "Canada", "Mexico");

    /** The three countries and Hawaii: an island that is its own country is in the file only when it is named. */
    private static final List<String> WITH_HAWAII = Arrays.asList("United States", "Canada", "Mexico", "Hawaii");

    /** The three countries alone: no island (Brian Fisher, 2026-11-11). */
    private static DataExtract.Request three(String bioregion) {
        return new DataExtract.Request(THREE, bioregion);
    }

    private static DataExtract.Request withHawaii(String bioregion) {
        return new DataExtract.Request(WITH_HAWAII, bioregion);
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
        DataExtract.Result result = DataExtract.run(c, dir, withHawaii("Nearctic"), SITE, null);
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
        // Hawaii is in the file because it is named with the countries (Brian Fisher, 2026-11-11)
        assertEquals(Arrays.asList("Hawaii"), m.get("islands"));
        assertEquals(new ArrayList<>(), m.get("islands_left_out"));
        assertEquals("{}", m.get("old_island_names").toString());
        assertEquals("[{name=Etats-Unis, valid_name=United States}, {name=Mexique, valid_name=Mexico}, {name=USA, valid_name=United States}]",
            m.get("outdated_names").toString());
        assertEquals(0L, m.get("filed_under_outdated_name"));
        assertEquals(new ArrayList<>(), m.get("countries_without_specimens"));
        assertEquals("10", m.get("tool_version"));
        assertEquals(Boolean.TRUE, m.get("ants_only"));
        assertEquals("{specimens=0, families=0, by_family={}}", m.get("left_out_not_formicidae").toString());
        assertEquals(0L, m.get("blank_family"));
        assertTrue(m.containsKey("no_country"));
        assertNull(m.get("no_country"));
        assertEquals(WITH_HAWAII, m.get("countries"));
        assertEquals("abc1234", m.get("antweb_commit"));
        assertEquals("2026-11-10 14:49:21", m.get("made_utc"));
        assertEquals("{Nearctic=4, Oceania=1}", m.get("by_bioregion").toString());
        assertEquals("{(no taxon row)=1, morphotaxon=1, valid=3}", m.get("by_taxon_status").toString());
        assertEquals("[{country=Canada, island_country=, specimens=1}, {country=Hawaii, island_country=Hawaii, specimens=1},"
            + " {country=Mexico, island_country=, specimens=1}, {country=United States, island_country=, specimens=2}]", m.get("by_place").toString());
        assertEquals("{names=2, names_without_specimen_in_extract=1, names_left_out_not_formicidae=0}", m.get("bioregion_list").toString());
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
        assertTrue(text, text.contains("Countries: United States, Canada, Mexico, Hawaii\nIslands included (named in the countries): Hawaii\n"
            + "Islands of these countries that are their own country in AntWeb, NOT in this file: none\n"
            + "Old island names read as the island (a specimen marked with one is the island's): none\nOutdated country names included: Etats-Unis (now United States), Mexique (now Mexico), USA (now United States)\n"));
        assertTrue(text, text.contains("Ants only (family Formicidae): 0 specimens of other families are left out; --all-families includes them\n"
            + "No family given: 0 (in the file)\nFiled under an outdated country name: 0\nGenera: 3   distinct names: 4\n"));
        assertTrue(text, text.contains("Spreadsheets: import the columns of specimens.csv as text. Values are written as they are in the database,\nalso when they start with =, +, - or @.\n"));
        assertFalse(text, text.contains("WARNING"));
        assertFalse(text, text.contains("Country spellings"));
        // nothing half-written is left: the files, and no .part
        List<String> left = new ArrayList<>(Arrays.asList(dir.list()));
        java.util.Collections.sort(left);
        assertEquals("[bioregion_list.csv, genus_summary.csv, manifest.json, manifest.txt, specimens.csv.gz]", left.toString());
        assertTrue(text, text.contains("  Hawaii / Hawaii                                   1\n"));   // as Python's "  {:<40} {:>10,}"
        assertTrue(text, text.contains("Without usable coordinates: 2 (in the file, has_coordinates = 0"));
        assertTrue(text, text.contains("AntWeb's Nearctic species list: 2 names of ants (0 names of other families left out); 1 of them have no specimen in this extract"));
        assertTrue(text, text.contains("Cite: AntWeb. Version 9.9.9. California Academy of Sciences, online at https://www.antweb.org. Extract of 2026-11-10."));
        assertFalse(text, text.contains("Differences:"));
        String json = new String(Files.readAllBytes(new File(dir, DataExtract.MANIFEST_JSON).toPath()), StandardCharsets.UTF_8);
        assertTrue(json, json.startsWith("{\n \"tool_version\": \"10\",\n \"made_utc\": \"2026-11-10 14:49:21\",\n \"antweb_commit\": \"abc1234\","));
        assertTrue(json, json.contains("\n \"specimens\": 5,\n \"verified\": true,\n \"differences\": [],\n"));
    }

    public void testTheExtractIsCompleteAndVerified() throws Exception {
        checkComplete(connection);
    }

    /** The same through a connection opened as the application's pools open theirs (results sent as UTF-8). */
    private static final String APP_SETTINGS = "&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8&characterSetResults=utf8"
        + "&connectionCollation=utf8_general_ci&connectionTimeZone=LOCAL";

    public void testTheSameWithTheApplicationsConnectionSettings() throws Exception {
        try (Connection c = connect(url() + APP_SETTINGS)) {
            checkComplete(c);
        }
    }

    private static String sessionValue(Connection c, String name) throws SQLException {
        try (Statement s = c.createStatement(); java.sql.ResultSet r = s.executeQuery("select @@" + name)) {
            r.next();
            return r.getString(1);
        }
    }

    /** Review L7: the pools ask for results as utf8 (three bytes at most); the extract asks for utf8mb4 for its run. */
    public void testAFourByteCharacterComesOutWhole() throws Exception {
        String ant = new String(Character.toChars(0x1F41C));
        exec("update specimen set localityname = concat('ant ', convert(unhex('F09F909C') using utf8mb4), ' hill') where code = 'casent2'");
        try (Connection c = connect(url() + APP_SETTINGS)) {
            String before = sessionValue(c, "character_set_results");
            DataExtract.Result result = DataExtract.run(c, dir, withHawaii(""), SITE, null);
            assertTrue(result.verified);
            assertEquals("ant " + ant + " hill", csv(DataExtract.SPECIMENS).get(1).get("localityname"));
            assertEquals("the session's result character set is put back", before, sessionValue(c, "character_set_results"));
        }
        // and through a connection without those settings
        delete(dir);
        assertEquals("ant " + ant + " hill", runAndRead(withHawaii("")).get(1).get("localityname"));
    }

    private void delete(File folder) {
        File[] files = folder.listFiles();
        if (files != null) for (File f : files) f.delete();
    }

    private List<Map<String, String>> runAndRead(DataExtract.Request request) throws Exception {
        DataExtract.Result result = DataExtract.run(connection, dir, request, SITE, null);
        assertTrue(result.text, result.verified);
        return csv(DataExtract.SPECIMENS);
    }

    public void testADifferentDatabaseCountIsReported() throws Exception {
        // A specimen arrives between the export and the count, as an upload during the extract would bring it.
        DataExtract.Result result = DataExtract.run(connection, dir, withHawaii(""), SITE, null, () -> {
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
            {"USA", "Not a valid country name: USA (AntWeb files its specimens under United States)."},
            // review M3: a row that is not valid and names no valid name was accepted ("VERIFIED, 0 specimens")
            {"Zaire", "Not a valid country name: Zaire (AntWeb keeps this name only as one that is not valid)."},
            {"Mexique", "Not a valid country name: Mexique (AntWeb files its specimens under Mexico)."},
            {"California", "AntWeb has no country named: California."},      // an adm1, not a country
        }) {
            try {
                DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("Canada", bad[0]), ""), SITE, null);
                fail("accepted " + bad[0]);
            } catch (DataExtract.BadRequest e) {
                assertTrue(e.getMessage(), e.getMessage().contains(bad[1]));
            }
            assertEquals("no file written for " + bad[0], 0, dir.list().length);
        }
        try {
            DataExtract.run(connection, dir, new DataExtract.Request(new ArrayList<>(), ""), SITE, null);
            fail("accepted no country");
        } catch (DataExtract.BadRequest e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("Give at least one country"));
        }
        try {
            DataExtract.run(connection, dir, withHawaii("Atlantic"), SITE, null);
            fail("accepted an unknown bioregion");
        } catch (DataExtract.BadRequest e) {
            assertEquals("AntWeb has no bioregion named: Atlantic.", e.getMessage());
        }
        assertEquals(0, dir.list().length);
    }

    private void others() throws SQLException {
        exec("insert into specimen (code, taxon_name, family, subfamily, genus, country, island_country, bioregion, access_group) values"
            + " ('beetle1', 'curculionidaex a', 'curculionidae', null, 'x', 'United States', null, 'Nearctic', 1),"
            + " ('beetle2', 'curculionidaex a', 'curculionidae', null, 'x', 'Canada', null, 'Nearctic', 1),"
            + " ('beetle3', 'curculionidaex b', 'Curculionidae', null, 'x', 'Mexico', null, 'Nearctic', 2),"
            + " ('wasp1', 'vespidaey a', 'vespidae', null, 'y', 'Hawaii', 'Hawaii', 'Oceania', 1),"
            + " ('spider1', 'salticidaez a', 'salticidae', null, 'z', 'United States', null, 'Nearctic', 2),"
            + " ('order1', '(coleoptera)', '(coleoptera)', null, null, 'USA', null, 'Nearctic', 1),"
            + " ('ant1', 'myrmicinaestenamma diecki', 'FORMICIDAE', 'myrmicinae', 'stenamma', 'Canada', null, 'Nearctic', 1),"
            + " ('blank1', 'myrmicinaestenamma diecki', '', 'myrmicinae', 'stenamma', 'Mexico', null, 'Nearctic', 1),"
            + " ('blank2', 'x', null, null, null, 'United States', null, null, 2),"
            + " ('beetle9', 'curculionidaex a', 'curculionidae', null, 'x', 'Brazil', null, 'Neotropical', 1)");
        // the bioregion's species list also holds names that are not ants, and one whose family is not given
        exec("insert into taxon (taxon_name, status, taxarank, subfamily, genus, species, family) values"
            + " ('curculionidaex a', 'valid', 'species', null, 'x', 'a', 'curculionidae'),"
            + " ('vespidaey a', 'valid', 'species', null, 'y', 'a', 'Vespidae'),"
            + " ('curculionidaex', 'valid', 'genus', null, 'x', null, 'curculionidae'),"
            + " ('myrmicinaestenamma nofamily', 'valid', 'species', 'myrmicinae', 'stenamma', 'nofamily', null)");
        exec("insert into bioregion_taxon values ('Nearctic', 'curculionidaex a', 'specimen', 0, 0), ('Nearctic', 'vespidaey a', 'specimen', 0, 0),"
            + " ('Nearctic', 'curculionidaex', 'specimen', 0, 0), ('Nearctic', 'myrmicinaestenamma nofamily', 'antcat', 0, 0),"
            + " ('Nearctic', 'not a taxon row', 'curator', 0, 0)");
    }

    /** Format 6: the bioregion's species list follows the same choice, and says how many names it leaves out. */
    public void testTheBioregionListIsOfAntsOnlyToo() throws Exception {
        others();
        DataExtract.Result result = DataExtract.run(connection, dir, withHawaii("Nearctic"), SITE, null);
        assertTrue(result.text, result.verified);
        List<Map<String, String>> list = csv(DataExtract.BIOREGION_LIST);
        // the ants, the name without a family and the name without a taxon row; not the beetle or the wasp (species), nor genus rows
        assertEquals(Arrays.asList("not a taxon row", "myrmicinaestenamma diecki", "myrmicinaestenamma lit", "myrmicinaestenamma nofamily"), column(list, "taxon_name"));
        assertEquals("{names=4, names_without_specimen_in_extract=3, names_left_out_not_formicidae=2}", result.manifest.get("bioregion_list").toString());
        assertTrue(result.text, result.text.contains("AntWeb's Nearctic species list: 4 names of ants (2 names of other families left out); 3 of them have no specimen in this extract (literature, or other countries)\n"));
        // all families: the whole list, and nothing said to be left out
        delete(dir);
        DataExtract.Result all = DataExtract.run(connection, dir, new DataExtract.Request(WITH_HAWAII, "Nearctic", true, false), SITE, null);
        assertTrue(all.text, all.verified);
        assertEquals(Arrays.asList("not a taxon row", "curculionidaex a", "vespidaey a", "myrmicinaestenamma diecki", "myrmicinaestenamma lit", "myrmicinaestenamma nofamily"),
            column(csv(DataExtract.BIOREGION_LIST), "taxon_name"));
        assertEquals("{names=6, names_without_specimen_in_extract=3, names_left_out_not_formicidae=null}", all.manifest.get("bioregion_list").toString());
        assertTrue(all.text, all.text.contains("AntWeb's Nearctic species list: 6 names; 3 of them have no specimen in this extract (literature, or other countries)\n"));
    }

    /**
     * Format 5 (Brian Fisher, 2026-11-10: "why did you include non ants for an ant study?"): AntWeb also holds beetles,
     * wasps and spiders found with ants (production: 1,289 of the 200,075 specimens of United States, Canada and Mexico).
     */
    public void testAntsOnlyIsTheDefaultAndSaysWhatItLeftOut() throws Exception {
        others();
        DataExtract.Result result = DataExtract.run(connection, dir, withHawaii(""), SITE, null);
        assertTrue(result.text, result.verified);
        // the ants (also written FORMICIDAE) and the two specimens with no family; no beetle, wasp or spider
        assertEquals(Arrays.asList("ant1", "blank1", "blank2", "casent1", "casent2", "casent3", "casent4", "casent5"), column(csv(DataExtract.SPECIMENS), "code"));
        Map<String, Object> m = result.manifest;
        assertEquals(Boolean.TRUE, m.get("ants_only"));
        // by family, the largest first, then by name; the old country name USA and the island are part of the filter
        assertEquals("{specimens=6, families=4, by_family={curculionidae=3, (coleoptera)=1, salticidae=1, vespidae=1}}", m.get("left_out_not_formicidae").toString());
        assertEquals(2L, m.get("blank_family"));
        assertEquals(0L, m.get("not_ants"));
        assertEquals(8L, m.get("specimens"));
        assertTrue(result.text, result.text.contains("Red-flagged at upload: 1 (column flag)\n"
            + "Ants only (family Formicidae): 6 specimens of other families are left out (curculionidae 3, (coleoptera) 1, salticidae 1, vespidae 1); --all-families includes them\n"
            + "No family given: 2 (in the file)\n"));
        // the check counted with the same filter: the six others are not a difference
        assertEquals(new ArrayList<>(), m.get("differences"));
        String json = new String(Files.readAllBytes(new File(dir, DataExtract.MANIFEST_JSON).toPath()), StandardCharsets.UTF_8);
        assertTrue(json, json.contains("\n \"flagged\": 1,\n \"ants_only\": true,\n \"left_out_not_formicidae\": {\n  \"specimens\": 6,\n  \"families\": 4,\n  \"by_family\": {\n   \"curculionidae\": 3,"));
        assertTrue(json, json.contains("\n \"blank_family\": 2,\n \"not_ants\": 0,\n"));

        // all families on request: every specimen of the places, and how many are not ants
        delete(dir);
        DataExtract.Result all = DataExtract.run(connection, dir, new DataExtract.Request(WITH_HAWAII, "", true, false), SITE, null);
        assertTrue(all.text, all.verified);
        assertEquals(Arrays.asList("ant1", "beetle1", "beetle2", "beetle3", "blank1", "blank2", "casent1", "casent2", "casent3", "casent4", "casent5",
            "order1", "spider1", "wasp1"), column(csv(DataExtract.SPECIMENS), "code"));
        assertEquals(Boolean.FALSE, all.manifest.get("ants_only"));
        assertNull(all.manifest.get("left_out_not_formicidae"));
        assertEquals(6L, all.manifest.get("not_ants"));
        assertEquals(2L, all.manifest.get("blank_family"));
        assertTrue(all.text, all.text.contains("All families (--all-families): 6 specimens in the file are not Formicidae\nNo family given: 2 (in the file)\n"));
        assertFalse(all.text, all.text.contains("Ants only"));
    }

    /** The database takes a family written with an accent for Formicidae; the file then holds a row that is not an ant. */
    public void testARowThatIsNotAnAntInAnAntsOnlyFileIsNotVerified() throws Exception {
        exec("insert into specimen (code, taxon_name, family, country) values ('odd1', 'x', 'formicida" + (char) 0xe9 + "', 'Canada')");
        DataExtract.Result result = DataExtract.run(connection, dir, withHawaii(""), SITE, null);
        assertFalse(result.verified);
        assertEquals(1L, result.manifest.get("not_ants"));
        assertEquals("[{file=specimens.csv.gz, not_formicidae_in_an_ants_only_file=1}]", result.manifest.get("differences").toString());
        assertTrue(result.text, result.text.contains("Check against the database: DIFFERENT, see differences; run again\n"));
        assertTrue(result.text, result.text.contains("Differences:\n  {\"file\": \"specimens.csv.gz\", \"not_formicidae_in_an_ants_only_file\": 1}\n"));
        // with all families it is one more specimen that is not an ant, and the file is verified
        delete(dir);
        DataExtract.Result all = DataExtract.run(connection, dir, new DataExtract.Request(WITH_HAWAII, "", true, false), SITE, null);
        assertTrue(all.verified);
        assertEquals(1L, all.manifest.get("not_ants"));
    }

    /**
     * The specimens that have no country at all (production 2026-11-10: 1,623): no choice of countries finds them,
     * and some belong to the countries asked for. On request they come in a file of their own.
     */
    public void testTheSpecimensWithoutACountryGoInTheirOwnFile() throws Exception {
        others();
        exec("insert into specimen (code, taxon_name, family, subfamily, genus, country, localityname, decimal_latitude, decimal_longitude, access_group) values"
            + " ('none1', 'myrmicinaestenamma utah01', 'formicidae', 'myrmicinae', 'stenamma', '', 'somewhere in Utah', 40.2, -111.6, 2),"
            + " ('none2', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', null, 'El Paso', null, null, 2),"
            + " ('none3', 'myrmicinaestenamma utah02', null, 'myrmicinae', 'stenamma', null, 'no group', null, null, null),"
            + " ('none4', 'curculionidaex a', 'curculionidae', null, 'x', null, 'a beetle', null, null, 1)");
        DataExtract.Request request = new DataExtract.Request(WITH_HAWAII, "Nearctic", false, true);
        DataExtract.Result result = DataExtract.run(connection, dir, request, SITE, null);
        assertTrue(result.text, result.verified);
        assertEquals(8, result.specimens);      // the main file is as without the option
        List<Map<String, String>> rows = csv(DataExtract.SPECIMENS_NO_COUNTRY);
        // casent8 is the fixture's specimen without a country; the beetle is left out as in the main file
        assertEquals(Arrays.asList("casent8", "none1", "none2", "none3"), column(rows, "code"));
        assertEquals(Arrays.asList("", "", "", ""), column(rows, "country"));
        assertEquals(Arrays.asList("0", "1", "0", "0"), column(rows, "has_coordinates"));
        assertEquals(new ArrayList<>(csv(DataExtract.SPECIMENS).get(0).keySet()), new ArrayList<>(rows.get(0).keySet()));
        // groups largest first; of the same size in the order they first appear (CAS, then the one without a name)
        assertEquals("{specimens=4, in_database=4, with_coordinates=1, names=3, names_not_in_the_countries_file=2,"
            + " by_group={Utah, \"Longino\" lab=2, CAS=1, =1}}", result.manifest.get("no_country").toString());
        assertTrue(result.text, result.text.contains("\nSpecimens with no country, in specimens_no_country.csv.gz (all of AntWeb; no country filter finds them; "
            + "one filed under an included island is in specimens.csv.gz and not here, so no specimen is in both files): "
            + "4; with coordinates 1; 3 names, 2 of them not in the countries file\n  by upload group: Utah, \"Longino\" lab 2, CAS 1, (none) 1\n\nFiles (sha256):\n"));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> files = (Map<String, Map<String, Object>>) result.manifest.get("files");
        assertEquals("[specimens.csv.gz, specimens_no_country.csv.gz, genus_summary.csv, bioregion_list.csv]", files.keySet().toString());
        assertEquals(DataExtract.sha256(new File(dir, DataExtract.SPECIMENS_NO_COUNTRY)), files.get(DataExtract.SPECIMENS_NO_COUNTRY).get("sha256"));
        assertEquals(Arrays.asList("specimens.csv.gz", "specimens_no_country.csv.gz", "genus_summary.csv", "bioregion_list.csv", "manifest.json", "manifest.txt"), result.files);
        List<String> left = new ArrayList<>(Arrays.asList(dir.list()));
        java.util.Collections.sort(left);
        assertEquals("[bioregion_list.csv, genus_summary.csv, manifest.json, manifest.txt, specimens.csv.gz, specimens_no_country.csv.gz]", left.toString());
        // the genus summary and the bioregion list are of the main file only
        assertEquals(Arrays.asList("", "notintaxon", "stenamma", "veromessor"), column(csv(DataExtract.GENUS_SUMMARY), "genus"));

        // with all families the beetle without a country is in its file too, and pages do not change the file
        delete(dir);
        DataExtract.Result all = DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("Canada"), "", true, true), SITE, null, null, 2);
        assertTrue(all.verified);
        assertEquals(Arrays.asList("casent8", "none1", "none2", "none3", "none4"), column(csv(DataExtract.SPECIMENS_NO_COUNTRY), "code"));
        assertEquals(5L, ((Map<?, ?>) all.manifest.get("no_country")).get("specimens"));
        // without the option: no file, and the manifest says so
        delete(dir);
        DataExtract.Result without = DataExtract.run(connection, dir, withHawaii(""), SITE, null);
        assertFalse(new File(dir, DataExtract.SPECIMENS_NO_COUNTRY).exists());
        assertNull(without.manifest.get("no_country"));
        assertFalse(without.text, without.text.contains("Specimens with no country"));
    }

    /** Review M3: VERIFIED proved the file equals the filter, not that the filter is complete. */
    public void testSpecimensUnderAnOutdatedNameAreInTheFileAndCounted() throws Exception {
        exec("insert into specimen (code, taxon_name, family, subfamily, genus, country, bioregion) values"
            + " ('casent12', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', 'USA', 'Nearctic'),"
            + " ('casent13', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', 'Etats-Unis', 'Nearctic'),"
            + " ('casent14', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', 'USA', 'Nearctic')");
        DataExtract.Result result = DataExtract.run(connection, dir, withHawaii(""), SITE, null);
        assertTrue(result.text, result.verified);
        assertEquals(8, result.specimens);
        assertEquals(Arrays.asList("casent1", "casent12", "casent13", "casent14", "casent2", "casent3", "casent4", "casent5"), column(csv(DataExtract.SPECIMENS), "code"));
        assertEquals(3L, result.manifest.get("filed_under_outdated_name"));
        assertTrue(result.manifest.get("by_place").toString(), result.manifest.get("by_place").toString().contains("{country=USA, island_country=, specimens=2}"));
        assertTrue(result.text, result.text.contains("Filed under an outdated country name: 3\n"));
        assertTrue(result.text, result.text.contains(String.format("  %-40s %,10d\n", "Etats-Unis", 1)));
        assertFalse("an old name is listed as such, not as another spelling", result.text.contains("Country spellings"));
        // only the old names of the countries asked for: Canada alone takes none of them
        DataExtract.Result canada = DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("Canada"), ""), SITE, null);
        assertEquals(1, canada.specimens);
        assertEquals("[]", canada.manifest.get("outdated_names").toString());
        assertTrue(canada.text, canada.text.contains("Outdated country names included: none\n"));
    }

    public void testACountryWithoutSpecimensIsAWarning() throws Exception {
        DataExtract.Result result = DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("Canada", "Cote d'Ivoire", "Brazil"), ""), SITE, null);
        assertTrue(result.verified);
        assertEquals(2, result.specimens);
        assertEquals(Arrays.asList("Cote d'Ivoire"), result.manifest.get("countries_without_specimens"));
        assertTrue(result.text, result.text.contains("Check against the database: VERIFIED, every count equal\n"
            + "WARNING: AntWeb has no ant specimen for: Cote d'Ivoire (check that these are the countries meant)\n"));
        // format 8: the warning counts what the file can hold. A country with a beetle and no ant is still a warning
        // in an ants-only extract (the file has none of its rows), and none when every family is asked for.
        exec("insert into specimen (code, taxon_name, family, country) values ('beetle9', 'curculionidaex a', 'curculionidae', 'Cote d''Ivoire')");
        DataExtract.Result beetle = DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("Canada", "Cote d'Ivoire", "Brazil"), ""), SITE, null);
        assertEquals(Arrays.asList("Cote d'Ivoire"), beetle.manifest.get("countries_without_specimens"));
        assertEquals(2, beetle.specimens);
        DataExtract.Result everyFamily = DataExtract.run(connection, dir,
            new DataExtract.Request(Arrays.asList("Canada", "Cote d'Ivoire", "Brazil"), "", true, false), SITE, null);
        assertEquals(new ArrayList<>(), everyFamily.manifest.get("countries_without_specimens"));
        assertEquals(3, everyFamily.specimens);
        assertFalse(everyFamily.text, everyFamily.text.contains("WARNING"));
        exec("delete from specimen where code = 'beetle9'");
        // a country whose specimens are all under its old name is not empty
        exec("delete from specimen where country = 'Mexico'");
        exec("insert into specimen (code, taxon_name, family, country) values ('casent15', 'x', 'formicidae', 'Mexique')");
        DataExtract.Result mexico = DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("Mexico"), ""), SITE, null);
        assertEquals(1, mexico.specimens);
        assertEquals(new ArrayList<>(), mexico.manifest.get("countries_without_specimens"));
        assertFalse(mexico.text, mexico.text.contains("WARNING"));
    }

    /**
     * Review L1 (production has three specimens that say "Mexico" with an accent): the database finds every
     * spelling it takes for the same name, and a plain GROUP BY added them up while the file had them apart, so a
     * complete file was reported as different.
     */
    public void testTwoSpellingsOfACountryAreCountedApartAndVerified() throws Exception {
        String accent = "M" + (char) 0xe9 + "xico";
        exec("insert into specimen (code, taxon_name, family, subfamily, genus, country, bioregion) values"
            + " ('casent16', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', '" + accent + "', 'Nearctic'),"
            + " ('casent17', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', '" + accent + "', 'Nearctic'),"
            + " ('casent18', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', 'MEXICO', 'Nearctic')");
        DataExtract.Result result = DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("Mexico"), ""), SITE, null);
        assertTrue(result.text, result.verified);
        assertEquals(4, result.specimens);
        assertEquals("[{country=MEXICO, island_country=, specimens=1}, {country=Mexico, island_country=, specimens=1}, {country=" + accent
            + ", island_country=, specimens=2}]", result.manifest.get("by_place").toString());
        assertTrue(result.text, result.text.contains("Country spellings in the file besides the names asked for (MySQL matches names without accents): MEXICO, "
            + accent + "\n"));
        assertEquals(new ArrayList<>(), result.manifest.get("countries_without_specimens"));
        assertEquals("M" + (char) 0xe9 + "xico", DataExtract.unhex("4DC3A97869636F"));
    }

    /** Review M2: one statement stayed open for the whole extract, so a change of the table waited for all of it. */
    public void testTheSpecimensAreReadInPagesAndTheTableIsFreeBetweenThem() throws Exception {
        // the same file whatever the page size (2 rows a page: pages end inside and at the end of the list)
        DataExtract.run(connection, dir, withHawaii("Nearctic"), SITE, null, null, 2);
        byte[] small = gunzip(new File(dir, DataExtract.SPECIMENS));
        for (int pageRows : new int[] {1, 3, 5, 6, 10000}) {
            delete(dir);
            DataExtract.Result r = DataExtract.run(connection, dir, withHawaii("Nearctic"), SITE, null, null, pageRows);
            assertTrue(r.verified);
            assertEquals(5, r.specimens);
            assertTrue("page of " + pageRows, Arrays.equals(small, gunzip(new File(dir, DataExtract.SPECIMENS))));
        }
        // a code with a quote, a backslash and an accent is a page's last row: the next page starts after it
        exec("insert into specimen (code, taxon_name, family, country) values ('casent1''b\\\\c" + (char) 0xe9 + "', 'x', 'formicidae', 'Canada'),"
            + " ('casent1z', 'x', 'formicidae', 'Canada')");
        delete(dir);
        DataExtract.Result odd = DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("Canada"), ""), SITE, null, null, 1);
        assertTrue(odd.verified);
        assertEquals(Arrays.asList("casent1'b\\c" + (char) 0xe9, "casent1z", "casent4"), column(csv(DataExtract.SPECIMENS), "code"));

        // 12,100 more rows, 100 a page: at row 5,000 (the end of a page) another connection changes the table
        // (a comment: it needs the table for itself for a moment) and is not kept waiting
        bulk();
        delete(dir);
        List<String> changed = new ArrayList<>();
        DataExtract.Result result = DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("Canada"), ""), SITE, rows -> {
            if (rows != 5000) return;
            try (Connection other = connect(url()); Statement st = other.createStatement()) {
                st.execute("set session lock_wait_timeout = 5");
                long t0 = System.currentTimeMillis();
                st.executeUpdate("alter table specimen comment = 'changed during the extract'");
                changed.add("in " + (System.currentTimeMillis() - t0) + " ms");
            } catch (SQLException e) {
                changed.add("FAILED " + e);
            }
        }, null, 100);
        assertEquals(1, changed.size());
        assertTrue("the change of the table waited for the extract: " + changed, changed.get(0).startsWith("in "));
        assertTrue(result.verified);
        assertEquals(12103, result.specimens);
        List<String> codes = column(csv(DataExtract.SPECIMENS), "code");
        List<String> sorted = new ArrayList<>(new java.util.TreeSet<>(codes));
        assertEquals("no code twice", codes.size(), sorted.size());
    }

    private static byte[] gunzip(File f) throws Exception {
        try (java.io.InputStream in = new GZIPInputStream(Files.newInputStream(f.toPath()))) {
            return in.readAllBytes();
        }
    }

    private void bulk() throws SQLException {
        exec("create table if not exists extract_seq (n int primary key)");
        exec("delete from extract_seq");
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < 110; i++) values.append(i == 0 ? "" : ", ").append("(").append(i).append(")");
        exec("insert into extract_seq values " + values);
        exec("insert into specimen (code, taxon_name, family, subfamily, genus, country, bioregion, localityname, decimal_latitude, decimal_longitude)"
            + " select concat('bulk', lpad(a.n * 110 + b.n, 6, '0')), 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', 'Canada',"
            + " 'Nearctic', concat('site ', a.n, ', \"', b.n, '\"'), 45.5, -75.5 from extract_seq a, extract_seq b");
        exec("drop table if exists extract_seq");
    }

    /**
     * Brian Fisher, 2026-11-11: "An oceanic island or archipelago with its own ant fauna is its own country in
     * AntWeb. Its specimens never count toward the political parent." An extract of United States has no specimen
     * of Hawaii, in whichever of the two forms it is stored (country Hawaii, or still country United States with
     * the island in island_country), and the manifest names the island as left out. Named with the countries, the
     * island is in the file, in both forms.
     */
    public void testAnIslandIsNotPartOfItsPoliticalParent() throws Exception {
        // casent5 is stored as the island (country Hawaii); casent20 as before the move: under the parent, marked Hawaii
        exec("insert into specimen (code, taxon_name, family, subfamily, genus, country, island_country, bioregion) values"
            + " ('casent20', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', 'United States', 'Hawaii', 'Oceania')");
        DataExtract.Result result = DataExtract.run(connection, dir, three(""), SITE, null);
        assertTrue(result.text, result.verified);
        assertEquals(new ArrayList<>(), result.manifest.get("islands"));
        assertEquals(Arrays.asList("Hawaii"), result.manifest.get("islands_left_out"));
        assertEquals(Arrays.asList("casent1", "casent2", "casent3", "casent4"), column(csv(DataExtract.SPECIMENS), "code"));
        assertTrue(result.text, result.text.contains("Countries: United States, Canada, Mexico\nIslands included (named in the countries): none\n"
            + "Islands of these countries that are their own country in AntWeb, NOT in this file: Hawaii (name an island with the countries to include it)\n"));
        assertFalse(result.text, result.text.contains("Hawaii /") || result.text.contains("/ Hawaii"));
        assertEquals(new ArrayList<>(), result.manifest.get("countries_without_specimens"));

        // named with the countries: both forms are in the file, each counted under what it says
        delete(dir);
        DataExtract.Result named = DataExtract.run(connection, dir, withHawaii(""), SITE, null);
        assertTrue(named.text, named.verified);
        assertEquals(Arrays.asList("casent1", "casent2", "casent20", "casent3", "casent4", "casent5"), column(csv(DataExtract.SPECIMENS), "code"));
        assertEquals(Arrays.asList("Hawaii"), named.manifest.get("islands"));
        assertEquals(new ArrayList<>(), named.manifest.get("islands_left_out"));
        assertTrue(named.text, named.text.contains("  Hawaii / Hawaii                                   1\n"));
        assertTrue(named.text, named.text.contains("  United States / Hawaii                            1\n"));

        // the island alone: its specimens in both forms, nothing of the parent
        delete(dir);
        DataExtract.Result alone = DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("Hawaii"), ""), SITE, null);
        assertTrue(alone.text, alone.verified);
        assertEquals(Arrays.asList("casent20", "casent5"), column(csv(DataExtract.SPECIMENS), "code"));
        assertEquals(new ArrayList<>(), alone.manifest.get("countries_without_specimens"));

        // an island of another country: the one of Ecuador (casent7: country Ecuador, marked Galapagos Islands)
        exec("insert into geolocale values (13, 'Ecuador', 'country', 1, null, 0, 'South America', null, 'Neotropical', null)");
        exec("insert into specimen (code, taxon_name, family, subfamily, genus, country, bioregion) values"
            + " ('casent21', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', 'Ecuador', 'Neotropical')");
        delete(dir);
        DataExtract.Result ecuador = DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("Ecuador", "Canada"), ""), SITE, null);
        assertTrue(ecuador.text, ecuador.verified);
        assertEquals(Arrays.asList("casent21", "casent4"), column(csv(DataExtract.SPECIMENS), "code"));
        assertEquals(Arrays.asList("Galapagos Islands"), ecuador.manifest.get("islands_left_out"));
        // the islands left out are the ones of the countries asked for: Hawaii is no island of Ecuador or Canada
        assertFalse(ecuador.text, ecuador.text.contains("Hawaii"));
    }

    /**
     * Format 10: a specimen marked with an old name of an island (island_country "Galapagos": a country row that is
     * not valid and names the island as its valid name) is the island's: left out of the parent's extract, in the
     * island's. And a specimen with no country that is filed under an included island is in the countries file,
     * not a second time in the file of the specimens without a country.
     */
    public void testAnOldIslandNameIsTheIslandAndNoSpecimenIsInBothFiles() throws Exception {
        exec("insert into geolocale values (13, 'Ecuador', 'country', 1, null, 0, 'South America', null, 'Neotropical', null),"
            + " (14, 'Galapagos', 'country', 0, 'Galapagos Islands', 0, null, null, null, null),"
            + " (15, 'Archipielago de Colon', 'country', null, 'Galapagos Islands', 0, null, null, null, null)");
        exec("insert into specimen (code, taxon_name, family, subfamily, genus, country, island_country, bioregion) values"
            + " ('casent30', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', 'Ecuador', 'Galapagos', 'Neotropical'),"
            + " ('casent31', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', 'Ecuador', null, 'Neotropical'),"
            + " ('casent32', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', null, 'Galapagos Islands', 'Neotropical'),"
            + " ('casent33', 'myrmicinaestenamma diecki', 'formicidae', 'myrmicinae', 'stenamma', 'Galapagos', null, 'Neotropical')");
        DataExtract.Islands sets = DataExtract.islands(connection, Arrays.asList("Ecuador"));
        assertEquals("{Galapagos Islands=[Archipielago de Colon, Galapagos]}", sets.oldNames.toString());
        assertEquals(Arrays.asList("Galapagos Islands", "Archipielago de Colon", "Galapagos", "Hawaii"), sets.marked(sets.other));

        // Ecuador alone: nothing marked as the island's, under its name or an old one
        DataExtract.Result ecuador = DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("Ecuador"), "", false, true), SITE, null);
        assertTrue(ecuador.text, ecuador.verified);
        assertEquals(Arrays.asList("casent31"), column(csv(DataExtract.SPECIMENS), "code"));
        assertEquals(Arrays.asList("Galapagos Islands"), ecuador.manifest.get("islands_left_out"));
        assertEquals("{Galapagos Islands=[Archipielago de Colon, Galapagos]}", ecuador.manifest.get("old_island_names").toString());
        assertTrue(ecuador.text, ecuador.text.contains("Old island names read as the island (a specimen marked with one is the island's):"
            + " Archipielago de Colon, Galapagos = Galapagos Islands\n"));
        // no island is included, so the specimen with no country stays in the file of the specimens without one
        assertEquals(Arrays.asList("casent32", "casent8"), column(csv(DataExtract.SPECIMENS_NO_COUNTRY), "code"));

        // the island named: its specimens under its name, its old names and the parent; casent32 only in the countries file
        delete(dir);
        DataExtract.Result both = DataExtract.run(connection, dir,
            new DataExtract.Request(Arrays.asList("Ecuador", "Galapagos Islands"), "", false, true), SITE, null);
        assertTrue(both.text, both.verified);
        assertEquals(Arrays.asList("casent30", "casent31", "casent32", "casent33", "casent7"), column(csv(DataExtract.SPECIMENS), "code"));
        assertEquals(Arrays.asList("casent8"), column(csv(DataExtract.SPECIMENS_NO_COUNTRY), "code"));
        assertEquals(Arrays.asList("Galapagos Islands"), both.manifest.get("islands"));
        assertEquals(new ArrayList<>(), both.manifest.get("islands_left_out"));
        assertEquals("[{name=Archipielago de Colon, valid_name=Galapagos Islands}, {name=Galapagos, valid_name=Galapagos Islands}]",
            both.manifest.get("outdated_names").toString());
        assertEquals(new ArrayList<>(), both.manifest.get("countries_without_specimens"));
        assertEquals(1L, ((Map<?, ?>) both.manifest.get("no_country")).get("specimens"));
    }

    public void testNamesAreSpelledAsTheDatabaseSpellsThem() throws Exception {
        assertEquals(Arrays.asList("United States", "Cote d'Ivoire"), DataExtract.resolve(connection, Arrays.asList("united states", "COTE D'IVOIRE")));
        assertEquals("Nearctic", DataExtract.resolveBioregion(connection, " nearctic "));
        assertEquals("", DataExtract.resolveBioregion(connection, ""));
        // the islands of a request: the ones named, every other one, and of those the ones of the countries asked for
        DataExtract.Islands named = DataExtract.islands(connection, Arrays.asList("United States", "Hawaii"));
        assertEquals("[Hawaii] [Galapagos Islands] []", named.included + " " + named.other + " " + named.leftOut);
        DataExtract.Islands parent = DataExtract.islands(connection, Arrays.asList("United States"));
        assertEquals("[] [Galapagos Islands, Hawaii] [Hawaii]", parent.included + " " + parent.other + " " + parent.leftOut);
        // the form's list: each island with its political parent (the row's country, not the subregion it hangs under)
        assertEquals(Arrays.asList("Galapagos Islands (Ecuador)", "Hawaii (United States)"), DataExtractAction.islandCountries(connection));
        DataExtract.Islands elsewhere = DataExtract.islands(connection, Arrays.asList("Cote d'Ivoire"));
        assertEquals("[] [Galapagos Islands, Hawaii] [] {}", elsewhere.included + " " + elsewhere.other + " " + elsewhere.leftOut + " " + elsewhere.oldNames);
        // a quote in a name does not break the queries
        exec("insert into specimen (code, taxon_name, family, country) values ('casent10', 'myrmicinaestenamma diecki', 'formicidae', 'Cote d''Ivoire')");
        DataExtract.Result result = DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("cote d'ivoire"), "nearctic"), SITE, null);
        assertTrue(result.verified);
        assertEquals(1, result.specimens);
        assertEquals(Arrays.asList("Cote d'Ivoire"), result.manifest.get("countries"));
        assertEquals("Nearctic", result.manifest.get("bioregion"));
    }

    public void testTheConnectionComesBackAsItWasAndOnlyReads() throws Exception {
        connection.setAutoCommit(false);
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        DataExtract.run(connection, dir, withHawaii(""), SITE, null);
        assertFalse(connection.getAutoCommit());
        assertFalse(connection.isReadOnly());
        assertEquals(Connection.TRANSACTION_REPEATABLE_READ, connection.getTransactionIsolation());
        connection.setAutoCommit(true);
        // the session's own counters: the extract ran no statement that changes data or tables
        long before = writes();
        DataExtract.run(connection, dir, withHawaii("Nearctic"), SITE, null);
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

    public void testTheGuardsAreAskedBetweenThePhasesToo() throws Exception {
        // a small extract (fewer rows than one progress report) still asks the job's guards after its file: a run
        // past its time is stopped there, not in the unguarded counts that follow (review of #355)
        exec("insert into specimen (code, taxon_name, family, country) values ('casentguard1', 'myrmicinaestenamma diecki', 'formicidae', 'Mexico')");
        try {
            DataExtract.run(connection, dir, withHawaii(""), SITE, n -> { throw new DataExtract.Stopped("guard at " + n); });
            fail("the guards were not asked after the file");
        } catch (DataExtract.Stopped e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("guard at "));
        }
    }

    public void testAZeroDateDoesNotStopTheExtract() throws Exception {
        exec("set session sql_mode = ''");
        exec("insert into specimen (code, taxon_name, family, country, last_modified, day_entered) values"
            + " ('casent0', 'myrmicinaestenamma diecki', 'formicidae', 'Mexico', '0000-00-00 00:00:00', '0000-00-00')");
        DataExtract.Result result = DataExtract.run(connection, dir, withHawaii(""), SITE, null);
        assertTrue(result.verified);
        Map<String, String> row = csv(DataExtract.SPECIMENS).get(0);
        assertEquals("casent0", row.get("code"));
        assertEquals("0000-00-00 00:00:00", row.get("last_modified"));
        assertEquals("0000-00-00", row.get("day_entered"));
    }

    public void testManyRowsAreStreamedWithProgress() throws Exception {
        bulk();
        List<Long> reports = new ArrayList<>();
        DataExtract.Result result = DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("Canada"), ""), SITE, reports::add);
        assertTrue(result.verified);
        assertEquals(12101, result.specimens);
        // every 5,000 rows, then at each phase after the file (review of #355: the guards between the phases)
        assertEquals(Arrays.asList(5000L, 10000L), reports.subList(0, 2));
        assertTrue(reports.toString(), reports.size() >= 5 && reports.subList(2, reports.size()).stream().allMatch(n -> n == 12101L));
        List<Map<String, String>> rows = csv(DataExtract.SPECIMENS);
        assertEquals(12101, rows.size());
        assertEquals("bulk000000", rows.get(0).get("code"));
        assertEquals("site 0, \"0\"", rows.get(0).get("localityname"));
        assertEquals("casent4", rows.get(12100).get("code"));
        assertTrue(result.text, result.text.contains("Specimens in the file: 12,101\n"));
        assertTrue(result.text, result.text.contains(String.format("  %-40s %,10d\n", "Canada", 12101)));
        // a run stopped by its progress callback (the job's disk and time guards) ends with that message
        delete(dir);
        try {
            DataExtract.run(connection, dir, new DataExtract.Request(Arrays.asList("Canada"), ""), SITE, n -> {
                throw new DataExtract.Stopped("stopped at " + n);
            });
            fail("not stopped");
        } catch (DataExtract.Stopped e) {
            assertEquals("stopped at 5000", e.getMessage());
        }
        // nothing that looks finished: only the part file the job then removes
        assertEquals("[specimens.csv.gz.part]", Arrays.toString(dir.list()));
        assertFalse(connection.isReadOnly());
    }
}
