package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import junit.framework.TestCase;

import org.calacademy.antweb.Artist;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.home.ArtistDb;

/**
 * Removing an artist in the Artist Manager (ArtistDb.removeArtist, 2026-10-23).
 *
 * Production, 2026-10-23: the CAS image upload list showed "id 514" as the photographer of many batches. Removing
 * an artist with "move the images to another artist" moved the images (image.artist) and deleted the artist, but
 * left the upload batches (image_upload.artist_id) on the deleted number. Now:
 *  - moving the images moves the batches too;
 *  - an artist still named on batches is not deleted without an artist to move them to.
 *
 * The test drops and recreates small tables artist, image and image_upload, and refuses to run when one of them
 * holds more than 100 rows (a real database). Runs in CI against MySQL 8.4 (host "mysql"); ANTWEB_TEST_DB_URL overrides.
 */
public class ArtistMoveDbTest extends TestCase {

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        connection = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        connection.setAutoCommit(true);
        for (String t : new String[] {"artist", "image", "image_upload"}) refuseReal(t);
        exec("drop table if exists artist");
        exec("drop table if exists image");
        exec("drop table if exists image_upload");
        exec("create table artist (id int NOT NULL, name varchar(128), created timestamp NULL DEFAULT CURRENT_TIMESTAMP,"
            + " active tinyint DEFAULT 1, curator_id int, PRIMARY KEY (id))");
        exec("create table image (id int NOT NULL AUTO_INCREMENT, artist int, image_upload_id int, PRIMARY KEY (id))");
        exec("create table image_upload (id int NOT NULL, artist_id int, PRIMARY KEY (id))");
        exec("insert into artist (id, name, curator_id) values (514, 'Duplicate name', 1), (600, 'Michele Esposito', 1),"
            + " (700, 'Nobody', 1)");
        exec("insert into image_upload (id, artist_id) values (1, 514), (2, 514), (3, 600), (4, 700)");
        exec("insert into image (artist, image_upload_id) values (514, 1), (514, 1), (514, 2), (600, 3)");
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) {
            try {
                exec("drop table if exists artist");
                exec("drop table if exists image");
                exec("drop table if exists image_upload");
            } finally {
                connection.close();
            }
        }
    }

    private static Login admin() {
        Login login = new Login();
        login.setId(1);
        login.setIsAdmin(true);
        return login;
    }

    public void testMovingImagesMovesTheBatches() throws Exception {
        String result = new ArtistDb(connection).removeArtist(new Artist(514, "Duplicate name"), new Artist(600, "Michele Esposito"), admin());
        assertTrue(result, result.contains("removed"));
        assertEquals(0, count("select count(*) from image where artist = 514"));
        assertEquals(0, count("select count(*) from image_upload where artist_id = 514"));
        assertEquals(3, count("select count(*) from image_upload where artist_id = 600"));
        assertEquals(0, count("select count(*) from artist where id = 514"));
    }

    public void testArtistOnBatchesIsNotDeletedWithoutAMove() throws Exception {
        // Artist 700 has no images but names batch 4.
        String result = new ArtistDb(connection).removeArtist(new Artist(700, "Nobody"), null, admin());
        assertTrue(result, result.contains("named on image upload batches"));
        assertEquals(1, count("select count(*) from artist where id = 700"));
        assertEquals(1, count("select count(*) from image_upload where artist_id = 700"));
    }

    private void refuseReal(String table) throws SQLException {
        try (Statement s = connection.createStatement();
             ResultSet r = s.executeQuery("select table_rows from information_schema.tables where table_schema = database() and table_name = '" + table + "'")) {
            if (r.next() && r.getLong(1) > 100) fail("refusing to run: table " + table + " looks real (this test drops it)");
        }
    }

    private void exec(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute(sql);
        }
    }

    private int count(String sql) throws SQLException {
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
        }
    }
}
