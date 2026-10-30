package org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The rules of the numbers at the top of a place page (country, adm1, island, region, subregion,
 * project, museum, bioregion), written once, used by the nightly run that stores them (GeolocaleDb,
 * ProjectDb, MuseumDb, BioregionDb) and by the count check (CountCheck, CountInvariants) that checks
 * them (Brian Fisher, 2026-10-20: every number on the site validated by a test).
 *
 *   Subfamilies  subfamily rows on the place's list that count as a subfamily (countsAsSubfamily, Brian Fisher
 *                2026-10-30): valid subfamilies only, on every kind of place, curated projects too (this replaced B67 of
 *                2026-10-27, every status for curated projects); the placeholder "(formicidae)" and misspelled subfamily
 *                names (status unrecognized) are on the list but not counted
 *   Genera       genus rows on the list that count as a genus (countsAsGenus, Brian Fisher 2026-10-30): valid genera and
 *                morphogenera; placeholders such as "(myrmicinae)" (identified only to subfamily) and genus names AntCat
 *                does not know (status unrecognized, "(formicidae)polyrachis") are on the list but not counted
 *   Species      species and subspecies rows on the list, status valid or morphotaxon (SPECIES_STATUSES,
 *                2026-10-28), ants (family formicidae), identified to genus (identifiedToGenus, 2026-10-22)
 *   Valid        species and subspecies rows on the list, status valid, not fossil
 *   Endemic      rows flagged endemic, status valid (ENDEMIC_STATUSES, 2026-10-28; endemicEligible, nativeRow)
 *   Specimens    the specimens of the species and subspecies rows of the list (every kind of place since
 *                2026-10-28; museums and bioregions counted all their specimens before)
 *
 * Counted status (of list rows and specimens): valid, unrecognized, morphotaxon, indetermined, unidentifiable
 * (CountCheck.COUNTABLE).
 *
 * Counting rules, Brian Fisher 2026-10-28 (docs/numbers.md section 5): a specimen counts at each rank it is
 * identified to, and no further. A "Lioponera indet" specimen counts in Specimens, makes Lioponera count in Genera
 * (its genus row is on the list) and its subfamily in Subfamilies, but is not a species: Species counts names of
 * status valid or morphotaxon only. indetermined, unidentifiable and unrecognized names never count as species
 * (until 2026-10-28 all five counted statuses did).
 *
 * Until 2026-10-21 Genera and Species (and the Subfamilies of museums and bioregions) were roll-up
 * sums of the per-row child counts of the list (CountDb.getCountableTaxonCount: sum(genus_count) of
 * the subfamily rows, sum(species_count) of the genus rows). A child count that was stale, of the
 * wrong rank, or a species whose genus row was missing from the list made the total wrong; projects
 * also summed yesterday's row counts (the list was recounted after the totals). Now they are counted
 * directly, so the total is right whatever the row counts are.
 *
 * Pie charts on the place page:
 *   Species per Subfamily   the rows counted by Species, grouped by subfamily: the slices add up to Species
 *   Specimens per Subfamily geolocales and projects: the specimen counts of the species and subspecies
 *                           rows, grouped by subfamily; museums and bioregions: the place's own specimens
 *                           (specimen.museum, specimen.bioregion) by subfamily. Either way the slices add
 *                           up to Specimens (one slice "Other" for names that are not a subfamily of the taxon table, and when the JSON would not fit its column, the smallest slices merged into it). Before 2026-10-21 museums and bioregions
 *                           counted every specimen in AntWeb of the names on their list, and project and
 *                           bioregion species pies counted rows of every rank.
 */
public final class PlaceTotals {

    private PlaceTotals() {}

    public static final String GEOLOCALE = "geolocale";
    public static final String PROJECT = "project";
    public static final String MUSEUM = "museum";
    public static final String BIOREGION = "bioregion";
    public static final String[] TYPES = {GEOLOCALE, PROJECT, MUSEUM, BIOREGION};

    /** Counted statuses (StatusSet.getCountables): of specimens and of list rows of every rank above species. */
    public static final String COUNTABLE = "('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable')";

    /**
     * The statuses of a name that counts as a species (Brian Fisher, 2026-10-28): valid names and morphospecies. Names of
     * the other counted statuses (indetermined, unidentifiable, unrecognized) are on the lists and their specimens count,
     * but they are not species: their specimens are identified to genus only (or, unrecognized, to a name AntCat does
     * not have; those are for curators to check against AntCat).
     */
    public static final String SPECIES_STATUSES = "('valid', 'morphotaxon')";

    /** The same in Java. */
    public static final java.util.List<String> SPECIES_STATUS_LIST = java.util.Arrays.asList("valid", "morphotaxon");

    /**
     * Statuses of names whose specimens are identified to genus only (the genus lists say "N specimens identified to genus
     * only", the species lists name them under the list): indetermined ("Lioponera indet") and unidentifiable (an AntCat
     * name that cannot be recognized, so its specimens are known to genus only).
     */
    public static final String GENUS_ONLY_STATUSES = "('indetermined', 'unidentifiable')";

    /**
     * The statuses of a name that can be endemic (Brian Fisher, 2026-10-22 and 2026-10-28, docs/numbers.md Q3): valid
     * names only. Morphospecies (their codes are not comparable between collections, 10-22), indetermined, unidentifiable
     * and unrecognized names (not species, 10-28) never count toward endemism, at any place: countries, adm1s, regions,
     * subregions, bioregions. Until 10-28 countries and adm1s took every counted status but morphotaxon, bioregions all
     * five, and the lists and numbers (flaggedCondition) valid and unrecognized. Every endemism rule takes it from here
     * (GeolocaleDb, BioregionDb, flaggedCondition, NumberChecks.endemicFlags).
     */
    public static final String ENDEMIC_STATUSES = "('valid')";

