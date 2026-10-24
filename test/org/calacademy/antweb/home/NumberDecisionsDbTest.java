package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import junit.framework.TestCase;

import org.calacademy.antweb.OrphanedImages;
import org.calacademy.antweb.Project;
import org.calacademy.antweb.TaxaPage;
import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.home.GroupCountDb;
import org.calacademy.antweb.home.PlaceImageCountDb;
import org.calacademy.antweb.home.ProjTaxonCountDb;
import org.calacademy.antweb.home.StatisticsDb;
import org.calacademy.antweb.util.CountCheck;
import org.calacademy.antweb.util.CountInvariants;
import org.calacademy.antweb.util.TaxonMgr;

import test.org.calacademy.antweb.util.NumbersTestDb;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * Brian Fisher's decisions of 2026-10-22 on the numbers (docs/numbers.md section 5, Q7, Q8, Q9, Q12 and the Port of
 * Entry project), each against the NumbersTestDb database, with the count check computing the same rule:
 *
 *   Q7   statsPage Global, Bioregion and Museum Data and the home "valid species + ssp." count ants only, and species
 *        only when identified to genus
 *   Q8   upload group Species = distinct full names of species and subspecies of the counted statuses (ants, identified
 *        to genus); the Totals row of groups.do = all groups' specimens together, not the largest group
 *   Q9   orphanedImages.do: "N images of M specimen codes that are not in AntWeb"
 *   Q12  "Unimaged" = no photo of the ant anywhere in AntWeb; a name with only label photos is unimaged
 *   POE  the Port of Entry project is a species list only: no specimen or image numbers, not checked
 *
 * Runs in CI against MySQL 8.4 (host "mysql"); ANTWEB_TEST_DB_URL overrides.
 */
public class NumberDecisionsDbTest extends TestCase {

    private Connection connection;

    static final String WASP = "vespidaepolistes a";
    static final String INDET = "(indet)(formicinae) (indet)";
    static final String FORM = "formicinae";
    static final String CAMPO = "formicinaecamponotus";
    static final String CA = "formicinaecamponotus a";   // the same epithet as pheidole a

    @Override
    protected void setUp() throws Exception {
        connection = connect();
        fill(connection);
        GroupCountDb.forgetTable();
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    private void exec(String sql) throws Exception {
        NumbersTestDb.exec(connection, sql);
    }

    private long one(String sql) throws Exception {
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getLong(1) : -1;
        }
    }

