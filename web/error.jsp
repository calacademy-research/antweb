<%@ page isErrorPage="true" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.*" %>
<%@ page import="java.lang.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<div class=left>

<% 
// This error page attempts to handle session issues for potentially stale queries.
// Should be used for search.  In general, we should use common/error.jsp

   String errorReason = "";
   String logMessage = "error.jsp ";

   if (session.isNew()) {
     errorReason = "new session";
%>
<h1>We'll be right back!</h1>
<hr></hr>
We're currently updating the software running AntWeb.  Please try again in about 5 minutes.

<p>If you receive this page repeatedly, please contact <a href="mailto:<%= AntwebUtil.getAdminEmail() %>"> <%= AntwebUtil.getAdminName() %></a> and tell him that something is amiss with AntWeb.</p>

<% 
     // AntwebUtil.infoStackTrace();  // This is not helpful.
  } else { 
     errorReason = "stale session";
%>
<h1>Oops, an error occurred! </h1>
<h2>Perhaps your session has gone stale.</h2>
<hr></hr>
Sometimes if you've performed a search and then left your computer, AntWeb forgets what you've been doing.  Currently, AntWeb remembers your activities for about ten minutes, and then forgets.  Please try again and everything should be fine.
<br><br>If trouble persists, please bring it to the attention of Brian Fisher.
<% } %>

<p>Thanks!</p>

<% 
if (exception != null) {
  errorReason = "exception";
  logMessage += errorReason + ": " + exception;
  logMessage += HttpUtil.getRequestInfo(request);
  out.println("<!--");
  out.println(logMessage);

  String shortStack = AntwebUtil.getShortAntwebStackTrace(exception);
  out.print(shortStack);

  out.println("-->");

  if (exception instanceof java.io.FileNotFoundException) {
    AntwebUtil.log(logMessage + " " + HttpUtil.getRequestInfo(request));  
  } else if (exception instanceof java.util.ConcurrentModificationException) {    
    AntwebUtil.log(logMessage + " 1 " + HttpUtil.getRequestInfo(request) + " " +  AntwebUtil.getStackTrace(exception));   // shortStack);
  } else {
    if (AntwebProps.isDevMode()) {
      AntwebUtil.log(logMessage + " 2 " + AntwebUtil.getStackTrace(exception));
    } else {
      AntwebUtil.log(logMessage + " 3 " + shortStack);
    }
  }
} else {
  logMessage += errorReason;
  //out.println(logMessage);
  AntwebUtil.log(logMessage);  
}

%>
</div>
<div class="right">
</div>
