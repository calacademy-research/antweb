package org.calacademy.antweb;

import java.util.*;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;

import jakarta.servlet.http.HttpSession;


public class StatusSet extends Status {
    private static final Log s_log = LogFactory.getLog(StatusSet.class);

    /*  
      StatusSets are groupings of statuses.  See Status for individual statuses.
    */
    //StatusSet values:
    public final static String WORLDANTS = "worldants";
    public final static String ALL = "all";
    public final static String ALL_DETERMINED = "all determined";
    public final static String ALL_INDETERMINED = "all indetermined";
    public final static String COMPLETE = "complete";
    public final static String VALID_FOSSIL = "valid fossil";
    public final static String VALID_WITH_FOSSIL = "Valid (with fossils)";
    public final static String VALID_EXTANT = "valid extant";

    //public final static String VALID_WITHOUT_FOSSIL = "valid without fossil";
    public final static String TYPE = "type";
    // If you add to this set, add to theStatusSetArray as well.

    /*
     * The status choice of lists (taxonomicPage.do, browse.do, images.do), Brian Fisher 2026-10-30 (docs/numbers.md,
     * "Status choice of lists"). Four choices with plain labels, each the rows of a number of the place page:
     *   valid        "Valid species"                 valid, extant          = Valid species / subspecies
     *   validmorpho  "Valid species + morphospecies" valid and morphotaxon  = Species / subspecies / morphospecies
     *                                                (PlaceTotals.SPECIES_STATUSES; species rows identified to genus)
     *   all          "All names"                     the five counted statuses (valid, morphotaxon, indetermined,
     *                                                unidentifiable, unrecognized): every name with specimens
     *   fossil       "Fossil species"                valid, fossil
     * On genus and subfamily lists the same choices apply to the genus and subfamily rows. These are the values of the
     * address (statusSet=...); listStatusSet() turns one into the StatusSet whose criteria the queries use. The old
     * choices "all determined", "all indetermined", "complete" and "type" were removed (they matched no number); old
     * addresses still work (listChoice maps them). The AntCat list (worldants) and Fossil Ants keep their own sets.
     */
    public final static String LIST_VALID = "valid";
    public final static String LIST_VALID_MORPHO = "validmorpho";
    public final static String LIST_ALL = "all";
    public final static String LIST_FOSSIL = "fossil";
    public final static String[] LIST_CHOICES = {LIST_VALID, LIST_VALID_MORPHO, LIST_ALL, LIST_FOSSIL};

    /** Internal set values of the choices validmorpho and all (listStatusSet); not address values. */
    public final static String VALID_MORPHO = "valid and morphotaxon";
    public final static String ALL_NAMES = "all names";

    /** The choices of the AntCat list (worldants) and Fossil Ants, which keep their own sets (default VALID_WITH_FOSSIL). */
    public final static String[] OWN_SET_CHOICES = {VALID_WITH_FOSSIL, VALID_EXTANT, VALID_FOSSIL};

    /**
     * Request attribute (Boolean) set by TaxaPageAction and BrowseAction on a list of subfamilies: whether the place's
     * subfamilies of the choice Valid + morphotaxa include a fossil one (TaxaPage.hasFossilSubfamilies). Brian Fisher,
     * 2026-10-30: the label and header say "extant and fossil" only then ("23 Subfamilies (valid subfamilies, extant and
     * fossil)" on All AntWeb, "8 Subfamilies (valid subfamilies)" on Algeria). See fossilSubfamilies(request).
     */
    public final static String FOSSIL_SUBFAMILIES_ATTR = "listFossilSubfamilies";

    /** The FOSSIL_SUBFAMILIES_ATTR of the request; true when not set (not known: the wording that names both). */
    public static boolean fossilSubfamilies(HttpServletRequest request) {
        Object v = request == null ? null : request.getAttribute(FOSSIL_SUBFAMILIES_ATTR);
        return !(v instanceof Boolean) || (Boolean) v;
    }
        
    public final static String[] allStatusSetArray = {    
        WORLDANTS, ALL, ALL_DETERMINED, ALL_INDETERMINED, COMPLETE, VALID_FOSSIL, VALID_EXTANT, TYPE
    };
    // TYPE is not offered to visitors (2026-10-29): it has no criteria ("1 = 1"), so it listed every name
    // under a label that promises type taxa only. It stays in allStatusSetArray (curators and admins).
    public final static String[] minimalStatusSetArray = {    
        ALL, COMPLETE, VALID_FOSSIL, VALID_EXTANT
    };

