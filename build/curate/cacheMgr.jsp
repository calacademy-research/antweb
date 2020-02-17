
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<%
  Utility util = new Utility();
  String domainApp = util.getDomainApp();
%>  

<!-- %@include file="/curate/curatorCheck.jsp" % -->

<title>Cache Manager - Antweb</title>

<h1>Antweb Cache Manager</h1>
<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>

Current Functions:
<ul>
	  <li><a href="<%= domainApp %>/cache.do?action=display">Display</a> Long Requests.
	  <!-- li><a href="< %= domainApp % >/cache.do?action=keepCurrent">Purge</a> Cache (remove old long_request records and caches). -->
      <li><a href="<%= domainApp %>/cache.do?url=all">Show</a> All Long Requests
	  <li><a href="<%= domainApp %>/cache.do?action=genCacheThread">Gen Cache Thread</a> - Should be invoked from cron job.  Here for testing only.
	  <li><a href="<%= domainApp %>/cache.do?action=genCacheItem">Gen Cache Item</a>
	  <li><a href="<%= domainApp %>/cache.do?action=purgeCache">Purge Cache</a> (delete cache files and records)
	  <li><a href="<%= domainApp %>/cache.do?action=forgetCaching">Forget Caching</a> (update cache records to be uncached)
</ul>

<!--
Deprecated functions:
<ul>
	  <li><a href="< %= domainApp % >/cache.do?action=deleteCaches">Delete</a> Cache.
	  <li><a href="< %= domainApp % >/cache.do?action=purgeCaches">Purge</a> Cache (remove all long_request records and delete the cache).
	  <li><a href="< %= domainApp % >/cache.do?action=genCache">Generate</a> Cache (deprecated).
	  <li><a href="< %= domainApp % >/cache.do?action=genCacheItem">Generate</a> a Cache Item.
</ul>
-->
<table border=1><tr>
  <td>#</td>
  <td><a href="<%= AntwebProps.getDomainApp() %>/cache.do?action=display&orderBy=id">ID</a></td>
  <td><a href="<%= AntwebProps.getDomainApp() %>/cache.do?action=display&orderBy=cache_type">Cache Type</a></td>
  <td><a href="<%= AntwebProps.getDomainApp() %>/cache.do?action=display&orderBy=url">URL</a></td>
  <td><a href="<%= AntwebProps.getDomainApp() %>/cache.do?action=display&orderBy=millis">Millis</a></td>
  <td><a href="<%= AntwebProps.getDomainApp() %>/cache.do?action=display&orderBy=cache_millis">Cache Millis</a></td>
  <td><a href="<%= AntwebProps.getDomainApp() %>/cache.do?action=display&orderBy=created">Created</a></td>
  <td><a href="<%= AntwebProps.getDomainApp() %>/cache.do?action=display&orderBy=cached">Cached</a></td>
  <td><a href="<%= AntwebProps.getDomainApp() %>/cache.do?action=display&orderBy=busy_connections">Conns</a></td>
  <td><a href="<%= AntwebProps.getDomainApp() %>/cache.do?action=display&orderBy=is_logged_in, created">Logged In</a></td>
  <td><a href="<%= AntwebProps.getDomainApp() %>/cache.do?action=display&orderBy=is_bot">Bot</a></td>
  <td>Get Cache</td>  
  <td>Gen Cache</td>  
</tr>
<%
  ArrayList<LongRequest> longRequests = (ArrayList) request.getAttribute("longRequests");
  int i = 0;
  for (LongRequest longRequest : longRequests) { 
    ++i;
    //String dirStr = longRequest.getDirFile();
    String cacheMillisStr = "";
    if (!"".equals(longRequest.getCacheDate())) {
      //dirStr = "<a href=\"" + domainApp + "/web/" + longRequest.getDirFile() + "\">" + longRequest.getDirFile() + "</a>";  
      cacheMillisStr = "" + longRequest.getCacheMillis();
    }  
  %>
  <tr>
    <td><a href="<%= domainApp %>/cache.do?url=<%= java.net.URLEncoder.encode(longRequest.getUrl()) %>"><%= i %></a></td>
    <td><%= longRequest.getId() %></td>
    <td><%= longRequest.getCacheType() %></td>
    <td><a href="<%= longRequest.getUrl() %>"><%= longRequest.getUrl() %></a></td>
    <!-- td>< %= dirStr % ></td -->
    <td><%= longRequest.getMaxMillis() %></td>
    <td><%= cacheMillisStr %></td>
    <td><%= longRequest.getCreateDate() %></td>
    <td><%= longRequest.getCacheDate() %></td>
    <td><%= longRequest.getBusyConnections() %></td>
    <td><%= longRequest.getIsLoggedIn() %></td>
    <td><%= longRequest.getIsBot() %></td>

    <td><a href="<%= longRequest.getUrl() %>&getCache=true"><img src="<%= AntwebProps.getDomainApp() %>/image/view_icon.png" height="13" width="13" /></a></td>
    <td><a href="<%= longRequest.getUrl() %>&genCache=true"><img src="<%= AntwebProps.getDomainApp() %>/image/modifyPen.jpeg" height="13" width="13" /></a></td>


  </tr>
<% } %>            
</table>

<br>
<hr>
