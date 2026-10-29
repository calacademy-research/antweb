<%-- Upload status page (jobStatus.do, JobStatusAction): uploads and admin data jobs run in the background
     (BackgroundJobs, table background_job), for curators and admins (curatorCheck.jsp). Tiles wrapper of
     curate/jobStatus-body.jsp. Docs: docs/flows/specimen-upload.md (section 3a). Tests: tests/e2e/test_background_jobs.py. --%>
<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>

<%@include file="/curate/curatorCheck.jsp" %>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
        <tiles:put name="title" value="Upload status" />
        <tiles:put name="body-content" value="/curate/jobStatus-body.jsp" />
</tiles:insert>
