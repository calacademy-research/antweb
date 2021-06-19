<!-- regionNav.jsp -->
<div id="view">
	<div id="current_view">Georegion: 
	<%
	Geolocale region = (Geolocale) overview;
	String thisPage = HttpUtil.getTarget(request);
	thisPage = HttpUtil.removeOverview(thisPage);
	String view = "";
	boolean isOverviewPage = (thisPage.contains(region.getTargetDo())) || (thisPage.contains("description.do"));
 
	if (isOverviewPage) {
		view = "<a href='" + region.getThisPageTarget() + "'>" + region.getName() + "</a>";
	    //A.log("regionNav.jsp O view:" + view);
   	} else {
		// used for taxonomic pages. Might be with images.
		view = region.getNavLink(request, thisPage, view);
   	}

	out.println(view);
	%>
	</div>
</div>
