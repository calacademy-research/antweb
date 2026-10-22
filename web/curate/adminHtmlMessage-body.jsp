<%@ page errorPage = "/error.jsp" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%-- Body of curate/adminHtmlMessage.jsp for a logged-in viewer: the result HTML (built and escaped in Java,
     e.g. QueryManager.runNamedQueryHtml) in one card. Tool-page look: common/tools.css, docs/guides/page-style.md.
     Named query results begin with their own title (h1 "Query: name") and say what they show. --%>
<%
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
    String domainApp = AntwebProps.getDomainApp();
    String message = (String) request.getAttribute("message");
%>
<%@include file="/common/toolsHead.jsp" %>
<div class="aw-tools" id="admin-result-page">
<p class="aw-crumbs"><a href="<%= domainApp %>">Home</a> | <a href="<%= domainApp %>/curate.do">Curator Tools</a><% if (LoginMgr.isAdmin(request)) { %> | <a href="<%= domainApp %>/query.do">Query Manager</a><% } %></p>
<div class="aw-card aw-result" id="admin-result">
<%= message == null ? "" : message %>
</div>
</div>
