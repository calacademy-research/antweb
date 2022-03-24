package org.calacademy.antweb.curate.orphans;

import java.io.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import java.util.*;
import java.sql.*;


public final class OrphanDescEditsAction extends Action {

    private static Log s_log = LogFactory.getLog(OrphanDescEditsAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ActionForward c = Check.login(request, mapping); if (c != null) return c;   
        
        ArrayList<Taxon> list = new ArrayList();

        Connection connection = null;
                
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "OrphanDescEditsAction.execute()");
            
            if (connection == null) s_log.error("execute() connection is null.");            

            OrphansDb orphansDb = new OrphansDb(connection);

            //Take care of the deletion first.
            if (form instanceof OrphanTaxonsForm) {
              OrphanTaxonsForm theForm = (OrphanTaxonsForm) form;
              String action = theForm.getAction();
              String taxonName = theForm.getTaxonName();
              if (false) s_log.warn("execute() action:" + action + " taxonName:" + taxonName 
                + " toTaxonName:" + theForm.getToTaxonName() 
                + " suggestedTaxonName:" + theForm.getSuggestedTaxonName()
                + " browse:" + theForm.getBrowse());
              if (theForm.getBrowse() != null && theForm.getBrowse().equals("browse")) {
                  String url = AntwebProps.getDomainApp() + "/browse.do";
                  if (theForm.getToTaxonName() != null && !"".equals(theForm.getToTaxonName())) {
                    url += "?taxonName=" + theForm.getToTaxonName();
                  } else {
                    url += "?subfamily=" + theForm.getSubfamily()
                      + "&name=" + theForm.getGenus() + "&rank=genus";
                  }
                  response.sendRedirect(url);
                  return null;                
              }
              if (action != null) {       
                if (action.equals("delete")) {
                   if (taxonName != null) {
                     deleteTaxonDescEdits(connection, taxonName);
                   }
                }
                if (action.equals("deleteTaxon")) {
                   if (taxonName != null) {
                     deleteTaxon(connection, taxonName);
                   }
                }
                if (action.equals("transferPage")) {
                  putLookupDataInRequest(request, connection);

                  Taxon orphanDescEditTaxon = orphansDb.getOrphanDescEditTaxon(taxonName);
                  request.setAttribute("orphanDescEditTaxon", orphanDescEditTaxon);   
                  return mapping.findForward("transferPage");
                }
                if (action.equals("transfer")) {
                  if ("".equals(theForm.getToTaxonName())
                    && (theForm.getSubfamily().equals("") || theForm.getGenus().equals("") || theForm.getSpecies().equals(""))
                    && "".equals(theForm.getSuggestedTaxonName())
                    ) {
                       String message = "Empty values not allowed - subfamily:" + theForm.getSubfamily() + " genus:" + theForm.getGenus() + " species:" + theForm.getSpecies() + " toTaxonName:" + theForm.getToTaxonName() + " suggestedTaxonName:" + theForm.getSuggestedTaxonName();
                       request.setAttribute("message", message);
                       return mapping.findForward("message");
                  }
                  String message = transferEditsToTaxon(connection, theForm);
                  if (message.contains("success")) {
                     request.setAttribute("message", message);                  
                  } else {
                     request.setAttribute("message", message);
                     return mapping.findForward("message");                  
                  }
                }
              }
            }

            putLookupDataInRequest(request, connection);

            list = orphansDb.getSpecimenOrphanDescEditTaxons();
            s_log.debug("execute(XXX)");
            list.addAll(orphansDb.getTaxonOrphanDescEditTaxons());
            request.setAttribute("orphanDescEditTaxons", list);        

