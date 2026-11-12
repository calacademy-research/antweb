package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.calacademy.antweb.Source;
import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.home.GeolocaleTaxonDb;
import org.calacademy.antweb.home.SpeciesListUploadDb;
import org.calacademy.antweb.upload.AntcatLocality;
import org.calacademy.antweb.util.GeolocaleMgr;

/**
 * The AntCat rows of an island's political parent (2026-11-12), against a database, with the statements the daily
 * AntCat import runs.
 *
 * Production, 2026-11-12, after the islands' specimens were moved: Ecuador's list still held 19 subspecies described
 * from the Galapagos with no specimen, source AntCat (AntCat's locality "Ecuador (Galapagos Is)", filed under the text
 * before the parenthesis). Brian Fisher, 2026-11-11: an oceanic island with its own ant fauna is its own country and
 * never counts toward the political parent; Ecuador's list has no Galapagos-only species.
 *
 * What the test shows: (1) the import, as it was, files such a name under Ecuador; (2) each daily import first
 * deletes every list row whose source is AntCat (SpeciesListUploadDb.prepareDatabase), a curator's and a specimen row
 * stay; (3) it then files each name under the country AntcatLocality gives: the island only, so the rows of (1) are
 * gone from Ecuador after one import, with no removal of its own; (4) the next day's import gives the same lists.
 * The place rows are production's (2026-11-11).
 *
 * In the schema ant_islandtest, made in setUp and dropped in tearDown (IslandCountryDbTest uses the same name; CI runs
 * the classes one after the other and grants the login that schema). Nothing in the schema the address names is read
 * or written. MySQL 8.4 in CI (host "mysql"); ANTWEB_TEST_DB_URL overrides.
 */
public class AntcatIslandRowsDbTest extends TestCase {

    static final String SCHEMA = "ant_islandtest";

    static final int ECUADOR = 103, GALAPAGOS = 1721, US = 97, HAWAII = 423;
    static final String SUB_A = "formicinaecamponotus macilentus albemarlensis";   // "Ecuador (Galapagos Is)", no specimen
    static final String SUB_B = "formicinaecamponotus planus fidelis";             // the same, on no list but AntCat's
    static final String SPECIES = "formicinaecamponotus macilentus";               // collected on the Galapagos
    static final String MAINLAND = "formicinaecamponotus ecuadorensis";            // "Ecuador (Pichincha)"
    static final String CURATOR = "myrmicinaepheidole curatoria";                  // on Ecuador's list by a curator
    static final String HAWAIIAN = "ponerinaeponera swezeyi";                      // "Hawaii (Oahu)"

    /** AntCat's export: the name and its type locality (the "country" column). */
    static final String[][] ANTCAT = {
        {SUB_A, "Ecuador (Galapagos Is)"}, {SUB_B, "Ecuador (Galapagos Is)"}, {SPECIES, "Ecuador (Galapagos Is)"},
        {MAINLAND, "Ecuador (Pichincha)"}, {HAWAIIAN, "Hawaii (Oahu)"},
    };

    private Connection connection;
    private final Map<String, Country> countries = new LinkedHashMap<>();
    private final MultiKeyMap<String, Adm1> adm1s = new MultiKeyMap<>();
    private final List<Adm1> adm1Rows = new ArrayList<>();

    private void country(int id, String name, String politicalParent, boolean island, boolean valid, String validName) {
        Country c = new Country();
        c.setId(id);
        c.setName(name);
        c.setGeorank("country");
        c.setCountry(politicalParent);
        c.setIsIsland(island);
        c.setIsValid(valid);
        c.setValidName(validName);
        countries.put(name, c);
    }

