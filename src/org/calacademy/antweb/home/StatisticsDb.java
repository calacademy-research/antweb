package org.calacademy.antweb.home;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;

/**
 * Site numbers. populateStatistics writes a row of table statistics (specimens, images, imaged species, taxa)
 * after each specimen upload and AntCat import (UploadAction) and in nightly set 4; the home page and
 * statsPage.do show the latest row. statsPage.do also reads Global, Bioregion and Museum Data here, and the
 * "imaged" rule (ANT_IMAGE, antImagedNames: a photo of the ant, not a label) is shared with other pages.
 * Tables statistics, specimen, image, taxon and the four list tables. Docs: docs/numbers.md,
 * docs/flows/home-and-statistics.md. Tests: NumberDecisionsDbTest, tests/e2e/test_home_and_stats.py.
 */
public class StatisticsDb extends AntwebDb {
    
    private static final Log s_log = LogFactory.getLog(StatisticsDb.class);

    public StatisticsDb(Connection connection) {
      super(connection);
    }

/*

Current Statistics:

Extant: 17 valid subfamilies, 334 valid genera, 13500 valid species, 1900 valid subspecies
Fossil: 3 valid subfamilies, 152 valid genera, 751 valid species, 3 valid subspecies

Number and % of valid species imaged.  (3000? are they all in one genus? Distributed across groups?).

List of species not imaged would be useful.

For each:

bioregion, 
  Extant: 17 valid subfamilies, 334 valid genera, 13500 valid species, 1900 valid subspecies
museum 
  Extant: 17 valid subfamilies, 334 valid genera, 13500 valid species, 1900 valid subspecies
  Fossil: 3 valid subfamilies, 152 valid genera, 751 valid species, 3 valid subspecies


*/


    /*
     * Brian Fisher, 2026-10-22 (docs/numbers.md Q7): every table of statsPage.do counts ants only (Formicidae), as the
     * place pages do, and species and subspecies only when identified to genus. The rule is
     * PlaceTotals.validAntTaxonCondition, shared with the home page number (VALID_SPECIES_SQL) and the count check
     * (NumberChecks stats.global, stats.bioregions, stats.museums, stats.nonants). Until 2026-10-22 these tables counted
     * valid taxa of every family on the lists (B31).
     */
    static final String[] STAT_RANKS = {"subfamily", "genus", "species", "subspecies"};

    /** Global Data: valid ant taxa of the taxon table of one rank, extant (0) or fossil (1). */
    public static String globalCountSql(String rank, int fossil) {
        // Fossil: by the view Fossil's rule, genera and subfamilies derived from the fossil species too (2026-11-06)
        return "select count(*) from taxon t where " + (fossil == 1 ? PlaceTotals.fossilViewCondition("t", rank, null, null, null)
            : PlaceTotals.validAntTaxonCondition("t", rank, fossil));
    }

    /** Bioregion and Museum Data: valid ant taxa of one rank on a list (parameter: the place's key). */
    public static String listCountSql(String listTable, String keyColumn, String rank, int fossil) {
        return "select count(*) from taxon t join " + listTable + " l on l.taxon_name = t.taxon_name where l." + keyColumn + " = ? and "
            + (fossil == 1 ? PlaceTotals.fossilViewCondition("t", rank, "l", listTable, keyColumn) : PlaceTotals.validAntTaxonCondition("t", rank, fossil));
    }

    private int[] globalCounts(int fossil) throws SQLException {
        UtilDb utilDb = new UtilDb(getConnection());
        int[] n = new int[4];
        for (int i = 0; i < 4; i++) n[i] = utilDb.getCount(globalCountSql(STAT_RANKS[i], fossil));
        return n;
    }

    private int[] listCounts(String listTable, String keyColumn, String key, int fossil) throws SQLException {
        int[] n = new int[4];
        for (int i = 0; i < 4; i++) {
            try (PreparedStatement stmt = getConnection().prepareStatement(listCountSql(listTable, keyColumn, STAT_RANKS[i], fossil))) {
                stmt.setString(1, key);
                try (ResultSet r = stmt.executeQuery()) {
                    if (r.next()) n[i] = r.getInt(1);
                }
            }
        }
        return n;
    }

    private static StatSet statSet(String name, String prefix, int[] n) {
        return statSet(name, prefix, n, false);
    }

    /** fossil: the Fossil row's subfamilies and genera are those with fossil species (Brian Fisher, 2026-11-06). */
    private static StatSet statSet(String name, String prefix, int[] n, boolean fossil) {
        StatSet statSet = new StatSet(name);
        statSet.set1(fossil ? "Subfamilies with fossil species" : prefix + "Subfamiles", n[0]);
        statSet.set2(fossil ? "Genera with fossil species" : prefix + "Genera", n[1]);
        statSet.set3(prefix + "Species", n[2]);
        statSet.set4(prefix + "Subspecies", n[3]);
        return statSet;
    }

