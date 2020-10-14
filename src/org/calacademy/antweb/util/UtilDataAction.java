package org.calacademy.antweb.util;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.Date;
import javax.servlet.http.*;

import org.apache.struts.action.*;

import java.sql.*;
import javax.sql.DataSource;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.curate.*;
import org.calacademy.antweb.Map;
import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.search.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.data.*;
import org.calacademy.antweb.data.geonet.*;
import org.calacademy.antweb.data.googleApis.*;
import org.calacademy.antweb.curate.speciesList.*;
import org.calacademy.antweb.search.AdvancedSearchAction;

import com.google.gson.*;

/*
// Requests look something like:  
   https://www.antweb.org/utilData.do?action=deleteConflictedDefaultImages
*/

public class UtilDataAction extends Action {

    private static Log s_log = LogFactory.getLog(UtilDataAction.class);

    private static String m_inComputeProcess;


    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) {
                         
        HttpSession session = request.getSession();                
        HttpUtil.setUtf8(request, response); 
        
        java.sql.Connection connection = null;
        
		UtilForm theForm = (UtilForm) form;
		String action = theForm.getAction();

        Login accessLogin = LoginMgr.getAccessLogin(request);
        Group accessGroup = GroupMgr.getAccessGroup(request);

        /*
          Indication of reloading of Manager objects can happen in the request with:
             example: &reload=geolocale
          Or it can just happen in 
        */        
        String reload = theForm.getReload();

        //ActionForward a = Check.init(Check.TAXON, request, mapping); if (a != null) return a;

        boolean isAllow = "allow".equals(theForm.getParam()); // Used by scheduler.

        String message = null;
        // Being called from a getUrl() by Schedule.do, Scheduler.java.
        if (!isAllow) { 

          List<String> needNothingInit = new ArrayList<String>(Arrays.asList(new String[] {"fetchGoogleApisData", "delGoogleMapFunction", "genGoogleMapFunction", "delGoogleMapFunction", "genObjectMaps"
                , "genGroupObjectMap", "genGroupObjectMaps", "updateGroupCounts", "updateGroupUploadStats", "exifData", "changeOwner", "changeOwnerAndPerms"
                , "adminAlertTest", "populateBioregion", "siteWarning", "imageUtil"})); 
          List<String> needLoginInit = new ArrayList<String>(Arrays.asList(new String[] {"imageUtil"}));          
          List<String> needTaxonInit = new ArrayList<String>(Arrays.asList(new String[] {"worldantsReload"}));          

          if (needNothingInit.contains(action)) { 
            // do nothing.
          } else
          if (needLoginInit.contains(action)) { 
            ActionForward a = Check.init(Check.TAXON, request, mapping); if (a != null) return a;
          } else          
          if (needTaxonInit.contains(action)) { 
            ActionForward a = Check.init(Check.TAXON, request, mapping); if (a != null) return a;
          }    

          if (accessGroup == null) {
              message = "Not Logged in";
          }
          if (message != null) {
              request.setAttribute("message", message);
              return (mapping.findForward("message"));
          }
        }
		
        try {
            UtilDataAction.setInComputeProcess(action);   

            DataSource dataSource = getDataSource(request, "longConPool");
            connection = DBUtil.getConnection(dataSource, "UtilAction.execute()", HttpUtil.getTarget(request));
            connection.setAutoCommit(false);
            
            if (action != null) {
            
              s_log.warn("execute() " + form.toString());
              OperationDetails operationDetails = doAction(action, theForm, accessLogin, accessGroup, connection, request, mapping);

              if (operationDetails instanceof UploadDetails) {
                ((UploadDetails) operationDetails).finish(accessLogin, request, connection);
              }

              connection.commit();
                           
              ActionForward forward = operationDetails.findForward(mapping, request);
              A.log("execute() action:" + action + " operationDetails:" + operationDetails + " forward:" + forward);  
              if (forward != null) return forward;

            } // end if action != null

			String t = AntwebMgr.reload(reload, true, connection);
			// No need to log.
            
			request.setAttribute("message", "action not found:" + action);
			return (mapping.findForward("message")); 
        } catch (SQLException e) {
            s_log.error("execute() action:" + action + " e:" + e);
//            DBUtil.rollback(connection);
            AntwebUtil.errorStackTrace(e);         
        } catch (IOException e) {
            s_log.error("execute() action:" + action + " e:" + e);
            AntwebUtil.errorStackTrace(e);         
        } finally {
            UtilDataAction.setInComputeProcess(null);
            DBUtil.close(connection, this, "UploadAction.execute() 1");
        }

        //this shouldn't happen in this example
        s_log.error("execute()  This should not happen");
        return mapping.findForward("failure");
    }

    private OperationDetails doAction(String action, UtilForm form, Login accessLogin, Group accessGroup, Connection connection, HttpServletRequest request, ActionMapping mapping) 
      throws IOException, SQLException {
      
        OperationDetails operationDetails = new OperationDetails(action);
        
		String message = null;
		Date startTime = new Date();

        String param = form.getParam();
        String param2 = form.getParam2();
		int num = form.getNum();
		String name = form.getName();
		int groupId = form.getGroupId();
        String code = form.getCode();	
        String text = form.getText();	
        String taxonName = form.getTaxonName();
        String prop = form.getProp();
        int secureCode = form.getSecureCode();

        boolean isAdmin = accessLogin != null && accessLogin.isAdmin();
        boolean isSecure = AntwebUtil.isSecureCode(secureCode) || isAdmin;
        s_log.warn("doAction() " + action + " request:" + request + " isSecure:" + isSecure + " secureCode:" + secureCode + " isAdmin:" + isAdmin);

        if (isSecure) {
            if ("allSets".equals(action)) {
                message = "allSets - ";
                message += doAction("set1", form, accessLogin, accessGroup, connection, request, mapping);
                message += " " + doAction("set2", form, accessLogin, accessGroup, connection, request, mapping);
                message += " " + doAction("set3", form, accessLogin, accessGroup, connection, request, mapping);
                message += " " + doAction("set4", form, accessLogin, accessGroup, connection, request, mapping);
                message += " " + doAction("set5", form, accessLogin, accessGroup, connection, request, mapping);
            }

            if ("set1to3".equals(action)) {
                message = "set1to3 - ";
                message += doAction("set1", form, accessLogin, accessGroup, connection, request, mapping);
                message += " " + doAction("set2", form, accessLogin, accessGroup, connection, request, mapping);
                message += " " + doAction("set3", form, accessLogin, accessGroup, connection, request, mapping);
            }

            // dev: 11.68 mins  Or 24.98 mins? Prod: 11.23 mins  Must be followed with the Count Crawls
            if ("set1".equals(action)) {
                message = "set1 - ";
                message += doAction("worldantsFetchAndReload", form, accessLogin, accessGroup, connection, request, mapping);
                message += " " + doAction("taxonFinish", form, accessLogin, accessGroup, connection, request, mapping);
            }

            // Dev: 31.50 mins and 25.75 mins. Prod: 43.93
            if ("set2".equals(action)) {
                message = "set2 - ";
                message += doAction("dataCleanup", form, accessLogin, accessGroup, connection, request, mapping);
                message += " " + doAction("generateGeolocaleTaxaFromSpecimens", form, accessLogin, accessGroup, connection, request, mapping); // Prod: 12.30 mins
                message += " " + doAction("GeolocaleCountCrawl", form, accessLogin, accessGroup, connection, request, mapping); // Prod: 30.48 mins
                GeolocaleMgr.populate(connection, true, true);
                doAction("geolocaleTaxonFix", form, accessLogin, accessGroup, connection, request, mapping); // Prod: 1.00 mins
            }

            // dev: 7.05 mins. Prod: 14.52 mins
            if ("set3".equals(action)) {
                message = "set3 - ";
                message += " " + doAction("ProjectCountCrawl", form, accessLogin, accessGroup, connection, request, mapping);   // Prod: 0.67 mins
                message += " " + doAction("populateMuseum", form, accessLogin, accessGroup, connection, request, mapping);  // was 12.05 mins now 22.68 mins. Prod: 8.17
                message += " " + doAction("populateBioregion", form, accessLogin, accessGroup, connection, request, mapping); // was 6.80 mins. Now 15.63 mins. // Prod: 5.52 mins
                message += doAction("updateTaxonSetTaxonNames", form, accessLogin, accessGroup, connection, request, mapping);
                message += " " + doAction("crawlForType", form, accessLogin, accessGroup, connection, request, mapping);
            }

            // dev: 6.58 mins. Prod: 15.17 mins
            if ("set4".equals(action)) {
                message = "set4 - ";
                // message += doAction("allCountCrawls", form, accessLogin, accessGroup, connection, request, mapping);
                message += doAction("imageCountCrawl", form, accessLogin, accessGroup, connection, request, mapping);
                // message += " " + doAction("calcEndemic", form, accessLogin, accessGroup, connection, request, mapping);
                // message += " " + doAction("calcIntroduced", form, accessLogin, accessGroup, connection, request, mapping);
            }

            // dev: 15.22 mins. Prod: 50.98 mins
            if ("set5".equals(action)) {
                message = "set5 - ";
                message += " " + doAction("deleteConflictedDefaultImages", form, accessLogin, accessGroup, connection, request, mapping);
                message += " " + doAction("genObjectMaps", form, accessLogin, accessGroup, connection, request, mapping); // Prod: 48.93 mins
                message += " " + doAction("deleteOldSpecimenUploadTaxa", form, accessLogin, accessGroup, connection, request, mapping);
                message += " " + doAction("checkAntwiki", form, accessLogin, accessGroup, connection, request, mapping);
            }
        }
		// ---------- Data Loading --------------------

        if (action.equals("updateFormicidaeProjects")) {
	        ProjTaxonDb projTaxonDb = new ProjTaxonDb(connection);
            projTaxonDb.addProjectFamily("worldants");
            message = "updateFormicidaeProjects";
        }

		if (action.equals("testWorldantsLog")) { 
		  operationDetails = new UploadDetails("testWorldantsLog", "Testing Worldants Log");	
          operationDetails.setMessage("success");

          A.log("testWorldantsLog");
		  
          ((UploadDetails) operationDetails).finish(accessLogin, request, connection);
    		  
          return operationDetails;		  
		}
		
		// 17.67 mins. success in 13.45 mins. success in 12.28 mins (after 1 constraint check removed).
		// success in 12.97 mins.		
		if (action.equals("worldantsFetchAndReload")) { 
		  operationDetails = (new SpeciesListUploader(connection)).worldantsFetchAndReload();	

		  LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr() + " worldantsFetchAndReload: " + operationDetails.getMessage());
		  
          ((UploadDetails) operationDetails).finish(accessLogin, request, connection);
    		  
          return operationDetails;		  
		}
		if (action.equals("worldantsReload")) {
		  operationDetails = (new SpeciesListUploader(connection)).worldantsReload();
		  if ("success".equals(operationDetails.getMessage())) message = "worldantsReload";		
		  LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr() + " worldantsReload: " + operationDetails.getMessage());
          A.log("doAction() operationDetails:" + operationDetails + " message:" + operationDetails.getMessage());  
          return operationDetails;
		}

		if (action.equals("adminAlertTest")) { 
            int worldantsChangeCount = (new WorldantsUploadDb(connection)).getWorldantsChangeCount();
            
            A.log("adminAlertTest: " + worldantsChangeCount);
            if (worldantsChangeCount == 0) {
              AdminAlertMgr.addIfFresh(AdminAlertMgr.noWorldantsChanges, AdminAlertMgr.noWorldantsChangesContains, AdminAlertMgr.WEEK, connection);
            }  

            AdminAlertMgr.add("Test", connection);
            
            AdminAlertMgr.log("Test");
        }
 
        // 35 sec
		if (action.equals("taxonFinish")) { 
		  (new TaxonDb(connection)).setSubfamilyChartColor();
		  message = "taxonFinished";
		}

