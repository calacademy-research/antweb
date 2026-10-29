<%-- Image comparison page of chosen search results (compareResults.do without shot, CompareResultsAction),
     logged-in users only (checked in the action): tiles wrapper of search/multiTaxaComparison-body.jsp, title
     "Comparison Tool - AntWeb". Docs: docs/flows/search.md. Tests: tests/e2e/test_login_needed.py. --%>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Comparison Tool - AntWeb" />
	<tiles:put name="body-content" value="/search/multiTaxaComparison-body.jsp" />	
</tiles:insert>
