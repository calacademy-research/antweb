<%-- Unused as of 2026-11-02: a tiles page (title from the request attribute header) whose body
     common/jspError-body.jsp, which did not compile, was removed on that date. No action forwards here, no JSP names
     it, and Caddy answers a direct request with 404 (not on the JSP allow list in docker/caddy/Caddyfile). --%><%@ page errorPage = "/error.jsp" %>

<% // Including pages should comply with this usage or errorPage beginning with slash %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 
<%
        session.removeAttribute("taxon");
%>

<%@include file="/common/antweb-defs.jsp" %>

<% 
    String header = (String) request.getAttribute("header"); 
    if (header == null) header = "message...";
%>    
    
<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%= header %>" />
	<tiles:put name="body-content" value="/common/jspError-body.jsp" />	
</tiles:insert>
