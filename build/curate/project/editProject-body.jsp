<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.home.*" %>

<%  String domainApp = (new Utility()).getDomainApp(); %>
    
<% Project thisProject = (Project) request.getAttribute("thisProject"); 
%>

<%@include file="/curate/adminCheck.jsp" %>

<div class="msg in_admin">
    <div class="msg_alert"></div>
    <div class="msg_type">NOTE</div>
    <div class="msg_pipe"></div>
    <div class="msg_copy">
You may use HTML in your page copy.
<b>Images must be already on the AntWeb server.</b> <br />
To Upload A File, go to the <a href="<%= domainApp %>/curate.do" target="new">Antweb Curator Tools</a>

    </div>
    <div class="clear"></div>
</div>

<div class=admin_left>
<h1>Editing <%= thisProject.getTitle() %> Project</h1>

<html:messages id="message" message="true">
<font color="red"><b>
<bean:write name="message"/><br>
</b></font>
</html:messages>

<hr></hr>

<form method="post" action="<%= domainApp %>/editProject.do?projectName=<%= thisProject.getName() %>">

<h3>Key: <%= thisProject.getName() %></h3>
<p>

<h3>Display Key:</h3>
<input name="displayKey" type=text class=input_550 value="<%= Formatter.formDisplay(thisProject.getDisplayKey()) %>">
<p>

<h3>Title:</h3>
<input name="title" type=text class=input_550 value="<%= thisProject.getTitle() %>">
<p>

<h3>Author:</h3>
<input name="author" type=text class=input_550 value="<%= Formatter.formDisplay(thisProject.getAuthor()) %>"> 
<p>
<h3>Page Copy:</h3>
<textarea name="contents" class=biotextarea550>
<%= Formatter.formDisplay(thisProject.getContents()) %>
</textarea>

<p><h3>Images:</h3>
<b>Image One:</b><br>
<input name="specimenImage1" type=text class=input_550 value="<%= Formatter.formDisplay(thisProject.getSpecimenImage1()) %>"><br>
<b>Image One Links to:</b><br>
<input name="specimenImage1Link" type=text class=input_550 value="<%= Formatter.formDisplay(thisProject.getSpecimenImage1Link()) %>"><br>

<p><b>Image Two:</b><br>
<input name="specimenImage2" type=text class=input_550 value="<%= Formatter.formDisplay(thisProject.getSpecimenImage2()) %>"><br>
<b>Image Two Links to:</b><br>
<input name="specimenImage2Link" type=text class=input_550 value="<%= Formatter.formDisplay(thisProject.getSpecimenImage2Link()) %>"><br>

<p><b>Image Three:</b><br>
<input name="specimenImage3" type=text class=input_550 value="<%= Formatter.formDisplay(thisProject.getSpecimenImage3()) %>"><br>
<b>Image Three Links to:</b><br>
<input name="specimenImage3Link" type=text class=input_550 value="<%= Formatter.formDisplay(thisProject.getSpecimenImage3Link()) %>"><br>

<p><h3>Author Image:</h3>
<input name="authorImage" type=text class=input_550 value="<%= Formatter.formDisplay(thisProject.getAuthorImage()) %>"><br>

<p><h3>Author Bio:</h3>
<input name="authorBio" type=text class=input_550 value="<%= Formatter.formDisplay(thisProject.getAuthorBio()) %>"><br>
</textarea>

<p><h3>Map (Image):</h3>
<input name="map" type=text class=input_550 value="<%= thisProject.getMap() %>"><br> <!-- Formatter.formDisplay(thisProject.getMap()) -->

<% if (thisProject.getMap() != null && !"".equals(thisProject.getMap())) { %>

        <div id="static_map" class="slide medium last"><img src="<%= AntwebProps.getImgDomainApp() %>/<%= Project.getSpeciesListDir() %><%= thisProject.getRoot() %>/<%= thisProject.getMap() %>"></div>
<% } %>

<br><br>


