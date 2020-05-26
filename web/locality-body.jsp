<%@ page language="java" %>
<%@ page import = "java.util.ResourceBundle" %>
<%@ page import = "java.util.ArrayList" %>
<%@ page import = "org.calacademy.antweb.*" %>
<%@ page import = "org.calacademy.antweb.util.*" %>
<%@ page import = "org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<% 
    Locality locality = (Locality) request.getAttribute("locality");
    
    Taxon taxon = (Taxon) session.getAttribute("taxon");
    Specimen specimen = (Specimen) session.getAttribute("specimen");

    Overview overview = OverviewMgr.getOverview(request);
    if (overview == null) overview = ProjectMgr.getProject(Project.ALLANTWEBANTS);
    
    String the_page = HttpUtil.getTarget(request);
    String pageContainer = "showBrowse";
%>
<input id="for_print" type="text" value="AntWeb. Available from: <%= HttpUtil.getFullJspTarget(request)%>">
<input id="for_web" type="text" value="URL: <%= HttpUtil.getFullJspTarget(request)%>">
<div class="page_contents">
    <h1>Locality: <bean:write name="locality" property="localityName"/></h1>
</div>

<div class="page_divider"></div>

<div id="page_data">
    <div id="overview_data" class="plain">
		<ul>
		<li>Code:</li>
		<li><b><%= locality.getLocalityCode() %></b></li>
		</ul>

		<ul>
		<li>Name:</li>
		<li><b><bean:write name="locality" property="localityName"/></b></li>
		</ul>
		
		<ul>
		<li>Bioregion:</li>
		<li><b><bean:write name="locality" property="bioregion"/></b></li>
		</ul>		

		<ul>
		<li>Museum:</li>
		  <li><b><a href="<%= AntwebProps.getDomainApp() %>/museum.do?name=<%= locality.getMuseumCode() %>"><%= locality.getMuseumName() %></a></b></li>
		</ul>

		<% if (Utility.displayEmptyOrNotBlank(locality.getCountry())) { %>
		<ul><li>Country:</li>
		<%
		   String countryLink = "";
		   if (locality.getCountry() != null) {
			 countryLink = "<a href='" + AntwebProps.getDomainApp() + "/country.do?name=" + locality.getCountry() + "'>" + locality.getCountry() + "</a>";
		   } %>
		<li><b><%= countryLink %></b></li></ul>
		<% }

     if (locality.getIslandCountry() != null) { %>
		<ul><li>Island:</li>
		<%
		   String islandCountryLink = "";
		     Geolocale island = GeolocaleMgr.getIsland(locality.getIslandCountry());
		     //A.log("locality-body.jsp adm1:" + adm1 + " country:" + locality.getCountry() + " adm1:" + locality.getAdm1());
		     if (island != null) {
  	 	       //A.log("locality-body.jsp parent:" + adm1.getParent() + " adm1:" + adm1.getName());
  			   islandCountryLink = "<a href='" + AntwebProps.getDomainApp() + "/island.do?name=" + island.getName() + "'>" + island.getName() + "</a>";
  		     } %>
		<li><b><%= islandCountryLink %></b> </li></ul>
  <% } else {
		 if (Utility.displayEmptyOrNotBlank(locality.getAdm1())) { %>
		<ul><li>Adm1:</li>
		<%
		   String adm1Link = "";
           Geolocale adm1 = GeolocaleMgr.getAdm1(locality.getAdm1(), locality.getCountry());
           A.log("locality-body.jsp adm1:" + adm1 + " country:" + locality.getCountry() + " adm1:" + locality.getAdm1());
           if (adm1 == null) {
             adm1Link = locality.getAdm1();
           } else {
             //A.log("locality-body.jsp parent:" + adm1.getParent() + " adm1:" + adm1.getName());
             adm1Link = "<a href='" + AntwebProps.getDomainApp() + "/adm1.do?id=" + adm1.getId() + "'>" + adm1.getName() + "</a>";
           } %>
		<li><b><%= adm1Link %></b> </li></ul>
		<% } %>

		<% if (Utility.displayEmptyOrNotBlank(locality.getAdm2())) { %>
		<ul><li>Adm2:</li>
		<li><b><bean:write name="locality" property="adm2"/></b></li></ul>
		<% }
     }

		String lat = new Float(locality.getDecimalLatitude()).toString();
		if (Utility.displayEmptyOrNotBlank(lat)) { %>
		<ul>
		<li>Latitude:</li>
		<li><b><%= Utility.notBlankValue(lat) %></b></li>
		</ul>
		<% } %>		
		
		<% 
		String lon = new Float(locality.getDecimalLongitude()).toString();		
		if (Utility.displayEmptyOrNotBlank(lon)) { %>
		<ul>
		<li>Longitude:</li>
		<li><b><%= Utility.notBlankValue(lon) %></b></li>
		</ul>
		<% } %>

		<% if (Utility.displayEmptyOrNotBlank(locality.getLatLonMaxError())) { %>
		<ul>
		<li>Lat. Lon. max error:</li>
		<li><b><bean:write name="locality" property="latLonMaxError"/></b></li>
		</ul>
		<% } %>

		<% if (Utility.displayEmptyOrNotBlank(locality.getElevation())) { %>
		<ul>
		<li>Elevation:</li>
		<li><b><bean:write name="locality" property="elevation"/> <%= (locality.getElevation() != null) ? "m" : "" %></b></li>   
		</ul>
		<% } %>

		<% if (Utility.displayEmptyOrNotBlank(locality.getElevationMaxError())) { %>
		<ul>
		<li>Elevation max error:</li>
		<li><b><bean:write name="locality" property="elevationMaxError"/></b></li>
		</ul>
		<% } %>

		<% if (Utility.displayEmptyOrNotBlank(locality.getLocalityNotes())) { %>
		<ul>
		<li>Locality notes:</li>
		<li><bean:write name="locality" property="localityNotes"/></li>
		</ul>
		<% } %>

