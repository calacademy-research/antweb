<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.curate.CuratorFilesAction" %>

<!-- curatorFiles-body.jsp (CuratorFilesAction) -->
<%
    String domainApp = AntwebProps.getDomainApp();
    String path = (String) request.getAttribute("curatorFilesPath");
    List<CuratorFilesAction.Entry> entries = (List<CuratorFilesAction.Entry>) request.getAttribute("curatorFiles");
    if (path == null || entries == null) return;
    boolean truncated = Boolean.TRUE.equals(request.getAttribute("curatorFilesTruncated"));
    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
%>
<div class="admin_left">
<h1>Your Curator Files</h1>
<a href="<%= domainApp %>">Home</a> | <a href="<%= domainApp %>/curate.do">Curator Tools</a>
<br><br>
<p>Folder: <b><%= Html.esc(path) %></b>. To link a file from a page, use the address in its link.</p>

<% if (entries.isEmpty()) { %>
<p>There are no files in your folder.</p>
<% } else { %>
<table cellpadding="4">
<tr><th align="left">Name</th><th align="right">Size</th><th align="left">Modified</th></tr>
<% for (CuratorFilesAction.Entry e : entries) {
     String date = fmt.format(new Date(e.modified()));
     if (e.isDir()) { %>
<tr><td><%= Html.esc(e.name()) %>/ (folder)</td><td></td><td><%= date %></td></tr>
<%   } else {
       String href = domainApp + path + URLEncoder.encode(e.name(), StandardCharsets.UTF_8).replace("+", "%20"); %>
<tr><td><a href="<%= Html.escAttr(href) %>"><%= Html.esc(e.name()) %></a></td><td align="right"><%= String.format("%,d", e.size()) %></td><td><%= date %></td></tr>
<%   }
   } %>
</table>
<% if (truncated) { %><p>Only the first <%= entries.size() %> entries are shown.</p><% } %>
<% } %>
</div>
