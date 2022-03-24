package org.calacademy.antweb.upload;

import java.io.IOException;
import java.sql.Connection;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.search.ResultItem;
import org.calacademy.antweb.search.RecentImageSearchResults;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class DeleteImagesAction extends Action {

    private static Log s_log = LogFactory.getLog(DeleteImagesAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        String message = null;
        
        // Extract attributes we will need
        Locale locale = getLocale(request);
        HttpSession session = request.getSession();
        String[] chosen = ((DeleteImagesForm) form).getChosen();
        String group = ((DeleteImagesForm) form).getGroup();
        String daysAgo = ((DeleteImagesForm) form).getDaysAgo();

        RecentImageSearchResults searchResults = (RecentImageSearchResults) session.getAttribute("searchResults");
        if (searchResults == null) {
          message = "Session expired. Refresh <a href='" + AntwebProps.getDomainApp() + "/recentSearchResults.do?searchMethod=recentImageSearch&daysAgo=30'>here</a>";
          request.setAttribute("message", message);
          return mapping.findForward("message");
        }

        ArrayList results = searchResults.getResults();
        ResultItem thisResult = null;

        if (chosen != null) {

            Connection connection = null;
            try {
                DataSource dataSource = getDataSource(request, "conPool");
                connection = DBUtil.getConnection(dataSource, "DeleteImagesAction.execute()");

                for (String s : chosen) {
                    thisResult = (ResultItem) results.get(Integer.parseInt(s));

                    ImageDb imageDb = new ImageDb(connection);
                    int shot = Integer.parseInt(thisResult.getShotNumber());
                    imageDb.deleteImage(thisResult.getCode(), thisResult.getShotType(), shot);
                }
            } catch (Exception e) {
                s_log.error("execute() e:" + e);
                message = "e:" + e;
                request.setAttribute("message", message);
                return mapping.findForward("message");
            } finally {
                DBUtil.close(connection, this, "DeleteImagesAction.execute()");
            }
            
            // Set a transactional control token to prevent double posting
            saveToken(request);
            
            request.setAttribute("group", group);
            request.setAttribute("daysAgo", daysAgo);
            return mapping.findForward("success");
        } else {
            request.setAttribute("message", message);
            return mapping.findForward("message");
        }
    }
}
