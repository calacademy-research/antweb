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
  // Port of Entry (Brian Fisher, 2026-10-22): a species list only, without specimen and image numbers (Project.isSpeciesListOnly).
  boolean isSpeciesListOnly = isProjectOverview && ((Project) overview).isSpeciesListOnly();
  String georank = isGeolocale ? geolocale.getGeorank() : null;
  boolean isListOnlyPlace = isGeolocale && geolocale.isCountedFromCountries();   // region or subregion
  String placeWord = "place";
  if (isGeolocale) {
    if (geolocale.isIsland()) placeWord = "island";
    else if ("adm1".equals(georank)) placeWord = "state or province";
    else if (georank != null) placeWord = georank;
  } else if (isBioregion) placeWord = "bioregion";
  else if (isMuseum) placeWord = "museum";
  else if (isProjectOverview) placeWord = "project";

  int imagesShown = overview.getImageCount();
  int imagedShown = overview.getImagedSpecimenCount();
  if (placeImages != null) {
    imagesShown = placeImages.allImages;
    imagedShown = placeImages.imagedSpecimens;
  }
%>
<%
  // Statistics table (Brian Fisher, 2026-10-20, "AntWeb Country Pages"): four sections, each number with
  // its definition. Rules are those of the nightly counts (GeolocaleDb, ProjectDb, MuseumDb, BioregionDb,
  // PlaceImageCountDb, CountDb). Each row carries data-stat and data-n for the tests.
  String pw = placeWord;
  boolean isAdm1 = isGeolocale && "adm1".equals(georank);
  String listName = "this " + pw + "'s AntWeb list";
  String speciesCount = Formatter.commaFormat(overview.getSpeciesCount());
  String speciesCell = speciesCount;
  if (overview instanceof Geolocale && LoginMgr.isAdmin(request)) {
    speciesCell = "<a href='" + AntwebProps.getDomainApp() + "/utilData.do?action=countReport&num=" + ((Geolocale) overview).getId() + "'>" + speciesCount + "</a>";
  }
  String introducedListLink = "<a href='" + AntwebProps.getDomainApp() + "/taxonomicPage.do?rank=species&project=introducedants'>introduced-species list</a>";

  boolean showEndemism = isBioregion || (isGeolocale && ("country".equals(georank) || isAdm1));
  String endemicCell = Formatter.commaFormat(overview.getEndemicSpeciesCount());
  String introducedCell = Formatter.commaFormat(overview.getIntroducedSpeciesCount());
  String endemicDef = "";
  String introducedDef = "";
  if (showEndemism) {
    if (isBioregion) {
      String bn = ((Bioregion) overview).getName();
      if (overview.getEndemicSpeciesCount() > 0) endemicCell = "<a href='" + AntwebProps.getDomainApp() + "/endemic.do?bioregionName=" + bn + "'>" + endemicCell + "</a>";
      if (overview.getIntroducedSpeciesCount() > 0) introducedCell = "<a href='" + AntwebProps.getDomainApp() + "/introduced.do?bioregionName=" + bn + "'>" + introducedCell + "</a>";
      endemicDef = "Species and subspecies, including morphospecies, recorded for this bioregion and no other bioregion in AntWeb. Excludes fossils and names not identified to genus. Endemism is calculated from AntWeb records only.";
      introducedDef = "Species introduced by humans that are not native to this bioregion, based on AntWeb's " + introducedListLink + ".";
    } else {
      int gid = ((Geolocale) overview).getId();
      if (overview.getEndemicSpeciesCount() > 0) endemicCell = "<a href='" + AntwebProps.getDomainApp() + "/endemic.do?geolocaleId=" + gid + "'>" + endemicCell + "</a>";
      if (overview.getIntroducedSpeciesCount() > 0) introducedCell = "<a href='" + AntwebProps.getDomainApp() + "/introduced.do?geolocaleId=" + gid + "'>" + introducedCell + "</a>";
      endemicDef = isAdm1
        ? "Named species and subspecies recorded for this state or province and no other state or province in AntWeb. Excludes morphospecies, fossils and names not identified to genus. Endemism is calculated from AntWeb records only."
        : "Named species and subspecies recorded for this country and no other country in AntWeb. Excludes morphospecies, fossils and names not identified to genus. Endemism is calculated from AntWeb records only.";
      introducedDef = "Species introduced by humans that are not native to this " + pw + "'s bioregion, based on AntWeb's " + introducedListLink + ".";
    }
  }

  // Regions and subregions (since 2026-10-20): the specimens counted on the pages of their countries, each once
  // (Geolocale.regionSpecimensSql). Countries, islands and states: only specimens under a name on the list; the
  // "Show Specimen Taxa" view of the species list (Geolocale.isCanShowSpecimenTaxa) shows the other names.
  String specimensFullDef;
  if (isListOnlyPlace) specimensFullDef = "Ant specimens collected in this " + pw + "'s countries and identified to a name on their lists; each specimen counted once.";
  else if (isGeolocale) specimensFullDef = "Ant specimens collected in this " + pw + " and identified to a species, morphospecies, or unidentified taxon on " + listName + ".";
  else if (isBioregion) specimensFullDef = "Ant specimens collected in this bioregion.";
  else if (isMuseum) specimensFullDef = "Ant specimens recorded as held by this museum.";
  else specimensFullDef = "Specimens, collected anywhere, of the species and subspecies on this project's list.";
  String fromWhere = isMuseum ? "held by this museum" : (isProjectOverview ? "of this project's species" : "from this " + pw);

  String specimenListLink = null;
  if (!HttpUtil.isBot(request) && overview instanceof LocalityOverview) {
    String listCriteria = ((LocalityOverview) overview).getSearchCriteria();
    if (listCriteria != null && !"".equals(listCriteria)) {
      specimenListLink = "<a rel=\"nofollow\" href=\"" + AntwebProps.getDomainApp() + "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=formicidae&" + listCriteria + "&resultRank=specimen\">List all specimens collected in " + Html.esc(overview.getName()) + "</a> (every record, under any name, including those not counted above)";
    }
  }
  // Countries, islands and states: every name their specimens are filed under (Show Specimen Taxa of the species list).
  if (specimenListLink != null && isGeolocale && geolocale.isCanShowSpecimenTaxa()) {
    String listParams = isAdm1
        ? "countryName=" + Html.url(geolocale.getParent()) + "&adm1Name=" + Html.url(geolocale.getName())
        : "countryName=" + Html.url(geolocale.getName());
    String specimenTaxaUrl = AntwebProps.getDomainApp() + "/taxonomicPage.do?rank=species&" + listParams + "&specimen=true";
    specimenListLink += "<br>Names of all specimens collected here, including those not on the list: <a id=\"specimen-taxa-link\" href=\"" + Html.escAttr(specimenTaxaUrl) + "\">Show Specimen Taxa</a>.";
  }
