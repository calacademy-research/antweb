	
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Map" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.upload.*" %>
<%@ page import="org.calacademy.antweb.home.*" %>
<%@ page import="com.mchange.v2.c3p0.impl.*" %>

<%
  String message = "serverStatus-body.jsp message:";
  Utility util = new Utility();
  String domainApp = util.getDomainApp();
%>

<%-- Tool-page look (common/tools.css, docs/guides/page-style.md; 2026-10-22): the same values and links as
     before, as label/value tables in cards. Tested in tests/e2e/test_tool_pages_style.py and
     tests/e2e/test_admin_staging.py. --%>
<%@include file="/common/toolsHead.jsp" %>
<%!
  // A true/false flag as a coloured word: red when true means busy or locked.
  static String flag(boolean busy) {
    return busy ? "<span class=\"aw-bad\">true</span>" : "<span class=\"aw-ok\">false</span>";
  }
%>
<div class="aw-tools" id="server-status-page">
<p class="aw-crumbs"><a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a></p>
<h1>Server Status</h1>
<div class="aw-intro"><p>What the AntWeb application is doing now: requests, uploads and jobs in progress, memory, disk and database
connections, and the server's settings. Look here before restarting the server (wait while an upload is in process or the image
upload is locked) or when the site is slow.</p></div>

<% // Object o = null;  o.toString(); // uncomment this line to create stacktrace %>

<div class="aw-card" id="server-operation">
<h2>Antweb Operation</h2>
<table class="aw-kv">
<tr><th scope="row">Concurrent Requests</th><td><%= SessionRequestFilter.getConcurrentRequests() %></td></tr>
<tr><th scope="row">Requests per second</th><td><%= SessionRequestFilter.getRequestsPerSecond() %></td></tr>
<tr><th scope="row">isServerBusy</th><td><%= request.getAttribute("isServerBusy") %></td></tr>
<tr><th scope="row">Is Upload in process</th><td><%= flag(UploadAction.isInUploadProcess()) %></td></tr>
<tr><th scope="row">Is Compute in process</th><td><%= flag(UtilDataAction.isInComputeProcess()) %></td></tr>
<%
// Uploads and admin data jobs started from a page run in the background (jobStatus.do).
String jobRunning = org.calacademy.antweb.util.BackgroundJobs.runningDescription();
java.util.List<Integer> jobsActive = org.calacademy.antweb.util.BackgroundJobs.get().activeIds();

OperationLock operationLock = (OperationLock) request.getAttribute("operationLock");
boolean imageLocked = (operationLock != null) && (operationLock.isLocked()) && (!operationLock.isExpired());
boolean otherOption = !UptimeAction.isFailOnPurpose();
%>
<tr><th scope="row">Background job running</th><td><%= jobRunning == null ? "<span class=\"aw-ok\">none</span>" : "<span class=\"aw-bad\">" + org.calacademy.antweb.util.Html.esc(jobRunning) + "</span>" %>
 (<%= jobsActive.size() %> waiting or running; <a href="<%= AntwebProps.getDomainApp() %>/jobStatus.do">Upload status</a>)</td></tr>
<tr><th scope="row">Is Image Upload locked</th><td><%= flag(imageLocked) %></td></tr>
<tr><th scope="row">Upload services</th><td>To take On/Off line: <%= org.calacademy.antweb.util.Csrf.postButton(request, domainApp + "/serverStatus.do?action=toggleDownTime", "Toggle Down Time") %>
<br><%= ServerDb.getDownTimeMessage() %></td></tr>
<tr><th scope="row">Uptime Fail On Purpose</th><td><%= UptimeAction.isFailOnPurpose() %>  <%= org.calacademy.antweb.util.Csrf.postButton(request, AntwebProps.getDomainApp() + "/uptime.do?fail=" + otherOption, "[toggle fail]") %></td></tr>
</table>
<p class="aw-note">If an upload is in process, or the image upload is locked, it is best to wait before restarting the server.</p>
</div>

<div class="aw-card" id="server-resources">
<h2>Time, memory and connections</h2>
<table class="aw-kv">
<tr><th scope="row">Current Time</th><td><%= (new Date()).toString() %></td></tr>
<tr><th scope="row">Server Start Time</th><td><%= (SessionRequestFilter.getInitTime()).toString() %></td></tr>
<tr><th scope="row">Run Time</th><td><%= AntwebUtil.hrsSince(SessionRequestFilter.getInitTime()) %> hrs (<%= AntwebUtil.minsSince(SessionRequestFilter.getInitTime()) %> mins)</td></tr>
<tr><th scope="row">AntwebMgr (re)Start Time</th><td><%= AntwebMgr.getStartTime() %> (<%= AntwebUtil.hrsSince(AntwebMgr.getStartTime()) %> hrs)</td></tr>
<tr><th scope="row">CPU</th><td><%= AntwebSystem.getCpuLoad() %></td></tr>
<tr><th scope="row">Disk</th><td><%= FileUtil.getDiskStats() %></td></tr>
<%
  String memoryStat = AntwebUtil.getMemoryStats();
  AntwebUtil.log("info", domainApp + "/serverStatus.jsp.  memory:" + memoryStat);
  String topReport = AntwebSystem.getTopReport();
  message += topReport;
