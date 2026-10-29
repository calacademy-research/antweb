<%-- Name update report page (nameUpdates.do, NameUpdatesAction): specimens moved to the current valid name by
     the nightly name update (curators see their own group). Tiles wrapper of curate/nameUpdates-body.jsp.
     Docs: docs/flows/antcat-to-antweb.md. Tests: tests/e2e/test_name_updates.py. --%>
<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>

<%@include file="/curate/curatorCheck.jsp" %>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
        <tiles:put name="title" value="Name update report" />
        <tiles:put name="body-content" value="/curate/nameUpdates-body.jsp" />
</tiles:insert>
