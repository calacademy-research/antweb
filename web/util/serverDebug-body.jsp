	
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
  //String message = "serverStatus-body.jsp message:";
  Utility util = new Utility();
  String domainApp = util.getDomainApp();
%>

<div class="admin_left">

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>

<br>
<h1><u>Server Debug</u></h1>

<br>
<br>This feature allows Antweb developers to set/unset the value of a runtime variable. Useful for debugging where
we want to turn the debugging on/off without having to recompile or restart the server. One debug can be set at a time
and will persist across restarts.
<br>
<br> The available debug options are defined in ServerDebug.java.
<br>

<br><b>Server Debug:</b> <%= ServerDebug.getDebug() %>
<br>To <a href="<%= domainApp %>/serverDebug.do?action=setDebug&param=">unset</a>
<br><br><b>ServerDebugs:</b>
<%
    String[] debugs = ServerDebug.getDebugs();
    if (debugs != null) {
      for (String debug : debugs) {
        out.println("<a href=" + domainApp + "/serverDebug.do?action=setDebug&param=" + debug + ">" + debug + "</a>");
      }
    }
%>

</div>