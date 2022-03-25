package org.calacademy.antweb.search;

import java.io.IOException;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
    
public final class CompareResultsAction extends ResultsAction {

    private static final Log s_log = LogFactory.getLog(CompareResultsAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        // Extract attributes we will need
        Locale locale = getLocale(request);
        HttpSession session = request.getSession();

        // OCT312016
        // A.log("CompareResultsAction.execute() session:" + session.getAttribute("activeSession"));

        if (session.getAttribute("activeSession") == null) {
          s_log.debug("CompareResultsAction.execute() no activeSession");
          return mapping.findForward("sessionExpired");
        }
        
        TaxaFromSearchForm taxaForm = (TaxaFromSearchForm) form;

        String projectName = taxaForm.getProject();
        Project project = ProjectMgr.getProject(projectName);
        if (project == null) {
	      request.setAttribute("message", "Project not found:" + projectName);
	      return mapping.findForward("message");
	    }

        /*
        Investigation: (11/30/2016)
        It seems that the above does not print Project Not found, but below, the projectName is null.
        As if a project with a null name is fetched. If we manually set the project to be from allantweb
        then we go straight to head shot. Then hard to get to all view (which is not an option).

        Sometimes, for instance with Genus Camponotus, the search takes a long time, but the query in mysql only takes 10 seconds. Some improvements?

        Project is used throughout this class. Could be overview instead?

        Brian would like this class fixed according to the inquiry on Nov 30th from Peter Hawkes (peterghawkes@gmail.com)

        I have a couple of questions about AntWeb seaches & comparing images - 
        if I log in and do an advanced search say for all Afro Lepisiota types with images, 
        I get a list of specimens sorted by name (i.e. L. affinis first) but if I then choose "compare images", 
        select all and hit "compare", I get the images ordered by specimen code 
        (i.e completely random as far as species name goes) even though it still indicated that it is sorting by specimen name 
        (if it shows any sort options at all - often this dropdown disappears when I go into image comparison).   
        If it is showing and I try to select any other sort option I get a "sorry, nothing matches your request" message.  
        Is there a bug of some sort? - I'm not sure how this feature is supposed to work....
        what I would like to be able to do is get the images sorted by species 
        (in the view with head/profile/dorsal/label piscs in a row), and ideally I'd then like to be able to 
        deselect specimens that clearly don't match the specimen I'm looking at and refresh the image set to include a reduced number.  
        This way I can gradually eliminate options - hopefully until I have a solid match...is this possible?
        */

        // OCT312016
           //A.log("CompareResultsAction.execute() projectName:" + projectName);

        if (taxaForm.getTaxa() != null || taxaForm.getChosen() != null) {
            String[] chosen = taxaForm.getChosen();
            ArrayList<String> chosenList = new ArrayList<>(Arrays.asList(chosen));
            ArrayList<ResultItem> chosenResults;
                
            String resultRank = taxaForm.getResultRank();
            if (resultRank == null) resultRank = ResultRank.SPECIMEN;

            TreeMap<Taxon, Integer> taxaToCompare = null;
            
            try {
                if (ResultRank.SPECIMEN.equals(resultRank)) {
                    /* 
                    Bad things happen with stale session here.  Must go all the way through the search process.
                    When session stale, must start all the way from search.  Should be fixed.
                      http://localhost/antweb/advancedSearch.do?org.apache.struts.taglib.html.TOKEN=e718b6e719920d0cf12d2e696f8466af&searchMethod=advancedSearch&advanced=true&collGroupOpen=none&specGroupOpen=none&geoGroupOpen=none&typeGroupOpen=none&searchType=equals&name=&subfamilySearchType=equals&subfamily=&genusSearchType=equals&genus=Hagensia&speciesSearchType=contains&species=havilandi&subspeciesSearchType=equals&subspecies=&biogeographicregion=&country=&adm1=&adm2SearchType=equals&adm2=&localityNameSearchType=equals&localityName=&localityCodeSearchType=equals&localityCode=&habitatSearchType=equals&habitat=&methodSearchType=equals&method=&collectedBySearchType=equals&collectedBy=&collectionCodeSearchType=equals&collectionCode=&dateCollectedSearchType=equal&dateCollected=&specimenCodeSearchType=equals&specimenCode=&locatedAtSearchType=equals&locatedAt=&elevationSearchType=equal&elevation=&casteSearchType=contains&caste=&ownedBySearchType=equals&ownedBy=&type=&resultRank=specimen                    
                    But it works when advanced searching on genus:Hagensia species:havilandi
                    */
                    AdvancedSearchResults searchResults = (AdvancedSearchResults) session.getAttribute("advancedSearchResults");
                    chosenResults = getChosenResultsFromResults(chosenList, searchResults.getResults());

                    taxaToCompare = getSpecimenToCompare(request, chosenResults, chosenList, project);
                    //A.log("execute() 1 taxaToCompare:" + taxaToCompare);

                } else if (ResultRank.SPECIES.equals(resultRank)) {
                    /* In this special case, the taxaList is the list of taxons for which we want to see specimens.
                       The searchResults are the specimen level data. */
                    ArrayList<ResultItem> taxaList = (ArrayList) session.getAttribute("taxonList");
                    AdvancedSearchResults searchResults = (AdvancedSearchResults) session.getAttribute("advancedSearchResults");
                    if (searchResults == null) return mapping.findForward("sessionExpired");
                    chosenResults = getSpecimensForTaxaFromResults(chosenList, taxaList, searchResults.getResults());
                    //A.log("execute() chosenResults:" + chosenResults);
                    taxaToCompare = getTaxaToCompare(request, chosenResults, chosenList, project, resultRank);
                    //A.log("execute() 2 taxaToCompare:" + taxaToCompare);
                } else {
				  String message = "Unsupported Result Rank for Compare Results:" + resultRank;
				  s_log.error("execute() " + message);
				  request.setAttribute("message", message);
				  return mapping.findForward("message");
                }
            } catch (IndexOutOfBoundsException e) {
              String message = "Session could be stale.  Please restart the search process.";
              s_log.error("execute() " + message);
              request.setAttribute("message", message);
              return mapping.findForward("message");
            }
                        
            /* The taxaToCompare TreeMap is keyed by Specimen (casent displayed), which is meaningless and makes
               it difficult to sort by TaxonName.  They were in the right order in getTaxaToCompare.
               Looks like a rewrite is necessary of UI is necessary.
               
               Use: http://localhost/antweb/advancedSearch.do?org.apache.struts.taglib.html.TOKEN=daf2080aa6a61dc28442ba6d33e008a2&searchMethod=advancedSearch&advanced=true&collGroupOpen=none&specGroupOpen=none&geoGroupOpen=none&typeGroupOpen=none&searchType=equals&name=&subfamilySearchType=equals&subfamily=&genusSearchType=equals&genus=&speciesSearchType=equals&species=nitida&subspeciesSearchType=equals&subspecies=&biogeographicregion=&country=&adm1=&adm2SearchType=equals&adm2=&localityNameSearchType=equals&localityName=&localityCodeSearchType=equals&localityCode=&habitatSearchType=equals&habitat=&methodSearchType=equals&method=&collectedBySearchType=equals&collectedBy=&collectionCodeSearchType=equals&collectionCode=&dateCollectedSearchType=equal&dateCollected=&specimenCodeSearchType=equals&specimenCode=&locatedAtSearchType=equals&locatedAt=&elevationSearchType=equal&elevation=&casteSearchType=contains&caste=&ownedBySearchType=equals&ownedBy=&type=&resultRank=specimen
               
               Compare images, select *.  Then Log in. to see Casent and taxon names.
               Tail the antwebInfo.log           

               See specimen.getTaxonNameDisplay() and compareTo()               
               See also: /web/search/multiTaxaComparison-body.jsp and ./web/search/multiTaxaOneView-body.jsp
             */

            // OCT312016
            //s_log.warn("execute() scope:" + mapping.getScope() + " taxaToCompare:" + taxaToCompare.size());

            //request.setAttribute("taxaToCompare", taxaToCompare);
            session.setAttribute("taxaToCompare", taxaToCompare);
            //A.log("execute() scope:" + mapping.getScope());
/* // Feb2020

            if ("request".equals(mapping.getScope())) {
            } else {
                TreeMap oldList = (TreeMap) session.getAttribute("taxaToCompare");
                if (oldList != null) {
                    Iterator finalIter = oldList.keySet().iterator();
                    Taxon oldTaxon = null;
                    while (finalIter.hasNext()) {
                        oldTaxon = (Taxon) finalIter.next();
                        try {
                            oldTaxon.callFinalize();
                        } catch (Throwable e) {
                            s_log.error("execute() finalize error.  oldTaxon.getName:"+ oldTaxon.getName() + " e:" + e);
                        }
                    }
                }
                //s_log.info("execute() session taxaToCompare:" + taxaToCompare);
                session.setAttribute("taxaToCompare", taxaToCompare);
            }
*/            
            
        }

        saveToken(request);

        String shot = request.getParameter("shot");
		//A.log("CompareResultsAction.execute() shot:" + shot);
        if (shot != null) {
            return mapping.findForward("oneView");
        } else {
            return mapping.findForward("success");
        }
    }

