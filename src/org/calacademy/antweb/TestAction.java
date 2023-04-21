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

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

//http://localhost/antweb/test.do
public final class TestAction extends Action {

    private static final Log s_log = LogFactory.getLog(TestAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

		String message = "Tested.";

		HttpSession session = request.getSession();
			
        if (HttpUtil.getTarget(request).contains("mobile")) {
          return mapping.findForward("mobile");
        }			
			
		Connection connection = null;
		String dbMethodName = DBUtil.getDbMethodName("TestAction.execute()");
		try {
			DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            int speciesFound = 0;
            if (AntwebProps.isDevMode()) {
            	SpecimenDb specimenDb = new SpecimenDb(connection);
				ArrayList<String> typeStatusList = specimenDb.getTypeStatusList(1);
                for (String typeStatus : typeStatusList) {
                	//String typeStatus = "Holotype of Anochetus daedalus";
   				    // This shows some functionality that would handle the specimen.type_status field entries.
				    //String taxonName = TaxonMgr.getTypeStatusSpecies(typeStatus);

				    //String taxonName = "Anochetus daedalus";
				    String taxonName = "ponerinaeanochetus daedalus";
					if (taxonName != null) {
						++speciesFound;
						//Species species = TaxonMgr.getSpecies(connection, taxonName);

						Taxon species = TaxonMgr.getTaxon(taxonName);
						message = "execute() taxonName:" + taxonName + " speiciesFound:" + speciesFound + " species:" + species;
						s_log.debug(message);
					}
				}
			}

            UploadDb uploadDb = new UploadDb(connection);
            uploadDb.updateCounts(21);

		} catch (SQLException e) {
			s_log.error("execute() e:" + e);
		} finally {	 		
			DBUtil.close(connection, this, dbMethodName);
		}
		        
        if (false) {        
		  request.setAttribute("message", message);
		  return mapping.findForward("message");
        }

		return mapping.findForward("success");
	}
	
}
