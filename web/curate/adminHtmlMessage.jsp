<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@ page import="org.calacademy.antweb.util.*" %>

<bean:define id="project" value="" toScope="session"/> 
<%
        session.removeAttribute("taxon");
%>

<%@include file="/common/antweb_admin-defs.jsp" %>

<% 
    String header = (String) request.getAttribute("header"); 
    if (header == null) header = "message...";

    String message = (String) request.getAttribute("message"); 
    LogMgr.appendLog("messages.txt", DateUtil.getFormatDateTimeStr(new java.util.Date()) + " - " + message + " " + HttpUtil.getTarget(request));
%>

<br><b><%= message %></b>

<%@include file="/util/developerDetail.jsp" %>
