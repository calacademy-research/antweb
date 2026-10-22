
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

<div class=left>
<%
    String domainApp = AntwebProps.getDomainApp();

    String message = (String) request.getAttribute("message"); 
    if (message != null) out.println("<h2>&nbsp;" + message + "</h2>");

    // Which view this is (ListUploadsAction): every group's uploads, one group's, or one login's.
    String uploadsView = (String) request.getAttribute("uploadsView");
    boolean isAllGroups = uploadsView == null || org.calacademy.antweb.util.ListUploadsAction.UPLOADS_VIEW_ALL.equals(uploadsView);
    org.calacademy.antweb.Login viewer = LoginMgr.getAccessLogin(request);
    org.calacademy.antweb.Group ownGroup = viewer == null ? null : viewer.getGroup();
%>
&nbsp;&nbsp;&nbsp;<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a>
<% if (LoginMgr.isAdmin(request)) { %> | <a href = "<%= domainApp %>/query.do?action=curiousQuery&name=lastSpecimenUpload">Each group's last upload</a><% } %>
<br><br>
<%
    if (isAllGroups) {
        out.println("<p class=\"uploads-view\">&nbsp;&nbsp;&nbsp;<b>" + org.calacademy.antweb.util.ListUploadsAction.ALL_GROUPS_LINE + "</b>");
        if (ownGroup != null) {
            out.println(" <a class=\"uploads-own-group\" href=\"" + domainApp + "/listSpecimenUploads.do?groupId=" + ownGroup.getId() + "\">"
                + org.calacademy.antweb.util.ListUploadsAction.ownGroupLinkText(ownGroup) + "</a>");
        }
        out.println("</p>");
    } else if (LoginMgr.isAdmin(request)) {
        out.println("<p class=\"uploads-view\">&nbsp;&nbsp;&nbsp;<a class=\"uploads-all-groups\" href=\"" + domainApp + "/listSpecimenUploads.do\">"
            + org.calacademy.antweb.util.ListUploadsAction.VIEW_ALL_LINK + "</a></p>");
    }
    out.println("<br>");

    ArrayList<Upload> list = (ArrayList) request.getAttribute("uploads"); 
    int count = 0;
    out.println("<table cellpadding=10><tr><th>#</th>");
    if (isAllGroups) out.println("<th>Group</th>");
    out.println("<th width='100'>Created</th><th>Log File</th><th>Uploaded file</th></tr>");
    // The uploaded data file (uploadFile.do): admins any group's, curators their own group's.
    org.calacademy.antweb.Login listLogin = LoginMgr.getAccessLogin(request);
    for (Upload upload : list) {
      ++count;
      out.println("<tr><td>" + count + ".</td>");
      if (isAllGroups) out.println("<td><a href=\"" + domainApp + "/listUploads.do?groupId=" + upload.getGroupId() + "\">" + GroupMgr.getGroup(upload.getGroupId()).getAbbrev() + "</a></td>");
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
    if (count == 0) out.println("<br> No uploads for this group");
%>

</div > 