    private TreeMap<Taxon, Integer> getSpecimenToCompare(HttpServletRequest request, ArrayList<ResultItem> chosenResults
            , ArrayList<String> chosenList, Project project) {

		TreeMap<Taxon, Integer> specimenToCompare = new TreeMap<>();   // maps a specimen to its position in the search results.  was new GenusSpeciesItemComparator()

		Specimen specimen = null;
		Connection connection = null;

		try {
			DataSource dataSource = getDataSource(request, "conPool");
			connection = DBUtil.getConnection(dataSource, "CompareResultsAction.getSpecimenToCompare()");
			String rank;
			
			//This did not seem to do the trick... later, specimens are (were?) getting ordered.
			s_log.debug("getSpecimenToCompare() chosenResults:" + chosenResults);
			//Collections.sort(chosenResults);
			s_log.debug("getSpecimenToCompare() after sort:" + chosenResults);

			int count = 0;
			String lastTaxonName = "";
			for (ResultItem thisItem : chosenResults) {
				rank = "specimen";                            
				specimen = new Specimen(thisItem.getCode(), project, connection, true); // getImages! Needed?
				//A.log("getSpecimenToCompare() specimen:" + specimen);

				int chosenListGetCount = 0;
				if (chosenList.size() > count) {
				  chosenListGetCount = Integer.parseInt(chosenList.get(count));
				  // s_log.info("  getTaxaToCompare() chosenListSize:" + chosenList.size() + " chosenListGetCount:" + chosenListGetCount);
				}
				
				specimenToCompare.put(specimen, chosenListGetCount);  // was count
                  // A.log("getSpecimenToCompare() specimen:" + specimen + " count:" + count + " chosenList.getCount:" + chosenListGetCount + " specimenToCompare:" + specimenToCompare.size());
				++count;      // was count++;              
			}

		} catch (SQLException e) {
			s_log.error("getspecimenToCompare() e:" + e);
		} finally {
			DBUtil.close(connection, this, "CompareResultsAction.getSpecimenToCompare()");
		}    
		
		return specimenToCompare;
    }

