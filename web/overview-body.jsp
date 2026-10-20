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
    // The field open in the editor, or "none" (DescEditSafety: after a refused save, the same field again).
    String editField = DescEditSafety.activeEditField(request);
    // Only curators and administrators see [Edit] links and the editor.
    boolean canEditDesc = DescEditSafety.canEdit(accessLogin);
        
    if (canEditDesc && !"none".equals(editField)) { %>
<%@ include file="/common/descEdit/editorScripts.jsp" %>
 <% } 

%>

<div id="page_contents">
<%@ include file="/common/descEdit/descEditNotice.jsp" %>

<%@ include file="/common/overviewHeading.jsp" %>

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
     out.println("<h2>" + org.calacademy.antweb.util.Html.esc(overview.getTitle()) + "</h2>");
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

<%
  // Each number is followed by what it counts (Brian Fisher, 2026-10-20). The rules are those of the
  // nightly counts: GeolocaleDb, ProjectDb, MuseumDb, BioregionDb, PlaceImageCountDb, CountDb.
  org.calacademy.antweb.home.PlaceImageCountDb.PlaceImages placeImages =
      (org.calacademy.antweb.home.PlaceImageCountDb.PlaceImages) request.getAttribute("placeImages");
  boolean isProjectOverview = (overview instanceof Project);
  String georank = isGeolocale ? geolocale.getGeorank() : null;
  boolean isListOnlyPlace = isGeolocale && !geolocale.isIsland() && ("region".equals(georank) || "subregion".equals(georank));
  String placeWord = "place";
  if (isGeolocale) {
    if (geolocale.isIsland()) placeWord = "island";
    else if ("adm1".equals(georank)) placeWord = "state or province";
    else if (georank != null) placeWord = georank;
  } else if (isBioregion) placeWord = "bioregion";
  else if (isMuseum) placeWord = "museum";
  else if (isProjectOverview) placeWord = "project";

  String specimensDef;
  if (isListOnlyPlace) specimensDef = "specimens of the species on this " + placeWord + "'s list, collected anywhere, not only here";
  else if (isGeolocale) specimensDef = "ant specimens collected here, identified to a species, morphospecies or indet name";
  else if (isBioregion) specimensDef = "ant specimens collected in this bioregion";
  else if (isMuseum) specimensDef = "ant specimens recorded as held by this museum";
  else specimensDef = "specimens, collected anywhere, of the species and subspecies on this project's list";

  int imagesShown = overview.getImageCount();
  int imagedShown = overview.getImagedSpecimenCount();
  if (placeImages != null) {
    imagesShown = placeImages.allImages;
    imagedShown = placeImages.imagedSpecimens;
  }
%>
<div id="place-stats"<% if (placeImages != null) { %> data-images="<%= placeImages.allImages %>" data-ant="<%= placeImages.antImages %>" data-labels="<%= placeImages.labelImages() %>" data-imaged="<%= placeImages.imagedSpecimens %>" data-label-only="<%= placeImages.labelOnlySpecimens %>"<% } %>>
<br><b>Specimens:</b>&nbsp;<%= Formatter.commaFormat(overview.getSpecimenCount()) %> <span class="stat_def">(<%= specimensDef %>)</span>
<% if (placeImages != null) { %>
<br><b>Imaged specimens:</b>&nbsp;<%= Formatter.commaFormat(imagedShown) %> <span class="stat_def">(specimens with at least one photo of the ant; label-only photos not counted)<% if (placeImages.labelOnlySpecimens > 0) { %> (<%= Formatter.commaFormat(placeImages.labelOnlySpecimens) %> more have only a label photo)<% } %></span>
<br><b>Images:</b>&nbsp;<%= Formatter.commaFormat(imagesShown) %><% if (imagesShown > 0) { %>: <span class="stat_def" id="image-breakdown"><%= org.calacademy.antweb.home.PlaceImageCountDb.breakdown(placeImages) %></span><% } %>
<% } else { %>
<% if (imagedShown != 0) { %>
<br><b>Imaged specimens:</b>&nbsp;<%= Formatter.commaFormat(imagedShown) %> <span class="stat_def">(specimens with at least one photo)</span>
<% } %>
<br><b>Images:</b>&nbsp;<%= Formatter.commaFormat(imagesShown) %> <span class="stat_def">(photos of these specimens, labels included)</span>
<% } %>
<br>

