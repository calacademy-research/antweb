
<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.upload.Upload" %>

<%@ page import="java.util.*" %>

<% 
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
%>

<%-- Tool-page look (common/tools.css, docs/guides/page-style.md). Tests read the title (first h2), the
     "<a href = ...>" links and the rows (split at "<tr>"): tests/e2e/test_upload_list_views.py,
     test_upload_report_links.py. --%>
<%@include file="/common/toolsHead.jsp" %>
<div class="aw-tools" id="specimen-uploads-page">
<%
    String domainApp = AntwebProps.getDomainApp();

    String message = (String) request.getAttribute("message"); 

    // Which view this is (ListUploadsAction): every group's uploads, one group's, or one login's.
    String uploadsView = (String) request.getAttribute("uploadsView");
    boolean isAllGroups = uploadsView == null || org.calacademy.antweb.util.ListUploadsAction.UPLOADS_VIEW_ALL.equals(uploadsView);
    org.calacademy.antweb.Login viewer = LoginMgr.getAccessLogin(request);
    org.calacademy.antweb.Group ownGroup = viewer == null ? null : viewer.getGroup();
%>
<p class="aw-crumbs"><a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a>
<% if (LoginMgr.isAdmin(request)) { %> | <a href = "<%= domainApp %>/query.do?action=curiousQuery&name=lastSpecimenUpload">Each group's last upload</a><% } %></p>
<% if (message != null) out.println("<h2 class=\"aw-title\">" + message + "</h2>"); %>
<div class="aw-intro"><p>One line per specimen upload, newest first: when it was made, its upload report (what was added, changed
or refused, line by line) and, where you may read it, the file that was uploaded.</p></div>
<%
    if (isAllGroups) {
        out.println("<p class=\"uploads-view\"><b>" + org.calacademy.antweb.util.ListUploadsAction.ALL_GROUPS_LINE + "</b>");
        if (ownGroup != null) {
            out.println(" <a class=\"uploads-own-group\" href=\"" + domainApp + "/listSpecimenUploads.do?groupId=" + ownGroup.getId() + "\">"
                + org.calacademy.antweb.util.ListUploadsAction.ownGroupLinkText(ownGroup) + "</a>");
        }
        out.println("</p>");
    } else if (LoginMgr.isAdmin(request)) {
        out.println("<p class=\"uploads-view\"><a class=\"uploads-all-groups\" href=\"" + domainApp + "/listSpecimenUploads.do\">"
            + org.calacademy.antweb.util.ListUploadsAction.VIEW_ALL_LINK + "</a></p>");
    }

    ArrayList<Upload> list = (ArrayList) request.getAttribute("uploads"); 
    int count = 0;
    out.println("<div class=\"aw-card\" id=\"specimen-uploads-list\">");
    out.println("<table class=\"aw-uploads\"><tr><th>#</th>");
    if (isAllGroups) out.println("<th>Group</th>");
    out.println("<th>Created</th><th>Log File</th><th>Uploaded file</th></tr>");
    // The uploaded data file (uploadFile.do): admins any group's, curators their own group's.
    org.calacademy.antweb.Login listLogin = LoginMgr.getAccessLogin(request);
    for (Upload upload : list) {
      ++count;
      out.println("<tr><td>" + count + ".</td>");
      if (isAllGroups) {
        // A group that is gone (deleted) shows its number: until 2026-10-27 it stopped the whole list.
        org.calacademy.antweb.Group uploadGroup = GroupMgr.getGroup(upload.getGroupId());
        String groupLabel = uploadGroup == null ? "group " + upload.getGroupId() : uploadGroup.getAbbrev();
        out.println("<td><a href=\"" + domainApp + "/listUploads.do?groupId=" + upload.getGroupId() + "\">" + Html.esc(groupLabel) + "</a></td>");
      }
      // Link the report only while it is on the server (most reports from before Nov 2019 are gone).
      if (upload.hasReport()) {
        out.println("<td>" + upload.getCreated() + "</td><td><a href=\"" + Html.escAttr(domainApp + "/web/log/" + upload.getUploadDir() + "/" + Html.url(upload.getLogFileName())) + "\">" + Html.esc(upload.getLogFileName()) + "</a></td>");
      } else {
        out.println("<td>" + upload.getCreated() + "</td><td>" + Html.esc(upload.getLogFileName()) + " (report no longer available)</td>");
      }
      String fileName = upload.getUploadedFileName();
      if (fileName == null || !org.calacademy.antweb.upload.UploadFileAction.mayRead(listLogin, fileName)) {
        out.println("<td></td>");
      } else if (upload.hasUploadedFile()) {
        out.println("<td><a class=\"uploaded-file\" href=\"" + Html.escAttr(domainApp + "/uploadFile.do?file=" + Html.url(fileName)) + "\">Download</a></td>");
      } else {
        out.println("<td>(no longer on the server)</td>");
      }
      out.println("</tr>");
    }
    out.println("</table>");
    if (count == 0) out.println("<p>No uploads for this group</p>");
    out.println("</div>");
%>

</div> 