    /** The same in Java. */
    public static final java.util.List<String> ENDEMIC_STATUS_LIST = java.util.Arrays.asList("valid");

    /**
     * SQL: the taxon (alias a) can be endemic: a species or subspecies of an endemic status (ENDEMIC_STATUSES), not
     * fossil, an ant, identified to genus.
     */
    public static String endemicEligible(String alias) {
        return alias + ".taxarank in ('species', 'subspecies') and " + alias + ".status in " + ENDEMIC_STATUSES
            + " and " + alias + ".fossil = 0 and " + alias + ".family = 'formicidae' and " + identifiedToGenus(alias);
    }

    /**
     * SQL: the list row (alias a, geolocale_taxon or bioregion_taxon) is native, not flagged introduced (Q4, Brian Fisher
     * 2026-10-21: introduced records never count toward endemism). Endemism counts native rows only, so a species
     * introduced in Reunion stays endemic to Madagascar, and an introduced row is never flagged endemic. The introduced
     * flags are set before the endemic ones (GeolocaleDb.updateCounts, BioregionDb.populate).
     */
    public static String nativeRow(String alias) {
        return "coalesce(" + alias + ".is_introduced, 0) = 0";
    }

    /** SQL: the taxon (alias a) has a status that counts as a species. */
    public static String speciesStatusCondition(String alias) {
        return alias + ".status in " + SPECIES_STATUSES;
    }

    /** Java: whether a name of this status counts as a species (SPECIES_STATUSES). */
    public static boolean isSpeciesStatus(String status) {
        return status != null && SPECIES_STATUS_LIST.contains(status);
    }

    /**
     * SQL: a species or subspecies name (alias a) that counts as a species: rank species or subspecies, status valid or
     * morphotaxon, identified to genus. Every species count uses it: the place's Species, the genus rows' Species of
     * every list (the nightly crawl, getCountChildrenQuery), the species lists (TaxaPage), "species + ssp. imaged", the
     * count check.
     */
    public static String countsAsSpecies(String alias) {
        return alias + ".taxarank in ('species', 'subspecies') and " + speciesStatusCondition(alias) + " and " + identifiedToGenus(alias);
    }

    /**
     * The statuses of a genus row that counts as a genus (Brian Fisher, 2026-10-30): valid genera and morphogenera
     * ("Form_gen-afrc-za01", status morphotaxon). Genus names AntCat does not know (misspellings, old names: status
     * unrecognized) stay in AntWeb with their specimens but are not genera; placeholders such as "(myrmicinae)" or
     * "(formicidae)" (specimens identified only to subfamily or family) never are.
     */
    public static final String GENUS_STATUSES = "('valid', 'morphotaxon')";

    /** The same in Java. */
    public static final java.util.List<String> GENUS_STATUS_LIST = java.util.Arrays.asList("valid", "morphotaxon");

    /**
     * SQL: the taxon (alias a) is not one of AntWeb's placeholders: its taxon name has no parenthesis. Placeholders are
     * the subfamily or family in parentheses in place of a name ("(formicidae)", "(dolichoderinae)", "(dacetini)"), a
     * genus filed under the placeholder subfamily ("(formicidae)polyrachis"), or a stray parenthesis from an upload file
     * ("Amblyoponinae)"). Brian Fisher, 2026-10-30: a value with parentheses and a subfamily or family name is
     * undetermined, never a genus (and never a subfamily).
     */
    public static String notPlaceholder(String alias) {
        String n = "coalesce(" + alias + ".taxon_name, '')";
        return "(" + n + " not like '%(%' and " + n + " not like '%)%')";
    }

    /** The same rule in Java, for a taxon name. */
    public static boolean isPlaceholder(String taxonName) {
        return taxonName != null && (taxonName.contains("(") || taxonName.contains(")"));
    }

    /**
     * SQL: a genus row (alias a) that counts as a genus (Brian Fisher, 2026-10-30): status valid or morphotaxon
     * (GENUS_STATUSES), identified to genus (identifiedToGenus) and not a placeholder (notPlaceholder). Every Genera
     * number uses it: the place's Genera (rankCondition(GENUS)), a subfamily row's "N Genera" on every list (the nightly
     * crawl, childCountCondition), upload groups' Genera (GroupCountDb), and the count check.
     */
    public static String countsAsGenus(String alias) {
        return "(" + alias + ".status in " + GENUS_STATUSES + " and " + identifiedToGenus(alias) + " and " + notPlaceholder(alias)
            + " and " + genusWithoutParenthesis(alias) + ")";
    }

    /**
     * The same rule in Java: status and taxon name and genus of a genus row. Identical to countsAsGenus (2026-10-30,
     * review of #307: the two differed on a genus value with a parenthesis): a status of GENUS_STATUSES, identified to
     * genus (isIdentifiedToGenus), a taxon name without "(" or ")" (isPlaceholder) and a genus value without "(" or ")".
     */
    public static boolean isCountedGenus(String status, String taxonName, String genus) {
        return status != null && GENUS_STATUS_LIST.contains(status) && isIdentifiedToGenus(genus) && !isPlaceholder(taxonName)
            && !hasParenthesis(genus);
    }

    /** SQL: the genus value (alias a) has no "(" or ")" (a value with one is a placeholder, never a genus). */
    public static String genusWithoutParenthesis(String alias) {
        String g = "coalesce(" + alias + ".genus, '')";
        return "(" + g + " not like '%(%' and " + g + " not like '%)%')";
    }

    /** Java: a value containing "(" or ")". */
    public static boolean hasParenthesis(String value) {
        return value != null && (value.contains("(") || value.contains(")"));
    }

