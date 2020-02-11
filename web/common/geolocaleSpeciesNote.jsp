
<%
if (
    LoginMgr.isAdmin(request) 
    && overview instanceof Geolocale
    && Rank.SPECIES.equals(pageRank)
    ) {
    
      boolean isUseChildren = ((Geolocale) overview).getIsUseChildren();       
     %>

Species in the "Georegion" lists are based on specimen records of valid species from the region, on literature, and on curation.  
Curators may add or remove valid species from the list and also morphospecies
(which must have at least one record in Antweb though not necessary from the specific georegion). 

<% if (isUseChildren) { %>
This list is based on it's children <%= Georank.getChildPluralRank((Geolocale) overview).toLowerCase() %>.
<% } %>

  <% if ("taxaPage".equals(pageType) && !isUseChildren) { %>
  <br><br>To see all specimen records from an editable country or Adm1, click on <b>Show Specimen Taxa</b> at the top of the list.
  <% } %>

<div class="page_divider taxonomic"></div>
<% } %>

