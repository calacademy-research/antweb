package org.calacademy.antweb.search;

import java.io.IOException;

import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import java.sql.*;

import org.calacademy.antweb.Map;  // not redundant.  To disambiguate.
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class MapResultsAction extends ResultsAction {

    private static Log s_log = LogFactory.getLog(MapResultsAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException { 

        // Extract attributes we will need
        Locale locale = getLocale(request);
        HttpSession session = request.getSession();

        if (session.getAttribute("activeSession") == null) {
          s_log.debug("MapResultsAction no activeSession.");
          return mapping.findForward("sessionExpired");
        }
        
        TaxaFromSearchForm taxaForm = (TaxaFromSearchForm) form;
        List taxa = null;

        String[] chosen = taxaForm.getChosen();
        ArrayList<String> chosenList = null;

        if (chosen != null) { 
          chosenList = new ArrayList(Arrays.asList(chosen));
        }

        String resultRank = taxaForm.getResultRank();
        if (resultRank == null) resultRank = (String) session.getAttribute("resultRank");
        if (resultRank == null) resultRank = ResultRank.SPECIMEN;
        String output = taxaForm.getOutput();
        if (output == null) output = (String) session.getAttribute("output");
        if (output == null) output = Output.LIST;

        s_log.debug("MapResultsAction.execute() formVal:" + taxaForm.getResultRank() + " resultRank:" + resultRank);
               
		AdvancedSearchResults advancedSearchResults = (AdvancedSearchResults) session.getAttribute("advancedSearchResults");
		ArrayList<ResultItem> searchResults = advancedSearchResults.getResults();
		
        ArrayList<ResultItem> taxonList = (ArrayList) session.getAttribute("taxonList");
		
		Map map = null;
        Connection connection = null;
        try {
            DataSource dataSource = getDataSource(request,"conPool");
            connection = DBUtil.getConnection(dataSource, "MapResultsAction.execute()");  
            String title = "Mapping Search Results";
            map = getMap(searchResults, taxonList, chosenList, resultRank, output, title, connection);
        } catch (IndexOutOfBoundsException e2) {
		  String message = "Case#:" + AntwebUtil.getCaseNumber() + " e:" + e2 + " target:" + HttpUtil.getTarget(request);
		  s_log.warn("execute() message:" + message);
		  request.setAttribute("message", message);
		  return mapping.findForward("message");                   
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            AntwebUtil.logStackTrace(e);
        } finally {
			DBUtil.close(connection, this, "MapResultsAction.execute()");
		}  
		        
        String sizeStr = (map.getChosenList() == null) ? "null" : "" + map.getChosenList().size();
        s_log.debug("MapResultsAction.execute() resultRank:" + resultRank + " title:" + map.getTitle() + " chosenList.size:" + sizeStr + " map:" + map);

        session.setAttribute("title", map.getTitle());  // now redundant. Could change the client code as well.
        session.setAttribute("map", map);
        session.setAttribute("chosenList", map.getChosenList());

        return (mapping.findForward("success"));
    }
    
    public Map getMap(ArrayList<ResultItem> searchResults, ArrayList<ResultItem> taxonList, ArrayList<String> chosenList, String resultRank
      , String output, String title, Connection connection) {
        Map map = null;
		boolean mapLocalities = false;
        String titleAddendum = "";
		String titleEnd = "";     
		
		int specimenCount = searchResults.size();   
        int localityCount = 0;
                
        String info = "searchResults:" + searchResults.size();
        if (taxonList != null) info += " taxonList:" + taxonList.size();
        if (chosenList != null) info += " chosenList:" + chosenList.size();
        
        //A.log("getMap(searchResults... output:" + output + " resultRank:" + resultRank);

	    if (Output.MAP_LOCALITY.equals(output)) { // for instance museums.
			chosenList = getSpecimensFromResults(chosenList, searchResults);
            int beforeChosenListSize = chosenList.size();
			mapLocalities = true; 
			chosenList = getLocalitiesFromSpecimens(chosenList, searchResults);       
            localityCount = chosenList.size();
            info += " localities:" + localityCount;
			//if (title.contains("AFRC")) s_log.warn("getMap() beforeSize:" + beforeChosenListSize + " afterSize:"  + chosenList.size() + " info:" + info);                             
		} else if (ResultRank.SPECIMEN.equals(resultRank) || ResultRank.SPECIES.equals(resultRank)) {
            String titleTaxaClause = "";		
	        if (ResultRank.SPECIMEN.equals(resultRank)) {
			  chosenList = getSpecimensFromResults(chosenList, searchResults);
            } else {
  			  chosenList = getSpecimensCodesForTaxaFromResults(chosenList, taxonList, searchResults, connection);  
            }

            specimenCount = chosenList.size();
            info += " chosenList(specimen):" + chosenList.size();
   		    // If too many results, then map one from each location.
			int maxChosenListSize = 1000;
			s_log.debug("getMap(searchResults... chosenListSize:" + chosenList.size());
			if (chosenList.size() > maxChosenListSize) {
				mapLocalities = true; 
				int beforeChosenListSize = chosenList.size();
				chosenList = getLocalitiesFromSpecimens(chosenList, searchResults);
				
				localityCount = chosenList.size();
				info += " chosenList(localities):" + chosenList.size();

				s_log.warn("getMap() beforeSize:" + beforeChosenListSize + " afterSize:"  + chosenList.size());     
			} else { 
			    mapLocalities = false;
   	   	  	    //titleEnd = " specimens" + titleTaxaClause + ")";			
			}

		} else {
		    s_log.warn("getMap() mapping of higher 'group by's not yet supported.");
		}	

        //if (title.contains("CASC")) s_log.warn("getMap() chosenList.size:" + chosenList.size() + " mapLocalities:" + mapLocalities + " specimenCount:" + specimenCount + " localityCount:" + localityCount + " info:" + info);

		map = new Map(chosenList, mapLocalities, specimenCount, localityCount, info, connection);	

		map.setTitle(title);
		
		//A.log("MapResultsAction.getMap() title:" + map.getTitle() + " resultRank:" + resultRank); //+ " chosenList:" + map.getChosenList() + " searchResults:" + searchResults);
        return map;
    }
    
    private ArrayList<String> getSpecimensCodesForTaxaFromResults(ArrayList<String> chosenList
      , ArrayList<ResultItem> taxonList, ArrayList<ResultItem> searchResults, Connection connection) {
        /*
           advancedSearchResults gets create by the initial search.  It is specimen level data.  JSP display it.
           Map and Compare both reduce this set, eliminating unimaged or unlocated.
           fullAdvancedSearchResults is the initial set.  Duplicated, kludgy.
           taxonList is the distinct set of taxons from the advancedSearchResults
           selectedTaxa is the list of selected taxa from the taxonList.
           In order to map from species, we will need to manually select the advancedSearchResults 
             that are in the selectedTaxa.
           */
        //A.log("getSpecimensCodesForTaxaFromResults()");

		ArrayList<String> codeList = new ArrayList();
		ArrayList<ResultItem> resultItems = searchResults;
        
        // Added to allow a null chosenList. Use all.
		if (chosenList == null) {
          for (ResultItem item : searchResults) {
			codeList.add(item.getCode());
          }          		
		  return codeList;
		}
        for (String chosen : chosenList) {
          if (chosen == null) s_log.warn("getSpecimensCodesForTaxafromResults() chosenList:" + chosenList);
          int thisChosen = (Integer.valueOf(chosen)).intValue();
          ResultItem selectedTaxon = (ResultItem) taxonList.get(thisChosen);
          for (ResultItem item : searchResults) {
			try {
              //s_log.warn("getSpecimensCodesForTaxaFromResults() selectedTaxon.getFullName():" + selectedTaxon.getFullName() 
              //  + " item.getFullName():" + item.getFullName() + " item.getCode():" + item.getCode());
              if (selectedTaxon.getFullName().equals(item.getFullName())) {
                codeList.add(item.getCode());
              }
			} catch (java.lang.IndexOutOfBoundsException e) {
				s_log.warn("getSpecimensCodesForTaxaFromResults() thisChosen:" + thisChosen + " searchResults:" + searchResults);
				throw e;
			}
          }
        }
        //s_log.warn("getSpecimensCodesForTaxaFromResults() codeList.size:" + codeList.size() 
        // + " selectedTaxa.size:" + selectedTaxa.size() + " theResults.size:" + theResults.size() 
        // + " codeList:" + codeList);
        return codeList;
    }    

    // NOT: for getSpecimensFromResults(ArrayList<String> chosen, ArrayList<ResultItem> searchResults) see superclass
    /* this takes a list of numbers representing which items were chosen,
       and the list of search results, and hands back a list of the specimens chosen
       based on the numbers */
    protected ArrayList<String> getSpecimensFromResults(ArrayList<String> chosenList, ArrayList<ResultItem> searchResults) 
      throws IndexOutOfBoundsException {
        ArrayList<String> codeList = new ArrayList<>();

        // Added to allow a null chosenList. Use all.
		if (chosenList == null) {
          for (ResultItem item : searchResults) {
			codeList.add(item.getCode());
          }          		
		  return codeList;
		}
        ResultItem thisItem = null;
        int thisChosen = 0;
        s_log.debug("getSpecimensFromResults()");
        for (String chosen : chosenList) {
            thisChosen = (Integer.valueOf(chosen)).intValue();
            thisItem = (ResultItem) searchResults.get(thisChosen);
            codeList.add(thisItem.getCode());
        }
        
        return codeList;
    }

    private ArrayList<String> getLocalitiesFromSpecimens(ArrayList<String> chosenList, ArrayList<ResultItem> searchResults)
      throws IndexOutOfBoundsException {
        //A.slog("getLocalitiesFromSpecimens searchSize:" + searchResults.size() + " chosenSize:" + chosenList.size());
        ArrayList<String> codeList = new ArrayList<>();
        HashSet<String> localityList = new HashSet<>();
        String testCode = "fmnhins0000112290";
        if (chosenList == null) {
          for (ResultItem item: searchResults) {
            String code = item.getCode();
            //if (testCode.equals(code)) A.log("getLocalitiesFromSpecimens() 1 code:" + code);
			if (!localityList.contains(item.getLocalityKey())) {
              //if (testCode.equals(code)) A.log("getLocalitiesFromSpecimens() locality:" + item.getLocalityKey());
			  codeList.add(code);
			  localityList.add(item.getLocalityKey());
			}
          }
          return codeList;
        }


        Hashtable<String, ResultItem> resultsHash = new Hashtable<>();
        for (ResultItem item: searchResults) {
          resultsHash.put(item.getCode(), item);
        }
        
        for (String chosen : chosenList) {
            //AntwebUtil.logFirst("getLocalitiesFromSpecimens() chosen:" + chosen);
            ResultItem item = resultsHash.get(chosen);
            if (item != null) {
                String t = "";
                if (item.getAdm1() != null && item.getAdm1().equals("Minnesota")) {
                 // A.slog("getLocalityFromSpecimens() adm1:Minnesota");
                  t = item.getCode() + " code:" + item.getCode() + " name:" + item.getLocalityName() + " localityCode:" + item.getLocalityCode();
                }
                //if (testCode.equals(item.getCode())) A.slog("getLocalitiesFromSpecimens() 2 code:" + item.getCode() + " localityKey:" + item.getLocalityKey() + " contains:" + localityList.contains(item.getLocalityKey()));
                if (!localityList.contains(item.getLocalityKey())) {
                  //if (!"".equals(t)) A.slog("getLocalitiesFromSpecimens() t:" + t);
                  codeList.add(item.getCode());
                  localityList.add(item.getLocalityKey());
                }
            }
        }
        //A.slog("getLocalitiesFromSpecimens() returnSize:" + codeList.size());
        return codeList;
    }
        
}