    private void adm1(int id, String name, String parent, String validName) {
        Adm1 a = new Adm1();
        a.setId(id);
        a.setName(name);
        a.setGeorank("adm1");
        a.setParent(parent);
        a.setIsValid(false);
        a.setValidName(validName);
        adm1s.put(name, parent, a);
        adm1Rows.add(a);
    }

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        connection = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        connection.setAutoCommit(true);
        try {
            exec("drop database if exists " + SCHEMA);     // left by a run that stopped in setUp
            exec("create database " + SCHEMA);
        } catch (SQLException e) {
            connection.close();
            connection = null;
            throw new SQLException("AntcatIslandRowsDbTest works in its own schema and the login may not make it. As root: grant all on "
                + SCHEMA + ".* to '<the login>'@'%'  (" + e.getMessage() + ")", e);
        }
        connection.setCatalog(SCHEMA);
        assertEquals("the test's own schema", java.util.Collections.singletonList(SCHEMA), column("select database()"));

        // the tables the import's first step empties, with the columns it and the list rows use
        exec("create table taxon (taxon_name varchar(128) primary key, antcat tinyint default 0)");
        exec("create table homonym (taxon_name varchar(128), antcat tinyint default 0)");
        exec("create table description_edit (taxon_name varchar(128), title varchar(64))");
        exec("create table description_homonym (taxon_name varchar(128), title varchar(64))");
        exec("create table proj_taxon (project_name varchar(64), taxon_name varchar(128), source varchar(40))");
        exec("create table geolocale_taxon (geolocale_id int not null, taxon_name varchar(128) not null, source varchar(40),"
            + " insert_method varchar(40), rev int default 0, is_introduced tinyint default 0, is_endemic tinyint default 0,"
            + " created timestamp default current_timestamp, primary key (geolocale_id, taxon_name))");
        exec("create table geolocale_taxon_dispute (geolocale_id int, taxon_name varchar(128), source varchar(40), rev int default 0,"
            + " curator_id int, created timestamp default current_timestamp)");

        country(US, "United States", null, false, true, "none");
        country(ECUADOR, "Ecuador", null, false, true, null);
        country(HAWAII, "Hawaii", "United States", true, true, "none");
        country(GALAPAGOS, "Galapagos Islands", "Ecuador", true, true, null);
        country(1732, "Galapagos", "Galapagos", true, false, "Galapagos Islands");
        country(8864, "Galápagos", null, true, false, "Galapagos Islands");
        adm1(620, "Galapagos", "Ecuador", "Galapagos Islands");
        adm1(2394, "Galápagos", "Ecuador", "Galapagos Islands");
        adm1(1017, "Oahu", "United States", "Hawaii");

