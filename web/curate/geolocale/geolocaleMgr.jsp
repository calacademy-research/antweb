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
