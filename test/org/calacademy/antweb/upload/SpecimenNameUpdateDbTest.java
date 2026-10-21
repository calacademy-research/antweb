package test.org.calacademy.antweb.upload;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import junit.framework.TestCase;

import org.calacademy.antweb.home.CuratorTaxonSetDb;
import org.calacademy.antweb.upload.SpecimenNameUpdate;
import org.calacademy.antweb.upload.SpecimenNameUpdateDb;

/**
 * The nightly name update against a database (2026-10): the queries, the specimen update, the
 * specimen_name_update rows, the maxChanges cap, and the parked curator list entries (PR #180).
 *
 * Runs in CI (.github/workflows/tests.yaml) in the antweb image against a MySQL 8.4 container
 * reachable as host "mysql" (database ant, user antweb), like CuratorTaxonSetTest.
 * ANTWEB_TEST_DB_URL overrides the URL. It creates its own small taxon, homonym, specimen and
 * description_edit tables (the last holds AntCat's taxonomic history, which gives each move its reason).
 */
public class SpecimenNameUpdateDbTest extends TestCase {

    static final String OLD = "formicinaetapinoma indicum";
    static final String NEW = "dolichoderinaeravavy indicus";
    static final String TO_HOMONYM = "myrmicinaecarebara tohom";
    static final String HOM_NAME = "myrmicinaecarebara silvestrii";
    static final String MORPHO = "formicinaecamponotus mg01";
    static final String VALID_AGAIN = "myrmicinaepheidole again";
    static final String OTHER = "myrmicinaepheidole other";
    static final String LOST = "ponerinaehypoponera lost";
    static final int CAS = 1;
    static final int JTL = 7;

    private Connection connection;

