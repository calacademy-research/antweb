<% 

//   String thisStatus = ..
//   Must be defined by including class...

if (true) {  // This allows multiple includes of statusDisplay on a single page without duplicated thisStatus declarations.

  // See statusDisplayChild.jsp, statusDisplayTitle.jsp and statusDisplayRow.jsp
  
  //if (AntwebProps.isDevMode()) AntwebUtil.log("statusDisplay.jsp status:" + thisChild.getStatus());
%> <a href='<%= AntwebProps.getDomainApp() %>/common/statusDisplayPage.jsp' target="new"> <%
  if (Status.VALID.equals(thisStatus)) { %>
  <img src="<%= AntwebProps.getDomainApp() %>/image/valid_name.png" border="0" title="Valid name.  <%= statusNote %>">
<% } else {

       // Alternative Worldant Statuses
       if (Status.COLLECTIVE_GROUP_NAME.equals(thisStatus)) { %>
         <img src="<%= AntwebProps.getDomainApp() %>/image/collectiveGroupName.png" width="11" height="12" border="0" title="Collective group name.  <%= statusNote %>">
    <% } else if (Status.HOMONYM.equals(thisStatus)) { %>
         <img src="<%= AntwebProps.getDomainApp() %>/image/homonym.png" width="11" height="12" border="0" title="Homonym.  <%= statusNote %>" >
    <% } else if (Status.EXCLUDED_FROM_FORMICIDAE.equals(thisStatus)) { %>
         <img src="<%= AntwebProps.getDomainApp() %>/image/excludedFromFormicidae.png" width="11" height="12" border="0" title="Excluded from formicidae.  <%= statusNote %>">
    <% } else if (Status.ORIGINAL_COMBINATION.equals(thisStatus)) { %>
         <img src="<%= AntwebProps.getDomainApp() %>/image/originalCombination.png" width="11" height="12" border="0" title="Original Combination.  <%= statusNote %>">
    <% } else if (Status.OBSOLETE_COMBINATION.equals(thisStatus)) { %>
         <img src="<%= AntwebProps.getDomainApp() %>/image/obsoleteCombination.gif" width="11" height="12" border="0" title="Obsolete Combination.  <%= statusNote %>">
    <% } else if (Status.SYNONYM.equals(thisStatus)) { %>
         <img src="<%= AntwebProps.getDomainApp() %>/image/synonym.png" width="11" height="12" border="0" title="Synonym.  <%= statusNote %>">
    <% } else if (Status.UNIDENTIFIABLE.equals(thisStatus)) { %>
         <img src="<%= AntwebProps.getDomainApp() %>/image/unidentifiable.jpg" width="11" height="12" border="0" title="Unidentifiable.  <%= statusNote %>">
    <% } else if (Status.UNAVAILABLE.equals(thisStatus)) { %>
         <img src="<%= AntwebProps.getDomainApp() %>/image/unavailable.jpg" width="11" height="12" border="0" title="Unavailable.  <%= statusNote %>">

    <% } else if (Status.UNAVAILABLE_UNCATEGORIZED.equals(thisStatus)) { %>
         <img src="<%= AntwebProps.getDomainApp() %>/image/uu.png" width="11" height="12" border="0" title="Unavailable Uncategorized.  <%= statusNote %>">
    <% } else if (Status.UNAVAILABLE_MISSPELLING.equals(thisStatus)) { %>
         <img src="<%= AntwebProps.getDomainApp() %>/image/um.png" width="11" height="12" border="0" title="Unavailable Misspelling.  <%= statusNote %>">

       <!-- Antweb specific statuses -->
    <% } else if (Status.UNRECOGNIZED.equals(thisStatus)) { %>
         <img src="<%= AntwebProps.getDomainApp() %>/image/unrecognized.png" width="11" height="12" border="0" title="Unrecognized.  <%= statusNote %>">
    <% } else if (Status.MORPHOTAXON.equals(thisStatus)) { %>
         <img src="<%= AntwebProps.getDomainApp() %>/image/morphoTaxon.gif" width="11" height="12" border="0" title="Morphotaxon.  <%= statusNote %>">
    <% } else if (Status.INDETERMINED.equals(thisStatus)) { %>
         <img src="<%= AntwebProps.getDomainApp() %>/image/indetermined.png" width="11" height="12" border="0" title="Indetermined.  <%= statusNote %>">

    <% } else { %>
         <img src="<%= AntwebProps.getDomainApp() %>/image/1x1.gif" width="11" height="12" border="0">
    <% } %>

<% } %>
</a>
<% 
} %>
