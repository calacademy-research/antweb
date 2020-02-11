package org.calacademy.antweb.curate;

import java.io.*;
import javax.servlet.http.*;
import org.apache.struts.action.*;
import org.apache.regexp.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;


public class ManageMuseumsAction extends Action {

    private static Log s_log = LogFactory.getLog(ManageMuseumsAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) {

        ArrayList<Museum> museumList = null;
        Museum museum = null;
        java.sql.Connection connection = null;
        HttpSession session = request.getSession();

        DynaActionForm df = (DynaActionForm) form;        
        String museumCode = (String) df.get("code");   
        String museumName = (String) df.get("name");   
        String museumTitle = (String) df.get("title");   
           
        Boolean isActive = (Boolean) df.get("isActive"); 
        if (isActive == null) isActive = new Boolean(false);   
        String action = (String) df.get("action");
        String step = (String) df.get("step");

        if (HttpUtil.getTarget(request).contains("viewMuseum")) action = "view";

        if (HttpUtil.isPost(request)) action = "save";
        
        if ("delete".equals(step)) action = "delete";

        // Is it new?
        if ("save".equals(action) && museumCode == null) {
          return (mapping.findForward("success")); 
        }
              
        String message = "";
        
        A.log("execute() action:" + action + " code:" + museumCode + " museumName:" + museumName + " isActive:" + isActive);
                            
        try {
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "ManageMuseumsAction");

            MuseumDb museumDb = new MuseumDb(connection);
            if ("delete".equals(action)) {
              message = museumDb.deleteByCode(museumCode);
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
            return (mapping.findForward("error"));
        } finally { 		
            DBUtil.close(connection, this, "ManageMuseumsAction");
        }

        if (museumList != null) request.setAttribute("museums", museumList);      
        if (museum != null) request.setAttribute("museum", museum);      
        if (message != null) request.setAttribute("message", message);
               
        return (mapping.findForward("success"));
    }
}
