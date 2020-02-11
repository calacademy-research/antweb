
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<%
  // This page in response to statistics.do

  Utility util = new Utility();
  String domainApp = util.getDomainApp();
%>  
<head>
<title>Antweb Realtime Statistics</title>
<link rel="shortcut icon" href="<%= domainApp %>/image/favicon.ico" />
</head>
<h1>Antweb Realtime Statistics</h1>

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>

<br><br>
<a href='<%= domainApp %>/statistics.do'>Refresh</a> | <a href="<%= domainApp %>/upload.do?action=runStatistics">Generate</a> a new Statistics snapshot (usually not necessary).

<%
  String statistics = (String) session.getAttribute("statistics");
  out.println(statistics);
%>
