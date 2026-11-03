package org.calacademy.antweb;

import java.util.*;
import java.io.Serializable;
import java.sql.*;
import java.util.Date;

import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import jakarta.servlet.http.*;

/**
 * The rows of a list page (taxonomicPage.do, TaxaPageAction): the place's list of subfamilies, genera or species in one of
 * the four views (StatusSet, ListView; Brian Fisher, 2026-11-02). taxaQuery is the list's own rows; on country, adm1,
 * region and subregion lists the views Curated and All specimen names add the names of specimens collected there
 * (specimenNamesWhere, ListView.specimenNamesSet), so viewQuery is the view's whole set; ListView reads the same queries
 * for the genus rows' numbers. Tests: test.org.calacademy.antweb.ListViewTest, home.PlaceTaxonNotesDbTest,
 * tests/e2e/test_list_sources.py, test_status_choice.py.
 */
public class TaxaPage implements Serializable {

    private static final Log s_log = LogFactory.getLog(TaxaPage.class);

	private String rank;
	private ArrayList<Taxon> children;
//	private Connection connection;
	private String browserParams;
	private int childrenCount;
	private String pluralRank;

	private HttpServletRequest request;

    private Overview overview;

	private int limit;  // This allows a throttling of the results for testing.
	
	private boolean isWithSpecimen = false;
	public boolean isWithSpecimen() { return isWithSpecimen; }


    private String statusSetStr;
    private String statusSetSize;

	public int getChildrenCount() {
		if (children != null) {
			return children.size();
		} else {
			return -1;
		}
	}

	public void setChildrenCount(int childrenCount) {
		this.childrenCount = childrenCount;
	}

	public void setChildren(ArrayList<Taxon> children) {
    	s_log.debug("setChildren(ArrayLIst) children:" + children);
		this.children = children;
	}

	public void setChildren(Connection connection, Overview overview, String rank) throws SQLException {
	    // Called from EOL
		fetchChildren(connection, overview, rank, false, true, false, true, Caste.DEFAULT, new StatusSet());
	}

	/**
	 * The query of a list's taxa (the rows of the place's list of a rank, with the status choice's criteria; 2026-10-30:
	 * a static method so the database tests run the list's own query). rank: subfamily, genus or species.
	 */
	public static String taxaQuery(Overview overview, String rank, boolean withFossil, StatusSet statusSet) {
		return "select " + selectColumns(rank) + listWhere(overview, rank, withFossil, statusSet);
	}

	/** The columns fetchChildren reads, for rows of rank: family, subfamily (, genus) (, genus, species, subspecies, taxarank). */
	public static String selectColumns(String rank) {
		String cols = "taxon.family as family, taxon.subfamily as subfamily";
		if ("genus".equals(rank)) cols += ", taxon.genus as genus";
		if ("species".equals(rank)) cols += ", taxon.genus as genus, taxon.species as species, taxon.subspecies as subspecies, taxon.taxarank as taxarank";
		return cols;
	}

	/**
	 * The view's whole set of rows (Brian Fisher, 2026-11-02): the list's own rows (listWhere) and, where the view adds
	 * them, the names of the specimens collected in the place (specimenNamesWhere). select: the columns; extra: a
	 * condition added to both halves (" and ...", or "").
	 */
	public static String viewQuery(Overview overview, String rank, boolean withFossil, StatusSet statusSet, String select, String extra) {
		return viewQuery(overview, rank, withFossil, statusSet, select, extra, null);
	}

	/** The same, the specimen half on the names collected here given (specimenNamesWhere's collected; null: a subquery). */
	public static String viewQuery(Overview overview, String rank, boolean withFossil, StatusSet statusSet, String select, String extra,
			java.util.Collection<String> collected) {
		return viewQueryHere(overview, rank, withFossil, statusSet, select, extra, collected == null ? null : ListView.namedIn("taxon", collected));
	}

