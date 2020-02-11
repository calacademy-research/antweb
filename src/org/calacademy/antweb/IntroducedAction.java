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
    
public final class IntroducedAction extends Action {

/*
Primary documentation spot for Introduced functionality.

The BioregionMapMgr (link on the curate page) displays and manages the mappings of
native bioregions to genera.


*/

    private static Log s_log = LogFactory.getLog(IntroducedAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        String geolocaleIdStr = (String) request.getParameter("geolocaleId");
        int geolocaleId = 0;
        try {
          geolocaleId = (new Integer(geolocaleIdStr)).intValue();
        } catch (NumberFormatException e) {
			request.setAttribute("message", "Invalid Geolocale Id:" + geolocaleIdStr);
			return (mapping.findForward("message"));                 
        }
        Geolocale geolocale = GeolocaleMgr.getGeolocale(geolocaleId);
        if (geolocale == null) {
			request.setAttribute("message", "geolocale not found:" + geolocaleId);
			return (mapping.findForward("message")); 
        }

        ArrayList<String> introduced = null;
        String dbUtilName = "IntroducedAction.execute()";
  		javax.sql.DataSource dataSource = null;
        Connection connection = null;
        try {
          dataSource = getDataSource(request, "conPool");

          if (HttpUtil.tooBusyForBots(dataSource, request)) { HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }
              
          connection = DBUtil.getConnection(dataSource, dbUtilName);
          introduced = getIntroduced(geolocaleId, connection);
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
        } finally {
			DBUtil.close(connection, this, dbUtilName);
		}
		
        request.setAttribute("introduced", introduced);        
        request.setAttribute("geolocale", geolocale);
        
	    return (mapping.findForward("introduced"));
    }

    private ArrayList<String> getIntroduced(int geolocaleId, Connection connection) {
        ArrayList<String> endemics = new ArrayList<String>();

		String query = "select gt.taxon_name from geolocale_taxon gt, taxon t where gt.taxon_name = t.taxon_name " 
		  + " and gt.geolocale_id = " + geolocaleId + " and gt.is_introduced = 1 order by t.genus, t.species, t.subspecies";

        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(connection, "getIntroduced()");

          rset = stmt.executeQuery(query);
          String taxonName = null;
          while (rset.next()) {
            taxonName = rset.getString("taxon_name");
            endemics.add(taxonName);
          }
          A.log("execute() query:" + query);                        
        } catch (SQLException e) {
          s_log.warn("getIntroduced() query:" + query + " e:" + e);        
        } finally {
          DBUtil.close(stmt, rset, this, "getIntroduced()");
        }   
        return endemics;
    }             
      
}