    /**
     * SQL: a subfamily row (alias a) that counts as a subfamily (Brian Fisher, 2026-10-30, the same principle as
     * countsAsGenus): status valid (AntCat's valid subfamilies) and not a placeholder. The placeholder "(formicidae)"
     * (status morphotaxon) and subfamily names AntCat does not know (misspellings, status unrecognized) are not
     * subfamilies. Every Subfamilies number uses it: the place's Subfamilies on every kind of place (rankCondition
     * (SUBFAMILY); curated projects too, replacing B67), the Formicidae row's "N Subfamilies" of every list (the nightly
     * crawl, childCountCondition) and the count check. Upload groups' Subfamilies already counted valid ant subfamilies
     * only (SpecimenDb.getAntSpecimenCriteria).
     */
    public static String countsAsSubfamily(String alias) {
        return "(" + alias + ".status = 'valid' and " + notPlaceholder(alias) + ")";
    }

    /** The same rule in Java: status and taxon name of a subfamily row. */
    public static boolean isCountedSubfamily(String status, String taxonName) {
        return "valid".equals(status) && !isPlaceholder(taxonName);
    }

    /**
     * The extra condition (alias a) of the child rows the nightly crawl counts for a parent row of a list
     * (Geolocale, Project, Museum, Bioregion getCountChildrenQuery): a subfamily row's Genera counts its genus rows that
     * count as a genus (countsAsGenus), the Formicidae row's Subfamilies its subfamily rows that count as a subfamily
     * (countsAsSubfamily: valid, not a placeholder; 2026-10-30). Other ranks: none here (species: speciesStatusCondition and identifiedToGenus, in the queries).
     */
    public static String childCountCondition(String rank, String alias) {
        if (GENUS.equals(rank)) return " and " + countsAsGenus(alias);
        if (SUBFAMILY.equals(rank)) return " and " + countsAsSubfamily(alias);
        return "";
    }

    public static final String SUBFAMILY = "subfamily";
    public static final String GENUS = "genus";
    public static final String SPECIES = "species";

    /** The place's list table. */
    public static String listTable(String type) {
        switch (type) {
            case GEOLOCALE: return "geolocale_taxon";
            case PROJECT: return "proj_taxon";
            case MUSEUM: return "museum_taxon";
            case BIOREGION: return "bioregion_taxon";
            default: throw new IllegalArgumentException(type);
        }
    }

    /** The column of the list table that names the place. */
    public static String listKey(String type) {
        switch (type) {
            case GEOLOCALE: return "geolocale_id";
            case PROJECT: return "project_name";
            case MUSEUM: return "code";
            case BIOREGION: return "bioregion_name";
            default: throw new IllegalArgumentException(type);
        }
    }

    /** The place's own table (its totals). */
    public static String placeTable(String type) {
        switch (type) {
            case GEOLOCALE: return "geolocale";
            case PROJECT: return "project";
            case MUSEUM: return "museum";
            case BIOREGION: return "bioregion";
            default: throw new IllegalArgumentException(type);
        }
    }

    /** The key column of the place's own table. */
    public static String placeKey(String type) {
        switch (type) {
            case GEOLOCALE: return "id";
            case PROJECT: return "project_name";
            case MUSEUM: return "code";
            case BIOREGION: return "name";
            default: throw new IllegalArgumentException(type);
        }
    }

    /** Taxon conditions (alias t) of the rows a total counts. rank: subfamily, genus or species. */
    public static String rankCondition(String rank) {
        switch (rank) {
            // 2026-10-30: valid subfamilies only, no placeholder (countsAsSubfamily; was the five counted statuses, and
            // "(formicidae)", a subfamily row of status morphotaxon since 2017, counted)
            case SUBFAMILY: return "t.taxarank = 'subfamily' and " + countsAsSubfamily("t") + ANTS;
            // 2026-10-30: valid genera and morphogenera, not placeholders (countsAsGenus); was the five counted statuses
            case GENUS: return "t.taxarank = 'genus' and " + countsAsGenus("t") + ANTS;
            // 2026-10-28: valid and morphotaxon only (SPECIES_STATUSES); was the five counted statuses
            case SPECIES: return "t.taxarank in ('species', 'subspecies') and t.status in " + SPECIES_STATUSES + ANTS + " and " + identifiedToGenus("t");
            default: throw new IllegalArgumentException(rank);
        }
    }

    /*
     * Brian Fisher, 2026-10-22: names not identified to genus (the genus part is indet, (indet), a subfamily in
     * parentheses, or the whole name is unidentified) do not count as species anywhere: not in species totals, not in
     * endemism, not in the species counts of lists. Their specimens still count in specimen totals (under their
     * subfamily where it is known). No placeholder genus rows are ever made for them.
     *
     * The one definition, used by every species count and by the count check: the taxon's genus column is not a
     * placeholder. AntWeb writes these genus placeholders: "(indet)" and "((indet))" (no genus in the file; the
     * second when the subfamily was "(indet)" too), "(formicinae)" and the like (the subfamily in parentheses),
     * "(formicidae)" (no subfamily either), and "indet" or "undet" typed in the file. Staging 2026-10-22, for example:
     * '(indet)((indet)) (indet)', '(indet)(formicinae) (indet)', 'formicinaeindet (indet)', 'indetindet (indet)'.
     * Morphogenera ('amblyoponinaeamblyoponine_genus8 nz01') are identified to genus and count.
     */

    /** The genus values that mean "not identified to genus" (compared in lower case), besides any value in parentheses. */
    public static final String[] UNIDENTIFIED_GENERA = {"indet", "undet", "(indet)", "((indet))"};

