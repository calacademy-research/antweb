<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import = "org.calacademy.antweb.*" %>
<%@ page import = "org.calacademy.antweb.util.*" %>
<%@ page import = "org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.Project" %>
<%@ page import="org.calacademy.antweb.upload.UploadFile" %>

<%@ page import="java.util.Hashtable" %>

<!-- overview-body.jsp -->
<%

    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

    java.util.Calendar today = java.util.Calendar.getInstance();
    int year = today.get(java.util.Calendar.YEAR);
  
    Overview overview = OverviewMgr.getOverview(request);
    String overviewType = overview.getTable();

    //A.log("overview-body.jsp overview:" + overview + " class:" + overview.getClass() ); // + " map:" + overview.getMap());

    Login accessLogin = LoginMgr.getAccessLogin(request);

    String mapType = "overview"; // Temporary. Used in googleMapInclude.jsp

    boolean isMuseum = (overview instanceof Museum);    
    boolean isGeolocale = (overview instanceof Geolocale);
    boolean isBioregion = (overview instanceof Bioregion);

    Hashtable desc = overview.getDescription();   
    //if (AntwebProps.isDevMode()) AntwebUtil.log("overview-body.jsp desc:" + desc);
    if (desc == null) desc = new Hashtable();

    String guiDefaultContent = AntwebProps.guiDefaultContent;  //AntwebProps.getProp("gui.default.content"); 
    String thisDesc = null;
    String descHeader = null;
    String descNotes = null;   

    boolean hasExtraPrivs = false;     

    String thisPageTarget = overview.getThisPageTarget();
    String editField = request.getParameter("editField");
    if (editField == null) editField = "none";
        
    if (accessLogin != null) { %>
<script type="text/javascript" src="<%= AntwebProps.getDomainApp() %>/ckeditor/ckeditor.js"></script>
 <% } 

%>

<div id="page_contents">

<%@ include file="common/overviewHeading.jsp" %>

<%
    String childrenList = "";
    if (LoginMgr.isCurator(request)) {
      childrenList = overview.getChildrenListDisplay("valid", "overview", null);
    } else {
      childrenList = overview.getChildrenListDisplay("live", "overview", null);
    }

  if (overview instanceof Geolocale) {
    out.println(childrenList);
  }
%>

<br><br>

    <div class="page_divider project"></div>
    
  <div id="page_data">

    <div id="overview_data">

<%
  if (isBioregion) {
    out.println(childrenList + "<br>");
  }
%>


<% if (isMuseum) { %>
  <% String museumName = overview.getName();
     mapType = "museum";
     out.println("<h2>" + overview.getTitle() + "</h2>");
   }

  Geolocale geolocale = null;
  if (overview instanceof Geolocale) {
    geolocale = (Geolocale) overview;
    if (!geolocale.getIsValid() || !geolocale.getIsLive()) {
        String invalidStr = "<font color=red>This location is ";
		if (!geolocale.getIsValid()) {
		 invalidStr += "Invalid";
	    }
		if (!geolocale.getIsLive()) {
		  if (!geolocale.getIsValid()) invalidStr += ", not live.";
		  else invalidStr += "Not Live.";
	    }
	    
        Geolocale validGeolocale = null;
        if ("country".equals(geolocale.getGeorank()) || geolocale.isIsland()) {
          validGeolocale = GeolocaleMgr.getValidCountry(geolocale.getValidName());
        } else {
          validGeolocale = GeolocaleMgr.getValidAdm1(geolocale.getParent(), geolocale.getValidName());
        }

        invalidStr += "</font>";
        invalidStr += "<br>";        
        out.println(invalidStr);
    }
  }

   if (isGeolocale) {
     if (geolocale.getValidName() != null) {
       Geolocale validNameGeolocale = null;
       if (geolocale instanceof Country) {
         validNameGeolocale = GeolocaleMgr.getCountry(geolocale.getValidName());
       } else if (geolocale instanceof Adm1) {
         validNameGeolocale = GeolocaleMgr.getAdm1(geolocale.getValidName(), geolocale.getParent());
       }
       if (validNameGeolocale != null) out.println("<b>Valid Name:</b>&nbsp;<a href='" + AntwebProps.getDomainApp() + "/geolocale.do?id=" + validNameGeolocale.getId() + "'>" + validNameGeolocale.getName() + "</a><br>");
     }
     
     if (!"".equals(geolocale.getAlternatives())) out.println("<b>Alternative Place Names:</b>&nbsp;" + geolocale.getAlternatives());

     mapType = "geolocale";
     if ("country".equals(geolocale.getGeorank())) {
       if (geolocale.isIsland()) {
            mapType = "Island";
       } else {
            mapType = "country";
       }
     } else {
       mapType = "adm1";
     }
   }
