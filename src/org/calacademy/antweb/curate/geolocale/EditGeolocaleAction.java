package org.calacademy.antweb.curate.geolocale;

import java.util.*;
import java.io.*;
import java.sql.*;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.data.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class EditGeolocaleAction extends Action {

    private static Log s_log = LogFactory.getLog(EditGeolocaleAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        HttpSession session = request.getSession();
        HttpUtil.setUtf8(request, response);       

        ActionForward c = Check.login(request, mapping); if (c != null) return c;
		Login accessLogin = LoginMgr.getAccessLogin(request);

        EditGeolocaleForm editGeolocaleForm = (EditGeolocaleForm) form;
        if (editGeolocaleForm == null) return mapping.findForward("startFresh");
        
        String name = editGeolocaleForm.getName();
        String georank = editGeolocaleForm.getGeorank();
        
        String action = editGeolocaleForm.getAction();
		String parent = editGeolocaleForm.getParent();

		String country = editGeolocaleForm.getCountry();

		int id = editGeolocaleForm.getId();
        
        s_log.debug("execute() action:" + action + " name:" + name + " country:" + country + " parent:" + parent + " id:" + id + " isFast:" + editGeolocaleForm.getIsFast());

        if ((name == null || "".equals(name)) && editGeolocaleForm.getIsCreate()) {
			request.setAttribute("message", "Enter an Adm1 Name in the URL bar...");
			return mapping.findForward("message");
        }
     
        if (name == null && id == 0) {
            return mapping.findForward("startFresh");
        }


        Connection connection = null;
        try {
          DataSource dataSource = getDataSource(request, "conPool");
		  connection = DBUtil.getConnection(dataSource, "EditGeolocaleAction.execute()");
          GeolocaleDb geolocaleDb = new GeolocaleDb(connection);              
        
		  Geolocale tempGeolocale = null;
		  if (id > 0) {
		    tempGeolocale = geolocaleDb.getGeolocale(id);
		  } else {
		    if ("adm1".equals(georank)) {
  		      tempGeolocale = geolocaleDb.getAdm1(name, parent);
		    } else {
  		      tempGeolocale = geolocaleDb.getGeolocale(name, georank);
            }
	      }

          s_log.debug("execute() name:" + name + " georank:" + georank + " tempGeolocale:" + tempGeolocale + " isSubmit:" + editGeolocaleForm.getIsSubmit() + " isUn:" + editGeolocaleForm.isUn());

          String message = null;
          boolean forwardToMessage = false;  

          if (editGeolocaleForm.getIsSubmit() || editGeolocaleForm.getIsCreate()) {
          
			s_log.debug("execute() name:" + name + " isPost:" + HttpUtil.isPost(request));
			if (editGeolocaleForm.getIsCreate()) {
			  if (editGeolocaleForm.getSource() == null) editGeolocaleForm.setSource("geolocaleMgr");
			  String createMessage = geolocaleDb.createGeolocale(editGeolocaleForm);
			  if ("success".equals(createMessage)) {
				Geolocale createGeolocale = null;
				if (!"adm1".equals(georank)) {
				  createGeolocale = geolocaleDb.getGeolocale(name, georank);
				} else {
				  createGeolocale = geolocaleDb.getAdm1(name, parent);
				  s_log.debug("execute() getAdm1(" + name + ", " + parent + ") createGeolocale:" + createGeolocale);
				}
				if (createGeolocale == null) {
				  forwardToMessage = true;
				  message = "Failed to fetch created geolocale:" + name  + " georank:" + georank + " form:" + editGeolocaleForm;
				} else {
				  id = createGeolocale.getId();
				  message = "Created: <a href='" + AntwebProps.getDomainApp() + "/editGeolocale.do?id=" + createGeolocale.getId() + "'>" + name + "</a>"; 
				}
			  } else {
				  forwardToMessage = true;
				  message = "Failed to create:" + name + ". " + createMessage;
			  }
			} else { // if not create
	   	      s_log.debug("execute() form:" + editGeolocaleForm);
			
			  message = geolocaleDb.updateGeolocale(editGeolocaleForm);
			  
			  //forwardToMessage = true;                  
			  message += " Updated.	";
			}
   	        if (!editGeolocaleForm.getIsFast()) {
			    GeolocaleMgr.populate(connection, true, false);               
            }		
          } else { // not submit
			
			//A.log("1 georank:" + georank + " fetchGeoData:" + editGeolocaleForm.getAction());            
			if (action != null) {
			  if ("removeFlickrData".equals(action)) {
				  message = " removeFlickrData adjust:" + editGeolocaleForm;
				  forwardToMessage = true;
				  geolocaleDb.setFlickrData(editGeolocaleForm.getId(), null, null, null, null);
			  } else {
				  if (!"country".equals(tempGeolocale.getGeorank())) {
					s_log.warn("execute() all of the actions, including:" + action + " should be run against countries.");
				  } else {
					if ("removeGeoData".equals(action)) {
						geolocaleDb.deleteFetchedAdm1(tempGeolocale, GeonamesPlace.source);
						geolocaleDb.deleteFetchedAdm1(tempGeolocale, FlickrPlace.source);
						message = "Flickr and Geonames data removed from country:" + tempGeolocale.getName();
					}
					if ("fetchGeoData".equals(action)) {
					  message = GeonamesPlace.fetchData(connection, tempGeolocale.getName());
					  message += " " + FlickrPlace.fetchAdm1Data(connection, tempGeolocale.getName());
					}
					if ("fetchFlickrCountryData".equals(action)) {
					  message = FlickrPlace.fetchCountryData(connection, tempGeolocale.getName());
					  //A.log("execute() fetchFlickrCountryData name:" + tempGeolocale.getName() + " message:" + message);
					}
					if ("fetchGeonamesData".equals(action)) {
					  message = GeonamesPlace.fetchData(connection, tempGeolocale.getName());  
					}
					if ("fetchFlickrData".equals(action)) {
					  message = FlickrPlace.fetchAdm1Data(connection, tempGeolocale.getName());
					}

					//if (action != null && action.contains("fetch")) {
					//    forwardToMessage = true;
					//	AntwebMgr.populate(connection, true);   // Maybe reload GeolocaleMgr?
					//}
			
				  }
			  }
			}
			
		  } // end if submit
		
		  if (editGeolocaleForm.getIsDelete()) {
		    Geolocale deleteGeo = geolocaleDb.getGeolocale(id);
		  
		    boolean success = geolocaleDb.deleteGeolocale(id);
		    if (!success) {
		 	  message = "Failed to delete:" + name;  
		    } else {
		  	  message = "Deleted:" + deleteGeo.getName();
		    }
		    forwardToMessage = true;
		  }


		  if (forwardToMessage) {
			//AntwebMgr.populate(connection, true);  
			String georankParam = "";
			if (georank != null) georankParam = "?georank=" + georank;
			message += "<br><br><< Back to <a href='" + AntwebProps.getDomainApp() + "/geolocaleMgr.do" + georankParam + "'>Geolocale Manager</a>";
			request.setAttribute("message", message);
			return mapping.findForward("message");
		  }

		  Geolocale geolocale = null;
		  if (id != 0) {
		    geolocale = geolocaleDb.getGeolocale(id);
		  } else {            
		    if ("adm1".equals(georank)) {
		  	  geolocale = geolocaleDb.getAdm1(name, parent);
		    } else {
	  	  	  geolocale = geolocaleDb.getGeolocale(name, georank);
		    }            
		  }            
		
		
		  if (message != null) {
		    request.setAttribute("message", message);
		    s_log.debug("EditGeolocaleAction message:" + message);
		  }
		
		  if (geolocale == null) {
		    message = " id:" + id + " name:" + name + " georank:" + georank + " action:" + action;
		    s_log.warn("execute() Error creating " + message );
			message = "<br><br>Error." + message;
			request.setAttribute("message", message);
			return mapping.findForward("message");
		  }

		  ArrayList<Geolocale> validChildren = geolocaleDb.getValidChildren(geolocale.getParent());
		  request.setAttribute("validChildren", validChildren);		
		  request.setAttribute("geolocale", geolocale);
		  request.setAttribute("form", editGeolocaleForm);
		  return mapping.findForward("success");

		} catch (SQLException | ClassCastException e) {
			s_log.error("execute() e:" + e);
		} finally {
			DBUtil.close(connection, this, "EditGeolocaleAction.execute()");
		}

        return mapping.findForward("failure");
    }
}
