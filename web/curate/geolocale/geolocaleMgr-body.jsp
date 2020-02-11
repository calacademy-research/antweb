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
    if (parentName != null) titleAddendum = " of <a href='" + domainApp + "/editGeolocale.do?name=" + parentName + "&georank=" + parentGeorank + "'>" + parentName + "</a>";
%>

<div class="in_admin">
<h1>Geolocale Manager:</h1>
<h2>&nbsp;&nbsp;&nbsp;&nbsp;<font color=green><%= org.calacademy.antweb.Formatter.initCap(Georank.getPluralRank(georank)) %><%= titleAddendum %></font></h2>
</div>

<br>

<html:form method="POST" action="geolocaleMgr">
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
        String parentClause = "&parent=" + parent.getParent();
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


String params = "georank=" + georank;
if (parentName != null) params += "&parent=" + parentName;
String target = AntwebProps.getDomainApp() + "/geolocaleMgr.do?" + params;
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
<th>Update</th>
<% if (!"NONE".equals(parentHeading)) { %>
  <th><%= parentLink %></th>
<% } %>
<th><%= regionLink %></th>
<% if (!"adm1".equals(georank)) { %>
<th><%= bioregionLink %></th>
<% } %>
<th>Woe ID</th>
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

<form id="geolocaleMgrForm" action="<%= thisTarget %>#<%= geolocale.getId() %>" method="POST">

<td>
<input type="hidden" name="parent" value="<%= geolocale.getParent() %>">
<input type="hidden" name="georank" value="<%= geolocale.getGeorank() %>">

<a name="<%= geolocale.getId() %>"></a> 
<a href="<%= domainApp %>/editGeolocale.do?id=<%= geolocale.getId() %>"><%= geolocale.getName() %></a>

<% if ("adm1".equals(georank)) {
	   String strVal = "<td><a href=\"" + AntwebProps.getDomainApp() + "/adm1Mgr.do?adm1Name=" 
	     + geolocale.getName() + "&countryName=" + geolocale.getParent() + "\"><img src='" + domainApp + "/image/view_icon.png'></a></td>";
       
       //strVal = URLEncoder.encode(strVal, "UTF-8");
       
%> <%= strVal %> <%
   } else {
	   String strVal = "<td></td>";
	   Geolocale mgrGeolocale = GeolocaleMgr.getDeepGeolocale(geolocale.getId());
	   if (mgrGeolocale != null) {     
		 int size = mgrGeolocale.getValidChildren().size();
		 if (size > 0) {
		   strVal = "<td><a href='" + domainApp + "/geolocaleMgr.do?georank=" + Georank.getChild(georank) + "&parent=" + geolocale.getName() + "'>" + size + "</a></td>";    
		 }
         //A.log("geolocaleMgr-body.jsp size:" + size);
		 %>
	<% } %>
	<%= strVal %>
<% } %>
</td>
<input type="hidden" name="id" value="<%= geolocale.getId() %>">
<input type="hidden" name="name" value="<%= geolocale.getName() %>">
<td><input type="checkbox" name="isValid" <%= (geolocale.getIsValid() == true)?"checked":"" %>></td>
<td><input type="checkbox" name="isLive" <%= (geolocale.getIsLive() == true)?"checked":"" %>></td>
<td><input type="checkbox" name="isUn" <%= (geolocale.isUn() == true)?"checked":"" %> disabled></td>

<td><%= geolocale.getSource() %></td>

<td>
<%
 String parentRank = Georank.getParent(georank);
 if (parentName != null && parentRank != null) {
 
   if (!geolocale.getIsValid()) { %>
 
<%@include file="/curate/geolocale/validSelect.jsp" %>

<% }   
 } else { %>
 <%= geolocale.getValidName() %>
<%
 }

	   %>	
  
</td>

<% if (!"NONE".equals(parentHeading)) { 
   String parentStr = geolocale.getParent();
   if (parentStr == null) parentStr = "";
%>

<td>
<input border="0" type="image" src="<%= domainApp %>/image/upGreen.jpg" width="25" value="Submit">
</td>

<td><%= parentStr %></td>

<% } %>
<td><%= geolocale.getRegion() %></td>
<% if (!"adm1".equals(georank)) { %>
<td><%= geolocale.getBioregion() %></td>
<% } 

String woeIdMarkup = "";
if (geolocale.getWoeId() != null && !("null".equals(geolocale.getWoeId()))) {
  woeIdMarkup = "<a href='https://www.flickr.com/places/info/" + geolocale.getWoeId() + "'>" + geolocale.getWoeId() + "</a>" 
    + "<input type='hidden' name='woeId' value='" + geolocale.getWoeId() + "'>";
} else {
  woeIdMarkup = "<input type='text' class='input_50' name='woeId' value=''>";
}
%>
<td><%= woeIdMarkup %></td>

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

<html:form method="GET" action="editGeolocale">
<input type="hidden" class="input_200" name="georank" value="<%= georank %>">
<input type="hidden" name="parent" value="<%= parentName %>">
<input type="hidden" name="isCreate" value="true">
Create <%= georank %> with name:<input type="text" class="input_20" name="name" value="">
<input border="0" type="image" src="<%= domainApp %>/image/addIcon.png" height="23" value="Submit">
</html:form>

<br>
<hr>
<br>
After changes are made, click to reload <a href='<%= AntwebProps.getDomainApp() %>/util.do?action=reloadAntwebMgr'>GeolocaleMgr</a> to be sure all changes are made live.
<br><br>
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
      <li><a href="<%= domainApp %>/utilData.do?action=taxaOutsideOfNativeBioregion">Taxa Outside of Native Bioregion Report</a>   
</ul>
</div>


</div>