/*
        // The country and bioregion are included in worldants file. Set source (and links)
        // for these records (as they are most authoritative).
		if (action.equals("worldantsSource")) { 
		  (new GeolocaleTaxonDb(connection)).worldantsSource();
		  (new BioregionTaxonDb(connection)).worldantsSource();
		  message = "worldantsSource";
		}

*/				  
		if (action.equals("plaziData")) {
		  PlaziDataAction plaziDataAction = new PlaziDataAction();                    
		  plaziDataAction.fetchPlaziData(connection);
          message = "Plazi Data fetched";
		}

		// This, after a regional Taxon List uploaded as a data file from the curate page
		// will push the data into geolocale. Inserting the record if it does not exist.
		if (action.equals("populateFromAntwikiData")) {
		  GeolocaleTaxonDb geolocaleTaxonDb = new GeolocaleTaxonDb(connection);
		  geolocaleTaxonDb.undoPopulateFromAntwikiData();
		  message = geolocaleTaxonDb.populateFromAntwikiData();
		}		

		if (action.equals("geolocaleTaxonFix")) {
		  GeolocaleTaxonDb geolocaleTaxonDb = new GeolocaleTaxonDb(connection);
		  String result = geolocaleTaxonDb.geolocaleTaxonFix();
		  message = result;
		}		

        // ---------------Overviews -------------------
			  	
		// Update to Current Valid Name.  3.77 mins.
		// Test if needed: http://localhost/antweb/util.do?action=curiousQuery&name=geolocaleTaxaNotUsingCurrentValidName	  	
 		if (action.equals("updateTaxonSetTaxonNames")) {
          message = TaxonSetDb.updateTaxonSetTaxonNames(connection); // works on all taxon sets.
        }
		   
        // This should only need to be run once. No! Species list tool will not allow addition.
        // But could have a specimen removed from a specimen upload. Keep it in the scheduler.
		if (action.equals("dataCleanup")) {
		  message = TaxonDb.deleteSpeciesWithoutSpecimenOrAntcatSource(connection);
		  message = TaxonDb.deleteGeneraWithoutSpecimenOrAntcatSource(connection);
          message += TaxonSetDb.dataCleanup(connection);
		}	

		if (action.equals("parseDates")) {
  	 	 // DateUtil.runTests();
          SpecimenDb specimenDb = new SpecimenDb(connection);
          message = specimenDb.parseDates();
		}	

		    		
		// ---- Geolocale ----------
		
		// 1.85 min
		//   Must come before the geolocale crawls
		if (action.equals("generateGeolocaleTaxaFromSpecimens")) {
		  message = (new GeolocaleTaxonDb(connection)).generateGeolocaleTaxaFromSpecimens();
		  message = "Finished Generate Geolocale Taxa From Specimens message:" + message;
		}

		// Very fast. Run occasionally to get adm1s in line with hierarchy. Should be rare. Unnecessary.
		//if (action.equals("geolocaleTaxonCheckIntegrity")) {
		if (action.equals("fixGeolocaleTaxonParentage")) {
		  message = new GeolocaleTaxonDb(connection).fixGeolocaleTaxonParentage();
		}

		if (action.equals("testGeolocaleTaxonParentage")) {
		  message = new GeolocaleTaxonDb(connection).testGeolocaleTaxonParentage();
		}
		
		// Not used yet...
		if (action.equals("setSpecimenSource")) {
		  message = new GeolocaleTaxonDb(connection).setSpecimenSource();
		}
			  
		// Geolocale counts updated 6.30 mins	  
		if (action.equals("GeolocaleCountCrawl")) {
		  int geolocaleId = form.getNum();
		  if (geolocaleId > 0) {
			(new GeolocaleDb(connection)).updateCounts(geolocaleId);
		  } else {			    
            CountDb.s_isBulk = true;
			(new GeolocaleDb(connection)).updateCounts();   // ~13.38 mins.
            CountDb.s_isBulk = false;
		  }                 
		  message = "Updated Geolocale Counts. ";
		}

		if (action.equals("updateGeolocaleParentHierarchy")) {
		  // Geolocales form a hierarchy but we would also like to know region, subregion, country
		  // up the parent chain for any geolocale (not just parent). Easy enought in SQL to touch up
		  // region, subregion and country, but hard to get the subregion for adm1. Here we do that.
		  (new GeolocaleDb(connection)).updateGeolocaleParentHierarchy();
		  message = "Finished update Geolocale Parent Hierarchy";
		}		
		
			
		// ---- Bioregion ----------
		// Bioregion populated 1.93 mins
		// for instance: /utilData.do?action=populateBioregion&name=indomalaya  
		// 7 sec for afrotropical
		if (action.equals("populateBioregion")) {
		  String bioregion = form.getName();   // ex: Afrotropical		  
		  String appendStr = "";
		  if (bioregion != null) {
		    bioregion = Formatter.initCap(bioregion);
			appendStr += ":" + bioregion;
			(new BioregionDb(connection)).populate(bioregion);
		  } else {
			(new BioregionDb(connection)).populate();
		  }
		  message = "Bioregion populated" + appendStr;                
		}		  

        // ------ Museums ---------
		//Museum populated 7.22 mins
		if (action.equals("populateMuseum")) {
		  (new TaxonDb(connection)).setSubfamilyChartColor();
		  String museumCode = form.getCode();   
		  // ex: /utilData.do?action=populateMuseum&code=ZMHB finished in 4.07 mins
		  // JTLC, CASC

		  MuseumDb museumDb = new MuseumDb(connection);
		  String appendStr = "";
		  if (museumCode != null && !"".equals(museumCode)) {
			appendStr += ":" + museumCode;
			museumDb.populate(museumCode);
		  } else {
			museumDb.populate();
		  }
		  museumDb.updateMuseum();

		  message = "Museum populated" + appendStr;                 
		}

        // ------ Project ----------

		// Fast.  0.40 min.
		// http://localhost/antweb/utilData.do?action=updateProjectCounts&name=allantwebants
		if (action.equals("ProjectCountCrawl")) {
		  String projectName = form.getName();
		  String appendStr = "";
		  ProjectDb projectDb = new ProjectDb(connection);
		  if (projectName != null) {
			projectDb.updateCounts(projectName);
			appendStr += ":" + projectName;
		  } else {
			projectDb.updateCounts();
		  }
		  message = "Project" + appendStr + " counts updated";
		}

		if (action.equals("regenerateAllAntweb")) {
		  ProjTaxonDb projTaxonDb = (new ProjTaxonDb(connection));
		  projTaxonDb.regenerateAllAntweb();            // Proj_taxon records
		  message = "All Antweb regenerated";

          /* moved into regen
          (new ProjTaxonCountDb(connection)).countCrawl("allantwebants"); // Proj_taxon counts
		  projTaxonDb.finishRegenerateAllAntweb();
		  (new ProjectDb(connection)).updateCounts("allantwebants");      // Project counts		 
          */
		}	

    // ---------- Count Crawls -------------------------
		// https://antweb-stg/utilData.do?action=geolocaleTaxonCountCrawl&num=392
		if (action.equals("geolocaleTaxonCountCrawl")) {
		  GeolocaleTaxonCountDb geolocaleTaxonCountDb = (new GeolocaleTaxonCountDb(connection));
          if (num == 0) {
            s_log.warn("countCrawl expecting num:" + num + ". Running ChildrenCountCrawls for all geolocales.");
		    geolocaleTaxonCountDb.childrenCountCrawl();
		  } else {
		    geolocaleTaxonCountDb.childrenCountCrawl(num);
		  }
		  message = "Finished Geolocale Count Crawl (" + form.getNum() + ")";
		}

        // Generally not needed as it runs as a part of populate(code).
		// http://localhost/antweb/utilData.do?action=museumTaxonCountCrawl&code=AFRC
		if (action.equals("museumTaxonCountCrawl")) {
		  MuseumTaxonCountDb museumTaxonCountDb = (new MuseumTaxonCountDb(connection));
          if (code == null || "".equals(code)) {
            s_log.warn("countCrawl expecting num:" + code + ". Running ChildrenCountCrawls for all museum.");
		    museumTaxonCountDb.childrenCountCrawl();
		  } else {
		    museumTaxonCountDb.childrenCountCrawl(code);
		  }
		  message = "Finished Museum Count Crawl (" + form.getNum() + ")";
		}

		if (action.equals("bioregionTaxonCountCrawl")) {
		  BioregionTaxonCountDb bioregionTaxonCountDb = (new BioregionTaxonCountDb(connection));
          if (name == null || "".equals(name)) {
            s_log.warn("countCrawl expecting name:" + name + ". Running ChildrenCountCrawls for all bioregions.");
		    bioregionTaxonCountDb.childrenCountCrawl();
		  } else {
		    bioregionTaxonCountDb.childrenCountCrawl(name);
		  }
		  message = "Finished Bioregion Count Crawl (" + form.getName() + ")";
		}

        // Useful for debugging? Code to execute and get results or a particular count.
		if (action.equals("countReport")) {
		  GeolocaleTaxonCountDb geolocaleTaxonCountDb = (new GeolocaleTaxonCountDb(connection));
          if (num == 0) {
            message = "countCrawl expecting num:" + num + ". Running ChildrenCountCrawls for all geolocales.";
            s_log.warn(message);
		  } else {
		    message = "Finished Geolocale Count Report (" + form.getNum() + ") " + geolocaleTaxonCountDb.countReport(num);
		  }
		}

		if (action.equals("imageCountCrawl")) {
		  ImageCountDb imageCountDb = (new ImageCountDb(connection));
		  imageCountDb.imageCountCrawls();
		  message = "Finished Image Count Crawls";
		}

        //  ---- Debugging Methods ---- 
        // This adequately computes geolocale_taxon image counts in 10.62 min. Subset of allCountCrawls.			  
        if (action.equals("geolocaleTaxonImageCountCrawl")) {
          int id = form.getId();
          if (id > 0) {
            (new GeolocaleTaxonCountDb(connection)).imageCountCrawl(id);                    
          } else {
            (new GeolocaleTaxonCountDb(connection)).imageCountCrawl();
          }
          message = "Finished Geolocale Image Count Crawl";
        }   

        // 37 sec.  Called following specimen upload.
		if (action.equals("crawlForType")) {
		  s_log.warn("execute() crawlForType");
		  (new TaxonDb(connection)).crawlForType();
		  message = "Finished crawl for type";
		}

		// ---------- Testing ------------		  
		// This is useful following the worldants species list upload.	
		
		// 50 sec		   
		// handled during GeolocaleDb.updateCounts()
		if (action.equals("calcEndemic")) {
		  message = (new GeolocaleDb(connection)).calcEndemic() + " geolocale endemcs calculated, ";
		  message += (new BioregionDb(connection)).calcEndemic() + " bioregion endemics calculated";
		}

        // 40 sec
		// handled during GeolocaleDb.updateCounts()
		if (action.equals("calcIntroduced")) {
		  message = (new GeolocaleDb(connection)).calcIntroduced() +  " geolocale introduced calculated, ";
		  message += (new BioregionDb(connection)).calcIntroduced()  + " bioregion introduced calculated.";
		}
           

        if (action.equals("getCurations")) {
            String curationsDml = "";
            ArrayList<Curation> curations = (new GeolocaleTaxonLogDb(connection)).getCurations();  
            int i = 0;
            message += "<h2>Curations</h2><br>See log for dml.";
            for (Curation curation : curations) {
              ++i;
              if (curation != null) {
                Geolocale geolocale = GeolocaleMgr.getGeolocale(curation.getGeolocaleId());
                message += "<br>" + geolocale.getGeorank() + ":" + geolocale + " taxon:" + curation.getTaxonName();
                
                curationsDml += "<br>insert into geolocale_taxon (geolocale_id, taxon_name, source) " 
                  + " values (" + geolocale.getId() + ", '" + curation.getTaxonName() + "', '" + Source.CURATOR + "');";
              }
            }
            message += "<br><br>Total:" + i;
            message += "<br><br><h2>DML</h2><br><br>" + curationsDml;
        }

