package org.calacademy.antweb;

import java.util.*;
import java.io.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import org.apache.struts.action.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import com.mchange.v2.c3p0.*;
import org.calacademy.antweb.home.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class StatisticsAction extends Action {

    private static Log s_log = LogFactory.getLog(StatisticsAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward c = Check.login(request, mapping); if (c != null) return c;

        String byUpload = (String) request.getParameter("byUpload");
        String project = (String) request.getParameter("project");
        String byProject = (String) request.getParameter("byProject");
        String byBioregion = (String) request.getParameter("byBioregion");
        String byMuseum = (String) request.getParameter("byMuseum");
        String byGeolocale = (String) request.getParameter("byGeolocale");
        String isLinkStr = (String) request.getParameter("isLink");
        boolean isLink = true;
        if ("false".equals(isLinkStr)) isLink = false;
        request.getSession().setAttribute("isLink", isLink);
        String bodyStr = (String) request.getParameter("body");
        boolean body = ("true".equals(bodyStr));

        boolean success = false;

        Connection connection = null;
        try {
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "StatisticsAction()");
                
            if ("true".equals(byUpload)) {
              success = getStatisticsByUpload(request, connection);
              return (mapping.findForward("statisticsStr"));
            } else if (project != null) {
              success = getStatisticsByAProject(request, project, isLink, connection);
            } else if ("true".equals(byProject)) {
              success = getStatisticsByProject(request, isLink, connection);
            } else if ("true".equals(byMuseum)) {
              success = getStatisticsByMuseum(request, isLink, connection);
            } else if ("true".equals(byBioregion)) {
              success = getStatisticsByBioregion(request, isLink, connection);
            } else if ("true".equals(byGeolocale)) {
              success = getStatisticsByGeolocale(request, isLink, connection);
            } else {
              success = getStatistics(request, connection);
              return (mapping.findForward("statisticsStr"));
            }
       
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            success = false;
        } finally { 		
            DBUtil.close(connection, this, "StatisticAction()");
        }
        
		if (success) {
		  if (body) {
		    return (mapping.findForward("statisticsRealTime-body"));
		  } else {
			return (mapping.findForward("statisticsRealTime"));
		  }
		} else {
			return (mapping.findForward("failure"));
		}
    }

    private boolean getStatistics(HttpServletRequest request, Connection connection) {

		HttpSession session = request.getSession();
		boolean success = false;

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "getStatistics()");              

            String query = "select count(*) from specimen";            
            rset = stmt.executeQuery(query);
            int count = 0;
            while (rset.next()) {
                count = rset.getInt(1);
            }            
            String statistics = "<br><br>current specimen count:<b>" + count + "</b><br><br>";

            query = "select id, action, specimens, extant_taxa, total_taxa, proj_taxa, bioregion_taxa, museum_taxa, geolocale_taxa, geolocale_taxa_introduced, geolocale_taxa_endemic " 
              + " , total_images, specimens_imaged, species_imaged, valid_species_imaged, login_id, created, exec_time from statistics " 
              + " order by created desc limit 1000";
            rset = stmt.executeQuery(query);

            statistics += "<table border=1><tr><td>Id</td><td>Action </td><td>Specimens</td><td>V. Sp.</td>" 
              + "<td>Total Taxa</td><td>Project Taxa</td><td>Bioregion Taxa</td><td>Museum Taxa</td><td>Geolocale Taxa</td><td>Introduced</td><td>Endemic</td><td>Total Images</td><td>Specimens Imaged </td>" 
              + "<td>Species Imaged </td><td>V. Sp. Imaged </td><td>Login Id</td><td>Created</td><td>Exec Time</td></tr>";

            count = 0;
            while (rset.next()) {
                ++count;
                int id = rset.getInt("id");
                String action = rset.getString("action");
                if (action == null) action = "";
                int specimens = rset.getInt("specimens");
                int extantTaxa = rset.getInt("extant_taxa");
                int totalTaxa = rset.getInt("total_taxa");
                int projTaxa = rset.getInt("proj_taxa");
                int bioregionTaxa = rset.getInt("bioregion_taxa");
                int museumTaxa = rset.getInt("museum_taxa");
                int geolocaleTaxa = rset.getInt("geolocale_taxa");
                int geolocaleTaxaIntroduced = rset.getInt("geolocale_taxa_introduced");
                int geolocaleTaxaEndemic = rset.getInt("geolocale_taxa_endemic");
                int totalImages = rset.getInt("total_images");
                int specimensImaged = rset.getInt("specimens_imaged");
                int speciesImaged = rset.getInt("species_imaged");
                int loginId = rset.getInt("login_id");
                Timestamp timestamp = rset.getTimestamp("created");
                String execTime = rset.getString("exec_time");
                if (execTime == null || "null".equals(execTime)) execTime = "n/a";
                int validSpeciesImaged = rset.getInt("valid_species_imaged");
                String formatDate = timestamp.toString();

                String specimensStr = "" + specimens;
                if (count == 1) specimensStr = "<a href=\"" + AntwebProps.getDomainApp() + "/statistics.do?byUpload=true\">" + A.commaFormat(specimens) + "</a>";

                String projectStr = "" + A.commaFormat(projTaxa);
                if (count == 1) projectStr = "<a href=\"" + AntwebProps.getDomainApp() + "/statistics.do?byProject=true\">" + A.commaFormat(projTaxa) + "</a>";

                String bioregionStr = "" + A.commaFormat(bioregionTaxa);
                if (count == 1) bioregionStr = "<a href=\"" + AntwebProps.getDomainApp() + "/statistics.do?byBioregion=true\">" + A.commaFormat(bioregionTaxa) + "</a>";

                String museumStr = "" + A.commaFormat(museumTaxa);
                if (count == 1) museumStr = "<a href=\"" + AntwebProps.getDomainApp() + "/statistics.do?byMuseum=true\">" + A.commaFormat(museumTaxa) + "</a>";

                String geolocaleStr = "" + A.commaFormat(geolocaleTaxa);
                if (count == 1) geolocaleStr = "<a href=\"" + AntwebProps.getDomainApp() + "/statistics.do?byGeolocale=true\">" + A.commaFormat(geolocaleTaxa) + "</a>";

                statistics += "<tr><td>" + id + "</td><td>" + action + "</td><td>" + specimensStr + "</td>" 
                + "<td>" + A.commaFormat(extantTaxa) + "</td><td>"+ A.commaFormat(totalTaxa) + "</td>" 
                + "<td>" + projectStr + "</td>" + "<td>" + bioregionStr + "</td>" + "<td>" + museumStr + "</td>"
                + " <td>" + geolocaleStr + "</td><td>" + A.commaFormat(geolocaleTaxaIntroduced) + "</td><td>" + A.commaFormat(geolocaleTaxaEndemic) + "</td>" 
                + " <td>" + A.commaFormat(totalImages) + "</td><td>" + A.commaFormat(specimensImaged) + "</td><td>" + A.commaFormat(speciesImaged) + "</td>" 
                + " <td>" + A.commaFormat(validSpeciesImaged) + "</td><td>" + loginId + "</td><td>" + formatDate + "</td>" 
                + " <td>" + execTime + "</td></tr>";
            }
            statistics += "</table>";

            session.setAttribute("statistics", statistics);
            success = true;            

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            success = false;
        } finally { 		
            DBUtil.close(stmt, rset, this, "getStatistics()");
        }
        return success;
	}

    private boolean getStatisticsByProject(HttpServletRequest request, boolean isLink, Connection connection) {

		HttpSession session = request.getSession();
		boolean success = false;

        try {
            ArrayList<ArrayList<String>> statistics = ProjTaxonDb.getStatisticsByProject(connection);

            session.setAttribute("statistics", statistics);
            session.setAttribute("isLink", isLink);
            success = true;            

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            success = false;
        }
        
        return success;
	}

    private boolean getStatisticsByBioregion(HttpServletRequest request, boolean isLink, Connection connection) {

		HttpSession session = request.getSession();
		boolean success = false;

        try {
            ArrayList<ArrayList<String>> statistics = BioregionTaxonDb.getStatisticsByBioregion(connection);

            session.setAttribute("statistics", statistics);
            session.setAttribute("isLink", isLink);
            success = true;            

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            success = false;
        }
        
        return success;
	}

    private boolean getStatisticsByMuseum(HttpServletRequest request, boolean isLink, Connection connection) {

		HttpSession session = request.getSession();
		boolean success = false;

        try {
            ArrayList<ArrayList<String>> statistics = MuseumTaxonDb.getStatisticsByMuseum(connection);

            session.setAttribute("statistics", statistics);
            session.setAttribute("isLink", isLink);
            success = true;            

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            success = false;
        }
        
        return success;
	}

    private boolean getStatisticsByGeolocale(HttpServletRequest request, boolean isLink, Connection connection) {

		HttpSession session = request.getSession();
		boolean success = false;

        try {
            ArrayList<ArrayList<String>> statistics = GeolocaleTaxonDb.getStatisticsByGeolocale(connection);

            session.setAttribute("statistics", statistics);
            session.setAttribute("isLink", isLink);
            success = true;            

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            success = false;
        }
        
        return success;
	}
			
    private boolean getStatisticsByAProject(HttpServletRequest request, String project, boolean isLink, Connection connection) {

		HttpSession session = request.getSession();
		boolean success = false;
        try {

            ArrayList<ArrayList<String>> statistics = new ArrayList<>();
            statistics.add(ProjTaxonDb.getProjectStatistics(project, connection));
            
            session.setAttribute("statistics", statistics);
            session.setAttribute("isLink", isLink);
            success = true;            

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            success = false;
        }
        
        return success;
	}
	
    private boolean getStatisticsByUpload(HttpServletRequest request, Connection connection) {
		HttpSession session = request.getSession();
		boolean success = false;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "getDescEditHistory()");        

            String query = "select count(code), name, id from specimen, ant_group where access_group = id group by access_group";
            rset = stmt.executeQuery(query);

            String statistics = "<table border=1><tr><td> Specimen Count </td><td> Group Name </td></tr>";
            while (rset.next()) {
                int count = rset.getInt(1);
                String groupName = rset.getString(2);
                int id = rset.getInt(3);
                statistics += "<tr><td>" + count + "</td><td>" + groupName + "(" + id + ")" + "</td></tr>";
            }            
            statistics += "</table>";            
            session.setAttribute("statistics", statistics);            
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            success = false;
        } finally { 		
            DBUtil.close(stmt, rset, this, "getStatisticsByUpload()");
        }
        
        return success;
	}


}
