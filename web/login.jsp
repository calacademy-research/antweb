<%-- Tiles page of the log-in form in the secure layout (common/antwebSecure-defs.jsp): login.do's failure forward
     (also the plain GET of login.do) and Check.loginForFeature (field guides, comparisons and maps of search results
     for visitors not logged in). "AntWeb - Please Log In", body login-body.jsp. On the JSP allow list in
     docker/caddy/Caddyfile. Tests: tests/e2e/test_login_needed.py, test_curator.py. --%><%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@ page import = "org.calacademy.antweb.util.*" %>

<bean:define id="project" value="" toScope="session"/> 

<%@include file="/common/antwebSecure-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="AntWeb - Please Log In" />
	<tiles:put name="body-content" value="/login-body.jsp" />	
</tiles:insert>
