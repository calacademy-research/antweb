package org.calacademy.antweb.util;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class SpecimenFetch extends HttpServlet {

  private static Log s_log = LogFactory.getLog(SpecimenFetch.class);
    
  // Accessible as: http://localhost/antweb/specimen/CASENT0106322X    
  //   http://data.antweb.org/specimen/CASENT0078328  
    
  public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

    String pathInfo = request.getPathInfo();
    String specimenCode = pathInfo.substring(1);

    String url = AntwebProps.getDomainApp() + "/specimen.do?name=" + specimenCode;
    //s_log.warn("doGet() pathInfo:" + pathInfo + " url:" + url);

    if (request.getRequestURL().toString().contains("data.")) {
      //s_log.warn("doGet() data request:" + request.getRequestURL());  // http://localhost/antweb/specimen/CASENT0106322X

      // return an xml page
    } 
    
    String output = null;
    try {
      output = HttpUtil.fetchUrl(url);
    } catch (Exception e) {
      output = "e:" + e.toString();
      //if (AntwebProps.isDevMode()) 
      output += " url:" + url;
      s_log.error("execute() " + output);
    }

    //response.setContentType("text/html");

    //A.log("SpecimenFetch output:" + output);

    response.setContentType("text/html;charset=UTF-8");
    Writer writer = response.getWriter();
    writer.write(output);
  }
}