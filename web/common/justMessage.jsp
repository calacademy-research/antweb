
<%@ page import="org.calacademy.antweb.util.LogMgr" %>
<%@ page import="org.calacademy.antweb.util.DateUtil" %>
<%@ page import="org.calacademy.antweb.util.HttpUtil" %>

<% 
    String message = (String) request.getAttribute("message"); 
    LogMgr.logMessagePage(request, message); // a short record only (MessagePageLog)
%>

<%= message %>
