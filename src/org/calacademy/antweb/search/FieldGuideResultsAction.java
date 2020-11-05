package org.calacademy.antweb.search;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.struts.action.*;
import org.w3c.dom.Document;

import java.sql.*;
import java.util.*;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.search.FieldGuide;
import org.calacademy.antweb.*;
import org.calacademy.antweb.Map;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


public final class FieldGuideResultsAction extends ResultsAction {

    private static Log s_log = LogFactory.getLog(FieldGuideResultsAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        Locale locale = getLocale(request);
        HttpSession session = request.getSession();
        ActionForward forward = null;
        TaxaFromSearchForm taxaForm = (TaxaFromSearchForm) form;

        Overview overview = OverviewMgr.getAndSetOverview(request);
        if (overview == null) return OverviewMgr.returnMessage(request, mapping);
                
        LocalityOverview localityOverview = (LocalityOverview) overview;

        if ((taxaForm.getTaxa() != null) || (taxaForm.getChosen() != null)) {

			String title = null;
			Taxon taxon = null;

			String[] chosen = taxaForm.getChosen();
			ArrayList<String> chosenList = new ArrayList(Arrays.asList(chosen));
			ArrayList<ResultItem> chosenResults = null;
			ArrayList<Taxon> chosenTaxa = null;

            String resultRank = taxaForm.getResultRank();
			if (resultRank == null) resultRank = ResultRank.SPECIMEN;
			
			String caste = taxaForm.getCaste();
			if (caste == null) caste = Caste.DEFAULT;
			caste = Caste.QUEEN;
            A.log("FieldGuideResultsAction.execute() caste:" + caste);			
		
			AdvancedSearchResults searchResults = (AdvancedSearchResults) session.getAttribute("advancedSearchResults");      
			if (searchResults == null) {
	          s_log.warn("execute() WST. searchResults is null. Sending to login.");
			  return (mapping.findForward("goToLogin"));        
			}

            if (ResultRank.SPECIMEN.equals(resultRank)) {
				chosenResults = getChosenResultsFromResults(chosenList, searchResults.getResults());
				forward = mapping.findForward("fieldGuideByTaxon");
				title = "Species Field Guide";  
				A.log("execute() chosenResults (specimens) count:" + chosenResults.size());                    
			} else if (ResultRank.isTaxonRank(resultRank)) {
			    // The taxaList is the list of taxons for which we want to see specimens.
				// The chosenList is the selected values from the taxaList
				// The searchResults are the specimen level data. 
				ArrayList<ResultItem> taxonList = (ArrayList) session.getAttribute("taxonList");
				if (searchResults == null) return mapping.findForward("sessionExpired");
				
				chosenResults = getSpecimensForTaxaFromResults(chosenList, taxonList, searchResults.getResults());
				
				//if (ResultRank.SPECIES.equals(resultRank)) chosenResults = getSpecimensForTaxaFromResults(chosenList, taxonList, searchResults.getResults());
				//if (ResultRank.GENUS.equals(resultRank)) chosenResults = getSpecimensForTaxaFromResults(chosenList, taxonList, searchResults.getResults());
				//if (ResultRank.SUBFAMILY.equals(resultRank)) chosenResults = getSpecimensForTaxaFromResults(chosenList, taxonList, searchResults.getResults());
				forward = mapping.findForward("fieldGuideByTaxon");
				title = (new Formatter()).capitalizeFirstLetter(resultRank) + " Field Guide";                    
				A.log("execute() chosenResults (taxon) count:" + chosenResults.size());   // For Bay Area ants Marin: 383          
			} else {
			    String message = "Unsupported Result Rank for Field Guide:" + resultRank;
			    s_log.error("execute() " + message);
			    request.setAttribute("message", message);
			    return (mapping.findForward("message"));            				
            }
            FieldGuide fieldGuide = new FieldGuide();
            java.sql.Connection connection = null;
            try {
              javax.sql.DataSource dataSource = getDataSource(request, "conPool");              
              connection = DBUtil.getConnection(dataSource, "FieldGuideResultsAction.execute()");			
    		  fieldGuide.setOverview(localityOverview);
    		  String fgRank = "species"; if (ResultRank.SPECIES.equals(resultRank)) fgRank = "genus";
			  fieldGuide.setRank(fgRank);

              if (localityOverview.getExtent() == null) s_log.warn("execute() localityOverview:" + localityOverview + " has empty blank extent");
			  
			  fieldGuide.setExtent(localityOverview.getExtent());
              chosenTaxa = getChosenTaxa(request, chosenResults, resultRank, caste, localityOverview, connection);    
              A.log("execute() (chosenTaxa) count:" + chosenTaxa.size());    // For Bay Area ants Marin: 81           
			  fieldGuide.setTaxa(chosenTaxa);			  
		  	  fieldGuide.setTitle((String) session.getAttribute("searchTitle"));	
            } catch (SQLException e) {
                s_log.error("execute() e:" + e + " caught on request:" + AntwebUtil.getRequestInfo(request));
                s_log.info(AntwebUtil.getStackTrace(e));
            } finally {
               DBUtil.close(connection, this, "FieldGuideResultsAction.execute()");
            }
            A.log("chosenTaxa count:" + chosenTaxa.size() + " chosenTaxa field guide taxa count:" + fieldGuide.getTaxa().size());

            request.setAttribute("fieldGuide", fieldGuide);
            request.setAttribute("title", title);                
            request.setAttribute("chosenTaxa", chosenTaxa);
        } else {
            String message = "No taxa chosen/found for target:" + HttpUtil.getTarget(request);
            s_log.warn(message);
            request.setAttribute("message", message);
            return (mapping.findForward("message"));
        }

        saveToken(request);
        return forward;
    }