	/** The same, the specimen half on a condition (ListView.hereCondition: stored names or an IN list; null: a subquery). */
	public static String viewQueryHere(Overview overview, String rank, boolean withFossil, StatusSet statusSet, String select, String extra,
			String here) {
		String x = extra == null ? "" : extra;
		String q = "select " + select + listWhere(overview, rank, withFossil, statusSet) + x;
		String specimenWhere = specimenNamesWhereHere(overview, rank, withFossil, statusSet, here);
		if (specimenWhere != null) q += " union select " + select + specimenWhere + x;
		return q;
	}

	/**
	 * The second half of a view (" from taxon where ..."), or null when the view adds no specimen names here: on
	 * country, adm1, region and subregion lists (ListView.addsSpecimenNames) the names of the not red-flagged specimens
	 * collected there that the view takes (ListView.specimenNamesSet: Curated the curated morphospecies, All specimen
	 * names every counted status), not disputed there (a curator removed the name from the list: ListView.notDisputed).
	 * Genus and subfamily rows: the genus or subfamily of such names, All specimen names only (Curated's genus rows come
	 * from the list, ListView.GenusRows).
	 */
	public static String specimenNamesWhere(Overview overview, String rank, boolean withFossil, StatusSet statusSet) {
		return specimenNamesWhere(overview, rank, withFossil, statusSet, null);
	}

	/**
	 * The same; collected: the names of the specimens collected here (ListView.PlaceSpecimens, already read by the page),
	 * or null to select them with a subquery. Same rows either way (2026-11-02, speed: a country's specimens are found by
	 * "country = X or island_country = X", which no index serves, so each subquery scanned the specimen table; a genus
	 * list now scans it once, ListView.loadPlaceSpecimens, and passes the names).
	 */
	public static String specimenNamesWhere(Overview overview, String rank, boolean withFossil, StatusSet statusSet,
			java.util.Collection<String> collected) {
		return specimenNamesWhereHere(overview, rank, withFossil, statusSet, collected == null ? null : ListView.namedIn("taxon", collected));
	}

	/**
	 * The same; hereCondition: SQL that the name (taxon) was collected here (ListView.hereCondition: the stored names of
	 * place_specimen_name, or an IN list of names read), or null for a subquery of the specimen table. The view Not
	 * curated (2026-11-02) adds the counted names collected here that are not curated (ListView.specimenNamesSet).
	 */
	public static String specimenNamesWhereHere(Overview overview, String rank, boolean withFossil, StatusSet statusSet,
			String hereCondition) {
		StatusSet names = ListView.specimenNamesSet(statusSet);
		if (names == null || !ListView.addsSpecimenNames(overview)) return null;
		if (!"species".equals(rank) && !StatusSet.ALL_NAMES.equals(names.getValue())) return null;
		String here = hereCondition == null ? ListView.collectedHere("taxon", PlaceTaxonNotes.placeSpecimenCriteria(overview)) : hereCondition;
		String w = " from taxon where " + here + " and taxon.family = 'formicidae'";
		if ("species".equals(rank)) {
			w += " and taxon.taxarank in ('species', 'subspecies') and " + org.calacademy.antweb.home.PlaceTotals.identifiedToGenus("taxon")
				+ names.getAndCriteria("taxon", rank);
			if (hidesNonSpecies(names)) w += " and " + org.calacademy.antweb.home.PlaceTotals.speciesStatusCondition("taxon");
		} else {
			// the genus or subfamily of the names collected here (a name at genus rank too)
			w += " and taxon.taxarank in ('genus', 'species', 'subspecies')" + names.getAndCriteria("taxon", rank);
			if ("genus".equals(rank)) w += " and coalesce(taxon.genus, '') <> ''";
		}
		w += ListView.notDisputed(overview, "taxon");
		if (!withFossil) w += " and taxon.fossil = 0 ";
		return w;
	}

