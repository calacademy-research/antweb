<!-- countryNav.jsp -->

<div id="view">
	<div id="current_view">Georegion: 
<%
	Geolocale country = (Geolocale) overview;
	String thisPage = HttpUtil.getTarget(request);
	thisPage = HttpUtil.removeOverview(thisPage);
	String view = "";
	boolean isOverviewPage = (thisPage.contains(country.getTargetDo())) || (thisPage.contains("description.do"));

	if (thisPage.contains("bigMap.do")) thisPage = AntwebProps.getDomainApp() + "/geolocale.do";
	
	A.log("countryNav.jsp thisPage:" + thisPage + " isOverviewPage:" + isOverviewPage);

	if (isOverviewPage) {        
		view = "<a href='" + country.getThisPageTarget() + "'>" + country.getName() + "</a>";
	} else if (thisPage.contains("endemic.do") || thisPage.contains("introduced.do")) {
	  view = "<a href='" + AntwebProps.getDomainApp() + "/country.do?id=" + country.getId() + "'>" +  country.getName() + "</a>";         
	} else {
		String delim = "&";
		if (!thisPage.contains("?")) delim = "?";
		view = "<a href='" + thisPage + delim + country.getParams()  + "'>" + country.getName() + "</a>";     
		//A.log("countryNav view:" + view);
	}
	//if (AntwebProps.isDevMode()) AntwebUtil.log("countryNav.jsp isOverviewPage:" + isOverviewPage + " view:" + view);
	if (country.getId() != 0) {
	   String subregionName = country.getParent();
	   Geolocale subregion = GeolocaleMgr.getGeolocale(subregionName, "subregion");

		if (subregion != null) {
		  if (isOverviewPage ) {
			view = "<a href='" + subregion.getThisPageTarget()  + "'>" + subregion.getName() + "</a>" + " - " + view;
		  } else if (thisPage.contains("endemic.do")) {
			view = "<a href='" + AntwebProps.getDomainApp() + "/subregion.do?id=" + subregion.getId() + "'>" +  subregion.getName() + "</a> - " + view;         
		  } else {
			// used for taxonomic pages.
			String delim = "&";
			if (!thisPage.contains("?")) delim = "?";				
			view = "<a href='" + thisPage + delim + subregion.getParams()  + "'>" + subregion.getName() + "</a>" + " - " + view;
		  }
		  if (subregion != null) {
			String subregionId = subregion.getParent();
			Geolocale region = GeolocaleMgr.getGeolocale(subregionId);
			if (isOverviewPage) {
			  view = "<a href='" + region.getThisPageTarget() + "'>" + region.getName() + "</a> - " + view;  // was grandParent.getThisPageTarget()
			} else if (thisPage.contains("endemic.do")) {
			  view = "<a href='" + AntwebProps.getDomainApp() + "/region.do?id=" + region.getId() + "'>" +  region.getName() + "</a> - " + view;         
			} else {
			  // used for taxonomic pages.
			  String delim = "&";
			  if (!thisPage.contains("?")) delim = "?";
			  view = "<a href='" + thisPage + delim + region.getParams() + "'>" + region.getName() + "</a> - " + view;  // was grandParent.getThisPageTarget()
			}
		  }
	  
		}
	  
	}


	out.println(view);   
%>
    </div>
</div>





