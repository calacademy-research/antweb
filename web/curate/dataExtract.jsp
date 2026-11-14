<%-- Data extract admin page (dataExtract.do, curate/DataExtractAction, administrators only): the form that starts a
     complete, verified specimen extract for a collaborator, and the extracts kept on the server with their download
     links. Tiles wrapper of curate/dataExtract-body.jsp. Docs: docs/guides/data-extract.md. Tests:
     tests/e2e/test_data_extract.py. --%>
<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>

<%@include file="/curate/adminCheck.jsp" %>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
        <tiles:put name="title" value="Data extract" />
        <tiles:put name="body-content" value="/curate/dataExtract-body.jsp" />
</tiles:insert>
