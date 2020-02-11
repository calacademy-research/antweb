package org.calacademy.antweb.data;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.w3c.dom.Document;

import javax.sql.DataSource;

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

public final class PlaziDataAction extends Action {

    private static Log s_log = LogFactory.getLog(PlaziDataAction.class);
    private static final Log s_antwebEventLog = LogFactory.getLog("antwebEventLog");
 
	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {
    /*
        This is a test method.  Will just display.
        
        Called as such:
          /getPlaziData.do?
          /getPlaziData.do?description.do    // to see the css formatting
    */
    
		// Extract attributes we will need
		HttpSession session = request.getSession();
     
        String message = getTreatment(181978);
        request.setAttribute("message", message);
        return (mapping.findForward("message"));             
    }
     
/*     
     // For links like this we want to grab the treatment citation.
     // http://plazi.cs.umb.edu/GgServer/search?resultFormat=html&taxonomicName.isNomenclature=taxonomicName.isNomenclature&taxonomicName.exactMatch=taxonomicName.exactMatch&taxonomicName.LSID=urn:lsid:biosci.ohio-state.edu:osuc_concepts:25516

        String url = "http://plazi.cs.umb.edu/GgServer/search?resultFormat=html&taxonomicName.isNomenclature=taxonomicName.isNomenclature&taxonomicName.exactMatch=taxonomicName.exactMatch&taxonomicName.LSID=urn:lsid:biosci.ohio-state.edu:osuc_concepts:";
        int holId = 25516;
        url += holId;
        //s_log.warn("doGet() pathInfo:" + pathInfo + " url:" + url);
        String treatmentPage = HttpUtil.getUrl(url);
        s_log.warn(treatmentPage);

//        String message = treatmentPage;
        
        String message = "<p class=\"documentText\">" 
+ " 15<a title=\"Search 'Aphaenogaster swammerdami'\" href=\"/GgServer/search?taxonomicName.isNomenclature=true&taxonomicName.exactMatch=true&taxonomicName.taxonomicName=Aphaenogaster+swammerdami\"> Aphaenogaster swammerdami</a><span class=\"externalLinkInLine\"><a title=\"Lookup Aphaenogaster swammerdami at Hymenoptera Name Server\" target=\"_blank\" href=\"http://osuc.biosci.ohio-state.edu/hymenoptera/nomenclator.lsid_entry?lsid=urn:lsid:biosci.ohio-state.edu:osuc_concepts:25516\">HNS</a></span> Forel</p>"
+ "<p class=\"documentText\"> "
+ "  ☿, "
+ " l'&icirc;le de Nossi-b&eacute; pr&egrave;s de Madagascar, "
+ "  Dr. Keller</p>';";
        
        request.setAttribute("message", message);
        return (mapping.findForward("message"));             
    }
*/    

    /*
     Primary access method.  Called like: http://localhost/antweb/util.do?action=plaziData
    */
    public void fetchPlaziData(Connection connection) throws SQLException {
        fetchPlaziData(connection, "homonym");
        fetchPlaziData(connection, "taxon");
    }
    public void fetchPlaziData(DataSource dataSource) throws SQLException {
        fetchPlaziData(dataSource, "homonym");
        fetchPlaziData(dataSource, "taxon");
    }

    private void fetchPlaziData(DataSource dataSource, String table) throws SQLException {
        Connection connection = dataSource.getConnection();
        fetchPlaziData(connection, table);
    }
    
