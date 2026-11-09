<%@ page errorPage = "/error.jsp" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%-- Body of apiKeys.do: built by org.calacademy.antweb.curate.ApiKeysAction (every value escaped there), in the
     tool-page look (common/tools.css, docs/guides/page-style.md). --%>
<%
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
    String domainApp = AntwebProps.getDomainApp();
    String html = (String) request.getAttribute("html");
%>
<%@include file="/common/toolsHead.jsp" %>
<div class="aw-tools" id="api-keys-page">
<p class="aw-crumbs"><a href="<%= domainApp %>">Home</a> | <a href="<%= domainApp %>/curate.do">Curator Tools</a> | <a href="<%= domainApp %>/apiV3.1.do">API documentation</a></p>
<h1>API keys</h1>
<%= html == null ? "" : html %>
</div>
