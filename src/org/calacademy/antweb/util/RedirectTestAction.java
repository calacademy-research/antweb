package org.calacademy.antweb.util;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.IOException;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
    
public final class RedirectTestAction extends HttpServlet {
  /* This class will redirect requests to antweb_test to the corresponding antweb page */
  
  private static final Log s_log = LogFactory.getLog(RedirectTestAction.class);

  public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

    String pathInfo = request.getPathInfo();    
//    String newsId = pathInfo.substring(1);
    String query = request.getQueryString();

    if ((pathInfo == null) || (pathInfo.length() < 2)) {
      response.sendRedirect("http://www.antweb.org");
      return;
    }

    String url = "http://www.antweb.org/" + pathInfo.substring(1) + (query == null ? "" : "?" + query);

    //s_log.warn("doGet() url:" + url);    

    response.sendRedirect(url);

/*
    String output = AntwebUtil.getUrl(url);

    response.setContentType("text/html");
    Writer writer = response.getWriter();
    writer.write(output);
*/
  }
}