    public static ArrayList<String> getAdminStatusSets() {
      ArrayList<String> statusSets = new ArrayList<>(Arrays.asList(allStatusSetArray));
      statusSets.addAll(getAllStatuses()); // call superclass
      return statusSets;
    }    
    public static ArrayList<String> getCuratorStatusSets() {
      return StatusSet.getAdminStatusSets();
    }        
    public static ArrayList<String> getStatusSets() {
      ArrayList<String> statusSets = new ArrayList<>();
      statusSets.addAll(Arrays.asList(minimalStatusSetArray));
      statusSets.addAll(getMinimalStatuses()); 
      return statusSets;
    }    
 
    public StatusSet () {
    }    
    public StatusSet(String value) {
      if (value == null) value = COMPLETE; //ALL;
      this.value = value;
    }

    public String toString() {
        return "StatusSet:" + value;
    }

    public static StatusSet getInstance(String project) {
        StatusSet statusSet = null;
        if (Project.WORLDANTS.equals(project)) {
          statusSet = new StatusSet(StatusSet.VALID);     
        } else  {
          statusSet = new StatusSet(StatusSet.ALL);
        }
        return statusSet;
    }
    
    public static String getAndCriteria(String projectName) {
        /* For calculating children counts,  
         */
        if (Project.WORLDANTS.equals(projectName)) {
          return new StatusSet(VALID).getAndCriteria();
          
        } else if (Project.ALLANTWEBANTS.equals(projectName)) {
          return new StatusSet(ALL).getAndCriteria();

        } else {
          return "";
        }
    }
    
    public String getAndCriteria() {
      String andCriteria = " and " + getCriteria();
      //A.log("getAndCriteria() value:" + getValue() + " andCriteria:" + andCriteria);
      //AntwebUtil.logShortStackTrace(5);
     return andCriteria;
    }
    
