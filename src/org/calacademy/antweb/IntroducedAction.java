package org.calacademy.antweb;

import java.util.*;
import java.io.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class IntroducedAction extends Action {

/*
Primary documentation spot for Introduced functionality.

The BioregionMapMgr (link on the curate page) displays and manages the mappings of
native bioregions to genera.


*/

    private static final Log s_log = LogFactory.getLog(IntroducedAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward b = Check.notBot(request, mapping); if (b != null) return b;

        ArrayList<String> introduced = null;

        String geolocaleIdStr = request.getParameter("geolocaleId");
        Geolocale geolocale = null;
        if (geolocaleIdStr != null) {
            ActionForward a = Check.init(Check.GEOLOCALE, request, mapping); if (a != null) return a;

            int geolocaleId = 0;
            try {
                geolocaleId = Integer.parseInt(geolocaleIdStr);
            } catch (NumberFormatException e) {
                request.setAttribute("message", "valid Geolocale ID expected. Found:" + geolocaleId);
                return mapping.findForward("message");
            }
            geolocale = GeolocaleMgr.getGeolocale(geolocaleId);
            if (geolocale == null) {
                request.setAttribute("message", "geolocale not found:" + geolocaleId);
                return mapping.findForward("message");
            }
        }

        String bioregionName = request.getParameter("bioregionName");
        Bioregion bioregion = null;
        if (bioregionName != null) {
            ActionForward a = Check.init(Check.BIOREGION, request, mapping); if (a != null) return a;

            bioregion = BioregionMgr.getBioregion(bioregionName);
            if (bioregion == null) {
                request.setAttribute("message", "bioregion not found:" + bioregion);
                return mapping.findForward("message");
            }
        }

        String dbUtilName = "IntroducedAction.execute()";
  		DataSource dataSource = null;
        Connection connection = null;
        try {
          dataSource = getDataSource(request, "conPool");
          if (HttpUtil.tooBusyForBots(dataSource, request)) { HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }
          connection = DBUtil.getConnection(dataSource, dbUtilName);

            if (geolocale != null) {
                introduced = getGeolocaleIntroduced(geolocale.getId(), connection);
                request.setAttribute("overview", geolocale);
            } else {
                introduced = getBioregionIntroduced(bioregionName, connection);
                request.setAttribute("overview", bioregion);
            }
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
        } finally {
			DBUtil.close(connection, this, dbUtilName);
		}
		
        request.setAttribute("introduced", introduced);
	    return mapping.findForward("introduced");
    }

    private ArrayList<String> getGeolocaleIntroduced(int geolocaleId, Connection connection) {
        ArrayList<String> introduced = new ArrayList<>();

		String query = "select gt.taxon_name from geolocale_taxon gt, taxon t where gt.taxon_name = t.taxon_name " 
		  + " and gt.geolocale_id = " + geolocaleId + " and gt.is_introduced = 1 order by t.genus, t.species, t.subspecies";

        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(connection, "getGeolocaleIntroduced()");

          rset = stmt.executeQuery(query);
          String taxonName = null;
          while (rset.next()) {
            taxonName = rset.getString("taxon_name");
              introduced.add(taxonName);
          }
          s_log.debug("execute() query:" + query);
        } catch (SQLException e) {
          s_log.warn("getGeolocaleIntroduced() query:" + query + " e:" + e);
        } finally {
          DBUtil.close(stmt, rset, this, "getGeolocaleIntroduced()");
        }   
        return introduced;
    }

    private ArrayList<String> getBioregionIntroduced(String bioregionName, Connection connection) {
        ArrayList<String> introduced = new ArrayList<>();

        String query = "select bt.taxon_name from bioregion_taxon bt, taxon t where bt.taxon_name = t.taxon_name "
                + " and bt.bioregion_name = '" + bioregionName + "' and bt.is_introduced = 1 order by t.genus, t.species, t.subspecies";

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "getBioregionIntroduced()");

            rset = stmt.executeQuery(query);
            String taxonName = null;
            while (rset.next()) {
                taxonName = rset.getString("taxon_name");
                introduced.add(taxonName);
            }
            s_log.debug("execute() query:" + query);
        } catch (SQLException e) {
            s_log.warn("getBioregionIntroduced() query:" + query + " e:" + e);
        } finally {
            DBUtil.close(stmt, rset, this, "getBioregionIntroduced()");
        }
        return introduced;
    }

}
