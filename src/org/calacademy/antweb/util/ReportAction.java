package org.calacademy.antweb.util;

import java.util.*;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;
import java.io.Writer;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

    
public final class ReportAction extends Action {

    private static final Log s_log = LogFactory.getLog(ReportAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

		String target = HttpUtil.getTarget(request);

		HttpUtil.setUtf8(request, response);

        s_log.debug("ReportAction.execute() target:" + target);

        DynaActionForm df = (DynaActionForm) form;
        String action = (String) df.get("action");
        if ("antwebAdm1s".equals(action)) {
          s_log.debug("Action:" + action);
          //return (mapping.findForward("countryAdm1Data"));
          String adm1CountryData = GeolocaleMgr.getAdm1CountryData();
          response.setContentType("application/json;charset=UTF-8");
		  response.setContentType("application/txt");
          response.setHeader("Content-disposition", "attachment; filename=antwebAdm1s.txt");
		  //response.setCharacterEncoding("");
		  //response.setContentLength(747044);
		  Writer writer = response.getWriter();
		  writer.write(adm1CountryData);
		  return null;
        }
        if ("acceptedAdm1s".equals(action)) {
          s_log.debug("Action:" + action);
          //return (mapping.findForward("countryAdm1Data"));
          String adm1CountryData = GeolocaleMgr.getAcceptedAdm1CountryData();
          response.setContentType("application/json;charset=UTF-8");
		  response.setContentType("application/txt");
          response.setHeader("Content-disposition", "attachment; filename=acceptedAdm1s.txt");
		  //response.setCharacterEncoding("");
		  //response.setContentLength(747044);
		  Writer writer = response.getWriter();
		  writer.write(adm1CountryData);
		  return null;
        }
         
        if (target != null && target.contains("countryAdm1List")) {
          return mapping.findForward("countryAdm1List");
        }

        if (target != null && target.contains("bioregionCountryList")) {
          ArrayList<HashMap<String, String>> bioregionCountryList = getBioregionCountryList(request);
          request.setAttribute("bioregionCountryList", bioregionCountryList);
          return mapping.findForward("bioregionCountryList");
        }
        
        return null;
	}	
	
/*
select g.bioregion, g.name as country, '' as adm1, '' as countryBioregion from geolocale g 
where g.georank = 'country' and g.is_valid = 1 
union select adm1.bioregion, adm1.parent as country, adm1.name as adm1, country.bioregion as countryBioregion from geolocale adm1, geolocale country    
where adm1.parent = country.name    and country.georank = 'country'    
and adm1.georank = 'adm1'    
and adm1.is_valid = 1    
and adm1.bioregion != country.bioregion and adm1.bioregion is not null order by bioregion, country, adm1;
*/

    private ArrayList<HashMap<String, String>> getBioregionCountryList(HttpServletRequest request) {
        ArrayList<HashMap<String, String>> bioregionCountryList = new ArrayList<>();
		boolean success = false;
		Connection connection = null;
        Statement stmt = null;
        ResultSet rset = null;
		try {
			DataSource dataSource = getDataSource(request, "conPool");
			connection = DBUtil.getConnection(dataSource, "getBioregionCountryList()");
			stmt = connection.createStatement();
			
  	 	    String query = "select g.bioregion, g.name as country, '' as adm1, '' as countryBioregion from geolocale g " 
  	 	      + "   where g.georank = 'country' and g.is_valid = 1" 
              + " union select adm1.bioregion, adm1.parent as country, adm1.name as adm1, country.bioregion as countryBioregion from geolocale adm1, geolocale country "
              + "   where adm1.parent = country.name " 
              + "   and country.georank = 'country' "
              + "   and adm1.georank = 'adm1' " 
              + "   and adm1.is_valid = 1 "
              + "   and adm1.bioregion != country.bioregion and adm1.bioregion is not null"
              + " order by bioregion, country, adm1 ";
            
            s_log.debug("getBioregionCountryList() query:" + query);
            
			rset = stmt.executeQuery(query);	
			while (rset.next()) {
			    HashMap<String, String> map = new HashMap<>();
				map.put("bioregion", rset.getString(1));
                map.put("country", rset.getString(2));
                map.put("adm1", rset.getString(3));
                map.put("countryBioregion", rset.getString(4));
                bioregionCountryList.add(map);                                 
			}    
                                      
		} catch (SQLException e) {
			s_log.error("getBioregionCountryList() e:" + e);
		} finally {
            DBUtil.close(connection, stmt, rset, this, "getBioregionCountryList()");
		}
        return bioregionCountryList;
    }

}
