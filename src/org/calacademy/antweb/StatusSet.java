package org.calacademy.antweb;

import java.util.*;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

import javax.servlet.http.HttpSession;


public class StatusSet extends Status {
    private static Log s_log = LogFactory.getLog(StatusSet.class);

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
        
    public final static String[] allStatusSetArray = {    
        WORLDANTS, ALL, ALL_DETERMINED, ALL_INDETERMINED, COMPLETE, VALID_FOSSIL, VALID_EXTANT, TYPE
    };
    public final static String[] minimalStatusSetArray = {    
        ALL, COMPLETE, VALID_FOSSIL, VALID_EXTANT, TYPE
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
          return (new StatusSet(VALID)).getAndCriteria();
          
        } else if (Project.ALLANTWEBANTS.equals(projectName)) {
          return (new StatusSet(ALL)).getAndCriteria();

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
    public static String getStatusSet(String requestStatusSet, HttpServletRequest request, Overview overview) {
        if (false && AntwebProps.isDevMode() && overview instanceof Project) {
          AntwebUtil.logShortStackTrace();
        }
        boolean setInSession = true;
        String statusSetStr = requestStatusSet;
        //A.log("getStatusSet() 1 overview:" + overview + " statusSetStr:" + statusSetStr);
        HttpSession session = request.getSession();
        if (statusSetStr == null) {
          if (OverviewMgr.isNewOverview(overview, session)) {
            //setInSession = false;
            String overviewName = overview.getName();
            if (Project.WORLDANTS.equals(overviewName)) statusSetStr = StatusSet.VALID_WITH_FOSSIL;
            if (Project.ALLANTWEBANTS.equals(overviewName)) statusSetStr = StatusSet.ALL;
            if (Project.FOSSILANTS.equals(overviewName)) statusSetStr = StatusSet.VALID_WITH_FOSSIL;
          }
          if (statusSetStr == null) statusSetStr = (String) session.getAttribute("statusSet");
          if (statusSetStr == null) statusSetStr = StatusSet.VALID_EXTANT; //StatusSet.ALL;
        }
        
        // See BrowseAction.java:400 for some notes about status related bugs.
		//if (Project.WORLDANTS.equals(overview.getName())) statusSetStr = Status.VALID;
		//if (Project.ALLANTWEBANTS.equals(overview.getName())) statusSetStr = StatusSet.ALL;

        //A.log("getStatusSet() 3 requestStatusSet:" + requestStatusSet + " sessionStatusSet:" + (String) session.getAttribute("statusSet") + " overview:" + overview + " statusSetStr:" + statusSetStr);
        
        if (setInSession) session.setAttribute("statusSet", statusSetStr);
        return statusSetStr;    
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
