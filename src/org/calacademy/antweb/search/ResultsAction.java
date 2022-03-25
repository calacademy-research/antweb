package org.calacademy.antweb.search;

import java.util.*;

import org.apache.struts.action.Action;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ResultsAction extends Action {

    private static Log s_log = LogFactory.getLog(ResultsAction.class);
    
    /* this takes a list of numbers representing which items were chosen,
       and the list of search results, and hands back a list of the taxa chosen
       based on the numbers */
    protected ArrayList<ResultItem> getChosenResultsFromResults(ArrayList<String> chosen, ArrayList<ResultItem> searchResults) {
    
        ArrayList<ResultItem> chosenResults = new ArrayList<>();

        for (String choice : chosen) {
          int chosenInt = Integer.parseInt(choice);
          ResultItem thisItem = searchResults.get(chosenInt);
          chosenResults.add(thisItem);
        }

        //Collections.sort(chosenResults);  // Will be sorted downstream, perhaps.
        //A.log("getChosenResultsFromResults() sorted?:" + chosenResults);
        
        return chosenResults;
    }
}
