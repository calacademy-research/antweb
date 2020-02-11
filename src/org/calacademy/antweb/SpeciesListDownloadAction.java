package org.calacademy.antweb;

import java.io.IOException;
import java.sql.*;
import java.io.*; 
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import org.apache.struts.action.*;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class SpeciesListDownloadAction extends Action {

    private static Log s_log = LogFactory.getLog(SpeciesListDownloadAction.class);
    
    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        HttpSession session = request.getSession();

        ActionForward a = Check.initLogin(request, mapping); if (a != null) return a;

        Overview overview = OverviewMgr.getAndSetOverview(request);
        if (overview == null) return OverviewMgr.returnMessage(request, mapping);

        java.sql.Connection connection = null;
        try {

          javax.sql.DataSource dataSource = getDataSource(request, "conPool");
          connection = DBUtil.getConnection(dataSource, "SpeciesListDownloadAction.execute()");

          ArrayList<Taxon> taxa = null;
          String message = null;

          A.log("execute() overview:" + overview); //projectName:" + projectName + " museumCode:" + museumCode);

          TaxonSetDb taxonSetDb = null;
          
          if (overview instanceof Project) {
            taxonSetDb = new ProjTaxonDb(connection);
            taxa = taxonSetDb.getTaxa(overview.getName());
          } else
          if (overview instanceof Geolocale) {
            taxonSetDb = new GeolocaleTaxonDb(connection);
            taxa = taxonSetDb.getTaxa(overview.getName());
          } else
          if (overview instanceof Museum) {
            taxonSetDb = new MuseumTaxonDb(connection);;
            taxa = taxonSetDb.getTaxa(((Museum) overview).getCode());
          }


          if (taxa == null || taxa.size() == 0) {
              message = "Overview:" + overview + " not found.";
          }

          /*
           else if (museumCode != null) {
            //taxa = (new MuseumTaxonDb(connection)).getTaxa(museumCode);
            if (taxa == null || taxa.size() == 0) {
              //message = "Museum:" + museumCode + " not found.";
              message = "Apologies but museum data download is not currently supported";
            }
          } 
          else {
              message = "Overview:" + overview + " not found.";
          }
          */

          if (message != null) {
              request.setAttribute("message", message);
              return mapping.findForward("message");
          }

          String output = "Subfamily\tGenus\tSpecies\tSubspecies\tAuthor Date\r";

          String line = "";
          for (Taxon taxon : taxa) {
            line = taxon.getPrettySubfamily() + "\t" + taxon.getPrettyGenus() + "\t" + taxon.getPrettySpecies() + "\t" + taxon.getPrettySubspecies() + "\t" + taxon.getAuthorDate() + "\r";            
            output += line;
          }

          response.setContentType("text/plain");
          Writer writer = response.getWriter();
          writer.write(output);
          return null;  
            
        } catch (SQLException e) {
          s_log.warn("execute() e:" + e);
        } finally {
          DBUtil.close(connection, "SpeciesListDownloadAction.execute()");
        }

        return mapping.findForward("success");  
    }
    
}
