<%-- Under the species of a genus page (description.do, browse.do, images.do; Brian Fisher, 2026-10-29): the specimens
     of the genus identified only to genus, "Not identified to species: Lividopone (indet), 45 specimens (4 imaged)",
     linked to that name's page in the same place. Not a species; no number of the page changes. Written by BrowseAction
     (PlaceTaxonNotes.indetLineHtml); nothing when there are none in the place. --%>
<% String genusIndetLine = (String) request.getAttribute(org.calacademy.antweb.PlaceTaxonNotes.INDET_LINE);
   if (genusIndetLine != null) { %><%= genusIndetLine %><% } %>
