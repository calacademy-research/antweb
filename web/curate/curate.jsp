<%-- Curate page (curate.do, CurateAction): the curator tools home, for logged-in curators and admins
     (curatorCheck.jsp). Tiles wrapper; the content is curate/curate-body.jsp.
     Tests: tests/e2e/test_curate_page_layout.py, test_curate_page_clarity.py. --%>
<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>

<% if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return; %>

<%@include file="/curate/curatorCheck.jsp" %>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
        <tiles:put name="title" value="AntWeb Curator Tools" />
        <tiles:put name="body-content" value="/curate/curate-body.jsp" />
</tiles:insert>
