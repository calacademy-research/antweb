<%-- Body of the Geolocale Manager (request attributes geolocaleArray, georank, parent, validChildren): a rank
     menu, one row per place with valid, live and valid name (geolocale/validSelect.jsp) and a save arrow for
     admins (POST to geolocaleMgr.do), a Create box for admins (POST to editGeolocale.do) and links to related
     pages and queries. Docs: docs/guides/manager-tools.md (Geolocale Manager). --%>
<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="java.net.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<%@include file="/curate/curatorCheck.jsp" %>

<%	
	ArrayList<Geolocale> geolocaleArray = (ArrayList<Geolocale>) request.getAttribute("geolocaleArray");

	int geolocaleCount = 0;	
	int validGeolocaleCount = 0;
	for (Geolocale geolocale : geolocaleArray) { 
      ++geolocaleCount;
	  if (geolocale.getIsValid()) ++validGeolocaleCount;
    }
	
	String georank = (String) request.getAttribute("georank");
	String parentName = (String) request.getAttribute("parent");
    ArrayList<Geolocale> validChildren = (ArrayList<Geolocale>) request.getAttribute("validChildren");
    
	
	if (georank == null) georank = "country";
	String parentGeorank = Georank.getParent(georank);
    String titleAddendum = "";
    if (parentName != null) titleAddendum = " of <a href='" + domainApp + "/editGeolocale.do?name=" + Html.escAttr(Html.url(parentName)) + "&georank=" + Html.escAttr(Html.url(parentGeorank)) + "'>" + Html.esc(parentName) + "</a>";
%>

<div class="in_admin">
<h1>Geolocale Manager:</h1>
<h2>&nbsp;&nbsp;&nbsp;&nbsp;<font color=green><%= Html.esc(org.calacademy.antweb.Formatter.initCap(Georank.getPluralRank(georank))) %><%= titleAddendum %></font></h2>
&nbsp;&nbsp;&nbsp;&nbsp;Tables: <a href="<%= domainApp %>/geolocaleMgr.do?name=displayGeoregions">georegions</a> (region, subregion, country)
 | <a href="<%= domainApp %>/geolocaleMgr.do?name=displayBioregions">bioregions</a>
</div>

<div class="mgr_help" style="max-width:70em;margin:6px 16px 12px 16px">
The places AntWeb uses: regions, subregions, countries and adm1s (states, provinces). Choose a rank and
click the magnifier; a number in the second column opens that place's children. Click a name to open all
its settings (Edit Geolocale). <b>Valid</b>: an accepted name, with its own page and species list.
<b>Live</b>: listed in the site's region menus. <b>UN</b>: on the UN list (fixed). <b>Valid Name</b>: for
an invalid name, the valid place that specimen data with this name are moved to at upload.
<b>C</b> and <b>BB</b>: a map centre and a bounding box are set. The green arrow saves that row's
Valid, Live and Valid Name at once (administrators only; curators can look). The change is live
immediately; the counts follow in the nightly run.
</div>
<% String mgrMessage = (String) request.getAttribute("message");
   if (mgrMessage != null) { %><p style="margin-left:16px"><font color=green><%= mgrMessage %></font></p><% } %>

<br>

<html:form method="POST" action="geolocaleMgr"><%= org.calacademy.antweb.util.Csrf.field(request) %>
  <select name="georank">
  <option value="region"<%= ("region".equals(georank) ? "selected" : "") %>>Region</option>
  <option value="subregion"<%= ("subregion".equals(georank) ? "selected" : "") %>>Subregion</option>
  <option value="country"<%= ("country".equals(georank) ? "selected" : "") %>>Country</option>
  <option value="adm1"<%= ("adm1".equals(georank) ? "selected" : "") %>>adm1</option>
  </select>
  <input border="0" type="image" src="<%= domainApp %>/image/view_glass.jpg" height="23" value="Submit">  
  <br>
</html:form>

<div class="admin_left">
<%
    Geolocale parent = null; 
    if (parentName != null) {
      parent = GeolocaleMgr.getGeolocale(parentName); 
      if (parent != null) { 
        String parentClause = "&parent=" + Html.escAttr(Html.url(parent.getParent()));
        if (parent.getParent() == null || "null".equals(parent.getParent())) parentClause = "";
      %>
<a href='<%= domainApp %>/geolocaleMgr.do?georank=<%= parentGeorank %><%= parentClause %>'><img src='<%= domainApp %>/image/upLeft.png' width=20></a>
 <%   }
    } %>
