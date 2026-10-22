<%@ page errorPage = "/error.jsp" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%-- Built by org.calacademy.antweb.util.CountCheckAction (values escaped there). --%>
<%
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
    String domainApp = AntwebProps.getDomainApp();
    String html = (String) request.getAttribute("html");
%>
<%-- Tool-page look (common/tools.css, docs/guides/page-style.md). --%>
<%@include file="/common/toolsHead.jsp" %>
<div class="aw-tools" id="count-check-page">
<p class="aw-crumbs"><a href="<%= domainApp %>">Home</a> | <a href="<%= domainApp %>/curate.do">Curator Tools</a></p>
<h1>Check the counts</h1>
<div class="aw-intro"><p>Are the numbers on country, project, museum and bioregion pages right? A fresh count from the specimen records, compared with what the site shows.</p></div>
<div class="aw-card" id="count-check-report">
<%= html == null ? "" : html %>
</div>
</div>
