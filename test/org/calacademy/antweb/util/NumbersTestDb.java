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
 * species pheidole a (valid), pheidole b (morphotaxon), subspecies pheidole a x (valid; parent the genus, as uploads write
 * it: Taxon.getParentTaxonNameFromName; 2026-10-22, was pheidole a, the B25 case), hypoponera b (valid).
 *
 * Admin numbers (2026-10-22, NumberChecks): upload group 1 (all five specimens; one upload by login 5), one description
 * edit by login 5, one image upload by login 5 with 2 files, artist 1 took the four images; statistics row as
 * populateStatistics writes it; the taxon columns the API /taxa reads = the Global rows.
 *
 * Specimens (all collected in Madagascar):
 *   casent1  pheidole a      museum CASC  bioregion Malagasy  images h, l
 *   casent2  pheidole a      CASC         Malagasy            -
 *   casent3  pheidole b      CASC         -                   p
 *   casent4  pheidole a x    CASC         -                   -
 *   casent5  hypoponera b    MCZC         Malagasy            l (label only)
 *
 * Places: country Madagascar (7), its adm1 Atsinanana (8, no specimens), subregion Indian Ocean (3, its countries: Madagascar), project madants,
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
    public static final int ATSINANANA = 8;

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
        "museum_taxon", "bioregion", "bioregion_taxon", "place_image_count", "statistics", "upload", "specimen_name_update", "ant_group",
        "description_edit", "image_upload", "image_uploaded", "description_hist", "object_edit", "object_hist", "group_count"};

    /** Curator numbers as LoginMgr has them for the fixture (login 5): uploads, description edits, image uploads, images uploaded. */
    public static java.util.Map<Integer, int[]> curatorNumbers() {
        java.util.Map<Integer, int[]> m = new java.util.TreeMap<>();
        m.put(5, new int[] {1, 1, 1, 2});
        return m;
    }

    /** artists.do numbers of the fixture (ArtistMgr): artist 1 took 4 images of 3 specimens. */
    public static java.util.Map<Integer, int[]> artistNumbers() {
        java.util.Map<Integer, int[]> m = new java.util.TreeMap<>();
        m.put(1, new int[] {4, 3});
        return m;
    }

    /** Pie columns as db/upgrade made them: varchar(5000) (6.3: geolocale, bioregion, project); museum: chartSize. */
    static String placeColumns(int chartSize) {
        return "subfamily_count int default 0, genus_count int default 0, species_count int default 0,"
            + " valid_species_count int default 0, specimen_count int default 0, image_count int default 0, imaged_specimen_count int default 0,"
            + " taxon_subfamily_dist_json varchar(" + chartSize + "), specimen_subfamily_dist_json varchar(" + chartSize + ")";
    }

    static final String PLACE_COLUMNS = placeColumns(5000);

    static final String LIST_COLUMNS = "taxon_name varchar(128), subfamily_count int default 0, genus_count int default 0,"
        + " species_count int default 0, specimen_count int default 0, image_count int default 0, created timestamp null";

    /** Old rows: written before any nightly run the check looks at. */
    static final String OLD = "'2020-01-01 10:00:00'";

    public static void create(Connection c) throws SQLException {
        for (String t : TABLES) exec(c, "drop table if exists " + t);
        exec(c, "create table taxon (taxon_name varchar(128) primary key, taxarank varchar(20), status varchar(40), parent_taxon_name varchar(128),"
            + " family varchar(40), subfamily varchar(64), genus varchar(64), species varchar(64), fossil tinyint default 0, image_count int default 0,"
            + " current_valid_name varchar(128),"
            + " chart_color varchar(10), subfamily_count int default 0, genus_count int default 0, species_count int default 0, specimen_count int default 0)");
        exec(c, "create table specimen (code varchar(40) primary key, taxon_name varchar(128), status varchar(40), country varchar(64), adm1 varchar(64),"
            + " island_country varchar(64), museum varchar(64), bioregion varchar(64), upload_id int, image_count int default 0, family varchar(40),"
            + " subfamily varchar(64), genus varchar(64), species varchar(64), subspecies varchar(64), caste varchar(20), ownedBy varchar(200),"
            + " access_group int, flag varchar(10), collectioncode varchar(64), localitycode varchar(64), decimal_latitude decimal(10, 6))");
        exec(c, "create table image (id int primary key, image_of_id varchar(40), shot_type varchar(4), upload_date datetime, artist int)");
        exec(c, "create table geolocale (id int primary key, name varchar(128), georank varchar(20), parent varchar(128), is_valid tinyint, is_live tinyint,"
            + " is_island tinyint default 0, endemic_species_count int default 0, introduced_species_count int default 0, " + PLACE_COLUMNS + ")");
        exec(c, "create table geolocale_taxon (geolocale_id int, " + LIST_COLUMNS + ", is_endemic tinyint default 0, is_introduced tinyint default 0,"
            + " primary key (geolocale_id, taxon_name))");
        exec(c, "create table project (project_name varchar(64) primary key, is_live tinyint, scope varchar(20) default 'PROJECT', " + PLACE_COLUMNS + ")");
        exec(c, "create table proj_taxon (project_name varchar(64), " + LIST_COLUMNS + ", primary key (project_name, taxon_name))");
        // museum: varchar(10000) in db/upgrade/6.2, which also has a commented-out change to varchar(1000); staging and
        // production evidently have the smaller one (2026-10-21 "Data too long" on uploads). The smaller one here.
        exec(c, "create table museum (code varchar(64) primary key, " + placeColumns(1000) + ")");
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
        exec(c, "create table upload (upload_id int primary key, created timestamp null, login_id int, group_id int, specimens int default 0,"
            + " collections int default 0, localities int default 0, subfamilies int default 0, genera int default 0, species int default 0,"
            + " ungeoreferenced int default 0, flagged int default 0)");
        exec(c, "create table ant_group (id int primary key, name varchar(64), upload_count int default 0, upload_specimens int default 0)");
        exec(c, "create table description_edit (id int auto_increment primary key, access_login int, created timestamp null)");
        // page-text history and place / project texts (2026-10-22: curator Description Edits count all four)
        exec(c, "create table description_hist (hist_id int auto_increment primary key, access_login int, created timestamp null)");
        exec(c, "create table object_edit (edit_id int auto_increment primary key, object_key varchar(128), access_login int, created timestamp null)");
        exec(c, "create table object_hist (hist_id int auto_increment primary key, object_key varchar(128), access_login int, created timestamp null)");
        exec(c, org.calacademy.antweb.home.GroupCountDb.createTable());
        exec(c, "create table image_upload (id int primary key, curator_id int, group_id int, image_count int, complete tinyint default 0, created timestamp null)");
        exec(c, "create table image_uploaded (id int primary key, image_upload_id int, created timestamp null)");
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
        taxon(c, PAX, "subspecies", "valid", PHEIDOLE, MYRM, 0);
        taxon(c, HB, "species", "valid", HYPO, PON, 1);

        specimen(c, "casent1", PA, "valid", "CASC", "Malagasy", 2, MYRM);
        specimen(c, "casent2", PA, "valid", "CASC", "Malagasy", 0, MYRM);
        specimen(c, "casent3", PB, "morphotaxon", "CASC", null, 1, MYRM);
        specimen(c, "casent4", PAX, "valid", "CASC", null, 0, MYRM);
        specimen(c, "casent5", HB, "valid", "MCZC", "Malagasy", 1, PON);
        exec(c, "insert into image (id, image_of_id, shot_type, upload_date) values (1, 'casent1', 'h', " + OLD + "), (2, 'casent1', 'l', " + OLD + "), (3, 'casent3', 'p', " + OLD + "),"
            + " (4, 'casent5', 'l', " + OLD + ")");
        exec(c, "insert into upload (upload_id, created) values (1, " + OLD + ")");

        // Madagascar and its subregion: the same list and numbers.
        exec(c, "insert into geolocale (id, name, georank, parent, is_valid, is_live, subfamily_count, genus_count, species_count, valid_species_count,"
            + " specimen_count, image_count, imaged_specimen_count, endemic_species_count, introduced_species_count, taxon_subfamily_dist_json,"
            + " specimen_subfamily_dist_json) values"
            + " (" + MADAGASCAR + ", 'Madagascar', 'country', 'Indian Ocean', 1, 1, 2, 2, 4, 3, 5, 4, 2, 3, 0, '" + json("Myrmicinae", "3", "Ponerinae", "1")
            + "', '" + json("Myrmicinae", "4", "Ponerinae", "1") + "'),"
            + " (" + INDIAN_OCEAN + ", 'Indian Ocean', 'subregion', 'Africa', 1, 1, 2, 2, 4, 3, 5, 4, 2, 0, 0, '" + json("Myrmicinae", "3", "Ponerinae", "1")
            + "', '" + json("Myrmicinae", "4", "Ponerinae", "1") + "')");
        for (int g : new int[] {MADAGASCAR, INDIAN_OCEAN}) {
            String t = "geolocale_taxon";
            String k = "geolocale_id";
            row(c, t, k, "" + g, FAMILY, 2, 2, 4, 5, 4);
            row(c, t, k, "" + g, MYRM, 0, 1, 3, 4, 3);
            row(c, t, k, "" + g, PON, 0, 1, 1, 1, 1);
            row(c, t, k, "" + g, PHEIDOLE, 0, 0, 3, 4, 3);
            row(c, t, k, "" + g, HYPO, 0, 0, 1, 1, 1);
            row(c, t, k, "" + g, PA, 0, 0, 0, 2, 2);
            row(c, t, k, "" + g, PB, 0, 0, 0, 1, 1);
            row(c, t, k, "" + g, PAX, 0, 0, 0, 1, 0);
            row(c, t, k, "" + g, HB, 0, 0, 0, 1, 1);
        }
        // A state of Madagascar with no specimens yet (the adm1 checks, B26, have one place to look at).
        exec(c, "insert into geolocale (id, name, georank, parent, is_valid, is_live) values (" + ATSINANANA + ", 'Atsinanana', 'adm1', 'Madagascar', 1, 1)");
        exec(c, "update geolocale_taxon set is_endemic = 1 where geolocale_id = " + MADAGASCAR + " and taxon_name in ('" + PA + "', '" + PAX + "', '" + HB + "')");

        // Projects: madants (all its specimens, any status) and allantwebants (Global).
        exec(c, "insert into project (project_name, is_live, subfamily_count, genus_count, species_count, valid_species_count, specimen_count, image_count,"
            + " imaged_specimen_count, taxon_subfamily_dist_json, specimen_subfamily_dist_json) values"
            + " ('madants', 1, 2, 2, 4, 3, 5, 4, 2, '" + json("Myrmicinae", "3", "Ponerinae", "1") + "', '" + json("Myrmicinae", "4", "Ponerinae", "1") + "'),"
            + " ('allantwebants', 1, 2, 2, 4, 3, 5, 4, 2, '" + json("Myrmicinae", "3", "Ponerinae", "1") + "', '" + json("Myrmicinae", "4", "Ponerinae", "1") + "')");
        for (String p : new String[] {"madants", "allantwebants"}) {
            String t = "proj_taxon";
            String k = "project_name";
            String key = "'" + p + "'";
            row(c, t, k, key, FAMILY, 2, 2, 4, 5, 4);
            row(c, t, k, key, MYRM, 0, 1, 3, 4, 3);
            row(c, t, k, key, PON, 0, 1, 1, 1, 1);
            row(c, t, k, key, PHEIDOLE, 0, 0, 3, 4, 3);
            row(c, t, k, key, HYPO, 0, 0, 1, 1, 1);
            row(c, t, k, key, PA, 0, 0, 0, 2, 2);
            row(c, t, k, key, PB, 0, 0, 0, 1, 1);
            row(c, t, k, key, PAX, 0, 0, 0, 1, 0);
            row(c, t, k, key, HB, 0, 0, 0, 1, 1);
        }

        // Museums.
        exec(c, "insert into museum (code, subfamily_count, genus_count, species_count, valid_species_count, specimen_count, image_count,"
            + " imaged_specimen_count, taxon_subfamily_dist_json, specimen_subfamily_dist_json) values"
            + " ('CASC', 1, 1, 3, 2, 4, 3, 2, '" + json("Myrmicinae", "3") + "', '" + json("Myrmicinae", "4") + "'),"
            + " ('MCZC', 1, 1, 1, 1, 1, 1, 0, '" + json("Ponerinae", "1") + "', '" + json("Ponerinae", "1") + "')");
        row(c, "museum_taxon", "code", "'CASC'", FAMILY, 1, 1, 3, 4, 3);
        row(c, "museum_taxon", "code", "'CASC'", MYRM, 0, 1, 3, 4, 3);
        row(c, "museum_taxon", "code", "'CASC'", PHEIDOLE, 0, 0, 3, 4, 3);
        row(c, "museum_taxon", "code", "'CASC'", PA, 0, 0, 0, 2, 2);
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
        exec(c, "update statistics set total_taxa = (select count(*) from taxon where taxarank in ('species', 'subspecies')),"
            + " proj_taxa = (select count(*) from proj_taxon), bioregion_taxa = (select count(*) from bioregion_taxon),"
            + " museum_taxa = (select count(*) from museum_taxon), geolocale_taxa = (select count(*) from geolocale_taxon),"
            + " geolocale_taxa_introduced = (select count(*) from geolocale_taxon where is_introduced = 1),"
            + " geolocale_taxa_endemic = (select count(*) from geolocale_taxon where is_endemic = 1),"
            + " valid_species_imaged = (" + org.calacademy.antweb.home.StatisticsDb.VALID_SPECIES_IMAGED_SQL + ")");

        // The API /taxa columns = the Global rows (allantwebants).
        exec(c, "update taxon t join proj_taxon a on a.project_name = 'allantwebants' and a.taxon_name = t.taxon_name set t.subfamily_count = a.subfamily_count,"
            + " t.genus_count = a.genus_count, t.species_count = a.species_count, t.specimen_count = a.specimen_count");

        // Upload group 1 (all specimens), its one upload by login 5 with the numbers UploadDb.updateCounts writes.
        exec(c, "update specimen set access_group = 1, collectioncode = if(code in ('casent1', 'casent2'), 'c1', 'c2'), localitycode = 'l1',"
            + " decimal_latitude = if(code = 'casent5', null, -18.9), genus = substring_index(substring_index(taxon_name, ' ', 1), 'inae', -1),"
            + " species = substring_index(substring_index(taxon_name, ' ', 2), ' ', -1)");
        exec(c, "update upload set login_id = 5, group_id = 1, specimens = 5, collections = 2, localities = 1, ungeoreferenced = 1, flagged = 0,"
            + " subfamilies = 2, genera = 2, species = 2");
        exec(c, "insert into ant_group values (1, 'CAS', 1, 5)");
        // today's numbers of the group as the nightly recount writes them (GroupCountDb, 2026-10-22)
        exec(c, "insert into group_count (group_id, specimens, collections, localities, ungeoreferenced, flagged, subfamilies, genera, species, counted)"
            + " values (1, 5, 2, 1, 1, 0, 2, 2, 2, " + OLD + ")");
        exec(c, "insert into description_edit (access_login, created) values (5, " + OLD + ")");
        exec(c, "insert into image_upload values (1, 5, 1, 2, 1, " + OLD + ")");
        exec(c, "insert into image_uploaded values (1, 1, " + OLD + "), (2, 1, " + OLD + ")");
        exec(c, "update image set artist = 1");
    }
}
