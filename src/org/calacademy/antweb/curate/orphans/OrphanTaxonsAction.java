package org.calacademy.antweb.curate.orphans;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
import java.util.*;
import java.util.Date;
import java.sql.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.TaxonDb;
import org.calacademy.antweb.util.*;

public final class OrphanTaxonsAction extends Action {

    private static final Log s_log = LogFactory.getLog(OrphanTaxonsAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        // Extract attributes we will need
        HttpSession session = request.getSession();
        Connection connection = null;

        ArrayList<Taxon> taxonList = new ArrayList<>();
        ArrayList<String> uploadList = new ArrayList<>();
        Statement stmt1 = null;  
        ResultSet rset1 = null;    
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "OrphanTaxonsAction");

            //Take care of the deletion first.
            if (form instanceof OrphanTaxonsForm) {
              OrphanTaxonsForm theForm = (OrphanTaxonsForm) form;
              String action = theForm.getAction();
              String taxonName = theForm.getTaxonName();
              String source = theForm.getSource();
              if (action != null) {       
                if (action.equals("delete")) {
                   if (taxonName != null) {
                     deleteTaxon(connection, taxonName);
                   } else {
                     deleteTaxonsFromSource(connection, source);
                   }
                }
              }
            }
            
            stmt1 = connection.createStatement();
            String query = "select max(created), source, count(*) from taxon group by source";
            
            rset1 = stmt1.executeQuery(query);
            while (rset1.next()) {
                Date max = rset1.getTimestamp(1);
                String source = rset1.getString(2);
                String count = rset1.getString(3);

                if (source.contains("biota")) {
                  //s_log.warn("Orhan() 1 max:" + max + " source:" + source);
                  uploadList.add("source:" + source + " lastUpload:" + max + " taxonCount:" + count + "");

                  Statement stmt2 = connection.createStatement();
                  query = "select taxon_name from taxon where source = '" + source + "' and created < date_sub('" + max + "', INTERVAL 1 DAY)";
                  ResultSet rset2 = stmt2.executeQuery(query);
 
                  //s_log.warn("orphan() q:" + query);
                  while (rset2.next()) {
                    String taxonName = rset2.getString(1);
                    Taxon taxon = new TaxonDb(connection).getTaxon(taxonName);
                    taxonList.add(taxon);
                  }
                  stmt2.close();
                  rset2.close();
                }
            }
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return mapping.findForward("error");
        } finally { 		
            DBUtil.close(connection, stmt1, rset1, this, "OrphanTaxonsAction");
        }

        request.setAttribute("uploads", uploadList);
        request.setAttribute("orphans", taxonList);

        return mapping.findForward("success");
    }

    
    private void deleteTaxon(Connection connection, String taxonName) 
        throws SQLException {
        s_log.warn("deleteTaxon() taxon:" + taxonName);
        Statement stmt1 = connection.createStatement();
        String delete = "delete from taxon where taxon_name = \"" + taxonName + "\""; 
        stmt1.executeUpdate(delete);    
    }

    private void deleteTaxonsFromSource(Connection connection, String source) 
        throws SQLException {
        s_log.warn("deleteTaxonFromSource() source:" + source);
        
            Statement stmt1 = connection.createStatement();
            String query = "select max(created) from taxon where source = \"" + source + "\" group by source";
            
            ResultSet rset1 = stmt1.executeQuery(query);
            while (rset1.next()) {
              Date max = rset1.getTimestamp(1);

              Statement stmt2 = connection.createStatement();
              query = "select taxon_name from taxon where source = \"" + source + "\" and created < date_sub('" + max + "', INTERVAL 1 DAY)";
              ResultSet rset2 = stmt2.executeQuery(query);
 
              //s_log.warn("orphan() q:" + query);
              while (rset2.next()) {
                String taxonName = rset2.getString(1);
                Statement stmt3 = connection.createStatement();
                String delete = "delete from taxon where taxon_name = \"" + taxonName + "\""; 
                stmt3.executeUpdate(delete);   
                stmt3.close();
              }
              stmt2.close();
            }     
            stmt1.close();
    }    
}