%>
<tr><th scope="row">Memory Stats</th><td><%= memoryStat %></td></tr>
<tr><th scope="row">Top</th><td><%= topReport %></td></tr>
<tr><th scope="row">Connection Pool</th><td><%= request.getAttribute("cpDiagnostics") %></td></tr>
<tr><th scope="row">Medium Connection Pool</th><td><%= request.getAttribute("cpMediumDiagnostics") %></td></tr>
<tr><th scope="row">Long Connection Pool</th><td><%= request.getAttribute("cpLongDiagnostics") %></td></tr>
<tr><th scope="row">DB Status</th><td><a href ="<%= domainApp %>/dbStatus.do">Page</a></td></tr>
</table>
</div>

<div class="aw-card" id="server-stats">
<h2>Server Stats</h2>
<table class="aw-kv">
<tr><th scope="row">AntwebMgr Report</th><td><%= AntwebMgr.getHtmlReport() %></td></tr>
<tr><th scope="row">TaxonMgr</th><td><%= TaxonMgr.report() %></td></tr>
<tr><th scope="row">Profile</th><td><%= QueryProfiler.report() %></td></tr>
<% /*
<br><br><b>UserAgentTracker</b>
  <br><b>&nbsp;&nbsp;&nbsp;botCount:</b>< %= UserAgentTracker.getBotDenialCount() % >
  <br><b>&nbsp;&nbsp;&nbsp;getBotDenialReason:</b>< %= UserAgentTracker.getBotDenialReason() % >
  <br><b>&nbsp;&nbsp;&nbsp;inVetMode:</b>< %= UserAgentTracker.isInVetMode() % >

<br><br><b>User Agents:</b> <a href='< %= AntwebProps.getDomainApp() % >/userAgents.do'>< %= UserAgentTracker.htmlSummary() % ></a>
<br><br><b>Bad Actor Report:</b> < %= BadActorMgr.getBadActorReport() % >
*/ %>
<tr><th scope="row">ProfileCounter</th><td><%= ProfileCounter.getReport() %></td></tr>
</table>
</div>

<div class="aw-card" id="server-properties">
<h2>Server Properties</h2>
<%
String serverDetails = (String) request.getAttribute("serverDetails");
%>
<table class="aw-kv">
<tr><th scope="row">DomainApp</th><td><%= domainApp %></td></tr>
<tr><th scope="row">SecureDomainApp</th><td><%= AntwebProps.getSecureDomainApp() %></td></tr>
<tr><th scope="row">ImgDomainApp</th><td><%= AntwebProps.getImgDomainApp() %></td></tr>
<tr><th scope="row">Antweb Props</th><td><%= AntwebProps.htmlReport() %></td></tr>
<tr><th scope="row">Your Local Address</th><td><%= request.getLocalAddr() %></td></tr>
<tr><th scope="row">Your Remote Address</th><td><%= request.getRemoteAddr() %></td></tr>
<tr><th scope="row">Your RemoteHost</th><td><%= request.getRemoteHost() %></td></tr>
<tr><th scope="row">User Agent</th><td><%= Html.esc(request.getHeader("User-Agent")) %></td></tr>
<tr><th scope="row">File Encoding</th><td><%= System.getProperty("file.encoding") %> : <%=java.nio.charset.Charset.defaultCharset() %></td></tr>
<tr><th scope="row">Legacy Merge Sort</th><td><%= System.getProperty("java.util.Arrays.useLegacyMergeSort") %></td></tr>
<tr><th scope="row">trimSpaces</th><td><%= getServletConfig().getInitParameter("trimSpaces") %></td></tr>
<tr><th scope="row">isLocal</th><td><%= AntwebProps.isLocal() %></td></tr>
<tr><th scope="row">isDevMode</th><td><%= AntwebProps.isDevMode() %></td></tr>
<tr><th scope="row">isStageMode</th><td><%= AntwebProps.isStageMode() %></td></tr>
<tr><th scope="row">isDevOrStageMode</th><td><%= AntwebProps.isDevOrStageMode() %></td></tr>
<tr><th scope="row">isLiveMode</th><td><%= AntwebProps.isLiveMode() %></td></tr>
<tr><th scope="row">Encodings</th><td><%= HttpUtil.showEncodings(request, response) %></td></tr>
<tr><th scope="row">Server Details</th><td><%= serverDetails %></td></tr>
</table>
</div>

<div class="aw-card" id="server-links">
<h2>Assorted Links</h2>
<ul class="aw-links">
     <li><a href = "<%= domainApp %>/dbStatus.do">Database Status</a> (connection pools; reset a pool)</li>
     <li><a href = "<%= domainApp %>/util.do?action=isRestart">Restart Status</a> (true when the application has asked for a restart; read by the host's restart checker)</li>
     <li><a href = "<%= domainApp %>/uptime.do">Uptime</a></li>
     <li><a href = "<%= domainApp %>/sessionExpired.jsp">sessionExpired.jsp</a></li>
     <li><a href = "<%= domainApp %>/error.jsp">error.jsp</a></li>
</ul>
</div>

<div class="aw-card" id="server-page-tracker">
<h2>Page Tracker</h2>
<pre>
<%= org.calacademy.antweb.util.Html.esc(PageTracker.showRequests()) %>
</pre>
</div>
</div>

<%
  AntwebUtil.log(message);
%>
