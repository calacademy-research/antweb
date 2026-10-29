<%-- Advanced search results by subfamily, genus or species (advancedSearch.do with resultRank; mode=compare or map
     from the Prepare actions), public: tiles wrapper of search/advancedSearchResultsByTaxon-body.jsp, title
     "Advanced Specimen Search - AntWeb". Docs: docs/flows/search.md. Tests:
     tests/e2e/test_search_pages.py::test_search_by_genus_rank_lists_genera. --%>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="showNav" value="search" toScope="request"/>
<% session.removeAttribute("taxon"); %>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Advanced Specimen Search - AntWeb" />
	<tiles:put name="body-content" value="/search/advancedSearchResultsByTaxon-body.jsp" />	
</tiles:insert>
