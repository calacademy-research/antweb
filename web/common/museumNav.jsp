<%-- Static include of common/siteNav.jsp for a museum: "Current View:" and its name, linked to its page on
     museum.do, elsewhere to the same page with museumCode=. Uses siteNav's variable overview. Docs:
     docs/flows/places.md. --%><!-- museumNav.jsp -->
<div id="current_view">Current View: 
<%
	Museum museum = (Museum) overview;
    String thisPage = HttpUtil.getTarget(request);
    thisPage = HttpUtil.removeOverview(thisPage);

    if (thisPage.contains(museum.getTargetDo())) {
   	    out.println("<a href='" + museum.getThisPageTarget() + "'>" + museum.getDisplayName() + "</a>");
    } else {
		String delim = "&";
		if (!thisPage.contains("?")) delim = "?";    
	    out.println("<a href='" + org.calacademy.antweb.util.Html.escAttr(thisPage + delim + museum.getParams()) + "'>" + museum.getDisplayName() + "</a>");
    }
%>
</div>