<br><b>Subfamilies:</b>&nbsp;<%= Formatter.commaFormat(overview.getSubfamilyCount()) %> <span class="stat_def">(subfamilies on this <%= placeWord %>'s list)</span>
<br><b>Genera:</b>&nbsp;<%= Formatter.commaFormat(overview.getGenusCount()) %> <span class="stat_def">(genera on this <%= placeWord %>'s list)</span>
<% String speciesCountLink = Formatter.commaFormat(overview.getSpeciesCount());
   if (overview instanceof Geolocale) {
     Geolocale g = (Geolocale) overview;
     if (LoginMgr.isAdmin(request)) speciesCountLink = "<a href='" + AntwebProps.getDomainApp() + "/utilData.do?action=countReport&num=" + g.getId() + "'>" + speciesCountLink + "</a>";
   }
%>
<br><b>Species/Subspecies:</b>&nbsp;<%= speciesCountLink %> <span class="stat_def">(species and subspecies on this <%= placeWord %>'s list, from any source, including morphospecies and indet names)</span>

<%
   if (overview.getValidSpeciesCount() > 0) {
%>
       <br><br><b>Valid Species/Subspecies:</b>&nbsp;<%=  Formatter.commaFormat(overview.getValidSpeciesCount()) %> <span class="stat_def">(currently valid, living (non-fossil) species and subspecies on the list)</span>
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
	  endemicCountHtml = "<br><b>&nbsp;&nbsp;Endemic:</b>&nbsp;" + endemicCountHtml
	    + ("adm1".equals(geolocale.getGeorank())
	        ? " <span class=\"stat_def\">(named species and subspecies, not morphospecies or fossils, found in this country only and on no other state or province's list)</span>"
	        : " <span class=\"stat_def\">(named species and subspecies, not morphospecies or fossils, on this " + placeWord + "'s list and on no other country's list)</span>");

	  String introducedCount = Formatter.commaFormat(overview.getIntroducedSpeciesCount());
	  introducedCountHtml = introducedCount;
	  if (overview.getIntroducedSpeciesCount() > 0) {
		int geolocaleId = ((Geolocale) overview).getId();
		if ("country".equals(geolocale.getGeorank()) || "adm1".equals(geolocale.getGeorank())) {
		  introducedCountHtml = "<a href='" + AntwebProps.getDomainApp() + "/introduced.do?geolocaleId=" + geolocaleId + "'>" + introducedCount + "</a>";  // * should be introducedCount but it is not calculated yet.
		}
	  }
	  introducedCountHtml = "<br><b>&nbsp;&nbsp;Introduced:</b>&nbsp;" + introducedCountHtml
	    + " <span class=\"stat_def\">(species known to have been introduced by people: on AntWeb's list of introduced ants and not native to this " + placeWord + "'s bioregion)</span>";
  }
  if (isBioregion) {
	  String endemicCount = Formatter.commaFormat(overview.getEndemicSpeciesCount());
	  endemicCountHtml = endemicCount;
	  if (overview.getEndemicSpeciesCount() > 0) {
		String bioregionName = ((Bioregion) overview).getName();
  	    endemicCountHtml = "<a href='" + AntwebProps.getDomainApp() + "/endemic.do?bioregionName=" + bioregionName + "'>" + endemicCount + "</a>";
	  }
	  endemicCountHtml = "<br><b>&nbsp;&nbsp;Endemic:</b>&nbsp;" + endemicCountHtml
	    + " <span class=\"stat_def\">(species and subspecies, morphospecies included, not fossils, on this bioregion's list and on no other bioregion's list)</span>";

	  String introducedCount = Formatter.commaFormat(overview.getIntroducedSpeciesCount());
	  introducedCountHtml = introducedCount;
	  if (overview.getIntroducedSpeciesCount() > 0) {
		String bioregionName = ((Bioregion) overview).getName();
  	    introducedCountHtml = "<a href='" + AntwebProps.getDomainApp() + "/introduced.do?bioregionName=" + bioregionName + "'>" + introducedCount + "</a>";
	  }
	  introducedCountHtml = "<br><b>&nbsp;&nbsp;Introduced:</b>&nbsp;" + introducedCountHtml
	    + " <span class=\"stat_def\">(species known to have been introduced by people: on AntWeb's list of introduced ants and not native to this bioregion)</span>";
  }
  %>
<%= endemicCountHtml %></b>
<%= introducedCountHtml %></b>
</div>
<style>#place-stats .stat_def { color: #666; font-size: 0.9em; }</style>

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
      // Settings of a project assigned to this curator (administrators get the link under Admin Info).
      if (overview instanceof Project && !LoginMgr.isAdmin(accessLogin)
          && SpeciesListAccess.canEdit(accessLogin, overview.getName())) { %>
        <br><br><a href="<%= AntwebProps.getDomainApp() %>/editProject.do?projectName=<%= Html.escAttr(Html.url(overview.getName())) %>">Edit project settings</a>
   <% }
      if (overview instanceof Museum) { %>
        <br><br><h2>Curator Info</h2>
        Museum counts are computed daily and may not reflect most recent specimen uploads.

	 <% if (LoginMgr.isAdmin(accessLogin)) { %>
          <br><b><%= overview.getRecalcButton(request) %></b> (admins: recounts this museum's specimens, images and taxa now instead of in the nightly run; takes a few minutes)
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

	   
         <br><b><%= overview.getRecalcButton(request) %></b> (Recalculate overview: <%= overview.getKeyStr() %> and make the charts. Update <%= overview.getTable() %> counts.)
         <% if ("geolocale".equals(overviewType)) { %>
         <br><b><%= overview.getGoogleMapFunctionLink() %></b><%= overview.getGoogleMapFunctionLinkDesc() %>
         <% } %>
         <br><b><a href='<%= AntwebProps.getDomainApp() %>/util.do?action=reloadAntwebMgr&name=<%= overviewType %>'>Reload Overview Manager</a></b> (Server-wide. Should not be needed. Takes up to 8 seconds.)
	     <br>
	<% }
    }

	%>

	<%    if (desc.get("author") != null) { %>
	<br>
	<h3><%= desc.get("author") %></h3>
	<%    }
    
    thisDesc = "contents"; 
    descHeader = "Contents"; 
    descNotes = ""; %>    
   <br><%@ include file="/common/descEdit/descEditFieldCK.jsp" %>

 <% thisDesc = "specimenImage1";
    descHeader = "Project Page Image 1"; %>
 <br><%@ include file="/common/descEdit/imageDescEditField.jsp" %>

 <% thisDesc = "specimenImage2";
    descHeader = "Project Page Image 2"; %>
 <%@ include file="/common/descEdit/imageDescEditField.jsp" %>

 <% thisDesc = "specimenImage3";
    descHeader = "Project Page Image 3"; %>
 <%@ include file="/common/descEdit/imageDescEditField.jsp" %>

 <% thisDesc = "authorImage";
    descHeader = "Author Image"; %>
 <%@ include file="/common/descEdit/imageDescEditField.jsp" %>

<%  thisDesc = "author"; 
    descHeader = "Author"; 
    descNotes = "";   %>
   <%@ include file="/common/descEdit/descEditFieldCK.jsp" %>

<%  thisDesc = "authorBio"; 
    descHeader = "Author Bio"; 
    descNotes = "";   %>
   <%@ include file="/common/descEdit/descEditFieldCK.jsp" %>

<% 

if (!HttpUtil.isOffline()) {

%>

<script src="//cdnjs.cloudflare.com/ajax/libs/d3/3.4.4/d3.min.js"></script>
<script src="<%= AntwebProps.getDomainApp() %><%= org.calacademy.antweb.util.Asset.url(application, "/chart/d3pie.min.js") %>"></script>

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
     <%@include file="/chart/pieChart.jsp" %>
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
    <%@include file="/chart/pieChart.jsp" %>
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
<input id="for_print" type="text" value="<%= org.calacademy.antweb.util.Html.esc(author) %>, (<%= year %>). AntWeb: Ants of <%= org.calacademy.antweb.util.Html.esc(title) %>. Available from: <%= org.calacademy.antweb.util.Html.esc(citeTarget) %>">
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

//A.log("mapType:" + mapType);
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