    static Connection connect() throws SQLException {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        String user = System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb");
        String password = System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME");
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    protected void setUp() throws Exception {
        connection = connect();
        connection.setAutoCommit(true);
        try (Statement s = connection.createStatement()) {
            for (String t : new String[] {"specimen", "taxon", "homonym", "description_edit"}) s.executeUpdate("drop table if exists " + t);
            s.executeUpdate("create table taxon (taxon_name varchar(128) not null primary key, taxarank varchar(20),"
                + " status varchar(40), current_valid_name varchar(128), insert_method varchar(30), family varchar(64),"
                + " subfamily varchar(64), tribe varchar(64), genus varchar(64), subgenus varchar(64), species varchar(64),"
                + " subspecies varchar(64), parent_taxon_name varchar(128), antcat_id int, author_date varchar(128))");
            s.executeUpdate("create table description_edit (taxon_name varchar(128) not null, title varchar(128), content mediumtext,"
                + " code varchar(128), is_manual_entry tinyint, author_date varchar(128))");
            s.executeUpdate("create table homonym (taxon_name varchar(128) not null, author_date varchar(128))");
            s.executeUpdate("create table specimen (code varchar(128) not null primary key, taxon_name varchar(128),"
                + " original_taxon_name varchar(128), access_group int, status varchar(40), family varchar(64), subfamily varchar(64),"
                + " tribe varchar(64), genus varchar(64), subgenus varchar(64), species varchar(64), subspecies varchar(64),"
                + " bioregion varchar(64), is_introduced tinyint default 0, country varchar(64), adm1 varchar(64),"
                + " island_country varchar(64), museum varchar(64), upload_id int)");
            // The application creates these once per run (ensureSchema); here they are emptied.
            s.executeUpdate(SpecimenNameUpdateDb.createTable(null, null));
            s.executeUpdate("delete from specimen_name_update");
            s.executeUpdate(CuratorTaxonSetDb.createTable(null, null));
            s.executeUpdate("delete from taxon_set_parked");
        }
        taxon(OLD, "species", "synonym", NEW, null, "formicinae", "tapinoma", "indicum", null);
        taxon(NEW, "species", "valid", null, null, "dolichoderinae", "ravavy", "indicus", null);
        taxon(TO_HOMONYM, "species", "synonym", HOM_NAME, null, "myrmicinae", "carebara", "tohom", null);
        exec("insert into homonym values ('" + HOM_NAME + "', 'Forel, 1905')");
        taxon(HOM_NAME, "species", "unrecognized", null, SpecimenNameUpdate.PLACEHOLDER_INSERT_METHOD, "myrmicinae", "carebara", "silvestrii", null);
        taxon(MORPHO, "species", "morphotaxon", null, null, "formicinae", "camponotus", "mg01", null);
        taxon(VALID_AGAIN, "species", "valid", null, null, "myrmicinae", "pheidole", "again", null);
        taxon(OTHER, "species", "synonym", "myrmicinaepheidole third", null, "myrmicinae", "pheidole", "other", null);
        taxon(LOST, "species", "synonym", "ponerinaehypoponera nowhere", null, "ponerinae", "hypoponera", "lost", null);
        // AntCat ids and taxonomic histories, as the AntCat import stores them.
        exec("update taxon set antcat_id = 101 where taxon_name = '" + OLD + "'");
        exec("update taxon set antcat_id = 102 where taxon_name = '" + NEW + "'");
        exec("update taxon set antcat_id = 103 where taxon_name = '" + VALID_AGAIN + "'");
        exec("insert into description_edit (taxon_name, title, content, is_manual_entry) values ('" + OLD + "', 'taxonomichistory',"
            + " '<p><b>Taxonomic history</b></p><div><div>Combination in <a href=\"https://www.antcat.org/catalog/102\"><i>Ravavy</i></a>:"
            + " <a href=\"https://antcat.org/references/1\">Fisher &amp; Bolton, 2026a</a> <a href=\"x.pdf\">PDF</a>: 12</div></div>', 0)");
        exec("insert into description_edit (taxon_name, title, content, is_manual_entry) values ('" + VALID_AGAIN + "', 'taxonomichistory',"
            + " '<p><b>Taxonomic history</b></p><div><div>Junior synonym of <i>Pheidole other</i>: Wilson, 2003 PDF: 5.</div>"
            + "<div>Status as species: Longino, 2019 PDF: 9; Fisher, 2025b: 3.</div></div>', 0)");

        // Uploaded as OLD when it was valid (2 CAS specimens, 1 JTL): now a synonym of NEW.
        specimen("casent0000001", OLD, null, CAS);
        specimen("casent0000002", OLD, null, CAS);
        specimen("jtl0000001", OLD, null, JTL);
        // Uploaded as VALID_AGAIN when it was a synonym of OTHER; VALID_AGAIN is valid again.
        specimen("casent0000003", OTHER, VALID_AGAIN, CAS);
        // Already moved at upload: nothing to do.
        specimen("casent0000004", NEW, OLD, CAS);
        // Left alone: homonym target, placeholder, morpho, valid name without taxon row.
        specimen("casent0000005", TO_HOMONYM, null, CAS);
        specimen("casent0000006", HOM_NAME, TO_HOMONYM, CAS);
        specimen("casent0000007", MORPHO, null, CAS);
        specimen("casent0000008", LOST, null, CAS);
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    private void exec(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) { s.executeUpdate(sql); }
    }

    private static String q(String v) { return v == null ? "null" : "'" + v + "'"; }

    private void taxon(String name, String rank, String status, String cvn, String insertMethod, String subfamily, String genus, String species, String subspecies) throws SQLException {
        exec("insert into taxon (taxon_name, taxarank, status, current_valid_name, insert_method, family, subfamily, genus, species, subspecies, parent_taxon_name)"
            + " values (" + q(name) + ", " + q(rank) + ", " + q(status) + ", " + q(cvn) + ", " + q(insertMethod) + ", 'formicidae', "
            + q(subfamily) + ", " + q(genus) + ", " + q(species) + ", " + q(subspecies) + ", " + q(subfamily + genus) + ")");
    }

    private void specimen(String code, String taxonName, String original, int group) throws SQLException {
        exec("insert into specimen (code, taxon_name, original_taxon_name, access_group, status, subfamily, genus, species, bioregion)"
            + " values (" + q(code) + ", " + q(taxonName) + ", " + q(original) + ", " + group + ", 'valid', 'x', 'x', 'x', 'Malagasy')");
    }

    private String one(String sql) throws SQLException {
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getString(1) : null;
        }
    }

    private int count(String sql) throws SQLException {
        return Integer.parseInt(one(sql));
    }

    private String nameOf(String code) throws SQLException {
        return one("select taxon_name from specimen where code = '" + code + "'");
    }

