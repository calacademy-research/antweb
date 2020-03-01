<!-- data_download_overlay.jsp -->
<%
  boolean useKML = false; // We no longer use KML. Could be re-activated, for instance for Thau.
  String kmlURL = null;

  if (!LoginMgr.isLoggedIn(request)) {
%>

<div id="download_data_overlay" style="display:none;">
    <div id="download_overlay"><!-- data_download_overlay.jsp 1 -->
        <div class="left"><h3>Download Specimen Data</h3></div><div class="right" id="close_download_data">X</div>
        <div class="clear"></div>
        <p><%= Login.MUST_LOGIN_MESSAGE %></p>
    </div>
</div>

<% }

    if (useKML) {
      String googleEarthParams = "";
      if (Utility.notBlank(taxon.getFullName())) {
        googleEarthParams += "google=";
        if (taxon.getSubfamily() != null) {
          googleEarthParams += "subfamily=" + taxon.getSubfamily()  + "---";
        }
        if (taxon.getGenus() != null) {
          googleEarthParams += "genus=" + taxon.getGenus()  + "---";
        }
        if (taxon.getSpecies() != null) {
          googleEarthParams += "species=" + taxon.getSpecies()  + "---";
        }
        if (overview != null) googleEarthParams += overview.getParams();
      }
      kmlURL = AntwebProps.getDomainApp() + "/" + AntwebProps.getGoogleEarthURI() + "?" + googleEarthParams;

      A.log("data_download_overlay.jsp kmlURL:" + kmlURL);
    }
%>

<div id="download_data_overlay" style="display:none;">
    <div id="download_overlay">
        <div class="left"><h3>Download Specimen Data</h3></div>
        <div class="right" id="close_download_data">X</div>
        <div class="clear"></div>
        <p>To download specimen data, Right-click and save to your desktop.</p>
        <ul><!-- data_download_overlay.jsp 2 -->
            <% if (useKML) { %>
                <li>&middot; <a href="<%= kmlURL %>" target="new">KML</a></li>
            <% } %>
                <iframe id="tab_data" width="100%" border="0" src="<%= util.getDomainApp() %>/search/loading_search_results_data.jsp"></iframe>
            <%
               String dataLink = (String) request.getAttribute("dataLink");
            %>
        </ul>
    </div>
</div>
