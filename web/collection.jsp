<%-- Tiles page of collection.do (CollectionAction; also prepareCompareResults.do's collectionSearch forward, with
     mode=compare): "Collection Search - AntWeb", body collection-body.jsp. Removes the session attribute taxon.
     Docs: docs/flows/places.md. Tests: tests/e2e/test_specimen_pages.py::test_collection_page_lists_the_specimen. --%><%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@ page import="org.calacademy.antweb.Overview" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<bean:define id="showNav" value="search" toScope="request"/>
<% session.removeAttribute("taxon"); %>

<%
Overview overview = OverviewMgr.getOverview(request);
%>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Collection Search - AntWeb" />
	<tiles:put name="body-content" value="/collection-body.jsp" />	
</tiles:insert>
