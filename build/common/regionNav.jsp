<!-- regionNav.jsp -->
<div id="view">
	<div id="current_view">Georegion: 
	<%
	Geolocale region = (Geolocale) overview;
	String thisPage = HttpUtil.getTarget(request);
	thisPage = HttpUtil.removeOverview(thisPage);
	String view = null;
	//boolean isOverviewPage = thisPage.contains(region.getTargetDo());
	boolean isOverviewPage = (thisPage.contains(region.getTargetDo())) || (thisPage.contains("description.do"));
 
	if (isOverviewPage) {
		view = "<a href='" + region.getThisPageTarget() + "'>" + region.getName() + "</a>";
	} else {
		// used for taxonomic pages.
		String delim = "&";
		if (!thisPage.contains("?")) delim = "?";
		view = "<a href='" + thisPage + delim + region.getParams()  + "'>" + region.getName() + "</a>";
	}
	  
	//if (AntwebProps.isDevMode()) AntwebUtil.log("regionNav.jsp isOverviewPage:" + isOverviewPage + " view:" + view);
   
	out.println(view);
	%>
	</div>
</div>
