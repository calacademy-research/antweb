<%-- Unused as of 2026-11-02: an old tiles page "AntWeb - Logout", body curator_logout-body.jsp. No action forwards
     here (logout.do goes to index.jsp), no page links it, and Caddy answers a direct request with 404 (not on the
     JSP allow list in docker/caddy/Caddyfile). --%><%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 

<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="AntWeb - Logout" />
	<tiles:put name="body-content" value="/curator_logout-body.jsp" />	
</tiles:insert>
