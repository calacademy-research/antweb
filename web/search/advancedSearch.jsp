<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<!-- % @ page language="java" % -->
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%  response.setHeader ("pragma", "no-cache");
	response.setHeader ("Cache-Control", "no-cache");
	response.setHeader ("Expires", "-1"); %>

<logic:present parameter="project">
   <% session.removeAttribute("project"); %>
</logic:present>
<%
        session.removeAttribute("project");
        session.removeAttribute("taxon");
%>

<!-- so the navigation knows which page it is on -->
<bean:define id="showNav" value="search" toScope="page"/>	

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Advanced Search - AntWeb" />
	<tiles:put name="body-content" value="/search/advancedSearch-body.jsp" />	
</tiles:insert>
	
