<%-- Your Curator Files page (curatorFiles.do, CuratorFilesAction): the logged-in curator's folder
     web/curator/<login id>/, where page-text images are uploaded (descEditImageUpload.do). Curators and admins
     (curatorCheck.jsp). Tiles wrapper of curate/curatorFiles-body.jsp. Tests: tests/e2e/test_curator.py. --%>
<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>

<% if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return; %>

<%@include file="/curate/curatorCheck.jsp" %>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
        <tiles:put name="title" value="Your Curator Files - AntWeb" />
        <tiles:put name="body-content" value="/curate/curatorFiles-body.jsp" />
</tiles:insert>
