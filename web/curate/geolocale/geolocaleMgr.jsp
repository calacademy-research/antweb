<%-- Geolocale Manager page (geolocaleMgr.do, GeolocaleMgrAction), curators look (curatorCheck.jsp). Tiles wrapper
     of curate/geolocale/geolocaleMgr-body.jsp. Docs: docs/guides/manager-tools.md (Geolocale Manager). Tests:
     tests/e2e/test_geolocale_edit_page.py, test_manager_tools.py. --%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>

<%@include file="/curate/curatorCheck.jsp" %>

<bean:define id="project" value="" toScope="session"/>
<%
        session.removeAttribute("taxon");
%>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Geolocale Manager" />
	<tiles:put name="body-content" value="/curate/geolocale/geolocaleMgr-body.jsp" />	
</tiles:insert>
