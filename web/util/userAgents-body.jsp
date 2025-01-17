
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>

<div class="admin_left">

<%
  String domainApp = AntwebProps.getDomainApp();
%>

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>
<br>


<h2>User Agent Tracker</h2>


    <%= UserAgentTracker.getDataAsHtml() %>


    <%= UserAgentTracker.getAgentSummaryReport() %>

</div>
