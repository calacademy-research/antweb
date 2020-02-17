<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.curate.geolocale.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.data.*" %>
<%@ page import="org.calacademy.antweb.data.googleApis.*" %>


<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<%@include file="/curate/curatorCheck.jsp" %>

<%	
	Geolocale geolocale = (Geolocale) request.getAttribute("geolocale");
	
    ArrayList<Geolocale> validChildren = (ArrayList<Geolocale>) request.getAttribute("validChildren");
        
	if (geolocale == null) {
        AntwebUtil.log("editGeolocale-body.jsp geolocale:" + geolocale);
        %> Geolocale Not found.  Back To <a href="<%= AntwebProps.getDomainApp() %>/geolocaleMgr.do">GeoLocale Manager</a><%
	    return;
    }

    EditGeolocaleForm form = (EditGeolocaleForm) request.getAttribute("form");
    
    String georank = geolocale.getGeorank();
    String parentRank = Georank.getParent(georank);
    String parent = geolocale.getParent();
    String childRank = Georank.getChild(georank);
%>

<% // These are necessary for the map to work. Included in layout.jsp elsewhere.
   if (HttpUtil.isOnline()) { %>
<script src="https://ajax.googleapis.com/ajax/libs/jquery/1/jquery.min.js"></script>
<% } %>
<script type="text/javascript" src="<%= AntwebProps.getDomainApp() %>/common/jquery.tools.min.js"></script>

<div class="in_admin">
<h1>Edit Geolocale</h1>

<br>&nbsp;&nbsp;&nbsp; <a href="<%= domainApp %>/geolocaleMgr.do?georank=<%= georank %>&parent=<%= geolocale.getParent() %>#<%= geolocale.getId() %>">Parent List</a>  
  | Public: <%= geolocale.getTag() %>
  <% if ("adm1".equals(georank)) { %>
  | <a href='<%= domainApp %>/adm1Mgr.do?adm1Name=<%= geolocale.getName() %>&countryName=<%= geolocale.getParent() %>'>Adm1 Mgr</a>
  <% } %>
  <% if (childRank != null) { %>
  | <a href='<%= domainApp %>/geolocaleMgr.do?georank=<%= childRank %>&parent=<%= geolocale.getName() %>'>Children List</a>
  <% } %>
</div>


<% 
   //A.log("XXX:" + geolocale + " tag:" + geolocale.getTag());

   String message = (String) request.getAttribute("message");
   if (message != null) out.println("<br><font color=red>" + message + "</font>");
%>

<!-- For UI stuff see the old: ~/dev/calacademy/antweb/web/curate/manageLogin-body.jsp -->

<!-- form action="< %= domainApp % >/editCountry.do" method="POST" -->
<html:form method="GET" action="editGeolocale">
            
<input type="hidden" name="isSubmit" value="true">
            
<%
    String disabled;
 	if (geolocale.isUn()) disabled = "disabled"; else disabled = "";
 	boolean isDisabled = geolocale.isUn(); 	  
    String selected = null;
%>

            <div class="clear"></div>
<br><br>
            
<input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="Submit">            
            
            <div class="clear"></div>
<br><br>

<div class="admin_left">

<h3>Id:</h3>
<input type="text" class="input_200" name="id" value="<%= geolocale.getId() %>" disabled>
<input type="hidden" class="input_200" name="id" value="<%= geolocale.getId() %>">
<br><br>

<h3>Name:</h3>
<input type="text" class="input_200" name="name" value="<%= geolocale.getName() %>">
<br><br>

<% if ("country".equals(georank)) { %>
<h3>ISO Code:</h3>
<input type="text" class="input_200" name="isoCode" value="<%= Formatter.formDisplay(geolocale.getIsoCode()) %>">
<br><br>

<h3>ISO 3 Code:</h3>
<input type="text" class="input_200" name="iso3Code" value="<%= Formatter.formDisplay(geolocale.getIso3Code()) %>">
<br><br>
<% } %>

