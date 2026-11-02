<%-- Tiles page of groups.do (GroupAction without name or id; orderBy sorts): "Groups", body groups-body.jsp. Docs:
     docs/numbers.md rows 85 to 90. Tests: tests/e2e/test_curate_page_layout.py. --%><%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Groups" />
	<tiles:put name="body-content" value="/groups-body.jsp" />	
</tiles:insert>
