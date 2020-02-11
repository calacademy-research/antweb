 <!-- pageToolLinks.jsp -->
<%
  String coming_from = null;
  if (request.getRequestURI().contains("showBrowse.jsp")) {
      coming_from = "&pr=b";
  } else if (request.getRequestURI().contains("specimenImages.jsp")) {
      coming_from = "&pr=i";
  } else if (request.getRequestURI().contains("imagePage.jsp")) {
      coming_from = "&pr=i";
  } else if (request.getRequestURI().contains("taxonPage.jsp")) {
      coming_from = "&pr=d";
  } else {
      coming_from = "";
  }
%>
        <div class="clear"></div>
        <div id="tools">
            <div class="tool_label"><a href="getComparison.do?<bean:write name="taxon" property="browserParams" /><%= coming_from %>">Compare Images</a></div>
<% if (!Rank.SPECIES.equals(taxon.getRank()) 
        // &&(map.getGoogleMapFunction() != null) && (map.getGoogleMapFunction().length() > 0)
      ) { %>
            <div class="tool_label"><a href="mapComparison.do?<bean:write name="taxon" property="browserParams" /><%= coming_from %>">Map <bean:write name="taxon" property="nextRank"/></a></div>
<% }

      //if (AntwebProps.isDevMode()) AntwebUtil.log("pageToolLinks.jsp childrenHaveImages:" + taxon.getChildrenHaveImages()); 
      // + " childrenHaveImages(p):" + taxon.getChildrenHaveImages("p")); // + " childImagesCount:" + taxon.getChildImagesCount("p"));      

    String ptRank = request.getParameter("rank");

    if ((Rank.GENUS.equals(taxon.getRank())) || (Rank.SUBFAMILY.equals(taxon.getRank())) 
      // && taxon.getChildrenHaveImages()
      //&& (taxon.getChildImagesCount("p") > 1)
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
            <div class="tool_label"><a href="<%= fieldGuideUrl %>?<%= fieldGuideParams %><%= overview.getParams() %>&rank=<%=ptRank%><%= coming_from %>">Create Field Guide</a></div>
<% } %>
<% if (Utility.notBlank(taxon.getGenus())) { %>
            <div class="tool_label"><span id="download_data" onclick="loadTabData('<%= util.getDomainApp() %>', 'getSpecimenList.do?<%= overview.getParams() %>&taxonName=<%= taxon.getTaxonName() %>'); return false;">Download Data</span></div>
<% }

  if (LoginMgr.isAdmin(request)) {
  A.log("pageToolLinks.jsp target:" + HttpUtil.getTarget(request));
    if (Rank.GENUS.equals(taxon.getRank()) && HttpUtil.getTarget(request).contains("images.do")) { 
      String overviewCriteria = "&" + overview.getSearchCriteria();
      if (!overviewCriteria.contains("museum=") || (!overviewCriteria.contains("project=") || overviewCriteria.contains("project=allantwebants"))) {
        String imageSearchUrl = AntwebProps.getDomainApp() + "/advancedSearch.do?searchMethod=advancedSearch&advanced=true"
          + "&subfamilySearchType=equals&subfamily=" + taxon.getSubfamily() 
          + "&genusSearchType=equals&genus=" + taxon.getGenus()
          + "&images=on&resultRank=specimen"
          + overviewCriteria;
   %> <div class="tool_label"><a target="_blank" href="<%= imageSearchUrl %>">Image Search</a></div> <%
      }
      A.log("pageToolLinks.jsp overview:" + overviewCriteria);     
    }
  }

 %>
            <div class="clear"></div>
        </div>
        <div class="clear"></div>
