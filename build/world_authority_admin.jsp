<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import = "org.calacademy.antweb.Group" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" toScope="session" value=""/>


<%@include file="/curate/adminCheck.jsp" %>


<%
        session.removeAttribute("taxon");
%>

<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Bolton's Catalog Administration - AntWeb" />
	<tiles:put name="body-content" value="/world_authority_admin-body.jsp" />	
</tiles:insert>
