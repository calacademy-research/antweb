package org.calacademy.antweb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.geolocale.Region;
import org.calacademy.antweb.geolocale.Subregion;
import org.calacademy.antweb.home.CuratorTaxonSetDb;
import org.calacademy.antweb.home.PlaceTotals;
import org.calacademy.antweb.home.SpecimenDb;
import org.calacademy.antweb.util.DBUtil;

/**
 * The four views of a list (Brian Fisher, 2026-11-02; docs/numbers.md "Views of lists"): Valid, Curated (the default
 * everywhere), All specimen names and Fossil, the same on every kind of list (countries, adm1s, regions, subregions,
 * bioregions, museums, projects, All AntWeb). StatusSet holds the address values and each view's criteria; this class
 * holds what the views add to them:
 *
 *  - Curated morphospecies (vettedCondition). A morphospecies (status morphotaxon) is curated when it is on any curated
 *    list entry: a geolocale_taxon or proj_taxon row whose source is one a curator writes with the Species List Tool or a
 *    list upload (CuratorTaxonSetDb.CURATOR_SOURCES: curator, speciesList, speciesListTool, speciesListUpload; not the
 *    proxy rows of parent regions, not the system lists worldants and allantwebants). Vetting belongs to the
 *    morphospecies: once curated it is in the Curated view of every place where it is on the list or, on country, adm1,
 *    region and subregion lists, where its not red-flagged specimens were collected (bioregion, museum and All AntWeb
 *    lists already take every specimen name). A curator who removes it from one place's list (a dispute,
 *    geolocale_taxon_dispute or proj_taxon_dispute) takes it off that place only (notDisputed). Computed at query time:
 *    one indexed lookup of geolocale_taxon by taxon_name per morphospecies row, then one primary-key lookup of
 *    proj_taxon per curated project; no stored flag, so a curator's change shows at once.
 *  - The specimen names of geolocale lists (addsSpecimenNames, specimenNamesSet, collectedHere): country and adm1 lists
 *    take valid names from specimens but not morphospecies or indet names, so Curated adds the curated morphospecies and
 *    All specimen names every counted name collected there (TaxaPage.specimenNamesWhere).
 *  - The genus rows (GenusRows): each row's Species in the view, its Not curated number (counted nightly,
 *    home.PlaceGenusCurationDb; the specimens of the genus collected here whose name is not curated:
 *    notCuratedCondition), its species imaged (Images: "N species imaged", StatisticsDb.antImagedNames), and which genus
 *    rows the views Valid and Curated show: a genus known in the place only from specimens identified to genus
 *    ("Acropyga (indet)": indetermined, unidentifiable) appears only in All specimen names (keepGenus).
 *  - The place's specimens by name (PlaceSpecimens), red-flagged ones never counted: the specimen numbers of rows added
 *    from specimens, the source icon (specimens trump literature: sourceKind) and the names the views add. Genus lists
 *    read them from place_specimen_name (stored nightly with the Not curated numbers: loadStoredPlaceSpecimens; 2026-11-02,
 *    speed: reading the place's specimens took 1.4 to 2.2 s per genus list), species lists from the specimen table.
 *  - The view Not curated (StatusSet.LIST_NOT_CURATED, reached from a genus row's Not curated number, not one of the four
 *    buttons): the names whose specimens that number counts.
 *
 * Tests without a database: test.org.calacademy.antweb.ListViewTest; SQL against MySQL: home.PlaceTaxonNotesDbTest.
 */
public final class ListView {

    private ListView() {
    }

    // ------------------------------------------------------------------------------------------------- labels

    /**
     * The hover text of a view's button (title attribute). pluralRank: the rows' rank ("species", "genera",
     * "subfamilies").
     */
    public static String choiceTitle(String choice, String pluralRank) {
        String r = pluralRank == null ? "names" : pluralRank.toLowerCase(Locale.ROOT);
        if (StatusSet.LIST_VALID.equals(choice)) return "Valid " + r + ": names AntCat calls valid, from specimens, curated entries or literature";
        if (StatusSet.LIST_VETTED.equals(choice)) return "Valid " + r + " and curated morphospecies (morphospecies a curator has put on a species list)";
        if (StatusSet.LIST_ALL.equals(choice)) return "Every name with specimens here: also the other morphospecies and the specimens identified only to genus";
        if (StatusSet.LIST_FOSSIL.equals(choice)) return "Fossil " + r;
        if (StatusSet.LIST_NOT_CURATED.equals(choice)) return "Names with specimens here that are not curated: morphospecies no curator has put on a list, specimens identified only to genus, names not in AntCat";
        return null;
    }

    /** The plain-words description of the four views, for "About this list" (web/common/taxonSetHeaderNotes.jsp). */
    public static String[][] viewDescriptions() {
        return new String[][] {
            {"Valid", "names AntCat calls valid, from specimens collected here (red-flagged specimens never count), from"
                + " curators' entries or from the literature."},
            {"Curated", "Valid plus the curated morphospecies. A morphospecies is curated once a curator has put it on any"
                + " species list; it then shows wherever its specimens were collected, unless a curator removed it from"
                + " that place's list. This is the view a list opens with."},
            {"All specimen names", "Curated plus every other morphospecies and the specimens identified only to genus"
                + " (\"Acropyga (indet)\"). A genus known here only from such specimens is listed only in this view."},
            {"Fossil", "fossil names."},
        };
    }

    // ------------------------------------------------------------------------------------------------- SQL

