<%@ page errorPage = "/error.jsp" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%-- Built by org.calacademy.antweb.curate.GenusConceptRulesAction (names escaped there). --%>
<%
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
    String domainApp = AntwebProps.getDomainApp();
    String html = (String) request.getAttribute("html");
%>
<%-- Tool-page look (common/tools.css, docs/guides/page-style.md). --%>
<%@include file="/common/toolsHead.jsp" %>
<div class="aw-tools" id="genus-concept-rules-page">
<p class="aw-crumbs"><a href="<%= domainApp %>">Home</a> | <a href="<%= domainApp %>/curate.do">Curator Tools</a> | <a href="<%= domainApp %>/nameUpdates.do">Name update report</a></p>
<h1>Old genus concepts</h1>
<%= html == null ? "" : html %>
</div>
