<%@ page language="java" %>
<%@ page import = "java.util.*" %>
<%@ page import = "org.calacademy.antweb.*" %>
<%@ page import = "org.calacademy.antweb.geolocale.*" %>
<%@ page import = "org.calacademy.antweb.Formatter" %>
<%@ page import = "org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Taxon" %>
<%@ page import="org.calacademy.antweb.Collection" %>
<%@ page import="org.calacademy.antweb.search.ResultItem" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.Overview" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<script type="text/javascript">
<!--
function selectAll(thisForm) {
  var count = thisForm.chosen.length;
  var checkedVal = thisForm.selectall.checked;
  for (var loop = 0; loop < count; loop++) {
    thisForm.chosen[loop].checked = checkedVal;
  }
}
// -->
</script>

<%
   String projectName = "";
   //Login accessLogin = LoginMgr.getAccessLogin(request);

   String the_page = HttpUtil.getTarget(request);

   if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

   Overview overview = OverviewMgr.getOverview(request);
   if (overview == null) overview = ProjectMgr.getProject(Project.ALLANTWEBANTS);
%>

<logic:present parameter="mode">
  <bean:parameter id="mode" name="mode"/>
</logic:present>
<logic:notPresent parameter="mode">
  <bean:define id="mode" value=""/>
</logic:notPresent>

<%
  Collection collection = (Collection) session.getAttribute("collection");
  //A.log("collection-body.jsp collection:" + collection);
  Taxon taxon = (Taxon) session.getAttribute("taxon");
  
  // If page is accessed directly (perhaps by a bot) the specimen will be null.
  Specimen specimen = (Specimen) session.getAttribute("specimen");

  Formatter formatter = new Formatter();
%>

<input id="for_print" type="text" value="AntWeb. Available from: <%= HttpUtil.getFullJspTarget(request) %>">
<input id="for_web" type="text" value="URL: <%= HttpUtil.getFullJspTarget(request) %>">
<br>
<div class="page_contents">
<h1>Collection: <%= collection.getCode() %>	</h1>
</div>

<div class="page_divider"></div>

<div id="page_data">
<div id="overview_data" class="plain">
<ul>
<li>Collection code:</li>
<li><b><bean:write name="collection" property="collectionCode"/></b></li>
</ul>

<% if (Utility.displayEmptyOrNotBlank(collection.getCollectedBy())) { %>
<ul>
<li>Collected by:</li>
<li><b><bean:write name="collection" property="collectedBy"/></b></li>
</ul>
<% } %>


<% if (Utility.displayEmptyOrNotBlank(collection.getDateCollectedStart())) { %>
<ul>
<li>Collection Start Date:</li>
<li><b><bean:write name="collection" property="dateCollectedStart"/></b></li>
</ul>
<% } %>

<% if (Utility.displayEmptyOrNotBlank(collection.getDateCollectedEnd())) { %>   
<ul>
<li>Date collected end:</li>
<li><b><%= formatter.clearNull(collection.getDateCollectedEnd()) %></b></li>
</ul>
<% } %>

<% if (Utility.displayEmptyOrNotBlank(collection.getMethod())) { %>
<ul>
<li>Method:</li>
<li><b><bean:write name="collection" property="method"/></b></li>
</ul>

<% } %>
<% if (Utility.displayEmptyOrNotBlank(collection.getHabitat())) { %>
<ul>
<li>Habitat:</li>
<li><b><bean:write name="collection" property="habitat"/></b></li>
</ul>
<% } %>
<% if (Utility.displayEmptyOrNotBlank(collection.getMicrohabitat())) { %>
<ul>
<li>Microhabitat:</li>
<li><b><bean:write name="collection" property="microhabitat"/></b></li>
</ul>
<% }

   Locality locality = collection.getLocality();
   if (locality == null) {
     AntwebUtil.log("collection-body.jsp collection:" + collection + " has null locality for request:" + request.getQueryString());
     return;     
   }
   if (Utility.displayEmptyOrNotBlank(locality.getLocalityCode())) { %>

<ul>
<li>Bioregion:</li>
  <li><b><%= locality.getBioregion() %></b></li>
</ul>

<ul>
<li>Locality Code:</li>
<li><b><a href="locality.do?code=<bean:write name="collection" property="locality.localityCode"/>">
 <bean:write name="collection" property="locality.localityCode"/></a></b></li>
</ul>
<% } %>
<% if (Utility.displayEmptyOrNotBlank(locality.getLocalityName())) { %>
<ul>
<li>Locality Name:</li>
<li><b><a href="locality.do?name=<bean:write name="collection" property="locality.localityName"/>">
<li><bean:write name="collection" property="locality.localityName"/></a></b></li>
</ul>
<% } %>

