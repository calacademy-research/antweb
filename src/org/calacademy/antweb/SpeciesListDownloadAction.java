package org.calacademy.antweb;

import java.sql.*;
import java.io.*; 
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class SpeciesListDownloadAction extends Action {

    private static final Log s_log = LogFactory.getLog(SpeciesListDownloadAction.class);
    
    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        HttpSession session = request.getSession();

        ActionForward a = Check.initLogin(request, mapping); if (a != null) return a;

        Overview overview = null;
        try {
            overview = OverviewMgr.getAndSetOverview(request);
        } catch (AntwebException e) {
            return OverviewMgr.returnMessage(request, mapping, e);
        }

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("SpeciesListDownloadAction.execute()");
        try {

          DataSource dataSource = getDataSource(request, "conPool");
          connection = DBUtil.getConnection(dataSource, dbMethodName);

          ArrayList<Taxon> taxa = null;
          String message = null;

          s_log.debug("execute() overview:" + overview); //projectName:" + projectName + " museumCode:" + museumCode);

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
            taxonSetDb = new MuseumTaxonDb(connection);
              taxa = taxonSetDb.getTaxa(((Museum) overview).getCode());
          }

          if (taxa == null || taxa.size() == 0) {
              message = "Overview:" + overview + " not found.";
          }

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
          DBUtil.close(connection, dbMethodName);
        }

        return mapping.findForward("success");  
    }
    
}