%>
<div id="place-stats"<% if (placeImages != null && !isSpeciesListOnly) { %> data-images="<%= placeImages.allImages %>" data-ant="<%= placeImages.antImages %>" data-labels="<%= placeImages.labelImages() %>" data-imaged="<%= placeImages.imagedSpecimens %>" data-label-only="<%= placeImages.labelOnlySpecimens %>"<% } %>>
<table class="place_stats">
<tr class="ps_head"><th colspan="3">Taxonomic diversity</th></tr>
<tr data-stat="subfamilies" data-n="<%= overview.getSubfamilyCount() %>"><td class="ps_name">Subfamilies</td><td class="ps_num"><%= Formatter.commaFormat(overview.getSubfamilyCount()) %></td><td class="ps_def">Subfamilies recorded on <%= listName %>.</td></tr>
<tr data-stat="genera" data-n="<%= overview.getGenusCount() %>"><td class="ps_name">Genera</td><td class="ps_num"><%= Formatter.commaFormat(overview.getGenusCount()) %></td><td class="ps_def">Genera recorded on <%= listName %>.</td></tr>
<tr data-stat="species" data-n="<%= overview.getSpeciesCount() %>"><td class="ps_name">Species / subspecies</td><td class="ps_num"><%= speciesCell %></td><td class="ps_def">All species and subspecies recorded on <%= listName %>, from any source, including valid names, morphospecies, and indetermined species. Names not identified to genus are not counted as species; their specimens are counted under Specimens.</td></tr>
<% if (overview.getValidSpeciesCount() > 0) { %>
<tr data-stat="valid" data-n="<%= overview.getValidSpeciesCount() %>"><td class="ps_name">Valid species / subspecies</td><td class="ps_num"><%= Formatter.commaFormat(overview.getValidSpeciesCount()) %></td><td class="ps_def">Currently valid, formally named, living (non-fossil) species and subspecies recorded on <%= listName %>. Excludes morphospecies and unidentified taxa.</td></tr>
<% } %>
<% if (showEndemism) { %>
<tr class="ps_head"><th colspan="3">Endemism and introduced species</th></tr>
<tr data-stat="endemic" data-n="<%= overview.getEndemicSpeciesCount() %>"><td class="ps_name">Endemic</td><td class="ps_num"><%= endemicCell %></td><td class="ps_def"><%= endemicDef %></td></tr>
<tr data-stat="introduced" data-n="<%= overview.getIntroducedSpeciesCount() %>"><td class="ps_name">Introduced</td><td class="ps_num"><%= introducedCell %></td><td class="ps_def"><%= introducedDef %></td></tr>
<% } %>
<% if (isSpeciesListOnly) { %>
<tr class="ps_head"><th colspan="3">About this list</th></tr>
<tr data-stat="species-list-only"><td colspan="3" class="ps_def" id="port-of-entry-note">These are ant species that have been intercepted at ports of entry (for example in cargo or plant shipments). They are not established introductions, and they are not counted in any place total or in the introduced species list. This page lists the species only; it shows no specimen or image numbers.</td></tr>
<% } else { %>
<tr class="ps_head"><th colspan="3">Specimen holdings</th></tr>
<tr data-stat="specimens" data-n="<%= overview.getSpecimenCount() %>"><td class="ps_name">Specimens</td><td class="ps_num"><%= Formatter.commaFormat(overview.getSpecimenCount()) %></td><td class="ps_def"><%= specimensFullDef %><% if (specimenListLink != null) { %><br><%= specimenListLink %><% } %></td></tr>
<% if (placeImages != null) { %>
<tr data-stat="imaged" data-n="<%= placeImages.imagedSpecimens %>"><td class="ps_name">Imaged specimens</td><td class="ps_num"><%= Formatter.commaFormat(placeImages.imagedSpecimens) %></td><td class="ps_def">Specimens with at least one image of the ant.<% if (placeImages.labelOnlySpecimens > 0) { %> Excludes specimens with only label images (<span data-label-only-shown="<%= placeImages.labelOnlySpecimens %>"><%= Formatter.commaFormat(placeImages.labelOnlySpecimens) %></span> additional specimens).<% } %></td></tr>
<tr class="ps_head"><th colspan="3">Image documentation</th></tr>
<%-- 2026-10-27 (Brian Fisher): "Images" means images of the ant everywhere (ImageCounts); every file is "Image files". --%>
<tr data-stat="images" data-n="<%= placeImages.allImages %>"><td class="ps_name">Image files</td><td class="ps_num"><%= Formatter.commaFormat(placeImages.allImages) %></td><td class="ps_def">Every image file of specimens <%= fromWhere %>: images of the ant and label images.</td></tr>
<tr data-stat="ant-images" data-n="<%= placeImages.antImages %>" class="ps_sub"><td class="ps_name">Images</td><td class="ps_num"><%= Formatter.commaFormat(placeImages.antImages) %></td><td class="ps_def" id="image-breakdown">Images showing the ant<%= org.calacademy.antweb.home.PlaceImageCountDb.antViews(placeImages) %>.</td></tr>
<tr data-stat="label-images" data-n="<%= placeImages.labelImages() %>" class="ps_sub"><td class="ps_name">Label images</td><td class="ps_num"><%= Formatter.commaFormat(placeImages.labelImages()) %></td><td class="ps_def">Images showing specimen labels rather than the ant.</td></tr>
<% } else { %>
<% if (imagedShown != 0) { %>
<tr data-stat="imaged" data-n="<%= imagedShown %>"><td class="ps_name">Imaged specimens</td><td class="ps_num"><%= Formatter.commaFormat(imagedShown) %></td><td class="ps_def">Specimens with at least one image (labels included until tonight's update).</td></tr>
<% } %>
<tr class="ps_head"><th colspan="3">Image documentation</th></tr>
<tr data-stat="images" data-n="<%= imagesShown %>"><td class="ps_name">Image files</td><td class="ps_num"><%= Formatter.commaFormat(imagesShown) %></td><td class="ps_def">Every image file of these specimens, labels included (images of the ant and label images appear apart after tonight's update).</td></tr>
<% } %>
<% } // not a species list only %>
</table>
</div>
<style>
#place-stats .place_stats { border-collapse: collapse; margin: 10px 0; width: 100%; max-width: 760px; }
#place-stats .place_stats th { text-align: left; padding: 10px 6px 4px; font-size: 1.05em; border-bottom: 1px solid #ccc; }
#place-stats .place_stats td { padding: 4px 6px; vertical-align: top; border-bottom: 1px solid #eee; }
#place-stats .ps_name { font-weight: bold; white-space: nowrap; }
#place-stats .ps_num { text-align: right; white-space: nowrap; font-variant-numeric: tabular-nums; }
#place-stats .ps_def { color: #555; font-size: 0.92em; }
#place-stats .ps_sub .ps_name { font-weight: normal; padding-left: 20px; }
@media (max-width: 600px) { #place-stats .ps_name { white-space: normal; } }
</style>



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
    
  		 out.println("<br><br><b>List of species with types at the museum: <a rel='nofollow' href='" + AntwebProps.getDomainApp() + "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&ownedBy=" + code + "&type=type&resultRank=species&output=list'>here</a>");
		 out.println("<br><b>List of type specimens from the museum: <a rel='nofollow' href='" + AntwebProps.getDomainApp() + "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&ownedBy=" + code + "&type=type&resultRank=specimen&output=list'>here</a>");
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

	   
         <br><b><%= overview.getRecalcButton(request) %></b> (recalculates all statistics on this page, including specimen and image counts, and redraws the charts using current records; also runs automatically each night; takes a minute or two)
         <% if ("geolocale".equals(overviewType)) { %>
         <br><b><%= overview.getGoogleMapFunctionLink() %></b><%= overview.getGoogleMapFunctionLinkDesc() %>
         <% } %>
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
   if (jsonData != null && !"".equals(jsonData) && !isSpeciesListOnly) {
   
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