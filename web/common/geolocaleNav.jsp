<%-- Unused as of 2026-10-30: a "GeolocaleNav View:" link to the place's parent (it leaves divs open). No JSP
     includes it (common/siteNav.jsp includes regionNav, subregionNav, countryNav and adm1Nav), and Caddy answers a
     direct request with 404 (not on the JSP allow list in docker/caddy/Caddyfile). --%>

<div id="page_context">
    <div id="view">
        <div id="current_view">GeolocaleNav View: 
<%
Geolocale geolocale = (Geolocale) overview;
Geolocale parent = GeolocaleMgr.getGeolocale(geolocale.getParent());

out.println("<a href='" + parent.getThisPageTarget() + "'>" + parent.getName() + "</a> - " + overview.getDisplayName());
%>
        </div>
