<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%
  // taxonNav.jsp creates the drop down list that allows us to change to views of the 
  // same but different overview type.

    String thisPage = HttpUtil.getTarget(request);
    thisPage = HttpUtil.removeOverview(thisPage);

    //if (AntwebProps.isDevMode()) AntwebUtil.log("taxonNav.jsp 1 class:" + taxon.getClass() + " thisPage:" + thisPage);

    String changeViewOptions = "";   
	if (taxon == null) {
	  if (AntwebProps.isDevMode()) AntwebUtil.log("taxonNav.jsp taxon is null for uri:" + uri);
	} else {

        changeViewOptions = taxon.getChangeViewOptions();
        if (changeViewOptions == null) {
          changeViewOptions = "";
          //A.log("taxonNav.jsp changeViewOptions:" + changeViewOptions);
        }
    }
    
    if (!"".equals(changeViewOptions)) { 
	    if (isChangableView) { %>
		   <div id="change_view" class="has_options">
			  Change View
			  <div id="view_choices" class="options">
				<ul>
					<%= changeViewOptions %>
				</ul>      
			  </div>      
			</div>      
     <% }
    } %>