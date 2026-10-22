<%@ page errorPage = "/error.jsp" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%-- Built by org.calacademy.antweb.util.JobStatusAction (texts escaped there). --%>
<%
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
    String html = (String) request.getAttribute("html");
%>
<%-- Tool-page look (common/tools.css, docs/guides/page-style.md). The page still reloads itself every
     5 seconds while a job is waiting or running (HTTP Refresh header, JobStatusAction). --%>
<%@include file="/common/toolsHead.jsp" %>
<div class="aw-tools" id="job-status-page">
<%= html == null ? "" : html %>
</div>
