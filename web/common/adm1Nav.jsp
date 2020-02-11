<!-- adm1Nav.jsp -->

<%
  if (HttpUtil.getTarget(request).contains("adm1s.do")) return;
%>

<div id="view">
	<div id="current_view">Georegion: 
<%

  // A.log("adm1Nav 1");

  Geolocale adm1 = (Geolocale) overview;
  String thisPage = HttpUtil.getTarget(request);
  thisPage = HttpUtil.removeOverview(thisPage);
  String view = "";
  
  boolean isOverviewPage = (thisPage.contains(adm1.getTargetDo())) || (thisPage.contains("description.do"));      
  //boolean isOverviewPage = thisPage.contains(adm1.getTargetDo());

//  A.log("adm1Nav.jsp isOverviewPage:" + isOverviewPage);

  if (thisPage.contains("bigMap.do")) thisPage = AntwebProps.getDomainApp() + "/geolocale.do";
	
  if (isOverviewPage) {
	view = "<a href='" + adm1.getThisPageTarget() + "'>" + adm1.getName() + "</a>";
  } else if (thisPage.contains("endemic.do")) {
	view = "<a href='" + AntwebProps.getDomainApp() + "/adm1.do?id=" + adm1.getId() + "'>" +  adm1.getName() + "</a>";         
  } else {
	String delim = "&";
	if (!thisPage.contains("?")) delim = "?";	  
	view = "<a href='" + thisPage + delim + adm1.getParams()  + "'>" + adm1.getName() + "</a>";     
  }

  if (adm1 != null) {
	String countryName = adm1.getParent();
	Geolocale country = GeolocaleMgr.getGeolocale(countryName, "country");

	if (isOverviewPage) {        
		view = "<a href='" + country.getThisPageTarget() + "'>" + country.getName() + "</a>" + " - " + view;
	} else if (thisPage.contains("endemic.do")) {
	  view = "<a href='" + AntwebProps.getDomainApp() + "/country.do?id=" + country.getId() + "'>" +  country.getName() + "</a> - " + view;         
	} else {
		// for taxonomic pages
		String delim = "&";
		if (!thisPage.contains("?")) delim = "?";		
		view = "<a href='" + thisPage + delim + country.getParams()  + "'>" + country.getName() + "</a>" + " - " + view;     
	}


	if (country.getId() != 0) {
	   String subregionName = country.getParent();
	   
	   Geolocale subregion = GeolocaleMgr.getGeolocale(subregionName, "subregion");

	  if (isOverviewPage && subregion != null) {
		view = "<a href='" + subregion.getThisPageTarget()  + "'>" + subregion.getName() + "</a>" + " - " + view;
	  } else if (thisPage.contains("endemic.do")) {
		view = "<a href='" + AntwebProps.getDomainApp() + "/subregion.do?id=" + subregion.getId() + "'>" +  subregion.getName() + "</a> - " + view;         
	  } else {
		// used for taxonomic pages.
		String delim = "&";
		if (!thisPage.contains("?")) delim = "?"; 
		if (subregion != null) view = "<a href='" + thisPage + delim + subregion.getParams()  + "'>" + subregion.getName() + "</a>" + " - " + view;
	  }
	  if (subregion != null) {
		String subregionId = subregion.getParent();
		Geolocale region = GeolocaleMgr.getGeolocale(subregionId);
		if (isOverviewPage) {
		  view = "<a href='" + region.getThisPageTarget() + "'>" + region.getName() + "</a>" + " - " + view;  // was grandParent.getThisPageTarget()
		} else if (thisPage.contains("endemic.do")) {
		  view = "<a href='" + AntwebProps.getDomainApp() + "/region.do?id=" + region.getId() + "'>" +  region.getName() + "</a> - " + view;         
		} else {
		  // used for taxonomic pages.
		  String delim = "&";
		  if (!thisPage.contains("?")) delim = "?";               
		  view = "<a href='" + thisPage + delim + region.getParams() + "'>" + region.getName() + "</a>" + " - " + view;  // was grandParent.getThisPageTarget()
		}
	  }
	}
  }

  out.println(view);
%>
	</div>
</div>