    public String getCriteria() {
      // default to taxon
      return getCriteria("taxon");
    }
    public String getCriteria(String table) {

      if (!("taxon".equals(table)
        || "specimen".equals(table)
        || "sp".equals(table)
        )) return null;

      String criteria = null;

      String defaultVal = " " + table + ".status in ('" + VALID + "', '" + UNRECOGNIZED + "', '"
          + MORPHOTAXON + "', '" + INDETERMINED + "', '" + UNIDENTIFIABLE + "')";  // + NOT_CURRENT_VALID + "', '"

      String allDetermined = " " + table + ".status in ('" + VALID + "', '" + UNRECOGNIZED + "', '"
          + MORPHOTAXON + "') and " + table + ".taxon_name not like '%(indet)%' ";    // + NOT_CURRENT_VALID + "', '"

      String allIndetermined = " " + table + ".status in ('" + VALID + "', '" + UNRECOGNIZED + "', '" 
        + MORPHOTAXON + "') and " + table + ".taxon_name like '%(indet)%' ";   // + NOT_CURRENT_VALID + "', '" 

      if (StatusSet.ALL.equals(getValue())) criteria = defaultVal; //return defaultVal;
      // List choices of 2026-10-30: "all" (every counted status) and "validmorpho" (the statuses of the Species number,
      // PlaceTotals.SPECIES_STATUSES: the one rule, not repeated here).
      if (StatusSet.ALL_NAMES.equals(getValue())) criteria = defaultVal;
      if (StatusSet.VALID_MORPHO.equals(getValue())) criteria = " " + org.calacademy.antweb.home.PlaceTotals.speciesStatusCondition(table);
      if (StatusSet.ALL_DETERMINED.equals(getValue())) criteria = allDetermined;
      if (StatusSet.ALL_INDETERMINED.equals(getValue())) criteria = allIndetermined;

/*
      if (StatusSet.WORLDANTS.equals(getValue())) return " source = 'worldants.txt' ";  // easier than setting the full set.
      // Warning, source = worldants should never be overwritten during an AntwebUpload.updateTaxon() call
      // This should not usually be used as there are statusSet within the Worldants upload that are not useful for display
      // Generally what is wanted here is VALID (defined in superclass).
*/
      if (StatusSet.WORLDANTS.equals(getValue())) {
          criteria = " " + table + ".status in ("
                  +   "'" + VALID + "'"
                  + ", '" + COLLECTIVE_GROUP_NAME + "'"
                  + ", '" + EXCLUDED_FROM_FORMICIDAE + "'"
                  + ", '" + HOMONYM + "'"
                  + ", '" + ORIGINAL_COMBINATION + "'"
                  + ", '" + SYNONYM + "'"
                  + ", '" + UNAVAILABLE + "'"
                  + ", '" + UNIDENTIFIABLE + "'"
                  + ", '" + OBSOLETE_COMBINATION + "'"
                  + ", '" + OBSOLETE_CLASSIFICATION + "'"
                  + ", '" + UNAVAILABLE_UNCATEGORIZED + "'"
                  + ", '" + UNAVAILABLE_MISSPELLING + "'"
                  + ")";
      }
      
      if (StatusSet.COMPLETE.equals(getValue())) criteria = " 1 = 1 ";

      //A.log("getAndCriteria() table:" + table + " status:" + getValue());        

      if ("taxon".equals(table)) {
        if (StatusSet.VALID_WITH_FOSSIL.equals(getValue())) criteria = " " + table + ".status = '" + VALID + "'"; // Will be further restricted by the fossilants list.
        if (StatusSet.VALID_FOSSIL.equals(getValue())) criteria = " " + table + ".status = '" + VALID + "' and fossil = 1 ";
        if (StatusSet.VALID_EXTANT.equals(getValue())) criteria = " " + table + ".status = '" + VALID + "' and fossil = 0 ";
      } else {
        // specimen and sp (2026-10-30, review of #307): the same three sets for specimen queries. Until then they fell
        // through to the five counted statuses, and "valid" (valid extant) is the default list choice of geolocales and
        // curated projects. A specimen has its name's status (specimen.status) but no fossil column: fossil is the
        // name's (taxon.fossil), so the fossil condition looks the name up (fossilCondition).
        if (StatusSet.VALID_WITH_FOSSIL.equals(getValue())) criteria = " " + table + ".status = '" + VALID + "'";
        if (StatusSet.VALID_FOSSIL.equals(getValue())) criteria = " " + table + ".status = '" + VALID + "' and " + fossilCondition(table, 1) + " ";
        if (StatusSet.VALID_EXTANT.equals(getValue())) criteria = " " + table + ".status = '" + VALID + "' and " + fossilCondition(table, 0) + " ";
      }
        
      if (StatusSet.TYPE.equals(getValue())) {  // type
          if ("specimen".equals(table)) {
              s_log.warn("getCriteria() THIS SHOULDN'T HAPPEN? table:" + table + " value:" + getValue());
              //AntwebUtil.logShortStackTrace();
/*
	at org.calacademy.antweb.StatusSet.getCriteria(StatusSet.java:154)
	at org.calacademy.antweb.StatusSet.getCriteria(StatusSet.java:100)
	at org.calacademy.antweb.StatusSet.getAndCriteria(StatusSet.java:92)
	at org.calacademy.antweb.TaxaPage.fetchChildren(TaxaPage.java:98)
 */
          } else {
              //s_log.warn("ISSUE! How this invoked? Search form? table:" + table);
              //criteria = "type_status = type_status"; //"" type = 1 ";
              //criteria = "type_status is not null"; //"" type = 1 ";
              criteria = "1 = 1";
              // Kiko invoked this Jan 4 2021 but it is not known how.
              //  This: http://localhost/antweb/browse.do?genus=myrmecina&museumCode=MCZC&statusSet=type
              // Called from org.calacademy.antweb.search.AdvancedSearch.createInitialResults(AdvancedSearch.java:330)
          }
      }

      if (criteria != null) {
          //A.log("getCriteria() table:" + table + " criteria = " + criteria);
          return criteria;
      }

      String singleStatusCriteria = super.getCriteria(table);
      if (singleStatusCriteria != null) {
          //A.log("getCriteria() table:" + table + " singleStatusCriteria = " + criteria);
          return singleStatusCriteria;
      }

      //A.log("getCriteria() table:" + table + " default:" + defaultVal);
      return defaultVal;
    }