        // rows that are not AntCat's: the species collected on the Galapagos (from specimens), a curator's entry on
        // Ecuador's list, and a curator's entry of one subspecies on the island's list
        row(GALAPAGOS, SPECIES, "specimen");
        row(ECUADOR, CURATOR, "curator");
        row(GALAPAGOS, SUB_A, "curator");
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) {
            exec("drop database if exists " + SCHEMA);     // every table the test made, and nothing else
            connection.close();
        }
    }

    private void exec(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) { s.executeUpdate(sql); }
    }

    private List<String> column(String sql) throws SQLException {
        List<String> list = new ArrayList<>();
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            while (r.next()) list.add(r.getString(1));
        }
        return list;
    }

    private void row(int geolocaleId, String taxon, String source) throws SQLException {
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (" + geolocaleId + ", '" + taxon + "', '" + source + "')");
    }

    /** Expected rows, in the order list() gives them. */
    private static List<String> rows(String... rows) {
        List<String> list = new ArrayList<>(Arrays.asList(rows));
        java.util.Collections.sort(list);
        return list;
    }

    /** A list: "name:source", by name. */
    private List<String> list(int geolocaleId) throws SQLException {
        List<String> list = column("select concat(taxon_name, ':', source) from geolocale_taxon where geolocale_id = " + geolocaleId);
        java.util.Collections.sort(list);
        return list;
    }

    /** The import's filing of each name of the export, with the country the given rule finds for its locality. */
    private void file(boolean asBefore) throws SQLException {
        GeolocaleTaxonDb lists = new GeolocaleTaxonDb(connection);
        for (String[] line : ANTCAT) {
            Country country = asBefore
                ? GeolocaleMgr.validCountry(countries, AntcatLocality.countryPart(line[1]))      // the text before the parenthesis
                : AntcatLocality.listCountry(countries, adm1s, adm1Rows, line[1]);
            assertNotNull(line[1], country);
            lists.setTaxonSet(country, line[0], Source.ANTCAT);
        }
    }

    /** One daily import: every AntCat row deleted (prepareDatabase), then filed again. */
    private void dailyImport(boolean asBefore) throws SQLException {
        new SpeciesListUploadDb(connection).prepareDatabase("worldants");
        file(asBefore);
    }

    public void testTheParentsAntcatRowsLeaveWithTheNextImport() throws Exception {
        // (1) as it was: the names described from the Galapagos are on Ecuador's list, source AntCat
        dailyImport(true);
        assertEquals(rows(SUB_A + ":antcat", MAINLAND + ":antcat", SPECIES + ":antcat", SUB_B + ":antcat", CURATOR + ":curator"),
            list(ECUADOR));
        assertEquals("the island has only what specimens and a curator gave it",
            rows(SUB_A + ":curator", SPECIES + ":specimen"), list(GALAPAGOS));
        assertEquals(rows(HAWAIIAN + ":antcat"), list(HAWAII));
        assertTrue(list(US).isEmpty());

        // (2) the import's first step, each day: every list row from AntCat goes; the others stay
        new SpeciesListUploadDb(connection).prepareDatabase("worldants");
        assertEquals(rows(CURATOR + ":curator"), list(ECUADOR));
        assertEquals(rows(SUB_A + ":curator", SPECIES + ":specimen"), list(GALAPAGOS));
        assertTrue(list(HAWAII).isEmpty());

        // (3) and files them again, now under the island only: Ecuador keeps its mainland name and the curator's
        file(false);
        assertEquals(rows(MAINLAND + ":antcat", CURATOR + ":curator"), list(ECUADOR));
        // on the island: AntCat's source replaces the curator's (as on every list), not the specimens'
        assertEquals(rows(SUB_A + ":antcat", SPECIES + ":specimen", SUB_B + ":antcat"), list(GALAPAGOS));
        assertEquals(rows(HAWAIIAN + ":antcat"), list(HAWAII));
        assertTrue(list(US).isEmpty());

        // (4) the next day: the same lists
        List<String> ecuador = list(ECUADOR), galapagos = list(GALAPAGOS), hawaii = list(HAWAII);
        dailyImport(false);
        assertEquals(ecuador, list(ECUADOR));
        assertEquals(rows(MAINLAND + ":antcat", CURATOR + ":curator"), list(ECUADOR));
        assertEquals(hawaii, list(HAWAII));
        // the curator's subspecies row became AntCat's in (3), so it is deleted and filed again like the others
        assertEquals(galapagos, list(GALAPAGOS));
        assertEquals("no name is on both lists", 0, column("select a.taxon_name from geolocale_taxon a join geolocale_taxon b"
            + " on b.taxon_name = a.taxon_name where a.geolocale_id = " + ECUADOR + " and b.geolocale_id = " + GALAPAGOS).size());
    }

    /** The import deletes rows of the sources antcat and proxyantcat on every list, and no other row. */
    public void testTheDailyDeleteTakesAntcatAndProxyRowsOnly() throws Exception {
        row(ECUADOR, SUB_A, "antcat");
        row(3, SUB_A, "proxyantcat");            // the subregion's row, written with the country's
        row(ECUADOR, MAINLAND, "specimen");
        row(ECUADOR, SUB_B, "literature");
        row(US, HAWAIIAN, "antcat");
        new SpeciesListUploadDb(connection).prepareDatabase("worldants");
        assertEquals(rows(MAINLAND + ":specimen", SUB_B + ":literature", CURATOR + ":curator"), list(ECUADOR));
        assertTrue(list(3).isEmpty());
        assertTrue(list(US).isEmpty());
        assertEquals(rows(SUB_A + ":curator", SPECIES + ":specimen"), list(GALAPAGOS));
    }
}