<h3>Geo Rank:</h3>
  <select name="georank" <%= disabled %>>
	<% 
%> 
  <option value="region"<%= ("region".equals(georank) ? "selected" : "") %>>Region</option>
  <option value="subregion"<%= ("subregion".equals(georank) ? "selected" : "") %>>Subregion</option>
  <option value="country"<%= ("country".equals(georank) ? "selected" : "") %>>Country</option>
  <option value="adm1"<%= ("adm1".equals(georank) ? "selected" : "") %>>adm1</option>
  </select>
<% if (isDisabled) { %><input type="hidden" class="input_200" name="georank" value="<%= georank %>"><% } %>
<br><br>

<h3>Valid:</h3>
Mostly the UN geolocales, with overrides. 
<br><input type="checkbox" name="isValid" <%= (geolocale.getIsValid() == true)?"checked":"" %>>
<br><br>

<h3>Is UN:</h3>
<input type="checkbox" name="isUn" <%= (geolocale.isUn() == true)?"checked":"" %> disabled>
<input type="hidden" class="input_200" name="isUn" value="<%= (geolocale.isUn())?"true":"false" %>">
<br><br>

<h3>Source:</h3>
<%= geolocale.getSource() %>
<input type="hidden" class="input_200" name="source" value="<%= geolocale.getSource() %>">
<br><br>

<% //if (AntwebProps.isDevMode()) AntwebUtil.log("editGeolocale-body.jsp georank:" + georank); %>

<h3>Valid Name:</h3>
<% if (!geolocale.getIsValid()) { %>

<%@include file="/curate/geolocale/validSelect.jsp" %>
  
<%   

    if (isOther) out.println("&nbsp;&nbsp;(Other:&nbsp;" + geolocale.getValidName() + ")");
 } else { %>
 <%= "N/A" %>
<%
 } %>    
<br><br>


<% if (!"region".equals(georank)) { %>
<h3><%= Georank.getParentHeading(georank) %></h3>
  <select name="parent">  <!-- %= disabled % -->
	<% 
       selected = "";
       if (geolocale.getParent() == null) selected = " selected"; else selected = "";
%>    <option value="none"<%= selected %>>None</option> <% 
       for (Geolocale validGeolocale : GeolocaleMgr.getGeolocales(Georank.getParent(georank), true)) {
	     if (validGeolocale.getName().equals(geolocale.getParent())) selected = "selected"; else selected = "";
 	 %>
      <option value='<%= validGeolocale %>' <%= selected %>><%= validGeolocale %></option>
	<% } %>	
  </select>
  
<br><br>
<% } %>

<h3>Bioregion:</h3>
  <select name="bioregion">  <!-- %= disabled % -->
	<%
       selected = "";
       if (geolocale.getBioregion() == null) selected = " selected"; else selected = "";
%>    <option value="none"<%= selected %>>None</option> <%
       for (Bioregion bioregion : BioregionMgr.getBioregions()) {
	     if (bioregion.getName().equals(geolocale.getBioregion())) selected = "selected"; else selected = "";
 	 %>
      <option value='<%= bioregion.getName() %>' <%= selected %>><%= bioregion.getName() %></option>
	<% } %>	
  </select>

<br><br>

<% // if (AntwebProps.isDevMode()) { %> 

<b>Alternate Bioregion:<b>
  <select name="altBioregion">  <!-- %= disabled % -->
	<%
       selected = "";
       if (geolocale.getAltBioregion() == null) selected = " selected"; else selected = "";
%>    <option value="none"<%= selected %>>None</option> <%
       for (Bioregion bioregion : BioregionMgr.getBioregions()) {
	     if (bioregion.getName().equals(geolocale.getAltBioregion())) selected = "selected"; else selected = "";
 	 %>
      <option value='<%= bioregion.getName() %>' <%= selected %>><%= bioregion.getName() %></option>
	<% } %>
  </select>
<br><br>
<% // } %>