<br>
<br>
(Valid:<%= validGeolocaleCount %> Total:<%= geolocaleCount %>)
<br><br>
<%
String thisTarget = HttpUtil.getTarget(request);
%>

<table>
<% String parentHeading = "NONE";
   if ("subregion".equals(georank)) parentHeading = "Region";
   if ("country".equals(georank)) parentHeading = "Subregion";
   if ("adm1".equals(georank)) parentHeading = "Country";


String params = "georank=" + Html.url(georank);
if (parentName != null) params += "&parent=" + Html.url(parentName);
String target = AntwebProps.getDomainApp() + "/geolocaleMgr.do?" + Html.escAttr(params);
String nameLink = "<a href='" + target + "&orderBy=name'>Name</a>";
String parentLink = "<a href='" + target + "&orderBy=parent'>" + parentHeading + "</a>";
String validLink = "<a href='" + target + "&orderBy=isValid'>Valid</a>";
String validNameLink = "<a href='" + target + "&orderBy=validName'>Valid Name</a>";
String liveLink = "<a href='" + target + "&orderBy=isLive'>Live</a>";
String sourceLink = "<a href='" + target + "&orderBy=source'>Source</a>";
String regionLink = "<a href='" + target + "&orderBy=region'>Region</a>";
String bioregionLink = "<a href='" + target + "&orderBy=bioregion'>Bioregion</a>";

%>
<tr>
<th><%= nameLink %></th>

<% if ("adm1".equals(georank)) { %>
<th>Mgr</th>
<% } else { %>
<th><img src="<%= domainApp %>/image/view_icon.png"></th>
<% } %>
<th><%= validLink %></th>
<th><%= liveLink %></th>
<th>UN</th>
<th><%= sourceLink %></th>
<th><%= validNameLink %></th>
<% if (!"NONE".equals(parentHeading)) { %>
  <th>Save</th>
  <th><%= parentLink %></th>
<% } %>
<th><%= regionLink %></th>
<% if (!"adm1".equals(georank)) { %>
<th><%= bioregionLink %></th>
<% } %>
<th>C</td>
<th>BB</td>
</tr>
<tr><td><hr></td>
<td><hr></td>
<td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td></tr>

<% 
//A.log("geolocaleMgr-body.jsp georank:" + georank);

