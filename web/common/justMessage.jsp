
<%@ page import="org.calacademy.antweb.util.LogMgr" %>
<%@ page import="org.calacademy.antweb.util.DateUtil" %>
<%@ page import="org.calacademy.antweb.util.HttpUtil" %>

<% 
    String message = (String) request.getAttribute("message"); 
    LogMgr.appendLog("messages.txt", DateUtil.getFormatDateTimeStr(new java.util.Date()) + " - " + message + " " + HttpUtil.getTarget(request));
%>

<%= message %>
