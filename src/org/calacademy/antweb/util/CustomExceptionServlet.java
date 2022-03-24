package org.calacademy.antweb.util;

import java.util.*;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CustomExceptionServlet extends HttpServlet {

  private static Log s_log = LogFactory.getLog(CustomExceptionServlet.class);
  
  static String errorPageURL = "error.jsp";
  
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
  	throws ServletException, IOException {
      handle(request, response);
  }
  
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
  	throws ServletException, IOException {
      handle(request, response);
  }
  
  protected void handle(HttpServletRequest request, HttpServletResponse response)
  	throws ServletException, IOException {
	  try {
		Throwable exception = null;

		// Check if struts has placed an exception object in request
		Object obj = null;
		// obj = request.getAttribute(Globals.EXCEPTION_KEY);

		if (obj == null) {
			// Since no struts exception is found,
			// check if a JSP exception is available in request.
			obj = request.getAttribute("javax.servlet.jsp.jspException");
		}

		if (obj != null && obj instanceof Throwable) {
			exception = (Throwable) obj;
		}
		//s_log.error("Request URI: " + request.getAttribute("javax.servlet.forward.request_uri"));

		// request uri containing the original URL value will be available
		// only on servers implementing servlet 2.4 spec
		String requestURI = (String) request.getAttribute("javax.servlet.forward.request_uri");

        // for instance: /usr/local/tomcat/logs/localhost.2019-05-23.log
        String logFileName = "/usr/local/tomcat/logs/localhost." +  DateUtil.getFormatDateStr(new Date()) + ".log";

		s_log.error("handle() e:" + exception + " request:" + HttpUtil.getTarget(request) 
		  + " see: " + logFileName );

		HttpUtil.sendRedirect(errorPageURL, request, response);
	  } catch (Exception e) {
		// Throwing exceptions from this method can result in request
		// going in to an infinite loop forwarding to the error servlet recursively.
		e.printStackTrace();
	  }
  }
  
}