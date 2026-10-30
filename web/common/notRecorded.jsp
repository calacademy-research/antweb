<%-- At the top of a genus or species page opened for a place that has no record of the taxon (Brian Fisher,
     2026-10-29): "Lividopone is not recorded from Afrotropical. Recorded from: Malagasy. See Lividopone in all of
     AntWeb." The site keeps the last place in the session, so visitors land on such pages without choosing them.
     Written by BrowseAction (PlaceTaxonNotes.notRecorded); nothing for a taxon on the place's list or collected there. --%>
<% String notRecordedNotice = (String) request.getAttribute(org.calacademy.antweb.PlaceTaxonNotes.NOT_RECORDED);
   if (notRecordedNotice != null) { %><%= notRecordedNotice %><% } %>
