package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

import org.calacademy.antweb.home.StatisticsDb;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * The imaged names kept in memory for the list pages' genus rows (StatisticsDb.antImagedNamesKept, 2026-11-17: asked
 * once per 500 names, the Images column was about 5 of the 7.6 s of the Myrmicinae page). On the numbers fixture
 * (NumbersTestDb): pheidole a imaged (casent1 head), pheidole b imaged (casent3 profile), hypoponera b label photo only,
 * pheidole a x no photo.
 *
 *   - the same answer as the live rule (antImagedNames), names compared without case;
 *   - a new photo is not seen until the names are read again: after a specimen upload is committed
 *     (imagedNamesChanged, SpecimenUploadJob) at once;
 *   - photos of a red-flagged specimen never count (2026-11-09), in the kept names too.
 *
 * Runs in CI against MySQL 8.4 (.github/workflows/tests.yaml); ANTWEB_TEST_DB_URL overrides.
 */
public class ImagedNamesKeptDbTest extends TestCase {

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        StatisticsDb.forgetImagedNames();
        connection = connect();
        fill(connection);
    }

    @Override
    protected void tearDown() throws Exception {
        StatisticsDb.forgetImagedNames();
        if (connection != null) connection.close();
    }

    private void exec(String sql) throws Exception {
        try (java.sql.Statement st = connection.createStatement()) {
            st.execute(sql);
        }
    }

    public void testKeptNamesAnswerAsTheLiveRule() throws Exception {
        Set<String> live = StatisticsDb.antImagedNames(connection, Arrays.asList(PA, PB, PAX, HB));
        Set<String> kept = StatisticsDb.antImagedNamesKept(connection, Arrays.asList(PA, PB, PAX, HB));
        assertEquals(new HashSet<>(Arrays.asList(PA, PB)), live);
        assertEquals(live, kept);
        assertEquals("names compared without case, the given spelling returned",
            new HashSet<>(Arrays.asList(PA.toUpperCase())), StatisticsDb.antImagedNamesKept(connection, Arrays.asList(PA.toUpperCase(), HB)));
    }

    public void testANewPhotoIsSeenAfterAnUpload() throws Exception {
        assertFalse(StatisticsDb.antImagedNamesKept(connection, Arrays.asList(HB)).contains(HB));
        exec("insert into specimen (code, taxon_name, status, country, family, subfamily) values ('kept1', '" + HB + "', 'valid', 'Comoros', 'formicidae', '" + PON + "')");
        exec("insert into image (id, image_of_id, shot_type, upload_date) values (70, 'kept1', 'd', '2020-01-01 10:00:00')");
        assertFalse("kept names: read again only when old or after an upload",
            StatisticsDb.antImagedNamesKept(connection, Arrays.asList(HB)).contains(HB));
        assertTrue("the live rule sees it at once", StatisticsDb.antImagedNames(connection, Arrays.asList(HB)).contains(HB));
        StatisticsDb.imagedNamesChanged();   // SpecimenUploadJob, after the upload is committed
        assertTrue(StatisticsDb.antImagedNamesKept(connection, Arrays.asList(HB)).contains(HB));
    }

    public void testPhotosOfRedFlaggedSpecimensNeverCount() throws Exception {
        exec("insert into specimen (code, taxon_name, status, country, family, subfamily, flag) values ('kept2', '" + PAX + "', 'valid', 'Madagascar', 'formicidae', '" + MYRM + "', 'red')");
        exec("insert into image (id, image_of_id, shot_type, upload_date) values (71, 'kept2', 'h', '2020-01-01 10:00:00')");
        assertFalse(StatisticsDb.antImagedNamesKept(connection, Arrays.asList(PAX)).contains(PAX));
    }
}
