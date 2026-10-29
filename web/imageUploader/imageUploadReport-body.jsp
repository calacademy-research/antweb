<%-- Body of the Image Upload Report (imageUploadReport.do?id=N): the batch (curator, group, artist, date,
     complete), each saved image with a link to its specimen, and the files not saved with the reason
     (image_upload, image_upload_refused, read by ImageUploadReport). Read-only.
     Docs: docs/flows/image-upload.md (section 7). Tests: tests/e2e/test_image_upload.py. --%>
<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="java.sql.Timestamp" %>
<%@ page import="java.net.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.imageUploader.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.home.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%
    ImageUpload imageUpload = (ImageUpload) request.getAttribute("imageUpload");
  	if (imageUpload == null) { %>
	  <br><br><b>Unable to find imageUpload.</b>	  
 <%   return;
    }

    Artist artist = ArtistMgr.getArtist(imageUpload.getArtistId());
    Group group = GroupMgr.getGroup(imageUpload.getGroupId());
    Curator curator = LoginMgr.getCurator(imageUpload.getCuratorId());
    if (curator == null) {
        AntwebUtil.log("Curator is null for curatorId:" + imageUpload.getCuratorId());
    }
%>


<div id="page_contents">

<div class="in_admin">

<br><a href='<%= AntwebProps.getDomainApp() %>/imageUploader.do'><< Back</a> to Image Uploader

<br><br>
<h2>Image Upload Report: <a href='<%= AntwebProps.getDomainApp() %>/imageUploadReport.do?id=<%= imageUpload.getId() %>'/><%= imageUpload.getId() %></a></h2>
</div>

<br><br>

<div id="page_data">

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Curator: </b></li>
        <li><%= curator != null ? curator.getLink() : "id " + imageUpload.getCuratorId() %> - <a href="<%= AntwebProps.getDomainApp() %>/listImageUploads.do?curatorId=<%= imageUpload.getCuratorId() %>">[Reports]</a>
</li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Group: </b></li>
        <li><%= group != null ? group.getLink() : "id " + imageUpload.getGroupId() %> - <a href="<%= AntwebProps.getDomainApp() %>/listImageUploads.do?groupId=<%= imageUpload.getGroupId() %>">[Reports]</a>
</li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Artist: </b></li>
        <li><%= artist != null ? artist.getLink() : "id " + imageUpload.getArtistId() %></li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>License: </b></li>
        <li>Attribution-ShareAlike (BY-SA) Creative Commons License and GNU Free Documentation License (GFDL)</li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Copyright: </b></li>
        <li><%= imageUpload.getCopyright() != null ? imageUpload.getCopyright().getCopyright() : "" %> </li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Created: </b></li>
        <li><%= imageUpload.getCreated() %></li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Upload Completed: </b></li>
        <li><%= imageUpload.getIsComplete() %></li>
        </ul>
    </div>
  
<%
    int totalImagesUploaded = 0;
    ArrayList<String> imageDirs = new ArrayList<String>();
    ArrayList<String> noImageDirs = new ArrayList<String>();
    String uniqueCasentSearch = "";
    for (ImageUploaded imageUploaded : imageUpload.getImages()) { 
        if (imageUploaded.getIsContinueUpload()) ++totalImagesUploaded;
        if (imageUploaded.getErrorMessage() != null) continue;
        String code = imageUploaded.getCode();        
        if (imageUploaded.getIsSpecimenDataExists()) {
          if (imageUploaded.getIsContinueUpload()) {
            if (!imageDirs.contains(code)) {
                uniqueCasentSearch += "+" + code;
                imageDirs.add(code);
            }

            //A.log("imageUploadReport-body.jsp imageUploaded:" + imageUploaded.getIsContinueUpload() + imageUploaded.getErrorMessage());
          }
        } else {
            if (!noImageDirs.contains(code)) {
                noImageDirs.add(code);
            }        
        }
    }         
%>   
 
    <div id="overview_data" class="plain">
        <ul>
        <li><b>Uploaded Images Search: </b></li>
        <li><a href='<%= AntwebProps.getDomainApp() %>/advancedSearch.do?searchMethod=advancedSearch&advanced=true&isIgnoreInsufficientCriteria=true&sortBy=name&specimenCodeSearchType=contains&specimenCode=<%= uniqueCasentSearch %>'>here</a></li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Recently Added Images: </b></li>
        <li><a href='<%= AntwebProps.getDomainApp() %>/recentSearchResults.do?searchMethod=recentImageSearch&daysAgo=30'>here</a></li>
        </ul>
    </div>
    
    <div id="overview_data" class="plain">
        <ul>
        <li><b>Total image files uploaded: </b></li>
        <li><%= totalImagesUploaded %></li>
        </ul>
    </div>
    
<div class="clear"></div>
<br>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Image files:</b></li>
        <li><%= imageUpload.getImageCount() %></li>
        </ul>
    </div>
<% if (!imageUpload.getRefused().isEmpty()) { %>
    <div id="overview_data" class="plain">
        <ul>
        <li><b>Not saved:</b></li>
        <li><a href="#notSaved"><%= imageUpload.getRefused().size() %></a></li>
        </ul>
    </div>
<% } %>

    <div id="overview_data" class="plain">
        <ul>
