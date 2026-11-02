<%-- Unused as of 2026-11-02: a tiles page "Groups" with the body contributors-body.jsp. No action forwards here
     (groups.do uses groups.jsp), and Caddy answers a direct request with 404 (not on the JSP allow list in
     docker/caddy/Caddyfile). --%><%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Groups" />
	<tiles:put name="body-content" value="/contributors-body.jsp" />	
</tiles:insert>
