<%-- Body of the Dispute Manager: the project species-list disputes (request attribute disputes, table
     proj_taxon_dispute) with a remove button each (POST with the form token to disputeMgr.do?action=remove). --%>
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
<td><%= Html.esc(dispute.getProjectName()) %></td>
<td><%= Html.esc(dispute.getTaxonName()) %></td>
<td><%= Csrf.postButton(request, domainApp + "/disputeMgr.do?action=remove&projectName=" + Html.url(dispute.getProjectName()) + "&taxonName=" + Html.url(dispute.getTaxonName()), "<img src='" + AntwebProps.getDomainApp() + "/image/redX.png' width=10 alt='remove'>") %></td>
</tr>
<% } %>
</table>
<br><br>

</div>
