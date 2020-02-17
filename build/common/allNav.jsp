<%@ page import="org.calacademy.antweb.geolocale.*" %>

<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%
  // allNav.jsp creates the drop down list that allows us to change back to allantwebants or worldants.
  String otherUrl = AntwebProps.getDomainApp();

  String thisPage = HttpUtil.getTarget(request);

  //x`A.log("allNav.jsp thisPage:" + thisPage + " overview:" + overview.getClass());

  boolean isPerformanceSensitive = Project.isPerformanceSensitive(thisPage);
  //boolean useWideBox = thisPage.contains("adm1.do") || thisPage.contains("country.do") || thisPage.contains("subregion.do");

  if (!isPerformanceSensitive || thisPage.contains("countryName=")) {

    thisPage = HttpUtil.removeOverview(thisPage);

    String changeViewOptions = "";
    if (overview != null) {
      if (overview.getName() == null) {
          if (AntwebProps.isDevMode()) AntwebUtil.log("ERROR allNav.jsp WST overview getName() is null.  ThisPage:" + thisPage);
      } else {
        //if (AntwebProps.isDevMode()) AntwebUtil.log("allNav.jsp thisPage:" + thisPage + " otherUrl:" + otherUrl + " changeViewOptions:" + changeViewOptions);
        if (!isPerformanceSensitive) {
          if (!overview.getName().equals(Project.ALLANTWEBANTS)) {
            changeViewOptions += Project.getAllAntwebLi(thisPage);
          }
          if (!overview.getName().equals(Project.WORLDANTS)) {
            changeViewOptions += Project.getBoltonLi(thisPage);
          }
        }
      }	
      changeViewOptions += overview.getChangeViewOptions(thisPage);
	}
	if (changeViewOptions == null) {
	    changeViewOptions = "";
	    AntwebUtil.log("taxonNav.jsp changeViewOptions:" + changeViewOptions);
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
    } 
  }
%>    


