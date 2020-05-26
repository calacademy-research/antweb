package org.calacademy.antweb.curate;

import java.util.Map;
import java.util.*;
import java.io.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.home.ProjectDb;
import javax.servlet.http.*;
import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;    
    
public class OperationDetails {

    private static Log s_log = LogFactory.getLog(OperationDetails.class);

    java.util.Date startTime = null;
    HttpServletRequest request = null;
    String operation = null;
    String message = null;
    private String forwardPage = null;
    
    public OperationDetails() {     
      this.operation = "undefined";
      this.startTime = new java.util.Date();
    }
        
    public OperationDetails(String operation) {     
      this.operation = operation;
      this.startTime = new java.util.Date();
    }

    public OperationDetails(String operation, String message) {
      this(operation);
      this.message = message;
    }

    public OperationDetails(String operation, String message, HttpServletRequest request) {
      this(operation, message);
      this.request = request;
      this.request.setAttribute("message", message);
    }

    public OperationDetails(String operation, String message, String forwardPage) {
      // The presence of a forward implies it is a message, and that was not successful execution.
      this(operation, message);
      setForwardPage(forwardPage);  
    }
    
    public String toString() {
      return "{operation:" + getOperation() + "}";
    }    

    // This is used by one case.  SpecimenUpload.
    public String getForwardPage() {
       return this.forwardPage;
    }
    public void setForwardPage(String forwardPage) {
       this.forwardPage = forwardPage;
    }

    public ActionForward returnForward(ActionMapping mapping, HttpServletRequest request) {
      if (getForwardPage() != null) return findForward(mapping, request);
      return null;
    }

    public ActionForward findForward(ActionMapping mapping, HttpServletRequest request) {
      ActionForward forward = null;

      if (request == null) {
        A.log("findForward() request is null");
        return null;
      }

      if (getForwardPage() == null) {
		forward = mapping.findForward("message");
      } else {
        forward = mapping.findForward(getForwardPage());
      }
      request.setAttribute("message", getMessage());

      A.log("findForward() forward:" + forward + " forwardPage:" + getForwardPage() + " request:" + request + " message:" + getMessage());
      return forward;    
    }

    public void setRequest(HttpServletRequest request) {
      this.request = request;
    }

    public HttpServletRequest getRequest() {
      return this.request;
    }

    public ActionForward getErrorForward(ActionMapping mapping) {    
        if (!"success".equals(getMessage())) {
          s_log.warn("execute() " + getMessage());
          return mapping.findForward("message");
        }
        return null;
    }    
    
    private String execTime;
    public String getExecTime() {
        return execTime;
    }
    public void setExecTime(String execTime) {
        this.execTime = execTime;
    }
    
    public String getExecTimeMin() {
      String execTime = getExecTime();
      int i = execTime.indexOf(" ");
      if (i > 0) {
        String execTimeSec = execTime.substring(0, i);
        try {
          int secs = (Integer.valueOf(execTimeSec)).intValue();
          double min = secs / 60;
          return " (" + min + " min)";
        } catch (NumberFormatException e) {
          // do nothing.          
        }
      }
      return "";
    }

    public void augment(OperationDetails opeationDetails) {
      if (!opeationDetails.getMessage().equals("success")) {
        setMessage(getMessage() + opeationDetails.getMessage());
      }
    }

    public String getOperation() {
      return this.operation;
    }
    public void setOperation(String operation) {
      this.operation = operation;
    }

    public void setStartTime(Date startTime) {
      this.startTime = startTime;
    }
    public Date getStartTime() {
      return this.startTime;
    }

    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }    
}