	/** The list's own rows of a view (" from taxon, <list> where ..."): the place's list, the view's criteria. */
	public static String listWhere(Overview overview, String rank, boolean withFossil, StatusSet statusSet) {
  	      String taxaQuery = " from taxon";
  	      
          taxaQuery += overview.getFetchChildrenClause();

          taxaQuery += " and family = 'formicidae'";				

		  if (!"species".equals(rank)) {
		    taxaQuery += " and taxarank = '" + rank + "' ";
		  } else {
		    taxaQuery += " and taxarank in ('species', 'subspecies')";
		    // Brian Fisher, 2026-10-22: a name not identified to genus is not a species; the species list and its
		    // header count leave it out, as the place's Species number does (PlaceTotals.identifiedToGenus). Its
		    // specimens still count in the place's Specimens.
		    taxaQuery += " and " + org.calacademy.antweb.home.PlaceTotals.identifiedToGenus("taxon");
          }
          // 2026-10-30 (review of #307): the choices Valid and Valid + morphotaxa list the rows of the place page's
          // number of the rank, by its rule (StatusSet.listRankCondition: countsAsGenus, countsAsSubfamily, species
          // identified to genus), so the list's header equals the number.
          taxaQuery += statusSet.getAndCriteria("taxon", rank);
          // Brian Fisher, 2026-10-28: only valid names and morphospecies are species. A species list of the internal
          // mixed sets (ALL, used by counts and the field guide) shows those, as the place's Species number counts them;
          // the specimens of the names identified to genus only are named under the list (GenusOnly). The list choice
          // "All names" (2026-10-30, StatusSet.ALL_NAMES) and a single status still list those names; the choices
          // Valid species and Valid species + morphospecies are filtered by their own criteria (StatusSet.listStatusSet).
          if ("species".equals(rank) && hidesNonSpecies(statusSet)) {
            taxaQuery += " and " + org.calacademy.antweb.home.PlaceTotals.speciesStatusCondition("taxon");
          }

 	      //A.log("clause:" + statusSet.getAndCriteria());

          if (!withFossil) {
			  taxaQuery += " and taxon.fossil = 0 ";
		  }

          return taxaQuery;
	}

	/**
	 * Whether the place's subfamilies of the choice Valid + morphotaxa (the rows of its Subfamilies number) include a
	 * fossil one. Brian Fisher, 2026-10-30: a subfamily list says "extant and fossil" only then (StatusSet.listHeading,
	 * choiceLabel, imagedHeading; request attribute StatusSet.FOSSIL_SUBFAMILIES_ATTR). The list's own query
	 * (taxaQuery) with a fossil row; browse.do's subfamilies (Family.setChildren) take the same clause and rule.
	 */
	public static boolean hasFossilSubfamilies(Connection connection, Overview overview) throws SQLException {
		String query = taxaQuery(overview, "subfamily", true, new StatusSet(StatusSet.VALID_MORPHO))
		    + " and taxon.fossil = 1 limit 1";
		Statement stmt = null;
		ResultSet rset = null;
		try {
			stmt = DBUtil.getStatement(connection, "hasFossilSubfamilies()");
			rset = stmt.executeQuery(query);
			return rset.next();
		} finally {
			DBUtil.close(stmt, rset, "hasFossilSubfamilies()");
		}
	}

	/**
	 * Sets StatusSet.FOSSIL_SUBFAMILIES_ATTR for a list of subfamilies (taxonomicPage.do, browse.do and images.do of the
	 * family). Not on the AntCat list and Fossil Ants (their own choices). On a database error the attribute is not set
	 * (the wording that names extant and fossil) and the page is shown.
	 */
	public static void setFossilSubfamilies(HttpServletRequest request, Connection connection, Overview overview) {
		if (overview == null || StatusSet.hasOwnStatusSets(overview.getName())) return;
		try {
			request.setAttribute(StatusSet.FOSSIL_SUBFAMILIES_ATTR, hasFossilSubfamilies(connection, overview));
		} catch (SQLException | RuntimeException e) {
			// the page is shown without the fossil-subfamily answer (full selector) rather than failing
			s_log.warn("setFossilSubfamilies() overview:" + overview + " e:" + e);
		}
	}

