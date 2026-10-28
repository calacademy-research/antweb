<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ page import = "java.awt.*" %>
<%@ page import = "javax.swing.ImageIcon" %>
<%@ page import = "org.calacademy.antweb.Formatter" %>
<%@ page import = "org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>

<%@ page import="java.util.ResourceBundle" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="java.util.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<jsp:useBean id="theImage" scope="session" type="org.calacademy.antweb.SpecimenImage" />
<jsp:useBean id="theImageTaxon" scope="session" type="org.calacademy.antweb.Specimen" />
<%-- No jsp:setProperty property="*" here (2026-10-27): the action fills the bean; request values must not overwrite what the page shows. --%>

<jsp:useBean id="specimen" scope="session" class="org.calacademy.antweb.Specimen" />

<%
    Login accessLogin = LoginMgr.getAccessLogin(request);
    Group accessGroup = GroupMgr.getAccessGroup(request);

    Overview overview = OverviewMgr.getOverview(request);
    Taxon taxon = (Taxon) session.getAttribute("theImageTaxon"); %>

<!--
If the page is accessed directly then a java.lang.InstantiationException is thrown.  Error.jsp
is called which displays a short stacktrace.  Remove session variables.  Mark, Feb 8, 2011.File
-->

<%
	String imagePath = AntwebProps.getDocRoot() + theImage.getHighres();
	//AntwebUtil.log("bigPicture-body.jsp imagePath:" + imagePath);
	
	Formatter formatter = new Formatter();

    //if (AntwebProps.isDevMode()) AntwebUtil.log("bigPicture.jsp imgDomainApp:" + AntwebProps.getImgDomainApp() + " domainApp:" + AntwebProps.getDomainApp()); 
    boolean localImage = false;
    if (AntwebProps.getImgDomainApp().equals(AntwebProps.getDomainApp())) {
      localImage = true;
    }    
// Removed the "localImage" check as it was causing 
// the EXIF data to never be read, on either stage OR the live site. 

// if (localImage) {
    if (imagePath.contains("/null")) {
      AntwebUtil.log("WSS.  Image path contains null in bigPicture-body.jsp:" + imagePath);
      String reqInfo = HttpUtil.getRequestInfo(request);
      AntwebUtil.log("  - Request Info:" + reqInfo); 
      return;
    }

    String photographer = null;
    String date = null;

    // EXIF is read from the image file when the page is shown (nothing is stored).
    // Needed for the admin section, and for the photographer when the image has no artist.
    Exif exif = null;
    if (LoginMgr.isAdmin(request) || theImage.getArtist() == null) {
      exif = new Exif(imagePath);
      if (exif.isFound()) {
        if (Utility.notBlank(exif.getArtist())) photographer = exif.getArtist();
      } else {
        A.log("exif not found:" + imagePath);
      }
    }
    
  //if (photographer == null) {
    Artist artist = theImage.getArtist();
    if (artist != null) photographer = artist.getName();
  
    if (theImage.getDate() != null) {
      date = theImage.getDate();
    }

  String object = "taxonName";
  String objectName = taxon.getTaxonName();

  A.log("bigPicture.jsp taxon:" + taxon.getFullName() + " specimen:" + specimen.getName() + " theImage:" + theImage);
%>

<div id="page_contents">

<h1>
<% if (specimen != null && (!"null".equals(specimen)) && !(specimen.getName() == null)) { %>
 <%  if (Utility.notBlank(taxon.getFullName())) { %>
   Specimen: <%= specimen.getName().toUpperCase() %>
   <span class="spec_name"><%= specimen.getTaxonNameDisplay() %> <% if (specimen.getIsValid()) { %><img src="image/valid_name.png" border="0" title="Valid Name"><% } %></span>
  <% } %>
<% } %>
</h1>
        <div class="links">
            <ul>
                <li><a href="specimen.do?name=<%= theImage.getCode() %>">Overview</a></li>
                <li><a href="specimenImages.do?name=<%= theImage.getCode() %>">Images</a></li>
