<%-- Statistics table page of statistics.do (StatisticsAction, logged-in users) for byProject, byBioregion,
     byMuseum, byGeolocale or project=: title, links, then util/statisticsRealTime-body.jsp. With body=true the
     action answers the body alone. --%>

<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<%
  Utility util = new Utility();
  String domainApp = util.getDomainApp();
%>  
<head>
<title>Antweb Realtime Statistics.</title>
<link rel="shortcut icon" href="<%= domainApp %><%= org.calacademy.antweb.util.Asset.url(application, "/image/favicon.ico") %>" />
</head>

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>
<%@include file="/util/statisticsRealTime-body.jsp" %>