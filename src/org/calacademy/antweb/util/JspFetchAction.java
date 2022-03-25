package org.calacademy.antweb.util;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.struts.action.*;

public class JspFetchAction extends Action {

    private static final Log s_log = LogFactory.getLog(JspFetchAction.class);
    
// DEPRECATED? The calls below do not seem to invoke this class.
// This class is not included in struts-config.xml. 
// web/log/upload/ is deprecated in favor of web/log/upload/worldants/ and web/log/upload/specimen/   
    
    // Accessible as: http://localhost/antweb/specimen/CASENT0106322X    
    //   http://data.antweb.org/specimen/CASENT0078328  
    
    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        HttpUtil.setUtf8(request, response); 
        
        return mapping.findForward("failure");
    }
    
    public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

      if (true) return;

      String pathInfo = request.getPathInfo();

      String dir = "web/log/upload/";
      String fileName = pathInfo.substring(1);    
    
      // String fileName = "web/log/upload/20161127-13:35:35-CASSpecimenList.jsp";
      //String output = AntwebUtil.readFile(dir, fileName);


      String url = AntwebProps.getDomainApp() + "/" + dir + fileName;
      //s_log.warn("doGet() pathInfo:" + pathInfo + " url:" + url);
      //http://localhost/antweb/web/log/upload/
    
      String output = HttpUtil.getUrl(url);
    
      if (output == null) output = "File not found.";

      s_log.warn("doGet() pathInfo:" + pathInfo + " fileName:" + fileName);

      response.setContentType("text/html;charset=UTF-8");
      Writer writer = response.getWriter();
      writer.write(output);
    }
}