package org.calacademy.antweb.upload;

import org.calacademy.antweb.AntwebAction;
import org.calacademy.antweb.Login;

import java.io.IOException;
import java.sql.Connection;
import java.util.*;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
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
    
public final class DeleteImagesAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(DeleteImagesAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        // Require a logged-in curator: this action changes data.
        ActionForward denied = LoginMgr.mustBeCurator(request, mapping); if (denied != null) return denied;

        // Deleting changes data: only from the form (POST), never from a link.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            request.setAttribute("message", "Images can only be deleted with the Delete Selected Images button on the Recent Images page.");
            return mapping.findForward("message");
        }
        // Curators may delete only images owned by their own group; admins may delete any.
        Login accessLogin = LoginMgr.getAccessLogin(request);
        boolean isAdmin = LoginMgr.isAdmin(accessLogin);
        int deletedCount = 0;
        int refusedCount = 0;

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

        ArrayList<ResultItem> results = searchResults.getResults();
        ResultItem thisResult;

        if (chosen != null) {

            Connection connection = null;
            String dbMethodName = DBUtil.getDbMethodName("DeleteImagesAction.execute()");
            try {
                DataSource dataSource = getDataSource(request, "conPool");
                connection = DBUtil.getConnection(dataSource, dbMethodName);

                for (String s : chosen) {
                    int index = Integer.parseInt(s);
                    if (index < 0 || index >= results.size()) continue;
                    thisResult = results.get(index);

                    ImageDb imageDb = new ImageDb(connection);
                    int shot = Integer.parseInt(thisResult.getShotNumber());
                    if (!isAdmin) {
                        Set<Integer> ownerGroups = imageDb.getImageGroupIds(thisResult.getCode(), thisResult.getShotType(), shot);
                        boolean ownsIt = !ownerGroups.isEmpty();
                        for (Integer ownerGroup : ownerGroups) {
                            if (ownerGroup == null || ownerGroup != accessLogin.getGroupId()) ownsIt = false;
                        }
                        if (!ownsIt) {
                            s_log.warn("execute() refused delete of image code:" + thisResult.getCode() + " shot:" + thisResult.getShotType() + shot
                                + " by login:" + accessLogin.getId() + " group:" + accessLogin.getGroupId() + " owners:" + ownerGroups);
                            refusedCount++;
                            continue;
                        }
                    }
                    imageDb.deleteImage(thisResult.getCode(), thisResult.getShotType(), shot);
                    deletedCount++;
                    LogMgr.appendLog("admin.log", "deleteImages login:" + accessLogin.getId() + " code:" + thisResult.getCode()
                        + " shot:" + thisResult.getShotType() + shot, true);
                }
            } catch (Exception e) {
                // A fixed text with a case number: the exception text may hold the submitted values.
                int caseNumber = AntwebUtil.getCaseNumber();
                s_log.error("execute() case:" + caseNumber + " e:" + e);
                message = "The images could not be deleted (case " + caseNumber + "). Deleted before the problem: " + deletedCount + ".";
                request.setAttribute("message", message);
                return mapping.findForward("message");
            } finally {
                DBUtil.close(connection, this, dbMethodName);
            }
            
            // Set a transactional control token to prevent double posting
            saveToken(request);

            if (refusedCount > 0) {
                request.setAttribute("message", "Deleted " + deletedCount + " image(s). Not deleted: " + refusedCount
                    + " image(s) that belong to another group. Ask an AntWeb administrator if they should be removed.");
                return mapping.findForward("message");
            }
            
            request.setAttribute("group", group);
            request.setAttribute("daysAgo", daysAgo);
            return mapping.findForward("success");
        } else {
            request.setAttribute("message", message);
            return mapping.findForward("message");
        }
    }
}