    public StatSet getExtantData() throws SQLException {
      return statSet("Extant", "Valid ", globalCounts(0));
    }

    public StatSet getFossilData() throws SQLException {
      return statSet("Fossil", "Valid ", globalCounts(1), true);
    }

// Bioregion data
    public ArrayList<StatSet> getBioregionData() throws SQLException {
      ArrayList<StatSet> statSets = new ArrayList<>();
      ArrayList<Bioregion> bioregions = BioregionMgr.getBioregions();
      if (bioregions == null) return statSets;
      
      for (Bioregion bioregion : bioregions) {
        statSets.add(getBioregionData(bioregion));
      }
      return statSets;
    }
    
    public StatSet getBioregionData(Bioregion bioregion) throws SQLException {
      return statSet(bioregion.getName(), "", listCounts("bioregion_taxon", "bioregion_name", bioregion.getName(), 0));
    }

// --- Museum Data ---
    public ArrayList<StatSet> getExtantMuseumData() throws SQLException {
      ArrayList<StatSet> statSets = new ArrayList<>();
      ArrayList<Museum> museums = MuseumMgr.getMuseums();
      if (museums == null) return statSets;
      for (Museum museum : museums) {
        statSets.add(getExtantMuseumData(museum));
      }
      return statSets;
    }
    
    public StatSet getExtantMuseumData(Museum museum) throws SQLException {
      return statSet(museum.getCode(), "", listCounts("museum_taxon", "code", museum.getCode(), 0));
    }

    public ArrayList<StatSet> getFossilMuseumData() throws SQLException {
      ArrayList<StatSet> statSets = new ArrayList<>();
      ArrayList<Museum> museums = MuseumMgr.getMuseums();
      if (museums == null) return statSets;      
      for (Museum museum : museums) {
        statSets.add(getFossilMuseumData(museum));
      }
      return statSets;
    }

    public StatSet getFossilMuseumData(Museum museum) throws SQLException {
      return statSet(museum.getCode(), "Valid ", listCounts("museum_taxon", "code", museum.getCode(), 1), true);
    }

    public StatSet getImageData(Museum museum) throws SQLException {
      return getFossilMuseumData(museum);   // not used; was a copy of getFossilMuseumData
    }


