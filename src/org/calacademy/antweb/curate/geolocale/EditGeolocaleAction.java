package org.calacademy.antweb.curate.geolocale;

import java.util.*;
import java.io.*;
import java.sql.*;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class EditGeolocaleAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(EditGeolocaleAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        HttpSession session = request.getSession();
        HttpUtil.setUtf8(request, response);       

        ActionForward c = Check.curator(request, mapping); if (c != null) return c;
		Login accessLogin = LoginMgr.getAccessLogin(request);

        EditGeolocaleForm editGeolocaleForm = (EditGeolocaleForm) form;
        if (editGeolocaleForm == null) return mapping.findForward("startFresh");
        
        String name = editGeolocaleForm.getName();
        String georank = editGeolocaleForm.getGeorank();
        
		String parent = editGeolocaleForm.getParent();

		String country = editGeolocaleForm.getCountry();

		int id = editGeolocaleForm.getId();
        
        s_log.debug("execute() name:" + name + " country:" + country + " parent:" + parent + " id:" + id + " isFast:" + editGeolocaleForm.getIsFast());

        if ((name == null || "".equals(name)) && editGeolocaleForm.getIsCreate()) {
			request.setAttribute("message", "Enter an Adm1 Name in the URL bar...");
			return mapping.findForward("message");
        }
     
        if (name == null && id == 0) {
            return mapping.findForward("startFresh");
        }

        // Deleting a geolocale (a button below the form): administrators only.
        boolean isAdminRequest = editGeolocaleForm.getIsDelete();
        if (isAdminRequest && !LoginMgr.isAdmin(accessLogin)) {
            request.setAttribute("message", "Only an AntWeb administrator may do this.");
            return mapping.findForward("message");
        }
        // Anything that changes data (save, create, delete) must come from
        // a form (POST), so the session's form token (util/Csrf, checked by SessionRequestFilter) is
        // required. A GET only shows the form.
        boolean isChange = editGeolocaleForm.getIsSubmit() || editGeolocaleForm.getIsCreate() || isAdminRequest;
        if (isChange && !HttpUtil.isPost(request)) {
            s_log.warn("execute() refused a change by GET. id:" + id + " login:" + (accessLogin == null ? "-" : accessLogin.getId()));
            request.setAttribute("message", "Nothing was changed: use the buttons on the Edit Geolocale page.");
            return mapping.findForward("message");
        }
        // Every setting on this form is structural (name, codes, rank, parent, bioregion, valid and live
        // flags, centroid and bounding box, admin notes); the page text has its own editor on the page. So saving and
        // creating are for administrators only; curators can still open the form to read the settings.
        if (isChange && !LoginMgr.isAdmin(accessLogin)) {
            s_log.warn("execute() refused a change by a non-admin. id:" + id + " login:" + (accessLogin == null ? "-" : accessLogin.getId()));
            request.setAttribute("message", "Only an AntWeb administrator may change countries and regions. Nothing was changed.");
            return mapping.findForward("message");
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
            boolean isSaved = true;
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
				  message = "Failed to fetch created geolocale:" + Html.esc(name)  + " georank:" + Html.esc(georank) + " form:" + Html.esc(editGeolocaleForm);
				} else {
				  id = createGeolocale.getId();
				  message = "Created: <a href='" + AntwebProps.getDomainApp() + "/editGeolocale.do?id=" + createGeolocale.getId() + "'>" + Html.esc(name) + "</a>"; 
				}
			  } else {
				  forwardToMessage = true;
				  message = "Failed to create:" + Html.esc(name) + ". " + Html.esc(createMessage);
			  }
			} else { // if not create
	   	      s_log.debug("execute() form:" + editGeolocaleForm);

			  String coordsError = checkCoords(editGeolocaleForm, tempGeolocale);
			  if (coordsError != null) {
			    isSaved = false;
			    // Nothing is saved; the form is shown again with what was typed.
			    message = "Nothing was saved. " + Html.esc(coordsError);
			    request.setAttribute("centroidInput", request.getParameter("centroidFixed"));
			    request.setAttribute("boundingBoxInput", request.getParameter("boundingBoxFixed"));
			  } else {
			    message = geolocaleDb.updateGeolocale(editGeolocaleForm);
			    // A rename onto a name that already has page text is refused (GeolocaleDb.updateGeolocale).
			    if (message != null && message.startsWith(GeolocaleDb.NOT_SAVED)) {
			      isSaved = false;
			    } else {
			      message += " Updated.";
			    }
			  }
			}
   	        if (isSaved && !editGeolocaleForm.getIsFast()) {
			    GeolocaleMgr.populate(connection, true, false);
            }
			
          }
		
		  if (editGeolocaleForm.getIsDelete()) {
		    Geolocale deleteGeo = geolocaleDb.getGeolocale(id);
		  
		    boolean success = geolocaleDb.deleteGeolocale(id);
		    if (!success) {
		 	  message = "Failed to delete:" + Html.esc(name);  
		    } else {
		  	  message = "Deleted:" + Html.esc(deleteGeo.getName());
		    }
		    forwardToMessage = true;
		  }


		  if (forwardToMessage) {
			//AntwebMgr.populate(connection, true);  
			String georankParam = "";
			if (georank != null) georankParam = "?georank=" + Html.escAttr(Html.url(georank));
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
		    message = " id:" + id + " name:" + Html.esc(name) + " georank:" + Html.esc(georank);
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
    /**
     * The centroid and bounding box fields are shown filled with the values the site uses now
     * (useCentroid(), useBoundingBox()) and are saved into centroid_fixed and bounding_box_fixed.
     * A field left as shown is not saved, so nothing changes for it. A changed field is checked
     * (numbers, latitude -90..90, longitude -180..180, south < north, west < east unless the box crosses
     * longitude 180) and saved in the tidy form; an emptied field clears the fixed value.
     * Returns an error message, or null when both fields are fine.
     */
    static String checkCoords(EditGeolocaleForm form, Geolocale current) {
        try {
            String centroid = form.getCentroidFixed();
            if (centroid != null) {
                String now = current == null ? null : current.useCentroid();
                form.setCentroidFixed(isUnchanged(centroid, now, true) ? null : GeoCoords.normalizeCentroid(centroid));
            }
            String box = form.getBoundingBoxFixed();
            if (box != null) {
                String now = current == null ? null : current.useBoundingBox();
                form.setBoundingBoxFixed(isUnchanged(box, now, false) ? null : GeoCoords.normalizeBoundingBox(box));
            }
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        return null;
    }

    private static boolean isUnchanged(String typed, String now, boolean isCentroid) {
        String t = typed.trim();
        String n = (now == null || "null".equals(now)) ? "" : now.trim();
        if (t.equals(n)) return true;
        if (t.isEmpty() || n.isEmpty()) return false;
        try {
            return isCentroid
                ? GeoCoords.normalizeCentroid(t).equals(GeoCoords.normalizeCentroid(n))
                : GeoCoords.normalizeBoundingBox(t).equals(GeoCoords.normalizeBoundingBox(n));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
