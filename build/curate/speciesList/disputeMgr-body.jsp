<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<%@include file="/curate/curatorCheck.jsp" %>

<%	
	ArrayList<ProjTaxon> disputeArray = (ArrayList<ProjTaxon>) request.getAttribute("disputes");
	
%>

<div class="in_admin">
<h1>Dispute Manager</h1>
</div>

<div class="admin_left">

<br><br>
<table>

<%
   AntwebUtil.log("disputeMgr-body.jsp");

%>

<tr><th>Project Name</th><th>Taxon Name</th><th>Remove</th>
<% 
 //if (AntwebProps.isDevMode()) AntwebUtil.log("disputeMgr-body.jsp");

for (ProjTaxon dispute : disputeArray) { 
%>
<tr>
<td><%= dispute.getProjectName() %></td>
<td><%= dispute.getTaxonName() %></td>
<td><a href="<%= domainApp %>/disputeMgr.do?action=remove&projectName=<%= dispute.getProjectName() %>&taxonName=<%= dispute.getTaxonName() %>"><img src='<%= AntwebProps.getDomainApp() %>/image/redX.png' width=10></a></td>
</tr>
<% } %>
</table>
<br><br>

</div>