    /** SQL: the taxon (alias a) is identified to genus (see above). A row without a genus value counts as before. */
    public static String identifiedToGenus(String alias) {
        // trimmed, as isIdentifiedToGenus (2026-10-30: the SQL and the Java rule are the same)
        String g = "trim(coalesce(" + alias + ".genus, ''))";
        return "(" + g + " not like '(%' and lower(" + g + ") not in ('indet', 'undet'))";
    }

    /** The same rule in Java, for a genus value (taxon.genus or specimen.genus). */
    public static boolean isIdentifiedToGenus(String genus) {
        if (genus == null) return true;
        String g = genus.trim().toLowerCase(java.util.Locale.ROOT);
        if (g.startsWith("(")) return false;
        for (String u : UNIDENTIFIED_GENERA) if (u.equals(g)) return false;
        return true;
    }

    /**
     * Ants only (alias t). Brian Fisher, 2026-10-21: specimens of other families stay in AntWeb but are not counted
     * on place pages; every number of the place page (totals, pies, Specimens, images) counts Formicidae only.
     */
    public static final String ANTS = " and t.family = 'formicidae'";

    /**
     * Brian Fisher, 2026-10-22 (docs/numbers.md Q7): ants only everywhere. The statsPage tables (Global Data, Bioregion
     * Data, Museum Data) and the home page's "valid species + ssp." count valid Formicidae taxa only, as the place pages
     * do, and a species or subspecies must be identified to genus (identifiedToGenus). One rule for those pages, the home
     * page number written by StatisticsDb.populateStatistics, and the count check (NumberChecks stats.*).
     *
     * alias: the taxon table's alias; rank: subfamily, genus, species or subspecies; fossil: 0 (extant), 1 (fossil) or
     * null (both).
     */
    public static String validAntTaxonCondition(String alias, String rank, Integer fossil) {
        String a = alias + ".";
        String c = a + "status = 'valid' and " + a + "taxarank = '" + rank + "' and " + a + "family = 'formicidae'";
        if (fossil != null) c += " and " + a + "fossil = " + fossil;
        if ("species".equals(rank) || "subspecies".equals(rank)) c += " and " + identifiedToGenus(alias);
        return c;
    }

    /** The same rule for species and subspecies together (home and statsPage "valid species + ssp."; fossils included). */
    public static String validAntSpeciesCondition(String alias) {
        return "(" + validAntTaxonCondition(alias, "species", null) + " or " + validAntTaxonCondition(alias, "subspecies", null) + ")";
    }

    /** The same rule in Java (statsPage "valid species + ssp.", TaxonMgr). */
    public static boolean isValidAntSpecies(String status, String rank, String family, String genus) {
        return "valid".equals(status) && ("species".equals(rank) || "subspecies".equals(rank)) && "formicidae".equals(family)
            && isIdentifiedToGenus(genus);
    }

    /** Valid species and subspecies, not fossil (alias t). */
    public static final String VALID_CONDITION = "t.taxarank in ('species', 'subspecies') and t.status = 'valid' and t.fossil = 0" + ANTS;

    /** The projects that are not curated lists (they list the names themselves, not renamed ones: listSpecimensFrom). */
    public static final String[] UNCURATED_PROJECTS = {"worldants", "allantwebants"};

    /**
     * The list rows (alias l) of one rank that a place's Subfamilies, Genera or Species number counts (alias t: their
     * taxon): rankCondition, the same on every kind of place. From 2026-10-27 (docs/numbers.md B67) to 2026-10-30 a
     * curated project's Subfamilies counted its subfamily rows of every status; Brian Fisher, 2026-10-30: Subfamilies
     * count valid subfamilies only everywhere (countsAsSubfamily), so B67 is superseded and there is no exception.
     */
    public static String listedCondition(String type, String rank) {
        listTable(type);   // an unknown place type is still an error
        return rankCondition(rank);
    }

    /** One place (parameter: its key): the number of rows of that rank on its list. */
    public static String listedCountSql(String type, String rank) {
        return "select count(distinct l.taxon_name) from " + listTable(type) + " l join taxon t on t.taxon_name = l.taxon_name"
            + " where l." + listKey(type) + " = ? and " + listedCondition(type, rank);
    }

    /** All places of a type: place key, number of rows of that rank on its list (places with none are missing). */
    public static String listedCountsByPlaceSql(String type, String rank) {
        return "select l." + listKey(type) + ", count(distinct l.taxon_name) from " + listTable(type) + " l"
            + " join taxon t on t.taxon_name = l.taxon_name where " + listedCondition(type, rank) + " group by l." + listKey(type);
    }

    /** One place (parameter: its key): valid extant species and subspecies on its list. */
    public static String validSpeciesSql(String type) {
        return "select count(distinct l.taxon_name) from " + listTable(type) + " l join taxon t on t.taxon_name = l.taxon_name"
            + " where l." + listKey(type) + " = ? and " + VALID_CONDITION;
    }

    /** All places of a type: place key, valid extant species and subspecies on its list. */
    public static String validSpeciesByPlaceSql(String type) {
        return "select l." + listKey(type) + ", count(distinct l.taxon_name) from " + listTable(type) + " l"
            + " join taxon t on t.taxon_name = l.taxon_name where " + VALID_CONDITION + " group by l." + listKey(type);
    }

    /**
     * "Species per Subfamily" pie of one place (parameter: its key): subfamily, count, chart_color (null:
     * the subfamily's colour). The rows of the Species number, so the slices add up to Species.
     */
    public static String speciesPieSql(String type) {
        return "select " + SLICE + " subfamily, count(distinct l.taxon_name) count from " + listTable(type) + " l"
            + " join taxon t on t.taxon_name = l.taxon_name" + sliceJoin("t.subfamily")
            + " where l." + listKey(type) + " = ? and " + rankCondition(SPECIES)
            + " group by " + SLICE + " order by " + SLICE;
    }

