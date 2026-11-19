package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import junit.framework.TestCase;

import org.calacademy.antweb.Caste;
import org.calacademy.antweb.Rank;
import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.home.TaxonDb;

/**
 * The species of a genus read at once (TaxonDb.speciesOfGenus, 2026-11-18, for images.do of large genera) give the
 * same taxa as getSpecies and getSubspecies one by one: names, status, author, default specimens and introduced map;
 * a synonym only where no other name exists; nothing (the caller asks getSpecies) where two names are not synonyms.
 * MySQL 8.4 in CI (host "mysql"); ANTWEB_TEST_DB_URL overrides.
 */
public class SpeciesOfGenusDbTest extends TestCase {

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
        row("formicinaecamponotus hova", "species", "hova", null, "valid", "Forel, 1891");
        row("formicinaecamponotus hova radamae", "subspecies", "hova", "radamae", "valid", "Forel, 1891");
        row("formicinaecamponotus maculatus", "species", "maculatus", null, "valid", "Fabricius, 1782");
        row("formicinaecamponotus oldname", "species", "oldname", null, "synonym", "Smith, 1858");
        // two names of one species, one a synonym: the other one
        row("formicinaecamponotus christi", "species", "christi", null, "valid", "Forel, 1886");
        exec("insert into taxon (taxon_name, taxarank, subfamily, genus, species, status) values"
            + " ('formicinaecamponotus christi2', 'species', 'formicinae', 'camponotus', 'christi', 'synonym')");
        // two names that are not synonyms: left to getSpecies (which throws)
        row("formicinaecamponotus twice", "species", "twice", null, "valid", "A");
        exec("insert into taxon (taxon_name, taxarank, subfamily, genus, species, status) values"
            + " ('formicinaecamponotus twice2', 'species', 'formicinae', 'camponotus', 'twice', 'morphotaxon')");
        // another genus whose name starts the same: not read
        exec("insert into taxon (taxon_name, taxarank, subfamily, genus, species, status) values"
            + " ('formicinaecamponotusx hova', 'species', 'formicinae', 'camponotusx', 'hova', 'valid')");
        exec("insert into taxon_prop values ('formicinaecamponotus hova', 'workerSpecimen', 'casent0001'),"
            + " ('formicinaecamponotus hova', 'queenSpecimen', 'casent0002'), ('formicinaecamponotus hova', 'introducedMap', 'Malagasy'),"
            + " ('formicinaecamponotus hova radamae', 'maleSpecimen', 'casent0003'),"
            + " ('formicinaecamponotusx hova', 'workerSpecimen', 'casent9999')");
    }

    private void row(String name, String rank, String species, String subspecies, String status, String author) throws SQLException {
        exec("insert into taxon (taxon_name, taxarank, family, subfamily, genus, species, subspecies, status, author_date, parent_taxon_name)"
            + " values ('" + name + "', '" + rank + "', 'formicidae', 'formicinae', 'camponotus', '" + species + "', "
            + (subspecies == null ? "null" : "'" + subspecies + "'") + ", '" + status + "', '" + author + "', 'formicinaecamponotus')");
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

    private static String describe(Taxon t) {
        if (t == null) return "null";
        return t.getTaxonName() + " | " + t.getRank() + " | " + t.getStatus() + " | " + t.getAuthorDate() + " | "
            + t.getDefaultSpecimen(Caste.WORKER) + " " + t.getDefaultSpecimen(Caste.QUEEN) + " " + t.getDefaultSpecimen(Caste.MALE)
            + " | " + t.getIntroducedMap() + " | " + t.getParentTaxonName();
    }

    public void testSameAsOneByOne() throws Exception {
        TaxonDb db = new TaxonDb(connection);
        TaxonDb.GenusSpecies all = db.speciesOfGenus("formicinae", "camponotus");
        for (String sp : new String[] {"hova", "maculatus", "oldname", "christi"}) {
            assertEquals(sp, describe(db.getSpecies("formicinae", "camponotus", sp)), describe(all.get(Rank.SPECIES, sp, null)));
        }
        assertEquals(describe(db.getSubspecies("formicinae", "camponotus", "hova", "radamae")),
            describe(all.get(Rank.SUBSPECIES, "hova", "radamae")));
        assertEquals("formicinaecamponotus christi", all.get(Rank.SPECIES, "christi", null).getTaxonName());
        assertNull("two names that are not synonyms: getSpecies decides", all.get(Rank.SPECIES, "twice", null));
        assertNull(all.get(Rank.SPECIES, "nosuch", null));
        assertNotNull("keys are case-blind", all.get(Rank.SPECIES, "HOVA", null));
    }

    public void testNothingWithoutAGenus() throws Exception {
        assertEquals(0, new TaxonDb(connection).speciesOfGenus(null, "camponotus").size());
        assertEquals(0, new TaxonDb(connection).speciesOfGenus("formicinae", "nosuchgenus").size());
    }
}
