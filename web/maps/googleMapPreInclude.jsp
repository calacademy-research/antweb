<%-- /web/maps/googleMapPreInclude.jsp (Leaflet) --%>
<%
  // Point this to YOUR existing microservice
  final String MAPS_BASE = "http://localhost:8081";
%>
<% if (!HttpUtil.isBot(request)) { %>
<!-- Leaflet + MarkerCluster from CDN -->
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet.markercluster@1.5.3/dist/MarkerCluster.css"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet.markercluster@1.5.3/dist/MarkerCluster.Default.css"/>

<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script src="https://unpkg.com/leaflet.markercluster@1.5.3/dist/leaflet.markercluster.js"></script>

<!-- Your microservice SDK -->
<script src="/maps/sdk/drawMap.js"></script>

<!-- AntWeb adapter that keeps drawGoogleMap* function names -->
<script src="<%= AntwebProps.getDomainApp() %>/maps/drawGoogleMap.js" type="text/javascript"></script>
<% } %>
