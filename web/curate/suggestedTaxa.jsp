<% // Drop down list of suggested alternatives...
   if (orphanDescEditTaxon.getPossibleValidNames() != null && orphanDescEditTaxon.getPossibleValidNames().size() > 0) { %>
<br>
<b>Or:</b>
<br>
&nbsp;&nbsp;&nbsp;select from <%= orphanDescEditTaxon.getDescription().size() 
%>
  <html:select property="suggestedTaxonName">
  <option value="noneSelected" selected>None Selected</option>
<%
for (DummyTaxon dummy : orphanDescEditTaxon.getPossibleValidNames()) {
  String statusChar = dummy.getStatus().substring(0,1);
%>
  <option value="<%= dummy.getTaxonName() %>"><%= statusChar %>:<%= dummy.getPrettyTaxonName() %></option>
<% } %>
  </html:select>
  <br>
<% }//else out.println("<br>size:0");  %>