    /**
     * SQL: the name (alias a: taxon, specimen, or another table with taxon_name) is on a curated entry of some list: a
     * geolocale_taxon or proj_taxon row with a curator's source (CuratorTaxonSetDb.CURATOR_SOURCES), outside the system
     * lists (CuratorTaxonSetDb.SYSTEM_PROJECTS). With status morphotaxon: a curated morphospecies; for a genus row (a
     * morphogenus), also a curated entry of one of its species.
     */
    public static String vettedCondition(String alias) {
        String sources = CuratorTaxonSetDb.curatorSourceList();
        StringBuilder sys = new StringBuilder("(");
        for (int i = 0; i < CuratorTaxonSetDb.SYSTEM_PROJECTS.size(); i++) {
            if (i > 0) sys.append(", ");
            sys.append("'").append(CuratorTaxonSetDb.SYSTEM_PROJECTS.get(i)).append("'");
        }
        sys.append(")");
        String own = "(exists (select 1 from geolocale_taxon vgt where vgt.taxon_name = " + alias + ".taxon_name and vgt.source in " + sources + ")"
            + " or exists (select 1 from project vp join proj_taxon vpt on vpt.project_name = vp.project_name"
            + " where vpt.taxon_name = " + alias + ".taxon_name and vpt.source in " + sources + " and vp.project_name not in " + sys + "))";
        // A morphogenus (a genus row: no space in its taxon name) is curated when a curated morphospecies of it is
        // (rule of 2026-11-02; review of #316: its own genus row is rarely a curator's entry). Matched by subfamily and
        // genus (taxon's genus_species_subspecies index, then geolocale_taxon and proj_taxon by taxon_name), not by a
        // name prefix: no LIKE wildcards ("_" in morphogenus names), no scan. Only genus rows pay for it.
        String sameGenus = "vts.genus = " + alias + ".genus and vts.subfamily = " + alias + ".subfamily and vts.status = 'morphotaxon'"
            + " and vts.taxarank in ('species', 'subspecies')";
        String under = "(instr(" + alias + ".taxon_name, ' ') = 0 and coalesce(" + alias + ".genus, '') <> '' and ("
            + "exists (select 1 from taxon vts join geolocale_taxon vgs on vgs.taxon_name = vts.taxon_name where " + sameGenus
            + " and vgs.source in " + sources + ")"
            + " or exists (select 1 from taxon vts join proj_taxon vpts on vpts.taxon_name = vts.taxon_name where " + sameGenus
            + " and vpts.source in " + sources + " and vpts.project_name not in " + sys + ")))";
        return "(" + own + " or " + under + ")";
    }

    /**
     * SQL: the name (alias a: taxon, specimen) is counted and not curated (Brian Fisher, 2026-11-02, the Not curated column
     * and view): of a counted status (StatusSet.getCountables) and neither a valid name nor a curated morphospecies
     * (vettedCondition). So uncurated morphospecies, names identified only to genus ("(indet)", indetermined,
     * unidentifiable) and names AntCat does not know (unrecognized). The nightly count (home.PlaceGenusCurationDb) and the
     * view use this one condition.
     */
    public static String notCuratedCondition(String alias) {
        return "(" + alias + ".status in " + StatusSet.getCountables().trim() + " and not (" + alias + ".status = '" + Status.VALID + "' or ("
            + alias + ".status = '" + Status.MORPHOTAXON + "' and " + vettedCondition(alias) + ")))";
    }

    /** Whether the view adds the names of specimens collected in the place: country, adm1, region and subregion lists. */
    public static boolean addsSpecimenNames(Overview overview) {
        // Countries and adm1s. A region's or subregion's list already holds its countries' specimen names (proxy rows of
        // the nightly), and reading every specimen of Africa per page took 3 to 6 s on staging (2026-11-02, review of #316).
        if (!(overview instanceof Country || overview instanceof Adm1)) return false;
        String c = PlaceTaxonNotes.placeSpecimenCriteria(overview);
        return c != null && !c.isEmpty();
    }

    /**
     * The set of the specimen names a view adds (TaxaPage.specimenNamesWhere), or null when it adds none: Curated the
     * curated morphospecies (StatusSet.VETTED_MORPHO), All specimen names every counted status (StatusSet.ALL_NAMES).
     */
    public static StatusSet specimenNamesSet(StatusSet viewSet) {
        if (viewSet == null) return null;
        if (StatusSet.VETTED.equals(viewSet.getValue())) return new StatusSet(StatusSet.VETTED_MORPHO);
        if (StatusSet.ALL_NAMES.equals(viewSet.getValue())) return new StatusSet(StatusSet.ALL_NAMES);
        if (StatusSet.NOT_CURATED.equals(viewSet.getValue())) return new StatusSet(StatusSet.NOT_CURATED);
        return null;
    }

    /** SQL: the name (alias) is the name of an ant specimen collected in the place (placeCriteria), not red-flagged. */
    public static String collectedHere(String alias, String placeCriteria) {
        return alias + ".taxon_name in (select specimen.taxon_name from specimen where" + SpecimenDb.getAntSpecimenCriteria("specimen")
            + " and " + SpecimenDb.notRedFlagged("specimen") + (placeCriteria == null ? "" : placeCriteria) + ")";
    }

    /**
     * SQL: the name (alias) is one of these (the names of the specimens collected in a place, read once by
     * loadPlaceSpecimens; the same rows as collectedHere without another scan of the specimen table). "1 = 0" when none.
     */
    public static String namedIn(String alias, java.util.Collection<String> names) {
        if (names == null || names.isEmpty()) return "1 = 0";
        StringBuilder b = new StringBuilder(alias).append(".taxon_name in (");
        boolean first = true;
        for (String n : names) {
            if (!first) b.append(", ");
            b.append(DBUtil.sqlLiteral(n));
            first = false;
        }
        return b.append(")").toString();
    }

    /** SQL (" and ...", or ""): the name (alias) is not disputed in the place (removed from its list by a curator). */
    public static String notDisputed(Overview overview, String alias) {
        if (overview instanceof Geolocale) {
            return " and not exists (select 1 from geolocale_taxon_dispute gtd where gtd.geolocale_id = " + ((Geolocale) overview).getId()
                + " and gtd.taxon_name = " + alias + ".taxon_name)";
        }
        if (overview instanceof Project) {
            return " and not exists (select 1 from proj_taxon_dispute ptd where ptd.project_name = " + DBUtil.sqlQuote(overview.getName())
                + " and ptd.taxon_name = " + alias + ".taxon_name)";
        }
        return "";
    }

