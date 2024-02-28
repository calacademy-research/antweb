<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="java.util.ResourceBundle" %>

<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
                                                                                                                                   
<%
   Overview overview = OverviewMgr.getOverview(request);
   if (overview == null) overview = ProjectMgr.getProject(Project.ALLANTWEBANTS);

   Specimen specimen = (Specimen) request.getAttribute("specimen");
   Taxon taxon = specimen;

   if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
  
   String thePage = HttpUtil.getTarget(request);

   org.calacademy.antweb.Formatter formatter = new Formatter();

   boolean hasMap = taxon.hasMap();
   org.calacademy.antweb.Map map = taxon.getMap();
   String object = "specimen";
   String objectName = specimen.getName();
   String mapType = "specimen";
   
   String dagger = "";
   if (specimen.getIsFossil()) dagger = "&dagger;";

   String guiDefaultContent = AntwebProps.guiDefaultContent;

   String thisPageTarget = "specimen.do?name=" + objectName;    // taxon.getBrowserParams();
   Hashtable desc = specimen.getDescription();   
   
   Login accessLogin = LoginMgr.getAccessLogin(request);
   
   String editField = request.getParameter("editField");
   if (editField == null) editField = "none";
%>

<% if (accessLogin != null) { %>
<script type="text/javascript" src="<%= AntwebProps.getDomainApp() %>/ckeditor/ckeditor.js"></script>
<% } %>

<input id="for_print" type="text" value="AntWeb. Available from: <%= HttpUtil.getFullJspTarget(request)%>">
<input id="for_web" type="text" value="URL: <%= HttpUtil.getFullJspTarget(request)%>">
<div id="page_contents">
<%@ include file="/common/taxonTitle.jsp" %>

        <div class="links">
            <ul>
                <li>Overview</li>
                <li><a href="<%= AntwebProps.getDomainApp() %>/specimenImages.do?name=<%=specimen.getName() %>">Images</a></li>
<%
    //A.log("specimen-body.jsp hasMap:" + taxon.hasMap() + " map:" + map);
    
    if (hasMap) {
        String params = "";
        // This removed. Sep 5 2018. Title was carrying over to maps page.
        //if (overview != null) params = "&" + overview.getParams();
        //AntwebUtil.log("specimen-body.jsp params:" + params);
        if (Rank.SPECIMEN.equals(taxon.getRank())) {
          if (specimen.getName() != null) {
            String link_params = "specimen=" + specimen.getName().toLowerCase();// + params;
%>
              <li><a href="<%= AntwebProps.getDomainApp() %>/bigMap.do?<%= link_params %>">Map</a></li>
<%        } else AntwebUtil.log("specimen-body.jsp specimenName:" + specimen.getName() + " overview:" + overview + " taxon:" + taxon);
        } else { 
          AntwebUtil.log("specimen-body.jsp why would rank not be specimen on this page:" + HttpUtil.getTarget(request));
        %> 
              <li><a href="<%= AntwebProps.getDomainApp() %>/bigMap.do?<%= object %>=<%= objectName %><%= params %>">Map</a></li>
<%      }
    } %>
            </ul>
        </div>
        
        <div class="clear"></div>

    <!-- 
        taxonName: <%= specimen.getTaxonName() %> 
        details: <%= specimen.getDetails() %>
        fullName: <%= specimen.getFullName() %>
        simpleName: <%= specimen.getSimpleName() %>
        prettyName: <%= specimen.getPrettyName() %>
        fullName: <%= specimen.getFullName() %>
        name: <%= specimen.getName() %>
    -->
<%@ include file="/common/taxonomicHierarchy.jsp" %>
</div>

<%  String defaultSpecimenTaxon = (String) session.getAttribute("defaultSpecimenTaxon");
    if (false && defaultSpecimenTaxon != null) { %> 
        <%@ include file="/curate/defaultSpecimen/defaultSpecimenConfirmPanel.jsp" %>
 <% } %>

<div id="page_data">
    <div id="overview_data" class="plain">
<% 
  String persistentIdentifier = AntwebProps.getDomainApp() + "/specimen/" + specimen.getCode().toUpperCase();
%>

<%@ include file="/common/flagInclude.jsp" %>

<h3> Persistent Identifier: </h3><a href="<%= persistentIdentifier %>"><%= persistentIdentifier %></a>

<% String seeAlso = specimen.getSeeAlso();
   if (seeAlso != null) { %>
     <h3>See Also: </h3> <%= seeAlso %>
<% } %>