    // If we want all specimens for a taxa instead of all qualifiying specimens of a taxa:
    private ArrayList<Taxon> getChosenTaxa(HttpServletRequest request, ArrayList<ResultItem> chosenResults
            , String resultRank, String caste, Overview overview, Connection connection) throws SQLException {
        ArrayList<Taxon> chosenTaxa = new ArrayList();

        A.log("getChosenTaxa() chosenResults:" + chosenResults.size() + " resultRank:" + resultRank + " overview:" + overview);
        String distinctTaxonName = "";
        Collections.sort(chosenResults);  // Must be sorted here in order to remove duplicates
        for (ResultItem resultItem : chosenResults) {
            if (resultItem.getTaxonName().equals(distinctTaxonName)) continue;
            distinctTaxonName = resultItem.getTaxonName();
            if (overview == null) overview = ProjectMgr.getProject(Project.ALLANTWEBANTS);
                Taxon taxon = null;
				if (ResultRank.SPECIES.equals(resultRank) || ResultRank.SPECIMEN.equals(resultRank)) {
                    taxon = Taxon.getInstance(connection, Family.FORMICIDAE, resultItem.getSubfamily(), resultItem.getGenus(), resultItem.getSpecies(), null, "species");
                    if (taxon == null) {
                      s_log.error("getChoseTaxa() subfamily:" + resultItem.getSubfamily() + " species:" + resultItem.getSpecies() + " genus:" + resultItem.getGenus());
                      // Last time this happened (in dev env) it was a data problem, remedied by a production database reload.
                      continue;
                    }
             	// taxon.setChildrenLocalized(project);
			} else {
  				 if (ResultRank.GENUS.equals(resultRank)) {
                    taxon = Taxon.getInstance(connection, Family.FORMICIDAE, resultItem.getSubfamily(), resultItem.getGenus(), null, null, resultRank);
                } else if (ResultRank.SUBFAMILY.equals(resultRank)) {
                    taxon = Taxon.getInstance(connection, Family.FORMICIDAE, resultItem.getSubfamily(), resultItem.getSubfamily(), null, null, resultRank);                
                }                  
	   		      //taxon.setChildren(project);
			}
            org.calacademy.antweb.Map map = new org.calacademy.antweb.Map(taxon, (LocalityOverview) overview, connection);
            taxon.setMap(map);	  			  

            //taxon.setImages(overview, false);                 
            taxon.setImages(overview, caste);
            chosenTaxa.add(taxon);
        }
        Collections.sort(chosenTaxa);
        return chosenTaxa;
    }

