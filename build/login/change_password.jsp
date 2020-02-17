<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ page import="org.calacademy.antweb.Group" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>



<%@include file="/curate/curatorCheck.jsp" %>

<%
	String groupName = accessGroup.getName();	
%>

<html:html locale="true">
<head>
<title>Changing Password for <%= groupName %></title>
<html:base/>
</head>
<body bgcolor="white">

<h1>Change Password for for <%= groupName %></h1>

<html:messages id="message" message="true">
<font color="red" fact="tahoma,arial,helvetica" size="4">
<bean:write name="message"/><br>
</font>
</html:messages>

 
<html:form method="POST" action="changePassword">
  <p>
  <table>
  <tr><td>Old Password:</td><td><html:password property="oldPassword"/></td></tr>
  <tr><td>New Password:</td><td><html:password property="newPassword1"/></td></tr>
  <tr><td>New Password Again:</td><td><html:password property="newPassword2"/></td></tr>
  </table>
  </p>
  
  
  <html:submit />
</html:form>


</body>
</html:html>
