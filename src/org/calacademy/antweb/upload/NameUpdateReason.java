package org.calacademy.antweb.upload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.calacademy.antweb.Status;
import org.calacademy.antweb.util.Html;

/**
 * Why the nightly name update moved a specimen (Brian Fisher, 2026-10-20): the taxonomic reason with
 * AntCat's reference, taken from AntCat's taxonomic history at the time of the move, and the AntCat id
 * of the taxon whose history gave it (https://www.antcat.org/catalog/ID). Stored in
 * specimen_name_update.reason and antcat_id: it is the specimen's taxonomic history.
 *
 *   synonym                  synonym: junior synonym of NEW (Author year)
 *   obsolete combination     new combination: NEW (Author year)
 *   unavailable misspelling  misspelling of NEW
 *   valid again              uploaded as UPLOADED; valid again (was junior synonym of OLD, Author year; status as species: Author year)
 *   anything else            AntCat status: STATUS (of the uploaded name)
 *
 * A reference that cannot be found in the history is left out rather than guessed. Names are the
 * display names (Crematogaster hespera); the page shows them in italics (toHtml). Pure: tested in
 * SpecimenNameUpdateTest.
 */
public final class NameUpdateReason {

    public static final int MAX_LENGTH = 500;
    public static final String ANTCAT_CATALOG = "https://www.antcat.org/catalog/";

    public final String text;
    /** AntCat id of the taxon whose history gave the reason, or null. */
    public final Integer antcatId;

    public NameUpdateReason(String text, Integer antcatId) {
        if (text != null && text.length() > MAX_LENGTH) text = text.substring(0, MAX_LENGTH);
        this.text = text;
        this.antcatId = antcatId == null || antcatId <= 0 ? null : antcatId;
    }

    public String toString() { return text + (antcatId != null ? " [AntCat " + antcatId + "]" : ""); }

    /** What the reason needs to know of one taxon. history may be null (none stored). */
    public static final class TaxonInfo {
        public final String name;       // display name, e.g. Crematogaster hespera
        public final String status;     // AntCat status, or null
        public final Integer antcatId;
        public final AntcatHistory history;
        public TaxonInfo(String name, String status, Integer antcatId, AntcatHistory history) {
            this.name = name;
            this.status = status;
            this.antcatId = antcatId;
            this.history = history == null ? AntcatHistory.parse(null) : history;
        }
    }

    /**
     * uploaded: the name in the curator's file; old: the name the specimen is filed under now; target:
     * the name it moves to. When target is the uploaded name, the specimen goes back to it (valid again).
     */
    public static NameUpdateReason of(TaxonInfo uploaded, TaxonInfo old, TaxonInfo target) {
        if (uploaded.name.equals(target.name)) {
            String syn = uploaded.history.juniorSynonymRef(old.name);
            String status = uploaded.history.statusAsSpeciesRef();
            StringBuilder b = new StringBuilder("uploaded as ").append(uploaded.name)
                .append("; valid again (was junior synonym of ").append(old.name);
            if (syn != null) b.append(", ").append(AntcatHistory.shortRef(syn));
            if (status != null) b.append("; status as species: ").append(AntcatHistory.shortRef(status));
            return new NameUpdateReason(b.append(")").toString(), uploaded.antcatId);
        }
        String status = uploaded.status;
        if (Status.SYNONYM.equals(status)) {
            String ref = uploaded.history.juniorSynonymRef(target.name);
            Integer id = uploaded.antcatId;
            if (ref == null && !old.name.equals(uploaded.name)) {
                ref = old.history.juniorSynonymRef(target.name);
                if (ref != null) id = old.antcatId;
            }
            return new NameUpdateReason("synonym: junior synonym of " + target.name + refPart(ref), id);
        }
        if (Status.OBSOLETE_COMBINATION.equals(status) || Status.ORIGINAL_COMBINATION.equals(status)) {
            String genus = target.name.split(" ", 2)[0];
            String ref = target.history.combinationRef(genus);
            return new NameUpdateReason("new combination: " + target.name + refPart(ref), ref != null ? target.antcatId : uploaded.antcatId);
        }
        if (Status.UNAVAILABLE_MISSPELLING.equals(status)) {
            return new NameUpdateReason("misspelling of " + target.name, uploaded.antcatId);
        }
        return new NameUpdateReason("AntCat status: " + (status == null ? "unknown" : status), uploaded.antcatId);
    }

    private static String refPart(String ref) {
        return ref == null ? "" : " (" + AntcatHistory.shortRef(ref) + ")";
    }

    // ------------------------------------------------------------ the page

    /** A reference as written by of(): after "(", ": " or ", ", up to ")" or ";" or the end. */
    private static final Pattern REF = Pattern.compile("(?<=\\(|: |, )[A-Z][^();:]*? \\d{4}[a-z]?(?=\\)|;|$)");

    /**
     * The reason as HTML: escaped, the given names in italics, each reference linked to the AntCat page
     * (when antcatId is known). Without a reference in the text, an "AntCat" link follows.
     */
    public static String toHtml(String text, Integer antcatId, String... names) {
        if (text == null) return "";
        List<int[]> spans = new ArrayList<>();   // start, end, 0 = name, 1 = reference
        if (antcatId != null && antcatId > 0) {
            Matcher m = REF.matcher(text);
            while (m.find()) spans.add(new int[] {m.start(), m.end(), 1});
        }
        boolean linked = !spans.isEmpty();
        List<String> sorted = new ArrayList<>();
        for (String n : names) if (n != null && !n.isEmpty() && !sorted.contains(n)) sorted.add(n);
        sorted.sort(Comparator.comparingInt(String::length).reversed());
        for (String n : sorted) {
            int from = 0;
            int i;
            while ((i = text.indexOf(n, from)) >= 0) {
                int end = i + n.length();
                boolean word = (i == 0 || !Character.isLetter(text.charAt(i - 1))) && (end == text.length() || !Character.isLetter(text.charAt(end)));
                if (word && !overlaps(spans, i, end)) spans.add(new int[] {i, end, 0});
                from = end;
            }
        }
        spans.sort(Comparator.comparingInt(s -> s[0]));
        StringBuilder b = new StringBuilder();
        int pos = 0;
        for (int[] s : spans) {
            b.append(Html.esc(text.substring(pos, s[0])));
            String part = Html.esc(text.substring(s[0], s[1]));
            if (s[2] == 0) b.append("<i>").append(part).append("</i>");
            else b.append("<a href=\"").append(ANTCAT_CATALOG).append(antcatId).append("\">").append(part).append("</a>");
            pos = s[1];
        }
        b.append(Html.esc(text.substring(pos)));
        if (!linked && antcatId != null && antcatId > 0) {
            b.append(" (<a href=\"").append(ANTCAT_CATALOG).append(antcatId).append("\">AntCat</a>)");
        }
        return b.toString();
    }

    private static boolean overlaps(List<int[]> spans, int start, int end) {
        for (int[] s : spans) if (start < s[1] && s[0] < end) return true;
        return false;
    }
}
