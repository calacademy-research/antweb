package org.calacademy.antweb.util;

import java.util.*;

import jakarta.servlet.http.*;

import org.apache.struts.action.*;

import java.sql.*;
import javax.sql.DataSource;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;

public class QueryAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(QueryAction.class);

    // homonyms: the Homonym Report (moved here from util.do, where it was admin only; its page needs a login).
    // curateAntcat was removed 2026-10-19: its checks are in the Integrity Check (checkIntegrity).
    // countryEndemic was removed 2026-10-19: the public /endemic.do page lists the same taxa.
    // taxaOutsideOfNativeBioregion (2026-10-19, from utilData.do): a read-only report, so no
    // "Run data job" confirmation.
    private static final Set<String> ADMIN_ACTIONS = Set.of(
        "checkIntegrity", "curiousQueries", "devQueries", "events",
        "homonyms", "taxaOutsideOfNativeBioregion");

    // Every sub-action query.do handles; anything else (descEdit, the old "Notes" link) is not found.
    static final Set<String> ACTIONS = Set.of(
        "checkIntegrity", "curiousQueries", "devQueries", "events",
        "homonyms", "taxaOutsideOfNativeBioregion", "curiousQuery", "query", "queryBattery");

    // Named queries that public pages link to (read-only, no login data): anyone may run them.
    static final Set<String> PUBLIC_QUERIES = Set.of(
        "worldantsUploads",                    // AntCat import history, linked from the upload reports
        "bioregionSpeciesListWithRangeData",   // bioregion species list page, "Species List with Range Data"
        "imageData");                          // "Download All" on the recent images search results
    // Named queries that pages show to curators (country and adm1 species lists).
    static final Set<String> CURATOR_QUERIES = Set.of("geolocaleSpeciesListWithRangeData");

    /** Who may run a named query: "public", "curator" or (every other query) "admin". */
    /** The page title of a result page of this action (plain text; the layout escapes it). */
    static String pageTitle(String action, String name) {
        String n = name == null ? "" : name;
        switch (action == null ? "query" : action) {
            case "checkIntegrity": return "Integrity Check - AntWeb";
            case "curiousQueries": return "Curious queries - AntWeb";
            case "devQueries": return "Development queries - AntWeb";
            case "queryBattery": return "Query battery: " + n + " - AntWeb";
            case "homonyms": return "Homonym Report - AntWeb";
            case "events": return "Curator activity log - AntWeb";
            case "taxaOutsideOfNativeBioregion": return "Taxa outside their native bioregions - AntWeb";
            default: return "Query: " + n + " - AntWeb";
        }
    }

    public static String namedQueryAccess(String name) {
        if (name != null && PUBLIC_QUERIES.contains(name)) return "public";
        if (name != null && CURATOR_QUERIES.contains(name)) return "curator";
        return "admin";
    }
    
    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) {
      
        ActionForward b = Check.notBot(request, mapping); if (b != null) return b;
      
        response.setCharacterEncoding("UTF-8");
      
        HttpSession session = request.getSession();                
        Connection connection = null;
		ActionForward returnLoc = null;                

		UtilForm theForm = (UtilForm) form;
		String action = theForm.getAction();
        String param = theForm.getParam();
		int num = theForm.getNum();
		String name = theForm.getName();

        s_log.debug("execute() start action:" + action + " name:" + name + " param:" + param);
        String dbMethodName = DBUtil.getDbMethodName("QueryAction.execute()");

        try {
            DataSource dataSource = getDataSource(request, "longConPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);

			if (action != null && !ACTIONS.contains(action)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                request.setAttribute("message", "action not found:" + Html.esc(action));
                return mapping.findForward("message");
			}

			if (action == null && name == null) {
                
                ActionForward c = Check.admin(request, mapping); if (c != null) return c;
                
                String message = Queries.getQueryManagerPage();
                request.setAttribute("message", message);
                request.setAttribute("header", "Query Manager");   // page title (otherwise "message...")
                returnLoc = mapping.findForward("unboldMessage");
            } else {
            
                if (action == null) action = "query";

                // Maintenance batteries and the events list (curator logins) are admin tools. Named queries
                // are admin tools too, except the few that public or curator pages link to (namedQueryAccess).
                boolean isNamedQuery = "curiousQuery".equals(action) || "query".equals(action) || "queryBattery".equals(action);
                String access = isNamedQuery && !"queryBattery".equals(action) ? namedQueryAccess(name) : "admin";
                if (ADMIN_ACTIONS.contains(action) || (isNamedQuery && "admin".equals(access))) {
                    ActionForward c = Check.admin(request, mapping); if (c != null) return c;
                } else if (isNamedQuery && "curator".equals(access)) {
                    ActionForward c = Check.curator(request, mapping); if (c != null) return c;
                }
                        
                if (action.equals("checkIntegrity")) {
                  String message = QueryManager.checkIntegrity(connection);
                  request.setAttribute("message", message);
                  returnLoc = mapping.findForward("adminHtmlMessage");
                }

                if (action.equals("curiousQueries")) {
                  String message = QueryManager.curiousQueries(connection);
                  request.setAttribute("message", message);
                  returnLoc = mapping.findForward("adminHtmlMessage");
                }

                if (action.equals("devQueries")) {
                  String message = QueryManager.runDevIntegrityQueries(connection);
                  request.setAttribute("message", message);
                  returnLoc = mapping.findForward("adminHtmlMessage");
                }


                if (action.equals("curiousQuery") || action.equals("query")) {
                  if (name == null || "".equals(name)) {
                    request.setAttribute("message", "Enter query name into url bar.");
                    return mapping.findForward("message");
                  }
              
                  String message = QueryManager.curiousQuery(name, connection);
                  request.setAttribute("message", message);
                  returnLoc = mapping.findForward("adminHtmlMessage");
                }

                if (action.equals("queryBattery")) {
                  if (name == null || "".equals(name)) {
                    request.setAttribute("message", "Enter battery name into url bar.");
                    return mapping.findForward("message");
                  }
              
                  String message = QueryManager.queryBattery(name, connection);
                  request.setAttribute("message", message);
                  returnLoc = mapping.findForward("adminHtmlMessage");
                }
            
                if (action.equals("homonyms")) {
                  HomonymDb homonymDb = new HomonymDb(connection);
                  ArrayList<Taxon> homonyms = homonymDb.getHomonyms();
                  //s_log.error("execute()  homonyms:" + homonyms);
                  request.setAttribute("homonyms", homonyms);
                  returnLoc = mapping.findForward("homonyms");
                }

                if (action.equals("taxaOutsideOfNativeBioregion")) {
                  String report = new GeolocaleTaxonDb(connection).getTaxaOutsideOfNativeBioregion();
                  request.setAttribute("message", "<h2>Taxa outside their native bioregions</h2>" + ReportHelp.TAXA_OUTSIDE + report);
                  returnLoc = mapping.findForward("adminHtmlMessage");
                }

                if (action.equals("events")) {
                  ArrayList<Event> events = new EventDb(connection).getEvents();
                  request.setAttribute("events", events);
                  HashMap<Integer, Login> curators = new LoginDb(connection).getCuratorMap();
                  request.setAttribute("curators", curators);
                  //s_log.error("execute()  events:" + event);
                  returnLoc = mapping.findForward("events");
                }

                if ("query".equals(action) && name != null && param != null) {
                    ActionForward a = Check.init(Check.GEOLOCALE, request, mapping); if (a != null) return a;

                    String message = QueryManager.runQueryWithParamHtml(name, param, connection);
                    request.setAttribute("message", message);
                    returnLoc = mapping.findForward("adminHtmlMessage");
                }
			}

			if (returnLoc != null) {
			  // A real page title for the admin result pages (curate/adminHtmlMessage.jsp): without one the
			  // title was "message...", which reads as a message page (tests/e2e/antweb.py classify()).
			  if (request.getAttribute("header") == null) request.setAttribute("header", pageTitle(action, name));
			  s_log.debug("execute() end name:" + name + " param:" + param);

			  return returnLoc;
			}
			request.setAttribute("message", "action not found:" + Html.esc(action));
			return mapping.findForward("message");

        } catch (SQLException e) {
            s_log.error("execute() action:" + action + " e:" + e);
            //AntwebUtil.errorStackTrace(e);
            s_log.debug(AntwebUtil.getAntwebStackTrace(e));
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }

        //this shouldn't happen in this example
        s_log.error("execute()  This should not happen");
        return mapping.findForward("failure");
    }
    
}