    /** Label of the slice of everything that is not a subfamily in the taxon table (other families, typos, none). */
    public static final String OTHER = "Other";

    /** The slice of a row: the subfamily when it is a subfamily of the taxon table, else Other (alias sf). */
    static final String SLICE = "coalesce(sf.taxon_name, '" + OTHER + "')";

    static String sliceJoin(String subfamilyColumn) {
        return " left join taxon sf on sf.taxon_name = " + subfamilyColumn + " and sf.taxarank = 'subfamily'";
    }

    /**
     * "Specimens per Subfamily" pie of one place (parameter: its key): subfamily, count. The specimen counts of the
     * species and subspecies rows of the list (as the Specimens number; museums and bioregions too since 2026-10-28,
     * before: the specimens of the museum or bioregion). A slice per subfamily of the taxon table, one Other slice for
     * the rest (2026-10-21: specimen.subfamily of other families or typos made one slice each, and the JSON outgrew its
     * column; see pieJson).
     */
    public static String specimenPieSql(String type) {
        return "select " + SLICE + " subfamily, sum(l.specimen_count) count from " + listTable(type) + " l"
            + " join taxon t on t.taxon_name = l.taxon_name" + sliceJoin("t.subfamily")
            + " where l." + listKey(type) + " = ? and t.taxarank in ('species', 'subspecies')" + ANTS
            + " group by " + SLICE + " having sum(l.specimen_count) > 0 order by " + SLICE;
    }

    // ---------------------------------------------------------------------------------------------- synonyms

    /*
     * Brian Fisher, 2026-10-22: a specimen filed under a junior synonym (or another name AntCat no longer uses, with a
     * current valid name) belongs to the valid species. The nightly run renames such rows of a place's list to the
     * valid name (TaxonSetDb.renameToValid, merging when the valid name is there already) before the list is counted,
     * and the specimen counts of museum, bioregion and project lists count those specimens under the valid name.
     * Until 2026-10-22 museum and bioregion lists were counted (and their totals written) before the rename, so the
     * museum AFRC showed 14 valid species rows that its Species number did not count (count check, staging).
     */

    /** A name (alias t, a taxon row) that list rows are renamed from: not valid, with a current valid name other than itself. */
    public static String renamedCondition(String t) {
        return "(" + t + ".status <> 'valid' and coalesce(" + t + ".current_valid_name, '') <> '' and " + t + ".current_valid_name <> " + t + ".taxon_name)";
    }

    /**
     * Whether the specimens of a synonym count under its valid name on this place's list: museums, bioregions and
     * projects, except worldants and allantwebants (they list every name as AntCat has it; allantwebants is the
     * "Global" column, all specimens filed under that very name; its Images follow the valid name since 2026-10-28,
     * allAntwebImagesSql). Geolocale lists are renamed before their counts
     * too (set 2), but their specimen counts are still by the exact name (docs/numbers.md).
     */
    public static boolean synonymsToValid(String type, String key) {
        if (MUSEUM.equals(type) || BIOREGION.equals(type)) return true;
        return PROJECT.equals(type) && !"worldants".equals(key) && !"allantwebants".equals(key);
    }

    /**
     * The specimens of each name on one place's list (columns taxonName, count), a specimen filed under a renamed name
     * (renamedCondition) counted under its current valid name when that is on the list, never under the old name.
     * keyLiteral: the place key as SQL (a quoted literal, or "?": then the statement takes the key twice, followed by
     * the parameters of specimenCriteria). specimenCriteria: more conditions on the specimen (alias s), starting with
     * " and", or "".
     */
    public static String specimensByListNameSql(String type, String keyLiteral, String specimenCriteria) {
        return "select m.list_name taxonName, count(*) count" + listSpecimensFrom(type, " = " + keyLiteral, "")
            + specimenCriteria
            + " group by m.list_name";
    }

    /**
     * The specimens counted under the names of a list, a specimen filed under a renamed name (renamedCondition) under its
     * current valid name when that is on the list, never under the old name: " from ... where (...)" with the aliases m
     * (m.pk the place key, m.list_name the list row's name, m.src the name the specimen is filed under) and s (specimen),
     * sx (the specimen's taxon row, may be null). keyCondition: a condition on the place key such as " = ?" or " = 'X'"
     * (then the statement takes the key twice), or null for every place. extraJoin: more joins, placed before the where
     * (for example the images of the specimen). A project's list (all projects at once): worldants and allantwebants count
     * the very name only (synonymsToValid), so they get no renamed rows. One rule (Brian Fisher, 2026-10-22; images
     * since 2026-10-28) for the specimens and images of museum, bioregion and curated project lists, the place's images
     * (PlaceImageCountDb) and the count check.
     */
    public static String listSpecimensFrom(String type, String keyCondition, String extraJoin) {
        String l = listTable(type);
        String k = listKey(type);
        String key = keyCondition == null ? "" : " and l." + k + keyCondition;
        String uncurated = PROJECT.equals(type) ? " and l." + k + " not in ('" + String.join("', '", UNCURATED_PROJECTS) + "')" : "";
        return " from (select l." + k + " pk, l.taxon_name list_name, l.taxon_name src from " + l + " l where 1 = 1" + key
            + " union all select l." + k + " pk, l.taxon_name list_name, st.taxon_name src from " + l + " l"
            + " join taxon st on st.current_valid_name = l.taxon_name"
            + " where " + renamedCondition("st") + key + uncurated
            + ") m join specimen s on s.taxon_name = m.src"
            + " left join taxon sx on sx.taxon_name = s.taxon_name"
            + (extraJoin == null ? "" : extraJoin)
            + " where (m.src <> m.list_name or coalesce(" + renamedCondition("sx") + ", false) = false"
            + (PROJECT.equals(type) ? " or m.pk in ('" + String.join("', '", UNCURATED_PROJECTS) + "')" : "") + ")";
    }

