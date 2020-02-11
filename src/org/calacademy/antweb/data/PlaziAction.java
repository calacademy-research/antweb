package org.calacademy.antweb.data;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.OutputKeys;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.w3c.dom.Document;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ResourceBundle;
import java.util.Set;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public final class PlaziAction extends Action {
/*
 Deprecated means of grabbing plazi data.
*/

    private static Log s_log = LogFactory.getLog(PlaziAction.class);
    private static final Log s_antwebEventLog = LogFactory.getLog("antwebEventLog");
 
	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

    /*
        Called as such:
          /getPlazi.do?test1
          /getPlazi.do?test2     // To process 
          /getPlazi.do?fetch
          /getPlazi.do?update
    */

		// Extract attributes we will need
		HttpSession session = request.getSession();
		
		boolean isTest = false;
		boolean fetchStubUpdate = false;
		boolean fetchSubsetUpdate = false;
		boolean isFetch = false;
        boolean isUpdate = false;
 		if (request.getQueryString() != null) {
          String queryString = request.getQueryString();
		  if (queryString.contains("test")) isTest = true;
		  if (queryString.contains("fetchStubUpdate")) fetchStubUpdate = true;
		  if (queryString.contains("fetchSubsetUpdate")) fetchSubsetUpdate = true;
          if (queryString.equals("fetch")) isFetch = true;
          if (queryString.contains("update")) isUpdate = true;          
        } else {
          isUpdate = true;
          s_log.warn("execute().  No params, so update.");
		}
		
		boolean success = true;

        String message = "done";

		java.sql.Connection connection = null;
		try {
			//javax.sql.DataSource dataSource = getDataSource(request, "conPool");
			//connection = dataSource.getConnection();
			
            // That seems to work (on stage)... Not on dev.  Not using connection pool.
            java.lang.Class.forName("com.mysql.jdbc.Driver").newInstance();
            String jdbc = "jdbc:mysql://127.0.0.1:3306/ant?autoReconnect=true&user=antweb&password=f0rm1c6";
            String jdbcutf8 = "&useUnicode=true&characterEncoding=UTF-8";
            connection = java.sql.DriverManager.getConnection(jdbc+jdbcutf8);              
			
            HashMap<String, ArrayList<PlaziTaxonDescription>> parsedTaxa = null;
			Plazi plazi = new Plazi();

            if (isTest)
                s_log.warn("execute() test");
                message = test(connection, plazi);

            if (fetchStubUpdate) {
                s_log.warn("execute() fetchStubUpdate");
                parsedTaxa = fetchStub(connection, plazi);
                if (parsedTaxa == null) { 
                  message = "Failed to fetchStub";
                } else {
                  message = update(connection, plazi, parsedTaxa);   
                }
            }

            if (fetchSubsetUpdate) {
                s_log.warn("execute() fetchSubsetUpdate");
                parsedTaxa = fetchSubset(connection, plazi, 1000);
                if (parsedTaxa == null) { 
                  message = "Failed to fetchSubset";
                } else {
                 message = update(connection, plazi, parsedTaxa);   
               }
             }

            if (isFetch) {
                s_log.warn("execute() fetch");
                parsedTaxa = fetch(connection, plazi);
                if (parsedTaxa == null) { 
                  message = "Failed to fetch";
                } else {
                 message = update(connection, plazi, parsedTaxa);   
               }
               s_log.warn("fetching parsedTaxa:" + parsedTaxa);
            }
            
            if (isUpdate) {
                s_log.warn("execute() update");
                parsedTaxa = fetch(connection, plazi);
                if (parsedTaxa == null) { 
                  message = "Failed to fetch";
                } else {
                 message = update(connection, plazi, parsedTaxa);   
               }
            }                          
		} catch (Exception e) {
			message = "e:" + e;
			s_log.error("execute() " + message);
			org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
			success = false;
		} finally {
			try {
				connection.close();
			} catch (Exception e) {
				s_log.error("execute() Connection.close e:" + e);
			}
		}

        request.setAttribute("message", message);
        return (mapping.findForward("message"));             
    }

    private String test(Connection connection, Plazi plazi) throws SQLException {
			
          // For testing a single record insertion to get character set issue resolved.  

            plazi.save(connection); 
            String message = "Plazi test record submitted.  See taxonomictreatment here: " 
              + " <a href=\"" + AntwebProps.getDomainApp() + "/description.do?genus=tetraponera&name=rufonigra&rank=species\">Tegraponera rufonigra</a>";
		
			s_log.warn("test() - Test complete");			

            return message;     

	}

    private HashMap<String, ArrayList<PlaziTaxonDescription>> fetchStub(Connection connection, Plazi plazi) 
        throws IOException {

            String fileName = "Eupolybothrus_Eupolybothrus_gloriastygis_tx.xml";

			HashMap<String, ArrayList<PlaziTaxonDescription>> parsedTaxa = new HashMap<String, ArrayList<PlaziTaxonDescription>>(); 
			HashMap<String, ArrayList<PlaziTaxonDescription>> newTaxa = null; 
        
            fetchFile(plazi, fileName, parsedTaxa, newTaxa);

			s_log.warn("fetchStub() - complete fileName:" + fileName);			
            return parsedTaxa;
	}

    private HashMap<String, ArrayList<PlaziTaxonDescription>> fetchSubset(Connection connection, Plazi plazi, int limit) 
        throws IOException {

			// first loop through the plazi files and get the taxon descriptions
			// stubbed out while testing			
			ArrayList<String> fileList = plazi.getFileList(limit);
			if (fileList == null) return null;
			
			HashMap<String, ArrayList<PlaziTaxonDescription>> parsedTaxa = new HashMap<String, ArrayList<PlaziTaxonDescription>>(); 
			HashMap<String, ArrayList<PlaziTaxonDescription>> newTaxa = null; 

            s_log.warn("fetchSubset() Plazi fileList size:" + fileList.size());

			int count = 0;
			for (String fileName : fileList) {

                fetchFile(plazi, fileName, parsedTaxa, newTaxa);  // no limit
                
                // s_log.info("Plazi filename (" + count + "):" + fileName);
				++count;               // not ++count; ??
				if (count > limit) break;
			}

			s_log.warn("fetchSubset() - complete count:" + count);			
            return parsedTaxa;
	}
	
    private HashMap<String, ArrayList<PlaziTaxonDescription>> fetch(Connection connection, Plazi plazi) 
         throws IOException {

			// first loop through the plazi files and get the taxon descriptions
			// stubbed out while testing			
			Plazi.s_state = Plazi.FETCH;
			ArrayList<String> fileList = plazi.getFileList(-1);
			if (fileList == null) return null;

			HashMap<String, ArrayList<PlaziTaxonDescription>> parsedTaxa = new HashMap<String, ArrayList<PlaziTaxonDescription>>(); 
			HashMap<String, ArrayList<PlaziTaxonDescription>> newTaxa = null; 

            s_log.warn("fetch() Plazi fileList size:" + fileList.size());

			int count = 0;
			for (String fileName : fileList) {

                fetchFile(plazi, fileName, parsedTaxa, newTaxa);  // no limit
                
                // s_log.info("Plazi filename (" + count + "):" + fileName);
				++count;               // not ++count; ??
			}

            Plazi.s_state = Plazi.IDLE;
			s_log.warn("fetch() - complete count:" + count);			
            return parsedTaxa;
	}
	
	private void fetchFile(Plazi plazi, String fileName, HashMap<String, ArrayList<PlaziTaxonDescription>> parsedTaxa
	    , HashMap<String, ArrayList<PlaziTaxonDescription>> newTaxa) 
	    throws IOException {
				newTaxa = plazi.getDescribedTaxa(fileName);
				if (newTaxa != null) {
					Set<String> keys = newTaxa.keySet();
					for (String taxon : keys) {
						if (!(parsedTaxa.containsKey(taxon))) {
							parsedTaxa.put(taxon, new ArrayList<PlaziTaxonDescription>());	
						}
						ArrayList al = (ArrayList) parsedTaxa.get(taxon);;
                        if (al != null) al.addAll(newTaxa.get(taxon));
					}
				}
	
	}

    private String update(Connection connection, Plazi plazi, HashMap<String, ArrayList<PlaziTaxonDescription>> parsedTaxa) 
      throws SQLException {

            s_antwebEventLog.info("Plazi");
            Plazi.s_state = Plazi.UPDATE;

			ArrayList<String> saved = plazi.save(connection, parsedTaxa);
			int savedPlaziCount = 0;
			for (String taxon: saved) {
				A.log("updated plazi info for " + taxon);
                ++savedPlaziCount;
			}
			s_log.warn("update() - Saved Plazi Count:" + savedPlaziCount);			

            String message = "Plazi Save operation completed.";
            Plazi.s_state = Plazi.IDLE;
            return message;
    }
	
}
