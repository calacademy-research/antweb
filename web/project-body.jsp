<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import = "org.calacademy.antweb.*" %>
<%@ page import = "org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.Project" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.upload.UploadFile" %>
<%@ page import="java.util.Hashtable" %>

<%
  if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

  java.util.Calendar today = java.util.Calendar.getInstance();
  int year = today.get(java.util.Calendar.YEAR);

  // project defined in project.jsp
    Project project = (Project) request.getAttribute("project");
    Project overview = project;
    
    Login accessLogin = LoginMgr.getAccessLogin(request);

    // For the gui editable boxes (CKeditor)
    Hashtable desc = project.getDescription();   
    if (desc == null) desc = new Hashtable();

    String guiDefaultContent = AntwebProps.guiDefaultContent;

    String thisDesc = null;
    String descHeader = null; 
    String descNotes = null;   

    boolean hasExtraPrivs = false;     

    String thisPageTarget = "project.do?name=" + project.getUseName();
    String editField = request.getParameter("editField");
    if (editField == null) editField = "none";

    if (accessLogin != null) { %>
<script type="text/javascript" src="<%= AntwebProps.getDomainApp() %>/ckeditor/ckeditor.js"></script>
 <% } %>

<input type="hidden" id="coords_for_geo" name="coords_for_geo" value="<%= project.getCoords() %>">
<%@include file="/projectMap.jsp" %>

<div id="page_contents">
<%
String summaryDo = overview.getTargetDo();
String summaryHeading = overview.getHeading();
if (AntwebProps.isDevMode()) AntwebUtil.log("project-body.jsp summaryDo:" + summaryDo);
if (!project.isAntProject()) {
  summaryDo = "country.do";
  summaryHeading = "Country";
}
%>

<br><br><h1><%= "<a href='" + AntwebProps.getDomainApp() + "/" + summaryDo + "'>" + summaryHeading + ":</a> " + overview.getDisplayName() %> </h1>


        <div class="links">
            <ul>
                <li>Overview</li>
                <li><span class="proj_options" id="project_list">Taxa
                    <div class="proj_taxon" id="proj_list">
                        <ul>
                            <li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=subfamily&project=<%= project.getUseName() %>"><span class="numbers"><%= project.getNumSubfamilies() %></span> Subfamilies</a></li>
                            <li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=genus&project=<%= project.getUseName() %>"><span class="numbers"><%= project.getNumGenera() %></span> Genera</a></li>
                            <li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=species&project=<%= project.getUseName() %>"><span class="numbers"><%= project.getNumSpecies() %></span> Species</a></li>
                        </ul>
                    </div>
                </span></li>
                <li><span class="proj_options" id="project_images">Images
                    <div class="proj_taxon" id="proj_images">
                        <ul>
                            <li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=subfamily&project=<%= project.getUseName() %>&images=true"><span class="numbers"><%= project.getNumSubfamilies() %> </span> Subfamilies</a></li>
                            <li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=genus&project=<%= project.getUseName() %>&images=true"><span class="numbers"><%= project.getNumGenera() %> %]</span> Genera</a></li>
                          <% if (project.getNumSpeciesImaged() < 1500) { %>                            
                            <li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=species&project=<%= project.getUseName() %>&images=true"><span class="numbers"><%= project.getNumSpeciesImaged() %></span> Species</a></li>
                          <% } else { %>
                            <li><span class="numbers"><%= project.getNumSpeciesImaged() %></span> Species</li>
                          <% } %>
                        </ul>
	                    </div>
                </span></li>
            </ul>
        </div>
        <div id="antcat_view">
        </div>
        <div class="clear"></div>
        
    </div>
    <div class="page_divider project"></div>
    <div id="totals_and_tools_container">
        <div id="totals_and_tools">

            <div class="clear"></div>
            <div id="tools">
                <div class="tool_label" id="project_fg">
                    Field Guides
                    <div id="pfg_choices">
                        <ul>
                            <li><a href="<%= AntwebProps.getDomainApp() %>/fieldGuide.do?project=<%= project.getUseName() %>&rank=subfamily">Subfamilies of <%= project.getTitle() %></a></li>
                            <li><a href="<%= AntwebProps.getDomainApp() %>/fieldGuide.do?project=<%= project.getUseName() %>&rank=genus">Genera of <%= project.getTitle() %></a></li>
<% if (!"Bolton World Catalog".equals(project.getTitle())) { %> 
                            <li><a href="<%= AntwebProps.getDomainApp() %>/fieldGuide.do?project=<%= project.getUseName() %>&rank=species">Species of <%= project.getTitle() %></a></li>
<% } %>
                        </ul>
                    </div>
                </div>
                <div class="tool_label"><span id="download_data">Download Data</span></div>
                <div class="clear"></div>
            </div>
            <div class="clear"></div>
<%
      String lowercase_project = project.getTitle().toLowerCase();

      // String kmlURL = AntwebProps.getGoogleEarthURI() + "?project=[% name %]";
