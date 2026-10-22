<%@ page errorPage = "/error.jsp" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%-- Built by org.calacademy.antweb.util.LogMgrAction (the list of logs, or one log or upload line, escaped there).
     Tool-page look: common/tools.css, docs/guides/page-style.md. --%>
<%
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
    String domainApp = AntwebProps.getDomainApp();
    String message = (String) request.getAttribute("message");
%>
<%@include file="/common/toolsHead.jsp" %>
<div class="aw-tools" id="log-manager-page">
<p class="aw-crumbs"><a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a> | <a href="<%= domainApp %>/logMgr.do?action=list">Log Manager</a></p>
<h1>Log Manager</h1>
<div class="aw-card" id="log-manager-content">
<%= message == null ? "" : message %>
</div>
</div>