    private void fetchPlaziData(Connection connection, String table) throws SQLException {
        // s_log.warn("fetchPlaziData() table:" + table);
    
        ArrayList<TaxonNameDateHol> taxonNameDateHols = getTaxonNameDateHol(connection, table);
        
        int holCount = 1;
        int holId = 0;
        //String taxonName = null;
        String treatment = null;
        int treatmentCount = 0;
        String taxonName = null;
        String authorDate = null;
 
     //    for (String taxonName : taxonNameHolIdMap.keySet()) {
        for (TaxonNameDateHol taxonNameDateHol : taxonNameDateHols) {

            taxonName = taxonNameDateHol.taxonName;
            authorDate = taxonNameDateHol.authorDate;
            holId = taxonNameDateHol.holId;
            //holId = taxonNameHolIdMap.get(taxonName).intValue();
            //s_log.info("Key = " + entry.getKey() + ", Value = " + entry.getValue());
            
            if (holId == 238202) {
              // causes e:java.io.IOException: Server returned HTTP response code: 500
              continue;
            }
            
            treatment = getTreatment(holId);
            if (treatment != null) {
              ++treatmentCount;
              storeTreatment(connection, table, taxonName, authorDate, treatment);

              if (181978 == holId || (treatmentCount % 100) == 0) {
                s_log.warn("fetchPlaziData() treatmentCount:" + treatmentCount + " holCount:" + holCount + " holId:" + holId + " taxonName:" + taxonName); 
              }
            }
            
            ++holCount;

            if ((holCount % 2000) == 0) {
                s_log.warn("fetchPlaziData() refresh connection.  holCount:" + holCount); 
            }              
              //  " indexOf:" + indexOf); // " length:" + treatmentPage.length());
        }  
        //connection.close();
    }

    private ArrayList<TaxonNameDateHol> getTaxonNameDateHol(Connection connection, String table) {
        ArrayList<TaxonNameDateHol> taxonNameDateHols = new ArrayList<TaxonNameDateHol>();
        
        String query = "select taxon_name";
        if ("homonym".equals(table)) query += ", author_date";
        query += " ,hol_id from " + table;

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "getTaxonNameHolIdMap()");
            rset = stmt.executeQuery(query);

            int recordCount = 1;
            int holId = 0;
            String taxonName = null;
            String treatment = null;
            int treatmentCount = 0;
            TaxonNameDateHol taxonNameDateHol = null;