// ---------- Geo Data Methods. (Many are deprecated) -----------

        if (action.equals("gson")) {
          Geolocale country = GeolocaleMgr.getCountry("Algeria");
          Gson gson = new Gson();
          message = " country:" + country + " gson:" + gson.toJson(country);                    
        }

        // This will populate country records with Flickr woe_id, bounds and centroid.
        if (action.equals("fetchFlickrCountryData")) {
          message = FlickrPlace.fetchCountryData(connection);
        }

        // This will populate adm1 records from Geonames.org.
        if (action.equals("fetchGeonamesData")) {   
          message = GeonamesPlace.fetchData(connection);
        }

        // This will populate more adm1 records (as invalid) from Flickr data.
        if (action.equals("fetchFlickrAdm1Data")) {   
          message = FlickrPlace.fetchAdm1Data(connection);
        }

        // This will run all three of the above.
        if (action.equals("fullGeodataFetch")) {   
          message = FlickrPlace.fetchCountryData(connection);
          AntwebMgr.populate(connection, true); 
          message += GeonamesPlace.fetchData(connection);
          AntwebMgr.populate(connection, true); 
          message += " " + FlickrPlace.fetchAdm1Data(connection);
        }
        
        if (action.equals("geodataTestFetch")) {   
          //String country = "Russia";
          String country = "Belgium";
          message = GeonamesPlace.fetchData(connection, country);
          message += " " + FlickrPlace.fetchAdm1Data(connection, country);
        }

        if (action.equals("fetchGeonetData")) {   
          message = GeonetMgr.fetchData(connection);
        }                  

        // can run for a single country as /utilData.do?action=fetchGeonetCentroidData&country=Comoros
        if (action.equals("fetchGeonetCentroidData")) {   
          if (form.getCountry() != null) {
            // param might be country
            message = GeonetMgr.fetchCentroidData(connection, form.getCountry());
          } else {
            message = GeonetMgr.fetchCentroidData(connection);
          }
        }

        if (action.equals("fetchGoogleApisData")) {   
          int id = 0;
          //Adm1 adm1 = (Adm1) GeolocaleMgr.getAdm1("Wainikeli", "Fiji");	                    
          id = form.getId();
          //if (AntwebProps.isDevMode() && id == 0) id = 5315;				  
          if (id > 0) {
            message += GoogleApisMgr.fetchData(connection, id);
          } else {
            message += GoogleApisMgr.fetchData(connection);
          }                
        }        

