<%-- Daily update page (dailyUpdate.do, DailyUpdateAction): where AntWeb's daily update (05:00 Pacific) is, step by
     step, with the usual time of each step (curators and admins). Tiles wrapper of util/dailyUpdate-body.jsp. The JSON
     (dailyUpdate.do?format=json) is written by the action and never reaches this page. Docs: docs/flows/nightly.md.
     Tests: tests/e2e/test_daily_update.py. --%>
<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>

<%@include file="/curate/curatorCheck.jsp" %>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
        <tiles:put name="title" value="Daily update" />
        <tiles:put name="body-content" value="/util/dailyUpdate-body.jsp" />
</tiles:insert>
