<%@ page errorPage = "/error.jsp" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%-- Built by org.calacademy.antweb.util.CountCheckAction (values escaped there). --%>
<%
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
    String domainApp = AntwebProps.getDomainApp();
    String html = (String) request.getAttribute("html");
%>
<div class=left>
<h1>Check the counts</h1>
<p>Are the numbers on country, project, museum and bioregion pages right? A fresh count from the specimen records, compared with what the site shows.</p>
<a href="<%= domainApp %>">Home</a> | <a href="<%= domainApp %>/curate.do">Curator Tools</a>
<%= html == null ? "" : html %>
</div>