    /**
     * The Images column of one museum, bioregion or curated project list (nightly set 4, Countable.getTaxonImageCountQuery):
     * columns taxonName, family, subfamily, genus, species, imageSum (images of the ant). Brian Fisher, 2026-10-28: images
     * follow the Specimens rule, so the images of a specimen filed under a synonym count under the valid name (Carebara
     * polita, a synonym of C. diversa: its 6 images count for C. diversa in the Afrotropical list). keyLiteral as in
     * specimensByListNameSql; specimenCriteria: the place's own conditions on the specimen (alias s).
     */
    public static String imagesByListNameSql(String type, String keyLiteral, String specimenCriteria) {
        return "select m.list_name taxonName, s.family family, s.subfamily subfamily, s.genus genus, s.species species, count(i.id) imageSum"
            + listSpecimensFrom(type, " = " + keyLiteral, StatisticsDb.ANT_IMAGES_OF_SPECIMEN)
            + specimenCriteria
            + " group by m.list_name, s.family, s.subfamily, s.genus, s.species"
            + " order by s.family, s.subfamily, s.genus, m.list_name";
    }

    /**
     * The Images column of the AntWeb-wide list (allantwebants, the first number of every list's Images cell,
     * ImagesAnywhere.imagesColumn): columns taxonName, family, subfamily, genus, species, imageSum (images of the ant).
     * Brian Fisher, 2026-10-28 (decision 3, docs/numbers.md row 44): the images of a specimen filed under a name AntCat
     * no longer uses (renamedCondition: a synonym, obsolete combination etc. with a current valid name) count under its
     * valid name, as on the museum, bioregion and curated project lists. allantwebants lists the counted statuses only
     * (ProjTaxonDb.regenerateAllAntweb), so those specimens were not counted anywhere on it, and a place's row could
     * show more than the AntWeb-wide one (staging run 40795139086: floridakeysants Camponotus inaequalis 32,
     * allantwebants 29). Each specimen counts once: under its own name when that is on the list (as before), else
     * under its current valid name when that is on the list. CountDb.imageCountCrawl adds the rows up by the taxon tree.
     */
    public static String allAntwebImagesSql(String projectLiteral) {
        String cols = " s.family family, s.subfamily subfamily, s.genus genus, s.species species, count(i.id) imageSum";
        return "select s.taxon_name taxonName," + cols
            + " from specimen s join proj_taxon pt on s.taxon_name = pt.taxon_name"
            + StatisticsDb.ANT_IMAGES_OF_SPECIMEN
            + " where pt.project_name = " + projectLiteral
            + " group by s.taxon_name, s.family, s.subfamily, s.genus, s.species"
            + " union all"
            + " select pt.taxon_name taxonName," + cols
            + " from specimen s join taxon sx on sx.taxon_name = s.taxon_name"
            + " join proj_taxon pt on pt.taxon_name = sx.current_valid_name and pt.project_name = " + projectLiteral
            + StatisticsDb.ANT_IMAGES_OF_SPECIMEN
            + " where " + renamedCondition("sx")
            + " and not exists (select 1 from proj_taxon own where own.project_name = " + projectLiteral
            + " and own.taxon_name = s.taxon_name)"
            + " group by pt.taxon_name, s.family, s.subfamily, s.genus, s.species"
            + " order by family, subfamily, genus, taxonName";
    }

    /**
     * The Specimens number of one place (parameter: its key): the sum of the specimen counts of the species and subspecies
     * rows of its list, ants. Every kind of place since 2026-10-28 (Brian Fisher: museum and bioregion "Specimens" = the
     * sum of their list rows, like countries); museums and bioregions counted every ant specimen of the museum or
     * bioregion before (any status, names not on the list too).
     */
    public static String listedSpecimensSql(String type) {
        return "select coalesce(sum(l.specimen_count), 0) from " + listTable(type) + " l join taxon t on t.taxon_name = l.taxon_name"
            + " where l." + listKey(type) + " = ? and t.taxarank in ('species', 'subspecies')" + ANTS;
    }

    /** Writes the Specimens number of one place (listedSpecimensSql). */
    public static int writeListedSpecimens(Connection connection, String type, String key) throws SQLException {
        int n = count(connection, listedSpecimensSql(type), key);
        try (PreparedStatement stmt = connection.prepareStatement("update " + placeTable(type)
                + " set specimen_count = ? where " + placeKey(type) + " = ?")) {
            stmt.setInt(1, n);
            stmt.setString(2, key);
            stmt.executeUpdate();
        }
        return n;
    }

    // ---------------------------------------------------------------------------------------------- genus only

    /**
     * Brian Fisher, 2026-10-28: a genus known at a place only from specimens identified to genus shows "No species" and
     * "N specimens identified to genus only" on the genus list; the species list has no row for those names and says under
     * the list how many such specimens there are, by genus. One place (parameter: its key): columns genus (the list row's
     * parent, the genus taxon name), taxon_name, specimen_count, subfamily, genus_part, species, subspecies, taxarank: the species and subspecies rows of the list whose names are
     * identified to genus only (GENUS_ONLY_STATUSES, identified to genus), with their specimen counts.
     */
    public static String genusOnlySql(String type) {
        return "select t.parent_taxon_name genus, l.taxon_name, l.specimen_count, t.subfamily, t.genus genus_part, t.species, t.subspecies, t.taxarank"
            + " from " + listTable(type) + " l"
            + " join taxon t on t.taxon_name = l.taxon_name"
            + " where l." + listKey(type) + " = ? and t.taxarank in ('species', 'subspecies') and t.status in " + GENUS_ONLY_STATUSES
            + ANTS + " and " + identifiedToGenus("t") + " and l.specimen_count > 0"
            + " order by t.genus, t.parent_taxon_name, l.taxon_name";   // by genus name: "Lioponera (1), Pheidole (2)"
    }