    /**
     * SQL: the specimen (alias table) is of a name with taxon.fossil = fossil (1 fossil, 0 extant). Specimens have no
     * fossil column of their own (is_fossil was never added); the name's row decides, as on the taxon lists.
     */
    static String fossilCondition(String table, int fossil) {
        return "exists (select 1 from taxon fst where fst.taxon_name = " + table + ".taxon_name and fst.fossil = " + fossil + ")";
    }

    /**
     * The criteria (with a leading " and ") of the rows of one rank on a list of this status set (2026-10-30, review of
     * #307): getAndCriteria(table) and, for the list choices Valid and Valid + morphotaxa (VALID_EXTANT, VALID_MORPHO),
     * the rule of the place page's number of that rank (listRankCondition), so that the list's header equals the number.
     * rank: the rank of the rows (subfamily, genus, species; other values: no rank condition).
     */
    public String getAndCriteria(String table, String rank) {
        return " and " + getCriteria(table) + listRankCondition(rank, table);
    }

    /**
     * The extra condition (" and ...", or "") of the rows of a rank on a list of this status set. Brian Fisher, 2026-10-30:
     * a list of the choices Valid and Valid + morphotaxa shows the rows the place page's number counts, by the same rules
     * (PlaceTotals): genus rows countsAsGenus (no placeholders such as "(myrmicinae)" or "amblyoponinae)", identified to
     * genus), subfamily rows countsAsSubfamily (valid, no placeholder "(formicidae)"), species rows identified to genus
     * (identifiedToGenus; their statuses are the choice's). All names, Fossil, the AntCat list's and Fossil Ants' sets
     * (other than valid extant, whose valid names are never placeholders) and the internal sets get none. Specimen
     * rows (table specimen or sp: TaxaPage's specimen half) carry a species-level name, so the rule is applied to their
     * genus or subfamily value (no parenthesis, identified to genus); their statuses are the choice's.
     */
    public String listRankCondition(String rank, String table) {
        String v = getValue();
        if (!(VALID_EXTANT.equals(v) || VALID_MORPHO.equals(v)) || rank == null) return "";
        boolean specimens = "specimen".equals(table) || "sp".equals(table);
        if (org.calacademy.antweb.home.PlaceTotals.GENUS.equals(rank)) {
            // a specimen row is a species-level name (its taxon_name is the species'): the rule on its genus value
            if (specimens) return " and " + org.calacademy.antweb.home.PlaceTotals.identifiedToGenus(table)
                + " and " + org.calacademy.antweb.home.PlaceTotals.genusWithoutParenthesis(table);
            return " and " + org.calacademy.antweb.home.PlaceTotals.countsAsGenus(table);
        }
        if (org.calacademy.antweb.home.PlaceTotals.SUBFAMILY.equals(rank)) {
            if (specimens) {
                String sf = "coalesce(" + table + ".subfamily, '')";
                return " and (" + sf + " not like '%(%' and " + sf + " not like '%)%')";
            }
            return " and " + org.calacademy.antweb.home.PlaceTotals.countsAsSubfamily(table);
        }
        if (org.calacademy.antweb.home.PlaceTotals.SPECIES.equals(rank) || "subspecies".equals(rank)) return " and " + org.calacademy.antweb.home.PlaceTotals.identifiedToGenus(table);
        return "";
    }

    public static String getCountables() {
      // Used by museums.  Museums do not use urecognized so as to be similar to taxonomic pages, imho -mark.
      // excludes synonym, unavailable uncategorized, etc...
      return " ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable')";  
        // With unrecognized, the museum counts are off.
        // Ex: pseudomyrmecinaesima ambigua erythrea has a genus (sima) which is a synonym.
    }

    public static boolean isAllAntwebAnts(String status) {
        List<String> statuses = Arrays.asList("valid", "unrecognized", "morphotaxon", "indetermined", "unidentifiable");

        return statuses.contains(status); 
    }    

    public static String getStatusSet(HttpServletRequest request, Overview overview) {
      String requestStatusSet = request.getParameter("statusSet");
      return getStatusSet(requestStatusSet, request, overview);
    }

