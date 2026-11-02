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
 *  - The genus rows (GenusRows): each row's Species in the view, its Other specimens (the specimens collected here under
 *    names not in the view, PlaceTaxonNotes.OtherNames), and which genus rows the views Valid and Curated show: a genus
 *    known in the place only from specimens identified to genus ("Acropyga (indet)": indetermined, unidentifiable)
 *    appears only in All specimen names (keepGenus).
 *  - The place's specimens by name (PlaceSpecimens), red-flagged ones never counted: the Other specimens, the specimen
 *    numbers of rows added from specimens, and the source icon (specimens trump literature: sourceKind).
 *
 * Tests without a database: test.org.calacademy.antweb.ListViewTest; SQL against MySQL: home.ListViewDbTest.
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
     * lists (CuratorTaxonSetDb.SYSTEM_PROJECTS). With status morphotaxon: a curated morphospecies.
     */
    public static String vettedCondition(String alias) {
        String sources = CuratorTaxonSetDb.curatorSourceList();
        StringBuilder sys = new StringBuilder("(");
        for (int i = 0; i < CuratorTaxonSetDb.SYSTEM_PROJECTS.size(); i++) {
            if (i > 0) sys.append(", ");
            sys.append("'").append(CuratorTaxonSetDb.SYSTEM_PROJECTS.get(i)).append("'");
        }
        sys.append(")");
        return "(exists (select 1 from geolocale_taxon vgt where vgt.taxon_name = " + alias + ".taxon_name and vgt.source in " + sources + ")"
            + " or exists (select 1 from project vp join proj_taxon vpt on vpt.project_name = vp.project_name"
            + " where vpt.taxon_name = " + alias + ".taxon_name and vpt.source in " + sources + " and vp.project_name not in " + sys + "))";
    }

    /** Whether the view adds the names of specimens collected in the place: country, adm1, region and subregion lists. */
    public static boolean addsSpecimenNames(Overview overview) {
        if (!(overview instanceof Country || overview instanceof Adm1 || overview instanceof Region || overview instanceof Subregion)) return false;
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

    public static ViewNames loadViewNames(Connection connection, Overview overview, StatusSet viewSet, String subfamily, String genus,
            PlaceSpecimens collected) throws SQLException {
        ViewNames out = new ViewNames();
        try (PreparedStatement stmt = connection.prepareStatement(viewNamesSql(overview, viewSet, subfamily, genus,
                collected == null ? null : collected.taxonNames()));
             ResultSet r = stmt.executeQuery()) {
            while (r.next()) out.add(r.getString("taxon_name"), r.getString("subfamily"), r.getString("genus"), r.getString("status"));
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------- rules

    /** The statuses of a species-level name that show a genus is known from more than specimens identified to genus. */
    public static final List<String> BEYOND_GENUS_STATUSES = java.util.Arrays.asList(Status.VALID, Status.MORPHOTAXON, Status.UNRECOGNIZED);

    /**
     * The specimens collected here under names not in the view, by genus (the Other specimens column, the line under a
     * genus page's species): the place's names of species or subspecies rank, of a counted status, identified to genus,
     * that are not names of the view.
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
     * Mozambique, known from morphospecies, is in Valid with no species and its Other specimens; a genus known only from
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
        /** The specimens collected here under names not in the view, by genus; null when the place selects none. */
        public final PlaceTaxonNotes.OtherNames otherNames;
        /** The genera (OtherNames.key) with a specimen collected here, not red-flagged (the specimen source icon). */
        final Set<String> generaWithSpecimens = new HashSet<>();

        public GenusRows(String choice, Map<String, Integer> speciesInView, Map<String, Integer> evidence, PlaceTaxonNotes.OtherNames otherNames) {
            this.choice = choice;
            this.speciesInView = speciesInView == null ? new HashMap<>() : speciesInView;
            this.evidence = evidence == null ? new HashMap<>() : evidence;
            this.otherNames = otherNames;
        }

        /** Marks the genera of the place's specimens (loadGenusRows; also for tests). */
        public GenusRows withSpecimens(PlaceSpecimens specimens) {
            if (specimens != null) for (String[] v : specimens.names.values()) generaWithSpecimens.add(PlaceTaxonNotes.OtherNames.key(v[0], v[1]));
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

        /** The genus row's Other specimens (0 when none; Fossil and All specimen names: never shown). */
        public PlaceTaxonNotes.OtherNames.Genus other(String subfamily, String genus) {
            if (otherNames == null || !showsOtherSpecimens()) return null;
            return otherNames.genus(subfamily, genus);
        }

        /** Valid and Curated (and All specimen names, which names them itself: none there in practice). Not Fossil. */
        public boolean showsOtherSpecimens() {
            return !StatusSet.LIST_FOSSIL.equals(choice);
        }

        public boolean keep(Taxon genusRow) {
            String sf = genusRow.getSubfamily();
            String g = genusRow.getGenus();
            Integer ev = evidence.get(PlaceTaxonNotes.OtherNames.key(sf, g));
            TaxonSet ts = genusRow.getTaxonSet();
            String kind = sourceKind(ts == null ? null : ts.getSource(), false);
            return keepGenus(choice, genusRow.getStatus(), kind, species(sf, g), ev == null ? 0 : ev);
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
     * The genus rows' numbers of a view in a place (one subfamily when given): the view's names (Species), for Valid and
     * Curated the names of All specimen names (the evidence of keepGenus), and the place's specimens (Other specimens).
     * Three queries, none per row.
     */
    public static GenusRows loadGenusRows(Connection connection, Overview overview, String choice, String subfamily) throws SQLException {
        return loadGenusRows(connection, overview, choice, subfamily, null);
    }

    /**
     * The same; placeSpecimens: the place's specimens by name already read for the page (all subfamilies), or null to read
     * them here. The specimen table is scanned once per page (2026-11-02, speed: three scans made country genus lists
     * take 6 to 11 s); the view queries take the names collected here from it (namedIn).
     */
    public static GenusRows loadGenusRows(Connection connection, Overview overview, String choice, String subfamily,
            PlaceSpecimens placeSpecimens) throws SQLException {
        PlaceSpecimens specimens = placeSpecimens != null ? placeSpecimens : loadPlaceSpecimens(connection, overview, subfamily, null);
        StatusSet viewSet = StatusSet.listStatusSet(choice);
        ViewNames view = loadViewNames(connection, overview, viewSet, subfamily, null, specimens);
        Map<String, Integer> evidence = null;
        if (StatusSet.LIST_VALID.equals(choice) || StatusSet.LIST_VETTED.equals(choice)) {
            evidence = loadViewNames(connection, overview, StatusSet.listStatusSet(StatusSet.LIST_ALL), subfamily, null, specimens)
                .countByGenus(BEYOND_GENUS_STATUSES);
        }
        return new GenusRows(choice, view.countByGenus(null), evidence, otherNames(specimens, view)).withSpecimens(specimens);
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
