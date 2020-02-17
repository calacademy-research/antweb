
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<%
  Utility util = new Utility();
  String domainApp = util.getDomainApp();
%>  

<!-- %@include file="/curate/curatorCheck.jsp" % -->

<title>Cache Detail - Antweb</title>

<h1>Antweb Cache Detail</h1>
<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a> | <a href = "<%= domainApp %>/cache.do?action=display">Cache Manager</a><br><br><br>

<ul>
	  <li><a href="<%= domainApp %>/cache.do?action=display">Display</a> Long Requests.
	  <li><a href="<%= domainApp %>/cache.do?action=deleteCaches">Delete</a> Cache.
	  <li><a href="<%= domainApp %>/cache.do?action=genCache">Generate</a> Cache.
</ul>

<table border=1><tr><td>#</td><td> Cache Type </td><td> URL </td><td>Dir File</td><td>Millis</td><td>Cache Millis</td><td>Request Time</td><td>Request Info</td><td>Busy</td></tr>
<%
  ArrayList<LongRequest> longRequests = (ArrayList) request.getAttribute("longRequests");
  int i = 0;  
  for (LongRequest longRequest : longRequests) { 
    ++i;
    String cacheMillisStr = "";
    if (!"".equals(longRequest.getCacheDate())) {
      cacheMillisStr = "" + longRequest.getCacheMillis();
    }  
  %>
  <tr><td><%= i %></td><td><%= longRequest.getCacheType() %></td><td><a href="<%= longRequest.getUrl() %>"><%= longRequest.getUrl() %></a></td><td><%= longRequest.getDirFile() %></td><td><%= longRequest.getMaxMillis() %></td><td><%= cacheMillisStr %></td><td><%= longRequest.getCacheDate() %></td><td><%= longRequest.getRequestInfo() %></td><td><%= longRequest.getBusyConnections() %></td></tr>
<% } %>            
</table>

<br>
<hr>
