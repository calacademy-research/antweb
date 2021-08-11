package org.calacademy.antweb.imageUploader;

import org.apache.struts.action.*;

import java.sql.*;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;

import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/*
 Initially from:
   http://www.tutorialspoint.com/jsp/jsp_file_uploading.htm
   https://stackoverflow.com/questions/20183295/uploading-multiple-files-using-jsp-servlets
*/

public final class ImageUploadReport extends Action {

    private static Log s_log = LogFactory.getLog(ImageUploadReport.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward a = Check.init(request, mapping); if (a != null) return a;
        ActionForward c = Check.login(request, mapping); if (c != null) return c;
        ActionForward d = Check.valid(request, mapping); if (d != null) return d;        
 
        HttpUtil.setUtf8(request, response);

        ImageUpload imageUpload = null;
        Connection connection = null;
        try {
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "GroupAction.execute()");        
        
            ImageUploadDb imageUploadDb = new ImageUploadDb(connection);        

            DynaActionForm df = (DynaActionForm) form;
            int imageUploadId = 0;
            Integer id = (Integer) df.get("id");
            if (id != null) imageUploadId = id.intValue();

            if (imageUploadId != 0) {            
              imageUpload = imageUploadDb.getImageUpload(imageUploadId);
              request.setAttribute("imageUpload", imageUpload);  
              return (mapping.findForward("report"));         
            } else {          
              return (mapping.findForward("error"));         
            }
        } catch (SQLException e) {
          AntwebUtil.log("ImageUploadReport.execute() e:" + e);
        } finally {
          DBUtil.close(connection, this, "ImageUploadReport.execute()");
        }

        AntwebUtil.log("execute() No messages set. This shouldn't happen");
        return (mapping.findForward("error"));         
	}	
}
