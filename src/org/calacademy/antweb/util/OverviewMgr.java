package org.calacademy.antweb.util;

//import java.util.*;
import java.sql.*;

import org.apache.struts.action.*;
import javax.servlet.http.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class OverviewMgr {

    private static final Log s_log = LogFactory.getLog(OverviewMgr.class);

    public static Overview getOverview(HttpServletRequest request) {
      HttpSession session = request.getSession();
      Overview overview = (Overview) session.getAttribute("overview");
      if (overview == null) {
        overview = new Project(Project.ALLANTWEBANTS);       
        // Should we set it in the session?
      }
      return overview;
    }
    public static void setOverview(HttpServletRequest request, Overviewable overview) {      
      // JSP pages will get overview from the request this way.
      request.setAttribute("overview", overview);       
      request.getSession().setAttribute("overview", overview);
    }
/*
    // Do not default to AllAntwebAnts!
    public static Overview getAndSetOverviewSafe(HttpServletRequest request) {
        Overview lastOverview = (Overview) request.getSession().getAttribute("overview");
        request.getSession().setAttribute("lastOverview", lastOverview);

        Overview overview = null;
        try {
            overview = OverviewMgr.findOverview(request);
        } catch (AntwebException e) {
            //s_log.warn("getAndSetOverview() e:" + e);
            return null;
        }

        // if (overview == null) overview = new Project(Project.ALLANTWEBANTS);

        OverviewMgr.setOverview(request, overview);
        return overview;
    }
*/
    // This can and should be called once per request. Fetch and set. Also set lastOverview.
    public static Overview getAndSetOverview(HttpServletRequest request) {
        Overview lastOverview = (Overview) request.getSession().getAttribute("overview");
        request.getSession().setAttribute("lastOverview", lastOverview);

        Overview overview = null;        
        try {
          overview = OverviewMgr.findOverview(request);
        } catch (AntwebException e) {
          //s_log.warn("getAndSetOverview() e:" + e);
          return null;
        }

        if (overview == null) overview = new Project(Project.ALLANTWEBANTS);
        OverviewMgr.setOverview(request, overview);   
        return overview;
    }
    
    public static Overview findOverview(HttpServletRequest request) throws AntwebException {

        //A.log("findOverview() sort:" + request.getParameter("sortBy") + " " + request.getParameter("sortOrder"));

        boolean debug = true && AntwebProps.isDevMode();
        String hasParams = null;
        Overview overview = null;
        
		String projectName = request.getParameter("project");
		if (projectName == null) projectName = request.getParameter("projectName");
		//A.log("getOverview() 1 projectName:" + projectName);
		if (projectName != null) {
          hasParams = "project overview";
		  overview = ProjectMgr.getProject(projectName, false); // Do not default to Allantwebants
		  // A.log("getOverview() projectName:" + projectName + " overview:" + overview);
		} else {        
			String museumCode = request.getParameter("museumCode");
			if (museumCode != null) {
              hasParams = "museum overview";
              overview = MuseumMgr.getMuseum(museumCode);
			  //s_log.warn("getOverview() museumCode:" + museumCode + " overview:" + overview);
			} else {
				String geolocaleIdStr = request.getParameter("geolocaleId");
				if (geolocaleIdStr != null) {
                  hasParams = "geolocale overview";
				  int geolocaleId = (Integer.valueOf(geolocaleIdStr)).intValue();
				  overview = GeolocaleMgr.getGeolocale(geolocaleId);
				} else {
					String regionName = request.getParameter("regionName");
					if (regionName != null) {
                      hasParams = "region overview";
					  overview = GeolocaleMgr.getRegion(regionName);
					} else {
						String subregionName = request.getParameter("subregionName");
						if (subregionName != null) {
                          hasParams = "subregion overview";
						  overview = GeolocaleMgr.getSubregion(subregionName);
						} else {
							String countryName = request.getParameter("countryName");
							String adm1Name = request.getParameter("adm1Name");
							if (countryName != null && adm1Name == null) {
                              hasParams = "adm1 overview";
							  overview = GeolocaleMgr.getCountry(countryName);
							} else {
								if (HttpUtil.getTarget(request).contains("adm1.do")) {
                                  hasParams = "adm1 overview";
								  adm1Name = request.getParameter("name");
								}
								if (adm1Name != null) {
								  if (GeolocaleMgr.isIsland(adm1Name)) {
                                    hasParams = "island overview";
								    overview = GeolocaleMgr.getIsland(adm1Name);
								  } else {
								    if (countryName == null) {
                                      hasParams = "country overview";
 								      countryName = request.getParameter("country");
                                    }
                                    overview = GeolocaleMgr.getAdm1(adm1Name, countryName);
                                        if (overview == null) {
                                            // Apparently adm1Name not found. Return country instead. Otherwise would be very poor performance.
                                            // The overview would end up being allantwebants. Why? To be resolved. Other misses could trigger?
                                            overview = GeolocaleMgr.getCountry(countryName);
                                            s_log.warn("findOverview() Serious performance issue? See TaxaPage.fetchChildren() overview:" + overview + " countryName:" + countryName + " adm1Name:" + adm1Name);
                                        }
								  }
                                  //A.log("getOverview() adm1Name:" + adm1Name + " country:" + countryName + " overview:" + overview); // + " country:" + ((Adm1)overview).getParent());								  
								} else {      
									String bioregionName = request.getParameter("bioregionName");
									if (bioregionName != null) {
                                      hasParams = "bioregion overview";
									  overview = BioregionMgr.getBioregion(bioregionName);
									  if (overview == null) s_log.warn("getOverview() bioregionName:" + bioregionName + " bioregion:" + overview);
									} else {
                                        hasParams = "session overview";	
										overview = (Overview) request.getSession().getAttribute("overview");	
										// A.log("getOverview() session overview:" + overview);	
										if (overview == null) overview = ProjectMgr.getProject(Project.ALLANTWEBANTS);							
									}
								}
							}
						}
					}
				}
			}
		}
		
		if (hasParams != null && overview == null) throw new AntwebException("Not found:" + hasParams);
        
        if (overview == null) return null;     

        if (debug) s_log.warn("findOverview() overview:" + overview + " class:" + overview.getClass() + " queryString:" + request.getQueryString());

        return overview;    
    }
    
    public static ActionForward returnMessage(HttpServletRequest request, ActionMapping mapping) {
      String message = "Overview not found for " + HttpUtil.getRequestReferer(request) + " " + DateUtil.getFormatDateTimeStr();
      message += ". <br><br>If you think this request should have been fulfilled, please email this error message to " + AntwebUtil.getAdminEmail() + ".";
      message += " Please indicate where you found the link, if not evident in the message. Thank you.";
      request.setAttribute("message", message);
      return mapping.findForward("message");        
    }    
        
    // This only gets called in cases of species list. Only Project and Geolocale are managed by the Species List Tool.
    public static Overview getOverview(String name) {
        Overview overview = null;
        boolean isProject = Project.isProjectName(name);
        if (isProject) {
          Project project = ProjectMgr.getProject(name);
          if (project != null) overview = (Overview) project;
        } else {
          Geolocale geolocale = (Geolocale) GeolocaleMgr.getGeolocale(name);
          if (geolocale != null) overview = (Overview) geolocale;
        }
        return overview;
    }

	public static boolean isNewOverview(Overview overview, HttpSession session) {
	
        // If we are visiting a world ants page, and the last one wasn't... we switch to valid.
        // fossilants, allantwebants also switch.
        boolean retVal = false;
        if (overview == null) { 
          retVal = true;     
        } else {
          retVal = !overview.equals(getLastOverview(session));                      
        }
        //A.log("isNewOverview() retVal:" + retVal + " overview:" + overview + " last:" + getLastOverview(session));
        return retVal;
	}

	public static Overview getLastOverview(HttpSession session) {
	    return (Overview) session.getAttribute("lastOverview");
	}    
}

