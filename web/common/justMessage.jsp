<%-- The global forward justMessage (used by util.SpecimenListAction, the Download Data list): writes only the
     request attribute message, without layout, and logs a short record (LogMgr.logMessagePage). --%>
<%@ page import="org.calacademy.antweb.util.LogMgr" %>
<%@ page import="org.calacademy.antweb.util.DateUtil" %>
<%@ page import="org.calacademy.antweb.util.HttpUtil" %>

<% 
    String message = (String) request.getAttribute("message"); 
    LogMgr.logMessagePage(request, message); // a short record only (MessagePageLog)
%>

<%= message %>
