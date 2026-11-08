<%@ page errorPage = "/error.jsp" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%-- Body of the Daily update page, built by org.calacademy.antweb.util.DailyUpdateAction (values escaped there):
     the steps of today's (or the last) daily update and the earlier ones. --%>
<%
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
    String domainApp = AntwebProps.getDomainApp();
    String html = (String) request.getAttribute("html");
%>
<%-- Tool-page look (common/tools.css, docs/guides/page-style.md). --%>
<%@include file="/common/toolsHead.jsp" %>
<div class="aw-tools" id="daily-update-page">
<p class="aw-crumbs"><a href="<%= domainApp %>">Home</a> | <a href="<%= domainApp %>/curate.do">Curator Tools</a> | <a href="<%= domainApp %>/dailyUpdate.do?format=json">JSON</a></p>
<h1>Daily update</h1>
<%= html == null ? "" : html %>
</div>