%>

<br><b>Specimens:</b>&nbsp;<%=  Formatter.commaFormat(overview.getSpecimenCount()) %>
<br><b>Images:</b>&nbsp;<%=  Formatter.commaFormat(overview.getImageCount()) %>

<% if (overview.getImagedSpecimenCount() != 0) { %>
<br><b>Imaged Specimens:</b>&nbsp;<%=  Formatter.commaFormat(overview.getImagedSpecimenCount()) %>
<% } %>

<br>

<br><b>Subfamilies:</b>&nbsp;<%= Formatter.commaFormat(overview.getSubfamilyCount()) %>
<br><b>Genera:</b>&nbsp;<%= Formatter.commaFormat(overview.getGenusCount()) %>
<% String speciesCountLink = Formatter.commaFormat(overview.getSpeciesCount());
   if (overview instanceof Geolocale) {
     Geolocale g = (Geolocale) overview;
     if (LoginMgr.isAdmin(request)) speciesCountLink = "<a href='" + AntwebProps.getDomainApp() + "/utilData.do?action=countReport&num=" + g.getId() + "'>" + speciesCountLink + "</a>";
   }
%>
<br><b>Species/Subspecies:</b>&nbsp;<%= speciesCountLink %>

<%
   if (overview.getValidSpeciesCount() > 0) {
%>
       <br><br><b>Valid Species/Subspecies:</b>&nbsp;<%=  Formatter.commaFormat(overview.getValidSpeciesCount()) %>
       <br>
<% } %>



<% 
   String endemicCountHtml = "";
   String introducedCountHtml = "";
  //A.log("overview-body.jsp geolocale:" + ((Geolocale)overview).getId());

  if (isGeolocale && ("country".equals(geolocale.getGeorank()) || "adm1".equals(geolocale.getGeorank())) ) {
	  String endemicCount = Formatter.commaFormat(overview.getEndemicSpeciesCount());
	  endemicCountHtml = endemicCount;
	  if (overview.getEndemicSpeciesCount() > 0) {
		int geolocaleId = ((Geolocale) overview).getId();
		if ("country".equals(geolocale.getGeorank()) || "adm1".equals(geolocale.getGeorank())) {
		  endemicCountHtml = "<a href='" + AntwebProps.getDomainApp() + "/endemic.do?geolocaleId=" + geolocaleId + "'>" + endemicCount + "</a>";  
		}
	  }
	  endemicCountHtml = "<br><b>&nbsp;&nbsp;Endemic:</b>&nbsp;" + endemicCountHtml;

	  String introducedCount = Formatter.commaFormat(overview.getIntroducedSpeciesCount());
	  introducedCountHtml = introducedCount;
	  if (overview.getIntroducedSpeciesCount() > 0) {
		int geolocaleId = ((Geolocale) overview).getId();
		if ("country".equals(geolocale.getGeorank()) || "adm1".equals(geolocale.getGeorank())) {
		  introducedCountHtml = "<a href='" + AntwebProps.getDomainApp() + "/introduced.do?geolocaleId=" + geolocaleId + "'>" + introducedCount + "</a>";  // * should be introducedCount but it is not calculated yet.
		}
	  }
	  introducedCountHtml = "<br><b>&nbsp;&nbsp;Introduced:</b>&nbsp;" + introducedCountHtml;
  }
  if (isBioregion) {
	  String endemicCount = Formatter.commaFormat(overview.getEndemicSpeciesCount());
	  endemicCountHtml = endemicCount;
	  if (overview.getEndemicSpeciesCount() > 0) {
		String bioregionName = ((Bioregion) overview).getName();
  	    endemicCountHtml = "<a href='" + AntwebProps.getDomainApp() + "/endemic.do?bioregionName=" + bioregionName + "'>" + endemicCount + "</a>";
	  }
	  endemicCountHtml = "<br><b>&nbsp;&nbsp;Endemic:</b>&nbsp;" + endemicCountHtml;

	  String introducedCount = Formatter.commaFormat(overview.getIntroducedSpeciesCount());
	  introducedCountHtml = introducedCount;
	  if (overview.getIntroducedSpeciesCount() > 0) {
		String bioregionName = ((Bioregion) overview).getName();
  	    introducedCountHtml = "<a href='" + AntwebProps.getDomainApp() + "/introduced.do?bioregionName=" + bioregionName + "'>" + introducedCount + "</a>";
	  }
	  introducedCountHtml = "<br><b>&nbsp;&nbsp;Introduced:</b>&nbsp;" + introducedCountHtml;
  }
  %>