    public void testReportChangesNothing() throws Exception {
        SpecimenNameUpdate.Summary s = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.REPORT, 50000);
        assertEquals(SpecimenNameUpdate.WOULD_CHANGE, s.rowMode);
        assertEquals("3 synonym + 1 resurrected", 4, s.specimens);
        assertEquals(2, s.byGroup.size());
        assertEquals(OLD, nameOf("casent0000001"));
        assertEquals(OTHER, nameOf("casent0000003"));
        assertEquals(4, count("select count(*) from specimen_name_update where mode = 'would_change'"));
        assertEquals(1, count("select count(*) from specimen_name_update where code = 'jtl0000001' and access_group = 7"
            + " and uploaded_name = '" + OLD + "' and old_name = '" + OLD + "' and new_name = '" + NEW + "' and antcat_status = 'synonym'"));
        // Why, from AntCat's taxonomic history (2026-10-20): Tapinoma indicum -> Ravavy indicus changes the
        // genus and only the gender ending, a new combination; the specimen uploaded as Pheidole again goes back.
        assertEquals("new combination: Ravavy indicus (Fisher & Bolton 2026a); spelling as in AntCat",
            one("select reason from specimen_name_update where code = 'jtl0000001'"));
        assertEquals("101", one("select antcat_id from specimen_name_update where code = 'jtl0000001'"));
        assertEquals("Your name Pheidole again is valid again. It had been shown under P. other (synonymy by Wilson 2003),"
            + " and was revived as a species by Longino 2019.",
            one("select reason from specimen_name_update where code = 'casent0000003'"));
        assertEquals("103", one("select antcat_id from specimen_name_update where code = 'casent0000003'"));
        assertTrue(s.line(), s.line().startsWith("name update: 4 specimens in 2 groups (report: would change)"));

