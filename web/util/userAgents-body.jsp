
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>

<div class="left">

<%
  String domainApp = AntwebProps.getDomainApp();
%>

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>
<br>

<br><h1>User Agent Tracker</h1>

<br><br><br><h2>Documentation</h2>

<br>&nbsp;&nbsp;&nbsp;&nbsp;
The User Agent Tracker is an in-memory list of the last MAX_QUEUE_SIZE web requests. If the last MAX_QUEUE_SIZE web
requests happen in too short of a time, which is less than PERIOD_IN_SECONDS, then we begin blocking bots. We blocking
the bots that are making more than ALLOWED_PERCENT of the list.

<br>&nbsp;&nbsp;&nbsp;&nbsp;
The "Most Recent Requests Queue Size" will quickly grow to reach the MAX_QUEUE_SIZE after a server restart. The "Span
in seconds" is the amount of time between the first and last web requests in the queue.

<br>&nbsp;&nbsp;&nbsp;&nbsp;
Requests from logged in user agents have their names in bold in the lists below, with an asterix.

<br>&nbsp;&nbsp;&nbsp;&nbsp;
The logic of this tool may need to evolve with traffic. The logic is in UserAgentTracker.blockTest().


<br><br><br><h2>Stats</h2>

    <%= UserAgentTracker.getStatsAsHtml() %>

<br><br><br><h2>Summary Report</h2>

    <%= UserAgentTracker.getAgentSummaryReport() %>

<br><br><br><h2>Requests:</h2>

    <%= UserAgentTracker.getRequestsAsHtml() %>

</div>