<%= endemicCountHtml %></b>
<%= introducedCountHtml %></b>

<!-- h3>Last Calculation:&nbsp;< %= overview.getCreated() % ></h3 -->


<% 
//A.log("*");
   // The Show All Specimen advanced search link.
   if (!HttpUtil.isBot(request) && isGeolocale) {
     String clause = null;
     if (overview instanceof LocalityOverview) {
       clause = "&family=formicidae&" + ((LocalityOverview) overview).getSearchCriteria();
     }
     
     if (clause != null) {
		 String specimenSearchUrl = "/advancedSearch.do?searchMethod=advancedSearch&advanced=true" + clause + "&resultRank=specimen";
         String speciesSearchUrl = specimenSearchUrl + "&resultRank=species";
     	 out.println("<br><br>");
     	 
     	 //if (LoginMgr.isAdmin(request)) { 
		 //  out.println("<b><a href='" + AntwebProps.getDomainApp() + speciesSearchUrl + "'>Show All Species<a></b> | ");     
         //}
		 out.println("<b><a href='" + AntwebProps.getDomainApp() + specimenSearchUrl + "'>Show All Specimens<a></b>");     
     }
   } %>

<%
  if (HttpUtil.isOffline()) { %>
<br>Taxa:
		<ul>
			<li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=subfamily&<%= overview.getParams() %>"><span class="numbers"></span> Subfamilies</a></li>
			<li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=genus&<%= overview.getParams() %>"><span class="numbers"></span> Genera</a></li> 
			<li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=species&<%= overview.getParams() %>"><span class="numbers"></span> Species</a></li> 
		</ul>
Images:
		<ul>
			<li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=subfamily&<%= overview.getParams() %>&images=true">Subfamilies</a></li>  
			<li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=genus&<%= overview.getParams() %>&images=true">Genera</a></li> 

		  <% if (overview.getSpeciesCount() < maxSpeciesCount) { // || LoginMgr.isCurator(request)) { %>                            
			<li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=species&<%= overview.getParams() %>&images=true">Species</a></li> 
		  <% } else {  %>
			<li><%= Formatter.commaFormat(overview.getSpeciesCount()) %> Species</li>   <!--span class="numbers"></span -->
		  <% } %>                          
		</ul>
<% 
  }
  
  if (isMuseum) {
    Museum museum = (Museum) overview;
    String code = museum.getCode();
    
  		 out.println("<br><br><b>List of species with types at the museum: <a href='" + AntwebProps.getDomainApp() + "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&ownedBy=" + code + "&type=type&resultRank=species&output=list'>here</a>");
		 out.println("<br><b>List of type specimens from the museum: <a href='" + AntwebProps.getDomainApp() + "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&ownedBy=" + code + "&type=type&resultRank=specimen&output=list'>here</a>");
  }

    // For the notes field
    String targetDo = overview.getTargetDo();
    String objectName = overview.getName();  // Used?

    if (LoginMgr.isCurator(request)) {
      if (overview instanceof Museum) { %>
        <br><br><h2>Curator Info</h2>
        Museum counts are computed daily and may not reflect most recent specimen uploads.

	 <% if (LoginMgr.isPeter(request)) { %>
          <br><b><%= overview.getRecalcLink() %></b> (Includes count crawl. Recalculate overview: <%= overview.getKeyStr() %> and make the charts. Update <%= overview.getTable() %> counts.)
     <% }

      }
    }

	if (LoginMgr.isAdmin(accessLogin)) { %>
	<br><br>
	<h2>Admin Info</h2>
	<%
       if (overview instanceof Project) { %>
         <a href="<%= AntwebProps.getDomainApp() %>/editProject.do?projectName=<%= overview.getName() %>">Edit Project</a><br>
    <% }
       if (overview instanceof Geolocale) { %>
         ID:<%= ((Geolocale) overview).getId() %>
         <br><a href="<%= AntwebProps.getDomainApp() %>/editGeolocale.do?id=<%= ((Geolocale) overview).getId() %>">Edit Geolocale</a><br>
    <% }

	   if (isGeolocale) {
		 out.println("Parent: " + geolocale.getParent());
         if (geolocale.isIsland()) {
        	out.println("<br>Country: " + geolocale.getCountry());
         }
		 out.println("<br>Bioregion: " + geolocale.getBioregion());
		 out.println("<br>Alt Bioregion: " + geolocale.getAltBioregion());
		 out.println("<br><b>Geo Data</b>");
		 out.println("<br>&nbsp;&nbsp;Centroid: " + geolocale.getCentroid());
		 out.println("<br>&nbsp;&nbsp;Centroid (Fixed): " + geolocale.getCentroidFixed());
		 out.println("<br>&nbsp;&nbsp;&nbsp;&nbsp;<b>Use</b>: " + geolocale.useCentroid());
		 out.println("<br>&nbsp;&nbsp;Bounding Box: " + geolocale.getBoundingBox());
		 out.println("<br>&nbsp;&nbsp;Bounding Box (Fixed): " + geolocale.getBoundingBoxFixed());
		 out.println("<br>&nbsp;&nbsp;&nbsp;&nbsp;<b>Use</b>: " + geolocale.useBoundingBox());
		 //out.println("<br><b>Admin Notes</b>: " + geolocale.getAdminNotes());
	   }

	   if (LoginMgr.isAdmin(request)) { %>

         <% if ("project".equals(overviewType)) { %>
         <br><b><a href='<%= AntwebProps.getDomainApp() %>/utilData.do?action=projTaxonChildCountCrawl&name=<%= overview.getName() %>'>Project Taxon Child Count Crawl</a></b>
         <% } %>
	   
         <br><b><%= overview.getRecalcLink() %></b> (Recalculate overview: <%= overview.getKeyStr() %> and make the charts. Update <%= overview.getTable() %> counts.)
         <% if ("geolocale".equals(overviewType)) { %>
         <br><b><%= overview.getGoogleMapFunctionLink() %></b><%= overview.getGoogleMapFunctionLinkDesc() %>
         <% } %>
         <br><b><a href='<%= AntwebProps.getDomainApp() %>/util.do?action=reloadAntwebMgr&name=<%= overviewType %>'>Reload Overview Manager</a></b> (Server-wide. Should not be needed. Takes up to 8 seconds.)
	     <br>
	<% }
    }

	if (LoginMgr.isDeveloper(accessLogin)) { %>
	<br>
	<h2>Dev Info</h2>

	<!-- Class:<%= overview.getClass().getName() %> -->
	<%
	  if (isGeolocale) {
		out.println("<br>Prefer Link: <a href='" + geolocale.getThisPageTarget() + "'> /" + geolocale.getThisPageTarget() + "</a>");
		out.println("<br>Alt Link: <a href='" + geolocale.getAltThisPageTarget() + "'> /" + geolocale.getAltThisPageTarget() + "</a>");
	  }
	} %>

	<%    if (desc.get("author") != null) { %>
	<br>
	<h3><%= desc.get("author") %></h3>
	<%    }
    
    thisDesc = "contents"; 
    descHeader = "Contents"; 
    descNotes = ""; %>    
   <br><%@ include file="common/descEdit/descEditFieldCK.jsp" %>

 <% thisDesc = "specimenImage1";
    descHeader = "Project Page Image 1"; %>
 <br><%@ include file="common/descEdit/imageDescEditField.jsp" %>

 <% thisDesc = "specimenImage2";
    descHeader = "Project Page Image 2"; %>
 <%@ include file="common/descEdit/imageDescEditField.jsp" %>

 <% thisDesc = "specimenImage3";
    descHeader = "Project Page Image 3"; %>
 <%@ include file="common/descEdit/imageDescEditField.jsp" %>

 <% thisDesc = "authorImage";
    descHeader = "Author Image"; %>
 <%@ include file="common/descEdit/imageDescEditField.jsp" %>