%>

            <div id="download_data_overlay" style="display:none;">
                <div id="download_overlay">
                    <div class="left"><h3>Download Species</h3></div><div class="right" id="close_download_data">X</div>
                    <div class="clear"></div>
                    <p>To download the species list, Right-click and save to your desktop.</p>
                    <ul><!-- project-body.jsp -->
    <% if (false) { %>
                        <li>&middot; <a href="<%= AntwebProps.getDomainApp() %>/<%= Project.getSpeciesListDir() %><%= project.getRoot() %>/<%= project.getUseName() %><%= UploadFile.getSpeciesListFileTail() %>" target="new">Tab-delimited</a></li>
    <% } %>
    <% if (true) { // AntwebProps.isDevMode() || LoginMgr.isAdmin(accessLogin)) { %>
                        <li>&middot; <a href="<%= AntwebProps.getDomainApp() %>/speciesListDownload.do?projectName=<%= project.getUseName() %>" target="new">Tab-delimited</a></li>
    <% } %>
                    </ul>
                </div>
            </div>
        </div>
    </div>
</div>

<div id="page_data">
<div id="overview_data">

<b>Subfamilies:&nbsp;<%= Formatter.commaFormat(overview.getSubfamilyCount()) %></b>
<br><b>Genera:&nbsp;<%= Formatter.commaFormat(overview.getGenusCount()) %></b>
<br><b>Species/Subspecies:&nbsp;<%= Formatter.commaFormat(overview.getSpeciesCount()) %></b>
<br><b>Endemic Species:&nbsp;<%=  Formatter.commaFormat(overview.getEndemicSpeciesCount()) %></b>
<br><b>Specimen:&nbsp;<%=  Formatter.commaFormat(overview.getSpecimenCount()) %></b>
<!-- br><b>Images:&nbsp;< %=  Formatter.commaFormat(overview.getImageCount()) % ></b -->
<br><b>Imaged Specimens:&nbsp;<%=  Formatter.commaFormat(overview.getImagedSpecimenCount()) %></b>
<!-- h3>Last Calculation:&nbsp;< %= overview.getCreated() % ></h3 -->

<!--
< % if (project.getAuthor() != null) { % >
<h3>< %= project.getAuthor() % > </h3>
< % } % >

 < % if (project.getContents() != null) { % >
        < %= project.getContents() % >
 < % } % >

<p>
< %= project.makeSpecimenImageHtml(1) % >
< %= project.makeSpecimenImageHtml(2) % >
< %= project.makeSpecimenImageHtml(3) % >
</p>

<p>< %= project.getAuthorImageTag() % ></p>

< % if (project.getAuthor() != null) { % >
<b>< %= project.getAuthor() % ></b><br />
< % } % >
< % if (project.getAuthorBio() != null) { % >
< %= project.getAuthorBio() % >
< % } % >
-->


<% if (desc.get("author") != null) { %>
<h3><%= desc.get("author") %></h3>
<% } 

    String targetDo = "project.do";
    String objectName = project.getName();

    thisDesc = "contents"; 
    descHeader = "Contents"; 
    descNotes = "";   %>
   <%@ include file="common/descEdit/descEditFieldCK.jsp" %>

 <% thisDesc = "specimenImage1";
    descHeader = "Project Page Image 1"; %>
 <%@ include file="common/descEdit/imageDescEditField.jsp" %>

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

<script src="//cdnjs.cloudflare.com/ajax/libs/d3/3.4.4/d3.min.js"></script>
<script src="<%= AntwebProps.getDomainApp() %>/chart/d3pie.min.js"></script>

<% if (true) {
     String title = "Species per Subfamily 'Pie Chart'";
     String subtitle = ""; //Distribution of Specimen";
     String footer = ""; //Source: Data submitted to Antweb.org";
     String jsonData = overview.getTaxonSubfamilyDistJson();
     //if (AntwebProps.isDevMode()) AntwebUtil.log("museum-body.jsp jsonData1:" + jsonData); 
%>
     <%@include file="chart/pieChart.jsp" %>
<% } %>


<% if (true) {
     String title = "Specimen per Subfamily 'Pie Chart'";
     String subtitle = ""; //Distribution of Specimen";
     String footer = ""; //Source: Data submitted to Antweb.org";
     String jsonData = overview.getSpecimenSubfamilyDistJson();
     //if (AntwebProps.isDevMode()) AntwebUtil.log("museum-body.jsp jsonData2:" + jsonData);
%>        
    <%@include file="chart/pieChart.jsp" %>
<% } %>

<input id="for_print" type="text" value="<%= project.getAuthor() %>, (<%= year %>). AntWeb: Ants of <%= project.getTitle() %>. Available from: <%= AntwebProps.getDomainApp() %>/page.do?name=<%= project.getRoot() %>">
<input id="for_web" type="text" value="URL: <%= AntwebProps.getDomainApp() %>/page.do?name=<%= project.getRoot() %>">

    </div>
    
    <div class="left">

        <div class="small_map">
<div id="map-canvas" style="height:232px; width:232px; border:1px solid #b9b9b9; overflow:hidden"></div>
        </div>
<% if (project.getMap() != null && !"".equals(project.getMap())) { %>
<div id="static_map" class="slide medium last"><img src="<%= AntwebProps.getImgDomainApp() %>/<%= Project.getSpeciesListDir() %><%= project.getRoot() %>/<%= project.getMap() %>"></div>
<% }

String thisProject = project.getUseName();
if (LoginMgr.isAdmin(accessLogin) || accessLogin.getProjectNames().contains(thisProject))) { %>
<br><a href="<%= AntwebProps.getDomainApp() %>/editProject.do?projectName=<%= thisProject %>">Edit Project</a>
<br><a href="<%= AntwebProps.getDomainApp() %>/editGeolocale.do?id=<%= project.getGeolocaleId() %>">Edit Geolocale</a>
<% } %>

    </div>
</div> 