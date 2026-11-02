<%-- Tiles page of forgotPassword.do (ForgotPasswordAction): "AntWeb - Password Retrieval", body
     forgot_password-body.jsp. On the JSP allow list in docker/caddy/Caddyfile. Docs:
     docs/flows/email-and-password-reset.md. Tests: tests/e2e/test_password_reset_pages.py. --%><%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 

<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="AntWeb - Password Retrieval" />
	<tiles:put name="body-content" value="/forgot_password-body.jsp" />	
</tiles:insert>