<%  if (desc != null) { 
      String thisDesc = null;
      String descHeader = null; 
      String descNotes = null;
      
      boolean hasExtraPrivs = true;
      if ( (accessLogin != null) && (accessLogin.getId() == 2) ) hasExtraPrivs = true;
          // We give extra privs to Jack - he can modify notes
      thisDesc = "notes";       
      descHeader = "Notes"; 
      descNotes = "A category intended as a place for content that is difficult to fit into available subject headings or that contains content intended for a wide variety of subject headings.";   %>
   <%@ include file="/common/descEdit/taxonEditFieldCK.jsp" %>
    
<% } else {
     org.calacademy.antweb.util.AntwebUtil.log("specimen-body.jsp desc is null");
   } %>

   <!-- insert author history here -->
   <% ArrayList<String> hist = (ArrayList) request.getAttribute("descEditHist");
      if (!(hist == null)) {
        if (!hist.isEmpty()) { %>
          <p><h3>Specimen Page Author History</h3>  
        <%
          int eventCount = 0;
          int eventDisplayCount = 3;
          String toolTip = "";
          for (String event : hist) {
            ++eventCount;
            if (eventCount > eventDisplayCount) { 
              int eventDisplayCounter = 0;
              for (String tipEvent : hist) {
                ++eventDisplayCounter;
                if (eventDisplayCounter > eventDisplayCount)   
                  toolTip += "\r\n" + tipEvent;
              }
              break;
            }
            out.println(event + "<br>");
          }
          if (!"".equals(toolTip)) {
            %>  <a title="<%= toolTip %>">...</a><br> <%          
          }
        }
      }
      
boolean bioregionNotNative = false;
String nativeWarning = "";
if (!specimen.getIsFossil()) {
  Taxon genus = TaxonMgr.getGenus(Taxon.getGenusTaxonNameFromName(taxon.getTaxonName()));
  if (genus != null) {
    bioregionNotNative = !TaxonPropMgr.isBioregionNative(specimen.getBioregion(), genus.getBioregionMap());  
  }      
}

String speciesDistStatus = specimen.getDistributionStatus();
if (bioregionNotNative && "Native".equals(speciesDistStatus)) speciesDistStatus = "Possibly introduced";
%>
  <h3>Species Distribution Status: <font size=2><%= speciesDistStatus %></font></h3>

<h3>Locality Information: </h3>

<ul>
<li><b>Bioregion: </b></li>

<%
  if (bioregionNotNative) nativeWarning = " <font color=red>(Warning: Genus not native)</font>";
%>

<li><%= specimen.getBioregion() %><%= nativeWarning %></li>
</ul>