<h3>Live:</h3>
<input type="checkbox" name="isLive" <%= (geolocale.getIsLive() == true)?"checked":"" %>>
<br><br>


<H3>Geodata Fetch Methods</H3>


<H4>Flickr Woe ID:</H4>
&nbsp;&nbsp;&nbsp;WoeId is the Flickr primary key. May not be modified (except by automated data fetchs).<br>
&nbsp;&nbsp;&nbsp;It is inferred from <%= FlickrPlace.getPlaceInfoTag(geolocale) %>
<%
String woeIdLink = "";
if (geolocale.getWoeId() != null && !("null".equals(geolocale.getWoeId()))) {
  woeIdLink = "<a href='https://www.flickr.com/places/info/" + geolocale.getWoeId() + "'>" + geolocale.getWoeId() + "</a>";
  out.println("<br>&nbsp;&nbsp;&nbsp;<b>Woe Id:</b> " + woeIdLink);
  String deleteLink = "&nbsp;&nbsp;&nbsp;<a href='" + domainApp + "/editGeolocale.do?id=" + geolocale.getId() + "&action=removeFlickrData'><img src='" + domainApp + "/image/redX.png' width='10'/></a>";
  out.println(deleteLink);
} else { %>
<br><input type="text" class="input_50" name="woeId" value="">
<%
}
%>
<br><br>

<h4>Geonames</h4>
<% if ("country".equals(georank)) { %>
<%= GeonamesPlace.getCountryTag(geolocale) %> (Used to select the geonameId to use to fetch the children).
<br><%= GeonamesPlace.getChildrenTag(geolocale) %> (These are the adm1 (children) for the selected goenameId).
<% } %>

&nbsp;&nbsp;&nbsp;<%= GeonamesPlace.getGeonamePageTag(geolocale) %>

<% if (false && "country".equals(georank)) { %>
<br><a href='<%= AntwebProps.getDomainApp() %>/editGeolocale.do?id=<%= geolocale.getId() %>&action=removeGeoData'>Remove</a> Geo Data.
<br><a href='<%= AntwebProps.getDomainApp() %>/editGeolocale.do?id=<%= geolocale.getId() %>&action=fetchGeoData'>Fetch</a> Geo Data.
<br>&nbsp;&nbsp;&nbsp;<a href='<%= AntwebProps.getDomainApp() %>/editGeolocale.do?id=<%= geolocale.getId() %>&action=fetchFlickrCountryData'>Fetch</a> Flickr Country Data.
<br>&nbsp;&nbsp;&nbsp;<a href='<%= AntwebProps.getDomainApp() %>/editGeolocale.do?id=<%= geolocale.getId() %>&action=fetchGeonamesData'>Fetch</a> Geonames Data.
<br>&nbsp;&nbsp;&nbsp;<a href='<%= AntwebProps.getDomainApp() %>/editGeolocale.do?id=<%= geolocale.getId() %>&action=fetchFlickrData'>Fetch</a> Flickr Data.
<% } %>

<br><br>

<h4>Google Apis</h4>
<%
   if ("country".equals(georank)) { 
	 String googleApisLink = GoogleApisMgr.getFetchCountryUrl(geolocale);
	 googleApisLink = "<a href='" + googleApisLink + "'>Link</a>";
 	 out.println("&nbsp;&nbsp;&nbsp;<b>GoogleApis:</b> " + googleApisLink);

     String georankTypeVal = Utility.notBlankValue(geolocale.getGeorankType());
%>
<br>&nbsp;&nbsp;&nbsp;<b>Georank Type:</b> <input type="text" class="input_120" name="georankType" value="<%= georankTypeVal %>">
<br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;This property set will adjust the <b>children Adm1</b> and be used in their Google Api fetches.

<% }
   if ("adm1".equals(geolocale.getGeorank())) {
	String googleApisLink = GoogleApisMgr.getFetchAdm1Url(geolocale);
	googleApisLink = "<a href='" + googleApisLink + "'>Link</a>";

    String loadGoogleApisLink = "<a href='" + AntwebProps.getDomainApp() + "/utilData.do?action=fetchGoogleApisData&id=" + geolocale.getId() + "'>load</a>";

	out.println("&nbsp;&nbsp;&nbsp;<b>GoogleApis:</b> " + googleApisLink);
	out.println("<br>&nbsp;&nbsp;&nbsp;<b>Set bounds and centroid:</b> " + loadGoogleApisLink + " (caution:irreversible)");
	%>

<br>&nbsp;&nbsp;&nbsp;<b>Georank Type:</b> <input type="text" class="input_120" name="georankType" value="<%= geolocale.getGeorankType() %>">
<br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;This property will be used in Google Api fetches. For Adm1, usually it will be Province.
	
<% } %>
<br><br>

