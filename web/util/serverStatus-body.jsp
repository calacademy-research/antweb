	
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
  String message = "serverStatus-body.jsp message:";
  Utility util = new Utility();
  String domainApp = util.getDomainApp();
%>

<div class="admin_left">

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>
<br>
<h1><u>Antweb Server Status</u></h1>

<% // Object o = null;  o.toString(); // uncomment this line to create stacktrace %>

<br>
<h3>Antweb Operation</h3>

<br><b>Current Time:</b> <%= (new Date()).toString() %>
<br><b>Server Start Time:</b> <%= (SessionRequestFilter.getInitTime()).toString() %>
<br><b>Run Time in hrs: </b><%= AntwebUtil.hrsSince(SessionRequestFilter.getInitTime()) %>. <b>In mins: </b><%= AntwebUtil.minsSince(SessionRequestFilter.getInitTime()) %>.
<br><b>AntwebMgr (re)Start Time:</b> <%= AntwebMgr.getStartTime() %>. <b>(Hrs:</b> <%= AntwebUtil.hrsSince(AntwebMgr.getStartTime()) %><b>).</b>
<br><br><b>Upload services</b> - To take On/Off line:<a href="<%= domainApp %>/serverStatus.do?action=toggleDownTime">Toggle Down Time</a>
<%
boolean otherOption = !UptimeAction.isFailOnPurpose();
%>
<br><b>Uptime Fail On Purpose:</b> <%= UptimeAction.isFailOnPurpose() %>  <a href='<%= AntwebProps.getDomainApp() %>/uptime.do?fail=<%= otherOption %>'>[toggle fail]</a>
<br><%= ServerStatusAction.getDownTimeMessage() %>
<br><b>Status</b>
&nbsp;&nbsp;&nbsp;(If Upload in process, or Image Upload locked, best to wait to restart the server).
<% 
String uploadValue = "";
if (UploadAction.isInUploadProcess()) {
  uploadValue = "<font color=\"red\">true</font>";
} else {
  uploadValue = "<font color=\"green\">false</font>";
}
%>
<br>&nbsp;&nbsp;&nbsp;Is Upload in process: <%= uploadValue %>
<% 
String computeValue = "";
if (UtilDataAction.isInComputeProcess()) {
  computeValue = "<font color=\"red\">true</font>";
} else {
  computeValue = "<font color=\"green\">false</font>";
}
%>
<br>&nbsp;&nbsp;&nbsp;Is Compute in process: <%= computeValue %>

<%
OperationLock operationLock = (OperationLock) request.getAttribute("operationLock");
String imageUploadLock = "";

if ((operationLock != null) && (operationLock.isLocked()) && (!operationLock.isExpired())) {
  imageUploadLock = "<font color=\"red\">true</font>";
} else {
  imageUploadLock = "<font color=\"green\">false</font>";
}
%>
<br>&nbsp;&nbsp;&nbsp;Is Image Upload locked: <%= imageUploadLock %>

<br><br><b>CPU:</b> <%= AntwebSystem.getCpuLoad() %>
<br>

<%
String topReport = AntwebSystem.getTopReport();
message += topReport;
%>

<%= topReport %>

<%= FileUtil.getDiskStats() %>
<br>
<%
  String memoryStat = AntwebUtil.getMemoryStats();
  AntwebUtil.log("info", domainApp + "/serverStatus.jsp.  memory:" + memoryStat);
%>
<b>Memory Stats</b> - <%= memoryStat %>

<br><b>isServerBusy:</b> <%= request.getAttribute("isServerBusy") %>
<br><b>Connection Pool:</b> <%= request.getAttribute("cpDiagnostics") %>

<br><br><br>
<h3>Server Stats</h3>

<br><b>AntwebMgr Report:</b> <%= AntwebMgr.getHtmlReport() %>
<br><b>Profile:</b><%= QueryProfiler.report() %>

<br><b>User Agents:</b> <a href='<%= AntwebProps.getDomainApp() %>/userAgents.do'><%= UserAgentTracker.htmlSummary() %></a>
<br><b>Bad Actor Report:</b> <%= BadActorMgr.getBadActorReport() %>
<br><b>ProfileCounter:</b> <%= ProfileCounter.getReport() %>

<br><br><br>
<h3>Server Properties </h3>

<b>DomainApp</b>: <%= domainApp %>
<br><b>SecureDomainApp</b>: <%= AntwebProps.getSecureDomainApp() %>
<br><b>ImgDomainApp:</b> <%= AntwebProps.getImgDomainApp() %>
<br><b>Antweb Props:</b><%= AntwebProps.htmlReport() %>
<br><b>Your Local Address:</b> <%= request.getLocalAddr() %>
<br><b>Your Remote Address:</b> <%= request.getRemoteAddr() %>
<br><b>Your RemoteHost:</b> <%= request.getRemoteHost() %>
<br><b>User Agent:</b> <%= request.getHeader("User-Agent") %>
<br><b>File Encoding:</b> <%= System.getProperty("file.encoding") %> : <%=java.nio.charset.Charset.defaultCharset() %>
<br><b>Legacy Merge Sort:</b> <%= System.getProperty("java.util.Arrays.useLegacyMergeSort") %>
<br><b>trimSpaces:</b> <%= getServletConfig().getInitParameter("trimSpaces") %>
<br><b>isLocal:</b> <%= AntwebProps.isLocal() %>
<br><b>isDevMode:</b> <%= AntwebProps.isDevMode() %>
<br><b>isStageMode:</b> <%= AntwebProps.isStageMode() %>
<br><b>isDevOrStageMode:</b> <%= AntwebProps.isDevOrStageMode() %>
<br><b>isLiveMode:</b> <%= AntwebProps.isLiveMode() %>
<br><b>Encodings:</b><%= HttpUtil.showEncodings(request, response) %>
<%
String serverDetails = (String) request.getAttribute("serverDetails");
%>
<br><b>Server Details:</b> <%= serverDetails %>
<br><b>TaxonMgr</b><%= TaxonMgr.report() %>

 %>  
<br><br><br>
<h3>Assorted Links</h3>
     <a href = "<%= domainApp %>/uptime.do">Uptime</a>
     <br><a href = "<%= domainApp %>/sessionExpired.jsp">sessionExpired.jsp</a>
     <br><a href = "<%= domainApp %>/error.jsp">error.jsp</a>

<br><br><br>
<h3>Form Actions</h3>
<html:form action="<%= domainApp %>/serverStatus">
       <input type="submit" name="methodHabitat" value="methodHabitat"/>
<br>
       <input type="submit" name="name" value="holdOpenConnection"/>
       
       <!-- input type="submit" name="ugServerRestart" value="ugServerRestart" / -->

       <br><br>
</html:form>

<br>
<h3>Page Tracker</h3>
<pre>
<%= PageTracker.showRequests() %>
</pre>


<hr>
</div>

<%
  AntwebUtil.log(message);
%>
