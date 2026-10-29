<%-- Adm1 Manager page (adm1Mgr.do, Adm1MgrAction), curators (curatorCheck.jsp). Tiles wrapper of
     curate/geolocale/adm1Mgr-body.jsp. Docs: docs/guides/manager-tools.md (Adm1 Manager). --%>
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
	<tiles:put name="title" value="Adm1 Manager" />
	<tiles:put name="body-content" value="/curate/geolocale/adm1Mgr-body.jsp" />	
</tiles:insert>



