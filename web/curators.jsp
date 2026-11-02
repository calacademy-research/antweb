<%-- Tiles page of curators.do (CuratorAction without name, id or curation parameters): "Curators", body
     curators-body.jsp. Docs: docs/numbers.md rows 80 and 81. Tests: tests/e2e/test_curate_page_layout.py. --%><%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Curators" />
	<tiles:put name="body-content" value="/curators-body.jsp" />	
</tiles:insert>