    /**
     * The status choice of a list (taxonomicPage.do) or browse page (browse.do, images.do): the one in the address
     * (statusSet=..., mapped by listChoice), else the page's default (defaultChoice). Brian Fisher, 2026-10-30. The choice
     * applies to the page it was made on and is kept in that page's own links (tabs, sort links: they carry statusSet);
     * it is never kept in the session (2026-10-29: no carry-over from page to page). On the AntCat list (worldants) and
     * Fossil Ants one of their own choices (ownChoice: a value they do not offer gives their default).
     */
    public static String getStatusSet(String requestStatusSet, HttpServletRequest request, Overview overview) {
        String name = overview == null ? null : overview.getName();
        if (hasOwnStatusSets(name)) return ownChoice(requestStatusSet);
        String choice = listChoice(requestStatusSet);
        if (choice != null) return choice;
        return defaultChoice(overview);
    }

    /** The AntCat list (worldants) and Fossil Ants keep their own status sets (default valid names with fossils). */
    public static boolean hasOwnStatusSets(String overviewName) {
        return Project.WORLDANTS.equals(overviewName) || Project.FOSSILANTS.equals(overviewName);
    }

    /**
     * The choice of the AntCat list (worldants) or Fossil Ants for an address value: one of OWN_SET_CHOICES (case and
     * spaces do not matter), any other value (none, "valid", "validmorpho" or "fossil" from another list, a single
     * status) their default, Valid (with fossils). Until 2026-10-30 the value was used as it was, so "validmorpho" listed
     * the five counted statuses and "fossil" a single status, under labels such as "Validmorpho" (review of #307).
     */
    public static String ownChoice(String value) {
        if (value != null) {
            String v = value.trim().replaceAll("\\s+", " ");
            for (String c : OWN_SET_CHOICES) if (c.equalsIgnoreCase(v)) return c;
        }
        return VALID_WITH_FOSSIL;
    }

    /**
     * The list choice (LIST_CHOICES) of an address value, or null when there is none (no value, or one not known: the
     * page's default applies). Case and spaces do not matter. Old values of before 2026-10-30 map to the nearest choice:
     *   valid, valid extant, Valid (with fossils)            -> valid
     *   validmorpho, all determined                          -> validmorpho
     *   all, all indetermined, complete, type, worldants,
     *   a single status (morphotaxon, indetermined, ...)     -> all
     *   fossil, valid fossil                                 -> fossil
     * "type" never selected type taxa (its criteria were "1 = 1", every name); type specimens are found by search
     * (types=on) and the list's type column (orderBy=type), which do not use statusSet.
     */
    public static String listChoice(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (v.isEmpty()) return null;
        switch (v) {
            case LIST_VALID: case VALID_EXTANT: case "valid (with fossils)": case "valid species":
                return LIST_VALID;
            case LIST_VALID_MORPHO: case ALL_DETERMINED: case VALID_MORPHO:
                return LIST_VALID_MORPHO;
            case LIST_ALL: case ALL_INDETERMINED: case COMPLETE: case TYPE: case WORLDANTS: case ALL_NAMES:
                return LIST_ALL;
            case LIST_FOSSIL: case VALID_FOSSIL: case "fossil species":
                return LIST_FOSSIL;
            default:
                // a single status chosen on its own (the old "more options"): all names, the nearest choice
                if (getAllStatuses().contains(v)) return LIST_ALL;
                return null;
        }
    }

    /** The kind of place of an overview for its default choice: PlaceTotals.GEOLOCALE, BIOREGION, MUSEUM, PROJECT or null. */
    public static String placeType(Overview overview) {
        if (overview instanceof Geolocale) return org.calacademy.antweb.home.PlaceTotals.GEOLOCALE;
        if (overview instanceof Bioregion) return org.calacademy.antweb.home.PlaceTotals.BIOREGION;
        if (overview instanceof Museum) return org.calacademy.antweb.home.PlaceTotals.MUSEUM;
        if (overview instanceof Project) return org.calacademy.antweb.home.PlaceTotals.PROJECT;
        return null;
    }

    public static String defaultChoice(Overview overview) {
        return defaultChoice(placeType(overview), overview == null ? null : overview.getName());
    }

