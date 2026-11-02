<%-- Unused as of 2026-11-02: an old tiles page "AntWeb - Curator Log In", body curator_login-body.jsp. No action
     forwards here, no page links it, and Caddy answers a direct request with 404 (not on the JSP allow list in
     docker/caddy/Caddyfile). The log-in page is login.jsp. --%><%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 

<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="AntWeb - Curator Log In" />
	<tiles:put name="body-content" value="/curator_login-body.jsp" />	
</tiles:insert>
