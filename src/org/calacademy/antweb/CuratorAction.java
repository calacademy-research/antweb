package org.calacademy.antweb;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;
import java.util.*;

import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

//import org.apache.commons.collections.*;
//import com.google.common.collect.*;

public final class CuratorAction extends Action {

    private static final Log s_log = LogFactory.getLog(CuratorAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {
        
        ActionForward a = Check.init(request, mapping); if (a != null) return a;

        Login accessLogin = LoginMgr.getAccessLogin(request); 

		HttpSession session = request.getSession();
        DynaActionForm df = (DynaActionForm) form;
        
        // Curator params.
        String name = (String) df.get("name"); // Name could be id or name. We try id first.
        int curatorId = 0;
        Integer id = (Integer) df.get("id");
        if (id != null) curatorId = id;
        
        //A.log("execute() form:" + df);

        // Contribution params.        
        String taxonName = (String) df.get("taxonName");
        int geolocaleId = 0;
        Integer geolocaleIdInt = (Integer) df.get("geolocaleId");
        if (geolocaleIdInt != null) geolocaleId = geolocaleIdInt;

		Connection connection = null;
		String dbMethodName = DBUtil.getDbMethodName("CuratorAction.execute()");
		try {
          DataSource dataSource = getDataSource(request, "conPool");
          connection = DBUtil.getConnection(dataSource, dbMethodName);

		  if (!Utility.isBlank(name) || curatorId != 0) {
			// Show Curator
			//A.log("execute() name:" + name + " curatorId:" + curatorId);
            LoginDb loginDb = new LoginDb(connection);
			Curator curator = loginDb.getCurator(curatorId);

			if (curator == null) {
				String message = "  Curator not found for name:" + name + " id:" + id + ".";
				request.setAttribute("message", message);
				return mapping.findForward("message");
			}

// Performing very poorly on the live server
//		    ArrayList<Curation> curations = (new GeolocaleTaxonLogDb(connection)).getCurations(curatorId);
//			request.setAttribute("curations", curations);

			request.setAttribute("curator", curator);
			return mapping.findForward("curator");
		  } else if (geolocaleId != 0 && taxonName != null) {
			// Show curation        
			//A.log("execute() taxonName:" + taxonName + " geolocaleId:" + geolocaleId);

            GeolocaleTaxon geolocaleTaxon = (GeolocaleTaxon) new GeolocaleTaxonDb(connection).get(geolocaleId, taxonName); // to get Curator
            if (geolocaleTaxon == null) {
				String message = "geolocale_taxon is null for taxonName:" + taxonName + " geolocaleId:" + geolocaleId + ".";
                //A.log("execute() " + message);
				request.setAttribute("message", message);
				return mapping.findForward("message");
            }
			Curation curation = new GeolocaleTaxonLogDb(connection).getCuration(taxonName, geolocaleId);
			if (curation == null) curation = new Curation();
			curation.setGeolocaleTaxon(geolocaleTaxon);
			request.setAttribute("curation", curation);
			//A.log("execute() curation:" + curation);
			return mapping.findForward("curation");
		  } else {
			// show all Curators.
			
			LoginDb loginDb = new LoginDb(connection);
			ArrayList<Curator> curators = LoginMgr.getCurators();
			request.setAttribute("curators", curators);
            //A.log("a1d:" + accessLogin.getName());
			
			return mapping.findForward("curators");
		  }

	   } catch (SQLException e) {
		 AntwebUtil.log("execute() e:" + e);
	   } finally {
         DBUtil.close(connection, this, dbMethodName);
       }
          



       return null; // never happen.
	}

}
