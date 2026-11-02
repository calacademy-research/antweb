<%-- Tiles page of artist.do's artists forward (artists.do forwards to artist.do; no id or name, or the Artist
     Manager after a change): "Artists", body artists-body.jsp. Docs: docs/numbers.md row 92. Tests:
     tests/e2e/test_manager_tools.py::test_photographers_public. --%><%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Artist" %>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Artists" />
	<tiles:put name="body-content" value="/artists-body.jsp" />	
</tiles:insert>
