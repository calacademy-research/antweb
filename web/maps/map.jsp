<%-- Unused as of 2026-10-30: a stand-alone Google Maps V3 demonstration page (a map centred on -34.397, 150.644, key
     AntwebProps.getGoogleMapKey()). No JSP includes it and nothing links it; Caddy answers a direct request with 404
     (not on the JSP allow list in docker/caddy/Caddyfile). The site's maps use Leaflet (maps/googleMapInclude.jsp,
     maps/googleMap.jsp). --%><%@ page import="org.calacademy.antweb.util.*" %>

<%
    // This map is just to demonstrate GoogleMap V3 functionality

    String googleMapKey = AntwebProps.getGoogleMapKey();
%>


<!DOCTYPE html>
<html>
  <head>
    <meta name="viewport" content="initial-scale=1.0, user-scalable=no" />
    <style type="text/css">
      html { height: 100% }
      body { height: 100%; margin: 0; padding: 0 }
      #map-canvas { height: 100% }
    </style>
    <script type="text/javascript"
      src="https://maps.googleapis.com/maps/api/js?key=<%= googleMapKey %>&sensor=true">
    </script>
    <script type="text/javascript">
      function initialize() {
        var mapOptions = {
          center: new google.maps.LatLng(-34.397, 150.644),
          zoom: 8,
          mapTypeId: google.maps.MapTypeId.ROADMAP
        };
        var map = new google.maps.Map(document.getElementById("map-canvas"),
            mapOptions);
      }
      google.maps.event.addDomListener(window, 'load', initialize);
    </script>
  </head>
  <body>
    <div id="map-canvas"/>
  </body>
</html>