<%  thisDesc = "author"; 
    descHeader = "Author"; 
    descNotes = "";   %>
   <%@ include file="common/descEdit/descEditFieldCK.jsp" %>

<%  thisDesc = "authorBio"; 
    descHeader = "Author Bio"; 
    descNotes = "";   %>
   <%@ include file="common/descEdit/descEditFieldCK.jsp" %>

<% 

if (!HttpUtil.isOffline()) {

%>

<script src="//cdnjs.cloudflare.com/ajax/libs/d3/3.4.4/d3.min.js"></script>
<script src="<%= AntwebProps.getDomainApp() %>/chart/d3pie.min.js"></script>

<%
   String jsonData = overview.getTaxonSubfamilyDistJson();
   //AntwebUtil.log("overview-body.jsp 1 jsonData:" + jsonData);
   if (jsonData != null && !"".equals(jsonData)) {

     String title = "Species per Subfamily 'Pie Chart'";
     String subtitle = ""; //Distribution of Specimen";
     String footer = ""; //Source: Data submitted to Antweb.org";
     
     // if (AntwebProps.isDevMode()) AntwebUtil.log("overview-body.jsp jsonData1:" + jsonData); 

     if (!jsonData.contains("\"color\": \"null\"")) {    
%>
     <%@include file="chart/pieChart.jsp" %>
<% 
     } else {
       //AntwebUtil.log("overview-body.jsp color is not null for overview:" + overview);
     }
   } %>


<%
   jsonData = overview.getSpecimenSubfamilyDistJson();
   //AntwebUtil.log("overview-body.jsp 2 jsonData:" + jsonData);
   if (jsonData != null && !"".equals(jsonData)) {
   
     String title = "Specimens per Subfamily 'Pie Chart'";
     String subtitle = ""; //Distribution of Specimen";
     String footer = ""; //Source: Data submitted to Antweb.org";

     //if (AntwebProps.isDevMode()) AntwebUtil.log("museum-body.jsp jsonData2:" + jsonData);
     if (!jsonData.contains("\"color\": \"null\"")) {    
%>        
    <%@include file="chart/pieChart.jsp" %>
<%   }
   }
} // if !offline

