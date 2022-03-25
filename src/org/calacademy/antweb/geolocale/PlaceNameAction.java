package org.calacademy.antweb.geolocale;

import java.io.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.*;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class PlaceNameAction extends Action {

    private static final Log s_log = LogFactory.getLog(PlaceNameAction.class);
    
    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        if (!GeolocaleMgr.isInitialized()) {
		  request.setAttribute("message", "Server Initializing...");
		  return mapping.findForward("message");
        }

        OverviewForm overviewForm = (OverviewForm) form;

        String uri = HttpUtil.getRequestURI(request);

        String placeName = overviewForm.getPlaceName();  
        if (placeName == null) {
		  request.setAttribute("message", "Must enter a place name for the search.");
		  return mapping.findForward("message");		              
        }

        if (!Formatter.containsUppercase(placeName)) {
          placeName = Formatter.initCap(placeName);    
        }

        if (uri.contains("/place.do")) {            

          if ("Florida".equals(placeName)) placeName = "Florida, United States";

		  Geolocale geolocale = GeolocaleMgr.getGeolocale(placeName);
          s_log.debug("execute() placeName:" + placeName + " geolocale:" + geolocale);
		  if (geolocale == null) {
		    int commaPos = placeName.indexOf(", ");
		    if (commaPos > 0) {
              String adm1 = placeName.substring(0, commaPos);
              String country = placeName.substring(commaPos + 2);
  		      geolocale = GeolocaleMgr.getAdm1(adm1, country);		    
		    }
	      }
          if (geolocale != null) {	  
            String target = geolocale.getThisPageTarget();

            //HttpUtil.getUrl(target); // Is this needed? Guess not.
            
            HttpUtil.sendRedirect(target, request, response);
            return null;
          } else {
			  request.setAttribute("message", "Place not found:" + placeName);
			  return mapping.findForward("message");		              
          }
        }

      // This will not happen
      return null;
    }
}
