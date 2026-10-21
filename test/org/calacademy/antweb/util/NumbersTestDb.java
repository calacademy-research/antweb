package test.org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A small AntWeb database in which every number agrees (2026-10-21), for CountInvariantsTest and
 * PlaceTotalsDbTest. MySQL 8.4 in CI (host "mysql"); ANTWEB_TEST_DB_URL overrides.
 *
 * Taxa: formicidae; subfamilies myrmicinae, ponerinae; genera myrmicinaepheidole, ponerinaehypoponera;
 * species pheidole a (valid), pheidole b (morphotaxon), subspecies pheidole a x (valid, parent pheidole a),
 * hypoponera b (valid).
 *
 * Specimens (all collected in Madagascar):
 *   casent1  pheidole a      museum CASC  bioregion Malagasy  images h, l
 *   casent2  pheidole a      CASC         Malagasy            -
 *   casent3  pheidole b      CASC         -                   p
 *   casent4  pheidole a x    CASC         -                   -
 *   casent5  hypoponera b    MCZC         Malagasy            l (label only)
 *
 * Places: country Madagascar (7), subregion Indian Ocean (3, its countries: Madagascar), project madants,
 * allantwebants (Global), museums CASC and MCZC, bioregion Malagasy; their lists and every stored number as
 * the nightly run writes them.
 */
public final class NumbersTestDb {

    private NumbersTestDb() {}

    public static final String FAMILY = "formicidae";
    public static final String MYRM = "myrmicinae";
    public static final String PON = "ponerinae";
    public static final String PHEIDOLE = "myrmicinaepheidole";
    public static final String HYPO = "ponerinaehypoponera";
    public static final String PA = "myrmicinaepheidole a";
    public static final String PB = "myrmicinaepheidole b";
    public static final String PAX = "myrmicinaepheidole a x";
    public static final String HB = "ponerinaehypoponera b";
    public static final int MADAGASCAR = 7;
    public static final int INDIAN_OCEAN = 3;

    public static Connection connect() throws SQLException {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL", "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        Connection c = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        c.setAutoCommit(true);
        return c;
    }

