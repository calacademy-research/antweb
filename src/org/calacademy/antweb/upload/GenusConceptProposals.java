package org.calacademy.antweb.upload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.calacademy.antweb.Rank;
import org.calacademy.antweb.Status;
import org.calacademy.antweb.Taxon;

/**
 * Old genus concepts proposed from AntCat's data (Brian Fisher, 2026-11-03: "I am not really interested in one
 * offs ... many genera need this treatment"). For each old genus G with morphospecies or indet specimens (not
 * red-flagged) in a bioregion B, the AntCat species of G that now go under another name (status that uses a
 * current valid name), the genus of each one's current valid name, and the valid species G still has in B.
 *
 * Bioregion of an AntCat species: its current valid name is on bioregion B's list (bioregion_taxon, built each
 * night from AntCat's distribution and AntWeb's specimens). A valid species of G "in B" likewise.
 *
 *   PROPOSE          every moved species known from B went to one genus H, H is a valid genus row, and G has no
 *                    valid species left in B: approve or reject on genusConceptRules.do
 *   SPLIT            the moved species of B went to two or more genera: manual
 *   MIXED            G still has valid species in B: manual
 *   NO_VALID_TARGET  one genus H, but H is not a valid genus row in AntWeb: manual
 *   (not listed)     G itself a synonym genus (GenusSynonymRule does it), a rule already approved or rejected for
 *                    (G, B), or no AntCat species of G known from B that moved
 *
 * The data come from GenusConceptRuleDb.loadPairs() (three grouped queries); this class only decides. Pure:
 * tested in GenusConceptProposalsTest.
 */
public final class GenusConceptProposals {

    private GenusConceptProposals() {}

    public enum Kind { PROPOSE, SPLIT, MIXED, NO_VALID_TARGET, SYNONYM_GENUS, RULED, NONE }

    /** The AntCat species of G known from B that went to one genus H. */
    public static final class Moved {
        public final String newGenus;
        public int species;                  // distinct current valid names in H
        public final List<String> examples = new ArrayList<>();   // "old>new" taxon names
        public String newGenusStatus;        // status of H's taxon row, or null
        public String newGenusRank;
        public Moved(String newGenus) { this.newGenus = newGenus; }
        public boolean newGenusValid() {
            return Status.VALID.equals(newGenusStatus) && Rank.GENUS.equals(newGenusRank);
        }
    }

    /** One old genus in one bioregion. */
    public static final class Pair {
        public final String genus;           // taxon name, e.g. ponerinaemesoponera
        public final String bioregion;
        public String genusStatus;           // status of G's genus row, or null
        public final Map<Integer, Integer> specimensByGroup = new TreeMap<>();
        public final Map<String, Moved> moved = new TreeMap<>();   // by new genus taxon name; never G itself
        public int validSpecies;             // G's valid species and subspecies on B's list
        public Kind kind = Kind.NONE;
        public Pair(String genus, String bioregion) {
            this.genus = genus;
            this.bioregion = bioregion;
        }
        public int specimens() {
            int n = 0;
            for (int c : specimensByGroup.values()) n += c;
            return n;
        }
        public int movedSpecies() {
            int n = 0;
            for (Moved m : moved.values()) n += m.species;
            return n;
        }
        /** PROPOSE / NO_VALID_TARGET: the one genus H. */
        public String newGenus() {
            return moved.size() == 1 ? moved.keySet().iterator().next() : null;
        }
        public String key() { return GenusConceptProposals.key(genus, bioregion); }
        public String toString() { return kind + " " + genus + " " + bioregion + " -> " + moved.keySet(); }
    }

    public static String key(String genus, String bioregion) {
        return genus + "\t" + bioregion;
    }

    /** ruled: the keys (key(genus, bioregion)) of the approved and rejected rules. */
    public static Kind classify(Pair p, Set<String> ruled) {
        if (Status.SYNONYM.equals(p.genusStatus)) return Kind.SYNONYM_GENUS;
        if (ruled != null && ruled.contains(p.key())) return Kind.RULED;
        if (p.moved.isEmpty()) return Kind.NONE;
        if (p.validSpecies > 0) return Kind.MIXED;
        if (p.moved.size() > 1) return Kind.SPLIT;
        Moved m = p.moved.values().iterator().next();
        return m.newGenusValid() ? Kind.PROPOSE : Kind.NO_VALID_TARGET;
    }

    /** Classifies every pair; returns the ones listed (PROPOSE, SPLIT, MIXED, NO_VALID_TARGET), PROPOSE first, most specimens first. */
    public static List<Pair> listed(List<Pair> pairs, Set<String> ruled) {
        List<Pair> out = new ArrayList<>();
        for (Pair p : pairs) {
            p.kind = classify(p, ruled);
            if (p.kind == Kind.PROPOSE || p.kind == Kind.SPLIT || p.kind == Kind.MIXED || p.kind == Kind.NO_VALID_TARGET) out.add(p);
        }
        out.sort(Comparator.comparing((Pair p) -> p.kind.ordinal()).thenComparing(p -> -p.specimens()).thenComparing(Pair::key));
        return out;
    }

    /** "3 of 3 species of Mesoponera in Afrotropical moved to Xiphopelta" (PROPOSE), or the split / mixed counts. */
    public static String evidence(Pair p) {
        String g = Taxon.getPrettyTaxonName(p.genus);
        int total = p.movedSpecies();
        StringBuilder b = new StringBuilder();
        if (p.moved.size() == 1) {
            Moved m = p.moved.values().iterator().next();
            b.append(m.species).append(" of ").append(total).append(" species").append(" of ").append(g)
             .append(" in ").append(p.bioregion).append(" moved to ").append(Taxon.getPrettyTaxonName(m.newGenus));
        } else {
            b.append(total).append(" species of ").append(g).append(" in ").append(p.bioregion).append(" moved to ");
            int i = 0;
            for (Moved m : p.moved.values()) {
                if (i++ > 0) b.append(", ");
                b.append(Taxon.getPrettyTaxonName(m.newGenus)).append(" (").append(m.species).append(")");
            }
        }
        if (p.validSpecies > 0) {
            b.append("; ").append(p.validSpecies).append(" valid species").append(" of ")
             .append(g).append(" still in ").append(p.bioregion);
        }
        return b.toString();
    }

    /** Up to max examples, "Mesoponera ambigua -> Xiphopelta ambigua". */
    public static List<String> examples(Pair p, int max) {
        List<String> out = new ArrayList<>();
        for (Moved m : p.moved.values()) {
            for (String e : m.examples) {
                if (out.size() >= max) return out;
                int gt = e.indexOf('>');
                out.add(gt < 0 ? Taxon.getPrettyTaxonName(e)
                    : Taxon.getPrettyTaxonName(e.substring(0, gt)) + " -> " + Taxon.getPrettyTaxonName(e.substring(gt + 1)));
            }
        }
        return out;
    }
}
