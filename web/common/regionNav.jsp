<%-- Static include of common/siteNav.jsp for a region: "Georegion:" and the region's name, linked to its page on
     region.do or description.do, elsewhere to the same page with its parameters (Geolocale.getNavLink). Uses
     siteNav's variable overview. Docs: docs/flows/places.md. --%><!-- regionNav.jsp -->
<div id="view">
	<div id="current_view">Georegion: 
	<%
	Geolocale region = (Geolocale) overview;
	String thisPage = HttpUtil.getTarget(request);
	thisPage = HttpUtil.removeOverview(thisPage);
	String view = "";
	boolean isOverviewPage = (thisPage.contains(region.getTargetDo())) || (thisPage.contains("description.do"));
 
	if (isOverviewPage) {
		view = "<a href='" + region.getThisPageTarget() + "'>" + org.calacademy.antweb.util.Html.esc(region.getName()) + "</a>";
	    //A.log("regionNav.jsp O view:" + view);
   	} else {
		// used for taxonomic pages. Might be with images.
		view = region.getNavLink(request, thisPage, view);
   	}

	out.println(view);
	%>
	</div>
</div>