    private TreeMap<Taxon, Integer> getTaxaToCompare(HttpServletRequest request, ArrayList<ResultItem> chosenResults
            , ArrayList<String> chosenList, Project project, String resultRank) {

		TreeMap<Taxon, Integer> taxaToCompare = new TreeMap<>(new GenusSpeciesItemComparator());   // maps a taxon to its position in the search results

		Taxon taxon = null;
		Connection connection = null;

		try {
			DataSource dataSource = getDataSource(request, "conPool");
			connection = DBUtil.getConnection(dataSource, "CompareResultsAction.getTaxaToCompare()");
			String rank;
			
			//This did not seem to do the trick...
			
			s_log.debug("getTaxaToCompare() chosenResults:" + chosenResults);
			//Collections.sort(chosenResults);
			s_log.debug("getTaxaToCompare() after sort:" + chosenResults);

			int count = 0;
			String lastTaxonName = "";
			for (ResultItem thisItem : chosenResults) {
				++s_count;
				// This happens when "species" is selected in the Group By field of the advanced search form.
				rank = thisItem.getRank();
				taxon = Taxon.getTaxonOfRank(rank);
				taxon.setRank(rank);
				taxon.setSubfamily(thisItem.getSubfamily());
				taxon.setGenus(thisItem.getGenus());
				taxon.setSpecies(thisItem.getSpecies());
				//A.log("getTaxaToCompare() subspecies:" + thisItem.getSubspecies());                        
				taxon.setSubspecies(thisItem.getSubspecies());
				
				// Added Mark Jul 2015 to speed things up (a lot).
				if (lastTaxonName.equals(taxon.getTaxonName())) continue;
				lastTaxonName = taxon.getTaxonName();
				//A.log("getTaxaToCompare() count:" + s_count + " taxon_name:" + taxon.getTaxonName() + " resultRank:" + resultRank + " rank:" + thisItem.getRank() + " subspecies:" + taxon.getSubspecies());
				
				taxon.setHasImages(true);
				taxon.generateBrowserParams(project);
				taxon.setImages(connection, project);

				int chosenListGetCount = 0;
				if (chosenList.size() > count) {
				  chosenListGetCount = Integer.parseInt(chosenList.get(count));
				  // s_log.info("  getTaxaToCompare() chosenListSize:" + chosenList.size() + " chosenListGetCount:" + chosenListGetCount);
				}
				
				taxaToCompare.put(taxon, chosenListGetCount);  // was count
				if (AntwebProps.isDevMode()) s_log.info("getTaxaToCompare() taxon:" + taxon + " count:" + count + " chosenList.getCount:" + chosenListGetCount + " taxaToCompare:" + taxaToCompare.size());
				++count;      // was count++;              
			}

		} catch (SQLException e) {
			s_log.error("getTaxaToCompare() e:" + e);
		} finally {
			DBUtil.close(connection, this, "CompareResultsAction.getTaxaToCompare()");
		}    
		return taxaToCompare;
    }

	private static int s_count = 0;

    protected ArrayList<ResultItem> getSpecimensForTaxaFromResults(ArrayList<String> chosenList
             , ArrayList<ResultItem> theTaxa, ArrayList<ResultItem> searchResults) {
        ArrayList<ResultItem> theSpecimens = new ArrayList<>();
        ResultItem thisItem;
        int thisChosen;
  
        for (String chosenListNext : chosenList) {
            thisChosen = Integer.parseInt(chosenListNext);
            thisItem = theTaxa.get(thisChosen);

            //A.log("getSpecimensForTaxaFromResults() chosenListNext:" + chosenListNext + " thisItem:" + thisItem);

            for (ResultItem resultItem : searchResults) {
                if (thisItem.getSpecies().equals(resultItem.getSpecies())
                  && thisItem.getGenus().equals(resultItem.getGenus())
                   ) {
                   //A.log("getSpecimensForTaxaFromResults() added - thisItem.species:" + resultItem.getCode());
                    theSpecimens.add(resultItem);
                }
            }    
        }        
        return theSpecimens;
    }
    
}
