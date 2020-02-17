<%@ page errorPage = "error.jsp" %>
<%@ page import = "org.calacademy.antweb.Group" %>
<%@ page import = "org.calacademy.antweb.util.AntwebProps" %>
<%@ page 
        language="java" 
        contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!-- %@ taglib prefix="html" uri="/struts-tags" % -->

<%
    Group accessGroup = GroupMgr.getAccessGroup(request);
    session.removeAttribute("taxon");
%>
<html>
<head>
<title>Image Uploader Guts</title>
<link href="common/antweb_style.css" rel="stylesheet" type="text/css">
<link href="common/admin_style.css" rel="stylesheet" type="text/css">
</head>
<body style="background-color:#fff;" onLoad="document.theForm.submit();">
<% if (AntwebProps.isDevMode()) { 
  out.println("Image Upload Frame.  Because isDevMode, do not invoke upload.php for contents.");
} else { 
//Insecure
%>
<form name="theForm" method="POST" action="<%= AntwebProps.getDomainApp() %>/imageUpload/upload.php">
<input type="hidden" name="group" value="<%= accessGroup.getId() %>">
</form>
<% } %>
</body>
</html>
