<%-- Session Properties page (sessionProps.do, a forward with no action class), admins only (adminCheck.jsp):
     developer debugging. Tiles wrapper of util/sessionProps-body.jsp. Docs: docs/catalog/actions.csv. Tests:
     tests/e2e/test_hardening_2026_10_19.py, test_curate_merge.py. --%>
<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>

<%@include file="/curate/adminCheck.jsp" %>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Session Properties" />
	<tiles:put name="body-content" value="/util/sessionProps-body.jsp" />	
</tiles:insert>