<%
String localityLink = specimen.getLocalityLink();
if (localityLink != null) { %>
<ul>
<li><b>Locality name: </b></li>
<li><%= localityLink %></li>
</ul>

<%
   Geolocale country = GeolocaleMgr.getCountry(specimen.getCountry());
   if (country != null) out.println("<ul><li><b>" + country.getHeading() + ":</b></li><li><a href='" + country.getThisPageTarget() + "'>" + country.getName() + "</a></li></ul>");
   //A.log("specimen-body.jsp islandCountry:" + GeolocaleMgr.getIsland(specimen.getIslandCountry()));
   Geolocale island = GeolocaleMgr.getIsland(specimen.getIslandCountry());
   if (island != null) {
      out.println("<ul><li><b>" + island.getHeading() + ":</b></li><li><a href='" + island.getThisPageTarget() + "'>" + island.getName() + "</a></li></ul>");
   }
   //out.println("<ul><li><b>" + specimen.getIslandCountry() + "</b></li><li></ul>");
   Adm1 adm1 = (Adm1) GeolocaleMgr.getAdm1(specimen.getAdm1(), specimen.getCountry());
   
   if (country != null && island == null) {
     //A.log("specimen-body.jsp  adm1:" + adm1);
     if (adm1 != null) {
       String warnNote = "";
       if (!GeolocaleMgr.isAccepted(adm1)) warnNote = "&nbsp;<font color=red>(Not Antweb valid Adm1)</font>";
       out.println("<ul><li><b>Adm1: </b></li><li><a href='" + adm1.getThisPageTarget() + "'>" + adm1.getName() + "</a></li></ul>" + warnNote);
     } else {
       String unrecognizedAdm1Value = "";
       if (specimen.getAdm1() != null) {
         unrecognizedAdm1Value = "<li>" + specimen.getAdm1() + " <font color=red><a title='unrecognized Adm1'>*</a></font></b></li>";
       }
       out.println("<ul><li><b>Adm1: </b></li>" + unrecognizedAdm1Value + "</ul>");
     }
   }
   if (Utility.notBlank(specimen.getAdm2())) out.println("<ul><li><b>Adm2: </b></li><li>" + specimen.getAdm2() + "</a></li></ul>");

   if (Utility.displayEmptyOrNotBlank(specimen.getLocLatitude()) 
       && Utility.displayEmptyOrNotBlank(specimen.getLocLongitude()) ) { %>
    <ul><li><b>Latitude: </b></li><li><bean:write name="specimen" property="locLatitude"/></li></ul>
 
    <ul><li><b>Longitude: </b></li><li><bean:write name="specimen" property="locLongitude"/></li></ul>
<% } %>

<%
   if (!"0.0".equals(specimen.getLocLatitude()) && !"0.0".equals(specimen.getLocLongitude()) ) {
     boolean isWithinCountryBounds = true;
     boolean isWithinAdm1Bounds = true;
     Geolocale geoCountry = GeolocaleMgr.getAnyCountry(specimen.getCountry());
     if (geoCountry != null) isWithinCountryBounds = geoCountry.isWithinBounds(specimen.getDoubleLatitude(), specimen.getDoubleLongitude());
     if (specimen.getCountry() == null) {
       AntwebUtil.log("specimen-body.jsp country null for: " + HttpUtil.getTarget(request));
     }
     Geolocale geoAdm1 = GeolocaleMgr.getAnyAdm1(specimen.getAdm1(), specimen.getCountry());
     if (geoAdm1 != null) isWithinAdm1Bounds = geoAdm1.isWithinBounds(specimen.getDoubleLatitude(), specimen.getDoubleLongitude());

     String heading = null;
     String bounds = null;
     if (!isWithinCountryBounds) {
       heading = "<font color=red>Out of Country Bounds: </font>";
       bounds = geoCountry.getTag() + " (left, bottom, right, top): " + geoCountry.useBoundingBox();
     }
     if (!isWithinAdm1Bounds) {
       heading = "<font color=red>Out of Adm1 Bounds: </font>";
       bounds = geoAdm1.getTag() + " (left, bottom, right, top): " + geoAdm1.useBoundingBox();
     }
     if (heading != null) { %>
       <ul><li><%= heading %></li><li><%= bounds %></li></ul>
  <% } 
   }
    %>


<% if (Utility.displayEmptyOrNotBlank(specimen.getLatLonMaxError())) { %>
    <ul><li><b>Lat/Long max error: </b></li><li><bean:write name="specimen" property="latLonMaxError"/></li></ul>
<% } %>

<% if (Utility.displayEmptyOrNotBlank(specimen.getElevation())) { %>    
    <ul><li><b>Elevation</b></li><li><bean:write name="specimen" property="elevation"/> <%= (specimen.getElevation() != null) ? "m" : "" %></li></ul>    
<% } %>

<% if (Utility.displayEmptyOrNotBlank(specimen.getElevationMaxError())) { %> 
    <ul><li><b>Elevation max error: </b></li><li><bean:write name="specimen" property="elevationMaxError"/></li></ul>
<% } %>

<% if (Utility.displayEmptyOrNotBlank(specimen.getLocalityNotes())) { %>
    <ul><li><b>Locality notes: </b></li><li><bean:write name="specimen" property="localityNotes"/></li></ul>
<% } %>
    
<% } else { %>
<% } %>    

<% 
/*
     All sorts of difficulty here resulting from special characters.  See:
       Finaly does work:
         http://localhost/antweb/specimen.do?name=casent0160810
       Always did work:
         http://localhost/antweb/browse.do?subfamily=myrmicinae&genus=tetramorium&name=pacificum&rank=species&project=mauritiusants       

     This is the way output are reported in browse.do:       
       < bean :write name="specimen" property="localityCode" / >

     ... but this was NOT working on this page.  Still not known why.
     
     In short, and in summary, a locality code like "Mah? Mont Copolia 520" which looks like
     "Mahe' Mont Copolia 520" when displayed, should encode as:
     
     http://localhost/antweb/locality.do?name=Mah%E9%20Mont%20Copolia%20520
     
     and the browser would autmatically do this from browse.do but not from specimen.do.  So here, 
     on specimen.do, now, we manually encode the path using the apache commons-httpdclient 
     org.apache.commons.httpclient.util.URIUtil class.  Java.net.URLEncoder is used for url 
     parameters and not for urls themselves (" " -> "+").
*/
%>

