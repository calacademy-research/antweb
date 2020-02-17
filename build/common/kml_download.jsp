<%
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
      String kmlURL = AntwebProps.getDomainApp() + "/" + AntwebProps.getGoogleEarthURI() + "?" + googleEarthParams;
%>
<div id="download_data_overlay" style="display:none;">
    <div id="download_overlay">
        <div class="left"><h3>Download KML Data</h3></div><div class="right" id="close_download_data">X</div>
        <div class="clear"></div>
        <p>To download KML data, Right-click and save to your desktop.</p>
        <ul>
            <li>&middot; <a href="<%= kmlURL %>" target="new">KML</a></li>
        </ul>
    </div>
</div>