        // A second preview replaces the first.
        SpecimenNameUpdate.run(connection, SpecimenNameUpdate.REPORT, 50000);
        assertEquals(4, count("select count(*) from specimen_name_update"));
    }

    public void testApplyMovesSpecimensAndKeepsTheUploadedName() throws Exception {
        SpecimenNameUpdate.run(connection, SpecimenNameUpdate.REPORT, 50000);
        SpecimenNameUpdate.Summary s = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000);
        assertEquals(SpecimenNameUpdate.CHANGED, s.rowMode);
        assertEquals(4, s.specimens);

        assertEquals(NEW, nameOf("casent0000001"));
        assertEquals(NEW, nameOf("jtl0000001"));
        assertEquals("Uploaded As", OLD, one("select original_taxon_name from specimen where code = 'casent0000001'"));
        assertEquals("valid", one("select status from specimen where code = 'casent0000001'"));
        assertEquals("name columns from the valid taxon", "dolichoderinae ravavy indicus",
            one("select concat(subfamily, ' ', genus, ' ', species) from specimen where code = 'casent0000001'"));
        assertEquals("formicidae", one("select family from specimen where code = 'casent0000001'"));
        assertNull(one("select subspecies from specimen where code = 'casent0000001'"));

        assertEquals("resurrected: back on the uploaded name", VALID_AGAIN, nameOf("casent0000003"));
        assertNull(one("select original_taxon_name from specimen where code = 'casent0000003'"));

        assertEquals("left alone", TO_HOMONYM, nameOf("casent0000005"));
        assertEquals(HOM_NAME, nameOf("casent0000006"));
        assertEquals(MORPHO, nameOf("casent0000007"));
        assertEquals(LOST, nameOf("casent0000008"));
        assertEquals(NEW, nameOf("casent0000004"));

        assertEquals("the preview is gone, the changes recorded", 0, count("select count(*) from specimen_name_update where mode = 'would_change'"));
        assertEquals(4, count("select count(*) from specimen_name_update where mode = 'changed'"));
        assertTrue(s.skipped.get(SpecimenNameUpdate.Outcome.HOMONYM) >= 1);
        assertEquals(1, (int) s.skipped.get(SpecimenNameUpdate.Outcome.PLACEHOLDER));
        assertEquals("a morpho on its own name is not even a candidate", 0, (int) s.skipped.get(SpecimenNameUpdate.Outcome.MORPHO_OR_INDET));
        assertEquals(1, (int) s.skipped.get(SpecimenNameUpdate.Outcome.NO_TARGET));

        // A second run finds nothing more, and keeps the history.
        SpecimenNameUpdate.Summary again = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000);
        assertEquals(0, again.specimens);
        assertEquals(4, count("select count(*) from specimen_name_update where mode = 'changed'"));

        // The page's queries.
        SpecimenNameUpdateDb db = new SpecimenNameUpdateDb(connection);
        assertEquals(1, db.getRuns(0, null, null, 30).size());
        assertEquals(2, db.getLines(CAS, db.getRuns(CAS, null, null, 30)).size());
        assertEquals("a curator sees only the group's lines", 1, db.getLines(JTL, db.getRuns(JTL, null, null, 30)).size());
        assertEquals(0, db.getRuns(JTL, "2001-01-01", "2001-01-02", 30).size());
        assertEquals(2, db.getGroupIds().size());
        SpecimenNameUpdateDb.Line jtl = db.getLines(JTL, db.getRuns(JTL, null, null, 30)).get(0);
        assertEquals("new combination: Ravavy indicus (Fisher & Bolton 2026a); spelling as in AntCat", jtl.reason);
        assertEquals(Integer.valueOf(101), jtl.antcatId);
        assertEquals(OLD, jtl.uploadedName);
    }

    /**
     * A name shared by two AntCat taxa (Pachycondyla sauteri: Wheeler, 1906, now Cryptopone sauteri; Forel,
     * 1912, now Ectomomyrmex sauteri): not moved, listed for the curator, replaced at the next run.
     */
    public void testSharedNameIsHeldForTheCurator() throws Exception {
        String obs = "ponerinaepachycondyla sauteri";
        String valid = "ponerinaecryptopone sauteri";
        taxon(obs, "species", "obsolete combination", valid, null, "ponerinae", "pachycondyla", "sauteri", null);
        taxon(valid, "species", "valid", null, null, "ponerinae", "cryptopone", "sauteri", null);
        specimen("casent0000020", obs, null, CAS);
        org.calacademy.antweb.upload.SharedNames shared = org.calacademy.antweb.upload.SharedNames.fromExport(new java.io.StringReader(
            "antcat id\tsubfamily\tgenus\tspecies\tsubspecies\tauthor date\tstatus\tcurrent valid name\n"
            + "509406\tPonerinae\tPachycondyla\tsauteri\t\tWheeler, 1906\tobsolete combination\tPonerinae Cryptopone sauteri\n"
            + "509563\tPonerinae\tPachycondyla\tsauteri\t\tForel, 1912\tobsolete combination\tPonerinae Ectomomyrmex sauteri\n"));

        SpecimenNameUpdate.Summary s = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000, shared);
        assertEquals("not moved", obs, nameOf("casent0000020"));
        assertEquals(4, s.specimens);
        assertEquals(1, s.sharedSpecimens);
        assertEquals(1, (int) s.skipped.get(SpecimenNameUpdate.Outcome.SHARED_NAME));
        assertTrue(s.line(), s.line().contains("for the curator to confirm (shared name): 1 specimens"));
        assertEquals("name is shared by two taxa; please confirm the identification: Cryptopone sauteri or Ectomomyrmex sauteri",
            one("select reason from specimen_name_update where code = 'casent0000020' and mode = 'shared_name'"));

        SpecimenNameUpdateDb db = new SpecimenNameUpdateDb(connection);
        java.util.List<SpecimenNameUpdateDb.Line> held = db.getShared(CAS);
        assertEquals(1, held.size());
        assertEquals(obs, held.get(0).oldName);
        assertEquals("casent0000020", held.get(0).codes.get(0));
        assertTrue("other groups do not see it", db.getShared(JTL).isEmpty());
        for (SpecimenNameUpdateDb.Run run : db.getRuns(0, null, null, 30)) assertFalse("not a run of moves", SpecimenNameUpdate.SHARED.equals(run.mode));

        // The next run replaces the list.
        SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000, shared);
        assertEquals(1, count("select count(*) from specimen_name_update where mode = 'shared_name'"));
        // Without the shared names (AntCat no longer has two taxa of the name), it moves.
        SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000, org.calacademy.antweb.upload.SharedNames.none());
        assertEquals(valid, nameOf("casent0000020"));
        assertEquals(0, count("select count(*) from specimen_name_update where mode = 'shared_name'"));
    }

    /**
     * Brian Fisher, 2026-10-20: the specimen upload report and the Name updates page share one code path
     * for names of two or more AntCat taxa (SharedNameReport). For the same name, against the same tables:
     * the upload's rule would move Pachycondyla sauteri to the one taxon the table holds, the upload keeps it
     * and writes its line; the nightly run holds it and the page writes its line. The two are identical.
     */
    public void testUploadAndPageGiveTheSameSharedNameLine() throws Exception {
        String obs = "ponerinaepachycondyla sauteri";
        String valid = "ponerinaecryptopone sauteri";
        taxon(obs, "species", "obsolete combination", valid, null, "ponerinae", "pachycondyla", "sauteri", null);
        taxon(valid, "species", "valid", null, null, "ponerinae", "cryptopone", "sauteri", null);
        specimen("casent0000021", obs, null, CAS);
        org.calacademy.antweb.upload.SharedNames shared = org.calacademy.antweb.upload.SharedNames.fromExport(new java.io.StringReader(
            "antcat id\tsubfamily\tgenus\tspecies\tsubspecies\tauthor date\tstatus\tcurrent valid name\n"
            + "509406\tPonerinae\tPachycondyla\tsauteri\t\tWheeler, 1906\tobsolete combination\tPonerinae Cryptopone sauteri\n"
            + "509563\tPonerinae\tPachycondyla\tsauteri\t\tForel, 1912\tobsolete combination\tPonerinae Ectomomyrmex sauteri\n"));

        // The upload: what SpecimenUploadProcess.setStatusAndCurrentValidName() asks.
        org.calacademy.antweb.upload.CurrentValidNameRule.Result rule = org.calacademy.antweb.upload.CurrentValidNameRule.decide(
            obs, org.calacademy.antweb.upload.CurrentValidNameRule.plainLookup(connection));
        assertEquals("the rule alone would move it", valid, rule.validTaxonName);
        String upload = org.calacademy.antweb.upload.SharedNameReport.uploadLine(obs, rule, shared);
        assertNotNull("kept for the curator", upload);

        // The nightly run and the page.
        SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000, shared);
        assertEquals("not moved", obs, nameOf("casent0000021"));
        String page = org.calacademy.antweb.curate.NameUpdatesAction.sharedHtml(new SpecimenNameUpdateDb(connection).getShared(CAS), "", shared);
        String li = page.substring(page.indexOf("<li class=\"name-update-shared\">") + "<li class=\"name-update-shared\">".length());
        assertEquals("the same text in the upload report and on nameUpdates.do", upload, li.substring(0, li.indexOf(" <details")));
        assertTrue(upload, upload.contains("<a href=\"https://www.antcat.org/catalog/509563\"><i>Pachycondyla sauteri</i> Forel, 1912</a>, obsolete combination, now <i>Ectomomyrmex sauteri</i>"));

        // Without the export, both read the homonym table the same way (SharedNames.load's fallback).
        org.calacademy.antweb.upload.SharedNames fromTable = org.calacademy.antweb.upload.SharedNames.fromHomonymTable(connection);
        assertNotNull("Carebara silvestrii: a placeholder row and a homonym", fromTable.candidatesOf("Carebara silvestrii"));
        assertEquals(2, fromTable.taxaOf("Carebara silvestrii").size());
        assertEquals("Forel, 1905", fromTable.taxaOf("Carebara silvestrii").get(0).authorDate);
    }

    /**
     * Filed under a name AntCat calls valid while the uploaded name now points elsewhere (Brian Fisher,
     * 2026-10-20: Camponotus gigas filed under Dinomyrmex gigas): not moved, listed per run as kept_valid
     * with the uploaded name, the name it is filed under, where the uploaded name points, and the specimens.
     * A specimen already where its uploaded name points (casent0000004) is not listed.
     */
    public void testKeptOnAValidNameIsListed() throws Exception {
        String gigas = "formicinaecamponotus gigas";
        String cruentatus = "formicinaecamponotus cruentatus";
        String dino = "formicinaedinomyrmex gigas";
        taxon(gigas, "species", "synonym", cruentatus, null, "formicinae", "camponotus", "gigas", null);
        taxon(cruentatus, "species", "valid", null, null, "formicinae", "camponotus", "cruentatus", null);
        taxon(dino, "species", "valid", null, null, "formicinae", "dinomyrmex", "gigas", null);
        specimen("casent0000030", dino, gigas, CAS);
        specimen("casent0000031", dino, gigas, CAS);
        specimen("jtl0000030", dino, gigas, JTL);

        SpecimenNameUpdate.Summary s = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000);
        assertEquals("not moved", dino, nameOf("casent0000030"));
        assertEquals(dino, nameOf("jtl0000030"));
        assertEquals("the moves are as before", 4, s.specimens);
        assertEquals(3, s.keptValidSpecimens);
        assertEquals("one name per group", 2, (int) s.skipped.get(SpecimenNameUpdate.Outcome.CURRENT_VALID));
        assertEquals(3, count("select count(*) from specimen_name_update where mode = 'kept_valid'"));
        assertEquals(0, count("select count(*) from specimen_name_update where mode = 'kept_valid' and code = 'casent0000004'"));
        assertEquals(1, count("select count(*) from specimen_name_update where mode = 'kept_valid' and code = 'jtl0000030'"
            + " and uploaded_name = '" + gigas + "' and old_name = '" + dino + "' and new_name = '" + cruentatus + "'"));

        SpecimenNameUpdateDb db = new SpecimenNameUpdateDb(connection);
        java.util.List<SpecimenNameUpdateDb.Line> cas = db.getKeptValid(CAS);
        assertEquals(1, cas.size());
        assertEquals(dino, cas.get(0).oldName);
        assertEquals(gigas, cas.get(0).uploadedName);
        assertEquals(cruentatus, cas.get(0).newName);
        assertEquals(2, cas.get(0).count);
        assertEquals("all groups", 2, db.getKeptValid(0).size());
        assertEquals("a curator sees the group's own", 1, db.getKeptValid(JTL).size());
        for (SpecimenNameUpdateDb.Run run : db.getRuns(0, null, null, 30)) assertFalse("not a run of moves", SpecimenNameUpdate.KEPT_VALID.equals(run.mode));
        String html = org.calacademy.antweb.curate.NameUpdatesAction.keptValidHtml(db.getKeptValid(0), "");
        assertTrue(html, html.contains("(2 names)</summary>"));

        // Two uploaded names on one valid name: two lines.
        String gigasLatr = "formicinaecamponotus gigantea";
        taxon(gigasLatr, "species", "synonym", cruentatus, null, "formicinae", "camponotus", "gigantea", null);
        specimen("casent0000032", dino, gigasLatr, CAS);
        SpecimenNameUpdate.run(connection, SpecimenNameUpdate.REPORT, 50000);
        assertEquals("replaced at each run", 4, count("select count(*) from specimen_name_update where mode = 'kept_valid'"));
        assertEquals(2, db.getKeptValid(CAS).size());
        assertEquals("the changed rows are kept", 4, count("select count(*) from specimen_name_update where mode = 'changed'"));
        try {
            db.deleteMode(SpecimenNameUpdate.CHANGED);
            fail("the history cannot be deleted this way");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    /**
     * The upload report's "Recognized invalid species" line (UploadNameReasons): the same reason as the
     * nightly name update for the same pair, read from the same tables; plain "old -> new" when they cannot be read.
     */
    public void testUploadReportReasonIsTheNameUpdateReason() throws Exception {
        SpecimenNameUpdate.run(connection, SpecimenNameUpdate.REPORT, 50000);
        String nightly = one("select reason from specimen_name_update where code = 'jtl0000001'");
        org.calacademy.antweb.upload.UploadNameReasons reasons = org.calacademy.antweb.upload.UploadNameReasons.of(connection);
        String line = reasons.line("old -> new", OLD, NEW);
        assertEquals("old -> new: <span class=\"name-update-reason\">new combination: <i>Ravavy indicus</i>"
            + " (<a href=\"https://www.antcat.org/catalog/101\">Fisher &amp; Bolton 2026a</a>); spelling as in AntCat</span>", line);
        assertEquals("new combination: Ravavy indicus (Fisher & Bolton 2026a); spelling as in AntCat", nightly);

        // The tables cannot be read (connection closed): the plain line, and the upload goes on.
        Connection closed = connect();
        closed.close();
        org.calacademy.antweb.upload.UploadNameReasons broken = org.calacademy.antweb.upload.UploadNameReasons.of(closed);
        assertEquals("old -> new", broken.line("old -> new", OLD, NEW));
    }

    /** A table made by the first version of the step (2026-10-20 morning) gets reason and antcat_id. */
    public void testOldTableGetsTheReasonColumns() throws Exception {
        exec("drop table if exists specimen_name_update_old");
        exec(SpecimenNameUpdateDb.createTable(null, null).replace("specimen_name_update", "specimen_name_update_old")
            .replace(" reason varchar(500) NULL,", "").replace(" antcat_id int NULL,", ""));
        assertEquals(0, count("select count(*) from information_schema.columns where table_schema = database()"
            + " and table_name = 'specimen_name_update_old' and column_name in ('reason', 'antcat_id')"));
        exec(SpecimenNameUpdateDb.ADD_REASON.replace("specimen_name_update", "specimen_name_update_old"));
        assertEquals(2, count("select count(*) from information_schema.columns where table_schema = database()"
            + " and table_name = 'specimen_name_update_old' and column_name in ('reason', 'antcat_id')"));
        exec("drop table specimen_name_update_old");
    }

    public void testMaxChangesStopsApply() throws Exception {
        SpecimenNameUpdate.Summary s = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 3);
        assertTrue(s.capped);
        assertEquals(SpecimenNameUpdate.WOULD_CHANGE, s.rowMode);
        assertEquals("nothing changed", OLD, nameOf("casent0000001"));
        assertEquals(4, count("select count(*) from specimen_name_update where mode = 'would_change'"));
        assertTrue(s.line(), s.line().contains("above maxChanges=3"));
    }

    public void testOffDoesNothing() throws Exception {
        SpecimenNameUpdate.Summary s = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.OFF, 50000);
        assertNull(s.rowMode);
        assertEquals(0, count("select count(*) from specimen_name_update"));
        assertEquals(OLD, nameOf("casent0000001"));
    }

    public void testParkedListEntriesFollowTheValidName() throws Exception {
        // A curator's row parked by a cleanup under a name that is now a synonym (PR #180).
        exec("insert into taxon_set_parked (list_type, list_key, taxon_name, source) values ('geolocale', '7', '" + OLD + "', 'speciesListTool')");
        exec("insert into taxon_set_parked (list_type, list_key, taxon_name, source) values ('project', 'madants', '" + OLD + "', 'curator')");
        exec("insert into taxon_set_parked (list_type, list_key, taxon_name, source) values ('project', 'madants', '" + NEW + "', 'curator')");
        SpecimenNameUpdate.Summary report = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.REPORT, 50000);
        assertEquals(2, report.parkedRenamed);
        assertEquals("report changes nothing", 2, count("select count(*) from taxon_set_parked where taxon_name = '" + OLD + "'"));

        SpecimenNameUpdate.Summary s = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000);
        assertEquals(2, s.parkedRenamed);
        assertEquals(0, count("select count(*) from taxon_set_parked where taxon_name = '" + OLD + "'"));
        assertEquals(1, count("select count(*) from taxon_set_parked where list_type = 'geolocale' and taxon_name = '" + NEW + "'"));
        assertEquals("one row per list and name", 1, count("select count(*) from taxon_set_parked where list_type = 'project' and taxon_name = '" + NEW + "'"));
    }

    public void testUploadRunningMeansNoRun() throws Exception {
        org.calacademy.antweb.upload.UploadAction.setIsInUploadProcess("test upload");
        try {
            SpecimenNameUpdate.Summary s = SpecimenNameUpdate.runLocked(connection, SpecimenNameUpdate.APPLY, 50000);
            assertNull(s.rowMode);
            assertTrue(s.line(), s.line().contains("an upload is running"));
            assertEquals(OLD, nameOf("casent0000001"));
        } finally {
            org.calacademy.antweb.upload.UploadAction.setIsInUploadProcess(null);
        }
        SpecimenNameUpdate.Summary s = SpecimenNameUpdate.runLocked(connection, SpecimenNameUpdate.REPORT, 50000);
        assertEquals(4, s.specimens);
        assertFalse("the lock is released", org.calacademy.antweb.upload.UploadAction.isInUploadProcess());
    }
}
