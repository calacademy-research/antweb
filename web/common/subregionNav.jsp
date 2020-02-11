

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
		// used for taxonomic pages.
		String delim = "&";
		if (!thisPage.contains("?")) delim = "?";            
		view = "<a href='" + thisPage + delim + subregion.getParams()  + "'>" + subregion.getName() + "</a>" + view;
A.log("subregionNav.jsp view:" + view);
	  }
	  if (subregion != null) {
		String subregionId = subregion.getParent();
		Geolocale region = GeolocaleMgr.getGeolocale(subregionId);
		if (isOverviewPage) {
		  view = "<a href='" + region.getThisPageTarget() + "'>" + region.getName() + "</a>" + " - " + view;  // was grandParent.getThisPageTarget()
		} else {
		  // used for taxonomic pages.
		  String delim = "&";
		  if (!thisPage.contains("?")) delim = "?";               
		  view = "<a href='" + thisPage + delim + region.getParams() + "'>" + region.getName() + "</a>" + " - " + view;  // was grandParent.getThisPageTarget()
		}
	  }

	  out.println(view);      
%>
	</div>
</div>