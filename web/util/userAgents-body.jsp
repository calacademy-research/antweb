
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.data.*" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Map" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.upload.*" %>
<%@ page import="org.calacademy.antweb.home.*" %>

<%@ page import="com.mchange.v2.c3p0.impl.*" %>

<%
  Utility util = new Utility();
  String domainApp = util.getDomainApp();
%>

<div class="admin_left">

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>
<br>

<h2>User Agent Manager</h2>

<br><b><font color=red>X</font></b> means that the agent is a known bot.
<br><b><font color=red><img src="<%= AntwebProps.getDomainApp() %>/image/greenCheck.png"></font></b> means that the user is logged in.
<br>

<br><a href=<%= AntwebProps.getDomainApp() %>/userAgents.do?name=>Reload</a>
<br>
<br><b>Run Time in hrs: </b><%= AntwebUtil.hrsSince(SessionRequestFilter.getInitTime()) %>. <b>In mins: </b><%= AntwebUtil.minsSince(SessionRequestFilter.getInitTime()) %>.
<br><b>Server Start: </b><%= SessionRequestFilter.getInitTime() %>
<br><b>Refreshed: </b><%= UserAgentTracker.getLastRefresh() %> &nbsp; <a href=<%= AntwebProps.getDomainApp() %>/userAgents.do?name=refresh>Refresh</a>
<br><br><b>Summary:</b> <%= UserAgentTracker.htmlSummary() %>

<br><br><b>User Agents:</b>
<%= UserAgentTracker.htmlUserAgents() %>

<br><br><b>White List:</b>
<%= UserAgentTracker.htmlWhiteList() %>

<br><br><b>Known Agents:</b>
<%= UserAgentTracker.htmlKnownAgents() %>

</div>
