<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="java.sql.Timestamp" %>
<%@ page import="java.net.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.upload.Upload" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%
	ArrayList<Group> groupArray = (ArrayList<Group>) request.getAttribute("groups");
%>

<div class="in_admin">
<br>
<h1>Specimen Contributors:</h1>
</div>

<br>

<table>
<%
String here = HttpUtil.getTarget(request);
if (!here.contains("orderBy=")) here += "?orderBy=name";
String target = AntwebProps.getDomainApp() + "/groups.do";
String a = "<span style='font-weight: 900;'>"; // "*"; //"<b>"; //"<font color=blue>";
String b = "</span>"; //"";  //"</b>"; //"</font>";
String c = "<span style='font-weight: 500;'>"; // "*"; //"<b>"; //"<font color=blue>";
String d = "</span>"; //"";  //"</b>"; //"</font>";

String nameLink = "<a href='" + target + "?orderBy=name'>Contributor</a>";
String uploadsLink = "<a href='" + target + "?orderBy=uploads'>Uploads</a>";
String firstUploadLink = "<a href='" + target + "?orderBy=firstUpload'>First Upload</a>";
String lastUploadLink = "<a href='" + target + "?orderBy=lastUpload'>Last Upload</a>";
String specimensLink = "<a href='" + target + "?orderBy=specimens'>Specimens</a>";
String collectionsLink = "<a href='" + target + "?orderBy=collections'>Collections</a>"; 
String localitiesLink = "<a href='" + target + "?orderBy=localities'>Localities</a>";
String subfamiliesLink = "<a href='" + target + "?orderBy=subfamilies'>Subfamilies</a>";
String generaLink = "<a href='" + target + "?orderBy=genera'>Genera</a>";
String speciesLink = "<a href='" + target + "?orderBy=species'>Species</a>";
String ungeoreferencedLink = "<a href='" + target + "?orderBy=ungeoreferenced'>Ungeoreferenced</a>";
String flaggedLink = "<a href='" + target + "?orderBy=flagged'>Flagged</a>";
%>

<tr>
<th><%= nameLink %></th>
<th><%= uploadsLink %></th>
<th><%= firstUploadLink %></th>
<th><%= lastUploadLink %></th>
<th><%= specimensLink %></th>
<th><%= ungeoreferencedLink %></th>
<th><%= flaggedLink %></th>
<th><%= collectionsLink %></th>
<th><%= localitiesLink %></th>
<th><%= subfamiliesLink %></th>
<th><%= generaLink %></th>
<th><%= speciesLink %></th>
</tr>

<% String hrLine = "<tr><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td></tr>";
%>

<%= hrLine %>

<%
//A.log("geolocaleMgr-body.jsp georank:" + georank);
int contributorTot = 0;
int uploadTot = 0;
Timestamp firstUploadTot = null;
Timestamp lastUploadTot = null;
int specimenTot = 0;
int ungeoreferencedTot = 0;
int flaggedTot = 0;
int collectionTot = 0;
int localityTot = 0;
int subfamilyTot = 0;
int generaTot = 0;
int speciesTot = 0;

