package org.calacademy.antweb.util;

import java.io.IOException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
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

public final class SpeciesListAction extends Action {
/* /getSpeciesList.do?name=allantwebants   This class returns a link to the species authority file */

    private static Log s_log = LogFactory.getLog(SpeciesListAction.class);

    public ActionForward execute (
        ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        Locale locale = getLocale(request);
        HttpSession session = request.getSession();
        Date startTime = new Date();
        
        DynaActionForm df = (DynaActionForm) form;
        String name = (String) df.get("name");       // (project name)
        if (name != null) {
          Connection connection = null;
          try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "SpeciesListAction.execute()");
        
            if (name.equals(Project.ALLANTWEBANTS)) {
              return getAllAntwebAnts(connection, dataSource, mapping, request);
            } else {
              if (ProjectDb.isSpeciesListMappingProject(connection, name)) {
                return createSpeciesListLink(connection, dataSource, mapping, request, name);
              } else {
                // for all regions (and bioregions [only malagasy due to jsp conditions])
                // we link directly to uploaded authority file
                String message = null;
                int antsIndex = name.indexOf("ants");
                if (antsIndex < 0) {
                    message = "antsIndex:" + antsIndex + " for name:" + name;
                    s_log.error(message);
                } else {
                    String dir = name.substring(0, antsIndex);
                    if (dir.equals("mad")) dir = "madagascar";
                    if (dir.equals("cal")) dir = "california";
                    String url = AntwebProps.getDomainApp() + "/web/speciesList/" + dir + "/" + name + UploadFile.getSpeciesListTail();
                    message = "<b>'Right-click' and 'Save Link As' to download:</b><br> <a href=\"" + url + "\">" + url + "</a>";
                    s_log.info(message);
                }
                request.setAttribute("message", message);
                return mapping.findForward("message");
              }
            }
           
          } catch (SQLException e) {
            String message = e.toString();
            s_log.error("execute() e:" + message);
            request.setAttribute("message", message);
            return mapping.findForward("message");              
          } finally {
            DBUtil.close(connection, this, "SpeciesListAction.execute()");
          }    
        }

        HttpUtil.finish(request, startTime);
        return null;
    }


    private ActionForward createSpeciesListLink(Connection connection, DataSource dataSource, ActionMapping mapping
        , HttpServletRequest request, String name) throws SQLException {
        int specimenCount = 0;
        StringBuffer data = new StringBuffer();    

        String dir = name.substring(0, name.indexOf("ants"));
        if (dir.equals("mad")) dir = "madagascar";
        if (dir.equals("cal")) dir = "california";
        String fileName = name + UploadFile.getSpeciesListTail();
        String url = AntwebProps.getDomainApp() + "/" + dir + "/" + fileName;
        String fullPath = AntwebProps.getDocRoot() + dir + "/" + fileName;

        if (true) { //!AntwebCacheMgr.isCached(fullPath)) {

            if (DBUtil.isServerBusy(dataSource, request)) {
                return mapping.findForward("message");            
            }

            String theQuery =
                "select taxon_name from taxon where family = 'formicidae' "
                  + " and taxarank in ('species', 'subspecies') "
                  + " and taxon_name in (select taxon_name from proj_taxon where project_name = '" + name + "')";
 
            Statement stmt = null;
            ResultSet rset = null;
            try {
                stmt = DBUtil.getStatement(connection , "SpeciesListAction.createSpeciesListLink()");
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
                DBUtil.close(stmt, rset, this, "SpeciesListAction.createSpeciesListLink()");
            }

            s_log.debug("createSpeciesListLink() fullPath:" + fullPath);
            AntwebUtil.writeFile(fullPath, data.toString());
        }
        String message = "<b>'Right-click' and 'Save Link As' to download:</b><br> <a href=\"" + url + "\">" + url + "</a>";
        s_log.info(message);
        request.setAttribute("message", message);
        return mapping.findForward("message");
    }   

    private ActionForward getAllAntwebAnts(Connection connection, DataSource dataSource
        , ActionMapping mapping, HttpServletRequest request)  throws SQLException {
        //String projectName = "allantweb";
        int specimenCount = 0;
        StringBuffer data = new StringBuffer();    

        String dir = "/web/data/";
        new Utility().createDirectory(dir);
        String fileName = "allantwebants" + UploadFile.getSpeciesListTail();
        String fullPath = AntwebProps.getDocRoot() + dir + fileName;
        
        if (true) { //!AntwebCacheMgr.isCached(fullPath)) {

            if (DBUtil.isServerBusy(dataSource, request)) {
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
        String message = "<b>'Right-click' and 'Save Link As' to download:</b><br>&nbsp;&nbsp;&nbsp;&nbsp; <a href=\"" + url + "\">" + url + "</a>";
        s_log.info("getAllAntwebAnts() specimenCount:" + specimenCount);
        request.setAttribute("message", message);
        return mapping.findForward("message");
    }
    
}
