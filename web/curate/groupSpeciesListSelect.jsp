<%-- Menu of species lists (variable groupSpeciesList, form property editSpeciesList) for a static include. Not
     used: no page includes it. --%>  <html:select property="editSpeciesList">
	<html:option value="none">Select...</html:option>
	<% for (SpeciesListable s : groupSpeciesList) { %>
         <html:option value="<%= s.getName() %>"><%= org.calacademy.antweb.util.Html.esc(s.getTitle()) %></html:option>
	<% } %>
  </html:select>
