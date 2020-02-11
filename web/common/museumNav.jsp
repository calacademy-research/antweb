<!-- museumNav.jsp -->
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
	    out.println("<a href='" + thisPage + delim + museum.getParams() + "'>" + museum.getDisplayName() + "</a>");
    }
%>
</div>