for (Geolocale geolocale : geolocaleArray) { 
%>
<tr>

<form id="geolocaleMgrForm" action="<%= Html.escAttr(thisTarget) %>#<%= geolocale.getId() %>" method="POST"><%= org.calacademy.antweb.util.Csrf.field(request) %>

<td>
<%-- After saving, show the same list again (the list's parent, not the row's). --%>
<% if (parentName != null) { %><input type="hidden" name="parent" value="<%= Html.escAttr(parentName) %>"><% } %>
<input type="hidden" name="georank" value="<%= Html.escAttr(geolocale.getGeorank()) %>">

<a name="<%= geolocale.getId() %>"></a> 
<a href="<%= domainApp %>/editGeolocale.do?id=<%= geolocale.getId() %>"><%= Html.esc(geolocale.getName()) %></a>

<% if ("adm1".equals(georank)) {
	   String strVal = "<td><a href=\"" + AntwebProps.getDomainApp() + "/adm1Mgr.do?adm1Name=" 
	     + Html.escAttr(Html.url(geolocale.getName())) + "&countryName=" + Html.escAttr(Html.url(geolocale.getParent())) + "\"><img src='" + domainApp + "/image/view_icon.png'></a></td>";
       
       //strVal = URLEncoder.encode(strVal, "UTF-8");
       
%> <%= strVal %> <%
   } else {
	   String strVal = "<td></td>";
	   Geolocale mgrGeolocale = GeolocaleMgr.getDeepGeolocale(geolocale.getId());
	   if (mgrGeolocale != null) {     
		 int size = mgrGeolocale.getValidChildren().size();
		 if (size > 0) {
		   strVal = "<td><a href='" + domainApp + "/geolocaleMgr.do?georank=" + Georank.getChild(georank) + "&parent=" + Html.escAttr(Html.url(geolocale.getName())) + "'>" + size + "</a></td>";    
		 }
         //A.log("geolocaleMgr-body.jsp size:" + size);
		 %>
	<% } %>
	<%= strVal %>
<% } %>
</td>
<input type="hidden" name="id" value="<%= geolocale.getId() %>">
<input type="hidden" name="name" value="<%= Html.escAttr(geolocale.getName()) %>">
<td><input type="checkbox" name="isValid" <%= (geolocale.getIsValid() == true)?"checked":"" %>></td>
<td><input type="checkbox" name="isLive" <%= (geolocale.getIsLive() == true)?"checked":"" %>></td>
<td><input type="checkbox" name="isUn" <%= (geolocale.isUn() == true)?"checked":"" %> disabled></td>

<td><%= Html.esc(geolocale.getSource()) %></td>

<td>
<%
 String parentRank = Georank.getParent(georank);
 if (parentName != null && parentRank != null) {
 
   if (!geolocale.getIsValid()) { %>
 
<%@include file="/curate/geolocale/validSelect.jsp" %>

<% }   
 } else { %>
 <%= Html.esc(geolocale.getValidName()) %>
<%
 }

	   %>	
  
</td>

<% if (!"NONE".equals(parentHeading)) { 
   String parentStr = geolocale.getParent();
   if (parentStr == null) parentStr = "";
%>

<td>
<% // Saving is for administrators (GeolocaleMgrAction refuses others): no Save arrow for curators.
   if (LoginMgr.isAdmin(request)) { %>
<input border="0" type="image" src="<%= domainApp %>/image/upGreen.jpg" width="25" value="Submit">
<% } %>
</td>

<td><%= Html.esc(parentStr) %></td>

<% } %>
<td><%= Html.esc(geolocale.getRegion()) %></td>
<% if (!"adm1".equals(georank)) { %>
<td><%= Html.esc(geolocale.getBioregion()) %></td>
<% } %>

<% if (geolocale.useCentroid() != null && !("".equals(geolocale.useCentroid()))) { %>
<td><img src="<%= AntwebProps.getDomainApp() %>/image/checkmark.gif" height="10"></td>
<% } else {%>
<td></td>
<% } %>

<% if (geolocale.useBoundingBox() != null && !("".equals(geolocale.useBoundingBox()))) { %>
<td><img src="<%= AntwebProps.getDomainApp() %>/image/checkmark.gif" height="10"></td>
<% } else {%>
<td></td>
<% } %>

</form>

</tr>
<% } %>
<tr><td><hr></td>
<td><hr></td>
	<td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td></tr>

</table>
(Count:<%= geolocaleCount %>)
<br>
<br>
<br>
<hr>

<!-- a href="< %= domainApp % >/editGeolocale.do?parent=< %= parentName % >&isCreate=true&georank=< %= georank % >name=">Create</a> Adm1. -->

<% if (LoginMgr.isAdmin(request)) {
     if (parentName == null && !"region".equals(georank)) { %>
To add a <%= Html.esc(georank) %>, first open the list of the place it belongs to (for an adm1: Country, then the number next to the country), then use the Create box there.
<%   } else { %>
<html:form method="POST" action="editGeolocale"><%= org.calacademy.antweb.util.Csrf.field(request) %>
<input type="hidden" class="input_200" name="georank" value="<%= Html.escAttr(georank) %>">
<% if (parentName != null) { %><input type="hidden" name="parent" value="<%= Html.escAttr(parentName) %>"><% } %>
<input type="hidden" name="isCreate" value="true">
Create <%= Html.esc(georank) %><%= parentName == null ? "" : " in " + Html.esc(parentName) %> with name:<input type="text" class="input_20" name="name" value="">
<input border="0" type="image" src="<%= domainApp %>/image/addIcon.png" height="23" value="Submit">
(created not valid and not live; open it afterwards to set its bioregion, map centre and bounding box)
</html:form>
<%   }
   } %>

<br>
<hr>
<br>
<hr>
<br><br>
<div class="admin_left">
<ul>
      <li><a href="<%= domainApp %>/countryDoc.do">Country Documentation</a>
      <li><a href="<%= domainApp %>/disputeMgr.do">Dispute Mgr</a>
      <li><b>Relevant Queries</b>
        <%= Queries.getQueryList("Geolocale") %>
      <li><a href="<%= domainApp %>/bioregionCountryList.do">Bioregion Country List</a>
      <li><a href="<%= domainApp %>/countryAdm1List.do">Country Adm1 List</a>   
      <li><a href="<%= domainApp %>/query.do?action=taxaOutsideOfNativeBioregion">Taxa outside their native bioregions</a>   
</ul>
</div>


</div>
