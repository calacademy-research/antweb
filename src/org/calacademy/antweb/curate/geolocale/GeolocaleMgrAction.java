package org.calacademy.antweb.curate.geolocale;

import java.util.*;
import java.io.*;
import java.sql.*;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class GeolocaleMgrAction extends Action {

    private static Log s_log = LogFactory.getLog(GeolocaleMgrAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        HttpUtil.setUtf8(request, response);       
        HttpSession session = request.getSession();

        ActionForward c = Check.login(request, mapping); if (c != null) return c;

        EditGeolocaleForm editGeolocaleForm = (EditGeolocaleForm) form;

        if ("displayGeoregions".equals(editGeolocaleForm.getName())) {
          request.setAttribute("message", GeolocaleMgr.getGeoregionsDisplayHtml());
          return mapping.findForward("adminMessage");
        }
        if ("displayBioregions".equals(editGeolocaleForm.getName())) {
          request.setAttribute("message", BioregionMgr.getBioregionsDisplayHtml());
          return mapping.findForward("adminMessage");
        }

        String georank = editGeolocaleForm.getGeorank();        		
		if (georank == null) georank = "country";
		
        Connection connection = null;
        try {
            DataSource dataSource = getDataSource(request, "conPool");
		    connection = DBUtil.getConnection(dataSource, "GeolocaleMgrAction.execute()");

            GeolocaleDb geolocaleDb = new GeolocaleDb(connection);              

			if (HttpUtil.isPost(request) && editGeolocaleForm.getId() > 0) {
			  s_log.debug("GeolocaleMgrAction.execute() POST form:" + editGeolocaleForm);
			  geolocaleDb.adjustGeolocale(editGeolocaleForm);
			}

            String orderBy = editGeolocaleForm.getOrderBy();            
            String parent = editGeolocaleForm.getParent();
            
            ArrayList<Geolocale> geolocaleArray = geolocaleDb.getGeolocales(georank, parent, false, orderBy);
s_log.debug("GeolocaleMgrAction.execute() geolocaleArray.size:" + geolocaleArray.size());
            request.setAttribute("georank", georank);
            request.setAttribute("parent", parent);
            request.setAttribute("geolocaleArray", geolocaleArray);
            ArrayList<Geolocale> validChildren = geolocaleDb.getValidChildren(parent);
            request.setAttribute("validChildren", validChildren);

            
            s_log.debug("execute() success");
            return (mapping.findForward("success"));

		} catch (SQLException e) {
			s_log.error("execute() e:" + e);
		} finally {
			DBUtil.close(connection, this, "GeolocaleMgrAction.execute()");
		}

        return (mapping.findForward("failure"));
    }
}
