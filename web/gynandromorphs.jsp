<%@ page import="org.calacademy.antweb.util.*" %>

<!-- uptime.jsp -->

<%
String retVal = AntwebUtil.getUrl(AntwebProps.getDomainApp() + "/anomalous.jsp");
%>

<%= retVal %>


