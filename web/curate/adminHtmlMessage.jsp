<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@ page import="org.calacademy.antweb.util.*" %>

<%-- Admin result pages built in Java (named query results query.do?name=..., Integrity Check, Taxa outside their
     native bioregions, the -D parts report). Since 2026-10-22, for a logged-in viewer: the admin layout and the
     tool-page look (curate/adminHtmlMessage-body.jsp, common/tools.css). A few named queries are open to the
     public (QueryAction.namedQueryAccess): an anonymous viewer gets the page as before (no layout). --%>
<bean:define id="project" value="" toScope="session"/> 
<%
        session.removeAttribute("taxon");
%>

<%@include file="/common/antweb_admin-defs.jsp" %>

<% 
    String header = (String) request.getAttribute("header"); 
    if (header == null) header = "message...";

    String message = (String) request.getAttribute("message"); 
    LogMgr.appendDataLog("messages.txt", DateUtil.getFormatDateTimeStr(new java.util.Date()) + " - " + message + " " + HttpUtil.getTarget(request));

    if (LoginMgr.getAccessLogin(request) != null) {
%>
<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%= header %>" />
	<tiles:put name="body-content" value="/curate/adminHtmlMessage-body.jsp" />
</tiles:insert>
<%
    } else {
%>
<br><b><%= message %></b>
<%
    }
%>
