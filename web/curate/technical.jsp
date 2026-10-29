<%-- Technical page (technical.do, a forward with no action class), admins only (adminCheck.jsp). Tiles wrapper of
     curate/technical-body.jsp. Docs: docs/guides/curate-page.md (Technical page). Tests:
     tests/e2e/test_curate_merge.py, test_admin_staging.py. --%>
<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@include file="/curate/adminCheck.jsp" %>

<bean:define id="project" value="" toScope="session"/>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title" value="Technical page - AntWeb" />
    <tiles:put name="body-content" value="/curate/technical-body.jsp" />
</tiles:insert>
