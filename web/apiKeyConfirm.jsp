<%-- Tiles page of apiKeyConfirm.do (ApiKeyConfirmAction, the link in the API key confirmation email): "AntWeb - Your
     API key", body apiKeyConfirm-body.jsp. Docs: docs/flows/api.md (API keys). Tests: tests/e2e/test_api_keys.py. --%><%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>

<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="AntWeb - Your API key" />
	<tiles:put name="body-content" value="/apiKeyConfirm-body.jsp" />
</tiles:insert>
