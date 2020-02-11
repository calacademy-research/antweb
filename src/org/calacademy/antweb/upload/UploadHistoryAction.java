package org.calacademy.antweb.upload;

import java.util.*;
import javax.servlet.http.*;
import org.apache.struts.action.*;
import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.*;

// Used in View Archived Specimen List Files on curate-body.jsp
/**
 * This class takes the UploadForm and retrieves the text value
 * and file attributes and puts them in the request for the display.jsp
 * page to display them
 */
public class UploadHistoryAction extends Action {

    private static Log s_log = LogFactory.getLog(UploadHistoryAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) {

        UploadForm uploadForm = (UploadForm) form;
        String name = uploadForm.getEditSpeciesList();
        ArrayList<String> files = AntwebUtil.getUploadDirFiles(name);
        request.setAttribute("files", files);
        return mapping.findForward("success");
    }

}