    /**
     * The choice a list opens with when the address names none. Brian Fisher, 2026-10-30 (replaces the rule of
     * 2026-10-29, "every counted status everywhere"):
     *   country, adm1, region, subregion (geolocales)     Valid species
     *   bioregion, museum                                 Valid species + morphospecies
     *   curated project                                   Valid species
     *   AntWeb-wide list (allantwebants), other pages     Valid species + morphospecies
     *   AntCat list (worldants), Fossil Ants              Valid (with fossils), their own set (as before)
     */
    public static String defaultChoice(String placeType, String overviewName) {
        if (hasOwnStatusSets(overviewName)) return VALID_WITH_FOSSIL;
        if (org.calacademy.antweb.home.PlaceTotals.GEOLOCALE.equals(placeType)) return LIST_VALID;
        if (org.calacademy.antweb.home.PlaceTotals.PROJECT.equals(placeType) && !Project.ALLANTWEBANTS.equals(overviewName)) return LIST_VALID;
        return LIST_VALID_MORPHO;
    }

    /**
     * The StatusSet whose criteria a list uses for getStatusSet's value on a page of that overview: on the AntCat list
     * and Fossil Ants the set of their choice (ownChoice), elsewhere the choice's.
     */
    public static StatusSet listStatusSet(String choice, String overviewName) {
        if (hasOwnStatusSets(overviewName)) return new StatusSet(ownChoice(choice));
        return listStatusSet(choice);
    }

    /** The StatusSet whose criteria a list of this choice uses (getStatusSet's value); other values as they are. */
    public static StatusSet listStatusSet(String choice) {
        if (LIST_VALID.equals(choice)) return new StatusSet(VALID_EXTANT);
        if (LIST_VALID_MORPHO.equals(choice)) return new StatusSet(VALID_MORPHO);
        if (LIST_ALL.equals(choice)) return new StatusSet(ALL_NAMES);
        if (LIST_FOSSIL.equals(choice)) return new StatusSet(VALID_FOSSIL);
        return new StatusSet(choice);
    }

    public static boolean isListChoice(String value) {
        return Arrays.asList(LIST_CHOICES).contains(value);
    }

    /** The choices the selector of a list offers (worldants and Fossil Ants: their own). */
    public static String[] choicesFor(String overviewName) {
        return hasOwnStatusSets(overviewName) ? OWN_SET_CHOICES : LIST_CHOICES;
    }

    /**
     * The selector's label of a choice for a list of rank (the rank of the rows: species, genus, subfamily). Species:
     * Valid species, Valid species + morphospecies, All names, Fossil species; genus: Valid genera, Valid genera +
     * morphogenera, All names, Fossil genera; subfamily: Valid subfamilies, Valid subfamilies, extant and fossil (only
     * when fossilSubfamilies; otherwise Valid subfamilies), All names, Fossil subfamilies. The AntCat list and Fossil
     * Ants: their choice (ownChoice), first letter capital.
     */
    public static String choiceLabel(String choice, String rank, String overviewName) {
        return choiceLabel(choice, rank, overviewName, true);
    }

    public static String choiceLabel(String choice, String rank, String overviewName, boolean fossilSubfamilies) {
        if (hasOwnStatusSets(overviewName)) return ownLabel(ownChoice(choice));
        return choiceLabel(choice, rank, fossilSubfamilies);
    }