String author = ""; 
String title = "";
String citeTarget = overview.getThisPageTarget();
if (overview instanceof Project) {
  Project project = (Project) overview;
  author = ((Project) overview).getAuthor();
  title = ((Project) overview).getTitle();
  mapType = "project";
} else {
  title = overview.getTitle(); //getName();
}
if (overview instanceof Bioregion) {
  mapType = "bioregion";
}
if (overview instanceof Geolocale) {
  author = ((Geolocale) overview).getAuthor();
}

%>
<input id="for_print" type="text" value="<%= author %>, (<%= year %>). AntWeb: Ants of <%= title %>. Available from: <%= citeTarget %>">
<input id="for_web" type="text" value="URL: <%= citeTarget %>">
<% // } %>

    </div> <!-- end page_contents -->

<%
// Use a Map object if defined (stored in object_map and loaded on server).
//   Otherwise use the centroid to map.
//     Otherwise use a map image.
if (!HttpUtil.isBot(request)) {
    //A.log("overview-body.jsp 1 overview:" + overview);

    if (overview instanceof LocalityOverview) {

      LocalityOverview localityOverview = (LocalityOverview) overview;
      Map map = localityOverview.getMap();

      // causes npe on server...
      //if (overview != null) A.log("overview-body.jsp 2 overview:" + overview);
      //A.log("overview-body.jsp 3 map:" + map);
      if (map != null) {
        //if (map.getGoogleMapFunction() != null) A.log("overview-body.jsp len:" + map.getGoogleMapFunction().length());

        String object = "localityOverview";
        if ("overview".equals(mapType)) {
          // Then we failed to assign the specific mapType.
          AntwebUtil.log("overview-body.jsp overview:" + mapType + " name:" + overview.getName());
        }
        //objectName is set above	

A.log("mapType:" + mapType);
    %>
        <div class="left">
          <div class="small_map">
            <%@include file="/maps/googleMapPreInclude.jsp" %>  
            <%@include file="/maps/googleMapInclude.jsp" %>  
          </div>
        </div>
    <%  
      } else {

          String centroid = localityOverview.useCentroid();
          if (centroid != null  && !"".equals(centroid)) {
            //A.log("overview-body with centroid:" + centroid);
            LocalityOverview mapOverview = localityOverview; %>        
            <%@include file="/maps/includeMap.jsp" %>
       <% } else { 
            String mapImg = localityOverview.getMapImage();

            //A.log("overview-body map with mapImg:" + mapImg);
            if (mapImg != null && !"".equals(mapImg)) { %>
               <img src="<%= AntwebProps.getDomainApp() %>/image/<%= mapImg %>">
         <% }
          } 

      }
    }
} else {
// do nothing for bots.
}
%>

   </div> <!-- page_data -->
   
</div> <!-- overview_data -->

<%
 if (false && LoginMgr.isAdmin(request)) {
//A.log("Hi!");
 %>
(Admin only) Data flow : this page displays data from the geolocale_taxon table.
<% } %>