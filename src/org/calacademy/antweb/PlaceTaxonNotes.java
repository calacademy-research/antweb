package org.calacademy.antweb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Bioregion;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.geolocale.LocalityOverview;
import org.calacademy.antweb.geolocale.Region;
import org.calacademy.antweb.geolocale.Subregion;
import org.calacademy.antweb.home.GenusOnly;
import org.calacademy.antweb.home.PlaceTotals;
import org.calacademy.antweb.home.SpecimenDb;
import org.calacademy.antweb.home.StatisticsDb;
import org.calacademy.antweb.util.AntwebProps;
import org.calacademy.antweb.util.Html;

/**
 * Two notes on the taxon pages (description.do, browse.do, images.do) seen in a place (Brian Fisher, 2026-10-29).
 *
 * Indet line. A specimen identified only to genus is filed under "<Genus> (indet)" (docs/numbers.md, "One form for
 * identified only to genus"). That name is not a species: no genus page listed it, so its page could be reached from no
 * genus page. Every genus page now says under its species: "Not identified to species: Lividopone (indet), 45 specimens
 * (4 imaged)", linked to the indet name's page of the same kind and place. In a place the numbers are the place's
 * specimens (the specimens the indet page lists there: country, adm1, bioregion, museum by Overview.getOverviewCriteria;
 * a region or subregion by its countries, Geolocale.regionCountriesSql); in all of AntWeb, every specimen. Imaged: with an
 * image of the ant (labels do not count, StatisticsDb.ANT_IMAGE). Shown only when N > 0; curated projects select no
 * specimens and show no line. It is not a species anywhere and changes no number of the page.
 *
 * Not recorded. The site keeps the last place in the session, so a visitor lands on Lividopone "in Afrotropical" without
 * choosing it, and the page said "0 Species Imaged (0 total)". When a genus, species or subspecies page is opened for a
 * place (country, adm1, region, subregion, bioregion, museum or a project other than allantwebants and worldants) and
 * the taxon is not on the place's list (TaxonSet.exists(): the row read for every page anyway) nor has a specimen there
 * (country and adm1 lists take valid names only from specimens, so a morphospecies collected there is not on the list
 * but is recorded), the page says at the top: "Lividopone is not recorded from Afrotropical. Recorded from: Malagasy.
 * See Lividopone in all of AntWeb." The bioregions are those whose list has the name (one query of bioregion_taxon).
 *
 * Specimens collected here not on the species list (Brian Fisher, 2026-10-30: "if there is a specimen that justifies
 * the presence of a genus in a place, I should be able to click to see the specimen"). A genus can be on a place's genus
 * list without any species on its species list: Acropyga and Form_gen-afrc-za01 in Mozambique are there from
 * morphospecies and unrecognized specimens collected in Mozambique, which country lists do not take as species rows
 * (GeolocaleTaxonDb.GENUS_ROWS_FROM_SPECIMENS_SQL). The specimens "of other names" of a genus in a place are the
 * place's specimens (placeSpecimenCriteria; red-flagged ones left out, as the lists leave them out,
 * SpecimenDb.notRedFlagged) filed under a species or subspecies name of the genus, identified to genus, of a counted
 * status, that is not a species of the place's list (a list row whose status counts as a species: valid or morphotaxon,
 * PlaceTotals.speciesStatusCondition). The indet name ("Acropyga (indet)") is one such name. One grouped query per page
 * (otherNamesSql), keyed by genus:
 *   - genus lists (taxonomicPage.do?rank=genus, browse.do of a subfamily in a place): a genus with no species on the
 *     list says "No species on the list &middot; 3 specimens collected here" instead of "No Species"; a genus with
 *     species adds "+ 2 specimens of other names"; both link to the place's specimen search for the genus;
 *   - the genus page in a place (description.do, browse.do, images.do): "Specimens collected here not on the species
 *     list: 3 (names: Acropyga mz01, Acropyga mz02)", the number linked to the specimen search, each name to its page
 *     in the place. In all of AntWeb the indet line stays as it was (there is no place list).
 *
 * Tested without a database by test.org.calacademy.antweb.PlaceTaxonNotesTest, the SQL against MySQL by
 * test.org.calacademy.antweb.home.PlaceTaxonNotesDbTest.
 */
public final class PlaceTaxonNotes {

    private PlaceTaxonNotes() {
    }

    /** The species part of the one name of a genus's specimens identified only to genus (2026-10-28). */
    public static final String INDET = "(indet)";

