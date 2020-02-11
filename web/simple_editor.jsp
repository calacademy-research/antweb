<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>

<%@ page import = "org.calacademy.antweb.Group" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>


<%@include file="/curate/adminCheck.jsp" %>


<html:html locale="true">
<head>
<title>Simple Editor</title>
<html:base/>
</head>
<body bgcolor="white">

<h1>Simple Editor</h1>


<html:form method="POST" action="simpleContentEditorWrite.do">
  <html:textarea property="contents" rows="20" cols="80"/>
  <html:submit/>
</html:form>


</body>
</html:html>
