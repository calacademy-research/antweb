package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;

public class ProjectDb extends AntwebDb {
    
    private static final Log s_log = LogFactory.getLog(ProjectDb.class);
        
    public ProjectDb(Connection connection) {
      super(connection);
    }

    public static int CALACADEMY = 1;

    public HashMap<String, Project> getAllProjects() {
      HashMap<String, Project> projects = new HashMap<>();

      String query = "select project_name from project " 
        + " order by project_name"
        ;
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "ProjectDb.getAllProjects()");

        //A.log("getAllProjects() query:" + query);

        rset = stmt.executeQuery(query);        
        while (rset.next()) {
          String projectName = rset.getString("project_name");
          if (projectName == null || "null".equals(projectName)) continue;
          Project project = getProject(projectName);               
          //A.log("getAllProjects() projectName:" + projectName + " project:" + project);
          projects.put(projectName, project);
        }
      } catch (SQLException e) {
        s_log.error("getAllProjects() query:" + query + " e:" + e);
      } finally {
        DBUtil.close(stmt, rset, this, "ProjectDb.getAllProjects()");
      }
      return projects;
    }
    
    // This is used to populate the menus for GLOBAL.
    public ArrayList<Project> getProjects(String scope) {

      ArrayList<Project> projects = new ArrayList<>();

      String query = "select project_name from project " 
         + " where scope = '" + scope + "'"
        + " and is_live = 1"
        + " order by project_name"
        ;
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "ProjectDb.getProjects(scope)");

        //A.log("getProjects() query:" + query);

        rset = stmt.executeQuery(query);        
        while (rset.next()) {
          String projectName = rset.getString("project_name");
          Project project = getProject(projectName);               
          projects.add(project);
        }
      } catch (SQLException e) {
        s_log.error("getProjects(scope) query:" + query + " e:" + e);
      } finally {
        DBUtil.close(stmt, rset, this, "ProjectDb.getProjects(scope)");
      }
      return projects;
    }

    public ArrayList<Project> getSubProjects() {
      ArrayList<Project> projects = new ArrayList<>();

      String query = "select project_name from project " 
        + " where project_name not in ('allantwebants', 'worldants')"
        + " and scope = '" + Project.PROJECT + "'"        
        + " and is_live = 1"
        + " order by project_name"
        ;
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "ProjectDb.getProjects()");

        //A.log("getProjects() query:" + query);

        rset = stmt.executeQuery(query);        
        while (rset.next()) {
          String projectName = rset.getString("project_name");
          Project project = getProject(projectName);               
          projects.add(project);
        }
      } catch (SQLException e) {
        s_log.error("getProjects() query:" + query + " e:" + e);
      } finally {
        DBUtil.close(stmt, rset, this, "ProjectDb.getProjects()");
      }
      return projects;
    }

    //was getFromDb()
    public Project getProject(String name) {
      return getProject(name, true);
    }

    public Project getProject(String name, boolean deep) {
        if (name == null) return null;
        Project project = new Project();
        project.setName(name);
        
        String theQuery = "select * from project " 
        + " where project_name='" + name + "'"
        + " order by project_name"
        ;

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "ProjectDb.getProject()");
            rset = stmt.executeQuery(theQuery);
            
            Formatter format = new Formatter();
            boolean found = rset.next();
            if (!found) {
              // s_log.error("getProject() projectName:" + name + " not found.  query:" + theQuery);      
              // org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);                   
              return null;
            }

            //project.setRoot(rset.getString("root"));
            project.setTitle(format.MSSQLunescapeCharacters(rset.getString("project_title")));
            project.setLastChanged(rset.getDate("last_changed"));
            // project.setContents(format.MSSQLunescapeCharacters(rset.getString("contents")));

            project.setMapImage(rset.getString("map"));
            
            project.setSpeciesListMappable(rset.getBoolean("species_list_mappable"));

            project.setScope(rset.getString("scope"));
            project.setIsLive(rset.getInt("is_live") == 1);

            project.setDisplayKey(rset.getString("display_key"));

              project.setExtent(rset.getString("extent"));
              project.setCoords(rset.getString("coords"));

            project.setSource(rset.getString("source"));
                        
            project.setSubfamilyCount(rset.getInt("subfamily_count"));
            project.setGenusCount(rset.getInt("genus_count"));
            project.setSpeciesCount(rset.getInt("species_count"));
            project.setValidSpeciesCount(rset.getInt("valid_species_count"));
            //project.setEndemicSpeciesCount(rset.getInt("endemic_species_count"));
            project.setSpecimenCount(rset.getInt("specimen_count"));
            project.setImageCount(rset.getInt("image_count"));          
            project.setImagedSpecimenCount(rset.getInt("imaged_specimen_count"));
            project.setTaxonSubfamilyDistJson(rset.getString("taxon_subfamily_dist_json"));
            project.setSpecimenSubfamilyDistJson(rset.getString("specimen_subfamily_dist_json"));
            project.setChartColor(rset.getString("chart_color"));
                        
            if (deep) {
              //A.log("fetch description");
              //project.setConnection(getConnection());
              
              Hashtable<String, String> description = new DescEditDb(getConnection()).getDescription(project.getName());
              project.setDescription(description);
            }

            //A.log("getFromDb() map:" + getMap() + " speciesListMappable:" + getSpeciesListMappable());
        } catch (SQLException e) {
            s_log.error("getProject() projectName:" + name + " query:" + theQuery + " e:" + e);
            AntwebUtil.logStackTrace(e);
        } finally {
            DBUtil.close(stmt, rset, this, "ProjectDb.getProject()");
        }
        return project;
    }
        
