package org.calacademy.antweb;

import java.io.IOException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;

public final class TaxaListAction extends Action {

    private static final Log s_log = LogFactory.getLog(TaxaListAction.class);

// taxaList.do?valid=1&extant=1&project=worldants&rank=subfamily

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward a = Check.init("project", request, mapping); if (a != null) return a;

		Locale locale = getLocale(request);

		HttpSession session = request.getSession();
		String rank = request.getParameter("rank");
		String projectName = request.getParameter("project"); // project
        String bioregionName = request.getParameter("bioregionName"); // bioregion
		String museumCode = request.getParameter("museumCode"); // museum
		String geolocaleName = request.getParameter("geolocaleName"); // geolocale

		String status = request.getParameter("status");
		String extant = request.getParameter("extant");
		String imaged = request.getParameter("imaged");		
    
        String title = "";
        if (status != null) title += Formatter.initCap(status) + " ";
        if ("1".equals(extant)) title += "Extant ";
        if ("1".equals(imaged)) title += "Imaged ";

        String pluralRank = Formatter.initCap(Rank.getPluralOf(rank));
        if (pluralRank == null || "null".equals(pluralRank)) pluralRank = "All";
        title += pluralRank + " of ";
    
        //A.log("execute() pluralRank:" + pluralRank + " title:" + title);
            
        String query = "select taxon_name from taxon where 1=1 ";

        if (!"".equals(rank) && rank != null) {
          if ("species".equals(rank)) {
            query += " and (taxarank = 'species' or taxarank = 'subspecies')";
          } else
            query += " and taxarank = '" + rank + "'";
        }
        
        if (!"".equals(status) && status != null) {
          query += " and status = '" + status + "'";
        }
        if (!"".equals(extant) && extant != null) {
          query += " and fossil != " + extant;
        }
        if ("1".equals(imaged)) {
          query += " and image_count > 0";
        } else if ("0".equals(imaged)) {
          query += " and image_count = 0";        
        }

        String param = "";
        if (!"".equals(projectName) && projectName != null) {
          query += " and taxon_name in (select taxon_name from proj_taxon where project_name = '" + projectName + "')";
          param = "&project=" + projectName;
          Project project = ProjectMgr.getProject(projectName);
          title += project.getTitle();
        }
        if (!"".equals(bioregionName) && bioregionName != null) {
          query += " and taxon_name in (select taxon_name from bioregion_taxon where bioregion_name = '" + bioregionName + "')";
          param = "&bioregionName=" + bioregionName;
          Bioregion bioregion = BioregionMgr.getBioregion(bioregionName);
          title += bioregion;
        }
        if (!"".equals(museumCode) && museumCode != null) {
          query += " and taxon_name in (select taxon_name from museum_taxon where code = '" + museumCode + "')";
          Museum museum = MuseumMgr.getMuseum(museumCode);
          param = "&museumName=" + museumCode;
          title += museum;
        }
        if (!"".equals(geolocaleName) && geolocaleName != null) {
          Geolocale geolocale = GeolocaleMgr.getGeolocale(geolocaleName);
          int geolocaleId = GeolocaleMgr.getGeolocaleId(geolocaleName);
          query += " and taxon_name in (select taxon_name from geolocale_taxon where geolocale_id = " + geolocaleId + ")";
          param = "&geolocaleId=" + geolocaleId;
          title += geolocale;
        }
        query += " limit 1000";

        ArrayList<String> taxaList = new ArrayList<>();
        Connection connection = null;
        Statement stmt = null;
        ResultSet rset = null;
		try {
			DataSource dataSource = getDataSource(request, "conPool");
			connection = DBUtil.getConnection(dataSource, "TaxaListAction.execute()");

            stmt = connection.createStatement();
            rset = stmt.executeQuery(query);
            s_log.debug("execute() query:" + query);

            while (rset.next()) {
                String taxonName = rset.getString(1);
                String taxonLink = "<a href=\"" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + param + "\">" + taxonName + "</a>";
                taxaList.add(taxonLink);
            }
		} catch (SQLException e) {
			s_log.error("execute() e:" + e);
		} finally {
          DBUtil.close(connection, stmt, rset, this, "TaxaListAction.execute()");
		}
		
        //s_log.warn("execute() list:" + taxaList);
		request.setAttribute("title", title);
	    request.setAttribute("list", taxaList);        
        return mapping.findForward("success");
	}
}
