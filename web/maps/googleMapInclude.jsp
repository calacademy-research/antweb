<%-- Static include that draws a map: the including page's variable map (org.calacademy.antweb.Map), by running its
     JavaScript call map.getGoogleMapFunction() in a Leaflet container 400 ms after the page loads. Size by object:
     dynamic (974 x 650, with the map's subtitle), thirds (262 x 262), else small (232 x 232) with an "Enlarge Map"
     link to bigMap.do (the taxon, specimen, locality or collection, plus the place's parameters except for
     localities and collections). Bots get "Log In to see maps." instead. Counts the maps shown
     (Map.addToDisplayMapCount). Included, after maps/googleMapPreInclude.jsp, by overview-body, specimen-body,
     collection-body, locality-body, group-body, search/fieldGuide-body and dynamicMap-body; uses their variables
     map, object, objectName, mapType and overview. Docs: docs/flows/places.md (Maps). Tests:
     tests/unit/test_map_popups.py, tests/e2e/test_site_links.py. --%><%@ page import="org.calacademy.antweb.Map" %>
<%@ page import="org.calacademy.antweb.util.UserAgentTracker" %>

<%
    boolean displayMap = true;

    if (HttpUtil.isBot(request)) {
        out.println("<a href='" + AntwebProps.getDomainApp() + "/login.do'>Log In</a> to see maps.");
        displayMap = false;
    }
%>

<%
    if (map != null && displayMap) {
        String googleMapFunction = map.getGoogleMapFunction();

        if (googleMapFunction != null && googleMapFunction.length() > 0) {
            Map.addToDisplayMapCount();
            Map.addToDisplayMapCount(mapType);

            int n = googleMapFunction.indexOf("', '") + 4;
            int o = googleMapFunction.indexOf("'", n);
            String divName = googleMapFunction.substring(n, o);

            String mapSize = "small";
            if ("dynamic".equals(object)) {
                mapSize = "large";
                googleMapFunction = googleMapFunction.replaceFirst("small", "big");
            }

            if ("large".equals(mapSize) && map.getSubtitle() != null)
                out.println("<h3>" + map.getSubtitle() + "</h3>");

            String heightAndWidth;
            if ("dynamic".equals(object)) {
                heightAndWidth = "height:650px;width:974px;";
            } else if ("thirds".equals(object)) {
                heightAndWidth = "height:262px;width:262px;";
            } else {
                heightAndWidth = "height:232px;width:232px;";
            }
%>

<!-- Leaflet map container -->
<div id="<%= divName %>"
     style="<%= heightAndWidth %>border:1px solid #b9b9b9; overflow:hidden;">
</div>

<% if (!"thirds".equals(object) && !"dynamic".equals(object)) {
    String overviewParam = "";
    if (overview != null) overviewParam = overview.getParams();

    // objectName is a taxon name, specimen code, locality code or collection code: encoded once (locality codes
    // are stored as written since 2026-10-22 and may hold < > " ? &), the whole link escaped for the attribute.
    String objectParam = "";
    if ("taxonName".equals(object) || "specimen".equals(object)) {
        objectParam = object + "=" + org.calacademy.antweb.util.Html.url(objectName) + "&";
    } else if ("locality".equals(object) || "collection".equals(object)) {
        objectParam = object + "=" + org.calacademy.antweb.util.Html.url(objectName) + "&";
        overviewParam = "";
    }
%>
<div style="text-align:center; margin-top:4px;">
    <a href="<%= org.calacademy.antweb.util.Html.escAttr(AntwebProps.getDomainApp() + "/bigMap.do?" + objectParam + overviewParam) %>">
        Enlarge Map
    </a>
</div>
<% } %>

<!-- Run Leaflet-compatible map after short delay -->
<script type="text/javascript">
    document.addEventListener("DOMContentLoaded", function() {
        setTimeout(function() {
            try {
                <%= googleMapFunction %>;
            } catch (err) {
                console.error("Map init failed:", err);
            }
        }, 400);
    });
</script>

<%
        } else {
            if (AntwebProps.isDevMode()) {
                String msg = "No map data found for " + object + " (" + org.calacademy.antweb.util.Html.esc(objectName) + ")";
                out.println("<div style='color:red'>" + msg + "</div>");
            }
        }
    }
%>

<!-- end googleMapInclude.jsp -->
