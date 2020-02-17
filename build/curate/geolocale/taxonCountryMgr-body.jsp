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
	ArrayList<Geolocale> geolocaleArray = (ArrayList<Geolocale>) request.getAttribute("geolocaleArray");
	
	String georank = (String) request.getAttribute("georank");
	if (georank == null) georank = "country";
%>

<div class="in_admin">
<h1>Geo Locale Manager: <%= org.calacademy.antweb.Formatter.initCap(georank) %></h1>
</div>

<html:form method="POST" action="geolocaleMgr">
View:
  <select name="georank">
  <option value="region"<%= ("region".equals(georank) ? "selected" : "") %>>Region</option>
  <option value="subregion"<%= ("subregion".equals(georank) ? "selected" : "") %>>Subregion</option>
  <option value="country"<%= ("country".equals(georank) ? "selected" : "") %>>Country</option>
  <option value="adm1"<%= ("adm1".equals(georank) ? "selected" : "") %>>adm1</option>
  </select>
  <input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="Submit">
</html:form>

<div class="admin_left">

<br><br>
<table>
<% String parentHeading = "NONE";
   if ("subregion".equals(georank)) parentHeading = "Region";
   if ("country".equals(georank)) parentHeading = "Subregion";
   if ("adm1".equals(georank)) parentHeading = "Country";
%>
<tr><th>Id</th><th>Name</th><th>isValid</th><th>isUN</th><th>Source</th><th>Valid Name</th>
<% if (!"NONE".equals(parentHeading)) { %>
  <th><%= parentHeading %></th>
<% } %>
<th>Region</th>
<th>Bioregion</th>
<th>IsLive</th><th>Extent</th><th>Coords</th>

<% 
if (AntwebProps.isDevMode()) AntwebUtil.log("geolocaleMgr-body.jsp georank:" + georank);

if (geolocaleArray == null) {
  AntwebUtil.log("taxonCountryMgr-body.jsp geolocaleArray is null");
  return;
}

for (Geolocale geolocale : geolocaleArray) { %>
<tr>
<td><a href="<%= domainApp %>/editGeolocale.do?id=<%= geolocale.getId() %>"><%= geolocale.getId() %></a></td>
<td><%= geolocale.getName() %></td>
<td>
<% if (geolocale.isValid()) { %><b><% } %>
<%= geolocale.isValid() %></td>
<% if (geolocale.isValid()) { %></b><% } %>
<td><%= geolocale.isUn() %></td>
<td><%= geolocale.getSource() %></td>
<td><%= geolocale.getValidName() %></td>
<% if (!"NONE".equals(parentHeading)) { %>
<td><input type="text" class="input_140" name="parent" value="<%= geolocale.getParent() %>" disabled></td>
<% } %>
<td><%= geolocale.getRegion() %></td>
<td><%= geolocale.getBioregion() %></td>

<td><%= geolocale.getIsLive() %></td>
<td><input type="text" class="input_20" name="extent" value="<%= geolocale.getExtent() %>" disabled></td>
<td><input type="text" class="input_20" name="coords" value="<%= geolocale.getCoords() %>" disabled></td>
<!-- td><input type="text" class="input_120" name="created" value="< %= geolocale.getCreated() % >" disabled></td -->
<td></td>
</tr>
<% } %>
</table>
<br><br>

<html:form method="POST" action="geolocaleMgr">
<input type="hidden" class="input_200" name="georank" value="<%= georank %>">
Create <%= georank %> with name:<input type="text" class="input_20" name="name" value="">
<input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="Submit">
</html:form>
</div>
