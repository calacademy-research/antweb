package org.calacademy.antweb.util;

import java.util.*;

import javax.servlet.http.*;

import org.apache.struts.action.*;

import java.sql.*;
import javax.sql.DataSource;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;

public class QueryAction extends Action {

    private static final Log s_log = LogFactory.getLog(QueryAction.class);
    
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

			if (action == null && name == null) {
                
                ActionForward c = Check.admin(request, mapping); if (c != null) return c;
                
                String message = Queries.getQueryManagerPage();
                request.setAttribute("message", message);
                returnLoc = mapping.findForward("unboldMessage");
            } else {
            
                if (action == null) action = "query";
                        
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

                if (action.equals("curateAntcat")) {
                  String message = QueryManager.runCurateAntcatQueries(connection);
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
            
                // utilAction.do?action=descEdit&field=notes
                if (action.equals("descEdit")) { 
                  String field = theForm.getField();
          
                  String query = "select taxon_name, title, content from description_edit where title = \"" + field + "\"";
                  String message = QueryManager.getQueryResults(connection, query);
                  request.setAttribute("message", message);
                  returnLoc = mapping.findForward("adminMessage");
                }


                if (action.equals("countryEndemic")) { 
                  String field = theForm.getField();
              
                  String query = "select taxon_name from geolocale_taxon where geolocale_id = " + field + " and is_endemic = 1";
                  //String query = "select gt.taxon_name taxon_name from geolocale g, geolocale_taxon gt, taxon t  where g.id = gt.geolocale_id and t.taxon_name = gt.taxon_name  and t.status != 'morphotaxon' and t.rank in ('species', 'subspecies') and g.georank = 'country'  and is_introduced = 0 and geolocale_id = " + field + " group by taxon_name, gt.geolocale_id having count(*) = 1 order by gt.taxon_name";
                  //String query = "select gt.taxon_name, is_introduced, is_endemic from geolocale_taxon gt, taxon t where gt.taxon_name = t.taxon_name and t.rank in ('species', 'subspecies') and geolocale_id = " + field + " and (is_introduced = 1 or is_endemic = 0)";
                  String message = QueryManager.getQueryResults(connection, query);
                  request.setAttribute("message", message);
                  returnLoc = mapping.findForward("adminMessage");
                }

                if (action.equals("homonyms")) {
                  HomonymDb homonymDb = new HomonymDb(connection);
                  ArrayList<Taxon> homonyms = homonymDb.getHomonyms();
                  //s_log.error("execute()  homonyms:" + homonyms);
                  request.setAttribute("homonyms", homonyms);
                  returnLoc = mapping.findForward("homonyms");
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
			  s_log.debug("execute() end name:" + name + " param:" + param);

			  return returnLoc;
			}
			request.setAttribute("message", "action not found:" + action);
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
