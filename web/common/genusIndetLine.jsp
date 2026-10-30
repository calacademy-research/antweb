<%-- Under the species of a genus page (description.do, browse.do, images.do; Brian Fisher, 2026-10-29): the specimens
     of the genus identified only to genus, "Not identified to species: Lividopone (indet), 45 specimens (4 imaged)",
     linked to that name's page in the same place. Not a species; no number of the page changes. Written by BrowseAction
     (PlaceTaxonNotes.indetLineHtml); nothing when there are none in the place.
     Since 2026-10-30, in a place that selects specimens (country, adm1, region, subregion, bioregion, museum) the line
     names every specimen collected there under a name not on the species list, the indet name being one of them:
     "Specimens collected here not on the species list: 3 (names: Acropyga mz01, Acropyga mz02)", the number linked to
     the place's specimen search, each name to its page (PlaceTaxonNotes.otherNamesLineHtml). All of AntWeb: as above. --%>
<% String genusIndetLine = (String) request.getAttribute(org.calacademy.antweb.PlaceTaxonNotes.INDET_LINE);
   if (genusIndetLine != null) { %><%= genusIndetLine %><% } %>
