
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
    
    // Sloppy. If there is a curator or a group there will be a colon in the message (title).
    boolean isAllGroups = !message.contains(":");
     
%>
&nbsp;&nbsp;&nbsp;<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>
<%
    ArrayList<Upload> list = (ArrayList) request.getAttribute("uploads"); 
    int count = 0;
    out.println("<table cellpadding=10><tr><th>#</th>");
    if (isAllGroups) out.println("<th>Group</th>");
    out.println("<th width='100'>Created</th><th>Log File</th></tr>");
    for (Upload upload : list) {
      ++count;
      out.println("<tr><td>" + count + ".</td>");
      if (isAllGroups) out.println("<td><a href=\"" + domainApp + "/listUploads.do?groupId=" + upload.getGroupId() + "\">" + GroupMgr.getGroup(upload.getGroupId()).getAbbrev() + "</a></td>");
      out.println("<td>" + upload.getCreated() + "</td><td><a href=\"" + domainApp + "/web/log/" + upload.getUploadDir() + "/" + upload.getLogFileName() + "\">" + upload.getLogFileName() + "</a></td>");
    }
    out.println("</tr></table>");
    if (count == 0) out.println("<br> No uploads for this group");

%>

</div > 
