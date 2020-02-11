<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import = "org.calacademy.antweb.util.*" %>

<%
/*
googleMap.jsp is used for Overview display (country.do, adm1.do). It can be set by bounds.
See overview-body.jsp for where it is included.
*/
%>

<script type="text/javascript" src="https://www.google.com/jsapi"></script>
<script type="text/javascript" src="https://maps.googleapis.com/maps/api/js?v=3&key=<%= AntwebProps.getGoogleMapKey() %>"></script>
<!-- was with: &sensor=false -->
<script>
var geocoder;
var map;
var coords = $("#coords_for_geo").val();
var zoom_level = $("#zoom_level").val();

//alert("googleMap coords:" + coords + " zoom_level:" + zoom_level);

var west = $("#west").val();
var south = $("#south").val();
var east = $("#east").val();
var north = $("#north").val();

function makeMap() {
  geocoder = new google.maps.Geocoder();
  var stripParen = coords.replace(/[\(\)\s]/g, "");
  var str = stripParen;
  var substr = str.split(',');
  var latLng = new google.maps.LatLng(substr[0],substr[1]);
/*
 The coords are used just to make sure the google.maps.GeocoderStatus is OK. But there
are issues. For some of the islands the logitude needs to be 43 (like Comoros) in order
to pass. Makes no sense. Perhaps we should remove the coords entirely and not check
status.
*/  
  //latLng = new google.maps.LatLng(-11.577506, 47.297277500000064);  // The Grand Glorious latLng doesn't work.
  //latLng = new google.maps.LatLng(-11.651, 43.35900000000004);  // The Comoros longitude does work
  //latLng = new google.maps.LatLng(-11.651, 43);  // For testing.
  geocoder.geocode( { 'location': latLng}
    , function(results, status) {
        var message = "googleMap results:" + results + " status:" + status + " coords:" + coords
           + " w:" + west + " s:" + south + " e:" + east + " n:" + north + " zoom:" + zoom_level;
        console.log("googleMap.jsp message:" + message);
                  
		if (status == google.maps.GeocoderStatus.OK) {

		  var mapOptions = {
			zoom: parseInt(zoom_level),
			navigationControl: true,
			navigationControlOptions: {style: google.maps.NavigationControlStyle.SMALL},
			mapTypeControl: false,
			streetViewControl: false,
			mapTypeId: google.maps.MapTypeId.TERRAIN
		  }      
		  //alert("mapOptions:" + mapOptions);

		  map = new google.maps.Map(document.getElementById('map-canvas'), mapOptions);
		  map.setCenter(results[0].geometry.location);

		  // If zoom is set (in overview-body.jsp then it will override the bounds.
		  // This is useful for united states and Russia, so large that the maps are vague.
		  if (zoom_level <= 0) {
			var bounds = new google.maps.LatLngBounds();
			var point;
			point = new google.maps.LatLng(north, west);
			bounds.extend(point);
			point = new google.maps.LatLng(south, east);
			bounds.extend(point);
			map.fitBounds(bounds);  
		  }
		} else {
  		  console.log("googleMap.jsp message google.maps.GeocoderStatus is NOT OK");
          /*  
            This is the alert that displays the failure of Kosovo requests.
            Also: 
              https://www.antweb.org/country.do?name=Kosovo
              https://www.antweb.org/country.do?name=French%20Southern%20Territories
              https://www.antweb.org/country.do?name=Grande+Glorieuse
              https://www.antweb.org/country.do?name=Juan+de+Nova+Island
          */
          //alert(message);  
		
		  $('.small_map').hide();
		  if ($('#static_map img').length>0) {
			  $('#static_map').show();
		  }
		}
  });
}

function forceIntoArray(theItem) {
	if (!(theItem instanceof Array)) {
		var temp = theItem;
		theItem = new Array();
		theItem.push(temp);
	}
  return theItem;
}
    
google.maps.event.addDomListener(window, 'load', makeMap);
</script>