// was Project.saveToDb(Project project)
    public void save(Project project) throws SQLException {
        if (project.getName() != null && project.getName().length() > 0) {
            String theInsert;
            if (null != project.getScope()) {
               theInsert = "insert into project (root, project_title, scope, project_name, species_list_mappable) values (";
               theInsert += "'" + project.getRoot() + "','" + project.getTitle() + "','" + project.getScope() + "','" + project.getName() + "', " + project.getSpeciesListMappable() + ")";
            } else {
               theInsert = "insert into project (root, project_title, scope, project_name) values (";
               theInsert += "'" + project.getRoot() + "','" + project.getTitle() + "', null,'" + project.getName() + "')";
            }
            
            Statement stmt = null;
            try {
                stmt = DBUtil.getStatement(getConnection(), "ProjectDb.save()");
                stmt.executeUpdate(theInsert);
            } catch (SQLException e) {
                s_log.error("save() root:" + project.getRoot() + " insert: " + theInsert);
                //org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
                throw e;
            } finally {
                DBUtil.close(stmt, null, this, "ProjectDb.save()");
            }
        }
    }
    // was Project.updateInDb()
    public void update(Project project) {
        if (project.getName() != null && project.getName().length() > 0) {
            String theQuery = "update project set ";
            theQuery += addSet("project_title", project.getTitle()) + ",";
            theQuery += addSet("last_changed", DBUtil.getCurrentSQLDate().toString()) + ",";

            theQuery += " species_list_mappable = " + project.getSpeciesListMappable() + ",";

            int MAX_MAP_LENGTH = 40;  // This is what the field is defined as in the database.
              // Weirdness in design of the getMapString causes danger of overwriting the database values.
            String mapImage = project.getMapImage();
            if (mapImage != null && mapImage.length() < MAX_MAP_LENGTH) {
                s_log.info("updateInDb() scope:" + project.getScope() + " title:" + project.getTitle() + " good mapImage:" + mapImage);                        
                theQuery += addSet("map", mapImage) + ",";
            } else {
                //theQuery += addSet("map", "") + ",";    // No.  If value is not appropriate, do not update!
                /* map value for africanants should be:biogeo-Africanv3a.gif not:<img class=border border=0 src="african/biogeo-Africanv3a.gif" width=233 height=242> */
                s_log.info("updateInDb() scope:" + project.getScope() + " title:" + project.getTitle() + " mapImage is null or exceeds:" + MAX_MAP_LENGTH 
                  + " mapImage:"+ mapImage + " Updating other fields.");            
            }
/*            
            theQuery += addSet("authorbio", project.getAuthorBio()) + ",";
            theQuery += addSet("author", project.getAuthor()) + ",";
*/
            theQuery += addSet("root", project.getRoot()) + ",";

            theQuery += " is_live = " + project.getIsLive() + ",";
            theQuery += addSet("source", project.getSource()) + ",";

              theQuery += addSet("extent", project.getExtent()) + ",";
              theQuery += addSet("coords", project.getCoords()) + ",";

            theQuery += addSet("display_key", project.getDisplayKey());
                  
            theQuery += " where project_name='" + project.getName() + "'";
            Statement stmt = null;
            try {
                stmt = DBUtil.getStatement(getConnection(), "ProjectDb.update()");
                stmt = getConnection().createStatement();
                stmt.executeUpdate(theQuery);

                //if ("worldants".equals(project.getName())) A.log("update() scope:" + project.getScope() + " title:" + project.getTitle() + " query:" + theQuery);

            } catch (SQLException e) {
                s_log.error("update() scope:" + project.getScope() + " title:" + project.getTitle() + " query: " + theQuery);
                AntwebUtil.logStackTrace(e);
            } finally {
                DBUtil.close(stmt, null, this, "ProjectDb.update()");
            }
        }
    }        

    public ArrayList<SpeciesListable> fetchSpeciesLists(Login login) throws SQLException {
      int loginId = login.getId();
    
    //  return fetchSpeciesLists(login.getId());
   // }
   // public ArrayList<SpeciesListable> fetchSpeciesLists(int loginId) throws SQLException {
    
      // A 0 adminId implies admin and will not restrict the search
      ArrayList<SpeciesListable> speciesListList = new ArrayList<>();

      //A.log("fetchProjects() groupId:" + loginId);        
      Statement stmt = null;
      ResultSet rset = null;
      try {
          stmt = DBUtil.getStatement(getConnection(), "fetchSpeciesLists()");

          String theQuery;
          if (loginId == 0 || login.isAdmin()) {
            theQuery = "select project_name, project_title, root from project "
              + " where project_name not in ('worldants', 'allantwebants')"
              + " order by project_title"
              ;
          } else {            
            theQuery = "select p.project_name, p.project_title, p.root from project p, login_project lp"
               + " where lp.project_name = p.project_name "
               + "   and lp.login_id = " + loginId
               + "   and p.project_name not in ('worldants', 'allantwebants')"
               + " order by project_title"
               ;
          }
          
          if (AntwebProps.isDevMode()) {
            //s_log.warn("fetchSpeciesLists() theQuery:" + theQuery);
            //AntwebUtil.logStackTrace();
 /*
 	at org.calacademy.antweb.home.ProjectDb.fetchSpeciesLists(ProjectDb.java:359)
	at org.calacademy.antweb.home.SpeciesListDb.fetchSpeciesLists(SpeciesListDb.java:62)
	at org.calacademy.antweb.home.SpeciesListDb.fetchSpeciesListsStr(SpeciesListDb.java:70)
	at org.calacademy.antweb.curate.speciesList.SpeciesListHistoryAction.execute(SpeciesListHistoryAction.java:74)
*/
		 }

          rset = stmt.executeQuery(theQuery);
          String name, title, root;

          while (rset.next()) {
            name = rset.getString("project_name");
            title = rset.getString("project_title");
            root = rset.getString("root");
            //s_log.info("fetchProjects() name:" + projectName + " title:" + projectTitle);    
            if (name != null && name.contains("ants")) {
              //A.log("fetchIsMappableSpeciesLists() 1 name:" + name + " root:" + root + " title:" + title);              
              Project project = new Project();
              project.setName(name);
              project.setTitle(title);      
              //project.setRoot(root);      
              speciesListList.add(project);
            } else {
              s_log.info("fetchSpeciesLists() 2 name:" + name + " root:" + root + " title:" + title);
            }  
          }

      } catch (SQLException e) {
         s_log.error("fetchSpeciesLists() e:" + e + " loginId:" + loginId + ": ");
         AntwebUtil.logStackTrace(e);
      } finally {
        DBUtil.close(stmt, rset, "fetchSpeciesLists()");
      }
      //Collections.sort(speciesListList);

      return speciesListList;
    }
    
    public void xgetStats(Project project) {

        String query = "";
        
        Statement stmt = null;
        ResultSet rset = null;
        try {

            StatusSet statusSet = new StatusSet(StatusSet.VALID);
            if (!Project.WORLDANTS.equals(project.getName())) statusSet = new StatusSet(StatusSet.ALL);
            
           // A.log("getStatsFromDb() name:" + getName() + " andCriteria:" + statusSet.getAndCriteria());

            query = "select count(taxon.subfamily) from taxon, proj_taxon " 
                    + " where proj_taxon.project_name = '" + project.getName() + "'" 
                    + " and proj_taxon.taxon_name = taxon.taxon_name " 
                    + " and taxarank = 'subfamily'"
                    + statusSet.getAndCriteria()
                    ;
                    // + " and taxon.valid=1";

            stmt = getConnection().createStatement();
            rset = stmt.executeQuery(query);                    

            rset.next();
            project.setNumSubfamilies(rset.getInt(1));

            DBUtil.close(stmt, rset, this, "getStats() 1");

            query = "select count(taxon.genus) from taxon, proj_taxon " 
                + " where proj_taxon.project_name = '" + project.getName() + "'"
                + " and  proj_taxon.taxon_name = taxon.taxon_name " 
                + " and taxarank = 'genus'"
                + statusSet.getAndCriteria()
                ;

            stmt = getConnection().createStatement();                
            rset = stmt.executeQuery(query);
            rset.next();
            project.setNumGenera(rset.getInt(1));

            DBUtil.close(stmt, rset, this, "getStats() 2");
       
            query = "select count(taxon.species) "
                    + " from taxon, proj_taxon "
                    + " where proj_taxon.project_name = '" + project.getName() + "'"
                    + " and proj_taxon.taxon_name = taxon.taxon_name "
                    + " and taxon.taxarank in ('species', 'subspecies')"
                    + statusSet.getAndCriteria()
                    ;

            stmt = getConnection().createStatement();                
            rset = stmt.executeQuery(query);
            rset.next();
            project.setNumSpecies(rset.getInt(1));

            DBUtil.close(stmt, rset, this, "getStatsFromDB() 3");
            
            query =
                "select count(distinct taxon.subfamily, taxon.genus, taxon.species) " 
                  + " from taxon, proj_taxon, specimen spec, image " 
                  + " where proj_taxon.project_name = '" + project.getName() + "'" 
                  + " and proj_taxon.taxon_name = taxon.taxon_name " 
                  + " and taxon.taxon_name = spec.taxon_name "
                  + " and spec.code = image.image_of_id "
                  + " and taxon.subfamily is not null and taxon.genus is not null "
                  + " and taxon.species is not null and taxon.subfamily != '' and taxon.genus != '' "
                  + " and taxon.species != ''"
                  + statusSet.getAndCriteria()
                    ;
            stmt = getConnection().createStatement();
            rset = stmt.executeQuery(query);
            rset.next();
            int totalImagedSpecies = rset.getInt(1);
            s_log.debug("getStats() Total Imaged Species:" + totalImagedSpecies + " query:" + query);
            project.setNumSpeciesImaged(totalImagedSpecies);
        } catch (Exception e) {
            s_log.error("getStats() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, this, "getStats() 4");
        }
    }
    
    public String addSet(String field, String value) {
        if (field == null) {
            s_log.error("addSet() field is null for value:" + value);
            return "";
        } 
        if (value == null || value.equals("null")) {
            value = ""; 
        }
        value =  AntFormatter.escapeQuotes(value);
        return field + "='" + value + "'";
    }    
          
    public void updateProjects(Login login) throws SQLException {
      if (login.isAdmin()) return; // Admins get all options automatically.  No need to store.
      String dml = "delete from login_project where login_id=" + login.getId();
      Statement stmt = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "updateProjects");
        stmt.executeUpdate(dml);

        //From SaveLoginAction, the list of projects comes from a form.  They are strings.
        String projectName;
        ArrayList<SpeciesListable> projects = login.getProjects();
        if (projects == null) return;                
        //A.log("updateProjects() projects:" + projects);
        for (SpeciesListable project : projects) {
        

          projectName = project.getName();
          dml = "insert into login_project (login_id, project_name) " 
            + " values (" + login.getId() + ",'" + projectName + "')";            

          if ("worldants".equals(projectName)) s_log.info("updateProjects() dml:" + dml);

          stmt.executeUpdate(dml);        
        }       
        stmt.close();
      } catch (SQLException e) {
        s_log.error("updateProjects() for login:" + login.getName() + " dml:" + dml);
        throw e;
      } finally {
          DBUtil.close(stmt, "updateProjects()");
      }
    }
    
    public void deleteSpeciesList(String speciesList) {
        String query;
        try {	
            Statement stmt;
            stmt = getConnection().createStatement();

            query = "delete from project where project_name = '" + speciesList + "'";
            stmt.executeUpdate(query);

            query = "delete from proj_taxon where project_name = '" + speciesList + "'";
            stmt.executeUpdate(query);

            stmt.close();
        } catch (SQLException e) {
          s_log.error("deleteSpeciesList(" + speciesList + ") e:" + e);
        }  

        s_log.info("deleteSpeciesList speciesList:" + speciesList);
    }

    public static String getProjectTableHeader() {
        return "<table border=1><tr><th> Project Name </th><th>Extinct Subfamily</th>" 
            + "<th>Extant Subfamilies</th><th>Valid Subfamilies</th><th>Total Subfamilies</th>" 
            + "<th>Extinct Genera</th><th>Extant Genera</th><th>Valid Genera</th><th>Total Genera</th>" 
            + "<th>Extinct Species</th><th>Extant Species</th><th>Valid Species</th>" 
            + "<th>Total Imaged Species</th><th>Total Species</th><th>Total Taxa</th></tr>";
    }

    public static String getAllProjectStatisticsHtml(ArrayList<ArrayList<String>> statistics, boolean isLink) {
        String allProjectStatsStr = "";
        for (ArrayList<String> projStats : statistics) {
            allProjectStatsStr += ProjectDb.getProjectStatisticsHtml(projStats, isLink);
        }
        return allProjectStatsStr;
    }
    
    public static String getProjectStatisticsHtml(ArrayList stats, boolean isLink) {
      // See ProjTaxonDb.getProjectStatistics()
      String statisticsStr = "";             
      String project = "";
      try {
        project = (String) stats.get(0);
      } catch (ClassCastException e) {
        // This is sloppy.  Sometimes the stats are single project, but in case of reloadProjects
        // it could be an array of arrays.  Circular, confusing, but effective.
        return ProjectDb.getAllProjectStatisticsHtml((ArrayList<ArrayList<String>>)stats, isLink);
      }
      ArrayList<String> statistics = (ArrayList<String>) stats;
      if (isLink) {
        statisticsStr += "<tr><td>" + project + "</td><td>" 
          + "<a href=\"" + AntwebProps.getDomainApp() + "/taxaList.do?extant=0&project=" + project + "&rank=subfamily\">" + statistics.get(1) + "</a></td><td>" 
          + "<a href=\"" + AntwebProps.getDomainApp() + "/taxaList.do?extant=1&project=" + project + "&rank=subfamily\">" + statistics.get(2) + "</a></td><td>" 
          + "<a href=\"" + AntwebProps.getDomainApp() + "/taxaList.do?status=valid&project=" + project + "&rank=subfamily\">" + statistics.get(3) + "</a></td><td>" 
          + "<a href=\"" + AntwebProps.getDomainApp() + "/taxaList.do?project=" + project + "&rank=subfamily\">" + statistics.get(4) + "</a></td><td>"  
          + "<a href=\"" + AntwebProps.getDomainApp() + "/taxaList.do?extant=0&project=" + project + "&rank=genus\">" + statistics.get(5) + "</a></td><td>" 
          + "<a href=\"" + AntwebProps.getDomainApp() + "/taxaList.do?extant=1&project=" + project + "&rank=genus\">" + statistics.get(6) + "</a></td><td>" 
          + "<a href=\"" + AntwebProps.getDomainApp() + "/taxaList.do?status=valid&project=" + project + "&rank=genus\">" + statistics.get(7) + "</a></td><td>" 
          + "<a href=\"" + AntwebProps.getDomainApp() + "/taxaList.do?project=" + project + "&rank=genus\">" + statistics.get(8) + "</a></td><td>"  
          + "<a href=\"" + AntwebProps.getDomainApp() + "/taxaList.do?extant=0&project=" + project + "&rank=species\">" + statistics.get(9) + "</a></td><td>" 
          + "<a href=\"" + AntwebProps.getDomainApp() + "/taxaList.do?extant=1&project=" + project + "&rank=species\">" + statistics.get(10) + "</a></td><td>" 
          + "<a href=\"" + AntwebProps.getDomainApp() + "/taxaList.do?status=valid&project=" + project + "&rank=species\">" + statistics.get(11) + "</a></td><td>"
          ;
         if ("0".equals(statistics.get(12))) statisticsStr += "N/A"; else statisticsStr += statistics.get(12);
         statisticsStr += "</td><td>";  
         statisticsStr += "<a href=\"" + AntwebProps.getDomainApp() + "/taxaList.do?project=" + project + "&rank=species\">" + statistics.get(13) + "</a></td><td>"  
          + "<a href=\"" + AntwebProps.getDomainApp() + "/taxaList.do?project=" + project + "\">" + statistics.get(14) + "</a></td></tr>";
      } else {
        statisticsStr += "<tr><td>" + project + "</td><td>" 
          + statistics.get(1) + "</td><td>" 
          + statistics.get(2) + "</td><td>" 
          + statistics.get(3) + "</td><td>"  
          + statistics.get(4) + "</td><td>" 
          + statistics.get(5) + "</td><td>" 
          + statistics.get(6) + "</td><td>" 
          + statistics.get(7) + "</td><td>"  
          + statistics.get(8) + "</td><td>" 
          + statistics.get(9) + "</td><td>" 
          + statistics.get(10) + "</td><td>" 
          + statistics.get(11) + "</td><td>";
        if ("0".equals(statistics.get(12))) statisticsStr += "N/A"; else statisticsStr += statistics.get(12);
          statisticsStr += "</td><td>";  
        statisticsStr +=
          statistics.get(13) + "</td><td>"
          + statistics.get(14) + "</td></tr>";
      }            
    
      return statisticsStr;
    }           

    public static boolean isSpeciesListMappingProject(Connection connection, String projectName)
      // SpeciesListMappable means that it is available to be used by the UI.
      throws SQLException {

      boolean isSpeciesListMappingProject = false;
      Statement stmt = null;
      try {
          stmt = DBUtil.getStatement(connection, "isSpeciesListMappingProject()");

          String query = "select project_name from project"
                  + " where project_name = '" + projectName + "' and species_list_mappable = 1";
          ResultSet rset1 = stmt.executeQuery(query);
          while (rset1.next()) {
              isSpeciesListMappingProject = true;
          }
      } finally {
          DBUtil.close(stmt, null, "isSpeciesListMappingProject()");
      }
      return isSpeciesListMappingProject;
    }     

 
