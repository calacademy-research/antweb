<%@ page import="org.calacademy.antweb.util.*" %>

<!-- uptime.jsp -->

<%
if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
    
String retVal = HttpUtil.getUrl(AntwebProps.getDomainApp() + "/anomalous.jsp");
%>

<%= retVal %>


