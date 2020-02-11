package org.calacademy.antweb.upload;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.*;
import java.sql.*;
import com.mchange.v2.c3p0.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;
    
public final class UploadReportAction extends Action {

    private static Log s_log = LogFactory.getLog(UploadReportAction.class);

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
 		  return (mapping.findForward("message"));
        }

        if (uploadId > 0) {
          Upload upload = UploadMgr.getUpload(uploadId);
          if (upload == null) {
            A.log("execute() upload not found:" + uploadId);
            UploadMgr.log();
          } else {
            String url = AntwebProps.getDomainApp() + "/web/log/" + upload.getUploadDir() + "/" + upload.getLogFileName();
            A.log("execute() redirect to url:" + url);
		
	    	response.sendRedirect(url);  
	    	return null;
		  }
		}
   	    request.setAttribute("message", "Upload Report not found:" + uploadId);
		return (mapping.findForward("message"));
	}

}