<%
    if (theImageTaxon.hasMap()) {
    String bigPictureParams = "";
    if (overview != null) {
      bigPictureParams = "?" + overview.getParams();
    }
%>
                <li><a href="bigMap.do?specimen=<%= specimen.getName() %><%= bigPictureParams %>">Map</a></li>
<%
    }
%>
            </ul>
        </div>
        <div id="antcat_view"></div>
        <div class="clear"></div>


<% //A.log("bigPicture.jsp 0 taxon:" + taxon.getTaxonName()); %>
<br>
<%@include file="/common/taxonomicHierarchy.jsp" %>

<%
  java.util.ArrayList<String> shotsToShow = new java.util.ArrayList();
  
  // Mark's fix.
  int i = 1;
  while (i < 51) {
    shotsToShow.add("h" + i);
    shotsToShow.add("p" + i);
    shotsToShow.add("d" + i);
    shotsToShow.add("v" + i);
    shotsToShow.add("l" + i);
    shotsToShow.add("f" + i);
    ++i;
  }

  // This code will display the thumbnail images. Not efficient.
  if (specimen != null) {
      if (shotsToShow == null) {
        AntwebUtil.log("bigPicture-body.jsp INVESTIGATE shotsToShow is null for request:" + HttpUtil.getTarget(request));
      }

      for (String shot : shotsToShow) {
        //A.log("devMode shotToShow:" + shotToShow);
      
        Hashtable images = specimen.getImages();
        if (images != null) {
		  Set<String> keySet = images.keySet();
		  for (String thisKey : keySet) {
			if (shot.equals(thisKey)) {        
				SpecimenImage specimenImage = (SpecimenImage) images.get(thisKey);
				String shotType = ((String) thisKey).substring(0,1);
				String shotNumber = ((String) thisKey).substring(1);
				String thisShot = request.getParameter("shot"); 
				String thisNumber = request.getParameter("number");           
		     //A.log("bigPicture-body.jsp shotNumber:" + shotNumber + " thisNumber:" + thisNumber);

				//A.log("bigPicture-body.jsp shot:" + shot + " thisKey:" + thisKey + " shotType:" + shotType + " images:" + images\ + " specimenImage:" + specimenImage.getThumbview()); // 
  %>
				<div class="slide vsmall" style="background-image:url('<%= AntwebProps.getImgDomainApp() %><%= specimenImage.getThumbview() %>');">
					<div class="<% if ((shotType.equals(thisShot)) && (shotNumber.equals(thisNumber))) { %>darken<% } else { %>hover<% } %> vsmall" onclick="window.location='bigPicture.do?name=<%= specimen.getName().toLowerCase() %>&shot=<%= (String) shotType %>&number=<%= (String) shotNumber %>'"></div>
					<div class="clear"></div>
				</div>
  <%
			}
		  }
        } else {
          // Picture like action will fail to display the slideshow images because of this.
          //A.log("bigPicture-body.jsp getImages null for specimen:" + specimen);        
        }      
      }
  } 
%>

<div class="clear"></div>

<span class="left" id="photo_metadata">

<% //if (AntwebProps.isDevMode()) AntwebUtil.log("bigPicture.jsp 1 taxon:" + taxon.getTaxonName()); %>

<% 

  //A.log("bigPicture.jsp theImage:" + theImage); 


String which_shot = theImage.getShot();
String the_shot = "";
if (which_shot.equals("p")) {
   the_shot = "Profile"; 
} else if (which_shot.equals("h")) { 
   the_shot = "Head"; 
} else if (which_shot.equals("d")) { 
   the_shot = "Dorsal"; 
} else if (which_shot.equals("v")) { 
   the_shot = "Ventral"; 
} else if (which_shot.equals("l")) { 
   the_shot = "Label";
} else if (which_shot.equals("f")) {
   the_shot = "Field";
} else {
  the_shot = "";
}
if (theImage.getCode() == null) {
  AntwebUtil.log("bigPicture code is null for imagePath:" + imagePath);
} else { %>
<% } 

