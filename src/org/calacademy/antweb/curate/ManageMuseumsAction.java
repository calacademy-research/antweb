package org.calacademy.antweb.curate;

import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import java.sql.*;
import java.util.ArrayList;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;


public class ManageMuseumsAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(ManageMuseumsAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) {
        // Require a logged-in administrator: this action changes data.
        ActionForward denied = LoginMgr.mustBeAdmin(request, mapping); if (denied != null) return denied;

        ArrayList<Museum> museumList = null;
        Museum museum = null;
        Connection connection = null;
        HttpSession session = request.getSession();

        DynaActionForm df = (DynaActionForm) form;        
        String museumCode = (String) df.get("code");   
        String museumName = (String) df.get("name");   
        String museumTitle = (String) df.get("title");   
           
        Boolean isActive = (Boolean) df.get("isActive"); 
        if (isActive == null) isActive = Boolean.FALSE;
        String action = (String) df.get("action");
        String step = (String) df.get("step");

        if (HttpUtil.getTarget(request).contains("viewMuseum")) action = "view";

        boolean isPost = HttpUtil.isPost(request);
        if (isPost) action = "save";

        String message = "";

        // Delete: only from the form's button (a POST, checked for the form token), and only after
        // a confirmation page. A link (GET) with step=delete deletes nothing.
        if ("delete".equals(step)) {
          if (!isPost) {
            action = "view";
            message = "To delete a museum, use the Delete button on its form.";
          } else if (!"yes".equals(request.getParameter("confirmDelete"))) {
            request.setAttribute("message", confirmDeleteHtml(request, museumCode));
            return mapping.findForward("message");
          } else {
            action = "delete";
          }
        }

        // Is it new?
        if ("save".equals(action) && museumCode == null) {
          return mapping.findForward("success");
        }

        s_log.debug("execute() action:" + action + " code:" + museumCode + " museumName:" + museumName + " isActive:" + isActive);
                            
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "ManageMuseumsAction");

            MuseumDb museumDb = new MuseumDb(connection);
            if ("delete".equals(action)) {
              message = museumDb.deleteByCode(museumCode);
              MuseumMgr.populate(connection, true);
            }

            if ("save".equals(action)) {
                museum = new Museum();
                museum.setCode(museumCode);
                museum.setName(museumName);
                museum.setTitle(museumTitle);
                museum.setIsActive(isActive);
                message = museumDb.saveMuseum(museum);
                MuseumMgr.populate(connection, true);
            }
            
            if ("view".equals(action)) {
                museum = museumDb.getMuseum(museumCode);                
            }
            
            if (museum == null) museum = new Museum();
            if (museum.getCode() == null) museum.setCode("");
            if (museum.getName() == null) museum.setName("");
            if (museum.getTitle() == null) museum.setTitle("");

            museumList = museumDb.getMuseums();
            //s_log.info("execute() groupList:" + groupList);          

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return mapping.findForward("error");
        } finally { 		
            DBUtil.close(connection, this, "ManageMuseumsAction");
        }

        if (museumList != null) request.setAttribute("museums", museumList);      
        if (museum != null) request.setAttribute("museum", museum);      
        if (message != null) request.setAttribute("message", message);
               
        return mapping.findForward("success");
    }

    /** The page that asks before a museum is deleted: a POST form with the form token. */
    static String confirmDeleteHtml(HttpServletRequest request, String code) {
        String domainApp = AntwebProps.getDomainApp();
        return "<div style=\"margin:1em 0\"><p>Delete museum <b>" + Html.esc(code) + "</b>? This cannot be undone.</p>"
            + "<form method=\"post\" action=\"" + domainApp + "/viewMuseum.do\">"
            + Csrf.field(request)
            + "<input type=\"hidden\" name=\"code\" value=\"" + Html.escAttr(code) + "\">"
            + "<input type=\"hidden\" name=\"step\" value=\"delete\">"
            + "<input type=\"hidden\" name=\"confirmDelete\" value=\"yes\">"
            + "<input type=\"submit\" value=\"Delete museum\"> &nbsp; "
            + "<a href=\"" + domainApp + "/viewMuseum.do?code=" + Html.escAttr(Html.url(code)) + "\">Cancel</a>"
            + "</form></div>";
    }
}
