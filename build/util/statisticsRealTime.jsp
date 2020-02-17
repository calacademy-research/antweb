
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<%
  Utility util = new Utility();
  String domainApp = util.getDomainApp();
%>  
<head>
<title>Antweb Realtime Statistics.</title>
<link rel="shortcut icon" href="<%= domainApp %>/image/favicon.ico" />
</head>

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>
<%@include file="/util/statisticsRealTime-body.jsp" %>