// ----------------- Assorted --------------------

        // Takes about 3 minutes.
        if ("testProjTaxon".equals(action)) {
          (new ProjTaxonDb(connection)).testProjTaxon();
          message = "testProjTaxon finished " + AntwebUtil.getMinsPassed(startTime);
        }

        // Not currently functioning.
        // Designed to capture all logs for inspection in case of an issue.
        if (action.equals("archiveLogs")) { 
          message = LogMgr.archiveLogs();
        } 

        // Very fast. Run occasionally to get adm1s in line with hierarchy. Should be rare. Unnecessary.
        if (action.equals("updateAdm1FromCountryData")) {   
          (new GeolocaleDb(connection)).updateAdm1FromCountryData();
          message = "Update Adm1 From Country Data";;
        }

        // Full set is 8.27 mins.  After transactions - 9.17 mins!
        // caste:1 in 3.45 mins
        // This is not run after specimen upload, but process does it.
        // Not sure if this is needed in the scheduler.
        if (action.equals("calcCaste")) {
          SpecimenDb specimenDb = new SpecimenDb(connection);
          //groupId = num;		
          String appendStr = "";
          if (groupId > 0) appendStr = "groupId:" + groupId;
          if (groupId > 0) {
            message += ":" + groupId;
            specimenDb.calcCaste(groupId);
          } if (code != null) {
            message += ":" + code;
            specimenDb.calcCaste(code);				
          } else {
            specimenDb.calcCaste();
          }
          message = "calcCaste";          
        }
        
        if (action.equals("siteWarning")) {
          A.log("text:" + text);
          AntwebMgr.createSiteWarning(text);
          message = "SiteWarning set:" + text;
        }

     // --- Map functions

        if (action.contains("updateNullLocalityCodes")) {
          SpecimenDb specimenDb = new SpecimenDb(connection);
          specimenDb.updateNullLocalityCodes();
          message = "updateNullLocalityCodes";   
        }
          
        if (action.contains("delGoogleMapFunction")) {
          int id = num;
          if (id > 0) {
              ObjectMapDb objectMapDb = new ObjectMapDb(connection);
              objectMapDb.deleteMap("geolocale_id = " + id);                 
          }
          message = "message: delete Google Map for id:" + id;
        }

        if (action.contains("genGoogleMapFunction")) {
            // param is country. param2 is adm1
            String country = param;
            String adm1 = param2;
            int id = num;

            //HttpUtil.getUrl(AntwebProps.getDomainApp() + "/utilData.do?action=genGoogleMapFunction&num=" + id + "&param=allow");
  
            s_log.warn("action=genGoogleMapFunction id:" + id + " country:" + country + " adm1:" + adm1);
            Geolocale geolocale = null;
            if (adm1 != null) {
              geolocale = GeolocaleMgr.getAdm1(adm1, country);
            } else if (country != null) {
              geolocale = GeolocaleMgr.getCountry(country);
            } else if (id != 0) {
              geolocale = GeolocaleMgr.getGeolocale(num);
              if ("country".equals(geolocale.getGeorank())) {
                country = geolocale.getName();
              } else if ("adm1".equals(geolocale.getGeorank())) {
                country = geolocale.getParent();
                adm1 = geolocale.getName();                  
              }
              A.log("UtilDataAction.genGoogleMapFunction georank:" + geolocale.getGeorank() + " geolocale:" + geolocale + " country:" + country + " adm1:" + adm1);
            }
            if (geolocale == null || !("country".equals(geolocale.getGeorank()) || "adm1".equals(geolocale.getGeorank()))) {
              message = "Geolocale not found for num(id):" + num + " param(country):" + param + " param2(adm1):" + param2;
            } else {
              A.log("UtilData.genGoogleMapFunction() id:" + id + " country:" + country + " adm1:" + adm1 + " geolocale:" + geolocale);
              SearchAction.setTempSpecimenSearchLimit(SearchAction.noSpecimenSearchLimit);

              Map map = (new AdvancedSearchAction()).getGoogleMap(country, adm1, ResultRank.SPECIMEN, Output.MAP_LOCALITY, connection);

              SearchAction.undoSetTempSpecimenSearchLimit();
            
              //String googleMapFunction = map.getGoogleMapFunction();
              //A.log("title:" + map.getTitle() + " subtitle:" + map.getSubtitle());
              //A.log("UtilData.genGoogleMapFunction googleMapFunction:" + googleMapFunction);

              if (map != null) {
                ObjectMapDb objectMapDb = new ObjectMapDb(connection);     
                objectMapDb.setGeolocaleMap(geolocale.getId(), map);                     
              }

              message = "title:" + map.getTitle() + " subtitle:" + map.getSubtitle() + " info:" + map.getInfo() + " googleMapFunction.length:" + map.getGoogleMapFunction().length();
            }
            if (message == null) message = "genGoogleMapFunction";
        }		
        
        if (action.contains("genObjectMaps")) {
            ObjectMapDb objectMapDb = new ObjectMapDb(connection);
            objectMapDb.genObjectMaps(); 
            message = "message: genObject Maps executed.";
        }

        if ("genGroupObjectMaps".equals(action)) {
            ObjectMapDb objectMapDb = new ObjectMapDb(connection);
            if (num > 0) {
              objectMapDb.genGroupObjectMap(num); 
            } else {
              objectMapDb.genGroupObjectMaps(); 				  
            }
            message = "genObject Maps executed.";
        }			  

        // testing
        if ("deleteOrphans".equals(action)) {
          (new OrphansDb(connection)).deleteOrphanedSpeciesFromSource("specimen1.txt");
          message = "deleteOrphans";
        }
                    
        if (action.contains("updateGroupCounts")) {
            UploadDb uploadDb = new UploadDb(connection);
            uploadDb.updateCounts(); 
            message = "groupDb Update Counts executed.";
        }

        if (action.contains("updateGroupUploadStats")) {
            ArrayList<Group> groups = GroupMgr.getGroups();
            UploadDb uploadDb = new UploadDb(connection);
            for (Group group : groups) {                  
                uploadDb.updateGroup(group);
            }
            message = "group first_specimen_update and upload_counts updated.";
        }			  

        if (action.equals("specimenPostProcess")) {
          // (new UploadAction.specimenPostProcess(connection);
          (new GroupDb(connection)).updateUploadSpecimens();

          message = "specimenPostProcess";
        }

        if (action.equals("deleteConflictedDefaultImages")) {
          message = (new TaxonPropDb(connection)).deleteConflictedDefaultImages();
        }

        // Morpho taxa created by prior Specimen list upload.
        if (action.equals("deleteOldSpecimenUploadTaxa")) {
            //message = (new TaxonDb(connection)).deleteOldSpecimenUploadTaxa();
        }

        if (action.equals("checkAntwikiForUpdates")) {
			message = AntWikiDataAction.checkForUpdates(connection);			
			A.log("doAction() Check For Antwiki Species and Fossil List Updates output:" + message);
        }

