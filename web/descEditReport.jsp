<%-- Tiles page of recentDescEdits.do (RecentDescEditsAction): "Description Edit Report - AntWeb", body
     descEditReport-body.jsp. Tests: tests/e2e/test_check57_merges.py::test_recent_desc_edits_page. --%><%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="showNav" value="search" toScope="request"/>

<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Description Edit Report - AntWeb" />
	<tiles:put name="body-content" value="/descEditReport-body.jsp" />	
</tiles:insert>
