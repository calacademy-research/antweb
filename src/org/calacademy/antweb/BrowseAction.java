package org.calacademy.antweb;

import java.io.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import org.apache.struts.action.*;
import java.sql.*;
import java.util.Date;
import java.util.MissingResourceException;

import javax.sql.DataSource;

import org.calacademy.antweb.home.TaxonDb;
import org.calacademy.antweb.home.HomonymDb;

import org.calacademy.antweb.upload.UploadAction;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.util.exception.*;
import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class BrowseAction extends DescriptionAction {

    private static final Log s_log = LogFactory.getLog(BrowseAction.class);

    public static final int s_mapComparisonLimit = 2;
    public static int s_mapComparisonCount = 0;
    public static final int s_getComparisonLimit = 2;
    public static int s_getComparisonCount = 0;

    /*
    BrowseAction called with browseForm in session:
      browse.do, navigateHierarchy, getComparison, oneView, mapComparison, description.do,
    in request:
      imagePickImageGetter, descriptionEdit,
    */

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        boolean logTimes = AntwebProps.isDevMode() && false;
        if (logTimes) A.log("execute() sort:" + request.getParameter("sortBy") + " " + request.getParameter("sortOrder"));

        String message = null;
        Date startTime = new Date();

        AntwebMgr.isPopulated(); // will force populate 

        if (logTimes) A.log("execute() after AntwebMgr.populate()");

        //if (!AntwebProps.isDevOrStageMode())
        AntwebSystem.cpuCheck();

        // 1st check without connection.
        if (HttpUtil.tooBusyForBots(request)) { HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }


        ActionForward a = Check.init(Check.TAXON, request, mapping); if (a != null) return a;
        ActionForward d = Check.valid(request, mapping); if (d != null) return d;

        Check.adminTest(request, mapping);

        if (ProjectMgr.hasMoved(request, response)) return null;

        if (logTimes) A.log("execute() 1");

        HttpSession session = request.getSession();
        Login accessLogin = LoginMgr.getAccessLogin(request);

        BrowseForm browseForm = (BrowseForm) form;
        String rank = browseForm.getRank();
        //boolean local = browseForm.getLocal(); // unnecessary now because non-global is the default.
        boolean global = browseForm.getGlobal();

        if (logTimes) A.log("execute() 1.1");

        // When and why would we want to do this?
        // Not for: http://localhost/antweb/description.do?subfamily=formicinae&genus=camponotus
        String queryString = request.getQueryString();

        if (logTimes) A.log("execute() 1.2 queryString:" + queryString);

        if (queryString == null) {
          request.setAttribute("message", "Invalid: no query string");
          return mapping.findForward("message");
        }
        //if (!queryString.contains("?")) queryString = "?" + queryString;
        boolean isPost = HttpUtil.isPost(request);

        if (rank == null || "".equals(rank)) rank = inferredRank(queryString);

        if (Rank.SUBGENUS.equals(rank)) rank = Rank.GENUS;

        if (logTimes) A.log("execute() 2 rank:" + rank);

        String family = browseForm.getFamily();
        String subfamily = browseForm.getSubfamily();
        String genus = browseForm.getGenus();
        //String subgenus = browseForm.getSubgenus();
        String species = browseForm.getSpecies();
        String subspecies = browseForm.getSubspecies();
        String[] chosen = browseForm.getChosen();
        String authorDate = browseForm.getAuthorDate();

        String name = browseForm.getName();
        if (name != null) {
          if (Rank.FAMILY.equals(rank)) family = name;
          if (Rank.SUBFAMILY.equals(rank)) subfamily = name;
          if (Rank.GENUS.equals(rank)) genus = name;
          if (Rank.SPECIES.equals(rank)) {
            if (name.contains(" ")) {
              rank = Rank.SUBSPECIES;
              species = name.substring(0, name.indexOf(" "));
              subspecies = name.substring(name.indexOf(" ") + 1);
            } else {
              species = name;
            }
          }
        }

        Overview overview = null;
        try {
            overview = OverviewMgr.getAndSetOverview(request);
        } catch (AntwebException e) {
            return OverviewMgr.returnMessage(request, mapping, e);
        }

        String title = overview.getTitle(); 
        //A.log("execute() title:" + title + " overview:" + overview);

        if (logTimes) A.log("execute() 3");

        // This block of code will handle a request that comes in with a taxonName parameter 
        // by building up and redirecting to a new request.
        boolean hasQueryString = queryString != null;
        if (browseForm.getTaxonName() != null && !"".equals(browseForm.getTaxonName()) || browseForm.getAntcatId() != 0) {
          //session.setAttribute("statusSet", StatusSet.ALL);
          A.log("execute() redirect taxonName:" + browseForm.getTaxonName() + " antcatId:" + browseForm.getAntcatId());
          return taxonNameRedirect(browseForm, mapping, request, response);
        } else if (hasQueryString && queryString.contains("antcatId=")) {
            request.setAttribute("message", "Enter an AntCat ID in the url.");
            return mapping.findForward("message");
        } else if (hasQueryString && queryString.contains("taxonName=")) {
            request.setAttribute("message", "Enter a taxon name in the url.");
            return mapping.findForward("message");
        }
                
        if (family == null && subfamily == null && genus == null && species == null) {
            message = "Taxon not found.";
            request.setAttribute("message", message);
            return mapping.findForward("message");
        }
        			
        // Some requests have "&;" in the queryString.  EOL data feed?
        String correctedUrl = HttpUtil.redirectCorrectedUrl(request, response);      // Can't.  Response committed.  Diagnostic.
        if (correctedUrl != null) {
          message = "Error found with URL.  Try this link <a href=\"" + correctedUrl + "\">here</a>.";
          request.setAttribute("message", message);
          return mapping.findForward("message");
        }

        if (logTimes) A.log("execute() 4");

        if("specimen".equals(rank)) {
           message = "Invalid request.";
           if (browseForm.getCode() != null) {
              String link =  AntwebProps.getDomainApp() + "/specimen.do?code=" + browseForm.getCode();
              message += "  Try:<a href='" + link + "'>" + link + "</a>";
           } else {
              message += "  Description.do requests are for taxa, not specimens.";
           }
           request.setAttribute("message", message);
           return mapping.findForward("message");
        }
     
        if (Taxon.getTaxonOfRank(rank) == null) {
             message = "Bad rank:" + rank + " " + AntwebUtil.getRequestInfo(request);
             s_log.info("execute() " + message);
             request.setAttribute("message", message);
             return mapping.findForward("message");
        }
  
        String cacheType = "";            
        // This class (BrowseAction) handles description.do, browse.do, images.do, getComparison, oneView, mapComparison, imagePickImageGetter struts actions.
        String requestUrl = HttpUtil.getRequestURL(request);
        if (requestUrl.contains("description.do")) cacheType = "description";
        if (requestUrl.contains("browse.do")) cacheType = "browse";
        if (requestUrl.contains("images.do")) cacheType = "images";
        if (requestUrl.contains("mapComparison.do")) cacheType = "mapComparison";                     
        if (requestUrl.contains("getComparison.do")) cacheType = "getComparison";
        if (requestUrl.contains("oneView.do")) cacheType = "oneView";
        boolean isGenCache = false;  // parameter has been passed in with url indicating that this request response will be cached.
        boolean isCachable = false;  // One of the struts action types that may be cached (browse.do and description.do)
        if ("true".equals(browseForm.getGenCache())) isGenCache = true;                
        if ("browse".equals(cacheType) || "images".equals(cacheType)) {
           isCachable = true;
        } else if ("description".equals(cacheType)) {   // Nov 13, 2013
           isCachable = false;
        }
        if ("mapComparison".equals(cacheType)
         || "getComparison".equals(cacheType)
           ) {
            if (accessLogin == null) {        
              request.setAttribute("message", Login.MUST_LOGIN_MESSAGE);
              return mapping.findForward("message");
            }
        }

        boolean getChildImages = false;
        boolean getChildMaps = false;
        String theParameter = mapping.getParameter(); 
          //s_log.warn("execute() theParameter:" + theParameter);
        if (theParameter != null) {
          if (theParameter.contains("getChildImages")) getChildImages = true;
          if (theParameter.contains("getChildMaps")) getChildMaps = true;
        }
		//if (AntwebProps.isDevMode() || logTimes) { 
		if ("browse".equals(cacheType)) {
		  getChildImages = true; // We need this.
		  getChildMaps = false; 
		  /* Was false. July 23rd. Was commented: // Don't need this. So we know to display a map link or not.
		     But we do want a map link here:
              http://localhost/antweb/browse.do?genus=anonychomyrma&species=dimorpha&rank=species&project=allantwebants	
             Will leave the code here unchanged but change the logic in the jsp to display the map link.	  
		  */
		}
		if ("mapComparison".equals(cacheType)) {
		  getChildImages = true;
		  getChildMaps = true;
		}

        //A.log("execute() cacheType:" + cacheType + " getChildImages:" + getChildImages + " getChildMaps:" + getChildMaps + " overview:" + overview);

        Taxon taxon = null;

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("BrowseAction.execute()");

        try {
        
          if ("mapComparison".equals(cacheType)) {
            ++s_mapComparisonCount;
            if (s_mapComparisonCount > s_mapComparisonLimit) {
              message = "Simultaneous map comparison limit exceeded.  Please try again later, or log in for unrestricted access.";  // message to bots
              request.setAttribute("message", message);
              //s_log.warn("execute() message:" + message);
              return mapping.findForward("message");
            }
          }
          if ("getComparison".equals(cacheType)) {
            ++s_getComparisonCount;
            if (s_getComparisonCount > s_getComparisonLimit) {
              message = "Simultaneous get comparison limit exceeded.  Please try again later, or log in for unrestricted access.";  // message to bots
              request.setAttribute("message", message);
              //s_log.warn("execute() message:" + message);
              return mapping.findForward("message");
            }
          }        
        
		  DataSource dataSource = getDataSource(request, "conPool");
          //s_log.info("execute() uniqueNumber:" + uniqueNumber + " request:" + HttpUtil.getTarget(request));

		  String target = HttpUtil.getTarget(request);
		  // Overdue resource check outs here may be isolated by utilizing two connections.
          // From: docker-compose exec antweb bash
          // ls /data/antweb/web/log/unclosedConnections/
		  connection = DBUtil.getConnection(dataSource, dbMethodName, target);
		  if (connection == null) {
		      message = "execute() Null connection !!!" + AntwebUtil.getRequestInfo(request);
              s_log.error(message);
		      throw new AntwebException(message);
          }

          SessionRequestFilter.processRequest(request, connection);

		  // 2nd check with connection.
          if (HttpUtil.tooBusyForBots(connection, request)) { HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }

		  TaxonDb taxonDb = new TaxonDb(connection);

          // To be removed when threat of lost geolocale_taxa has passed.
          //(new GeolocaleTaxonDb(connection)).hasCalMorphos(); 

		  if (logTimes) A.log("execute() 5 time:" +  AntwebUtil.millisSince(startTime));

		  /* --- Here is where we fetch the Taxon or Homonym --- */

		  if (!"homonym".equals(browseForm.getStatus()) && browseForm.getAuthorDate() == null) {
			if (taxon == null) {
              if (logTimes) A.log("execute  () family:" + family + " subfamily:" + subfamily + " genus:" + genus + " species:" + species + " subspecies:" + subspecies + " rank:" + rank);

              //Add this check to field guide too..
              String checkMessage = null;
              if (Rank.SUBFAMILY.equals(rank) && (subfamily == null))
                    checkMessage = "Must specify subfamily:" + subfamily + " to get taxon of rank:" + rank + ". ";
              if (Rank.GENUS.equals(rank) && (genus == null))
                  checkMessage = "Must specify genus:" + genus + " to get taxon of rank:" + rank + ". ";
              if (Rank.SPECIES.equals(rank) && (genus == null || species == null))
                  checkMessage = "Must specify genus:" + genus + " and species:" + species + " to get taxon of rank:" + rank + ". ";
              if (Rank.SUBSPECIES.equals(rank) && (genus == null || species == null || subspecies == null))
                  checkMessage = "Must specify genus:" + genus + ", species:" + species + " and subspecies:" + subspecies + " to getFullTaxon of rank:" + rank + ". ";
              if (checkMessage != null) {
                  //s_log.info("execute() " + checkMessage + " requestInfo:" + AntwebUtil.getRequestInfo(request));
              } else {

                  taxon = taxonDb.getFullTaxon(family, subfamily, genus, species, subspecies, rank);

              }
              //if (logTimes) A.log("execute() taxon.getSource:" + taxon.getSource() + " desc:" + taxon.getDescription().size());
			}
		  }
		  if (taxon == null) {
			// Perhaps it is a Homonym...
			taxon = new HomonymDb(connection).getFullHomonym(family, subfamily, genus, species, subspecies, authorDate);
            //A.log("execute() homonym:" + taxon);
			/* See the homonyms of /description.do?subfamily=formicinae&genus=camponotus
			They are actually of rank: subgenus
			*/
		  }              
		  //if (taxon != null) A.log("execute() authorDate:" + browseForm.getAuthorDate() + " class:" + taxon.getClass());

		  if (taxon == null) {
			  message = "Taxon not found.";
			  request.setAttribute("message", message);
			  return mapping.findForward("message");
		  }

          if (!Status.VALID.equals(taxon.getStatus()) && Project.WORLDANTS.equals(overview.getName())) {
            // Should we only change this if in worldants?
              Overview allantwebants = ProjectMgr.getProject(Project.ALLANTWEBANTS);
              OverviewMgr.setOverview(request, allantwebants);
              overview = allantwebants;
          }
          //A.log("execute() 2 desc:" + taxon.getDescription().size());

		  taxon.initTaxonSet(connection, overview);

          // This is only used to specify homonym.
          String statusStr = browseForm.getStatus();
          String statusSetStr = StatusSet.getStatusSet(browseForm.getStatusSet(), request, overview);
          String statusSetSize = StatusSet.getStatusSetSize(request);
          
          taxon.setStatusSetStr(statusSetStr);
          taxon.setStatusSetSize(statusSetSize);
	      //A.log("execute() statusStr:" + statusStr + " statusSetStr:" + statusSetStr + " statusSetSize:" + statusSetSize);

          if (logTimes) A.log("execute() family:" + family + " subfamily:" + subfamily + " genus:" + genus
            + " species:" + species + " subspecies:" + subspecies + " rank:" + rank + " overview:" + overview
            + " statusStr:" + statusStr + " statusSetStr:" + statusSetStr + " resetProject:" + browseForm.getResetProject()
            + " taxon.status:" + taxon.getStatus());

		  if (taxon.getTaxonSet() == null) {
              // if (!ProjectDb.projectHasTaxon(projectName, taxon, connection)) {
			  message = "Taxon:" + taxon.getTaxonName() + " not found for overview:" + overview;
			  request.setAttribute("message", message);
			  return mapping.findForward("message");
		  }

		  String facet = HttpUtil.getFacet(request);
		  taxon.setChangeViewOptions(connection, overview, facet);
						
		  //A.log("execute() saveDescriptionEdit accessGroup:" + accessGroup + " taxon:" + taxon); 
		  boolean success = saveDescriptionEdit(browseForm, taxon, accessLogin, request, connection);   
		  if (!success) return mapping.findForward("message");

		  if (accessLogin != null) getDescEditHistory(taxon, connection, request);

		  taxon.setBrowserParams(queryString);
		  //A.log("execute() setBrowserParams:" + queryString);
		  //taxon.setSimilar(projectName);

		  if (logTimes) A.log("execute() setImages() before");

		  String caste = Caste.DEFAULT; //ALL;
		  if ("images".equals(cacheType)) {
		    caste = Caste.getCaste(browseForm.getCaste(), request);
            //A.log("execute() caste:" + caste);
  		    //taxon.setImages(overview, caste);
          } else {
  		    //taxon.setImages(null, caste); // Do not use overview to select images on the overview page. Do for images.do.
  		    // Otherwise, no image here: http://localhost/antweb/description.do?genus=aphaenogaster&species=fulva&rank=species&countryName=Madagascar
          }

          String subgenus = browseForm.getSubgenus();
          //A.log("execute() subgenus:" + subgenus);

  		  taxon.setImages(connection, overview, caste);

		  if (logTimes) A.log("execute() statusSetStr:" + statusSetStr);

		  //A.log("execute() cacheType:" + cacheType + " childImages:" + getChildImages + " childMaps:" + getChildMaps);

		  if (!"description".equals(cacheType)) {
			//StatusSet statusSet = new StatusSet(StatusSet.ALL); //statusSetStr); // Sep 2017
			StatusSet statusSet = new StatusSet(statusSetStr);

			/*  
                Investigation Sep 20 2018. Bug. If one goes to a worldants page and then searches
                on a non-valid page, no specimens or images will show up. From this page:
                https://www.antweb.org/browse.do?genus=rhopalothrix&species=jtl014&rank=species
                go to worldants, back and refresh would have emptied list. Fixed.		
			
			    Investigation Aug 20 2018. The above line was in place causing the status selector
				to not work. Not sure what "statusSet=vald browse bug" might result. By using the
				statusSetStr above we have the selector working correctly on this page:
   	  	        http://localhost/antweb/browse.do?subfamily=dolichoderinae&rank=subfamily&bioregionName=Neotropical		
			*/
              
			taxon.setChildren(connection, overview, statusSet, getChildImages, getChildMaps, caste, global, subgenus);

            if (logTimes) A.log("execute() childrenSize:" + taxon.getChildren().size());

			//taxon.setChildren(projectObj, statusSet, getChildImages, getChildMaps);                    
		  }
		  //if (logTimes) s_log.warn("execute() *** taxon:" + taxon + " class:" + taxon.getClass() + " getChildImages:" + getChildImages);   

		  if (logTimes) A.log("execute() after setChildren time:" +  AntwebUtil.millisSince(startTime) + " cachetype:" + cacheType + " getChildImages:" + getChildImages);

/*
A page like this has no map, so shouldn't be calculated.
  https://www.antweb.org/browse.do?subfamily=ponerinae&rank=subfamily&adm1Name=Atakora&countryName=Benin

We are showin the full map of ponerinae for every adm1.
  https://www.antweb.org/browse.do?subfamily=ponerinae&rank=subfamily&adm1Name=Atakora&countryName=Benin
  We should probably generate a single ponerinae map and store it in taxon_props table.
  No. See Map:493. We do seem to use it.
  
*/

		  Map map = null;
		  if ("description".equals(cacheType) || "images".equals(cacheType)) { // || "browse".equals(cacheType)) { // || "images".equals(cacheType)) { // Wait, don't need on images page!? ) {
		    if (overview instanceof LocalityOverview) {
			 
		/*
			  if ("ponerinae".equals(taxon.getTaxonName())) {
			     s_log.warn("execute() INVESTIGATE too much, too long? taxon:" + taxon + " overview:" + overview);
			  }
*/
              LocalityOverview localityOverview = (LocalityOverview) overview;
              //A.log("execute() getMap(" + taxon + ", " + localityOverview + ")");
              map = MapMgr.getMap(taxon, localityOverview, connection);
			}// else if (overview instanceof Geolocale) {
            //  Project project = GeolocaleMgr.getProject((Geolocale)overview);
            //  map = new Map(taxon, project, connection);
            //  A.log("execute() 2 cacheType:" + cacheType + " overview:" + overview + " map:" + map);
            //}
		  } if ("mapComparison".equals(cacheType)) {
		    if (overview instanceof LocalityOverview) {
			  taxon.setMap(new Map(taxon, (LocalityOverview) overview, connection, 1));
            }
		  } else {
			  // was: taxon.setMap(new Map(taxon, (LocalityOverview) overview, connection, 1));
			  // taxon.setMap(new Map(taxon, connection));
			  // This could perform quickly, without fetching the rows.  Just so we know if we need a map link.
			  //taxon.setHasMap(taxon, projectName, connection)              
		  }

          //A.log("execute() map:" + map + " cacheType:" + cacheType);
		  
		  if (map != null) taxon.setMap(map);

		  if (logTimes) A.log("execute() after setMap time:" +  AntwebUtil.millisSince(startTime));

/*
		  // This code will potentially record the cached page.
		  //A.log("execute() finishing taxon:" + taxon.getTaxonName() + " isGenCache:" + isGenCache + " isCachable:" + isCachable + " isGetCache:" + isGetCache);
		  if (!isGenCache && isCachable && !isGetCache) {
			// if &genCache=true then just return results to the calling code that will write to the system.  Do not cache or record.  Done in the AntwebCacheMgr.finish()
			  int busy = DBUtil.getNumBusyConnections(dataSource);
			  AntwebCacheMgr.finish(request, connection, busy, startTime, cacheType, overview, taxon.getTaxonName());
		  }
*/


            if (taxon == null) {
                message = "Taxon not found";
                if (!overview.getName().equals(Project.ALLANTWEBANTS)) {
                    message += " for overview:" + overview.getName();
                } else {
                    message += ".";
                }
                if (UploadAction.isInUploadProcess()) {
                    // An upload is currently in process.  Request that this process be re-attempted shortly.
                    message += "  A curator is currently in the process of an Upload.  Please try again shortly.";
                    s_log.info("execute() " + message);
                } else {
                    s_log.info("execute() " + message + "  No upload in process.");
                }
                request.setAttribute("message", message);
                LogMgr.appendLog("noExists.txt", new Date() + " - " + AntwebUtil.getRequestInfo(request));

                if (logTimes) A.log("execute() return no taxon found time:" +  AntwebUtil.millisSince(startTime));

                return mapping.findForward("message");
            }

            if ("oneView".equals(cacheType)) chosen = (String[]) session.getAttribute("chosen");
            if ("getCompare".equals(cacheType)) session.setAttribute("chosen", chosen);
            //A.log("execute() scope:" + mapping.getScope() + " showTaxon:" + taxon + " chosen:" + chosen + " cacheType:" + cacheType);
            if (chosen != null) {
                taxon.filterChildren(chosen);
            }

            // A.log("execute() scope:" + mapping.getScope() + " rank:" + taxon.getRank());
            // if ("request".equals(mapping.getScope())) {

            request.getSession().setAttribute("taxon", taxon);

            request.setAttribute("taxon", taxon);
            request.setAttribute("showTaxon", taxon);

            //session.setAttribute("taxon", taxon);

            if ("getComparison".equals(cacheType) || "mapComparison".equals(cacheType)) {
                session.setAttribute("showTaxon", taxon);
                session.setAttribute("mykids", taxon.getChildren());
            }

            //A.log("execute() taxon:" + taxon + " taxonImages:" + taxon.getImages() );
            //OpenGraphMgr.setOGTitle(Taxon.getPrettyTaxonName(taxon.getTaxonName()));
            request.setAttribute("ogTitle", Taxon.getPrettyTaxonName(taxon.getTaxonName()));
            if (taxon.getImages() != null) {
                SpecimenImage headShot = taxon.getImages().get("h");
                if (headShot != null) {
                    String ogImage = headShot.getMedres();
                    //A.log("execute() ogImg:" + ogImg);
                    //OpenGraphMgr.setOGImage(ogImg);
                    request.setAttribute("ogImage", ogImage);
                } else s_log.debug("No Open Graph Image set. No headshot");

                if (AntwebDebug.isDebugTaxon(taxon.getTaxonName())) s_log.debug("has d image:" + taxon.getImages().get("d"));

            }

            String execTime = HttpUtil.finish(request, startTime);
            taxon.setExecTime(execTime);

            // Set a transactional control token to prevent double posting
            // This was removed because Tokens in forms where messing with luke's new functionality.
            // saveToken(request);

            if (taxon instanceof Homonym) {
                if (logTimes) A.log("execute() return homonym time:" +  AntwebUtil.millisSince(startTime));

                return mapping.findForward("homonym");
            }

            if (rank.equals("specimen")) {
                if (logTimes) A.log("execute() return specimen time:" +  AntwebUtil.millisSince(startTime));

                return mapping.findForward("specimen");
            } else if (request.getParameter("shot") != null) {
                if (logTimes) A.log("execute() return oneView time:" +  AntwebUtil.millisSince(startTime));

                return mapping.findForward("oneView");
            } else {
                if (logTimes) A.log("execute() return success time:" +  AntwebUtil.millisSince(startTime));

                return mapping.findForward("success");
            }

        } catch (MissingResourceException e) {
			// This was around the new Map() command above, but we seemed to be not closing the db connection.
			message = "e:" + e + " MissingResource overview:"+ overview.getName();
        } catch (SQLException e) {
            message = "Exception caught on request.";
            if (!HttpUtil.isBot(request) || AntwebProps.isDevMode()) {
                s_log.error("execute() e:" + e + " requestInfo:" + HttpUtil.getShortRequestInfo(request));
            }
        } catch (TaxonNotFoundException e) {
            message = e.getMessage();
            request.setAttribute("message", "Taxon not found for " + message);
            String logMessage = message + " " + HttpUtil.getTarget(request) + " referrer:" + HttpUtil.getReferrerUrl(request);
            A.log("execute() message:" + logMessage);
            LogMgr.appendWebLog("taxonNotFound.txt", logMessage, true);
            return mapping.findForward("message");
        } catch (Exception e) {
            message = "e:" + e;
        } finally {
//		  ResourceTracker.remove(dbMethodName + " " + target);

            if ("mapComparison".equals(cacheType)) --s_mapComparisonCount;
            if ("getComparison".equals(cacheType)) --s_getComparisonCount;
        
            QueryProfiler.profile(cacheType, startTime);	        
            if (!DBUtil.close(connection, this, dbMethodName)) {
               s_log.error("execute() DBUtil.close error on dbMethodName:" + dbMethodName);
               AntwebUtil.logStackTrace();
            }

            int alertMins = 8;
            long minsSince = AntwebUtil.minsSince(startTime);
            if (minsSince >= alertMins) {
                s_log.error("execute() request over alertMins:" + minsSince + " request:" + HttpUtil.getRequestInfo(request));
            }
            //s_log.info("execute() closing uniqueNumber:" + uniqueNumber);
        }

        // Error handling.
        s_log.error("execute() " + message + " " + AntwebUtil.getRequestInfo(request));
        request.setAttribute("message", message);
        return mapping.findForward("message");
    }

    protected ActionForward taxonNameRedirect(BrowseForm browseForm, ActionMapping mapping
      , HttpServletRequest request, HttpServletResponse response) {

	  String taxonName = browseForm.getTaxonName();
	  
      //A.log("taxonNameRedirect taxonName:" + taxonName);	  
	  if (taxonName != null && Formatter.containsUppercase(taxonName)) {
        // It must be a pretty taxon name. Convert.
		String commonName = Formatter.initCap(taxonName);
	    String fromPrettyName = Taxon.getTaxonNameFromPrettyName(commonName);
	    //A.log("taxonNameRedirect() fromPrettyName:" + fromPrettyName + " taxonName:" + taxonName);
	    if (fromPrettyName != null)
  	      taxonName = fromPrettyName;
	  }
	  
	  int antcatId = browseForm.getAntcatId();
	  Connection connection = null;
      String dbMethodName = DBUtil.getDbMethodName("BrowseAction.taxonNameRedirect()");
	  try {
		DataSource dataSource = getDataSource(request, "conPool");
		connection = DBUtil.getConnection(dataSource, dbMethodName);

		Taxon fetchTaxon = null;
		
		String authorDate = browseForm.getAuthorDate();
		// fetch with taxon name
		if (taxonName != null && !"".equals(taxonName)) {
		  if (!"homonym".equals(browseForm.getStatus()) && authorDate == null)
              //A.log("4");

            //String commonName = Formatter.initCap(taxonName);
              //A.log("6 commonName:" + commonName);
              
			fetchTaxon = new TaxonDb(connection).getTaxon(taxonName);
		  if (fetchTaxon == null) {
              //A.log("5");
			if (authorDate == null) 
			  fetchTaxon = new HomonymDb(connection).getHomonym(taxonName);
			else 
			  fetchTaxon = new HomonymDb(connection).getHomonym(taxonName, authorDate);
		  } 
		  if (fetchTaxon == null) {
            String commonTaxonName = CommonNames.get(taxonName);
            if (commonTaxonName != null) {
              //A.log("6");
              fetchTaxon = new TaxonDb(connection).getTaxon(commonTaxonName);
            }		  
		  }
		}

		String fetchStr = "null";
		if (fetchTaxon != null) fetchStr = fetchTaxon.getTaxonName();
		s_log.debug("taxonNameRedirect() antcatId:" + antcatId + " taxonName:" + taxonName + " fetchStr:" + fetchStr + " resetProject:" + browseForm.getResetProject());

		// fetch with Antcat ID
		if (fetchTaxon == null && antcatId != 0) {

		  TaxonDb taxonDb = new TaxonDb(connection);
          taxonName = taxonDb.getTaxonNameFromAntcatId("taxon", antcatId);

		  if (taxonName == null) {
			request.setAttribute("message", "Taxon name not found for antcatId:" + antcatId);
			return mapping.findForward("message");
		  }
		  fetchTaxon = new TaxonDb(connection).getTaxon(taxonName);

		  if (fetchTaxon == null) {
			fetchTaxon = new HomonymDb(connection).getHomonym(browseForm.getAntcatId());
			taxonName = fetchTaxon.getTaxonName();
		  }
		}
		
		String possibleSpecimen = taxonName.toLowerCase();
		if (fetchTaxon != null) {
		  String target = HttpUtil.getTarget(request);
		  String targetDo = "description.do";
		  if (target.contains("browse.do")) targetDo = "browse.do";
			else if (target.contains("images.do")) targetDo = "images.do";
		  
		  String url = fetchTaxon.getUrl(targetDo);
          if (browseForm.getResetProject()) url += "&resetProject=true";
		  s_log.debug("taxonNameRedirect() resetProject:" + browseForm.getResetProject() + " taxon:" + fetchTaxon + " url:" + url);
		  HttpUtil.sendRedirect(url, request, response);  
		  return null;
		} else if (possibleSpecimen.contains("casent") || possibleSpecimen.contains("blf") || possibleSpecimen.contains("jtl") 
		    || possibleSpecimen.contains("lacm") || possibleSpecimen.contains("sam-") || possibleSpecimen.contains("fmnhi") 
		    || possibleSpecimen.contains("inb00") || possibleSpecimen.contains("kbve")) {  
          s_log.debug("execute() taxonName" + taxonName);
          String url = AntwebProps.getDomainApp() + "/specimen.do?code=" + taxonName;
		  HttpUtil.sendRedirect(url, request, response);  
          return null;
        } else {
		  request.setAttribute("message", taxonName + " does not exist in the database");
		  return mapping.findForward("message");
		}
	  } catch (IOException | SQLException e) {
		s_log.warn("execute() fetchTaxon e:" + e);
	  } finally {
		DBUtil.close(connection, this, dbMethodName);
	  }
	  return mapping.findForward("error");
    }

    private String inferredRank(String queryString) {

        //A.log("inferredRank() queryString:" + queryString);
        // remove &orderBy=species which would throw off inference.
        String testString = HttpUtil.getTargetMinusParam(queryString, "orderBy");
        //A.log("inferredRank() 1 testString:" + testString);

        testString = HttpUtil.getTargetMinusParam(testString, "orderby");
        //A.log("inferredRank() 2 testString:" + testString);
        //A.log("inferredRank() queryString:" + queryString + " testString:" + testString);

        if(testString.contains("subspecies"))return Rank.SUBSPECIES;
        if(testString.contains("species"))return Rank.SPECIES;
        if(testString.contains("genus") &&!queryString.contains("subgenus"))return Rank.GENUS;
        if(testString.contains("subgenus"))return Rank.SUBGENUS;
        if(testString.contains("subfamily"))return Rank.SUBFAMILY;
        if(testString.contains("family"))return Rank.FAMILY;
        return null;
    }


}
