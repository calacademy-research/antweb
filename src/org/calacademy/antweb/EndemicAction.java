package org.calacademy.antweb;

import java.util.*;
import java.io.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class EndemicAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(EndemicAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward b = Check.notBot(request, mapping); if (b != null) return b;

        ArrayList<String> endemics = null;

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

        String dbUtilName = "EndemicAction.execute()";

        if (HttpUtil.tooBusyForBots(request)) { return HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }

        Connection connection = null;
        try {
  		    DataSource dataSource = getDataSource(request, "conPool");
		    connection = DBUtil.getConnection(dataSource, dbUtilName);

            if (geolocale != null) {
                endemics = getGeolocaleEndemics(geolocale.getId(), connection);
                request.setAttribute("overview", geolocale);
            } else {
                endemics = getBioregionEndemics(bioregionName, connection);
                request.setAttribute("overview", bioregion);
            }
		} catch (SQLException e) {
            s_log.error("execute() e:" + e);
        } finally {
			DBUtil.close(connection, this, dbUtilName);
		}

        request.setAttribute("endemic", endemics);        
        //request.setAttribute("geolocale", geolocale);

        return mapping.findForward("endemic");
    }

    /** The names flagged endemic on the geolocale's list, by the one rule of the lists and the numbers
     *  (PlaceTotals.flaggedNamesSql; Brian Fisher 2026-10-27, docs/numbers.md B70: counted statuses, no synonyms). */
    private ArrayList<String> getGeolocaleEndemics(int geolocaleId, Connection connection) {
        return flagged(org.calacademy.antweb.home.PlaceTotals.GEOLOCALE, "" + geolocaleId, connection);
    }

    private ArrayList<String> getBioregionEndemics(String bioregionName, Connection connection) {
        return flagged(org.calacademy.antweb.home.PlaceTotals.BIOREGION, bioregionName, connection);
    }

    private ArrayList<String> flagged(String type, String key, Connection connection) {
        try {
            return org.calacademy.antweb.home.PlaceTotals.flaggedNames(connection, type, key, "endemic");
        } catch (SQLException e) {
            s_log.warn("getGeolocaleEndemics() " + type + " " + key + " e:" + e);
            return new ArrayList<>();
        }
    }
}
