<%-- Server Status page (serverStatus.do, ServerStatusAction), admins only (adminCheck.jsp). Tiles wrapper of
     util/serverStatus-body.jsp. Docs: docs/catalog/actions.csv. Tests: tests/e2e/test_admin_staging.py,
     test_request_handling.py, test_tool_pages_style.py. --%>
<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>

<%@include file="/curate/adminCheck.jsp" %>

<%
        session.removeAttribute("taxon");
%>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Server Status" />
	<tiles:put name="body-content" value="/util/serverStatus-body.jsp" />	
</tiles:insert>



