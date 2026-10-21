<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>

<%@ page import="java.util.*" %>


<%@include file="/common/antweb-defs.jsp" %>

<div class=left>

<h2>Specimen files uploaded before<%= request.getAttribute("groupName") == null ? "" : ": " + Html.esc((String) request.getAttribute("groupName")) %></h2>


<%

    String domainApp = AntwebProps.getDomainApp();
%>
<p><a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a></p>
<p id="upload-history-help" style="max-width:900px">Copies of the specimen data files this group sent to AntWeb (its own uploads,
and TaxonWorks or GBIF files), newest first. Each name starts with the date and time of the upload. Click a file to download it,
for example to see exactly what was sent in an earlier upload. AntWeb's data for the group always come from its latest upload.</p>
<%
    ArrayList<String> files = (ArrayList<String>) request.getAttribute("files"); 
    if (files == null || files.isEmpty()) {
      out.println("<p><b>No specimen files found for this group.</b></p>");
      files = new ArrayList<>();
    }
    int count = 0;
    out.println("<table cellpadding=10><tr><th>#</th>");
    out.println("<th>Files</th>");
    out.println("</tr>");
    for (String file : files) {
      ++count;
      out.println("<tr><td>" + count + "</td><td><a href=\"" + domainApp + "/uploadFile.do?file=" + Html.escAttr(Html.url(file)) + "\">" + Html.esc(file) + "</a></td></tr>");
    }
    out.println("</table>");
%>

</div > 
