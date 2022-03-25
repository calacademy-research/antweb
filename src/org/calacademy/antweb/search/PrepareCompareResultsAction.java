package org.calacademy.antweb.search;

import java.io.IOException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import java.sql.*;
import javax.sql.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class PrepareCompareResultsAction extends PrepareAction {

    private static final Log s_log = LogFactory.getLog(PrepareCompareResultsAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

		// Extract attributes we will need
		Locale locale = getLocale(request);
		HttpSession session = request.getSession();
		
        if (session.getAttribute("activeSession") == null) return mapping.findForward("sessionExpired");
        
		String resultRank = request.getParameter("resultRank");
        if (resultRank == null) {
          return mapping.findForward("error");
        }		
		
        String resultSetModifier = "unset";		
		GenericSearchResults results = null;
		String forwardString = "failure";
		if (ResultRank.SPECIMEN.equals(resultRank)) {
			results = (AdvancedSearchResults) session.getAttribute("advancedSearchResults");
			if (session.getAttribute("fullAdvancedSearchResults") != null) results.setResults((ArrayList)session.getAttribute("fullAdvancedSearchResults")); // added to fix session
            resultSetModifier = "(from " + results.getResults().size() + " total specimens)";
			forwardString = "advancedSearch";
        } else if (ResultRank.isTaxonRank(resultRank)) {
            // This set should show for all qualifying specimen's species.  
            /* Take the distinct set of species and get the ones that are imaged. */
            results = (AdvancedSearchResults) session.getAttribute("advancedSearchResults");
			if (session.getAttribute("fullAdvancedSearchResults") != null) results.setResults((ArrayList)session.getAttribute("fullAdvancedSearchResults")); // added to fix session
            request.setAttribute("resultRank", resultRank);            
            resultSetModifier = "";
            request.setAttribute("resultSetModifier", resultSetModifier);			
            forwardString = "advancedSearchByTaxon";
        }
		/*  else if (resultRank.equals("speciesSpecific")) {
         // This set would show only species with imaged in specimens within search criteria
            results = (AdvancedSearchResults) session.getAttribute("advancedSearchResults");
			if (session.getAttribute("fullAdvancedSearchResults") != null) results.setResults((ArrayList)session.getAttribute("fullAdvancedSearchResults")); // added to fix session
            //session.setAttribute("taxonList", ((AdvancedSearchResults) results).getSpeciesList());
            resultSetModifier = "(from " + results.getResults().size() + " imaged specimens)";
            request.setAttribute("resultRank", resultRank);            
            forwardString = "advancedSearchByTaxon";	
		} else if  (resultRank.equals("taxa")) {
			results = (SearchResults) session.getAttribute("searchResults");
			forwardString = "search";
		} */ else if (ResultRank.BAY_AREA.equals(resultRank)) {
			results = (BayAreaSearchResults) session.getAttribute("searchResults");
			forwardString = "bayAreaSearch";
		} else {
			String message = "Unsupported Result Rank for Prepare Compare Results:" + resultRank;
			s_log.error("execute() " + message);
			request.setAttribute("message", message);
		    return mapping.findForward("message");
		}
		
		
		 /* else if (resultRank.equals("collection")) {
			results = (SearchResults) ((Collection) session.getAttribute("collection")).getSpecimens();
			forwardString = "collectionSearch";
		}*/

        //s_log.warn("execute() results.size:" + results.getResults().size());

		// This is held for PrepareMapResults, download  // added to fix session
		session.setAttribute("fullAdvancedSearchResults", results.getResults());  

		ArrayList newResults = getModifiedSet(resultRank, results.getResults(), request);
		results.setResults(newResults);

        if (resultRank.equals("specimen")) {
            resultSetModifier = newResults.size() + " imaged specimens " + resultSetModifier; 
        }		
        if (resultRank.equals("species")) {
            ArrayList taxonList = getExtraSpeciesList(request, (AdvancedSearchResults) results);
            resultSetModifier = taxonList.size() + " imaged species " +  resultSetModifier;
            session.setAttribute("taxonList", taxonList);		
		}

        request.setAttribute("resultSetModifier", resultSetModifier);

        if (ResultRank.SPECIES_SPECIFIC.equals(resultRank)) {
            session.setAttribute("taxonList", ((AdvancedSearchResults) results).getSpeciesList());		
		}
		
		if (session.getAttribute("advancedSearchResults") != null) {
			session.setAttribute("advancedSearchResults", results);
		} else if (session.getAttribute("searchResults") != null) {
			session.setAttribute("searchResults", results);
		}

        //A.log("execute() scope:" + mapping.getScope());

		return mapping.findForward(forwardString);
	}

    public ArrayList<ResultItem> getModifiedSet(String resultRank, ArrayList theResults, HttpServletRequest request) {
        // Create a results set that only has results with images.
		ArrayList newResults = new ArrayList();
        if (!resultRank.equals("species")) {  // we do a special thing in this case below.
  		  Iterator iter = theResults.iterator();
 		  ResultItem thisItem = null;
		  while (iter.hasNext()) {
			thisItem = (ResultItem) iter.next();
			if (thisItem.getHasImages()) {
				newResults.add(thisItem);
			}
		  }
          return newResults;
		}    
		return theResults;
    }
    
    private ArrayList<ResultItem> getExtraSpeciesList(HttpServletRequest request, AdvancedSearchResults results) {
      /* Get Extra Species List here means that we are not using the list of specimens (and their species) list that
         results directly from the query.  We are taking that set of species names to get the list of taxons.
         This list will have potentially many more images because those images, though they belong to the taxon
         are of specimens not necessarily in the region specified in the criteria.  */
         
        ArrayList<ResultItem> specList = results.getSpeciesList();
 	    ArrayList<ResultItem> imagedSpecList = new ArrayList<>();
		HashMap<String, ResultItem> specHash = new HashMap<>();
		
        //s_log.warn("getExtraSpeciesList() results.size:" + results.getResults().size());		
		Iterator<ResultItem> resIter = specList.iterator();
		// Go through all of the results (specimens) and put the species fullName/resultItem in specHash
		ResultItem thisItem;
		while (resIter.hasNext()) {
		  thisItem = resIter.next();
          String name = thisItem.getTaxonName();
		  specHash.put(name,thisItem);

          Connection connection = null;
          try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "PrepareCompareResultsAction.getExtraSpeciesList()");
            
            //Taxon fetchTaxon = Taxon.getInstance(connection, name);
            Taxon fetchTaxon = Taxon.getTaxonOfRank(thisItem.getSubfamily(), thisItem.getGenus(), thisItem.getSpecies(), null);
            fetchTaxon.setHasImages(connection);
            if (fetchTaxon != null) {
                if (fetchTaxon.isImaged()) {
                    //s_log.warn("getExtraSpeciesList() imaged name:" + name);
                    imagedSpecList.add(thisItem);
                }
            }
            //s_log.warn("getExtraSpeciesList() name:" + name + " fetched:" + fetchTaxon);		

          } catch (SQLException e) {
            s_log.warn("getExtraSpeciesList() fetchTaxon e:" + e);
          } finally {
            DBUtil.close(connection, this, "PrepareCompareResultsAction.getExtraSpeciesList()");
          }
        }
        
        return imagedSpecList;
    }
 }
