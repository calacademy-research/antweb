package org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.calacademy.antweb.Museum;
import org.calacademy.antweb.Overview;
import org.calacademy.antweb.Project;
import org.calacademy.antweb.geolocale.Bioregion;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.util.Html;

/**
 * The specimens of a place identified to genus only, by genus (Brian Fisher, 2026-10-28; PlaceTotals.genusOnlySql): a
 * genus list row that has no species says "N specimens identified to genus only", and the species list, which has no
 * row for those names, says under the list "N specimens identified only to genus are not listed here: Lioponera (1),
 * Pheidole (2). Show all specimens." Read from the place's list rows (their specimen counts, the numbers the place's
 * Specimens adds up), one query per list page.
 */
public final class GenusOnly {

    /** The names of one genus identified to genus only, and their specimens. */
    public static final class Genus {
        public final String taxonName;     // the genus row's name (subfamily + genus)
        public final String subfamily;
        public final String genus;         // the genus part, as the taxon table has it
        public int specimens;
        public final List<String[]> names = new ArrayList<>();   // {taxon_name, species, subspecies, taxarank}

        Genus(String taxonName, String subfamily, String genus) {
            this.taxonName = taxonName;
            this.subfamily = subfamily;
            this.genus = genus;
        }

        /** "Lioponera" */
        public String displayName() {
            if (genus == null || genus.isEmpty()) return taxonName;
            return Character.toUpperCase(genus.charAt(0)) + genus.substring(1);
        }
    }

    private final Map<String, Genus> byGenus = new LinkedHashMap<>();   // genus taxon name (lower case) -> genus

    public GenusOnly() {}

    /** The place type and key of PlaceTotals for an overview, or null (not a place with a list). */
    public static String[] typeAndKey(Overview overview) {
        if (overview instanceof Geolocale) return new String[] {PlaceTotals.GEOLOCALE, String.valueOf(((Geolocale) overview).getId())};
        if (overview instanceof Project) return new String[] {PlaceTotals.PROJECT, ((Project) overview).getName()};
        if (overview instanceof Museum) return new String[] {PlaceTotals.MUSEUM, ((Museum) overview).getCode()};
        if (overview instanceof Bioregion) return new String[] {PlaceTotals.BIOREGION, ((Bioregion) overview).getName()};
        return null;
    }

    /** Reads the place's names identified to genus only; an empty result for an overview that is not a place. */
    public static GenusOnly load(Connection connection, Overview overview) throws SQLException {
        GenusOnly g = new GenusOnly();
        String[] tk = typeAndKey(overview);
        if (tk == null || connection == null) return g;
        try (PreparedStatement stmt = connection.prepareStatement(PlaceTotals.genusOnlySql(tk[0]))) {
            if (PlaceTotals.GEOLOCALE.equals(tk[0])) stmt.setInt(1, Integer.parseInt(tk[1]));
            else stmt.setString(1, tk[1]);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    g.add(r.getString("genus"), r.getString("subfamily"), r.getString("genus_part"), r.getString("taxon_name"),
                        r.getString("species"), r.getString("subspecies"), r.getString("taxarank"), r.getInt("specimen_count"));
                }
            }
        }
        return g;
    }

    /** Adds one list row (also for tests). */
    public void add(String genusTaxonName, String subfamily, String genus, String taxonName, String species, String subspecies,
                    String taxarank, int specimens) {
        if (genusTaxonName == null || specimens <= 0) return;
        Genus x = byGenus.computeIfAbsent(genusTaxonName.toLowerCase(java.util.Locale.ROOT), k -> new Genus(genusTaxonName, subfamily, genus));
        x.specimens += specimens;
        x.names.add(new String[] {taxonName, species, subspecies, taxarank});
    }

    public boolean isEmpty() { return byGenus.isEmpty(); }

    public java.util.Collection<Genus> genera() { return byGenus.values(); }

    /** The specimens identified to genus only of one genus row (its taxon name), 0 when none. */
    public int specimens(String genusTaxonName) {
        if (genusTaxonName == null) return 0;
        Genus x = byGenus.get(genusTaxonName.toLowerCase(java.util.Locale.ROOT));
        return x == null ? 0 : x.specimens;
    }

    public int totalSpecimens() {
        int n = 0;
        for (Genus x : byGenus.values()) n += x.specimens;
        return n;
    }

    /**
     * The search criteria of those specimens, to add to the place's advanced search (LocalityOverview.getSearchCriteria):
     * "genusSearchType=equals&genus=lioponera&speciesSearchType=equals&species=indet" (several names: "indet, sp1", a
     * search box value is a list of entries separated by commas, docs/numbers.md 5.3).
     */
    public static String searchParams(Genus x) {
        java.util.LinkedHashSet<String> species = new java.util.LinkedHashSet<>();
        for (String[] n : x.names) if (n[1] != null && !n[1].isEmpty()) species.add(n[1]);
        String p = "genusSearchType=equals&genus=" + Html.url(x.genus);
        if (!species.isEmpty()) p += "&speciesSearchType=equals&species=" + Html.url(String.join(", ", species));
        return p;
    }

    /** The genus of one genus row (its taxon name), or null. */
    public Genus genus(String genusTaxonName) {
        return genusTaxonName == null ? null : byGenus.get(genusTaxonName.toLowerCase(java.util.Locale.ROOT));
    }

    /** The words of a genus row: "N specimens identified to genus only" (1 specimen: "1 specimen ..."). */
    public static String genusRowText(int specimens) {
        return specimens + (specimens == 1 ? " specimen" : " specimens") + " identified to genus only";
    }

    /**
     * The line under a species list, without the link: "3 specimens identified only to genus are not listed here:
     * Lioponera (1), Pheidole (2)." (null when there are none).
     */
    public String speciesListText() {
        if (byGenus.isEmpty()) return null;
        StringBuilder b = new StringBuilder();
        int n = totalSpecimens();
        b.append(n).append(n == 1 ? " specimen identified only to genus is" : " specimens identified only to genus are").append(" not listed here: ");
        boolean first = true;
        for (Genus x : byGenus.values()) {
            if (!first) b.append(", ");
            b.append(x.displayName()).append(" (").append(x.specimens).append(")");
            first = false;
        }
        return b.append(".").toString();
    }
}
