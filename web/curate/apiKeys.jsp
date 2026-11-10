<%-- API keys admin page (apiKeys.do, curate/ApiKeysAction, administrators only): every registration for a free API key
     with its use, and the forms to revoke, reactivate, set the level, limits or note. Tiles wrapper of
     curate/apiKeys-body.jsp. Docs: docs/flows/api.md (API keys). Tests: tests/e2e/test_api_keys.py. --%>
<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>

<%@include file="/curate/adminCheck.jsp" %>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
        <tiles:put name="title" value="API keys" />
        <tiles:put name="body-content" value="/curate/apiKeys-body.jsp" />
</tiles:insert>
