

<div id="page_context">
    <div id="view">
        <div id="current_view">GeolocaleNav View: 
<%
Geolocale geolocale = (Geolocale) overview;
Geolocale parent = GeolocaleMgr.getGeolocale(geolocale.getParent());

out.println("<a href='" + parent.getThisPageTarget() + "'>" + parent.getName() + "</a> - " + overview.getDisplayName());
%>
        </div>
