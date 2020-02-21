package org.calacademy.antweb.util;

import org.calacademy.antweb.*;
import java.util.List;
import java.util.Iterator;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.*;

import org.apache.commons.io.output.DeferredFileOutputStream;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class FileUploader extends HttpServlet {

  private static Log s_log = LogFactory.getLog(FileUploader.class);

  // Accessible as: http://localhost/antweb/specimen/CASENT0106322X
    
  public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

    boolean isMultipart = ServletFileUpload.isMultipartContent(request);

    s_log.warn("doGet() isMultiPart:" + isMultipart);

    if (!isMultipart) {
      // do nothing
    } else {
      FileItemFactory factory = new DiskFileItemFactory();
      ServletFileUpload upload = new ServletFileUpload(factory);
      List items = null;
      try {
        items = upload.parseRequest(request);
      } catch (FileUploadException e) {
        s_log.error("doGet() e:" + e);
      }
      Iterator itr = items.iterator();
      while (itr.hasNext()) {
        FileItem item = (FileItem) itr.next();
        if (item.isFormField()) {
          // do nothing
        } else {
          try {
            String itemName = item.getName();
//            String docBase = request.getRealPath("/");
            String docBase = AntwebProps.getDocRoot();
            // could be: /Users/macpro/dev/apache-tomcat-7.0.21/webapps/antweb/
            String toUploadDir = docBase + "web/toUpload/";
            (new Utility()).makeDirTree(toUploadDir);
s_log.warn("doPost() toUploadDir:" + toUploadDir);
            File savedFile = new File(toUploadDir + itemName);
            item.write(savedFile);
            
            response.setContentType("text/html");
            Writer writer = response.getWriter();
            
            String output = "<tr><td><b>Your file has been saved at the loaction:</b></td></tr><tr><td><b>" + docBase + "uploadedFiles" + "\\" + itemName + "</td></tr>";
            writer.write(output);
          } catch (Exception e) {
            s_log.error("doPost() e:" + e);
          }
        }
      }
    }
  }
}

  /*
    String pathInfo = request.getPathInfo();
    String specimenCode = pathInfo.substring(1);

    String url = AntwebProps.getDomainApp() + "/specimen.do?name=" + specimenCode;
    //s_log.warn("doGet() pathInfo:" + pathInfo + " url:" + url);

    if (request.getRequestURL().toString().contains("data.")) {
      s_log.warn("doGet() data request:" + request.getRequestURL());  // http://localhost/antweb/specimen/CASENT0106322X

      // return an xml page
    } 
    
    String output = AntwebUtil.getUrl(url);
    response.setContentType("text/html");
    Writer writer = response.getWriter();
    writer.write(output);
  */