// ----------- Debug -------------
        
        // 597493 specimen records updated in 1.28 mins  (with no num param).
        // 8016 specimen records updated for group:27:Curator Matthew Prebus in 0.08 mins  
        // 203774 specimen records updated for group:1:California Academy of Sciences in 0.43 mins
        // Is after every specimen upload (with groupId as num).
        if (action.equals("updateSpecimenStatus")) {
          SpecimenDb specimenDb = new SpecimenDb(connection);

          groupId = num;				
          if (groupId > 0) {
            message = specimenDb.updateSpecimenStatus(groupId);
          } else {
            message = specimenDb.updateSpecimenStatus();
          }
        }
        
        if (action.equals("taxaOutsideOfNativeBioregion")) {
          String taxaOutside = new GeolocaleTaxonDb(connection).getTaxaOutsideOfNativeBioregion();
          message = taxaOutside + " " + AntwebUtil.getMinsPassed(startTime);
        }
        
        if (action.equals("fixHtmlAuthorDates")) {
          (new TaxonDb(connection)).fixHtmlAuthorDates();
          message = "fixHtmlAuthorDates";
        }
        
        if (action.equals("exifData")) {
          (new ImageDb(connection)).getExifData();
          message = "getExifData()";		  
        }
/*
        if (action.equals("imageSecure")) {
            message = (new DescEditDb(connection)).getImageSecure();
        }
*/
        if (action.equals("imageUtil")) {
          s_log.warn("execute() imageUtil");  //"CASENT0101586";
          try {
            if ("updateExif".equals(form.getName())) ImageUtil.execute("updateExif", connection);
            if ("count".equals(form.getName())) ImageUtil.execute("count", connection);
          } catch (InterruptedException e) {
            A.log("e:" + e);
          }
        }

                    
