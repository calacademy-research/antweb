<%-- Tiles page of the global forward notLoggedIn (LoginMgr.mustLogIn, mustBeAdmin and others): "AntWeb - Please Log
     In", body notLoggedIn-body.jsp. On the JSP allow list in docker/caddy/Caddyfile. Tests:
     tests/e2e/test_login_needed.py. --%><%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="request"/> 

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="AntWeb - Please Log In" />
	<tiles:put name="body-content" value="/notLoggedIn-body.jsp" />	
</tiles:insert>