    // Everything below is not used and unnecessary. Oops. Really?
    // for statistics.do
    public String getStatistics() throws SQLException {
        String statistics = null;
        try {
            Statement stmt = getConnection().createStatement();              

            String query = "select count(*) from specimen";            
            ResultSet resultSet = stmt.executeQuery(query);
            int count = 0;
            while (resultSet.next()) {
                count = resultSet.getInt(1);
            }            
            statistics = "<br><br>current specimen count:<b>" + count + "</b><br><br>";

            query = "select id, action, specimens, extant_taxa, total_taxa, proj_taxa, bioregion_taxa, museum_taxa, geolocale_taxa, geolocale_taxa_introduced, geolocale_taxa_endemic " 
              + " , total_images, specimens_imaged, species_imaged, valid_species_imaged, login_id, created, exec_time from statistics " 
              + " order by created desc limit 1000";
            resultSet = stmt.executeQuery(query);

            statistics += "<table border=1><tr><td>Id</td><td>Action </td><td>Specimens</td><td>V. Sp.</td>" 
              + "<td>Total Taxa</td><td>Project Taxa</td><td>Bioregion Taxa</td><td>Museum Taxa</td><td>Geolocale Taxa</td><td>Introduced</td><td>Endemic</td><td title='Images of the ant since 2026-10-27; rows before counted every image file, labels included'>Total Images</td><td>Specimens Imaged </td>" 
              + "<td>Species Imaged </td><td>V. Sp. Imaged </td><td>Login Id</td><td>Created</td><td>Exec Time</td></tr>";

            count = 0;
            while (resultSet.next()) {
                ++count;
                int id = resultSet.getInt("id");
                String action = resultSet.getString("action");
                if (action == null) action = "";
                int specimens = resultSet.getInt("specimens");
                int extantTaxa = resultSet.getInt("extant_taxa");
                int totalTaxa = resultSet.getInt("total_taxa");
                int projTaxa = resultSet.getInt("proj_taxa");
                int bioregionTaxa = resultSet.getInt("bioregion_taxa");
                int museumTaxa = resultSet.getInt("museum_taxa");
                int geolocaleTaxa = resultSet.getInt("geolocale_taxa");
                int geolocaleTaxaIntroduced = resultSet.getInt("geolocale_taxa_introduced");
                int geolocaleTaxaEndemic = resultSet.getInt("geolocale_taxa_endemic");
                int totalImages = resultSet.getInt("total_images");
                int specimensImaged = resultSet.getInt("specimens_imaged");
                int speciesImaged = resultSet.getInt("species_imaged");
                int loginId = resultSet.getInt("login_id");
                Timestamp timestamp = resultSet.getTimestamp("created");
                String execTime = resultSet.getString("exec_time");
                if (execTime == null || "null".equals(execTime)) execTime = "n/a";
                int validSpeciesImaged = resultSet.getInt("valid_species_imaged");
                String formatDate = timestamp.toString();

                String specimensStr = "" + specimens;
                if (count == 1) specimensStr = "<a href=\"" + AntwebProps.getDomainApp() + "/statistics.do?byUpload=true\">" + A.commaFormat(specimens) + "</a>";

                String projectStr = "" + A.commaFormat(projTaxa);
                if (count == 1) projectStr = "<a href=\"" + AntwebProps.getDomainApp() + "/statistics.do?byProject=true\">" + A.commaFormat(projTaxa) + "</a>";

                String bioregionStr = "" + A.commaFormat(bioregionTaxa);
                if (count == 1) bioregionStr = "<a href=\"" + AntwebProps.getDomainApp() + "/statistics.do?byBioregion=true\">" + A.commaFormat(bioregionTaxa) + "</a>";

                String museumStr = "" + A.commaFormat(museumTaxa);
                if (count == 1) museumStr = "<a href=\"" + AntwebProps.getDomainApp() + "/statistics.do?byMuseum=true\">" + A.commaFormat(museumTaxa) + "</a>";

                String geolocaleStr = "" + A.commaFormat(geolocaleTaxa);
                if (count == 1) geolocaleStr = "<a href=\"" + AntwebProps.getDomainApp() + "/statistics.do?byGeolocale=true\">" + A.commaFormat(geolocaleTaxa) + "</a>";

                statistics += "<tr><td>" + id + "</td><td>" + Html.esc(action) + "</td><td>" + specimensStr + "</td>" 
                + "<td>" + A.commaFormat(extantTaxa) + "</td><td>"+ A.commaFormat(totalTaxa) + "</td>" 
                + "<td>" + projectStr + "</td>" + "<td>" + bioregionStr + "</td>" + "<td>" + museumStr + "</td>"
                + " <td>" + geolocaleStr + "</td><td>" + A.commaFormat(geolocaleTaxaIntroduced) + "</td><td>" + A.commaFormat(geolocaleTaxaEndemic) + "</td>" 
                + " <td>" + A.commaFormat(totalImages) + "</td><td>" + A.commaFormat(specimensImaged) + "</td><td>" + A.commaFormat(speciesImaged) + "</td>" 
                + " <td>" + A.commaFormat(validSpeciesImaged) + "</td><td>" + loginId + "</td><td>" + formatDate + "</td>" 
                + " <td>" + execTime + "</td></tr>";
            }
            statistics += "</table>";

            stmt.close();
        } catch (SQLException e) {
          s_log.error("execute() e:" + e);
          throw e;
        }
        return statistics;
	}

    public ArrayList<ArrayList<String>> getStatisticsByProject() {
        ArrayList<ArrayList<String>> statistics = null;
        try {
            statistics = ProjTaxonDb.getStatisticsByProject(getConnection());

        } catch (SQLException e) {
          s_log.error("getStatisticsByProject() e:" + e);        
        }
        
        return statistics;
	}

    public ArrayList<ArrayList<String>> getStatisticsByBioregion() throws SQLException {
        ArrayList<ArrayList<String>> statistics = null; 
        try {
            statistics = BioregionTaxonDb.getStatisticsByBioregion(getConnection());
        } catch (SQLException e) {
          s_log.error("getStatisticsByBioregion() e:" + e);
          throw e;
        }
        return statistics;
	}

    public ArrayList<ArrayList<String>> getStatisticsByMuseum() throws SQLException {
		ArrayList<ArrayList<String>> statistics = null;		
        Connection connection = null;
        try {
            statistics = MuseumTaxonDb.getStatisticsByMuseum(getConnection());          
        } catch (SQLException e) {
          s_log.error("getStatisticsByMuseum() e:" + e);
          throw e;
        }
        return statistics;
	}

