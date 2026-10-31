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

/** Class Taxon keeps track of the information about a specific taxon */
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
  	      String taxaQuery = "select taxon.family as family, taxon.subfamily as subfamily ";
				
	      if (rank.equals("genus")) {
	      taxaQuery += ", taxon.genus as genus ";
  	      }
	      if (rank.equals("species")) {
	   	    taxaQuery += ", genus, species, subspecies, taxarank";
	   	  }
          	      
  	      taxaQuery += " from taxon";
  	      
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
		String query = taxaQuery(overview, "subfamily", true, StatusSet.listStatusSet(StatusSet.LIST_VALID_MORPHO))
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

        // Specimen taxa are filtered by country, adm1 or project only. For other places (bioregion, region,
        // museum) the specimen half had no place filter and listed every taxon in AntWeb, so it is left out.
        if (withSpecimen && !(overview instanceof Country || overview instanceof Adm1 || overview instanceof Project)) withSpecimen = false;
        isWithSpecimen = withSpecimen;

        String fetchChildrenQuery = "";
	    String taxaQuery = null;
        String specimenQuery = null;

        String orderByPhrase = "";
        String taxonOrderByPhrase = "";
          
        if (withTaxa) {
          taxaQuery = taxaQuery(overview, rank, withFossil, statusSet);
          fetchChildrenQuery = taxaQuery;
          s_log.debug("FetchChildren() overview:" + overview.getClass() + " query:" + taxaQuery);
        }
        //A.log("fetchChildren() withSpecimen:" + withSpecimen + " overview:" + overview.getClass());

        if (withSpecimen) {
          if (taxaQuery != null) fetchChildrenQuery += " union ";

          // This will add in specimen derived data.
          specimenQuery = "select distinct specimen.family as family, specimen.subfamily as subfamily";

	      if (rank.equals("genus")) {
		    specimenQuery += ", specimen.genus as genus ";
    	  }
  	      if (rank.equals("species")) {
	   	    specimenQuery += ", specimen.genus, specimen.species, specimen.subspecies, IF(subspecies is null ,'species','subspecies') as taxarank";
	      }          
          
          // Ant specimens only: non-ant specimens are found by search, not listed on place pages (2026-10-21).
          // alias "specimen": the status criteria (StatusSet.getCriteria) take the table name specimen or sp
          specimenQuery += " from specimen where" + org.calacademy.antweb.home.SpecimenDb.getAntSpecimenCriteria("specimen");
          if (rank.equals("species")) specimenQuery += " and " + org.calacademy.antweb.home.PlaceTotals.identifiedToGenus("specimen");
          // 2026-10-30 (review of #307): the specimen half takes the list's status choice too (it took none, so a list of
          // Valid species listed the names of every specimen), with the same rank rule as the taxa above.
          specimenQuery += statusSet.getAndCriteria("specimen", rank);
          // the specimens' names that count as species (2026-10-28, as the taxa above)
          if (rank.equals("species") && hidesNonSpecies(statusSet)) specimenQuery += " and " + org.calacademy.antweb.home.PlaceTotals.speciesStatusCondition("specimen");
          
          if (overview instanceof Country) {
            specimenQuery += " and country = " + DBUtil.sqlQuote(overview);
          }
          if (overview instanceof Adm1) {
            specimenQuery += " and adm1 = " + DBUtil.sqlQuote(overview);
          }

          if (overview instanceof Project) {
            specimenQuery += " and ( country = " + DBUtil.sqlQuote(overview.getTitle())
              + " or adm1 = " + DBUtil.sqlQuote(overview.getTitle()) + " )" ;
          }

          fetchChildrenQuery += specimenQuery;
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

	private PlaceTaxonNotes.OtherNames otherNames;

	/** The place's specimens of names not on its species list, by genus (genus lists, 2026-10-30; PlaceTaxonNotes). */
	public void findOtherNames(Connection connection, Overview overview) {
		try {
			otherNames = PlaceTaxonNotes.loadOtherNames(connection, overview, null, null);
		} catch (SQLException | RuntimeException e) {
			s_log.warn("findOtherNames() overview:" + overview + " e:" + e);
			otherNames = null;
		}
	}

	public PlaceTaxonNotes.OtherNames getOtherNames() {
		return otherNames;
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
