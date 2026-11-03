package org.calacademy.antweb.upload;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import org.calacademy.antweb.Rank;
import org.calacademy.antweb.Status;
import org.calacademy.antweb.Taxon;

/**
 * Old genus concepts (Brian Fisher, 2026-11-03, list design rules): "Where every species AntCat moved out of an
 * old genus in one bioregion went to one genus, an approved rule lets the nightly name update move that
 * bioregion's specimens of the old genus (morphospecies and indets) to the new genus, keeping the code part of
 * the name and recording the reason. Rules are proposed from AntCat data and approved by Brian one by one;
 * ambiguous splits stay manual."
 *
 * The old genus stays valid in AntCat (Mesoponera is valid elsewhere), so this is not GenusSynonymRule. Only a
 * morphospecies or indet of the old genus whose specimen bioregion is one of the rule's moves; a species name
 * never (AntCat places those); the new genus must be a valid genus row. Used by the nightly name update
 * (SpecimenNameUpdate, third pass) and the specimen upload (SpecimenUploadProcess.setStatusAndCurrentValidName).
 *
 * The approved rules are rows of table genus_concept_rule (GenusConceptRuleDb), one per old genus and bioregion,
 * approved or rejected on genusConceptRules.do from the proposals AntCat's data gives (GenusConceptProposals;
 * Brian Fisher, 2026-11-03: "many genera need this treatment"). SEED is only the first approved rule, put in the
 * table when it is created. Tested in GenusConceptRuleTest.
 */
public final class GenusConceptRule {

    private GenusConceptRule() {}

    /** One approved rule. Genera are taxon names (subfamily + genus); bioregions as in specimen.bioregion. */
    public static final class Rule {
        public final String oldGenus;
        public final Set<String> bioregions;
        public final String newGenus;
        public final String approvedBy;
        public final String approvedOn;
        public final String note;
        public Rule(String oldGenus, Set<String> bioregions, String newGenus, String approvedBy, String approvedOn, String note) {
            this.oldGenus = oldGenus;
            this.bioregions = bioregions;
            this.newGenus = newGenus;
            this.approvedBy = approvedBy;
            this.approvedOn = approvedOn;
            this.note = note;
        }
        public String toString() { return oldGenus + " " + bioregions + " -> " + newGenus + " (" + approvedBy + ", " + approvedOn + ")"; }
    }

    /** The first approved rule, put in genus_concept_rule when the table is created (GenusConceptRuleDb.ensureSchema). */
    public static final List<Rule> SEED = List.of(
        new Rule("ponerinaemesoponera", Set.of("Afrotropical", "Malagasy"), "ponerinaexiphopelta", "Brian Fisher", "2026-11-03",
            "AntCat moved every Afrotropical and Malagasy species of Mesoponera to Xiphopelta")
    );

    /** The rule for a genus and bioregion, or null. */
    public static Rule ruleFor(List<Rule> rules, String genus, String bioregion) {
        if (genus == null || bioregion == null) return null;
        for (Rule r : rules) {
            if (r.oldGenus.equals(genus) && r.bioregions.contains(bioregion)) return r;
        }
        return null;
    }

    /** The old genera that have a rule (for the nightly candidates query). */
    public static Set<String> oldGenera(List<Rule> rules) {
        Set<String> s = new java.util.TreeSet<>();
        for (Rule r : rules) s.add(r.oldGenus);
        return s;
    }

    /** The bioregions of all rules (for the nightly candidates query). */
    public static Set<String> bioregions(List<Rule> rules) {
        Set<String> s = new java.util.TreeSet<>();
        for (Rule r : rules) s.addAll(r.bioregions);
        return s;
    }

    /** A decision: the rule and the new name. */
    public static final class Move {
        public final Rule rule;
        public final String newName;
        Move(Rule rule, String newName) {
            this.rule = rule;
            this.newName = newName;
        }
        public String toString() { return newName + " by " + rule; }
    }

    /**
     * Where a specimen filed under taxonName in bioregion goes, or null: no rule for its genus and bioregion, not
     * a morphospecies or indet, or the new genus is not a valid genus row.
     */
    public static Move decide(List<Rule> rules, String taxonName, String bioregion, CurrentValidNameRule.Lookup lookup) throws SQLException {
        String genus = GenusSynonymRule.genusPart(taxonName);
        Rule rule = ruleFor(rules, genus, bioregion);
        if (rule == null) return null;
        String rest = taxonName.substring(genus.length());
        if (rest.isBlank() || !Taxon.isMorphoOrIndet(taxonName)) return null;
        CurrentValidNameRule.TaxonRow target = lookup.getTaxon(rule.newGenus);
        if (target == null || !Rank.GENUS.equals(target.rank) || !Status.VALID.equals(target.status)) return null;
        return new Move(rule, rule.newGenus + rest);
    }

    /** "Old genus concept: Mesoponera in the Afrotropical region is Xiphopelta (rule approved by Brian Fisher, 2026-11-03)". */
    public static NameUpdateReason reason(Rule rule, String bioregion, Integer antcatId) {
        return new NameUpdateReason(NameUpdateReason.OLD_GENUS_CONCEPT + Taxon.getPrettyTaxonName(rule.oldGenus) + " in the " + bioregion
            + " region is " + Taxon.getPrettyTaxonName(rule.newGenus) + " (rule approved by " + rule.approvedBy + ", " + rule.approvedOn + ")", antcatId);
    }
}