    public ArrayList<ArrayList<String>> getStatisticsByGeolocale() throws SQLException {
        ArrayList<ArrayList<String>> statistics = null;
        try {
            statistics = GeolocaleTaxonDb.getStatisticsByGeolocale(getConnection());
        } catch (SQLException e) {
            s_log.error("getStatisticsByGeolocale() e:" + e);
            throw e;
        }
        return statistics;
    }

    public ArrayList<ArrayList<String>> getStatisticsByAProject(String project) throws SQLException {
		ArrayList<ArrayList<String>> statistics = null;
        try {
            statistics = new ArrayList<>();
            statistics.add(ProjTaxonDb.getProjectStatistics(project, getConnection()));
         
        } catch (SQLException e) {
          s_log.error("execute() e:" + e);
          throw e;
        }
        
        return statistics;
	}
	
    public String getStatisticsByUpload() throws SQLException {
        String statistics = null;
        try {
            Statement stmt = getConnection().createStatement();              

            String query = "select count(code), name, id from specimen, ant_group where access_group = id group by access_group";
            ResultSet resultSet = stmt.executeQuery(query);

            statistics = "<table border=1><tr><td> Specimen Count </td><td> Group Name </td></tr>";
            while (resultSet.next()) {
                int count = resultSet.getInt(1);
                String groupName = resultSet.getString(2);
                int id = resultSet.getInt(3);
                statistics += "<tr><td>" + count + "</td><td>" + Html.esc(groupName) + "(" + id + ")" + "</td></tr>";
            }            
            statistics += "</table>";            
            
            stmt.close();         
        } catch (SQLException e) {
          s_log.error("execute() e:" + e);
          throw e;
        }
        
        return statistics;
	}

    /* Called by the Scheduler (or at startup?)
    /public void populateStatistics() throws SQLException, IOException {
        populateStatistics("routine", 0, null, AntwebProps.getDocRoot());
    }
*/

    /** An image of the ant: shot type not 'l' (label). Alias i. */
    public static final String ANT_IMAGE = "lower(trim(coalesce(i.shot_type, ''))) <> 'l'";

    /** The images of the ant of specimen s (alias i), labels left out: the Images column of every list counts these
     *  (Brian Fisher, 2026-10-27: "Images columns count ant images only"); count(i.id) per name. A left join, so a
     *  name whose specimens have no image of the ant still gets its row written (0), as before. */
    public static final String ANT_IMAGES_OF_SPECIMEN = " left join image i on i.image_of_id = s.code and " + ANT_IMAGE;

    /** Written to compute.log by nightly set 4 once the lists' Images are counted as ant images (2026-10-27). */
    public static final String ANT_IMAGES_RULE_LOG = "list Images counted as ant images, 2026-10-27";

    /** Specimen codes with an image of the ant, each once (alias x, column image_of_id). */
    static final String ANT_IMAGED = "(select i.image_of_id from image i where " + ANT_IMAGE + " group by i.image_of_id) x";

    /**
     * "Imaged" for a name (Brian Fisher, 2026-10-22, docs/numbers.md Q6, Q12): a specimen filed under the name, anywhere
     * in AntWeb, has a photo of the ant; label photos do not count, so a name with only label photos is unimaged. Used by
     * taxonomicPage.do?isImaged=false (TaxaPage.findUnimaged) and, in Python, by the API's /unimagedGeolocaleTaxa
     * (imagedTaxaSql in api/v3.1/api.py). Returns the names of the given ones that are imaged.
     */
    public static java.util.Set<String> antImagedNames(Connection connection, java.util.Collection<String> names) throws SQLException {
        java.util.Set<String> imaged = new java.util.HashSet<>();
        java.util.List<String> all = new java.util.ArrayList<>(names);
        for (int from = 0; from < all.size(); from += 500) {
            java.util.List<String> part = all.subList(from, Math.min(all.size(), from + 500));
            StringBuilder in = new StringBuilder();
            for (int i = 0; i < part.size(); i++) in.append(i == 0 ? "?" : ", ?");
            try (PreparedStatement stmt = connection.prepareStatement(ANT_IMAGED_NAMES_SQL + " and s.taxon_name in (" + in + ")")) {
                for (int i = 0; i < part.size(); i++) stmt.setString(i + 1, part.get(i));
                try (ResultSet r = stmt.executeQuery()) {
                    while (r.next()) imaged.add(r.getString(1));
                }
            }
        }
        return imaged;
    }

    /** Names with a photo of the ant anywhere in AntWeb (antImagedNames; add "and s.taxon_name in (...)"). */
    public static final String ANT_IMAGED_NAMES_SQL = "select distinct s.taxon_name from specimen s join image i on i.image_of_id = s.code where " + ANT_IMAGE;

