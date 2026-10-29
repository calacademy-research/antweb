package org.calacademy.antweb.upload;

import org.calacademy.antweb.AntwebAction;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
    
/**
 * /uploadReport.do?uploadId=N (public): redirects to the report of one specimen upload under web/log/
 * (the upload as kept by UploadMgr), or says the report is no longer on the server. Linked from the
 * specimen page and from the header of each report.
 * Tests: tests/e2e/test_specimen_upload_roundtrip.py, tests/e2e/test_background_jobs.py.
 */
public final class UploadReportAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(UploadReportAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        DynaActionForm df = (DynaActionForm) form;
        int uploadId = 0;
        
        Object uploadIdObj = df.get("uploadId");
        if (uploadIdObj != null) {
          uploadId = (int) uploadIdObj;
        } else {
          String message = "uploadId not found.";
          //s_log.warn("execute() " + message);
   	      request.setAttribute("message", message);
 		  return mapping.findForward("message");
        }

        if (uploadId > 0) {
          Upload upload = UploadMgr.getUpload(uploadId);
          if (upload == null) {
            s_log.debug("execute() upload not found:" + uploadId);
            UploadMgr.log();
          } else if (!upload.hasReport()) {
            request.setAttribute("message", "The report for upload " + uploadId + " is no longer available.");
            return mapping.findForward("message");
          } else {
            String url = AntwebProps.getDomainApp() + "/web/log/" + upload.getUploadDir() + "/" + upload.getLogFileName();
            s_log.debug("execute() redirect to url:" + url);
		
	    	response.sendRedirect(url);  
	    	return null;
		  }
		}
   	    request.setAttribute("message", "Upload Report not found:" + Html.esc(uploadId));
		return mapping.findForward("message");
	}

}
