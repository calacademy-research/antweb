<%@ page errorPage = "/error.jsp" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%-- Built by org.calacademy.antweb.curate.NameUpdatesAction (names and codes escaped there). --%>
<%
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
    String domainApp = AntwebProps.getDomainApp();
    String html = (String) request.getAttribute("html");
%>
<%-- Tool-page look (common/tools.css, docs/guides/page-style.md). The explanation at the top is the
     report-help box (NameUpdatesAction.HELP). --%>
<%@include file="/common/toolsHead.jsp" %>
<div class="aw-tools" id="name-updates-page">
<p class="aw-crumbs"><a href="<%= domainApp %>">Home</a> | <a href="<%= domainApp %>/curate.do">Curator Tools</a></p>
<h1>Name update report</h1>
<%= html == null ? "" : html %>
</div>