    /** Home and statsPage "specimens imaged". */
    /** Home and statsPage "total specimen images": images of the ant (labels counted apart; Brian Fisher 2026-10-27). */
    public static final String TOTAL_IMAGES_SQL = "select count(*) from image i where " + ANT_IMAGE;
    /** The same number before 2026-10-27: every image row, labels included. */
    public static final String TOTAL_IMAGES_SQL_BEFORE = "select count(*) from image";
    /** Label images (shown next to the total as "+ N labels"). */
    public static final String LABEL_IMAGES_SQL = "select count(*) from image i where not (" + ANT_IMAGE + ")";

    public static final String IMAGED_SPECIMENS_SQL = "select count(*) from " + ANT_IMAGED + " join specimen s on s.code = x.image_of_id";

    /** Home and statsPage "species + ssp. imaged": ant species and subspecies that are valid names or morphospecies
     *  (Brian Fisher, 2026-10-28, PlaceTotals.SPECIES_STATUSES; was the five counted statuses), as the statsPage text says. */
    public static final String IMAGED_SPECIES_SQL = "select count(distinct s.taxon_name) from " + ANT_IMAGED + " join specimen s on s.code = x.image_of_id"
        + " join taxon t on t.taxon_name = s.taxon_name where t.taxarank in ('species', 'subspecies')"
        + " and t.status in " + PlaceTotals.SPECIES_STATUSES + PlaceTotals.ANTS   // Q7 2026-10-22: ants only
        + " and " + PlaceTotals.identifiedToGenus("t");   // 2026-10-22: names not identified to genus are not species

    /** The same number by the rule before 2026-10-28 (the five counted statuses), so the count check can tell a number
     *  written before the change (pending until the next nightly run) from a wrong one. */
    public static final String IMAGED_SPECIES_SQL_BEFORE_20261028 = "select count(distinct s.taxon_name) from " + ANT_IMAGED + " join specimen s on s.code = x.image_of_id"
        + " join taxon t on t.taxon_name = s.taxon_name where t.taxarank in ('species', 'subspecies')"
        + " and t.status in ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable')" + PlaceTotals.ANTS
        + " and " + PlaceTotals.identifiedToGenus("t");

    /** statistics.valid_species_imaged. */
    public static final String VALID_SPECIES_IMAGED_SQL = "select count(distinct s.taxon_name) from " + ANT_IMAGED + " join specimen s on s.code = x.image_of_id"
        + " join taxon t on t.taxon_name = s.taxon_name where " + PlaceTotals.validAntSpeciesCondition("t");   // Q7 2026-10-22: ants only

    /** Home "valid species + ssp." (statistics.extant_taxa): valid ant species and subspecies, identified to genus, fossils
     *  included (2026-10-22, Q7: was valid species and subspecies of every family). */
    public static final String VALID_SPECIES_SQL = "select count(*) from taxon t where " + PlaceTotals.validAntSpeciesCondition("t");

    // ---- Home page numbers of 2026-11-06 (Brian Fisher, release 9.3.0): global diversity (AntCat) apart from AntWeb's
    // coverage. Valid ant species only (no subspecies), living apart from fossil as on statsPage Global Data (fossil 0
    // or 1), identified to genus; "with specimens" = at least one specimen record filed under the name; "imaged" = a
    // specimen filed under the name has a photo of the ant (labels never count; ANT_IMAGED). Subspecies, morphospecies
    // and imaged fossils are on statsPage. Each is a column of the statistics row (HOME_COLUMNS), recounted every morning
    // by the count check ("home").

    /** A valid ant species identified to genus, living (fossil 0) or fossil (fossil 1). */
    public static String validAntSpeciesOnly(String alias, int fossil) {
        return alias + ".status = 'valid' and " + alias + ".taxarank = 'species' and " + alias + ".fossil = " + fossil
            + PlaceTotals.ANTS.replace("t.", alias + ".") + " and " + PlaceTotals.identifiedToGenus(alias);
    }

    /** The names (alias t) with a specimen record. */
    static final String HAS_SPECIMEN = "exists (select 1 from specimen s where s.taxon_name = t.taxon_name)";
    /** The names (alias t) a specimen with a photo of the ant is filed under. */
    static final String HAS_ANT_IMAGE = "t.taxon_name in (select s.taxon_name from " + "(select i.image_of_id from image i where "
        + "lower(trim(coalesce(i.shot_type, ''))) <> 'l' group by i.image_of_id) x join specimen s on s.code = x.image_of_id)";