	public String fetchChildren(Connection connection, Overview overview, String rank, boolean withImages, boolean withTaxa, boolean withSpecimen, boolean withFossil, String caste, StatusSet statusSet)
	  throws SQLException 
	{
		if (overview.getName().equals("allantwebants") && "species".equals(rank)) {
          String message = "fetchChildren() Expensive query averted. overview:" + overview + " rank:" + rank + " withImages:" + withImages + " withTaxa:" + withTaxa
				  + " withSpecimen:" + withSpecimen + " withFossil:" + withFossil + " caste:" + caste + " statusSet:" + statusSet;
          message += " target:" + HttpUtil.getTarget(getRequest()) + " referrer:" + HttpUtil.getReferrerUrl(getRequest());
		  A.log(message);
          //AntwebUtil.logShortStackTrace();
			// at org.calacademy.antweb.TaxaPage.fetchChildren(TaxaPage.java:67)
			// at org.calacademy.antweb.TaxaPageAction.execute(TaxaPageAction.java:179)

			// TaxaPage - fetchChildren() Expensive query... all species of allantwebants. withImages:true withTaxa:true 
			//   withSpecimen:false withFossil:true caste:default statusSet:org.calacademy.antweb.StatusSet@3a25716b			
            // https://www.antweb.org/taxonomicPage.do?rank=species&images=true&countryName=Russia&adm1Name=Volgograd2121121121212.1
            return "Query not allowed.";
	    } else {
			//A.log("fetchChildren() overview:" + overview + " overview class:" + overview.getClass()+ "withImages:" + withImages + " withTaxa:" + withTaxa
			//		+ " withSpecimen:" + withSpecimen + " withFossil:" + withFossil + " caste:" + caste + " statusSet:" + statusSet);
		}
	
        long now = new java.util.Date().getTime();
	    ArrayList<Taxon> theseChildren = new ArrayList<>();

        // The view's rows (Brian Fisher, 2026-11-02): the list's rows and, on country, adm1, region and subregion lists in
        // the views Curated and All specimen names, the names of the specimens collected there (viewQuery). withSpecimen
        // (the old specimen=true, "Names from: Specimens collected here") is now the view All specimen names
        // (TaxaPageAction); the parameter is kept for the callers.
        String fetchChildrenQuery = "";
        if (withTaxa) {
          String specimenWhere = specimenNamesWhereHere(overview, rank, withFossil, statusSet, ListView.hereCondition("taxon", collectedPlaceSpecimens));
          isWithSpecimen = specimenWhere != null;
          fetchChildrenQuery = taxaQuery(overview, rank, withFossil, statusSet);
          if (specimenWhere != null) fetchChildrenQuery += " union select " + selectColumns(rank) + specimenWhere;
          s_log.debug("FetchChildren() overview:" + overview.getClass() + " query:" + fetchChildrenQuery);
        }

		if (rank.equals("species")) {
			fetchChildrenQuery += " order by genus, species, subspecies";
		} else {
			fetchChildrenQuery += " order by " + rank;
		}

        if (getLimit() != 0) {
            fetchChildrenQuery += " limit " + getLimit();
        }        


        Statement stmt = null;
        ResultSet rset = null;
        try {
        
            TaxonDb taxonDb = new TaxonDb(connection);
            
            stmt = DBUtil.getStatement(connection, "fetchChildren()");
            rset = stmt.executeQuery(fetchChildrenQuery);

            s_log.debug("fetchChidren() query:" + fetchChildrenQuery);
        				
    		long again = new Date().getTime();

			// A.log("fetchChildren(5) overview:" + overview + " rank:" + rank + " caste:" + caste + " withImages:" + withImages + " taxaQuery:" + fetchChildrenQuery);
			//A.log("fetchChildren() statusSetValue:" + statusSet.getValue() + " statusSet:" + statusSet.getStatusSets());
				
			now = again;


			while (rset.next()) {
				Taxon child = null;

                switch (rank) {
                    case "family":
                        child = new Family();
                        child.setFamily(rset.getString("family"));
                        child.setRank(rank);
                        break;
                    case "subfamily":
                        child = new Subfamily();
                        //child.setName(rset.getString("subfamily"));
                        child.setRank(rank);
                        child.setSubfamily(rset.getString("subfamily"));
                        break;
                    case "genus":
                        child = new Genus();
                        //child.setName(rset.getString("genus"));
                        child.setRank(rank);
                        child.setSubfamily(rset.getString("subfamily"));
                        child.setGenus(rset.getString("genus"));
                        break;
                    case "species":
                        String selectedRank = rset.getString("taxarank");
                        if ("species".equals(selectedRank)) {
                            child = new Species();
                        } else { // Then it is subspecies
                            child = new Subspecies();
                        }
                        child.setRank(selectedRank);
                        //child.setName(rset.getString("species"));
                        child.setSubfamily(rset.getString("subfamily"));
                        child.setGenus(rset.getString("genus"));
                        String species = rset.getString("species");
                        //if (species.contains("'")) s_log.warn("fetchChildren species with single quote:" + species);
                        child.setSpecies(species);
                        child.setSubspecies(rset.getString("subspecies"));

                        child.setIntroducedMap(TaxonPropMgr.getIntroducedMap(child.getTaxonName()));
                        break;
                }

                if (child == null) {
                  s_log.warn("fetchChildren() not found for query:" + fetchChildrenQuery);
                } else {
                  //if (overview.getProject() != null) projectName = overview.getProject().getName();
                  child.generateBrowserParams(overview);
                  child.setPrettyName();
                  //A.log("fetchChildren() details? child:" + child);                                        	
                  // Here we populate the fossil, author_date, status, type, and default_specimen fields in a separate query.
                  child.setDetails(connection, withImages);
                  if (withImages) {
                    child.setImages(connection, overview, caste); // Will be overriden?
                    //A.log("fetchChildren(5) withImages:" + withImages + " caste:" + caste + " overview:" + overview + " child:" + child.getClass() + " imageCount:" + child.getImageCount() + " images:" + child.getImages());
                  }
                  child.initTaxonSet(connection, overview);

                  //A.log("fetchChildren() projTaxon:" + child.getProjTaxon().toString());
                  //if (child.getImageCount() < 4) A.log("fetchChildren() class:" + child.getClass() + " withImages:" + withImages + " imageCount:" + child.getImageCount() + " child:" + child);

                  theseChildren.add(child);
                }
			}

			again = new Date().getTime();
			now = new Date().getTime();
			//s_log.error("basic query took " + (again-now) + " millis");

		} catch (SQLException e) {
            if (AntwebProps.isDevMode()) {
                s_log.error("execute() e:" + e + " fetchChildrenQuery:" + fetchChildrenQuery);
            }
		    //s_log.error("fetchChildren() e:" + e + " fetchChildrenQuery:" + fetchChildrenQuery + " requestInfo:" + HttpUtil.getShortRequestInfo(request));
		    //AntwebUtil.logStackTrace(e);
			throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "fetchChildren()");
        }	

