package org.calacademy.antweb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Bioregion;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.geolocale.Region;
import org.calacademy.antweb.geolocale.Subregion;
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