    public static final String LIVING_SPECIES_SQL = "select count(*) from taxon t where " + validAntSpeciesOnly("t", 0);
    public static final String FOSSIL_SPECIES_SQL = "select count(*) from taxon t where " + validAntSpeciesOnly("t", 1);
    public static final String LIVING_SPECIES_WITH_SPECIMENS_SQL = LIVING_SPECIES_SQL + " and " + HAS_SPECIMEN;
    public static final String LIVING_SPECIES_IMAGED_SQL = LIVING_SPECIES_SQL + " and " + HAS_ANT_IMAGE;
    public static final String FOSSIL_SPECIES_IMAGED_SQL = FOSSIL_SPECIES_SQL + " and " + HAS_ANT_IMAGE;
    /** Valid living ant subspecies (statsPage). */
    public static final String LIVING_SUBSPECIES_SQL = "select count(*) from taxon t where t.status = 'valid' and t.taxarank = 'subspecies'"
        + " and t.fossil = 0" + PlaceTotals.ANTS + " and " + PlaceTotals.identifiedToGenus("t");
    /** Ant morphospecies (species and subspecies rank, identified to genus) and those imaged (statsPage). */
    public static final String MORPHOSPECIES_SQL = "select count(*) from taxon t where t.status = 'morphotaxon' and t.taxarank in ('species', 'subspecies')"
        + PlaceTotals.ANTS + " and " + PlaceTotals.identifiedToGenus("t");
    public static final String MORPHOSPECIES_IMAGED_SQL = MORPHOSPECIES_SQL + " and " + HAS_ANT_IMAGE;

    /** statistics column -> the query that counts it (populateStatistics writes them; CountInvariants.home recounts them). */
    public static final LinkedHashMap<String, String> HOME_COLUMNS = new LinkedHashMap<>();
    static {
        HOME_COLUMNS.put("living_species", LIVING_SPECIES_SQL);
        HOME_COLUMNS.put("fossil_species", FOSSIL_SPECIES_SQL);
        HOME_COLUMNS.put("living_species_specimens", LIVING_SPECIES_WITH_SPECIMENS_SQL);
        HOME_COLUMNS.put("living_species_imaged", LIVING_SPECIES_IMAGED_SQL);
        HOME_COLUMNS.put("fossil_species_imaged", FOSSIL_SPECIES_IMAGED_SQL);
        HOME_COLUMNS.put("living_subspecies", LIVING_SUBSPECIES_SQL);
        HOME_COLUMNS.put("morphospecies", MORPHOSPECIES_SQL);
        HOME_COLUMNS.put("morphospecies_imaged", MORPHOSPECIES_IMAGED_SQL);
    }

    /** What each column is, for the count check and the hover text. */
    public static final java.util.Map<String, String> HOME_LABELS = java.util.Map.of(
        "living_species", "valid living ant species",
        "fossil_species", "fossil species",
        "living_species_specimens", "species with specimens in AntWeb",
        "living_species_imaged", "species imaged",
        "fossil_species_imaged", "fossil species imaged",
        "living_subspecies", "valid living subspecies",
        "morphospecies", "morphospecies",
        "morphospecies_imaged", "morphospecies imaged");

    private static volatile boolean s_homeColumnsChecked = false;

    /** Adds the HOME_COLUMNS to a statistics table made before 2026-11-06 (int, null for the rows written before). */
    public static void ensureHomeColumns(Connection connection) throws SQLException {
        if (s_homeColumnsChecked) return;
        try (Statement stmt = connection.createStatement()) {
            Set<String> have = new HashSet<>();
            try (ResultSet r = stmt.executeQuery("select column_name from information_schema.columns"
                    + " where table_schema = database() and table_name = 'statistics'")) {
                while (r.next()) have.add(r.getString(1).toLowerCase(Locale.ROOT));
            }
            for (String col : HOME_COLUMNS.keySet()) {
                if (!have.contains(col)) stmt.executeUpdate("alter table statistics add column " + col + " int null");
            }
        }
        s_homeColumnsChecked = true;
    }

    /** "87%": part of whole, rounded; "" when whole is 0. */
    public static String percent(long part, long whole) {
        return whole <= 0 ? "" : Math.round(100.0 * part / whole) + "%";
    }

    /** One number of the home page box (web/genInc/statistics.jsp): the number, its label, an optional grey line. */
    static String stat(String key, long n, String title, String label, String sub) {
        return "<div class=\"stat\" data-stat=\"" + key + "\"><span class=\"numbers\" title=\"" + title + "\">" + n + "</span>"
            + "<span class=\"stat-l\">" + label + "</span>" + (sub == null || sub.isEmpty() ? "" : "<span class=\"stat-s\">" + sub + "</span>")
            + "</div>\n";
    }