		this.children = theseChildren;
		//A.log("fetchChildren size:" + children.size());
        return null;  // indicating success
	}

	/*
	 * Brian Fisher, 2026-10-22 (docs/numbers.md Q12): "Unimaged" always means no photo of the ant anywhere in AntWeb; a
	 * name whose specimens have only label photos is not imaged. The rule is StatisticsDb.antImagedNames (the API's
	 * /unimagedGeolocaleTaxa uses the same, imagedTaxaSql). Set by TaxaPageAction for isImaged=false; null otherwise.
	 */
	private java.util.Set<String> unimagedNames;

	/** The children with no photo of the ant anywhere in AntWeb (isImaged=false), or null when not asked for. */
	public java.util.Set<String> getUnimagedNames() {
		return unimagedNames;
	}

	/** Marks the children that have no photo of the ant anywhere in AntWeb (StatisticsDb.antImagedNames). */
	/**
	 * Whether a species list of this status set leaves out the names that are not species (2026-10-28): the internal
	 * sets that mix statuses (all, all determined, all indetermined, complete) and the list choice Valid species +
	 * morphospecies (StatusSet.VALID_MORPHO, 2026-10-30: its criteria are already the species statuses; named here so
	 * the rule is stated once for every set that mixes statuses). valid, valid extant, valid fossil, worldants, a single
	 * status and the list choice All names (StatusSet.ALL_NAMES, 2026-10-30) are left as they are.
	 */
	public static boolean hidesNonSpecies(StatusSet statusSet) {
		if (statusSet == null) return true;
		String v = statusSet.getValue();
		return v == null || StatusSet.ALL.equals(v) || StatusSet.ALL_DETERMINED.equals(v) || StatusSet.ALL_INDETERMINED.equals(v)
			|| StatusSet.COMPLETE.equals(v) || StatusSet.VALID_MORPHO.equals(v);
	}

	private org.calacademy.antweb.home.GenusOnly genusOnly;

	/** The place's specimens identified to genus only (genus and species lists, 2026-10-28). */
	public void findGenusOnly(Connection connection, Overview overview) {
		try {
			genusOnly = org.calacademy.antweb.home.GenusOnly.load(connection, overview);
		} catch (SQLException | RuntimeException e) {
			s_log.warn("findGenusOnly() overview:" + overview + " e:" + e);
			genusOnly = null;
		}
	}

	public org.calacademy.antweb.home.GenusOnly getGenusOnly() {
		return genusOnly;
	}

	private ListView.GenusRows genusRows;

	/**
	 * Genus and species lists of a place that selects specimens (2026-11-02, speed): the place's specimens by name, read
	 * once before fetchChildren (loadCollected) and used by its specimen half, findGenusRows and findPlaceSpecimens,
	 * instead of a scan per query. Genus lists take the names stored by the nightly count (place_specimen_name,
	 * ListView.genusListSpecimens; the specimen table only until the first count), species lists the specimen table.
	 */
	private ListView.PlaceSpecimens collectedPlaceSpecimens;

	/** Above this many names collected here (a region such as Africa), the specimen half keeps its subquery instead of a
	 *  long IN list (review of #316). */
	public static final int MAX_NAMED_IN = 5000;

	public void loadCollected(Connection connection, Overview overview, String rank) {
		if (!ListView.hasViews(overview) || !ListView.readsPlaceSpecimens(overview)) return;
		try {
			collectedPlaceSpecimens = Rank.GENUS.equals(rank) ? ListView.genusListSpecimens(connection, overview, null)
				: ListView.loadPlaceSpecimens(connection, overview, null, null);
		} catch (SQLException | RuntimeException e) {
			s_log.warn("loadCollected() overview:" + overview + " e:" + e);
			collectedPlaceSpecimens = null;
		}
	}

	/**
	 * Genus lists (Brian Fisher, 2026-11-02): the view's numbers of the genus rows (Species, Not curated, species imaged)
	 * and the rows the view shows (ListView.GenusRows.keep: in Valid and Curated a genus known here only from specimens identified to
	 * genus is left out). The children are filtered here, so the header counts the rows shown. On a database error the
	 * rows stay as the list has them, with their stored numbers.
	 */
	public void findGenusRows(Connection connection, Overview overview, String choice) {
		if (!ListView.hasViews(overview)) return;
		try {
			genusRows = ListView.loadGenusRows(connection, overview, choice, null, collectedPlaceSpecimens);
			if (children != null) children = genusRows.filter(children);
		} catch (SQLException | RuntimeException e) {
			s_log.warn("findGenusRows() overview:" + overview + " e:" + e);
			genusRows = null;
		}
	}

	public ListView.GenusRows getGenusRows() {
		return genusRows;
	}

	private ListView.SubfamilyRows subfamilyRows;

	/**
	 * Subfamily lists (B77, Brian Fisher, 2026-11-03): each subfamily row's Genera are the genus rows its browse page shows
	 * in the same view (ListView.SubfamilyRows), not the stored genus_count. On a database error the rows keep their stored
	 * numbers.
	 */
	public void findSubfamilyRows(Connection connection, Overview overview, String choice) {
		try {
			subfamilyRows = ListView.loadSubfamilyRows(connection, overview, choice);
		} catch (SQLException | RuntimeException e) {
			s_log.warn("findSubfamilyRows() overview:" + overview + " e:" + e);
			subfamilyRows = null;
		}
	}

	public ListView.SubfamilyRows getSubfamilyRows() {
		return subfamilyRows;
	}

	private ListView.PlaceSpecimens placeSpecimens;

	/**
	 * Species lists (2026-11-02): the place's specimens by name (red-flagged never counted), for the source icon
	 * (specimens trump literature) and the specimens of the rows added from specimens. Only the names that need it:
	 * the rows whose list entry is not from specimens and the rows not on the list (a row from specimens shows the
	 * specimen icon anyway). Null when the place selects none.
	 */
	public void findPlaceSpecimens(Connection connection, Overview overview) {
		java.util.List<String> names = new java.util.ArrayList<>();
		if (children != null) {
			for (Taxon child : children) {
				TaxonSet ts = child.getTaxonSet();
				if (ts == null || !ts.exists() || !Source.SPECIMEN.equals(Source.getSourceCanonical(ts.getSource()))) names.add(child.getTaxonName());
			}
		}
		if (names.isEmpty()) return;
		// the place's specimens already read for the list (loadCollected): no second query
		if (collectedPlaceSpecimens != null) {
			placeSpecimens = collectedPlaceSpecimens;
			return;
		}
		try {
			placeSpecimens = ListView.loadPlaceSpecimens(connection, overview, null, null, names);
		} catch (SQLException | RuntimeException e) {
			s_log.warn("findPlaceSpecimens() overview:" + overview + " e:" + e);
			placeSpecimens = null;
		}
	}

	public ListView.PlaceSpecimens getPlaceSpecimens() {
		return placeSpecimens;
	}

	public void findUnimaged(Connection connection) throws SQLException {
		java.util.Set<String> names = new java.util.HashSet<>();
		if (children != null) for (Taxon child : children) names.add(child.getTaxonName());
		java.util.Set<String> imaged = StatisticsDb.antImagedNames(connection, names);
		unimagedNames = new java.util.HashSet<>(names);
		unimagedNames.removeAll(imaged);
	}

	public ArrayList<Taxon> getChildren() {
		return children;
	}

	public String getBrowserParams() {
		return browserParams;
	}
	public void setBrowserParams(String rank, Overview overview) {
        browserParams = "rank=" + rank + "&" + overview.getParams();
	}
	/*
    public String getBrowserParams() {
      // ?subfamily=myrmicinae&genus=crematogaster&project=allantwebants&rank=genus&pr=i
        String params = "";
        params += "rank=" + getRank();
        params += getParams();
        return params;   
    }

    public String getParams() {
      // ?subfamily=myrmicinae&genus=crematogaster&project=allantwebants&rank=genus&pr=i
        String params = "";
        if (getOverview() != null) {
            params += "&" + getOverview().getParams();
        }
        return params;
    }
*/
/*
	public String getPluralRank() {
	    return Rank.getPluralRank(rank);
	}
	public void setPluralRank(String pluralRank) {
		this.pluralRank = pluralRank;
	}
*/
	public String getStatusSetStr() {
	    return statusSetStr;
	}
	public void setStatusSetStr(String statusSetStr) {
		this.statusSetStr = statusSetStr;
	}    

	public String getStatusSetSize() {
	    return statusSetSize;
	}
	public void setStatusSetSize(String statusSetSize) {
		this.statusSetSize = statusSetSize;
	}

	public HttpServletRequest getRequest() {
		return this.request;
	}
	public void setRequest(HttpServletRequest request) {
		this.request = request;
	}

	/*
	public String getRank() {
		return rank;
	}
	public void setRank(String rank) {
		this.rank = rank;
	}
*/
/*	public Connection getConnection() {
		return connection;
	}
	public void setConnection(Connection connection) {
		this.connection = connection;
	}
*/
    public void setLimit(int limit) {
      this.limit = limit;
    }    
    public int getLimit() {
      return this.limit;
    }

}
