<%-- /web/maps/googleMapPreInclude.jsp (Leaflet version with fullscreen) --%>
<%
  // Map microservice base
  final String MAPS_BASE = "http://104.61.194.241/:8081";
%>
<% if (!HttpUtil.isBot(request)) { %>
<!-- Leaflet core -->
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>

<!-- MarkerCluster -->
<link rel="stylesheet" href="https://unpkg.com/leaflet.markercluster@1.5.3/dist/MarkerCluster.css"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet.markercluster@1.5.3/dist/MarkerCluster.Default.css"/>
<script src="https://unpkg.com/leaflet.markercluster@1.5.3/dist/leaflet.markercluster.js"></script>

<!-- Fullscreen plugin -->
<link rel="stylesheet" href="https://unpkg.com/leaflet.fullscreen@2.4.0/Control.FullScreen.css"/>
<script src="https://unpkg.com/leaflet.fullscreen@2.4.0/Control.FullScreen.js"></script>

<!-- Microservice SDK -->
<script src="<%= MAPS_BASE %>/sdk/config.js"></script>
<script src="<%= MAPS_BASE %>/sdk/drawMap.js"></script>

<!-- AntWeb adapter (keeps drawGoogleMap* function names) -->
<script src="<%= AntwebProps.getDomainApp() %>/maps/drawGoogleMap.js"></script>
<% } %>