    /** A place key as a quoted SQL literal. */
    public static String literal(String key) {
        return "'" + (key == null ? "" : key.replace("\\", "\\\\").replace("'", "''")) + "'";
    }

    /** Runs a one-number query with one parameter (the place key). */
    public static int count(Connection connection, String sql, String key) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, key);
            try (ResultSet r = stmt.executeQuery()) {
                return r.next() ? r.getInt(1) : 0;
            }
        }
    }

    /** The place's Subfamilies, Genera and Species numbers, counted from its list. */
    public static int[] listedCounts(Connection connection, String type, String key) throws SQLException {
        return new int[] {
            count(connection, listedCountSql(type, SUBFAMILY), key),
            count(connection, listedCountSql(type, GENUS), key),
            count(connection, listedCountSql(type, SPECIES), key)
        };
    }

    /** Colour of a slice whose subfamily has no colour of its own (the slice is kept, so the pie still adds up). */
    public static final String NO_COLOR = "#999999";

    private static final org.apache.commons.logging.Log s_log = org.apache.commons.logging.LogFactory.getLog(PlaceTotals.class);

    /** One slice: label (subfamily or Other), count. */
    static final class Slice {
        String subfamily;
        long count;
        Slice(String subfamily, long count) { this.subfamily = subfamily; this.count = count; }
    }

    static java.util.List<Slice> slices(Connection connection, String sql, String key) throws SQLException {
        java.util.List<Slice> list = new java.util.ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, key);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) list.add(new Slice(r.getString(1), r.getLong(2)));
            }
        }
        return list;
    }

    static String json(java.util.List<Slice> slices) {
        StringBuilder json = new StringBuilder();
        int i = 0;
        for (Slice sl : slices) {
            String color = null;
            if (sl.subfamily != null && !OTHER.equals(sl.subfamily)) {
                try {
                    org.calacademy.antweb.Subfamily s = org.calacademy.antweb.util.TaxonMgr.getSubfamily(sl.subfamily);
                    if (s != null) color = s.getChartColor();
                } catch (RuntimeException e) {
                    // taxa not loaded (tests): the slice keeps NO_COLOR
                }
            }
            if (color == null) color = NO_COLOR;
            if (i++ > 0) json.append(",");
            String label = sl.subfamily == null || OTHER.equals(sl.subfamily) ? OTHER : org.calacademy.antweb.Formatter.initCap(sl.subfamily);
            json.append(org.calacademy.antweb.util.HttpUtil.getJsonElement(i, label, (int) sl.count, color));
        }
        return json.toString();
    }

    /**
     * The pie's JSON, at most maxLength characters (the column's size; 0 = no limit): if longer, the smallest
     * slices are merged into Other until it fits, so the slices still add up. Same format as before
     * (HttpUtil.getJsonElement), read by overview-body.jsp.
     */
    public static String fit(java.util.List<Slice> in, int maxLength) {
        java.util.List<Slice> slices = new java.util.ArrayList<>();
        Slice other = null;
        for (Slice s : in) {
            if (OTHER.equals(s.subfamily) || s.subfamily == null) {
                if (other == null) other = new Slice(OTHER, 0);
                other.count += s.count;
            } else {
                slices.add(new Slice(s.subfamily, s.count));
            }
        }
        while (true) {
            java.util.List<Slice> all = new java.util.ArrayList<>(slices);
            if (other != null) all.add(other);
            String json = json(all);
            if (maxLength <= 0 || json.length() <= maxLength) return json;
            if (slices.isEmpty()) return "";       // not even one slice fits: no chart
            Slice smallest = slices.get(0);
            for (Slice s : slices) if (s.count < smallest.count) smallest = s;
            slices.remove(smallest);
            if (other == null) other = new Slice(OTHER, 0);
            other.count += smallest.count;
        }
    }

    /** The JSON of one pie (one place, parameter: its key), unlimited length. */
    public static String pieJson(Connection connection, String sql, String key) throws SQLException {
        return fit(slices(connection, sql, key), 0);
    }

    private static final java.util.Map<String, Integer> s_columnLength = new java.util.concurrent.ConcurrentHashMap<>();

    /** Characters the column holds (information_schema), 0 when unknown or very large. */
    static int columnLength(Connection connection, String table, String column) {
        return s_columnLength.computeIfAbsent(table + "." + column, k -> {
            try (PreparedStatement stmt = connection.prepareStatement("select character_maximum_length from information_schema.columns"
                    + " where table_schema = database() and table_name = ? and column_name = ?")) {
                stmt.setString(1, table);
                stmt.setString(2, column);
                try (ResultSet r = stmt.executeQuery()) {
                    if (!r.next()) return 0;
                    long n = r.getLong(1);
                    return n <= 0 || n > 1000000 ? 0 : (int) n;
                }
            } catch (SQLException e) {
                return 0;
            }
        });
    }

    /** Forgets the column sizes read (tests that change the schema). */
    public static void clearColumnLengths() { s_columnLength.clear(); }

    /**
     * Writes both pies of one place (nightly run, Recalculate, museum recount after an upload). A chart never
     * stops the work around it (2026-10-21: an upload rolled back with "Data too long for column
     * specimen_subfamily_dist_json"): the JSON is fitted to the column, and any error is logged and the old
     * charts are kept.
     */
    public static void writePies(Connection connection, String type, String key) {
        try {
            String table = placeTable(type);
            String species = fit(slices(connection, speciesPieSql(type), key), columnLength(connection, table, "taxon_subfamily_dist_json"));
            String specimens = fit(slices(connection, specimenPieSql(type), key), columnLength(connection, table, "specimen_subfamily_dist_json"));
            try (PreparedStatement stmt = connection.prepareStatement("update " + table
                    + " set taxon_subfamily_dist_json = ?, specimen_subfamily_dist_json = ? where " + placeKey(type) + " = ?")) {
                stmt.setString(1, species);
                stmt.setString(2, specimens);
                stmt.setString(3, key);
                stmt.executeUpdate();
            }
        } catch (SQLException | RuntimeException e) {
            s_log.error("writePies() " + type + " " + key + ": charts not written, the old ones are kept. e:" + e);
        }
    }

    /** Writes the Subfamilies, Genera and Species numbers of one place, counted from its list. */
    public static int[] writeListedCounts(Connection connection, String type, String key) throws SQLException {
        int[] counts = listedCounts(connection, type, key);
        try (PreparedStatement stmt = connection.prepareStatement("update " + placeTable(type)
                + " set subfamily_count = ?, genus_count = ?, species_count = ? where " + placeKey(type) + " = ?")) {
            stmt.setInt(1, counts[0]);
            stmt.setInt(2, counts[1]);
            stmt.setInt(3, counts[2]);
            stmt.setString(4, key);
            stmt.executeUpdate();
        }
        return counts;
    }

    /** Writes the Valid species number of one place, counted from its list. */
    public static int writeValidSpecies(Connection connection, String type, String key) throws SQLException {
        int n = count(connection, validSpeciesSql(type), key);
        try (PreparedStatement stmt = connection.prepareStatement("update " + placeTable(type)
                + " set valid_species_count = ? where " + placeKey(type) + " = ?")) {
            stmt.setInt(1, n);
            stmt.setString(2, key);
            stmt.executeUpdate();
        }
        return n;
    }

    /**
     * Endemic and Introduced numbers of every geolocale or bioregion from the flags of its list
     * (is_endemic, is_introduced), the rows endemic.do and introduced.do list (2026-10-21). field: endemic
     * or introduced. Before, the endemic loop added the first species of the next place to the previous
     * one (first place +1, last place -1), and regions and subregions got flags but no number.
     */
    public static String countsFromFlagsSql(String type, String field) {
        if (!"endemic".equals(field) && !"introduced".equals(field)) throw new IllegalArgumentException(field);
        if (!GEOLOCALE.equals(type) && !BIOREGION.equals(type)) throw new IllegalArgumentException(type);
        return "update " + placeTable(type) + " p set p." + field + "_species_count = (select count(*) from " + listTable(type) + " l"
            + " where l." + listKey(type) + " = p." + placeKey(type) + " and " + flaggedCondition(field) + ")";
    }

    /**
     * The list rows (alias l) flagged endemic or introduced that the lists and the numbers count. Brian Fisher,
     * 2026-10-27 (docs/numbers.md B70): endemic.do, introduced.do and the Endemic and Introduced numbers follow the
     * status rule of the counts: a flagged name counts when it has a counted status (COUNTABLE), so never a synonym
     * (nor another name AntCat no longer uses). Until then the Introduced list and both numbers took every flagged row,
     * and the Endemic list every status. One rule for the lists, the numbers (countsFromFlagsSql) and the count check
     * (CountInvariants place.totals.endemic and place.totals.introduced). Endemic since 2026-10-28: valid names only
     * (ENDEMIC_STATUSES, Brian Fisher); Introduced keeps the counted statuses.
     */
    public static String flaggedCondition(String field) {
        if (!"endemic".equals(field) && !"introduced".equals(field)) throw new IllegalArgumentException(field);
        String statuses = "endemic".equals(field) ? ENDEMIC_STATUSES : COUNTABLE;
        return "l.is_" + field + " = 1 and exists (select 1 from taxon ft where ft.taxon_name = l.taxon_name and ft.status in " + statuses + ")";
    }

    /** endemic.do, introduced.do: the names flagged on one place's list (parameter: its key), by genus, species, subspecies. */
    public static String flaggedNamesSql(String type, String field) {
        if (!GEOLOCALE.equals(type) && !BIOREGION.equals(type)) throw new IllegalArgumentException(type);
        return "select l.taxon_name from " + listTable(type) + " l join taxon t on t.taxon_name = l.taxon_name"
            + " where l." + listKey(type) + " = ? and " + flaggedCondition(field) + " order by t.genus, t.species, t.subspecies";
    }

    /** The names of flaggedNamesSql for one place. */
    public static java.util.ArrayList<String> flaggedNames(Connection connection, String type, String key, String field) throws SQLException {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(flaggedNamesSql(type, field))) {
            if (GEOLOCALE.equals(type)) stmt.setInt(1, Integer.parseInt(key));
            else stmt.setString(1, key);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) names.add(r.getString(1));
            }
        }
        return names;
    }

    public static int writeCountsFromFlags(Connection connection, String type, String field) throws SQLException {
        try (java.sql.Statement stmt = connection.createStatement()) {
            return stmt.executeUpdate(countsFromFlagsSql(type, field));
        }
    }

    /** Sum of the "value" fields of a pie chart's JSON (as written by HttpUtil.getJsonElement). */
    public static int pieSum(String json) {
        if (json == null) return 0;
        int sum = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"value\":\\s*(-?\\d+)").matcher(json);
        while (m.find()) sum += Integer.parseInt(m.group(1));
        return sum;
    }
}
