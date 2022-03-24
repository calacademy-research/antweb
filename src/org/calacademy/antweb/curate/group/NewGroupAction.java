package org.calacademy.antweb.curate.group;

import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

/**
 * This class takes the UploadForm and retrieves the text value
 * and file attributes and puts them in the request for the display.jsp
 * page to display them
 *
 */

public class NewGroupAction extends Action {

    private static Log s_log = LogFactory.getLog(NewGroupAction.class);

    public ActionForward execute( ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) {

        Connection connection = null;
        String query;
        int newGroupId = 0;
        
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "NewGroupAction");
          
            connection.setAutoCommit(true);

            String theQuery = "select max(id) as maxid from ant_group";
            Statement stmt = connection.createStatement();
            ResultSet rset = stmt.executeQuery(theQuery);
            int max = 0;
            while (rset.next()) {
                max = rset.getInt("maxid");
            }
            if (max > 0) {
                newGroupId = max + 1;
            }

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return mapping.findForward("error");
        } finally { 		
            DBUtil.close(connection, this, "NewGroupAction");
        }
        
        if (newGroupId > 0) {
            Group newGroup = new Group();
            newGroup.init();
            newGroup.setId(newGroupId);
            request.getSession().setAttribute("thisGroup", newGroup);
            request.setAttribute("isNewGroup", "true");
            return mapping.findForward("success");
        } else {
            return mapping.findForward("error");
        }
    }

    

}
