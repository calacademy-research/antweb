package org.calacademy.antweb;

import java.io.IOException;
import java.util.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;

public final class TaxaListAction extends AntwebAction {

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
        java.util.List<String> args = new java.util.ArrayList<>();  // values for the ? placeholders, in order
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
            { query += " and taxarank = ?"; args.add(rank); }
        }
        
        if (!"".equals(status) && status != null) {
          query += " and status = ?"; args.add(status);
        }
        if (!"".equals(extant) && extant != null) {
          query += " and fossil != " + ("1".equals(extant) ? "1" : "0");
        }
        // Imaged: in a place, the place's images (its list row's image_count, as statistics.do counts "Imaged v. sp.";
        // B63, 2026-10-27: was the name's images anywhere); without a place, the name's images.
        String imageCondition = "";
        if ("1".equals(imaged)) {
          imageCondition = " and image_count > 0";
        } else if ("0".equals(imaged)) {
          imageCondition = " and image_count = 0";
        }
        boolean inAPlace = !isEmpty(projectName) || !isEmpty(bioregionName) || !isEmpty(museumCode) || !isEmpty(geolocaleName);
        if (!inAPlace) query += imageCondition;

        String param = "";
        if (!"".equals(projectName) && projectName != null) {
          query += " and taxon_name in (select taxon_name from proj_taxon where project_name = ?" + imageCondition + ")"; args.add(projectName);
          param = "&project=" + urlEncode(projectName);
          Project project = ProjectMgr.getProject(projectName);
          title += project.getTitle();
        }
        if (!"".equals(bioregionName) && bioregionName != null) {
          query += " and taxon_name in (select taxon_name from bioregion_taxon where bioregion_name = ?" + imageCondition + ")"; args.add(bioregionName);
          param = "&bioregionName=" + urlEncode(bioregionName);
          Bioregion bioregion = BioregionMgr.getBioregion(bioregionName);
          title += bioregion;
        }
        if (!"".equals(museumCode) && museumCode != null) {
          query += " and taxon_name in (select taxon_name from museum_taxon where code = ?" + imageCondition + ")"; args.add(museumCode);
          Museum museum = MuseumMgr.getMuseum(museumCode);
          param = "&museumName=" + urlEncode(museumCode);
          title += museum;
        }
        if (!"".equals(geolocaleName) && geolocaleName != null) {
          Geolocale geolocale = GeolocaleMgr.getGeolocale(geolocaleName);
          // by id when given one (statistics.do links geolocales by id: names are not unique, B64)
          int geolocaleId = geolocaleIdOf(geolocaleName);
          query += " and taxon_name in (select taxon_name from geolocale_taxon where geolocale_id = " + geolocaleId + imageCondition + ")";
          param = "&geolocaleId=" + geolocaleId;
          title += geolocale;
        }
        query += " limit 1000";

        ArrayList<String> taxaList = new ArrayList<>();
        Connection connection = null;
        java.sql.PreparedStatement stmt = null;
        ResultSet rset = null;
        String dbMethodName = DBUtil.getDbMethodName("TaxaListAction.execute()");
		try {
			DataSource dataSource = getDataSource(request, "conPool");
			connection = DBUtil.getConnection(dataSource, dbMethodName);

            // Request values are bound as parameters, never pasted into the SQL (security audit 2026-10).
            stmt = connection.prepareStatement(query);
            for (int i = 0; i < args.size(); i++) stmt.setString(i + 1, args.get(i));
            rset = stmt.executeQuery();
            s_log.debug("execute() query:" + query);

            while (rset.next()) {
                String taxonName = rset.getString(1);
                String taxonLink = "<a href=\"" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + urlEncode(taxonName) + param + "\">" + escapeHtml(taxonName) + "</a>";
                taxaList.add(taxonLink);
            }
		} catch (SQLException e) {
			s_log.error("execute() e:" + e);
		} finally {
          DBUtil.close(connection, stmt, rset, this, dbMethodName);
		}
		
        //s_log.warn("execute() list:" + taxaList);
		request.setAttribute("title", escapeHtml(title));
	    request.setAttribute("list", taxaList);        
        return mapping.findForward("success");
	}

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /** The geolocale's id: the parameter itself when it is a number, else the id of the place of that name. */
    static int geolocaleIdOf(String geolocaleName) {
        if (geolocaleName.matches("\\d{1,9}")) return Integer.parseInt(geolocaleName);
        return GeolocaleMgr.getGeolocaleId(geolocaleName);
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
