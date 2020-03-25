package org.calacademy.antweb;

import java.util.*;
import java.io.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.util.AntwebProps;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class EndemicAction extends Action {

    private static Log s_log = LogFactory.getLog(EndemicAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ArrayList<String> endemics = null;

        String geolocaleIdStr = (String) request.getParameter("geolocaleId");
        Geolocale geolocale = null;
        if (geolocaleIdStr != null) {
            ActionForward a = Check.init(Check.GEOLOCALE, request, mapping); if (a != null) return a;

            int geolocaleId = 0;
            try {
                geolocaleId = (Integer.valueOf(geolocaleIdStr)).intValue();
            } catch (NumberFormatException e) {
                request.setAttribute("message", "valid Geolocale ID expected. Found:" + geolocaleId);
                return (mapping.findForward("message"));
            }
            geolocale = GeolocaleMgr.getGeolocale(geolocaleId);
            if (geolocale == null) {
                request.setAttribute("message", "geolocale not found:" + geolocaleId);
                return (mapping.findForward("message"));
            }
        }

        String bioregionName = (String) request.getParameter("bioregionName");
        Bioregion bioregion = null;
        if (bioregionName != null) {
            ActionForward a = Check.init(Check.BIOREGION, request, mapping); if (a != null) return a;

            bioregion = BioregionMgr.getBioregion(bioregionName);
            if (bioregion == null) {
                request.setAttribute("message", "bioregion not found:" + bioregion);
                return (mapping.findForward("message"));
            }
        }

        String dbUtilName = "EndemicAction.execute()";
        Connection connection = null;
        try {
  		    javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            if (HttpUtil.tooBusyForBots(dataSource, request)) { HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }
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

        return (mapping.findForward("endemic"));
    }

    private ArrayList<String> getGeolocaleEndemics(int geolocaleId, Connection connection) {
        ArrayList<String> endemics = new ArrayList<String>();

		String query = "select gt.taxon_name from geolocale_taxon gt, taxon where gt.taxon_name = taxon.taxon_name " 
		  + " and gt.geolocale_id = " + geolocaleId 
		  + (new StatusSet()).getAndCriteria()
		  + " and gt.is_endemic = 1 order by genus, species, subspecies";

        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(connection, "getEndemics()");

          rset = stmt.executeQuery(query);
          String taxonName = null;
          while (rset.next()) {
            taxonName = rset.getString("taxon_name");
            endemics.add(taxonName);
          }
          //A.log("execute() count:" + count);                        
        } catch (SQLException e) {
          s_log.warn("getEndemics() query:" + query + " e:" + e);        
        } finally {
          DBUtil.close(stmt, rset, this, "getEndemics()");
        }   
        return endemics;
    }

    private ArrayList<String> getBioregionEndemics(String bioregionName, Connection connection) {
        ArrayList<String> endemics = new ArrayList<String>();

        String query = "select bt.taxon_name from bioregion_taxon bt, taxon where bt.taxon_name = taxon.taxon_name "
                + " and bt.bioregion_name = '" + bioregionName + "'"
                + (new StatusSet()).getAndCriteria()
                + " and bt.is_endemic = 1 order by genus, species, subspecies";

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "getBioregionEndemics()");

            rset = stmt.executeQuery(query);
            String taxonName = null;
            while (rset.next()) {
                taxonName = rset.getString("taxon_name");
                endemics.add(taxonName);
            }
            //A.log("execute() count:" + count);
        } catch (SQLException e) {
            s_log.warn("getBioregionEndemics() query:" + query + " e:" + e);
        } finally {
            DBUtil.close(stmt, rset, this, "getBioregionEndemics()");
        }
        return endemics;
    }
}
