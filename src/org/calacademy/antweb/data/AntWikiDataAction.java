package org.calacademy.antweb.data;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.regexp.*;

import org.apache.struts.action.*;

import java.sql.*;
import java.util.ArrayList;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public final class AntWikiDataAction extends Action {

    private static Log s_log = LogFactory.getLog(AntWikiDataAction.class);

    private static final Log s_antwebEventLog = LogFactory.getLog("antwebEventLog");
 
	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {
		
		String message = "";
    /*
        This is a test method.  Will just display.
        
        Called as such:
          /antWikiData.do?action=checkForUpdates

        Database loads actually happen as this:
          /util.do?action=countryData
          /util.do?action=taxonCountryData          
    */

        DynaActionForm df = (DynaActionForm) form;
        String action = (String) df.get("action");

        if ("checkForUpdates".equals(action)) {
        
			Connection connection = null;
			try {
				DataSource dataSource = getDataSource(request, "conPool");
				connection = DBUtil.getConnection(dataSource, "EditGeolocaleAction.execute()");

				message = AntWikiDataAction.checkForUpdates(connection);

			} catch (SQLException | ClassCastException e) {
				s_log.error("execute() e:" + e);
			} finally {
			    DBUtil.close(connection, this, "EditGeolocaleAction.execute()");
			}        
		
        } else {
          // Used for testing.
          /*
			// Extract attributes we will need
			HttpSession session = request.getSession();
	 
			//String countryPage = getCountryPage();
			//String message = countryPage;
		
			ArrayList<String> taxonCountryPage = getTaxonCountryPage();
			processPage(taxonCountryPage, null);
		
			message = taxonCountryPage.toString();
			*/

        }
        request.setAttribute("message", message);
        return mapping.findForward("message");
    }
    
    private void processPage(ArrayList<String> taxonCountryPage, Connection connection) {

        String[] columns = null;
        for (String line : taxonCountryPage) {
             columns = line.split("\t");             
             
             String country = null;
             String introduced = null;
             
             int i = 0;
             AntWikiData antWikiData = new AntWikiData(line); //null; //columns[i];
             //String shortTaxonName = antwikiData.getShortTaxonName();
             country = antWikiData.getCountry();	 //null; //columns[i];

             s_log.debug("parseTaxonCountryPage()  country:" + country + " introduced:" + introduced);

             //s_log.warn("parseTaxonCountryPage() shortTaxonName:" + shortTaxonName + " country:" + country + " introduced:" + introduced);

             if (connection != null) {
               //storeTaxonCountry(connection, taxonName, country, isIntroduced);
             }             
        }
    }
    
// ---------------------------------------------------------------------------------------     

    /*
       Primary entry point.  This will load from a file into Antwiki_taxon_country.
       On curate page, Upload Data File.  Submit.
       Upload of a file with title containing "Regional_Taxon_List" in the fileName.  
       This file comes from Antwiki.  Added to source tree, see: /web/data/
       WARNING!  Must open the file in Textwrangler, copy all, past in new file and save.
       This file is binary and will not work unless massaged as such.
       
       After data is loaded into the antwiki_taxon_country table, then...
    */
    public String loadRegionalTaxonList(BufferedReader in, Connection connection) 
      throws IOException, SQLException {
        String messageStr = "";
      
        AntwikiTaxonCountryDb antwikiTaxonCountryDb = new AntwikiTaxonCountryDb(connection);
      
        //A.log("AntwikiDataAction.loadRegionalTaxonList()");
      
        antwikiTaxonCountryDb.emptyTaxonCountry();
        
        String message = load(in, connection, antwikiTaxonCountryDb);
        messageStr += message;

        return messageStr;
    }

    private String load(BufferedReader in, Connection connection, AntwikiTaxonCountryDb antwikiTaxonCountryDb) 
      throws IOException, SQLException {        
        
        String messageStr = null;
        String theLine = "";
        int insertCount = 0;
        int notValidTaxonCount = 0;
        int notFoundCount = 0;
        try {
            RE tab = new RE("\t");
            
            String[] components;
            StringBuffer content = new StringBuffer();

            int lineNum = 1;
            TaxonDb taxonDb = new TaxonDb(connection);
            
            while (theLine != null) {            
              theLine = in.readLine();
              if (theLine != null) theLine = theLine.trim();

              if (theLine == null || "".equals(theLine) || " ".equals(theLine)) {
                //s_log.warn("loadRegionalTaxonList() 1 theLine:" + theLine + ":");
                continue;
              }         
              ++lineNum;
             
             AntWikiData antWikiData = new AntWikiData(theLine);
             String shortTaxonName = antWikiData.getShortTaxonName();
             String genus = antWikiData.getGenus();
             String species = antWikiData.getSpecies();
             String subspecies = antWikiData.getSubspecies();
             String country = antWikiData.getCountry();
             String introduced = null;
             String region = null;
             boolean isIntroduced = false;
             String source = null;

              //A.log("loadRegionalTaxonList() shortTaxonName:" + shortTaxonName + " genus:" + genus + " country:" + country);

              Taxon taxon = getTaxon(connection, genus, species, subspecies);
              if (taxon == null) {

                if ("(indet)".equals(species)) {
                   s_log.warn("load() genus specified but (indet) record does not exist.  Create? taxonName:" + antWikiData.getShortTaxonName());
                  content.append("line:" + lineNum + ": Species record not found for shortTaxonName:" + antWikiData.getShortTaxonName() + " (indet)<br>\n");
                   // If genus exists, create genus (indet) record.
                } else {
                  s_log.warn("load() not found... shortTaxonName:" + antWikiData.getShortTaxonName() + " genus:" + genus + " species:" + species + " subspecies:" + subspecies);
                  content.append("Taxon not found.  line:" + lineNum + " taxonName:" + antWikiData.getShortTaxonName() + "<br>\n");
                }                  
                ++notFoundCount;
                s_log.debug("loadRegionalTaxonList() not found shortTaxonName:" + antWikiData.getTaxonName());
                continue;
              }
             
              
              String taxonName = taxon.getTaxonName();

              //if (AntwebProps.isDevOrStageMode()) s_log.warn("load() taxonName:" + taxonName + " status:" + taxon.getStatus());

              if (taxonName.equals("myrmicinaetrichomyrmex destructor")) s_log.debug("load() taxonName:" + taxonName + " country:" + country); // + " isIntroduced:" + isIntroduced + " source:" + source);

              if (taxon.getStatus().equals(Status.VALID) || !Status.usesCurrentValidName(taxon.getStatus())) { //   taxon.getStatus().equals(Status.UNAVAILABLE)) {
                  insertCount += antwikiTaxonCountryDb.storeTaxonCountry(antWikiData.getShortTaxonName(), null, taxon.getTaxonName(), country, region, isIntroduced, source);
              } else {
                  if (Status.usesCurrentValidName(taxon.getStatus())) {
                    // has currentValidName?
                    //if (AntwebProps.isDevOrStageMode()) s_log.warn("load() notValid:" + taxonName + " currentValidName:" + taxon.getCurrentValidName());
                    String currentValidName = taxon.getCurrentValidName();
                    String prettyCurrentValidName = Taxon.getPrettyTaxonName(currentValidName);
                    
                    insertCount += antwikiTaxonCountryDb.storeTaxonCountry(antWikiData.getShortTaxonName(), taxon.getTaxonName(), currentValidName, country, region, isIntroduced, source);
        
                    ++notValidTaxonCount;
                    content.append("line:" + lineNum + " <a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.getPrettyTaxonName(taxonName) + "</a> is of status:" + taxon.getStatus() + ".  Using: <a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + currentValidName + "'>" + prettyCurrentValidName + "</a><br>\n");
                  } else {
                    content.append("line:" + lineNum + ": <a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + taxonName + "</a> not valid and does not use current valid name.<br>\n");
				  }
              }

            } // end while loop through lines

            //s_log.warn("testFileValid() validTaxonCount:" + validTaxonCount + " notValidTaxonCount:" + notValidTaxonCount);
            messageStr = "<h3>Valid Test</h3><br><br>" 
              + "validTaxonCount:" + insertCount + " inserted into antwiki_taxon_country.  NotValidTaxonCount:" + notValidTaxonCount
              + " notFoundCount:" + notFoundCount + "\n"
              + "<br><br>" + content.toString();

        } catch (RESyntaxException e) {
          s_log.warn("load() e:" + e);
        }
        return messageStr;
    }
    
    private ArrayList<String> getTaxonCountryPage() {
        ArrayList<String> pageLines = null;
        try {
            String url = "http://www.antwiki.org/wiki/images/0/0c/AntWiki_Regional_Taxon_List.txt";
            pageLines = HttpUtil.getUtf8UrlLines(url);

            if (pageLines == null) return null;
        } catch (IOException e) {
            s_log.error("getTaxonCountryPage() e:" + e);
        }
        return pageLines;
    }


    // Could be faster by being a single query, instead of just getting the taxonName here.
    private Taxon getTaxon(Connection connection, String genus, String species, String subspecies) throws SQLException {
        String taxonName = null;

        Statement stmt = null;
        ResultSet rset = null;
        String query = "select taxon_name from taxon where genus = '" + genus + "'";
        if (species != null && !"".equals(species)) {
            query += " and species = '" + species + "'";
        } else {
            query += " and species is null";
        }
        if (subspecies != null && !"".equals(subspecies)) {
            query += " and subspecies = '" + subspecies + "'";
        } else {
            query += " and subspecies is null";
        }
        try {
            stmt = DBUtil.getStatement(connection, "getTaxon()");
            rset = stmt.executeQuery(query);
            int count = 0;
            while (rset.next()) {
                ++count;
                taxonName = rset.getString("taxon_name");
            }
            //A.log("getTaxon(3) query:" + query + " count:" + count);

        } catch (SQLException e) {
            s_log.error("getTaxon() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getTaxon()");
        }

        //A.log("getTaxon() taxonName:" + taxonName + " query:" + query);

        return new TaxonDb(connection).getTaxon(taxonName);
    }

// ---------------------------------------------------------------------------------------

    // Primary access method.  Called like: http://localhost/antweb/util.do?action=countryData
// We don't do it this way now. Upload a massaged file.
/*
    public String fetchCountryData(Connection connection) throws SQLException {
    // Entry point for /util.do?action=countryData

        String countryPage = getCountryPage();
        
        if (countryPage == null) {
          s_log.warn("fetchCountryData() countryPage is null");
          return "Failed to fetch data";
        }
        
        storeCountryData(countryPage, connection);

        return null;
    }

    private String getCountryPage() {     
        String countryPage = null;
        try {
            String url = "http://www.antwiki.org/wiki/index.php?title=Countries_by_Regions&action=edit";
            countryPage = HttpUtil.getUtf8Url(url);

            if (countryPage == null) {
              return null;
            } 
        } catch (IOException e) {
            s_log.error("getCountryPage() e:" + e);
        }
        return countryPage;        
    }
    
    private void storeCountryData(String countryPage, Connection connection) {
        int indexOfCountryData = countryPage.indexOf("wikitable sortable");
                
        if (indexOfCountryData > 0) {

            int endIndex = countryPage.indexOf("}", indexOfCountryData);
            int indexOfCountry = countryPage.indexOf("[[", indexOfCountryData) + 2;

            while (indexOfCountry < endIndex) {
                int endIndexOfCountry = countryPage.indexOf("]]", indexOfCountry);
                    
                String country = countryPage.substring(indexOfCountry, endIndexOfCountry);                    

                int indexOfRegion = countryPage.indexOf("|", endIndexOfCountry) + 1;
                int endIndexOfRegion = countryPage.indexOf("|", indexOfRegion);
                String unRegion = countryPage.substring(indexOfRegion, endIndexOfRegion); 
                unRegion = unRegion.trim();

                int indexOfSubregion = countryPage.indexOf("|", endIndexOfRegion) + 1;
                int endIndexOfSubregion = countryPage.indexOf("|", indexOfSubregion);
                String unSubregion = countryPage.substring(indexOfSubregion, endIndexOfSubregion); 
                unSubregion = unSubregion.trim();

                int indexOfBioregion = countryPage.indexOf("|", endIndexOfSubregion) + 1;
                int endIndexOfBioregion = countryPage.indexOf("|", indexOfBioregion);
                String bioregion = countryPage.substring(indexOfBioregion, endIndexOfBioregion); 


                String isAllAscii = Utility.isAllASCII(country);
                if ("false".equals(isAllAscii)) { 
                  A.log("storeCountryData() isAllAscii:" + isAllAscii + " country:" + country + " unRegion:" + unRegion + " unSubregion:" + unSubregion + " bioregion:" + bioregion);                
                }
                                  
                storeCountry(connection, country, unRegion, unSubregion, bioregion);  

                indexOfCountry = countryPage.indexOf("[[", indexOfCountry + 4) + 2;
                if (indexOfCountry <= 0) break;
            }        
        }
    }     
    
    private void storeCountry(Connection connection, String country, String unRegion, String unSubregion, String bioregion) {
        String descriptionTable = "description_edit";

        String sql = null;
        Statement stmt = null;
        country = AntFormatter.escapeQuotes(country);

        try {
          sql = "delete from un_country where name = '" + country + "'";

          stmt = connection.createStatement();
          stmt.executeUpdate(sql);

          sql = "insert into un_country (name, un_region, un_subregion, bioregion) VALUES ('" + country + "','" + unRegion + "','" + unSubregion + "','" + bioregion + "')";
          // A.log("storeCountry() country:" + country + " unRegion:" + unRegion);
          A.log("storeCountry() sql:" + sql);

          stmt = connection.createStatement();
          stmt.executeUpdate(sql);

        } catch (SQLException e) {
            s_log.error("storeCountry() e:" + e);
        } finally {
          try {
            if (stmt != null) stmt.close();
          } catch (SQLException e) {
            s_log.error("storeCountry() e:" + e);
          }
            //DBUtil.close(stmt, "this", "getAntwebSpecimenCodes()");
        }
    }
*/

// ---------- Antwiki Species Update -------------
    
    // This is checked by the scheduler. Notify Brian if new Species found.

    public static String checkForUpdates(Connection connection) {
      String message = "";
      message += AntWikiDataAction.checkForValidSpeciesListUpdate(connection);
      message += ".  " + AntWikiDataAction.checkForFossilSpeciesListUpdate(connection);  
      return message;    
    }

    public static String checkForValidSpeciesListUpdate(Connection connection) {
      String upDate = AntWikiDataAction.getValidSpeciesListUpDate();

      // Check in database. Is this the last update date?
      AntwebDb antwebDb = new AntwebDb(connection);
      boolean isCurrent = antwebDb.isCurrentInLookup(connection, "validSpeciesListUpDate", upDate);

      //boolean isCurrent = antwebDb.isCurrentInLookup(connection, "validSpeciesListUpDate", upDate);
      // Update lookup table. set upDate to validSpeciesUpDate      
      
      s_log.debug("isCurrent:" + isCurrent + " upDate:" + upDate);
       
      String prefix = "";
      if (!isCurrent) {
        // create an Admin message.  
        prefix = "Updated!  ";    
        s_log.debug("checkForValidSpeciesListUpdate() upDate:" + upDate);
        AdminAlertMgr.add("Antwiki Valid Species List Upload:" + upDate, connection);
      }
          
      return prefix + "Last Valid Species List update:" + upDate;
    }
        
    private static String getValidSpeciesListUpDate() {
       // String url = "http://antwiki.org/wiki/Species_Accounts";
        String url = "https://antwiki.org/wiki/Species_Accounts";
        String output = "";
        try {
          output = HttpUtil.getUrl(url);
        } catch (IOException e) {
          s_log.warn("getValidSpeciesListUpDate() e:" + e);
        }

        int i = output.indexOf("List of valid species (names in use)");
        String validSpeciesListUpDate = output.substring(i + 69, i + 69 + 10);
        s_log.debug("AntWikiDataAction.getValidSpeciesListUpDate() i:" + i + " validSpeciesUpDate:" + validSpeciesListUpDate);
        return validSpeciesListUpDate;
    }

    public static String checkForFossilSpeciesListUpdate(Connection connection) {

      String upDate = AntWikiDataAction.getFossilSpeciesListUpDate();
      
      //upDate = "test";
      
      // Check in database. Is this the last update date?
      AntwebDb antwebDb = new AntwebDb(connection);
      boolean isCurrent = antwebDb.isCurrentInLookup(connection, "fossilSpeciesListUpDate", upDate);
      
      String prefix = "";
      if (!isCurrent) {
        prefix = "Updated!  ";
        // create an Admin message.      
        s_log.debug("checkForFossilSpeciesListUpdate() upDate:" + upDate);
        AdminAlertMgr.add("Antwiki Fossil Species List Upload:" + upDate, connection);
      }
          
      return prefix + "Last Fossil Species List update:" + upDate;
    }
        
    private static String getFossilSpeciesListUpDate() {
      String url = "https://antwiki.org/wiki/Species_Accounts";
      String output = "";
      try {
        output = HttpUtil.getUrl(url);
      } catch (IOException e) {
        s_log.warn("getFossilSpeciesListUpDate() e:" + e);
      }
      int i = output.indexOf("List of valid fossil species (names in use)</a> (tab-delimited text) (Date: ");
      String fossilSpeciesListUpDate = output.substring(i + 76, i + 76 + 10);
      s_log.debug("AntWikiDataAction.getFossilSpeciesListUpDate() validSpeciesUpDate:" + fossilSpeciesListUpDate);
 
      return fossilSpeciesListUpDate;
    }    
}
