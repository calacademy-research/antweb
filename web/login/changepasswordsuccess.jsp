<%-- Unused as of 2026-11-02: the old Success! page after a password change, with the body
     /changepasswordsuccess-body.jsp, which does not exist (the body is in web/login/). No action forwards here, and
     Caddy answers a direct request with 404 (not on the JSP allow list in docker/caddy/Caddyfile). --%><%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>



<%@include file="/curate/curatorCheck.jsp" %>


<bean:define id="project" value="" toScope="session"/> 

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Success!" />
	<tiles:put name="body-content" value="/changepasswordsuccess-body.jsp" />	
</tiles:insert>
