package org.calacademy.antweb;

import java.sql.*;
import java.io.*; 
import java.util.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class OverviewAction extends DescriptionAction {

    private static final Log s_log = LogFactory.getLog(OverviewAction.class);
    
    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

      if (!GeolocaleMgr.isInitialized()) {
		  request.setAttribute("message", "Server Initializing...");
		  return mapping.findForward("message");
      }

      HttpUtil.setUtf8(request, response);       

      Login accessLogin = LoginMgr.getAccessLogin(request);
      //Group accessGroup = accessLogin.getGroup();
      
      OverviewForm overviewForm = (OverviewForm) form;
	  String action = overviewForm.getAction();

      if (HttpUtil.tooBusyForBots(request)) { return HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }

      Connection connection = null;
      String dbMethodName = DBUtil.getDbMethodName("OverviewAction.execute()");
      try {
        DataSource dataSource = null;

        if (!"recalc".equals(action)) dataSource = getDataSource(request, "conPool");
          else dataSource = getDataSource(request, "longConPool");

        connection = DBUtil.getConnection(dataSource, dbMethodName, HttpUtil.getTarget(request));

        // Recalculate changes data: curators only, and only by POST (the Recalculate button,
        // so the form token is checked); a GET with action=recalc just shows the page.
        boolean isRecalc = "recalc".equals(action) && LoginMgr.isCurator(request) && HttpUtil.isPost(request);

        AntwebMgr.populate(connection);

        // Like to check AntewbMgr.isPopulated()?  Otherwise sometimes... NPE. When?

        String uri = HttpUtil.getRequestURI(request);

        String name = overviewForm.getName();    
        int id = overviewForm.getId();
           
        s_log.debug("execute() name:" + name + " uri:" + uri);

        if (uri.contains("/projects.do")) {
            ArrayList<Project> projects = ProjectMgr.getAntProjects();
            request.setAttribute("overviews", projects);
            return mapping.findForward("projects");   
        }
        if (uri.contains("/project.do")) {
          if (name != null && !"".equals(name)) {
            String projectName = ProjectMgr.getProjectName(name);
            ProjectDb projectDb = new ProjectDb(connection);

            Project project = projectDb.getProject(projectName);
            if (project == null) {
              request.setAttribute("message", "Project:" + Html.esc(name) + " not found.");
              return mapping.findForward("message");
            }

            if (isRecalc) {
                // The taxon child counts first (the region, museum and bioregion recounts include
                // their crawl too), then the project's own counts.
                new ProjTaxonCountDb(connection).childrenCountCrawl(projectName);
                projectDb.updateCounts(projectName);
                new PlaceImageCountDb(connection).updatePlace(PlaceImageCountDb.PROJECT, projectName);
                ProjectMgr.populate(connection, true);
                //A.log("Project:" + projectName + " action:" + action);
            }

            if (HttpUtil.isPost(request)) {
              boolean success = saveDescriptionEdit(overviewForm, project, accessLogin
                , request, connection);   
              if (!success) return mapping.findForward("message");
            }
            prepareDescriptionEdit(project, accessLogin, request, connection);
  
            OverviewMgr.setOverview(request, project);  
            request.setAttribute("overview", project); setPlaceImages(request, project, connection);
            //A.log("execute() 2 statusSet:" + (String) request.getSession().getAttribute("statusSet"));
                
            return mapping.findForward("project");
          } else {
              request.setAttribute("message", "Project not found.");
              return mapping.findForward("message");
          }          
        }

        if (uri.contains("/museums.do")) {
              ArrayList<Museum> museums = MuseumMgr.getMuseums();
              request.setAttribute("overviews", museums);
              return mapping.findForward("museums");  
        }
        if (uri.contains("/museum.do")) {
           String museumCode = overviewForm.getCode();        
           if (museumCode == null) {
             museumCode = overviewForm.getName();        
           }
 
           if (museumCode != null && !"".equals(museumCode)) {
              // Recalculate on a museum page is for administrators (the button is shown only to them;
              // curators recount the museums of their own upload from the Curate page).
              if (isRecalc && LoginMgr.isAdmin(request)) {		
                new TaxonDb(connection).setSubfamilyChartColor();
              	
	  	   	    MuseumDb museumDb = new MuseumDb(connection);
  	  		    museumDb.populate(museumCode);
                new PlaceImageCountDb(connection).updatePlace(PlaceImageCountDb.MUSEUM, museumCode);
   
                MuseumMgr.populate(connection, true);
              }						

              Museum museum = MuseumMgr.getMuseum(museumCode);
              // *** museum.setName(museumCode);
              if (museum == null) {
                request.setAttribute("message", "Museum not found");          
                return mapping.findForward("message");
              }

              if (HttpUtil.isPost(request)) {
                boolean success = saveDescriptionEdit(overviewForm,  museum, accessLogin
                 , request, connection);   
                if (!success) return mapping.findForward("message");
              }
              prepareDescriptionEdit(museum, accessLogin, request, connection);
  
              OverviewMgr.setOverview(request, museum); 
              museum.setMap(new ObjectMapDb(connection).getMuseumMap(museum.getCode()));

              request.setAttribute("overview", museum); setPlaceImages(request, museum, connection);
              return mapping.findForward("museum");  
           } else {
              request.setAttribute("message", "Museum not found.");
              return mapping.findForward("message");       
           }
        }          

        if (uri.contains("/bioregions.do")) {
              ArrayList<Bioregion> bioregions = BioregionMgr.getBioregions();
              request.setAttribute("overviews", bioregions);
              return mapping.findForward("bioregions"); 
        }
        if (uri.contains("/bioregion.do")) {
           if (name != null && !"".equals(name)) {

               if (isRecalc) {
                   new TaxonDb(connection).setSubfamilyChartColor();

                   BioregionDb bioregionDb = new BioregionDb(connection);
                   bioregionDb.populate(name);
                   new PlaceImageCountDb(connection).updatePlace(PlaceImageCountDb.BIOREGION, name);

                   BioregionMgr.populate(connection, true);
               }


               Bioregion bioregion = BioregionMgr.getBioregion(name);
              if (bioregion == null) {
                request.setAttribute("message", "Bioregion not found");          
                return mapping.findForward("message");
              }

              if (HttpUtil.isPost(request)) {
                boolean success = saveDescriptionEdit(overviewForm,  bioregion, accessLogin
                 , request, connection);   
                if (!success) return mapping.findForward("message");
              }
              prepareDescriptionEdit(bioregion, accessLogin, request, connection);

              OverviewMgr.setOverview(request, bioregion);
              request.setAttribute("overview", bioregion); setPlaceImages(request, bioregion, connection);
              return mapping.findForward("bioregion");  
           } else {
              request.setAttribute("message", "Bioregion not found.");
              return mapping.findForward("message");        
           }
        }

        if (uri.contains("/geolocale.do")) {            
          Geolocale geolocale = null;
          if (id > 0) {
            geolocale = GeolocaleMgr.getGeolocale(id);
            if (geolocale == null) {
                request.setAttribute("message", "Geolocale:" + id + " not found");          
                return mapping.findForward("message");            
            }
          }
          
          // These are convenient for bigMap.do
          String regionName = overviewForm.getRegionName();
          if (regionName != null) {
            geolocale = GeolocaleMgr.getGeolocale(regionName);
            if (geolocale == null) {
                request.setAttribute("message", "region:" + Html.esc(regionName) + " not found");          
                return mapping.findForward("message");            
            }
          }
          String subregionName = overviewForm.getSubregionName();
          if (subregionName != null) {
            geolocale = GeolocaleMgr.getGeolocale(subregionName);
            if (geolocale == null) {
                request.setAttribute("message", "Subregion:" + Html.esc(subregionName) + " not found");          
                return mapping.findForward("message");            
            }
          }
          String countryName = overviewForm.getCountryName();
          if (countryName != null) {
            geolocale = GeolocaleMgr.getGeolocale(countryName);
            if (geolocale == null) {
                request.setAttribute("message", "Country:" + Html.esc(countryName) + " not found");          
                return mapping.findForward("message");            
            }
          }
          String adm1Name = overviewForm.getAdm1Name();
          if (adm1Name != null) {
              if (countryName != null) {
                  geolocale = GeolocaleMgr.getAdm1(adm1Name, countryName);
                  if (geolocale == null) {
                      request.setAttribute("message", "Adm1:" + Html.esc(adm1Name) + " not found");
                      return mapping.findForward("message");
                  }
              } else {
                  geolocale = GeolocaleMgr.getAdm1IfUnique(adm1Name);
                  if (geolocale == null) {
                      request.setAttribute("message", "Unique Adm1:" + Html.esc(adm1Name) + " not found");
                      return mapping.findForward("message");
                  }
              }
          }

          if (geolocale == null) {
              s_log.error("execute() geolocale not found in request. " + HttpUtil.getRequestInfo(request));

              request.setAttribute("message", "Geolocale not found");
              return mapping.findForward("message");
          }

		  String url = geolocale.getThisPageTarget();

		  //HttpUtil.getUrl(url);  // Needed? Guess not.
          HttpUtil.sendRedirect(url, request, response);
		  return null;          
        }
        
        if (uri.contains("/regions.do")) { 
              ArrayList<Geolocale> geolocales = GeolocaleMgr.getRegions();
              request.setAttribute("overviews", geolocales);
              return mapping.findForward("regions");
        }        
        if (uri.contains("/region.do")) {            
          Geolocale geolocale = null;
          String key = null;
          if (id > 0) {
            geolocale = GeolocaleMgr.getGeolocale(id);
            key = "id:" + id; 
          } else if (name != null) {            
            geolocale = GeolocaleMgr.getGeolocale(name, "region");
            key = "name:" + name;
          }
          if ((id > 0 || name != null) && geolocale == null) {
            request.setAttribute("message", Html.esc(key) + " not found.");
            return mapping.findForward("message");
          }            
            
          if (geolocale != null) {    
              if (HttpUtil.isPost(request)) {
                boolean success = saveDescriptionEdit(overviewForm,  geolocale, accessLogin
                 , request, connection);   
                if (!success) return mapping.findForward("message");
              }                
              prepareDescriptionEdit(geolocale, accessLogin, request, connection);
              OverviewMgr.setOverview(request, geolocale);
              request.setAttribute("overview", geolocale); setPlaceImages(request, geolocale, connection);
              return mapping.findForward("region");
           } else {
              // No id or name (bare region.do): the list of regions, not "Region not found." (2026-10-22)
              request.setAttribute("overviews", GeolocaleMgr.getRegions());
              return mapping.findForward("regions");
           }
        }
        
        if (uri.contains("/subregions.do")) {  
              ArrayList<Geolocale> geolocales = GeolocaleMgr.getSubregions();
              request.setAttribute("overviews", geolocales);
              return mapping.findForward("subregions");
        }        
        if (uri.contains("/subregion.do")) {
           Geolocale geolocale = null;
           String key = null;
           if (id > 0) {
             geolocale = GeolocaleMgr.getGeolocale(id);
             key = "id:" + id; 
           } else if (name != null) {            
             geolocale = GeolocaleMgr.getGeolocale(name, "subregion");
             key = "name:" + name;
           }
           if ((id > 0 || name != null) && geolocale == null) {
             request.setAttribute("message", Html.esc(key) + " not found.");
             return mapping.findForward("message");
           }
           if (geolocale != null) {
              if (HttpUtil.isPost(request)) {
                boolean success = saveDescriptionEdit(overviewForm,  geolocale, accessLogin
                 , request, connection);   
                if (!success) return mapping.findForward("message");
              }
              prepareDescriptionEdit(geolocale, accessLogin, request, connection);
              OverviewMgr.setOverview(request, geolocale);
              request.setAttribute("overview", geolocale); setPlaceImages(request, geolocale, connection);
              return mapping.findForward("subregion");
           } else {
              // No id or name (bare subregion.do): the list of subregions, not "Subregion not found." (2026-10-22)
              request.setAttribute("overviews", GeolocaleMgr.getSubregions());
              return mapping.findForward("subregions");
           }
        }   

        if (uri.contains("/countries.do")) {          
            ArrayList<Geolocale> geolocales = GeolocaleMgr.getCountries();
            request.setAttribute("overviews", geolocales);
            return mapping.findForward("countries");
        }  

        if (uri.contains("/country.do") || uri.contains("/island.do")) {
          Geolocale geolocale = null;
          String key = null;
          if (id > 0) {
            geolocale = GeolocaleMgr.getGeolocale(id);
            key = "id:" + id; 
          } else if (name != null) {            
            geolocale = GeolocaleMgr.getGeolocale(name, "country");
            key = "name:" + name;
          }
          if ((id > 0 || name != null) && geolocale == null) {
            request.setAttribute("message", Html.esc(key) + " not found.");
            return mapping.findForward("message");
          }

		  if (isRecalc) {
			GeolocaleDb geolocaleDb = new GeolocaleDb(connection);
			geolocaleDb.updateCounts(geolocale.getId());
			new PlaceImageCountDb(connection).updatePlace(PlaceImageCountDb.GEOLOCALE, "" + geolocale.getId());
			GeolocaleMgr.populate(connection, true, false);
			s_log.debug("execute() AntwebMgr.populate()");
		  }

          if (geolocale != null) {
            if (HttpUtil.isPost(request)) {
              boolean success = saveDescriptionEdit(overviewForm, geolocale, accessLogin
                , request, connection);   
              if (!success) return mapping.findForward("message");
            }
            prepareDescriptionEdit(geolocale, accessLogin, request, connection);

            geolocale = GeolocaleMgr.getGeolocale(geolocale.getId());
            OverviewMgr.setOverview(request, geolocale);
			geolocale.setMap(new ObjectMapDb(connection).getGeolocaleMap(geolocale.getId()));
            request.setAttribute("overview", geolocale); setPlaceImages(request, geolocale, connection); 
            return mapping.findForward("country");
          } else {
            request.setAttribute("message", "Country not found.");
            return mapping.findForward("message");
          }              
        }

        /*
          Specimens: 5,748
          Images: 22,008
          Imaged Specimens: 5,312

          Subfamilies: 19
          Genera: 564
          Species/Subspecies: 13,808
       */
        if (uri.contains("/adm1s.do")) {          
          ArrayList<Geolocale> geolocales = GeolocaleMgr.getAdm1sWithSpecimen();
          request.setAttribute("overviews", geolocales);
          return mapping.findForward("adm1s");  
        }              

        if (uri.contains("/adm1.do")) {
        
          if (overviewForm.getCountry() != null && overviewForm.getCountryName() != null) {
            String message = "Shouldn't specify country and countryName.";
            //s_log.warn("execute() " + message + " requestInfo:" + HttpUtil.getRequestInfo(request));
            request.setAttribute("message", message);
            return mapping.findForward("message");
          }
        
          Geolocale geolocale = null;   
          String key = null;            
          if (id > 0) {
            geolocale = GeolocaleMgr.getGeolocale(id);
            key = "id:" + id; 
          } else if (name != null) {            
			String country = overviewForm.getCountry();  
			if (country == null) {
			   //s_log.error("execute() country is null for " + HttpUtil.getRequestInfo(request));
               request.setAttribute("message", "Invalid request. Must include a country to uniquely specimen an Adm1 in adm1.do.");
               return mapping.findForward("message");
 			}
 			
            //A.log("execute() country:" + country + " id:" + id);
            geolocale = GeolocaleMgr.getAnyAdm1(name, country);  // getGeolocale(name, "adm1");
            key = "name:" + name;

            String geoString = " geolocale:" + geolocale;
            if (geolocale != null) geoString += " id:" + geolocale.getId() + " name:" + geolocale.getName() + " parent:" + geolocale.getParent() + " georank:" + geolocale.getGeorank();
            //A.log("OverviewAction.execute() name:" + name + " country:" + country + geoString);

	        if (geolocale == null && GeolocaleMgr.isIsland(name)) {
              geolocale = GeolocaleMgr.getIsland(name);
              //A.log("OverviewAction.execute() geolocale:" + geolocale + " id:" + geolocale.getId() + " georank:" + geolocale.getGeorank());
              OverviewMgr.setOverview(request, geolocale);   
              geolocale.setMap(new ObjectMapDb(connection).getGeolocaleMap(geolocale.getId()));
              request.setAttribute("overview", geolocale); setPlaceImages(request, geolocale, connection); 
              return mapping.findForward("island");              
            }
          }
          
          //A.log("execute() Adm1.do geolocale:" + geolocale + " geoId:" + geolocale.getId() + " id:" + id + " name:" + name);          
          if ((id > 0 || name != null) && (geolocale == null || geolocale.getId() == 0)) {
            //A.log("execute() Adm1.do not found");
            request.setAttribute("message", Html.esc(key) + " not found.");
            return mapping.findForward("message");
          }
          
		  if (isRecalc) {
			GeolocaleDb geolocaleDb = new GeolocaleDb(connection);
			geolocaleDb.updateCounts(geolocale.getId());
			new PlaceImageCountDb(connection).updatePlace(PlaceImageCountDb.GEOLOCALE, "" + geolocale.getId());
			GeolocaleMgr.populate(connection, true, false);
			s_log.debug("execute() geolocale:" + geolocale.getName() + " recalculated.");
		  }
		            
          if (geolocale != null) {
            if (HttpUtil.isPost(request)) {
              boolean success = saveDescriptionEdit(overviewForm, geolocale, accessLogin
                , request, connection);   
              if (!success) return mapping.findForward("message");
            }
            prepareDescriptionEdit(geolocale, accessLogin, request, connection);

            geolocale = GeolocaleMgr.getGeolocale(geolocale.getId());
            OverviewMgr.setOverview(request, geolocale);            
			geolocale.setMap(new ObjectMapDb(connection).getGeolocaleMap(geolocale.getId()));
            request.setAttribute("overview", geolocale); setPlaceImages(request, geolocale, connection); 
            return mapping.findForward("adm1");  
          } else {
            request.setAttribute("message", "Adm1 not found.");
            return mapping.findForward("message");
          }          
        }   
      } catch (SQLException e) {
          s_log.error("execute() e:" + e);
      } finally {
          DBUtil.close(connection, this, dbMethodName);
      }

      // Reached after a database error (2026-10-26, finding F17: it returned null, a blank page with HTTP 200).
        request.setAttribute("message", "The page could not be built (database error). Please try again later.");
        return mapping.findForward("message");
    }

    /**
     * The image numbers of the place (PlaceImageCountDb) for overview-body.jsp: request attribute
     * "placeImages", null when not computed (yet) or when the table is missing.
     */
    static void setPlaceImages(HttpServletRequest request, Overview overview, Connection connection) {
        String type = null;
        String key = null;
        if (overview instanceof Geolocale) { type = PlaceImageCountDb.GEOLOCALE; key = "" + ((Geolocale) overview).getId(); }
        else if (overview instanceof Project) { type = PlaceImageCountDb.PROJECT; key = overview.getName(); }
        else if (overview instanceof Museum) { type = PlaceImageCountDb.MUSEUM; key = ((Museum) overview).getCode(); }
        else if (overview instanceof Bioregion) { type = PlaceImageCountDb.BIOREGION; key = overview.getName(); }
        if (type == null || key == null) return;
        try {
            request.setAttribute("placeImages", new PlaceImageCountDb(connection).read(type, key));
        } catch (SQLException e) {
            s_log.warn("setPlaceImages() " + type + " " + key + " e:" + e);
        }
    }
}