            while (rset.next()) {
              holId = rset.getInt("hol_id");
              if (holId == 0) continue;

              taxonNameDateHol = new TaxonNameDateHol();

              taxonNameDateHol.taxonName = rset.getString("taxon_name");
              if ("homonym".equals(table)) taxonNameDateHol.authorDate = rset.getString("author_date");
              taxonNameDateHol.holId = holId;

              taxonNameDateHols.add(taxonNameDateHol);

              ++recordCount;

              //if (foundTreatment) break;

              //if (AntwebProps.isDevOrStageMode() && (recordCount % 100) == 0) 
              //  s_log.warn("getTaxonNameDateHol() " + recordCount + " holId:" + holId + " taxonName:" + taxonNameDateHol.taxonName); 
                //  " indexOf:" + indexOf); // " length:" + treatmentPage.length());
            }

        } catch (SQLException e) {
            s_log.error("getTaxonNameDateHol() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "this", "getTaxonNameDateHol()");
        }      
        return taxonNameDateHols;
    }

    private String getTreatment(int holId) {     
        String treatment = "";
        try {
            String url = "http://plazi.cs.umb.edu/GgServer/search?resultFormat=html&taxonomicName.isNomenclature=taxonomicName.isNomenclature&taxonomicName.exactMatch=taxonomicName.exactMatch&taxonomicName.LSID=urn:lsid:biosci.ohio-state.edu:osuc_concepts:";
            //String url = "http://plazi.cs.umb.edu/GgServer/search?resultFormat=xml&taxonomicName.isNomenclature=taxonomicName.isNomenclature&taxonomicName.exactMatch=taxonomicName.exactMatch&taxonomicName.LSID=urn:lsid:biosci.ohio-state.edu:osuc_concepts:";
            url += holId;
            String treatmentPage = HttpUtil.getUrl(url);

            if (treatmentPage == null) return null;
                if (treatmentPage.contains("No treatment yet on plazi")) return null;

                // One kind of page.  See: http://localhost/antweb/description.do?genus=pheidole&species=yucatana&rank=species&project=allantwebants
                int indexOfTreatment = treatmentPage.indexOf("Treatment</td>");
                if (indexOfTreatment > 0) {

                  // For these kind of pages, we want to get the citation as well as the treatment.
                  // Search for "treatment citation</td>" 
                  int indexOfCitation = treatmentPage.indexOf("treatment citation</td>");
                  indexOfCitation = treatmentPage.indexOf("<a href", indexOfCitation);
                  if (indexOfTreatment > 0) {
                    int endIndexOfCitation = treatmentPage.indexOf("</a>", indexOfCitation);
                    treatment = "<b>Treatment Citation:</b> " + treatmentPage.substring(indexOfCitation, endIndexOfCitation) + "<br><br>";
                  }

                  // Now we get the treatment itself                     
                  indexOfTreatment = treatmentPage.indexOf("<p class", indexOfTreatment);
                  if (indexOfTreatment > 0) {
                    int endIndexOf = treatmentPage.indexOf("</td>", indexOfTreatment);

                    treatment += treatmentPage.substring(indexOfTreatment, endIndexOf);

                    treatment = treatment.replaceAll("\"/GgServer/", "\"http://plazi.cs.umb.edu/GgServer/");
                    return treatment;
                  }
                }
                
                // An other kind of page. See: http://localhost/antweb/description.do?genus=pheidole&species=tenuinodis&rank=species&project=allantwebants
                int indexOf = treatmentPage.indexOf("<table class=\"documentResultTable\">");
                if (indexOf > 0) {
                  int endIndexOf = treatmentPage.indexOf("</table>", indexOf);

                  treatment = treatmentPage.substring(indexOf, endIndexOf + 8);

                  treatment = treatment.replaceAll("\"/GgServer/", "\"http://plazi.cs.umb.edu/GgServer/");

                  A.log("getTreatment() treatment:" + treatment);

                  return treatment;
                }

                ++notFoundCount;
                s_log.warn("getTreatment() treatment not found number:" + notFoundCount + " within page for url:" + url);

        } catch (IOException e) {
            s_log.error("getTreatment() e:" + e);
        }
        return null;
    }     
    
    private static int notFoundCount = 0;
    
    private void storeTreatment(Connection connection, String table, String taxonName, String authorDate, String treatment) {
        boolean isHomonym = ("homonym".equals(table));
        
        A.log("storeTreatment() taxonName:" + taxonName + " treatment:" + treatment);    
        String descriptionTable = "description_edit";
        if (isHomonym) descriptionTable = "description_homonym";

        String sql = null;
        Statement stmt = null;
        PreparedStatement prepStmt = null;  

        try {
          sql = "delete from " + descriptionTable + " where taxon_name = '" + taxonName + "'";
          if (isHomonym) sql += " and author_date = '" + authorDate + "'";
          sql += " and title = 'plaziData'";
          stmt = connection.createStatement();
          stmt.executeUpdate(sql);

          sql = "insert into " + descriptionTable + " (taxon_name, title, content, is_manual_entry";
          if (isHomonym) sql += ", author_date";
          sql += ") VALUES (?, ?, ?, false";
          if (isHomonym) sql += ", ?";          
          sql += ")";
          prepStmt = connection.prepareStatement(sql);
          prepStmt.setString(1, taxonName);
          prepStmt.setString(2, "plaziData");
          prepStmt.setString(3, treatment);
          if (isHomonym) prepStmt.setString(4, authorDate);
          prepStmt.executeUpdate();
        
        } catch (SQLException e) {
            s_log.error("storeTreatment() e:" + e);
        } finally {
          try {
            if (stmt != null) stmt.close();
            if (prepStmt != null) prepStmt.close();
          } catch (SQLException e) {
            s_log.error("storeTreatment() e:" + e);
          }
            //DBUtil.close(stmt, "this", "getAntwebSpecimenCodes()");
        }

    }
	
}


class TaxonNameDateHol {
String taxonName = null;
String authorDate = null;
int holId = 0;

/*
  public String toString {
    return "taxonName:" + taxonName + " authorDate:" + authorDate + " hol:" + holId;
  }
*/
}