    /** Request attributes read by web/common/genusIndetLine.jsp and web/common/notRecorded.jsp. */
    public static final String INDET_LINE = "genusIndetLine";
    public static final String NOT_RECORDED = "notRecorded";
    /** Request attribute (an OtherNames) read by web/taxonReport.jsp: the genus rows of a subfamily's browse page in a place. */
    public static final String OTHER_NAMES = "placeOtherNames";

    /** "dorylinaelividopone" -> "dorylinaelividopone (indet)". */
    public static String indetName(String genusTaxonName) {
        return genusTaxonName == null ? null : genusTaxonName + " " + INDET;
    }

    /** All of AntWeb: the indet line counts every specimen, the notice never shows. */
    public static boolean isAllOfAntWeb(Overview overview) {
        if (overview == null) return true;
        String name = overview.getName();
        return Project.ALLANTWEBANTS.equals(name) || Project.WORLDANTS.equals(name);
    }

    /**
     * SQL condition (starting with " and ", on the table named specimen) that selects the place's specimens: "" for all of
     * AntWeb; null for a place that selects no specimens (a curated project, the fossil list).
     */
    public static String placeSpecimenCriteria(Overview overview) {
        if (isAllOfAntWeb(overview)) return "";
        if (overview instanceof Region || overview instanceof Subregion) {
            // The countries of a region or subregion, as its own Specimens number counts them (2026-10-20).
            String rc = "(" + Geolocale.regionCountriesSql(" and r.id = " + ((Geolocale) overview).getId()) + ")";
            return " and (specimen.country in (select rc.cname from " + rc + " rc where rc.island = 0)"
                + " or specimen.island_country in (select rc.cname from " + rc + " rc where rc.island = 1))";
        }
        if (overview instanceof Country || overview instanceof Adm1 || overview instanceof Bioregion || overview instanceof Museum) {
            String c = overview.getOverviewCriteria();
            return c == null || c.isEmpty() ? null : c;
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------- indet line

    /** One parameter (the indet name): the specimens N and those with an image of the ant M. */
    public static String indetCountSql(String placeCriteria) {
        return "select count(*), coalesce(sum(case when exists (select 1 from image i where i.image_of_id = specimen.code and "
            + StatisticsDb.ANT_IMAGE + ") then 1 else 0 end), 0) from specimen where specimen.taxon_name = ?"
            + (placeCriteria == null ? "" : placeCriteria);
    }

    /** {N, M} of the genus's indet name in the place, or null when the place selects no specimens. */
    public static int[] countIndet(Connection connection, String genusTaxonName, Overview overview) throws SQLException {
        String criteria = placeSpecimenCriteria(overview);
        if (criteria == null || genusTaxonName == null) return null;
        try (PreparedStatement stmt = connection.prepareStatement(indetCountSql(criteria))) {
            stmt.setString(1, indetName(genusTaxonName));
            try (ResultSet r = stmt.executeQuery()) {
                if (!r.next()) return new int[] {0, 0};
                return new int[] {r.getInt(1), r.getInt(2)};
            }
        }
    }

    /** "45 specimens (4 imaged)", "1 specimen (0 imaged)". */
    public static String specimensText(int n, int m) {
        return n + (n == 1 ? " specimen" : " specimens") + " (" + m + " imaged)";
    }

    /** The line, or null when N is 0: "Not identified to species: <a>Lividopone (indet)</a>, 45 specimens (4 imaged)". */
    public static String indetLineHtml(String genusDisplayName, int n, int m, String url) {
        if (n <= 0) return null;
        return "<div id=\"genus_indet\" class=\"genus_indet\" style=\"margin:10px 0;\">Not identified to species: <a href=\""
            + Html.escAttr(url) + "\">" + Html.esc(genusDisplayName + " " + INDET) + "</a>, " + specimensText(n, m) + "</div>";
    }

    /** The page of the indet name: same page kind (description.do, browse.do, images.do), same place. */
    public static String indetUrl(String targetDo, String subfamily, String genus, Overview overview) {
        String params = "subfamily=" + Html.url(subfamily) + "&genus=" + Html.url(genus) + "&species=" + Html.url(INDET) + "&rank=species";
        return AntwebProps.getDomainApp() + "/" + targetDo + "?" + params + "&" + placeParams(overview);
    }

    // ------------------------------------------------------------------------------------------------- not recorded

    /** Whether the notice can apply: a genus, species or subspecies page in a place (not all of AntWeb). */
    public static boolean placeHasList(Overview overview, String rank) {
        if (overview == null || isAllOfAntWeb(overview)) return false;
        if (!Rank.GENUS.equals(rank) && !Rank.SPECIES.equals(rank) && !Rank.SUBSPECIES.equals(rank)) return false;
        return overview instanceof Geolocale || overview instanceof Bioregion || overview instanceof Museum || overview instanceof Project;
    }

    /**
     * One query (parameters: the name, then childrenPattern(name)): whether a specimen of the taxon is in the place. A
     * genus: its names ("Lividopone (indet)" included); a species: the name and its subspecies; a subspecies: the name.
     */
    public static String placeSpecimenSql(String placeCriteria) {
        return "select 1 from specimen where (specimen.taxon_name = ? or specimen.taxon_name like ?)" + placeCriteria + " limit 1";
    }

    /** name + " %" for LIKE, with LIKE's own characters escaped (morphogenera have "_"). */
    public static String childrenPattern(String taxonName) {
        return taxonName.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + " %";
    }

    /** The bioregions whose list has the name (parameter: the name). */
    public static final String BIOREGIONS_SQL = "select bioregion_name from bioregion_taxon where taxon_name = ? order by bioregion_name";

    /**
     * The notice's HTML, or null when the taxon is on the place's list or recorded there. onList: the page's TaxonSet
     * exists (read for every page anyway).
     */
    public static String notRecorded(Connection connection, Taxon taxon, Overview overview, boolean onList, String targetDo) throws SQLException {
        if (onList || taxon == null || !placeHasList(overview, taxon.getRank())) return null;
        String name = taxon.getTaxonName();
        if (name == null) return null;
        String criteria = placeSpecimenCriteria(overview);
        if (criteria != null && !criteria.isEmpty()) {
            try (PreparedStatement stmt = connection.prepareStatement(placeSpecimenSql(criteria))) {
                stmt.setString(1, name);
                stmt.setString(2, childrenPattern(name));
                try (ResultSet r = stmt.executeQuery()) {
                    if (r.next()) return null;   // collected there: recorded, though not on the list
                }
            }
        }
        List<String> bioregions = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(BIOREGIONS_SQL)) {
            stmt.setString(1, name);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) bioregions.add(r.getString(1));
            }
        }
        return notRecordedHtml(taxon.getPrettyName(), placeName(overview), bioregions, targetDo, taxonParams(taxon));
    }

    /** "Lividopone is not recorded from Afrotropical." + "Recorded from: Malagasy." + "See Lividopone in all of AntWeb." */
    public static String notRecordedHtml(String taxonDisplay, String placeDisplay, List<String> bioregions, String targetDo, String taxonParams) {
        String base = AntwebProps.getDomainApp() + "/" + targetDo + "?" + taxonParams;
        StringBuilder b = new StringBuilder();
        b.append("<div id=\"not_recorded\" class=\"not_recorded\" style=\"margin:10px 0;padding:8px 12px;border:1px solid #d4b106;background:#fffbe6;\">");
        b.append(Html.esc(taxonDisplay)).append(" is not recorded from ").append(Html.esc(placeDisplay)).append(".");
        if (bioregions != null && !bioregions.isEmpty()) {
            b.append("<br>Recorded from: ");
            for (int i = 0; i < bioregions.size(); i++) {
                if (i > 0) b.append(", ");
                String bio = bioregions.get(i);
                b.append("<a href=\"").append(Html.escAttr(base + "&bioregionName=" + Html.url(bio))).append("\">").append(Html.esc(bio)).append("</a>");
            }
            b.append(".");
        }
        b.append("<br><a href=\"").append(Html.escAttr(base + "&project=" + Project.ALLANTWEBANTS)).append("\">See ")
            .append(Html.esc(taxonDisplay)).append(" in all of AntWeb</a>");
        return b.append("</div>").toString();
    }

    // ------------------------------------------------------------------------------------------------- other names

    /** The specimens collected in a place under names not on its species list, by genus (one query, otherNamesSql). */
    public static final class OtherNames {

        /** One genus: its specimens of other names, and the names ({taxon_name, species, subspecies, taxarank, count}). */
        public static final class Genus {
            public final String subfamily;
            public final String genus;
            public int specimens;
            public final List<String[]> names = new ArrayList<>();

            Genus(String subfamily, String genus) {
                this.subfamily = subfamily;
                this.genus = genus;
            }

            /** "Acropyga", "Form_gen-afrc-za01" */
            public String displayName() {
                return capitalized(genus);
            }
        }

        private final Map<String, Genus> byGenus = new LinkedHashMap<>();

        static String key(String subfamily, String genus) {
            return (subfamily == null ? "" : subfamily.toLowerCase(Locale.ROOT)) + "|" + (genus == null ? "" : genus.toLowerCase(Locale.ROOT));
        }

        /** Adds one name's specimens (also for tests). */
        public void add(String subfamily, String genus, String taxonName, String species, String subspecies, String taxarank, int count) {
            if (genus == null || count <= 0) return;
            Genus g = byGenus.computeIfAbsent(key(subfamily, genus), k -> new Genus(subfamily, genus));
            g.specimens += count;
            g.names.add(new String[] {taxonName, species, subspecies, taxarank, String.valueOf(count)});
        }

        /** The genus row's specimens of other names (by the row's subfamily and genus), or null when none. */
        public Genus genus(String subfamily, String genus) {
            return byGenus.get(key(subfamily, genus));
        }

        public boolean isEmpty() { return byGenus.isEmpty(); }

        public java.util.Collection<Genus> genera() { return byGenus.values(); }
    }

    /**
     * The query (Brian Fisher, 2026-10-30): the place's specimens grouped by name first (so the list is looked up once per
     * name), then the names of a counted status, identified to genus, that are not a species of the place's list.
     * Parameters, in this order: the subfamily (bySubfamily), the genus (byGenus), the subfamily again (bySubfamily), the
     * list key (geolocale id, project name, museum code, bioregion name).
     */
    public static String otherNamesSql(String listType, String placeCriteria, boolean bySubfamily, boolean byGenus) {
        return "select t.taxon_name, t.subfamily, t.genus, t.species, t.subspecies, t.taxarank, x.n"
            + " from (select specimen.taxon_name, count(*) n from specimen where" + SpecimenDb.getAntSpecimenCriteria("specimen")
            + " and " + SpecimenDb.notRedFlagged("specimen")
            + (bySubfamily ? " and specimen.subfamily = ?" : "") + (byGenus ? " and specimen.genus = ?" : "")
            + (placeCriteria == null ? "" : placeCriteria)
            + " group by specimen.taxon_name) x"
            + " join taxon t on t.taxon_name = x.taxon_name"
            + " where t.taxarank in ('species', 'subspecies') and t.family = 'formicidae' and t.status in " + PlaceTotals.COUNTABLE
            + " and " + PlaceTotals.identifiedToGenus("t")
            + (bySubfamily ? " and t.subfamily = ?" : "")
            + " and not (" + PlaceTotals.speciesStatusCondition("t") + " and exists (select 1 from " + PlaceTotals.listTable(listType)
            + " l where l." + PlaceTotals.listKey(listType) + " = ? and l.taxon_name = t.taxon_name))"
            + " order by t.subfamily, t.genus, t.taxon_name";
    }

    /**
     * The place's specimens of other names, by genus; only a subfamily's or one genus's when given. Null for all of AntWeb
     * and for places that select no specimens (curated projects): there the rows keep "No Species".
     */
    public static OtherNames loadOtherNames(Connection connection, Overview overview, String subfamily, String genus) throws SQLException {
        String criteria = placeSpecimenCriteria(overview);
        String[] tk = GenusOnly.typeAndKey(overview);
        if (connection == null || criteria == null || criteria.isEmpty() || tk == null) return null;
        boolean bySubfamily = subfamily != null && !subfamily.isEmpty();
        boolean byGenus = genus != null && !genus.isEmpty();
        OtherNames out = new OtherNames();
        try (PreparedStatement stmt = connection.prepareStatement(otherNamesSql(tk[0], criteria, bySubfamily, byGenus))) {
            int i = 0;
            if (bySubfamily) stmt.setString(++i, subfamily);
            if (byGenus) stmt.setString(++i, genus);
            if (bySubfamily) stmt.setString(++i, subfamily);
            if (PlaceTotals.GEOLOCALE.equals(tk[0])) stmt.setInt(++i, Integer.parseInt(tk[1]));
            else stmt.setString(++i, tk[1]);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    out.add(r.getString("subfamily"), r.getString("genus"), r.getString("taxon_name"), r.getString("species"),
                        r.getString("subspecies"), r.getString("taxarank"), r.getInt("n"));
                }
            }
        }
        return out;
    }

    /** The genus row with no species on the list: "No species on the list". */
    public static final String NO_SPECIES_ON_LIST = "No species on the list";

    /** "3 specimens collected here", "1 specimen collected here" */
    public static String collectedHereText(int n) {
        return n + (n == 1 ? " specimen" : " specimens") + " collected here";
    }

    /** "+ 2 specimens of other names", "+ 1 specimen of other names" */
    public static String otherNamesText(int n) {
        return "+ " + n + (n == 1 ? " specimen" : " specimens") + " of other names";
    }

    /** A link (rel=nofollow: a search) or, without a URL (bots), the text alone. */
    static String link(String text, String url, String cls, int n) {
        String data = " class=\"" + cls + "\" data-other-specimens=\"" + n + "\"";
        if (url == null) return "<span" + data + ">" + Html.esc(text) + "</span>";
        return "<a rel=\"nofollow\"" + data + " href=\"" + Html.escAttr(url) + "\">" + Html.esc(text) + "</a>";
    }

    /** The genus row with no species on the list: "No species on the list &middot; <a>3 specimens collected here</a>". */
    public static String genusRowNoSpeciesHtml(int n, String url) {
        return NO_SPECIES_ON_LIST + " &middot; " + link(collectedHereText(n), url, "other-names", n);
    }

    /** Under a genus row's "N Species": "<a>+ 2 specimens of other names</a>" (small). */
    public static String genusRowOtherHtml(int n, String url) {
        return "<br><small>" + link(otherNamesText(n), url, "other-names", n) + "</small>";
    }

    /** More names than this: the search takes the genus alone (a search box value is a list of names, docs/numbers.md 5.3). */
    public static final int MAX_SEARCH_NAMES = 30;

    /**
     * The search criteria of a genus's specimens of other names, to add to the place's specimen search: the genus, and
     * unless allNames, the species parts of the names ("genusSearchType=equals&genus=acropyga&speciesSearchType=equals&
     * species=mz01, mz02"). allNames (no species on the list): every specimen of the genus collected there.
     */
    public static String searchParams(OtherNames.Genus g, boolean allNames) {
        String p = "genusSearchType=equals&genus=" + Html.url(g.genus);
        if (allNames) return p;
        java.util.LinkedHashSet<String> species = new java.util.LinkedHashSet<>();
        for (String[] n : g.names) if (n[1] != null && !n[1].isEmpty()) species.add(n[1]);
        if (species.isEmpty() || species.size() > MAX_SEARCH_NAMES) return p;
        return p + "&speciesSearchType=equals&species=" + Html.url(String.join(", ", species));
    }

    /** The place's specimen search ("List all specimens collected in <place>"), or null (no place search). */
    public static String placeSearchUrl(Overview overview) {
        if (!(overview instanceof LocalityOverview) || isAllOfAntWeb(overview)) return null;
        String c = ((LocalityOverview) overview).getSearchCriteria();
        if (c == null || c.isEmpty()) return null;
        return AntwebProps.getDomainApp() + "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=formicidae&" + c
            + "&resultRank=specimen";
    }

    /** "Acropyga mz01", "Acropyga (indet)", "Pheidole a x" */
    public static String nameDisplay(OtherNames.Genus g, String[] name) {
        StringBuilder b = new StringBuilder(g.displayName());
        if (name[1] != null && !name[1].isEmpty()) b.append(" ").append(name[1]);
        if (name[2] != null && !name[2].isEmpty()) b.append(" ").append(name[2]);
        return b.toString();
    }

    /** The page of one name in the place: same page kind (description.do, browse.do, images.do). */
    public static String nameUrl(String targetDo, OtherNames.Genus g, String[] name, Overview overview) {
        String rank = Rank.SUBSPECIES.equals(name[3]) ? Rank.SUBSPECIES : Rank.SPECIES;
        String p = "subfamily=" + Html.url(g.subfamily) + "&genus=" + Html.url(g.genus) + "&species=" + Html.url(name[1]);
        if (Rank.SUBSPECIES.equals(rank)) p += "&subspecies=" + Html.url(name[2]);
        return AntwebProps.getDomainApp() + "/" + targetDo + "?" + p + "&rank=" + rank + "&" + placeParams(overview);
    }

    /** Names shown on the genus page's line; more: "and N more". */
    public static final int MAX_LINE_NAMES = 12;

    /**
     * The genus page's line in a place, or null when none: "Specimens collected here not on the species list: <a>3</a>
     * (names: <a>Acropyga mz01</a>, <a>Acropyga mz02</a>)". searchUrl: the specimen search of those specimens (null:
     * the number is not linked); targetDo: the page kind the names link to.
     */
    public static String otherNamesLineHtml(OtherNames.Genus g, String searchUrl, String targetDo, Overview overview) {
        if (g == null || g.specimens <= 0) return null;
        StringBuilder b = new StringBuilder();
        b.append("<div id=\"genus_other_names\" class=\"genus_indet\" data-other-specimens=\"").append(g.specimens)
            .append("\" style=\"margin:10px 0;\">Specimens collected here not on the species list: ");
        if (searchUrl == null) b.append(g.specimens);
        else b.append("<a rel=\"nofollow\" href=\"").append(Html.escAttr(searchUrl)).append("\">").append(g.specimens).append("</a>");
        b.append(" (names: ");
        int shown = 0;
        for (String[] name : g.names) {
            if (shown == MAX_LINE_NAMES) break;
            if (shown > 0) b.append(", ");
            b.append("<a href=\"").append(Html.escAttr(nameUrl(targetDo, g, name, overview))).append("\">")
                .append(Html.esc(nameDisplay(g, name))).append("</a>");
            shown++;
        }
        if (g.names.size() > shown) b.append(" and ").append(g.names.size() - shown).append(" more");
        return b.append(")</div>").toString();
    }

    // ------------------------------------------------------------------------------------------------- names from

    /** The heading of a list with the specimens' names (specimen=true): "54 Valid species on the list or collected in Mozambique". */
    public static String withSpecimensHeading(String heading, String placeDisplay) {
        if (heading == null) return null;
        // the "(of N species and morphospecies)" of Valid species is the place page's number, not this list's
        String h = heading.replaceFirst(" \\(of [\\d,]+ species and morphospecies\\)$", "");
        return h + " on the list or collected in " + placeDisplay;
    }

    /** The two choices of the "Names from" selector: the place's list (specimen=false), or with the specimens' names too (specimen=true). */
    public static final String NAMES_FROM_LIST = "Place list";
    public static final String NAMES_FROM_SPECIMENS = "Specimens collected here";

    /** The label of the current choice. */
    public static String namesFromLabel(boolean withSpecimens) {
        return withSpecimens ? NAMES_FROM_SPECIMENS : NAMES_FROM_LIST;
    }

    /** "acropyga" -> "Acropyga" */
    static String capitalized(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------------------------------------- helpers

    /** The taxon's own parameters, encoded: "subfamily=...&genus=...&species=...&rank=species". */
    public static String taxonParams(Taxon taxon) {
        String rank = taxon.getRank();
        String p = "subfamily=" + Html.url(taxon.getSubfamily()) + "&genus=" + Html.url(taxon.getGenus());
        if (Rank.SPECIES.equals(rank) || Rank.SUBSPECIES.equals(rank)) p += "&species=" + Html.url(taxon.getSpecies());
        if (Rank.SUBSPECIES.equals(rank)) p += "&subspecies=" + Html.url(taxon.getSubspecies());
        return p + "&rank=" + Html.url(rank);
    }

    /** The place's parameters (Overview.getParams) with each value URL-encoded; project=allantwebants for all of AntWeb. */
    public static String placeParams(Overview overview) {
        if (isAllOfAntWeb(overview)) return "project=" + Project.ALLANTWEBANTS;
        StringBuilder b = new StringBuilder();
        for (String pair : overview.getParams().split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            if (b.length() > 0) b.append("&");
            if (eq < 0) b.append(Html.url(pair));
            else b.append(pair, 0, eq + 1).append(Html.url(pair.substring(eq + 1)));
        }
        return b.toString();
    }

    /** The place as the page names it: "Mozambique", "Toliara, Madagascar", "Afrotropical", a museum's or project's name. */
    public static String placeName(Overview overview) {
        if (overview instanceof Adm1) return overview.getTitle();
        String name = null;
        if (overview instanceof Bioregion) name = ((Bioregion) overview).getDisplayName();
        if (overview instanceof Museum) name = ((Museum) overview).getDisplayName();
        if (overview instanceof Project) name = ((Project) overview).getDisplayName();
        if (name == null) name = overview.getTitle();
        if (name == null) name = overview.getName();
        return name;
    }
}
