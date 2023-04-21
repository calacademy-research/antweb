package org.calacademy.antweb.util;


import javax.sql.DataSource;
import java.sql.*;

import javax.servlet.http.*;

import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.*;

public class Check {

    private static final Log s_log = LogFactory.getLog(Check.class);

    public Check() {
    }


// ------------ General Purpose Checks ----------------
/* Check mostly has to do with verifying the status of the AntwebMgr startup procedure.
     Certain services must be up to support certain operations.
     These are more general services...
*/  
    // ActionForward a = Check.notBot(request, mapping); if (a != null) return a;
    public static ActionForward notBot(HttpServletRequest request, ActionMapping mapping) {
        if (HttpUtil.isBot(request)) {
            request.setAttribute("message", "no bots allowed");
            return mapping.findForward("message");
        }
        return null;
    }

    // For use in JSP files. For example with a request object fetched like overview...
    // String e = Check.requestAttribute(request, "overview"); if (e != null) { out.println("<br><br><b>Error:" + e + "</b>"); return; }
    // Could be above or below the fetch. But above makes more sense. See web/endemic-body.jsp.
    public static String requestAttribute(HttpServletRequest request, String objectName) {
		Object object = request.getAttribute(objectName);
		if (object == null) {
		  String message = "requestAttribute() No object:" + objectName + " for request:" + HttpUtil.getRequestInfo(request);
		  s_log.error("requestAttribute() message:" + message);
		  return message;
		}
		return null;
    }

// ----------------------------------------------------    

    // ActionForward a = Check.initLogin(request, mapping); if (a != null) return a;
    public static ActionForward initLogin(HttpServletRequest request, ActionMapping mapping) {
        ActionForward a = null;
        a = Check.init(request, mapping); if (a != null) return a;
        a = Check.login(request, mapping);
        return a;
    }

    // ActionForward a = Check.loginValid(request, mapping); if (a != null) return a;
    public static ActionForward loginValid(HttpServletRequest request, ActionMapping mapping) {
        ActionForward a = null;
        a = Check.login(request, mapping); if (a != null) return a;
        a = Check.valid(request, mapping);
        return a;
    }

    // ActionForward a = Check.initLoginValid(request, mapping); if (a != null) return a;
    public static ActionForward initLoginValid(HttpServletRequest request, ActionMapping mapping) {
        ActionForward a = null;
        a = Check.initLogin(request, mapping); if (a != null) return a;
        a = Check.valid(request, mapping);
        return a;
    }

    /*
    // ActionForward a = Check.initLoginValidbusy(getDataSource(request, "conPool"), request, mapping); if (a != null) return a;
    public static ActionForward initLoginValidBusy(DataSource dataSource, HttpServletRequest request, ActionMapping mapping) {
        ActionForward a = Check.initLoginValid(request, mapping);
        if (a != null) return a;
        a = Check.busy(dataSource, request, mapping);
        return a;
    }
*/

    // --------------------------------------------------------------------------------------

    /*
      If multiple checks in an action class, include them in the order presented here.
      So that if not initialized, that is the first response, if not logged in, etc...
    */


    // ActionForward a = Check.init(request, mapping); if (a != null) return a;
    public static ActionForward init(HttpServletRequest request, ActionMapping mapping) {
      return AntwebMgr.isInitializing(request, mapping);
    }

    // ActionForward a = Check.init(Check.GEOLOCALE, request, mapping); if (a != null) return a;
    public static final String LOGIN = "login";
    public static final String PROJECT = "project";
    public static final String BIOREGION = "bioregion";
    public static final String GEOLOCALE = "geolocale";
    public static final String MUSEUM = "museum";
    public static final String TAXON = "taxon";
    public static final String TAXONPROP = "taxonProp";
    public static final String ARTIST = "artist";
    public static final String UPLOAD = "upload";
    public static final String ADMINALERT = "adminAlert";

    // To be used by servlets (non-Struts).
    public static String init(String manager) {
      if (AntwebMgr.isServerInitializing(manager)) {
          String message = "Server is initializing the " + Formatter.initCap(manager) + " Manager.";
          return message;      
      }
      return null; // no message means that it works.
    }
    
