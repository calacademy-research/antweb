package org.calacademy.antweb.util;

import java.io.IOException;
import java.util.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.apache.struts.action.*;
import java.sql.*;
import java.util.Date;
import javax.sql.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.AntwebUtil;
import org.calacademy.antweb.upload.UploadFile;
import org.calacademy.antweb.home.TaxonDb;
import org.calacademy.antweb.home.ProjectDb;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class SpeciesListAction extends AntwebAction {
/* /getSpeciesList.do?name=allantwebants   This class returns a link to the species authority file.
   Administrators only. A request shows the link to the file as it is; the file is written again
   only when an administrator asks for it (&rebuild=true, then a POST from the confirm button). */

    private static final Log s_log = LogFactory.getLog(SpeciesListAction.class);

    public ActionForward execute (
        ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ActionForward adminOnly = Check.admin(request, mapping);
        if (adminOnly != null) return adminOnly;

        Date startTime = new Date();
        
        DynaActionForm df = (DynaActionForm) form;
        String name = (String) df.get("name");       // (project name)
        if (name != null && !isValidName(name)) {
            request.setAttribute("message", "Invalid name.");
            return mapping.findForward("message");
        }
        boolean rebuild = "true".equals(request.getParameter("rebuild"));
        if (name != null && rebuild) {
            ActionForward confirm = PostOnly.confirm(request, mapping, "Write the species list file for " + name + " again");
            if (confirm != null) return confirm;
        }
        if (name != null) {
          Connection connection = null;
          String dbMethodName = DBUtil.getDbMethodName("SpeciesListAction.execute()");
          try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
        
            if (name.equals(Project.ALLANTWEBANTS)) {
              return getAllAntwebAnts(connection, dataSource, mapping, request, rebuild);
            } else {
              if (ProjectDb.isSpeciesListMappingProject(connection, name)) {
                return createSpeciesListLink(connection, dataSource, mapping, request, name, rebuild);
              } else {
                // for all regions (and bioregions [only malagasy due to jsp conditions])
                // we link directly to uploaded authority file
                String message = null;
                int antsIndex = name.indexOf("ants");
                if (antsIndex < 0) {
                    message = "antsIndex:" + antsIndex + " for name:" + Html.esc(name);
                    s_log.error(message);
                } else {
                    String dir = name.substring(0, antsIndex);
                    if (dir.equals("mad")) dir = "madagascar";
                    if (dir.equals("cal")) dir = "california";
                    String url = AntwebProps.getDomainApp() + "/web/speciesList/" + dir + "/" + name + UploadFile.getSpeciesListTail();
                    message = "<b>'Right-click' and 'Save Link As' to download:</b><br> <a href=\"" + Html.escAttr(url) + "\">" + Html.esc(url) + "</a>";
                    s_log.info(message);
                }
                request.setAttribute("message", message);
                return mapping.findForward("message");
              }
            }
           
          } catch (SQLException e) {
            String message = Html.esc(e.toString());
            s_log.error("execute() e:" + message);
            request.setAttribute("message", message);
            return mapping.findForward("message");              
          } finally {
            DBUtil.close(connection, this, dbMethodName);
          }    
        }

        HttpUtil.finish(request, startTime);
        return null;
    }


    /** Project names are letters, digits, underscore and hyphen. */
    public static boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,80}");
    }

    /** The message page: a link to the file if it exists, and a link to write it again. */
    static String fileMessage(String url, boolean exists, String name) {
        String rebuildUrl = AntwebProps.getDomainApp() + "/getSpeciesList.do?name=" + Html.url(name) + "&rebuild=true";
        String rebuildLink = "<br><br><a href=\"" + Html.escAttr(rebuildUrl) + "\">Write the file again</a> (loads every species; slow)";
        if (!exists) return "The species list file for " + Html.esc(name) + " has not been written yet." + rebuildLink;
        return "<b>'Right-click' and 'Save Link As' to download:</b><br> <a href=\"" + Html.escAttr(url) + "\">" + Html.esc(url) + "</a>" + rebuildLink;
    }

    private ActionForward createSpeciesListLink(Connection connection, DataSource dataSource, ActionMapping mapping
        , HttpServletRequest request, String name, boolean rebuild) throws SQLException {
        int specimenCount = 0;
        StringBuffer data = new StringBuffer();    

        String dir = name.substring(0, name.indexOf("ants"));
        if (dir.equals("mad")) dir = "madagascar";
        if (dir.equals("cal")) dir = "california";
        String fileName = name + UploadFile.getSpeciesListTail();
        String url = AntwebProps.getDomainApp() + "/" + dir + "/" + fileName;
        String fullPath = AntwebProps.getDocRoot() + dir + "/" + fileName;

        if (rebuild) {

            if (DBStatus.isServerBusy(connection, request)) {
            //if (DBUtil.isServerBusy(dataSource, request)) {
                return mapping.findForward("message");            
            }

            String theQuery =
                "select taxon_name from taxon where family = 'formicidae' "
                  + " and taxarank in ('species', 'subspecies') "
                  + " and taxon_name in (select taxon_name from proj_taxon where project_name = '" + DBUtil.escapeQuotes(name) + "')";
 
            Statement stmt = null;
            ResultSet rset = null;
            String dbMethodName = DBUtil.getDbMethodName("SpeciesListAction.createSpeciesListLink()");

            try {
                stmt = DBUtil.getStatement(connection , dbMethodName);
                rset = stmt.executeQuery(theQuery);

                data.append(Species.getDataHeader() + "\n");

                s_log.debug("createSpeciesListLink() query:" + theQuery);

                while (rset.next()) {
                  ++specimenCount;
                  String taxonName = rset.getString(1);
                  Species specie = (Species) new TaxonDb(connection).getTaxon(taxonName);

                  data.append(specie.getData() + "\n");
                }
            } finally {
                DBUtil.close(stmt, rset, this, dbMethodName);
            }

            s_log.debug("createSpeciesListLink() fullPath:" + fullPath);
            AntwebUtil.writeFile(fullPath, data.toString());
        }
        String message = fileMessage(url, new java.io.File(fullPath).isFile(), name);
        s_log.info("createSpeciesListLink() name:" + name + " rebuild:" + rebuild);
        request.setAttribute("message", message);
        return mapping.findForward("message");
    }   

    private ActionForward getAllAntwebAnts(Connection connection, DataSource dataSource
        , ActionMapping mapping, HttpServletRequest request, boolean rebuild)  throws SQLException {
        //String projectName = "allantweb";
        int specimenCount = 0;
        StringBuffer data = new StringBuffer();    

        String dir = "/web/data/";
        String fileName = "allantwebants" + UploadFile.getSpeciesListTail();
        String fullPath = AntwebProps.getDocRoot() + dir + fileName;
        
        if (rebuild) {
            new Utility().createDirectory(dir);

            if (DBStatus.isServerBusy(connection, request)) {
            //if (DBUtil.isServerBusy(dataSource, request)) {
                return mapping.findForward("message");            
            }		              
      
            String theQuery =
                "select taxon_name from taxon where family = 'formicidae' "
                  + " and taxarank in ('species', 'subspecies') ";

            Statement stmt = null;
            ResultSet rset = null;
            try {
              stmt = DBUtil.getStatement(connection, "SpeciesListAction.getAllAntwebAnts()");
              stmt = connection.createStatement();
              rset = stmt.executeQuery(theQuery);

              data.append(Species.getDataHeader() + "\n");

              s_log.debug("query:" + theQuery);

              while (rset.next()) {
                ++specimenCount;
                String taxonName = rset.getString(1);
                Species specie = (Species) new TaxonDb(connection).getTaxon(taxonName);

                data.append(specie.getData() + "\n");
              }

            } finally {
                DBUtil.close(stmt, rset, this, "SpeciesListAction.getAllAntwebAnts()");
            }
            
            AntwebUtil.writeFile(fullPath, data.toString());
        }
        
        String url = AntwebProps.getDomainApp() + dir + fileName;
        String message = fileMessage(url, new java.io.File(fullPath).isFile(), Project.ALLANTWEBANTS);
        s_log.info("getAllAntwebAnts() rebuild:" + rebuild + " specimenCount:" + specimenCount);
        request.setAttribute("message", message);
        return mapping.findForward("message");
    }
    
}
