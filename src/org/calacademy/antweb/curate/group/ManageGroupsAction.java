package org.calacademy.antweb.curate.group;

import javax.servlet.http.*;
import org.apache.struts.action.*;

import java.sql.*;
import java.util.ArrayList;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.home.*;

/**
 * This class takes the UploadForm and retrieves the text value
 * and file attributes and puts them in the request for the display.jsp
 * page to display them
 *
 */

public class ManageGroupsAction extends Action {

    private static Log s_log = LogFactory.getLog(ManageGroupsAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) {

        Connection connection = null;
        ArrayList groupList = null;

        try {
            connection = getDataSource(request, "conPool").getConnection();

            groupList = new GroupDb(connection).getAllGroups();
            //s_log.info("execute() groupList:" + groupList);
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                s_log.error("execute() finally e:" + e);
            }
        }

        if (groupList != null) {
          request.getSession().setAttribute("antwebGroups", groupList);      
          return mapping.findForward("success");
        } else {
          return mapping.findForward("error");
        }
    }
}
