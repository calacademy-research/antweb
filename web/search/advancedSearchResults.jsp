<%-- Advanced search results by specimen (advancedSearch.do, advancedSearchResults.do; mode=compare or map from
     prepareCompareResults.do and prepareMapResults.do), public: tiles wrapper of
     search/advancedSearchResults-body.jsp, title "Advanced Specimen Search - AntWeb"; a direct request of this JSP
     is refused (isStaticCallCheck). Docs: docs/flows/search.md. Tests: tests/e2e/test_search_pages.py,
     test_download_guards.py. --%>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="showNav" value="search" toScope="request"/>
<% session.removeAttribute("taxon"); 

   if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
%>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Advanced Specimen Search - AntWeb" />
	<tiles:put name="body-content" value="/search/advancedSearchResults-body.jsp" />	
</tiles:insert>