<div class="clear"></div> 
<h3>Collection Information: </b></h3>

<ul>
<li><b>Collection code: </b></li>
<li><a href="<%= AntwebProps.getDomainApp() %>/collection.do?name=<%= formatter.clearNull((String) specimen.getCollectionCode()) %>"><%= formatter.clearNull((String) specimen.getCollectionCode()) %></a> &nbsp;</li>

</ul>

<ul>
<li><b>Collected by: </b></li>
<li><%= formatter.clearNull((String)  specimen.getCollectedBy()) %> &nbsp;</li>
</ul>

<ul>
<li><b>Date collected start: </b></li>
<li><%= formatter.clearNull(specimen.getDateCollectedStart()) %>
  <% // A.log("loggedin:" + AntwebProps.isLoggedIn(request) + " s:" + specimen.getDateCollectedStart() + " s2:" + specimen.getDateCollectedStartStr()); %>
  <% if (LoginMgr.isLoggedIn(request) && specimen.getDateCollectedStart() != null 
       && !specimen.getDateCollectedStart().equals(specimen.getDateCollectedStartStr())) { %>
       (Input as: <%= formatter.clearNull(specimen.getDateCollectedStartStr()) %>)<% } 
   %>
</li>
</ul>
  
<% if (Utility.displayEmptyOrNotBlank(specimen.getDateCollectedEnd())) { %>   
<ul>
<li><b>Date collected end: </b></li>
<li><%= formatter.clearNull(specimen.getDateCollectedEnd()) %>
  <% if (LoginMgr.isLoggedIn(request) && specimen.getDateCollectedEnd() != null 
       && !specimen.getDateCollectedEnd().equals(specimen.getDateCollectedEndStr())) { %>
       (Input as: <%= formatter.clearNull(specimen.getDateCollectedEndStr()) %>)<% } 
   %>
</li>
</ul>
<% } %>

<% if (LoginMgr.isAdmin(request)) { %>
<ul>
<li><b>Museum: </b></li>
<%
   String museumCodeText = "";
   if (specimen.getMuseumCode() != null) {
     museumCodeText = "<a href='" + AntwebProps.getDomainApp() + "/museum.do?name=" + specimen.getMuseumCode() + "'>" + specimen.getMuseumCode() + "</a>";
   }
%>
   <li><%= museumCodeText %> &nbsp;</li>
   
</ul>
<% } %>

<% if (Utility.displayEmptyOrNotBlank(specimen.getHabitat())) { %>   
<ul>
<li><b>Habitat: </b></li>
<li><%= formatter.clearNull((String) specimen.getHabitat()) %>&nbsp;</li>
</ul>
<% } %>

<ul>
<li><b>Sampling method: </b></li>
<li><%= formatter.clearNull((String) specimen.getMethod()) %></li>
</ul>

<% if (specimen.getIsIntroduced()) { %>
<ul>
<li><b>Is Introduced: </b></li>
<li><%= specimen.getIsIntroduced() %></li>
</ul>
<% } %>

<% if (Utility.displayEmptyOrNotBlank(specimen.getMicrohabitat())) { %>   
<ul>
<li><b>Microhabitat: </b></li>
<li><%= formatter.clearNull(specimen.getMicrohabitat()) %></li>   <!-- (String) desc.get("microhabitat") -->
</ul>   <!-- was transecttype -->
<% } %>

<% 
if ((Utility.displayEmptyOrNotBlank(specimen.getCollectionNotes()))) { %>
<ul>
<li><b>Collection notes: </b></li><li><bean:write name="specimen" property="collectionNotes"/></li>
</ul>
<% } %>


<div class="clear"></div>
<h3>Specimen Information: </b></h3>

<ul>
<li><b>Life stage/sex notes: </b></li>
<li><%= formatter.clearNull((String) specimen.getLifeStage()) %></li>
</ul>

<%
String casteValue = specimen.getCaste();
if (casteValue == null) casteValue = "undefined";
%>
<ul>
<li><b>Caste: </b></li>
<li><%= casteValue %></li>
</ul>

<% if (specimen.getSubcaste() != null) { %>
 <ul>
<li><b>Subcaste: </b></li>
<li><%= formatter.clearNull((String) specimen.getSubcaste()) %></li>
</ul>
<% } %>

<ul>
<li><b>Located at: </b></li>
<li><%= specimen.getLocatedAtLink() %></li>
</ul>

