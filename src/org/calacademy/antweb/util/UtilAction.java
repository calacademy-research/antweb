package org.calacademy.antweb.util;

import java.io.*;
import java.util.*;

import jakarta.servlet.http.*;

//import org.apache.fop.apps.Driver;
//import org.apache.fop.apps.FOPException;
//import org.apache.fop.messaging.MessageHandler;

import org.apache.struts.action.*;

import java.sql.*;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;


public class UtilAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(UtilAction.class);

    // Every sub-action util.do handles. Anything else is answered with 404 before any other check
    // (cleanup batch 3 removed 18 developer tests and reports that duplicated query.do).
    static final Set<String> ACTIONS = Set.of(
        "postInstantiate", "isRestart", "reloadAntwebMgr", "logQueryStats", "emailTest");

    static final Set<String> CHANGING_ACTIONS = Set.of(
        "reloadAntwebMgr", "emailTest", "logQueryStats");
    
    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) {

		UtilForm theForm = (UtilForm) form;
		String action = theForm.getAction();

		ActionForward forward = null;

		if (action == null || !ACTIONS.contains(action)) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			request.setAttribute("message", "action not found:" + Html.esc(action));
			return mapping.findForward("message");
		}

		// Everything here is an admin tool, except isRestart (polled by the host's
		// reboot_checker.sh) and postInstantiate (AntWeb's own call after startup).
		if (!"isRestart".equals(action) && !"postInstantiate".equals(action) && !LoginMgr.isAdmin(request)) {
			request.setAttribute("message", "Administrators only.");
			return mapping.findForward("message");
		}

		// postInstantiate: AntwebMgr calls it (without a login) after loading the managers and
		// passes its own secret code. Anyone else gets a refusal, so it can not be used to reload
		// the managers on demand.
		if ("postInstantiate".equals(action) && !LoginMgr.isAdmin(request)
		    && !AntwebMgr.isPostInitializeCode(theForm.getSecureCode())) {
			request.setAttribute("message", "Administrators only.");
			return mapping.findForward("message");
		}

		// Actions that change state run only by POST from a browser (CSRF-checked);
		// a GET link shows a Run button instead.
		if (CHANGING_ACTIONS.contains(action)) {
			ActionForward confirm = PostOnly.confirm(request, mapping, "Run admin utility: " + action
			    + (theForm.getName() == null ? "" : " " + theForm.getName()));
			if (confirm != null) return confirm;
		}

		String[] longActions = new String[]{"postInstantiate", "reloadAntwebMgr"};
		if (Arrays.asList(longActions).contains(action)) {
			forward = doLongAction(mapping, form, request, response);
		} else {
			forward = doAction(mapping, form, request, response);
		}
	    return forward;
    }

	private ActionForward doLongAction(ActionMapping mapping, ActionForm form,
								   HttpServletRequest request, HttpServletResponse response) {


		HttpSession session = request.getSession();
		HttpUtil.setUtf8(request, response);

		Connection connection = null;
		ActionForward returnLoc = null;

		UtilForm theForm = (UtilForm) form;
		String action = theForm.getAction();
		String param = theForm.getParam();
		String param2 = theForm.getParam2();
		int num = theForm.getNum();
		String name = theForm.getName();

		//A.log("execute() name:" + name + " param:" + param);
		String dbMethodName = DBUtil.getDbMethodName("UtilAction.execute()");
		try {

			DataSource dataSource = getDataSource(request, "longConPool");
			connection = DBUtil.getConnection(dataSource, dbMethodName);

			if (action != null) {
				if (action.equals("postInstantiate")) {
					AntwebMgr.postInitialize(connection);
					return null;
				}


				if (action.equals("reloadAntwebMgr") && !LoginMgr.isAdmin(request)) {
					request.setAttribute("message", "Administrators only.");
					return mapping.findForward("message");
				}
				if (action.equals("reloadAntwebMgr")) {
					boolean forceReload = true;
					if ("group".equals(name)) {
						GroupMgr.populate(connection, forceReload);
						request.setAttribute("message", "GroupMgr Reloaded.");
					} else if ("allAntweb".equals(name)) {
						AllAntwebMgr.populate(connection);
						request.setAttribute("message", "AllAntwebMgr Reloaded.");
					} else if ("login".equals(name)) {
						LoginMgr.populate(connection, forceReload, true);
						request.setAttribute("message", "LoginMgr Reloaded.");
					} else if ("project".equals(name)) {
						ProjectMgr.populate(connection, forceReload);
						request.setAttribute("message", "ProjectMgr Reloaded.");
					} else if ("bioregion".equals(name)) {
						BioregionMgr.populate(connection, forceReload);
						request.setAttribute("message", "BioregionMgr Reloaded.");
					} else if ("museum".equals(name)) {
						MuseumMgr.populate(connection, forceReload);
						request.setAttribute("message", "MuseumMgr Reloaded.");
					} else if ("geolocale".equals(name)) {
						GeolocaleMgr.populate(connection, forceReload, true);  // Slow!
						request.setAttribute("message", "GeolocaleMgr Reloaded.");
					} else if ("taxonProp".equals(name)) {
						TaxonPropMgr.populate(connection, forceReload);
						request.setAttribute("message", "TaxonPropMgr Reloaded.");
					} else if ("taxon".equals(name)) {
						TaxonMgr.populate(connection, forceReload, true);
						request.setAttribute("message", "TaxonMgr Reloaded.");
					} else if ("upload".equals(name)) {
						UploadMgr.populate(connection, forceReload);
						request.setAttribute("message", "UploadMgr Reloaded.");
					} else if ("artist".equals(name)) {
						ArtistMgr.populate(connection, forceReload, true);
						request.setAttribute("message", "ArtistMgr Reloaded.");
					} else if ("adminAlert".equals(name)) {
						AdminAlertMgr.populate(connection);
						request.setAttribute("message", "AdminAlertMgr Reloaded.");
					} else {
						AntwebMgr.populate(connection, true);
						request.setAttribute("message", "AntwebMgr Reloaded.");
					}
					returnLoc = mapping.findForward("message");
					return returnLoc;
				}

				if (returnLoc != null) return returnLoc;
			}
			request.setAttribute("message", "action not found:" + Html.esc(action));
			return mapping.findForward("message");

		} catch (SQLException | IOException e) {
			s_log.error("execute() action:" + action + " e:" + e);
			AntwebUtil.errorStackTrace(e);
		} finally {
			DBUtil.close(connection, this, dbMethodName);
		}

		//this shouldn't happen in this example
		s_log.error("execute()  This should not happen");
		return mapping.findForward("failure");
	}


    private ActionForward doAction(ActionMapping mapping, ActionForm form,
	    HttpServletRequest request, HttpServletResponse response) {


		HttpSession session = request.getSession();
		HttpUtil.setUtf8(request, response);

		Connection connection = null;
		ActionForward returnLoc = null;

		UtilForm theForm = (UtilForm) form;
		String action = theForm.getAction();
		String param = theForm.getParam();
		String param2 = theForm.getParam2();
		int num = theForm.getNum();
		String name = theForm.getName();

        //A.log("execute() name:" + name + " param:" + param);
		String dbMethodName = DBUtil.getDbMethodName("UtilAction.execute()");
        try {

            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);

			if (action != null) {

			  if (action.equals("logQueryStats")) {
				DBUtil.logQueryStats();
				String message = "Query stats logged to queryStats.log. <a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=queryStatsLog'>Query Stats Log</a>";
				request.setAttribute("message", message);
				returnLoc = mapping.findForward("adminMessage");
			  }
			  if (action.equals("emailTest") && !LoginMgr.isAdmin(request)) {
			    request.setAttribute("message", "Administrators only.");
			    return mapping.findForward("message");
			  }
			  if (action.equals("emailTest")) {
			    String recipients = AntwebUtil.getDevEmail(); // + ", " + AntwebUtil.getAdminEmail();
			    String subject = "Test Message from Antweb";
			    String body = "This is a test message sent from the antweb server.";
				Emailer.sendMail(recipients, subject, body);
				String message = "Message sent";                    
				request.setAttribute("message", message);
				returnLoc = mapping.findForward("adminMessage");
			  }

			  if (action.equals("isRestart")) {
				  String message = "false";
				  if (AntwebSystem.isRestart()) message = "true";
				  request.setAttribute("message", message);
				  returnLoc = mapping.findForward("message");
			  }

			  if (returnLoc != null) return returnLoc;
			}               
			request.setAttribute("message", "action not found:" + Html.esc(action));
			return mapping.findForward("message");

        } catch (SQLException e) {
            s_log.error("execute() action:" + action + " e:" + e);
            AntwebUtil.errorStackTrace(e);
		} finally {
            DBUtil.close(connection, this, dbMethodName);
        }

        //this shouldn't happen in this example
        s_log.error("execute()  This should not happen");
        return mapping.findForward("failure");
    }
        
}


