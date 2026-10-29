<%-- Dispute Manager page (disputeMgr.do, DisputeMgrAction), curators (curatorCheck.jsp). Tiles wrapper of
     curate/speciesList/disputeMgr-body.jsp. Docs: docs/guides/manager-tools.md (Geolocale Manager links). --%>
<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>

<%@include file="/curate/curatorCheck.jsp" %>

<bean:define id="project" value="" toScope="session"/>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Dispute Manager" />
	<tiles:put name="body-content" value="/curate/speciesList/disputeMgr-body.jsp" />	
</tiles:insert>