<ul>
<li><b>Owned by: </b></li>
<li><%= specimen.getOwnedByLink() %></li>
</ul>
  
<% if (Utility.displayEmptyOrNotBlank(specimen.getDeterminedBy())) { %>   
<ul>
<li><b>Determined by: </b></li>
<li><%= formatter.clearNull(specimen.getDeterminedBy()) %> </li> <!-- (String) desc.get("determinedby") -->
</ul>
<% } %>
  
<ul>
<li><b>Date determined: </b></li>
<li><%= formatter.clearNull(specimen.getDateDetermined()) %>
  <% if (LoginMgr.isLoggedIn(request) && specimen.getDateDeterminedStr() != null 
       && !specimen.getDateDetermined().equals(specimen.getDateDeterminedStr())) { %>
       (Input as: <%= formatter.clearNull(specimen.getDateDeterminedStr()) %>)<% } 
   %>
</li>
</ul>
  
<% if (Utility.displayEmptyOrNotBlank(specimen.getTypeStatus())) { %>   
<ul>
<li><b>Type status: </b></li>
<li>
<bean:write name="specimen" property="typeStatus"/></li>
</ul>
<% } %>

<ul>
<li><b>Type: </b></li>
<li>
<logic:present name="specimen" property="isType"><logic:equal name="specimen" property="isType" value="true"><img src="<%= AntwebProps.getDomainApp() %>/image/has_type_status_icon.png"></logic:equal></logic:present>
<bean:write name="specimen" property="isType"/></li>
</ul>


<% if (Utility.displayEmptyOrNotBlank(specimen.getMedium())) { %>   
<ul>
<li><b>Medium: </b></li>
<li><%= formatter.clearNull(specimen.getMedium()) %> </li>  <!-- was (String) desc.get("medium")  -->
</ul>
<% } %>

<% if (Utility.displayEmptyOrNotBlank(specimen.getDnaExtractionNotes())) { %>   
<ul>
<li><b>DNA notes: </b></li>
<li><%= formatter.clearNull(specimen.getDnaExtractionNotes()) %> </li>  <!-- (String) desc.get("dnaextractionnotes") -->
</ul>
<% } %>
  
<% if (Utility.displayEmptyOrNotBlank(specimen.getSpecimenNotes())) { %>
<ul>
<li><b>Specimen notes: </b></li>
<li> <bean:write name="specimen" property="specimenNotes"/></li>
</ul>
<% } %>
<div class="clear"></div>

<% if (specimen.hasOriginalTaxonName()) { %>
<ul>
<li><b>Uploaded As: </b></li>
<li><a href="<%= AntwebProps.getDomainApp() + "/description.do?taxonName=" + specimen.getOriginalTaxonName() %>"><%= Taxon.displayTaxonName(specimen.getOriginalTaxonName()) %></a> &nbsp;</li>
</ul>
<% } %>

<% Group specimenGroup = specimen.getGroup();
   if (specimenGroup != null) { 
     Curator specimenCurator = specimen.getCurator();
     if (specimenCurator != null) { %>
       <ul>
       <li><b>Data upload login: </b></li>
       <li><%= specimenCurator.getLink() %> &nbsp;</li>
       </ul>
       <ul>
  <% }
     if (specimenGroup != null) { %>
       <li><b>Data upload group: </b></li>
       <li><%= specimenGroup.getLink() %> &nbsp;</li>
       </ul>
<%   }
   } else { %>
     <ul>
     <li><b>Data uploaded by: </b></li>
     <li></li>
     </ul>
<% } %>

<ul>
<li><b>Last Modified: </b></li>
<li><%= formatter.clearNull(specimen.getLastModified()) %> &nbsp;</li>
</ul>

<%
if (LoginMgr.isAdmin(request) || specimen.isCurator(accessLogin)) { %>

<br>
<h3>Curator Information: </h3> 

<ul>
<li><b>Upload Record: </b></li>
<% if (specimen.getBackupFileName() != null) { %>
<li>Upload File Line: <a href='<%= AntwebProps.getDomainApp() %>/showLog.do?action=uploadLog&file=<%= specimen.getBackupFileName() %>&line=<%= specimen.getLineNum() %>'><%= specimen.getLineNum() %></a></li>
<% } %>
</ul>

<% if (UploadMgr.hasUpload(specimen.getUploadId())) { 
  org.calacademy.antweb.upload.Upload upload = UploadMgr.getUpload(specimen.getUploadId());
%>
<ul>
<li><b>Upload Report: </b></li>
<li><a href='<%= AntwebProps.getDomainApp() %>/uploadReport.do?uploadId=<%= specimen.getUploadId() %>'><%= upload.getLogFileName() %></a></li>
</ul>
<% } %>
<% 
} 
%>