<% if (Utility.displayEmptyOrNotBlank(locality.getCountry())) { %>
<ul>
<li>Country:</li>
<%
   String countryLink = "";
   if (locality.getCountry() != null) {
     countryLink = "<a href='" + AntwebProps.getDomainApp() + "/country.do?name=" + locality.getCountry() + "'>" + locality.getCountry() + "</a>";
   } %>
<li><b><%= countryLink %></b></li>
</ul>
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
        <ul>
        <li>Adm1:</li>
        <%
           String adm1Link = "";
           if (locality.getAdm1() != null) {
             adm1Link = "<a href='" + AntwebProps.getDomainApp() + "/adm1.do?name=" + java.net.URLEncoder.encode(locality.getAdm1()) + "&country=" + locality.getCountry() + "'>" + locality.getAdm1() + "</a>";
           } %>
        <li><b><%= adm1Link %></b></li>
        </ul>
        <%
        } %>
        <% if (Utility.displayEmptyOrNotBlank(locality.getAdm2())) { %>
        <ul>
        <li>Adm2:</li>
        <li><b><bean:write name="collection" property="locality.adm2"/></b></li>
        </ul>
        <% }
      } %>

		<% 
		//Locality locality = collection.getLocality();
		
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
		<li><b><%= Utility.notBlankValue(locality.getLatLonMaxError()) %></b></li>
		</ul>
		<% } %>

		<% if (Utility.displayEmptyOrNotBlank(locality.getElevation())) { %>
		<ul>
		<li>Elevation:</li>
		<li><b><%= Utility.notBlankValue(locality.getElevation()) %> <%= (locality.getElevation() != null) ? "m" : "" %></b></li>   
		</ul>
		<% } %>

		<% if (Utility.displayEmptyOrNotBlank(locality.getElevationMaxError())) { %>
		<ul>
		<li>Elevation max error:</li>
		<li><b><%= Utility.notBlankValue(locality.getElevationMaxError()) %></b></li>
		</ul>
		<% } %>

		<% if (Utility.displayEmptyOrNotBlank(locality.getLocalityNotes())) { %>
		<ul>
		<li>Locality notes:</li>
		<li><%= Utility.notBlankValue(locality.getLocalityNotes()) %></li>
		</ul>
		<% } %>



        <% if (Utility.displayEmptyOrNotBlank(collection.getCollectionNotes())) { %>
        <ul>
        <li>Collection notes:</li>
        <li><bean:write name="collection" property="collectionNotes"/></li>
        </ul>
        <% } %>

</div>
<div class="left">

<% 
    Map map = collection.getMap(); 
    String object = "collection";
    String objectName = collection.getCode();
    String mapType = "collection";
%> 
        <div class="small_map">         
            <%@include file="/maps/googleMapPreInclude.jsp" %>  
            <%@include file="/maps/googleMapInclude.jsp" %>  
        </div>

    </div>