for (Group group: groupArray) {
  ++contributorTot;
  Upload upload = group.getLastUpload();
  if (upload == null) upload = new Upload(); %>
<tr>
<%
String t1 = ""; String t2 = "";
if (here.contains("orderBy=name")) { t1 = a; t2 = b; } else { t1 = c; t2 = d; } %>
<td><%= t1 %><a href='<%= AntwebProps.getDomainApp() %>/group.do?id=<%= group.getId() %>'><%= group.getName() %></a><%= t2 %></td>
<% if (here.contains("orderBy=uploads")) { t1 = a; t2 = b; } else { t1 = c; t2 = d; } 
   uploadTot += group.getUploadCount();
%>
<td align="right"><%= t1 %><%= Formatter.commaFormat(group.getUploadCount()) %><%= t2 %></td>

	 <% Timestamp firstUpload = null;
		if (group.getFirstUploadDate() != null) {
		  firstUpload = group.getFirstUploadDate();
		  if (firstUploadTot == null) firstUploadTot = firstUpload;
		  if (firstUpload != null && group.getFirstUploadDate().compareTo(firstUploadTot) < 0) firstUploadTot = group.getFirstUploadDate();
		} 
        if (here.contains("orderBy=firstUpload")) { t1 = a; t2 = b; } else { t1 = ""; t2 = ""; }		
		%>
<td align="right"><%= t1 %><%= Utility.getSimpleDate(firstUpload) %></td>

	 <% Timestamp lastUpload = null;
		if (group.getLastUploadDate() != null) {
		  lastUpload = group.getLastUploadDate();
		  if (lastUploadTot == null) lastUploadTot = lastUpload;
		  if (lastUpload != null && group.getLastUploadDate().compareTo(lastUploadTot) > 0) lastUploadTot = group.getLastUploadDate();
		} 
		if (here.contains("orderBy=lastUpload")) { t1 = a; t2 = b; } else { t1 = ""; t2 = ""; } 

String logFile = AntwebProps.getDomainApp() + "/web/log/upload/" + upload.getLogFileName();
t1 += "<a href='" + logFile + "'>";
t2 += "</a>";		
		%>		
<td align="right"><%= t1 %><%= Utility.getSimpleDate(lastUpload) %><%= t2 %></td>

<% if (here.contains("orderBy=specimens")) { t1 = a; t2 = b; } else { t1 = ""; t2 = ""; } 
   specimenTot += upload.getSpecimens();
%>
<td align="right"><%= t1 %><%= Formatter.commaFormat(upload.getSpecimens()) %><%= t2 %></td>
<% if (here.contains("orderBy=ungeoreferenced")) { t1 = a; t2 = b; } else { t1 = ""; t2 = ""; } 
   ungeoreferencedTot += upload.getUngeoreferenced();
%>
<td align="right"><%= t1 %><%= Formatter.commaFormat(upload.getUngeoreferenced()) %><%= t2 %></td>
<% if (here.contains("orderBy=flagged")) { t1 = a; t2 = b; } else { t1 = ""; t2 = ""; } 
   flaggedTot += upload.getFlagged();
%>
<td align="right"><%= t1 %><%= Formatter.commaFormat(upload.getFlagged()) %><%= t2 %></td>

<% if (here.contains("orderBy=collections")) { t1 = a; t2 = b; } else { t1 = ""; t2 = ""; } 
   collectionTot += upload.getCollections();
%>
<td align="right"><%= t1 %><%= Formatter.commaFormat(upload.getCollections()) %><%= t2 %></td>
<% if (here.contains("orderBy=localities")) { t1 = a; t2 = b; } else { t1 = ""; t2 = ""; } 
   localityTot += upload.getLocalities();
%>
<td align="right"><%= t1 %><%= Formatter.commaFormat(upload.getLocalities()) %><%= t2 %></td>
<% if (here.contains("orderBy=subfamilies")) { t1 = a; t2 = b; } else { t1 = ""; t2 = ""; } 
   if (upload.getSubfamilies() > subfamilyTot) subfamilyTot = upload.getSubfamilies();
%>
<td align="right"><%= t1 %><%= upload.getSubfamilies() %><%= t2 %></td>
<% if (here.contains("orderBy=genera")) { t1 = a; t2 = b; } else { t1 = ""; t2 = ""; } 
   if (upload.getGenera() > generaTot) generaTot = upload.getGenera();
%>
<td align="right"><%= t1 %><%= upload.getGenera() %><%= t2 %></td>
<% if (here.contains("orderBy=species")) { t1 = a; t2 = b; } else { t1 = ""; t2 = ""; } 
   if (upload.getSpecies() > speciesTot) speciesTot = upload.getSpecies();
%>
<td align="right"><%= t1 %><%= Formatter.commaFormat(upload.getSpecies()) %><%= t2 %></td>
</tr>
<tr></tr>
<% } %>

<%= hrLine %>
<tr>
<td><b>Totals:&nbsp;</b><%= contributorTot %></td>
<td align="right"><%= Formatter.commaFormat(uploadTot) %></td>
<td align="right"><%= Utility.getSimpleDate(firstUploadTot) %></td>
<td align="right"><%= Utility.getSimpleDate(lastUploadTot) %></td>
<td align="right"><%= Formatter.commaFormat(specimenTot) %></td>
<td align="right"><%= Formatter.commaFormat(ungeoreferencedTot) %></td>
<td align="right"><%= Formatter.commaFormat(flaggedTot) %></td>
<td align="right"><%= Formatter.commaFormat(collectionTot) %></td>
<td align="right"><%= Formatter.commaFormat(localityTot) %></td>
<td align="right"><%= subfamilyTot %></td>
<td align="right"><%= generaTot %></td>
<td align="right"><%= Formatter.commaFormat(speciesTot) %></td>
</tr>

</table>

<br>

