
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.util.*" %>

<% 
	Taxon taxon = (Taxon) session.getAttribute("taxon");

	Overview overview = OverviewMgr.getOverview(request);

	//A.log("siteNav.jsp overview:" + overview + " taxon:" + taxon);

	String uri = null;

	try {
		uri = request.getRequestURI();

		org.calacademy.antweb.Formatter format = new org.calacademy.antweb.Formatter();

		Utility util = new Utility();
		String domainApp = util.getDomainApp();

        //A.log("siteNav.jsp isMobile:" + HttpUtil.isMobile(request));
      
        if (HttpUtil.isMobile(request)) { %>
           <%@ include file="mobileSearchBar.jsp" %>
     <% } %>


        
	  <%@ include file="menuBar.jsp" %>

	  <%
		HashMap<String, Project> allProjects = ProjectMgr.getAllProjectsHash();

		//String queryString = HttpUtil.getQueryString(request);
		boolean isChangableView = true;
		//if (uri.contains("bigPicture.jsp")) {
		//  isChangableView = false;
		//} 
		//if (AntwebProps.isDevMode()) AntwebUtil.log("siteNav.jsp requestURI:" + uri);

 	    //String facet = HttpUtil.getFacet(request);
	    //String strutsTarget = facet;
 
		boolean hasIndexStuff = false;
		hasIndexStuff = (uri.indexOf("index.jsp") != -1) || (uri.indexOf(".jsp") == -1);  

        // NPE possibility.
	    //A.log("siteNav.jsp overview:" + overview + " class:" + overview.getClass() + " hasIndexStuff:" + hasIndexStuff + " uri:" + uri);

		if (hasIndexStuff) {
		     // do not display current view stuff
		} else {
			// Do not Cite or add Current View to header on these pages...
			if ( // in page list
				(uri.contains("index.jsp")) 
			 || (uri.contains("about.jsp")) 
			 || (uri.contains("citing_antweb.jsp")) 
			 || (uri.contains("user_guide.jsp")) 
			 || (uri.contains("tools.jsp")) 
			 || (uri.contains("tech.jsp")) 
			 || (uri.contains("staff.jsp")) 
			 || (uri.contains("curators.jsp")) 
			 || (uri.contains("documentation.jsp")) 
			 || (uri.contains("releaseNotes.jsp")) 
			 || (uri.contains("press.jsp")) 
			 || (uri.contains("donate.jsp")) 
			 || (uri.contains("contact.jsp")) 
			 || (uri.contains("comparison.jsp")) 
			 || (uri.contains("mapComparison.jsp")) 
			 || (uri.contains("fieldGuide.jsp")) 
			 || (uri.contains("basicResults.jsp")) 
			 || (uri.contains("multiTaxaComparison.jsp")) 
			 || (uri.contains("multiTaxaOneView.jsp")) 
			 || (uri.contains("descEditReport.jsp")) 
			 || (uri.contains("recentSearchResults.jsp")) 
			 //|| (uri.contains("login.jsp")) 
			 || (uri.contains("notLoggedIn.jsp")) 
			 || (uri.contains("geneva.jsp")) 
			 || (uri.contains("genoa.jsp")) 
			 || (uri.contains("basel.jsp")) 
			 || (uri.contains("oxford.jsp")) 
			 || (uri.contains("berlin.jsp")) 
			 || (uri.contains("copenhagen.jsp")) 
			 || (uri.contains("worldants.jsp")) 
			 || (uri.contains("imageLikes.jsp")) 
			 || (uri.contains("editProject.do"))  // Should this be jsp?

			 || (uri.contains("subregions.jsp")) 
			 || (uri.contains("regions.jsp")) 
			 || (uri.contains("countries.jsp")) 
			 || (uri.contains("museums.jsp")) 
			 || (uri.contains("projects.jsp")) 
	 
			 || (uri.contains("message.jsp")) 
			 || (uri.contains("collection.jsp"))
			 || (uri.contains("locality.jsp"))
			 || (uri.contains("advancedSearch.jsp")) 
			 || (uri.contains("advancedSearchResults.jsp")) 
			 || (uri.contains("advancedSearchResultsByTaxon.jsp")) 
             || (uri.contains("statsPage.jsp"))
			 ) {
		%>
  
		<div id="page_context">
		  <div id="view"></div>

		<%
			} else { // not in page list
		%>
		<div id="page_context">
		  <!-- div id="view" was causing everything after to be bold -->
		<%      
				// Display some Nav stuff.  Maybe for a Taxon, a Project or an (other kind of) Overview.
				if (overview instanceof Project) {
				  %> <%@ include file="projectNav.jsp" %> <%
				} else 
				if (overview instanceof Bioregion) { %>
				  <%@ include file="bioregionNav.jsp" %> <%
				} else 
				if (overview instanceof Museum) { %> 
				  <%@ include file="museumNav.jsp" %> <%
				} else
				if (overview instanceof Geolocale) {
				  String georank = ((Geolocale) overview).getGeorank();

				  //A.log("siteNav.jsp 2 overview:" + overview + " taxon:" + taxon);

				  if ("region".equals(georank)) {
		%> <%@ include file="regionNav.jsp" %> <%
				  } else 
				  if ("subregion".equals(georank)) {
		%> <%@ include file="subregionNav.jsp" %> <%
				  }
				  if ("country".equals(georank)) {
		%> <%@ include file="countryNav.jsp" %> <%
				  }
				  if ("adm1".equals(georank)) {
	    %> <%@ include file="adm1Nav.jsp" %> <%
				  }
				}

				if (taxon != null) { %>
				  <%@ include file="taxonNav.jsp" %> <%
				} else { %>
				  <%@ include file="allNav.jsp" %> <%
				}

		  %>
			  <div id="cite">Cite this page</div>
			  <div id="citation_overlay">
				  <div id="citation_info_overlay">
					  <div class="left"><h3>Citing AntWeb</h3></div><div class="right" id="close_citation_overlay">X</div>
					  <div class="clear"></div>
					  <p>To cite this page, please use the following:</p>
					  <p><b>&middot; For print:</b> <span id="cite_print"></span>. Accessed <span class="today"></span></p>
					  <p><b>&middot; For web:</b> <span id="cite_web"></span></p>
					  <p><span id="cite_copyright"></span></p>
				  </div>
			  </div>
			  <div class="clear"></div>
		<%
			} // end not in page list
		%>

		</div>
	  <% 
		} // hasIndexStuff
	} catch (Exception e) {
	  String message = "siteNav.jsp e:" + e + " trapped on target:" + HttpUtil.getTarget(request) + " taxon:" + taxon;
	  AntwebUtil.log(message);
	  if (AntwebProps.isDevMode()) AntwebUtil.logStackTrace(e);
	  //out.println(message);
	  //throw e;
	}
%>