    /** The home page box, from the counted numbers (Brian Fisher, 2026-11-06). */
    public static String homeBoxHtml(long specimens, java.util.Map<String, Long> n, long antImages, long imagedSpecimens, long labelImages) {
        long living = n.getOrDefault("living_species", 0L);
        StringBuilder b = new StringBuilder();
        b.append(stat("specimens", specimens, "Specimen records in AntWeb", "specimen records", null));
        b.append(stat("living-species", living, "Valid living ant species in AntCat (species only, identified to genus); fossil species apart",
            "valid living ant species", "+ " + A.commaFormat(n.getOrDefault("fossil_species", 0L)) + " fossil species"));
        b.append(stat("species-with-specimens", n.getOrDefault("living_species_specimens", 0L),
            "Valid living ant species with at least one specimen record in AntWeb", "species with specimens in AntWeb",
            percent(n.getOrDefault("living_species_specimens", 0L), living) + " of valid living species"));
        b.append(stat("species-imaged", n.getOrDefault("living_species_imaged", 0L),
            "Valid living ant species with a photo of the ant (label photos do not count)", "species imaged",
            percent(n.getOrDefault("living_species_imaged", 0L), living) + " of valid living species"));
        b.append("<div class=\"stat-rule\"></div>\n");
        b.append(stat("images", antImages, "Images of the ant; label images are counted separately",
            "images of ants of " + A.commaFormat(imagedSpecimens) + " specimens", "+ " + A.commaFormat(labelImages) + " label images"));
        return b.toString();
    }

    /** The rules before 2026-10-22 (Q7, every family), only so the count check can tell a number written before the change
     *  (pending until the next nightly run) from a wrong one. */
    public static final String VALID_SPECIES_SQL_BEFORE_Q7 = "select count(*) from taxon where status = 'valid' and taxarank in ('species', 'subspecies')";
    public static final String IMAGED_SPECIES_SQL_BEFORE_Q7 = "select count(distinct s.taxon_name) from " + ANT_IMAGED + " join specimen s on s.code = x.image_of_id"
        + " join taxon t on t.taxon_name = s.taxon_name where t.taxarank in ('species', 'subspecies')"
        + " and t.status in ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable') and " + PlaceTotals.identifiedToGenus("t");
    public static final String VALID_SPECIES_IMAGED_SQL_BEFORE_Q7 = "select count(distinct s.taxon_name) from " + ANT_IMAGED + " join specimen s on s.code = x.image_of_id"
        + " join taxon t on t.taxon_name = s.taxon_name where t.taxarank in ('species', 'subspecies') and t.status = 'valid'";

