<%-- Introduced Map Manager page (introducedMapMgr.do, IntroducedMapMgrAction; the action allows admins only, this
     page checks for a curator). Tiles wrapper of curate/introducedMapMgr-body.jsp. Docs:
     docs/guides/manager-tools.md. Tests: tests/e2e/test_manager_tools.py, test_hardening_2026_10_18.py. --%>
<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>

<%@include file="/curate/curatorCheck.jsp" %>

<%
        session.removeAttribute("taxon");
%>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Introduced Map Manager" />
	<tiles:put name="body-content" value="/curate/introducedMapMgr-body.jsp" />	
</tiles:insert>



