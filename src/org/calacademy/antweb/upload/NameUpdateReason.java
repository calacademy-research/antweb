package org.calacademy.antweb.upload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
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
 * The reason compares the old name (the one the specimen was filed under) with the new name, whatever
 * the chain of AntCat acts between them (spec: claude/antweb-name-update-reasons-spec.md):
 *
 *   epithet changed (beyond a gender ending)   synonym: junior synonym of NEW (Author year)
 *   genus changed, epithet same or gender      new combination: NEW (Author year)
 *                                              [; ending agrees with the gender of GENUS]
 *   subspecies to species                      raised to species: NEW (Author year)
 *   species to subspecies                      placed as subspecies: NEW (Author year)
 *   A b b -> A b                               nominotypical subspecies, shown as the species
 *   same genus, only the gender ending         ending agrees with the gender of GENUS (ICZN Art. 34.2)
 *   other spelling of the same epithet         misspelling of NEW
 *   the uploaded name is valid again           Your name NEW is valid again. It had been shown under
 *                                              OLD (synonymy by Author year), and was revived as a
 *                                              species by Author year.
 *
 * The same old -> new pair always gets the same reason, whatever name the curator uploaded (2026-10-20:
 * Colobopsis pylartes -> C. impressa read "synonym" for a group that uploaded C. pylartes and "new
 * combination" for one that uploaded Camponotus pylartes, because the reason followed the uploaded
 * name's status and history). The uploaded name counts only for "valid again" (the new name is it).
 *
 * Gender ending or misspelling: an ending change is gender agreement when the new ending is the form
 * of the new genus's grammatical gender (Opisthopsis pictus -> picta, Opisthopsis is feminine);
 * otherwise it is a misspelling (Camponotus ligniperdus -> ligniperda: Camponotus is masculine, so -a
 * is not agreement; ligniperda is a noun, invariable). The gender of a genus is read from its ending
 * (genderOf). A reference that cannot be found in the history is left out rather than guessed. Names are
 * display names (Crematogaster hespera); the page shows them in italics (toHtml). Pure: tested in
 * AntcatHistoryTest.
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

    public boolean equals(Object o) {
        return o instanceof NameUpdateReason r && java.util.Objects.equals(text, r.text) && java.util.Objects.equals(antcatId, r.antcatId);
    }

    public int hashCode() { return java.util.Objects.hash(text, antcatId); }

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
     * Otherwise the uploaded name plays no part.
     */
    public static NameUpdateReason of(TaxonInfo uploaded, TaxonInfo old, TaxonInfo target) {
        if (uploaded != null && uploaded.name.equals(target.name) && !old.name.equals(target.name)) return validAgain(old, target);
        return of(old, target);
    }

    /** Valid again: the curator's name, shown under old while it was old's junior synonym, is valid again. */
    public static NameUpdateReason validAgain(TaxonInfo old, TaxonInfo target) {
        String[] o = parts(old.name);
        Predicate<String> isOld = s -> sameTaxon(parts(s), o, false);
        String syn = target.history.juniorSynonymRef(isOld);
        String revived = target.history.revivalRef(isOld);
        StringBuilder b = new StringBuilder("Your name ").append(target.name).append(" is valid again. It had been shown under ")
            .append(abbreviate(old.name, target.name));
        if (syn != null) b.append(" (synonymy by ").append(AntcatHistory.shortRef(syn)).append(")");
        b.append(", and was revived as a species");
        if (revived != null) b.append(" by ").append(AntcatHistory.shortRef(revived));
        return new NameUpdateReason(b.append(".").toString(), target.antcatId);
    }

    /** Why a specimen filed under old now goes under target (not back to its uploaded name). */
    public static NameUpdateReason of(TaxonInfo old, TaxonInfo target) {
        String[] o = parts(old.name);
        String[] n = parts(target.name);
        Integer fallbackId = old.antcatId != null ? old.antcatId : target.antcatId;
        if (o.length < 2 || n.length < 2 || o.length > 3 || n.length > 3) return statusReason(old, fallbackId);

        // A b b -> A b: the nominotypical subspecies is the species. Otherwise go on with A b.
        String nominotypical = null;
        if (o.length == 3 && o[2].equals(o[1])) {
            o = new String[] {o[0], o[1]};
            if (Arrays.equals(o, n)) return new NameUpdateReason("nominotypical subspecies, shown as the species", target.antcatId);
            nominotypical = o[0] + " " + o[1];
        }
        boolean genusChanged = !o[0].equals(n[0]);

        if (o.length == 3 && n.length == 2) {        // subspecies -> species
            if (relation(o[2], n[1]) != Relation.OTHER) {
                return cited("raised to species: " + target.name, target.history.raisedRef(), target, old.history.raisedRef(), old, fallbackId);
            }
            return synonym(old, target, o, n, nominotypical, fallbackId);
        }
        if (o.length == 2 && n.length == 3) {        // species -> subspecies
            if (relation(o[1], n[2]) != Relation.OTHER) {
                String[] species = {n[0], n[1]};
                Predicate<String> ofSpecies = s -> sameTaxon(parts(s), species, false);
                return cited("placed as subspecies: " + target.name, target.history.subspeciesRef(ofSpecies), target,
                    old.history.subspeciesRef(ofSpecies), old, fallbackId);
            }
            return synonym(old, target, o, n, nominotypical, fallbackId);
        }
        // Same rank: compare the last epithet (and, for subspecies, the species).
        Relation r = relation(o[o.length - 1], n[n.length - 1]);
        if (o.length == 3 && relation(o[1], n[1]) == Relation.OTHER && r != Relation.OTHER) {
            String[] species = {n[0], n[1]};
            Predicate<String> ofSpecies = s -> sameTaxon(parts(s), species, false);
            return cited("placed as subspecies: " + target.name, target.history.subspeciesRef(ofSpecies), target,
                old.history.subspeciesRef(ofSpecies), old, fallbackId);
        }
        String oldEnd = o[o.length - 1];
        String newEnd = n[n.length - 1];
        switch (r) {
            case SAME:
            case GENDER:
                if (genusChanged) {
                    String genus = n[0];
                    String note = "";
                    Gender g = genderOf(genus);
                    boolean agrees = g != null && endingAgrees(newEnd, g);
                    Gender og = genderOf(o[0]);
                    if (agrees && (r == Relation.GENDER || (og != null && !endingAgrees(oldEnd, og) && hasGenderEnding(oldEnd)))) {
                        note = "; ending agrees with the gender of " + genus;
                    }
                    String ref = target.history.combinationRef(genus);
                    if (ref != null) return new NameUpdateReason("new combination: " + target.name + refPart(ref) + note, target.antcatId);
                    ref = old.history.combinationRef(genus);
                    return new NameUpdateReason("new combination: " + target.name + refPart(ref) + note, ref != null ? old.antcatId : fallbackId);
                }
                if (r == Relation.SAME) return statusReason(old, fallbackId);   // only the subgenus differs
                Gender g = genderOf(n[0]);
                if (g == null || endingAgrees(newEnd, g)) {
                    return new NameUpdateReason("ending agrees with the gender of " + n[0], target.antcatId != null ? target.antcatId : fallbackId);
                }
                return new NameUpdateReason("misspelling of " + target.name, fallbackId);
            case SPELLING:
                if (genusChanged) {
                    String ref = target.history.combinationRef(n[0]);
                    return new NameUpdateReason("new combination: " + target.name + refPart(ref), ref != null ? target.antcatId : fallbackId);
                }
                return new NameUpdateReason("misspelling of " + target.name, fallbackId);
            default:
                if (Status.UNAVAILABLE_MISSPELLING.equals(old.status) && !genusChanged && o.length == n.length
                        && (o.length == 2 || o[1].equals(n[1])) && levenshtein(normal(oldEnd), normal(newEnd)) <= 2) {
                    return new NameUpdateReason("misspelling of " + target.name, fallbackId);
                }
                return synonym(old, target, o, n, nominotypical, fallbackId);
        }
    }

    /**
     * Synonym: the synonymy in the junior's (old) history, the first with the current senior after the
     * last revival; if the old name has none (an obsolete combination or misspelling often has no
     * history), the "Senior synonym of" entry for it in the new name's history.
     */
    private static NameUpdateReason synonym(TaxonInfo old, TaxonInfo target, String[] o, String[] n, String nominotypical, Integer fallbackId) {
        String text = (nominotypical != null ? "synonym: " + nominotypical + " is a junior synonym of " : "synonym: junior synonym of ") + target.name;
        String ref = old.history.juniorSynonymRef(s -> sameTaxon(parts(s), n, false));
        if (ref != null) return new NameUpdateReason(text + refPart(ref), old.antcatId);
        ref = target.history.seniorSynonymRef(s -> sameTaxon(parts(s), o, false));
        if (ref == null) ref = target.history.seniorSynonymRef(s -> sameTaxon(parts(s), o, true));
        if (ref == null) ref = target.history.seniorSynonymRef(s -> sameLastEpithet(parts(s), o));
        if (ref != null) return new NameUpdateReason(text + refPart(ref), target.antcatId);
        return new NameUpdateReason(text, fallbackId);
    }

    private static NameUpdateReason cited(String text, String ref, TaxonInfo from, String ref2, TaxonInfo from2, Integer fallbackId) {
        if (ref != null) return new NameUpdateReason(text + refPart(ref), from.antcatId);
        if (ref2 != null) return new NameUpdateReason(text + refPart(ref2), from2.antcatId);
        return new NameUpdateReason(text, fallbackId);
    }

    private static NameUpdateReason statusReason(TaxonInfo old, Integer id) {
        return new NameUpdateReason("AntCat status: " + (old.status == null ? "unknown" : old.status), id);
    }

    // ------------------------------------------------------------ names and endings

    /**
     * "Camponotus (Colobopsis) pylartes" -> {Camponotus, pylartes}: the genus and the epithets, without
     * subgenus or the author and year a history may add ("Formica picea Nylander, 1846").
     */
    public static String[] parts(String name) {
        if (name == null) return new String[0];
        String s = name.replace("\u2020", "").replaceAll("\\([^)]*\\)", " ").trim();
        if (s.isEmpty()) return new String[0];
        String[] p = s.split("\\s+");
        int n = 1;
        while (n < p.length && p[n].matches("[a-z][a-z-]*")) n++;
        return Arrays.copyOf(p, n);
    }

    /**
     * The same taxon in any genus: same number of epithets, each the same or another gender ending (with
     * loose, also another spelling). The genus is not compared: a history names the senior in the
     * combination of its time ("Junior synonym of Camponotus (Colobopsis) impressa").
     */
    static boolean sameTaxon(String[] a, String[] b, boolean loose) {
        if (a.length != b.length || a.length < 2) return false;
        for (int i = 1; i < a.length; i++) {
            Relation r = relation(a[i], b[i]);
            if (r == Relation.OTHER || (!loose && r == Relation.SPELLING)) return false;
        }
        return true;
    }

    /**
     * The last epithet the same (any spelling), at either rank: the senior's history may name the junior
     * at the rank it was synonymized at ("Senior synonym of Pheidole pilifera coloradensis" for
     * Pheidole coloradensis; "Senior synonym of Plagiolepis maura" for Plagiolepis pallescens maura).
     */
    static boolean sameLastEpithet(String[] a, String[] b) {
        return a.length >= 2 && b.length >= 2 && relation(a[a.length - 1], b[b.length - 1]) != Relation.OTHER;
    }

    public enum Relation { SAME, GENDER, SPELLING, OTHER }
    public enum Gender { MASCULINE, FEMININE, NEUTER }

    /** Adjective endings by declension: masculine, feminine, neuter. */
    private static final String[][] ENDINGS = {
        {"us", "a", "um"}, {"er", "ra", "rum"}, {"er", "era", "erum"}, {"is", "is", "e"}, {"er", "ris", "re"}, {"ior", "ior", "ius"}
    };

    /**
     * How two epithets relate: the same; the same stem with another gender ending (nomada, nomadus;
     * niger, nigra); another spelling (noveboracensis, novaeboracensis; haenschei, haenschi: the same
     * after ae/oe -> e, final ei/ii -> i, y -> i, j -> i, double letters made single); or another name.
     */
    public static Relation relation(String a, String b) {
        a = a.toLowerCase(Locale.ROOT);
        b = b.toLowerCase(Locale.ROOT);
        if (a.equals(b)) return Relation.SAME;
        for (String[] decl : ENDINGS) {
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (decl[i].equals(decl[j])) continue;
                    if (a.endsWith(decl[i]) && b.endsWith(decl[j])) {
                        String sa = a.substring(0, a.length() - decl[i].length());
                        String sb = b.substring(0, b.length() - decl[j].length());
                        if (sa.length() >= 2 && sa.equals(sb)) return Relation.GENDER;
                    }
                }
            }
        }
        if (normal(a).equals(normal(b))) return Relation.SPELLING;
        return Relation.OTHER;
    }

    static String normal(String s) {
        s = s.toLowerCase(Locale.ROOT).replace("ae", "e").replace("oe", "e").replace('y', 'i').replace('j', 'i');
        s = s.replaceAll("(ei|ii)$", "i");
        return s.replaceAll("(.)\\1+", "$1");
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    /**
     * The grammatical gender of a genus name, from its ending (ICZN Art. 30 and the usual Greek and
     * Latin endings of ant genera), or null when the ending does not tell: -myrmex, -ops, -oides, -ides,
     * -ites, -otes, -us, -er, -or masculine; -myrma, -opsis, -pone, -ponera, -is, -ys, -a, -e feminine;
     * -ma (Greek -ma, Stigmatomma), -um, -on neuter.
     */
    public static Gender genderOf(String genus) {
        if (genus == null) return null;
        String g = genus.toLowerCase(Locale.ROOT);
        if (g.endsWith("myrmex") || g.endsWith("ops") || g.endsWith("oides") || g.endsWith("ides") || g.endsWith("ites")
            || g.endsWith("otes") || g.endsWith("us") || g.endsWith("er") || g.endsWith("or")) return Gender.MASCULINE;
        if (g.endsWith("myrma") || g.endsWith("opsis") || g.endsWith("pone") || g.endsWith("ponera")) return Gender.FEMININE;
        if (g.endsWith("ma") || g.endsWith("um") || g.endsWith("on")) return Gender.NEUTER;
        if (g.endsWith("is") || g.endsWith("ys") || g.endsWith("a") || g.endsWith("e")) return Gender.FEMININE;
        return null;
    }

    /** Whether an epithet's ending is a form of that gender (-us, -er, -is masculine; -a, -ra, -is feminine; -um, -rum, -e neuter). */
    static boolean endingAgrees(String epithet, Gender g) {
        int col = g == Gender.MASCULINE ? 0 : g == Gender.FEMININE ? 1 : 2;
        String best = null;
        for (String[] decl : ENDINGS) {
            for (String e : decl) if (epithet.endsWith(e) && (best == null || e.length() > best.length())) best = e;
        }
        if (best == null) return false;
        for (String[] decl : ENDINGS) {
            if (epithet.endsWith(decl[col]) && decl[col].length() >= best.length()) return true;
        }
        return false;
    }

    static boolean hasGenderEnding(String epithet) {
        for (String[] decl : ENDINGS) for (String e : decl) if (epithet.endsWith(e)) return true;
        return false;
    }

    /** "Crematogaster cerasi" with "Crematogaster browni" -> "C. cerasi"; another genus stays in full. */
    static String abbreviate(String name, String other) {
        String[] a = name.split(" ", 2);
        String[] b = other.split(" ", 2);
        if (a.length == 2 && b.length == 2 && a[0].equals(b[0]) && !a[0].isEmpty()) return a[0].charAt(0) + ". " + a[1];
        return name;
    }

    private static String refPart(String ref) {
        return ref == null ? "" : " (" + AntcatHistory.shortRef(ref) + ")";
    }

    // ------------------------------------------------------------ the page

    /** A reference as written by of(): after "(", ": ", ", " or "by ", up to ")", ";", the final "." or the end. */
    private static final Pattern REF = Pattern.compile("(?<=\\(|: |, |by )[A-Z][^();:]*? \\d{4}[a-z]?(?=\\)|;|\\.?$)");

    /**
     * The reason as HTML: escaped, the given names (also abbreviated, C. cerasi) in italics, each reference linked to the AntCat page
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
        for (String n : names) {
            if (n == null || n.isEmpty()) continue;
            if (!sorted.contains(n)) sorted.add(n);
            String[] p = n.split(" ", 2);   // and abbreviated: C. cerasi
            if (p.length == 2 && !p[0].isEmpty()) {
                String abbr = p[0].charAt(0) + ". " + p[1];
                if (!sorted.contains(abbr)) sorted.add(abbr);
            }
        }
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
