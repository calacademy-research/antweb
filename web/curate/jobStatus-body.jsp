<%@ page errorPage = "/error.jsp" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%-- Built by org.calacademy.antweb.util.JobStatusAction (texts escaped there). --%>
<%
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
    String html = (String) request.getAttribute("html");
%>
<div class=left>
<%= html == null ? "" : html %>
</div>