<H3>Centroid:</H3>
Lat, Lon.  Data from Flickr. May not be modified.<br>
<% //String centroid = geolocale.getLatitude() + ", " + geolocale.getLongitude();
   String centroid = geolocale.getCentroid();
   if (centroid == null || "null".equals(centroid)) centroid = "";
%>
<input type="text" class="input_20" name="centroid" value="<%= centroid %>" disabled>
<br><br>

<H3>Centroid (Fixed):</H3>
This Lat, Lon will override the Centroid (data from Flickr).<br>
<% //String centroidFixed = geolocale.getLatitudeFixed() + ", " + geolocale.getLongitudeFixed();
   String centroidFixed = geolocale.getCentroidFixed();
   //if (geolocale.getLatitudeFixed() == null || geolocale.getLongitudeFixed() == null) centroidFixed = "";
   if (geolocale.getCentroidFixed() == null) centroidFixed = "";
%>
<input type="text" class="input_20" name="centroidFixed" value="<%= centroidFixed %>">
<br><br>

<% String displayUseCentroid = geolocale.useCentroid();
   if (displayUseCentroid == null) displayUseCentroid = ""; 
   //A.log("editGeolocale-body.jsp displayUse:" + displayUseCentroid);
   %>
&nbsp;&nbsp;&nbsp;<b>Use Centroid:</b>&nbsp;<%= displayUseCentroid %>
<br>&nbsp;&nbsp;&nbsp;(A Fixed value will override the original value, if it exists)
<br><br>

<H3>Bounding Box:</H3>
Left (West Longitude), Bottom (South Latitude), Right (East Longitude), Top (North Latitude).  Data from Flickr. May not be modified.<br>
<% String boundingBox = geolocale.getBoundingBox();
   if (boundingBox == null || "null".equals(boundingBox)) boundingBox = "";
%>
<input type="text" class="input_200" name="boundingBox" value="<%= boundingBox %>" disabled>
<br><br>

<H3>Bounding Box (Fixed):</H3>
Left (West Longitude), Bottom (South Latitude), Right (East Longitude), Top (North Latitude). 
&nbsp;&nbsp;&nbsp;(This will override the Bounding Box).<br>
Look up bounding box: <a href='https://www.mapdevelopers.com/geocode_bounding_box.php'>Geocode Bounding Box</a>
<% String boundingBoxFixed = geolocale.getBoundingBoxFixed();
   if (boundingBoxFixed == null || "null".equals(boundingBoxFixed)) boundingBoxFixed = "";
%>
<input type="text" class="input_550" name="boundingBoxFixed" value="<%= boundingBoxFixed %>">
<br>

<% String displayUseBB = geolocale.useBoundingBox();
   if (displayUseBB == null) displayUseBB = ""; 
   //A.log("editGeolocale-body.jsp displayUse:" + displayUseBB);
   %>
<br>&nbsp;&nbsp;&nbsp;<b>Use Bounding Box:</b>&nbsp;<%= displayUseBB %>
<br>&nbsp;&nbsp;&nbsp;(A Fixed value will override the original value, if it exists)
<br>
<br>
 
<h3>Map:</h3>


