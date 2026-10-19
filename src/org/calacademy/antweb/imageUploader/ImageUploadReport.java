package org.calacademy.antweb.imageUploader;

import org.calacademy.antweb.AntwebAction;

import org.apache.struts.action.*;

import java.sql.*;

import java.io.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/*
 Initially from:
   http://www.tutorialspoint.com/jsp/jsp_file_uploading.htm
   https://stackoverflow.com/questions/20183295/uploading-multiple-files-using-jsp-servlets
*/

public final class ImageUploadReport extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(ImageUploadReport.class);

    public static final String NOT_OWN_GROUP = "This image upload report belongs to another group.";

    /** Curators see their own group's reports; administrators any. */
    public static boolean mayView(org.calacademy.antweb.Login login, int uploadGroupId) {
        if (login == null) return false;
        if (Boolean.TRUE.equals(login.isAdmin())) return true;
        return login.getGroupId() == uploadGroupId;
    }

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward a = Check.init(request, mapping); if (a != null) return a;
        ActionForward c = Check.curator(request, mapping); if (c != null) return c;
        ActionForward d = Check.valid(request, mapping); if (d != null) return d;        
 
        HttpUtil.setUtf8(request, response);

        ImageUpload imageUpload = null;
        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("ImageUploadReport.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
        
            ImageUploadDb imageUploadDb = new ImageUploadDb(connection);        

            DynaActionForm df = (DynaActionForm) form;
            int imageUploadId = 0;
            Integer id = (Integer) df.get("id");
            if (id != null) imageUploadId = id;

            if (imageUploadId != 0) {            
              imageUpload = imageUploadDb.getImageUpload(imageUploadId);
              if (imageUpload != null && !mayView(LoginMgr.getAccessLogin(request), imageUpload.getGroupId())) {
                request.setAttribute("message", NOT_OWN_GROUP);
                return mapping.findForward("message");
              }
              if (imageUpload != null) {
                // Files not saved; a name saved in this upload (for example on a later try) is left out.
                java.util.Set<String> saved = new java.util.HashSet<>();
                for (ImageUploaded iu : imageUpload.getImages()) {
                  if (iu.getErrorMessage() == null) saved.add(iu.getFileName());
                }
                java.util.List<ImageUploadRefusedDb.Refused> refused = new ImageUploadRefusedDb(connection).list(imageUploadId);
                refused.removeIf(r -> saved.contains(r.getFileName()));
                imageUpload.setRefused(refused);
              }
              request.setAttribute("imageUpload", imageUpload);  
              return mapping.findForward("report");
            } else {          
              return mapping.findForward("error");
            }
        } catch (SQLException e) {
          AntwebUtil.log("ImageUploadReport.execute() e:" + e);
        } finally {
          DBUtil.close(connection, this, dbMethodName);
        }

        AntwebUtil.log("execute() No messages set. This shouldn't happen");
        return mapping.findForward("error");
	}	
}
