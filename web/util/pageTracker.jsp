<%@ page import="org.calacademy.antweb.util.*" %>

<br>Request Details:
<%
Tracker thisTracker = PageTracker.getTracker(request);
if (thisTracker != null) { %>
<br>&nbsp;&nbsp;Target:<%= thisTracker.getTarget() %>
<br>&nbsp;&nbsp;Response time:<%= thisTracker.getSinceStartTime() %>
<% } else { %>
<br>&nbsp;&nbsp;Tracker not found
<% }%>

<br>Server Details:
<br>&nbsp;&nbsp;Server Busy Connection Count:<%= DBUtil.getServerBusyConnectionCount() %>

<br>&nbsp;&nbsp;Request Count:<%= PageTracker.getRequestCount() %>

<br>&nbsp;&nbsp;Target list:
<%
    for (Tracker tracker : PageTracker.getTrackers()) { %>
      <br>&nbsp;&nbsp;&nbsp;&nbsp;<%= tracker.getTarget() %> <%= tracker.getSinceStartTime() %>
<%  } %>
