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

<h2>Upload History</h2>


<%

    String domainApp = AntwebProps.getDomainApp();
    
    String message = null; // (String) request.getAttribute("message"); 
    if (message != null) out.println("<h2>" + message + "</h2>");
%>
<br><br><p><p><a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>
<%
    ArrayList<String> files = (ArrayList<String>) request.getAttribute("files"); 
    int count = 0;
    out.println("<table cellpadding=10><tr><th>#</th>");
    out.println("<th>Files</th>");
    out.println("</tr>");
    for (String file : files) {
      ++count;
      out.println("<tr><td>" + count + "</td><td><a href=\"" + domainApp + "/web/upload/" + file + "\">" + file + "</a></td></tr>");
    }
    out.println("</table>");
%>

</div > 
