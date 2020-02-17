<!-- list_tools.jsp -->
<ul>
<li><a href="getComparison.do?<bean:write name="taxon" property="browserParams" />">Compare Images</a></li>
<% if ( !Rank.SPECIES.equals(taxon.getRank()) && (map.getGoogleMapFunction() != null) 
     && (map.getGoogleMapFunction().length() > 0)
      ) { %>
            <li><a href="mapComparison.do?<bean:write name="taxon" property="browserParams" />">Map <bean:write name="taxon" property="nextRank"/></a></li>
<% } %>
<%
    if ( (Rank.GENUS.equals(taxon.getRank()) || Rank.SUBFAMILY.equals(taxon.getRank()) ) 
        && (taxon.getChildrenHaveImages())
      // (taxon.getChildImagesCount("p") > 1)
      ) {
     // There will only be a field guide option for genus and subfamily, and if there are more than a single item to be printed.
      String fieldGuideParams = "";
      if (taxon.getSubfamily() != null) {
        fieldGuideParams += "subfamily=" + taxon.getSubfamily()  + "&";
      }
      if (taxon.getGenus() != null) {
        fieldGuideParams += "genus=" + taxon.getGenus()  + "&";
      }
      if (taxon.getSpecies() != null) {
        fieldGuideParams += "species=" + taxon.getSpecies()  + "&";
      }
      String fieldGuideUrl = AntwebProps.getDomainApp() + "/fieldGuide.do";
%>
            <li><a href="<%= fieldGuideUrl %>?<%= fieldGuideParams %>project=<%= projectName %>">Create Field Guide</a></li>
<% } %>
<% if (Utility.notBlank(taxon.getGenus())) { %>
<logic:greaterThan name="taxon" property="childrenCount" value="0">
             <li id="download_data"><a href="">Download Data</a></li>
</logic:greaterThan>
<% } %>
</ul>