    public static void exec(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) { s.executeUpdate(sql); }
    }

    static final String[] TABLES = {"taxon", "specimen", "image", "geolocale", "geolocale_taxon", "project", "proj_taxon", "museum",
        "museum_taxon", "bioregion", "bioregion_taxon", "place_image_count", "statistics", "upload", "specimen_name_update"};

    static final String PLACE_COLUMNS = "subfamily_count int default 0, genus_count int default 0, species_count int default 0,"
        + " valid_species_count int default 0, specimen_count int default 0, image_count int default 0, imaged_specimen_count int default 0,"
        + " taxon_subfamily_dist_json text, specimen_subfamily_dist_json text";

    static final String LIST_COLUMNS = "taxon_name varchar(128), subfamily_count int default 0, genus_count int default 0,"
        + " species_count int default 0, specimen_count int default 0, image_count int default 0, created timestamp null";

    /** Old rows: written before any nightly run the check looks at. */
    static final String OLD = "'2020-01-01 10:00:00'";

    public static void create(Connection c) throws SQLException {
        for (String t : TABLES) exec(c, "drop table if exists " + t);
        exec(c, "create table taxon (taxon_name varchar(128) primary key, taxarank varchar(20), status varchar(40), parent_taxon_name varchar(128),"
            + " family varchar(40), subfamily varchar(64), genus varchar(64), species varchar(64), fossil tinyint default 0, image_count int default 0,"
            + " chart_color varchar(10))");
        exec(c, "create table specimen (code varchar(40) primary key, taxon_name varchar(128), status varchar(40), country varchar(64), adm1 varchar(64),"
            + " island_country varchar(64), museum varchar(64), bioregion varchar(64), upload_id int, image_count int default 0, family varchar(40),"
            + " subfamily varchar(64), genus varchar(64), species varchar(64), subspecies varchar(64), caste varchar(20), ownedBy varchar(200))");
        exec(c, "create table image (id int primary key, image_of_id varchar(40), shot_type varchar(4), upload_date datetime)");
        exec(c, "create table geolocale (id int primary key, name varchar(128), georank varchar(20), parent varchar(128), is_valid tinyint, is_live tinyint,"
            + " is_island tinyint default 0, endemic_species_count int default 0, introduced_species_count int default 0, " + PLACE_COLUMNS + ")");
        exec(c, "create table geolocale_taxon (geolocale_id int, " + LIST_COLUMNS + ", is_endemic tinyint default 0, is_introduced tinyint default 0,"
            + " primary key (geolocale_id, taxon_name))");
        exec(c, "create table project (project_name varchar(64) primary key, is_live tinyint, scope varchar(20) default 'PROJECT', " + PLACE_COLUMNS + ")");
        exec(c, "create table proj_taxon (project_name varchar(64), " + LIST_COLUMNS + ", primary key (project_name, taxon_name))");
        exec(c, "create table museum (code varchar(64) primary key, " + PLACE_COLUMNS + ")");
        exec(c, "create table museum_taxon (code varchar(64), " + LIST_COLUMNS + ", primary key (code, taxon_name))");
        exec(c, "create table bioregion (name varchar(64) primary key, endemic_species_count int default 0, introduced_species_count int default 0, "
            + PLACE_COLUMNS + ")");
        exec(c, "create table bioregion_taxon (bioregion_name varchar(64), " + LIST_COLUMNS + ", is_endemic tinyint default 0,"
            + " is_introduced tinyint default 0, source varchar(30), primary key (bioregion_name, taxon_name))");
        exec(c, org.calacademy.antweb.home.PlaceImageCountDb.createTable(null, null));
        exec(c, "create table statistics (id int auto_increment primary key, action varchar(40), specimens int, extant_taxa int, total_taxa int,"
            + " proj_taxa int, bioregion_taxa int, museum_taxa int, geolocale_taxa int, geolocale_taxa_introduced int, geolocale_taxa_endemic int,"
            + " total_images int, specimens_imaged int, species_imaged int, valid_species_imaged int, login_id int,"
            + " created timestamp default current_timestamp, exec_time varchar(100))");
        exec(c, "create table upload (upload_id int primary key, created timestamp null)");
    }

    private static void taxon(Connection c, String name, String rank, String status, String parent, String subfamily, int images) throws SQLException {
        exec(c, "insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, image_count) values ('" + name + "', '" + rank
            + "', '" + status + "', " + (parent == null ? "null" : "'" + parent + "'") + ", 'formicidae', " + (subfamily == null ? "null" : "'" + subfamily + "'")
            + ", " + images + ")");
    }

    private static void specimen(Connection c, String code, String name, String status, String museum, String bioregion, int images, String subfamily)
            throws SQLException {
        exec(c, "insert into specimen (code, taxon_name, status, country, museum, bioregion, upload_id, image_count, family, subfamily, ownedBy) values ('"
            + code + "', '" + name + "', '" + status + "', 'Madagascar', '" + museum + "', " + (bioregion == null ? "null" : "'" + bioregion + "'")
            + ", 1, " + images + ", 'formicidae', '" + subfamily + "', '" + museum + "')");
    }

    /** One list row: counts as the crawl writes them. */
    private static void row(Connection c, String table, String keyColumn, String key, String name, int sub, int gen, int sp, int specimens, int images)
            throws SQLException {
        exec(c, "insert into " + table + " (" + keyColumn + ", taxon_name, subfamily_count, genus_count, species_count, specimen_count, image_count, created)"
            + " values (" + key + ", '" + name + "', " + sub + ", " + gen + ", " + sp + ", " + specimens + ", " + images + ", " + OLD + ")");
    }

    private static String json(String... labelValue) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < labelValue.length; i += 2) {
            if (i > 0) b.append(",");
            b.append("{\"label\": \"").append(labelValue[i]).append("\",\"value\": ").append(labelValue[i + 1]).append(",\"color\": \"#999999\"}");
        }
        return b.toString();
    }

    /** The whole database, every number right. */
    public static void fill(Connection c) throws SQLException {
        create(c);
        taxon(c, FAMILY, "family", "valid", null, null, 4);
        taxon(c, MYRM, "subfamily", "valid", FAMILY, MYRM, 3);
        taxon(c, PON, "subfamily", "valid", FAMILY, PON, 1);
        taxon(c, PHEIDOLE, "genus", "valid", MYRM, MYRM, 3);
        taxon(c, HYPO, "genus", "valid", PON, PON, 1);
        taxon(c, PA, "species", "valid", PHEIDOLE, MYRM, 2);
        taxon(c, PB, "species", "morphotaxon", PHEIDOLE, MYRM, 1);
        taxon(c, PAX, "subspecies", "valid", PA, MYRM, 0);
        taxon(c, HB, "species", "valid", HYPO, PON, 1);

        specimen(c, "casent1", PA, "valid", "CASC", "Malagasy", 2, MYRM);
        specimen(c, "casent2", PA, "valid", "CASC", "Malagasy", 0, MYRM);
        specimen(c, "casent3", PB, "morphotaxon", "CASC", null, 1, MYRM);
        specimen(c, "casent4", PAX, "valid", "CASC", null, 0, MYRM);
        specimen(c, "casent5", HB, "valid", "MCZC", "Malagasy", 1, PON);
        exec(c, "insert into image values (1, 'casent1', 'h', " + OLD + "), (2, 'casent1', 'l', " + OLD + "), (3, 'casent3', 'p', " + OLD + "),"
            + " (4, 'casent5', 'l', " + OLD + ")");
        exec(c, "insert into upload values (1, " + OLD + ")");

        // Madagascar and its subregion: the same list and numbers.
        exec(c, "insert into geolocale (id, name, georank, parent, is_valid, is_live, subfamily_count, genus_count, species_count, valid_species_count,"
            + " specimen_count, image_count, imaged_specimen_count, endemic_species_count, introduced_species_count, taxon_subfamily_dist_json,"
            + " specimen_subfamily_dist_json) values"
            + " (" + MADAGASCAR + ", 'Madagascar', 'country', 'Indian Ocean', 1, 1, 2, 2, 4, 3, 5, 4, 2, 2, 0, '" + json("Myrmicinae", "3", "Ponerinae", "1")
            + "', '" + json("Myrmicinae", "4", "Ponerinae", "1") + "'),"
            + " (" + INDIAN_OCEAN + ", 'Indian Ocean', 'subregion', 'Africa', 1, 1, 2, 2, 4, 3, 5, 4, 2, 0, 0, '" + json("Myrmicinae", "3", "Ponerinae", "1")
            + "', '" + json("Myrmicinae", "4", "Ponerinae", "1") + "')");
        for (int g : new int[] {MADAGASCAR, INDIAN_OCEAN}) {
            String t = "geolocale_taxon";
            String k = "geolocale_id";
            row(c, t, k, "" + g, FAMILY, 2, 2, 3, 4, 4);
            row(c, t, k, "" + g, MYRM, 0, 1, 2, 3, 3);
            row(c, t, k, "" + g, PON, 0, 1, 1, 1, 1);
            row(c, t, k, "" + g, PHEIDOLE, 0, 0, 2, 3, 3);
            row(c, t, k, "" + g, HYPO, 0, 0, 1, 1, 1);
            row(c, t, k, "" + g, PA, 0, 0, 1, 2, 2);
            row(c, t, k, "" + g, PB, 0, 0, 0, 1, 1);
            row(c, t, k, "" + g, PAX, 0, 0, 0, 1, 0);
            row(c, t, k, "" + g, HB, 0, 0, 0, 1, 1);
        }
        exec(c, "update geolocale_taxon set is_endemic = 1 where geolocale_id = " + MADAGASCAR + " and taxon_name in ('" + PA + "', '" + HB + "')");

        // Projects: madants (all its specimens, any status) and allantwebants (Global).
        exec(c, "insert into project (project_name, is_live, subfamily_count, genus_count, species_count, valid_species_count, specimen_count, image_count,"
            + " imaged_specimen_count, taxon_subfamily_dist_json, specimen_subfamily_dist_json) values"
            + " ('madants', 1, 2, 2, 4, 3, 5, 4, 2, '" + json("Myrmicinae", "3", "Ponerinae", "1") + "', '" + json("Myrmicinae", "4", "Ponerinae", "1") + "'),"
            + " ('allantwebants', 1, 2, 2, 4, 3, 5, 4, 2, '" + json("Myrmicinae", "3", "Ponerinae", "1") + "', '" + json("Myrmicinae", "4", "Ponerinae", "1") + "')");
        for (String p : new String[] {"madants", "allantwebants"}) {
            String t = "proj_taxon";
            String k = "project_name";
            String key = "'" + p + "'";
            row(c, t, k, key, FAMILY, 2, 2, 3, 4, 4);
            row(c, t, k, key, MYRM, 0, 1, 2, 3, 3);
            row(c, t, k, key, PON, 0, 1, 1, 1, 1);
            row(c, t, k, key, PHEIDOLE, 0, 0, 2, 3, 3);
            row(c, t, k, key, HYPO, 0, 0, 1, 1, 1);
            row(c, t, k, key, PA, 0, 0, 1, 2, 2);
            row(c, t, k, key, PB, 0, 0, 0, 1, 1);
            row(c, t, k, key, PAX, 0, 0, 0, 1, 0);
            row(c, t, k, key, HB, 0, 0, 0, 1, 1);
        }

        // Museums.
        exec(c, "insert into museum (code, subfamily_count, genus_count, species_count, valid_species_count, specimen_count, image_count,"
            + " imaged_specimen_count, taxon_subfamily_dist_json, specimen_subfamily_dist_json) values"
            + " ('CASC', 1, 1, 3, 2, 4, 3, 2, '" + json("Myrmicinae", "3") + "', '" + json("Myrmicinae", "4") + "'),"
            + " ('MCZC', 1, 1, 1, 1, 1, 1, 0, '" + json("Ponerinae", "1") + "', '" + json("Ponerinae", "1") + "')");
        row(c, "museum_taxon", "code", "'CASC'", FAMILY, 1, 1, 2, 3, 3);
        row(c, "museum_taxon", "code", "'CASC'", MYRM, 0, 1, 2, 3, 3);
        row(c, "museum_taxon", "code", "'CASC'", PHEIDOLE, 0, 0, 2, 3, 3);
        row(c, "museum_taxon", "code", "'CASC'", PA, 0, 0, 1, 2, 2);
        row(c, "museum_taxon", "code", "'CASC'", PB, 0, 0, 0, 1, 1);
        row(c, "museum_taxon", "code", "'CASC'", PAX, 0, 0, 0, 1, 0);
        row(c, "museum_taxon", "code", "'MCZC'", FAMILY, 1, 1, 1, 1, 1);
        row(c, "museum_taxon", "code", "'MCZC'", PON, 0, 1, 1, 1, 1);
        row(c, "museum_taxon", "code", "'MCZC'", HYPO, 0, 0, 1, 1, 1);
        row(c, "museum_taxon", "code", "'MCZC'", HB, 0, 0, 0, 1, 1);

        // Bioregion Malagasy: casent1, casent2, casent5.
        exec(c, "insert into bioregion (name, subfamily_count, genus_count, species_count, valid_species_count, specimen_count, image_count,"
            + " imaged_specimen_count, endemic_species_count, introduced_species_count, taxon_subfamily_dist_json, specimen_subfamily_dist_json) values"
            + " ('Malagasy', 2, 2, 2, 2, 3, 3, 1, 1, 0, '" + json("Myrmicinae", "1", "Ponerinae", "1") + "', '" + json("Myrmicinae", "2", "Ponerinae", "1") + "')");
        String b = "'Malagasy'";
        row(c, "bioregion_taxon", "bioregion_name", b, FAMILY, 2, 2, 2, 3, 3);
        row(c, "bioregion_taxon", "bioregion_name", b, MYRM, 0, 1, 1, 2, 2);
        row(c, "bioregion_taxon", "bioregion_name", b, PON, 0, 1, 1, 1, 1);
        row(c, "bioregion_taxon", "bioregion_name", b, PHEIDOLE, 0, 0, 1, 2, 2);
        row(c, "bioregion_taxon", "bioregion_name", b, HYPO, 0, 0, 1, 1, 1);
        row(c, "bioregion_taxon", "bioregion_name", b, PA, 0, 0, 0, 2, 2);
        row(c, "bioregion_taxon", "bioregion_name", b, HB, 0, 0, 0, 1, 1);
        exec(c, "update bioregion_taxon set is_endemic = 1 where taxon_name = '" + PA + "'");

        // Place images (PlaceImageCountDb). Madagascar, its subregion and the projects: h 1, p 1, l 2 (casent1, casent5).
        String[][] all = {{"geolocale", "" + MADAGASCAR}, {"geolocale", "" + INDIAN_OCEAN}, {"project", "madants"}, {"project", "allantwebants"}};
        for (String[] p : all) {
            String k = "('" + p[0] + "', '" + p[1] + "', ";
            exec(c, "insert into place_image_count (place_type, place_key, shot_type, image_count, specimen_count) values "
                + k + "'h', 1, 1), " + k + "'p', 1, 1), " + k + "'l', 2, 2), " + k + "'*all', 4, 3), " + k + "'*ant', 2, 2), " + k + "'*label_only', 1, 1)");
        }
        // CASC: casent1 (h, l), casent3 (p). MCZC: casent5 (l). Malagasy: casent1 (h, l), casent5 (l).
        String k = "('museum', 'CASC', ";
        exec(c, "insert into place_image_count (place_type, place_key, shot_type, image_count, specimen_count) values "
            + k + "'h', 1, 1), " + k + "'p', 1, 1), " + k + "'l', 1, 1), " + k + "'*all', 3, 2), " + k + "'*ant', 2, 2), " + k + "'*label_only', 0, 0)");
        k = "('museum', 'MCZC', ";
        exec(c, "insert into place_image_count (place_type, place_key, shot_type, image_count, specimen_count) values "
            + k + "'l', 1, 1), " + k + "'*all', 1, 1), " + k + "'*ant', 0, 0), " + k + "'*label_only', 1, 1)");
        k = "('bioregion', 'Malagasy', ";
        exec(c, "insert into place_image_count (place_type, place_key, shot_type, image_count, specimen_count) values "
            + k + "'h', 1, 1), " + k + "'l', 2, 2), " + k + "'*all', 3, 2), " + k + "'*ant', 1, 1), " + k + "'*label_only', 1, 1)");

        // Home page numbers: 3 valid species + ssp., 5 specimens, 2 species imaged, 2 specimens imaged, 4 images.
        exec(c, "insert into statistics (action, specimens, extant_taxa, total_images, specimens_imaged, species_imaged, created)"
            + " values ('nightly', 5, 3, 4, 2, 2, " + OLD + ")");
    }
}
