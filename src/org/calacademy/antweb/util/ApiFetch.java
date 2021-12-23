package org.calacademy.antweb.util;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ApiFetch extends HttpServlet {

  private static Log s_log = LogFactory.getLog(ApiFetch.class);
    
  // Accessible as: http://localhost/antweb/apiV3/specimens?minDate=1970-01-01&maxDate=1979-12-31&genus=Tetramorium   

  // Deprecated. Was designed to pass through nicely formatted requests from the api-server
  // back through the live Antweb server, to a browser. Lost formatting. No good.
    
  public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

    s_log.debug("ApiFetch.doGet() requestInfo:" + HttpUtil.getTarget(request));

    String apiServer = "http://10.2.22.50";
    String apiV = "apiV3";
    String pathInfo = request.getPathInfo();
    String queryString = request.getQueryString();
    String url = apiServer + "/" + apiV + pathInfo + "?" + queryString;
    
    //s_log.warn("doGet() url:" + url);

/*    
    String output = HttpUtil.getUrl(url);
    //response.setContentType("application/json;charset=UTF-8");
    response.setContentType("application/json");
    //response.setCharacterEncoding("");
    //response.setContentLength(747044);
    Writer writer = response.getWriter();
    writer.write(output);
*/

    response.sendRedirect(url);

 //   request.getRequestDispatcher(url).forward(request, response);
  }
}