package org.calacademy.antweb.util;

import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The named queries of the Query Manager (query.do), the Integrity Check, the Admin Alerts and the
 * "Relevant Queries" boxes of the manager pages.
 *
 * Every query (reviewed one by one on 2026-10-22 at Brian Fisher's request: "make sure they are working and
 * explained") has
 *   - a name (its address: query.do?name=...),
 *   - a plain-language explanation: what it lists, how to read it, what to do about what it finds. The first
 *     sentence is the one-line summary shown in the Query Manager list and the Relevant Queries boxes;
 *   - one column heading per column of its select (QueriesReviewTest checks the count);
 *   - read-only SQL (QueryManager.isReadOnly refuses anything else).
 * Queries only administrators may run, except the few QueryAction.PUBLIC_QUERIES and CURATOR_QUERIES.
 * The list of queries, one line each, is in docs/guides/manager-tools.md (Query Manager).
 *
 * Links: a query may build a link to an AntWeb page in SQL (link() below); the result page keeps only such
 * links (QueryManager.cell), every other value is shown as text.
 */
public abstract class Queries {

    private static final Log s_log = LogFactory.getLog(Queries.class);

    // ---------------- Lists of queries run together ----------------

    /** The Integrity Check (query.do?action=checkIntegrity): checks that should find nothing. */
    public static String[] getIntegrityNames() {
        String[] list = {
            "noSpeciesForSpecies"
          , "noGenusForGenus"
          , "noSubfamilyForSubfamily"
          , "emptyGenus"
          , "emptySubfamily"
          , "generaInMultipleSubfamilies"
          , "taxonNameWithDoubleQuote"
          , "taxonNameWithSingleQuote"
          , "adminLoginIdIsZero"
          // badParentTaxonName (moved from the removed Curate AntCat page, 2026-10-19) lists the taxa that
          // sourceWithBadParent only counted by source (sourceWithBadParent was removed 2026-10-22).
          , "badParentTaxonName"
          , "unrecognizedWithSynonymParent"
          , "geolocaleTaxaNotUsingCurrentValidName"
          , "validsWithCurrentValidName"
          , "missingCurrentValidName"
          , "checkSpecimenStatus"
          , "spaceInGenusName"
          , "invalidBioregion"
          , "quadrinomialsInSpeciesLists"
          , "nonAntsInSpeciesListsProject"
          , "nonAntsInSpeciesListsGeolocale"
          , "badSingleQuotes"
          , "geolocaleNameWithQuestionMarks"
          , "geolocaleTaxaWithoutTaxon"
          , "geolocaleTaxaMorphosWithNoSpecimen"
          , "homonymWithoutTaxon"
          , "projTaxaWithoutTaxonWithoutHomonym"
          , "projTaxaWithoutTaxonWithHomonym"
        };
        return list;
    }
    public static ArrayList<NamedQuery> getIntegrityQueries() {
        return Queries.getNamedQueryList(getIntegrityNames());
    }

    /** query.do?action=devQueries (QueryManager): checks whose hits are for the AntWeb developers. */
    public static String[] getDevIntegrityNames() {
        String[] list = {
            "emptySourceTaxonSets"
          , "noSpeciesForSpecies"
        };
        return list;
    }

    /** The Admin Alerts (AdminAlertMgr): checked on every server start; highest priority. */
    public static String[] getAdminCheckNames() {
        String[] list = {
            "noSpeciesForSpecies"
          , "geolocaleTaxaPresent"
        };
        return list;
    }
    public static ArrayList<NamedQuery> getAdminCheckQueries() {
        return Queries.getNamedQueryList(getAdminCheckNames());
    }

    /** query.do?action=curiousQueries: counts and lists for orientation. */
    public static String[] getCuriousNames() {
        String[] list = {
            "speciesEqualsSubspecies"
          , "specimenCountByTaxa"
          , "taxonCountBySubfamily"
          , "taxonCountByGenus"
          , "taxonCountBySpecies"
          , "speciesListHomonyms"
          , "introducedSpecimen"
          , "morphoProjTaxa"
          , "duplicatedGeolocaleNames"
          , "nonAnts"
          , "nonIntroducedAntsInMultipleBioregions"
          , "geolocaleTaxonDisputes"
          , "projectTaxonDisputes"
        };
        return list;
    }
    public static ArrayList<NamedQuery> getCuriousQueries() {
        return getNamedQueryList(getCuriousNames());
    }

    // For Geolocale Manager (Relevant Queries box).
    public static ArrayList<String> getGeolocaleQueries() {
      String[] list = {
          "invalidValidNamelessCountries"
        , "usedAndUnusedCoords"
        , "lowGeolocaleTaxonCount"
        , "unfinishedBioregions"
        , "nonvalidValidNames"
        , "nonLiveValidCountries"
        , "geolocaleTaxaFromInvalidTaxonName"
        , "geolocaleTaxaInMultipleBioregions"
        , "geolocaleTaxaWithoutTaxon"
      };
      return new ArrayList<>(Arrays.asList(list));
    }

    // For Login Manager (Relevant Queries box).
    public static ArrayList<String> getLoginQueries() {
      String[] list = {
          "uploadingCurators"
        , "curatorEmails"
      };
      return new ArrayList<>(Arrays.asList(list));
    }

    // For Group Manager (Relevant Queries box).
    public static ArrayList<String> getGroupQueries() {
      String[] list = {
          "specimenGroups"
      };
      return new ArrayList<>(Arrays.asList(list));
    }

    /** query.do?action=queryBattery&name=projectTaxonCounts (QueryManager.getBattery). */
    public static String[] getBatteryNames() {
        return new String[] {"projectTaxaCount", "projectTaxaCountByProject", "projectTaxaCountByProjectRank"};
    }

    // ---------------- Helpers ----------------

    private static String[] h(String... headings) {
        return headings;
    }

    /**
     * SQL that makes a link to an AntWeb page for each row: page is the address after the domain up to the
     * value (e.g. "/description.do?taxonName="), column the SQL column. Spaces in the value are written %20,
     * as QueryManager.cell keeps a link only when its address has no space.
     */
    static String link(String page, String column) {
        return "concat(\"<a href='" + AntwebProps.getDomainApp() + page + "\", replace(" + column + ", ' ', '%20'), \"'>\", "
            + column + ", \"</a>\")";
    }

    // Statuses that are not "in use": every AntCat status except valid. AntWeb's own statuses for names AntCat
    // does not have are morphotaxon (morphospecies), indetermined and unrecognized (Status.java).
    private static final String ANTWEB_OWN_STATUSES = "'morphotaxon', 'indetermined', 'unrecognized'";

// ---------------- Taxon queries ----------------

    private static ArrayList<NamedQuery> getTaxaNamedQueries() {
        ArrayList<NamedQuery> queries = new ArrayList<>();

        // Replaces brokenTaxaHierarchy (the same check without names containing a parenthesis) and badSpecies
        // (the same check without empty strings), both removed 2026-10-22.
        queries.add(new NamedQuery(
            "noSpeciesForSpecies"
          , "Species-rank names whose species part is empty. Such a name cannot be placed under its genus, so its"
            + " page and the counts above it are wrong. Normally none (it is also an Admin Alert). Fix: correct the"
            + " name at its source (Source column: the group's specimen upload file, or AntCat for worldants)."
          , h("Taxon Name", "Source", "Created")
          , "select taxon_name, source, created from taxon where taxarank = 'species' and (species is null or species = '')"
            + " order by source, taxon_name"
        ));

        queries.add(new NamedQuery(
            "geolocaleTaxaPresent"
          , "Admin Alert check that the country species lists are filled: shows 0 when there are at least 50,000"
            + " rows (one per name per country; there are normally far more), and \"records missing!\" when there"
            + " are fewer. Fewer means the lists were emptied and not rebuilt, so country pages show few or no"
            + " species. Fix: look at the last nightly run (compute.log, sets 2 and 3) and run it again."
          , h("Result")
          // The concat is because the Admin Alerts scan the result for "records missing".
          , "select if (count(*) < 50000, concat('records', ' missing!'), 0) isLow from geolocale_taxon gt, geolocale g"
            + " where gt.geolocale_id = g.id and g.georank = 'country'"
        ));

        queries.add(new NamedQuery(
            "nonAsciiTaxonName"
          , "Names that contain a character other than plain English letters, digits and punctuation (an accented"
            + " letter, a curly quote, a non-breaking space), usually pasted into an upload file. Such a name does"
            + " not match the same name typed plainly, so its specimens can end up under two names. Fix: correct"
            + " the name at its source (Source column)."
          , h("Taxon Name", "Source", "Created")
          , "select taxon_name, source, created from taxon where not hex(taxon_name) regexp '^([0-7][0-9A-F])*$'"
            + " order by source, taxon_name"
        ));

        // Was: AntCat synonyms with source 'worldants.txt' (AntCat names have source 'worldants') whose taxon name
        // is the subfamily joined to the current valid name; it found nothing. The current valid name is a full
        // taxon name (subfamily included).
        queries.add(new NamedQuery(
            "selfRefSynonyms"
          , "Synonyms whose current valid name is the name itself, so the taxon page sends readers back to the"
            + " same page. An error in the catalog. Fix: report it to AntCat (antcat.org); AntWeb takes the"
            + " correction with the next nightly import."
          , h("Taxon Name", "Source", "Current Valid Name")
          , "select taxon_name, source, current_valid_name from taxon where status = 'synonym' and current_valid_name = taxon_name"
            + " order by taxon_name"
        ));

        queries.add(new NamedQuery(
            "homonyms"
          , "Every name in the homonym table: AntCat names spelled exactly like another ant name but with another"
            + " author and year. AntWeb keeps one of each such pair as the taxon and the others here. For reference;"
            + " the Homonym Report on the Curate page shows the same with links."
          , h("Taxon Name", "Author Date")
          , "select taxon_name, author_date from homonym order by taxon_name"
        ));

        // Was: original combinations where concat(subfamily, current_valid_name) is not a name. The current valid
        // name already holds the subfamily, so it listed every original combination; and longCurrentValidNames
        // (the same, with three spaces in the name) was removed with it.
        queries.add(new NamedQuery(
            "invalidCurrentValid"
          , "Names whose current valid name (the name AntCat says to use instead) is not among AntWeb's names or"
            + " homonyms. The taxon page's link to the valid name leads nowhere, and the specimens filed under the"
            + " name cannot be moved to it. Fix: look the name up in AntCat; if AntCat is right, the next nightly"
            + " import should bring the valid name, otherwise report it to AntCat."
          , h("Taxon Name", "Status", "Current Valid Name", "Source")
          , "select t.taxon_name, t.status, t.current_valid_name, t.source from taxon t"
            + " where t.current_valid_name is not null and t.current_valid_name != '' and t.current_valid_name != t.taxon_name"
            + " and not exists (select 1 from taxon v where v.taxon_name = t.current_valid_name)"
            + " and not exists (select 1 from homonym h where h.taxon_name = t.current_valid_name)"
            + " order by t.taxon_name"
        ));

        queries.add(new NamedQuery(
            "noGenusForGenus"
          , "Genus-rank names whose genus part is empty. The genus page and every name under it are then"
            + " misplaced. Normally none. Fix: correct the name at its source (Source column)."
          , h("Taxon Name", "Source", "Created")
          , "select taxon_name, source, created from taxon where taxarank = 'genus' and (genus is null or genus = '')"
          ));

        queries.add(new NamedQuery(
            "noSubfamilyForSubfamily"
          , "Subfamily-rank names whose subfamily part is empty. Normally none. Fix: correct the name at its"
            + " source (Source column)."
          , h("Taxon Name", "Source", "Created")
          , "select taxon_name, source, created from taxon where taxarank = 'subfamily' and (subfamily is null or subfamily = '')"
          ));

        // Was: genus = '' only, and tribes (which have no genus) were listed.
        queries.add(new NamedQuery(
            "emptyGenus"
          , "Names below the genus (genera, species, subspecies) whose genus part is empty, so they do not show"
            + " under any genus. Normally none. Fix: correct the name at its source (Source column)."
          , h("Taxon Name", "Subfamily", "Genus", "Rank", "Source")
          , "select taxon_name, subfamily, genus, taxarank, source from taxon where (genus is null or genus = '')"
            + " and taxarank in ('genus', 'subgenus', 'species', 'subspecies')"
          ));

        // Was: statuses other than synonym and original combination (obsolete combinations of genera that moved
        // subfamily were listed); the detail query used another filter. Both now use the names in use.
        queries.add(new NamedQuery(
            "generaInMultipleSubfamilies"
          , "Genera whose names in use (valid names and AntWeb's own morphospecies, indeterminate and unrecognized"
            + " names) are filed under more than one subfamily. Usually a specimen upload that still uses a genus's"
            + " old subfamily. Fix: open the detail query to see the names, then correct the subfamily in the"
            + " upload file (Source column)."
          , h("Genus", "Subfamily Count", "Source", "Statuses")
          , "select genus, count(distinct subfamily), source, group_concat(distinct status) from taxon"
            + " where taxarank in ('genus', 'species', 'subspecies') and status in ('valid', " + ANTWEB_OWN_STATUSES + ")"
            + " group by genus, source having count(distinct subfamily) > 1"
          , "generaInMultipleSubfamiliesDetail"
          ));

        queries.add(new NamedQuery(
          "generaInMultipleSubfamiliesDetail"
          , "Detail of generaInMultipleSubfamilies: every name in use of the genera filed under more than one"
            + " subfamily, with the subfamily each is under and where it came from. The names under the wrong"
            + " subfamily are the ones to correct at their source."
          , h("Taxon Name", "Subfamily", "Genus", "Status", "Source", "Created", "Insert Method")
          , "select taxon_name, subfamily, genus, status, source, created, insert_method from taxon"
            + " where taxarank in ('genus', 'species', 'subspecies') and status in ('valid', " + ANTWEB_OWN_STATUSES + ")"
            + " and genus in (select genus from taxon where taxarank in ('genus', 'species', 'subspecies')"
            + " and status in ('valid', " + ANTWEB_OWN_STATUSES + ") group by genus having count(distinct subfamily) > 1)"
            + " order by genus, subfamily, taxon_name"
        ));

        queries.add(new NamedQuery(
            "emptySubfamily"
          , "Names that have a genus but no subfamily, so they are missing from the subfamily's pages and"
            + " counts. Normally none. Fix: correct the name at its source (Source column)."
          , h("Taxon Name", "Rank", "Subfamily", "Genus", "Source", "Created")
          , "select taxon_name, taxarank, subfamily, genus, source, created from taxon where (subfamily is null or subfamily = '')"
            + " and (genus is not null and genus != '') order by source, taxon_name"
          ));

        queries.add(new NamedQuery(
            "taxonNameWithDoubleQuote"
          , "Names that contain a double quote (\"), which breaks links and searches. Fix: correct the name at"
            + " its source (Source column)."
          , h("Taxon Name", "Rank", "Status", "Type", "Source", "Insert Method", "Created")
          , "select taxon_name, taxarank, status, type, source, insert_method, created from taxon where taxon_name like '%\"%'"
          ));

        queries.add(new NamedQuery(
            "taxonNameWithSingleQuote"
          , "Names that contain an apostrophe ('), which breaks links and searches. Fix: correct the name at its"
            + " source (Source column)."
          , h("Taxon Name", "Rank", "Status", "Type", "Source", "Insert Method", "Created")
          , "select taxon_name, taxarank, status, type, source, insert_method, created from taxon where taxon_name like '%''%'"
          ));

        queries.add(new NamedQuery(
            "validsWithCurrentValidName"
          , "Valid names that also have a current valid name. A valid name is its own current valid name, so the"
            + " field should be empty; pages may send readers to the other name. Fix: report it to AntCat if the"
            + " name is from AntCat (Source worldants); otherwise correct the source."
          , h("Taxon Name", "Status", "Rank", "Source", "Current Valid Name")
          , "select taxon_name, status, taxarank, source, current_valid_name from taxon where status = 'valid'"
            + " and current_valid_name is not null and current_valid_name != ''"
          ));

        queries.add(new NamedQuery(
            "missingCurrentValidName"
          , "Synonyms, combinations and unavailable names without a current valid name. AntWeb cannot say which"
            + " name to use instead, and cannot move their specimens to it. Fix: report the name to AntCat (Source"
            + " worldants), which should give each such name a current valid name."
          , h("Taxon Name", "Current Valid Name", "Status", "Created", "Source", "Line Num", "Rank")
          , "select taxon_name, current_valid_name, status, created, source, line_num, taxarank from taxon"
            + " where (current_valid_name is null or current_valid_name = '')"
            + " and status in ('unavailable uncategorized', 'original combination', 'unavailable misspelling', 'obsolete combination', 'synonym')"
          ));

        queries.add(new NamedQuery(
            "spaceInGenusName"
          , "Names whose genus part contains a space (for example \"pheidole x\" typed in the genus column of an"
            + " upload). Such a name makes a genus that does not exist. Fix: correct the genus in the upload file"
            + " (Source column)."
          , h("Taxon Name", "Genus", "Source")
          , "select taxon_name, genus, source from taxon where genus like '% %'"
          ));

        queries.add(new NamedQuery(
            "unrecognizedWithSynonymParent"
          , "Names that AntCat does not recognize (they came from specimen data) whose genus or species is a synonym in AntCat:"
            + " the specimens were identified under an old name. Fix: change the identification to the current valid name"
            + " in TaxonWorks (CAS) or in the curator's upload file; if the name is right, it is missing from AntCat."
          , h("Taxon Name", "Source")
          , "select taxon_name, source from taxon where status = 'unrecognized'"
            + " and parent_taxon_name in (select taxon_name from taxon where status = 'synonym')"
          ));

        // Was: taxon_name not in (select taxon_name from specimen), which finds nothing as soon as one specimen has
        // no taxon name.
        queries.add(new NamedQuery(
            "unrecognizedTaxaWithoutSpecimen"
          , "Unrecognized names from specimen uploads that no specimen uses any more (the specimens were"
            + " re-identified or deleted). The nightly run deletes such names; rows here mean it has not run since"
            + " the upload that left them. Nothing to fix by hand."
          , h("Source", "Created", "Taxon Name")
          , "select t.source, t.created, t.taxon_name from taxon t where t.source like 'specimen%' and t.status = 'unrecognized'"
            + " and not exists (select 1 from specimen s where s.taxon_name = t.taxon_name) order by t.source, t.created"
          ));

        queries.add(new NamedQuery(
            "speciesEqualsSubspecies"
          , "Pairs of names in the same subfamily and genus where the second name's species part is in"
            + " parentheses (an old way of writing a subgenus) and its subspecies part is the first name's species."
            + " Both probably mean the same species, written two ways. Fix: correct the second name at its source."
          , h("Taxon 1 Name", "Subfamily", "Genus", "Species", "Subspecies",
              "Taxon 2 Name", "Subfamily", "Genus", "Species", "Subspecies")
          , "select t1.taxon_name, t1.subfamily, t1.genus, t1.species, t1.subspecies, t2.taxon_name, t2.subfamily, t2.genus,"
            + " t2.species, t2.subspecies from taxon t1, taxon t2 where t1.species = t2.subspecies and t2.species like '(%'"
            + " and t1.subfamily = t2.subfamily and t1.genus = t2.genus"
          ));

        queries.add(new NamedQuery(
            "taxonCountBySubfamily"
          , "The 8 subfamilies with the most names in AntWeb (all ranks and statuses: valid names, synonyms,"
            + " morphospecies...). For orientation only."
          , h("Subfamily", "Names")
          , "select subfamily, count(*) from taxon group by subfamily order by count(*) desc limit 8"
          ));

        queries.add(new NamedQuery(
            "taxonCountByGenus"
          , "The 8 genera with the most names in AntWeb (all ranks and statuses). For orientation only."
          , h("Genus", "Names")
          , "select genus, count(*) from taxon group by genus order by count(*) desc limit 8"
          ));

        queries.add(new NamedQuery(
            "taxonCountBySpecies"
          , "The 8 species epithets used by the most names, across all genera (the empty one counts the names"
            + " above species rank). For orientation only."
          , h("Species Epithet", "Names")
          , "select species, count(*) from taxon group by species order by count(*) desc limit 8"
          ));

        queries.add(new NamedQuery(
           "indeterminedAndQuestionableMorphoTaxa"
          , "Indeterminate names (status indetermined) and morphospecies whose name contains a parenthesis. For"
            + " review: they hold specimens not yet identified to species. Nothing to fix unless a name is a typo."
          , h("Taxon Name", "Rank", "Status", "Source", "Created")
          , "select taxon_name, taxarank, status, source, created from taxon where status = 'indetermined'"
            + " or (status = 'morphotaxon' and taxon_name like '%(%') order by status desc, source, taxon_name"
        ));

        queries.add(new NamedQuery(
            "badParentTaxonName"
          , "Names whose parent (the genus of a species, the species of a subspecies) is not among AntWeb's names,"
            + " usually a misspelled or outdated genus or species in specimen data. Fix: correct the identification in"
            + " TaxonWorks (CAS) or in the curator's upload file; if the parent name is correct, check it in AntCat."
            + " The detail query adds each name's specimen count and groups."
          , h("Taxon Name", "Parent Taxon Name", "Source", "Status", "Current Valid Name", "Created")
          , "select t.taxon_name, t.parent_taxon_name, t.source, t.status, t.current_valid_name, t.created from taxon t"
            + " where t.family = 'formicidae' and t.taxarank != 'family'"
            + " and t.status not in ('unavailable misspelling', 'unavailable uncategorized', 'original combination')"
            + " and not exists (select 1 from taxon p where p.taxon_name = t.parent_taxon_name) order by t.created"
          , "badParentTaxonNameSpecimens"
        ));

        // Replaces sourceWithBadParent and its two detail queries (2026-10-22): the first two listed the same names
        // as badParentTaxonName; the second detail query had 3 headings for 9 columns.
        queries.add(new NamedQuery(
            "badParentTaxonNameSpecimens"
          , "Detail of badParentTaxonName: the same names with the number of specimens filed under each and the"
            + " groups that uploaded them, so each group can be asked to correct its identifications. Names with"
            + " 0 specimens disappear with the nightly run."
          , h("Taxon Name", "Parent Taxon Name", "Source", "Specimens", "Groups")
          , "select t.taxon_name, t.parent_taxon_name, t.source"
            + ", (select count(*) from specimen s where s.taxon_name = t.taxon_name)"
            + ", (select group_concat(distinct g.name) from specimen s, ant_group g where s.taxon_name = t.taxon_name and s.access_group = g.id)"
            + " from taxon t where t.family = 'formicidae' and t.taxarank != 'family'"
            + " and t.status not in ('unavailable misspelling', 'unavailable uncategorized', 'original combination')"
            + " and not exists (select 1 from taxon p where p.taxon_name = t.parent_taxon_name) order by t.source, t.taxon_name"
        ));

        queries.add(new NamedQuery(
           "taxaFromOldSpecimenLists"
          , "Names that came from the species list of a project that no longer exists (source ending in \"ants\" or"
            + " \"speciesList.txt\"), with the number of specimens filed under each. Names with 0 specimens are"
            + " left-overs; names with specimens are in use and stay."
          , h("Taxon Name", "Rank", "Status", "Source", "Created", "Specimens")
          , "select t.taxon_name, t.taxarank, t.status, t.source, t.created, (select count(*) from specimen s where s.taxon_name = t.taxon_name) count"
            + " from taxon t where (t.source like '%ants' or t.source like '%speciesList.txt')"
            + " and t.source not in (select project_name from project) order by count desc"
        ));

        return queries;
    }

// ---------------- Homonym queries ----------------

    private static ArrayList<NamedQuery> getHomonymNamedQueries() {
        ArrayList<NamedQuery> queries = new ArrayList<>();

        queries.add(new NamedQuery(
           "homonymWithoutTaxon"
          , "Homonyms whose name is not in the taxon table. Each homonym belongs next to a taxon of the same"
            + " name; without it the homonym's page and the Homonym Report break. The nightly AntCat import deletes"
            + " these; rows here after a nightly run are for the developers."
          , h("Taxon Name")
          , "select " + link("/description.do?taxonName=", "h.taxon_name")
            + " from homonym h where not exists (select 1 from taxon t where t.taxon_name = h.taxon_name)"
        ));
        return queries;
    }

// ---------------- Specimen queries ----------------

    private static ArrayList<NamedQuery> getSpecimenNamedQueries() {
        ArrayList<NamedQuery> queries = new ArrayList<>();

        // Was: select distinct ... order by g.name, sp.taxon_name: an SQL error on MySQL 8 (ORDER BY columns not
        // in the DISTINCT select list), and a link to http://antweb.org.
        queries.add(new NamedQuery(
              "notValidTaxaFromSpecimenList"
            , "Specimen records filed under a name AntCat does not consider valid (synonym, homonym, unavailable"
            + " name...), per group, with the name to use instead and the number of specimens. The nightly name"
            + " update moves specimens to the current valid name; what is left here it could not move (no current"
            + " valid name, or a name shared by two AntCat taxa: see the Name updates page). Morphospecies,"
            + " indeterminate and unrecognized names are not listed. Fix: the group corrects the identification."
            , h("Group", "Taxon Name", "Status", "Current Valid Name", "Specimens")
            , "select g.abbrev, " + link("/description.do?taxonName=", "t.taxon_name") + ", t.status, t.current_valid_name, count(*)"
            + " from specimen sp, taxon t, ant_group g where sp.taxon_name = t.taxon_name and sp.access_group = g.id"
            + " and t.family = 'formicidae' and t.status not in ('valid', " + ANTWEB_OWN_STATUSES + ")"
            + " group by g.abbrev, t.taxon_name, t.status, t.current_valid_name order by g.abbrev, t.taxon_name"
        ));

        queries.add(new NamedQuery(
          "specimenCountByOwner"
        , "Specimen records per \"owned by\" value (the institution that owns the specimen, as written in the"
          + " upload file), largest first. Different spellings of one institution show as separate rows."
        , h("Specimens", "Owned By")
        , "select count(*) theCount, s.ownedby from specimen s group by s.ownedby order by theCount desc"
        ));

        queries.add(new NamedQuery(
            "checkSpecimenStatus"
          , "Specimens with no status (the status of their name, set at upload). They are left out of the"
            + " species and specimen counts of place pages. Normally none. Fix: the next upload of the group sets"
            + " the status; if rows stay after an upload, it is for the developers. At most 5,000 rows."
          , h("Code", "Taxon Name", "Group", "Created")
          , "select s.code, s.taxon_name, g.name, s.created from specimen s left join ant_group g on g.id = s.access_group"
            + " where s.status is null order by g.name, s.code limit 5000"
          ));

        // Replaces oldBadBioregionsInSpecimen (the same check against a list of names written into the query).
        queries.add(new NamedQuery(
            "invalidBioregion"
          , "Bioregion names in specimen data that are not one of AntWeb's bioregions (bioregion table), with the"
            + " number of specimens and the groups that uploaded them. These specimens are missing from the"
            + " bioregion pages and counts. Fix: the bioregion is derived from the country at upload, so correct the"
            + " country (or the bioregion column) in the group's upload file, or the country's bioregion in the"
            + " Geolocale Manager."
          , h("Bioregion", "Specimens", "Groups")
          , "select s.bioregion, count(*), group_concat(distinct g.name) from specimen s left join ant_group g on g.id = s.access_group"
            + " where s.bioregion is not null and s.bioregion not in (select name from bioregion) group by s.bioregion"
          ));

        queries.add(new NamedQuery(
            "specimenCountByTaxa"
          , "The 8 names with the most specimen records. For orientation only."
          , h("Taxon Name", "Specimens")
          , "select taxon_name, count(*) from specimen group by taxon_name order by count(*) desc limit 8"
          ));

        queries.add(new NamedQuery(
            "specimenGroups"
          , "How many specimen records each upload group has on AntWeb: group number, name and count, largest"
            + " first. Use it to compare groups or to check a group's total after an upload."
          , h("Group ID", "Group Name", "Specimens")
          , "select access_group, (select name from ant_group where id = access_group) name, count(*) from specimen"
            + " group by access_group order by count(*) desc"
        ));

        queries.add(new NamedQuery(
            "introducedSpecimen"
          , "Specimen records flagged as introduced (a species found outside its native bioregions), by group,"
            + " bioregion and country. For review: a wrong flag usually means the species' native bioregions need"
            + " correcting in the Introduced Map Manager. At most 5,000 rows."
          , h("Group", "Bioregion", "Code", "Taxon Name", "Country")
          , "select g.name, s.bioregion, s.code, s.taxon_name, s.country from specimen s, ant_group g"
            + " where s.access_group = g.id and s.is_introduced = 1 order by g.name, s.bioregion, s.country limit 5000"
          ));

        // Was: only specimens with a label photo (image shot type l), and 5 headings for 6 columns.
        queries.add(new NamedQuery(
            "nonAnts"
          , "Specimen records that are not ants (family not Formicidae, or empty), with the group that uploaded"
            + " them. AntWeb holds a few on purpose (wasps, ant mimics); an empty family is usually an upload"
            + " mistake. Fix: correct the family in the group's upload file. At most 5,000 rows."
          , h("Code", "Family", "Subfamily", "Genus", "Species", "Group")
          , "select " + link("/specimen.do?code=", "s.code") + ", s.family, s.subfamily, s.genus, s.species, g.name"
            + " from specimen s left join ant_group g on g.id = s.access_group"
            + " where s.family is null or s.family != 'formicidae' order by s.family, s.code limit 5000"
        ));

        queries.add(new NamedQuery(
            "castes"
          , "Every combination of life stage/sex, caste and subcaste in specimen data, with the number of"
            + " specimens and the groups (by number) that use it, most common first. Caste is computed from the"
            + " life stage; a common life stage with an empty caste means a spelling AntWeb does not recognize."
          , h("Specimens", "Life Stage/Sex", "Caste", "Subcaste", "Group IDs")
          , "select count(*), concat(left(life_stage, 25), if(length(life_stage) > 25, '...', '')), caste, subcaste,"
            + " group_concat(distinct access_group) from specimen group by life_stage, caste, subcaste order by count(*) desc"
        ));

        queries.add(new NamedQuery(
            "nullCastes"
          , "Life stages of specimens that have no caste, with the number of specimens and the groups (by"
            + " number), most common first. The caste (worker, queen, male...) is computed from the life stage, so"
            + " these life stages are spellings AntWeb does not recognize. Fix: the group writes a recognized life"
            + " stage, or the developers add the spelling."
          , h("Specimens", "Life Stage/Sex", "Group IDs")
          , "select count(*), life_stage, group_concat(distinct access_group) from specimen where caste is null"
            + " group by life_stage order by count(*) desc"
        ));

        queries.add(new NamedQuery(
            "nullLifeStage"
          , "Imaged specimens with no life stage/sex, by group. Their caste is unknown, so they are missing from"
            + " the worker/queen/male counts and image choices. Fix: the group adds the life stage in its upload"
            + " file. At most 5,000 rows."
          , h("Code", "Group")
          , "select s.code, g.name from specimen s left join ant_group g on g.id = s.access_group"
            + " where s.life_stage is null and s.image_count > 0 order by g.name, s.code limit 5000"
        ));

        // Replaces multiBioregionSpecimenTaxa (the same list with fewer columns). Was: country != 'Port of Entry'
        // also left out specimens without a country.
        queries.add(new NamedQuery(
           "nonIntroducedAntsInMultipleBioregions"
          , "Names whose specimens come from more than one bioregion and that are not on the introduced species"
            + " list (project introducedants), with the bioregions, countries (first 140 characters) and groups."
            + " Either the species is introduced somewhere and the list lacks it, or a specimen is misidentified"
            + " or has the wrong country. Indeterminate names and Port of Entry specimens are left out. The report"
            + " \"Taxa outside their native bioregions\" (Curate page) shows a related view."
          , h("Taxon Name", "Bioregions", "Countries", "Group IDs", "Bioregion Count")
          , "select taxon_name, group_concat(distinct bioregion), substring(group_concat(distinct country), 1, 140),"
            + " group_concat(distinct access_group), count(distinct bioregion) count from specimen"
            + " where bioregion is not null and taxon_name not like '%indet%' and (country is null or country != 'Port of Entry')"
            + " and taxon_name not in (select taxon_name from proj_taxon where project_name = 'introducedants')"
            + " group by taxon_name having count(distinct bioregion) > 1 order by count desc, taxon_name"
        ));

        return queries;
    }

// ---------------- Species list exports ----------------

    // Was "Fossil Queries". Removed 2026-10-22: validNonfossilSpeciesWithMuseums (a subset of
    // validNonfossilSpeciesWithMuseumsOwnedByLocatedAt), validNonfossilTaxaWithBioregions (an SQL error: taxon_name
    // ambiguous), antwebUniqueValidTaxa and antwebUniqueFossilTaxa (compared with AntWiki lists loaded once in 2016).
    private static ArrayList<NamedQuery> getFossilNamedQueries() {
        ArrayList<NamedQuery> queries = new ArrayList<>();

        queries.add(new NamedQuery(
           "validNonfossilSpeciesWithBioregions"
          , "Every valid, living (non-fossil) species with the bioregions it is recorded from (bioregion species"
            + " lists). An export to copy into a spreadsheet; rows match validNonfossilSpeciesWithMuseumsOwnedByLocatedAt"
            + " by taxon name. A species with no bioregion shows null."
          , h("Taxon Name", "Subfamily", "Genus", "Species", "Bioregions")
          , "select t.taxon_name, t.subfamily, t.genus, t.species, group_concat(distinct bt.bioregion_name) from taxon t"
            + " left join bioregion_taxon bt on t.taxon_name = bt.taxon_name where t.status = 'valid' and t.fossil = 0"
            + " and t.taxarank = 'species' group by t.taxon_name, t.subfamily, t.genus, t.species order by t.taxon_name"
        ));

        queries.add(new NamedQuery(
           "validNonfossilSpeciesWithMuseumsOwnedByLocatedAt"
          , "Every valid, living (non-fossil) species with the museums, \"owned by\" and \"located at\" values of its"
            + " specimens. An export to copy into a spreadsheet; a species without specimens shows null. Takes up"
            + " to a minute."
          , h("Taxon Name", "Museums", "Owned By", "Located At")
          , "select t.taxon_name, group_concat(distinct s.museum) museum, group_concat(distinct s.ownedby) ownedBy,"
            + " group_concat(distinct s.locatedat) locatedAt from taxon t left join specimen s on t.taxon_name = s.taxon_name"
            + " where t.status = 'valid' and t.fossil = 0 and t.taxarank = 'species' group by t.taxon_name order by t.taxon_name"
        ));

        return queries;
    }

// ---------------- Login queries ----------------

    private static ArrayList<NamedQuery> getLoginNamedQueries() {
        ArrayList<NamedQuery> queries = new ArrayList<>();

        queries.add(new NamedQuery(
            "loginProjectMapping"
          , "Which logins may edit which project species lists, by group and login. Use it to see who looks after"
            + " a project's list; change it in the Login Manager."
          , h("Group Name", "Login Name", "Login Email", "Project Title", "Project Name")
          , "select g.name, l.name, l.email, p.project_title, p.project_name from ant_group g, login l, login_project lp, project p"
            + " where g.id = l.group_id and l.id = lp.login_id and lp.project_name = p.project_name order by g.name, l.name, p.project_title"
        ));

        queries.add(new NamedQuery(
            "curatorInfoAndProjects"
          , "The logins assigned to at least one live project (the species-list curators), with their email and"
            + " projects, one row per login."
          , h("Login Name", "Email", "Projects")
          , "select l.name, l.email, group_concat(distinct lp.project_name order by lp.project_name) from login l, login_project lp, project p"
            + " where l.id = lp.login_id and lp.project_name = p.project_name and p.is_live = 1 group by l.id, l.name, l.email order by l.name"
        ));

        queries.add(new NamedQuery(
            "curatorEmails"
          , "The emails of the logins assigned to a live project (the species-list curators), in one"
            + " comma-separated cell to paste into an email. These are not the uploaders: see uploadingCurators for those."
          , h("Emails")
          , "select group_concat(distinct concat(l.email, ' ')) from login l, login_project lp, project p"
            + " where l.id = lp.login_id and lp.project_name = p.project_name and p.is_live = 1"
        ));

        // Inactive added 2026-10-22: an inactive login (InactiveAccountDb) cannot log in until it is reactivated.
        queries.add(new NamedQuery(
            "uploadingCurators"
          , "Every login allowed to upload specimens or images: login ID, first and last name, email, name, upload"
            + " group number, administrator, the two upload rights (1 = yes) and whether the login is inactive (1 ="
            + " cannot log in until reactivated). Use it to see who can upload."
          , h("Login ID", "First Name", "Last Name", "Email", "Name", "Group ID", "Is Admin?",
              "Can Upload Specimens", "Can Upload Images", "Inactive")
          , "select id, first_name, last_name, email, name, group_id, is_admin, is_upload_specimens, is_upload_images, inactive"
            + " from login where is_upload_specimens = true or is_upload_images = true"
        ));

        return queries;
    }

// ---------------- Group queries ----------------

    private static ArrayList<NamedQuery> getGroupsNamedQueries() {
        ArrayList<NamedQuery> queries = new ArrayList<>();

        queries.add(new NamedQuery(
            "adminLoginIdIsZero"
          , "Upload groups that have no administrator login. Specimens loaded for such a group get no access"
            + " login, which breaks later reloads of the group's files. Normally none. Fix: open the group in the"
            + " Upload groups page (viewGroups.do) and choose its administrator."
          , h("Group ID", "Group Name")
          , "select id, name from ant_group where (admin_login_id = 0 or admin_login_id is null) and id > 0"
          ));

        // Was: "Specimen uploads ordered by date", with a "Last Upload" column that was the date of the newest
        // specimen record, not of an upload (Brian Fisher, 2026-10-22). Shows both now, labeled.
        queries.add(new NamedQuery(
            "lastSpecimenUpload"
          , "Each group's specimens and two dates: the last specimen upload recorded in the upload table (the"
            + " Specimen Uploads list) and the creation date of the group's newest specimen record. An upload"
            + " replaces all of a group's specimens, so the two dates are normally the same day. A newer specimen"
            + " date means an upload that was not recorded in the upload table (its report is missing from the"
            + " list); an empty upload date means no upload of the group was ever recorded. Groups with the most"
            + " recent activity first."
          , h("Group ID", "Group Name", "Specimens", "Last Upload (upload table)", "Uploads Recorded", "Newest Specimen Record")
          , "select g.id, g.name, s.specimens, u.last_upload, u.uploads, s.newest from ant_group g"
            + " join (select access_group, count(*) specimens, max(created) newest from specimen group by access_group) s on s.access_group = g.id"
            + " left join (select group_id, max(created) last_upload, count(*) uploads from upload group by group_id) u on u.group_id = g.id"
            + " order by greatest(coalesce(u.last_upload, s.newest), s.newest) desc"
        ));

        return queries;
    }

// ---------------- Geolocale (place) queries ----------------

    private static ArrayList<NamedQuery> getGeolocaleNamedQueries() {
        ArrayList<NamedQuery> queries = new ArrayList<>();

        queries.add(new NamedQuery(
            "invalidValidNamelessCountries"
          , "Countries marked not valid (an old or alternative name) that do not say which valid country they stand"
            + " for. Specimens with such a country are not counted for any valid country. Fix: set the valid name"
            + " of each in the Geolocale Manager (Edit Geolocale)."
          , h("Name")
          , "select name from geolocale where georank = 'country' and is_valid = 0 and (valid_name is null or valid_name = '')"
          ));

        // Was: (not valid and coords) or (valid and no coords) and country, so the first part was not limited to
        // countries (AND binds before OR).
        queries.add(new NamedQuery(
            "usedAndUnusedCoords"
          , "Valid countries without map coordinates (their map is blank) and countries that are not valid but"
            + " still have coordinates (unused). Fix: add the coordinates of a valid country in the Geolocale"
            + " Manager; coordinates of a non-valid one can be left or cleared."
          , h("Name", "Is Valid", "Is UN", "Coords")
          , "select name, is_valid, is_un, coords from geolocale where georank = 'country'"
            + " and ((is_valid = 0 and coords is not null) or (is_valid = 1 and coords is null)) order by is_valid, name"
          ));

        queries.add(new NamedQuery(
            "nonAntsInSpeciesListsGeolocale"
           , "Names on country, adm1 or region species lists that are not ants (family not Formicidae). Place"
            + " lists are for ants only. Fix: remove the name with the Species List Tool, or correct its family at"
            + " its source if it is an ant."
           , h("Taxon Name", "Family", "Place")
           , "select gt.taxon_name, t.family, g.name from geolocale_taxon gt, taxon t, geolocale g"
            + " where gt.geolocale_id = g.id and gt.taxon_name = t.taxon_name and t.family != 'formicidae'"
           ));

        /*
          To fix these: run the select below, then in the result replace each curly quote with two straight ones ('').
            select concat(concat(concat("update geolocale set name = '", name),"' where id = "), id) as dml from geolocale where name like '%‘%' or name like '%’%'
        */
        queries.add(new NamedQuery(
            "badSingleQuotes"
          , "Place names written with a curly apostrophe (‘ or ’) instead of the straight one ('). Specimen data"
            + " uses the straight one, so these places do not match their specimens. Fix: rename the place in the"
            + " Geolocale Manager with a straight apostrophe."
          , h("ID", "Name", "Source", "Created")
          , "select id, name, source, created from geolocale where name like '%‘%' or name like '%’%'"
           ));

        queries.add(new NamedQuery(
            "geolocaleNameWithQuestionMarks"
          , "Place names that contain a question mark, usually an accented letter lost when the name was loaded."
            + " Such a place does not match its specimens. Fix: correct the name in the Geolocale Manager."
          , h("ID", "Name", "Parent", "Source", "Created")
          , "select id, name, parent, source, created from geolocale where name like '%?%'"
           ));

        queries.add(new NamedQuery(
            "duplicatedGeolocaleNames"
          , "Place names used by more than one place (for example Georgia, a country and a US state), highest"
            + " rank first. Often correct; worth checking when a place's specimens or species look wrong, since"
            + " specimen data names places by name."
          , h("ID", "Name", "Georank", "Parent", "Is Valid")
          , "select id, name, georank, parent, is_valid from geolocale where name in (select name from geolocale group by name"
            + " having count(*) > 1) order by georank desc, is_valid desc, name, valid_name"
        ));

        // Was: "less than 5 proj_taxon records" over 2 headings for 3 columns; it counted geolocale_taxon and listed
        // countries with 5 or fewer, valid or not.
        queries.add(new NamedQuery(
            "lowGeolocaleTaxonCount"
          , "Valid countries whose species list has 5 names or fewer (all ranks), with the count. A country with"
            + " ants recorded should have more; a short list usually means no specimens or literature records have"
            + " been loaded for it yet. For review."
          , h("Geolocale ID", "Country", "Bioregion", "Names on List")
          , "select g.id, g.name, g.bioregion, count(gt.taxon_name) from geolocale g left join geolocale_taxon gt on gt.geolocale_id = g.id"
            + " where g.georank = 'country' and g.is_valid = 1 group by g.id, g.name, g.bioregion having count(gt.taxon_name) <= 5 order by g.name"
          ));

        // Was: 2 headings for 3 columns, and countries that are not valid were listed.
        queries.add(new NamedQuery(
            "unfinishedBioregions"
          , "Valid countries without a bioregion. Their specimens get no bioregion at upload, so they are missing"
            + " from the bioregion pages. Fix: set the country's bioregion in the Geolocale Manager."
          , h("Geolocale ID", "Country")
          , "select g.id, g.name from geolocale g where g.georank = 'country' and g.is_valid = 1 and (g.bioregion is null or g.bioregion = '')"
        ));

        queries.add(new NamedQuery(
            "nonvalidValidNames"
          , "Places whose valid name (the place they stand for) is not a valid place. Their specimens are counted"
            + " for no place. Fix: correct the valid name in the Geolocale Manager."
          , h("Geolocale ID", "Name", "Parent", "Valid Name")
          , "select id, name, parent, valid_name from geolocale where valid_name is not null and valid_name != ''"
            + " and valid_name != 'none' and valid_name not in (select name from geolocale where is_valid = 1)"
        ));

        queries.add(new NamedQuery(
            "nonLiveValidCountries"
          , "Valid countries that are not live, so they have no public page or list. Check that this is meant"
            + " (for example a country with no ant records yet); set Is Live in the Geolocale Manager if not."
          , h("Geolocale ID", "Name", "Is Valid", "Is Live")
          , "select id, name, is_valid, is_live from geolocale where georank = 'country' and (is_live = 0 or is_live is null)"
            + " and is_valid = 1 order by name"
        ));

        // Was: one cell with every specimen code of the country.
        queries.add(new NamedQuery(
            "geolocaleCountryTest"
          , "Country names in specimen data that are not a country in AntWeb's place table (valid or not), with"
            + " the number of specimens and a few of their codes. These specimens are on no country page. Fix:"
            + " correct the country in the group's upload file, or add the name as a non-valid country with its"
            + " valid name in the Geolocale Manager."
          , h("Country", "Specimens", "Example Codes")
          , "select s.country, s.n, s.codes from (select country, count(*) n, substring(group_concat(code), 1, 120) codes"
            + " from specimen where country is not null and country != '' group by country) s"
            + " where not exists (select 1 from geolocale g where g.georank = 'country' and g.name = s.country) order by s.n desc"
          ));

        // Was: 2 headings for 3 columns, every specimen code in one cell, and specimens without an adm1 were listed.
        queries.add(new NamedQuery(
            "geolocaleAdm1Test"
          , "Adm1 names (state, province) in specimen data that are not an adm1 of that country in AntWeb's place"
            + " table, with the number of specimens and a few codes. These specimens are on no adm1 page. Fix:"
            + " correct the adm1 in the group's upload file, or add the adm1 in the Geolocale Manager (Adm1 Manager)."
          , h("Country", "Adm1", "Specimens", "Example Codes")
          , "select s.country, s.adm1, s.n, s.codes from (select country, adm1, count(*) n, substring(group_concat(code), 1, 120) codes"
            + " from specimen where adm1 is not null and adm1 != '' group by country, adm1) s"
            + " where not exists (select 1 from geolocale g where g.georank = 'adm1' and g.parent = s.country and g.name = s.adm1)"
            + " order by s.country, s.n desc"
          ));

        // extentAndCoords was removed 2026-10-22: it joined project.geolocale_id, a column dropped in 2017
        // (db/upgrade/6.16), so it could only fail.
        return queries;
    }

// ---------------- Project species list queries ----------------

    private static ArrayList<NamedQuery> getProjectTaxaNamedQueries() {
        ArrayList<NamedQuery> queries = new ArrayList<>();

        queries.add(new NamedQuery(
           "projTaxaWithoutTaxonWithoutHomonym"
          , "Names on project species lists that are neither among AntWeb's names nor homonyms (the AntCat lists"
            + " worldants and fossilants are left out). The list shows a name without a page. Fix: correct or"
            + " remove the name with the Species List Tool."
          , h("Project Name", "Taxon Name", "Source")
          , "select pt.project_name, " + link("/description.do?taxonName=", "pt.taxon_name") + ", pt.source from proj_taxon pt"
            + " where (pt.source is null or pt.source not in ('worldants', 'fossilants'))"
            + " and not exists (select 1 from taxon t where t.taxon_name = pt.taxon_name)"
            + " and not exists (select 1 from homonym h where h.taxon_name = pt.taxon_name) order by pt.project_name, pt.source"
        ));

        queries.add(new NamedQuery(
           "projTaxaWithoutTaxonWithHomonym"
          , "Names on project species lists that are homonyms but not among AntWeb's names (the AntCat lists are"
            + " left out). Each homonym should have a taxon of the same name; without it the list links to no page."
            + " Fix: replace the name with the valid one in the Species List Tool."
          , h("Project Name", "Taxon Name", "Source")
          , "select pt.project_name, " + link("/description.do?taxonName=", "pt.taxon_name") + ", pt.source from proj_taxon pt"
            + " where (pt.source is null or pt.source not in ('worldants', 'fossilants'))"
            + " and not exists (select 1 from taxon t where t.taxon_name = pt.taxon_name)"
            + " and exists (select 1 from homonym h where h.taxon_name = pt.taxon_name) order by pt.project_name, pt.source"
        ));

        queries.add(new NamedQuery(
            "quadrinomialsInSpeciesLists"
          , "Names with four parts (genus, species, subspecies and one more) on project species lists other than"
            + " allantwebants and worldants. Species lists should hold species and subspecies only. Fix: replace the"
            + " name with its species or subspecies in the Species List Tool."
          , h("Taxon Name", "Project Name", "Source", "Created")
          , "select taxon_name, project_name, source, created from proj_taxon where project_name not in ('worldants', 'allantwebants')"
            + " and length(taxon_name) - length(replace(taxon_name, ' ', '')) > 2"
          ));

        queries.add(new NamedQuery(
            "nonAntsInSpeciesListsProject"
           , "Names on project species lists (other than allantwebants) that are not ants. Project lists are for"
            + " ants only. Fix: remove the name with the Species List Tool, or correct its family at its source."
           , h("Taxon Name", "Family", "Project Name")
           , "select pt.taxon_name, t.family, pt.project_name from proj_taxon pt, taxon t where pt.taxon_name = t.taxon_name"
            + " and t.family not like 'form%' and pt.project_name != 'allantwebants'"
           ));

        queries.add(new NamedQuery(
            "speciesListHomonyms"
          , "Homonyms on project species lists (other than allantwebants and worldants; the genus Camponotus is"
            + " left out). A homonym on a list may be the wrong one of two same-spelled names. For review in the"
            + " Species List Tool."
          , h("Homonym", "Project")
          , "select pt.taxon_name, pt.project_name from proj_taxon pt where pt.project_name not in ('allantwebants', 'worldants')"
            + " and pt.taxon_name not in ('formicinaecamponotus') and pt.taxon_name in (select taxon_name from homonym)"
            + " order by pt.project_name, pt.taxon_name"
          ));

        queries.add(new NamedQuery(
            "morphoProjTaxa"
          , "Names on project species lists (other than allantwebants and worldants) that look like morphospecies"
            + " (they contain a digit, ?, -, _, a parenthesis or a dot) and were added without a source. For review"
            + " in the Species List Tool."
          , h("Taxon Name", "Project Name", "Source", "Created")
          , "select taxon_name, project_name, source, created from proj_taxon where (taxon_name like '%?%' or taxon_name like '%1%'"
            + " or taxon_name like '%2%' or taxon_name like '%3%' or taxon_name like '%4%' or taxon_name like '%5%' or taxon_name like '%6%'"
            + " or taxon_name like '%7%' or taxon_name like '%8%' or taxon_name like '%9%' or taxon_name like '%-%' or taxon_name like '%\\_%'"
            + " or taxon_name like '%(%' or taxon_name like '%)%' or taxon_name like '%.%') and source is null"
            + " and project_name not in ('worldants', 'allantwebants')"
        ));

        // Was: pt.taxon_name not in (select taxon_name from specimen), which finds nothing as soon as one specimen
        // has no taxon name.
        queries.add(new NamedQuery(
           "projTaxaMorphosWithNoSpecimen"
          , "Morphospecies on project species lists that no specimen is filed under. A morphospecies may be on a"
            + " list only while AntWeb has a specimen of it. Fix: remove it with the Species List Tool, or upload"
            + " its specimen."
          , h("Taxon Name", "Project Name", "Source")
          , "select pt.taxon_name, pt.project_name, pt.source from proj_taxon pt, taxon t where pt.taxon_name = t.taxon_name"
            + " and t.taxarank in ('species', 'subspecies') and t.status = 'morphotaxon'"
            + " and not exists (select 1 from specimen s where s.taxon_name = pt.taxon_name)"
        ));

        // Replaces notValidTaxaFromSpeciesList and incorrectNameInSpeciesList (the same list), 2026-10-22.
        queries.add(new NamedQuery(
            "notValidProjectTaxonNames"
          , "Names on project species lists (other than allantwebants and worldants) that are not valid in AntCat"
            + " (synonyms, unavailable names...; morphospecies left out), with the name to use instead. Fix: replace"
            + " the name with its current valid name in the Species List Tool."
          , h("Project Name", "Taxon Name", "Status", "Current Valid Name", "Source")
          , "select p.project_name, t.taxon_name, t.status, t.current_valid_name, pt.source from taxon t, proj_taxon pt, project p"
            + " where t.taxon_name = pt.taxon_name and pt.project_name = p.project_name and t.status not in ('valid', 'morphotaxon')"
            + " and p.project_name not in ('allantwebants', 'worldants') order by p.project_name, t.taxon_name"
        ));

        // The project taxon counts battery (query.do?action=queryBattery&name=projectTaxonCounts).
        queries.add(new NamedQuery(
            "projectTaxaCount"
          , "The number of rows of all project species lists together. Part of the project taxon counts set"
            + " (query.do?action=queryBattery&name=projectTaxonCounts), used to check the counts before and after a"
            + " nightly run."
          , h("Rows")
          , "select count(*) from proj_taxon"
        ));
        queries.add(new NamedQuery(
            "projectTaxaCountByProject"
          , "The number of names on each project species list. Part of the project taxon counts set."
          , h("Project Name", "Names")
          , "select project_name, count(taxon_name) from proj_taxon group by project_name order by project_name"
        ));
        // Was: headings in another order than the columns.
        queries.add(new NamedQuery(
            "projectTaxaCountByProjectRank"
          , "The names on the worldants and allantwebants lists by rank and source. worldants holds every AntCat"
            + " name and allantwebants every name with a specimen; a large drop between two runs means a list was"
            + " not rebuilt. Part of the project taxon counts set."
          , h("Project Name", "Rank", "Source", "Names")
          , "select pt.project_name, t.taxarank, pt.source, count(pt.taxon_name) from proj_taxon pt, taxon t where pt.taxon_name = t.taxon_name"
            + " and pt.project_name in ('worldants', 'allantwebants') group by pt.project_name, t.taxarank, pt.source"
            + " order by pt.project_name, t.taxarank, pt.source"
        ));

        return queries;
    }

// ---------------- Geolocale (place) species list queries ----------------

    private static ArrayList<NamedQuery> getGeolocaleTaxaNamedQueries() {
        ArrayList<NamedQuery> queries = new ArrayList<>();

        queries.add(new NamedQuery(
            "badGeolocaleTaxonParentTaxonName"
          , "Names on a place's species list whose parent (the genus of a species, the subfamily of a genus) is"
            + " not on the same list. The name then does not show under its parent on the place's pages. The"
            + " nightly run adds missing parents; rows here after a nightly run are for the developers. Takes up"
            + " to a minute."
          , h("Taxon Name", "Geolocale ID", "Geolocale Name", "Source", "Parent Taxon Name", "Created")
          , "select gt.taxon_name, gt.geolocale_id, g.name, gt.source, t.parent_taxon_name, gt.created"
            + " from geolocale_taxon gt join taxon t on gt.taxon_name = t.taxon_name join geolocale g on g.id = gt.geolocale_id"
            + " where t.parent_taxon_name != 'hymenoptera'"
            + " and not exists (select 1 from geolocale_taxon p where p.geolocale_id = gt.geolocale_id and p.taxon_name = t.parent_taxon_name)"
            + " order by gt.source, gt.taxon_name"
        ));

        // Replaces geolocaleListMorphotaxa (the same check, also in the Integrity Check), 2026-10-22.
        queries.add(new NamedQuery(
           "geolocaleTaxaMorphosWithNoSpecimen"
          , "Morphospecies on country, adm1 or region species lists that no specimen is filed under. A morphospecies"
            + " may be on a list only while AntWeb has a specimen of it (from any place). Fix: remove it with the"
            + " Species List Tool, or upload its specimen."
          , h("Taxon Name", "Geolocale Name", "Source")
          , "select " + link("/description.do?taxonName=", "t.taxon_name") + ", g.name, gt.source from geolocale_taxon gt, taxon t, geolocale g"
            + " where g.id = gt.geolocale_id and gt.taxon_name = t.taxon_name and t.taxarank in ('species', 'subspecies')"
            + " and t.status = 'morphotaxon' and not exists (select 1 from specimen s where s.taxon_name = t.taxon_name)"
            + " order by g.name, t.taxon_name"
        ));

        // Replaces notValidGeolocaleTaxonNames (the same list), 2026-10-22.
        queries.add(new NamedQuery(
           "geolocaleTaxaFromInvalidTaxonName"
          , "Names on place species lists that are neither valid nor morphospecies (synonyms, unavailable,"
            + " unrecognized and indeterminate names...), with the name to use instead where AntCat gives one."
            + " Fix: replace the name with its current valid name in the Species List Tool; a name from specimens"
            + " follows the specimens' identification."
          , h("Taxon Name", "Status", "Current Valid Name", "Source", "Parent", "Place", "Geolocale ID")
          , "select gt.taxon_name, t.status, t.current_valid_name, gt.source, g.parent, g.name, gt.geolocale_id"
            + " from geolocale_taxon gt, geolocale g, taxon t where gt.geolocale_id = g.id and gt.taxon_name = t.taxon_name"
            + " and t.status not in ('valid', 'morphotaxon') order by gt.taxon_name, g.parent, g.name"
        ));

        queries.add(new NamedQuery(
           "geolocaleTaxaNotUsingCurrentValidName"
          , "Country and region species lists (by geolocale id) that still hold a synonym or other non-valid name"
            + " instead of its current valid name. Fix: in the Species List Tool for that country or region, replace"
            + " the name with the current valid name; names that come from specimens follow the next nightly run."
          , h("Geolocale ID", "Place", "Taxon Name", "Status", "Current Valid Name")
          , "select gt.geolocale_id, g.name, gt.taxon_name, t.status, t.current_valid_name from geolocale_taxon gt, taxon t, geolocale g"
            + " where gt.taxon_name = t.taxon_name and g.id = gt.geolocale_id and t.status not in ('valid', 'morphotaxon')"
            + " and t.current_valid_name is not null and t.current_valid_name != '' and t.taxon_name != t.current_valid_name"
            + " order by g.name, gt.taxon_name"
        ));

        queries.add(new NamedQuery(
           "geolocaleTaxaWithoutTaxon"
          , "Names on place species lists that are not among AntWeb's names (renamed or deleted), with the newest"
            + " date one was added and where it came from. The lists show a name without a page. The nightly run"
            + " removes most; the detail query lists each place."
          , h("Taxon Name", "Last Added", "Source")
          , "select gt.taxon_name, max(gt.created), gt.source from geolocale_taxon gt"
            + " where not exists (select 1 from taxon t where t.taxon_name = gt.taxon_name)"
            + " group by gt.taxon_name, gt.source order by gt.source, max(gt.created) desc, gt.taxon_name"
          , "geolocaleTaxaWithoutTaxonDetail"
        ));

        queries.add(new NamedQuery(
           "geolocaleTaxaWithoutTaxonDetail"
          , "Detail of geolocaleTaxaWithoutTaxon: each place species list row whose name is not among AntWeb's"
            + " names, with the place."
          , h("Source", "Taxon Name", "Geolocale Name", "Created", "Insert Method")
          , "select gt.source, gt.taxon_name, g.name, gt.created, gt.insert_method from geolocale_taxon gt, geolocale g"
            + " where gt.geolocale_id = g.id and not exists (select 1 from taxon t where t.taxon_name = gt.taxon_name)"
            + " order by gt.source, gt.taxon_name, g.georank desc"
        ));

        // Was described as "multiple" bioregions; it lists three or more.
        queries.add(new NamedQuery(
           "geolocaleTaxaInMultipleBioregions"
          , "Species and subspecies on the species lists of places in three or more bioregions that are not on"
            + " the introduced species list (project introducedants), most bioregions first. Either the species is"
            + " introduced and the list lacks it, or a place's list holds a wrong name. For review."
          , h("Taxon Name", "Bioregion Count", "Bioregions")
          , "select " + link("/description.do?taxonName=", "t.taxon_name")
            + ", count(distinct g.bioregion), group_concat(distinct concat(' ', g.bioregion)) from geolocale_taxon gt, geolocale g, taxon t"
            + " where g.id = gt.geolocale_id and gt.taxon_name = t.taxon_name"
            + " and t.taxon_name not in (select taxon_name from proj_taxon where project_name = 'introducedants')"
            + " and t.taxarank in ('species', 'subspecies') group by t.taxon_name having count(distinct g.bioregion) > 2"
            + " order by count(distinct g.bioregion) desc"
        ));

        return queries;
    }

// ---------------- Bioregion and museum species list queries ----------------

    private static ArrayList<NamedQuery> getBioregionTaxaNamedQueries() {
        ArrayList<NamedQuery> queries = new ArrayList<>();

        queries.add(new NamedQuery(
            "notValidBioregionTaxonNames"
          , "Names on bioregion species lists that are neither valid nor morphospecies, with the name to use"
            + " instead. Bioregion lists are built from specimens and AntCat by the nightly run, so these names"
            + " come from specimens filed under them: the fix is the specimens' identification."
          , h("Bioregion Name", "Taxon Name", "Status", "Current Valid Name")
          , "select b.name, t.taxon_name, t.status, t.current_valid_name from taxon t, bioregion_taxon bt, bioregion b"
            + " where t.taxon_name = bt.taxon_name and bt.bioregion_name = b.name and t.status not in ('valid', 'morphotaxon')"
            + " order by b.name, t.taxon_name"
        ));

        return queries;
    }

    private static ArrayList<NamedQuery> getMuseumTaxaNamedQueries() {
        ArrayList<NamedQuery> queries = new ArrayList<>();

        queries.add(new NamedQuery(
            "notValidMuseumTaxonNames"
          , "Names on museum species lists that are neither valid nor morphospecies, with the name to use"
            + " instead. Museum lists are built from the specimens the museum owns, so the fix is the specimens'"
            + " identification."
          , h("Museum Name", "Taxon Name", "Status", "Current Valid Name")
          , "select m.name, t.taxon_name, t.status, t.current_valid_name from taxon t, museum_taxon mt, museum m"
            + " where t.taxon_name = mt.taxon_name and mt.code = m.code and t.status not in ('valid', 'morphotaxon')"
            + " order by m.name, t.taxon_name"
        ));
        return queries;
    }

// ---------------- Image queries ----------------

    private static ArrayList<NamedQuery> getImageNamedQueries() {
        ArrayList<NamedQuery> queries = new ArrayList<>();

        queries.add(new NamedQuery(
          "imageCountByOwner"
        , "Images (one per photo: head, profile, dorsal, label...) per \"owned by\" value of the photographed"
          + " specimen, largest first. Takes up to a minute."
        , h("Images", "Owned By")
        , "select count(i.id) theCount, s.ownedby from image i, specimen s where i.image_of_id = s.code group by s.ownedby order by theCount desc"
        ));

        // Public: the "Newest 5,000 images (table)" link (was "Download All") on the recent images search results. Was every image ever uploaded (about a million
        // rows, more than a page can show) and 7 headings for 6 columns. Limited to the newest 5,000 (2026-10-22).
        queries.add(new NamedQuery(
           "imageData"
          , "The newest 5,000 images with their specimen, shot (h head, p profile, d dorsal, l label...), shot"
            + " number, upload date, photographer and the group that uploaded them, newest first."
          , h("Specimen", "Shot", "Number", "Upload Date", "Photographer", "Uploaded By")
          , "select i.image_of_id, i.shot_type, i.shot_number, i.upload_date, a.name, g.name from image i"
            + " join group_image gi on gi.image_id = i.id join ant_group g on g.id = gi.group_id"
            + " left join artist a on a.id = i.artist where i.upload_date is not null"
            + " order by i.upload_date desc, i.image_of_id, i.shot_type, i.shot_number limit 5000"
        ));

        // Was: "Image Count by Status" with a heading "distinct t.taxon_name"; it counts names, not images.
        queries.add(new NamedQuery(
                "imageStatusCounts"
                , "How many names have at least one photographed specimen, by the name's status (valid,"
                + " morphotaxon, synonym...). Takes up to a minute."
                , h("Status", "Names with Images")
                , "select t.status, count(distinct t.taxon_name) from taxon t, specimen s, image i"
                + " where t.taxon_name = s.taxon_name and s.code = i.image_of_id group by t.status order by count(distinct t.taxon_name) desc"
        ));

        return queries;
    }

// ---------------- Assorted queries ----------------

    private static ArrayList<NamedQuery> getAssortedNamedQueries() {
        ArrayList<NamedQuery> queries = new ArrayList<>();

        queries.add(new NamedQuery(
                "descriptionEditCountByOwner"
                , "Taxon page text sections (one per name and section title) per group of the curator who last"
                + " edited each, largest first."
                , h("Text Sections", "Group")
                , "select count(distinct de.taxon_name, de.title) theCount, g.name from description_edit de, ant_group g"
                + " where de.access_group = g.id group by g.id, g.name order by theCount desc"
        ));

        queries.add(new NamedQuery(
                "importedCountryData"
                , "The UN country list loaded once in 2015 (un_country): name, UN region and subregion, and the"
                + " bioregion given then. Reference only; nothing updates it and no page uses it."
                , h("Country", "UN Region", "UN Subregion", "Bioregion")
                , "select name, un_region, un_subregion, bioregion from un_country order by name"
        ));

        queries.add(new NamedQuery(
                "worldantsUploads"
                , "The last 60 imports of AntCat names (Worldants uploads), newest first."
                , h("Created", "Operation", "Message", "File Size", "Worldants Count", "Exec Time", "Log File")
                , "SELECT created, operation, validate_message, file_size, orig_worldants_count, exec_time "
                + ", concat(concat(\"<a href='" + AntwebProps.getDomainApp() + "/web/log/worldants/\",log_file_name), \"'>Log file</a>\") "
                + " FROM worldants_upload order by created desc limit 60"
        ));

        // ancillaryFiles was removed 2026-10-22: it listed the "ancillary" pages of before 2011, a feature that is gone.

        queries.add(new NamedQuery(
                "adminAlerts"
                , "Every Admin Alert so far (warnings AntWeb raises for the administrators: failed imports, low"
                + " counts...), the ones not yet acknowledged first. Acknowledge them in the Admin Alert Manager."
                , h("ID", "Alert", "Acknowledged", "Created")
                , "select id, alert, acknowledged, created from admin_alerts order by acknowledged, created desc, id"
        ));

        queries.add(new NamedQuery(
                "emptySourceTaxonSets"
                , "Rows of project, place and bioregion species lists with an empty source (where the name came"
                + " from). Every row should say whether it came from specimens, AntCat or a curator; an empty source"
                + " points at the code that added it (for the developers). Key is the project, place id or bioregion."
                , h("Key", "Taxon Name", "Source", "Created")
                , "select project_name, taxon_name, source, created from proj_taxon where source = '' and taxon_name not in ('incertae_sedis', '(formicidae)')"
                + " union select geolocale_id, taxon_name, source, created from geolocale_taxon where source = '' and taxon_name not in ('incertae_sedis', '(formicidae)')"
                + " union select bioregion_name, taxon_name, source, created from bioregion_taxon where source = '' and taxon_name not in ('incertae_sedis', '(formicidae)')"
        ));

        queries.add(new NamedQuery(
                "geolocaleTaxonDisputes"
                , "Names a curator said do not occur in a place (disputes, set with the Species List Tool), newest"
                + " first. The nightly run keeps them off that place's list even if specimens say otherwise."
                , h("Geolocale", "Geolocale ID", "Taxon Name", "Source", "Curator", "Created")
                , "select g.name, g.id, gtd.taxon_name, gtd.source, l.name, gtd.created from geolocale_taxon_dispute gtd"
                + " join geolocale g on gtd.geolocale_id = g.id left join login l on l.id = gtd.curator_id order by gtd.created desc"
        ));
        queries.add(new NamedQuery(
                "projectTaxonDisputes"
                , "Names a curator said do not occur in a project (disputes, set with the Species List Tool), newest"
                + " first."
                , h("Project", "Taxon Name", "Source", "Curator", "Created")
                , "select p.project_name, ptd.taxon_name, ptd.source, l.name, ptd.created from proj_taxon_dispute ptd"
                + " join project p on ptd.project_name = p.project_name left join login l on l.id = ptd.curator_id order by ptd.created desc"
        ));

        queries.add(new NamedQuery(
                "conflictedDefaultImages"
                , "Taxa whose default images (chosen on the taxon page) come from a specimen now filed under another"
                + " name, so the page shows another species' photos. The nightly run (set 5) removes such choices;"
                + " rows here after a nightly run are for the developers."
                , h("Taxon Name", "Property", "Specimen", "Created", "Login ID", "Specimen Taxon Name")
                , "select tp.taxon_name, tp.prop, tp.value, tp.created, tp.login_id, s.taxon_name from taxon_prop tp, specimen s"
                + " where tp.value = s.code and tp.prop like '%Specimen' and tp.taxon_name != s.taxon_name"
        ));

        return queries;
    }

    // Invoke like: /query.do?action=curiousQuery&name=nonAsciiTaxonName
    public static ArrayList<NamedQuery> getNamedQueries() {
        ArrayList<NamedQuery> namedQueries = new ArrayList<>();
        for (Map.Entry<String, ArrayList<NamedQuery>> block : getBlocks().entrySet()) {
            namedQueries.addAll(block.getValue());
        }
        return namedQueries;
    }

    /** The queries by topic, in the order of the Query Manager page. */
    static LinkedHashMap<String, ArrayList<NamedQuery>> getBlocks() {
        LinkedHashMap<String, ArrayList<NamedQuery>> blocks = new LinkedHashMap<>();
        blocks.put("Taxon Queries", getTaxaNamedQueries());
        blocks.put("Homonym Queries", getHomonymNamedQueries());
        blocks.put("Specimen Queries", getSpecimenNamedQueries());
        blocks.put("Species List Exports (valid living species)", getFossilNamedQueries());
        blocks.put("Login Queries", getLoginNamedQueries());
        blocks.put("Group Queries", getGroupsNamedQueries());
        blocks.put("Geolocale (Place) Queries", getGeolocaleNamedQueries());
        blocks.put("Project Species List Queries", getProjectTaxaNamedQueries());
        blocks.put("Place Species List Queries", getGeolocaleTaxaNamedQueries());
        blocks.put("Bioregion Species List Queries", getBioregionTaxaNamedQueries());
        blocks.put("Museum Species List Queries", getMuseumTaxaNamedQueries());
        blocks.put("Image Queries", getImageNamedQueries());
        blocks.put("Assorted Queries", getAssortedNamedQueries());
        return blocks;
    }

    public static String getQueryManagerPage() {
        String page = "<h2>&nbsp;Query Manager</h2>";
        page += "<div class=\"mgr_help\" style=\"max-width:60em;margin:6px 16px 12px 16px\">Ready-made database queries, by topic."
            + " Click a name to run it: the page shows what the query is for and what to do about its rows, the query,"
            + " its rows and how long it took. The queries only read data (nothing is changed); some take a minute on"
            + " the full database, and none may run longer than " + QueryManager.QUERY_TIMEOUT_SECS + " seconds."
            + " Most are checks that should find no rows. Administrators only, except the few that public pages link to."
            + " The checks that should find nothing also run together in the Integrity Check.</div>";
        for (Map.Entry<String, ArrayList<NamedQuery>> block : getBlocks().entrySet()) {
            page += getQueryManagerBlock(block.getKey(), block.getValue());
        }
        return page;
    }
    public static String getQueryManagerBlock(String title, ArrayList<NamedQuery> queries) {
        String block = "";
        block += "<br><br><h3>&nbsp;<u>" + Html.esc(title) + "</u></h3>";
        for (NamedQuery namedQuery : queries) {
          block += "&nbsp;&nbsp;&nbsp;&nbsp;<a href='" + AntwebProps.getDomainApp() + "/query.do?name=" + namedQuery.getName() + "'>"
            + namedQuery.getName() + "</a> - " + Html.esc(namedQuery.getSummary()) + "<br>" ;
        }
        return block;
    }

    /**
     * One plain line next to each query of the "Relevant Queries" boxes of the Upload groups page
     * (viewGroups.do) and the Login Manager (viewLogins.do): what it lists, its columns, when to use it
     * (Brian Fisher, 2026-10-21). A query without a line here shows the first sentence of its explanation
     * (Geolocale Manager, 2026-10-22).
     */
    static final Map<String, String> QUERY_LIST_HELP = Map.of(
        "specimenGroups", "How many specimen records each upload group has on AntWeb: group number, name and count, "
            + "largest first. Use it to compare groups or to check a group's total after an upload.",
        "uploadingCurators", "Every login allowed to upload specimens or images: login ID, first and last name, email, "
            + "name, upload group number, administrator, the two upload rights (1 = yes) and whether the login is "
            + "inactive. Use it to see who can upload.",
        "curatorEmails", "The emails of the logins assigned to a live project (the species-list curators), in one "
            + "comma-separated cell to paste into an email. These are not the uploaders: see uploadingCurators for those.");

    public static String getQueryList(String listName) {
      String html = "";
      ArrayList<String> queries = null;
      if ("Geolocale".equals(listName)) queries = Queries.getGeolocaleQueries();
      if ("Login".equals(listName)) queries = Queries.getLoginQueries();
      if ("Groups".equals(listName)) queries = Queries.getGroupQueries();
      int i = 0;
      if (queries == null) {
        s_log.warn("QueryList:" + listName + " not found.");
        return null;
      }
      for (String query : queries) {
        if (i == 0) html += "<ul align=left>";
        ++i;
        html += "<li><a href='" + AntwebProps.getDomainApp() + "/query.do?action=curiousQuery&name=" + query +"'>" + query + "</a>";
        String help = QUERY_LIST_HELP.get(query);
        if (help == null) {
          NamedQuery namedQuery = getNamedQuery(query);
          if (namedQuery != null) help = namedQuery.getSummary();
        }
        if (help != null) html += " <span class=\"query-help\">" + Html.esc(help) + "</span>";
        if (i == queries.size()) html += "</ul>";
      }
      return html;
    }

    public static NamedQuery getNamedQuery(String name) {
      for (NamedQuery namedQuery : getNamedQueries()) {
        if (namedQuery.getName().equals(name)) return namedQuery;
      }
      return null;
    }

    public static ArrayList<NamedQuery> getNamedQueryList(String[] list) {
        ArrayList<NamedQuery> namedQueries = new ArrayList<>();
        ArrayList<NamedQuery> all = getNamedQueries();
        for (String name : list) {
          for (NamedQuery namedQuery : all) {
            if (name.equals(namedQuery.getName())) {
              namedQueries.add(namedQuery);
              break;
            }
          }
        }
        return namedQueries;
    }
}
