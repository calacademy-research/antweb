<%-- Statistics snapshot page of statistics.do without a by... parameter, or with byUpload=true
     (StatisticsAction, logged-in users): the rows of table statistics as HTML (request attribute "statistics")
     and a link to make a new snapshot (upload.do?action=runStatistics). --%>

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
<link rel="shortcut icon" href="<%= domainApp %><%= org.calacademy.antweb.util.Asset.url(application, "/image/favicon.ico") %>" />
</head>
<h1>Antweb Realtime Statistics</h1>

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>

<br><br>
<a href='<%= domainApp %>/statistics.do'>Refresh</a> | <a href="<%= domainApp %>/upload.do?action=runStatistics">Generate</a> a new Statistics snapshot (usually not necessary).

<%
  String statistics = (String) request.getAttribute("statistics");
  out.println(statistics == null ? "<p>No statistics were read (see the log).</p>" : statistics);
%>
