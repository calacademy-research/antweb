<%-- Unused as of 2026-11-02: fetches and prints /anomalous.jsp, which does not exist. No action forwards here,
     nothing links it, and Caddy answers a direct request with 404 (not on the JSP allow list in
     docker/caddy/Caddyfile). --%><%@ page import="org.calacademy.antweb.util.*" %>

<!-- uptime.jsp -->

<%
if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
    
String retVal = HttpUtil.getUrl(AntwebProps.getDomainApp() + "/anomalous.jsp");
%>

<%= retVal %>