<%  
    //HashSet<String> imageDirs = new HashSet<String>();
    
    boolean backedUpFiles = false;    
    
    int i = 0; 
    for (ImageUploaded imageUploaded : imageUpload.getImages()) { 
      ++i;
      
      String flag = "";
      if (imageUploaded.getIsReUploaded()) {
        backedUpFiles = true;
//        flag = " <a title='Image already existed. It has been moved to the backup directory. See below.'><img width='12' src='" + AntwebProps.getDomainApp() + "/image/redICircle.png'></a>";
        flag = " <a href='" + AntwebProps.getDomainApp() + "/images/backup/" + Html.escAttr(Html.url(imageUploaded.getFileName())) + "' title='Image already existed. It has been moved to the duplicates directory. See below.'><font color=red>Duplicate</font></a>";
      }
      if (!imageUploaded.getIsSpecimenDataExists()) {
        flag = " <a title='Specimen data does not exist for this image. Upload data and then re-upload images.'><img width='10' src='" + AntwebProps.getDomainApp() + "/image/redX.png'></a>";
      }
      if (imageUploaded.getErrorMessage() != null) {
A.log("imagerUploadReport-body.jsp fileName:" + imageUploaded.getFileName() + " errorMessage:" + imageUploaded.getErrorMessage() + " flag:" + flag);      
        flag = " <font color=red>" + Html.esc(imageUploaded.getErrorMessage()) + "</font>";
      }
      String specimenLink = ""; //imageUploaded.getDisplayName();
      String tifLink = Html.esc(imageUploaded.getFileName());
      if (imageUploaded.getIsContinueUpload()) {
        specimenLink = "<a href='" + AntwebProps.getDomainApp() + "/bigPicture.do?code=" + Html.escAttr(Html.url(imageUploaded.getCode())) + "&amp;shot=" + Html.escAttr(Html.url(imageUploaded.getShot())) + "&amp;number=" + Html.escAttr(imageUploaded.getNumber()) + "'><img src='" + AntwebProps.getDomainApp() + "/image/upRight.png' width=13></a>";
        tifLink = "<a href='" + AntwebProps.getDomainApp() + "/images/" + Html.escAttr(imageUploaded.getCode()) + "/" + Html.escAttr(imageUploaded.getFileName()) + "'>" + Html.esc(imageUploaded.getFileName()) + "</a>";
      }

      %>
      <ul>
        <li><b><%= i %>:</b></li>
        <li><%= tifLink %> <%= specimenLink %> <%= flag %></li>        
      </ul>
 <% } %>
        </ul>
    </div>

<div class="clear"></div>
<br>

<%
    // Files that were not saved, and why (image_upload_refused). Names and reasons may come from
    // the curator's browser: always escaped.
    if (!imageUpload.getRefused().isEmpty()) { %>
    <div id="notSaved" class="plain">
        <ul>
        <li><b>Not saved (<%= imageUpload.getRefused().size() %>):</b></li>
        <li>These files were not saved. To add them, fix what the reason says, then upload them again.</li>
        </ul>
    </div>
    <div class="plain image-upload-not-saved">
        <ul>
<%
   int r = 0;
   for (ImageUploadRefusedDb.Refused refusedFile : imageUpload.getRefused()) {
     ++r; %>
      <ul>
        <li><b><%= r %>:</b></li>
        <li><span class="not-saved-name"><%= Html.esc(refusedFile.getFileName()) %></span>: <span class="not-saved-reason"><%= Html.esc(refusedFile.getReason()) %></span></li>
      </ul>
<% } %>
        </ul>
    </div>

<div class="clear"></div>
<br>
<% } %>

<% if (imageDirs.size() > 0) { %>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Uploaded Specimen Set: </b></li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
<%        
   i = 0;
   for (String imgDir : imageDirs) { 
     ++i;
   %>
    <ul>
        <li><b><%= i %>: </b></li>
        <%-- Link to the specimen page. The old link opened the specimen's images folder, and folder listings are off. --%>
        <li><a href='<%= AntwebProps.getDomainApp() %>/specimen.do?code=<%= Html.escAttr(Html.url(imgDir)) %>'><%= Html.esc(imgDir) %></a></li>
    </ul>
<% } %>
        </li>
        </ul>
    </div>

<% } %>        

<div class="clear"></div>
<br>
<% if (noImageDirs.size() > 0) { %>

    <div id="overview_data" class="plain">
        <ul>
        <li><b><a title='Specimen data does not exist for this image. Upload data and then re-upload images.'>
          <img width='12' src='<%= AntwebProps.getDomainApp() %>/image/redX.png'></a> No Specimen Data Set: </b></li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
<%        
   i = 0;
   for (String imgDir : noImageDirs) { 
     ++i;
   %>
    <ul>
        <li><b><%= i %>: </b></li>
        <li><%= Html.esc(imgDir) %></li>        
    </ul>
<% } %>
        </li>
        </ul>
    </div>
<% } %>    
        
    
<div class="clear"></div>
<br>

<% if (backedUpFiles) { %>

    <div id="overview_data" class="plain">
        <ul>
        <li><font color=red>Duplicate</font> <b>Images Dir</b></li>
        <li>Copies of images that existed before this upload are kept in images/backup/. Each <font color=red>Duplicate</font> link above opens the copy.</li>
        </ul>
    </div>

<% } %>    
    
</div>    

</div>
