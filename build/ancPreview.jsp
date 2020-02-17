<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@ page import = "org.calacademy.antweb.Group" %>


<%@include file="/curate/curatorCheck.jsp" %>

<bean:define id="mode" toScope="request" value="preview"/>

<jsp:useBean id="ancFile" scope="session" class="org.calacademy.antweb.AncFile" />
<jsp:setProperty name="ancFile" property="*" />

<%
   	String realPage = ancFile.getPreviewPageURL();
	response.sendRedirect(realPage);
%>