String numberTitle = "";
if (theImage.getNumber() > 1) {
  numberTitle = " (Number " + theImage.getNumber() + ")";
}
%>

    <br><h3><b><%= the_shot %> View <%= numberTitle %></b></h3>
    <br> 


<ul>
<%
   String origUrl = AntwebProps.getDomainApp() + theImage.getOrigUrl();
   if (false && AntwebProps.isDevMode()) { %> * <%= origUrl %> <% }
   if (theImage.getOrigUrl() != null) {
   %>
    <li><a href="<%= AntwebProps.getDomainApp() %><%= theImage.getOrigUrl() %>" target="new">View Highest Resolution</a></li>
<% } %>

<%
   // if ((AntwebProps.isDevOrStageMode()) || ) { 
   boolean displayLikeLink = true;

   if (accessLogin == null) displayLikeLink = false;
   if (!HttpUtil.getTarget(request).contains("bigPicture")) displayLikeLink = false;  // no like button on a pictureLike to avoid double posts.
   if ("l".equals(theImage.getShot())) displayLikeLink = false;

   if (displayLikeLink) {  %>
   <li><%= org.calacademy.antweb.util.Csrf.postButton(request, AntwebProps.getDomainApp() + "/pictureLike.do?code=" + Html.url(theImage.getCode()) + "&shot=" + Html.url(theImage.getShot()) + "&number=" + theImage.getNumber(), "Add to Favorites<img src='" + AntwebProps.getDomainApp() + "/image/yellow-star-md.png' width=10/>") %></li>
<% } %>

</ul>
<br>
<ul>
<%
    Group imageGroup = theImage.getGroup();
    if (imageGroup == null) {
      A.log("bigPicture-body.jsp imageGroup is null for request:" + HttpUtil.getTarget(request));
    } else { %>
      <li><b>Uploaded By:</b> <%= imageGroup.getLink() %></li>
 <% }
    String photographerLink = "";
    if (artist != null) photographerLink = artist.getLink();
    %>
    <li><b>Photographer:</b> <%= photographerLink %></li>
    <li><b>Date Uploaded:</b> <%= date %></li>
</ul>
</span>
<span class="right"><b><a href="#" onclick="history.back(); return false;">Back</a></b></span>
<div class="clear"></div>

</div>

<div id="page_data">

<div class="big_picture">
    <img src="<%= AntwebProps.getImgDomainApp() %><%= theImage.getHighres() %>">
</div>

