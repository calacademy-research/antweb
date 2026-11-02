<%-- Unused as of 2026-11-02: a tiles page "AntWeb - Permission Denied", body noAccess-body.jsp. No action forwards
     here (the global forward permissionDenied goes to permissionDenied.jsp), no page links it, and Caddy answers a
     direct request with 404 (not on the JSP allow list in docker/caddy/Caddyfile). --%><%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 

<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="AntWeb - Permission Denied" />
	<tiles:put name="body-content" value="/noAccess-body.jsp" />	
</tiles:insert>
