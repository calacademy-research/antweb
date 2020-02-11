package org.calacademy.antweb;

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
/** Class Taxon keeps track of the information about a specific taxon */
public class TaxaPage implements Serializable {

    private static Log s_log = LogFactory.getLog(TaxaPage.class);

	private String rank;
	private ArrayList<Taxon> children;
	private Connection connection;
	private String browserParams;
	private int childrenCount;
	private String pluralRank;
	
    private Overview overview;

	private int limit;  // This allows a throttling of the results for testing.
	
	private boolean isWithSpecimen = false;
	public boolean isWithSpecimen() { return isWithSpecimen; }


    private String statusSetStr = null;
    private String statusSetSize = null;
    

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
    	A.log("setChildren(ArrayLIst) children:" + children);
		this.children = children;
	}

	public void setChildren() throws SQLException {
	    // Called from EOL
		fetchChildren(false, true, false, Caste.DEFAULT, new StatusSet()); //, null);
	}

/*
    // deprecate!
 	public void fetchChildren(boolean withImages, boolean withTaxa, boolean withSpecimen, StatusSet statusSet) throws SQLException 
	{
	  fetchChildren(withImages, withTaxa, withSpecimen, Caste.DEFAULT, statusSet);
    }
*/
	public void fetchChildren(boolean withImages, boolean withTaxa, boolean withSpecimen, String caste, StatusSet statusSet) //, String orderBy) 
	  throws SQLException 
	{
	    Overview overview = getOverview();  // Why not a parameter?
	
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
	   	    taxaQuery += ", genus, species, subspecies, rank";
	   	  }
          	      
  	      taxaQuery += " from taxon";
  	      
          taxaQuery += overview.getFetchChildrenClause();

          taxaQuery += " and family = 'formicidae'";				

		  if (!"species".equals(rank)) {
		    taxaQuery += " and rank = '" + rank + "' ";			
		  } else {
		    taxaQuery += " and rank in ('species', 'subspecies')";
          }
          taxaQuery += statusSet.getAndCriteria();

          //A.log("FetchChildren() overview:" + getOverview().getClass() + " query:" + taxaQuery);  	      
        
          fetchChildrenQuery = taxaQuery;
        }          
        //A.log("fetchChildren() withSpecimen:" + withSpecimen + " overview:" + getOverview().getClass());        

        if (withSpecimen) {
          if (taxaQuery != null) fetchChildrenQuery += " union ";

          // This will add in specimen derived data.
          specimenQuery = "select distinct s.family as family, s.subfamily as subfamily";

	      if (rank.equals("genus")) {
		    specimenQuery += ", s.genus as genus ";
    	  }
  	      if (rank.equals("species")) {
	   	    specimenQuery += ", s.genus, s.species, s.subspecies, IF(subspecies is null ,'species','subspecies') as rank";
	      }          
          
          specimenQuery += " from specimen s ";
          
          if (getOverview() instanceof Country) {
            specimenQuery += " where country = '" + getOverview() + "'";
          }
          if (getOverview() instanceof Adm1) {
            specimenQuery += " where adm1 = '" + getOverview() + "'";
          }

          if (getOverview() instanceof Project) {
            specimenQuery += " where ( country = '" + ((Project) getOverview()).getTitle() + "'"
              + " or adm1 = '" + ((Project) getOverview()).getTitle() + "' )" ;
          }

          fetchChildrenQuery += specimenQuery;
        }

//        if (orderBy != null) {
//          fetchChildrenQuery += " order by " + orderByPhrase;
//        } else {
		  if (rank.equals("species")) {
			  fetchChildrenQuery += " order by genus, species, subspecies";
		  } else {
			  fetchChildrenQuery += " order by " + rank;
		  }
