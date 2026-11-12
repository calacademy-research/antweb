package test.org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import junit.framework.TestCase;

import org.calacademy.antweb.Museum;
import org.calacademy.antweb.Project;
import org.calacademy.antweb.geolocale.Bioregion;
import org.calacademy.antweb.home.PlaceImageCountDb;
import org.calacademy.antweb.home.PlaceViewTotalDb;
import org.calacademy.antweb.util.UtilDataAction;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * The last steps of the admin jobs for ONE place (utilData.do?action=populateMuseum&code=, populateBioregion&name=,
 * geolocaleCountCrawl&num=, projectCountCrawl&name=; UtilDataAction.finishOnePlace), review of PR #341, 2026-11-11.
 * On the numbers fixture (NumbersTestDb), whose museum and bioregion rows hold Images and Imaged specimens as the
 * rebuild of a museum or bioregion writes them (MuseumDb.populate, BioregionDb.populate: every image of the place's
 * specimens, red-flagged specimens' too):
 *
 *  - after the rebuild of one museum or one bioregion the job counts the place's images by the rule (photos of
 *    red-flagged specimens are not counted, Brian Fisher 2026-11-09), as "Update statistics" on the place's page does;
 *    until 2026-11-11 the job stopped after the rebuild and the place showed the red-flagged specimens' images until
 *    the next daily update. No other place's numbers change;
 *  - the job's answer says when the place's list totals could not be counted, and why ("; list totals not recounted:
 *    ..."); until then that was a warning in the server log and the job answered "populated". The images are counted
 *    all the same.
 *
 * tests/unit/test_recalc_view_totals.py checks in the source that each of the four jobs calls finishOnePlace after its
 * rebuild and adds the note to its answer. Runs in CI against MySQL 8.4; ANTWEB_TEST_DB_URL overrides.
 */
public class OnePlaceJobDbTest extends TestCase {

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        connection = connect();
        fill(connection);
        // the columns the list page's own code reads (TaxaPage.fetchChildren, Taxon.setDetails), as PlaceViewTotalDbTest
        exec(connection, "alter table taxon add column subspecies varchar(64), add column antcat_id int, add column type tinyint default 0,"
            + " add column author_date varchar(128)");
        exec(connection, "update taxon set genus = 'pheidole' where taxon_name like 'myrmicinaepheidole%'");
        exec(connection, "update taxon set genus = 'hypoponera' where taxon_name like 'ponerinaehypoponera%'");
        exec(connection, "update taxon set species = substring_index(taxon_name, ' ', -1) where taxarank in ('species', 'subspecies')");
        exec(connection, "alter table proj_taxon add column rev int default 0");
        exec(connection, "drop table if exists place_view_total");
        exec(connection, "drop table if exists place_view_total_state");
        exec(connection, PlaceViewTotalDb.createTable(null, null));
        exec(connection, PlaceViewTotalDb.createStateTable(null, null));
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    private static Museum casc() {
        Museum m = new Museum();
        m.setCode("CASC");
        m.setName("CASC");
        return m;
    }

    private static Bioregion malagasy() {
        Bioregion b = new Bioregion();
        b.setName("Malagasy");
        return b;
    }

    /** {Images (image_count), Imaged specimens (imaged_specimen_count)} of a place's row. */
    private int[] images(String table, String keyColumn, String key) throws Exception {
        try (PreparedStatement s = connection.prepareStatement("select image_count, imaged_specimen_count from " + table + " where " + keyColumn + " = ?")) {
            s.setString(1, key);
            try (ResultSet r = s.executeQuery()) {
                assertTrue(table + " " + key, r.next());
                return new int[] {r.getInt(1), r.getInt(2)};
            }
        }
    }

    private int one(String sql) throws Exception {
        try (PreparedStatement s = connection.prepareStatement(sql); ResultSet r = s.executeQuery()) {
            return r.next() ? r.getInt(1) : -1;
        }
    }

    private static void assertImages(String what, int images, int imagedSpecimens, int[] row) {
        assertEquals(what + ": Images", images, row[0]);
        assertEquals(what + ": Imaged specimens", imagedSpecimens, row[1]);
    }

    /**
     * CASC: casent1 (head and label photo) and casent3 (a profile photo). casent3 is red-flagged. The rebuild of the
     * museum leaves 3 images and 2 imaged specimens in its row (it counts every image); the job's last steps count 2
     * images (casent1's) and 1 imaged specimen, and the place's image table with them. MCZC is not touched.
     */
    public void testTheJobForOneMuseumCountsItsImagesWithoutRedFlaggedSpecimens() throws Exception {
        exec(connection, "update specimen set flag = 'red' where code = 'casent3'");
        assertImages("CASC as the museum's rebuild writes it", 3, 2, images("museum", "code", "CASC"));
        exec(connection, "update museum set image_count = 77, imaged_specimen_count = 66 where code = 'MCZC'");   // to see it is not touched
        String note = UtilDataAction.finishOnePlace(connection, PlaceImageCountDb.MUSEUM, "CASC", casc());
        assertEquals("nothing to add to the job's answer", "", note);
        assertImages("CASC after the job", 2, 1, images("museum", "code", "CASC"));
        assertEquals("the place's image table: every file", 2,
            one("select image_count from place_image_count where place_type = 'museum' and place_key = 'CASC' and shot_type = '" + PlaceImageCountDb.ALL + "'"));
        assertEquals("no profile view left (it was the red-flagged specimen's)", -1,
            one("select image_count from place_image_count where place_type = 'museum' and place_key = 'CASC' and shot_type = 'p'"));
        assertImages("MCZC is another museum: not touched", 77, 66, images("museum", "code", "MCZC"));
        assertEquals("MCZC's image table too", 1,
            one("select image_count from place_image_count where place_type = 'museum' and place_key = 'MCZC' and shot_type = '" + PlaceImageCountDb.ALL + "'"));
        // and the museum's list totals are counted (the second step)
        assertTrue(one("select n from place_view_total where list_type = 'museum' and list_key = 'CASC' and taxarank = 'species'") > 0);
        assertEquals(1, one("select count(*) from place_view_total_state where list_type = 'museum' and list_key = 'CASC'"));
        assertEquals(0, one("select count(*) from place_view_total where list_type = 'museum' and list_key = 'MCZC'"));
    }

    /**
     * Malagasy: casent1 (head and label photo), casent2 (none), casent5 (a label photo). casent1 is red-flagged: the
     * rebuild's 3 images and 1 imaged specimen become 1 image (the label of casent5) and no imaged specimen.
     */
    public void testTheJobForOneBioregionCountsItsImagesWithoutRedFlaggedSpecimens() throws Exception {
        exec(connection, "update specimen set flag = 'red' where code = 'casent1'");
        assertImages("Malagasy as the bioregion's rebuild writes it", 3, 1, images("bioregion", "name", "Malagasy"));
        String note = UtilDataAction.finishOnePlace(connection, PlaceImageCountDb.BIOREGION, "Malagasy", malagasy());
        assertEquals("", note);
        assertImages("Malagasy after the job", 1, 0, images("bioregion", "name", "Malagasy"));
        assertImages("a museum is not touched by a bioregion's job", 3, 2, images("museum", "code", "CASC"));
        assertTrue(one("select n from place_view_total where list_type = 'bioregion' and list_key = 'Malagasy' and taxarank = 'species'") > 0);
    }

    /**
     * The count of the place's list totals fails (the lists' query reads a column that is gone): the job's answer says
     * so and why, escaped for the job page; the images are counted all the same; the place has no list totals (its page
     * shows its own counts). A place AntWeb does not have in memory: said too. The AntWeb-wide list has no list totals:
     * nothing to say.
     */
    public void testTheJobsAnswerSaysWhenTheListTotalsAreNotRecounted() throws Exception {
        exec(connection, "update specimen set flag = 'red' where code = 'casent3'");
        exec(connection, "alter table taxon drop column subspecies");
        String note = UtilDataAction.finishOnePlace(connection, PlaceImageCountDb.MUSEUM, "CASC", casc());
        assertTrue(note, note.startsWith("; list totals not recounted: "));
        assertTrue("the reason: " + note, note.contains("subspecies"));
        assertTrue(note, note.endsWith("(the place's page shows its own counts for Subfamilies, Genera and Species until the next daily update)"));
        assertTrue("the database's message, escaped for the job page: " + note, note.contains("&#39;taxon.subspecies&#39;") && !note.contains("<"));
        assertImages("the images are counted all the same", 2, 1, images("museum", "code", "CASC"));
        assertEquals("no list totals rather than old ones", 0, one("select count(*) from place_view_total where list_type = 'museum' and list_key = 'CASC'"));

        assertEquals("; list totals not recounted: the place is not among the places in memory", UtilDataAction.recountListTotals(connection, null));
        Project all = new Project();
        all.setName(Project.ALLANTWEBANTS);
        assertEquals("the AntWeb-wide list has no list totals", "", UtilDataAction.recountListTotals(connection, all));
    }
}
