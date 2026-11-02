<%-- Static include of common/siteNav.jsp for a bioregion: "Current View:" and its name, linked to its page on
     bioregion.do, elsewhere to the same page with its parameters. Uses siteNav's variable overview. Docs:
     docs/flows/places.md. --%><!-- bioregionNav.jsp -->

<div id="view">

	<div id="current_view">Current View:         
	<%
		Bioregion bioregion = (Bioregion) overview;

		String thisPage = HttpUtil.getTarget(request);
		thisPage = HttpUtil.removeOverview(thisPage);

		if (thisPage.contains(bioregion.getTargetDo())) {    
			String link = "<a href='" + bioregion.getThisPageTarget() + "'>" + bioregion.getDisplayName() + "</a>";
			//if (AntwebProps.isDevMode()) AntwebUtil.log("bioregionNav.jsp link:" + link + " thisPageTarget:" + bioregion.getThisPageTarget());
			out.println(link);
		} else {
			String delim = "&";
			if (!thisPage.contains("?")) delim = "?";    
			out.println("<a href='" + org.calacademy.antweb.util.Html.escAttr(thisPage + delim + bioregion.getParams()) + "'>" + bioregion.getDisplayName() + "</a>");
		}
	%>
	</div>
</div>