package org.calacademy.antweb.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;


import org.apache.struts.action.*;

import javax.servlet.http.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.Formatter;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class ProjectMgr {

    private static final Log s_log = LogFactory.getLog(ProjectMgr.class);
    
    private static ArrayList<Project> s_globalProjects = null;
    private static ArrayList<Project> s_subglobalProjects = null;
    private static HashMap<String, Project> s_allProjectsHash = null;

    public static void populate(Connection connection) {
      populate(connection, false);
    }

    public static void populate(Connection connection, boolean forceReload) {
      if (!forceReload && (s_globalProjects != null)) return;

      ProjectDb projectDb = new ProjectDb(connection);
      s_globalProjects = projectDb.getProjects("GLOBAL");
      s_subglobalProjects = projectDb.getSubProjects();
      s_allProjectsHash = projectDb.getAllProjects();
    }

    public static ArrayList<Project> getAntProjects() {    
      ArrayList<Project> projects = new ArrayList<>();
      for (String key : s_allProjectsHash.keySet()) {
        Project project = s_allProjectsHash.get(key);
        if ("PROJECT".equals(project.getScope())) {
            projects.add(project);
        }
      }
      return projects;
    } 

    public static ArrayList<Project> getAllProjects() {
      ArrayList<Project> projects = new ArrayList<>();
      for (String key : s_allProjectsHash.keySet()) {
        projects.add(s_allProjectsHash.get(key));
      }
      return projects;
    }

    public static ArrayList<Project> getGlobalProjects() {
      return s_globalProjects;
    }    
    public static ArrayList<Project> getSubglobalProjects() {
      return s_subglobalProjects;
    }    

    public static HashMap<String, Project> getAllProjectsHash() {
      if (s_allProjectsHash == null) AntwebMgr.isPopulated();
      return s_allProjectsHash;
    }
    
    public static HashMap<String, Project> getLiveProjectsHash() {
      return getLiveProjectsHash(true);
    }
    public static HashMap<String, Project> getLiveProjectsHash(boolean isLive) {
      HashMap<String, Project> projects = new HashMap<>();
      for (String key : s_allProjectsHash.keySet()) {
        Project project = s_allProjectsHash.get(key);
        if (isLive == project.getIsLive()) {
          projects.put(key, project);
        }
      }
      return projects;
    }

    public static ArrayList<Project> getLiveProjects() {
    
      ArrayList<Project> projects = new ArrayList<>();
      for (String key : s_allProjectsHash.keySet()) {
        Project project = s_allProjectsHash.get(key);
        if (true == project.getIsLive()) {
          projects.add(project);
        }
      }
      return projects;
    }

    // Used in the distribution list to exclude certain "projects".  Poor design Thau to 
    // have these listed as "biogeographicregions".
    // United States 
    public static boolean isAggregate(String projectName) {
      boolean isAggregate = false;
      if (
        //   "projectsants".equals(projectName)
        //  || "globalants".equals(projectName) ||
             Project.WORLDANTS.equals(projectName)
          || Project.ALLANTWEBANTS.equals(projectName)
          // || "unitedstatesants".equals(projectName)
        ) isAggregate = true;
      // if (isAggregate) s_log.warn("isAggregate() projectName:" + projectName);
      return isAggregate; 
    }
    
    private static boolean unloadedProjectsReported = false;
    
    
    public static Project getProject(String useName) {
      return getProject(useName, true);
    }
    public static Project getProject(String useName, boolean defaultToAllAntweb) {
      if (useName == null || "".equals(useName)) {
        return getProject(Project.ALLANTWEBANTS);
        //s_log.warn("useName is null");
        //AntwebUtil.logStackTrace();
      }
      HashMap<String, Project> projects = getAllProjectsHash();
      if (projects == null || projects.keySet() == null) {
        if (unloadedProjectsReported == false) {
          s_log.warn("getProject() Seems projects not loaded yet.  useName:" + useName);
          unloadedProjectsReported = true;
        }
        return null;
      } else {
        if (unloadedProjectsReported == true) {
          s_log.warn("getProject() Projects are loaded now. Projects.size:" + projects.size());
          unloadedProjectsReported = false;
        }
      }
      for (String key : projects.keySet()) {
        if (useName.equals(key)) {
          return s_allProjectsHash.get(key);
        }
      }

      String projectName = ProjectMgr.getProjectName(useName);

      //A.log("getProject() useName:" + useName + " projectName:" + projectName);

      if (projectName != null) return ProjectMgr.getProject(projectName, false);
      
      return null;
    }

  /*
    ProjectName would be something like "southcarolinaants".  
    DisplayName could be "South_Carolina" - because "South Carolina" is the title.
      Or it could be anything as defined in the display_key field of the project table.
  */
    public static String getProjectName(String candidate) {
      // candidate could be projectName, displayKey, or Title (with underscores instead of spaces).
      HashMap<String, Project> projects = getAllProjectsHash();
      if (projects == null) {
        s_log.warn("getProjectName() projects is null.  Startup issue? Database up? candidate:" + candidate);
        return candidate;
      }
      for (String key : projects.keySet()) {
        Project project = s_allProjectsHash.get(key);
        if (project.getName().equals(candidate)) return project.getName();

        String newKey = null;
        if (candidate == null) {
          //A.log("getProjectName() candidate is null");
          return null;
        }
        if (candidate.contains("_")) newKey = candidate.replace("_", " ");
        //A.log("getProjectName() candidate:" + candidate + " newKey:" + newKey + " title:" + project.getTitle());
        if (project.getTitle().equals(newKey)) return project.getName();

        if (project.getDisplayKey() != null && project.getDisplayKey().equals(candidate)) return project.getName();
      }
      return null;
    }

    public static String getDisplayKey(String projectName) {
      HashMap<String, Project> projects = getAllProjectsHash();
      for (String key : projects.keySet()) {
        Project project = s_allProjectsHash.get(key);
        if (project.getName().equals(projectName)) {

          if (project.getDisplayKey() != null && !project.getDisplayKey().equals("")) {
            return project.getDisplayKey();
          }
//          if (project.getTitle() != null && !project.getTitle().equals("")) {
//            s_log.warn("getDisplayKey() Dont think we should return a modified title.  Why?  Remove.");
            // See similar logic in getProjectName(candidate) above.
//            return project.getTitle().replace(" ", "_");          
//          }
          return project.getName();
        }
      }
      return null;
    }

    public static String getUseName(String candidate) {
      HashMap<String, Project> projects = getAllProjectsHash();
      if (projects == null) return candidate;  // Could happen before projects are loaded.
      for (String key : projects.keySet()) {
        Project project = s_allProjectsHash.get(key);
        if (project.getName().equals(candidate)
          || (project.getDisplayKey() != null && project.getDisplayKey().equals(candidate)))
        {
          if (project.getDisplayKey() != null && !project.getDisplayKey().equals("")) {
            //s_log.warn("getUseName() *** " + project.getDisplayKey());
            return project.getDisplayKey();
          }
          return project.getName();
        }
      }
      return null;
    }

    public static String getTitle(String projectName) {
      HashMap<String, Project> projects = getAllProjectsHash();
      for (String key : projects.keySet()) {
        Project project = s_allProjectsHash.get(key);
        if (project.getName().equals(projectName)) {
          return project.getTitle();
        }
      }
      return "";
    }

    public static boolean hasMoved(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // To support projects that have moved.
        String target = HttpUtil.getTarget(request);
        if (target != null) {
          String redirect = null;
          // Until we support bioregion pages.

/*          
          if (target.contains("malagasyants")) redirect = "/bioregion.do?name=Malagasy";
          if (target.contains("eurasianants")) redirect = "/bioregion.do?name=Palearctic";
          if (target.contains("nearcticants")) redirect = "/bioregion.do?name=Nearctic";
          if (target.contains("pacificislandsants")) redirect = "/bioregion.do?name=Oceania";
          if (target.contains("africanants")) redirect = "/bioregion.do?name=Afrotropical";
          if (target.contains("neotropicalants")) redirect = "/bioregion.do?name=Neotropical";
          if (target.contains("australianants")) redirect = "/bioregion.do?name=Australasia";
          if (target.contains("southeastasiaants")) redirect = "/bioregion.do?name=Indomalaya";
*/

          // Could be something like this.  Would need to substitute the full project=malagasyants for bioregionName=Malagasy          
          if (target.contains("malagasyants")) redirect = target.replace("project=malagasyants", "bioregionName=Malagasy");
          if (target.contains("eurasianants")) redirect = target.replace("project=eurasianants", "Palearctic"); //"/bioregion.do?name=Palearctic";
          if (target.contains("nearcticants")) redirect = target.replace("project=nearcticants", "Nearctic"); //"/bioregion.do?name=Nearctic";
          if (target.contains("pacificislandsants")) redirect = target.replace("project=pacificislandsants", "Oceania"); //"/bioregion.do?name=Oceania";
          if (target.contains("africanants")) redirect = target.replace("project=africanants", "Afrotropical"); //"/bioregion.do?name=Afrotropical";
          if (target.contains("neotropicalants")) redirect = target.replace("project=neotropicalants", "Neotropical"); //"/bioregion.do?name=Neotropical";
          if (target.contains("australianants")) redirect = target.replace("project=australianants", "Australasia"); //"/bioregion.do?name=Australasia";
          if (target.contains("southeastasiaants")) redirect = target.replace("project=southeastasiaants", "Indomalaya"); //"/bioregion.do?name=Indomalaya";


          if (target.contains("globalants")) redirect = "/project.do?name=allantwebants";
          if (target.contains("projectsants")) redirect = AntwebProps.getDomainApp();

          if (target.contains("calants") && !target.contains("eotropicalants") && !target.contains("frotropicalants")) redirect = target.replace("calants", "californiaants");
          if (target.contains("barrowants")) redirect = target.replace("barrowants", "barrowislandants");
          if (target.contains("europaants")) redirect = target.replace("europaants", "europaislandants");
          if (target.contains("solomonsants")) redirect = target.replace("solomonsants", "solomonislandsants");
          if (target.contains("solomonislandants")) redirect = target.replace("solomonislandants", "solomonislandsants");
          if (target.contains("saudiants")) redirect = target.replace("saudiants", "saudiarabiaants");
          if (target.contains("uaeants")) redirect = target.replace("uaeants", "unitedarabemiratesants");
          if (target.contains("czechants")) redirect = target.replace("czechants", "czechrepublicants");
          if (target.contains("madants")) redirect = target.replace("madants", "madagascarants");
          if (target.contains("galapagosants")) redirect = target.replace("galapagosants", "galapagosislandsants");

          try {
            if (target.contains("project=") && target.contains("ants")) {
              int projectI = target.indexOf("project=") + 8;
              int antsI = target.indexOf("ants");
              //s_log.warn("hasMoved() projectI:" + projectI + " antsI:" + antsI);
              String projStr = target.substring(projectI, antsI);
              Geolocale country = GeolocaleMgr.getCountryWithLowerCaseNoSpace(projStr);     
              if (country != null) {
                String newTarget = target.substring(0, projectI - 8) + "countryName=" + country.getName() + target.substring(antsI + 4);
                //s_log.warn("hasMoved() newTarget:" + newTarget);                 

                // We have found that the projectName without "ants" is a country name, so use it.  Redirect.
                redirect = newTarget;
              }       
              //s_log.warn("hasMoved() projStr:" + projStr + " target:" + target);   
            }
          } catch (Exception e) { // do nothing 
          }
/*
http://localhost/antweb/browse.do?genus=pseudomyrmex&rank=genus&project=brazilants
*/

          if (redirect != null) {
            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
			response.setHeader("Location", redirect);
            // response.sendRedirect(redirect);
            PageTracker.remove(request);
            return true;
          }
        }
        return false;    
    }

}

