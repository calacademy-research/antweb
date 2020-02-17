<!-- projectNav.jsp -->

<div id="current_view">Current View: 
<%
	Project project = (Project) overview;
    String thisPage = HttpUtil.getTarget(request);
    thisPage = HttpUtil.removeOverview(thisPage);

    //A.log("projectNav thisPage:" + thisPage + " project:" + project);

    if (thisPage.contains(project.getTargetDo())) {
   	    out.println("<a href='" + project.getThisPageTarget() + "'>" + project.getDisplayName() + "</a>");
    } else {
        String delim = "&";
        if (!thisPage.contains("?")) delim = "?";
	    out.println("<a href='" + thisPage + delim + project.getParams() + "'>" + project.getDisplayName() + "</a>");
    }

%>
</div>


