<%
    // The View in AntCat link searches AntCat for the name. A name not identified to genus (a placeholder such as
    // "(camponotini)", or indet / undet) has no AntCat entry and its concise name is empty, so no link is shown
    // (PlaceTotals.isIdentifiedToGenus, Brian Fisher's rule of 2026-10-22).
    String antcatViewName = java.util.Objects.toString(taxon.getPrettyConciseName(), "").trim();
    if (!antcatViewName.isEmpty() && org.calacademy.antweb.home.PlaceTotals.isIdentifiedToGenus(taxon.getGenus())) {
%>
<div id="antcat_view">
    <a href="https://www.antcat.org/catalog/search?utf8=%E2%9C%93&st=m&qq=<%= java.net.URLEncoder.encode(antcatViewName, java.nio.charset.StandardCharsets.UTF_8) %>&commit=Go" target="new">View in AntCat</a>
</div>
<% } %>
