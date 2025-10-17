<%@ page import="org.calacademy.antweb.Map" %>
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

    String objectParam = "";
    if ("taxonName".equals(object) || "specimen".equals(object)) {
        objectParam = object + "=" + objectName + "&";
    } else if ("locality".equals(object) || "collection".equals(object)) {
        objectParam = object + "=" + objectName + "&";
        overviewParam = "";
    }
%>
<div style="text-align:center; margin-top:4px;">
    <a href="<%= AntwebProps.getDomainApp() %>/bigMap.do?<%= objectParam %><%= overviewParam %>">
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
                String msg = "No map data found for " + object + " (" + objectName + ")";
                out.println("<div style='color:red'>" + msg + "</div>");
            }
        }
    }
%>

<!-- end googleMapInclude.jsp -->
