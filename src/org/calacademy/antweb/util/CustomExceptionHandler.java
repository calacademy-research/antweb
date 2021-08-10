package org.calacademy.antweb.util;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.struts.action.*;
import org.apache.struts.config.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class CustomExceptionHandler extends ExceptionHandler {

  private static Log s_log = LogFactory.getLog(CustomExceptionHandler.class);

  public ActionForward execute(Exception ex, ExceptionConfig ae,
	ActionMapping mapping, ActionForm formInstance,
	HttpServletRequest request, HttpServletResponse response)
	throws ServletException {

	//log the error message
	s_log.error(ex);

	return super.execute(ex, ae, mapping, formInstance, request, response);
  }

}