<%-- Static include of common/siteNav.jsp for an adm1: the "Georegion:" trail region - subregion - country - adm1. On
     the adm1 page (adm1.do, or description.do) each name links its place's page; on endemic.do the places' pages by
     id; elsewhere (species lists, image views; bigMap.do links geolocale.do) the same page with each place's
     parameters (Geolocale.getNavLink). Nothing on adm1s.do. Uses siteNav's variable overview. Docs:
     docs/flows/places.md. --%><!-- adm1Nav.jsp -->

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

  //A.log("adm1Nav.jsp isOverviewPage:" + isOverviewPage);

  if (thisPage.contains("bigMap.do")) thisPage = AntwebProps.getDomainApp() + "/geolocale.do";
	
  if (isOverviewPage) {
	view = "<a href='" + adm1.getThisPageTarget() + "'>" + org.calacademy.antweb.util.Html.esc(adm1.getName()) + "</a>";
  } else if (thisPage.contains("endemic.do")) {
	view = "<a href='" + AntwebProps.getDomainApp() + "/adm1.do?id=" + adm1.getId() + "'>" + org.calacademy.antweb.util.Html.esc(adm1.getName()) + "</a>";         
  } else {
	String delim = "&";
	if (!thisPage.contains("?")) delim = "?";	  
	view = "<a href='" + org.calacademy.antweb.util.Html.escAttr(thisPage + delim + adm1.getParams())  + "'>" + org.calacademy.antweb.util.Html.esc(adm1.getName()) + "</a>";     
  }

  if (adm1 != null) {
	String countryName = adm1.getParent();
	Geolocale country = GeolocaleMgr.getGeolocale(countryName, "country");

	if (isOverviewPage) {        
		view = "<a href='" + country.getThisPageTarget() + "'>" + org.calacademy.antweb.util.Html.esc(country.getName()) + "</a>" + " - " + view;
	} else if (thisPage.contains("endemic.do")) {
	  view = "<a href='" + AntwebProps.getDomainApp() + "/country.do?id=" + country.getId() + "'>" + org.calacademy.antweb.util.Html.esc(country.getName()) + "</a> - " + view;         
	} else {
		// for taxonomic pages
		String delim = "&";
		if (!thisPage.contains("?")) delim = "?";		
		view = "<a href='" + org.calacademy.antweb.util.Html.escAttr(thisPage + delim + country.getParams())  + "'>" + org.calacademy.antweb.util.Html.esc(country.getName()) + "</a>" + " - " + view;     
	}

	if (country.getId() != 0) {
	   String subregionName = country.getParent();
	   
	   Geolocale subregion = GeolocaleMgr.getGeolocale(subregionName, "subregion");

	  if (isOverviewPage && subregion != null) {
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
		  view = "<a href='" + region.getThisPageTarget() + "'>" + org.calacademy.antweb.util.Html.esc(region.getName()) + "</a>" + " - " + view;  // was grandParent.getThisPageTarget()
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