<h3>Map (Coords & Extent):</h3>
  (Modify in the associated geolocale).
  <br>

<% String googleMapKey = AntwebProps.getGoogleMapKey(); %>

<script src="http://ajax.googleapis.com/ajax/libs/jquery/1/jquery.min.js"></script>
<script type="text/javascript" src="https://www.google.com/jsapi"></script>
<script type="text/javascript" src="http://maps.googleapis.com/maps/api/js?v=3&key=<%= googleMapKey %>&sensor=false"></script>
<%
    String got_coords = thisProject.getCoords();
    
  if (HttpUtil.isOnline()) {        
    if (got_coords == null) { 
%>
<script>
  var geocoder;
  var map;
  function makeMap() {
    geocoder = new google.maps.Geocoder();
    var address = '<%= thisProject.getTitle() %>';
    geocoder.geocode( { 'address': address}, function(results, status) {
      if (status == google.maps.GeocoderStatus.OK) {
        $("#coords").val(results[0].geometry.location);
        var mapOptions = {
          zoom: 5,
          disableDefaultUI: true,
          mapTypeId: google.maps.MapTypeId.ROADMAP
        }
        map = new google.maps.Map(document.getElementById('map-canvas'), mapOptions);
        map.setCenter(results[0].geometry.location);
      } else {
        alert('Geocode was not successful for the following reason (1): ' + status);
      }
    });
  }

  function codeLocation() {
    var lat = $('#new_lat').val();
    var lng = $('#new_lng').val();
    var latlng = new google.maps.LatLng(lat, lng);
    geocoder.geocode( { 'location': latlng}, function(results, status) {
      if (status == google.maps.GeocoderStatus.OK) {
        $("#coords").val(results[0].geometry.location);
        map.setCenter(results[0].geometry.location);
      } else {
        alert('Geocode was not successful for the following reason(2): ' + status);
      }
    });
  }

  google.maps.event.addDomListener(window, 'load', makeMap);
  
</script>

<% 
    } else {  // if coords == null  
%>

<script>

  var geocoder;
  var map;

  var coords = $("#coords").val();
  function makeMap() {
    geocoder = new google.maps.Geocoder();
    var stripParen = coords.replace(/[\(\)\s]/g, "");
    var str = stripParen;
    var substr = str.split(',');
    var latlng = new google.maps.LatLng(substr[0],substr[1]);
    geocoder.geocode( { 'location': latlng}, function(results, status) {
      if (status == google.maps.GeocoderStatus.OK) {
        $("#coords").val(results[0].geometry.location);
        var mapOptions = {
          zoom: 5,
          disableDefaultUI: true,
          mapTypeId: google.maps.MapTypeId.ROADMAP
        }
        map = new google.maps.Map(document.getElementById('map-canvas'), mapOptions);
        map.setCenter(results[0].geometry.location);
      } else {
        alert('Geocode was not successful for the following reason(3): ' + status);
      }
    });
  }

  function codeLocation() {
    var lat = $('#new_lat').val();
    var lng = $('#new_lng').val();
    var latlng = new google.maps.LatLng(lat, lng);
    geocoder.geocode( { 'location': latlng}, function(results, status) {
      if (status == google.maps.GeocoderStatus.OK) {
        $("#coords").val(results[0].geometry.location);
        map.setCenter(results[0].geometry.location);
      } else {
        alert('Geocode (' + lat + ', ' + lng + ') was not successful for the following reason(4): ' + status);
      }
    });
  }

  google.maps.event.addDomListener(window, 'load', makeMap);

</script>

<% 
    } // if coords == null
  } // if isOnline 
%>


<div id="map-canvas" style="width:262px; height:262px;"></div>


<% 
  String disabled = "";
  String selected = "";
%>


<h3>Is Live:</h3>
Will be listed in the Region hierarchy.<br>
<input type="checkbox" name="isLive" <%= (thisProject.getIsLive() == true)?"checked":"" %>>
<br><br>

<h3>Source:</h3>
<%= thisProject.getSource() %>
<br><br>