<%
if (LoginMgr.isAdmin(request)) {
 %>

<br>
<h3>Admin Information: </h3> 

<ul>
<li><b>TaxonName: </b></li>
<%
  String taxonNameUrl = AntwebProps.getDomainApp() + "/description.do?taxonName=" + specimen.getTaxonName();
  //if (AntwebProps.isDevMode()) AntwebUtil.log("TaxonNameUrl:" + taxonNameUrl);
%>
<li><a href="<%= taxonNameUrl %>"><%= specimen.getTaxonName() %></a></li>
</ul>

<ul>
<li><b>Upload File: </b></li>
<% if (specimen.getBackupFileName() != null) { %>
<li><a href='<%= AntwebProps.getDomainApp() %>/web/upload/<%= specimen.getBackupFileName() %>'><%= specimen.getBackupFileName() %></a></li>
<% } %>
</ul>

<ul>
<li><b>Upload Search: </b></li>
<li><a href="<%= AntwebProps.getDomainApp() %>/advancedSearch.do?searchMethod=advancedSearch&advanced=true&uploadId=<%= specimen.getUploadId() %>">Upload ID: <%= specimen.getUploadId() %></a></li> <!-- AntwebMgr.getSpecimenUploadId() -->
</ul>

<ul>
<li><b>Images dir: </b></li>
<li><a href='<%= AntwebProps.getDomainApp() %>/images/<%= specimen.getCode() %>'>/images/<%= specimen.getCode() %></a></li>
</ul>

<ul>
<li><b>Image Count: </b></li>
<li><%= specimen.getImageCount() %> &nbsp;</li>
</ul>

<ul>
<li><b>Default Pic for: </b></li>
<li><a href='<%= AntwebProps.getDomainApp() %>/description.do?taxonName=<%= specimen.getDefaultFor() %>'><%= Taxon.getPrettyTaxonName(specimen.getDefaultFor()) %></a></li>

<ul>
<li><b>Line Number: </b></li>
<li><%= specimen.getLineNum() %></li>
</ul>

<ul>
<li><b>Description: </b></li>
<li><a href='<%= AntwebProps.getDomainApp() %>/showLog.do?action=specimenDetails&code=<%= specimen.getCode() %>'>Link</a> -
<%= specimen.getDetailHash() %> &nbsp;</li>
</ul>

<ul>
<li><b>Introduced: </b></li>
<li><%= specimen.getIsIntroduced() %> &nbsp;</li>
</ul>


<% if (specimen.getTheXml() != null) { %>
<ul>
<li><b>XML: </b></li>
<li><%= specimen.getTheXml() %></li>
</ul>
<br><br><p>
<% } %>

<% 
} 
%>

<div class="clear"></div>
<br /><br />
<% 
   String amissEmail = AntwebUtil.getAdminEmail();
   if (specimen != null && specimen.getGroup() != null) amissEmail = specimen.getGroup().getAdminEmail();  
%>
<a href="mailto: <%= amissEmail %>?subject=Regarding AntWeb page <%= thePage %>">See something amiss? Send us an email.</a>

</div>

    <div class="left">
<%

if (!HttpUtil.isBot(request)) {
   if (specimen.getImages() != null) { %>    
<logic:iterate id="theImage" name="specimen" collection="<%= specimen.getImages() %>">
  <logic:equal name="theImage" property="key" value="p1">
        <div class="slide medium last" style="background-image: url('<%= AntwebProps.getImgDomainApp() %><bean:write name="theImage" property="value.thumbview" />');" onclick="window.location='<%= AntwebProps.getDomainApp() %>/bigPicture.do?name=<%= specimen.getName() %>&shot=p&number=1';"></div>
        <div class="clear"></div>
  </logic:equal>
</logic:iterate>

<%
 A.log("specimen-body.jsp isBot:" + HttpUtil.isBot(request));
%>

        <div class="small_map">
            <%@include file="/maps/googleMapPreInclude.jsp" %>
            <%@include file="/maps/googleMapInclude.jsp" %>
        </div>
    </div>
    <div class="clear"></div>

<% } else { 
     AntwebUtil.log("specimen-body.jsp specimen.getImages() is null. request:" + HttpUtil.getTarget(request)
     );
   } 
}   
%>

</div>
