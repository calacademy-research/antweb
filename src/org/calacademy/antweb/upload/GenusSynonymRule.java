package org.calacademy.antweb.upload;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.calacademy.antweb.Rank;
import org.calacademy.antweb.Status;
import org.calacademy.antweb.Taxon;

/**
 * Where a specimen goes when the genus part of the name it is filed under is not a genus AntWeb shows
 * (Brian Fisher, 2026-11-03). Two cases, one rule; used by the nightly name update (SpecimenNameUpdate,
 * second pass) and tested without a database (GenusSynonymRuleTest).
 *
 * 1. The genus is a junior synonym of exactly one valid genus (Phyracaces -> Lioponera, Wasmannia ->
 *    Allomerus per Longino &amp; Branstetter 2026, Oligomyrmex -> Carebara, ...). A morphospecies or indet
 *    filed under it moves to the valid genus with the code part of its name:
 *      dorylinaephyracaces (indet)   -> dorylinaelioponera (indet)
 *      myrmicinaewasmannia alw02     -> myrmicinaeallomerus alw02
 *    A species name AntCat knows is left to the species rule (the first pass). One AntCat does not know
 *    (AntCat has no record of most past combinations: Pyramica ludovici is only "Combination in Pyramica"
 *    in Strumigenys ludovici's history) is never moved by guess, the epithet into the senior genus
 *    (homonymy makes that unsafe): left, and listed for the AntCat editors (COMBINATION_NOT_IN_ANTCAT,
 *    with the senior genus). Once AntCat has the combination, the species rule moves it the next night.
 *
 * 2. The name has a placeholder subfamily ("(formicidae)pyramica ludovici": the upload got no subfamily,
 *    or could not place the genus in one). The genus is looked up by its name alone (taxarank genus, any
 *    status, a real subfamily); it must be exactly one genus row, otherwise the specimen stays
 *    (GENUS_NOT_IN_ANTCAT, listed for the curator, never corrected by guess; GENUS_AMBIGUOUS). The name is
 *    rebuilt with that subfamily (myrmicinaepyramica ludovici) and then:
 *      a species AntCat knows   -> the species rule, as a normal upload files it (Pyramica ludovici ->
 *                                  Strumigenys ludovici; a valid name stays as rebuilt)
 *      a morphospecies or indet -> case 1 when the genus is a synonym, otherwise the rebuilt name
 *      a species AntCat does not know -> left: in a synonym genus COMBINATION_NOT_IN_ANTCAT (listed), in a
 *                                  valid genus NOT_IN_ANTCAT (counted)
 *
 * The valid genus must be a genus row with status valid; the rule never makes up a genus. Taxon rows
 * for a new morphospecies/indet name are made by the caller (apply mode only).
 */
public final class GenusSynonymRule {

    private GenusSynonymRule() {}

    /** The subfamily placeholders an upload gives a name it could not place in a subfamily. */
    public static final String[] PLACEHOLDER_SUBFAMILIES = {"(formicidae)", "(indet)"};

    /** insert_method of a taxon row made for a moved morphospecies or indet. */
    public static final String INSERT_METHOD = "genusSynonymMove";

    public enum Kind {
        /** Nothing for this rule (a valid genus, a species name in a synonym genus: the species rule's). */
        NONE,
        /** A morphospecies or indet goes to the valid genus of its synonym genus. */
        GENUS_MOVE,
        /** Placeholder subfamily: filed under the rebuilt name (the genus's own subfamily). */
        SUBFAMILY_PLACED,
        /** Placeholder subfamily, species AntCat knows under another name: its current valid name. */
        SPECIES_RULE,
        /** Placeholder subfamily, the species rule would file it under a homonym placeholder: held for the curator. */
        SHARED_NAME,
        /** Placeholder subfamily, no genus row of that name: left, listed for the curator. */
        GENUS_NOT_IN_ANTCAT,
        /** Placeholder subfamily, two or more genus rows of that name: left. */
        GENUS_AMBIGUOUS,
        /** A species AntCat does not know (not a morphospecies or indet) after a placeholder, in a valid genus: left. */
        NOT_IN_ANTCAT,
        /**
         * A species name AntCat does not know in a synonym genus (also after a placeholder): left, listed for the
         * AntCat editors with the senior genus (validGenus, when there is one). Brian Fisher, 2026-11-03.
         */
        COMBINATION_NOT_IN_ANTCAT,
        /** A synonym genus whose current valid genus has no valid genus row: left. */
        NO_VALID_GENUS
    }

    /** The genus rows of a genus name, which the placeholder case needs on top of CurrentValidNameRule.Lookup. */
    public interface Genera {
        /** taxon_name of each genus row (taxarank genus) of this genus name with a real subfamily (not in parentheses). */
        List<String> genusRows(String genusName) throws SQLException;
    }

