package org.calacademy.antweb.upload;

import org.calacademy.antweb.AntwebAction;
import org.calacademy.antweb.Login;

import java.util.*;
import jakarta.servlet.http.*;
import org.apache.struts.action.*;
import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.util.*;

// Used in View Archived Specimen List Files on curate-body.jsp
/**
 * This class takes the UploadForm and retrieves the text value
 * and file attributes and puts them in the request for the display.jsp
 * page to display them
 */
public class UploadHistoryAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(UploadHistoryAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) {

        // Archived upload files are curator data.
        ActionForward c = Check.curator(request, mapping); if (c != null) return c;

        UploadForm uploadForm = (UploadForm) form;
        String name = uploadForm.getEditSpeciesList();
        // Curators list their own group's files only (the menu offers no others); admins any.
        Login accessLogin = LoginMgr.getAccessLogin(request);
        if (!LoginMgr.isAdmin(accessLogin) && (accessLogin == null || !("specimen" + accessLogin.getGroupId()).equals(name))) {
            name = "none";
        }
        ArrayList<String> files = AntwebUtil.getUploadDirFiles(name);
        request.setAttribute("files", files);
        return mapping.findForward("success");
    }

}
