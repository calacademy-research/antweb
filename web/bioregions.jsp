<%-- Tiles page of bioregions.do (OverviewAction): the list of every such place with its counts, "BioRegions -
     AntWeb", body overviews-body.jsp. Docs: docs/flows/places.md, docs/numbers.md rows 73 to 77. Tests:
     tests/e2e/test_place_pages.py::test_list_and_place_page. --%><%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="showNav" value="search" toScope="request"/>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="BioRegions - AntWeb" />
	<tiles:put name="body-content" value="/overviews-body.jsp" />	
</tiles:insert>
