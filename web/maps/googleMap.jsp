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

<%-- The small overview map, drawn with Leaflet and OpenStreetMap (Google Maps retired October 2026). --%>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
window.addEventListener('load', function () {
  var val = function (id) { var e = document.getElementById(id); return e ? e.value : ''; };
  var el = document.getElementById('map-canvas');
  if (!el || typeof L === 'undefined') return;
  var parts = val('coords_for_geo').replace(/[()\s]/g, '').split(',');
  var lat = parseFloat(parts[0]), lng = parseFloat(parts[1]);
  var zoom = parseInt(val('zoom_level'), 10) || 0;
  var w = parseFloat(val('west')), s = parseFloat(val('south')), e = parseFloat(val('east')), n = parseFloat(val('north'));
  if (isNaN(lat) || isNaN(lng)) { el.style.display = 'none'; return; }
  var map = L.map(el, { scrollWheelZoom: false, attributionControl: true });
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 18, attribution: '&copy; OpenStreetMap contributors'
  }).addTo(map);
  if (zoom <= 0 && !isNaN(w) && !isNaN(s) && !isNaN(e) && !isNaN(n)) {
    map.fitBounds([[s, w], [n, e]]);
  } else {
    map.setView([lat, lng], zoom > 0 ? zoom : 5);
  }
});
</script>
