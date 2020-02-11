package org.calacademy.antweb.curate.ancFile;

import java.util.*;
import java.io.*;
import java.sql.*;
import javax.sql.DataSource;

import javax.servlet.ServletException;
import javax.servlet.http.*;

import org.apache.struts.action.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class AncPageMgrAction extends Action {

    private static Log s_log = LogFactory.getLog(AncPageMgrAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        A.log("In AncPageMgrAction");        
        
        // Extract attributes we will need
        HttpSession session = request.getSession();

        ActionForward c = Check.login(request, mapping); if (c != null) {
			session.removeAttribute("ancillary");
            return c;
        }
                
        String ancPageMgrReport = "";		
        String query;

        Connection connection = null;
        
        ArrayList<AncFile> ancFileArray = new ArrayList<AncFile>();
        
        try {
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "AncPageMgrAction.execute()");

            query = "select id, title, fileName, directory, last_changed, project_name, access_login from ancillary";

            Statement stmt = connection.createStatement();
            ResultSet rset = stmt.executeQuery(query);
            int i = 0;
            while (rset.next()) {
              ++i;
 
              AncFile ancFile = new AncFile();
              ancFile.setId(rset.getInt("id"));
              ancFile.setTitle(rset.getString("title"));
              ancFile.setFileName(rset.getString("fileName"));
              ancFile.setDirectory(rset.getString("directory"));
              ancFile.setLastChanged(rset.getDate("last_changed"));
              ancFile.setProject(rset.getString("project_name"));
              ancFile.setAccessLoginId(rset.getInt("access_login"));
              
              ancFileArray.add(ancFile);
              //ancFile.setAccessLogin(rset.getString("access_login"));
 /*
              if (i == 1) {
                  ancPageMgrReport = "<br><br><b>Query:</b> " + query + " <br><b>returns:</b><br>";          
              } else {
                  ancPageMgrReport += "<br>";
              }
              ancPageMgrReport += rset.getString("title");
              ancPageMgrReport += ", " + rset.getString("fileName");
              ancPageMgrReport += ", " + rset.getString("directory");
              ancPageMgrReport += ", " + rset.getString("last_changed");
              ancPageMgrReport += ", " + rset.getString("project_name");  
              ancPageMgrReport += ", " + rset.getString("access_login");  
*/
            }
            stmt.close();
          
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return (mapping.findForward("error"));
        } finally { 		
            DBUtil.close(connection, this, "AncPageMgrAction.execute()");
        }

        request.setAttribute("ancFileArray", ancFileArray);
s_log.warn("return success");                          
        return (mapping.findForward("success"));
    }
}
