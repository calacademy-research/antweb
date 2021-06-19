

<div id="view">
	<div id="current_view">Georegion: 
<%
	  Geolocale subregion = (Geolocale) overview;
	  String thisPage = HttpUtil.getTarget(request);
	  thisPage = HttpUtil.removeOverview(thisPage);
	  String view = "";
	  //boolean isOverviewPage = thisPage.contains(subregion.getTargetDo());
	  boolean isOverviewPage = (thisPage.contains(subregion.getTargetDo())) || (thisPage.contains("description.do"));
  
	  if (isOverviewPage) {
		view = "<a href='" + subregion.getThisPageTarget()  + "'>" + subregion.getName() + "</a>" + view;
	  } else {
		// used for taxonomic pages. Might be with images.
  	  	  view = subregion.getNavLink(request, thisPage, view);
	  }
	  if (subregion != null) {
		String regionId = subregion.getParent();
		Region region = (Region) GeolocaleMgr.getGeolocale(regionId);
		if (isOverviewPage) {
		  view = "<a href='" + region.getThisPageTarget() + "'>" + region.getName() + "</a>" + " - " + view;  // was grandParent.getThisPageTarget()
		} else {
		  // used for taxonomic pages. Might be with images.
  	  	  view = region.getNavLink(request, thisPage, view);
		}
	  }

	if (AntwebProps.isDevMode()) AntwebUtil.log("subregionNav.jsp isOverviewPage:" + isOverviewPage + " view:" + view);


	  out.println(view);      
%>
	</div>
</div>