    public static final class Result {
        public final Kind kind;
        /** GENUS_MOVE, SUBFAMILY_PLACED, SPECIES_RULE: the name the specimen goes under. */
        public final String newName;
        /** The genus part of the filed name ("dorylinaephyracaces", "(formicidae)pyramica"). */
        public final String filedGenus;
        /** The genus row the filed name stands for (dorylinaephyracaces, myrmicinaepyramica), or null. */
        public final String genus;
        /** GENUS_MOVE: the valid genus row (dorylinaelioponera); COMBINATION_NOT_IN_ANTCAT: the senior genus, or null. */
        public final String validGenus;
        /** The filed name with the genus row's subfamily (placeholder case), else the filed name. */
        public final String rebuiltName;
        /** SPECIES_RULE, SHARED_NAME (and SUBFAMILY_PLACED of a species name): the species rule's answer for rebuiltName. */
        public final CurrentValidNameRule.Result speciesRule;
        /** True when the filed name has a placeholder subfamily. */
        public final boolean placeholder;

        Result(Kind kind, String newName, String filedGenus, String genus, String validGenus, String rebuiltName,
               CurrentValidNameRule.Result speciesRule, boolean placeholder) {
            this.kind = kind;
            this.newName = newName;
            this.filedGenus = filedGenus;
            this.genus = genus;
            this.validGenus = validGenus;
            this.rebuiltName = rebuiltName;
            this.speciesRule = speciesRule;
            this.placeholder = placeholder;
        }

        public boolean moves() {
            return newName != null && (kind == Kind.GENUS_MOVE || kind == Kind.SUBFAMILY_PLACED || kind == Kind.SPECIES_RULE);
        }

        public String toString() { return kind + (newName != null ? " -> " + newName : ""); }
    }

    private static Result none(Kind kind, String filedGenus, String genus, String rebuilt, boolean placeholder) {
        return new Result(kind, null, filedGenus, genus, null, rebuilt, null, placeholder);
    }

    /** "dorylinaephyracaces (indet)" -> "dorylinaephyracaces"; null when the name has no part after the genus. */
    public static String genusPart(String taxonName) {
        if (taxonName == null) return null;
        int sp = taxonName.indexOf(' ');
        return sp <= 0 ? null : taxonName.substring(0, sp);
    }

    /** "(formicidae)pyramica" -> "pyramica"; null when the genus part has no placeholder subfamily or the genus is not a plain name. */
    public static String placeholderGenus(String genusPart) {
        if (genusPart == null) return null;
        for (String p : PLACEHOLDER_SUBFAMILIES) {
            if (genusPart.startsWith(p)) {
                String g = genusPart.substring(p.length());
                return g.matches("[a-z]+") ? g : null;
            }
        }
        return null;
    }

