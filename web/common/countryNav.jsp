<%-- Static include of common/siteNav.jsp for a country: the "Georegion:" trail region - subregion - country. On the
     country page (or description.do) each name links its place's page; on endemic.do the places' pages by id (on
     introduced.do only the country's); elsewhere (species lists, image views; bigMap.do links geolocale.do) the same
     page with each place's parameters (Geolocale.getNavLink). Uses siteNav's variable overview. Docs:
     docs/flows/places.md. --%><!-- countryNav.jsp -->

<div id="view">
	<div id="current_view">Georegion: 
<%
	Geolocale country = (Geolocale) overview;
	String thisPage = HttpUtil.getTarget(request);
	thisPage = HttpUtil.removeOverview(thisPage);
	String view = "";
	boolean isOverviewPage = (thisPage.contains(country.getTargetDo())) || (thisPage.contains("description.do"));

	if (thisPage.contains("bigMap.do")) thisPage = AntwebProps.getDomainApp() + "/geolocale.do";
	
	//A.log("countryNav.jsp thisPage:" + thisPage + " isOverviewPage:" + isOverviewPage);

	if (isOverviewPage) {        
		view = "<a href='" + country.getThisPageTarget() + "'>" + org.calacademy.antweb.util.Html.esc(country.getName()) + "</a>";
	} else if (thisPage.contains("endemic.do") || thisPage.contains("introduced.do")) {
	  view = "<a href='" + AntwebProps.getDomainApp() + "/country.do?id=" + country.getId() + "'>" + org.calacademy.antweb.util.Html.esc(country.getName()) + "</a>";         
	} else {

  	  String delim = "&";
	  if (!thisPage.contains("?")) delim = "?";
	  view = "<a href='" + org.calacademy.antweb.util.Html.escAttr(thisPage + delim + country.getParams())  + "'>" + org.calacademy.antweb.util.Html.esc(country.getName()) + "</a>";
	  //A.log("countryNav view:" + view);

	}
	//if (AntwebProps.isDevMode()) AntwebUtil.log("countryNav.jsp isOverviewPage:" + isOverviewPage + " view:" + view);
	if (country.getId() != 0) {
	   String subregionName = country.getParent();
	   Geolocale subregion = GeolocaleMgr.getGeolocale(subregionName, "subregion");

		if (subregion != null) {
		  if (isOverviewPage ) {
			view = "<a href='" + subregion.getThisPageTarget()  + "'>" + org.calacademy.antweb.util.Html.esc(subregion.getName()) + "</a>" + " - " + view;
		  } else if (thisPage.contains("endemic.do")) {
			view = "<a href='" + AntwebProps.getDomainApp() + "/subregion.do?id=" + subregion.getId() + "'>" + org.calacademy.antweb.util.Html.esc(subregion.getName()) + "</a> - " + view;         
		  } else {
			// used for taxonomic pages. Might be with images.
  	  	    view = subregion.getNavLink(request, thisPage, view);
		  }
		  if (subregion != null) {
			String subregionId = subregion.getParent();
			Geolocale region = GeolocaleMgr.getGeolocale(subregionId);
			if (isOverviewPage) {
			  view = "<a href='" + region.getThisPageTarget() + "'>" + org.calacademy.antweb.util.Html.esc(region.getName()) + "</a> - " + view;  // was grandParent.getThisPageTarget()
			} else if (thisPage.contains("endemic.do")) {
			  view = "<a href='" + AntwebProps.getDomainApp() + "/region.do?id=" + region.getId() + "'>" + org.calacademy.antweb.util.Html.esc(region.getName()) + "</a> - " + view;         
			} else {
			  // used for taxonomic pages. Might be with images.
  	  	      view = region.getNavLink(request, thisPage, view);
			}
		  }
	  
		}
	  
	}


	out.println(view);   
%>
    </div>
</div>