    private CountCheck.Result check() throws Exception {
        CountCheck cc = new CountCheck(connection);
        cc.curatorNumbers = curatorNumbers();
        cc.artistNumbers = artistNumbers();
        return cc.run(new ArrayList<>(), new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"), true, true, true, null);
    }

    private static String list(CountCheck.Result r) {
        StringBuilder b = new StringBuilder();
        for (CountCheck.Mismatch m : r.mismatches) b.append("\n  ").append(m.check).append(": ").append(m);
        return b.toString();
    }

    private static int real(CountCheck.Result r, String check) {
        int n = 0;
        for (CountCheck.Mismatch m : r.mismatches) if (m.isReal() && m.check.equals(check)) ++n;
        return n;
    }

    private static int pending(CountCheck.Result r, String check) {
        int n = 0;
        for (CountCheck.Mismatch m : r.mismatches) if (!m.isReal() && m.check.equals(check)) ++n;
        return n;
    }

    private static int at(CountCheck.Result r, String type, String key) {
        int n = 0;
        for (CountCheck.Mismatch m : r.mismatches) if (m.place != null && type.equals(m.place.type) && key.equalsIgnoreCase(m.place.key)) ++n;
        return n;
    }

    /** A valid wasp and a valid ant name not identified to genus, on the lists of CASC and Malagasy. */
    private void addWaspAndIndet() throws Exception {
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus, fossil) values"
            + " ('" + WASP + "', 'species', 'valid', 'vespidaepolistes', 'vespidae', null, 'polistes', 0),"
            + " ('vespidaepolistes', 'genus', 'valid', null, 'vespidae', null, 'polistes', 0),"
            + " ('" + INDET + "', 'species', 'valid', '" + FORM + "', 'formicidae', '" + FORM + "', '(indet)', 0)");
        for (String name : new String[] {WASP, "vespidaepolistes", INDET}) {
            exec("insert into museum_taxon (code, taxon_name, created) values ('CASC', '" + name + "', '2020-01-01')");
            exec("insert into bioregion_taxon (bioregion_name, taxon_name, created) values ('Malagasy', '" + name + "', '2020-01-01')");
        }
    }

    // ------------------------------------------------------------------ Q7

    public void testStatsPageAndHomeCountAntsIdentifiedToGenusOnly() throws Exception {
        addWaspAndIndet();
        StatisticsDb db = new StatisticsDb(connection);
        // Global Data, Extant: subfamilies, genera, species, subspecies of ants (the wasp genus and species are not counted)
        StatSetCheck.assertValues(db.getExtantData(), 2, 2, 2, 1);
        assertEquals("home valid species + ssp.: pheidole a, a x, hypoponera b", 3, one(StatisticsDb.VALID_SPECIES_SQL));
        assertEquals("the rule before Q7 counted the wasp and the indet name", 5, one(StatisticsDb.VALID_SPECIES_SQL_BEFORE_Q7));
        org.calacademy.antweb.geolocale.Bioregion b = new org.calacademy.antweb.geolocale.Bioregion();
        b.setName("Malagasy");
        StatSetCheck.assertValues(db.getBioregionData(b), 2, 2, 2, 0);
        org.calacademy.antweb.Museum m = new org.calacademy.antweb.Museum();
        m.setCode("CASC");
        StatSetCheck.assertValues(db.getExtantMuseumData(m), 1, 1, 1, 1);

        // statsPage's first number (taxa in memory) by the same rule
        assertEquals(1, TaxonMgr.countValidSpecies(Arrays.asList(
            taxon("species", "valid", "formicidae", "pheidole"), taxon("species", "valid", "vespidae", "polistes"),
            taxon("species", "valid", "formicidae", "(indet)"), taxon("genus", "valid", "formicidae", "pheidole"))));

        // the check computes the same rule: no difference; the home row written before the change is pending, not wrong
        CountCheck.Result r = check();
        for (String id : new String[] {"stats.global", "stats.bioregions", "stats.museums", "stats.nonants", "home"}) {
            assertEquals(id + list(r), 0, real(r, id));
        }
        exec("update statistics set extant_taxa = 5");
        r = check();
        assertEquals(list(r), 0, real(r, "home"));
        assertEquals(list(r), 1, pending(r, "home"));
        exec("update statistics set extant_taxa = 4");   // neither rule: wrong
        assertEquals(1, real(check(), "home"));
    }

    private static Taxon taxon(String rank, String status, String family, String genus) {
        Taxon t = new Taxon();
        t.setRank(rank);
        t.setStatus(status);
        t.setFamily(family);
        t.setGenus(genus);
        return t;
    }

    /** StatSet values in order: subfamilies, genera, species, subspecies. */
    static final class StatSetCheck {
        static void assertValues(org.calacademy.antweb.util.StatSet s, int... v) {
            for (int i = 0; i < v.length; i++) assertEquals(s.getName() + " value " + (i + 1), v[i], s.getValue(i + 1));
        }
    }

    // ------------------------------------------------------------------ Q8

    /** Group 2: pheidole a (also in group 1), camponotus a (same epithet), and a name not identified to genus. */
    private void addGroup2() throws Exception {
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus) values"
            + " ('" + FORM + "', 'subfamily', 'valid', '" + FAMILY + "', 'formicidae', '" + FORM + "', null),"
            + " ('" + CAMPO + "', 'genus', 'valid', '" + FORM + "', 'formicidae', '" + FORM + "', 'camponotus'),"
            + " ('" + CA + "', 'species', 'valid', '" + CAMPO + "', 'formicidae', '" + FORM + "', 'camponotus'),"
            + " ('" + INDET + "', 'species', 'indetermined', '" + FORM + "', 'formicidae', '" + FORM + "', '(indet)')");
        exec("insert into specimen (code, taxon_name, status, country, family, subfamily, genus, species, access_group, collectioncode, localitycode, decimal_latitude) values"
            + " ('g2a', '" + PA + "', 'valid', 'Madagascar', 'formicidae', '" + MYRM + "', 'pheidole', 'a', 2, 'c1', 'l1', -18.9),"
            + " ('g2b', '" + CA + "', 'valid', 'Madagascar', 'formicidae', '" + FORM + "', 'camponotus', 'a', 2, 'c3', 'l1', -18.9),"
            + " ('g2c', '" + INDET + "', 'indetermined', 'Madagascar', 'formicidae', '" + FORM + "', '(indet)', '(indet)', 2, 'c3', 'l1', -18.9)");
        exec("insert into ant_group values (2, 'MCZ', 1, 3)");
        exec("insert into upload (upload_id, created, login_id, group_id) values (2, '2020-01-01 10:00:00', 5, 2)");
    }

    public void testGroupSpeciesAreFullNamesAndTotalsAreDistinct() throws Exception {
        addGroup2();
        new GroupCountDb(connection).recountAll();
        assertEquals("group 1: pheidole a, b, a x, hypoponera b (was 2: the epithets a, b of valid names)", 4,
            one("select species from group_count where group_id = 1"));
        assertEquals("group 2: pheidole a and camponotus a; the indet name is not a species", 2, one("select species from group_count where group_id = 2"));
        assertEquals("group 2 genera: pheidole, camponotus", 2, one("select genera from group_count where group_id = 2"));
        assertEquals("group 2 subfamilies", 2, one("select subfamilies from group_count where group_id = 2"));
        assertEquals("group 2 specimens: the indet one too", 3, one("select specimens from group_count where group_id = 2"));
        // Totals row: all groups' specimens together (not the largest group, 4)
        int all = GroupCountDb.ALL_GROUPS;
        assertEquals("pheidole a counted once", 5, one("select species from group_count where group_id = " + all));
        assertEquals(3, one("select genera from group_count where group_id = " + all));
        assertEquals(3, one("select subfamilies from group_count where group_id = " + all));
        assertEquals("c1, c2, c3", 3, one("select collections from group_count where group_id = " + all));
        assertEquals(8, one("select specimens from group_count where group_id = " + all));
        assertEquals(5, new GroupCountDb(connection).totals().getSpecies());

        // The Totals are counted one distinct number per query (staging 2026-10-24: five count(distinct) in one query over
        // 1 million specimens wrote 23 GB of temporary tables): the same numbers as that one query gave.
        long[] t = GroupCountDb.countTotals(connection, null);
        String onePass = "select 0 g," + GroupCountDb.countColumns() + " where s.access_group in (" + GroupCountDb.LISTED_GROUPS_SQL + ")";
        try (java.sql.Statement q = connection.createStatement(); ResultSet rs = q.executeQuery(onePass)) {
            assertTrue(rs.next());
            long[] v = {rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(10), rs.getLong(7), rs.getLong(8), rs.getLong(9)};
            assertEquals(java.util.Arrays.toString(v), java.util.Arrays.toString(t));
        }
        for (String sql : GroupCountDb.totalsSqls()) {
            assertTrue("at most one distinct per query: " + sql, sql.split("distinct", -1).length <= 2);
        }

        // an upload's own numbers use the same rule (UploadDb.getCounts runs GroupCountDb.groupCountsSql for its group)
        try (java.sql.PreparedStatement stmt = connection.prepareStatement(GroupCountDb.groupCountsSql())) {
            stmt.setInt(1, 2);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("specimens", 3, rs.getInt(3));
                assertEquals("species", 2, rs.getInt(9));
            }
        }

        CountCheck.Result r = check();
        assertEquals(list(r), 0, real(r, "admin.groups"));
        exec("update group_count set species = 4 where group_id = " + all);   // the largest group, the old Totals rule
        r = check();
        assertEquals(list(r), 1, real(r, "admin.groups"));
    }

    /** The Totals row is never zeros: an ant_group row with id 0, or an older Totals row, does not leave 0 shown (staging 2026-10-24). */
    public void testTotalsRowIsNeverZeroFilled() throws Exception {
        int all = GroupCountDb.ALL_GROUPS;
        exec("insert into ant_group (id, name) values (" + all + ", 'zero')");
        new GroupCountDb(connection).recountAll();
        assertTrue("Totals counted, not zeros", one("select specimens from group_count where group_id = " + all) > 0);
        assertNotNull(new GroupCountDb(connection).totals());
    }

    public void testGroupNumbersOfTheOldRuleArePendingUntilTheNightlyRun() throws Exception {
        exec("update group_count set species = 2 where group_id = 1");   // as the nightly run before 2026-10-22 wrote it
        CountCheck.Result r = check();
        assertEquals(list(r), 0, real(r, "admin.groups"));
        assertEquals(list(r), 1, pending(r, "admin.groups"));
        exec("update group_count set species = 3 where group_id = 1");   // neither rule
        assertEquals(1, real(check(), "admin.groups"));
    }

    // ------------------------------------------------------------------ Q9

    public void testOrphanedImagesCountImagesAndCodes() throws Exception {
        exec("insert into image (id, image_of_id, shot_type, upload_date) values (40, 'gone1', 'h', '2020-01-01 10:00:00'),"
            + " (41, 'gone1', 'p', '2020-01-01 10:00:00'), (42, 'gone2', 'h', '2020-01-01 10:00:00')");
        OrphanedImages page = new OrphanedImages();
        page.setConnection(connection);
        page.setOrphans();
        assertEquals("images", 3, page.getImageCount());
        assertEquals("specimen codes", 2, page.getOrphans().size());
        CountCheck.Result r = check();
        assertEquals(list(r), 0, real(r, "stats.images"));
        String json = org.calacademy.antweb.util.NumberChecks.json(r.invariants.expected);
        assertTrue(json, json.contains("\"orphanedImages\":{\"codes\":2,\"images\":3}"));
    }

    // ------------------------------------------------------------------ Q12

    public void testUnimagedMeansNoPhotoOfTheAntAnywhere() throws Exception {
        // pheidole a: casent1 head photo; pheidole b: casent3 profile; hypoponera b: casent5 label only; pheidole a x: none
        Set<String> imaged = StatisticsDb.antImagedNames(connection, Arrays.asList(PA, PB, PAX, HB));
        assertEquals(new java.util.HashSet<>(Arrays.asList(PA, PB)), imaged);

        TaxaPage page = new TaxaPage();
        ArrayList<Taxon> children = new ArrayList<>();
        for (String[] n : new String[][] {{MYRM, "pheidole", "a"}, {PON, "hypoponera", "b"}}) {
            Taxon t = new Taxon();
            t.setRank("species");
            t.setSubfamily(n[0]);
            t.setGenus(n[1]);
            t.setSpecies(n[2]);
            children.add(t);
        }
        page.setChildren(children);
        page.findUnimaged(connection);
        assertEquals("label-only hypoponera b is unimaged; pheidole a is imaged", java.util.Collections.singleton(HB), page.getUnimagedNames());

        // imaged anywhere: an image of the ant from any place makes the name imaged everywhere
        exec("insert into specimen (code, taxon_name, status, country, family, subfamily) values ('elsewhere1', '" + HB + "', 'valid', 'Comoros', 'formicidae', '" + PON + "')");
        exec("insert into image (id, image_of_id, shot_type, upload_date) values (60, 'elsewhere1', 'd', '2020-01-01 10:00:00')");
        assertTrue(StatisticsDb.antImagedNames(connection, Arrays.asList(HB)).contains(HB));
    }

    // ------------------------------------------------------------------ Port of Entry

    public void testPortOfEntryHasNoNumbersAndIsNotChecked() throws Exception {
        String poe = Project.PORT_OF_ENTRY;
        exec("insert into project (project_name, is_live, scope, subfamily_count, genus_count, species_count, valid_species_count, specimen_count, image_count)"
            + " values ('" + poe + "', 1, 'PROJECT', 1, 1, 1, 1, 37430, 99)");
        // the list's rows counted every AntWeb specimen of the species (not interceptions)
        exec("insert into proj_taxon (project_name, taxon_name, specimen_count, image_count, species_count, created) values"
            + " ('" + poe + "', '" + PA + "', 213986, 9, 0, '2020-01-01'), ('" + poe + "', '" + PHEIDOLE + "', 213986, 9, 7, '2020-01-01')");
        assertTrue(Project.isSpeciesListOnly(poe));

        // the count check skips it, with a note
        CountCheck.Result r = check();
        assertEquals(list(r), 0, at(r, "project", poe));
        assertTrue(r.notes.toString(), r.notes.contains(CountInvariants.SPECIES_LIST_ONLY_NOTE));

        // no nightly step counts its numbers: the list's counts are dropped, its images not counted
        Project p = new Project();
        p.setName(poe);
        new ProjTaxonCountDb(connection).recount(p);
        assertEquals(0, one("select sum(specimen_count) + sum(image_count) + sum(species_count) from proj_taxon where project_name = '" + poe + "'"));
        assertEquals("the list itself is kept", 2, one("select count(*) from proj_taxon where project_name = '" + poe + "'"));
        PlaceImageCountDb.PlaceImages images = new PlaceImageCountDb(connection).updatePlace(PlaceImageCountDb.PROJECT, poe);
        assertEquals(0, images.allImages);
        assertEquals(0, one("select count(*) from place_image_count where place_type = 'project' and place_key = '" + poe + "'"));
        // other projects are still counted
        assertTrue(new PlaceImageCountDb(connection).updatePlace(PlaceImageCountDb.PROJECT, "madants").allImages > 0);
    }
}