// ------------- Update Counts From Specimen Data ----------------------


    public void updateCounts() throws SQLException {
        ArrayList<Project> projectList = ProjectMgr.getAntProjects();
        for (Project project : projectList) {
            A.log("updateCounts() ant project:" + project.getName());
            updateCounts(project.getName());
        }
        projectList = ProjectMgr.getGlobalProjects();
        for (Project project : projectList) {
            A.log("updateCounts() global project:" + project.getName());
            updateCounts(project.getName());
        }

        updateProject();

        LogMgr.appendLog("compute.log", "  Projects counts computed", true);
    }

    public void updateCounts(String projectName) throws SQLException {
        Project project = ProjectMgr.getProject(projectName);
        //A.log("project:" + project);
        freshStart(project);

        updateCountsFromSpecimenData(projectName);

        // update fields (subfamily_count, genus_count, species_count, image_count, json fields).
        finish(project);
    }

    private void freshStart(Project project) throws SQLException {
        String projectName = project.getName();
        UtilDb utilDb = new UtilDb(getConnection());
        utilDb.updateField("project", "subfamily_count", null, "project_name = '" + projectName + "'");
        utilDb.updateField("project", "genus_count", null, "project_name = '" + projectName + "'");
        utilDb.updateField("project", "species_count", null, "project_name = '" + projectName + "'");
        //utilDb.updateField("project", "endemic_species_count", null, "project_name = '" + projectName + "'");
        utilDb.updateField("project", "specimen_count", null, "project_name = '" + projectName + "'");
        utilDb.updateField("project", "image_count", null, "project_name = '" + projectName + "'");
        utilDb.updateField("project", "imaged_specimen_count", null, "project_name = '" + projectName + "'");
        utilDb.updateField("project", "taxon_subfamily_dist_json", null, "project_name = '" + projectName + "'");
        utilDb.updateField("project", "specimen_subfamily_dist_json", null, "project_name = '" + projectName + "'");
    }


    private void updateCountsFromSpecimenData(String projectName) {
      Project project = getFromSpecimenData(projectName);
      if (project == null) {
        s_log.error("updateCountsFromSpecimenData() Project not found:" + projectName);
        return;
      }

      String dml;
      Statement stmt = null;      
      try {
          stmt = DBUtil.getStatement(getConnection(), "updateCountsFromSpecimenData()");
          int x = 0;

          dml = "update project " 
            + " set image_count = " + project.getImageCount()
            + "  , specimen_count = " + project.getSpecimenCount()
            + " where project_name = '" + project.getName() + "'";

          x = stmt.executeUpdate(dml);
          if ("bayareaants".equals(projectName)) A.log("updateCountsFromSpecimenData() x:" + x + " dml:" + dml);
      } catch (SQLException e) {
          s_log.error("updateCountsFromSpecimenData() e:" + e);
      } finally {
          DBUtil.close(stmt, null, "updateCountsFromSpecimenData()");
      }      
    }


    public Project getFromSpecimenData(String projectName) {

        Project project = new Project();
        project.setName(projectName);

        getSpecimenAndImageCount(project);

        //A.log("getFromSpecimenData() project:" + project.getName() + " s:" + project.getSpecimenCount() + " i:" + project.getImageCount());

        return project;
    }

    private void getSpecimenAndImageCount(Project project) {

        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getSpecimenAndImageCount()");

            String query = "select sum(pt.specimen_count) specimen_count, sum(pt.image_count) image_count "
                    + " from proj_taxon pt, taxon t "
                    + " where pt.taxon_name = t.taxon_name "
                    + " and t.taxarank in ('species', 'subspecies')"
                    + " and pt.project_name = '" + project.getName() + "'";

            if ("bayareaants".equals(project.getName())) A.log("getSpecimenAndImageCount() query:" + query);

            ResultSet rset = stmt.executeQuery(query);
            while (rset.next()) {
                project.setSpecimenCount(rset.getInt("specimen_count"));
                project.setImageCount(rset.getInt("image_count"));
            }
        } catch (SQLException e) {
            s_log.error("getSpecimenAndImageCount() e:" + e);
        } finally {
            DBUtil.close(stmt, null, "getSpecimenAndImageCount()");
        }
    }


    public void updateProject() throws SQLException {
      updateColors();
    }
    private void updateColors() throws SQLException {
      String[] colors = HttpUtil.getColors();
      ArrayList<Project> projectList = ProjectMgr.getLiveProjects();    
      int i = 0;
      for (Project project : projectList) {
        if (i >= colors.length) i = 0; 
        updateColor(project.getName(), colors[i]);
        ++i;      
      }
    }   
    private void updateColor(String projectName, String color) throws SQLException {
      UtilDb utilDb = new UtilDb(getConnection());
      utilDb.updateField("project", "chart_color", "'" + color + "'", "project_name = '" + projectName + "'" );
    }

    public String finish() throws SQLException {
      for (Project project : ProjectMgr.getLiveProjects()) {
        finish(project);
      }  
      return "Projects Finished";
    }

    private String finish(Project project) throws SQLException {
        updateCountableTaxonData(project);

        //updateImagedSpecimenCount(project);
        updateValidSpeciesCount(project);

        makeCharts(project);

        return "Project Finished:" + project;
    }       

    private void updateCountableTaxonData(Project project) throws SQLException {
        ProjTaxonCountDb projTaxonCountDb = new ProjTaxonCountDb(getConnection());
        String criteria = "project_name = '" + project + "'";
        int subfamilyCount = projTaxonCountDb.getCountableTaxonCount("proj_taxon", criteria, "subfamily");
        int genusCount = projTaxonCountDb.getCountableTaxonCount("proj_taxon", criteria, "genus");        
        int speciesCount = projTaxonCountDb.getCountableTaxonCount("proj_taxon", criteria, "species");

        boolean subfamilyIsZero = subfamilyCount == 0;

        A.log("updateCountableTaxonCounts() project:" + project + " subfamilyCount:" + subfamilyCount + " condition:" + !("worldants".equals(project.getName()) && subfamilyCount == 0));

        //A.log("updateCountableTaxonData() project:" + project + " subfamily:" + subfamilyCount + " genus:" + genusCount + " species:" + speciesCount);
        if (!("worldants".equals(project.getName()) && subfamilyCount == 0)) { // Don't munge data!
          //s_log.warn("updateCountableTaxonData(" + project + ") subfamilyCount:" + subfamilyCount);
          projTaxonCountDb.updateCountableTaxonCounts("project", criteria, subfamilyCount, genusCount, speciesCount);                  
        } else {
          s_log.info("updateCountableTaxonData(" + project + ") Not updating subfamilyCount:" + subfamilyCount);
        }
    }

    private void updateValidSpeciesCount(Project project) throws SQLException {
        int count = getValidSpeciesCount(project);
        UtilDb utilDb = new UtilDb(getConnection());
        utilDb.updateField("project", "valid_species_count", Integer.valueOf(count).toString(), "project_name = '" + project.getName() + "'");
    }

    private int getValidSpeciesCount(Project project) {
        int validSpeciesCount = 0;
        String query = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getValidSpeciesCount()");

            query = "select count(*) count from taxon, proj_taxon pt where taxon.taxon_name = pt.taxon_name"
                    + " and taxarank in ('species', 'subspecies') and status = 'valid' and fossil = 0"
                    + " and project_name = '" + project.getName() + "'";

            rset = stmt.executeQuery(query);
            while (rset.next()) {
                validSpeciesCount = rset.getInt("count");
            }
            if ("bayareaants".equals(project)) A.log("getValidSpeciesCount() project:" + project + " validSpeciesCount:" + validSpeciesCount + " query:" + query);
        } catch (SQLException e2) {
            s_log.error("getValidSpeciesCount() e:" + e2 + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "getValidSpeciesCount()");
        }
        s_log.debug("getValidSpeciesCount() name:" + project.getName() + " count:" + validSpeciesCount);
        return validSpeciesCount;
    }

    // --- Charts ---
        
    public void makeCharts() throws SQLException {
      for (Project project : ProjectMgr.getLiveProjects()) {
        makeCharts(project);
      }  
    }         
            
    public void makeCharts(Project project) throws SQLException {
//A.log("makeCharts() start project:" + project);
      UtilDb utilDb = new UtilDb(getConnection());
      ProjTaxonCountDb projTaxonCountDb = new ProjTaxonCountDb(getConnection());
      String criteria = "project_name = '" + project + "'";
      String taxonCountQuery = getTaxonSubfamilyDistJsonQuery(criteria);
      String specimenCountQuery = getSpecimenSubfamilyDistJsonQuery(criteria);
        //A.log("makeCharts() taxonCountQuery:" + taxonCountQuery);
            utilDb.updateField("project", "taxon_subfamily_dist_json", "'" + projTaxonCountDb.getTaxonSubfamilyDistJson(taxonCountQuery) + "'", criteria);
            utilDb.updateField("project", "specimen_subfamily_dist_json", "'" + projTaxonCountDb.getSpecimenSubfamilyDistJson(specimenCountQuery) + "'", criteria);
    }
    
    public String getTaxonSubfamilyDistJsonQuery(String criteria) {
      String query = "select t.subfamily, count(*) count, t2.chart_color " 
          + " from proj_taxon pt, taxon t, taxon t2, project p " 
          + " where pt.taxon_name = t.taxon_name " 
          + " and t.subfamily = t2.taxon_name "          
          + " and p.project_name = pt.project_name "
          + " and p." + criteria
          + " and t.status in ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable') " 
          + " and t.family = 'formicidae' " 
          + " group by t.subfamily"; 
      return query;
    }
    
    public String getSpecimenSubfamilyDistJsonQuery(String criteria) {
      String query = "select subfamily, count(*) count " 
          + " from proj_taxon pt, specimen s, project p " 
          + " where pt.taxon_name = s.taxon_name " 
          + " and p.project_name = pt.project_name "
          + " and p." + criteria
          + " and s.status in ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable') " 
          + " and s.family = 'formicidae' " 
          + " group by subfamily"; 
      return query;
    }  
    
           
}