    /**
     * The names removed from the place's list (disputes) with the date they were removed (Brian Fisher, 2026-11-03: All
     * specimen names shows them, marked). Null for places without disputes (museums, bioregions) or when there are none.
     */
    public static Map<String, java.util.Date> loadDisputes(Connection connection, Overview overview) throws SQLException {
        String q;
        if (overview instanceof Geolocale) {
            q = "select taxon_name, created from geolocale_taxon_dispute where geolocale_id = " + ((Geolocale) overview).getId();
        } else if (overview instanceof Project) {
            q = "select taxon_name, created from proj_taxon_dispute where project_name = " + DBUtil.sqlQuote(overview.getName());
        } else {
            return null;
        }
        Map<String, java.util.Date> out = new HashMap<>();
        try (java.sql.Statement s = connection.createStatement(); java.sql.ResultSet r = s.executeQuery(q)) {
            while (r.next()) out.put(r.getString(1), r.getTimestamp(2));
        }
        return out.isEmpty() ? null : out;
    }

    /** The mark of a name removed from the list, after its name in All specimen names ("" when not removed). */
    public static String removedMarkHtml(Map<String, java.util.Date> disputes, String taxonName) {
        if (disputes == null || taxonName == null || !disputes.containsKey(taxonName)) return "";
        java.util.Date d = disputes.get(taxonName);
        String date = d == null ? "" : " (" + new java.text.SimpleDateFormat("yyyy-MM-dd").format(d) + ")";
        return " <span class=\"removed-from-list\" title=\"Removed from this list with the Species List Tool; shown here because"
            + " specimens of this name were collected here\">removed from the list" + date + "</span>";
    }

    /** Whether a list reads the place's specimens as a whole (source icons, rows from specimens): not regions or subregions. */
    public static boolean readsPlaceSpecimens(Overview overview) {
        return !(overview instanceof Region || overview instanceof Subregion);
    }

    /**
     * SQL: the name (alias) was collected in the place, from the names already read (collected), or null to select them
     * with a subquery of the specimen table (collectedHere): stored names (place_specimen_name) by a subquery of that
     * table (indexed, no IN list limit); names read from the specimen table as an IN list up to TaxaPage.MAX_NAMED_IN.
     */
    public static String hereCondition(String alias, PlaceSpecimens collected) {
        if (collected == null) return null;
        if (collected.storedType != null) {
            return alias + ".taxon_name in (select psn.taxon_name from place_specimen_name psn where psn.list_type = "
                + DBUtil.sqlLiteral(collected.storedType) + " and psn.list_key = " + DBUtil.sqlLiteral(collected.storedKey) + ")";
        }
        if (collected.taxonNames().size() > TaxaPage.MAX_NAMED_IN) return null;
        return namedIn(alias, collected.taxonNames());
    }

    /** Whether the overview has the four views (not the AntCat list and Fossil Ants, which keep their own sets). */
    public static boolean hasViews(Overview overview) {
        return overview != null && !StatusSet.hasOwnStatusSets(overview.getName());
    }

    // ------------------------------------------------------------------------------------------------- fossil button

    /**
     * Request attribute (Boolean) read by web/common/statusesDisplay.jsp: whether the list has fossil names in the place,
     * so the Fossil button is shown (Brian Fisher, 2026-11-02: hide it otherwise, not greyed). Not set: shown.
     */
    public static final String HAS_FOSSIL_ATTR = "listHasFossil";

    /**
     * One EXISTS query: the rows of the view Fossil (the list's valid fossil names of rank rowRank: subfamily, genus or
     * species), of one subfamily or genus when given (a browse page's children).
     */
    public static String hasFossilSql(Overview overview, String rowRank, String subfamily, String genus) {
        String q = "select 1" + TaxaPage.listWhere(overview, rowRank, true, StatusSet.listStatusSet(StatusSet.LIST_FOSSIL));
        if (subfamily != null && !subfamily.isEmpty()) q += " and taxon.subfamily = " + DBUtil.sqlLiteral(subfamily);
        if (genus != null && !genus.isEmpty()) q += " and taxon.genus = " + DBUtil.sqlLiteral(genus);
        return q + " limit 1";
    }