<% 
   // Show All Species advanced search link.

// &localityNameSearchType=contains&localityName=Ambr
   
   // The Show All Specimen advanced search link.
   if (!HttpUtil.isBot(request)) {
     if (locality.getLocalityCode() != null) {
		 String specimenSearchUrl = "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&localityCodeSearchType=equals&localityCode=\"" + locality.getLocalityCode() + "\"";
		 String speciesSearchUrl = specimenSearchUrl + "&resultRank=species";	 
		 out.println("<br><br><b><a href='" + AntwebProps.getDomainApp() + speciesSearchUrl + "'>Show All Species<a></b>");     
		 out.println(" | <b><a href='" + AntwebProps.getDomainApp() + specimenSearchUrl + "'>Show All Specimens<a></b>");     
	   } else if (locality.getLocalityName() != null) {
		 String specimenSearchUrl = "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&localityNameSearchType=equals&localityName=\"" + locality.getLocalityName() + "\"";
		 String speciesSearchUrl = specimenSearchUrl + "&resultRank=species";
		 out.println("<br><br><b><a href='" + AntwebProps.getDomainApp() + speciesSearchUrl + "'>Show All Species<a></b>");     
		 out.println(" | <b><a href='" + AntwebProps.getDomainApp() + specimenSearchUrl + "'>Show All Specimens<a></b>");        
	   }
   }
   %>

    </div>


    <div class="left">
		<%
		if (locality != null) {
		  if (locality.getMap() != null) {
			Map map = locality.getMap(); 
			String object = "locality";
			String objectName = locality.getObjectName();
			String mapType = "locality";
		%>
			<div class="small_map">
				<%@include file="/maps/googleMapPreInclude.jsp" %>  
				<%@include file="/maps/googleMapInclude.jsp" %>  
			</div>
		<%    } else {
			AntwebUtil.log("locality-body.jsp WSS.  No map for locality:" + locality.getLocalityName());
		  }
		} else {
		  AntwebUtil.log("locality-body.jsp WSS.  locality is null.  QueryString:" + request.getQueryString());
		}
		%>
    </div>
</div>

<div class="clear"></div>

<div id="page_data">
	<br /><br />
	<% if (locality.getCollections().size() < 1) { %>

	No specimens in this collection.

	<% } else {
	  String pluralStr = (locality.getCollections().size() > 1) ? "s" : "";
	 %>

	<h3><%= locality.getCollections().size() + "&nbsp;collection" + pluralStr  %> </h3>

	<div class="clear"></div>

	<div class="browse_col_one"><span class="col_header">Collection</span></div>
	<div class="browse_col_two"><span class="col_header">Collected by</span></div>
	<div class="browse_col_three"><span class="col_header">Method</span></div>
	<div class="browse_col_four"><span class="col_header">Habitat</span></div>
	<div class="clear"></div>
	<hr></hr>

	<logic:iterate id="row" name="locality" property="collections">

	<% if (((Collection) row).getCollectionCode() != null) { %>

	<bean:define id="prettycode" name="row" property="collectionCode"/>

	<div class="browse_col_one">
		<logic:notEmpty name="row" property="collectionCode">
		<a href="collection.do?name=<bean:write name="row" property="collectionCode"/>">
		<b><%= ((String) prettycode).toUpperCase() %></b>
		</a>
		</logic:notEmpty>
	</div>

	<% } else { %>
	<div class="browse_col_one"></div>
	<% } %>

	<div class="browse_col_two">
	<logic:notEmpty name="row" property="collectedBy">
	<bean:write name="row" property="collectedBy"/>
	</logic:notEmpty>
	</div>

	<div class="browse_col_three">
	<logic:notEmpty name="row" property="method">
	<bean:write name="row" property="method"/>
	</logic:notEmpty>
	</div>

	<div class="browse_col_four">
	<logic:notEmpty name="row" property="habitat">
	<bean:write name="row" property="habitat"/>
	</logic:notEmpty>
	<logic:notEmpty name="row" property="microhabitat">
	- <bean:write name="row" property="microhabitat"/>
	</logic:notEmpty>
	</div>

	<div class=clear></div>

	<hr></hr>
	</logic:iterate>
	<% } %>

	<div class=clear></div>

	<% 
	  // For example of multiple "uploaded by" locality: /locality.do?name=JTL049770
	   String institutionsStr = locality.getInstitutionsStr();
	   String amissEmail = locality.getAmissEmail();
	%>
	<br />
	<p>Specimen data uploaded by <%= institutionsStr %> | <a href="mailto:<%= amissEmail %>?subject=Regarding AntWeb page <%= the_page %>">See something amiss? Send us an email.</a></p>
</div>
