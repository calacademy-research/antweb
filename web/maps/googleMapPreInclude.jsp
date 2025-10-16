<%-- /web/maps/googleMapPreInclude.jsp (Local Integrated Leaflet Setup) --%>
<% if (!HttpUtil.isBot(request)) { %>

<!-- Leaflet core and plugins -->
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet.markercluster@1.5.3/dist/MarkerCluster.css"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet.markercluster@1.5.3/dist/MarkerCluster.Default.css"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet.fullscreen@1.6.0/Control.FullScreen.css"/>

<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script src="https://unpkg.com/leaflet.markercluster@1.5.3/dist/leaflet.markercluster.js"></script>
<script src="https://unpkg.com/leaflet.fullscreen@1.6.0/Control.FullScreen.js"></script>

<!-- Local SDK & adapter -->
<script src="/maps/sdk/drawMap.js" type="text/javascript"></script>
<script src="/maps/drawGoogleMap.js" type="text/javascript"></script>

<% } %>
