<%-- Tiles page of locality.do (LocalityAction): "Locality - AntWeb", body locality-body.jsp. Removes the session
     attribute taxon. Docs: docs/flows/places.md. Tests: tests/e2e/test_specimen_pages.py::
     test_locality_page_lists_the_specimens_collection. --%><%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="showNav" value="search" toScope="request"/>
<% session.removeAttribute("taxon"); %>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Locality - AntWeb" />
	<tiles:put name="body-content" value="/locality-body.jsp" />	
</tiles:insert>
