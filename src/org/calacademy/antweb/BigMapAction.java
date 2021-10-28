package org.calacademy.antweb;

import java.io.IOException;
import java.sql.*	;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.*;

import org.calacademy.antweb.geolocale.*;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class BigMapAction extends Action {

    private static Log s_log = LogFactory.getLog(BigMapAction.class);
    
    private static int s_mapCount = 0;
    private static int s_mapLimit = 3;
    
    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ActionForward a = Check.init(Check.GEOLOCALE, request, mapping); if (a != null) return a;
        ActionForward d = Check.valid(request, mapping); if (d != null) return d;
        
        if (ProjectMgr.hasMoved(request, response)) return null;

        Login accessLogin = LoginMgr.getAccessLogin(request);
        
        Locale locale = getLocale(request);
        HttpSession session = request.getSession();

        Overview overview = OverviewMgr.getAndSetOverview(request);
        if (overview == null) return OverviewMgr.returnMessage(request, mapping);

        DynaActionForm df = (DynaActionForm) form;
        String taxonName = (String) df.get("taxonName");
        String specimenCode = (String) df.get("specimen");
        String localityKey = (String) df.get("locality");
        String collectionCode = (String) df.get("collection");
        String projectName = (String) df.get("project");
        String countryName = (String) df.get("countryName");
        String adm1Name = (String) df.get("adm1Name");
        String museumCode = (String) df.get("museumCode");
        String geolocaleFocusVal = (String) df.get("geolocaleFocus");
        boolean geolocaleFocus = "true".equals(geolocaleFocusVal);
        Boolean refresh = (Boolean) df.get("refresh");
        boolean isRefresh = (refresh != null && refresh);
        if (taxonName != null && isRefresh) {
          MapMgr.removeMap(taxonName);
          //request.setAttribute("message", "Map cache has been refreshed");
          //return (mapping.findForward("message"));
        }

        int geolocaleId = 0;
        Geolocale geolocale = null;
        if (!Utility.isBlank(adm1Name)) {
          geolocale = GeolocaleMgr.getAdm1(adm1Name, countryName);
          if (geolocale != null) geolocaleId = geolocale.getId();
        } else if (!Utility.isBlank(countryName)) {
          geolocale = GeolocaleMgr.getCountry(countryName);
          if (geolocale != null) geolocaleId = geolocale.getId();
        }
        //A.log("BigMapAction.execute() geolocaleId:" + geolocaleId + " countryName:" + countryName + " adm1Name:" + adm1Name + " geolocale:" + geolocale);

        Project project = ProjectMgr.getProject(projectName);
        
        java.sql.Connection connection = null;
        try {
          javax.sql.DataSource dataSource = getDataSource(request, "conPool");
          
          if (HttpUtil.tooBusyForBots(dataSource, request)) { HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }
          
          connection = DBUtil.getConnection(dataSource, "BigMapAction.execute()");                        

          if (!Utility.isBlank(specimenCode)) {
              Specimen specimen = (Specimen) session.getAttribute("specimen");
              if (specimen == null || !specimenCode.equals(specimen.getCode())) {
                specimen = (new Specimen(specimenCode, connection)); //(new SpecimenDb(connection)).getSpecimen(specimenCode);
                session.setAttribute("specimen", specimen);
              }          
          }
              
          ++s_mapCount;
          if (s_mapCount > s_mapLimit) {
            String message = "Simultaneous map limit exceeded.  Please try again later, or log in for unrestricted access.";  // message to bots
            request.setAttribute("message", message);
            //s_log.warn("execute() message:" + message);
            return (mapping.findForward("message"));                  
          }           
                
          Map map = getMap(project, taxonName, specimenCode, localityKey, collectionCode, geolocaleId, museumCode, geolocaleFocus, connection, session);

          if (map != null) {
            String title = map.getTitle();
            A.log("BigMapAction.execute() title:" + title);
			session.setAttribute("title", title);          
            session.setAttribute("map", map);
            return (mapping.findForward("success"));
          }

        }  catch (SQLException sqle) {
            s_log.error("execute() e:" + sqle + " requestInfo:" + AntwebUtil.getRequestInfo(request));
        } catch (Exception e) {
            AntwebUtil.logStackTrace(e);        
        } finally {
            --s_mapCount;
            DBUtil.close(connection, this, "BigMapAction.execute()");
        }

        s_log.info("execute() no map found for taxonName:" + taxonName + " specimenCode:" + specimenCode + " locality:" + localityKey + " collectionCode:" + collectionCode);      
        //http://www.antweb.org/bigMapReq.do?name=microps&genus=nylanderia&rank=species&project=worldants
        String message = "Big Map not found.";
        request.setAttribute("message", message);
        return (mapping.findForward("message"));
    }
    
    private Map getMap(Project project, String taxonName, String specimenCode, String localityKey, String collectionCode
        , int geolocaleId, String museumCode, boolean geolocaleFocus, Connection connection, HttpSession session) throws SQLException {
        
        Map thisMap = null;
        A.log("BigMapAction.getMap() project:" + project + " geolocaleId:" + geolocaleId + " taxonName:" + taxonName);
                     
        // Taxon map.  
        if ((taxonName != null) && (!"".equals(taxonName))) {
          //A.log("getMap() taxonName:" + taxonName);
          Taxon taxon = (new TaxonDb(connection)).getTaxon(taxonName);
          if (taxon != null) {
          
            LocalityOverview localityOverview = project;
            if (geolocaleId > 0) {
              localityOverview = GeolocaleMgr.getGeolocale(geolocaleId);
            }

            if (!geolocaleFocus) {
              thisMap = MapMgr.getMap(taxon, localityOverview, connection);
              //A.log("getMap() 1 thisMap:" + thisMap);
            } else {            
              thisMap = new Map(taxon, localityOverview, connection, geolocaleFocus);
            }
            //A.log("getMap() title:" + thisMap.getTitle() + " taxon:" + taxon + " localityOverview:" + localityOverview + " geolocaleFocus:" + geolocaleFocus);

            session.setAttribute("taxon", taxon);
          } else {
            String message = "BigMapAction.getMap(" + project + ", " + taxonName + ", " 
              + specimenCode + ", " + localityKey + ", " + collectionCode + ", ...) taxon not found.";
            LogMgr.appendLog("notFound.txt", message);
          }
        } else 

        if ((localityKey != null) && (!"".equals(localityKey))) {
          //s_log.warn("getMap() locality:" + localityKey);
          Locality locality = null;
          try {
            locality = (new LocalityDb(connection)).getLocalityByCodeOrName(localityKey);
          } catch (SQLException e) {
            s_log.warn("getMap() 1 e:" + e);
          }
          if (locality != null) {
            thisMap = new Map(locality);
            //if (thisMap != null) s_log.warn("getMap() fetched: locality:" + localityKey);
          }
        } else 
         
        if ((collectionCode != null) && (!"".equals(collectionCode))) {
          //s_log.warn("getMap() collectionCode:" + collectionCode);
          Collection collection = null;
          try {
            collection = (new CollectionDb(connection)).getCollection(collectionCode);
          } catch (SQLException e) {
            s_log.warn("getMap() 2 e:" + e);
          }
          if (collection != null) {
            thisMap = new Map(collection);
            //if (thisMap != null) s_log.warn("getMap() fetched: " + collectionCode);
          }
        } else 
           
        if ((specimenCode != null) && (!"".equals(specimenCode))) {
          Locality locality = null;
  		  if (specimenCode != null) {
		    ArrayList arrayList = new ArrayList();
		    arrayList.add(specimenCode);
		    thisMap = new Map(arrayList, connection);	    
	 	  }
        } else 
        
        if (geolocaleId > 0) {
          ObjectMapDb objectMapDb = new ObjectMapDb(connection);
          thisMap = objectMapDb.getGeolocaleMap(geolocaleId);
        } else
        
        if (museumCode != null) {
          ObjectMapDb objectMapDb = new ObjectMapDb(connection);
          thisMap = objectMapDb.getMuseumMap(museumCode);        
        }

        //A.log("getMap() end map:" + thisMap.getMapName() + " " + thisMap.getDisplayMapHashCounts());
        return thisMap;
    }

    private Map getMapFromSession(HttpSession session) {
        Map thisMap = null;

        // get the map from the request parameters, or from the session
        //A.log("getMapFromSession()");
        Taxon thisTaxon = (Taxon) session.getAttribute("taxon");
        if ((thisTaxon != null) && (thisTaxon.getMap() != null)) {
            thisMap = thisTaxon.getMap();
        } else {
            thisMap = (Map) session.getAttribute("map");
        }
        
        return thisMap;
    }
    
    private void logMap(Map thisMap, String taxonName, String specimen, String locality, String collectionCode) {
        if (!thisMap.isLocality() && !thisMap.isCollection()) {
          // must be specimen or taxon
          if ((taxonName != null) && (!taxonName.equals(""))) {
            s_log.warn("request for taxon:" + taxonName + " map.taxonName:" + thisMap.getTaxonName());
          } 
          if ((specimen != null) && (!specimen.equals(""))) {
            s_log.warn("request for specimen:" + specimen + " map.taxonName:" + thisMap.getTaxonName());
          }
        }   
        if (thisMap.isLocality()) {
          if (locality == null) {
            s_log.warn("map.isLocality but locality is null");
          } else if (locality.equals(thisMap.getLocalityCode())) {
            s_log.warn("locality/map name equality");
          } else {
            s_log.warn("NOT locality/map name equality.  locality:" + locality + " map.localityName:" + thisMap.getLocalityCode());           
          }
        }
        if (thisMap.isCollection()) {
          if (collectionCode == null) {
            s_log.warn("map.isCollection but collectionCode is null");
          } else if (collectionCode.equals(thisMap.getCollectionCode())) {
            s_log.warn("collection/map name equality.");
          } else {
            s_log.warn("NOT collection/map name equality.  collectionCode:" + collectionCode + " map.collectionCode:" + thisMap.getCollectionCode());          
          }
        }
    }      

}
