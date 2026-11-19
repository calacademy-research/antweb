package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import junit.framework.TestCase;

import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.home.TaxonDb;
import org.calacademy.antweb.upload.CurrentValidNameRule;

/**
 * TaxonDb.brief (2026-11-19, the specimen upload's light lookup) gives the status, current valid name and rank that
 * getTaxon gives for the same name: a valid species, a synonym with its current valid name, a current valid name equal
 * to the name in another case ("", as a Taxon holds), a genus, a name with no row, a name with a quote. And the upload's
 * lookup (CurrentValidNameRule.uploadLookup) answers with them. MySQL 8.4 in CI; ANTWEB_TEST_DB_URL overrides.
 */
public class TaxonBriefDbTest extends TestCase {

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        connection = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        connection.setAutoCommit(true);
        exec("drop table if exists taxon");
        exec("drop table if exists taxon_prop");
        exec("create table taxon (taxon_name varchar(128) primary key, taxarank varchar(32), kingdom_name varchar(32),"
            + " phylum_name varchar(32), order_name varchar(32), class_name varchar(32), family varchar(64), subfamily varchar(64),"
            + " tribe varchar(64), genus varchar(64), subgenus varchar(64), species varchar(64), subspecies varchar(64),"
            + " status varchar(32), access_group int, source varchar(64), line_num int, insert_method varchar(64), created timestamp null,"
            + " fossil tinyint default 0, type tinyint default 0, antcat tinyint default 0, antcat_id int, pending tinyint default 0,"
            + " author_date varchar(128), author_date_html varchar(256), authors varchar(128), year varchar(8), available tinyint default 0,"
            + " current_valid_name varchar(128), current_valid_rank varchar(32), current_valid_parent varchar(128),"
            + " original_combination tinyint default 0, was_original_combination varchar(128), parent_taxon_name varchar(128),"
            + " image_count int default 0, hol_id int, chart_color varchar(16))");
        exec("create table taxon_prop (taxon_name varchar(128), prop varchar(64), value text, primary key (taxon_name, prop))");
        row("formicinaecamponotus hova", "species", "hova", "valid", null);
        row("formicinaecamponotus oldname", "species", "oldname", "synonym", "formicinaecamponotus hova");
        row("formicinaecamponotus samecase", "species", "samecase", "valid", "FormicinaeCamponotus Samecase");
        row("myrmicinaecardiocondyla wroughtoni", "species", "wroughtoni", "synonym", "myrmicinaecardiocondyla wroughtonii");
        exec("insert into taxon (taxon_name, taxarank, subfamily, genus, status) values ('formicinaecamponotus', 'genus', 'formicinae', 'camponotus', 'valid')");
    }

    private void row(String name, String rank, String species, String status, String currentValidName) throws SQLException {
        exec("insert into taxon (taxon_name, taxarank, family, subfamily, genus, species, status, current_valid_name) values ('"
            + name + "', '" + rank + "', 'formicidae', 'formicinae', 'camponotus', '" + species + "', '" + status + "', "
            + (currentValidName == null ? "null" : "'" + currentValidName + "'") + ")");
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) {
            exec("drop table if exists taxon");
            exec("drop table if exists taxon_prop");
            connection.close();
        }
    }

    private void exec(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    public void testSameAsGetTaxon() throws SQLException {
        String[] names = {"formicinaecamponotus hova", "formicinaecamponotus oldname", "formicinaecamponotus samecase",
            "myrmicinaecardiocondyla wroughtoni", "formicinaecamponotus", "formicinaecamponotus nosuchname"};
        TaxonDb taxonDb = new TaxonDb(connection);
        CurrentValidNameRule.Lookup lookup = CurrentValidNameRule.uploadLookup(connection);
        for (String name : names) {
            Taxon full = taxonDb.getTaxon(name);
            TaxonDb.Brief brief = TaxonDb.brief(connection, name);
            CurrentValidNameRule.TaxonRow row = lookup.getTaxon(name);
            if (full == null) {
                assertNull(name, brief);
                assertNull(name, row);
                continue;
            }
            assertNotNull(name, brief);
            assertEquals(name, full.getStatus(), brief.status);
            assertEquals(name, full.getCurrentValidName(), brief.currentValidName);
            assertEquals(name, full.getRank(), brief.rank);
            assertEquals(name, brief.status, row.status);
            assertEquals(name, brief.currentValidName, row.currentValidName);
            assertEquals(name, brief.rank, row.rank);
        }
        assertEquals("", TaxonDb.brief(connection, "formicinaecamponotus samecase").currentValidName);   // same name, other case
        assertEquals("", TaxonDb.brief(connection, "formicinaecamponotus hova").currentValidName);       // none: "" as a Taxon
        assertEquals("formicinaecamponotus hova", TaxonDb.brief(connection, "formicinaecamponotus oldname").currentValidName);
        assertNull(TaxonDb.brief(connection, "formicinaecamponotus o'brien"));
        assertNull(TaxonDb.brief(connection, null));
    }
}
