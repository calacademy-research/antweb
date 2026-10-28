<%-- The one definition of "Images" (Brian Fisher, 2026-10-27; docs/numbers.md section 1, ImageCounts): included under
     every list or table that shows an Images number. Set imagesDefinitionFiles = true before including it on a page
     that also shows Image files (uploads). --%>
<p class="images-definition"><%= org.calacademy.antweb.ImageCounts.DEFINITION %><% if (request.getAttribute("imagesDefinitionFiles") != null) { %> <%= org.calacademy.antweb.ImageCounts.FILES_DEFINITION %><% } %></p>