<% 
/*
// To be added. This will support V3. Not successfully debugged yet.

      LocalityOverview localityOverview = (LocalityOverview) geolocale;
      Map map = localityOverview.getMap();
      String mapType = "geolocale";
      String object = "locality";
      Overview overview = null;
      String objectName = null;
 % >

        <div class="left">
          <div class="small_map">
            < %@include file="/maps/googleMapPreInclude.jsp" % >  
            < %@include file="/maps/googleMapInclude.jsp" % >  
          </div>
        </div>
        
        
        
// Was here. Bad map. Old code. To be removed. See functioning at V7.83.2 or earlier. 
// Once removed we can remove (or deprecated map.jsp, googleMap.jsp and includeMap.jsp.
// See documentation in googleMapInclude.jsp.

< %@include file="/maps/includeMap.jsp" % >

      LocalityOverview localityOverview = (LocalityOverview) overview;
      Map map = localityOverview.getMap();

      // causes npe on server...
      //if (overview != null) A.log("overview-body.jsp 2 overview:" + overview);
      //A.log("overview-body.jsp 3 map:" + map);
      if (map != null) {
        //if (map.getGoogleMapFunction() != null) A.log("overview-body.jsp len:" + map.getGoogleMapFunction().length());

        String object = "localityOverview";
        if ("overview".equals(mapType)) {
          // Then we failed to assign the specific mapType.
          AntwebUtil.log("overview-body.jsp overview:" + mapType + " name:" + overview.getName());
        }
        //objectName is set above	
    % >

*/
%>

<br><br>

<H3>Admin Notes:</H3>
<% String adminNotes = geolocale.getAdminNotes();
   if (adminNotes == null || "null".equals(adminNotes)) adminNotes = "";
%>
<textarea rows="4" cols="50" name="adminNotes" id="editor1" class="biotextarea550"><%= adminNotes %></textarea>
<br>


<h3>&nbsp;<h3>
<h3>Use Parent Region:</h3>
For some Geolocales it is best to display the region.<br>
<input type="checkbox" name="isUseParentRegion" <%= (geolocale.getIsUseParentRegion() == true)?"checked":"" %>>
<br><br>

<h3>&nbsp;<h3>
<h3>Is Island:</h3>
Some countries are treated as countries (like Hawaii) to support our bioregion data.<br>
<input type="checkbox" name="isIsland" <%= (geolocale.getIsIsland() == true)?"checked":"" %>>
<br><br>

<h3>Locality:</h3>
<% String locality = geolocale.getLocality();
   if (locality == null || "null".equals(locality)) locality = "";
%><%= locality %>
<br><br>

<h3>Use Children:</h3>
To indicate that adm1 should be used, for instance, United States, Brazil, China and Australia.<br>
<input type="checkbox" name="isUseChildren" <%= (geolocale.getIsUseChildren() == true)?"checked":"" %>>
<br><br>

<h3>Created:</h3>
<input type="text" class="input_120" name="created" value="<%= geolocale.getCreated() %>" disabled>
<br><br>



<h3>Author:</h3>
<input name="author" type=text class=input_550 value="<%= Formatter.formDisplay(geolocale.getAuthor()) %>"> 
<br><br>
<p>


</div>
            <div class="clear"></div>

			<br>Fast:<input type="checkbox" name="isFast" <%= (form.getIsFast() == true)?"checked":"" %>> If fast, will not reload GeolocaleMgr. To manually reload, click  <a href='<%= AntwebProps.getDomainApp() %>/util.do?action=reloadAntwebMgr'>GeolocaleMgr</a><br><br>
			<input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="Submit">
            
            <div class="align_left">
            <br><br>< Back To <a href="<%= AntwebProps.getDomainApp() %>/geolocaleMgr.do?georank=<%= georank %>#<%= geolocale.getId() %>">Geo Locale Manager</a>

            | <a href="<%= AntwebProps.getDomainApp() %>/editGeolocale.do?id=<%= geolocale.getId() %>&isDelete=true">Delete</a>
            </div>

            <div class="clear"></div>
            


<!-- /form -->
</html:form>