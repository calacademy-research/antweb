<!-- bioregionNav.jsp -->

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
			out.println("<a href='" + thisPage + delim + bioregion.getParams() + "'>" + bioregion.getDisplayName() + "</a>");
		}
	%>
	</div>
</div>