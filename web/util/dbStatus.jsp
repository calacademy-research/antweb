<%-- Database Status page (dbStatus.do, DbStatusAction), admins only (adminCheck.jsp): busy connection count,
     the open and old connections (DBUtil), the three connection pools' diagnostics and the MySQL process list,
     from request attributes the action sets. Read-only. Docs: docs/catalog/actions.csv. Tests:
     tests/e2e/test_curate_merge.py, test_request_handling.py. --%>

<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.upload.*" %>
<%@ page import="org.calacademy.antweb.home.*" %>

<%@ page import="com.mchange.v2.c3p0.impl.*" %>

<%
  Utility util = new Utility();
  String domainApp = util.getDomainApp();
%>  

<%@include file="/curate/adminCheck.jsp" %>

<% 
//AdminUpdatesAction.add("dbStatus"); 
%>

<title>Database Status</title>

<h1>Antweb DB Status</h1>
<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a> | <a href='<%= domainApp %>/dbStatus.do'>Refresh</a><br><br><br>

Busy Connections:<%= DBUtil.getServerBusyConnectionCount() %>

<%
String connString = DBUtil.getConnectionList();  
      //A.log("connString:" + connString);
%>
<pre>
<br><h3>Connection List:</h3><%= Html.esc(connString).replace("&lt;br&gt;", "\n") %>
</pre>
<br>
<%
String oldConnString = DBUtil.getOldConnectionList();  
      //A.log("connString:" + connString);
%>
<pre>
<br><h3>Old Connection List:</h3><%= Html.esc(oldConnString).replace("&lt;br&gt;", "\n") %>
</pre>
<br>
<%
  String cpStats = (String) request.getAttribute("cpDiagnostics");
  //AntwebUtil.log("info", domainApp + "/dbStatus.do.  cpStats:" + cpStats );
%>
<pre>
<br><h3>Connection Pool Diagnostics:</h3><%= cpStats %>
</pre>

<pre>
<br><h3>Medium Connection Pool Diagnostics:</h3><%= request.getAttribute("mediumConPoolDiagnostics") %>
</pre>

<pre>
<br><h3>Long Connection Pool Diagnostics:</h3><%= request.getAttribute("longConPoolDiagnostics") %>
</pre>


<%
  String mySqlProcessListHtml = (String) request.getAttribute("mySqlProcessListHtml");
  //A.log("ProcessList:" + mySqlProcessListHtml);
%>
<pre>
<br><h3>MySql Process List:</h3>
<%= mySqlProcessListHtml %> 
</pre>

<h3>Assorted Links</h3>
<br>

<hr>