//        }						

        if (getLimit() != 0) {
            fetchChildrenQuery += " limit " + getLimit();
        }        
					
        Statement stmt = null;
        ResultSet rset = null;
        try {
        
            TaxonDb taxonDb = new TaxonDb(connection);
            
            stmt = DBUtil.getStatement(getConnection(), "fetchChildren()"); 
            rset = stmt.executeQuery(fetchChildrenQuery);

            A.log("fetchChidren() query:" + fetchChildrenQuery);
        				
    		long again = new java.util.Date().getTime();

			// A.log("fetchChildren(5) overview:" + overview + " rank:" + rank + " caste:" + caste + " withImages:" + withImages + " taxaQuery:" + fetchChildrenQuery);
			//A.log("fetchChildren() statusSetValue:" + statusSet.getValue() + " statusSet:" + statusSet.getStatusSets());
				
			now = again;
				
			while (rset.next()) {
				Taxon child = null;

				if ("family".equals(rank)) {
					child = new Family();
					child.setFamily(rset.getString("family"));
					child.setRank(rank);
				} else if ("subfamily".equals(rank)) {
					child = new Subfamily();
					//child.setName(rset.getString("subfamily"));
					child.setRank(rank);
					child.setSubfamily(rset.getString("subfamily"));
				} else if ("genus".equals(rank)) {
					child = new Genus();
					//child.setName(rset.getString("genus"));
					child.setRank(rank);
					child.setSubfamily(rset.getString("subfamily"));
					child.setGenus(rset.getString("genus"));
				} else if ("species".equals(rank)) {
					String selectedRank = (rset.getString("rank"));
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
				}

                if (child == null) {
                  s_log.warn("fetchChildren() not found for query:" + fetchChildrenQuery);
                } else {

                  String projectName = null;
                
                  //if (getOverview().getProject() != null) projectName = getOverview().getProject().getName();
                  child.generateBrowserParams(overview);
                
                  child.setConnection(connection);
                  child.setPrettyName();

                  //A.log("fetchChildren() details? child:" + child);                                        	
                  // Here we populate the fossil, author_date, status, type, and default_specimen fields in a separate query.
                  child.setDetails(withImages);

                  if (withImages) {
                    child.setImages(getOverview(), caste); // Will be overriden?
                    //A.log("fetchChildren(5) withImages:" + withImages + " caste:" + caste + " overview:" + getOverview() + " child:" + child.getClass() + " imageCount:" + child.getImageCount() + " images:" + child.getImages());
                  }

                  child.initTaxonSet(getOverview());   

                  //A.log("fetchChildren() projTaxon:" + child.getProjTaxon().toString());                                        
                  //if (child.getImageCount() < 4) A.log("fetchChildren() class:" + child.getClass() + " withImages:" + withImages + " imageCount:" + child.getImageCount() + " child:" + child);                                        

                  child.setConnection(null);

                  theseChildren.add(child);
                }
			}
			again = new java.util.Date().getTime(); 
			now = new java.util.Date().getTime();
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
	}

	public ArrayList<Taxon> getChildren() {
		return children;
	}

/*
    private ArrayList<String> nonImagedTaxa = null;
    
    public ArrayList<String> getNonImagedTaxa() {
      return this.nonImagedTaxa;
    }
// Only for Geolocale right now.
    public void fetchNonImagedTaxa(ArrayList<Taxon> childrenList) {

	  A.log("fetchNonImagedTaxa() childrenList.size:" + childrenList.size());


      ArrayList<String> taxa = new ArrayList<String>();

      Statement stmt = null;
      ResultSet rset = null;
      String rankClause = Rank.getRankClause(getRank());
      Geolocale geolocale = (Geolocale) getOverview();

      String query = "select t.taxon_name from geolocale g, geolocale_taxon gt, taxon t "
      + " where g.id = gt.geolocale_id and gt.taxon_name = t.taxon_name "
      + " and g.id = " + geolocale.getId()
      + " and " + rankClause;

        int i = 0;
        try {
            stmt = DBUtil.getStatement(getConnection(), "fetchNonImagedTaxa()");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
              ++i;
              String taxonName = rset.getString("taxon_name");
              
              boolean contains = false;
              for (Taxon taxon : childrenList) {
                if (taxonName.equals(taxon.getTaxonName())) {
                  contains = true;
                  break;
                }
              }
              A.log("fetchNonImagedTaxa() i:" + i + " contains:" + contains + " taxonName:" + taxonName + " childrenList.size:" + childrenList.size());
              if (!contains) taxa.add(taxonName);
            }
        } catch (SQLException e) {
            s_log.error("fetchNonImagedTaxa() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "this", "fetchNonImagedTaxa()");
        }

        //A.log("fetchNonImagedTaxa() childrenList.size():" + childrenList.size() + " query.size:" + i + " query:" + query);

        this.nonImagedTaxa = taxa;
    }
*/

/*
	protected boolean notBlank(String theTerm) {

		boolean itIsNotBlank = true;
		if ((theTerm == null)
			|| (theTerm.length() <= 0)
			|| (theTerm.equals("null"))
			|| (theTerm.equals("NULL"))) {
			itIsNotBlank = false;
		}

		return itIsNotBlank;
	}
*/
	protected void finalize() throws Throwable {
		super.finalize();
		if (children != null) {
			Iterator childIter = children.iterator();
			Taxon thisChild = null;
			while (childIter.hasNext()) {
				thisChild = (Taxon) childIter.next();
				thisChild.finalize();
			}
			children.clear();
		}
		connection = null;
	}

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
        /*
         else {
          if (getProject() != null) {
            params += "&project=" + ProjectMgr.getUseName(getProject());
          }         
        } */
        return params;   
    }

	public String getPluralRank() {
	    return Rank.getPluralRank(rank);
	}

	public void setPluralRank(String pluralRank) {
		this.pluralRank = pluralRank;
	}

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
	
    public Overview getOverview() {
      return overview;
    }
    public void setOverview(Overview overview) {
      this.overview = overview;
    }
    
	public String getRank() {
		return rank;
	}

	public void setRank(String rank) {
		this.rank = rank;
	}

	public Connection getConnection() {
		return connection;
	}

	public void setConnection(Connection connection) {
		this.connection = connection;
	}

    public void setLimit(int limit) {
      this.limit = limit;
    }    
    public int getLimit() {
      return this.limit;
    }

}
