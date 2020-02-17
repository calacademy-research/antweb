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

<div id="page_contents">

<div class=left>

<%
    String domainApp = AntwebProps.getDomainApp();

    String message = (String) request.getAttribute("message"); 
    if (message != null) out.println("<h2>&nbsp;" + message + "</h2>");

    // Sloppy. If there is a curator or a group there will be a colon in the message (title).
    boolean isAllGroups = !message.contains(":");
    
    String uploadsBy = "all";
    if (message.contains("curator.do")) uploadsBy = "curator";
    if (message.contains("group.do")) uploadsBy = "group";
    //AntwebUtil.log("listImageUploads-body.jsp uploadsBy:" + uploadsBy);
%>
&nbsp;&nbsp;&nbsp;<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>
<%
    ArrayList<ImageUpload> list = (ArrayList) request.getAttribute("imageUploads");

    out.println("<br><h3>&nbsp;&nbsp;&nbsp;Total image uploads: " + list.size() + "</h3><br><br>");
    
    int i = list.size() + 1;
    %>
    <table cellpadding=10><tr><th>#</th>
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
    <th width='140'>Created</th>
    </tr>
    <%
   // ImageUpload t = null;
    int c = 0;
    for (ImageUpload imageUpload : list) {
     // t = imageUpload;
      --i;
      ++c;
      out.println("<tr><td>" + c + ".</td>");
      
      if ("all".equals(uploadsBy)) {
        Group group = GroupMgr.getGroup(imageUpload.getGroupId());
        out.println("<td>" + group.getLinkAbbrev() + "</td>");
        Curator curator = LoginMgr.getCurator(imageUpload.getCuratorId());
        out.println("<td>" + curator.getLink() + "</td>");
      }
      if ("group".equals(uploadsBy)) {
        Curator curator = LoginMgr.getCurator(imageUpload.getCuratorId());
        out.println("<td>" + curator.getLink() + "</td>");
      }
      Artist artist = ArtistMgr.getArtist(imageUpload.getArtistId());
      out.println("<td>" + artist.getLink() + "</td>");
      out.println("<td>" + imageUpload.getIsComplete() + "</td>");
      out.println("<td>" + imageUpload.getImageCount() + "</td>");
      out.println("<td><a href='" + domainApp + "/imageUploadReport.do?id=" + imageUpload.getId() + "'>" + imageUpload.getCreated() + "</a></td>");
    }
    out.println("</tr></table>");

    if (i == 0 && message.contains("curator.do")) out.println("<br> No uploads for this curator");
    if (i == 0 && message.contains("group.do")) out.println("<br> No uploads for this group");

%>

</div > 

</div > 