// ----------- Deprecated -------------

        if (action.equals("moveImages")) {
          s_log.warn("execute() moveImages code:" + code);  //"CASENT0101586";
          message = AntwebFunctions.moveImages(code);
        }     
        if (action.equals("changeOwner")) {
          s_log.warn("execute() changeOwner code:" + code);  //"CASENT0101586";
          message = AntwebFunctions.changeOwner(code);
        }     
        if (action.equals("changeOwnerAndPerms")) {
          s_log.warn("execute() changeOwnerAndPerms code:" + code);
          message = AntwebFunctions.changeOwnerAndPerms(code);
        } 

                    
/*
        // This to get geolocale_taxon in alignment with specimen.
        if (action.equals("processSpecimen")) {
            (new GeolocaleTaxonDb(connection)).processSpecimen();
            message = "Geolocale Taxon Process Specimen";
        }
        
        // To be called after data change. endemic data into geolocale_taxon and geolocale.
        if (action.equals("finishCountryUpload")) {
          message = (new GeolocaleTaxonDb(connection)).finishCountryUpload();
        }
*/
	   
        if ("".equals(message) || message == null) {
          s_log.warn("doAction() message:" + message + " for action:" + action);
          operationDetails.setMessage("action:" + action + " not found");
          return operationDetails;
        }

		message += "<br><br> in " + AntwebUtil.getMinsPassed(startTime) + ". ";	   
	    s_log.warn("doAction() action:" + action + " message:" + message);
        operationDetails.setMessage(message);
        operationDetails.setForwardPage(null);
        return operationDetails;
    }    
    
    public static boolean isInComputeProcess() {
      return m_inComputeProcess != null;
    }
    public static String getIsInComputeProcess() {
      return m_inComputeProcess;
    }
    public static void setInComputeProcess(String inComputeProcess) {
      m_inComputeProcess = inComputeProcess;
    }
  
}