            return mapping.findForward("success");
                      
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return mapping.findForward("error");
        } finally { 		
            DBUtil.close(connection, this, "OrphanDescEditsAction.execute()");
        }

    } 

    private void putLookupDataInRequest(HttpServletRequest request, Connection connection) 
        throws SQLException {

        ArrayList<String> subfamilies = new ArrayList<>();
        ArrayList<String> genera = new ArrayList<>();
        ArrayList<String> speciesList = new ArrayList<>();
        
        Statement stmt1 = connection.createStatement();
        String query = "select distinct subfamily from taxon where family = 'formicidae' and taxarank = 'subfamily' and status = 'valid'";
        ResultSet rset1 = stmt1.executeQuery(query);
        while (rset1.next()) {
            String subfamily = rset1.getString(1);
            subfamilies.add(subfamily);
        }

        query = "select distinct genus from taxon where family = 'formicidae' and status = 'valid' and taxarank = 'genus'";
        rset1 = stmt1.executeQuery(query);
        while (rset1.next()) {
            String genus = rset1.getString(1);
            genera.add(genus);
        }

        query = "select distinct species from taxon where family = 'formicidae' and status = 'valid' and (taxarank = 'species' or taxarank = 'subspecies')";
        rset1 = stmt1.executeQuery(query);
        while (rset1.next()) {
            String species = rset1.getString(1);
            speciesList.add(species);
        }
        
        stmt1.close();

        request.setAttribute("subfamilies", subfamilies);
        request.setAttribute("genera", genera);
        request.setAttribute("speciesList", speciesList);
    }

    private void deleteTaxonDescEdits(Connection connection, String taxonName) 
        throws SQLException {
        
        // Do not operate on Specimen Description_edit records (code is null);
        
        Statement stmt1 = connection.createStatement();
        String delete = "delete from description_edit where taxon_name = '" + taxonName + "' and code is null"; 
        stmt1.executeUpdate(delete);
        s_log.warn("deleteTaxonDescEdits taxon_name:" + taxonName + " dml:" + delete);
    }
    
    private void deleteTaxon(Connection connection, String taxonName) 
        throws SQLException {

        Statement stmt1 = connection.createStatement();
        String delete = "delete from taxon where taxon_name = '" + taxonName + "'"; 
        stmt1.executeUpdate(delete);
        delete = "delete from proj_taxon where taxon_name = '" + taxonName + "'"; 
        stmt1.executeUpdate(delete);
        delete = "delete from geolocale_taxon where taxon_name = '" + taxonName + "'"; 
        stmt1.executeUpdate(delete);
        delete = "delete from bioregion_taxon where taxon_name = '" + taxonName + "'"; 
        stmt1.executeUpdate(delete);
        delete = "delete from museum_taxon where taxon_name = '" + taxonName + "'"; 
        stmt1.executeUpdate(delete);
        s_log.warn("deleteTaxon taxon_name:" + taxonName + " dml:" + delete);
    }

    private String transferEditsToTaxon(Connection connection, OrphanTaxonsForm theForm) 
        throws SQLException {
        // DO operate on Specimen Description_edit records (code is null);
        
        String oldTaxonName = theForm.getTaxonName();        

        Taxon newTaxon = null;
        String newTaxonName = "";
        
        if (theForm.getToTaxonName() != null && !"".equals(theForm.getToTaxonName())) {
          newTaxon = new TaxonDb(connection).getTaxon(theForm.getToTaxonName());
          if (newTaxon == null) return "taxon:" + theForm.getToTaxonName() + " does not exist.";
        } if (theForm.getSuggestedTaxonName() != null && !"".equals(theForm.getSuggestedTaxonName())) {
          s_log.debug("transferEditsToTaxon() suggestedTaxonName:" + theForm.getSuggestedTaxonName());
          newTaxon = new TaxonDb(connection).getTaxon(theForm.getSuggestedTaxonName());
        } else {
          String subfamily = theForm.getSubfamily();
          String genus = theForm.getGenus();
          String species = theForm.getSpecies();
          String subspecies = theForm.getSubspecies();
         
          newTaxon = Taxon.getTaxonOfRank(subfamily, genus, species, subspecies);
        }

        if (!newTaxon.isExists(connection)) {
          return "taxon:" + newTaxonName + " does not exist.";
        }

        newTaxonName = newTaxon.getTaxonName();

        s_log.warn("transferEditsToTaxon() from:" + oldTaxonName + " to:" + newTaxonName);
        Statement stmt1 = connection.createStatement();
        String transfer = "update description_edit set taxon_name = \"" + newTaxonName + "\" where taxon_name = \"" + oldTaxonName + "\" and code is null"; 
        int returnVal = stmt1.executeUpdate(transfer);    
        s_log.warn("ReturnVal = " + returnVal);
        return "Description Edits successfully transfered to " + newTaxonName;
    }   
}

