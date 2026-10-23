<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.imageUploader.*" %>

<%@ page import="java.util.*" %>

<% 
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
%>

<%-- Tool-page look (common/tools.css, docs/guides/page-style.md). tests/e2e/test_image_upload.py reads the
     rows (split at "<tr>", "<td class="aw-num">count</td> <td class="aw-nowrap"><a href='...imageUploadReport.do?id=N'") and the
     summary line ("N image upload batches since DATE, holding M images", id image-uploads-total; Brian Fisher 2026-10-23:
     a total says what it counts and since when). --%>
<%@include file="/common/toolsHead.jsp" %>
<div class="aw-tools" id="image-uploads-page">

<%
    String domainApp = AntwebProps.getDomainApp();

    String message = (String) request.getAttribute("message"); 

    // Sloppy. If there is a curator or a group there will be a colon in the message (title).
    boolean isAllGroups = !message.contains(":");
    
    String uploadsBy = "all";
    if (message.contains("curator.do")) uploadsBy = "curator";
    if (message.contains("group.do")) uploadsBy = "group";
    //AntwebUtil.log("listImageUploads-body.jsp uploadsBy:" + uploadsBy);
%>
<%
    // The way back from the list of all groups to one's own group's list (Brian Fisher, 2026-10-23).
    Group accessGroup = GroupMgr.getAccessGroup(request);
%>
<p class="aw-crumbs"><a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><% if (!"all".equals(uploadsBy)) { %> | <a id="all-image-uploads" href = "<%= domainApp %>/listImageUploads.do">All groups' image uploads</a><% } else if (accessGroup != null) { %> | <a id="own-group-image-uploads" href = "<%= domainApp %>/listImageUploads.do?groupId=<%= accessGroup.getId() %>"><%= Html.esc(accessGroup.getAbbrev()) %> image uploads</a><% } %></p>
<% if (message != null) out.println("<h2 class=\"aw-title\">" + message + "</h2>"); %>
<div class="aw-intro"><p>One line per batch of uploaded images, newest first: who uploaded it, the photographer, whether it
finished and how many images it holds. The date opens the batch's report (images saved, and any not saved with the reason).</p></div>
<%
    ArrayList<ImageUpload> list = (ArrayList) request.getAttribute("imageUploads");

    out.println("<div class=\"aw-card\" id=\"image-uploads-list\">");
    // What the total counts, and since when: batches (not images), from the oldest batch listed; and the images they hold.
    java.util.Date oldest = null;
    long images = 0;
    for (ImageUpload u : list) {
      if (u.getCreated() != null && (oldest == null || u.getCreated().before(oldest))) oldest = u.getCreated();
      images += u.getImageCount();
    }
    java.text.SimpleDateFormat day = new java.text.SimpleDateFormat("yyyy-MM-dd");
    java.text.SimpleDateFormat minute = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
    out.println("<p id=\"image-uploads-total\"><b>" + Formatter.commaFormat(list.size()) + " image upload batches"
      + (oldest != null ? " since " + day.format(oldest) : "") + ", holding " + Formatter.commaFormat(images) + " images</b>"
      + " (the oldest batch listed; Image Count added up).</p>");
    
    int i = list.size() + 1;
    %>
    <table class="aw-uploads"><tr><th>#</th>
    <%
    if ("all".equals(uploadsBy)) { %>
    <th>Group</th>
    <th>Curator</th>
 <% } 
    if ("group".equals(uploadsBy)) { %>
    <th>Curator</th>
 <% } %>
    <th>Artist</th>
    <th>Complete</th>
    <th>Image Count</th>
    <th>Created</th>
    </tr>
    <%
   // ImageUpload t = null;
    int c = 0;
    for (ImageUpload imageUpload : list) {
     // t = imageUpload;
      --i;
      ++c;
      out.println("<tr><td>" + c + ".</td>");
      
      // A login that is not (or no longer) a listed curator, a removed group or artist: show the id.
      if ("all".equals(uploadsBy)) {
        Group group = GroupMgr.getGroup(imageUpload.getGroupId());
        out.println("<td>" + (group != null ? group.getLinkAbbrev() : "id " + imageUpload.getGroupId()) + "</td>");
        Curator curator = LoginMgr.getCurator(imageUpload.getCuratorId());
        out.println("<td>" + (curator != null ? curator.getLink() : "id " + imageUpload.getCuratorId()) + "</td>");
      }
      if ("group".equals(uploadsBy)) {
        Curator curator = LoginMgr.getCurator(imageUpload.getCuratorId());
        out.println("<td>" + (curator != null ? curator.getLink() : "id " + imageUpload.getCuratorId()) + "</td>");
      }
      Artist artist = ArtistMgr.getArtist(imageUpload.getArtistId());
      out.println("<td>" + (artist != null ? artist.getLink() : "id " + imageUpload.getArtistId()) + "</td>");
      out.println("<td>" + imageUpload.getIsComplete() + "</td>");
      out.println("<td class=\"aw-num\">" + imageUpload.getImageCount() + "</td>");
      out.println("<td class=\"aw-nowrap\"><a href='" + domainApp + "/imageUploadReport.do?id=" + imageUpload.getId() + "'>"
        + (imageUpload.getCreated() != null ? minute.format(imageUpload.getCreated()) : "") + "</a></td>");
    }
    out.println("</tr></table>");

    if (i == 0 && message.contains("curator.do")) out.println("<p>No uploads for this curator</p>");
    if (i == 0 && message.contains("group.do")) out.println("<p>No uploads for this group</p>");
    out.println("</div>");
%>

</div> 