    public void populateStatistics(String action, int loginId, String execTime, String docBase)
            throws SQLException, IOException {

            UtilDb utilDb = new UtilDb(getConnection());

            // number of specimens imaged: specimens with an image of the ant, labels not counted
            // (2026-10-21; as Imaged specimens on the place pages, PlaceImageCountDb).
            String query = IMAGED_SPECIMENS_SQL;
            int imagedSpecimens = utilDb.getCount(query);
            AntwebMgr.setImagedSpecimensCount(imagedSpecimens);
            //A.log("populateStatistics 1 query:" + query);

            // number of specimen records
            query = "select count(*) from specimen";
            int specimenRecords = utilDb.getCount(query);
            AntwebMgr.setSpecimensCount(specimenRecords);
            //A.log("populateStatistics 2 query:" + query);

            // number of species + spp. imaged: species and subspecies names with a specimen that has an image of
            // the ant (2026-10-21; was distinct genus, species, subspecies strings of specimens with any image,
            // so genus-only names, and labels, counted).
            query = IMAGED_SPECIES_SQL;
            int imagedSpecies = utilDb.getCount(query);
            AntwebMgr.setImagedSpeciesCount(imagedSpecies);

            //A.log("populateStatistics 3 query:" + query);
            // Valid species and subspecies imaged (admin statistics.do "V. Sp. Imaged"; 2026-10-21: it counted images).
            query = VALID_SPECIES_IMAGED_SQL;
            int validSpeciesImaged = utilDb.getCount(query);
            //A.log("populateStatistics 4 query:" + query);

            // images of the ant (2026-10-27: every image record, labels included, until then) and label images
            query = TOTAL_IMAGES_SQL;
            int totalImages = utilDb.getCount(query);
            AntwebMgr.setTotalImagesCount(totalImages);
            int labelImages = utilDb.getCount(LABEL_IMAGES_SQL);
            AntwebMgr.setLabelImagesCount(labelImages);
            //A.log("populateStatistics 5 query:" + query);

            // number of valid species in database
            query = VALID_SPECIES_SQL;
            //A.log("runStatistics() query:" + query);            
            int validSpecies = utilDb.getCount(query);
            AntwebMgr.setValidSpeciesCount(validSpecies);

            //A.log("populateStatistics 6 query:" + query);

            // the home page numbers of 2026-11-06 (HOME_COLUMNS)
            java.util.Map<String, Long> home = new LinkedHashMap<>();
            for (java.util.Map.Entry<String, String> e : HOME_COLUMNS.entrySet()) home.put(e.getKey(), (long) utilDb.getCount(e.getValue()));
            AntwebMgr.setHomeCounts(home);

            query = "select count(*) from taxon where taxarank in ('species', 'subspecies')";
            int numberTotalTaxa = utilDb.getCount(query);

            query = "select count(*) from proj_taxon";
            int numberProjTaxa = utilDb.getCount(query);
            //if (numberProjTaxa < 78000) AdminAlertMgr.add("Project_taxa below expected count:" + numberProjTaxa, getConnection());

            query = "select count(*) from bioregion_taxon";
            int numberBioregionTaxa = utilDb.getCount(query);

            query = "select count(*) from museum_taxon";
            int numberMuseumTaxa = utilDb.getCount(query);

            query = "select count(*) from geolocale_taxon";
            int numberGeolocaleTaxa = utilDb.getCount(query);

            query = "select count(*) from geolocale_taxon where is_introduced = 1";
            int numberGeolocaleTaxaIntroduced = utilDb.getCount(query);

            query = "select count(*) from geolocale_taxon where is_endemic = 1";
            int numberGeolocaleTaxaEndemic = utilDb.getCount(query);
            s_log.debug("populateStatistics query:" + query);

            if (docBase != null) {

                Path outputFile = Paths.get(docBase + "/web/genInc/" + "statistics.jsp");
                BufferedWriter outFile = Files.newBufferedWriter(outputFile);

                // here write out the results
                //outFile.write("number of types imaged: " + imagedTypes + "<br>\n");
            
                // The home page box (Brian Fisher, 2026-11-06, release 9.3.0): specimens, valid living species (+ fossil),
                // those with specimens and those imaged with their share, images of ants. Until then: valid species + ssp.,
                // specimen records, species + ssp. imaged (morphospecies included), specimens imaged, total images.
                outFile.write(homeBoxHtml(specimenRecords, home, totalImages, imagedSpecimens, labelImages));
                outFile.close();
            }

            if (AntwebProps.isDevMode() && execTime == null) {
                A.log("populateStatistcs() execTime:" + execTime + " Why is execTime null?");
                AntwebUtil.logShortStackTrace();
                /* Why is the execTime null?
                at org.calacademy.antweb.home.StatisticsDb.populateStatistics(StatisticsDb.java:446)
                at org.calacademy.antweb.upload.UploadAction.runStatistics(UploadAction.java:1383)
                at org.calacademy.antweb.upload.UploadAction.execute(UploadAction.java:207)
                */
            }

            String update = "insert into statistics "
                    + " (action, specimens, extant_taxa, total_taxa, proj_taxa, bioregion_taxa, museum_taxa, geolocale_taxa, geolocale_taxa_introduced, geolocale_taxa_endemic " 
                    + " , total_images, specimens_imaged, species_imaged, valid_species_imaged, login_id, exec_time) "  //
                    + " values ('" + action + "'," +  specimenRecords + "," + validSpecies + "," + numberTotalTaxa
                    + "," + numberProjTaxa + "," + numberBioregionTaxa + "," + numberMuseumTaxa + "," + numberGeolocaleTaxa + "," + numberGeolocaleTaxaIntroduced + "," + numberGeolocaleTaxaEndemic
                    + "," + totalImages + "," + imagedSpecimens 
                    + "," + imagedSpecies + "," + validSpeciesImaged + "," + loginId + ", '" + execTime + "')";
            int c = utilDb.runDml(update);
            // the home page numbers of 2026-11-06, on the row just written (columns added to older tables first)
            try {
                ensureHomeColumns(getConnection());
                StringBuilder set = new StringBuilder();
                for (java.util.Map.Entry<String, Long> e : home.entrySet()) set.append(set.length() == 0 ? "" : ", ").append(e.getKey()).append(" = ").append(e.getValue());
                utilDb.runDml("update statistics set " + set + " order by id desc limit 1");
            } catch (SQLException e) {
                s_log.error("populateStatistics() home columns e:" + e);
            }
    }
}
        