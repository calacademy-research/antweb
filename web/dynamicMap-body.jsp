<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="org.calacademy.antweb.Map" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.geolocale.	*" %>

<!-- dynamicMap-body.jsp -->

<%
// title, map, chosenList

   Map map = (Map) session.getAttribute("map");
   //A.log("dynamicMap-body.jsp map:" + map + " isMapLocalities:" + map.isMapLocalities());

   Taxon taxon = (Taxon) session.getAttribute("taxon");
   if (taxon == null) {
     AntwebUtil.log("dyanamicMap-body.jsp taxon not found");
     return;
   }
   //A.log("dynamicMap-body.jsp taxon:" + taxon.getClass());
   Specimen specimen = (Specimen) session.getAttribute("specimen");

   if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
   
   
   String the_page = HttpUtil.getTarget(request);
   String dagger = "";
   if (taxon.getIsFossil()) dagger = "&dagger;";
%>
<%@include file="/maps/googleMapPreInclude.jsp" %>
<%
    String object = "dynamic";
    String objectName = null;
    String mapType = "dynPage";

    Overview overview = OverviewMgr.getOverview(request);
    //if (overview == null) overview = ProjectMgr.getProject(Project.ALLANTWEBANTS);
    
    String rank = taxon.getRank();
    String taxonPrettyName = taxon.getPrettyName();

    if (taxonPrettyName == null || "".equals(taxonPrettyName)) AntwebUtil.log("dynamicMap-body.jsp taxonPrettyName:" + taxonPrettyName + " requestInfo:" + HttpUtil.getRequestInfo(request));

    boolean isSearch = false;
 
	String title = (String) session.getAttribute("title");

    String cacheNote = "";
    boolean justCached = false;
    if (map.getCached() != null) justCached = (AntwebUtil.secsSince(map.getCached()) < 60);

    //A.log("justCached:" + justCached + " date:" + map.getCached());
    if (!justCached) {
      cacheNote = "<b>*</b>This map was cached <font color=green>" + map.getCached() + ".</font>";
      if (LoginMgr.isCurator(request)) {
        cacheNote += " <a href='" + HttpUtil.getTarget(request) + "&refresh=true'>Refresh map</a>.";
      } else {
        cacheNote += " Log in as curator to refresh.";
      }
    }

    boolean isLocality = false;
    boolean isSpecimen = false;
    boolean isTaxon = request.getParameter("taxonName") != null;
    String link_params = "";

    boolean isGeolocale = false;
    String adm1Name = request.getParameter("adm1Name");
    String countryName = request.getParameter("countryName");
    if (!isTaxon && (adm1Name != null || countryName != null)) isGeolocale = true;
    
    boolean isMuseum = false;
    if (request.getParameter("museumCode") != null) isMuseum = true;

    A.log("dynamicMap-body.jsp mapType:" + mapType + " title:" + title); // if not null + " overview:" + overview.getTitle());
    
    //if ((rank == null) || (taxonPrettyName == null)) {
    String searchParam = (String) request.getParameter("searchMethod");
    if (searchParam != null) {
        isSearch = true;
        if (title == null) title = map.getTitle(); //"Map of Selected Results"; 
        mapType += "Search";     
    } else if (adm1Name != null || countryName != null) {
      if (countryName != null) {
        mapType += "Country";
        title = "Country: " + request.getParameter("countryName"); 
      }
      if (adm1Name != null) {
        mapType += "Adm1";
        title = "Adm1: " + request.getParameter("adm1Name");
        if (overview != null) title = "Adm1: " + overview.getTitle(); 
      } 
    } else if (request.getParameter("locality") != null) {
        isLocality = true;
        title = "Locality: " + request.getParameter("locality"); // sloppy. Not pretty title. The overview is Project:Allantwebants.
        mapType += "Locality";
    } else if (request.getParameter("collection") != null) {
        isLocality = true;
        title = "Collection: " + request.getParameter("collection"); 
        mapType += "Collection";
    } else if (request.getParameter("taxonName") != null) {
        String taxonName = request.getParameter("taxonName");
        //title = "Map of Taxon: " + taxonName; 
        mapType += "Taxon";
    } else if (request.getParameter("specimen") != null) {
        isSpecimen = true;
        String specimenCode = request.getParameter("specimen"); 
        taxon = specimen;
        title = "Map of Specimen: " + specimenCode; 
        if (specimen == null) A.log("dynamicMap-body.jsp null specimen:" + specimenCode);
        mapType += "Specimen";
    } else if (isMuseum) {
      // No need.
    } else {
        AntwebUtil.log("dynamicMap-body.jsp unrecognized queryString:" + HttpUtil.getQueryString(request));    
    }
   
    A.log("dynamicMap-body.jsp mapType:" + mapType + " title:" + title + " isLocality:" + isLocality + " fromSearch:" + isSearch); //  + " isGoogleMapFunction:" + map.getIsGoogleMapFunction());

    if (isLocality) {
%>
<input id="for_print" type="text" value="AntWeb. Available from: <%= HttpUtil.getFullJspTarget(request)%>">
<input id="for_web" type="text" value="URL: <%= HttpUtil.getFullJspTarget(request)%>">
<div id="page_contents">
	<h1><%= title %></h1>
	<span class="right"><b><a href="#" onclick="history.back(); return false;">Back</a></b></span>
	<div class="clear"></div>
	<div class="page_divider"></div>
</div>
<div id="page_data">
	<div id="map">
		<%@include file="/maps/googleMapInclude.jsp" %>
		<div class="clear"></div>
	</div>
</div>
<%
    } else if (isSearch) {
      //if (AntwebProps.isDevOrStageMode()) AntwebUtil.log("dynamicMap-body.jsp from search");
%>
	<div id="page_contents">
	<h1><%= title %></h1>
	<span class="right"><b><a href="#" onclick="history.back(); return false;">Back</a></b></span>
		<div class="clear"></div>
		<div class="page_divider"></div>
	</div>
	<div id="page_data">
		<div id="map">
			<%@include file="/maps/googleMapInclude.jsp" %>
			<div class="clear"></div>
		</div>
	</div>
<%
    } else if (isSpecimen) {
      if (AntwebProps.isDevOrStageMode()) AntwebUtil.log("dynamicMap-body.jsp from specimen");
%>

<input id="for_print" type="text" value="AntWeb. Available from: <%= HttpUtil.getFullJspTarget(request)%>">
<input id="for_web" type="text" value="URL: <%= HttpUtil.getFullJspTarget(request)%>">
<div id="page_contents">
<%@ include file="/common/taxonTitle.jsp" %>

        <div class="links">
            <ul>
                <li><a href="<%= AntwebProps.getDomainApp() %>/specimen.do?name=<%= specimen.getName() %>">Overview</a></li>                
                <li><a href="<%= AntwebProps.getDomainApp() %>/specimenImages.do?name=<%=specimen.getName() %>">Images</a></li>
<%
    if (taxon.hasMap()) {
        String params = "";
        if (overview != null) params = "&" + overview.getParams();
        
        if (Rank.SPECIMEN.equals(taxon.getRank())) {
          if (specimen.getName() != null) {
            link_params = "specimen=" + specimen.getName().toLowerCase() + params;
%>
              <li>Map</li>
<%        } else AntwebUtil.log("specimen-body.jsp specimenName:" + specimen.getName() + " overview:" + overview + " taxon:" + taxon);
        } else { %> 
              <li><a href="<%= AntwebProps.getDomainApp() %>/bigMap.do?<%= object %>=<%= objectName %><%= params %>">Map</a></li>
<%      }
    } %>
            </ul>
        </div>
        
        <div class="clear"></div>

<%
    String specimenPrettyName = specimen.getPrettyName();
    if (specimenPrettyName == null || "".equals(specimenPrettyName)) AntwebUtil.log("dynamicMap-body.jsp specimenPrettyName:" + specimenPrettyName + " requestInfo:" + HttpUtil.getRequestInfo(request));

%>
    <!--
        taxonName:<%= specimen.getTaxonName() %> 
        details:<%= specimen.getDetails() %>
        fullName:<%= specimen.getFullName() %>
        simpleName:<%= specimen.getSimpleName() %>
        prettyName:<%= specimenPrettyName %>
        fullName:<%= specimen.getFullName() %>
        name:<%= specimen.getName() %>
    -->
<%@ include file="/common/taxonomicHierarchy.jsp" %>
	
	
	<div id="page_data">
		<div id="map">
			<%@include file="/maps/googleMapInclude.jsp" %>
			<div class="clear"></div>
		</div>
	</div>
<%
    } else {  
      //A.log("Must be a taxon or Geolocale, or museum?");
%>

<input id="for_print" type="text" value="AntWeb. Available from: <%= HttpUtil.getFullJspTarget(request)%>">
<input id="for_web" type="text" value="URL: <%= HttpUtil.getFullJspTarget(request)%>">

<div id="page_contents">
    <% if (isGeolocale) { %>
        <br><h2><%= title %></h2>
    <% } else if (isMuseum) { %>
        <br><h2><%= title %></h2>    
    <% } else { // Taxon! %> 
        <%@ include file="/common/taxonTitle.jsp" %>
        <%
          taxon.generateBrowserParams(overview);
          if (AntwebProps.isDevMode()) AntwebUtil.log("dynamicMap.jsp taxon:" + taxon + " browserParams:" + taxon.getBrowserParams());
          String overviewDo = "description.do";
          if (taxon instanceof Specimen) overviewDo = "specimen.do";
          //if (overview instanceof Country) overviewDo = "country.do";
          if (taxon.toString() != null || "null".equals(taxon)) {
            //A.log("dynamicMap-body.jsp taxon:" + taxon + " overview:" + overview);      
        %>
        <div class="links">
            <ul>
                <li><a href="<%= overviewDo %>?<%= taxon.getBrowserParams() %>">Overview</a></li>

             <% if (taxon instanceof Specimen) { %>                
                <li><a href="specimenImages.do?name=<%= specimen.getCode() %>">Images</a></li>
             <% } else { %>
                <li><a href="browse.do?<%= taxon.getBrowserParams() %>"><%= Rank.getNextPluralRank(rank) %></a></li>
                <li><a href="images.do?<%= taxon.getBrowserParams() %>">Images</a></li>
             <% } %>             
                <li>Map</li>
            </ul>
        </div>
        <%@ include file="/common/viewInAntCat.jsp" %>
        <div class="clear"></div>

        <%@ include file="/common/taxonomicHierarchy.jsp" %>

       <% } %>
    <% } %>	
	 
    <div id="totals_and_tools_container">
        <div id="totals_and_tools">
            <!-- %@ include file="/common/pageToolsMap.jsp" % -->
            <!-- %@ include file="/common/kml_download.jsp" % -->
          <% if (true) { 
               boolean isGeolocaleFocus = "true".equals(request.getParameter("geolocaleFocus"));
               String url = HttpUtil.getTarget(request);
               if (!isGeolocaleFocus && (adm1Name != null || countryName != null)) {
                 url += "&geolocaleFocus=true";
            %>
            <br>To see a map of the <%= taxon.getPrettyName() %> in <%= title %>, click: <a href='<%= url %>'>here</a>
          <%   }
             } %>
           <%= cacheNote %>  
        </div>
    </div>

</div>

<div id="page_data">
    <div id="map">
        <%@include file="/maps/googleMapInclude.jsp" %>
        <div class="clear"></div>
    </div>

    <div class="clear"></div>

<br><br>
<a href="mailto:antweb@calacademy.org?subject=Regarding AntWeb page <%= the_page %>">See something amiss? Send us an email.</a>
</div>
<%
    }
%>