    public static boolean hasFossil(Connection connection, Overview overview, String rowRank, String subfamily, String genus) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(hasFossilSql(overview, rowRank, subfamily, genus));
             ResultSet r = stmt.executeQuery()) {
            return r.next();
        }
    }

    /**
     * Sets HAS_FOSSIL_ATTR for a list with the four views (not the AntCat list and Fossil Ants: their own choices). On a
     * database error the attribute is not set and the button is shown.
     */
    public static void setHasFossil(jakarta.servlet.http.HttpServletRequest request, Connection connection, Overview overview,
            String rowRank, String subfamily, String genus) {
        if (!hasViews(overview) || rowRank == null) return;
        if (!(Rank.SUBFAMILY.equals(rowRank) || Rank.GENUS.equals(rowRank) || Rank.SPECIES.equals(rowRank))) return;
        try {
            request.setAttribute(HAS_FOSSIL_ATTR, hasFossil(connection, overview, rowRank, subfamily, genus));
        } catch (SQLException | RuntimeException e) {
            org.apache.commons.logging.LogFactory.getLog(ListView.class).warn("setHasFossil() overview:" + overview + " e:" + e);
        }
    }

    /** Whether the Fossil button is shown: the list has fossil names, or not known, or Fossil is the current view. */
    public static boolean showsFossilButton(Object hasFossilAttr, String current) {
        return StatusSet.LIST_FOSSIL.equals(current) || !Boolean.FALSE.equals(hasFossilAttr);
    }

    // ------------------------------------------------------------------------------------------------- place specimens

    /**
     * The not red-flagged ant specimens collected in a place, by name (one grouped query): {subfamily, genus, species,
     * subspecies, taxarank, status} and the number of specimens of each name.
     */
    public static final class PlaceSpecimens {
        final Map<String, String[]> names = new LinkedHashMap<>();
        final Map<String, Integer> counts = new HashMap<>();
        /** Read from place_specimen_name (the nightly count): its list_type and list_key; null when read from the specimens. */
        String storedType;
        String storedKey;

        /** Marks the names as those stored for the place (hereCondition then selects them from place_specimen_name). */
        public PlaceSpecimens stored(String type, String key) {
            this.storedType = type;
            this.storedKey = key;
            return this;
        }

        public boolean isStored() {
            return storedType != null;
        }

        /** Adds one name (also for tests). */
        public void add(String taxonName, String subfamily, String genus, String species, String subspecies, String taxarank,
                String status, int count) {
            if (taxonName == null || count <= 0) return;
            names.put(taxonName, new String[] {subfamily, genus, species, subspecies, taxarank, status});
            counts.merge(taxonName, count, Integer::sum);
        }

        /** The specimens of the name collected here (0 when none). */
        public int count(String taxonName) {
            Integer n = taxonName == null ? null : counts.get(taxonName);
            return n == null ? 0 : n;
        }

        public boolean has(String taxonName) {
            return count(taxonName) > 0;
        }

        public Set<String> taxonNames() {
            return names.keySet();
        }
    }

    /**
     * The query of PlaceSpecimens: the place's specimens grouped by name first, then the names' rows. Parameters, in this
     * order: the subfamily (bySubfamily), the genus (byGenus).
     */
    public static String placeSpecimensSql(String placeCriteria, boolean bySubfamily, boolean byGenus) {
        return placeSpecimensSql(placeCriteria, bySubfamily, byGenus, 0);
    }

    /** The same, of names only (nNames parameters after the others: the taxon names), when nNames > 0. */
    public static String placeSpecimensSql(String placeCriteria, boolean bySubfamily, boolean byGenus, int nNames) {
        StringBuilder in = new StringBuilder();
        if (nNames > 0) {
            in.append(" and specimen.taxon_name in (");
            for (int i = 0; i < nNames; i++) in.append(i == 0 ? "?" : ", ?");
            in.append(")");
        }
        return "select t.taxon_name, t.subfamily, t.genus, t.species, t.subspecies, t.taxarank, t.status, x.n"
            + " from (select specimen.taxon_name, count(*) n from specimen where" + SpecimenDb.getAntSpecimenCriteria("specimen")
            + " and " + SpecimenDb.notRedFlagged("specimen")
            + (bySubfamily ? " and specimen.subfamily = ?" : "") + (byGenus ? " and specimen.genus = ?" : "") + in
            + (placeCriteria == null ? "" : placeCriteria)
            + " group by specimen.taxon_name) x"
            + " join taxon t on t.taxon_name = x.taxon_name where t.family = 'formicidae'";
    }

    /**
     * The place's specimens by name, only a subfamily's or one genus's when given. Null for all of AntWeb and for places
     * that select no specimens (curated projects).
     */
    public static PlaceSpecimens loadPlaceSpecimens(Connection connection, Overview overview, String subfamily, String genus) throws SQLException {
        return loadPlaceSpecimens(connection, overview, subfamily, genus, null);
    }

    /** Names in one query of loadPlaceSpecimens with a list of names. */
    static final int NAMES_PER_QUERY = 500;

    /**
     * The same, of these names only when names is not null (species lists: the rows whose source icon or specimen number
     * needs it, not every name collected there), in queries of NAMES_PER_QUERY names.
     */
    public static PlaceSpecimens loadPlaceSpecimens(Connection connection, Overview overview, String subfamily, String genus,
            java.util.Collection<String> names) throws SQLException {
        String criteria = PlaceTaxonNotes.placeSpecimenCriteria(overview);
        if (connection == null || criteria == null || criteria.isEmpty()) return null;
        boolean bySubfamily = subfamily != null && !subfamily.isEmpty();
        boolean byGenus = genus != null && !genus.isEmpty();
        PlaceSpecimens out = new PlaceSpecimens();
        List<List<String>> chunks = new ArrayList<>();
        if (names == null) {
            chunks.add(null);
        } else {
            List<String> all = new ArrayList<>(new java.util.LinkedHashSet<>(names));
            for (int k = 0; k < all.size(); k += NAMES_PER_QUERY) chunks.add(all.subList(k, Math.min(all.size(), k + NAMES_PER_QUERY)));
        }
        for (List<String> chunk : chunks) {
            int nNames = chunk == null ? 0 : chunk.size();
            try (PreparedStatement stmt = connection.prepareStatement(placeSpecimensSql(criteria, bySubfamily, byGenus, nNames))) {
                int i = 0;
                if (bySubfamily) stmt.setString(++i, subfamily);
                if (byGenus) stmt.setString(++i, genus);
                if (chunk != null) for (String n : chunk) stmt.setString(++i, n);
                try (ResultSet r = stmt.executeQuery()) {
                    while (r.next()) {
                        out.add(r.getString("taxon_name"), r.getString("subfamily"), r.getString("genus"), r.getString("species"),
                            r.getString("subspecies"), r.getString("taxarank"), r.getString("status"), r.getInt("n"));
                    }
                }
            }
        }
        return out;
    }

    /**
     * The query of the stored names of a place (place_specimen_name, written by the nightly count with the Not curated
     * numbers): the same columns as placeSpecimensSql. Parameters: list_type, list_key, the subfamily (bySubfamily).
     */
    public static String storedPlaceSpecimensSql(boolean bySubfamily) {
        return "select t.taxon_name, t.subfamily, t.genus, t.species, t.subspecies, t.taxarank, t.status, p.specimens n"
            + " from place_specimen_name p join taxon t on t.taxon_name = p.taxon_name"
            + " where p.list_type = ? and p.list_key = ? and t.family = 'formicidae'" + (bySubfamily ? " and t.subfamily = ?" : "");
    }

    /**
     * The place's names as stored by the nightly count (one indexed read; 2026-11-02, speed: genus lists no longer read
     * the specimen table), only a subfamily's when given; null when the place has no stored names (regions,
     * subregions, projects) or its kind was never counted (table missing or empty: the caller reads the specimens).
     * After an upload the names catch up the next morning.
     */
    public static PlaceSpecimens loadStoredPlaceSpecimens(Connection connection, Overview overview, String subfamily) {
        String[] tk = org.calacademy.antweb.home.PlaceGenusCurationDb.typeAndKey(overview);
        if (connection == null || tk == null) return null;
        if (!org.calacademy.antweb.home.PlaceGenusCurationDb.namesFilled(connection, tk[0])) return null;
        boolean bySubfamily = subfamily != null && !subfamily.isEmpty();
        PlaceSpecimens out = new PlaceSpecimens().stored(tk[0], tk[1]);
        try (PreparedStatement stmt = connection.prepareStatement(storedPlaceSpecimensSql(bySubfamily))) {
            stmt.setString(1, tk[0]);
            stmt.setString(2, tk[1]);
            if (bySubfamily) stmt.setString(3, subfamily);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    out.add(r.getString("taxon_name"), r.getString("subfamily"), r.getString("genus"), r.getString("species"),
                        r.getString("subspecies"), r.getString("taxarank"), r.getString("status"), r.getInt("n"));
                }
            }
        } catch (SQLException e) {
            org.apache.commons.logging.LogFactory.getLog(ListView.class).warn("loadStoredPlaceSpecimens() overview:" + overview + " e:" + e);
            return null;
        }
        return out;
    }

    /**
     * The place's specimens by name for a genus list (one subfamily when given): the stored names; when there are none
     * yet, on country and adm1 lists (whose views add the names collected there) the specimen table, elsewhere none
     * (museum and bioregion lists hold their specimen names; the source icon then comes from the list row).
     */
    public static PlaceSpecimens genusListSpecimens(Connection connection, Overview overview, String subfamily) throws SQLException {
        PlaceSpecimens stored = loadStoredPlaceSpecimens(connection, overview, subfamily);
        if (stored != null) return stored;
        if (addsSpecimenNames(overview)) return loadPlaceSpecimens(connection, overview, subfamily, null);
        return null;
    }

    // ------------------------------------------------------------------------------------------------- view names

    /** The species and subspecies names of a view in a place: taxon name -> {subfamily, genus, status}. */
    public static final class ViewNames {
        final Map<String, String[]> names = new HashMap<>();

        public void add(String taxonName, String subfamily, String genus, String status) {
            if (taxonName != null) names.put(taxonName, new String[] {subfamily, genus, status});
        }

        public boolean contains(String taxonName) {
            return names.containsKey(taxonName);
        }

        public Set<String> taxonNames() {
            return names.keySet();
        }

        /** Of the names given (imaged ones), how many by genus (key OtherNames.key(subfamily, genus)). */
        public Map<String, Integer> countByGenusOf(java.util.Collection<String> which) {
            Map<String, Integer> out = new HashMap<>();
            if (which == null) return out;
            for (String n : which) {
                String[] v = names.get(n);
                if (v != null) out.merge(PlaceTaxonNotes.OtherNames.key(v[0], v[1]), 1, Integer::sum);
            }
            return out;
        }

        /** Names by genus (key OtherNames.key(subfamily, genus)); statuses: null for any, else only those. */
        public Map<String, Integer> countByGenus(List<String> statuses) {
            Map<String, Integer> out = new HashMap<>();
            for (String[] v : names.values()) {
                if (statuses != null && !statuses.contains(v[2])) continue;
                out.merge(PlaceTaxonNotes.OtherNames.key(v[0], v[1]), 1, Integer::sum);
            }
            return out;
        }
    }

    /** The query of a view's species and subspecies names (TaxaPage.viewQuery), of one subfamily or genus when given. */
    public static String viewNamesSql(Overview overview, StatusSet viewSet, String subfamily, String genus) {
        return viewNamesSql(overview, viewSet, subfamily, genus, null);
    }

    /** The same, the specimen half on the names collected here given (null: a subquery). */
    public static String viewNamesSql(Overview overview, StatusSet viewSet, String subfamily, String genus, java.util.Collection<String> collected) {
        String extra = "";
        if (subfamily != null && !subfamily.isEmpty()) extra += " and taxon.subfamily = " + DBUtil.sqlQuote(subfamily);
        if (genus != null && !genus.isEmpty()) extra += " and taxon.genus = " + DBUtil.sqlQuote(genus);
        return TaxaPage.viewQuery(overview, Rank.SPECIES, true, viewSet,
            "taxon.taxon_name as taxon_name, taxon.subfamily as subfamily, taxon.genus as genus, taxon.status as status", extra, collected);
    }

    public static ViewNames loadViewNames(Connection connection, Overview overview, StatusSet viewSet, String subfamily, String genus) throws SQLException {
        return loadViewNames(connection, overview, viewSet, subfamily, genus, null);
    }

    /** The same, the specimen half on the names collected here as hereCondition gives them (null: a subquery). */
    public static String viewNamesSqlHere(Overview overview, StatusSet viewSet, String subfamily, String genus, String here) {
        String extra = "";
        if (subfamily != null && !subfamily.isEmpty()) extra += " and taxon.subfamily = " + DBUtil.sqlQuote(subfamily);
        if (genus != null && !genus.isEmpty()) extra += " and taxon.genus = " + DBUtil.sqlQuote(genus);
        return TaxaPage.viewQueryHere(overview, Rank.SPECIES, true, viewSet,
            "taxon.taxon_name as taxon_name, taxon.subfamily as subfamily, taxon.genus as genus, taxon.status as status", extra, here);
    }

    public static ViewNames loadViewNames(Connection connection, Overview overview, StatusSet viewSet, String subfamily, String genus,
            PlaceSpecimens collected) throws SQLException {
        ViewNames out = new ViewNames();
        // stored names: a subquery of place_specimen_name; names read here: an IN list, above MAX_NAMED_IN names the
        // specimen half keeps its subquery (no very long IN list; review of #316)
        try (PreparedStatement stmt = connection.prepareStatement(viewNamesSqlHere(overview, viewSet, subfamily, genus,
                hereCondition("taxon", collected)));
             ResultSet r = stmt.executeQuery()) {
            while (r.next()) out.add(r.getString("taxon_name"), r.getString("subfamily"), r.getString("genus"), r.getString("status"));
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------- rules

    /** The statuses of a species-level name that show a genus is known from more than specimens identified to genus. */
    public static final List<String> BEYOND_GENUS_STATUSES = java.util.Arrays.asList(Status.VALID, Status.MORPHOTAXON, Status.UNRECOGNIZED);

    /**
     * The specimens collected here under names not in the view, by genus (the line under a genus page's species; the
     * Other specimens column of genus lists until 2026-11-02, replaced by Not curated): the place's names of species or
     * subspecies rank, of a counted status, identified to genus, that are not names of the view.
     */
    public static PlaceTaxonNotes.OtherNames otherNames(PlaceSpecimens specimens, ViewNames view) {
        if (specimens == null) return null;
        PlaceTaxonNotes.OtherNames out = new PlaceTaxonNotes.OtherNames();
        for (Map.Entry<String, String[]> e : specimens.names.entrySet()) {
            String[] v = e.getValue();
            if (!(Rank.SPECIES.equals(v[4]) || Rank.SUBSPECIES.equals(v[4]))) continue;
            if (!StatusSet.isAllAntwebAnts(v[5]) || !PlaceTotals.isIdentifiedToGenus(v[1])) continue;
            if (view != null && view.contains(e.getKey())) continue;
            out.add(v[0], v[1], e.getKey(), v[2], v[3], v[4], specimens.count(e.getKey()));
        }
        return out;
    }

    /**
     * Whether a genus row is shown in a view (Brian Fisher, 2026-11-02). All specimen names and Fossil: every row of the
     * view's SQL. Valid and Curated: a row with species in the view; else a valid genus known here from more than
     * specimens identified to genus: a list entry that is not from specimens (curator, literature, AntCat), or a name of
     * the genus on the list or collected here that is valid, a morphospecies or unrecognized (evidence). So Acropyga in
     * Mozambique, known from morphospecies, is in Valid with no species and its Not curated number; a genus known only from
     * "(indet)" specimens is only in All specimen names; a morphogenus is in Curated only with a curated morphospecies.
     */
    public static boolean keepGenus(String choice, String status, String sourceKind, int speciesInView, int evidence) {
        if (!(StatusSet.LIST_VALID.equals(choice) || StatusSet.LIST_VETTED.equals(choice))) return true;
        if (speciesInView > 0) return true;
        if (!Status.VALID.equals(status)) return false;
        return !SOURCE_SPECIMEN.equals(sourceKind) || evidence > 0;
    }

    /**
     * The source icons of a row (rule of 2026-11-02): specimen, curated, literature and AntCat. Literature is the
     * literature entries of the list (source literature); a row from the AntCat import (source worldants, the AntCat type
     * localities) has its own label, "AntCat" (Brian Fisher, 2026-11-02: literature means Bolton's catalogue only).
     */
    public static final String SOURCE_SPECIMEN = "specimen";
    public static final String SOURCE_CURATED = "curated";
    public static final String SOURCE_LITERATURE = "literature";
    public static final String SOURCE_ANTCAT = "antcat";

    /**
     * The source of a row as one of the three icons: specimen when the name has a specimen collected here (not
     * red-flagged; "specimens trump literature", even when its list entry is from the literature), else by the list
     * entry's source (Source.getSourceCanonical): specimen; curator -> curated; literature -> literature; the AntCat
     * import (worldants, antcat) -> antcat. A row with no source (museum lists, rows added from specimens): specimen.
     */
    public static String sourceKind(String rowSource, boolean hasPlaceSpecimen) {
        if (hasPlaceSpecimen) return SOURCE_SPECIMEN;
        String c = Source.getSourceCanonical(rowSource);
        if (Source.CURATOR.equals(c)) return SOURCE_CURATED;
        if (Source.LITERATURE.equals(c)) return SOURCE_LITERATURE;
        if (Source.ANTCAT.equals(c)) return SOURCE_ANTCAT;
        return SOURCE_SPECIMEN;
    }

    /** The word of a source icon (legend, screen readers): "Specimen", "Curated", "Literature", "AntCat". */
    public static String sourceWord(String kind) {
        if (SOURCE_CURATED.equals(kind)) return "Curated";
        if (SOURCE_LITERATURE.equals(kind)) return "Literature";
        if (SOURCE_ANTCAT.equals(kind)) return "AntCat";
        return "Specimen";
    }

    /** The hover text of a source icon: the word, or "From the AntCat import" for AntCat. */
    public static String sourceTitle(String kind) {
        return SOURCE_ANTCAT.equals(kind) ? "From the AntCat import" : sourceWord(kind);
    }

    /**
     * The icon of a source (an inline mark, styled by .src-icon in antweb.css), its hover text as title and its word
     * for screen readers. AntCat is shown as the word itself (a small label), not a symbol.
     */
    public static String sourceIconHtml(String kind) {
        String word = sourceWord(kind);
        String glyph = SOURCE_CURATED.equals(kind) ? "&#10003;" : SOURCE_LITERATURE.equals(kind) ? "&#128214;"
            : SOURCE_ANTCAT.equals(kind) ? "AntCat" : "&#9673;";
        return "<span class=\"src-icon src-" + kind + "\" data-source=\"" + kind + "\" title=\"" + sourceTitle(kind) + "\" role=\"img\" aria-label=\""
            + word + "\">" + glyph + "</span>";
    }

    // ------------------------------------------------------------------------------------------------- genus rows

    /** The numbers of a view's genus rows in a place (genus lists, the genus rows of a subfamily's browse page). */
    public static final class GenusRows {
        public final String choice;
        final Map<String, Integer> speciesInView;
        final Map<String, Integer> evidence;
        /** The genera (OtherNames.key) with a specimen collected here, not red-flagged (the specimen source icon). */
        final Set<String> generaWithSpecimens = new HashSet<>();
        /** The stored Not curated numbers by genus (home.PlaceGenusCurationDb), or null (not counted, or no column). */
        Map<String, org.calacademy.antweb.home.PlaceGenusCurationDb.Row> curation;
        /** Whether the place has the Not curated column (countries, adm1s, museums, bioregions). */
        boolean placeHasCuration;
        /** The species in the view with an image of the ant, by genus (the Images cell); null when not read. */
        Map<String, Integer> imaged;

        public GenusRows(String choice, Map<String, Integer> speciesInView, Map<String, Integer> evidence) {
            this.choice = choice;
            this.speciesInView = speciesInView == null ? new HashMap<>() : speciesInView;
            this.evidence = evidence == null ? new HashMap<>() : evidence;
        }

        /** Marks the genera of the place's specimens (loadGenusRows; also for tests). */
        public GenusRows withSpecimens(PlaceSpecimens specimens) {
            if (specimens != null) for (String[] v : specimens.names.values()) generaWithSpecimens.add(PlaceTaxonNotes.OtherNames.key(v[0], v[1]));
            return this;
        }

        /** The stored Not curated numbers (null: cells empty); placeHasColumn: the place shows the column. */
        public GenusRows withCuration(boolean placeHasColumn, Map<String, org.calacademy.antweb.home.PlaceGenusCurationDb.Row> rows) {
            this.placeHasCuration = placeHasColumn;
            this.curation = rows;
            if (rows != null) for (org.calacademy.antweb.home.PlaceGenusCurationDb.Row r : rows.values()) {
                if (r.specimens > 0) generaWithSpecimens.add(PlaceTaxonNotes.OtherNames.key(r.subfamily, r.genus));
            }
            return this;
        }

        /** The species of the view with an image of the ant, by genus (also for tests). */
        public GenusRows withImaged(Map<String, Integer> imagedByGenus) {
            this.imaged = imagedByGenus;
            return this;
        }

        /** Whether a specimen of the genus was collected here (not red-flagged). */
        public boolean hasSpecimens(String subfamily, String genus) {
            return generaWithSpecimens.contains(PlaceTaxonNotes.OtherNames.key(subfamily, genus));
        }

        /** The genus row's species and subspecies in the view. */
        public int species(String subfamily, String genus) {
            Integer n = speciesInView.get(PlaceTaxonNotes.OtherNames.key(subfamily, genus));
            return n == null ? 0 : n;
        }

        /**
         * The Not curated column (Brian Fisher, 2026-11-02): in the views Valid and Curated (the same number in both), on
         * countries, adm1s, museums and bioregions; not in All specimen names (those names are all on the list there) or
         * Fossil, not on regions, subregions or projects.
         */
        public boolean showsNotCurated() {
            return placeHasCuration && (StatusSet.LIST_VALID.equals(choice) || StatusSet.LIST_VETTED.equals(choice));
        }

        /** The genus row's stored Not curated numbers, or null (column not shown, not counted, or none). */
        public org.calacademy.antweb.home.PlaceGenusCurationDb.Row notCurated(String subfamily, String genus) {
            if (!showsNotCurated() || curation == null) return null;
            return curation.get(org.calacademy.antweb.home.PlaceGenusCurationDb.key(subfamily, genus));
        }

        /** The genus row's species in the view with an image of the ant, or -1 when not read. */
        public int imaged(String subfamily, String genus) {
            if (imaged == null) return -1;
            Integer n = imaged.get(PlaceTaxonNotes.OtherNames.key(subfamily, genus));
            return n == null ? 0 : n;
        }

        public boolean keep(Taxon genusRow) {
            TaxonSet ts = genusRow.getTaxonSet();
            return keep(genusRow.getSubfamily(), genusRow.getGenus(), genusRow.getStatus(), ts == null ? null : ts.getSource());
        }

        /** The same, from the genus row's subfamily, genus, status and list source (subfamilyRows: no Taxon per row). */
        public boolean keep(String subfamily, String genus, String status, String listSource) {
            Integer ev = evidence.get(PlaceTaxonNotes.OtherNames.key(subfamily, genus));
            return keepGenus(choice, status, sourceKind(listSource, false), species(subfamily, genus), ev == null ? 0 : ev);
        }

        /** The genus rows the view shows (keep), in their order. */
        public ArrayList<Taxon> filter(List<Taxon> rows) {
            ArrayList<Taxon> out = new ArrayList<>();
            if (rows == null) return out;
            for (Taxon t : rows) if (t != null && keep(t)) out.add(t);
            return out;
        }
    }

    /**
     * The genus rows' numbers of a view in a place (one subfamily when given): the view's names (Species, and their images:
     * the Images cell), for Valid and Curated the names of All specimen names (the evidence of keepGenus), and the stored
     * Not curated numbers. No query per row; the place's specimens come from place_specimen_name (genusListSpecimens).
     */
    public static GenusRows loadGenusRows(Connection connection, Overview overview, String choice, String subfamily) throws SQLException {
        return loadGenusRows(connection, overview, choice, subfamily, genusListSpecimens(connection, overview, subfamily));
    }

    /**
     * The same; specimens: the place's names collected there (genusListSpecimens: stored, or read from the specimen table
     * when not counted yet), or null (regions, subregions, projects, or not counted on museums and bioregions).
     */
    public static GenusRows loadGenusRows(Connection connection, Overview overview, String choice, String subfamily,
            PlaceSpecimens specimens) throws SQLException {
        StatusSet viewSet = StatusSet.listStatusSet(choice);
        ViewNames view = loadViewNames(connection, overview, viewSet, subfamily, null, specimens);
        Map<String, Integer> evidence = null;
        if (StatusSet.LIST_VALID.equals(choice) || StatusSet.LIST_VETTED.equals(choice)) {
            evidence = loadViewNames(connection, overview, StatusSet.listStatusSet(StatusSet.LIST_ALL), subfamily, null, specimens)
                .countByGenus(BEYOND_GENUS_STATUSES);
        }
        GenusRows rows = new GenusRows(choice, view.countByGenus(null), evidence).withSpecimens(specimens);
        boolean hasColumn = org.calacademy.antweb.home.PlaceGenusCurationDb.typeAndKey(overview) != null;
        rows.withCuration(hasColumn, hasColumn ? org.calacademy.antweb.home.PlaceGenusCurationDb.read(connection, overview) : null);
        // Images: of the species of the genus in the view, those with an image of the ant anywhere in AntWeb (labels never
        // count; Brian Fisher, 2026-11-02, 18:40). One query per 500 names (StatisticsDb.antImagedNames).
        rows.withImaged(view.countByGenusOf(org.calacademy.antweb.home.StatisticsDb.antImagedNames(connection, view.taxonNames())));
        return rows;
    }

    /**
     * The numbers of a view's genus rows that decide which rows the view shows (Species in the view and the evidence of
     * keepGenus), without the Not curated and Images numbers (subfamilyRows: the rows are counted, not shown).
     */
    static GenusRows loadGenusRowsToKeep(Connection connection, Overview overview, String choice, PlaceSpecimens specimens) throws SQLException {
        StatusSet viewSet = StatusSet.listStatusSet(choice);
        ViewNames view = loadViewNames(connection, overview, viewSet, null, null, specimens);
        Map<String, Integer> evidence = null;
        if (StatusSet.LIST_VALID.equals(choice) || StatusSet.LIST_VETTED.equals(choice)) {
            evidence = loadViewNames(connection, overview, StatusSet.listStatusSet(StatusSet.LIST_ALL), null, null, specimens)
                .countByGenus(BEYOND_GENUS_STATUSES);
        }
        return new GenusRows(choice, view.countByGenus(null), evidence);
    }

    // ------------------------------------------------------------------------------------------------- subfamily rows

    /**
     * The Genera of a view's subfamily rows in a place (B77; Brian Fisher, 2026-11-03: "browse header counts the view's
     * rows"). Since 2026-11-02 the browse page of a subfamily in a place lists and counts the genus rows of the chosen view
     * (Subfamily.setChildren with the view's criteria, then GenusRows.filter); the subfamily's row on the place's list
     * showed the stored genus_count of the nightly count instead (production: Palearctic Ponerinae 14, its browse page in
     * the default view Curated 15, with the curated morphogenus Ponerine_genus2). Now the row shows the genus rows its
     * browse page shows in the same view, linked to that page in that view, as a genus row's Species (GenusRows).
     */
    public static final class SubfamilyRows {
        public final String choice;
        /** subfamily (lower case) -> the genus rows of the view under it. */
        final Map<String, Integer> genera;

        public SubfamilyRows(String choice, Map<String, Integer> genera) {
            this.choice = choice;
            this.genera = genera == null ? new HashMap<>() : genera;
        }

        /** The subfamily row's genera in the view (0 when none). */
        public int genera(String subfamily) {
            Integer n = subfamily == null ? null : genera.get(subfamily.toLowerCase(Locale.ROOT));
            return n == null ? 0 : n;
        }
    }

    /**
     * The query of the genus rows of a view in a place, as the subfamily's browse page selects them (Subfamily.setChildren:
     * the list's genus rows, the view's criteria, a genus value), every subfamily at once: subfamily, genus, status and the
     * list row's source (null on museum lists, which have none).
     */
    public static String subfamilyGenusRowsSql(Overview overview, String listTable, StatusSet viewSet) {
        String source = "museum_taxon".equals(listTable) ? "null" : listTable + ".source";
        return "select taxon.subfamily, taxon.genus, taxon.status, " + source + " source"
            + TaxaPage.listWhere(overview, Rank.GENUS, true, viewSet) + " and taxon.genus != ''";
    }

    /**
     * The Genera of the subfamily rows of a place's list in a view: two or three queries per page (the view's names, the
     * evidence for Valid and Curated, the genus rows), none per row. Null for an overview without the views or that is not
     * a place (the rows keep their stored numbers).
     */
    public static SubfamilyRows loadSubfamilyRows(Connection connection, Overview overview, String choice) throws SQLException {
        if (!hasViews(overview) || choice == null) return null;
        String[] tk = org.calacademy.antweb.home.GenusOnly.typeAndKey(overview);
        if (tk == null) return null;
        StatusSet viewSet = StatusSet.listStatusSet(choice);
        // which genus rows the browse page keeps (BrowseAction: GenusRows.filter, in Valid and Curated only)
        GenusRows keep = loadGenusRowsToKeep(connection, overview, choice, genusListSpecimens(connection, overview, null));
        Map<String, Integer> genera = new HashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(subfamilyGenusRowsSql(overview, PlaceTotals.listTable(tk[0]), viewSet));
             ResultSet r = stmt.executeQuery()) {
            java.util.Set<String> seen = new HashSet<>();   // a genus once (the browse page selects distinct genera)
            while (r.next()) {
                String sf = r.getString(1);
                String g = r.getString(2);
                if (sf == null || g == null || !seen.add(PlaceTaxonNotes.OtherNames.key(sf, g))) continue;
                if (keep.keep(sf, g, r.getString(3), r.getString(4))) genera.merge(sf.toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
        }
        return new SubfamilyRows(choice, genera);
    }

    /**
     * The specimens of one genus collected here under names not in the view (the genus page's line, rule of
     * 2026-10-30, kept 2026-11-02), or null: in Valid and Curated only (in All specimen names those names are on the list).
     */
    public static PlaceTaxonNotes.OtherNames.Genus genusOtherNames(Connection connection, Overview overview, String choice,
            String subfamily, String genus) throws SQLException {
        if (!(StatusSet.LIST_VALID.equals(choice) || StatusSet.LIST_VETTED.equals(choice))) return null;
        PlaceSpecimens specimens = loadPlaceSpecimens(connection, overview, subfamily, genus);
        if (specimens == null) return null;
        ViewNames view = loadViewNames(connection, overview, StatusSet.listStatusSet(choice), subfamily, genus, specimens);
        return otherNames(specimens, view).genus(subfamily, genus);
    }

    /** The names (subset of names) with a specimen collected here; empty when the place selects none. */
    public static Set<String> withPlaceSpecimens(PlaceSpecimens specimens, java.util.Collection<String> names) {
        Set<String> out = new HashSet<>();
        if (specimens == null || names == null) return out;
        for (String n : names) if (specimens.has(n)) out.add(n);
        return out;
    }
}
