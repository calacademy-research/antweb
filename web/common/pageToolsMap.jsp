<%-- Unused as of 2026-10-30: a "Download KML Data" label for a taxon with a genus. Its only mention, in
     dynamicMap-body.jsp, is inside an HTML comment, so it is not included; Caddy answers a direct request with 404
     (not on the JSP allow list in docker/caddy/Caddyfile). --%>        <div class="clear"></div>
        <div id="tools">
<% if (Utility.notBlank(taxon.getGenus())) { %>
            <div class="tool_label"><span id="download_data">Download KML Data</span></div>
<% } %>
            <div class="clear"></div>
        </div>
        <div class="clear"></div>