    protected ArrayList<ResultItem> getSpecimensForTaxaFromResults(ArrayList<String> chosenList
            , ArrayList<ResultItem> theTaxa, ArrayList<ResultItem> searchResults) {
        ArrayList<ResultItem> theSpecimens = new ArrayList();
        ResultItem thisItem = null;
        int thisChosen = 0;
  
        for (String chosenListNext : chosenList) {
            thisChosen = (Integer.valueOf(chosenListNext)).intValue();
            thisItem = (ResultItem) theTaxa.get(thisChosen);
            for (ResultItem resultItem : searchResults) {
                if (thisItem.getSpecies().equals(resultItem.getSpecies())
                  && thisItem.getGenus().equals(resultItem.getGenus())
                   ) {
                   //s_log.info("getSpecimensForTaxaFromResults() added - thisItem.species:" + resultItem.getCode());
                    theSpecimens.add(resultItem);
                }
            }    
        }   
        A.log("getSpecimensForTaxaFromResults() chosenList:" + chosenList.size()
            + " theTaxa:" + theTaxa.size()
            + " searchResults:" + searchResults.size()
            + " theSpecimens:" + theSpecimens.size());  
          // for Bay Area Ants Marin: chosenList:63 theTaxa:63 searchResults:333 theSpecimens:383
        return theSpecimens;
    }

/*
    // If we only wanted qualifying specimens on the map, as opposed to all for the taxon, this...
    private ArrayList<Taxon> getChosenTaxa(HttpServletRequest request, ArrayList<ResultItem> chosenResults
            , String resultRank, String project, Connection connection) {
        
        ArrayList<Taxon> chosenTaxa = new ArrayList();

        //s_log.warn("getChosenTaxa() chosenResults:" + chosenResults.size() + " resultRank:" + resultRank + " project:" + project);            
        
        String distinctTaxonName = "";
        Taxon taxon = null;
        ArrayList specimenList = new ArrayList();
        for (ResultItem resultItem : chosenResults) {
          if (!resultItem.getTaxonName().equals(distinctTaxonName)) {  // new taxon set in specimen list

            if (taxon != null) {  // finish and add the last one
              taxon.setMap(new Map(specimenList, connection));
              chosenTaxa.add(taxon);         
              specimenList = new ArrayList();
            }
            distinctTaxonName = resultItem.getTaxonName();

                if ((project == null) || ("".equals(project))) project = "allantwebants";

				if ((resultRank.equals("species") || (resultRank.equals("specimen")))) {
                  taxon = Taxon.getInstance(connection, resultItem.getSpecies(), resultItem.getGenus(), "species");
	//  			  taxon.setChildrenLocalized(project);
				} else {
  				  if (resultRank.equals("genus")) {
                    taxon = Taxon.getInstance(connection, resultItem.getGenus(), null, resultRank);
                  } else if (resultRank.equals("subfamily")) {
                    taxon = Taxon.getInstance(connection, resultItem.getSubfamily(), null, resultRank);                
                  }                  
	   		      //taxon.setChildren(project);
				}
				
				// Only if we want all specimens for the taxon.  If we want result set specimens...
                //org.calacademy.antweb.Map map = new org.calacademy.antweb.Map(taxon, project, connection);
                //taxon.setMap(map);	  			  

                taxon.setImages(project, false);                 
           
          } // end if new taxon

          specimenList.add(resultItem.getCode());

        } // end for loop
        if (taxon != null) {  // finish and add the last one
        s_log.warn("getChosenTaxa add taxon:" + taxon + " with specimenList:" + specimenList);
          taxon.setMap(new Map(specimenList, connection));
          chosenTaxa.add(taxon);         
        }                 
                 
        return chosenTaxa;
    }

*/
}



