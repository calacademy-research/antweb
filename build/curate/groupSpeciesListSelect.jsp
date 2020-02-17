  <html:select property="editSpeciesList">
	<html:option value="none">Select...</html:option>
	<% for (SpeciesListable s : groupSpeciesList) { %>
         <html:option value="<%= s.getName() %>"><%= s.getTitle() %></html:option>
	<% } %>
  </html:select>
