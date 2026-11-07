<%-- The introduced (red I) or endemic (green E) mark of a list row, linked to common/distStatusDisplayPage.jsp;
     introduced when the row's introduced map says the taxon is not native to the place's bioregion
     (TaxonPropMgr.isIntroducedIn, the rule of the nightly flags; a place without a bioregion marks none). Statically included by taxonReportBody.jsp (uses overview, child, thisChild).
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

	// The rule of the nightly Introduced flags (TaxonPropMgr.isIntroducedIn; no bioregion: not introduced).
	isIntroduced = TaxonPropMgr.isIntroducedIn(introducedMap, bioregion);
	// A row added from specimens (not on the place's list) is on no introduced or endemic list: no mark (Brian Fisher,
	// 2026-11-05: New Caledonia's Brachymyrmex obscurior_cf, 1 specimen, was marked Introduced).
	boolean onList = thisChild.getTaxonSet() != null && thisChild.getTaxonSet().exists();
	if (!onList) isIntroduced = false;

    if (isIntroduced) { %>
      <a href='<%= AntwebProps.getDomainApp() %>/common/distStatusDisplayPage.jsp#type' target="new">
        <img src="<%= AntwebProps.getDomainApp() %>/image/redI.png" width="11" height="12" border="0" title="Introduced">
      </a>
 <% } else if (onList && thisChild.getTaxonSet().getIsEndemic()) { %>
      <a href='<%= AntwebProps.getDomainApp() %>/common/distStatusDisplayPage.jsp#type' target="new">
        <img src="<%= AntwebProps.getDomainApp() %>/image/greenE.png" width="11" height="12" border="0" title="Endemic">
      </a>
 <% } else { %>
      <img src="<%= AntwebProps.getDomainApp() %>/image/1x1.gif" width="11" height="12" border="0">
 <% } %>
