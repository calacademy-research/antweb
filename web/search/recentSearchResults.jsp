<%-- Recently added images page (recentSearchResults.do?searchMethod=recentImageSearch&daysAgo=N, SearchAction),
     public: tiles wrapper of search/recentSearchResults-body.jsp, title "Recently Added Specimen Images - AntWeb".
     Docs: docs/flows/search.md. Tests: tests/e2e/test_search_pages.py::test_recent_images. --%>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="showNav" value="search" toScope="request"/>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Recently Added Specimen Images - AntWeb" />
	<tiles:put name="body-content" value="/search/recentSearchResults-body.jsp" />	
</tiles:insert>
