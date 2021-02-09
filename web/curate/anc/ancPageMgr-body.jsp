<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<jsp:setProperty name="ancFile" property="*" />


<%@include file="/curate/curatorCheck.jsp" %>

<%	
	ArrayList<AncFile> ancFileArray = (ArrayList<AncFile>) request.getAttribute("ancFileArray");
%>

<div class="in_admin">
<h1>Ancillary Page Manager</h1>
</div>

<div class="admin_left">

<br><br>
<table>
<tr><td>Title</td><td>Project</td><td>Last Changed</td><td>Directory</td><td>File Name</td><td>Curator</td></tr>
<% for (AncFile ancFile : ancFileArray) { %>
<tr>
<% String dir = "";
   //if (ancFile.getDirectory() != null) dir += ancFile.getDirectory() + "/";
   dir = ancFile.getUrlLoc();
%>

<td><a href="<%= dir %><%= ancFile.getFileName() %>.jsp"><%= ancFile.getTitle() %></a></td>
<td><%= ancFile.getProject() %></td>
<td><%= ancFile.getLastChanged() %></td>
<td><%= ancFile.getDirectory() %></td>
<td><%= ancFile.getFileName() %></td>         
<td><%= ancFile.getAccessLoginId() %></td>         
<td></td>
</tr>
<% } %>
</table>

</div>
