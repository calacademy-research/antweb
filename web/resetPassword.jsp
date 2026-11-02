<%-- Tiles page of resetPassword.do (ResetPasswordAction, the emailed link): "AntWeb - Set a New Password", body
     resetPassword-body.jsp, in the secure layout. Docs: docs/flows/email-and-password-reset.md. Tests:
     tests/e2e/test_password_reset_pages.py. --%><%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 

<%@include file="/common/antwebSecure-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="AntWeb - Set a New Password" />
	<tiles:put name="body-content" value="/resetPassword-body.jsp" />	
</tiles:insert>