<p><h3>Geocoding Coordinates for Homepage 	:</h3>
<input id="coords" type="text" name="coords" value="<%= thisProject.getCoords() %>"><br />
These are automatically generated when the map is created by geocoding, or when you change the center latitude and center longtitude and click "Change Coordinates".

<p>If you want to adjust your homepage map, simply tinker with the center latitude and center longitude in the two fields below, and click "Change Coordinates".<br />
<b>Center Latitude:</b> <input id="new_lat" type="text" size="10"> <b>Center Longitude:</b> <input id="new_lng" type="text" size="10"> <input type="button" value="Change Coordinates" onclick="codeLocation()"></p>

<p><h3>Geographic Extent:</h3>
<input id="extent" name="extent" type=text class=input_550 value="<%= thisProject.getExtent() %>"><br>
Needed to do the maps for region field guides. Separated by spaces. Must not contain any commas.

<p><h3>Locality (Mapping Range Criteria):</h3>
<%
String locValue = thisProject.getLocality();
if (locValue == null || "null".equals(locValue)) locValue = "";
%>
<input name="locality" type=text class=input_550 value="<%= locValue %>"><br>
For examples: biogeographicregion='Afrotropical' (or Australasia, Indomalaya, Malagasy, Nearctic, Neotropical, Oceania, Palearctic)	 &nbsp;&nbsp;&nbsp; or &nbsp;&nbsp;&nbsp; country='Peru' &nbsp;&nbsp;&nbsp; or &nbsp;&nbsp;&nbsp; adm1='California'

<p><h3>Map Image:</h3>
<input name="map" type=text class=input_550 value="<%= thisProject.getMap() %>"><br>
<br><br>



<!--
<br>
<h3>Bioregion Project:</h3>
  <select name="bioregion" < %= disabled % >>
	< % 
       selected = "";
       if (thisProject.getBioregion() == null) selected = " selected"; else selected = "";
% >    <option value="none"< %= selected % >>None</option> < % 
       for (String bioregionName : BioregionMgr.getBioregionNames()) {
       if (bioregionName != null)
	     if (bioregionName.equals(thisProject.getName())) selected = "selected"; else selected = "";
 	 % >
      <option value='< %= bioregionName % >' < %= selected % >>< %= bioregionName % ></option>
	< % } % >	
  </select>  
<br><br>
-->

<!--
<p><h3>Geo Locale:<h3>
<input name="geolocaleId" type=text class=input_550 value="< %= thisProject.getGeolocaleId() % >"><br>
< % if (thisProject.getGeolocaleId() != 0) { % >
<a href="< %= AntwebProps.getDomainApp() % >/editGeolocale.do?id=< %= thisProject.getGeolocaleId() % >">Edit</a>
< % } else { % >
<br><br>

< % } % >

-->



<p>
<h3>Species List Mapping:</h3> 
(This project may be modified via the Species List Mapping Tool):
<select name="speciesListMappable" id="SpeciesList_select">
  <option id="speciesListMappable" value="true" <%= (thisProject.getSpeciesListMappable() ? "selected" : "") %>>true</option>  
  <option id="speciesListMappable" value="false" <%= (!thisProject.getSpeciesListMappable() ? "selected" : "") %>>false</option>  
</select> 
 
<div class="clear"></div>
<br />
<br />
<div class="msg in_admin">
    <div class="msg_actions" align="center">
<input border="0" type="image" src="<%= domainApp %>/image/orange_done.gif" width="137" height="36" value="Preview">
<a href="<%= domainApp %>/curate.do"><img border=0" src="<%= domainApp %>/image/grey_cancel.gif" width="123" height="36"></a>
</form>
    </div>
</div>

</form>

</div>
<div class=admiin_right>


<% if (LoginMgr.isAdmin(request)) { %>  
<a href="<%= domainApp %>/editProject.do?action=delete&projectName=<%= thisProject.getName() %>">Delete</a>
<% } %>

</div> 
