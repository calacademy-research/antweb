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

   // We prefer to use this jspError.jsp over the one in the root web directory.  
   // String errorReason = "";  // define the errorReason in the including page.
   // See bigPicture.jsp for an example.

   String logMessage = "error.jsp logMessage: ";

   String errorReason = (String) request.getAttribute("errorReason");

%>
<h3>Error Page: <%= Html.esc(errorReason) %></h3>
<br><hr></hr>
<p>If you believe this to be a malfunction
, please contact <a href="mailto:<%= AntwebUtil.getAdminEmail() %>"><%= AntwebUtil.getAdminName() %></a> and tell him that something is amiss with AntWeb.</p>

<% 
if (exception != null) {
  errorReason = "exception";
  logMessage += errorReason + ": " + exception;
  // Never in the page for visitors: logged with a case number; administrators see it on the page.
  String caseId = Long.toString(System.currentTimeMillis(), 36);
  logMessage = "case:" + caseId + " " + logMessage;
  String shortStack = AntwebUtil.getAntwebStackTrace(exception);
  boolean errorShowAdmin = false;
  try { errorShowAdmin = LoginMgr.isAdmin(request); } catch (Exception e) { /* no session */ }
  out.println("<p><small>Case " + caseId + "</small></p>");
  if (errorShowAdmin) out.println("<pre style=\"white-space:pre-wrap\">" + Html.esc(logMessage + " " + shortStack) + "</pre>");

  if (exception instanceof java.io.FileNotFoundException) {
    AntwebUtil.log(logMessage + " " + HttpUtil.getRequestInfo(request));  
  } else if (exception instanceof java.util.ConcurrentModificationException) {    
    AntwebUtil.log(logMessage + " " + HttpUtil.getRequestInfo(request) + " " + shortStack);  
  } else {
    if (AntwebProps.isDevMode()) {
      AntwebUtil.log(logMessage + " " + AntwebUtil.getStackTrace(exception));
    } else {
      AntwebUtil.log(logMessage + " " + shortStack);
    }
  }
} else {
  logMessage += errorReason;
  //out.println(logMessage);
  //AntwebUtil.log(logMessage);  
}

%>
</div>
<div class="right">
</div>