</div>
<div class="clear"></div>
<div id="page_data">
<br /><br />
<% if (collection.getResults().size() < 1) { //.getSpecimens() %>

No specimens in this collection.

<% } else if (collection.getResults().size() > 0) { //getSpecimens(). 

     String recordCountString = collection.getResults().size() + " specimens";  

     int speciesCount = 0;
     if (collection.getResults().size() > 0) {
       HashSet speciesSet = new HashSet();
       ArrayList<ResultItem> results = collection.getResults();
       for (ResultItem resultItem : results) {
         speciesSet.add(resultItem.getFullName());
       }
       speciesCount = speciesSet.size();
     }
     if (speciesCount > 0) {
       //A.log("collection-body.jsp speciesCount:" + speciesCount);
       recordCountString += " of " + speciesCount + " species";
     }

     boolean useSpecimenReport = AntwebProps.isDevMode();
     // the following is a workaround for antweb_test...
     boolean reallyUseSpecimenReport = false;
     if (reallyUseSpecimenReport) {
 
       String pageContainer = "collection";
 
       if (request.getParameter("sortBy") != null) {
        collection.sortBy(request.getParameter("sortBy")); 
       }

       session.setAttribute("taxon", collection.getResultsAsTaxon());

       int limit = 1000;
 %>
       <%@include file="/specimenReport.jsp" %> 

 <%  } else { %>

<h3><%= recordCountString %></h3>  <!-- getSpecimens(). -->

<div class="browse_col_one"><span class="col_header">Specimen Code</span></div>
<div class="browse_col_two"><span class="col_header">Name</span></div>
<div class="browse_col_three"><span class="col_header">Type Status</span></div>
<div class="browse_col_four"><span class="col_header">Caste</span></div>
<div class="clear"></div>
<hr></hr>

<logic:equal name="mode" value="map">
<form action="mapResults.do" name="taxaFromSearchForm" method="POST">
<input type="hidden" name="resultRank" value="specimen"/>
</logic:equal>
<logic:equal name="mode" value="compare">
<form action="compareResults.do" name="taxaFromSearchForm" method="POST">
<input type="hidden" name="resultRank" value="specimen"/>
</logic:equal>

<logic:equal name="mode" value="map">
<b>SELECT ALL</b>
<input type="checkbox" name="selectall" onClick="selectAll(document.taxaFromSearchForm);">
<input type="submit" class="submit" value="Map &#187;" align="right"/>
<hr></hr>
</logic:equal>
<logic:equal name="mode" value="compare"> 
<b>SELECT ALL</b>
<input type="checkbox" name="selectall" onClick="selectAll(document.taxaFromSearchForm);">
<input type="submit" class="submit" value="Compare &#187;" align="right"/>
<hr></hr>
</logic:equal>

<logic:iterate id="row" name="collection" property="results" indexId="index">

<%
 String prettyCode = "";
 String prettyName = "";
 String prettyType = "";
 String prettyCaste = "";

 ResultItem resultItem = (ResultItem) row;
 if (resultItem.getCode() != null) prettyCode = resultItem.getCode();
 if (resultItem.getFullName() != null) prettyName = resultItem.getFullName();
 if (resultItem.getType() != null) prettyType = resultItem.getType();
 if (resultItem.getCaste() != null) prettyCaste = resultItem.getCaste();
%>

<logic:notEmpty name="row" property="code">

<logic:equal name="mode" value="map">
<input type="checkbox" name="chosen" value="<bean:write name="index"/>"/>
</logic:equal>
<logic:equal name="mode" value="compare">
<input type="checkbox" name="chosen" value="<bean:write name="index"/>"/>
</logic:equal>

<div class="browse_col_one">
<logic:present name="row" property="images">
<logic:equal name="row" property="images" value="true">
<a href="specimen.do?name=<bean:write name="row" property="code"/>"><img src="image/has_photo.gif" border="0"></a>
</logic:equal>
<logic:notEqual name="row" property="images" value="true">
<img src="image/no_photo.gif">
</logic:notEqual>
</logic:present>

<a href="specimen.do?name=<bean:write name="row" property="code"/>">
<b><%= ((String) prettyCode).toUpperCase() %></b>
</a>
</logic:notEmpty>
</div>

<div class="browse_col_two">
<logic:notEmpty name="row" property="pageParams">
<a href="description.do?<bean:write name="row" property="pageParams"/>">
<%= new Formatter().capitalizeFirstLetter((String) prettyName) %>
</a>
</logic:notEmpty>
</div>

<div class="browse_col_three">
<logic:notEmpty name="row" property="type">
<%= new Formatter().capitalizeFirstLetter((String) prettyType) %>
</logic:notEmpty>
</div>

<div class="browse_col_four">
<logic:notEmpty name="row" property="caste">
<%= new Formatter().capitalizeFirstLetter((String) prettyCaste) %>
</logic:notEmpty>
</div>

<div class=clear></div>
<hr></hr>
</logic:iterate>
  <% } %>

<% } %>

<logic:equal name="mode" value="map">
</form>
</logic:equal>
<logic:equal name="mode" value="compare">
</form>
</logic:equal>

<% 
   String institutionsStr = collection.getInstitutionsStr();
   // example with multiple attributions: /collection.do?name=Ma-D-02-1-01
   
   String amissEmail = collection.getAmissEmail();
 %>
<div class=clear></div>
<br />
<p>Specimen data uploaded by <%= institutionsStr %> | <a href="mailto:<%= amissEmail %>?subject=Regarding AntWeb page <%= the_page %>">See something amiss? Send us an email.</a></p>
</div>