    public static ActionForward init(String manager, HttpServletRequest request, ActionMapping mapping) {
      if (AntwebMgr.isServerInitializing(manager)) {
          request.setAttribute("message", "Server is initializing the " + Formatter.initCap(manager) + " Manager.");
          return mapping.findForward("message");       
      }
      return null;
    }

    // ActionForward b = Check.busy(connection, request, mapping); if (b != null) return b;
    public static ActionForward busy(Connection connection, HttpServletRequest request, ActionMapping mapping) {
        if (connection == null) s_log.error("busy() connection null");
        try {
            if (DBStatus.isServerBusy(connection)) {
                String message = DBStatus.getServerBusyReport();
                request.setAttribute("message", message);
                return mapping.findForward("message");
            }
        } catch (SQLException e) {
            request.setAttribute("message", e.toString());
            return mapping.findForward("message");
        }
        return null;
    }

    /*
    // Avoid
    // ActionForward b = Check.busy(getDataSource(request, "conPool"), request, mapping); if (b != null) return b; 
    public static ActionForward Xbusy(DataSource dataSource, HttpServletRequest request, ActionMapping mapping) {
      try {
        if (DBStatus.isServerBusy(dataSource)) {
          String message = DBStatus.getServerBusyReport();
          request.setAttribute("message", message);
          return mapping.findForward("message");            
        }	
      } catch (SQLException e) {
          request.setAttribute("message", e.toString());
          return mapping.findForward("message");                  
      }
      return null;
    }
    */

    // ActionForward c = Check.login(request, mapping); if (c != null) return c;
    public static ActionForward login(HttpServletRequest request, ActionMapping mapping) {
      return LoginMgr.mustLogIn(request, mapping);
    }

    // ActionForward c = Check.admin(request, mapping); if (c != null) return c;
    public static ActionForward admin(HttpServletRequest request, ActionMapping mapping) {
      return LoginMgr.mustBeAdmin(request, mapping);
    }

    // ActionForward c = Check.curator(request, mapping); if (c != null) return c;
    public static ActionForward curator(HttpServletRequest request, ActionMapping mapping) {
        return LoginMgr.mustBeCurator(request, mapping);
    }

    // ActionForward d = Check.valid(request, mapping); if (d != null) return d;
    public static ActionForward valid(HttpServletRequest request, ActionMapping mapping) {
      return HttpUtil.invalidRequest(request, mapping);
    }

    public static void adminTest(HttpServletRequest request, ActionMapping mapping) {
        Scheduler.set1Test();
    }

    // e is sometimes used for exceptions. We will skip.

    // ActionForward f = Check.upload(request, mapping); if (f != null) return f;
    public static ActionForward upload(HttpServletRequest request, ActionMapping mapping) {
        if (UploadAction.isInUploadProcess()) {
            // An upload is currently in process.  Request that this process be re-attempted shortly.
            String message = "A curator is currently in the process of an Upload.  Please try again shortly.";
            request.setAttribute("message", message);
            return mapping.findForward("message");
        }
        return null;
    }

    // ActionForward g = Check.compute(request, mapping); if (g != null) return g;
    public static ActionForward compute(HttpServletRequest request, ActionMapping mapping) {
        if (UtilDataAction.isInComputeProcess()) {
            String message = "Antweb is currently in it's re-computation process.  Please try again shortly.";
            request.setAttribute("message", message);
            return mapping.findForward("message");
        }
        return null;
    }    
}

/*
For convenience:

    ActionForward a = Check.init(request, mapping); if (a != null) return a;
    ActionForward b = Check.busy(getDataSource(request, "conPool"), request, mapping); if (b != null) return b; 
    ActionForward c = Check.login(request, mapping); if (c != null) return c;
    ActionForward d = Check.valid(request, mapping); if (d != null) return d;        

    Login accessLogin = LoginMgr.getAccessLogin(request);
    Group accessGroup = GroupMgr.getAccessGroup(request);

*/