<% if (LoginMgr.isCurator(request)) { %>
    <div id="overview_data" class="plain">
 
<br>
<h3>Curator Information:</h3>
<ul>
<li><b>Images Directory:</b></li>
<li><a href='<%= AntwebProps.getDomainApp() %>/images/<%= specimen.getCode() %>'>/images/<%= specimen.getCode() %></a></li>
</ul>
<ul>
<% if (theImage.getUploadId() > 0) { %>
<li><b>Upload Report:</b></li>
<li><a href='<%= AntwebProps.getDomainApp() %>/imageUploadReport.do?id=<%= theImage.getUploadId() %>'><%= theImage.getUploadId() %></a></li>
</ul>
<% } %>

<ul>
    </div>
<div class="clear"></div>
<% } %>

   
<% if (LoginMgr.isAdmin(request)) { %>
    <div id="overview_data" class="plain">
 
<br>
<h3>Admin Information:</h3>
<ul>
<li><b>Description:</b></li>
<li><%= theImage.getDescription() %></li>
</ul>
<ul>

<li><b>Artist:</b></li>
<li><%= theImage.getArtist() %></li>
</ul>
<ul>

<li><b>image:</b></li>
<li><%= theImage %></li>
</ul>

<ul>
<li><b>EXIF data:</b></li>
<li class="exif_data">
<p class="exif_note">EXIF is information the camera and imaging software store inside the image file:
camera and lens, exposure (shutter speed, aperture, ISO), the date the photo was taken, the software used,
and often the photographer and copyright. It is read from the high resolution file each time this page
is shown. Camera maker's notes and the embedded thumbnail are binary data and are not shown.</p>
<% if (exif == null || !exif.isFound()) { %>
  <p>The image file could not be read.</p>
<% } else if (exif.getRows().isEmpty()) { %>
  <p>This image has no EXIF data.</p>
<% } else { %>
  <table class="exif_table" style="border-collapse:collapse">
  <% for (ExifText.Row row : exif.getRows()) { %>
    <tr><td style="padding:2px 12px 2px 0;vertical-align:top;white-space:nowrap"><%= Html.esc(row.label) %></td><td style="padding:2px 0"><%= Html.esc(row.value) %></td></tr>
  <% } %>
  </table>
<% } %>
</li>
</ul>

<ul>
<li><b>High Res Path:</b></li>
<li><%= theImage.getOrigUrl()  %></li>
</ul>

<ul>
<li><b>Orig File data:</b></li>
<li><%= theImage.getOrigFileData()  %></li>
<% if (true && AntwebProps.isDevMode()) { %> * <%= theImage.getOrigFileData() %> <% } %>

</ul>

<ul>
<li><b>High Res File data:</b></li>
<li><a href='<%= AntwebProps.getImgDomainApp() + theImage.getHighres() %>'><img width = 12 src='<%= AntwebProps.getDomainApp() %>/image/upRight.png'></a> <%= theImage.getHighResData()  %></li>
</ul>

<ul>
<li><b>Med Res File data:</b></li>
<li><a href='<%= AntwebProps.getImgDomainApp() + theImage.getMedres() %>'><img width = 12 src='<%= AntwebProps.getDomainApp() %>/image/upRight.png'></a> <%= theImage.getMedResData()  %></li>
</ul>

<ul>
<li><b>Low Res File data:</b></li>
<li><a href='<%= AntwebProps.getImgDomainApp() + theImage.getLowres() %>'><img width = 12 src='<%= AntwebProps.getDomainApp() %>/image/upRight.png'></a> <%= theImage.getLowResData()  %></li>
</ul>

<ul>
<li><b>Thumb Res File data:</b></li>
<li><a href='<%= AntwebProps.getImgDomainApp() + theImage.getThumbview() %>'><img width = 12 src='<%= AntwebProps.getDomainApp() %>/image/upRight.png'></a> <%= theImage.getThumbData()  %></li>
</ul>


    </div>
<div class="clear"></div>
<% }

   if (LoginMgr.isCurator(request)) {
     //A.log("execute() group:" + theImage.getGroup().getId());
     if (LoginMgr.isAdmin(request) || theImage.getGroup().getId() == accessGroup.getId()) { 
     %>
<br><form method="POST" action="<%= AntwebProps.getDomainApp() %>/bigPicture.do" style="display:inline" onsubmit="return confirm('Delete this image? This cannot be undone.');"><%= org.calacademy.antweb.util.Csrf.field(request) %>
<input type="hidden" name="action" value="delete"><input type="hidden" name="code" value="<%= Html.escAttr(theImage.getCode()) %>"><input type="hidden" name="shot" value="<%= Html.escAttr(theImage.getShot()) %>"><input type="hidden" name="number" value="<%= theImage.getNumber() %>">
<b><input type="submit" value="Delete"> this image (irreversible!)</b></form>

<%   }
   }           
              

  java.util.Calendar today = java.util.Calendar.getInstance();
  int year = today.get(java.util.Calendar.YEAR);
%>
<input id="for_print" type="text" value="Photo by <%= Html.escAttr(photographer) %> / From www.antweb.org">
<input id="for_web" type="text" value="Photo by <%= Html.escAttr(photographer) %> / URL: <%= Html.escAttr(HttpUtil.getFullJspTarget(request)) %>">
<input id="extra_copyright" type="text" value="Image Copyright &copy; AntWeb 2002 - <%= year %>.  Licensing: Creative Commons Attribution License">
</div>
