package org.calacademy.antweb.util;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.apache.struts.action.*;
import org.apache.struts.config.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * Struts exception handler that logs the exception, then hands it to the default ExceptionHandler. Not named in
 * WEB-INF/struts-config.xml or anywhere else, so it is never used (unhandled errors go to the error page of
 * web.xml, CustomExceptionServlet).
 */
public class CustomExceptionHandler extends ExceptionHandler {

  private static final Log s_log = LogFactory.getLog(CustomExceptionHandler.class);

  public ActionForward execute(Exception ex, ExceptionConfig ae,
	ActionMapping mapping, ActionForm formInstance,
	HttpServletRequest request, HttpServletResponse response)
	throws ServletException {

	//log the error message
	s_log.error(ex);

	return super.execute(ex, ae, mapping, formInstance, request, response);
  }

}