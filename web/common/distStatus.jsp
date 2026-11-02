<%-- The introduced (red I) or endemic (green E) mark of a list row, linked to common/distStatusDisplayPage.jsp;
     introduced when the row's introduced map says the taxon is not native to the place's bioregion
     (TaxonPropMgr.isBioregionNative). Statically included by taxonReportBody.jsp (uses overview, child, thisChild).
     Docs: docs/numbers.md rows 24 and 25. --%><% 
	String bioregion = null;
	//String altBioregion = null;
	if (overview instanceof Bioregion) {
	  bioregion = overview.getName();
	}  
	if (overview instanceof Geolocale) {
	  bioregion = ((Geolocale) overview).getBioregion(); 
	  //altBioregion = ((Geolocale) overview).getAltBioregion();
	}

	String introducedMap = child.getIntroducedMap();

    //A.log("distStatus.jsp intrdocuedMap:" + introducedMap);
	boolean isIntroduced = false;

	if (introducedMap != null) {
	  isIntroduced = !TaxonPropMgr.isBioregionNative(bioregion, introducedMap);  
	  //A.log("distStatus.jsp overview:" + overview + " child:" + child + " isIntroduced:" + isIntroduced + " bioregion:" + bioregion + " introducedMap:" + introducedMap);
	} else {
	  //A.log("distStatus.jsp NOPE overview:" + overview);
	}

    if (isIntroduced) { %>
      <a href='<%= AntwebProps.getDomainApp() %>/common/distStatusDisplayPage.jsp#type' target="new">
        <img src="<%= AntwebProps.getDomainApp() %>/image/redI.png" width="11" height="12" border="0" title="Introduced">
      </a>
 <% } else if (thisChild.getTaxonSet().getIsEndemic()) { %>
      <a href='<%= AntwebProps.getDomainApp() %>/common/distStatusDisplayPage.jsp#type' target="new">
        <img src="<%= AntwebProps.getDomainApp() %>/image/greenE.png" width="11" height="12" border="0" title="Endemic">
      </a>
 <% } else { %>
      <img src="<%= AntwebProps.getDomainApp() %>/image/1x1.gif" width="11" height="12" border="0">
 <% } %>
