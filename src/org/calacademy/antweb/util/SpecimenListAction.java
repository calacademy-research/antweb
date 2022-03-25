package org.calacademy.antweb.util;

import java.io.*;
//import java.nio.file.*;  // need Java 7

import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;
import java.util.Date;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.AntwebUtil;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class SpecimenListAction extends Action {
/* This class returns a link to the specimen authority file generate for any given taxon. */


/*
  To generate the full ant specimen list, this url: http://localhost/antweb/getSpecimenList.do?taxonName=formicidae
    to generate this file:
*/

    private static Log s_log = LogFactory.getLog(SpecimenListAction.class);

    private static int specimenCount = 0;

    public ActionForward execute(
        ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ActionForward a = Check.loginValid(request, mapping); if (a != null) return a; 
        Login accessLogin = LoginMgr.getAccessLogin(request);
        
        s_log.debug("execute() request:" + HttpUtil.getTarget(request));
        
        Date startTime = new Date();
        
        specimenCount = 0;
 
        HttpSession session = request.getSession();        
        DynaActionForm df = (DynaActionForm) form;
        
        String rightClickSave = (String) df.get("action");

        Overview overview = null;
        try {
            overview = OverviewMgr.getAndSetOverview(request);
        } catch (AntwebException e) {
            return OverviewMgr.returnMessage(request, mapping, e);
        }
        
        String taxonName = (String) df.get("taxonName");
        if (taxonName != null) {

          String dir = "/web/data/specimenData";
          new Utility().createDirectory(dir);
          String fileName = "/" + overview.getName() + "-" + taxonName + ".txt";
          String fullPath = AntwebProps.getDocRoot() + dir + fileName;   

          String url = AntwebProps.getDomainApp() + dir + fileName;
          String dataLink = "&middot; <a href=\"" + url + "\" target=\"new\">Tab-delimited data</a>";
          s_log.debug("execute() taxon:" + taxonName + " specimenCount:" + specimenCount + " rightClickSave:" + rightClickSave + " dataLink:" + dataLink);
          request.setAttribute("dataLink", dataLink);

          //String data = null;
          boolean isInCache = false;         
          // This cache logic is different because we do not want to fetch.  We just
          // want to know if it is there.  We just create a link to it if it exists.
           
          boolean isGetCache = "true".equals(df.get("getCache"));
          s_log.debug("execute() getCache:" + isGetCache);
          boolean isGenCache = "true".equals(df.get("genCache"));
          if (!isGenCache) {
            boolean fetchFromCache = AntwebCacheMgr.isFetchFromCache(accessLogin, isGetCache);
            if (fetchFromCache) {
                isInCache = AntwebCacheMgr.hasInCache("specimenData", taxonName, overview.getName());
/*
                data = AntwebCacheMgr.fetchFromCache("specimenData", taxonName, projectName);
                if (data != null) {
                  s_log.warn("execute() Fetched cached page.  taxonName:" + taxonName + " projectName:" + projectName + " cacheType:specimenList");              
                  PrintWriter out = response.getWriter();
                  out.println(data);
                  return null;
                } else {
                  if (isGetCache) {
                    String message = "Specimen Data not found in cache for taxonName:" + taxonName + " projectName:" + projectName;
                    s_log.info("Execute() " + message);
                    request.setAttribute("message", message);
                    return (mapping.findForward("message"));
                  }                
                }
*/
            }
          }
          
          //if (data == null) {
          if (!isInCache) {
            AntwebUtil.remove(fullPath);
            //s_log.warn("execute() writing:" + fullPath);

            //StringBuffer dataBuffer = new StringBuffer();
            //dataBuffer.append(Specimen.getDataHeader() + "\n");

            String rank = null;
          
            Connection connection = null;
            try {

              // To skip the whole business and just generate a full specimen list...
              //if (taxonName.equals(Family.ANT_FAMILY)) {
              //    return generateAllAntwebAnts(mapping, request);
              //}

              DataSource dataSource = getDataSource(request, "conPool");
              
              if (DBUtil.isServerBusy(dataSource, request)) {
                return mapping.findForward("message");            
              }		              
      
              connection = DBUtil.getConnection(dataSource, "SpecimenListAction.execute()");
              Taxon taxon = new TaxonDb(connection).getTaxon(taxonName);

              if (taxon == null) {
                s_log.warn("execute() taxon not found:" + taxonName);
                return null;
              }
              rank = taxon.getRank();
              if (rank == null) {
                s_log.warn("execute() rank is null for taxon:" + taxonName);
                return null;
              }
              if (rank.equals(Rank.SUBSPECIES)) {
                generateAntwebSpecimenData(overview, taxon.getFamily(), taxon.getSubfamily(), taxon.getGenus(), taxon.getSpecies(), taxon.getSubspecies(), fullPath, connection);
            
              } else if (rank.equals(Rank.SPECIES)) {
                generateAntwebSpecimenData(overview, taxon.getFamily(), taxon.getSubfamily(), taxon.getGenus(), taxon.getSpecies(), fullPath, connection);
                //dataBuffer.append(getSpeciesSpecimenData(taxon, projectName));
                
              } else if (rank.equals(Rank.GENUS)) {
                generateAntwebSpecimenData(overview, taxon.getFamily(), taxon.getSubfamily(), taxon.getGenus(), fullPath, connection);
/*              
                taxon.setChildren(projectName);  // project name
                ArrayList<Taxon> theSpecies = taxon.getChildren();
                for (Taxon species : theSpecies) {
                  species.setConnection(taxon.getConnection());              
                  dataBuffer.append(getSpeciesSpecimenData(species, projectName));
                }
*/

              } else if (rank.equals(Rank.SUBFAMILY)) {
                generateAntwebSpecimenData(overview, taxon.getFamily(), taxon.getSubfamily(), fullPath, connection);

              } else if (rank.equals(Rank.FAMILY)) {
                generateAntwebSpecimenData(overview, taxon.getFamily(), fullPath, connection);
              
              } else {
                s_log.warn("execute() Specimen List functionality not supported rank:" + rank + " taxon:" + taxonName);
                request.setAttribute("message", "rank:" + rank + " not supported.");
                return mapping.findForward("message");                
              }
              if (!isGenCache) {
                int busy = DBUtil.getNumBusyConnections(dataSource);
                AntwebCacheMgr.finish(request, connection, busy, startTime, "specimenList", overview, taxonName);
              }

            } catch (SQLException e) {
              String message = e.toString();
              s_log.error("execute() e:" + message);
              request.setAttribute("message", message);
              return mapping.findForward("message");              
            } finally {
              QueryProfiler.profile("specimenList", startTime);            
              DBUtil.close(connection, this, "SpecimenListAction.execute()");
            }
            
          } // if isInCache
          
          String message = dataLink;
          request.setAttribute("message", message);
          return mapping.findForward("justMessage");

/*        // This will return the results directly to the browser
          PrintWriter out = response.getWriter();
          //response.setTitle("SpecimenList - Antweb");
          out.println(data);
          return null;
 */
       }
       return null;  // should not happen
    }

/*
    private String getSpeciesSpecimenData(Taxon species, String projectName) throws SQLException {
        String data = "";
        species.setChildren(projectName);  // project name
        ArrayList<Taxon> specimens = species.getChildren();
        //s_log.warn("getSpeciesSpecimenData() species:" + species.getTaxonName() + " specimenCount:" + specimens.size());
        for (Taxon taxonSpecimen : specimens) {
            ++specimenCount;
            Specimen specimen = (Specimen) taxonSpecimen;
            specimen.setConnection(species.getConnection());
            data += specimen.getData();            
            data += "\n";
        }
        return data;
    }
*/

    private void generateAntwebSpecimenData(Overview overview, String family
      , String fullPath, Connection connection) throws SQLException {
        generateAntwebSpecimenData(overview, family, null, null, fullPath, connection);
    }
    
    private void generateAntwebSpecimenData(Overview overview, String family, String subfamily
      , String fullPath, Connection connection) throws SQLException {
        generateAntwebSpecimenData(overview, family, subfamily, null, fullPath, connection);
    }
    
    private void generateAntwebSpecimenData(Overview overview, String family, String subfamily, String genus
      , String fullPath, Connection connection) throws SQLException {
        generateAntwebSpecimenData(overview, family, subfamily, genus, null, fullPath, connection);
    }
    
    private void generateAntwebSpecimenData(Overview overview, String family, String subfamily, String genus, String species
      , String fullPath, Connection connection) throws SQLException {
        generateAntwebSpecimenData(overview, family, subfamily, genus, species, null, fullPath, connection);
    }

    private void generateAntwebSpecimenData(Overview overview, String family, String subfamily, String genus, String species, String subspecies
      , String fullPath, Connection connection) throws SQLException {
        StringBuffer dataBuffer = new StringBuffer();
        dataBuffer.append(Specimen.getDataHeader() + "\n");
                    
        ArrayList<String> codes = new SpecimenDb(connection).getAntwebSpecimenCodes(overview, family, subfamily, genus, species, subspecies);

        int specimenCount = 0;
        for (String code : codes) {
            ++specimenCount;

            Specimen specimen = Specimen.getShallowInstance(code, connection);
            dataBuffer.append(specimen.getData(connection) + "\n");

            // Write out
            if (specimenCount % 10000 == 0) {
                LogMgr.appendFile(fullPath, dataBuffer.toString());
                dataBuffer = new StringBuffer();                  
            }
            // Log progress
            if (specimenCount % 10000 == 0) {
                s_log.warn("getAllAntwebSpecimenData() specimenCount:" + specimenCount);
            }
        }
        s_log.debug("geneateAntwebSpecimenData() fullPath:" + fullPath + " data:" + dataBuffer);
        LogMgr.appendFile(fullPath, dataBuffer.toString());
        //return dataBuffer;
    }
    
    
    // Functional but deprecated.
    private ActionForward generateAllAntwebAnts(ActionMapping mapping, HttpServletRequest request) throws SQLException {

        String dir = "/web/data/specimenData/";
        new Utility().createDirectory(dir);
        String fileName = "allantwebants-formicidae.txt";
        String fullDir = AntwebProps.getDocRoot() + dir;          
        String fullPath = AntwebProps.getDocRoot() + dir + fileName;          

        s_log.debug("execute() writing:" + fullPath);
        AntwebUtil.remove(fullPath);

        StringBuffer dataBuffer = new StringBuffer();
        dataBuffer.append(Specimen.getDataHeader() + "\n");

        Connection connection = null;
        try {
            DataSource dataSource = getDataSource(request, "conPool");
              
            if (DBUtil.isServerBusy(dataSource, request)) {
                request.setAttribute("message", "Server Busy");
                return mapping.findForward("message");           
            }
      
            // Use the first connection to get all of the specimen codes
            connection = DBUtil.getConnection(dataSource, "SpecimenListAction.generateAllAntwebAnts()");
            ArrayList<String> codes = new SpecimenDb(connection).getAntwebSpecimenCodes(ProjectMgr.getProject(Project.ALLANTWEBANTS), Family.ANT_FAMILY);

            int specimenCount = 0;
            for (String code : codes) {
                ++specimenCount;

                Specimen specimen = Specimen.getShallowInstance(code, connection);
                dataBuffer.append(specimen.getData(connection) + "\n");

                if (specimenCount % 10000 == 0) {
                    s_log.warn("generateAllAntwebAnts() specimenCount:" + specimenCount + " writing to:" + fullPath);
                    LogMgr.appendFile(fullPath, dataBuffer.toString());
                    dataBuffer = new StringBuffer();                
                }
                if (specimenCount % 100000 == 0) {
                    //connection.close();     
                    DBUtil.close(connection, this, "SpecimenListAction.generateAllAntwebAnts()");                
                    connection = DBUtil.getConnection(dataSource, "SpecimenListAction.generateAllAntwebAnts()");

                    s_log.warn("generateAllAntwebAnts() count:" + specimenCount + " new connection!");
                }            
            }
            LogMgr.appendFile(fullPath, dataBuffer.toString());

        } catch (SQLException e) {
            String message = e.toString();
            s_log.error("execute() e:" + message);
            request.setAttribute("message", message);
            return mapping.findForward("message");              
        } finally {
            //QueryProfiler.profile("specimenList", startTime);            
            DBUtil.close(connection, this, "SpecimenListAction.generateAllAntwebAnts()");
        }

        // A.log("execute() writing:" + fullPath);

        String url = AntwebProps.getDomainApp() + dir + fileName;
        String message = "<li>&middot; <a href=\"" + url + "\" target=\"new\">Tab-delimited data</a></li>";
        //s_log.info("execute() taxon:" + taxonName + " project:" + projectName + " specimenCount:" + specimenCount + " rightClickSave:" + rightClickSave);
        request.setAttribute("message", message);
        return mapping.findForward("message");
    }
    
}
