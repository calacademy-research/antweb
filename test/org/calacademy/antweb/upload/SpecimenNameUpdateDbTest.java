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
            for (String t : new String[] {"specimen", "taxon", "homonym", "description_edit", "genus_concept_rule"}) s.executeUpdate("drop table if exists " + t);
            s.executeUpdate("create table taxon (taxon_name varchar(128) not null primary key, taxarank varchar(20),"
                + " status varchar(40), current_valid_name varchar(128), insert_method varchar(30), family varchar(64),"
                + " subfamily varchar(64), tribe varchar(64), genus varchar(64), subgenus varchar(64), species varchar(64),"
                + " subspecies varchar(64), parent_taxon_name varchar(128), antcat_id int, author_date varchar(128),"
                + " source varchar(64), access_group int)");
            s.executeUpdate("create table description_edit (taxon_name varchar(128) not null, title varchar(128), content mediumtext,"
                + " code varchar(128), is_manual_entry tinyint, author_date varchar(128))");
            s.executeUpdate("create table homonym (taxon_name varchar(128) not null, author_date varchar(128))");
            s.executeUpdate("create table specimen (code varchar(128) not null primary key, taxon_name varchar(128),"
                + " original_taxon_name varchar(128), access_group int, status varchar(40), family varchar(64), subfamily varchar(64),"
                + " tribe varchar(64), genus varchar(64), subgenus varchar(64), species varchar(64), subspecies varchar(64),"
                + " bioregion varchar(64), is_introduced tinyint default 0, country varchar(64), adm1 varchar(64),"
                + " island_country varchar(64), museum varchar(64), upload_id int, flag varchar(20))");
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
        assertEquals(4, count("select count(*) from specimen_name_update where mode = 'would_change'"));
        assertEquals("the curator's list, replaced too", 1, count("select count(*) from specimen_name_update where mode = 'shared_name'"));
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
        // casent0000005: its valid name is a homonym (a placeholder taxon); casent0000006 is already on the placeholder.
        // One category since 2026-10-20: held for the curator, not a separate "homonym" or "placeholder" count.
        assertEquals(1, (int) s.skipped.get(SpecimenNameUpdate.Outcome.SHARED_NAME));
        assertEquals(1, count("select count(*) from specimen_name_update where mode = 'shared_name' and code = 'casent0000005'"));
        assertEquals("its current valid name, Carebara silvestrii, is shared by two or more AntCat taxa; please confirm the identification",
            one("select reason from specimen_name_update where code = 'casent0000005'"));
        assertEquals(0, count("select count(*) from specimen_name_update where code = 'casent0000006'"));
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
        assertEquals("and casent0000005 (valid name only a homonym)", 2, s.sharedSpecimens);
        assertEquals(2, (int) s.skipped.get(SpecimenNameUpdate.Outcome.SHARED_NAME));
        assertTrue(s.line(), s.line().contains("for the curator to confirm (shared name): 2 specimens"));
        assertEquals("name is shared by two taxa; please confirm the identification: Cryptopone sauteri or Ectomomyrmex sauteri",
            one("select reason from specimen_name_update where code = 'casent0000020' and mode = 'shared_name'"));

        SpecimenNameUpdateDb db = new SpecimenNameUpdateDb(connection);
        java.util.List<SpecimenNameUpdateDb.Line> held = db.getShared(CAS);
        assertEquals(2, held.size());
        assertEquals(obs, held.get(1).oldName);
        assertEquals("casent0000020", held.get(1).codes.get(0));
        assertTrue("other groups do not see it", db.getShared(JTL).isEmpty());
        for (SpecimenNameUpdateDb.Run run : db.getRuns(0, null, null, 30)) assertFalse("not a run of moves", SpecimenNameUpdate.SHARED.equals(run.mode));

        // The next run replaces the list.
        SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000, shared);
        assertEquals(2, count("select count(*) from specimen_name_update where mode = 'shared_name'"));
        // Without the shared names (AntCat no longer has two taxa of the name), it moves.
        SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000, org.calacademy.antweb.upload.SharedNames.none());
        assertEquals(valid, nameOf("casent0000020"));
        assertEquals(0, count("select count(*) from specimen_name_update where mode = 'shared_name' and code = 'casent0000020'"));
    }

    /**
     * Brian Fisher, 2026-10-20 preview: "Wasmannia auropunctata -> Allomerus auropunctatus" was shown twice under
     * one group (1 and 42 specimens). Rows that show the same old -> new pair in one group are one line, the
     * counts added; specimens with another reason keep it on that line. Another group, or another run, is apart.
     */
    public void testOneLinePerPairAndGroup() throws Exception {
        exec("insert into specimen_name_update (run_date, mode, code, access_group, uploaded_name, old_name, new_name, antcat_status, reason, antcat_id) values"
            + " ('2026-10-20 05:20:00', 'would_change', 'mg0001', 9, 'myrmicinaewasmannia auropunctata', 'myrmicinaewasmannia auropunctata', 'myrmicinaeallomerus auropunctatus', 'obsolete combination', 'new combination: Allomerus auropunctatus (X 2026)', 11),"
            + " ('2026-10-20 05:20:00', 'would_change', 'mg0002', 9, 'myrmicinaewasmannia auropunctata', 'myrmicinaewasmannia auropunctata', 'myrmicinaeallomerus auropunctatus', 'obsolete combination', 'new combination: Allomerus auropunctatus (X 2026)', 11),"
            + " ('2026-10-20 05:20:00', 'would_change', 'mg0003', 9, 'formicinaewasmannia auropunctata', 'formicinaewasmannia auropunctata', 'myrmicinaeallomerus auropunctatus', 'obsolete combination', 'new combination: Allomerus auropunctatus (X 2026)', 11),"
            + " ('2026-10-20 05:20:00', 'would_change', 'mg0004', 9, 'myrmicinaeallomerus auropunctatus', 'myrmicinaewasmannia auropunctata', 'myrmicinaeallomerus auropunctatus', 'synonym', 'Your name Allomerus auropunctatus is valid again.', 12),"
            + " ('2026-10-20 05:20:00', 'would_change', 'abs0001', 10, 'myrmicinaewasmannia auropunctata', 'myrmicinaewasmannia auropunctata', 'myrmicinaeallomerus auropunctatus', 'obsolete combination', 'new combination: Allomerus auropunctatus (X 2026)', 11)");
        SpecimenNameUpdateDb db = new SpecimenNameUpdateDb(connection);
        java.util.List<SpecimenNameUpdateDb.Run> runs = db.getRuns(0, null, null, 30);
        java.util.List<SpecimenNameUpdateDb.Line> lines = db.getLines(0, runs);
        assertEquals("one line per group", 2, lines.size());
        SpecimenNameUpdateDb.Line mg = lines.get(0);
        assertEquals(9, mg.groupId);
        assertEquals("counts added", 4, mg.count);
        assertEquals(java.util.List.of("mg0003", "mg0001", "mg0002", "mg0004"), mg.codes);
        assertEquals("new combination: Allomerus auropunctatus (X 2026)", mg.reason);
        assertEquals(java.util.List.of("Your name Allomerus auropunctatus is valid again."), mg.moreReasons);
        assertEquals(1, lines.get(1).count);

        String html = org.calacademy.antweb.curate.NameUpdatesAction.linesHtml(runs, lines, "");
        assertEquals(2, html.split("class=\"name-update\"", -1).length - 1);
        assertTrue(html, html.contains(">4 specimens</summary>"));
        assertTrue(html, html.contains(">X 2026</a>)</span>; <span class=\"name-update-reason\">Your name"));
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
        String li = page.substring(page.indexOf("<li class=\"name-update-shared\">" + org.calacademy.antweb.Taxon.makeDescriptionLink(obs)) + "<li class=\"name-update-shared\">".length());
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

    private void history(String taxonName, String text) throws SQLException {
        exec("insert into description_edit (taxon_name, title, content, is_manual_entry) values ('" + taxonName + "', 'taxonomichistory',"
            + " '<p><b>Taxonomic history</b></p><div><div>" + text + "</div></div>', 0)");
    }

    /**
     * Brian Fisher, 2026-11-03: a morphospecies or indet filed under a synonym genus moves to the valid genus with
     * its code, the uploaded name kept, the reason from the old genus's AntCat history; its taxon row is made in
     * apply mode only. A morphospecies in a valid genus, and one in a synonym genus whose valid genus has no row, stay.
     */
    public void testGenusSynonymMovesMorphospeciesAndIndets() throws Exception {
        taxon("dorylinaephyracaces", "genus", "synonym", "dorylinaelioponera", null, "dorylinae", "phyracaces", null, null);
        taxon("dorylinaelioponera", "genus", "valid", null, null, "dorylinae", "lioponera", null, null);
        taxon("myrmicinaewasmannia", "genus", "synonym", "Allomerus", null, "myrmicinae", "wasmannia", null, null);
        taxon("myrmicinaeallomerus", "genus", "valid", null, null, "myrmicinae", "allomerus", null, null);
        taxon("myrmicinaelostgenus", "genus", "synonym", "myrmicinaenowhere", null, "myrmicinae", "lostgenus", null, null);
        taxon("formicinaecamponotus", "genus", "valid", null, null, "formicinae", "camponotus", null, null);
        taxon("dorylinaephyracaces (indet)", "species", "indetermined", null, "specimenUpload", "dorylinae", "phyracaces", "(indet)", null);
        exec("update taxon set antcat_id = 201, tribe = 'lioponerini' where taxon_name = 'dorylinaephyracaces'");
        exec("update taxon set tribe = 'lioponerini' where taxon_name = 'dorylinaelioponera'");
        history("dorylinaephyracaces", "Junior synonym of <i>Cerapachys</i>: Brown, 1975 PDF: 4.</div><div>Junior synonym of <i>Lioponera</i>: Borowiec, 2016: 18.");
        history("myrmicinaewasmannia", "Junior synonym of <i>Allomerus</i>: Longino &amp; Branstetter, 2026 PDF: 12.");
        specimen("fmnhins0000118007", "dorylinaephyracaces (indet)", null, 21);
        specimen("alw0000002", "myrmicinaewasmannia alw02", null, 21);
        specimen("casent0000040", "myrmicinaelostgenus (indet)", null, CAS);

        SpecimenNameUpdate.Summary report = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.REPORT, 50000);
        assertEquals("4 as before + 2", 6, report.specimens);
        assertEquals(2, report.genusSpecimens);
        assertEquals("report changes nothing", "dorylinaephyracaces (indet)", nameOf("fmnhins0000118007"));
        assertEquals("no taxon row made in report mode", 0, count("select count(*) from taxon where taxon_name = 'dorylinaelioponera (indet)'"));
        assertEquals("genus synonym: Phyracaces is a junior synonym of Lioponera (Borowiec 2016)",
            one("select reason from specimen_name_update where code = 'fmnhins0000118007' and mode = 'would_change'"));
        assertEquals("201", one("select antcat_id from specimen_name_update where code = 'fmnhins0000118007'"));
        assertEquals("dorylinaelioponera (indet)", one("select new_name from specimen_name_update where code = 'fmnhins0000118007'"));
        assertEquals("synonym", one("select antcat_status from specimen_name_update where code = 'fmnhins0000118007'"));

        SpecimenNameUpdate.Summary s = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000);
        assertEquals(6, s.specimens);
        assertEquals(2, s.taxaCreated);
        assertTrue(s.line(), s.line().contains("of these, by genus synonym or missing subfamily: 2 (2 morphospecies/indet taxa added)"));
        assertEquals("dorylinaelioponera (indet)", nameOf("fmnhins0000118007"));
        assertEquals("Uploaded As", "dorylinaephyracaces (indet)", one("select original_taxon_name from specimen where code = 'fmnhins0000118007'"));
        assertEquals("indetermined", one("select status from specimen where code = 'fmnhins0000118007'"));
        assertEquals("dorylinae lioponerini lioponera (indet)",
            one("select concat(subfamily, ' ', tribe, ' ', genus, ' ', species) from specimen where code = 'fmnhins0000118007'"));
        assertEquals("myrmicinaeallomerus alw02", nameOf("alw0000002"));
        assertEquals("morphotaxon", one("select status from specimen where code = 'alw0000002'"));
        // The taxon rows, as the upload makes a morphospecies/indet row.
        assertEquals("species indetermined formicidae dorylinae lioponera (indet) dorylinaelioponera genusSynonymMove 21",
            one("select concat_ws(' ', taxarank, status, family, subfamily, genus, species, parent_taxon_name, insert_method, access_group)"
                + " from taxon where taxon_name = 'dorylinaelioponera (indet)'"));
        assertEquals("morphotaxon", one("select status from taxon where taxon_name = 'myrmicinaeallomerus alw02'"));
        assertEquals("genus synonym: Wasmannia is a junior synonym of Allomerus (Longino & Branstetter 2026)",
            one("select reason from specimen_name_update where code = 'alw0000002' and mode = 'changed'"));

        assertEquals("a morphospecies in a valid genus stays", MORPHO, nameOf("casent0000007"));
        assertEquals("valid genus has no row: stays", "myrmicinaelostgenus (indet)", nameOf("casent0000040"));
        assertEquals(1, (int) s.skipped.get(SpecimenNameUpdate.Outcome.NO_VALID_GENUS));

        SpecimenNameUpdate.Summary again = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000);
        assertEquals("nothing more, and not moved back", 0, again.specimens);
        assertEquals("dorylinaelioponera (indet)", nameOf("fmnhins0000118007"));
        assertEquals(0, again.taxaCreated);
    }

    /**
     * Brian Fisher, 2026-11-03: names filed without a subfamily ("(formicidae)pyramica ludovici", SAM-HYM-C003105).
     * A combination AntCat has goes to its current valid name; a morphospecies or indet to the valid genus or, in a
     * valid genus, to its subfamily; a genus AntCat does not have (a misspelling) and a combination AntCat does not
     * have are left and listed, replaced each run; once AntCat has the combination, it moves.
     */
    public void testNoSubfamilyPlaceholders() throws Exception {
        taxon("myrmicinaepyramica", "genus", "synonym", "myrmicinaestrumigenys", null, "myrmicinae", "pyramica", null, null);
        taxon("myrmicinaestrumigenys", "genus", "valid", null, null, "myrmicinae", "strumigenys", null, null);
        taxon("myrmicinaepyramica ludovici", "species", "obsolete combination", "myrmicinaestrumigenys ludovici", null, "myrmicinae", "pyramica", "ludovici", null);
        taxon("myrmicinaestrumigenys ludovici", "species", "valid", null, null, "myrmicinae", "strumigenys", "ludovici", null);
        taxon("myrmicinaestrumigenys emmae", "species", "valid", null, null, "myrmicinae", "strumigenys", "emmae", null);
        // The upload's placeholder rows (status morphotaxon by the parentheses).
        taxon("(formicidae)pyramica", "genus", "unrecognized", null, "addMissingGenus", "(formicidae)", "pyramica", null, null);
        taxon("(formicidae)pyramica ludovici", "species", "morphotaxon", null, "specimenUpload", "(formicidae)", "pyramica", "ludovici", null);
        exec("update taxon set antcat_id = 301 where taxon_name = 'myrmicinaestrumigenys ludovici'");
        exec("update taxon set antcat_id = 302 where taxon_name = 'myrmicinaestrumigenys'");
        history("myrmicinaestrumigenys ludovici", "Combination in <i>Pyramica</i>: Bolton, 1999 PDF: 1673.</div><div>Combination in <i>Strumigenys</i>: Baroni Urbani &amp; De Andrade, 2007 PDF: 128.");
        specimen("sam-hym-c003105", "(formicidae)pyramica ludovici", null, 66);
        specimen("sam-hym-c000001", "(formicidae)pyramica (indet)", null, 66);
        specimen("sam-hym-c000002", "(formicidae)strumigenys mg01", null, 66);
        specimen("sam-hym-c000003", "(formicidae)stumigenys ludovici", null, 66);
        specimen("sam-hym-c000004", "(formicidae)pyramica emmae", null, 66);
        specimen("sam-hym-c000005", "(formicidae)pyramica emmae", null, 81);

        SpecimenNameUpdate.Summary s = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000);
        assertEquals(3, s.genusSpecimens);
        assertEquals("myrmicinaestrumigenys ludovici", nameOf("sam-hym-c003105"));
        assertEquals("Uploaded As", "(formicidae)pyramica ludovici", one("select original_taxon_name from specimen where code = 'sam-hym-c003105'"));
        assertEquals("valid", one("select status from specimen where code = 'sam-hym-c003105'"));
        assertEquals("new combination: Strumigenys ludovici (Baroni Urbani & De Andrade 2007)",
            one("select reason from specimen_name_update where code = 'sam-hym-c003105' and mode = 'changed'"));
        assertEquals("myrmicinaestrumigenys (indet)", nameOf("sam-hym-c000001"));
        assertEquals("indetermined", one("select status from taxon where taxon_name = 'myrmicinaestrumigenys (indet)'"));
        assertEquals("myrmicinaestrumigenys mg01", nameOf("sam-hym-c000002"));
        assertEquals("placed in its subfamily: Strumigenys is in Myrmicinae (the upload gave no subfamily)",
            one("select reason from specimen_name_update where code = 'sam-hym-c000002' and mode = 'changed'"));

        // Left and listed: a misspelled genus (never corrected by guess) ...
        assertEquals("(formicidae)stumigenys ludovici", nameOf("sam-hym-c000003"));
        assertEquals(1, s.noGenusSpecimens);
        assertEquals(1, count("select count(*) from specimen_name_update where mode = 'no_genus' and code = 'sam-hym-c000003'"));
        // ... and a combination AntCat does not have, with the senior genus (never moved by guess into it).
        assertEquals("(formicidae)pyramica emmae", nameOf("sam-hym-c000004"));
        assertEquals(2, s.noCombinationSpecimens);
        assertEquals("myrmicinaestrumigenys", one("select new_name from specimen_name_update where mode = 'no_combo' and code = 'sam-hym-c000004'"));
        assertTrue(s.line(), s.line().contains("combination not in AntCat, for the AntCat editors: 2 specimens"));
        assertTrue(s.line(), s.line().contains("genus not in AntCat (no subfamily), for the curator: 1 specimens"));
        SpecimenNameUpdateDb db = new SpecimenNameUpdateDb(connection);
        java.util.List<String[]> forAntcat = db.getCombinationsForAntcat();
        assertEquals(1, forAntcat.size());
        assertEquals("Pyramica|emmae|Strumigenys|2|66,81", String.join("|", forAntcat.get(0)));
        assertEquals(1, db.getNoGenus(66).size());
        assertTrue("other groups do not see it", db.getNoGenus(81).isEmpty());
        for (SpecimenNameUpdateDb.Run run : db.getRuns(0, null, null, 30)) {
            assertFalse("not a run of moves", SpecimenNameUpdate.NO_GENUS.equals(run.mode) || SpecimenNameUpdate.NO_COMBINATION.equals(run.mode));
        }
        String html = org.calacademy.antweb.curate.NameUpdatesAction.noCombinationHtml(db.getNoCombination(0), forAntcat, "");
        assertTrue(html, html.contains("Not moved: combination not in AntCat (2 names)"));
        assertTrue(html, html.contains("<td><i>Pyramica</i></td><td>emmae</td><td><i>Strumigenys</i></td><td>2</td><td>66,81</td>"));
        assertTrue(html, html.contains(", genus now <i>Strumigenys</i>"));
        String noGenusHtml = org.calacademy.antweb.curate.NameUpdatesAction.noGenusHtml(db.getNoGenus(0), "");
        assertTrue(noGenusHtml, noGenusHtml.contains("Not moved: genus not in AntCat (1 name)"));

        // AntCat adds the past combination: the next night moves it, and the list is replaced.
        taxon("myrmicinaepyramica emmae", "species", "obsolete combination", "myrmicinaestrumigenys emmae", null, "myrmicinae", "pyramica", "emmae", null);
        SpecimenNameUpdate.Summary next = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000);
        assertEquals(2, next.genusSpecimens);
        assertEquals("myrmicinaestrumigenys emmae", nameOf("sam-hym-c000004"));
        assertEquals(0, count("select count(*) from specimen_name_update where mode = 'no_combo'"));

        // Later AntCat changes the name again: the specimen follows from the name it was uploaded as.
        taxon("myrmicinaestrumigenys newname", "species", "valid", null, null, "myrmicinae", "strumigenys", "newname", null);
        exec("update taxon set status = 'synonym', current_valid_name = 'myrmicinaestrumigenys newname' where taxon_name = 'myrmicinaestrumigenys ludovici'");
        exec("update taxon set current_valid_name = 'myrmicinaestrumigenys newname' where taxon_name = 'myrmicinaepyramica ludovici'");
        SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000);
        assertEquals("myrmicinaestrumigenys newname", nameOf("sam-hym-c003105"));
        assertEquals("(formicidae)pyramica ludovici", one("select original_taxon_name from specimen where code = 'sam-hym-c003105'"));
    }

    private void specimenIn(String code, String taxonName, int group, String bioregion) throws SQLException {
        exec("insert into specimen (code, taxon_name, original_taxon_name, access_group, status, subfamily, genus, species, bioregion)"
            + " values (" + q(code) + ", " + q(taxonName) + ", null, " + group + ", 'indetermined', 'ponerinae', 'mesoponera', 'x', " + q(bioregion) + ")");
    }

    /**
     * Brian Fisher, 2026-11-03, rule 1 of the old genus concepts (GenusConceptRule): Mesoponera in the Afrotropical and
     * Malagasy regions is Xiphopelta. Only the morphospecies and indets of those bioregions move (the same name in
     * another bioregion, in the same group, stays); a species name stays; the taxon row is made in apply mode only.
     */
    public void testOldGenusConceptMovesOneBioregion() throws Exception {
        taxon("ponerinaemesoponera", "genus", "valid", null, null, "ponerinae", "mesoponera", null, null);
        taxon("ponerinaexiphopelta", "genus", "valid", null, null, "ponerinae", "xiphopelta", null, null);
        taxon("ponerinaemesoponera (indet)", "species", "indetermined", null, "specimenUpload", "ponerinae", "mesoponera", "(indet)", null);
        taxon("ponerinaemesoponera ambigua", "species", "valid", null, null, "ponerinae", "mesoponera", "ambigua", null);
        exec("update taxon set antcat_id = 601, tribe = 'ponerini' where taxon_name = 'ponerinaexiphopelta'");
        specimenIn("kg0000001", "ponerinaemesoponera (indet)", 30, "Afrotropical");
        specimenIn("kg0000002", "ponerinaemesoponera (indet)", 30, "Afrotropical");
        specimenIn("kg0000003", "ponerinaemesoponera (indet)", 30, "Indomalaya");
        specimenIn("kg0000004", "ponerinaemesoponera kg01", 30, "Afrotropical");
        specimenIn("kg0000005", "ponerinaemesoponera ambigua", 30, "Afrotropical");
        specimenIn("mracfor001128", "ponerinaemesoponera (indet)", 31, "Malagasy");

        SpecimenNameUpdate.Summary report = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.REPORT, 50000);
        assertEquals("4 as before + 4", 8, report.specimens);
        assertEquals(4, report.conceptSpecimens);
        assertEquals("ponerinaemesoponera (indet)", nameOf("kg0000001"));
        assertEquals(0, count("select count(*) from taxon where taxon_name like 'ponerinaexiphopelta %'"));
        assertEquals("Old genus concept: Mesoponera in the Afrotropical region is Xiphopelta (rule approved by Brian Fisher, 2026-11-03)",
            one("select reason from specimen_name_update where code = 'kg0000001'"));
        assertEquals("601", one("select antcat_id from specimen_name_update where code = 'kg0000001'"));
        assertEquals(0, count("select count(*) from specimen_name_update where code = 'kg0000003'"));

        SpecimenNameUpdate.Summary s = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000);
        assertEquals(4, s.conceptSpecimens);
        assertTrue(s.line(), s.line().contains("of these, by an approved old genus concept: 4"));
        assertEquals("ponerinaexiphopelta (indet)", nameOf("kg0000001"));
        assertEquals("ponerinaexiphopelta (indet)", nameOf("kg0000002"));
        assertEquals("another bioregion stays", "ponerinaemesoponera (indet)", nameOf("kg0000003"));
        assertEquals("ponerinaexiphopelta kg01", nameOf("kg0000004"));
        assertEquals("a species name stays", "ponerinaemesoponera ambigua", nameOf("kg0000005"));
        assertEquals("ponerinaexiphopelta (indet)", nameOf("mracfor001128"));
        assertEquals("Uploaded As", "ponerinaemesoponera (indet)", one("select original_taxon_name from specimen where code = 'kg0000001'"));
        assertNull(one("select original_taxon_name from specimen where code = 'kg0000003'"));
        assertEquals("ponerinae ponerini xiphopelta indetermined",
            one("select concat_ws(' ', subfamily, tribe, genus, status) from specimen where code = 'kg0000001'"));
        assertEquals("species indetermined xiphopelta (indet) ponerinaexiphopelta genusSynonymMove",
            one("select concat_ws(' ', taxarank, status, genus, species, parent_taxon_name, insert_method) from taxon where taxon_name = 'ponerinaexiphopelta (indet)'"));
        assertEquals("morphotaxon", one("select status from taxon where taxon_name = 'ponerinaexiphopelta kg01'"));
        assertEquals("Old genus concept: Mesoponera in the Malagasy region is Xiphopelta (rule approved by Brian Fisher, 2026-11-03)",
            one("select reason from specimen_name_update where code = 'mracfor001128' and mode = 'changed'"));

        SpecimenNameUpdate.Summary again = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000);
        assertEquals("nothing more, nothing moved back", 0, again.specimens);
        assertEquals("ponerinaexiphopelta (indet)", nameOf("kg0000001"));
    }

    /**
     * Brian Fisher, 2026-11-03: old genus concepts proposed from AntCat's data (GenusConceptRuleDb.loadPairs, the three
     * grouped queries) on a small fixture, then an approved row of genus_concept_rule driving the nightly move.
     *   Alpha, Afrotropical: both AntCat species known there went to Beta, no valid Alpha left -> PROPOSE
     *   Alpha, Malagasy: one to Beta, one to Gamma -> SPLIT
     *   Alpha, Neotropical: one to Beta, a valid Alpha species left -> MIXED
     *   Alpha, Indomalaya: only red-flagged specimens -> not listed
     *   Syn (a synonym genus) and Mesoponera (the seed rule) -> not listed
     */
    public void testGenusConceptProposalsAndApprovedRule() throws Exception {
        exec("drop table if exists bioregion_taxon");
        exec("create table bioregion_taxon (bioregion_name varchar(64), taxon_name varchar(128))");
        try {
            for (String g : new String[] {"alpha", "beta", "gamma", "mesoponera", "xiphopelta"}) {
                taxon("ponerinae" + g, "genus", "valid", null, null, "ponerinae", g, null, null);
            }
            taxon("ponerinaesyn", "genus", "synonym", "ponerinaebeta", null, "ponerinae", "syn", null, null);
            String[][] moved = {   // old, current valid name, bioregions of the valid name
                {"ponerinaealpha one", "ponerinaebeta one", "Afrotropical,Malagasy"},
                {"ponerinaealpha two", "ponerinaebeta two", "Afrotropical,Neotropical"},
                {"ponerinaealpha three", "ponerinaegamma three", "Malagasy"},
                {"ponerinaealpha five", "ponerinaealpha four", "Neotropical"},    // within the genus: not a move; Alpha four is valid
                {"ponerinaesyn six", "ponerinaebeta six", "Afrotropical"},
                {"ponerinaemesoponera seven", "ponerinaexiphopelta seven", "Afrotropical"}};
            for (String[] m : moved) {
                String[] t = m[1].substring("ponerinae".length()).split(" ");
                if (count("select count(*) from taxon where taxon_name = '" + m[1] + "'") == 0) {
                    taxon(m[1], "species", "valid", null, null, "ponerinae", t[0], t[1], null);
                }
                String[] o = m[0].substring("ponerinae".length()).split(" ");
                taxon(m[0], "species", "obsolete combination", m[1], null, "ponerinae", o[0], o[1], null);
                for (String b : m[2].split(",")) exec("insert into bioregion_taxon values ('" + b + "', '" + m[1] + "')");
            }
            String[][] specimens = {   // code, name, group, bioregion, status, flag
                {"pa0001", "ponerinaealpha (indet)", "40", "Afrotropical", "indetermined", null},
                {"pa0002", "ponerinaealpha af01", "40", "Afrotropical", "morphotaxon", null},
                {"pa0003", "ponerinaealpha af01", "41", "Afrotropical", "morphotaxon", null},
                {"pa0004", "ponerinaealpha (indet)", "40", "Malagasy", "indetermined", null},
                {"pa0005", "ponerinaealpha (indet)", "40", "Neotropical", "indetermined", null},
                {"pa0006", "ponerinaealpha (indet)", "40", "Indomalaya", "indetermined", "red"},
                {"pa0007", "ponerinaesyn (indet)", "40", "Afrotropical", "indetermined", null},
                {"pa0008", "ponerinaemesoponera (indet)", "40", "Afrotropical", "indetermined", null},
                {"pa0009", "ponerinaealpha four", "40", "Afrotropical", "valid", null}};
            for (String[] sp : specimens) {
                exec("insert into specimen (code, taxon_name, access_group, status, flag, subfamily, genus, species, bioregion) values ("
                    + q(sp[0]) + ", " + q(sp[1]) + ", " + sp[2] + ", " + q(sp[4]) + ", " + q(sp[5]) + ", 'ponerinae', 'alpha', 'x', " + q(sp[3]) + ")");
            }

            org.calacademy.antweb.upload.GenusConceptRuleDb rules = new org.calacademy.antweb.upload.GenusConceptRuleDb(connection);
            rules.ensureSchema();
            assertEquals("the seed: Mesoponera in two bioregions", 2, rules.approved().size());
            java.util.List<org.calacademy.antweb.upload.GenusConceptProposals.Pair> listed = rules.proposals();
            java.util.Map<String, String> kinds = new java.util.TreeMap<>();
            for (org.calacademy.antweb.upload.GenusConceptProposals.Pair p : listed) kinds.put(p.genus + " " + p.bioregion, p.kind.toString());
            assertEquals("{ponerinaealpha Afrotropical=PROPOSE, ponerinaealpha Malagasy=SPLIT, ponerinaealpha Neotropical=MIXED}", kinds.toString());
            org.calacademy.antweb.upload.GenusConceptProposals.Pair propose = listed.get(0);
            assertEquals("ponerinaebeta", propose.newGenus());
            assertEquals(3, propose.specimens());
            assertEquals("{40=2, 41=1}", propose.specimensByGroup.toString());
            assertEquals("2 of 2 species of Alpha in Afrotropical moved to Beta",
                org.calacademy.antweb.upload.GenusConceptProposals.evidence(propose));
            assertEquals(java.util.List.of("Alpha one -> Beta one", "Alpha two -> Beta two"),
                org.calacademy.antweb.upload.GenusConceptProposals.examples(propose, 5));

            // The page's decision: approve only what is proposed.
            String refused = org.calacademy.antweb.curate.GenusConceptRulesAction.decide(rules, listed, "approve",
                "ponerinaealpha", "Malagasy", "ponerinaebeta", null, "test");
            assertTrue(refused, refused.startsWith("Nothing changed"));
            String approved = org.calacademy.antweb.curate.GenusConceptRulesAction.decide(rules, listed, "approve",
                "ponerinaealpha", "Afrotropical", "ponerinaebeta", "AntCat moved both", "Brian Fisher");
            assertTrue(approved, approved.startsWith("Approved: Alpha in the Afrotropical region is Beta"));
            org.calacademy.antweb.curate.GenusConceptRulesAction.decide(rules, listed, "reject", "ponerinaealpha", "Malagasy", null, null, "Brian Fisher");
            assertEquals("approved and rejected pairs are not proposed again", 1, rules.proposals().size());
            assertEquals("rejected", one("select status from genus_concept_rule where old_genus = 'ponerinaealpha' and bioregion = 'Malagasy'"));
            assertEquals(3, rules.approved().size());

            // The approved row drives the nightly move: Afrotropical only, morphospecies and indets only.
            SpecimenNameUpdate.Summary s = SpecimenNameUpdate.run(connection, SpecimenNameUpdate.APPLY, 50000);
            assertEquals("ponerinaebeta (indet)", nameOf("pa0001"));
            assertEquals("ponerinaebeta af01", nameOf("pa0002"));
            assertEquals("ponerinaebeta af01", nameOf("pa0003"));
            assertEquals("ponerinaealpha (indet)", nameOf("pa0004"));
            assertEquals("ponerinaealpha four", nameOf("pa0009"));
            assertEquals("and the seed rule", "ponerinaexiphopelta (indet)", nameOf("pa0008"));
            assertEquals(4, s.conceptSpecimens);
            assertEquals("Old genus concept: Alpha in the Afrotropical region is Beta (rule approved by Brian Fisher, " + new java.sql.Date(System.currentTimeMillis()) + ")",
                one("select reason from specimen_name_update where code = 'pa0001' and mode = 'changed'"));
        } finally {
            exec("drop table if exists bioregion_taxon");
        }
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