    /** A value of the AntCat list's or Fossil Ants' own sets as the selector shows it: the value, first letter capital. */
    static String ownLabel(String value) {
        if (value == null || value.isEmpty()) return "";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    public static String choiceLabel(String choice, String rank) {
        return choiceLabel(choice, rank, true);
    }

    public static String choiceLabel(String choice, String rank, boolean fossilSubfamilies) {
        String r = rank == null ? "species" : rank.toLowerCase(Locale.ROOT);
        String plural;
        String morpho;
        if ("genus".equals(r) || "genera".equals(r)) { plural = "genera"; morpho = "morphogenera"; }
        else if ("subfamily".equals(r) || "subfamilies".equals(r)) { plural = "subfamilies"; morpho = null; }
        else { plural = "species"; morpho = "morphospecies"; }
        if (LIST_VALID.equals(choice)) return "Valid " + plural;
        // subfamilies (2026-10-30): the choice lists the rows of the Subfamilies number, valid subfamilies, extant and fossil
        // ("extant and fossil" only when the place has a fossil one, fossilSubfamilies)
        if (LIST_VALID_MORPHO.equals(choice)) return morpho == null ? "Valid " + plural + subfamilySuffix(fossilSubfamilies) : "Valid " + plural + " + " + morpho;
        if (LIST_ALL.equals(choice)) return "All names";
        if (LIST_FOSSIL.equals(choice)) return "Fossil " + plural;
        return ownLabel(choice);
    }

    /**
     * The header of a list (h2 display_count): what it shows, its number first. count: the rows listed; pluralRank: the
     * rows' rank as the page names it (Species, Genera, Subfamilies, Specimens); speciesTotal: the place page's Species /
     * subspecies / morphospecies (Valid species of a species list name it: "2,672 Valid species (of 6,960 species and
     * morphospecies)"), or a negative number when not known. Examples: "6,960 Species (valid species and
     * morphospecies)", "116 Genera (valid genera and morphogenera)", "7,215 Species (all names)", "12 Fossil species".
     * A value that is not a list choice (worldants, Fossil Ants) keeps the old form "N Valid extant Species". The number
     * is not escaped HTML (plain text: the JSP escapes it).
     */
    public static String listHeading(String choice, int count, String pluralRank, String singularRank, int speciesTotal,
            String overviewName) {
        return listHeading(choice, count, pluralRank, singularRank, speciesTotal, overviewName, true);
    }

    /** fossilSubfamilies: a subfamily list of Valid + morphotaxa says "extant and fossil" only when true (FOSSIL_SUBFAMILIES_ATTR). */
    public static String listHeading(String choice, int count, String pluralRank, String singularRank, int speciesTotal,
            String overviewName, boolean fossilSubfamilies) {
        // the AntCat list and Fossil Ants: their own choice (ownChoice), in the old form ("N Valid (with fossils) Species")
        // (choice null: a species' specimens, no status words)
        if (hasOwnStatusSets(overviewName) && choice != null) return listHeading("own:" + ownChoice(choice), count, pluralRank, singularRank, -1);
        return listHeading(choice, count, pluralRank, singularRank, speciesTotal, fossilSubfamilies);
    }

    public static String listHeading(String choice, int count, String pluralRank, String singularRank, int speciesTotal) {
        return listHeading(choice, count, pluralRank, singularRank, speciesTotal, true);
    }

    public static String listHeading(String choice, int count, String pluralRank, String singularRank, int speciesTotal,
            boolean fossilSubfamilies) {
        String plural = pluralRank == null ? "" : pluralRank;
        String single = singularRank == null ? plural : singularRank;
        String noun = count == 1 ? single : plural;
        String num = count <= 0 ? "No" : String.format(Locale.US, "%,d", count);
        String lower = noun.toLowerCase(Locale.ROOT);
        boolean speciesRows = "species".equals(lower);
        boolean generaRows = "genera".equals(lower) || "genus".equals(lower);
        if (LIST_VALID.equals(choice)) {
            String h = num + " Valid " + lower;
            if (speciesRows && speciesTotal >= 0) h += " (of " + String.format(Locale.US, "%,d", speciesTotal) + " species and morphospecies)";
            return h;
        }
        if (LIST_VALID_MORPHO.equals(choice)) {
            if (speciesRows) return num + " " + noun + " (valid species and morphospecies)";
            if (generaRows) return num + " " + noun + " (valid genera and morphogenera)";
            // other rows (subfamilies): since 2026-10-30 the rows of the Subfamilies number (PlaceTotals.countsAsSubfamily)
            if ("subfamilies".equals(lower) || "subfamily".equals(lower)) return num + " " + noun + " (valid subfamilies" + subfamilySuffix(fossilSubfamilies) + ")";
            return num + " " + noun + " (valid names and morphotaxa)";
        }
        if (LIST_ALL.equals(choice)) return num + " " + noun + " (all names)";
        if (LIST_FOSSIL.equals(choice)) return num + " Fossil " + lower;
        if (choice != null && choice.startsWith("own:")) choice = choice.substring(4);
        String statusStr = (choice == null || ALL.equals(choice)) ? "" : ownLabel(choice) + " ";
        return num + " " + statusStr + noun;
    }

    /**
     * The header of a list's images view (taxonomicPage.do?images=true, h2 display_count): how many of the listed rows are
     * imaged, out of the rows listed, in the words of the same choice as the list's header (listHeading), so the two
     * agree (Brian Fisher, 2026-10-30; until then "N Species Imaged (out of M Total)" did not say which names). Examples:
     * "40 Valid species imaged (out of 98 valid species)", "116 Species imaged (out of 140 valid species and
     * morphospecies)", "80 Genera imaged (out of 116 valid genera and morphogenera)", "5 Species imaged (out of 7 names,
     * all statuses)", "3 Fossil species imaged (out of 12 fossil species)". The AntCat list and Fossil Ants: their own
     * value ("40 Valid Species imaged (out of 98 valid species)"). Numbers with thousands separators, 0 as "0". Plain
     * text: the JSP escapes it.
     */
    public static String imagedHeading(String choice, int imaged, int total, String pluralRank, String singularRank,
            String overviewName) {
        return imagedHeading(choice, imaged, total, pluralRank, singularRank, overviewName, true);
    }

    /** fossilSubfamilies: as listHeading's. choice null (a species' specimens): "N Specimens imaged (out of M specimens)". */
    public static String imagedHeading(String choice, int imaged, int total, String pluralRank, String singularRank,
            String overviewName, boolean fossilSubfamilies) {
        if (hasOwnStatusSets(overviewName) && choice != null) choice = "own:" + ownChoice(choice);
        return imagedHeading(choice, imaged, total, pluralRank, singularRank, fossilSubfamilies);
    }

    public static String imagedHeading(String choice, int imaged, int total, String pluralRank, String singularRank) {
        return imagedHeading(choice, imaged, total, pluralRank, singularRank, true);
    }

    public static String imagedHeading(String choice, int imaged, int total, String pluralRank, String singularRank,
            boolean fossilSubfamilies) {
        String plural = pluralRank == null ? "" : pluralRank;
        String single = singularRank == null ? plural : singularRank;
        String noun = imaged == 1 ? single : plural;
        String lower = noun.toLowerCase(Locale.ROOT);
        String pluralLower = plural.toLowerCase(Locale.ROOT);
        String totalNoun = (total == 1 ? single : plural).toLowerCase(Locale.ROOT);
        boolean speciesRows = "species".equals(pluralLower);
        boolean generaRows = "genera".equals(pluralLower);
        String num = String.format(Locale.US, "%,d", imaged);
        String of = " (out of " + String.format(Locale.US, "%,d", total) + " ";
        if (LIST_VALID.equals(choice)) return num + " Valid " + lower + " imaged" + of + "valid " + totalNoun + ")";
        if (LIST_VALID_MORPHO.equals(choice)) {
            if (speciesRows) return num + " " + noun + " imaged" + of + "valid species and morphospecies)";
            if (generaRows) return num + " " + noun + " imaged" + of + "valid genera and morphogenera)";
            if ("subfamilies".equals(pluralLower)) return num + " " + noun + " imaged" + of + "valid subfamilies" + subfamilySuffix(fossilSubfamilies) + ")";
            return num + " " + noun + " imaged" + of + "valid names and morphotaxa)";
        }
        if (LIST_ALL.equals(choice)) return num + " " + noun + " imaged" + of + (total == 1 ? "name" : "names") + ", all statuses)";
        if (LIST_FOSSIL.equals(choice)) return num + " Fossil " + lower + " imaged" + of + "fossil " + totalNoun + ")";
        if (choice != null && choice.startsWith("own:")) choice = choice.substring(4);
        String status = (choice == null || ALL.equals(choice)) ? "" : ownLabel(choice) + " ";
        return num + " " + status + noun + " imaged" + of + status.toLowerCase(Locale.ROOT) + totalNoun + ")";
    }

    /** ", extant and fossil" when the subfamilies of the choice Valid + morphotaxa include a fossil one, else "". */
    static String subfamilySuffix(boolean fossilSubfamilies) {
        return fossilSubfamilies ? ", extant and fossil" : "";
    }

    public static String getAllAntwebClause() {
       return " status in ('morphotaxon', 'indetermined', 'unrecognized')";
    }

	public static String getStatusSetSize(HttpServletRequest request) {
        String statusSetSize = null;
        HttpSession session = request.getSession();
        statusSetSize = (String) session.getAttribute("statusSetSize");
        if (request.getParameter("statusSetSize") != null) {
          statusSetSize = request.getParameter("statusSetSize");
        }
        if (statusSetSize == null) statusSetSize = "min";
        session.setAttribute("statusSetSize", statusSetSize);
        return statusSetSize;
    }
	
}