    /**
     * The decision for a specimen filed under taxonName. lookup: the taxon table (as CurrentValidNameRule; the
     * rank must be read); genera: the genus rows by genus name (placeholder case only).
     */
    public static Result decide(String taxonName, CurrentValidNameRule.Lookup lookup, Genera genera) throws SQLException {
        String filedGenus = genusPart(taxonName);
        if (filedGenus == null) return none(Kind.NONE, null, null, taxonName, false);
        String rest = taxonName.substring(filedGenus.length());   // " (indet)", " alw02", " ludovici"
        if (rest.isBlank()) return none(Kind.NONE, filedGenus, null, taxonName, false);

        String placeholderGenus = placeholderGenus(filedGenus);
        boolean placeholder = placeholderGenus != null;
        String genus;
        if (placeholder) {
            List<String> rows = genera == null ? List.of() : genera.genusRows(placeholderGenus);
            if (rows.isEmpty()) return none(Kind.GENUS_NOT_IN_ANTCAT, filedGenus, null, taxonName, true);
            if (rows.size() > 1) return none(Kind.GENUS_AMBIGUOUS, filedGenus, null, taxonName, true);
            genus = rows.get(0);
        } else {
            genus = filedGenus;
        }
        CurrentValidNameRule.TaxonRow genusRow = lookup.getTaxon(genus);
        if (genusRow == null || !Rank.GENUS.equals(genusRow.rank)) {
            return none(placeholder ? Kind.GENUS_NOT_IN_ANTCAT : Kind.NONE, filedGenus, null, taxonName, placeholder);
        }
        String rebuilt = genus + rest;
        boolean synonymGenus = Status.SYNONYM.equals(genusRow.status);

        if (!Taxon.isMorphoOrIndet(rebuilt)) {
            // A species name: the species rule decides, as for any uploaded name.
            CurrentValidNameRule.Result r = CurrentValidNameRule.decide(rebuilt, lookup);
            if (r.kind == CurrentValidNameRule.Kind.NOT_IN_ANTCAT) {
                // Never moved by guess (Brian Fisher, 2026-11-03): left, listed for AntCat or counted.
                if (synonymGenus) {
                    return new Result(Kind.COMBINATION_NOT_IN_ANTCAT, null, filedGenus, genus, validGenus(genusRow, genus, lookup, genera),
                        rebuilt, r, placeholder);
                }
                return placeholder ? none(Kind.NOT_IN_ANTCAT, filedGenus, genus, rebuilt, true)
                    : none(Kind.NONE, filedGenus, genus, rebuilt, false);
            }
            // Filed under its own (subfamily) name: the first pass handles it.
            if (!placeholder) return none(Kind.NONE, filedGenus, genus, rebuilt, false);
            switch (r.kind) {
                case REPLACE:
                    return new Result(Kind.SPECIES_RULE, r.validTaxonName, filedGenus, genus, null, rebuilt, r, true);
                case TARGET_IS_HOMONYM:
                    return new Result(Kind.SHARED_NAME, null, filedGenus, genus, null, rebuilt, r, true);
                default:
                    // KEEP, NO_CURRENT_VALID_NAME, TARGET_MISSING, TARGET_SAME: the upload keeps the name, as rebuilt.
                    return new Result(Kind.SUBFAMILY_PLACED, rebuilt, filedGenus, genus, null, rebuilt, r, true);
            }
        }

        // A morphospecies or indet.
        if (synonymGenus) {
            String valid = validGenus(genusRow, genus, lookup, genera);
            if (valid == null) return none(Kind.NO_VALID_GENUS, filedGenus, genus, rebuilt, placeholder);
            return new Result(Kind.GENUS_MOVE, valid + rest, filedGenus, genus, valid, rebuilt, null, placeholder);
        }
        if (placeholder && Status.VALID.equals(genusRow.status)) {
            return new Result(Kind.SUBFAMILY_PLACED, rebuilt, filedGenus, genus, null, rebuilt, null, true);
        }
        // A valid genus filed under its own name, or a genus of another status (homonym, unavailable, ...).
        return none(placeholder ? Kind.NO_VALID_GENUS : Kind.NONE, filedGenus, genus, rebuilt, placeholder);
    }

    /**
     * The valid genus row of a synonym genus row, or null (none, not a genus, not valid, a homonym, itself). Its
     * current_valid_name is usually subfamily+genus; a bare genus name ("Allomerus") is looked up by genus name and
     * must be one genus row, as TaxonMgr.getCurrentValidGenus reads it.
     */
    static String validGenus(CurrentValidNameRule.TaxonRow genusRow, String genus, CurrentValidNameRule.Lookup lookup, Genera genera) throws SQLException {
        String cvn = genusRow.currentValidName;
        if (cvn == null || cvn.isBlank()) return null;
        cvn = cvn.trim();
        if (lookup.isHomonym(cvn)) return null;
        String valid = lookup.getCurrentValidTaxonName(cvn);
        if (valid == null && genera != null && cvn.matches("[A-Za-z]+")) {
            List<String> rows = genera.genusRows(cvn.toLowerCase(Locale.ROOT));
            if (rows.size() == 1) valid = rows.get(0);
        }
        if (valid == null || valid.equals(genus) || valid.indexOf(' ') >= 0) return null;
        CurrentValidNameRule.TaxonRow validRow = lookup.getTaxon(valid);
        if (validRow == null || !Rank.GENUS.equals(validRow.rank) || !Status.VALID.equals(validRow.status)) return null;
        return valid;
    }

    /** The species and subspecies of a name's part after the genus: " alw02" -> {alw02, null}; " (indet)" -> {(indet), null}. */
    public static String[] speciesParts(String taxonName) {
        String g = genusPart(taxonName);
        if (g == null) return new String[] {null, null};
        String rest = taxonName.substring(g.length()).trim();
        int sp = rest.indexOf(' ');
        if (sp < 0) return new String[] {rest, null};
        return new String[] {rest.substring(0, sp), rest.substring(sp + 1).trim()};
    }

    /** The genus rows of a genus name, as plain queries (for the nightly name update and its tests). */
    public static Genera plainGenera(Connection connection) {
        return genusName -> {
            List<String> rows = new ArrayList<>();
            if (genusName == null) return rows;
            try (PreparedStatement stmt = connection.prepareStatement(
                    "select taxon_name from taxon where taxarank = 'genus' and genus = ? and taxon_name not like '(%'"
                    + " and (family = 'formicidae' or family is null) order by taxon_name")) {
                stmt.setString(1, genusName.toLowerCase(Locale.ROOT));
                try (ResultSet r = stmt.executeQuery()) {
                    while (r.next()) rows.add(r.getString(1));
                }
            }
            return rows;
        };
    }
}
