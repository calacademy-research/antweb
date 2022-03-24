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

import javax.servlet.http.*;

/** Class Taxon keeps track of the information about a specific taxon */
public class TaxaPage implements Serializable {

    private static Log s_log = LogFactory.getLog(TaxaPage.class);

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
	    ArrayList<Taxon> theseChildren = new ArrayList();

        isWithSpecimen = withSpecimen;

        String fetchChildrenQuery = "";
	    String taxaQuery = null;
        String specimenQuery = null;

        String orderByPhrase = "";
        String taxonOrderByPhrase = "";
          
        if (withTaxa) {
  	      taxaQuery = "select taxon.family as family, taxon.subfamily as subfamily ";
				
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
          }
          taxaQuery += statusSet.getAndCriteria();

 	      //A.log("clause:" + statusSet.getAndCriteria());

          if (!withFossil) {
			  taxaQuery += " and taxon.fossil = 0 ";
		  }

          fetchChildrenQuery = taxaQuery;
          s_log.debug("FetchChildren() overview:" + overview.getClass() + " query:" + taxaQuery);
        }
        //A.log("fetchChildren() withSpecimen:" + withSpecimen + " overview:" + overview.getClass());

        if (withSpecimen) {
          if (taxaQuery != null) fetchChildrenQuery += " union ";

          // This will add in specimen derived data.
          specimenQuery = "select distinct s.family as family, s.subfamily as subfamily";

	      if (rank.equals("genus")) {
		    specimenQuery += ", s.genus as genus ";
    	  }
  	      if (rank.equals("species")) {
	   	    specimenQuery += ", s.genus, s.species, s.subspecies, IF(subspecies is null ,'species','subspecies') as taxarank";
	      }          
          
          specimenQuery += " from specimen s ";
          
          if (overview instanceof Country) {
            specimenQuery += " where country = '" + overview+ "'";
          }
          if (overview instanceof Adm1) {
            specimenQuery += " where adm1 = '" + overview + "'";
          }

          if (overview instanceof Project) {
            specimenQuery += " where ( country = '" + ((Project) overview).getTitle() + "'"
              + " or adm1 = '" + ((Project) overview).getTitle() + "' )" ;
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
                        String selectedRank = (rset.getString("taxarank"));
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
		    s_log.error("fetchChildren() e:" + e + " fetchChildrenQuery:" + fetchChildrenQuery);
		    AntwebUtil.logStackTrace(e);
			throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "fetchChildren()");
        }	

		this.children = theseChildren;
		//A.log("fetchChildren size:" + children.size());
        return null;  // indicating success
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
